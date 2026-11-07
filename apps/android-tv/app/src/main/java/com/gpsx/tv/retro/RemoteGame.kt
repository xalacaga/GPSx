// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.retro

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.OsConstants
import androidx.annotation.RequiresApi
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * The files of a game that stays on the share. Each one is a system file descriptor the native host
 * reads with no JNI crossing: the system calls back here, on [thread], for the blocks the core asks
 * for. Nothing is written to the TV's storage. Owns the descriptors until [close], which comes after
 * the host has stopped.
 */
@RequiresApi(Build.VERSION_CODES.O)
internal class RemoteGame(context: Context) : AutoCloseable {
    private val storage = context.getSystemService(StorageManager::class.java)
    private val thread = HandlerThread("gpsx-retro-remote").apply { start() }
    private val handler = Handler(thread.looper)
    private val prefetch = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "gpsx-retro-prefetch").apply { isDaemon = true } }
    private val descriptors = linkedMapOf<String, ParcelFileDescriptor>()
    private val files = mutableListOf<ReadAheadFile>()

    /** Names the core sees under [ROOT], and their descriptors, in the same order. */
    val names: Array<String> get() = descriptors.keys.toTypedArray()
    val fds: IntArray get() = descriptors.values.map { it.fd }.toIntArray()

    fun add(name: String, source: RandomRead) {
        if (name in descriptors) {
            source.close()
            return
        }
        val file = ReadAheadFile(source, prefetch)
        files += file
        descriptors[name] = storage.openProxyFileDescriptor(ParcelFileDescriptor.MODE_READ_ONLY, callback(file), handler)
    }

    /** A small file GPSx wrote itself, such as a playlist. */
    fun addLocal(name: String, file: File) {
        descriptors[name] = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun callback(file: ReadAheadFile) =
        object : ProxyFileDescriptorCallback() {
            override fun onGetSize(): Long = file.size

            override fun onRead(offset: Long, size: Int, data: ByteArray): Int =
                try {
                    file.read(offset, data, 0, size)
                } catch (failure: IOException) {
                    throw ErrnoException("onRead", OsConstants.EIO, failure)
                }

            override fun onRelease() = Unit
        }

    override fun close() {
        descriptors.values.forEach { runCatching { it.close() } }
        descriptors.clear()
        files.forEach { runCatching { it.close() } }
        files.clear()
        prefetch.shutdown()
        thread.quitSafely()
    }

    companion object {
        /** Folder the native host answers for; it exists nowhere on the TV. */
        const val ROOT = "/gpsx-remote/"
    }
}
