// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.remoteplay

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.Surface
import com.gpsx.tv.ui.StreamQuality
import com.metallic.chiaki.lib.ChiakiLog
import com.metallic.chiaki.lib.Codec
import com.metallic.chiaki.lib.ConnectInfo
import com.metallic.chiaki.lib.ConnectVideoProfile
import com.metallic.chiaki.lib.ConnectedEvent
import com.metallic.chiaki.lib.ControllerState
import com.metallic.chiaki.lib.LoginPinRequestEvent
import com.metallic.chiaki.lib.QuitEvent
import com.metallic.chiaki.lib.Regist
import com.metallic.chiaki.lib.RegistEvent
import com.metallic.chiaki.lib.RegistEventCanceled
import com.metallic.chiaki.lib.RegistEventFailed
import com.metallic.chiaki.lib.RegistEventSuccess
import com.metallic.chiaki.lib.RegistInfo
import com.metallic.chiaki.lib.Session
import com.metallic.chiaki.lib.StreamMetrics
import com.metallic.chiaki.lib.Target
import com.metallic.chiaki.lib.discoveryWakeup
import java.net.InetSocketAddress
import java.net.Socket
import com.gpsx.tv.diagnostics.StreamTestRecorder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class RemotePlayPhase {
    NEEDS_REGISTRATION,
    REGISTERING,
    READY,
    CONNECTING,
    STREAMING,
    LOGIN_PIN_REQUIRED,
    WAKING,
    STOPPING,
    STOPPED,
    FAILED,
}

data class RemotePlaySnapshot(
    val phase: RemotePlayPhase = RemotePlayPhase.NEEDS_REGISTRATION,
    val detail: String = "Enregistrez cette application sur la PS5.",
    val consoleName: String? = null,
    val discoveryRunning: Boolean = false,
    val discoveredHost: String? = null,
    val discoveryDetail: String = "Recherche LAN non lancée.",
    /** Title shown while a cloud game streams; null for console Remote Play. */
    val cloudTitle: String? = null,
)

/**
 * Coarse Android facade around the pinned chiaki-ng engine.
 *
 * Compressed video and PCM stay inside native code. Registration material is
 * encrypted with an Android Keystore key in app-private storage and is never logged.
 */
