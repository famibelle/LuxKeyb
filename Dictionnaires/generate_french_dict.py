#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
🇫🇷 DICTIONNAIRE FRANÇAIS — la seconde rangée de suggestions
=============================================================

Produit `android_keyboard/app/src/main/assets/french_simple_dict.json` à partir
de **Lexique 3.83**, la base lexicale de référence du français.

    cd Dictionnaires
    python generate_french_dict.py --strict

Ce que ça remplace
------------------

L'actif livré jusqu'ici contenait **662 mots aux fréquences inventées** : `le`
15000, `de` 12000, `un` 10000, `à` 9500 — une suite décroissante écrite à la
main, héritée du clavier créole dont ce projet est issu. Le support bilingue
était donc câblé de bout en bout et alimenté par rien.

Deux consommateurs, et le second est le plus important
------------------------------------------------------

1. `FrenchDictionary.getSuggestions()` remplit la **seconde rangée** de la barre
   de suggestions, à partir de trois lettres, deux propositions au maximum.

2. `FrenchDictionary.containsWord()` alimente `SuggestionEngine.isKnownWord()`,
   que `KreyolSpellCheckerService` interroge pour décider s'il souligne un mot.
   Or `res/xml/kreyol_spellchecker.xml` déclare la locale **`fr`** : le clavier
   remplace donc le correcteur français du système. Avec 662 mots, il soulignait
   la quasi-totalité du français écrit par l'utilisateur, dans **toutes** ses
   applications. C'est ce défaut-là que la couverture répare, et c'est pourquoi
   on garde les formes rares au lieu de tronquer sur la fréquence : un mot rare
   mais correct ne doit pas être souligné.

Pourquoi Lexique plutôt qu'une liste de fréquences brute
--------------------------------------------------------

Lexique est vérifié à la main : ni bruit d'OCR, ni mots anglais, ni noms propres
en vrac, contrairement aux listes tirées directement d'un corpus. Il porte les
**formes fléchies** (`mangeons`, `mangeaient`), qui sont ce qu'un clavier
complète, et non les seuls lemmes. Et il donne deux fréquences par forme, dans
deux registres distincts.

La fréquence livrée est `freqfilms2 + freqlivres`, deux taux par million qu'on
additionne — l'un mesuré sur des sous-titres de films, l'autre sur des livres.
Garder les deux est le même raisonnement que LuxAlign + LETZ côté luxembourgeois
et il se vérifie sur les mots : `bonjour` vaut 569,88 aux sous-titres contre
50,74 aux livres, un rapport de 11 ; le registre parlé est celui du téléphone,
mais il perdrait `cependant` et `notamment`, que les gens écrivent aussi.

⚠️ Les fréquences de Lexique sont **par entrée (forme, lemme, catégorie)** et
non par forme : `est` apparaît en ADJ, NOM, AUX et VER avec quatre valeurs
différentes. Il faut donc **sommer** les lignes d'une même graphie, sinon la
forme la plus courante du français se retrouve au niveau du nom commun « est »
(le point cardinal).

Note d'échelle : contrairement au dictionnaire luxembourgeois, celle-ci n'a
aucune contrainte de calibrage. `mergeSuggestionsLuxFirst()` réserve les
positions 1 à 3 au luxembourgeois et 4 à 5 au français sans jamais comparer
leurs scores, et `frenchPenalty` multiplie toutes les propositions françaises
par la même constante. La fréquence ne sert donc qu'à ordonner le français
entre lui, et on la garde en occurrences par million, très en dessous de
`EDIT_DISTANCE_WEIGHT`.

Licence
-------

Lexique est publié sous **CC BY-SA 4.0**. L'attribution est obligatoire et
figure dans `Dictionnaires/CORPUS.md` ainsi que dans la carte « Sources » de
l'application. Le partage à l'identique porte sur cet actif dérivé.

