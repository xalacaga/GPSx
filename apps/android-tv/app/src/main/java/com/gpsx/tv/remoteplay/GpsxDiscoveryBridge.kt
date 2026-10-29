// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.remoteplay

import android.os.Build

/** Bounded JNI call into the portable PS5 SRCH broadcast transport. */
internal class GpsxDiscoveryBridge {
    fun discoverPs5(timeoutMillis: Int = 1_200): DiscoveryResult {
        val usesHostBridge = isEmulator()
        val packed =
            if (usesHostBridge) {
                nativeDiscoverPs5ViaEmulatorHost(timeoutMillis)
            } else {
                nativeDiscoverPs5(timeoutMillis)
            }
        if (packed == -2L) return DiscoveryResult.HostBridgeUnavailable
        if (packed < 0) return DiscoveryResult.Failed
        if (packed == 0L) return DiscoveryResult.NotFound
        val address =
            listOf(24, 16, 8, 0).joinToString(".") { shift ->
                ((packed ushr shift) and 0xff).toString()
            }
        return DiscoveryResult.Found(address)
    }

    private external fun nativeDiscoverPs5(timeoutMillis: Int): Long
    private external fun nativeDiscoverPs5ViaEmulatorHost(timeoutMillis: Int): Long

    private fun isEmulator(): Boolean =
        shouldUseEmulatorHostBridge(Build.HARDWARE, Build.PRODUCT, Build.MODEL)

    companion object {
        init {
            System.loadLibrary("gpsx_android_input")
        }
    }
}

internal fun shouldUseEmulatorHostBridge(hardware: String, product: String, model: String): Boolean =
    hardware.contains("ranchu", ignoreCase = true) ||
        hardware.contains("goldfish", ignoreCase = true) ||
        product.startsWith("sdk_", ignoreCase = true) ||
        product.equals("sdk", ignoreCase = true) ||
        model.contains("emulator", ignoreCase = true)

internal sealed interface DiscoveryResult {
    data class Found(val address: String) : DiscoveryResult
    data object NotFound : DiscoveryResult
    data object HostBridgeUnavailable : DiscoveryResult
    data object Failed : DiscoveryResult
}
