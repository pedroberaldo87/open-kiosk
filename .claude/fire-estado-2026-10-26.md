# Estado do Fire — KFRAPWI · Fire OS 8.3.3.8 (RS8338/3339)

## Chaves de ajuste (valor lido · comando que devolve este valor)

- `global LOCKSCREEN_AD_ENABLED` = `1` · desfazer: `adb shell settings put global LOCKSCREEN_AD_ENABLED 1`
- `global location_global_kill_switch` = `0` · desfazer: `adb shell settings put global location_global_kill_switch 0`
- `global private_dns_mode` = `null` · desfazer: `adb shell settings delete global private_dns_mode`
- `global private_dns_specifier` = `null` · desfazer: `adb shell settings delete global private_dns_specifier`
- `global heads_up_notifications_enabled` = `1` · desfazer: `adb shell settings put global heads_up_notifications_enabled 1`
- `global stay_on_while_plugged_in` = `7` · desfazer: `adb shell settings put global stay_on_while_plugged_in 7`
- `secure search_on_lockscreen_settings` = `1` · desfazer: `adb shell settings put secure search_on_lockscreen_settings 1`
- `secure screensaver_enabled` = `1` · desfazer: `adb shell settings put secure screensaver_enabled 1`
- `secure screensaver_activate_on_sleep` = `1` · desfazer: `adb shell settings put secure screensaver_activate_on_sleep 1`
- `secure screensaver_activate_on_dock` = `1` · desfazer: `adb shell settings put secure screensaver_activate_on_dock 1`
- `secure limit_ad_tracking` = `0` · desfazer: `adb shell settings put secure limit_ad_tracking 0`
- `secure amz_limit_ad_tracking` = `0` · desfazer: `adb shell settings put secure amz_limit_ad_tracking 0`
- `secure non_amz_limit_ad_tracking` = `0` · desfazer: `adb shell settings put secure non_amz_limit_ad_tracking 0`
- `secure USAGE_METRICS_UPLOAD_ENABLED` = `1` · desfazer: `adb shell settings put secure USAGE_METRICS_UPLOAD_ENABLED 1`
- `secure usage_metrics_marketing_enabled` = `1` · desfazer: `adb shell settings put secure usage_metrics_marketing_enabled 1`

## Apps desligados (pm list packages -d) · desfazer cada um: `adb shell pm enable <pacote>`


## Quiosque

- Home: `packageName=com.amazon.firelauncher`
- Activity à frente: `mResumedActivity: ActivityRecord{6ab39a1 u0 com.openkiosk/.presentation.MainActivity t142}`
- SYSTEM_ALERT_WINDOW do quiosque: `SYSTEM_ALERT_WINDOW: allow; time=+1h26m44s364ms ago; rejectTime=+72d10h23m54s324ms ago`
- Fuso: `America/Sao_Paulo`
- Registrador de memória vivo: `1` processo(s)

## Contas

- 6 type=amazon.account
- 1 type=com.amazon.account
- 1 type=com.amazon.account.contacts
- 1 type=com.amazon.account.whispersync

## Recusas e reversões medidas durante a execução (2026-10-26)

- F3.1 · `cmd package set-home-activity com.openkiosk/.presentation.MainActivity` → `Success`, mas `resolve-activity HOME` segue `com.amazon.firelauncher` e o botão Home abre `com.amazon.firelauncher/.Launcher`. `cmd role add-role-holder android.app.role.HOME com.openkiosk 0` também sem efeito. O vigia do quiosque traz a tela de volta em ~6 s.
- F1.1/F1.4 · `global LOCKSCREEN_AD_ENABLED` e `global location_global_kill_switch` voltam a `1` e `0` em todo reinício (medido em 2 reinícios, 19:23 e 19:26). Os 9 ajustes `secure` e os 16 apps desligados sobreviveram.
