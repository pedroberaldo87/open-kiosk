# Graph Report - open-kiosk  (2026-10-27)

## Corpus Check
- 80 files · ~50,930 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 21 file(s) not represented in the graph (top: .xml 7, (none) 4, .log 4)

## Summary
- 830 nodes · 1737 edges · 47 communities (22 shown, 25 thin omitted)
- Extraction: 86% EXTRACTED · 14% INFERRED · 0% AMBIGUOUS · INFERRED: 248 edges (avg confidence: 0.94)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `9d1156f0`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- ScreenStateManager
- MotionDetectionManager.kt
- KioskWebView.kt
- KioskViewModel
- WebViewRecoveryManager
- OPEN-KIOSK
- KioskLockManager.kt
- MotionDetectionManager
- SensorWakeManager.kt
- Deploy — como se compila, assina, testa e instala
- PlaylistEntity
- PlaylistItem
- PowerStateMonitor.kt
- shouldWakeOnRelaunch
- KioskLockManager
- KioskConfig
- OfflineScreen.kt
- MainActivity
- attributes
- PinDialog.kt
- Composable
- body
- KioskWatchdogService
- PlaylistManager
- .attachBaseContext
- KioskPrefs
- KioskViewModelReattachSensorsTest
- [Unreleased]
- PULL_REQUEST_TEMPLATE.md
- KioskViewModelSensorOffTest
- Estado do Fire — KFRAPWI · Fire OS 8.3.3.8 (RS8338/3339)
- OpenKioskApplication
- NetworkCallback
- fire-estado.sh
- AGENTS.md
- GEMINI.md

## God Nodes (most connected - your core abstractions)
1. `KioskViewModel` - 53 edges
2. `ScreenStateManager` - 44 edges
3. `MotionDetectionManager` - 32 edges
4. `KioskConfig` - 30 edges
5. `ScreenState` - 28 edges
6. `PlaylistManager` - 27 edges
7. `ConfigRepository` - 24 edges
8. `PlaylistItem` - 24 edges
9. `KioskLockManager` - 24 edges
10. `SensorWakeManager` - 24 edges

## Surprising Connections (you probably didn't know these)
- `Cobertura de teste` --references--> `ConfigRepository`  [INFERRED]
  .claude/docs/data-stores.md → app/src/main/java/com/openkiosk/data/repository/ConfigRepository.kt
- `Provisionamento do tablet por cabo (Fire HD 8 / KFRAPWI)` --references--> `MainActivity`  [INFERRED]
  .claude/docs/deploy.md → app/src/main/java/com/openkiosk/presentation/MainActivity.kt
- `Sensores (proximidade / chacoalhada)` --references--> `isApproach()`  [INFERRED]
  .claude/docs/patterns.md → app/src/main/java/com/openkiosk/sensors/SensorWakeManager.kt
- `Limpeza da Amazon por cabo (plano 2026-10-26-fire-limpeza)` --references--> `KioskWatchdogService`  [INFERRED]
  .claude/docs/deploy.md → app/src/main/java/com/openkiosk/service/KioskWatchdogService.kt
- `2. Injecao de dependencia (Hilt)` --references--> `OpenKioskApplication`  [INFERRED]
  .claude/docs/architecture.md → app/src/main/java/com/openkiosk/OpenKioskApplication.kt

## Import Cycles
- None detected.

## Communities (47 total, 25 thin omitted)

### Community 0 - "ScreenStateManager"
Cohesion: 0.08
Nodes (31): ScreenState, ACTIVE, DEEP_SLEEP, DIM, SLEEP, KioskScreen(), brightnessFor(), releaseStrandedOnAttach() (+23 more)

### Community 2 - "MotionDetectionManager.kt"
Cohesion: 0.07
Nodes (5): changedPixelRatio(), MotionDetectionAnalyzer, MotionDetectionAnalyzerLightTest, MotionDetectionAnalyzerPulseWarmupTest, MotionDetectionAnalyzerResetTest

### Community 3 - "KioskWebView.kt"
Cohesion: 0.12
Nodes (3): KioskWebView(), WebChromeClient, WebViewClient

### Community 5 - "WebViewRecoveryManager"
Cohesion: 0.08
Nodes (18): relaunchDelayMs(), WebViewRecoveryManager, WatchdogBackoffTest, AutoRefreshDisabledTest, 5.7 Recuperacao da pagina fora da tela, Armadilhas (gotchas) que ja causaram bug, Camera e movimento, Convencoes e armadilhas (patterns) (+10 more)

### Community 6 - "OPEN-KIOSK"
Cohesion: 0.05
Nodes (40): Attribution, Contributor Covenant Code of Conduct, Enforcement, Our Pledge, Our Standards, Before Submitting, Build Commands, Code Style (+32 more)

### Community 8 - "KioskLockManager.kt"
Cohesion: 0.06
Nodes (19): KioskDeviceAdminReceiver, runs-on, steps, jobs, build, name, branches, branches (+11 more)

### Community 11 - "SensorWakeManager.kt"
Cohesion: 0.13
Nodes (5): isApproach(), isNear(), SensorEventListener, SensorEventListener, SensorWakeProximityTest

### Community 12 - "Deploy — como se compila, assina, testa e instala"
Cohesion: 0.05
Nodes (33): BrightnessForStateTest, As armadilhas que mordem de verdade, Comandos rápidos, Custom Rules, Documentação, Knowledge Graph (graphify), OPEN-KIOSK, Assinar o release (+25 more)

### Community 13 - "PlaylistEntity"
Cohesion: 0.07
Nodes (9): AppDatabase, ConfigDao, PlaylistDao, ConfigEntity, PlaylistEntity, DatabaseModule, 1. Banco Room `openkiosk.db` — ajustes e lista de endereços, Risco de migração (o banco também morre por dentro) (+1 more)

