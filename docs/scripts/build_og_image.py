#!/usr/bin/env python3
"""Produit les visuels d'aperçu de partage à partir de leur source HTML.

Un aperçu de partage est le premier contact d'un destinataire avec le projet,
quand un lien est transféré par mail ou dans une messagerie. Les visuels
publicitaires existants portent un appel à l'action et un badge de magasin :
c'est le bon registre pour une campagne, pas pour une page qui circule dans
une administration. Ceux-ci sont donc rendus à part.

    python3 docs/scripts/build_og_image.py

Sortie : docs/assets/og/<nom>.png, au format 1200 x 630.
"""

import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

DOCS = Path(__file__).resolve().parents[1]
SOURCE = DOCS / "assets" / "og"

LARGEUR, HAUTEUR = 1200, 630

# Chrome retire la hauteur de son propre habillage de --window-size, et sous
# une certaine taille il rend la page en grand avant de redimensionner la
# capture. On capture donc plus grand, puis on recadre au pixel. Même
# raisonnement que docs/assets/ads/generate.py.
MARGE = 200

CHROMES = ["google-chrome", "google-chrome-stable", "chromium", "chromium-browser"]


def trouver(noms: list[str]) -> str | None:
    for nom in noms:
        chemin = shutil.which(nom)
        if chemin:
            return chemin
    return None


def rendre(chrome: str, page: Path, cible: Path) -> bool:
    with tempfile.TemporaryDirectory() as profil:
        subprocess.run(
            [
                chrome, "--headless", "--no-sandbox", "--disable-gpu",
                "--hide-scrollbars", "--force-device-scale-factor=1",
                "--allow-file-access-from-files",
                f"--user-data-dir={profil}",
                f"--window-size={LARGEUR},{HAUTEUR + MARGE}",
                "--virtual-time-budget=5000",
                f"--screenshot={cible}",
                page.as_uri(),
            ],
            check=False,
            capture_output=True,
        )
    return cible.exists()


def main() -> int:
    chrome = trouver(CHROMES)
    if not chrome:
        print("aucun binaire Chrome ou Chromium trouvé : " + ", ".join(CHROMES))
        return 1
    convert = trouver(["convert", "magick"])
    if not convert:
        print("ImageMagick introuvable : le recadrage au pixel est impossible")
        return 1

    pages = sorted(SOURCE.glob("*.html"))
    if not pages:
        print(f"aucune source dans {SOURCE.relative_to(DOCS.parent)}")
        return 1

    echecs = []
    for page in pages:
        cible = page.with_suffix(".png")
        if not rendre(chrome, page, cible):
            echecs.append(page.name)
            continue
        subprocess.run(
            [convert, str(cible), "-crop", f"{LARGEUR}x{HAUTEUR}+0+0", "+repage", str(cible)],
            check=True,
        )
        print(f"  {cible.relative_to(DOCS.parent)}  {LARGEUR} x {HAUTEUR}  "
              f"{cible.stat().st_size // 1024} Ko")

    # Chrome écrit des PNG nus : la paternité Potomitan™ est réinscrite après
    # coup, comme le fait déjà l'export des visuels publicitaires.
    tagueur = DOCS.parent / "scripts" / "tag_assets.py"
    if tagueur.exists():
        subprocess.run([sys.executable, str(tagueur), str(SOURCE)], check=False)

    if echecs:
        print("échecs : " + ", ".join(echecs))
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
