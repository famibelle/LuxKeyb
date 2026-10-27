#!/usr/bin/env python3
"""La planche de la boîte de départ : les 52 cartes à jouer et les 2 cartes de
règle, d'après Dictionnaires/cartes/selection.csv (scripts/candidats_cartes.py).

Les cartes sont les **schémas** de la règle (scripts/illustrations_regle.py),
pas les fichiers d'impression : la planche sert à voir la boîte d'un coup
d'œil, à relire les mots et à vérifier la courbe de coût.

À lancer depuis la racine du dépôt :
python3 scripts/planche_boite.py [sortie.svg]
"""
import collections
import csv
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import illustrations_regle as IR  # noqa: E402

import base64  # noqa: E402

RACINE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
SELECTION = os.path.join(RACINE, 'Dictionnaires/cartes/selection.csv')
PAQUETS = {'Déieren': 'les animaux', 'Iessen': 'ce qu’on mange'}
# Le verso commun, publié dans cartes-imprimables/ ; sinon le dos
# schématique de la règle.
VERSO = os.path.join(RACINE, 'cartes-imprimables/verso/verso_300dpi.png')
GENRES = {'m.': 'Nom masculin', 'f.': 'Nom féminin', 'n.': 'Nom neutre',
          'm./n.': 'Masc. ou neutre', 'f./n.': 'Fém. ou neutre', 'm./f.': 'Masc. ou fém.'}

ECHELLE = .72
L_CARTE, H_CARTE = 150 * ECHELLE, 215 * ECHELLE
COLONNES = 13
ECART = 10
MARGE = 30


def nature(c):
    if c['nature'] == 'nom':
        return GENRES.get(c['genre'], 'Nom')
    return c['nature'].capitalize()


def une_carte(x, y, c):
    dessin = c['dessin'] if c['dessin'] in IR.MEUBLES else None
    return IR.carte(x, y, c['mot'], nature(c), c['sens'], int(c['cout']),
                    force=int(c['force']) if c['force'] != '0' else None, dessin=dessin, echelle=ECHELLE)


def carte_regle(x, y, titre, age, lignes):
    """Une carte de règle, à la place d'un joker : le métal de l'or, du texte."""
    clair, fonce, trait = IR.PALIERS[4][0]
    g = IR.uid('r')
    corps = ''.join(IR.texte(75, 64 + i * 12.5, l, 8.6, 400, '#2b2420', 'middle')
                    for i, l in enumerate(lignes))
    return f'''<g transform="translate({x} {y}) scale({ECHELLE})">
  <defs><linearGradient id="{g}" x1="0" y1="0" x2=".7" y2="1"><stop offset="0" stop-color="{clair}"/><stop offset="1" stop-color="{fonce}"/></linearGradient></defs>
  <rect x="2" y="4" width="150" height="215" rx="11" fill="#000" opacity=".18"/>
  <rect width="150" height="215" rx="11" fill="url(#{g})" stroke="{trait}" stroke-width="1.2"/>
  <rect x="7" y="7" width="136" height="201" rx="7" fill="#FBF6EA"/>
  <rect x="14" y="14" width="122" height="34" rx="6" fill="url(#{g})" stroke="{trait}" stroke-width="1"/>
  {IR.texte(75, 31, titre, 12.5, 800, trait, 'middle')}
  {IR.texte(75, 43, age, 8, 700, trait, 'middle')}
  {corps}
  <rect x="58" y="168" width="34" height="34" rx="3" fill="#fff" stroke="{trait}" stroke-width="1" stroke-dasharray="3 2"/>
  {IR.texte(75, 189, 'QR', 9, 800, trait, 'middle')}
</g>'''


