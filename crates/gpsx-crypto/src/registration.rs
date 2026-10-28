// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

use std::fmt;

use aes::Aes128;
use aes::cipher::{AsyncStreamCipher, KeyIvInit, generic_array::GenericArray};
use cfb_mode::{Decryptor, Encryptor};
use hmac::{Hmac, Mac};
use sha2::Sha256;
use zeroize::{Zeroize, Zeroizing};

const MATERIAL_SIZE: usize = 16;
const TABLE_OFFSET_COUNT: u8 = 32;

const PS4_PRE10_AES_KEY: [u8; MATERIAL_SIZE] = [
    0x3f, 0x1c, 0xc4, 0xb6, 0xdc, 0xbb, 0x3e, 0xcc, 0x50, 0xba, 0xed, 0xef, 0x97, 0x34, 0xc7, 0xc9,
];
const ECHO_B: [u8; MATERIAL_SIZE] = [
    0xe1, 0xec, 0x9c, 0x3a, 0xdd, 0xbd, 0x08, 0x85, 0xfc, 0x0e, 0x1d, 0x78, 0x90, 0x32, 0xc0, 0x04,
];
const HMAC_KEY_PS4_PRE10: [u8; MATERIAL_SIZE] = [
    0xac, 0x07, 0x88, 0x83, 0xc8, 0x3a, 0x1f, 0xe8, 0x11, 0x46, 0x3a, 0xf3, 0x9e, 0xe3, 0xe3, 0x77,
];
const HMAC_KEY_PS4_MODERN: [u8; MATERIAL_SIZE] = [
    0x20, 0xd6, 0x6f, 0x59, 0x04, 0xea, 0x7c, 0x14, 0xe5, 0x57, 0xff, 0xc5, 0x2e, 0x48, 0x8a, 0xc8,
];
const HMAC_KEY_PS5: [u8; MATERIAL_SIZE] = [
    0x46, 0x46, 0x87, 0xb3, 0x49, 0xca, 0x8c, 0xe8, 0x59, 0xc5, 0x27, 0x0f, 0x5d, 0x7a, 0x69, 0xd6,
];

const fn hex_nibble(value: u8) -> u8 {
    match value {
        b'0'..=b'9' => value - b'0',
        b'a'..=b'f' => value - b'a' + 10,
        _ => panic!("invalid internal hexadecimal table"),
    }
}

const fn decode_table(hex: &[u8]) -> [u8; 512] {
    assert!(hex.len() == 1024, "invalid internal table length");
    let mut table = [0_u8; 512];
    let mut index = 0;
    while index < table.len() {
        table[index] = (hex_nibble(hex[index * 2]) << 4) | hex_nibble(hex[index * 2 + 1]);
        index += 1;
    }
    table
}

