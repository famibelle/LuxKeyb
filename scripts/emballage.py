#!/usr/bin/env python3
"""L'étui des boîtes du Wuertduell : un étui à rabats droits pour 54 cartes
poker (52 cartes à jouer et 2 cartes de règle), une variante par boîte.

Le même habit que le dos des cartes (cartes-imprimables/dessin/verso_luxe.py) :
la nuit bleue, la rosace guillochée, l'or et le lion du logo. La couleur de la
boîte (rouge pour la boîte de départ, vert pour l'extension, bleu et violet
pour les niveaux) dit le produit en rayon, et les emblèmes de ses deux paquets
sont ceux du médaillon des cartes (CadreOrne.emblemeDuPaquet).

Le gabarit (en mm, intérieur 64,5 × 89,5 × 19) :

    [côté A][  face  ][côté B][  dos   ][colle]
    couvercle et fond tiennent au dos, leur languette rentre derrière la face ;
    les rabats anti-poussière tiennent aux côtés ; encoche pour le pouce en
    haut de la face.

Les cotes définitives sont celles du gabarit de l'imprimeur choisi : elles se
changent ici (W, H, D), tout le dessin suit.

Sorties, dans impression/emballage/<boîte>/ (privé, comme tout fichier
d'impression) :
- etui_impression.pdf : page 1 le visuel à plat, fond perdu de 3 mm compris ;
  page 2 le tracé de découpe et de rainage seul, aux mêmes cotes ;
- etui_bat.pdf : le visuel avec le tracé, les zones de sécurité et le
  cartouche, pour relire ;
- etui_plat.png, etui_maquette.png : aperçus ;
et, pour les quatre ensemble, impression/emballage/etuis_gamme.png.

Le PDF passe par Chromium : pas de transparence avant un dégradé, pas de
texte détouré (voir carte_appli.py).

À lancer depuis la racine du dépôt : python3 scripts/emballage.py [B1 E1 NA1 NA2]
"""
import base64
import glob
import html
import io
import math
import os
import subprocess
import sys

import qrcode
from PIL import Image, ImageDraw

RACINE = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
sys.path.insert(0, os.path.join(RACINE, 'cartes-imprimables/dessin'))
import carte_appli as CA  # noqa: E402
import verso_luxe as VL  # noqa: E402

SORTIE = os.path.join(RACINE, 'impression/emballage')
POLICES = os.path.join(RACINE, 'cartes-imprimables/dessin/polices')
SERIF = os.path.join(RACINE, 'android_keyboard/app/src/main/assets/fonts/carnet_tres_rare.ttf')  # EB Garamond, OFL
LOGO = os.path.join(RACINE, 'Logos/luxembourg-logo-hd.png')
URL_REGLE = 'https://famibelle.github.io/LuxKeyb/regle-du-jeu.html'

# Les mentions qui ne s'impriment qu'une fois connues. Tant qu'elles valent
# None (ou False), le fichier d'impression les omet, et le BAT les signale en
# magenta : rien de provisoire ne doit partir chez l'imprimeur (propriétaire,
# 2026-11-01). Le code-barres de chaque boîte est son « ean », dans PRODUITS.
EDITEUR = None              # « Nom · rue · L-0000 Ville », obligatoire pour un jouet
PAYS_FABRICATION = None     # « Fabriqué en Belgique », selon l'imprimeur choisi
MARQUAGE_CE = False         # après les essais EN 71 et le dossier technique

# ------------------------------------------------------------------ les cotes (mm)

W, H, D = 64.5, 89.5, 19.0      # intérieur : largeur, hauteur, épaisseur (54 cartes de 0,32 mm, sous film)
LANGUETTE = 14.0                # languette des couvercles
COLLE = 12.0                    # patte de collage
RABAT = 15.0                    # rabats anti-poussière
POUCE = 9.0                     # demi-largeur de l'encoche
POUCE_PROF = 5.5                # sa profondeur : le texte de la face commence dessous
FP = 3.0                        # fond perdu
SECU = 2.5                      # marge de sécurité du texte, depuis un pli ou une coupe

XA, XF, XB, XD, XC = 0.0, D, D + W, 2 * D + W, 2 * D + 2 * W     # côté A, face, côté B, dos, colle
T = D + LANGUETTE                                               # hauteur au-dessus du corps
LARGEUR, HAUTEUR = XC + COLLE, 2 * T + H

# ------------------------------------------------------------------ les couleurs

NUIT_CENTRE, NUIT_BORD = VL.NUIT_CENTRE, VL.NUIT_BORD
OR_HI, OR_MID, OR_LO, OR_TRAIT = CA.HI, CA.MID, CA.LO, CA.TRAIT
CREME = '#efe3bd'
TEXTE_DOS = '#e8e2cf'

PRODUITS = {
    'B1': dict(nom='Boîte de départ', couleur='#ED2939', dossier='boite', ean=None,
               paquets=[('Déieren', 'les animaux', 'patte'), ('Iessen', 'ce qu’on mange', 'couverts')],
               eventail=['Hond', 'Kaz', 'Brout'],
               contenu='deux paquets de 26, Déieren (les animaux) et Iessen (ce qu’on mange)'),
    'E1': dict(nom='Extension', couleur='#2E9D57', dossier='extension', ean=None,
               paquets=[('Schoul', 'l’école', 'livre'), ('Natur', 'la nature', 'feuille')],
               eventail=['Buch', 'Mound', 'Sonn'],
               contenu='deux paquets de 26, Schoul (l’école) et Natur (la nature)'),
    'NA1': dict(nom='Niveau A1', couleur='#0094D8', dossier='niveau_a1', ean=None,
                paquets=[('Doheem', 'à la maison', 'maison'), ('Dobaussen', 'dehors', 'panneau')],
                eventail=['Kaffi', 'Apel', 'Bus'],
                contenu='deux paquets de 26, Doheem (à la maison) et Dobaussen (dehors)',
                mots='Tous les mots appartiennent au vocabulaire de niveau A1 que recense le dictionnaire officiel.'),
    'NA2': dict(nom='Niveau A2', couleur='#8A4FD0', dossier='niveau_a2', ean=None,
                paquets=[('Doheem', 'à la maison', 'maison'), ('Dobaussen', 'dehors', 'panneau')],
                eventail=['Haus', 'Metro', 'Schnéi'],
                contenu='deux paquets de 26, Doheem (à la maison) et Dobaussen (dehors)',
                mots='Tous les mots appartiennent au vocabulaire de niveau A2 que recense le dictionnaire officiel.'),
}

# ------------------------------------------------------------------ petits outils


def m(a, b, t):
    return CA.melange(a, b, t)


def esc(s):
    return html.escape(s, quote=False)


def texte(x, y, s, taille, couleur, poids=400, style='normal', ancre='middle', famille='Roboto', extra=''):
    return (f'<text x="{x:.2f}" y="{y:.2f}" text-anchor="{ancre}" font-family="{famille}" font-size="{taille}" '
            f'font-weight="{poids}" font-style="{style}" fill="{couleur}" {extra}>{esc(s)}</text>')


def lignes(x, y, rangs, taille, couleur, interligne, ancre='start', **k):
    """Plusieurs lignes ; un rang peut mêler du gras : [('gras', True), (' suite', False)]."""
    s = ''
    for i, rang in enumerate(rangs):
        yy = y + i * interligne
        if isinstance(rang, str):
            s += texte(x, yy, rang, taille, couleur, ancre=ancre, **k)
        else:
            morceaux = ''.join(f'<tspan font-weight="{700 if g else 400}">{esc(t)}</tspan>' for t, g in rang)
            s += (f'<text x="{x:.2f}" y="{yy:.2f}" text-anchor="{ancre}" font-family="Roboto" font-size="{taille}" '
                  f'fill="{couleur}">{morceaux}</text>')
    return s


