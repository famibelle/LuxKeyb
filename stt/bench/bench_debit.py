#!/usr/bin/env python3
"""La dictée en ligne sous débit montant contraint, **sur le téléphone**.

    python stt/bench/bench_debit.py --work W --device 192.168.1.37:44721 --out R.json

Même montage que `bench_device.py` (Chrome rejoue l'extrait au haut-parleur
devant le micro du téléphone, la dictée s'écrit dans la page), avec en plus
`proxy_debit` qui tourne **sur le téléphone** et par lequel passe la dictée.
Le débit est changé en cours de route selon un scénario.

Pourquoi le proxy est sur le téléphone : voir `proxy_debit.c`. Brider depuis
le poste ne montre rien, le tunnel adb absorbe tout.

La dictée envoie 32 000 octets par seconde (PCM 16 bits, 16 kHz). Les débits
ci-dessous sont en octets par seconde montants, mesurés au proxy.
"""

import argparse
import json
import re
import signal
import subprocess
import sys
import threading
import time
from http.server import ThreadingHTTPServer
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import bench_device as bd
from bench_luxasr import wer_infixe

DISTANT = "/data/local/tmp"
PROXY_PORT = 8899
ILLIMITE = 0

# (nom, [(seconde après l'appui sur le micro, débit)], ce qu'on attend)
SCENARIOS = [
    ("illimité", [(0, ILLIMITE)], "aucune alerte"),
    ("48 Ko/s (384 kbit/s)", [(0, 48_000)], "aucune alerte : 1,5 × le besoin"),
    ("24 Ko/s (192 kbit/s)", [(0, 24_000)],
     "alerte vers 10 s, micro jamais coupé, texte complet après l'envoi du reste"),
    ("12 Ko/s (96 kbit/s)", [(0, 12_000)],
     "alerte vers 5 s, micro coupé vers 16 s, le reste part, « trop lente »"),
    ("trou de 10 s à 8 Ko/s", [(0, ILLIMITE), (8, 8_000), (18, ILLIMITE)],
     "alerte puis retour à la normale, micro jamais coupé, texte complet"),
    ("zone blanche après 8 s", [(0, ILLIMITE), (8, 1)],
     "abandon vers 15 s, « connexion perdue », texte d'avant gardé"),
]

# Ce que le champ montre tant que la dictée n'est pas close : le micro et son
# vumètre pendant l'écoute, puis un cercle qui tourne pendant que le reste
# part et que le service conclut. Depuis que la dictée finit d'envoyer après
# avoir coupé le micro, cette seconde phase peut durer près de 30 s.
EN_COURS = re.compile(r"[🎤◐◓◑◒]")


def texte_propre(v):
    return re.sub(r"\s*[◐◓◑◒]\s*$", "", bd.texte_dicte(v)).strip()


def journal_proxy(dev):
    return bd.adb(dev, "shell", "cat", f"{DISTANT}/proxy.log")


def regler_debit(dev, debit):
    bd.adb(dev, "shell", f"echo {debit} > {DISTANT}/debit")


