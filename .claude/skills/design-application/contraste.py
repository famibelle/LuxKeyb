#!/usr/bin/env python3
"""Rapport de contraste WCAG 2.1 entre une encre et un fond.

Le calcul de la charte (docs/charte-graphique.html) et des commentaires de
KeyboardTheme.kt, pour vérifier une couleur avant de la poser plutôt
qu'après une capture :

    python3 contraste.py "#FFFFFF" "#ED2939"           # une paire
    python3 contraste.py "#99333333" "#FFFFFF"         # encre translucide
    python3 contraste.py --palette                     # palettes de l'appli

Une encre en #AARRGGBB (la notation Android) est d'abord composée sur son
fond, comme le fait l'écran. Seuils : 4,5:1 pour le texte courant, 3:1 pour
le texte large (24 px, ou 18,7 px en gras) et pour les éléments non textuels
(contour d'une touche, pastille, icône).
"""
import argparse
import sys


def rvb(hexa):
    h = hexa.lstrip("#")
    if len(h) == 6:
        return 255, tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
    if len(h) == 8:
        return int(h[:2], 16), tuple(int(h[i:i + 2], 16) for i in (2, 4, 6))
    raise ValueError(f"couleur illisible : {hexa}")


def composer(encre, fond):
    alpha, e = rvb(encre)
    _, f = rvb(fond)
    a = alpha / 255
    return tuple(round(a * ce + (1 - a) * cf) for ce, cf in zip(e, f))


def luminance(c):
    def lin(v):
        v /= 255
        return v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = (lin(v) for v in c)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def rapport(encre, fond):
    l1 = luminance(composer(encre, fond))
    l2 = luminance(rvb(fond)[1])
    hi, lo = max(l1, l2), min(l1, l2)
    return (hi + 0.05) / (lo + 0.05)


def verdict(r):
    if r >= 4.5:
        return "texte courant"
    if r >= 3:
        return "texte large ou élément graphique seulement"
    return "décor seulement, aucun texte"


def virgule(r):
    return f"{r:.2f}".replace(".", ",")


# Les paires qui portent du texte ou un repère dans l'appli aujourd'hui.
PALETTES = {
    "Clavier (KeyboardTheme.kt)": [
        ("lettre, clair", "#1A1A1A", "#FFFFFF"),
        ("lettre, sombre", "#F0F0F0", "#2B2B2B"),
        ("glyphe blanc sur rouge", "#FFFFFF", "#ED2939"),
        ("glyphe encre sur bleu ciel", "#1A1A1A", "#00A1DE"),
    ],
    "Appli compagnon (SettingsActivity.kt)": [
        ("titre blanc sur bandeau #0080FF", "#FFFFFF", "#0080FF"),
        ("onglet actif orange sur blanc", "#FF8C00", "#FFFFFF"),
        ("onglet inactif gris sur blanc", "#888888", "#FFFFFF"),
        ("texte secondaire #6B6B6B sur #F5F5F5", "#6B6B6B", "#F5F5F5"),
        ("lien #1976D2 sur blanc", "#1976D2", "#FFFFFF"),
        ("blanc sur vert réussite #4CAF50", "#FFFFFF", "#4CAF50"),
        ("blanc sur violet carnet #5E35B1", "#FFFFFF", "#5E35B1"),
        ("lavande #E8E0FF sur violet carnet", "#E8E0FF", "#5E35B1"),
        ("brun #5D4037 sur blanc", "#5D4037", "#FFFFFF"),
        ("blanc sur sarcelle #0E6E76", "#FFFFFF", "#0E6E76"),
    ],
    "Remplaçants proposés (§ 9)": [
        ("orange foncé #B35900 sur blanc", "#B35900", "#FFFFFF"),
        ("blanc sur vert foncé #2E7D32", "#FFFFFF", "#2E7D32"),
        ("blanc sur bleu nuit #003876", "#FFFFFF", "#003876"),
    ],
}


def main():
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("encre", nargs="?")
    p.add_argument("fond", nargs="?")
    p.add_argument("--palette", action="store_true",
                   help="mesurer les paires en usage dans l'appli")
    a = p.parse_args()

    if a.palette:
        for titre, paires in PALETTES.items():
            print(f"\n{titre}")
            for nom, e, f in paires:
                r = rapport(e, f)
                print(f"  {virgule(r):>6}:1  {nom:<42} {verdict(r)}")
        return
    if not (a.encre and a.fond):
        p.print_usage()
        sys.exit(2)
    r = rapport(a.encre, a.fond)
    print(f"{virgule(r)}:1 — {verdict(r)}")


if __name__ == "__main__":
    main()
