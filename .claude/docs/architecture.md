---
generated: 2026-08-15
generated-commit: 628b8ea
project: open-kiosk
scope: [app/src/main/java/com/openkiosk/sleep/ScreenStateManager.kt, app/src/main/java/com/openkiosk/presentation/viewmodel/KioskViewModel.kt, app/src/main/java/com/openkiosk/service/KioskWatchdogService.kt, app/src/main/java/com/openkiosk/sensors/MotionDetectionManager.kt, app/src/main/java/com/openkiosk/kiosk/KioskLockManager.kt, app/src/main/java/com/openkiosk/domain/PlaylistManager.kt, app/src/main/java/com/openkiosk/di/DatabaseModule.kt, app/src/main/java/com/openkiosk/presentation/MainActivity.kt, app/src/main/java/com/openkiosk/power/PowerStateMonitor.kt, app/src/main/java/com/openkiosk/presentation/screen/KioskScreen.kt, app/src/main/java/com/openkiosk/receiver/BootReceiver.kt, app/src/main/java/com/openkiosk/receiver/KioskDeviceAdminReceiver.kt, app/src/main/java/com/openkiosk/OpenKioskApplication.kt, app/src/main/AndroidManifest.xml, app/src/main/java/com/openkiosk/domain/model/ScreenState.kt, app/src/main/java/com/openkiosk/domain/model/KioskConfig.kt, app/src/main/java/com/openkiosk/data/local/KioskPrefs.kt, app/src/main/java/com/openkiosk/webview/WebViewRecoveryManager.kt, app/src/main/java/com/openkiosk/data/repository/ConfigRepository.kt, app/src/main/java/com/openkiosk/data/local/AppDatabase.kt]
verified-by: "./gradlew testDebugUnitTest (14 arquivos de teste: ls app/src/test/java/com/openkiosk/*/*.kt | wc -l = 14); no aparelho, adb logcat -s ScreenState:D KioskViewModel:D MotionDetection:D KioskWatchdog:D PowerState:D mostra cada transicao e cada decisao de sensor"
doc-sig: open-kiosk/ScreenStateManager.kt@gen=3.8#4091485e
---

# Arquitetura — OPEN-KIOSK

O que este doc cobre: as camadas e como se ligam, a maquina de estado de tela, a injecao de
dependencia, quem chama quem, e as decisoes de arquitetura com o motivo de cada uma.
Os sete caminhos de execucao passo a passo ficam no doc de caminhos de execucao; aqui esta
so a forma das pecas.

---

## 1. Camadas

Quatro camadas, de baixo para cima. Cada nome abaixo e um pacote real sob
`app/src/main/java/com/openkiosk/`.

```
data/        Room (AppDatabase, ConfigDao, PlaylistDao) + SharedPreferences (KioskPrefs)
             -> repositorios: ConfigRepository, PlaylistRepository

domain/      modelos puros: KioskConfig, PlaylistItem, ScreenState
             + PlaylistManager (rotacao de enderecos)

"gerentes"   singletons que falam com o Android e nao sabem nada de tela:
             sleep/ScreenStateManager, sensors/MotionDetectionManager,
             sensors/SensorWakeManager, kiosk/KioskLockManager,
             power/PowerStateMonitor, webview/WebViewRecoveryManager

presentation/ MainActivity (uma so) -> KioskViewModel -> KioskScreen (Compose)
service/      KioskWatchdogService (processo vivo, fora da activity)
receiver/     BootReceiver, KioskDeviceAdminReceiver
```

- [confirmado] Uma unica Activity: `AndroidManifest.xml` declara so
  `.presentation.MainActivity`, com `android:launchMode="singleTask"` e
  `android:screenOrientation="landscape"`.
- [confirmado] Nao ha camada de rede propria — o conteudo e uma pagina web dentro do WebView
  (`presentation/component/KioskWebView.kt`, montado por `KioskScreen`). O unico monitor de
  rede e o `ConnectivityManager.NetworkCallback` criado em
  `KioskViewModel :: startConnectivityMonitoring`.
