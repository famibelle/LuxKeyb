#!/usr/bin/env python3
"""Confronte les deux bancs LuxASR : le service seul et la chaîne du téléphone.

    python stt/bench/compare_luxasr.py --hote R/host.json --tel R/device.json

Les deux fichiers portent les mêmes tranches, notées par le même alignement par
infixe contre la même référence humaine. L'appariement est ce qui compte : la
référence du corpus couvre mal certaines tranches et gonfle les deux WER de la
même manière, si bien que **l'écart** est mesuré proprement là où les valeurs
absolues, elles, sont pessimistes.
"""

import argparse
import json
import statistics
from pathlib import Path


def charger(p):
    return {l["id"]: l for l in json.loads(Path(p).read_text(encoding="utf-8"))}


def pondere(lignes):
    m = sum(l["mots"] for l in lignes)
    return sum(l["wer"] * l["mots"] for l in lignes) / m if m else float("nan")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--hote", required=True)
    ap.add_argument("--tel", required=True)
    ap.add_argument("--out", type=Path)
    ap.add_argument("--work", type=Path, help="dossier de travail : active le WER "
                    "de pipeline (tranches d'un fichier recollées contre la "
                    "référence entière), protocole du banc du 28 août")
    args = ap.parse_args()

    H, T = charger(args.hote), charger(args.tel)
    communs = [i for i in T if i in H]
    muets = [i for i in communs if not T[i]["hyp"]]

    print(f"{len(communs)} tranches communes, "
          f"{sum(T[i]['dur'] for i in communs):.0f} s d'audio\n")
    print(f"{'tranche':<18}{'durée':>6}{'hôte':>8}{'tél.':>8}{'écart':>8}   texte du téléphone")
    for i in communs:
        h, t = H[i], T[i]
        print(f"{i:<18}{t['dur']:5.1f}s{h['wer']*100:7.1f}%{t['wer']*100:7.1f}%"
              f"{(t['wer']-h['wer'])*100:+7.1f}   {t['hyp'][:52]}")

    hl = [H[i] for i in communs]
    tl = [T[i] for i in communs]
    ecarts = [T[i]["wer"] - H[i]["wer"] for i in communs if T[i]["hyp"]]
    lat = [T[i]["t_final_apres_audio"] for i in communs
           if T[i].get("t_final_apres_audio")]
    prem = [T[i]["t_premier_texte"] for i in communs if T[i].get("t_premier_texte")]

    print(f"\nWER pondéré        service seul {pondere(hl)*100:5.1f} %"
          f"   ·   téléphone {pondere(tl)*100:5.1f} %")
    print(f"WER médian         service seul {statistics.median(l['wer'] for l in hl)*100:5.1f} %"
          f"   ·   téléphone {statistics.median(l['wer'] for l in tl)*100:5.1f} %")
    if ecarts:
        print(f"écart apparié      médian {statistics.median(ecarts)*100:+.1f} pt"
              f"   ·   moyen {statistics.mean(ecarts)*100:+.1f} pt")
    print(f"tranches muettes   {len(muets)}/{len(communs)}"
          + (f"  ({', '.join(muets)})" if muets else ""))
    if prem:
        print(f"1er texte affiché  médiane {statistics.median(prem):.1f} s "
              f"après le début de la parole")
    if lat:
        print(f"texte final        médiane {statistics.median(lat):.1f} s après la fin "
              f"de la parole (dont 1,5 s de détection de fin d'énoncé)")

    if args.work:
        import sys as _sys
        _sys.path.insert(0, str(Path(__file__).parent))
        from wer import wer as wer_standard
        manif = {e["id"]: e for e in json.loads(
            (args.work / "manifest.json").read_text(encoding="utf-8"))}
        tranches = {}
        for x in json.loads((args.work / "slices.json").read_text(encoding="utf-8")):
            tranches.setdefault(x["parent"], []).append(x["id"])
        print("\n— WER de pipeline (tranches recollées contre la référence du "
              "fichier de 60 s ;\n  même protocole que le banc embarqué du "
              "28 août : tiny 72,1 %, base 36,0 %) —")
        for parent, ids in sorted(tranches.items()):
            for nom, src in (("service seul", H), ("téléphone", T)):
                if not all(i in src for i in ids):
                    continue
                hyp = " ".join(src[i]["hyp"] for i in sorted(ids))
                w, n, *_ = wer_standard(manif[parent]["reference"], hyp)
                print(f"  {parent}  {nom:<13} {w*100:5.1f} %  ({n} mots de référence)")

    if args.out:
        args.out.write_text(json.dumps({
            "tranches": len(communs),
            "wer_pondere_hote": pondere(hl), "wer_pondere_tel": pondere(tl),
            "wer_median_hote": statistics.median(l["wer"] for l in hl),
            "wer_median_tel": statistics.median(l["wer"] for l in tl),
            "ecart_median": statistics.median(ecarts) if ecarts else None,
            "muettes": muets,
            "t_premier_texte_median": statistics.median(prem) if prem else None,
            "t_final_median": statistics.median(lat) if lat else None,
        }, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
