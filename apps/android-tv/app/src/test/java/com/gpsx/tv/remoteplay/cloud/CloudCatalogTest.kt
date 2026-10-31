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
