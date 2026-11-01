#!/usr/bin/env python3
"""Le présentoir de comptoir du Wuertduell : douze étuis (scripts/emballage.py)
debout, de face, en quatre colonnes d'une boîte chacune, et un fronton qui
montre le jeu.

    fronton (274 × 190 mm), glissé par deux pattes dans le dos à double paroi
    ┌──────────────────────────────┐
    │ WUERTDUELL · quatre cartes   │
    └──────────────────────────────┘
    ┌──────┬──────┬──────┬──────┐  dos (100 mm)
    │ B1   │ E1   │ NA1  │ NA2  │  trois étuis par colonne, l'un derrière l'autre
    └──────┴──────┴──────┴──────┘  lèvre (40 mm) : le nom de chaque boîte

Trois pièces :
1. le bac, d'un seul tenant : fond, lèvre et dos à double paroi (la seconde
   paroi se rabat à l'intérieur, imprimée côté client), deux flancs, pattes
   de collage ; deux fentes reçoivent les pattes du fronton ;
2. le fronton ;
3. trois cloisons, sans impression.

Imprimé d'un seul côté. Les cotes suivent celles de l'étui ; un fabricant de
PLV les reprendra sur son propre gabarit (cannelure, rainages, verrous).

Sorties, dans impression/emballage/presentoir/ : presentoir_impression.pdf
(une page par pièce, fond perdu 3 mm, puis le tracé), presentoir_bat.pdf,
presentoir_plat.png, presentoir_maquette.png.

À lancer depuis la racine du dépôt, après scripts/emballage.py :
python3 scripts/presentoir.py
"""
import os
import subprocess
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import emballage as E  # noqa: E402
import carte_appli as CA  # noqa: E402  (chemin ajouté par emballage)
import verso_luxe as VL  # noqa: E402

SORTIE = os.path.join(E.SORTIE, 'presentoir')
FP = E.FP

# ------------------------------------------------------------------ les cotes (mm)

COL, CLOISON, NCOL = 66.0, 2.0, 4         # une colonne : un étui de 64,5 mm et du jeu
EP = 2.0                                   # épaisseur du carton (cannelure E)
LI = NCOL * COL + (NCOL - 1) * CLOISON     # largeur intérieure : 270
LE = LI + 2 * EP                           # largeur extérieure : 274
PROF = 3 * E.D + 3.0                       # trois étuis l'un derrière l'autre : 60
LEVRE, DOS_H = 40.0, 100.0                 # hauteurs de la lèvre et du dos
FRONTON_H = 190.0
PATTE_L, PATTE_H = 44.0, 30.0              # les pattes du fronton
PATTES_X = (54.0, LE - 54.0 - PATTE_L)
COLLE = 15.0

ORDRE = ['B1', 'E1', 'NA1', 'NA2']
CARTES_FRONTON = [('B1', 'Kaz'), ('E1', 'Bam'), ('NA1', 'Apel'), ('NA2', 'Metro')]
URL_REGLE = E.URL_REGLE

# Le bac à plat : le fond au milieu ; le dos et sa seconde paroi au-dessus,
# la lèvre et la sienne au-dessous, les flancs de part et d'autre.
X0 = DOS_H + 2                               # bord gauche du fond (les flancs sont à côté)
Y_DOS2, Y_DOS, Y_FOND = 0.0, DOS_H, 2 * DOS_H
Y_LEVRE = Y_FOND + PROF
Y_LEVRE2 = Y_LEVRE + LEVRE
BAC_L, BAC_H = 2 * X0 + LE, Y_LEVRE2 + LEVRE


def nuit(l, h, ident='nuit_p'):
    return f'<rect x="{-FP}" y="{-FP}" width="{l + 2 * FP}" height="{h + 2 * FP}" fill="url(#{ident})"/>'


