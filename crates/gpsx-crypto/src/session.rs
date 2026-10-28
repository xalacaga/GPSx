// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

use std::collections::BTreeMap;
use std::fmt;
use std::marker::PhantomData;
use std::ops::Range;

use aes::Aes128;
use aes_gcm::aead::{AeadInPlace, KeyInit as AeadKeyInit};
use aes_gcm::{AesGcm, Nonce, Tag, aead::consts::U16};
use ctr::cipher::{KeyIvInit, StreamCipher, StreamCipherSeek};
use hmac::{Hmac, Mac};
use rand_core::{CryptoRng, RngCore};
use secp256k1::{PublicKey, SecretKey, ecdh::shared_secret_point};
use sha2::{Digest, Sha256};
use subtle::ConstantTimeEq;
use zeroize::{Zeroize, Zeroizing};

use crate::launch_spec::{
    EncryptedLaunchSpecLength, LaunchSpec, LaunchSpecError, SessionRpcrypt,
    write_encrypted_launch_spec,
};

const HANDSHAKE_KEY_SIZE: usize = 16;
const PUBLIC_KEY_SIZE: usize = 65;
const PUBLIC_KEY_TAG_SIZE: usize = 32;
const TRAFFIC_KEY_SIZE: usize = 16;
const BLOCK_SIZE: u64 = 16;
const GMAC_TAG_SIZE: usize = 4;
const GMAC_EPOCH_POSITIONS: u64 = 45_000;
const GMAC_EPOCH_IV_OFFSET: u64 = 44_910;
const MAX_SCALAR_ATTEMPTS: usize = 8;
const MAX_PACKET_SIZE: usize = 2_048;
const MAX_FORWARD_DISTANCE: u64 = 4 * 1_024 * 1_024;
const MAX_BACKWARD_DISTANCE: u64 = 256 * 1_024;
const MAX_GLOBAL_FAILURES: u8 = 32;
const MAX_EPOCH_FAILURES: u8 = 8;
const MAX_EPOCH_INVOCATIONS: u16 = 2_048;
const MAX_TRACKED_EPOCHS: usize = 102;

type Aes128CtrLe = ctr::Ctr128LE<Aes128>;
type Aes128Gcm16 = AesGcm<Aes128, U16>;

/// Static, secret-free failures from session cryptography.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum SessionCryptoError {
    EntropyUnavailable,
    ScalarGenerationExhausted,
    InvalidPeerMaterialLength,
    PeerAuthenticationFailed,
    InvalidPeerPublicKey,
    CryptoInitialization,
    PositionOverflow,
    PacketTooLarge,
    InvalidEncryptedRange,
    PositionOutsideWindow,
    AuthenticationFailed,
    InvocationLimitReached,
    FailureLimitReached,
    InvocationStateExhausted,
    SessionTerminated,
    LaunchSpecNotWritten,
}

impl fmt::Display for SessionCryptoError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(match self {
            Self::EntropyUnavailable => "session entropy source failed",
            Self::ScalarGenerationExhausted => "session scalar rejection limit reached",
            Self::InvalidPeerMaterialLength => "peer handshake material has an invalid length",
            Self::PeerAuthenticationFailed => "peer handshake authentication failed",
            Self::InvalidPeerPublicKey => "peer public key is invalid",
            Self::CryptoInitialization => "session cryptographic primitive could not initialize",
            Self::PositionOverflow => "session key position overflowed",
            Self::PacketTooLarge => "authenticated packet exceeds the accepted size",
            Self::InvalidEncryptedRange => "encrypted packet range is invalid",
            Self::PositionOutsideWindow => "packet position is outside the accepted window",
            Self::AuthenticationFailed => "packet authentication failed",
            Self::InvocationLimitReached => "GMAC invocation limit reached",
            Self::FailureLimitReached => "authentication failure limit reached",
            Self::InvocationStateExhausted => "bounded GMAC epoch state is exhausted",
            Self::SessionTerminated => "session cryptography is terminated",
            Self::LaunchSpecNotWritten => "encrypted LaunchSpec was not written",
        })
    }
}

impl std::error::Error for SessionCryptoError {}

macro_rules! redacted_format {
    ($type:ty, $label:literal) => {
        impl fmt::Debug for $type {
            fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
                formatter.write_str(concat!($label, "([REDACTED])"))
            }
        }
        impl fmt::Display for $type {
            fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
                formatter.write_str(concat!($label, "([REDACTED])"))
            }
        }
    };
}

struct HandshakeKey([u8; HANDSHAKE_KEY_SIZE]);

impl Drop for HandshakeKey {
    fn drop(&mut self) {
        self.0.zeroize();
    }
}

redacted_format!(HandshakeKey, "HandshakeKey");

struct EphemeralScalar(SecretKey);

impl EphemeralScalar {
    fn from_candidate(candidate: &[u8; 32]) -> Option<Self> {
        SecretKey::from_secret_bytes(*candidate).ok().map(Self)
    }

    fn as_ref(&self) -> &SecretKey {
        &self.0
    }
}

impl Drop for EphemeralScalar {
    fn drop(&mut self) {
        self.0.non_secure_erase();
    }
}

redacted_format!(EphemeralScalar, "EphemeralScalar");

/// Marker for a handshake that has not yet authenticated its peer.
pub struct AwaitingPeer;

/// Public, authenticated-by-HMAC material sent during the handshake.
#[derive(Clone, Eq, PartialEq)]
pub struct LocalHandshakeMaterial {
    public_key: [u8; PUBLIC_KEY_SIZE],
    tag: [u8; PUBLIC_KEY_TAG_SIZE],
}

impl LocalHandshakeMaterial {
    pub fn public_key(&self) -> &[u8; PUBLIC_KEY_SIZE] {
        &self.public_key
    }

    pub fn tag(&self) -> &[u8; PUBLIC_KEY_TAG_SIZE] {
        &self.tag
    }
}

impl fmt::Debug for LocalHandshakeMaterial {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_struct("LocalHandshakeMaterial")
            .field("public_key_len", &self.public_key.len())
            .field("tag_len", &self.tag.len())
            .finish()
    }
}

/// Unauthenticated peer bytes. Validation happens only inside `finish`.
pub struct UnverifiedPeerKey {
    public_key: [u8; PUBLIC_KEY_SIZE],
    tag: [u8; PUBLIC_KEY_TAG_SIZE],
}

impl UnverifiedPeerKey {
    pub fn new(public_key: &[u8], tag: &[u8]) -> Result<Self, SessionCryptoError> {
        let public_key = public_key
            .try_into()
            .map_err(|_| SessionCryptoError::InvalidPeerMaterialLength)?;
        let tag = tag
            .try_into()
            .map_err(|_| SessionCryptoError::InvalidPeerMaterialLength)?;
        Ok(Self { public_key, tag })
    }
}

impl fmt::Debug for UnverifiedPeerKey {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_struct("UnverifiedPeerKey")
            .field("public_key_len", &self.public_key.len())
            .field("tag", &"[REDACTED]")
            .finish()
    }
}

/// One-attempt handshake owner. It cannot be cloned or reconstructed from bytes.
///
/// ```compile_fail
/// use gpsx_crypto::{AwaitingPeer, HandshakeSession};
/// fn require_clone<T: Clone>() {}
/// require_clone::<HandshakeSession<AwaitingPeer>>();
/// ```
///
/// A session cannot be reconstructed from caller-provided key bytes:
///
/// ```compile_fail
/// use gpsx_crypto::{AwaitingPeer, HandshakeSession};
/// let _ = HandshakeSession::<AwaitingPeer>::from_bytes([0_u8; 16], [0_u8; 32]);
/// ```
///
/// The private handshake-key owner cannot be imported or constructed:
///
/// ```compile_fail
/// use gpsx_crypto::HandshakeKey;
/// let _ = HandshakeKey::from([0_u8; 16]);
/// ```
///
/// `finish` consumes the one-attempt session:
///
/// ```compile_fail
/// use gpsx_crypto::{AwaitingPeer, HandshakeSession, UnverifiedPeerKey};
/// fn reuse(session: HandshakeSession<AwaitingPeer>, peer: UnverifiedPeerKey) {
///     let _ = session.finish(peer);
///     let _ = session.local_public_key_and_tag();
/// }
/// ```
///
/// Local and remote traffic owners cannot be inverted:
///
/// ```compile_fail
/// use gpsx_crypto::{LocalTrafficKeys, TrafficKeyPair};
/// fn invert(pair: TrafficKeyPair) -> LocalTrafficKeys { pair.remote }
/// ```
pub struct HandshakeSession<State> {
    handshake_key: HandshakeKey,
    ephemeral: EphemeralScalar,
    local_material: LocalHandshakeMaterial,
    launch_spec_written: bool,
    state: PhantomData<State>,
}

impl HandshakeSession<AwaitingPeer> {
    pub fn new<R>(entropy: &mut R) -> Result<Self, SessionCryptoError>
    where
        R: CryptoRng + RngCore,
    {
        let mut handshake_key = HandshakeKey([0; HANDSHAKE_KEY_SIZE]);
        entropy
            .try_fill_bytes(&mut handshake_key.0)
            .map_err(|_| SessionCryptoError::EntropyUnavailable)?;

        let mut candidate = Zeroizing::new([0_u8; 32]);
        let mut ephemeral = None;
        for _ in 0..MAX_SCALAR_ATTEMPTS {
            entropy
                .try_fill_bytes(candidate.as_mut())
                .map_err(|_| SessionCryptoError::EntropyUnavailable)?;
            if let Some(value) = EphemeralScalar::from_candidate(&candidate) {
                ephemeral = Some(value);
                break;
            }
        }
        let ephemeral = ephemeral.ok_or(SessionCryptoError::ScalarGenerationExhausted)?;
        Self::from_parts(handshake_key, ephemeral)
    }

