#!/usr/bin/env python3
"""Banc des modèles candidats à une dictée **hors ligne**, même protocole pour tous.

    python stt/bench/bench_offline.py --work W --out R.json --nom small \\
        --ggml M.bin --binary build/lux_bench
    python stt/bench/bench_offline.py --work W --out R.json --nom turbo-zls \\
        --ct2 ZLSCompLing/Whisper-Large-V3-Turbo-Luxembourgish

Deux régimes, les deux du banc du 28 août :

- **fichiers** : une passe par fichier de 60 s (`manifest.json`) ;
- **énoncés** : une passe par tranche (`slices.json`), puis les tranches d'un
  même fichier recollées contre sa référence entière. C'est le régime d'une
  dictée hors ligne réaliste — on transcrit l'énoncé une fois fini, puisqu'un
  gros modèle ne peut pas re-transcrire en continu — et c'est la définition du
  « WER du pipeline » d'`analyze_stream.py`.

Pourquoi un second moteur. Le modèle du ZLS n'est publié qu'au format
CTranslate2, que whisper.cpp ne lit pas ; il passe donc par faster-whisper,
réglé comme `whisper_jni.cpp` (glouton, température 0 sans repli, langue
forcée, sans contexte d'une passe à l'autre). Les modèles ggml passent par
`lux_bench`, c'est-à-dire par le code même de l'APK. Mesurer `tiny` et `base`
ici aussi sert de témoin : s'ils retrouvent leurs chiffres d'août, les
nouveaux se lisent sur la même échelle.

Les temps de calcul mesurés sur le poste ne disent rien du téléphone ; seul
`--cpu-sonde` donne un ordre de grandeur, sur quelques tranches en CPU.
"""

import argparse
import json
import statistics
import subprocess
import sys
import time
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
from wer import wer, repetition_ratio


def ggml(binary, modele, chemins, work, threads):
    lst = work / "inputs_offline.txt"
    lst.write_text("\n".join(str(p) for p in chemins), encoding="utf-8")
    proc = subprocess.Popen([str(binary), "--model", str(modele), "--input", f"@{lst}",
                             "--mode", "full", "--threads", str(threads)],
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
    for ligne in proc.stdout:
        if ligne.startswith("{"):
            r = json.loads(ligne)
            if r.get("kind") != "load":
                yield r["file"], r["text"], r["ms"]
    proc.wait()


def ct2(modele, chemins, device, compute, threads):
    from faster_whisper import WhisperModel
    m = WhisperModel(modele, device=device, compute_type=compute, cpu_threads=threads)
    for p in chemins:
        a = np.fromfile(p, dtype="<f4")
        t0 = time.time()
        segs, _ = m.transcribe(a, language="lb", beam_size=1, best_of=1,
                               temperature=0.0, condition_on_previous_text=False,
                               vad_filter=False, without_timestamps=True)
        texte = " ".join(s.text.strip() for s in segs)
        yield p, texte, (time.time() - t0) * 1000


def moteur(args, chemins, device=None, compute=None, threads=None):
    if args.ggml:
        return ggml(args.binary, args.ggml, chemins, args.work, threads or args.threads)
    return ct2(args.ct2, chemins, device or args.device, compute or args.compute,
               threads or args.threads)


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--nom", required=True)
    ap.add_argument("--ggml", type=Path)
    ap.add_argument("--binary", type=Path)
    ap.add_argument("--ct2")
    ap.add_argument("--device", default="cuda")
    ap.add_argument("--compute", default="float16")
    ap.add_argument("--threads", type=int, default=3)
    ap.add_argument("--cpu-sonde", type=int, default=0,
                    help="en plus, N tranches de 4 à 8 s en CPU (int8 pour CT2)")
    args = ap.parse_args()
    if bool(args.ggml) == bool(args.ct2):
        ap.error("--ggml ou --ct2, l'un des deux")

    manifest = json.loads((args.work / "manifest.json").read_text(encoding="utf-8"))
    refs = {e["id"]: e["reference"] for e in manifest}
    par_chemin = {e["f32"]: e for e in manifest}
    tranches = json.loads((args.work / "slices.json").read_text(encoding="utf-8"))
    tr_chemin = {s["f32"]: s for s in tranches}

    # Régime « fichiers »
    err = mots = 0
    fichiers = []
    for p, hyp, ms in moteur(args, [e["f32"] for e in manifest]):
        e = par_chemin[p]
        w, n, s, i, d = wer(e["reference"], hyp)
        err += s + i + d
        mots += n
        fichiers.append({"id": e["id"], "wer": w, "ms": ms, "rep": repetition_ratio(hyp),
                         "hyp": hyp})
        print(f"  [F {len(fichiers):2d}/{len(manifest)}] {e['id']}  WER {w*100:5.1f} %  "
              f"{ms:6.0f} ms", flush=True)
    wer_fichiers = err / mots

    # Régime « énoncés »
    lignes = []
    for p, hyp, ms in moteur(args, [s["f32"] for s in tranches]):
        s = tr_chemin[p]
        lignes.append({"id": s["id"], "parent": s["parent"], "dur": s["dur"],
                       "text": hyp, "ms": ms, "rep": repetition_ratio(hyp)})
        print(f"  [E {len(lignes):3d}/{len(tranches)}] {s['id']}  {s['dur']:4.1f}s  "
              f"{ms:6.0f} ms  « {hyp[:50]} »", flush=True)
    groupes = {}
    for r in lignes:
        groupes.setdefault(r["parent"], []).append(r)
    err = mots = 0
    for pid, g in groupes.items():
        hyp = " ".join(r["text"] for r in sorted(g, key=lambda x: x["id"]))
        w, n, s, i, d = wer(refs[pid], hyp)
        err += s + i + d
        mots += n
    wer_enonces = err / mots

    sonde = None
    if args.cpu_sonde:
        choix = [s for s in tranches if 4 <= s["dur"] <= 8][:args.cpu_sonde]
        ms = [m for _, _, m in moteur(args, [s["f32"] for s in choix], device="cpu",
                                      compute="int8", threads=args.threads)]
        sonde = {"tranches": len(choix), "threads": args.threads,
                 "ms_median": statistics.median(ms)}

    res = {
        "modele": args.nom,
        "moteur": "whisper.cpp (lux_bench, paramètres de l'APK)" if args.ggml
                  else f"faster-whisper {args.device}/{args.compute}",
        "source": str(args.ggml.name if args.ggml else args.ct2),
        "taille_octets": args.ggml.stat().st_size if args.ggml else None,
        "wer_fichiers_60s": round(wer_fichiers, 4),
        "fichiers": len(fichiers),
        "wer_enonces_recolles": round(wer_enonces, 4),
        "tranches": len(lignes),
        "boucles_enonces": sum(1 for r in lignes if r["rep"] > 0.15),
        "muettes": sum(1 for r in lignes if not r["text"].strip()),
        "ms_median_enonce_poste": statistics.median(r["ms"] for r in lignes),
        "sonde_cpu": sonde,
    }
    print(json.dumps(res, ensure_ascii=False, indent=1))
    # Le détail garde les transcriptions : il ne va jamais dans results/, le
    # corpus ne déclarant aucune licence.
    res["detail"] = {"fichiers": fichiers, "enonces": lignes}
    args.out.write_text(json.dumps(res, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
