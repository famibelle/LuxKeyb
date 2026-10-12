#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Pose un filigrane « LuxKeyb » en diagonale sur chaque carte d'une capture.

Les captures du carnet (docs/Screenshots/lux_carnet_*.png) montrent des cartes
posées sur un fond clair : le gris du carnet (#F5F5F5) entre les cartes
ouvertes d'un montage, le crème d'une page d'album (#FFFBF2) entre les
vignettes. Le script trouve les cartes par ce fond, sans qu'on lui donne la
mise en page, et écrit le filigrane en travers de chacune.

Le filigrane est incrusté dans l'image, pas posé par la page : un calque CSS
disparaît dès qu'on enregistre l'image.

    python docs/scripts/filigrane_cartes.py docs/Screenshots/lux_carnet_metaux.png

Modifie les fichiers en place. À passer une seule fois, sur une capture neuve :
une seconde passe doublerait le filigrane. L'original reste dans l'historique
git.
"""

import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

TEXTE = "LuxKeyb"
FONDS = ((245, 245, 245), (255, 251, 242))
POLICE = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
# Un pixel s'écarte du fond au-delà de cette distance (somme des trois canaux).
ECART = 24
# Un intervalle de fond plus court que ceci ne sépare pas deux cartes.
SEPARATION = 4
# Une carte plus petite que ceci n'en est pas une (bavure, ombre).
COTE_MIN = 60


def segments(occupe, longueur):
    """Les intervalles [début, fin) où `occupe` est vrai, fusionnés par-dessus
    les trous plus courts que SEPARATION."""
    brut, debut = [], None
    for i in range(longueur):
        if occupe[i] and debut is None:
            debut = i
        elif not occupe[i] and debut is not None:
            brut.append([debut, i])
            debut = None
    if debut is not None:
        brut.append([debut, longueur])
    fusion = []
    for s in brut:
        if fusion and s[0] - fusion[-1][1] < SEPARATION:
            fusion[-1][1] = s[1]
        else:
            fusion.append(s)
    return [s for s in fusion if s[1] - s[0] >= COTE_MIN]


def cartes(im):
    """Les rectangles des cartes, par découpe récursive : les bandes de
    rangées occupées, puis les colonnes dans chacune, puis les rangées dans
    chaque colonne, jusqu'à ce que plus rien ne se sépare. Une seule passe ne
    suffit pas : une ombre qui court sur toute la hauteur soude les rangées
    tant qu'on n'a pas d'abord isolé les colonnes."""
    rgb = im.convert("RGB")
    px = rgb.load()

    def hors_fond(x, y):
        r, g, b = px[x, y]
        return all(abs(r - f[0]) + abs(g - f[1]) + abs(b - f[2]) > ECART for f in FONDS)

    def couper(boite, en_rangees):
        x0, y0, x1, y1 = boite
        if en_rangees:
            parts = segments([any(hors_fond(x, y) for x in range(x0, x1, 2)) for y in range(y0, y1)], y1 - y0)
            enfants = [(x0, y0 + a, x1, y0 + b) for a, b in parts]
        else:
            parts = segments([any(hors_fond(x, y) for y in range(y0, y1, 2)) for x in range(x0, x1)], x1 - x0)
            enfants = [(x0 + a, y0, x0 + b, y1) for a, b in parts]
        return enfants

    def decouper(boite, en_rangees, immobile=0):
        enfants = couper(boite, en_rangees)
        if len(enfants) == 1 and enfants[0] == boite:
            # Rien de coupé dans ce sens : un second refus dans l'autre et
            # la boîte est une carte.
            return [boite] if immobile else decouper(boite, not en_rangees, 1)
        return [c for e in enfants for c in decouper(e, not en_rangees)]

    l, h = rgb.size
    return decouper((0, 0, l, h), True)


def filigrane(im, boite):
    """Le mot en travers de la carte, le long de sa diagonale montante : blanc
    translucide cerné de sombre, lisible sur le métal clair comme sur un ciel
    de nuit, sans masquer la carte."""
    x0, y0, x1, y1 = boite
    l, h = x1 - x0, y1 - y0
    angle = math.degrees(math.atan2(h, l))
    diagonale = math.hypot(l, h)
    # Le mot occupe un peu plus de la moitié de la diagonale.
    taille = max(12, int(diagonale * 0.55 / (len(TEXTE) * 0.62)))
    police = ImageFont.truetype(POLICE, taille)
    trait = max(1, taille // 18)

    gauche, haut, droite, bas = police.getbbox(TEXTE, stroke_width=trait)
    calque = Image.new("RGBA", (droite - gauche + 4, bas - haut + 4), (0, 0, 0, 0))
    ImageDraw.Draw(calque).text(
        (2 - gauche, 2 - haut), TEXTE, font=police,
        fill=(255, 255, 255, 92), stroke_width=trait, stroke_fill=(0, 0, 0, 56),
    )
    calque = calque.rotate(angle, resample=Image.BICUBIC, expand=True)
    cx, cy = x0 + l // 2, y0 + h // 2
    im.alpha_composite(calque, (cx - calque.width // 2, cy - calque.height // 2))


def traiter(chemin):
    source = Image.open(chemin)
    mode = source.mode
    im = source.convert("RGBA")
    boites = cartes(im)
    for b in boites:
        filigrane(im, b)
    (im if mode == "RGBA" else im.convert("RGB")).save(chemin, optimize=True)
    print(f"{chemin} : {len(boites)} cartes")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    for nom in sys.argv[1:]:
        traiter(Path(nom))
