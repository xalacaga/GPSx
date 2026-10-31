// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.remoteplay.cloud

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

private val NPSSO_PATTERN = Regex("[A-Za-z0-9_-]{32,256}")

/**
 * Accepts a raw NPSSO value or the JSON body of Sony's `ssocookie` endpoint.
 * Returns null when the input does not look like a token.
 */
fun normalizeNpsso(input: String): String? {
    val trimmed = input.trim()
    val candidate =
        if (trimmed.startsWith("{")) {
            runCatching { JSONObject(trimmed).optString("npsso", "") }.getOrDefault("")
        } else {
            trimmed.removePrefix("npsso=")
        }
    return candidate.takeIf(NPSSO_PATTERN::matches)
}

/** Android Keystore-encrypted storage for the PSN NPSSO session token. Never logs the value. */
internal class PsnTokenStore(context: Context) {
    private val store = EncryptedSecretStore(context, "gpsx_psn_account", "gpsx_psn_npsso_v1") { NPSSO_PATTERN.matches(it) }

    fun save(npsso: String): Boolean = store.save(npsso)

    fun load(): String? = store.load()

    fun clear() = store.clear()
}

/**
 * One secret string encrypted with an AES-GCM Android Keystore key in private
 * preferences. A value that fails decryption or [isValid] is wiped.
 */
class EncryptedSecretStore(
    context: Context,
    preferencesName: String,
    private val keyAlias: String,
    private val isValid: (String) -> Boolean,
) {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun save(secret: String): Boolean =
        isValid(secret) &&
            runCatching {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
                val encrypted = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
                check(
                    preferences.edit()
                        .putString(IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                        .putString(PAYLOAD, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                        .commit(),
                )
                true
            }.getOrDefault(false)

    fun load(): String? {
        val iv = preferences.getString(IV, null) ?: return null
        val payload = preferences.getString(PAYLOAD, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(payload, Base64.NO_WRAP)), Charsets.UTF_8).takeIf(isValid)
        }.getOrNull() ?: run {
            clear()
            null
        }
    }

    fun clear() {
        preferences.edit().clear().commit()
        runCatching { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(keyAlias) }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV = "iv"
        const val PAYLOAD = "payload"
        const val GCM_TAG_BITS = 128
    }
}
