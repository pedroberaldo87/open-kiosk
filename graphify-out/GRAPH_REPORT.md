# Graph Report - open-kiosk  (2026-08-15)

## Corpus Check
- 59 files · ~219,507 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 507 nodes · 652 edges · 45 communities (38 shown, 7 thin omitted)
- Extraction: 95% EXTRACTED · 5% INFERRED · 0% AMBIGUOUS · INFERRED: 33 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `0383d046`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Community 9|Community 9]]
- [[_COMMUNITY_Community 10|Community 10]]
- [[_COMMUNITY_Community 11|Community 11]]
- [[_COMMUNITY_Community 12|Community 12]]
- [[_COMMUNITY_Community 13|Community 13]]
- [[_COMMUNITY_Community 14|Community 14]]
- [[_COMMUNITY_Community 15|Community 15]]
- [[_COMMUNITY_Community 16|Community 16]]
- [[_COMMUNITY_Community 17|Community 17]]
- [[_COMMUNITY_Community 18|Community 18]]
- [[_COMMUNITY_Community 19|Community 19]]
- [[_COMMUNITY_Community 20|Community 20]]
- [[_COMMUNITY_Community 21|Community 21]]
- [[_COMMUNITY_Community 22|Community 22]]
- [[_COMMUNITY_Community 23|Community 23]]
- [[_COMMUNITY_Community 24|Community 24]]
- [[_COMMUNITY_Community 25|Community 25]]
- [[_COMMUNITY_Community 26|Community 26]]
- [[_COMMUNITY_Community 27|Community 27]]
- [[_COMMUNITY_Community 28|Community 28]]
- [[_COMMUNITY_Community 29|Community 29]]
- [[_COMMUNITY_Community 30|Community 30]]
- [[_COMMUNITY_Community 31|Community 31]]
- [[_COMMUNITY_Community 32|Community 32]]
- [[_COMMUNITY_Community 33|Community 33]]
- [[_COMMUNITY_Community 34|Community 34]]
- [[_COMMUNITY_Community 35|Community 35]]
- [[_COMMUNITY_Community 36|Community 36]]
- [[_COMMUNITY_Community 37|Community 37]]
- [[_COMMUNITY_Community 38|Community 38]]
- [[_COMMUNITY_Community 39|Community 39]]
- [[_COMMUNITY_Community 40|Community 40]]
- [[_COMMUNITY_Community 41|Community 41]]

## God Nodes (most connected - your core abstractions)
1. `KioskViewModel` - 27 edges
2. `ScreenStateManager` - 24 edges
3. `Project Reference` - 15 edges
4. `OPEN-KIOSK` - 14 edges
5. `MotionDetectionManager` - 13 edges
6. `PlaylistManager` - 12 edges
7. `MainActivity` - 12 edges
8. `WebViewRecoveryManager` - 12 edges
9. `KioskViewModelAttachTest` - 11 edges
10. `KioskViewModelConfigSensorsTest` - 11 edges

## Surprising Connections (you probably didn't know these)
- `KioskScreen()` --calls--> `OfflineScreen()`  [INFERRED]
  app/src/main/java/com/openkiosk/presentation/screen/KioskScreen.kt → app/src/main/java/com/openkiosk/presentation/component/OfflineScreen.kt
- `KioskScreen()` --calls--> `PinDialog()`  [INFERRED]
  app/src/main/java/com/openkiosk/presentation/screen/KioskScreen.kt → app/src/main/java/com/openkiosk/presentation/component/PinDialog.kt
- `KioskScreen()` --calls--> `SettingsDrawerContent()`  [INFERRED]
  app/src/main/java/com/openkiosk/presentation/screen/KioskScreen.kt → app/src/main/java/com/openkiosk/presentation/screen/SettingsScreen.kt
- `shouldWakeOnRelaunch()` --calls--> `wakesScreenOnLaunch()`  [INFERRED]
  app/src/main/java/com/openkiosk/service/KioskWatchdogService.kt → app/src/main/java/com/openkiosk/sleep/ScreenStateManager.kt
