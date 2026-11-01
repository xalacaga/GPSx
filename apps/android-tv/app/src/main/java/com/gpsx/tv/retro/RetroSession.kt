// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.retro

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

enum class RetroPhase { IDLE, PREPARING, PLAYING, FAILED }

data class RetroSessionSnapshot(
    val phase: RetroPhase = RetroPhase.IDLE,
    val title: String = "",
    val detail: String = "",
    /** Display aspect ratio of the running game. */
    val aspect: Float = 4f / 3f,
    val players: Int = 0,
)

/** The core refused the game: recorded as incompatible, unlike a network or download failure. */
private class GameRefused(message: String) : IOException(message)

/**
 * One retro game at a time: installs the system's core if needed, copies the game from the share
 * into the app cache (unzipped for console cores), then runs it in the native host. States:
 * IDLE → PREPARING → PLAYING → IDLE, or FAILED with a reason (left with [stop] or a new [play]).
 * Pad input goes through [padMapper], one player port per controller.
 */
class RetroSession(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val installer = CoreInstaller(appContext)
    private val gamesDir = File(appContext.cacheDir, "retro/games")
    private val systemDir = File(appContext.filesDir, "retro/system")
    private val savesDir = File(appContext.filesDir, "retro/saves")
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "gpsx-retro-session").apply { isDaemon = true } }
    private val lock = Any()
    private var handle = 0L
    private var surface: Surface? = null
    private var listener: ((RetroSessionSnapshot) -> Unit)? = null
    private var snapshot = RetroSessionSnapshot()

    val padMapper = RetroPadMapper(sink = ::submit, onQuitChord = ::stop)

    val isPlaying: Boolean get() = snapshot().phase == RetroPhase.PLAYING

    /** True while a game is being prepared or played: the pads belong to it. */
    val isActive: Boolean get() = snapshot().phase.let { it == RetroPhase.PREPARING || it == RetroPhase.PLAYING }

    fun snapshot(): RetroSessionSnapshot = synchronized(lock) { snapshot }

    fun setListener(value: ((RetroSessionSnapshot) -> Unit)?) {
        val current = synchronized(lock) {
            listener = value
            snapshot
        }
        value?.invoke(current)
    }

    fun play(game: RetroGame, library: RetroLibrary) {
        if (isActive) return
        val core = game.system.core
        if (core == null) {
            update { RetroSessionSnapshot(RetroPhase.FAILED, game.title, "Pas encore d’émulateur pour la ${game.system.label}.") }
            return
        }
        padMapper.reset()
        update { RetroSessionSnapshot(RetroPhase.PREPARING, game.title, "Préparation…") }
        worker.execute {
            try {
                val coreFile = installer.ensure(core) { detail -> update { copy(detail = detail) } }
                update { copy(detail = "Copie du jeu depuis le NAS…") }
                val gameFile = prepareGame(game, library)
                update { copy(detail = "Démarrage de l’émulateur…") }
                val opened = open(coreFile, gameFile, game, library)
                val geometry = RetroNative.nativeGeometry(opened)
                synchronized(lock) {
                    handle = opened
                    surface?.let { RetroNative.nativeSetSurface(opened, it) }
                }
                val sound = RetroNative.nativeStart(opened)
                update {
                    copy(
                        phase = RetroPhase.PLAYING,
                        detail = if (sound) "" else "Pas de sortie audio disponible.",
                        aspect = (geometry[2] / 1000f).takeIf { it > 0.3f && it < 4f } ?: (4f / 3f),
                    )
                }
            } catch (failure: Exception) {
                Log.w(TAG, "retro launch failed: ${failure.javaClass.simpleName}: ${failure.message}")
                closeHandle()
                update { copy(phase = RetroPhase.FAILED, detail = "Lancement impossible : ${failure.message ?: failure.javaClass.simpleName}") }
            }
        }
    }

    /**
     * Opens [gameFile] in the native host and records the outcome: "ok", or "ko:<reason>" when the
     * core refuses it (strict for arcade sets, where a missing ROM is only logged). A pending mark
     * is left during the load, so a crash inside the core is remembered too.
     */
    private fun open(coreFile: File, gameFile: File, game: RetroGame, library: RetroLibrary): Long {
        val saves = File(savesDir, game.system.name.lowercase(Locale.ROOT)).apply { mkdirs() }
        systemDir.mkdirs()
        val sram = File(saves, gameFile.nameWithoutExtension + ".srm")
        library.markPending(game)
        val opened =
            RetroNative.nativeOpen(
                coreFile.absolutePath, gameFile.absolutePath, systemDir.absolutePath, saves.absolutePath, sram.absolutePath, "", game.system.keepsZip,
            )
        if (opened == 0L) {
            val reason = RetroNative.nativeLastError().ifEmpty { "chargement impossible" }
            library.markCompat(game, "ko:$reason")
            throw GameRefused(reason)
        }
        library.markCompat(game, COMPAT_OK)
        return opened
    }

    /** The game's video surface; null when it is destroyed. Safe before or after the game starts. */
    fun setSurface(value: Surface?) {
        synchronized(lock) {
            surface = value
            if (handle != 0L) RetroNative.nativeSetSurface(handle, value)
        }
    }

    fun stop() {
        padMapper.reset()
        worker.execute {
            closeHandle()
            update { RetroSessionSnapshot() }
        }
    }

    override fun close() {
        stop()
        worker.shutdown()
    }

    private fun submit(input: RetroPlayerInput) {
        synchronized(lock) {
            if (handle != 0L) RetroNative.nativeSetInput(handle, input.port, input.buttons, input.leftX, input.leftY, input.rightX, input.rightY)
        }
        val players = padMapper.playerCount
        if (players != snapshot().players) update { copy(players = players) }
    }

    private fun closeHandle() {
        val closing = synchronized(lock) { handle.also { handle = 0L } }
        if (closing != 0L) RetroNative.nativeClose(closing)
    }

    /**
     * Copies the game into the cache. Console games are unzipped (the core gets the ROM itself);
     * arcade sets stay zipped, with the system's BIOS sets copied next to them when the share has
     * them in the game's folder or the system folder.
     */
    private fun prepareGame(game: RetroGame, library: RetroLibrary): File {
        gamesDir.deleteRecursively()
        gamesDir.mkdirs()
        val name = game.path.last()
        val copied = File(gamesDir, name)
        library.openGame(game.path).use { input -> copied.outputStream().use { input.copyTo(it) } }
        if (game.system.keepsZip) {
            val folders = listOf(game.path.dropLast(1), game.path.take(1)).distinct()
            game.system.biosSets.forEach { bios ->
                folders.firstNotNullOfOrNull { folder ->
                    runCatching {
                        val target = File(gamesDir, "$bios.zip")
                        library.openGame(folder + "$bios.zip").use { input -> target.outputStream().use { input.copyTo(it) } }
                        target
                    }.getOrNull()
                }
            }
            return copied
        }
        if (!name.endsWith(".zip", ignoreCase = true)) return copied
        val romExtensions = game.system.extensions - "zip"
        ZipInputStream(copied.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val entryName = entry.name.substringAfterLast('/')
                if (entry.isDirectory || entryName.substringAfterLast('.', "").lowercase(Locale.ROOT) !in romExtensions) continue
                val rom = File(gamesDir, entryName)
                var written = 0L
                rom.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        written += read
                        if (written > MAX_ROM_BYTES) throw IOException("jeu trop volumineux")
                        out.write(buffer, 0, read)
                    }
                }
                copied.delete()
                return rom
            }
        }
        throw IOException("aucun fichier de jeu ${game.system.label} dans l’archive")
    }

    private fun update(change: RetroSessionSnapshot.() -> RetroSessionSnapshot) {
        val (next, target) = synchronized(lock) {
            snapshot = snapshot.change()
            snapshot to listener
        }
        if (target != null) main.post { target(next) }
    }

    private companion object {
        const val TAG = "GPSxRetro"
        const val MAX_ROM_BYTES = 64L * 1024 * 1024
    }
}
