#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
🇱🇺 WUERTPLAZ — génération des grilles de chassé-croisé
======================================================

Produit `android_keyboard/app/src/main/assets/luxemburgish_chassecroise.json`,
l'actif du septième jeu : une grille vide, la liste des mots à y caser, et
aucune définition. Le joueur déduit des longueurs et des croisements.

    cd Dictionnaires
    python generate_chassecroise.py --strict

Comme `generate_crossword.py`, dont il réutilise le moteur de placement, ce
script ne reconstruit rien : il **consomme** les actifs déjà livrés — le
dictionnaire de fréquences pour les mots, la table des traductions pour les
gloses. Il tourne hors ligne, après `LuxembourgishComplet.py` et
`generate_translations.py`.

Ce que ce jeu fait et qu'aucun des six autres ne fait
-----------------------------------------------------

C'est le **seul jeu jouable sans connaître un mot de luxembourgeois**. Les six
autres supposent une compréhension préalable, ne serait-ce que pour lire la
définition : Kräizwuert demande de produire l'orthographe à partir du sens,
Wuertlück et Wuertriet font choisir, Wuertsich et Wuertmix montrent le mot mais
il faut savoir ce qu'on cherche. Ici les mots sont donnés et la déduction est
géométrique, donc un débutant complet entre par cette porte.

Quatre choix qui décident de la qualité des grilles :

1. **La solution doit être unique**, et c'est l'invariant propre à ce jeu.
   Si deux mots de même longueur peuvent s'échanger sans contredire un
   croisement, la grille a deux solutions et le joueur qui trouve la seconde
   se voit refuser une réponse juste. `solution_unique()` énumère les
   affectations possibles et rejette la grille au deuxième succès. Mesuré sur
   la livraison de Kräizwuert : 285 grilles sur 300 passaient déjà, donc la
   contrainte n'est pas chère — mais elle n'est pas gratuite, et surtout elle
   ne se voit pas à l'œil sur un JSON valide.

2. **La difficulté porte sur la géométrie, pas sur la rareté du vocabulaire.**
   Les mots étant affichés, leur fréquence n'a plus rien à voir avec la
   difficulté du jeu : ce qui la fait, c'est le nombre de mots de même longueur
   (une longueur unique désigne son emplacement toute seule) et la taille de la
   grille. Un seul plancher de fréquence, donc, à `FREQ_MIN` pour les trois
   niveaux — ce qui donne 6 001 mots partout, là où Kräizwuert plafonnait à 237
   en Facile et y saturait son vivier.

3. **La glose est la récompense, pas l'indice.** Elle n'est pas affichée avant
   le placement, seulement quand le mot est verrouillé — donc deux filtres de
   `generate_crossword.py` tombent : une acception qui répète le mot
   (« Budget » → budget) n'est plus un cadeau mais une confirmation honnête, et
   la glose n'a plus besoin d'« apprendre quelque chose ». Reste l'exigence
   d'avoir une glose : 40 % du vivier n'en a aucune (noms propres, `Bettel`,
   `RTL`, `Esch`), et un mot muet romprait la promesse pour toute la grille.

4. **Les accents restent dans la grille**, comme dans Kräizwuert, et le vivier
   reste borné à l'alphabet du pavé. Ici le joueur ne les écrit pas, mais il
   les lit dans la liste et les reconnaît dans la grille : « GRÉNG » sans son
   accent enseignerait la faute que ces jeux existent pour corriger.

Attribution : les gloses viennent du Lëtzebuerger Online Dictionnaire (ZLS,
CC0), les mots et leurs fréquences des corpus LuxAlign (CC BY-NC 4.0) et LETZ
(CC BY 4.0). Voir CORPUS.md. Le jeu affiche ces crédits ; ne pas les retirer.

