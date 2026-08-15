---
generated: 2026-08-15
generated-commit: 628b8ea
project: open-kiosk
scope: [app/src/main/java/com/openkiosk/data/repository/PlaylistRepository.kt, app/src/main/java/com/openkiosk/data/local/dao/PlaylistDao.kt, app/src/main/java/com/openkiosk/data/local/dao/ConfigDao.kt, app/src/main/java/com/openkiosk/di/DatabaseModule.kt, app/src/main/java/com/openkiosk/data/repository/ConfigRepository.kt, app/src/main/java/com/openkiosk/data/local/AppDatabase.kt, app/src/main/java/com/openkiosk/data/local/KioskPrefs.kt, app/src/main/java/com/openkiosk/data/local/entity/PlaylistEntity.kt, app/src/main/java/com/openkiosk/data/local/entity/ConfigEntity.kt, app/src/main/java/com/openkiosk/domain/model/KioskConfig.kt]
verified-by: |
  Com o tablet ligado no cabo:
    adb shell run-as com.openkiosk ls -la databases shared_prefs
    adb shell run-as com.openkiosk sqlite3 databases/openkiosk.db ".tables"
    adb shell run-as com.openkiosk sqlite3 databases/openkiosk.db "SELECT key FROM config ORDER BY key;"
    adb shell run-as com.openkiosk sqlite3 databases/openkiosk.db "SELECT id,url,position,durationSeconds FROM playlist ORDER BY position;"
    adb shell run-as com.openkiosk cat shared_prefs/open_kiosk_prefs.xml
  Sem aparelho, no repositorio:
    grep -rn "getSharedPreferences\|DataStore\|openFileOutput\|filesDir\|cacheDir" --include="*.kt" app/src
    grep -c 'ConfigEntity("' app/src/main/java/com/openkiosk/data/repository/ConfigRepository.kt
doc-sig: open-kiosk/PlaylistRepository.kt@gen=3.8#15780373
---

# Depositos de dados — inventario

Tudo que sobrevive a fechar o aplicativo cabe em **tres** lugares. A lista abaixo saiu de uma
varredura mecanica, nao de leitura a olho:

```
grep -rn "getSharedPreferences\|DataStore\|openFileOutput\|filesDir\|cacheDir" --include="*.kt" app/src
# unica ocorrencia: app/src/main/java/com/openkiosk/data/local/KioskPrefs.kt:18
```

- Um banco SQLite gerido pelo Room (`openkiosk.db`) — a configuracao e a lista de enderecos.
- Um arquivo de preferencias do Android (`open_kiosk_prefs`) — o estado da tela e o idioma.
- O armazenamento interno do proprio navegador embutido (DOM storage) — o que o site guarda.

Nao existe banco remoto, nem servidor, nem sincronizacao. Nada sai do tablet. [confirmado]

---

## 1. Banco SQLite do Room — `openkiosk.db`

### Onde vive

- Nome do arquivo: literal `"openkiosk.db"` em **DatabaseModule.kt** :: `provideDatabase`. [confirmado]
- Caminho em disco: `/data/data/com.openkiosk/databases/openkiosk.db` — [inferido] a partir de
  `applicationId = "com.openkiosk"` em **app/build.gradle.kts** mais o nome acima. O tablet nao
  estava conectado nesta sessao (`adb devices` voltou vazio), entao o caminho nao foi visto com os
  proprios olhos; confirmar com o comando de `verified-by`.
- Criado por Hilt como `@Singleton` — uma instancia so para o processo inteiro, servida pelo
  `SingletonComponent`. **DatabaseModule.kt** :: `provideDatabase`. [confirmado]
- Construido com `Room.databaseBuilder(...).build()`, sem `addMigrations` e sem
  `fallbackToDestructiveMigration`. **DatabaseModule.kt** :: `provideDatabase`. [confirmado]

### Versao e esquema

- `@Database(entities = [ConfigEntity::class, PlaylistEntity::class], version = 1, exportSchema = false)`
  em **AppDatabase.kt**. [confirmado]
- `exportSchema = false` significa que **nao existe copia do esquema no repositorio** — nao ha
  arquivo JSON para comparar versoes. [confirmado]
