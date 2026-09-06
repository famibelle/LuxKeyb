#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
🇱🇺 KRÄIZWUERT — génération des grilles de mots croisés
======================================================

Produit `android_keyboard/app/src/main/assets/luxemburgish_crossword.json`,
l'actif du sixième jeu : une grille numérotée, la liste des définitions en
français, et au joueur d'écrire les mots luxembourgeois lettre par lettre.

    cd Dictionnaires
    python generate_crossword.py --strict

Comme `generate_cloze.py`, ce script ne reconstruit rien : il **consomme** les
actifs déjà livrés — le dictionnaire de fréquences pour les mots, la table des
traductions pour les définitions. Il faut donc l'exécuter APRÈS
`LuxembourgishComplet.py` et `generate_translations.py`. À la différence de
`generate_cloze.py`, il ne touche pas aux corpus Hugging Face : tout ce dont il
a besoin tient dans les assets, et il tourne en quelques secondes hors ligne.

C'est le seul jeu où le joueur **produit** l'orthographe au lieu de la
reconnaître : Wuertsich fait chercher un mot déjà écrit, Wuertmix redonne ses
lettres, Wuertriet et Wuertlück corrigent un choix. Ici rien n'est donné, et
c'est le but — les accents et la majuscule des substantifs s'apprennent en les
écrivant.

Quatre choix qui décident de la qualité des grilles :

1. **Les grilles sont construites ici, jamais sur l'appareil.** Un placement
   croisé est une recherche avec retours en arrière ; la faire à l'ouverture du
   jeu coûterait une attente visible et, surtout, aucune garde ne pourrait
   vérifier le résultat avant livraison. Construites ici, les grilles sont
   validées une fois pour toutes — voir `valider()` — et `CrosswordAssetTest`
   rejoue la même vérification sur l'actif livré.

2. **Toute suite de deux lettres ou plus est un mot posé.** C'est l'invariant
   qui sépare une grille d'un tas de lettres : deux mots parallèles collés
   fabriquent des colonnes que personne n'a écrites, et le joueur qui les lit
   croit à une faute. `_placement_valide()` l'obtient en exigeant que les
   cases voisines perpendiculaires d'une case non croisée soient vides, et que
   les deux cases qui prolongent le mot le soient aussi.

3. **Les accents restent dans la grille.** Écrire GRENG pour « gréng »
   enseignerait la faute que le jeu existe pour corriger. Le pavé de saisie
   porte donc Ä Ë É Ö Ü, et le vivier est restreint aux mots qui s'écrivent
   avec ces seules lettres — un « Ministère » demanderait une touche de plus
   pour un emprunt qui n'apprend rien.

4. **La définition ne contient jamais sa réponse.** Le luxembourgeois emprunte
   au français, si bien qu'une acception glose parfois le mot par lui-même
   (« Maison » → « maison médicale de garde »). Ces acceptions-là sont retirées
   de la définition ; s'il n'en reste aucune, le mot sort du vivier.

Attribution : les définitions viennent du Lëtzebuerger Online Dictionnaire
(ZLS, CC0), les mots et leurs fréquences des corpus LuxAlign (CC BY-NC 4.0) et
LETZ (CC BY 4.0). Voir CORPUS.md. Le jeu affiche ces crédits ; ne pas les
retirer.