    fn from_parts(
        handshake_key: HandshakeKey,
        ephemeral: EphemeralScalar,
    ) -> Result<Self, SessionCryptoError> {
        let public_key = PublicKey::from_secret_key(ephemeral.as_ref());
        let public_bytes = public_key.serialize_uncompressed();
        let tag = public_key_tag(&handshake_key.0, &public_bytes)?;
        Ok(Self {
            handshake_key,
            ephemeral,
            local_material: LocalHandshakeMaterial {
                public_key: public_bytes,
                tag,
            },
            launch_spec_written: false,
            state: PhantomData,
        })
    }

    pub fn local_public_key_and_tag(&self) -> LocalHandshakeMaterial {
        self.local_material.clone()
    }

    /// Writes only a fully encrypted and outer-base64 LaunchSpec.
    ///
    /// The raw handshake key has no public egress:
    ///
    /// ```compile_fail
    /// use gpsx_crypto::{AwaitingPeer, HandshakeSession};
    /// use zeroize::Zeroizing;
    /// fn raw(session: &HandshakeSession<AwaitingPeer>) {
    ///     let mut output = Zeroizing::new([0_u8; 16]);
    ///     session.write_launch_spec_handshake_key(&mut output);
    /// }
    /// ```
    pub fn write_encrypted_launch_spec(
        &mut self,
        rpcrypt: &mut SessionRpcrypt,
        spec: LaunchSpec,
        output: &mut [u8],
    ) -> Result<EncryptedLaunchSpecLength, LaunchSpecError> {
        if self.launch_spec_written {
            return Err(LaunchSpecError::AlreadyWritten);
        }
        let length = write_encrypted_launch_spec(&self.handshake_key.0, rpcrypt, spec, output)?;
        self.launch_spec_written = true;
        Ok(length)
    }

    pub fn finish(self, peer: UnverifiedPeerKey) -> Result<TrafficKeyPair, SessionCryptoError> {
        if !self.launch_spec_written {
            return Err(SessionCryptoError::LaunchSpecNotWritten);
        }
        let mut mac = <Hmac<Sha256> as Mac>::new_from_slice(&self.handshake_key.0)
            .map_err(|_| SessionCryptoError::CryptoInitialization)?;
        mac.update(&peer.public_key);
        mac.verify_slice(&peer.tag)
            .map_err(|_| SessionCryptoError::PeerAuthenticationFailed)?;

        if peer.public_key[0] != 0x04 {
            return Err(SessionCryptoError::InvalidPeerPublicKey);
        }
        let peer_public = PublicKey::from_byte_array_uncompressed(peer.public_key)
            .map_err(|_| SessionCryptoError::InvalidPeerPublicKey)?;
        let shared_point =
            Zeroizing::new(shared_secret_point(&peer_public, self.ephemeral.as_ref()));
        let shared_x: &[u8; 32] = shared_point[..32]
            .try_into()
            .map_err(|_| SessionCryptoError::CryptoInitialization)?;
        let local = LocalTrafficKeys::derive(&self.handshake_key.0, shared_x)?;
        let remote = RemoteTrafficKeys::derive(&self.handshake_key.0, shared_x)?;
        Ok(TrafficKeyPair { local, remote })
    }

    #[cfg(test)]
    fn from_test_vectors(
        handshake_key: [u8; HANDSHAKE_KEY_SIZE],
        scalar: [u8; 32],
    ) -> Result<Self, SessionCryptoError> {
        let ephemeral = EphemeralScalar::from_candidate(&scalar)
            .ok_or(SessionCryptoError::ScalarGenerationExhausted)?;
        let mut session = Self::from_parts(HandshakeKey(handshake_key), ephemeral)?;
        session.launch_spec_written = true;
        Ok(session)
    }

    #[cfg(test)]
    pub(crate) fn from_unprepared_test_vectors(
        handshake_key: [u8; HANDSHAKE_KEY_SIZE],
        scalar: [u8; 32],
    ) -> Result<Self, SessionCryptoError> {
        let ephemeral = EphemeralScalar::from_candidate(&scalar)
            .ok_or(SessionCryptoError::ScalarGenerationExhausted)?;
        Self::from_parts(HandshakeKey(handshake_key), ephemeral)
    }
}

redacted_format!(HandshakeSession<AwaitingPeer>, "HandshakeSession");

fn public_key_tag(
    handshake_key: &[u8; HANDSHAKE_KEY_SIZE],
    public_key: &[u8; PUBLIC_KEY_SIZE],
) -> Result<[u8; PUBLIC_KEY_TAG_SIZE], SessionCryptoError> {
    let mut mac = <Hmac<Sha256> as Mac>::new_from_slice(handshake_key)
        .map_err(|_| SessionCryptoError::CryptoInitialization)?;
    mac.update(public_key);
    Ok(mac.finalize().into_bytes().into())
}

#[derive(Clone, Copy)]
enum Direction {
    Local = 2,
    Remote = 3,
}

/// Directionally distinct traffic owners returned only by a finished handshake.
///
/// ```compile_fail
/// use gpsx_crypto::TrafficKeyPair;
/// fn require_clone<T: Clone>() {}
/// require_clone::<TrafficKeyPair>();
/// ```
pub struct TrafficKeyPair {
    pub local: LocalTrafficKeys,
    pub remote: RemoteTrafficKeys,
}

redacted_format!(TrafficKeyPair, "TrafficKeyPair");

struct TrafficCore {
    key_base: [u8; TRAFFIC_KEY_SIZE],
    iv: [u8; TRAFFIC_KEY_SIZE],
    key_gmac_base: [u8; TRAFFIC_KEY_SIZE],
    key_gmac_current: [u8; TRAFFIC_KEY_SIZE],
    key_gmac_epoch: u64,
}

impl TrafficCore {
    fn derive(
        direction: Direction,
        handshake_key: &[u8; HANDSHAKE_KEY_SIZE],
        shared_secret: &[u8; 32],
    ) -> Result<Self, SessionCryptoError> {
        let mut input = Zeroizing::new([0_u8; 21]);
        input[0] = 1;
        input[1] = direction as u8;
        input[2] = 0;
        input[3..19].copy_from_slice(handshake_key);
        input[19] = 1;
        input[20] = 0;
        let mut mac = <Hmac<Sha256> as Mac>::new_from_slice(shared_secret)
            .map_err(|_| SessionCryptoError::CryptoInitialization)?;
        mac.update(input.as_ref());
        let mut digest = mac.finalize().into_bytes();
        let mut key_base = [0_u8; TRAFFIC_KEY_SIZE];
        let mut iv = [0_u8; TRAFFIC_KEY_SIZE];
        key_base.copy_from_slice(&digest[..TRAFFIC_KEY_SIZE]);
        iv.copy_from_slice(&digest[TRAFFIC_KEY_SIZE..]);
        digest.zeroize();
        let key_gmac_base = derive_gmac_key(0, &key_base, &iv)?;
        Ok(Self {
            key_base,
            iv,
            key_gmac_base,
            key_gmac_current: key_gmac_base,
            key_gmac_epoch: 0,
        })
    }

    #[cfg(test)]
    fn derive_test_index(
        index: u8,
        handshake_key: &[u8; HANDSHAKE_KEY_SIZE],
        shared_secret: &[u8; 32],
    ) -> Result<Self, SessionCryptoError> {
        let mut input = Zeroizing::new([0_u8; 21]);
        input[0] = 1;
        input[1] = index;
        input[2] = 0;
        input[3..19].copy_from_slice(handshake_key);
        input[19] = 1;
        input[20] = 0;
        let mut mac = <Hmac<Sha256> as Mac>::new_from_slice(shared_secret)
            .map_err(|_| SessionCryptoError::CryptoInitialization)?;
        mac.update(input.as_ref());
        let mut digest = mac.finalize().into_bytes();
        let mut key_base = [0_u8; TRAFFIC_KEY_SIZE];
        let mut iv = [0_u8; TRAFFIC_KEY_SIZE];
        key_base.copy_from_slice(&digest[..TRAFFIC_KEY_SIZE]);
        iv.copy_from_slice(&digest[TRAFFIC_KEY_SIZE..]);
        digest.zeroize();
        let key_gmac_base = derive_gmac_key(0, &key_base, &iv)?;
        Ok(Self {
            key_base,
            iv,
            key_gmac_base,
            key_gmac_current: key_gmac_base,
            key_gmac_epoch: 0,
        })
    }

    fn crypt_in_place(
        &self,
        key_position: u64,
        buffer: &mut [u8],
    ) -> Result<(), SessionCryptoError> {
        validate_counter_range(&self.iv, key_position, buffer.len())?;
        let mut cipher = Aes128CtrLe::new_from_slices(&self.key_base, &self.iv)
            .map_err(|_| SessionCryptoError::CryptoInitialization)?;
        cipher
            .try_seek(key_position)
            .map_err(|_| SessionCryptoError::PositionOverflow)?;
        cipher.apply_keystream(buffer);
        Ok(())
    }

    fn key_for_epoch(
        &self,
        epoch: u64,
    ) -> Result<Zeroizing<[u8; TRAFFIC_KEY_SIZE]>, SessionCryptoError> {
        if epoch == self.key_gmac_epoch {
            return Ok(Zeroizing::new(self.key_gmac_current));
        }
        if epoch == 0 {
            return Ok(Zeroizing::new(self.key_gmac_base));
        }
        Ok(Zeroizing::new(derive_gmac_key(
            epoch,
            &self.key_gmac_base,
            &self.iv,
        )?))
    }

    fn commit_forward_epoch(&mut self, epoch: u64, key: &[u8; TRAFFIC_KEY_SIZE]) {
        if epoch > self.key_gmac_epoch {
            self.key_gmac_current.zeroize();
            self.key_gmac_current.copy_from_slice(key);
            self.key_gmac_epoch = epoch;
        }
    }

    fn full_gmac(
        &self,
        key: &[u8; TRAFFIC_KEY_SIZE],
        key_position: u64,
        packet: &[u8],
    ) -> Result<Tag, SessionCryptoError> {
        let iv = counter_at(&self.iv, key_position / BLOCK_SIZE)?;
        let cipher = Aes128Gcm16::new_from_slice(key)
            .map_err(|_| SessionCryptoError::CryptoInitialization)?;
        let mut empty = [];
        cipher
            .encrypt_in_place_detached(Nonce::<U16>::from_slice(&iv), packet, &mut empty)
            .map_err(|_| SessionCryptoError::CryptoInitialization)
    }
}

