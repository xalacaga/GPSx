// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.remoteplay.cloud

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.gpsx.tv.remoteplay.RemotePlayEngine
import com.metallic.chiaki.lib.Codec
import com.metallic.chiaki.lib.CloudProvisionCallbacks
import com.metallic.chiaki.lib.CloudProvisionRequest
import com.metallic.chiaki.lib.ConnectInfo
import com.metallic.chiaki.lib.ConnectVideoProfile
import com.metallic.chiaki.lib.ErrorCode
import com.metallic.chiaki.lib.cloudCatalogFetchUnified
import com.metallic.chiaki.lib.cloudCatalogInvalidateCache
import com.metallic.chiaki.lib.cloudProvisionSession
import com.metallic.chiaki.lib.initNativeSsl
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

enum class CloudResolution(val lines: Int, val width: Int, val label: String) {
    HD_720(720, 1280, "720p"),
    FULL_HD_1080(1080, 1920, "1080p"),
}

data class CloudSnapshot(
    val signedIn: Boolean = false,
    val loading: Boolean = false,
    val provisioning: Boolean = false,
    val detail: String = "Connectez votre compte PlayStation pour voir le catalogue cloud.",
    val warning: String = "",
    val games: List<CloudGame> = emptyList(),
    val resolution: CloudResolution = CloudResolution.HD_720,
    val bitrateKbps: Int = DEFAULT_BITRATE_KBPS,
)

internal const val DEFAULT_BITRATE_KBPS = 20_000

/**
 * PlayStation Plus cloud gaming: PSN sign-in state, unified catalog and server
 * provisioning through the pinned Pylux engine. The NPSSO token stays in
 * [PsnTokenStore] and is only handed to native code; it is never logged.
 */
