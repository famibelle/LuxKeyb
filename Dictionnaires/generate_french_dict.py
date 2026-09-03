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

Deux paliers, et pourquoi
-------------------------

Le but n'est pas d'écrire en français mais d'**insérer des mots français dans
une frappe luxembourgeoise**. Les deux consommateurs n'ont donc pas les mêmes
besoins, et l'actif les sépare comme `luxemburgish_lod_forms.json` le fait déjà
côté luxembourgeois :

- `suggest` : les formes qui peuvent apparaître dans la rangée bleue, avec leur
  fréquence. On en écarte **les formes verbales rares** (catégorie dominante
  VER ou AUX, moins de 5 occurrences par million) : la conjugaison française
  explose en formes que personne n'insère dans une phrase luxembourgeoise —
  `réagissent`, `chanteriez`, `finissions`.
- `bloom` : **toutes** les formes, dans un filtre de Bloom, et non plus une
  liste de mots. Le correcteur n'a besoin que de répondre « ce mot est-il du
  français ? », et les deux erreurs d'un filtre de Bloom tombent du bon côté :
  il ne rejette **jamais** un mot qu'on y a mis, donc il ne peut pas souligner
  un mot français correct — la garantie qui compte ; il accepte parfois un mot
  qui n'y est pas, donc il laisse passer une faute de frappe, ce qui est bénin
  dans un clavier luxembourgeois. 125 348 formes tiennent ainsi dans ~150 Ko au
  lieu de ~7 Mo de chaînes en mémoire.

  Les formes verbales rares ne sont donc plus livrées en clair du tout : elles
  n'existent que dans le filtre.

**Le critère est grammatical, pas fréquentiel, et c'est mesuré.** Sur les 292
insertions françaises relevées dans les conférences de presse du gouvernement :

    tout (125 348 formes)              proposé 75,0 %   reconnu 100 %
    verbes rares écartés (71 586)      proposé 71,9 %   reconnu 100 %
    fréquence >= 2   (25 811)          proposé 60,6 %   reconnu 100 %

Un seuil de fréquence coûte quatre fois plus cher pour un gain comparable :
`résilience`, `incitatif`, `législation` sont au plancher de fréquence et sont
précisément ce qu'on insère. Les formes verbales rares, non.

Et il ne faut pas réduire l'index de suggestion davantage : mesuré, il ne pèse
que 673 Ko, parce qu'il ne porte que des indices entiers. Le rétrécir de moitié
économise 400 Ko et coûte 11 points de propositions.

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
import base64
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
# Mesuré à 71 586 formes proposables ; sous 40 000, le tri par catégorie a
# déraillé et la rangée bleue se viderait sans que rien n'échoue.
SEUIL_STRICT_PROPOSABLES = 40_000


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
    categories = {}
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
            # La catégorie dominante est celle qui pèse le plus dans le corpus,
            # pas la première rencontrée : `est` est un nom, un auxiliaire et un
            # verbe, et c'est le verbe qui décide de son sort.
            par_categorie = categories.setdefault(graphie, {})
            categorie = ligne.get("cgram") or ""
            par_categorie[categorie] = par_categorie.get(categorie, 0.0) + total
    return frequences, categories, locutions


FNV_OFFSET = 0xCBF29CE484222325
FNV_PRIME = 0x100000001B3
MASQUE64 = 0xFFFFFFFFFFFFFFFF
MASQUE63 = 0x7FFFFFFFFFFFFFFF
BLOOM_FAUX_POSITIFS = 0.01


def fnv1a(donnees):
    """FNV-1a 64 bits. Choisi pour être réimplémentable à l'identique en Kotlin
    en dix lignes, sans dépendance : le filtre est écrit ici et relu là-bas, et
    la moindre divergence de hachage ferait souligner tout le français."""
    h = FNV_OFFSET
    for octet in donnees:
        h = ((h ^ octet) * FNV_PRIME) & MASQUE64
    return h


def bits_bloom(nombre, faux_positifs=BLOOM_FAUX_POSITIFS):
    """Taille et nombre de hachages optimaux.

    Le nombre de bits est rendu **impair** et le second hachage forcé impair
    dans [indices_bloom] : sans ces deux précautions, la construction de
    Kirsch-Mitzenmacher fait partager leur parité aux k indices d'un même mot,
    qui ne couvrent alors que la moitié du filtre. Mesuré : 3,4 % de faux
    positifs au lieu de 1 %, pour la même taille.
    """
    import math
    bits = int(-nombre * math.log(faux_positifs) / (math.log(2) ** 2))
    if bits % 2 == 0:
        bits += 1
    hachages = max(1, round(bits / nombre * math.log(2)))
    return bits, hachages


def indices_bloom(mot, bits, hachages):
    """Kirsch-Mitzenmacher : deux hachages suffisent à en simuler k.

    Le masque 63 bits, plutôt qu'un modulo non signé, existe pour Kotlin : ses
    Long sont signés et `remainderUnsigned` n'arrive qu'à l'API 24, sous le
    minSdk 21 du projet. Les deux côtés font donc la même opération simple.
    """
    donnees = mot.encode("utf-8")
    h1 = fnv1a(donnees)
    h2 = fnv1a(b"\x00" + donnees) | 1
    return [((h1 + i * h2) & MASQUE63) % bits for i in range(hachages)]