class RemotePlayEngine(context: Context? = null) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private val applicationContext = context?.applicationContext
    private val consoleStore = applicationContext?.let(::RegisteredConsoleStore)
    private val discoveryBridge = GpsxDiscoveryBridge()
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "gpsx-remote-play").apply { isDaemon = true }
    }
    private val closed = AtomicBoolean(false)
    private val lock = Any()
    private var listener: ((RemotePlaySnapshot) -> Unit)? = null
    private var snapshot = RemotePlaySnapshot()
    private var registeredConsole: RegisteredConsole? = null
    private var registration: Regist? = null
    private var session: Session? = null
    private var surface: Surface? = null

    /** Per-session test reports, uploaded on request from the Diagnostics screen. */
    val testRecorder: StreamTestRecorder? = applicationContext?.let(::StreamTestRecorder)
    private val sampler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "gpsx-test-sampler").apply { isDaemon = true }
    }
    private var sampling: ScheduledFuture<*>? = null
    @Volatile private var endReason = "Arrêt demandé par l’utilisateur."

    // The emulator decode path is not representative, so AUTO keeps H.264 there.
    // Held only while a session exists: Wi-Fi power save otherwise adds tens of ms of jitter.
    private val streamWifiLock: WifiManager.WifiLock? by lazy {
        runCatching {
            val mode =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
            applicationContext?.getSystemService(WifiManager::class.java)
                ?.createWifiLock(mode, "gpsx-stream")
                ?.apply { setReferenceCounted(false) }
        }.getOrNull()
    }

    private val autoHevc by lazy {
        !shouldUseEmulatorHostBridge(Build.HARDWARE, Build.PRODUCT, Build.MODEL) && hasHardwareHevc1080p60()
    }

    init {
        consoleStore?.load()?.let { restored ->
            registeredConsole = restored
            snapshot =
                snapshot.copy(
                    phase = RemotePlayPhase.READY,
                    detail = "Association PS5 restaurée depuis le stockage privé.",
                    consoleName = restored.name,
                    discoveredHost = restored.host,
                )
        }
    }

    fun snapshot(): RemotePlaySnapshot = synchronized(lock) { snapshot }

    fun setListener(value: ((RemotePlaySnapshot) -> Unit)?) {
        val current = synchronized(lock) {
            listener = value
            snapshot
        }
        value?.invoke(current)
    }

    fun discoverPs5() {
        if (closed.get() || snapshot().discoveryRunning) return
        updateSnapshot {
            copy(
                discoveryRunning = true,
                discoveryDetail = "Recherche de la PS5 sur le réseau local…",
            )
        }
        worker.execute {
            val multicastLock =
                runCatching {
                    applicationContext
                        ?.getSystemService(WifiManager::class.java)
                        ?.createMulticastLock("gpsx-ps5-discovery")
                        ?.apply {
                            setReferenceCounted(false)
                            acquire()
                        }
                }.getOrNull()
            try {
                when (val result = discoveryBridge.discoverPs5()) {
                    is DiscoveryResult.Found ->
                        updateSnapshot {
                            copy(
                                discoveryRunning = false,
                                discoveredHost = result.address,
                                discoveryDetail = "PS5 détectée sur le LAN : ${result.address}",
                            )
                        }
                    DiscoveryResult.NotFound ->
                        updateSnapshot {
                            copy(
                                discoveryRunning = false,
                                discoveredHost = null,
                                discoveryDetail = "Aucune PS5 détectée. Vérifiez le même réseau et Remote Play.",
                            )
                        }
                    DiscoveryResult.HostBridgeUnavailable ->
                        updateSnapshot {
                            copy(
                                discoveryRunning = false,
                                discoveredHost = null,
                                discoveryDetail = "Bridge AVD indisponible sur l’hôte Windows.",
                            )
                        }
                    DiscoveryResult.Failed ->
                        updateSnapshot {
                            copy(
                                discoveryRunning = false,
                                discoveredHost = null,
                                discoveryDetail = "Échec borné de la découverte LAN.",
                            )
                        }
                }
            } finally {
                runCatching { if (multicastLock?.isHeld == true) multicastLock.release() }
            }
        }
    }

    fun register(host: String, pinText: String, accountIdText: String) {
        if (closed.get()) return
        val normalizedHost = host.trim()
        val pin = pinText.trim().toIntOrNull()
        val accountId = decodeAccountId(accountIdText)
        if (normalizedHost.isEmpty() || pin == null || pinText.trim().length != 8 || accountId == null) {
            publish(RemotePlayPhase.FAILED, "Adresse, PIN PS5 à 8 chiffres ou Account ID invalide.")
            return
        }
        publish(RemotePlayPhase.REGISTERING, "Enregistrement local en cours…")
        worker.execute {
            try {
                registration?.close()
                val log = ChiakiLog(ChiakiLog.SAFE_LEVELS) { _, _ -> }
                registration =
                    Regist(
                        RegistInfo(Target.PS5_1, normalizedHost, false, null, accountId, pin),
                        log,
                    ) { event -> handleRegistrationEvent(normalizedHost, event) }
            } catch (_: Throwable) {
                publish(RemotePlayPhase.FAILED, "Le moteur natif n’a pas pu démarrer l’enregistrement.")
            }
        }
    }

    fun start(quality: StreamQuality) {
        if (closed.get()) return
        val console = synchronized(lock) { registeredConsole }
        if (console == null) {
            publish(RemotePlayPhase.NEEDS_REGISTRATION, "Enregistrez d’abord GPSx sur la PS5.")
            return
        }
        updateSnapshot { copy(cloudTitle = null) }
        publish(RemotePlayPhase.CONNECTING, "Connexion à ${console.name}…")
        worker.execute {
            if (!isSessionPortOpen(console.host, QUICK_PROBE_MILLIS) && !wakeAndWait(console)) return@execute
            publish(RemotePlayPhase.CONNECTING, "Connexion à ${console.name}…")
        }
        launchSession(
            ConnectInfo(
                ps5 = true,
                host = console.host,
                registKey = console.registKey,
                morning = console.morning,
                videoProfile =
                    videoProfileFor(quality, hevc = autoHevc),
            ),
            "Stream PS5 actif.",
        )
    }

    /** Sends a WAKEUP to the registered PS5 and waits until it accepts session requests. */
    fun wakeConsole() {
        if (closed.get()) return
        val console = synchronized(lock) { registeredConsole }
        if (console == null) {
            publish(RemotePlayPhase.NEEDS_REGISTRATION, "Enregistrez d’abord GPSx sur la PS5.")
            return
        }
        worker.execute {
            if (isSessionPortOpen(console.host, QUICK_PROBE_MILLIS)) {
                publish(RemotePlayPhase.READY, "${console.name} est déjà allumée.")
            } else if (wakeAndWait(console)) {
                publish(RemotePlayPhase.READY, "${console.name} est prête.")
            }
        }
    }

    /** Runs on [worker]. Returns true once the console accepts TCP on the session port. */
    private fun wakeAndWait(console: RegisteredConsole): Boolean {
        val credential = wakeupCredential(console.registKey)
        if (credential == null) {
            publish(RemotePlayPhase.FAILED, "Clé d’enregistrement inutilisable pour le réveil ; réassociez la PS5.")
            return false
        }
        val sent = runCatching { discoveryWakeup(console.host, credential, ps5 = true) }.getOrNull()
        if (sent?.isSuccess != true) {
            publish(RemotePlayPhase.FAILED, "Échec de l’envoi du signal de réveil.")
            return false
        }
        publish(RemotePlayPhase.WAKING, "Réveil de ${console.name}…")
        val deadline = System.nanoTime() + WAKE_TIMEOUT_MILLIS * 1_000_000L
        while (!closed.get() && System.nanoTime() < deadline) {
            if (isSessionPortOpen(console.host, PROBE_MILLIS)) return true
            Thread.sleep(PROBE_INTERVAL_MILLIS)
        }
        publish(RemotePlayPhase.FAILED, "${console.name} ne s’est pas réveillée. Vérifiez le mode repos et l’option « Rester connecté à Internet ».")
        return false
    }

    /** Starts a stream from an already provisioned cloud allocation. Called from any thread. */
    fun startCloud(connectInfo: ConnectInfo, title: String) {
        if (closed.get()) return
        updateSnapshot { copy(cloudTitle = title) }
        publish(RemotePlayPhase.CONNECTING, "Connexion au serveur cloud pour $title…")
        launchSession(connectInfo, "Jeu cloud actif : $title.")
    }

    private fun launchSession(connectInfo: ConnectInfo, streamingDetail: String) {
        worker.execute {
            if (snapshot().phase != RemotePlayPhase.CONNECTING) return@execute
            try {
                stopSession()
                runCatching { streamWifiLock?.acquire() }
                val newSession = Session(connectInfo)
                newSession.eventCallback = { event ->
                    when (event) {
                        ConnectedEvent -> {
                            publish(RemotePlayPhase.STREAMING, streamingDetail)
                            beginTestRecording(connectInfo)
                        }
                        is LoginPinRequestEvent -> publish(RemotePlayPhase.LOGIN_PIN_REQUIRED, "PIN utilisateur PS5 requis.")
                        is QuitEvent -> {
                            val label = event.reasonString?.takeIf(String::isNotBlank) ?: "Session terminée."
                            endReason = label
                            publish(if (event.reason.isError) RemotePlayPhase.FAILED else RemotePlayPhase.STOPPED, label)
                            // Join and free off the native callback thread; also releases the Wi-Fi lock.
                            if (!closed.get()) worker.execute { if (synchronized(lock) { session } === newSession) stopSession() }
                        }
                        else -> Unit
                    }
                }
                synchronized(lock) {
                    session = newSession
                    surface
                }?.let(newSession::setSurface)
                val result = newSession.start()
                if (!result.isSuccess) {
                    stopSession()
                    publish(RemotePlayPhase.FAILED, "Échec du démarrage de la session ($result).")
                }
            } catch (_: Throwable) {
                stopSession()
                publish(RemotePlayPhase.FAILED, "Le moteur natif n’a pas pu créer la session.")
            }
        }
    }

    fun stop() {
        if (closed.get()) return
        publish(RemotePlayPhase.STOPPING, "Arrêt ordonné de la session…")
        worker.execute {
            stopSession()
            publish(RemotePlayPhase.STOPPED, "Session arrêtée.")
        }
    }

    fun submitLoginPin(pin: String) {
        synchronized(lock) { session }?.setLoginPin(pin.filter(Char::isDigit))
    }

    fun setSurface(value: Surface?) {
        val active = synchronized(lock) {
            surface = value
            session
        }
        active?.setSurface(value)
    }

    fun submitControllerState(state: ControllerState) {
        if (snapshot().phase == RemotePlayPhase.STREAMING) {
            synchronized(lock) { session }?.setControllerState(state.detachedCopy())
        }
    }

    /** Latest native measurements, or null when no session is streaming. Cheap; safe to poll once per second. */
    fun metrics(): StreamMetrics? =
        // Read under the lock: stopSession() detaches the session under the same lock before freeing it.
        if (isStreaming()) synchronized(lock) { session?.let { runCatching(it::metrics).getOrNull() } } else null

    fun isStreaming(): Boolean = snapshot().phase == RemotePlayPhase.STREAMING

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        worker.execute {
            registration?.stop()
            registration?.close()
            registration = null
            stopSession()
            synchronized(lock) {
                registeredConsole = null
                listener = null
                surface = null
            }
        }
        worker.shutdown()
        sampler.shutdown()
    }

    private fun handleRegistrationEvent(host: String, event: RegistEvent) {
        when (event) {
            is RegistEventSuccess -> {
                val console =
                    RegisteredConsole(
                        host = host,
                        name = event.host.serverNickname.ifBlank { "PS5" },
                        registKey = event.host.rpRegistKey.copyOf(),
                        morning = event.host.rpKey.copyOf(),
                    )
                val persisted = consoleStore?.save(console) == true
                synchronized(lock) {
                    registeredConsole = console
                }
                publish(
                    RemotePlayPhase.READY,
                    if (persisted) {
                        "Console enregistrée dans le stockage privé chiffré."
                    } else {
                        "Console enregistrée pour cette session ; stockage persistant indisponible."
                    },
                    console.name,
                )
            }
            RegistEventCanceled -> publish(RemotePlayPhase.STOPPED, "Enregistrement annulé.")
            RegistEventFailed -> publish(RemotePlayPhase.FAILED, "La PS5 a refusé l’enregistrement.")
        }
        worker.execute {
            registration?.close()
            registration = null
        }
    }

    private fun stopSession() {
        val owned = synchronized(lock) {
            val current = session
            session = null
            current
        }
        if (owned != null) {
            runCatching { owned.stop() }
            runCatching { owned.close() }
        }
        runCatching { if (streamWifiLock?.isHeld == true) streamWifiLock?.release() }
        finishTestRecording()
    }

    private fun beginTestRecording(connectInfo: ConnectInfo) {
        val recorder = testRecorder ?: return
        endReason = "Arrêt demandé par l’utilisateur."
        recorder.begin(if (connectInfo.serviceType != null) "cloud" else "console", connectInfo.videoProfile, autoHevc)
        synchronized(lock) {
            sampling?.cancel(false)
            sampling = sampler.scheduleWithFixedDelay({ metrics()?.let(recorder::sample) }, 1, 1, TimeUnit.SECONDS)
        }
    }

    private fun finishTestRecording() {
        synchronized(lock) {
            sampling?.cancel(false)
            sampling = null
        }
        testRecorder?.finish(endReason)
    }

    private fun publish(phase: RemotePlayPhase, detail: String, consoleName: String? = snapshot().consoleName) {
        val next = synchronized(lock) {
            snapshot.copy(
                phase = phase,
                detail = detail,
                consoleName = consoleName?.ifBlank { "PS5" },
            )
        }
        publishSnapshot(next)
    }

    private fun updateSnapshot(transform: RemotePlaySnapshot.() -> RemotePlaySnapshot) {
        val next = synchronized(lock) { snapshot.transform() }
        publishSnapshot(next)
    }

    private fun publishSnapshot(next: RemotePlaySnapshot) {
        val callback = synchronized(lock) {
            snapshot = next
            listener
        }
        if (callback != null) main.post { callback(next) }
    }

    private fun decodeAccountId(text: String): ByteArray? {
        val compact = text.trim().replace(" ", "")
        val bytes =
            runCatching {
                if (compact.matches(Regex("[0-9a-fA-F]{16}"))) {
                    compact.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                } else {
                    Base64.decode(compact, Base64.DEFAULT)
                }
            }.getOrNull()
        return bytes?.takeIf { it.size == 8 }
    }

}

