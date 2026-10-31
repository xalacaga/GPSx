// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.diagnostics

import android.content.Context
import android.util.Base64
import com.gpsx.tv.remoteplay.cloud.EncryptedSecretStore
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

private val GITHUB_TOKEN_PATTERN = Regex("(github_pat_[A-Za-z0-9_]{20,255}|gh[pousr]_[A-Za-z0-9]{30,255})")
private val REPOSITORY_PATTERN = Regex("[A-Za-z0-9-]{1,39}/[A-Za-z0-9._-]{1,100}")

fun isPlausibleGitHubToken(value: String): Boolean = GITHUB_TOKEN_PATTERN.matches(value.trim())

fun isValidRepository(value: String): Boolean = REPOSITORY_PATTERN.matches(value.trim())

/** Repository path for an uploaded report: results/YYYY/MM/<file>. */
internal fun reportPath(fileName: String): String {
    val year = fileName.take(4)
    val month = fileName.drop(4).take(2)
    return if (year.all(Char::isDigit) && month.all(Char::isDigit)) "results/$year/$month/$fileName" else "results/$fileName"
}

sealed interface UploadResult {
    data class Sent(val count: Int) : UploadResult
    data class Failed(val message: String) : UploadResult
}

/**
 * Uploads test reports to the owner's diagnostics repository through the GitHub
 * contents API. The token is a fine-grained token limited to that repository,
 * stored with Android Keystore and never logged or exported.
 */
class GitHubResultUploader(context: Context) {
    private val tokenStore =
        EncryptedSecretStore(context, "gpsx_github_diagnostics", "gpsx_github_diagnostics_v1") { isPlausibleGitHubToken(it) }
    private val settings = context.getSharedPreferences("gpsx_github_settings", Context.MODE_PRIVATE)

    var repository: String
        get() = settings.getString(KEY_REPOSITORY, DEFAULT_REPOSITORY) ?: DEFAULT_REPOSITORY
        set(value) {
            if (isValidRepository(value)) settings.edit().putString(KEY_REPOSITORY, value.trim()).apply()
        }

    fun hasToken(): Boolean = tokenStore.load() != null

    fun saveToken(raw: String): Boolean = isPlausibleGitHubToken(raw) && tokenStore.save(raw.trim())

    fun clearToken() = tokenStore.clear()

    /** Blocking; call off the main thread. Uploads every report not yet sent. */
    fun uploadPending(recorder: StreamTestRecorder): UploadResult {
        val token = tokenStore.load() ?: return UploadResult.Failed("Aucun jeton GitHub enregistré.")
        val pending = recorder.list().filterNot(StoredReport::uploaded)
        if (pending.isEmpty()) return UploadResult.Sent(0)
        var sent = 0
        for (report in pending) {
            val error = upload(token, report.file)
            if (error != null) return UploadResult.Failed("$sent envoyé(s), puis échec : $error")
            recorder.markUploaded(report.name)
            sent++
        }
        return UploadResult.Sent(sent)
    }

    private fun upload(token: String, file: File): String? {
        val bytes = file.readBytes()
        if (bytes.size > MAX_REPORT_BYTES) return "rapport trop volumineux"
        val body =
            JSONObject()
                .put("message", "test: add ${file.name}")
                .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
                .toString()
                .toByteArray(Charsets.UTF_8)
        val url = URL("https://api.github.com/repos/$repository/contents/${reportPath(file.name)}")
        val connection = url.openConnection() as HttpsURLConnection
        return try {
            connection.requestMethod = "PUT"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body) }
            when (val code = connection.responseCode) {
                200, 201 -> null
                401 -> "jeton refusé ou expiré"
                403, 404 -> "accès refusé au dépôt $repository (vérifiez les droits Contents du jeton)"
                422 -> null // already uploaded under this name
                else -> "HTTP $code"
            }
        } catch (e: java.io.IOException) {
            "réseau indisponible"
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val KEY_REPOSITORY = "repository"
        const val DEFAULT_REPOSITORY = "xalacaga/GPSx-diagnostics"
        const val MAX_REPORT_BYTES = 900 * 1024
    }
}
