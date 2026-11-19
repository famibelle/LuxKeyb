#!/usr/bin/env python3
"""Le dos commun des cartes imprimées : cartes-imprimables/verso/.

Le dessin est dans cartes-imprimables/dessin/verso_luxe.py ; il n'avait pas
de script pour le sortir, et le PDF livré (octobre 2026) ne se refabriquait
donc plus. C'est ce que fait ce fichier, avec la recette de l'étui : Chrome
sans interface, page remise au format exact, puis pdftoppm.

Sortie : verso.pdf et verso_300dpi.png, 69 × 94 mm fond perdu compris, soit
815 × 1110 px à 300 dpi.

**La mise en page du dos livré**, relevée sur le fichier lui-même :
le dessin (300 × 440) est posé sur **84 mm de haut**, centré, comme le recto
(skill imprimeur, § 3) ; tout le reste de la page, c'est-à-dire le fond
perdu, est le métal du palier, un dégradé d'or en biais. Ses extrémités ont
été remesurées sur le PNG livré (l'écart moyen avec le fichier d'origine est
de deux niveaux sur 255), faute du script qui l'avait écrit.

À lancer depuis la racine du dépôt :
python3 scripts/verso_carte.py                 # le dos
python3 scripts/verso_carte.py --comparer FIC  # l'ancien dos, comparé à un PNG
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import emballage as E  # noqa: E402

sys.path.insert(0, os.path.join(E.RACINE, 'cartes-imprimables/dessin'))
import carte_appli as CA  # noqa: E402
import verso_luxe as VL  # noqa: E402

SORTIE = os.path.join(E.RACINE, 'cartes-imprimables/verso')
LARGEUR_MM, HAUTEUR_MM = 69.0, 94.0
LARGEUR_PX, HAUTEUR_PX = 815, 1110      # 69 × 94 mm à 300 dpi
DESSIN_H = 84.0                         # la hauteur du dessin sur la carte
DESSIN_L = DESSIN_H * VL.L / VL.H
# Le dégradé du métal, en mm sur la page (voir l'en-tête).
METAL = (3.112, 7.218, 44.171, 102.440)


def fond():
    return (f'<linearGradient id="metal" gradientUnits="userSpaceOnUse" '
            f'x1="{METAL[0]}" y1="{METAL[1]}" x2="{METAL[2]}" y2="{METAL[3]}">'
            f'<stop offset="0" stop-color="{CA.LO}"/>'
            f'<stop offset=".5" stop-color="{CA.MID}"/>'
            f'<stop offset="1" stop-color="{CA.HI}"/></linearGradient>')


def au_format(pdf):
    """La page exactement à 69 × 94 mm.

    Chrome arrondit la taille de page (196,08 × 265,92 pt au lieu de
    195,591 × 266,457) : le dos sortait 0,17 mm trop large et 0,19 mm trop
    court, ce qui déplace le trait de coupe. On remet la boîte au format
    exact et le dessin à l'échelle avec elle.
    """
    from pypdf import PdfWriter, Transformation
    from pypdf.generic import RectangleObject
    cible = (LARGEUR_MM * 72 / 25.4, HAUTEUR_MM * 72 / 25.4)
    ecrivain = PdfWriter(clone_from=pdf)
    page = ecrivain.pages[0]
    boite = page.mediabox
    page.add_transformation(Transformation().scale(cible[0] / float(boite.width),
                                                   cible[1] / float(boite.height)))
    page.mediabox = RectangleObject((0, 0, *cible))
    page.cropbox = RectangleObject((0, 0, *cible))
    # add_transformation décompresse le flux de la page : sans cela le PDF
    # passait de 0,6 à 1,7 Mo pour le même dessin.
    page.compress_content_streams()
    with open(pdf, 'wb') as fh:
        ecrivain.write(fh)


def construire(logo_png, dossier=SORTIE, nom='verso'):
    os.makedirs(dossier, exist_ok=True)
    x = (LARGEUR_MM - DESSIN_L) / 2
    y = (HAUTEUR_MM - DESSIN_H) / 2
    page = (f'<div class="page"><svg xmlns="http://www.w3.org/2000/svg" '
            f'width="{LARGEUR_MM}mm" height="{HAUTEUR_MM}mm" viewBox="0 0 {LARGEUR_MM} {HAUTEUR_MM}">'
            f'<defs>{fond()}</defs>'
            f'<rect x="0" y="0" width="{LARGEUR_MM}" height="{HAUTEUR_MM}" fill="url(#metal)"/>'
            f'<svg x="{x}" y="{y}" width="{DESSIN_L}" height="{DESSIN_H}" viewBox="0 0 {VL.L} {VL.H}">'
            f'{VL.verso(LARGEUR_PX, logo_png)}</svg></svg></div>')
    f = os.path.join(dossier, f'_{nom}.html')
    with open(f, 'w', encoding='utf-8') as fh:
        fh.write(E.page_html(page, LARGEUR_MM, HAUTEUR_MM))
    pdf = os.path.join(dossier, f'{nom}.pdf')
    E.chrome_pdf(f, pdf)
    os.remove(f)
    au_format(pdf)
    png = os.path.join(dossier, f'{nom}_300dpi.png')
    E.raster(pdf, png, 300)
    # pdftoppm arrondit 94 mm à 1111 px : on coupe la ligne en trop, dans le
    # fond perdu, pour garder la taille attendue à 300 dpi.
    from PIL import Image
    im = Image.open(png)
    if im.size != (LARGEUR_PX, HAUTEUR_PX):
        im.crop((0, 0, LARGEUR_PX, HAUTEUR_PX)).save(png, dpi=(300, 300))
    return pdf


if __name__ == '__main__':
    args = sys.argv[1:]
    if args[:1] == ['--comparer']:
        import numpy as np
        from PIL import Image
        tmp = os.path.join(E.RACINE, 'impression', '_verso_controle')
        construire(VL.logo_or(E.LOGO, 600), tmp, 'controle')
        a = np.asarray(Image.open(os.path.join(tmp, 'controle_300dpi.png')).convert('RGB')).astype(int)
        b = np.asarray(Image.open(args[1]).convert('RGB')).astype(int)
        print('tailles', a.shape, b.shape)
        if a.shape == b.shape:
            d = np.abs(a - b).max(axis=2)
            print(f'écart max {d.max()}, moyen {d.mean():.2f}, '
                  f'pixels au-delà de 8 : {(d > 8).mean() * 100:.2f} %')
    else:
        # Le lion est dans ses couleurs d'origine depuis le 2026-11-19 ; le
        # mode --comparer garde logo_or pour refaire l'ancien dos de contrôle.
        print(construire(VL.logo_couleurs(E.LOGO, 600)))