Fait avec ❤️ pour préserver le Luxembourgeois
"""

import json
import os
import random
import sys
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from LuxembourgishComplet import _classe_de_casse
from generate_crossword import (
    ALPHABET,
    ESSAIS_PAR_GRILLE,
    LONGUEUR_MAX,
    LONGUEUR_MAX_ACCEPTION,
    LONGUEUR_MIN,
    LONGUEUR_MIN_MAJUSCULE,
    LONGUEUR_MIN_MINUSCULE,
    MOTS_MIN_LIVRABLE,
    ACCEPTIONS_MAX,
    _cases,
    _construire_une_grille,
    _densite,
    _dun_seul_tenant,
    _suites,
    charger_actifs,
)

if sys.platform.startswith('win'):
    import codecs
    sys.stdout = codecs.getwriter('utf-8')(sys.stdout.buffer, 'strict')
    sys.stderr = codecs.getwriter('utf-8')(sys.stderr.buffer, 'strict')

RACINE_ASSETS = Path(__file__).resolve().parent.parent / \
    "android_keyboard/app/src/main/assets"
CHEMIN_GRILLES = RACINE_ASSETS / "luxemburgish_chassecroise.json"
DOSSIER_BACKUPS = Path(__file__).resolve().parent / "backups"

# Graine fixe, pour la même raison que dans les autres générateurs : deux
# exécutions sur les mêmes actifs doivent produire le même fichier.
GRAINE = 20260907

# Plancher de fréquence unique. Voir le point 2 de l'en-tête : la difficulté ne
# passe plus par la rareté, donc rien ne justifie trois planchers. Celui-ci est
# celui du niveau Difficile de Kräizwuert, le plus bas des trois, et il laisse
# 6 001 mots glosés dans le vivier.
FREQ_MIN = 20

# Les trois niveaux, gradués sur la seule géométrie.
#
# `collisions` est la part visée de mots qui partagent leur longueur avec un
# autre mot de la même grille. C'est *la* mesure de difficulté d'un
# chassé-croisé : un mot dont la longueur est unique se pose sans réfléchir, et
# une grille dont toutes les longueurs sont uniques se remplit sans déduction.
# La valeur n'est pas une borne mais une cible — parmi les grilles candidates
# qui passent l'unicité, on garde la plus proche. Une borne dure aurait fait
# échouer des niveaux entiers sans rien apporter.
NIVEAUX = {
    1: {"nom": "Facile", "taille": 9, "mots": 7, "collisions": 0.25},
    2: {"nom": "Normal", "taille": 10, "mots": 10, "collisions": 0.55},
    3: {"nom": "Difficile", "taille": 11, "mots": 13, "collisions": 0.85},
}

GRILLES_PAR_NIVEAU = 100

# Plus haut que le plafond de Kräizwuert (4) : le vivier étant six fois plus
# grand à tous les niveaux, la répétition n'est plus le risque. Reste utile
# comme garde-fou contre les mots aux lettres commodes.
MAX_GRILLES_PAR_MOT = 3

# Nombre de solutions au-delà duquel on cesse de chercher : deux suffisent à
# rejeter la grille, et énumérer les suivantes ne dirait rien de plus.
SOLUTIONS_MAX = 2


def recompense_de(mot, glose):
    """La glose montrée quand le mot est verrouillé, ou None s'il n'y en a pas.

    Deux différences avec `definition_de()` de `generate_crossword.py`, et elles
    tiennent toutes deux à ce que la glose arrive **après** le placement :

    - Une acception qui répète le mot est gardée. « Budget » → budget ne livre
      rien puisque le mot est déjà posé ; c'est une confirmation, et le
      luxembourgeois empruntant beaucoup au français, la refuser coûterait
      1 445 formes pour rien.
    - Rien n'exige que la glose « apprenne quelque chose » : ce filtre existe
      pour les jeux qui font *chercher* le mot, pas pour celui qui le donne.

    Ce qui reste : les locutions trop longues débordent de l'écran d'un
    téléphone, et on les laisse tomber s'il reste autre chose.
    """
    retenues = [a.strip() for a in glose.split(",") if a.strip()]
    if not retenues:
        return None
    courtes = [a for a in retenues if len(a) <= LONGUEUR_MAX_ACCEPTION]
    if courtes:
        retenues = courtes
    return ", ".join(retenues[:ACCEPTIONS_MAX])


def construire_vivier(dico, table):
    """Les mots casables, en un seul vivier.

    Mêmes exigences que Kräizwuert — une forme du dictionnaire de fréquences,
    écrite dans l'alphabet du pavé, ni acronyme ni mot grammatical — moins
    l'exigence que la glose ne contienne pas le mot, plus un plancher de
    fréquence unique.

    Les noms propres n'ont toujours pas besoin d'être détectés : le LOD ne glose
    ni « Bettel », ni « RTL », ni « Esch », et l'exigence de glose les écarte.
    """
    print("\n🔤 CONSTRUCTION DU VIVIER")
    print("-" * 45)

    rejets = Counter()
    vivier = []

    for mot, freq in dico.items():
        if freq < FREQ_MIN:
            rejets["trop rare"] += 1
            continue
        if not (LONGUEUR_MIN <= len(mot) <= LONGUEUR_MAX):
            rejets["longueur"] += 1
            continue

        classe = _classe_de_casse(mot)
        if classe == "ACRONYME":
            rejets["acronyme"] += 1
            continue
        if classe == "MAJUSCULE":
            if len(mot) < LONGUEUR_MIN_MAJUSCULE:
                rejets["longueur"] += 1
                continue
        elif len(mot) < LONGUEUR_MIN_MINUSCULE:
            rejets["mot grammatical"] += 1
            continue

        majuscule = mot.upper()
        if any(c not in ALPHABET for c in majuscule):
            rejets["hors alphabet"] += 1
            continue

        glose = table.get(mot) or table.get(mot.lower())
        if not glose:
            rejets["sans glose"] += 1
            continue

        recompense = recompense_de(mot, glose)
        if recompense is None:
            rejets["glose vide"] += 1
            continue

        vivier.append({
            "m": majuscule,
            "f": mot,
            "g": recompense,
            "freq": freq,
        })

    for motif, nombre in rejets.most_common():
        print(f"   ↩️  {nombre:>6} formes écartées — {motif}")
    print(f"   ✅ {len(vivier)} mots casables (fréquence ≥ {FREQ_MIN})")

    longueurs = Counter(len(e["m"]) for e in vivier)
    detail = " · ".join(f"{n}:{longueurs[n]}" for n in sorted(longueurs))
    print(f"   📏 par longueur — {detail}")
    return vivier


def solution_unique(grille):
    """La grille se résout-elle d'une seule façon ?

    L'invariant de ce fichier. Un chassé-croisé ne donne pas de définitions :
    la seule chose qui désigne l'emplacement d'un mot est sa longueur et les
    lettres que ses croisements lui imposent. Si deux mots peuvent s'échanger
    en respectant tout cela, la grille a deux solutions — et comme le jeu ne
    peut en connaître qu'une, il refuse une réponse juste, ce qu'aucun joueur
    ne pardonne.

    L'énumération est un simple retour en arrière sur une bijection mots ↔
    emplacements. Elle tient parce qu'une grille compte au plus treize mots et
    que l'ordre d'essai attaque d'abord les emplacements les plus contraints :
    ceux dont peu de mots partagent la longueur, puis les plus croisés.
    """
    mots = grille["mots"]
    n = len(mots)
    lettres = [m["m"] for m in mots]
    cases = [_cases(m["m"], m["r"], m["c"], m["d"] == "H") for m in mots]

    # Pour chaque emplacement, les croisements : (rang dans ce mot, autre
    # emplacement, rang dans l'autre mot).
    occupants = defaultdict(list)
    for i, suite in enumerate(cases):
        for rang, case in enumerate(suite):
            occupants[case].append((i, rang))
    croisements = defaultdict(list)
    for occupation in occupants.values():
        if len(occupation) == 2:
            (a, i), (b, j) = occupation
            croisements[a].append((i, b, j))
            croisements[b].append((j, a, i))

    par_longueur = Counter(len(mot) for mot in lettres)
    ordre = sorted(
        range(n),
        key=lambda i: (par_longueur[len(lettres[i])], -len(croisements[i]))
    )

    affecte = [None] * n
    pris = [False] * n
    total = 0

    def poser(rang):
        nonlocal total
        if total >= SOLUTIONS_MAX:
            return
        if rang == n:
            total += 1
            return
        emplacement = ordre[rang]
        attendue = len(lettres[emplacement])
        for candidat, texte in enumerate(lettres):
            if pris[candidat] or len(texte) != attendue:
                continue
            if any(
                affecte[autre] is not None and lettres[affecte[autre]][j] != texte[i]
                for i, autre, j in croisements[emplacement]
            ):
                continue
            affecte[emplacement] = candidat
            pris[candidat] = True
            poser(rang + 1)
            affecte[emplacement] = None
            pris[candidat] = False
            if total >= SOLUTIONS_MAX:
                return

    poser(0)
    return total == 1


def taux_de_collision(grille):
    """Part des mots qui partagent leur longueur avec un autre de la grille.

    Zéro veut dire que chaque longueur désigne son emplacement toute seule et
    que la grille se remplit sans déduction ; un veut dire qu'aucun mot ne se
    place sans regarder ses croisements.
    """
    mots = grille["mots"]
    if not mots:
        return 0.0
    longueurs = Counter(len(m["m"]) for m in mots)
    partages = sum(1 for m in mots if longueurs[len(m["m"])] > 1)
    return partages / len(mots)


def construire_grilles(vivier, rng):
    """Construit la livraison, niveau par niveau.

    Pour chaque grille : plusieurs candidates, on écarte celles à solutions
    multiples, et parmi les survivantes on garde celle dont le taux de
    collision approche le mieux la cible du niveau. À taux égal, la plus dense
    l'emporte — une grille très croisée se déduit mieux qu'une étoile.
    """
    print("\n🧩 CONSTRUCTION DES GRILLES")
    print("-" * 45)

    grilles = []
    for niveau, base in NIVEAUX.items():
        reglage = dict(base, niveau=niveau)
        usages = Counter()
        produites = 0
        rejets_unicite = 0
        collisions = []

        for _ in range(GRILLES_PAR_NIVEAU):
            meilleure = None
            meilleur_score = None
            for _ in range(ESSAIS_PAR_GRILLE):
                candidate = _construire_une_grille(
                    _disponibles(vivier, usages), usages, rng, reglage)
                if candidate is None:
                    continue
                if not solution_unique(candidate):
                    rejets_unicite += 1
                    continue
                ecart = abs(taux_de_collision(candidate) - reglage["collisions"])
                score = (-ecart, _densite(candidate))
                if meilleur_score is None or score > meilleur_score:
                    meilleure, meilleur_score = candidate, score
            if meilleure is None:
                continue
            for mot in meilleure["mots"]:
                usages[mot["m"]] += 1
            grilles.append(meilleure)
            collisions.append(taux_de_collision(meilleure))
            produites += 1

        moyenne_mots = 0
        moyenne_collision = 0
        if produites:
            recentes = grilles[-produites:]
            moyenne_mots = sum(len(g["mots"]) for g in recentes) / produites
            moyenne_collision = sum(collisions) / produites
        print(f"   ✅ {reglage['nom']:<10} {produites:>4} grilles, "
              f"{moyenne_mots:.1f} mots, collisions {moyenne_collision:.0%} "
              f"(cible {reglage['collisions']:.0%}), "
              f"{len(usages)} formes employées, "
              f"{rejets_unicite} candidates non uniques écartées")

    return grilles


def _disponibles(vivier, usages):
    """Le vivier amputé des mots déjà trop vus.

    `_construire_une_grille()` applique son propre plafond, celui de
    Kräizwuert ; on lui passe donc une liste déjà filtrée par le nôtre, qui est
    plus serré parce que le vivier est six fois plus grand.
    """
    return [e for e in vivier if usages[e["m"]] < MAX_GRILLES_PAR_MOT]


def valider(grilles):
    """Contrôles qui doivent tenir avant d'écrire quoi que ce soit.

    Ceux de `generate_crossword.valider()`, moins celui sur la définition qui
    ne s'applique plus, plus l'unicité de la solution.
    """
    print("\n🔎 VALIDATION")
    print("-" * 45)

    erreurs = []
    for index, grille in enumerate(grilles):
        mots = grille["mots"]
        if len(mots) < MOTS_MIN_LIVRABLE:
            erreurs.append(f"#{index} : {len(mots)} mots seulement")

        cases = {}
        conflit = False
        for mot in mots:
            if len(mot["m"]) < LONGUEUR_MIN:
                erreurs.append(f"#{index} : « {mot['m']} » trop court")
            if any(c not in ALPHABET for c in mot["m"]):
                erreurs.append(f"#{index} : « {mot['m']} » hors alphabet")
            if not mot["g"].strip():
                erreurs.append(f"#{index} : « {mot['m']} » sans récompense")
            if mot["m"] != mot["f"].upper():
                erreurs.append(
                    f"#{index} : « {mot['m']} » ne majuscule pas « {mot['f']} »")
            for (r, c), lettre in zip(
                    _cases(mot["m"], mot["r"], mot["c"], mot["d"] == "H"), mot["m"]):
                if not (0 <= r < grille["h"] and 0 <= c < grille["w"]):
                    erreurs.append(f"#{index} : « {mot['m']} » déborde de la grille")
                    conflit = True
                    break
                if cases.setdefault((r, c), lettre) != lettre:
                    erreurs.append(f"#{index} : croisement contradictoire en ({r},{c})")
                    conflit = True

        if len(set(m["m"] for m in mots)) != len(mots):
            erreurs.append(f"#{index} : un mot est posé deux fois")

        # L'invariant hérité : rien ne se lit dans la grille qui ne soit un mot
        # posé. Il compte double ici — le joueur qui lit une suite non voulue
        # la cherche dans sa liste et ne l'y trouve pas.
        if not conflit:
            posees = Counter(m["m"] for m in mots)
            lues = Counter(_suites(grille))
            if posees != lues:
                intruses = [s for s in lues if s not in posees]
                erreurs.append(
                    f"#{index} : suites de lettres non voulues {intruses[:4]}")

        if mots and not _dun_seul_tenant(mots):
            erreurs.append(f"#{index} : la grille est en plusieurs morceaux")

        # L'invariant propre à ce jeu.
        if not conflit and not solution_unique(grille):
            erreurs.append(f"#{index} : la grille admet plusieurs solutions")

    if erreurs:
        for erreur in erreurs[:20]:
            print(f"   ❌ {erreur}")
        print(f"   ❌ {len(erreurs)} erreurs au total")
        return False

    total_mots = sum(len(g["mots"]) for g in grilles)
    formes = len({m["m"] for g in grilles for m in g["mots"]})
    moyenne = sum(taux_de_collision(g) for g in grilles) / len(grilles)
    print(f"   ✅ {len(grilles)} grilles valides, {total_mots} mots posés, "
          f"{formes} formes distinctes")
    print(f"   ✅ solution unique vérifiée sur les {len(grilles)} grilles, "
          f"collisions moyennes {moyenne:.0%}")
    return True


def sauvegarder(grilles, attribution_lod):
    """Écrit l'actif, après copie horodatée de la version précédente."""
    print("\n💾 ÉCRITURE DE L'ACTIF")
    print("-" * 45)

    if CHEMIN_GRILLES.exists():
        DOSSIER_BACKUPS.mkdir(exist_ok=True)
        horodatage = datetime.now().strftime("%Y%m%d_%H%M%S")
        copie = DOSSIER_BACKUPS / f"luxemburgish_chassecroise_{horodatage}.json"
        copie.write_bytes(CHEMIN_GRILLES.read_bytes())
        print(f"   🗄️  sauvegarde : {copie.name}")

    charge = {
        "version": 1,
        "generated": datetime.now().strftime("%Y-%m-%d"),
        # Les crédits voyagent dans l'actif, comme pour les autres jeux : les
        # gloses sont celles du LOD, les mots et leurs fréquences viennent des
        # deux corpus sous Creative Commons.
        "attribution": list(attribution_lod) + [
            "fredxlpy/LuxAlign — CC BY-NC 4.0 — Philippy, Guo, Klein, Bissyandé (COLING 2025)",
            "fredxlpy/LETZ — CC BY 4.0 — Philippy, Haddadan, Guo (SIGUL 2024)",
        ],
        "grilles": grilles,
    }
    with open(CHEMIN_GRILLES, "w", encoding="utf-8") as f:
        json.dump(charge, f, ensure_ascii=False, separators=(",", ":"))

    taille = CHEMIN_GRILLES.stat().st_size
    print(f"   ✅ {CHEMIN_GRILLES.name} — {len(grilles)} grilles, "
          f"{taille / 1024:.0f} Ko")


