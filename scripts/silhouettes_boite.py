#!/usr/bin/env python3
"""Les silhouettes des noms du Wuertduell qui n'en avaient pas : la boîte de
départ (2026-10-26), puis l'extension Schoul et Natur (2026-10-28).

Chaque silhouette suit les règles de `carnet/Meubles.kt` : un carré de cent
unités centré sur l'origine, y vers le bas, remplissage pair-impair. Une forme
posée dans une autre y creuse un trou (l'œil, la rayure) ; deux pièces qui
doivent rester pleines ne se chevauchent pas : elles sont séparées d'un jour
de deux unités, à la manière d'un pictogramme (l'aile et le corps, la patte et
le thorax).

Ce fichier est la source des nouveaux meubles : il écrit leur code Kotlin
(à recopier dans Meubles.kt) et leur chemin SVG (lu par
scripts/illustrations_regle.py pour les schémas et la planche de la boîte).

    python3 scripts/silhouettes_boite.py kotlin [boite|extension]   # le bloc pour Meubles.kt
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

    def tranches(self, cx, cy, rx, ry, coupes, jour):
        """Une ellipse coupée en tranches verticales par des jours : les rayures
        d'une abeille, en pochoir. Chaque tranche est un polygone qui suit
        l'ellipse."""
        bords = [cx - rx] + [x for c in coupes for x in (c - jour / 2, c + jour / 2)] + [cx + rx]
        for i in range(0, len(bords), 2):
            x0, x1 = bords[i], bords[i + 1]
            haut, bas = [], []
            n = 8
            for k in range(n + 1):
                x = x0 + (x1 - x0) * k / n
                d = ry * math.sqrt(max(0.0, 1 - ((x - cx) / rx) ** 2))
                haut.append((x, cy - d))
                bas.append((x, cy + d))
            pts = haut + bas[::-1]
            self.pol(*[c for p in pts for c in p])
        return self


def symetrique(t, *v):
    """Un polygone et son reflet par rapport à l'axe vertical."""
    t.pol(*v)
    t.pol(*[(-c if i % 2 == 0 else c) for i, c in enumerate(v)])
    return t


# ---------------------------------------------- l'outillage de l'extension
# Tout ce qui suit s'écrit avec les primitives de Trace : un arc, un trait ou
# une rotation ne sont que des polygones et des courbes calculés ici.

def arc(cx, cy, r, a0, a1, n=24):
    """Les points d'un arc de cercle, de a0 à a1 degrés (y vers le bas)."""
    return [(cx + r * math.cos(math.radians(a0 + (a1 - a0) * k / n)),
             cy + r * math.sin(math.radians(a0 + (a1 - a0) * k / n))) for k in range(n + 1)]


def trait(t, pts, w):
    """Un trait d'épaisseur w le long d'une ligne brisée, en un seul polygone :
    la tige, la jambe du compas, le point d'interrogation."""
    g, d = [], []
    for i, (x, y) in enumerate(pts):
        a, b = pts[max(i - 1, 0)], pts[min(i + 1, len(pts) - 1)]
        dx, dy = b[0] - a[0], b[1] - a[1]
        n = math.hypot(dx, dy)
        nx, ny = -dy / n * w / 2, dx / n * w / 2
        g.append((x + nx, y + ny))
        d.append((x - nx, y - ny))
    return t.pol(*[c for p in g + d[::-1] for c in p])


class Tourne:
    """Les primitives, tournées de `angle` degrés autour de l'origine puis
    posées en (tx, ty) : le crayon et le tube couchés en biais."""

    def __init__(self, t, angle, tx=0, ty=0):
        self.t, self.tx, self.ty = t, tx, ty
        self.c, self.s = math.cos(math.radians(angle)), math.sin(math.radians(angle))

    def p(self, x, y):
        return x * self.c - y * self.s + self.tx, x * self.s + y * self.c + self.ty

    def pol(self, *v):
        self.t.pol(*[c for i in range(0, len(v), 2) for c in self.p(v[i], v[i + 1])])
        return self

    def M(self, x, y):
        self.t.M(*self.p(x, y))
        return self

    def L(self, x, y):
        self.t.L(*self.p(x, y))
        return self

    def C(self, x1, y1, x2, y2, x, y):
        self.t.C(*self.p(x1, y1), *self.p(x2, y2), *self.p(x, y))
        return self

    def Z(self):
        self.t.Z()
        return self

    def arrondi(self, l, t, r, b, k):
        """Le rectangle arrondi, en courbes : addRoundRect ne tourne pas."""
        q = k * 0.4477
        return self.M(l + k, t).L(r - k, t).C(r - q, t, r, t + q, r, t + k).L(r, b - k) \
            .C(r, b - q, r - q, b, r - k, b).L(l + k, b).C(l + q, b, l, b - q, l, b - k) \
            .L(l, t + k).C(l, t + q, l + q, t, l + k, t).Z()

    def note(self, commentaire):
        self.t.note(commentaire)
        return self


