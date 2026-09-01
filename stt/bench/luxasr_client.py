#!/usr/bin/env python3
"""Client du service LuxASR, calqué sur LuxAsrSession.kt.

Sert de témoin au banc sur téléphone : mêmes trames, même cadence, mais sans
micro ni haut-parleur. L'écart entre les deux mesures est le coût de la chaîne
acoustique, pas celui du service.

Protocole (relevé dans LuxAsrSession.kt) : WebSocket, PCM 16 bits little-endian
16 kHz mono en trames binaires, contrôle en JSON, « stop » pour conclure.

`stream()` en est la forme générale : il prend un **plan d'émission** — la
chronologie réelle, bloc par bloc, avec pour chacun le droit ou non de partir
sur le réseau. C'est ce qui permet de mesurer un flux suspendu, où l'horloge
avance sans que le service reçoive quoi que ce soit ; `transcribe()` n'en est
que le cas où tout est émis.
"""

import json
import time
import numpy as np
import websocket   # websocket-client

ENDPOINT = "wss://luxasr.uni.lu/prod/ws/transcribe"
RATE = 16000
CHUNK_MS = 160          # AudioRecorder rend ~160 ms par bloc
FINAL_GRACE_S = 6.0

# Le réglage embarqué dans LuxAsrSession ; le serveur, lui, part sur 5,0 / 0,8.
CONFIG_APP = {"language": "lb",
              "chunk_params": {"periodic_send_interval": 2.0,
                               "silence_threshold": 0.5,
                               "max_chunk_duration": 30.0}}


def pcm16(f32: np.ndarray) -> bytes:
    return (np.clip(f32, -1.0, 1.0) * 32767.0).astype("<i2").tobytes()


def decouper(samples: np.ndarray, chunk_ms=CHUNK_MS):
    n = int(RATE * chunk_ms / 1000)
    return [samples[i:i + n] for i in range(0, len(samples), n)]


def stream(plan, config=None, temps_reel=True, timeout=45, grace=FINAL_GRACE_S):
    """Joue un plan d'émission et rend le détail de ce que le service a répondu.

    `plan` : suite de `(emettre, bloc)`. Le bloc compte toujours dans l'horloge —
    c'est la durée réelle de l'énoncé — mais n'est envoyé que si `emettre`. Un
    bloc retenu est donc du temps qui passe sans que le service reçoive rien,
    ce qu'aucun `sleep` ne reproduirait : il faut que le reste du flux garde sa
    place dans le temps.

    Rend un dict ; `passes` porte l'instant de chaque hypothèse, sans quoi on ne
    saurait pas dire si le service a décodé *pendant* le trou.
    """
    ws = websocket.create_connection(ENDPOINT, timeout=timeout)
    passes, texte, t_first = [], "", None
    emis = 0

    def draine(bloquant_s):
        nonlocal texte, t_first
        ws.settimeout(bloquant_s)
        while True:
            try:
                m = json.loads(ws.recv())
            except Exception:
                return None
            t = m.get("type")
            if t == "transcription":
                texte = m.get("accumulated_text") or texte
                if t_first is None and texte:
                    t_first = time.monotonic() - t0
                passes.append({"t": time.monotonic() - t0,
                               "proc": m.get("metrics", {}).get("processing_time", 0.0),
                               "mots": len(texte.split())})
            elif t in ("recording_stopped", "error"):
                return t

    t0 = time.monotonic()
    try:
        if config:
            ws.send(json.dumps({"type": "config", **config}))
        ecoule = 0.0
        for emettre, bloc in plan:
            if emettre and len(bloc):
                ws.send_binary(pcm16(bloc))
                emis += len(bloc)
            ecoule += len(bloc) / RATE
            draine(0.001)          # ce qui est déjà là, sans bloquer
            if temps_reel:
                d = t0 + ecoule - time.monotonic()
                if d > 0:
                    time.sleep(d)
        t_stop = time.monotonic()
        ws.send(json.dumps({"type": "stop"}))
        while time.monotonic() - t_stop < grace:
            if draine(timeout) in ("recording_stopped", "error"):
                break
    finally:
        try:
            ws.close()
        except Exception:
            pass
    return {"texte": texte.strip(), "passes": passes, "t_first": t_first,
            "t_stop": t_stop - t0, "t_final": time.monotonic() - t_stop,
            "audio_emis_s": emis / RATE, "duree_plan_s": ecoule}


def transcribe(samples: np.ndarray, temps_reel=True, timeout=45, config=None):
    """Envoie un énoncé et rend (texte, passes, t_premier_mot, t_final)."""
    r = stream([(True, b) for b in decouper(samples)],
               config=config, temps_reel=temps_reel, timeout=timeout)
    return r["texte"], [p["proc"] for p in r["passes"]], r["t_first"], r["t_final"]
