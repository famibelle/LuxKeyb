#!/usr/bin/env python3
"""Le sel du tirage des lieux : chaque case de l'album des paysages doit rester
atteinte par au moins trois mots de Wuertplaz, dans les quatre langues.

Refait en Python le calcul de `SeriesPaysagesAssetTest` : la forme d'une
grille remonte à son représentant de famille, qui donne son champ ; son rang
dans le dictionnaire donne sa rareté ; `Lieu.pour` tire son lieu parmi ceux
du champ (FNV-1a sur « sel + mot », sur les unités UTF-16 comme Kotlin).
La répartition est lue dans `Lieu.choix` (Paysages.kt).

    python3 scripts/sel_des_lieux.py                    # les cases les plus minces avec le sel actuel
    python3 scripts/sel_des_lieux.py --chercher 5000    # les meilleurs sels « lieu:9x3:<k>: »
    python3 scripts/sel_des_lieux.py --chercher 5000 --prefixe "lieu:4x9:"

Un sel ne se change qu'avec une nouvelle répartition, et une bascule de plus
dans le carnet (`Carnet.basculeDeLaGrille`) : il fixe le lieu des cartes déjà
gagnées.
"""
import argparse
import json
import re
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
APPLI = RACINE / "android_keyboard/app/src/main"
ACTIFS = APPLI / "assets"
PAYSAGES = APPLI / "java/com/example/kreyolkeyboard/carnet/Paysages.kt"
CARNET = APPLI / "java/com/example/kreyolkeyboard/carnet/CarnetModels.kt"
LANGUES = ["", "_de", "_en", "_pt"]
PLANCHER = 3


def repartition():
    """`Lieu.choix` : {champ ou None: [lieux]}, et le sel actuel."""
    texte = PAYSAGES.read_text(encoding="utf-8")
    bloc = texte[texte.index("fun choix(champ: Champ?)"):]
    bloc = bloc[:bloc.index("\n        }\n")]
    choix = {}
    for champ, lieux in re.findall(r"(Champ\.\w+|null) -> listOf\(([^)]*)\)", bloc):
        cle = None if champ == "null" else champ.split(".")[1].lower()
        choix[cle] = [l.strip() for l in lieux.split(",")]
    sel = re.search(r'private const val SEL = "([^"]*)"', texte).group(1)
    return choix, sel


def seuils():
    t = CARNET.read_text(encoding="utf-8")
    return [int(re.search(rf"const val {n} = ([\d_]+)", t).group(1).replace("_", ""))
            for n in ("SEUIL_COMMUN", "SEUIL_PEU_COMMUN", "SEUIL_RARE")]


def mots_des_grilles():
    """Par langue : la liste (représentant, champ, rareté) des formes de Wuertplaz."""
    familles = json.loads((ACTIFS / "luxemburgish_familles.json").read_text())["familles"]
    rep = {t: t for t in familles}
    for t, formes in familles.items():
        for f in formes.split(" "):
            if f:
                rep.setdefault(f, t)
    blasons = json.loads((ACTIFS / "luxemburgish_blasons.json").read_text())["blasons"]
    champ = {k: (v.get("c") or None) for k, v in blasons.items()}
    rang = {}
    for i, e in enumerate(json.loads((ACTIFS / "luxemburgish_dict.json").read_text())):
        rang.setdefault(e[0], i)
    s = seuils()

    def rarete(r):
        if r is None:
            return 3
        return 0 if r < s[0] else 1 if r < s[1] else 2 if r < s[2] else 3

    par_langue = {}
    for suf in LANGUES:
        grilles = json.loads((ACTIFS / f"luxemburgish_chassecroise{suf}.json").read_text())["grilles"]
        vus, liste = set(), []
        for g in grilles:
            for m in g["mots"]:
                f = m["f"]
                if f in vus:
                    continue
                vus.add(f)
                t = rep.get(f, f)
                liste.append((t, champ.get(t), rarete(rang.get(f))))
        par_langue[suf] = liste
    return par_langue


def hachage(texte):
    h = 0x811C9DC5
    for u in texte.encode("utf-16-le").hex(" ", 2).split():
        h ^= int(u[2:] + u[:2], 16)
        h = (h * 0x01000193) & 0xFFFFFFFF
    return h


def compter(choix, sel, mots):
    """{langue: {(lieu, rareté): mots}} sur toutes les cases de l'album."""
    album = [(l, r) for ls in choix.values() for i, l in enumerate(ls) for r in range(4) if r < 3 or i == 0]
    resultat = {}
    for suf, liste in mots.items():
        n = dict.fromkeys(album, 0)
        for tete, champ, r in liste:
            ls = choix[champ]
            lieu = ls[0] if len(ls) == 1 or r == 3 else ls[(hachage(sel + tete) >> 1) % len(ls)]
            n[(lieu, r)] += 1
        resultat[suf] = n
    return album, resultat


def minces(resultat, combien=6):
    return sorted((v, suf or "lb", f"{l}:{r}") for suf, n in resultat.items() for (l, r), v in n.items())[:combien]


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--chercher", type=int, default=0, help="nombre de sels à essayer")
    ap.add_argument("--prefixe", default="lieu:9x3:")
    a = ap.parse_args()
    choix, sel = repartition()
    mots = mots_des_grilles()
    album, res = compter(choix, sel, mots)
    print(f"{len(album)} cases, sel actuel {sel!r} : les plus minces {minces(res)}")
    if a.chercher:
        essais = []
        for k in range(1, a.chercher + 1):
            s = f"{a.prefixe}{k}:"
            v = sorted(x for n in compter(choix, s, mots)[1].values() for x in n.values())
            essais.append((v[0], sum(v[:10]), s))
        essais.sort(reverse=True)
        for plancher, somme, s in essais[:5]:
            print(f"{s!r} : case la plus mince {plancher}, dix plus minces {somme}")
        if essais[0][0] < PLANCHER:
            print(f"Aucun sel ne tient le plancher de {PLANCHER} : la répartition est trop serrée.")


if __name__ == "__main__":
    main()
