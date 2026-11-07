package com.gpsx.tv.retro

import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroLibraryTest {
    @Test
    fun `folders map to systems with the longest prefix`() {
        assertEquals(RetroSystem.SNES, RetroSystem.fromFolder("SNES SFC French Best Set"))
        assertEquals(RetroSystem.NEOGEO, RetroSystem.fromFolder("neogeo"))
        assertEquals(RetroSystem.ARCADE, RetroSystem.fromFolder("mame"))
        assertEquals(RetroSystem.GBC, RetroSystem.fromFolder("Game Boy Color"))
        assertEquals(RetroSystem.GB, RetroSystem.fromFolder("gb"))
        assertEquals(RetroSystem.GBA, RetroSystem.fromFolder("GBA"))
        assertEquals(RetroSystem.MEGADRIVE, RetroSystem.fromFolder("Mega Drive"))
        assertEquals(RetroSystem.MASTERSYSTEM, RetroSystem.fromFolder("Master System"))
        assertEquals(RetroSystem.PCENGINE, RetroSystem.fromFolder("pcengine"))
        assertEquals(RetroSystem.PLAYSTATION, RetroSystem.fromFolder("psx"))
        assertEquals(RetroSystem.NES, RetroSystem.fromFolder("nes"))
        assertNull(RetroSystem.fromFolder("Documents"))
        assertNull(RetroSystem.fromFolder("###"))
    }

    @Test
    fun `systems accept their extensions and skip bios sets`() {
        assertTrue(RetroSystem.SNES.accepts("Aladdin (France).zip"))
        assertTrue(RetroSystem.SNES.accepts("game.SFC"))
        assertFalse(RetroSystem.SNES.accepts("SNES SFC French Best Set.nfo"))
        assertFalse(RetroSystem.NEOGEO.accepts("neogeo.zip"))
        assertTrue(RetroSystem.NEOGEO.accepts("mslug.zip"))
        assertFalse(RetroSystem.ARCADE.accepts("eggsplc.chd"))
        assertFalse(RetroSystem.SNES.accepts(".zip"))
    }

    @Test
    fun `the systems the owner chose have a core`() {
        val systems = listOf(RetroSystem.NES, RetroSystem.MEGADRIVE, RetroSystem.MASTERSYSTEM, RetroSystem.GB, RetroSystem.GBC, RetroSystem.GBA, RetroSystem.PCENGINE, RetroSystem.PLAYSTATION)
        assertTrue(systems.all { it.playable })
        assertEquals("genesis_plus_gx", RetroSystem.MASTERSYSTEM.core)
        assertEquals("bios", RetroSystem.PLAYSTATION.biosFolder)
    }

    @Test
    fun `playstation lists the cue and not its tracks`() {
        assertTrue(RetroSystem.PLAYSTATION.accepts("Wipeout (Europe).cue"))
        assertTrue(RetroSystem.PLAYSTATION.accepts("Wipeout (Europe).chd"))
        assertFalse(RetroSystem.PLAYSTATION.accepts("Wipeout (Europe) (Track 1).bin"))
    }

    @Test
    fun `discs of one game become one set in disc order`() {
        val sets =
            DiscSets.group(
                listOf(
                    listOf("Final Fantasy VII (France) (Disc 2).cue"),
                    listOf("Final Fantasy VII (France) (Disc 1).cue"),
                    listOf("Final Fantasy VII (France) (Disc 3).chd"),
                    listOf("Final Fantasy VII (France) (Disc 3).cue"),
                    listOf("Wipeout (Europe).cue"),
                    listOf("RPG", "Other (Disc 1).cue"),
                ),
            )
        assertEquals(3, sets.size)
        assertEquals(3, sets.first { it.size > 1 }.size)
        assertEquals("Final Fantasy VII (France) (Disc 1).cue", sets.first { it.size > 1 }.first().last())
        assertEquals("Final Fantasy VII (France) (Disc 3).chd", sets.first { it.size > 1 }.last().last())
    }

    @Test
    fun `only cd images stay on the share`() {
        assertTrue(RetroGame(RetroSystem.PLAYSTATION, listOf("psx", "Wipeout.cue"), "Wipeout").staysOnShare)
        assertTrue(RetroGame(RetroSystem.PCENGINE, listOf("pcengine", "Game.CHD"), "Game").staysOnShare)
        assertFalse(RetroGame(RetroSystem.PCENGINE, listOf("pcengine", "Game.pce"), "Game").staysOnShare)
        assertFalse(RetroGame(RetroSystem.SNES, listOf("snes", "Game.zip"), "Game").staysOnShare)
    }

    @Test
    fun `cue sheets name their tracks once and never leave the folder`() {
        val cue =
            listOf(
                """FILE "Game (Track 1).bin" BINARY""",
                "  TRACK 01 MODE2/2352",
                "    INDEX 01 00:00:00",
                """FILE "Game (Track 2).bin" BINARY""",
                "file track3.bin binary",
                """FILE "Game (Track 1).bin" BINARY""",
                """FILE "..\..\secret.bin" BINARY""",
                """FILE "../other/track4.bin" BINARY""",
                """FILE ".." BINARY""",
            ).joinToString("\r\n")
        assertEquals(listOf("Game (Track 1).bin", "Game (Track 2).bin", "track3.bin", "secret.bin", "track4.bin"), CueSheet.files(cue))
        assertEquals(emptyList<String>(), CueSheet.files("REM nothing here"))
    }

    @Test
    fun `no-intro names become titles and regions`() {
        assertEquals(RomName("ActRaiser", "FR"), RomNames.parse("ActRaiser (France).zip"))
        assertEquals(RomName("Actraiser 2", "EU"), RomNames.parse("Actraiser 2 (Europe) (En,Fr,De).zip"))
        assertEquals(RomName("The Adventures of Batman & Robin", "EU"), RomNames.parse("Adventures of Batman & Robin, The (Europe).zip"))
        assertEquals(RomName("Asterix & Obelix", "EU"), RomNames.parse("Asterix & Obelix (Europe) (En,Fr,De,Es).zip"))
        assertEquals(RomName("Legend of Zelda", "US"), RomNames.parse("Legend of Zelda (USA, Europe) [!].sfc"))
        assertEquals(RomName("mslug", ""), RomNames.parse("mslug.zip"))
        assertEquals("Combat", RomNames.folderGenre("# Combat #"))
    }

    @Test
    fun `gamelist entries keep relative paths and details`() {
        val entries =
            GameList.parse(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <gameList>
                  <game>
                    <path>./# Sport #/2020bb.zip</path>
                    <name>2020 SUPER BASEBALL</name>
                    <desc>LE BASEBALL REVISITÉ.</desc>
                    <releasedate>19910101T000000</releasedate>
                    <genre>SPORT / BASEBALL</genre>
                    <players>1-2</players>
                    <image>./downloaded_images/2020bb-image.png</image>
                  </game>
                  <game><name>no path</name></game>
                </gameList>
                """.trimIndent().byteInputStream(),
            )
        assertEquals(1, entries.size)
        val game = entries.single()
        assertEquals("# Sport #/2020bb.zip", game.path)
        assertEquals("2020 SUPER BASEBALL", game.name)
        assertEquals(1991, game.year)
        assertEquals("1-2", game.players)
        assertEquals("downloaded_images/2020bb-image.png", game.image)
    }

    @Test
    fun `malformed or hostile gamelist gives no entry`() {
        assertTrue(GameList.parse("<gameList><game>".byteInputStream()).isEmpty())
        val entity =
            """
            <?xml version="1.0"?>
            <!DOCTYPE gameList [<!ENTITY x SYSTEM "file:///etc/passwd">]>
            <gameList><game><path>a.zip</path><name>&x;</name></game></gameList>
            """.trimIndent()
        assertTrue(GameList.parse(entity.byteInputStream()).none { it.name.contains("root") })
    }

    @Test
    fun `share addresses accept unc paths and urls`() {
        assertEquals("smb://192.168.1.230/Public/Retrogaming/", ShareAddress.normalise("\\\\192.168.1.230\\Public\\Retrogaming"))
        assertEquals("smb://nas/Public/", ShareAddress.normalise("smb://nas/Public"))
        assertEquals("smb://nas/Public/Retro/", ShareAddress.normalise(" nas/Public/Retro/ "))
        assertNull(ShareAddress.normalise("\\\\nas"))
        assertNull(ShareAddress.normalise("http://nas/Public"))
        assertNull(ShareAddress.normalise(""))
        assertEquals("\\\\192.168.1.230\\Public\\Retrogaming", ShareAddress.display(ShareAddress.DEFAULT))
    }

    @Test
    fun `scanner builds shelves from an emulationstation share`() {
        val share =
            FakeShare(
                mapOf(
                    "" to listOf(dir("SNES SFC French Best Set"), dir("neogeo"), dir("mame"), dir("Documents")),
                    "SNES SFC French Best Set" to listOf(file("Aladdin (France).zip"), file("ActRaiser (France).zip"), file("SNES SFC French Best Set.nfo")),
                    "neogeo" to listOf(dir("# Combat #"), dir("downloaded_images"), file("gamelist.xml"), file("neogeo.zip")),
                    "neogeo/# Combat #" to listOf(file("3countb.zip"), file("kof98.zip")),
                    "neogeo/downloaded_images" to listOf(file("3countb-image.png")),
                    "mame" to listOf(dir("eggsplc"), file("ace.zip")),
                    "mame/eggsplc" to listOf(file("eggsplc.chd")),
                ),
                files =
                    mapOf(
                        "neogeo/gamelist.xml" to
                            """
                            <gameList><game><path>./# Combat #/3countb.zip</path><name>3 COUNT BOUT</name>
                            <releasedate>19930101T000000</releasedate><image>./downloaded_images/3countb-image.png</image></game></gameList>
                            """.trimIndent(),
                    ),
            )
        val shelves = ShareScanner.scan(share)

        assertEquals(listOf(RetroSystem.SNES, RetroSystem.NEOGEO, RetroSystem.ARCADE), shelves.map { it.system })
        assertEquals(listOf("ActRaiser", "Aladdin"), shelves[0].games.map { it.title })
        assertEquals(listOf("SNES SFC French Best Set", "Aladdin (France).zip"), shelves[0].games[1].path)

        val neogeo = shelves[1].games
        assertEquals(listOf("3 COUNT BOUT", "kof98"), neogeo.map { it.title })
        assertEquals(1993, neogeo[0].year)
        assertEquals(listOf("neogeo", "downloaded_images", "3countb-image.png"), neogeo[0].image)
        assertEquals("Combat", neogeo[1].genre)

        assertEquals(listOf("ace"), shelves[2].games.map { it.title })
    }

    @Test
    fun `box art urls follow libretro thumbnail names`() {
        assertEquals(
            "https://thumbnails.libretro.com/Nintendo%20-%20Super%20Nintendo%20Entertainment%20System/Named_Boxarts/" +
                "Adventures%20of%20Batman%20_%20Robin%2C%20The%20%28Europe%29.png",
            Thumbnails.boxArtUrl(RetroSystem.SNES, "Adventures of Batman & Robin, The (Europe).zip"),
        )
        assertEquals("", Thumbnails.boxArtUrl(RetroSystem.NEOGEO, "mslug.zip"))
        assertEquals("", Thumbnails.boxArtUrl(RetroSystem.SNES, ".zip"))
    }

    @Test
    fun `an unreadable folder gives an empty shelf, not a failed scan`() {
        val share = FakeShare(mapOf("" to listOf(dir("snes"))))
        assertEquals(emptyList<RetroShelf>(), ShareScanner.scan(share))
    }

    private fun dir(name: String) = RomFile(name, directory = true)

    private fun file(name: String) = RomFile(name, directory = false, size = 1)

    private class FakeShare(private val folders: Map<String, List<RomFile>>, private val files: Map<String, String> = emptyMap()) : RomSource {
        override fun list(path: List<String>): List<RomFile> = folders[path.joinToString("/")] ?: throw FileNotFoundException(path.toString())

        override fun open(path: List<String>): InputStream =
            ByteArrayInputStream((files[path.joinToString("/")] ?: throw FileNotFoundException(path.toString())).toByteArray())
    }
}
