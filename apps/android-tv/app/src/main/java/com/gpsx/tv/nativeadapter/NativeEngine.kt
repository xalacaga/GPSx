// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.nativeadapter

import android.view.Surface
import java.util.concurrent.atomic.AtomicLong

/**
 * Kotlin owner of one native adapter handle.
 *
 * The handle is an opaque registry key, never a native pointer. [close] is
 * idempotent and must run from the stream owner before Android objects die.
 */
class NativeEngine : AutoCloseable {
    private val handle = AtomicLong(nativeCreate().also { require(it != 0L) })
    private val surfaceGeneration = AtomicLong(0L)

    fun attachSurface(surface: Surface, generation: Long): NativeStatus =
        withHandle { nativeAttachSurface(it, surface, generation) }

    fun clearSurface(generation: Long): NativeStatus =
        withHandle { nativeClearSurface(it, generation) }

    fun startAudio(): NativeStatus = withHandle(::nativeStartAudio)

    fun stopAudio(): NativeStatus = withHandle(::nativeStopAudio)

    fun audioHealth(): NativeAudioHealth {
        val owned = handle.get()
        return if (owned == 0L) NativeAudioHealth.CLOSED else NativeAudioHealth.fromCode(nativeAudioHealth(owned))
    }

    internal fun resourceCountsForTest(): NativeResourceCounts {
        val encoded = nativeResourceCounts()
        return NativeResourceCounts(
            engines = (encoded ushr 32).toInt(),
            workers = encoded.toInt(),
            windows = nativeWindowCount(),
        )
    }

    internal fun nextSurfaceGeneration(): Long = surfaceGeneration.incrementAndGet()

    internal fun forceCodecFailureForTest(force: Boolean) {
        withHandle { nativeSetForceCodecFailureForTest(it, force) }
    }

    internal fun setLifecycleTestModeForTest(enabled: Boolean) {
        withHandle { nativeSetLifecycleTestModeForTest(it, enabled) }
    }

    internal fun forceAudioDisconnectForTest() {
        withHandle { nativeForceAudioDisconnectForTest(it) }
    }

    internal fun installCallbackDuringStopForTest() {
        withHandle(::nativeInstallCallbackDuringStopForTest)
    }

    override fun close() {
        val owned = handle.getAndSet(0L)
        if (owned != 0L) {
            nativeDestroy(owned)
        }
    }

    private inline fun withHandle(operation: (Long) -> Int): NativeStatus {
        val owned = handle.get()
        return if (owned == 0L) NativeStatus.CLOSED else NativeStatus.fromCode(operation(owned))
    }

    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeAttachSurface(handle: Long, surface: Surface, generation: Long): Int
    private external fun nativeClearSurface(handle: Long, generation: Long): Int
    private external fun nativeStartAudio(handle: Long): Int
    private external fun nativeStopAudio(handle: Long): Int
    private external fun nativeAudioHealth(handle: Long): Int
    private external fun nativeSetForceCodecFailureForTest(handle: Long, force: Boolean): Int
    private external fun nativeSetLifecycleTestModeForTest(handle: Long, enabled: Boolean): Int
    private external fun nativeForceAudioDisconnectForTest(handle: Long): Int
    private external fun nativeInstallCallbackDuringStopForTest(handle: Long): Int
    private external fun nativeResourceCounts(): Long
    private external fun nativeWindowCount(): Int

    companion object {
        init {
            System.loadLibrary("gpsx_android_adapter")
        }
    }
}

internal data class NativeResourceCounts(val engines: Int, val workers: Int, val windows: Int)

enum class NativeAudioHealth(val code: Int) {
    READY(0),
    UNAVAILABLE(1),
    DISCONNECTED(2),
    CLOSED(3),
    ;

    companion object {
        fun fromCode(code: Int): NativeAudioHealth = entries.firstOrNull { it.code == code } ?: DISCONNECTED
    }
}

enum class NativeStatus(val code: Int) {
    OK(0),
    CLOSED(1),
    STALE_GENERATION(2),
    INVALID_SURFACE(3),
    CODEC_ERROR(4),
    AUDIO_ERROR(5),
    ;

    companion object {
        fun fromCode(code: Int): NativeStatus = entries.firstOrNull { it.code == code } ?: CLOSED
    }
}
