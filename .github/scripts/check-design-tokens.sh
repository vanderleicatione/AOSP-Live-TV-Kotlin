#!/bin/sh
# Design system (docs/design-system.md): no literal colour or size in the design-system layouts and
# styles; they use the tokens in values/tcl_*.xml. "0dp" is allowed (it means "no size / fill").
cd "$(dirname "$0")/../../app/src/main/res-aosp/app" || exit 2
# 1.5b: also the options screens (menu cards, side panel rows); option_item_rating/attribution are not shown.
FILES=$(ls layout/tcl_*.xml layout/channel_banner.xml layout/channel_list_overlay.xml layout/program_guide*.xml \
  layout/menu.xml layout/menu_card_*.xml layout/item_list.xml layout/option_container.xml layout/option_fragment.xml \
  layout/option_item_action.xml layout/option_item_check_box.xml layout/option_item_common.xml \
  layout/option_item_divider.xml layout/option_item_radio_button.xml layout/option_item_switch.xml \
  layout/option_item_channel_*.xml color/tcl_*.xml drawable/tcl_*.xml values/tcl_styles.xml)
if grep -nE '"#[0-9A-Fa-f]{3,8}"|>#[0-9A-Fa-f]{3,8}<|"-?[0-9.]+(sp|dp|dip|px)"|>-?[0-9.]+(sp|dp|dip|px)<' $FILES \
  | grep -v '"0dp"'; then
  echo "Literal colour/size above: use a token from values/tcl_colors.xml, tcl_dimens.xml or tcl_type.xml." >&2
  exit 1
fi
echo "Design tokens: OK ($(echo $FILES | wc -w) files)"
