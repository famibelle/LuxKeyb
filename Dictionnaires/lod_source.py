#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
🇱🇺 LOD — accès partagé aux données du Lëtzebuerger Online Dictionnaire
=======================================================================

Le dictionnaire officiel du **Zenter fir d'Lëtzebuerger Sprooch**, publié en
**CC0 1.0** sur data.public.lu. C'est la seule source du projet qui n'impose
rien : les corpus LuxAlign (CC BY-NC) et LETZ (CC BY) exigent, eux, une
attribution. On crédite quand même le ZLS — c'est leur travail.

Ce module ne produit aucun actif. Il porte ce que `generate_translations.py`
(les gloses) et `generate_lod_forms.py` (les formes) avaient tous les deux
besoin de savoir, pour qu'une seule copie décide de l'URL, du cache et de la
lecture de l'index.

Deux choses à ne pas refaire autrement :

- **L'URL n'est pas codée en dur.** Le ZLS republie chaque trimestre sous un
  chemin horodaté et laisse l'ancien en ligne. On demande à l'API de
  data.public.lu la ressource la plus récente, sinon on gloserait et on
  compléterait avec l'édition d'il y a deux ans, sans que rien ne le signale.
- **Le cache vit hors du dépôt** (`Dictionnaires/luxemburgish_data/lod/`) :
  188 Mo de XML n'ont rien à faire dans un historique git, a fortiori dans un
  historique partagé avec KreyolKeyb.
"""

import io
import json
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

DOSSIER_CACHE = Path(__file__).resolve().parent / "luxemburgish_data" / "lod"

API_DATASETS = "https://data.public.lu/api/1/datasets/{slug}/"

# Les deux jeux de données du ZLS dont on a besoin. Le premier porte les
# traductions, le second la liste des graphies qui mènent à chaque article.
SOURCES_LOD = {
    "art": {
        "slug": "letzebuerger-online-dictionnaire-lod-linguistesch-daten",
        "fichier": "new_lod-art.xml",
        "libelle": "LOD — Linguistesch Daten (articles)",
    },
    "search": {
        "slug": "letzebuerger-online-dictionnaire-lod-index-vun-der-sich-funktioun",
        "fichier": "new_lod-search.xml",
        "libelle": "LOD — Index vun der Sich-Funktioun (graphies)",
    },
}

ATTRIBUTION = [
    "Lëtzebuerger Online Dictionnaire (LOD) — Zenter fir d'Lëtzebuerger Sprooch",
    "https://lod.lu · data.public.lu · licence CC0 1.0",
]


def telecharger_source(cle, hors_ligne=False, verbeux=True):
    """Renvoie le XML d'une des deux ressources LOD, depuis le cache si possible."""
    source = SOURCES_LOD[cle]
    cache = DOSSIER_CACHE / source["fichier"]

    if cache.exists():
        if verbeux:
            taille = cache.stat().st_size / 1_048_576
            print(f"   📁 cache : {cache.name} ({taille:.1f} Mo)")
        return cache.read_bytes()

    if hors_ligne:
        raise RuntimeError(
            f"{source['fichier']} absent du cache et mode hors ligne demandé")

    url_api = API_DATASETS.format(slug=source["slug"])
    if verbeux:
        print(f"   🌐 {source['libelle']}")
    with urllib.request.urlopen(url_api, timeout=60) as reponse:
        metadonnees = json.load(reponse)

    ressources = [r for r in metadonnees.get("resources", [])
                  if (r.get("format") or "").lower() == "zip"]
    if not ressources:
        raise RuntimeError(f"aucune archive ZIP publiée pour {source['slug']}")
    ressource = max(ressources, key=lambda r: r.get("last_modified") or "")
    if verbeux:
        print(f"      {ressource['title']} — {ressource.get('last_modified', '?')[:10]}")

    with urllib.request.urlopen(ressource["url"], timeout=600) as reponse:
        archive = reponse.read()

    with zipfile.ZipFile(io.BytesIO(archive)) as zf:
        noms = [n for n in zf.namelist() if n.endswith(".xml")]
        if not noms:
            raise RuntimeError(f"pas de XML dans {ressource['title']}")
        contenu = zf.read(noms[0])

    DOSSIER_CACHE.mkdir(parents=True, exist_ok=True)
    cache.write_bytes(contenu)
    if verbeux:
        print(f"      → {len(contenu) / 1_048_576:.1f} Mo mis en cache")
    return contenu