- [confirmado] `AndroidManifest.xml` traz `android:usesCleartextTraffic="true"` e
  `android:networkSecurityConfig="@xml/network_security_config"` — quiosque aponta para
  painel interno em HTTP simples com frequencia.

---

## 2. Injecao de dependencia (Hilt)

- [confirmado] Raiz: `OpenKioskApplication` anotada `@HiltAndroidApp`, corpo vazio — o
  arquivo tem 7 linhas e nada alem da anotacao.
- [confirmado] Unico modulo Hilt do projeto e `di/DatabaseModule.kt`, `@InstallIn(SingletonComponent::class)`:

```kotlin
Room.databaseBuilder(context, AppDatabase::class.java, "openkiosk.db").build()
```

  - `provideDatabase` e `@Singleton`; `provideConfigDao` e `providePlaylistDao` nao sao —
    entregam `db.configDao()` / `db.playlistDao()`, que ja vem do banco singleton.
  - [confirmado] `AppDatabase` esta em `version = 1`, `exportSchema = false`, sem nenhuma
    `Migration` registrada.
- [confirmado] Todo o resto se injeta por construtor com `@Inject constructor` + `@Singleton`
  na propria classe (padrao "sem modulo"). Os que carregam `@Singleton` hoje, gerado por
  `grep -rn "@Singleton" --include="*.kt" app/src/main/java -l`:

```
power/PowerStateMonitor.kt
di/DatabaseModule.kt
sensors/MotionDetectionManager.kt
sleep/ScreenStateManager.kt
kiosk/KioskLockManager.kt
data/repository/PlaylistRepository.kt
data/repository/ConfigRepository.kt
sensors/SensorWakeManager.kt
domain/PlaylistManager.kt
```

- [confirmado] `KioskViewModel` e `@HiltViewModel`; recebe sete dependencias no construtor
  (`ConfigRepository`, `ScreenStateManager`, `KioskLockManager`, `MotionDetectionManager`,
  `SensorWakeManager`, `PowerStateMonitor`, `PlaylistManager`). `PlaylistManager` e o unico
  publico (`val playlistManager`), porque a tela precisa chamar `next()`/`previous()`.
- [confirmado] `WebViewRecoveryManager` NAO e injetado: nasce como campo da ViewModel
  (`val recoveryManager = WebViewRecoveryManager()`). E o unico gerente cuja vida acompanha
  a ViewModel, nao o processo.

### Por que os gerentes sao singleton e a ViewModel nao

- [inferido] Camera, sensores, brilho e estado de tela precisam sobreviver a recriacao da
  activity (troca de idioma chama `recreate`, o vigia relanca a MainActivity). Se fossem
  presos a ViewModel, cada recriacao perderia o registro.
- [confirmado] Esse mesmo fato ja mordeu uma vez e o comentario ficou no codigo:
  `PowerStateMonitor :: onChangeCallback` e `@Volatile` e e SEMPRE trocado em `start()`,
  com o comentario "O singleton sobrevive a recriacao da activity: (...) plugar/desplugar
  deixava de trocar o modo da camera para sempre".

---

## 3. Maquina de estado de tela

- [confirmado] `domain/model/ScreenState.kt` declara QUATRO estados, nesta ordem:
  `ACTIVE`, `DIM`, `SLEEP`, `DEEP_SLEEP`.
- [confirmado] Dono unico da maquina: `sleep/ScreenStateManager.kt`. Ele publica
  `screenState: StateFlow<ScreenState>` e ninguem mais escreve estado.

### Decisao central: dormir e escurecer, nunca desligar o painel

- [confirmado] `ScreenStateManager :: brightnessFor(state, dimBrightness)` e uma funcao pura:

```kotlin
ScreenState.ACTIVE -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
ScreenState.DIM -> dimBrightness
ScreenState.SLEEP, ScreenState.DEEP_SLEEP -> 0.0f
```

- [confirmado] `FLAG_KEEP_SCREEN_ON` e adicionada em `attachActivity`, em
  `transitionToActive` e tambem em `KioskLockManager :: enterImmersiveMode`.
