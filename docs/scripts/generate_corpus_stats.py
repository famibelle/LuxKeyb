#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Calcule les chiffres affichés par docs/corpus.html.

Rejoue le découpage et les seuils de Dictionnaires/LuxembourgishComplet.py, de
sorte que les totaux de la page soient comparables terme à terme avec ce que le
clavier embarque réellement. Écrit docs/assets/corpus_stats.json.

    pip install datasets
    python docs/scripts/generate_corpus_stats.py

Les deux corpus sont publics : aucun HF_TOKEN n'est nécessaire.
"""

import json
import re
import sys
from collections import Counter, defaultdict
from datetime import date
from pathlib import Path

from datasets import load_dataset

RACINE = Path(__file__).resolve().parents[2]
SORTIE = RACINE / "docs" / "assets" / "corpus_stats.json"
ASSETS = RACINE / "android_keyboard" / "app" / "src" / "main" / "assets"

# Mêmes constantes que le pipeline : toute divergence rendrait la page fausse
# de façon invisible, puisque les deux chiffres resteraient plausibles.
# {1,} et non {2,} : le clavier compte les mots d'une lettre. `InputProcessor`
# découpe sur tout ce qui n'est pas une lettre — l'apostrophe comprise — donc
# `d'Leit` lui donne `d` puis `Leit`, et le pipeline tokenise pareil depuis le
# 2026-09-04. Un motif qui refuse `d` mesurerait le modèle avec une définition
# du mot que l'application n'a pas : il compterait comme une erreur toute
# prédiction de `d`, `a` ou `e`, qui sont parmi les mots les plus fréquents.
MOTIF_MOT = re.compile(
    r'\b[a-zA-ZàáâäèéêëìíîïòóôöùúûüçñÀÁÂÄÈÉÊËÌÍÎÏÒÓÔÖÙÚÛÜÇÑäëéöü\-]{1,}\b'
)
SEUIL_FREQUENCE_DICO = 3
SEUIL_OCCURRENCES_CONTEXTE = 20

CORPUS = [
    {
        "cle": "luxalign",
        "dataset": "fredxlpy/LuxAlign",
        "configs": ["lb-en", "lb-fr"],
        "champ": "lb",
    },
    {
        "cle": "letz",
        "dataset": "fredxlpy/LETZ",
        "configs": ["LETZ-SYN", "LETZ-WoT"],
        "champ": "text",
    },
]

# Jeu d'évaluation. Ne sert jamais à construire le dictionnaire : il ne mesure
# que la qualité des fichiers déjà produits, et n'est donc pas redistribué.
# `not_paraphrase` est volontairement écarté — ce sont des phrases altérées
# exprès pour être fausses, utiles au banc d'essai d'origine, nuisibles ici.
EVALUATION = {
    "dataset": "fredxlpy/ParaLux",
    "config": "default",
    "champs": ["anchor", "paraphrase"],
}

# Mots allemands sans équivalent orthographique en luxembourgeois : leur
# présence signale une bascule de langue, pas un emprunt. « dass » en est
# volontairement absent — c'est une variante luxembourgeoise légitime.
GERMANISMES = [
    "und", "wir", "auch", "ich", "ist", "für", "die", "das", "haben", "nicht",
    "wird", "werden", "sind", "sehr", "mehr", "schon", "über", "weil",
    "natürlich", "vielleicht", "wichtig", "zusammen", "gibt", "jetzt",
    "einen", "einem", "einer", "eine", "ein", "diese", "dieser", "können",
    "gegen", "zwischen", "während", "ohne",
]

# Marqueurs du registre parlé : deuxième personne, famille, vie domestique.
# C'est ce qu'on tape sur un téléphone, et ce qu'un corpus de presse ignore.
REGISTRE_QUOTIDIEN = [
    "dech", "däi", "däin", "deng", "denger", "dengem", "hues", "bass",
    "kanns", "mamm", "brudder", "schwëster", "monni", "frëndin", "kärel",
    "owend", "vakanz", "gaart", "noper",
]

DIACRITIQUES = "éëäüèêçôàöîâ"


def decouper(texte):
    """Découpe comme le clavier, mots d'une lettre compris — voir MOTIF_MOT."""
    return [m.lower().strip("-") for m in MOTIF_MOT.findall(texte.lower())
            if m.strip("-")]