def png64(im, fmt='PNG', **k):
    b = io.BytesIO()
    im.save(b, fmt, **k)
    return base64.b64encode(b.getvalue()).decode()


_logo = None


def logo_or():
    global _logo
    if _logo is None:
        _logo = VL.logo_or(LOGO, 600)
    return _logo


def carte_png(dossier, mot, largeur_px=520):
    """Le recto exporté par l'appli, sans fond perdu, coins arrondis (3 mm)."""
    fichiers = (glob.glob(os.path.join(RACINE, 'impression', dossier, 'recto', f'*_{mot}.png'))
                or glob.glob(os.path.join(RACINE, 'impression', dossier, 'export', f'{mot}.png')))
    if not fichiers:
        return None
    im = Image.open(fichiers[0]).convert('RGB')
    marge = round(3 / 69 * im.width)
    im = im.crop((marge, marge, im.width - marge, im.height - marge))
    im = im.resize((largeur_px, round(largeur_px * im.height / im.width)), Image.LANCZOS)
    masque = Image.new('L', im.size, 0)
    r = round(3 / 63 * im.width)
    ImageDraw.Draw(masque).rounded_rectangle((0, 0, im.width - 1, im.height - 1), r, fill=255)
    im.putalpha(masque)
    return png64(im, optimize=True)


# ------------------------------------------------------------------ les emblèmes des paquets
# Repris de CadreOrne.emblemeDuPaquet, dans un rayon de huit unités.


def embleme(forme, fond):
    blanc = '#fdfdfd'
    sombre = m(fond, '#000000', .6)
    if forme == 'patte':
        doigts = ''.join(f'<ellipse cx="{x}" cy="{y}" rx="{r * .85}" ry="{r}" fill="{blanc}"/>'
                         for x, y, r in ((-5.2, -1.6, 1.6), (-2, -5.2, 1.75), (2, -5.2, 1.75), (5.2, -1.6, 1.6)))
        return (f'<path d="M0 -0.2 C3.4 -0.2 5.4 3.6 4.2 5.4 C3.2 6.9 1.4 5.9 0 5.9 C-1.4 5.9 -3.2 6.9 -4.2 5.4 '
                f'C-5.4 3.6 -3.4 -0.2 0 -0.2 Z" fill="{blanc}"/>' + doigts)
    if forme == 'couverts':
        t = f'stroke="{blanc}" stroke-linecap="round" stroke-linejoin="round" fill="none"'
        return (''.join(f'<line x1="{x}" y1="-7" x2="{x}" y2="-3" {t} stroke-width="1.2"/>' for x in (-5.4, -3.6, -1.8))
                + f'<path d="M-5.4 -3 Q-3.6 -0.6 -1.8 -3" {t} stroke-width="1.2"/>'
                + f'<line x1="-3.6" y1="-1.8" x2="-3.6" y2="7" {t} stroke-width="1.5"/>'
                + f'<path d="M3 -7 C6.2 -5 6.2 -0.5 5.4 0.8 L3 0.8 Z" fill="{blanc}"/>'
                + f'<line x1="3.7" y1="0.8" x2="3.7" y2="7" {t} stroke-width="1.5"/>')
    if forme == 'livre':
        t = f'stroke="{blanc}" stroke-linecap="round" stroke-linejoin="round" fill="none"'
        s = ''
        for sens in (-1, 1):
            s += (f'<path d="M0 -3.6 Q{sens * 3.6} -6 {sens * 7} -4.6 L{sens * 7} 4.4 Q{sens * 3.6} 3 0 5.4" '
                  f'{t} stroke-width="1.2"/>')
            for k in range(2):
                y = -1.6 + k * 2.6
                s += f'<line x1="{sens * 1.8}" y1="{y - .4}" x2="{sens * 5.2}" y2="{y - .9}" {t} stroke-width=".8"/>'
        return s + f'<line x1="0" y1="-3.6" x2="0" y2="5.4" {t} stroke-width="1.2"/>'
    if forme == 'feuille':
        return (f'<path d="M-5.6 5.6 C-7 -2 -1 -6.6 6.4 -6.4 C6.6 1 2 7 -5.6 5.6 Z" fill="{blanc}"/>'
                f'<line x1="-5.6" y1="5.6" x2="3.6" y2="-3.6" stroke="{sombre}" stroke-width="1" stroke-linecap="round"/>'
                f'<line x1="-5.6" y1="5.6" x2="-7.4" y2="7.4" stroke="{blanc}" stroke-width="1.3" stroke-linecap="round"/>')
    if forme == 'maison':
        return (f'<path d="M0 -7.2 L7.2 -0.4 L5 -0.4 L5 6 L-5 6 L-5 -0.4 L-7.2 -0.4 Z" fill="{blanc}"/>'
                f'<rect x="2.8" y="-6" width="1.6" height="3" fill="{blanc}"/>'
                f'<rect x="-1.6" y="1.4" width="3.2" height="4.6" rx=".8" fill="{sombre}"/>')
    if forme == 'panneau':
        s = f'<rect x="-0.8" y="-7.4" width="1.6" height="14.8" fill="{blanc}"/>'
        for sens, haut in ((1, -5.6), (-1, -0.2)):
            s += (f'<path d="M{-sens * 4.6} {haut} L{sens * 4.6} {haut} L{sens * 7} {haut + 1.9} '
                  f'L{sens * 4.6} {haut + 3.8} L{-sens * 4.6} {haut + 3.8} Z" fill="{blanc}"/>')
        return s
    raise ValueError(forme)


def medaillon(cx, cy, r, forme, couleur, ident):
    """L'émail du paquet dans son anneau d'or, comme sur la carte."""
    return (f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="url(#or_anneau)" stroke="{OR_TRAIT}" stroke-width="{r * .06}"/>'
            f'<circle cx="{cx}" cy="{cy}" r="{r * .82}" fill="url(#{ident})" stroke="{OR_TRAIT}" stroke-width="{r * .05}"/>'
            f'<g transform="translate({cx} {cy}) scale({r * .82 / 10.5})">{embleme(forme, couleur)}</g>')


def lion(cx, cy, r):
    """Le médaillon du dos : anneau d'or, cœur de nuit, lion d'or."""
    return (f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="url(#or_anneau)" stroke="{OR_TRAIT}" stroke-width="{r * .04}"/>'
            f'<circle cx="{cx}" cy="{cy}" r="{r * .8}" fill="url(#coeur)" stroke="{OR_TRAIT}" stroke-width="{r * .03}"/>'
            + VL.rosace(cx, cy, r * .66, r * .05, 16, 6, '#3d5296', r * .008)
            + f'<image href="data:image/png;base64,{logo_or()}" x="{cx - r * .62}" y="{cy - r * .63}" '
              f'width="{r * 1.24}" height="{r * 1.24}"/>')


def qr(x, y, cote, url):
    """Un code QR vectoriel, sur sa zone claire."""
    q = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M, border=0)
    q.add_data(url)
    q.make(fit=True)
    mat = q.get_matrix()
    n = len(mat)
    marge = cote * 2 / (n + 4)
    u = (cote - 2 * marge) / n
    d = ''.join(f'M{x + marge + j * u:.3f} {y + marge + i * u:.3f}h{u:.3f}v{u:.3f}h{-u:.3f}z'
                for i, rang in enumerate(mat) for j, v in enumerate(rang) if v)
    return (f'<rect x="{x}" y="{y}" width="{cote}" height="{cote}" rx="{cote * .06}" fill="#ffffff"/>'
            f'<path d="{d}" fill="#0b1333"/>')


