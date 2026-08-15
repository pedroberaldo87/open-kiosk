package com.openkiosk.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import com.openkiosk.data.local.KioskPrefs
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.openkiosk.R
import com.openkiosk.domain.model.ScreenState
import com.openkiosk.presentation.MainActivity
import com.openkiosk.sleep.wakesScreenOnLaunch

private const val TAG = "KioskWatchdog"
private const val CHANNEL_ID = "kiosk_watchdog"
private const val NOTIFICATION_ID = 1
private const val TICK_MS = 5_000L
private const val MAX_TICK_MS = 60_000L

/**
 * Espacamento entre tentativas de relance: 5s nas duas primeiras, dobrando ate 60s quando
 * o relance repetido nao surte efeito — repetir identico a cada 5s so faz a notificacao
 * piscar por cima do app que cobriu o quiosque, para sempre.
 */
internal fun relaunchDelayMs(consecutiveMisses: Int): Long =
    if (consecutiveMisses <= 1) TICK_MS
    else minOf(TICK_MS shl minOf(consecutiveMisses - 1, 4), MAX_TICK_MS)

/**
 * Decide, a partir do ESTADO GUARDADO (que sobrevive à morte do processo), se o relance
 * pode acender o painel. PowerManager.isInteractive não serve: enquanto a janela vive,
 * FLAG_KEEP_SCREEN_ON a mantém verdadeira mesmo dormindo, e quando o processo morre ela
 * fica falsa justamente no caso em que o relance precisa acontecer.
 */
fun shouldWakeOnRelaunch(storedState: String?): Boolean =
    wakesScreenOnLaunch(
        runCatching { ScreenState.valueOf(storedState ?: "") }.getOrDefault(ScreenState.ACTIVE)
    )

/**
 * Serviço em primeiro plano que prende o processo (START_STICKY) e, a cada tick,
 * traz a MainActivity de volta se algo a cobriu (lockscreen de propaganda do Fire).
 */
class KioskWatchdogService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private var consecutiveMisses = 0

    private val tickRunnable: Runnable = object : Runnable {
        override fun run() {
            if (!MainActivity.isForeground) {
                consecutiveMisses++
                if (consecutiveMisses > 1) {
                    Log.w(TAG, "relançamento anterior não trouxe a MainActivity de volta ($consecutiveMisses seguidas)")
                }
                Log.d(TAG, "MainActivity fora do topo — relançando")
                // Caminho isento do bloqueio de início em segundo plano (Android 10+):
                // repostar a notificação do serviço com full screen intent faz o SISTEMA
                // abrir a activity. Exige canal IMPORTANCE_HIGH.
                // Full screen intent ACENDE a tela: só quando o estado guardado é de tela
                // visível (ACTIVE/DIM). Em SLEEP/DEEP_SLEEP o relance é silencioso.
                if (shouldWakeOnRelaunch(
                        KioskPrefs.of(this@KioskWatchdogService)
                            .getString(KioskPrefs.KEY_SCREEN_STATE, null)
                    )
                ) {
                    startForeground(NOTIFICATION_ID, buildNotification(fullScreen = true))
                }
                // Segundo tiro: pode ser descartado em silêncio se o app estiver em segundo plano.
                startActivity(mainActivityIntent())
            } else {
                if (consecutiveMisses > 0) {
                    // Activity de volta: desarma o aviso de tela cheia, senao ele fica
                    // pendurado e um repost futuro abre a tela sem motivo.
                    startForeground(NOTIFICATION_ID, buildNotification(fullScreen = false))
                }
                consecutiveMisses = 0
            }
            handler.postDelayed(this, relaunchDelayMs(consecutiveMisses))
        }
    }

    private fun mainActivityIntent() =
        Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        )

    private fun buildNotification(fullScreen: Boolean): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .apply {
                if (fullScreen) {
                    setFullScreenIntent(
                        PendingIntent.getActivity(
                            this@KioskWatchdogService,
                            0,
                            mainActivityIntent(),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        ),
                        true
                    )
                }
            }
            .build()

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            // IMPORTANCE_HIGH é requisito do full screen intent; sem som/vibração para
            // não fazer barulho no quiosque.
            NotificationChannel(CHANNEL_ID, "Kiosk", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
        // Android 14+ nao concede USE_FULL_SCREEN_INTENT na instalacao fora de
        // chamada/alarme: sem ela o relance degrada a banner. No Fire atual (base 9/11)
        // nao morde; o aviso fica para quando o parque mudar.
        if (android.os.Build.VERSION.SDK_INT >= 34 && !manager.canUseFullScreenIntent()) {
            Log.w(TAG, "USE_FULL_SCREEN_INTENT não concedida — relance degradado a banner")
        }
        startForeground(NOTIFICATION_ID, buildNotification(fullScreen = false))
        handler.postDelayed(tickRunnable, TICK_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, KioskWatchdogService::class.java))
        }
    }
}
