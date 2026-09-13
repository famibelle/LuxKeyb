# -*- coding: utf-8 -*-
"""Planche de contrôle des meubles : les 74 silhouettes de `carnet/Meubles.kt`,
rendues dans un navigateur, sans émulateur ni appareil.

## Pourquoi cet outil existe

Les meubles sont écrits en `Path` Kotlin, et un `Path` ne se relit pas. Un
tracé peut être géométriquement juste et illisible ; il peut surtout être
**percé** sans qu'on s'en doute, parce que le remplissage est en `EVEN_ODD` et
que deux morceaux pleins qui se chevauchent y creusent un trou au lieu de se
souder. Les deux erreurs sont invisibles à la lecture du code et évidentes à
l'œil.

La première fois que cette planche a été tirée, elle a renvoyé dix-huit dessins
sur soixante-quatorze : six têtes d'animaux dont les oreilles se creusaient
dans le crâne, une main dont les doigts perçaient la paume, un banc dont les
pieds traversaient l'assise, et une dizaine d'objets simplement illisibles.
Aucun n'aurait été vu autrement qu'en installant l'application.

## Ce que la planche montre, et ce qu'elle ne montre pas

Elle **transpose** le Kotlin en SVG : mêmes coordonnées, même règle de
remplissage, même gravure — l'ombre portée en bas à droite, puis la matière
claire. Ce n'est donc pas le rendu d'Android mais son équivalent géométrique,
ce qui suffit pour juger d'une silhouette et d'un trou.

Elle ne montre ni la matière du palier, ni la partition, ni la découpe en
arche des deux paliers hauts. Pour ça, il faut l'appareil.

Usage :
    python3 Dictionnaires/apercu_meubles.py [sortie.html]
"""

import math
import os
import re
import sys

RACINE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(RACINE, 'android_keyboard', 'app', 'src', 'main', 'java',
                      'com', 'example', 'kreyolkeyboard', 'carnet', 'Meubles.kt')


class Trace:
    """Le même vocabulaire de tracé que l'objet `Meubles`, en SVG.

    Les quatre raccourcis — disque, ovale, boîte arrondie, polygone — sont
    ceux du Kotlin, et c'est le seul endroit où les deux doivent rester
    d'accord. Un raccourci ajouté là-bas et pas ici fera échouer la
    transposition bruyamment, ce qui est le bon comportement.
    """

    def __init__(self):
        self.d = []

    def moveTo(self, x, y):
        self.d.append(f'M{x:.2f} {y:.2f}')

    def lineTo(self, x, y):
        self.d.append(f'L{x:.2f} {y:.2f}')

    def cubicTo(self, a, b, c, d, e, f):
        self.d.append(f'C{a:.2f} {b:.2f} {c:.2f} {d:.2f} {e:.2f} {f:.2f}')

    def quadTo(self, a, b, c, d):
        self.d.append(f'Q{a:.2f} {b:.2f} {c:.2f} {d:.2f}')

    def close(self):
        self.d.append('Z')

    def pol(self, *v):
        self.moveTo(v[0], v[1])
        for k in range(2, len(v), 2):
            self.lineTo(v[k], v[k + 1])
        self.close()

    def disque(self, cx, cy, r):
        self.d.append(f'M{cx - r:.2f} {cy:.2f}a{r:.2f} {r:.2f} 0 1 0 {2 * r:.2f} 0'
                      f'a{r:.2f} {r:.2f} 0 1 0 {-2 * r:.2f} 0Z')

    def ovale(self, l, t, r, b):
        rx, ry = (r - l) / 2, (b - t) / 2
        cx, cy = (l + r) / 2, (t + b) / 2
        self.d.append(f'M{cx - rx:.2f} {cy:.2f}a{rx:.2f} {ry:.2f} 0 1 0 {2 * rx:.2f} 0'
                      f'a{rx:.2f} {ry:.2f} 0 1 0 {-2 * rx:.2f} 0Z')

    def arrondi(self, l, t, r, b, rayon):
        k = min(rayon, (r - l) / 2, (b - t) / 2)
        self.d.append(
            f'M{l + k:.2f} {t:.2f}H{r - k:.2f}A{k:.2f} {k:.2f} 0 0 1 {r:.2f} {t + k:.2f}'
            f'V{b - k:.2f}A{k:.2f} {k:.2f} 0 0 1 {r - k:.2f} {b:.2f}'
            f'H{l + k:.2f}A{k:.2f} {k:.2f} 0 0 1 {l:.2f} {b - k:.2f}'
            f'V{t + k:.2f}A{k:.2f} {k:.2f} 0 0 1 {l + k:.2f} {t:.2f}Z')