def evenements_app(dev):
    out = bd.adb(dev, "logcat", "-d", "-v", "epoch", "-s", "LuxAsrSession:*")
    lignes = []
    for l in out.splitlines():
        m = re.match(r"\s*(\d+\.\d+)\s+\d+\s+\d+\s+([DIWE])\s+LuxAsrSession:\s*(.*)", l)
        if m and not m.group(3).startswith("\tat ") and not m.group(3).startswith("at "):
            lignes.append((float(m.group(1)), m.group(3)))
    return lignes


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("--device", required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--micro", default="667,1011")
    ap.add_argument("--duree", type=float, default=30.0,
                    help="secondes d'audio rejouées par scénario")
    ap.add_argument("--fichier", default="", help="id du fichier de 60 s à rejouer")
    ap.add_argument("--captures", type=Path, help="dossier des captures du bandeau")
    ap.add_argument("--scenarios", default="", help="numéros à jouer, ex. 3,5 (tous par défaut)")
    args = ap.parse_args()
    # Interrompu, le banc doit quand même retirer le proxy du téléphone (finally).
    signal.signal(signal.SIGTERM, lambda *a: sys.exit(1))
    dev = args.device
    micro = tuple(int(v) for v in args.micro.split(","))

    manifest = json.loads((args.work / "manifest.json").read_text(encoding="utf-8"))
    e = next(m for m in manifest if m["id"].startswith(args.fichier)) if args.fichier else manifest[0]
    bd.DOSSIER_WAV = args.work / "wav"
    bd.DOSSIER_WAV.mkdir(exist_ok=True)
    extrait = bd.DOSSIER_WAV / "debit.wav"
    a = np.fromfile(e["f32"], dtype="<f4")[: int(args.duree * 16000)]
    tmp = args.work / "debit.f32"
    a.astype("<f4").tofile(tmp)
    bd.ecrire_wav(tmp, extrait)
    silence = bd.DOSSIER_WAV / "silence.wav"
    if not silence.exists():
        import wave
        with wave.open(str(silence), "wb") as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(16000)
            w.writeframes(b"\0" * 3200)

    if not bd.adb(dev, "shell", "pidof", "proxy_debit").strip():
        raise SystemExit("❌ proxy_debit ne tourne pas sur le téléphone")

    serveur = ThreadingHTTPServer(("127.0.0.1", bd.PORT), bd.Handler)
    threading.Thread(target=serveur.serve_forever, daemon=True).start()
    bd.adb(dev, "reverse", f"tcp:{bd.PORT}", f"tcp:{bd.PORT}")
    bd.volume_max(dev)

    # La dictée passe par le proxy du téléphone. Le processus du clavier ne
    # relit le proxy qu'à son démarrage : on le relance.
    regler_debit(dev, ILLIMITE)
    bd.adb(dev, "shell", "settings", "put", "global", "http_proxy", f"127.0.0.1:{PROXY_PORT}")
    ime = "com.potomitan.luxkeyboard/com.example.kreyolkeyboard.KreyolInputMethodServiceRefactored"
    bd.adb(dev, "shell", "am", "force-stop", "com.potomitan.luxkeyboard")
    # Un arrêt forcé du clavier actif fait basculer le système sur le clavier
    # du constructeur, et pas tout de suite : une resélection immédiate est
    # écrasée. On insiste jusqu'à ce qu'elle tienne.
    for _ in range(10):
        time.sleep(1.5)
        bd.adb(dev, "shell", "ime", "set", ime)
        time.sleep(1.5)
        if bd.adb(dev, "shell", "settings", "get", "secure", "default_input_method").strip() == ime:
            break
    else:
        raise SystemExit("❌ impossible de resélectionner le clavier LuxKeyb")

    bd.adb(dev, "shell", "am", "start", "-a", "android.intent.action.VIEW",
           "-d", f"http://localhost:{bd.PORT}/",
           "-n", "com.android.chrome/com.google.android.apps.chrome.Main")
    time.sleep(6)
    bd.assurer_chrome(dev)
    larg, haut = map(int, re.search(r"(\d+)x(\d+)", bd.adb(dev, "shell", "wm", "size")).groups())
    bd.taper(dev, larg * 0.28, haut * 0.285)
    time.sleep(2)
    bd.taper(dev, larg // 2, haut * 0.17)
    time.sleep(2.5)
    if "luxkeyboard" not in bd.adb(dev, "shell", "dumpsys", "input_method").split("mCurId=")[1][:60]:
        raise SystemExit("❌ le clavier affiché n'est pas LuxKeyb")

    resultats = []
    try:
        choix = [int(x) for x in args.scenarios.split(",")] if args.scenarios else range(len(SCENARIOS))
        for nom, plan, attendu in (SCENARIOS[i] for i in choix):
            print(f"\n▶ {nom} — attendu : {attendu}", flush=True)
            regler_debit(dev, plan[0][1])
            bd.ETAT.envoyer("clear")
            time.sleep(1.0)
            bd.adb(dev, "logcat", "-c")
            marque = bd.ETAT.marque()
            t0 = time.time()
            bd.taper(dev, *micro)

            arret = threading.Event()

            def appliquer():
                for t, d in plan[1:]:
                    if arret.wait(max(0, t0 + t - time.time())):
                        return
                    regler_debit(dev, d)
                    print(f"   {t:4.0f} s : débit → {d or 'illimité'}", flush=True)

            numero = len(resultats)   # figé ici : le résultat est ajouté avant les dernières captures

            def capturer():
                if not args.captures:
                    return
                args.captures.mkdir(parents=True, exist_ok=True)
                i = 0
                def une():
                    png = subprocess.run(["adb", "-s", dev, "exec-out", "screencap", "-p"],
                                         capture_output=True).stdout
                    (args.captures / f"{numero}_{time.time() - t0:05.1f}.png").write_bytes(png)
                while not arret.is_set():
                    une()
                    i += 1
                    arret.wait(2.0)
                # Le message qui dit pourquoi la dictée s'est fermée s'affiche
                # juste après la fermeture : on le capture aussi.
                une()
                time.sleep(1.0)
                une()

            threads = [threading.Thread(target=appliquer, daemon=True),
                       threading.Thread(target=capturer, daemon=True)]
            for th in threads:
                th.start()
            time.sleep(1.6)
            bd.ETAT.envoyer("play", url="/debit.wav", clip="debit")

            fin = None
            close = False
            limite = time.time() + args.duree + 60
            while time.time() < limite:
                if fin is None and bd.ETAT.depuis(marque, "audio_end"):
                    fin = time.time()
                textes = bd.ETAT.depuis(marque, "texte")
                vu = any("🎤" in (x.get("v") or "") for x in textes)
                if vu and textes and not EN_COURS.search(textes[-1].get("v") or ""):
                    close = True
                    time.sleep(1.0)
                    break
                time.sleep(0.2)
            arret.set()
            t_fin_dictee = time.time() - t0
            # Laisser le texte final arriver même si la dictée s'est close tôt,
            # puis remettre le débit et attendre la fin de l'audio rejoué.
            regler_debit(dev, ILLIMITE)
            while fin is None and time.time() < limite:
                if bd.ETAT.depuis(marque, "audio_end"):
                    fin = time.time()
                time.sleep(0.2)

            textes = [texte_propre(x.get("v")) for x in bd.ETAT.depuis(marque, "texte")]
            hyp = next((t for t in reversed(textes) if t), "")
            w, mots = wer_infixe(e["reference"], hyp) if hyp else (1.0, 0)
            ev = evenements_app(dev)
            t_app0 = ev[0][0] if ev else None

            def quand(motif):
                for t, m in ev:
                    if motif in m:
                        return round(t - t_app0, 1)
                return None

            r = {
                "scenario": nom, "plan": plan, "attendu": attendu,
                "close": close, "duree_dictee_s": round(t_fin_dictee, 1),
                "t_alerte_lente_s": quand("réseau lent"),
                "t_micro_coupe_s": quand("micro coupé"),
                "t_abandon_s": quand("abandon"),
                "erreur": next((m for _, m in ev if m.startswith("❌")), None),
                "mots_rendus": len(hyp.split()),
                "wer_infixe": round(w, 3) if hyp else None,
                "hyp": hyp,
            }
            resultats.append(r)
            print(f"   dictée close à {r['duree_dictee_s']} s · alerte : {r['t_alerte_lente_s']} · "
                  f"micro coupé : {r['t_micro_coupe_s']} · abandon : {r['t_abandon_s']}", flush=True)
            print(f"   {r['mots_rendus']} mots, WER {w*100:.1f} %  « {hyp[:70]} »", flush=True)
            time.sleep(2)
    finally:
        regler_debit(dev, ILLIMITE)
        bd.adb(dev, "shell", "settings", "put", "global", "http_proxy", ":0")
        bd.adb(dev, "shell", "settings", "delete", "global", "http_proxy")
        args.out.write_text(json.dumps({"fichier": e["id"], "duree_s": args.duree,
                                        "journal_proxy": journal_proxy(dev),
                                        "scenarios": resultats},
                                       ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"\n💾 {args.out} — proxy global retiré du téléphone")


if __name__ == "__main__":
    main()
