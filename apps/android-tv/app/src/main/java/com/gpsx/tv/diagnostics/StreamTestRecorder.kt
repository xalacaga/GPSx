// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.diagnostics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import com.gpsx.tv.BuildConfig
import com.metallic.chiaki.lib.ConnectVideoProfile
import com.metallic.chiaki.lib.StreamMetrics
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class StoredReport(
    val file: File,
    val name: String,
    val kind: String,
    val durationSeconds: Double,
    val fpsP50: Double?,
    val rttP95: Double?,
)

/**
 * Records one JSON report per streamed session under app-private storage. Reports
 * hold measurements and non-secret device/network context only.
 */
class StreamTestRecorder(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, "test-reports")
    private val lock = Any()
    private var active: StreamTestContext? = null
    private var startedAt = 0L
    private var startedElapsed = 0L
    private val samples = ArrayList<StreamSample>()

    fun begin(kind: String, profile: ConnectVideoProfile, hardwareHevc: Boolean) =
        synchronized(lock) {
            active =
                StreamTestContext(
                    appVersion = BuildConfig.VERSION_NAME,
                    sourceRevision = BuildConfig.SOURCE_REVISION,
                    manufacturer = Build.MANUFACTURER.take(64),
                    model = Build.MODEL.take(64),
                    androidRelease = Build.VERSION.RELEASE.take(16),
                    sdk = Build.VERSION.SDK_INT,
                    evidenceClass = if (isEmulator()) "EMULATOR" else "DEVICE",
                    sessionKind = kind,
                    requestedWidth = profile.width,
                    requestedHeight = profile.height,
                    requestedFps = profile.maxFPS,
                    requestedBitrateKbps = profile.bitrate,
                    codec = profile.codec.name,
                    hardwareHevc = hardwareHevc,
                    network = networkInfo(),
                )
            startedAt = System.currentTimeMillis()
            startedElapsed = SystemClock.elapsedRealtime()
            samples.clear()
        }

    fun sample(metrics: StreamMetrics, audioRoute: String, probeRttMs: Double?) =
        synchronized(lock) {
            if (active == null || samples.size >= MAX_SAMPLES) return@synchronized
            val t = (SystemClock.elapsedRealtime() - startedElapsed) / 1000.0
            samples +=
                StreamSample(
                    t, metrics.fps, metrics.bitrateMbps, metrics.rttMs, metrics.packetLoss, metrics.framesLost, metrics.width, metrics.height,
                    metrics.audioState, metrics.audioUnderflows, metrics.audioOverflows, metrics.audioReopens, metrics.audioCallbacks,
                    audioRoute,
                    metrics.videoSamplesWithoutDecoder,
                    metrics.videoSamplesQueued,
                    metrics.videoFramesRendered,
                    metrics.videoDecodeLatencyMs,
                    metrics.videoFramesDroppedLate,
                    metrics.videoDecodedCodec,
                    probeRttMs,
                )
        }

    /** Writes the report when at least [MIN_SAMPLES] seconds were recorded. */
    fun finish(endReason: String, logDigest: List<LogDigestEntry> = emptyList()) {
        val (context, recorded, start) =
            synchronized(lock) {
                val current = active ?: return
                active = null
                Triple(current, samples.toList(), startedAt)
            }
        if (recorded.size < MIN_SAMPLES) return
        runCatching {
            directory.mkdirs()
            val stamp = fileStamp(start)
            val json = buildStreamTestReport(context, iso(start), iso(System.currentTimeMillis()), endReason, recorded, logDigest)
            File(directory, "$stamp-${context.sessionKind}.json").writeText(json.toString(), Charsets.UTF_8)
            prune()
        }
    }

    fun list(): List<StoredReport> =
        directory.listFiles { file -> file.name.endsWith(".json") }
            ?.sortedByDescending(File::getName)
            ?.mapNotNull { file ->
                runCatching {
                    val root = org.json.JSONObject(file.readText(Charsets.UTF_8))
                    val summary = root.getJSONObject("summary")
                    StoredReport(
                        file = file,
                        name = file.name,
                        kind = root.getJSONObject("stream").optString("kind"),
                        durationSeconds = summary.optDouble("durationSeconds", 0.0),
                        fpsP50 = summary.getJSONObject("fps").optDouble("p50").takeUnless(Double::isNaN),
                        rttP95 = summary.getJSONObject("rttMs").optDouble("p95").takeUnless(Double::isNaN),
                    )
                }.getOrNull()
            }.orEmpty()

    fun load(report: StoredReport): org.json.JSONObject? =
        runCatching { org.json.JSONObject(report.file.readText(Charsets.UTF_8)) }.getOrNull()

    /** Deletes every stored report; returns how many were removed. A session in progress still saves its own. */
    fun deleteAll(): Int =
        directory.listFiles { file -> file.name.endsWith(".json") }?.count(File::delete) ?: 0

    private fun prune() {
        directory.listFiles()?.sortedByDescending(File::getName)?.drop(MAX_REPORTS)?.forEach(File::delete)
    }

    @Suppress("DEPRECATION")
    private fun networkInfo(): NetworkInfo {
        val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
        val capabilities = runCatching { connectivity?.getNetworkCapabilities(connectivity.activeNetwork) }.getOrNull()
        val transport =
            when {
                capabilities == null -> "none"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                else -> "other"
            }
        if (transport != "wifi") return NetworkInfo(transport, null, null, null, null)
        // Frequency, link speed and RSSI only: SSID and BSSID are never read.
        val info = runCatching { appContext.getSystemService(WifiManager::class.java)?.connectionInfo }.getOrNull()
        val standard =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && info != null) {
                when (info.wifiStandard) {
                    4 -> "802.11n"
                    5 -> "802.11ac"
                    6 -> "802.11ax"
                    7 -> "802.11ad"
                    8 -> "802.11be"
                    1 -> "legacy"
                    else -> "unknown"
                }
            } else {
                null
            }
        return NetworkInfo(transport, info?.frequency, info?.linkSpeed, info?.rssi, standard)
    }

    private fun isEmulator(): Boolean =
        Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish") || Build.PRODUCT.startsWith("sdk")

    private fun iso(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))

    private fun fileStamp(millis: Long): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))

    private companion object {
        const val MIN_SAMPLES = 5
        const val MAX_REPORTS = 30
    }
}
