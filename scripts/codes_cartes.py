#!/usr/bin/env python3
"""Fabrique les codes signés des cartes en papier (carnet/CartesPapier.kt).

Un code verse des cartes dans le carnet de l'application : un ensemble de
android_keyboard/app/src/main/assets/cartes_imprimees.json (`@B1`, la boîte
de départ), des mots (`Kaz`) ou des numéraux avec leur valeur (`eent#1`).
Chaque code a sa propre série, tirée au hasard : il ne sert qu'une fois sur
un même téléphone.

La signature demande la **clé privée**, qui ne vit que dans
impression/cle_cartes/privee.pem (hors dépôt) et chez le propriétaire. Sans
elle, ce script ne fait rien : c'est voulu.

    pip install cryptography segno
    python3 scripts/codes_cartes.py @B1 -n 500 -o impression/codes/B1
    python3 scripts/codes_cartes.py @FK27 -n 100 -o impression/codes/FK27
    python3 scripts/codes_cartes.py Kaz Hond eent#1 -n 1 -o impression/codes/essai

Sorties : codes.csv (série, texte du code) et un SVG du code QR par série,
à poser sur la carte code (15 mm de côté au moins, à tester sur l'épreuve).
Les sorties vont dans impression/ : un code signé ne se publie **jamais**
(dépôt public, issue, capture d'écran), il donnerait ses cartes à tous.
"""
import argparse
import base64
import csv
import json
import os
import secrets
import sys

RACINE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
CLE = os.path.join(RACINE, 'impression/cle_cartes/privee.pem')
ACTIF = os.path.join(RACINE, 'android_keyboard/app/src/main/assets/cartes_imprimees.json')
PREFIXE = 'LUXKEYB:P:1:'


def signer(cle, texte):
    from cryptography.hazmat.primitives import hashes
    from cryptography.hazmat.primitives.asymmetric import ec
    signature = cle.sign(texte.encode('utf-8'), ec.ECDSA(hashes.SHA256()))
    return base64.urlsafe_b64encode(signature).decode().rstrip('=')


def code(cle, cartes, serie=None):
    serie = serie or secrets.token_hex(6)
    texte = f'{PREFIXE}{serie}:{",".join(cartes)}'
    return serie, f'{texte}:{signer(cle, texte)}'


def verifier_elements(cartes):
    """Un ensemble doit exister dans l'asset : sinon l'appli dira « mets à jour »."""
    with open(ACTIF, encoding='utf-8') as f:
        ensembles = json.load(f)['ensembles']
    for e in cartes:
        if e.startswith('@') and e[1:] not in ensembles:
            sys.exit(f'ensemble inconnu : {e} (connus : {", ".join("@" + k for k in ensembles)})')
        if ':' in e or ',' in e or not e.strip('@#'):
            sys.exit(f'élément invalide : {e!r}')


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('cartes', nargs='+', help='@ENSEMBLE, mot ou numéral#valeur')
    p.add_argument('-n', '--nombre', type=int, default=1, help='nombre de codes (un par carte code imprimée)')
    p.add_argument('-o', '--sortie', required=True)
    p.add_argument('--cle', default=CLE)
    a = p.parse_args()
    if not os.path.exists(a.cle):
        sys.exit(f'clé privée absente : {a.cle}')
    verifier_elements(a.cartes)

    from cryptography.hazmat.primitives import serialization
    import segno
    with open(a.cle, 'rb') as f:
        cle = serialization.load_pem_private_key(f.read(), password=None)
    os.makedirs(a.sortie, exist_ok=True)
    with open(os.path.join(a.sortie, 'codes.csv'), 'w', newline='', encoding='utf-8') as f:
        w = csv.writer(f)
        w.writerow(['serie', 'code'])
        for _ in range(a.nombre):
            serie, texte = code(cle, a.cartes)
            w.writerow([serie, texte])
            segno.make(texte, error='m', micro=False).save(
                os.path.join(a.sortie, f'{serie}.svg'), scale=1, border=2, dark='#1B1610')
    print(f'{a.nombre} code(s) dans {a.sortie}')


if __name__ == '__main__':
    main()
