// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.remoteplay

import android.os.Handler
import android.os.Looper
import android.util.Log
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

/** Trigger buttons in the raw HID layout; the analog trigger axes own L2/R2, so these are consumed only. */
internal const val SONY_HID_TRIGGER_KEY = 0

/**
 * Raw HID button order of DualSense and DualShock 4 when no Sony kernel driver is bound
 * (hid-generic on Android 11 kernels): HID button n arrives as Linux scan code BTN_GAMEPAD + n - 1,
 * so Generic.kl turns Square into BUTTON_A and Cross into BUTTON_B. Mapping the scan code restores
 * the physical PS5 layout whatever key layout file the TV ships.
 */
internal fun sonyHidScanCodeToPs5Button(scanCode: Int): Int? =
    when (scanCode) {
        0x130 -> Ps5ButtonMask.SQUARE
        0x131 -> Ps5ButtonMask.CROSS
        0x132 -> Ps5ButtonMask.CIRCLE
        0x133 -> Ps5ButtonMask.TRIANGLE
        0x134 -> Ps5ButtonMask.L1
        0x135 -> Ps5ButtonMask.R1
        0x136, 0x137 -> SONY_HID_TRIGGER_KEY
        0x138 -> Ps5ButtonMask.CREATE
        0x139 -> Ps5ButtonMask.OPTIONS
        0x13a -> Ps5ButtonMask.L3
        0x13b -> Ps5ButtonMask.R3
        0x13c -> Ps5ButtonMask.PLAYSTATION
        0x13d -> Ps5ButtonMask.TOUCHPAD
        else -> null
    }

/**
 * INFERENCE: a Sony pad whose key layout yields BUTTON_C and BUTTON_Z is the raw HID layout read
 * through Generic.kl; Sony kernel drivers never report those two buttons, and the AOSP DualSense
 * fallback layout maps them to face buttons instead.
 */
private fun InputDevice.usesSonyRawHidLayout(): Boolean =
    vendorId == SONY_VENDOR_ID &&
        hasKeys(KeyEvent.KEYCODE_BUTTON_C, KeyEvent.KEYCODE_BUTTON_Z).all { it }

private const val PAD_LOG_TAG = "GPSxPad"

private val BUTTON_NAMES =
    listOf(
        Ps5ButtonMask.CROSS to "CROSS", Ps5ButtonMask.CIRCLE to "CIRCLE", Ps5ButtonMask.SQUARE to "SQUARE", Ps5ButtonMask.TRIANGLE to "TRIANGLE",
        Ps5ButtonMask.DPAD_UP to "UP", Ps5ButtonMask.DPAD_DOWN to "DOWN", Ps5ButtonMask.DPAD_LEFT to "LEFT", Ps5ButtonMask.DPAD_RIGHT to "RIGHT",
        Ps5ButtonMask.L1 to "L1", Ps5ButtonMask.R1 to "R1", Ps5ButtonMask.L3 to "L3", Ps5ButtonMask.R3 to "R3",
        Ps5ButtonMask.CREATE to "CREATE", Ps5ButtonMask.OPTIONS to "OPTIONS", Ps5ButtonMask.PLAYSTATION to "PS", Ps5ButtonMask.TOUCHPAD to "TOUCHPAD",
    )

/** Held PlayStation buttons as "CROSS+L1", or "none". */
internal fun buttonNames(buttons: Int): String =
    BUTTON_NAMES.filter { buttons and it.first != 0 }.joinToString("+") { it.second }.ifEmpty { "none" }

/** L1 + R1 + Options held together (owner's choice of three buttons), reserved to leave the stream. */
internal const val QUIT_CHORD = Ps5ButtonMask.L1 or Ps5ButtonMask.R1 or Ps5ButtonMask.OPTIONS
internal const val QUIT_CHORD_HOLD_MILLIS = 1_000L

/**
 * Tracks the quit chord across pads. [update] returns true when the chord has just been completed
 * on some pad (the caller starts the hold timer) and false otherwise; [held] tells whether the chord
 * is still complete when the timer fires.
 */
internal class QuitChord {
    private val complete = mutableSetOf<Int>()

    fun update(deviceId: Int, buttons: Int): Boolean {
        val isComplete = buttons and QUIT_CHORD == QUIT_CHORD
        return if (isComplete) complete.add(deviceId) else { complete.remove(deviceId); false }
    }

    fun held(): Boolean = complete.isNotEmpty()

    fun isHeld(deviceId: Int): Boolean = deviceId in complete

    fun reset() = complete.clear()
}

private data class PlatformControllerState(
    var buttons: Int = 0,
    var l2: Int = 0,
    var r2: Int = 0,
    var leftX: Int = 0,
    var leftY: Int = 0,
    var rightX: Int = 0,
    var rightY: Int = 0,
)

/**
 * Android capture feeding the portable Rust `gpsx-input` merge/edge state machine. Holding
 * [QUIT_CHORD] for [QUIT_CHORD_HOLD_MILLIS] calls [onQuitChord] on the main thread, so a stream can
 * be left with the pad alone; while the chord is complete its buttons are released towards the game.
 */