def croix(t, cx, cy, l, e, angle=0):
    """Une croix d'un seul tenant (le plus, le fois) : deux barres posées
    l'une sur l'autre se creuseraient au croisement."""
    pts = [(-e, -l), (e, -l), (e, -e), (l, -e), (l, e), (e, e), (e, l), (-e, l), (-e, e), (-l, e), (-l, -e), (-e, -e)]
    c, s = math.cos(math.radians(angle)), math.sin(math.radians(angle))
    t.pol(*[v for x, y in pts for v in (cx + x * c - y * s, cy + x * s + y * c)])


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


def inter(p1, p2, p3, p4):
    """Le point où la droite p1p2 coupe la droite p3p4."""
    (x1, y1), (x2, y2), (x3, y3), (x4, y4) = p1, p2, p3, p4
    d = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
    a, b = x1 * y2 - y1 * x2, x3 * y4 - y3 * x4
    return (a * (x3 - x4) - (x1 - x2) * b) / d, (a * (y3 - y4) - (y1 - y2) * b) / d


def sur(p, q, y):
    """Le point du segment pq à la hauteur y."""
    return p[0] + (q[0] - p[0]) * (y - p[1]) / (q[1] - p[1]), y


def union_disques(cx, cy, centres, rayon, n=96):
    """Le contour de plusieurs disques fondus en un seul (les pétales) : pour
    chaque direction, le plus lointain des bords."""
    pts = []
    for k in range(n):
        a = 2 * math.pi * k / n
        ux, uy = math.cos(a), math.sin(a)
        r = max(px * ux + py * uy + math.sqrt(max(0.0, rayon ** 2 - (px * px + py * py) + (px * ux + py * uy) ** 2))
                for px, py in centres)
        pts += [cx + r * ux, cy + r * uy]
    return pts


SILHOUETTES = {}
JEUX = {}


def silhouette(nom, mot, jeu='boite'):
    def enregistrer(fonction):
        t = Trace()
        fonction(t)
        SILHOUETTES[nom] = (mot, t)
        JEUX[nom] = jeu
        return fonction
    return enregistrer


# --------------------------------------------------------------- les bêtes

@silhouette('abeille', 'Beie')
def _(t):
    # Le corps rond coupé de deux rayures, la tête à part, deux ailes rondes.
    t.tranches(6, 10, 28, 15, [-2, 14], 5).note('le corps et ses rayures')
    t.disque(-33, 9, 9).note('la tête')
    t.disque(-36, 6, 2.6).note("l'œil")
    t.ovale(-18, -38, 2, -10).note('les ailes')
    t.ovale(6, -40, 30, -10)
    t.pol(35, 7, 47, 10, 35, 13).note('le dard')
    t.pol(-38, -2, -46, -18, -43, -19, -35, -3).note('les antennes')
    t.pol(-31, -3, -32, -20, -29, -20, -28, -3)


@silhouette('fourmi', 'Ameis')
def _(t):
    # Tête, thorax, abdomen en trois pièces ; six pattes coudées.
    t.disque(-34, -6, 8).note('la tête')
    t.ovale(-24, -12, -6, 2).note('le thorax')
    t.ovale(-3, -18, 40, 14).note("l'abdomen")
    for x0, x1, x2 in ((-21, -30, -40), (-15, -16, -20), (-9, 2, 10)):
        t.pol(x0 - 1.5, 4, x0 + 1.5, 4, x1 + 1.5, 22, x2 + 1, 33, x2 - 2, 33, x1 - 1.5, 23)
    t.note('les pattes')
    t.pol(-37, -15, -41, -28, -50, -32, -50, -29, -43, -26, -40, -15).note('les antennes')
    t.pol(-32, -15, -30, -30, -38, -40, -36, -41, -28, -31, -30, -15)


@silhouette('guepe', 'Harespel')
def _(t):
    # La taille de guêpe : un thorax, un jour, un abdomen en pointe rayé.
    t.disque(-38, 0, 7).note('la tête')
    t.disque(-41, -2, 1.8).note("l'œil")
    t.ovale(-29, -8, -9, 8).note('le thorax')
    t.M(-6, 0).C(-4, -12, 18, -14, 30, -8).C(38, -4, 44, 0, 48, 4) \
        .C(40, 8, 34, 14, 26, 15).C(12, 18, -4, 12, -6, 0).Z().note("l'abdomen")
    for x, h in ((5, 9), (15, 8), (25, 6)):
        t.pol(x, -h, x + 4, -h, x + 4, h + 2, x, h + 2)
    t.note('ses rayures')
    t.ovale(-24, -32, 16, -14).note("l'aile")
    t.pol(-42, -8, -48, -22, -45, -23, -39, -9).note("l'antenne")
    for x in (-24, -18, -12):
        t.pol(x, 10, x + 2, 10, x - 2, 24, x - 4, 24)
    t.note('les pattes')


