package com.openkiosk.sleep

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.WindowManager
import com.openkiosk.domain.model.ScreenState
import com.openkiosk.receiver.KioskDeviceAdminReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private var dimTimeoutMs: Long = 10_000L
    private var dimBrightness: Float = 0.1f

    private val powerManager: PowerManager =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val adminComponent = ComponentName(context, KioskDeviceAdminReceiver::class.java)

    @Suppress("DEPRECATION")
    private var wakeLock: PowerManager.WakeLock = powerManager.newWakeLock(
        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
        "openkiosk:wake"
    )

    private val dimRunnable = Runnable { transitionToDim() }
    private val sleepRunnable = Runnable { transitionToSleep() }

    fun configure(activeTimeoutMs: Long, dimTimeoutMs: Long, dimBrightness: Float) {
        this.activeTimeoutMs = activeTimeoutMs
        this.dimTimeoutMs = dimTimeoutMs
        this.dimBrightness = dimBrightness.coerceIn(0.0f, 1.0f)
    }

    fun attachActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    fun detachActivity() {
        activityRef = null
    }

    fun onUserActivity() {
        handler.removeCallbacks(dimRunnable)
        handler.removeCallbacks(sleepRunnable)

        transitionToActive()

        handler.postDelayed(dimRunnable, activeTimeoutMs)
    }

    private fun transitionToActive() {
        val activity = activityRef?.get() ?: return

        activity.runOnUiThread {
            val window = activity.window

            val layoutParams = window.attributes
            layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = layoutParams

            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setTurnScreenOn(true)
            }
        }

        if (!wakeLock.isHeld) {
            wakeLock.acquire(10 * 60 * 1000L) // 10-minute safety timeout
        }

        _screenState.value = ScreenState.ACTIVE
    }

    private fun transitionToDim() {
        val activity = activityRef?.get() ?: return

        releaseWakeLock()

        activity.runOnUiThread {
            val layoutParams = activity.window.attributes
            layoutParams.screenBrightness = dimBrightness
            activity.window.attributes = layoutParams
        }

        _screenState.value = ScreenState.DIM

        handler.postDelayed(sleepRunnable, dimTimeoutMs)
    }

    private fun transitionToSleep() {
        releaseWakeLock()

        val isDeviceOwner = devicePolicyManager.isDeviceOwnerApp(context.packageName)

        if (isDeviceOwner) {
            devicePolicyManager.lockNow()
        } else {
            val activity = activityRef?.get()
            if (activity != null) {
                activity.runOnUiThread {
                    val window = activity.window
                    val layoutParams = window.attributes
                    layoutParams.screenBrightness = 0.0f
                    window.attributes = layoutParams
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }

        _screenState.value = ScreenState.SLEEP
    }

    private fun releaseWakeLock() {
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }
}
