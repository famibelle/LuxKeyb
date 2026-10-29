#!/usr/bin/env python3
"""Les rectos de la boîte de départ, dessinés par l'application elle-même.

L'appli exporte une carte pour l'impression (appui long sur « Partager » dans
le lecteur de cartes, puis « Cette carte », carnet/ImpressionCarte.kt) : 600 dpi,
69 × 94 mm fond perdu compris, 1630 × 2220 px. Ce script fait les deux bouts de
la chaîne, autour de scripts/exporter_rectos.sh qui pilote l'émulateur :

1. `carnet` écrit un carnet (shared_prefs/carnet_cartes.xml) qui contient les
   52 cartes de l'ensemble B1 (assets/cartes_imprimees.json), numérotées pour
   que le tri « Récentes » les range dans l'ordre de la boîte ;
2. `assembler` range les PNG exportés : fichiers numérotés, liste de contrôle,
   PDF d'une carte par page en 600 dpi pour l'imprimeur (impression/boite/,
   privé) et en 300 dpi pour cartes-imprimables/boite/ (public).

À lancer depuis la racine du dépôt :
python3 scripts/rectos_boite.py carnet > carnet_cartes.xml
scripts/exporter_rectos.sh 52
python3 scripts/rectos_boite.py assembler [dossier des PNG exportés]

Le PDF demande img2pdf (pip install img2pdf), qui embarque les PNG sans les
recompresser.
"""
import csv
import io
import json
import os
import shutil
import sys
import time
from xml.sax.saxutils import escape

from PIL import Image, ImageDraw, ImageFont

RACINE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
ASSETS = os.path.join(RACINE, 'android_keyboard/app/src/main/assets')
SELECTION = os.path.join(RACINE, 'Dictionnaires/cartes/selection.csv')
PRIVE = os.path.join(RACINE, 'impression/boite')
PUBLIC = os.path.join(RACINE, 'cartes-imprimables/boite')
VERSO = os.path.join(RACINE, 'cartes-imprimables/verso')
TAILLE = (1630, 2220)  # 69 × 94 mm à 600 dpi


def boite():
    """Les cartes de B1 : (mot, nombre ou None, paquet), dans l'ordre de la boîte."""
    ens = json.load(open(os.path.join(ASSETS, 'cartes_imprimees.json')))['ensembles']['B1']
    paquets = [nom for nom, n in ens['paquets'] for _ in range(n)]
    cartes = []
    for cle, paquet in zip(ens['cartes'], paquets):
        mot, _, v = cle.partition('#')
        cartes.append((mot, int(v) if v else None, paquet))
    return cartes


def nom_fichier(mot):
    """Le nom que l'appli donne à l'export (impression_<nom>_69x94mm_600dpi.png)."""
    return ''.join(ch for ch in mot if ch.isalnum())


def carnet():
    """Une carte par mot, gagnée à Wuertsich (Zuelwuert pour les nombres)."""
    d = int(time.time() * 1000)
    cartes = boite()
    entrees = []
    for i, (mot, nombre, _) in enumerate(cartes):
        c = {'m': mot, 'd': d, 'n': 1, 'g': 'zw' if nombre else 'ws', 'k': len(cartes) - i}
        if nombre:
            c['v'] = nombre
        entrees.append(c)
    blob = escape(json.dumps(entrees, ensure_ascii=False, separators=(',', ':')), {'"': '&quot;'})
    print("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>")
    print('<map>')
    print(f'    <string name="cartes">{blob}</string>')
    print('</map>')


def rarete(mot, nombre, rang):
    """Rarete.pourRang et Rarete.pourNombre (carnet/CarnetModels.kt)."""
    if nombre is not None:
        return 'Commun'
    r = rang.get(mot)
    if r is None or r >= 9000:
        return 'Très rare'
    if r >= 6500:
        return 'Rare'
    if r >= 3000:
        return 'Peu commun'
    return 'Commun'


def cout(mot, nombre):
    """ImpressionCarte.cout : la longueur du mot, rien pour un nombre."""
    if nombre is not None:
        return 0
    n = len(mot)
    return 1 if n <= 4 else 2 if n <= 7 else 3 if n <= 10 else 4


def pdf(images, sortie):
    """Une carte par page, 69 × 94 mm, PNG embarqués tels quels."""
    import img2pdf
    mise = img2pdf.get_layout_fun((img2pdf.mm_to_pt(69), img2pdf.mm_to_pt(94)))
    with open(sortie, 'wb') as f:
        f.write(img2pdf.convert(images, layout_fun=mise))


