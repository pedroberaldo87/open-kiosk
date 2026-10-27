package com.openkiosk.service

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import com.openkiosk.data.local.KioskPrefs
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.provider.Settings
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

private const val GRAPHICS_CHECK_MS = 10 * 60_000L

// knob de campo: subir se o quiosque reiniciar sozinho de madrugada sem a memoria estar
// apertando; baixar se o tablet ainda ficar sem memoria antes do reinicio. Recem-aberto o
// app usa ~40 MB de memoria de video; o motor de paginas do Fire deixa ~0,3 MB presos a
// cada carregamento de pagina, e so reiniciar o processo os devolve.
internal const val GRAPHICS_RESTART_KB = 300_000L

/**
 * Reinicia só com a tela apagada (SLEEP/DEEP_SLEEP): o app volta preto pelo estado guardado
 * e ninguém vê a troca. Com a tela acesa espera — reiniciar ali piscaria na frente de alguém.
 */
internal fun shouldRestartForGraphics(graphicsKb: Long, storedState: String?): Boolean =
    graphicsKb >= GRAPHICS_RESTART_KB && !shouldWakeOnRelaunch(storedState)

/** Chave global que o pacote de ofertas do Fire (com.amazon.kindle.kso) religa em todo boot. */
internal const val LOCKSCREEN_AD_KEY = "LOCKSCREEN_AD_ENABLED"

/** Qualquer valor que não seja "0" (inclusive ausente) é anúncio ligado. */
internal fun lockscreenAdIsOn(value: String?): Boolean = value != "0"

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

    // O pacote de ofertas é protegido (não desliga nem suspende por adb) e reescreve a chave
    // no boot (histórico de `dumpsys settings`: pkg:com.amazon.kindle.kso). Vigiar a chave
    // devolve o 0 no instante em que ele a religa, em vez de esperar o próximo boot.
    private val lockscreenAdObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = disableLockscreenAd()
    }

    private fun disableLockscreenAd() {
        val value = Settings.Global.getString(contentResolver, LOCKSCREEN_AD_KEY)
        if (!lockscreenAdIsOn(value)) return
        try {
            Settings.Global.putString(contentResolver, LOCKSCREEN_AD_KEY, "0")
            Log.d(TAG, "anúncio de tela de bloqueio religado ($value) — desligado de novo")
        } catch (e: SecurityException) {
            Log.w(TAG, "sem WRITE_SECURE_SETTINGS — rode: adb shell pm grant com.openkiosk android.permission.WRITE_SECURE_SETTINGS")
        }
    }

    // A memória de vídeo presa pelo motor de páginas pertence ao processo: destruir a
    // WebView não a devolve (medido: 386 recriações, de 87 para 154 MB). Só um processo
    // novo começa do zero, e o arranque frio já volta no estado de tela guardado.
    private val graphicsRunnable: Runnable = object : Runnable {
        override fun run() {
            // Pelo sistema, não por Debug.getMemoryInfo: este lê o próprio processo sem o
            // contador da placa de vídeo e devolve 0 (medido no Fire). Android 10+ amostra esta
            // consulta a cada 5 min, e o intervalo aqui é maior que isso.
            val info = getSystemService(ActivityManager::class.java)
                .getProcessMemoryInfo(intArrayOf(Process.myPid())).first()
            val graphicsKb = info.getMemoryStat("summary.graphics")?.toLongOrNull() ?: 0L
            val state = KioskPrefs.of(this@KioskWatchdogService)
                .getString(KioskPrefs.KEY_SCREEN_STATE, null)
            Log.d(TAG, "memória de vídeo: $graphicsKb KB (estado $state)")
            if (shouldRestartForGraphics(graphicsKb, state)) restartProcess(graphicsKb)
            else handler.postDelayed(this, GRAPHICS_CHECK_MS)
        }
    }

    private fun restartProcess(graphicsKb: Long) {
        Log.w(TAG, "memória de vídeo em $graphicsKb KB com a tela apagada — reiniciando o processo")
        val relaunch = PendingIntent.getActivity(
            this, 1, mainActivityIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarms = getSystemService(AlarmManager::class.java)
        val at = System.currentTimeMillis() + 2_000L
        if (android.os.Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
            alarms.setExact(AlarmManager.RTC, at, relaunch)
        } else {
            alarms.set(AlarmManager.RTC, at, relaunch)
        }
        Process.killProcess(Process.myPid())
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
        handler.postDelayed(graphicsRunnable, GRAPHICS_CHECK_MS)
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor(LOCKSCREEN_AD_KEY), false, lockscreenAdObserver
        )
        disableLockscreenAd()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(lockscreenAdObserver)
        handler.removeCallbacks(graphicsRunnable)
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
