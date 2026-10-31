// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.remoteplay.cloud

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONTokener

/**
 * Sony sign-in inside the app. The page talks only to Sony; once signed in, the
 * NPSSO cookie is read from `ca.account.sony.com`, handed to [onToken], and all
 * WebView cookies are wiped so the token lives only in [PsnTokenStore].
 */
@SuppressLint("SetJavaScriptEnabled")
internal class PsnLoginView(context: Context, private val onToken: (String) -> Unit) : WebView(context) {
    private var delivered = false
    private var sawSignIn = false
    private var tokenPageAttempts = 0

    init {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        CookieManager.getInstance().setAcceptCookie(true)
        webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (request.url.host != REDIRECT_HOST) return false
                    // Sign-in finished. The redirect carries an OAuth code GPSx does not need: drop it
                    // without loading it, then read the NPSSO session token from Sony.
                    tokenFromCookies()?.let(::deliver) ?: loadUrl(SSO_COOKIE_URL)
                    return true
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (delivered) return
                    tokenFromCookies()?.let(::deliver) ?: run {
                        val uri = Uri.parse(url)
                        val host = uri.host.orEmpty()
                        val path = uri.path.orEmpty()
                        when {
                            host == SONY_ACCOUNT_HOST && path == SSO_COOKIE_PATH -> readTokenFromPage()
                            path.contains("signin") || path.contains("authorize") -> sawSignIn = true
                            // Signed in and redirected away from the sign-in form: ask Sony for the token.
                            sawSignIn && host.endsWith(SONY_DOMAIN) && tokenPageAttempts < MAX_TOKEN_PAGE_ATTEMPTS -> {
                                tokenPageAttempts++
                                loadUrl(SSO_COOKIE_URL)
                            }
                        }
                    }
                }
            }
    }

    fun start() {
        delivered = false
        sawSignIn = false
        tokenPageAttempts = 0
        loadUrl(SIGN_IN_URL)
    }

    private fun tokenFromCookies(): String? =
        CookieManager.getInstance().getCookie(SONY_ACCOUNT_ORIGIN)
            ?.split(';')
            ?.map(String::trim)
            ?.firstOrNull { it.startsWith("npsso=") }
            ?.let(::normalizeNpsso)

    private fun readTokenFromPage() {
        evaluateJavascript("document.body ? document.body.innerText : ''") { encoded ->
            val text = runCatching { JSONTokener(encoded).nextValue() as? String }.getOrNull().orEmpty()
            normalizeNpsso(text)?.let(::deliver) ?: run {
                // Not signed in yet: go back to the Sony sign-in form.
                sawSignIn = false
                loadUrl(SIGN_IN_URL)
            }
        }
    }

    private fun deliver(token: String) {
        if (delivered) return
        delivered = true
        CookieManager.getInstance().removeAllCookies(null)
        clearHistory()
        loadUrl("about:blank")
        onToken(token)
    }

    private companion object {
        // Sony's Remote Play sign-in, as used by chiaki-ng; the public client ID is not a secret.
        const val SIGN_IN_URL =
            "https://auth.api.sonyentertainmentnetwork.com/2.0/oauth/authorize?service_entity=urn:service-entity:psn" +
                "&response_type=code&client_id=ba495a24-818c-472b-b12d-ff231c1b5745" +
                "&redirect_uri=https://remoteplay.dl.playstation.net/remoteplay/redirect&scope=psn:clientapp" +
                "&request_locale=fr_FR&ui=pr&service_logo=ps&layout_type=popup&smcid=remoteplay&prompt=always" +
                "&PlatformPrivacyWs1=minimal&"
        const val REDIRECT_HOST = "remoteplay.dl.playstation.net"
        const val SONY_DOMAIN = "sony.com"
        const val MAX_TOKEN_PAGE_ATTEMPTS = 3
        const val SONY_ACCOUNT_HOST = "ca.account.sony.com"
        const val SONY_ACCOUNT_ORIGIN = "https://ca.account.sony.com"
        const val SSO_COOKIE_PATH = "/api/v1/ssocookie"
        const val SSO_COOKIE_URL = "https://ca.account.sony.com/api/v1/ssocookie"
    }
}
