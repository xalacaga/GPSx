package com.gpsx.tv.remoteplay

import com.gpsx.tv.ui.StreamQuality
import com.metallic.chiaki.lib.Codec
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoProfileTest {
    @Test
    fun autoUsesFullHdOnEmulatorAndDevice() {
        val emulator = videoProfileFor(StreamQuality.AUTO, isEmulator = true)
        val device = videoProfileFor(StreamQuality.AUTO, isEmulator = false)

        assertEquals(1920, emulator.width)
        assertEquals(1080, emulator.height)
        assertEquals(60, emulator.maxFPS)
        assertEquals(15_000, emulator.bitrate)
        assertEquals(Codec.H264, emulator.codec)
        assertEquals(1920, device.width)
        assertEquals(1080, device.height)
        assertEquals(15_000, device.bitrate)
    }

    @Test
    fun explicitQualityOverridesEmulatorAutoSelection() {
        assertEquals(1920, videoProfileFor(StreamQuality.FULL_HD_1080, true).width)
        assertEquals(1280, videoProfileFor(StreamQuality.HD_720, false).width)
    }
}
