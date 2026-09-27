#!/usr/bin/env python3
"""Mots modifiés par le clavier, en tapant réellement les touches (Galaxy A21s).

`bench_tel_correction.py` envoyait le texte par `input text`, ce qui contourne le mode
de saisie ordinaire d'un clavier : Gboard n'y corrige presque rien. Ici chaque lettre
est un appui sur sa touche, centré, et une espace suit chaque mot, comme sous un doigt
appliqué. On compare ensuite le texte du champ à ce qui a été tapé.

Ce qui sort différent peut venir de deux choses que l'on ne peut pas séparer depuis
l'extérieur et qu'un utilisateur ne sépare pas non plus : la correction automatique
et le décodage des points de contact (Gboard a ainsi changé un `h` centré en `s`).
On compte donc **les mots qui sortent autres que ceux tapés**, casse et accents repliés.

Les 20 phrases sans diacritique du banc de prédiction, réglages par défaut.

    TEL_SERIE=<ip>:<port> python3 bench_tel_correction_touches.py [lux samsung gboard]
"""
import json, os, sys
from pathlib import Path

SP = Path(__file__).resolve().parent
sys.path.insert(0, str(SP))
os.environ.setdefault("TEL_SERIE", "")
import bench_tel_vitesse as v  # noqa: E402
import bench_tel_correction as c  # noqa: E402

# Touches relevées sur les captures du 2026-09-20 (720×1600).
CARTES = {
    "gboard": {**{ch: (x, 1142) for ch, x in zip("qwertzuiop", [38, 101, 165, 229, 293, 358, 422, 487, 551, 617])},
               **{ch: (x, 1245) for ch, x in zip("asdfghjkl", [38, 101, 165, 229, 293, 358, 422, 487, 551])},
               **{ch: (x, 1348) for ch, x in zip("yxcvbnm", [147, 218, 289, 359, 430, 502, 573])}},
    "samsung": {**{ch: (x, 1158) for ch, x in zip("qwertzuiop", [45, 115, 185, 255, 325, 395, 464, 534, 604, 674])},
                **{ch: (x, 1257) for ch, x in zip("asdfghjkl", [80, 150, 220, 290, 360, 430, 500, 570, 640])},
                **{ch: (x, 1357) for ch, x in zip("yxcvbnm", [150, 219, 289, 360, 430, 500, 570])}},
    "lux": {**{ch: (x, 1188) for ch, x in zip("qwertzuiop", [48, 117, 187, 256, 325, 394, 463, 532, 601, 670])},
            **{ch: (x, 1278) for ch, x in zip("asdfghjkl", [48, 117, 187, 256, 325, 394, 463, 532, 601])},
            **{ch: (x, 1367) for ch, x in zip("yxcvbnm", [150, 219, 289, 358, 430, 499, 568])}},
}


def main():
    ordre = sys.argv[1:] or ["lux", "samsung", "gboard"]
    ph = c.phrases()
    sortie = {}
    for nom in ordre:
        v.preparer(nom)
        carte = CARTES[nom]
        ex, ey = v.GEOMETRIE[nom]["espace"]
        # champ vide au départ : un reste de la phrase précédente fausserait tout
        v.shell("input keyevent 123; sleep 0.2; input keyevent " + " ".join(["67"] * 200) + "; sleep 0.6")
        mots = remplaces = accents = casse = 0
        finals, exemples = [], []
        for phrase in ph:
            v.shell("input keyevent 123; sleep 0.2; input keyevent " + " ".join(["67"] * 140) + "; sleep 0.6")
            avant = (c.texte_du_champ() or "").strip()
            if avant and avant != "":
                v.shell("input keyevent 123; sleep 0.2; input keyevent " + " ".join(["67"] * 200) + "; sleep 0.6")
            for w in phrase.split():
                taps = [f"input tap {carte[ch][0]} {carte[ch][1]}; sleep 0.14" for ch in w]
                v.shell("; ".join(taps) + f"; input tap {ex} {ey}; sleep 0.6")
            final = (c.texte_du_champ() or "").strip()
            finals.append({"tape": phrase, "final": final})
            if not final:
                print(f"  ! {nom} : champ illisible", flush=True)
                continue
            r, a, ca, e = c.comparer(phrase, final)
            mots += len(phrase.split())
            remplaces += r
            accents += a
            casse += ca
            exemples += e
        sortie[nom] = {"mots": mots, "modifies": remplaces, "accents_seuls": accents,
                       "casse_seule": casse, "exemples": exemples, "phrases": finals}
        print(f"{nom:8s} {mots} mots : {remplaces} modifiés, {accents} accents seuls, {casse} casse seule",
              flush=True)
    (SP / "correction_tel_touches.json").write_text(json.dumps(sortie, ensure_ascii=False, indent=1),
                                                    encoding="utf-8")


if __name__ == "__main__":
    main()
