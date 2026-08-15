---
generated: 2026-08-15
project: open-kiosk
scope: [app/src/main/java/com/openkiosk/sleep/ScreenStateManager.kt, app/src/main/java/com/openkiosk/sensors/MotionDetectionAnalyzer.kt, app/src/main/java/com/openkiosk/sensors/MotionDetectionManager.kt, app/src/main/java/com/openkiosk/sensors/SensorWakeManager.kt, app/src/main/java/com/openkiosk/webview/WebViewRecoveryManager.kt, app/src/main/java/com/openkiosk/presentation/component/KioskWebView.kt, app/src/main/java/com/openkiosk/presentation/component/PinDialog.kt, app/src/test/java/com/openkiosk/presentation/KioskViewModelSensorOffTest.kt, app/src/test/java/com/openkiosk/sensors/MotionDetectionAnalyzerLightTest.kt, app/src/test/java/com/openkiosk/service/WatchdogBackoffTest.kt]
verified-by: "./gradlew testDebugUnitTest (resultados em app/build/test-results/testDebugUnitTest/*.xml); no aparelho: adb logcat -s MotionDetection:D ScreenState:D KioskWebView:E para ler changeRatio, transicoes de estado e morte do renderer"
doc-sig: open-kiosk/ScreenStateManager.kt@gen=3.8#7484b555
---

# Convencoes e armadilhas (patterns)

## Convencoes que o codigo segue de fato

- Logica que da para testar sai da classe e vira funcao pura de arquivo, `internal` ou publica, ao lado da classe que a usa. Exemplos lidos: `brightnessFor()` e `strandedInDeepSleep()` e `wakesScreenOnLaunch()` em **ScreenStateManager.kt**; `changedPixelRatio()` em **MotionDetectionAnalyzer.kt**; `isNear()` e `isApproach()` em **SensorWakeManager.kt**; `relaunchDelayMs()` e `shouldWakeOnRelaunch()` em **KioskWatchdogService.kt**. [confirmado]
- Comentario explica POR QUE, quase sempre em portugues, e quase sempre descreve o bug que a linha evita — nao o que a linha faz. Padrao a manter em codigo novo. [confirmado]
- Constante de calibracao de campo leva a instrucao de ajuste no proprio comentario ("knob de campo: subir se ...; baixar se ..."): `PIXEL_THRESHOLD` em **MotionDetectionAnalyzer.kt**, `MAX_PULSE_GAP_MS` e `CONTINUOUS_POLLING_MS` em **MotionDetectionManager.kt**. [confirmado]
- Gerentes de longa vida sao `@Singleton` + `@Inject constructor(@ApplicationContext context)`: `ScreenStateManager`, `MotionDetectionManager`, `SensorWakeManager`. `WebViewRecoveryManager` e a excecao — instanciado a mao em **KioskViewModel.kt** (`val recoveryManager = WebViewRecoveryManager()`). [confirmado]
- Referencia a Activity so por `WeakReference` (**ScreenStateManager.kt**, campo `activityRef`), e todo caminho que toca janela faz `activityRef?.get() ?: return`. [confirmado]
- Toda temporizacao usa `Handler(Looper.getMainLooper())` + `Runnable` nomeado como campo, para poder `removeCallbacks` no mesmo objeto. Nao ha corrotina agendando sono/pulso/backoff. [confirmado]
- Teste unitario e JVM puro (JUnit4 + mockito-kotlin), nome de teste em crase e em portugues, e o KDoc da classe abre com "Invariante:" dizendo o bug que o teste tranca. [confirmado]

## Armadilhas (gotchas) que ja causaram bug

### Ciclo de sono / estado de tela

