#!/usr/bin/env python3
"""Dépouillement final du comparatif de prédiction : LuxKeyb, Gboard, Clavier Samsung.

Deux bancs, mêmes 341 positions (40 phrases du corpus ZLS) :

- **téléphone** (Galaxy A21s, Android 12) : LuxKeyb 20.3.0 et Samsung 5.4.85.4 relevés
  le 8 septembre 2026, Gboard 18.2.4 le 20 ;
- **émulateur** (Pixel 7 Pro, Android 16, 3 Go) : LuxKeyb 26.2.0 et Gboard 18.2.4 le
  20 septembre, Gboard 12.4 le 20 aussi, sur un AVD Android 14.

Deux corrections propres à Gboard, sans lesquelles ses chiffres sont faux :

1. **L'icône de la barre est lue comme un mot** (« 88 » par l'OCR) : on l'écarte.
2. **Sa meilleure suggestion est la case du centre**, pas celle de gauche. Mesuré sur
   les trois relevés, le mot juste tombe au centre 28, 26 et 20 fois contre 20, 16 et
   15 à gauche ; chez LuxKeyb et chez Samsung c'est la gauche qui domine (34 et 27).
   Le top-1 de Gboard est donc sa case centrale.

Le top-3 ne dépend pas de l'ordre : on compte les trois cases.

    python3 score_final.py
"""
import json, unicodedata
from pathlib import Path

SP = Path(__file__).resolve().parent
ICONE = {"88", "8", "B", "8B"}          # l'icône de grille de Gboard, lue comme un mot


def plie(mot):
    return "".join(c for c in unicodedata.normalize("NFD", mot.strip().lower())
                   if not unicodedata.combining(c))


def lire_json(nom):
    return json.loads((SP / nom).read_text(encoding="utf-8"))


def par_cle(lignes, decalage=0):
    return {(l["phrase"] + decalage, l["position"]): l for l in lignes}


def gboard(lignes):
    """Recalcule vide, top-1 (case du centre) et top-3 depuis les mots lus."""
    out = {}
    for l in lignes:
        s = [x for x in l["suggestions"] if x not in ICONE]
        attendu = plie(l["attendu"])
        centre = s[1] if len(s) >= 3 else (s[0] if s else None)
        out[(l["phrase"], l["position"])] = {
            "vide": not s,
            "top1": centre is not None and plie(centre) == attendu,
            "top3_ok": any(plie(x) == attendu for x in s[:3]),
            "attendu": l["attendu"], "phrase": l["phrase"],
        }
    return out


def deja_calcule(lignes, decalage=0):
    return {(l["phrase"] + decalage, l["position"]):
            {"vide": l["vide"], "top1": l["top1"], "top3_ok": l["top3_ok"],
             "attendu": l["attendu"], "phrase": l["phrase"] + decalage}
            for l in lignes}


def mcnemar(a, b, ks, cle):
    x = sum(a[k][cle] and not b[k][cle] for k in ks)
    y = sum(b[k][cle] and not a[k][cle] for k in ks)
    return x, y, ((abs(x - y) - 1) ** 2 / (x + y) if x + y else 0.0)


def taux(d, ks, cle):
    return sum(bool(d[k][cle]) for k in ks) / len(ks)


def bloc(titre, claviers, ref, ks_all):
    print(f"\n######## {titre}")
    vues = [("ensemble", lambda k: True),
            ("sans diacritique", lambda k: k[0] < 20),
            ("accentuée", lambda k: k[0] >= 20),
            ("ensemble, sans les phrases 0 et 20 (retapées pendant la mise au point)",
             lambda k: k[0] not in (0, 20))]
    sortie = {}
    for nom, sel in vues:
        ks = [k for k in ks_all if sel(k)]
        print(f"== {nom} ({len(ks)} positions)")
        sortie[nom] = {}
        for lab, d in claviers:
            v, t1, t3 = (taux(d, ks, c) for c in ("vide", "top1", "top3_ok"))
            print(f"  {lab:28s} vide {v:5.1%}  top-1 {t1:5.1%}  top-3 {t3:5.1%}")
            sortie[nom][lab] = {"n": len(ks), "vide": v, "top1": t1, "top3": t3}
        for lab, d in claviers:
            if lab == ref[0]:
                continue
            for cle in ("top1", "top3_ok"):
                x, y, khi = mcnemar(ref[1], d, ks, cle)
                print(f"    {ref[0]} contre {lab:22s} {cle:7s}: {x:3d} / {y:3d}  khi² {khi:5.2f}"
                      f"{'  *' if khi > 3.84 else ''}")
    return sortie


def main():
    # téléphone
    lux_t = {**deja_calcule(lire_json("resultat_tel_lux.json")),
             **deja_calcule(lire_json("resultat_acc_lux.json"), 20)}
    sam_t = {**deja_calcule(lire_json("resultat_tel_samsung.json")),
             **deja_calcule(lire_json("resultat_acc_samsung.json"), 20)}
    gb_t = gboard(lire_json("resultat_tel_gboard.json"))
    ks = sorted(set(lux_t) & set(sam_t) & set(gb_t))
    res = {"telephone": bloc("TÉLÉPHONE (Galaxy A21s)",
                             [("LuxKeyb 20.3.0", lux_t), ("Clavier Samsung 5.4.85.4", sam_t),
                              ("Gboard 18.2.4", gb_t)], ("LuxKeyb 20.3.0", lux_t), ks)}
    # émulateur
    lux_e = deja_calcule(lire_json("resultat_emu_lux.json"))
    g18_e = gboard(lire_json("resultat_emu_gboard18.json"))
    g12_e = gboard(lire_json("resultat_emu_gboard.json"))
    ks = sorted(set(lux_e) & set(g18_e) & set(g12_e))
    res["emulateur"] = bloc("ÉMULATEUR",
                            [("LuxKeyb 26.2.0", lux_e), ("Gboard 18.2.4", g18_e),
                             ("Gboard 12.4", g12_e)], ("LuxKeyb 26.2.0", lux_e), ks)
    (SP / "comparaison_finale.json").write_text(json.dumps(res, ensure_ascii=False, indent=1),
                                                encoding="utf-8")


if __name__ == "__main__":
    main()