const PS4_KEYS_0: [u8; 512] = decode_table(b"bece5df0c17db5d0cb30135daa5623fbc4bcf18f3857fbd4d43f2638b5ceed6a21bc38d01e68cc7b45d1be421a08aa16fdb0c0f4da35e912fd21074834c1fc9f8cb6cb5db29c84e01afaa0c7eb3a93b3b3f115af13bd21abea5b80506b311d7c1d40ba3c560ee7943a5ba14080740aad28cf47df42a669e95ebbc0c00eb2c58aee0803d284e591001d460655099d399fd8e7fdad9e9397c5eae7a310a7f2a2937f0704b4eebbbf88239c6ea762b14b671eb83b1f64935a99ecdafd0c6ab7fee412763265b84123d117099c242d5c9d1279dea1ce69aca4bc392f573884612d2ae804f8d59d0bff7e560cec870a1eabdf938113eecf32025abfb017b7bab57ff0017be1cb397e606da4756e299245a64f7400867873befd3ee0d10c6c0b4909836c858a1dcb16ce817c49c92c6361dee23f98b273f09aec7b7cf1c9e17fa5198b4be838a4347df428fe0d4d11570c95f1afd73480f4eb9b50e66a5deace0c854ec55b9344c4249880fcf7729c310bee8967b3a2694fb3795a140270ed501375006af3c6051a003334f5ac9e04dbc200b01bc4f3979d7fbeb8238d99e7cb74374c57ecd26949467574af5140a4117bb32f51dae2ef3373121825390309ca49dc8ef194d780179e8746c10478d1e53d2588ec723a284168146e10e4c9577590fe221a638ef4b88d1a36fdb6cb72c297529f91721b7557903bfd5a938cdbfca303df");
const PS5_KEYS_0: [u8; 512] = decode_table(b"24d8c2694c677871ee31bd2b83b21d61c9a78eed9ad36a6b5cc83579a724e21706602edff4db271055d9ea164e900cbf406f54a531702d5d1e27df3740ba9d5dffe1057080d4b7c2967f2f42eb5a08dec1b55215f6b5f2d969a5c7c47f4664a4fd4698a7e12a8e6faf654228b9c26f3ee3e44ee45b9d6010b85ab07d040c4c2478bdb8badb8fe3a0756d28c2335b3283dd51b0a58d0966e45cb8700be68214b6d2b0c2e055f384ad9d3af877f59d9aa97df1451b9b5525d8c1ff03a5480b1b190cbde0cd48f32c9919d6b8bbd635436f71e3ef3e97b8e940a847e0e001169da7e5944b1dd280a27ff29810380db856c37a4b4c85ec2f2389afd5ba9aadb0619c51b46d024926a43484203530230a4714321a960ee80f9696d4ba683a671574e0d6604c685073142f1159acc832d1db4c8a94753361d1d4fdaa6a6168d8ae314fb8077b270ff90bb0c264b372ea8b874009b482b4ad76f936056089c820eba5f1510b27a7f0768496ebb12ec28528bc4834d4018d5b2554e0c44fa0fa998d6d7a64b1a95da4f9f522eb9af4a87a784b7fe28b0450437d262d1998386a4f2d30152e4fcdb9ce9e8d12c9fe338b84ce5b40e37f726d6c8a6a9e54f1e3645d6e7fac1ae7f7fa0022ed2b23fa58c5eb44925dccaa829f23fba6c9652ae07912652c34c52316c9cc0530f3960b90671aa7694c3e43249d4e68bd8b756e9d076f1a6aba");
const PS4_KEYS_1: [u8; 512] = decode_table(b"c848c2b408eb88f75f4a092d591f09cd1c18f47a284a966db3597153757e825057e459b3f4496940eb17c99f179771aec9607ff82e0894e843eada6be51959331f89ae47577b1c66feff95bf556bd59327eaa62467399fd30caa2642e7664dd81875fe440346ee3ef83cb7859703070692ff5917270b21f7057f69900e3891c6672348ba088e57dd91d040471c5bbfc8063f96a0dc00e59af53b908066bb0f9330072b5645a09ab1b0720ce4dd70dd7c5abfd4e80dca37e50efd12ee799a5ea71e31af1f4652caf342003df2897c1c7760b74a8076474b3fb6912f9cc2f1ad4429cb328c0a8d057546a1f8da1aa720de3259fe70b587f392fdb4dff4a6e37d983be1ba18db61d1c2a6ee0825fa868a7bfebc02bd225f2530515d28365a298e52eb4914287f0ac46925856bec333357ab5013cfe7737823064d1fbb3811b16e6c6fcd4b0e428580bba83968c95bbb4658868857885eea7c37dffd023945892ef2e4f008c0b6eb9c6e7a81a32646fee1703c3e117a32ce45027d32cd080706a3a8f734feee06b014d66b2d2e01af7711ec1f3138179cd0e0c54da4d6adb9e6e1e3e29e44919a5e26caccda4dd7786a75a619adcc62c7b60d14b1beebcb10cfa9eee24208358a5cbcf149fe647803490c85f0e47726d25ef5c13b3d2dcccf2aaced8852746dbfb2b9f758511c50f63df2c4470a213047812de4750d8f2d22b06327");
const PS5_KEYS_1: [u8; 512] = decode_table(b"794d7830fe10524ca8905b9a7e5fd3e113e0f10fa3e7bb457fdc8ed5f1045c7851eff8655903398437ae59df23b66034e64be2f54c13c6daf9fdb36584d645ec2c00f2eddccb936e6146e5d60194ee78850e685eb55bcdd36341fc81431c6f7cbae8bd8631d5707fb54a903e84e171e00299f471e702ed36afde56c290e0aec2f9af53c6d862163227fb6e9b48c6eaff6f780222982c1fbfb08ea939bcdf17ded70ee17a010ec387fcaae46b0f5b0af118198ae52c369b403099249448d747b2af6b8c409e4d6d3407c1262fbb14f7bc3652bd84fe4a9af48adb3489aaf10d940b92f41ce46c792d6ec0190ad55594140513c26223b3d426c444567acd1cead474b936409f08fb4962059298ad1d9f8a768bd40f214076b61691459366cc12ea4df409e2ac33d06f4351073ed7952c1e1f0c24b30e3aef95f5eb77dd20f23598f2aea966e613ef5d3a2d66ede21ee9324a40bf37c67029d98ca1614a293dc7559c949ec911451028a727d1d3d08479c7a9b0f6af458c3cd4df3bf70da24f13977827f048c0a5ab830105d012d71e123a4e9877aebab14eb53b59ca6da51180919c0769595a53707c9597116d668da3bdbb2db0bf9b10cbc70f5b7e67e2b04bba1012b9bc97fd48e48ac10fa1309d5620241a7d5ba0b4be9d384fb456a84d137c44e88497eb782c5285e4a2f6f3d9719eeeb81147fba91bc740c6e1196d50a12a");

/// Console generation supported by the registration-only primitive boundary.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum RegistrationTarget {
    Ps4Pre10,
    Ps4Modern,
    Ps5,
}

/// Validated column in the modern registration cipher table.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct CipherTableOffset(u8);

impl CipherTableOffset {
    pub fn new(value: u8) -> Result<Self, RegistrationCryptoError> {
        (value < TABLE_OFFSET_COUNT)
            .then_some(Self(value))
            .ok_or(RegistrationCryptoError::InvalidTableOffset)
    }
}

/// Validated column in the independent modern aeropause table.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct AeropauseTableOffset(u8);

impl AeropauseTableOffset {
    pub fn new(value: u8) -> Result<Self, RegistrationCryptoError> {
        (value < TABLE_OFFSET_COUNT)
            .then_some(Self(value))
            .ok_or(RegistrationCryptoError::InvalidTableOffset)
    }
}

/// Eight decimal PIN digits owned and erased by the registration boundary.
///
/// ```compile_fail
/// use gpsx_crypto::RegistrationPin;
/// let pin = RegistrationPin::new(*b"00000000").unwrap();
/// let duplicate = pin.clone();
/// ```
pub struct RegistrationPin([u8; 8]);