def cadre_grand(x0, y0, x1, y1, k):
    """Le filet d'or de l'étui, à l'échelle k."""
    return f'<g transform="scale({k})">{E.cadre(x0 / k, y0 / k, x1 / k, y1 / k)}</g>'


# ------------------------------------------------------------------ les visuels, chacun dans son repère


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

    # Quatre cartes, une par boîte, en éventail.
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

    # À gauche, pour qui ; à droite, la règle et le prix.
    yy = 86
    for titre, sous in (('2 joueurs', 'face à face'), ('dès 8 ans', 'dès 6 ans : la Bataille des mots'),
                        ('15 min', 'la partie')):
        s.append(E.texte(34, yy, titre, 7.4, E.OR_HI, 700))
        s.append(E.texte(34, yy + 5.4, sous, 3.3, E.CREME, 400, 'italic'))
        yy += 21
    s.append(E.qr(l - 34 - 15, 78, 30, URL_REGLE))
    s.append(E.texte(l - 34, 114, 'La règle du jeu', 4.2, E.CREME, 700))
    # La pastille du prix reste blanche : elle reçoit l'étiquette du commerçant.
    s.append(f'<rect x="{l - 34 - 18}" y="124" width="36" height="16" rx="8" fill="#ffffff"/>')

    # Le bas : la règle d'or, et les quatre boîtes.
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
        x = EP + i * (COL + CLOISON)
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
    s = [nuit(LI, DOS_H)]
    s.append(f'<path d="M0 3 H{LI}" stroke="{E.OR_MID}" stroke-width=".8"/>')
    import random
    hasard = random.Random(100)
    for _ in range(40):
        s.append(f'<circle cx="{hasard.uniform(4, LI - 4):.1f}" cy="{hasard.uniform(6, 30):.1f}" '
                 f'r="{hasard.uniform(.25, .6):.2f}" fill="#e9dcb0"/>')
    return ''.join(s)


def trapeze(avant_a_droite):
    """Le contour d'un flanc (largeur PROF, haut de DOS_H derrière, de LEVRE devant)."""
    if avant_a_droite:
        return f'M0 0 L{PROF} {DOS_H - LEVRE} L{PROF} {DOS_H} L0 {DOS_H} Z'
    return f'M0 {DOS_H - LEVRE} L{PROF} 0 L{PROF} {DOS_H} L0 {DOS_H} Z'


def flanc(avant_a_droite):
    s = [nuit(PROF, DOS_H, 'nuit_flanc')]
    cx = PROF * (.36 if avant_a_droite else .64)
    s.append(E.lion(cx, DOS_H - 30, 12))
    s.append(E.texte(PROF / 2, DOS_H - 9, 'WUERTDUELL', 6.4, 'url(#or_flanc)', famille='EBG', extra='letter-spacing=".4"'))
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


# ------------------------------------------------------------------ les pièces à plat


def contour_bac():
    """La coupe du bac (sans les fentes), sens horaire depuis le haut de la seconde paroi du dos."""
    x0, x1 = X0, X0 + LE
    p = [(x0 + EP, Y_DOS2), (x1 - EP, Y_DOS2), (x1 - EP, Y_DOS),            # seconde paroi du dos
         (x1, Y_DOS), (x1 + COLLE, Y_DOS + 4), (x1 + COLLE, Y_FOND - 4), (x1, Y_FOND),   # patte du dos
         (x1 + DOS_H, Y_FOND), (x1 + LEVRE, Y_LEVRE), (x1, Y_LEVRE),       # flanc droit (à plat)
         (x1 + COLLE, Y_LEVRE + 4), (x1 + COLLE, Y_LEVRE2 - 4), (x1, Y_LEVRE2),         # patte de la lèvre
         (x1 - EP, Y_LEVRE2), (x1 - EP, Y_LEVRE2 + LEVRE), (x0 + EP, Y_LEVRE2 + LEVRE), (x0 + EP, Y_LEVRE2),
         (x0, Y_LEVRE2), (x0 - COLLE, Y_LEVRE2 - 4), (x0 - COLLE, Y_LEVRE + 4), (x0, Y_LEVRE),
         (x0 - LEVRE, Y_LEVRE), (x0 - DOS_H, Y_FOND), (x0, Y_FOND),
         (x0 - COLLE, Y_FOND - 4), (x0 - COLLE, Y_DOS + 4), (x0, Y_DOS), (x0 + EP, Y_DOS)]
    return 'M' + ' L'.join(f'{x:.2f} {y:.2f}' for x, y in p) + ' Z'


