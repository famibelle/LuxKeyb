#!/usr/bin/env python3
"""Banc de prédiction sur émulateur, Gboard luxembourgeois contre notre clavier.

Même corpus que le banc de téléphone : les 40 phrases (341 positions) des deux
journaux `journal_tel_lux.json` et `journal_acc_lux.json`. Chaque lettre est
frappée **sur sa touche**, `é` `ä` `ë` compris : le clavier luxembourgeois de
Gboard les a sur des touches dédiées, comme le nôtre, si bien que le même geste
vaut pour les deux et que le banc n'a plus besoin de reposer le contexte par
intent (défaut du premier banc d'émulateur, voir BANC-CLAVIERS.md).

Le champ est celui de Google Messages ouvert par `sms:0000`, un champ ordinaire.
Toutes les commandes passent par `adb -s emulator-5554` : un téléphone réel peut
être branché en même temps et ne doit jamais recevoir une frappe.

    python3 bench_emu_touches.py gboard
    python3 bench_emu_touches.py lux --limite 1      # essai sur une phrase
"""
import argparse, io, json, subprocess, sys, time
from pathlib import Path
from PIL import Image

SP = Path(__file__).resolve().parent
SERIE = "emulator-5554"

CLAVIERS = {
    "lux": {
        "ime": "com.potomitan.luxkeyboard/com.example.kreyolkeyboard.KreyolInputMethodServiceRefactored",
        "espace": (450, 2114),
        "bande": (0, 1300, 1080, 1660),
    },
    "gboard": {
        "ime": "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME",
        "espace": (591, 2124),
        "bande": (120, 1480, 1000, 1620),
    },
}
# Gboard 18.2.4 (celui du téléphone), installé sur l'AVD `kreyol_playstore` : mêmes
# touches, même barre, seul le dossier de sortie change.
CLAVIERS["gboard18"] = CLAVIERS["gboard"]

# Carte des touches, relevée sur les captures du 2026-09-20 (1080×2340, 440 dpi).
_LUX_X = [73, 176, 279, 382, 486, 590, 694, 798, 902, 1006]
_LUX_X3 = [225, 329, 433, 537, 642, 747, 852]
CARTE_LUX = {c: (_LUX_X[i], 1690) for i, c in enumerate("qwertzuiop")}
CARTE_LUX.update({c: (_LUX_X[i], 1830) for i, c in enumerate("asdfghjkl")})
CARTE_LUX["é"] = (_LUX_X[9], 1830)
CARTE_LUX.update({c: (_LUX_X3[i], 1972) for i, c in enumerate("yxcvbnm")})
CARTE_LUX["ä"] = (242, 2114)
CARTE_LUX["ë"] = (670, 2114)

# Gboard « Luxembourgeois » : deux rangées de onze touches, ë en bout de la
# première, é et ä en bout de la deuxième.
_GB_X = [55, 151, 248, 345, 442, 539, 636, 734, 831, 928, 1025]
CARTE_GBOARD = {c: (_GB_X[i], 1705) for i, c in enumerate("qwertzuiopë")}
CARTE_GBOARD.update({c: (_GB_X[i], 1843) for i, c in enumerate("asdfghjklé" + "ä")})
CARTE_GBOARD.update({c: (_GB_X[2 + i], 1983) for i, c in enumerate("yxcvbnm")})

CARTES = {"lux": CARTE_LUX, "gboard": CARTE_GBOARD, "gboard18": CARTE_GBOARD}


def adb(*a, timeout=120):
    return subprocess.run(["adb", "-s", SERIE, *a], capture_output=True, timeout=timeout)


def shell(cmd, timeout=120):
    return adb("shell", cmd, timeout=timeout)


def clavier_visible():
    out = adb("shell", "dumpsys input_method").stdout.decode("utf-8", "replace")
    return "mInputShown=true" in out


def capture(bande):
    return Image.open(io.BytesIO(adb("exec-out", "screencap", "-p", timeout=30).stdout)).crop(bande)