impl RegistrationPin {
    pub fn new(mut digits: [u8; 8]) -> Result<Self, RegistrationCryptoError> {
        if !digits.iter().all(u8::is_ascii_digit) {
            digits.zeroize();
            return Err(RegistrationCryptoError::InvalidPin);
        }
        Ok(Self(digits))
    }

    fn big_endian_bytes(&self) -> [u8; 4] {
        let mut value = self
            .0
            .iter()
            .fold(0_u32, |value, digit| value * 10 + u32::from(*digit - b'0'));
        let bytes = value.to_be_bytes();
        value.zeroize();
        bytes
    }
}

impl Drop for RegistrationPin {
    fn drop(&mut self) {
        self.0.zeroize();
    }
}

/// Fresh per-attempt ambassador bytes supplied by a future platform CSPRNG adapter.
///
/// ```compile_fail
/// use gpsx_crypto::RegistrationAmbassador;
/// fn require_clone<T: Clone>() {}
/// require_clone::<RegistrationAmbassador>();
/// ```
pub struct RegistrationAmbassador([u8; MATERIAL_SIZE]);

impl RegistrationAmbassador {
    pub fn from_csprng_bytes(bytes: [u8; MATERIAL_SIZE]) -> Self {
        Self(bytes)
    }
}

impl Drop for RegistrationAmbassador {
    fn drop(&mut self) {
        self.0.zeroize();
    }
}

/// Derived registration material. Access is explicit and the owned bytes are erased on drop.
///
/// ```compile_fail
/// use gpsx_crypto::RegistrationMaterial;
/// fn require_clone<T: Clone>() {}
/// require_clone::<RegistrationMaterial>();
/// ```
pub struct RegistrationMaterial([u8; MATERIAL_SIZE]);

impl RegistrationMaterial {
    pub fn expose_for_protocol(&self) -> &[u8; MATERIAL_SIZE] {
        &self.0
    }
}

impl Drop for RegistrationMaterial {
    fn drop(&mut self) {
        self.0.zeroize();
    }
}

/// Static, input-free failures from registration cryptography.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum RegistrationCryptoError {
    InvalidPin,
    InvalidTableOffset,
    InvalidTarget,
    CryptoInitialization,
}

impl fmt::Display for RegistrationCryptoError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(match self {
            Self::InvalidPin => "registration PIN must contain eight decimal digits",
            Self::InvalidTableOffset => "registration table offset is outside the accepted range",
            Self::InvalidTarget => "registration target is not valid for this operation",
            Self::CryptoInitialization => {
                "registration cryptographic primitive could not initialize"
            }
        })
    }
}

impl std::error::Error for RegistrationCryptoError {}

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

redacted_format!(RegistrationPin, "RegistrationPin");
redacted_format!(RegistrationAmbassador, "RegistrationAmbassador");
redacted_format!(RegistrationMaterial, "RegistrationMaterial");

/// Registration-only key and ambassador owner.
///
/// ```compile_fail
/// use gpsx_crypto::RegistrationContext;
/// fn require_clone<T: Clone>() {}
/// require_clone::<RegistrationContext>();
/// ```
pub struct RegistrationContext {
    target: RegistrationTarget,
    bright: [u8; MATERIAL_SIZE],
    ambassador: [u8; MATERIAL_SIZE],
}

/// One-shot owner that may decrypt only the response paired with one request.
///
/// A successful request encryption consumes [`RegistrationContext`], so trying
/// to encrypt a second request with the same key and IV does not compile:
///
/// ```compile_fail
/// use gpsx_crypto::{CipherTableOffset, RegistrationAmbassador, RegistrationContext,
///     RegistrationPin, RegistrationTarget};
/// let context = RegistrationContext::new_modern(
///     RegistrationTarget::Ps5,
///     RegistrationAmbassador::from_csprng_bytes([0; 16]),
///     RegistrationPin::new(*b"00000000").unwrap(),
///     CipherTableOffset::new(0).unwrap(),
/// ).unwrap();
/// let mut request = [];
/// let response = context.encrypt_request_in_place(&mut request).unwrap();
/// let second = context.encrypt_request_in_place(&mut request);
/// ```
pub struct RegistrationResponseDecryptor {
    context: RegistrationContext,
}

impl RegistrationContext {
    pub fn new_ps4_pre10(ambassador: RegistrationAmbassador, pin: RegistrationPin) -> Self {
        let mut context = Self {
            target: RegistrationTarget::Ps4Pre10,
            bright: [0; MATERIAL_SIZE],
            ambassador: [0; MATERIAL_SIZE],
        };
        context.bright.copy_from_slice(&PS4_PRE10_AES_KEY);
        let pin_bytes = Zeroizing::new(pin.big_endian_bytes());
        for (target, source) in context.bright.iter_mut().zip(pin_bytes.iter()) {
            *target ^= *source;
        }
        context.ambassador.copy_from_slice(&ambassador.0);
        context
    }

    pub fn new_modern(
        target: RegistrationTarget,
        ambassador: RegistrationAmbassador,
        pin: RegistrationPin,
        offset: CipherTableOffset,
    ) -> Result<Self, RegistrationCryptoError> {
        let table = match target {
            RegistrationTarget::Ps4Modern => &PS4_KEYS_0,
            RegistrationTarget::Ps5 => &PS5_KEYS_0,
            RegistrationTarget::Ps4Pre10 => return Err(RegistrationCryptoError::InvalidTarget),
        };
        let mut context = Self {
            target,
            bright: [0; MATERIAL_SIZE],
            ambassador: [0; MATERIAL_SIZE],
        };
        for (row, value) in context.bright.iter_mut().enumerate() {
            *value = table[row * usize::from(TABLE_OFFSET_COUNT) + usize::from(offset.0)];
        }
        let pin_bytes = Zeroizing::new(pin.big_endian_bytes());
        for (target, source) in context.bright[12..].iter_mut().zip(pin_bytes.iter()) {
            *target ^= *source;
        }
        context.ambassador.copy_from_slice(&ambassador.0);
        Ok(context)
    }

