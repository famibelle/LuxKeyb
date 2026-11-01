#!/usr/bin/env python3
"""Modèle économique LuxKeyb : marges par canal, seuils, coût d'une collection.

Les hypothèses sont en tête de fichier, chacune avec sa source et son statut.
À relancer après chaque devis, puis recopier le résultat dans
MODELE-ECONOMIQUE.md avec la date :

    python3 .claude/skills/business-modele/scripts/modele.py
    python3 .claude/skills/business-modele/scripts/modele.py --prix pochette=1.5 boite=8
    python3 .claude/skills/business-modele/scripts/modele.py --collection

Aucune dépendance. Les montants sont en euros, TVA comprise tant que la
franchise s'applique (pas de TVA facturée, rien à reverser).
"""
import argparse
import random
import statistics

# --- Hypothèses --------------------------------------------------------------
# (bas, haut, source, statut). Statut : estimé | hypothèse | devis | confirmé.

FRAIS_FIXES = {
    "marque BOIP (10 ans)": (275, 275, "imprimeur § 7", "estimé"),
    "prototypes": (60, 60, "imprimeur § 7", "estimé"),
    "maquette de la pochette": (50, 50, "imprimeur § 7", "estimé"),
    "essais EN 71": (300, 800, "BOUTIQUE § 8, « quelques centaines d'euros »", "hypothèse"),
}

# Prix cibles : ce qu'un parent paie sans y réfléchir (§ 4 de la skill).
# Coûts unitaires : imprimeur § 4 et § 7 (qualité exigée comprise).
PRODUITS = {
    "pochette": dict(
        libelle="pochette de 6 cartes", prix=2.0,
        couts={"impression à la demande": (1.25, 1.40), "offset": (0.60, 0.80)},
        tirages={"impression à la demande": 500, "offset": 1000},
        source="imprimeur § 7", statut="estimé"),
    "boite": dict(
        libelle="boîte de 54 cartes", prix=10.0,
        couts={"impression à la demande": (6.0, 10.0), "offset": (3.0, 4.5)},
        tirages={"impression à la demande": 100, "offset": 500},
        source="imprimeur § 4 et § 7", statut="estimé"),
    "coffret": dict(
        libelle="coffret complet (248 cartes)", prix=25.0,
        couts={"impression à la demande": (25.0, 40.0), "offset": (8.0, 15.0)},
        tirages={"impression à la demande": 20, "offset": 300},
        source="imprimeur § 4", statut="estimé"),
}

# Commission du revendeur et frais de paiement, en part du prix et en fixe.
CANAUX = {
    "vente directe (stand, école)": dict(commission=(0.0, 0.0), pct=0.0, fixe=0.0),
    "librairie, boutique": dict(commission=(0.30, 0.40), pct=0.0, fixe=0.0),
    "en ligne (Stripe), port à part": dict(commission=(0.0, 0.0), pct=0.015, fixe=0.25),
}
# Le port n'est pas compté : il est payé à part par le client. Un produit dont
# le port dépasse la moitié du prix ne se vend pas seul en ligne (§ 6).

# Pochettes : composition retenue (imprimeur § 7) et raretés imprimées.
POCHETTE = dict(C=3, PC=2, slot_rare=1, p_tres_rare=1 / 6, p_ultra=1 / 75)
EDITIONS = {
    "collection complète (248)": dict(C=140, PC=70, R=30, TR=7, UR=1),
    "édition de 54 (30/16/6/2)": dict(C=30, PC=16, R=6, TR=2, UR=0),
}

# --- Calculs ------------------------------------------------------------------


def marge(prix, cout, canal):
    """Marge unitaire (bas, haut) : le pire coût avec la pire commission."""
    c = CANAUX[canal]
    net_bas = prix * (1 - c["commission"][1]) - prix * c["pct"] - c["fixe"]
    net_haut = prix * (1 - c["commission"][0]) - prix * c["pct"] - c["fixe"]
    return net_bas - cout[1], net_haut - cout[0]


def prix_plancher(cout, canal):
    """Prix sous lequel le pire cas perd de l'argent dans ce canal."""
    c = CANAUX[canal]
    return (cout[1] + c["fixe"]) / (1 - c["commission"][1] - c["pct"])


def total_fixes():
    return (sum(v[0] for v in FRAIS_FIXES.values()),
            sum(v[1] for v in FRAIS_FIXES.values()))