def charger(source):
    """Renvoie les phrases uniques d'un corpus.

    La déduplication n'est pas cosmétique : LuxAlign apparie chaque phrase
    luxembourgeoise à la fois à l'anglais et au français, et LETZ réutilise la
    même phrase avec des dizaines d'étiquettes de thème. Compter les lignes
    brutes multiplierait les fréquences par un facteur arbitraire, différent
    d'un corpus à l'autre.
    """
    phrases = set()
    for config in source["configs"]:
        jeu = load_dataset(source["dataset"], config)
        for split in jeu.keys():
            for item in jeu[split]:
                texte = item.get(source["champ"])
                if isinstance(texte, str) and texte.strip():
                    phrases.add(texte.strip())
    return phrases


def stats_corpus(phrases):
    brut = "\n".join(sorted(phrases))
    mots = []
    for phrase in phrases:
        mots += decouper(phrase)
    compteur = Counter(mots)
    total = len(mots) or 1
    return {
        "phrases": len(phrases),
        "caracteres": len(brut),
        "mots": len(mots),
        "formes": len(compteur),
        "longueur_moyenne": round(len(mots) / max(len(phrases), 1), 1),
        "hapax": sum(1 for n in compteur.values() if n == 1),
        "germanismes_pour_10000": round(
            10000 * sum(compteur.get(m, 0) for m in GERMANISMES) / total, 1),
        "quotidien_pour_10000": round(
            10000 * sum(compteur.get(m, 0) for m in REGISTRE_QUOTIDIEN) / total, 1),
        "top_mots": compteur.most_common(25),
        "_compteur": compteur,
        "_brut": brut,
    }


