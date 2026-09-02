#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
🇱🇺 TRADUCTIONS — glose française des mots luxembourgeois des jeux
==================================================================

Produit `android_keyboard/app/src/main/assets/luxemburgish_translations.json`,
la table qui permet aux quatre jeux d'afficher ce que veut dire le mot qu'ils
font chercher. Sans elle, Wuertsich et Wuertmix demandent de retrouver
« KËSCHT » sans jamais dire qu'il s'agit d'une caisse : on y exerce son
orthographe, jamais son vocabulaire.

    cd Dictionnaires
    python generate_translations.py --strict

Comme `generate_cloze.py`, ce script ne reconstruit rien : il **consomme** le
dictionnaire déjà livré dans les assets et n'y ajoute aucun mot. Il faut donc
l'exécuter APRÈS `LuxembourgishComplet.py`, sinon la table gloserait des formes
qui ne sont plus dans le dictionnaire et laisserait les nouvelles sans rien.

La source : le **Lëtzebuerger Online Dictionnaire (LOD)**, le dictionnaire
officiel du Zenter fir d'Lëtzebuerger Sprooch, publié en **CC0** sur
data.public.lu. C'est la seule licence du projet qui n'impose rien — les corpus
LuxAlign (CC BY-NC) et LETZ (CC BY) exigent, eux, une attribution. On crédite
quand même le ZLS, dans l'actif et à l'écran : c'est leur travail.

Trois choses non évidentes décident de la couverture et de la qualité :

1. **Le dictionnaire de l'app contient des formes fléchies, le LOD des lemmes.**
   Chercher « Haiser » dans la liste des lemmes ne donne rien. C'est l'index de
   recherche du LOD (`new_lod-search.xml`, ses `<spelling>`) qui fait le pont :
   il liste toutes les graphies par lesquelles on atteint un article, flexions
   comprises. Passer par lui fait bondir la couverture de 36 % à 55 % des
   formes, et de 72 % à 90 % des occurrences.

2. **Les sous-entrées idiomatiques sont écartées.** Un article du LOD range ses
   locutions dans des `<meaning>` porteurs d'un `<secondaryHeadword>` :
   l'article « A » (œil) glose ainsi « œil au beurre noir » et « ouvrir les yeux
   sur ». Les retenir ferait afficher « A = œil au beurre noir ». On ne garde
   que les acceptions du mot lui-même.

3. **Ce qui reste sans glose est surtout du nom propre**, et c'est très bien :
   « Esch », « Bettel », « RTL », « Jean-Claude » n'ont rien à traduire. Les
   jeux tirent donc leurs mots parmi les formes glosées — voir
   `TranslationDictionary.kt` côté Android — et un mot sans glose ne fait plus
   perdre une question.

