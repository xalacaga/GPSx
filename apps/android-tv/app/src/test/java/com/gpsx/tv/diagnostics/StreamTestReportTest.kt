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
    fun `report carries no network identity or secret fields`() {
        val text = buildStreamTestReport(context, "a", "b", "fin", samples(10)).toString()
        listOf("ssid", "bssid", "host", "ip", "registKey", "npsso", "token", "mac").forEach { field ->
            assertFalse("unexpected field $field", Regex("\"$field\"", RegexOption.IGNORE_CASE).containsMatchIn(text))
        }
    }

    @Test
    fun `github token, repository and path validation`() {
        assertTrue(isPlausibleGitHubToken("github_pat_" + "A1".repeat(20)))
        assertTrue(isPlausibleGitHubToken("ghp_" + "a".repeat(36)))
        assertFalse(isPlausibleGitHubToken("password123"))
        assertFalse(isPlausibleGitHubToken(""))
        assertTrue(isValidRepository("xalacaga/GPSx-diagnostics"))
        assertFalse(isValidRepository("../etc"))
        assertFalse(isValidRepository("a/b/c"))
        assertEquals("results/2026/10/20261031-100000-console.json", reportPath("20261031-100000-console.json"))
        assertEquals("results/odd.json", reportPath("odd.json"))
    }
}