def main():
    print("Chargement des corpus...", file=sys.stderr)
    phrases_par_corpus = {}
    for source in CORPUS:
        print(f"  {source['dataset']}", file=sys.stderr)
        phrases_par_corpus[source["cle"]] = charger(source)

    par_corpus = {cle: stats_corpus(p) for cle, p in phrases_par_corpus.items()}
    union = set().union(*phrases_par_corpus.values())
    global_ = stats_corpus(union)

    # Recouvrement lexical : montre que les deux corpus ne se remplacent pas.
    formes = {cle: set(s["_compteur"]) for cle, s in par_corpus.items()}
    a, b = formes["luxalign"], formes["letz"]

    # N-grammes, mêmes seuils que le pipeline.
    bigrammes, trigrammes = Counter(), Counter()
    for phrase in union:
        m = decouper(phrase)
        bigrammes.update(zip(m, m[1:]))
        trigrammes.update(zip(m, m[1:], m[2:]))

    contextes_deux = Counter()
    for (x, y), n in bigrammes.items():
        contextes_deux[f"{x} {y}"] += n
    suites_deux = defaultdict(Counter)
    for (x, y, z), n in trigrammes.items():
        suites_deux[f"{x} {y}"][z] += n

    exemples = []
    for contexte, total in contextes_deux.most_common(200):
        if total < SEUIL_OCCURRENCES_CONTEXTE or contexte not in suites_deux:
            continue
        mot, freq = suites_deux[contexte].most_common(1)[0]
        exemples.append([contexte, mot, round(freq / total, 3), total])
        if len(exemples) == 12:
            break

    # Courbe de couverture : combien de formes pour lire quelle part du corpus.
    occurrences = sorted(global_["_compteur"].values(), reverse=True)
    total_occ = sum(occurrences)
    couverture = []
    cumul = 0
    seuil = 0
    for i, n in enumerate(occurrences, 1):
        cumul += n
        if i in (100, 500, 1000, 5000, 10000, 20000, len(occurrences)):
            couverture.append([i, round(100 * cumul / total_occ, 1)])

    brut = global_["_brut"]
    diacritiques = [[c, brut.count(c) + brut.count(c.upper())] for c in DIACRITIQUES]
    diacritiques = [d for d in diacritiques if d[1] > 0]
    ponctuation = sorted(
        ([c, brut.count(c)] for c in ".,'-\"’:%?“„!;"),
        key=lambda x: -x[1])

    # Ce qui arrive réellement dans l'APK.
    dico = json.loads((ASSETS / "luxemburgish_dict.json").read_text("utf-8"))
    ngrams = json.loads((ASSETS / "luxemburgish_ngrams.json").read_text("utf-8"))

    # Qualité de prédiction, mesurée sur ParaLux.
    #
    # Une partition aléatoire du corpus d'entraînement flatte le modèle : les
    # phrases retirées viennent des mêmes articles, de la même période et du
    # même style que celles gardées. Mesuré ainsi, le modèle annonçait 23,9 %
    # de bonnes propositions en top-3 ; sur ParaLux il en fait 18,8 %. Les cinq
    # points d'écart sont le prix de l'honnêteté.
    #
    # ParaLux est un banc d'essai de détection de paraphrase issu du même
    # article que LuxAlign, mais aucune de ses phrases n'y figure : le
    # recouvrement est nul, ce qui en fait un jeu réellement inédit pour le
    # clavier. Seules les colonnes `anchor` et `paraphrase` sont retenues ;
    # `not_paraphrase` contient des altérations fabriquées exprès pour être
    # fausses (« aus hirem Haus » pour « aus hirem Auto ») et n'a rien à faire
    # dans une évaluation de prédiction.
    #
    # Le décompte surprend et n'est pas un bug : 312 exemples à deux colonnes
    # donnent 312 phrases distinctes, et non 624. Le jeu est bâti sur 156
    # paires mutuelles, chaque phrase figurant une fois comme ancre et une fois
    # comme paraphrase d'une autre. La déduplication couvre donc bien la
    # totalité des phrases authentiques du jeu.
    #
    # On interroge ici les fichiers réellement embarqués dans l'APK, et non un
    # modèle reconstruit pour l'occasion : le chiffre publié est donc celui que
    # l'utilisateur obtient.
    phrases_eval = set()
    jeu = load_dataset(EVALUATION["dataset"], EVALUATION["config"])
    for split in jeu.keys():
        for item in jeu[split]:
            for champ in EVALUATION["champs"]:
                texte = item.get(champ)
                if isinstance(texte, str) and texte.strip():
                    phrases_eval.add(texte.strip())

    # Les entrées du dictionnaire portent depuis 2026-08-29 leur casse
    # canonique (« Joer », « RTL »), tandis que `decouper()` replie les phrases
    # en minuscules. Comparer les deux tels quels ferait chuter la couverture
    # et le top-3 d'une vingtaine de points, pour une régression qui n'existe
    # pas : l'évaluation se fait donc à casse repliée, des deux côtés.
    formes_dico = {mot.lower() for mot, _ in dico}

    def evaluer(phrases):
        touches = bons = evenements = 0
        mots_vus = couverts = 0
        for phrase in sorted(phrases):
            m = decouper(phrase)
            for mot in m:
                mots_vus += 1
                if mot in formes_dico:
                    couverts += 1
            for i in range(1, len(m)):
                evenements += 1
                candidats = ngrams.get(f"{m[i - 2]} {m[i - 1]}") if i >= 2 else None
                if candidats is None:
                    candidats = ngrams.get(m[i - 1])
                if candidats:
                    touches += 1
                    if m[i] in [c["word"].lower() for c in candidats[:3]]:
                        bons += 1
        return {
            "phrases": len(phrases),
            "evenements": evenements,
            "couverture_lexicale": round(100 * couverts / max(mots_vus, 1), 1),
            "contexte_reconnu": round(100 * touches / max(evenements, 1), 1),
            "top3": round(100 * bons / max(evenements, 1), 1),
        }

    # Jeu principal : le corpus de traduction du ZLS, dont on retire les
    # segments déjà vus à l'entraînement. Cinquante fois plus d'événements que
    # ParaLux, en CC0, et d'un mélange de sources différent (Chambre, presse,
    # LOD). ParaLux reste mesuré à côté : deux témoins indépendants qui
    # s'accordent valent mieux qu'un seul, et l'écart entre eux dit quelque
    # chose — ParaLux partage la source RTL de LuxAlign et flatte donc un peu.
    #
    # Ce corpus n'est **pas** à l'entraînement, et c'est ce qui lui donne sa
    # valeur : voir la note en tête de `CORPUS_SOURCES` dans
    # `Dictionnaires/LuxembourgishComplet.py`.
    zls_mesure = None
    try:
        sys.path.insert(0, str(RACINE / "Dictionnaires"))
        import zls_source
        # `union` réunit déjà les phrases des deux corpus d'entraînement,
        # dédupliquées : c'est exactement ce dont le filtre de recouvrement a
        # besoin.
        phrases_entrainement = union
        inedits = zls_source.segments_inedits(phrases_entrainement)
        zls_mesure = evaluer({s["lb"] for s in inedits})
        zls_mesure["jeu"] = "ZLS Iwwersetzungskorpus (inédits)"
        zls_mesure["recouvrement_entrainement"] = round(
            100 * (1 - len(inedits) / max(len(zls_source.segments()), 1)), 2)
    except Exception as exc:  # noqa: BLE001 — le jeu principal ne doit pas casser la CI
        print(f"  ⚠️ corpus ZLS indisponible pour l'évaluation : {exc}")

    paralux = evaluer(phrases_eval)
    touches = bons = evenements = 0
    mots_vus = couverts = 0

    resultat = {
        "genere_le": date.today().isoformat(),
        "corpus": {
            cle: {k: v for k, v in s.items() if not k.startswith("_")}
            for cle, s in par_corpus.items()
        },
        "global": {k: v for k, v in global_.items() if not k.startswith("_")},
        "recouvrement": {
            "communes": len(a & b),
            "luxalign_seul": len(a - b),
            "letz_seul": len(b - a),
        },
        "diacritiques": diacritiques,
        "ponctuation": ponctuation,
        "couverture": couverture,
        "ngrams": {
            "bigrammes_distincts": len(bigrammes),
            "trigrammes_distincts": len(trigrammes),
            "top_bigrammes": [[" ".join(k), n] for k, n in bigrammes.most_common(20)],
            "top_trigrammes": [[" ".join(k), n] for k, n in trigrammes.most_common(15)],
            "exemples_contextes": exemples,
        },
        "clavier": {
            "mots_dictionnaire": len(dico),
            "frequence_max": dico[0][1] if dico else 0,
            "mot_le_plus_frequent": dico[0][0] if dico else "",
            "contextes": len(ngrams),
            "contextes_deux_mots": sum(1 for k in ngrams if " " in k),
            "seuil_frequence": SEUIL_FREQUENCE_DICO,
            "seuil_contexte": SEUIL_OCCURRENCES_CONTEXTE,
            "octets_dictionnaire": (ASSETS / "luxemburgish_dict.json").stat().st_size,
            "octets_ngrams": (ASSETS / "luxemburgish_ngrams.json").stat().st_size,
        },
        "prediction": dict(zls_mesure or paralux,
                           **({} if zls_mesure is None else {})),
        "prediction_paralux": dict(paralux, jeu="ParaLux"),
    }

    SORTIE.parent.mkdir(parents=True, exist_ok=True)
    SORTIE.write_text(json.dumps(resultat, ensure_ascii=False, indent=1), "utf-8")
    print(f"Écrit {SORTIE} ({SORTIE.stat().st_size} octets)", file=sys.stderr)


if __name__ == "__main__":
    main()