- Sem activity anexada a maquina de estado FICA PARADA de proposito: `onUserActivity()` retorna cedo quando `activityRef?.get() == null`. Se voce "consertar" isso, o app grava um `ACTIVE→DIM→SLEEP` que nenhum painel viveu, e o vigia le esse SLEEP fantasma como "nao acenda" — o quiosque fica atras do anuncio da Amazon. [confirmado]
- `setState()` grava TODA transicao em SharedPreferences sob `KioskPrefs.KEY_SCREEN_STATE` (valor literal `"screen_state"`). Esse texto e lido de outro processo pelo vigia (`shouldWakeOnRelaunch(storedState: String?)`). Estado novo no enum `ScreenState` obriga a decidir o lado do vigia em `wakesScreenOnLaunch()`, senao vira "acorda" por default. [confirmado — costura verificada nos dois lados: ScreenStateManager.setState e KioskWatchdogService.shouldWakeOnRelaunch]
- Ordem do arranque frio: `attachActivity()` roda ANTES da config real chegar do banco, entao o brilho de DIM sai com o default. Por isso `configure()` chama `applyBrightness(brightnessFor(...))` de novo. Tirar essa segunda aplicacao devolve o bug do brilho errado no primeiro minuto. [confirmado]
- Sono profundo preso tem DOIS pontos de soltura, e nenhum e opcional: `configure()` solta so se ja houver janela (`activityRef?.get() != null`), `attachActivity()` solta quando a janela volta. Motivo: soltar destacado gravaria ACTIVE sem painel. Guarda unica: `strandedInDeepSleep(deepSleepEnabled, state)`. [confirmado]
- `detachActivity()` remove tambem o `deepSleepCheckRunnable`. O unico caminho de volta do DEEP_SLEEP e esse monitor de minuto em minuto (`DEEP_SLEEP_CHECK_INTERVAL_MS = 60_000L`), entao `resumeTimers()` tem que repo-lo — e repoe. Esquecer isso deixa o painel preto ate alguem tocar. [confirmado]
- `resumeTimers()` e `onUserActivity()` NAO sao intercambiaveis: o primeiro so rearma o cronometro do estado atual (usado no `onResume`), o segundo carimba presenca e volta para ACTIVE. Trocar um pelo outro acorda o painel toda vez que a activity volta. [confirmado]
- `dimBrightness` e forcado a `coerceIn(0.01f, 1.0f)` em `configure()`: DIM nunca chega a 0, porque preto e o estado SLEEP e o overlay preto e quem simula desligado. [confirmado]
- Existem DUAS `brightnessFor` no mesmo arquivo: a top-level `internal fun brightnessFor(state, dimBrightness)` (testada) e o metodo privado de mesmo nome com um argumento, que so injeta o campo. Mexer na de um argumento nao e coberto por teste. [confirmado]
- A tela NUNCA e desligada no nivel do sistema: nao ha `lockNow()`, nem WakeLock, nem DevicePolicyManager em **ScreenStateManager.kt** — sono e brilho 0 + overlay preto, com `FLAG_KEEP_SCREEN_ON` sempre ligado. Sem isso, camera e sensores morreriam junto com a tela. [confirmado — grep por lockNow/WakeLock no arquivo nao acha nada]

### Camera e movimento

- Movimento e medido DEPOIS de descontar o deslocamento global de luz: `changedPixelRatio()` monta um histograma de diferencas (-255..255), acha a mediana em uma passada e so conta pixel cuja diferenca se afasta dessa mediana mais que `PIXEL_THRESHOLD`. Comparar pixel a pixel sem essa subtracao faz a propria tela apagando parecer gente passando — foi o bug de "acordar sozinho a noite". [confirmado; corresponde ao commit 1035693a02c57c0f]
- `PIXEL_THRESHOLD = 20` hoje. Historico: 30 -> 15 -> 20. O numero nao e gosto, e o meio-termo entre dois modos de falha medidos no Fire: com 30 a pessoa em luz fraca era descartada (o delta real dela fica na faixa de 10-25), com 15 o ruido de ganho da camera passava por movimento. Quem for recalibrar em outro aparelho precisa dessa faixa: medir o delta de uma pessoa em luz fraca e o piso de ruido, e escolher entre os dois. [confirmado — comentario literal em **MotionDetectionAnalyzer.kt** linhas 9-13, acima de `private const val PIXEL_THRESHOLD = 20`]
- Qualquer doc ou comentario que ainda diga 15 ou 30 como valor vigente esta velho — inclusive o comentario dentro de **MotionDetectionAnalyzerLightTest.kt**, que escreve "PIXEL_THRESHOLD=30" (o teste passa mesmo assim, o texto e que mente). [confirmado]
- Calibracao medida no aparelho (Fire HD 8 / KFRAPWI, Fire OS base Android 11, por cabo): piso de ruido com a sala parada deu `changeRatio` de 0.00001 a 0.0025; um evento real de movimento deu 0.0823; o limiar de `MotionSensitivity.MEDIUM` e 0.05 (**KioskConfig.kt**). Margem de ~20x sobre o ruido e ~1.6x sob o evento real. [confirmado]
- Primeiro quadro apos o bind e LIXO (auto-exposicao convergindo) e e descartado sem virar referencia: `WARMUP_FRAMES = 1` e o contador `warmupRemaining`. [confirmado]
- `reset(warmup = false)` so pode ser usado quando a CAMERA ficou ligada — isto e, no ciclo pulsado, que apenas troca o analyzer (`clearAnalyzer`/`setAnalyzer`) e nunca faz unbind. Usar `warmup = false` depois de um bind reintroduz o falso movimento por exposicao. [confirmado]
- Dois relogios diferentes com nomes parecidos, e confundi-los cega ou frita o tablet:
  - amostragem (distancia entre os dois quadros comparados): `CONTINUOUS_POLLING_MS = 200L` em DIM, `PULSED_POLLING_MS = 200L` dentro do pulso;
  - pulso (janela vs. cegueira em SLEEP): `CAPTURE_WINDOW_MS = 2500L` de captura e `_pulseIntervalMs` de silencio, vindo da config `cameraPulseIntervalSeconds`.
  A config do menu e cadencia de PULSO, nunca de amostragem. [confirmado]
