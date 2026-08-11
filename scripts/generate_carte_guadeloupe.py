#!/usr/bin/env python3
"""Redessine la silhouette de la Guadeloupe à partir de données publiques.

La silhouette qui servait jusqu'ici était dessinée à la main : un papillon
approximatif, sans Marie-Galante, sans les Saintes, sans la Désirade. Celle-ci
vient de Natural Earth (échelle 1:10 000 000), placé dans le **domaine public**
par ses auteurs : aucune attribution n'est exigée, aucune licence à racheter,
et le tracé peut être redistribué dans le dépôt comme dans les supports
imprimés.

    python3 scripts/generate_carte_guadeloupe.py          # régénère les deux SVG
    python3 scripts/generate_carte_guadeloupe.py --voir    # ouvre un aperçu PNG

Deux fichiers en sortent, qui ne diffèrent que par leur remplissage, parce que
la charte pose la carte sur les deux sortes de fond :

    docs/assets/carte-guadeloupe.svg          #F5F2EA, pour fond sombre
    docs/assets/carte-guadeloupe-sombre.svg   #0E6E76, pour fond clair

Le viewBox reste 220x175, celui du tracé précédent : la carte est centrée
dedans. Une autre proportion aurait décalé la mise en page partout où l'image
est posée, jusque dans la ligne de crédits du tract où elle est calée sur la
hauteur du texte.
"""

import json
import math
import subprocess
import sys
import urllib.request
from pathlib import Path

RACINE = Path(__file__).resolve().parents[1]
ASSETS = RACINE / "docs" / "assets"
CACHE = Path("/tmp") / "ne_10m_admin_0_map_subunits.geojson"

SOURCE = ("https://raw.githubusercontent.com/nvkelso/natural-earth-vector/"
          "master/geojson/ne_10m_admin_0_map_subunits.geojson")

LARGEUR, HAUTEUR, MARGE = 220, 175, 2

# Les deux remplissages de la charte : la carte se pose sur les deux sortes de
# fond, et un SVG appelé en <img> ne peut pas hériter de la couleur du texte.
TEINTES = [("", "#F5F2EA", "pour fond sombre"), ("-sombre", "#0E6E76", "pour fond clair")]

# Sous 4 mm de haut, dans une ligne de crédits, l'archipel entier ne se lit
# plus : Marie-Galante, les Saintes et la Désirade deviennent des poussières,
# et les deux îles principales, réduites pour leur laisser la place, une tache.
# La variante compacte ne garde que Basse-Terre et Grande-Terre, cadrées au
# plus près, ce qui laisse toute la hauteur disponible au papillon.
COMPACTE_LARGEUR, COMPACTE_MARGE, COMPACTE_TERRES = 100, 1, 2


def geometrie():
    """Les six terres de l'archipel, en degrés."""
    if not CACHE.exists():
        print(f"Téléchargement de Natural Earth ({SOURCE.rsplit('/', 1)[-1]})…")
        urllib.request.urlretrieve(SOURCE, CACHE)
    donnees = json.loads(CACHE.read_text(encoding="utf-8"))
    for f in donnees["features"]:
        if f["properties"].get("SUBUNIT") == "Guadeloupe":
            return f["geometry"]["coordinates"]
    sys.exit("Guadeloupe introuvable dans le jeu de données : le champ SUBUNIT a-t-il changé ?")


def projeter(parties, largeur, hauteur, marge):
    """Équirectangulaire, longitudes resserrées par le cosinus de la latitude.

    À l'échelle d'un archipel, cette projection ne se distingue pas d'une
    projection conforme, et elle tient en trois lignes. Le tracé est ensuite
    ajusté au viewBox demandé, et centré dedans.
    """
    points = [c for poly in parties for anneau in poly for c in anneau]
    lat_moyenne = sum(p[1] for p in points) / len(points)
    k = math.cos(math.radians(lat_moyenne))

    xs = [p[0] * k for p in points]
    ys = [p[1] for p in points]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)

    echelle = min((largeur - 2 * marge) / (x1 - x0), (hauteur - 2 * marge) / (y1 - y0))
    dx = (largeur - (x1 - x0) * echelle) / 2
    dy = (hauteur - (y1 - y0) * echelle) / 2

    def point(c):
        # L'axe des ordonnées d'un SVG descend, celui des latitudes monte.
        return (round((c[0] * k - x0) * echelle + dx, 2),
                round((y1 - c[1]) * echelle + dy, 2))

    return [[[point(c) for c in anneau] for anneau in poly] for poly in parties]


