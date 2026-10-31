#!/usr/bin/env python3
"""Les trois meubles encore dessinés à la main : le beurre, la framboise et
l'alphabet.

Les silhouettes de la boîte de départ du Wuertduell (2026-10-26) et de son
extension Schoul et Natur (2026-10-28) étaient toutes dessinées ici. Depuis
le 2026-10-31, les meubles viennent du domaine public
(scripts/meubles_domaine.py) ; ces trois-là restent, faute d'une silhouette
libre qui vaille la leur.

Chaque silhouette suit les règles de `carnet/Meubles.kt` : un carré de cent
unités centré sur l'origine, y vers le bas, remplissage pair-impair. Une forme
posée dans une autre y creuse un trou (la lettre dans le cube) ; deux pièces
qui doivent rester pleines ne se chevauchent pas : elles sont séparées d'un
jour, à la manière d'un pictogramme (les faces de la plaquette de beurre).

Le script écrit leur code Kotlin (à recopier dans la TABLE de Meubles.kt) et
leur chemin SVG (lu par scripts/illustrations_regle.py, avec ceux de
scripts/meubles.json, pour les schémas et la planche de la boîte).

    python3 scripts/silhouettes_boite.py kotlin      # les entrées pour Meubles.kt
    python3 scripts/silhouettes_boite.py apercu x.svg
"""
import math
import sys


def f(v):
    s = f'{v:.1f}'.rstrip('0').rstrip('.')
    return '0' if s == '-0' else s


class Trace:
    """Les primitives de Meubles.kt, écrites à la fois en SVG et en Kotlin."""

    def __init__(self):
        self.svg, self.kt = [], []

    def note(self, commentaire):
        if self.kt:
            self.kt[-1] = self.kt[-1].ljust(58) + ' // ' + commentaire
        return self

    def M(self, x, y):
        self.svg.append(f'M{f(x)} {f(y)}')
        self.kt.append(f'p.moveTo({f(x)}f, {f(y)}f)')
        return self

    def L(self, x, y):
        self.svg.append(f'L{f(x)} {f(y)}')
        self.kt.append(f'p.lineTo({f(x)}f, {f(y)}f)')
        return self

    def C(self, x1, y1, x2, y2, x, y):
        self.svg.append(f'C{f(x1)} {f(y1)} {f(x2)} {f(y2)} {f(x)} {f(y)}')
        self.kt.append(f'p.cubicTo({f(x1)}f, {f(y1)}f, {f(x2)}f, {f(y2)}f, {f(x)}f, {f(y)}f)')
        return self

    def Z(self):
        self.svg.append('Z')
        self.kt.append('p.close()')
        return self

    def pol(self, *v):
        pts = [f'{f(v[i])} {f(v[i + 1])}' for i in range(0, len(v), 2)]
        self.svg.append('M' + ' L'.join(pts) + ' Z')
        self.kt.append('p.pol(' + ', '.join(f'{f(x)}f' for x in v) + ')')
        return self

    def disque(self, cx, cy, r):
        self.svg.append(f'M{f(cx - r)} {f(cy)} A{f(r)} {f(r)} 0 1 0 {f(cx + r)} {f(cy)} '
                        f'A{f(r)} {f(r)} 0 1 0 {f(cx - r)} {f(cy)} Z')
        self.kt.append(f'p.disque({f(cx)}f, {f(cy)}f, {f(r)}f)')
        return self

    def ovale(self, l, t, r, b):
        cx, cy, rx, ry = (l + r) / 2, (t + b) / 2, (r - l) / 2, (b - t) / 2
        self.svg.append(f'M{f(l)} {f(cy)} A{f(rx)} {f(ry)} 0 1 0 {f(r)} {f(cy)} '
                        f'A{f(rx)} {f(ry)} 0 1 0 {f(l)} {f(cy)} Z')
        self.kt.append(f'p.ovale({f(l)}f, {f(t)}f, {f(r)}f, {f(b)}f)')
        return self

    def arrondi(self, l, t, r, b, k):
        self.svg.append(f'M{f(l + k)} {f(t)} L{f(r - k)} {f(t)} A{f(k)} {f(k)} 0 0 1 {f(r)} {f(t + k)} '
                        f'L{f(r)} {f(b - k)} A{f(k)} {f(k)} 0 0 1 {f(r - k)} {f(b)} L{f(l + k)} {f(b)} '
                        f'A{f(k)} {f(k)} 0 0 1 {f(l)} {f(b - k)} L{f(l)} {f(t + k)} A{f(k)} {f(k)} 0 0 1 {f(l + k)} {f(t)} Z')
        self.kt.append(f'p.arrondi({f(l)}f, {f(t)}f, {f(r)}f, {f(b)}f, {f(k)}f)')
        return self


# ------------------------------------------------------ l'outillage
# Un arc et les trois lettres ne sont que des polygones et des courbes
# calculés ici, écrits avec les primitives de Trace.

def arc(cx, cy, r, a0, a1, n=24):
    """Les points d'un arc de cercle, de a0 à a1 degrés (y vers le bas)."""
    return [(cx + r * math.cos(math.radians(a0 + (a1 - a0) * k / n)),
             cy + r * math.sin(math.radians(a0 + (a1 - a0) * k / n))) for k in range(n + 1)]


def lettre_A(t, cx, cy):
    t.pol(cx - 13, cy + 13, cx - 3.5, cy - 13, cx + 3.5, cy - 13, cx + 13, cy + 13, cx + 6.6, cy + 13,
          cx + 4.7, cy + 7.5, cx - 4.7, cy + 7.5, cx - 6.6, cy + 13)
    t.pol(cx - 2.9, cy + 2, cx, cy - 6.5, cx + 2.9, cy + 2)


