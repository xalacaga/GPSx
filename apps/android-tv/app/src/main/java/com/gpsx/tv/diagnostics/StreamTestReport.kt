// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.diagnostics

import org.json.JSONArray
import org.json.JSONObject

/** One per-second sample of native stream measurements. */
data class StreamSample(
    val tSeconds: Double,
    val fps: Double,
    val bitrateMbps: Double,
    val rttMs: Double,
    val packetLoss: Double,
    val framesLost: Long,
    val width: Int,
    val height: Int,
    val audioState: Int = 0,
    val audioUnderflows: Long = 0,
    val audioOverflows: Long = 0,
    val audioReopens: Long = 0,
    val audioCallbacks: Long = 0,
    /** Output the stream reported ("Bluetooth", "HDMI", "système" when the API does not say). */
    val audioRoute: String = "",
    val videoSamplesWithoutDecoder: Long = 0,
    val videoSamplesQueued: Long = 0,
    val videoFramesRendered: Long = 0,
    val videoDecodeLatencyMs: Double = 0.0,
    val videoFramesDroppedLate: Long = 0,
    val videoDecodedCodec: Int = 0,
    /** TCP connect time from the TV to the console, measured by GPSx; null for cloud or failed probes. */
    val probeRttMs: Double? = null,
)

/** Non-secret context captured with a test. Never holds addresses, names, keys or tokens. */
data class StreamTestContext(
    val appVersion: String,
    val sourceRevision: String,
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdk: Int,
    val evidenceClass: String,
    val sessionKind: String,
    val requestedWidth: Int,
    val requestedHeight: Int,
    val requestedFps: Int,
    val requestedBitrateKbps: Int,
    val codec: String,
    val hardwareHevc: Boolean,
    val network: NetworkInfo,
)

data class NetworkInfo(
    val transport: String,
    val wifiFrequencyMhz: Int?,
    val wifiLinkMbps: Int?,
    val wifiRssiDbm: Int?,
    val wifiStandard: String?,
)

internal const val REPORT_SCHEMA = "gpsx-stream-test/1"
internal const val MAX_SAMPLES = 4 * 3600

/** Nearest-rank percentile; null for an empty list. */
internal fun percentile(values: List<Double>, p: Double): Double? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val rank = kotlin.math.ceil(p / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
    return sorted[rank - 1]
}

