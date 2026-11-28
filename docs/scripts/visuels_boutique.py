#!/usr/bin/env python3
"""Les photos de produits du site, découpées dans les maquettes d'impression.

La vitrine de précommande (docs/precommande*.html) et la section « jeu de
cartes » de l'accueil montrent six images. Elles avaient été découpées puis
retouchées à la main en novembre 2026, ce qui a fini par leur faire dire
autre chose que les fichiers d'impression (le nom de l'application, l'âge).
Ce script les refait à partir des maquettes, pour qu'elles ne puissent plus
diverger : on corrige le dessin, on relance, le site suit.

Sources, toutes privées, hors du dépôt (impression/ est ignoré) :

    scripts/emballage.py   -> impression/emballage/etuis_gamme.png
    scripts/presentoir.py  -> impression/emballage/presentoir/presentoir_maquette.png
    scripts/etui_ouvert.py -> impression/emballage/boite/etui_ouvert.png

Sorties, aux dimensions que les pages déclarent en dur (ne pas les changer
sans changer les attributs width/height des six `<img>` des quatre langues) :

    docs/assets/boutique/etui-depart.webp        340 × 480
    docs/assets/boutique/etui-extension.webp     340 × 480
    docs/assets/boutique/etui-niveau-a1.webp     340 × 480
    docs/assets/boutique/etui-niveau-a2.webp     340 × 480
    docs/assets/boutique/collection.webp        1000 × 414
    docs/assets/boutique/presentoir.webp         640 × 654
    docs/assets/accueil/boite-ouverte.webp      1400 × 898

Le cadrage n'est pas écrit en pixels : il est trouvé sur l'image, en cherchant
ce qui est sombre sur le fond crème, puis élargi à la proportion demandée. Une
maquette rendue plus grande ou recadrée autrement continue donc de marcher.

À lancer depuis la racine du dépôt : python3 docs/scripts/visuels_boutique.py
"""
import os
import sys

from PIL import Image

RACINE = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '../..'))
EMB = os.path.join(RACINE, 'impression/emballage')
BOUTIQUE = os.path.join(RACINE, 'docs/assets/boutique')
ACCUEIL = os.path.join(RACINE, 'docs/assets/accueil')

# Le fond des maquettes est un dégradé crème ; les boîtes sont bleu nuit. Le
# seuil passe entre les deux sans attraper l'ombre portée, plus claire.
SEUIL = 170
QUALITE = 88


def masque(im, seuil=SEUIL):
    return im.convert('L').point(lambda v: 255 if v < seuil else 0)


def colonnes(m, mini=20):
    """Les plages de x où il y a de l'encre : une par boîte, de gauche à droite."""
    largeur, hauteur = m.size
    plein = [any(m.getpixel((x, y)) for y in range(0, hauteur, 4)) for x in range(largeur)]
    plages, debut = [], None
    for x, v in enumerate(plein + [False]):
        if v and debut is None:
            debut = x
        elif not v and debut is not None:
            if x - debut >= mini:
                plages.append((debut, x))
            debut = None
    return plages


def boite_encre(m, x0=0, x1=None):
    """Le rectangle qui contient l'encre, dans la tranche [x0, x1[."""
    x1 = x1 if x1 is not None else m.size[0]
    bb = m.crop((x0, 0, x1, m.size[1])).getbbox()
    if bb is None:
        raise SystemExit('rien trouvé sur la maquette : le seuil ou la source a changé')
    return (bb[0] + x0, bb[1], bb[2] + x0, bb[3])


def cadrer(im, bb, cible, marge=.05, bas=.0):
    """Élargit [bb] à la proportion de [cible], sans sortir de l'image.

    [marge] est la respiration autour du sujet, en parts de sa plus grande
    dimension ; [bas] en ajoute sous lui, pour garder l'ombre portée.
    """
    x0, y0, x1, y1 = bb
    air = marge * max(x1 - x0, y1 - y0)
    x0, y0, x1, y1 = x0 - air, y0 - air, x1 + air, y1 + air + bas * (y1 - y0)
    proportion = cible[0] / cible[1]
    l, h = x1 - x0, y1 - y0
    if l / h < proportion:
        l = h * proportion
    else:
        h = l / proportion
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    # Le cadre ne doit pas sortir de l'image : on le ramène, et s'il ne tient
    # toujours pas, on le rétrécit.
    l, h = min(l, im.width), min(h, im.height)
    l, h = min(l, h * proportion), min(h, l / proportion)
    x0 = min(max(cx - l / 2, 0), im.width - l)
    y0 = min(max(cy - h / 2, 0), im.height - h)
    decoupe = im.crop((round(x0), round(y0), round(x0 + l), round(y0 + h)))
    return decoupe.resize(cible, Image.LANCZOS)


def ecrire(im, chemin):
    os.makedirs(os.path.dirname(chemin), exist_ok=True)
    im.convert('RGB').save(chemin, 'WEBP', quality=QUALITE, method=6)
    print(f'{os.path.relpath(chemin, RACINE)}  {im.width} × {im.height}  '
          f'{os.path.getsize(chemin) // 1024} Ko')


def source(chemin):
    if not os.path.exists(chemin):
        raise SystemExit(f'manque {os.path.relpath(chemin, RACINE)} : relancer le script qui le produit')
    return Image.open(chemin).convert('RGB')


VITRINES = [('boite', 'etui-depart'), ('extension', 'etui-extension'),
            ('niveau_a1', 'etui-niveau-a1'), ('niveau_a2', 'etui-niveau-a2')]


def etuis():
    """Les quatre fiches produit, chacune depuis son rendu seul.

    Pas depuis la gamme : ses boîtes sont à 660 px de pas pour 600 de large,
    un cadrage aéré y ferait entrer le voisin. emballage.vitrine rend donc
    chaque étui seul, déjà dans la proportion des vignettes.
    """
    for dossier, nom in VITRINES:
        im = source(os.path.join(EMB, dossier, 'etui_vitrine.png'))
        ecrire(cadrer(im, boite_encre(masque(im)), (340, 480), marge=.035, bas=.05),
               os.path.join(BOUTIQUE, f'{nom}.webp'))


def collection():
    im = source(os.path.join(EMB, 'etuis_gamme.png'))
    m = masque(im)
    plages = colonnes(m)
    if len(plages) != 4:
        raise SystemExit(f'{len(plages)} boîtes trouvées au lieu de 4 : revoir le seuil')
    ecrire(cadrer(im, boite_encre(m), (1000, 414), marge=.012, bas=.10),
           os.path.join(BOUTIQUE, 'collection.webp'))


def presentoir():
    im = source(os.path.join(EMB, 'presentoir/presentoir_maquette.png'))
    ecrire(cadrer(im, boite_encre(masque(im)), (640, 654), marge=.03),
           os.path.join(BOUTIQUE, 'presentoir.webp'))


def boite_ouverte():
    im = source(os.path.join(EMB, 'boite/etui_ouvert.png'))
    # Les cartes posées sont claires : le seuil des boîtes les perdrait. Ici
    # on cherche simplement ce qui n'est pas le fond.
    m = im.convert('L').point(lambda v: 255 if v < 215 else 0)
    ecrire(cadrer(im, boite_encre(m), (1400, 898), marge=.02),
           os.path.join(ACCUEIL, 'boite-ouverte.webp'))


if __name__ == '__main__':
    quoi = sys.argv[1:] or ['etuis', 'collection', 'presentoir', 'ouverte']
    for nom, faire in (('etuis', etuis), ('collection', collection),
                       ('presentoir', presentoir), ('ouverte', boite_ouverte)):
        if nom in quoi:
            faire()