def etendue(poly) -> float:
    """Aire du rectangle englobant d'une terre, de quoi classer les six."""
    anneau = poly[0]
    xs = [c[0] for c in anneau]
    ys = [c[1] for c in anneau]
    return (max(xs) - min(xs)) * (max(ys) - min(ys))


def chemin(parties) -> str:
    """Un seul attribut d, une sous-forme fermée par terre."""
    morceaux = []
    for poly in parties:
        for anneau in poly:
            debut = anneau[0]
            segments = " ".join(f"L {x} {y}" for x, y in anneau[1:-1])
            morceaux.append(f"M {debut[0]} {debut[1]} {segments} Z")
    return " ".join(morceaux)


def svg(d: str, largeur: int, hauteur: int, remplissage: str, usage: str, note: str) -> str:
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {largeur} {hauteur}" role="img" aria-label="Carte de la Guadeloupe">
  <title>Guadeloupe</title>
  <!-- Tracé dérivé de Natural Earth 1:10m (domaine public), voir
       scripts/generate_carte_guadeloupe.py. Remplissage {remplissage} : {usage}.
       {note} -->
  <path fill="{remplissage}"
    d="{d}" />
</svg>
"""


def ecrire(base: str, parties, largeur: int, hauteur: int, note: str):
    d = chemin(parties)
    for suffixe, remplissage, usage in TEINTES:
        nom = f"{base}{suffixe}.svg"
        (ASSETS / nom).write_text(
            svg(d, largeur, hauteur, remplissage, usage, note), encoding="utf-8")
        print(f"  {nom:34} {remplissage}  {usage}")
    return d


def main() -> int:
    brut = geometrie()

    complete = projeter(brut, LARGEUR, HAUTEUR, MARGE)
    d = ecrire("carte-guadeloupe", complete, LARGEUR, HAUTEUR,
               f"Archipel complet, {len(brut)} terres.")

    # Les deux plus grandes terres, cadrées au plus près : leur proportion
    # décide de la hauteur du viewBox, pour qu'il ne reste aucun vide autour.
    principales = sorted(brut, key=etendue, reverse=True)[:COMPACTE_TERRES]
    points = [c for poly in principales for anneau in poly for c in anneau]
    k = math.cos(math.radians(sum(p[1] for p in points) / len(points)))
    largeur_deg = (max(p[0] for p in points) - min(p[0] for p in points)) * k
    hauteur_deg = max(p[1] for p in points) - min(p[1] for p in points)
    compacte_hauteur = round(COMPACTE_LARGEUR * hauteur_deg / largeur_deg)

    compacte = projeter(principales, COMPACTE_LARGEUR, compacte_hauteur, COMPACTE_MARGE)
    ecrire("carte-guadeloupe-compacte", compacte, COMPACTE_LARGEUR, compacte_hauteur,
           "Variante des petites tailles : Basse-Terre et Grande-Terre seules.")

    print(f"\n{len(brut)} terres, {len(d)} caractères de tracé.")
    print("Penser à relancer scripts/tag_assets.py : les fichiers neufs sortent sans métadonnées.")

    if "--voir" in sys.argv:
        apercu = Path("/tmp") / "apercu-carte-guadeloupe.png"
        subprocess.run(["convert", "-background", "#0A5259", "-density", "300",
                        str(ASSETS / "carte-guadeloupe.svg"), str(apercu)], check=False)
        subprocess.run(["display", str(apercu)], check=False)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
