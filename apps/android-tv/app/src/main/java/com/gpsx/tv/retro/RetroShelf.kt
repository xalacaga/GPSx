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
)

/** The games of one system folder on the share. */
data class RetroShelf(val system: RetroSystem, val folder: String, val games: List<RetroGame>)

/**
 * Builds the shelves of an EmulationStation-style share: each top folder whose name maps to a
 * [RetroSystem] becomes a shelf, with games read from its files and from one level of subfolders
 * (Recalbox genre folders such as "# Combat #"). Media folders and BIOS sets are skipped; folders
 * holding only CHD disks (MAME) give no game. `gamelist.xml` supplies names and details when present.
 */
object ShareScanner {
    private val MEDIA_FOLDERS =
        setOf("images", "videos", "downloaded_images", "downloaded_videos", "media", "bezels", "manuals", "marquees", "snap", "boxart", "wheel")

    fun scan(source: RomSource): List<RetroShelf> =
        source.list(emptyList())
            .filter { it.directory }
            .mapNotNull { folder ->
                val system = RetroSystem.fromFolder(folder.name) ?: return@mapNotNull null
                RetroShelf(system, folder.name, scanFolder(source, system, folder.name))
            }
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
        return files
            .map { (relative, folderGenre) ->
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
                )
            }
            .sortedBy { it.title.lowercase(Locale.ROOT) }
    }
}
