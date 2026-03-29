package com.dustvalve.next.android.ui.screens.settings

import android.annotation.SuppressLint
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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

private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fmusic.youtube.com%2F"

/** Cookie that confirms a fully authenticated YouTube Music session. */
private const val AUTH_MARKER_COOKIE = "__Secure-3PAPISID"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeMusicLoginScreen(
    onLoginSuccess: (Map<String, String>) -> Unit,
    onBack: () -> Unit,
) {
    val cookieManager = remember { CookieManager.getInstance() }
    var loginHandled by rememberSaveable { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isPageLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
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
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_sign_in_youtube)) },
                windowInsets = WindowInsets(0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                        // Google login requires third-party cookies
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        // Clear YouTube/Google cookies on fresh entry so re-login starts clean
                        if (!loginHandled) {
                            clearDomainCookies(cookieManager, "https://youtube.com")
                            clearDomainCookies(cookieManager, "https://music.youtube.com")
                            clearDomainCookies(cookieManager, "https://google.com")
                            clearDomainCookies(cookieManager, "https://accounts.google.com")
                            cookieManager.flush()
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false
                                // Allow Google and YouTube domains for the login flow
                                if (!isAllowedHost(url)) return true
                                return false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isPageLoading = false
                                loadError = null
                                if (url != null && isYouTubeMusicPage(url)) {
                                    handleLoginIfNeeded(cookieManager)
                                }
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: android.webkit.WebResourceError,
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request.isForMainFrame) {
                                    isPageLoading = false
                                    loadError = error.description?.toString() ?: "Failed to load page"
                                }
                            }

                            private fun handleLoginIfNeeded(cm: CookieManager) {
                                if (loginHandled) return
                                val cookies = extractAllYouTubeCookies(cm)
                                if (AUTH_MARKER_COOKIE in cookies) {
                                    loginHandled = true
                                    currentOnLoginSuccess(cookies)
                                }
                            }
                        }

                        loadUrl(LOGIN_URL)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (isPageLoading) {
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

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

private fun isAllowedHost(url: String): Boolean {
    val host = url.toUri().host ?: return false
    return host == "youtube.com" || host.endsWith(".youtube.com") ||
        host == "google.com" || host.endsWith(".google.com") ||
        host.endsWith(".gstatic.com") || host.endsWith(".googleapis.com")
}

private fun isYouTubeMusicPage(url: String): Boolean {
    val host = url.toUri().host ?: return false
    return host == "music.youtube.com" || host == "www.music.youtube.com"
}

/**
 * Extracts cookies from both youtube.com and music.youtube.com domains,
 * merging them into a single map.
 */
private fun extractAllYouTubeCookies(cookieManager: CookieManager): Map<String, String> {
    val result = mutableMapOf<String, String>()
    listOf(
        "https://youtube.com",
        "https://music.youtube.com",
        "https://www.youtube.com",
    ).forEach { url ->
        val cookieString = cookieManager.getCookie(url) ?: return@forEach
        cookieString.split(";").forEach { cookie ->
            val parts = cookie.trim().split("=", limit = 2)
            if (parts.size == 2) {
                result[parts[0].trim()] = parts[1].trim()
            }
        }
    }
    return result
}

private fun clearDomainCookies(cookieManager: CookieManager, url: String) {
    cookieManager.getCookie(url)
        ?.split(";")
        ?.forEach { cookie ->
            val name = cookie.trim().split("=", limit = 2).firstOrNull()?.trim()
            if (name != null) {
                val domain = url.toUri().host?.let { ".$it" } ?: return@forEach
                cookieManager.setCookie(
                    url,
                    "$name=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Domain=$domain"
                )
            }
        }
}
