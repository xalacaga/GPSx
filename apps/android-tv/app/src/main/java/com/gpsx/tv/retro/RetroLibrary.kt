// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.retro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import com.gpsx.tv.remoteplay.cloud.EncryptedSecretStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import jcifs.smb.SmbAuthException

data class RetroSnapshot(
    val shareUrl: String = ShareAddress.DEFAULT,
    /** NAS account name; empty for guest access. The password never leaves [RetroLibrary]. */
    val user: String = "",
    val hasPassword: Boolean = false,
    /** True after the NAS refused the current account (or guest access). */
    val signInRequired: Boolean = false,
    val scanning: Boolean = false,
    val detail: String = "",
    val shelves: List<RetroShelf> = emptyList(),
)

/**
 * The Retro library shown in the Consoles menu: the owner's NAS address, the systems found on it
 * and their games. Scans run on one worker thread; listeners are called on the main thread.
 * See docs/RETROGAMING_PLAN.md (step R1).
 */
class RetroLibrary(
    context: Context,
    private val sourceFactory: (String, SmbCredentials?) -> RomSource = ::SmbRomSource,
) : AutoCloseable {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val passwordStore = EncryptedSecretStore(context.applicationContext, SECRET_PREFERENCES, SECRET_KEY_ALIAS) { it.length in 1..256 }
    private val coverDir = File(context.applicationContext.cacheDir, "retro-covers")
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "gpsx-retro").apply { isDaemon = true } }
    private val images = Executors.newFixedThreadPool(2) { runnable -> Thread(runnable, "gpsx-retro-art").apply { isDaemon = true } }
    private val artCache = object : LruCache<String, Bitmap>(ART_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val lock = Any()
    private val closed = AtomicBoolean(false)
    private var listener: ((RetroSnapshot) -> Unit)? = null
    private var snapshot =
        RetroSnapshot(
            shareUrl = preferences.getString(KEY_SHARE, null) ?: ShareAddress.DEFAULT,
            user = preferences.getString(KEY_USER, null).orEmpty(),
        )
    @Volatile private var source: RomSource = createSource(snapshot.shareUrl, snapshot.user, passwordStore.load())

    init {
        refresh()
    }

    fun snapshot(): RetroSnapshot = synchronized(lock) { snapshot }

    fun setListener(value: ((RetroSnapshot) -> Unit)?) {
        val current = synchronized(lock) {
            listener = value
            snapshot
        }
        value?.invoke(current)
    }

    /**
     * Saves the share address (UNC path or smb:// URL) and NAS account, then rescans; false when the
     * address cannot be read. A blank [password] keeps the stored one for the same [user]; a blank
     * [user] means guest access and forgets the password.
     */
    fun setAccess(rawShare: String, user: String, password: String): Boolean {
        val url = ShareAddress.normalise(rawShare) ?: return false
        val account = user.trim().take(128)
        val previous = snapshot()
        when {
            account.isEmpty() -> passwordStore.clear()
            password.isNotEmpty() -> if (!passwordStore.save(password)) return false
            account != previous.user -> passwordStore.clear()
        }
        preferences.edit().putString(KEY_SHARE, url).putString(KEY_USER, account).apply()
        source = createSource(url, account, passwordStore.load())
        artCache.evictAll()
        update { copy(shareUrl = url, user = account, shelves = emptyList(), signInRequired = false) }
        refresh()
        return true
    }

    fun refresh() {
        if (closed.get()) return
        update { copy(scanning = true, detail = "Lecture de ${ShareAddress.display(shareUrl)}…") }
        worker.execute {
            val current = source
            val started = System.nanoTime()
            val result = runCatching { ShareScanner.scan(current) }
            if (current !== source) return@execute
            result
                .onSuccess { shelves ->
                    val games = shelves.sumOf { it.games.size }
                    Log.i(TAG, "share scanned: ${shelves.size} systems, $games games in ${(System.nanoTime() - started) / 1_000_000} ms")
                    update {
                        copy(
                            scanning = false,
                            shelves = shelves,
                            detail =
                                if (shelves.isEmpty()) {
                                    "Aucun dossier de console reconnu sur ${ShareAddress.display(shareUrl)}."
                                } else {
                                    "${shelves.size} consoles, $games jeux sur ${ShareAddress.display(shareUrl)}."
                                },
                        )
                    }
                }
                .onFailure { failure ->
                    Log.w(TAG, "share scan failed: ${failure.javaClass.simpleName}")
                    if (failure is SmbAuthException) {
                        update {
                            copy(
                                scanning = false,
                                signInRequired = true,
                                detail =
                                    if (user.isEmpty()) {
                                        "Le NAS demande un identifiant : renseignez-le dans « Accès au NAS »."
                                    } else {
                                        "Identifiant ou mot de passe refusé par le NAS."
                                    },
                            )
                        }
                    } else {
                        update { copy(scanning = false, detail = "NAS injoignable : ${ShareAddress.display(shareUrl)} (${failure.javaClass.simpleName}).") }
                    }
                }
        }
    }

    /** Opens a file of the share; blocking, call it off the main thread. */
    fun openGame(path: List<String>): java.io.InputStream = source.open(path)

    /** The cover of [game] scaled to about [width] pixels if already in memory. */
    fun cachedArt(game: RetroGame, width: Int): Bitmap? = artKey(game, width)?.let(artCache::get)

    /**
     * Loads the cover of [game]: the `gamelist.xml` image from the share, else the libretro box art,
     * which is kept on disk so each cover is downloaded once. [onLoaded] gets null when there is none.
     */
    fun loadArt(game: RetroGame, width: Int, onLoaded: (Bitmap?) -> Unit) {
        val key = artKey(game, width)
        if (key == null || closed.get()) return onLoaded(null)
        artCache.get(key)?.let { return onLoaded(it) }
        val current = source
        images.execute {
            val bitmap =
                runCatching {
                    val bytes =
                        if (game.image.isNotEmpty()) current.open(game.image).use { it.readBytes() } else downloadCover(game.coverUrl)
                    bytes?.let { decodeScaled(it, width) }
                }.getOrNull()
            bitmap?.let { artCache.put(key, it) }
            main.post { onLoaded(bitmap) }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(lock) { listener = null }
        worker.shutdownNow()
        images.shutdownNow()
    }

    private val shareUrl: String get() = snapshot().shareUrl

    private fun createSource(url: String, user: String, password: String?): RomSource {
        val credentials = if (user.isNotEmpty() && password != null) SmbCredentials(user, password) else null
        synchronized(lock) { snapshot = snapshot.copy(hasPassword = password != null) }
        return sourceFactory(url, credentials)
    }

    private fun artKey(game: RetroGame, width: Int): String? =
        when {
            game.image.isNotEmpty() -> "$width:" + game.image.joinToString("/")
            game.coverUrl.isNotEmpty() -> "$width:" + game.coverUrl
            else -> null
        }

    private fun decodeScaled(bytes: ByteArray, width: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /**
     * Box art bytes from the disk cache or the libretro server (HTTPS, normal certificate checks).
     * A missing cover is remembered as an empty file so the server is not asked again.
     */
    private fun downloadCover(url: String): ByteArray? {
        if (!url.startsWith(Thumbnails.SERVER)) return null
        val file = File(coverDir, sha1(url) + ".png")
        if (file.isFile) return file.readBytes().takeIf { it.isNotEmpty() }
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "GPSx")
            val code = connection.responseCode
            coverDir.mkdirs()
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                file.writeBytes(ByteArray(0))
                return null
            }
            if (code != HttpURLConnection.HTTP_OK) return null
            val bytes = connection.inputStream.use { input -> readLimited(input, MAX_COVER_BYTES) } ?: return null
            val partial = File(coverDir, file.name + ".part")
            partial.writeBytes(bytes)
            partial.renameTo(file)
            return bytes
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return out.toByteArray()
            if (out.size() + read > limit) return null
            out.write(buffer, 0, read)
        }
    }

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun update(change: RetroSnapshot.() -> RetroSnapshot) {
        val (next, target) = synchronized(lock) {
            snapshot = snapshot.change()
            snapshot to listener
        }
        if (target != null && !closed.get()) main.post { target(next) }
    }

    private companion object {
        const val TAG = "GPSxRetro"
        const val PREFERENCES = "gpsx_retro"
        const val KEY_SHARE = "share_url"
        const val KEY_USER = "share_user"
        const val SECRET_PREFERENCES = "gpsx_retro_account"
        const val SECRET_KEY_ALIAS = "gpsx_retro_smb_password_v1"
        const val ART_CACHE_BYTES = 24 * 1024 * 1024
        const val MAX_COVER_BYTES = 4 * 1024 * 1024
    }
}
