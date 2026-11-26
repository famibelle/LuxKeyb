# -*- coding: utf-8 -*-
"""Les images de la 41.1.8 : l'application s'appelle « De Lëtzebuerger Clavier ».

Un changement de nom ne bouge pas à l'écran. Le GIF sert donc à montrer que le
nom est le **même partout** : un surligneur passe sur le bandeau de
l'application, puis sur le nom cité par l'étape d'activation, celui que
l'utilisateur doit retrouver dans la liste du téléphone. La capture système,
elle, montre ce nom dans cette liste.

Captures sources, prises sur l'émulateur (1080 × 2424, build debug de la
41.1.8, langue française) :

    nom_appli.png      l'accueil de l'appli, onglet Haut, étape 2 dépliée
    nom_claviers.png   Paramètres › Clavier à l'écran

Elles ne sont pas versionnées, comme celles de `gif_glose_gras.py` : les
images produites le sont. Le readback de l'émulateur plafonne à ~3 i/s, rien
ne s'y filme ; tout mouvement est reconstruit ici.

Le cadre de la capture système s'arrête **au-dessus** de la ligne suivante :
un clavier d'une autre session y est installé, et rien de créole ne paraît sur
le site (voir `docs/` nettoyé le 2026-09-05).
"""
import os
import subprocess

from PIL import Image, ImageDraw

S = os.path.dirname(os.path.abspath(__file__))
CADRES = os.path.join(S, 'cadres-nom')
SORTIE = os.path.join(S, '..', 'Screenshots')
FPS = 10

JAUNE = (255, 214, 10)
BORD = (214, 158, 0, 150)
DEBORD = 6

VOILE = 86

# Le marqueur s'appuie sur du texte sombre posé sur fond clair. Sur le bandeau
# bleu de l'appli, le texte est blanc : repeindre par-dessus l'assombrit et le
# rend moins lisible qu'avant d'être désigné, quelle que soit l'opacité. Là, le
# trait passe donc SOUS le mot, et se déroule pareil.
PLEIN, TRAIT = 'plein', 'trait'
EPAISSEUR = 7


def surligner(im, boite, avance, facon=PLEIN):
    """Le mot désigné de gauche à droite ; `avance` va de 0 à 1."""
    if avance <= 0:
        return im
    x0, y0, x1, y1 = boite
    x1 = x0 + (x1 - x0) * avance
    couche = Image.new('RGBA', im.size, (0, 0, 0, 0))
    dessin = ImageDraw.Draw(couche)
    if facon == PLEIN:
        dessin.rounded_rectangle(
            [x0 - DEBORD, y0 - DEBORD, x1 + DEBORD, y1 + DEBORD],
            radius=8, fill=JAUNE + (VOILE,), outline=BORD, width=2,
        )
    else:
        dessin.rounded_rectangle(
            [x0 - DEBORD, y1 + 4, x1 + DEBORD, y1 + 4 + EPAISSEUR],
            radius=EPAISSEUR // 2, fill=JAUNE + (255,),
        )
    return Image.alpha_composite(im, couche)


def monter(source, cadre, largeur, mots, nom):
    plan = []

    def tenir(im, secondes):
        plan.extend([im] * max(1, round(secondes * FPS)))

    def vue(avances):
        im = source
        for (boite, facon), avance in zip(mots, avances):
            im = surligner(im, boite, avance, facon)
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

    os.makedirs(CADRES, exist_ok=True)
    for f in os.listdir(CADRES):
        os.remove(os.path.join(CADRES, f))
    for i, im in enumerate(plan):
        im.save(os.path.join(CADRES, 'f%03d.png' % i))
    print(nom, len(plan), 'cadres', plan[0].size)

    pal = os.path.join(CADRES, 'palette.png')
    sortie = os.path.join(SORTIE, nom)
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', str(FPS),
                    '-i', os.path.join(CADRES, 'f%03d.png'),
                    '-vf', 'palettegen=max_colors=160:stats_mode=diff', pal], check=True)
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', str(FPS),
                    '-i', os.path.join(CADRES, 'f%03d.png'), '-i', pal,
                    '-lavfi', 'paletteuse=dither=none:diff_mode=rectangle',
                    '-loop', '0', sortie], check=True)
    print(' ', os.path.normpath(sortie), os.path.getsize(sortie) // 1024, 'Ko')


# Le nom dans le bandeau, puis le même nom cité par l'étape « Sélectionner le
# clavier » : c'est celui que l'utilisateur doit retrouver dans son téléphone.
appli = Image.open(os.path.join(S, 'nom_appli.png')).convert('RGBA')
monter(appli, (0, 150, 1080, 1250), 600,
       [((237, 197, 847, 246), TRAIT),
        ((360, 1066, 800, 1108), PLEIN)],
       'lux_nom_appli.gif')

# La liste des claviers du téléphone, coupée au-dessus de la ligne suivante.
claviers = Image.open(os.path.join(S, 'nom_claviers.png')).convert('RGB')
cadre = claviers.crop((0, 420, 1080, 1170))
cadre = cadre.resize((600, round(cadre.height * 600 / cadre.width)), Image.LANCZOS)
chemin = os.path.join(SORTIE, 'lux_nom_claviers.png')
cadre.save(chemin, optimize=True)
print('lux_nom_claviers.png', cadre.size, os.path.getsize(chemin) // 1024, 'Ko')
