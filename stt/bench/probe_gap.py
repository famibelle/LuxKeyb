#!/usr/bin/env python3
"""Sonde du protocole LuxASR : que se passe-t-il quand on cesse d'émettre ?

    python stt/bench/probe_gap.py --work W --out R.json [--fichiers 3] [--repet 2]

Trois questions, auxquelles rien dans le protocole publié ne répond, et dont
dépend l'architecture de la dictée :

1. **Le découpeur du service compte-t-il en échantillons reçus ou en temps
   réel ?** S'il décode pendant qu'on n'envoie plus rien, c'est l'horloge ; sinon
   ce sont les échantillons. Suspendre le flux sur les silences n'a pas le même
   sens dans les deux cas.
2. **Le contexte inter-segments survit-il à une interruption du flux ?** Le
   service annonce `context_management.enabled` ; reste à savoir s'il tient à
   travers un trou.
3. **La queue hallucinée vient-elle bien du silence envoyé ?** Toute
   l'architecture actuelle repose sur cette causalité — `SILENCE_HANGOVER_MS`
   n'existe que pour ne pas laisser de blanc au modèle — et elle n'a été
   observée qu'une fois, à 10 s de blanc, sur un extrait.

Cinq conditions sur le même audio, le trou étant placé dans une pause que le
locuteur a réellement laissée (sinon on mesurerait la coupure en plein mot, pas
la reprise) :

    plein            l'énoncé tel quel, « stop » dès la fin
    silence_insere   G s de silence ÉMISES au milieu
    coupure          G s pendant lesquelles on n'ÉMET RIEN, l'horloge avançant
    queue_silence    T s de silence ÉMISES avant « stop »
    queue_coupure    T s sans émission avant « stop »

`plein` vs `silence_insere` isole le coût du silence lui-même ; `silence_insere`
vs `coupure` isole celui de l'interruption. Les deux dernières rejouent la
situation qui a fait broder le service.
"""

import argparse
import json
import statistics as st
import sys
import time
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import vad
from luxasr_client import RATE, CONFIG_APP, decouper, stream
from bench_luxasr import wer_infixe
from wer import repetition_ratio


# Délai au-delà duquel une hypothèse ne peut plus être le décodage de ce qui
# précédait le trou. Le service rend en ~270 ms ; 1,5 s laisse large.
MARGE_TROU = 1.5


def plan_simple(a):
    return [(True, b) for b in decouper(a)]


def plan_trou(a, debut_s, duree_s, emettre):
    """Plan où `duree_s` de silence s'intercalent à `debut_s`.

    Le silence occupe toujours la même place dans le temps ; seul son droit à
    partir sur le réseau change. C'est la seule façon de comparer « silence
    envoyé » et « flux suspendu » à chronologie identique.
    """
    i = int(debut_s * RATE)
    creux = np.zeros(int(duree_s * RATE), dtype="<f4")
    return (plan_simple(a[:i])
            + [(emettre, b) for b in decouper(creux)]
            + plan_simple(a[i:]))


