// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.remoteplay

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.metallic.chiaki.lib.ErrorCode
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChiakiPackagingTest {
    @Test
    fun pinnedNativeEngineLoadsAndResolvesJni() {
        assertTrue(ErrorCode(0).toString().isNotBlank())
    }
}
