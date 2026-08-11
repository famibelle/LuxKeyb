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

SORTIES = [
    ("carte-guadeloupe.svg", "#F5F2EA", "pour fond sombre"),
    ("carte-guadeloupe-sombre.svg", "#0E6E76", "pour fond clair"),
]


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


def projeter(parties):
    """Équirectangulaire, longitudes resserrées par le cosinus de la latitude.

    À l'échelle d'un archipel, cette projection ne se distingue pas d'une
    projection conforme, et elle tient en trois lignes.
    """
    points = [c for poly in parties for anneau in poly for c in anneau]
    lat_moyenne = sum(p[1] for p in points) / len(points)
    k = math.cos(math.radians(lat_moyenne))

    xs = [p[0] * k for p in points]
    ys = [p[1] for p in points]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)

    echelle = min((LARGEUR - 2 * MARGE) / (x1 - x0), (HAUTEUR - 2 * MARGE) / (y1 - y0))
    dx = (LARGEUR - (x1 - x0) * echelle) / 2
    dy = (HAUTEUR - (y1 - y0) * echelle) / 2

    def point(c):
        # L'axe des ordonnées d'un SVG descend, celui des latitudes monte.
        return (round((c[0] * k - x0) * echelle + dx, 2),
                round((y1 - c[1]) * echelle + dy, 2))

    return [[[point(c) for c in anneau] for anneau in poly] for poly in parties]


def chemin(parties) -> str:
    """Un seul attribut d, une sous-forme fermée par terre."""
    morceaux = []
    for poly in parties:
        for anneau in poly:
            debut = anneau[0]
            segments = " ".join(f"L {x} {y}" for x, y in anneau[1:-1])
            morceaux.append(f"M {debut[0]} {debut[1]} {segments} Z")
    return " ".join(morceaux)


def svg(d: str, remplissage: str, usage: str) -> str:
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {LARGEUR} {HAUTEUR}" role="img" aria-label="Carte de la Guadeloupe">
  <title>Guadeloupe</title>
  <!-- Tracé dérivé de Natural Earth 1:10m (domaine public), voir
       scripts/generate_carte_guadeloupe.py. Remplissage {remplissage} : {usage}. -->
  <path fill="{remplissage}"
    d="{d}" />
</svg>
"""


def main() -> int:
    parties = projeter(geometrie())
    d = chemin(parties)
    for nom, remplissage, usage in SORTIES:
        (ASSETS / nom).write_text(svg(d, remplissage, usage), encoding="utf-8")
        print(f"  {nom:32} {remplissage}  {usage}")

    print(f"\n{len(parties)} terres, {len(d)} caractères de tracé.")
    print("Penser à relancer scripts/tag_assets.py : les fichiers neufs sortent sans métadonnées.")

    if "--voir" in sys.argv:
        apercu = Path("/tmp") / "apercu-carte-guadeloupe.png"
        subprocess.run(["convert", "-background", "#0A5259", "-density", "300",
                        str(ASSETS / SORTIES[0][0]), str(apercu)], check=False)
        subprocess.run(["display", str(apercu)], check=False)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
