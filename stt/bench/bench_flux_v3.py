#!/usr/bin/env python3
"""Le moteur live du 16 septembre 2026 contre l'API par lots, au régime clavier.

    python stt/bench/bench_flux_v3.py   (après prepare_dataset.py --files 6 --slice-files 0)

Mêmes 22 énoncés de 8 à 22 s que `bench_api_enonce.py` le 1er septembre (graine
1729, fenêtres bornées par des pauses, WER infixe), passés dans la même minute au
WebSocket puis à /asr2. Le WebSocket reçoit tout l'audio en temps réel, sans
`chunk_params` : le nouveau moteur ne les lit plus.

`vu_a_l_arret` est ce que l'utilisateur voit à l'instant où il cesse de parler,
engagé et queue instable compris.
"""
import json, statistics as st, sys, time
from pathlib import Path
import numpy as np
import websocket
sys.path.insert(0, str(Path(__file__).parent))
from bench_continu import fenetres
from bench_luxasr import wer_infixe
from luxasr_api import transcrire

S = Path(__file__).resolve().parent.parent  # dossier de travail : work/manifest.json
RATE, CHUNK = 16000, 2560


def ws(samples):
    c = websocket.create_connection("wss://luxasr.uni.lu/prod/ws/transcribe", timeout=60)
    msgs, t0 = [], time.monotonic()
    def drain(tmo):
        c.settimeout(tmo)
        while True:
            try:
                m = json.loads(c.recv())
            except Exception:
                return
            m["_t"] = time.monotonic() - t0
            msgs.append(m)
            if m.get("type") in ("recording_stopped", "error"):
                return
    c.send(json.dumps({"type": "config", "language": "lb"}))
    for i in range(0, len(samples), CHUNK):
        c.send_binary((np.clip(samples[i:i + CHUNK], -1, 1) * 32767).astype("<i2").tobytes())
        drain(0.001)
        d = t0 + (i + CHUNK) / RATE - time.monotonic()
        if d > 0:
            time.sleep(d)
    t_stop = time.monotonic() - t0
    c.send(json.dumps({"type": "stop"}))
    drain(60)
    c.close()
    tr = [m for m in msgs if m.get("type") == "transcription"]
    fin = next((m["_t"] for m in msgs if m.get("type") == "recording_stopped"), msgs[-1]["_t"])
    # ce que l'utilisateur voit au moment où il arrête de parler : engagé + queue
    vu = next((m for m in reversed(tr) if m["_t"] <= t_stop), None)
    return {"texte": (tr[-1].get("accumulated_text") or "").strip() if tr else "",
            "t_final": fin - t_stop,
            "t_premier": next((m["_t"] for m in tr if m.get("accumulated_text") or m.get("partial_text")), None),
            "t_premier_engage": next((m["_t"] for m in tr if m.get("accumulated_text")), None),
            "vu_a_l_arret": ((vu.get("accumulated_text") or "") + " " + (vu.get("partial_text") or "")).strip() if vu else ""}


lignes = []
for e in json.load(open(S / "work/manifest.json")):
    a = np.fromfile(e["f32"], dtype="<f4")
    for k, (d, f) in enumerate(fenetres(a, 8, 22)):
        bout = a[int(d * RATE):int(f * RATE)]
        w = ws(bout)
        time.sleep(0.5)
        t_api, m = transcrire(bout, intervalle=0.2, nom=f"{e['id']}_{k:02d}.wav")
        time.sleep(0.5)
        ww, mots = wer_infixe(e["reference"], w["texte"]) if w["texte"] else (1.0, 0)
        wa, mots_a = wer_infixe(e["reference"], t_api) if t_api else (1.0, 0)
        wv, _ = wer_infixe(e["reference"], w["vu_a_l_arret"]) if w["vu_a_l_arret"] else (1.0, 0)
        l = {"id": f"{e['id']}_{k:02d}", "duree_s": round(len(bout) / RATE, 1),
             "wer_ws": ww, "mots_ws": mots, "wer_api": wa, "mots_api": mots_a, "wer_vu_arret": wv,
             "delai_ws_s": w["t_final"], "delai_api_s": m["t_total_s"],
             "t_premier_s": w["t_premier"], "t_premier_engage_s": w["t_premier_engage"],
             "hyp_ws": w["texte"], "hyp_api": t_api, "identiques": w["texte"] == t_api}
        lignes.append(l)
        print(f"{l['id']:14} {l['duree_s']:5.1f}s  WS {ww*100:5.1f}%  API {wa*100:5.1f}%  "
              f"délai WS {w['t_final']:.2f}s API {m['t_total_s']:.2f}s  "
              f"1er aperçu {w['t_premier'] or 0:.1f}s", flush=True)


def pond(cle, mots):
    u = [l for l in lignes if l[mots]]
    return sum(l[cle] * l[mots] for l in u) / sum(l[mots] for l in u)


agg = {
    "n": len(lignes),
    "wer_pondere_ws": pond("wer_ws", "mots_ws"), "wer_median_ws": st.median(l["wer_ws"] for l in lignes),
    "wer_pondere_api": pond("wer_api", "mots_api"), "wer_median_api": st.median(l["wer_api"] for l in lignes),
    "ws_meilleur": sum(l["wer_ws"] < l["wer_api"] for l in lignes),
    "api_meilleure": sum(l["wer_api"] < l["wer_ws"] for l in lignes),
    "identiques": sum(l["identiques"] for l in lignes),
    "delai_ws_median_s": st.median(l["delai_ws_s"] for l in lignes), "delai_ws_max_s": max(l["delai_ws_s"] for l in lignes),
    "delai_api_median_s": st.median(l["delai_api_s"] for l in lignes), "delai_api_max_s": max(l["delai_api_s"] for l in lignes),
    "premier_apercu_median_s": st.median(l["t_premier_s"] for l in lignes if l["t_premier_s"]),
    "premier_engage_median_s": st.median(l["t_premier_engage_s"] for l in lignes if l["t_premier_engage_s"]),
}
print(json.dumps(agg, indent=1))
json.dump({"agrege": agg, "lignes": lignes}, open(S / "bench/enonce_sept19.json", "w"), ensure_ascii=False, indent=1)