@silhouette('dauphin', 'Delfin')
def _(t):
    # En saut : le dos en arc, le front bombé (le melon) au-dessus d'un bec
    # court, et l'aileron recourbé vers l'arrière. Droit et pointu, c'était un
    # requin.
    t.M(-48, 14).C(-44, 10, -38, 8, -33, 7).note('le bec') \
        .C(-32, -2, -26, -8, -18, -10).note('le melon') \
        .C(-8, -14, 0, -15, 6, -14) \
        .C(8, -24, 14, -32, 22, -34).C(18, -26, 18, -18, 21, -11).note("l'aileron, recourbé") \
        .C(30, -6, 36, 2, 39, 10) \
        .C(42, 4, 46, 0, 49, -2).C(48, 8, 47, 16, 49, 26).C(44, 22, 40, 20, 36, 19).note('la queue') \
        .C(28, 16, 14, 14, 2, 14) \
        .C(-2, 20, -8, 26, -14, 28).C(-13, 22, -12, 18, -12, 14).note('la nageoire') \
        .C(-26, 15, -38, 17, -48, 16).Z()
    t.disque(-25, 3, 2.6).note("l'œil")
    t.M(-46, 14.5).C(-40, 12, -34, 12, -30, 13).C(-34, 13.6, -40, 14, -46, 15.5).Z().note('la bouche')


@silhouette('veau', 'Kallef')
def _(t):
    # Une tête de face, sans cornes (le bœuf les a), oreilles à l'horizontale.
    t.M(0, -34).C(18, -34, 26, -22, 26, -6).C(26, 12, 20, 22, 19, 30) \
        .C(16, 44, -16, 44, -19, 30).C(-20, 22, -26, 12, -26, -6) \
        .C(-26, -22, -18, -34, 0, -34).Z().note('la tête')
    symetrique(t, -28, -16, -48, -24, -46, -12, -28, -6).note('les oreilles')
    t.disque(-10, -8, 4).note('les yeux')
    t.disque(10, -8, 4)
    t.disque(-7, 31, 3).note('les naseaux')
    t.disque(7, 31, 3)
    t.pol(-7, -35, 0, -44, 7, -35).note('le toupet')


@silhouette('elephant', 'Elefant')
def _(t):
    t.M(40, -4).C(40, -22, 26, -28, 8, -28).note('le dos') \
        .C(-4, -28, -12, -27, -18, -25) \
        .C(-30, -32, -46, -24, -44, -6).note('le front') \
        .L(-46, 28).C(-46, 34, -38, 36, -37, 30).L(-38, 24).note('la trompe') \
        .C(-37, 12, -33, 6, -26, 6) \
        .L(-23, 18).L(-23, 42).L(-11, 42).L(-11, 22).note('les pattes') \
        .C(0, 24, 12, 24, 20, 22).L(20, 42).L(32, 42).L(32, 22) \
        .C(38, 18, 40, 10, 40, -4).Z()
    t.M(-22, -18).C(-10, -22, -4, -10, -6, 2).C(-8, 8, -14, 10, -20, 6) \
        .C(-24, -2, -26, -12, -22, -18).Z().note("l'oreille, en réserve")
    t.disque(-33, -12, 2.6).note("l'œil")
    t.pol(42, -2, 48, 12, 45, 13, 41, 3).note('la queue')


@silhouette('crocodile', 'Krokodil')
def _(t):
    t.M(-49, 3).L(-30, -2).note('la mâchoire') \
        .C(-26, -8, -19, -9, -14, -6).note("l'œil, en bosse") \
        .L(-8, -7).L(-5, -12).L(-2, -7).L(3, -12).L(6, -7).L(11, -12) \
        .L(14, -7).L(19, -12).L(22, -7).L(27, -11).L(30, -6).note('les écailles') \
        .C(38, -6, 44, -4, 49, -1).note('la queue') \
        .C(42, 4, 32, 6, 24, 6) \
        .L(24, 15).L(15, 15).L(15, 8).note('les pattes') \
        .L(-8, 8).L(-8, 15).L(-17, 15).L(-17, 7) \
        .C(-27, 9, -39, 10, -49, 8).Z()
    t.disque(-19, -5, 2.2).note("l'œil")
    t.pol(-46, 5, -26, 4, -26, 5.6, -46, 6.4).note('la gueule')


@silhouette('hirondelle', 'Schmuewel')
def _(t):
    # Vue de dessous : les ailes en faux et la queue fourchue, qui la séparent
    # du merle perché.
    t.M(0, -34).C(6, -34, 8, -28, 8, -21) \
        .C(22, -19, 38, -9, 49, 8).C(34, 2, 20, 2, 8, 6).note("l'aile") \
        .L(8, 14).L(19, 46).L(0, 25).L(-19, 46).L(-8, 14).note('la queue fourchue') \
        .L(-8, 6).C(-20, 2, -34, 2, -49, 8) \
        .C(-38, -9, -22, -19, -8, -21).C(-8, -28, -6, -34, 0, -34).Z()


