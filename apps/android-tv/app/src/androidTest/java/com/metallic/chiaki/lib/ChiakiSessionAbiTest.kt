// SPDX-License-Identifier: AGPL-3.0-only

package com.metallic.chiaki.lib

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChiakiSessionAbiTest {
    @Test
    fun nativeSessionCanResolveEveryKotlinCallback() {
        Session(
            ConnectInfo(
                ps5 = true,
                host = "127.0.0.1",
                registKey = ByteArray(16),
                morning = ByteArray(16),
                videoProfile = ConnectVideoProfile(1280, 720, 60, 10_000, Codec.H264),
            ),
        ).close()
    }
}
