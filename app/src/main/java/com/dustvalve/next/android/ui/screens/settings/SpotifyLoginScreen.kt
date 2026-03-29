package com.dustvalve.next.android.ui.screens.settings

import android.annotation.SuppressLint
import android.view.WindowManager
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.dustvalve.next.android.domain.repository.SpotifyRepository
import kotlinx.coroutines.launch

private const val REDIRECT_PREFIX = "http://127.0.0.1:5588/login"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginScreen(
    spotifyRepository: SpotifyRepository,
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    var loginHandled by rememberSaveable { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isPageLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val currentOnLoginSuccess by rememberUpdatedState(onLoginSuccess)
    val scope = rememberCoroutineScope()

    val authUrl = remember {
        try {
            spotifyRepository.getAuthorizationURL()
        } catch (e: Exception) {
            null
        }
    }

    // Prevent screenshots on the login screen
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
                title = { Text(stringResource(R.string.settings_sign_in_spotify)) },
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
            if (authUrl == null) {
                Text(
                    text = stringResource(R.string.settings_spotify_native_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 16.dp),
                )
            } else {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            webViewRef = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    val url = request?.url?.toString() ?: return false

                                    // Intercept the OAuth redirect
                                    if (url.startsWith(REDIRECT_PREFIX)) {
                                        if (!loginHandled) {
                                            loginHandled = true
                                            val uri = url.toUri()
                                            val code = uri.getQueryParameter("code") ?: return true
                                            val state = uri.getQueryParameter("state") ?: ""
                                            scope.launch {
                                                try {
                                                    spotifyRepository.handleOAuthCode(code, state)
                                                    currentOnLoginSuccess()
                                                } catch (e: Exception) {
                                                    loadError = "Login failed: ${e.message}"
                                                    loginHandled = false
                                                }
                                            }
                                        }
                                        return true
                                    }

                                    // Allow Spotify domains
                                    val host = url.toUri().host ?: return false
                                    return !(host.endsWith("spotify.com") ||
                                        host.endsWith("spotify.net") ||
                                        host.endsWith("facebook.com") ||
                                        host.endsWith("apple.com") ||
                                        host.endsWith("google.com") ||
                                        host.endsWith("accounts.google.com"))
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isPageLoading = false
                                    loadError = null
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
                            }

                            loadUrl(authUrl)
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
}
