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
import com.metallic.chiaki.lib.Target
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

enum class RemotePlayPhase {
    NEEDS_REGISTRATION,
    REGISTERING,
    READY,
    CONNECTING,
    STREAMING,
    LOGIN_PIN_REQUIRED,
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
        publish(RemotePlayPhase.CONNECTING, "Connexion à ${console.name}…")
        worker.execute {
            try {
                stopSession()
                val newSession =
                    Session(
                        ConnectInfo(
                            ps5 = true,
                            host = console.host,
                            registKey = console.registKey,
                            morning = console.morning,
                            videoProfile =
                                videoProfileFor(
                                    quality,
                                    shouldUseEmulatorHostBridge(Build.HARDWARE, Build.PRODUCT, Build.MODEL),
                                ),
                        ),
                    )
                newSession.eventCallback = { event ->
                    when (event) {
                        ConnectedEvent -> publish(RemotePlayPhase.STREAMING, "Stream PS5 actif.")
                        is LoginPinRequestEvent -> publish(RemotePlayPhase.LOGIN_PIN_REQUIRED, "PIN utilisateur PS5 requis.")
                        is QuitEvent -> {
                            val label = event.reasonString?.takeIf(String::isNotBlank) ?: "Session terminée."
                            publish(if (event.reason.isError) RemotePlayPhase.FAILED else RemotePlayPhase.STOPPED, label)
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

@Suppress("UNUSED_PARAMETER")
internal fun videoProfileFor(quality: StreamQuality, isEmulator: Boolean): ConnectVideoProfile =
    when (quality) {
        StreamQuality.HD_720 -> ConnectVideoProfile(1280, 720, 60, 10_000, Codec.H264)
        StreamQuality.AUTO -> ConnectVideoProfile(1920, 1080, 60, 15_000, Codec.H264)
        StreamQuality.FULL_HD_1080 -> ConnectVideoProfile(1920, 1080, 60, 15_000, Codec.H264)
    }