def en_python(corps):
    """Le corps d'un meuble Kotlin, rendu exécutable.

    Ce n'est pas un compilateur : la grammaire admise est celle que les
    meubles emploient, et rien de plus — appels de tracé, `val`, boucle
    `for (i in a..b)`, et l'expression conditionnelle. Tout le reste lèvera,
    ce qui vaut mieux qu'un dessin faux rendu silencieusement.
    """
    lignes, niveau = [], 1
    for brute in corps.split('\n'):
        L = brute.strip()
        if not L or L.startswith('//'):
            continue
        L = re.sub(r'\s*//.*$', '', L)
        if L == '}':
            niveau -= 1
            continue
        L = re.sub(r'(\d)f\b', r'\1', L)                       # 12f → 12
        L = (L.replace('Math.PI.toFloat()', 'math.pi')
              .replace('.toDouble()', '').replace('.toFloat()', '')
              .replace('Math.cos', 'math.cos').replace('Math.sin', 'math.sin'))
        L = re.sub(r'^val ', '', L)
        boucle = re.match(r'for \(i in (-?\d+)\.\.(-?\d+)\) \{', L)
        if boucle:
            lignes.append('    ' * niveau +
                          f'for i in range({boucle.group(1)}, {int(boucle.group(2)) + 1}):')
            niveau += 1
            continue
        cond = re.match(r'if \((.+?)\) (p\..+) else (p\..+)$', L)
        if cond:
            lignes.append('    ' * niveau +
                          f'({cond.group(2)}) if ({cond.group(1)}) else ({cond.group(3)})')
            continue
        L = re.sub(r'= if \((.+?)\) (.+?) else (.+)$', r'= (\2) if (\1) else (\3)', L)
        lignes.append('    ' * niveau + L)
    return '\n'.join(lignes)


def meubles():
    with open(SOURCE, encoding='utf-8') as f:
        source = f.read()
    table = source[source.index('private val TABLE'):]
    for nom, corps in re.findall(r'\n        "([a-z_]+)" to \{ p ->\n(.*?)\n        \},',
                                 table, re.S):
        contexte = {'math': math}
        exec('def _meuble(p):\n' + en_python(corps), contexte)
        t = Trace()
        contexte['_meuble'](t)
        yield nom, ' '.join(t.d)


def main():
    sortie = sys.argv[1] if len(sys.argv) > 1 else os.path.join(RACINE, 'meubles.html')
    tuiles = []
    for nom, d in meubles():
        tuiles.append(
            f'<figure><svg viewBox="-52 -52 104 104" role="img" aria-label="{nom}">'
            f'<path d="{d}" fill="#fff" fill-rule="evenodd" '
            f'transform="translate(2.2,2.8)" opacity=".30"/>'
            f'<path d="{d}" fill="#fff" fill-rule="evenodd" opacity=".93"/>'
            f'</svg><figcaption>{nom}</figcaption></figure>')
    with open(sortie, 'w', encoding='utf-8') as f:
        f.write('<!doctype html><meta charset="utf-8">'
                '<title>Les meubles du carnet</title><style>'
                'body{background:#0d0b09;margin:0;padding:14px;color:#c8bfae;'
                'font:11px/1.3 ui-sans-serif,system-ui,sans-serif}'
                'main{display:grid;grid-template-columns:repeat(auto-fill,minmax(96px,1fr));gap:10px}'
                'figure{margin:0;text-align:center}'
                'svg{width:100%;aspect-ratio:1;border-radius:6px;'
                'background:linear-gradient(150deg,#8d7f62,#5d5340)}'
                'figcaption{margin-top:3px}</style><main>'
                + ''.join(tuiles) + '</main>')
    print(f'{len(tuiles)} meubles → {sortie}')


if __name__ == '__main__':
    main()
