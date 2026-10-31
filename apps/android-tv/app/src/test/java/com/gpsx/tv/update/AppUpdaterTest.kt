// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {
    private val sha = "0123456789abcdef".repeat(4)
    private val release =
        """
        {"tag_name": "v0.2.0", "assets": [
          {"name": "gpsx.apk", "browser_download_url": "https://github.com/xalacaga/GPSx/releases/download/v0.2.0/gpsx.apk"},
          {"name": "release.json", "browser_download_url": "https://github.com/xalacaga/GPSx/releases/download/v0.2.0/release.json"}
        ]}
        """.trimIndent()
    private val manifest = """{"versionCode": 2, "versionName": "0.2.0", "apkSha256": "$sha", "packageName": "com.gpsx.tv"}"""

    @Test
    fun `parses a complete release`() {
        val parsed = parseLatestRelease(release) { manifest }!!
        assertEquals(2, parsed.versionCode)
        assertEquals("0.2.0", parsed.versionName)
        assertEquals(sha, parsed.apkSha256)
        assertEquals("com.gpsx.tv", parsed.packageName)
        assertTrue(parsed.apkUrl.endsWith("/gpsx.apk"))
    }

    @Test
    fun `rejects missing assets, bad hashes and foreign hosts`() {
        assertNull(parseLatestRelease("""{"assets": []}""") { manifest })
        assertNull(parseLatestRelease(release) { manifest.replace(sha, "zz") })
        assertNull(parseLatestRelease(release) { """{"versionCode": 0}""" })
        assertNull(parseLatestRelease(release) { null })
        assertNull(parseLatestRelease(release.replace("https://github.com", "https://evil.example")) { manifest })
        assertNull(parseLatestRelease("not json") { manifest })
    }

    @Test
    fun `only https github hosts are allowed`() {
        assertTrue(isAllowedUrl("https://api.github.com/repos/x/y/releases/latest"))
        assertTrue(isAllowedUrl("https://objects.githubusercontent.com/a"))
        assertFalse(isAllowedUrl("http://github.com/a"))
        assertFalse(isAllowedUrl("https://github.com.evil.example/a"))
        assertFalse(isAllowedUrl("https://notgithub.com/a"))
    }
}
