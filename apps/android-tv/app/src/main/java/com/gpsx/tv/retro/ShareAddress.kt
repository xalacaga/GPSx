// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.retro

/**
 * Where the owner's games live, as an SMB URL with a trailing slash. Accepts what people copy from
 * Windows Explorer (`\\192.168.1.230\Public\Retrogaming`), a bare `host/share/path`, or an
 * `smb://` URL; returns null when no host and share can be read.
 */
object ShareAddress {
    const val DEFAULT = "smb://192.168.1.230/Public/Retrogaming/"

    fun normalise(raw: String): String? {
        val trimmed = raw.trim().replace('\\', '/')
        if (trimmed.isEmpty()) return null
        val withoutScheme =
            when {
                trimmed.startsWith("smb://", ignoreCase = true) -> trimmed.substring(6)
                trimmed.contains("://") -> return null
                else -> trimmed
            }
        val parts = withoutScheme.split('/').filter(String::isNotBlank)
        if (parts.size < 2) return null
        val host = parts.first()
        if (host.any { it.isWhitespace() || it == '@' || it == '?' || it == '#' }) return null
        return "smb://" + parts.joinToString("/") + "/"
    }

    /** `smb://host/share/path/` → `\\host\share\path`, the form shown to the owner. */
    fun display(url: String): String = "\\\\" + url.removePrefix("smb://").trimEnd('/').replace('/', '\\')
}
