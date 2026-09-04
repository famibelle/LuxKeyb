#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Banc de qualité du clavier — trois mesures que les autres bancs ne font pas.

    python3 docs/scripts/bench_qualite.py

`bench_frappes.js` répond à « le mot est-il proposé ». Ce banc-ci répond à
trois questions qui décrivent mieux ce que l'utilisateur vit :

1. **L'économie de frappes (KSR)** — la métrique de référence du domaine :
   quelle part des touches sont évitées en touchant une suggestion. « Proposé
   en top-3 » ne dit pas *quand* : un mot de dix lettres proposé à la neuvième
   n'économise rien.

2. **Le taux de soulignement abusif, par langue** — combien de mots corrects
   le correcteur système conteste. Le clavier déclare la locale `fr` et
   remplace donc le correcteur français ; ce qu'il fait des autres langues du
   pays se mesure, au lieu de se supposer.

3. **Le taux de correction indue** — combien de fois le clavier propose de
   remplacer un mot bien écrit. C'était le défaut de « déchet », corrigé en
   11.4.1 pour le français ; rien ne surveillait sa réapparition ailleurs.

Le corpus d'évaluation est celui du ZLS (`Dictionnaires/zls_source.py`), dont
les colonnes `lb`, `fr` et `de` sont des traductions professionnelles du même
texte : les trois langues sont donc mesurées sur un contenu comparable, ce qui
n'est le cas d'aucune autre source du projet.

