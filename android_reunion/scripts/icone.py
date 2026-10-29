#!/usr/bin/env python3
"""Fabrique l'icône de lancement : le lion du clavier, plus une pastille micro.

    python3 android_reunion/scripts/icone.py

Les deux applications portent le même lion ; la pastille orange (l'accent de la
charte) et son micro disent laquelle on ouvre. Sources : les icônes du clavier,
densité par densité.
"""
from pathlib import Path
from PIL import Image, ImageDraw

RACINE = Path(__file__).resolve().parents[2]
SOURCE = RACINE / "android_keyboard/app/src/main/res"
CIBLE = RACINE / "android_reunion/app/src/main/res"
ORANGE = (255, 140, 0, 255)
BLANC = (255, 255, 255, 255)
ECHELLE = 4  # dessin sur-échantillonné, puis réduit : des bords lisses


def pastille(taille):
    t = taille * ECHELLE
    img = Image.new("RGBA", (t, t), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    r = t * 0.235                      # rayon de la pastille
    cx, cy = t * 0.735, t * 0.735      # en bas à droite, sur la bande bleue
    bord = t * 0.03
    d.ellipse([cx - r - bord, cy - r - bord, cx + r + bord, cy + r + bord], fill=BLANC)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=ORANGE)
    # Le micro : une capsule, son arceau, son pied.
    lw, lh = r * 0.42, r * 0.78
    d.rounded_rectangle([cx - lw / 2, cy - r * 0.62, cx + lw / 2, cy - r * 0.62 + lh], radius=lw / 2, fill=BLANC)
    e = r * 0.09
    arc = [cx - r * 0.42, cy - r * 0.52, cx + r * 0.42, cy + r * 0.36]
    d.arc(arc, start=0, end=180, fill=BLANC, width=int(e))
    d.line([cx, cy + r * 0.36, cx, cy + r * 0.58], fill=BLANC, width=int(e))
    d.line([cx - r * 0.22, cy + r * 0.58, cx + r * 0.22, cy + r * 0.58], fill=BLANC, width=int(e))
    return img.resize((taille, taille), Image.LANCZOS)


for dossier in sorted(SOURCE.glob("mipmap-*dpi")):
    for nom in ("ic_launcher.png", "ic_launcher_round.png"):
        base = Image.open(dossier / nom).convert("RGBA")
        sortie = Image.alpha_composite(base, pastille(base.width))
        (CIBLE / dossier.name).mkdir(parents=True, exist_ok=True)
        sortie.save(CIBLE / dossier.name / nom, optimize=True)
        print(CIBLE / dossier.name / nom, base.size)