Fait avec ❤️ pour préserver le Luxembourgeois
"""

import json
import os
import random
import re
import sys
from collections import Counter
from datetime import datetime
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from LuxembourgishComplet import _classe_de_casse

if sys.platform.startswith('win'):
    import codecs
    sys.stdout = codecs.getwriter('utf-8')(sys.stdout.buffer, 'strict')
    sys.stderr = codecs.getwriter('utf-8')(sys.stderr.buffer, 'strict')

RACINE_ASSETS = Path(__file__).resolve().parent.parent / \
    "android_keyboard/app/src/main/assets"
CHEMIN_DICT = RACINE_ASSETS / "luxemburgish_dict.json"
CHEMIN_TRADUCTIONS = RACINE_ASSETS / "luxemburgish_translations.json"
CHEMIN_GRILLES = RACINE_ASSETS / "luxemburgish_crossword.json"
DOSSIER_BACKUPS = Path(__file__).resolve().parent / "backups"

# Graine fixe, pour la même raison que dans generate_cloze.py : deux exécutions
# sur les mêmes actifs doivent produire le même fichier.
GRAINE = 20260906

# Alphabet de la grille, c'est-à-dire aussi celui du pavé de saisie. Les vingt-
# six lettres plus les cinq voyelles infléchies du luxembourgeois. Un mot qui
# demanderait autre chose (« Ministère », « Français ») sort du vivier : ajouter
# une touche par emprunt allongerait le pavé sans rien apprendre.
ALPHABET = set("ABCDEFGHIJKLMNOPQRSTUVWXYZÄËÉÖÜ")

# Repli accents + casse, identique à AccentTolerantMatcher.normalize côté
# Kotlin. Sert à comparer un mot à sa glose, jamais à écrire dans la grille.
_PLIAGE = {}
for _source, _cible in (("àáâäãåāăą", "a"), ("èéêëēėęě", "e"), ("ìíîïīįĩ", "i"),
                        ("òóôöõøōőœ", "o"), ("ùúûüūůũűų", "u"), ("ýÿŷ", "y"),
                        ("ç", "c"), ("ñ", "n")):
    for _c in _source:
        _PLIAGE[_c] = _cible

# Longueur des mots. En deçà de 3 lettres il n'y a pas de mot à écrire ; au-delà
# de 11 le mot ne tient plus dans la plus grande grille, et le luxembourgeois
# compose sans limite (« wäissbandkräizschniewel »).
LONGUEUR_MIN, LONGUEUR_MAX = 3, 11

# Un substantif de 3 lettres reste un mot à part entière ; un mot minuscule de
# moins de 5 lettres est presque toujours grammatical (« ass », « ginn »,
# « och »), et sa glose ne définit rien — « de » se glose « tu, agence de
# voyage ». Même seuil que generate_cloze.py, pour la même raison.
LONGUEUR_MIN_MAJUSCULE = 3
LONGUEUR_MIN_MINUSCULE = 5

# Une acception plus longue que cela est une locution entière : elle déborde de
# la liste des définitions sur un téléphone. On la laisse tomber si le mot en a
# d'autres.
LONGUEUR_MAX_ACCEPTION = 42
ACCEPTIONS_MAX = 3

# Les trois niveaux : un plancher de fréquence, une taille de grille et un
# nombre de mots visé. La difficulté porte sur la rareté du vocabulaire, pas
# sur la taille de la grille — celle-ci suit seulement le nombre de mots.
NIVEAUX = {
    1: {"nom": "Facile", "freq_min": 800, "taille": 9, "mots": 8},
    2: {"nom": "Normal", "freq_min": 150, "taille": 10, "mots": 10},
    3: {"nom": "Difficile", "freq_min": 20, "taille": 11, "mots": 12},
}

# Grilles livrées par niveau. Une partie en consomme une : cent grilles font
# cent parties avant de revoir la première.
GRILLES_PAR_NIVEAU = 100

# Combien de grilles au maximum peuvent partager le même mot. Sans plafond, les
# mots aux lettres commodes (« Joer », « Land », « Leit ») reviennent dans une
# grille sur deux et le jeu paraît tourner en rond.
MAX_GRILLES_PAR_MOT = 4

# Nombre de tirages tentés pour poser un mot de plus, et nombre de grilles
# construites puis départagées pour n'en garder qu'une. Le placement est
# glouton : reconstruire plusieurs fois et garder la plus dense coûte moins que
# d'écrire un vrai retour en arrière, pour un résultat équivalent à cette
# échelle.
TENTATIVES_PAR_MOT = 400
ESSAIS_PAR_GRILLE = 12

# Une grille en dessous de ce nombre de mots n'est pas livrée : elle se remplit
# en une minute et donne l'impression d'un jeu vide.
MOTS_MIN_LIVRABLE = 6

# Deux mots d'une même grille ne doivent pas partager ce préfixe : « Joer » et
# « Joren » côte à côte ne sont pas deux définitions, c'est un piège.
PREFIXE_COMMUN = 4


def plier(texte):
    """Replie casse et accents, comme AccentTolerantMatcher.normalize."""
    return "".join(_PLIAGE.get(c, c) for c in texte.lower())


def charger_actifs():
    """Lit le dictionnaire de fréquences et la table des traductions."""
    print("\n📚 LECTURE DES ACTIFS LIVRÉS")
    print("-" * 45)

    with open(CHEMIN_DICT, "r", encoding="utf-8") as f:
        brut = json.load(f)
    if not isinstance(brut, list):
        raise ValueError(f"{CHEMIN_DICT.name} doit être un tableau de paires")
    dico = {paire[0]: paire[1] for paire in brut}

    with open(CHEMIN_TRADUCTIONS, "r", encoding="utf-8") as f:
        traductions = json.load(f)

    table = traductions["translations"]
    print(f"   ✅ {len(dico)} entrées de dictionnaire")
    print(f"   ✅ {len(table)} formes glosées")
    return dico, table, traductions.get("attribution", [])


def definition_de(mot, glose):
    """La définition montrée au joueur, ou None si elle livrerait la réponse.

    Une acception qui contient le mot cherché n'est pas une définition : elle
    donne la réponse. Le luxembourgeois ayant emprunté « Maison », « Budget »,
    « Accident » au français, le cas est fréquent — et c'est exactement le
    vocabulaire qu'un francophone reconnaît en premier.
    """
    motif = re.compile(r"\b" + re.escape(plier(mot)) + r"\b")
    retenues = []
    for acception in glose.split(","):
        acception = acception.strip()
        if not acception:
            continue
        if motif.search(plier(acception)):
            continue
        retenues.append(acception)

    if not retenues:
        return None
    # Les locutions ne sont écartées que s'il reste autre chose : mieux vaut une
    # définition longue que pas de définition.
    courtes = [a for a in retenues if len(a) <= LONGUEUR_MAX_ACCEPTION]
    if courtes:
        retenues = courtes
    return ", ".join(retenues[:ACCEPTIONS_MAX])


def construire_vivier(dico, table):
    """Les mots jouables, par niveau de difficulté.

    Le filtre est le même que celui des autres jeux — une forme du dictionnaire
    de fréquences, glosée par une glose qui apprend quelque chose — plus deux
    exigences propres à la grille : elle s'écrit dans l'alphabet du pavé, et sa
    définition ne la contient pas.

    Les noms propres n'ont pas besoin d'être détectés : le LOD ne glose ni
    « Bettel », ni « RTL », ni « Esch », donc ils ne franchissent pas la
    première condition. C'est ce qui permet à ce script de se passer du corpus.
    """
    print("\n🔤 CONSTRUCTION DU VIVIER")
    print("-" * 45)

    rejets = Counter()
    vivier = []

    for mot, freq in dico.items():
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

        definition = definition_de(mot, glose)
        if definition is None:
            rejets["glose = le mot"] += 1
            continue

        vivier.append({
            "m": majuscule,
            "f": mot,
            "g": definition,
            "freq": freq,
        })

    for motif, nombre in rejets.most_common():
        print(f"   ↩️  {nombre:>6} formes écartées — {motif}")

    par_niveau = {}
    for niveau, reglage in NIVEAUX.items():
        mots = [e for e in vivier if e["freq"] >= reglage["freq_min"]]
        par_niveau[niveau] = mots
        print(f"   ✅ {reglage['nom']:<10} {len(mots):>6} mots "
              f"(fréquence ≥ {reglage['freq_min']})")
    return par_niveau


def _cases(mot, ligne, colonne, horizontal):
    if horizontal:
        return [(ligne, colonne + i) for i in range(len(mot))]
    return [(ligne + i, colonne) for i in range(len(mot))]


def _placement_valide(grille, sens, mot, ligne, colonne, horizontal, taille):
    """Le mot peut-il être posé là, et combien de fois croise-t-il ?

    Quatre conditions, et elles tiennent ensemble l'invariant du fichier : toute
    suite de deux lettres ou plus, en ligne comme en colonne, est un mot posé.

    - Les deux cases qui prolongeraient le mot sont vides ou hors grille, sinon
      on allonge une suite existante en un mot que personne n'a écrit.
    - Une case déjà occupée doit porter la même lettre : c'est un croisement.
    - Un croisement est **perpendiculaire**. Sans cela « ENGEM » se pose sur les
      cinq dernières lettres de « SENGEM » : chaque lettre coïncide, les deux
      bouts sont libres, et la grille ne montre pourtant qu'un mot des deux.
    - Une case libre ne doit avoir aucun voisin perpendiculaire occupé, sinon
      on colle deux mots parallèles et on fabrique des paires de lettres dans
      l'autre sens.

    Retourne le nombre de croisements, ou None si le placement est refusé.
    """
    cases = _cases(mot, ligne, colonne, horizontal)
    for r, c in cases:
        if not (0 <= r < taille and 0 <= c < taille):
            return None

    avant = (ligne, colonne - 1) if horizontal else (ligne - 1, colonne)
    apres = (ligne, colonne + len(mot)) if horizontal else (ligne + len(mot), colonne)
    if grille.get(avant) or grille.get(apres):
        return None

    direction = "H" if horizontal else "V"
    croisements = 0
    for (r, c), lettre in zip(cases, mot):
        occupant = grille.get((r, c))
        if occupant is not None:
            if occupant != lettre:
                return None
            if direction in sens.get((r, c), ()):
                return None
            croisements += 1
            continue
        voisins = [(r - 1, c), (r + 1, c)] if horizontal else [(r, c - 1), (r, c + 1)]
        if any(grille.get(v) for v in voisins):
            return None
    return croisements


def _construire_une_grille(vivier, usages, rng, reglage):
    """Pose des mots un à un, chacun croisant un mot déjà posé.

    Le premier mot est posé au milieu, horizontalement. Les suivants sont tirés
    au sort et essayés à tous leurs croisements possibles, dans un ordre
    lui-même mélangé : sans cela, la grille se déploie toujours dans le même
    coin et les cent grilles d'un niveau se ressemblent.
    """
    taille = reglage["taille"]
    cible = reglage["mots"]

    disponibles = [e for e in vivier if usages[e["m"]] < MAX_GRILLES_PAR_MOT]
    if len(disponibles) < cible * 4:
        return None

    # Le mot d'amorce est long : il offre le plus de croisements possibles, donc
    # la grille se construit autour de lui au lieu de s'étirer en escalier.
    longs = [e for e in disponibles if len(e["m"]) >= min(7, LONGUEUR_MAX)]
    if not longs:
        return None
    amorce = rng.choice(longs)

    grille = {}
    # Sens des mots qui passent par chaque case : une case ne peut porter qu'un
    # mot horizontal et un mot vertical, jamais deux du même sens.
    sens = {}
    poses = []

    def poser(entree, ligne, colonne, horizontal):
        direction = "H" if horizontal else "V"
        for (r, c), lettre in zip(
                _cases(entree["m"], ligne, colonne, horizontal), entree["m"]):
            grille[(r, c)] = lettre
            sens.setdefault((r, c), set()).add(direction)
        poses.append({
            "r": ligne, "c": colonne,
            "d": "H" if horizontal else "V",
            "m": entree["m"], "f": entree["f"], "g": entree["g"],
        })

    depart = (taille - len(amorce["m"])) // 2
    poser(amorce, taille // 2, depart, True)

    prefixes = {plier(amorce["m"])[:PREFIXE_COMMUN]}
    utilises = {amorce["m"]}

    for _ in range(TENTATIVES_PAR_MOT):
        if len(poses) >= cible:
            break
        entree = rng.choice(disponibles)
        mot = entree["m"]
        if mot in utilises:
            continue
        prefixe = plier(mot)[:PREFIXE_COMMUN]
        if prefixe in prefixes:
            continue

        # Un croisement se cherche depuis les lettres déjà écrites : pour chaque
        # case posée qui porte une lettre du candidat, on essaie de faire passer
        # le candidat par là, dans le sens perpendiculaire au mot qui l'occupe.
        candidats = []
        for (r, c), lettre in grille.items():
            for i, ch in enumerate(mot):
                if ch != lettre:
                    continue
                for horizontal in (True, False):
                    ligne = r if horizontal else r - i
                    colonne = c - i if horizontal else c
                    candidats.append((ligne, colonne, horizontal))
        rng.shuffle(candidats)

        for ligne, colonne, horizontal in candidats:
            croisements = _placement_valide(
                grille, sens, mot, ligne, colonne, horizontal, taille)
            if croisements:
                poser(entree, ligne, colonne, horizontal)
                utilises.add(mot)
                prefixes.add(prefixe)
                break

    if len(poses) < MOTS_MIN_LIVRABLE:
        return None

    # Recadrage sur les cases réellement écrites : une grille posée au milieu
    # d'un canevas de 11 laisse deux colonnes vides de chaque côté, et l'écran
    # d'un téléphone ne peut pas se le permettre.
    lignes = [r for r, _ in grille]
    colonnes = [c for _, c in grille]
    haut, gauche = min(lignes), min(colonnes)
    for mot in poses:
        mot["r"] -= haut
        mot["c"] -= gauche

    return {
        "l": reglage["niveau"],
        "w": max(colonnes) - gauche + 1,
        "h": max(lignes) - haut + 1,
        "mots": poses,
    }


def _densite(grille):
    """Ce qui départage deux grilles : d'abord le nombre de mots, puis les
    croisements. Une grille très croisée se corrige toute seule — une lettre
    trouvée en aide une autre — et c'est ce qui distingue des mots croisés
    d'une liste de définitions."""
    cases = sum(len(m["m"]) for m in grille["mots"])
    distinctes = len({
        (r, c)
        for m in grille["mots"]
        for r, c in _cases(m["m"], m["r"], m["c"], m["d"] == "H")
    })
    return (len(grille["mots"]), cases - distinctes)


