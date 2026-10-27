#!/usr/bin/env python3
"""Le verso commun des cartes imprimées, en unités de carte (300 × 440).

Même orfèvrerie que le recto (carte_appli.py) : plateau d'or poli, moulure au
pixel, rang de perles, ferronnerie — ici aux quatre coins, pour qu'un dos ne
dise pas dans quel sens la carte est tenue. Au centre, une rosace guillochée,
le motif des billets de banque, belle et difficile à contrefaire, et un
médaillon d'or qui porte le lion du logo.

Aucune transparence sur les tracés : le PDF de Chromium délave les dégradés
qui suivent un élément semi-transparent (voir carte_appli.defs_joyaux). Les
fondus sont donc des couleurs mélangées d'avance.
"""
import base64
import io
import math

import numpy as np
from PIL import Image

import carte_appli as CA

L, H = CA.L, CA.H
NUIT_CENTRE, NUIT_BORD = '#22356f', '#0b1333'
GRAVURE = '#8c7a4c'          # l'or de la rosace, éteint sur la nuit
GRAVURE_FINE = '#5d5640'


def logo_or(chemin, cote=900):
    """Le lion du logo, recoloré pour la nuit : traits en or, angles rouge et
    bleu gardés, fond rendu transparent."""
    im = Image.open(chemin).convert('RGB').resize((cote, cote), Image.LANCZOS)
    a = np.asarray(im).astype(np.float32)
    fond = np.array([245, 246, 249], np.float32)
    lum = a.mean(axis=2)
    sat = a.max(axis=2) - a.min(axis=2)
    ecart = np.abs(a - fond).max(axis=2)
    couleur = sat > 60                                   # les deux angles du drapeau
    alpha = np.clip(ecart / 60, 0, 1)
    trait = np.array([243, 214, 140], np.float32)        # or clair
    rgb = np.where(couleur[..., None], a, trait)
    # Un trait sombre est pleinement or ; son bord antialiasé s'efface.
    alpha = np.where(couleur, alpha, np.clip((245 - lum) / 120, 0, 1))
    rgba = np.dstack([rgb, alpha * 255]).astype(np.uint8)
    tampon = io.BytesIO()
    Image.fromarray(rgba, 'RGBA').save(tampon, 'PNG', optimize=True)
    return base64.b64encode(tampon.getvalue()).decode()


def rosace(cx, cy, r, amplitude, lobes, brins, couleur, epaisseur):
    """Un guilloché : [brins] courbes r(t) = r + a·cos(lobes·t), déphasées."""
    s = ''
    n = 720
    for k in range(brins):
        phase = k / brins * 2 * math.pi / lobes
        pts = []
        for i in range(n + 1):
            t = i / n * 2 * math.pi
            rr = r + amplitude * math.cos(lobes * (t + phase))
            pts.append(f'{cx + rr * math.cos(t):.2f},{cy + rr * math.sin(t):.2f}')
        s += f'<polyline points="{" ".join(pts)}" fill="none" stroke="{couleur}" stroke-width="{epaisseur}"/>'
    return s


def texte_arc(cx, cy, r, texte, taille, couleur, haut=True, espace=1.5):
    """Un texte centré sur un arc, en haut (lu de gauche à droite) ou en bas."""
    import uuid
    i = 'arc' + uuid.uuid4().hex[:6]
    if haut:
        d = f'M{cx - r} {cy} A{r} {r} 0 0 1 {cx + r} {cy}'
    else:
        d = f'M{cx - r} {cy} A{r} {r} 0 0 0 {cx + r} {cy}'
    return (f'<defs><path id="{i}" d="{d}"/></defs><text font-family="EBG" font-size="{taille}" fill="{couleur}" '
            f'letter-spacing="{espace}"><textPath href="#{i}" startOffset="50%" text-anchor="middle">{texte}</textPath></text>')


