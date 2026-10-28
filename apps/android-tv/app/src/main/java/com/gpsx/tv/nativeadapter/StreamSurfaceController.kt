// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.nativeadapter

import android.view.SurfaceHolder
import android.view.SurfaceView

/** Owns the Surface callback registration and monotonically increasing generation. */
class StreamSurfaceController(
    private val view: SurfaceView,
    private val engine: NativeEngine,
    private val statusSink: (NativeStatus) -> Unit = {},
) : SurfaceHolder.Callback, AutoCloseable {
    private var generation = 0L
    private var closed = false

    init {
        view.holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (!closed) {
            generation = engine.nextSurfaceGeneration()
            statusSink(engine.attachSurface(holder.surface, generation))
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (!closed) {
            statusSink(engine.clearSurface(generation))
        }
    }

    override fun close() {
        if (!closed) {
            closed = true
            view.holder.removeCallback(this)
            statusSink(engine.clearSurface(generation))
        }
    }
}
