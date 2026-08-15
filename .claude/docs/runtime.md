---
generated: 2026-08-15
project: open-kiosk
scope:
  - app/src/main/java/com/openkiosk/sleep/ScreenStateManager.kt
  - app/src/main/java/com/openkiosk/presentation/screen/SettingsScreen.kt
  - app/src/main/java/com/openkiosk/presentation/viewmodel/KioskViewModel.kt
  - app/src/main/java/com/openkiosk/service/KioskWatchdogService.kt
  - app/src/main/java/com/openkiosk/sensors/MotionDetectionAnalyzer.kt
  - app/src/main/java/com/openkiosk/sensors/MotionDetectionManager.kt
  - app/src/main/java/com/openkiosk/webview/WebViewRecoveryManager.kt
  - app/src/main/java/com/openkiosk/presentation/MainActivity.kt
  - app/src/main/java/com/openkiosk/presentation/component/KioskWebView.kt
  - app/src/main/java/com/openkiosk/power/PowerStateMonitor.kt
  - app/src/main/java/com/openkiosk/receiver/BootReceiver.kt
  - app/src/main/java/com/openkiosk/presentation/screen/KioskScreen.kt
  - app/src/main/java/com/openkiosk/sensors/SensorWakeManager.kt
  - app/src/main/java/com/openkiosk/kiosk/KioskLockManager.kt
  - app/src/main/java/com/openkiosk/data/repository/ConfigRepository.kt
  - app/src/main/java/com/openkiosk/data/local/KioskPrefs.kt
  - app/src/main/java/com/openkiosk/domain/model/KioskConfig.kt
  - app/src/main/java/com/openkiosk/presentation/viewmodel/SettingsViewModel.kt
  - app/src/main/AndroidManifest.xml
verified-by: |
  Testes de unidade: ./gradlew :app:testDebugUnitTest
  (14 arquivos de teste — `ls app/src/test/java/com/openkiosk/*/*.kt | wc -l` → 14)
  No aparelho, cada caminho tem um rastro proprio no log:
  adb logcat -s ScreenState:D MotionDetection:D KioskWatchdog:D KioskViewModel:D PowerState:D WebViewRecoveryManager:D
  Estado guardado entre mortes do processo:
  adb shell run-as com.openkiosk cat /data/data/com.openkiosk/shared_prefs/open_kiosk_prefs.xml
doc-sig: open-kiosk/ScreenStateManager.kt@gen=3.8#fd215075
---

# Caminhos de execução

Sete jornadas ponta a ponta. Para cada uma: o que dispara, a sequência de chamadas na ordem,
e o que o dono vê na tela.

Vocabulário usado aqui:
- **painel apagado** = brilho da janela em 0 com a tela do sistema ainda ligada. O aplicativo NUNCA
  desliga a tela no nível do sistema: `FLAG_KEEP_SCREEN_ON` é reposto em
  `ScreenStateManager.attachActivity()`, em `transitionToActive()` e em
  `KioskLockManager.enterImmersiveMode()` [confirmado].
- **estado guardado** = `ScreenState` escrito em SharedPreferences (`KioskPrefs.FILE` /
  `KioskPrefs.KEY_SCREEN_STATE`) a cada troca, em `ScreenStateManager.setState()` [confirmado].

---

## 1 · Acordar por movimento

**Gatilho:** alguém se mexe na frente da câmera frontal, com a tela em DIM ou SLEEP.

Sequência:

```
KioskViewModel.applySensorsFor(DIM|SLEEP, cfg)
  └─ startWakeSensors() → startCameraIfNeeded(cfg)
       └─ MotionDetectionManager.start(lifecycleOwner, threshold, onMotion)
            ├─ ProcessCameraProvider.getInstance(context)  [assíncrono]
            ├─ ImageAnalysis: 320x240, YUV_420_888, STRATEGY_KEEP_ONLY_LATEST
            ├─ CameraSelector.LENS_FACING_FRONT
            └─ provider.unbindAll() + bindToLifecycle(...)   → isRunning = true
MotionDetectionAnalyzer.analyze(image)
  ├─ ignora quadro se (agora − último) < pollingIntervalMs   (CONTINUOUS_POLLING_MS = 200 ms)
  ├─ descarta WARMUP_FRAMES = 1 quadro depois do bind (exposição automática convergindo)
  ├─ changedPixelRatio(current, prev)
  └─ se changeRatio > motionThreshold → onMotionDetected()
KioskViewModel.onUserInteraction() → ScreenStateManager.onUserActivity()
  ├─ cancela dimRunnable e sleepRunnable
  ├─ transitionToActive(): FLAG_KEEP_SCREEN_ON + applyBrightness(BRIGHTNESS_OVERRIDE_NONE)
  ├─ setState(ACTIVE) → grava KioskPrefs.KEY_SCREEN_STATE
  └─ postDelayed(dimRunnable, activeTimeoutMs)
```

