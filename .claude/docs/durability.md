---
generated: 2026-08-15
generated-commit: 90934e0
project: open-kiosk
scope: [app/src/main/java/com/openkiosk/data/repository/ConfigRepository.kt, app/src/main/java/com/openkiosk/data/local/AppDatabase.kt, app/src/main/java/com/openkiosk/data/local/KioskPrefs.kt, app/src/main/AndroidManifest.xml, app/build.gradle.kts, app/src/main/java/com/openkiosk/data/local/entity/ConfigEntity.kt, app/src/main/java/com/openkiosk/data/local/entity/PlaylistEntity.kt, app/src/main/java/com/openkiosk/data/local/dao/ConfigDao.kt, app/src/main/java/com/openkiosk/data/local/dao/PlaylistDao.kt, app/src/main/java/com/openkiosk/di/DatabaseModule.kt, app/src/main/java/com/openkiosk/data/repository/PlaylistRepository.kt, app/src/main/java/com/openkiosk/domain/model/KioskConfig.kt]
verified-by: "adb shell run-as com.openkiosk ls -l files/../databases/ e .../shared_prefs/ no tablet; grep -rn \"getSharedPreferences|Room.databaseBuilder|allowBackup\" app/src/main; ./gradlew test"
doc-sig: open-kiosk/ConfigRepository.kt@gen=3.8#93ba7c81
---

# Durabilidade — onde o estado mora e o que acontece se o tablet sumir

Resumo em uma linha: **nada sai do aparelho.** Nenhum depósito tem cópia, nenhum tem
rotina de restauração, e nenhum foi restaurado alguma vez. O que segue é a conta
depósito por depósito.

---

## 1. Banco Room `openkiosk.db` — ajustes e lista de endereços

Onde é criado (arquivo + símbolo):

```
app/src/main/java/com/openkiosk/di/DatabaseModule.kt  ·  DatabaseModule.provideDatabase
    Room.databaseBuilder(context, AppDatabase::class.java, "openkiosk.db").build()
```

Caminho no aparelho: `/data/data/com.openkiosk/databases/openkiosk.db` (padrão do Room
quando se passa só o nome do arquivo) — [inferido], não listado no aparelho nesta sessão.

Duas tabelas, declaradas em `AppDatabase` (`entities = [ConfigEntity::class, PlaylistEntity::class]`) — [confirmado]:

- `config` — chave/valor de texto. `ConfigEntity` tem `@PrimaryKey val key: String` e `val value: String`.
- `playlist` — `PlaylistEntity(id autoGenerate, url, durationSeconds, position)`.

Chaves gravadas em `config` por `ConfigRepository.updateConfig(config: KioskConfig)`
(17 linhas `ConfigEntity(` no bloco `setAll`, contado com
`sed -n '46,64p' .../ConfigRepository.kt | grep -c 'ConfigEntity('` → 17; e
`grep -c '^    val ' .../KioskConfig.kt` → 17 campos, ou seja o objeto inteiro é gravado) — [confirmado]:

```
activeTimeoutSeconds  dimTimeoutSeconds  dimBrightnessPercent
wakeOnMotion  wakeOnProximity  wakeOnShake  motionSensitivity
cameraPollingIntervalSeconds  cameraPulseIntervalSeconds
deepSleepEnabled  deepSleepStartHour  deepSleepEndHour
autoRefreshMinutes  lockTaskEnabled  pinEnabled  pin  startUrl
```

- `pin` é segredo: o valor padrão está literal em `ConfigRepository.observeConfig` e **não é reproduzido aqui**. Ele mora em texto puro na tabela `config`, sem cifra — [confirmado].
- Linha de durabilidade: **sem cópia. Frequência: nenhuma. Nunca restaurado.**
- `android:allowBackup="false"` em `AndroidManifest.xml` (elemento `<application>`) desliga até o backup automático do Android — nem o `adb backup` nem o backup de nuvem levam este banco — [confirmado].
- Não existe nenhuma rotina de exportar/importar no código: `grep -rni "export|backup|restore|ACTION_CREATE_DOCUMENT|OpenDocument" app/src/main/java` só devolve a palavra "restore" num comentário de `MotionDetectionManager` e o `exportSchema` do Room — [confirmado].
- Desinstalar o aplicativo apaga o banco junto (é diretório privado do pacote) — [inferido].

### Risco de migração (o banco também morre por dentro)

```
app/src/main/java/com/openkiosk/data/local/AppDatabase.kt
    @Database(entities = [...], version = 1, exportSchema = false)
```

