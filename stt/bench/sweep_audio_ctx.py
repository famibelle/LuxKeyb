#!/usr/bin/env python3
"""Coût en qualité de la réduction du contexte de l'encodeur.

whisper encode toujours 30 s, silence compris : c'est la quasi-totalité du coût
d'une passe, et sur un téléphone milieu de gamme cela fait 6,2 s pour transcrire
quatre secondes de parole, indépendamment de la longueur de l'énoncé (mesuré :
4,5 s → 6 571 ms, 6,9 s → 6 183 ms, 11,0 s → 6 164 ms).

`audio_ctx` tronque ce contexte. La question n'est donc pas s'il fait gagner du
temps — il en fait gagner presque proportionnellement — mais combien il coûte en
justesse. Ce script trace la courbe sur les énoncés courts, qui sont le régime
réel d'un clavier.

`auto` dimensionne le contexte sur la durée de l'énoncé, avec 15 % de marge.
En deçà de ce que dure la parole, ce n'est plus une dégradation mais une
troncature : la fin de l'audio n'est jamais encodée.
"""

import json
import statistics
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from wer import wer

CTX = ["1500", "768", "512", "384", "256", "auto"]


def run(binary, model, listfile, ctx, threads=3):
    # errors="replace" : à contexte réduit, whisper rend parfois une séquence
    # UTF-8 tronquée en fin de segment. Ce n'est pas un artefact du banc — c'est
    # un risque réel pour l'application, où NewStringUTF() reçoit ces octets.
    raw = subprocess.run(
        [str(binary), "--model", str(model), "--input", f"@{listfile}",
         "--mode", "full", "--threads", str(threads), "--audio-ctx", ctx],
        capture_output=True).stdout
    out = raw.decode("utf-8", errors="replace")
    rows = []
    for line in out.splitlines():
        if not line.startswith("{"):
            continue
        r = json.loads(line)
        if r.get("kind") == "final":
            rows.append(r)
    return rows


def main(work, results, binary, models):
    slices = json.loads((Path(work) / "slices.json").read_text(encoding="utf-8"))
    manifest = {e["id"]: e for e in
                json.loads((Path(work) / "manifest.json").read_text(encoding="utf-8"))}
    by_path = {s["f32"]: s for s in slices}
    lst = Path(work) / "inputs_sweep.txt"
    lst.write_text("\n".join(s["f32"] for s in slices), encoding="utf-8")

    out = {}
    for name, model in models.items():
        out[name] = {}
        print(f"\n╔══ {name}")
        print(f"║ {'audio_ctx':>10} {'WER':>8} {'passe médiane':>14} {'gain':>7}")
        base_ms = None
        for ctx in CTX:
            rows = run(binary, model, lst, ctx)
            per_parent = {}
            for r in rows:
                s = by_path[r["file"]]
                per_parent.setdefault(s["parent"], []).append((s["id"], r["text"]))
            err = words = 0
            for pid, group in per_parent.items():
                hyp = " ".join(t for _, t in sorted(group))
                w, n, sub, ins, dele = wer(manifest[pid]["reference"], hyp)
                err += sub + ins + dele
                words += n
            ms = statistics.median(r["ms"] for r in rows)
            if base_ms is None:
                base_ms = ms
            out[name][ctx] = {"wer": err / words, "ms": ms,
                              "ctx_median": statistics.median(r["audio_ctx"] for r in rows)}
            print(f"║ {ctx:>10} {err/words*100:7.1f} % {ms:11.0f} ms {base_ms/ms:6.2f}×")
    Path(results).write_text(json.dumps(out, ensure_ascii=False, indent=1),
                             encoding="utf-8")
    print(f"\n💾 {results}")


if __name__ == "__main__":
    work, results, binary, tiny, base = sys.argv[1:6]
    main(work, results, binary, {"tiny q5_1": tiny, "base q5_1": base})