# Une graphie de l'index porte soit suggest="true", soit suggest="false" avec
# un `reason` qui dit pourquoi le LOD ne la propose pas de lui-même. Ces
# catégories ne se valent pas du tout, et les confondre livrerait une bouillie
# de translittérations ASCII et de paradigmes générés par règle :
#
#   suggest=true         103 688 formes — les graphies que le LOD assume.
#   n-rule                29 649 — variantes de la règle d'Eifel (« Ae » devant
#                         consonne). Parfaitement correctes en contexte : les
#                         souligner en rouge serait un bug du correcteur.
#   filled-croissant      28 650 — déclinaisons complétées par règle, sans
#                         attestation (« aachtafofzegstenem »).
#   umlaut                14 236 — translittérations ASCII (« aarbechtsfaeeg »
#                         pour « aarbechtsfäeg »). Personne ne tape ça.
#   withUnverifiedArticle 11 049 — formes agglutinées à l'article (« d'Aacht »).
#   adj-to-Subst-es        8 552 — substantivations neutres régulières.
#   PPresent               5 881 — participes présents.
#   allegro                4 495 — contractions de l'oral (« rabruecht »).
#   erroneous-spelling     1 016 — fautes répertoriées. À bannir des deux côtés.
CATEGORIE_PROPOSABLE = "suggest"

# Ce qui entre dans le dictionnaire du clavier, et ce qui n'est que « connu ».
# Le second palier n'est jamais proposé : il sert uniquement à ne pas souligner
# en rouge une forme correcte.
CATEGORIES_SUGGEREES = (CATEGORIE_PROPOSABLE,)
CATEGORIES_CONNUES = ("n-rule",)


def _admissible(forme):
    """Une graphie qu'un clavier peut avoir à compléter.

    On écarte les locutions (« virun Ae féieren » : le moteur ne complète que
    des mots), ce qui commence par autre chose qu'une lettre (« 'brut », « $ »,
    « 3D-Drucker ») et les formes d'une seule lettre, qui ne se complètent pas.
    """
    if len(forme) < 2 or " " in forme:
        return False
    if not forme[0].isalpha():
        return False
    return all(c.isalpha() or c in "-'" for c in forme)


def graphies_par_categorie(xml_index, verbeux=True):
    """{catégorie: {formes}} — mono-mots admissibles de l'index de recherche.

    La catégorie vaut `suggest` pour les graphies assumées par le LOD, sinon la
    valeur de l'attribut `reason`. Une forme peut apparaître dans plusieurs
    catégories selon l'article : `suggest` l'emporte alors, et c'est voulu —
    une graphie que le LOD propose ailleurs reste proposable.
    """
    par_categorie = {}
    for _, entree in ET.iterparse(io.BytesIO(xml_index), events=("end",)):
        if entree.tag != "entry":
            continue
        graphies = entree.find("spellings")
        if graphies is not None:
            for graphie in graphies.findall("spelling"):
                forme = (graphie.text or "").strip()
                if not _admissible(forme):
                    continue
                if graphie.get("suggest") == "true":
                    categorie = CATEGORIE_PROPOSABLE
                else:
                    categorie = graphie.get("reason") or "inconnue"
                par_categorie.setdefault(categorie, set()).add(forme)
        entree.clear()

    proposables = par_categorie.get(CATEGORIE_PROPOSABLE, set())
    for categorie, formes in par_categorie.items():
        if categorie != CATEGORIE_PROPOSABLE:
            formes -= proposables

    if verbeux:
        for categorie, formes in sorted(par_categorie.items(),
                                        key=lambda kv: -len(kv[1])):
            print(f"   🔤 {categorie:<22} {len(formes):>7}")
    return par_categorie
