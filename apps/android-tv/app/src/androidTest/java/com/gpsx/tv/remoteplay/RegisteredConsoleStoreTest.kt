// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.remoteplay

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegisteredConsoleStoreTest {
    @Test
    fun registrationRoundTripsThroughAppPrivateKeystoreEncryption() {
        val suffix = UUID.randomUUID().toString()
        val preferencesName = "gpsx_registration_test_$suffix"
        val store =
            RegisteredConsoleStore(
                InstrumentationRegistry.getInstrumentation().targetContext,
                preferencesName = preferencesName,
                keyAlias = "gpsx_registration_test_key_$suffix",
            )
        val expected =
            RegisteredConsole(
                host = "192.0.2.50",
                name = "Test PS5",
                registKey = ByteArray(16) { (it + 1).toByte() },
                morning = ByteArray(16) { (it + 17).toByte() },
            )

        try {
            assertEquals(true, store.save(expected))
            val restored = requireNotNull(store.load())
            assertEquals(expected.host, restored.host)
            assertEquals(expected.name, restored.name)
            assertArrayEquals(expected.registKey, restored.registKey)
            assertArrayEquals(expected.morning, restored.morning)

            val preferences =
                InstrumentationRegistry.getInstrumentation().targetContext
                    .getSharedPreferences(preferencesName, 0)
            preferences.edit().putString("payload", "corrupted").commit()
            assertNull(store.load())
            assertEquals(true, preferences.all.isEmpty())
        } finally {
            store.clear()
        }
    }
}
