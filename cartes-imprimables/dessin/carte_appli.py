#!/usr/bin/env python3
"""La carte très rare de l'appli, portée en SVG pour l'impression.

Recopie, pour le palier très rare, le dessin de `carnet/CadreOrne.kt`
(dessinerMetal, reliefDuPlateau, moulure, ferronnerie, feuille, joyau,
plaque, sertissure, pastille, ecu, dessinerGemme, dessinerRayons,
dessinerSemis, Verre, Irisation), de `MotGrave.kt` et de la mise en page de
`CarteCarnet.complete(…, impression = true)`, en **unités de carte**
(300 × 440). Les nombres sont ceux du code ; ne pas les « arrondir ».

La moulure du cadre est calculée au pixel, comme dans l'appli : elle est
rendue en PNG à la résolution d'impression et posée sur le métal.

Publié avec la licence des visuels (LICENSE-VISUELS.md) : usage scolaire et
éducatif libre, vente interdite. Voir cartes-imprimables/README.md.
"""
import base64
import io
import math
import re

import numpy as np
from PIL import Image

L, H = 300.0, 440.0
RAYON = 18.0

# Le métal et le support du palier très rare (METAUX[3], SUPPORTS[3]).
HI, MID, LO, TRAIT, JOYAU = '#FFF0BC', '#E0B74E', '#8E6216', '#4A3208', '#C558E8'
ENCRE_SUPPORT = 'rgba(74,50,8,0.55)'          # 0x8C4A3208
PALIER = 3
BORD = 12 + PALIER * 2                        # 18

FENETRE = (32, 36, 268, 208)
PLAQUE = (28, 184, 272, 228)
GEMME = (11, 11, 65, 65)
GEMME_CENTRE = (141, 27, 159, 53)
NATURE = (96, 244, 204, 272)
PROVENANCE = (127, 369, 173, 415)
PANNEAU = (38, 262, 262, 384)
PANNEAU_TEXTE = (48, 277, 252, 378)
ECU_G = (28, 375, 80, 410)
ECU_D = (220, 375, 272, 410)
# Les écus élargis de la carte imprimée (Ornement.ECU_*_IMPRIME, 2026-10-26).
ECU_G_IMPRIME = (28, 375, 118, 410)
ECU_D_IMPRIME = (182, 375, 272, 410)
ENCRE, ENCRE_DOUCE, ENCRE_PALE = '#1B1610', '#4A4234', '#7A7160'

SANS = "font-family=\"'Roboto', 'DejaVu Sans', sans-serif\""


def hexrgb(c):
    c = c.lstrip('#')
    return tuple(int(c[i:i + 2], 16) for i in (0, 2, 4))


def melange(a, b, t):
    a, b = hexrgb(a), hexrgb(b)
    return '#%02x%02x%02x' % tuple(int(x + (y - x) * t) for x, y in zip(a, b))


def hsv(h, s, v):
    import colorsys
    r, g, b = colorsys.hsv_to_rgb((h % 360) / 360, s, v)
    return '#%02x%02x%02x' % (round(r * 255), round(g * 255), round(b * 255))


def i32(x):
    x &= 0xFFFFFFFF
    return x - (1 << 32) if x >= 1 << 31 else x


def ushr(x, n):
    return (x & 0xFFFFFFFF) >> n


def teinte_de(mot, hue_champ):
    """Ornement.teinteDe(mot, champ) : la teinte du champ, écartée de ±12°."""
    c = 7919
    for ch in mot.lower()[:3]:
        c = i32(c * 31 + ord(ch))
    q = c >> 3  # shr arithmétique
    r = int(math.fmod(q, 25))
    ecart = ((r + 25) % 25) - 12
    return ((hue_champ + ecart) % 360 + 360) % 360


_n = [0]


def uid(p):
    _n[0] += 1
    return f'{p}{_n[0]}'


# ------------------------------------------------------------------ chemins


def roundrect(x0, y0, x1, y1, r):
    return (f'M{x0 + r} {y0} H{x1 - r} A{r} {r} 0 0 1 {x1} {y0 + r} V{y1 - r} A{r} {r} 0 0 1 {x1 - r} {y1} '
            f'H{x0 + r} A{r} {r} 0 0 1 {x0} {y1 - r} V{y0 + r} A{r} {r} 0 0 1 {x0 + r} {y0} Z')


def chemin_fenetre(r=FENETRE):
    """cheminFenetre(r, arche = true)."""
    l, t, rr, b = r
    w = rr - l
    fleche = w * 0.30
    ra = 8
    return (f'M{l} {b - ra} L{l} {t + fleche} A{w / 2} {fleche} 0 0 1 {rr} {t + fleche} L{rr} {b - ra} '
            f'A{ra} {ra} 0 0 1 {rr - ra} {b} L{l + ra} {b} A{ra} {ra} 0 0 1 {l} {b - ra} Z')


