"""Planches annotées du guide utilisateur : une section de carte à gauche, légendes et flèches à droite.

Produit guide_carte_{haut,texte,bas,vignette}.png dans les drawables de l'app.

Deux captures d'émulateur en 1080 x 2340 (AVD kreyol_test) :
  --carte   la carte « Waasser » ouverte en grand depuis Mäi Carnet
  --grille  la grille du carnet où la vignette « Aarbecht » est entière

Les coordonnées des cibles sont en pixels de ces captures : une autre carte,
un autre écran ou un changement de mise en page de la carte demande de les
reprendre. Vérifier chaque planche à l'œil, puis dans le guide sur l'émulateur.

    python docs/scripts/guide_carte_annotee.py --carte carte.png --grille grille.png
"""
import argparse
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

NOTO = "/usr/share/fonts/truetype/noto/"
F_NUM = ImageFont.truetype(NOTO + "NotoSans-Bold.ttf", 28)
F_TITRE = ImageFont.truetype(NOTO + "NotoSans-Bold.ttf", 31)
F_TEXTE = ImageFont.truetype(NOTO + "NotoSans-Regular.ttf", 28)

LARGEUR = 1080
FOND = (255, 255, 255)
ACCENT = (230, 81, 0)
ENCRE = (40, 40, 40)
DOUX = (90, 90, 90)
MARGE = 24
ESPACE = 22


def couper(texte, police, largeur, d):
    lignes, cour = [], ""
    for mot in texte.split():
        essai = (cour + " " + mot).strip()
        if d.textlength(essai, font=police) <= largeur:
            cour = essai
        else:
            lignes.append(cour)
            cour = mot
    if cour:
        lignes.append(cour)
    return lignes


def fleche(d, x0, y0, x1, y1):
    for epais, coul in ((10, FOND), (4, ACCENT)):
        d.line((x0, y0, x1, y1), fill=coul, width=epais)
    ang = math.atan2(y1 - y0, x1 - x0)
    L, ouv = 28, 0.42
    pts = [(x1, y1),
           (x1 - L * math.cos(ang - ouv), y1 - L * math.sin(ang - ouv)),
           (x1 - L * math.cos(ang + ouv), y1 - L * math.sin(ang + ouv))]
    d.polygon(pts, fill=FOND)
    pts2 = [(x1 - 3 * math.cos(ang), y1 - 3 * math.sin(ang))] + pts[1:]
    d.polygon(pts2, fill=ACCENT)


def planche(source, boite, cible_l, legendes, sortie):
    """legendes : (repère, titre, texte, (x, y) dans l'image source), dans l'ordre vertical."""
    img = Image.open(source).convert("RGB")
    x0, y0, x1, y1 = boite
    s = cible_l / (x1 - x0)
    carte = img.crop(boite).resize((cible_l, round((y1 - y0) * s)), Image.LANCZOS)

    tmp = ImageDraw.Draw(Image.new("RGB", (10, 10)))
    col_x = MARGE + cible_l + 80
    col_l = LARGEUR - col_x - MARGE
    blocs = []
    for rep, titre, texte, (px, py) in legendes:
        lt = couper(titre, F_TITRE, col_l - 52, tmp)
        lx = couper(texte, F_TEXTE, col_l, tmp)
        h = len(lt) * 42 + len(lx) * 37
        blocs.append([rep, lt, lx, (px, py), h])

    # Chaque légende vise la hauteur de sa cible, puis on écarte les chevauchements.
    total = sum(b[4] for b in blocs) + ESPACE * (len(blocs) - 1)
    haut = max(carte.height, total) + 2 * 30
    cy = (haut - carte.height) // 2
    ys = []
    for b in blocs:
        ty = cy + (b[3][1] - y0) * s
        ys.append(ty - 21)
    for i in range(len(ys)):
        ys[i] = max(ys[i], 30 if i == 0 else ys[i - 1] + blocs[i - 1][4] + ESPACE)
    debord = ys[-1] + blocs[-1][4] - (haut - 30)
    if debord > 0:
        ys[-1] -= debord
        for i in range(len(ys) - 2, -1, -1):
            ys[i] = min(ys[i], ys[i + 1] - blocs[i][4] - ESPACE)

    out = Image.new("RGB", (LARGEUR, haut), FOND)
    out.paste(carte, (MARGE, cy))
    d = ImageDraw.Draw(out)
    for (rep, lt, lx, (px, py), h), y in zip(blocs, ys):
        tx, ty = MARGE + (px - x0) * s, cy + (py - y0) * s
        fleche(d, col_x - 16, y + 21, tx, ty)
        d.ellipse((col_x - 2, y + 1, col_x + 40, y + 43), fill=ACCENT)
        d.text((col_x + 19, y + 22), str(rep), font=F_NUM, fill=FOND, anchor="mm")
        yy = y
        for i, l in enumerate(lt):
            d.text((col_x + 54 if i == 0 else col_x, yy + 2), l, font=F_TITRE, fill=ENCRE)
            yy += 42
        for l in lx:
            d.text((col_x, yy), l, font=F_TEXTE, fill=DOUX)
            yy += 37
    out.save(sortie, optimize=True)
    print(sortie, out.size)


if __name__ == "__main__":
    racine = Path(__file__).resolve().parents[2]
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--carte", required=True)
    ap.add_argument("--grille", required=True)
    ap.add_argument("--sortie", default=str(racine / "android_keyboard/app/src/main/res/drawable-nodpi"))
    args = ap.parse_args()
    carte, T = args.carte, args.sortie

    planche(carte, (50, 490, 1032, 1335), 560, [
        (1, "Le mot", "tel que vous l'avez gagné", (900, 624)),
        (2, "Longueur", "le nombre de lettres du mot", (238, 705)),
        (3, "Illustration", "sa couleur dit le domaine du sens", (870, 930)),
        (4, "Cadre", "son métal dit la rareté", (1004, 1180)),
    ], f"{T}/guide_carte_haut.png")

    planche(carte, (50, 1310, 1032, 1720), 560, [
        (5, "Nature et jeu", "ce qu'est le mot, et où il a été gagné", (866, 1364)),
        (6, "Sens", "la traduction française du mot", (800, 1480)),
        (7, "Exemple", "le mot dans une phrase", (850, 1560)),
        (8, "Traduction", "de la phrase, quand le ZLS l'a publiée", (850, 1665)),
    ], f"{T}/guide_carte_texte.png")

    planche(carte, (50, 1560, 1032, 1925), 560, [
        (9, "VUES", "combien de fois vous avez gagné le mot", (306, 1745)),
        (10, "Joyau", "la rareté, en couleur", (566, 1795)),
        (11, "NIVEAU", "le casier de révision, de 1 à 6", (930, 1745)),
        (12, "Série", "numéro, jeu et date de la carte", (385, 1893)),
        (13, "Rang", "place du mot parmi les plus fréquents", (955, 1900)),
    ], f"{T}/guide_carte_bas.png")

    planche(args.grille, (515, 768, 984, 1236), 420, [
        ("A", "Le mot", "", (835, 1128)),
        ("B", "Jeu et sens", "l'emoji du jeu, puis le début du sens", (800, 1168)),
        ("C", "Niveau", "six traits, remplis à chaque casier gagné", (905, 1198)),
    ], f"{T}/guide_carte_vignette.png")