class ControllerMapper(
    private val sink: (ControllerState) -> Unit,
    private val onQuitChord: () -> Unit = {},
) {
    private val controllers = mutableMapOf<Int, PlatformControllerState>()
    private val rawHidLayout = mutableMapOf<Int, Boolean>()
    private val bridge = GpsxInputBridge()
    private val quitChord = QuitChord()
    private val main = Handler(Looper.getMainLooper())
    private val quitIfStillHeld = Runnable {
        if (quitChord.held()) {
            quitChord.reset()
            onQuitChord()
        }
    }

    /**
     * Forgets every held button and stick. Called when a stream starts or ends: buttons released
     * outside a stream (the L1+R1+Options chord that ended the previous one) never reach this
     * mapper, and would otherwise still count as held. FACT (GP520 live log, 0.2.22): a stale
     * L1+R1+OPTIONS made the next stream quit one second after the first pad movement.
     */
    fun reset() {
        controllers.clear()
        quitChord.reset()
        main.removeCallbacks(quitIfStillHeld)
        bridge.reset()
    }

    fun key(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return false
        val state = controllers.getOrPut(event.deviceId, ::PlatformControllerState)
        val pressed = event.action == KeyEvent.ACTION_DOWN
        val rawHid = isRawHid(event.device)
        if (rawHid) {
            val mask = sonyHidScanCodeToPs5Button(event.scanCode)
            if (mask == null) {
                if (pressed && !isDpadKey(event.keyCode)) logPad("unmapped raw HID button code ${event.keyCode} scan 0x${event.scanCode.toString(16)}")
                return isDpadKey(event.keyCode)
            }
            if (mask == SONY_HID_TRIGGER_KEY) return true
            state.buttons = if (pressed) state.buttons or mask else state.buttons and mask.inv()
            logButtons(event, rawHid, state)
            emit(event.deviceId, event.eventTime * 1_000_000L, state)
            return true
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_L2 -> state.l2 = if (pressed) 255 else 0
            KeyEvent.KEYCODE_BUTTON_R2 -> state.r2 = if (pressed) 255 else 0
            else -> {
                val mask = androidKeyToPs5Button(event.device?.vendorId ?: 0, event.keyCode)
                if (mask == null) {
                    if (pressed && KeyEvent.isGamepadButton(event.keyCode)) logPad("unmapped button code ${event.keyCode} scan 0x${event.scanCode.toString(16)}")
                    return false
                }
                state.buttons = if (pressed) state.buttons or mask else state.buttons and mask.inv()
            }
        }
        logButtons(event, rawHid, state)
        emit(event.deviceId, event.eventTime * 1_000_000L, state)
        return true
    }

    /** One line per button change for on-device checks through the live log; stick motion is not logged. */
    private fun logButtons(event: KeyEvent, rawHid: Boolean, state: PlatformControllerState) {
        if (event.repeatCount > 0) return
        val action = if (event.action == KeyEvent.ACTION_DOWN) "down" else "up"
        logPad(
            // Worded without "key": the live log drops any line containing it (secret filter).
            "code ${event.keyCode} scan 0x${event.scanCode.toString(16)} $action " +
                "(${if (rawHid) "raw HID" else "Android layout"}) -> ${buttonNames(state.buttons)} l2=${state.l2} r2=${state.r2}",
        )
    }

    private fun logPad(message: String) {
        runCatching { Log.i(PAD_LOG_TAG, message) }
    }

    fun motion(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_MOVE || event.source and InputDevice.SOURCE_JOYSTICK == 0) return false
        val state = controllers.getOrPut(event.deviceId, ::PlatformControllerState)
        state.leftX = event.axis(MotionEvent.AXIS_X).stick()
        state.leftY = event.axis(MotionEvent.AXIS_Y).stick()
        state.rightX = event.axis(MotionEvent.AXIS_Z).stick()
        state.rightY = event.axis(MotionEvent.AXIS_RZ).stick()
        if (isRawHid(event.device)) {
            // Raw HID reports L2/R2 pressure as Rx/Ry, which Android centres on -1..1.
            state.l2 = event.rangeFraction(MotionEvent.AXIS_RX).trigger()
            state.r2 = event.rangeFraction(MotionEvent.AXIS_RY).trigger()
        } else {
            state.l2 = maxOf(event.axis(MotionEvent.AXIS_LTRIGGER), event.axis(MotionEvent.AXIS_BRAKE)).trigger()
            state.r2 = maxOf(event.axis(MotionEvent.AXIS_RTRIGGER), event.axis(MotionEvent.AXIS_GAS)).trigger()
        }
        state.buttons = state.buttons.withHat(event.axis(MotionEvent.AXIS_HAT_X), event.axis(MotionEvent.AXIS_HAT_Y))
        emit(event.deviceId, event.eventTime * 1_000_000L, state)
        return true
    }

    private fun emit(deviceId: Int, eventTimeNs: Long, state: PlatformControllerState) {
        if (quitChord.update(deviceId, state.buttons)) {
            main.removeCallbacks(quitIfStillHeld)
            main.postDelayed(quitIfStillHeld, QUIT_CHORD_HOLD_MILLIS)
        }
        val buttons = if (quitChord.isHeld(deviceId)) state.buttons and QUIT_CHORD.inv() else state.buttons
        bridge.normalize(
            deviceId,
            buttons,
            state.l2,
            state.r2,
            state.leftX,
            state.leftY,
            state.rightX,
            state.rightY,
            eventTimeNs,
        )?.let(sink)
    }

    // Android assigns a new device ID when a pad reconnects, so the cached layout never goes stale.
    private fun isRawHid(device: InputDevice?): Boolean =
        device != null && rawHidLayout.getOrPut(device.id) { device.usesSonyRawHidLayout() }

    // D-pad keys of a raw HID pad are synthesised from the HAT axis; consume them so focus never moves.
    private fun isDpadKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT

    private fun MotionEvent.axis(axis: Int): Float = getAxisValue(axis).coerceIn(-1f, 1f)

    /** Position of [axis] within its reported range, 0 at rest and 1 fully pressed. */
    private fun MotionEvent.rangeFraction(axis: Int): Float {
        val range = device?.getMotionRange(axis, source) ?: return 0f
        if (range.range <= 0f) return 0f
        return (getAxisValue(axis) - range.min) / range.range
    }
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
