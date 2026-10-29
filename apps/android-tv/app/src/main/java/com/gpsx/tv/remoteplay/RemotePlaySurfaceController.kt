// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.remoteplay

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

    override fun surfaceCreated(holder: SurfaceHolder) = engine.setSurface(holder.surface)
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = engine.setSurface(holder.surface)
    override fun surfaceDestroyed(holder: SurfaceHolder) = engine.setSurface(null)

    override fun close() {
        if (closed) return
        closed = true
        view.holder.removeCallback(this)
        engine.setSurface(null)
    }
}