@silhouette('lapin', 'Kanéngchen')
def _(t):
    # Les longues oreilles dressées : le chat a des pointes, le lapin des
    # cuillères.
    t.ovale(-21, -50, -7, -12).note('les oreilles')
    t.ovale(7, -50, 21, -12)
    t.ovale(-17, -44, -11, -18).note('leur creux')
    t.ovale(11, -44, 17, -18)
    t.M(0, -10).C(18, -10, 28, 4, 28, 16).C(28, 32, 16, 42, 0, 42) \
        .C(-16, 42, -28, 32, -28, 16).C(-28, 4, -18, -10, 0, -10).Z().note('la tête')
    t.disque(-11, 10, 4).note('les yeux')
    t.disque(11, 10, 4)
    t.pol(-4, 22, 4, 22, 0, 27).note('le nez')


# ------------------------------------------------------------- la table

@silhouette('oignon', 'Ënn')
def _(t):
    t.M(0, -42).C(4, -32, 8, -26, 14, -22).C(30, -12, 38, 2, 36, 16) \
        .C(34, 32, 18, 40, 0, 40).C(-18, 40, -34, 32, -36, 16) \
        .C(-38, 2, -30, -12, -14, -22).C(-8, -26, -4, -32, 0, -42).Z().note('le bulbe')
    t.M(-3, -16).C(-15, -6, -19, 12, -12, 32).L(-9, 32).C(-14, 12, -11, -6, -1, -16).Z().note('ses pelures')
    t.M(3, -16).C(15, -6, 19, 12, 12, 32).L(9, 32).C(14, 12, 11, -6, 1, -16).Z()
    for x in (-8, 0, 8):
        t.pol(x - 1, 42, x + 1, 42, x + 1.5 * (x / 8 if x else 0) + 1, 49, x + 1.5 * (x / 8 if x else 0) - 1, 49)
    t.note('les racines')


@silhouette('tomate', 'Tomat')
def _(t):
    t.ovale(-42, -24, 42, 40).note('le fruit')
    pts = []
    for i in range(10):
        a = i * math.pi / 5 - math.pi / 2
        r = 17 if i % 2 == 0 else 6
        pts += [math.cos(a) * r, -11 + math.sin(a) * r * .6]
    t.pol(*pts).note('le calice, en réserve')
    t.pol(-2, -27, 2, -27, 4, -40, 0, -40).note('la queue')


@silhouette('prune', 'Promm')
def _(t):
    t.M(0, -26).C(22, -26, 32, -6, 30, 12).C(28, 32, 16, 44, 0, 44) \
        .C(-16, 44, -30, 32, -30, 12).C(-30, -8, -20, -26, 0, -26).Z().note('le fruit')
    t.M(3, -20).C(15, -4, 15, 22, 5, 38).L(3, 38).C(12, 22, 12, -4, 1, -20).Z().note('son sillon')
    t.pol(-1, -29, 2, -29, 6, -44, 3, -45).note('la queue')
    t.M(8, -36).C(16, -48, 32, -46, 38, -40).C(30, -32, 16, -30, 8, -36).Z().note('la feuille')


@silhouette('banane', 'Banann')
def _(t):
    t.M(-40, -32).L(-33, -36).note('la queue') \
        .C(-28, -10, -8, 12, 20, 16).C(32, 18, 42, 14, 47, 8) \
        .C(42, 26, 24, 34, 6, 32).C(-20, 30, -38, 6, -43, -28).Z()
    t.M(-34, -18).C(-26, 4, -8, 20, 18, 23).C(-6, 23, -26, 8, -34, -18).Z().note("l'arête")


@silhouette('beurre', 'Botter')
def _(t):
    # La plaquette en perspective sur son assiette : trois faces séparées
    # d'un jour, comme le fromage a ses trous.
    t.pol(-36, -7, -12, -22, 38, -22, 14, -7).note('le dessus')
    t.pol(-36, -4, 13, -4, 13, 22, -36, 22).note('la face')
    t.pol(16, -5, 38, -19, 38, 7, 16, 21).note('le côté')
    t.ovale(-46, 25, 46, 37).note("l'assiette")


@silhouette('crepe', 'Paangech')
def _(t):
    t.arrondi(-8, -18, 8, -11, 2).note('la noix de beurre')
    t.arrondi(-36, -8, 36, 3, 5.5).note('la pile')
    t.arrondi(-40, 6, 40, 17, 5.5)
    t.arrondi(-38, 20, 38, 31, 5.5)
    t.ovale(-48, 34, 48, 44).note("l'assiette")


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