def spirale(pts, cx, cy, r0, r1, de, a):
    n = max(2, int(abs(a - de) / 10))
    for i in range(n + 1):
        t = i / n
        ang = math.radians(de + (a - de) * t)
        r = r0 + (r1 - r0) * t
        pts.append(f'L{cx + r * math.cos(ang):.2f} {cy + r * math.sin(ang):.2f}')


def grad_lineaire(id_, x1, y1, x2, y2, couleurs, positions=None):
    n = len(couleurs)
    positions = positions or [i / (n - 1) for i in range(n)]
    stops = ''.join(f'<stop offset="{p}" stop-color="{c}"/>' for c, p in zip(couleurs, positions))
    return (f'<linearGradient id="{id_}" gradientUnits="userSpaceOnUse" x1="{x1}" y1="{y1}" x2="{x2}" '
            f'y2="{y2}">{stops}</linearGradient>')


_joyaux = {}


def defs_joyaux():
    """Les dégradés des joyaux, un par couleur, relatifs à chaque pierre.

    Un dégradé par perle en faisait plus de cinquante : le PDF de Chromium en
    perdait au-delà (écus, onglet et gemmes sortaient pâles à l'impression).
    """
    # Opaques : le blanc à 95 % et le noir à 55 % de l'appli, mélangés
    # d'avance à la couleur. Un dégradé transparent fait perdre au PDF de
    # Chromium (Skia) les dégradés qui le suivent : ils sortent délavés.
    return ''.join(
        f'<radialGradient id="{g}" cx=".325" cy=".3" r=".675"><stop offset="0" stop-color="{melange(c, "#ffffff", .95)}"/>'
        f'<stop offset=".42" stop-color="{c}"/><stop offset="1" stop-color="{melange(c, "#000000", .55)}"/>'
        f'</radialGradient>' for c, g in _joyaux.items())


