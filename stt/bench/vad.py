#!/usr/bin/env python3
"""Le détecteur de fin d'énoncé du clavier, rejoué hors téléphone.

Port fidèle de `LuxAsrSession.detecterFinDEnonce()` — mêmes constantes, même
cadence de blocs, même plancher de bruit adaptatif. Il n'existe pas pour
mesurer le détecteur en soi, mais pour répondre à une question qu'aucun banc
actuel ne pose : **combien de fois le micro se refermerait-il tout seul** au
milieu d'une minute de parole continue.

Toute divergence avec le Kotlin fausserait précisément ce chiffre : si une
constante bouge là-bas, elle doit bouger ici.
"""

import numpy as np

RATE = 16000
CHUNK_MS = 160                  # AudioRecorder rend ~160 ms par bloc

# --- Constantes reprises telles quelles de LuxAsrSession.kt -------------------
SPEECH_FLOOR_RMS = 0.012        # plancher absolu, sous lequel rien n'est parole
SPEECH_MARGIN = 2.5             # marge au-dessus du plancher de bruit observé
NOISE_RISE = 0.02               # vitesse de remontée du plancher, par bloc
SILENCE_HANGOVER_MS = 1500      # silence qui termine l'énoncé
MAX_UTTERANCE_MS = 90_000


def blocs(samples: np.ndarray, chunk_ms=CHUNK_MS):
    """Découpe en blocs de la taille que rend AudioRecord, dernier bloc inclus."""
    n = int(RATE * chunk_ms / 1000)
    return [samples[i:i + n] for i in range(0, len(samples), n)]


def rms_par_bloc(samples: np.ndarray, chunk_ms=CHUNK_MS) -> np.ndarray:
    return np.array([float(np.sqrt(np.mean(b.astype(np.float64) ** 2)))
                     if len(b) else 0.0
                     for b in blocs(samples, chunk_ms)])


def trace(samples: np.ndarray, hangover_ms=SILENCE_HANGOVER_MS, chunk_ms=CHUNK_MS):
    """Déroule le détecteur bloc à bloc.

    Rend `(evenements, coupures)` :

    - `evenements` : un dict par bloc — instant, énergie, seuil retenu, et si le
      bloc a été pris pour de la parole.
    - `coupures` : les instants où l'application aurait appelé `stop()`. Le
      détecteur est **réarmé** après chaque coupure, exactement comme le fait
      `start()` en rouvrant une session : sans ça on ne verrait que la première.
    """
    ev, coupures = [], []
    noise = 0.0
    heard = False
    last_speech = 0.0
    t_session = 0.0
    dt = chunk_ms / 1000.0

    for k, b in enumerate(blocs(samples, chunk_ms)):
        t = k * dt
        rms = float(np.sqrt(np.mean(b.astype(np.float64) ** 2))) if len(b) else 0.0

        noise = rms if rms < noise else noise + (rms - noise) * NOISE_RISE
        seuil = max(SPEECH_FLOOR_RMS, noise * SPEECH_MARGIN)
        parole = rms >= seuil
        ev.append({"t": t, "rms": rms, "seuil": seuil, "parole": parole})

        if parole:
            heard = True
            last_speech = t
            continue
        if not heard or (t - last_speech) * 1000 < hangover_ms:
            continue

        # Le micro se refermerait ici. On repart comme le ferait une nouvelle
        # session : plancher de bruit remis à zéro, parole pas encore entendue.
        coupures.append(t)
        noise, heard, last_speech, t_session = 0.0, False, t, t

    return ev, coupures


def pauses(samples: np.ndarray, mini_s=0.5, chunk_ms=CHUNK_MS):
    """Plages non-parole d'au moins `mini_s`, vues par ce même détecteur.

    Sert à placer un trou expérimental là où le locuteur en a déjà laissé un :
    couper en plein phonème mesurerait la découpe, pas la reprise.
    """
    ev, _ = trace(samples, hangover_ms=10 ** 9, chunk_ms=chunk_ms)
    dt = chunk_ms / 1000.0
    out, debut = [], None
    for e in ev:
        if not e["parole"] and debut is None:
            debut = e["t"]
        elif e["parole"] and debut is not None:
            if e["t"] - debut >= mini_s:
                out.append((debut, e["t"]))
            debut = None
    if debut is not None and ev and ev[-1]["t"] + dt - debut >= mini_s:
        out.append((debut, ev[-1]["t"] + dt))
    return out
