#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
🇱🇺 EXPLORATION — où le LOD range le genre grammatical d'un substantif
========================================================================

Ce n'est **pas** un générateur d'actif : `lire_traductions()`
(`generate_translations.py`) sait déjà lire `<lemma>` et `<partOfSpeech>`
dans `new_lod-art.xml`, mais rien dans le pipeline actuel n'a jamais eu
besoin du genre (masculin / féminin / neutre), donc personne n'a vérifié
sous quel nom le LOD le range — un attribut sur `<entry>`, une valeur de
`<partOfSpeech>` du genre `Subst-M`, un élément séparé, ou une information
qu'on ne récupère qu'à travers l'article (`d'`, `den`, `d'Kand`)...

Ce script dépouille quelques dizaines de substantifs de l'article LOD et
imprime tout ce qui les entoure, pour repérer le bon champ **avant**
d'écrire l'extraction définitive dans un vrai générateur d'actif.

    cd Dictionnaires
    python explore_lod_genre.py            # télécharge (ou relit le cache)
    python explore_lod_genre.py --hors-ligne
    python explore_lod_genre.py --n 50 --contient Subst
"""

import argparse
import io
import sys
import xml.etree.ElementTree as ET

from lod_source import telecharger_source

if sys.platform.startswith('win'):
    import codecs
    sys.stdout = codecs.getwriter('utf-8')(sys.stdout.buffer, 'strict')


def decrire_entree(entree, profondeur=0):
    """Un enfant par ligne : balise, attributs, texte s'il tient sur la ligne."""
    marge = "  " * profondeur
    for enfant in entree:
        texte = (enfant.text or "").strip()
        if len(texte) > 60:
            texte = texte[:57] + "…"
        attrs = " ".join(f'{k}="{v}"' for k, v in enfant.attrib.items())
        ligne = f"{marge}<{enfant.tag}"
        if attrs:
            ligne += f" {attrs}"
        ligne += ">"
        if texte:
            ligne += f" {texte!r}"
        print(ligne)
        # Un seul niveau de plus : assez pour voir un <flection type="…">
        # sous <meaning>, pas assez pour noyer la sortie dans les exemples.
        if profondeur < 1 and len(enfant) > 0 and enfant.tag != "example":
            decrire_entree(enfant, profondeur + 1)


def main():
    analyseur = argparse.ArgumentParser(description=__doc__)
    analyseur.add_argument("--hors-ligne", action="store_true")
    analyseur.add_argument("--n", type=int, default=20,
                            help="nombre d'entrées à imprimer")
    analyseur.add_argument("--contient", default="Subst",
                            help="ne garder que les partOfSpeech contenant ce texte")
    arguments = analyseur.parse_args()

    xml_articles = telecharger_source("art", arguments.hors_ligne)

    vus = 0
    for _, entree in ET.iterparse(io.BytesIO(xml_articles), events=("end",)):
        if entree.tag != "entry":
            continue
        pos = entree.findtext(".//partOfSpeech") or ""
        if arguments.contient not in pos:
            entree.clear()
            continue

        print("=" * 70)
        print(f"id={entree.get('id')!r} lemma={entree.findtext('lemma')!r} "
              f"partOfSpeech={pos!r} attrs={dict(entree.attrib)}")
        decrire_entree(entree)
        entree.clear()

        vus += 1
        if vus >= arguments.n:
            break

    if vus == 0:
        print(f"Aucune entrée dont partOfSpeech contient {arguments.contient!r}.")
    else:
        print("=" * 70)
        print(f"\n{vus} entrées imprimées. Cherche le genre dans ce qui précède : "
              "un attribut de <entry>, une valeur de <partOfSpeech>, ou un "
              "élément propre (<flection>, <genus>…). Rapporte le nom exact "
              "trouvé pour qu'on écrive l'extraction.")


if __name__ == "__main__":
    sys.exit(main() or 0)