- Como a versao e 1 e nunca subiu, nao ha migracao a escrever hoje. No dia em que subir para 2 sem
  migracao declarada, o Room derruba o aplicativo ao abrir o banco antigo. [inferido — regra do
  Room, nao reproduzida neste aparelho]

### Tabela `config` — o ajuste do quiosque

Formato: chave/valor, tudo texto.

```kotlin
// app/src/main/java/com/openkiosk/data/local/entity/ConfigEntity.kt
@Entity(tableName = "config")
data class ConfigEntity(
    @PrimaryKey val key: String,
    val value: String
)
```

- Numero de chaves gravadas de uma vez pelo `ConfigRepository.updateConfig(config: KioskConfig)`:
  **17**, derivado no run com
  `grep -c 'ConfigEntity("' app/src/main/java/com/openkiosk/data/repository/ConfigRepository.kt`. [confirmado]
- O conjunto lido e o conjunto escrito sao identicos — comparado mecanicamente com `comm`, sem
  diferenca nos dois sentidos. [confirmado]

Chaves, copiadas literalmente de **ConfigRepository.kt** (lista completa — gerada por extracao
mecanica das ocorrencias de `map["..."]`):

```
activeTimeoutSeconds
dimTimeoutSeconds
dimBrightnessPercent
wakeOnMotion
wakeOnProximity
wakeOnShake
motionSensitivity
cameraPulseIntervalSeconds
deepSleepEnabled
deepSleepStartHour
deepSleepEndHour
autoRefreshMinutes
lockTaskEnabled
pinEnabled
pin
startUrl
```

Como o valor volta a ser objeto: **ConfigRepository.kt** :: `observeConfig` junta as linhas num
mapa (`entities.associate { it.key to it.value }`) e converte cada chave com `toIntOrNull()`,
`toBooleanStrictOrNull()` ou `MotionSensitivity.valueOf(...)`. **Todo valor invalido ou ausente cai
no padrao de fabrica em silencio** — nao ha erro, nao ha log. [confirmado]

`motionSensitivity` guarda o NOME do enum, nao o numero. Os limiares moram em
**KioskConfig.kt** :: `enum class MotionSensitivity`:

```kotlin
enum class MotionSensitivity(val threshold: Double) {
    LOW(0.08),
    MEDIUM(0.05),
    HIGH(0.03)
}
```

O `MEDIUM = 0.05` casa com o que foi medido no aparelho: sala parada gera de 0.00001 a 0.0025, e um
movimento real mediu 0.0823 e acordou a tela. Ou seja, o limiar fica bem acima do ruido e bem
abaixo do evento real. [relatado — medicao feita em sessao, nao reproduzida agora]

### Tabela `playlist` — a lista de enderecos

```kotlin
// app/src/main/java/com/openkiosk/data/local/entity/PlaylistEntity.kt
@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val durationSeconds: Int = 30,
    val position: Int = 0
)
```

- A ordem de exibicao e a coluna `position`; toda consulta ordena por ela
  (`ORDER BY position ASC` nos dois `@Query` de **PlaylistDao.kt**). [confirmado]
- Quem escolhe a posicao de um item novo e o banco, nao o codigo Kotlin:
  **PlaylistDao.kt** :: `nextPosition` roda `SELECT COALESCE(MAX(position), -1) + 1 FROM playlist`,
  chamado por **PlaylistRepository.kt** :: `addItem` antes de inserir. Lista vazia devolve 0. [confirmado]
- Nao existe operacao de reordenar. Remover o item do meio deixa um buraco na numeracao
  (`position` nao e recalculado em `removeItem`) — o buraco nao quebra nada porque a ordenacao e
  relativa, mas a numeracao deixa de ser contigua. [confirmado por leitura de
  **PlaylistRepository.kt** :: `removeItem`, que so chama `playlistDao.delete`]
- `durationSeconds` (padrao 30) e o tempo de cada endereco no ar antes de trocar. [confirmado]

### Conversao entidade <-> dominio

- **PlaylistRepository.kt** tem duas funcoes privadas de extensao, `PlaylistEntity.toDomain()` e
  `PlaylistItem.toEntity()`, campo a campo. Os quatro campos de `PlaylistEntity` e os quatro de
  `PlaylistItem` (**domain/model/PlaylistItem.kt**) sao iguais em nome e tipo. [confirmado]
