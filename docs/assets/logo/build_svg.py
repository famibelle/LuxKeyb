#!/usr/bin/env python3
"""Ecrit les trois SVG de la marque produit a partir du trace de l'archipel.

Le trace de la carte n'est pas recopie a la main : il est lu dans
docs/assets/carte-guadeloupe-compacte.svg, la source deja utilisee par le
tract et le triptyque, pour que les deux restent le meme dessin. Si cette
carte est un jour reprise, relancer ce script suffit a mettre le logo a jour.

    python3 docs/assets/logo/build_svg.py     # reecrit les trois SVG
    python3 docs/assets/logo/generate.py      # puis rasterise les PNG

Les couleurs sont fixees ici et nulle part ailleurs : un logo garde ses
couleurs propres, il ne suit pas les palettes des supports.
"""

import re
from pathlib import Path

SORTIE = Path(__file__).resolve().parent
CARTE = SORTIE.parent / "carte-guadeloupe-compacte.svg"

ile = re.search(r' d="([^"]+)"', CARTE.read_text(encoding="utf-8")).group(1)

ENTETE = """<svg width="512" height="512" viewBox="0 0 200 200" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="{alt}">
  <title>Klavyé Kréyòl Karukera · {nom}</title>
  <desc>Visuel de Klavyé Kréyòl Karukera, le clavier kréyòl guadeloupéen publié par Potomitan™.</desc>
"""

# Le K : fut, bras haut, jambe basse. Les deux partent du meme point du fut.
# Coordonnees deja reduites de 16 % autour du centre, pour laisser voir l'ile.
K_COULEUR = """  <rect x="64.72" y="46.24" width="15.96" height="107.52" rx="7.98" fill="{fut}"/>
  <path d="M82.36 100 L131.92 50.44" fill="none" stroke="{haut}" stroke-width="15.96" stroke-linecap="round"/>
  <path d="M82.36 100 L131.92 149.56" fill="none" stroke="{bas}" stroke-width="15.96" stroke-linecap="round"/>
"""

ILE = '  <g transform="translate(25,30.3) scale(1.5)"><path fill="{couleur}" fill-opacity="{opacite}" d="{d}"/></g>\n'


def ecrire(nom: str, alt: str, corps: str) -> None:
    svg = ENTETE.format(alt=alt, nom=nom) + corps + "</svg>\n"
    (SORTIE / f"{nom}.svg").write_text(svg, encoding="utf-8")
    print("écrit", nom + ".svg")


# 1. Marque de reference : pastille pleine, degrade mer de la charte.
#    Le degrade reprend l'angle 157° de docs/charte-graphique.html, converti
#    en coordonnees de boite englobante.
reference = (
    """  <defs>
    <linearGradient id="fondMer" x1="0.2445" y1="-0.1026" x2="0.7555" y2="1.1026">
      <stop offset="0" stop-color="#12848E"/>
      <stop offset="0.52" stop-color="#0A5259"/>
      <stop offset="1" stop-color="#053035"/>
    </linearGradient>
  </defs>
  <rect width="200" height="200" fill="url(#fondMer)"/>
"""
    + ILE.format(couleur="#FFFFFF", opacite="0.2", d=ile)
    + K_COULEUR.format(fut="#F5F2EA", haut="#E8B14A", bas="#E0705F")
)
ecrire("klavye-logo", "Logo Klavyé Kréyòl : un K sur la silhouette de la Guadeloupe", reference)

# 2. Version detouree : pas de pastille, pour un fond clair.
clair = ILE.format(couleur="#0E6E76", opacite="0.16", d=ile) + K_COULEUR.format(
    fut="#0E6E76", haut="#C97F1E", bas="#C94A3B"
)
ecrire("klavye-logo-clair", "Logo Klavyé Kréyòl détouré, pour fond clair", clair)

# 3. Version une couleur : tampon, sérigraphie, gravure. Changer les deux
#    occurrences de #1C2624 suffit a la passer dans une autre encre.
mono = ILE.format(couleur="#1C2624", opacite="0.18", d=ile) + K_COULEUR.format(
    fut="#1C2624", haut="#1C2624", bas="#1C2624"
)
ecrire("klavye-logo-mono", "Logo Klavyé Kréyòl en une seule couleur", mono)
