#!/usr/bin/env python3
"""Client de l'API par lots de LuxASR — le flux `/asr2` en file d'attente.

L'ancien flux est déprécié : on poste les octets **bruts** du fichier dans le
corps de la requête, jamais en `multipart/form-data`, avec un `Content-Type`
`audio/*`. Le serveur répond un `job_id`, qu'on interroge jusqu'à `completed`.

Ce que cette API donne et que le WebSocket n'a pas :

- `prompt` — un amorçage orthographique appliqué à tout l'enregistrement, pas
  seulement au premier segment. C'est la porte d'entrée du vocabulaire propre à
  l'utilisateur (noms de localités, marques, jargon).
- `beam_size` — la largeur du faisceau de décodage.
- vraisemblablement un modèle bien plus gros que le tiny affiné servi par
  `wss://luxasr.uni.lu/prod/ws/transcribe`.

Ce qu'elle enlève : le temps réel. C'est un traitement par lots, donc pas
d'affichage progressif — on enregistre, on envoie, on attend, on insère.

**Accès limité, accordé pour un usage de laboratoire.** Les appels sont
séquentiels et les lots petits, à dessein : ne pas mettre à genou leurs
serveurs est une condition explicite de l'accord.
"""

import io
import json
import struct
import time

import numpy as np
import requests

BASE = "https://luxasr.uni.lu"
RATE = 16000


def wav_octets(samples: np.ndarray, rate: int = RATE) -> bytes:
    """float32 [-1, 1] → conteneur WAV PCM 16 bits, en mémoire."""
    pcm = np.clip(samples, -1.0, 1.0)
    pcm = (pcm * 32767.0).astype("<i2").tobytes()
    entete = b"RIFF" + struct.pack("<I", 36 + len(pcm)) + b"WAVEfmt "
    entete += struct.pack("<IHHIIHH", 16, 1, 1, rate, rate * 2, 2, 16)
    entete += b"data" + struct.pack("<I", len(pcm))
    return entete + pcm


def transcrire(samples, language="lb", diarization="Disabled", outfmt="text",
               prompt=None, beam_size=None, min_silence_duration_ms=None,
               nom="dictee.wav", intervalle=1.0, timeout=300):
    """Soumet un énoncé et rend (texte, mesures).

    `diarization` est désactivée par défaut : une dictée de clavier n'a qu'un
    locuteur, et la séparation coûte du temps de calcul pour rien.

    Les mesures rendues comptent le temps de file d'attente séparément du temps
    de traitement — c'est la file qui décide de l'utilisabilité au clavier, et
    elle dépend de leur charge, pas de notre audio.
    """
    params = {"language": language, "diarization": diarization, "outfmt": outfmt}
    if prompt:
        params["prompt"] = prompt
    if beam_size:
        params["beam_size"] = beam_size
    if min_silence_duration_ms:
        params["min_silence_duration_ms"] = min_silence_duration_ms

    octets = samples if isinstance(samples, (bytes, bytearray)) else wav_octets(samples)
    t0 = time.time()
    r = requests.post(f"{BASE}/asr2", params=params, data=octets, timeout=60,
                      headers={"Content-Type": "audio/wav", "X-Filename": nom})
    r.raise_for_status()
    job = r.json()["job_id"]
    t_soumis = time.time() - t0

    t_traitement = None
    while True:
        if time.time() - t0 > timeout:
            raise TimeoutError(f"job {job} toujours en cours après {timeout} s")
        time.sleep(intervalle)
        s = requests.get(f"{BASE}/v3/asr/jobs/{job}", timeout=30).json()
        etat = s.get("status")
        if etat == "processing" and t_traitement is None:
            t_traitement = time.time() - t0        # fin de la file d'attente
        if etat == "completed":
            break
        if etat == "failed":
            raise RuntimeError(f"job {job} en échec : {json.dumps(s)[:200]}")

    res = requests.get(f"{BASE}/v3/asr/jobs/{job}/result", timeout=60)
    res.raise_for_status()
    texte = res.text if outfmt in ("text", "colored_text") else res.text
    return texte.strip(), {
        "job": job,
        "t_soumission_s": round(t_soumis, 2),
        "t_attente_s": round(t_traitement, 2) if t_traitement else None,
        "t_total_s": round(time.time() - t0, 2),
        "octets": len(octets),
    }
