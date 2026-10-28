// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

#![forbid(unsafe_code)]

//! Pure registration and authenticated-session cryptographic behavior for GPSx.
//!
//! This crate performs no I/O, platform entropy acquisition, transport,
//! credential storage, wake-up, JNI, Android, Windows, or GP520 work. Platform
//! adapters inject a CSPRNG and own all network and lifecycle behavior.

mod launch_spec;
mod registration;
mod rpcrypt_tables;
mod session;

pub use launch_spec::{
    EncryptedLaunchSpecLength, LaunchSpec, LaunchSpecError, MAX_ENCRYPTED_LAUNCH_SPEC_SIZE,
    MAX_LAUNCH_SPEC_PLAINTEXT_SIZE, SessionCodec, SessionMorning, SessionNonce, SessionRpcrypt,
    SessionTarget,
};

pub use registration::{
    AeropauseTableOffset, CipherTableOffset, RegistrationAmbassador, RegistrationContext,
    RegistrationCryptoError, RegistrationMaterial, RegistrationPin, RegistrationResponseDecryptor,
    RegistrationTarget,
};
pub use session::{
    AwaitingPeer, HandshakeSession, KeyPositionReconstructor, LocalHandshakeMaterial,
    LocalTrafficKeys, OutboundKeyPositionAllocator, OutboundPacketPositions, ReceiveOutcome,
    RemoteTrafficKeys, SessionCryptoError, TrafficKeyPair, UnverifiedPeerKey,
};
