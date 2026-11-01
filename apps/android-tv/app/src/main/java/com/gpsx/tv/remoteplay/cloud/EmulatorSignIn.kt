// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.remoteplay.cloud

import android.os.Build
import com.gpsx.tv.remoteplay.shouldUseEmulatorHostBridge

/** Fixed guest port on the emulator, so the PC relay can be set up before the page exists. */
const val EMULATOR_SIGN_IN_PORT = 47800

/** Emulator system property naming the PC's LAN address; set by tools/powershell/start-psn-signin-relay.ps1. */
private const val LAN_HOST_PROPERTY = "debug.gpsx.lan_host"

/**
 * Starts [server] for the current device. On the emulator the phone cannot reach the guest's
 * virtual network, so the page listens on [EMULATOR_SIGN_IN_PORT] and the QR code shows the
 * PC address that relays to it. Elsewhere the page uses a random port on the device's own address.
 */
fun startLocalSignIn(server: LocalTokenServer): String? {
    if (!shouldUseEmulatorHostBridge(Build.HARDWARE, Build.PRODUCT, Build.MODEL)) return server.start()
    val pcHost = readSystemProperty(LAN_HOST_PROPERTY)?.takeIf { Regex("\\d{1,3}(\\.\\d{1,3}){3}").matches(it) }
    return server.start(advertisedHost = pcHost ?: LocalTokenServer.lanAddress(), port = EMULATOR_SIGN_IN_PORT)
}

private fun readSystemProperty(name: String): String? =
    runCatching {
        val process = ProcessBuilder("getprop", name).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.readText() }.trim().also { process.waitFor() }
    }.getOrNull()?.takeIf(String::isNotEmpty)