Fait avec ❤️ pour préserver le Luxembourgeois
"""

import argparse
import csv
import json
import sys
import urllib.request
from datetime import datetime
from pathlib import Path

if sys.platform.startswith('win'):
    import codecs
    sys.stdout = codecs.getwriter('utf-8')(sys.stdout.buffer, 'strict')
    sys.stderr = codecs.getwriter('utf-8')(sys.stderr.buffer, 'strict')

RACINE = Path(__file__).resolve().parent
RACINE_ASSETS = RACINE.parent / "android_keyboard/app/src/main/assets"
CHEMIN_SORTIE = RACINE_ASSETS / "french_simple_dict.json"
DOSSIER_CACHE = RACINE / "french_data"
DOSSIER_BACKUPS = RACINE / "backups"

URL_LEXIQUE = "http://www.lexique.org/databases/Lexique383/Lexique383.tsv"
FICHIER_LEXIQUE = "Lexique383.tsv"

SOURCE = "Lexique 3.83 (New & Pallier, lexique.org)"
LICENCE = "CC BY-SA 4.0"
ATTRIBUTION = (
    "New, B., Pallier, C., Brysbaert, M., Ferrand, L. (2004). Lexique 2 : "
    "A New French Lexical Database. Behavior Research Methods, Instruments, "
    "& Computers, 36(3), 516-524. Base Lexique 3.83, http://www.lexique.org, "
    "CC BY-SA 4.0."
)

# Lexique 3.83 compte 125 653 graphies distinctes, dont 305 locutions à espace.
# Sous la moitié, c'est que le fichier a changé de structure ou n'a pas été
# téléchargé en entier : mieux vaut échouer que livrer un correcteur français
# qui souligne tout.
SEUIL_STRICT = 60_000


def telecharger_lexique(hors_ligne=False):
    """Le TSV de Lexique, mis en cache hors du dépôt (25 Mo)."""
    DOSSIER_CACHE.mkdir(parents=True, exist_ok=True)
    chemin = DOSSIER_CACHE / FICHIER_LEXIQUE
    if chemin.exists():
        print(f"   📁 cache : {chemin.name} ({chemin.stat().st_size / 1e6:.1f} Mo)")
        return chemin
    if hors_ligne:
        raise SystemExit(f"❌ {chemin} absent et --hors-ligne demandé")
    print(f"   ⬇️  téléchargement : {URL_LEXIQUE}")
    urllib.request.urlretrieve(URL_LEXIQUE, chemin)
    print(f"   📁 cache : {chemin.name} ({chemin.stat().st_size / 1e6:.1f} Mo)")
    return chemin


def lire_frequences(chemin):
    """Une fréquence par graphie, sommée sur toutes ses entrées.

    Renvoie aussi le compte des locutions écartées, pour que le journal dise
    ce qui a été retiré plutôt que de le taire.
    """
    frequences = {}
    locutions = 0
    with chemin.open(encoding="utf-8", newline="") as flux:
        for ligne in csv.DictReader(flux, delimiter="\t"):
            graphie = (ligne.get("ortho") or "").strip()
            if not graphie:
                continue
            # Une locution ne se tape pas comme un mot : la complétion par
            # préfixe s'arrêterait au premier espace et le correcteur ne verrait
            # jamais la forme entière.
            if " " in graphie:
                locutions += 1
                continue
            total = 0.0
            for colonne in ("freqfilms2", "freqlivres"):
                try:
                    total += float(ligne.get(colonne) or 0)
                except ValueError:
                    pass
            frequences[graphie] = frequences.get(graphie, 0.0) + total
    return frequences, locutions


def sauvegarder_precedent(chemin):
    if not chemin.exists():
        return
    DOSSIER_BACKUPS.mkdir(parents=True, exist_ok=True)
    horodatage = datetime.now().strftime("%Y%m%d_%H%M%S")
    copie = DOSSIER_BACKUPS / f"{chemin.stem}_{horodatage}{chemin.suffix}"
    copie.write_bytes(chemin.read_bytes())
    print(f"   💾 sauvegarde : {copie.name}")


def main():
    analyseur = argparse.ArgumentParser(
        description="Dictionnaire français du clavier, depuis Lexique 3.83")
    analyseur.add_argument("--strict", action="store_true",
                           help="échouer si la couverture s'effondre")
    analyseur.add_argument("--hors-ligne", action="store_true",
                           help="n'utiliser que le cache local")
    analyseur.add_argument("--sortie", type=Path, default=CHEMIN_SORTIE)
    arguments = analyseur.parse_args()

    print("🇫🇷 DICTIONNAIRE FRANÇAIS — SECONDE RANGÉE DE SUGGESTIONS")
    print("=" * 60)

    print("\n🔎 Source")
    chemin = telecharger_lexique(arguments.hors_ligne)
    frequences, locutions = lire_frequences(chemin)
    print(f"   📖 {len(frequences)} graphies simples, {locutions} locutions écartées")

    if not frequences:
        print("❌ aucune graphie lue — structure du TSV inattendue ?")
        return 1

    # Plancher à 1 : une forme attestée par Lexique mais trop rare pour peser un
    # millionième reste une forme correcte, et c'est le correcteur qui en a
    # besoin. La classer à 0 la ferait passer pour absente.
    mots = sorted(
        ((graphie, max(1, round(valeur))) for graphie, valeur in frequences.items()),
        key=lambda paire: (-paire[1], paire[0]),
    )

    rares = sum(1 for _, f in mots if f == 1)
    print("\n📊 Fréquences (occurrences par million, sous-titres + livres)")
    print(f"   ↑ {mots[0][0]} = {mots[0][1]}")
    print(f"   ↓ {rares} formes au plancher de 1")

    if arguments.strict and len(mots) < SEUIL_STRICT:
        print(f"\n❌ --strict : {len(mots)} formes, moins que le seuil de {SEUIL_STRICT}")
        return 1

    contenu = {
        "version": "lexique383_1.0",
        "language": "fr",
        "type": "simple_dictionary",
        "word_count": len(mots),
        "created": datetime.now().strftime("%Y-%m-%d"),
        "source": SOURCE,
        "licence": LICENCE,
        "attribution": ATTRIBUTION,
        "description": (
            "Formes fléchies du français et leur fréquence par million "
            "(sous-titres + livres). Alimente la seconde rangée de suggestions "
            "et le correcteur orthographique français du clavier."
        ),
        "words": [[graphie, frequence] for graphie, frequence in mots],
    }

    sortie = arguments.sortie
    sauvegarder_precedent(sortie)
    sortie.write_text(
        json.dumps(contenu, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    print(f"\n💾 {sortie.name} — {sortie.stat().st_size / 1024:.0f} Ko")
    print("✅ Terminé")
    return 0


if __name__ == "__main__":
    sys.exit(main())
