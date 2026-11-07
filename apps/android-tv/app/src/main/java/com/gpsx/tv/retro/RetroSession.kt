// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.retro

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import com.gpsx.tv.remoteplay.bluetoothOutputActive
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
    /** System of the game being prepared or played, for the controls diagram. */
    val system: RetroSystem? = null,
)

/** The core refused the game: recorded as incompatible, unlike a network or download failure. */
private class GameRefused(message: String) : IOException(message)

/**
 * One retro game at a time: installs the system's core if needed, copies the game from the share
 * into the app cache (unzipped for console cores) or, for CD images, leaves it on the share and
 * opens it as a [RemoteGame], then runs it in the native host. States:
 * IDLE → PREPARING → PLAYING → IDLE, or FAILED with a reason (left with [stop] or a new [play]).
 * Pad input goes through [padMapper], one player port per controller.
 */
class RetroSession(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val installer = CoreInstaller(appContext)
    private val storage = RetroStorage(appContext)
    private val systemDir = File(appContext.filesDir, "retro/system")
    private val savesDir = storage.savesDir
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "gpsx-retro-session").apply { isDaemon = true } }
    private val lock = Any()
    private var handle = 0L
    private var remote: RemoteGame? = null
    private var surface: Surface? = null
    private var listener: ((RetroSessionSnapshot) -> Unit)? = null
    private var snapshot = RetroSessionSnapshot()

    val padMapper = RetroPadMapper(sink = ::submit, onQuitChord = ::stop, onDiskChord = ::nextDisk)

    init {
        worker.execute(storage::removeLegacyCopies)
    }

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
        update { RetroSessionSnapshot(RetroPhase.PREPARING, game.title, "Préparation…", system = game.system) }
        worker.execute {
            try {
                val coreFile = installer.ensure(core) { detail -> update { copy(detail = detail) } }
                copyBios(game, library)
                val gameFile = if (game.staysOnShare) openRemote(game, library) else prepareGame(game, library)
                update { copy(detail = "Démarrage de l’émulateur…") }
                val opened = open(coreFile, gameFile, game, library)
                val geometry = RetroNative.nativeGeometry(opened)
                synchronized(lock) {
                    handle = opened
                    surface?.let { RetroNative.nativeSetSurface(opened, it) }
                }
                val sound = RetroNative.nativeStart(opened, useOpenSlEs())
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
                coreFile.absolutePath, gameFile.path, systemDir.absolutePath, saves.absolutePath, sram.absolutePath, game.system.coreOptions, game.system.keepsZip,
                remote?.names ?: emptyArray(), remote?.fds ?: IntArray(0),
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

    /** The next disc of the running game, shown briefly over the picture. */
    private fun nextDisk() {
        synchronized(lock) { if (handle != 0L) RetroNative.nativeNextDisk(handle) }
        main.postDelayed(::showDisk, DISK_NOTICE_DELAY_MILLIS)
    }

    private fun showDisk() {
        val disks = synchronized(lock) { if (handle != 0L) RetroNative.nativeDisks(handle) else null } ?: return
        if (disks[1] < 2) return
        update { copy(detail = "Disque ${disks[0] + 1} / ${disks[1]}") }
        main.postDelayed({ update { if (phase == RetroPhase.PLAYING) copy(detail = "") else this } }, DISK_NOTICE_MILLIS)
    }

    private fun submit(input: RetroPlayerInput) {
        synchronized(lock) {
            if (handle != 0L) RetroNative.nativeSetInput(handle, input.port, input.buttons, input.leftX, input.leftY, input.rightX, input.rightY)
        }
        val players = padMapper.playerCount
        if (players != snapshot().players) update { copy(players = players) }
    }

    /**
     * Same rule as Remote Play: OpenSL ES when the compatibility setting is on or a Bluetooth output
     * is active. FACT (GP520, 0.2.4): AAudio routed to a Bluetooth soundbar runs without being heard.
     */
    private fun useOpenSlEs(): Boolean {
        val forced = appContext.getSharedPreferences("gpsx_engine_settings", Context.MODE_PRIVATE).getBoolean("audio_compatibility", false)
        val bluetooth = appContext.getSystemService(AudioManager::class.java)?.let(::bluetoothOutputActive) ?: false
        Log.i(TAG, "retro audio: compatibility setting $forced, Bluetooth output $bluetooth")
        return forced || bluetooth
    }

    private fun closeHandle() {
        val closing = synchronized(lock) { handle.also { handle = 0L } }
        if (closing != 0L) RetroNative.nativeClose(closing)
        remote?.close()
        remote = null
    }

    /**
     * Opens a CD game where it is, with the tracks its .cue sheets name. A game of several discs
     * loads through a playlist, named after the game because its saves take that name. The file
     * returned exists only for the core, under [RemoteGame.ROOT].
     */
    private fun openRemote(game: RetroGame, library: RetroLibrary): File {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) throw IOException("lecture depuis le NAS impossible avant Android 8")
        update { copy(detail = "Ouverture du jeu sur le NAS…") }
        val opened = RemoteGame(appContext).also { remote = it }
        val discs = game.disks.ifEmpty { listOf(game.path) }
        discs.forEach { disc ->
            opened.add(disc.last(), library.openRandom(disc))
            if (disc.last().endsWith(".cue", ignoreCase = true)) {
                val sheet = library.openGame(disc).use { it.bufferedReader().readText() }
                CueSheet.files(sheet).forEach { track -> opened.add(track, library.openRandom(disc.dropLast(1) + track)) }
            }
        }
        if (discs.size == 1) return File(RemoteGame.ROOT, discs.single().last())
        val name = game.title.filter { it.isLetterOrDigit() || it == ' ' }.ifBlank { "game" } + ".m3u"
        val playlist = File(appContext.cacheDir, "retro-discs.m3u")
        playlist.writeText(discs.joinToString("\n", postfix = "\n") { it.last() })
        opened.addLocal(name, playlist)
        return File(RemoteGame.ROOT, name)
    }

    /**
     * The game file on the TV: the kept copy when a complete one exists, else a fresh copy from the
     * share. A copy is complete once its marker names the game file, so an interrupted copy is redone.
     */
    private fun prepareGame(game: RetroGame, library: RetroLibrary): File {
        val dir = storage.gameDir(game)
        val ready = File(dir, RetroStorage.READY_MARKER)
        if (ready.isFile) File(dir, ready.readText()).takeIf(File::isFile)?.let { return it }
        update { copy(detail = "Copie du jeu depuis le NAS…") }
        dir.deleteRecursively()
        dir.mkdirs()
        val file = if (game.disks.isEmpty()) copyGame(game, library, dir) else copyDisks(game, library, dir)
        ready.writeText(file.name)
        return file
    }

    /**
     * Copies every disk of a multi-disk game and writes the M3U playlist the core loads, listing the
     * disks in order (with the multi-drive option they go in DF0–DF3).
     */
    private fun copyDisks(game: RetroGame, library: RetroLibrary, dir: File): File {
        val names = game.disks.mapIndexed { index, disk ->
            update { copy(detail = "Copie de la disquette ${index + 1} / ${game.disks.size} depuis le NAS…") }
            val target = File(dir, disk.last())
            library.openGame(disk).use { input -> target.outputStream().use { input.copyTo(it) } }
            target.name
        }
        return File(dir, "game.m3u").apply { writeText(names.joinToString("\n", postfix = "\n")) }
    }

    /**
     * Copies the system's BIOS files (Amiga Kickstart) from its share folder into the core's system
     * folder, once; a missing folder is not an error (the core falls back to its own replacement).
     */
    private fun copyBios(game: RetroGame, library: RetroLibrary) {
        val folder = game.system.biosFolder ?: return
        val source = game.path.take(1) + folder
        val files = runCatching { library.listShare(source) }.getOrDefault(emptyList()).filter { !it.directory }
        systemDir.mkdirs()
        files.forEach { file ->
            val target = File(systemDir, file.name)
            if (target.isFile && target.length() == file.size) return@forEach
            runCatching { library.openGame(source + file.name).use { input -> target.outputStream().use { input.copyTo(it) } } }
                .onFailure { target.delete() }
        }
    }

    /**
     * Copies the game into [gamesDir]. Console games are unzipped (the core gets the ROM itself);
     * arcade sets stay zipped, with the system's BIOS sets copied next to them when the share has
     * them in the game's folder or the system folder. A .cue brings the tracks it names.
     */
    private fun copyGame(game: RetroGame, library: RetroLibrary, gamesDir: File): File {
        val name = game.path.last()
        val copied = File(gamesDir, name)
        library.openGame(game.path).use { input -> copied.outputStream().use { input.copyTo(it) } }
        if (name.endsWith(".cue", ignoreCase = true)) {
            CueSheet.files(copied.readText()).forEach { track ->
                library.openGame(game.path.dropLast(1) + track).use { input -> File(gamesDir, track).outputStream().use { input.copyTo(it) } }
            }
            return copied
        }
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
        const val DISK_NOTICE_DELAY_MILLIS = 300L
        const val DISK_NOTICE_MILLIS = 3_000L
    }
}