EAN_L = ['0001101', '0011001', '0010011', '0111101', '0100011', '0110001', '0101111', '0111011', '0110111', '0001011']
EAN_PARITE = ['LLLLLL', 'LLGLGG', 'LLGGLG', 'LLGGGL', 'LGLLGG', 'LGGLLG', 'LGGGLL', 'LGLGLG', 'LGLGGL', 'LGGLGL']
EAN_L_MM = 29.8                         # le code à 80 %, marges claires comprises
# Sur le dessous de l'étui (19 mm), le code n'a pas sa hauteur de 20,7 mm : ses
# barres sont raccourcies (11,6 mm au lieu de 18,3). GS1 le déconseille pour
# les caisses, c'est pourtant l'usage sur les étuis de cartes : à confirmer
# avec l'imprimeur ou le revendeur (propriétaire, 2026-11-01 : sur la tranche).
EAN_H_MM = 14.6


def cle_ean(douze):
    return str((10 - sum(int(c) * (3 if i % 2 else 1) for i, c in enumerate(douze)) % 10) % 10)


def ean13(x, y, code, hauteur=EAN_H_MM):
    """Un vrai code EAN-13 à 80 % (module de 0,264 mm), sur sa zone blanche de 29,8 mm × [hauteur]."""
    code = ''.join(c for c in str(code) if c.isdigit())
    if len(code) == 12:
        code += cle_ean(code)
    if len(code) != 13 or cle_ean(code[:12]) != code[12]:
        raise ValueError(f'EAN-13 invalide : {code}')
    r_codes = [''.join('1' if b == '0' else '0' for b in l) for l in EAN_L]
    modules = '101'
    for chiffre, parite in zip(code[1:7], EAN_PARITE[int(code[0])]):
        modules += EAN_L[int(chiffre)] if parite == 'L' else r_codes[int(chiffre)][::-1]
    modules += '01010' + ''.join(r_codes[int(c)] for c in code[7:]) + '101'
    # À 80 % : module 0,264 mm, marges claires de 11 et 7 modules ; les gardes
    # descendent de 1,3 mm sous les barres, les chiffres dessous.
    m = .264
    barres = hauteur - 3.0
    x0 = x + 11 * m
    longues = {*range(0, 3), *range(45, 50), *range(92, 95)}
    s = f'<rect x="{x}" y="{y}" width="{EAN_L_MM}" height="{hauteur}" fill="#ffffff"/>'
    for i, b in enumerate(modules):
        if b == '1':
            h = barres + 1.3 if i in longues else barres
            s += f'<rect x="{x0 + i * m:.3f}" y="{y + .3}" width="{m + .005:.3f}" height="{h}" fill="#000000"/>'
    corps = 'font-family="Roboto" font-size="2.4" fill="#000000"'
    s += f'<text x="{x0 - .5:.2f}" y="{y + hauteur - .35}" text-anchor="end" {corps}>{code[0]}</text>'
    s += f'<text x="{x0 + 24 * m:.2f}" y="{y + hauteur - .35}" text-anchor="middle" {corps} letter-spacing=".42">{code[1:7]}</text>'
    s += f'<text x="{x0 + 71 * m:.2f}" y="{y + hauteur - .35}" text-anchor="middle" {corps} letter-spacing=".42">{code[7:]}</text>'
    return s


def marquage_ce(x, y, h, couleur):
    """Le marquage CE, en tracés : deux demi-anneaux, la barre du E."""
    r = h / 2
    ri = r * .74
    s = ''
    for k, cx in enumerate((x + r, x + 2.25 * r)):
        cy = y + r
        s += (f'<path d="M{cx} {cy - r} A{r} {r} 0 0 0 {cx} {cy + r} L{cx} {cy + ri} A{ri} {ri} 0 0 1 {cx} {cy - ri} Z" '
              f'fill="{couleur}"/>')
        if k == 1:
            s += f'<rect x="{cx - r * .82}" y="{cy - (r - ri) / 2}" width="{r * .82}" height="{r - ri}" fill="{couleur}"/>'
    return s


def cadre(x0, y0, x1, y1):
    """Le filet d'or des panneaux : un trait, un filet, et quatre perles aux coins."""
    s = (f'<path d="{CA.roundrect(x0, y0, x1, y1, 2.4)}" fill="none" stroke="{OR_MID}" stroke-width=".55"/>'
         f'<path d="{CA.roundrect(x0 + .9, y0 + .9, x1 - .9, y1 - .9, 1.6)}" fill="none" stroke="{OR_LO}" stroke-width=".2"/>')
    for x, y in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
        s += CA.joyau(x, y, .75, OR_HI)
    return s


# ------------------------------------------------------------------ les panneaux
# Chaque panneau se dessine dans son repère (0, 0 en haut à gauche, en mm).


def face(p, ids):
    s = []
    cx = W / 2
    s.append(f'<rect x="{-FP}" y="{-FP}" width="{W + 2 * FP}" height="{H + 2 * FP}" fill="url(#nuit_face)"/>')
    s.append(VL.rosace(cx, 47, 27, 2.4, 22, 9, VL.GRAVURE_FINE, .1))
    s.append(VL.rosace(cx, 47, 20, 1.6, 30, 7, VL.GRAVURE, .12))
    import random
    hasard = random.Random(54)
    for _ in range(22):
        x, y = hasard.uniform(4, W - 4), hasard.uniform(24, 66)
        s.append(f'<circle cx="{x:.2f}" cy="{y:.2f}" r="{hasard.uniform(.12, .3):.2f}" fill="#e9dcb0"/>')
    s.append(cadre(2.6, 2.6, W - 2.6, H - 2.6))

    s.append(texte(cx, 10.2, 'LËTZEBUERGESCH CLAVIER', 1.95, OR_HI, 700, extra='letter-spacing=".42"'))
    s.append(texte(cx, 18.4, 'WUERTDUELL', 7.4, 'url(#or_titre)', famille='EBG', extra='letter-spacing=".5"'))
    s.append(texte(cx, 22.8, 'Le duel des mots luxembourgeois', 2.45, CREME, 400, 'italic'))
    # « FR » : l'édition, puisque les sens sont écrits en français.
    s.append(f'<rect x="{W - 11.2}" y="5.2" width="7.4" height="3.6" rx="1.8" fill="none" stroke="{OR_MID}" stroke-width=".3"/>')
    s.append(texte(W - 7.5, 7.88, 'FR', 2.2, OR_HI, 700))

    # L'éventail : trois vraies cartes, exportées par l'appli.
    lc, hc = 25.0, 25.0 * 88 / 63
    for k, (mot, angle, dx, dy) in enumerate(zip(p['eventail'], (-12, 0, 12), (-13, 0, 13), (2.4, 0, 2.4))):
        img = ids['cartes'][k]
        x, y = cx + dx - lc / 2, 27.4 + dy
        rot = f'rotate({angle} {cx + dx} {y + hc})'
        ombre = f'<path d="{CA.roundrect(x + .5, y + .7, x + lc + .5, y + hc + .7, 1.3)}" fill="#050a1f"/>'
        if img:
            s.append(f'<g transform="{rot}">{ombre}<image href="data:image/png;base64,{img}" x="{x}" y="{y}" '
                     f'width="{lc}" height="{hc}"/></g>')
        else:
            s.append(f'<g transform="{rot}">{ombre}<path d="{CA.roundrect(x, y, x + lc, y + hc, 1.3)}" fill="#3a4a7a"/>'
                     + texte(x + lc / 2, y + hc / 2, mot, 3, '#ffffff', 700) + '</g>')

    # Le bandeau du produit, et ses deux paquets.
    y0 = 66.4
    s.append(f'<path d="M2.6 {y0} H{W - 2.6} V{y0 + 8} H2.6 Z" fill="url(#bandeau)"/>')
    s.append(f'<path d="M2.6 {y0} H{W - 2.6}" stroke="{OR_HI}" stroke-width=".35"/>')
    s.append(f'<path d="M2.6 {y0 + 8} H{W - 2.6}" stroke="{OR_LO}" stroke-width=".35"/>')
    s.append(texte(cx, y0 + 5.55, p['nom'].upper(), 3.3, '#ffffff', 700, extra='letter-spacing=".35"'))
    (n1, _, f1), (n2, _, f2) = p['paquets']
    yp = 80.0
    s.append(medaillon(9.6, yp, 3.4, f1, p['couleur'], 'email'))
    s.append(medaillon(W - 9.6, yp, 3.4, f2, p['couleur'], 'email'))
    s.append(texte(14.2, yp + 1.0, n1, 2.85, CREME, 700, ancre='start'))
    s.append(texte(W - 14.2, yp + 1.0, n2, 2.85, CREME, 700, ancre='end'))
    s.append(f'<path d="M{cx} {yp - 1.6} L{cx + 1.2} {yp} L{cx} {yp + 1.6} L{cx - 1.2} {yp} Z" fill="{OR_MID}"/>')
    s.append(texte(cx, 86.0, '2 joueurs · dès 8 ans · 15 min · 54 cartes', 2.12, OR_HI, 400))
    return ''.join(s)


