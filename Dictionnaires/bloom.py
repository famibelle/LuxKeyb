#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Filtre de Bloom — construction, partagée par les générateurs d'actifs.

Le clavier s'en sert pour répondre à « ce mot existe-t-il ? », la seule
question que pose le correcteur orthographique. Le choix tient à l'asymétrie
des erreurs d'un filtre de Bloom :

- il ne peut **jamais** rejeter une forme qu'on y a mise, donc il ne peut pas
  faire souligner un mot correct — le défaut que le correcteur existe pour
  éviter ;
- il accepte à tort environ 1 % des chaînes absentes, donc il laisse passer une
  faute de temps en temps, ce qui est sans conséquence.

En échange, 150 000 formes tiennent dans ~175 Ko au lieu de plusieurs Mo de
chaînes et de tables de hachage, dans un processus de saisie qu'Android
compresse en swap dès qu'il passe en arrière-plan.

Le hachage est **écrit deux fois**, ici et dans `BloomFilter.kt`. Une
divergence ferait souligner toute une langue d'un coup sans rien casser
d'autre : chaque actif porteur d'un filtre est donc accompagné d'un test qui
rejoue le filtre livré sur les formes livrées.
"""

import math

FNV_OFFSET = 0xCBF29CE484222325
FNV_PRIME = 0x100000001B3
MASQUE64 = 0xFFFFFFFFFFFFFFFF
MASQUE63 = 0x7FFFFFFFFFFFFFFF
FAUX_POSITIFS = 0.01


def fnv1a(donnees):
    """FNV-1a 64 bits — dix lignes, réimplémentables à l'identique en Kotlin."""
    h = FNV_OFFSET
    for octet in donnees:
        h = ((h ^ octet) * FNV_PRIME) & MASQUE64
    return h


def dimensionner(nombre, faux_positifs=FAUX_POSITIFS):
    """Nombre de bits et de hachages optimaux.

    Le nombre de bits est rendu **impair**, et [indices] force le second
    hachage impair : sans ces deux précautions, la construction de
    Kirsch-Mitzenmacher fait partager leur parité aux k indices d'un même mot,
    qui ne couvrent alors que la moitié du filtre. Mesuré : 3,42 % de faux
    positifs au lieu de 1,03 %, pour la même taille.
    """
    bits = int(-nombre * math.log(faux_positifs) / (math.log(2) ** 2))
    if bits % 2 == 0:
        bits += 1
    return bits, max(1, round(bits / nombre * math.log(2)))


def indices(mot, bits, hachages):
    """Kirsch-Mitzenmacher : deux hachages en simulent k.

    Le masque 63 bits plutôt qu'un modulo non signé existe pour Kotlin, dont
    les Long sont signés et dont `remainderUnsigned` n'arrive qu'à l'API 24,
    sous le minSdk 21 du projet.
    """
    donnees = mot.encode("utf-8")
    h1 = fnv1a(donnees)
    h2 = fnv1a(b"\x00" + donnees) | 1
    return [((h1 + i * h2) & MASQUE63) % bits for i in range(hachages)]


def construire(formes, faux_positifs=FAUX_POSITIFS):
    """Le filtre, ses bits et son nombre de hachages."""
    formes = list(formes)
    bits, hachages = dimensionner(len(formes), faux_positifs)
    tableau = bytearray((bits + 7) // 8)
    for mot in formes:
        for indice in indices(mot, bits, hachages):
            tableau[indice >> 3] |= 1 << (indice & 7)
    return tableau, bits, hachages


def contient(tableau, bits, hachages, mot):
    return all(tableau[i >> 3] & (1 << (i & 7)) for i in indices(mot, bits, hachages))


def mesurer_faux_positifs(tableau, bits, hachages, connues, echantillons=20000,
                          graine=20260904):
    """Taux mesuré et non calculé : c'est le filtre livré qu'on interroge."""
    import random
    alea = random.Random(graine)
    lettres = "abcdefghijklmnopqrstuvwxyzéèêàôùïüçäë"
    faux = essais = 0
    while essais < echantillons:
        mot = "".join(alea.choice(lettres) for _ in range(alea.randint(4, 12)))
        if mot in connues:
            continue
        essais += 1
        if contient(tableau, bits, hachages, mot):
            faux += 1
    return faux / essais