- `enablePulsedMode(intervalMs)` respeita o valor do dono com piso de 1s (`coerceAtLeast(1_000L)`). O teto antigo engolia em silencio o que o menu oferecia. A constante `MAX_PULSE_GAP_MS = 4000L` esta declarada e NAO e referenciada por nenhum codigo (grep em app/src acha so a propria declaracao e o comentario): implementada, inativa. [confirmado]
- Bind da camera e assincrono e pode ser cancelado no meio: `MotionDetectionManager` guarda um contador `generation`, incrementado por `start()` e por `stop()`; o listener aborta se `myGeneration != generation`. Codigo novo que faca bind fora desse caminho tem que repetir a guarda, senao a camera volta a rodar depois de um `stop()`. [confirmado]
- `stop()` zera `analyzer` e `imageAnalysis`. Depois disso `updateConfig(threshold)` vira no-op silencioso — mudar a sensibilidade com a camera parada nao faz nada, e o valor entra so no proximo `start()`, que ja recebe o threshold por parametro. [confirmado]

### Sensores (proximidade / chacoalhada)

- `start()` e idempotente por par de flags: `if (startedWith == Pair(wakeOnProximity, wakeOnShake)) return`. Sem isso, cada re-registro zeraria `proximityNear` para null e a primeira aproximacao seria engolida. [confirmado]
- Primeira leitura de proximidade e so linha de base: `isApproach()` exige `prevNear == false`. So a transicao longe->perto acorda; leitura "perto" sustentada nao redispara. [confirmado]
- Debounce inicializado com `System.currentTimeMillis()` no `start()` (nao com 0), senao o primeiro evento dispara na hora. Janelas: 1000 ms proximidade, 2000 ms chacoalhada; chacoalhada exige `magnitude - GRAVITY_EARTH > 12.0`. [confirmado]
- No Fire HD 8 alvo NAO existe sensor de proximidade (`pm list features` lista so accelerometer), entao o ramo `wakeOnProximity` cai no `Log.w("Proximity sensor not available on this device")`. O caminho esta implementado e ligado, mas inativo neste hardware — nesse tablet quem acorda por presenca e a camera. [confirmado]

### WebView e recuperacao de pagina

- Renderer morto tem contrato duro do Android: em `onRenderProcessGone` a instancia morta precisa SAIR da hierarquia E ser destruida, e o callback tem que devolver `true`; senao o processo do app cai junto. **KioskWebView.kt** faz `removeView` + `destroy()` e depois `webViewKey++` para recriar por `key(webViewKey)`. [confirmado]
- Nao comparar `webView.url` com a url pedida para decidir recarga: o endereco efetivo pos-redirecionamento nunca bate com o pedido e a pagina recarregaria a cada recomposicao. O codigo guarda `lastRequestedUrl` e so recarrega quando ele muda. [confirmado]
- Auto-refresh desligado chega como 0 do menu; `startAutoRefresh()` retorna cedo quando `autoRefreshIntervalMs <= 0L`. Agendar com atraso 0 seria recarga em laco continuo — o oposto de desligar. [confirmado]
- Backoff de erro de pagina e a lista literal `5_000L, 15_000L, 30_000L, 60_000L` em `WebViewRecoveryManager.backoffDelays`, com a ultima repetida para sempre; `onSuccess()` zera `retryCount`. Nao confundir com o backoff do vigia (`relaunchDelayMs`), que e 5s dobrando ate 60s. [confirmado]
- Em SLEEP/DEEP_SLEEP o WebView e congelado (`onPause()` + `pauseTimers()`) pelo parametro `paused`, ligado em **KioskScreen.kt** a `screenState == SLEEP || screenState == DEEP_SLEEP`. Sem isso, JS, timers e video seguiriam a plena carga atras da tela preta. [confirmado — costura verificada nos dois lados]
- `onDispose` faz a sequencia completa (`stopLoading`, `about:blank`, `clearHistory`, limpar chrome client e touch listener, `removeView`, `destroy`). Encurtar essa sequencia vaza WebView em cada troca de pagina da lista. [confirmado]

### PIN

