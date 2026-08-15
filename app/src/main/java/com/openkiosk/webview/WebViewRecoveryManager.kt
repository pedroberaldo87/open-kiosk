package com.openkiosk.webview

import android.os.Handler
import android.os.Looper
import android.util.Log

private const val TAG = "WebViewRecoveryManager"

class WebViewRecoveryManager {

    private val handler = Handler(Looper.getMainLooper())
    private var autoRefreshRunnable: Runnable? = null
    private var retryRunnable: Runnable? = null
    private var retryCount = 0

    var autoRefreshIntervalMs: Long = 30 * 60 * 1000L // default 30 min

    private val backoffDelays = longArrayOf(
        5_000L,
        15_000L,
        30_000L,
        60_000L
    )

    fun startAutoRefresh(onRefresh: () -> Unit) {
        stopAutoRefresh()
        // "Desativado" no menu chega como 0: agendar com atraso 0 seria recarga em laco
        // continuo, o oposto de desligar.
        if (autoRefreshIntervalMs <= 0L) {
            Log.d(TAG, "Auto-refresh desativado")
            return
        }
        val runnable = object : Runnable {
            override fun run() {
                Log.d(TAG, "Auto-refresh triggered")
                onRefresh()
                handler.postDelayed(this, autoRefreshIntervalMs)
            }
        }
        autoRefreshRunnable = runnable
        handler.postDelayed(runnable, autoRefreshIntervalMs)
    }

    fun onError(onRetry: () -> Unit) {
        stopRetry()
        val delay = if (retryCount < backoffDelays.size) {
            backoffDelays[retryCount]
        } else {
            backoffDelays.last()
        }
        retryCount++
        Log.d(TAG, "Scheduling retry #$retryCount in ${delay}ms")
        val runnable = Runnable {
            Log.d(TAG, "Retry #$retryCount triggered")
            onRetry()
        }
        retryRunnable = runnable
        handler.postDelayed(runnable, delay)
    }

    fun onSuccess() {
        retryCount = 0
        stopRetry()
    }

    /** Visível para o teste do "desativado": nada agendado quando o intervalo é 0. */
    fun isAutoRefreshScheduled(): Boolean = autoRefreshRunnable != null

    fun stop() {
        stopAutoRefresh()
        stopRetry()
    }

    private fun stopAutoRefresh() {
        autoRefreshRunnable?.let { handler.removeCallbacks(it) }
        autoRefreshRunnable = null
    }

    private fun stopRetry() {
        retryRunnable?.let { handler.removeCallbacks(it) }
        retryRunnable = null
    }
}
