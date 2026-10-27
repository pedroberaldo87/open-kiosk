#!/usr/bin/env bash
# Fotografa o estado do Fire que o plano 2026-10-26-fire-limpeza mexe, lido do tablet por adb,
# com o comando que devolve cada valor. Rodar de novo e comparar com `diff` mostra o que a
# Amazon religou sozinha.
# Uso: .claude/fire-estado.sh > .claude/fire-estado-AAAA-MM-DD.md
set -u
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
sh_() { "$ADB" shell "$@" | tr -d '\r'; }

CHAVES="global:LOCKSCREEN_AD_ENABLED global:location_global_kill_switch global:private_dns_mode
global:private_dns_specifier global:heads_up_notifications_enabled global:stay_on_while_plugged_in
secure:search_on_lockscreen_settings secure:screensaver_enabled secure:screensaver_activate_on_sleep
secure:screensaver_activate_on_dock secure:limit_ad_tracking secure:amz_limit_ad_tracking
secure:non_amz_limit_ad_tracking secure:USAGE_METRICS_UPLOAD_ENABLED secure:usage_metrics_marketing_enabled"

echo "# Estado do Fire — $(sh_ getprop ro.product.model) · $(sh_ getprop ro.build.version.name)"
echo
echo "## Chaves de ajuste (valor lido · comando que devolve este valor)"
echo
for c in $CHAVES; do
  ns="${c%%:*}"; k="${c#*:}"; v="$(sh_ settings get "$ns" "$k")"
  if [ "$v" = "null" ]; then undo="settings delete $ns $k"; else undo="settings put $ns $k $v"; fi
  echo "- \`$ns $k\` = \`$v\` · desfazer: \`adb shell $undo\`"
done
echo
echo "## Apps desligados (pm list packages -d) · desfazer cada um: \`adb shell pm enable <pacote>\`"
echo
sh_ pm list packages -d | sed 's/^package:/- /' | sort
echo
echo "## Quiosque"
echo
echo "- Home: \`$(sh_ cmd package resolve-activity -c android.intent.category.HOME -a android.intent.action.MAIN | grep -m1 -o 'packageName=[^ ]*')\`"
echo "- Activity à frente: \`$(sh_ dumpsys activity activities | grep -m1 -o 'mResumedActivity: ActivityRecord{[^}]*}')\`"
echo "- SYSTEM_ALERT_WINDOW do quiosque: \`$(sh_ appops get com.openkiosk SYSTEM_ALERT_WINDOW)\`"
echo "- Fuso: \`$(sh_ getprop persist.sys.timezone)\`"
echo "- Registrador de memória vivo: \`$(sh_ ps -A -o PID,ARGS | grep -c '[g]lwatch.sh')\` processo(s)"
echo
echo "## Contas"
echo
sh_ dumpsys account | grep 'Account {' | grep -oE 'type=[a-z.]+' | sort | uniq -c | sed 's/^ */- /'