def capture_stable(bande, essais=5):
    """Deux captures identiques à 0,6 s d'intervalle valent stabilité."""
    img = capture(bande)
    for _ in range(essais):
        time.sleep(0.6)
        suiv = capture(bande)
        if suiv.tobytes() == img.tobytes():
            return suiv
        img = suiv
    return img


def phrases():
    """Les 40 phrases du banc de téléphone, reconstruites depuis leurs journaux.

    La dernière position d'une phrase porte le contexte de tous les mots sauf le
    dernier, qui est l'attendu : c'est la phrase entière."""
    out = []
    for nom in ("journal_tel_lux.json", "journal_acc_lux.json"):
        journal = json.loads((SP / nom).read_text(encoding="utf-8"))
        derniers = {}
        for e in journal:
            derniers[e["phrase"]] = e
        for ip in sorted(derniers):
            e = derniers[ip]
            out.append(f'{e["contexte"]} {e["attendu"]}')
    return out


def ouvrir_champ():
    shell("input keyevent 4; input keyevent 4; sleep 0.4")
    shell("am start -a android.intent.action.SENDTO -d sms:0000 "
          "-p com.google.android.apps.messaging > /dev/null; sleep 2.5")
    for _ in range(5):
        shell("input tap 554 2184; sleep 1.2")
        if clavier_visible():
            return True
    return False


def frappe(mot, carte):
    cmds = []
    for c in mot.lower():
        x, y = carte[c]
        cmds.append(f"input tap {x} {y}; sleep 0.12")
    return "; ".join(cmds)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("clavier", choices=list(CLAVIERS))
    ap.add_argument("--limite", type=int, default=0, help="n'utiliser que les n premières phrases")
    ap.add_argument("--reprise", type=int, default=0, help="reprendre à cette phrase")
    ap.add_argument("--serie", choices=["tout", "sans", "accents"], default="tout")
    args = ap.parse_args()
    cfg, carte = CLAVIERS[args.clavier], CARTES[args.clavier]
    dossier = SP / f"emu_{args.clavier}"
    dossier.mkdir(exist_ok=True)

    tout = phrases()
    if args.serie == "sans":
        tout = tout[:20]
    elif args.serie == "accents":
        tout = tout[20:]
    if args.limite:
        tout = tout[:args.limite]

    shell(f"settings put secure spell_checker_enabled 1")
    adb("shell", "ime", "set", cfg["ime"]); time.sleep(1.5)
    if not ouvrir_champ():
        sys.exit("clavier absent : champ non ouvert")
    sx, sy = cfg["espace"]
    espace = f"; input tap {sx} {sy}; sleep 1.0"
    journal_chemin = SP / f"journal_emu_{args.clavier}.json"
    journal = []
    if args.reprise and journal_chemin.exists():
        journal = [e for e in json.loads(journal_chemin.read_text("utf-8")) if e["phrase"] < args.reprise]
    t0 = time.time()
    for ip, phrase in enumerate(tout):
        if ip < args.reprise:
            continue
        mots = phrase.split()
        if not clavier_visible() and not ouvrir_champ():
            sys.exit(f"clavier perdu à la phrase {ip}")
        shell("input keyevent " + " ".join(["67"] * 140) + "; sleep 0.5; "
              + frappe(mots[0], carte) + espace, timeout=300)
        for i in range(1, len(mots)):
            nom = f"{ip:02d}_{i:02d}.png"
            capture_stable(cfg["bande"]).save(dossier / nom)
            journal.append({"phrase": ip, "position": i, "contexte": " ".join(mots[:i]),
                            "attendu": mots[i], "image": nom})
            if i + 1 < len(mots):
                shell(frappe(mots[i], carte) + espace, timeout=300)
        journal_chemin.write_text(json.dumps(journal, ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"[{args.clavier}] phrase {ip+1}/{len(tout)} ({len(mots)-1} positions, "
              f"{time.time()-t0:.0f}s)", flush=True)
    print(f"[{args.clavier}] terminé : {len(journal)} positions en {time.time()-t0:.0f}s")


if __name__ == "__main__":
    main()
