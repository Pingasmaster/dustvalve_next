package com.dustvalve.next.android.ui.screens.settings

import android.annotation.SuppressLint
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.core.net.toUri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import com.dustvalve.next.android.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val LOGIN_URL = "https://bandcamp.com/login"

private val AUTH_COOKIE_NAMES = setOf("identity", "session", "client_id", "js_logged_in")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AccountLoginScreen(
    onLoginSuccess: (Map<String, String>) -> Unit,
    onBack: () -> Unit,
) {
    val cookieManager = remember { CookieManager.getInstance() }
    var loginHandled by rememberSaveable { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isPageLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
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
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in to Dustvalve") },
                windowInsets = WindowInsets(0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "Back",
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

                        // Only expire cookies on fresh entry, not on config change recreation
                        if (!loginHandled) {
                            cookieManager.getCookie("https://bandcamp.com")
                                ?.split(";")
                                ?.forEach { cookie ->
                                    val name = cookie.trim().split("=", limit = 2).firstOrNull()?.trim()
                                    if (name != null) {
                                        cookieManager.setCookie(
                                            "https://bandcamp.com",
                                            "$name=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Domain=.bandcamp.com"
                                        )
                                    }
                                }
                            cookieManager.flush()
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false
                                // Block navigation to non-Dustvalve domains
                                if (!isDustvalveHost(url)) return true
                                if (!url.contains("/login")) {
                                    handleLoginIfNeeded(cookieManager)
                                    return true
                                }
                                return false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isPageLoading = false
                                loadError = null
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
                                    loadError = error.description?.toString() ?: "Failed to load page"
                                }
                            }

                            private fun handleLoginIfNeeded(cm: CookieManager) {
                                if (loginHandled) return
                                val cookies = extractCookies(cm, "https://bandcamp.com")
                                val authCookies = cookies.filterKeys { it in AUTH_COOKIE_NAMES }
                                if (authCookies.isNotEmpty()) {
                                    loginHandled = true
                                    currentOnLoginSuccess(authCookies)
                                }
                            }
                        }

                        loadUrl(LOGIN_URL)
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
