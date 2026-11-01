#!/usr/bin/env python3
"""Les rectos d'une boîte, dessinés par l'application elle-même.

L'appli exporte une carte pour l'impression (appui long sur « Partager » dans
le lecteur de cartes, puis « Cette carte », carnet/ImpressionCarte.kt) : 600 dpi,
69 × 94 mm fond perdu compris, 1630 × 2220 px. Ce script fait les deux bouts de
la chaîne, autour de scripts/exporter_rectos.sh qui pilote l'émulateur :

1. `carnet` écrit un carnet (shared_prefs/carnet_cartes.xml) qui contient les
   52 cartes d'un ensemble de assets/cartes_imprimees.json (B1, la boîte de
   départ, par défaut ; NA1 et NA2, les boîtes de niveau), numérotées pour que
   le tri « Récentes » les range dans l'ordre de la boîte ;
2. `assembler` range les PNG exportés : fichiers au numéro de la collection,
   liste de contrôle, PDF d'une carte par page en 600 dpi pour l'imprimeur
   (impression/<boîte>/, privé) et en 300 dpi pour cartes-imprimables/<boîte>/
   (public).

À lancer depuis la racine du dépôt :
python3 scripts/rectos_boite.py carnet [ENSEMBLE] > carnet_cartes.xml
scripts/exporter_rectos.sh 52 [dossier d'export]
python3 scripts/rectos_boite.py assembler [ENSEMBLE] [dossier des PNG exportés]

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
VERSO = os.path.join(RACINE, 'cartes-imprimables/verso')
TAILLE = (1630, 2220)  # 69 × 94 mm à 600 dpi
# La numérotation de la collection (ImpressionCarte.COLLECTION).
COLLECTION = ['B1', 'E1', 'NA1', 'NA2']
# Par ensemble : le dossier (sous impression/ et cartes-imprimables/), la
# sélection qui donne nature et sens, le titre de l'aperçu.
BOITES = {
    'B1': ('boite', 'selection.csv', 'la boîte de 52 cartes'),
    'NA1': ('niveau_a1', 'niveaux.csv', 'la boîte Niveau A1'),
    'NA2': ('niveau_a2', 'niveaux.csv', 'la boîte Niveau A2'),
}


def ensembles():
    return json.load(open(os.path.join(ASSETS, 'cartes_imprimees.json')))['ensembles']


def premier_numero(ensemble):
    """Le numéro de la première carte de l'ensemble dans la collection."""
    e = ensembles()
    return 1 + sum(len(e[i]['cartes']) for i in COLLECTION[:COLLECTION.index(ensemble)])


def boite(ensemble='B1'):
    """Les cartes d'un ensemble : (mot, nombre ou None, paquet), dans l'ordre de la boîte."""
    ens = ensembles()[ensemble]
    paquets = [nom for nom, n in ens['paquets'] for _ in range(n)]
    cartes = []
    for cle, paquet in zip(ens['cartes'], paquets):
        mot, _, v = cle.partition('#')
        cartes.append((mot, int(v) if v else None, paquet))
    return cartes


def nom_fichier(mot):
    """Le nom que l'appli donne à l'export (impression_<nom>_69x94mm_600dpi.png)."""
    return ''.join(ch for ch in mot if ch.isalnum())


def carnet(ensemble):
    """Une carte par mot, gagnée à Wuertsich (Zuelwuert pour les nombres)."""
    d = int(time.time() * 1000)
    cartes = boite(ensemble)
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