def joyau(cx, cy, r, couleur, facettes=0):
    """Ornement.joyau : une pierre, éclat en haut à gauche, liseré clair."""
    g = _joyaux.setdefault(couleur, f'joyau{len(_joyaux)}')
    if facettes >= 3:
        pts = ' '.join(f'{cx + math.cos(-math.pi / 2 + i / facettes * 2 * math.pi) * r:.2f},'
                       f'{cy + math.sin(-math.pi / 2 + i / facettes * 2 * math.pi) * r:.2f}' for i in range(facettes))
        return (f'<polygon points="{pts}" fill="url(#{g})"/><polygon points="{pts}" fill="none" stroke="{melange(couleur, "#ffffff", .55)}" '
                f'stroke-width="{r * .14}"/>')
    return (f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="url(#{g})"/>'
            f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{melange(couleur, "#ffffff", .55)}" stroke-width="{r * .14}"/>')


def barre(d, trait, haut, bas, plein=None):
    """Ornement.barre : fer forgé en relief."""
    g = uid('b')
    s = [f'<defs>{grad_lineaire(g, 0, haut, 0, bas, [HI, MID, LO], [0, .5, 1])}</defs>']
    cap = 'stroke-linecap="round" stroke-linejoin="round"'
    s.append(f'<g transform="translate(.8 1.2)">'
             + (f'<path d="{plein}" fill="#000" fill-opacity=".22"/>' if plein else '')
             + f'<path d="{d}" fill="none" stroke="{melange(MID, "#000000", .22)}" stroke-width="{trait + .6}" {cap}/></g>')
    s.append(f'<path d="{d}" fill="none" stroke="{TRAIT}" stroke-width="{trait + 1.4}" {cap}/>')
    if plein:
        s.append(f'<path d="{plein}" fill="url(#{g})" stroke="{TRAIT}" stroke-width="1.2"/>')
    s.append(f'<path d="{d}" fill="none" stroke="url(#{g})" stroke-width="{trait}" {cap}/>')
    s.append(f'<path d="{d}" fill="none" stroke="{melange(HI, "#ffffff", .55)}" stroke-width="{trait * .3}" '
             f'transform="translate(-.25 -.45)" {cap}/>')
    return ''.join(s), g


def collier(x, y, angle, trait, grad):
    return (f'<g transform="translate({x} {y}) rotate({angle})"><rect x="-1.1" y="{-trait * 1.25}" width="2.2" '
            f'height="{trait * 2.5}" rx=".5" fill="url(#{grad})" stroke="{TRAIT}" stroke-width=".8"/></g>')


def ferronnerie():
    trait = 2.6
    tige = ['M278 93']
    spirale(tige, 277, 93, 1, 3.5, -360, 180)
    tige.append('C273.5 80 261 64 265 44')
    spirale(tige, 250, 44, 15, 2.5, 0, -540)
    crosse = ['M262 35', 'C266 32 270 30 270 26']
    spirale(crosse, 275, 26, 5, 1.2, 180, 630)
    branche = ['M203 29']
    spirale(branche, 203, 30, 1, 5.5, 630, 90)
    branche.append('C220 37 234 34 243.7 30.4')
    d = ' '.join(tige) + ' ' + ' '.join(crosse) + ' ' + ' '.join(branche)
    s, g = barre(d, trait, 18, 100)
    s += collier(262, 35, -53, trait, g) + collier(243.7, 30.4, -20, trait, g)
    perle = trait * .8
    s += joyau(250, 44, perle, HI) + joyau(275, 26, perle * .8, HI) + joyau(203, 30, perle * .8, HI)
    return s


def feuille(x, y, l, angle):
    d = f'M0 0 Q{l * .45} {-l * .38} {l} 0 Q{l * .45} {l * .38} 0 0 Z'
    return (f'<path d="{d}" fill="#fff" fill-opacity=".55" transform="translate({x + .45} {y + .85}) rotate({angle})"/>'
            f'<path d="{d}" fill="#000" fill-opacity=".3" transform="translate({x} {y}) rotate({angle})"/>')


def plaque():
    l, t, r, b = PLAQUE
    q = min((b - t) * .55, min(l, L - r) - 9)
    cy = (t + b) / 2
    d = f'M{l - q} {cy} L{l} {t} L{r} {t} L{r + q} {cy} L{r} {b} L{l} {b} Z'
    g = uid('p')
    s = [f'<defs>{grad_lineaire(g, 0, t, 0, b, [HI, MID, LO], [0, .55, 1])}</defs>',
         f'<path d="{d}" fill="#000" fill-opacity=".22" transform="translate(1.5 2.5)"/>',
         f'<path d="{d}" fill="url(#{g})" stroke="{TRAIT}" stroke-width="1.2"/>',
         f'<path d="M{l - q} {cy} L{l} {t} M{l - q} {cy} L{l} {b} M{r + q} {cy} L{r} {t} M{r + q} {cy} L{r} {b}" '
         f'stroke="{ENCRE_SUPPORT}" stroke-width="1"/>',
         f'<rect x="{l + 3.5}" y="{t + 3.5}" width="{r - l - 7}" height="{b - t - 7}" rx="2" fill="none" '
         f'stroke="{ENCRE_SUPPORT}" stroke-width=".8"/>']
    rayon = 3.6
    for x in (l + 9, r - 9):
        for y in (t + 7, b - 7):
            s.append(f'<circle cx="{x}" cy="{y}" r="{rayon * 1.3}" fill="{LO}" stroke="{TRAIT}" stroke-width=".7"/>')
            s.append(joyau(x, y, rayon, HI))
    return ''.join(s)


def sertissure(r, griffes):
    l, t, rr, b = r
    cx, cy, ry = (l + rr) / 2, (t + b) / 2, (b - t) / 2
    sx = (rr - l) / (b - t)
    g = uid('s')
    s = (f'<defs>{grad_lineaire(g, cx, cy - ry, cx, cy + ry, [HI, LO])}</defs>'
         f'<g transform="translate({cx} {cy}) scale({sx} 1) translate({-cx} {-cy})">'
         f'<circle cx="{cx}" cy="{cy}" r="{ry}" fill="url(#{g})" stroke="{TRAIT}" stroke-width="1.4"/>')
    for i in range(griffes):
        a = i / griffes * 2 * math.pi
        s += joyau(cx + math.cos(a) * ry, cy + math.sin(a) * ry, ry * .09, HI)
    return s + '</g>'


def gemme(r, creux, teinte, intensite=1.0, lum=(-.66, -.75)):
    """Ornement.dessinerGemme."""
    l, t, rr, b = r
    gx, gy = (l + rr) / 2, (t + b) / 2
    gr = (b - t) / 2 - creux
    sx = (rr - l) / (b - t)
    lerp = lambda a, b_, k: a + (b_ - a) * max(0, min(1, k))
    g = uid('g')
    d = (f'<radialGradient id="{g}" gradientUnits="userSpaceOnUse" cx="{gx - gr * .3}" cy="{gy - gr * .35}" '
         f'r="{gr * 1.4}"><stop offset="0" stop-color="{hsv(teinte, lerp(.18, .34, intensite), 1)}"/>'
         f'<stop offset=".55" stop-color="{hsv(teinte, lerp(.5, .88, intensite), .72)}"/>'
         f'<stop offset="1" stop-color="{hsv(teinte, lerp(.7, .95, intensite), .34)}"/></radialGradient>')
    a_ovale = lerp(0x58, 0xC0, intensite) / 255
    cxo, cyo = gx - gr * .28, gy - gr * .38
    ox0, oy0, ox1, oy1 = gx - gr * .74, gy - gr * .66, gx + gr * .18, gy - gr * .10
    return (f'<defs>{d}</defs><g transform="translate({gx} {gy}) scale({sx} 1) translate({-gx} {-gy})">'
            f'<circle cx="{gx}" cy="{gy}" r="{gr}" fill="url(#{g})"/>'
            f'<circle cx="{gx}" cy="{gy}" r="{gr - .6}" fill="none" stroke="{melange(hsv(teinte, lerp(.7, .95, intensite), .34), "#000000", .25)}" stroke-width="1.1"/>'
            f'<ellipse cx="{(ox0 + ox1) / 2}" cy="{(oy0 + oy1) / 2}" rx="{(ox1 - ox0) / 2}" ry="{(oy1 - oy0) / 2}" '
            f'fill="{melange(hsv(teinte, lerp(.5, .88, intensite), .72), "#ffffff", a_ovale)}" transform="rotate(-28 {cxo} {cyo})"/>'
            f'<circle cx="{gx + lum[0] * gr * .64}" cy="{gy + lum[1] * gr * .64}" r="{gr * .11}" fill="{melange(hsv(teinte, .3, 1), "#ffffff", .9)}"/>'
            f'</g>')


def chemin_ecu(r):
    l, t, rr, b = r
    h = b - t
    return f'M{l} {t} L{rr} {t} L{rr} {t + h * .5} Q{(l + rr) / 2} {b + h * .16} {l} {t + h * .5} Z'


def ecu(r):
    g = uid('e')
    l, t, rr, b = r
    d = chemin_ecu(r)
    return (f'<defs>{grad_lineaire(g, 0, t, 0, b, [HI, MID, LO], [0, .45, 1])}</defs>'
            f'<path d="{d}" fill="url(#{g})" stroke="{TRAIT}" stroke-width="1.4"/>'
            f'<path d="{d}" fill="none" stroke="{melange(MID, "#ffffff", .5)}" stroke-width=".9"/>')


def brillance():
    """La brillance de l'irisation (au repos) : une bande en travers de la
    fenêtre, du bas gauche au haut droit, blanche à 19 % au centre.

    En deux aplats et non en dégradé transparent, que le PDF de Chromium
    rend mal (voir [defs_joyaux])."""
    ax, ay, bx, by = FENETRE[0], FENETRE[3], FENETRE[2], FENETRE[1]
    vx, vy = bx - ax, by - ay
    n = math.hypot(vx, vy)
    px, py = -vy / n * 400, vx / n * 400
    s = ''
    for t0, t1, o in ((.38, .62, .06), (.45, .55, .08)):
        p0 = (ax + vx * t0, ay + vy * t0)
        p1 = (ax + vx * t1, ay + vy * t1)
        pts = [(p0[0] + px, p0[1] + py), (p1[0] + px, p1[1] + py), (p1[0] - px, p1[1] - py), (p0[0] - px, p0[1] - py)]
        s += '<polygon points="' + ' '.join(f'{x:.1f},{y:.1f}' for x, y in pts) + f'" fill="#fff" fill-opacity="{o}"/>'
    return s


def etincelle(cx, cy, r, alpha):
    d = (f'M{cx} {cy - r} Q{cx + r * .16} {cy - r * .16} {cx + r} {cy} Q{cx + r * .16} {cy + r * .16} {cx} {cy + r} '
         f'Q{cx - r * .16} {cy + r * .16} {cx - r} {cy} Q{cx - r * .16} {cy - r * .16} {cx} {cy - r} Z')
    return f'<path d="{d}" fill="#fff" fill-opacity="{alpha / 255:.3f}"/>'


def dans_arche(x, y, r):
    l, t, rr, b = r
    if x < l or x > rr or y > b:
        return False
    fleche = (rr - l) * .30
    base = t + fleche
    if y >= base:
        return True
    dx = (x - (l + rr) / 2) / ((rr - l) / 2)
    dy = (y - base) / fleche
    return dx * dx + dy * dy <= 1


def semis(mot):
    """Ornement.dessinerSemis, arithmétique 32 bits de Kotlin comprise."""
    g = 7919
    for ch in mot:
        g = i32(g * 31 + ord(ch))
    n, marge = 11, 12 + PALIER * 2 + 7
    interdits = [GEMME, PLAQUE, GEMME_CENTRE, NATURE, PANNEAU, ECU_G_IMPRIME, ECU_D_IMPRIME, PROVENANCE]
    poses = essais = 0
    s = ''
    while poses < n and essais < n * 8:
        essais += 1
        g = i32(g * 1103515245 + 12345)
        x = marge + (ushr(g, 8) % 1000) / 1000 * (L - 2 * marge)
        y = marge + (ushr(g, 18) % 1000) / 1000 * (H - 2 * marge)
        r = 2.5 + (ushr(g, 4) % 5)
        if any(x > a - r and x < c - 0 + r and y > b_ - r and y < d + r for a, b_, c, d in interdits):
            continue
        m = r + 5
        f = FENETRE
        dehors = not dans_arche(x, y, (f[0] - m, f[1] - m, f[2] + m, f[3] + m))
        dedans = dans_arche(x, y, (f[0] + m, f[1] + m, f[2] - m, f[3] - m))
        if not dehors and not dedans:
            continue
        s += etincelle(x, y, r, 90 + (ushr(g, 12) % 100))
        poses += 1
    return s


# ------------------------------------------------------------- la moulure


def moulure_png(largeur_px):
    """Ornement.moulure, au pixel : le relief du cadre, en PNG transparent."""
    u = largeur_px / L
    w, h = int(round(L * u)), int(round(H * u))
    j, i = np.mgrid[0:h, 0:w]
    x = (i + .5) / u
    y = (j + .5) / u
    lx, ly = -.61, -.79
    pente = 2.4
    clair, plafond = .65, .6

    def distance(hx, hy, r):
        dx, dy = x - L / 2, y - H / 2
        sx, sy = np.where(dx < 0, -1., 1.), np.where(dy < 0, -1., 1.)
        qx, qy = np.abs(dx) - hx + r, np.abs(dy) - hy + r
        coin = (qx > 0) & (qy > 0)
        lc = np.sqrt(np.maximum(qx, 0) ** 2 + np.maximum(qy, 0) ** 2)
        lc = np.where(lc == 0, 1, lc)
        nx = np.where(coin, sx * qx / lc, np.where(qx > qy, sx, 0.))
        ny = np.where(coin, sy * qy / lc, np.where(qx > qy, 0., sy))
        d = np.where(coin, lc - r, np.maximum(qx, qy) - r)
        return d, nx, ny

    dehors = 2.45
    d_in, gix, giy = distance(L / 2 - BORD, H / 2 - BORD, 11)
    d_out, nx, ny = distance(L / 2 - dehors, H / 2 - dehors, RAYON - dehors)
    actif = (d_in > 0) & (d_out <= .6)
    a = -d_out
    p_out = np.where(a > 0, np.exp(-np.maximum(a, 0) / pente), 1.)
    p_in = np.exp(-np.maximum(d_in, 0) / pente)
    s = p_out * (nx * lx + ny * ly) - p_in * (gix * lx + giy * ly)
    s = np.where(s > .45, s + (s - .45) * 1.2, s)
    alpha = np.where(s > 0, np.minimum(s * clair, plafond), np.minimum(-s, .8))
    alpha = np.where(actif, alpha, 0)
    alpha = (alpha * 255).astype(np.int32)
    alpha = np.where(alpha < 3, 0, alpha).astype(np.uint8)
    blanc = (s > 0).astype(np.uint8) * 255
    rgba = np.dstack([blanc, blanc, blanc, alpha])
    tampon = io.BytesIO()
    Image.fromarray(rgba, 'RGBA').save(tampon, 'PNG', optimize=True)
    return base64.b64encode(tampon.getvalue()).decode()


# ------------------------------------------------------------- le texte


def texte(x, y, s, taille, couleur, poids='normal', style='normal', ancre='middle', famille=SANS, extra=''):
    return (f'<text x="{x}" y="{y}" text-anchor="{ancre}" {famille} font-size="{taille}" font-weight="{poids}" '
            f'font-style="{style}" fill="{couleur}" {extra}>{s}</text>')


def mot_grave(mot, r, taille):
    """MotGrave : le nom gravé dans la plaque, lèvre claire, tranche sombre, creux."""
    fond = melange(LO, '#000000', .62)
    ombre = melange(LO, '#000000', .9)
    lumiere = melange(HI, '#ffffff', .85)
    l, t, rr, b = r
    cx = (l + rr) / 2
    base = (t + b) / 2 + taille * .34
    levre = max(1.5, taille * .07)
    g = uid('c')
    fam = 'font-family="EBG"'
    s = [f'<defs>{grad_lineaire(g, 0, base - taille * .75, 0, base, [ombre, ombre, fond], [0, .35, 1])}</defs>']
    k = 1.0
    while k <= levre + .01:
        s.append(texte(cx, base + k, mot, taille, lumiere, famille=fam, extra='fill-opacity=".94"'))
        k += 1
    s.append(texte(cx, base - levre * .45, mot, taille, ombre, famille=fam, extra='fill-opacity=".69"'))
    s.append(texte(cx, base, mot, taille, f'url(#{g})', famille=fam))
    return ''.join(s)


# Le type en pictogramme, comme carnet/PictoType.kt : les symboles de
# grammaire Montessori (grand triangle noir pour le nom, rond rouge pour le
# verbe, triangle bleu plus petit pour l'adjectif), « 123 » pour le nombre.
PICTO_NOIR, PICTO_ROUGE, PICTO_BLEU = '#15120E', '#D32F2F', '#2F6FD6'


def picto_type(type_, cx, haut):
    """Le pictogramme de [type_] ('nom', 'verbe', 'adjectif', 'nombre') dans la
    case de 18 unités qui commence à [haut], ou None pour un autre type."""
    base = haut + 17
    trait = f'stroke="{TRAIT}" stroke-width="1.1" stroke-linejoin="round"'

    def triangle(h, couleur):
        d = h / 1.732
        return f'<path d="M{cx} {base - h} L{cx + d:.2f} {base} L{cx - d:.2f} {base} Z" fill="{couleur}" {trait}/>'

    if type_ == 'nom':
        return triangle(16, PICTO_NOIR)
    if type_ == 'adjectif':
        return triangle(12.5, PICTO_BLEU)
    if type_ == 'verbe':
        return f'<circle cx="{cx}" cy="{haut + 9}" r="7.5" fill="{PICTO_ROUGE}" {trait}/>'
    if type_ == 'nombre':
        return texte(cx, haut + 9 + 15 * .36, '123', 15, TRAIT, 'bold')
    return None


# ------------------------------------------------------------- la carte


def carte(mot, nature, glose, exemple, pouvoir, cout, type_, serie_g, serie_d, hue_champ,
          fenetre_defs, fenetre_art, largeur_px):
    """Le SVG de la carte (viewBox 0 0 300 440), moulure au pixel pour [largeur_px]."""
    teinte = teinte_de(mot, hue_champ)
    eclat = melange(HI, '#ffffff', .7)
    fond = melange(LO, TRAIT, .35)
    s = ['<defs>', fenetre_defs,
         grad_lineaire('plateau', 0, 0, L, H, [fond, eclat, MID, LO, HI, eclat, MID, LO, eclat, MID],
                       [0, .1, .2, .32, .45, .52, .62, .75, .86, 1]),
         grad_lineaire('face', 0, BORD, 0, H - BORD, [hsv(teinte, .10, .99), hsv(teinte, .22, .88)]),
         grad_lineaire('panneau', 0, PANNEAU[1], 0, PANNEAU[3], ['#FFFCF2', '#F0EADA']),
         f'<clipPath id="clipFace"><path d="{roundrect(BORD, BORD, L - BORD, H - BORD, 11)}"/></clipPath>',
         f'<clipPath id="clipFen"><path d="{chemin_fenetre()}"/></clipPath>',
         '</defs>']

    # La face et les rayons.
    s.append(f'<path d="{roundrect(BORD, BORD, L - BORD, H - BORD, 11)}" fill="url(#face)"/>')
    cx, cy = L / 2, FENETRE[1] + (FENETRE[3] - FENETRE[1]) * .45
    rayons = ''
    for i in range(0, 24, 2):
        a0, a1 = math.radians(i * 15), math.radians(i * 15 + 15)
        rayons += (f'M{cx} {cy} L{cx + 340 * math.cos(a0):.1f} {cy + 340 * math.sin(a0):.1f} '
                   f'A340 340 0 0 1 {cx + 340 * math.cos(a1):.1f} {cy + 340 * math.sin(a1):.1f} Z ')
    s.append(f'<g clip-path="url(#clipFace)"><path d="{rayons}" fill="#FFEEBE" fill-opacity=".3"/></g>')

    # La fenêtre : le lieu, le sujet, l'irisation (au repos), la vitre.
    visible_bas = min(FENETRE[3], PLAQUE[1] + 4)
    vl, vh = FENETRE[2] - FENETRE[0], visible_bas - FENETRE[1]
    e = max(vl / 100, vh / 66)
    ox, oy = FENETRE[0] + (vl - 100 * e) / 2, FENETRE[1] + (vh - 66 * e) / 2
    s.append(f'<g clip-path="url(#clipFen)"><rect x="{FENETRE[0]}" y="{FENETRE[1]}" width="{vl}" '
             f'height="{FENETRE[3] - FENETRE[1]}" fill="#111930"/>'
             f'<g transform="translate({ox:.3f} {oy:.3f}) scale({e:.4f})">{fenetre_art}</g>'
             + brillance()
             + 
             f'<g transform="translate(0 1.4)">'
             + ''.join(f'<path d="{chemin_fenetre()}" fill="none" stroke="#000" stroke-opacity="{o}" stroke-width="{w}"/>'
                       for w, o in ((9, .063), (5, .086), (2, .133)))
             + '</g></g>')

    # Le métal : plateau, moulure, perles, contour.
    plateau = (roundrect(0, 0, L, H, 18) + ' ' + roundrect(BORD, BORD, L - BORD, H - BORD, 11))
    s.append(f'<path d="{plateau}" fill-rule="evenodd" fill="url(#plateau)"/>')
    s.append(f'<image href="data:image/png;base64,{moulure_png(largeur_px)}" x="0" y="0" width="{L}" height="{H}"/>')
    rp = .75 + (PALIER - 2) * .1
    eperle = BORD - 1.1
    rang = roundrect(eperle, eperle, L - eperle, H - eperle, 12.1)
    for r_, dx, dy, coul in ((rp, .35, .45, melange(MID, '#000000', .45)), (rp, 0, 0, MID),
                             (rp * .55, -rp * .25, -rp * .3, eclat)):
        s.append(f'<path d="{rang}" fill="none" stroke="{coul}" stroke-width="{2 * r_}" '
                 f'stroke-linecap="round" stroke-dasharray="0 3.2" transform="translate({dx} {dy})"/>')
    s.append(f'<path d="{roundrect(1.2, 1.2, L - 1.2, H - 1.2, RAYON)}" fill="none" stroke="{TRAIT}" stroke-width="2.5"/>')

    # La sertissure de l'ouverture.
    s.append(f'<path d="{chemin_fenetre()}" fill="none" stroke="{MID}" stroke-width="8"/>')
    s.append(f'<path d="{chemin_fenetre()}" fill="none" stroke="{TRAIT}" stroke-width="1.4"/>')
    s.append(f'<path d="{chemin_fenetre()}" fill="none" stroke="{melange(MID, "#ffffff", .55)}" stroke-width="1.2" '
             f'transform="translate(0 -1.5)"/>')

    # La clef de voûte.
    kx, ky = (FENETRE[0] + FENETRE[2]) / 2, FENETRE[1] + 2
    g = uid('k')
    s.append(f'<defs>{grad_lineaire(g, 0, ky - 11, 0, ky + 16, [HI, LO])}</defs>'
             f'<path d="M{kx - 18} {ky + 16} L{kx - 11} {ky - 11} L{kx + 11} {ky - 11} L{kx + 18} {ky + 16} Z" '
             f'fill="url(#{g})" stroke="{TRAIT}" stroke-width="1.2"/>')

    # Ferronnerie, acanthes, rivets.
    s.append(ferronnerie())
    depart = FENETRE[1] + 30
    pas = (H - depart - 80) / 5
    for i in range(5):
        y = depart + i * pas
        s.append(feuille(BORD / 2 - 3, y, 9, -28) + feuille(L - BORD / 2 + 3, y, 9, 208))
    for y in (H * .42, H * .70):
        s.append(joyau(BORD + 6, y, 3.2, HI) + joyau(L - BORD - 6, y, 3.2, HI))

    # Plaque, sertissures, panneau, onglet, écus, disque du médaillon.
    s.append(plaque())
    s.append(sertissure(GEMME, 8) + sertissure(GEMME_CENTRE, 6))
    l, t, r_, b = PANNEAU
    s.append(f'<rect x="{l}" y="{t}" width="{r_ - l}" height="{b - t}" rx="9" fill="url(#panneau)" '
             f'stroke="{MID}" stroke-width="2.4"/>'
             f'<rect x="{l}" y="{t}" width="{r_ - l}" height="{b - t}" rx="9" fill="none" stroke="{TRAIT}" stroke-width="1"/>'
             f'<rect x="{l + 4}" y="{t + 4}" width="{r_ - l - 8}" height="{b - t - 8}" rx="5" fill="none" '
             f'stroke="{melange("#F4EFE2", "#000000", .16)}" stroke-width=".8"/>')
    l, t, r_, b = NATURE
    rr = (b - t) / 2
    g = uid('n')
    s.append(f'<defs>{grad_lineaire(g, 0, t, 0, b, [HI, MID, LO], [0, .5, 1])}</defs>'
             f'<rect x="{l}" y="{t}" width="{r_ - l}" height="{b - t}" rx="{rr}" fill="url(#{g})" stroke="{TRAIT}" stroke-width="1.2"/>'
             f'<rect x="{l + 2.5}" y="{t + 2.5}" width="{r_ - l - 5}" height="{b - t - 5}" rx="{rr - 2.5}" fill="none" '
             f'stroke="{melange(MID, "#ffffff", .55)}" stroke-width=".9"/>')
    s.append(ecu(ECU_G_IMPRIME) + ecu(ECU_D_IMPRIME))
    s.append(texte((ECU_G_IMPRIME[0] + ECU_G_IMPRIME[2]) / 2, ECU_G_IMPRIME[1] + 9.5, 'COÛT', 11, TRAIT, 'bold'))
    s.append(texte((ECU_D_IMPRIME[0] + ECU_D_IMPRIME[2]) / 2, ECU_D_IMPRIME[1] + 9.5, 'TYPE', 11, TRAIT, 'bold'))
    pcx, pcy = (PROVENANCE[0] + PROVENANCE[2]) / 2, (PROVENANCE[1] + PROVENANCE[3]) / 2
    g = uid('m')
    s.append(f'<defs>{grad_lineaire(g, 0, pcy - 23, 0, pcy + 23, [HI, MID])}</defs>'
             f'<circle cx="{pcx}" cy="{pcy}" r="23" fill="url(#{g})" stroke="{TRAIT}" stroke-width="1.4"/>'
             f'<circle cx="{pcx}" cy="{pcy}" r="13.8" fill="none" stroke="{TRAIT}" stroke-width="1.6"/>'
             f'<circle cx="{pcx}" cy="{pcy}" r="21.3" fill="none" stroke="{melange(MID, "#ffffff", .55)}" stroke-width=".9"/>')
    for i in range(24):
        a = i / 24 * 2 * math.pi
        s.append(joyau(pcx + math.cos(a) * 22.4, pcy + math.sin(a) * 22.4, .8, HI))
    # La carte imprimée n'a pas de provenance : l'émail prend la couleur du
    # palier (Rarete.TRES_RARE.couleur) et l'étoile de l'ultra rare, au lieu
    # d'un disque vide.
    g = uid('email')
    violet = '#8E24AA'
    s.append(f'<defs><radialGradient id="{g}" gradientUnits="userSpaceOnUse" cx="{pcx - 13 * .3}" cy="{pcy - 13 * .35}" '
             f'r="{13 * 1.5}"><stop offset="0" stop-color="{melange(violet, "#ffffff", .45)}"/>'
             f'<stop offset=".5" stop-color="{violet}"/><stop offset="1" stop-color="{melange(violet, "#000000", .45)}"/>'
             f'</radialGradient></defs><circle cx="{pcx}" cy="{pcy}" r="13" fill="url(#{g})"/>'
             f'<circle cx="{pcx}" cy="{pcy}" r="12.5" fill="none" stroke="{melange(violet, "#000000", .6)}" stroke-width="1"/>')
    s.append(texte(pcx, pcy + 6.3, '✦', 17, melange(HI, '#ffffff', .3), famille="font-family=\"'DejaVu Sans'\""))

    # Les gemmes taillées, le semis.
    s.append(gemme(GEMME, 4, teinte) + gemme(GEMME_CENTRE, 3, teinte))
    s.append(semis(mot))

    # Les textes posés par CarteCarnet.complete(…, impression = true).
    s.append(texte(38, 38 + 25 * .36, str(len(mot)), 25, '#fff', 'bold'))
    s.append(mot_grave(mot, PLAQUE, 21 * 1.14))
    s.append(texte(150, 258 + 11 * .36, nature, 11, ENCRE, 'bold'))
    # Les blocs du panneau (CarteCarnet.panneau) : la glose, le pouvoir sur
    # deux lignes au plus, puis la phrase ; marges de 3 et 2 unités entre eux,
    # centrés dans la hauteur laissée au-dessus des écus.
    blocs = [([glose], 15, ENCRE, 'bold', 'normal', 0), (pouvoir, 14, ENCRE, 'normal', 'normal', 3),
             (exemple, 12, ENCRE_DOUCE, 'normal', 'italic', 2)]
    total = sum(len(lg) * t_ * 1.17 + m_ for lg, t_, _, _, _, m_ in blocs)
    y = PANNEAU_TEXTE[1] + ((ECU_G[1] - 3 - PANNEAU_TEXTE[1]) - total) / 2
    for lg, t_, coul, poids, style, m_ in blocs:
        y += m_
        for txt in lg:
            y += t_ * .93
            s.append(texte(150, y, txt, t_, coul, poids, style))
            y += t_ * .24
    # Centrés dans 384,5–402,5 comme les TextView : ligne de base à
    # 393,5 + 0,34 em (Roboto). 15 unités : « ✦✦✦✦ » tient dans les
    # 60 unités de ECU_IMPRIME_LARGEUR_TEXTE. Le type est un pictogramme
    # (picto_type) ; seul un type sans rôle au jeu reste écrit.
    s.append(texte((ECU_G_IMPRIME[0] + ECU_G_IMPRIME[2]) / 2, 393.5 + 15 * .34, cout, 15, TRAIT, 'bold'))
    cx_type = (ECU_D_IMPRIME[0] + ECU_D_IMPRIME[2]) / 2
    s.append(picto_type(type_, cx_type, 384.5) or texte(cx_type, 393.5 + 15 * .34, type_, 15, TRAIT, 'bold'))
    milieu = H - (BORD + 2.45) / 2
    s.append(texte(22, milieu + 2.7, serie_g, 7.5, TRAIT, 'bold', ancre='start'))
    s.append(texte(278, milieu + 2.7, serie_d, 7.5, TRAIT, 'bold', ancre='end'))
    s.insert(0, f'<defs>{defs_joyaux()}</defs>')
    return '\n'.join(s)