- **ConfigRepository** nao tem esse par: a ida (objeto -> 17 linhas) e a volta (linhas -> objeto)
  sao duas listas escritas a mao, uma em `updateConfig(config: KioskConfig)` e outra em
  `observeConfig`. Campo novo em `KioskConfig` exige tocar nos DOIS lugares, senao ele grava e nao
  volta, ou volta e nunca grava. [confirmado]

### Quem escreve

- **SettingsViewModel.kt** — unico ponto de escrita da configuracao pela interface, via
  `configRepository.updateConfig(key, value)` (a forma de UMA chave). [confirmado por
  `grep -rn "updateConfig(" --include="*.kt" app/src/main`]
- **SettingsViewModel.kt** — escrita da lista: `addItem`, `removeItem`, `updateItem`. [confirmado]
- **ConfigRepository.kt** :: `updateConfig(config: KioskConfig)` (a forma das 17 chaves de uma vez)
  **nao tem nenhum chamador em `app/src/main`** — so a forma de uma chave e usada.
  Portanto: implementado, inativo. [confirmado por grep no diretorio inteiro]
- Insercao usa `OnConflictStrategy.REPLACE` nos dois DAOs — gravar a mesma chave por cima
  sobrescreve sem erro. **ConfigDao.kt** :: `set` / `setAll`, **PlaylistDao.kt** :: `insert`. [confirmado]

### Quem le

- **KioskViewModel.kt** — `configRepository.observeConfig()` em dois pontos: um `StateFlow` de
  configuracao e um `collect` que reaplica ajuste ao vivo (entre outros, repassa o limiar para
  `motionDetectionManager.updateConfig(cfg.motionSensitivity.threshold)`). [confirmado]
- **SettingsViewModel.kt** — `observeConfig()` e `observePlaylist()` para desenhar a gaveta. [confirmado]
- **PlaylistManager.kt** — `playlistRepository.observePlaylist().collect { ... }`, o rodizio dos
  enderecos. [confirmado]
- Leitura e por `Flow` do Room: mexeu na tabela, quem observa recebe a lista nova sozinho, sem
  ninguem avisar. E isso que faz o ajuste valer na hora. [confirmado — `observeAll(): Flow<...>` nos
  dois DAOs]

### Buracos achados na comparacao mecanica interface x banco

Comparando as chaves que a gaveta escreve (`SettingsScreen.kt`) com as que o repositorio le:

- `startUrl` — **lida e nunca escrita por nenhuma tela**. E o endereco de reserva quando a lista
  esta vazia (**KioskViewModel.kt**: `item?.url ?: config.value.startUrl`; **KioskScreen.kt**:
  `currentUrl.ifBlank { config.startUrl }`). Na pratica fica congelada no padrao de fabrica
  definido em **KioskConfig.kt**. [confirmado]
- Os padroes de fabrica estao escritos **duas vezes**, com os mesmos valores: nos parametros de
  **KioskConfig.kt** e nos `?:` de **ConfigRepository.kt** :: `observeConfig`. Mudar um lado sem o
  outro faz o aplicativo se comportar de um jeito antes de alguem abrir a gaveta e de outro depois. [confirmado]

### Estado inicial

A tabela `config` nasce vazia: `Room.databaseBuilder(...).build()` nao tem `addCallback`, nao ha
semeadura. Ate o dono mexer na gaveta, **nao existe uma unica linha em `config`** e tudo vem dos
`?:` de `observeConfig`. [confirmado]

---

## 2. Preferencias do Android — `open_kiosk_prefs`

Todo o nome e todas as chaves moram num arquivo so, e o proprio arquivo explica por que:

```kotlin
// app/src/main/java/com/openkiosk/data/local/KioskPrefs.kt
object KioskPrefs {
    const val FILE = "open_kiosk_prefs"
    const val KEY_SCREEN_STATE = "screen_state"
    const val KEY_LANGUAGE = "language"

    fun of(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
```

- Caminho em disco: `/data/data/com.openkiosk/shared_prefs/open_kiosk_prefs.xml` — [inferido] pela
  convencao do Android mais o `FILE` acima; nao verificado no aparelho nesta sessao.