impl Drop for TrafficCore {
    fn drop(&mut self) {
        self.key_base.zeroize();
        self.iv.zeroize();
        self.key_gmac_base.zeroize();
        self.key_gmac_current.zeroize();
        self.key_gmac_epoch.zeroize();
    }
}

redacted_format!(TrafficCore, "TrafficCore");

fn derive_gmac_key(
    epoch: u64,
    key: &[u8; TRAFFIC_KEY_SIZE],
    iv: &[u8; TRAFFIC_KEY_SIZE],
) -> Result<[u8; TRAFFIC_KEY_SIZE], SessionCryptoError> {
    let offset = epoch
        .checked_mul(GMAC_EPOCH_IV_OFFSET)
        .ok_or(SessionCryptoError::PositionOverflow)?;
    let advanced_iv = counter_at(iv, offset)?;
    let mut input = Zeroizing::new([0_u8; 32]);
    input[..16].copy_from_slice(key);
    input[16..].copy_from_slice(&advanced_iv);
    let mut digest = Sha256::digest(input.as_ref());
    let mut output = [0_u8; TRAFFIC_KEY_SIZE];
    for (index, value) in output.iter_mut().enumerate() {
        *value = digest[index] ^ digest[index + TRAFFIC_KEY_SIZE];
    }
    digest.zeroize();
    Ok(output)
}

fn counter_at(
    base: &[u8; TRAFFIC_KEY_SIZE],
    offset: u64,
) -> Result<[u8; TRAFFIC_KEY_SIZE], SessionCryptoError> {
    let value = u128::from_le_bytes(*base)
        .checked_add(u128::from(offset))
        .ok_or(SessionCryptoError::PositionOverflow)?;
    Ok(value.to_le_bytes())
}

fn validate_counter_range(
    iv: &[u8; TRAFFIC_KEY_SIZE],
    key_position: u64,
    length: usize,
) -> Result<(), SessionCryptoError> {
    let final_position = if length == 0 {
        key_position
    } else {
        key_position
            .checked_add(
                u64::try_from(length - 1).map_err(|_| SessionCryptoError::PositionOverflow)?,
            )
            .ok_or(SessionCryptoError::PositionOverflow)?
    };
    counter_at(iv, final_position / BLOCK_SIZE).map(|_| ())
}

/// Monotonic 32-to-64-bit position reconstruction with RFC 1982 half-range behavior.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct KeyPositionReconstructor {
    previous: u64,
}

impl KeyPositionReconstructor {
    pub const fn new() -> Self {
        Self { previous: 0 }
    }

    pub fn request_position(&self, low: u32) -> Result<u64, SessionCryptoError> {
        let previous_low = self.previous as u32;
        let mut high = (self.previous >> 32) as u32;
        if seq32_gt(low, previous_low) && low < previous_low {
            high = high
                .checked_add(1)
                .ok_or(SessionCryptoError::PositionOverflow)?;
        } else if seq32_lt(low, previous_low) && low > previous_low && high > 0 {
            high -= 1;
        }
        Ok((u64::from(high) << 32) | u64::from(low))
    }

    pub fn commit_authenticated(&mut self, position: u64) {
        if position >= self.previous {
            self.previous = position;
        }
    }

    pub const fn highest_authenticated(&self) -> u64 {
        self.previous
    }
}

fn seq32_lt(left: u32, right: u32) -> bool {
    if left == right {
        return false;
    }
    let difference = i64::from(right) - i64::from(left);
    (left < right && difference < (1_i64 << 31)) || (left > right && -difference > (1_i64 << 31))
}

fn seq32_gt(left: u32, right: u32) -> bool {
    if left == right {
        return false;
    }
    let difference = i64::from(right) - i64::from(left);
    (left < right && difference > (1_i64 << 31)) || (left > right && -difference < (1_i64 << 31))
}

/// Unique outbound tag/encryption positions for one packet.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct OutboundPacketPositions {
    pub tag: u64,
    pub encryption: u64,
}

/// Checked allocator implementing `tag=p`, `encryption=p+16`, then aligned advance.
#[derive(Debug, Default, Eq, PartialEq)]
pub struct OutboundKeyPositionAllocator {
    next: u64,
}

impl OutboundKeyPositionAllocator {
    pub const fn new() -> Self {
        Self { next: 0 }
    }

    pub fn allocate(
        &mut self,
        payload_length: usize,
    ) -> Result<OutboundPacketPositions, SessionCryptoError> {
        if payload_length > MAX_PACKET_SIZE {
            return Err(SessionCryptoError::PacketTooLarge);
        }
        let tag = self.next;
        let encryption = tag
            .checked_add(BLOCK_SIZE)
            .ok_or(SessionCryptoError::PositionOverflow)?;
        let end = encryption
            .checked_add(
                u64::try_from(payload_length).map_err(|_| SessionCryptoError::PositionOverflow)?,
            )
            .ok_or(SessionCryptoError::PositionOverflow)?;
        self.next = end
            .checked_add(BLOCK_SIZE - 1)
            .ok_or(SessionCryptoError::PositionOverflow)?
            & !(BLOCK_SIZE - 1);
        Ok(OutboundPacketPositions { tag, encryption })
    }

    pub const fn next_position(&self) -> u64 {
        self.next
    }
}

#[derive(Clone, Copy, Debug, Default)]
struct EpochUsage {
    invocations: u16,
    failures: u8,
}

#[derive(Default)]
struct InvocationState {
    epochs: BTreeMap<u64, EpochUsage>,
    global_failures: u8,
    terminated: bool,
}

impl InvocationState {
    fn ensure_active(&self) -> Result<(), SessionCryptoError> {
        if self.terminated {
            Err(SessionCryptoError::SessionTerminated)
        } else {
            Ok(())
        }
    }

    fn record_invocation(&mut self, epoch: u64) -> Result<(), SessionCryptoError> {
        self.ensure_active()?;
        if !self.epochs.contains_key(&epoch) && self.epochs.len() == MAX_TRACKED_EPOCHS {
            self.terminated = true;
            return Err(SessionCryptoError::InvocationStateExhausted);
        }
        let usage = self.epochs.entry(epoch).or_default();
        if usage.invocations == MAX_EPOCH_INVOCATIONS {
            self.terminated = true;
            return Err(SessionCryptoError::InvocationLimitReached);
        }
        usage.invocations += 1;
        Ok(())
    }

    fn record_global_failure(&mut self) -> SessionCryptoError {
        if self.global_failures < MAX_GLOBAL_FAILURES {
            self.global_failures += 1;
        }
        if self.global_failures == MAX_GLOBAL_FAILURES {
            self.terminated = true;
            SessionCryptoError::FailureLimitReached
        } else {
            SessionCryptoError::PositionOutsideWindow
        }
    }

    fn record_authentication_failure(&mut self, epoch: u64) -> SessionCryptoError {
        if self.global_failures < MAX_GLOBAL_FAILURES {
            self.global_failures += 1;
        }
        let usage = self.epochs.entry(epoch).or_default();
        if usage.failures < MAX_EPOCH_FAILURES {
            usage.failures += 1;
        }
        if self.global_failures == MAX_GLOBAL_FAILURES || usage.failures == MAX_EPOCH_FAILURES {
            self.terminated = true;
            SessionCryptoError::FailureLimitReached
        } else {
            SessionCryptoError::AuthenticationFailed
        }
    }

    fn purge_before(&mut self, minimum_epoch: u64) {
        self.epochs.retain(|epoch, _| *epoch >= minimum_epoch);
    }
}

/// Locally emitted traffic keys and their one-way position allocator.
///
/// ```compile_fail
/// use gpsx_crypto::LocalTrafficKeys;
/// fn require_clone<T: Clone>() {}
/// require_clone::<LocalTrafficKeys>();
/// ```
pub struct LocalTrafficKeys {
    core: TrafficCore,
    allocator: OutboundKeyPositionAllocator,
}

impl LocalTrafficKeys {
    fn derive(
        handshake_key: &[u8; HANDSHAKE_KEY_SIZE],
        shared_secret: &[u8; 32],
    ) -> Result<Self, SessionCryptoError> {
        Ok(Self {
            core: TrafficCore::derive(Direction::Local, handshake_key, shared_secret)?,
            allocator: OutboundKeyPositionAllocator::new(),
        })
    }

    /// Encrypts only `encrypted_range`, then authenticates the complete packet.
    ///
    /// The caller owns the packet layout and must zero protocol MAC/key-position
    /// fields as required before this method. The authenticated packet is capped
    /// at 2,048 bytes; the range must be contained within it.
    /// If an internal error occurs after CTR has transformed the range, the
    /// caller must discard the packet: the range may remain transformed and the
    /// monotonic allocator position is intentionally never rolled back.
    pub fn protect_in_place(
        &mut self,
        packet: &mut [u8],
        encrypted_range: Range<usize>,
    ) -> Result<(OutboundPacketPositions, [u8; GMAC_TAG_SIZE]), SessionCryptoError> {
        if packet.len() > MAX_PACKET_SIZE {
            return Err(SessionCryptoError::PacketTooLarge);
        }
        let encrypted_length = validate_encrypted_range(packet.len(), &encrypted_range)?;
        let positions = self.allocator.allocate(encrypted_length)?;
        let epoch = gmac_epoch(positions.tag);
        self.core
            .crypt_in_place(positions.encryption, &mut packet[encrypted_range])?;
        let key = self.core.key_for_epoch(epoch)?;
        let full_tag = self.core.full_gmac(&key, positions.tag, packet)?;
        self.core.commit_forward_epoch(epoch, &key);
        let mut tag = [0_u8; GMAC_TAG_SIZE];
        tag.copy_from_slice(&full_tag[..GMAC_TAG_SIZE]);
        Ok((positions, tag))
    }
}

