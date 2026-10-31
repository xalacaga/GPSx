// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.remoteplay.cloud

import org.json.JSONObject

enum class CloudCategory { OWNED, STREAMABLE, PURCHASEABLE }

/** One row of the unified Pylux catalog contract (schema v8). Routing values are used verbatim. */
data class CloudGame(
    val productId: String,
    val name: String,
    val platform: String,
    val category: CloudCategory,
    val streamServiceType: String,
    val streamIdentifier: String,
    val entitlementId: String,
    val isOwned: Boolean,
    val plusCatalog: Boolean,
) {
    /** Only streamable routes can be provisioned; purchaseable rows need a store action first. */
    val playable: Boolean get() = category != CloudCategory.PURCHASEABLE && streamIdentifier.isNotEmpty()

    /**
     * Pylux's owned fast path: only owned PS Now rows carry a pre-resolved streaming entitlement.
     * Sending it for other rows (owned PS5 games streamed through pscloud) makes allocation fail.
     */
    val usesOwnedFastPath: Boolean get() = isOwned && streamServiceType == "psnow" && entitlementId.isNotEmpty()
}

data class CloudCatalog(
    val games: List<CloudGame>,
    val settledLocale: String,
    val fallbackRegion: String,
    val resolvedStoreLang: String,
    val nativeMode: Boolean,
    val warning: String,
)

/** Store country/language handed to provisioning, following Pylux's native/fallback split. */
data class CloudStoreRegion(val country: String, val language: String, val catalogIsForeign: Boolean)

internal const val MAX_CATALOG_BYTES = 16 * 1024 * 1024
internal const val MAX_CATALOG_GAMES = 20_000
private const val MAX_FIELD_LENGTH = 512
private val SERVICE_TYPES = setOf("psnow", "pscloud")

/** Parses the native catalog payload; returns null for malformed or oversized input. */
fun parseCloudCatalog(json: String): CloudCatalog? {
    if (json.length > MAX_CATALOG_BYTES) return null
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
    val rows = root.optJSONArray("games")
    val count = rows?.length() ?: 0
    if (count > MAX_CATALOG_GAMES) return null
    val games = ArrayList<CloudGame>(count)
    for (index in 0 until count) {
        rows?.optJSONObject(index)?.toCloudGame()?.let(games::add)
    }
    return CloudCatalog(
        games = games,
        settledLocale = root.boundedString("settledLocale"),
        fallbackRegion = root.boundedString("fallbackRegion"),
        resolvedStoreLang = root.boundedString("resolvedStoreLang"),
        nativeMode = root.optBoolean("nativeMode", true),
        warning = root.boundedString("warning"),
    )
}

fun storeRegionFor(locale: String, fallbackRegion: String, resolvedStoreLang: String, nativeMode: Boolean): CloudStoreRegion {
    val parts = locale.split("-", limit = 2)
    val localeLanguage = parts.getOrNull(0)?.lowercase()?.takeIf(String::isNotEmpty) ?: "en"
    val localeCountry = parts.getOrNull(1)?.uppercase()?.takeIf(String::isNotEmpty) ?: "US"
    return if (fallbackRegion.isNotEmpty()) {
        CloudStoreRegion(fallbackRegion, resolvedStoreLang.ifEmpty { localeLanguage }, !nativeMode)
    } else {
        CloudStoreRegion(localeCountry, localeLanguage, !nativeMode)
    }
}

private fun JSONObject.toCloudGame(): CloudGame? {
    val productId = boundedString("productId")
    val name = boundedString("name")
    if (productId.isEmpty() || name.isEmpty()) return null
    val category =
        when (boundedString("category")) {
            "owned" -> CloudCategory.OWNED
            "streamable" -> CloudCategory.STREAMABLE
            "purchaseable" -> CloudCategory.PURCHASEABLE
            else -> return null
        }
    val streamServiceType = boundedString("streamServiceType").takeIf(SERVICE_TYPES::contains) ?: return null
    return CloudGame(
        productId = productId,
        name = name,
        platform = boundedString("platform"),
        category = category,
        streamServiceType = streamServiceType,
        streamIdentifier = boundedString("streamIdentifier"),
        entitlementId = boundedString("entitlementId"),
        isOwned = optBoolean("isOwned", false),
        plusCatalog = optBoolean("plusCatalog", false),
    )
}

private fun JSONObject.boundedString(name: String): String =
    optString(name, "").takeIf { it.length <= MAX_FIELD_LENGTH }.orEmpty()

/** French explanation for the native provisioning error sentinels; other messages pass through, bounded. */
fun provisioningFailureLabel(message: String): String =
    when {
        "AUTHORIZATION_FAILED" in message -> "session PlayStation expirée, reconnectez votre compte."
        "PS_PLUS_SUBSCRIPTION_REQUIRED" in message -> "abonnement PlayStation Plus Premium requis pour le jeu en cloud."
        message.startsWith("GAME_NOT_FREE") -> "ce jeu n’est plus inclus dans PlayStation Plus (catalogue à actualiser)."
        message.startsWith("ACCOUNT_PRIVACY_SETTINGS") -> "paramètres de confidentialité du compte PlayStation à mettre à jour."
        "PING_TIMEOUT" in message -> "latence vers les serveurs Sony trop élevée (moins de 80 ms requis)."
        else -> message.take(200)
    }