def png(im, dpi):
    b = io.BytesIO()
    im.save(b, 'PNG', dpi=(dpi, dpi))
    return b.getvalue()


def apercu(images, sortie):
    """Les deux paquets d'un coup d'œil, 13 cartes par ligne, sans fond perdu."""
    L, H, cols, ecart = 230, 313, 13, 12
    marge = round(3 / 69 * TAILLE[0])
    rangs = -(-len(images) // cols)
    ap = Image.new('RGB', (cols * L + (cols + 1) * ecart, rangs * H + (rangs + 1) * ecart + 40), '#2b2440')
    try:
        police = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 26)
    except OSError:
        police = ImageFont.load_default()
    ImageDraw.Draw(ap).text((ecart, 8), 'Wuertduell : la boîte de 52 cartes (Déieren 1 à 26, Iessen 27 à 52)',
                            fill='white', font=police)
    for k, im in enumerate(images):
        im = im.crop((marge, marge, im.width - marge, im.height - marge)).resize((L, H), Image.LANCZOS)
        ap.paste(im, (ecart + (k % cols) * (L + ecart), 52 + (k // cols) * (H + ecart)))
    ap.save(sortie)


def assembler(export):
    selection = {r['mot']: r for r in csv.DictReader(open(SELECTION))}
    rang = {}
    for i, (w, _) in enumerate(json.load(open(os.path.join(ASSETS, 'luxemburgish_dict.json')))):
        rang.setdefault(w, i + 1)

    cartes = boite()
    manquants = [mot for mot, _, _ in cartes
                 if not os.path.exists(os.path.join(export, nom_fichier(mot) + '.png'))]
    if manquants:
        sys.exit(f'Exports manquants dans {export} : {", ".join(manquants)}')

    recto = os.path.join(PRIVE, 'recto')
    os.makedirs(recto, exist_ok=True)
    os.makedirs(PUBLIC, exist_ok=True)
    images, fichiers, pages_300, lignes = [], [], [], []
    for i, (mot, nombre, paquet) in enumerate(cartes):
        im = Image.open(os.path.join(export, nom_fichier(mot) + '.png'))
        assert im.size == TAILLE, (mot, im.size)
        # L'appli écrit de l'ARGB entièrement opaque : en RVB, le PDF n'a pas de masque de transparence.
        assert im.mode != 'RGBA' or im.getchannel('A').getextrema() == (255, 255), mot
        im = im.convert('RGB')
        dst = os.path.join(recto, f'{i + 1:03d}_{nom_fichier(mot)}.png')
        im.save(dst, dpi=(600, 600))
        fichiers.append(dst)
        images.append(im)
        pages_300.append(png(im.resize((TAILLE[0] // 2, TAILLE[1] // 2), Image.LANCZOS), 300))
        s = selection.get(mot, {})
        lignes.append({
            'numero': f'{i + 1:03d}', 'mot': mot, 'paquet': paquet,
            'nature': s.get('nature') or ('nombre' if nombre else ''), 'force': len(mot),
            'cout': cout(mot, nombre), 'rarete': rarete(mot, nombre, rang), 'sens': s.get('sens', ''),
        })

    for dossier in (PRIVE, PUBLIC):
        with open(os.path.join(dossier, 'liste.csv'), 'w', newline='') as f:
            w = csv.DictWriter(f, fieldnames=list(lignes[0]))
            w.writeheader()
            w.writerows(lignes)
    pdf(fichiers, os.path.join(PRIVE, 'rectos_52.pdf'))
    pdf(pages_300, os.path.join(PUBLIC, 'rectos_52.pdf'))
    shutil.copy(os.path.join(VERSO, 'verso.pdf'), os.path.join(PRIVE, 'dos.pdf'))
    shutil.copy(os.path.join(VERSO, 'verso_300dpi.png'), os.path.join(PRIVE, 'dos_300dpi.png'))
    apercu(images, os.path.join(PRIVE, 'apercu_52.png'))
    for f in ('impression/boite/rectos_52.pdf', 'cartes-imprimables/boite/rectos_52.pdf'):
        print(f, os.path.getsize(os.path.join(RACINE, f)) // 1_000_000, 'Mo')


if __name__ == '__main__':
    if sys.argv[1:2] == ['carnet']:
        carnet()
    elif sys.argv[1:2] == ['assembler']:
        assembler(sys.argv[2] if len(sys.argv) > 2 else os.path.join(PRIVE, 'export'))
    else:
        sys.exit(__doc__)
