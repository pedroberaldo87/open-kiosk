package com.openkiosk.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.openkiosk.data.local.KioskPrefs
import com.openkiosk.service.KioskWatchdogService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Abrir sozinho no boot faz parte do modo quiosque, que vem desligado.
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && KioskPrefs.keepInFront(context)) {
            val launchIntent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            if (launchIntent != null) {
                context.startActivity(launchIntent)
            }
            KioskWatchdogService.start(context)
        }
    }
}
