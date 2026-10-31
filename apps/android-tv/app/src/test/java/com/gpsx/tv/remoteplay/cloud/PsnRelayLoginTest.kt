// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.remoteplay.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PsnRelayLoginTest {
    @Test
    fun `codes use the unambiguous six character alphabet and vary`() {
        val codes = (1..200).map { PsnRelayLogin.newCode() }
        codes.forEach { assertTrue(it, Regex("[A-HJ-NP-Z2-9]{6}").matches(it)) }
        assertTrue(codes.toSet().size > 190)
    }

    @Test
    fun `login url carries only the code`() {
        val login = PsnRelayLogin("https://relay.example")
        assertEquals("https://relay.example/psstream/?psstream_code=${login.code}", login.loginUrl)
    }
}
