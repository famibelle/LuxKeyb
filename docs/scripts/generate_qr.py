#!/usr/bin/env python3
"""Régénère les QR codes du site.

    python docs/scripts/generate_qr.py

Les QR du site étaient jusqu'ici des fichiers sans provenance : impossible de
savoir vers quoi ils pointaient sans les scanner, ni de les refaire à
l'identique quand une URL changeait. Ce script les décrit en clair.

Convention respectée par les fichiers existants : 980 × 980 px, fond blanc,
une seule couleur d'accent par destination.
"""

import pathlib
import qrcode
from qrcode.constants import ERROR_CORRECT_H

ASSETS = pathlib.Path(__file__).resolve().parents[1] / "assets"
TAILLE = 980

# Une couleur par destination, pour que deux QR côte à côte ne se confondent
# pas. Les deux premières sont celles des fichiers déjà en place.
CODES = {
    "qr-luxkeyb-test-ferme.png": (
        "https://play.google.com/apps/testing/com.potomitan.luxkeyboard",
        "#ED2939",
    ),
    "qr-luxkeyb-apk.png": (
        "https://github.com/famibelle/LuxKeyb/releases/latest/download/"
        "LetzebuergeschClavier-latest.apk",
        "#003876",
    ),
    "qr-luxkeyb-labs.png": (
        "https://github.com/famibelle/LuxKeyb/releases/download/labs/"
        "LetzebuergeschClavier-Labs.apk",
        # --sea du thème du site, franchement distinct du rouge du test fermé
        # et du bleu de l'APK stable.
        "#0E6E76",
    ),
    "qr-luxkeyb-site.png": (
        # Le QR des supports imprimés (tract, affiche, triptyque). Il vise la
        # page d'accueil et non le test fermé : un tract est ramassé par
        # n'importe qui, et le test fermé répondrait à la plupart des scanneurs
        # que le programme n'est pas ouvert à leur compte. L'accueil, lui,
        # propose les deux chemins et les explique.
        #
        # Noir d'encre, à la différence des quatre autres : celui-ci finit sur
        # du papier, parfois photocopié en niveaux de gris, où une couleur
        # claire perd le contraste dont la lecture optique a besoin.
        "https://famibelle.github.io/LuxKeyb/",
        "#1C2624",
    ),
    "qr-luxkeyb-ambassadeurs.png": (
        # Le seul QR qui mène à une page et non à un téléchargement : il finit
        # sur une diapositive ou un stand, là où l'on montre le kit à quelqu'un
        # qui va relayer. D'où une couleur hors des quatre autres, qui sont
        # toutes celles d'une installation.
        "https://famibelle.github.io/LuxKeyb/ambassadeurs.html",
        "#4A3B8C",
    ),
    "qr-luxkeyb-luxasr.png": (
        # Directement sur l'APK, comme les trois autres : en démonstration, le
        # téléchargement doit partir dès le scan, et c'est le présentateur qui
        # dit à voix haute comment activer le clavier ensuite. Une version
        # antérieure pointait sur labs-luxasr.html pour que le téléphone lise
        # lui-même la marche à suivre ; cela coûtait une pression de plus au
        # moment précis où tout le monde regarde.
        #
        # Contrepartie assumée : ce QR devient faux si le tag `labs-luxasr` ou
        # le nom de l'asset changent. Les deux sont fixés par .github/workflows/
        # labs.yml — les modifier oblige à repasser ici avec --force.
        "https://github.com/famibelle/LuxKeyb/releases/download/labs-luxasr/"
        "LetzebuergeschClavier-LuxASR-Demo.apk",
        # --sun du thème : la couleur que le site emploie déjà pour signaler
        # ce qui demande une lecture attentive, et distincte des trois autres.
        "#C97F1E",
    ),
}


def generer(nom: str, url: str, couleur: str) -> None:
    # Correction d'erreur H : ces QR finissent aussi sur des tracts et des
    # affiches, où ils sont scannés de travers et parfois abîmés.
    qr = qrcode.QRCode(error_correction=ERROR_CORRECT_H, border=2)
    qr.add_data(url)
    qr.make(fit=True)

    img = qr.make_image(fill_color=couleur, back_color="white").convert("RGB")
    # NEAREST et non un rééchantillonnage lissé : un QR flou se scanne mal.
    img = img.resize((TAILLE, TAILLE), 0)

    dest = ASSETS / nom
    img.save(dest)
    print(f"  {nom}  {img.size[0]}×{img.size[1]}  →  {url}")


if __name__ == "__main__":
    import sys

    # Par défaut, seuls les QR manquants sont écrits. Réécrire un QR déjà en
    # place change son tramage sans rien apporter, et ces fichiers partent à
    # l'impression : mieux vaut ne pas les faire bouger sans raison.
    #
    # --force seul réécrit TOUT, ce qui a déjà fait bouger deux QR étrangers au
    # changement en cours. On peut donc le restreindre :
    #
    #     python docs/scripts/generate_qr.py --force qr-luxkeyb-luxasr.png
    force = "--force" in sys.argv
    vises = [a for a in sys.argv[1:] if not a.startswith("-")]
    if vises:
        inconnus = set(vises) - set(CODES)
        if inconnus:
            sys.exit(f"Nom(s) inconnu(s) : {', '.join(sorted(inconnus))}")

    print(f"QR codes dans {ASSETS} :")
    for nom, (url, couleur) in CODES.items():
        if vises and nom not in vises:
            continue
        if (ASSETS / nom).exists() and not force:
            print(f"  {nom}  déjà présent, ignoré (--force pour réécrire)")
            continue
        generer(nom, url, couleur)
