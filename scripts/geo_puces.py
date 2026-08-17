#!/usr/bin/env python3
"""Mesure la géométrie réelle des puces de suggestion sur une capture d'écran.

Repère les pixels aux couleurs des puces (`#2E9E5B` kréyòl, `#3B6FC4` français,
cf. `KeyboardColors` dans `BilingualSuggestion.kt`), reconstitue par remplissage
la boîte englobante de chaque puce, et affiche pour chaque rangée la taille des
puces, l'écart horizontal entre elles et l'écart vertical entre les rangées, en
dp et en millimètres.

Pourquoi un script plutôt qu'une lecture du code : la fenêtre de l'IME n'apparaît
pas dans `uiautomator dump`, et les valeurs du code ne suffisent pas à prédire ce
qui s'affiche. Le style `Button` de la plateforme impose par exemple un plancher
de 88 dp à la largeur des puces, ce qu'aucune ligne du projet ne dit. C'est cette
mesure qui a montré que le défaut d'accessibilité était l'écart *vertical* entre
la rangée kréyòl et la rangée française (0,58 mm avant la 10.11.2), et non la
taille des puces comme supposé au départ. Cf. `ACCESSIBILITE.md`, point 1.

    adb exec-out screencap -p > capture.png
    python3 scripts/geo_puces.py capture.png [densite_dpi]

La densité est lue via `adb shell wm density` si elle n'est pas passée en
argument, et sert à convertir les pixels en dp puis en millimètres.
"""

import subprocess
import sys
from PIL import Image

KREYOL = (0x2E, 0x9E, 0x5B)
FRENCH = (0x3B, 0x6F, 0xC4)
TOLERANCE = 26  # les bords des puces sont adoucis par l'anticrénelage
MM_PAR_DP = 0.15875  # 1 dp = 1/160 pouce
MIN_COTE_PX = 30  # écarte le texte et les artefacts, garde les puces


def teinte(pixel):
    """Rend 'KR', 'FR' ou None selon la couleur de fond de puce reconnue."""
    for nom, reference in (("KR", KREYOL), ("FR", FRENCH)):
        if all(abs(pixel[i] - reference[i]) <= TOLERANCE for i in range(3)):
            return nom
    return None


def densite(argv):
    if len(argv) > 2:
        return int(argv[2])
    sortie = subprocess.run(
        ["adb", "shell", "wm", "density"], capture_output=True, text=True
    ).stdout
    for jeton in sortie.replace("\n", " ").split():
        if jeton.isdigit():
            return int(jeton)
    raise SystemExit("Densité introuvable : passez-la en second argument.")


def boites(image):
    """Boîtes englobantes des zones de couleur de puce, par remplissage."""
    largeur, hauteur = image.size
    pixels = image.load()
    vu = [[False] * largeur for _ in range(hauteur)]
    trouvees = []
    for y in range(hauteur):
        for x in range(largeur):
            if vu[y][x] or teinte(pixels[x, y]) is None:
                continue
            nom = teinte(pixels[x, y])
            pile = [(x, y)]
            vu[y][x] = True
            x0 = x1 = x
            y0 = y1 = y
            while pile:
                cx, cy = pile.pop()
                x0, x1 = min(x0, cx), max(x1, cx)
                y0, y1 = min(y0, cy), max(y1, cy)
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = cx + dx, cy + dy
                    if (0 <= nx < largeur and 0 <= ny < hauteur
                            and not vu[ny][nx]
                            and teinte(pixels[nx, ny]) is not None):
                        vu[ny][nx] = True
                        pile.append((nx, ny))
            if (x1 - x0) > MIN_COTE_PX and (y1 - y0) > MIN_COTE_PX:
                trouvees.append((nom, x0, y0, x1, y1))
    return trouvees


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    dpi = densite(sys.argv)
    ppd = dpi / 160.0
    image = Image.open(sys.argv[1]).convert("RGB")
    puces = boites(image)
    if not puces:
        print("Aucune puce de suggestion détectée sur cette capture.")
        return 1

    puces.sort(key=lambda b: (b[2], b[1]))
    rangees = {}
    for puce in puces:
        rangees.setdefault(round(puce[2] / 20), []).append(puce)

    print(f"{image.size[0]}x{image.size[1]}, {dpi} dpi ({ppd:.2f} px/dp), "
          f"{len(puces)} puces\n")
    bas_precedent = None
    for cle in sorted(rangees):
        rangee = rangees[cle]
        print(f"Rangée {rangee[0][0]} :")
        for _, x0, y0, x1, y1 in rangee:
            w, h = x1 - x0 + 1, y1 - y0 + 1
            print(f"  x {x0:4d}..{x1:4d}  y {y0}..{y1}   "
                  f"{w/ppd:5.1f} x {h/ppd:4.1f} dp   "
                  f"({w/ppd*MM_PAR_DP:.1f} x {h/ppd*MM_PAR_DP:.1f} mm)")
        for i in range(len(rangee) - 1):
            ecart = rangee[i + 1][1] - rangee[i][3] - 1
            print(f"  écart horizontal {i+1}-{i+2} : {ecart} px = "
                  f"{ecart/ppd:.1f} dp ({ecart/ppd*MM_PAR_DP:.2f} mm)")
        if bas_precedent is not None:
            ecart = rangee[0][2] - bas_precedent - 1
            print(f"  écart VERTICAL avec la rangée précédente : {ecart} px = "
                  f"{ecart/ppd:.1f} dp ({ecart/ppd*MM_PAR_DP:.2f} mm)")
        bas_precedent = max(b[4] for b in rangee)
        print()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
