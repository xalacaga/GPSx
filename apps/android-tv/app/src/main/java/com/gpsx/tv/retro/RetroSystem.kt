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
    NES("Nintendo NES", "fceumm", setOf("nes", "zip"), listOf("nes", "famicom", "nintendoentertainment"), thumbnailSet = "Nintendo - Nintendo Entertainment System"),
    MEGADRIVE("Mega Drive", "genesis_plus_gx", setOf("md", "gen", "bin", "smd", "zip"), listOf("megadrive", "genesis", "md"), thumbnailSet = "Sega - Mega Drive - Genesis"),
    MASTERSYSTEM("Master System", "genesis_plus_gx", setOf("sms", "zip"), listOf("mastersystem", "sms"), thumbnailSet = "Sega - Master System - Mark III"),
    GBA("Game Boy Advance", "mgba", setOf("gba", "zip"), listOf("gba", "gameboyadvance"), thumbnailSet = "Nintendo - Game Boy Advance"),
    GBC("Game Boy Color", "gambatte", setOf("gbc", "zip"), listOf("gbc", "gameboycolor"), thumbnailSet = "Nintendo - Game Boy Color"),
    GB("Game Boy", "gambatte", setOf("gb", "zip"), listOf("gb", "gameboy"), thumbnailSet = "Nintendo - Game Boy"),
    // CD games need the owner's syscard BIOS in the folder's bios subfolder; HuCards need nothing.
    PCENGINE("PC Engine", "mednafen_pce_fast", setOf("pce", "cue", "chd", "zip"), listOf("pcengine", "pce", "turbografx", "tg16"), thumbnailSet = "NEC - PC Engine - TurboGrafx 16"),
    // A .cue names its tracks, which stay hidden from the list and are copied with it. PCSX ReARMed
    // uses the owner's BIOS from the folder's bios subfolder, else its own replacement.
    PLAYSTATION("PlayStation", "pcsx_rearmed", setOf("cue", "chd", "pbp"), listOf("playstation", "psx", "ps1"), thumbnailSet = "Sony - PlayStation"),
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
     * FACT (GP520, 0.2.27): with the core's defaults Amiga games ran at 31 to 38 frames per second
     * instead of 50, slow and full of audio underruns, so the rest trades detail for speed: low
     * resolution unless a game needs more, every other frame drawn, plain sound mixing without the
     * drive noise. Disks read four times faster, as loading was slower still at that speed.
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
                        "puae_video_resolution=auto-lores",
                        "puae_gfx_framerate=1",
                        "puae_sound_interpol=none",
                        "puae_floppy_sound=0",
                        "puae_floppy_speed=400",
                    ).joinToString("\n", postfix = "\n")
                else -> ""
            }

    /** Folder of the system on the share holding its BIOS files, copied to the core's system folder. */
    val biosFolder: String? get() = if (this == AMIGA || this == PCENGINE || this == PLAYSTATION) "bios" else null

    val playable: Boolean get() = core != null

    /** Systems whose games can come on several discs. */
    val hasDiscs: Boolean get() = this == PCENGINE || this == PLAYSTATION

    /** Systems whose cartridges can play their music from a CD beside them (MSU-MD). */
    val cartridgesWithCd: Boolean get() = this == MEGADRIVE


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