- `changedPixelRatio()` (em `MotionDetectionAnalyzer.kt`) monta um histograma das diferenças do
  plano Y (-255..255), acha a MEDIANA — que é o deslocamento global de luz — e conta só os pixels
  cujo desvio depois de descontar essa mediana passa de `PIXEL_THRESHOLD = 20`. É isso que impede
  a tela caindo para preto, ou a lâmpada da sala apagando, de virar "pessoa passando" [confirmado].
- Sensibilidade do menu vira número em `MotionSensitivity`: `LOW(0.08)`, `MEDIUM(0.05)`,
  `HIGH(0.03)` [confirmado, `app/src/main/java/com/openkiosk/domain/model/KioskConfig.kt`].
- No Fire HD 8 real: piso de ruído com a sala parada entre 0.00001 e 0.0025; um evento real de
  movimento mediu 0.0823 e acordou a tela, contra o limiar MEDIUM de 0.05 [relatado — medição de
  sessão, não reproduzida agora].
- O quadro de referência só existe DEPOIS do aquecimento; enquanto `previousFrame` é nulo nenhuma
  comparação acontece [confirmado, `MotionDetectionAnalyzer.analyze`].

**Na tela:** o retângulo preto de SLEEP some (`KioskScreen` só o desenha em SLEEP/DEEP_SLEEP), o
brilho volta ao cheio e o WebView é descongelado (`KioskWebView`, `LaunchedEffect(paused, ...)` →
`onResume()` + `resumeTimers()`) [confirmado].

**Sensores irmãos do mesmo caminho:** `SensorWakeManager` (proximidade e chacoalhada) chama o mesmo
`onUserInteraction()`. No aparelho de teste não há sensor de proximidade — `pm list features` só
lista o acelerômetro —, então o ramo de proximidade cai no `Log.w("Proximity sensor not available
on this device")` [relatado quanto à medição; o ramo de log está confirmado em
`SensorWakeManager.start`].

---

## 2 · Ciclo de sono ACTIVE → DIM → SLEEP (→ DEEP_SLEEP)

**Gatilho:** ninguém interage. Os relógios são dois `Runnable` no `Handler` da thread principal.

```
onUserActivity()                    → postDelayed(dimRunnable, activeTimeoutMs)
dimRunnable  → transitionToDim()    → applyBrightness(dimBrightness)
                                      setState(DIM)
                                      postDelayed(sleepRunnable, dimTimeoutMs)
sleepRunnable → transitionToSleep() → applyBrightness(0.0f)
                                      setState(SLEEP)
```

- O brilho de cada estado sai de uma função pura, `brightnessFor(state, dimBrightness)`:
  ACTIVE = `BRIGHTNESS_OVERRIDE_NONE`, DIM = valor configurado, SLEEP e DEEP_SLEEP = `0.0f`
  [confirmado; coberto por `app/src/test/java/com/openkiosk/sleep/BrightnessForStateTest.kt`].
- `configure()` faz `dimBrightness.coerceIn(0.01f, 1.0f)` — o painel nunca fica 100% preto em DIM
  [confirmado].
- DEEP_SLEEP **não** vem de inatividade: vem da janela de horário. `deepSleepCheckRunnable` roda a
  cada `DEEP_SLEEP_CHECK_INTERVAL_MS = 60_000L`; dentro da janela, qualquer estado que não seja
  ACTIVE nem DEEP_SLEEP cai para DEEP_SLEEP; ao SAIR da janela ele chama `onUserActivity()`, e essa
  é a única volta automática do sono profundo [confirmado].
