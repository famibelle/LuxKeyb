#!/usr/bin/env python3
"""Le BAT des cartes d'une boîte du Wuertduell, en un seul PDF à relire.

Le fichier d'impression, lui, reste ce que `rectos_boite.py assembler` écrit :
impression/<boîte>/rectos_52.pdf (600 dpi, une carte par page, 69 × 94 mm fond
perdu compris), impression/<boîte>/dos.pdf et les deux cartes de règle de
impression/cartes_regle/. Ce BAT ne les remplace pas : il les montre, avec les
traits de coupe, la zone de sécurité et le cartouche, pour que l'imprimeur et
le propriétaire relisent la même chose avant de lancer la série.

Pages : la fiche technique, la planche des 54 cartes et du dos, puis chaque
carte en vraie grandeur avec ses repères (52 rectos, 2 cartes de règle, le dos).

Les pages sont en JPEG 300 dpi : un BAT se lit et s'envoie par courriel, il ne
s'imprime pas. Rien de provisoire n'y est maquillé : ce qui manque encore est
signalé en magenta, comme sur les BAT de l'étui et du présentoir.

À lancer depuis la racine du dépôt :
python3 scripts/bat_cartes.py [ENSEMBLE]           # B1 (défaut), E1, NA1, NA2
python3 scripts/bat_cartes.py [ENSEMBLE] --leger   # le même, allégé pour le courriel
Demande Pillow et img2pdf.
"""
import csv
import io
import os
import sys
from datetime import date

from PIL import Image, ImageDraw, ImageFont

RACINE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
REGLES = os.path.join(RACINE, 'impression/cartes_regle')
VERSO = os.path.join(RACINE, 'cartes-imprimables/verso')
POLICES = '/usr/share/fonts/truetype/dejavu'

BOITES = {
    'B1': ('boite', 'Boîte de départ', 'Déieren et Iessen'),
    'E1': ('extension', 'Extension', 'Schoul et Natur'),
    'NA1': ('niveau_a1', 'Boîte Niveau A1', 'Doheem et Dobaussen'),
    'NA2': ('niveau_a2', 'Boîte Niveau A2', 'Doheem et Dobaussen'),
}

DPI = 300
FP = 3.0        # fond perdu, mm
COUPE = (63.0, 88.0)
RAYON = 3.0     # coins arrondis
SECU = 3.0      # texte et QR à au moins 3 mm du bord de coupe (skill imprimeur § 2)

ENCRE = '#1a1a1a'
GRIS = '#8a8a8a'
ROUGE = '#e3002b'
VERT = '#00903a'
MAGENTA = '#ff00a0'
PAPIER = '#ffffff'


def mm(v):
    return v * DPI / 25.4


def police(taille, gras=False, italique=False):
    nom = 'DejaVuSans' + ('-BoldOblique' if gras and italique else
                          '-Bold' if gras else '-Oblique' if italique else '')
    return ImageFont.truetype(os.path.join(POLICES, nom + '.ttf'), taille)


def page(l_mm, h_mm):
    im = Image.new('RGB', (round(mm(l_mm)), round(mm(h_mm))), PAPIER)
    return im, ImageDraw.Draw(im)


QUALITE = 94        # le BAT de lecture ; « leger » descend à 78 pour le courriel


def jpeg(im):
    b = io.BytesIO()
    im.save(b, 'JPEG', quality=QUALITE, subsampling=0 if QUALITE >= 90 else 1, dpi=(DPI, DPI))
    return b.getvalue()


def pointilles(d, boite, couleur, pas=14, epaisseur=3):
    """Un rectangle en pointillés : PIL ne sait pas pointiller tout seul."""
    x0, y0, x1, y1 = boite
    for x in range(round(x0), round(x1), pas * 2):
        d.line([x, y0, min(x + pas, x1), y0], fill=couleur, width=epaisseur)
        d.line([x, y1, min(x + pas, x1), y1], fill=couleur, width=epaisseur)
    for y in range(round(y0), round(y1), pas * 2):
        d.line([x0, y, x0, min(y + pas, y1)], fill=couleur, width=epaisseur)
        d.line([x1, y, x1, min(y + pas, y1)], fill=couleur, width=epaisseur)


# ------------------------------------------------------------------ la carte, en vraie grandeur

L_PAGE, H_PAGE = 95.0, 132.0        # la carte avec ses marges de repérage et sa légende