@silhouette('petit_pain', 'Bréitchen')
def _(t):
    # Rond, et une seule grigne : le pain a la miche et trois entailles.
    t.M(-40, 20).C(-42, -8, -22, -24, 0, -24).C(22, -24, 42, -8, 40, 20) \
        .C(40, 28, 34, 30, 26, 30).L(-26, 30).C(-34, 30, -40, 28, -40, 20).Z().note('le pain')
    t.M(-26, 2).C(-12, -12, 12, -12, 26, 2).C(12, -5, -12, -5, -26, 2).Z().note('la grigne')


@silhouette('noisette', 'Hieselnoss')
def _(t):
    t.M(0, -13).C(22, -13, 32, 3, 30, 19).C(28, 37, 14, 45, 0, 47) \
        .C(-14, 45, -28, 37, -30, 19).C(-32, 3, -22, -13, 0, -13).Z().note('la noix')
    # La cupule en feuilles festonnées qui retombent sur les flancs : en
    # dents de scie, elle passait pour une couronne.
    t.M(-28, -14).C(-38, -18, -40, -28, -34, -34).C(-30, -30, -26, -30, -24, -34) \
        .C(-20, -42, -12, -42, -8, -36).C(-4, -44, 4, -44, 8, -36) \
        .C(12, -42, 20, -42, 24, -34).C(26, -30, 30, -30, 34, -34) \
        .C(40, -28, 38, -18, 28, -14).C(16, -19, -16, -19, -28, -14).Z().note('la cupule')
    t.M(-14, 4).C(-10, -2, -4, -4, 2, -3).C(-4, 0, -9, 3, -12, 8).Z().note('le reflet')


# ---------------------------------------------- l'extension : Schoul

@silhouette('question', 'Fro', 'extension')
def _(t):
    # Une question se pose à voix haute : la bulle, et le point
    # d'interrogation creusé dedans.
    t.M(-30, -40).L(30, -40).C(38, -40, 44, -34, 44, -26).L(44, 10).C(44, 18, 38, 24, 30, 24) \
        .L(-6, 24).L(-30, 44).L(-22, 24).L(-30, 24).C(-38, 24, -44, 18, -44, 10).L(-44, -26) \
        .C(-44, -34, -38, -40, -30, -40).Z().note('la bulle')
    trait(t, arc(0, -18, 10, 200, 420, 20) + [(0, -1), (0, 4)], 8).note("le point d'interrogation, en réserve")
    t.disque(0, 13, 4.6)


@silhouette('cahier', 'Heft', 'extension')
def _(t):
    # Fermé, à spirale : le livre ouvert de Meubles.kt est déjà « Buch ».
    t.arrondi(-24, -44, 40, 44, 5).note('la couverture')
    t.arrondi(-10, -30, 30, -6, 3).note("l'étiquette, en réserve")
    t.arrondi(-4, -22, 24, -19.5, 1.2).note('ses lignes')
    t.arrondi(-4, -16, 16, -13.5, 1.2)
    for y in (-34, -20, -6, 8, 22, 36):
        t.arrondi(-38, y - 3, -16, y + 3, 3)
    t.note('la spirale, qui passe dans la couverture')


@silhouette('colle', 'Koll', 'extension')
def _(t):
    # Le tube couché en biais, bec en bas à droite, et la goutte qui en tombe :
    # sans la goutte, c'était du dentifrice.
    r = Tourne(t, 30, 0, -4)
    r.pol(-44, -15, -35, -15, -37, -10, -35, -5, -37, 0, -35, 5, -37, 10, -35, 15, -44, 15).note('le pli du tube')
    r.M(-33, -16).L(8, -16).C(16, -16, 20, -7, 26, -6).L(34, -5).L(34, 5).L(26, 6).C(20, 7, 16, 16, 8, 16) \
        .L(-33, 16).Z().note('le tube et son bec')
    r.arrondi(-26, -7, 4, 7, 2.5).note("l'étiquette")
    t.M(36, 26).C(41, 33, 44, 37, 44, 41).C(44, 45.5, 40.5, 48, 36, 48).C(31.5, 48, 28, 45.5, 28, 41) \
        .C(28, 37, 31, 33, 36, 26).Z().note('la goutte')


@silhouette('calcul', 'Calcul', 'extension')
def _(t):
    # Les quatre opérations sur quatre touches, dans l'ordre de la calculette.
    for cx in (-22, 22):
        for cy in (-22, 22):
            t.arrondi(cx - 20, cy - 20, cx + 20, cy + 20, 7)
    t.note('les quatre touches')
    croix(t, -22, -22, 11, 3.5)
    t.note('plus, moins, fois, divisé, en réserve')
    t.arrondi(11, -25.5, 33, -18.5, 2)
    croix(t, -22, 22, 12, 3.5, 45)
    t.arrondi(11, 19, 33, 25, 2)
    t.disque(22, 13, 3.4)
    t.disque(22, 31, 3.4)