### Community 14 - "PlaylistItem"
Cohesion: 0.06
Nodes (20): PlaylistRepository, PlaylistItem, SettingsViewModel, 1. Banco SQLite do Room — `openkiosk.db`, 3. Armazenamento do navegador embutido, Buracos achados na comparacao mecanica interface x banco, Cobertura de teste, Conversao entidade <-> dominio (+12 more)

### Community 16 - "shouldWakeOnRelaunch"
Cohesion: 0.14
Nodes (6): lockscreenAdIsOn(), shouldRestartForGraphics(), shouldWakeOnRelaunch(), GraphicsRestartTest, KioskWatchdogRelaunchTest, LockscreenAdTest

### Community 17 - "KioskLockManager"
Cohesion: 0.11
Nodes (7): KioskLockManager, PowerStateMonitor, SensorWakeManager, KioskViewModelAttachTest, KioskViewModelPlaceholderConfigTest, 2. Injecao de dependencia (Hilt), Por que os gerentes sao singleton e a ViewModel nao

### Community 18 - "KioskConfig"
Cohesion: 0.13
Nodes (9): ConfigRepository, KioskConfig, MotionSensitivity, HIGH, LOW, MEDIUM, KioskViewModelConfigSensorsTest, 5.1 Config real vs. valor de fabrica (`realConfig` nulo) (+1 more)

### Community 21 - "MainActivity"
Cohesion: 0.22
Nodes (4): MainActivity, 4. Quem chama quem, O vigia nao chama a ViewModel — le uma bandeira e um arquivo, 3 · Relance quando algo cobre o aplicativo

### Community 22 - "attributes"
Cohesion: 0.13
Nodes (14): description, label, options, placeholder, render, body, attributes, id (+6 more)

### Community 25 - "Composable"
Cohesion: 0.30
Nodes (13): AddUrlRow(), AutoRefreshSection(), DropdownRow(), DurationDropdown(), KioskSection(), LanguageSection(), PinChangeDialog(), PlaylistSection() (+5 more)

### Community 26 - "body"
Cohesion: 0.17
Nodes (11): description, label, body, attributes, id, type, validations, description (+3 more)

### Community 29 - ".attachBaseContext"
Cohesion: 0.25
Nodes (5): 5.2 Trava de quiosque: dono do aparelho com queda para imersivo, 5.5 Sensores de proximidade e chacoalhada: implementados, sem hardware aqui, 5.6 Idioma lido antes do Hilt, 5. Decisoes de arquitetura, com o motivo, Achados históricos reconciliados contra o código desta fatia

### Community 30 - "KioskPrefs"
Cohesion: 0.32
Nodes (4): KioskPrefs, 2. Preferencias do Android — `open_kiosk_prefs`, `language` — o idioma escolhido a mao, `screen_state` — o estado da tela

### Community 32 - "[Unreleased]"
Cohesion: 0.25
Nodes (7): [1.0.0] - 2025-06-01, Added, Added, Changelog, Fixed, Removed, [Unreleased]

### Community 33 - "PULL_REQUEST_TEMPLATE.md"
Cohesion: 0.50
Nodes (3): Checklist, Description, Related Issue

### Community 35 - "Estado do Fire — KFRAPWI · Fire OS 8.3.3.8 (RS8338/3339)"
Cohesion: 0.29
Nodes (6): Apps desligados (pm list packages -d) · desfazer cada um: `adb shell pm enable <pacote>`, Chaves de ajuste (valor lido · comando que devolve este valor), Contas, Estado do Fire — KFRAPWI · Fire OS 8.3.3.8 (RS8338/3339), Quiosque, Recusas e reversões medidas durante a execução (2026-10-26)

## Knowledge Gaps
- **7 isolated node(s):** `LOW`, `MEDIUM`, `HIGH`, `ACTIVE`, `DIM` (+2 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 232 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **25 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `KioskViewModel` connect `KioskViewModel` to `ScreenStateManager`, `KioskViewModelSensorOffTest`, `WebViewRecoveryManager`, `KioskViewModelReattachSensorsTest.kt`, `MotionDetectionManager`, `PlaylistItem`, `KioskLockManager`, `KioskConfig`, `KioskScreen.kt`, `MainActivity`, `MainActivity.kt`, `PlaylistManager`, `KioskViewModelReattachSensorsTest`?**
  _High betweenness centrality (0.141) - this node is a cross-community bridge._
- **Are the 5 inferred relationships involving `KioskViewModel` (e.g. with `2. Injecao de dependencia (Hilt)` and `4. Quem chama quem`) actually correct?**
  _`KioskViewModel` has 5 INFERRED edges - model-reasoned connections that need verification._
- **What connects `LOW`, `MEDIUM`, `HIGH` to the rest of the system?**
  _7 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `ScreenStateManager` be split into smaller, more focused modules?**
  _Cohesion score 0.08035714285714286 - nodes in this community are weakly interconnected._
- **Why does `ScreenState` connect `ScreenStateManager` to `KioskWatchdogService.kt`, `KioskViewModel`, `KioskViewModelReattachSensorsTest.kt`, `Deploy — como se compila, assina, testa e instala`, `shouldWakeOnRelaunch`, `KioskScreen.kt`?**
  _High betweenness centrality (0.073) - this node is a cross-community bridge._
- **Are the 9 inferred relationships involving `ScreenStateManager` (e.g. with `Runnable` and `2. Injecao de dependencia (Hilt)`) actually correct?**
  _`ScreenStateManager` has 9 INFERRED edges - model-reasoned connections that need verification._
- **Should `MotionDetectionManager.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.06775510204081632 - nodes in this community are weakly interconnected._