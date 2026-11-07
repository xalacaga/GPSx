package com.gpsx.tv.retro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroSearchTest {
    private fun game(system: RetroSystem, title: String) = RetroGame(system, listOf(system.name, "$title.zip"), title)

    private val index =
        RetroSearch.index(
            listOf(
                RetroShelf(
                    RetroSystem.MEGADRIVE,
                    "megadrive",
                    listOf(
                        game(RetroSystem.MEGADRIVE, "Sonic The Hedgehog 2"),
                        game(RetroSystem.MEGADRIVE, "Dr. Robotnik's Mean Bean Machine"),
                        game(RetroSystem.MEGADRIVE, "Mega Man: The Wily Wars"),
                        game(RetroSystem.MEGADRIVE, "Street Fighter II' - Special Champion Edition"),
                    ),
                ),
                RetroShelf(
                    RetroSystem.SNES,
                    "snes",
                    listOf(
                        game(RetroSystem.SNES, "Street Fighter II"),
                        game(RetroSystem.SNES, "Super Street Fighter II"),
                        game(RetroSystem.SNES, "Astérix & Obélix"),
                    ),
                ),
                RetroShelf(RetroSystem.MASTERSYSTEM, "mastersystem", listOf(game(RetroSystem.MASTERSYSTEM, "Sonic The Hedgehog 2"))),
            ),
        )

    private fun titles(query: String) = RetroSearch.search(index, query).map { it.title }

    @Test
    fun `a blank query shows nothing`() {
        assertTrue(titles("").isEmpty())
        assertTrue(titles("  - ").isEmpty())
    }

    @Test
    fun `search spans every console`() {
        assertEquals(
            setOf(RetroSystem.MEGADRIVE, RetroSystem.MASTERSYSTEM),
            RetroSearch.search(index, "sonic").map { it.system }.toSet(),
        )
    }

    @Test
    fun `case accents and punctuation are ignored`() {
        assertEquals(listOf("Astérix & Obélix"), titles("ASTERIX obelix"))
        assertEquals(listOf("Dr. Robotnik's Mean Bean Machine"), titles("robotnik's"))
    }

    @Test
    fun `typed words start title words in any order`() {
        assertEquals(listOf("Sonic The Hedgehog 2", "Sonic The Hedgehog 2"), titles("2 son"))
        assertTrue(titles("xonic").isEmpty())
    }

    @Test
    fun `spaces do not matter in a joined name`() {
        assertEquals(listOf("Mega Man: The Wily Wars"), titles("megaman"))
        assertEquals(listOf("Mega Man: The Wily Wars"), titles("mega man"))
    }

    @Test
    fun `titles starting with the query come first`() {
        assertEquals(
            listOf("Street Fighter II", "Street Fighter II' - Special Champion Edition", "Super Street Fighter II"),
            titles("street fighter"),
        )
    }
}