def dos(p, ids):
    s = []
    cx = W / 2
    s.append(f'<rect x="{-FP}" y="{-FP}" width="{W + 2 * FP}" height="{H + 2 * FP}" fill="url(#nuit_dos)"/>')
    s.append(VL.rosace(cx, 44, 34, 2, 26, 6, '#1d2b5c', .1))
    s.append(cadre(2.6, 2.6, W - 2.6, H - 2.6))
    s.append(texte(cx, 9.6, 'WUERTDUELL', 4.4, 'url(#or_titre_dos)', famille='EBG', extra='letter-spacing=".35"'))
    s.append(texte(cx, 13.5, 'Le duel des mots luxembourgeois', 2.2, CREME, 400, 'italic'))

    x0, t, li = 5.6, 2.3, 2.9
    y = 19.4
    s.append(lignes(x0, y, ['Chaque carte est un mot luxembourgeois : sa Force',
                            'est son nombre de lettres.'], t, TEXTE_DOS, li))
    y += 2 * li + 1.3
    for k, e in enumerate(['Pose des noms, des verbes et des adjectifs.',
                           'Attaque : la plus grande Force gagne.',
                           'Prends les 5 cartes du trésor adverse.']):
        s.append(f'<circle cx="{x0 + 1.5}" cy="{y - .8}" r="1.5" fill="url(#bandeau)"/>')
        s.append(texte(x0 + 1.5, y, str(k + 1), 2.1, '#ffffff', 700))
        s.append(texte(x0 + 4.4, y, e, t, TEXTE_DOS, ancre='start'))
        y += li
    y += .9
    s.append(texte(cx, y, 'Règle d’or : chaque carte jouée se lit à voix haute.', 2.3, OR_HI, 700))
    y += 2.2
    s.append(f'<path d="M8 {y} H{W - 8}" stroke="{OR_LO}" stroke-width=".25"/>')
    y += 3.4
    paragraphes = [('Dans la boîte : 52 cartes à jouer en ' + p['contenu'] + ', et 2 cartes de règle.', TEXTE_DOS, 'normal')]
    if p.get('mots'):
        paragraphes.append((p['mots'], TEXTE_DOS, 'normal'))
    paragraphes.append(('La carte code ajoute aussi les 52 cartes au carnet de l’application gratuite '
                        'Lëtzebuergesch Clavier.', CREME, 'italic'))
    for txt, coul, style in paragraphes:
        rangs = coupe(txt, 47)
        s.append(lignes(x0, y, rangs, 2.2, coul, 2.75, style=style))
        y += len(rangs) * 2.75 + .7

    # En bas : la règle complète, et à côté les mentions (le code-barres est
    # sous la boîte). Une mention encore inconnue ne s'imprime pas.
    yb = H - 4.2 - 20.7
    s.append(qr(x0, yb + 1.2, 15.5, URL_REGLE))
    s.append(texte(x0 + 7.75, yb + 19.6, 'La règle complète', 1.95, CREME, 700))
    xm = x0 + 18.6
    rangs = ['© Lëtzebuergesch Clavier', 'Mots et sens : Lëtzebuerger', 'Online Dictionnaire (ZLS), CC0']
    if EDITEUR:
        rangs += coupe(f'Éditeur : {EDITEUR}', 30)
    if PAYS_FABRICATION:
        rangs.append(PAYS_FABRICATION)
    s.append(lignes(xm, yb + 4.4, rangs, 2.12, TEXTE_DOS, 2.55))
    if MARQUAGE_CE:
        s.append(marquage_ce(W - 5.6 - 7.4, yb + 15, 4.6, CREME))
    return ''.join(s)


def cote_a(p, ids):
    """Le dos de la boîte en rayon : le nom du jeu et du produit, à la verticale.
    Le titre à droite, le bandeau du produit à gauche, un médaillon à chaque bout."""
    s = [f'<rect x="{-FP}" y="{-FP}" width="{D + 2 * FP}" height="{H + 2 * FP}" fill="url(#nuit_cote)"/>']
    s.append(medaillon(D / 2, 8.6, 4.6, p['paquets'][0][2], p['couleur'], 'email'))
    s.append(medaillon(D / 2, H - 8.6, 4.6, p['paquets'][1][2], p['couleur'], 'email'))
    s.append(f'<g transform="translate({D - 3.2} {H / 2}) rotate(-90)">'
             + texte(0, 0, 'WUERTDUELL', 6.0, 'url(#or_titre_cote)', famille='EBG', extra='letter-spacing=".45"') + '</g>')
    s.append(f'<rect x="2.8" y="16.4" width="6.2" height="{H - 32.8}" rx="3.1" fill="url(#bandeau_v)"/>')
    s.append(f'<g transform="translate(6.8 {H / 2}) rotate(-90)">'
             + texte(0, 0, p['nom'].upper(), 2.6, '#ffffff', 700, extra='letter-spacing=".3"') + '</g>')
    return ''.join(s)


def cote_b(p, ids):
    """L'autre côté : pour qui, combien de temps, deux jeux dans la boîte."""
    s = [f'<rect x="{-FP}" y="{-FP}" width="{D + 2 * FP}" height="{H + 2 * FP}" fill="url(#nuit_cote)"/>']
    s.append(lion(D / 2, 9.4, 6.4))
    rangs = [('2 joueurs · dès 8 ans · 15 min', 2.5, 700, OR_HI),
             ('Deux jeux : la Bataille des mots dès 6 ans,', 2.15, 400, CREME),
             ('le Wuertduell dès 8 ans', 2.15, 400, CREME),
             ('54 cartes · les sens sont en français', 2.15, 400, CREME)]
    g = f'<g transform="translate({D / 2} {H / 2 + 7.5}) rotate(-90)">'
    yy = -6.6
    for txt, taille, poids, coul in rangs:
        g += texte(0, yy, txt, taille, coul, poids)
        yy += 3.9 if taille > 2.3 else 3.2
    s.append(g + '</g>')
    return ''.join(s)