def main():
    strict = "--strict" in sys.argv

    print("🇱🇺 WUERTPLAZ — GÉNÉRATION DES GRILLES DE CHASSÉ-CROISÉ 🇱🇺")
    print("=" * 70)
    print(f"Date: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 70)

    rng = random.Random(GRAINE)

    try:
        dico, table, attribution = charger_actifs()
    except Exception as e:
        print(f"\n❌ Actifs illisibles : {e}")
        print("   Lancez d'abord `python LuxembourgishComplet.py --strict` "
              "puis `python generate_translations.py --strict`.")
        return 1

    vivier = construire_vivier(dico, table)
    if len(vivier) < 1000:
        print("\n❌ Vivier trop maigre pour construire des grilles.")
        return 1

    grilles = construire_grilles(vivier, rng)
    if not grilles:
        print("\n❌ Aucune grille produite, rien n'est écrit.")
        return 1

    if not valider(grilles):
        print("\n❌ Validation échouée, rien n'est écrit.")
        return 1

    attendu = GRILLES_PAR_NIVEAU * len(NIVEAUX)
    if strict and len(grilles) < attendu * 0.9:
        print(f"\n❌ Mode strict : {len(grilles)} grilles seulement, "
              f"attendu ~{attendu}.")
        return 1

    sauvegarder(grilles, attribution)
    print("\n🎉 TERMINÉ")
    return 0


if __name__ == "__main__":
    sys.exit(main())
