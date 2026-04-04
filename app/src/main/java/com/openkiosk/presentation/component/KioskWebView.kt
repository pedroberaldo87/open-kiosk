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
    modifier: Modifier = Modifier,
    onError: (() -> Unit)? = null,
    onPageLoaded: (() -> Unit)? = null
) {
    var webViewKey by remember { mutableIntStateOf(0) }

    key(webViewKey) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                WebView(context).apply {
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
                if (webView.url != url) {
                    webView.loadUrl(url)
                }
            }
        )

        DisposableEffect(Unit) {
            onDispose {
                // WebView cleanup is handled by AndroidView
            }
        }
    }
}