def numeros(p):
    """Le numéro de collection de la première carte de la boîte (ImpressionCarte.numero)."""
    sys.path.insert(0, os.path.join(RACINE, 'scripts'))
    import rectos_boite
    code = next(c for c, q in PRODUITS.items() if q is p)
    return rectos_boite.premier_numero(code)


def couvercle(p, ids):
    """Le dessus de la boîte (dessiné à l'endroit ; le gabarit le retourne)."""
    s = [f'<rect x="{-FP}" y="{-FP}" width="{W + 2 * FP}" height="{D + 2 * FP}" fill="url(#nuit_cote)"/>']
    s.append(lion(9.6, D / 2, 6.6))
    s.append(texte(18.6, D / 2 + .3, 'WUERTDUELL', 5.0, 'url(#or_titre_couvercle)', famille='EBG', ancre='start',
                   extra='letter-spacing=".4"'))
    s.append(f'<rect x="18.6" y="{D / 2 + 2.3}" width="{W - 18.6 - 5}" height="4.4" rx="2.2" fill="url(#bandeau)"/>')
    s.append(texte(18.6 + (W - 23.6) / 2, D / 2 + 5.5, p['nom'].upper(), 2.4, '#ffffff', 700, extra='letter-spacing=".3"'))
    return ''.join(s)


def fond(p, ids):
    """Le dessous : le nom de la boîte, et le code-barres quand elle en a un."""
    s = [f'<rect x="{-FP}" y="{-FP}" width="{W + 2 * FP}" height="{D + 2 * FP}" fill="url(#nuit_cote)"/>']
    # Pour le commerçant et le collectionneur : la boîte, ses numéros de
    # collection, son édition (propriétaire, 2026-11-01). La place de droite
    # reçoit le code-barres ; le lot, tamponné par l'imprimeur, trouve la sienne.
    debut = numeros(p)
    s.append(texte(4.2, 5.4, 'WUERTDUELL', 3.0, 'url(#or_titre_fond)', famille='EBG', ancre='start',
                   extra='letter-spacing=".2"'))
    s.append(texte(4.2, 8.7, p['nom'], 2.2, CREME, 700, ancre='start'))
    s.append(texte(4.2, 11.8, f'Cartes n° {debut} à {debut + 51}', 2.12, TEXTE_DOS, ancre='start'))
    s.append(f'<text x="4.2" y="14.5" font-family="Roboto" font-size="2.12" fill="{TEXTE_DOS}">1'
             f'<tspan font-size="1.45" dy="-.75">re</tspan><tspan dy=".75"> édition</tspan></text>')
    if p.get('ean'):
        s.append(ean13(W - SECU - EAN_L_MM, (D - EAN_H_MM) / 2, p['ean']))
    return ''.join(s)


def notes_bat(p):
    """Ce qui manque encore, en magenta sur le BAT seulement (jamais dans le fichier d'impression)."""
    mag = '#e4007c'
    s = ''
    if not p.get('ean'):
        x, y = XD + W - SECU - EAN_L_MM, T + H + (D - EAN_H_MM) / 2
        s += (f'<rect x="{x}" y="{y}" width="{EAN_L_MM}" height="{EAN_H_MM}" fill="none" stroke="{mag}" '
              f'stroke-width=".3" stroke-dasharray="1 .7"/>'
              + lignes(x + EAN_L_MM / 2, y + 5.2, ['Réservé au code-barres', 'EAN-13 (GS1 Belgium', '& Luxembourg)'], 2.0, mag, 2.6,
                       ancre='middle', poids=700))
    manque = [m for m, ok in (('éditeur (nom et adresse)', EDITEUR), ('pays de fabrication', PAYS_FABRICATION),
                              ('marquage CE après essais EN 71', MARQUAGE_CE)) if not ok]
    manque.append('numéro de lot (apposé par l’imprimeur)')
    y = T + H + D + LANGUETTE + 6
    s += texte(XD, y, 'À compléter avant impression :', 2.4, mag, 700, ancre='start')
    s += lignes(XD, y + 3.2, ['· ' + m for m in manque], 2.2, mag, 2.8)
    return s


def coupe(texte_, n):
    mots, rangs, cour = texte_.split(), [], ''
    for mot in mots:
        if len(cour) + len(mot) + 1 > n and cour:
            rangs.append(cour)
            cour = mot
        else:
            cour = (cour + ' ' + mot).strip()
    return rangs + [cour]


# ------------------------------------------------------------------ le gabarit

def contour():
    """La ligne de coupe, d'un seul tenant (sens horaire, depuis le haut du côté A)."""
    def rabat(x0, x1, y, vers):          # rabat anti-poussière : vers = -1 en haut, +1 en bas
        e = 3.0
        return [(x0, y + vers * 0), (x0 + .6, y + vers * (RABAT - 4)), (x0 + 4, y + vers * RABAT),
                (x1 - 1.2, y + vers * RABAT), (x1, y + vers * 3.2), (x1, y)] if x0 < x1 else None

    def languette(x0, x1, y, vers):     # couvercle + languette, du bord x0 au bord x1
        yl = y + vers * D
        yt = yl + vers * LANGUETTE
        return [(x0, yl), (x0 + 1.6, yt - vers * 4.5), (x0 + 5.5, yt), (x1 - 5.5, yt), (x1 - 1.6, yt - vers * 4.5), (x1, yl)]

    pts = []
    # Haut, de gauche à droite.
    pts += [(XA, T)] + rabat(XA, XF, T, -1)[1:]
    pts += [(XF, T), (XF + W / 2 - POUCE, T), ('arc', XF + W / 2 + POUCE, T), (XB, T)]
    rb = [(XB, T), (XB, T - 3.2), (XB + 1.2, T - RABAT), (XD - 4, T - RABAT), (XD - .6, T - (RABAT - 4)), (XD, T)]
    pts += rb[1:]
    pts += languette(XD, XC, T, -1) + [(XC, T)]
    # La patte de collage.
    pts += [(XC + COLLE, T + 5), (XC + COLLE, T + H - 5), (XC, T + H)]
    # Bas, de droite à gauche.
    pts += languette(XC, XD, T + H, 1) + [(XD, T + H)]
    pts += [(XD - .6, T + H + RABAT - 4), (XD - 4, T + H + RABAT),
            (XB + 1.2, T + H + RABAT), (XB, T + H + 3.2), (XB, T + H)]
    pts += [(XF, T + H)]
    pts += [(XF, T + H + 3.2), (XF - 1.2, T + H + RABAT), (XA + 4, T + H + RABAT), (XA + .6, T + H + RABAT - 4),
            (XA, T + H)]
    d = f'M{pts[0][0]:.2f} {pts[0][1]:.2f}'
    for pt in pts[1:]:
        if pt[0] == 'arc':
            d += f' A{POUCE} {POUCE_PROF} 0 0 0 {pt[1]:.2f} {pt[2]:.2f}'
        else:
            d += f' L{pt[0]:.2f} {pt[1]:.2f}'
    return d + ' Z'


def plis():
    seg = [(XF, T, XF, T + H), (XB, T, XB, T + H), (XD, T, XD, T + H), (XC, T, XC, T + H),
           (XA, T, XF, T), (XB, T, XD, T), (XD, T, XC, T), (XD, T - D, XC, T - D),
           (XA, T + H, XF, T + H), (XB, T + H, XD, T + H), (XD, T + H, XC, T + H), (XD, T + H + D, XC, T + H + D)]
    return ''.join(f'M{a:.2f} {b:.2f} L{c:.2f} {e:.2f} ' for a, b, c, e in seg)


