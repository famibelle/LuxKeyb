#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Mesure : les phrases d'exemple du LOD comme corpus du modèle n-grammes
======================================================================

Expérience, pas pipeline : ce script **n'écrit aucun fichier de
l'application**. Il reconstruit en mémoire le modèle n-grammes de
`LuxembourgishComplet.py` (mêmes seuils, même découpage que le clavier) pour
plusieurs corpus d'entraînement, et les compare sur les mêmes jeux
d'évaluation.

Pourquoi c'est une question
---------------------------

LETZ, déjà au corpus, n'est qu'une sélection des exemples du LOD (5 862
phrases). Le LOD lui-même en porte 58 962, en CC0. Trois pièges, que le
script mesure plutôt que de les supposer :

1. **Le jeu d'évaluation contient des exemples du LOD.** Le corpus de
   traduction du ZLS en reprend environ la moitié. Entraîner dessus fausserait
   la mesure, et le filtre de recouvrement de `generate_corpus_stats.py`
   amputerait en silence la moitié du jeu. On retire donc de l'entraînement
   tout exemple présent dans le ZLS, et on vérifie aussi les quasi-doublons
   (l'orthographe du ZLS est standardisée, une phrase retouchée échapperait au
   filtre exact).
2. **Le mot illustré est là par construction.** Chaque article reçoit ses
   exemples, que le mot soit courant ou rare : compter ce mot aplatirait les
   fréquences du dictionnaire. On compare le décompte brut et le décompte qui
   ignore `<inflectedHeadword>` pour les unigrammes.
3. **Le seuil de 20 occurrences par contexte** élimine la plupart des
   contextes propres au LOD : on liste ceux qui le franchissent.

Usage
-----

    pip install datasets
    HF_HUB_DISABLE_XET=1 python Dictionnaires/mesure_exemples_lod.py \
        [--sortie rapport.json]

