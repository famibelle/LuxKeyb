#!/usr/bin/env python3
"""Agrege les result.json de la campagne en un tableau comparatif."""
import glob
import json
import os

SP = os.path.dirname(os.path.abspath(__file__))
LABELS = {}
for line in open(f"{SP}/devices.txt"):
    if "|" in line:
        a, b = line.strip().split("|", 1)
        LABELS[a] = b

rows = []
for p in sorted(glob.glob(f"{SP}/results/*/result.json")):
    d = json.load(open(p))
    avd = d["avd"]
    r = {"avd": avd, "label": LABELS.get(avd, avd),
         "geom": d.get("geometry"), "dpi": d.get("density"),
         "api": d.get("api", "").split("android-")[-1].split("/")[0],
         "boot": d.get("boot_seconds"), "err": d.get("error")}
    for o in d.get("orientations", []):
        # surtout pas o["orientation"][0] : « portrait » et « paysage »
        # partagent la meme initiale et s'ecrasaient l'un l'autre
        k = {"portrait": "p", "paysage": "a"}[o["orientation"]]
        r[f"{k}_ok"] = o.get("layout_ok")
        r[f"{k}_rot"] = o.get("rotation")
        r[f"{k}_kh"] = o.get("key_height_dp")
        r[f"{k}_kw"] = o.get("key_width_dp")
        r[f"{k}_ime"] = o.get("ime_height_dp")
        r[f"{k}_share"] = o.get("ime_share_app_pct")
        r[f"{k}_appleft"] = o.get("app_left_dp")
        r[f"{k}_typed"] = o.get("typed")
        r[f"{k}_typedok"] = o.get("typed_ok")
        r[f"{k}_below"] = o.get("px_below_last_row")
        r[f"{k}_err"] = o.get("error") or (o.get("errors") or None)
        # Page de symboles (campagne du 23 aout)
        r[f"{k}_symok"] = o.get("sym_layout_ok")
        r[f"{k}_symrows"] = [x["keys"] for x in (o.get("sym_rows") or [])]
        r[f"{k}_symw"] = o.get("sym_key_width_dp")
        r[f"{k}_symspread"] = o.get("sym_width_spread_dp")
        r[f"{k}_hashok"] = o.get("sym_hash_ok")
        r[f"{k}_hashoff"] = o.get("sym_hash_offset_px")
        r[f"{k}_symerr"] = o.get("sym_error")
    rows.append(r)

hdr = (f"{'appareil':34} {'géométrie':10} {'dpi':>4} {'api':>3} | "
       f"{'P touche':>8} {'P clav%':>7} {'P saisie':>8} {'P 123':>6} {'P #':>4} | "
       f"{'Y touche':>8} {'Y clav%':>7} {'Y saisie':>8} {'Y 123':>6} {'Y #':>4}")
print(hdr)
print("-" * len(hdr))
for r in rows:
    def f(v, s=""):
        return "-" if v is None else f"{v}{s}"
    def oui(v):
        return "OK" if v else ("NON" if v is not None else "-")
    print(f"{r['label'][:34]:34} {f(r['geom']):10} {f(r['dpi']):>4} "
          f"{f(r['api']):>3} | "
          f"{f(r.get('p_kh')):>8} {f(r.get('p_share')):>7} "
          f"{oui(r.get('p_typedok')):>8} {oui(r.get('p_symok')):>6} "
          f"{oui(r.get('p_hashok')):>4} | "
          f"{f(r.get('a_kh')):>8} {f(r.get('a_share')):>7} "
          f"{oui(r.get('a_typedok')):>8} {oui(r.get('a_symok')):>6} "
          f"{oui(r.get('a_hashok')):>4}")

print()
for r in rows:
    probs = []
    if r.get("err"):
        probs.append(r["err"])
    for k, nom in (("p", "portrait"), ("a", "paysage")):
        if r.get(f"{k}_ok") is False or r.get(f"{k}_ok") is None:
            probs.append(f"{nom}: géométrie clavier non conforme")
        if r.get(f"{k}_err"):
            probs.append(f"{nom}: {r[f'{k}_err']}")
        if r.get(f"{k}_symok") is False:
            probs.append(f"{nom}: page de symboles {r.get(f'{k}_symrows')} "
                         f"au lieu de [10, 10, 10, 4]")
        if r.get(f"{k}_hashok") is False:
            probs.append(f"{nom}: « # » n'a pas ete insere "
                         f"({r.get(f'{k}_symerr') or 'champ inchange'})")
        if r.get(f"{k}_typedok") is False:
            probs.append(f"{nom}: saisie = {r.get(f'{k}_typed')!r}")
        if k == "a" and r.get("a_rot") not in (90, 270) and r.get("a_rot") is not None:
            probs.append(f"paysage: rotation restée à {r['a_rot']}")
    if probs:
        print(f"[{r['label']}]")
        for p in probs:
            print("   -", p)

# La synthèse va dans results/, à côté des mesures dont elle dérive, et non
# dans le répertoire des scripts qu'elle polluerait à chaque passage.
os.makedirs(f"{SP}/results", exist_ok=True)
json.dump(rows, open(f"{SP}/results/resume.json", "w"),
          ensure_ascii=False, indent=1)
