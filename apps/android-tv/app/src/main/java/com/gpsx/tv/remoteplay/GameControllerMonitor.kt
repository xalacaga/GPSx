// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.remoteplay

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice

enum class GameControllerKind(val label: String) {
    DUALSENSE("DualSense / PlayStation"),
    XBOX("Xbox"),
    GENERIC("Manette Android"),
    ;

    companion object {
        internal fun fromVendorId(vendorId: Int): GameControllerKind =
            when (vendorId) {
                0x054c -> DUALSENSE
                0x045e -> XBOX
                else -> GENERIC
            }
    }
}

data class ConnectedGameController(
    val deviceId: Int,
    val name: String,
    val kind: GameControllerKind,
    val vendorId: Int,
    val productId: Int,
)

/** Observes Android input devices; Bluetooth pairing itself remains system-owned. */
class GameControllerMonitor(context: Context) : InputManager.InputDeviceListener, AutoCloseable {
    private val inputManager = context.getSystemService(InputManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var callback: ((List<ConnectedGameController>) -> Unit)? = null
    private var registered = false

    fun start(callback: (List<ConnectedGameController>) -> Unit) {
        this.callback = callback
        if (!registered) {
            inputManager.registerInputDeviceListener(this, main)
            registered = true
        }
        callback(scan())
    }

    override fun onInputDeviceAdded(deviceId: Int) = publish()
    override fun onInputDeviceRemoved(deviceId: Int) = publish()
    override fun onInputDeviceChanged(deviceId: Int) = publish()

    override fun close() {
        if (registered) inputManager.unregisterInputDeviceListener(this)
        registered = false
        callback = null
    }

    private fun publish() {
        callback?.invoke(scan())
    }

    private fun scan(): List<ConnectedGameController> =
        InputDevice.getDeviceIds()
            .asSequence()
            .mapNotNull(InputDevice::getDevice)
            .filter { device ->
                device.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                    device.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
            }
            .sortedBy(InputDevice::getId)
            .take(2)
            .map { device ->
                ConnectedGameController(
                    deviceId = device.id,
                    name = device.name.ifBlank { "Manette ${device.id}" },
                    kind = GameControllerKind.fromVendorId(device.vendorId),
                    vendorId = device.vendorId,
                    productId = device.productId,
                )
            }
            .toList()
}
