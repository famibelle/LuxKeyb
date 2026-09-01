#!/usr/bin/env python3
"""Parole continue **sur le téléphone** : combien de fois le micro se referme-t-il ?

    python stt/bench/bench_device_continu.py --work W --out R.json --device IP:PORT

`bench_device.py` rejoue des tranches découpées aux silences : une tranche, un
appui sur le micro, un énoncé. C'est le régime « phrase par phrase », le seul où
une pause coïncide forcément avec une fin de phrase. Ce banc-ci rejoue les
fichiers de **60 s entiers** et laisse le détecteur de l'application faire ce
qu'il fait vraiment — refermer le micro dès 1,5 s de silence, au milieu du
propos si c'est là que le locuteur respire.

Deux régimes, le même audio :

    un_appui   un seul appui au départ, comme quelqu'un qui dicte sans
               surveiller le clavier. Ce qu'il obtient s'arrête à la première
               respiration ; `couverture` dit quelle part de la minute a
               survécu.
    re_appui   le banc rappuie dès que l'application repasse à IDLE, ce qu'un
               utilisateur très attentif ferait. C'est la borne optimiste :
               la reprise y est immédiate, alors qu'en vrai il faut d'abord
               s'apercevoir que le micro s'est fermé.

**Comment la coupure est vue.** Pas par le journal de l'application : l'APK Labs
est un build release, et sur ce One UI aucune ligne `LuxAsrSession` n'atteint
`logcat` (vérifié — le tampon montre Chromium et le système, rien de l'IME).

Elle est lue dans le **DOM** : la dictée arrive en texte de composition
(`setComposingText`), et la fin d'énoncé appelle `finishComposingText()`, ce que
Chrome rend au champ sous la forme d'un événement `compositionend`. C'est donc
l'application elle-même qui annonce sa coupure, à l'instant où elle la décide.

Un premier essai déduisait la coupure d'un silence du texte : il comptait
faux — 5 s sans mise à jour arrivent aussi au démarrage, avant la première
hypothèse, et les ré-appuis déclenchés sur ces fausses coupures **arrêtaient**
la dictée en cours, un second appui valant « stop ». Les chiffres du régime
`re_appui` mesuraient alors l'instrument, pas l'application.

Autocontrôle : chaque passage doit se terminer par au moins un `compositionend`,
celui de la fin normale. Zéro `compositionend` sur tout un passage ne veut pas
dire « aucune coupure » mais « le signal ne remonte pas » — à vérifier avant de
lire quoi que ce soit d'autre.
"""

import argparse
import json
import re
import subprocess
import sys
import threading
import time
import wave
from http.server import ThreadingHTTPServer
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import bench_device as bd
from bench_luxasr import wer_infixe
from wer import normalize, repetition_ratio


# La page du banc, augmentée des événements de composition : c'est par eux que
# la fermeture du micro se voit.
PAGE = bd.PAGE.replace("</script>", """
t.addEventListener('compositionstart',()=>post('compo_debut'));
t.addEventListener('compositionend',()=>post('compo_fin'));
</script>""")


def ecrire_wav_plein(f32, dst):
    bd.ecrire_wav(f32, dst)