- [confirmado] O preto de SLEEP tem duas metades: brilho `0.0f` no `WindowManager` e o
  retangulo preto de `KioskScreen` (`Box` com `background(Color.Black)`), que fica visivel
  quando `screenState == SLEEP || screenState == DEEP_SLEEP` e acorda a tela no toque
  (`clickable { viewModel.onUserInteraction() }`).
- Motivo, em lingua de gente: se o painel apagasse de verdade, a activity deixaria de ficar
  visivel — e ai a camera presa ao ciclo de vida para, o toque nao chega e nao sobra nenhum
  jeito de perceber que alguem chegou. O comentario de `MainActivity :: onStart` diz isso
  com todas as letras. [confirmado]
- [confirmado] `ScreenStateManager` nao importa `DevicePolicyManager`, `PowerManager` nem
  `WakeLock` — o arquivo inteiro nao tem esses simbolos. Confirma o commit
  "remove lockNow(), WakeLock, DevicePolicyManager dependency".

### Estado sobrevive a morte do processo

- [confirmado] O estado inicial e lido de disco no construtor:

```kotlin
ScreenState.valueOf(prefs.getString(KioskPrefs.KEY_SCREEN_STATE, null) ?: "")
```

  com `.getOrDefault(ScreenState.ACTIVE)`.
- [confirmado] `ScreenStateManager :: setState` grava em `SharedPreferences` a cada
  transicao. O nome do arquivo e da chave vive so em `data/local/KioskPrefs.kt`
  (`FILE = "open_kiosk_prefs"`, `KEY_SCREEN_STATE = "screen_state"`) — o proprio comentario
  do arquivo explica: quem escreve e o `ScreenStateManager`, quem le e o vigia, e nome
  repetido em dois arquivos faria o vigia ler vazio e acender o painel de madrugada.
- [confirmado] `applyStoredBrightness(activity)` e chamada por
  `KioskViewModel :: applyLaunchBrightness`, invocada em `MainActivity :: onCreate` ANTES do
  `setContent` — o painel ja nasce preto se o app dormia quando morreu.

### Guardas que existem por bug real

- [confirmado] `onUserActivity()` retorna cedo quando `activityRef?.get() == null`. Motivo no
  comentario: sem janela na frente a maquina gravaria em disco um `ACTIVE -> DIM -> SLEEP`
  que nenhum painel viveu, e o vigia leria esse SLEEP fantasma como "nao acenda".
- [confirmado] `detachActivity()` cancela os tres `Runnable` (`dimRunnable`, `sleepRunnable`,
  `deepSleepCheckRunnable`); `resumeTimers()` rearma a partir do estado atual, e repoe o
  monitor de sono profundo se `deepSleepEnabled` — sem isso a saida da janela noturna, unico
  caminho de volta do `DEEP_SLEEP`, nunca rodaria.
- [confirmado] `strandedInDeepSleep(deepSleepEnabled, state)` e funcao pura de nivel de
  arquivo (`!deepSleepEnabled && state == DEEP_SLEEP`), consultada em DOIS pontos:
  `configure()` (so solta se ja ha janela anexada) e `attachActivity()` (solta quando a
  janela volta). Sem isso, desligar a agenda noturna com o painel apagado deixaria o
  quiosque preto para sempre.
- [confirmado] `isInDeepSleepWindow()` trata a virada de meia-noite: se
  `deepSleepStartHour > deepSleepEndHour`, vale `hour >= start || hour < end`.
- [confirmado] O monitor de sono profundo e um `Runnable` reagendado a cada
  `DEEP_SLEEP_CHECK_INTERVAL_MS = 60_000L`.

---

## 4. Quem chama quem

Fluxo de config (uma direcao so: banco -> ViewModel -> gerentes):

```
ConfigDao.observeAll()
  -> ConfigRepository.observeConfig(): Flow<KioskConfig>
  -> KioskViewModel.init { configRepository.observeConfig().collect { cfg -> ... } }
       -> screenStateManager.configure(activeTimeoutMs, dimTimeoutMs, dimBrightness,
                                       deepSleepEnabled, deepSleepStartHour, deepSleepEndHour)
       -> recoveryManager.autoRefreshIntervalMs = cfg.autoRefreshMinutes * 60 * 1000L
       -> motionDetectionManager.updateConfig(cfg.motionSensitivity.threshold)
       -> _realConfig.value = cfg   (na 1a vez: applyKioskLock)
       -> applySensorsFor(screenState.value, cfg)
```