@silhouette('crayon', 'Crayong', 'extension')
def _(t):
    # Couché à 45°, la mine en bas : chaque pièce séparée de la suivante par
    # un jour, à la manière d'un pochoir.
    r = Tourne(t, -45)
    r.pol(-56, 0, -47, -3.6, -47, 3.6).note('la mine')
    r.pol(-45, -4.4, -31, -9, -31, 9, -45, 4.4).note('le bois taillé')
    r.pol(-29, -9, 31, -9, 31, 9, -29, 9).note('le corps')
    r.pol(-25, -3.8, 27, -3.8, 27, -2.2, -25, -2.2).note('ses pans')
    r.pol(-25, 2.2, 27, 2.2, 27, 3.8, -25, 3.8)
    r.pol(33, -9.5, 43, -9.5, 43, 9.5, 33, 9.5).note('la virole')
    r.pol(37, -6, 39, -6, 39, 6, 37, 6)
    r.arrondi(45, -9, 56, 9, 3.5).note('la gomme')


@silhouette('alphabet', 'Alphabet', 'extension')
def _(t):
    # Les cubes de la classe des petits, A sur B et C.
    t.arrondi(-20, -44, 20, -4, 4).note('les trois cubes')
    t.arrondi(-44, 2, -4, 42, 4)
    t.arrondi(4, 2, 44, 42, 4)
    lettre_A(t, 0, -24)
    t.note('A, B, C, en réserve')
    lettre_B(t, -24, 22)
    lettre_C(t, 24, 22)


@silhouette('cartable', 'Schoulsak', 'extension')
def _(t):
    # De face : la poignée, le rabat et ses deux fermoirs.
    trait(t, arc(0, -36, 12, 180, 360, 16), 5).note('la poignée')
    t.arrondi(-34, -32, 34, 46, 10).note('le sac')
    trait(t, [(-26, -32), (-26, 2)] + arc(-16, 2, 10, 180, 90, 8) + arc(16, 2, 10, 90, 0, 8) + [(26, -32)],
          2.6).note('le bord du rabat, en réserve')
    for x in (-15, 15):
        t.arrondi(x - 5, 16, x + 5, 26, 1.5)
        t.arrondi(x - 2, 19, x + 2, 23, .8)
    t.note('les fermoirs')


@silhouette('mathematiques', 'Mathematik', 'extension')
def _(t):
    # L'équerre et le compas : « Calcul » a déjà les opérations.
    t.pol(-46, 44, -46, -28, 26, 44).note("l'équerre")
    t.pol(-38, 36, -38, -8, 6, 36).note('son jour')
    for i in range(1, 6):
        y = -28 + i * 12
        t.pol(-46, y, -40, y, -40, y + 1.6, -46, y + 1.6)
    t.note('ses graduations')
    t.disque(30, -38, 6.5).note('la tête du compas')
    t.disque(30, -38, 2.4)
    t.pol(28, -50, 32, -50, 32, -45.5, 28, -45.5).note('sa poignée')
    trait(t, [(27, -32), (16, 14)], 6.2).note('la jambe à la pointe')
    t.pol(14.2, 16.8, 18.2, 17.8, 13.4, 28).note('la pointe')
    trait(t, [(33, -32), (42, 6)], 6.2).note('la jambe au crayon')
    t.pol(39.4, 9, 45, 8, 44.4, 20).note('sa mine')


# ----------------------------------------------- l'extension : Natur

@silhouette('hetre', 'Bich', 'extension')
def _(t):
    # Le hêtre isolé : une large couronne ovale posée haut sur un fût lisse,
    # et ses maîtresses branches qui montent dedans. « arbre » a une couronne
    # en nuage et pas de branches ; plus plate, celle-ci faisait un champignon.
    t.M(0, -46).C(30, -46, 48, -30, 48, -10).C(48, 8, 32, 18, 14, 18).L(6, 18).L(6, 14) \
        .L(-6, 14).L(-6, 18).L(-14, 18).C(-32, 18, -48, 8, -48, -10).C(-48, -30, -30, -46, 0, -46).Z() \
        .note('la couronne')
    t.pol(-3.5, 14, -3, -2, -22, -18, -19, -22, 0, -7, 19, -22, 22, -18, 3, -2, 3.5, 14).note('les branches, en réserve')
    t.pol(-2, -10, -1.5, -28, 1.5, -28, 2, -10)
    t.M(-6, 20).L(6, 20).L(7, 34).C(8, 40, 14, 42, 22, 44).L(-22, 44).C(-14, 42, -8, 40, -7, 34).Z() \
        .note('le fût et ses racines')


