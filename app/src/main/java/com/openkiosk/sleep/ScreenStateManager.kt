package com.openkiosk.sleep

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import com.openkiosk.domain.model.ScreenState
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import java.lang.ref.WeakReference
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "ScreenState"
private const val DEEP_SLEEP_CHECK_INTERVAL_MS = 60_000L // check every minute

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
    private var deepSleepEnabled: Boolean = false
    private var deepSleepStartHour: Int = 22
    private var deepSleepEndHour: Int = 6

    private val dimRunnable = Runnable { transitionToDim() }
    private val sleepRunnable = Runnable { transitionToSleep() }

    private val deepSleepCheckRunnable: Runnable = object : Runnable {
        override fun run() {
            if (!deepSleepEnabled) return
            if (isInDeepSleepWindow()) {
                val current = _screenState.value
                if (current != ScreenState.ACTIVE && current != ScreenState.DEEP_SLEEP) {
                    transitionToDeepSleep()
                }
            } else if (_screenState.value == ScreenState.DEEP_SLEEP) {
                // Exited deep sleep window — wake up
                Log.d(TAG, "Deep sleep window ended — waking")
                onUserActivity()
                return // onUserActivity will restart timers, don't double-schedule
            }
            handler.postDelayed(this, DEEP_SLEEP_CHECK_INTERVAL_MS)
        }
    }

    fun configure(
        activeTimeoutMs: Long,
        dimTimeoutMs: Long,
        dimBrightness: Float,
        deepSleepEnabled: Boolean = false,
        deepSleepStartHour: Int = 22,
        deepSleepEndHour: Int = 6
    ) {
        this.activeTimeoutMs = activeTimeoutMs
        this.dimTimeoutMs = dimTimeoutMs
        this.dimBrightness = dimBrightness.coerceIn(0.01f, 1.0f)
        this.deepSleepEnabled = deepSleepEnabled
        this.deepSleepStartHour = deepSleepStartHour
        this.deepSleepEndHour = deepSleepEndHour

        // Restart deep sleep monitoring
        handler.removeCallbacks(deepSleepCheckRunnable)
        if (deepSleepEnabled) {
            handler.post(deepSleepCheckRunnable)
        }
    }

    fun attachActivity(activity: Activity) {
        activityRef = WeakReference(activity)
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun detachActivity() {
        activityRef = null
        handler.removeCallbacks(deepSleepCheckRunnable)
    }

    fun onUserActivity() {
        handler.removeCallbacks(dimRunnable)
        handler.removeCallbacks(sleepRunnable)

        if (_screenState.value != ScreenState.ACTIVE) {
            transitionToActive()
        }

        handler.postDelayed(dimRunnable, activeTimeoutMs)
    }

    fun isInDeepSleepWindow(): Boolean {
        if (!deepSleepEnabled) return false
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (deepSleepStartHour > deepSleepEndHour) {
            // Crosses midnight: e.g., 22:00-06:00
            hour >= deepSleepStartHour || hour < deepSleepEndHour
        } else {
            // Same day: e.g., 13:00-17:00
            hour >= deepSleepStartHour && hour < deepSleepEndHour
        }
    }

    private fun transitionToActive() {
        val activity = activityRef?.get() ?: return

        activity.runOnUiThread {
            val window = activity.window
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        _screenState.value = ScreenState.ACTIVE
        Log.d(TAG, "→ ACTIVE")
    }

    private fun transitionToDim() {
        // If in deep sleep window, skip DIM and go straight to DEEP_SLEEP
        if (isInDeepSleepWindow()) {
            transitionToDeepSleep()
            return
        }

        val activity = activityRef?.get() ?: return

        activity.runOnUiThread {
            val window = activity.window
            window.attributes = window.attributes.apply {
                screenBrightness = dimBrightness
            }
        }

        _screenState.value = ScreenState.DIM
        Log.d(TAG, "→ DIM")

        handler.postDelayed(sleepRunnable, dimTimeoutMs)
    }

    private fun transitionToSleep() {
        // If in deep sleep window, go to DEEP_SLEEP instead
        if (isInDeepSleepWindow()) {
            transitionToDeepSleep()
            return
        }

        val activity = activityRef?.get() ?: return

        activity.runOnUiThread {
            val window = activity.window
            window.attributes = window.attributes.apply {
                screenBrightness = 0.0f
            }
        }

        _screenState.value = ScreenState.SLEEP
        Log.d(TAG, "→ SLEEP")
    }

    private fun transitionToDeepSleep() {
        val activity = activityRef?.get() ?: return

        handler.removeCallbacks(dimRunnable)
        handler.removeCallbacks(sleepRunnable)

        activity.runOnUiThread {
            val window = activity.window
            window.attributes = window.attributes.apply {
                screenBrightness = 0.0f
            }
        }

        _screenState.value = ScreenState.DEEP_SLEEP
        Log.d(TAG, "→ DEEP_SLEEP (scheduled: ${deepSleepStartHour}:00-${deepSleepEndHour}:00)")
    }
}
