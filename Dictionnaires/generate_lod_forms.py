#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
🇱🇺 FORMES LOD — la couverture que le corpus ne peut pas donner
================================================================

Produit `android_keyboard/app/src/main/assets/luxemburgish_lod_forms.json`,
la liste des formes luxembourgeoises attestées par le **Lëtzebuerger Online
Dictionnaire** et absentes du dictionnaire de fréquences.

    cd Dictionnaires
    python generate_lod_forms.py --strict   # après LuxembourgishComplet.py

Pourquoi un second actif plutôt qu'un dictionnaire élargi
---------------------------------------------------------

`luxemburgish_dict.json` est un relevé de fréquences : chaque entrée porte le
nombre de fois où la forme apparaît dans LuxAlign + LETZ, et cinq consommateurs
s'appuient sur ce sens-là. Les trois jeux y tirent leurs mots, le mot du jour et
« Mots à découvrir » le parcourent, et surtout `LuxLevels` calcule les huit
paliers en **pourcentage de sa taille** : y verser 85 000 formes sans fréquence
ferait reculer d'un cran tous les joueurs existants et remplirait Wuertmix de
« wäissbandkräizschniewel ». Le dictionnaire reste donc ce qu'il dit être.

Les formes du LOD vont dans un actif séparé, que **seul `SuggestionEngine`
lit** : la complétion et le correcteur orthographique en profitent, le reste de
l'application ne les voit pas.

Ce que ça change, mesuré (2026-09-02)
-------------------------------------

Le corpus est du journalisme RTL : il n'écrit jamais ce qu'on tape sur un
téléphone. Manquaient ainsi `Läffelen`, `Forschetten`, `Telleren`, `Mounden`,
`sprang`, `denks`, `schaffesch`, `schreifs` — toutes attestées au LOD. Le
dictionnaire couvre 38 410 formes, le LOD en propose 103 688 ; leur
intersection n'est que de 18 752, d'où ~85 000 ajouts.

L'inverse est vrai aussi, et c'est pourquoi il s'agit d'une union et jamais
d'un remplacement : 19 374 entrées du dictionnaire sont inconnues du LOD —
`Rue`, `CSV`, `RTL`, `Bettel`, `Juncker`, `OGBL` — soit 7,3 % des occurrences
du corpus. Noms propres, sigles et emprunts, qu'aucun dictionnaire de langue
n'a vocation à lister.

Deux paliers, deux portées
--------------------------

- `suggest` : graphies que le LOD assume (`suggest="true"`). Proposées à la
  frappe et acceptées par le correcteur.
- `spellcheck` : variantes de la **règle d'Eifel** (`reason="n-rule"`), où le
  n final tombe devant consonne (`Ae` pour `Aen`). Le LOD ne les propose pas
  comme graphie de tête, mais elles sont correctes en contexte : les souligner
  en rouge serait un bug. Elles sont donc **connues sans être proposées**.

Ce qui est écarté et pourquoi, voir `lod_source.CATEGORIES_*`.

