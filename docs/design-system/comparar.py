"""Compara duas pastas de capturas (capturar.sh) pixel a pixel, fora das áreas mascaradas.
Uso: python docs/design-system/comparar.py <referencia> <nova> [pasta-diff]
Sai com 1 se alguma tela diferir. As máscaras cobrem o que muda com o relógio."""
import os, sys
from PIL import Image, ImageChops, ImageDraw

# (x0, y0, x1, y1) em pixels de 1920x1080.
MASCARAS = {
    # Data e hora do EPG (o detalhe abre por cima do EPG).
    "epg": [(1300, 60, 1920, 110)],
    "detalhe": [(1300, 60, 1920, 110)],
    # Barra de progresso do programa no cartão do canal.
    "menu": [(410, 874, 720, 890)],
}
# A linha do "agora" e a régua do EPG andam com o relógio: rode as duas versões em seguida
# (capturar.sh com o APK antigo, depois com o novo) para a grade cair na mesma meia hora.

ref, nova = sys.argv[1], sys.argv[2]
saida = sys.argv[3] if len(sys.argv) > 3 else None


def colunas_do_agora(im):
    """Colunas da linha vermelha do "agora" no EPG (anda com o relógio)."""
    w, h = im.size
    px = im.load()
    cols = set()
    for x in range(w):
        if sum(1 for y in range(300, h, 4) if px[x, y][0] > 40 and px[x, y][0] > 2 * max(px[x, y][1], px[x, y][2])) > 40:
            cols.update(range(x - 12, x + 13))
    return [(x, 230, x, h) for x in cols]


falhou = False
for nome in sorted(f for f in os.listdir(ref) if f.endswith(".png")):
    a = Image.open(os.path.join(ref, nome)).convert("RGB")
    b = Image.open(os.path.join(nova, nome)).convert("RGB")
    mascaras = list(MASCARAS.get(nome[:-4], []))
    if nome[:-4] in ("epg", "detalhe"):
        mascaras += colunas_do_agora(a) + colunas_do_agora(b)
    for m in (a, b):
        d = ImageDraw.Draw(m)
        for r in mascaras:
            d.rectangle(r, fill=(255, 0, 255))
    diff = ImageChops.difference(a, b)
    caixa = diff.getbbox()
    n = sum(1 for p in diff.get_flattened_data() if p != (0, 0, 0)) if caixa else 0
    print(f"{nome:24} {'igual' if not caixa else f'{n} px diferentes em {caixa}'}")
    if caixa:
        falhou = True
        if saida:
            os.makedirs(saida, exist_ok=True)
            diff.point(lambda v: 255 if v else 0).save(os.path.join(saida, nome))
sys.exit(1 if falhou else 0)
