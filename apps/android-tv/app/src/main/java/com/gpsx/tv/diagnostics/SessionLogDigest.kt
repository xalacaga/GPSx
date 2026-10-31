// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.diagnostics

/** One recurring native log message, numbers masked, with how often it appeared. */
data class LogDigestEntry(val count: Int, val message: String)

internal const val MAX_LOG_DIGEST_ENTRIES = 8
internal const val MAX_LOG_DIGEST_LINES = 50_000
private const val MAX_LOG_MESSAGE_LENGTH = 90

/** Informational lines worth keeping: stream format, decoder setup and profile switches. */
private val KEPT_INFO = listOf("Switched to profile", "Initializing decoder", "AMediaCodec", "Stream is H.26", "Audio Output started")

/** Lines that might carry secrets are dropped whole rather than redacted. */
private val SECRET_HINTS = listOf("key", "token", "npsso", "secret", "auth", "spec", "handshake", "session id", "session_id", "http", "cookie", "regist")

/**
 * Summarises a native session log for test reports: warnings, errors and a few setup lines,
 * grouped by message with digits masked (which also hides addresses). Only the first
 * [MAX_LOG_DIGEST_LINES] lines are read; the most frequent [MAX_LOG_DIGEST_ENTRIES] messages
 * are kept in first-seen order.
 */
fun digestSessionLog(lines: Sequence<String>): List<LogDigestEntry> {
    val counts = LinkedHashMap<String, Int>()
    lines.take(MAX_LOG_DIGEST_LINES).forEach { line ->
        val warning = line.startsWith("[W] ") || line.startsWith("[E] ")
        val info = line.startsWith("[I] ") && KEPT_INFO.any(line::contains)
        if (!warning && !info) return@forEach
        if (SECRET_HINTS.any { line.contains(it, ignoreCase = true) }) return@forEach
        val message =
            line.replace(Regex("[0-9A-Fa-f]{16,}"), "…")
                .replace(Regex("\\d+"), "#")
                .trim()
                .take(MAX_LOG_MESSAGE_LENGTH)
        counts[message] = (counts[message] ?: 0) + 1
    }
    val kept = counts.entries.sortedByDescending { it.value }.take(MAX_LOG_DIGEST_ENTRIES).map { it.key }.toSet()
    return counts.entries.filter { it.key in kept }.map { LogDigestEntry(it.value, it.key) }
}
