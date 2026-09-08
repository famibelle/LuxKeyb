#!/usr/bin/env python3
"""Banc de prédiction sur téléphone réel (Galaxy A21s, Android 12).

Les deux claviers reçoivent exactement la même chose : les mots de la phrase sont
écrits l'un après l'autre dans un champ de saisie, la barre d'espace du clavier
testé est frappée, et la barre de suggestions est photographiée. Tout se passe
dans une seule session de saisie, donc sans reposer le contexte.

Les phrases retenues n'ont aucun diacritique : `adb shell input text` ne sait pas
écrire « ë », et faire dépendre la mesure d'un appui long propre à chaque clavier
aurait introduit une différence entre les deux.
"""
import argparse, json, random, re, sys, time
from pathlib import Path

SP = Path(__file__).resolve().parent
sys.path.insert(0, str(SP))
from tel import shell, capture, noeuds, tap  # noqa: E402

CLAVIERS = {
    "lux": {
        "ime": "com.potomitan.luxkeyboard/com.example.kreyolkeyboard.KreyolInputMethodServiceRefactored",
        "espace": (300, 1456),
        "bande": (0, 975, 720, 1050),
    },
    "samsung": {
        "ime": "com.samsung.android.honeyboard/.service.HoneyBoardService",
        "espace": (395, 1455),
        "bande": (0, 940, 720, 1020),
    },
}
CHAMP = (360, 653)          # champ de recherche du Wierderbuch, dans notre application


def phrases(n, graine):
    sys.path.insert(0, "/home/medhi/SourceCode/LuxKeyb/Dictionnaires")
    import zls_source
    retenues = [s["lb"].strip().rstrip(".!?") for s in zls_source.segments(hors_ligne=True)]
    retenues = [p for p in retenues if re.match(r"^[A-Za-z ]+$", p) and 8 <= len(p.split()) <= 15]
    random.Random(graine).shuffle(retenues)
    return retenues[:n]


def capture_stable(bande, essais=4):
    img = capture(bande)
    for _ in range(essais):
        time.sleep(0.5)
        suiv = capture(bande)
        if suiv.tobytes() == img.tobytes():
            return suiv
        img = suiv
    return img


def ouvrir_champ():
    shell("am", "start", "-n",
          "com.potomitan.luxkeyboard/com.example.kreyolkeyboard.SettingsActivity")
    time.sleep(3.5)
    for n in noeuds():
        if "Wierderbuch" in n["text"]:
            tap(n["cx"], n["cy"], pause=3)
            break
    tap(*CHAMP, pause=2.5)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("clavier", choices=list(CLAVIERS))
    ap.add_argument("--phrases", type=int, default=20)
    ap.add_argument("--graine", type=int, default=20260908)
    args = ap.parse_args()
    cfg = CLAVIERS[args.clavier]
    dossier = SP / f"tel_{args.clavier}"
    dossier.mkdir(exist_ok=True)

    shell("ime", "set", cfg["ime"]); time.sleep(2)
    ouvrir_champ()
    sx, sy = cfg["espace"]
    journal, t0 = [], time.time()
    for ip, phrase in enumerate(phrases(args.phrases, args.graine)):
        mots = phrase.split()
        shell("input keyevent " + " ".join(["67"] * 120) + "; sleep 0.4"
              + f"; input text {mots[0]}; sleep 0.3; input tap {sx} {sy}; sleep 0.9", timeout=180)
        for i in range(1, len(mots)):
            nom = f"{ip:02d}_{i:02d}.png"
            capture_stable(cfg["bande"]).save(dossier / nom)
            journal.append({"phrase": ip, "position": i, "contexte": " ".join(mots[:i]),
                            "attendu": mots[i], "image": nom})
            if i + 1 < len(mots):
                shell(f"input text {mots[i]}; sleep 0.3; input tap {sx} {sy}; sleep 0.9",
                      timeout=180)
        print(f"[{args.clavier}] phrase {ip+1} ({len(mots)-1} positions, "
              f"{time.time()-t0:.0f}s)", flush=True)
    (SP / f"journal_tel_{args.clavier}.json").write_text(
        json.dumps(journal, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"[{args.clavier}] terminé : {len(journal)} positions en {time.time()-t0:.0f}s")


if __name__ == "__main__":
    main()