def lettre_B(t, cx, cy):
    t.M(cx - 11, cy - 13).L(cx + 2, cy - 13).C(cx + 9, cy - 13, cx + 11.5, cy - 9.5, cx + 11.5, cy - 6.5) \
        .C(cx + 11.5, cy - 3.5, cx + 10, cy - 1.5, cx + 8, cy - 0.5) \
        .C(cx + 11, cy + 0.5, cx + 13, cy + 3, cx + 13, cy + 6.5) \
        .C(cx + 13, cy + 10.5, cx + 9.5, cy + 13, cx + 3, cy + 13).L(cx - 11, cy + 13).Z()
    t.M(cx - 4.6, cy - 7.8).L(cx + 1.5, cy - 7.8).C(cx + 4.4, cy - 7.8, cx + 5.6, cy - 6.6, cx + 5.6, cy - 5) \
        .C(cx + 5.6, cy - 3.3, cx + 4.4, cy - 2.4, cx + 1.5, cy - 2.4).L(cx - 4.6, cy - 2.4).Z()
    t.M(cx - 4.6, cy + 2.6).L(cx + 2.5, cy + 2.6).C(cx + 5.4, cy + 2.6, cx + 6.8, cy + 4, cx + 6.8, cy + 5.4) \
        .C(cx + 6.8, cy + 6.8, cx + 5.4, cy + 7.9, cx + 2.5, cy + 7.9).L(cx - 4.6, cy + 7.9).Z()


def lettre_C(t, cx, cy):
    exterieur, interieur = arc(cx, cy, 13.5, 40, 320, 28), arc(cx, cy, 7.4, 320, 40, 28)
    t.pol(*[c for p in exterieur + interieur for c in p])


SILHOUETTES = {}


def silhouette(nom, mot):
    """Enregistre une silhouette."""
    def enregistrer(fonction):
        t = Trace()
        fonction(t)
        SILHOUETTES[nom] = (mot, t)
        return fonction
    return enregistrer


# --------------------------------------------------- les trois dessins

@silhouette('beurre', 'Botter')
def _(t):
    # La plaquette en perspective sur son assiette : trois faces séparées
    # d'un jour, comme le fromage a ses trous.
    t.pol(-36, -7, -12, -22, 38, -22, 14, -7).note('le dessus')
    t.pol(-36, -4, 13, -4, 13, 22, -36, 22).note('la face')
    t.pol(16, -5, 38, -19, 38, 7, 16, 21).note('le côté')
    t.ovale(-46, 25, 46, 37).note("l'assiette")


@silhouette('framboise', 'Hammbier')
def _(t):
    # Les grains, tangents sans se toucher : le pair-impair creuserait leurs
    # recouvrements.
    r, dx, dy = 6.6, 14.2, 12.4
    rangees = [(-16, 4), (-16 + dy, 5), (-16 + 2 * dy, 4), (-16 + 3 * dy, 3), (-16 + 4 * dy, 2)]
    for y, n in rangees:
        for k in range(n):
            t.disque((k - (n - 1) / 2) * dx, y, r)
    t.note('les grains')
    pts = []
    for i in range(10):
        a = i * math.pi / 5 - math.pi / 2
        rr = 15 if i % 2 == 0 else 5
        pts += [math.cos(a) * rr, -32 + math.sin(a) * rr * .55]
    t.pol(*pts).note('le calice')
    t.pol(-1.5, -38, 1.5, -38, 3, -48, 0, -48).note('la queue')


@silhouette('alphabet', 'Alphabet')
def _(t):
    # Les cubes de la classe des petits, A sur B et C.
    t.arrondi(-20, -44, 20, -4, 4).note('les trois cubes')
    t.arrondi(-44, 2, -4, 42, 4)
    t.arrondi(4, 2, 44, 42, 4)
    lettre_A(t, 0, -24)
    t.note('A, B, C, en réserve')
    lettre_B(t, -24, 22)
    lettre_C(t, 24, 22)


# ------------------------------------------------------------- sorties

def kotlin():
    l = []
    for nom, (mot, t) in SILHOUETTES.items():
        l.append(f'        "{nom}" to {{ p ->                                   // {mot}'.rstrip())
        l += ['            ' + x for x in t.kt]
        l.append('        },')
    return '\n'.join(l)


def chemins():
    """nom → chemin SVG, pour illustrations_regle.MEUBLES."""
    return {nom: ' '.join(t.svg) for nom, (_, t) in SILHOUETTES.items()}


def apercu():
    cases = []
    for i, (nom, (mot, t)) in enumerate(SILHOUETTES.items()):
        x, y = (i % 6) * 130 + 65, (i // 6) * 150 + 70
        cases.append(f'<g transform="translate({x} {y})"><rect x="-55" y="-55" width="110" height="110" rx="8" '
                     f'fill="#22356f"/><path d="{" ".join(t.svg)}" fill-rule="evenodd" fill="#FFFDF6"/>'
                     f'<text y="72" text-anchor="middle" font-family="sans-serif" font-size="13">{mot} · {nom}</text></g>')
    h = ((len(SILHOUETTES) - 1) // 6 + 1) * 150 + 10
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 780 {h}" width="780" height="{h}">'
            f'<rect width="780" height="{h}" fill="#f3f1ea"/>{"".join(cases)}</svg>')


if __name__ == '__main__':
    if sys.argv[1:2] == ['kotlin']:
        print(kotlin())
    elif sys.argv[1:2] == ['apercu']:
        open(sys.argv[2], 'w').write(apercu())
    else:
        sys.exit(__doc__)
