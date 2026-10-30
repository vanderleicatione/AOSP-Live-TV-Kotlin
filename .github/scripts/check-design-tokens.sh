#!/bin/sh
# Design system (docs/design-system.md): no literal colour or size in the design-system layouts and
# styles; they use the tokens in values/tcl_*.xml. "0dp" is allowed (it means "no size / fill").
cd "$(dirname "$0")/../../app/src/main/res-aosp/app" || exit 2
FILES=$(ls layout/tcl_*.xml layout/channel_banner.xml layout/channel_list_overlay.xml layout/program_guide*.xml \
  color/tcl_*.xml drawable/tcl_*.xml values/tcl_styles.xml)
if grep -nE '"#[0-9A-Fa-f]{3,8}"|>#[0-9A-Fa-f]{3,8}<|"-?[0-9.]+(sp|dp|dip|px)"|>-?[0-9.]+(sp|dp|dip|px)<' $FILES \
  | grep -v '"0dp"'; then
  echo "Literal colour/size above: use a token from values/tcl_colors.xml, tcl_dimens.xml or tcl_type.xml." >&2
  exit 1
fi
echo "Design tokens: OK ($(echo $FILES | wc -w) files)"
