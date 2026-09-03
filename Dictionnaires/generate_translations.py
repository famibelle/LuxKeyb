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
CHEMIN_FAMILLES = RACINE_ASSETS / "luxemburgish_familles.json"
CHEMIN_EXEMPLES = RACINE_ASSETS / "luxemburgish_exemples.json"
CHEMIN_LOD_IDS = RACINE_ASSETS / "luxemburgish_lod_ids.json"
CHEMIN_FORMES = RACINE_ASSETS / "luxemburgish_lod_forms.json"
DOSSIER_BACKUPS = Path(__file__).resolve().parent / "backups"

# Nombre maximal d'acceptions gardées par mot. Une seule glose ampute
# « Schlass » (château / serrure) d'un sens que le joueur croira faux ; au-delà
# de trois, la ligne déborde de l'écran d'un téléphone.
MAX_GLOSES = 3

# Une glose plus longue que cela est une définition déguisée, pas une
# traduction : elle ne tiendra pas sur la ligne du mot à trouver.
LONGUEUR_MAX_GLOSE = 48

# Combien de phrases d'exemple la fiche d'un mot porte. Deux : une seule laisse
# croire que le mot ne s'emploie que là, et au-delà la fiche pousse ses deux
# boutons hors de l'écran — le même plafond que les flexions, pour la même
# raison.
MAX_EXEMPLES = 2

# Une phrase plus longue que cela est un paragraphe sur un téléphone. La
# médiane du LOD est à 53 caractères, le neuvième décile à 77 : on ne coupe
# donc que la queue (maximum observé : 149).
LONGUEUR_MAX_EXEMPLE = 110

# Une phrase trop courte n'illustre rien : « en décke Kapp » n'apprend pas à
# employer le mot.
LONGUEUR_MIN_EXEMPLE = 15

# Les marques que porte un `<attribute>` d'exemple, et pourquoi on les écarte
# toutes les trois :
#
#   EGS   3 040 — emploi figuré. Chacune de ces phrases, sans exception, porte
#                 un `<gloss>` qui l'explique : la montrer sans lui ferait
#                 illustrer « A » (œil) par une expression qui ne parle pas
#                 d'organe. C'est la règle du `secondaryHeadword` appliquée à
#                 l'exemple plutôt qu'à l'acception.
#   VULG      8 — registre obscène, jamais montré par l'application.
#   PEJ       1 — péjoratif, même raison.
#
# IRON (35) et FAM (29) restent : ce sont des phrases ordinaires, seulement
# marquées comme telles.
MARQUES_ECARTEES = {"EGS", "VULG", "PEJ"}


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


def _phrase(texte):
    """Recompose la phrase d'un `<example>`, mot à mot.

    Le LOD ne stocke pas de phrase : il stocke la suite de ses mots, le mot
    vedette étant balisé à part (`<inflectedHeadword>`) pour que le site le
    mette en gras. Recoller demande deux précautions, sinon la phrase se lit
    comme une transcription :

    - pas d'espace après une élision (« d' », « s' »), qui est un mot à elle
      seule dans le fichier — sans quoi « an d' Ae gekuckt » ;
    - pas d'espace avant une ponctuation isolée, le fichier écrivant tantôt
      « midd, » d'un bloc, tantôt « ! » à part.
    """
    morceaux = []
    for enfant in texte:
        if enfant.tag == "attribute":
            continue
        mot = (enfant.text or "").strip()
        if not mot:
            continue
        if morceaux and not morceaux[-1].endswith(("'", "\u2019")) \
                and mot[0] not in ",.!?;:)»…":
            morceaux.append(" ")
        morceaux.append(mot)
    return "".join(morceaux)


def lire_exemples(xml_articles, verbeux=True):
    """id d'article → phrases d'exemple, dans l'ordre des acceptions.

    Le LOD porte 58 962 phrases d'exemple, écrites par le ZLS pour illustrer
    l'emploi de chaque mot : c'est ce qu'une glose ne donne jamais. « Haus =
    maison » ne dit pas qu'on dit « ech ginn heem », et un apprenant lit une
    phrase plus vite qu'une définition.

    Deux tris repris de [lire_traductions], pour que la phrase montrée illustre
    bien le sens affiché : les acceptions idiomatiques (`secondaryHeadword`)
    sont sautées, et les autres remises dans l'ordre de leur `<number>`, que le
    fichier ne respecte pas. Sans ce second tri la fiche de « gutt »
    illustrerait « huppé » pendant que sa glose annonce « bon ».
    """
    par_article = {}
    for _, entree in ET.iterparse(io.BytesIO(xml_articles), events=("end",)):
        if entree.tag != "entry":
            continue
        identifiant = entree.get("id")

        phrases = []
        for sens in entree.iter("meaning"):
            if sens.find("secondaryHeadword") is not None:
                continue
            try:
                rang = int(sens.findtext("number") or 0)
            except ValueError:
                rang = 0
            for exemple in sens.iter("example"):
                texte = exemple.find("text")
                if texte is None:
                    continue
                marques = {(e.text or "").strip() for e in texte
                           if e.tag == "attribute"}
                if marques & MARQUES_ECARTEES:
                    continue
                phrase = _phrase(texte)
                if LONGUEUR_MIN_EXEMPLE <= len(phrase) <= LONGUEUR_MAX_EXEMPLE:
                    phrases.append((rang, phrase))

        retenues = []
        for _, phrase in sorted(phrases, key=lambda p: p[0]):
            if phrase not in retenues:
                retenues.append(phrase)
            if len(retenues) >= MAX_EXEMPLES:
                break
        if identifiant and retenues:
            par_article[identifiant] = retenues
        entree.clear()

    if verbeux:
        total = sum(len(v) for v in par_article.values())
        print(f"   💬 {total} phrases d'exemple sur {len(par_article)} articles")
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


