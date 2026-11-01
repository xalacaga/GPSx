// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.retro

import android.content.Context
import android.os.StatFs
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/** Space the Retro section uses on the TV, in bytes, and what is left on its storage. */
data class RetroStorageUse(
    val games: Long,
    val gameCount: Int,
    val covers: Long,
    val cores: Long,
    val saves: Long,
    val free: Long,
)

/**
 * Where the Retro section keeps files on the TV. Each game is copied once from the share and kept,
 * so it starts again without the NAS copy (owner decision 2026-11-01, with an easy erase and the
 * space shown in Settings). Covers and emulator cores are caches that come back when needed; saves
 * are the owner's progress. Each kind is erased separately, on request only.
 */
class RetroStorage(context: Context) {
    private val appContext = context.applicationContext
    val gamesDir = File(appContext.filesDir, "retro/games")
    val coversDir = File(appContext.cacheDir, "retro-covers")
    val coresDir = File(appContext.filesDir, "retro/cores")
    val savesDir = File(appContext.filesDir, "retro/saves")

    /** Folder holding the kept copy of [game] (its ROM, or arcade set and BIOS sets). */
    fun gameDir(game: RetroGame): File = File(gamesDir, game.system.name.lowercase(Locale.ROOT) + "/" + sha1(game.path.joinToString("/")))

    fun use(): RetroStorageUse =
        RetroStorageUse(
            games = size(gamesDir),
            gameCount = gamesDir.listFiles()?.sumOf { system -> system.listFiles()?.count { File(it, READY_MARKER).isFile } ?: 0 } ?: 0,
            covers = size(coversDir),
            cores = size(coresDir),
            saves = size(savesDir),
            free = runCatching { StatFs(appContext.filesDir.absolutePath).availableBytes }.getOrDefault(0L),
        )

    fun clearGames() {
        gamesDir.deleteRecursively()
    }

    /** Covers and emulator cores; both are downloaded again when a game needs them. */
    fun clearCaches() {
        coversDir.deleteRecursively()
        coresDir.deleteRecursively()
    }

    fun clearSaves() {
        savesDir.deleteRecursively()
    }

    /** Removes the per-game copies of builds before 0.2.22, which used the cache folder. */
    fun removeLegacyCopies() {
        File(appContext.cacheDir, "retro/games").deleteRecursively()
    }

    private fun size(dir: File): Long = dir.walkBottomUp().filter(File::isFile).sumOf(File::length)

    companion object {
        /** Marker naming the game file inside a complete copy; a folder without it is redone. */
        const val READY_MARKER = ".ready"

        private fun sha1(value: String): String =
            MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        /** "1,4 Mo", "820 Ko", "0 o". */
        fun format(bytes: Long): String =
            when {
                bytes >= 1024L * 1024 * 1024 -> String.format(Locale.FRANCE, "%.1f Go", bytes / (1024.0 * 1024 * 1024))
                bytes >= 1024L * 1024 -> String.format(Locale.FRANCE, "%.1f Mo", bytes / (1024.0 * 1024))
                bytes >= 1024L -> "${bytes / 1024} Ko"
                else -> "$bytes o"
            }
    }
}
