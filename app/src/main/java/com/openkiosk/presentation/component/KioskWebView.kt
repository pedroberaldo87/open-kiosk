package com.openkiosk.presentation.component

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.openkiosk.R
import com.openkiosk.webview.BlockedPage
import com.openkiosk.webview.NavigationPolicy
import com.openkiosk.webview.WebSocketGuard
import com.openkiosk.webview.WebSocketPolicyBridge
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "KioskWebView"

private const val INJECT_JS = """
    (function() {
        document.documentElement.style.overflow = 'hidden';
        document.body.style.overflow = 'hidden';
        document.documentElement.style.webkitUserSelect = 'none';
        document.documentElement.style.userSelect = 'none';
        var style = document.createElement('style');
        style.textContent = '::-webkit-scrollbar { display: none !important; } * { -webkit-user-select: none !important; user-select: none !important; }';
        document.head.appendChild(style);
    })();
"""

// Logs scheme://host only: userinfo, paths and query strings can carry credentials or tokens.
private fun redact(url: String): String {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd < 0) return "?"
    val authorityEnd = url.indexOfAny(charArrayOf('/', '?', '#'), schemeEnd + 3)
        .let { if (it < 0) url.length else it }
    val host = url.substring(schemeEnd + 3, authorityEnd).substringAfterLast('@')
    return url.substring(0, schemeEnd + 3) + host
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun KioskWebView(
    url: String,
    allowedUrls: List<String>,
    onUserInteraction: () -> Unit,
    paused: Boolean,
    modifier: Modifier = Modifier,
    onError: (() -> Unit)? = null,
    onPageLoaded: (() -> Unit)? = null
) {
    var webViewKey by remember { mutableIntStateOf(0) }

    // Hosts the owner configured (start URL + playlist) plus the URL being shown. Read through a
    // State so the live WebView sees playlist edits without being recreated.
    val navigationPolicy by rememberUpdatedState(NavigationPolicy(allowedUrls + url))

    key(webViewKey) {
        // Instancia viva desta chave; vira null assim que for destruida
        val webViewRef = remember { mutableStateOf<WebView?>(null) }
        // Comparar com webView.url recarregaria a cada recomposicao, porque o
        // endereco efetivo pos-redirecionamento nunca bate com o pedido.
        var lastRequestedUrl by remember { mutableStateOf(url) }

        AndroidView(
            modifier = modifier,
            factory = { context ->
                WebView(context).apply {
                    webViewRef.value = this
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    // Custom view container for fullscreen video
                    var customView: View? = null
                    var customViewCallback: WebChromeClient.CustomViewCallback? = null

                    // Main-frame URL the policy refused and answered with the local blocked page.
                    // Written on the IO thread of shouldInterceptRequest, read on the UI thread.
                    val blockedMainFrame = AtomicReference<String?>(null)

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            // The blocked page is a finished load, but not a successful one: do not
                            // report success to the recovery manager, and do not report an error
                            // either, because retrying the same refused URL would reload forever.
                            if (url != null && url == blockedMainFrame.get()) return
                            view?.evaluateJavascript(INJECT_JS, null)
                            onPageLoaded?.invoke()
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                Log.e(TAG, "WebView error: ${error?.description} (code: ${error?.errorCode})")
                                onError?.invoke()
                            }
                        }

                        override fun onRenderProcessGone(
                            view: WebView?,
                            detail: android.webkit.RenderProcessGoneDetail?
                        ): Boolean {
                            Log.e(TAG, "Render process gone, crash=${detail?.didCrash()}, priority=${detail?.rendererPriorityAtExit()}")
                            // Contrato do Android: a instancia morta tem que sair da
                            // hierarquia E ser destruida, senao o app cai junto.
                            webViewRef.value = null
                            view?.let { dead ->
                                (dead.parent as? ViewGroup)?.removeView(dead)
                                dead.destroy()
                            }
                            webViewKey++
                            return true
                        }

                        // Confines the kiosk to the configured hosts: link taps, scripted
                        // navigation and server redirects all pass through here. Returning true
                        // cancels the load and keeps the current page on screen.
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val target = request?.url?.toString() ?: return true
                            val allowed = navigationPolicy.allowsNavigation(target)
                            if (!allowed) Log.w(TAG, "Blocked navigation to ${redact(target)}")
                            return !allowed
                        }

                        // Second line of defence: main-frame loads that skip the override (the
                        // first request of loadUrl, some redirects) and plain-http sub-resources
                        // to hosts that are neither on the LAN nor configured as http by the owner.
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val target = request?.url?.toString() ?: return null
                            val allowed = if (request.isForMainFrame) {
                                navigationPolicy.allowsNavigation(target)
                            } else {
                                navigationPolicy.allowsRequest(target)
                            }
                            if (request.isForMainFrame) {
                                blockedMainFrame.set(if (allowed) null else target)
                            }
                            if (allowed) return null
                            Log.w(TAG, "Blocked request to ${redact(target)}")
                            if (request.isForMainFrame) {
                                // Show why the screen is not the page the owner expects, with the
                                // host to add to the playlist. Local data only: no network, no loop.
                                val ctx = view?.context
                                val page = BlockedPage.html(
                                    title = ctx?.getString(R.string.blocked_title) ?: "Address not allowed",
                                    message = ctx?.getString(R.string.blocked_message) ?: "",
                                    host = NavigationPolicy.hostOf(target)
                                )
                                return WebResourceResponse(
                                    "text/html",
                                    "utf-8",
                                    403,
                                    "Blocked by kiosk policy",
                                    emptyMap(),
                                    ByteArrayInputStream(page.toByteArray(Charsets.UTF_8))
                                )
                            }
                            return WebResourceResponse(
                                "text/plain",
                                "utf-8",
                                403,
                                "Blocked by kiosk policy",
                                emptyMap(),
                                ByteArrayInputStream(ByteArray(0))
                            )
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            customView = view
                            customViewCallback = callback
                            if (view != null) {
                                (this@apply.parent as? ViewGroup)?.let { parent ->
                                    parent.addView(
                                        view,
                                        FrameLayout.LayoutParams(
                                            FrameLayout.LayoutParams.MATCH_PARENT,
                                            FrameLayout.LayoutParams.MATCH_PARENT
                                        )
                                    )
                                }
                            }
                        }

                        override fun onHideCustomView() {
                            customView?.let { view ->
                                (view.parent as? ViewGroup)?.removeView(view)
                            }
                            customViewCallback?.onCustomViewHidden()
                            customView = null
                            customViewCallback = null
                        }

                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            Log.d(TAG, "Console: ${consoleMessage?.message()} [${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()}]")
                            return true
                        }
                    }

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        setSupportZoom(false)
                        displayZoomControls = false
                        builtInZoomControls = false
                        useWideViewPort = true
                        loadWithOverviewMode = true
                    }

                    setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            onUserInteraction()
                        }
                        false
                    }

                    // ws:// follows the plain-http rule. Must be in place before the first
                    // loadUrl so the script runs ahead of the page's own scripts.
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                        addJavascriptInterface(
                            WebSocketPolicyBridge { navigationPolicy },
                            WebSocketGuard.BRIDGE_NAME
                        )
                        WebViewCompat.addDocumentStartJavaScript(this, WebSocketGuard.SCRIPT, setOf("*"))
                    } else {
                        Log.w(TAG, "WebView has no document-start scripts: ws:// is not restricted")
                    }

                    loadUrl(NavigationPolicy.withDefaultScheme(url))
                }
            },
            update = { webView ->
                if (lastRequestedUrl != url) {
                    lastRequestedUrl = url
                    webView.loadUrl(NavigationPolicy.withDefaultScheme(url))
                }
            }
        )

        // Em SLEEP/DEEP_SLEEP a tela fica preta, mas JS/timers/video seguiriam
        // rodando a plena carga. Congela a instancia viva e descongela ao voltar.
        LaunchedEffect(paused, webViewRef.value) {
            webViewRef.value?.let { webView ->
                if (paused) {
                    webView.onPause()
                    webView.pauseTimers()
                } else {
                    webView.onResume()
                    webView.resumeTimers()
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                webViewRef.value?.let { webView ->
                    webViewRef.value = null
                    webView.stopLoading()
                    webView.loadUrl("about:blank")
                    webView.clearHistory()
                    webView.webChromeClient = null
                    webView.setOnTouchListener(null)
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    webView.destroy()
                }
            }
        }
    }
}
