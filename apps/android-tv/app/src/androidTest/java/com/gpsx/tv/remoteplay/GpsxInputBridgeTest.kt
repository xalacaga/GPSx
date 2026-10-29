// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.remoteplay

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.metallic.chiaki.lib.ControllerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GpsxInputBridgeTest {
    @Test
    fun invalidDiscoveryWindowFailsBeforeNetworkIo() {
        assertEquals(DiscoveryResult.Failed, GpsxDiscoveryBridge().discoverPs5(0))
    }

    @Test
    fun portableInputStateIsMergedAndMappedToChiakiOrder() {
        val bridge = GpsxInputBridge()
        val everyButton = (1 shl 16) - 1
        val normalized =
            bridge.normalize(
                deviceId = 7,
                buttons = everyButton,
                l2 = 255,
                r2 = 0,
                leftX = -12_345,
                leftY = 23_456,
                rightX = 1,
                rightY = -1,
                eventTimeNs = 42,
            )

        assertNotNull(normalized)
        requireNotNull(normalized)
        val expectedButtons =
            ControllerState.CROSS or ControllerState.CIRCLE or
                ControllerState.SQUARE or ControllerState.TRIANGLE or
                ControllerState.DPAD_UP or ControllerState.DPAD_DOWN or
                ControllerState.DPAD_LEFT or ControllerState.DPAD_RIGHT or
                ControllerState.L1 or ControllerState.R1 or
                ControllerState.L3 or ControllerState.R3 or
                ControllerState.SHARE or ControllerState.OPTIONS or
                ControllerState.PS or ControllerState.TOUCHPAD
        assertEquals(expectedButtons, normalized.buttons)
        assertEquals(255U.toUByte(), normalized.l2State)
        assertEquals((-12_345).toShort(), normalized.leftX)
        assertEquals(23_456.toShort(), normalized.leftY)
    }
}
