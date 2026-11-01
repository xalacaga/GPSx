// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.retro

import java.util.Locale

/**
 * Amiga disk images named the TOSEC way: "Kick Off 2 (1990)(Anco)[cr OCL](Disk 1 of 2).adf".
 * A collection holds many releases of one game (cracks, hacks, alternates, bad dumps) and splits
 * multi-disk games into one file per disk. [group] turns that into one entry per game: the disks
 * of a release are gathered in order, and the most complete, least altered release is kept.
 */
object AmigaReleases {
    /** One game: its disks in order (folder-relative paths), title, year and publisher. */
    data class Release(val disks: List<List<String>>, val title: String, val year: Int?, val publisher: String)

    private val DISK_TAG = Regex("""\s*\((?:Disk|Disque)\s*(\d+)\s*of\s*(\d+)\)""", RegexOption.IGNORE_CASE)
    private val YEAR = Regex("""\((\d{4})[^)]*\)""")
    private val BRACKETS = Regex("""\[([^\]]*)\]""")

    fun group(files: List<List<String>>): List<Release> {
        // Disks of one release share their name once the disk tag is removed.
        val releases =
            files.groupBy { path -> DISK_TAG.replace(baseName(path.last()), "").trim().lowercase(Locale.ROOT) }
                .map { (_, disks) ->
                    val ordered = disks.sortedBy { diskNumber(it.last()) }
                    val name = DISK_TAG.replace(baseName(ordered.first().last()), "").trim()
                    Candidate(ordered, name, expectedDisks(ordered.first().last()))
                }
        // Releases of one game share the title before the first tag.
        return releases.groupBy { titleKey(it.name) }
            .map { (_, candidates) ->
                val best = candidates.minWith(compareBy<Candidate>({ it.penalty() }, { it.name.length }, { it.name }))
                Release(best.disks, RomNames.parse(stripTags(best.name) + ".adf").title, year(best.name), publisher(best.name))
            }
            .sortedBy { it.title.lowercase(Locale.ROOT) }
    }

    private data class Candidate(val disks: List<List<String>>, val name: String, val expected: Int) {
        /** Lower is better: incomplete disk sets, bad dumps, then each extra alteration tag. */
        fun penalty(): Int {
            val tags = BRACKETS.findAll(name).map { it.groupValues[1].lowercase(Locale.ROOT) }.toList()
            var score = 0
            if (disks.size < expected) score += 1000
            if (tags.any { it.startsWith("b") && !it.startsWith("bt") }) score += 500
            if (tags.any { it == "!" }) score -= 50
            score += tags.count { it != "!" } * 10
            return score
        }
    }

    private fun baseName(file: String): String = file.substringBeforeLast('.', file)

    private fun diskNumber(file: String): Int = DISK_TAG.find(file)?.groupValues?.get(1)?.toIntOrNull() ?: 1

    private fun expectedDisks(file: String): Int = DISK_TAG.find(file)?.groupValues?.get(2)?.toIntOrNull() ?: 1

    /** "Kick Off 2 (1990)(Anco)[cr OCL]" → "kick off 2". */
    private fun titleKey(name: String): String = stripTags(name).lowercase(Locale.ROOT).trim()

    private fun stripTags(name: String): String = name.substringBefore(" (").substringBefore(" [").trim()

    private fun year(name: String): Int? = YEAR.find(name)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1980..2030 }

    /** The tag after the year: "(1990)(Anco)" → "Anco". */
    private fun publisher(name: String): String {
        val tags = Regex("""\(([^)]*)\)""").findAll(name).map { it.groupValues[1] }.toList()
        val yearIndex = tags.indexOfFirst { it.take(4).toIntOrNull() != null }
        return tags.getOrNull(yearIndex + 1)?.takeUnless { it.startsWith("Disk", ignoreCase = true) }.orEmpty()
    }
}
