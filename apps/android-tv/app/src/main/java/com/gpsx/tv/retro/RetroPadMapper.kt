// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.retro

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.gpsx.tv.remoteplay.Ps5ButtonMask
import com.gpsx.tv.remoteplay.QUIT_CHORD
import com.gpsx.tv.remoteplay.QUIT_CHORD_HOLD_MILLIS
import com.gpsx.tv.remoteplay.androidKeyToPs5Button
import com.gpsx.tv.remoteplay.buttonNames
import com.gpsx.tv.remoteplay.sonyHidScanCodeToPs5Button
import kotlin.math.roundToInt

/** RetroPad button bits, as libretro numbers its joypad buttons. */
internal object RetroPad {
    const val B = 1 shl 0
    const val Y = 1 shl 1
    const val SELECT = 1 shl 2
    const val START = 1 shl 3
    const val UP = 1 shl 4
    const val DOWN = 1 shl 5
    const val LEFT = 1 shl 6
    const val RIGHT = 1 shl 7
    const val A = 1 shl 8
    const val X = 1 shl 9
    const val L = 1 shl 10
    const val R = 1 shl 11
    const val L2 = 1 shl 12
    const val R2 = 1 shl 13
    const val L3 = 1 shl 14
    const val R3 = 1 shl 15

    /**
     * PlayStation layout → RetroPad by position: Cross (bottom) is B, Circle (right) A, Square
     * (left) Y, Triangle (top) X, Create is Select and Options Start.
     */
    fun fromPs5(buttons: Int, l2: Boolean, r2: Boolean): Int {
        var pad = 0
        fun map(from: Int, to: Int) {
            if (buttons and from != 0) pad = pad or to
        }
        map(Ps5ButtonMask.CROSS, B)
        map(Ps5ButtonMask.CIRCLE, A)
        map(Ps5ButtonMask.SQUARE, Y)
        map(Ps5ButtonMask.TRIANGLE, X)
        map(Ps5ButtonMask.DPAD_UP, UP)
        map(Ps5ButtonMask.DPAD_DOWN, DOWN)
        map(Ps5ButtonMask.DPAD_LEFT, LEFT)
        map(Ps5ButtonMask.DPAD_RIGHT, RIGHT)
        map(Ps5ButtonMask.L1, L)
        map(Ps5ButtonMask.R1, R)
        map(Ps5ButtonMask.L3, L3)
        map(Ps5ButtonMask.R3, R3)
        map(Ps5ButtonMask.CREATE, SELECT)
        map(Ps5ButtonMask.OPTIONS, START)
        if (l2) pad = pad or L2
        if (r2) pad = pad or R2
        return pad
    }
}

/** One player's pad as the retro core sees it. */
data class RetroPlayerInput(val port: Int, val buttons: Int, val leftX: Int, val leftY: Int, val rightX: Int, val rightY: Int)

/**
 * Android pad events → one RetroPad per physical controller. Each controller gets the next free
 * player port (P1, P2…) the first time it sends a button, up to [ports]. The left stick also drives
 * the D-pad, since most retro games read only the D-pad. Holding L1 + R1 + Options for
 * [QUIT_CHORD_HOLD_MILLIS] on any pad calls [onQuitChord], as in Remote Play; L1 + R1 + Create the
 * same way calls [onDiskChord].
 */