- `transitionToDim()` e `transitionToSleep()` checam `isInDeepSleepWindow()` primeiro e pulam
  direto para DEEP_SLEEP [confirmado].
- A janela cruza a meia-noite quando `deepSleepStartHour > deepSleepEndHour` (ex.: 22→6); senão é
  intervalo do mesmo dia [confirmado, `isInDeepSleepWindow`].

Sensores por estado — decidido em um lugar só, `KioskViewModel.applySensorsFor()` [confirmado]:

```
ACTIVE      → motionDetectionManager.stop() + sensorWakeManager.stop()
DIM         → sensores de acordar ligados, câmera em análise CONTÍNUA (disablePulsedMode)
SLEEP       → na tomada: contínua;  na bateria: enablePulsedMode(cameraPulseIntervalSeconds * 1000)
DEEP_SLEEP  → tudo desligado — só o toque acorda
```

**Na tela:** DIM escurece; SLEEP e DEEP_SLEEP pintam a caixa preta de tela cheia com
`clickable` que chama `onUserInteraction()`, e o gesto da gaveta é bloqueado fora de ACTIVE
(`gesturesEnabled = !showPinDialog && screenState == ScreenState.ACTIVE`) [confirmado,
`KioskScreen.kt`]. O WebView é congelado (`onPause()` + `pauseTimers()`) enquanto dorme.

**Duas travas contra estado fantasma** (o que o quiosque faria de errado sem elas):
- `onUserActivity()` retorna cedo quando não há Activity anexada — sem janela na frente, avançar
  gravaria em disco um SLEEP que nenhum painel viveu, e o vigia leria esse SLEEP como "não
  acenda" [confirmado, comentário e guarda em `ScreenStateManager.onUserActivity`].
- `strandedInDeepSleep(deepSleepEnabled, state)` — desligar a agenda noturna com o painel apagado
  deixaria a tela presa, porque o monitor que a devolveria nunca mais roda. A soltura acontece em
  `configure()` se a janela estiver na frente, ou em `attachActivity()` quando ela voltar
  [confirmado; coberto por `app/src/test/java/com/openkiosk/sleep/ScreenStateDeepSleepExitTest.kt`].

---

## 3 · Relance quando algo cobre o aplicativo

**Gatilho:** a `MainActivity` sai do topo — no Fire, tipicamente a tela de bloqueio com propaganda
da Amazon.

```
MainActivity.onStart()  → isForeground = true
MainActivity.onStop()   → isForeground = false
KioskWatchdogService.tickRunnable  (primeiro tick em TICK_MS = 5_000L)
  se !MainActivity.isForeground:
    consecutiveMisses++
    shouldWakeOnRelaunch(prefs[KEY_SCREEN_STATE])
      → true  (ACTIVE|DIM): startForeground(1, notificação com setFullScreenIntent)
      → false (SLEEP|DEEP_SLEEP): relance silencioso
    startActivity(MainActivity | FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP)
  senão:
    repost da notificação SEM full screen intent; consecutiveMisses = 0
  postDelayed(this, relaunchDelayMs(consecutiveMisses))
```

- `relaunchDelayMs()`: 5 s nas duas primeiras tentativas, depois dobra até o teto de
  `MAX_TICK_MS = 60_000L`. Rodando a fórmula
  `if (n <= 1) 5000 else minOf(5000 shl minOf(n-1, 4), 60000)`: 5 s, 5 s, 10 s, 20 s, 40 s, 60 s,
  60 s… [confirmado por leitura; coberto por
  `app/src/test/java/com/openkiosk/service/WatchdogBackoffTest.kt`].
- `isForeground` é marcado em `onStart`/`onStop`, **não** em `onResume`/`onPause`: um diálogo do
  sistema por cima (o pedido de permissão de câmera) apenas PAUSA a Activity, e marcar no pause
  faria o vigia matar o próprio diálogo em 5 s [confirmado, comentário em `MainActivity`].
