#!/usr/bin/env python3
"""Fabrique les icônes de lancement du build Labs.

    python scripts/make_labs_icon.py

Le logo reste strictement celui de l'application : on lui ajoute seulement un
bandeau « LABS » en bas, pour qu'un testeur distingue d'un coup d'œil le build
expérimental de la version stable — les deux partagent le même applicationId et
ne peuvent donc pas cohabiter sur le téléphone.

Le bandeau est masqué par le canal alpha de l'icône source. C'est ce qui le fait
suivre la silhouette : plein cadre sur `ic_launcher.png`, arrondi sur
`ic_launcher_round.png`, dont les coins sont transparents. Sans ce masque, la
bande dépasserait du disque sur la variante ronde.

Les fichiers produits vont dans `src/labs/res/`, l'arborescence de ressources du
type de build `labs` : ils remplacent ceux de `src/main/res/` pour cette seule
variante, sans toucher à l'icône des builds debug et release.
"""

import pathlib
from PIL import Image, ImageDraw, ImageFont

RACINE = pathlib.Path(__file__).resolve().parents[1]
SRC = RACINE / "android_keyboard/app/src/main/res"
DST = RACINE / "android_keyboard/app/src/labs/res"

POLICE = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
TEXTE = "LABS"

# Encre sombre plutôt qu'une couleur du drapeau : le rouge et le bleu
# appartiennent déjà au blason, et les réutiliser ferait lire le bandeau comme
# une partie du logo au lieu d'une mention ajoutée.
FOND_BANDEAU = (23, 33, 31, 255)
ENCRE = (255, 255, 255, 255)

HAUTEUR_BANDEAU = 0.28   # part de la hauteur de l'icône

# Part de la largeur occupée par « LABS ». Plus étroit sur la variante ronde :
# le bandeau y est clippé par le disque, dont la corde se rétrécit vers le bas,
# et le L et le S venaient frôler le bord.
LARGEUR_TEXTE = 0.74
LARGEUR_TEXTE_ROND = 0.60


def taille_texte(police, texte, tracking):
    d = ImageDraw.Draw(Image.new("RGBA", (1, 1)))
    largeur = sum(d.textlength(c, font=police) for c in texte)
    largeur += tracking * (len(texte) - 1)
    haut = d.textbbox((0, 0), texte, font=police)
    return largeur, haut[3] - haut[1], haut[1]


def dessiner_texte(draw, x, y, texte, police, tracking):
    """Dessine caractère par caractère : Pillow n'a pas d'interlettrage, et
    « LABS » trop serré devient illisible en dessous de 48 px."""
    for c in texte:
        draw.text((x, y), c, font=police, fill=ENCRE)
        x += draw.textlength(c, font=police) + tracking


def badger(src: pathlib.Path, dst: pathlib.Path, rond: bool = False) -> None:
    base = Image.open(src).convert("RGBA")
    w, h = base.size

    bandeau = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(bandeau)
    haut_bandeau = round(h * HAUTEUR_BANDEAU)
    d.rectangle([0, h - haut_bandeau, w, h], fill=FOND_BANDEAU)

    # Taille de police cherchée par essais : la métrique exacte dépend de la
    # police et du tracking, et une formule fermée se tromperait aux petites
    # tailles, justement celles où la lisibilité est critique.
    cible = w * (LARGEUR_TEXTE_ROND if rond else LARGEUR_TEXTE)
    taille, police, tracking = 4, None, 0
    for essai in range(4, h):
        p = ImageFont.truetype(POLICE, essai)
        t = max(1, round(essai * 0.12))
        lg, _, _ = taille_texte(p, TEXTE, t)
        if lg > cible:
            break
        taille, police, tracking = essai, p, t
    police = police or ImageFont.truetype(POLICE, taille)

    lg, ht, offset = taille_texte(police, TEXTE, tracking)
    x = (w - lg) / 2
    y = h - haut_bandeau + (haut_bandeau - ht) / 2 - offset
    dessiner_texte(d, x, y, TEXTE, police, tracking)

    # Le masque : le bandeau n'existe que là où l'icône est opaque.
    bandeau.putalpha(Image.composite(
        bandeau.split()[-1],
        Image.new("L", (w, h), 0),
        base.split()[-1].point(lambda a: 255 if a > 0 else 0),
    ))

    dst.parent.mkdir(parents=True, exist_ok=True)
    Image.alpha_composite(base, bandeau).save(dst, optimize=True)
    print(f"  {dst.relative_to(RACINE)}  ({w}×{h})")


if __name__ == "__main__":
    print("Icônes Labs :")
    n = 0
    for densite in sorted(SRC.glob("mipmap-*")):
        for nom in ("ic_launcher.png", "ic_launcher_round.png"):
            src = densite / nom
            if src.exists():
                badger(src, DST / densite.name / nom, rond="round" in nom)
                n += 1
    print(f"{n} fichiers générés dans {DST.relative_to(RACINE)}")