redacted_format!(LocalTrafficKeys, "LocalTrafficKeys");

/// Result of an inbound packet operation. Stale packets never consume failure budget.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ReceiveOutcome {
    Authenticated,
    StaleDiscarded,
}

/// Remotely received traffic keys with bounded reconstruction and GMAC state.
///
/// ```compile_fail
/// use gpsx_crypto::RemoteTrafficKeys;
/// fn require_clone<T: Clone>() {}
/// require_clone::<RemoteTrafficKeys>();
/// ```
pub struct RemoteTrafficKeys {
    core: TrafficCore,
    reconstructor: KeyPositionReconstructor,
    has_authenticated_position: bool,
    usage: InvocationState,
}

impl RemoteTrafficKeys {
    fn derive(
        handshake_key: &[u8; HANDSHAKE_KEY_SIZE],
        shared_secret: &[u8; 32],
    ) -> Result<Self, SessionCryptoError> {
        Ok(Self {
            core: TrafficCore::derive(Direction::Remote, handshake_key, shared_secret)?,
            reconstructor: KeyPositionReconstructor::new(),
            has_authenticated_position: false,
            usage: InvocationState::default(),
        })
    }

    pub fn verify_and_decrypt_in_place(
        &mut self,
        low_position: u32,
        packet: &mut [u8],
        encrypted_range: Range<usize>,
        received_tag: &[u8; GMAC_TAG_SIZE],
    ) -> Result<ReceiveOutcome, SessionCryptoError> {
        self.usage.ensure_active()?;
        if packet.len() > MAX_PACKET_SIZE {
            let error = self.usage.record_global_failure();
            return Err(if error == SessionCryptoError::PositionOutsideWindow {
                SessionCryptoError::PacketTooLarge
            } else {
                error
            });
        }
        validate_encrypted_range(packet.len(), &encrypted_range)?;
        let position = self.reconstructor.request_position(low_position)?;
        if self.has_authenticated_position {
            let highest = self.reconstructor.highest_authenticated();
            if position < highest.saturating_sub(MAX_BACKWARD_DISTANCE) {
                return Ok(ReceiveOutcome::StaleDiscarded);
            }
            if position > highest.saturating_add(MAX_FORWARD_DISTANCE) {
                return Err(self.usage.record_global_failure());
            }
        } else if position > MAX_FORWARD_DISTANCE {
            return Err(self.usage.record_global_failure());
        }

        let epoch = gmac_epoch(position);
        self.usage.record_invocation(epoch)?;
        let key = self.core.key_for_epoch(epoch)?;
        let full_tag = self.core.full_gmac(&key, position, packet)?;
        if !bool::from(full_tag[..GMAC_TAG_SIZE].ct_eq(received_tag)) {
            return Err(self.usage.record_authentication_failure(epoch));
        }

        let encryption_position = position
            .checked_add(BLOCK_SIZE)
            .ok_or(SessionCryptoError::PositionOverflow)?;
        self.core
            .crypt_in_place(encryption_position, &mut packet[encrypted_range])?;
        self.core.commit_forward_epoch(epoch, &key);
        if !self.has_authenticated_position || position > self.reconstructor.highest_authenticated()
        {
            self.reconstructor.commit_authenticated(position);
            self.has_authenticated_position = true;
            let minimum = position.saturating_sub(MAX_BACKWARD_DISTANCE);
            self.usage.purge_before(gmac_epoch(minimum));
        }
        Ok(ReceiveOutcome::Authenticated)
    }
}

redacted_format!(RemoteTrafficKeys, "RemoteTrafficKeys");

fn validate_encrypted_range(
    packet_length: usize,
    encrypted_range: &Range<usize>,
) -> Result<usize, SessionCryptoError> {
    if encrypted_range.start > encrypted_range.end || encrypted_range.end > packet_length {
        return Err(SessionCryptoError::InvalidEncryptedRange);
    }
    Ok(encrypted_range.end - encrypted_range.start)
}