def defs(p):
    c = p['couleur']
    return (CA.grad_lineaire('or_titre', 0, 12.8, 0, 19.2, [OR_HI, OR_MID, OR_LO], [0, .55, 1])
            + CA.grad_lineaire('or_titre_dos', 0, 6, 0, 10.4, [OR_HI, OR_MID, OR_LO], [0, .55, 1])
            + CA.grad_lineaire('or_titre_cote', 0, -4.4, 0, .4, [OR_HI, OR_MID, OR_LO], [0, .55, 1])
            + CA.grad_lineaire('or_titre_fond', 0, 3.1, 0, 5.4, [OR_HI, OR_MID, OR_LO], [0, .55, 1])
            + CA.grad_lineaire('or_titre_couvercle', 0, D / 2 - 4, 0, D / 2 + .8, [OR_HI, OR_MID, OR_LO], [0, .55, 1])
            + CA.grad_lineaire('bandeau', 0, 0, 0, 1, [m(c, '#ffffff', .18), c, m(c, '#000000', .25)])
            .replace('gradientUnits="userSpaceOnUse"', 'gradientUnits="objectBoundingBox"')
            + CA.grad_lineaire('bandeau_v', 0, 0, 1, 0, [m(c, '#ffffff', .18), c, m(c, '#000000', .25)])
            .replace('gradientUnits="userSpaceOnUse"', 'gradientUnits="objectBoundingBox"')
            + CA.grad_lineaire('or_anneau', 0, 0, 0, 1, [OR_HI, OR_MID, OR_LO])
            .replace('gradientUnits="userSpaceOnUse"', 'gradientUnits="objectBoundingBox"')
            + f'<radialGradient id="email" cx=".38" cy=".32" r=".75"><stop offset="0" stop-color="{m(c, "#ffffff", .35)}"/>'
              f'<stop offset=".55" stop-color="{c}"/><stop offset="1" stop-color="{m(c, "#000000", .45)}"/></radialGradient>'
            + '<radialGradient id="coeur" cx=".5" cy=".42" r=".6"><stop offset="0" stop-color="#2c4486"/>'
              '<stop offset="1" stop-color="#101a44"/></radialGradient>'
            + f'<radialGradient id="nuit_face" gradientUnits="userSpaceOnUse" cx="{W / 2}" cy="44" r="62">'
              f'<stop offset="0" stop-color="{NUIT_CENTRE}"/><stop offset="1" stop-color="{NUIT_BORD}"/></radialGradient>'
            + f'<radialGradient id="nuit_dos" gradientUnits="userSpaceOnUse" cx="{W / 2}" cy="44" r="62">'
              f'<stop offset="0" stop-color="#1a2a5e"/><stop offset="1" stop-color="{NUIT_BORD}"/></radialGradient>'
            + f'<linearGradient id="nuit_cote" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="{NUIT_BORD}"/>'
              f'<stop offset=".5" stop-color="#16244f"/><stop offset="1" stop-color="{NUIT_BORD}"/></linearGradient>'
            + CA.defs_joyaux())


def visuel(code):
    """Le visuel à plat, en mm, fond perdu compris ; le repère est le coin du gabarit."""
    p = PRODUITS[code]
    ids = {'cartes': [carte_png(p['dossier'], mot) for mot in p['eventail']]}
    corps = [f'<rect x="{-FP}" y="{-FP}" width="{LARGEUR + 2 * FP}" height="{HAUTEUR + 2 * FP}" fill="{NUIT_BORD}"/>']

    def panneau(x, y, l, h, dessin, transf=''):
        cid = f'clip{len(corps)}'
        corps.append(f'<clipPath id="{cid}"><rect x="{x - FP}" y="{y - FP}" width="{l + 2 * FP}" height="{h + 2 * FP}"/></clipPath>')
        corps.append(f'<g clip-path="url(#{cid})"><g transform="translate({x} {y}){transf}">{dessin}</g></g>')

    # Les rabats et languettes, simplement dans la nuit : on ne les voit qu'en ouvrant.
    panneau(XA, T, D, H, cote_a(p, ids))
    panneau(XF, T, W, H, face(p, ids))
    panneau(XB, T, D, H, cote_b(p, ids))
    panneau(XD, T, W, H, dos(p, ids))
    # Le couvercle tient au haut du dos et bascule vers l'avant : à plat, il est tête en bas.
    panneau(XD, T - D, W, D, couvercle(p, ids), f' rotate(180 {W / 2} {D / 2})')
    panneau(XD, T + H, W, D, fond(p, ids))
    # La patte de collage reste sans encre ni pelliculage.
    corps.append(f'<path d="M{XC} {T} L{XC + COLLE + FP} {T + 5 - FP * .4} L{XC + COLLE + FP} {T + H - 5 + FP * .4} '
                 f'L{XC} {T + H} Z" fill="#ffffff"/>')
    return defs(p), '\n'.join(corps)


def trace(cartouche=False):
    s = (f'<path d="{contour()}" fill="none" stroke="#e4002b" stroke-width=".3"/>'
         f'<path d="{plis()}" fill="none" stroke="#0080ff" stroke-width=".3" stroke-dasharray="1.6 1"/>')
    if cartouche:
        # Les zones de sécurité du texte, à 2,5 mm des plis et des coupes.
        for x, y, l, h in ((XA, T, D, H), (XF, T, W, H), (XB, T, D, H), (XD, T, W, H), (XD, T - D, W, D), (XD, T + H, W, D)):
            s += (f'<rect x="{x + SECU}" y="{y + SECU}" width="{l - 2 * SECU}" height="{h - 2 * SECU}" fill="none" '
                  f'stroke="#00a651" stroke-width=".18" stroke-dasharray=".5 .6"/>')
        s += f'<rect x="{-FP}" y="{-FP}" width="{LARGEUR + 2 * FP}" height="{HAUTEUR + 2 * FP}" fill="none" stroke="#999" stroke-width=".15"/>'
        for x, y, nom in ((XA + D / 2, T - 1.6, 'côté A'), (XF + W / 2, T - 1.6, 'face'), (XB + D / 2, T - 1.6, 'côté B'),
                          (XD + W / 2, T - D - LANGUETTE - 1.6, 'languette'), (XD + W / 2, T - D / 2, ''),
                          (XC + COLLE / 2, T + H / 2, 'colle')):
            if nom:
                s += texte(x, y, nom, 2.2, '#e4007c', 700)
        s += (f'<g transform="translate({XC + COLLE / 2 + 1} {T + H / 2 + 12}) rotate(-90)">'
              + texte(0, 0, 'sans encre ni pelliculage', 1.8, '#e4007c') + '</g>')
    return s