- `MODE_PRIVATE`: so o proprio aplicativo le. [confirmado]

### `screen_state` — o estado da tela

- Escrito por **ScreenStateManager.kt**: `prefs.edit().putString(KioskPrefs.KEY_SCREEN_STATE, state.name).apply()`. [confirmado]
- Lido em dois lugares:
  - **ScreenStateManager.kt**, no arranque, dentro de um `runCatching { ScreenState.valueOf(...) }` —
    valor sujo nao derruba o aplicativo, cai no padrao. [confirmado]
  - **KioskWatchdogService.kt**, o vigia que roda em processo/servico separado. [confirmado]
- Valores possiveis, de **domain/model/ScreenState.kt**: `ACTIVE`, `DIM`, `SLEEP`, `DEEP_SLEEP`. [confirmado]
- **Este e o unico dado compartilhado entre o aplicativo e o vigia.** O comentario no proprio
  KioskPrefs.kt registra o motivo de o nome viver num lugar so: se o nome for repetido dos dois
  lados e alguem renomear um, o vigia le vazio, o padrao e "acende", e o painel acende de
  madrugada sem ninguem na frente. [confirmado — costura verificada nos DOIS lados]

### `language` — o idioma escolhido a mao

- Escrito por **SettingsScreen.kt** :: `LanguageSection`, com `prefs.edit().putString("language", langCode).apply()`
  seguido de `(context as? Activity)?.recreate()`. Opcoes: `auto`, `en`, `pt`, `es`. [confirmado]
- Lido por **MainActivity.kt** :: `attachBaseContext`, com padrao `"auto"`. [confirmado]
- Por que aqui e nao no banco: `attachBaseContext` roda ANTES do Hilt injetar qualquer coisa, e a
  leitura precisa ser sincrona. O Room, sendo assincrono, chegaria tarde demais para escolher o
  idioma da tela. [inferido do posicionamento da chamada em `attachBaseContext`; casa com a
  descricao do commit 98d9efd]
- Ponto de atencao: a gravacao em `SettingsScreen.kt` usa a string literal `"language"` em vez da
  constante `KioskPrefs.KEY_LANGUAGE`, embora o arquivo de preferencias venha de `KioskPrefs.of`.
  As duas pontas coincidem hoje. [confirmado]

---

## 3. Armazenamento do navegador embutido

- **KioskWebView.kt** :: bloco `settings.apply { ... }` liga `domStorageEnabled = true`. [confirmado]
- Efeito: cada site aberto pode guardar dados no perfil padrao do WebView
  (`/data/data/com.openkiosk/app_webview/`, [inferido] pela convencao do Android — nao verificado
  no aparelho). Esse deposito **nao e gerido pelo aplicativo**: nao ha codigo que limpe, exporte ou
  leia esses dados.
- `grep -rn "CookieManager\|clearCache\|clearFormData" --include="*.kt" app/src/main` nao retorna
  nada — nao ha limpeza nem controle de cookies escrito no aplicativo. [confirmado]

---

## O que NAO persiste

- Backup do Android desligado: `android:allowBackup="false"` em **app/src/main/AndroidManifest.xml**.
  Isso significa que **nem o banco nem as preferencias entram no backup do sistema** — trocar de
  tablet nao traz nada junto. [confirmado]
- Sem `DataStore`, sem arquivo proprio em `filesDir`/`cacheDir`, sem escrita em
  `Settings.System`/`Global`/`Secure` a partir do codigo Kotlin — todos os greps voltaram vazios. [confirmado]
- Os ajustes de provisionamento feitos pelo cabo (`stay_on_while_plugged_in`, `locksettings`,
  `cmd package set-home-activity`, `appops SYSTEM_ALERT_WINDOW`) vivem no ANDROID, nunca no banco do
  aplicativo. Reinstalar o aplicativo nao os apaga; apagar os dados do aplicativo tambem nao. [relatado —
  provisionamento executado em sessao, nao reproduzido agora]

---

## Cobertura de teste

- Nenhum teste exercita SQL de verdade. `grep -rl "AppDatabase\|PlaylistDao\|ConfigDao\|Room" app/src/test`
  retorna um unico arquivo, **KioskViewModelConfigSensorsTest.kt**, e nele o `ConfigRepository` e um
  `mock()` — o Room aparece so num comentario. [confirmado]
