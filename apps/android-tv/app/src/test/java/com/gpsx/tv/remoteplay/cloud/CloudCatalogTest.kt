// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.remoteplay.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudCatalogTest {
    private val payload =
        """
        {
          "schemaVersion": 8, "total": 4, "nativeMode": false,
          "fallbackRegion": "FR", "resolvedStoreLang": "", "settledLocale": "fr-FR", "warning": "",
          "games": [
            {"productId": "EP0001", "name": "Owned Game", "category": "owned", "platform": "ps5",
             "isOwned": true, "streamServiceType": "pscloud", "streamIdentifier": "EP0001-ENT",
             "entitlementId": "EP0001-ENT", "plusCatalog": false},
            {"productId": "EP0002", "name": "Plus Game", "category": "streamable", "platform": "ps4",
             "isOwned": false, "streamServiceType": "psnow", "streamIdentifier": "EP0002", "plusCatalog": true},
            {"productId": "EP0003", "name": "Store Game", "category": "purchaseable", "platform": "ps5",
             "streamServiceType": "pscloud", "streamIdentifier": ""},
            {"productId": "", "name": "Broken row", "category": "owned", "streamServiceType": "pscloud"},
            {"productId": "EP0005", "name": "Unknown route", "category": "owned", "streamServiceType": "ftp"}
          ]
        }
        """.trimIndent()

    @Test
    fun `parses contract rows and drops malformed ones`() {
        val catalog = parseCloudCatalog(payload)!!

        assertEquals(listOf("EP0001", "EP0002", "EP0003"), catalog.games.map(CloudGame::productId))
        assertEquals("fr-FR", catalog.settledLocale)
        assertEquals("FR", catalog.fallbackRegion)
        assertFalse(catalog.nativeMode)
        val owned = catalog.games[0]
        assertEquals(CloudCategory.OWNED, owned.category)
        assertEquals("EP0001-ENT", owned.streamIdentifier)
        assertTrue(owned.playable)
        assertTrue(catalog.games[1].playable)
        assertFalse("purchaseable rows cannot be streamed", catalog.games[2].playable)
    }

    @Test
    fun `owned fast path is limited to owned ps now rows with an entitlement`() {
        val games = parseCloudCatalog(payload)!!.games
        // Owned PS5 game streamed through pscloud: Pylux never sends its entitlement.
        assertFalse(games.first { it.productId == "EP0001" }.usesOwnedFastPath)
        assertFalse(games.first { it.productId == "EP0002" }.usesOwnedFastPath)
        val ownedPsNow = games.first { it.productId == "EP0002" }.copy(isOwned = true, entitlementId = "EP0002-ENT")
        assertTrue(ownedPsNow.usesOwnedFastPath)
        assertFalse(ownedPsNow.copy(entitlementId = "").usesOwnedFastPath)
    }

    @Test
    fun `provisioning sentinels become readable french reasons`() {
        assertTrue(provisioningFailureLabel("PS_PLUS_SUBSCRIPTION_REQUIRED").contains("Premium"))
        assertTrue(provisioningFailureLabel("ERR PING_TIMEOUT best=120").contains("80 ms"))
        assertTrue(provisioningFailureLabel("GAME_NOT_FREE:9.99").contains("PlayStation Plus"))
        assertEquals("Allocation failed", provisioningFailureLabel("Allocation failed"))
        assertEquals(200, provisioningFailureLabel("x".repeat(500)).length)
    }

    @Test
    fun `art prefers landscape https images and ignores other schemes`() {
        fun art(fields: String) =
            parseCloudCatalog(
                """{"games":[{"productId":"P","name":"N","category":"streamable","streamServiceType":"psnow","streamIdentifier":"S"$fields}]}""",
            )!!.games.single().artUrl
        assertEquals("https://img/land.jpg", art(""","landscapeImageUrl":"https://img/land.jpg","imageUrl":"https://img/box.jpg""""))
        assertEquals("https://img/box.jpg", art(""","landscapeImageUrl":"http://img/land.jpg","imageUrl":"https://img/box.jpg""""))
        assertEquals("", art(""))
    }

    @Test
    fun `ps4 cloud decoded in software announces a lower bandwidth`() {
        assertEquals(PS4_SOFTWARE_BITRATE_KBPS, streamBitrateKbps("psnow", 20_000, softwarePs4Decoder = true))
        assertEquals(20_000, streamBitrateKbps("psnow", 20_000, softwarePs4Decoder = false))
        assertEquals(20_000, streamBitrateKbps("pscloud", 20_000, softwarePs4Decoder = true))
        assertEquals(5_000, streamBitrateKbps("psnow", 5_000, softwarePs4Decoder = true))
    }

    @Test
    fun `rejects invalid json and oversized payloads`() {
        assertNull(parseCloudCatalog("not json"))
        assertNull(parseCloudCatalog(" ".repeat(MAX_CATALOG_BYTES + 1)))
    }

    @Test
    fun `missing games array yields an empty catalog`() {
        assertEquals(emptyList<CloudGame>(), parseCloudCatalog("""{"nativeMode": true}""")!!.games)
    }

    @Test
    fun `store region follows native and fallback modes`() {
        assertEquals(CloudStoreRegion("FR", "fr", false), storeRegionFor("fr-FR", "", "", nativeMode = true))
        assertEquals(CloudStoreRegion("HU", "en", true), storeRegionFor("en-US", "HU", "", nativeMode = false))
        assertEquals(CloudStoreRegion("NL", "nl", true), storeRegionFor("en-US", "NL", "nl", nativeMode = false))
        assertEquals(CloudStoreRegion("US", "en", false), storeRegionFor("", "", "", nativeMode = true))
    }

    @Test
    fun `npsso accepts raw tokens and ssocookie json only`() {
        val token = "a".repeat(64)
        assertEquals(token, normalizeNpsso(token))
        assertEquals(token, normalizeNpsso("  npsso=$token "))
        assertEquals(token, normalizeNpsso("""{"npsso":"$token"}"""))
        assertNull(normalizeNpsso(""))
        assertNull(normalizeNpsso("short"))
        assertNull(normalizeNpsso("""{"error":"invalid_grant"}"""))
        assertNull(normalizeNpsso("https://example.com/?code=$token"))
    }
}
