#!/usr/bin/env python3
"""Dépouillement du banc : lecture OCR des barres capturées, puis comptage.

Le mot attendu est cherché parmi les trois premières suggestions affichées,
casse repliée. Une pastille coupée par le bord de la capture ne compte que si
le mot attendu commence par ce qui a été lu (au moins trois lettres).
"""
import json, sys, unicodedata
from pathlib import Path
from PIL import Image
from rapidocr_onnxruntime import RapidOCR

SP = Path(__file__).resolve().parent
OCR = RapidOCR()


def plie(mot):
    """Casse et diacritiques repliées : l'OCR perd régulièrement le tréma."""
    sans = unicodedata.normalize("NFD", mot.strip().lower())
    return "".join(c for c in sans if not unicodedata.combining(c))


def couleur(img, box):
    """Couleur de la pastille sous un mot lu.

    On échantillonne une grille autour de la boîte plutôt que son centre : au
    centre on tombe sur le glyphe, blanc sur fond rouge.
    """
    rgb = img.convert("RGB")
    x0 = int(min(p[0] for p in box)) - 6
    x1 = int(max(p[0] for p in box)) + 6
    y0 = int(min(p[1] for p in box)) - 6
    y1 = int(max(p[1] for p in box)) + 6
    votes = {"LB": 0, "FR": 0}
    for i in range(9):
        for j in range(5):
            x = x0 + (x1 - x0) * i // 8
            y = y0 + (y1 - y0) * j // 4
            x = max(0, min(rgb.width - 1, x))
            y = max(0, min(rgb.height - 1, y))
            r, g, b = rgb.getpixel((x, y))
            if r > 140 and g < 110 and b < 110:
                votes["LB"] += 1
            elif b > 140 and r < 130 and g > 110:
                votes["FR"] += 1
    if max(votes.values()) == 0:
        return "?"
    return max(votes, key=votes.get)


def lire(chemin, clavier):
    img = Image.open(chemin)
    res, _ = OCR(str(chemin))
    lus = []
    for box, txt, conf in (res or []):
        t = txt.strip()
        if not t or conf < 0.5:
            continue
        x0 = min(p[0] for p in box)
        x1 = max(p[0] for p in box)
        tronque = x1 >= img.width - 12
        tag = couleur(img, box) if clavier == "lux" else "LB"
        lus.append({"x": x0, "mot": t, "tronque": tronque, "rangee": tag})
    lus.sort(key=lambda d: d["x"])
    if clavier == "lux":
        lus = [d for d in lus if d["rangee"] == "LB"]
    return lus


def touche(attendu, cand):
    a = plie(attendu)
    for c in cand:
        m = plie(c["mot"])
        if m == a:
            return True
        if c["tronque"] and len(m) >= 3 and a.startswith(m):
            return True
    return False


def main(clavier):
    journal = json.loads((SP / f"journal_{clavier}.json").read_text(encoding="utf-8"))
    dossier = SP / f"shots_{clavier}"
    lignes = []
    for e in journal:
        cand = lire(dossier / e["image"], clavier)
        trois = cand[:3]
        lignes.append({**e,
                       "suggestions": [c["mot"] for c in cand],
                       "top3": [c["mot"] for c in trois],
                       "vide": not cand,
                       "top1": touche(e["attendu"], trois[:1]),
                       "top3_ok": touche(e["attendu"], trois)})
    (SP / f"resultat_{clavier}.json").write_text(
        json.dumps(lignes, ensure_ascii=False, indent=1), encoding="utf-8")
    n = len(lignes)
    vides = sum(l["vide"] for l in lignes)
    t1 = sum(l["top1"] for l in lignes)
    t3 = sum(l["top3_ok"] for l in lignes)
    print(f"{clavier} : {n} positions")
    print(f"  barre vide      : {vides:4d}  ({100*vides/n:.1f} %)")
    print(f"  top-1           : {t1:4d}  ({100*t1/n:.1f} %)")
    print(f"  top-3           : {t3:4d}  ({100*t3/n:.1f} %)")
    if n - vides:
        print(f"  top-3 hors vide : {t3:4d}  ({100*t3/(n-vides):.1f} % des barres remplies)")


if __name__ == "__main__":
    for c in sys.argv[1:] or ["lux", "gboard"]:
        main(c)
