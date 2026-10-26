#!/usr/bin/env python3
"""Dessine les illustrations de la règle du jeu (docs/regle-du-jeu.md), en SVG,
dans docs/assets/regle/.

Les silhouettes sont celles de l'application (carnet/Meubles.kt), recopiées
dans leur carré de cent unités centré sur l'origine ; le tracé d'un verbe ou
d'un adjectif suit la règle de `Trace` (carnet/CadreOrne.kt) : voyelle en haut,
consonne en bas. Le métal du cadre et le ciel suivent le coût, comme l'heure
et le métal des cartes de l'application.

Ce sont des **schémas** de la règle, pas les fichiers d'impression : la carte
imprimée reste à dessiner (voir la skill imprimeur).

À lancer depuis la racine du dépôt : python3 scripts/illustrations_regle.py
"""
import math
import os
import sys

SORTIE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'docs/assets/regle')
POLICE = "font-family=\"'Helvetica Neue', Arial, sans-serif\""

# ---------------------------------------------------------------- silhouettes


def pol(*v):
    pts = [f'{v[i]:g} {v[i + 1]:g}' for i in range(0, len(v), 2)]
    return 'M' + ' L'.join(pts) + ' Z'


def disque(cx, cy, r):
    return f'M{cx - r:g} {cy:g} A{r:g} {r:g} 0 1 0 {cx + r:g} {cy:g} A{r:g} {r:g} 0 1 0 {cx - r:g} {cy:g} Z'


def ovale(l, t, r, b):
    cx, cy, rx, ry = (l + r) / 2, (t + b) / 2, (r - l) / 2, (b - t) / 2
    return f'M{l:g} {cy:g} A{rx:g} {ry:g} 0 1 0 {r:g} {cy:g} A{rx:g} {ry:g} 0 1 0 {l:g} {cy:g} Z'


def lion():
    pts = []
    for i in range(20):
        a = i * math.pi / 10 - math.pi / 2
        r = 49 if i % 2 == 0 else 31
        pts += [math.cos(a) * r, math.sin(a) * r]
    return ' '.join([pol(*pts), disque(-13, -9, 4.5), disque(13, -9, 4.5), ovale(-16, 4, 16, 24),
                     pol(0, 17, 8, 7, -8, 7)])


MEUBLES = {
    'chat': ' '.join([pol(-34, -14, -30, -46, -8, -28), pol(34, -14, 30, -46, 8, -28),
                      'M0 -32 C26 -32 38 -14 38 4 C38 26 22 40 0 40 C-22 40 -38 26 -38 4 '
                      'C-38 -14 -26 -32 0 -32 Z']),
    'chien': ' '.join(['M0 -38 C16 -38 26 -28 28 -16 C40 -20 48 -4 42 14 C38 26 30 30 25 26 '
                       'C22 38 12 46 0 46 C-12 46 -22 38 -25 26 C-30 30 -38 26 -42 14 '
                       'C-48 -4 -40 -20 -28 -16 C-26 -28 -16 -38 0 -38 Z',
                       disque(-12, -4, 4), disque(12, -4, 4), ovale(-12, 18, 12, 34), ovale(-6, 20, 6, 28)]),
    'poisson': ' '.join(['M46 0 C30 -26 -6 -30 -22 -14 L-46 -26 L-36 0 L-46 26 L-22 14 '
                         'C-6 30 30 26 46 0 Z', disque(22, -6, 4.5)]),
    'cheval': ' '.join(['M-6 -46 L4 -30 C22 -28 38 -16 44 0 L26 8 C22 0 12 -4 4 0 '
                        'C-4 10 -8 26 -8 46 L-32 46 C-32 16 -26 -4 -14 -16 L-26 -22 L-14 -26 '
                        'L-24 -36 L-10 -32 Z', disque(16, -8, 3.5)]),
    'lion': lion(),
    'pain': ' '.join(['M-46 26 C-46 -18 -30 -34 0 -34 C30 -34 46 -18 46 26 C46 34 40 36 32 36 '
                      'L-32 36 C-40 36 -46 34 -46 26 Z']
                     + [pol(x - 12, -18, x + 2, -22, x + 5, -13, x - 9, -9) for x in (-22, 0, 22)]),
    'fromage': ' '.join([pol(-44, 30, -44, -8, 44, -30, 44, 30), disque(-20, 12, 7),
                         disque(4, -2, 5), disque(24, 14, 6)]),
}

