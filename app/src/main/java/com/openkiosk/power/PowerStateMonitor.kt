package com.openkiosk.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PowerState"

/**
 * Diz se o tablet está na tomada e avisa quando o carregador entra ou sai.
 * Decisão do dono: na tomada a câmera analisa sem pausa em SLEEP; na bateria, pulsa.
 */
@Singleton
class PowerStateMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var receiver: BroadcastReceiver? = null

    // O singleton sobrevive a recriacao da activity: sem trocar o aviso, a ViewModel nova
    // caía no retorno-cedo e a antiga levava o registro embora ao ser descartada — daí
    // plugar/desplugar deixava de trocar o modo da camera para sempre.
    @Volatile private var onChangeCallback: (() -> Unit)? = null

    fun isPlugged(): Boolean {
        // Intent sticky de bateria: leitura pontual, sem registrar receiver.
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }

    fun start(onChange: () -> Unit) {
        // Troca o aviso SEMPRE: quem chegou por ultimo e quem esta vivo.
        onChangeCallback = onChange
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.d(TAG, "Carregador ${if (intent.action == Intent.ACTION_POWER_CONNECTED) "conectado" else "removido"}")
                onChangeCallback?.invoke()
            }
        }
        receiver = r
        context.registerReceiver(r, IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        })
    }

    fun stop() {
        receiver?.let { context.unregisterReceiver(it) }
        receiver = null
    }
}