def apercu(images, sortie, titre):
    """Les deux paquets d'un coup d'œil, 13 cartes par ligne, sans fond perdu."""
    L, H, cols, ecart = 230, 313, 13, 12
    marge = round(3 / 69 * TAILLE[0])
    rangs = -(-len(images) // cols)
    ap = Image.new('RGB', (cols * L + (cols + 1) * ecart, rangs * H + (rangs + 1) * ecart + 40), '#2b2440')
    try:
        police = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 26)
    except OSError:
        police = ImageFont.load_default()
    ImageDraw.Draw(ap).text((ecart, 8), titre, fill='white', font=police)
    for k, im in enumerate(images):
        im = im.crop((marge, marge, im.width - marge, im.height - marge)).resize((L, H), Image.LANCZOS)
        ap.paste(im, (ecart + (k % cols) * (L + ecart), 52 + (k // cols) * (H + ecart)))
    ap.save(sortie)


def assembler(ensemble, export):
    dossier, fichier, titre = BOITES[ensemble]
    prive = os.path.join(RACINE, 'impression', dossier)
    public = os.path.join(RACINE, 'cartes-imprimables', dossier)
    selection = {r['mot']: r for r in csv.DictReader(open(os.path.join(RACINE, 'Dictionnaires/cartes', fichier)))}
    rang = {}
    for i, (w, _) in enumerate(json.load(open(os.path.join(ASSETS, 'luxemburgish_dict.json')))):
        rang.setdefault(w, i + 1)

    cartes = boite(ensemble)
    debut = premier_numero(ensemble)
    manquants = [mot for mot, _, _ in cartes
                 if not os.path.exists(os.path.join(export, nom_fichier(mot) + '.png'))]
    if manquants:
        sys.exit(f'Exports manquants dans {export} : {", ".join(manquants)}')

    recto = os.path.join(prive, 'recto')
    os.makedirs(recto, exist_ok=True)
    os.makedirs(public, exist_ok=True)
    images, fichiers, pages_300, lignes = [], [], [], []
    for i, (mot, nombre, paquet) in enumerate(cartes):
        im = Image.open(os.path.join(export, nom_fichier(mot) + '.png'))
        assert im.size == TAILLE, (mot, im.size)
        # L'appli écrit de l'ARGB entièrement opaque : en RVB, le PDF n'a pas de masque de transparence.
        assert im.mode != 'RGBA' or im.getchannel('A').getextrema() == (255, 255), mot
        im = im.convert('RGB')
        dst = os.path.join(recto, f'{debut + i:03d}_{nom_fichier(mot)}.png')
        im.save(dst, dpi=(600, 600))
        fichiers.append(dst)
        images.append(im)
        pages_300.append(png(im.resize((TAILLE[0] // 2, TAILLE[1] // 2), Image.LANCZOS), 300))
        s = selection.get(mot, {})
        lignes.append({
            'numero': f'{debut + i:03d}', 'mot': mot, 'paquet': paquet,
            'nature': s.get('nature') or ('nombre' if nombre else ''), 'force': len(mot),
            'cout': cout(mot, nombre), 'rarete': rarete(mot, nombre, rang), 'sens': s.get('sens', ''),
        })

    for d in (prive, public):
        with open(os.path.join(d, 'liste.csv'), 'w', newline='') as f:
            w = csv.DictWriter(f, fieldnames=list(lignes[0]))
            w.writeheader()
            w.writerows(lignes)
    pdf(fichiers, os.path.join(prive, 'rectos_52.pdf'))
    pdf(pages_300, os.path.join(public, 'rectos_52.pdf'))
    shutil.copy(os.path.join(VERSO, 'verso.pdf'), os.path.join(prive, 'dos.pdf'))
    shutil.copy(os.path.join(VERSO, 'verso_300dpi.png'), os.path.join(prive, 'dos_300dpi.png'))
    noms = [nom for nom, _ in ensembles()[ensemble]['paquets']]
    apercu(images, os.path.join(prive, 'apercu_52.png'),
           f'Wuertduell : {titre} ({noms[0]} {debut} à {debut + 25}, {noms[1]} {debut + 26} à {debut + 51})')
    for d in (prive, public):
        f = os.path.join(d, 'rectos_52.pdf')
        print(os.path.relpath(f, RACINE), os.path.getsize(f) // 1_000_000, 'Mo')


if __name__ == '__main__':
    args = sys.argv[1:]
    ensemble = args.pop(1) if len(args) > 1 and args[1] in BOITES else 'B1'
    if args[:1] == ['carnet']:
        carnet(ensemble)
    elif args[:1] == ['assembler']:
        assembler(ensemble, args[1] if len(args) > 1
                  else os.path.join(RACINE, 'impression', BOITES[ensemble][0], 'export'))
    else:
        sys.exit(__doc__)