class CloudPlayController(context: Context, private val engine: RemotePlayEngine) : AutoCloseable {
    private val appContext = context.applicationContext
    private val tokenStore = PsnTokenStore(appContext)
    private val regionPreferences = appContext.getSharedPreferences(REGION_PREFERENCES, Context.MODE_PRIVATE)
    private val catalogDir = File(appContext.cacheDir, "cloud-catalog")
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "gpsx-cloud").apply { isDaemon = true }
    }
    private val lock = Any()
    private val cancelProvisioning = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private var sslReady = false
    private var listener: ((CloudSnapshot) -> Unit)? = null
    private var snapshot = CloudSnapshot()

    init {
        worker.execute {
            if (tokenStore.load() != null) {
                update { copy(signedIn = true, detail = "Compte PlayStation connecté.") }
                refreshCatalogOnWorker(forceRefresh = false)
            }
        }
    }

    fun snapshot(): CloudSnapshot = synchronized(lock) { snapshot }

    fun setListener(value: ((CloudSnapshot) -> Unit)?) {
        val current = synchronized(lock) {
            listener = value
            snapshot
        }
        value?.invoke(current)
    }

    /** Stores a token captured from Sony's sign-in page or typed by the user. */
    fun signIn(rawToken: String): Boolean {
        val npsso = normalizeNpsso(rawToken)
        if (npsso == null) {
            update { copy(detail = "Jeton NPSSO invalide : il doit contenir 64 caractères environ.") }
            return false
        }
        worker.execute {
            if (!tokenStore.save(npsso)) {
                update { copy(detail = "Impossible d’enregistrer le jeton dans le stockage chiffré.") }
                return@execute
            }
            runCatching { cloudCatalogInvalidateCache(catalogDir.absolutePath) }
            update { copy(signedIn = true, detail = "Compte PlayStation connecté.") }
            refreshCatalogOnWorker(forceRefresh = true)
        }
        return true
    }

    fun signOut() {
        worker.execute {
            tokenStore.clear()
            regionPreferences.edit().clear().commit()
            runCatching { cloudCatalogInvalidateCache(catalogDir.absolutePath) }
            update { CloudSnapshot(resolution = resolution, bitrateKbps = bitrateKbps) }
        }
    }

    fun refreshCatalog() {
        worker.execute { refreshCatalogOnWorker(forceRefresh = true) }
    }

    fun cycleResolution() {
        update {
            copy(resolution = if (resolution == CloudResolution.HD_720) CloudResolution.FULL_HD_1080 else CloudResolution.HD_720)
        }
    }

    fun play(game: CloudGame) {
        if (!game.playable || snapshot().provisioning) return
        cancelProvisioning.set(false)
        update { copy(provisioning = true, detail = "Réservation d’un serveur pour ${game.name}…") }
        worker.execute {
            val npsso = tokenStore.load()
            if (npsso == null) {
                update { copy(provisioning = false, signedIn = false, detail = "Reconnectez votre compte PlayStation.") }
                return@execute
            }
            ensureSsl()
            val current = snapshot()
            val region =
                storeRegionFor(
                    regionPreferences.getString(KEY_LOCALE, null) ?: defaultLocale(),
                    regionPreferences.getString(KEY_FALLBACK_REGION, "").orEmpty(),
                    regionPreferences.getString(KEY_STORE_LANG, "").orEmpty(),
                    regionPreferences.getBoolean(KEY_NATIVE_MODE, true),
                )
            val result =
                runCatching {
                    cloudProvisionSession(
                        CloudProvisionRequest(
                            serviceType = game.streamServiceType,
                            gameIdentifier = game.streamIdentifier,
                            gameName = game.name,
                            npsso = npsso,
                            storeCountry = region.country,
                            storeLang = region.language,
                            gameLanguage = region.language,
                            ownedEntitlementId = if (game.isOwned) game.entitlementId else "",
                            ownedPlatform = if (game.isOwned) game.platform else "",
                            priorDatacentersJson = regionPreferences.getString(datacenterKey(game), "").orEmpty(),
                            catalogIsForeign = region.catalogIsForeign,
                            resolution = current.resolution.lines,
                            bitrateKbps = current.bitrateKbps,
                        ),
                        object : CloudProvisionCallbacks {
                            override fun onProgress(stage: String) {
                                update { copy(detail = "${game.name} : ${stage.take(120)}") }
                            }

                            override fun isCancelled(): Boolean = cancelProvisioning.get() || closed.get()
                        },
                    )
                }.getOrNull()
            if (result == null || result.err != 0 || result.serverIp.isEmpty()) {
                val reason =
                    result?.errorMessage?.takeIf(String::isNotBlank)?.take(200)
                        ?: result?.let { ErrorCode(it.err).toString() }
                        ?: "le moteur natif a échoué"
                update { copy(provisioning = false, detail = "Échec de la réservation : $reason") }
                return@execute
            }
            if (result.datacenterPings.isNotEmpty()) {
                regionPreferences.edit().putString(datacenterKey(game), result.datacenterPings.take(MAX_PREFERENCE_LENGTH)).apply()
            }
            update { copy(provisioning = false, detail = "Serveur réservé (${result.rttMs} ms). Démarrage du stream…") }
            engine.startCloud(
                ConnectInfo(
                    ps5 = result.platform == "ps5",
                    host = result.serverIp,
                    registKey = ByteArray(0x10),
                    morning = ByteArray(0x10),
                    videoProfile =
                        ConnectVideoProfile(
                            current.resolution.width,
                            current.resolution.lines,
                            60,
                            current.bitrateKbps,
                            if (game.streamServiceType == "pscloud") Codec.H265 else Codec.H264,
                        ),
                    serviceType = game.streamServiceType,
                    cloudLaunchSpec = result.launchSpec,
                    cloudHandshakeKey = result.handshakeKey,
                    cloudSessionId = result.sessionId,
                    cloudPort = result.serverPort,
                    cloudPsnWrapperType = result.psnWrapperType,
                    cloudMtuIn = result.mtuIn,
                    cloudMtuOut = result.mtuOut,
                    cloudRttUs = result.rttMs.toLong() * 1000L,
                ),
                game.name,
            )
        }
    }

    fun cancelProvisioning() {
        cancelProvisioning.set(true)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        cancelProvisioning.set(true)
        synchronized(lock) { listener = null }
        worker.shutdown()
    }

    private fun refreshCatalogOnWorker(forceRefresh: Boolean) {
        val npsso = tokenStore.load()
        if (npsso == null) {
            update { copy(signedIn = false, loading = false) }
            return
        }
        update { copy(loading = true, detail = "Chargement du catalogue cloud…") }
        ensureSsl()
        catalogDir.mkdirs()
        val error = arrayOfNulls<String>(1)
        val json =
            runCatching {
                cloudCatalogFetchUnified(
                    npsso,
                    regionPreferences.getString(KEY_LOCALE, null) ?: defaultLocale(),
                    catalogDir.absolutePath,
                    forceRefresh,
                    error,
                )
            }.getOrNull()
        val catalog = json?.let(::parseCloudCatalog)
        if (catalog == null) {
            val reason = error[0]?.takeIf(String::isNotBlank)?.take(200) ?: "réponse invalide"
            update { copy(loading = false, detail = "Catalogue indisponible : $reason") }
            return
        }
        regionPreferences.edit()
            .apply { if (catalog.settledLocale.isNotEmpty()) putString(KEY_LOCALE, catalog.settledLocale) }
            .putString(KEY_FALLBACK_REGION, catalog.fallbackRegion)
            .putString(KEY_STORE_LANG, catalog.resolvedStoreLang)
            .putBoolean(KEY_NATIVE_MODE, catalog.nativeMode)
            .apply()
        val playable = catalog.games.count(CloudGame::playable)
        update {
            copy(
                loading = false,
                games = catalog.games,
                warning = catalog.warning.take(300),
                detail = "$playable jeux jouables dans le catalogue cloud.",
            )
        }
    }

    private fun ensureSsl() {
        if (sslReady) return
        sslReady = runCatching { initNativeSsl(appContext.cacheDir.absolutePath) }.isSuccess
    }

    private fun update(transform: CloudSnapshot.() -> CloudSnapshot) {
        val (next, callback) = synchronized(lock) {
            snapshot = snapshot.transform()
            snapshot to listener
        }
        if (callback != null) main.post { callback(next) }
    }

    private fun datacenterKey(game: CloudGame) = "$KEY_DATACENTERS_PREFIX${game.streamServiceType}"

    private fun defaultLocale(): String = Locale.getDefault().toLanguageTag().ifBlank { "en-US" }

    private companion object {
        const val REGION_PREFERENCES = "gpsx_cloud_region"
        const val KEY_LOCALE = "settled_locale"
        const val KEY_FALLBACK_REGION = "fallback_region"
        const val KEY_STORE_LANG = "resolved_store_lang"
        const val KEY_NATIVE_MODE = "native_mode"
        const val KEY_DATACENTERS_PREFIX = "datacenters_"
        const val MAX_PREFERENCE_LENGTH = 64 * 1024
    }
}
