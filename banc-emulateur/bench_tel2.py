#!/usr/bin/env python3
"""Banc de prédiction sur téléphone, phrases **accentuées**.

La première passe évitait les diacritiques faute de pouvoir les écrire avec
`adb shell input text`. Ici chaque lettre accentuée est réellement frappée sur le
clavier testé :

  - Lëtzebuergesch Clavier : `é` et `ë` ont leur propre touche, un simple appui ;
  - Clavier Samsung : appui long sur `e` puis glissement jusqu'à la case voulue
    du popup (`è é ê ë ē`), ce qui demande un geste de 1,2 s par accent.

C'est la différence que le comparatif mesure aussi, au passage : deux frappes
d'un doigt contre un appui long et un glissement.
"""
import argparse, json, random, re, sys, time
from pathlib import Path

SP = Path(__file__).resolve().parent
sys.path.insert(0, str(SP))
from tel import shell, capture  # noqa: E402
from bench_tel import ouvrir_champ, capture_stable  # noqa: E402

CLAVIERS = {
    "lux": {
        "ime": "com.potomitan.luxkeyboard/com.example.kreyolkeyboard.KreyolInputMethodServiceRefactored",
        "espace": (300, 1456),
        "bande": (0, 975, 720, 1050),
        # touches dédiées : un appui suffit
        "accents": {"é": "input tap 671 1277", "ë": "input tap 438 1456"},
    },
    "samsung": {
        "ime": "com.samsung.android.honeyboard/.service.HoneyBoardService",
        "espace": (395, 1455),
        "bande": (0, 940, 720, 1020),
        # appui long sur « e » puis glissement vers la case du popup
        "accents": {"é": "input swipe 185 1156 258 1040 1200",
                    "ë": "input swipe 185 1156 385 1040 1200"},
    },
}


def phrases(n, graine):
    sys.path.insert(0, "/home/medhi/SourceCode/LuxKeyb/Dictionnaires")
    import zls_source
    retenues = []
    for s in zls_source.segments(hors_ligne=True):
        p = s["lb"].strip().rstrip(".!?")
        if not re.match(r"^[A-Za-zéë ]+$", p):
            continue
        if not re.search(r"[éë]", p):
            continue
        if p[0] in "éë":                 # la majuscule automatique fausserait la frappe
            continue
        if 8 <= len(p.split()) <= 15:
            retenues.append(p)
    random.Random(graine).shuffle(retenues)
    return retenues[:n]


def frappe(mot, accents):
    """Commandes shell qui écrivent un mot, accents compris."""
    cmds, tampon = [], ""
    for c in mot:
        if c in accents:
            if tampon:
                cmds.append(f"input text {tampon}; sleep 0.2")
                tampon = ""
            cmds.append(accents[c] + "; sleep 0.4")
        else:
            tampon += c
    if tampon:
        cmds.append(f"input text {tampon}; sleep 0.2")
    return "; ".join(cmds)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("clavier", choices=list(CLAVIERS))
    ap.add_argument("--phrases", type=int, default=20)
    ap.add_argument("--graine", type=int, default=20260908)
    args = ap.parse_args()
    cfg = CLAVIERS[args.clavier]
    dossier = SP / f"acc_{args.clavier}"
    dossier.mkdir(exist_ok=True)

    shell("ime", "set", cfg["ime"]); time.sleep(2)
    ouvrir_champ()
    sx, sy = cfg["espace"]
    espace = f"; input tap {sx} {sy}; sleep 1.0"
    journal, t0 = [], time.time()
    for ip, phrase in enumerate(phrases(args.phrases, args.graine)):
        mots = phrase.split()
        shell("input keyevent " + " ".join(["67"] * 140) + "; sleep 0.4; "
              + frappe(mots[0], cfg["accents"]) + espace, timeout=300)
        for i in range(1, len(mots)):
            nom = f"{ip:02d}_{i:02d}.png"
            capture_stable(cfg["bande"]).save(dossier / nom)
            journal.append({"phrase": ip, "position": i, "contexte": " ".join(mots[:i]),
                            "attendu": mots[i], "image": nom})
            if i + 1 < len(mots):
                shell(frappe(mots[i], cfg["accents"]) + espace, timeout=300)
        print(f"[{args.clavier}] phrase {ip+1} ({len(mots)-1} positions, "
              f"{time.time()-t0:.0f}s)", flush=True)
    (SP / f"journal_acc_{args.clavier}.json").write_text(
        json.dumps(journal, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"[{args.clavier}] terminé : {len(journal)} positions en {time.time()-t0:.0f}s")


if __name__ == "__main__":
    main()