def construire_bloom(formes):
    bits, hachages = bits_bloom(len(formes))
    tableau = bytearray((bits + 7) // 8)
    for mot in formes:
        for indice in indices_bloom(mot, bits, hachages):
            tableau[indice >> 3] |= 1 << (indice & 7)
    return tableau, bits, hachages


CATEGORIES_VERBALES = {"VER", "AUX"}
SEUIL_VERBE_RARE = 5


def est_verbe_rare(graphie, frequence, categories):
    """Une forme verbale qu'on n'insère pas dans une phrase luxembourgeoise.

    Le seuil porte sur la forme, pas sur le lemme : `travaille` reste proposable
    quand `travaillassions` ne l'est pas, alors que les deux partagent leur
    lemme.
    """
    par_categorie = categories.get(graphie)
    if not par_categorie:
        return False
    dominante = max(par_categorie, key=par_categorie.get)
    return dominante in CATEGORIES_VERBALES and frequence < SEUIL_VERBE_RARE


def mesurer_faux_positifs(tableau, bits, hachages, connues, echantillons=20000):
    """Taux mesuré, pas calculé : c'est le filtre livré qu'on interroge."""
    import random
    alea = random.Random(20260903)
    lettres = "abcdefghijklmnopqrstuvwxyzéèêàôùïüç"
    faux = essais = 0
    while essais < echantillons:
        mot = "".join(alea.choice(lettres) for _ in range(alea.randint(4, 12)))
        if mot in connues:
            continue
        essais += 1
        if all(tableau[i >> 3] & (1 << (i & 7))
               for i in indices_bloom(mot, bits, hachages)):
            faux += 1
    return faux / essais


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
    frequences, categories, locutions = lire_frequences(chemin)
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

    proposables = [(g, f) for g, f in mots if not est_verbe_rare(g, f, categories)]
    reconnues = [g for g, f in mots if est_verbe_rare(g, f, categories)]

    rares = sum(1 for _, f in mots if f == 1)
    print("\n📊 Fréquences (occurrences par million, sous-titres + livres)")
    print(f"   ↑ {mots[0][0]} = {mots[0][1]}")
    print(f"   ↓ {rares} formes au plancher de 1")
    print("\n📐 Paliers")
    print(f"   ✍️  proposables : {len(proposables)}")
    print(f"   ✅ reconnues seulement : {len(reconnues)} formes verbales rares")

    if arguments.strict and len(mots) < SEUIL_STRICT:
        print(f"\n❌ --strict : {len(mots)} formes, moins que le seuil de {SEUIL_STRICT}")
        return 1
    toutes = [graphie for graphie, _ in mots]
    tableau, bits, hachages = construire_bloom(toutes)
    faux = mesurer_faux_positifs(tableau, bits, hachages, set(toutes))
    print("\n🧪 Filtre de Bloom")
    print(f"   {len(tableau) // 1024} Ko, {hachages} hachages pour {len(toutes)} formes")
    print(f"   faux positifs mesurés : {100 * faux:.2f} %")
    if arguments.strict and faux > 0.02:
        print(f"\n❌ --strict : {100*faux:.1f} % de faux positifs, filtre sous-dimensionné")
        return 1

    if arguments.strict and len(proposables) < SEUIL_STRICT_PROPOSABLES:
        print(f"\n❌ --strict : {len(proposables)} formes proposables, "
              f"moins que le seuil de {SEUIL_STRICT_PROPOSABLES}")
        return 1

    contenu = {
        "version": "lexique383_1.0",
        "language": "fr",
        "type": "simple_dictionary",
        "word_count": len(mots),
        "counts": {"suggest": len(proposables), "spellcheck": len(reconnues)},
        "created": datetime.now().strftime("%Y-%m-%d"),
        "source": SOURCE,
        "licence": LICENCE,
        "attribution": ATTRIBUTION,
        "description": (
            "Formes fléchies du français. `suggest_mots` / `suggest_freq` "
            "alimentent la seconde rangée de suggestions, triées par fréquence "
            "décroissante ; `spellcheck` ajoute les formes verbales rares, que "
            "le correcteur accepte sans jamais les proposer."
        ),
        # Trois tableaux plats plutôt qu'un tableau de paires : `org.json`
        # construit tout l'arbre avant que le chargeur ne le lise, et 125 348
        # JSONArray imbriqués sont un pic d'allocation que le processus de
        # saisie paie au pire moment. Deux tableaux parallèles ne coûtent que
        # leurs éléments.
        "suggest_mots": [graphie for graphie, _ in proposables],
        "suggest_freq": [frequence for _, frequence in proposables],
        "bloom_bits": bits,
        "bloom_hachages": hachages,
        "bloom": base64.b64encode(bytes(tableau)).decode("ascii"),
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
