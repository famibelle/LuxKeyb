#!/usr/bin/env python3
"""Rasterise la marque produit Klavye Kreyol en PNG, a ses tailles d'usage.

Les SVG de ce dossier sont la source ; ce script n'en produit que des copies
figees, pour les endroits qui n'acceptent pas de vectoriel : l'avatar d'un
groupe WhatsApp, une icone de site, un fichier envoye par message.

Chaque PNG est rendu par Chrome a sa taille exacte, sans redimensionnement
apres coup, parce qu'un logo reduit apres rendu perd la nettete de ses bords.
Les versions detouree et monochrome sortent sur fond transparent.

    python3 docs/assets/logo/generate.py           # tout exporter (demande Pillow)
    python3 docs/assets/logo/generate.py 640       # seulement les noms qui matchent

Comme docs/assets/ads/generate.py, le script appelle scripts/tag_assets.py en
fin de course : Chrome ecrit des PNG nus, sans auteur ni copyright.
"""

import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ICI = Path(__file__).resolve().parent
RACINE = ICI.parents[2]

# (source, taille, transparent). L'avatar WhatsApp est en 640 : l'application
# recompresse tout ce qui depasse, et reaffiche entre 48 et 96 px.
SORTIES = [
    ("klavye-logo.svg", 1024, False),
    ("klavye-logo.svg", 640, False),
    ("klavye-logo.svg", 512, False),
    ("klavye-logo.svg", 192, False),
    ("klavye-logo.svg", 48, False),
    ("klavye-logo.svg", 32, False),
    ("klavye-logo-clair.svg", 1024, True),
    ("klavye-logo-clair.svg", 512, True),
    ("klavye-logo-mono.svg", 1024, True),
]

# Les pages a rendre telles quelles, en un seul format.
PAGES = [("invitation.html", 1080, 1080)]


def chrome() -> str:
    for nom in ("google-chrome", "google-chrome-stable", "chromium", "chromium-browser"):
        chemin = shutil.which(nom)
        if chemin:
            return chemin
    sys.exit("Chrome introuvable : installer google-chrome ou chromium.")


# Chrome retranche la hauteur du cadre de fenetre a ce que demande
# --window-size : une fenetre de 512 rend une vue de 425 px et le bas du visuel
# est coupe. On rend donc plus haut que necessaire, puis on recadre. La marge
# est large parce que la difference depend de l'environnement graphique.
MARGE_FENETRE = 220


def rendre(navigateur: str, url: str, sortie: Path, largeur: int, hauteur: int, transparent: bool):
    with tempfile.TemporaryDirectory() as profil:
        commande = [
            navigateur, "--headless", "--disable-gpu", "--no-sandbox",
            f"--user-data-dir={profil}",
            f"--window-size={largeur},{hauteur + MARGE_FENETRE}",
            "--hide-scrollbars",
            "--virtual-time-budget=4000",
            f"--screenshot={sortie}",
        ]
        if transparent:
            # ARGB : un alpha nul laisse passer le fond de la page.
            commande.append("--default-background-color=00000000")
        commande.append(url)
        subprocess.run(commande, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        recadrer(sortie, largeur, hauteur)


def recadrer(fichier: Path, largeur: int, hauteur: int):
    from PIL import Image

    with Image.open(fichier) as image:
        if image.size == (largeur, hauteur):
            return
        if image.size[1] < hauteur:
            sys.exit(
                f"{fichier.name} : la vue rendue fait {image.size[1]} px de haut, "
                f"moins que les {hauteur} attendus. Augmenter MARGE_FENETRE."
            )
        image.crop((0, 0, largeur, hauteur)).save(fichier)


def page_pour_svg(svg: Path, taille: int) -> str:
    """Un SVG rendu directement par Chrome garde ses attributs width/height.

    On le pose donc dans une page minimale qui impose la taille voulue, sinon
    tous les exports sortiraient a 512.
    """
    html = (
        "<!doctype html><meta charset='utf-8'>"
        "<style>html,body{margin:0;padding:0;background:transparent;}"
        f"img{{display:block;width:{taille}px;height:{taille}px;}}</style>"
        f"<img src='{svg.name}'>"
    )
    fichier = svg.parent / f".rendu-{taille}.html"
    fichier.write_text(html, encoding="utf-8")
    return fichier.as_uri(), fichier


def main() -> int:
    filtres = [a for a in sys.argv[1:] if not a.startswith("-")]
    navigateur = chrome()
    faits = []

    for source, taille, transparent in SORTIES:
        nom = f"{Path(source).stem}-{taille}.png"
        if filtres and not any(f in nom for f in filtres):
            continue
        url, temporaire = page_pour_svg(ICI / source, taille)
        sortie = ICI / nom
        try:
            rendre(navigateur, url, sortie, taille, taille, transparent)
        finally:
            temporaire.unlink(missing_ok=True)
        print(f"  {nom}  ({taille}×{taille})")
        faits.append(sortie)

    for page, largeur, hauteur in PAGES:
        nom = f"{Path(page).stem}.png"
        if filtres and not any(f in nom for f in filtres):
            continue
        sortie = ICI / nom
        rendre(navigateur, (ICI / page).as_uri(), sortie, largeur, hauteur, False)
        print(f"  {nom}  ({largeur}×{hauteur})")
        faits.append(sortie)

    if not faits:
        print("Rien a exporter : aucun nom ne correspond au filtre.")
        return 1

    # Chrome ecrit des PNG nus : sans ce passage, les visuels circulent sans
    # auteur ni copyright.
    subprocess.run(
        [sys.executable, str(RACINE / "scripts/tag_assets.py"), str(ICI)],
        check=False,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
