// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
// Derived from chiaki-ng commit c8bea2642d0e95402f373af55584a3b98cd89417.

@file:OptIn(ExperimentalUnsignedTypes::class)

package com.metallic.chiaki.lib

import android.view.Surface

enum class Target(val value: Int) {
    PS4_UNKNOWN(0),
    PS4_8(800),
    PS4_9(900),
    PS4_10(1000),
    PS5_UNKNOWN(1_000_000),
    PS5_1(1_000_100),
    ;

    companion object {
        @JvmStatic
        fun fromValue(value: Int): Target = entries.firstOrNull { it.value == value } ?: PS4_10
    }
}

enum class Codec(val value: Int) {
    H264(0),
    H265(1),
    H265_HDR(2),
}

data class ConnectVideoProfile(
    val width: Int,
    val height: Int,
    val maxFPS: Int,
    val bitrate: Int,
    val codec: Codec,
)

data class ConnectInfo(
    val ps5: Boolean,
    val host: String,
    val registKey: ByteArray,
    val morning: ByteArray,
    val videoProfile: ConnectVideoProfile,
)

class ErrorCode(val value: Int) {
    val isSuccess: Boolean = value == 0
    override fun toString(): String = ChiakiNative.errorCodeToString(value)
}

class QuitReason(val value: Int) {
    val isError: Boolean = ChiakiNative.quitReasonIsError(value)
    override fun toString(): String = ChiakiNative.quitReasonToString(value)
}

sealed interface SessionEvent
data object ConnectedEvent : SessionEvent
data class LoginPinRequestEvent(val pinIncorrect: Boolean) : SessionEvent
data class QuitEvent(val reason: QuitReason, val reasonString: String?) : SessionEvent
data class RumbleEvent(val left: UByte, val right: UByte) : SessionEvent

class CreateError(val errorCode: ErrorCode) : Exception("Native chiaki object creation failed: $errorCode")

data class ControllerTouch(
    var x: UShort = 0U,
    var y: UShort = 0U,
    var id: Byte = -1,
)

data class ControllerState(
    var buttons: UInt = 0U,
    var l2State: UByte = 0U,
    var r2State: UByte = 0U,
    var leftX: Short = 0,
    var leftY: Short = 0,
    var rightX: Short = 0,
    var rightY: Short = 0,
    var touches: Array<ControllerTouch> = arrayOf(ControllerTouch(), ControllerTouch()),
    var gyroX: Float = 0f,
    var gyroY: Float = 0f,
    var gyroZ: Float = 0f,
    var accelX: Float = 0f,
    var accelY: Float = 1f,
    var accelZ: Float = 0f,
    var orientX: Float = 0f,
    var orientY: Float = 0f,
    var orientZ: Float = 0f,
    var orientW: Float = 1f,
) {
    companion object {
        val CROSS: UInt = 1U shl 0
        val CIRCLE: UInt = 1U shl 1
        val SQUARE: UInt = 1U shl 2
        val TRIANGLE: UInt = 1U shl 3
        val DPAD_LEFT: UInt = 1U shl 4
        val DPAD_RIGHT: UInt = 1U shl 5
        val DPAD_UP: UInt = 1U shl 6
        val DPAD_DOWN: UInt = 1U shl 7
        val L1: UInt = 1U shl 8
        val R1: UInt = 1U shl 9
        val L3: UInt = 1U shl 10
        val R3: UInt = 1U shl 11
        val OPTIONS: UInt = 1U shl 12
        val SHARE: UInt = 1U shl 13
        val TOUCHPAD: UInt = 1U shl 14
        val PS: UInt = 1U shl 15
    }

    fun detachedCopy(): ControllerState = copy(touches = touches.map(ControllerTouch::copy).toTypedArray())
}

class Session(connectInfo: ConnectInfo) : AutoCloseable {
    private var nativePtr: Long
    var eventCallback: ((SessionEvent) -> Unit)? = null

    init {
        val result = ChiakiNative.CreateResult(0, 0)
        ChiakiNative.sessionCreate(result, connectInfo, null, false, this)
        val error = ErrorCode(result.errorCode)
        if (!error.isSuccess) throw CreateError(error)
        nativePtr = result.ptr
    }

