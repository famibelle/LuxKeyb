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
lecture de l'index. Il lit aussi les phrases d'exemple que
`LuxembourgishComplet.py` verse au corpus et que
`docs/scripts/generate_corpus_stats.py` mesure : une seule copie des règles
d'exclusion, pour que les deux ne divergent pas.

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
import re
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

DOSSIER_CACHE = Path(__file__).resolve().parent / "luxemburgish_data" / "lod"

API_DATASETS = "https://data.public.lu/api/1/datasets/{slug}/"

# Les trois jeux de données du ZLS dont on a besoin. Le premier porte les
# traductions, le second la liste des graphies qui mènent à chaque article, le
# troisième les tables de flexion — et avec elles les étiquettes grammaticales
# que les articles ne donnent pas.
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
    "tab": {
        "slug": "letzebuerger-online-dictionnaire-lod-flexiounstabellen",
        "fichier": "new_lod-tab.xml",
        "libelle": "LOD — Flexiounstabellen (tables de flexion)",
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


# ---------------------------------------------------------------------------
# Les phrases d'exemple, comme corpus du dictionnaire et des n-grammes
# ---------------------------------------------------------------------------
#
# Le LOD porte 58 962 phrases d'exemple, écrites par le ZLS pour montrer
# comment un mot s'emploie. C'est la langue de tous les jours que la presse
# n'écrit pas : « wëlls de », « kanns du », « looss dech ». LETZ, au corpus
# depuis le début, n'en était qu'une sélection de 5 862.
#
# Mesuré le 2026-11-20 (`mesure_exemples_lod.py`,
# `reports/exemples_lod_ngrammes_2026-11-20.md`) : top-3 du corpus du ZLS de
# 18,1 à 19,6 %, +0,3 point sur sa partie Chambre et presse, deuxième personne
# de 14 à 25 %, ParaLux inchangé. Trois règles en sortent, appliquées ici pour
# que le pipeline et la page corpus ne puissent pas diverger.

# VULG et PEJ : l'application ne montre jamais ces registres. VEREELZT
# (vieilli) : un clavier ne doit pas proposer un usage sorti de la langue.
# EGS (emploi figuré) reste : c'est de la langue réelle, seulement imagée.
MARQUES_EXEMPLE_ECARTEES = {"VULG", "PEJ", "VEREELZT"}

# Ressemblance au-delà de laquelle un exemple est tenu pour un segment du jeu
# d'évaluation retouché. Le ZLS a standardisé son corpus : « Kannapee » y est
# devenu « Canapé », « Gezei » a disparu. Une clé exacte laisserait fuir ces
# phrases, que le modèle aurait apprises avant d'être interrogé dessus.
SEUIL_QUASI_DOUBLON = 0.9

_MOT_SIMPLE = re.compile(r"[^\W\d_]+")


def _recoller(texte):
    """Phrase d'un `<text>` d'exemple, et positions de ses mots vedettes.

    Le LOD ne stocke pas de phrase mais la suite de ses mots, le mot illustré
    balisé à part (`<inflectedHeadword>`). Pas d'espace après une élision
    (« d' »), ni avant une ponctuation isolée — même règle que
    `generate_translations._phrase`.
    """
    morceaux, vedettes, longueur = [], [], 0
    for enfant in texte:
        if enfant.tag not in ("word", "inflectedHeadword"):
            continue
        mot = (enfant.text or "").strip()
        if not mot:
            continue
        if morceaux and not morceaux[-1].endswith(("'", "’")) \
                and mot[0] not in ",.!?;:)»…":
            morceaux.append(" ")
            longueur += 1
        if enfant.tag == "inflectedHeadword":
            vedettes.append((longueur, longueur + len(mot)))
        morceaux.append(mot)
        longueur += len(mot)
    return "".join(morceaux), vedettes


def phrases_exemple(xml_articles=None, verbeux=True):
    """Les phrases d'exemple distinctes : [(phrase, [(début, fin) des vedettes])].

    Une phrase par clé de comparaison : un même exemple sert parfois à
    plusieurs articles (le même `id` revient jusqu'à sept fois), et le compter
    sept fois le pondérerait arbitrairement. L'ordre est celui du fichier.
    """
    from zls_source import cle_de_comparaison

    if xml_articles is None:
        xml_articles = telecharger_source("art", verbeux=verbeux)
    vues, phrases, ecartees = set(), [], 0
    for _, entree in ET.iterparse(io.BytesIO(xml_articles), events=("end",)):
        if entree.tag != "entry":
            continue
        for exemple in entree.iter("example"):
            texte = exemple.find("text")
            if texte is None:
                continue
            marques = {(e.text or "").strip() for e in texte if e.tag == "attribute"}
            if marques & MARQUES_EXEMPLE_ECARTEES:
                ecartees += 1
                continue
            phrase, vedettes = _recoller(texte)
            cle = cle_de_comparaison(phrase)
            if len(_MOT_SIMPLE.findall(phrase)) < 2 or cle in vues:
                continue
            vues.add(cle)
            phrases.append((phrase, vedettes))
        entree.clear()
    if verbeux:
        print(f"   💬 {len(phrases)} phrases d'exemple distinctes "
              f"({ecartees} écartées : {', '.join(sorted(MARQUES_EXEMPLE_ECARTEES))})")
    return phrases


def _quasi_doublons(exemples, segments):
    """Indices des exemples presque identiques à un segment d'évaluation.

    Index sur les trigrammes de mots, puis difflib sur les rares candidats :
    comparer 50 000 phrases à 10 000 deux à deux prendrait une heure.
    """
    import difflib

    def mots(p):
        return [m.lower() for m in _MOT_SIMPLE.findall(p)]

    index, textes = {}, []
    for i, (phrase, _) in enumerate(exemples):
        m = mots(phrase)
        textes.append(" ".join(m))
        for tri in set(zip(m, m[1:], m[2:])):
            index.setdefault(tri, set()).add(i)
    trouves = set()
    for segment in segments:
        m = mots(segment)
        tris = set(zip(m, m[1:], m[2:]))
        if not tris:
            continue
        votes = {}
        for tri in tris:
            for i in index.get(tri, ()):
                votes[i] = votes.get(i, 0) + 1
        texte = " ".join(m)
        # Départage par indice : sans lui, l'ordre des ex æquo suit celui d'un
        # set de chaînes, qui change à chaque lancement (PYTHONHASHSEED), et
        # le pipeline et la page corpus n'écartaient pas les mêmes phrases.
        for i, n in sorted(votes.items(), key=lambda kv: (-kv[1], kv[0]))[:5]:
            if n < max(1, len(tris) // 2):
                break
            if difflib.SequenceMatcher(None, texte, textes[i]).ratio() >= SEUIL_QUASI_DOUBLON:
                trouves.add(i)
                break
    return trouves


def exemples_pour_entrainement(phrases_deja_vues, segments_evaluation, verbeux=True):
    """Les exemples du LOD à verser au corpus, une fois le jeu d'évaluation ôté.

    `segments_evaluation` : les segments luxembourgeois du corpus de traduction
    du ZLS (`zls_source.segments()`). **Obligatoire** : 5 715 de ses 10 807
    segments sont des exemples du LOD. Les apprendre ferait gagner 0,8 point de
    top-3 qui n'existe pas, et le filtre de `generate_corpus_stats.py`
    amputerait en silence la moitié du jeu publié.

    `phrases_deja_vues` : le reste du corpus. LETZ reprend 94,8 % de ses
    phrases au LOD, souvent avec une autre ponctuation : la comparaison se fait
    par clé, sinon ces phrases compteraient deux fois.

    Renvoie [(phrase, [(début, fin) des mots vedettes])].
    """
    from zls_source import cle_de_comparaison

    exemples = phrases_exemple(verbeux=verbeux)
    cles_evaluation = {cle_de_comparaison(s) for s in segments_evaluation}
    hors_evaluation = [e for e in exemples
                       if cle_de_comparaison(e[0]) not in cles_evaluation]
    exacts = len(exemples) - len(hors_evaluation)
    proches = _quasi_doublons(hors_evaluation, segments_evaluation)
    hors_evaluation = [e for i, e in enumerate(hors_evaluation) if i not in proches]
    cles_vues = {cle_de_comparaison(p) for p in phrases_deja_vues}
    retenus = [e for e in hors_evaluation if cle_de_comparaison(e[0]) not in cles_vues]
    if verbeux:
        print(f"   🚫 {exacts} exemples du jeu d'évaluation du ZLS écartés, "
              f"et {len(proches)} quasi-doublons")
        print(f"   ♻️ {len(hors_evaluation) - len(retenus)} déjà au corpus (LETZ)")
        print(f"   ✅ {len(retenus)} phrases d'exemple retenues")
    return retenus
