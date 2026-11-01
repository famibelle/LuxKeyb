#!/usr/bin/env python3
"""Le présentoir de comptoir du Wuertduell : douze étuis (scripts/emballage.py)
debout, de face, en quatre colonnes d'une boîte chacune, et un fronton qui
montre le jeu. **Il se monte sans colle ni outil** (propriétaire, 2026-11-01 :
« tout pour faciliter l'impression, la prédécoupe, la découpe et le montage »).

    fronton (282 × 190 mm), glissé par deux pattes dans le haut du dos
    ┌──────────────────────────────┐
    │ WUERTDUELL · quatre cartes   │
    └──────────────────────────────┘
    ┌──────┬──────┬──────┬──────┐  dos (100 mm)
    │ B1   │ E1   │ NA1  │ NA2  │  trois étuis par colonne, l'un derrière l'autre
    └──────┴──────┴──────┴──────┘  lèvre (40 mm) : le nom de chaque boîte

Le bac est d'un seul tenant et se verrouille comme une boîte à pizza : le dos,
la lèvre et chaque flanc ont une **seconde paroi** qui se rabat à l'intérieur
et dont les pattes entrent dans des fentes du fond ; les rabats du dos et de
la lèvre sont pris entre les deux parois des flancs. Les trois cloisons et le
fronton s'enfoncent dans leurs fentes. Rien n'est collé : le présentoir se
démonte et se range à plat.

Les cotes intérieures sont celles des étuis ; les parois en ajoutent
l'épaisseur (trois cartons de chaque côté, deux devant et derrière).

Sorties, dans impression/emballage/presentoir/ (privé) :
- presentoir_{bac,fronton,cloisons}.pdf : page 1 le visuel (fond perdu 3 mm),
  page 2 le tracé seul, aux mêmes cotes ;
- presentoir_{bac,fronton,cloisons}_decoupe.{pdf,svg,dxf} : les tracés pour
  la table de découpe ou le fabricant de l'outil : PDF en tons directs
  « Decoupe » (magenta) et « Rainage » (cyan), SVG à calques, DXF en mm ;
- presentoir_bat.pdf : les pièces, leurs tracés, leurs noms, le cartouche ;
- presentoir_notice.pdf : la notice de montage illustrée, A4 ;
- presentoir_maquette.png, presentoir_plat.png, etapes/ : aperçus.

Le modèle en volume est construit **à partir du gabarit** : chaque paroi
tourne autour de son pli, si bien que la maquette et les étapes de la notice
montrent exactement ce que donne la découpe.

Demande reportlab et ezdxf (pip install --user reportlab ezdxf) pour les
fichiers de découpe, et shapely pour la géométrie.

À lancer depuis la racine du dépôt, après scripts/emballage.py :
python3 scripts/presentoir.py
"""
import math
import os
import shutil
import subprocess
import sys

import numpy as np
from PIL import Image, ImageOps
from shapely.affinity import translate
from shapely.geometry import LineString, Polygon, box
from shapely.ops import linemerge, unary_union

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import emballage as E  # noqa: E402
import carte_appli as CA  # noqa: E402  (chemin ajouté par emballage)
import verso_luxe as VL  # noqa: E402

SORTIE = os.path.join(E.SORTIE, 'presentoir')
FP = E.FP

# ------------------------------------------------------------------ les cotes (mm)

EP = 2.0                                   # épaisseur du carton (cannelure E)
JEU = 0.5                                  # jeu des fentes
COL, CLOISON, NCOL = 66.0, 2.0, 4          # une colonne : un étui de 64,5 mm et du jeu
LI = NCOL * COL + (NCOL - 1) * CLOISON     # largeur intérieure : 270
PI = 3 * E.D + 3.0                         # profondeur intérieure : trois étuis, 60
LAT, AR, AV = 3 * EP, 2 * EP, 2 * EP       # épaisseur des parois : flanc (3 cartons), dos, lèvre
LE = LI + 2 * LAT                          # largeur du fond : 282
BP = PI + AR + AV                          # profondeur du fond : 68
LEVRE, DOS_H = 40.0, 100.0                 # hauteurs de la lèvre et du dos
FRONTON_H = 190.0
PATTE_L, PATTE_H = 44.0, 30.0              # les pattes du fronton
PATTES_X = (54.0, LE - 54.0 - PATTE_L)
RABAT_W = 30.0                             # rabats du dos et de la lèvre
LANG_W, LANG_WF, LANG_P = 24.0, 14.0, 5.0  # pattes de verrouillage : dos et lèvre, flancs, longueur
CLOISON_H, CL_P = 55.0, PI - 2             # une cloison
CL_TAB_W, CL_TAB_P = 14.0, 4.0
CL_TABS_U = (13.0, CL_P - 13.0)
CARTON_RECTO, CARTON_VERSO = '#e9dfca', '#e3d5b8'   # le carton nu

ORDRE = ['B1', 'E1', 'NA1', 'NA2']
CARTES_FRONTON = [('B1', 'Kaz'), ('E1', 'Bam'), ('NA1', 'Apel'), ('NA2', 'Metro')]
URL_REGLE = E.URL_REGLE

ETAPES = [
    'Poser le bac à plat, face imprimée dessous.',
    'Relever le dos et la lèvre, puis plier leurs rabats vers l’intérieur.',
    'Relever les deux flancs contre les rabats.',
    'Rabattre la paroi intérieure de chaque flanc par-dessus les rabats, pattes dans les fentes du fond.',
    'Rabattre la seconde paroi du dos et celle de la lèvre, pattes dans les fentes du fond.',
    'Glisser les trois cloisons, pattes dans les fentes du fond.',
    'Enfoncer les deux pattes du fronton dans les fentes du haut du dos.',
    'Ranger trois étuis par colonne.',
]
ETAPES_COURTES = [
    'Poser le bac à plat, face imprimée dessous.',
    'Relever le dos et la lèvre, plier leurs rabats vers l’intérieur.',
    'Relever les flancs contre les rabats.',
    'Rabattre l’intérieur des flancs, pattes dans les fentes.',
    'Rabattre l’intérieur du dos et de la lèvre, pattes dans les fentes.',
    'Glisser les trois cloisons dans les fentes du fond.',
    'Enfoncer le fronton dans les deux fentes du haut du dos.',
    'Ranger trois étuis par colonne.',
]


def hauteur_flanc(u):
    """La hauteur du flanc à la profondeur u (0 au dos, BP devant)."""
    return DOS_H - (DOS_H - LEVRE) * u / BP


def reflet(p, a, b):
    """Le symétrique de p par rapport à la droite (a, b)."""
    dx, dy = b[0] - a[0], b[1] - a[1]
    n = math.hypot(dx, dy)
    dx, dy = dx / n, dy / n
    t = (p[0] - a[0]) * dx + (p[1] - a[1]) * dy
    fx, fy = a[0] + t * dx, a[1] + t * dy
    return (2 * fx - p[0], 2 * fy - p[1])