def construire_grilles(par_niveau, rng):
    """Construit la livraison, niveau par niveau."""
    print("\n🧩 CONSTRUCTION DES GRILLES")
    print("-" * 45)

    grilles = []
    for niveau, reglage in NIVEAUX.items():
        reglage = dict(reglage, niveau=niveau)
        vivier = par_niveau[niveau]
        usages = Counter()
        produites = 0

        for _ in range(GRILLES_PAR_NIVEAU):
            meilleure = None
            for _ in range(ESSAIS_PAR_GRILLE):
                candidate = _construire_une_grille(vivier, usages, rng, reglage)
                if candidate is None:
                    continue
                if meilleure is None or _densite(candidate) > _densite(meilleure):
                    meilleure = candidate
            if meilleure is None:
                continue
            for mot in meilleure["mots"]:
                usages[mot["m"]] += 1
            grilles.append(meilleure)
            produites += 1

        moyenne = 0
        if produites:
            recentes = grilles[-produites:]
            moyenne = sum(len(g["mots"]) for g in recentes) / produites
        print(f"   ✅ {reglage['nom']:<10} {produites:>4} grilles, "
              f"{moyenne:.1f} mots en moyenne, "
              f"{len(usages)} formes distinctes employées")

    return grilles


def _suites(grille):
    """Toutes les suites de deux lettres ou plus, en ligne et en colonne.

    C'est la lecture du joueur : ce qu'il voit écrit dans la grille, mot posé ou
    non. Sert à vérifier l'invariant central du fichier.
    """
    cases = {}
    for mot in grille["mots"]:
        for (r, c), lettre in zip(
                _cases(mot["m"], mot["r"], mot["c"], mot["d"] == "H"), mot["m"]):
            cases[(r, c)] = lettre

    trouvees = []
    for horizontal in (True, False):
        for a in range(grille["h"] if horizontal else grille["w"]):
            courant = ""
            for b in range((grille["w"] if horizontal else grille["h"]) + 1):
                pos = (a, b) if horizontal else (b, a)
                lettre = cases.get(pos)
                if lettre:
                    courant += lettre
                else:
                    if len(courant) >= 2:
                        trouvees.append(courant)
                    courant = ""
    return trouvees


