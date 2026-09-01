#!/usr/bin/env python3
"""Compare l'API par lots `/asr2` au WebSocket temps réel, sur les mêmes tranches.

    python stt/bench/bench_api.py --work W --contre R/host_luxasr.json --out R/api.json

Les deux points de terminaison de LuxASR ne servent pas le même modèle : le
WebSocket sert un whisper tiny affiné, l'API par lots vraisemblablement bien
plus gros. Le but est de chiffrer l'écart sur des tranches déjà mesurées, sans
rejouer l'audio : mêmes identifiants, même référence, même notation infixe.

Les appels sont **séquentiels**, un travail à la fois. L'accès est limité et
accordé pour un usage de laboratoire ; saturer leur file n'est pas une option.
"""

import argparse
import json
import statistics as st
import sys
import time
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
from bench_luxasr import wer_infixe
from luxasr_api import transcrire
from wer import repetition_ratio


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("--contre", type=Path, help="résultats WebSocket à comparer")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--max", type=int, default=0)
    ap.add_argument("--prompt", default="")
    ap.add_argument("--beam", type=int, default=0)
    ap.add_argument("--intervalle", type=float, default=1.0,
                    help="période d'interrogation du job")
    ap.add_argument("--pause", type=float, default=1.0,
                    help="attente entre deux travaux, par courtoisie")
    args = ap.parse_args()

    refs = {e["id"]: e["reference"]
            for e in json.loads((args.work / "manifest.json").read_text())}
    tranches = json.loads((args.work / "slices.json").read_text())
    if args.max:
        tranches = tranches[:args.max]
    ancien = {}
    if args.contre and args.contre.exists():
        # accepte les deux formats : la liste brute du banc WebSocket et le
        # {"agrege", "lignes"} produit ici, pour comparer un passage au suivant.
        brut = json.loads(args.contre.read_text())
        ancien = {l["id"]: l for l in (brut["lignes"] if isinstance(brut, dict)
                                       else brut)}

    lignes = []
    for n, s in enumerate(tranches, 1):
        a = np.fromfile(s["f32"], dtype="<f4")
        try:
            texte, m = transcrire(a, prompt=args.prompt or None,
                                  beam_size=args.beam or None,
                                  intervalle=args.intervalle, nom=s["id"] + ".wav")
        except Exception as e:
            print(f"  {s['id']} ⚠️  {type(e).__name__}: {e}", flush=True)
            continue
        w, mots = wer_infixe(refs[s["parent"]], texte) if texte else (1.0, 0)
        l = {"id": s["id"], "dur": s["dur"], "wer": w, "mots": mots,
             "repetition": repetition_ratio(texte), "hyp": texte, **m}
        lignes.append(l)
        av = ancien.get(s["id"])
        comp = f"   ws {av['wer']*100:5.1f} %" if av else ""
        print(f"[{n}/{len(tranches)}] {s['id']:22} {s['dur']:5.1f}s  "
              f"api {w*100:5.1f} % {mots:3d} mots  {m['t_total_s']:5.1f}s{comp}",
              flush=True)
        time.sleep(args.pause)

    utiles = [l for l in lignes if l["mots"]]
    agg = {
        "n": len(lignes),
        "wer_pondere": sum(l["wer"] * l["mots"] for l in utiles) / sum(l["mots"] for l in utiles),
        "wer_median": st.median(l["wer"] for l in utiles),
        "t_total_median": st.median(l["t_total_s"] for l in lignes),
        "t_total_max": max(l["t_total_s"] for l in lignes),
        "repetition_max": max(l["repetition"] for l in lignes),
    }
    print(f"\n  API : WER pondéré {agg['wer_pondere']*100:.1f} % · "
          f"médiane {agg['wer_median']*100:.1f} % · "
          f"latence médiane {agg['t_total_median']:.1f} s "
          f"(max {agg['t_total_max']:.1f} s)")

    if ancien:
        paires = [(ancien[l["id"]]["wer"], l["wer"]) for l in utiles
                  if l["id"] in ancien and ancien[l["id"]]["mots"]]
        if paires:
            ws = [a for a, _ in paires]
            api = [b for _, b in paires]
            d = [b - a for a, b in paires]
            agg["apparie"] = {
                "n": len(paires),
                "ws_median": st.median(ws), "api_median": st.median(api),
                "ecart_median": st.median(d), "ecart_moyen": sum(d) / len(d),
                "api_meilleur": sum(1 for x in d if x < 0),
            }
            print(f"  apparié sur {len(paires)} tranches : WS {st.median(ws)*100:.1f} % → "
                  f"API {st.median(api)*100:.1f} % · écart médian {st.median(d)*100:+.1f} pt · "
                  f"l'API gagne {agg['apparie']['api_meilleur']}/{len(paires)} fois")

    args.out.write_text(json.dumps({"agrege": agg, "lignes": lignes},
                                   ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"\n💾 {args.out}")


if __name__ == "__main__":
    main()