def pattes(x0, x1, y, centres, largeur, profondeur, sens):
    """Les points d'un bord horizontal de x0 vers x1 en y, avec des pattes
    (sens −1 vers le haut, +1 vers le bas), un peu effilées pour entrer sans forcer."""
    pts = []
    for c in sorted(centres, reverse=x0 > x1):
        g, d = (c - largeur / 2, c + largeur / 2) if x0 < x1 else (c + largeur / 2, c - largeur / 2)
        r = 1.5 if x0 < x1 else -1.5
        pts += [(g, y), (g + r, y + sens * profondeur), (d - r, y + sens * profondeur), (d, y)]
    return pts


# ------------------------------------------------------------------ le gabarit du bac


class Piece:
    def __init__(self, nom, poly, parent=None, charniere=None, cle=None, z=0.0, imprime=True):
        self.nom, self.poly, self.parent, self.cle, self.imprime = nom, poly, parent, cle, imprime
        self.charniere = charniere          # (point du pli, direction du pli)
        self.z = z                          # le pli décalé vers l'intérieur : l'épaisseur du carton, en volume


def cloisons_x(X0):
    """L'abscisse des trois cloisons, sur le plan : la colonne 0 (boîte de
    départ) est à gauche du client, donc à droite du plan, vu du dessous."""
    droite = X0 + LE - LAT
    return [droite - (i + 1) * COL - i * CLOISON - CLOISON / 2 for i in range(NCOL - 1)]


def gabarit():
    """Les pièces du bac à plat, en mm, avec leurs plis, les coupes entre deux
    pièces voisines non attachées et les fentes. Le plan est ramené en
    coordonnées positives ; X0, YF sont le coin du fond."""
    X0, YF = 0.0, 0.0
    P = {}
    P['fond'] = Piece('fond', [(X0, YF), (X0 + LE, YF), (X0 + LE, YF + BP), (X0, YF + BP)])
    # Le dos et sa seconde paroi.
    P['dos'] = Piece('dos', [(X0, YF - DOS_H), (X0 + LE, YF - DOS_H), (X0 + LE, YF), (X0, YF)],
                     'fond', ((X0, YF), (1, 0)), 'dos')
    y2 = YF - DOS_H - (DOS_H - 2 * EP)
    cx_dos = (X0 + LE * .25, X0 + LE * .75)
    P['dos_int'] = Piece('dos_int', [(X0 + LAT, YF - DOS_H), (X0 + LAT, y2)]
                         + pattes(X0 + LAT, X0 + LE - LAT, y2, cx_dos, LANG_W, LANG_P, -1)
                         + [(X0 + LE - LAT, y2), (X0 + LE - LAT, YF - DOS_H)],
                         'dos', ((X0, YF - DOS_H), (1, 0)), 'dos_int', z=-EP / 2)
    # La lèvre et sa seconde paroi.
    yl = YF + BP
    P['levre'] = Piece('levre', [(X0, yl), (X0 + LE, yl), (X0 + LE, yl + LEVRE), (X0, yl + LEVRE)],
                       'fond', ((X0, yl), (1, 0)), 'levre')
    y3 = yl + LEVRE + (LEVRE - 2 * EP)
    P['levre_int'] = Piece('levre_int', [(X0 + LE - LAT, yl + LEVRE), (X0 + LE - LAT, y3)]
                           + pattes(X0 + LE - LAT, X0 + LAT, y3, cx_dos, LANG_W, LANG_P, 1)
                           + [(X0 + LAT, y3), (X0 + LAT, yl + LEVRE)],
                           'levre', ((X0, yl + LEVRE), (1, 0)), 'levre_int', z=-EP / 2)
    # Les rabats du dos et de la lèvre, pris entre les deux parois des flancs.
    hr = hauteur_flanc(EP + RABAT_W) - 3
    for cote, x, s in (('g', X0, -1), ('d', X0 + LE, 1)):
        P[f'rabat_dos_{cote}'] = Piece(f'rabat_dos_{cote}', [(x, YF), (x + s * RABAT_W, YF), (x + s * RABAT_W, YF - hr),
                                                            (x, YF - DOS_H + 3)],
                                       'dos', ((x - s * EP, YF), (0, 1)), 'rabats', imprime=False)
        P[f'rabat_levre_{cote}'] = Piece(f'rabat_levre_{cote}', [(x, yl), (x + s * RABAT_W, yl),
                                                                (x + s * RABAT_W, yl + LEVRE - 3), (x, yl + LEVRE - 3)],
                                         'levre', ((x - s * EP, yl), (0, 1)), 'rabats', imprime=False)
    # Les flancs : un trapèze (DOS_H derrière, LEVRE devant), et leur paroi
    # intérieure, symétrique du flanc par rapport à son pli du haut.
    plis, coupes_int, fentes = [], [], []
    for cote, x, s in (('g', X0, -1), ('d', X0 + LE, 1)):
        def plan(u, v, x=x, s=s):          # (profondeur, hauteur) du flanc vers le plan
            return (x + s * v, YF + u)
        A, B, C, D = plan(0, 0), plan(BP, 0), plan(BP, LEVRE), plan(0, DOS_H)
        P[f'flanc_{cote}'] = Piece(f'flanc_{cote}', [A, D, C, B], 'fond', (A, (0, 1)), 'flancs')
        u0, u1 = AR, BP - AV
        uc = (BP * .3, BP * .7)
        bas = [plan(u0, 2 * EP)]
        for c in uc:
            g, d = c - LANG_WF / 2, c + LANG_WF / 2
            bas += [plan(g, 2 * EP), plan(g + 1.5, 2 * EP - LANG_P), plan(d - 1.5, 2 * EP - LANG_P), plan(d, 2 * EP)]
        bas += [plan(u1, 2 * EP)]
        haut = [plan(u1, hauteur_flanc(u1)), plan(u0, hauteur_flanc(u0))]
        interieur = [reflet(p, D, C) for p in bas] + haut
        P[f'flanc_int_{cote}'] = Piece(f'flanc_int_{cote}', interieur, f'flanc_{cote}',
                                       (D, (C[0] - D[0], C[1] - D[1])), 'flancs_int', z=-EP)
        plis += [LineString([A, B]), LineString(haut),
                 LineString([(x, YF), (x, YF - DOS_H + 3)]), LineString([(x, yl), (x, yl + LEVRE - 3)])]
        coupes_int += [LineString([(x, YF), (x + s * RABAT_W, YF)]), LineString([(x, yl), (x + s * RABAT_W, yl)])]
        # Les fentes des pattes du flanc, dans le fond.
        xf = x - s * (LAT - EP / 2)
        for c in uc:
            fentes.append(box(xf - (EP + JEU) / 2, YF + c - (LANG_WF + 1) / 2, xf + (EP + JEU) / 2, YF + c + (LANG_WF + 1) / 2))
    plis += [LineString([(X0, YF), (X0 + LE, YF)]), LineString([(X0, yl), (X0 + LE, yl)]),
             LineString([(X0 + LAT, YF - DOS_H), (X0 + LE - LAT, YF - DOS_H)]),
             LineString([(X0 + LAT, yl + LEVRE), (X0 + LE - LAT, yl + LEVRE)])]
    # Les fentes du fond : pattes du dos, de la lèvre, des cloisons.
    for c in cx_dos:
        for y in (YF + 1.5 * EP, yl - 1.5 * EP):
            fentes.append(box(c - (LANG_W + 1) / 2, y - (EP + JEU) / 2, c + (LANG_W + 1) / 2, y + (EP + JEU) / 2))
    for xc in cloisons_x(X0):
        for u in CL_TABS_U:
            y = YF + AR + 1 + u
            fentes.append(box(xc - (EP + JEU) / 2, y - (CL_TAB_W + 1) / 2, xc + (EP + JEU) / 2, y + (CL_TAB_W + 1) / 2))
    # Les fentes du fronton, sur le pli du haut du dos.
    for px in PATTES_X:
        fentes.append(box(X0 + px - .5, YF - DOS_H - (EP + JEU) / 2, X0 + px + PATTE_L + .5, YF - DOS_H + (EP + JEU) / 2))

    # Deux pièces ne doivent jamais se chevaucher sur le plan.
    polys = {n: Polygon(p.poly) for n, p in P.items()}
    for a in polys:
        for b in polys:
            if a < b and polys[a].intersection(polys[b]).area > .01:
                sys.exit(f'gabarit : {a} et {b} se chevauchent')

    # On ramène tout en coordonnées positives.
    mx, my, _, _ = unary_union(list(polys.values())).bounds
    dec = (-mx, -my)
    for p in P.values():
        p.poly = [(q[0] + dec[0], q[1] + dec[1]) for q in p.poly]
        if p.charniere:
            p.charniere = ((p.charniere[0][0] + dec[0], p.charniere[0][1] + dec[1]), p.charniere[1])
    plis = [translate(l, *dec) for l in plis]
    coupes_int = [translate(l, *dec) for l in coupes_int]
    fentes = [translate(f, *dec) for f in fentes]
    return P, plis, coupes_int, fentes, X0 + dec[0], YF + dec[1]