def fentes_bac():
    """Les deux fentes du pli haut du dos, où entrent les pattes du fronton."""
    return ''.join(f'<rect x="{X0 + px - .5}" y="{Y_DOS - 1.5}" width="{PATTE_L + 1}" height="3" rx="1.5"/>'
                   for px in PATTES_X)


def plis_bac():
    x0, x1 = X0, X0 + LE
    seg = [(x0 + EP, Y_DOS, x1 - EP, Y_DOS), (x0, Y_FOND, x1, Y_FOND), (x0, Y_LEVRE, x1, Y_LEVRE),
           (x0 + EP, Y_LEVRE2, x1 - EP, Y_LEVRE2), (x0, Y_DOS, x0, Y_FOND), (x1, Y_DOS, x1, Y_FOND),
           (x0, Y_FOND, x0, Y_LEVRE), (x1, Y_FOND, x1, Y_LEVRE), (x0, Y_LEVRE, x0, Y_LEVRE2), (x1, Y_LEVRE, x1, Y_LEVRE2)]
    return ''.join(f'M{a:.2f} {b:.2f} L{c:.2f} {e:.2f} ' for a, b, c, e in seg)


def bac(defs_):
    """Le visuel du bac à plat, chaque paroi tournée pour se lire une fois montée."""
    s = [f'<rect x="{-FP}" y="{-FP}" width="{BAC_L + 2 * FP}" height="{BAC_H + 2 * FP}" fill="{E.NUIT_BORD}"/>']
    n = [0]

    def paroi(x, y, l, h, dessin, transf='', forme=None):
        n[0] += 1
        clip = forme or f'<rect x="{-FP}" y="{-FP}" width="{l + 2 * FP}" height="{h + 2 * FP}"/>'
        s.append(f'<clipPath id="pc{n[0]}">{clip}</clipPath>')
        s.append(f'<g transform="translate({x} {y}){transf}"><g clip-path="url(#pc{n[0]})">{dessin}</g></g>')

    # Le dos, vu de derrière : à l'endroit, le haut loin du fond.
    paroi(X0, Y_DOS, LE, DOS_H, dos_exterieur())
    # Sa seconde paroi se rabat à l'intérieur, face au client : tête en bas à plat.
    paroi(X0 + EP, Y_DOS2, LI, DOS_H, dos_interieur(), f' rotate(180 {LI / 2} {DOS_H / 2})')
    # La lèvre se relève devant : tête en bas à plat.
    paroi(X0, Y_LEVRE, LE, LEVRE, levre(), f' rotate(180 {LE / 2} {LEVRE / 2})')
    # Vu du dessous, la gauche du plan est le flanc droit du présentoir : le haut vers l'extérieur.
    paroi(X0, Y_FOND, PROF, DOS_H, flanc(False), f' rotate(-90) translate({-PROF} {-DOS_H})')
    paroi(X0 + LE, Y_FOND, PROF, DOS_H, flanc(True), f' rotate(90) translate(0 {-DOS_H})')
    s.append(f'<rect x="{X0}" y="{Y_FOND}" width="{LE}" height="{PROF}" fill="{E.NUIT_BORD}"/>')
    s.append(E.texte(X0 + LE / 2, Y_FOND + PROF / 2 + 2, 'Présentoir Wuertduell · 12 étuis', 5, '#3a4a7a', 700))
    # Les pattes de collage, sans encre.
    for x, y0, y1, sens in ((X0 + LE, Y_DOS, Y_FOND, 1), (X0, Y_DOS, Y_FOND, -1),
                            (X0 + LE, Y_LEVRE, Y_LEVRE2, 1), (X0, Y_LEVRE, Y_LEVRE2, -1)):
        s.append(f'<path d="M{x} {y0} L{x + sens * (COLLE + FP)} {y0 + 3} L{x + sens * (COLLE + FP)} {y1 - 3} L{x} {y1} Z" fill="#ffffff"/>')
    return ''.join(s)


