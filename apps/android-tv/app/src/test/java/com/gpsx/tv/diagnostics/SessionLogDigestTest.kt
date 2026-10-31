// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLogDigestTest {
    @Test
    fun `groups warnings with digits masked and keeps setup lines`() {
        val log =
            sequenceOf(
                "[I] Initializing decoder with mime video/avc",
                "[I] Takion packet received",
                "[W] Detected missing or corrupt frame(s) from 3 to 9",
                "[W] Detected missing or corrupt frame(s) from 12 to 40",
                "[E] AMediaCodec_queueInputBuffer() failed: -10000",
            )
        val digest = digestSessionLog(log)
        assertEquals(3, digest.size)
        assertEquals(LogDigestEntry(1, "[I] Initializing decoder with mime video/avc"), digest[0])
        assertEquals(LogDigestEntry(2, "[W] Detected missing or corrupt frame(s) from # to #"), digest[1])
        assertTrue(digest.none { it.message.contains("Takion packet") })
    }

    @Test
    fun `drops lines that may carry secrets and masks addresses`() {
        val digest =
            digestSessionLog(
                sequenceOf(
                    "[W] Using handshake key abcdef",
                    "[E] Regist failed",
                    "[W] Connect to 192.168.1.20 timed out",
                    "[W] Hash 0123456789abcdef0123 mismatch",
                ),
            )
        assertEquals(listOf("[W] Connect to #.#.#.# timed out", "[W] Hash … mismatch"), digest.map(LogDigestEntry::message))
        assertFalse(digest.any { it.message.contains("192") })
    }

    @Test
    fun `keeps only the most frequent messages`() {
        val lines = (0 until 20).flatMap { i -> List(i + 1) { "[W] message ${'a' + i}" } }.asSequence()
        val digest = digestSessionLog(lines)
        assertEquals(MAX_LOG_DIGEST_ENTRIES, digest.size)
        assertEquals(20, digest.maxOf(LogDigestEntry::count))
    }
}