# Les meubles de la boîte de départ : scripts/silhouettes_boite.py, qui écrit
# aussi leur code pour Meubles.kt.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import silhouettes_boite  # noqa: E402
MEUBLES.update(silhouettes_boite.chemins())
MEUBLES.update(silhouettes_boite.recopies())

# ------------------------------------------------------------- les paliers

# Métal du cadre (clair, foncé, trait) et ciel (haut, bas), par coût : l'étain
# du matin, le bronze de l'après-midi, l'argent du soir, l'or de la nuit.
PALIERS = {
    1: (('#B9C0C4', '#7E878C', '#2E3437'), ('#A6C6DC', '#F3EBDC')),
    2: (('#E3B475', '#B07C3C', '#3E2910'), ('#E2A865', '#FCE6B6')),
    3: (('#F2F6F9', '#B9C4CE', '#3D474F'), ('#463A76', '#F7B179')),
    4: (('#FFF0BC', '#E0B74E', '#4A3208'), ('#091030', '#2A3B70')),
}
SYMBOLES = {0: 'gratuit', 1: '●', 2: '◆◆', 3: '★★★', 4: '✦✦✦✦'}
VOYELLES = 'aeiouyäëéèêîïôöüû'
_ids = [0]


def uid(prefixe):
    _ids[0] += 1
    return f'{prefixe}{_ids[0]}'


def trace(mot):
    """Le monogramme d'un mot, dans la boîte 100 × 66 du paysage (règle de `Trace`)."""
    n = len(mot)
    pas = 68 / (max(n, 7) - 1)
    pts = []
    for i, ch in enumerate(mot):
        v = ch.lower() in VOYELLES
        y = 36 + (-1 if v else 1) * 13 * (0.42 + 0.58 * ((ord(ch) % 7) / 6))
        pts.append((16 + pas * i, y, v))
    d = f'M{pts[0][0]:.1f} {pts[0][1]:.1f}'
    for (ax, ay, _), (bx, by, _) in zip(pts, pts[1:]):
        d += (f' Q{ax + (bx - ax) * .55:.1f} {ay:.1f} {(ax + bx) / 2:.1f} {(ay + by) / 2:.1f}'
              f' Q{bx - (bx - ax) * .55:.1f} {by:.1f} {bx:.1f} {by:.1f}')
    noeuds = ''.join(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="2.5" fill="#fff" stroke="#3a3a3a" stroke-width=".8"/>'
                     for x, y, v in pts if v)
    return (f'<path d="{d}" fill="none" stroke="#3a3a3a" stroke-width="4.4" stroke-linecap="round" opacity=".35" '
            f'transform="translate(.6 1)"/><path d="{d}" fill="none" stroke="#fff" stroke-width="3.2" '
            f'stroke-linecap="round"/>{noeuds}')


