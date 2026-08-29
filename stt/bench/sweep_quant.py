#!/usr/bin/env python3
"""Quantification, attention flash, threads : les leviers qui ne cassent rien.

Contrairement à `audio_ctx`, aucun de ces trois paramètres ne change ce que le
modèle voit. La quantification déplace le compromis taille/justesse, l'attention
flash réorganise le même calcul, et les threads ne font que le paralléliser :
la dégradation, quand il y en a, est bornée et progressive — là où tronquer le
contexte fait passer le WER au-dessus de 100 % dès 512.
"""

import json
import statistics
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from wer import wer


def run(binary, model, listfile, threads, flash, ctx="0"):
    cmd = [str(binary), "--model", str(model), "--input", f"@{listfile}",
           "--mode", "full", "--threads", str(threads)]
    if ctx != "0":
        cmd += ["--audio-ctx", ctx]
    if flash:
        cmd += ["--flash-attn"]
    raw = subprocess.run(cmd, capture_output=True).stdout
    rows = []
    for line in raw.decode("utf-8", errors="replace").splitlines():
        if line.startswith("{"):
            r = json.loads(line)
            if r.get("kind") == "final":
                rows.append(r)
    return rows


def main(work, out_path, binary, variants):
    slices = json.loads((Path(work) / "slices.json").read_text(encoding="utf-8"))
    manifest = {e["id"]: e for e in
                json.loads((Path(work) / "manifest.json").read_text(encoding="utf-8"))}
    by_path = {s["f32"]: s for s in slices}
    lst = Path(work) / "inputs_quant.txt"
    lst.write_text("\n".join(s["f32"] for s in slices), encoding="utf-8")

    print(f"{'variante':>34} {'WER':>8} {'passe méd.':>11} {'p90':>8}", flush=True)
    print("─" * 66, flush=True)
    out = {}
    for label, (model, threads, flash, ctx) in variants.items():
        rows = run(binary, model, lst, threads, flash, ctx)
        if not rows:
            print(f"{label:>34}   échec", flush=True)
            continue
        per = {}
        for r in rows:
            per.setdefault(by_path[r["file"]]["parent"], []).append(
                (by_path[r["file"]]["id"], r["text"]))
        err = words = 0
        for pid, g in per.items():
            w, n, s_, i_, d_ = wer(manifest[pid]["reference"],
                                   " ".join(t for _, t in sorted(g)))
            err += s_ + i_ + d_
            words += n
        ms = sorted(r["ms"] for r in rows)
        med, p90 = statistics.median(ms), ms[int(0.9 * (len(ms) - 1))]
        out[label] = {"wer": err / words, "ms": med, "p90": p90}
        print(f"{label:>34} {err/words*100:7.1f}% {med:8.0f} ms {p90:6.0f} ms", flush=True)
    Path(out_path).write_text(json.dumps(out, ensure_ascii=False, indent=1),
                              encoding="utf-8")
    print(f"\n💾 {out_path}", flush=True)


if __name__ == "__main__":
    work, out_path, binary, models_dir, tiny = sys.argv[1:6]
    M = Path(models_dir)
    V = {}
    for q in ["q4_0", "q5_0", "q5_1", "q8_0"]:
        V[f"base {q}, 3 threads"] = (M / f"ggml-lb-base-{q}.bin", 3, False, "0")
    V["base q5_1, 6 threads"] = (M / "ggml-lb-base-q5_1.bin", 6, False, "0")
    V["base q5_1, flash"] = (M / "ggml-lb-base-q5_1.bin", 3, True, "0")
    V["base q5_1, 6 threads + flash"] = (M / "ggml-lb-base-q5_1.bin", 6, True, "0")
    V["base q5_1, ctx 768, 6 threads"] = (M / "ggml-lb-base-q5_1.bin", 6, False, "768")
    V["tiny q5_1, 3 threads (référence)"] = (tiny, 3, False, "0")
    main(work, out_path, binary, V)
