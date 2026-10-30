# Design system TCL do fork

Os valores visuais (cor, tamanho, texto, tempo) ficam em **tokens**. As telas são montadas com **7 componentes**. Tudo usa Views e XML, como o resto do app: nada de Compose e nenhuma biblioteca nova.

Os valores vêm do APK da TCL Live TV (`com.tcl.tv`, recursos decodificados), sem nada medido no olho. Quando a tela do fork já usava um valor diferente, ficou o valor da tela, porque a meta da 1.5a era **diferença zero**. Essas divergências estão anotadas abaixo.

## Tokens

Arquivos: `app/src/main/res-aosp/app/values/tcl_colors.xml`, `tcl_dimens.xml`, `tcl_type.xml`, `tcl_motion.xml` e `tcl_styles.xml` (componentes).

### Cor

| Token | Valor | Nome na TCL | Uso |
|---|---|---|---|
| `tcl_color_text_primary` | `#E6FFFFFF` | `element_text_color_focus` | título, número e nome no banner |
| `tcl_color_text_secondary` | `#B3FFFFFF` | `element_text_color_normal` | programa, horários, canal anterior/próximo, chip |
| `tcl_color_text_tertiary` | `#66FFFFFF` | `element_text_color_weak` | régua de horas do EPG |
| `tcl_color_text_soft` | `#B3F0F0F2` | `element_primary_white_70` | título "Guia", relógio, linha da lista |
| `tcl_color_text_strong` | `#E6F0F0F2` | `element_primary_white_90` | texto do programa no EPG |
| `tcl_color_text_weak` | `#66F0F0F2` | `element_primary_white_40` | nome do canal no EPG |
| `tcl_color_focus_bg` | `#FFF0F0F2` | `channeltype_color_focused1` | barra clara de foco |
| `tcl_color_on_focus` | `#E6000000` | `channeltype_color_focused2` | texto e ícone sobre o foco |
| `tcl_color_on_focus_secondary` | `#99000000` | `element_primary_black_60` | 2ª linha sobre o foco |
| `tcl_color_icon` | `#FFF0F0F2` | `element_color_ui5_white` | ícones do trilho |
| `tcl_color_surface_panel` | `#CC0A0A0A` | (layout `activity_channellist`) | coluna de canais |
| `tcl_color_surface_rail` | `#E1000000` | (layout `activity_channellist`) | trilho |
| `tcl_color_surface_banner` | `#6618191D` | `bg_focus_info` | cartão do banner |
| `tcl_color_banner_stroke` | `#B2999999` | `bg_focus_info` | borda do cartão |
| `tcl_color_surface_cell` / `_selected` | `#1FF0F0F2` / `#3DF0F0F2` | `element_primary_white_12/24` | célula de canal no EPG |
| `tcl_color_surface_logo` | `#80000000` | `element_primary_black_50` | fundo da logo |
| `tcl_color_surface_chip` | `#14F0F0F2` | `element_tcl_tag_bg_normal_color` | chip |
| `tcl_color_divider` | `#1FF0F0F2` | `element_primary_white_12` | divisória trilho/lista |
| `tcl_color_tick` / `_now` | `#4DFFFFFF` / `#A3FFFFFF` | `epg_pro_time_bar_divider_color` | marcas de meia hora |
| `tcl_color_accent_live` | `#FFFA2C25` | `element_tcl_card_red` | ícone de "tocando", linha do "agora" |
| `tcl_color_progress` / `_track` | `#FFE7211A` / `#66F0F0F2` | `epg_detail_seek_bar` | barra de progresso do detalhe |
| `tcl_color_scrim` | `#FF000000` | — | faixa preta à esquerda do EPG |

Base: `tcl_white_{100,90,70,40,24,12,8}` (TCL `element_primary_white_*`, cor `#F0F0F2`) e `tcl_black_{100,97,96,90,88,70,60,50,40,15,0}` (pretos das máscaras). Os layouts usam os tokens semânticos; a base só aparece nas máscaras em gradiente.

### Tipografia (`TextAppearance.Tcl.*`)

| Estilo | Tamanho | Onde |
|---|---|---|
| `Hero` | 40sp | título no detalhe do programa |
| `Display` | 28sp | número no banner (pelo dimen do AOSP) |
| `Title` | 24sp | título no topo do EPG |
| `Headline` | 19sp | "Guia de programação", descrição no EPG |
| `Body` | 16sp | número e nome na lista, relógio, canal no detalhe |
| `Label` | 14sp | programa na lista, nome no banner, horas do EPG |
| `Caption` | 12sp | chip, horários do detalhe |