Fait avec ❤️ pour préserver le Luxembourgeois
"""

import argparse
import base64
import json
import unicodedata
import sys
from datetime import datetime
from pathlib import Path

if sys.platform.startswith('win'):
    import codecs
    sys.stdout = codecs.getwriter('utf-8')(sys.stdout.buffer, 'strict')
    sys.stderr = codecs.getwriter('utf-8')(sys.stderr.buffer, 'strict')

from bloom import construire as construire_bloom
from bloom import mesurer_faux_positifs
from lod_source import (ATTRIBUTION, CATEGORIES_CONNUES, CATEGORIES_SUGGEREES,
                        graphies_par_categorie, telecharger_source)

RACINE_ASSETS = Path(__file__).resolve().parent.parent / \
    "android_keyboard/app/src/main/assets"
CHEMIN_DICT = RACINE_ASSETS / "luxemburgish_dict.json"
CHEMIN_FORMES = RACINE_ASSETS / "luxemburgish_lod_forms.json"
DOSSIER_BACKUPS = Path(__file__).resolve().parent / "backups"

# Sous ce nombre d'ajouts, quelque chose s'est cassé en amont : soit l'index du
# LOD a changé de structure, soit le dictionnaire a explosé. Mesuré à ~85 000,
# on refuse en dessous de la moitié.
SEUIL_STRICT_SUGGEST = 40_000
SEUIL_STRICT_SPELLCHECK = 10_000


def replier(mot):
    """Comme `AccentTolerantMatcher.normalize` : minuscules sans diacritiques.

    C'est sous cette forme que `SuggestionEngine.isKnownWord` interroge, donc
    c'est sous cette forme que le filtre doit être construit. Une divergence
    ferait souligner toute la langue d'un coup ; `LodFormsAssetTest` rejoue le
    filtre livré sur les formes livrées.
    """
    return "".join(c for c in unicodedata.normalize("NFD", mot.lower())
                   if unicodedata.category(c) != "Mn")


def choisir_casse(formes):
    """Une seule graphie par forme repliée, la capitale l'emportant.

    Le LOD liste parfois `Rout` et `rout` depuis deux articles distincts. Les
    livrer toutes deux ferait afficher deux fois la même suggestion, puisque
    `applyCasingPattern()` recase de toute façon la proposition sur ce que
    l'utilisateur a tapé. On garde la capitalisée : le luxembourgeois
    capitalise ses substantifs, et c'est la forme qui porte l'information.
    """
    groupes = {}
    for forme in formes:
        groupes.setdefault(forme.casefold(), []).append(forme)
    retenues = []
    collisions = 0
    for variantes in groupes.values():
        if len(variantes) > 1:
            collisions += 1
        retenues.append(sorted(variantes, key=lambda f: (f.islower(), f))[0])
    return retenues, collisions


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
        description="Formes du LOD absentes du dictionnaire de fréquences")
    analyseur.add_argument("--strict", action="store_true",
                           help="échouer si la couverture s'effondre")
    analyseur.add_argument("--hors-ligne", action="store_true",
                           help="n'utiliser que le cache local du LOD")
    analyseur.add_argument("--sortie", type=Path, default=CHEMIN_FORMES)
    arguments = analyseur.parse_args()

    print("🇱🇺 FORMES LOD — COMPLÉMENT DU DICTIONNAIRE")
    print("=" * 60)

    if not CHEMIN_DICT.exists():
        print(f"❌ {CHEMIN_DICT} introuvable — lancez d'abord LuxembourgishComplet.py")
        return 1
    dictionnaire = json.loads(CHEMIN_DICT.read_text(encoding="utf-8"))
    if not isinstance(dictionnaire, list):
        print("❌ le dictionnaire n'est pas un tableau de paires")
        return 1
    connues = {forme.casefold() for forme, _ in dictionnaire}
    print(f"\n📚 Dictionnaire : {len(dictionnaire)} formes")

    print("\n🔎 Index de recherche du LOD")
    try:
        xml_index = telecharger_source("search", arguments.hors_ligne)
    except Exception as erreur:
        print(f"❌ LOD indisponible : {erreur}")
        return 1
    par_categorie = graphies_par_categorie(xml_index)

    def retenir(categories):
        brutes = set()
        for categorie in categories:
            brutes |= par_categorie.get(categorie, set())
        return {f for f in brutes if f.casefold() not in connues}

    print("\n🔗 Différence avec le dictionnaire")
    proposables_brutes = retenir(CATEGORIES_SUGGEREES)
    proposables, collisions = choisir_casse(proposables_brutes)
    proposables.sort(key=lambda f: (f.casefold(), f))
    # Le second palier ne doit pas redire le premier, sinon le moteur
    # chargerait deux fois la même forme.
    deja = {f.casefold() for f in proposables}
    connues_seules_brutes = {f for f in retenir(CATEGORIES_CONNUES)
                             if f.casefold() not in deja}
    connues_seules, _ = choisir_casse(connues_seules_brutes)
    connues_seules.sort(key=lambda f: (f.casefold(), f))

    total_lod = len(par_categorie.get("suggest", set()))
    print(f"   ✅ proposables    : {len(proposables)} ajouts "
          f"(sur {total_lod} graphies assumées par le LOD)")
    print(f"      dont {collisions} formes livrées en une seule casse "
          f"(Rout/rout et consorts)")
    print(f"   ✅ connues seules : {len(connues_seules)} variantes de la règle d'Eifel")
    couverture = len(dictionnaire) + len(proposables)
    print(f"   📈 formes reconnues à la frappe : {len(dictionnaire)} → {couverture} "
          f"(×{couverture / max(1, len(dictionnaire)):.1f})")

    if arguments.strict:
        if len(proposables) < SEUIL_STRICT_SUGGEST:
            print(f"❌ --strict : seulement {len(proposables)} ajouts proposables, "
                  f"attendu ≥ {SEUIL_STRICT_SUGGEST}")
            return 1
        if len(connues_seules) < SEUIL_STRICT_SPELLCHECK:
            print(f"❌ --strict : seulement {len(connues_seules)} variantes n-rule, "
                  f"attendu ≥ {SEUIL_STRICT_SPELLCHECK}")
            return 1

    # Filtre de Bloom de **toutes** les formes que le clavier reconnaît :
    # le dictionnaire de fréquences, les formes proposables du LOD et les
    # variantes de la règle d'Eifel, repliées comme `AccentTolerantMatcher`
    # les replie — c'est sous cette forme que `isKnownWord` interroge.
    #
    # Il remplace deux tables de hachage tenues en mémoire par le processus de
    # saisie : `normalizedWordSet` (123 000 entrées) et `extraKnownForms`
    # (26 000 chaînes qui n'existaient que pour elle). Le correcteur ne pose
    # qu'une question, « ce mot existe-t-il », et un filtre de Bloom ne peut
    # jamais rejeter ce qu'on y a mis : il ne fera donc pas souligner un mot
    # correct. Il accepte ~1 % d'intrus, c'est-à-dire qu'il laisse passer une
    # faute de temps en temps — sans conséquence.
    reconnues = ({replier(m) for m, _ in dictionnaire}
                 | {replier(m) for m in proposables}
                 | {replier(m) for m in connues_seules})
    tableau, bits, hachages = construire_bloom(reconnues)
    faux = mesurer_faux_positifs(tableau, bits, hachages, reconnues)
    print(f"\n🧪 Filtre de Bloom : {len(tableau) // 1024} Ko, {hachages} hachages "
          f"pour {len(reconnues)} formes repliées")
    print(f"   faux positifs mesurés : {100 * faux:.2f} %")
    if arguments.strict and faux > 0.02:
        print(f"\n❌ --strict : {100*faux:.1f} % de faux positifs, filtre sous-dimensionné")
        return 1

    contenu = {
        "version": datetime.now().strftime("%Y.%m.%d"),
        "generated": datetime.now().isoformat(timespec="seconds"),
        "source": "Lëtzebuerger Online Dictionnaire (LOD) — index de recherche",
        "licence": "CC0-1.0",
        "attribution": ATTRIBUTION,
        "counts": {"suggest": len(proposables), "spellcheck": len(connues_seules)},
        "suggest": proposables,
        "spellcheck": connues_seules,
        "bloom_bits": bits,
        "bloom_hachages": hachages,
        "bloom": base64.b64encode(bytes(tableau)).decode("ascii"),
    }

    sauvegarder_precedent(arguments.sortie)
    arguments.sortie.parent.mkdir(parents=True, exist_ok=True)
    arguments.sortie.write_text(
        json.dumps(contenu, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8")
    taille = arguments.sortie.stat().st_size / 1024
    print(f"\n💾 {arguments.sortie.name} — {taille:.0f} Ko")
    print("✅ Terminé")
    return 0


if __name__ == "__main__":
    sys.exit(main())
