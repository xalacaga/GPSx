// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.retro

import android.view.Surface

/** Coarse JNI bridge to the native retro host (`retro_host.cpp`); one core at a time. */
internal object RetroNative {
    init {
        System.loadLibrary("gpsx_retro")
    }

    /**
     * Loads [corePath] and the game; returns a handle, or 0 with [nativeLastError] set. [options] holds
     * "key=value" lines answered to the core's option queries; other options keep their defaults.
     * With [strict], an error the core logs while loading (a missing ROM) fails the load.
     * [remoteNames] and [remoteFds] are the files of a game that stays on the share, which the core
     * reads under [RemoteGame.ROOT]; they stay open until [nativeClose] returns.
     */
    external fun nativeOpen(
        corePath: String,
        gamePath: String,
        systemDir: String,
        saveDir: String,
        sramPath: String,
        options: String,
        strict: Boolean,
        remoteNames: Array<String>,
        remoteFds: IntArray,
    ): Long

    external fun nativeLastError(): String

    /** [base width, base height, aspect × 1000, fps × 1000]. */
    external fun nativeGeometry(handle: Long): IntArray

    /** Starts the core thread with OpenSL ES ([openSlEs]) or AAudio output; false when it runs without sound. */
    external fun nativeStart(handle: Long, openSlEs: Boolean): Boolean

    external fun nativeSetSurface(handle: Long, surface: Surface?)

    /** Player [port] state: RetroPad button bits and sticks in -32768..32767. */
    external fun nativeSetInput(handle: Long, port: Int, buttons: Int, leftX: Int, leftY: Int, rightX: Int, rightY: Int)

    /** Asks for the next disc of the game; the core thread changes it before its next frame. */
    external fun nativeNextDisk(handle: Long)

    /** [current disc from 0, number of discs]; 0 discs when the core offers no disc changing. */
    external fun nativeDisks(handle: Long): IntArray

    /** [frames run, frames shown, audio underruns, dropped audio frames, core asked to shut down]. */
    external fun nativeStats(handle: Long): LongArray

    /** Stops the core, writes its save RAM and frees the handle. */
    external fun nativeClose(handle: Long)
}
