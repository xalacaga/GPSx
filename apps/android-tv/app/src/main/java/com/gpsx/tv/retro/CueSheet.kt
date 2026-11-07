// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.retro

/**
 * Track files named by a CD `.cue` sheet. The sheet comes from the owner's share, so it is treated
 * as untrusted: only the file name of each entry is kept, and tracks are read beside the sheet.
 */
object CueSheet {
    private val FILE_LINE = Regex("""^\s*FILE\s+(?:"([^"]+)"|(\S+))\s+\S+\s*$""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

    fun files(text: String): List<String> =
        FILE_LINE.findAll(text)
            .map { line -> line.groupValues[1].ifEmpty { line.groupValues[2] } }
            .map { name -> name.replace('\\', '/').substringAfterLast('/').trim() }
            .filter { name -> name.isNotEmpty() && name != "." && name != ".." }
            .distinct()
            .toList()
}
