// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.remoteplay

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameControllerKindTest {
    @Test
    fun knownBluetoothControllerVendorsAreClassified() {
        assertEquals(GameControllerKind.DUALSENSE, GameControllerKind.fromVendorId(0x054c))
        assertEquals(GameControllerKind.XBOX, GameControllerKind.fromVendorId(0x045e))
        assertEquals(GameControllerKind.GENERIC, GameControllerKind.fromVendorId(0x1234))
    }

    @Test
    fun emulatorBridgeSelectionNeverCapturesProductionHardware() {
        assertEquals(true, shouldUseEmulatorHostBridge("ranchu", "sdk_google_atv64", "Google TV"))
        assertEquals(true, shouldUseEmulatorHostBridge("goldfish", "aosp_x86_64", "Android SDK emulator"))
        assertEquals(false, shouldUseEmulatorHostBridge("amlogic", "gp520", "Google TV Streamer"))
        assertEquals(false, shouldUseEmulatorHostBridge("qcom", "bravia", "Sony TV"))
    }

    @Test
    fun dualSenseStandardButtonsMapToPs5Semantics() {
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
                KeyEvent.KEYCODE_BUTTON_1 to Ps5ButtonMask.TOUCHPAD,
            )

        expected.forEach { (keyCode, mask) ->
            assertEquals(mask, androidKeyToPs5Button(0x054c, keyCode))
        }
        assertEquals(expected.size, expected.values.toSet().size)
    }

    @Test
    fun sonyRawHidScanCodesMapToPhysicalPs5Buttons() {
        val expected =
            mapOf(
                0x130 to Ps5ButtonMask.SQUARE,
                0x131 to Ps5ButtonMask.CROSS,
                0x132 to Ps5ButtonMask.CIRCLE,
                0x133 to Ps5ButtonMask.TRIANGLE,
                0x134 to Ps5ButtonMask.L1,
                0x135 to Ps5ButtonMask.R1,
                0x138 to Ps5ButtonMask.CREATE,
                0x139 to Ps5ButtonMask.OPTIONS,
                0x13a to Ps5ButtonMask.L3,
                0x13b to Ps5ButtonMask.R3,
                0x13c to Ps5ButtonMask.PLAYSTATION,
                0x13d to Ps5ButtonMask.TOUCHPAD,
            )

        expected.forEach { (scanCode, mask) ->
            assertEquals(mask, sonyHidScanCodeToPs5Button(scanCode))
        }
        assertEquals(expected.size, expected.values.toSet().size)
        // L2/R2 buttons are consumed; the analog Rx/Ry axes carry the trigger value.
        assertEquals(SONY_HID_TRIGGER_KEY, sonyHidScanCodeToPs5Button(0x136))
        assertEquals(SONY_HID_TRIGGER_KEY, sonyHidScanCodeToPs5Button(0x137))
        assertNull(sonyHidScanCodeToPs5Button(0x13e))
    }

    @Test
    fun xboxUsesPs5LayoutWithoutClaimingSonyTouchpad() {
        assertEquals(Ps5ButtonMask.CROSS, androidKeyToPs5Button(0x045e, KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(Ps5ButtonMask.CIRCLE, androidKeyToPs5Button(0x045e, KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(Ps5ButtonMask.SQUARE, androidKeyToPs5Button(0x045e, KeyEvent.KEYCODE_BUTTON_X))
        assertEquals(Ps5ButtonMask.TRIANGLE, androidKeyToPs5Button(0x045e, KeyEvent.KEYCODE_BUTTON_Y))
        assertEquals(Ps5ButtonMask.CREATE, androidKeyToPs5Button(0x045e, KeyEvent.KEYCODE_BUTTON_SELECT))
        assertEquals(Ps5ButtonMask.OPTIONS, androidKeyToPs5Button(0x045e, KeyEvent.KEYCODE_BUTTON_START))
        assertEquals(Ps5ButtonMask.PLAYSTATION, androidKeyToPs5Button(0x045e, KeyEvent.KEYCODE_BUTTON_MODE))
        assertNull(androidKeyToPs5Button(0x045e, KeyEvent.KEYCODE_BUTTON_1))
    }
}