⚠️ Ce script rejoue les règles du moteur, il ne l'exécute pas. Elles sont lues
dans `SuggestionEngine` et `FrenchDictionary` et listées devant chaque mesure.
Un écart entre les deux fausserait tout : quatre états ont été vérifiés sur
appareil le 2026-09-04, et la règle du repli Levenshtein l'a été deux fois.
"""

import json
import re
import sys
import unicodedata
from pathlib import Path

RACINE = Path(__file__).resolve().parents[2]
ASSETS = RACINE / "android_keyboard/app/src/main/assets"
sys.path.insert(0, str(RACINE / "Dictionnaires"))

MOTIF = re.compile(r"[^\W\d_]+", re.UNICODE)
POOL = 40           # CANDIDATE_POOL_SIZE
MAX_LB = 3          # bilingualConfig.maxLuxSuggestions
MAX_FR = 2          # MAX_FRENCH_SUGGESTIONS
MIN_FR = 3          # MIN_ACTIVATION_LENGTH
MIN_REPLI = 3       # devraitCorriger : sous trois lettres, pas de correction


def plie(mot):
    """AccentTolerantMatcher.normalize : minuscules sans diacritiques."""
    return "".join(c for c in unicodedata.normalize("NFD", mot.lower())
                   if unicodedata.category(c) != "Mn")


def charger():
    dico = json.loads((ASSETS / "luxemburgish_dict.json").read_text("utf-8"))
    ngrams = json.loads((ASSETS / "luxemburgish_ngrams.json").read_text("utf-8"))
    lod = json.loads((ASSETS / "luxemburgish_lod_forms.json").read_text("utf-8"))
    fr = json.loads((ASSETS / "french_simple_dict.json").read_text("utf-8"))

    lb_connu = {plie(m) for m, _ in dico}
    lb_connu |= {plie(m) for m in lod["suggest"]} | {plie(m) for m in lod["spellcheck"]}
    fr_mots = fr["suggest_mots"]
    fr_freq = fr["suggest_freq"]
    # Le palier de reconnaissance français est un filtre de Bloom : les formes
    # verbales rares n'y sont pas en clair. Le rejouer est indispensable, sans
    # quoi `générer` et `contrôlent` passeraient pour inconnus et le taux de
    # soulignement français serait surestimé. On réutilise le hachage du
    # générateur plutôt que d'en écrire un troisième.
    import base64
    from generate_french_dict import indices_bloom
    bloom = bytearray(base64.b64decode(fr["bloom"]))
    bits, hachages = fr["bloom_bits"], fr["bloom_hachages"]

    def fr_reconnu(mot):
        m = mot.lower()
        return all(bloom[i >> 3] & (1 << (i & 7))
                   for i in indices_bloom(m, bits, hachages))

    fr_connu = fr_reconnu

    # Index par préfixe, comme le moteur : trois lettres côté français,
    # une seule côté luxembourgeois (MIN_WORD_LENGTH y vaut 1).
    seaux_fr = {}
    for m, f in zip(fr_mots, fr_freq):
        if len(m) >= MIN_FR:
            seaux_fr.setdefault(m[:MIN_FR], []).append((m, f))
    seaux_lb = {}
    for m, f in dico:
        n = plie(m)
        for k in range(1, min(len(n), 4) + 1):
            seaux_lb.setdefault(n[:k], []).append((m, f, n))
    return dico, ngrams, lb_connu, fr_connu, seaux_lb, seaux_fr


def rangee_lb(prefixe, contexte_mots, seaux_lb):
    """Préfixe sur les formes repliées, bonus n-gramme, trois premières."""
    n = plie(prefixe)
    seau = seaux_lb.get(n[:min(len(n), 4)], [])
    scores = {}
    vus = 0
    for mot, freq, norm in seau:
        if not norm.startswith(n):
            continue
        scores[mot] = float(freq)
        vus += 1
        if vus >= POOL:
            break
    for mot in contexte_mots:
        if plie(mot).startswith(n):
            scores[mot] = scores.get(mot, 0.0) + 150_000.0
    return [m for m, _ in sorted(scores.items(), key=lambda kv: -kv[1])[:MAX_LB]]


def rangee_fr(prefixe, seaux_fr):
    if len(prefixe) < MIN_FR:
        return []
    seau = seaux_fr.get(prefixe.lower()[:MIN_FR], [])
    c = sorted((x for x in seau if x[0].startswith(prefixe.lower())),
               key=lambda x: (-x[1], len(x[0])))
    return [m for m, _ in c[:MAX_FR]]


def contexte(historique, ngrams):
    if len(historique) >= 2:
        cle = " ".join(historique[-2:]).lower()
        if cle in ngrams:
            return [c["word"] for c in ngrams[cle][:5]]
    if historique:
        cle = historique[-1].lower()
        if cle in ngrams:
            return [c["word"] for c in ngrams[cle][:5]]
    return []


def main():
    import zls_source
    dico, ngrams, lb_connu, fr_connu, seaux_lb, seaux_fr = charger()
    print(f"dictionnaire {len(dico)} formes · {len(ngrams)} contextes\n")

    entrainement = set()
    try:
        from datasets import load_dataset
        for cfg in ("lb-en", "lb-fr"):
            for r in load_dataset("fredxlpy/LuxAlign", cfg, split="train"):
                entrainement.add(r["lb"])
        for cfg in ("LETZ-SYN", "LETZ-WoT"):
            d = load_dataset("fredxlpy/LETZ", cfg)
            for sp in d:
                for r in d[sp]:
                    entrainement.add(r["text"])
    except Exception as exc:
        print(f"⚠️ corpus d'entraînement indisponible ({exc}) : recouvrement non filtré")
    segments = zls_source.segments_inedits(entrainement) if entrainement else zls_source.segments()
    print(f"corpus ZLS : {len(segments)} segments inédits\n")

    # ------------------------------------------------------------------
    print("1️⃣  ÉCONOMIE DE FRAPPES (KSR) — colonne luxembourgeoise")
    frappes_sans = frappes_avec = 0
    mots_traites = 0
    for seg in segments[:3000]:
        mots = MOTIF.findall(seg["lb"])
        historique = []
        for mot in mots:
            frappes_sans += len(mot)
            cible = contexte(historique, ngrams)
            touchee = None
            # Avant la première lettre : la prédiction contextuelle suffit-elle ?
            if mot in cible[:MAX_LB]:
                touchee = 0
            else:
                for k in range(1, len(mot)):
                    proposees = rangee_lb(mot[:k], cible, seaux_lb) + rangee_fr(mot[:k], seaux_fr)
                    if mot in proposees:
                        touchee = k
                        break
            frappes_avec += (touchee + 1) if touchee is not None else len(mot)
            historique.append(mot)
            mots_traites += 1
    ksr = 100 * (1 - frappes_avec / frappes_sans)
    print(f"   {mots_traites} mots · {frappes_sans} frappes sans aide, "
          f"{frappes_avec} avec")
    print(f"   → économie de frappes : {ksr:.1f} %\n")

    # ------------------------------------------------------------------
    print("2️⃣  SOULIGNEMENT ABUSIF — mots corrects que le correcteur conteste")
    for langue, champ in (("luxembourgeois", "lb"), ("français", "fr"), ("allemand", "de")):
        vus = souligne = 0
        exemples = []
        for seg in segments[:3000]:
            for mot in MOTIF.findall(seg[champ]):
                if len(mot) < 2:
                    continue
                vus += 1
                if plie(mot) not in lb_connu and not fr_connu(mot):
                    souligne += 1
                    if len(exemples) < 6:
                        exemples.append(mot)
        print(f"   {langue:15} {100*souligne/max(vus,1):5.1f} %  "
              f"({souligne}/{vus})   ex. {', '.join(exemples)}")
    print()

    # ------------------------------------------------------------------
    print("3️⃣  CORRECTION INDUE — le clavier propose de corriger un mot juste")
    print("   (le repli Levenshtein se déclenche : aucun préfixe luxembourgeois,")
    print("    trois lettres ou plus, et le mot n'est pas du français reconnu)")
    for langue, champ in (("luxembourgeois", "lb"), ("français", "fr"), ("allemand", "de")):
        vus = indues = 0
        exemples = []
        for seg in segments[:3000]:
            for mot in MOTIF.findall(seg[champ]):
                if len(mot) < MIN_REPLI:
                    continue
                vus += 1
                n = plie(mot)
                a_prefixe = bool(rangee_lb(mot, [], seaux_lb))
                if a_prefixe:
                    continue
                if fr_connu(mot):                # devraitCorriger, depuis la 11.4.1
                    continue
                indues += 1
                if len(exemples) < 6:
                    exemples.append(mot)
        print(f"   {langue:15} {100*indues/max(vus,1):5.1f} %  "
              f"({indues}/{vus})   ex. {', '.join(exemples)}")


if __name__ == "__main__":
    main()
