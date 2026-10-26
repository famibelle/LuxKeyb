#!/usr/bin/env python3
"""Planche de contrôle de lisibilité d'une carte à jouer.

Les tests que fait un designer de cartes avant de montrer une maquette,
réunis sur une seule image :

1. la carte à sa taille réelle (63 × 88 mm vus à 60 cm de distance) ;
2. la **vignette** à 25 % : le test du pouce, la carte vue de l'autre côté
   de la table ; le sujet et la Force doivent encore se lire ;
3. le **noir et blanc** : si deux informations ne se distinguent que par
   leur couleur, elles fusionnent ici ;
4. la **deutéranopie** et la **protanopie** (simulation de Machado, 2009) :
   environ un garçon sur douze voit les couleurs ainsi ;
5. l'**éventail** : la carte tenue en main, recouverte par la suivante, dont
   seul le coin haut gauche dépasse ; ce coin doit suffire pour jouer.

    python3 controle_lisibilite.py carte.png [autre.png …] -o planche.png

Le PNG d'entrée peut avoir un fond perdu : --fond-perdu 3 le retire avant
les tests (en mm, pour une carte de 63 × 88 mm).
"""
import argparse

import numpy as np
from PIL import Image, ImageDraw, ImageFont

LARGEUR_MM, HAUTEUR_MM = 63, 88

# Machado, Oliveira, Fernandes (2009), sévérité 1, en RVB linéaire.
DEUTERANOPIE = np.array([[0.367322, 0.860646, -0.227968],
                         [0.280085, 0.672501, 0.047413],
                         [-0.011820, 0.042940, 0.968881]])
PROTANOPIE = np.array([[0.152286, 1.052583, -0.204868],
                       [0.114503, 0.786281, 0.099216],
                       [-0.003882, -0.048116, 1.051998]])


def lineaire(a):
    return np.where(a <= 0.04045, a / 12.92, ((a + 0.055) / 1.055) ** 2.4)


def gamma(a):
    a = np.clip(a, 0, 1)
    return np.where(a <= 0.0031308, a * 12.92, 1.055 * a ** (1 / 2.4) - 0.055)


def daltonisme(im, matrice):
    a = lineaire(np.asarray(im.convert('RGB'), np.float64) / 255)
    b = gamma(a @ matrice.T)
    return Image.fromarray((b * 255).round().astype(np.uint8), 'RGB')


def eventail(im, decalage=0.22):
    """Trois exemplaires de la carte en éventail : seul le coin de gauche se voit
    sur les deux premières."""
    l, h = im.size
    pas = int(l * decalage)
    fond = Image.new('RGB', (l + 2 * pas, h), 'white')
    for i in range(3):
        fond.paste(im, (i * pas, 0))
    return fond


def police(taille):
    for nom in ('DejaVuSans.ttf', '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'):
        try:
            return ImageFont.truetype(nom, taille)
        except OSError:
            pass
    return ImageFont.load_default()


def planche(chemin, fond_perdu_mm, hauteur):
    im = Image.open(chemin).convert('RGB')
    if fond_perdu_mm:
        l, h = im.size
        dx = round(l * fond_perdu_mm / (LARGEUR_MM + 2 * fond_perdu_mm))
        dy = round(h * fond_perdu_mm / (HAUTEUR_MM + 2 * fond_perdu_mm))
        im = im.crop((dx, dy, l - dx, h - dy))
    carte = im.resize((round(hauteur * im.width / im.height), hauteur), Image.LANCZOS)
    vignette = im.resize((carte.width // 4, hauteur // 4), Image.LANCZOS)
    tests = [
        ('Carte', carte),
        ('Vignette 25 %', vignette),
        ('Noir et blanc', carte.convert('L').convert('RGB')),
        ('Deutéranopie', daltonisme(carte, DEUTERANOPIE)),
        ('Protanopie', daltonisme(carte, PROTANOPIE)),
        ('Éventail', eventail(carte)),
    ]
    marge, titre = 24, 40
    f = police(22)
    # Une colonne est aussi large que son image ou que son titre.
    colonnes = [max(t.width, round(f.getlength(nom))) for nom, t in tests]
    largeur = sum(colonnes) + marge * (len(tests) + 1)
    sortie = Image.new('RGB', (largeur, hauteur + titre + 2 * marge), '#f3f1ea')
    d = ImageDraw.Draw(sortie)
    x = marge
    for (nom, t), colonne in zip(tests, colonnes):
        d.text((x, marge), nom, fill='#333333', font=f)
        sortie.paste(t, (x, marge + titre))
        x += colonne + marge
    return sortie


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('cartes', nargs='+')
    p.add_argument('-o', '--sortie', default='controle_lisibilite.png')
    p.add_argument('--fond-perdu', type=float, default=0, help='fond perdu à retirer, en mm')
    p.add_argument('--hauteur', type=int, default=700, help='hauteur de la carte sur la planche, en px')
    a = p.parse_args()
    lignes = [planche(c, a.fond_perdu, a.hauteur) for c in a.cartes]
    sortie = Image.new('RGB', (max(l.width for l in lignes), sum(l.height for l in lignes)), '#f3f1ea')
    y = 0
    for l in lignes:
        sortie.paste(l, (0, y))
        y += l.height
    sortie.save(a.sortie)
    print(a.sortie)


if __name__ == '__main__':
    main()