def carte(x, y, mot, nature, sens, cout, force=None, dessin=None, echelle=1.0, sous=None):
    """Une carte de 150 × 215 unités, coin haut gauche en (x, y)."""
    force = len(mot) if force is None else force
    metal, ciel = PALIERS[max(cout, 1)]
    g, c = uid('m'), uid('c')
    if dessin:
        sujet = (f'<g transform="translate(75 70) scale(.55)"><path d="{MEUBLES[dessin]}" fill-rule="evenodd" '
                 f'fill="#3a3a3a" opacity=".35" transform="translate(2 3)"/><path d="{MEUBLES[dessin]}" '
                 f'fill-rule="evenodd" fill="#FFFDF6" stroke="#3a3a3a" stroke-width="2.2"/></g>')
    elif nature.startswith('Nombre'):
        sujet = (f'<text x="75" y="86" text-anchor="middle" {POLICE} font-size="44" font-weight="800" '
                 f'fill="#fff" stroke="#3a3a3a" stroke-width="1.5" paint-order="stroke">{sens}</text>')
    else:
        sujet = f'<g transform="translate(12 38) scale(1.26 1.1)">{trace(mot)}</g>'
    taille_mot = 15 if len(mot) <= 9 else 12
    # Un nombre n'a pas de Force : il ne combat pas.
    gemme = '' if nature.startswith('Nombre') else (
        f'<circle cx="21" cy="21" r="15" fill="#7B2FA6" stroke="{metal[2]}" stroke-width="1.5"/>'
        f'<text x="21" y="27" text-anchor="middle" {POLICE} font-size="17" font-weight="800" fill="#fff">{force}</text>')
    return f'''<g transform="translate({x} {y}) scale({echelle})">
  <defs><linearGradient id="{g}" x1="0" y1="0" x2=".7" y2="1"><stop offset="0" stop-color="{metal[0]}"/><stop offset="1" stop-color="{metal[1]}"/></linearGradient>
  <linearGradient id="{c}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{ciel[0]}"/><stop offset="1" stop-color="{ciel[1]}"/></linearGradient></defs>
  <rect x="2" y="4" width="150" height="215" rx="11" fill="#000" opacity=".18"/>
  <rect width="150" height="215" rx="11" fill="url(#{g})" stroke="{metal[2]}" stroke-width="1.2"/>
  <rect x="7" y="7" width="136" height="201" rx="7" fill="#FBF6EA"/>
  <rect x="12" y="30" width="126" height="80" rx="6" fill="url(#{c})"/>
  {sujet}
  {gemme}
  <rect x="14" y="102" width="122" height="27" rx="6" fill="url(#{g})" stroke="{metal[2]}" stroke-width="1"/>
  <text x="75" y="121" text-anchor="middle" {POLICE} font-size="{taille_mot}" font-weight="800" fill="{metal[2]}">{mot}</text>
  <rect x="33" y="135" width="84" height="16" rx="8" fill="#EADFC4"/>
  <text x="75" y="146.5" text-anchor="middle" {POLICE} font-size="8.5" font-weight="700" fill="#4b3c22">{nature}</text>
  <text x="75" y="172" text-anchor="middle" {POLICE} font-size="13" fill="#2b2420">{sens if not nature.startswith('Nombre') else 'en lettres : ' + mot}</text>
  <text x="75" y="199" text-anchor="middle" {POLICE} font-size="12" fill="{metal[2]}">{SYMBOLES[cout]}</text>
</g>'''


def dos(x, y, echelle=1.0, angle=0):
    """Le dos d'une carte : les deux angles du logo, rouge et bleu clair."""
    return f'''<g transform="translate({x} {y}) rotate({angle}) scale({echelle})">
  <rect x="2" y="4" width="150" height="215" rx="11" fill="#000" opacity=".18"/>
  <rect width="150" height="215" rx="11" fill="#fff" stroke="#9aa4ab" stroke-width="1.2"/>
  <path d="M8 8 H90 L8 120 Z" fill="#ED2939"/><path d="M142 207 H60 L142 95 Z" fill="#00A1DE"/>
  <circle cx="75" cy="107" r="22" fill="#fff" stroke="#c9cfd3"/>
  <text x="75" y="114" text-anchor="middle" {POLICE} font-size="18" font-weight="800" fill="#7a858c">LX</text>
</g>'''


def numero(x, y, n):
    return (f'<circle cx="{x}" cy="{y}" r="11" fill="#ED2939" stroke="#fff" stroke-width="2"/>'
            f'<text x="{x}" y="{y + 4.5}" text-anchor="middle" {POLICE} font-size="13" font-weight="800" fill="#fff">{n}</text>')


def texte(x, y, s, taille=15, poids=400, couleur='#2b2420', ancre='start'):
    return (f'<text x="{x}" y="{y}" text-anchor="{ancre}" {POLICE} font-size="{taille}" font-weight="{poids}" '
            f'fill="{couleur}">{s}</text>')