    pub fn aeropause_ps4_pre10(&self) -> Result<RegistrationMaterial, RegistrationCryptoError> {
        if self.target != RegistrationTarget::Ps4Pre10 {
            return Err(RegistrationCryptoError::InvalidTarget);
        }
        let mut output = [0_u8; MATERIAL_SIZE];
        for (index, value) in output.iter_mut().enumerate() {
            *value = self.ambassador[index]
                .wrapping_sub(index as u8)
                .wrapping_sub(0x29)
                ^ ECHO_B[index];
        }
        Ok(RegistrationMaterial(output))
    }

    /// Derives modern aeropause material from its independently typed offset.
    ///
    /// The following does not compile, preventing accidental reuse or swapping
    /// of the cipher-table offset:
    ///
    /// ```compile_fail
    /// use gpsx_crypto::{AeropauseTableOffset, CipherTableOffset};
    /// fn requires_aeropause(_: AeropauseTableOffset) {}
    /// let cipher = CipherTableOffset::new(7).unwrap();
    /// requires_aeropause(cipher);
    /// ```
    pub fn aeropause(
        &self,
        offset: AeropauseTableOffset,
    ) -> Result<RegistrationMaterial, RegistrationCryptoError> {
        let (table, adjustment) = match self.target {
            RegistrationTarget::Ps4Modern => (&PS4_KEYS_1, 0x29_u8),
            RegistrationTarget::Ps5 => (&PS5_KEYS_1, 0xd3_u8),
            RegistrationTarget::Ps4Pre10 => return Err(RegistrationCryptoError::InvalidTarget),
        };
        let mut output = [0_u8; MATERIAL_SIZE];
        for (row, value) in output.iter_mut().enumerate() {
            let key = table[row * usize::from(TABLE_OFFSET_COUNT) + usize::from(offset.0)];
            *value = (self.ambassador[row] ^ key)
                .wrapping_add(adjustment)
                .wrapping_add(row as u8);
        }
        Ok(RegistrationMaterial(output))
    }

    pub fn encrypt_request_in_place(
        self,
        buffer: &mut [u8],
    ) -> Result<RegistrationResponseDecryptor, RegistrationCryptoError> {
        self.crypt_in_place(buffer, true)?;
        Ok(RegistrationResponseDecryptor { context: self })
    }

    fn crypt_in_place(
        &self,
        buffer: &mut [u8],
        encrypt: bool,
    ) -> Result<(), RegistrationCryptoError> {
        let iv = self.registration_iv()?;
        if encrypt {
            Encryptor::<Aes128>::new(
                GenericArray::from_slice(&self.bright),
                GenericArray::from_slice(&iv[..]),
            )
            .encrypt(buffer);
        } else {
            Decryptor::<Aes128>::new(
                GenericArray::from_slice(&self.bright),
                GenericArray::from_slice(&iv[..]),
            )
            .decrypt(buffer);
        }
        Ok(())
    }

    fn registration_iv(&self) -> Result<Zeroizing<[u8; MATERIAL_SIZE]>, RegistrationCryptoError> {
        let key = match self.target {
            RegistrationTarget::Ps4Pre10 => HMAC_KEY_PS4_PRE10,
            RegistrationTarget::Ps4Modern => HMAC_KEY_PS4_MODERN,
            RegistrationTarget::Ps5 => HMAC_KEY_PS5,
        };
        let mut input = Zeroizing::new([0_u8; MATERIAL_SIZE + 8]);
        input[..MATERIAL_SIZE].copy_from_slice(&self.ambassador);
        // Registration is the deliberately fixed counter-zero boundary.
        let mut mac = Hmac::<Sha256>::new_from_slice(&key)
            .map_err(|_| RegistrationCryptoError::CryptoInitialization)?;
        mac.update(&input[..]);
        let mut digest = mac.finalize().into_bytes();
        let mut iv = Zeroizing::new([0_u8; MATERIAL_SIZE]);
        iv.copy_from_slice(&digest[..MATERIAL_SIZE]);
        digest.zeroize();
        Ok(iv)
    }
}

impl RegistrationResponseDecryptor {
    pub fn decrypt_response_in_place(
        self,
        buffer: &mut [u8],
    ) -> Result<(), RegistrationCryptoError> {
        self.context.crypt_in_place(buffer, false)
    }
}

impl Drop for RegistrationContext {
    fn drop(&mut self) {
        self.bright.zeroize();
        self.ambassador.zeroize();
    }
}

redacted_format!(RegistrationContext, "RegistrationContext");
redacted_format!(
    RegistrationResponseDecryptor,
    "RegistrationResponseDecryptor"
);

#[cfg(test)]
mod tests {
    use super::*;

    #[path = "../../../../../tests/support/registration_codec_oracle.rs"]
    mod codec_oracle;