Fluxo de estado de tela (uma direcao so: gerente -> ViewModel -> sensores e tela):

```
ScreenStateManager._screenState
  -> KioskViewModel.screenState  (repassado sem transformar)
       -> collect -> applySensorsFor(state, config.value)
       -> KioskScreen.collectAsState() -> overlay preto, gesto da gaveta, WebView pausado
```

Fluxo de presenca (sensor -> um unico ponto de entrada):

```
MotionDetectionManager.onMotion  \
SensorWakeManager.onWake          >-- KioskViewModel.onUserInteraction()
KioskScreen toque / KioskWebView /        -> ScreenStateManager.onUserActivity()
```

- [confirmado] `KioskViewModel :: applySensorsFor` carrega o comentario "Unico lugar que
  decide quais sensores rodam em cada estado de tela" e e o unico chamador de
  `startWakeSensors` / `motionDetectionManager.stop()` nas quatro pernas do `when`.
- [confirmado] `applySensorsFor` e chamada de QUATRO lugares em `KioskViewModel`: no collect
  de config, no callback de `powerStateMonitor.start`, no collect de `screenState`, e no fim
  de `attachActivity`. As duas ultimas existem porque `collect` nao re-emite quando o estado
  nao mudou.

### O vigia nao chama a ViewModel — le uma bandeira e um arquivo

- [confirmado] `KioskWatchdogService :: tickRunnable` le `MainActivity.isForeground`
  (companion `@Volatile var`, marcado em `onStart`/`onStop`, com setter privado).
- [confirmado] Marcado em `onStart`/`onStop` e nao em `onResume`/`onPause` de proposito: o
  comentario diz que um dialogo do sistema por cima (o pedido de permissao de camera) apenas
  PAUSA a activity, e relancar ali mataria o dialogo.
- [confirmado] O vigia le o estado de tela do disco, nao da memoria:
  `shouldWakeOnRelaunch(KioskPrefs.of(this).getString(KioskPrefs.KEY_SCREEN_STATE, null))`,
  que delega para `wakesScreenOnLaunch(state)` — funcao publica declarada em
  `sleep/ScreenStateManager.kt`. Essa e a unica costura entre o pacote `service` e o pacote
  `sleep`, e ela existe nos dois lados hoje.
- [confirmado] Motivo documentado no proprio KDoc de `shouldWakeOnRelaunch`:
  `PowerManager.isInteractive` nao serve, porque enquanto a janela vive o
  `FLAG_KEEP_SCREEN_ON` a mantem verdadeira mesmo dormindo, e quando o processo morre ela
  fica falsa justamente no caso em que o relance precisa acontecer.

---

## 5. Decisoes de arquitetura, com o motivo

### 5.1 Config real vs. valor de fabrica (`realConfig` nulo)

- [confirmado] `KioskViewModel` tem DOIS fluxos de config:
  - `config: StateFlow<KioskConfig>` = `stateIn(viewModelScope, SharingStarted.Eagerly, KioskConfig())`
  - `_realConfig: MutableStateFlow<KioskConfig?>` que comeca `null`
- Motivo (comentario colado no campo): "Nulo = 'o banco ainda nao respondeu'. Decisao que nao
  pode nascer do valor de fabrica (pedir camera, entrar em lock task) espera este virar
  nao-nulo."
- [confirmado] Os defaults de `KioskConfig` que tornam isso necessario: `wakeOnMotion = true`
  e `lockTaskEnabled = true`. Sem a espera, o app pediria camera a quem a desligou e travaria
  por um instante quem desativou a trava.
- [confirmado] Dois consumidores esperam o nao-nulo: `MainActivity :: onResume` faz
  `viewModel.realConfig.filterNotNull().first()` antes de `requestCameraIfNeeded`; e
  `KioskViewModel :: attachActivity` chama `applyKioskLock(activity, realConfig.value)` —
  com `null` cai no ramo imersivo.