- `shouldWakeOnRelaunch()` decide pelo ESTADO GUARDADO, não por `PowerManager.isInteractive` —
  enquanto a janela vive, `FLAG_KEEP_SCREEN_ON` mantém `isInteractive` verdadeiro mesmo dormindo
  [confirmado, comentário em `KioskWatchdogService`].
- O canal `CHANNEL_ID = "kiosk_watchdog"` é criado com `IMPORTANCE_HIGH` (requisito do full screen
  intent), sem som e sem vibração [confirmado].
- `onStartCommand` devolve `START_STICKY`; o serviço é declarado no manifesto com
  `android:foregroundServiceType="specialUse"` [confirmado, `AndroidManifest.xml`].
- Sem `SYSTEM_ALERT_WINDOW` concedida por `appops`, o relance não sai — o bloqueio de início de
  activity em segundo plano do Android 10+ come o `startActivity` [relatado; o manifesto declara a
  permissão e o comentário dele diz exatamente isso — `AndroidManifest.xml`, bloco
  `SYSTEM_ALERT_WINDOW`].
- Prioridade do processo medida no aparelho: `oom_score_adj = 0` (aplicativo comum em segundo
  plano fica em 700) [relatado].
- Android 14+: `manager.canUseFullScreenIntent()` falso vira `Log.w("USE_FULL_SCREEN_INTENT não
  concedida — relance degradado a banner")`. No Fire atual não morde [confirmado como código;
  o "não morde" é [relatado]].

**Na tela:** o quiosque volta por cima da tela de bloqueio (`setShowWhenLocked(true)`,
`setTurnScreenOn(true)`, `FLAG_DISMISS_KEYGUARD` em `onCreate`), e o painel acende só se o estado
guardado era de painel visível.

---

## 4 · Troca de energia (tomada ↔ bateria)

**Gatilho:** o carregador entra ou sai.

```
KioskViewModel.init → powerStateMonitor.start { applySensorsFor(screenState.value, config.value) }
PowerStateMonitor: registerReceiver(ACTION_POWER_CONNECTED, ACTION_POWER_DISCONNECTED)
  onReceive → onChangeCallback.invoke()
KioskViewModel.applySensorsFor(SLEEP, cfg)
  ├─ powerStateMonitor.isPlugged() == true  → motionDetectionManager.disablePulsedMode()
  └─ isPlugged() == false                   → motionDetectionManager.enablePulsedMode(...)
```

- `isPlugged()` lê o intent sticky `ACTION_BATTERY_CHANGED` com `registerReceiver(null, ...)` e
  olha `BatteryManager.EXTRA_PLUGGED` — leitura pontual, sem receiver permanente [confirmado].
- A troca só muda comportamento em SLEEP. Em ACTIVE e DEEP_SLEEP não há câmera; em DIM a análise já
  é contínua [confirmado, `applySensorsFor`].
- O callback é SUBSTITUÍDO a cada `start()` (`onChangeCallback = onChange` antes do
  `if (receiver != null) return`): o monitor é `@Singleton` e sobrevive à recriação da ViewModel;
  sem trocar o aviso, a ViewModel nova ficava sem receber e plugar/desplugar deixava de trocar o
  modo da câmera [confirmado, comentário e código em `PowerStateMonitor`].

Ciclo pulsado, quando na bateria (`MotionDetectionManager`):

```
captureRunnable → analyzer.reset(warmup = false)      // câmera nunca desligou: sem aquecimento
                  analyzer.updatePollingInterval(PULSED_POLLING_MS = 200 ms)
                  imageAnalysis.setAnalyzer(...)
                  postDelayed(sleepRunnable, CAPTURE_WINDOW_MS = 2500 ms)
sleepRunnable  → imageAnalysis.clearAnalyzer()
                  postDelayed(captureRunnable, _pulseIntervalMs)
```

- Entre pulsos a câmera continua ligada (não há `unbind`); o que para é o analisador — quem
  economiza é a CPU, não o sensor [confirmado].
- `enablePulsedMode()` aplica `intervalMs.coerceAtLeast(1_000L)`; o menu oferece 5, 10, 15, 20, 30
  e 60 s [confirmado, `pulseOptions` em `SettingsScreen.kt`].
