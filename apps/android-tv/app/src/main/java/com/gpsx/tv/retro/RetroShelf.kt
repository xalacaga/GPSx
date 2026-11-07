// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.retro

import java.io.InputStream
import java.util.Locale

/** One entry of a share folder; [name] carries no trailing slash. */
data class RomFile(val name: String, val directory: Boolean, val size: Long = 0)

/**
 * Read-only access to the owner's games. Paths are folder and file names from the share root, so
 * names holding `#`, spaces or accents never go through URL parsing in GPSx code.
 */
interface RomSource {
    fun list(path: List<String>): List<RomFile>

    fun open(path: List<String>): InputStream

    /** The file for random reads, for games that stay on the share. */
    fun openRandom(path: List<String>): RandomRead = throw UnsupportedOperationException()
}

data class RetroGame(
    val system: RetroSystem,
    /** Folder and file names from the share root, ending with the game file. */
    val path: List<String>,
    val title: String,
    val region: String = "",
    val year: Int? = null,
    val genre: String = "",
    val players: String = "",
    val description: String = "",
    /** Share path of the cover image from `gamelist.xml`; empty when there is none. */
    val image: List<String> = emptyList(),
    /** Box art on the libretro thumbnail server, used when [image] is empty; empty when none applies. */
    val coverUrl: String = "",
    /** All disks of a multi-disk game, in order (the first is [path]); empty for a single file. */
    val disks: List<List<String>> = emptyList(),
) {
    /** CD images are heavy: they are read from the share and never copied to the TV. */
    val staysOnShare: Boolean get() = path.last().substringAfterLast('.', "").lowercase(Locale.ROOT) in DISC_IMAGES

    private companion object {
        val DISC_IMAGES = setOf("cue", "chd", "pbp")
    }
}

/**
 * Discs of one game: files named alike but for a "(Disc 2)" tag, in disc order, the first disc
 * first. A disc number seen twice (a .cue beside a .chd) keeps the first file.
 */
object DiscSets {
    private val DISC_TAG = Regex("""\s*\((?:Disc|Disk|CD)\s*(\d+)(?:\s*of\s*\d+)?\)""", RegexOption.IGNORE_CASE)

    fun group(files: List<List<String>>): List<List<List<String>>> =
        files.groupBy { path -> (path.dropLast(1) + DISC_TAG.replace(path.last().substringBeforeLast('.'), "")).joinToString("/").lowercase(Locale.ROOT) }
            .values
            .map { discs -> discs.distinctBy(::discNumber).sortedBy(::discNumber) }

    private fun discNumber(path: List<String>): Int = DISC_TAG.find(path.last())?.groupValues?.get(1)?.toIntOrNull() ?: 1
}

/** The games of one system folder on the share. */
data class RetroShelf(val system: RetroSystem, val folder: String, val games: List<RetroGame>)

/**
 * Builds the shelves of an EmulationStation-style share: each top folder whose name maps to a
 * [RetroSystem] becomes a shelf, with games read from its files and from one level of subfolders
 * (Recalbox genre folders such as "# Combat #"). Media folders and BIOS sets are skipped; folders
 * holding only CHD disks (MAME) give no game, and a folder without games gives no shelf, so folders
 * prepared ahead of the owner's games stay hidden. `gamelist.xml` supplies names and details when present.
 */
object ShareScanner {
    private val MEDIA_FOLDERS =
        setOf("images", "videos", "downloaded_images", "downloaded_videos", "media", "bezels", "manuals", "marquees", "snap", "boxart", "wheel", "bios")

    fun scan(source: RomSource): List<RetroShelf> =
        source.list(emptyList())
            .filter { it.directory }
            .mapNotNull { folder ->
                val system = RetroSystem.fromFolder(folder.name) ?: return@mapNotNull null
                RetroShelf(system, folder.name, scanFolder(source, system, folder.name))
            }
            .filter { it.games.isNotEmpty() }
            .sortedWith(compareBy<RetroShelf> { it.system.ordinal }.thenBy { it.folder.lowercase(Locale.ROOT) })

    private fun scanFolder(source: RomSource, system: RetroSystem, folder: String): List<RetroGame> {
        val root = listOf(folder)
        val entries = runCatching { source.list(root) }.getOrDefault(emptyList())
        val described =
            entries.firstOrNull { !it.directory && it.name.equals("gamelist.xml", ignoreCase = true) }
                ?.let { file -> runCatching { source.open(root + file.name).use(GameList::parse) }.getOrNull() }
                .orEmpty()
                .associateBy { it.path.lowercase(Locale.ROOT) }
        val files = mutableListOf<Pair<List<String>, String>>() // relative path, genre folder
        entries.forEach { entry ->
            if (!entry.directory) {
                if (system.accepts(entry.name)) files += listOf(entry.name) to ""
            } else if (entry.name.lowercase(Locale.ROOT) !in MEDIA_FOLDERS) {
                runCatching { source.list(root + entry.name) }.getOrDefault(emptyList())
                    .filter { !it.directory && system.accepts(it.name) }
                    .forEach { files += listOf(entry.name, it.name) to RomNames.folderGenre(entry.name) }
            }
        }
        if (system == RetroSystem.AMIGA) {
            return AmigaReleases.group(files.map { it.first }).map { release ->
                // A scraper lists either the first disk or every disk; any of them identifies the game.
                val info = release.disks.firstNotNullOfOrNull { described[it.joinToString("/").lowercase(Locale.ROOT)] }
                RetroGame(
                    system = system,
                    path = root + release.disks.first(),
                    title = info?.name?.takeIf(String::isNotBlank) ?: release.title,
                    year = info?.year ?: release.year,
                    genre = info?.genre?.takeIf(String::isNotBlank) ?: release.publisher,
                    players = info?.players.orEmpty(),
                    description = info?.description.orEmpty(),
                    image = info?.image?.takeIf(String::isNotBlank)?.let { root + it.split('/') }.orEmpty(),
                    disks = if (release.disks.size > 1) release.disks.map { root + it } else emptyList(),
                )
            }
        }
        val sets = if (system.hasDiscs) DiscSets.group(files.map { it.first }) else files.map { listOf(it.first) }
        val genres = files.toMap()
        return sets
            .map { discs ->
                val relative = discs.first()
                val folderGenre = genres[relative].orEmpty()
                val info = described[relative.joinToString("/").lowercase(Locale.ROOT)]
                val parsed = RomNames.parse(relative.last())
                RetroGame(
                    system = system,
                    path = root + relative,
                    title = info?.name?.takeIf(String::isNotBlank) ?: parsed.title,
                    region = parsed.region,
                    year = info?.year,
                    genre = info?.genre?.takeIf(String::isNotBlank) ?: folderGenre,
                    players = info?.players.orEmpty(),
                    description = info?.description.orEmpty(),
                    image = info?.image?.takeIf(String::isNotBlank)?.let { root + it.split('/') }.orEmpty(),
                    coverUrl = Thumbnails.boxArtUrl(system, relative.last()),
                    disks = if (discs.size > 1) discs.map { root + it } else emptyList(),
                )
            }
            .sortedBy { it.title.lowercase(Locale.ROOT) }
    }
}
