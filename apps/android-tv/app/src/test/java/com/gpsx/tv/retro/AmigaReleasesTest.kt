package com.gpsx.tv.retro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmigaReleasesTest {
    private fun files(vararg names: String) = names.map { listOf(it) }

    @Test
    fun `disks of one release become one game in disk order`() {
        val releases =
            AmigaReleases.group(
                files(
                    "Diggers CD32 (1993)(Millennium)(AGA)[cr Spreadpoint](Disk 3 of 3).adf",
                    "Diggers CD32 (1993)(Millennium)(AGA)[cr Spreadpoint](Disk 1 of 3).adf",
                    "Diggers CD32 (1993)(Millennium)(AGA)[cr Spreadpoint](Disk 2 of 3).adf",
                ),
            )
        val game = releases.single()
        assertEquals("Diggers CD32", game.title)
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
                    "Lemmings CD32 (1990)(Psygnosis)[cr Impact](Disk 2 of 2).adf",
                    "Lemmings CD32 (1990)(Psygnosis)[a](Disk 1 of 2).adf",
                    "Lemmings CD32 (1990)(Psygnosis)[a](Disk 2 of 2).adf",
                ),
            )
        assertEquals(listOf("Kick Off 2", "Lemmings CD32"), releases.map { it.title })
        assertEquals("Kick Off 2 (1990)(Anco)[cr OCL].adf", releases[0].disks.single().last())
        // The complete two-disk release wins over the lone second disk of another crack.
        assertEquals(2, releases[1].disks.size)
    }

    @Test
    fun `trailing articles move to the front and missing years stay unknown`() {
        val game = AmigaReleases.group(files("Humans CD32, The (1994)(Gametek)(AGA)(M5)[cr GOD](Disk 1 of 4)[a][HD].adf")).single()
        assertEquals("The Humans CD32", game.title)
        assertNull(AmigaReleases.group(files("Demo Disk.adf")).single().year)
    }

    @Test
    fun `amiga folders and options are recognised`() {
        assertEquals(RetroSystem.AMIGA, RetroSystem.fromFolder("amiga"))
        assertEquals("puae_floppy_multidrive=enabled\n", RetroSystem.AMIGA.coreOptions)
        assertEquals("bios", RetroSystem.AMIGA.biosFolder)
        assertEquals("snes9x_2010_aspect=4:3\n", RetroSystem.SNES.coreOptions)
        assertEquals("", RetroSystem.NEOGEO.coreOptions)
    }
}
