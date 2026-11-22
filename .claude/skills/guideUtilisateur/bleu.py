#!/usr/bin/env python3
"""Trouve le bouton bleu « Suivant » / « Terminer » de la visite dans une capture.

uiautomator ne rend rien tant qu'une carte ouverte s'anime (la scène n'est
jamais au repos) : on repère alors le bouton par sa couleur #0080FF dans la
moitié droite de l'écran. Imprime « x y », ou rien.

Piège : sur une étape qui éclaire le bandeau bleu (la roue dentée, le « ? »),
le bandeau a la même couleur et fausse le centre ; passer par uiautomator.

    python bleu.py capture.png
"""
import sys
from PIL import Image

im = Image.open(sys.argv[1]).convert("RGB")
w, h = im.size
px = im.load()
xs, ys = [], []
for y in range(0, h, 4):
    for x in range(w // 2, w, 4):
        r, g, b = px[x, y]
        if r < 10 and 120 < g < 136 and b > 245:
            xs.append(x)
            ys.append(y)
print(f"{sum(xs) // len(xs)} {sum(ys) // len(ys)}" if xs else "")
