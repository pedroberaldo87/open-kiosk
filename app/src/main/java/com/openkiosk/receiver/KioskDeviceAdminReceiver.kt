package com.openkiosk.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class KioskDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)

        val devicePolicyManager =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(context, this::class.java)

        if (devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
            devicePolicyManager.setLockTaskPackages(
                componentName,
                arrayOf(context.packageName)
            )
        }
    }
}
