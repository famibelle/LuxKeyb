#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
🇱🇺 EXPLORATION — les étiquettes grammaticales des tables de flexion du LOD
============================================================================

Le pipeline actuel lit deux ressources du ZLS : les articles
(`new_lod-art.xml`, dont `generate_translations.py` tire le lemme, le
`partOfSpeech` et les gloses) et l'index de recherche (`new_lod-search.xml`,
dont `generate_lod_forms.py` tire les graphies). Aucune des deux n'a jamais
livré le **genre grammatical** d'un substantif, parce que rien n'en avait
besoin : la carte du carnet déduit la nature d'un mot de sa majuscule et de
sa finale (voir `Armorial.natureDe`), jamais d'une étiquette.

Les **Flexiounstabellen** sont la troisième ressource, et la seule qui
décrive la morphologie. Ce script ne produit aucun actif : il dépouille
l'archive pour montrer *où* le genre se range, avant qu'on écrive
l'extraction dans un vrai générateur.

    cd Dictionnaires
    python explore_lod_genre.py --zip ~/Téléchargements/260727-new-lod-tab.zip
    python explore_lod_genre.py                  # télécharge si le réseau le permet
    python explore_lod_genre.py --n 6 --contient Subst

Ce qu'il imprime, dans cet ordre :

1. le contenu de l'archive — un XML ou mille, on ne le sait pas d'avance ;
2. les premières tables, balise par balise, attributs compris ;
3. un **recensement** : chaque balise, chaque attribut, et ses valeurs
   distinctes. C'est lui qui répond à la question — une colonne de valeurs
   `m` / `f` / `n`, ou `Substantiv` / `Verb` / `Adjektiv`, se voit d'un coup.
"""

import argparse
import io
import sys
import xml.etree.ElementTree as ET
import zipfile
from collections import Counter, defaultdict
from pathlib import Path

from lod_source import telecharger_source

if sys.platform.startswith('win'):
    import codecs
    sys.stdout = codecs.getwriter('utf-8')(sys.stdout.buffer, 'strict')

# Au-delà, ce n'est plus une valeur d'étiquette mais du contenu : une liste de
# mille graphies distinctes ne nous apprend rien sur la structure.
MAX_VALEURS = 25


def xml_de_larchive(chemin):
    """Le premier XML d'une archive locale, et la liste de ce qu'elle contient."""
    with zipfile.ZipFile(chemin) as archive:
        noms = archive.namelist()
        print(f"📦 {Path(chemin).name} — {len(noms)} entrées")
        for nom in noms[:20]:
            info = archive.getinfo(nom)
            print(f"   {nom}  ({info.file_size / 1_048_576:.1f} Mo)")
        if len(noms) > 20:
            print(f"   … et {len(noms) - 20} autres")
        xmls = [n for n in noms if n.endswith(".xml")]
        if not xmls:
            raise SystemExit("❌ aucun .xml dans l'archive")
        return archive.read(xmls[0])


def decrire(element, profondeur=0, plafond=2):
    """Un enfant par ligne : balise, attributs, texte s'il tient sur la ligne."""
    for enfant in element:
        texte = (enfant.text or "").strip()
        if len(texte) > 60:
            texte = texte[:57] + "…"
        attrs = " ".join(f'{k}="{v}"' for k, v in enfant.attrib.items())
        ligne = "  " * profondeur + f"<{enfant.tag}"
        if attrs:
            ligne += f" {attrs}"
        ligne += ">"
        if texte:
            ligne += f" {texte!r}"
        print(ligne)
        if profondeur < plafond:
            decrire(enfant, profondeur + 1, plafond)


def main():
    analyseur = argparse.ArgumentParser(description=__doc__)
    analyseur.add_argument("--zip", type=Path,
                           help="archive déjà téléchargée (sinon : le cache, "
                                "sinon le réseau)")
    analyseur.add_argument("--hors-ligne", action="store_true")
    analyseur.add_argument("--n", type=int, default=8,
                           help="nombre de tables à imprimer en entier")
    analyseur.add_argument("--contient", default="",
                           help="ne garder que les tables dont le XML contient "
                                "ce texte (p. ex. Subst)")
    arguments = analyseur.parse_args()

    if arguments.zip:
        brut = xml_de_larchive(arguments.zip)
    else:
        brut = telecharger_source("tab", arguments.hors_ligne)

    racine = ET.parse(io.BytesIO(brut)).getroot()
    print(f"\n🌳 racine <{racine.tag}> — {len(racine)} enfants directs, "
          f"attributs {dict(racine.attrib)}")

    # 1. Les premières tables en entier.
    montrees = 0
    for table in racine:
        if arguments.contient:
            entier = ET.tostring(table, encoding="unicode")
            if arguments.contient not in entier:
                continue
        print("=" * 70)
        print(f"<{table.tag} {dict(table.attrib)}>")
        decrire(table)
        montrees += 1
        if montrees >= arguments.n:
            break

    # 2. Le recensement, sur tout le fichier.
    balises = Counter()
    valeurs = defaultdict(set)
    for element in racine.iter():
        balises[element.tag] += 1
        for cle, valeur in element.attrib.items():
            valeurs[f"{element.tag}@{cle}"].add(valeur)

    print("=" * 70)
    print("\n📊 BALISES")
    for balise, compte in balises.most_common():
        print(f"   {balise:<28} {compte:>8}")

    print("\n📊 ATTRIBUTS ET LEURS VALEURS")
    for cle in sorted(valeurs):
        lot = sorted(valeurs[cle])
        if len(lot) <= MAX_VALEURS:
            print(f"   {cle:<28} {len(lot):>5} : {', '.join(lot)}")
        else:
            print(f"   {cle:<28} {len(lot):>5} : {', '.join(lot[:MAX_VALEURS])} …")

    print("\nCherche dans le recensement une colonne à deux ou trois valeurs "
          "courtes (m / f / n) ou les noms des catégories (Substantiv, Verb, "
          "Adjektiv). Colle cette sortie et j'écris l'extraction.")


if __name__ == "__main__":
    sys.exit(main() or 0)
