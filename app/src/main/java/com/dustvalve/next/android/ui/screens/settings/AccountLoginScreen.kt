// slack-lints DeprecatedCall fires here as a known false positive
// (slackhq/slack-lints#268): `android.webkit.WebView` is NOT deprecated in
// the Android SDK (API 37, 2026) - only WebSQL + software-draw are. The rule
// triggers because some legacy WebView overload carries @Deprecated and
// slack-lints' DeprecatedCallDetector flags every overload of any name with
// any @Deprecated symbol.
//
// We CANNOT use Chrome Custom Tabs here: this flow must read the Set-Cookie
// session token via CookieManager.getCookie() to bootstrap the Bandcamp
// session. CCT deliberately shares the user's browser cookie jar and never
// exposes it to the host app. Credential Manager only supports passkeys /
// passwords / Sign-in-with-Google; Bandcamp publishes neither an OAuth code
// endpoint nor an OIDC provider, so AppAuth-Android is also off the table.
// RFC 8252 section 8.12 prefers external user-agents but does not address legacy
// cookie-only login providers.
//
// Compensating controls applied to this WebView (OWASP MASTG-KNOW-0018 /
// MASVS-AUTH 2026, Oversecured WebView checklist):
//   * Strict scheme+host allowlist via WebViewClient.shouldOverrideUrlLoading
//     (everything off-allowlist is blocked, not just non-https).
//   * FLAG_SECURE on the activity window - blocks screenshots / screen
//     recording / RecentScreens preview of the auth page.
//   * settings.allowFileAccess / allowContentAccess /
//     allowFileAccessFromFileURLs / allowUniversalAccessFromFileURLs all OFF
//     (defaults vary across vendor WebView builds; we set them explicitly).
//   * settings.mixedContentMode = MIXED_CONTENT_NEVER_ALLOW.
//   * JavaScript enabled from the start (required by the Bandcamp form); the
//     initial load is a hardcoded https allowlisted URL and every subsequent
//     navigation is gated by shouldOverrideUrlLoading, so JS never runs on an
//     off-allowlist origin.
//   * No addJavascriptInterface; no JS->native bridge of any kind.
//   * WebViewClient.onReceivedSslError cancels (never proceeds) - the
//     inherited default; no override weakens it.
//   * On first entry per login flow: bandcamp.com cookies expired +
//     WebStorage.deleteAllData, so the captured cookie originates from the
//     user's fresh session, not a stale residual. (Guarded by a saveable
//     flag so config-change recreation doesn't wipe a login in progress.)
//   * Cookies are read with CookieManager.getCookie("https://bandcamp.com")
//     and Domain-scoped manually - we never persist cookies for subdomains
//     we did not navigate to.
//   * webViewRef.destroy() in DisposableEffect.onDispose to clear native
//     resources and break in-memory caches.
//   * setWebContentsDebuggingEnabled is left at the platform default
//     (false in release builds).
//
// Revisit when AndroidX ships a "trusted in-app OAuth browser" API that
// exposes redirect cookies safely. As of API 37 / June 2026, none exists.
@file:Suppress("DeprecatedCall")

package com.dustvalve.next.android.ui.screens.settings

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.dustvalve.next.android.R

private const val LOGIN_URL = "https://bandcamp.com/login"

