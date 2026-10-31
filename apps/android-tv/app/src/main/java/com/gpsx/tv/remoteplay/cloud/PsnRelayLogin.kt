// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.remoteplay.cloud

import java.io.IOException
import java.net.URL
import java.security.SecureRandom
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

/**
 * PSN sign-in through the Pylux relay, as in Pylux `PsnLoginActivity`: the TV
 * registers a short code, the owner signs in on a phone at [loginUrl], and the
 * relay hands back the NPSSO token. The relay operator (xbgamestream.com) sees the
 * token; GPSx shows that before starting and never logs the code or the token.
 */
class PsnRelayLogin(private val baseUrl: String = RELAY_URL) {
    val code: String = newCode()

    val loginUrl: String get() = "$baseUrl/psstream/?psstream_code=$code"

    /** Blocking. Registers [code] with the relay. */
    fun register(): Boolean = post("/psstream/create-code")?.optString("result") == "success"

    /** Blocking. Returns the NPSSO token once the phone sign-in finished, else null. */
    fun poll(): String? {
        val response = post("/psstream/get-tokens") ?: return null
        if (response.optString("result") != "success") return null
        return normalizeNpsso(response.optString("npsso"))
    }

    private fun post(path: String): JSONObject? {
        val connection = URL(baseUrl + path).openConnection() as HttpsURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(JSONObject().put("code", code).toString().toByteArray()) }
            if (connection.responseCode != 200) return null
            val body = connection.inputStream.use { it.readBytes().take(MAX_RESPONSE_BYTES).toByteArray() }
            runCatching { JSONObject(String(body, Charsets.UTF_8)) }.getOrNull()
        } catch (_: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val RELAY_URL = "https://www.xbgamestream.com"
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val MAX_RESPONSE_BYTES = 16 * 1024
        private val random = SecureRandom()

        internal fun newCode(): String = (1..6).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
    }
}
