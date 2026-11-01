package com.gpsx.tv.retro

import com.gpsx.tv.remoteplay.Ps5ButtonMask
import org.junit.Assert.assertEquals
import org.junit.Test

class RetroPlayTest {
    @Test
    fun `playstation buttons map to the retropad by position`() {
        assertEquals(RetroPad.B, RetroPad.fromPs5(Ps5ButtonMask.CROSS, l2 = false, r2 = false))
        assertEquals(RetroPad.A, RetroPad.fromPs5(Ps5ButtonMask.CIRCLE, l2 = false, r2 = false))
        assertEquals(RetroPad.Y, RetroPad.fromPs5(Ps5ButtonMask.SQUARE, l2 = false, r2 = false))
        assertEquals(RetroPad.X, RetroPad.fromPs5(Ps5ButtonMask.TRIANGLE, l2 = false, r2 = false))
        assertEquals(RetroPad.START or RetroPad.SELECT, RetroPad.fromPs5(Ps5ButtonMask.OPTIONS or Ps5ButtonMask.CREATE, l2 = false, r2 = false))
        assertEquals(RetroPad.L or RetroPad.R or RetroPad.L2 or RetroPad.R2, RetroPad.fromPs5(Ps5ButtonMask.L1 or Ps5ButtonMask.R1, l2 = true, r2 = true))
        assertEquals(
            RetroPad.UP or RetroPad.DOWN or RetroPad.LEFT or RetroPad.RIGHT,
            RetroPad.fromPs5(Ps5ButtonMask.DPAD_UP or Ps5ButtonMask.DPAD_DOWN or Ps5ButtonMask.DPAD_LEFT or Ps5ButtonMask.DPAD_RIGHT, l2 = false, r2 = false),
        )
        // The PlayStation and touchpad buttons have no RetroPad equivalent.
        assertEquals(0, RetroPad.fromPs5(Ps5ButtonMask.PLAYSTATION or Ps5ButtonMask.TOUCHPAD, l2 = false, r2 = false))
    }

    @Test
    fun `left stick past half way acts as the d-pad`() {
        assertEquals(0, RetroPadMapper.stickAsDpad(10_000, -10_000))
        assertEquals(Ps5ButtonMask.DPAD_RIGHT or Ps5ButtonMask.DPAD_UP, RetroPadMapper.stickAsDpad(30_000, -30_000))
        assertEquals(Ps5ButtonMask.DPAD_LEFT or Ps5ButtonMask.DPAD_DOWN, RetroPadMapper.stickAsDpad(-30_000, 30_000))
    }

    @Test
    fun `cores come from the official build server for the running abi`() {
        assertEquals("armeabi-v7a", CoreInstaller.abiFor("/data/app/~~x/com.gpsx.tv-1/lib/arm"))
        assertEquals("arm64-v8a", CoreInstaller.abiFor("/data/app/com.gpsx.tv-1/lib/arm64/"))
        assertEquals("x86_64", CoreInstaller.abiFor("/data/app/com.gpsx.tv-1/lib/x86_64"))
        assertEquals(
            "https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/snes9x2010_libretro_android.so.zip",
            CoreInstaller.coreUrl("armeabi-v7a", "snes9x2010"),
        )
    }
}
