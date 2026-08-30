#!/usr/bin/env python3
"""Compare deux passages de `bench_device.py` sur les mêmes tranches.

Sert à mesurer ce que change un réglage du découpage côté serveur : le texte
arrive-t-il plus tôt et en plus de fois, et à quel prix en exactitude. Les deux
passages doivent avoir tourné dos à dos sur le même appareil, sinon l'acoustique
de la pièce se mélange à l'effet cherché.
"""

import argparse
import json
import statistics as st
from pathlib import Path


def charge(p):
    return {l["id"]: l for l in json.loads(Path(p).read_text(encoding="utf-8"))}


def agrege(lignes):
    utiles = [l for l in lignes if l["mots"]]
    pond = (sum(l["wer"] * l["mots"] for l in utiles) / sum(l["mots"] for l in utiles)
            if utiles else float("nan"))
    prem = [l["t_premier_texte"] for l in lignes if l.get("t_premier_texte")]
    return {
        "tranches": len(lignes),
        "muettes": sum(1 for l in lignes if not l["mots"]),
        "wer_pondere": pond,
        "wer_median": st.median([l["wer"] for l in utiles]) if utiles else float("nan"),
        "maj_total": sum(l["passes"] for l in lignes),
        # « le texte a bougé avant que la personne ait fini de parler » —
        # compter les mises à jour ne suffit pas : la dernière remplace toujours
        # la précédente, même quand tout est arrivé après coup.
        "avant_la_fin": sum(1 for l in lignes
                            if l.get("t_premier_texte")
                            and l["t_premier_texte"] < l["dur"]),
        "t_premier_median": st.median(prem) if prem else None,
    }


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--a", required=True, help="passage de référence")
    ap.add_argument("--b", required=True, help="passage à comparer")
    ap.add_argument("--nom-a", default="A")
    ap.add_argument("--nom-b", default="B")
    ap.add_argument("--out", type=Path)
    args = ap.parse_args()

    A, B = charge(args.a), charge(args.b)
    communs = sorted(set(A) & set(B))
    print(f"\n{len(communs)} tranches communes\n")
    print(f"  {'tranche':22} {args.nom_a:>22}   {args.nom_b:>22}")
    for i in communs:
        a, b = A[i], B[i]
        ta = f"{a['t_premier_texte']:.1f}s" if a.get("t_premier_texte") else "  -  "
        tb = f"{b['t_premier_texte']:.1f}s" if b.get("t_premier_texte") else "  -  "
        print(f"  {i:22} {a['wer']*100:5.1f} % {a['passes']:2d} maj {ta:>7}   "
              f"{b['wer']*100:5.1f} % {b['passes']:2d} maj {tb:>7}")

    res = {}
    for nom, d in ((args.nom_a, A), (args.nom_b, B)):
        ag = agrege([d[i] for i in communs])
        res[nom] = ag
        print(f"\n  {nom} : WER pondéré {ag['wer_pondere']*100:.1f} % · "
              f"médiane {ag['wer_median']*100:.1f} % · {ag['maj_total']} mises à jour · "
              f"{ag['avant_la_fin']}/{len(communs)} tranches où le texte arrive "
              f"avant la fin de la parole"
              + (f" · 1er texte {ag['t_premier_median']:.2f} s"
                 if ag["t_premier_median"] else ""))

    da = [B[i]["wer"] - A[i]["wer"] for i in communs if A[i]["mots"] and B[i]["mots"]]
    if da:
        print(f"\n  écart apparié sur {len(da)} tranches : "
              f"médiane {st.median(da)*100:+.1f} pt · moyenne "
              f"{sum(da)/len(da)*100:+.1f} pt")
        res["ecart_apparie"] = {"n": len(da), "median": st.median(da),
                                "moyen": sum(da) / len(da)}
    if args.out:
        args.out.write_text(json.dumps(res, ensure_ascii=False, indent=1),
                            encoding="utf-8")
        print(f"\n💾 {args.out}")


if __name__ == "__main__":
    main()