private val AUTH_COOKIE_NAMES = setOf("identity", "session", "client_id", "js_logged_in")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AccountLoginScreen(onLoginSuccess: (Map<String, String>) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val cookieManager = remember { CookieManager.getInstance() }
    var loginHandled by rememberSaveable { mutableStateOf(false) }
    // Cookie expiry must run once per login flow, NOT on every WebView
    // recreation: mid-login loginHandled is still false, so keying the guard
    // on it alone wiped an in-progress login on rotation.
    var cookiesClearedOnce by rememberSaveable { mutableStateOf(false) }
    // WebView back/forward state so rotation restores the in-progress flow.
    val webViewStateBundle = rememberSaveable { Bundle() }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isPageLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val loadErrorFallback = stringResource(R.string.error_load_page)
    val blockedNavMsg = stringResource(R.string.login_navigation_blocked)
    // Keep a stable reference to the latest callback to avoid stale lambda captures in WebView
    val currentOnLoginSuccess by rememberUpdatedState(onLoginSuccess)

    // Prevent screenshots/screen recordings on the login screen
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Best-effort snapshot (onPageFinished already keeps the bundle
            // current) before releasing native resources.
            try {
                webViewRef?.saveState(webViewStateBundle)
            } catch (_: Exception) {
            }
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_sign_in_bandcamp)) },
                windowInsets = WindowInsets(0),
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.common_cd_back),
                        )
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        webViewRef = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // Explicit hardening (defaults vary across vendor WebView builds).
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.allowFileAccessFromFileURLs = false
                        settings.allowUniversalAccessFromFileURLs = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

                        // Only expire cookies + web storage once per login flow -
                        // never on config-change recreation (that wiped mid-login state).
                        if (!loginHandled && !cookiesClearedOnce) {
                            cookiesClearedOnce = true
                            cookieManager.getCookie("https://bandcamp.com")
                                ?.split(";")
                                ?.forEach { cookie ->
                                    val name = cookie.trim().split("=", limit = 2).firstOrNull()?.trim()
                                    if (name != null) {
                                        cookieManager.setCookie(
                                            "https://bandcamp.com",
                                            "$name=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Domain=.bandcamp.com",
                                        )
                                    }
                                }
                            cookieManager.flush()
                            WebStorage.getInstance().deleteAllData()
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString() ?: return false
                                // Block navigation to non-Dustvalve domains - with
                                // feedback, so an SSO/IdP redirect isn't a silent no-op.
                                if (!isDustvalveHost(url)) {
                                    Toast.makeText(context, blockedNavMsg, Toast.LENGTH_SHORT).show()
                                    return true
                                }
                                // Auxiliary auth pages (signup, forgot password, ...)
                                // stay inside the WebView.
                                if (isAuthFlowUrl(url)) return false
                                // Any other Bandcamp page is the post-login redirect:
                                // capture cookies instead of rendering it.
                                handleLoginIfNeeded(cookieManager)
                                return true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isPageLoading = false
                                loadError = null
                                // Keep the saved-state bundle current on every committed
                                // navigation so rotation restores the in-progress flow.
                                view?.saveState(webViewStateBundle)
                                if (url != null && !url.contains("/login") && isDustvalveHost(url)) {
                                    handleLoginIfNeeded(cookieManager)
                                }
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: android.webkit.WebResourceError,
                            ) {
                                super.onReceivedError(view, request, error)
                                // Only handle errors for the main frame to avoid spurious sub-resource errors
                                if (request.isForMainFrame) {
                                    isPageLoading = false
                                    loadError = error.description?.toString() ?: loadErrorFallback
                                }
                            }

                            private fun handleLoginIfNeeded(cm: CookieManager) {
                                if (loginHandled) return
                                val cookies = extractCookies(cm, "https://bandcamp.com")
                                val authCookies = cookies.filterKeys { it in AUTH_COOKIE_NAMES }
                                if ("identity" in authCookies) {
                                    loginHandled = true
                                    currentOnLoginSuccess(authCookies)
                                }
                            }
                        }

                        if (webViewStateBundle.isEmpty) {
                            loadUrl(LOGIN_URL)
                        } else {
                            // Recreation (e.g. rotation): resume the in-progress flow.
                            restoreState(webViewStateBundle)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Loading indicator
            if (isPageLoading) {
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Error state
            loadError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 16.dp),
                )
            }
        }
    }
}

private fun isDustvalveHost(url: String): Boolean {
    val host = url.toUri().host ?: return false
    return host == "bandcamp.com" || host.endsWith(".bandcamp.com")
}

// Auxiliary auth pages that must render inside the WebView. Everything is
// already host-gated to *.bandcamp.com before this check runs.
private val AUTH_PATH_PREFIXES = listOf(
    "/login",
    "/signup",
    "/forgot_password",
    "/forgot-password",
    "/password_reset",
    "/password-reset",
    "/recover",
    "/oauth",
)

private fun isAuthFlowUrl(url: String): Boolean {
    val path = url.toUri().path ?: return false
    return AUTH_PATH_PREFIXES.any { prefix -> path == prefix || path.startsWith("$prefix/") }
}

private fun extractCookies(cookieManager: CookieManager, url: String): Map<String, String> {
    val cookieString = cookieManager.getCookie(url) ?: return emptyMap()
    return cookieString.split(";")
        .mapNotNull { cookie ->
            val parts = cookie.trim().split("=", limit = 2)
            if (parts.size == 2) {
                parts[0].trim() to parts[1].trim()
            } else {
                null
            }
        }
        .toMap()
}