O estilo define **só o tamanho**. Cor, peso (`sans-serif-medium`) e espaçamento entre letras (`0.04`) continuam na view. Assim, trocar `textSize` por `textAppearance` não muda nada na tela.

### Espaço, raio e tamanhos

- **Espaço** (escala de 4dp): `tcl_space_4/8/12/16/20/24/32`; divisória `tcl_divider` (1px).
- **Raio:** `tcl_radius_cell` 6dp (foco, célula), `tcl_radius_panel` 8dp (banner), `tcl_radius_logo` 4dp, `tcl_radius_chip` 21dp, `tcl_radius_progress` 1dp.
- **Logo:** `tcl_logo_small_*` 46×26dp (lista, vizinhos no banner) e `tcl_logo_large_*` 64×36dp (banner, detalhe).
- **Componentes:** medidas exatas da TCL por componente (`tcl_rail_*`, `tcl_channel_row_*`, `tcl_banner_*`, `tcl_epg_*`, `tcl_dialog_*`, `tcl_chip_*`, `tcl_option_row_*`).
- **Alfa:** `tcl_alpha_unselected` 0.6 (categoria não escolhida no trilho).

### Tempo (`tcl_motion.xml`, em ms)

| Token | Valor | Uso |
|---|---|---|
| `tcl_motion_hide_banner` | 8000 | banner some sozinho |
| `tcl_motion_hide_list` | 15000 | lista some sozinha |
| `tcl_motion_hide_guide` | 60000 | EPG some sozinho |
| `tcl_motion_fast` | 150 | fade da grade e do detalhe do EPG |
| `tcl_motion_normal` | 250 | entrada e saída do EPG |

Os inteiros do AOSP (`channel_banner_show_duration`, `program_guide_*`) apontam para esses tokens.

### Divergências em relação ao plano aprovado (mantido o que está na tela)

- **Raios:** o plano previa só 6 e 8dp. A tela também usa 4dp (logo), 21dp (chip, igual à TCL) e 1dp (barra de progresso). Viraram tokens próprios.
- **Tipografia:** o detalhe do programa usa 40sp, como no `fragment_epgdetail_pro` da TCL. Entrou como `Hero`, acima da escala de 6 tamanhos.
- **Branco do texto:** a TCL usa dois brancos, `#FFFFFF` (`element_text_color_*`) e `#F0F0F2` (`element_primary_white_*`), e o fork já usava os dois. Por isso há `text_secondary` (`#B3FFFFFF`) e `text_soft` (`#B3F0F0F2`), em vez de um token só.
- **Espaço:** medidas fora da escala de 4dp (13, 15, 21, 25, 58, 78dp…) são da TCL e ficaram como tokens do componente, não da escala.
- **Marcas da régua do EPG:** `#A3FFFFFF` não está na paleta `element_*` da TCL; é o valor que o fork já mostrava.

## Componentes

Cada componente é um layout `tcl_*.xml` ou um estilo `Widget.Tcl.*`. Só ganha classe Kotlin quando tem comportamento. Capturas em `docs/design-system/golden/`.

| Componente | Arquivos | Para que serve | Onde é usado | Captura |
|---|---|---|---|---|
| **TclChannelRow** | `layout/tcl_channel_row.xml`, `tcl_channel_row_compact.xml`, `ui/TclChannelRow.kt` | Logo, número, nome, programa e "tocando". `bind(channel, program, isPlaying)` | lista lateral; banner (canal anterior/próximo) | `lista_todos.png`, `banner.png` |
| **TclLogoCard** | `Widget.Tcl.LogoCard` / `.Framed`, `TclChannelRow.loadLogo()` | Logo encaixada, com ou sem o cartão escuro. Carrega do TIF e, se falhar, do feed | lista, banner, detalhe | `lista_todos.png`, `detalhe.png` |
| **TclChip** | `layout/tcl_chip.xml`, `Widget.Tcl.Chip` | Etiquetas FHD/CC/áudio | topo do EPG e detalhe (`GuideTags`) | `epg.png` ("HD") |
| **TclRail / TclRailItem** | `Widget.Tcl.Rail`, `layout/tcl_rail_item.xml` | Trilho de ícones com foco claro | lista lateral (Todos/Transmissão/Antena/IPTV e engrenagem) | `lista_transmissao.png` |
| **TclPanel** | `Widget.Tcl.Panel` | Coluna escura de 280dp ao lado do trilho | lista lateral | `lista_todos.png` |
| **TclOptionRow** | `layout/tcl_option_row.xml`, `Widget.Tcl.OptionRow` | Opção com o valor à direita, mesmo foco da lista | ainda não usado: é a base da 1.5b | — |
| **TclDialog** | `Widget.Tcl.Dialog`, `Widget.Tcl.Dialog.Content` | Tela cheia sobre o vídeo, com máscara preta; segura as teclas | detalhe do programa no EPG | `detalhe.png` |

