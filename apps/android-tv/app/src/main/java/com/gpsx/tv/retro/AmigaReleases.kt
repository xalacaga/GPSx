// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.retro

import java.util.Locale

/**
 * Amiga disk images named the TOSEC way: "Kick Off 2 (1990)(Anco)[cr OCL](Disk 1 of 2).adf".
 * A collection holds many releases of one game (cracks, hacks, alternates, bad dumps) and splits
 * multi-disk games into one file per disk. [group] turns that into one entry per game: the disks
 * of a release are gathered in order, and the most complete, least altered release is kept.
 *
 * Only what an Amiga 500 can run is listed. FACT (GP520, 0.2.27): even A500 games ran below full
 * speed, so AGA, CD32 and big-memory releases cannot be played on this TV. A game with an A500
 * release keeps it; a title known only from bad dumps is left out, as it cannot run properly.
 */
object AmigaReleases {
    /** One game: its disks in order (folder-relative paths), title, year and publisher. */
    data class Release(val disks: List<List<String>>, val title: String, val year: Int?, val publisher: String)

    /** "(Disk 1 of 2)", with the side label some sets add after it: "(Boot)", "(A)". */
    private val DISK_TAG = Regex("""\s*\((?:Disk|Disque)\s*(\d+)\s*of\s*(\d+)\)(?:\s*\([A-Za-z0-9 ]{1,12}\))?""", RegexOption.IGNORE_CASE)
    private val BEYOND_A500 = Regex("""\bCD32\b|\bCDTV\b|\((?:AGA|A1200[^)]*|A4000[^)]*|\d+MB [^)]*|HD Installer)\)|\[WB\]""", RegexOption.IGNORE_CASE)
    private val BAD_DUMP = Regex("""b\d*(?: .*)?""")
    private val YEAR = Regex("""\((\d{4})[^)]*\)""")
    private val BRACKETS = Regex("""\[([^\]]*)\]""")

    fun group(files: List<List<String>>): List<Release> {
        // Disks of one release share their name once the disk tag is removed.
        val releases =
            files.filterNot { BEYOND_A500.containsMatchIn(it.last()) }
                .groupBy { path -> DISK_TAG.replace(baseName(path.last()), "").trim().lowercase(Locale.ROOT) }
                .map { (_, disks) ->
                    val ordered = disks.sortedBy { diskNumber(it.last()) }
                    val name = DISK_TAG.replace(baseName(ordered.first().last()), "").trim()
                    Candidate(ordered, name, expectedDisks(ordered.first().last()))
                }
        // Releases of one game share the title before the first tag.
        return releases.filterNot(Candidate::badDump)
            .groupBy { titleKey(it.name) }
            .map { (_, candidates) ->
                val best = candidates.minWith(compareBy<Candidate>({ it.penalty() }, { it.name.length }, { it.name }))
                Release(best.disks, RomNames.parse(stripTags(best.name) + ".adf").title, year(best.name), publisher(best.name))
            }
            .sortedBy { it.title.lowercase(Locale.ROOT) }
    }

    private data class Candidate(val disks: List<List<String>>, val name: String, val expected: Int) {
        private val tags: List<String> get() = BRACKETS.findAll(name).map { it.groupValues[1].lowercase(Locale.ROOT) }.toList()

        /** "[b]", "[b2]", "[b corrupt]"; not "[bootable]" or "[bt ...]". */
        val badDump: Boolean get() = tags.any(BAD_DUMP::matches)

        /** Lower is better: incomplete disk sets, then each extra alteration tag. */
        fun penalty(): Int {
            var score = 0
            if (disks.size < expected) score += 1000
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
