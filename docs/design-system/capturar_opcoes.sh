#!/bin/sh
# Capturas de referência das telas de opções (1.5b, docs/design-system.md).
# Uso: sh docs/design-system/capturar_opcoes.sh <pasta-de-saida>
# Mesmo emulador e mesmo canal de capturar.sh; abre cada tela pelas teclas e volta com Voltar.
set -e
OUT=${1:?pasta de saida}
ADB=${ADB:-C:/Users/Chaintech/AppData/Local/tvbuild/sdk/platform-tools/adb.exe}
export MSYS_NO_PATHCONV=1
E="$ADB -s emulator-5554"
mkdir -p "$OUT"
TMP="$OUT/.tmp"
mkdir -p "$TMP"
TMPW=$(cygpath -w "$TMP" 2>/dev/null || echo "$TMP")

key() { $E shell input keyevent "$@"; }
shot() {
  rm -f "$TMP"/*.png
  $E emu screenrecord screenshot "$TMPW" >/dev/null
  mv "$TMP"/*.png "$OUT/$1.png"
  echo "  $1.png"
}
# Menu com a linha "Opções da TV" em foco (o primeiro cartão é a legenda).
options_row() { key MENU; sleep 3; key DPAD_DOWN; sleep 2; }

$E shell am start -n com.tcl.channelplus/tif.falso.Setup >/dev/null
sleep 4
$E shell am force-stop com.android.tv
$E shell am start -n com.android.tv/.TvActivity >/dev/null
sleep 8
key 16 11 11 12            # 9445 That's ROCK
sleep 13                   # banner some sozinho (8 s)

options_row;                       shot opcoes_menu
key DPAD_CENTER;          sleep 2; shot opcoes_legenda
key BACK;                 sleep 2
# Cartões desligados (sem faixa de vídeo/áudio no emulador) não pegam foco: a última → é Configurações.
options_row; key DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT; sleep 2
key DPAD_CENTER;          sleep 2; shot opcoes_configuracoes
key DPAD_CENTER;          sleep 2; shot opcoes_personalizar   # foco no canal atual; OK aqui o esconderia
# Sobe até "Agrupar por" (primeiro item; a lista tem uns 190 itens) e confere antes de abrir.
key $(for i in $(seq 220); do printf 'DPAD_UP '; done); sleep 2
$E shell uiautomator dump /sdcard/opcoes.xml >/dev/null
$E shell cat /sdcard/opcoes.xml | grep -qE 'focused="true"[^>]*>[^>]*>[^>]*text="(Group by|Agrupar por)"' \
  || { echo "foco fora de 'Agrupar por'; parando para nao mudar um canal" >&2; key BACK BACK BACK; exit 1; }
key DPAD_CENTER;          sleep 2; shot opcoes_agrupar
key BACK;                 sleep 2   # volta a Personalizar
key BACK;                 sleep 2   # volta a Configurações
key DPAD_DOWN DPAD_CENTER; sleep 4; shot opcoes_fontes
key BACK;                 sleep 2
rmdir "$TMP"