def valider(grilles):
    """Contrôles qui doivent tenir avant d'écrire quoi que ce soit."""
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
                erreurs.append(f"#{index} : « {mot['m']} » sans définition")
            if re.search(r"\b" + re.escape(plier(mot["m"])) + r"\b", plier(mot["g"])):
                erreurs.append(f"#{index} : la définition de « {mot['m']} » la contient")
            if mot["m"] != mot["f"].upper():
                erreurs.append(f"#{index} : « {mot['m']} » ne majuscule pas « {mot['f']} »")
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

        # L'invariant : rien ne se lit dans la grille qui ne soit un mot posé.
        if not conflit:
            posees = Counter(m["m"] for m in mots)
            lues = Counter(_suites(grille))
            if posees != lues:
                intruses = [s for s in lues if s not in posees]
                erreurs.append(
                    f"#{index} : suites de lettres non voulues {intruses[:4]}")

        # Une grille doit être d'un seul tenant : deux blocs séparés sont deux
        # demi-grilles, et le joueur qui remplit l'un ne peut plus s'aider de
        # l'autre.
        if mots and not _dun_seul_tenant(mots):
            erreurs.append(f"#{index} : la grille est en plusieurs morceaux")

    if erreurs:
        for erreur in erreurs[:20]:
            print(f"   ❌ {erreur}")
        print(f"   ❌ {len(erreurs)} erreurs au total")
        return False

    total_mots = sum(len(g["mots"]) for g in grilles)
    formes = len({m["m"] for g in grilles for m in g["mots"]})
    print(f"   ✅ {len(grilles)} grilles valides, {total_mots} définitions, "
          f"{formes} formes distinctes")
    return True


