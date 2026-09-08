#!/usr/bin/env python3
"""Banc de frappe, seconde version : chaque clavier reçoit le contexte comme il
sait le lire.

Le premier banc reposait le contexte par intent à chaque mot accentué, faute de
pouvoir taper « ë » avec `adb shell input text`. Gboard s'en accommode : il
reconstruit son contexte à partir du champ. Notre clavier, non — sur une session
neuve il repart sans historique et la barre reste vide. Mesurer les deux ainsi
revenait à mesurer ce défaut plutôt que le moteur de prédiction.

Ici :
  - « live » (notre clavier) : une seule session par phrase, les mots sont frappés
    dans le champ et les trois lettres accentuées du luxembourgeois sont tapées
    sur leurs touches dédiées ;
  - « intent » (Gboard) : le champ reçoit le contexte, ce que Gboard relit.
Dans les deux cas la barre d'espace du clavier testé est frappée, puis la barre
de suggestions est photographiée.
"""
import argparse, json, re, shlex, sys, time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from bench import CLAVIERS, CHAMP, ESPACE, shell, capture, sequence_pose  # noqa: E402


def capture_stable(bande, essais=5):
    """Photographie la barre une fois qu'elle a fini de se peindre.

    Sur cet émulateur le rendu arrive parfois après la seconde d'attente : on
    prenait alors soit une barre encore vide, soit les suggestions de la
    position précédente. Deux captures identiques à 0,6 s d'intervalle valent
    stabilité.
    """
    img = capture(bande)
    for _ in range(essais):
        time.sleep(0.6)
        suivante = capture(bande)
        if suivante.tobytes() == img.tobytes():
            return suivante
        img = suivante
    return img

SP = Path(__file__).resolve().parent
# Touches accentuées de notre clavier, relevées sur la disposition livrée.
TOUCHES = {"é": (1006, 1830), "ä": (242, 2114), "ë": (670, 2114)}
ACCENTS = "".join(TOUCHES)


def phrases_du_corpus(n, graine):
    """Phrases ZLS tapables telle quelle : lettres, espaces, apostrophe droite,
    et les trois voyelles à tréma ou accent qui ont leur touche."""
    import random
    sys.path.insert(0, "/home/medhi/SourceCode/LuxKeyb/Dictionnaires")
    import zls_source
    propre = re.compile(r"^[A-Za-zéäë' ]+$")
    retenues = []
    for s in zls_source.segments(hors_ligne=True):
        p = s["lb"].strip().rstrip(".!?")
        if not propre.match(p):
            continue
        mots = p.split()
        if not (8 <= len(mots) <= 15):
            continue
        if mots[0][0] in ACCENTS:      # la majuscule automatique fausserait la frappe
            continue
        retenues.append(" ".join(mots))
    random.Random(graine).shuffle(retenues)
    return retenues[:n]


def frappe_du_mot(mot):
    """Commandes shell qui écrivent un mot : texte ASCII d'un bloc, lettres
    accentuées sur leur touche."""
    cmds, tampon = [], ""
    for c in mot:
        if c in TOUCHES:
            if tampon:
                cmds.append(f"input text {shlex.quote(tampon)}; sleep 0.15")
                tampon = ""
            x, y = TOUCHES[c]
            cmds.append(f"input tap {x} {y}; sleep 0.2")
        else:
            tampon += c
    if tampon:
        cmds.append(f"input text {shlex.quote(tampon)}; sleep 0.15")
    return "; ".join(cmds)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("clavier", choices=list(CLAVIERS))
    ap.add_argument("--mode", choices=["live", "intent"], required=True)
    ap.add_argument("--phrases", type=int, default=20)
    ap.add_argument("--graine", type=int, default=20260908)
    args = ap.parse_args()

    cfg = CLAVIERS[args.clavier]
    dossier = SP / f"shots2_{args.clavier}"
    dossier.mkdir(exist_ok=True)
    shell("ime", "set", cfg["ime"])
    time.sleep(1.5)
    sx, sy = cfg["espace"]
    espace = ESPACE.format(sx=sx, sy=sy)

    phrases = phrases_du_corpus(args.phrases, args.graine)
    journal, t0 = [], time.time()
    for ip, phrase in enumerate(phrases):
        mots = phrase.split()
        if args.mode == "live":
            # une seule session : on ouvre le champ, on le vide, on frappe
            shell(sequence_pose(mots[0]).replace("; input keyevent 123; sleep 0.2", ""),
                  timeout=180)
            shell("input keyevent " + " ".join(["67"] * 80) + "; sleep 0.4"
                  + "; " + frappe_du_mot(mots[0]) + espace, timeout=180)
        else:
            shell(sequence_pose(mots[0]) + espace, timeout=180)
        for i in range(1, len(mots)):
            nom = f"{ip:02d}_{i:02d}.png"
            capture_stable(cfg["bande"]).save(dossier / nom)
            journal.append({"phrase": ip, "position": i, "contexte": " ".join(mots[:i]),
                            "attendu": mots[i], "image": nom})
            if i + 1 < len(mots):
                if args.mode == "live":
                    shell(frappe_du_mot(mots[i]) + espace, timeout=180)
                else:
                    shell(sequence_pose(" ".join(mots[:i + 1])) + espace, timeout=180)
        print(f"[{args.clavier}/{args.mode}] phrase {ip+1}/{len(phrases)} "
              f"({len(mots)-1} positions, {time.time()-t0:.0f}s)", flush=True)
    (SP / f"journal2_{args.clavier}.json").write_text(
        json.dumps(journal, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"[{args.clavier}] terminé : {len(journal)} positions en {time.time()-t0:.0f}s")


if __name__ == "__main__":
    main()
