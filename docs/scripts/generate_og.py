#!/usr/bin/env python3
"""Régénère les images de partage de l'accueil, une par langue.

    python docs/scripts/generate_og.py

Une image de partage (1200 × 630, balise og:image) est ce qu'affichent
LinkedIn, WhatsApp ou Facebook quand on colle le lien d'une page. Chaque
version de l'accueil a la sienne : partage.png (index.md, français),
partage-en.png, partage-de.png et partage-pt.png.

Le visuel du clavier, à droite, n'est pas dessiné ici : il vient de
partage.png lui-même, et ce script ne fait que repeindre le panneau de gauche
(slogan, accroche, deux pastilles, bouton) dans chaque langue. Repeindre le
français sur partage.png redonne donc la même image ; pour changer le visuel du
clavier, il faut remplacer partage.png, puis relancer ce script pour les trois
autres langues.

Les textes reprennent le slogan des fiches Play Store
(LuxKeybPlayStore/texts/<langue>/full_description.txt) et l'accroche de chaque
page. Polices Noto Sans, celles du système (paquet fonts-noto-core).
"""

import pathlib
from PIL import Image, ImageDraw, ImageFont

OG = pathlib.Path(__file__).resolve().parents[1] / "assets" / "og"
SOURCE = OG / "partage.png"

# langue: (fichier, titre en trois lignes, accroche en deux lignes,
#          pastilles dicter / taper, bouton)
TEXTES = {
    "fr": ("partage.png",
           ["Vous le parlez.", "Maintenant,", "vous l'écrivez."],
           ["Ce n'est pas vous qui écrivez mal le", "luxembourgeois : c'est votre clavier."],
           ["Parlez : il écrit pour vous", "Tapez : il trouve le mot et l'accent"],
           "Gratuit sur Google Play"),
    "en": ("partage-en.png",
           ["You speak it.", "Now you", "write it."],
           ["You're not bad at writing Luxembourgish:", "your keyboard just doesn't know it."],
           ["Speak: it writes for you", "Type: it finds the word and the accent"],
           "Free on Google Play"),
    "de": ("partage-de.png",
           ["Sie sprechen es.", "Jetzt schreiben", "Sie es."],
           ["Nicht Sie schreiben schlecht", "Luxemburgisch: Ihre Tastatur kennt es nicht."],
           ["Diktieren: Sie schreibt mit", "Tippen: Sie findet Wort und Akzent"],
           "Kostenlos bei Google Play"),
    "pt": ("partage-pt.png",
           ["Fala-o.", "Agora,", "escreve-o."],
           ["O problema não é a sua escrita", "em luxemburguês: é o seu teclado."],
           ["Fale: ele escreve por si", "Escreva: ele acha a palavra e o acento"],
           "Grátis no Google Play"),
}

FOND = (15, 27, 45)
BLEU = (0, 161, 222)
ROUGE = (237, 41, 57)
GRIS_CLAIR = (203, 213, 225)
GRIS = (148, 163, 184)

POLICE = "/usr/share/fonts/truetype/noto/NotoSans-%s.ttf"
def gras(taille): return ImageFont.truetype(POLICE % "Bold", taille)
def normal(taille): return ImageFont.truetype(POLICE % "Regular", taille)

X = 60          # marge gauche du texte
LIMITE = 556    # bord droit du panneau, avant la bande du clavier


def micro(d, cx, cy):
    d.rounded_rectangle([cx-7, cy-14, cx+7, cy+6], radius=7, fill="white")
    d.arc([cx-12, cy-8, cx+12, cy+12], start=0, end=180, fill="white", width=3)
    d.line([cx, cy+12, cx, cy+17], fill="white", width=3)
    d.line([cx-6, cy+17, cx+6, cy+17], fill="white", width=3)


def clavier(d, cx, cy):
    d.rounded_rectangle([cx-15, cy-10, cx+15, cy+11], radius=4, outline="white", width=3)
    for i in (-8, 0, 8):
        d.rectangle([cx+i-2, cy-4, cx+i+1, cy-1], fill="white")
    d.line([cx-8, cy+5, cx+8, cy+5], fill="white", width=3)


def ajuster(d, lignes, police, taille, marge=0):
    """Plus grande taille, à partir de `taille`, où toutes les lignes tiennent
    dans le panneau : une phrase allemande plus longue rétrécit au lieu de
    déborder sur le clavier."""
    while max(marge + d.textlength(l, font=police(taille)) for l in lignes) > LIMITE - X:
        taille -= 1
    return taille


def dessiner(source, titre, accroche, pastilles, bouton):
    im = source.copy()
    d = ImageDraw.Draw(im)
    # La bande tricolore du bord gauche (x < 16) et le clavier restent.
    d.rectangle([16, 0, 566, 630], fill=FOND)

    y = 44
    t = ajuster(d, titre, gras, 54)
    for ligne in titre:
        d.text((X, y), ligne, font=gras(t), fill="white")
        y += 66

    y += 16
    t = ajuster(d, accroche, normal, 26)
    for ligne in accroche:
        d.text((X, y), ligne, font=normal(t), fill=GRIS_CLAIR)
        y += 36

    y += 14
    t = ajuster(d, pastilles, gras, 23, marge=80)
    for icone, texte in zip((micro, clavier), pastilles):
        w = d.textlength(texte, font=gras(t))
        d.rounded_rectangle([X, y, X + 58 + w + 22, y + 46], radius=23, fill=BLEU)
        icone(d, X + 30, y + 21)
        d.text((X + 56, y + 23), texte, font=gras(t), fill="white", anchor="lm")
        y += 58

    w = d.textlength(bouton, font=gras(26))
    d.rounded_rectangle([X, 488, X + w + 44, 542], radius=27, fill=ROUGE)
    d.text((X + 22, 515), bouton, font=gras(26), fill="white", anchor="lm")

    d.text((X, 578), "Lëtzebuergesch Clavier", font=normal(21), fill=GRIS, anchor="lm")
    return im


def main():
    # Lue une seule fois avant toute écriture : la version française réécrit
    # le fichier source lui-même.
    source = Image.open(SOURCE).convert("RGB")
    for langue, (fichier, *textes) in TEXTES.items():
        dessiner(source, *textes).save(OG / fichier, optimize=True)
        print(f"{langue}: {fichier}")


if __name__ == "__main__":
    main()
