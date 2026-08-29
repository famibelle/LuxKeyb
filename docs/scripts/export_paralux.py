#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Écrit les phrases de ParaLux dans un fichier JSON temporaire, pour le banc.

    python docs/scripts/export_paralux.py /tmp/paralux.json

**Le fichier produit ne doit jamais être commité.** ParaLux est un jeu
d'évaluation : rien n'en est redistribué, ni dans le dépôt, ni dans les assets,
ni sur le site. Il sert uniquement à mesurer, et le chemin de sortie est un
argument obligatoire précisément pour qu'il n'atterrisse pas par défaut dans un
répertoire suivi par git.

Pourquoi ParaLux et pas une partition du corpus d'entraînement : une partition
vient des mêmes articles, de la même période et du même style que ce qui reste,
et flatte le modèle. ParaLux partage la source de LuxAlign (RTL.lu) mais aucune
de ses phrases n'y figure — c'est le seul jeu réellement inédit disponible.
Seules les colonnes `anchor` et `paraphrase` sont retenues : `not_paraphrase`
contient des altérations fabriquées exprès pour être fausses.
"""

import json
import sys
from pathlib import Path

from datasets import load_dataset

CHAMPS = ("anchor", "paraphrase")


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit(__doc__.strip().splitlines()[2].strip())

    sortie = Path(sys.argv[1])
    phrases = set()
    jeu = load_dataset("fredxlpy/ParaLux", "default")
    for split in jeu:
        for item in jeu[split]:
            for champ in CHAMPS:
                texte = item.get(champ)
                if isinstance(texte, str) and texte.strip():
                    phrases.add(texte.strip())

    sortie.write_text(json.dumps(sorted(phrases), ensure_ascii=False), "utf-8")
    # 312 phrases distinctes et non 624 : le jeu est bâti sur 156 paires
    # mutuelles, chaque phrase figurant une fois comme ancre et une fois comme
    # paraphrase d'une autre.
    print(f"{len(phrases)} phrases écrites dans {sortie}", file=sys.stderr)


if __name__ == "__main__":
    main()
