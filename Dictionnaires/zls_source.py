#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
🇱🇺 Corpus de traduction du ZLS — accès et mise en cache
========================================================

Le **Méisproochegen Iwwersetzungskorpus fir d'Lëtzebuergescht** (Tech-in-GOV
2025), publié par le Zenter fir d'Lëtzebuerger Sprooch sur data.public.lu :
10 807 segments, ~153 600 mots luxembourgeois, traduits par des professionnels
en français, allemand et anglais, et **orthographiquement standardisés**.

Licence **CC0 1.0** — aucune obligation, contrairement à LuxAlign (CC BY-NC) et
à LETZ (CC BY). Le ZLS est crédité quand même, comme pour le LOD.

Deux usages, et il faut choisir
-------------------------------

Ce corpus peut servir de **corpus d'entraînement** ou de **jeu d'évaluation**,
mais pas les deux : l'y verser détruirait ce qui en fait sa valeur.

Mesuré le 2026-09-04, son recouvrement avec LuxAlign + LETZ n'est que de
**6,84 %** — 10 068 segments réellement inédits, contre 312 phrases pour
ParaLux, le seul jeu inédit dont le projet disposait. Cinquante fois plus
d'événements de frappe, ce qui ramène le bruit statistique de ±0,8 point à
±0,1.

L'URL n'est pas figée : le ZLS republie sous un chemin horodaté et laisse les
anciens en ligne, comme pour le LOD. On demande donc à l'API de data.public.lu
la ressource la plus récente. L'archive (5,4 Mo) est mise en cache hors du
dépôt, sous `Dictionnaires/zls_data/`.

Fait avec ❤️ pour préserver le Luxembourgeois
"""

import io
import json
import re
import unicodedata
import urllib.request
import zipfile
from pathlib import Path

RACINE = Path(__file__).resolve().parent
DOSSIER_CACHE = RACINE / "zls_data"
SLUG = "meisproochegen-iwwersetzungskorpus-fir-dletzebuergescht"
API = f"https://data.public.lu/api/1/datasets/{SLUG}/"
# La ressource quadrilingue : un seul fichier porte les quatre langues alignées.
TITRE_RESSOURCE = "LU-FR-DE-EN"

ATTRIBUTION = (
    "Méisproochegen Iwwersetzungskorpus fir d'Lëtzebuergescht (Tech-in-GOV "
    "2025), Zenter fir d'Lëtzebuerger Sprooch · data.public.lu · licence CC0 1.0"
)


def _ressource_la_plus_recente():
    with urllib.request.urlopen(API, timeout=90) as reponse:
        jeu = json.load(reponse)
    candidates = [r for r in jeu["resources"]
                  if TITRE_RESSOURCE in (r.get("title") or "")]
    if not candidates:
        raise RuntimeError(f"aucune ressource « {TITRE_RESSOURCE} » dans {SLUG}")
    return max(candidates, key=lambda r: r.get("created_at") or "")


def telecharger(hors_ligne=False):
    """L'archive du corpus, mise en cache hors du dépôt."""
    DOSSIER_CACHE.mkdir(parents=True, exist_ok=True)
    caches = sorted(DOSSIER_CACHE.glob("*.zip"))
    if caches and hors_ligne:
        return caches[-1]
    if hors_ligne:
        raise SystemExit(f"❌ cache absent dans {DOSSIER_CACHE} et --hors-ligne demandé")
    ressource = _ressource_la_plus_recente()
    horodatage = (ressource.get("created_at") or "")[:10].replace("-", "")
    chemin = DOSSIER_CACHE / f"lu-fr-de-en-{horodatage}.zip"
    if chemin.exists():
        return chemin
    urllib.request.urlretrieve(ressource["url"], chemin)
    return chemin


def segments(hors_ligne=False):
    """Les segments du corpus, dans l'ordre du fichier.

    Renvoie des dictionnaires `{id, lb, fr, de, en}`. Le JSONL est lu depuis
    l'archive sans la déployer : 4,3 Mo décompressés qu'il est inutile de poser
    sur le disque.
    """
    chemin = telecharger(hors_ligne)
    with zipfile.ZipFile(chemin) as archive:
        noms = [n for n in archive.namelist() if n.endswith(".jsonl")]
        if not noms:
            raise RuntimeError(f"aucun .jsonl dans {chemin.name}")
        with archive.open(noms[0]) as flux:
            texte = io.TextIOWrapper(flux, encoding="utf-8")
            return [json.loads(ligne) for ligne in texte if ligne.strip()]


def cle_de_comparaison(phrase):
    """Clé de recouvrement : casse, accents composés et ponctuation ignorés.

    Sert à retirer du jeu d'évaluation les segments qui figurent déjà dans le
    corpus d'entraînement. Sans elle, on mesurerait le modèle sur des phrases
    qu'il a apprises et on publierait un chiffre flatteur.
    """
    return re.sub(r"[^\w]+", "", unicodedata.normalize("NFC", phrase.lower()))


def segments_inedits(phrases_entrainement, hors_ligne=False):
    """Les segments absents du corpus d'entraînement fourni."""
    connues = {cle_de_comparaison(p) for p in phrases_entrainement}
    return [s for s in segments(hors_ligne)
            if cle_de_comparaison(s["lb"]) not in connues]


if __name__ == "__main__":
    tous = segments()
    mots = sum(len(re.findall(r"[^\W\d_]+", s["lb"], re.UNICODE)) for s in tous)
    print(f"{len(tous)} segments · {mots} mots luxembourgeois")
    print(f"langues : {sorted(set(tous[0]) - {'id'})}")