- `MAX_PULSE_GAP_MS = 4000L` está declarado e **nunca é usado**: `grep -rn "MAX_PULSE_GAP_MS"
  app/src` devolve só a linha da declaração e a do comentário. O teto de 4 s descrito no comentário
  não é aplicado — o intervalo escolhido pelo dono vale inteiro [confirmado por grep].

---

## 5 · Arranque frio

**Gatilho:** processo novo — depois do boot (`BootReceiver`), depois de o vigia relançar, ou
abertura manual.

```
BootReceiver.onReceive(ACTION_BOOT_COMPLETED)
  ├─ packageManager.getLaunchIntentForPackage(...) + FLAG_ACTIVITY_NEW_TASK → startActivity
  └─ KioskWatchdogService.start(context)

MainActivity.attachBaseContext(newBase)
  └─ lê KioskPrefs.KEY_LANGUAGE ANTES da injeção do Hilt; se != "auto", createConfigurationContext
MainActivity.onCreate()
  ├─ setShowWhenLocked(true) / setTurnScreenOn(true) / FLAG_DISMISS_KEYGUARD
  ├─ viewModel.applyLaunchBrightness(this) → ScreenStateManager.applyStoredBrightness()
  ├─ KioskWatchdogService.start(this)
  └─ setContent { KioskScreen(viewModel) }
MainActivity.onStart()   → setTurnScreenOn(true); isForeground = true
MainActivity.onResume()
  ├─ viewModel.attachActivity(this, this)
  │    ├─ ScreenStateManager.attachActivity(): FLAG_KEEP_SCREEN_ON + brilho do estado guardado
  │    ├─ applyKioskLock(activity, realConfig.value)   // realConfig ainda null → só imersivo
  │    ├─ recoveryManager.startAutoRefresh { _needsRefresh = true }
  │    ├─ startConnectivityMonitoring(activity)
  │    ├─ ScreenStateManager.resumeTimers()
  │    └─ applySensorsFor(screenState.value, config.value)
  ├─ lifecycleScope: realConfig.filterNotNull().first() → requestCameraIfNeeded(cfg.wakeOnMotion)
  └─ requestNotificationIfNeeded()
```

Ordem que importa — **estado guardado → brilho → config real libera câmera e lock task**:

- O `ScreenState` inicial é lido do disco na construção do `ScreenStateManager`:
  `ScreenState.valueOf(prefs.getString(KEY_SCREEN_STATE, null) ?: "")` com
  `getOrDefault(ScreenState.ACTIVE)`. O quiosque volta apagado se estava apagado [confirmado].
- O display é SEMPRE alimentado no relance: com o painel realmente desligado a Activity não fica
  visível, a câmera presa ao lifecycle não roda e o toque não chega — nenhum caminho de presença
  sobraria. "Painel claro" é outra coisa e essa segue o estado guardado [confirmado, comentário em
  `MainActivity.onStart`].
- `realConfig` é `MutableStateFlow<KioskConfig?>(null)` — nulo significa "o banco ainda não
  respondeu". Decisão que não pode nascer do valor de fábrica espera ele virar não-nulo
  [confirmado]:
  - **lock task**: o `KioskConfig()` de fábrica tem `lockTaskEnabled = true` e travaria por um
    instante quem desligou; `applyKioskLock` com config nula cai no modo imersivo, e o lock task
    real entra no primeiro `realConfig` recebido pelo `collect`.
  - **câmera**: o de fábrica tem `wakeOnMotion = true` e pediria permissão a quem desligou a
    câmera nos ajustes.
- `configure()` reaplica o brilho (`applyBrightness(brightnessFor(_screenState.value))`) porque
  `attachActivity` rodou antes da config real e usou o `dimBrightness` padrão [confirmado].
- `resumeTimers()` religa o cronômetro a partir do estado atual SEM carimbar presença de usuário —
  voltar do `onResume` não é gente na frente. Ele também repõe o `deepSleepCheckRunnable`, que
  `detachActivity()` tinha retirado [confirmado].
- `cameraPermissionAsked` e `notificationPermissionAsked` garantem no máximo um pedido por
  processo; sem a bandeira, uma negativa definitiva viraria laço de attach/detach a cada `onResume`
  [confirmado].
