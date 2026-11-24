# -*- coding: utf-8 -*-
"""Synthétise les deux GIF de la 41.1.7 : le mot et sa traduction en gras.

La nouveauté ne bouge pas à l'écran — un mot est en gras, voilà tout. Le GIF
sert donc à **montrer le lien** que le gras établit : un surligneur passe sur
le mot luxembourgeois dans sa phrase, puis sur le mot français dans sa
traduction, et les deux restent éclairés ensemble.

Captures sources, prises sur l'émulateur (1080 × 2424, build de la 41.1.7) :

    glose_carte.png   le carnet, carte « Beie » ouverte
    glose_fiche.png   le Wierderbuch, fiche « Haus » ouverte

Elles ne sont pas versionnées, comme celles de `gif_revision.py` : le GIF
produit l'est. Le readback de l'émulateur plafonne à ~3 i/s, rien ne s'y
filme ; tout mouvement est reconstruit ici.

La carte reçoit son filigrane « LuxKeyb » avant le montage, par la fonction de
`filigrane_cartes.py` : la détection automatique de ce script cherche les
cartes sur le fond clair du carnet et ne les trouve pas sous le voile sombre
d'une carte ouverte, donc la boîte est donnée à la main.
"""
import os
import subprocess
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from filigrane_cartes import filigrane  # noqa: E402

S = os.path.dirname(os.path.abspath(__file__))
CADRES = os.path.join(S, 'cadres-glose')
FPS = 10

# Le surligneur : un jaune de marqueur, posé par-dessus le texte, qui le laisse
# lisible. Le trait déborde un peu du mot, comme une main qui surligne.
JAUNE = (255, 214, 10, 86)
BORD = (214, 158, 0, 120)
DEBORD = 6


def surligner(im, boite, avance):
    """Le mot surligné de gauche à droite ; `avance` va de 0 à 1."""
    if avance <= 0:
        return im
    x0, y0, x1, y1 = boite
    x1 = x0 + (x1 - x0) * avance
    couche = Image.new('RGBA', im.size, (0, 0, 0, 0))
    ImageDraw.Draw(couche).rounded_rectangle(
        [x0 - DEBORD, y0 - DEBORD, x1 + DEBORD, y1 + DEBORD],
        radius=8, fill=JAUNE, outline=BORD, width=2,
    )
    return Image.alpha_composite(im, couche)


def monter(source, cadre, largeur, mots, nom):
    """Un GIF : le cadre tenu, puis les deux mots surlignés l'un après l'autre."""
    plan = []

    def tenir(im, secondes):
        plan.extend([im] * max(1, round(secondes * FPS)))

    def vue(avances):
        im = source
        for boite, avance in zip(mots, avances):
            im = surligner(im, boite, avance)
        im = im.convert('RGB').crop(cadre)
        hauteur = round(im.height * largeur / im.width)
        return im.resize((largeur, hauteur), Image.LANCZOS)

    tenir(vue((0, 0)), 1.3)
    for a in (0.35, 0.7, 1.0):
        tenir(vue((a, 0)), 0.09)
    tenir(vue((1, 0)), 0.8)
    for a in (0.35, 0.7, 1.0):
        tenir(vue((1, a)), 0.09)
    tenir(vue((1, 1)), 2.4)

    for f in os.listdir(CADRES):
        os.remove(os.path.join(CADRES, f))
    for i, im in enumerate(plan):
        im.save(os.path.join(CADRES, 'f%03d.png' % i))
    print(nom, len(plan), 'cadres', plan[0].size)

    pal = os.path.join(CADRES, 'palette.png')
    sortie = os.path.join(S, '..', 'Screenshots', nom)
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', str(FPS),
                    '-i', os.path.join(CADRES, 'f%03d.png'),
                    '-vf', 'palettegen=max_colors=160:stats_mode=diff', pal], check=True)
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', str(FPS),
                    '-i', os.path.join(CADRES, 'f%03d.png'), '-i', pal,
                    '-lavfi', 'paletteuse=dither=none:diff_mode=rectangle',
                    '-loop', '0', sortie], check=True)
    print(' ', os.path.normpath(sortie), os.path.getsize(sortie) // 1024, 'Ko')


os.makedirs(CADRES, exist_ok=True)

# La carte « Beie » : « Bei » dans la phrase, « abeille » dans la traduction.
carte = Image.open(os.path.join(S, 'glose_carte.png')).convert('RGBA')
filigrane(carte, (61, 439, 1006, 1818))
monter(carte, (53, 400, 1014, 1840), 600,
       [(512, 1458, 583, 1498), (468, 1508, 562, 1548)],
       'lux_glose_gras_carte.gif')

# La fiche « Haus » : « Haus » dans la phrase, « maison » dans la traduction.
fiche = Image.open(os.path.join(S, 'glose_fiche.png')).convert('RGBA')
monter(fiche, (0, 1150, 1080, 1935), 600,
       [(69, 1671, 161, 1708), (865, 1729, 982, 1763)],
       'lux_glose_gras_fiche.gif')
