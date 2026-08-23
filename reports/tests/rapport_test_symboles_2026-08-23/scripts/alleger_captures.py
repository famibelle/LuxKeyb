#!/usr/bin/env python3
"""Génère les aperçus affichés dans le rapport à partir des captures brutes.

Le README embarque 108 images : à pleine fidélité la page pèse 16 Mo et se
charge d'un bloc sur GitHub. Les captures étant des interfaces à aplats, une
palette de 256 couleurs divise le poids par ~2,7 sans différence visible
(écart moyen mesuré : 0,07 niveau par canal). La **résolution est conservée
telle quelle** : seul le nombre de couleurs change.

Les originaux restent dans `captures/`, qui fait foi ; `apercus/` n'est qu'une
vue d'affichage, régénérable à tout moment par ce script.

Usage : python3 alleger_captures.py
"""
import os
import sys

from PIL import Image

SP = os.path.dirname(os.path.abspath(__file__))
RAPPORT = os.path.dirname(SP)
SRC = os.path.join(RAPPORT, "captures")
DST = os.path.join(RAPPORT, "apercus")


def main():
    if not os.path.isdir(SRC):
        sys.exit(f"répertoire source introuvable : {SRC}")
    os.makedirs(DST, exist_ok=True)
    avant = apres = 0
    fichiers = sorted(f for f in os.listdir(SRC) if f.endswith(".png"))
    for nom in fichiers:
        s, d = os.path.join(SRC, nom), os.path.join(DST, nom)
        im = Image.open(s).convert("RGB")
        im.quantize(colors=256, method=Image.MEDIANCUT).save(d, optimize=True)
        avant += os.path.getsize(s)
        apres += os.path.getsize(d)
    print(f"{len(fichiers)} images : {avant / 1e6:.1f} Mo -> {apres / 1e6:.1f} Mo "
          f"(x{avant / apres:.1f})")


if __name__ == "__main__":
    main()
