// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.remoteplay

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class RegisteredConsole(
    val host: String,
    val name: String,
    val registKey: ByteArray,
    val morning: ByteArray,
)

/** Private, authenticated storage for Remote Play registration material. */
internal class RegisteredConsoleStore(
    private val context: Context,
    private val preferencesName: String = PREFERENCES_NAME,
    private val keyAlias: String = KEY_ALIAS,
) {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun save(console: RegisteredConsole): Boolean =
        runCatching {
            val plaintext = console.encode()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(plaintext)
            check(
                preferences.edit()
                    .putString(IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                    .putString(PAYLOAD, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .commit(),
            )
            true
        }.getOrDefault(false)

    fun load(): RegisteredConsole? {
        val iv = preferences.getString(IV, null) ?: return null
        val payload = preferences.getString(PAYLOAD, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP)),
            )
            cipher.doFinal(Base64.decode(payload, Base64.NO_WRAP)).decodeConsole()
        }.getOrElse {
            preferences.edit().clear().commit()
            null
        }
    }

    internal fun clear() {
        preferences.edit().clear().commit()
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(keyAlias)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun RegisteredConsole.encode(): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(FORMAT_VERSION)
                output.writeUTF(host)
                output.writeUTF(name)
                output.writeSized(registKey)
                output.writeSized(morning)
            }
            bytes.toByteArray()
        }

    private fun ByteArray.decodeConsole(): RegisteredConsole =
        DataInputStream(ByteArrayInputStream(this)).use { input ->
            check(input.readInt() == FORMAT_VERSION)
            val host = input.readUTF().also { check(it.isNotBlank() && it.length <= MAX_TEXT_LENGTH) }
            val name = input.readUTF().also { check(it.isNotBlank() && it.length <= MAX_TEXT_LENGTH) }
            val registKey = input.readSized()
            val morning = input.readSized()
            check(input.available() == 0)
            RegisteredConsole(host, name, registKey, morning)
        }

    private fun DataOutputStream.writeSized(value: ByteArray) {
        require(value.isNotEmpty() && value.size <= MAX_KEY_LENGTH)
        writeInt(value.size)
        write(value)
    }

    private fun DataInputStream.readSized(): ByteArray {
        val size = readInt()
        check(size in 1..MAX_KEY_LENGTH)
        return ByteArray(size).also(::readFully)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFERENCES_NAME = "gpsx_registered_console"
        const val KEY_ALIAS = "gpsx_remote_play_registration_v1"
        const val IV = "iv"
        const val PAYLOAD = "payload"
        const val FORMAT_VERSION = 1
        const val GCM_TAG_BITS = 128
        const val MAX_TEXT_LENGTH = 512
        const val MAX_KEY_LENGTH = 256
    }
}