def articles_tries(forme, par_graphie, par_graphie_min, par_article):
    """Articles du LOD atteints par une forme, du plus pertinent au moins.

    Extrait de [gloser] pour que la glose d'une forme et la famille à laquelle
    on la rattache désignent le **même** article : sans cela, « Forschetten »
    pourrait être glosée par un article et regroupée sous un autre, et la fiche
    afficherait des formes qui n'ont rien à voir avec le sens montré.
    """
    identifiants = par_graphie.get(forme) or par_graphie_min.get(forme.lower())
    if not identifiants:
        return []

    def priorite(identifiant):
        lemme, nom_propre, _ = par_article.get(identifiant, ("", True, []))
        if lemme == forme:
            rang = 0
        elif lemme.lower() == forme.lower():
            rang = 1
        else:
            rang = 2
        return (rang, 1 if nom_propre else 0)

    return sorted(identifiants, key=priorite)


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
    identifiants = articles_tries(forme, par_graphie, par_graphie_min, par_article)
    if not identifiants:
        return None

    gloses = []
    for identifiant in identifiants:
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
    exemples_par_article = lire_exemples(xml_articles)
    par_graphie = lire_graphies(xml_index)
    par_graphie_min = {}
    for forme, identifiants in par_graphie.items():
        par_graphie_min.setdefault(forme.lower(), []).extend(identifiants)

    print("\n🔗 Appariement")
    table = OrderedDict()
    occurrences_glosees = 0
    occurrences_totales = 0
    article_de = {}
    for forme, frequence in dictionnaire:
        occurrences_totales += frequence
        gloses = gloser(forme, par_graphie, par_graphie_min, par_article)
        if gloses:
            table[forme] = ", ".join(gloses)
            occurrences_glosees += frequence
            tries = articles_tries(forme, par_graphie, par_graphie_min, par_article)
            if tries:
                article_de[forme] = tries[0]

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
            tries = articles_tries(forme, par_graphie, par_graphie_min, par_article)
            if tries:
                article_de[forme] = tries[0]
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

    # Les familles : une entrée du Wierderbuch par article du LOD, et non par
    # forme. Sans elles, chercher « manger » remplit l'écran de « iessen »,
    # « iesse », « giess », « ësst » — quarante lignes pour neuf mots.
    #
    # Le représentant est le lemme de l'article quand il figure dans la famille,
    # sinon la forme la plus courte. Le lemme d'abord parce que c'est l'entrée
    # que le LOD lui-même affiche, et qu'un pluriel en tête de liste se lit
    # comme une faute.
    par_article_formes = OrderedDict()
    for forme, identifiant in article_de.items():
        par_article_formes.setdefault(identifiant, []).append(forme)

    familles = OrderedDict()
    representant_de_article = {}
    for identifiant, formes in par_article_formes.items():
        lemme = par_article.get(identifiant, ("", True, []))[0]
        if lemme in formes:
            representant = lemme
        else:
            minuscules = {f.lower(): f for f in formes}
            representant = minuscules.get(lemme.lower()) or \
                min(formes, key=lambda f: (len(f), f))
        # Retenu même pour un article à forme unique : c'est la clé sous
        # laquelle la fiche cherchera ses exemples, et le Wierderbuch affiche
        # alors cette forme-là sans qu'elle constitue une famille.
        representant_de_article[identifiant] = representant
        if len(formes) < 2:
            continue
        autres = sorted(f for f in formes if f != representant)
        familles[representant] = " ".join(autres)

    formes_groupees = sum(1 + v.count(" ") + 1 for v in familles.values()) if familles else 0
    print(f"   👪 {len(familles)} familles regroupant {formes_groupees} formes "
          f"(sur {len(table)} glosées)")

    # Les exemples sont indexés par la forme que la fiche affiche — le
    # représentant de la famille, ou la forme elle-même quand elle est seule —
    # et jamais par flexion : dupliquer les deux phrases de « Haus » sous
    # « Haiser », « Haus », « Haus' » triplerait l'actif pour un contenu
    # identique, alors que la recherche remonte déjà de la flexion au
    # représentant.
    exemples = OrderedDict()
    for identifiant, representant in representant_de_article.items():
        phrases = exemples_par_article.get(identifiant)
        if phrases and representant not in exemples:
            exemples[representant] = phrases
    couverture = 100 * len(exemples) / max(1, len(representant_de_article))
    print(f"   💬 {len(exemples)} mots illustrés d'au moins une phrase "
          f"({couverture:.1f} % des articles atteints)")

    # L'identifiant d'article du LOD, pour le bouton « Voir sur le
    # dictionnaire officiel ». Il faut l'embarquer parce que lod.lu ne peut
    # pas être atteint par une recherche : sa route /sich/<langue>/<mot> émet
    # sa recherche sur un bus d'événements au montage du composant, et sur une
    # ouverture à froid — ce que fait un lien venu d'ailleurs, à chaque fois —
    # personne n'écoute encore. La page tombe alors sur « proposez ce mot »,
    # y compris pour « Haus ». La route /artikel/<id>, elle, est rendue par
    # leur serveur et arrive directement sur l'article.
    #
    # Indexé par la forme que la fiche affiche, comme les exemples : c'est
    # `Resultat.mot` que le bouton passera. Le premier article gagne quand
    # deux se partagent un représentant — c'est celui qu'`articles_tries` a
    # classé en tête, donc celui dont la fiche montre la glose.
    articles = OrderedDict()
    for identifiant, representant in representant_de_article.items():
        if representant not in articles:
            articles[representant] = identifiant

    if arguments.strict:
        if len(familles) < 10000:
            print(f"❌ --strict : seulement {len(familles)} familles, "
                  "le regroupement du Wierderbuch serait inopérant")
            return 1
        if part_formes < 40:
            print(f"❌ --strict : couverture des formes tombée à {part_formes:.1f} %")
            return 1
        if len(exemples) < 10000:
            print(f"❌ --strict : seulement {len(exemples)} mots illustrés, "
                  "les fiches du Wierderbuch seraient sans exemple")
            return 1
        if len(articles) < 20000:
            print(f"❌ --strict : seulement {len(articles)} identifiants "
                  "d'article, le bouton lod.lu retomberait sur une recherche "
                  "qui ne donne rien")
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

    # Actif séparé, comme luxemburgish_lod_forms.json : seul le champ de
    # recherche du Wierderbuch s'en sert. Le fondre dans les traductions
    # ferait analyser un mégaoctet de plus à l'ouverture de l'onglet des jeux,
    # qui n'en a aucun usage.
    contenu_familles = {
        "version": contenu["version"],
        "generated": contenu["generated"],
        "source": contenu["source"],
        "licence": contenu["licence"],
        "attribution": ATTRIBUTION,
        "count": len(familles),
        "familles": familles,
    }
    sauvegarder_precedent(CHEMIN_FAMILLES)
    CHEMIN_FAMILLES.write_text(
        json.dumps(contenu_familles, ensure_ascii=False, indent=None,
                   separators=(",", ":")),
        encoding="utf-8")
    taille = CHEMIN_FAMILLES.stat().st_size / 1024
    print(f"💾 {CHEMIN_FAMILLES.name} — {taille:.0f} Ko")

    # Troisième actif séparé, et pour la même raison que les familles : seule
    # la fiche d'un mot ouvre ce fichier, une fois qu'on a touché un résultat.
    # Le fondre dans les traductions ferait analyser ces phrases à l'ouverture
    # de l'onglet des jeux, du mot du jour et des mots à découvrir, qui n'en
    # montrent aucune.
    contenu_exemples = {
        "version": contenu["version"],
        "generated": contenu["generated"],
        "source": contenu["source"],
        "licence": contenu["licence"],
        "attribution": ATTRIBUTION,
        "count": len(exemples),
        "exemples": exemples,
    }
    sauvegarder_precedent(CHEMIN_EXEMPLES)
    CHEMIN_EXEMPLES.write_text(
        json.dumps(contenu_exemples, ensure_ascii=False, indent=None,
                   separators=(",", ":")),
        encoding="utf-8")
    taille = CHEMIN_EXEMPLES.stat().st_size / 1024
    print(f"💾 {CHEMIN_EXEMPLES.name} — {taille:.0f} Ko")

    # Quatrième actif séparé : la fiche ne l'ouvre que si l'on touche « Voir
    # sur le dictionnaire officiel ». Il est aussi le seul dont le contenu
    # n'est pas du texte lisible, ce qui le rend inutile partout ailleurs.
    contenu_ids = {
        "version": contenu["version"],
        "generated": contenu["generated"],
        "source": contenu["source"],
        "licence": contenu["licence"],
        "attribution": ATTRIBUTION,
        "count": len(articles),
        "articles": articles,
    }
    sauvegarder_precedent(CHEMIN_LOD_IDS)
    CHEMIN_LOD_IDS.write_text(
        json.dumps(contenu_ids, ensure_ascii=False, indent=None,
                   separators=(",", ":")),
        encoding="utf-8")
    taille = CHEMIN_LOD_IDS.stat().st_size / 1024
    print(f"💾 {CHEMIN_LOD_IDS.name} — {taille:.0f} Ko")
    print("✅ Terminé")
    return 0


if __name__ == "__main__":
    sys.exit(main())