def prepare_phone(dev, work, fichiers):
    """Reprend le montage de bench_device : serveur local, Chrome, micro."""
    bd.PAGE = PAGE
    bd.DOSSIER_WAV = work / "wav"
    bd.DOSSIER_WAV.mkdir(exist_ok=True)
    with wave.open(str(bd.DOSSIER_WAV / "silence.wav"), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(16000)
        w.writeframes(b"\0" * 3200)
    for e in fichiers:
        ecrire_wav_plein(e["f32"], bd.DOSSIER_WAV / f"{e['id']}.wav")

    serveur = ThreadingHTTPServer(("127.0.0.1", bd.PORT), bd.Handler)
    threading.Thread(target=serveur.serve_forever, daemon=True).start()
    subprocess.run(["adb", "-s", dev, "reverse", f"tcp:{bd.PORT}", f"tcp:{bd.PORT}"],
                   capture_output=True)

    bd.adb(dev, "shell", "media", "volume", "--stream", "3", "--set", "15")
    bd.adb(dev, "shell", "settings", "put", "system", "screen_off_timeout", "1800000")
    bd.adb(dev, "shell", "am", "start", "-a", "android.intent.action.VIEW",
           "-d", f"http://localhost:{bd.PORT}/",
           "-n", "com.android.chrome/com.google.android.apps.chrome.Main")
    time.sleep(6)
    bd.assurer_chrome(dev)
    bd.adb(dev, "shell", "uiautomator", "dump", "/sdcard/ui.xml")
    if 'id/url_bar' in bd.adb(dev, "shell", "cat", "/sdcard/ui.xml") \
            .split('focused="true"')[0][-400:]:
        bd.adb(dev, "shell", "input", "keyevent", "111")
        time.sleep(1.0)

    larg, haut = map(int, re.search(r"(\d+)x(\d+)",
                                    bd.adb(dev, "shell", "wm", "size")).groups())
    bd.taper(dev, larg * 0.28, haut * 0.285)      # déverrouille la lecture audio
    time.sleep(2)
    if not bd.ETAT.depuis(0, "ready"):
        print("⚠️  la page n'a pas confirmé le déverrouillage audio")
    bd.taper(dev, larg // 2, haut * 0.17)         # focus textarea → clavier
    time.sleep(2.5)
    micro = bd.bornes_micro(dev)
    if micro is None:
        micro = (int(larg * 0.945), int(haut * 0.6325))
        print(f"⚠️  micro absent de l'arbre d'accessibilité — repli {micro}")
    print(f"🎙️  micro en {micro}")
    return micro


def passage(dev, micro, e, mode, attente_max):
    bd.ETAT.envoyer("clear")
    time.sleep(0.8)
    marque = bd.ETAT.marque()
    bd.taper(dev, *micro)
    time.sleep(1.6)                       # ouverture WebSocket + micro
    bd.ETAT.envoyer("play", url=f"/{e['id']}.wav", clip=e["id"])

    t_audio = time.time()
    fin_audio = None
    reappuis, coupures, vus = 0, [], 0
    n_textes, t_dernier_texte = 0, time.time()
    limite = time.time() + attente_max
    while time.time() < limite:
        if fin_audio is None and bd.ETAT.depuis(marque, "audio_end"):
            fin_audio = time.time()
        textes = bd.ETAT.depuis(marque, "texte")
        if len(textes) != n_textes:
            n_textes, t_dernier_texte = len(textes), time.time()

        # L'application a figé son texte de composition : le micro est refermé.
        # Tant que l'audio joue, c'est une coupure subie ; après, c'est la fin
        # normale de l'énoncé, qu'on ne compte pas.
        fins = bd.ETAT.depuis(marque, "compo_fin")
        if len(fins) > vus:
            vus = len(fins)
            if fin_audio is None:
                coupures.append(round(time.time() - t_audio, 1))
                if mode == "re_appui":
                    time.sleep(0.3)
                    bd.taper(dev, *micro)
                    reappuis += 1
                    time.sleep(1.4)
                    t_dernier_texte = time.time()
                    continue

        if fin_audio and time.time() - fin_audio > 6.0 \
                and time.time() - t_dernier_texte > 3.0:
            break
        time.sleep(0.2)

    textes = [x for x in bd.ETAT.depuis(marque, "texte") if x.get("v")]
    hyp = textes[-1]["v"].strip() if textes else ""
    return {"hyp": hyp, "maj": len(textes), "reappuis": reappuis,
            "coupures": len(coupures), "t_coupures": coupures,
            "compo_fin": vus, "compo_debut": len(bd.ETAT.depuis(marque, "compo_debut"))}


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--device", required=True)
    ap.add_argument("--fichiers", type=int, default=3)
    ap.add_argument("--modes", default="un_appui,re_appui")
    ap.add_argument("--attente-max", type=float, default=110.0)
    args = ap.parse_args()

    dev = args.device
    manifest = json.loads((args.work / "manifest.json").read_text(encoding="utf-8"))
    fichiers = manifest[:args.fichiers]
    micro = prepare_phone(dev, args.work, fichiers)

    lignes = []
    for e in fichiers:
        ref_mots = len(normalize(e["reference"]).split())
        for mode in args.modes.split(","):
            r = passage(dev, micro, e, mode, args.attente_max)
            w, mots = wer_infixe(e["reference"], r["hyp"]) if r["hyp"] else (1.0, 0)
            lignes.append({
                "fichier": e["id"], "mode": mode, "wer": w, "mots": mots,
                "mots_reference": ref_mots,
                "couverture": mots / max(1, ref_mots),
                "repetition": repetition_ratio(r["hyp"]),
                **r})
            print(f"  {e['id']} {mode:9} WER {w*100:5.1f} %  "
                  f"{mots:3d}/{ref_mots} mots ({mots/max(1,ref_mots)*100:3.0f} %)  "
                  f"{r['coupures']} coupure(s) {r['t_coupures']}  "
                  f"{r['reappuis']} ré-appui(s)  "
                  f"[compo {r['compo_debut']}/{r['compo_fin']}]", flush=True)

    res = {"date": time.strftime("%Y-%m-%d"), "appareil": dev,
           "corpus": "Akabi/Luxemburgish_Press_Conferences_Gov",
           "protocole": "fichiers de 60 s rejoués au haut-parleur, "
                        "coupures lues sur compositionend dans la page "
                        "(le journal de l'IME n'atteint pas logcat sur ce build release)",
           "mesures": lignes}
    args.out.write_text(json.dumps(res, ensure_ascii=False, indent=1), encoding="utf-8")

    for mode in args.modes.split(","):
        g = [l for l in lignes if l["mode"] == mode]
        if not g:
            continue
        print(f"\n  {mode:9} couverture médiane "
              f"{sorted(l['couverture'] for l in g)[len(g)//2]*100:.0f} %  ·  "
              f"{sum(l['coupures'] for l in g)} coupures sur {len(g)} minute(s)")
    print(f"\n💾 {args.out}")


if __name__ == "__main__":
    main()
