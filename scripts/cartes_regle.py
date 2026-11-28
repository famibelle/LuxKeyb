#!/usr/bin/env python3
"""Les deux cartes de règle de chaque boîte du Wuertduell, à la place des jokers.

Format poker 63 × 88 mm, fond perdu de 3 mm (69 × 94), aux couleurs de l'étui
(bleu nuit et or, scripts/emballage.py). Les textes sont ceux de la planche de
la boîte (scripts/planche_boite.py, REGLES) ; la carte code reprend la
formulation de la règle publiée (docs/regle-du-jeu.md, « Les paquets »).

- « Bataille des mots » : sa règle, et la place du **code de la boîte** avec
  son mode d'emploi (un QR signé par scripts/codes_cartes.py, différent pour
  chaque boîte : l'imprimeur le pose en données variables). Le fichier
  d'impression laisse cette place blanche ; le BAT la signale en magenta.
- « Wuertduell » : sa règle et le QR de la règle complète, qui donne les deux
  jeux sur la même page.

Le verso est le dos commun (cartes-imprimables/verso/).

Sortie dans impression/cartes_regle/ (privé) : regle_<carte>.pdf (69 × 94 mm),
regle_<carte>_600dpi.png, et regles_bat.png (les deux, zone du code marquée).

À lancer depuis la racine du dépôt : python3 scripts/cartes_regle.py
Demande Chrome, et rien d'autre : voir [rendre].
"""
import base64
import html
import os
import shutil
import subprocess
import sys
import tempfile

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import emballage as E  # noqa: E402
import planche_boite as PB  # noqa: E402

RACINE = E.RACINE
SORTIE = os.path.join(RACINE, 'impression/cartes_regle')
CODE = ('Dans l’appli, touche « Recevoir », puis « Le code d’une carte en papier » : '
        'les 52 cartes de la boîte rejoignent ton carnet.')
NBSP = ' '


def typo(s):
    """Espaces insécables du français : dans les guillemets, avant « : »."""
    return (s.replace('« ', '«' + NBSP).replace(' »', NBSP + '»')
            .replace(' :', NBSP + ':').replace(' ;', NBSP + ';'))


def ligne(x, y, s, taille, couleur, poids=400, ancre='start', famille='Roboto', style='normal', espace=0):
    return (f'<text x="{x}" y="{y}" text-anchor="{ancre}" font-family="{famille}" font-size="{taille}" '
            f'font-weight="{poids}" font-style="{style}" letter-spacing="{espace}" fill="{couleur}">'
            f'{html.escape(typo(s), quote=False)}</text>')


def coupe(s, n):
    mots, rangs, r = typo(s).split(' '), [], ''
    for m in mots:
        if r and len(r) + 1 + len(m) > n:
            rangs.append(r)
            r = m
        else:
            r = (r + ' ' + m).strip()
    return rangs + [r]


def recto(titre, age, lignes, avec_code, bat=False):
    """La face, en mm, origine au coin du format fini, fond perdu autour."""
    s = [f'<defs><radialGradient id="nuit" cx="31.5" cy="40" r="60" gradientUnits="userSpaceOnUse">'
         f'<stop offset="0" stop-color="{E.NUIT_CENTRE}"/><stop offset="1" stop-color="{E.NUIT_BORD}"/></radialGradient>'
         f'<linearGradient id="or" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{E.OR_HI}"/>'
         f'<stop offset=".6" stop-color="{E.OR_MID}"/><stop offset="1" stop-color="{E.OR_LO}"/></linearGradient></defs>',
         '<rect x="-3" y="-3" width="69" height="94" fill="url(#nuit)"/>',
         # Le double filet d'or de l'étui, dans la zone de sécurité.
         f'<rect x="3.4" y="3.4" width="56.2" height="81.2" rx="2.4" fill="none" stroke="{E.OR_MID}" stroke-width=".55"/>',
         f'<rect x="4.4" y="4.4" width="54.2" height="79.2" rx="1.8" fill="none" stroke="{E.OR_LO}" stroke-width=".25"/>',
         ligne(31.5, 10.6, 'WUERTDUELL', 2.5, E.OR_MID, 700, 'middle', espace=.9),
         # Un titre long rapetisse pour rester entre les filets (44 mm environ).
         ligne(31.5, 19.8, titre, 8.2 if len(titre) <= 11 else 6.2, 'url(#or)', 400, 'middle', famille='EBG'),
         ligne(31.5, 25.0, age, 2.9, E.CREME, 400, 'middle'),
         f'<path d="M14 28.2 H49" stroke="{E.OR_LO}" stroke-width=".3"/>']
    # La règle de la Bataille se resserre un peu pour laisser la place au code.
    pas, blanc = (4.0, 1.4) if avec_code else (4.4, 2.2)
    y = 34.0
    for t in lignes:
        if not t:
            y += blanc
            continue
        s.append(ligne(9, y, t, 3.1, E.TEXTE_DOS))
        y += pas
    q, xq = 15.0, 8.0
    # La règle de la Bataille est courte : le code et son mode d'emploi montent.
    yq = 60.0 if avec_code else 65.4
    if avec_code:
        # La place du code de la boîte : l'imprimeur y pose le QR signé de
        # chaque boîte (données variables). Son mode d'emploi à côté.
        s.append(f'<rect x="{xq}" y="{yq}" width="{q}" height="{q}" rx="{q * .06}" fill="#ffffff"/>')
        if bat:
            s.append(f'<rect x="{xq}" y="{yq}" width="{q}" height="{q}" rx="{q * .06}" fill="none" '
                     f'stroke="#e6007e" stroke-width=".35" stroke-dasharray="1 .6"/>')
            s.append(ligne(xq + q / 2, yq + q / 2 - 1, 'CODE DE', 1.8, '#e6007e', 700, 'middle'))
            s.append(ligne(xq + q / 2, yq + q / 2 + 1.6, 'LA BOÎTE', 1.8, '#e6007e', 700, 'middle'))
        x = xq + q + 3
        yc = yq + 1.9
        s.append(ligne(x, yc, 'Ton code', 2.65, E.OR_MID, 700))
        for r in coupe(CODE, 25):
            yc += 3.2
            s.append(ligne(x, yc, r, 2.65, E.CREME, 400, style='italic'))
        assert yc <= 82.5, f'le mode d’emploi du code mord sur le filet d’or ({yc:.1f} mm)'
    else:
        # Le QR de la règle complète, et sa légende à côté.
        s.append(E.qr(xq, yq, q, E.URL_REGLE))
        s.append(ligne(xq + q + 3, yq + 6.4, 'La règle complète', 2.65, E.OR_MID, 700))
        s.append(ligne(xq + q + 3, yq + 9.8, 'des deux jeux, à scanner', 2.65, E.CREME, 400, style='italic'))
    assert y - pas < yq - 1.5, f'la règle descend sur le QR ({y:.1f} mm)'
    return ''.join(s)


