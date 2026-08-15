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
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

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

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun KioskWebView(
    url: String,
    onUserInteraction: () -> Unit,
    paused: Boolean,
    modifier: Modifier = Modifier,
    onError: (() -> Unit)? = null,
    onPageLoaded: (() -> Unit)? = null
) {
    var webViewKey by remember { mutableIntStateOf(0) }

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

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
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

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean = false
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

                    loadUrl(url)
                }
            },
            update = { webView ->
                if (lastRequestedUrl != url) {
                    lastRequestedUrl = url
                    webView.loadUrl(url)
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
