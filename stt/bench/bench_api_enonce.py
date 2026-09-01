#!/usr/bin/env python3
"""L'API par lots dans le régime que le clavier vise : une à trois phrases.

    python stt/bench/bench_api_enonce.py --work W --out R/api_enonce.json

Les tranches de `prepare_dataset.py` vont de 2 à 15 s et sont calibrées pour
mesurer un moteur ; elles ne ressemblent pas à une dictée. Ici on découpe des
fenêtres de 8 à 22 s **bornées par des pauses du locuteur**, comme le fait
`bench_continu.py`, pour que chaque envoi soit un énoncé plausible : une à
trois phrases complètes, commencées et terminées au bon endroit.

Trois grandeurs sont rendues par énoncé, car ce sont les trois qui décident si
la dictée est utilisable : la durée de l'audio, le WER, et le délai entre la
fin de la parole et le texte — soumission, file d'attente et traitement
compris. Le délai est ce que l'utilisateur vit après avoir appuyé sur stop.

Appels séquentiels, un travail à la fois : l'accès est limité.
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
from bench_luxasr import wer_infixe
from luxasr_api import transcrire
from wer import repetition_ratio

RATE = 16000


def fenetres(a, lo, hi, mini_pause=0.5):
    """Fenêtres de `lo` à `hi` s, bornées par des pauses — cf. bench_continu."""
    bornes = [0.0] + [(x + y) / 2 for x, y in vad.pauses(a, mini_s=mini_pause)]
    bornes.append(len(a) / RATE)
    out, debut = [], 0.0
    for b in bornes[1:]:
        if b - debut < lo:
            continue
        if b - debut > hi:
            debut = b
            continue
        out.append((debut, b))
        debut = b
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--lo", type=float, default=8.0)
    ap.add_argument("--hi", type=float, default=22.0)
    ap.add_argument("--fichiers", type=int, default=6)
    ap.add_argument("--prompt", default="")
    ap.add_argument("--beam", type=int, default=0)
    ap.add_argument("--intervalle", type=float, default=0.2)
    ap.add_argument("--pause", type=float, default=0.5)
    args = ap.parse_args()

    manifeste = json.loads((args.work / "manifest.json").read_text())[:args.fichiers]

    lignes = []
    for e in manifeste:
        a = np.fromfile(e["f32"], dtype="<f4")
        for k, (d, f) in enumerate(fenetres(a, args.lo, args.hi)):
            bout = a[int(d * RATE):int(f * RATE)]
            duree = len(bout) / RATE
            try:
                texte, m = transcrire(bout, prompt=args.prompt or None,
                                      beam_size=args.beam or None,
                                      intervalle=args.intervalle,
                                      nom=f"{e['id']}_{k:02d}.wav")
            except Exception as exc:
                print(f"  ⚠️  {e['id']}_{k:02d} {type(exc).__name__}: {exc}", flush=True)
                continue
            w, mots = wer_infixe(e["reference"], texte) if texte else (1.0, 0)
            lignes.append({"id": f"{e['id']}_{k:02d}", "duree_s": round(duree, 1),
                           "wer": w, "mots": mots,
                           "repetition": repetition_ratio(texte),
                           "delai_s": m["t_total_s"],
                           "t_soumission_s": m["t_soumission_s"],
                           "hyp": texte})
            print(f"  {lignes[-1]['id']:22} {duree:5.1f}s  WER {w*100:5.1f} %  "
                  f"{mots:3d} mots  délai {m['t_total_s']:5.2f}s", flush=True)
            time.sleep(args.pause)

    utiles = [l for l in lignes if l["mots"]]
    agg = {
        "n": len(lignes),
        "duree_mediane_s": st.median(l["duree_s"] for l in lignes),
        "duree_min_s": min(l["duree_s"] for l in lignes),
        "duree_max_s": max(l["duree_s"] for l in lignes),
        "audio_total_s": round(sum(l["duree_s"] for l in lignes), 1),
        "wer_pondere": sum(l["wer"] * l["mots"] for l in utiles) / sum(l["mots"] for l in utiles),
        "wer_median": st.median(l["wer"] for l in utiles),
        "wer_p90": sorted(l["wer"] for l in utiles)[int(0.9 * (len(utiles) - 1))],
        "delai_median_s": st.median(l["delai_s"] for l in lignes),
        "delai_max_s": max(l["delai_s"] for l in lignes),
        "delai_sur_duree": st.median(l["delai_s"] / l["duree_s"] for l in lignes),
        "repetition_max": max(l["repetition"] for l in lignes),
    }
    print(f"\n  {agg['n']} énoncés · durée médiane {agg['duree_mediane_s']:.1f} s "
          f"({agg['duree_min_s']:.1f}–{agg['duree_max_s']:.1f} s)")
    print(f"  WER pondéré {agg['wer_pondere']*100:.1f} % · médiane "
          f"{agg['wer_median']*100:.1f} % · 9e décile {agg['wer_p90']*100:.1f} %")
    print(f"  délai médian {agg['delai_median_s']:.2f} s "
          f"(max {agg['delai_max_s']:.2f} s), soit "
          f"{agg['delai_sur_duree']*100:.0f} % de la durée de l'audio")

    args.out.write_text(json.dumps({"agrege": agg, "lignes": lignes},
                                   ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"\n💾 {args.out}")


if __name__ == "__main__":
    main()
