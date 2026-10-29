// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.remoteplay

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.metallic.chiaki.lib.ControllerState
import kotlin.math.roundToInt

internal object Ps5ButtonMask {
    const val CROSS = 1 shl 0
    const val CIRCLE = 1 shl 1
    const val SQUARE = 1 shl 2
    const val TRIANGLE = 1 shl 3
    const val DPAD_UP = 1 shl 4
    const val DPAD_DOWN = 1 shl 5
    const val DPAD_LEFT = 1 shl 6
    const val DPAD_RIGHT = 1 shl 7
    const val L1 = 1 shl 8
    const val R1 = 1 shl 9
    const val L3 = 1 shl 10
    const val R3 = 1 shl 11
    const val CREATE = 1 shl 12
    const val OPTIONS = 1 shl 13
    const val PLAYSTATION = 1 shl 14
    const val TOUCHPAD = 1 shl 15
}

/** Maps Android's standard gamepad layout to the PS5 controller model used by the core. */
internal fun androidKeyToPs5Button(vendorId: Int, keyCode: Int): Int? =
    when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> Ps5ButtonMask.CROSS
        KeyEvent.KEYCODE_BUTTON_B -> Ps5ButtonMask.CIRCLE
        KeyEvent.KEYCODE_BUTTON_X -> Ps5ButtonMask.SQUARE
        KeyEvent.KEYCODE_BUTTON_Y -> Ps5ButtonMask.TRIANGLE
        KeyEvent.KEYCODE_DPAD_UP -> Ps5ButtonMask.DPAD_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> Ps5ButtonMask.DPAD_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> Ps5ButtonMask.DPAD_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> Ps5ButtonMask.DPAD_RIGHT
        KeyEvent.KEYCODE_BUTTON_L1 -> Ps5ButtonMask.L1
        KeyEvent.KEYCODE_BUTTON_R1 -> Ps5ButtonMask.R1
        KeyEvent.KEYCODE_BUTTON_THUMBL -> Ps5ButtonMask.L3
        KeyEvent.KEYCODE_BUTTON_THUMBR -> Ps5ButtonMask.R3
        KeyEvent.KEYCODE_BUTTON_SELECT -> Ps5ButtonMask.CREATE
        KeyEvent.KEYCODE_BUTTON_START -> Ps5ButtonMask.OPTIONS
        KeyEvent.KEYCODE_BUTTON_MODE -> Ps5ButtonMask.PLAYSTATION
        // AOSP's DualSense fallback key layout exposes the touchpad click as BUTTON_1.
        KeyEvent.KEYCODE_BUTTON_1 -> if (vendorId == SONY_VENDOR_ID) Ps5ButtonMask.TOUCHPAD else null
        else -> null
    }

private const val SONY_VENDOR_ID = 0x054c

private data class PlatformControllerState(
    var buttons: Int = 0,
    var l2: Int = 0,
    var r2: Int = 0,
    var leftX: Int = 0,
    var leftY: Int = 0,
    var rightX: Int = 0,
    var rightY: Int = 0,
)

/** Android capture feeding the portable Rust `gpsx-input` merge/edge state machine. */
class ControllerMapper(private val sink: (ControllerState) -> Unit) {
    private val controllers = mutableMapOf<Int, PlatformControllerState>()
    private val bridge = GpsxInputBridge()

    fun key(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return false
        val state = controllers.getOrPut(event.deviceId, ::PlatformControllerState)
        val pressed = event.action == KeyEvent.ACTION_DOWN
        when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_L2 -> state.l2 = if (pressed) 255 else 0
            KeyEvent.KEYCODE_BUTTON_R2 -> state.r2 = if (pressed) 255 else 0
            else -> {
                val mask = androidKeyToPs5Button(event.device?.vendorId ?: 0, event.keyCode) ?: return false
                state.buttons = if (pressed) state.buttons or mask else state.buttons and mask.inv()
            }
        }
        emit(event.deviceId, event.eventTime * 1_000_000L, state)
        return true
    }

    fun motion(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_MOVE || event.source and InputDevice.SOURCE_JOYSTICK == 0) return false
        val state = controllers.getOrPut(event.deviceId, ::PlatformControllerState)
        state.leftX = event.axis(MotionEvent.AXIS_X).stick()
        state.leftY = event.axis(MotionEvent.AXIS_Y).stick()
        state.rightX = event.axis(MotionEvent.AXIS_Z).stick()
        state.rightY = event.axis(MotionEvent.AXIS_RZ).stick()
        state.l2 = maxOf(event.axis(MotionEvent.AXIS_LTRIGGER), event.axis(MotionEvent.AXIS_BRAKE)).trigger()
        state.r2 = maxOf(event.axis(MotionEvent.AXIS_RTRIGGER), event.axis(MotionEvent.AXIS_GAS)).trigger()
        state.buttons = state.buttons.withHat(event.axis(MotionEvent.AXIS_HAT_X), event.axis(MotionEvent.AXIS_HAT_Y))
        emit(event.deviceId, event.eventTime * 1_000_000L, state)
        return true
    }

    private fun emit(deviceId: Int, eventTimeNs: Long, state: PlatformControllerState) {
        bridge.normalize(
            deviceId,
            state.buttons,
            state.l2,
            state.r2,
            state.leftX,
            state.leftY,
            state.rightX,
            state.rightY,
            eventTimeNs,
        )?.let(sink)
    }

    private fun MotionEvent.axis(axis: Int): Float = getAxisValue(axis).coerceIn(-1f, 1f)
    private fun Float.stick(): Int = (this * Short.MAX_VALUE).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
    private fun Float.trigger(): Int = (coerceIn(0f, 1f) * 255).roundToInt()

    private fun Int.withHat(x: Float, y: Float): Int {
        val dpadMask = Ps5ButtonMask.DPAD_UP or Ps5ButtonMask.DPAD_DOWN or Ps5ButtonMask.DPAD_LEFT or Ps5ButtonMask.DPAD_RIGHT
        var result = this and dpadMask.inv()
        if (x < -0.5f) result = result or Ps5ButtonMask.DPAD_LEFT
        if (x > 0.5f) result = result or Ps5ButtonMask.DPAD_RIGHT
        if (y < -0.5f) result = result or Ps5ButtonMask.DPAD_UP
        if (y > 0.5f) result = result or Ps5ButtonMask.DPAD_DOWN
        return result
    }
}