PIECES, PLIS, COUPES_INT, FENTES, X0, YF = gabarit()
_, _, BAC_L, BAC_H = unary_union([Polygon(p.poly) for p in PIECES.values()]).bounds


def lignes_de(g):
    if g.is_empty:
        return []
    if g.geom_type in ('MultiLineString', 'LineString'):
        g = linemerge(g) if g.geom_type == 'MultiLineString' else g
    geoms = [g] if g.geom_type == 'LineString' else list(getattr(g, 'geoms', []))
    return [x for x in geoms if x.geom_type == 'LineString' and x.length > .01]


def traits_bac():
    """Les coupes et les rainages du bac, fusionnés en tracés continus et sans doublon."""
    matiere = unary_union([Polygon(p.poly) for p in PIECES.values()])
    bords = [matiere.exterior] + list(matiere.interiors) + [f.exterior for f in FENTES]
    coupes = unary_union([LineString(b.coords) for b in bords] + COUPES_INT)
    rainages = unary_union(PLIS).difference(unary_union(FENTES))
    return lignes_de(coupes), lignes_de(rainages)


def piece_fronton():
    d = [(0, 0), (LE, 0), (LE, FRONTON_H)]
    for px in sorted(PATTES_X, reverse=True):
        d += [(px + PATTE_L, FRONTON_H), (px + PATTE_L - 1.5, FRONTON_H + PATTE_H), (px + 1.5, FRONTON_H + PATTE_H),
              (px, FRONTON_H)]
    return d + [(0, FRONTON_H)]


def traits_fronton():
    coupe = LineString(piece_fronton() + [(0, 0)])
    plis = [LineString([(px, FRONTON_H), (px + PATTE_L, FRONTON_H)]) for px in PATTES_X]
    return [coupe], plis


def piece_cloison(x0):
    pts = [(x0, 0), (x0 + CL_P, 0), (x0 + CL_P, CLOISON_H)]
    for u in sorted(CL_TABS_U, reverse=True):
        pts += [(x0 + u + CL_TAB_W / 2, CLOISON_H), (x0 + u + CL_TAB_W / 2 - 1, CLOISON_H + CL_TAB_P),
                (x0 + u - CL_TAB_W / 2 + 1, CLOISON_H + CL_TAB_P), (x0 + u - CL_TAB_W / 2, CLOISON_H)]
    return pts + [(x0, CLOISON_H)]


def traits_cloisons():
    return [LineString(piece_cloison(k * (CL_P + 12)) + [(k * (CL_P + 12), 0)]) for k in range(3)], []


# ------------------------------------------------------------------ les visuels, chacun dans son repère


def nuit(l, h, ident='nuit_p'):
    return f'<rect x="{-FP}" y="{-FP}" width="{l + 2 * FP}" height="{h + 2 * FP}" fill="url(#{ident})"/>'


def cadre_grand(x0, y0, x1, y1, k):
    """Le filet d'or de l'étui, à l'échelle k."""
    return f'<g transform="scale({k})">{E.cadre(x0 / k, y0 / k, x1 / k, y1 / k)}</g>'


def fronton(cartes):
    l, h = LE, FRONTON_H
    cx = l / 2
    s = [nuit(l, h)]
    s.append(VL.rosace(cx, 104, 92, 7, 30, 10, VL.GRAVURE_FINE, .22))
    s.append(VL.rosace(cx, 104, 66, 4, 40, 6, VL.GRAVURE_FINE, .18))
    import random
    hasard = random.Random(274)
    for _ in range(70):
        x, y = hasard.uniform(12, l - 12), hasard.uniform(12, h - 12)
        s.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{hasard.uniform(.25, .7):.2f}" fill="#e9dcb0"/>')
    s.append(cadre_grand(6, 6, l - 6, h - 6, 2.6))
    s.append(E.texte(cx, 24, 'LËTZEBUERGESCH CLAVIER', 5.2, E.OR_HI, 700, extra='letter-spacing="1.3"'))
    s.append(E.texte(cx, 51, 'WUERTDUELL', 27, 'url(#or_fronton)', famille='EBG', extra='letter-spacing="1.6"'))
    s.append(E.texte(cx, 62.5, 'Le duel des mots luxembourgeois', 7.2, E.CREME, 400, 'italic'))

    lc, hc = 44.0, 44.0 * 88 / 63
    for k, img in enumerate(cartes):
        angle = (-16, -5.5, 5.5, 16)[k]
        dx = (-48, -16, 16, 48)[k]
        dy = (6, 0, 0, 6)[k]
        x, y = cx + dx - lc / 2, 70 + dy
        rot = f'rotate({angle} {cx + dx} {y + hc})'
        ombre = f'<path d="{CA.roundrect(x + 1.2, y + 1.6, x + lc + 1.2, y + hc + 1.6, 2.2)}" fill="#050a1f"/>'
        s.append(f'<g transform="{rot}">{ombre}<image href="data:image/png;base64,{img}" x="{x}" y="{y}" '
                 f'width="{lc}" height="{hc}"/></g>')

    yy = 86
    for titre, sous in (('2 joueurs', 'face à face'), ('dès 8 ans', 'dès 6 ans : la Bataille des mots'),
                        ('15 min', 'la partie')):
        s.append(E.texte(36, yy, titre, 7.4, E.OR_HI, 700))
        s.append(E.texte(36, yy + 5.4, sous, 3.3, E.CREME, 400, 'italic'))
        yy += 21
    s.append(E.qr(l - 36 - 15, 78, 30, URL_REGLE))
    s.append(E.texte(l - 36, 114, 'La règle du jeu', 4.2, E.CREME, 700))
    # La pastille du prix reste blanche : elle reçoit l'étiquette du commerçant.
    s.append(f'<rect x="{l - 36 - 18}" y="124" width="36" height="16" rx="8" fill="#ffffff"/>')

    s.append(f'<path d="M24 151 H{l - 24}" stroke="{E.OR_LO}" stroke-width=".5"/>')
    s.append(E.texte(cx, 161.5, 'Règle d’or : chaque carte jouée se lit à voix haute en luxembourgeois.', 5.2,
                     E.OR_HI, 700))
    x = cx - 104
    for code in ORDRE:
        p = E.PRODUITS[code]
        s.append(f'<circle cx="{x + 3}" cy="{172.6}" r="2.8" fill="{p["couleur"]}"/>')
        s.append(E.texte(x + 8.4, 174.2, p['nom'], 4.4, E.CREME, 700, ancre='start'))
        x += 56
    return ''.join(s)


