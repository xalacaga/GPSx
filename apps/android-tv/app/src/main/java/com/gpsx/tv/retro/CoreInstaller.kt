// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.retro

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Downloads libretro cores from the official build server into app-private storage, on the
 * owner's request (docs/RETROGAMING_PLAN.md). Cores are never part of the repository or the APK.
 * The download uses HTTPS with normal certificate checks; the SHA-256 of each installed core is
 * logged so a given build can be identified.
 */
class CoreInstaller(context: Context) {
    private val appContext = context.applicationContext
    private val coresDir = File(appContext.filesDir, "retro/cores/$abi")

    /** The ABI this process runs, as the build server names it. */
    val abi: String get() = abiFor(appContext.applicationInfo.nativeLibraryDir)

    fun installed(core: String): File? = coreFile(core).takeIf { it.isFile && it.length() > 0 }

    /** Returns the core library, downloading it first if needed; throws [IOException] on failure. */
    fun ensure(core: String, onProgress: (String) -> Unit): File {
        installed(core)?.let { return it }
        coresDir.mkdirs()
        val url = coreUrl(abi, core)
        onProgress("Téléchargement de l’émulateur $core…")
        val connection = URL(url).openConnection() as HttpURLConnection
        val target = coreFile(core)
        val partial = File(coresDir, target.name + ".part")
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "GPSx")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode} pour $core")
            val digest = MessageDigest.getInstance("SHA-256")
            var found = false
            ZipInputStream(connection.inputStream.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory || !entry.name.endsWith(".so")) continue
                    var written = 0L
                    partial.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            written += read
                            if (written > MAX_CORE_BYTES) throw IOException("core $core trop volumineux")
                            digest.update(buffer, 0, read)
                            out.write(buffer, 0, read)
                        }
                    }
                    found = true
                    break
                }
            }
            if (!found) throw IOException("archive du core $core sans bibliothèque")
            if (!partial.renameTo(target)) throw IOException("installation du core $core impossible")
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            Log.i(TAG, "core $core installed for $abi: ${target.length()} bytes, sha256 $hash")
            return target
        } finally {
            partial.delete()
            connection.disconnect()
        }
    }

    private fun coreFile(core: String) = File(coresDir, "${core}_libretro_android.so")

    companion object {
        private const val TAG = "GPSxRetro"
        private const val MAX_CORE_BYTES = 128L * 1024 * 1024
        const val BUILD_SERVER = "https://buildbot.libretro.com/nightly/android/latest/"

        fun coreUrl(abi: String, core: String): String = "$BUILD_SERVER$abi/${core}_libretro_android.so.zip"

        /** `/data/app/…/lib/arm` → `armeabi-v7a`; the folder name is the ABI the app was installed with. */
        fun abiFor(nativeLibraryDir: String): String =
            when (nativeLibraryDir.trimEnd('/').substringAfterLast('/')) {
                "arm64" -> "arm64-v8a"
                "arm" -> "armeabi-v7a"
                "x86_64" -> "x86_64"
                "x86" -> "x86"
                else -> "armeabi-v7a"
            }
    }
}
