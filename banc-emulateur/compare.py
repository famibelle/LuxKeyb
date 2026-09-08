#!/usr/bin/env python3
"""Comparaison appariée des deux claviers sur les mêmes positions."""
import json, sys
from pathlib import Path

SP = Path(__file__).resolve().parent


def charger(nom):
    d = json.loads((SP / f"resultat_{nom}.json").read_text(encoding="utf-8"))
    return {(l["phrase"], l["position"]): l for l in d}


def main():
    lux, gb = charger("lux"), charger("gboard")
    cles = sorted(set(lux) & set(gb))
    n = len(cles)
    stats = {}
    for nom, jeu in (("Lëtzebuergesch Clavier", lux), ("Gboard", gb)):
        s = [jeu[k] for k in cles]
        stats[nom] = {
            "positions": n,
            "vide": sum(l["vide"] for l in s),
            "top1": sum(l["top1"] for l in s),
            "top3": sum(l["top3_ok"] for l in s),
        }
    for nom, s in stats.items():
        print(f"{nom}")
        print(f"   barre vide : {s['vide']:3d}/{n}  ({100*s['vide']/n:.1f} %)")
        print(f"   top-1      : {s['top1']:3d}/{n}  ({100*s['top1']/n:.1f} %)")
        print(f"   top-3      : {s['top3']:3d}/{n}  ({100*s['top3']/n:.1f} %)")
    deux = sum(lux[k]["top3_ok"] and gb[k]["top3_ok"] for k in cles)
    seul_l = sum(lux[k]["top3_ok"] and not gb[k]["top3_ok"] for k in cles)
    seul_g = sum(gb[k]["top3_ok"] and not lux[k]["top3_ok"] for k in cles)
    aucun = n - deux - seul_l - seul_g
    print(f"\napparié sur {n} positions")
    print(f"   les deux    : {deux:3d}  ({100*deux/n:.1f} %)")
    print(f"   nous seuls  : {seul_l:3d}  ({100*seul_l/n:.1f} %)")
    print(f"   Gboard seul : {seul_g:3d}  ({100*seul_g/n:.1f} %)")
    print(f"   aucun       : {aucun:3d}  ({100*aucun/n:.1f} %)")
    exemples = [k for k in cles if lux[k]["top3_ok"] != gb[k]["top3_ok"]]
    print("\nquelques désaccords :")
    for k in exemples[:12]:
        l, g = lux[k], gb[k]
        print(f'  « {l["contexte"][-34:]} » → {l["attendu"]}')
        print(f'      nous   {l["top3"]}  {"✓" if l["top3_ok"] else "·"}')
        print(f'      Gboard {g["top3"]}  {"✓" if g["top3_ok"] else "·"}')
    json.dump({"positions": n, "stats": stats,
               "apparie": {"les_deux": deux, "nous": seul_l, "gboard": seul_g, "aucun": aucun}},
              open(SP / "comparaison.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
