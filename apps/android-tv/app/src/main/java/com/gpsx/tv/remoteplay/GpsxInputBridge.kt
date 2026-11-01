// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.remoteplay

import com.metallic.chiaki.lib.ControllerState

/** Coarse JNI boundary into the portable Rust `gpsx-input` state machine. */
internal class GpsxInputBridge {
    init {
        nativeReset()
    }

    /** Forgets every pad's state, as when the bridge is created. */
    @Synchronized
    fun reset() = nativeReset()

    @Synchronized
    fun normalize(
        deviceId: Int,
        buttons: Int,
        l2: Int,
        r2: Int,
        leftX: Int,
        leftY: Int,
        rightX: Int,
        rightY: Int,
        eventTimeNs: Long,
    ): ControllerState? {
        val packed = nativeUpdate(deviceId.toLong(), buttons, l2, r2, leftX, leftY, rightX, rightY, eventTimeNs)
        if (packed < 0 || nativeOverflowCount() != 0L) return null
        val normalizedButtons = packed.toInt()
        return ControllerState(
            buttons = normalizedButtons.toChiakiButtons(),
            l2State = ((packed ushr 32) and 0xff).toUByte(),
            r2State = ((packed ushr 40) and 0xff).toUByte(),
            leftX = nativeAxis(0).toShort(),
            leftY = nativeAxis(1).toShort(),
            rightX = nativeAxis(2).toShort(),
            rightY = nativeAxis(3).toShort(),
        )
    }

    private external fun nativeUpdate(
        deviceId: Long,
        buttons: Int,
        l2: Int,
        r2: Int,
        leftX: Int,
        leftY: Int,
        rightX: Int,
        rightY: Int,
        eventTimeNs: Long,
    ): Long

    private external fun nativeAxis(axis: Int): Int
    private external fun nativeOverflowCount(): Long
    private external fun nativeReset()

    private fun Int.toChiakiButtons(): UInt {
        var result = 0U
        fun map(from: Int, to: UInt) {
            if (this and (1 shl from) != 0) result = result or to
        }
        map(0, ControllerState.CROSS)
        map(1, ControllerState.CIRCLE)
        map(2, ControllerState.SQUARE)
        map(3, ControllerState.TRIANGLE)
        map(4, ControllerState.DPAD_UP)
        map(5, ControllerState.DPAD_DOWN)
        map(6, ControllerState.DPAD_LEFT)
        map(7, ControllerState.DPAD_RIGHT)
        map(8, ControllerState.L1)
        map(9, ControllerState.R1)
        map(10, ControllerState.L3)
        map(11, ControllerState.R3)
        map(12, ControllerState.SHARE)
        map(13, ControllerState.OPTIONS)
        map(14, ControllerState.PS)
        map(15, ControllerState.TOUCHPAD)
        return result
    }

    companion object {
        init {
            System.loadLibrary("gpsx_android_input")
        }
    }
}
