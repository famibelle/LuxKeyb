#!/usr/bin/env python3
"""Prépare le jeu de test de la dictée à partir du corpus Hugging Face.

    python stt/bench/prepare_dataset.py --work <scratchpad> --files 50 --slice-files 15

`Akabi/Luxemburgish_Press_Conferences_Gov` : 470 mp3 de 60 s exactement,
16 kHz mono, avec transcription humaine dans metadata.jsonl. Dépôt public, aucun
token nécessaire.

**L'audio ne doit jamais entrer dans le dépôt** : le dépôt HF ne déclare aucune
licence. Tout atterrit dans le dossier de travail, seuls les agrégats chiffrés
sont versionnés — même politique que ParaLux pour le dictionnaire.

Deux sorties :

- `full/` : fichiers de 60 s décodés en float32 brut, pour mesurer le modèle.
- `slices/` : tranches de 2 à 15 s découpées aux silences, pour mesurer le
  pipeline. Un énoncé de clavier dure quelques secondes ; mesurer la latence
  perçue sur des monologues de 60 s ne dirait rien de l'usage réel.
"""

import argparse
import hashlib
import json
import random
import re
import subprocess
import sys
from pathlib import Path

REPO = "Akabi/Luxemburgish_Press_Conferences_Gov"
RATE = 16000


def log(msg):
    print(msg, flush=True)


def decode(src: Path, dst: Path, start=None, dur=None):
    """mp3 → float32 brut mono 16 kHz, le seul format que whisper accepte."""
    cmd = ["ffmpeg", "-v", "error", "-y"]
    if start is not None:
        cmd += ["-ss", f"{start:.3f}"]
    cmd += ["-i", str(src)]
    if dur is not None:
        cmd += ["-t", f"{dur:.3f}"]
    cmd += ["-ar", str(RATE), "-ac", "1", "-f", "f32le", str(dst)]
    subprocess.run(cmd, check=True)


def silences(src: Path, noise="-22dB", min_dur=0.30):
    """Frontières de silence, via le filtre silencedetect de ffmpeg.

    Le seuil est calibré, pas deviné. Sur 6 fichiers, en faisant varier le
    bruit de fond accepté :

        -32 dB → médiane 15,0 s, 55 % de tronçons coupés en plein mot
        -28 dB → médiane  6,9 s, 26 %
        -25 dB → médiane  4,7 s, 15 %
        -22 dB → médiane  3,7 s,  4 %      ← retenu
        -18 dB → médiane  3,3 s,  0 %

    Une conférence de presse n'a pas de vrai silence — climatisation, salle,
    micros ouverts — et à -32 dB aucune respiration n'est vue : les tranches
    tombaient toutes sur la borne haute des 15 s, en plein mot, ce qui ne
    ressemble à aucune dictée. À -22 dB la médiane vaut 3,7 s, soit la longueur
    d'un énoncé de clavier. Descendre plus bas ne gagne plus grand-chose et
    commence à couper dans les phonèmes peu énergiques.
    """
    out = subprocess.run(
        ["ffmpeg", "-v", "info", "-i", str(src),
         "-af", f"silencedetect=noise={noise}:d={min_dur}", "-f", "null", "-"],
        capture_output=True, text=True).stderr
    marks = []
    for m in re.finditer(r"silence_(start|end): ([0-9.]+)", out):
        marks.append((m.group(1), float(m.group(2))))
    return marks


def cut_points(marks, total=60.0):
    """Milieu de chaque silence : le point de coupe le plus sûr."""
    pts, start = [], None
    for kind, t in marks:
        if kind == "start":
            start = t
        elif start is not None:
            pts.append((start + t) / 2.0)
            start = None
    return [p for p in pts if 0.5 < p < total - 0.5]


