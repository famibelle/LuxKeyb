#!/usr/bin/env python3
"""Transcrire un enregistrement avec l'API par lots de LuxASR, locuteurs séparés.

    python3 luxasr_transcrire.py interview.m4a -o interview.json
    python3 luxasr_transcrire.py dictee.wav --sans-diarisation --outfmt text
    python3 luxasr_transcrire.py --sonde-cors

Le fichier part tel quel, en octets bruts (jamais en multipart, l'ancien flux
déprécié), avec un `Content-Type` déduit de l'extension. Le travail est suivi
jusqu'au bout : un seul envoi, un sondage à 2 s puis 1,2 fois plus espacé à
chaque tour (10 s au plus), comme leur client officiel (SKILL.md § 1). Le
résultat ne se lit qu'une fois, le service l'efface aussitôt : il est écrit
avant toute autre chose.

`--sonde-cors` vérifie si une page web peut lire la réponse 202 de `/asr2`
(SKILL.md § 5). Elle soumet une demi-seconde de silence : un travail
minuscule, mais un vrai travail.
"""

import argparse
import struct
import sys
import time
from pathlib import Path

import requests

BASE = "https://luxasr.uni.lu"
ORIGINE_SITE = "https://famibelle.github.io"

TYPES = {
    ".wav": "audio/wav", ".mp3": "audio/mpeg", ".mp2": "audio/mpeg",
    ".m4a": "audio/mp4", ".aac": "audio/aac", ".ogg": "audio/ogg",
    ".oga": "audio/ogg", ".opus": "audio/ogg", ".flac": "audio/flac",
    ".weba": "audio/webm", ".wma": "audio/x-ms-wma",
    ".mp4": "video/mp4", ".mov": "video/quicktime", ".qta": "video/quicktime",
    ".webm": "video/webm", ".wmv": "video/x-ms-wmv",
}


def journal(*morceaux):
    print(*morceaux, file=sys.stderr, flush=True)


def soumettre(octets, content_type, nom=None, origine=None, **params):
    entetes = {"Content-Type": content_type}
    if nom:
        entetes["X-Filename"] = nom
    if origine:
        entetes["Origin"] = origine
    params = {k: v for k, v in params.items() if v not in (None, "")}
    r = requests.post(f"{BASE}/asr2", params=params, data=octets,
                      headers=entetes, timeout=600)
    r.raise_for_status()
    return r


def suivre(job, delai_max=3600):
    """Sonde le travail jusqu'à `completed` ; rend le corps du résultat."""
    t0, dernier, pause = time.time(), None, 2.0
    while True:
        if time.time() - t0 > delai_max:
            raise TimeoutError(f"travail {job} toujours en cours après {delai_max} s")
        time.sleep(pause)
        pause = min(10.0, pause * 1.2)
        s = requests.get(f"{BASE}/v3/asr/jobs/{job}", timeout=30).json()
        etat = s.get("status")
        if etat != dernier:
            extra = "".join(f" {k}={s[k]}" for k in ("queue_position", "eta_seconds") if k in s)
            journal(f"[{time.time() - t0:6.1f} s] {etat}{extra}")
            dernier = etat
        if etat == "completed":
            break
        if etat in ("failed", "not_found"):
            raise RuntimeError(f"travail {job} : {etat} : {str(s)[:300]}")
    r = requests.get(f"{BASE}/v3/asr/jobs/{job}/result", timeout=120)
    r.raise_for_status()
    return r.text


def silence_wav(secondes=0.5, rate=16000):
    pcm = b"\0\0" * int(secondes * rate)
    entete = b"RIFF" + struct.pack("<I", 36 + len(pcm)) + b"WAVEfmt "
    entete += struct.pack("<IHHIIHH", 16, 1, 1, rate, rate * 2, 2, 16)
    return entete + b"data" + struct.pack("<I", len(pcm)) + pcm