def page_html(contenu, l, h, transparent=False):
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>
@font-face {{ font-family: EBG; src: url("file://{SERIF}"); }}
@font-face {{ font-family: Roboto; font-weight: 400; font-style: normal; src: url("file://{POLICES}/roboto-latin-400-normal.woff2"); }}
@font-face {{ font-family: Roboto; font-weight: 400; font-style: italic; src: url("file://{POLICES}/roboto-latin-400-italic.woff2"); }}
@font-face {{ font-family: Roboto; font-weight: 700; font-style: normal; src: url("file://{POLICES}/roboto-latin-700-normal.woff2"); }}
@font-face {{ font-family: Roboto; font-weight: 700; font-style: italic; src: url("file://{POLICES}/roboto-latin-700-italic.woff2"); }}
@page {{ size: {l}mm {h}mm; margin: 0; }}
html, body {{ margin: 0; padding: 0; {'background: transparent;' if transparent else ''} }}
.page {{ width: {l}mm; height: {h}mm; overflow: hidden; page-break-after: always; break-after: page; }}
.page:last-child {{ page-break-after: auto; break-after: auto; }}
svg {{ display: block; }}
</style></head><body>{contenu}</body></html>"""


def svg(defs_, corps, l, h, vx, vy):
    return (f'<div class="page"><svg xmlns="http://www.w3.org/2000/svg" width="{l}mm" height="{h}mm" '
            f'viewBox="{vx} {vy} {l} {h}"><defs>{defs_}</defs>{corps}</svg></div>')


def chrome_pdf(fichier_html, pdf):
    subprocess.run(['google-chrome', '--headless=new', '--disable-gpu', '--no-sandbox', '--no-pdf-header-footer',
                    '--virtual-time-budget=10000', f'--print-to-pdf={pdf}', f'file://{fichier_html}'],
                   check=True, capture_output=True, timeout=180)


def raster(pdf, png, dpi, page=1):
    base = png[:-4]
    subprocess.run(['pdftoppm', '-png', '-r', str(dpi), '-f', str(page), '-l', str(page), '-singlefile', pdf, base],
                   check=True)


def maquette(dossier, plat_png, dpi):
    """La boîte en volume, vue de face et de dos, en CSS 3D dans Chromium."""
    im = Image.open(plat_png).convert('RGB')
    u = dpi / 25.4

    def zone(x, y, l, h, rot=0):
        z = im.crop((round((x + FP) * u), round((y + FP) * u), round((x + l + FP) * u), round((y + h + FP) * u)))
        return z.rotate(rot, expand=True) if rot else z

    faces = {'face': zone(XF, T, W, H), 'dos': zone(XD, T, W, H), 'droite': zone(XB, T, D, H),
             'gauche': zone(XA, T, D, H), 'dessus': zone(XD, T - D, W, D, 180), 'dessous': zone(XD, T + H, W, D)}
    chemins = {}
    for nom, z in faces.items():
        f = os.path.join(dossier, f'_face_{nom}.png')
        z.save(f)
        chemins[nom] = f
    k = 6.2   # px par mm
    w, h, d = W * k, H * k, D * k

    def boite(rx, ry, x):
        return f"""<div class="scene" style="left:{x}px"><div class="boite" style="transform: rotateX({rx}deg) rotateY({ry}deg);">
<div class="f" style="width:{w}px;height:{h}px;background-image:url('file://{chemins['face']}');transform: translateZ({d / 2}px);"></div>
<div class="f" style="width:{w}px;height:{h}px;background-image:url('file://{chemins['dos']}');transform: rotateY(180deg) translateZ({d / 2}px);"></div>
<div class="f" style="width:{d}px;height:{h}px;left:{(w - d) / 2}px;background-image:url('file://{chemins['droite']}');transform: rotateY(90deg) translateZ({w / 2}px);"></div>
<div class="f" style="width:{d}px;height:{h}px;left:{(w - d) / 2}px;background-image:url('file://{chemins['gauche']}');transform: rotateY(-90deg) translateZ({w / 2}px);"></div>
<div class="f" style="width:{w}px;height:{d}px;top:{(h - d) / 2}px;background-image:url('file://{chemins['dessus']}');transform: rotateX(90deg) translateZ({h / 2}px);"></div>
<div class="f" style="width:{w}px;height:{d}px;top:{(h - d) / 2}px;background-image:url('file://{chemins['dessous']}');transform: rotateX(-90deg) translateZ({h / 2}px);"></div>
</div><div class="ombre"></div></div>"""

    page = f"""<!doctype html><html><head><meta charset="utf-8"><style>
