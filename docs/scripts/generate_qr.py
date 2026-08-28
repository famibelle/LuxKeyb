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
    tout = "--force" in sys.argv

    print(f"QR codes dans {ASSETS} :")
    for nom, (url, couleur) in CODES.items():
        if (ASSETS / nom).exists() and not tout:
            print(f"  {nom}  déjà présent, ignoré (--force pour réécrire)")
            continue
        generer(nom, url, couleur)
