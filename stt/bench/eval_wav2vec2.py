#!/usr/bin/env python3
"""Évalue les modèles wav2vec 2.0 luxembourgeois sur notre corpus.

Les WER publiés par leurs auteurs (2,64 % en test pour le large) sont mesurés
sur leur propre partition de RTL.lu : même source, même domaine, même registre
que l'entraînement. C'est le piège que `CLAUDE.md` documente déjà pour le
dictionnaire, où une partition aléatoire annonçait 23,9 % là où ParaLux, jamais
vu à l'entraînement, en donnait 18,8 %.

Ce script les rejoue donc sur **nos** énoncés — conférences de presse
gouvernementales, autre domaine — avec le même code de WER et les mêmes 161
tranches que les modèles Whisper, pour que les chiffres soient comparables.

Intérêt architectural, indépendamment du WER : wav2vec 2.0 en décodage CTC ne
traite que l'audio qu'on lui donne. Son coût est linéaire dans la durée, là où
Whisper encode toujours 30 s — ce qui est la totalité de notre problème de
latence.
"""

import json
import statistics
import sys
import time
from pathlib import Path

import numpy as np
import torch

sys.path.insert(0, str(Path(__file__).parent))
from wer import wer

MODELS = [
    "Lemswasabi/wav2vec2-base-luxembourgish-4h",
    "Lemswasabi/wav2vec2-large-xlsr-53-842h-luxembourgish-14h",
]


DICT = Path(__file__).resolve().parents[2] / (
    "android_keyboard/app/src/main/assets/luxemburgish_dict.json")


def casing_coverage(texts):
    """Part des mots produits que le dictionnaire embarqué sait recapitaliser.

    Le dictionnaire est un tableau de paires [mot, fréquence] conservant la
    casse du corpus. Un mot n'est restaurable sans ambiguïté que s'il n'y
    apparaît qu'avec une seule casse — « de » et « De » coexistant, il faudrait
    un modèle pour trancher, pas une table.
    """
    formes = {}
    for mot, _ in json.loads(DICT.read_text(encoding="utf-8")):
        formes.setdefault(mot.lower(), set()).add(mot)
    total = couverts = 0
    for t in texts:
        for w in t.lower().split():
            total += 1
            if len(formes.get(w, ())) == 1:
                couverts += 1
    return couverts / max(1, total)


def main(work, out_path, threads=3):
    # Wav2Vec2Processor.from_pretrained échoue sur ces dépôts : publiés début
    # 2023, ils n'ont pas de processor_config.json, que transformers 5.x exige
    # désormais. L'extracteur et le tokeniseur se chargent séparément à partir
    # des fichiers qui, eux, sont bien là.
    from transformers import (Wav2Vec2CTCTokenizer, Wav2Vec2FeatureExtractor,
                              Wav2Vec2ForCTC)

    torch.set_num_threads(threads)          # comme SttEngine.threadCount()
    slices = json.loads((Path(work) / "slices.json").read_text(encoding="utf-8"))
    manifest = {e["id"]: e for e in
                json.loads((Path(work) / "manifest.json").read_text(encoding="utf-8"))}

    out = {}
    for name in MODELS:
        print(f"\n╔══ {name}", flush=True)
        extractor = Wav2Vec2FeatureExtractor.from_pretrained(name)
        tokenizer = Wav2Vec2CTCTokenizer.from_pretrained(name)
        model = Wav2Vec2ForCTC.from_pretrained(name).eval()
        params = sum(p.numel() for p in model.parameters())
        print(f"║ {params/1e6:.0f} M paramètres", flush=True)

        per_parent, times, durations = {}, [], []
        for i, s in enumerate(slices, 1):
            audio = np.fromfile(s["f32"], dtype=np.float32)
            inputs = extractor(audio, sampling_rate=16000, return_tensors="pt")
            t0 = time.perf_counter()
            with torch.no_grad():
                logits = model(inputs.input_values).logits
            ms = (time.perf_counter() - t0) * 1000
            text = tokenizer.batch_decode(torch.argmax(logits, dim=-1))[0]
            per_parent.setdefault(s["parent"], []).append((s["id"], text))
            times.append(ms)
            durations.append(s["dur"])
            if i % 40 == 0:
                print(f"║   {i}/{len(slices)}  médiane {statistics.median(times):.0f} ms",
                      flush=True)

        err = words = nsub = nins = ndel = 0
        for pid, g in per_parent.items():
            w, n, sub, ins, dele = wer(manifest[pid]["reference"],
                                       " ".join(t for _, t in sorted(g)))
            err += sub + ins + dele
            nsub += sub; nins += ins; ndel += dele
            words += n

        # Le coût est-il proportionnel à la durée, contrairement à Whisper ?
        short = [t for t, d in zip(times, durations) if d < 4]
        long_ = [t for t, d in zip(times, durations) if d >= 10]
        # Casse restaurable : ces modèles ne produisent que des minuscules, or
        # le luxembourgeois met une majuscule aux noms communs. La question
        # n'est donc pas s'il faut restaurer la casse mais si le dictionnaire
        # embarqué y suffit — un mot n'est restaurable que s'il n'y figure
        # qu'avec une seule casse.
        restaurable = casing_coverage(
            [t for g in per_parent.values() for _, t in g])

        out[name] = {
            "params_M": params / 1e6,
            "wer": err / words,
            "sub": nsub / words, "ins": nins / words, "del": ndel / words,
            "casse_restaurable": restaurable,
            "ms_median": statistics.median(times),
            "ms_short": statistics.median(short) if short else None,
            "ms_long": statistics.median(long_) if long_ else None,
        }
        print(f"║ WER {err/words*100:.1f} %   "
              f"(sub {nsub/words*100:.1f} · ins {nins/words*100:.1f} · "
              f"del {ndel/words*100:.1f})   passe médiane {statistics.median(times):.0f} ms",
              flush=True)
        print(f"║ casse restaurable par le dictionnaire : {restaurable*100:.1f} %",
              flush=True)
        print(f"║ énoncés < 4 s : {statistics.median(short):.0f} ms   "
              f"≥ 10 s : {statistics.median(long_):.0f} ms  "
              f"→ rapport {statistics.median(long_)/statistics.median(short):.2f}×",
              flush=True)
        del model

    Path(out_path).write_text(json.dumps(out, ensure_ascii=False, indent=1),
                              encoding="utf-8")
    print(f"\n💾 {out_path}", flush=True)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
