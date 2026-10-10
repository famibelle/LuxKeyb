#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Les séries du carnet : quels dessins peuvent réellement se gagner.

Produit `android_keyboard/app/src/main/assets/luxemburgish_series.json`, lu par
`carnet/Series.kt`.

    python3 Dictionnaires/generate_series.py

## Ce que ce fichier garantit

Une série est une page d'album à emplacements vides. Elle n'a de sens que si
chaque emplacement peut se remplir : une série impossible à finir est pire que
pas de série, le joueur croit qu'il lui manque quelque chose alors que le jeu
le lui refuse. Voir `GAMIFICATION-CARNET.md`, section 2.

La série des enluminures compte donc **les dessins dont au moins un mot peut
sortir d'un jeu, dans chacune des quatre langues de glose**, et rien d'autre.
Un mot se gagne quand une forme de sa famille est :

- dans une grille de Kräizwuert ou de Wuertplaz de la langue ;
- une réponse de Wuertlück ;
- ou dans la réserve de tirage de Wuertsich (3 à 8 lettres), Wuertmix (4 à 10)
  ou Wuertriet (5) : forme du dictionnaire, glose instructive dans la langue,
  ni nom propre, ni fragment, ni mot écarté par `MotsEcartes.kt`.

C'est la règle de `TranslationDictionary.proposables`, relue ici. La glose
change avec la langue de l'interface, et la grille aussi : un dessin
atteignable en français peut ne pas l'être en allemand (« Pizza » se glose
« Pizza »). L'intersection est la seule série que l'on peut promettre à tout le
monde.

## Pourquoi un actif à part, et pas une clé de plus dans les blasons

`luxemburgish_blasons.json` a été produit avant que les grilles n'existent en
quatre langues, et `generate_blasons.py` relancé aujourd'hui reclasse environ
trois cents mots dans un autre champ : la couleur de leur carte changerait.
Ajouter une clé à cet actif obligerait à le régénérer, donc à repeindre des
cartes que personne n'a demandé de repeindre. Ce fichier-ci ne lit les blasons
que pour savoir quel lemme porte quel dessin.

## Les dessins écartés

Ils restent dessinés (`Meubles.kt` n'est pas touché) et une carte qui les
porterait les montrerait toujours ; ils sont seulement hors de la série. Le
fichier les liste avec leur cause, pour qu'on ne les cherche pas.
"""

import json
import os
import re
import sys
import unicodedata
from datetime import date

RACINE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ACTIFS = os.path.join(RACINE, 'android_keyboard', 'app', 'src', 'main', 'assets')
KOTLIN = os.path.join(RACINE, 'android_keyboard', 'app', 'src', 'main', 'java',
                      'com', 'example', 'kreyolkeyboard')
SORTIE = os.path.join(ACTIFS, 'luxemburgish_series.json')

LANGUES = ('fr', 'de', 'en', 'pt')

# Plancher de la série : en dessous, un générateur de grilles ou un filtre a
# fait disparaître des pans entiers du vivier, et c'est à voir avant de livrer.
MINIMUM = 100


def actif(nom):
    with open(os.path.join(ACTIFS, nom), encoding='utf-8') as f:
        return json.load(f)


def suffixe(langue):
    return '' if langue == 'fr' else f'_{langue}'


def plier(texte):
    """`AccentTolerantMatcher.normalize`, à ß près (absent du dictionnaire)."""
    return ''.join(c for c in unicodedata.normalize('NFD', texte.lower())
                   if unicodedata.category(c) != 'Mn')


def mots_ecartes():
    """Les quatre listes de `MotsEcartes.FORMES`, lues dans le Kotlin.

    `SUJETS` n'en fait pas partie : il n'écarte que des phrases, pas des mots.
    """
    with open(os.path.join(KOTLIN, 'MotsEcartes.kt'), encoding='utf-8') as f:
        source = f.read()
    formes = set()
    for nom in ('RELIGION', 'PARTIS', 'REGISTRE', 'GROSSIERETES'):
        bloc = re.search(r'private val %s = listOf\((.*?)\n    \)' % nom, source, re.S)
        if not bloc:
            sys.exit(f'MotsEcartes.kt : liste {nom} introuvable')
        formes |= {plier(m) for m in re.findall(r'"([^"]+)"', bloc.group(1))}
    return formes


def glose_instructive(mot, glose):
    plie = plier(mot)
    return any(plier(s.strip()) != plie for s in glose.split(','))


def jeux_par_langue(langue, dico, ecartes, reponses_cloze):
    """Les formes que l'un des sept jeux peut faire gagner, dans [langue]."""
    t = actif(f'luxemburgish_translations{suffixe(langue)}.json')
    traductions = t['translations']
    exclus = set(t.get('noms_propres', [])) | set(t.get('fragments', []))

    gagnables = set(reponses_cloze)
    for nom in ('luxemburgish_crossword', 'luxemburgish_chassecroise'):
        for grille in actif(f'{nom}{suffixe(langue)}.json')['grilles']:
            gagnables.update(m['f'] for m in grille['mots'])

    for forme in dico:
        n = len(forme)
        if not (3 <= n <= 10):
            continue
        glose = traductions.get(forme)
        if not glose or forme in exclus or plier(forme) in ecartes:
            continue
        if glose_instructive(forme, glose):
            gagnables.add(forme)
    # Les grilles et Wuertlück passent déjà le filtre de neutralité au
    # chargement : un mot écarté n'en sort jamais, d'où qu'il vienne.
    return {f for f in gagnables if plier(f) not in ecartes}


def main():
    blasons = actif('luxemburgish_blasons.json')['blasons']
    dessin_de = {lemme: e['m'] for lemme, e in blasons.items() if 'm' in e}

    familles = actif('luxemburgish_familles.json')['familles']
    dico = [forme for forme, _ in actif('luxemburgish_dict.json')]
    ecartes = mots_ecartes()
    reponses_cloze = {it['a'] for it in actif('luxemburgish_cloze.json')['items']}

    par_langue = {l: jeux_par_langue(l, dico, ecartes, reponses_cloze) for l in LANGUES}

    atteints = {}   # dessin -> lemmes qui le donnent partout
    causes = {}     # dessin -> langues où aucun de ses lemmes ne se gagne
    for lemme, dessin in sorted(dessin_de.items()):
        formes = {lemme} | set(str(familles.get(lemme, '')).split())
        manque = [l for l in LANGUES if not (formes & par_langue[l])]
        if manque:
            causes.setdefault(dessin, {})[lemme] = manque
        else:
            atteints.setdefault(dessin, []).append(lemme)

    serie = sorted(atteints)
    exclus = {d: c for d, c in sorted(causes.items()) if d not in atteints}

    if len(serie) < MINIMUM:
        sys.exit(f'série des enluminures : {len(serie)} dessins, sous le plancher '
                 f'de {MINIMUM}. Un vivier a fondu : voir avant de livrer.')

    sortie = {
        'version': 1,
        'generated': date.today().isoformat(),
        'langues': list(LANGUES),
        'enluminures': serie,
        'exclus': exclus,
    }
    with open(SORTIE, 'w', encoding='utf-8') as f:
        json.dump(sortie, f, ensure_ascii=False, indent=1, sort_keys=True)
        f.write('\n')

    print(f'dessins déclarés : {len(set(dessin_de.values()))}')
    print(f'série des enluminures : {len(serie)}')
    for dessin, lemmes in exclus.items():
        detail = ', '.join(f'{l} ({"/".join(ls)})' for l, ls in lemmes.items())
        print(f'  hors série : {dessin:14} {detail}')


if __name__ == '__main__':
    main()
