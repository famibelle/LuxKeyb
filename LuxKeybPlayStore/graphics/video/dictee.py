"""Fabrique la dictée mot à mot à partir de la vraie capture de dictée.

Les trois lignes de la bulle sont masquées puis révélées mot par mot : les
blancs entre les mots sont trouvés dans la capture elle-même (colonnes sans
encre), pas devinés.
"""
import json, pathlib
from PIL import Image

V = pathlib.Path(__file__).parent
SRC = pathlib.Path("/home/medhi/SourceCode/LuxKeyb/LuxKeybPlayStore/graphics/"
                   "captures-emulateur-pixel9/20-clavier-dictee-luxasr.png")
LIGNES = [(1425, 1490), (1488, 1550), (1550, 1615)]
X0, X1 = 140, 720

img = Image.open(SRC).convert("RGB")
fond = img.getpixel((120, 1520))
px = img.load()


def encre(x, y0, y1):
    return any(sum(px[x, y]) < 450 for y in range(y0, y1))


# coupures : fin de chaque mot, ligne par ligne
mots = []  # (ligne, x_fin)
for li, (y0, y1) in enumerate(LIGNES):
    cols = [encre(x, y0, y1) for x in range(X0, X1)]
    x, vide = 0, 0
    fin = None
    for i, c in enumerate(cols):
        if c:
            if fin is not None and vide >= 12:
                mots.append((li, X0 + fin + 1))
            vide, fin = 0, i
        else:
            vide += 1
    if fin is not None:
        mots.append((li, X0 + fin + 1))

d = V / "dictee"
d.mkdir(exist_ok=True)


def etat(k):
    """Image avec les k premiers mots visibles."""
    im = img.copy()
    visibles = mots[:k]
    for li, (y0, y1) in enumerate(LIGNES):
        sur_ligne = [x for l, x in visibles if l == li]
        debut = max(sur_ligne) + 2 if sur_ligne else X0 - 5
        if debut < X1 + 20:
            im.paste(fond, (debut, y0, X1 + 20, y1))
    return im


log = []
for k in range(len(mots) + 1):
    p = d / f"{k:03d}.png"
    etat(k).save(p)
    log.append({"img": str(p), "tap": None, "hold": False})
(d / "log.json").write_text(json.dumps(log, indent=1))
print(len(mots), "mots")