    const AMBASSADOR: [u8; 16] = [
        0x13, 0x37, 0xde, 0xad, 0xbe, 0xef, 0xc0, 0xff, 0xee, 0x42, 0x63, 0x68, 0x69, 0x61, 0x6b,
        0x69,
    ];
    const MESSAGE: &[u8] = b"GPSx-registration-oracle-synthetic-v1";
    const ORACLE_V2: &str =
        include_str!("../../../tests/fixtures/registration_crypto_oracle_v2.json");

    fn pin() -> RegistrationPin {
        RegistrationPin::new(*b"13374201").unwrap()
    }

    fn modern(target: RegistrationTarget, offset: u8) -> RegistrationContext {
        RegistrationContext::new_modern(
            target,
            RegistrationAmbassador::from_csprng_bytes(AMBASSADOR),
            pin(),
            CipherTableOffset::new(offset).unwrap(),
        )
        .unwrap()
    }

    fn decode<const N: usize>(hex: &str) -> [u8; N] {
        assert_eq!(hex.len(), N * 2);
        let bytes = hex.as_bytes();
        let mut output = [0_u8; N];
        for (index, value) in output.iter_mut().enumerate() {
            *value = (hex_nibble(bytes[index * 2]) << 4) | hex_nibble(bytes[index * 2 + 1]);
        }
        output
    }

    fn assert_codec_oracle_crypto(fixture: &str) {
        let oracle = codec_oracle::parse(fixture).expect("strict codec oracle JSON");
        assert_eq!(oracle.inputs.synthetic_number, 13_374_201);
        assert_eq!(oracle.inputs.seed_16, "1337deadbeefc0ffee42636869616b69");
        assert_eq!(oracle.inputs.online_id, "ChiakiNanami1337");
        assert_eq!(oracle.inputs.account_id, "0001020304050607");
        assert_eq!(oracle.cases.len(), 9);
        assert_eq!(
            oracle
                .cases
                .iter()
                .map(|case| case.name)
                .collect::<Vec<_>>(),
            [
                "ps4_8_online_default",
                "ps4_9_online_compat_default",
                "ps4_9_account_default",
                "ps4_10_account_default",
                "ps4_10_account_0_31",
                "ps4_10_account_31_0",
                "ps5_account_default",
                "ps5_account_0_31",
                "ps5_account_31_0",
            ]
        );

        let ambassador: [u8; 16] = codec_oracle::decode_hex(oracle.inputs.seed_16)
            .unwrap()
            .try_into()
            .unwrap();
        for (index, case) in oracle.cases.into_iter().enumerate() {
            assert!(matches!(case.identity, "online_id" | "account_id"));
            assert_eq!(codec_oracle::decode_hex(case.filler).unwrap().len(), 0x1e0);
            assert!(!case.payload.is_empty());
            assert!(!case.outer_request.is_empty());
            assert_eq!(case.trailing_comma, index < 8);
            let (target, expected_numeric_target) = match case.profile {
                "ps4_8" => (RegistrationTarget::Ps4Pre10, 800),
                "ps4_9" => (RegistrationTarget::Ps4Pre10, 900),
                "ps4_10_target" => (RegistrationTarget::Ps4Modern, 1_000),
                "ps5" => (RegistrationTarget::Ps5, 1_000_100),
                _ => panic!("unexpected codec oracle profile"),
            };
            assert_eq!(case.target, expected_numeric_target, "{} target", case.name);
            let context = match case.cipher_offset {
                Some(offset) => RegistrationContext::new_modern(
                    target,
                    RegistrationAmbassador::from_csprng_bytes(ambassador),
                    pin(),
                    CipherTableOffset::new(offset).unwrap(),
                )
                .unwrap(),
                None => {
                    assert_eq!(target, RegistrationTarget::Ps4Pre10);
                    RegistrationContext::new_ps4_pre10(
                        RegistrationAmbassador::from_csprng_bytes(ambassador),
                        pin(),
                    )
                }
            };
            let aeropause = match case.aeropause_offset {
                Some(offset) => context
                    .aeropause(AeropauseTableOffset::new(offset).unwrap())
                    .unwrap(),
                None => context.aeropause_ps4_pre10().unwrap(),
            };
            assert_eq!(
                aeropause.expose_for_protocol().as_slice(),
                codec_oracle::decode_hex(case.aeropause).unwrap(),
                "{} aeropause",
                case.name
            );

            let mut encrypted = codec_oracle::decode_hex(case.plaintext_inner).unwrap();
            let _response = context.encrypt_request_in_place(&mut encrypted).unwrap();
            assert_eq!(
                encrypted,
                codec_oracle::decode_hex(case.encrypted_inner).unwrap(),
                "{} encrypted inner",
                case.name
            );
        }
    }

    #[test]
    fn codec_oracle_crypto_fields_are_bound_for_all_nine_cases() {
        assert!(codec_oracle::has_crlf_only(codec_oracle::FIXTURE));
        assert_codec_oracle_crypto(codec_oracle::FIXTURE);
    }

    #[test]
    fn codec_oracle_crypto_field_mutations_are_detected() {
        let mutated_aeropause = codec_oracle::FIXTURE.replacen(
            "75064b815c8db1e97999fe4637e35969",
            "7999fe4637e3596975064b815c8db1e9",
            1,
        );
        assert!(
            std::panic::catch_unwind(|| assert_codec_oracle_crypto(&mutated_aeropause)).is_err()
        );

        let mutated_encrypted = codec_oracle::FIXTURE.replacen(
            "f2fd4ddc8621da3d9f35f3df82586508",
            "02fd4ddc8621da3d9f35f3df82586508",
            1,
        );
        assert!(
            std::panic::catch_unwind(|| assert_codec_oracle_crypto(&mutated_encrypted)).is_err()
        );

        let extra_case = codec_oracle::duplicate_first_case();
        assert!(std::panic::catch_unwind(|| assert_codec_oracle_crypto(&extra_case)).is_err());
    }