const fn gmac_epoch(key_position: u64) -> u64 {
    key_position.saturating_sub(1) / GMAC_EPOCH_POSITIONS
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fmt::Write as _;
    use std::num::NonZeroU32;

    struct ZeroRng;

    impl RngCore for ZeroRng {
        fn next_u32(&mut self) -> u32 {
            0
        }

        fn next_u64(&mut self) -> u64 {
            0
        }

        fn fill_bytes(&mut self, destination: &mut [u8]) {
            destination.fill(0);
        }

        fn try_fill_bytes(&mut self, destination: &mut [u8]) -> Result<(), rand_core::Error> {
            self.fill_bytes(destination);
            Ok(())
        }
    }

    impl CryptoRng for ZeroRng {}

    struct FailingRng;

    impl RngCore for FailingRng {
        fn next_u32(&mut self) -> u32 {
            0
        }

        fn next_u64(&mut self) -> u64 {
            0
        }

        fn fill_bytes(&mut self, _destination: &mut [u8]) {}

        fn try_fill_bytes(&mut self, _destination: &mut [u8]) -> Result<(), rand_core::Error> {
            Err(rand_core::Error::from(NonZeroU32::new(1).unwrap()))
        }
    }

    impl CryptoRng for FailingRng {}

    fn decode<const N: usize>(hex: &str) -> [u8; N] {
        assert_eq!(hex.len(), N * 2);
        let mut output = [0_u8; N];
        for (index, value) in output.iter_mut().enumerate() {
            *value = u8::from_str_radix(&hex[index * 2..index * 2 + 2], 16).unwrap();
        }
        output
    }

    fn decode_vec(hex: &str) -> Vec<u8> {
        assert_eq!(hex.len() % 2, 0);
        (0..hex.len() / 2)
            .map(|index| u8::from_str_radix(&hex[index * 2..index * 2 + 2], 16).unwrap())
            .collect()
    }

    fn encode_hex(bytes: &[u8]) -> String {
        bytes.iter().fold(String::new(), |mut output, byte| {
            write!(output, "{byte:02x}").unwrap();
            output
        })
    }

    fn build_session_oracle() -> Option<String> {
        let mut output = String::new();
        writeln!(output, "{{").ok()?;
        writeln!(output, "  \"schema_version\": 1,").ok()?;
        writeln!(
            output,
            "  \"upstream_commit\": \"c8bea2642d0e95402f373af55584a3b98cd89417\","
        )
        .ok()?;
        writeln!(
            output,
            "  \"mbedtls_commit\": \"8b3f26a5ac38d4fdccbc5c5366229f3e01dafcc0\","
        )
        .ok()?;
        output.push_str(
            "  \"generation\": {\n\
             \x20   \"ecdh\": \"GPSx-authored scratch-only secp256k1 affine arithmetic plus Python stdlib HMAC-SHA-256\",\n\
             \x20   \"traffic\": \"pinned upstream gkcrypt.c executed with pinned Mbed TLS\",\n\
             \x20   \"gmac_full_tag\": \"pinned Mbed TLS AES-GCM full tag; MSB32 checked against upstream chiaki_gkcrypt_gmac\",\n\
             \x20   \"upstream_test_coverage\": [\n\
             \x20     \"gkcrypt/ecdh\",\n\
             \x20     \"gkcrypt/key_stream\",\n\
             \x20     \"gkcrypt/endecrypt\",\n\
             \x20     \"gkcrypt/gmac\",\n\
             \x20     \"gkcrypt/gen_gmac_key\",\n\
             \x20     \"gkcrypt/gmac_split\",\n\
             \x20     \"gkcrypt/gmac_multiple_of_key_refresh\",\n\
             \x20     \"keystate/key_state\",\n\
             \x20     \"keystate/key_state_nocommit\"\n\
             \x20   ],\n\
             \x20   \"mbedtls_ecdh_exclusion\": \"pinned chiaki Mbed TLS set_local_key regenerates the supplied scalar; its deterministic test path is excluded and recorded, not repaired\"\n\
             \x20 },\n\
             \x20 \"ecdh\": {\n\
             \x20   \"handshake_key\": \"fc5d4ba03a353abb6a7fac791b17bb34\",\n\
             \x20   \"local_public_key\": \"04f40af135a4889436cee52b5c73a33ec5ad0be0952f57f4f0ed0c80b0beda7ca643789393a5947e9faa3f6795c9aa09a96325dfe850bfc3f1db62a50abfb0fff7\",\n\
             \x20   \"local_public_key_tag\": \"99b5cbb537180bfc55da437f4476a817c937fe561b8abe0c4112ab71f5a68d29\",\n\
             \x20   \"remote_public_key\": \"04dfef08bba856f2b44b8a0e4f44203f8e493feed43ce93afe5c64677720157c5910156794ae5f024aad0ccefa14150aabee080b141276ea3ec0d565f46877a3ca\",\n\
             \x20   \"remote_public_key_tag\": \"13c589e23b728524a99f968003a181305968f1bbb64dc4a76ccef6794ceb2d98\",\n\
             \x20   \"local_scalar_32\": \"16e75dcbda9855fb6befdd8aa5f16e7f46fde1d2279703601872d84b1538d900\",\n\
             \x20   \"shared_x_32\": \"b81c6146e749738c9630ca13ff71e59b3bf94198d467a5a2bc7804928143ec1d\"\n\
             \x20 },\n\
             \x20 \"keystate\": {\n\
             \x20   \"commit\": [\n",
        );
        let lows: [u64; 8] = [
            0, 0x1337, 0xffff0000, 0x1337, 0xffff1337, 0x50000000, 0xb0000000, 0,
        ];
        let committed: [u64; 8] = [
            0,
            0x1337,
            0xffff0000,
            0x1_00001337,
            0xffff1337,
            0x1_50000000,
            0x1_b0000000,
            0x2_00000000,
        ];
        let not_committed: [u64; 8] = [
            0, 0x1337, 0xffff0000, 0x1337, 0xffff1337, 0x50000000, 0xb0000000, 0,
        ];
        for (index, (low, expected)) in lows.into_iter().zip(committed).enumerate() {
            writeln!(output, "      {{").ok()?;
            writeln!(output, "        \"low\": {low},").ok()?;
            writeln!(output, "        \"expected\": {expected}").ok()?;
            writeln!(
                output,
                "      }}{}",
                if index + 1 == lows.len() { "" } else { "," }
            )
            .ok()?;
        }
        output.push_str("    ],\n    \"no_commit\": [\n");
        for (index, (low, expected)) in lows.into_iter().zip(not_committed).enumerate() {
            writeln!(output, "      {{").ok()?;
            writeln!(output, "        \"low\": {low},").ok()?;
            writeln!(output, "        \"expected\": {expected}").ok()?;
            writeln!(
                output,
                "      }}{}",
                if index + 1 == lows.len() { "" } else { "," }
            )
            .ok()?;
        }
        output.push_str(
            "    ],\n\
             \x20   \"rfc1982_half_range\": {\n\
             \x20     \"previous\": 0,\n\
             \x20     \"low\": 2147483648,\n\
             \x20     \"expected\": 2147483648,\n\
             \x20     \"less\": false,\n\
             \x20     \"greater\": false\n\
             \x20   }\n\
             \x20 },\n\
             \x20 \"directions\": [\n",
        );

        let handshake = decode("fc5d4ba03a353abb6a7fac791b17bb34");
        let shared = decode("b81c6146e749738c9630ca13ff71e59b3bf94198d467a5a2bc7804928143ec1d");
        let packet =
            decode::<32>("475053782d73657373696f6e2d6f7261636c652d626f756e646172792d763100");
        let positions = [
            0,
            1,
            44_999,
            45_000,
            45_001,
            90_000,
            90_001,
            (1_u64 << 32) + 0x420,
            0x6b1de0,
        ];
        for (direction_index, (name, direction)) in
            [("local", Direction::Local), ("remote", Direction::Remote)]
                .into_iter()
                .enumerate()
        {
            let core = TrafficCore::derive(direction, &handshake, &shared).ok()?;
            let index = direction as u8;
            writeln!(output, "    {{").ok()?;
            writeln!(output, "      \"name\": \"{name}\",").ok()?;
            writeln!(output, "      \"index\": {index},").ok()?;
            writeln!(output, "      \"positions\": [").ok()?;
            let mut current_epoch = 0;
            for (position_index, position) in positions.into_iter().enumerate() {
                let epoch = gmac_epoch(position);
                current_epoch = current_epoch.max(epoch);
                let key = core.key_for_epoch(epoch).ok()?;
                let mut ciphertext = packet;
                if core
                    .crypt_in_place(position + BLOCK_SIZE, &mut ciphertext)
                    .is_err()
                {
                    return None;
                }
                let full_tag = core.full_gmac(&key, position, &ciphertext).ok()?;
                writeln!(output, "        {{").ok()?;
                writeln!(output, "          \"position\": {position},").ok()?;
                writeln!(output, "          \"epoch\": {epoch},").ok()?;
                writeln!(
                    output,
                    "          \"encryption_position\": {},",
                    position + BLOCK_SIZE
                )
                .ok()?;
                writeln!(output, "          \"current_epoch\": {current_epoch},").ok()?;
                writeln!(
                    output,
                    "          \"epoch_key\": \"{}\",",
                    encode_hex(key.as_ref())
                )
                .ok()?;
                writeln!(
                    output,
                    "          \"ciphertext\": \"{}\",",
                    encode_hex(&ciphertext)
                )
                .ok()?;
                writeln!(
                    output,
                    "          \"tag_full\": \"{}\",",
                    encode_hex(full_tag.as_slice())
                )
                .ok()?;
                writeln!(
                    output,
                    "          \"tag_msb32\": \"{}\"",
                    encode_hex(&full_tag[..GMAC_TAG_SIZE])
                )
                .ok()?;
                writeln!(
                    output,
                    "        }}{}",
                    if position_index + 1 == positions.len() {
                        ""
                    } else {
                        ","
                    }
                )
                .ok()?;
            }
            writeln!(output, "      ],").ok()?;
            writeln!(
                output,
                "      \"key_base\": \"{}\",",
                encode_hex(&core.key_base)
            )
            .ok()?;
            writeln!(output, "      \"iv\": \"{}\",", encode_hex(&core.iv)).ok()?;
            writeln!(
                output,
                "      \"key_gmac_base\": \"{}\",",
                encode_hex(&core.key_gmac_base)
            )
            .ok()?;
            writeln!(output, "      \"packet\": \"{}\",", encode_hex(&packet)).ok()?;
            writeln!(output, "      \"fold_xor\": {{").ok()?;

            let advanced_iv = counter_at(&core.iv, GMAC_EPOCH_IV_OFFSET).ok()?;
            let mut input = [0_u8; 32];
            input[..16].copy_from_slice(&core.key_gmac_base);
            input[16..].copy_from_slice(&advanced_iv);
            let digest = Sha256::digest(input);
            let folded = derive_gmac_key(1, &core.key_gmac_base, &core.iv).ok()?;
            writeln!(output, "        \"epoch\": 1,").ok()?;
            writeln!(output, "        \"multiplier\": {GMAC_EPOCH_IV_OFFSET},").ok()?;
            writeln!(
                output,
                "        \"advanced_iv\": \"{}\",",
                encode_hex(&advanced_iv),
            )
            .ok()?;
            writeln!(
                output,
                "        \"sha256_left\": \"{}\",",
                encode_hex(&digest[..16]),
            )
            .ok()?;
            writeln!(
                output,
                "        \"sha256_right\": \"{}\",",
                encode_hex(&digest[16..]),
            )
            .ok()?;
            writeln!(
                output,
                "        \"folded_key\": \"{}\"",
                encode_hex(&folded),
            )
            .ok()?;
            writeln!(output, "      }}").ok()?;
            writeln!(
                output,
                "    }}{}",
                if direction_index == 0 { "," } else { "" }
            )
            .ok()?;
        }
        output.push_str("  ],\n  \"arithmetic\": {\n    \"epoch_boundaries\": [\n");
        let boundary_positions = [0_u64, 1, 44_999, 45_000, 45_001, 90_000, 90_001];
        for (index, position) in boundary_positions.into_iter().enumerate() {
            writeln!(output, "      {{").ok()?;
            writeln!(output, "        \"position\": {position},").ok()?;
            writeln!(output, "        \"epoch\": {}", gmac_epoch(position)).ok()?;
            writeln!(
                output,
                "      }}{}",
                if index + 1 == boundary_positions.len() {
                    ""
                } else {
                    ","
                }
            )
            .ok()?;
        }
        let largest_valid_epoch = u64::MAX / GMAC_EPOCH_IV_OFFSET;
        output.push_str(
            "    ],\n\
             \x20   \"counter_cases\": [\n\
             \x20     {\n\
             \x20       \"base_le\": \"feffffffffffffffffffffffffffffff\",\n\
             \x20       \"offset\": 1,\n\
             \x20       \"result_le\": \"ffffffffffffffffffffffffffffffff\"\n\
             \x20     },\n\
             \x20     {\n\
             \x20       \"base_le\": \"ffffffffffffffffffffffffffffffff\",\n\
             \x20       \"offset\": 1,\n\
             \x20       \"error\": \"position_overflow\"\n\
             \x20     }\n\
             \x20   ],\n\
             \x20   \"epoch_multiplier\": {\n",
        );
        writeln!(output, "      \"multiplier\": {GMAC_EPOCH_IV_OFFSET},").ok()?;
        writeln!(
            output,
            "      \"largest_valid_epoch\": {largest_valid_epoch},"
        )
        .ok()?;
        writeln!(
            output,
            "      \"first_overflow_epoch\": {}",
            largest_valid_epoch + 1
        )
        .ok()?;
        output.push_str(
            "    }\n\
             \x20 },\n\
             \x20 \"policy\": {\n\
             \x20   \"forward_window_bytes\": 4194304,\n\
             \x20   \"backward_window_bytes\": 262144,\n\
             \x20   \"simulated_forward_gaps\": [\n",
        );
        let gaps = [65_536_u64, 262_144, 1_048_576, 4_194_304, 4_194_305];
        for (index, bytes) in gaps.into_iter().enumerate() {
            writeln!(output, "      {{").ok()?;
            writeln!(output, "        \"bytes\": {bytes},").ok()?;
            writeln!(
                output,
                "        \"accepted\": {}",
                if bytes <= 4_194_304 { "true" } else { "false" }
            )
            .ok()?;
            writeln!(
                output,
                "      }}{}",
                if index + 1 == gaps.len() { "" } else { "," }
            )
            .ok()?;
        }
        output.push_str(
            "    ],\n\
             \x20   \"per_epoch_invocation_limit\": 2048,\n\
             \x20   \"global_failure_limit\": 32\n\
             \x20 }\n\
             }\n",
        );
        Some(output)
    }

    fn session_oracle_matches(fixture: &str) -> bool {
        build_session_oracle().is_some_and(|expected| fixture == expected)
    }

    fn protect_at(core: &TrafficCore, position: u64, clear: &[u8]) -> (Vec<u8>, [u8; 4]) {
        let mut encrypted = clear.to_vec();
        core.crypt_in_place(position + BLOCK_SIZE, &mut encrypted)
            .unwrap();
        let epoch = gmac_epoch(position);
        let key = core.key_for_epoch(epoch).unwrap();
        let full_tag = core.full_gmac(&key, position, &encrypted).unwrap();
        let mut tag = [0_u8; 4];
        tag.copy_from_slice(&full_tag[..4]);
        (encrypted, tag)
    }

    #[test]
    fn closed_session_oracle_binds_every_field_and_rejects_mutations() {
        let fixture = include_str!("../../../tests/fixtures/session_crypto_oracle_v1.json");
        assert_eq!(build_session_oracle().as_deref(), Some(fixture));

        let mutations = [
            fixture.replacen(
                "\"tag_full\": \"9c37f48b8e41c6a2a2a785938751810d\"",
                "\"tag_full\": \"0c37f48b8e41c6a2a2a785938751810d\"",
                1,
            ),
            fixture.replacen("\"position\": 45000,", "\"position\": 45002,", 1),
            fixture.replacen(
                "\"remote_public_key_tag\": \"13c589",
                "\"remote_public_key_tag\": \"03c589",
                1,
            ),
            fixture.replacen(
                "\"local_public_key\": \"04f40a",
                "\"local_public_key\": \"04e40a",
                1,
            ),
            fixture.replacen("\"packet\": \"47505378", "\"packet\": \"47505379", 1),
            fixture.replacen(
                "\"first_overflow_epoch\": 410749144371177",
                "\"first_overflow_epoch\": 410749144371178",
                1,
            ),
            fixture.replacen(
                "\"bytes\": 4194305,\n        \"accepted\": false",
                "\"bytes\": 4194305,\n        \"accepted\": true",
                1,
            ),
            fixture.replacen("\"offset\": 1,", "\"offset\": 2,", 1),
            format!("{fixture}{{}}\n"),
        ];
        for mutation in mutations {
            assert_ne!(mutation, fixture);
            assert!(!session_oracle_matches(&mutation));
        }

        for field in [
            "schema_version",
            "upstream_commit",
            "mbedtls_commit",
            "generation",
            "ecdh",
            "keystate",
            "directions",
            "arithmetic",
            "policy",
        ] {
            let original = format!("  \"{field}\":");
            let replacement = format!("  \"{field}_mutated\":");
            let mutation = fixture.replacen(&original, &replacement, 1);
            assert_ne!(mutation, fixture, "missing top-level field {field}");
            assert!(!session_oracle_matches(&mutation));
        }
    }

    #[test]
    fn pinned_ecdh_vector_authenticates_and_derives() {
        let handshake = decode("fc5d4ba03a353abb6a7fac791b17bb34");
        let local_scalar =
            decode("16e75dcbda9855fb6befdd8aa5f16e7f46fde1d2279703601872d84b1538d900");
        let local_public = decode(
            "04f40af135a4889436cee52b5c73a33ec5ad0be0952f57f4f0ed0c80b0beda7ca643789393a5947e9faa3f6795c9aa09a96325dfe850bfc3f1db62a50abfb0fff7",
        );
        let local_tag = decode("99b5cbb537180bfc55da437f4476a817c937fe561b8abe0c4112ab71f5a68d29");
        let remote_public = decode::<65>(
            "04dfef08bba856f2b44b8a0e4f44203f8e493feed43ce93afe5c64677720157c5910156794ae5f024aad0ccefa14150aabee080b141276ea3ec0d565f46877a3ca",
        );
        let remote_tag =
            decode::<32>("13c589e23b728524a99f968003a181305968f1bbb64dc4a76ccef6794ceb2d98");
        let session = HandshakeSession::from_test_vectors(handshake, local_scalar).unwrap();
        let material = session.local_public_key_and_tag();
        assert_eq!(material.public_key(), &local_public);
        assert_eq!(material.tag(), &local_tag);
        let remote_point = PublicKey::from_byte_array_uncompressed(remote_public).unwrap();
        let local_secret = SecretKey::from_secret_bytes(local_scalar).unwrap();
        let shared_point = shared_secret_point(&remote_point, &local_secret);
        assert_eq!(
            &shared_point[..32],
            &decode::<32>("b81c6146e749738c9630ca13ff71e59b3bf94198d467a5a2bc7804928143ec1d")
        );
        let pair = session
            .finish(UnverifiedPeerKey::new(&remote_public, &remote_tag).unwrap())
            .unwrap();
        assert_eq!(
            pair.local.core.key_base,
            decode("0d62d1faa5c09109decf0c06bbc11407")
        );
        assert_eq!(
            pair.local.core.iv,
            decode("4b33277afdb3c138e7225b4d7e8d4860")
        );
        assert_eq!(
            pair.remote.core.key_base,
            decode("374c6ca1395d2e0036bc22cb6e9ce345")
        );
        assert_eq!(
            pair.remote.core.iv,
            decode("c7e84527683b0a404732bb35a669c32c")
        );
        assert_eq!(format!("{pair:?}"), "TrafficKeyPair([REDACTED])");
    }

    #[test]
    fn entropy_failure_and_eight_scalar_rejections_are_classified() {
        assert_eq!(
            HandshakeSession::new(&mut FailingRng).unwrap_err(),
            SessionCryptoError::EntropyUnavailable
        );
        assert_eq!(
            HandshakeSession::new(&mut ZeroRng).unwrap_err(),
            SessionCryptoError::ScalarGenerationExhausted
        );
    }

    #[test]
    fn peer_mutations_fail_before_key_derivation() {
        let handshake = decode("fc5d4ba03a353abb6a7fac791b17bb34");
        let scalar = decode("16e75dcbda9855fb6befdd8aa5f16e7f46fde1d2279703601872d84b1538d900");
        let public = decode::<65>(
            "04dfef08bba856f2b44b8a0e4f44203f8e493feed43ce93afe5c64677720157c5910156794ae5f024aad0ccefa14150aabee080b141276ea3ec0d565f46877a3ca",
        );
        let tag = decode::<32>("13c589e23b728524a99f968003a181305968f1bbb64dc4a76ccef6794ceb2d98");
        for index in 0..public.len() {
            let mut changed = public;
            changed[index] ^= 1;
            let error = HandshakeSession::from_test_vectors(handshake, scalar)
                .unwrap()
                .finish(UnverifiedPeerKey::new(&changed, &tag).unwrap())
                .unwrap_err();
            assert_eq!(error, SessionCryptoError::PeerAuthenticationFailed);
        }
        for index in 0..tag.len() {
            let mut changed = tag;
            changed[index] ^= 1;
            let error = HandshakeSession::from_test_vectors(handshake, scalar)
                .unwrap()
                .finish(UnverifiedPeerKey::new(&public, &changed).unwrap())
                .unwrap_err();
            assert_eq!(error, SessionCryptoError::PeerAuthenticationFailed);
        }
    }

    #[test]
    fn non_uncompressed_sec1_prefixes_are_rejected_after_authentication() {
        let handshake = decode("fc5d4ba03a353abb6a7fac791b17bb34");
        let scalar = decode("16e75dcbda9855fb6befdd8aa5f16e7f46fde1d2279703601872d84b1538d900");
        let public = decode::<65>(
            "04dfef08bba856f2b44b8a0e4f44203f8e493feed43ce93afe5c64677720157c5910156794ae5f024aad0ccefa14150aabee080b141276ea3ec0d565f46877a3ca",
        );
        for prefix in [0x02, 0x03, 0x06, 0x07] {
            let mut changed = public;
            changed[0] = prefix;
            let valid_tag = public_key_tag(&handshake, &changed).unwrap();
            assert_eq!(
                HandshakeSession::from_test_vectors(handshake, scalar)
                    .unwrap()
                    .finish(UnverifiedPeerKey::new(&changed, &valid_tag).unwrap())
                    .unwrap_err(),
                SessionCryptoError::InvalidPeerPublicKey
            );
        }
    }

    #[test]
    fn pinned_traffic_derivation_and_ctr_vectors_match() {
        let handshake = decode("83cf931a6aa769a6c4485d19c15ccc52");
        let secret = decode("73c8d549c4d9db502ec044ea33648c6ac9f36c41b6a0504fe093defb619b0973");
        let core = TrafficCore::derive_test_index(42, &handshake, &secret).unwrap();
        assert_eq!(core.key_base, decode("08816fa2e5558961d5a286d90eec5b8c"));
        assert_eq!(core.iv, decode("2ae1bb3d84dc9aa9c352a4cf3ffb8b72"));
        let mut stream = [0_u8; 32];
        core.crypt_in_place(0x30, &mut stream).unwrap();
        assert_eq!(
            stream,
            decode("0f6d89855ba786745ba1fe5c81196cd554c41ccaf6e934a4892698b06212b31a")
        );
        let mut high = [0_u8; 32];
        core.crypt_in_place((1_u64 << 32) + 0x420, &mut high)
            .unwrap();
        assert_eq!(
            high,
            decode("14319ff8be0817a4fc28490b1e5f7b7efae914dec6dfe75dd69c753f9462d52c")
        );
    }

    #[test]
    fn pinned_unaligned_ctr_vector_round_trips() {
        let handshake = decode("14f1e6946c5dcea8b7aa4850f64d21ac");
        let secret = decode("0ceb7709834d7afc50b8468cc63c1e7c4e4a88934280c128e61ee9d41b8c6936");
        let core = TrafficCore::derive_test_index(42, &handshake, &secret).unwrap();
        assert_eq!(core.key_base, decode("277aa51dacd15f0e5412face0dc4636a"));
        assert_eq!(core.iv, decode("ef2040c2153c0266321f42bbf450344d"));
        let clear = decode::<37>(
            "4e619f945d4b8ebd2a154d036acd49569cc75ce3e700179a38d9695345f90cb58c05650f70",
        );
        let expected = decode::<37>(
            "23f48dd8aaf9589bb1944fad2b8daa8d2588faf8b6d417f45f78ecf54e3720b0768107679a",
        );
        let mut actual = clear;
        core.crypt_in_place(0x11, &mut actual).unwrap();
        assert_eq!(actual, expected);
        core.crypt_in_place(0x11, &mut actual).unwrap();
        assert_eq!(actual, clear);
    }

    #[test]
    fn nist_aes_gcm_and_ctr_known_answers_match() {
        type StandardGcm = aes_gcm::Aes128Gcm;
        let gcm = StandardGcm::new_from_slice(&[0_u8; 16]).unwrap();
        let mut empty = [];
        let tag = gcm
            .encrypt_in_place_detached(aes_gcm::Nonce::from_slice(&[0_u8; 12]), &[], &mut empty)
            .unwrap();
        assert_eq!(
            tag.as_slice(),
            &decode::<16>("58e2fccefa7e3061367f1d57a4e7455a")
        );

        type NistCtr = ctr::Ctr128BE<Aes128>;
        let key = decode::<16>("2b7e151628aed2a6abf7158809cf4f3c");
        let counter = decode::<16>("f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff");
        let mut block = decode::<16>("6bc1bee22e409f96e93d7e117393172a");
        NistCtr::new_from_slices(&key, &counter)
            .unwrap()
            .apply_keystream(&mut block);
        assert_eq!(block, decode("874d6191b620e3261bef6864990db6ce"));
    }

    #[test]
    fn pinned_epoch_key_vector_matches() {
        let key = decode("beeba0f03d05707d3ac73cd732b94801");
        let iv = decode("e87187e763e0df463dc2024a2cd29c45");
        assert_eq!(
            derive_gmac_key(1, &key, &iv).unwrap(),
            decode("e3db92d9ddd36899aefd9b15e1a6878b")
        );
        assert_eq!(gmac_epoch(44_999), 0);
        assert_eq!(gmac_epoch(45_000), 0);
        assert_eq!(gmac_epoch(45_001), 1);
    }

    #[test]
    fn upstream_gmac_vectors_match() {
        fn core_with_current_gmac_key(key: [u8; 16], iv: [u8; 16]) -> TrafficCore {
            TrafficCore {
                key_base: [0; 16],
                iv,
                key_gmac_base: [0; 16],
                key_gmac_current: key,
                key_gmac_epoch: 0,
            }
        }
        fn tag(core: &TrafficCore, position: u64, packet: &[u8]) -> [u8; 4] {
            let epoch_key = core.key_for_epoch(gmac_epoch(position)).unwrap();
            let full = core.full_gmac(&epoch_key, position, packet).unwrap();
            full[..4].try_into().unwrap()
        }

        let packet = decode_vec(
            "0300180019000250210500000000000069a000f9f89824da560b74ec6815d31f8543aad3cdb8502e90a4b085f3be9a4797c1ab5fa5fd625c261a54367230c32be02c5a8607f5dc1255b48e175be47f965586f0fb45e2f8e87d5f3a8bb0646c54f68c4ea412a3267f225b1998bad4ad9f5f63af1f978c1749a33071d2b0492c75a289da50fd9b0841982d8c0b9279ae602383ef5cdace71082d0b17625648c5e20f221ddcccc282a68eec1e040c08aaa2806c4fd36fc19b769a58aa2556eff2fd77f0324e83e23025a55af1dfc875a2b62bfdacbc6309a1cc2bf546ef49838f0bc147ac5a002eec1cd00a66b5ee9eadc9642dcb980f43a6e14ce79f2dab9401d7528419",
        );
        let core = core_with_current_gmac_key(
            decode("b64b1e653fbba7ab80b31e5a324decc0"),
            decode("7cc8d0199edfd8c3b50f32ee36336a5a"),
        );
        assert_eq!(tag(&core, 0x69a0, &packet), decode("6f811097"));
        assert_eq!(
            tag(&core, (1_u64 << 32) + 0x420, &packet),
            decode("4a309810")
        );

        let split_packet = decode_vec(
            "02001e00090020080103000000000000adf0024e002c6f3b6c2e4a279c503484ea37c776ab92304361c71dcbc43594cc44e2fa8986475ec201394c9f67b36e354497f428e25112daf52c431c49462c53e3e97661872b7ae32fbb9f4d668a31bc649d060d4add4221733c9721845365537af4577761ae0c7a4290fc6bbb386e52b6e5260fd22af15650dd65149ee3b570675547cd8ec1baf672b1b4111b25d43d19b5f5dd2fefd6d1afb4dc93e53cc3a464b3add3fc238486f7f910df156e0c6e439a633bb53805f94a684afb95949d31fd843143850d8ebb66ca4d3b0a75e5ebded5f88285b2ccdb709ea9e2b0ac760128ea1039b7477882c9a42416c3e51602469207db3ab0d0ddc015dc05c761e57d82d04c1744b4fa28d894c1f4d0b3506a2581987b4967a219b241e5d2df9a4fc960ffb291e85f7767ffc0a17657eb6134578075db03c553f5b3a861df0fb6e6c439a0cde9a632b8ba469c431649659d7187f337e5e0c6df5e4cc5209b16ed132d63792bd0514400448c7c2cc68220caca1f7fea71b7845b8c945b92b6efbe5f083d05b2fbcb5a01d416718d9ef58dae804090feef5da474f3e34a9a678ba6b55918614f425f2b7439a8305d8df1ad254f41ca27da07754946d781aa2761569c294090220acd3a7bb48596078013123f2527a0f626bdd1b095bd26ba49a6810731d3e125daab71b9651d13ab825295b362a64848b8d8552698a1c957661cacb1c75ffa44c6d819018d411fc70f891def349a68778c",
        );
        let split_core = core_with_current_gmac_key(
            decode("35958187ebcb63549000d7d5bdf4c5f4"),
            decode("b8defca448f04c3fd5940e1c046463b1"),
        );
        assert_eq!(tag(&split_core, 0xadf0, &split_packet), decode("d66a07b7"));
        assert_eq!(
            tag(&split_core, (1_u64 << 32) + 0x420, &split_packet),
            decode("f017953c")
        );

        let refresh_core = TrafficCore::derive_test_index(
            3,
            &decode("7058375091ead1377158ecb30bea2387"),
            &decode("3c3af0ecd6331bb16d244f4819de063dc70eac9570ac249286a724d07a375552"),
        )
        .unwrap();
        let refresh_packet = decode_vec(
            "0311a411a5000250210500000000006b1de00083f1c47971e4677cccb792078bba8a08d662f5075d4a2014b3e70e5c77fd6d60a3c548a9c061f984f63762acbc1f5de44090547137ab337eaaa5f4c40903f4b6a12f022753c6a5b19c8fb469d65c7f795f1447cca88b37f7776d01f45b59ca96730a20f37ad1d27ffeb666f4db10aff307ee498aa1a573d823830e0c0c3a49dcd80cc8e4b6f23c6d256a7da01c3a8e0f3f2547118967c4f9acc7e29374c6fbdd4857ae53e2f4178062f309d8d47a466b08769c2981914e57af70b63278989e85f2e36915afd432540fd4a2fcf536dc0645f406a3382102fdf05175eab1f68e3935fd7574adbc3efbee7d0f2d76befdee",
        );
        assert_eq!(
            tag(&refresh_core, 0x6b1de0, &refresh_packet),
            decode("20cca5f1")
        );
    }

    #[test]
    fn key_state_vectors_and_half_range_edge_match() {
        let mut state = KeyPositionReconstructor::new();
        for (low, expected) in [
            (0, 0),
            (0x1337, 0x1337),
            (0xffff0000, 0xffff0000),
            (0x1337, 0x1_00001337),
            (0xffff1337, 0xffff1337),
            (0x50000000, 0x1_50000000),
            (0xb0000000, 0x1_b0000000),
            (0, 0x2_00000000),
        ] {
            let result = state.request_position(low).unwrap();
            assert_eq!(result, expected);
            state.commit_authenticated(result);
        }
        let edge = KeyPositionReconstructor { previous: 0 };
        assert_eq!(edge.request_position(0x80000000).unwrap(), 0x80000000);
        assert!(!seq32_lt(0, 0x80000000));
        assert!(!seq32_gt(0, 0x80000000));
    }

    #[test]
    fn outbound_allocator_never_repeats_tag_blocks() {
        let mut allocator = OutboundKeyPositionAllocator::new();
        let mut previous_block = None;
        for length in 0..=MAX_PACKET_SIZE {
            let positions = allocator.allocate(length).unwrap();
            assert_eq!(positions.encryption, positions.tag + 16);
            let block = positions.tag / 16;
            assert_ne!(previous_block, Some(block));
            previous_block = Some(block);
        }
    }

    #[test]
    fn local_and_remote_round_trip_and_reject_forgery() {
        let handshake = [0x42; 16];
        let secret = [0x24; 32];
        let mut local = LocalTrafficKeys::derive(&handshake, &secret).unwrap();
        let mut remote = RemoteTrafficKeys::derive(&handshake, &secret).unwrap();
        // The remote direction uses a distinct key; use matching local-direction
        // cores to test the packet state machine without weakening direction types.
        remote.core = TrafficCore::derive(Direction::Local, &handshake, &secret).unwrap();
        let mut clear = vec![0_u8; 16];
        clear[..8].copy_from_slice(b"TAKION\0\0");
        clear.extend_from_slice(b"bounded synthetic payload");
        let mut encrypted = clear.clone();
        let encrypted_range = 16..encrypted.len();
        let (positions, tag) = local
            .protect_in_place(&mut encrypted, encrypted_range.clone())
            .unwrap();
        assert_eq!(&encrypted[..16], &clear[..16]);
        assert_ne!(&encrypted[16..], &clear[16..]);
        assert_eq!(
            remote
                .verify_and_decrypt_in_place(
                    positions.tag as u32,
                    &mut encrypted,
                    encrypted_range,
                    &tag,
                )
                .unwrap(),
            ReceiveOutcome::Authenticated
        );
        assert_eq!(encrypted, clear);

        let mut forged = vec![0_u8; 16];
        let bad_tag = [0_u8; 4];
        assert_eq!(
            remote.verify_and_decrypt_in_place(16, &mut forged, 16..16, &bad_tag),
            Err(SessionCryptoError::AuthenticationFailed)
        );

        let mut header_mutation = clear.clone();
        let mutation_range = 16..header_mutation.len();
        let (mutation_positions, mutation_tag) = local
            .protect_in_place(&mut header_mutation, mutation_range.clone())
            .unwrap();
        let encrypted_payload = header_mutation[mutation_range.clone()].to_vec();
        header_mutation[1] ^= 1;
        assert_eq!(
            remote.verify_and_decrypt_in_place(
                mutation_positions.tag as u32,
                &mut header_mutation,
                mutation_range.clone(),
                &mutation_tag,
            ),
            Err(SessionCryptoError::AuthenticationFailed)
        );
        assert_eq!(&header_mutation[mutation_range], encrypted_payload);
    }

    #[test]
    fn invocation_and_failure_limits_are_terminal() {
        let handshake = [0x11; 16];
        let secret = [0x22; 32];
        let core = TrafficCore::derive(Direction::Local, &handshake, &secret).unwrap();
        let mut remote = RemoteTrafficKeys::derive(&handshake, &secret).unwrap();
        remote.core = TrafficCore::derive(Direction::Local, &handshake, &secret).unwrap();
        let (_, tag) = protect_at(&core, 0, &[]);
        for _ in 0..MAX_EPOCH_INVOCATIONS {
            assert_eq!(
                remote
                    .verify_and_decrypt_in_place(0, &mut [], 0..0, &tag)
                    .unwrap(),
                ReceiveOutcome::Authenticated
            );
        }
        assert_eq!(
            remote.verify_and_decrypt_in_place(0, &mut [], 0..0, &tag),
            Err(SessionCryptoError::InvocationLimitReached)
        );
        assert_eq!(
            remote.verify_and_decrypt_in_place(0, &mut [], 0..0, &tag),
            Err(SessionCryptoError::SessionTerminated)
        );

        let mut remote = RemoteTrafficKeys::derive(&handshake, &secret).unwrap();
        for attempt in 1..=MAX_EPOCH_FAILURES {
            let result = remote.verify_and_decrypt_in_place(0, &mut [], 0..0, &[0; 4]);
            if attempt < MAX_EPOCH_FAILURES {
                assert_eq!(result, Err(SessionCryptoError::AuthenticationFailed));
            } else {
                assert_eq!(result, Err(SessionCryptoError::FailureLimitReached));
            }
        }
        assert_eq!(
            remote.verify_and_decrypt_in_place(0, &mut [], 0..0, &[0; 4]),
            Err(SessionCryptoError::SessionTerminated)
        );
    }

    #[test]
    fn oversized_receive_packets_fail_before_gmac_and_exhaust_global_budget() {
        let handshake = [0x31; 16];
        let secret = [0x42; 32];
        let mut remote = RemoteTrafficKeys::derive(&handshake, &secret).unwrap();
        let initial_gmac_epoch = remote.core.key_gmac_epoch;

        for attempt in 1..=MAX_GLOBAL_FAILURES {
            let mut packet = [0x5a; MAX_PACKET_SIZE + 1];
            let result = remote.verify_and_decrypt_in_place(0, &mut packet, 0..0, &[0; 4]);
            if attempt < MAX_GLOBAL_FAILURES {
                assert_eq!(result, Err(SessionCryptoError::PacketTooLarge));
            } else {
                assert_eq!(result, Err(SessionCryptoError::FailureLimitReached));
            }
            assert!(remote.usage.epochs.is_empty());
            assert_eq!(remote.core.key_gmac_epoch, initial_gmac_epoch);
            assert_eq!(packet, [0x5a; MAX_PACKET_SIZE + 1]);
        }

        assert_eq!(
            remote.verify_and_decrypt_in_place(0, &mut [0; MAX_PACKET_SIZE + 1], 0..0, &[0; 4]),
            Err(SessionCryptoError::SessionTerminated)
        );
    }

    #[test]
    fn one_million_real_packets_cross_one_thousand_epochs_without_termination() {
        let handshake = [0x53; 16];
        let secret = [0xa7; 32];
        let mut local = LocalTrafficKeys::derive(&handshake, &secret).unwrap();
        let mut remote = RemoteTrafficKeys::derive(&handshake, &secret).unwrap();
        remote.core = TrafficCore::derive(Direction::Local, &handshake, &secret).unwrap();
        let mut final_epoch = 0;

        for packet_index in 0_u32..1_000_000 {
            let packet_length = 48 + usize::try_from(packet_index % 3).unwrap() * 16;
            let mut packet = [0_u8; 80];
            packet[..4].copy_from_slice(&packet_index.to_be_bytes());
            packet[16..packet_length].fill(packet_index as u8);
            let encrypted_range = 16..packet_length;
            let (positions, tag) = local
                .protect_in_place(&mut packet[..packet_length], encrypted_range.clone())
                .unwrap();
            final_epoch = gmac_epoch(positions.tag);
            assert_eq!(
                remote
                    .verify_and_decrypt_in_place(
                        positions.tag as u32,
                        &mut packet[..packet_length],
                        encrypted_range.clone(),
                        &tag,
                    )
                    .unwrap(),
                ReceiveOutcome::Authenticated
            );
            assert_eq!(&packet[..4], &packet_index.to_be_bytes());
            assert!(packet[4..16].iter().all(|byte| *byte == 0));
            assert!(
                packet[encrypted_range]
                    .iter()
                    .all(|byte| *byte == packet_index as u8)
            );
        }
        assert!(final_epoch >= 1_000);
        assert!(remote.usage.epochs.len() <= 8);
        assert!(!remote.usage.terminated);
        assert_eq!(remote.usage.global_failures, 0);
    }

    #[test]
    fn receive_windows_accept_loss_bursts_and_bound_attacker_work() {
        let handshake = [0x31; 16];
        let secret = [0x73; 32];
        let core = TrafficCore::derive(Direction::Local, &handshake, &secret).unwrap();
        let mut remote = RemoteTrafficKeys::derive(&handshake, &secret).unwrap();
        remote.core = TrafficCore::derive(Direction::Local, &handshake, &secret).unwrap();

        for position in [0_u64, 64 * 1_024, 256 * 1_024, 1_024 * 1_024] {
            let clear = b"loss-burst replay fixture";
            let (mut packet, tag) = protect_at(&core, position, clear);
            let packet_length = packet.len();
            assert_eq!(
                remote
                    .verify_and_decrypt_in_place(
                        position as u32,
                        &mut packet,
                        0..packet_length,
                        &tag,
                    )
                    .unwrap(),
                ReceiveOutcome::Authenticated
            );
            assert_eq!(packet, clear);
        }

        let failures_before_stale = remote.usage.global_failures;
        assert_eq!(
            remote
                .verify_and_decrypt_in_place(0, &mut [], 0..0, &[0; 4])
                .unwrap(),
            ReceiveOutcome::StaleDiscarded
        );
        assert_eq!(remote.usage.global_failures, failures_before_stale);

        let far = (1_024 * 1_024 + MAX_FORWARD_DISTANCE + 16) as u32;
        let invocations_before = remote
            .usage
            .epochs
            .values()
            .map(|usage| u64::from(usage.invocations))
            .sum::<u64>();
        for attempt in 1..=MAX_GLOBAL_FAILURES {
            let result = remote.verify_and_decrypt_in_place(far, &mut [], 0..0, &[0; 4]);
            if attempt < MAX_GLOBAL_FAILURES {
                assert_eq!(result, Err(SessionCryptoError::PositionOutsideWindow));
            } else {
                assert_eq!(result, Err(SessionCryptoError::FailureLimitReached));
            }
        }
        let invocations_after = remote
            .usage
            .epochs
            .values()
            .map(|usage| u64::from(usage.invocations))
            .sum::<u64>();
        assert_eq!(invocations_after, invocations_before);
        assert_eq!(
            remote.verify_and_decrypt_in_place(far, &mut [], 0..0, &[0; 4]),
            Err(SessionCryptoError::SessionTerminated)
        );
    }

    #[test]
    fn counter_and_allocator_overflow_are_rejected() {
        assert_eq!(
            counter_at(&[0xff; 16], 1),
            Err(SessionCryptoError::PositionOverflow)
        );
        let mut allocator = OutboundKeyPositionAllocator {
            next: u64::MAX - 15,
        };
        assert_eq!(
            allocator.allocate(1),
            Err(SessionCryptoError::PositionOverflow)
        );
        assert_eq!(
            derive_gmac_key(u64::MAX, &[0; 16], &[0; 16]),
            Err(SessionCryptoError::PositionOverflow)
        );
        let overflowing_core = TrafficCore {
            key_base: [0; 16],
            iv: [0xff; 16],
            key_gmac_base: [0; 16],
            key_gmac_current: [0; 16],
            key_gmac_epoch: 0,
        };
        assert_eq!(
            overflowing_core.crypt_in_place(16, &mut [0]),
            Err(SessionCryptoError::PositionOverflow)
        );
    }

    #[test]
    fn packet_ranges_and_source_structure_are_bounded() {
        let handshake = [0x91; 16];
        let secret = [0x19; 32];
        let mut local = LocalTrafficKeys::derive(&handshake, &secret).unwrap();
        assert_eq!(
            local.protect_in_place(&mut [0; 8], 4..9),
            Err(SessionCryptoError::InvalidEncryptedRange)
        );
        assert_eq!(
            local.protect_in_place(&mut [0; MAX_PACKET_SIZE + 1], 0..0),
            Err(SessionCryptoError::PacketTooLarge)
        );

        let source = include_str!("session.rs");
        let standard_thread = ["std::", "thread"].concat();
        let thread_spawn = ["thread::", "spawn"].concat();
        let vector_deque = ["Vec", "Deque"].concat();
        assert!(!source.contains(&standard_thread));
        assert!(!source.contains(&thread_spawn));
        assert!(!source.contains(&vector_deque));
        let encrypted_egress = ["pub fn write_encrypted_", "launch_spec("].concat();
        assert_eq!(source.matches(&encrypted_egress).count(), 1);
        let raw_key_egress = ["pub fn write_launch_spec_", "handshake_key("].concat();
        assert!(!source.contains(&raw_key_egress));
        let general_accessor = ["pub fn handshake_", "key("].concat();
        let byte_constructor = ["pub fn from_", "bytes("].concat();
        let rebind_finish = ["finish_", "with_key"].concat();
        assert!(!source.contains(&general_accessor));
        assert!(!source.contains(&byte_constructor));
        assert!(!source.contains(&rebind_finish));
    }

    #[test]
    fn all_secret_owners_are_redacted() {
        let session = HandshakeSession::from_test_vectors([7; 16], {
            let mut scalar = [0_u8; 32];
            scalar[31] = 1;
            scalar
        })
        .unwrap();
        let text = format!("{session:?}");
        assert!(text.contains("[REDACTED]"));
        assert!(!text.contains("070707"));
    }
}
