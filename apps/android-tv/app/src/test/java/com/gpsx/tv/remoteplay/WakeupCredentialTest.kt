// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.remoteplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakeupCredentialTest {
    private fun key(text: String, size: Int = 16): ByteArray =
        ByteArray(size).also { text.toByteArray(Charsets.US_ASCII).copyInto(it) }

    @Test
    fun `hex registration key text becomes the wakeup credential`() {
        assertEquals(0x1a2b3c4dL, wakeupCredential(key("1a2b3c4d")))
        assertEquals(0xffffffffL, wakeupCredential(key("FFFFFFFF")))
        assertEquals(0x7L, wakeupCredential(key("7", size = 1)))
    }

    @Test
    fun `invalid registration keys are rejected`() {
        assertNull(wakeupCredential(ByteArray(16)))
        assertNull(wakeupCredential(key("123456789")))
        assertNull(wakeupCredential(key("zz12")))
        assertNull(wakeupCredential(ByteArray(0)))
    }
}
