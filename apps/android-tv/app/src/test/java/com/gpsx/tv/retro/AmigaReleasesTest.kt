package com.gpsx.tv.retro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmigaReleasesTest {
    private fun titles(vararg files: String) = AmigaReleases.group(files.map { listOf(it) }).map { it.title }

    @Test
    fun `only what an Amiga 500 can run is listed`() {
        assertEquals(
            listOf("Alien Breed", "Lotus"),
            titles(
                "Alien Breed (1991)(Team 17)[cr SKR][bootable].adf",
                "Breathless (1995)(Power Computing)(AGA)[cr](Disk 1 of 3).adf",
                "Lotus (1990)(Gremlin)(AGA)[cr].adf",
                "Lotus (1990)(Gremlin)[cr].adf",
                "Big (1993)(Team)(4MB Boot).adf",
                "Oscar CD32 (1993)(Flair).adf",
                "Zork Zero (1983)(Infocom)[WB].adf",
                "Runner, The (19xx)(-)[cr CAWAQ][b].adf",
                "Rock Challenge (19xx)(Readysoft)[b2].adf",
            ),
        )
    }

    @Test
    fun `side labels after the disk tag stay in one release`() {
        val release = AmigaReleases.group(listOf(listOf("Game (1990)(Pub)(Disk 2 of 2)(Data).adf"), listOf("Game (1990)(Pub)(Disk 1 of 2)(Boot).adf"))).single()

        assertEquals(listOf("Game (1990)(Pub)(Disk 1 of 2)(Boot).adf", "Game (1990)(Pub)(Disk 2 of 2)(Data).adf"), release.disks.map { it.last() })
    }

    private fun files(vararg names: String) = names.map { listOf(it) }

    @Test
    fun `disks of one release become one game in disk order`() {
        val releases =
            AmigaReleases.group(
                files(
                    "Diggers (1993)(Millennium)[cr Spreadpoint](Disk 3 of 3).adf",
                    "Diggers (1993)(Millennium)[cr Spreadpoint](Disk 1 of 3).adf",
                    "Diggers (1993)(Millennium)[cr Spreadpoint](Disk 2 of 3).adf",
                ),
            )
        val game = releases.single()
        assertEquals("Diggers", game.title)
        assertEquals(1993, game.year)
        assertEquals("Millennium", game.publisher)
        assertEquals(listOf("1 of 3", "2 of 3", "3 of 3"), game.disks.map { Regex("Disk (\\d of \\d)").find(it.last())!!.groupValues[1] })
    }

    @Test
    fun `one release per game, complete and unaltered first`() {
        val releases =
            AmigaReleases.group(
                files(
                    "Kick Off 2 (1990)(Anco)[cr OCL].adf",
                    "Kick Off 2 (1990)(Anco)[cr OCL][tr Tu].adf",
                    "Kick Off 2 (1990)(Anco)[b].adf",
                    "Lemmings (1990)(Psygnosis)[cr Impact](Disk 2 of 2).adf",
                    "Lemmings (1990)(Psygnosis)[a](Disk 1 of 2).adf",
                    "Lemmings (1990)(Psygnosis)[a](Disk 2 of 2).adf",
                ),
            )
        assertEquals(listOf("Kick Off 2", "Lemmings"), releases.map { it.title })
        assertEquals("Kick Off 2 (1990)(Anco)[cr OCL].adf", releases[0].disks.single().last())
        // The complete two-disk release wins over the lone second disk of another crack.
        assertEquals(2, releases[1].disks.size)
    }

    @Test
    fun `trailing articles move to the front and missing years stay unknown`() {
        val game = AmigaReleases.group(files("Humans, The (1994)(Gametek)(M5)[cr GOD](Disk 1 of 4)[a][HD].adf")).single()
        assertEquals("The Humans", game.title)
        assertNull(AmigaReleases.group(files("Demo Disk.adf")).single().year)
    }

    @Test
    fun `amiga folders and options are recognised`() {
        assertEquals(RetroSystem.AMIGA, RetroSystem.fromFolder("amiga"))
        val amiga = RetroSystem.AMIGA.coreOptions.lines()
        assertEquals(true, "puae_floppy_multidrive=enabled" in amiga)
        assertEquals(true, "puae_analogmouse=right" in amiga)
        assertEquals(true, "puae_mapper_y=RETROK_RETURN" in amiga)
        assertEquals("bios", RetroSystem.AMIGA.biosFolder)
        assertEquals("snes9x_2010_aspect=4:3\n", RetroSystem.SNES.coreOptions)
        assertEquals("", RetroSystem.NEOGEO.coreOptions)
    }
}