def levre():
    """La lèvre, côté client : le nom de chaque boîte au pied de sa colonne."""
    s = [nuit(LE, LEVRE)]
    s.append(f'<path d="M0 1.2 H{LE}" stroke="{E.OR_MID}" stroke-width=".8"/>')
    for i, code in enumerate(ORDRE):
        p = E.PRODUITS[code]
        x = LAT + i * (COL + CLOISON)
        s.append(f'<rect x="{x + 2}" y="6" width="{COL - 4}" height="16" rx="8" fill="{p["couleur"]}"/>')
        s.append(E.texte(x + COL / 2, 16.6, p['nom'].upper(), 4.6, '#ffffff', 700, extra='letter-spacing=".3"'))
        (n1, _, f1), (n2, _, f2) = p['paquets']
        s.append(E.medaillon(x + 8, 30.5, 4.4, f1, p['couleur'], f'email_{code}'))
        s.append(E.medaillon(x + COL - 8, 30.5, 4.4, f2, p['couleur'], f'email_{code}'))
        s.append(E.texte(x + COL / 2, 29.6, n1, 3.3, E.CREME, 700))
        s.append(E.texte(x + COL / 2, 34.4, n2, 3.3, E.CREME, 700))
    return ''.join(s)


def dos_exterieur():
    s = [nuit(LE, DOS_H)]
    s.append(VL.rosace(LE / 2, DOS_H / 2, 40, 3, 26, 6, '#1d2b5c', .25))
    s.append(E.lion(LE / 2, 34, 18))
    s.append(E.texte(LE / 2, 70, 'WUERTDUELL', 12, 'url(#or_dos_p)', famille='EBG', extra='letter-spacing=".8"'))
    s.append(E.texte(LE / 2, 80, 'Lëtzebuergesch Clavier', 4.4, E.CREME, 400, 'italic'))
    return ''.join(s)


def dos_interieur():
    """La seconde paroi du dos, côté client : on n'en voit que le haut, au-dessus des étuis."""
    l, h = LE - 2 * LAT, DOS_H - 2 * EP
    s = [nuit(l, h)]
    s.append(f'<path d="M0 3 H{l}" stroke="{E.OR_MID}" stroke-width=".8"/>')
    import random
    hasard = random.Random(100)
    for _ in range(40):
        s.append(f'<circle cx="{hasard.uniform(4, l - 4):.1f}" cy="{hasard.uniform(6, 30):.1f}" '
                 f'r="{hasard.uniform(.25, .6):.2f}" fill="#e9dcb0"/>')
    return ''.join(s)


def flanc(avant_a_droite):
    s = [nuit(BP, DOS_H, 'nuit_flanc')]
    cx = BP * (.36 if avant_a_droite else .64)
    s.append(E.lion(cx, DOS_H - 30, 12))
    s.append(E.texte(BP / 2, DOS_H - 9, 'WUERTDUELL', 6.4, 'url(#or_flanc)', famille='EBG', extra='letter-spacing=".4"'))
    return ''.join(s)


def fond_notice():
    """Sous le fond : la notice en huit lignes, lisible sur le présentoir livré à
    plat. Le fond est percé de fentes (cloisons, pattes) : le texte tient en
    quatre colonnes, entre les fentes des cloisons."""
    s = [nuit(LE, BP, 'nuit_flanc')]
    s.append(E.texte(LE / 2, 9.8, 'Montage sans colle ni outil', 4.4, E.OR_HI, 700))
    bords = [LAT] + sorted(xc - X0 for xc in cloisons_x(X0)) + [LE - LAT]
    for z in range(4):
        x = bords[z] + 4
        y = 19.5
        for i in (2 * z, 2 * z + 1):
            s.append(f'<circle cx="{x + 2.6}" cy="{y - 1.1}" r="2.8" fill="{E.OR_MID}"/>')
            s.append(E.texte(x + 2.6, y + .05, str(i + 1), 3.3, '#0b1333', 700))
            rangs = E.coupe(ETAPES_COURTES[i], 30)
            s.append(E.lignes(x + 7.2, y, rangs, 3.0, E.CREME, 3.9))
            y += len(rangs) * 3.9 + 4.6
    return ''.join(s)


def defs(couleurs):
    d = E.defs(E.PRODUITS['B1'])
    d += CA.grad_lineaire('or_fronton', 0, 30, 0, 52, [E.OR_HI, E.OR_MID, E.OR_LO], [0, .55, 1])
    d += CA.grad_lineaire('or_dos_p', 0, 61, 0, 71, [E.OR_HI, E.OR_MID, E.OR_LO], [0, .55, 1])
    d += CA.grad_lineaire('or_flanc', 0, DOS_H - 14, 0, DOS_H - 8.5, [E.OR_HI, E.OR_MID, E.OR_LO], [0, .55, 1])
    d += (f'<radialGradient id="nuit_p" cx=".5" cy=".55" r=".75"><stop offset="0" stop-color="{E.NUIT_CENTRE}"/>'
          f'<stop offset="1" stop-color="{E.NUIT_BORD}"/></radialGradient>'
          f'<linearGradient id="nuit_flanc" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#16244f"/>'
          f'<stop offset="1" stop-color="{E.NUIT_BORD}"/></linearGradient>')
    for code, c in couleurs.items():
        d += (f'<radialGradient id="email_{code}" cx=".38" cy=".32" r=".75"><stop offset="0" stop-color="{E.m(c, "#ffffff", .35)}"/>'
              f'<stop offset=".55" stop-color="{c}"/><stop offset="1" stop-color="{E.m(c, "#000000", .45)}"/></radialGradient>')
    return d


def chemin(g):
    """Une géométrie shapely en chemin SVG."""
    d = ''
    for poly in getattr(g, 'geoms', [g]):
        if poly.is_empty or poly.geom_type != 'Polygon':
            continue
        for anneau in [poly.exterior] + list(poly.interiors):
            d += 'M' + ' L'.join(f'{x:.3f} {y:.3f}' for x, y in anneau.coords) + ' Z '
    return d