    fun start(): ErrorCode = ErrorCode(ChiakiNative.sessionStart(nativePtr))
    fun stop(): ErrorCode = ErrorCode(ChiakiNative.sessionStop(nativePtr))
    fun setSurface(surface: Surface?) = ChiakiNative.sessionSetSurface(nativePtr, surface)
    fun setControllerState(state: ControllerState) = ChiakiNative.sessionSetControllerState(nativePtr, state)
    fun setLoginPin(pin: String) = ChiakiNative.sessionSetLoginPin(nativePtr, pin)

    override fun close() {
        val owned = nativePtr
        if (owned == 0L) return
        ChiakiNative.sessionJoin(owned)
        ChiakiNative.sessionFree(owned)
        nativePtr = 0L
    }

    @Suppress("unused")
    private fun eventConnected() {
        eventCallback?.invoke(ConnectedEvent)
    }

    @Suppress("unused")
    private fun eventLoginPinRequest(pinIncorrect: Boolean) {
        eventCallback?.invoke(LoginPinRequestEvent(pinIncorrect))
    }

    @Suppress("unused")
    private fun eventQuit(reasonValue: Int, reasonString: String?) {
        eventCallback?.invoke(QuitEvent(QuitReason(reasonValue), reasonString))
    }

    @Suppress("unused")
    private fun eventRumble(left: Int, right: Int) {
        eventCallback?.invoke(RumbleEvent(left.toUByte(), right.toUByte()))
    }
}

class ChiakiLog(
    val levelMask: Int,
    private val callback: (level: Int, text: String) -> Unit,
) {
    @Suppress("unused")
    private fun log(level: Int, text: String) = callback(level, text)

    companion object {
        const val SAFE_LEVELS: Int = (1 shl 0) or (1 shl 1) or (1 shl 2)
    }
}

data class RegistInfo(
    val target: Target,
    val host: String,
    val broadcast: Boolean,
    val psnOnlineId: String?,
    val psnAccountId: ByteArray?,
    val pin: Int,
)

data class RegistHost(
    val target: Target,
    val apSsid: String,
    val apBssid: String,
    val apKey: String,
    val apName: String,
    val serverMac: ByteArray,
    val serverNickname: String,
    val rpRegistKey: ByteArray,
    val rpKeyType: UInt,
    val rpKey: ByteArray,
)

sealed interface RegistEvent
data object RegistEventCanceled : RegistEvent
data object RegistEventFailed : RegistEvent
data class RegistEventSuccess(val host: RegistHost) : RegistEvent

class Regist(info: RegistInfo, log: ChiakiLog, private val callback: (RegistEvent) -> Unit) : AutoCloseable {
    private var nativePtr: Long

    init {
        val result = ChiakiNative.CreateResult(0, 0)
        ChiakiNative.registStart(result, info, log, this)
        val error = ErrorCode(result.errorCode)
        if (!error.isSuccess) throw CreateError(error)
        nativePtr = result.ptr
    }

    fun stop() {
        if (nativePtr != 0L) ChiakiNative.registStop(nativePtr)
    }

    override fun close() {
        val owned = nativePtr
        if (owned == 0L) return
        ChiakiNative.registFree(owned)
        nativePtr = 0L
    }

    @Suppress("unused")
    private fun event(event: RegistEvent) = callback(event)
}

private class ChiakiNative {
    data class CreateResult(var errorCode: Int, var ptr: Long)

    companion object {
        init {
            System.loadLibrary("chiaki-jni")
        }

        @JvmStatic external fun errorCodeToString(value: Int): String
        @JvmStatic external fun quitReasonToString(value: Int): String
        @JvmStatic external fun quitReasonIsError(value: Int): Boolean
        @JvmStatic external fun sessionCreate(result: CreateResult, info: ConnectInfo, logFile: String?, verbose: Boolean, session: Session)
        @JvmStatic external fun sessionFree(ptr: Long)
        @JvmStatic external fun sessionStart(ptr: Long): Int
        @JvmStatic external fun sessionStop(ptr: Long): Int
        @JvmStatic external fun sessionJoin(ptr: Long): Int
        @JvmStatic external fun sessionSetSurface(ptr: Long, surface: Surface?)
        @JvmStatic external fun sessionSetControllerState(ptr: Long, state: ControllerState)
        @JvmStatic external fun sessionSetLoginPin(ptr: Long, pin: String)
        @JvmStatic external fun registStart(result: CreateResult, info: RegistInfo, log: ChiakiLog, regist: Regist)
        @JvmStatic external fun registStop(ptr: Long)
        @JvmStatic external fun registFree(ptr: Long)
    }
}