def trace_bac():
    return (f'<path d="{contour_bac()}" fill="none" stroke="#e4002b" stroke-width=".4"/>'
            f'<g fill="none" stroke="#e4002b" stroke-width=".4">{fentes_bac()}</g>'
            f'<path d="{plis_bac()}" fill="none" stroke="#0080ff" stroke-width=".4" stroke-dasharray="2.4 1.4"/>')


def trace_fronton():
    d = f'M0 0 H{LE} V{FRONTON_H}'
    for px in sorted(PATTES_X, reverse=True):
        d += f' H{px + PATTE_L} V{FRONTON_H + PATTE_H - 4} Q{px + PATTE_L} {FRONTON_H + PATTE_H} {px + PATTE_L - 4} {FRONTON_H + PATTE_H} H{px + 4} Q{px} {FRONTON_H + PATTE_H} {px} {FRONTON_H + PATTE_H - 4} V{FRONTON_H}'
    d += ' H0 Z'
    plis = ''.join(f'M{px} {FRONTON_H} H{px + PATTE_L} ' for px in PATTES_X)
    return (f'<path d="{d}" fill="none" stroke="#e4002b" stroke-width=".4"/>'
            f'<path d="{plis}" fill="none" stroke="#0080ff" stroke-width=".4" stroke-dasharray="2.4 1.4"/>')


CLOISON_H = 55.0


def trace_cloisons():
    s = ''
    for k in range(3):
        x = k * (PROF + 24)
        s += (f'<path d="M{x} 0 H{x + PROF} V{CLOISON_H + 10} H{x} Z" fill="#f4efe4" stroke="#e4002b" stroke-width=".4"/>'
              f'<path d="M{x} {CLOISON_H} H{x + PROF}" stroke="#0080ff" stroke-width=".4" stroke-dasharray="2.4 1.4"/>')
    return s


# ------------------------------------------------------------------ la maquette


def faces_etui(code, dpi=200):
    """Les faces d'un étui, prises sur son PDF d'impression."""
    dossier = os.path.join(E.SORTIE, E.PRODUITS[code]['dossier'])
    base = os.path.join(SORTIE, f'_etui_{code}')
    subprocess.run(['pdftoppm', '-png', '-r', str(dpi), '-f', '1', '-l', '1', '-singlefile',
                    os.path.join(dossier, 'etui_impression.pdf'), base], check=True)
    im = Image.open(base + '.png').convert('RGB')
    os.remove(base + '.png')
    u = dpi / 25.4

    def zone(x, y, l, h, rot=0):
        z = im.crop((round((x + FP) * u), round((y + FP) * u), round((x + l + FP) * u), round((y + h + FP) * u)))
        return z.rotate(rot, expand=True) if rot else z

    sortie = {}
    for nom, z in (('face', zone(E.XF, E.T, E.W, E.H)), ('droite', zone(E.XB, E.T, E.D, E.H)),
                   ('dessus', zone(E.XD, E.T - E.D, E.W, E.D, 180))):
        f = os.path.join(SORTIE, f'_{code}_{nom}.png')
        z.save(f)
        sortie[nom] = f
    return sortie