- [confirmado] O `collect` do `init` le `configRepository.observeConfig()` DIRETO, nunca o
  `stateIn`, pelo mesmo motivo (comentario no lugar).
- [confirmado] O `stateIn` e `SharingStarted.Eagerly` porque o collect direto tirou dele o
  assinante permanente; sem `Eagerly`, `config.value` ficaria preso no valor de fabrica.

### 5.2 Trava de quiosque: dono do aparelho com queda para imersivo

- [confirmado] `KioskLockManager :: startLockTask` bifurca em `isDeviceOwner()`:
  - ramo dono: `setLockTaskPackages`, `setLockTaskFeatures(LOCK_TASK_FEATURE_NONE)`,
    `setKeyguardDisabled(true)`, `clearPackagePersistentPreferredActivities`,
    `addPersistentPreferredActivity` com `IntentFilter(ACTION_MAIN)` + `CATEGORY_HOME` +
    `CATEGORY_DEFAULT`, e por fim `activity.startLockTask()`.
  - senao: `enterImmersiveMode(activity)` — esconde barras via `WindowInsetsController` no
    Android 11+ e `systemUiVisibility` abaixo disso.
- [confirmado nesta sessao, no aparelho] No Amazon Fire HD 8 (KFRAPWI) o registro de dono do
  aparelho NAO passa: `com.amazon.parentalcontrols` ja e profile owner e ha 9 contas. Logo o
  ramo efetivo no hardware alvo e o imersivo — `isDeviceOwner()` retorna falso e todo o bloco
  de `DevicePolicyManager` fica sem rodar. E "implementado, inativo neste aparelho".
- [confirmado] `KioskDeviceAdminReceiver :: onEnabled` tambem so age sob
  `isDeviceOwnerApp` — mesma inatividade neste aparelho.
- [confirmado nesta sessao] O provisionamento que substitui o dono e por cabo:
  `stay_on_while_plugged_in=7`, `locksettings set-disabled true`,
  `cmd package set-home-activity`, e `appops SYSTEM_ALERT_WINDOW allow` — esta ultima
  OBRIGATORIA, sem ela o vigia nao consegue relancar. A permissao correspondente esta
  declarada: `android.permission.SYSTEM_ALERT_WINDOW` no `AndroidManifest.xml`, com o
  comentario explicando que ela isenta o relance do bloqueio de inicio em segundo plano.
- [confirmado] `MainActivity` declara `CATEGORY_HOME` + `CATEGORY_DEFAULT` no `intent-filter`
  — e isso que permite ao `cmd package set-home-activity` apontar para o app.

### 5.3 O vigia: servico em primeiro plano com recuo progressivo

- [confirmado] `KioskWatchdogService` e `Service` com `onStartCommand` retornando
  `START_STICKY`, iniciado por `KioskWatchdogService.start(context)` de dois lugares:
  `MainActivity :: onCreate` e `BootReceiver :: onReceive` (sob `ACTION_BOOT_COMPLETED`).
- [confirmado] Canal `CHANNEL_ID = "kiosk_watchdog"` criado com `IMPORTANCE_HIGH`,
  `setSound(null, null)` e `enableVibration(false)` — importancia alta e requisito do full
  screen intent, e o silencio evita barulho no quiosque.
- [confirmado] Dois tiros por tique: repostar a notificacao com `setFullScreenIntent` (que faz
  o SISTEMA abrir a activity, caminho isento do bloqueio de inicio em segundo plano) e
  `startActivity(mainActivityIntent())` com `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_SINGLE_TOP`.
- [confirmado] Recuo progressivo em funcao pura testavel:

```kotlin
internal fun relaunchDelayMs(consecutiveMisses: Int): Long =
    if (consecutiveMisses <= 1) TICK_MS
    else minOf(TICK_MS shl minOf(consecutiveMisses - 1, 4), MAX_TICK_MS)
```

  com `TICK_MS = 5_000L` e `MAX_TICK_MS = 60_000L`. Motivo no KDoc: repetir identico a cada
  5s so faz a notificacao piscar por cima do app que cobriu o quiosque, para sempre.