/** Builds the versioned JSON report uploaded to the diagnostics repository. */
fun buildStreamTestReport(
    context: StreamTestContext,
    startedAtIso: String,
    endedAtIso: String,
    endReason: String,
    samples: List<StreamSample>,
): JSONObject {
    // The first 5 s are connection warm-up and are excluded from the summary.
    val steady = samples.filter { it.tSeconds >= WARMUP_SECONDS }
    fun stats(select: (StreamSample) -> Double): JSONObject {
        val values = steady.map(select)
        return JSONObject().apply {
            put("p50", percentile(values, 50.0) ?: JSONObject.NULL)
            put("p95", percentile(values, 95.0) ?: JSONObject.NULL)
            put("p99", percentile(values, 99.0) ?: JSONObject.NULL)
            put("min", values.minOrNull() ?: JSONObject.NULL)
            put("max", values.maxOrNull() ?: JSONObject.NULL)
        }
    }
    return JSONObject().apply {
        put("schema", REPORT_SCHEMA)
        put("evidenceClass", context.evidenceClass)
        put("app", JSONObject().put("version", context.appVersion).put("sourceRevision", context.sourceRevision))
        put(
            "device",
            JSONObject()
                .put("manufacturer", context.manufacturer)
                .put("model", context.model)
                .put("androidRelease", context.androidRelease)
                .put("sdk", context.sdk),
        )
        put(
            "stream",
            JSONObject()
                .put("kind", context.sessionKind)
                .put("requestedWidth", context.requestedWidth)
                .put("requestedHeight", context.requestedHeight)
                .put("requestedFps", context.requestedFps)
                .put("requestedBitrateKbps", context.requestedBitrateKbps)
                .put("codec", context.codec)
                .put("hardwareHevc", context.hardwareHevc),
        )
        put(
            "network",
            JSONObject()
                .put("transport", context.network.transport)
                .put("wifiFrequencyMhz", context.network.wifiFrequencyMhz ?: JSONObject.NULL)
                .put("wifiLinkMbps", context.network.wifiLinkMbps ?: JSONObject.NULL)
                .put("wifiRssiDbm", context.network.wifiRssiDbm ?: JSONObject.NULL)
                .put("wifiStandard", context.network.wifiStandard ?: JSONObject.NULL),
        )
        put("startedAt", startedAtIso)
        put("endedAt", endedAtIso)
        put("endReason", endReason.take(200))
        put(
            "summary",
            JSONObject()
                .put("samples", samples.size)
                .put("steadySamples", steady.size)
                .put("durationSeconds", samples.lastOrNull()?.tSeconds ?: 0.0)
                .put("fps", stats { it.fps })
                .put("bitrateMbps", stats { it.bitrateMbps })
                .put("rttMs", stats { it.rttMs })
                .put("probeRttMs", probeStats(steady))
                .put("decodeLatencyMs", stats { it.videoDecodeLatencyMs })
                .put("packetLoss", stats { it.packetLoss })
                .put("framesLost", samples.lastOrNull()?.framesLost ?: 0L)
                .put(
                    "video",
                    JSONObject()
                        .put("samplesWithoutDecoder", samples.lastOrNull()?.videoSamplesWithoutDecoder ?: 0L)
                        .put("samplesQueued", samples.lastOrNull()?.videoSamplesQueued ?: 0L)
                        .put("framesRendered", samples.lastOrNull()?.videoFramesRendered ?: 0L)
                        .put("framesDroppedLate", samples.lastOrNull()?.videoFramesDroppedLate ?: 0L)
                        .put("decodedCodec", decodedCodecLabel(samples.lastOrNull()?.videoDecodedCodec ?: 0)),
                )
                .put(
                    "audio",
                    JSONObject()
                        .put("everPlayed", samples.any { it.audioState == 1 })
                        .put("finalState", samples.lastOrNull()?.audioState ?: 0)
                        .put("underflows", samples.lastOrNull()?.audioUnderflows ?: 0L)
                        .put("overflows", samples.lastOrNull()?.audioOverflows ?: 0L)
                        .put("reopens", samples.lastOrNull()?.audioReopens ?: 0L)
                        .put("callbacks", samples.lastOrNull()?.audioCallbacks ?: 0L)
                        .put("route", samples.lastOrNull()?.audioRoute ?: "")
                        .put("routes", JSONArray(samples.map { it.audioRoute }.filter(String::isNotEmpty).distinct()))
                        .put("api", when (samples.lastOrNull()?.audioState) { 1 -> "AAudio"; 3 -> "OpenSLES"; 2 -> "failed"; else -> "none" }),
                ),
        )
        put(
            "samples",
            JSONArray().apply {
                samples.take(MAX_SAMPLES).forEach { s ->
                    put(
                        JSONArray()
                            .put(s.tSeconds)
                            .put(s.fps)
                            .put(s.bitrateMbps)
                            .put(s.rttMs)
                            .put(s.packetLoss)
                            .put(s.framesLost)
                            .put(s.width)
                            .put(s.height)
                            .put(s.audioState)
                            .put(s.audioUnderflows),
                    )
                }
            },
        )
        put("sampleFields", JSONArray(listOf("t", "fps", "bitrateMbps", "rttMs", "packetLoss", "framesLost", "width", "height", "audioState", "audioUnderflows")))
    }
}

internal fun decodedCodecLabel(value: Int): String =
    when (value) {
        1 -> "H264"
        2 -> "H265"
        else -> "none"
    }

private fun probeStats(steady: List<StreamSample>): JSONObject {
    val values = steady.mapNotNull(StreamSample::probeRttMs)
    return JSONObject().apply {
        put("p50", percentile(values, 50.0) ?: JSONObject.NULL)
        put("p95", percentile(values, 95.0) ?: JSONObject.NULL)
        put("p99", percentile(values, 99.0) ?: JSONObject.NULL)
        put("min", values.minOrNull() ?: JSONObject.NULL)
        put("max", values.maxOrNull() ?: JSONObject.NULL)
    }
}

private const val WARMUP_SECONDS = 5.0