- `version = 1`, `exportSchema = false`, e a construção em `DatabaseModule.provideDatabase` **não** chama `addMigrations(...)` nem `fallbackToDestructiveMigration()` — `grep -rn "Migration|fallbackToDestructive" app/src/main/java` não devolve nada — [confirmado].
- Consequência: no dia em que alguém acrescentar um campo e subir a `version`, o aplicativo estoura ao abrir o banco em qualquer tablet que já tenha dados. E como `exportSchema = false`, não existe `app/schemas/` (`ls app/schemas` → *No such file or directory*) para escrever a migração a partir do esquema antigo — [confirmado o estado; [inferido] a consequência, que é o comportamento padrão do Room].

### Verificação

```
adb shell run-as com.openkiosk ls -l /data/data/com.openkiosk/databases/
adb shell run-as com.openkiosk sqlite3 /data/data/com.openkiosk/databases/openkiosk.db "select key,value from config;"
```

(o segundo comando depende de `sqlite3` existir no Fire OS — não executado nesta sessão)

---

## 2. SharedPreferences `open_kiosk_prefs` — estado de tela e idioma

```
app/src/main/java/com/openkiosk/data/local/KioskPrefs.kt
    const val FILE = "open_kiosk_prefs"
    const val KEY_SCREEN_STATE = "screen_state"
    const val KEY_LANGUAGE = "language"
```

Duas chaves (`grep -c 'const val KEY_' .../KioskPrefs.kt` → 2) — [confirmado].

Quem escreve e quem lê — [confirmado] por `grep -rn "getSharedPreferences|KioskPrefs\." app/src/main/java`:

- escreve `screen_state`: `ScreenStateManager` (`prefs.edit().putString(KioskPrefs.KEY_SCREEN_STATE, state.name)`)
- lê `screen_state`: `KioskWatchdogService` — a costura existe dos dois lados hoje
- lê `language`: `MainActivity.attachBaseContext`, antes do Hilt
- escreve `language`: `SettingsScreen`, na seção de idioma

- Linha de durabilidade: **sem cópia. Frequência: nenhuma. Nunca restaurado.**
- Mesmo `allowBackup="false"`: o arquivo `/data/data/com.openkiosk/shared_prefs/open_kiosk_prefs.xml` não entra em backup nenhum — [confirmado no manifesto].
- Perder este arquivo é barato: o estado de tela é recalculado no arranque e o idioma volta a `auto`. É o único depósito cuja perda não dói.
- Ponto frágil de nome: `SettingsScreen` grava com a string literal `"language"` em vez da constante `KioskPrefs.KEY_LANGUAGE` (mesmo valor hoje, então funciona). Renomear a constante quebraria só um dos lados, em silêncio — [confirmado].

### Verificação

```
adb shell run-as com.openkiosk cat /data/data/com.openkiosk/shared_prefs/open_kiosk_prefs.xml
```

---

## 3. Dados do site exibido (localStorage / cookies do WebView)

```
app/src/main/java/com/openkiosk/presentation/component/KioskWebView.kt
    javaScriptEnabled = true
    domStorageEnabled = true
```

- O WebView guarda dados do site no diretório privado do pacote — [inferido], não inspecionado no aparelho.
- Não há `CookieManager` configurado no código (`grep -rn "CookieManager" app/src/main/java` → vazio), ou seja, o comportamento é o padrão da plataforma — [confirmado].
- Linha de durabilidade: **sem cópia. Frequência: nenhuma. Nunca restaurado.** Se o painel exibir uma página que guarda sessão/estado local, isso se perde na troca de tablet.

---

## 4. Provisionamento do aparelho (mora fora do aplicativo)

Estes ajustes NÃO estão em nenhum arquivo do projeto: são estado do próprio Fire OS,
aplicado por cabo. Perder o tablet = refazer tudo à mão — [confirmado nesta sessão, no
Fire HD 8 / KFRAPWI conectado por cabo]:

```
adb shell settings put global stay_on_while_plugged_in 7
adb shell locksettings set-disabled true
adb shell cmd package set-home-activity com.openkiosk/.presentation.MainActivity
adb shell appops set com.openkiosk SYSTEM_ALERT_WINDOW allow
```

- O último é **obrigatório**: sem ele o vigia não consegue relançar o aplicativo. A
  permissão está declarada no manifesto (`android.permission.SYSTEM_ALERT_WINDOW`, com
  o comentário no próprio arquivo explicando a isenção de início em segundo plano) — [confirmado],
  mas declarar não concede: quem concede é o `appops` acima.
- Registro de dono do dispositivo (device owner) **não passa neste aparelho**: o
  Controle Parental da Amazon (`com.amazon.parentalcontrols`) já é profile owner e há 9
  contas — [confirmado nesta sessão]. Ou seja, o caminho de cabo acima é o provisionamento
  real, não um plano B.
- Linha de durabilidade: **sem cópia. Frequência: nenhuma. Nunca restaurado.** [TODO: sem
  cobertura declarada] não existe script de provisionamento versionado no repositório —
  os comandos vivem em prosa, não em arquivo executável.

---

## 5. Chave de assinatura de release

