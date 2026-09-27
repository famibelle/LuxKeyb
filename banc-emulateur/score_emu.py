#!/usr/bin/env python3
"""Dépouillement du banc d'émulateur : Gboard luxembourgeois contre notre clavier.

Lit les captures de `bench_emu_touches.py` par OCR (`score.lire`), compte la
barre vide, le top-1 et le top-3, puis apparie les deux claviers position par
position. Le khi² de McNemar porte sur les seules positions où l'un trouve le mot
et pas l'autre ; seuil à 5 % : 3,84.

    python3 score_emu.py
"""
import json, sys
from pathlib import Path

SP = Path(__file__).resolve().parent
sys.path.insert(0, str(SP))
from score import lire, touche  # noqa: E402

SERIES = {"sans diacritique": range(0, 20), "accentuée": range(20, 40)}


def depouiller(clavier):
    journal = json.loads((SP / f"journal_emu_{clavier}.json").read_text(encoding="utf-8"))
    dossier = SP / f"emu_{clavier}"
    cache = SP / f"resultat_emu_{clavier}.json"
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
    cache.write_text(json.dumps(lignes, ensure_ascii=False, indent=1), encoding="utf-8")
    return {(l["phrase"], l["position"]): l for l in lignes}


def pct(a, n):
    return f"{100 * a / n:.1f} %" if n else "—"


def bilan(nom, jeu, cles):
    s = [jeu[k] for k in cles]
    n = len(s)
    return {"nom": nom, "n": n,
            "vide": sum(l["vide"] for l in s),
            "top1": sum(l["top1"] for l in s),
            "top3": sum(l["top3_ok"] for l in s)}


def main():
    lux, gb = depouiller("lux"), depouiller("gboard")
    cles = sorted(set(lux) & set(gb))
    sortie = {"positions": len(cles)}
    for titre, phrases in [("ensemble", range(0, 40))] + list(SERIES.items()):
        ks = [k for k in cles if k[0] in phrases]
        print(f"\n== {titre} : {len(ks)} positions")
        for nom, jeu in (("Lëtzebuergesch Clavier", lux), ("Gboard luxembourgeois", gb)):
            b = bilan(nom, jeu, ks)
            print(f"  {nom:24s} vide {pct(b['vide'], b['n']):>7}  "
                  f"top-1 {pct(b['top1'], b['n']):>7}  top-3 {pct(b['top3'], b['n']):>7}")
            sortie.setdefault(titre, {})[nom] = b
        deux = sum(lux[k]["top3_ok"] and gb[k]["top3_ok"] for k in ks)
        nous = sum(lux[k]["top3_ok"] and not gb[k]["top3_ok"] for k in ks)
        eux = sum(gb[k]["top3_ok"] and not lux[k]["top3_ok"] for k in ks)
        khi2 = (abs(nous - eux) - 1) ** 2 / (nous + eux) if nous + eux else 0.0
        print(f"  apparié : les deux {deux}, nous seuls {nous}, Gboard seul {eux}, "
              f"aucun {len(ks) - deux - nous - eux} ; McNemar khi² = {khi2:.2f} (seuil 3,84)")
        sortie[titre]["apparie"] = {"les_deux": deux, "nous": nous, "gboard": eux, "khi2": round(khi2, 2)}
    print("\nquelques désaccords :")
    for k in [k for k in cles if lux[k]["top3_ok"] != gb[k]["top3_ok"]][:14]:
        l, g = lux[k], gb[k]
        print(f'  « {l["contexte"][-34:]} » → {l["attendu"]}')
        print(f'      nous   {l["top3"]}  {"✓" if l["top3_ok"] else "·"}')
        print(f'      Gboard {g["top3"]}  {"✓" if g["top3_ok"] else "·"}')
    (SP / "comparaison_emu.json").write_text(json.dumps(sortie, ensure_ascii=False, indent=1),
                                             encoding="utf-8")


if __name__ == "__main__":
    main()
