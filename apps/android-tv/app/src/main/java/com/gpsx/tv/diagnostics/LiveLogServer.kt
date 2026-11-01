// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.diagnostics

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live view of GPSx's own log for remote debugging on the local network, started from Diagnostics.
 * Android lets an app read the log of its own processes, which covers the native engine and the
 * in-process MediaCodec client (the vendor codec service is out of reach). Lines that might carry a
 * secret are dropped and IPv4 addresses are masked before anything is kept.
 *
 * GET /<code>?from=N returns the kept lines numbered N and above as text, after a first line
 * "# next M". The server stops after [timeoutMillis] or [close]; nothing is written to disk.
 */
class LiveLogServer(
    private val timeoutMillis: Long = 30 * 60 * 1000L,
    private val logcatCommand: List<String> = listOf("logcat", "-v", "time"),
) : AutoCloseable {
    val code: String = (1..6).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
    private val closed = AtomicBoolean(false)
    private val lines = ArrayDeque<String>()
    private var firstNumber = 0L
    private var server: ServerSocket? = null
    private var logcat: Process? = null

    /** Starts reading the log and serving it; returns "host:port/code", or null when no port opens. */
    fun start(host: String, bindHost: String = "0.0.0.0", port: Int = LIVE_LOG_PORT): String? {
        val socket =
            runCatching {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(bindHost, port), 4)
                    soTimeout = 500
                }
            }.getOrNull() ?: return null
        server = socket
        logcat = runCatching { ProcessBuilder(logcatCommand).redirectErrorStream(true).start() }.getOrNull()
        logcat?.let { process ->
            Thread({ process.inputStream.bufferedReader().useLines { all -> all.forEach { if (!closed.get()) append(it) } } }, "gpsx-live-log-read")
                .apply { isDaemon = true }.start()
        }
        Thread({ serve(socket) }, "gpsx-live-log").apply { isDaemon = true }.start()
        return "$host:${socket.localPort}/$code"
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { server?.close() }
        runCatching { logcat?.destroy() }
    }

    /** Filters and stores one log line; visible for tests. */
    internal fun append(raw: String) {
        val line = sanitizeLogLine(raw) ?: return
        synchronized(lines) {
            lines.addLast(line)
            while (lines.size > MAX_LINES) {
                lines.removeFirst()
                firstNumber++
            }
        }
    }

    private fun serve(socket: ServerSocket) {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
        while (!closed.get() && System.nanoTime() < deadline) {
            val client =
                try {
                    socket.accept()
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (_: IOException) {
                    break
                }
            client.use { runCatching { handle(it) } }
        }
        close()
    }

    private fun handle(client: Socket) {
        client.soTimeout = 10_000
        val head = readHead(BufferedInputStream(client.getInputStream())) ?: return
        val target = head.substringBefore("\r\n").split(" ").getOrNull(1) ?: return
        val out = client.getOutputStream()
        if (target.substringBefore('?') != "/$code") {
            out.write(response(404, "Introuvable."))
            return
        }
        val from = Regex("from=(\\d+)").find(target)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val body =
            synchronized(lines) {
                val next = firstNumber + lines.size
                val skip = (from - firstNumber).coerceIn(0L, lines.size.toLong()).toInt()
                buildString {
                    append("# next ").append(next).append('\n')
                    lines.drop(skip).forEach { append(it).append('\n') }
                }
            }
        out.write(response(200, body))
    }

    /** The one live log of the app: it outlives the Diagnostics screen while a game is launched elsewhere. */
    object Current {
        @Volatile var address: String? = null
            private set
        private var server: LiveLogServer? = null

        @Synchronized
        fun start(host: String?): String? {
            stop()
            val lan = host ?: return null
            val created = LiveLogServer()
            address = created.start(lan)?.also { server = created }
            return address
        }

        @Synchronized
        fun stop() {
            server?.close()
            server = null
            address = null
        }
    }

    companion object {
        const val LIVE_LOG_PORT = 47900
        internal const val MAX_LINES = 20_000
        private const val ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"
        private val random = SecureRandom()

        private val SECRET_HINTS =
            listOf("key", "token", "npsso", "secret", "auth", "handshake", "session id", "session_id", "sessionid", "cookie", "regist", "password", "onlineid", "accountid", "account_id", "spec")
        private val IPV4 = Regex("\\b\\d{1,3}(\\.\\d{1,3}){3}\\b")

        /** Drops lines that might carry a secret, masks IPv4 addresses; null when the line is dropped. */
        internal fun sanitizeLogLine(raw: String): String? {
            if (SECRET_HINTS.any { raw.contains(it, ignoreCase = true) }) return null
            return IPV4.replace(raw, "x.x.x.x").take(400)
        }

        private fun readHead(input: InputStream): String? {
            val buffer = ByteArrayOutputStream()
            while (buffer.size() < 8 * 1024) {
                val b = input.read()
                if (b < 0) return null
                buffer.write(b)
                val text = buffer.toString(Charsets.ISO_8859_1.name())
                if (text.endsWith("\r\n\r\n")) return text
            }
            return null
        }

        private fun response(code: Int, body: String): ByteArray {
            val payload = body.toByteArray(Charsets.UTF_8)
            val head =
                "HTTP/1.1 $code ${if (code == 200) "OK" else "Not Found"}\r\n" +
                    "Content-Type: text/plain; charset=utf-8\r\nContent-Length: ${payload.size}\r\n" +
                    "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
            return head.toByteArray(Charsets.ISO_8859_1) + payload
        }
    }
}