def bac():
    """Le visuel du bac à plat : la nuit partout, et chaque paroi imprimée
    tournée pour se lire une fois montée. Vu du dessous, la gauche du plan est
    le flanc droit du présentoir."""
    s = [f'<rect x="{-FP}" y="{-FP}" width="{BAC_L + 2 * FP}" height="{BAC_H + 2 * FP}" fill="{E.NUIT_BORD}"/>']
    polys = {n: Polygon(p.poly) for n, p in PIECES.items()}
    n = [0]

    def paroi(nom, x, y, dessin, transf=''):
        """Le dessin d'une pièce, débordant de 3 mm sur les coupes, jamais sur les pièces voisines."""
        n[0] += 1
        autres = unary_union([q for k, q in polys.items() if k != nom])
        clip = polys[nom].buffer(FP, join_style=2).difference(autres)
        s.append(f'<clipPath id="pc{n[0]}"><path d="{chemin(clip)}" clip-rule="evenodd"/></clipPath>')
        s.append(f'<g clip-path="url(#pc{n[0]})"><g transform="translate({x} {y}){transf}">{dessin}</g></g>')

    paroi('dos', X0, YF - DOS_H, dos_exterieur())
    paroi('dos_int', X0 + LAT, YF - 2 * DOS_H + 2 * EP, dos_interieur(),
          f' rotate(180 {(LE - 2 * LAT) / 2} {(DOS_H - 2 * EP) / 2})')
    paroi('levre', X0, YF + BP, levre(), f' rotate(180 {LE / 2} {LEVRE / 2})')
    paroi('flanc_g', X0, YF, flanc(False), f' rotate(-90) translate({-BP} {-DOS_H})')
    paroi('flanc_d', X0 + LE, YF, flanc(True), f' rotate(90) translate(0 {-DOS_H})')
    paroi('fond', X0, YF, fond_notice())
    return ''.join(s)


def trace(coupes, rainages, epaisseur=.4, couleurs=('#e4002b', '#0080ff')):
    d_c = ' '.join('M' + ' L'.join(f'{x:.3f} {y:.3f}' for x, y in l.coords) for l in coupes)
    d_r = ' '.join('M' + ' L'.join(f'{x:.3f} {y:.3f}' for x, y in l.coords) for l in rainages)
    return ((f'<path d="{d_c}" fill="none" stroke="{couleurs[0]}" stroke-width="{epaisseur}"/>' if coupes else '')
            + (f'<path d="{d_r}" fill="none" stroke="{couleurs[1]}" stroke-width="{epaisseur}" '
               f'stroke-dasharray="2.4 1.4"/>' if rainages else ''))


NOMS = {'fond': 'fond', 'dos': 'dos', 'dos_int': 'seconde paroi du dos', 'levre': 'lèvre',
        'levre_int': 'seconde paroi de la lèvre', 'flanc_g': 'flanc', 'flanc_d': 'flanc',
        'flanc_int_g': 'paroi intérieure', 'flanc_int_d': 'paroi intérieure',
        'rabat_dos_g': 'rabat', 'rabat_dos_d': 'rabat', 'rabat_levre_g': 'rabat', 'rabat_levre_d': 'rabat'}


def etiquettes():
    s = ''
    for nom, p in PIECES.items():
        c = Polygon(p.poly).representative_point()
        dy = -22 if nom == 'fond' else 0
        s += E.texte(c.x, c.y + dy + 1.4, NOMS[nom], 3.6, '#e4007c', 700)
    return s


# ------------------------------------------------------------------ les fichiers de découpe


def ecrire_decoupe(base_, coupes, rainages, l, h):
    """Les tracés seuls, aux cotes et à l'origine du visuel (fond perdu compris),
    en trois formats : PDF à tons directs, SVG à calques, DXF en mm."""
    def d_de(lignes):
        return ' '.join('M' + ' L'.join(f'{x + FP:.3f} {y + FP:.3f}' for x, y in li.coords) for li in lignes)
    svg = (f'<?xml version="1.0" encoding="UTF-8"?>\n'
           f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:inkscape="http://www.inkscape.org/namespaces/inkscape" '
           f'width="{l}mm" height="{h}mm" viewBox="0 0 {l} {h}">\n'
           f'<g inkscape:groupmode="layer" id="Decoupe" inkscape:label="Découpe">'
           f'<path d="{d_de(coupes)}" fill="none" stroke="#ff00ff" stroke-width="0.25"/></g>\n'
           f'<g inkscape:groupmode="layer" id="Rainage" inkscape:label="Rainage">'
           f'<path d="{d_de(rainages)}" fill="none" stroke="#00ffff" stroke-width="0.25"/></g>\n</svg>\n')
    with open(base_ + '.svg', 'w', encoding='utf-8') as f:
        f.write(svg)

    # PDF à tons directs : la couleur dit à la machine ce qu'elle fait.
    try:
        from reportlab.lib.colors import CMYKColorSep
        from reportlab.pdfgen import canvas
        pt = 72 / 25.4
        c = canvas.Canvas(base_ + '.pdf', pagesize=(l * pt, h * pt))
        c.setTitle(os.path.basename(base_))
        for lignes, couleur in ((coupes, CMYKColorSep(0, 1, 0, 0, spotName='Decoupe', density=1)),
                                (rainages, CMYKColorSep(1, 0, 0, 0, spotName='Rainage', density=1))):
            c.setStrokeColor(couleur)
            c.setLineWidth(.25)
            if hasattr(c, 'setStrokeOverprint'):
                c.setStrokeOverprint(True)
            for li in lignes:
                p = c.beginPath()
                for k, (x, y) in enumerate(li.coords):
                    X, Y = (x + FP) * pt, (h - (y + FP)) * pt
                    if k == 0:
                        p.moveTo(X, Y)
                    else:
                        p.lineTo(X, Y)
                c.drawPath(p, stroke=1, fill=0)
        c.showPage()
        c.save()
    except ImportError:
        print('reportlab absent : pas de PDF de découpe à tons directs')

    # DXF, en millimètres, axe des y vers le haut.
    try:
        import ezdxf
        doc = ezdxf.new('R2010', setup=True)
        doc.units = ezdxf.units.MM
        doc.layers.add('DECOUPE', color=6)
        doc.layers.add('RAINAGE', color=4, linetype='DASHED')
        msp = doc.modelspace()
        for lignes, calque in ((coupes, 'DECOUPE'), (rainages, 'RAINAGE')):
            for li in lignes:
                pts = [(x + FP, h - (y + FP)) for x, y in li.coords]
                ferme = len(pts) > 2 and math.dist(pts[0], pts[-1]) < 1e-6
                msp.add_lwpolyline(pts[:-1] if ferme else pts, close=ferme, dxfattribs={'layer': calque})
        doc.saveas(base_ + '.dxf')
    except ImportError:
        print('ezdxf absent : pas de DXF')


# ------------------------------------------------------------------ le volume, construit sur le gabarit