private const val SESSION_PORT = 9295
private const val QUICK_PROBE_MILLIS = 800
private const val PROBE_MILLIS = 1_000
private const val PROBE_INTERVAL_MILLIS = 1_500L
private const val WAKE_TIMEOUT_MILLIS = 60_000L

private fun isSessionPortOpen(host: String, timeoutMillis: Int): Boolean =
    runCatching { Socket().use { it.connect(InetSocketAddress(host, SESSION_PORT), timeoutMillis) } }.isSuccess

/**
 * The WAKEUP user credential is the registration key's ASCII hex text (up to
 * the first NUL, at most 8 characters) read as an unsigned 64-bit number.
 */
internal fun wakeupCredential(registKey: ByteArray): Long? {
    val end = registKey.indexOf(0.toByte()).let { if (it >= 0) it else registKey.size }
    if (end == 0 || end > 8) return null
    val text = String(registKey, 0, end, Charsets.US_ASCII)
    return text.toULongOrNull(16)?.toLong()
}

/** AUTO uses HEVC when [hevc] reports a hardware decoder; explicit choices stay on H.264. */
internal fun videoProfileFor(quality: StreamQuality, hevc: Boolean): ConnectVideoProfile =
    when (quality) {
        StreamQuality.HD_720 -> ConnectVideoProfile(1280, 720, 60, 10_000, Codec.H264)
        StreamQuality.AUTO -> ConnectVideoProfile(1920, 1080, 60, 15_000, if (hevc) Codec.H265 else Codec.H264)
        StreamQuality.FULL_HD_1080 -> ConnectVideoProfile(1920, 1080, 60, 15_000, Codec.H264)
    }