def maquette(pieces):
    """Le présentoir garni, en CSS 3D : chaque paroi est son visuel, chaque étui ses trois faces vues."""
    k = 2.4
    faces = {code: faces_etui(code) for code in ORDRE}

    def plan(img, l, h, transf, extra=''):
        fond = f"background-image:url('file://{img}');" if img.endswith('.png') else f'background:{img};'
        return (f'<div class="p" style="width:{l * k:.1f}px;height:{h * k:.1f}px;{fond}'
                f'transform:{transf};{extra}"></div>')

    def t(x, y, z, rot=''):
        return f'translate3d({x * k:.1f}px,{-y * k:.1f}px,{-z * k:.1f}px) {rot}'

    els = []
    carton = '#e8e1d0'
    # Les flancs : dehors imprimé, dedans carton nu.
    trap_g = 'clip-path: polygon(0 0, 100% 60%, 100% 100%, 0 100%);'
    trap_d = 'clip-path: polygon(0 60%, 100% 0, 100% 100%, 0 100%);'
    els.append(plan(pieces['flanc_g'], PROF, DOS_H, t(0, DOS_H, PROF, 'rotateY(-90deg)'), trap_g))
    els.append(plan(carton, PROF, DOS_H, t(EP, DOS_H, 0, 'rotateY(90deg)'), trap_d))
    els.append(plan(pieces['flanc_d'], PROF, DOS_H, t(LE, DOS_H, 0, 'rotateY(90deg)'), trap_d))
    els.append(plan(carton, PROF, DOS_H, t(LE - EP, DOS_H, PROF, 'rotateY(-90deg)'), trap_g))
    # Le dos, côté client, et le fronton au-dessus.
    els.append(plan(pieces['dos_int'], LI, DOS_H, t(EP, DOS_H, PROF - EP)))
    els.append(plan(pieces['fronton'], LE, FRONTON_H, t(0, DOS_H + FRONTON_H, PROF - EP / 2)))
    # Le fond.
    els.append(plan('#101a44', LE, PROF, t(0, 0, PROF, 'rotateX(90deg)')))
    # Les cloisons.
    for i in range(1, NCOL):
        x = EP + i * (COL + CLOISON) - CLOISON / 2
        els.append(plan(carton, PROF - EP, CLOISON_H, t(x, CLOISON_H, PROF - EP, 'rotateY(-90deg)')))
    # Les étuis, du fond vers l'avant.
    for r in (2, 1, 0):
        for i, code in enumerate(ORDRE):
            f = faces[code]
            x = EP + i * (COL + CLOISON) + (COL - E.W) / 2
            z = EP + .6 + r * (E.D + .4)
            els.append(plan(f['face'], E.W, E.H, t(x, E.H, z)))
            els.append(plan(f['dessus'], E.W, E.D, t(x, E.H, z + E.D, 'rotateX(90deg)')))
            els.append(plan(f['droite'], E.D, E.H, t(x + E.W, E.H, z, 'rotateY(90deg)')))
    # La lèvre, devant tout.
    els.append(plan(pieces['levre'], LE, LEVRE, t(0, LEVRE, 0)))

    largeur, hauteur = 1500, 1080
    page = f"""<!doctype html><html><head><meta charset="utf-8"><style>
html,body {{ margin:0; width:{largeur}px; height:{hauteur}px; overflow:hidden;
  background: linear-gradient(180deg, #f6f1e6 0%, #ece3cf 66%, #cdbf9f 66.1%, #bfae8a 100%); }}
.scene {{ position:absolute; left:{largeur / 2 - LE * k / 2:.0f}px; top:{hauteur * .84:.0f}px; perspective: 2600px; }}
.monde {{ position:relative; transform-style: preserve-3d; transform: rotateX(-14deg) rotateY(-24deg); }}
.p {{ position:absolute; left:0; top:0; transform-origin: 0 0; background-size: 100% 100%; backface-visibility: hidden; }}
.ombre {{ position:absolute; left:{largeur / 2 - LE * k * .62:.0f}px; top:{hauteur * .78:.0f}px; width:{LE * k * 1.25:.0f}px; height:{PROF * k * 1.6:.0f}px;
  border-radius:50%; background: radial-gradient(ellipse, rgba(70,50,20,.45), rgba(70,50,20,0) 68%); }}
</style></head><body><div class="ombre"></div><div class="scene"><div class="monde">{''.join(els)}</div></div></body></html>"""
    f = os.path.join(SORTIE, '_maquette.html')
    with open(f, 'w', encoding='utf-8') as fh:
        fh.write(page)
    sortie = os.path.join(SORTIE, 'presentoir_maquette.png')
    subprocess.run(['google-chrome', '--headless=new', '--disable-gpu', '--no-sandbox', '--hide-scrollbars',
                    '--allow-file-access-from-files', '--virtual-time-budget=8000', f'--window-size={largeur},{hauteur + 87}',
                    f'--screenshot={sortie}', f'file://{f}'], check=True, capture_output=True, timeout=180)
    Image.open(sortie).crop((0, 0, largeur, hauteur)).save(sortie)
    os.remove(f)
    for fs in faces.values():
        for x in fs.values():
            os.remove(x)
    return sortie


