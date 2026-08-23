"""Detecte la geometrie du clavier IME a partir d'une capture d'ecran.

Les touches portent un contour #D0D0D0 de 1 dp sur un fond de clavier #F5F5F5 :
c'est ce contour qui sert de marqueur, les interieurs de touches blanches
finissant en degrade exactement sur la couleur du fond.
"""
import sys
import numpy as np
from PIL import Image

BORDER = np.array([208, 208, 208])
TOL = 26

# Disposition alphabetique de la v10.12.15. Elle a change deux fois depuis la
# campagne du 16 aout : "ò" a quitte la rangee 1 (10.11.3, 5a788156, il reste en
# appui long sur "o") et l'apostrophe est revenue en rangee 3 (10.11.4). Les
# comptes attendus sont donc 10/10/9/9, et non plus 11/10/8/9.
ROWS = [
    ["a", "z", "e", "r", "t", "y", "u", "i", "o", "p"],
    ["q", "s", "d", "f", "g", "h", "j", "k", "l", "m"],
    ["shift", "w", "x", "c", "v", "b", "n", "'", "back"],
    ["123", ",", "é", "-", " ", "è", ".", "emoji", "enter"],
]

# Page de symboles (mode 123). C'est l'objet de cette campagne : "#" y a ete
# ajoute en 10.12.15, en 9e colonne de la rangee 3, qui passe de 9 a 10 touches.
ROWS_NUM = [
    ["1", "2", "3", "4", "5", "6", "7", "8", "9", "0"],
    ["-", "/", ":", ";", "(", ")", "€", "&", "@", '"'],
    ["=", ".", ",", "?", "!", "'", "+", "*", "#", "back"],
    ["ABC", "emoji", " ", "enter"],
]

# Index de la touche "#" dans ROWS_NUM
HASH_POS = (2, 8)


def border_mask(im):
    a = np.asarray(im.convert("RGB")).astype(np.int16)
    d = np.abs(a - BORDER).max(axis=2)
    return d <= TOL


def runs(idx, gap=3):
    if len(idx) == 0:
        return []
    out = []
    start = prev = int(idx[0])
    for i in idx[1:]:
        i = int(i)
        if i - prev > gap:
            out.append((start, prev))
            start = i
        prev = i
    out.append((start, prev))
    return out


def detect_rows(m, W, min_row_frac=0.45):
    hproj = m.sum(axis=1) / W
    strong = np.where(hproj > min_row_frac)[0]
    bands = runs(strong, gap=2)
    rows, i = [], 0
    while i < len(bands) - 1:
        top, bot = bands[i][1], bands[i + 1][0]
        if bot - top >= 12:
            rows.append((top, bot))
            i += 2
        else:
            i += 1
    return rows


def keys_in_row(m, top, bot, expected, min_col_frac=0.5):
    """Renvoie les bornes (x0,x1) des touches d'une rangee.

    La detection produit alternativement une touche puis l'interstice qui la
    suit : on garde donc un segment sur deux, en verifiant que le compte tombe
    juste (2n-1 segments pour n touches)."""
    band = m[top:bot, :]
    vproj = band.sum(axis=0) / max(1, (bot - top))
    cols = np.where(vproj > min_col_frac)[0]
    segs = runs(cols, gap=2)
    cells = []
    for a, b in zip(segs, segs[1:]):
        if b[0] - a[1] >= 6:
            cells.append((a[1], b[0]))
    keys = cells[0::2]
    if len(keys) != expected and len(cells) >= expected:
        # repli : garder les `expected` segments les plus larges, dans l'ordre
        widest = sorted(sorted(cells, key=lambda c: c[1] - c[0])[-expected:])
        keys = widest
    return keys, len(cells)


def analyse(path, spec=None):
    """`spec` : disposition attendue, ROWS (alphabetique) par defaut ou
    ROWS_NUM pour la page de symboles."""
    spec = spec or ROWS
    im = Image.open(path)
    W, H = im.size
    m = border_mask(im)
    rows = detect_rows(m, W)
    out = {"w": W, "h": H, "rows": [], "ok": len(rows) == 4}
    for n, (t, b) in enumerate(rows):
        exp = len(spec[n]) if n < 4 else 0
        keys, ncells = keys_in_row(m, t, b, exp)
        out["rows"].append({
            "top": t, "bot": b, "height": b - t,
            "n_keys": len(keys), "expected": exp,
            "keys": keys, "n_cells": ncells,
        })
        if len(keys) != exp:
            out["ok"] = False
    return out


def key_center(info, row, idx):
    r = info["rows"][row]
    x0, x1 = r["keys"][idx]
    return ((x0 + x1) // 2, (r["top"] + r["bot"]) // 2)


def tap_seq(info, text, spec=None):
    """Traduit un texte en suite de taps (x, y) sur le clavier detecte."""
    spec = spec or ROWS
    pos = {}
    for ri, row in enumerate(spec):
        for ki, k in enumerate(row):
            pos.setdefault(k, (ri, ki))
    seq = []
    for ch in text:
        key = ch if ch in pos else None
        if key is None:
            raise KeyError(f"caractere non mappe: {ch!r}")
        ri, ki = pos[key]
        seq.append(key_center(info, ri, ki))
    return seq


if __name__ == "__main__":
    import json
    spec = ROWS_NUM if "--num" in sys.argv else ROWS
    info = analyse(sys.argv[1], spec)
    print(json.dumps({k: v for k, v in info.items() if k != "rows"}))
    for n, r in enumerate(info["rows"]):
        print(f"  rangee {n}: y={r['top']}..{r['bot']} h={r['height']} "
              f"touches={r['n_keys']}/{r['expected']} cellules={r['n_cells']}")
