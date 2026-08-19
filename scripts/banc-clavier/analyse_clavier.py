#!/usr/bin/env python3
"""Contrôle l'affichage des touches sur une capture de clavier.

Repère les rangées puis les touches par segmentation, sans coordonnées écrites
en dur, pour rester valable quelle que soit la géométrie de l'appareil.
"""
import sys, json
from PIL import Image


def charger(chemin, y_ime):
    im = Image.open(chemin).convert("RGB")
    return im.load(), im.size[0], im.size[1], y_ime


def diff(c1, c2):
    return sum(abs(a - b) for a, b in zip(c1, c2))


def rangees(px, w, h, y0):
    """Bandes horizontales occupées par des touches, du haut vers le bas."""
    # Colonne témoin : en paysage une bande noire borde l'écran, et une sonde
    # posée sur le bord y lirait du noir en guise de fond de clavier.
    xr = 3
    while xr < w // 3 and sum(px[xr, (y0 + h) // 2]) / 3 < 60:
        xr += 1
    xr += 2
    fond = px[xr, (y0 + h) // 2]
    # Le clavier s'arrête avant la barre de navigation, dont le fond diffère :
    # sans cette borne, cette barre passe pour une rangée de touches.
    bas = h
    for y in range(h - 1, y0, -1):
        if diff(px[xr, y], fond) < 14:
            bas = y
            break
    h = bas
    densite = []
    for y in range(y0, h):
        n = sum(1 for x in range(0, w, 6) if diff(px[x, y], fond) > 18)
        densite.append((y, n / (w / 6)))
    bandes, debut = [], None
    for y, d in densite:
        if d > 0.35 and debut is None:
            debut = y
        elif d <= 0.35 and debut is not None:
            if y - debut > 20:
                bandes.append((debut, y - 1))
            debut = None
    if debut is not None and h - debut > 20:
        bandes.append((debut, h - 1))
    return fond, bandes


def touches(px, w, fond, bande):
    """Segments horizontaux d'une rangée, à mi-hauteur."""
    y = (bande[0] + bande[1]) // 2
    segs, debut = [], None
    for x in range(w):
        occupe = diff(px[x, y], fond) > 14
        if occupe and debut is None:
            debut = x
        elif not occupe and debut is not None:
            if x - debut > 12:
                segs.append((debut, x - 1))
            debut = None
    if debut is not None and w - debut > 12:
        segs.append((debut, w - 1))
    if not segs:
        return segs
    # Les bordures et les indices de coin produisent des éclats de quelques
    # pixels : on ne garde que ce qui a la taille d'une touche.
    largeurs = sorted(b - a + 1 for a, b in segs)
    mediane = largeurs[len(largeurs) // 2]
    return [(a, b) for a, b in segs if (b - a + 1) >= mediane * 0.45]


def bord_haut(px, fond, x, y0, y1):
    for y in range(y0, y1):
        if diff(px[x, y], fond) > 14:
            return y
    return None


def analyse(chemin, y_ime):
    px, w, h, y0 = charger(chemin, y_ime)
    fond, bandes = rangees(px, w, h, y0)
    res = []
    if len(bandes) < 2:
        return [("rangées", "ECHEC", f"{len(bandes)} rangée(s) détectée(s)")]

    derniere = bandes[-1] if bandes[-1][1] - bandes[-1][0] > 30 else bandes[-2]
    hauteur_rangee = derniere[1] - derniere[0] + 1
    milieu = (derniere[0] + derniere[1]) // 2

    def sature(c):
        return max(c) - min(c) > 45

    # Touches colorées de la rangée du bas (123, ponctuations, espace, emoji,
    # entrée) : les touches blanches se confondent avec le fond du clavier, et
    # ce sont justement les colorées dont la géométrie a régressé par le passé.
    # Trois lignes plutôt qu'une : le libellé blanc d'une touche traverse son
    # centre et couperait la bande en deux si on ne lisait qu'à mi-hauteur.
    lignes = [derniere[0] + hauteur_rangee // 4, milieu,
              derniere[1] - hauteur_rangee // 4]
    colore = [any(sature(px[x, y]) for y in lignes) for x in range(w)]
    segs, debut_seg = [], None
    for x in range(w):
        if colore[x] and debut_seg is None:
            debut_seg = x
        elif not colore[x] and debut_seg is not None:
            if x - debut_seg > hauteur_rangee // 4:
                segs.append((debut_seg, x - 1))
            debut_seg = None
    if debut_seg is not None and w - debut_seg > hauteur_rangee // 4:
        segs.append((debut_seg, w - 1))
    # Le libellé blanc d'une touche colorée coupe sa bande en deux à mi-hauteur
    # (le tiret, par exemple) : on recolle ce que sépare moins d'un interstice.
    fusion = []
    for seg in segs:
        if fusion and seg[0] - fusion[-1][1] < hauteur_rangee // 10:
            fusion[-1] = (fusion[-1][0], seg[1])
        else:
            fusion.append(seg)
    segs = fusion
    res.append(("touches colorées de la rangée du bas",
                "OK" if len(segs) >= 6 else "ECHEC",
                f"{len(segs)} touches colorées sur les 7 attendues"))

    hauts = []
    for a, b in segs:
        x = (a + b) // 2
        for y in range(derniere[0] - 6, derniere[1]):
            if sature(px[x, y]):
                hauts.append(y)
                break
    ecart = (max(hauts) - min(hauts)) if hauts else 999
    res.append(("alignement de la rangée du bas", "OK" if ecart <= 4 else "ECHEC",
                f"écart de {ecart} px entre les bords supérieurs"))

    # emoji : la touche colorée qui contient du jaune
    jaune_max = 0
    for a, b in segs:
        n = sum(1 for y in range(derniere[0], derniere[1], 2)
                for x in range(a, b, 2)
                if px[x, y][0] > 200 and px[x, y][1] > 150 and px[x, y][2] < 130)
        jaune_max = max(jaune_max, n)
    res.append(("touche emoji", "OK" if jaune_max > 40 else "ECHEC",
                f"{jaune_max} points d'emoji dans la meilleure touche"))

    # Libellés qui portent du texte : la touche de mode (première touche
    # colorée, « 123 ») et la barre d'espace (la plus large). Une ellipse « … »
    # ne tiendrait que sur une hauteur ou une largeur dérisoire. Les
    # ponctuations sont écartées : une virgule est petite par nature.
    def blanc(a, b):
        # Le libellé de la barre d'espace est un blanc semi-transparent : on ne
        # cherche donc pas du blanc pur mais ce qui tranche sur le fond de la
        # touche, relevé dans son coin supérieur gauche, à l'écart du texte.
        ref = sum(px[(a + b) // 2, derniere[0] + hauteur_rangee // 8]) / 3
        return [(x, y) for y in range(derniere[0] + 3, derniere[1] - 2)
                for x in range(a + 5, b - 4)
                if sum(px[x, y]) / 3 > ref + 45]

    if segs:
        a, b = segs[0]
        pts = blanc(a, b)
        part = ((max(p[1] for p in pts) - min(p[1] for p in pts) + 1) / hauteur_rangee) if pts else 0
        res.append(("libellé de la touche 123", "OK" if part > 0.18 else "ECHEC",
                    f"occupe {part:.0%} de la hauteur de touche"))

        a, b = max(segs, key=lambda s: s[1] - s[0])
        pts = blanc(a, b)
        part = ((max(p[0] for p in pts) - min(p[0] for p in pts) + 1) / (b - a + 1)) if pts else 0
        res.append(("libellé de la barre d'espace", "OK" if part > 0.25 else "ECHEC",
                    f"occupe {part:.0%} de la largeur de la touche"))

    # Rangées de lettres : les compter par leur fond est illusoire, il se
    # confond avec celui du clavier, et le découpage en rangées est fragile en
    # paysage. On compte donc les caractères eux-mêmes, par taches sombres, sur
    # toute la zone au-dessus de la rangée du bas : le clavier alphabétique en
    # porte 29 (10 + 10 + 9), plus les lettres accentuées qui se détachent en
    # deux morceaux. Ce contrôle vise le défaut classique des touches vides.
    # La zone des lettres se déduit de la rangée du bas : les trois rangées qui
    # la précèdent ont la même hauteur qu'elle. Se repérer sur le fond du
    # clavier serait moins sûr, l'application au-dessus pouvant avoir le même.
    zone_bas = derniere[0] - 4
    zone_haut = max(y0, int(zone_bas - 3.25 * hauteur_rangee))
    vus = set()
    taches = 0
    for y0_ in range(zone_haut, zone_bas):
        for x0_ in range(w):
            if (x0_, y0_) in vus:
                continue
            if sum(px[x0_, y0_]) / 3 >= 120:
                continue
            pile, taille = [(x0_, y0_)], 0
            vus.add((x0_, y0_))
            while pile:
                x, y = pile.pop()
                taille += 1
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if (zone_haut <= ny < zone_bas and 0 <= nx < w
                            and (nx, ny) not in vus
                            and sum(px[nx, ny]) / 3 < 120):
                        vus.add((nx, ny))
                        pile.append((nx, ny))
            if taille > 40:
                taches += 1
    res.append(("caractères des rangées de lettres",
                "OK" if 25 <= taches <= 60 else "ECHEC",
                f"{taches} caractères visibles (29 attendus, accents compris)"))

    return res


if __name__ == "__main__":
    print(json.dumps(analyse(sys.argv[1], int(sys.argv[2])), ensure_ascii=False))
