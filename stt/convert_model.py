#!/usr/bin/env python3
"""Régénère l'asset de dictée vocale à partir du modèle Hugging Face.

    python stt/convert_model.py --output android_keyboard/app/src/main/assets

Chaîne : safetensors F32 (151 Mo) → ggml f16 (75 Mo) → ggml q5_1 (31 Mo).

Trois choses que la carte de modèle ne dit pas et qui cassent la conversion si
on les ignore :

1. `unilux/whisper-tiny-v1-luxembourgish` n'existe qu'en safetensors. Il n'y a
   ni GGUF, ni ONNX, ni CTranslate2 en amont : la conversion est à notre charge.

2. Le script de whisper.cpp lit `max_length` dans `config.json`, où transformers
   ne l'écrit plus depuis la 4.x — il vit dans `generation_config.json`. Sans le
   recopier, la conversion meurt sur `struct.error: required argument is not an
   integer` après avoir écrit un fichier de 24 octets. C'est le contexte texte
   du décodeur (448), pas une longueur de génération.

3. Les filtres mel ne sont pas dans le dépôt HF : ils viennent de
   `whisper/assets/mel_filters.npz` du dépôt openai/whisper.

q5_1 est le point retenu : q4_0 (25 Mo) dégrade sensiblement un modèle déjà
« tiny », q8_0 (42 Mo) coûte 11 Mo d'APK pour un gain qui ne s'entend pas.
"""

import argparse
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

HF_REPO = "unilux/whisper-tiny-v1-luxembourgish"   # défaut : le modèle embarqué
WHISPER_CPP = "https://github.com/ggerganov/whisper.cpp.git"
WHISPER_CPP_TAG = "v1.7.4"          # doit rester aligné sur le sous-module
OPENAI_WHISPER = "https://github.com/openai/whisper.git"

HF_FILES = [
    "config.json", "generation_config.json", "preprocessor_config.json",
    "tokenizer_config.json", "special_tokens_map.json", "added_tokens.json",
    "normalizer.json", "vocab.json", "merges.txt", "model.safetensors",
]

QUANT = "q5_1"


def asset_name(repo: str, quant: str) -> str:
    """ggml-lb-<taille>-<quant>.bin, la taille lue dans le nom du dépôt."""
    size = repo.split("/")[-1].split("-")[1]      # whisper-tiny-v1-… → tiny
    return f"ggml-lb-{size}-{quant}.bin"


ASSET_NAME = asset_name(HF_REPO, QUANT)


def run(cmd, **kw):
    print(f"  $ {' '.join(str(c) for c in cmd)}")
    subprocess.run(cmd, check=True, **kw)


def fetch_model(dest: Path, repo: str = HF_REPO):
    from huggingface_hub import hf_hub_download
    dest.mkdir(parents=True, exist_ok=True)
    for name in HF_FILES:
        print(f"  ⬇️  {name}")
        shutil.copy(hf_hub_download(repo, name), dest / name)


def patch_max_length(model_dir: Path):
    """Recopie max_length depuis generation_config.json (cf. point 2 du docstring)."""
    cfg_path = model_dir / "config.json"
    cfg = json.loads(cfg_path.read_text())
    if cfg.get("max_length") is not None:
        return
    gen = json.loads((model_dir / "generation_config.json").read_text())
    max_length = gen["max_length"]
    if max_length != cfg["max_target_positions"]:
        raise SystemExit(
            f"max_length ({max_length}) ≠ max_target_positions "
            f"({cfg['max_target_positions']}) : le modèle amont a changé de "
            "forme, ne pas convertir à l'aveugle."
        )
    cfg["max_length"] = max_length
    cfg_path.write_text(json.dumps(cfg, indent=2))
    print(f"  🔧 max_length={max_length} recopié dans config.json")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--output", type=Path, required=True,
                    help="dossier assets de l'app")
    ap.add_argument("--work", type=Path, default=None,
                    help="dossier de travail (temporaire par défaut)")
    # Les deux options servent à comparer des tailles de modèle hors de l'APK
    # (cf. stt/bench). Les valeurs par défaut reproduisent exactement l'asset
    # embarqué : la commande du README et le job CI generate-stt-model
    # n'ont pas à changer.
    ap.add_argument("--repo", default=HF_REPO,
                    help=f"dépôt Hugging Face (défaut : {HF_REPO})")
    ap.add_argument("--quant", default=QUANT,
                    help=f"quantification ggml (défaut : {QUANT})")
    args = ap.parse_args()

    repo, quant = args.repo, args.quant
    asset = asset_name(repo, quant)

    work = args.work or Path(tempfile.mkdtemp(prefix="luxstt-"))
    work.mkdir(parents=True, exist_ok=True)
    print(f"📂 Dossier de travail : {work}")

    print(f"\n1/5 · Récupération de {repo}")
    model_dir = work / "hf-model"
    if not (model_dir / "model.safetensors").exists():
        fetch_model(model_dir, repo)
    patch_max_length(model_dir)

    print("\n2/5 · Dépôts outils")
    wcpp = work / "whisper.cpp"
    if not wcpp.exists():
        run(["git", "clone", "--depth", "1", "--branch", WHISPER_CPP_TAG,
             WHISPER_CPP, str(wcpp)])
    oai = work / "openai-whisper"
    if not oai.exists():
        run(["git", "clone", "--depth", "1", OPENAI_WHISPER, str(oai)])

    print("\n3/5 · Conversion safetensors → ggml f16")
    ggml_dir = work / "ggml-out"
    ggml_dir.mkdir(exist_ok=True)
    run([sys.executable, str(wcpp / "models" / "convert-h5-to-ggml.py"),
         str(model_dir), str(oai), str(ggml_dir)])
    f16 = ggml_dir / "ggml-model.bin"
    if not f16.exists():
        raise SystemExit("conversion échouée : ggml-model.bin absent")

    print("\n4/5 · Compilation de l'outil de quantification")
    build = wcpp / "build-host"
    run(["cmake", "-B", str(build), "-S", str(wcpp), "-DCMAKE_BUILD_TYPE=Release",
         "-DWHISPER_BUILD_TESTS=OFF", "-DWHISPER_BUILD_SERVER=OFF"])
    run(["cmake", "--build", str(build), "-j", "--target", "quantize"])

    print(f"\n5/5 · Quantification {quant}")
    out = ggml_dir / asset
    run([str(build / "bin" / "quantize"), str(f16), str(out), quant])

    args.output.mkdir(parents=True, exist_ok=True)
    dest = args.output / asset
    shutil.copy(out, dest)

    mo = dest.stat().st_size / 1e6
    print(f"\n✅ {dest} ({mo:.1f} Mo)")
    print("   Pense à vérifier que build.gradle garde bien noCompress 'bin' :")
    print("   sans lui, AAsset_getBuffer() ne peut plus mmap le modèle.")


if __name__ == "__main__":
    main()
