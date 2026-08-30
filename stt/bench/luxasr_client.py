#!/usr/bin/env python3
"""Client du service LuxASR, calqué sur LuxAsrSession.kt.

Sert de témoin au banc sur téléphone : mêmes trames, même cadence, mais sans
micro ni haut-parleur. L'écart entre les deux mesures est le coût de la chaîne
acoustique, pas celui du service.

Protocole (relevé dans LuxAsrSession.kt) : WebSocket, PCM 16 bits little-endian
16 kHz mono en trames binaires, contrôle en JSON, « stop » pour conclure.
"""

import json
import time
import numpy as np
import websocket   # websocket-client

ENDPOINT = "wss://luxasr.uni.lu/prod/ws/transcribe"
RATE = 16000
CHUNK_MS = 160          # AudioRecorder rend ~160 ms par bloc
FINAL_GRACE_S = 6.0


def pcm16(f32: np.ndarray) -> bytes:
    return (np.clip(f32, -1.0, 1.0) * 32767.0).astype("<i2").tobytes()


def transcribe(samples: np.ndarray, temps_reel=True, timeout=45):
    """Envoie un énoncé et rend (texte, passes, t_premier_mot, t_final)."""
    ws = websocket.create_connection(ENDPOINT, timeout=timeout)
    passes, texte, t_first = [], "", None
    t0 = time.monotonic()
    try:
        n = int(RATE * CHUNK_MS / 1000)
        for i in range(0, len(samples), n):
            ws.send_binary(pcm16(samples[i:i + n]))
            # Draine sans bloquer ce qui est déjà arrivé
            ws.settimeout(0.001)
            while True:
                try:
                    m = json.loads(ws.recv())
                except Exception:
                    break
                if m.get("type") == "transcription":
                    texte = m.get("accumulated_text") or texte
                    if t_first is None and texte:
                        t_first = time.monotonic() - t0
                    passes.append(m.get("metrics", {}).get("processing_time", 0.0))
            if temps_reel:
                cible = t0 + (i + n) / RATE
                d = cible - time.monotonic()
                if d > 0:
                    time.sleep(d)
        t_stop = time.monotonic()
        ws.settimeout(timeout)
        ws.send(json.dumps({"type": "stop"}))
        while time.monotonic() - t_stop < FINAL_GRACE_S:
            try:
                m = json.loads(ws.recv())
            except Exception:
                break
            t = m.get("type")
            if t == "transcription":
                texte = m.get("accumulated_text") or texte
                if t_first is None and texte:
                    t_first = time.monotonic() - t0
                passes.append(m.get("metrics", {}).get("processing_time", 0.0))
            elif t in ("recording_stopped", "error"):
                break
    finally:
        try:
            ws.close()
        except Exception:
            pass
    return texte.strip(), passes, t_first, time.monotonic() - t_stop