def slices_of(marks, total=60.0, lo=2.0, hi=15.0):
    """Tranches de durée plausible pour une dictée, entre deux silences."""
    pts = [0.0] + cut_points(marks, total) + [total]
    out, a = [], 0.0
    for p in pts[1:]:
        if p - a < lo:
            continue                      # trop court : on prolonge
        # Silence trop lointain : on tronçonne à hi, quitte à tomber en plein
        # mot. C'est ce que fait aussi la borne des 30 s de l'application.
        while p - a > hi:
            out.append((a, hi))
            a += hi
        if p - a >= lo:
            out.append((a, p - a))
            a = p
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("--files", type=int, default=50, help="fichiers pour l'étape 1")
    ap.add_argument("--slice-files", type=int, default=15, help="fichiers à découper")
    ap.add_argument("--seed", type=int, default=1729)
    args = ap.parse_args()

    from huggingface_hub import hf_hub_download

    work = args.work
    (work / "mp3").mkdir(parents=True, exist_ok=True)
    (work / "full").mkdir(exist_ok=True)
    (work / "slices").mkdir(exist_ok=True)

    log(f"📥 metadata.jsonl depuis {REPO}")
    meta_path = hf_hub_download(REPO, "metadata.jsonl", repo_type="dataset")
    entries = [json.loads(l) for l in open(meta_path, encoding="utf-8")]
    log(f"   {len(entries)} segments référencés")

    # metadata.jsonl et le contenu réel du dépôt divergent : une partie des
    # entrées pointe vers des mp3 absents (404). On n'en garde que l'intersection
    # plutôt que d'échouer au milieu du lot.
    from huggingface_hub import list_repo_files
    present = set(list_repo_files(REPO, repo_type="dataset"))
    keep = [e for e in entries if e["file_name"] in present]
    log(f"   {len(keep)} réellement présents dans le dépôt "
        f"({len(entries) - len(keep)} entrées orphelines ignorées)")
    entries = keep

    random.Random(args.seed).shuffle(entries)
    chosen = entries[:args.files]

    manifest, slice_manifest = [], []
    for n, e in enumerate(chosen, 1):
        name = e["file_name"]
        # Le cache est indexé sur le fichier source, jamais sur le rang dans le
        # tirage. Nommer 001.f32, 002.f32… paraît plus lisible mais rend le
        # dossier de travail silencieusement faux dès que le tirage change : un
        # passage interrompu laisse des fichiers que le passage suivant réutilise
        # sous une autre référence. Constaté ici — 8 fichiers sur 50 mesurés
        # contre la transcription d'un autre, avec des WER de 96 à 152 % qui
        # ressemblaient à une panne du modèle.
        stem = f"{n:03d}_{hashlib.md5(name.encode()).hexdigest()[:8]}"
        log(f"[{n}/{len(chosen)}] {name.split('/')[-1][:60]}")
        mp3 = Path(hf_hub_download(REPO, name, repo_type="dataset"))
        f32 = work / "full" / f"{stem}.f32"
        if not f32.exists():
            decode(mp3, f32)
        manifest.append({"id": stem, "file_name": name,
                         "f32": str(f32), "reference": e["transcription"]})

        if n <= args.slice_files:
            segs = slices_of(silences(mp3))
            log(f"      ↳ {len(segs)} tranches "
                f"({', '.join(f'{d:.1f}s' for _, d in segs[:8])}…)")
            for k, (start, dur) in enumerate(segs):
                sf = work / "slices" / f"{stem}_{k:02d}.f32"
                if not sf.exists():
                    decode(mp3, sf, start=start, dur=dur)
                slice_manifest.append({"id": f"{stem}_{k:02d}", "parent": stem,
                                       "start": start, "dur": dur, "f32": str(sf)})

    (work / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
    (work / "slices.json").write_text(
        json.dumps(slice_manifest, ensure_ascii=False, indent=1), encoding="utf-8")

    tot = sum(s["dur"] for s in slice_manifest)
    log(f"\n✅ {len(manifest)} fichiers de 60 s ({len(manifest)} min d'audio)")
    log(f"✅ {len(slice_manifest)} tranches, {tot/60:.1f} min, "
        f"durée moyenne {tot/max(1,len(slice_manifest)):.1f} s")


if __name__ == "__main__":
    main()
