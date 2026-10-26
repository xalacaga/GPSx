// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

#![forbid(unsafe_code)]

//! Pure, registration-only cryptographic behavior for GPSx.
//!
//! This crate performs no I/O, randomness, registration transport, credential
//! storage, wake-up, authentication-session, ECDH, GCM, GKCrypt, JNI, Android,
//! Windows, or GP520 work. A platform adapter must create a fresh ambassador
//! with its operating-system CSPRNG for every real registration attempt.

mod registration;

pub use registration::{
    AeropauseTableOffset, CipherTableOffset, RegistrationAmbassador, RegistrationContext,
    RegistrationCryptoError, RegistrationMaterial, RegistrationPin, RegistrationResponseDecryptor,
    RegistrationTarget,
};
