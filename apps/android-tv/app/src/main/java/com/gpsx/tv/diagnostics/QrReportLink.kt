// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.diagnostics

import java.net.URLEncoder
import java.util.Locale
import org.json.JSONObject

/** Largest link that still gives a QR code a phone can scan from a TV screen. */
internal const val MAX_QR_URL_LENGTH = 1_800
internal const val DIAGNOSTICS_REPOSITORY = "xalacaga/GPSx-diagnostics"

/** Repository path for a report file: results/YYYY/MM/<name>. */
internal fun reportPath(fileName: String): String {
    val year = fileName.take(4)
    val month = fileName.drop(4).take(2)
    return if (year.length == 4 && month.length == 2 && (year + month).all(Char::isDigit)) {
        "results/$year/$month/$fileName"
    } else {
        "results/$fileName"
    }
}

/**
 * Plain-text summary of a `gpsx-stream-test/1` report, with at most [maxSamples]
 * evenly spaced samples. Uses only characters that stay short once URL-encoded.
 */
internal fun compactReport(report: JSONObject, maxSamples: Int): String {
    fun num(value: Any?): String =
        when (value) {
            null, JSONObject.NULL -> "na"
            is Number -> {
                val d = value.toDouble()
                if (d == Math.rint(d) && kotlin.math.abs(d) < 1e9) d.toLong().toString() else String.format(Locale.ROOT, "%.2f", d)
            }
            else -> value.toString().replace(Regex("[^A-Za-z0-9._-]"), "-").take(40)
        }
    val app = report.getJSONObject("app")
    val device = report.getJSONObject("device")
    val stream = report.getJSONObject("stream")
    val net = report.getJSONObject("network")
    val summary = report.getJSONObject("summary")
    val audio = summary.optJSONObject("audio") ?: JSONObject()
    fun stat(name: String): String {
        val s = summary.getJSONObject(name)
        return listOf("p50", "p95", "p99", "min", "max").joinToString(" ") { num(s.opt(it)) }
    }
    val lines = mutableListOf(
        report.optString("schema") + " compact",
        "app ${num(app.opt("version"))} ${app.optString("sourceRevision").take(12)}",
        "device ${num(device.opt("manufacturer"))} ${num(device.opt("model"))} android ${num(device.opt("androidRelease"))} sdk ${num(device.opt("sdk"))} ${num(report.opt("evidenceClass"))}",
        "stream ${num(stream.opt("kind"))} ${num(stream.opt("requestedWidth"))}x${num(stream.opt("requestedHeight"))} ${num(stream.opt("requestedFps"))}fps ${num(stream.opt("requestedBitrateKbps"))}kbps ${num(stream.opt("codec"))} hwhevc ${num(stream.opt("hardwareHevc"))}",
        "net ${num(net.opt("transport"))} ${num(net.opt("wifiFrequencyMhz"))}MHz ${num(net.opt("wifiLinkMbps"))}Mbps ${num(net.opt("wifiRssiDbm"))}dBm ${num(net.opt("wifiStandard"))}",
        "time ${num(report.opt("startedAt"))} dur ${num(summary.opt("durationSeconds"))} end ${num(report.opt("endReason"))}",
        "stats p50 p95 p99 min max",
        "fps ${stat("fps")}",
        "mbps ${stat("bitrateMbps")}",
        "rtt ${stat("rttMs")}",
        "loss ${stat("packetLoss")}",
        "lost ${num(summary.opt("framesLost"))} audio state ${num(audio.opt("finalState"))} played ${num(audio.opt("everPlayed"))} under ${num(audio.opt("underflows"))} over ${num(audio.opt("overflows"))} reopen ${num(audio.opt("reopens"))}",
        "samples t fps mbps rtt loss lost audio",
    )
    val samples = report.getJSONArray("samples")
    val count = samples.length()
    if (count > 0 && maxSamples > 0) {
        val step = maxOf(1, kotlin.math.ceil(count / maxSamples.toDouble()).toInt())
        for (index in 0 until count step step) {
            val s = samples.getJSONArray(index)
            val audioState = if (s.length() > 8) num(s.opt(8)) else "na"
            lines += listOf(num(s.opt(0)), num(s.opt(1)), num(s.opt(2)), num(s.opt(3)), num(s.opt(4)), num(s.opt(5)), audioState).joinToString(" ")
        }
    }
    return lines.joinToString("\n") + "\n"
}

/**
 * GitHub "new file" link that pre-fills the compact report; the owner opens it on a
 * signed-in phone and commits it. No credential ever reaches the device.
 * Returns null when even a sample-free summary does not fit [MAX_QR_URL_LENGTH].
 */
fun reportUploadUrl(report: JSONObject, fileName: String, repository: String = DIAGNOSTICS_REPOSITORY): String? {
    val path = reportPath(fileName.removeSuffix(".json") + ".txt")
    for (samples in listOf(30, 20, 12, 6, 0)) {
        val url =
            "https://github.com/$repository/new/main?filename=" + URLEncoder.encode(path, "UTF-8") +
                "&value=" + URLEncoder.encode(compactReport(report, samples), "UTF-8")
        if (url.length <= MAX_QR_URL_LENGTH) return url
    }
    return null
}
