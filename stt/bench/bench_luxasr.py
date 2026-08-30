#!/usr/bin/env python3
"""Banc du service LuxASR — témoin hors téléphone.

    python stt/bench/bench_luxasr.py --work W --out R.json [--max 62]

Rejoue chaque tranche en temps réel dans le protocole exact de
`LuxAsrSession`, et note l'hypothèse contre la transcription humaine du
fichier parent.

**Alignement par infixe.** Le corpus ne donne de référence que par fichier de
60 s ; une tranche n'en couvre qu'un fragment. On note donc l'hypothèse contre
la fenêtre de la référence qui lui ressemble le plus — début et fin de
référence gratuits, substitutions, insertions et suppressions comptées
normalement à l'intérieur. C'est légèrement optimiste par construction (la
fenêtre est choisie après coup), et il faut le savoir en lisant les chiffres ;
les tranches étant coupées aux silences, la fenêtre est en pratique contrainte.
"""

import argparse
import json
import statistics
import sys
import time
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
from luxasr_client import transcribe
from wer import normalize


def wer_infixe(ref: str, hyp: str):
    """Distance d'édition mot à mot, bords de la référence gratuits."""
    r, h = normalize(ref).split(), normalize(hyp).split()
    if not h:
        return 1.0, 0
    # prev[j] : coût d'aligner h[:i] sur un suffixe de r[:j]
    prev = [0] * (len(r) + 1)          # démarrer n'importe où dans r : gratuit
    for i in range(1, len(h) + 1):
        cur = [prev[0] + 1] + [0] * len(r)
        for j in range(1, len(r) + 1):
            cur[j] = min(
                prev[j - 1] + (0 if h[i - 1] == r[j - 1] else 1),  # sub/match
                cur[j - 1] + 1,                                    # del (ref)
                prev[j] + 1,                                       # ins (hyp)
            )
        prev = cur
    return min(prev) / len(h), len(h)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--max", type=int, default=0)
    ap.add_argument("--only", default="", help="préfixes d'id séparés par des virgules")
    args = ap.parse_args()

    slices = json.loads((args.work / "slices.json").read_text(encoding="utf-8"))
    refs = {e["id"]: e["reference"]
            for e in json.loads((args.work / "manifest.json").read_text(encoding="utf-8"))}
    if args.only:
        garde = tuple(args.only.split(","))
        slices = [s for s in slices if s["id"].startswith(garde)]
    if args.max:
        slices = slices[:args.max]

    lignes = []
    for n, s in enumerate(slices, 1):
        a = np.fromfile(s["f32"], dtype="<f4")
        t0 = time.monotonic()
        try:
            texte, passes, t_first, t_final = transcribe(a)
        except Exception as e:
            print(f"  [{n:2d}/{len(slices)}] {s['id']} ÉCHEC {e}", flush=True)
            continue
        w, nh = wer_infixe(refs[s["parent"]], texte)
        lignes.append({"id": s["id"], "dur": s["dur"], "wer": w, "mots": nh,
                       "passes": len(passes), "proc_s": sum(passes),
                       "t_premier_mot": t_first, "t_final": t_final,
                       "total_s": time.monotonic() - t0, "hyp": texte})
        print(f"  [{n:2d}/{len(slices)}] {s['id']} {s['dur']:4.1f}s  "
              f"WER {w*100:5.1f} %  {len(passes)} passe(s)  "
              f"finale +{t_final*1000:4.0f} ms", flush=True)

    if lignes:
        pond = sum(l["wer"] * l["mots"] for l in lignes) / sum(l["mots"] for l in lignes)
        print(f"\nWER pondéré {pond*100:.1f} %   "
              f"médiane {statistics.median(l['wer'] for l in lignes)*100:.1f} %   "
              f"finale médiane {statistics.median(l['t_final'] for l in lignes)*1000:.0f} ms")
    args.out.write_text(json.dumps(lignes, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"💾 {args.out}")


if __name__ == "__main__":
    main()
