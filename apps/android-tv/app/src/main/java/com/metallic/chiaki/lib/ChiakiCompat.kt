// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
// Derived from chiaki-ng commit c8bea2642d0e95402f373af55584a3b98cd89417 and
// Pylux commit ca87a49a12bab071948f565ca53ffdad790775d3 (cloud and PSN fields).

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

// Every property below is read by name from native code; keep names and JVM types.
data class ConnectInfo(
    val ps5: Boolean,
    val host: String,
    val registKey: ByteArray,
    val morning: ByteArray,
    val videoProfile: ConnectVideoProfile,
    val serviceType: String? = null,
    val cloudLaunchSpec: String? = null,
    val cloudHandshakeKey: String? = null,
    val cloudSessionId: String? = null,
    val cloudPort: Int = 0,
    val cloudPsnWrapperType: Int = 0,
    val cloudMtuIn: Int = 0,
    val cloudMtuOut: Int = 0,
    val cloudRttUs: Long = 0L,
    val duid: String? = null,
    val psnAccountId: String? = null,
    val holepunchSessionPtr: Long = 0L,
    val autoRegist: Boolean = false,
)

/** Live stream measurements reported by the native session. */
data class StreamMetrics(
    val bitrateMbps: Double,
    val packetLoss: Double,
    val framesLost: Long,
    val fps: Double,
    val rttMs: Double,
    val width: Int,
    val height: Int,
    /** 0 not opened yet, 1 AAudio, 2 failed, 3 OpenSL ES (engines without audio stats report 0). */
    val audioState: Int = 0,
    val audioUnderflows: Long = 0,
    val audioOverflows: Long = 0,
    val audioReopens: Long = 0,
    /** Audio callbacks so far; stays at 0 when the output never pulls audio. */
    val audioCallbacks: Long = 0,
    /** Android output device ID the audio stream reports (0 unknown or system default). */
    val audioDeviceId: Int = 0,
    /** Video samples dropped because no decoder existed yet (no Surface). */
    val videoSamplesWithoutDecoder: Long = 0,
    val videoSamplesQueued: Long = 0,
    /** Decoded frames sent to the Surface; stays at 0 when nothing is displayed. */
    val videoFramesRendered: Long = 0,
    /** Smoothed time from queueing a sample to its decoded frame (ms); 0 before the first frame. */
    val videoDecodeLatencyMs: Double = 0.0,
    /** Decoded frames skipped because a newer one was already ready (catching up after a stall). */
    val videoFramesDroppedLate: Long = 0,
    /** Codec actually decoded, read from the stream: 0 none yet, 1 H.264, 2 H.265. */
    val videoDecodedCodec: Int = 0,
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

    /** Reopens the audio stream on the current output route (after a Bluetooth device connects or leaves). */
    fun restartAudio() = ChiakiNative.sessionRestartAudio(nativePtr)

    fun metrics(): StreamMetrics? {
        val values = ChiakiNative.sessionGetMetrics(nativePtr)?.takeIf { it.size >= 7 } ?: return null
        fun at(index: Int): Double = values.getOrElse(index) { 0.0 }
        return StreamMetrics(
            values[0], values[1], values[2].toLong(), values[3], values[4], values[5].toInt(), values[6].toInt(),
            at(7).toInt(), at(8).toLong(), at(9).toLong(), at(10).toLong(), at(11).toLong(), at(12).toInt(),
            at(13).toLong(), at(14).toLong(), at(15).toLong(), at(16), at(17).toLong(), at(18).toInt(),
        )
    }

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

    // PSN auto-registration, holepunch and PS-chord callbacks are resolved by the
    // native session; GPSx does not use those flows yet.
    @Suppress("unused", "UNUSED_PARAMETER")
    private fun eventRegist(host: RegistHost) = Unit

    @Suppress("unused")
    private fun eventHolepunch() = Unit

    @Suppress("unused")
    private fun eventPsChord() = Unit
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

/** Progress and cancellation hooks invoked on the provisioning thread. */
interface CloudProvisionCallbacks {
    fun onProgress(stage: String)
    fun isCancelled(): Boolean
}

/** Result of [cloudProvisionSession]; [err] is 0 when a cloud server is allocated. */
data class CloudProvisionResult(
    val err: Int,
    val serverIp: String,
    val serverPort: Int,
    val handshakeKey: String,
    val launchSpec: String,
    val sessionId: String,
    val entitlementId: String,
    val platform: String,
    val psnWrapperType: Int,
    val mtuIn: Int,
    val mtuOut: Int,
    val rttMs: Int,
    val datacenterPings: String,
    val errorMessage: String,
)

data class CloudProvisionRequest(
    val serviceType: String,
    val gameIdentifier: String,
    val gameName: String,
    val npsso: String,
    val storeCountry: String = "",
    val storeLang: String = "",
    val gameLanguage: String = "",
    val ownedEntitlementId: String = "",
    val ownedPlatform: String = "",
    val forcedDatacenter: String = "",
    val priorDatacentersJson: String = "",
    val catalogIsForeign: Boolean = false,
    val resolution: Int = 0,
    val bitrateKbps: Int = 0,
)

/** Sends one discovery WAKEUP datagram to [host] using a temporary native socket. */
fun discoveryWakeup(host: String, userCredential: Long, ps5: Boolean): ErrorCode =
    ErrorCode(ChiakiNative.discoveryServiceWakeup(0L, host, userCredential, ps5))

/** Audio API for sessions created afterwards: false = automatic (AAudio, then OpenSL ES), true = OpenSL ES only. */
fun setAudioCompatibilityMode(enabled: Boolean) = ChiakiNative.audioSetApiPreference(if (enabled) 1 else 0)

/** Whether decoders created afterwards try the low-latency MediaCodec hints first. */
fun setDecoderLowLatencyHints(enabled: Boolean) = ChiakiNative.videoSetLowLatencyHints(enabled)

/** Android output device ID for AAudio streams opened afterwards; 0 leaves routing to the system. */
fun setPreferredAudioDevice(deviceId: Int) = ChiakiNative.audioSetPreferredDevice(deviceId)

/** Installs the CA bundle used by the native HTTPS client. Call once before cloud requests. */
fun initNativeSsl(cacheDir: String) = ChiakiNative.initNativeSsl(cacheDir)

/** Blocking; returns the unified catalog JSON, or null with the failure detail in [errorOut]. */
fun cloudCatalogFetchUnified(npsso: String?, locale: String?, cacheDir: String, forceRefresh: Boolean, errorOut: Array<String?>): String? =
    ChiakiNative.cloudCatalogFetchUnified(npsso, locale, cacheDir, forceRefresh, errorOut)?.toString(Charsets.UTF_8)

fun cloudCatalogInvalidateCache(cacheDir: String) = ChiakiNative.cloudCatalogInvalidateCache(cacheDir)

/** Blocking Kamaji + Gaikai provisioning; call off the main thread. */
fun cloudProvisionSession(request: CloudProvisionRequest, callbacks: CloudProvisionCallbacks?): CloudProvisionResult {
    val strings = arrayOfNulls<String>(8)
    val ints = IntArray(5)
    val err =
        with(request) {
            ChiakiNative.cloudProvisionSession(
                serviceType, gameIdentifier, gameName, npsso, storeCountry, storeLang, gameLanguage,
                ownedEntitlementId, ownedPlatform, forcedDatacenter, priorDatacentersJson,
                catalogIsForeign, resolution, bitrateKbps, callbacks, strings, ints,
            )
        }
    return CloudProvisionResult(
        err = err,
        serverIp = strings[0].orEmpty(),
        serverPort = ints[0],
        handshakeKey = strings[1].orEmpty(),
        launchSpec = strings[2].orEmpty(),
        sessionId = strings[3].orEmpty(),
        entitlementId = strings[4].orEmpty(),
        platform = strings[5].orEmpty(),
        psnWrapperType = ints[1],
        mtuIn = ints[2],
        mtuOut = ints[3],
        rttMs = ints[4],
        datacenterPings = strings[6].orEmpty(),
        errorMessage = strings[7].orEmpty(),
    )
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
        @JvmStatic external fun sessionGetMetrics(ptr: Long): DoubleArray?
        @JvmStatic external fun audioSetApiPreference(preference: Int)
        @JvmStatic external fun audioSetPreferredDevice(deviceId: Int)
        @JvmStatic external fun videoSetLowLatencyHints(enabled: Boolean)
        @JvmStatic external fun sessionRestartAudio(ptr: Long)
        @JvmStatic external fun registStart(result: CreateResult, info: RegistInfo, log: ChiakiLog, regist: Regist)
        @JvmStatic external fun registStop(ptr: Long)
        @JvmStatic external fun registFree(ptr: Long)
        @JvmStatic external fun discoveryServiceWakeup(ptr: Long, host: String, userCredential: Long, ps5: Boolean): Int
        @JvmStatic external fun initNativeSsl(cacheDir: String)
        @JvmStatic external fun cloudCatalogFetchUnified(npsso: String?, locale: String?, cacheDir: String, forceRefresh: Boolean, errorOut: Array<String?>): ByteArray?
        @JvmStatic external fun cloudCatalogInvalidateCache(cacheDir: String)
        @JvmStatic external fun cloudProvisionSession(
            serviceType: String, gameIdentifier: String, gameName: String, npsso: String,
            storeCountry: String, storeLang: String, gameLanguage: String,
            ownedEntitlementId: String, ownedPlatform: String, forcedDatacenter: String,
            priorDatacentersJson: String, catalogIsForeign: Boolean, resolution: Int, bitrateKbps: Int,
            callbacks: CloudProvisionCallbacks?, stringOut: Array<String?>, intOut: IntArray,
        ): Int
    }
}