@silhouette('orage', 'Donner', 'extension')
def _(t):
    # Le nuage et l'éclair : la pluie de Meubles.kt a des gouttes.
    t.M(-38, 6).C(-50, 6, -50, -14, -37, -15).C(-37, -30, -20, -36, -10, -26).C(-4, -44, 22, -44, 25, -24) \
        .C(38, -30, 50, -14, 41, -2).C(46, 4, 44, 6, 38, 6).Z().note('le nuage')
    t.pol(-4, 10, 14, 10, 4, 24, 15, 24, -10, 49, -2, 31, -13, 31).note("l'éclair")


@silhouette('verger', 'Bongert', 'extension')
def _(t):
    # Deux pommiers en couronne de feuillage, et leurs pommes en réserve. En
    # disques lisses, c'étaient des sucettes.
    def couronne(cx, cy, k):
        t.M(cx - 22 * k, cy + 6 * k).C(cx - 30 * k, cy + 2 * k, cx - 28 * k, cy - 14 * k, cx - 17 * k, cy - 14 * k) \
            .C(cx - 16 * k, cy - 26 * k, cx + 2 * k, cy - 30 * k, cx + 8 * k, cy - 20 * k) \
            .C(cx + 20 * k, cy - 26 * k, cx + 32 * k, cy - 12 * k, cx + 24 * k, cy - 2 * k) \
            .C(cx + 32 * k, cy + 6 * k, cx + 24 * k, cy + 18 * k, cx + 12 * k, cy + 14 * k) \
            .C(cx + 6 * k, cy + 20 * k, cx - 8 * k, cy + 20 * k, cx - 12 * k, cy + 14 * k) \
            .C(cx - 22 * k, cy + 18 * k, cx - 28 * k, cy + 12 * k, cx - 22 * k, cy + 6 * k).Z()
    couronne(-20, -12, 1)
    t.note('les deux pommiers')
    couronne(26, 0, .72)
    for x, y in ((-28, -16), (-12, -24), (-22, 2), (-4, -8), (20, -4), (32, -8), (27, 8)):
        t.disque(x, y, 3.8)
    t.note('les pommes, en réserve')
    t.pol(-24, 9, -16, 9, -16, 36, -24, 36).note('les troncs')
    t.pol(23, 14, 29, 14, 29, 36, 23, 36)
    t.arrondi(-48, 38, 48, 46, 4).note("l'herbe")


@silhouette('automne', 'Hierscht', 'extension')
def _(t):
    # La feuille d'érable, et deux feuilles qui tombent.
    t.pol(0, -46, 6, -32, 15, -36, 12, -21, 30, -27, 25, -15, 33, -11, 22, -2, 26, 6, 10, 4, 3, 10,
          2, 22, -2, 22, -3, 10, -10, 4, -26, 6, -22, -2, -33, -11, -25, -15, -30, -27, -12, -21, -15, -36, -6, -32) \
        .note("la feuille d'érable")
    t.pol(-1, 2, 0, -30, 1, 2).note('sa nervure')
    Tourne(t, 35, 30, 34).M(-12, 0).C(-6, -7, 6, -7, 12, 0).C(6, 7, -6, 7, -12, 0).Z().note('les feuilles qui tombent')
    Tourne(t, -20, -32, 30).M(-9, 0).C(-4, -5, 4, -5, 9, 0).C(4, 5, -4, 5, -9, 0).Z()


@silhouette('printemps', 'Fréijoer', 'extension')
def _(t):
    # La pousse qui sort de terre : la fleur est déjà « Feierblumm ».
    t.M(-42, 46).L(-42, 40).C(-30, 30, -12, 26, 0, 26).C(12, 26, 30, 30, 42, 40).L(42, 46).Z().note('la terre')
    trait(t, [(0, 23), (-1, 10), (0, -2)], 6).note('la tige')
    t.M(-4, -6).C(-12, -26, -30, -34, -44, -30).C(-40, -16, -24, -4, -4, -6).Z().note('les deux feuilles')
    t.M(4, -6).C(10, -32, 30, -44, 46, -40).C(44, -20, 26, -4, 4, -6).Z()
    trait(t, [(-10, -11), (-24, -21), (-36, -27)], 1.8).note('leurs nervures')
    trait(t, [(10, -12), (24, -26), (38, -35)], 1.8)