- [confirmado] Quando a activity volta e havia falhas, o servico reposta a notificacao com
  `fullScreen = false` — senao o aviso de tela cheia fica pendurado e um repost futuro abre a
  tela sem motivo.
- [confirmado] `AndroidManifest.xml` declara o servico com
  `android:foregroundServiceType="specialUse"` e a `<property>`
  `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` com valor `kiosk`.
- [confirmado nesta sessao, no aparelho] O efeito medido do servico em primeiro plano:
  `oom_score_adj = 0` (app comum em segundo plano fica em 700) — o sistema poupa este
  processo na hora de matar por memoria.
- [confirmado] Aviso plantado para o futuro: em `onCreate`, se `SDK_INT >= 34` e
  `!manager.canUseFullScreenIntent()`, o servico loga "USE_FULL_SCREEN_INTENT nao concedida
  — relance degradado a banner". O comentario diz que no Fire atual isso nao morde.

### 5.4 Camera: continua na tomada, pulsada na bateria

- [confirmado] `KioskViewModel :: applySensorsFor`, perna `SLEEP`:
  `if (powerStateMonitor.isPlugged()) motionDetectionManager.disablePulsedMode()` senao
  `enablePulsedMode(cfg.cameraPulseIntervalSeconds * 1000L)`. O comentario chama isso de
  "decisao do dono": na tomada, analise continua sem vao cego; na bateria, pulso, aceitando
  perder quem passa rapido.
- [confirmado] `PowerStateMonitor :: isPlugged` le a intent pegajosa de bateria sem registrar
  receiver (`registerReceiver(null, IntentFilter(ACTION_BATTERY_CHANGED))` e
  `EXTRA_PLUGGED != 0`); `start()` registra `ACTION_POWER_CONNECTED` e
  `ACTION_POWER_DISCONNECTED` uma unica vez.
- [confirmado] O pulso NAO desliga a camera: `sleepRunnable` chama
  `imageAnalysis?.clearAnalyzer()` e `captureRunnable` chama `setAnalyzer(...)` de volta.
  Nao ha `unbind` entre pulsos — so em `stop()` (`cameraProvider?.unbindAll()`).
- [confirmado] Cada pulso comeca com `analyzer.reset(warmup = false)`, com o comentario
  "a camera nunca foi desligada entre pulsos (so o analyzer): sem aquecimento".
- [confirmado] Constantes de `MotionDetectionManager.kt`, com o proprio comentario dizendo
  que sao "knob de campo":

```kotlin
CAPTURE_WINDOW_MS = 2500L
PULSED_POLLING_MS = 200L
CONTINUOUS_POLLING_MS = 200L
```

- [confirmado] `enablePulsedMode` aplica `intervalMs.coerceAtLeast(1_000L)` — o piso e 1s, e o
  comentario registra que "o intervalo escolhido pelo dono MANDA", depois de um teto antigo
  de 4s engolir os valores do menu em silencio.
- [confirmado] Guarda de corrida: `MotionDetectionManager` mantem `private var generation` e
  aborta o bind assincrono se `myGeneration != generation` — protege contra `stop()` chamado
  enquanto o `ProcessCameraProvider` ainda carregava.
- [confirmado] Camera frontal fixa (`requireLensFacing(LENS_FACING_FRONT)`), resolucao alvo
  `Size(320, 240)`, `STRATEGY_KEEP_ONLY_LATEST`, formato `OUTPUT_IMAGE_FORMAT_YUV_420_888`.
- [confirmado] `updateConfig(threshold)` reaplica a sensibilidade no analyzer VIVO —
  o comentario diz que sem isso trocar nas configuracoes so valeria depois de a camera
  religar. Os valores vem de `MotionSensitivity` em `KioskConfig.kt`:
  `LOW(0.08)`, `MEDIUM(0.05)`, `HIGH(0.03)`.