    #[derive(Clone, Copy)]
    struct OracleCase<'a> {
        target: RegistrationTarget,
        profile: &'a str,
        cipher_offset: Option<u8>,
        aeropause_offset: Option<u8>,
        bright: &'a str,
        aeropause: &'a str,
        iv: &'a str,
        ciphertext: &'a str,
        trailing_comma: bool,
    }

    fn fixture_case_line(case: &OracleCase<'_>) -> String {
        let offset =
            |value: Option<u8>| value.map_or_else(|| "null".to_owned(), |value| value.to_string());
        let mut line = format!(
            "    {{\"profile\": \"{}\", \"cipher_offset\": {}, \"aeropause_offset\": {}, \"derived_16\": \"{}\", \"mix_16\": \"{}\", \"initial_vector_16\": \"{}\", \"encrypted\": \"{}\"}}",
            case.profile,
            offset(case.cipher_offset),
            offset(case.aeropause_offset),
            case.bright,
            case.aeropause,
            case.iv,
            case.ciphertext,
        );
        if case.trailing_comma {
            line.push(',');
        }
        line
    }

    fn fixture_has_case(fixture: &str, case: &OracleCase<'_>) -> bool {
        let expected = fixture_case_line(case);
        fixture.lines().any(|line| line == expected)
    }

    fn fixture_has_expected_inputs_and_case_count(fixture: &str) -> bool {
        let expected_inputs = [
            "    \"synthetic_number\": 13374201,",
            "    \"seed_16\": \"1337deadbeefc0ffee42636869616b69\",",
            "    \"message\": \"475053782d726567697374726174696f6e2d6f7261636c652d73796e7468657469632d7631\",",
            "    \"counter\": 0",
        ];
        expected_inputs
            .iter()
            .all(|expected| fixture.lines().any(|line| line == *expected))
            && fixture
                .lines()
                .filter(|line| line.contains("\"profile\":"))
                .count()
                == 7
    }

    fn assert_oracle(case: &OracleCase<'_>) {
        assert!(fixture_has_case(ORACLE_V2, case));
        let context = match case.cipher_offset {
            Some(offset) => modern(case.target, offset),
            None => RegistrationContext::new_ps4_pre10(
                RegistrationAmbassador::from_csprng_bytes(AMBASSADOR),
                pin(),
            ),
        };
        assert_eq!(context.bright, decode::<16>(case.bright));
        let material = match case.aeropause_offset {
            Some(offset) => context.aeropause(AeropauseTableOffset::new(offset).unwrap()),
            None => context.aeropause_ps4_pre10(),
        }
        .unwrap();
        assert_eq!(
            *material.expose_for_protocol(),
            decode::<16>(case.aeropause)
        );
        assert_eq!(*context.registration_iv().unwrap(), decode::<16>(case.iv));
        let mut encrypted = MESSAGE.to_vec();
        let response = context.encrypt_request_in_place(&mut encrypted).unwrap();
        assert_eq!(encrypted, decode::<37>(case.ciphertext));
        response.decrypt_response_in_place(&mut encrypted).unwrap();
        assert_eq!(encrypted, MESSAGE);
    }

    #[test]
    fn oracle_v2_has_independent_offsets_for_every_modern_target() {
        assert!(fixture_has_expected_inputs_and_case_count(ORACLE_V2));
        let pre10 = OracleCase {
            target: RegistrationTarget::Ps4Pre10,
            profile: "ps4_pre10",
            cipher_offset: None,
            aeropause_offset: None,
            bright: "3fd0d64fdcbb3ecc50baedef9734c7c9",
            aeropause: "0be12fbb4c7c994a411e2d4ca419f435",
            iv: "82d04e574b5b5d30bfc0bb727b575597",
            ciphertext: "f6c177c1c527920e8f36e297c37b6509dd2e821527ea124a8458af648c26b48e7b976a71b3",
            trailing_comma: true,
        };
        assert_oracle(&pre10);

        let cases = [
            (
                RegistrationTarget::Ps4Modern,
                0,
                31,
                "be218c1dee7f12e8016c4bc56a9be3e3",
                "6c2e31975a76b397ed60c4f4bfc5bd86",
                "aaa8c0070ea897ccc689672eae043494",
                "3e1ad99784ef81e2d5aec4a0c86c94bc15d397872a7360a4fd084c7c6877f1c157ad797233",
                true,
            ),
            (
                RegistrationTarget::Ps5,
                0,
                31,
                "2406fffd78d20ce551d6b85164e2083c",
                "3eaf774537352d05dfb613ddd7bbf825",
                "2507999f9d131eb1880f1481725046ec",
                "a1d7e88d2c0c5ce218fa064428269b892953cb32d962c1a839f9a5fc3d0999d2cdf04b9a09",
                true,
            ),
            (
                RegistrationTarget::Ps4Modern,
                31,
                0,
                "6a9f7c8a93e42af0838b4e004c423026",
                "048aece1060d0d0606db3fc0d3b9c019",
                "aaa8c0070ea897ccc689672eae043494",
                "8f7c70e990d87bb887ae3da254377235a332f35ec42f465bffdfd5f46ed532ed525b9f1a28",
                true,
            ),
            (
                RegistrationTarget::Ps5,
                31,
                0,
                "175da424b619a79ce04ff17a15602643",
                "3d3ac7ed1e1048ce75eb6ed2f0dd5c0f",
                "2507999f9d131eb1880f1481725046ec",
                "45386dfd2c49d07b6c15b05c8226b2798ff7ddfea9285f3fea3cef89d5726bdb0739afdb6b",
                true,
            ),
            (
                RegistrationTarget::Ps4Modern,
                7,
                22,
                "d07be0940088d17e6d81288034b854cf",
                "aecd6cb88806ba3cfc935261bea2d57c",
                "aaa8c0070ea897ccc689672eae043494",
                "3fc825d538cc85fddb9b6f18350fde73db369364137ed597a61ff1534afbe7846a3d934552",
                true,
            ),
            (
                RegistrationTarget::Ps5,
                7,
                22,
                "7110c26fa0ad997f342fb09622de390a",
                "7bc5e816784c330573892732068ab654",
                "2507999f9d131eb1880f1481725046ec",
                "301e4cf4cffcde9b001312eccd205706a06aae904114792fb2b9d01c30726ec609cf5c71f6",
                false,
            ),
        ];
        for (
            target,
            cipher_offset,
            aeropause_offset,
            bright,
            aeropause,
            iv,
            ciphertext,
            trailing_comma,
        ) in cases
        {
            let case = OracleCase {
                target,
                profile: match target {
                    RegistrationTarget::Ps4Modern => "ps4_10_plus",
                    RegistrationTarget::Ps5 => "ps5",
                    RegistrationTarget::Ps4Pre10 => unreachable!(),
                },
                cipher_offset: Some(cipher_offset),
                aeropause_offset: Some(aeropause_offset),
                bright,
                aeropause,
                iv,
                ciphertext,
                trailing_comma,
            };
            assert_oracle(&case);
        }
    }

    #[test]
    fn oracle_v2_offset_mutation_is_detected() {
        let mutated = ORACLE_V2.replacen(
            "\"cipher_offset\": 0, \"aeropause_offset\": 31",
            "\"cipher_offset\": 31, \"aeropause_offset\": 0",
            1,
        );
        let expected = OracleCase {
            target: RegistrationTarget::Ps4Modern,
            profile: "ps4_10_plus",
            cipher_offset: Some(0),
            aeropause_offset: Some(31),
            bright: "be218c1dee7f12e8016c4bc56a9be3e3",
            aeropause: "6c2e31975a76b397ed60c4f4bfc5bd86",
            iv: "aaa8c0070ea897ccc689672eae043494",
            ciphertext: "3e1ad99784ef81e2d5aec4a0c86c94bc15d397872a7360a4fd084c7c6877f1c157ad797233",
            trailing_comma: true,
        };
        assert!(fixture_has_case(ORACLE_V2, &expected));
        assert!(!fixture_has_case(&mutated, &expected));
    }

    #[test]
    fn oracle_v2_input_and_case_count_mutations_are_detected() {
        let mutated_input = ORACLE_V2.replacen(
            "\"synthetic_number\": 13374201",
            "\"synthetic_number\": 13374202",
            1,
        );
        assert!(!fixture_has_expected_inputs_and_case_count(&mutated_input));

        let extra_case = ORACLE_V2.replacen(
            "\"cases\": [",
            "\"cases\": [\n    {\"profile\": \"extra\"},",
            1,
        );
        assert!(!fixture_has_expected_inputs_and_case_count(&extra_case));
    }

    #[test]
    fn upstream_registration_vectors_are_reproduced() {
        let ambassador = [
            0xdc, 0xa1, 0xc8, 0x4d, 0xfe, 0x50, 0xd6, 0x57, 0x22, 0xda, 0x09, 0x65, 0x42, 0x31,
            0xe7, 0xc2,
        ];
        let make = |target| {
            RegistrationContext::new_modern(
                target,
                RegistrationAmbassador::from_csprng_bytes(ambassador),
                RegistrationPin::new(*b"78703893").unwrap(),
                CipherTableOffset::new(30).unwrap(),
            )
            .unwrap()
        };
        assert_eq!(
            make(RegistrationTarget::Ps4Modern).bright,
            decode::<16>("edfc1dc5a2fe2d7f09198575336c1316")
        );
        assert_eq!(
            make(RegistrationTarget::Ps5).bright,
            decode::<16>("e29d644c141b9d617431a56d34cfc17f")
        );

        let iv_ambassador = [
            0x3e, 0x7e, 0x7a, 0x82, 0x59, 0x73, 0xad, 0xab, 0x2f, 0x69, 0x43, 0x46, 0xbd, 0x44,
            0xda, 0xb5,
        ];
        let iv_context = |target| {
            RegistrationContext::new_modern(
                target,
                RegistrationAmbassador::from_csprng_bytes(iv_ambassador),
                RegistrationPin::new(*b"00000000").unwrap(),
                CipherTableOffset::new(0).unwrap(),
            )
            .unwrap()
        };
        assert_eq!(
            iv_context(RegistrationTarget::Ps4Modern)
                .registration_iv()
                .unwrap()
                .as_ref(),
            &decode::<16>("ac489977f92ac55bb9093c33b6113c46")
        );
        assert_eq!(
            iv_context(RegistrationTarget::Ps5)
                .registration_iv()
                .unwrap()
                .as_ref(),
            &decode::<16>("9044408273f8044dca767b5a16394d64")
        );
    }

    #[test]
    fn nist_sp800_38a_f_3_13_and_f_3_14_cfb128() {
        let key = decode::<16>("2b7e151628aed2a6abf7158809cf4f3c");
        let iv = decode::<16>("000102030405060708090a0b0c0d0e0f");
        let plaintext = decode::<64>(
            "6bc1bee22e409f96e93d7e117393172aae2d8a571e03ac9c9eb76fac45af8e51\
             30c81c46a35ce411e5fbc1191a0a52eff69f2445df4f9b17ad2b417be66c3710",
        );
        let ciphertext = decode::<64>(
            "3b3fd92eb72dad20333449f8e83cfb4ac8a64537a0b3a93fcde3cdad9f1ce58b\
             26751f67a3cbb140b1808cf187a4f4dfc04b05357c5d1c0eeac4c66f9ff7f2e6",
        );
        let mut encrypted = plaintext;
        Encryptor::<Aes128>::new(
            GenericArray::from_slice(&key),
            GenericArray::from_slice(&iv),
        )
        .encrypt(&mut encrypted);
        assert_eq!(encrypted, ciphertext);
        Decryptor::<Aes128>::new(
            GenericArray::from_slice(&key),
            GenericArray::from_slice(&iv),
        )
        .decrypt(&mut encrypted);
        assert_eq!(encrypted, plaintext);
    }

    #[test]
    fn rfc4231_hmac_sha256_cases_and_iv_truncation() {
        let sequential_key: Vec<u8> = (1..=25).collect();
        let cases: Vec<(Vec<u8>, Vec<u8>, &str)> = vec![
            (vec![0x0b; 20], b"Hi There".to_vec(), "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7"),
            (b"Jefe".to_vec(), b"what do ya want for nothing?".to_vec(), "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843"),
            (vec![0xaa; 20], vec![0xdd; 50], "773ea91e36800e46854db8ebd09181a72959098b3ef8c122d9635514ced565fe"),
            (sequential_key, vec![0xcd; 50], "82558a389a443c0ea4cc819899f2083a85f0faa3e578f8077a2e3ff46729665b"),
            (vec![0xaa; 131], b"Test Using Larger Than Block-Size Key - Hash Key First".to_vec(), "60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54"),
            (vec![0xaa; 131], b"This is a test using a larger than block-size key and a larger than block-size data. The key needs to be hashed before being used by the HMAC algorithm.".to_vec(), "9b09ffa71b942fcb27635fbcd5b0e944bfdc63644f0713938a7f51535c3a35e2"),
        ];
        for (key, data, expected) in cases {
            let mut mac = Hmac::<Sha256>::new_from_slice(&key).unwrap();
            mac.update(&data);
            let digest = mac.finalize().into_bytes();
            assert_eq!(&digest[..], &decode::<32>(expected));
            assert_eq!(&digest[..16], &decode::<32>(expected)[..16]);
        }
    }

    #[test]
    fn offsets_cover_the_closed_range() {
        for value in 0..=31 {
            assert!(CipherTableOffset::new(value).is_ok());
            assert!(AeropauseTableOffset::new(value).is_ok());
        }
        assert_eq!(
            CipherTableOffset::new(32),
            Err(RegistrationCryptoError::InvalidTableOffset)
        );
        assert_eq!(
            AeropauseTableOffset::new(u8::MAX),
            Err(RegistrationCryptoError::InvalidTableOffset)
        );
    }

    #[test]
    fn payload_lengths_round_trip_with_one_shot_states() {
        for length in [0, 5, 16, 37, 257] {
            let input: Vec<u8> = (0..length).map(|value| value as u8).collect();
            let mut first = input.clone();
            let first_response = modern(RegistrationTarget::Ps5, 7)
                .encrypt_request_in_place(&mut first)
                .unwrap();
            let mut second = input.clone();
            let second_response = modern(RegistrationTarget::Ps5, 7)
                .encrypt_request_in_place(&mut second)
                .unwrap();
            assert_eq!(first, second);
            first_response
                .decrypt_response_in_place(&mut first)
                .unwrap();
            second_response
                .decrypt_response_in_place(&mut second)
                .unwrap();
            assert_eq!(first, input);
            assert_eq!(second, input);
        }
    }

    #[test]
    fn invalid_inputs_fail_with_static_redacted_errors() {
        assert!(RegistrationPin::new(*b"12A45678").is_err());
        let context = modern(RegistrationTarget::Ps4Modern, 0);
        let material = context
            .aeropause(AeropauseTableOffset::new(0).unwrap())
            .unwrap();
        let pre10_error = RegistrationContext::new_modern(
            RegistrationTarget::Ps4Pre10,
            RegistrationAmbassador::from_csprng_bytes(AMBASSADOR),
            pin(),
            CipherTableOffset::new(0).unwrap(),
        )
        .unwrap_err();
        assert_eq!(pre10_error, RegistrationCryptoError::InvalidTarget);
        let mut empty = [];
        let response = context.encrypt_request_in_place(&mut empty).unwrap();
        for text in [
            format!("{response:?}"),
            response.to_string(),
            RegistrationPin::new(*b"12345678").unwrap().to_string(),
            RegistrationAmbassador::from_csprng_bytes(AMBASSADOR).to_string(),
            material.to_string(),
        ] {
            assert!(text.contains("[REDACTED]"));
            assert!(!text.contains("12345678"));
            assert!(!text.contains("13374201"));
        }
    }
}