- Provisionamento por cabo neste parque, porque registro de dono do dispositivo não passa no Fire
  (o Controle Parental da Amazon já é profile owner e há 9 contas) [relatado]:
  ```
  adb shell settings put global stay_on_while_plugged_in 7
  adb shell locksettings set-disabled true
  adb shell cmd package set-home-activity com.openkiosk/.presentation.MainActivity
  adb shell appops set com.openkiosk SYSTEM_ALERT_WINDOW allow    # obrigatória para o relance
  ```
  Sem dono do dispositivo, `KioskLockManager.startLockTask()` cai no `else` e só entra em modo
  imersivo [confirmado, `KioskLockManager.startLockTask`].

**Na tela:** se o aparelho dormia quando o processo morreu, a janela nasce preta e continua preta —
sem o clarão de um ACTIVE de brilho cheio antes de a config chegar.

---

## 6 · Recuperação da página

**Gatilho:** erro de carregamento, morte do renderizador, ou o relógio de recarga automática.

### Erro no quadro principal

```
WebViewClient.onReceivedError(request.isForMainFrame == true)
  → KioskViewModel.onWebViewError() → WebViewRecoveryManager.onError { _needsRefresh = true }
       backoffDelays = [5_000, 15_000, 30_000, 60_000] ms, grudando em 60 s a partir da 5ª
  → KioskScreen: LaunchedEffect(needsRefresh) { refreshKey++; onRefreshConsumed() }
  → key(refreshKey) recria o KioskWebView do zero
WebViewClient.onPageFinished
  → evaluateJavascript(INJECT_JS)     // esconde barra de rolagem e desliga seleção de texto
  → KioskViewModel.onWebViewPageLoaded() → recoveryManager.onSuccess() → retryCount = 0
```

### Renderizador morto

```
WebViewClient.onRenderProcessGone(view, detail)
  ├─ Log.e com detail.didCrash() e detail.rendererPriorityAtExit()
  ├─ webViewRef.value = null
  ├─ (dead.parent as? ViewGroup).removeView(dead)  +  dead.destroy()
  ├─ webViewKey++          // Compose recria a árvore inteira daquela chave
  └─ return true           // contrato do Android; devolver false derruba o app junto
```

### Recarga automática

- `recoveryManager.autoRefreshIntervalMs = cfg.autoRefreshMinutes * 60 * 1000L`, atribuído no
  `collect` da config em `KioskViewModel.init` [confirmado].
- `startAutoRefresh()` retorna cedo quando o intervalo é `<= 0L` e loga "Auto-refresh desativado" —
  o menu manda `0` para "Desativado", e agendar com atraso 0 seria recarga em laço contínuo
  [confirmado; coberto por `app/src/test/java/com/openkiosk/webview/AutoRefreshDisabledTest.kt`].
- Opções do menu: 0, 5, 10, 15, 30, 60 minutos [confirmado, `options` em `AutoRefreshSection`].
- **Buraco na troca do intervalo** [inferido — não reproduzido no aparelho]: `startAutoRefresh()` só
  é chamado em `KioskViewModel.attachActivity()`. Mudar o valor na gaveta grava o campo mas não
  reagenda nada. Duas consequências:
  - de 0 para 30 min: nada é agendado até o próximo `onResume`;
  - de 30 min para 0 ("Desativado"): o `Runnable` já armado continua, e quando dispara ele faz
    `handler.postDelayed(this, autoRefreshIntervalMs)` lendo o campo VIVO, agora 0 — exatamente a
    recarga em laço que a guarda de entrada existe para evitar.

**Na tela:** a página some e volta recarregada; entre a morte do renderizador e a recriação o dono
vê um instante de branco.

---

## 7 · Mudança de ajuste

**Gatilho:** o dono arrasta a gaveta (só em ACTIVE), passa o PIN se estiver ligado, e mexe em um
controle.

