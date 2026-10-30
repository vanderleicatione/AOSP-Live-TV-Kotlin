#!/bin/sh
# Capturas de referência do design system no emulador (docs/design-system.md).
# Uso: sh docs/design-system/capturar.sh <pasta-de-saida>
# Precisa do emulador emulator-5554 ligado com o tif-falso e o fork instalados.
# Sempre os mesmos passos: republica a Channel+ (programas em volta da hora atual),
# abre o fork, sintoniza o 9445 pelas teclas de número e percorre as telas.
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
# A captura do emulador passa por fora do Android: funciona com o vídeo seguro na tela.
shot() {
  rm -f "$TMP"/*.png
  $E emu screenrecord screenshot "$TMPW" >/dev/null
  mv "$TMP"/*.png "$OUT/$1.png"
  echo "  $1.png"
}

$E shell am start -n com.tcl.channelplus/tif.falso.Setup >/dev/null
sleep 4
$E shell am force-stop com.android.tv
$E shell am start -n com.android.tv/.TvActivity >/dev/null
sleep 8
key 16 11 11 12            # 9445 That's ROCK (Channel+ com guia no TIF)
sleep 4;  shot banner
sleep 9                    # banner some sozinho (8 s)
key DPAD_CENTER;            sleep 2; shot lista_todos
key DPAD_LEFT DPAD_DOWN;    sleep 2; shot lista_transmissao
key DPAD_DOWN;              sleep 2; shot lista_antena
key BACK;                   sleep 2
key --longpress DPAD_CENTER; sleep 4; shot epg
key DPAD_RIGHT DPAD_CENTER; sleep 3; shot detalhe
key BACK;                   sleep 1
key BACK;                   sleep 2
key MENU;                   sleep 3; shot menu
key BACK
rmdir "$TMP"
