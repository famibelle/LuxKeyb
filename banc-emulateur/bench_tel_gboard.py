#!/usr/bin/env python3
"""Banc de prédiction sur le Galaxy A21s : Gboard 18.2.4, clavier « Lëtzebuergesch ».

Mêmes 40 phrases que les bancs de septembre (341 positions), reconstruites depuis
`journal_tel_lux.json` et `journal_acc_lux.json`, dans le même champ (recherche
du Wierderbuch). Chaque lettre est frappée sur sa touche, `é` `ä` `ë` compris :
Gboard les a sur des touches dédiées, comme notre clavier.

Le numéro de série Wi-Fi du téléphone change à chaque connexion : il se donne en
variable d'environnement, `TEL_SERIE=<ip>:<port>`.

    TEL_SERIE=<ip>:<port> python3 bench_tel_gboard.py [--limite N]
"""
import argparse, json, os, sys, time
from pathlib import Path

SP = Path(__file__).resolve().parent
sys.path.insert(0, str(SP))
import tel  # noqa: E402
tel.SERIE = os.environ.get("TEL_SERIE", tel.SERIE)
from tel import shell, capture  # noqa: E402
import bench_tel  # noqa: E402
from bench_tel import capture_stable, ouvrir_champ  # noqa: E402

DELAI = float(os.environ.get("DELAI", "0.12"))
ESPACE = (395, 1452)
BANDE = (0, 1010, 720, 1090)
# Touches relevées sur la capture du 2026-09-20 (720×1600) : onze touches par rangée.
_X = [38, 101, 165, 229, 293, 358, 422, 487, 551, 617, 682]
CARTE = {c: (_X[i], 1142) for i, c in enumerate("qwertzuiopë")}
CARTE.update({c: (_X[i], 1245) for i, c in enumerate("asdfghjklé" + "ä")})
CARTE.update({c: (x, 1348) for c, x in zip("yxcvbnm", [147, 218, 289, 359, 430, 502, 573])})


def phrases():
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


ACCENTS = "éäë"


def frappe(mot):
    """Comme les bancs de septembre : le texte ASCII par `input text`, exact, sans que
    Gboard ait à décoder des points de contact ; seules les trois lettres accentuées
    sont frappées sur leur touche dédiée. Un décodage contextuel de Gboard remplaçait
    en effet un `h` tapé au centre de sa touche (`schonn` devenait `scsonn`), quelle que
    soit la cadence des appuis."""
    cmds, tampon = [], ""
    for c in mot.lower():
        if c in ACCENTS:
            if tampon:
                cmds.append(f"input text {tampon}; sleep 0.2")
                tampon = ""
            x, y = CARTE[c]
            cmds.append(f"input tap {x} {y}; sleep 0.3")
        else:
            tampon += c
    if tampon:
        cmds.append(f"input text {tampon}; sleep 0.2")
    return "; ".join(cmds)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--limite", type=int, default=0)
    ap.add_argument("--reprise", type=int, default=0)
    args = ap.parse_args()
    tout = phrases()
    if args.limite:
        tout = tout[:args.limite]
    dossier = SP / "tel_gboard"
    dossier.mkdir(exist_ok=True)
    chemin = SP / "journal_tel_gboard.json"
    journal = []
    if args.reprise and chemin.exists():
        journal = [e for e in json.loads(chemin.read_text("utf-8")) if e["phrase"] < args.reprise]
    ouvrir_champ()
    sx, sy = ESPACE
    espace = f"; input tap {sx} {sy}; sleep 0.9"
    t0 = time.time()
    for ip, phrase in enumerate(tout):
        if ip < args.reprise:
            continue
        mots = phrase.split()
        shell("input keyevent 123; sleep 0.3; input keyevent " + " ".join(["67"] * 140) + "; sleep 0.5; "
              + frappe(mots[0]) + espace, timeout=300)
        for i in range(1, len(mots)):
            nom = f"{ip:02d}_{i:02d}.png"
            capture_stable(BANDE).save(dossier / nom)
            journal.append({"phrase": ip, "position": i, "contexte": " ".join(mots[:i]),
                            "attendu": mots[i], "image": nom})
            if i + 1 < len(mots):
                shell(frappe(mots[i]) + espace, timeout=300)
        # Contenu réel du champ, pour contrôler la frappe : Gboard peut corriger un mot,
        # mais une phrase méconnaissable signale une touche manquée.
        champ = next((n["text"] for n in tel.noeuds() if n["cls"].endswith("EditText")), "")
        for e in journal:
            if e["phrase"] == ip:
                e["champ_final"] = champ
        chemin.write_text(json.dumps(journal, ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"[tel gboard] phrase {ip+1}/{len(tout)} ({len(mots)-1} positions, "
              f"{time.time()-t0:.0f}s)", flush=True)
    print(f"[tel gboard] terminé : {len(journal)} positions en {time.time()-t0:.0f}s")


if __name__ == "__main__":
    main()
