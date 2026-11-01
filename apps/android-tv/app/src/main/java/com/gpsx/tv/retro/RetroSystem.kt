// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.retro

import java.util.Locale

/**
 * A system the Retro section knows, recognised from an EmulationStation-style folder name
 * (Recalbox/Batocera: one folder per system). [core] is the libretro core GPSx installs for it;
 * null means the system is listed but cannot be played yet. See docs/RETROGAMING_PLAN.md.
 */
enum class RetroSystem(
    val label: String,
    val core: String?,
    val extensions: Set<String>,
    /** Normalised folder-name prefixes (lowercase, letters and digits only). */
    private val folderPrefixes: List<String>,
    /** Arcade sets are loaded as zip files by the core; console games are unzipped first. */
    val keepsZip: Boolean = false,
    /** Sets that are BIOS files, not games, for this system. */
    val biosSets: Set<String> = emptySet(),
    /** libretro thumbnail set whose box art is named after No-Intro file names; null when none fits. */
    val thumbnailSet: String? = null,
) {
    SNES("Super Nintendo", "snes9x2010", setOf("sfc", "smc", "fig", "swc", "zip"), listOf("snes", "sfc", "supernintendo", "superfamicom"), thumbnailSet = "Nintendo - Super Nintendo Entertainment System"),
    NES("Nintendo NES", null, setOf("nes", "zip"), listOf("nes", "famicom", "nintendoentertainment"), thumbnailSet = "Nintendo - Nintendo Entertainment System"),
    MEGADRIVE("Mega Drive", null, setOf("md", "gen", "bin", "smd", "zip"), listOf("megadrive", "genesis", "md"), thumbnailSet = "Sega - Mega Drive - Genesis"),
    GBA("Game Boy Advance", null, setOf("gba", "zip"), listOf("gba", "gameboyadvance"), thumbnailSet = "Nintendo - Game Boy Advance"),
    GBC("Game Boy Color", null, setOf("gbc", "zip"), listOf("gbc", "gameboycolor"), thumbnailSet = "Nintendo - Game Boy Color"),
    GB("Game Boy", null, setOf("gb", "zip"), listOf("gb", "gameboy"), thumbnailSet = "Nintendo - Game Boy"),
    // FB Alpha 2012 Neo Geo reads the older romsets Recalbox shipped; FinalBurn Neo requires
    // sp-s3.sp1 in neogeo.zip, which those sets lack (FACT, owner's share, 2026-11-01).
    NEOGEO("Neo Geo", "fbalpha2012_neogeo", setOf("zip"), listOf("neogeo"), keepsZip = true, biosSets = setOf("neogeo")),
    ARCADE(
        "Arcade",
        "fbneo",
        setOf("zip"),
        listOf("mame", "arcade", "fbneo", "fba", "finalburn"),
        keepsZip = true,
        biosSets = setOf("neogeo", "pgm", "decocass", "isgsm", "skns", "nmk004", "ym2608", "cchip", "qsound"),
    ),
    // PUAE reads disk images by path; its Kickstart files come from the share's amiga/bios folder.
    AMIGA("Amiga", "puae", setOf("adf", "adz", "dms", "ipf", "lha", "hdf", "m3u", "zip"), listOf("amiga", "commodoreamiga")),
    ;

    /**
     * Core options GPSx sets for this system, as "key=value" lines; other options take the core's
     * declared defaults. SNES: a 4:3 picture (the core's "auto" widens PAL games). Amiga: each disk
     * of a game goes in its own drive (DF0–DF3), so most multi-disk games run without swapping disks,
     * and the pad covers the keys and mouse most games need (owner request: play with the pad as
     * much as possible): Square Return, Triangle Space, L1/R1 F1/F2, L2/R2 mouse buttons (core
     * default), right stick mouse (the left one stays the joystick), Create virtual keyboard.
     */
    val coreOptions: String
        get() =
            when (this) {
                SNES -> "snes9x_2010_aspect=4:3\n"
                AMIGA ->
                    listOf(
                        "puae_floppy_multidrive=enabled",
                        "puae_analogmouse=right",
                        "puae_mapper_y=RETROK_RETURN",
                        "puae_mapper_x=RETROK_SPACE",
                        "puae_mapper_l=RETROK_F1",
                        "puae_mapper_r=RETROK_F2",
                    ).joinToString("\n", postfix = "\n")
                else -> ""
            }

    /** Folder of the system on the share holding its BIOS files, copied to the core's system folder. */
    val biosFolder: String? get() = if (this == AMIGA) "bios" else null

    val playable: Boolean get() = core != null


    /** True when [fileName] has one of this system's extensions and is not a BIOS set. */
    fun accepts(fileName: String): Boolean {
        val dot = fileName.lastIndexOf('.')
        if (dot <= 0) return false
        val extension = fileName.substring(dot + 1).lowercase(Locale.ROOT)
        if (extension !in extensions) return false
        return fileName.substring(0, dot).lowercase(Locale.ROOT) !in biosSets
    }

    companion object {
        /**
         * The system a share folder holds, or null when the name means nothing to GPSx. Longer
         * prefixes win, so "Game Boy Color" is GBC and not GB, and "SNES SFC French Best Set" is SNES.
         */
        fun fromFolder(name: String): RetroSystem? {
            val normalised = name.lowercase(Locale.ROOT).filter { it in 'a'..'z' || it in '0'..'9' }
            if (normalised.isEmpty()) return null
            return entries
                .flatMap { system -> system.folderPrefixes.map { prefix -> prefix to system } }
                .filter { (prefix, _) -> normalised.startsWith(prefix) }
                .maxByOrNull { (prefix, _) -> prefix.length }
                ?.second
        }
    }
}
