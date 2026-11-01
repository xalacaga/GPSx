// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.remoteplay

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Locks the pad mapping shared by PS5 Remote Play and cloud streams (both send the same
 * controller state). The mask bit order must stay the gpsx-input `Button` order
 * (crates/gpsx-input/src/lib.rs), which GpsxInputBridge maps name by name to the engine's bits.
 */
class ControllerMappingTest {
    private val sony = 0x054c

    @Test
    fun `mask bits follow the gpsx-input button order`() {
        val order =
            listOf(
                Ps5ButtonMask.CROSS, Ps5ButtonMask.CIRCLE, Ps5ButtonMask.SQUARE, Ps5ButtonMask.TRIANGLE,
                Ps5ButtonMask.DPAD_UP, Ps5ButtonMask.DPAD_DOWN, Ps5ButtonMask.DPAD_LEFT, Ps5ButtonMask.DPAD_RIGHT,
                Ps5ButtonMask.L1, Ps5ButtonMask.R1, Ps5ButtonMask.L3, Ps5ButtonMask.R3,
                Ps5ButtonMask.CREATE, Ps5ButtonMask.OPTIONS, Ps5ButtonMask.PLAYSTATION, Ps5ButtonMask.TOUCHPAD,
            )
        order.forEachIndexed { bit, mask -> assertEquals("bit $bit", 1 shl bit, mask) }
    }

    @Test
    fun `android standard gamepad keys land on the matching PlayStation buttons`() {
        val expected =
            mapOf(
                KeyEvent.KEYCODE_BUTTON_A to Ps5ButtonMask.CROSS,
                KeyEvent.KEYCODE_BUTTON_B to Ps5ButtonMask.CIRCLE,
                KeyEvent.KEYCODE_BUTTON_X to Ps5ButtonMask.SQUARE,
                KeyEvent.KEYCODE_BUTTON_Y to Ps5ButtonMask.TRIANGLE,
                KeyEvent.KEYCODE_DPAD_UP to Ps5ButtonMask.DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN to Ps5ButtonMask.DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT to Ps5ButtonMask.DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT to Ps5ButtonMask.DPAD_RIGHT,
                KeyEvent.KEYCODE_BUTTON_L1 to Ps5ButtonMask.L1,
                KeyEvent.KEYCODE_BUTTON_R1 to Ps5ButtonMask.R1,
                KeyEvent.KEYCODE_BUTTON_THUMBL to Ps5ButtonMask.L3,
                KeyEvent.KEYCODE_BUTTON_THUMBR to Ps5ButtonMask.R3,
                KeyEvent.KEYCODE_BUTTON_SELECT to Ps5ButtonMask.CREATE,
                KeyEvent.KEYCODE_BUTTON_START to Ps5ButtonMask.OPTIONS,
                KeyEvent.KEYCODE_BUTTON_MODE to Ps5ButtonMask.PLAYSTATION,
            )
        expected.forEach { (key, mask) -> assertEquals("keycode $key", mask, androidKeyToPs5Button(sony, key)) }
        assertEquals(Ps5ButtonMask.TOUCHPAD, androidKeyToPs5Button(sony, KeyEvent.KEYCODE_BUTTON_1))
        assertNull(androidKeyToPs5Button(0x045e, KeyEvent.KEYCODE_BUTTON_1))
    }

    @Test
    fun `logged button names read in PlayStation terms`() {
        assertEquals("none", buttonNames(0))
        assertEquals("CROSS+L1", buttonNames(Ps5ButtonMask.CROSS or Ps5ButtonMask.L1))
        assertEquals("CREATE+OPTIONS+PS+TOUCHPAD", buttonNames(Ps5ButtonMask.CREATE or Ps5ButtonMask.OPTIONS or Ps5ButtonMask.PLAYSTATION or Ps5ButtonMask.TOUCHPAD))
    }

    @Test
    fun `raw HID DualSense buttons follow the physical layout`() {
        val expected =
            mapOf(
                0x130 to Ps5ButtonMask.SQUARE, 0x131 to Ps5ButtonMask.CROSS, 0x132 to Ps5ButtonMask.CIRCLE, 0x133 to Ps5ButtonMask.TRIANGLE,
                0x134 to Ps5ButtonMask.L1, 0x135 to Ps5ButtonMask.R1, 0x136 to SONY_HID_TRIGGER_KEY, 0x137 to SONY_HID_TRIGGER_KEY,
                0x138 to Ps5ButtonMask.CREATE, 0x139 to Ps5ButtonMask.OPTIONS, 0x13a to Ps5ButtonMask.L3, 0x13b to Ps5ButtonMask.R3,
                0x13c to Ps5ButtonMask.PLAYSTATION, 0x13d to Ps5ButtonMask.TOUCHPAD,
            )
        expected.forEach { (scan, mask) -> assertEquals("scan ${scan.toString(16)}", mask, sonyHidScanCodeToPs5Button(scan)) }
        assertNull(sonyHidScanCodeToPs5Button(0x13e))
    }
}
