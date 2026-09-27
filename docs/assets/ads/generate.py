#!/usr/bin/env python3
"""Exporte les visuels publicitaires de docs/publicites.html en PNG.

Chaque visuel est rendu seul, a sa taille de diffusion exacte, par Chrome en
mode headless. La liste des formats est lue directement dans la page pour
qu'il n'y ait qu'une seule source de verite : ajouter une entree au tableau
SPECS suffit, ce script la reprend.

    python3 docs/assets/ads/generate.py            # tout exporter
    python3 docs/assets/ads/generate.py tiktok     # seulement les ids qui matchent
    python3 docs/assets/ads/generate.py --voir     # suivre le rendu dans une fenetre
"""

import re
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

DOCS = Path(__file__).resolve().parents[2]
PAGE = DOCS / "publicites.html"
OUT = DOCS / "assets" / "ads" / "export"

SPEC_RE = re.compile(r"\{id:'([\w-]+)',\s*w:\s*(\d+),\s*h:\s*(\d+)")
MIN_WINDOW = 900
MARGIN = 300


def chrome() -> str:
    for name in ("google-chrome", "google-chrome-stable", "chromium", "chromium-browser"):
        path = shutil.which(name)
        if path:
            return path
    sys.exit("Chrome introuvable : installer google-chrome ou chromium.")


def specs():
    found = SPEC_RE.findall(PAGE.read_text(encoding="utf-8"))
    if not found:
        sys.exit(f"Aucun format trouve dans {PAGE} : le tableau SPECS a-t-il change de forme ?")
    return [(i, int(w), int(h)) for i, w, h in found]


def open_viewer(preview: Path):
    """Ouvre une fenetre qui se rafraichit a chaque nouveau visuel.

    `display -update` relit le fichier des qu'il change : une seule fenetre
    suffit donc a suivre toute la serie.
    """
    if not shutil.which("display"):
        print("(--voir ignore : ImageMagick 'display' est introuvable)")
        return None
    preview.parent.mkdir(parents=True, exist_ok=True)
    if not preview.exists():
        subprocess.run(["convert", "-size", "900x700", "xc:#F5F2EA", str(preview)], check=False)
    return subprocess.Popen(
        ["display", "-update", "1", "-title", "Visuels Letzebuergesch Clavier", str(preview)],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        # Session detachee : la fenetre reste ouverte apres la fin du script.
        start_new_session=True,
    )


def main() -> int:
    binary = chrome()
    args = sys.argv[1:]
    watch = "--voir" in args
    keep = [a for a in args if not a.startswith("-")]
    OUT.mkdir(parents=True, exist_ok=True)
    todo = [s for s in specs() if not keep or any(k in s[0] for k in keep)]
    if not todo:
        sys.exit(f"Aucun format ne correspond a {keep}.")

    # Hors du depot : c'est un artefact d'atelier, pas un livrable.
    preview = Path(tempfile.gettempdir()) / "apercu-visuels-luxkeyb.png"
    viewer = open_viewer(preview) if watch else None
    failures = []
    for ad_id, w, h in todo:
        target = OUT / f"{ad_id}.png"
        # --window-size decrit la fenetre, pas le viewport : Chrome en retire la
        # hauteur de son propre habillage, et sous une certaine taille il rend la
        # page en grand avant de redimensionner la capture. Dans les deux cas le
        # visuel ressort tronque ou ecrase. On capture donc toujours plus large que
        # demande, et on recadre au pixel.
        shot_w, shot_h = max(w, MIN_WINDOW), max(h, MIN_WINDOW) + MARGIN
        with tempfile.TemporaryDirectory() as profile:
            subprocess.run(
                [
                    binary, "--headless", "--no-sandbox", "--disable-gpu",
                    "--hide-scrollbars", "--force-device-scale-factor=1",
                    "--allow-file-access-from-files",
                    f"--user-data-dir={profile}",
                    f"--window-size={shot_w},{shot_h}",
                    "--virtual-time-budget=5000",
                    f"--screenshot={target}",
                    f"{PAGE.as_uri()}?render={ad_id}",
                ],
                check=False,
                capture_output=True,
            )
        if target.exists():
            subprocess.run(
                ["convert", str(target), "-crop", f"{w}x{h}+0+0", "+repage", str(target)],
                check=True,
            )
        if target.exists():
            print(f"  {ad_id:<22} {w:>5} x {h:<5} {target.stat().st_size // 1024:>5} Ko", flush=True)
            if viewer:
                # Le visuel est mis a la taille de la fenetre, legende, puis ecrit
                # d'un coup (fichier temporaire + remplacement) pour que le
                # visualiseur ne relise jamais une image a moitie ecrite.
                tmp = preview.with_suffix(".tmp.png")
                subprocess.run(
                    ["convert", str(target), "-resize", "820x760>",
                     "-bordercolor", "#F5F2EA", "-border", "24",
                     "-background", "#F5F2EA", "-fill", "#1C2624", "-pointsize", "20",
                     "-gravity", "south", "label:" + f"{ad_id}  ·  {w} x {h}",
                     "-append", str(tmp)],
                    check=False,
                )
                if tmp.exists():
                    tmp.replace(preview)
                time.sleep(1.5)  # le temps de regarder passer le visuel
        else:
            failures.append(ad_id)
            print(f"  {ad_id:<22} ECHEC")

    print(f"\n{len(todo) - len(failures)}/{len(todo)} visuels exportes dans {OUT}", flush=True)

    # Chrome sort des PNG nus : sans cet appel, les visuels partiraient chez les
    # regies sans nom d'auteur ni copyright, et rien ne dirait plus d'ou ils
    # viennent une fois telecharges d'une page.
    tagueur = DOCS.parent / "scripts" / "tag_assets.py"
    subprocess.run([sys.executable, str(tagueur), str(OUT)], check=False)

    if viewer:
        print("Fenetre d'apercu laissee ouverte (la fermer a la main).", flush=True)
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