Fait avec ❤️ pour préserver le Luxembourgeois
"""

import argparse
import io
import json
import sys
import unicodedata
import xml.etree.ElementTree as ET
from collections import OrderedDict
from datetime import datetime
from pathlib import Path

from lod_source import ATTRIBUTION, telecharger_source

if sys.platform.startswith('win'):
    import codecs
    sys.stdout = codecs.getwriter('utf-8')(sys.stdout.buffer, 'strict')
    sys.stderr = codecs.getwriter('utf-8')(sys.stderr.buffer, 'strict')

RACINE_ASSETS = Path(__file__).resolve().parent.parent / \
    "android_keyboard/app/src/main/assets"
CHEMIN_DICT = RACINE_ASSETS / "luxemburgish_dict.json"
CHEMIN_TRAD = RACINE_ASSETS / "luxemburgish_translations.json"
CHEMIN_FORMES = RACINE_ASSETS / "luxemburgish_lod_forms.json"
DOSSIER_BACKUPS = Path(__file__).resolve().parent / "backups"

# Nombre maximal d'acceptions gardées par mot. Une seule glose ampute
# « Schlass » (château / serrure) d'un sens que le joueur croira faux ; au-delà
# de trois, la ligne déborde de l'écran d'un téléphone.
MAX_GLOSES = 3

# Une glose plus longue que cela est une définition déguisée, pas une
# traduction : elle ne tiendra pas sur la ligne du mot à trouver.
LONGUEUR_MAX_GLOSE = 48


def lire_traductions(xml_articles, verbeux=True):
    """id d'article → (lemme, nom propre ?, gloses françaises).

    Deux tris qui changent ce que le joueur lira en premier :

    - les `<meaning>` porteurs d'un `<secondaryHeadword>` sont sautés — ce sont
      les locutions de l'article, pas le mot : l'article « A » (œil) glose
      ainsi « œil au beurre noir » ;
    - les acceptions restantes sont remises dans l'ordre de leur `<number>`,
      que le fichier ne respecte pas. Sans ce tri « gutt » se glose
      « meilleur, huppé, bon » au lieu de « bon, meilleur, huppé ».
    """
    par_article = {}
    for _, entree in ET.iterparse(io.BytesIO(xml_articles), events=("end",)):
        if entree.tag != "entry":
            continue
        identifiant = entree.get("id")
        lemme = (entree.findtext("lemma") or "").strip()
        nom_propre = (entree.findtext(".//partOfSpeech") or "") == "NP"

        acceptions = []
        for sens in entree.iter("meaning"):
            if sens.find("secondaryHeadword") is not None:
                continue
            try:
                rang = int(sens.findtext("number") or 0)
            except ValueError:
                rang = 0
            for cible in sens.findall("targetLanguage"):
                if cible.get("lang") != "fr":
                    continue
                glose = (cible.findtext("translation") or "").strip()
                if glose and len(glose) <= LONGUEUR_MAX_GLOSE:
                    acceptions.append((rang, glose))

        gloses = []
        for _, glose in sorted(acceptions, key=lambda a: a[0]):
            if glose not in gloses:
                gloses.append(glose)
        if identifiant and gloses:
            par_article[identifiant] = (lemme, nom_propre, gloses)
        entree.clear()

    if verbeux:
        propres = sum(1 for _, propre, _ in par_article.values() if propre)
        print(f"   📖 {len(par_article)} articles glosés en français "
              f"(dont {propres} noms propres)")
    return par_article


def lire_graphies(xml_index, verbeux=True):
    """graphie → ids d'articles, pour toutes les formes d'un seul mot.

    Les graphies à plusieurs mots (« virun Ae féieren ») et celles à apostrophe
    (« d'A ») sont écartées : le dictionnaire de l'app ne contient que des
    formes simples, et les apparier ne ferait que du bruit.
    """
    par_graphie = {}
    for _, entree in ET.iterparse(io.BytesIO(xml_index), events=("end",)):
        if entree.tag != "entry":
            continue
        identifiant = entree.get("id")
        graphies = entree.find("spellings")
        if identifiant and graphies is not None:
            for graphie in graphies.findall("spelling"):
                forme = (graphie.text or "").strip()
                if forme and " " not in forme and "'" not in forme:
                    par_graphie.setdefault(forme, []).append(identifiant)
        entree.clear()

    if verbeux:
        print(f"   🔤 {len(par_graphie)} graphies simples indexées")
    return par_graphie


def gloser(forme, par_graphie, par_graphie_min, par_article):
    """Gloses d'une forme du dictionnaire, ou None.

    La graphie exacte d'abord, puis la même à la casse près : le dictionnaire
    élit une casse canonique par forme et le LOD lemmatise à la sienne, si bien
    que « Aacht » et « aacht » ne se rencontrent qu'après repli.

    Une graphie mène souvent à plusieurs articles, et l'ordre décide de ce que
    le joueur lit. Deux préférences, dans cet ordre :

    - **l'article dont c'est le lemme** passe devant celui où la forme n'est
      qu'une flexion. « rout » est à la fois l'adjectif ROUT (rouge) et une
      forme de ROUEN (se reposer) ; sans cette règle le jeu annonce « rout =
      se reposer » ;
    - **les noms propres passent en dernier**, sans être écartés. Les écarter
      priverait « Lëtzebuerg » de sa seule glose ; les laisser devant fait
      gloser « Schlass » par « Beaufort-Château » plutôt que par « château ».
    """
    identifiants = par_graphie.get(forme) or par_graphie_min.get(forme.lower())
    if not identifiants:
        return None

    def priorite(identifiant):
        lemme, nom_propre, _ = par_article.get(identifiant, ("", True, []))
        if lemme == forme:
            rang = 0
        elif lemme.lower() == forme.lower():
            rang = 1
        else:
            rang = 2
        return (rang, 1 if nom_propre else 0)

    gloses = []
    for identifiant in sorted(identifiants, key=priorite):
        for glose in par_article.get(identifiant, ("", False, []))[2]:
            if glose not in gloses:
                gloses.append(glose)
            if len(gloses) >= MAX_GLOSES:
                return gloses
    return gloses or None


def _plier(texte):
    """Minuscules sans diacritiques. Miroir de `AccentTolerantMatcher.normalize`."""
    decompose = unicodedata.normalize("NFD", texte.lower())
    return "".join(c for c in decompose if unicodedata.category(c) != "Mn")


def _instructive(forme, glose):
    """Vrai si au moins une acception diffère du mot lui-même.

    Le luxembourgeois emprunte massivement au français : « Accident » se glose
    « accident », « Budget » « budget ». La glose est exacte et sans aucun
    secours. Les jeux ne tirent pas ces mots-là ; la table les garde, parce
    qu'une glose juste reste juste là où le mot arrive par un autre chemin.
    """
    return any(_plier(x.strip()) != _plier(forme) for x in glose.split(","))


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
        description="Glose française des mots du dictionnaire luxembourgeois")
    analyseur.add_argument("--strict", action="store_true",
                           help="échouer si la couverture s'effondre")
    analyseur.add_argument("--hors-ligne", action="store_true",
                           help="n'utiliser que le cache local du LOD")
    analyseur.add_argument("--sortie", type=Path, default=CHEMIN_TRAD)
    arguments = analyseur.parse_args()

    print("🇱🇺 TRADUCTIONS LUXEMBOURGEOIS → FRANÇAIS")
    print("=" * 60)

    if not CHEMIN_DICT.exists():
        print(f"❌ {CHEMIN_DICT} introuvable — lancez d'abord LuxembourgishComplet.py")
        return 1
    dictionnaire = json.loads(CHEMIN_DICT.read_text(encoding="utf-8"))
    if not isinstance(dictionnaire, list):
        print("❌ le dictionnaire n'est pas un tableau de paires")
        return 1
    print(f"\n📚 Dictionnaire : {len(dictionnaire)} formes")

    print("\n🔎 Sources LOD")
    try:
        xml_articles = telecharger_source("art", arguments.hors_ligne)
        xml_index = telecharger_source("search", arguments.hors_ligne)
    except Exception as erreur:
        print(f"❌ LOD indisponible : {erreur}")
        return 1

    par_article = lire_traductions(xml_articles)
    par_graphie = lire_graphies(xml_index)
    par_graphie_min = {}
    for forme, identifiants in par_graphie.items():
        par_graphie_min.setdefault(forme.lower(), []).extend(identifiants)

    print("\n🔗 Appariement")
    table = OrderedDict()
    occurrences_glosees = 0
    occurrences_totales = 0
    for forme, frequence in dictionnaire:
        occurrences_totales += frequence
        gloses = gloser(forme, par_graphie, par_graphie_min, par_article)
        if gloses:
            table[forme] = ", ".join(gloses)
            occurrences_glosees += frequence

    part_formes = 100 * len(table) / max(1, len(dictionnaire))
    part_occurrences = 100 * occurrences_glosees / max(1, occurrences_totales)
    print(f"   ✅ {len(table)} formes du corpus glosées "
          f"({part_formes:.1f} % des formes, {part_occurrences:.1f} % des occurrences)")

    # Les formes que le LOD apporte au clavier sans passer par le corpus
    # (`generate_lod_forms.py`) sont glosées elles aussi : sans quoi l'onglet
    # Wierderbuch chercherait dans 38 000 mots pendant que le clavier en
    # complète 123 000, et « Läffelen » n'y renverrait rien.
    #
    # Elles ne rejoignent PAS les réserves de jeu comptées plus bas : les trois
    # jeux tirent leurs mots de luxemburgish_dict.json, et le chiffre annoncé
    # ici doit rester celui qu'ils voient.
    formes_lod = []
    if CHEMIN_FORMES.exists():
        actif = json.loads(CHEMIN_FORMES.read_text(encoding="utf-8"))
        formes_lod = [f for f in actif.get("suggest", []) if f not in table]
    else:
        print("   ⚠️ luxemburgish_lod_forms.json absent — "
              "seules les formes du corpus seront glosées")

    glosees_lod = 0
    for forme in formes_lod:
        gloses = gloser(forme, par_graphie, par_graphie_min, par_article)
        if gloses:
            table[forme] = ", ".join(gloses)
            glosees_lod += 1
    if formes_lod:
        print(f"   ✅ {glosees_lod} formes LOD glosées en plus "
              f"(sur {len(formes_lod)} apportées au clavier)")

    # Les jeux ne tirent que parmi les formes dont la glose apprend quelque
    # chose : si l'une de ces réserves se vide, le jeu correspondant se
    # retrouve sans mots et l'échec est silencieux à l'écran. On les compte
    # ici, où l'échec est bruyant — avec la même règle que
    # `TranslationDictionary.gloseInstructive`, sinon le chiffre annoncé ne
    # serait pas celui que le jeu voit.
    formes_dictionnaire = {forme for forme, _ in dictionnaire}
    instructives = [f for f, glose in table.items()
                    if f in formes_dictionnaire and _instructive(f, glose)]
    glosees_dico = sum(1 for f in table if f in formes_dictionnaire)
    print(f"   💡 {len(instructives)} formes du dictionnaire dont la glose ne répète "
          f"pas le mot ({glosees_dico - len(instructives)} emprunts ou toponymes "
          f"écartés du tirage)")
    reserves = {
        "Wuertsich (3–8 lettres)": sum(1 for f in instructives if 3 <= len(f) <= 8),
        "Wuertmix (4–10 lettres)": sum(1 for f in instructives if 4 <= len(f) <= 10),
        "Wuertriet (5 lettres)": sum(1 for f in instructives
                                     if len(f) == 5 and f.isalpha()),
    }
    for libelle, compte in reserves.items():
        print(f"   🎮 {libelle} : {compte} mots")

    if arguments.strict:
        if part_formes < 40:
            print(f"❌ --strict : couverture des formes tombée à {part_formes:.1f} %")
            return 1
        if reserves["Wuertriet (5 lettres)"] < 300:
            print("❌ --strict : moins de 300 mots de 5 lettres glosés, "
                  "Wuertriet n'aurait plus de réserve")
            return 1

    contenu = {
        "version": datetime.now().strftime("%Y.%m.%d"),
        "generated": datetime.now().isoformat(timespec="seconds"),
        "source": "Lëtzebuerger Online Dictionnaire (LOD)",
        "licence": "CC0-1.0",
        "attribution": ATTRIBUTION,
        "count": len(table),
        "translations": table,
    }

    sauvegarder_precedent(arguments.sortie)
    arguments.sortie.parent.mkdir(parents=True, exist_ok=True)
    arguments.sortie.write_text(
        json.dumps(contenu, ensure_ascii=False, indent=None,
                   separators=(",", ":")),
        encoding="utf-8")
    taille = arguments.sortie.stat().st_size / 1024
    print(f"\n💾 {arguments.sortie.name} — {taille:.0f} Ko")
    print("✅ Terminé")
    return 0


if __name__ == "__main__":
    sys.exit(main())
