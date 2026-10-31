package com.gpsx.tv.remoteplay

import com.gpsx.tv.ui.StreamQuality
import com.metallic.chiaki.lib.Codec
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoProfileTest {
    @Test
    fun autoUsesFullHdAndPrefersHardwareHevc() {
        val h264 = videoProfileFor(StreamQuality.AUTO, hevc = false)
        val hevc = videoProfileFor(StreamQuality.AUTO, hevc = true)

        assertEquals(1920, h264.width)
        assertEquals(1080, h264.height)
        assertEquals(60, h264.maxFPS)
        assertEquals(15_000, h264.bitrate)
        assertEquals(Codec.H264, h264.codec)
        assertEquals(Codec.H265, hevc.codec)
        assertEquals(1920, hevc.width)
        assertEquals(15_000, hevc.bitrate)
    }

    @Test
    fun explicitQualityStaysOnH264() {
        assertEquals(Codec.H264, videoProfileFor(StreamQuality.FULL_HD_1080, hevc = true).codec)
        assertEquals(1920, videoProfileFor(StreamQuality.FULL_HD_1080, true).width)
        assertEquals(1280, videoProfileFor(StreamQuality.HD_720, false).width)
    }
}
