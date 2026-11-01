// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.remoteplay.cloud

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PSN sign-in without a third-party relay: while the sign-in screen is open, the TV serves one
 * page on the local network at a random, single-use path. The owner signs in to Sony on a phone,
 * copies the NPSSO value shown by Sony and pastes it into that page.
 *
 * The token crosses the home network once, in plain HTTP, so the server accepts a single valid
 * token, stops after [timeoutMillis] or [MAX_WRONG_PATHS] requests to other paths, and never logs
 * requests or the token. [onFinish] runs once on the server thread: with the token, or with null
 * when the server expired or stopped itself; not after [close].
 */
class LocalTokenServer(
    private val onFinish: (String?) -> Unit,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : AutoCloseable {
    private val secret = newSecret()
    private val closed = AtomicBoolean(false)
    private var server: ServerSocket? = null

    /**
     * Binds on [bindHost] and returns the URL to show in the QR code, or null when no local network
     * address exists or the port cannot be opened. [advertisedHost] defaults to the LAN address.
     */
    fun start(advertisedHost: String? = lanAddress(), bindHost: String = "0.0.0.0", port: Int = 0): String? {
        val host = advertisedHost ?: return null
        val socket =
            runCatching {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(bindHost, port), BACKLOG)
                    soTimeout = ACCEPT_POLL_MILLIS
                }
            }.getOrNull() ?: return null
        server = socket
        Thread({ serve(socket) }, "gpsx-psn-local").apply { isDaemon = true }.start()
        return "http://$host:${socket.localPort}/$secret"
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) runCatching { server?.close() }
    }

    private fun serve(socket: ServerSocket) {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
        var wrongPaths = 0
        var token: String? = null
        while (!closed.get() && token == null && wrongPaths < MAX_WRONG_PATHS && System.nanoTime() < deadline) {
            val client =
                try {
                    socket.accept()
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (_: IOException) {
                    break
                }
            client.use {
                when (val outcome = runCatching { handle(it) }.getOrDefault(Outcome.Ignored)) {
                    is Outcome.Token -> token = outcome.value
                    Outcome.WrongPath -> wrongPaths++
                    Outcome.Ignored -> Unit
                }
            }
        }
        runCatching { socket.close() }
        if (closed.compareAndSet(false, true)) onFinish(token)
    }

    private fun handle(client: Socket): Outcome {
        client.soTimeout = CLIENT_TIMEOUT_MILLIS
        val input = BufferedInputStream(client.getInputStream())
        val head = readHead(input) ?: return Outcome.Ignored
        val lines = head.split("\r\n")
        val parts = lines.first().split(" ")
        if (parts.size < 2) return Outcome.Ignored
        val method = parts[0]
        val path = parts[1].substringBefore('?')
        val out = client.getOutputStream()
        if (path != "/$secret") {
            out.write(response(404, "Not Found", "text/plain", "Introuvable."))
            return if (path == "/favicon.ico") Outcome.Ignored else Outcome.WrongPath
        }
        when (method) {
            "GET" -> out.write(response(200, "OK", "text/html", formPage(error = null)))
            "POST" -> {
                val length =
                    lines.firstNotNullOfOrNull { line ->
                        line.takeIf { it.startsWith("content-length:", ignoreCase = true) }?.substringAfter(':')?.trim()?.toIntOrNull()
                    } ?: 0
                if (length !in 1..MAX_BODY_BYTES) {
                    out.write(response(400, "Bad Request", "text/html", formPage(error = "Envoi invalide.")))
                    return Outcome.Ignored
                }
                val body = String(readExactly(input, length), Charsets.UTF_8)
                val token = formField(body, "npsso")?.let(::normalizeNpsso)
                if (token == null) {
                    out.write(response(400, "Bad Request", "text/html", formPage(error = "Jeton non reconnu : copiez toute la valeur npsso (environ 64 caractères).")))
                    return Outcome.Ignored
                }
                out.write(response(200, "OK", "text/html", donePage()))
                out.flush()
                return Outcome.Token(token)
            }
            else -> out.write(response(405, "Method Not Allowed", "text/plain", "Méthode refusée."))
        }
        return Outcome.Ignored
    }

    private sealed interface Outcome {
        data class Token(val value: String) : Outcome
        data object WrongPath : Outcome
        data object Ignored : Outcome
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10 * 60 * 1000L
        const val SSO_COOKIE_URL = "https://ca.account.sony.com/api/v1/ssocookie"
        internal const val MAX_WRONG_PATHS = 20
        private const val BACKLOG = 4
        private const val ACCEPT_POLL_MILLIS = 500
        private const val CLIENT_TIMEOUT_MILLIS = 10_000
        private const val MAX_HEAD_BYTES = 8 * 1024
        private const val MAX_BODY_BYTES = 4 * 1024
        private const val ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"
        private val random = SecureRandom()

        /** 26 characters from a 32-letter alphabet: 130 bits, unguessable on a home network. */
        internal fun newSecret(): String = (1..26).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

        /** First private IPv4 address of an active interface (Wi-Fi or Ethernet), or null. */
        fun lanAddress(): String? =
            runCatching {
                NetworkInterface.getNetworkInterfaces().toList()
                    .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                    .flatMap { it.inetAddresses.toList() }
                    .filterIsInstance<Inet4Address>()
                    .firstOrNull { it.isSiteLocalAddress }
                    ?.hostAddress
            }.getOrNull()

        internal fun formField(body: String, name: String): String? =
            body.split('&').firstNotNullOfOrNull { pair ->
                val key = pair.substringBefore('=')
                if (key == name) runCatching { URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8") }.getOrNull() else null
            }

        private fun readHead(input: InputStream): String? {
            val buffer = ByteArrayOutputStream()
            var matched = 0
            while (buffer.size() < MAX_HEAD_BYTES) {
                val b = input.read()
                if (b < 0) return null
                buffer.write(b)
                matched =
                    when {
                        (matched % 2 == 0 && b == '\r'.code) || (matched % 2 == 1 && b == '\n'.code) -> matched + 1
                        b == '\r'.code -> 1
                        else -> 0
                    }
                if (matched == 4) return buffer.toString(Charsets.ISO_8859_1.name()).removeSuffix("\r\n\r\n")
            }
            return null
        }

        private fun readExactly(input: InputStream, length: Int): ByteArray {
            val data = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = input.read(data, read, length - read)
                if (n < 0) throw IOException("truncated body")
                read += n
            }
            return data
        }

        private fun response(code: Int, reason: String, type: String, body: String): ByteArray {
            val payload = body.toByteArray(Charsets.UTF_8)
            val head =
                "HTTP/1.1 $code $reason\r\n" +
                    "Content-Type: $type; charset=utf-8\r\n" +
                    "Content-Length: ${payload.size}\r\n" +
                    "Cache-Control: no-store\r\n" +
                    "Referrer-Policy: no-referrer\r\n" +
                    "X-Content-Type-Options: nosniff\r\n" +
                    "Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'\r\n" +
                    "Connection: close\r\n\r\n"
            return head.toByteArray(Charsets.ISO_8859_1) + payload
        }

        private const val STYLE =
            "body{margin:0;padding:24px 16px;background:#0c0c0d;color:#f4f1ea;font:16px/1.5 system-ui,sans-serif}" +
                "main{max-width:560px;margin:0 auto}h1{font-size:26px;margin:0 0 4px}h1 b{color:#ff5a1f}" +
                "ol{padding-left:20px}li{margin:10px 0}a{color:#ff5a1f;font-weight:700;word-break:break-all}" +
                "textarea{box-sizing:border-box;width:100%;height:96px;background:#161618;color:#f4f1ea;border:2px solid #2e2e33;" +
                "padding:10px;font:14px monospace}button{margin-top:12px;width:100%;padding:14px;border:0;background:#ff5a1f;" +
                "color:#0c0c0d;font-weight:900;font-size:17px}.err{color:#ffc400;font-weight:700}.muted{color:#8d8a84;font-size:13px}"

        private fun formPage(error: String?): String =
            "<!doctype html><html lang=\"fr\"><head><meta charset=\"utf-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>GPSx – connexion PSN</title>" +
                "<style>$STYLE</style></head><body><main><h1><b>G</b>PSx · connexion PSN</h1>" +
                "<p class=\"muted\">Cette page est servie par votre TV, sur votre réseau local uniquement.</p><ol>" +
                "<li>Connectez-vous à votre compte Sony sur <a href=\"https://www.playstation.com/\" target=\"_blank\" rel=\"noopener\">playstation.com</a>.</li>" +
                "<li>Dans le même navigateur, ouvrez <a href=\"$SSO_COOKIE_URL\" target=\"_blank\" rel=\"noopener\">$SSO_COOKIE_URL</a>. " +
                "Sony affiche <code>{\"npsso\":\"…\"}</code>.</li>" +
                "<li>Copiez la valeur npsso (ou tout le texte) et collez-la ci-dessous.</li></ol>" +
                (if (error != null) "<p class=\"err\">$error</p>" else "") +
                "<form method=\"post\"><textarea name=\"npsso\" autocomplete=\"off\" autocapitalize=\"off\" spellcheck=\"false\" " +
                "placeholder=\"Collez ici la valeur npsso\" required></textarea><button type=\"submit\">Envoyer à la TV</button></form>" +
                "<p class=\"muted\">Le jeton est chiffré sur la TV et n’est jamais journalisé. Client non officiel : usage aux risques du titulaire du compte.</p>" +
                "</main></body></html>"

        private fun donePage(): String =
            "<!doctype html><html lang=\"fr\"><head><meta charset=\"utf-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>GPSx – connecté</title>" +
                "<style>$STYLE</style></head><body><main><h1><b>G</b>PSx · c’est fait</h1>" +
                "<p>Le jeton a été envoyé à la TV. Vous pouvez fermer cette page.</p></main></body></html>"
    }
}