- [confirmado nesta sessao, no aparelho] Piso de ruido medido com a sala parada:
  changeRatio de 0.00001 a 0.0025; o limiar MEDIUM = 0.05 fica ~20x acima do teto do ruido.
  Um evento real de movimento mediu 0.0823 e acordou a tela.

### 5.5 Sensores de proximidade e chacoalhada: implementados, sem hardware aqui

- [confirmado] `KioskViewModel :: startWakeSensors` so chama `sensorWakeManager.start(...)` se
  `cfg.wakeOnProximity || cfg.wakeOnShake`; senao chama `sensorWakeManager.stop()`. O
  comentario explica o `else`: sem ele, desligar o sensor na gaveta deixaria o ouvinte
  acordando o painel ate a tela trocar de estado.
- [confirmado] `AndroidManifest.xml` declara `android.hardware.sensor.proximity` e
  `android.hardware.sensor.accelerometer` com `android:required="false"` — o app instala em
  aparelho sem eles.
- [confirmado nesta sessao, no aparelho] O Fire HD 8 NAO tem sensor de proximidade
  (`pm list features` so lista o acelerometro). O caminho de proximidade e "implementado,
  inativo neste aparelho"; a camera e o unico detector de presenca que funciona.

### 5.6 Idioma lido antes do Hilt

- [confirmado] `MainActivity :: attachBaseContext` le `KioskPrefs.KEY_LANGUAGE` de
  `SharedPreferences` de forma sincrona e, se diferente de `"auto"`, monta
  `createConfigurationContext(config)` com o `Locale` escolhido.
- Motivo [inferido do codigo]: `attachBaseContext` roda antes de qualquer injecao, entao o
  idioma nao pode vir do Room — dai a preferencia em arquivo, nao no banco. E a segunda razao
  de `KioskPrefs` existir ao lado do Room.

### 5.7 Recuperacao da pagina fora da tela

- [confirmado] `WebViewRecoveryManager` (campo da ViewModel) guarda
  `backoffDelays = longArrayOf(5_000L, 15_000L, 30_000L, 60_000L)` e satura no ultimo valor.
- [confirmado] `startAutoRefresh` retorna cedo quando `autoRefreshIntervalMs <= 0L`, com o
  comentario: "Desativado" no menu chega como 0, e agendar com atraso 0 seria recarga em laco
  continuo — o oposto de desligar.
- [confirmado] A tela nao conhece nada disso: a ViewModel so vira `_needsRefresh` para `true`,
  e `KioskScreen` reage incrementando `refreshKey`, que reconstroi o `KioskWebView` dentro de
  `androidx.compose.runtime.key(refreshKey)`.

### 5.8 Rotacao de enderecos

- [confirmado] `PlaylistManager` e `@Singleton`, mas nao tem escopo proprio: recebe o escopo
  de fora em `start(scope: CoroutineScope)`. `KioskViewModel :: init` passa `viewModelScope`,
  e `onCleared()` chama `playlistManager.stop()`.
- [confirmado] Dois `Job` separados: `collectionJob` (observa o banco) e `rotationJob` (o laco
  de `delay(current.durationSeconds * 1000L)`). Qualquer mudanca na lista zera `currentIndex`
  e reinicia a rotacao.
- [confirmado] `startRotation` retorna cedo com `items.size <= 1` — um endereco so nao gira.
- [confirmado] `startRotation` e `restartRotation` carregam o MESMO laco `while(true)` copiado
  (mesmas cinco linhas). Codigo duplicado que ja estava la; fica registrado, nao mexido.

---

## 6. Reconciliacao dos achados historicos

- [confirmado] "SLEEP usa brilho 0 + retangulo preto, sem `lockNow`" — visivel em
  `brightnessFor` e no `Box` preto de `KioskScreen`.
- [confirmado] "`FLAG_KEEP_SCREEN_ON` sempre ativa" — `attachActivity`,
  `transitionToActive`, `enterImmersiveMode`.
- [confirmado] "`KioskViewModel` guarda referencia de `LifecycleOwner` e tem
  `onCameraPermissionGranted()`" — `lifecycleOwnerRef: WeakReference<LifecycleOwner>` e a
  funcao publica de mesmo nome existem.