html,body {{ margin:0; width:1500px; height:900px; background: radial-gradient(ellipse at 50% 35%, #fbf7ee 0%, #e9e1cf 70%, #d9cfb8 100%); overflow:hidden; }}
.scene {{ position:absolute; top:150px; width:{w}px; height:{h}px; perspective: 1700px; }}
.boite {{ position:relative; width:{w}px; height:{h}px; transform-style: preserve-3d; }}
.f {{ position:absolute; background-size: 100% 100%; backface-visibility: hidden; }}
.ombre {{ position:absolute; left:-30px; top:{h + 26}px; width:{w + 90}px; height:46px; border-radius:50%;
  background: radial-gradient(ellipse, rgba(60,45,20,.45), rgba(60,45,20,0) 70%); transform: translateZ(-200px); }}
</style></head><body>{boite(-16, -32, 210)}{boite(-16, 148, 900)}</body></html>"""
    f = os.path.join(dossier, '_maquette.html')
    with open(f, 'w', encoding='utf-8') as fh:
        fh.write(page)
    sortie = os.path.join(dossier, 'etui_maquette.png')
    subprocess.run(['google-chrome', '--headless=new', '--disable-gpu', '--no-sandbox', '--hide-scrollbars',
                    '--allow-file-access-from-files', '--virtual-time-budget=6000', '--window-size=1500,990',
                    f'--screenshot={sortie}', f'file://{f}'], check=True, capture_output=True, timeout=120)
    for c in list(chemins.values()) + [f]:
        os.remove(c)
    # La fenêtre sans interface de Chromium rend 87 px de moins que sa hauteur : on recadre.
    Image.open(sortie).crop((0, 0, 1500, 900)).save(sortie)
    return sortie


def gamme(dpi=200):
    """Les quatre étuis côte à côte, de trois quarts : etuis_gamme.png."""
    k = 4.4
    w, h, d = W * k, H * k, D * k
    boites, temp = [], []
    for i, code in enumerate(PRODUITS):
        dossier = os.path.join(SORTIE, PRODUITS[code]['dossier'])
        plat = os.path.join(SORTIE, f'_gamme_{code}.png')
        raster(os.path.join(dossier, 'etui_impression.pdf'), plat, dpi)
        im = Image.open(plat).convert('RGB')
        u = dpi / 25.4

        def zone(x, y, l, hh, rot=0):
            z = im.crop((round((x + FP) * u), round((y + FP) * u), round((x + l + FP) * u), round((y + hh + FP) * u)))
            return z.rotate(rot, expand=True) if rot else z

        f = {}
        for nom, z in (('face', zone(XF, T, W, H)), ('droite', zone(XB, T, D, H)), ('dessus', zone(XD, T - D, W, D, 180))):
            f[nom] = os.path.join(SORTIE, f'_gamme_{code}_{nom}.png')
            z.save(f[nom])
        temp += list(f.values()) + [plat]
        x = 120 + i * 330
        boites.append(f"""<div class="scene" style="left:{x}px"><div class="boite">
<div class="f" style="width:{w}px;height:{h}px;background-image:url('file://{f['face']}');transform: translateZ({d / 2}px);"></div>
<div class="f" style="width:{d}px;height:{h}px;left:{(w - d) / 2}px;background-image:url('file://{f['droite']}');transform: rotateY(90deg) translateZ({w / 2}px);"></div>
<div class="f" style="width:{w}px;height:{d}px;top:{(h - d) / 2}px;background-image:url('file://{f['dessus']}');transform: rotateX(90deg) translateZ({h / 2}px);"></div>
</div><div class="ombre"></div></div>""")
    page = f"""<!doctype html><html><head><meta charset="utf-8"><style>
html,body {{ margin:0; width:1500px; height:700px; overflow:hidden;
  background: radial-gradient(ellipse at 50% 30%, #fbf7ee 0%, #e9e1cf 70%, #d9cfb8 100%); }}
.scene {{ position:absolute; top:150px; width:{w}px; height:{h}px; perspective: 1600px; }}
.boite {{ position:relative; width:{w}px; height:{h}px; transform-style: preserve-3d; transform: rotateX(-14deg) rotateY(-26deg); }}
.f {{ position:absolute; background-size: 100% 100%; backface-visibility: hidden; }}
.ombre {{ position:absolute; left:-20px; top:{h + 18}px; width:{w + 70}px; height:36px; border-radius:50%;
  background: radial-gradient(ellipse, rgba(60,45,20,.42), rgba(60,45,20,0) 70%); }}
</style></head><body>{''.join(boites)}</body></html>"""
    f = os.path.join(SORTIE, '_gamme.html')
    with open(f, 'w', encoding='utf-8') as fh:
        fh.write(page)
    sortie = os.path.join(SORTIE, 'etuis_gamme.png')
    subprocess.run(['google-chrome', '--headless=new', '--disable-gpu', '--no-sandbox', '--hide-scrollbars',
                    '--allow-file-access-from-files', '--virtual-time-budget=6000', '--window-size=1500,787',
                    f'--screenshot={sortie}', f'file://{f}'], check=True, capture_output=True, timeout=120)
    Image.open(sortie).crop((0, 0, 1500, 700)).save(sortie)
    for c in temp + [f]:
        os.remove(c)
    return sortie


def planche(code, dpi=203.2):
    """L'emballage complet d'une boîte sur une image : la boîte en volume, face
    et dos, puis ses six faces dépliées en croix, chacune dans le sens où elle
    se lit une fois montée : etui_complet.png."""
    from PIL import ImageFont
    p = PRODUITS[code]
    dossier = os.path.join(SORTIE, p['dossier'])
    plat = os.path.join(dossier, '_planche.png')
    raster(os.path.join(dossier, 'etui_impression.pdf'), plat, dpi)
    im = Image.open(plat).convert('RGB')
    os.remove(plat)
    u = dpi / 25.4

    def zone(x, y, l, h, rot=0):
        z = im.crop((round((x + FP) * u), round((y + FP) * u), round((x + l + FP) * u), round((y + h + FP) * u)))
        return z.rotate(rot, expand=True) if rot else z

    faces = {'côté A': zone(XA, T, D, H), 'face': zone(XF, T, W, H), 'côté B': zone(XB, T, D, H),
             'dos': zone(XD, T, W, H), 'dessus': zone(XD, T - D, W, D, 180), 'dessous': zone(XD, T + H, W, D)}
    vol = Image.open(os.path.join(dossier, 'etui_maquette.png')).convert('RGB').crop((150, 90, 1350, 790))
    ecart, marge = 26, 40
    lw, lh, ld = faces['face'].width, faces['face'].height, faces['côté A'].width
    largeur = max(vol.width, 2 * ld + 2 * lw + 3 * ecart) + 2 * marge
    y_net = marge + 70 + vol.height + 70
    hauteur = y_net + ld + lh + ld + 2 * ecart + 60 + marge
    fond = Image.new('RGB', (largeur, hauteur), '#f4efe4')
    d = ImageDraw.Draw(fond)
    try:
        gras = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 34)
        petit = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 20)
    except OSError:
        gras = petit = ImageFont.load_default()
    d.text((marge, marge), f'Wuertduell · {p["nom"]} · l’étui du jeu de 54 cartes', fill='#1b2340', font=gras)
    cotes = ' × '.join(f'{v:g}'.replace('.', ',') for v in (W, H, D))
    d.text((marge, marge + 44), f'{cotes} mm · 52 cartes à jouer, {p["contenu"]}, et 2 cartes de règle',
           fill='#5b5446', font=petit)
    fond.paste(vol, ((largeur - vol.width) // 2, marge + 70))
    x0 = (largeur - (2 * ld + 2 * lw + 3 * ecart)) // 2
    xs = {'côté A': x0, 'face': x0 + ld + ecart, 'côté B': x0 + ld + lw + 2 * ecart, 'dos': x0 + 2 * ld + lw + 3 * ecart}
    y_corps = y_net + ld + ecart
    for nom, x in xs.items():
        fond.paste(faces[nom], (x, y_corps))
    fond.paste(faces['dessus'], (xs['face'], y_net))
    fond.paste(faces['dessous'], (xs['face'], y_corps + lh + ecart))
    for nom, (x, y, l) in {'dessus': (xs['face'], y_net, lw), 'côté A': (xs['côté A'], y_corps, ld),
                           'face': (xs['face'], y_corps, lw), 'côté B': (xs['côté B'], y_corps, ld),
                           'dos': (xs['dos'], y_corps, lw), 'dessous': (xs['face'], y_corps + lh + ecart, lw)}.items():
        h = faces[nom].height
        d.text((x + l / 2, y + h + 4), nom, fill='#5b5446', font=petit, anchor='mt') if nom not in ('dessus',) else \
            d.text((x - 10, y + h / 2), nom, fill='#5b5446', font=petit, anchor='rm')
    sortie = os.path.join(dossier, 'etui_complet.png')
    fond.save(sortie)
    return sortie


def produire(code):
    dossier = os.path.join(SORTIE, PRODUITS[code]['dossier'])
    os.makedirs(dossier, exist_ok=True)
    defs_, corps = visuel(code)
    l, h = LARGEUR + 2 * FP, HAUTEUR + 2 * FP

    # Le fichier de l'imprimeur : le visuel, puis le tracé seul.
    pages = svg(defs_, corps, l, h, -FP, -FP) + svg('', trace(), l, h, -FP, -FP)
    f = os.path.join(dossier, '_impression.html')
    with open(f, 'w', encoding='utf-8') as fh:
        fh.write(page_html(pages, l, h))
    chrome_pdf(f, os.path.join(dossier, 'etui_impression.pdf'))
    os.remove(f)

    # Le BAT : le visuel, le tracé, les zones, le cartouche.
    marge, entete = 12, 16
    lb, hb = l + 2 * marge, h + 2 * marge + entete
    p = PRODUITS[code]
    cartouche = (texte(-FP, -FP - marge - entete + 9, f'Wuertduell · étui « {p["nom"]} » · BAT à plat', 5, '#222', 700, ancre='start')
                 + texte(-FP, -FP - marge - entete + 15.5,
                         f'Intérieur {W:g} × {H:g} × {D:g} mm (54 cartes poker sous film) · fond perdu {FP:g} mm · '
                         f'rouge : coupe · bleu pointillé : rainage · vert : sécurité du texte ({SECU:g} mm) · '
                         'cotes à caler sur le gabarit de l’imprimeur', 2.6, '#444', ancre='start'))
    pages = svg(defs_, corps + trace(True) + notes_bat(p) + cartouche, lb, hb + 14, -FP - marge, -FP - marge - entete)
    f = os.path.join(dossier, '_bat.html')
    with open(f, 'w', encoding='utf-8') as fh:
        fh.write(page_html(pages, lb, hb + 14))
    chrome_pdf(f, os.path.join(dossier, 'etui_bat.pdf'))
    os.remove(f)

    raster(os.path.join(dossier, 'etui_bat.pdf'), os.path.join(dossier, 'etui_plat.png'), 150)
    plat = os.path.join(dossier, '_plat300.png')
    raster(os.path.join(dossier, 'etui_impression.pdf'), plat, 300)
    maquette(dossier, plat, 300)
    os.remove(plat)
    return planche(code)


if __name__ == '__main__':
    for code in sys.argv[1:] or list(PRODUITS):
        print(code, produire(code))
    if not sys.argv[1:]:
        print(gamme())
