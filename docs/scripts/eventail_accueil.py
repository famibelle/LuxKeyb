#!/usr/bin/env python3
"""Compose l'éventail de cartes de l'accueil (docs/index.html).

    python docs/scripts/eventail_accueil.py

Trois cartes du Wuertduell, du bronze à l'or : Hond, Kuch, Kaz
(docs/assets/cartes/carte_*_wuertduell.webp, coins déjà transparents).
Même méthode que .claude/skills/supports-communication/eventail.py, mais
les cartes restent entières : assets/cartes-eventail.png, recadré en bas
pour le triptyque, coupait le pied des cartes sur la page.

Sortie : docs/assets/accueil/cartes-eventail.png, PNG transparent, recadré
sur l'alpha avec une marge pour garder l'ombre.
"""
import pathlib
from PIL import Image, ImageFilter

DOCS = pathlib.Path(__file__).resolve().parents[1]
D = DOCS / "assets" / "cartes"
SORTIE = DOCS / "assets" / "accueil" / "cartes-eventail.png"

K = 0.62                       # échelle des cartes (600 × 838 à l'origine)
# nom, angle (degrés, positif = penché à droite), centre x, centre y
CARTES = [("hond", -9, 250, 330), ("kuch", 0, 500, 300), ("kaz", 9, 750, 330)]
W, H = 1000, 680

fond = Image.new("RGBA", (W, H), (0, 0, 0, 0))
for nom, angle, cx, cy in CARTES:
    im = Image.open(D / f"carte_{nom}_wuertduell.webp").convert("RGBA")
    im = im.resize((round(im.width * K), round(im.height * K)), Image.LANCZOS)
    r = im.rotate(-angle, expand=True, resample=Image.BICUBIC)
    x, y = cx - r.width // 2, cy - r.height // 2
    ombre = Image.new("RGBA", r.size, (0, 0, 0, 0))
    ombre.putalpha(r.getchannel("A").point(lambda v: int(v * 0.35)))
    ombre = ombre.filter(ImageFilter.GaussianBlur(10))
    fond.alpha_composite(ombre, (x + 5, y + 10))
    fond.alpha_composite(r, (x, y))

b = fond.getchannel("A").point(lambda v: 255 if v > 4 else 0).getbbox()
m = 6
fond = fond.crop((max(0, b[0] - m), max(0, b[1] - m), min(W, b[2] + m), min(H, b[3] + m)))
SORTIE.parent.mkdir(parents=True, exist_ok=True)
fond.save(SORTIE, optimize=True)
print(SORTIE.relative_to(DOCS), fond.size)
