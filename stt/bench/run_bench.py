#!/usr/bin/env python3
"""Lance le harnais et agrège les mesures.

    python stt/bench/run_bench.py --stage full   --work W --model M --out R.json
    python stt/bench/run_bench.py --stage stream --work W --model M --out R.json

Étape `full`  : une passe par fichier de 60 s → qualité et vitesse du modèle.
Étape `stream`: rejeu de la cadence de SttSession sur chaque tranche → ce que
                l'utilisateur voit et à quel moment.
"""

import argparse
import json
import statistics
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from wer import wer, cer, repetition_ratio, normalize


def log(m):
    print(m, flush=True)


def run_harness(binary, model, paths, mode, threads, work):
    lst = work / f"inputs_{mode}.txt"
    lst.write_text("\n".join(str(p) for p in paths), encoding="utf-8")
    cmd = [str(binary), "--model", str(model), "--input", f"@{lst}",
           "--mode", mode, "--threads", str(threads)]
    log(f"$ {' '.join(cmd)}")
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                            text=True, bufsize=1)
    for line in proc.stdout:
        line = line.strip()
        if line.startswith("{"):
            yield json.loads(line)
    proc.wait()


def stage_full(args):
    manifest = json.loads((args.work / "manifest.json").read_text(encoding="utf-8"))
    by_path = {e["f32"]: e for e in manifest}
    paths = [e["f32"] for e in manifest]

    log(f"\n=== Étape 1 — le modèle seul, {len(paths)} fichiers de 60 s ===")
    rows, load_ms = [], None
    for rec in run_harness(args.binary, args.model, paths, "full", args.threads, args.work):
        if rec["kind"] == "load":
            load_ms = rec["ms"]
            log(f"modèle chargé en {load_ms:.0f} ms, {rec['threads']} threads")
            continue
        e = by_path[rec["file"]]
        hyp, ref = rec["text"], e["reference"]
        w, n, s, i, d = wer(ref, hyp)
        wt = wer(ref, hyp, trim_edges=True)[0]
        rows.append({
            "id": e["id"], "audio_s": rec["audio_s"], "ms": rec["ms"],
            "rtf": rec["ms"] / 1000.0 / rec["audio_s"],
            "wer": w, "wer_trim": wt, "cer": cer(ref, hyp),
            "cer_trim": cer(ref, hyp, trim_edges=True),
            "ref_words": n, "sub": s, "ins": i, "del": d,
            "rep": repetition_ratio(hyp),
            "hyp": hyp, "ref": ref,
        })
        log(f"  [{len(rows):2d}/{len(paths)}] {e['id']}  "
            f"WER {w*100:5.1f} %  CER {rows[-1]['cer']*100:5.1f} %  "
            f"rép {rows[-1]['rep']*100:4.1f} %  {rec['ms']:6.0f} ms")
    return {"load_ms": load_ms, "rows": rows}


def stage_stream(args):
    slices = json.loads((args.work / "slices.json").read_text(encoding="utf-8"))
    by_path = {s["f32"]: s for s in slices}
    paths = [s["f32"] for s in slices]

    log(f"\n=== Étape 2 — le pipeline vécu, {len(paths)} tranches ===")
    per, cur, load_ms = [], None, None
    for rec in run_harness(args.binary, args.model, paths, "stream", args.threads, args.work):
        if rec["kind"] == "load":
            load_ms = rec["ms"]
            continue
        if cur is None or cur["f32"] != rec["file"]:
            cur = {"f32": rec["file"], "partials": [], "final": None}
            per.append(cur)
        if rec["kind"] == "partial":
            cur["partials"].append(rec)
        else:
            cur["final"] = rec
            s = by_path[rec["file"]]
            p = cur["partials"]
            first = p[0]["t_shown_ms"] if p else None
            premiere = f"{first/1000:.1f}s" if first else "jamais"
            log(f"  [{len(per):3d}/{len(paths)}] {s['id']} {s['dur']:5.1f}s : "
                f"{len(p)} hyp., 1re à {premiere}, finale +{rec['ms']:.0f} ms")
    return {"load_ms": load_ms, "utterances": per,
            "slices": {s["f32"]: s for s in slices}}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--stage", choices=["full", "stream"], required=True)
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("--model", type=Path, required=True)
    ap.add_argument("--binary", type=Path, required=True)
    ap.add_argument("--threads", type=int, default=3)
    ap.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()

    res = stage_full(args) if args.stage == "full" else stage_stream(args)
    res["model"] = str(args.model)
    res["threads"] = args.threads
    args.out.write_text(json.dumps(res, ensure_ascii=False), encoding="utf-8")
    log(f"\n💾 {args.out}")


if __name__ == "__main__":
    main()
