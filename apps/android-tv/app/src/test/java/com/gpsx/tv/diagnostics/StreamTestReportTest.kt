// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamTestReportTest {
    private val context =
        StreamTestContext(
            appVersion = "0.2.0",
            sourceRevision = "a".repeat(40),
            manufacturer = "BenQ",
            model = "GP520",
            androidRelease = "11",
            sdk = 30,
            evidenceClass = "DEVICE",
            sessionKind = "console",
            requestedWidth = 1920,
            requestedHeight = 1080,
            requestedFps = 60,
            requestedBitrateKbps = 15_000,
            codec = "H265",
            hardwareHevc = true,
            network = NetworkInfo("wifi", 5180, 866, -52, "802.11ax"),
        )

    private fun samples(count: Int) =
        (0 until count).map { i -> StreamSample(i.toDouble(), 60.0 - (i % 3), 12.0, 10.0 + i, 0.0, i / 10L, 1920, 1080) }

    @Test
    fun `percentile uses nearest rank`() {
        val values = (1..100).map(Int::toDouble)
        assertEquals(50.0, percentile(values, 50.0)!!, 0.0)
        assertEquals(95.0, percentile(values, 95.0)!!, 0.0)
        assertEquals(100.0, percentile(values, 99.5)!!, 0.0)
        assertNull(percentile(emptyList(), 50.0))
    }

    @Test
    fun `report summarises steady samples after warm-up`() {
        val report = buildStreamTestReport(context, "2026-10-31T10:00:00Z", "2026-10-31T10:01:00Z", "Arrêt", samples(60))
        assertEquals(REPORT_SCHEMA, report.getString("schema"))
        val summary = report.getJSONObject("summary")
        assertEquals(60, summary.getInt("samples"))
        assertEquals(55, summary.getInt("steadySamples"))
        assertEquals(15.0, summary.getJSONObject("rttMs").getDouble("min"), 0.0)
        assertEquals(5L, summary.getLong("framesLost"))
        assertEquals(60, report.getJSONArray("samples").length())
        assertEquals("802.11ax", report.getJSONObject("network").getString("wifiStandard"))
    }

    @Test
    fun `report summarises video decoder counters and the console probe`() {
        val probed =
            samples(20).mapIndexed { i, s ->
                s.copy(videoSamplesWithoutDecoder = 3, videoSamplesQueued = 100L + i, videoFramesRendered = 90L + i, videoDecodeLatencyMs = 25.0, probeRttMs = if (i % 2 == 0) 4.0 else null)
            }
        val report = buildStreamTestReport(context, "a", "b", "fin", probed)
        val summary = report.getJSONObject("summary")
        val video = summary.getJSONObject("video")
        assertEquals(3L, video.getLong("samplesWithoutDecoder"))
        assertEquals(119L, video.getLong("samplesQueued"))
        assertEquals(109L, video.getLong("framesRendered"))
        assertEquals(4.0, summary.getJSONObject("probeRttMs").getDouble("p50"), 0.0)
        val text = compactReport(report, 6)
        assertTrue(text.contains("video nodec 3 queued 119 rendered 109"))
        assertTrue(text.contains("probe 4 4 4 4 4"))
        assertTrue(text.contains("decode 25 25 25 25 25"))
    }

    @Test
    fun `report keeps the last and every distinct audio route`() {
        val routed = samples(10).mapIndexed { i, s -> s.copy(audioRoute = if (i < 5) "HDMI" else "Bluetooth") }
        val audio = buildStreamTestReport(context, "a", "b", "fin", routed).getJSONObject("summary").getJSONObject("audio")
        assertEquals("Bluetooth", audio.getString("route"))
        assertEquals(listOf("HDMI", "Bluetooth"), (0 until audio.getJSONArray("routes").length()).map(audio.getJSONArray("routes")::getString))
    }

    @Test
    fun `report carries no network identity or secret fields`() {
        val text = buildStreamTestReport(context, "a", "b", "fin", samples(10)).toString()
        listOf("ssid", "bssid", "host", "ip", "registKey", "npsso", "token", "mac").forEach { field ->
            assertFalse("unexpected field $field", Regex("\"$field\"", RegexOption.IGNORE_CASE).containsMatchIn(text))
        }
    }

    @Test
    fun `qr link pre-fills a compact report in the diagnostics repository`() {
        val report = buildStreamTestReport(context, "2026-10-31T10:00:00Z", "2026-10-31T10:10:00Z", "Arrêt", samples(600))
        val url = reportUploadUrl(report, "20261031-100000-console.json")!!
        assertTrue(url.startsWith("https://github.com/xalacaga/GPSx-diagnostics/issues/new?title=report+20261031-100000-console&labels=report&body="))
        assertTrue(url.length <= MAX_QR_URL_LENGTH)
        val text = compactReport(report, 60)
        assertTrue(text.startsWith("gpsx-stream-test/1 compact\n"))
        assertTrue(text.contains("device BenQ GP520 android 11 sdk 30 DEVICE"))
        assertTrue(text.contains("net wifi 5180MHz 866Mbps -52dBm 802.11ax"))
        assertEquals(60, text.lines().dropWhile { !it.startsWith("samples") }.drop(1).count(String::isNotBlank))
        assertEquals("results/odd.txt", reportPath("odd.txt"))
    }
}
