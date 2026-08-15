package com.openkiosk.presentation

import android.Manifest
import android.content.Context
import com.openkiosk.data.local.KioskPrefs
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.openkiosk.presentation.screen.KioskScreen
import com.openkiosk.presentation.viewmodel.KioskViewModel
import com.openkiosk.service.KioskWatchdogService
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: KioskViewModel by viewModels()

    // Cada permissão é pedida no máximo uma vez por processo.
    private var cameraPermissionAsked = false
    private var notificationPermissionAsked = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onCameraPermissionGranted()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Sem notificação postada o full screen intent do watchdog não relança a activity.
        if (!granted) Log.w(TAG, "POST_NOTIFICATIONS negada — watchdog sem full screen intent")
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = KioskPrefs.of(newBase)
        val lang = prefs.getString(KioskPrefs.KEY_LANGUAGE, "auto") ?: "auto"
        if (lang != "auto") {
            val locale = Locale(lang)
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            super.attachBaseContext(newBase.createConfigurationContext(config))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Volta por cima da lockscreen (propaganda do Fire) sem depender do usuário
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)

        // Brilho do estado guardado ANTES da primeira exibição: o display sempre liga
        // (ver onStart), mas o painel já nasce preto se o app dormia quando morreu.
        viewModel.applyLaunchBrightness(this)

        KioskWatchdogService.start(this)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                KioskScreen(viewModel = viewModel)
            }
        }
    }

    // Marcado em onStart/onStop (e não em onResume/onPause) porque um diálogo do sistema
    // por cima — o pedido de permissão de câmera, por exemplo — apenas PAUSA a activity.
    // Marcar em onPause faria o watchdog relançar a MainActivity em 5s e matar o diálogo.
    override fun onStart() {
        super.onStart()
        // Display SEMPRE alimentado: com o painel desligado de verdade a activity não fica
        // visível, a câmera presa ao lifecycle não roda e o toque não chega — nenhum caminho
        // de presença sobraria. "Painel claro" é outra coisa, e essa sim segue o estado
        // guardado, pelo brilho aplicado em onCreate/attachActivity. O atributo também é
        // fixado em onCreate; repetir aqui cobre a activity reaproveitada (singleTask).
        setTurnScreenOn(true)
        isForeground = true
    }

    override fun onStop() {
        super.onStop()
        isForeground = false
    }

    override fun onResume() {
        super.onResume()
        viewModel.attachActivity(this, this)
        // Espera a config REAL do banco: o placeholder tem wakeOnMotion=true e pediria
        // camera a quem a desligou nas configuracoes.
        lifecycleScope.launch {
            val cfg = viewModel.realConfig.filterNotNull().first()
            requestCameraIfNeeded(cfg.wakeOnMotion)
        }
        requestNotificationIfNeeded()
    }

    override fun onPause() {
        super.onPause()
        viewModel.detachActivity()
    }

    @Suppress("DEPRECATION", "MissingSuperCall")
    override fun onBackPressed() {
        // Intentionally not calling super — blocks back button in kiosk mode
    }

    private fun requestCameraIfNeeded(wakeOnMotion: Boolean) {
        if (!wakeOnMotion) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.onCameraPermissionGranted()
        } else if (!cameraPermissionAsked) {
            // onResume roda de novo quando o diálogo devolve o foco; sem esta bandeira
            // uma negativa definitiva vira laço (attach/detach a cada volta).
            cameraPermissionAsked = true
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun requestNotificationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            if (!notificationPermissionAsked) {
                notificationPermissionAsked = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            return
        }
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            Log.w(TAG, "Notificações desabilitadas — watchdog sem full screen intent")
        }
    }

    companion object {
        private const val TAG = "MainActivity"

        /** Lido pelo KioskWatchdogService para decidir se precisa relançar a activity. */
        @Volatile
        var isForeground: Boolean = false
            private set
    }
}