def piece_png(defs_, dessin, l, h, nom, dpi=150):
    """Un visuel seul, rendu en PNG pour la maquette."""
    html_ = E.page_html(E.svg(defs_, dessin, l, h, 0, 0), l, h)
    f = os.path.join(SORTIE, f'_{nom}.html')
    with open(f, 'w', encoding='utf-8') as fh:
        fh.write(html_)
    pdf = f[:-5] + '.pdf'
    E.chrome_pdf(f, pdf)
    png = os.path.join(SORTIE, f'_{nom}.png')
    E.raster(pdf, png, dpi)
    os.remove(f)
    os.remove(pdf)
    return png


def produire():
    os.makedirs(SORTIE, exist_ok=True)
    couleurs = {c: p['couleur'] for c, p in E.PRODUITS.items()}
    cartes = [E.carte_png(E.PRODUITS[code]['dossier'], mot, 700) for code, mot in CARTES_FRONTON]
    if None in cartes:
        manque = [m for (c, m), img in zip(CARTES_FRONTON, cartes) if img is None]
        sys.exit(f'rectos absents : {", ".join(manque)} (scripts/rectos_boite.py)')
    art_fronton = fronton(cartes)
    # Les joyaux s'inscrivent en dessinant : les définitions viennent après les dessins.
    levre(), dos_interieur(), flanc(True)
    defs_ = defs(couleurs)

    # Le fichier de l'imprimeur : bac, fronton, cloisons, puis les tracés seuls.
    l_b, h_b = BAC_L + 2 * FP, BAC_H + 2 * FP
    l_f, h_f = LE + 2 * FP, FRONTON_H + PATTE_H + 2 * FP
    dessin_fronton = (f'<clipPath id="clip_fronton"><rect x="{-FP}" y="{-FP}" width="{LE + 2 * FP}" '
                      f'height="{FRONTON_H + FP}"/></clipPath><g clip-path="url(#clip_fronton)">{art_fronton}</g>' + ''.join(
        f'<rect x="{px - FP}" y="{FRONTON_H}" width="{PATTE_L + 2 * FP}" height="{PATTE_H + FP}" fill="{E.NUIT_BORD}"/>'
        for px in PATTES_X))
    l_c, h_c = 3 * PROF + 2 * 24 + 2 * FP, CLOISON_H + 10 + 2 * FP
    art_bac = bac(defs_)
    pages = (E.svg(defs_, art_bac, l_b, h_b, -FP, -FP) + E.svg(defs_, dessin_fronton, l_f, h_f, -FP, -FP)
             + E.svg('', trace_bac(), l_b, h_b, -FP, -FP) + E.svg('', trace_fronton(), l_f, h_f, -FP, -FP)
             + E.svg('', trace_cloisons(), l_c, h_c, -FP, -FP))
    # Chromium prend la taille de la première page pour toutes : un PDF par format.
    for nom, contenu, l, h in (('presentoir_bac', E.svg(defs_, art_bac, l_b, h_b, -FP, -FP)
                                + E.svg('', trace_bac(), l_b, h_b, -FP, -FP), l_b, h_b),
                               ('presentoir_fronton', E.svg(defs_, dessin_fronton, l_f, h_f, -FP, -FP)
                                + E.svg('', trace_fronton(), l_f, h_f, -FP, -FP), l_f, h_f),
                               ('presentoir_cloisons', E.svg('', trace_cloisons(), l_c, h_c, -FP, -FP), l_c, h_c)):
        f = os.path.join(SORTIE, f'_{nom}.html')
        with open(f, 'w', encoding='utf-8') as fh:
            fh.write(E.page_html(contenu, l, h))
        E.chrome_pdf(f, os.path.join(SORTIE, f'{nom}.pdf'))
        os.remove(f)
    del pages

    # Le BAT : les trois pièces sur une planche, avec leurs tracés et un cartouche.
    marge = 14
    lb = marge * 3 + l_b + l_f
    hb = marge * 2 + 18 + max(h_b, h_f + marge + h_c)
    corps = (f'<rect x="0" y="0" width="{lb}" height="{hb}" fill="#ffffff"/>'
             + E.texte(marge, 13, 'Wuertduell · présentoir de comptoir (12 étuis) · BAT à plat', 7, '#222', 700, ancre='start')
             + E.texte(marge, 21, f'Bac {LE:g} × {PROF:g} mm, dos {DOS_H:g} mm, lèvre {LEVRE:g} mm · fronton {LE:g} × {FRONTON_H:g} mm · '
                       f'hauteur montée {DOS_H + FRONTON_H:g} mm · carton {EP:g} mm · rouge : coupe · bleu : rainage · '
                       'cotes à reprendre sur le gabarit du fabricant', 3.4, '#444', ancre='start')
             + f'<g transform="translate({marge + FP} {marge + 18 + FP})">{art_bac}{trace_bac()}</g>'
             + f'<g transform="translate({marge * 2 + l_b + FP} {marge + 18 + FP})">{dessin_fronton}{trace_fronton()}</g>'
             + f'<g transform="translate({marge * 2 + l_b + FP} {marge * 2 + 18 + h_f + FP})">{trace_cloisons()}</g>'
             + E.texte(marge * 2 + l_b, marge * 2 + 18 + h_f - 3, 'Trois cloisons (sans impression)', 3.6, '#444', 700, ancre='start')
             + E.texte(marge * 2 + l_b + FP + LE - 34, marge + 18 + FP + 121, 'pastille pour l’étiquette de prix',
                       3.2, '#e4007c', 700))
    f = os.path.join(SORTIE, '_bat.html')
    with open(f, 'w', encoding='utf-8') as fh:
        fh.write(E.page_html(E.svg(defs_, corps, lb, hb, 0, 0), lb, hb))
    E.chrome_pdf(f, os.path.join(SORTIE, 'presentoir_bat.pdf'))
    os.remove(f)
    E.raster(os.path.join(SORTIE, 'presentoir_bat.pdf'), os.path.join(SORTIE, 'presentoir_plat.png'), 60)

    # La maquette.
    pieces = {'fronton': piece_png(defs_, art_fronton, LE, FRONTON_H, 'fronton'),
              'levre': piece_png(defs_, levre(), LE, LEVRE, 'levre'),
              'dos_int': piece_png(defs_, dos_interieur(), LI, DOS_H, 'dos_int'),
              'flanc_g': piece_png(defs_, flanc(True), PROF, DOS_H, 'flanc_g'),
              'flanc_d': piece_png(defs_, flanc(False), PROF, DOS_H, 'flanc_d')}
    sortie = maquette(pieces)
    for p in pieces.values():
        os.remove(p)
    return sortie


if __name__ == '__main__':
    print(produire())
