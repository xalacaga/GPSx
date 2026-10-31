// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.remoteplay

import android.os.Build
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView

class RemotePlaySurfaceController(
    private val view: SurfaceView,
    private val engine: RemotePlayEngine,
) : SurfaceHolder.Callback, AutoCloseable {
    private var closed = false

    init {
        view.holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        holder.surface.requestStreamFrameRate()
        engine.setSurface(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        holder.surface.requestStreamFrameRate()
        engine.setSurface(holder.surface)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) = engine.setSurface(null)

    override fun close() {
        if (closed) return
        closed = true
        view.holder.removeCallback(this)
        engine.setSurface(null)
    }
}

/** Asks the display to run at the stream's 60 fps so frames are not judder-converted. */
private fun Surface.requestStreamFrameRate() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isValid) {
        runCatching { setFrameRate(60f, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE) }
    }
}
