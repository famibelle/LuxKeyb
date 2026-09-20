# -*- coding: utf-8 -*-
"""Synthétise lux_revision.gif : la boîte de Leitner, une carte retournée, et
la carte qui repart trois jours plus loin.

Le readback d'écran de l'émulateur plafonne à ~3 i/s : aucune animation ne se
filme. Les états sont donc capturés un par un et le mouvement est reconstruit
ici — le retournement par une compression horizontale de la carte, l'appui par
un disque translucide qui pulse là où le doigt se pose.
"""
import os, subprocess
from PIL import Image, ImageDraw

S = os.path.dirname(os.path.abspath(__file__))
CADRES = os.path.join(S, 'cadres')
os.makedirs(CADRES, exist_ok=True)
for f in os.listdir(CADRES):
    os.remove(os.path.join(CADRES, f))

COUPE = (0, 96, 1080, 2280)          # sans la barre d'état ni la barre système
LARGEUR = 420
FPS = 10

def charger(nom):
    im = Image.open(os.path.join(S, nom + '.png')).convert('RGB').crop(COUPE)
    h = round(im.height * LARGEUR / im.width)
    return im.resize((LARGEUR, h), Image.LANCZOS)

def appui(im, x, y, r=26, t=1.0):
    """Le disque bleu de compose.py, à l'échelle de la vignette."""
    im = im.copy()
    couche = Image.new('RGBA', im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(couche)
    rr = r * (0.75 + 0.35 * t)
    d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=(79, 195, 247, 110),
              outline=(1, 87, 155, 210), width=3)
    im.paste(Image.alpha_composite(im.convert('RGBA'), couche).convert('RGB'))
    return im

# La carte occupe, dans la vue capturée, ce rectangle (repère de la vignette).
CARTE = (43, 272, 377, 764)

def pincer(fond, carte, k):
    """La carte vue de biais : largeur comprimée vers son axe."""
    im = fond.copy()
    x0, y0, x1, y1 = CARTE
    bande = carte.crop(CARTE)
    l = max(2, int(bande.width * k))
    bande = bande.resize((l, bande.height), Image.LANCZOS)
    cx = (x0 + x1) // 2
    # sous la carte, le fond de l'écran : un aplat pris juste à sa gauche
    im.paste(Image.new('RGB', (x1 - x0, y1 - y0), fond.getpixel((x0 - 12, y0 + 120))),
             (x0, y0))
    im.paste(bande, (cx - l // 2, y0))
    return im

boite = charger('rev1')
question = charger('rev2')
reponse = charger('rev3')
suivante = charger('q2')
apres = charger('boite_apres')

# Les deux boutons, dans le repère de la vignette : « Retourner la carte »
# occupe toute la largeur, « Je savais » la moitié droite.
Y_BOUTON = round((2166 - COUPE[1]) * LARGEUR / 1080)
X_RETOURNER, X_JE_SAVAIS = LARGEUR // 2, round(795 * LARGEUR / 1080)

plan = []
def tenir(im, secondes):
    plan.extend([im] * max(1, round(secondes * FPS)))

tenir(boite, 1.7)
tenir(appui(boite, LARGEUR // 2, round((2102 - COUPE[1]) * LARGEUR / 1080), t=0.4), 0.2)
tenir(appui(boite, LARGEUR // 2, round((2102 - COUPE[1]) * LARGEUR / 1080), t=1.0), 0.3)
tenir(question, 1.8)
tenir(appui(question, X_RETOURNER, Y_BOUTON, t=0.4), 0.2)
tenir(appui(question, X_RETOURNER, Y_BOUTON, t=1.0), 0.3)
for k in (0.72, 0.42, 0.16):
    tenir(pincer(question, question, k), 0.06)
for k in (0.16, 0.42, 0.72, 1.0):
    tenir(pincer(reponse, reponse, k), 0.06)
tenir(reponse, 2.6)
tenir(appui(reponse, X_JE_SAVAIS, Y_BOUTON, t=0.4), 0.2)
tenir(appui(reponse, X_JE_SAVAIS, Y_BOUTON, t=1.0), 0.3)
tenir(suivante, 1.5)
tenir(apres, 2.8)

for i, im in enumerate(plan):
    im.save(os.path.join(CADRES, 'f%03d.png' % i))
print(len(plan), 'cadres', plan[0].size)

pal = os.path.join(S, 'palette.png')
sortie = os.path.join(S, 'lux_revision.gif')
subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', str(FPS),
                '-i', os.path.join(CADRES, 'f%03d.png'),
                '-vf', 'palettegen=max_colors=192:stats_mode=diff', pal], check=True)
subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', str(FPS),
                '-i', os.path.join(CADRES, 'f%03d.png'), '-i', pal,
                '-lavfi', 'paletteuse=dither=none:diff_mode=rectangle',
                '-loop', '0', sortie], check=True)
print(sortie, os.path.getsize(sortie) // 1024, 'Ko')
