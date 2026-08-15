<!-- project-doc:v2 gen=3.8 -->
# OPEN-KIOSK

Navegador de quiosque para Android, de código aberto, feito para letreiro digital. Exibe páginas
web em tela cheia num tablet fixo e usa a câmera frontal para perceber que tem gente na frente e
acender a tela. Clone do Fully Kiosk Browser.

**Pilha:** Kotlin · Jetpack Compose · CameraX · Room · Hilt · WebView — `minSdk` 28, `compileSdk` 34
**Aparelho-alvo:** Amazon Fire HD 8 (`KFRAPWI`, Fire OS com base Android 11), fixo na parede e na tomada

## Comandos rápidos

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # não há java no PATH
./gradlew compileDebugKotlin testDebugUnitTest lintDebug   # o portão: tem que sair BUILD SUCCESSFUL
./gradlew assembleDebug                                    # gera app/build/outputs/apk/debug/
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
~/Library/Android/sdk/platform-tools/adb logcat -s ScreenState:D KioskViewModel:D MotionDetection:D KioskWatchdog:D PowerState:D
```

Contagens deste projeto (derive, não confie no número escrito):
`find app/src/main -name '*.kt' | wc -l` · `find app/src/test -name '*.kt' | wc -l` · `ls .claude/docs/*.md | wc -l`

## As armadilhas que mordem de verdade

- **Movimento se mede descontando a luz global.** Comparar dois quadros direto faz a tela
  escurecendo virar "pessoa passando" — foi o defeito que acendia o painel de madrugada.
- **Sem a janela do aplicativo na frente, a máquina de estado de tela CONGELA.** Transição
  disparada com o aplicativo pausado grava um estado que nenhum painel viveu, e o serviço vigia
  lê esse estado fantasma como "não acenda".
- **Toda tela de navegação antiga tem que ser destruída na mão.** Trocar a chave do Compose só
  tira a antiga da árvore; sem `destroy()` ela vaza e o sistema mata o processo por memória.
- **Decisão irreversível espera a configuração real do banco.** O valor de fábrica chega primeiro
  e pediria câmera a quem a desligou, ou entraria na trava de quiosque de quem a desativou.
- **O registro de dono do dispositivo NÃO passa no Fire de produção** (o Controle Parental da
  Amazon já ocupa o posto). O que substitui é o provisionamento por cabo — ver o doc de publicação.

## Documentação

- **[architecture.md](docs/architecture.md)** → mexer nas camadas, na injeção de dependência ou na máquina de estado de tela; entender quem chama quem
- **[runtime.md](docs/runtime.md)** → seguir um caminho ponta a ponta: acordar por movimento, ciclo de sono, relance, troca de energia, arranque frio, recuperação da página, mudança de ajuste
- **[patterns.md](docs/patterns.md)** → escrever código novo aqui: convenções, armadilhas e o que cada teste cobre
- **[data-stores.md](docs/data-stores.md)** → mexer no banco, nas preferências ou em qualquer coisa que persiste
- **[durability.md](docs/durability.md)** → responder "e se o aparelho quebrar?": o que tem cópia e o que não tem
- **[deploy.md](docs/deploy.md)** → compilar, assinar, publicar, ou provisionar um tablet novo por cabo

## Knowledge Graph (graphify)

Existe um grafo do código em `graphify-out/` (`graph.json`, `graph.html`, `GRAPH_REPORT.md`).
Consulte-o antes de mexer em código que você não conhece: ele diz quem depende de quem e quais
peças são mais conectadas. Atualize com `graphify update . --force` (só análise estática, sem custo
de modelo).

## Custom Rules

- Escreva comentário de código explicando **por que**, não o que a linha faz.
- Constante que é botão de calibração de campo leva o comentário `knob de campo:` dizendo para
  que lado mexer.
- Nunca commitar `local.properties`, a chave de assinatura, nem valor de senha.

<!-- project-doc:v2:end -->
