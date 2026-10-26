#!/usr/bin/env python3
"""Les silhouettes des noms de la boîte de départ qui n'en avaient pas.

Chaque silhouette suit les règles de `carnet/Meubles.kt` : un carré de cent
unités centré sur l'origine, y vers le bas, remplissage pair-impair. Une forme
posée dans une autre y creuse un trou (l'œil, la rayure) ; deux pièces qui
doivent rester pleines ne se chevauchent pas : elles sont séparées d'un jour
de deux unités, à la manière d'un pictogramme (l'aile et le corps, la patte et
le thorax).

Ce fichier est la source des nouveaux meubles : il écrit leur code Kotlin
(à recopier dans Meubles.kt) et leur chemin SVG (lu par
scripts/illustrations_regle.py pour les schémas et la planche de la boîte).

    python3 scripts/silhouettes_boite.py kotlin    # le bloc pour Meubles.kt
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


SILHOUETTES = {}


def silhouette(nom, mot):
    def enregistrer(fonction):
        t = Trace()
        fonction(t)
        SILHOUETTES[nom] = (mot, t)
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

def kotlin():
    l = ['        // ---- la boîte de départ du Wuertduell (scripts/silhouettes_boite.py) ----']
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