```
SettingsDrawerContent (SettingsScreen.kt)
  → SettingsViewModel.updateConfig(key, value)      // chave e valor, ambos String
  → ConfigRepository.updateConfig(key, value) → ConfigDao.set(ConfigEntity(key, value))   [Room]
  → ConfigRepository.observeConfig() reemite KioskConfig
  → KioskViewModel.init { configRepository.observeConfig().collect { cfg -> ... } }
       ├─ screenStateManager.configure(activeTimeoutMs, dimTimeoutMs, dimBrightness,
       │                               deepSleepEnabled, deepSleepStartHour, deepSleepEndHour)
       ├─ recoveryManager.autoRefreshIntervalMs = cfg.autoRefreshMinutes * 60 * 1000L
       ├─ motionDetectionManager.updateConfig(cfg.motionSensitivity.threshold)
       ├─ _realConfig.value = cfg   (+ applyKioskLock na PRIMEIRA vez)
       └─ applySensorsFor(screenState.value, cfg)
```

- O `collect` lê o **repositório direto**, nunca o `stateIn`: o valor de fábrica do placeholder
  (`deepSleepEnabled = false`) decidiria a agenda noturna antes de o Room responder [confirmado,
  comentário em `KioskViewModel.init`].
- O `stateIn` de `config` usa `SharingStarted.Eagerly` porque perdeu o assinante permanente quando
  o `collect` passou a ler o repositório; sem `Eagerly`, `config.value` ficaria preso no valor de
  fábrica [confirmado].
- Reaplicação na hora, sem esperar a tela trocar de estado:
  - sensibilidade → `MotionDetectionManager.updateConfig()` mexe no analisador VIVO
    (`analyzer?.updateThreshold`); sem isso a troca só valeria depois de a câmera religar
    [confirmado].
  - desmarcar "acordar por câmera" → `startCameraIfNeeded` chama `motionDetectionManager.stop()`,
    não apenas deixa de ligar [confirmado].
  - desmarcar proximidade e chacoalhada → o `else` em `startWakeSensors` chama
    `sensorWakeManager.stop()`; sem ele o ouvinte seguiria acordando o painel [confirmado].
- Chaves de config escritas pela gaveta — 14, geradas mecanicamente:
  ```
  $ grep -o 'onUpdate("[a-zA-Z]*"' app/src/main/java/com/openkiosk/presentation/screen/SettingsScreen.kt | sort -u
  onUpdate("activeTimeoutSeconds"
  onUpdate("cameraPulseIntervalSeconds"
  onUpdate("deepSleepEnabled"
  onUpdate("deepSleepEndHour"
  onUpdate("deepSleepStartHour"
  onUpdate("dimBrightnessPercent"
  onUpdate("dimTimeoutSeconds"
  onUpdate("lockTaskEnabled"
  onUpdate("motionSensitivity"
  onUpdate("pin"
  onUpdate("pinEnabled"
  onUpdate("wakeOnMotion"
  onUpdate("wakeOnProximity"
  onUpdate("wakeOnShake"
  ```
  Mais `autoRefreshMinutes`, escrito por `AutoRefreshSection` via
  `viewModel.updateConfig("autoRefreshMinutes", ...)` [confirmado].
- **Fora do Room**: o idioma. `LanguageSection` grava direto em `KioskPrefs` (chave `"language"`) e
  chama `(context as? Activity)?.recreate()` — leitura síncrona em `attachBaseContext` antes do
  Hilt [confirmado].
- **Sem campo na gaveta**: `startUrl` — `grep -n "startUrl"` em `SettingsScreen.kt` não devolve
  nada. O endereço exibido vem da playlist; `startUrl` só aparece como reserva em
  `KioskViewModel` (`item?.url ?: config.value.startUrl`) e em `KioskScreen`
  (`currentUrl.ifBlank { config.startUrl }`) [confirmado por grep].
- **Sem consumidor no runtime**: `cameraPollingIntervalSeconds` existe em `KioskConfig` e em
  `ConfigRepository`, mas `grep -rn "cameraPollingIntervalSeconds" app/src` não acha nenhum leitor
  no caminho de execução — a cadência de amostragem é a constante `CONTINUOUS_POLLING_MS` do
  `MotionDetectionManager` [confirmado por grep].
- Os três `Slider` (tempo até DIM, tempo até SLEEP, brilho de DIM) chamam `onUpdate` em
  `onValueChange`, ou seja, uma escrita no Room por passo do arrasto, cada uma disparando o
  `collect` inteiro [inferido — o efeito de enxurrada não foi medido].
