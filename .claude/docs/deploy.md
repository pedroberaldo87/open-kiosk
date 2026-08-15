---
generated: 2026-08-15
generated-commit: 90934e0
project: open-kiosk
scope: [app/build.gradle.kts, build.gradle.kts, settings.gradle.kts, gradle/libs.versions.toml, gradle/wrapper/gradle-wrapper.properties, gradle.properties, .github/workflows/build.yml, app/src/main/AndroidManifest.xml, app/proguard-rules.pro, app/src/main/res/xml/network_security_config.xml, app/src/main/res/xml/device_admin_policies.xml, .gitignore, CHANGELOG.md, README.md]
verified-by: "./gradlew lintDebug && ./gradlew test && ./gradlew assembleDebug (JDK 17); no aparelho: adb install -r app/build/outputs/apk/debug/app-debug.apk e adb shell dumpsys package com.openkiosk | grep -i SYSTEM_ALERT_WINDOW"
doc-sig: open-kiosk/build.gradle.kts@gen=3.8#80dc59eb
---

# Deploy — como se compila, assina, testa e instala

## Estado da verificacao nesta rodada

- Nao existe `java` no PATH desta maquina; o JDK vem junto do Android Studio. Exporte antes de qualquer comando Gradle: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` (openjdk 21). [confirmado]
- Com o JDK exportado, `./gradlew compileDebugKotlin testDebugUnitTest lintDebug` sai `BUILD SUCCESSFUL`, somando 33 testes sem falha (`grep -ho 'tests="[0-9]*"' app/build/test-results/testDebugUnitTest/*.xml | awk -F'"' '{s+=$2} END {print s}'`). [confirmado]
- O `adb` vive em `~/Library/Android/sdk/platform-tools/adb` e nao esta no PATH. Os comandos de provisionamento abaixo foram executados num Amazon Fire HD 8 (`KFRAPWI`, base Android 11) ligado por cabo. [confirmado]

## Ferramental exigido

- Gradle wrapper `8.9` — `gradle/wrapper/gradle-wrapper.properties`, `distributionUrl` = `gradle-8.9-bin.zip`. [confirmado]
- JDK 17 — `app/build.gradle.kts`, bloco `compileOptions` (`JavaVersion.VERSION_17`) e `kotlinOptions.jvmTarget = "17"`; o CI instala o mesmo (`.github/workflows/build.yml`, passo `Set up JDK 17`, `distribution: 'temurin'`). [confirmado]
- Android SDK apontado por `sdk.dir` em `local.properties` (arquivo fora do git). [confirmado]
- Memoria da JVM do Gradle: `org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8` em `gradle.properties`. [confirmado]

## Versoes fixadas (copia literal de `gradle/libs.versions.toml`, secao `[versions]`)

```toml
agp = "8.5.2"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.27"
hilt = "2.51.1"
compose-bom = "2024.06.00"
camerax = "1.3.4"
room = "2.6.1"
```

- Os plugins entram por alias do catalogo: `build.gradle.kts` raiz declara os cinco com `apply false` (`android-application`, `kotlin-android`, `kotlin-compose`, `hilt-android`, `ksp`) e `app/build.gradle.kts` os aplica. [confirmado]
- Repositorios: `settings.gradle.kts` usa `RepositoriesMode.FAIL_ON_PROJECT_REPOS` com `google()` + `mavenCentral()` — modulo nenhum pode declarar repositorio proprio. [confirmado]
- Modulo unico: `settings.gradle.kts` faz `rootProject.name = "OPEN-KIOSK"` e `include(":app")`. [confirmado]
- Correcao historica do commit 003e6f3 (`dependencyResolution` -> `dependencyResolutionManagement`) esta no arquivo de hoje. [confirmado]

## Identidade do pacote

- `namespace` e `applicationId` = `com.openkiosk`; `minSdk = 28`; `targetSdk = 34`; `compileSdk = 34`; `versionCode = 1`; `versionName = "1.0.0"` — todos em `app/build.gradle.kts`, bloco `defaultConfig`. [confirmado]
- Nao ha automacao de versao: subir release exige editar `versionCode`/`versionName` a mao. [confirmado]

## Compilar

Nesta maquina nao ha Java no PATH (ver "Estado da verificacao"): o wrapper so roda depois de apontar para o JDK que vem embutido no Android Studio. Exportar antes de qualquer `./gradlew`:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$PATH"
```

Os dois caminhos existem no disco (`ls -d` em cada um retorna o proprio caminho). [confirmado]

```bash
./gradlew lintDebug        # lint do variant debug (mesmo passo do CI)
./gradlew test             # testes unitarios JVM
./gradlew assembleDebug    # APK debug
./gradlew assembleRelease  # APK release (exige assinatura, ver abaixo)
```

- Saida debug: `app/build/outputs/apk/debug/app-debug.apk`. O arquivo existe no disco com 56M (`ls -lh app/build/outputs/apk/debug/app-debug.apk`), o que bate com o "~56MB" escrito no README. [confirmado]
- Nao existe `app/build/outputs/apk/release/` (`ls app/build/outputs/apk/` -> so `debug`): o "~2.4MB" do README para o release nao pode ser conferido aqui. [confirmado que a pasta nao existe]
- O CI nao roda os testes: `build.yml` executa `lintDebug` e `assembleDebug`. O portao local completo e `./gradlew compileDebugKotlin testDebugUnitTest lintDebug`, hoje com 33 testes verdes. [confirmado]

## Assinar o release

- `app/build.gradle.kts` le `local.properties` no topo (`val localProps = Properties()...`) e monta `signingConfigs.create("release")` com estas quatro chaves, copiadas literalmente do arquivo:

```properties
signing.storeFile
signing.storePassword
signing.keyAlias
signing.keyPassword
```

- Padrao de `signing.storeFile` quando a chave falta: `../keystore/open-kiosk.jks`. As tres senhas/alias caem para string vazia (`getProperty(..., "")`). [confirmado]
- **O release nao compila no estado atual**: `local.properties` contem apenas `sdk.dir` (`cut -d= -f1 local.properties` -> `sdk.dir`) e o diretorio `keystore/` nao existe (`ls keystore` -> `No such file or directory`). [confirmado]
- Nada de segredo vai para o git: `.gitignore` lista `local.properties`, `/local.properties`, `keystore/`, `*.jks`, `*.apk`, `*.aab`. [confirmado]
- Gerar a keystore (comando do README, secao "Release Signing"):

```bash
keytool -genkeypair -v -keystore keystore/open-kiosk.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias openkiosk
```

- O commit 003e6f3 menciona um artefato de segredos redigido "for standalone builds" que nao foi localizado na arvore atual. [relatado]

## O que o buildType release faz

- `isMinifyEnabled = true` e `isShrinkResources = true` — R8 ligado, recursos podados. [confirmado]
- Regras em `app/proguard-rules.pro`, que preserva (entre elas): `dagger.hilt.**`, `javax.inject.**`, subclasses de `ViewComponentManager`, `com.openkiosk.data.local.entity.**` (entidades Room) e os metodos anotados com `@android.webkit.JavascriptInterface` em `com.openkiosk.presentation.component.KioskJsBridge`. [confirmado]
- Risco de regressao ao mexer nessas regras: entidade Room ou metodo da ponte JS renomeados pelo R8 quebram so no APK release, nunca no debug. [inferido]

## Testes

- 14 arquivos de teste JVM em `app/src/test` (`find app/src/test -name '*.kt' | wc -l` -> 14), cobrindo entre outros: `KioskViewModelAttachTest.kt`, `MotionDetectionAnalyzerLightTest.kt`, `WatchdogBackoffTest.kt`, `KioskWatchdogRelaunchTest.kt`, `BrightnessForStateTest.kt`, `AutoRefreshDisabledTest.kt`. [confirmado]
- Dependencias de teste declaradas direto em `app/build.gradle.kts` (sem catalogo): `junit:junit:4.13.2`, `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1`, `org.mockito.kotlin:mockito-kotlin:5.3.1`, `org.mockito:mockito-core:5.11.0`. [confirmado]
- `testOptions { unitTests.isReturnDefaultValues = true }` — chamadas ao framework Android nao stubadas devolvem valor padrao em vez de estourar. [confirmado]
- Ha `androidTestImplementation` declarado (`androidx.test.ext:junit:1.1.5`, `androidx.test:runner:1.5.2`, `androidx.room:room-testing:2.6.1`), mas nao existe diretorio `app/src/androidTest` (`ls app/src/` -> `main test`): dependencia instrumentada declarada, suite inexistente. [confirmado]

## Integracao continua (`.github/workflows/build.yml`)

- Gatilho: `push` e `pull_request` na branch `main`. Runner: `ubuntu-latest`. [confirmado]
- Passos, na ordem do arquivo: `actions/checkout@v4` -> `actions/setup-java@v4` (17, temurin) -> `gradle/actions/setup-gradle@v4` -> `./gradlew lintDebug` -> `./gradlew assembleDebug` -> `actions/upload-artifact@v4` com `name: app-debug` e `path: app/build/outputs/apk/debug/app-debug.apk`. [confirmado]
- O CI **nao roda `./gradlew test`** — os 14 testes so rodam na maquina de quem lembrar. [confirmado]
- O CI **nao constroi release**: nao ha `assembleRelease`, nem `secrets` de assinatura, nem job por tag. [confirmado]
- O badge de build do README aponta para este mesmo workflow (`README.md`, linha do badge `Build`). [confirmado]

## Instalar no tablet

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- `-r` reinstala por cima preservando os dados (o banco Room com playlist e ajustes sobrevive). [inferido]
- Trocar entre APK debug e release **exige desinstalar**: assinaturas diferentes. [inferido]

## Provisionamento do tablet por cabo (Fire HD 8 / KFRAPWI)

O caminho de device owner do README **nao passa neste aparelho**: o Controle Parental da Amazon (`com.amazon.parentalcontrols`) ja e profile owner e ha 9 contas. Sem restauracao de fabrica, `dpm set-device-owner` falha. [relatado]

Provisionamento que substitui, quatro comandos, cada um resolvendo uma coisa [relatado — medido em sessao anterior, nao reexecutado aqui]:

```bash
adb shell settings put global stay_on_while_plugged_in 7
# a tela nunca apaga sozinha enquanto o tablet esta na tomada (7 = AC + USB + wireless)

adb shell locksettings set-disabled true
# mata a tela de bloqueio com propaganda da Amazon, que roubava a frente do app

adb shell cmd package set-home-activity com.openkiosk/.presentation.MainActivity
# o app vira a tela inicial: botao home volta para o quiosque

adb shell appops set com.openkiosk SYSTEM_ALERT_WINDOW allow
# OBRIGATORIO: sem isso o vigia nao consegue trazer o app de volta para a frente
```

- Nao adianta tentar desligar a origem da propaganda: `com.amazon.kindle.kso` (Special Offers) e pacote protegido e o sistema recusa `pm disable-user`. Por cabo so da para contornar — o `locksettings set-disabled true` acima tira a tela de bloqueio do caminho, o anuncio em si continua instalado. [relatado — nao reexecutado aqui; nada no repositorio menciona `kso`]
- O quarto comando tem contraparte no codigo: `app/src/main/AndroidManifest.xml` declara `android.permission.SYSTEM_ALERT_WINDOW`, com comentario no proprio arquivo dizendo que a concessao por adb isenta o relance do bloqueio de inicio de activity em segundo plano. [confirmado]
- O alvo do terceiro comando existe: `MainActivity` esta em `.presentation.MainActivity` no manifesto, com `intent-filter` contendo `MAIN` + `LAUNCHER` + `HOME` + `DEFAULT` — ou seja, o app se candidata a tela inicial pelo proprio manifesto. [confirmado]
- Verificar depois de provisionar:

```bash
adb shell settings get global stay_on_while_plugged_in
adb shell cmd package resolve-activity -c android.intent.category.HOME
adb shell dumpsys package com.openkiosk | grep -i SYSTEM_ALERT_WINDOW
```

## Device owner (caminho do README, so em aparelho limpo)

```bash
adb shell dpm set-device-owner com.openkiosk/.receiver.KioskDeviceAdminReceiver
adb shell dpm remove-active-admin com.openkiosk/.receiver.KioskDeviceAdminReceiver
```

- O receptor existe: `AndroidManifest.xml` declara `.receiver.KioskDeviceAdminReceiver` com `android:permission="android.permission.BIND_DEVICE_ADMIN"`, `exported="true"`, meta-data `android.app.device_admin` -> `@xml/device_admin_policies` e acao `android.app.action.DEVICE_ADMIN_ENABLED`. Costura conferida nos dois lados. [confirmado]
- `app/src/main/res/xml/device_admin_policies.xml` pede uma unica politica: `<force-lock />`. Nada de wipe, senha ou camera. [confirmado]
- Exigencia do README: aparelho sem contas configuradas (fabrica ou setup novo). [confirmado que o README afirma isso]

## Rede (o que o APK permite por padrao)

- `app/src/main/res/xml/network_security_config.xml`: `<base-config cleartextTrafficPermitted="true">` com `<certificates src="system" />` — HTTP puro liberado para qualquer host, ancoras de confianca so as do sistema (nenhum CA proprio embutido). [confirmado]
- Ligado no manifesto por `android:networkSecurityConfig="@xml/network_security_config"`, com `android:usesCleartextTraffic="true"` redundante ao lado. Costura conferida nos dois lados. [confirmado]
- Consequencia pratica: URL de letreiro em `http://` na rede interna funciona; em contrapartida, nenhuma pagina exibida esta protegida de adulteracao na rede. [inferido]
- `android:allowBackup="false"` no manifesto — o backup do Android nao leva a configuracao para outro aparelho. [confirmado]

## Diagnostico no aparelho

```bash
adb logcat -s KioskViewModel:D MotionDetection:D SensorWake:D ScreenState:D
```

- O README passou a listar a mesma forma completa, com `ScreenState:D`, `KioskWatchdog:D` e `PowerState:D`, mais o que cada linha de log significa. [confirmado]

## Correcoes a fazer na documentacao existente

- `README.md`, secao "Tested Hardware", diz `Amazon Fire HD 8 (KFRAPWI, FireOS on Android 11 base)` — corrigido nesta rodada, junto da lista do que foi verificado no aparelho. [confirmado]
- `README.md` passou a documentar o provisionamento por cabo numa secao propria ("If device owner is refused (retail Amazon Fire)"), com os cinco comandos e o aviso de que a permissao de sobreposicao e obrigatoria para o vigia relancar. [confirmado]
- `CHANGELOG.md`, entrada `[1.0.0]`, diz `Internationalization support (English + Portuguese)`, mas `app/src/main/res/` tem `values-es`. [confirmado]

## Pendencias

- [TODO: sem workflow de release] Nao ha job de tag, assinatura no CI nem publicacao de APK — o release e sempre manual, na maquina do dono.
- [TODO: CI nao roda testes] `build.yml` executa `lintDebug` e `assembleDebug`; os 14 testes de `app/src/test` nunca rodam no CI.
- [TODO: release nunca verificado aqui] Com `JAVA_HOME` apontado para o JDK do Android Studio o debug compila e os testes passam, mas sem keystore no disco o `assembleRelease` (e o tamanho ~2,4 MB que o README afirma) continua sem confirmacao.
- [TODO: sem exportar configuracao] Playlist e ajustes nao saem do aparelho; trocar de tablet exige redigitar tudo. Registrado tambem no doc de durabilidade.
