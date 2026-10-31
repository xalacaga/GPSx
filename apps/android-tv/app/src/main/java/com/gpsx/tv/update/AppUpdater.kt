// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.update

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

/** Contents of the `release.json` asset published next to every GPSx APK. */
data class ReleaseManifest(
    val versionCode: Int,
    val versionName: String,
    val apkSha256: String,
    val packageName: String,
    val apkUrl: String,
)

private val SHA256_HEX = Regex("[0-9a-f]{64}")
private val ALLOWED_HOSTS = listOf("github.com", "githubusercontent.com")

/** Parses the latest-release API response; returns null unless both assets and fields are valid. */
fun parseLatestRelease(releaseJson: String, manifestJson: (String) -> String?): ReleaseManifest? {
    val release = runCatching { JSONObject(releaseJson) }.getOrNull() ?: return null
    val assets = release.optJSONArray("assets") ?: return null
    var manifestUrl: String? = null
    var apkUrl: String? = null
    for (index in 0 until assets.length()) {
        val asset = assets.optJSONObject(index) ?: continue
        val url = asset.optString("browser_download_url")
        when (asset.optString("name")) {
            "release.json" -> manifestUrl = url
            "gpsx.apk" -> apkUrl = url
        }
    }
    if (manifestUrl == null || apkUrl == null || !isAllowedUrl(manifestUrl) || !isAllowedUrl(apkUrl)) return null
    val manifest = manifestJson(manifestUrl)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
    val versionCode = manifest.optInt("versionCode", -1)
    val sha = manifest.optString("apkSha256").lowercase()
    val name = manifest.optString("versionName").take(32)
    val packageName = manifest.optString("packageName")
    if (versionCode <= 0 || !SHA256_HEX.matches(sha) || name.isEmpty() || packageName.isEmpty()) return null
    return ReleaseManifest(versionCode, name, sha, packageName, apkUrl)
}

internal fun isAllowedUrl(url: String): Boolean =
    runCatching {
        val parsed = URL(url)
        parsed.protocol == "https" && ALLOWED_HOSTS.any { parsed.host == it || parsed.host.endsWith(".$it") }
    }.getOrDefault(false)

sealed interface UpdateCheck {
    data class Available(val manifest: ReleaseManifest) : UpdateCheck
    data object UpToDate : UpdateCheck
    data class Failed(val message: String) : UpdateCheck
}

/**
 * In-app updater for the public GitHub release channel. The APK is verified
 * (SHA-256, package name, same signing certificate as the installed app) before
 * Android's package installer asks the user to confirm.
 */
class AppUpdater(context: Context, private val repository: String = DEFAULT_REPOSITORY) {
    private val appContext = context.applicationContext

    /** Blocking; call off the main thread. */
    fun check(): UpdateCheck {
        val release =
            httpGetText("https://api.github.com/repos/$repository/releases/latest")
                ?: return UpdateCheck.Failed("Impossible de joindre GitHub.")
        val manifest =
            parseLatestRelease(release) { httpGetText(it) }
                ?: return UpdateCheck.Failed("Release GitHub incomplète ou invalide.")
        if (manifest.packageName != appContext.packageName) return UpdateCheck.Failed("La release vise une autre application.")
        return if (manifest.versionCode > installedVersionCode()) UpdateCheck.Available(manifest) else UpdateCheck.UpToDate
    }

    /** Blocking download and verification, then hands the APK to the system installer. */
    fun downloadAndInstall(manifest: ReleaseManifest, onStatus: (String) -> Unit) {
        val directory = File(appContext.cacheDir, "updates").apply { mkdirs() }
        directory.listFiles()?.forEach(File::delete)
        val apk = File(directory, "gpsx-${manifest.versionCode}.apk")
        onStatus("Téléchargement de GPSx ${manifest.versionName}…")
        if (!download(manifest.apkUrl, apk)) return onStatus("Échec du téléchargement.")
        if (sha256(apk) != manifest.apkSha256) {
            apk.delete()
            return onStatus("Empreinte SHA-256 invalide : mise à jour refusée.")
        }
        val archive = archiveInfo(apk)
        if (archive == null || archive.packageName != appContext.packageName) {
            apk.delete()
            return onStatus("APK inattendu : mise à jour refusée.")
        }
        if (signerDigests(archive) != signerDigests(installedPackageInfo())) {
            apk.delete()
            return onStatus("Signature différente de l’application installée : mise à jour refusée.")
        }
        onStatus("Confirmez l’installation à l’écran.")
        install(apk, onStatus)
    }

    private fun install(apk: File, onStatus: (String) -> Unit) {
        val installer = appContext.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(appContext.packageName)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("gpsx.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val action = "${appContext.packageName}.INSTALL_STATUS.$sessionId"
            registerStatusReceiver(action, onStatus)
            val flags =
                PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(appContext, sessionId, Intent(action).setPackage(appContext.packageName), flags)
            session.commit(pending.intentSender)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerStatusReceiver(action: String, onStatus: (String) -> Unit) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            @Suppress("DEPRECATION")
                            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
                            return
                        }
                        PackageInstaller.STATUS_SUCCESS -> onStatus("Mise à jour installée.")
                        PackageInstaller.STATUS_FAILURE_ABORTED -> onStatus("Installation annulée.")
                        else -> onStatus("Installation refusée par Android.")
                    }
                    runCatching { context.unregisterReceiver(this) }
                }
            }
        val filter = IntentFilter(action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(receiver, filter)
        }
    }

    private fun httpGetText(url: String): String? =
        open(url)?.let { connection ->
            try {
                if (connection.responseCode != 200 || !isAllowedUrl(connection.url.toString())) return null
                connection.inputStream.use { input ->
                    val bytes = input.readNBytesCompat(MAX_JSON_BYTES + 1)
                    if (bytes.size > MAX_JSON_BYTES) null else String(bytes, Charsets.UTF_8)
                }
            } catch (_: java.io.IOException) {
                null
            } finally {
                connection.disconnect()
            }
        }

    private fun download(url: String, target: File): Boolean =
        open(url)?.let { connection ->
            try {
                if (connection.responseCode != 200 || !isAllowedUrl(connection.url.toString())) return false
                var total = 0L
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_APK_BYTES) return false
                            output.write(buffer, 0, read)
                        }
                    }
                }
                true
            } catch (_: java.io.IOException) {
                false
            } finally {
                connection.disconnect()
            }
        } ?: false

    private fun open(url: String): HttpURLConnection? {
        if (!isAllowedUrl(url)) return null
        return (URL(url).openConnection() as HttpsURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream")
            setRequestProperty("User-Agent", "GPSx-updater")
        }
    }

    private fun installedVersionCode(): Long {
        val info = installedPackageInfo()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }

    private fun installedPackageInfo(): PackageInfo =
        appContext.packageManager.getPackageInfo(appContext.packageName, signatureFlags())

    private fun archiveInfo(apk: File): PackageInfo? =
        appContext.packageManager.getPackageArchiveInfo(apk.absolutePath, signatureFlags())

    @Suppress("DEPRECATION")
    private fun signatureFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners
            } else {
                info.signatures
            }
        return signatures.orEmpty().map { digest(it.toByteArray()) }.toSet()
    }

    private fun sha256(file: File): String =
        file.inputStream().use { input ->
            val md = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                md.update(buffer, 0, read)
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val DEFAULT_REPOSITORY = "xalacaga/GPSx"
        const val MAX_JSON_BYTES = 256 * 1024
        const val MAX_APK_BYTES = 200L * 1024 * 1024
    }
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