def planche_carte(source, legende, entete, note=None):
    """Une carte à sa taille, fond perdu compris, avec ses repères et sa légende."""
    im, d = page(L_PAGE, H_PAGE)
    carte = Image.open(source).convert('RGB').resize((round(mm(69)), round(mm(94))), Image.LANCZOS)
    x0, y0 = round((im.width - carte.width) / 2), round(mm(13))
    x1, y1 = x0 + carte.width, y0 + carte.height
    im.paste(carte, (x0, y0))

    d.text((mm(6), mm(5.5)), entete, font=police(24), fill=GRIS)
    d.rectangle([x0, y0, x1, y1], outline=GRIS, width=2)          # le bord du fond perdu

    c = mm(FP)                                                     # la coupe, 3 mm plus loin
    d.rounded_rectangle([x0 + c, y0 + c, x1 - c, y1 - c], radius=mm(RAYON), outline=ROUGE, width=4)
    s = mm(FP + SECU)                                              # la sécurité, 3 mm encore
    pointilles(d, (x0 + s, y0 + s, x1 - s, y1 - s), VERT)

    for (x, horiz) in ((x0 + c, False), (x1 - c, False)):          # traits de coupe, dans la marge
        d.line([x, y0 - mm(5), x, y0 - mm(1.5)], fill=ENCRE, width=3)
        d.line([x, y1 + mm(1.5), x, y1 + mm(5)], fill=ENCRE, width=3)
    for y in (y0 + c, y1 - c):
        d.line([x0 - mm(5), y, x0 - mm(1.5), y], fill=ENCRE, width=3)
        d.line([x1 + mm(1.5), y, x1 + mm(5), y], fill=ENCRE, width=3)

    y = y1 + mm(8)
    for i, (texte, p) in enumerate(legende):
        d.text((mm(6), y), texte, font=p, fill=ENCRE)
        y += p.size * 1.45
    if note:
        d.text((mm(6), im.height - mm(9)), note, font=police(19, italique=True), fill=MAGENTA)
    return im


# ------------------------------------------------------------------ la fiche technique

def typo(s):
    """Espaces insécables du français : dans les guillemets, avant « : ».

    Elles tiennent aussi le texte ensemble quand `coupe` le replie : coupe ne
    sépare que sur l'espace ordinaire, donc « Bataille des mots » ne se brise
    jamais sur son guillemet fermant.
    """
    return (s.replace('« ', '«\u00a0').replace(' »', '\u00a0»')
             .replace(' :', '\u00a0:').replace(' ;', '\u00a0;'))


def coupe(texte, p, largeur):
    """Découpe un texte à la largeur donnée (px), mot à mot."""
    rangs, r = [], ''
    for m in texte.split(' '):
        essai = (r + ' ' + m).strip()
        if r and p.getlength(essai) > largeur:
            rangs.append(r)
            r = m
        else:
            r = essai
    return rangs + [r] if r else rangs


