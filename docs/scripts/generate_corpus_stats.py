#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Calcule la synthèse statistique du corpus POTOMITAN/PawolKreyol-gfc.

Le résultat est écrit dans docs/assets/corpus_stats.json, que docs/corpus.html
consomme pour afficher ses chiffres et ses graphiques.

    python3 docs/scripts/generate_corpus_stats.py

Le jeu de données est public : le script lit directement l'export parquet servi
par le datasets-server de Hugging Face, sans HF_TOKEN ni la librairie datasets.
En cas d'échec du téléchargement, il se rabat sur PawolKreyol/Textes_kreyol.json,
l'instantané local, et le signale dans le champ `source_donnees` du JSON : ce
n'est alors plus une photo du dataset publié.

La tokenisation reproduit exactement celle de Dictionnaires/KreyolComplet.py
(même expression régulière, mêmes seuils de n-grammes). Les totaux calculés ici
doivent donc coller à ceux des assets livrés dans le clavier, à la poignée de
mots curés à la main près.
"""

import io
import json
import re
import sys
import unicodedata
import urllib.request
from collections import Counter, defaultdict
from pathlib import Path

DOCS = Path(__file__).resolve().parents[1]
RACINE = DOCS.parent
SORTIE = DOCS / "assets" / "corpus_stats.json"
INSTANTANE_LOCAL = RACINE / "PawolKreyol" / "Textes_kreyol.json"
ASSETS_ANDROID = RACINE / "android_keyboard" / "app" / "src" / "main" / "assets"

DATASET = "POTOMITAN/PawolKreyol-gfc"
URL_PARQUET = (
    "https://huggingface.co/datasets/POTOMITAN/PawolKreyol-gfc/resolve/"
    "refs%2Fconvert%2Fparquet/default/train/0000.parquet"
)
URL_TAILLE = "https://datasets-server.huggingface.co/size?dataset=POTOMITAN%2FPawolKreyol-gfc"
URL_REVISION = "https://huggingface.co/api/datasets/POTOMITAN/PawolKreyol-gfc"
URL_REVISION_PARQUET = (
    "https://huggingface.co/api/datasets/POTOMITAN/PawolKreyol-gfc/"
    "revision/refs%2Fconvert%2Fparquet"
)

# Motif identique à celui du pipeline dictionnaire : au moins deux caractères,
# lettres latines accentuées et trait d'union admis.
PATTERN_MOT = re.compile(
    r"\b[a-zA-ZàáâäèéêëìíîïòóôöùúûüçñÀÁÂÄÈÉÊËÌÍÎÏÒÓÔÖÙÚÛÜÇÑ\-]{2,}\b"
)

# Seuils du modèle de prédiction, repris de KreyolComplet.creer_ngrams()
SEUIL_PROBABILITE = 0.01
MAX_CANDIDATS = 5
MIN_OCCURRENCES_CONTEXTE = 2


def telecharger_corpus():
    """Renvoie (lignes, origine). Une ligne est un dict {Source, Texte}."""
    try:
        import pandas as pd

        with urllib.request.urlopen(URL_PARQUET, timeout=60) as reponse:
            brut = reponse.read()
        df = pd.read_parquet(io.BytesIO(brut))
        lignes = df.to_dict("records")
        return lignes, "huggingface"
    except Exception as erreur:  # réseau coupé, pandas absent, pyarrow absent…
        print(f"⚠️  Lecture du dataset impossible ({erreur})", file=sys.stderr)
        print(f"    Repli sur l'instantané local {INSTANTANE_LOCAL}", file=sys.stderr)
        with open(INSTANTANE_LOCAL, encoding="utf-8") as f:
            return json.load(f), "instantané local"


def taille_publiee():
    """Octets du parquet publié, tels que rapportés par Hugging Face."""
    try:
        with urllib.request.urlopen(URL_TAILLE, timeout=30) as reponse:
            donnees = json.load(reponse)
        return donnees["size"]["dataset"]["num_bytes_parquet_files"]
    except Exception:
        return None


def revisions_publiees():
    """Empreintes du dataset : commit de `main` et commit de l'export parquet.

    Les deux sont suivis parce qu'ils ne bougent pas ensemble : Hugging Face
    reconvertit l'export en parquet quelques minutes après un commit sur `main`.
    Ne surveiller que `main` reviendrait à enregistrer une révision dont les
    chiffres de cette page ne proviennent pas encore.
    """
    empreintes = {}
    for cle, url in (("dataset", URL_REVISION), ("parquet", URL_REVISION_PARQUET)):
        try:
            with urllib.request.urlopen(url, timeout=30) as reponse:
                donnees = json.load(reponse)
            empreintes[cle] = donnees.get("sha")
            if cle == "dataset":
                empreintes["derniere_modification"] = donnees.get("lastModified")
        except Exception:
            empreintes[cle] = None
    return empreintes


def tokeniser(texte):
    mots = [mot.strip("-") for mot in PATTERN_MOT.findall(texte.lower())]
    return [mot for mot in mots if len(mot) >= 2]


def source_courte(source, limite=44):
    """Étiquette lisible pour un graphique, à partir d'une référence longue.

    On tronque plutôt qu'on ne coupe à la première virgule : plusieurs sources
    partagent le même auteur et ne se distinguent que par le titre qui suit.
    """
    texte = " ".join(source.split())
    if len(texte) <= limite:
        return texte
    coupe = texte[:limite].rsplit(" ", 1)[0].rstrip(" ,;:")
    return (coupe or texte[:limite].rstrip()) + "…"


def analyser(lignes):
    stats = {}

    textes = []
    par_source_texte = Counter()
    par_source_lignes = Counter()
    lignes_vides = 0

    for ligne in lignes:
        texte = ligne.get("Texte")
        source = (ligne.get("Source") or "(source non renseignée)").strip()
        par_source_lignes[source] += 1
        if not isinstance(texte, str) or not texte.strip():
            lignes_vides += 1
            continue
        textes.append((source, texte))
        par_source_texte[source] += len(texte)

    stats["lignes_total"] = len(lignes)
    stats["lignes_vides"] = lignes_vides
    stats["lignes_utiles"] = len(textes)
    stats["sources_total"] = len(par_source_lignes)

    caracteres = sum(len(t) for _, t in textes)
    stats["caracteres"] = caracteres
    stats["octets_utf8"] = sum(len(t.encode("utf-8")) for _, t in textes)

    # Longueur des entrées
    longueurs = sorted(len(t) for _, t in textes)

    def centile(p):
        return longueurs[min(len(longueurs) - 1, int(len(longueurs) * p))]

    stats["longueur_entree"] = {
        "min": longueurs[0],
        "max": longueurs[-1],
        "moyenne": round(caracteres / len(textes), 1),
        "mediane": centile(0.5),
        "p90": centile(0.90),
    }
    tranches = [(0, 10), (10, 25), (25, 50), (50, 100), (100, 200), (200, 500), (500, None)]
    stats["tranches_longueur_entree"] = [
        {
            "min": bas,
            "max": haut,
            "n": sum(1 for l in longueurs if l >= bas and (haut is None or l < haut)),
        }
        for bas, haut in tranches
    ]

    # Comptages de n-grammes
    unigrammes = Counter()
    bigrammes = Counter()
    trigrammes = Counter()
    suivants_unigramme = defaultdict(Counter)
    suivants_bigramme = defaultdict(Counter)
    par_source_mots = Counter()

    for source, texte in textes:
        mots = tokeniser(texte)
        par_source_mots[source] += len(mots)
        for mot in mots:
            unigrammes[mot] += 1
        for i in range(len(mots) - 1):
            bigrammes[(mots[i], mots[i + 1])] += 1
            suivants_unigramme[mots[i]][mots[i + 1]] += 1
        for i in range(len(mots) - 2):
            trigrammes[(mots[i], mots[i + 1], mots[i + 2])] += 1
            suivants_bigramme[(mots[i], mots[i + 1])][mots[i + 2]] += 1

    total_mots = sum(unigrammes.values())
    stats["mots_total"] = total_mots
    stats["mots_uniques"] = len(unigrammes)
    stats["richesse_lexicale"] = round(len(unigrammes) / total_mots, 4)
    stats["mots_par_entree"] = round(total_mots / len(textes), 1)
    stats["bigrammes_uniques"] = len(bigrammes)
    stats["trigrammes_uniques"] = len(trigrammes)
    hapax = sum(1 for occurrences in unigrammes.values() if occurrences == 1)
    stats["hapax"] = hapax
    stats["hapax_pourcent"] = round(100 * hapax / len(unigrammes), 1)
    stats["longueur_mot_moyenne"] = round(
        sum(len(mot) * n for mot, n in unigrammes.items()) / total_mots, 2
    )

    stats["top_mots"] = [
        {"mot": mot, "n": n, "pourcent": round(100 * n / total_mots, 2)}
        for mot, n in unigrammes.most_common(25)
    ]
    stats["top_bigrammes"] = [
        {"suite": " ".join(cle), "n": n} for cle, n in bigrammes.most_common(20)
    ]
    stats["top_trigrammes"] = [
        {"suite": " ".join(cle), "n": n} for cle, n in trigrammes.most_common(20)
    ]

    # Couverture : combien de mots différents suffisent à couvrir X % du texte
    cumul = 0
    couverture = {}
    for rang, (_, n) in enumerate(unigrammes.most_common(), 1):
        cumul += n
        for seuil in (50, 75, 90, 95, 99):
            if str(seuil) not in couverture and cumul >= total_mots * seuil / 100:
                couverture[str(seuil)] = rang
    stats["couverture"] = couverture

    # Mots les plus longs : à longueur égale, les plus attestés d'abord
    longs = sorted(unigrammes.items(), key=lambda kv: (-len(kv[0]), -kv[1], kv[0]))
    stats["mots_longs"] = [
        {"mot": mot, "lettres": len(mot), "n": n} for mot, n in longs[:20]
    ]

    types_par_longueur = Counter(len(mot) for mot in unigrammes)
    stats["longueur_mots"] = [
        {"lettres": lettres, "mots": types_par_longueur[lettres]}
        for lettres in sorted(types_par_longueur)
    ]

    # Complexité morphologique : le kréyòl compose beaucoup par trait d'union
    # (kaz-la, an-mwen, ba-y…). On mesure ces composés sur les mots différents,
    # pas sur les occurrences : c'est le vocabulaire qui est agglutinant.
    composes = {mot: n for mot, n in unigrammes.items() if "-" in mot}
    stats["composes"] = {
        "mots": len(composes),
        "pourcent": round(100 * len(composes) / len(unigrammes), 1),
        "occurrences": sum(composes.values()),
        "occurrences_pourcent": round(100 * sum(composes.values()) / total_mots, 1),
        "exemples": [
            {"mot": mot, "n": n}
            for mot, n in sorted(composes.items(), key=lambda kv: -kv[1])[:12]
        ],
    }
    segments = Counter(mot.count("-") + 1 for mot in composes)
    stats["composes"]["segments"] = [
        {"segments": s, "mots": segments[s]} for s in sorted(segments)
    ]

    longs_10 = {mot: n for mot, n in unigrammes.items() if len(mot) >= 10}
    stats["mots_10_lettres"] = {
        "mots": len(longs_10),
        "pourcent": round(100 * len(longs_10) / len(unigrammes), 1),
        "avec_trait_union": sum(1 for mot in longs_10 if "-" in mot),
        "hapax": sum(1 for n in longs_10.values() if n == 1),
    }

    # Marqueurs de temps, mode et aspect. La liste et les fonctions reprennent
    # Dictionnaires/RAPPORT_LINGUISTIQUE.md : rien n'est inventé ici, on ne fait
    # que recompter dans le corpus courant.
    MARQUEURS = [
        ("ka", "Aspect progressif ou habituel"),
        ("té", "Passé"),
        ("ké", "Futur"),
        ("kay", "Futur proche"),
        ("pé", "Possibilité"),
        ("ja", "Accompli"),
        ("pa", "Négation"),
    ]
    tma = []
    for marqueur, fonction in MARQUEURS:
        occurrences = unigrammes.get(marqueur, 0)
        if not occurrences:
            continue
        suites = suivants_unigramme.get(marqueur, Counter())
        tma.append(
            {
                "marqueur": marqueur,
                "fonction": fonction,
                "n": occurrences,
                "pourcent": round(100 * occurrences / total_mots, 2),
                "collocations": [
                    {"mot": suivant, "n": n} for suivant, n in suites.most_common(3)
                ],
            }
        )
    stats["tma"] = tma

    # Combinaisons de marqueurs, qui portent l'essentiel de la conjugaison
    codes = {m for m, _ in MARQUEURS}
    stats["tma_combinaisons"] = [
        {"suite": " ".join(cle), "n": n}
        for cle, n in bigrammes.most_common()
        if cle[0] in codes and cle[1] in codes
    ][:10]
    stats["tma_part"] = round(
        100 * sum(entree["n"] for entree in tma) / total_mots, 1
    )

    # Sources
    stats["top_sources"] = [
        {
            "source": source,
            "court": source_courte(source),
            "mots": mots,
            "lignes": par_source_lignes[source],
            "caracteres": par_source_texte[source],
        }
        for source, mots in par_source_mots.most_common(15)
    ]
    mots_top10 = sum(m for _, m in par_source_mots.most_common(10))
    stats["part_top10_sources"] = round(100 * mots_top10 / total_mots, 1)

    # Lettres et accents
    lettres = Counter()
    for _, texte in textes:
        for caractere in texte.lower():
            if caractere.isalpha():
                lettres[caractere] += 1
    total_lettres = sum(lettres.values())
    stats["top_lettres"] = [
        {"lettre": c, "n": n, "pourcent": round(100 * n / total_lettres, 2)}
        for c, n in lettres.most_common(12)
    ]
    accentuees = {
        c: n for c, n in lettres.items() if len(unicodedata.normalize("NFD", c)) > 1
    }
    stats["accents"] = [
        {"lettre": c, "n": n} for c, n in sorted(accentuees.items(), key=lambda kv: -kv[1])
    ]
    stats["accents_pourcent"] = round(100 * sum(accentuees.values()) / total_lettres, 2)

    # Modèle de prédiction, reconstruit avec les seuils du pipeline
    def meilleurs(compteur_suivants, total_contexte):
        candidats = [
            (suivant, n / total_contexte)
            for suivant, n in compteur_suivants.items()
            if n / total_contexte > SEUIL_PROBABILITE
        ]
        candidats.sort(key=lambda c: -c[1])
        return candidats[:MAX_CANDIDATS]

    cles_1mot = 0
    cles_2mots = 0
    contextes_ecartes = 0
    total_candidats = 0
    contextes = []

    for mot, suivants in suivants_unigramme.items():
        candidats = meilleurs(suivants, unigrammes[mot])
        if candidats:
            cles_1mot += 1
            total_candidats += len(candidats)
            contextes.append((mot, candidats[0], unigrammes[mot], len(candidats)))

    for (mot1, mot2), suivants in suivants_bigramme.items():
        occurrences = bigrammes[(mot1, mot2)]
        if occurrences < MIN_OCCURRENCES_CONTEXTE:
            contextes_ecartes += 1
            continue
        candidats = meilleurs(suivants, occurrences)
        if candidats:
            cles_2mots += 1
            total_candidats += len(candidats)
            contextes.append(
                (f"{mot1} {mot2}", candidats[0], occurrences, len(candidats))
            )

    stats["modele"] = {
        "cles": cles_1mot + cles_2mots,
        "cles_1mot": cles_1mot,
        "cles_2mots": cles_2mots,
        "contextes_ecartes": contextes_ecartes,
        "candidats_moyen": round(total_candidats / (cles_1mot + cles_2mots), 2),
    }
    contextes.sort(key=lambda c: -c[2])
    stats["contextes_frequents"] = [
        {
            "contexte": contexte,
            "suite": premier[0],
            "probabilite": round(premier[1], 3),
            "occurrences": occurrences,
            "candidats": nb,
        }
        for contexte, premier, occurrences, nb in contextes[:15]
    ]

    return stats


def comparer_aux_assets(stats):
    """Rapproche le corpus des fichiers effectivement embarqués dans le clavier."""
    try:
        with open(ASSETS_ANDROID / "creole_dict.json", encoding="utf-8") as f:
            dictionnaire = json.load(f)
        with open(ASSETS_ANDROID / "creole_ngrams.json", encoding="utf-8") as f:
            ngrams = json.load(f)
        with open(ASSETS_ANDROID / "french_simple_dict.json", encoding="utf-8") as f:
            francais = json.load(f)
    except FileNotFoundError:
        return

    mots = [entree[0] if isinstance(entree, list) else entree for entree in dictionnaire]
    stats["clavier"] = {
        "dictionnaire_mots": len(mots),
        "ngrams_cles": len(ngrams),
        "francais_mots": francais.get("word_count", len(francais.get("words", []))),
    }


def main():
    lignes, origine = telecharger_corpus()
    stats = analyser(lignes)
    comparer_aux_assets(stats)

    stats["dataset"] = DATASET
    stats["dataset_url"] = f"https://huggingface.co/datasets/{DATASET}"
    stats["source_donnees"] = origine
    octets = taille_publiee()
    if octets:
        stats["parquet_octets"] = octets

    # Empreintes servant au workflow à décider si le dataset a bougé
    empreintes = revisions_publiees()
    stats["dataset_revision"] = empreintes.get("dataset")
    stats["dataset_revision_parquet"] = empreintes.get("parquet")
    stats["dataset_derniere_modification"] = empreintes.get("derniere_modification")

    SORTIE.parent.mkdir(parents=True, exist_ok=True)
    with open(SORTIE, "w", encoding="utf-8") as f:
        json.dump(stats, f, ensure_ascii=False, indent=1)
        f.write("\n")

    print(f"✅ {SORTIE.relative_to(RACINE)} écrit depuis : {origine}")
    print(
        f"   {stats['lignes_total']} lignes · {stats['sources_total']} sources · "
        f"{stats['mots_total']} mots · {stats['mots_uniques']} mots différents"
    )


if __name__ == "__main__":
    main()
