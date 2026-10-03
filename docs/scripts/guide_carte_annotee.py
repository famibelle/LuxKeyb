"""Planches annotées du guide utilisateur : une section de carte à gauche, légendes et flèches à droite.

Produit guide_carte_{haut,texte,bas,vignette}.png dans les drawables de l'app.

Deux captures d'émulateur en 1080 x 2424 (AVD pixel9), dans la langue voulue
(cmd locale set-app-locales) :
  --carte   la carte « Waasser » ouverte en grand depuis Mäi Carnet
  --grille  la grille du carnet (A → Z, défilée en bas) où la vignette
            « Waasser » est entière

Les coordonnées des cibles sont en pixels de ces captures : une autre carte,
un autre écran ou un changement de mise en page de la carte demande de les
reprendre. Vérifier chaque planche à l'œil, puis dans le guide sur l'émulateur.

    python docs/scripts/guide_carte_annotee.py --langue de --carte carte.png --grille grille.png
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


# Légendes des planches, une série par langue de l'interface. Les numéros
# renvoient au texte du guide (sa_le_mot_tel_que_vous, sa_la_ligne_de_nature_ce,
# sa_ecu_vues_combien_de_fois, sa_la_petite_carte_ne_garde) : changer l'un
# demande de changer l'autre, dans toutes les langues.
LEGENDES = {
    "fr": {
        "haut": [("Le mot", "tel que vous l'avez gagné"), ("Longueur", "le nombre de lettres du mot"),
                 ("Illustration", "sa couleur dit le domaine du sens"), ("Cadre", "son métal dit la rareté")],
        "texte": [("Nature et jeu", "ce qu'est le mot, et où il a été gagné"), ("Sens", "la traduction du mot"),
                  ("Exemple", "le mot dans une phrase"), ("Traduction", "de la phrase, quand le ZLS l'a publiée")],
        "bas": [("VUES", "combien de fois vous avez gagné le mot"), ("Médaillon", "le jeu qui a donné la carte"),
                ("NIVEAU", "le casier de révision, de 1 à 6"), ("Série", "numéro et date de la carte"),
                ("Rang", "place du mot parmi les plus fréquents")],
        "vignette": [("Le mot", ""), ("Jeu et sens", "l'emoji du jeu, puis le début du sens"),
                     ("Niveau", "six traits, remplis à chaque casier gagné")],
    },
    "en": {
        "haut": [("The word", "as you won it"), ("Length", "the number of letters"),
                 ("Illustration", "its colour shows the field of meaning"), ("Frame", "its metal shows the rarity")],
        "texte": [("Word class and game", "what the word is, and where it was won"), ("Meaning", "the translation of the word"),
                  ("Example", "the word in a sentence"), ("Translation", "of the sentence, when the ZLS published one")],
        "bas": [("SEEN", "how many times you won the word"), ("Medallion", "the game that gave the card"),
                ("LEVEL", "the review compartment, from 1 to 6"), ("Serial", "number and date of the card"),
                ("Rank", "the word's place among the most frequent")],
        "vignette": [("The word", ""), ("Game and meaning", "the game's emoji, then the start of the meaning"),
                     ("Level", "six bars, filled with each compartment passed")],
    },
    "de": {
        "haut": [("Das Wort", "so wie Sie es gewonnen haben"), ("Länge", "die Anzahl der Buchstaben"),
                 ("Illustration", "ihre Farbe zeigt das Bedeutungsfeld"), ("Rahmen", "sein Metall zeigt die Seltenheit")],
        "texte": [("Wortart und Spiel", "was das Wort ist und wo es gewonnen wurde"), ("Bedeutung", "die Übersetzung des Wortes"),
                  ("Beispiel", "das Wort in einem Satz"), ("Übersetzung", "des Satzes, wenn der ZLS eine veröffentlicht hat")],
        "bas": [("GESEHEN", "wie oft Sie das Wort gewonnen haben"), ("Medaillon", "das Spiel, das die Karte gab"),
                ("STUFE", "das Fach der Lernkartei, von 1 bis 6"), ("Serie", "Nummer und Datum der Karte"),
                ("Rang", "Platz des Wortes unter den häufigsten")],
        "vignette": [("Das Wort", ""), ("Spiel und Bedeutung", "das Emoji des Spiels, dann der Anfang der Bedeutung"),
                     ("Stufe", "sechs Striche, gefüllt mit jedem geschafften Fach")],
    },
    "pt": {
        "haut": [("A palavra", "tal como a ganhou"), ("Comprimento", "o número de letras"),
                 ("Ilustração", "a cor indica o campo do sentido"), ("Moldura", "o metal indica a raridade")],
        "texte": [("Classe e jogo", "o que a palavra é, e onde foi ganha"), ("Sentido", "a tradução da palavra"),
                  ("Exemplo", "a palavra numa frase"), ("Tradução", "da frase, quando o ZLS a publicou")],
        "bas": [("VISTA", "quantas vezes ganhou a palavra"), ("Medalhão", "o jogo que deu a carta"),
                ("NÍVEL", "o compartimento de revisão, de 1 a 6"), ("Série", "número e data da carta"),
                ("Posição", "o lugar da palavra entre as mais frequentes")],
        "vignette": [("A palavra", ""), ("Jogo e sentido", "o emoji do jogo, depois o início do sentido"),
                     ("Nível", "seis traços, preenchidos a cada compartimento")],
    },
    "lb": {
        "haut": [("D'Wuert", "sou wéi Dir et gewonnen hutt"), ("Längt", "d'Zuel vun de Buschtawen"),
                 ("Illustratioun", "hir Faarf weist d'Bedeitungsfeld"), ("Kader", "säi Metall weist d'Seelenheet")],
        "texte": [("Wuertaart a Spill", "wat d'Wuert ass, a wou et gewonnen gouf"), ("Bedeitung", "d'Iwwersetzung vum Wuert"),
                  ("Beispill", "d'Wuert an engem Saz"), ("Iwwersetzung", "vum Saz, wann den ZLS eng publizéiert huet")],
        "bas": [("GESINN", "wéi dacks Dir d'Wuert gewonnen hutt"), ("Medaillon", "d'Spill, dat d'Kaart ginn huet"),
                ("NIVEAU", "d'Fach vun der Widderhuelung, vun 1 bis 6"), ("Serie", "Nummer an Datum vun der Kaart"),
                ("Rang", "d'Plaz vum Wuert ënner deenen heefegsten")],
        "vignette": [("D'Wuert", ""), ("Spill a Bedeitung", "d'Emoji vum Spill, dann den Ufank vun der Bedeitung"),
                     ("Niveau", "sechs Stréch, gefëllt mat all gepackte Fach")],
    },
}

# Les cibles, en pixels des captures du pixel9 (1080 x 2424) : la carte
# « Waasser » ouverte depuis Mäi Carnet trié A → Z, et la grille du même
# carnet défilée jusqu'en bas, où la vignette « Waasser » est entière.
CIBLES = {
    "haut": ((55, 505, 1025, 1250), [(895, 1164), (178, 623), (847, 847), (1012, 1090)]),
    "texte": ((55, 1270, 1025, 1700), [(708, 1331), (774, 1452), (786, 1537), (786, 1627)]),
    "bas": ((55, 1580, 1025, 1925), [(230, 1755), (538, 1760), (847, 1755), (218, 1866), (932, 1898)]),
}
VIGNETTE = ((31, 1379, 494, 1842), [(261, 1738), (261, 1778), (261, 1811)])


if __name__ == "__main__":
    racine = Path(__file__).resolve().parents[2]
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--carte", required=True)
    ap.add_argument("--grille", required=True)
    ap.add_argument("--langue", default="en", choices=sorted(LEGENDES))
    args = ap.parse_args()
    # L'anglais est la langue par défaut de l'app (values/), donc des drawables.
    dossier = "drawable-nodpi" if args.langue == "en" else f"drawable-{args.langue}-nodpi"
    T = racine / "android_keyboard/app/src/main/res" / dossier
    T.mkdir(parents=True, exist_ok=True)
    L = LEGENDES[args.langue]

    for nom in ("haut", "texte", "bas"):
        boite, cibles = CIBLES[nom]
        legendes = L[nom]
        if nom == "texte" and args.langue == "pt":
            # Le ZLS ne traduit pas en portugais : la carte n'a pas de ligne
            # de traduction (repère 8), et le sens et la phrase descendent.
            cibles = [cibles[0], (774, 1507), (786, 1592)]
            legendes = legendes[:3]
        debut = {"haut": 1, "texte": 5, "bas": 9}[nom]
        planche(args.carte, boite, 560, [
            (debut + i, titre, texte, xy) for i, ((titre, texte), xy) in enumerate(zip(legendes, cibles))
        ], f"{T}/guide_carte_{nom}.png")

    boite, cibles = VIGNETTE
    planche(args.grille, boite, 420, [
        (rep, titre, texte, xy) for rep, (titre, texte), xy in zip("ABC", L["vignette"], cibles)
    ], f"{T}/guide_carte_vignette.png")