def pause_mediane(a, mini=0.5):
    """Centre d'une pause réelle, cherchée dans le tiers central de l'énoncé."""
    d = len(a) / RATE
    cand = [(x, y) for x, y in vad.pauses(a, mini_s=mini) if d / 3 < (x + y) / 2 < 2 * d / 3]
    if not cand:
        return d / 2, 0.0
    x, y = max(cand, key=lambda p: p[1] - p[0])
    return (x + y) / 2, y - x


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--fichiers", type=int, default=3)
    ap.add_argument("--duree", type=float, default=22.0, help="secondes d'audio utilisées")
    ap.add_argument("--trou", type=float, default=3.0, help="durée du trou médian")
    ap.add_argument("--queue", type=float, default=8.0, help="silence final avant stop")
    ap.add_argument("--repet", type=int, default=2, help="passages par condition")
    ap.add_argument("--defaut-serveur", action="store_true",
                    help="ne pas envoyer chunk_params (5,0 / 0,8 au lieu de 2,0 / 0,5)")
    args = ap.parse_args()

    config = None if args.defaut_serveur else CONFIG_APP
    manifest = json.loads((args.work / "manifest.json").read_text(encoding="utf-8"))
    manifest = manifest[:args.fichiers]

    lignes = []
    for e in manifest:
        a = np.fromfile(e["f32"], dtype="<f4")[:int(args.duree * RATE)]
        centre, longueur = pause_mediane(a)
        print(f"\n📄 {e['id']}  {len(a)/RATE:.0f} s  ·  pause à {centre:.1f} s "
              f"({longueur:.1f} s observée)", flush=True)

        conditions = {
            "plein": (plan_simple(a), None),
            "silence_insere": (plan_trou(a, centre, args.trou, True), (centre, centre + args.trou)),
            "coupure": (plan_trou(a, centre, args.trou, False), (centre, centre + args.trou)),
            "queue_silence": (plan_simple(a) + [(True, b) for b in
                              decouper(np.zeros(int(args.queue * RATE), dtype="<f4"))],
                              (len(a) / RATE, len(a) / RATE + args.queue)),
            "queue_coupure": (plan_simple(a) + [(False, b) for b in
                              decouper(np.zeros(int(args.queue * RATE), dtype="<f4"))],
                              (len(a) / RATE, len(a) / RATE + args.queue)),
        }

        for nom, (plan, fenetre) in conditions.items():
            for r in range(args.repet):
                try:
                    res = stream(plan, config=config)
                except Exception as exc:
                    print(f"   {nom} #{r+1} ÉCHEC {exc}", flush=True)
                    continue
                w, nh = wer_infixe(e["reference"], res["texte"])
                # Marge : une hypothèse qui tombe juste après le début du trou
                # peut n'être que le décodage de ce qui précède — le service
                # rend en ~270 ms. On ne compte que ce qui arrive assez tard
                # pour n'avoir aucune autre explication que l'horloge.
                pendant = (sum(1 for p in res["passes"]
                               if fenetre[0] + MARGE_TROU <= p["t"] <= fenetre[1])
                           if fenetre else 0)
                lignes.append({
                    "fichier": e["id"], "condition": nom, "essai": r + 1,
                    "wer": w, "mots": nh, "passes": len(res["passes"]),
                    "passes_pendant_trou": pendant,
                    "repetition": repetition_ratio(res["texte"]),
                    "t_premier_texte": res["t_first"],
                    "audio_emis_s": res["audio_emis_s"],
                    "duree_plan_s": res["duree_plan_s"],
                    "pause_s": longueur, "hyp": res["texte"],
                    "t_passes": [round(p["t"], 2) for p in res["passes"]],
                    "fenetre": list(fenetre) if fenetre else None,
                })
                print(f"   {nom:15} #{r+1}  WER {w*100:5.1f} %  {nh:3d} mots  "
                      f"{len(res['passes'])} passes (dont {pendant} dans le trou)  "
                      f"rép {lignes[-1]['repetition']*100:4.1f} %", flush=True)
                time.sleep(1.0)      # ne pas enchaîner les sessions sur le service

    args.out.write_text(json.dumps(lignes, ensure_ascii=False, indent=1), encoding="utf-8")
    resume(lignes)
    print(f"\n💾 {args.out}")


def resume(lignes):
    if not lignes:
        return
    noms = ["plein", "silence_insere", "coupure", "queue_silence", "queue_coupure"]
    print(f"\n{'condition':16} {'WER':>7} {'mots':>6} {'passes':>7} "
          f"{'dans le trou':>13} {'répétition':>11}")
    for nom in noms:
        g = [l for l in lignes if l["condition"] == nom]
        if not g:
            continue
        pond = sum(l["wer"] * l["mots"] for l in g) / max(1, sum(l["mots"] for l in g))
        print(f"  {nom:14} {pond*100:6.1f} % {st.median(l['mots'] for l in g):6.0f} "
              f"{st.median(l['passes'] for l in g):7.0f} "
              f"{sum(l['passes_pendant_trou'] for l in g):13d} "
              f"{st.median(l['repetition'] for l in g)*100:10.1f} %")

    trou = [l for l in lignes if l["condition"] == "coupure"]
    if trou:
        n = sum(l["passes_pendant_trou"] for l in trou)
        print("\n1. Découpeur : " + (
            f"{n} hypothèse(s) rendue(s) plus de {MARGE_TROU:.1f} s après le début "
            "du trou, alors que rien n'était émis → le service décode sur "
            "l'horloge, pas sur les échantillons reçus."
            if n else
            "aucune hypothèse pendant le flux suspendu → le service décode sur "
            "les échantillons reçus ; suspendre le flux suspend le décodage."))
    a = [l for l in lignes if l["condition"] == "silence_insere"]
    if a and trou:
        wa = sum(l["wer"] * l["mots"] for l in a) / max(1, sum(l["mots"] for l in a))
        wc = sum(l["wer"] * l["mots"] for l in trou) / max(1, sum(l["mots"] for l in trou))
        print(f"2. Contexte : WER {wa*100:.1f} % silence émis contre {wc*100:.1f} % "
              f"flux suspendu ({(wc-wa)*100:+.1f} pt pour la suspension).")
    qs = [l for l in lignes if l["condition"] == "queue_silence"]
    qc = [l for l in lignes if l["condition"] == "queue_coupure"]
    pl = [l for l in lignes if l["condition"] == "plein"]
    if qs and qc and pl:
        def m(g, k):
            return st.median(l[k] for l in g)
        print(f"3. Queue : mots {m(pl,'mots'):.0f} (plein) → {m(qs,'mots'):.0f} "
              f"(silence émis) / {m(qc,'mots'):.0f} (flux suspendu) ; "
              f"répétition {m(pl,'repetition')*100:.1f} % → "
              f"{m(qs,'repetition')*100:.1f} % / {m(qc,'repetition')*100:.1f} %.")


if __name__ == "__main__":
    main()