class RetroPadMapper(
    private val sink: (RetroPlayerInput) -> Unit,
    private val onQuitChord: () -> Unit,
    private val onDiskChord: () -> Unit = {},
    private val ports: Int = 4,
) {
    private class Pad(val port: Int) {
        var buttons = 0
        var l2 = false
        var r2 = false
        var hat = 0
        var leftX = 0
        var leftY = 0
        var rightX = 0
        var rightY = 0
    }

    private val pads = mutableMapOf<Int, Pad>()
    private val main = Handler(Looper.getMainLooper())
    private var chordHeld = false
    private val quitIfStillHeld = Runnable { if (chordHeld) { chordHeld = false; onQuitChord() } }
    private var diskChordHeld = false
    private val diskIfStillHeld = Runnable { if (diskChordHeld) { diskChordHeld = false; onDiskChord() } }

    val playerCount: Int get() = pads.size

    fun reset() {
        pads.clear()
        chordHeld = false
        diskChordHeld = false
        main.removeCallbacks(quitIfStillHeld)
        main.removeCallbacks(diskIfStillHeld)
    }

    fun key(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return false
        val pressed = event.action == KeyEvent.ACTION_DOWN
        val sonyRaw = event.device?.let { it.vendorId == SONY_VENDOR_ID && it.hasKeys(KeyEvent.KEYCODE_BUTTON_C, KeyEvent.KEYCODE_BUTTON_Z).all { has -> has } } == true
        val pad = padFor(event.deviceId) ?: return true
        when {
            !sonyRaw && event.keyCode == KeyEvent.KEYCODE_BUTTON_L2 -> pad.l2 = pressed
            !sonyRaw && event.keyCode == KeyEvent.KEYCODE_BUTTON_R2 -> pad.r2 = pressed
            else -> {
                val mask =
                    if (sonyRaw) sonyHidScanCodeToPs5Button(event.scanCode) else androidKeyToPs5Button(event.device?.vendorId ?: 0, event.keyCode)
                if (mask == null || mask == 0) {
                    if (pressed && event.repeatCount == 0) logPad("retro P${pad.port + 1} unmapped code ${event.keyCode} scan 0x${event.scanCode.toString(16)}")
                    return isGamepad(event)
                }
                pad.buttons = if (pressed) pad.buttons or mask else pad.buttons and mask.inv()
            }
        }
        if (event.repeatCount == 0) {
            // Worded without "key": the live log drops lines containing it (secret filter).
            logPad(
                "retro P${pad.port + 1} code ${event.keyCode} scan 0x${event.scanCode.toString(16)} ${if (pressed) "down" else "up"} " +
                    "(${if (sonyRaw) "raw HID" else "Android layout"}) -> ${buttonNames(pad.buttons)} l2=${pad.l2} r2=${pad.r2}",
            )
        }
        emit(pad)
        return true
    }

    private fun logPad(message: String) {
        runCatching { Log.i("GPSxPad", message) }
    }

    fun motion(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_MOVE || event.source and InputDevice.SOURCE_JOYSTICK == 0) return false
        val pad = padFor(event.deviceId) ?: return true
        pad.leftX = event.axis(MotionEvent.AXIS_X).stick()
        pad.leftY = event.axis(MotionEvent.AXIS_Y).stick()
        pad.rightX = event.axis(MotionEvent.AXIS_Z).stick()
        pad.rightY = event.axis(MotionEvent.AXIS_RZ).stick()
        pad.l2 = maxOf(event.axis(MotionEvent.AXIS_LTRIGGER), event.axis(MotionEvent.AXIS_BRAKE)) > TRIGGER_THRESHOLD
        pad.r2 = maxOf(event.axis(MotionEvent.AXIS_RTRIGGER), event.axis(MotionEvent.AXIS_GAS)) > TRIGGER_THRESHOLD
        var hat = 0
        val hatX = event.axis(MotionEvent.AXIS_HAT_X)
        val hatY = event.axis(MotionEvent.AXIS_HAT_Y)
        if (hatX < -0.5f) hat = hat or Ps5ButtonMask.DPAD_LEFT
        if (hatX > 0.5f) hat = hat or Ps5ButtonMask.DPAD_RIGHT
        if (hatY < -0.5f) hat = hat or Ps5ButtonMask.DPAD_UP
        if (hatY > 0.5f) hat = hat or Ps5ButtonMask.DPAD_DOWN
        pad.hat = hat
        emit(pad)
        return true
    }

    private fun padFor(deviceId: Int): Pad? =
        pads[deviceId] ?: run {
            val used = pads.values.map { it.port }.toSet()
            val port = (0 until ports).firstOrNull { it !in used } ?: return null
            Pad(port).also { pads[deviceId] = it }
        }

    private fun emit(pad: Pad) {
        val ps = pad.buttons or pad.hat or stickAsDpad(pad.leftX, pad.leftY)
        val anyChord = pads.values.any { it.buttons and QUIT_CHORD == QUIT_CHORD }
        if (anyChord && !chordHeld) {
            main.removeCallbacks(quitIfStillHeld)
            main.postDelayed(quitIfStillHeld, QUIT_CHORD_HOLD_MILLIS)
        } else if (!anyChord) {
            main.removeCallbacks(quitIfStillHeld)
        }
        chordHeld = anyChord
        val anyDiskChord = pads.values.any { it.buttons and DISK_CHORD == DISK_CHORD }
        if (anyDiskChord && !diskChordHeld) {
            main.removeCallbacks(diskIfStillHeld)
            main.postDelayed(diskIfStillHeld, QUIT_CHORD_HOLD_MILLIS)
        } else if (!anyDiskChord) {
            main.removeCallbacks(diskIfStillHeld)
        }
        diskChordHeld = anyDiskChord
        // While the chord is complete its buttons are released towards the game.
        val buttons =
            when {
                ps and QUIT_CHORD == QUIT_CHORD -> ps and QUIT_CHORD.inv()
                ps and DISK_CHORD == DISK_CHORD -> ps and DISK_CHORD.inv()
                else -> ps
            }
        sink(RetroPlayerInput(pad.port, RetroPad.fromPs5(buttons, pad.l2, pad.r2), pad.leftX, pad.leftY, pad.rightX, pad.rightY))
    }

    private fun isGamepad(event: KeyEvent): Boolean = KeyEvent.isGamepadButton(event.keyCode)

    private fun MotionEvent.axis(axis: Int): Float = getAxisValue(axis).coerceIn(-1f, 1f)

    private fun Float.stick(): Int = (this * Short.MAX_VALUE).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

    companion object {
        private const val SONY_VENDOR_ID = 0x054c
        internal const val DISK_CHORD = Ps5ButtonMask.L1 or Ps5ButtonMask.R1 or Ps5ButtonMask.CREATE
        private const val TRIGGER_THRESHOLD = 0.5f
        private const val STICK_DPAD_THRESHOLD = Short.MAX_VALUE / 2

        /** Left stick pushed past half way acts as the D-pad. */
        internal fun stickAsDpad(x: Int, y: Int): Int {
            var mask = 0
            if (x < -STICK_DPAD_THRESHOLD) mask = mask or Ps5ButtonMask.DPAD_LEFT
            if (x > STICK_DPAD_THRESHOLD) mask = mask or Ps5ButtonMask.DPAD_RIGHT
            if (y < -STICK_DPAD_THRESHOLD) mask = mask or Ps5ButtonMask.DPAD_UP
            if (y > STICK_DPAD_THRESHOLD) mask = mask or Ps5ButtonMask.DPAD_DOWN
            return mask
        }
    }
}
