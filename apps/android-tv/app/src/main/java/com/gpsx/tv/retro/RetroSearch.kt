// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.retro

import java.text.Normalizer
import java.util.Locale

/**
 * Title search across every console of the share. Accents, case and punctuation are ignored, and
 * each typed word only has to start a word of the title, in any order, so results narrow as the
 * owner types on a TV keyboard. Spaces may also be left out or added ("megaman" finds "Mega Man",
 * "mega man" finds "Megaman").
 */
object RetroSearch {
    class Entry internal constructor(val game: RetroGame, internal val title: String) {
        internal val words = title.split(' ')
        internal val joined = title.replace(" ", "")
    }

    /** Prepares titles once, so that each key press only compares text. */
    fun index(shelves: List<RetroShelf>): List<Entry> =
        shelves.flatMap { shelf -> shelf.games.map { Entry(it, normalise(it.title)) } }

    /** Games matching [query]: titles starting with it first, then by title and console; none when it is blank. */
    fun search(index: List<Entry>, query: String): List<RetroGame> {
        val needle = normalise(query)
        if (needle.isEmpty()) return emptyList()
        val typed = needle.split(' ')
        val joined = needle.replace(" ", "")
        return index
            .filter { entry -> typed.all { word -> entry.words.any { it.startsWith(word) } } || joined in entry.joined }
            .sortedWith(
                compareBy<Entry> { !it.joined.startsWith(joined) }
                    .thenBy { it.title }
                    .thenBy { it.game.system.ordinal },
            )
            .map { it.game }
    }

    internal fun normalise(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(MARKS, "")
            .lowercase(Locale.ROOT)
            .replace(SEPARATORS, " ")
            .trim()

    private val MARKS = Regex("\\p{M}+")
    private val SEPARATORS = Regex("[^\\p{L}\\p{N}]+")
}