- [confirmado] "Sono profundo por faixa de horario configuravel, nao por inatividade" —
  `isInDeepSleepWindow()` compara `Calendar.HOUR_OF_DAY` com `deepSleepStartHour` /
  `deepSleepEndHour`; `transitionToDim` e `transitionToSleep` desviam para
  `transitionToDeepSleep()` quando a janela esta aberta.
- [confirmado] "DEEP_SLEEP desliga tudo, acorda so no toque" — perna `DEEP_SLEEP` de
  `applySensorsFor` chama `motionDetectionManager.stop()` e `sensorWakeManager.stop()`; o
  toque chega pelo `Box` preto de `KioskScreen`.
- [confirmado] "Limiar por pixel 15 -> 20" — `MotionDetectionAnalyzer.kt` tem
  `private const val PIXEL_THRESHOLD = 20` (lido por grep, arquivo nao lido inteiro).
- [confirmado] "Deteccao descarta os primeiros quadros apos religar a camera (aquecimento)" —
  o parametro existe na costura que este doc ve: `analyzer.reset(warmup = false)` em
  `captureRunnable` e `analyzer.reset()` em `disablePulsedMode`.
- [confirmado] "i18n lido em `attachBaseContext` antes da injecao" — secao 5.6.
- [confirmado] "Trafego HTTP simples liberado por `network_security_config.xml`" — atributos
  no `AndroidManifest.xml`.
- [relatado] "Min SDK 28, target 34, pacote `com.openkiosk`" — as tres linhas aparecem em
  `app/build.gradle.kts` (`minSdk = 28`, `targetSdk = 34`, `compileSdk = 34`), arquivo que li
  so por grep, nao inteiro.
- [relatado] "Config so pela gaveta local, sem servidor de administracao remota" — nenhum dos
  arquivos desta fatia abre socket ou cliente HTTP proprio, mas isso e ausencia, nao prova
  positiva; quem confirma e o doc de durabilidade.

### Propostas de invalidacao

- "Pulsed camera uses clearAnalyzer/setAnalyzer to preserve previousFrame between pulses"
  (commit 3b49b2a5051cb88a) — CONTRADITO em parte por
  `MotionDetectionManager.kt :: captureRunnable`, que chama `a.reset(warmup = false)` no
  inicio de cada pulso: o quadro de referencia e DESCARTADO, nao preservado. O que se
  preserva e o bind da camera. A metade "sem bind/unbind" segue verdadeira.
- "Smart sleep/wake state machine (ACTIVE/DIM/SLEEP)" (commit inicial 1965b7af6434c6b8) —
  CONTRADITO por `domain/model/ScreenState.kt`, que declara quatro constantes, com
  `DEEP_SLEEP` incluida.

---

## 7. Lacunas conhecidas

- [TODO: sem cobertura declarada] Nao ha como salvar nem restaurar a configuracao em arquivo.
  `grep -rn "CreateDocument\|OpenDocument\|writeText\|Environment.getExternal" --include="*.kt" app/src/main/java`
  nao retorna nada. A lista de enderecos e os ajustes so existem dentro de `openkiosk.db` no
  aparelho; trocar de tablet exige redigitar tudo a mao. O detalhe da pendencia esta no doc de
  durabilidade.
- [TODO: verificar] `android.permission.WAKE_LOCK` continua declarada no
  `AndroidManifest.xml`, mas nenhum arquivo desta fatia adquire `WakeLock` — o commit
  2f5e2667d858924d removeu esse uso do `ScreenStateManager`. Falta varrer o resto do projeto
  antes de tirar a declaracao.
- RESOLVIDO nesta rodada: `cameraPollingIntervalSeconds` (persistido sem nenhum leitor) e
  `MAX_PULSE_GAP_MS` (declarada e nunca referenciada) eram sobras de refatoracao e foram
  REMOVIDOS do codigo. `grep -rn "cameraPollingIntervalSeconds\|MAX_PULSE_GAP_MS" app/src`
  nao retorna nada. [confirmado]