def _dun_seul_tenant(mots):
    """Tous les mots se rejoignent-ils par des croisements ?"""
    cases = [
        set(_cases(m["m"], m["r"], m["c"], m["d"] == "H"))
        for m in mots
    ]
    atteints = {0}
    frontiere = [0]
    while frontiere:
        i = frontiere.pop()
        for j, autres in enumerate(cases):
            if j in atteints:
                continue
            if cases[i] & autres:
                atteints.add(j)
                frontiere.append(j)
    return len(atteints) == len(mots)


def sauvegarder(grilles, attribution_lod):
    """Écrit l'actif, après copie horodatée de la version précédente."""
    print("\n💾 ÉCRITURE DE L'ACTIF")
    print("-" * 45)

    if CHEMIN_GRILLES.exists():
        DOSSIER_BACKUPS.mkdir(exist_ok=True)
        horodatage = datetime.now().strftime("%Y%m%d_%H%M%S")
        copie = DOSSIER_BACKUPS / f"luxemburgish_crossword_{horodatage}.json"
        copie.write_bytes(CHEMIN_GRILLES.read_bytes())
        print(f"   🗄️  sauvegarde : {copie.name}")

    charge = {
        "version": 1,
        "generated": datetime.now().strftime("%Y-%m-%d"),
        # Les crédits voyagent dans l'actif, comme pour les autres jeux : les
        # définitions sont celles du LOD, les mots et leurs fréquences viennent
        # des deux corpus sous Creative Commons.
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

    print("🇱🇺 KRÄIZWUERT — GÉNÉRATION DES GRILLES DE MOTS CROISÉS 🇱🇺")
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

    par_niveau = construire_vivier(dico, table)
    if min(len(v) for v in par_niveau.values()) < 200:
        print("\n❌ Vivier trop maigre pour construire des grilles.")
        return 1

    grilles = construire_grilles(par_niveau, rng)
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
