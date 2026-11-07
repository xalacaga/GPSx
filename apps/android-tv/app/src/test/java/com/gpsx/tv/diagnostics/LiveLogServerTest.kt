// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.diagnostics

import java.io.InputStream
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveLogServerTest {
    @Test
    fun `secret lines are dropped and addresses masked`() {
        assertNull(LiveLogServer.sanitizeLogLine("I/Chiaki: [KAMAJI] session created (onlineId someone)"))
        assertNull(LiveLogServer.sanitizeLogLine("I/Chiaki: npsso=abc"))
        assertNull(LiveLogServer.sanitizeLogLine("I/Chiaki: handshake ok"))
        assertEquals("E/Chiaki: connect to x.x.x.x failed", LiveLogServer.sanitizeLogLine("E/Chiaki: connect to 203.0.113.9 failed"))
    }

    @Test
    fun `a log ended by close does not throw out of the reading thread`() {
        val ended =
            object : InputStream() {
                override fun read(): Int = throw InterruptedIOException("read interrupted")
            }

        LiveLogServer().read(ended)
    }

    private fun get(url: String): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            val code = connection.responseCode
            code to ((if (code < 400) connection.inputStream else connection.errorStream)?.use { String(it.readBytes()) } ?: "")
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun `serves kept lines from a given number behind the code`() {
        // A command that prints nothing keeps the test independent of a real logcat.
        val server = LiveLogServer(logcatCommand = listOf("java", "-version"))
        val address = server.start("127.0.0.1", bindHost = "127.0.0.1", port = 0)
        assertNotNull(address)
        try {
            server.append("W/Chiaki: Missing reference frame 5 for decoding frame 6")
            server.append("I/Chiaki: token hidden")
            server.append("E/MediaCodec: codec error")
            val base = "http://" + address!!
            val (code, all) = get(base)
            assertEquals(200, code)
            assertTrue(all.contains("Missing reference frame"))
            assertFalse(all.contains("token"))
            val (_, tail) = get("$base?from=1")
            assertTrue(tail.startsWith("# next 2\n"))
            assertFalse(tail.contains("Missing reference frame"))
            assertTrue(tail.contains("codec error"))
            assertEquals(404, get(base.substringBeforeLast('/') + "/wrong").first)
        } finally {
            server.close()
        }
    }
}