def sonde_cors(origine):
    """Montre, réponse par réponse, si le navigateur aurait le droit de lire."""
    def acao(r):
        return r.headers.get("Access-Control-Allow-Origin") or "— absent —"

    pre = requests.options(f"{BASE}/asr2", timeout=30, headers={
        "Origin": origine, "Access-Control-Request-Method": "POST",
        "Access-Control-Request-Headers": "content-type"})
    print(f"OPTIONS /asr2          {pre.status_code}  ACAO : {acao(pre)}  "
          f"en-têtes permis : {pre.headers.get('Access-Control-Allow-Headers', '—')}")

    r = soumettre(silence_wav(), "audio/wav", origine=origine,
                  language="lb", diarization="Disabled", outfmt="text")
    job = r.json()["job_id"]
    print(f"POST /asr2             {r.status_code}  ACAO : {acao(r)}")

    vus = set()
    for _ in range(60):
        s = requests.get(f"{BASE}/v3/asr/jobs/{job}", timeout=30,
                         headers={"Origin": origine})
        etat = s.json().get("status")
        if (s.status_code, etat) not in vus:
            vus.add((s.status_code, etat))
            print(f"GET  jobs/{{id}}         {s.status_code}  ACAO : {acao(s)}  ({etat})")
        if etat in ("completed", "failed"):
            break
        time.sleep(1)
    res = requests.get(f"{BASE}/v3/asr/jobs/{job}/result", timeout=30,
                       headers={"Origin": origine})
    print(f"GET  jobs/{{id}}/result  {res.status_code}  ACAO : {acao(res)}")
    lisible = "Access-Control-Allow-Origin" in r.headers
    print("\nVerdict : " + ("une page web peut lire le job_id."
                            if lisible else
                            "le 202 n'a pas d'en-tête CORS — la page ne peut "
                            "pas lire le job_id (SKILL.md § 5)."))


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("fichier", nargs="?", type=Path)
    ap.add_argument("-o", "--sortie", type=Path,
                    help="où écrire le résultat (sinon : sortie standard)")
    ap.add_argument("--outfmt", default="json",
                    help="json (défaut), text, colored_text, srt, vtt, textgrid, maxqda")
    ap.add_argument("--sans-diarisation", action="store_true",
                    help="un seul locuteur : n'active pas la séparation des voix")
    ap.add_argument("--locuteurs", type=int, choices=range(1, 11), metavar="N",
                    help="nombre de voix, 1 à 10 (num_speakers, non documenté pour "
                         "l'API : c'est ce qu'envoie leur formulaire)")
    ap.add_argument("--prompt", help="amorçage orthographique (noms propres, jargon ; "
                                     "900 caractères au plus)")
    ap.add_argument("--beam", type=int, help="largeur du faisceau (défaut du service sinon)")
    ap.add_argument("--langue", default="lb")
    ap.add_argument("--sonde-cors", action="store_true",
                    help="vérifier si une page web peut lire la réponse de /asr2")
    ap.add_argument("--origine", default=ORIGINE_SITE,
                    help=f"origine simulée par la sonde (défaut : {ORIGINE_SITE})")
    args = ap.parse_args()

    if args.sonde_cors:
        sonde_cors(args.origine)
        return
    if not args.fichier:
        ap.error("indiquer un fichier audio, ou --sonde-cors")

    ctype = TYPES.get(args.fichier.suffix.lower())
    if not ctype:
        ap.error(f"extension inconnue : {args.fichier.suffix} "
                 f"(connues : {', '.join(sorted(TYPES))})")
    if not args.fichier.is_file():
        ap.error(f"fichier introuvable : {args.fichier}")
    octets = args.fichier.read_bytes()

    journal(f"envoi de {args.fichier.name} ({len(octets) / 1e6:.1f} Mo, {ctype})")
    r = soumettre(octets, ctype, nom=args.fichier.name,
                  language=args.langue, outfmt=args.outfmt,
                  diarization="Disabled" if args.sans_diarisation else "Enabled",
                  num_speakers=None if args.sans_diarisation else args.locuteurs,
                  prompt=args.prompt[:900] if args.prompt else None, beam_size=args.beam)
    job = r.json()["job_id"]
    journal(f"travail {job}")
    resultat = suivre(job)

    if args.sortie:
        args.sortie.write_text(resultat, encoding="utf-8")
        journal(f"écrit : {args.sortie}")
    else:
        print(resultat)


if __name__ == "__main__":
    main()