def svg(l, h, corps, titre):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {l} {h}" role="img" aria-label="{titre}">\n'
            f'<title>{titre}</title>\n<rect width="{l}" height="{h}" rx="18" fill="#F6F1E7"/>\n{corps}\n</svg>\n')


# ------------------------------------------------------------- les images


def carte_annotee():
    s = 1.9
    c = carte(40, 30, 'Kaz', 'Nom féminin', 'chat', 1, dessin='chat', echelle=s)
    reperes = [  # (x, y) sur la carte, en unités de carte
        (46, 14), (122, 44), (125, 116), (117, 143), (110, 172), (98, 199)]
    legendes = [
        ('La Force', 'le nombre de lettres du mot : K-a-z, 3'),
        ('Le dessin', 'il montre ce que le mot veut dire'),
        ('Le mot', 'à lire à voix haute pour jouer la carte'),
        ('La nature et le genre', 'nom, verbe, adjectif ou nombre ; féminin'),
        ('Le sens', 'la réponse, que l’adversaire peut vérifier'),
        ('Le coût', 'on compte les symboles : ● coûte 1'),
    ]
    corps = [c]
    for i, ((px, py), (titre, detail)) in enumerate(zip(reperes, legendes), 1):
        corps.append(numero(40 + px * s, 30 + py * s, i))
        y = 70 + (i - 1) * 62
        corps.append(numero(380, y - 5, i))
        corps.append(texte(400, y, titre, 17, 800))
        corps.append(texte(400, y + 21, detail, 14, 400, '#5a4e44'))
    return svg(760, 470, '\n'.join(corps), 'Une carte annotée : la Force, le dessin, le mot, la nature, le sens et le coût')


def bataille():
    corps = [
        texte(380, 42, 'Chacun retourne sa carte et lit le mot à voix haute', 17, 700, ancre='middle'),
        carte(90, 70, 'Kaz', 'Nom féminin', 'chat', 1, dessin='chat', echelle=1.15),
        carte(500, 70, 'Päerd', 'Nom neutre', 'cheval', 2, dessin='cheval', echelle=1.15),
        texte(380, 200, '3', 44, 800, '#7B2FA6', 'middle'),
        texte(380, 232, 'contre', 15, 400, '#5a4e44', 'middle'),
        texte(380, 278, '5', 44, 800, '#7B2FA6', 'middle'),
        texte(380, 360, 'Le mot le plus long gagne : Päerd emporte les deux cartes.', 17, 700, '#2b2420', 'middle'),
    ]
    return svg(760, 400, '\n'.join(corps), 'La Bataille des mots : Kaz, 3 lettres, contre Päerd, 5 lettres ; Päerd gagne')


