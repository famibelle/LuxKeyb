#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Réencode le modèle n-grammes de l'application pour le simulateur web.

Le fichier embarqué dans l'APK pèse 5,1 Mo. C'est sans conséquence sur Android,
où il est lu une fois depuis les assets locaux, mais le simulateur le
retélécharge à chaque visite : autant de mégaoctets sur une connexion mobile,
et autant de JSON à analyser avant que le clavier réponde.

La réduction est **sans perte** : elle ne retire aucun contexte et aucun
candidat, elle change seulement l'encodage.

    {"an der": [{"word": "rue", "probability": 0.08}, ...]}   5,1 Mo
    {"an der": ["rue", ...]}                                  1,4 Mo

Le champ `probability` ne sert au simulateur qu'à trier les candidats par
probabilité décroissante — or le fichier est déjà écrit dans cet ordre par
LuxembourgishComplet.py, et le tri de JavaScript est stable depuis ES2019.
Le retirer ne change donc aucune suggestion. Le simulateur n'affiche de toute
façon que les mots.

Une autre piste a été mesurée puis abandonnée : supprimer les contextes à deux
mots dont la liste est identique à celle de leur repli à un mot, que
resolveNgramContext() retrouverait à l'identique. Ils ne sont que 229 sur
16 249, soit 10 Ko — le gain ne payait pas la complexité.

    python docs/scripts/build_simulateur_ngrams.py

Écrit docs/assets/simulateur-ngrams.json.
"""

import json
import sys
from pathlib import Path

RACINE = Path(__file__).resolve().parents[2]
SOURCE = RACINE / "android_keyboard" / "app" / "src" / "main" / "assets" / "luxemburgish_ngrams.json"
SORTIE = RACINE / "docs" / "assets" / "simulateur-ngrams.json"


def main():
    if not SOURCE.exists():
        sys.exit(f"Modèle introuvable : {SOURCE}\n"
                 f"Lancez d'abord Dictionnaires/LuxembourgishComplet.py --strict.")

    modele = json.loads(SOURCE.read_text("utf-8"))

    allege = {}
    desordonnes = 0
    for contexte, candidats in modele.items():
        probas = [c.get("probability", 0) for c in candidats]
        # Le réencodage ne se permet de perdre les probabilités que parce que
        # l'ordre du fichier les remplace. Si un contexte arrivait désordonné,
        # ce serait faux en silence : on le retrie plutôt que de le supposer.
        if any(probas[i] < probas[i + 1] for i in range(len(probas) - 1)):
            desordonnes += 1
            candidats = sorted(candidats,
                               key=lambda c: c.get("probability", 0),
                               reverse=True)
        allege[contexte] = [c["word"] for c in candidats]

    SORTIE.parent.mkdir(parents=True, exist_ok=True)
    SORTIE.write_text(
        json.dumps(allege, ensure_ascii=False, separators=(",", ":")), "utf-8")

    avant = SOURCE.stat().st_size
    apres = SORTIE.stat().st_size
    print(f"{len(allege)} contextes, "
          f"{sum(len(v) for v in allege.values())} candidats", file=sys.stderr)
    if desordonnes:
        print(f"⚠️  {desordonnes} contextes retriés (source non ordonnée)",
              file=sys.stderr)
    print(f"{avant/1e6:.2f} Mo → {apres/1e6:.2f} Mo "
          f"(−{100*(avant-apres)/avant:.0f} %)", file=sys.stderr)


if __name__ == "__main__":
    main()