def rapport(prix_force):
    fixes = total_fixes()
    print("Hypothèses à vérifier :")
    for nom, (b, h, src, st) in FRAIS_FIXES.items():
        print(f"  {nom:28} {b:>5}–{h:<5} € · {st} · {src}")
    print(f"  {'frais fixes de lancement':28} {fixes[0]:>5}–{fixes[1]:<5} €\n")

    for cle, p in PRODUITS.items():
        prix = prix_force.get(cle, p["prix"])
        print(f"== {p['libelle']} à {prix:.2f} € ({p['statut']}, {p['source']})")
        for mode, cout in p["couts"].items():
            n = p["tirages"][mode]
            print(f"  {mode} : coût {cout[0]:.2f}–{cout[1]:.2f} €, tirage {n}")
            for canal in CANAUX:
                mb, mh = marge(prix, cout, canal)
                plancher = prix_plancher(cout, canal)
                alerte = "  ← perte" if mb < 0 else ""
                print(f"    {canal:32} marge {mb:+6.2f} à {mh:+6.2f} €"
                      f" · plancher {plancher:5.2f} €{alerte}")
            # Vente directe : combien d'unités pour couvrir frais fixes + tirage.
            if mode == "offset":
                avance = (n * cout[0], n * cout[1])
                seuil = ((fixes[0] + avance[0]) / prix, (fixes[1] + avance[1]) / prix)
                print(f"    avance du tirage {avance[0]:.0f}–{avance[1]:.0f} € ;"
                      f" équilibre en vente directe à {seuil[0]:.0f}–{seuil[1]:.0f}"
                      f" unités ({seuil[0] / n:.0%}–{seuil[1] / n:.0%} du tirage)")
            else:
                mb, mh = marge(prix, cout, "vente directe (stand, école)")
                if mh <= 0:
                    print("    jamais à l'équilibre à ce prix")
                else:
                    lo = fixes[0] / mh
                    hi = fixes[1] / mb if mb > 0 else float("inf")
                    print(f"    équilibre en vente directe à {lo:.0f}–{hi:.0f} unités")
        print()


def pochettes_pour_completer(edition, collectionneurs, rng):
    """Pochettes ouvertes, en tout, pour que chacun ait toute l'édition."""
    besoin = {r: [collectionneurs] * n for r, n in edition.items() if n}
    reste = sum(collectionneurs * n for n in edition.values())
    ouvertes = 0

    def tirer(r):
        nonlocal reste
        if r not in besoin:
            return
        i = rng.randrange(len(besoin[r]))
        if besoin[r][i] > 0:
            besoin[r][i] -= 1
            reste -= 1

    while reste > 0:
        ouvertes += 1
        for _ in range(POCHETTE["C"]):
            tirer("C")
        for _ in range(POCHETTE["PC"]):
            tirer("PC")
        x = rng.random()
        if x < POCHETTE["p_ultra"] and edition.get("UR"):
            tirer("UR")
        elif x < POCHETTE["p_ultra"] + POCHETTE["p_tres_rare"]:
            tirer("TR")
        else:
            tirer("R")
    return ouvertes


def collection(prix_force, essais=1500):
    prix = prix_force.get("pochette", PRODUITS["pochette"]["prix"])
    prix_coffret = prix_force.get("coffret", PRODUITS["coffret"]["prix"])
    rng = random.Random(2026)
    print(f"Coût pour compléter avec des pochettes à {prix:.2f} €"
          f" (coffret à {prix_coffret:.2f} €) :")
    for nom, ed in EDITIONS.items():
        for k in (1, 2, 4):
            r = sorted(pochettes_pour_completer(ed, k, rng) / k for _ in range(essais))
            med = statistics.median(r)
            p90 = r[int(0.9 * len(r))]
            qui = "seul" if k == 1 else f"{k} qui échangent"
            print(f"  {nom:28} {qui:16} {med:5.0f} pochettes ({med * prix:6.0f} €)"
                  f", 90 % : {p90:4.0f} ({p90 * prix:5.0f} €) par enfant")
    print()


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--prix", nargs="*", default=[], metavar="PRODUIT=EUROS",
                    help="remplacer un prix cible, ex. pochette=1.5 boite=8")
    ap.add_argument("--collection", action="store_true",
                    help="simuler le coût pour compléter une édition")
    a = ap.parse_args()
    prix_force = {k: float(v) for k, v in (x.split("=") for x in a.prix)}
    rapport(prix_force)
    if a.collection:
        collection(prix_force)


if __name__ == "__main__":
    main()
