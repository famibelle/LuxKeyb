#!/usr/bin/env python3
"""Liste les formes du dictionnaire qui ne se distinguent que par les accents.

Le dictionnaire est construit à partir de textes créoles d'époques et de
plumes différentes, dont certaines n'accentuent pas. Il en résulte des groupes
de formes identiques une fois les accents retirés. Deux cas s'y mélangent, et
seul un locuteur formé peut les séparer :

  - des paires minimales légitimes, que le créole distingue justement par
    l'accent (« sé » et « sè », « pyé » et « pyè ») : le clavier doit proposer
    les deux ;
  - des graphies non normées héritées du corpus (« bel » à côté de « bèl ») :
    le clavier doit les reconnaître sans jamais les proposer, sans quoi il
    valide la faute dans une classe où l'orthographe est évaluée.

Ce script ne tranche rien : il produit le tableau à annoter. La colonne
« decision » est laissée vide, à remplir par des professeurs de créole.

    python3 Dictionnaires/lister_variantes_accentuees.py

Sortie : Dictionnaires/variantes_accentuees.csv, séparateur point-virgule et
encodage UTF-8 avec BOM, pour s'ouvrir correctement dans un tableur français.
"""

import csv
import json
import unicodedata
from collections import defaultdict
from pathlib import Path

RACINE = Path(__file__).resolve().parents[1]
DICTIONNAIRE = RACINE / "android_keyboard/app/src/main/assets/creole_dict.json"
SORTIE = Path(__file__).resolve().parent / "variantes_accentuees.csv"

COLONNES = [
    "groupe",
    "forme",
    "frequence",
    "part_du_groupe",
    "sans_diacritique",
    "decision",
    "commentaire",
]


def plier(mot: str) -> str:
    """Retire les diacritiques et met en minuscules, pour regrouper les formes."""
    decompose = unicodedata.normalize("NFD", mot.lower())
    return "".join(c for c in decompose if unicodedata.category(c) != "Mn")


def porte_un_accent(mot: str) -> bool:
    return plier(mot) != mot.lower()


def main() -> int:
    entrees = json.loads(DICTIONNAIRE.read_text(encoding="utf-8"))

    groupes: dict[str, list[tuple[str, int]]] = defaultdict(list)
    for mot, frequence in entrees:
        groupes[plier(mot)].append((mot, frequence))

    # Un groupe n'a d'intérêt que s'il contient plusieurs formes, et seulement
    # si l'une d'elles au moins porte un accent : deux graphies sans accent qui
    # se confondent ne relèveraient pas de cette question.
    variantes = {
        cle: formes
        for cle, formes in groupes.items()
        if len(formes) > 1 and any(porte_un_accent(m) for m, _ in formes)
    }

    # Les groupes les plus fréquents d'abord : ce sont ceux dont l'arbitrage
    # change le plus de choses pour l'utilisateur.
    ordonnes = sorted(
        variantes.items(),
        key=lambda item: -sum(f for _, f in item[1]),
    )

    lignes = 0
    with SORTIE.open("w", encoding="utf-8-sig", newline="") as fichier:
        redacteur = csv.writer(fichier, delimiter=";")
        redacteur.writerow(COLONNES)
        for cle, formes in ordonnes:
            total = sum(f for _, f in formes) or 1
            for mot, frequence in sorted(formes, key=lambda x: -x[1]):
                redacteur.writerow([
                    cle,
                    mot,
                    frequence,
                    f"{100 * frequence / total:.0f} %",
                    "" if porte_un_accent(mot) else "oui",
                    "",
                    "",
                ])
                lignes += 1

    print(f"{SORTIE.relative_to(RACINE)}")
    print(f"  {len(ordonnes)} groupes, {lignes} formes à examiner")
    print(f"  sur {len(entrees)} formes au dictionnaire")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
