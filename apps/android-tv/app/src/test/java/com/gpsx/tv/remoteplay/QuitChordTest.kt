// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.remoteplay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuitChordTest {
    @Test
    fun `chord completes only with L1 R1 and Options together`() {
        val chord = QuitChord()
        assertFalse(chord.update(1, Ps5ButtonMask.L1 or Ps5ButtonMask.R1))
        assertFalse(chord.update(1, Ps5ButtonMask.OPTIONS))
        assertFalse(chord.held())
        assertTrue(chord.update(1, QUIT_CHORD or Ps5ButtonMask.CROSS))
        assertTrue(chord.held())
        assertTrue(chord.isHeld(1))
    }

    @Test
    fun `completion is reported once and releasing a button cancels it`() {
        val chord = QuitChord()
        assertTrue(chord.update(1, QUIT_CHORD))
        assertFalse(chord.update(1, QUIT_CHORD))
        assertFalse(chord.update(1, Ps5ButtonMask.L1 or Ps5ButtonMask.R1))
        assertFalse(chord.held())
    }

    @Test
    fun `pads are tracked separately`() {
        val chord = QuitChord()
        assertTrue(chord.update(1, QUIT_CHORD))
        assertFalse(chord.update(2, Ps5ButtonMask.L1))
        assertTrue(chord.isHeld(1))
        assertFalse(chord.isHeld(2))
        chord.reset()
        assertFalse(chord.held())
    }
}