def fiche(ensemble, lignes, pages, exporte):
    """La première page : ce qu'on imprime, comment, et ce qui manque encore."""
    dossier, titre, paquets = BOITES[ensemble]
    im, d = page(210, 297)
    M, GOUT = mm(16), mm(10)
    COL = (im.width - 2 * M - GOUT) / 2
    CLE = mm(26)
    p_cle, p_val, p_sec = police(28, gras=True), police(28), police(38, gras=True)

    d.rectangle([0, 0, im.width, mm(36)], fill='#2b2440')
    d.text((M, mm(10)), 'WUERTDUELL', font=police(58, gras=True), fill='#e8c86a')
    d.text((M, mm(22)), f'{titre} · bon à tirer des 54 cartes', font=police(34), fill='white')
    d.text((im.width - M, mm(23)), date.today().strftime('%d/%m/%Y'),
           font=police(28), fill='#b9b2cc', anchor='ra')

    HAUT = mm(50)
    y = [HAUT, HAUT]

    def bloc(col, s):
        x = M + col * (COL + GOUT)
        d.text((x, y[col]), s, font=p_sec, fill=ENCRE)
        y[col] += mm(7)
        d.line([x, y[col], x + COL, y[col]], fill=GRIS, width=2)
        y[col] += mm(4)

    def item(col, cle, valeur, couleur=ENCRE):
        x = M + col * (COL + GOUT)
        d.text((x, y[col]), cle, font=p_cle, fill=ENCRE)
        rangs = coupe(typo(valeur), p_val, COL - CLE)
        for i, l in enumerate(rangs):
            d.text((x + CLE, y[col] + i * mm(5.4)), l, font=p_val, fill=couleur)
        y[col] += mm(5.4) * len(rangs) + mm(2.4)

    bloc(0, 'Le produit')
    item(0, 'Jeu', f'Wuertduell · {titre} ({paquets})')
    item(0, 'Contenu', f'54 cartes : {len(lignes)} cartes à jouer et 2 cartes de règle, '
                       'à la place des jokers')
    item(0, 'Numéros', f'n° {lignes[0]["numero"]} à {lignes[-1]["numero"]} de la collection')
    item(0, 'Dos', 'Commun aux 54 cartes, identique de l’une à l’autre')

    y[0] += mm(6)
    bloc(0, 'Les cartes')
    item(0, 'Format fini', f'{COUPE[0]:g} × {COUPE[1]:g} mm (poker), coins arrondis rayon {RAYON:g} mm')
    item(0, 'Fond perdu', f'{FP:g} mm sur les quatre côtés, soit un fichier de 69 × 94 mm')
    item(0, 'Sécurité', f'Texte et symboles à au moins {SECU:g} mm du bord de coupe')
    item(0, 'Définition', '600 dpi, soit 1630 × 2220 px par carte, en RVB')
    item(0, 'Carton', '310 à 330 g/m² à âme noire ou bleue, qualité carte à jouer')
    item(0, 'Finition', 'Pelliculage toilé (linen), découpe à l’emporte-pièce')
    item(0, 'Option', 'Dorure à chaud ou vernis sélectif sur les rares et les très rares')
    item(0, 'Couleurs', 'Fournies en RVB : merci de nous envoyer votre profil ICC '
                        '(FOGRA39, FOGRA51…) pour que nous refassions l’épreuve avant conversion')

    bloc(1, 'Les fichiers d’impression')
    item(1, 'Rectos', f'{dossier}/rectos_52.pdf — {len(lignes)} pages, 600 dpi, 69 × 94 mm')
    item(1, 'Dos', f'{dossier}/dos.pdf — 1 page, même format')
    item(1, 'Règles', 'cartes_regle/regle_bataille.pdf et regle_wuertduell.pdf')
    item(1, 'Liste', f'{dossier}/liste.csv — numéro, mot, paquet, nature, Force, coût, '
                     'rareté, sens')
    item(1, 'Ce BAT', f'{pages} pages, JPEG 300 dpi : il se lit et se relit, il ne s’imprime pas')

    y[1] += mm(6)
    bloc(1, 'À compléter avant impression')
    item(1, 'Code', 'La place blanche de la carte « Bataille des mots » reçoit un QR '
                    'différent par boîte : impression à données variables, à chiffrer', MAGENTA)
    item(1, 'Lot', 'Apposé par l’imprimeur, il n’est pas dans le visuel', MAGENTA)
    item(1, 'BAT papier', 'Demandé avant la série, quelle que soit l’épreuve écran', MAGENTA)

    y[1] += mm(6)
    bloc(1, 'Bon à tirer')
    for l in ('Date : ......... / ......... / .................',
              'Nom : ..........................................................',
              'Signature :'):
        d.text((M + COL + GOUT, y[1]), l, font=p_val, fill=ENCRE)
        y[1] += mm(9)

    yb = im.height - mm(26)
    d.line([M, yb, im.width - M, yb], fill=GRIS, width=2)
    for i, l in enumerate([
            f'Cartes dessinées par l’application Lëtzebuergesch Clavier, exportées le {exporte}.',
            '© Lëtzebuergesch Clavier · Mots et sens : LOD / ZLS (CC0).',
            'Document confidentiel, remis pour devis et impression.']):
        d.text((M, yb + mm(5) + i * mm(5.4)), l, font=police(24), fill=GRIS)
    return im


# ------------------------------------------------------------------ la planche de contrôle