- `KioskScreen()` --calls--> `KioskWebView()`  [INFERRED]
  app/src/main/java/com/openkiosk/presentation/screen/KioskScreen.kt → app/src/main/java/com/openkiosk/presentation/component/KioskWebView.kt

## Import Cycles
- None detected.

## Communities (45 total, 7 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.15
Nodes (14): Activity, Boolean, Float, Int, Long, Runnable, ScreenState, StateFlow (+6 more)

### Community 1 - "Community 1"
Cohesion: 0.09
Nodes (16): Boolean, Context, Int, Intent, Long, Runnable, String, IBinder (+8 more)

### Community 2 - "Community 2"
Cohesion: 0.11
Nodes (13): Boolean, Double, ImageProxy, Long, ImageAnalysis, Int, ImageProxy, Int (+5 more)

### Community 3 - "Community 3"
Cohesion: 0.08
Nodes (14): Boolean, String, Unit, Boolean, Context, KioskViewModel, KioskViewModel, Bundle (+6 more)

### Community 4 - "Community 4"
Cohesion: 0.13
Nodes (11): Activity, Boolean, KioskConfig, LifecycleOwner, PlaylistItem, ScreenState, StateFlow, String (+3 more)

### Community 5 - "Community 5"
Cohesion: 0.21
Nodes (20): Boolean, Int, List, PlaylistItem, String, MotionSensitivity, AddUrlRow(), AutoRefreshSection() (+12 more)

### Community 6 - "Community 6"
Cohesion: 0.10
Nodes (20): Architecture, Build, Camera Motion Detection, Code of Conduct, Configuration, Contributing, Debugging, Device Owner Setup (Full Kiosk Lock) (+12 more)

### Community 7 - "Community 7"
Cohesion: 0.15
Nodes (7): String, Boolean, Long, Runnable, PinDialog(), AutoRefreshDisabledTest, WebViewRecoveryManager

### Community 8 - "Community 8"
Cohesion: 0.17
Nodes (9): Activity, Boolean, Context, Intent, ComponentName, DeviceAdminReceiver, DevicePolicyManager, KioskLockManager (+1 more)

### Community 9 - "Community 9"
Cohesion: 0.20
Nodes (7): Flow, Int, List, Long, PlaylistItem, String, PlaylistRepository

### Community 10 - "Community 10"
Cohesion: 0.18
Nodes (8): Boolean, Double, LifecycleOwner, Long, Runnable, MotionDetectionAnalyzer, ProcessCameraProvider, MotionDetectionManager

### Community 11 - "Community 11"
Cohesion: 0.21
Nodes (8): Boolean, Float, Pair, SensorEventListener, isApproach(), isNear(), SensorWakeManager, SensorWakeProximityTest

### Community 12 - "Community 12"
Cohesion: 0.12
Nodes (15): Banco de Dados, CI/CD, Comandos Úteis, Decisões de Arquitetura, Dependências Críticas, Estrutura de Diretórios, Gestão de Energia (State Machine), Gotchas (+7 more)

### Community 13 - "Community 13"
Cohesion: 0.20
Nodes (6): Flow, Int, List, Long, PlaylistDao, PlaylistEntity

### Community 14 - "Community 14"
Cohesion: 0.23
Nodes (6): List, PlaylistItem, StateFlow, CoroutineScope, PlaylistManager, Job

### Community 15 - "Community 15"
Cohesion: 0.15
Nodes (7): Boolean, BroadcastReceiver, Unit, Context, Intent, PowerStateMonitor, BootReceiver

### Community 16 - "Community 16"
Cohesion: 0.19
Nodes (8): Int, KioskConfig, List, PlaylistItem, StateFlow, String, ViewModel, SettingsViewModel

### Community 17 - "Community 17"
Cohesion: 0.17
Nodes (8): ConfigRepository, KioskLockManager, MotionDetectionManager, PlaylistManager, PowerStateMonitor, ScreenStateManager, SensorWakeManager, KioskViewModelAttachTest