def table():
    corps = [texte(30, 40, 'Ton côté de la table au troisième tour', 18, 800)]
    # le trésor : 5 cartes faces cachées
    for i in range(5):
        corps.append(dos(30 + i * 34, 70, .62))
    corps.append(texte(30 + 2 * 34 + 47, 225, 'Le trésor : 5 cartes', 15, 800, ancre='middle'))
    corps.append(texte(30 + 2 * 34 + 47, 244, 'que l’adversaire veut prendre', 13, 400, '#5a4e44', 'middle'))
    # la réserve : 3 cartes faces cachées, en pile décalée
    for i in range(3):
        corps.append(dos(330 + i * 16, 70 + i * 6, .62))
    corps.append(texte(395, 225, 'La réserve : 3 cartes', 15, 800, ancre='middle'))
    corps.append(texte(395, 244, '= 3 symboles à dépenser', 13, 400, '#5a4e44', 'middle'))
    # la pioche
    for i in range(4):
        corps.append(dos(560 + i * 2, 70 - i * 2, .62))
    corps.append(texte(608, 225, 'La pioche', 15, 800, ancre='middle'))
    corps.append(texte(608, 244, 'une carte par tour', 13, 400, '#5a4e44', 'middle'))
    # les noms posés, dont un avec son adjectif glissé dessous
    corps.append(carte(50, 352, 'grouss', 'Adjectif', 'grand', 2, echelle=.78))
    corps.append(texte(108, 540, 'grouss', 11, 800, '#3E2910', 'middle'))
    corps.append(carte(50, 290, 'Hond', 'Nom masculin', 'chien', 1, dessin='chien', echelle=.78))
    corps.append(carte(210, 290, 'Fësch', 'Nom masculin', 'poisson', 2, dessin='poisson', echelle=.78))
    corps.append(texte(190, 580, 'Tes noms : trois au plus', 15, 800, ancre='middle'))
    corps.append(texte(190, 599, 'grouss glissé sous Hond : +2 pour toujours', 13, 400, '#5a4e44', 'middle'))
    # la main
    for i, (m, n, s_, c, d) in enumerate([('drénken', 'Verbe', 'boire', 2, None),
                                         ('Brout', 'Nom neutre', 'pain', 2, 'pain'),
                                         ('véier', 'Nombre', '4', 0, None)]):
        corps.append(carte(420 + i * 85, 300 + abs(i - 1) * 8, m, n, s_, c, echelle=.72,
                           force=(0 if c == 0 else None), dessin=d))
    corps.append(texte(560, 580, 'Ta main', 15, 800, ancre='middle'))
    corps.append(texte(560, 599, 'tu poses ce que tes symboles permettent', 13, 400, '#5a4e44', 'middle'))
    return svg(760, 625, '\n'.join(corps),
               'La table de jeu : le trésor de 5 cartes, la réserve, la pioche, les noms posés et la main')


def combat():
    corps = [
        texte(380, 40, 'Hond attaque, Fësch bloque', 18, 800, ancre='middle'),
        carte(40, 125, 'grouss', 'Adjectif', 'grand', 2, echelle=.9),
        carte(40, 60, 'Hond', 'Nom masculin', 'chien', 1, dessin='chien', echelle=.9),
        carte(560, 60, 'Fësch', 'Nom masculin', 'poisson', 2, dessin='poisson', echelle=.9),
        '<path d="M230 170 H520" stroke="#ED2939" stroke-width="5" marker-end="url(#fl)"/>',
        '<defs><marker id="fl" viewBox="0 0 10 10" refX="7" refY="5" markerWidth="5" markerHeight="5" '
        'orient="auto"><path d="M0 0 L10 5 L0 10 Z" fill="#ED2939"/></marker></defs>',
        texte(250, 108, 'Hond', 15, 800), texte(470, 108, '4', 15, 800, ancre='end'),
        texte(250, 130, '+ grouss (adjectif)', 14, 400, '#5a4e44'), texte(470, 130, '+2', 14, 700, '#5a4e44', 'end'),
        texte(250, 152, '+ « chien ! » (le sens)', 14, 400, '#5a4e44'), texte(470, 152, '+1', 14, 700, '#5a4e44', 'end'),
        texte(250, 210, 'Force de l’attaque', 15, 800), texte(470, 210, '7', 22, 800, '#ED2939', 'end'),
        texte(250, 240, 'Fësch bloque', 15, 400), texte(470, 240, '5', 22, 800, '#00A1DE', 'end'),
        texte(108, 340, 'grouss, glissé dessous', 12, 700, '#3E2910', 'middle'),
        texte(380, 380, '7 contre 5 : Hond gagne, Fësch est défaussé.', 17, 800, ancre='middle'),
        texte(380, 406, 'Si personne n’avait bloqué, tu prenais une carte du trésor.', 14, 400,
              '#5a4e44', 'middle'),
    ]
    return svg(760, 430, '\n'.join(corps), 'Un combat : Hond, avec grouss et le sens dit, Force 7, contre Fësch, Force 5')


def main():
    os.makedirs(SORTIE, exist_ok=True)
    for nom, f in [('carte-annotee', carte_annotee), ('bataille', bataille), ('table', table), ('combat', combat)]:
        with open(os.path.join(SORTIE, f'{nom}.svg'), 'w', encoding='utf-8') as fh:
            fh.write(f())
        print(nom)


if __name__ == '__main__':
    main()