def verso(largeur_px, logo_png):
    b = CA.BORD
    eclat = CA.melange(CA.HI, '#ffffff', .7)
    fond = CA.melange(CA.LO, CA.TRAIT, .35)
    cx, cy = L / 2, H / 2
    s = ['<defs>',
         CA.grad_lineaire('v_plateau', 0, 0, L, H, [fond, eclat, CA.MID, CA.LO, CA.HI, eclat, CA.MID, CA.LO, eclat, CA.MID],
                          [0, .1, .2, .32, .45, .52, .62, .75, .86, 1]),
         f'<radialGradient id="v_nuit" gradientUnits="userSpaceOnUse" cx="{cx}" cy="{cy}" r="250">'
         f'<stop offset="0" stop-color="{NUIT_CENTRE}"/><stop offset="1" stop-color="{NUIT_BORD}"/></radialGradient>',
         CA.grad_lineaire('v_anneau', 0, cy - 66, 0, cy + 66, [CA.HI, CA.MID, CA.LO], [0, .5, 1]),
         CA.grad_lineaire('v_anneau2', 0, cy + 60, 0, cy - 60, [CA.HI, CA.MID, CA.LO], [0, .5, 1]),
         f'<radialGradient id="v_coeur" gradientUnits="userSpaceOnUse" cx="{cx}" cy="{cy - 15}" r="70">'
         f'<stop offset="0" stop-color="#2c4486"/><stop offset="1" stop-color="#101a44"/></radialGradient>',
         f'<clipPath id="v_clipFace"><path d="{CA.roundrect(b, b, L - b, H - b, 11)}"/></clipPath>',
         '</defs>']

    # La nuit, gravée.
    s.append(f'<path d="{CA.roundrect(b, b, L - b, H - b, 11)}" fill="url(#v_nuit)"/>')
    s.append('<g clip-path="url(#v_clipFace)">')
    s.append(rosace(cx, cy, 118, 14, 18, 12, GRAVURE_FINE, .45))
    s.append(rosace(cx, cy, 92, 9, 24, 10, GRAVURE, .5))
    s.append(rosace(cx, cy, 160, 10, 30, 6, GRAVURE_FINE, .4))
    # Un semis d'étoiles, symétrique par demi-tour comme tout le dos.
    import random
    hasard = random.Random(2027)
    for _ in range(26):
        x, y = hasard.uniform(b + 8, L - b - 8), hasard.uniform(b + 8, H / 2 - 70)
        r = hasard.uniform(.6, 1.5)
        for px, py in ((x, y), (L - x, H - y)):
            s.append(f'<circle cx="{px:.1f}" cy="{py:.1f}" r="{r:.2f}" fill="#e9dcb0"/>')
    s.append('</g>')

    # Le cadre d'or : plateau, moulure, perles, contour, sertissure intérieure.
    plateau = CA.roundrect(0, 0, L, H, 18) + ' ' + CA.roundrect(b, b, L - b, H - b, 11)
    s.append(f'<path d="{plateau}" fill-rule="evenodd" fill="url(#v_plateau)"/>')
    s.append(f'<image href="data:image/png;base64,{CA.moulure_png(largeur_px)}" x="0" y="0" width="{L}" height="{H}"/>')
    rp = .85
    e = b - 1.1
    rang = CA.roundrect(e, e, L - e, H - e, 12.1)
    for r_, dx, dy, coul in ((rp, .35, .45, CA.melange(CA.MID, '#000000', .45)), (rp, 0, 0, CA.MID),
                             (rp * .55, -rp * .25, -rp * .3, eclat)):
        s.append(f'<path d="{rang}" fill="none" stroke="{coul}" stroke-width="{2 * r_}" stroke-linecap="round" '
                 f'stroke-dasharray="0 3.2" transform="translate({dx} {dy})"/>')
    s.append(f'<path d="{CA.roundrect(1.2, 1.2, L - 1.2, H - 1.2, CA.RAYON)}" fill="none" stroke="{CA.TRAIT}" stroke-width="2.5"/>')
    s.append(f'<path d="{CA.roundrect(b + 6, b + 6, L - b - 6, H - b - 6, 7)}" fill="none" stroke="{CA.MID}" stroke-width="1.6"/>')
    s.append(f'<path d="{CA.roundrect(b + 6, b + 6, L - b - 6, H - b - 6, 7)}" fill="none" stroke="{CA.TRAIT}" stroke-width=".6"/>')

    # La ferronnerie aux quatre coins (celle du recto est en haut à droite).
    fer = CA.ferronnerie()
    for transf in ('', f'translate({L} 0) scale(-1 1)', f'translate(0 {H}) scale(1 -1)', f'translate({L} {H}) scale(-1 -1)'):
        s.append(f'<g transform="{transf}">{fer}</g>')

    # Les acanthes des flancs : trois en haut, et leur reflet en bas, pour que
    # le dos reste symétrique par demi-tour. Recopiées du recto, dont le pas
    # s'arrête au-dessus des écus, elles finissaient aux deux tiers de la
    # hauteur : le bas des flancs était nu et disait dans quel sens la carte
    # était tenue (2026-10-27). Le reflet est dessiné à part, pour ne pas
    # dupliquer les identifiants des dégradés.
    depart = 66
    pas = (H - 2 * depart) / 5

    def acanthes():
        return ''.join(CA.feuille(b / 2 - 3, depart + i * pas, 9, -28) + CA.feuille(L - b / 2 + 3, depart + i * pas, 9, 208)
                       for i in range(3))

    s.append(acanthes())
    s.append(f'<g transform="translate(0 {H}) scale(1 -1)">{acanthes()}</g>')

    # Les rivets des flancs, comme au recto.
    for y in (H * .30, H * .70):
        s.append(CA.joyau(b + 6, y, 3.2, CA.HI) + CA.joyau(L - b - 6, y, 3.2, CA.HI))

    # Le médaillon : anneau d'or à grènetis, cœur de nuit, lion d'or.
    s.append(f'<circle cx="{cx}" cy="{cy}" r="68" fill="{CA.melange(NUIT_BORD, "#000000", .3)}"/>')
    s.append(f'<circle cx="{cx}" cy="{cy}" r="66" fill="url(#v_anneau)" stroke="{CA.TRAIT}" stroke-width="1.4"/>')
    s.append(f'<circle cx="{cx}" cy="{cy}" r="56" fill="url(#v_anneau2)" stroke="{CA.TRAIT}" stroke-width="1.2"/>')
    for i in range(40):
        a = i / 40 * 2 * math.pi
        s.append(CA.joyau(cx + math.cos(a) * 61, cy + math.sin(a) * 61, 1.25, CA.HI))
    s.append(f'<circle cx="{cx}" cy="{cy}" r="52" fill="url(#v_coeur)" stroke="{CA.TRAIT}" stroke-width="1.6"/>')
    s.append(rosace(cx, cy, 44, 3.5, 16, 6, '#3d5296', .35))
    s.append(f'<image href="data:image/png;base64,{logo_png}" x="{cx - 40}" y="{cy - 41}" width="80" height="80"/>')

    # Le nom du jeu, en haut et en bas du médaillon : lisible dans les deux sens.
    s.append(texte_arc(cx, cy, 75, 'WUERTDUELL', 15, CA.HI, haut=True, espace=3))
    s.append(f'<g transform="rotate(180 {cx} {cy})">{texte_arc(cx, cy, 75, "WUERTDUELL", 15, CA.HI, haut=True, espace=3)}</g>')
    s.append(CA.joyau(cx - 82, cy, 3, CA.HI) + CA.joyau(cx + 82, cy, 3, CA.HI))

    # La ligne du cadre, comme la ligne de série du recto : la licence des
    # visuels (LICENSE-VISUELS.md), que chaque carte porte depuis que les
    # fichiers sont publics. Le crédit du LOD passe sur l'emballage (CC0 ne
    # demande pas de mention).
    #
    # Posée à même le plateau, sa moitié gauche tombait sur la bande sombre du
    # dégradé, à 1,9:1 de contraste là où un texte de 4 pt en demande 7. Un
    # bandeau d'or clair réglait le contraste mais encadrait la mention comme
    # une étiquette (propriétaire, 2026-10-27) : chaque lettre a donc un fin
    # halo d'or clair, comme une gravure, et le texte reste sur l'or. Le halo
    # est fait de copies pleines décalées en cercle, comme la lèvre de
    # carte_appli.mot_grave : un texte détouré (stroke) sortirait en police
    # Type 3 du PDF, que les imprimeurs refusent.
    #
    # Elle est écrite aux deux bouts, la seconde tournée d'un demi-tour :
    # seule en bas, elle disait le sens de la carte (2026-10-27).
    milieu = H - (b + 2.45) / 2
    halo = CA.HI

    def mention():
        m = ''
        for txt, x, ancre in (('© Lëtzebuergesch Clavier', 22, 'start'),
                              ('Usage scolaire libre · vente interdite', 278, 'end')):
            for k in range(16):
                a = k / 16 * 2 * math.pi
                m += CA.texte(f'{x + .6 * math.cos(a):.2f}', f'{milieu + 2.7 + .6 * math.sin(a):.2f}', txt, 7.5,
                              halo, 'bold', ancre=ancre)
            m += CA.texte(x, milieu + 2.7, txt, 7.5, CA.TRAIT, 'bold', ancre=ancre)
        return m

    s.append(mention())
    s.append(f'<g transform="rotate(180 {cx} {cy})">{mention()}</g>')
    s.insert(0, f'<defs>{CA.defs_joyaux()}</defs>')
    return '\n'.join(s)