REGLES = [
    ('Bataille des mots', 'dès 6 ans · 2 joueurs', [
        'Chacun retourne une carte', 'et lit son mot à voix haute.', '',
        'Le mot le plus long gagne', 'les deux cartes.', '',
        'Égalité : le premier qui dit', 'le sens les emporte.']),
    ('Wuertduell', 'dès 8 ans · 2 joueurs', [
        'Trésor : 5 cartes cachées.', 'Main : 5 cartes.', '',
        'Pioche · réserve · pose · attaque.', 'Verbe +3 · adjectif +2', 'Nombre : gratuit, +1 pièce.',
        'Gagne : prends les 5 cartes', 'du trésor adverse.']),
]


def planche():
    cartes = collections.defaultdict(list)
    with open(SELECTION, encoding='utf-8') as f:
        for c in csv.DictReader(f):
            if c['ensemble'] in PAQUETS:
                cartes[c['ensemble']].append(c)
    largeur = 2 * MARGE + COLONNES * L_CARTE + (COLONNES - 1) * ECART
    corps, y = [], MARGE
    corps.append(IR.texte(MARGE, y + 22, 'Wuertduell · la boîte de départ : 52 cartes à jouer + 2 cartes de règle',
                          26, 800))
    y += 46
    total = 0
    for nom, theme in PAQUETS.items():
        paquet = cartes[nom]
        total += len(paquet)
        courbe = collections.Counter(int(c['cout']) for c in paquet)
        natures = collections.Counter(c['nature'] for c in paquet)
        resume = (f"{len(paquet)} cartes : {natures['nom']} noms, {natures['verbe']} verbes, "
                  f"{natures['adjectif']} adjectifs, {natures['nombre']} nombres · coûts "
                  + ', '.join(f"{n} × {cout} pièce{'s' if cout > 1 else ''}" if cout else f'{n} gratuits'
                              for cout, n in sorted(courbe.items())))
        y += 24
        corps.append(IR.texte(MARGE, y, f'Paquet {nom}, {theme}', 20, 800))
        corps.append(IR.texte(MARGE + 330, y, resume, 14, 400, '#5a4e44'))
        y += 14
        for i, c in enumerate(paquet):
            ligne, col = divmod(i, COLONNES)
            corps.append(une_carte(MARGE + col * (L_CARTE + ECART), y + ligne * (H_CARTE + ECART), c))
        y += ((len(paquet) - 1) // COLONNES + 1) * (H_CARTE + ECART) + 6
    y += 24
    corps.append(IR.texte(MARGE, y, 'Les 2 cartes de règle, à la place des jokers, et le dos commun', 20, 800))
    y += 14
    for i, (titre, age, lignes) in enumerate(REGLES):
        corps.append(carte_regle(MARGE + i * (L_CARTE + ECART), y, titre, age, lignes))
    x = MARGE + 2 * (L_CARTE + ECART)
    if os.path.exists(VERSO):
        # Le PNG a son fond perdu de 3 mm : la carte finie en est le centre.
        with open(VERSO, 'rb') as f:
            png = base64.b64encode(f.read()).decode()
        fx, fy = L_CARTE * 69 / 63, H_CARTE * 94 / 88
        g = IR.uid('v')
        corps.append(f'<defs><clipPath id="{g}"><rect x="{x}" y="{y}" width="{L_CARTE}" height="{H_CARTE}" '
                     f'rx="{11 * ECHELLE}"/></clipPath></defs><g clip-path="url(#{g})"><image '
                     f'href="data:image/png;base64,{png}" x="{x - (fx - L_CARTE) / 2}" y="{y - (fy - H_CARTE) / 2}" '
                     f'width="{fx}" height="{fy}" preserveAspectRatio="none"/></g>')
    else:
        corps.append(IR.dos(x, y, ECHELLE))
    y += H_CARTE + MARGE
    assert total == 52, f'{total} cartes à jouer au lieu de 52'
    return IR.svg(round(largeur), round(y), '\n'.join(corps), 'La boîte de départ du Wuertduell : 52 cartes et 2 cartes de règle')


if __name__ == '__main__':
    sortie = sys.argv[1] if len(sys.argv) > 1 else 'planche_boite.svg'
    with open(sortie, 'w', encoding='utf-8') as f:
        f.write(planche())
    print(sortie)