@silhouette('glacier', 'Gletscher', 'extension')
def _(t):
    # Deux sommets inégaux sous leur glace, séparée de la roche par un jour qui
    # se lit sans couleur. Un seul triangle symétrique se lisait comme une tente.
    j = 3.2
    A, g, d = (10, -46), (-34, 42), (48, 42)            # le grand sommet
    a, g2, d2 = (-27, -8), (-48, 42), (-6, 42)          # le petit, devant
    vg = [sur(A, g, -16), (-4, -10), (2, -18), (8, -2), (14, -16), (22, -8), sur(A, d, -18)]
    t.pol(*A, *[c for p in vg[::-1] for c in p]).note('la glace du grand sommet')
    roche = [(x, y + j) for x, y in vg]
    roche[0], roche[-1] = sur(A, g, vg[0][1] + j), sur(A, d, vg[-1][1] + j)
    a_d, d2_d = (a[0] + j * 1.6, a[1]), (d2[0] + j * 1.6, d2[1])   # le flanc droit du petit, décalé
    t.pol(*[c for p in roche + [d, d2_d, inter(A, g, a_d, d2_d)] for c in p]).note('sa roche, rognée par le petit')
    vp = [sur(a, g2, 8), (-32, 14), (-27, 6), (-22, 14), sur(a, d2, 8)]
    t.pol(*a, *[c for p in vp[::-1] for c in p]).note('la glace du petit')
    rp = [(x, y + j) for x, y in vp]
    rp[0], rp[-1] = sur(a, g2, vp[0][1] + j), sur(a, d2, vp[-1][1] + j)
    t.pol(*[c for p in rp + [d2, g2] for c in p]).note('sa roche')


@silhouette('coquelicot', 'Feierblumm', 'extension')
def _(t):
    # De face : quatre pétales ronds qui se chevauchent, le grand cœur sombre
    # (sa capsule, ses étamines), et le bouton qui penche sur sa tige : c'est
    # lui qui fait le coquelicot. De profil, la coupe se lisait comme un bol.
    cx, cy = 10, -20
    centres = [(-8, -8), (8, -8), (8, 8), (-8, 8)]
    t.pol(*union_disques(cx, cy, centres, 17)).note('les quatre pétales')
    for (px, py), a0 in zip(centres, (200, 290, 20, 110)):
        trait(t, arc(cx + px, cy + py, 17 - 1.4, a0, a0 + 46, 8), 1.8)
    t.note('leurs recouvrements')
    t.disque(cx, cy, 11.5).note('le cœur, en réserve')
    t.disque(cx, cy, 5).note('la capsule')
    for k in range(8):
        a = 2 * math.pi * k / 8
        t.disque(cx + 8.6 * math.cos(a), cy + 8.6 * math.sin(a), 1.8)
    t.note('les étamines')
    trait(t, [(cx, cy + 27), (cx - 1, 30), (cx - 4, 40), (cx - 6, 47)], 4.6).note('la tige')
    trait(t, [(-18, 47), (-21, 24), (-26, 6), (-33, 0), (-39, 2)], 4).note('la tige du bouton')
    t.M(-42, 4).C(-36, 4, -33, 12, -35, 20).C(-37, 26, -45, 26, -47, 20).C(-49, 12, -48, 4, -42, 4).Z() \
        .note('le bouton, penché')


# ------------------------------------------------- recopiés de Meubles.kt

def recopies():
    """Les trois meubles de la boîte que Meubles.kt avait déjà et que les
    schémas n'avaient pas : recopiés tels quels, pour la planche."""
    gateau = Trace().pol(-40, 44, -40, 4, 40, 4, 40, 44).pol(-34, 4, -34, -16, 34, -16, 34, 4) \
        .pol(-3, -16, 3, -16, 3, -34, -3, -34).M(0, -50).C(7, -44, 6, -36, 0, -36) \
        .C(-6, -36, -7, -44, 0, -50).Z().pol(-40, 16, 40, 16, 40, 22, -40, 22)
    lait = Trace().arrondi(-28, -18, 26, 44, 6).pol(-18, -34, 16, -34, 18, -18, -20, -18) \
        .pol(-22, -42, 20, -42, 20, -34, -22, -34).pol(26, -8, 46, -2, 46, 20, 26, 26) \
        .pol(29, 2, 39, 5, 39, 13, 29, 16)
    cafe = Trace().pol(-34, -12, 22, -12, 18, 28, -30, 28).M(22, -6).C(44, -6, 46, 20, 24, 20) \
        .L(24, 10).C(34, 10, 34, 4, 22, 4).Z().pol(-42, 28, 34, 28, 34, 40, -42, 40) \
        .pol(-16, -44, -10, -44, -10, -22, -16, -22).pol(2, -44, 8, -44, 8, -22, 2, -22)
    return {'gateau': ' '.join(gateau.svg), 'lait': ' '.join(lait.svg), 'cafe': ' '.join(cafe.svg)}


# ------------------------------------------------------------- sorties

TITRES = {'boite': 'la boîte de départ du Wuertduell',
          'extension': "l'extension du Wuertduell, Schoul et Natur"}


def kotlin(jeu='boite'):
    l = [f'        // ---- {TITRES[jeu]} (scripts/silhouettes_boite.py) ----']
    for nom, (mot, t) in SILHOUETTES.items():
        if JEUX[nom] != jeu:
            continue
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
        print(kotlin(*sys.argv[2:3]))
    elif sys.argv[1:2] == ['apercu']:
        open(sys.argv[2], 'w').write(apercu())
    else:
        sys.exit(__doc__)
