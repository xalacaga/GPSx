// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.remoteplay.cloud

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTokenServerTest {
    private val token = "a".repeat(32) + "B9_-" + "z".repeat(28)

    /** Starts a loopback server; results land in the returned queue (an empty string stands for null). */
    private fun startServer(timeoutMillis: Long = 30_000): Triple<LocalTokenServer, String, ArrayBlockingQueue<String>> {
        val results = ArrayBlockingQueue<String>(1)
        val server = LocalTokenServer(onFinish = { results.add(it ?: "") }, timeoutMillis = timeoutMillis)
        val url = server.start(advertisedHost = "127.0.0.1", bindHost = "127.0.0.1")
        assertNotNull(url)
        return Triple(server, url!!, results)
    }

    private fun request(url: String, body: String? = null): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            val stream = if (code < 400) connection.inputStream else connection.errorStream
            return code to (stream?.use { String(it.readBytes(), Charsets.UTF_8) } ?: "")
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun `secret path serves the form and other paths do not`() {
        val (server, url, _) = startServer()
        try {
            assertTrue(Regex("http://127\\.0\\.0\\.1:\\d+/[a-km-np-z2-9]{26}").matches(url))
            val (code, page) = request(url)
            assertEquals(200, code)
            assertTrue(page.contains("ssocookie"))
            assertEquals(404, request(url.substringBeforeLast('/') + "/guess").first)
        } finally {
            server.close()
        }
    }

    @Test
    fun `invalid token keeps the server open and a valid one finishes it`() {
        val (server, url, results) = startServer()
        try {
            assertEquals(400, request(url, "npsso=short").first)
            assertNull(results.poll())
            val json = URLEncoder.encode("{\"npsso\":\"$token\"}", "UTF-8")
            assertEquals(200, request(url, "npsso=$json").first)
            assertEquals(token, results.poll(5, TimeUnit.SECONDS))
        } finally {
            server.close()
        }
    }

    @Test
    fun `too many wrong paths stop the server without a token`() {
        val (server, url, results) = startServer()
        try {
            val base = url.substringBeforeLast('/')
            repeat(LocalTokenServer.MAX_WRONG_PATHS) { request("$base/x$it") }
            assertEquals("", results.poll(5, TimeUnit.SECONDS))
        } finally {
            server.close()
        }
    }

    @Test
    fun `server expires without a token`() {
        val (server, _, results) = startServer(timeoutMillis = 300)
        try {
            assertEquals("", results.poll(5, TimeUnit.SECONDS))
        } finally {
            server.close()
        }
    }

    @Test
    fun `close does not report a result`() {
        val (server, _, results) = startServer()
        server.close()
        assertNull(results.poll(1_500, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `form field decoding`() {
        assertEquals("a b+c", LocalTokenServer.formField("x=1&npsso=a+b%2Bc", "npsso"))
        assertNull(LocalTokenServer.formField("x=1", "npsso"))
    }
}
