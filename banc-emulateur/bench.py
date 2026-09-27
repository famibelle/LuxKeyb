#!/usr/bin/env python3
"""Banc de frappe sur émulateur : prédiction du mot suivant, clavier contre clavier.

Protocole, identique pour les deux claviers :
  - le champ de saisie reçoit le début de la phrase (intent sms:?body=, Unicode sûr) ;
  - on tape la barre d'espace du clavier testé, ce qui déclenche sa prédiction ;
  - on capture la barre de suggestions et on lit les mots proposés (OCR) ;
  - on compare au mot réellement écrit dans le corpus, casse repliée.
"""
import argparse, io, json, random, re, shlex, subprocess, sys, time, unicodedata
from pathlib import Path
from PIL import Image

SP = Path(__file__).resolve().parent
CLAVIERS = {
    "lux": {
        "ime": "com.potomitan.luxkeyboard/com.example.kreyolkeyboard.KreyolInputMethodServiceRefactored",
        "espace": (450, 2113),
        "bande": (0, 1300, 1080, 1660),
    },
    "gboard": {
        "ime": "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME",
        "espace": (591, 2122),
        "bande": (120, 1480, 1000, 1620),
    },
}
CHAMP = (554, 2184)
# Frappe de l'espace, attente du rendu, puis état du clavier renvoyé dans le même
# aller-retour : « 1 » si la fenêtre de saisie était bien affichée à la capture.
ESPACE = "; input tap {sx} {sy}; sleep 1.0; dumpsys input_method | grep -c mInputShown=true"


def adb(*args, timeout=60):
    return subprocess.run(["adb", *args], capture_output=True, timeout=timeout)


def shell(*args, timeout=60):
    return adb("shell", *args, timeout=timeout)


def capture(bande):
    out = adb("exec-out", "screencap", "-p", timeout=30).stdout
    return Image.open(io.BytesIO(out)).crop(bande)


def clavier_visible():
    out = shell("dumpsys", "input_method").stdout.decode("utf-8", "replace")
    return "mInputShown=true" in out


def poser_contexte(texte):
    """Remplit le champ avec `texte`, curseur en fin, clavier affiché.

    Le clavier est refermé avant de relancer l'activité : sans cela le champ
    bouge pendant la transition et le tap de mise au point tombe sur une touche.
    """
    from urllib.parse import quote
    shell("input", "keyevent", "4")
    time.sleep(0.25)
    shell("am", "start", "-a", "android.intent.action.VIEW",
          "-d", f"sms:0000?body={quote(texte)}")
    time.sleep(1.0)
    for essai in range(4):
        shell("input", "tap", str(CHAMP[0]), str(CHAMP[1]))
        time.sleep(0.55)
        if essai or clavier_visible():
            break
    else:
        print("  ! clavier non affiché après 4 essais", flush=True)
    shell("input", "keyevent", "123")  # fin de ligne
    time.sleep(0.2)


def sequence_pose(texte):
    """Commandes shell qui reposent le contexte et rouvrent le clavier."""
    from urllib.parse import quote
    uri = f"sms:0000?body={quote(texte)}"
    return ("input keyevent 4; sleep 0.2"
            f"; am start -a android.intent.action.VIEW -d {shlex.quote(uri)} > /dev/null"
            "; sleep 1.6"
            f"; input tap {CHAMP[0]} {CHAMP[1]}; sleep 1.2"
            "; input keyevent 123; sleep 0.2")


def texte_du_champ():
    """Le contenu réel du champ de saisie, pour vérifier le contexte posé."""
    sys.path.insert(0, str(SP))
    from ui import dump, nodes
    for n in nodes(dump()):
        if n["res"].endswith("compose_message_text"):
            return n["text"]
    return None


def mots_de(phrase):
    return phrase.split()


def phrases_du_corpus(n, graine):
    sys.path.insert(0, "/home/medhi/SourceCode/LuxKeyb/Dictionnaires")
    import zls_source
    segs = zls_source.segments(hors_ligne=True)
    propre = re.compile(r"^[^\W\d_](?:[^\W\d_]|['’ ])*[.!?]?$", re.UNICODE)
    retenues = []
    for s in segs:
        p = s["lb"].strip()
        if not propre.match(p):
            continue
        m = mots_de(p.rstrip(".!?"))
        if 8 <= len(m) <= 15:
            retenues.append(" ".join(m))
    random.Random(graine).shuffle(retenues)
    return retenues[:n]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("clavier", choices=list(CLAVIERS))
    ap.add_argument("--phrases", type=int, default=20)
    ap.add_argument("--graine", type=int, default=20260908)
    args = ap.parse_args()

    cfg = CLAVIERS[args.clavier]
    dossier = SP / f"shots_{args.clavier}"
    dossier.mkdir(exist_ok=True)
    shell("ime", "set", cfg["ime"])
    time.sleep(1.5)

    phrases = phrases_du_corpus(args.phrases, args.graine)
    journal = []
    t0 = time.time()
    sx, sy = cfg["espace"]
    for ip, phrase in enumerate(phrases):
        mots = mots_de(phrase)
        # Le contexte est reposé par intent (Unicode sûr) puis l'espace du clavier
        # est frappé : tout tient en un seul appel shell, les attentes se faisant
        # sur l'appareil. Un aller-retour adb coûte ici près d'une seconde.
        sequence = sequence_pose(mots[0]) + ESPACE.format(sx=sx, sy=sy)
        for i in range(1, len(mots)):
            res = shell(sequence, timeout=180)
            if not res.stdout.decode().strip().endswith("1"):
                # clavier absent au moment de la capture : on repose le contexte
                shell(sequence_pose(" ".join(mots[:i])) +
                      ESPACE.format(sx=sx, sy=sy), timeout=180)
            nom = f"{ip:02d}_{i:02d}.png"
            capture(cfg["bande"]).save(dossier / nom)
            journal.append({"phrase": ip, "position": i, "contexte": " ".join(mots[:i]),
                            "attendu": mots[i], "image": nom})
            if i + 1 < len(mots):
                suivant = mots[i]
                if suivant.isascii():
                    sequence = (f"input text {shlex.quote(suivant)}; sleep 0.3"
                                + ESPACE.format(sx=sx, sy=sy))
                else:
                    sequence = (sequence_pose(" ".join(mots[:i + 1]))
                                + ESPACE.format(sx=sx, sy=sy))
        print(f"[{args.clavier}] phrase {ip+1}/{len(phrases)} "
              f"({len(mots)-1} positions, {time.time()-t0:.0f}s)", flush=True)
    (SP / f"journal_{args.clavier}.json").write_text(
        json.dumps(journal, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"[{args.clavier}] terminé : {len(journal)} positions en {time.time()-t0:.0f}s")


if __name__ == "__main__":
    main()