- O PIN é comparado e guardado em texto claro (`PinChangeDialog` compara `input == currentPin`;
  `ConfigRepository` guarda a chave `pin` como `String`) [confirmado]. Padrão de fábrica:
  `pinEnabled = false` [confirmado, `KioskConfig`].

**Na tela:** o efeito é imediato para brilho, tempos, sensibilidade e sensores. Para idioma, a
Activity se recria. Para recarga automática, ver o buraco descrito no caminho 6.

---

## Reconciliação com o histórico

Achados de commits e sessões antigas, conferidos contra o código lido agora:

- "SLEEP usa brilho 0 + camada preta em vez de desligar a tela; sem `lockNow()`, sem WakeLock, sem
  `DevicePolicyManager` no `ScreenStateManager`" — **[confirmado]**: o arquivo não importa nenhum
  dos três; `brightnessFor(SLEEP, _) = 0.0f`; a caixa preta está em `KioskScreen`.
- "`FLAG_KEEP_SCREEN_ON` sempre ativo" — **[confirmado]** em três pontos (`attachActivity`,
  `transitionToActive`, `KioskLockManager.enterImmersiveMode`).
- "Detecção por câmera passa a rodar em DIM/SLEEP" — **[confirmado]**, `applySensorsFor`.
- "Movimento medido depois de descontar o deslocamento global de luz (mediana das diferenças)" —
  **[confirmado]**, `changedPixelRatio`.
- "Limiar por pixel 15 → 20" — **[confirmado]**: `PIXEL_THRESHOLD = 20`. O valor 15 de commits
  anteriores está superado.
- "Deep sleep ativado por faixa de horário configurável, não por inatividade" — **[confirmado]**,
  `deepSleepCheckRunnable` + `isInDeepSleepWindow`.
- "Modo quiosque: dono do dispositivo com reserva imersiva" — **[confirmado]** no código
  (`KioskLockManager.startLockTask` → `else → enterImmersiveMode`). Que o registro de dono não
  passe no Fire por causa do Controle Parental da Amazon é **[relatado]**.
- "Acordar por proximidade e por acelerômetro" — **[confirmado]** como código
  (`SensorWakeManager`); no aparelho testado só o acelerômetro existe **[relatado]**.
- "PIN desligado por padrão" — **[confirmado]**, `KioskConfig.pinEnabled = false`.
- "Idioma detectado do sistema, com troca manual lida em `attachBaseContext` antes do Hilt" —
  **[confirmado]**.

### Invalidação proposta

- Achado `3b49b2a5051cb88a` ("Add energy management: pulsed camera in SLEEP, scheduled deep
  sleep") afirma: *"Pulsed camera uses clearAnalyzer/setAnalyzer to preserve previousFrame between
  pulses"*. O código **contradiz** a parte do `previousFrame`.
  Evidência: `MotionDetectionManager.captureRunnable` chama `analyzer.reset(warmup = false)` no
  início de cada pulso, e `MotionDetectionAnalyzer.reset()` faz `previousFrame = null`. O quadro de
  referência é DESCARTADO a cada pulso; a primeira amostra da janela vira a nova referência.
  O que de fato se preserva entre pulsos é a instância do analisador e a câmera ligada — não há
  `bind`/`unbind`, e por isso o `warmup` é dispensado. A afirmação correta é "sem sobrecusto de
  bind/unbind", não "preserva o quadro anterior".

---

## Pendências

- [TODO: sem cobertura declarada] Trocar o intervalo de recarga automática pela gaveta não reagenda
  o relógio (ver caminho 6). Falta confirmar no aparelho se o caso 30 min → "Desativado" realmente
  entra em recarga contínua, e onde a correção deve morar (`WebViewRecoveryManager.startAutoRefresh`
  chamado de novo no `collect` da config, ou guarda no repost do `Runnable`).
- [TODO: sem cobertura declarada] `MAX_PULSE_GAP_MS` está declarado e não é usado; o comentário
  que o acompanha descreve um teto que o código não aplica. Decidir entre aplicar ou remover.
- [TODO: sem cobertura declarada] `cameraPollingIntervalSeconds` persiste no Room sem leitor no
  caminho de execução e sem campo na gaveta.