- O PIN e travado em EXATAMENTE 4 digitos em dois lugares independentes: **PinDialog.kt** (filtro `value.length <= 4` e submissao automatica em `pin.length == 4`) e **SettingsScreen.kt** (mesmo filtro no campo de cadastro). Mudar so um lado cria PIN que ninguem consegue digitar. [confirmado]
- O dialogo AUTO-SUBMETE ao quarto digito; o botao OK e caminho redundante. Nao ha atraso nem limite de tentativas — a protecao e contra curioso, nao contra ataque. [confirmado]
- O PIN e comparado em texto claro contra o valor que veio da config. Nao ha hash. [confirmado — comparacao literal `pin == correctPin` em PinDialog.kt]

## Testes: o que cobrem e por que existem

Contagem mecanica: `ls app/src/test/java/com/openkiosk/*/*.kt | wc -l` -> 14 arquivos; `grep -o 'tests="[0-9]*"' app/build/test-results/testDebugUnitTest/*.xml | grep -o '[0-9]*' | paste -sd+ - | bc` -> 32 casos, com `failures="0" errors="0"` em todos os 14 XML. [confirmado — resultados lidos do diretorio de build, gerados em 15/08 15:28; para reproduzir, `./gradlew testDebugUnitTest`]

- **MotionDetectionAnalyzerLightTest.kt** — mudanca de luz no quadro INTEIRO nao e movimento; mudanca em uma REGIAO e. Existe por causa do painel apagando de madrugada e sendo lido como pessoa. Traz ainda um caso de ruido de ganho e um bloco "LIMITE CONHECIDO" declarando o caso sem resposta certa (tela apagando ilumina so parte do quadro), deliberadamente sem teste.
- **MotionDetectionAnalyzerResetTest.kt** — apos `reset()`, quadro velho nao gera deteccao; o quadro de aquecimento nao vira referencia.
- **MotionDetectionAnalyzerPulseWarmupTest.kt** — no ciclo pulsado (`reset(warmup = false)`) o primeiro quadro da janela JA vale como referencia; descartar custaria uma comparacao inteira da janela curta.
- **SensorWakeProximityTest.kt** — regras de `isNear`/`isApproach` (primeira leitura e so linha de base).
- **BrightnessForStateTest.kt** — os quatro estados de `brightnessFor()`.
- **ScreenStateDeepSleepExitTest.kt** — saida do sono profundo (`strandedInDeepSleep`).
- **WatchdogBackoffTest.kt** — `relaunchDelayMs`: 5s nas duas primeiras, dobrando (10/20/40), teto 60s inclusive em 50 falhas. Existe para que o relance que nao surte efeito nao repita notificacao identica a cada 5s para sempre.
- **KioskWatchdogRelaunchTest.kt** — `shouldWakeOnRelaunch`: ACTIVE/DIM acendem, SLEEP/DEEP_SLEEP nao, e texto invalido/nulo cai em "acende".
- **AutoRefreshDisabledTest.kt** — intervalo 0 nao agenda nada (`isAutoRefreshScheduled()` existe so para esse teste enxergar).
- **KioskViewModelSensorOffTest.kt** — desmarcar camera e sensores na gaveta PARA o que ja roda (`verify(sensorWakeManager).stop()` e `verify(motionDetectionManager).stop()`). Sem isso o ouvinte continua acordando o painel.
- **KioskViewModelConfigSensorsTest.kt**, **KioskViewModelAttachTest.kt**, **KioskViewModelReattachSensorsTest.kt**, **KioskViewModelPlaceholderConfigTest.kt** — reaplicacao de config na hora (ex.: `verify(motionDetectionManager).enablePulsedMode(45_000L)`), anexo e reanexo da activity, config placeholder. [confirmado por leitura parcial: li a assinatura do caso de `enablePulsedMode`; nao li os quatro arquivos integralmente]

Padrao ao adicionar teste: KDoc comecando por "Invariante:", nome do caso em portugues entre crases, e o codigo testado extraido para funcao pura quando o Android atrapalha (Handler, Window, SharedPreferences nao existem no JVM puro).

## Nao testado hoje (armadilha por ausencia)

- Nao ha teste para o par `detachActivity()` + `resumeTimers()` repondo o monitor de sono profundo — o bug do painel preso so aparece no aparelho. [confirmado — nenhum arquivo em app/src/test cita resumeTimers]
- Nao ha teste de `onRenderProcessGone` (precisa de renderer real). Verificacao possivel so no aparelho: `adb shell am crash` no processo do renderer + `adb logcat -s KioskWebView:E`. [inferido]
- [TODO: sem cobertura declarada] A lista de enderecos e os ajustes nao tem como sair do aparelho. Trocar de tablet exige redigitar tudo a mao. A fazer: salvar e restaurar a configuracao em arquivo, pela propria gaveta de ajustes.