Le LOD et le corpus du ZLS sont mis en cache par `lod_source.py` et
`zls_source.py`, hors du dépôt.
"""

import argparse
import difflib
import io
import json
import random
import sys
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from pathlib import Path

RACINE = Path(__file__).resolve().parent
sys.path.insert(0, str(RACINE))
sys.path.insert(0, str(RACINE.parent / "docs" / "scripts"))

import lod_source  # noqa: E402
import zls_source  # noqa: E402
from zls_source import cle_de_comparaison  # noqa: E402

# Le découpage et les seuils de la page corpus, eux-mêmes alignés sur le
# pipeline et sur `InputProcessor` : un écart rendrait la comparaison fausse.
from generate_corpus_stats import (  # noqa: E402
    CORPUS, EVALUATION, SEUIL_FREQUENCE_DICO, SEUIL_OCCURRENCES_CONTEXTE,
    decouper, charger,
)

SEUIL_PERTINENCE = 0.01  # LuxembourgishComplet.creer_ngrams
TOP = 3

# Marques d'exemple écartées. VULG et PEJ : l'application ne les montre
# jamais. VEREELZT (vieilli) : un clavier ne doit pas proposer un usage sorti
# de la langue. EGS (figuré) reste : c'est un emploi réel, seulement imagé.
MARQUES_ECARTEES = {"VULG", "PEJ", "VEREELZT"}

# Deuxième personne du singulier : le registre que la presse n'écrit pas.
# Une tranche d'évaluation est faite des événements dont le mot précédent ou
# le mot attendu en fait partie.
DEUXIEME_PERSONNE = {
    "du", "dech", "däin", "däi", "deng", "dengem", "denger", "dengen",
    "hues", "bass", "kanns", "wëlls", "solls", "gees", "kënns", "wells",
    "hätts", "wäers", "méchs", "kriss", "weess", "gesäis", "brauchs",
}


# ---------------------------------------------------------------------------
# Lecture du LOD
# ---------------------------------------------------------------------------

def lire_exemples_lod():
    """Toutes les phrases d'exemple, découpées, avec le mot illustré repéré.

    Renvoie {clé de comparaison: (phrase, [(token, est_vedette)])}, une entrée
    par phrase distincte : un même exemple sert parfois à plusieurs articles
    (le même `id` revient jusqu'à sept fois), et le compter sept fois
    reviendrait à le pondérer arbitrairement.
    """
    xml = lod_source.telecharger_source("art", verbeux=False)
    phrases = {}
    ecartees = Counter()
    brutes = 0
    for _, noeud in ET.iterparse(io.BytesIO(xml), events=("end",)):
        if noeud.tag != "entry":
            continue
        for exemple in noeud.iter("example"):
            texte = exemple.find("text")
            if texte is None:
                continue
            brutes += 1
            marques = {(e.text or "").strip() for e in texte if e.tag == "attribute"}
            if marques & MARQUES_ECARTEES:
                for m in marques & MARQUES_ECARTEES:
                    ecartees[m] += 1
                continue
            morceaux, tokens = [], []
            for enfant in texte:
                if enfant.tag not in ("word", "inflectedHeadword"):
                    continue
                mot = (enfant.text or "").strip()
                if not mot:
                    continue
                if morceaux and not morceaux[-1].endswith(("'", "’")) \
                        and mot[0] not in ",.!?;:)»…":
                    morceaux.append(" ")
                morceaux.append(mot)
                vedette = enfant.tag == "inflectedHeadword"
                tokens += [(t, vedette) for t in decouper(mot)]
            phrase = "".join(morceaux)
            if len(tokens) >= 2:
                phrases.setdefault(cle_de_comparaison(phrase), (phrase, tokens))
        noeud.clear()
    return phrases, brutes, ecartees


# ---------------------------------------------------------------------------
# Comptes et modèle
# ---------------------------------------------------------------------------

class Comptes:
    """Unigrammes, bigrammes, trigrammes d'un corpus — additionnables."""

    def __init__(self):
        self.uni = Counter()      # sert de dénominateur aux contextes à un mot
        self.uni_dico = Counter()  # fréquence du dictionnaire (vedette ôtée ou non)
        self.bi = Counter()
        self.tri = Counter()

    @classmethod
    def de_phrases(cls, listes_de_tokens, ignorer_vedette=False, poids=1,
                   vedette_jamais_predite=False):
        """`vedette_jamais_predite` : le mot illustré sert de contexte mais
        n'est jamais compté comme suite. Sans cela, « an dengem » propose
        « fierkelsjelli, gulasch, kaartespill » : le LOD illustre des dizaines
        de mots rares par la même amorce, et chacun y passe le seuil de 1 %.
        Le dénominateur (les unigrammes) est inchangé, donc la probabilité
        des autres suites aussi."""
        c = cls()
        for tokens in listes_de_tokens:
            mots = [t for t, _ in tokens]
            for m in mots:
                c.uni[m] += poids
            for t, vedette in tokens:
                if not (ignorer_vedette and vedette):
                    c.uni_dico[t] += poids
            vedettes = [v and vedette_jamais_predite for _, v in tokens]
            for i, (a, b) in enumerate(zip(mots, mots[1:])):
                if not vedettes[i + 1]:
                    c.bi[(a, b)] += poids
            for i, (a, b, d) in enumerate(zip(mots, mots[1:], mots[2:])):
                if not vedettes[i + 2]:
                    c.tri[(a, b, d)] += poids
        return c

    def __add__(self, autre):
        r = Comptes()
        for nom in ("uni", "uni_dico", "bi", "tri"):
            s = Counter(getattr(self, nom))
            s.update(getattr(autre, nom))
            setattr(r, nom, s)
        return r


class Modele:
    """Le modèle n-grammes livré, reconstruit à l'identique (casse repliée)."""

    def __init__(self, comptes):
        self.comptes = comptes
        suivants_mot = defaultdict(Counter)
        for (a, b), n in comptes.bi.items():
            suivants_mot[a][b] += n
        contextes_paire = Counter()
        for (a, b), n in comptes.bi.items():
            contextes_paire[f"{a} {b}"] += n
        suivants_paire = defaultdict(Counter)
        for (a, b, d), n in comptes.tri.items():
            suivants_paire[f"{a} {b}"][d] += n
        self.predictions = {}
        self._classer(suivants_mot, comptes.uni)
        self._classer(suivants_paire, contextes_paire)
        self.dico = Counter({m: n for m, n in comptes.uni_dico.items()
                             if n >= SEUIL_FREQUENCE_DICO})
        self._par_prefixe = None

    def _classer(self, suivants, totaux):
        for contexte, compteur in suivants.items():
            total = totaux[contexte]
            if total < SEUIL_OCCURRENCES_CONTEXTE:
                continue
            candidats = [(m, n) for m, n in compteur.most_common(5)
                         if n / total > SEUIL_PERTINENCE]
            if candidats:
                self.predictions[contexte] = [m for m, _ in candidats]

    def suite(self, m, i):
        cands = self.predictions.get(f"{m[i - 2]} {m[i - 1]}") if i >= 2 else None
        if cands is None:
            cands = self.predictions.get(m[i - 1])
        return cands

    def completions(self, prefixe):
        """Les TOP formes du dictionnaire qui commencent par `prefixe`."""
        if self._par_prefixe is None:
            self._par_prefixe = defaultdict(list)
            for mot, n in self.dico.most_common():
                cle = mot[:2]
                if len(self._par_prefixe[cle]) < 400:
                    self._par_prefixe[cle].append(mot)
        return [m for m in self._par_prefixe.get(prefixe[:2], [])
                if m.startswith(prefixe)][:TOP]


# ---------------------------------------------------------------------------
# Évaluation
# ---------------------------------------------------------------------------

def evaluer_phrase(modele, phrase):
    """(événements, contexte reconnu, top-3, évts 2e pers., top-3 2e pers.,
    mots ≥ 4 lettres, complétés sur 2 lettres, mots couverts, mots)."""
    m = decouper(phrase)
    r = [0] * 9
    for mot in m:
        r[8] += 1
        r[7] += mot in modele.dico
        if len(mot) >= 4:
            r[5] += 1
            r[6] += mot in modele.completions(mot[:2])
    for i in range(1, len(m)):
        r[0] += 1
        cands = modele.suite(m, i)
        bon = 0
        if cands:
            r[1] += 1
            bon = m[i] in cands[:TOP]
            r[2] += bon
        if m[i - 1] in DEUXIEME_PERSONNE or m[i] in DEUXIEME_PERSONNE:
            r[3] += 1
            r[4] += bon
    return r


def resumer(lignes):
    t = [sum(col) for col in zip(*lignes)] if lignes else [0] * 9
    pct = lambda a, b: round(100 * a / b, 2) if b else None  # noqa: E731
    return {
        "phrases": len(lignes),
        "evenements": t[0],
        "couverture_lexicale": pct(t[7], t[8]),
        "contexte_reconnu": pct(t[1], t[0]),
        "top3": pct(t[2], t[0]),
        "evenements_2e_personne": t[3],
        "top3_2e_personne": pct(t[4], t[3]),
        "completion_2_lettres": pct(t[6], t[5]),
    }


def ecart_bootstrap(lignes_a, lignes_b, col_num=2, col_den=0, tirages=1000):
    """Intervalle à 95 % de l'écart B − A (points), rééchantillonné par phrase."""
    n = len(lignes_a)
    if n == 0:
        return None
    alea = random.Random(20261120)
    ecarts = []
    for _ in range(tirages):
        idx = [alea.randrange(n) for _ in range(n)]
        den = sum(lignes_a[i][col_den] for i in idx) or 1
        a = sum(lignes_a[i][col_num] for i in idx)
        b = sum(lignes_b[i][col_num] for i in idx)
        ecarts.append(100 * (b - a) / den)
    ecarts.sort()
    return [round(ecarts[int(0.025 * tirages)], 2),
            round(ecarts[int(0.975 * tirages)], 2)]


# ---------------------------------------------------------------------------
# Quasi-doublons ZLS ↔ LOD
# ---------------------------------------------------------------------------

def quasi_doublons(segments, phrases_lod, seuil=0.9):
    """Segments du ZLS qui ressemblent à un exemple du LOD sans l'égaler.

    Le ZLS a standardisé l'orthographe de ses segments : un exemple retouché
    (« Dir » → « dir », une virgule) échappe à la clé exacte et fuirait dans
    l'évaluation. Index sur les trigrammes de mots, puis difflib.
    """
    index = defaultdict(set)
    textes = []
    for i, (phrase, tokens) in enumerate(phrases_lod):
        mots = [t for t, _ in tokens]
        textes.append(" ".join(mots))
        for tri in set(zip(mots, mots[1:], mots[2:])):
            index[tri].add(i)
    trouves = []
    for seg in segments:
        mots = decouper(seg)
        tris = set(zip(mots, mots[1:], mots[2:]))
        if not tris:
            continue
        votes = Counter()
        for tri in tris:
            votes.update(index.get(tri, ()))
        texte = " ".join(mots)
        for i, n in votes.most_common(3):
            if n < max(1, len(tris) // 2):
                break
            if difflib.SequenceMatcher(None, texte, textes[i]).ratio() >= seuil:
                trouves.append((seg, phrases_lod[i][0]))
                break
    return trouves


# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[1])
    parser.add_argument("--sortie", type=Path)
    args = parser.parse_args()

    print("📖 corpus Hugging Face…", file=sys.stderr)
    phrases = {s["cle"]: charger(s) for s in CORPUS}
    tok = lambda ps: [[(t, False) for t in decouper(p)] for p in ps]  # noqa: E731

    print("📖 exemples du LOD…", file=sys.stderr)
    lod, lod_brutes, lod_ecartees = lire_exemples_lod()
    print("📖 corpus du ZLS…", file=sys.stderr)
    zls = [s["lb"] for s in zls_source.segments()]
    cles_zls = {cle_de_comparaison(s) for s in zls}

    from datasets import load_dataset
    paralux = set()
    jeu = load_dataset(EVALUATION["dataset"], EVALUATION["config"])
    for split in jeu.keys():
        for item in jeu[split]:
            for champ in EVALUATION["champs"]:
                if isinstance(item.get(champ), str) and item[champ].strip():
                    paralux.add(item[champ].strip())

    # --- Recouvrements --------------------------------------------------------
    cles_letz = {cle_de_comparaison(p) for p in phrases["letz"]}
    cles_luxalign = {cle_de_comparaison(p) for p in phrases["luxalign"]}
    cles_lod = set(lod)
    rapport = {
        "lod": {
            "exemples_bruts": lod_brutes,
            "ecartes_par_marque": dict(lod_ecartees),
            "phrases_distinctes": len(lod),
            "mots": sum(len(t) for _, t in lod.values()),
            "mots_vedettes": sum(v for _, t in lod.values() for _, v in t),
        },
        "recouvrements": {
            "letz_dans_lod_pct": round(100 * len(cles_letz & cles_lod) / len(cles_letz), 1),
            "zls_segments": len(zls),
            "zls_dans_lod": sum(cle_de_comparaison(s) in cles_lod for s in zls),
            "zls_dans_entrainement_actuel": sum(
                cle_de_comparaison(s) in (cles_letz | cles_luxalign) for s in zls),
            "paralux_dans_lod": sum(cle_de_comparaison(p) in cles_lod for p in paralux),
        },
    }

    # --- Jeu d'évaluation figé ------------------------------------------------
    # Celui que publie la page corpus : les segments du ZLS absents de
    # l'entraînement ACTUEL. Il reste le même pour toutes les variantes ; c'est
    # l'entraînement qu'on ampute des phrases du ZLS, jamais l'évaluation.
    connues = cles_letz | cles_luxalign
    eval_zls = [s for s in zls if cle_de_comparaison(s) not in connues]
    eval_zls_lod = [s for s in eval_zls if cle_de_comparaison(s) in cles_lod]
    eval_zls_autres = [s for s in eval_zls if cle_de_comparaison(s) not in cles_lod]

    lod_hors_zls = [(p, t) for c, (p, t) in lod.items() if c not in cles_zls]
    print("🔎 quasi-doublons…", file=sys.stderr)
    fuites = quasi_doublons(eval_zls_autres, lod_hors_zls)
    rapport["recouvrements"]["quasi_doublons_zls_lod"] = len(fuites)
    rapport["recouvrements"]["quasi_doublons_exemples"] = fuites[:15]
    cles_fuites = {cle_de_comparaison(s) for s, _ in fuites}
    # Les phrases du LOD quasi identiques à un segment évalué sont retirées de
    # l'entraînement aussi : sinon le modèle aurait vu la réponse.
    cles_lod_fuites = {cle_de_comparaison(p) for _, p in fuites}
    lod_entrainement = [(p, t) for p, t in lod_hors_zls
                        if cle_de_comparaison(p) not in cles_lod_fuites]
    rapport["lod"]["phrases_entrainement_hors_zls"] = len(lod_entrainement)

    jeux = {
        "ZLS (inédits, jeu publié)": eval_zls,
        "ZLS — segments issus du LOD": eval_zls_lod,
        "ZLS — Chambre, presse, autres": eval_zls_autres,
        "ParaLux": sorted(paralux),
    }

    # --- Variantes ------------------------------------------------------------
    print("🧮 comptes…", file=sys.stderr)
    c_luxalign = Comptes.de_phrases(tok(phrases["luxalign"]))
    c_letz = Comptes.de_phrases(tok(phrases["letz"] - phrases["luxalign"]))
    t_lod = [t for _, t in lod_entrainement]
    c_lod_brut = Comptes.de_phrases(t_lod)
    c_lod = Comptes.de_phrases(t_lod, ignorer_vedette=True)
    c_lod_cible = Comptes.de_phrases(t_lod, ignorer_vedette=True,
                                     vedette_jamais_predite=True)
    c_lod_x3 = Comptes.de_phrases(t_lod, ignorer_vedette=True, poids=3)
    c_lod_naif = Comptes.de_phrases([t for _, t in lod.values()])

    variantes = {
        "A · actuel (LuxAlign + LETZ)": c_luxalign + c_letz,
        "B · + LOD hors ZLS, vedette ôtée": c_luxalign + c_letz + c_lod,
        "B2 · B, vedette jamais prédite": c_luxalign + c_letz + c_lod_cible,
        "B' · + LOD hors ZLS, vedette comptée": c_luxalign + c_letz + c_lod_brut,
        "C · LuxAlign + LOD (LETZ retiré)": c_luxalign + c_lod,
        "D · + LOD pondéré ×3": c_luxalign + c_letz + c_lod_x3,
        "N · naïf : + LOD complet (fuite)": c_luxalign + c_letz + c_lod_naif,
    }

    modeles, lignes = {}, {}
    rapport["variantes"] = {}
    for nom, comptes in variantes.items():
        print(f"🧠 {nom}", file=sys.stderr)
        mod = Modele(comptes)
        modeles[nom] = mod
        rapport["variantes"][nom] = {
            "contextes": len(mod.predictions),
            "contextes_deux_mots": sum(" " in k for k in mod.predictions),
            "formes_dico_seuil3": len(mod.dico),
            "mesures": {},
        }
        for jeu_nom, ps in jeux.items():
            l = [evaluer_phrase(mod, p) for p in ps]
            lignes[(nom, jeu_nom)] = l
            rapport["variantes"][nom]["mesures"][jeu_nom] = resumer(l)

    # Le piège 1, chiffré : ce que le filtre de la page corpus garderait si on
    # entraînait naïvement sur tout le LOD.
    connues_naif = connues | cles_lod
    rapport["recouvrements"]["eval_zls_si_lod_naif"] = sum(
        cle_de_comparaison(s) not in connues_naif for s in zls)

    # --- Écarts et intervalles ------------------------------------------------
    base = "A · actuel (LuxAlign + LETZ)"
    rapport["ecarts_vs_A"] = {}
    for nom in variantes:
        if nom == base:
            continue
        rapport["ecarts_vs_A"][nom] = {
            jeu_nom: {
                "top3_ic95": ecart_bootstrap(lignes[(base, jeu_nom)], lignes[(nom, jeu_nom)]),
                "top3_2e_pers_ic95": ecart_bootstrap(
                    lignes[(base, jeu_nom)], lignes[(nom, jeu_nom)], 4, 3),
                "completion_ic95": ecart_bootstrap(
                    lignes[(base, jeu_nom)], lignes[(nom, jeu_nom)], 6, 5),
            }
            for jeu_nom in jeux
        }

    # --- Ce que le LOD change dans le modèle ----------------------------------
    a, b = modeles[base], modeles["B · + LOD hors ZLS, vedette ôtée"]
    nouveaux = sorted((k for k in b.predictions if k not in a.predictions),
                      key=lambda k: -(b.comptes.uni[k] if " " not in k
                                      else b.comptes.bi[tuple(k.split(" ", 1))]))
    rapport["contextes_nouveaux"] = len(nouveaux)
    rapport["contextes_nouveaux_exemples"] = {k: b.predictions[k][:3] for k in nouveaux[:40]}
    rapport["contextes_nouveaux_2e_personne"] = {
        k: b.predictions[k][:3] for k in nouveaux
        if set(k.split()) & DEUXIEME_PERSONNE}
    changes = [k for k in a.predictions if k in b.predictions
               and a.predictions[k][0] != b.predictions[k][0]]
    rapport["contextes_premier_choix_change"] = len(changes)
    rapport["premier_choix_change_2e_personne"] = {
        k: {"avant": a.predictions[k][:3], "apres": b.predictions[k][:3]}
        for k in changes if set(k.split()) & DEUXIEME_PERSONNE}

    b2 = modeles["B2 · B, vedette jamais prédite"]
    rapport["B2_contextes_2e_personne"] = {
        k: {"B": b.predictions.get(k, [])[:3], "B2": b2.predictions.get(k, [])[:3]}
        for k in rapport["contextes_nouveaux_2e_personne"]}
    rapport["B2_contextes"] = len(b2.predictions)

    # Piège 2 : les mots que la vedette ferait monter dans le dictionnaire.
    bb = modeles["B' · + LOD hors ZLS, vedette comptée"]
    gonfles = []
    for mot, n in bb.dico.items():
        n_b = b.dico.get(mot, 0)
        if n >= 10 and n_b < n / 2:
            gonfles.append((mot, a.dico.get(mot, 0), n_b, n))
    gonfles.sort(key=lambda x: -(x[3] - x[2]))
    rapport["vedette_gonfle"] = {
        "formes_dont_la_frequence_double_au_moins": len(gonfles),
        "exemples (mot, A, B vedette ôtée, B' vedette comptée)": gonfles[:25],
    }

    texte = json.dumps(rapport, ensure_ascii=False, indent=2)
    if args.sortie:
        args.sortie.write_text(texte, encoding="utf-8")
    print(texte)


if __name__ == "__main__":
    main()
