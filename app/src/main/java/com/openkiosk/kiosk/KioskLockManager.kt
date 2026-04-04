package com.openkiosk.kiosk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import com.openkiosk.receiver.KioskDeviceAdminReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KioskLockManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val devicePolicyManager: DevicePolicyManager
        get() = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val adminComponentName: ComponentName
        get() = ComponentName(context, KioskDeviceAdminReceiver::class.java)

    fun isDeviceOwner(): Boolean {
        return devicePolicyManager.isDeviceOwnerApp(context.packageName)
    }

    fun startLockTask(activity: Activity) {
        if (isDeviceOwner()) {
            devicePolicyManager.setLockTaskPackages(adminComponentName, arrayOf(context.packageName))
            devicePolicyManager.setLockTaskFeatures(
                adminComponentName,
                DevicePolicyManager.LOCK_TASK_FEATURE_NONE
            )
            activity.startLockTask()
        } else {
            enterImmersiveMode(activity)
        }
    }

    fun stopLockTask(activity: Activity) {
        try {
            activity.stopLockTask()
        } catch (_: IllegalStateException) {
            // Not in lock task mode
        }
    }

    fun enterImmersiveMode(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = activity.window.insetsController ?: return
            controller.hide(
                android.view.WindowInsets.Type.statusBars() or
                    android.view.WindowInsets.Type.navigationBars()
            )
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
        }

        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun setAsHomeApp(activity: Activity) {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_HOME)
            component = ComponentName(context, activity::class.java)
        }
        activity.startActivity(intent)
    }
}