def planche(images, etiquettes, titre):
    im, d = page(297, 210)
    cols, rangs = 11, -(-len(images) // 11)
    haut, bas = mm(18), mm(10)
    larg = (im.width - 2 * mm(8)) / cols
    hcell = (im.height - haut - bas) / rangs
    l = round(min(larg * 0.86, hcell * 0.78 * 63 / 88))
    h = round(l * 88 / 63)
    d.text((mm(8), mm(6)), titre, font=police(28, gras=True), fill=ENCRE)
    for k, (src, et) in enumerate(zip(images, etiquettes)):
        c = Image.open(src).convert('RGB')
        m = round(FP / 69 * c.width)
        c = c.crop((m, m, c.width - m, c.height - m)).resize((l, h), Image.LANCZOS)
        x = round(mm(8) + (k % cols) * larg + (larg - l) / 2)
        y = round(haut + (k // cols) * hcell)
        im.paste(c, (x, y))
        d.rectangle([x, y, x + l, y + h], outline=GRIS, width=1)
        d.text((x + l / 2, y + h + mm(1.4)), et, font=police(15), fill=ENCRE, anchor='ma')
    return im


# ------------------------------------------------------------------

def construire(ensemble):
    dossier, titre, paquets = BOITES[ensemble]
    prive = os.path.join(RACINE, 'impression', dossier)
    lignes = list(csv.DictReader(open(os.path.join(prive, 'liste.csv'))))
    recto = os.path.join(prive, 'recto')
    fichiers = [os.path.join(recto, f) for f in sorted(os.listdir(recto)) if f.endswith('.png')]
    assert len(fichiers) == len(lignes), (len(fichiers), len(lignes))
    exporte = date.fromtimestamp(os.path.getmtime(fichiers[0])).strftime('%d/%m/%Y')

    extras = [(os.path.join(REGLES, 'regle_bataille_bat.png'), 'Règle', 'Carte de règle « Bataille des mots »',
               [('Carte de règle · Bataille des mots (dès 6 ans)', police(28, gras=True)),
                ('Remplace un joker. La zone magenta reçoit le QR du code de la boîte,', police(21)),
                ('différent d’une boîte à l’autre : impression à données variables.', police(21))],
               'À compléter avant impression : le QR du code, posé par l’imprimeur.'),
              (os.path.join(REGLES, 'regle_wuertduell_bat.png'), 'Règle', 'Carte de règle « Wuertduell »',
               [('Carte de règle · Wuertduell (dès 8 ans)', police(28, gras=True)),
                ('Remplace le second joker. Le QR mène à la règle complète des deux jeux.', police(21))], None),
              (os.path.join(VERSO, 'verso_300dpi.png'), 'Dos', 'Le dos commun',
               [('Le dos commun aux 54 cartes', police(28, gras=True)),
                ('Identique sur toutes les cartes, de la boîte comme des autres jeux :', police(21)),
                ('une seule forme, un seul fichier (dos.pdf).', police(21))], None)]

    total = 2 + len(fichiers) + len(extras)
    pages = [jpeg(fiche(ensemble, lignes, total, exporte))]
    pages.append(jpeg(planche(
        [f for f in fichiers] + [e[0] for e in extras],
        [l['numero'] for l in lignes] + [e[1] for e in extras],
        f'Wuertduell · {titre} · les 54 cartes et le dos, sans fond perdu · planche de contrôle')))

    n = 2
    for f, l in zip(fichiers, lignes):
        n += 1
        legende = [(f'n° {l["numero"]} · {l["mot"]}', police(30, gras=True)),
                   (f'{l["paquet"]} · {l["nature"]} · Force {l["force"]} · coût {l["cout"]} · {l["rarete"]}',
                    police(21)),
                   (f'« {l["sens"]} »', police(21, italique=True))]
        pages.append(jpeg(planche_carte(f, legende, f'Wuertduell · {titre} · BAT {n}/{total}')))
    for f, _, _, legende, note in extras:
        n += 1
        pages.append(jpeg(planche_carte(f, legende, f'Wuertduell · {titre} · BAT {n}/{total}', note)))

    import img2pdf
    sortie = os.path.join(prive, 'cartes_bat_leger.pdf' if QUALITE < 90 else 'cartes_bat.pdf')
    with open(sortie, 'wb') as fp:
        fp.write(img2pdf.convert(pages))
    print(sortie, f'{total} pages', f'{os.path.getsize(sortie) / 1e6:.1f} Mo')


if __name__ == '__main__':
    args = [a for a in sys.argv[1:] if a != '--leger']
    if '--leger' in sys.argv:
        # Même BAT, plus léger : 57 pages à 300 dpi passent mal par courriel.
        globals()['QUALITE'] = 78
    construire(args[0] if args else 'B1')
