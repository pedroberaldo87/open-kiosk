package com.openkiosk.sleep

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import com.openkiosk.domain.model.ScreenState
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ScreenStateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _screenState = MutableStateFlow(ScreenState.ACTIVE)
    val screenState: StateFlow<ScreenState> = _screenState.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var activityRef: WeakReference<Activity>? = null

    private var activeTimeoutMs: Long = 30_000L
    private var dimTimeoutMs: Long = 60_000L
    private var dimBrightness: Float = 0.1f

    private val dimRunnable = Runnable { transitionToDim() }
    private val sleepRunnable = Runnable { transitionToSleep() }

    fun configure(activeTimeoutMs: Long, dimTimeoutMs: Long, dimBrightness: Float) {
        this.activeTimeoutMs = activeTimeoutMs
        this.dimTimeoutMs = dimTimeoutMs
        this.dimBrightness = dimBrightness.coerceIn(0.01f, 1.0f)
    }

    fun attachActivity(activity: Activity) {
        activityRef = WeakReference(activity)
        // Ensure screen stays on at OS level — always
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun detachActivity() {
        activityRef = null
    }

    fun onUserActivity() {
        handler.removeCallbacks(dimRunnable)
        handler.removeCallbacks(sleepRunnable)

        if (_screenState.value != ScreenState.ACTIVE) {
            transitionToActive()
        }

        handler.postDelayed(dimRunnable, activeTimeoutMs)
    }

    private fun transitionToActive() {
        val activity = activityRef?.get() ?: return

        activity.runOnUiThread {
            val window = activity.window
            // Restore system brightness
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            // Ensure FLAG_KEEP_SCREEN_ON is always set
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        _screenState.value = ScreenState.ACTIVE
    }

    private fun transitionToDim() {
        val activity = activityRef?.get() ?: return

        activity.runOnUiThread {
            val window = activity.window
            // Reduce brightness but keep screen on
            window.attributes = window.attributes.apply {
                screenBrightness = dimBrightness
            }
            // FLAG_KEEP_SCREEN_ON stays — never remove it
        }

        _screenState.value = ScreenState.DIM

        handler.postDelayed(sleepRunnable, dimTimeoutMs)
    }

    private fun transitionToSleep() {
        val activity = activityRef?.get() ?: return

        activity.runOnUiThread {
            val window = activity.window
            // Brightness to minimum — simulates screen off
            // The black overlay in KioskScreen ensures fully black display
            window.attributes = window.attributes.apply {
                screenBrightness = 0.0f
            }
            // FLAG_KEEP_SCREEN_ON stays — Activity must remain alive for sensors
        }

        _screenState.value = ScreenState.SLEEP
    }
}
