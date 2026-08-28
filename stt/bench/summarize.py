#!/usr/bin/env python3
"""Agrège les mesures brutes en chiffres publiables."""
import json, statistics, sys
from pathlib import Path


def pct(x): return f"{x*100:.1f} %"


def q(v, p):
    v = sorted(v)
    i = max(0, min(len(v) - 1, int(round(p * (len(v) - 1)))))
    return v[i]


def summarize_full(res, label):
    rows = res["rows"]
    w = [r["wer"] for r in rows]
    wt = [r["wer_trim"] for r in rows]
    c = [r["cer"] for r in rows]
    rtf = [r["rtf"] for r in rows]
    rep = [r["rep"] for r in rows]
    # WER agrégé : erreurs totales ÷ mots totaux. C'est le chiffre à publier ;
    # la moyenne des WER par fichier surpondère les fichiers courts.
    err = sum(r["sub"] + r["ins"] + r["del"] for r in rows)
    words = sum(r["ref_words"] for r in rows)

    print(f"\n╔══ {label} — {len(rows)} fichiers, {sum(r['audio_s'] for r in rows)/60:.0f} min")
    print(f"║ WER agrégé          {pct(err/words)}   ({err} erreurs / {words} mots)")
    print(f"║   substitutions     {pct(sum(r['sub'] for r in rows)/words)}")
    print(f"║   insertions        {pct(sum(r['ins'] for r in rows)/words)}")
    print(f"║   omissions         {pct(sum(r['del'] for r in rows)/words)}")
    print(f"║ WER médian /fichier {pct(statistics.median(w))}   "
          f"[p10 {pct(q(w,.1))} · p90 {pct(q(w,.9))}]")
    print(f"║ WER rogné (bords)   {pct(statistics.median(wt))} médian")
    print(f"║ CER médian          {pct(statistics.median(c))}   "
          f"[p10 {pct(q(c,.1))} · p90 {pct(q(c,.9))}]")
    print(f"║ RTF médian          {statistics.median(rtf):.3f}  "
          f"({1/statistics.median(rtf):.0f}× le temps réel, {res['threads']} threads)")
    print(f"║ chargement          {res['load_ms']:.0f} ms")
    boucles = [r for r in rows if r["rep"] > 0.15]
    print(f"║ boucles de décodage {len(boucles)}/{len(rows)} fichiers "
          f"(répétition > 15 %), max {pct(max(rep))}")
    return {
        "n": len(rows), "wer": err/words, "wer_median": statistics.median(w),
        "wer_p10": q(w,.1), "wer_p90": q(w,.9),
        "wer_trim_median": statistics.median(wt),
        "cer_median": statistics.median(c), "cer_p10": q(c,.1), "cer_p90": q(c,.9),
        "sub": sum(r['sub'] for r in rows)/words, "ins": sum(r['ins'] for r in rows)/words,
        "dele": sum(r['del'] for r in rows)/words,
        "rtf_median": statistics.median(rtf), "load_ms": res["load_ms"],
        "loops": len(boucles), "rep_max": max(rep),
        "per_file": [{"id": r["id"], "wer": r["wer"], "cer": r["cer"],
                      "rep": r["rep"], "rtf": r["rtf"]} for r in rows],
    }


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    dest = next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--out=")), None)
    out = {}
    for arg in args:
        label, path = arg.split("=", 1)
        res = json.loads(Path(path).read_text(encoding="utf-8"))
        out[label] = summarize_full(res, label)
    print()
    if dest:
        Path(dest).write_text(json.dumps(out, ensure_ascii=False, indent=1),
                              encoding="utf-8")
        print(f"💾 {dest}")