- Nao existe pasta `app/src/androidTest` (`ls` retorna "No such file or directory"), entao nao ha
  teste instrumentado abrindo o banco no aparelho. [confirmado]
- Consequencia: erro de SQL nas consultas de **PlaylistDao.kt** (por exemplo o `COALESCE` do
  `nextPosition`) so apareceria rodando o aplicativo no tablet. [inferido]

---

## Reconciliacao com o historico

- "Room persistence for configs and playlist" (commit inicial) → [confirmado]:
  **AppDatabase.kt** declara exatamente `ConfigEntity` e `PlaylistEntity`.
- "Hilt dependency injection throughout" → [confirmado] nesta fatia: **DatabaseModule.kt** provê
  banco e os dois DAOs pelo `SingletonComponent`.
- "URL playlist with configurable rotation timers" → [confirmado]: `durationSeconds` por item em
  **PlaylistEntity.kt**, consumido por **PlaylistManager.kt** via `observePlaylist()`.
- "Manual override available in settings via SharedPreferences — read synchronously in
  attachBaseContext before Hilt injection" (commit de i18n) → [confirmado] nos DOIS lados:
  escrita em **SettingsScreen.kt** :: `LanguageSection`, leitura em **MainActivity.kt** ::
  `attachBaseContext`.
- "Deep sleep activated by configurable time range" → [confirmado] no que toca a persistencia:
  chaves `deepSleepEnabled`, `deepSleepStartHour`, `deepSleepEndHour` em **ConfigRepository.kt**, com
  padrao 22h/6h em **KioskConfig.kt**.
- "PIN toggle (disabled by default)" → [confirmado]: `pinEnabled: Boolean = false` em
  **KioskConfig.kt** e o mesmo padrao no `?:` de `observeConfig`.
- "Proximity and accelerometer sensor wake" → a chave `wakeOnProximity` existe e nasce ligada
  (**KioskConfig.kt**). No Fire HD 8 testado nao ha sensor de proximidade (`pm list features` so
  lista acelerometro), entao **essa chave nao tem efeito neste aparelho** — persistida, inerte. [relatado
  quanto a medicao do aparelho; confirmado quanto a existencia e ao padrao da chave]
- "Lower pixel threshold from 30 to 15" / "Limiar por pixel 15 -> 20" → fora desta fatia; esse
  numero vive no analisador de camera, nao em nenhum deposito. Nao entra aqui.
- Estados do ciclo de sono: o commit inicial fala em `ACTIVE/DIM/SLEEP`, e hoje **ScreenState.kt**
  tem quatro valores (o `DEEP_SLEEP` entrou depois, no commit de gestao de energia). O valor
  gravado em `screen_state` pode ser qualquer um dos quatro. [confirmado]

### Invalidacao proposta

- Memoria "Config via local drawer (swipe left, PIN protected)" → o codigo contradiz o
  "PIN protected" incondicional. **KioskConfig.kt** :: `pinEnabled = false` e
  **ConfigRepository.kt** :: `observeConfig` (`map["pinEnabled"]... ?: false`) mostram que a gaveta
  abre SEM PIN de fabrica. Redacao correta: "gaveta local, com PIN opcional, desligado de fabrica".

---

## Pendencia registrada (nao implementar)

[TODO: sem cobertura declarada] A lista de enderecos e os ajustes nao tem como sair do aparelho.
Trocar de tablet exige redigitar tudo a mao. A fazer: salvar e restaurar a configuracao em arquivo,
pela propria gaveta de ajustes.

Contexto que agrava a pendencia, apurado aqui:
- `android:allowBackup="false"` no manifesto — o backup do sistema tambem nao carrega esses dados. [confirmado]
- `exportSchema = false` — nao ha esquema versionado no repositorio para guiar leitura ou escrita de
  um arquivo de exportacao. [confirmado]
- A funcao que gravaria a configuracao inteira de uma vez ja existe e esta sem uso
  (**ConfigRepository.kt** :: `updateConfig(config: KioskConfig)`), o que a torna o ponto natural do
  "restaurar" quando isso for feito. [confirmado — implementado, inativo]
