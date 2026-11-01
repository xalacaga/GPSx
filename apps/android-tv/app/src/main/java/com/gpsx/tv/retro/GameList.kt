// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.retro

import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** One game described by an EmulationStation `gamelist.xml`; paths are relative to its folder. */
data class GameListEntry(
    val path: String,
    val name: String,
    val description: String = "",
    val year: Int? = null,
    val genre: String = "",
    val players: String = "",
    val developer: String = "",
    val publisher: String = "",
    val image: String = "",
)

/**
 * Reader for EmulationStation `gamelist.xml` files (Recalbox, Batocera, RetroPie). The file comes
 * from the owner's share, so it is treated as untrusted: no DTD, no external entities, and a
 * malformed file gives an empty list rather than an error.
 */
object GameList {
    fun parse(input: InputStream): List<GameListEntry> {
        val factory = DocumentBuilderFactory.newInstance()
        // Not every Android parser knows these features; it resolves no external entity anyway.
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { factory.isExpandEntityReferences = false }
        val document = runCatching { factory.newDocumentBuilder().parse(input) }.getOrNull() ?: return emptyList()
        val games = document.getElementsByTagName("game")
        return (0 until games.length).mapNotNull { index ->
            val game = games.item(index) as? Element ?: return@mapNotNull null
            val path = normalisePath(game.child("path"))
            if (path.isEmpty()) return@mapNotNull null
            GameListEntry(
                path = path,
                name = game.child("name"),
                description = game.child("desc"),
                year = game.child("releasedate").take(4).toIntOrNull()?.takeIf { it in 1950..2100 },
                genre = game.child("genre"),
                players = game.child("players"),
                developer = game.child("developer"),
                publisher = game.child("publisher"),
                image = normalisePath(game.child("image")),
            )
        }
    }

    /** "./# Sport #/2020bb.zip" → "# Sport #/2020bb.zip"; backslashes become slashes. */
    fun normalisePath(raw: String): String = raw.trim().replace('\\', '/').removePrefix("./").trimStart('/')

    private fun Element.child(tag: String): String {
        val nodes = getElementsByTagName(tag)
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node.parentNode === this) return node.textContent.orEmpty().trim()
        }
        return ""
    }
}