```
app/build.gradle.kts  ·  signingConfigs { create("release") { ... } }
    storeFile = file(localProps.getProperty("signing.storeFile", "../keystore/open-kiosk.jks"))
    storePassword / keyAlias / keyPassword  ← lidos de local.properties, default ""
```

- Os nomes das propriedades são `signing.storeFile`, `signing.storePassword`, `signing.keyAlias`, `signing.keyPassword`, lidos de `local.properties` na raiz — [confirmado]. **Nenhum valor aparece neste doc nem no repositório.**
- Linha de durabilidade: **fora do controle do projeto.** O `.jks` e o `local.properties` são responsabilidade do dono da máquina. Perder o keystore significa não conseguir mais publicar atualização sobre a instalação existente — [inferido, é o comportamento do Android].

---

## O que existe declarado e não é usado

- `libs.datastore.preferences` está em `dependencies` de `app/build.gradle.kts`, mas `grep -rn "DataStore|preferencesDataStore" app/src/main/java` não devolve nada. **Implementado, inativo** — dependência declarada sem uso — [confirmado].
- `androidTestImplementation("androidx.room:room-testing:2.6.1")` está declarado, e `find app/src/androidTest -type f | wc -l` → 0. Nenhum teste instrumentado existe; não há teste de migração nem de leitura/escrita real do banco — [confirmado].
- Os 14 testes unitários (`find app/src/test -name '*.kt' | wc -l` → 14) cobrem sensores, sono, watchdog e viewmodel; **nenhum toca `ConfigRepository` nem `AppDatabase`** — a lista de arquivos inclui `MotionDetectionAnalyzer*`, `SensorWakeProximityTest`, `BrightnessForStateTest`, `ScreenStateDeepSleepExitTest`, `AutoRefreshDisabledTest`, `WatchdogBackoffTest`, `KioskWatchdogRelaunchTest` e cinco `KioskViewModel*` — [confirmado].

---

## Pendências

- **[TODO: sem cobertura declarada]** A lista de endereços e os ajustes não têm como sair
  do aparelho. Trocar de tablet exige redigitar tudo à mão. A fazer: salvar e restaurar a
  configuração em arquivo, pela própria gaveta de ajustes.
- **[TODO: sem cobertura declarada]** Nenhum caminho de migração do banco: subir a
  `version` do `@Database` hoje derruba o aplicativo em qualquer tablet com dados, e sem
  `app/schemas/` não há esquema antigo para escrever a migração contra.
- **[TODO: sem cobertura declarada]** O provisionamento por cabo não está versionado como
  script no repositório.
- **[TODO: nunca exercitado]** Nenhuma restauração foi feita alguma vez, em nenhum
  depósito — não porque falhou, mas porque não existe o que restaurar de.
- **[TODO: não inspecionado]** Os caminhos reais em `/data/data/com.openkiosk/` não foram
  listados no aparelho nesta sessão; estão [inferido] a partir do padrão do Android.

---

## Achados históricos reconciliados contra o código desta fatia

- "Room persistence for configs and playlist" (commit inicial) → [confirmado]: `AppDatabase` declara as duas entidades e os dois DAOs.
- "Min SDK 28, Target SDK 34, package com.openkiosk" (memória do projeto) → [confirmado]: `app/build.gradle.kts`, bloco `defaultConfig` (`minSdk = 28`, `targetSdk = 34`, `applicationId = "com.openkiosk"`, `namespace = "com.openkiosk"`).
- "Add release signing config via local.properties (no secrets in repo)" → [confirmado]: `signingConfigs` lê `local.properties` com defaults vazios; nenhum valor no arquivo versionado.
- "Allow cleartext HTTP traffic (network_security_config.xml)" → [confirmado]: `AndroidManifest.xml` tem `android:networkSecurityConfig="@xml/network_security_config"` e `android:usesCleartextTraffic="true"`.
- "Remove missing ic_launcher reference from manifest" → [confirmado]: o elemento `<application>` não tem atributo `android:icon`.
- "Language override in SharedPreferences, read synchronously in attachBaseContext" → [confirmado]: `KioskPrefs.KEY_LANGUAGE` lido em `MainActivity.attachBaseContext`.
- "Proximity sensor wake" (commit inicial) → parcialmente contraditado pelo hardware, não pelo código: a chave `wakeOnProximity` existe e é persistida por `ConfigRepository` — [confirmado] —, mas o Fire HD 8 testado **não tem sensor de proximidade** (`pm list features` só lista acelerômetro) — [confirmado nesta sessão]. O ajuste é durável e inerte neste aparelho.
- "Kiosk Lock Mode (Device Owner + immersive fallback)" → a chave `lockTaskEnabled` é persistida — [confirmado]; que o modo funcione neste aparelho é [relatado] e, pelo bloqueio de device owner descrito acima, depende do caminho por cabo.