### Community 18 - "Community 18"
Cohesion: 0.17
Nodes (8): ConfigRepository, KioskLockManager, MotionDetectionManager, PlaylistManager, PowerStateMonitor, ScreenStateManager, SensorWakeManager, KioskViewModelConfigSensorsTest

### Community 19 - "Community 19"
Cohesion: 0.17
Nodes (8): ConfigRepository, KioskLockManager, MotionDetectionManager, PlaylistManager, PowerStateMonitor, ScreenStateManager, SensorWakeManager, KioskViewModelPlaceholderConfigTest

### Community 20 - "Community 20"
Cohesion: 0.17
Nodes (8): ConfigRepository, KioskLockManager, MotionDetectionManager, PlaylistManager, PowerStateMonitor, ScreenStateManager, SensorWakeManager, KioskViewModelReattachSensorsTest

### Community 21 - "Community 21"
Cohesion: 0.17
Nodes (8): ConfigRepository, KioskLockManager, MotionDetectionManager, PlaylistManager, PowerStateMonitor, ScreenStateManager, SensorWakeManager, KioskViewModelSensorOffTest

### Community 22 - "Community 22"
Cohesion: 0.27
Nodes (5): Flow, List, String, ConfigEntity, ConfigDao

### Community 23 - "Community 23"
Cohesion: 0.28
Nodes (5): ConfigDao, Context, PlaylistDao, AppDatabase, DatabaseModule

### Community 24 - "Community 24"
Cohesion: 0.22
Nodes (8): Before Submitting, Build Commands, Code Style, Contributing to Open Kiosk, Development Setup, How to Contribute, Internationalization, License

### Community 25 - "Community 25"
Cohesion: 0.29
Nodes (4): ConfigDao, PlaylistDao, AppDatabase, RoomDatabase

### Community 26 - "Community 26"
Cohesion: 0.33
Nodes (4): Flow, KioskConfig, String, ConfigRepository

### Community 27 - "Community 27"
Cohesion: 0.38
Nodes (3): ImageProxy, Int, MotionDetectionAnalyzerResetTest

### Community 28 - "Community 28"
Cohesion: 0.29
Nodes (6): Reporting a Vulnerability, Response Timeline, Scope, Security Policy, Supported Versions, What to include

### Community 29 - "Community 29"
Cohesion: 0.33
Nodes (5): Attribution, Contributor Covenant Code of Conduct, Enforcement, Our Pledge, Our Standards

### Community 30 - "Community 30"
Cohesion: 0.40
Nodes (3): Context, KioskPrefs, SharedPreferences

### Community 32 - "Community 32"
Cohesion: 0.50
Nodes (3): [1.0.0] - 2025-06-01, Added, Changelog

### Community 33 - "Community 33"
Cohesion: 0.50
Nodes (3): Checklist, Description, Related Issue

### Community 34 - "Community 34"
Cohesion: 0.50
Nodes (3): generated_ts, tree_hash, verdicts

### Community 35 - "Community 35"
Cohesion: 0.50
Nodes (3): generated_ts, tree_hash, verdicts

## Knowledge Gaps
- **169 isolated node(s):** `tree_hash`, `generated_ts`, `verdicts`, `tree_hash`, `generated_ts` (+164 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **7 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `KioskScreen()` connect `Community 3` to `Community 5`, `Community 7`?**
  _High betweenness centrality (0.032) - this node is a cross-community bridge._
- **Why does `KioskWebView()` connect `Community 3` to `Community 4`?**
  _High betweenness centrality (0.023) - this node is a cross-community bridge._
- **Why does `KioskViewModel` connect `Community 4` to `Community 16`?**
  _High betweenness centrality (0.023) - this node is a cross-community bridge._
- **What connects `tree_hash`, `generated_ts`, `verdicts` to the rest of the system?**
  _169 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.14772727272727273 - nodes in this community are weakly interconnected._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.08817204301075268 - nodes in this community are weakly interconnected._
- **Should `Community 2` be split into smaller, more focused modules?**
  _Cohesion score 0.10837438423645321 - nodes in this community are weakly interconnected._