def T(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def R(axe, deg):
    """Rotation de Rodrigues autour d'un axe unitaire : la même que rotate3d() en CSS."""
    a = np.array(axe, float)
    a /= np.linalg.norm(a)
    t = math.radians(deg)
    K = np.array([[0, -a[2], a[1]], [a[2], 0, -a[0]], [-a[1], a[0], 0]])
    m = np.eye(4)
    m[:3, :3] = np.eye(3) + math.sin(t) * K + (1 - math.cos(t)) * K @ K
    return m


def css(m):
    return 'matrix3d(' + ','.join(f'{v:.6f}' for v in m.T.flatten()) + ')'


def pose(colonnes, origine):
    """Une matrice de pose : directions x et y de la plaque, et son origine (en mm)."""
    cx, cy = np.array(colonnes[0], float), np.array(colonnes[1], float)
    m = np.eye(4)
    m[:3, 0], m[:3, 1], m[:3, 2], m[:3, 3] = cx, cy, np.cross(cx, cy), origine
    return m


class Scene:
    """Des plaques planes posées par des matrices (en mm), éclairées selon leur orientation."""

    def __init__(self, k):
        self.k, self.faces = k, []

    def plaque(self, M, l, h, recto, verso, clip=None):
        """Une plaque de l × h mm ; recto et verso : une image ou une couleur."""
        self.faces.append((M, l, h, recto, clip))
        self.faces.append((M @ T(l, 0, 0) @ R((0, 1, 0), 180), l, h, verso,
                           [(l - x, y) for x, y in clip] if clip else None))

    def html(self, V, largeur, hauteur):
        k = self.k
        lum = np.array([-.35, -.75, .55])
        lum /= np.linalg.norm(lum)
        els = []
        for M, l, h, fond, clip in self.faces:
            n = (V @ M)[:3, :3] @ np.array([0, 0, 1.0])
            n /= np.linalg.norm(n)
            b = .6 + .4 * max(0.0, float(n @ lum))
            style = f"background-image:url('file://{fond}');" if fond.endswith('.png') else f'background:{fond};'
            if clip:
                style += 'clip-path:polygon(' + ','.join(f'{x * k:.1f}px {y * k:.1f}px' for x, y in clip) + ');'
            els.append(f'<div class="p" style="width:{l * k:.1f}px;height:{h * k:.1f}px;{style}'
                       f'filter:brightness({b:.3f});transform:{css(M @ np.diag([1 / k, 1 / k, 1 / k, 1.0]))}"></div>')
        return f"""<!doctype html><html><head><meta charset="utf-8"><style>
html,body {{ margin:0; width:{largeur}px; height:{hauteur}px; overflow:hidden;
  background: radial-gradient(ellipse at 50% 40%, #fbf8f1 0%, #ece4d2 75%, #ddd2bb 100%); }}
.scene {{ position:absolute; left:0; top:0; width:{largeur}px; height:{hauteur}px; perspective:2800px;
  perspective-origin:50% 45%; }}
.monde {{ position:absolute; left:0; top:0; transform-style:preserve-3d; transform:{css(V)}; }}
.p {{ position:absolute; left:0; top:0; transform-origin:0 0; background-size:100% 100%; backface-visibility:hidden; }}
</style></head><body><div class="scene"><div class="monde">{''.join(els)}</div></div></body></html>"""


def textures(dossier, dpi=150):
    """Les images des pièces : recto pris sur le visuel du bac, verso sur le carton nu avec ses rainages et ses fentes."""
    u = dpi / 25.4
    rec = os.path.join(dossier, '_recto.png')
    E.raster(os.path.join(SORTIE, 'presentoir_bac.pdf'), rec, dpi)
    _, rainages = traits_bac()
    l, h = BAC_L + 2 * FP, BAC_H + 2 * FP
    fentes = ''.join(f'<path d="{chemin(f)}" fill="#7d6d50"/>' for f in FENTES)
    corps = (f'<rect x="{-FP}" y="{-FP}" width="{l}" height="{h}" fill="{CARTON_VERSO}"/>' + fentes
             + trace([], rainages, .7, ('', '#8a7a5c')))
    f = os.path.join(dossier, '_verso.html')
    with open(f, 'w', encoding='utf-8') as fh:
        fh.write(E.page_html(E.svg('', corps, l, h, -FP, -FP), l, h))
    E.chrome_pdf(f, f[:-5] + '.pdf')
    ver = os.path.join(dossier, '_verso.png')
    E.raster(f[:-5] + '.pdf', ver, dpi)
    os.remove(f)
    os.remove(f[:-5] + '.pdf')
    im_r, im_v = Image.open(rec).convert('RGB'), Image.open(ver).convert('RGB')
    tex = {}
    for nom, p in PIECES.items():
        x0, y0, x1, y1 = Polygon(p.poly).bounds
        cadre_ = (round((x0 + FP) * u), round((y0 + FP) * u), round((x1 + FP) * u), round((y1 + FP) * u))
        r = os.path.join(dossier, f'_t_{nom}_r.png')
        v = os.path.join(dossier, f'_t_{nom}_v.png')
        (im_r.crop(cadre_) if p.imprime else Image.new('RGB', (cadre_[2] - cadre_[0], cadre_[3] - cadre_[1]),
                                                       E.NUIT_BORD)).save(r)
        ImageOps.mirror(im_v.crop(cadre_)).save(v)
        tex[nom] = (r, v)
    fr = os.path.join(dossier, '_fronton.png')
    E.raster(os.path.join(SORTIE, 'presentoir_fronton.pdf'), fr, dpi)
    im = Image.open(fr).convert('RGB')
    im.crop((round(FP * u), round(FP * u), round((LE + FP) * u), round((FRONTON_H + FP) * u))).save(fr)
    tex['fronton'] = (fr, CARTON_VERSO)
    os.remove(rec)
    os.remove(ver)
    return tex


def faces_etui(code, dossier, dpi=150):
    """Les faces d'un étui, prises sur son PDF d'impression."""
    b = os.path.join(dossier, f'_etui_{code}.png')
    E.raster(os.path.join(E.SORTIE, E.PRODUITS[code]['dossier'], 'etui_impression.pdf'), b, dpi)
    im = Image.open(b).convert('RGB')
    os.remove(b)
    u = dpi / 25.4

    def zone(x, y, l, h, rot=0):
        z = im.crop((round((x + FP) * u), round((y + FP) * u), round((x + l + FP) * u), round((y + h + FP) * u)))
        return z.rotate(rot, expand=True) if rot else z

    sortie = {}
    for nom, z in (('face', zone(E.XF, E.T, E.W, E.H)), ('droite', zone(E.XB, E.T, E.D, E.H)),
                   ('gauche', zone(E.XA, E.T, E.D, E.H)), ('dessus', zone(E.XD, E.T - E.D, E.W, E.D, 180))):
        f = os.path.join(dossier, f'_{code}_{nom}.png')
        z.save(f)
        sortie[nom] = f
    return sortie


def chaine(nom, etat):
    """La position d'une pièce pliée : le produit des rotations autour des plis, du fond jusqu'à elle.
    Chaque pli tourne vers −z, l'intérieur du bac posé face imprimée dessous."""
    p = PIECES[nom]
    if p.parent is None:
        return np.eye(4)
    (hx, hy), (ax, ay) = p.charniere
    c = Polygon(p.poly).centroid
    signe = 1 if (ax * (c.y - hy) - ay * (c.x - hx)) < 0 else -1
    angle = signe * etat.get(p.cle, 0)
    return chaine(p.parent, etat) @ T(hx, hy, p.z) @ R((ax, ay, 0), angle) @ T(-hx, -hy, -p.z)


def construire(scene, tex, etat, etuis=None):
    """Le bac plié selon l'état, puis les cloisons, le fronton et les étuis."""
    for nom, p in PIECES.items():
        x0, y0, x1, y1 = Polygon(p.poly).bounds
        M = chaine(nom, etat) @ T(x0, y0, 0)
        scene.plaque(M, x1 - x0, y1 - y0, tex[nom][0], tex[nom][1], [(x - x0, y - y0) for x, y in p.poly])
    if etat.get('cloisons') is not None:
        dz = etat['cloisons']
        for xc in cloisons_x(X0):
            scene.plaque(pose(((0, 1, 0), (0, 0, 1)), (xc, YF + AR + 1, -CLOISON_H - dz)), CL_P, CLOISON_H,
                         CARTON_RECTO, CARTON_VERSO)
    if etat.get('fronton') is not None:
        dz = etat['fronton']
        scene.plaque(pose(((-1, 0, 0), (0, 0, 1)), (X0 + LE, YF + EP / 2, -(DOS_H + FRONTON_H) - dz)), LE, FRONTON_H,
                     tex['fronton'][0], tex['fronton'][1])
    # Les étuis : la colonne 0 à gauche du client (+x sur le plan), le rang 0 devant.
    if etuis:
        droite = X0 + LE - LAT
        for i, code in enumerate(ORDRE):
            f = etuis[code]
            bx = droite - (i + 1) * COL - i * CLOISON + (COL - E.W) / 2
            for r in (2, 1, 0):
                yav = YF + AR + PI - .6 - r * (E.D + .4)
                yar = yav - E.D
                scene.faces.append((pose(((-1, 0, 0), (0, 0, 1)), (bx + E.W, yav, -E.H)), E.W, E.H, f['face'], None))
                scene.faces.append((pose(((-1, 0, 0), (0, 1, 0)), (bx + E.W, yar, -E.H)), E.W, E.D, f['dessus'], None))
                scene.faces.append((pose(((0, -1, 0), (0, 0, 1)), (bx, yav, -E.H)), E.D, E.H, f['droite'], None))
                scene.faces.append((pose(((0, 1, 0), (0, 0, 1)), (bx + E.W, yar, -E.H)), E.D, E.H, f['gauche'], None))


def rendre(etat, sortie, dossier, tex, etuis=None, k=1.5, largeur=1200, hauteur=860, rx=-24, ry=200, centre=None):
    """Une vue du présentoir : posé face imprimée dessous (−90° autour de x), tourné vers le client."""
    scene = Scene(k)
    construire(scene, tex, etat, etuis)
    cx, cy, cz = centre or (X0 + LE / 2, YF + BP / 2, -70)
    V = (T(largeur / 2, hauteur * .5, 0) @ R((1, 0, 0), rx) @ R((0, 1, 0), ry) @ R((1, 0, 0), -90)
         @ np.diag([k, k, k, 1.0]) @ T(-cx, -cy, -cz))
    f = os.path.join(dossier, '_scene.html')
    with open(f, 'w', encoding='utf-8') as fh:
        fh.write(scene.html(V, largeur, hauteur))
    subprocess.run(['google-chrome', '--headless=new', '--disable-gpu', '--no-sandbox', '--hide-scrollbars',
                    '--allow-file-access-from-files', '--virtual-time-budget=8000',
                    f'--window-size={largeur},{hauteur + 87}', f'--screenshot={sortie}', f'file://{f}'],
                   check=True, capture_output=True, timeout=180)
    Image.open(sortie).crop((0, 0, largeur, hauteur)).save(sortie)
    os.remove(f)
    return sortie


# Les huit étapes : l'angle de chaque pli (en degrés), et les pièces rapportées.
PLIE = {'dos': 90, 'levre': 90, 'rabats': 90, 'flancs': 90, 'flancs_int': 180, 'dos_int': 180, 'levre_int': 180}
ETATS = [
    {},
    {'dos': 90, 'levre': 90, 'rabats': 45},
    {'dos': 90, 'levre': 90, 'rabats': 90, 'flancs': 55},
    {'dos': 90, 'levre': 90, 'rabats': 90, 'flancs': 90, 'flancs_int': 120},
    {'dos': 90, 'levre': 90, 'rabats': 90, 'flancs': 90, 'flancs_int': 180, 'dos_int': 150, 'levre_int': 150},
    {**PLIE, 'cloisons': 45},
    {**PLIE, 'cloisons': 0, 'fronton': 60},
    {**PLIE, 'cloisons': 0, 'fronton': 0},
]


def notice(images):
    """La notice A4 : les pièces, puis les huit étapes illustrées."""
    cases = ''.join(f'<div class="case"><img src="file://{img}"><p><b>{i + 1}</b>{E.esc(t)}</p></div>'
                    for i, (img, t) in enumerate(zip(images, ETAPES)))
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>
@font-face {{ font-family: Roboto; font-weight: 400; src: url("file://{E.POLICES}/roboto-latin-400-normal.woff2"); }}
@font-face {{ font-family: Roboto; font-weight: 700; src: url("file://{E.POLICES}/roboto-latin-700-normal.woff2"); }}
@font-face {{ font-family: EBG; src: url("file://{E.SERIF}"); }}
@page {{ size: A4; margin: 9mm 10mm; }}
body {{ font-family: Roboto, sans-serif; color: #1b2340; margin: 0; }}
h1 {{ font-family: EBG, serif; font-weight: normal; font-size: 22pt; margin: 0 0 1mm; color: #0b1333; letter-spacing: .5pt; }}
.sous {{ font-size: 10.5pt; margin: 0 0 3mm; color: #5b5446; }}
.pieces {{ font-size: 9.5pt; margin: 0 0 3.5mm; padding: 2.2mm 4mm; background: #f4efe4; border-radius: 2mm; }}
.grille {{ display: grid; grid-template-columns: 1fr 1fr; gap: 2mm 5mm; }}
.case img {{ width: 100%; border-radius: 1.5mm; display: block; }}
.case p {{ font-size: 9pt; margin: 1mm 0 0; line-height: 1.3; }}
.case b {{ display: inline-block; width: 5mm; height: 5mm; line-height: 5mm; text-align: center; border-radius: 50%;
  background: #E0B74E; color: #0b1333; margin-right: 1.8mm; font-size: 8.5pt; }}
.pied {{ font-size: 8pt; color: #5b5446; margin-top: 2.5mm; }}
</style></head><body>
<h1>Monter le présentoir Wuertduell</h1>
<p class="sous">Sans colle ni outil. Il se démonte et se range à plat.</p>
<p class="pieces"><b>Dans le carton :</b> le bac (une seule pièce), le fronton, trois cloisons.
Une fois monté : {LE / 10:.1f} × {BP / 10:.1f} cm au sol, {(DOS_H + FRONTON_H) / 10:.0f} cm de haut, douze étuis.</p>
<div class="grille">{cases}</div>
<p class="pied">Pour le démonter : sortir les étuis, le fronton et les cloisons, puis dégager les pattes du fond dans l’ordre inverse.</p>
</body></html>""".replace(f'{LE / 10:.1f}', f'{LE / 10:.1f}'.replace('.', ',')).replace(f'{BP / 10:.1f}', f'{BP / 10:.1f}'.replace('.', ','))


def produire():
    os.makedirs(SORTIE, exist_ok=True)
    couleurs = {c: p['couleur'] for c, p in E.PRODUITS.items()}
    cartes = [E.carte_png(E.PRODUITS[code]['dossier'], mot, 700) for code, mot in CARTES_FRONTON]
    if None in cartes:
        manque = [m for (c, m), img in zip(CARTES_FRONTON, cartes) if img is None]
        sys.exit(f'rectos absents : {", ".join(manque)} (scripts/rectos_boite.py)')
    art_fronton = fronton(cartes)
    art_bac = bac()
    # Les joyaux s'inscrivent en dessinant : les définitions viennent après les dessins.
    defs_ = defs(couleurs)

    coupes_b, rainages_b = traits_bac()
    coupes_f, rainages_f = traits_fronton()
    coupes_c, rainages_c = traits_cloisons()
    l_b, h_b = BAC_L + 2 * FP, BAC_H + 2 * FP
    l_f, h_f = LE + 2 * FP, FRONTON_H + PATTE_H + 2 * FP
    l_c, h_c = 3 * CL_P + 2 * 12 + 2 * FP, CLOISON_H + CL_TAB_P + 2 * FP
    dessin_fronton = (f'<clipPath id="clip_fronton"><rect x="{-FP}" y="{-FP}" width="{LE + 2 * FP}" '
                      f'height="{FRONTON_H + FP}"/></clipPath><g clip-path="url(#clip_fronton)">{art_fronton}</g>' + ''.join(
                          f'<rect x="{px - FP}" y="{FRONTON_H}" width="{PATTE_L + 2 * FP}" height="{PATTE_H + FP}" '
                          f'fill="{E.NUIT_BORD}"/>' for px in PATTES_X))
    dessin_cloisons = ''.join(f'<path d="{chemin(Polygon(piece_cloison(k * (CL_P + 12))).buffer(FP, join_style=2))}" '
                              f'fill="{CARTON_RECTO}"/>' for k in range(3))

    # Les fichiers de l'imprimeur : une pièce par PDF (le visuel, puis le tracé), et les fichiers de découpe.
    for nom, art, coupes, rainages, l, h in (('presentoir_bac', art_bac, coupes_b, rainages_b, l_b, h_b),
                                              ('presentoir_fronton', dessin_fronton, coupes_f, rainages_f, l_f, h_f),
                                              ('presentoir_cloisons', dessin_cloisons, coupes_c, rainages_c, l_c, h_c)):
        contenu = E.svg(defs_, art, l, h, -FP, -FP) + E.svg('', trace(coupes, rainages), l, h, -FP, -FP)
        f = os.path.join(SORTIE, f'_{nom}.html')
        with open(f, 'w', encoding='utf-8') as fh:
            fh.write(E.page_html(contenu, l, h))
        E.chrome_pdf(f, os.path.join(SORTIE, f'{nom}.pdf'))
        os.remove(f)
        ecrire_decoupe(os.path.join(SORTIE, f'{nom}_decoupe'), coupes, rainages, l, h)

    # Le BAT : les trois pièces sur une planche, tracés, noms des pièces, cartouche.
    marge = 14
    lb = marge * 3 + l_b + l_f
    hb = marge * 2 + 18 + max(h_b, h_f + marge + h_c)
    corps = (f'<rect x="0" y="0" width="{lb}" height="{hb}" fill="#ffffff"/>'
             + E.texte(marge, 13, 'Wuertduell · présentoir de comptoir (12 étuis), montage sans colle · BAT à plat',
                       7, '#222', 700, ancre='start')
             + E.texte(marge, 21, f'Fond {LE:g} × {BP:g} mm, dos {DOS_H:g} mm, lèvre {LEVRE:g} mm, fronton {LE:g} × '
                       f'{FRONTON_H:g} mm, hauteur montée {DOS_H + FRONTON_H:g} mm · carton {EP:g} mm, fentes '
                       f'{EP + JEU:g} mm · rouge : coupe · bleu : rainage · cotes à reprendre sur le gabarit du fabricant'
                       .replace('.', ','), 3.4, '#444', ancre='start')
             + f'<g transform="translate({marge + FP} {marge + 18 + FP})">{art_bac}{trace(coupes_b, rainages_b)}{etiquettes()}</g>'
             + f'<g transform="translate({marge * 2 + l_b + FP} {marge + 18 + FP})">{dessin_fronton}{trace(coupes_f, rainages_f)}</g>'
             + f'<g transform="translate({marge * 2 + l_b + FP} {marge * 2 + 18 + h_f + FP})">{dessin_cloisons}'
               f'{trace(coupes_c, rainages_c)}</g>'
             + E.texte(marge * 2 + l_b, marge * 2 + 18 + h_f - 3, 'Trois cloisons (sans impression)', 3.6, '#444', 700,
                       ancre='start')
             + E.texte(marge * 2 + l_b + FP + LE - 36, marge + 18 + FP + 121, 'pastille pour l’étiquette de prix',
                       3.2, '#e4007c', 700))
    f = os.path.join(SORTIE, '_bat.html')
    with open(f, 'w', encoding='utf-8') as fh:
        fh.write(E.page_html(E.svg(defs_, corps, lb, hb, 0, 0), lb, hb))
    E.chrome_pdf(f, os.path.join(SORTIE, 'presentoir_bat.pdf'))
    os.remove(f)
    E.raster(os.path.join(SORTIE, 'presentoir_bat.pdf'), os.path.join(SORTIE, 'presentoir_plat.png'), 60)

    # Le volume : les textures, les étapes de la notice, la maquette garnie.
    tmp = os.path.join(SORTIE, '_tmp')
    os.makedirs(tmp, exist_ok=True)
    tex = textures(tmp)
    etuis = {code: faces_etui(code, tmp) for code in ORDRE}
    dossier_etapes = os.path.join(SORTIE, 'etapes')
    os.makedirs(dossier_etapes, exist_ok=True)
    images = []
    # Le plan à plat demande du recul ; le bac monté, on s'approche.
    cadrages = [(1.25, -60), (1.3, -95), (1.38, -100), (1.38, -100), (1.85, -50), (1.85, -50), (1.28, -150), (1.28, -150)]
    for i, (etat, (k, z)) in enumerate(zip(ETATS, cadrages)):
        img = os.path.join(dossier_etapes, f'etape_{i + 1}.png')
        rendre(etat, img, tmp, tex, etuis if i == len(ETATS) - 1 else None, k=k, largeur=1000, hauteur=500,
               rx=-30, ry=205, centre=(X0 + LE / 2, YF + BP / 2, z))
        images.append(img)
    f = os.path.join(SORTIE, '_notice.html')
    with open(f, 'w', encoding='utf-8') as fh:
        fh.write(notice(images))
    E.chrome_pdf(f, os.path.join(SORTIE, 'presentoir_notice.pdf'))
    os.remove(f)
    sortie = rendre({**PLIE, 'cloisons': 0, 'fronton': 0}, os.path.join(SORTIE, 'presentoir_maquette.png'), tmp, tex,
                    etuis, k=2.6, largeur=1500, hauteur=1080, rx=-14, ry=204, centre=(X0 + LE / 2, YF + BP / 2, -140))
    shutil.rmtree(tmp)
    return sortie


if __name__ == '__main__':
    print(produire())
