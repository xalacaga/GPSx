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
        assertEquals(emptyList<RetroGame>(), ShareScanner.scan(share).single().games)
    }

    private fun dir(name: String) = RomFile(name, directory = true)

    private fun file(name: String) = RomFile(name, directory = false, size = 1)

    private class FakeShare(private val folders: Map<String, List<RomFile>>, private val files: Map<String, String> = emptyMap()) : RomSource {
        override fun list(path: List<String>): List<RomFile> = folders[path.joinToString("/")] ?: throw FileNotFoundException(path.toString())

        override fun open(path: List<String>): InputStream =
            ByteArrayInputStream((files[path.joinToString("/")] ?: throw FileNotFoundException(path.toString())).toByteArray())
    }
}
