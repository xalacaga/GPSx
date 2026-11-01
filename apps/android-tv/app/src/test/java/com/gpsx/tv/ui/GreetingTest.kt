package com.gpsx.tv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingTest {
    @Test
    fun `greeting follows the hour of the day`() {
        assertEquals("Bonsoir.", greetingFor(4))
        assertEquals("Bonjour.", greetingFor(5))
        assertEquals("Bonjour.", greetingFor(17))
        assertEquals("Bonsoir.", greetingFor(18))
        assertEquals("Bonsoir.", greetingFor(23))
    }
}
