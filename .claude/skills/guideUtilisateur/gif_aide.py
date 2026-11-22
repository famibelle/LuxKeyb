#!/usr/bin/env python3
"""Assemble un GIF de la visite ou de l'aide pour la page nouveautés.

Chaque image peut recevoir le filigrane « LuxKeyb » sur des cartes, en
boîtes données à la main : le voile sombre de la visite empêche la détection
automatique de docs/scripts/filigrane_cartes.py. La bulle blanche est
préservée (elle passe par-dessus le filigrane).

    python gif_aide.py sortie.gif LARGEUR img1.png[:x0,y0,x1,y1;...] img2.png ...

Exemple (captures du A21s, 720 x 1600) :
    python gif_aide.py docs/Screenshots/lux_aide_visite.gif 432 \\
        rappel.png menu.png essai.png carnet.png:29,372,348,696 partage.png:29,269,672,1416

À lancer depuis la racine du dépôt. Chaque image dure 3,5 s, la dernière 5 s.
"""
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

sys.path.insert(0, "docs/scripts")
from filigrane_cartes import filigrane  # noqa: E402


def bulle(im):
    px = im.load()
    w, h = im.size
    lignes = [y for y in range(0, h, 2)
              if sum(1 for x in range(0, w, 4) if px[x, y][:3] == (255, 255, 255)) > 100]
    return (0, min(lignes) - 4, w, max(lignes) + 6) if lignes else None


def marquer(im, boites):
    base, m = im.copy(), im.copy()
    for b in boites:
        filigrane(m, b)
    bb = bulle(base)
    if bb:
        m.paste(base.crop(bb), bb[:2])
    return m


sortie, largeur, args = sys.argv[1], int(sys.argv[2]), sys.argv[3:]
tmp = Path(tempfile.mkdtemp())
liste = []
for i, a in enumerate(args):
    chemin, _, boites = a.partition(":")
    im = Image.open(chemin).convert("RGBA")
    if boites:
        im = marquer(im, [tuple(int(v) for v in b.split(",")) for b in boites.split(";")])
    p = tmp / f"{i:02d}.png"
    im.convert("RGB").resize((largeur, round(im.height * largeur / im.width)), Image.LANCZOS).save(p)
    liste.append(f"file '{p}'\nduration {5 if i == len(args) - 1 else 3.5}")
liste.append(f"file '{tmp / f'{len(args) - 1:02d}.png'}'")
(tmp / "l.txt").write_text("\n".join(liste))
subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", str(tmp / "l.txt"),
                "-vf", "split[a][b];[a]palettegen=stats_mode=full[p];[b][p]paletteuse=dither=none:diff_mode=rectangle",
                "-loop", "0", sortie], check=True)
shutil.rmtree(tmp)
print(sortie)
