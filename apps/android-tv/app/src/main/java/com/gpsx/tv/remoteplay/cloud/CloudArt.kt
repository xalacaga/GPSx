// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.remoteplay.cloud

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

private const val MAX_ART_BYTES = 8 * 1024 * 1024

/** Width of blurred art: tiny on purpose, it is scaled up into a soft blur. */
const val BLURRED_ART_WIDTH = 48

/** Width of catalog card thumbnails. */
const val THUMBNAIL_ART_WIDTH = 320

/**
 * Blocking. Downloads catalog art over https (no cookies, bounded size) and returns it scaled to
 * [targetWidth] pixels wide. At [BLURRED_ART_WIDTH], scaling it back up gives a blur on every API
 * level (Compose's blur modifier needs API 31; the GP520 runs API 30). Null on any failure.
 */
fun loadArt(url: String, targetWidth: Int): Bitmap? {
    if (!url.startsWith("https://")) return null
    val bytes =
        runCatching {
            val connection = URL(url).openConnection() as HttpsURLConnection
            try {
                connection.connectTimeout = 5_000
                connection.readTimeout = 8_000
                if (connection.responseCode != 200) return null
                connection.inputStream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (out.size() + n > MAX_ART_BYTES) throw IOException("art too large")
                        out.write(buffer, 0, n)
                    }
                    out.toByteArray()
                }
            } finally {
                connection.disconnect()
            }
        }.getOrNull() ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetWidth * 2) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    if (decoded.width <= targetWidth) return decoded
    val height = (decoded.height * targetWidth / decoded.width.toFloat()).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(decoded, targetWidth, height, true).also { if (it !== decoded) decoded.recycle() }
}

/**
 * Process-wide art cache: at most three downloads at once (the catalog shows up to 30 cards and a
 * TV box has little bandwidth to spare), decoded bitmaps kept within about 32 MB.
 */
object CloudArtCache {
    private val dispatcher = Executors.newFixedThreadPool(3) { Thread(it, "gpsx-art").apply { isDaemon = true } }.asCoroutineDispatcher()
    private val cache =
        object : LruCache<String, Bitmap>(32 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }
    private val failed = mutableSetOf<String>()

    fun cached(url: String, width: Int): Bitmap? = cache.get("$width|$url")

    suspend fun load(url: String, width: Int): Bitmap? {
        val key = "$width|$url"
        cache.get(key)?.let { return it }
        if (synchronized(failed) { key in failed }) return null
        // A smaller version (the blurred background) is cut from a cached thumbnail rather than downloaded again.
        val thumbnail = if (width < THUMBNAIL_ART_WIDTH) cache.get("$THUMBNAIL_ART_WIDTH|$url") else null
        val bitmap =
            withContext(dispatcher) {
                thumbnail?.let { Bitmap.createScaledBitmap(it, width, (it.height * width / it.width.toFloat()).toInt().coerceAtLeast(1), true) }
                    ?: loadArt(url, width)
            }
        if (bitmap != null) cache.put(key, bitmap) else synchronized(failed) { failed += key }
        return bitmap
    }
}