STYLE = f'''<style>
@font-face {{ font-family: EBG; src: url("file://{E.SERIF}"); }}
@font-face {{ font-family: Roboto; font-weight: 400; font-style: normal; src: url("file://{E.POLICES}/roboto-latin-400-normal.woff2"); }}
@font-face {{ font-family: Roboto; font-weight: 400; font-style: italic; src: url("file://{E.POLICES}/roboto-latin-400-italic.woff2"); }}
@font-face {{ font-family: Roboto; font-weight: 700; font-style: normal; src: url("file://{E.POLICES}/roboto-latin-700-normal.woff2"); }}
@page {{ size: 69mm 94mm; margin: 0; }}
html, body {{ margin: 0; padding: 0; }}
svg {{ display: block; }}
</style>'''


def page(contenu, px=None):
    taille = f'width="{px[0]}" height="{px[1]}"' if px else 'width="69mm" height="94mm"'
    return (f'<!doctype html><meta charset="utf-8">{STYLE}<body><svg xmlns="http://www.w3.org/2000/svg" '
            f'{taille} viewBox="-3 -3 69 94">{contenu}</svg></body>')


PX = (1630, 2220)       # 600 dpi sur 69 × 94 mm, fond perdu compris


def rendre(pages):
    """[(html, pdf ou None, png ou None)] : Chrome sans interface, directement.

    C'était puppeteer-core, qui n'est pas installé sur la machine et bloquait
    toute reprise des cartes de règle. Chrome sait faire les deux tout seul,
    comme pour les étuis : `--print-to-pdf` pour le PDF (le format vient du
    `@page` de la feuille de style) et `--screenshot` pour le PNG. Sa fenêtre
    sans interface rend 87 px de moins que la hauteur demandée, d'où le
    surplus puis le recadrage, exactement comme dans emballage.maquette.
    """
    d = tempfile.mkdtemp()
    base = ['google-chrome', '--headless=new', '--disable-gpu', '--no-sandbox',
            '--allow-file-access-from-files', '--virtual-time-budget=10000']
    for i, (h, pdf, png) in enumerate(pages):
        f = os.path.join(d, f'{i}.html')
        open(f, 'w', encoding='utf-8').write(h)
        if pdf:
            subprocess.run(base + ['--no-pdf-header-footer', f'--print-to-pdf={pdf}', f'file://{f}'],
                           check=True, capture_output=True, timeout=180)
        if png:
            subprocess.run(base + ['--hide-scrollbars', f'--window-size={PX[0]},{PX[1] + 87}',
                                   f'--screenshot={png}', f'file://{f}'],
                           check=True, capture_output=True, timeout=180)
            im = Image.open(png)
            assert im.size[0] >= PX[0] and im.size[1] >= PX[1], f'{png} rendu en {im.size}'
            im.crop((0, 0, *PX)).save(png)
    shutil.rmtree(d, ignore_errors=True)


def main():
    os.makedirs(SORTIE, exist_ok=True)
    cartes = [('bataille', *PB.REGLES[0], True), ('wuertduell', *PB.REGLES[1], False)]
    pages = []
    for nom, titre, age, lignes, code in cartes:
        pages.append((page(recto(titre, age, lignes, code)), os.path.join(SORTIE, f'regle_{nom}.pdf'), None))
        pages.append((page(recto(titre, age, lignes, code), px=PX), None,
                      os.path.join(SORTIE, f'regle_{nom}_600dpi.png')))
        pages.append((page(recto(titre, age, lignes, code, bat=True), px=PX), None,
                      os.path.join(SORTIE, f'regle_{nom}_bat.png')))
    rendre(pages)
    ims = [Image.open(os.path.join(SORTIE, f'regle_{n}_bat.png')) for n in ('bataille', 'wuertduell')]
    w, h = ims[0].width // 3, ims[0].height // 3
    planche = Image.new('RGB', (2 * w + 30, h + 20), '#e6e2d8')
    for i, im in enumerate(ims):
        planche.paste(im.convert('RGB').resize((w, h), Image.LANCZOS), (10 + i * (w + 10), 10))
    planche.save(os.path.join(SORTIE, 'regles_bat.png'))
    for f in sorted(os.listdir(SORTIE)):
        print(os.path.join('impression/cartes_regle', f))


if __name__ == '__main__':
    main()