**Onde o `TclChannelRow` não entrou:** a célula de canal do EPG (`program_guide_table_header_column_item`) continua com o binder do `ProgramTableAdapter`. Ela tem regras próprias que a linha não tem: canal bloqueado com cadeado, número menor com mais de 4 dígitos e logo invisível até carregar. Passar essas regras para o componente mudaria o comportamento, e a 1.5a não podia mudar nada. O texto da célula já usa os tokens.

**"Sem informações" num lugar só:** `TclChannelRow.titleOrNoInformation(res, title)` é a única regra de "programa sem título" (o `ChannelBannerView` usa para o banner). `TclChannelRow.programTitle(context, program)` acrescenta o episódio (lista e topo do EPG). EPG, menu e detalhe chamam essas funções.

## Padrões de código (A1, item 3)

- **Um controlador de telas:** tudo que aparece sobre o vídeo passa pelo `TvOverlayManager`, que decide quem fica visível, o Voltar e o auto-esconder. Nenhuma tela se abre sozinha.
- **Dados → tela num lugar só:** cada componente tem um binder (ex.: `TclChannelRow.bind(channel, program, isPlaying)`). A mesma regra não se repete em 3 telas.
- **Fontes de dados atrás de uma porta:** primeiro o TIF, depois o feed (`ProgramDataManager`, `FeedGuide`). A tela não sabe de onde veio o dado.
- **Nada de valor solto:** cor, tamanho e espaço só por token. O passo **Design tokens** do CI (`.github/scripts/check-design-tokens.sh`) falha se aparecer `#…` ou `sp`/`dp`/`px` literal em `layout/tcl_*`, `channel_banner`, `channel_list_overlay`, `program_guide*`, `color/tcl_*`, `drawable/tcl_*` e `values/tcl_styles.xml`. Só `0dp` é permitido, porque significa "sem tamanho" ou "preencher". Os arquivos `values/tcl_*.xml` são a definição dos tokens e os arquivos antigos do AOSP ficam fora.
- **Nada de Compose nem biblioteca nova** sem necessidade medida.

## Capturas de referência

Precisa do emulador `emulator-5554` com o tif-falso e o fork (`plano-canais.md`, parte D).

```bash
sh docs/design-system/capturar.sh <pasta>
```

```bash
python docs/design-system/comparar.py docs/design-system/golden <pasta> <pasta-diff>
```

- `capturar.sh` republica a Channel+ falsa, abre o fork, sintoniza o **9445** pelas teclas de número e captura sempre na mesma ordem: `banner`, `lista_todos`, `lista_transmissao`, `lista_antena`, `epg`, `detalhe`, `menu`. Leva cerca de 1 min. A captura usa `adb emu screenrecord screenshot`, porque o `screencap` falha com o vídeo seguro na tela.
- `comparar.py` compara pixel a pixel e sai com 1 se alguma tela diferir. Ele **mascara** o relógio do EPG, a barra de progresso do cartão do menu e a linha vermelha do "agora", que anda com o relógio.
- **A grade do EPG muda a cada meia hora.** Para comparar duas versões, rode `capturar.sh` com o APK antigo e logo depois com o novo, dentro da mesma meia hora, e compare as duas pastas. As capturas em `golden/` servem de referência visual; elas são da versão anterior à 1.5a (`1d705c1`).

### Resultado da 1.5a

As capturas antes (APK `1d705c1`) e depois (APK da 1.5a) foram feitas em seguida e comparadas com `comparar.py`. Resultado detalhado em `plano-canais.md` (etapa 1.5a).
