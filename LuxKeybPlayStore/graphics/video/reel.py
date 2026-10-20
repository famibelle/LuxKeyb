"""Monte le Reel vertical 1080x1920 à 30 i/s, sans son, en français.

Les séquences animées (frappe, dictée) sont reprises de la vidéo de
présentation 1920x1080 : l'écran du téléphone y est toujours au même endroit,
on le découpe image par image. Wierderbuch, jeux et carnet viennent des
captures pleine taille de `captures-emulateur-pixel9`, plus nettes que la
vidéo pour un écran entier.

La scène du carnet pose les cartes elles-mêmes, au design de la 37, reprises
des captures de `docs/Screenshots` (celles de la page des nouveautés).

Les textes restent entre y=150 et y=1640 : en bas, Instagram pose la légende
et les boutons par-dessus la vidéo.

    python3 reel.py   ->  Letzebuergesch_Clavier_reel_fr.mp4
"""
import math
import pathlib, subprocess, tempfile
from PIL import Image, ImageDraw, ImageFilter, ImageFont

V = pathlib.Path(__file__).parent
G = V.parent
CAP = G / "captures-emulateur-pixel9"
SHOTS = G.parents[1] / "docs" / "Screenshots"
SOURCE = V / "Letzebuergesch_Clavier_presentation_fr.mp4"
SORTIE = V / "Letzebuergesch_Clavier_reel_fr.mp4"
BADGE = V / "badge_google_play_fr.png"  # docs/assets/google-play-badge-fr.svg, rendu par Chromium
ICONE = G / "feature-graphic" / "icone-et-presentation" / "Icône de l'application.png"

W, H, FPS = 1080, 1920, 30
FONDU = 0.3
LARGEUR_TEL = 900
BAS = 1640  # en dessous, la légende et les boutons d'Instagram
ROUGE, BLEU, ENCRE, PAPIER, GRIS = (237, 41, 57), (0, 161, 222), (31, 41, 51), (245, 245, 243), (84, 96, 110)
POLICE = "/usr/share/fonts/truetype/crosextra/Carlito-{}.ttf"
# l'écran du téléphone dans la vidéo source, sans le cadre ni la ligne de
# l'horloge (coupée dans la séquence de dictée)
ÉCRAN_SOURCE = (989, 200, 1770, 990)


def police(taille, gras=False):
    return ImageFont.truetype(POLICE.format("Bold" if gras else "Regular"), taille)


def lignes(texte, f, largeur):
    sortie = []
    for para in texte.split("\n"):
        cur = ""
        for mot in para.split():
            essai = (cur + " " + mot).strip()
            if f.getlength(essai) <= largeur:
                cur = essai
            else:
                sortie.append(cur)
                cur = mot
        sortie.append(cur)
    return sortie


def bloc_texte(d, y, texte, f, couleur, interligne, centre=False, largeur=920):
    for l in lignes(texte, f, largeur):
        x = (W - f.getlength(l)) / 2 if centre else 80
        d.text((x, y), l, font=f, fill=couleur)
        y += interligne
    return y


def fond():
    im = Image.new("RGB", (W, H), PAPIER)
    d = ImageDraw.Draw(im)
    for i, c in enumerate((ROUGE, (255, 255, 255), BLEU)):
        d.rectangle((i * W // 3, 0, (i + 1) * W // 3, 12), fill=c)
    return im


def icone(taille):
    ic = Image.open(ICONE).convert("RGB").resize((taille, taille), Image.LANCZOS)
    m = Image.new("L", (taille, taille), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, taille - 1, taille - 1), taille // 5, fill=255)
    out = Image.new("RGBA", (taille, taille))
    out.paste(ic, (0, 0), m)
    return out


import functools


@functools.lru_cache(None)
def calque_entete():
    """Logo et nom en haut, présents sur les scènes d'écran."""
    c = Image.new("RGBA", (W, 140))
    c.alpha_composite(icone(72), (80, 50))
    ImageDraw.Draw(c).text((170, 66), "Lëtzebuergesch Clavier", font=police(36, True), fill=GRIS)
    return c


def téléphone(écran, hauteur):
    """Pose un écran dans un cadre sombre à coins arrondis, avec son ombre."""
    k = min(hauteur / écran.height, LARGEUR_TEL / écran.width)
    écran = écran.resize((round(écran.width * k), round(écran.height * k)), Image.LANCZOS)
    b, r, marge = 14, 40, 60
    w, h = écran.width + 2 * b, écran.height + 2 * b
    out = Image.new("RGBA", (w + 2 * marge, h + 2 * marge))
    ombre = Image.new("L", out.size, 0)
    ImageDraw.Draw(ombre).rounded_rectangle((marge, marge + 24, marge + w, marge + h + 24), r, fill=95)
    out.paste(ENCRE, (0, 0, *out.size), ombre.filter(ImageFilter.GaussianBlur(26)))
    ImageDraw.Draw(out).rounded_rectangle((marge, marge, marge + w, marge + h), r, fill=ENCRE)
    m = Image.new("L", écran.size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, écran.width - 1, écran.height - 1), r - b, fill=255)
    out.paste(écran.convert("RGB"), (marge + b, marge + b), m)
    return out, marge


def carte(image, hauteur):
    """Pose une carte (ou un écran) sans cadre, coins arrondis et ombre."""
    k = min(hauteur / image.height, LARGEUR_TEL / image.width)
    image = image.resize((round(image.width * k), round(image.height * k)), Image.LANCZOS)
    r, marge = round(image.width * 0.035), 60
    out = Image.new("RGBA", (image.width + 2 * marge, image.height + 2 * marge))
    ombre = Image.new("L", out.size, 0)
    ImageDraw.Draw(ombre).rounded_rectangle(
        (marge, marge + 24, marge + image.width, marge + image.height + 24), r, fill=110)
    out.paste(ENCRE, (0, 0, *out.size), ombre.filter(ImageFilter.GaussianBlur(26)))
    m = Image.new("L", image.size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, image.width - 1, image.height - 1), r, fill=255)
    out.paste(image.convert("RGB"), (marge, marge), m)
    return out, marge


def ease(t):
    t = max(0.0, min(1.0, t))
    return 1 - (1 - t) ** 3


def entrée(calque, t, retard=0.0, dy=50):
    """Opacité et décalage vertical d'un calque qui arrive."""
    a = ease((t - retard) / 0.45)
    return a, round((1 - a) * dy)


def poser(im, calque, xy, alpha):
    if alpha <= 0:
        return
    if alpha < 1:
        calque = calque.copy()
        calque.putalpha(calque.getchannel("A").point(lambda v: int(v * alpha)))
    im.alpha_composite(calque, xy)


class Écran:
    """Une fonctionnalité : titre en haut, téléphone animé en dessous."""

    def __init__(self, kicker, titre, sous, images, hauteur_tel, cadre=téléphone, retourne=False,
                 apparition=True):
        # la première image sert de vignette (Instagram, GitHub) : la scène
        # d'ouverture y est entière, pas encore en train d'apparaître
        self.apparition = apparition
        self.cadre = cadre
        self.retourne = retourne  # chaque image arrive en se retournant, comme une carte
        self.images = images  # liste de (fonction -> Image, durée)
        self.duree = sum(d for _, d in images)
        self.hauteur_tel = hauteur_tel
        self.textes = []
        y = 190
        for texte, f, coul, il, gap in ((kicker.upper(), police(34, True), BLEU, 44, 8),
                                        (titre, police(76, True), ENCRE, 82, 18),
                                        (sous, police(40), GRIS, 52, 0)):
            if not texte:
                continue
            c = Image.new("RGBA", (W, 400))
            bas = bloc_texte(ImageDraw.Draw(c), 0, texte, f, coul, il)
            self.textes.append((c.crop((0, 0, W, bas)), y))
            y += bas + gap
        self.y_tel = y + 30
        self.cache = {}
        # téléphone plus court que la place libre : on centre le tout
        tel, marge = cadre(images[0][0](), self.hauteur_max())
        decalage = max(0, (BAS - (self.y_tel + tel.height - 2 * marge)) // 2)
        self.textes = [(c, y + decalage) for c, y in self.textes]
        self.y_tel += decalage

    def hauteur_max(self):
        return min(self.hauteur_tel, BAS - self.y_tel)

    def image(self, t):
        acc = 0
        for i, (src, d) in enumerate(self.images):
            if t < acc + d or i == len(self.images) - 1:
                return src, t - acc
            acc += d

    def rendu(self, t):
        ta = t if self.apparition else t + 10
        im = fond().convert("RGBA")
        poser(im, calque_entete(), (0, 0), 1)
        for i, (c, y) in enumerate(self.textes):
            a, dy = entrée(c, ta, 0.08 * i)
            poser(im, c, (0, y + dy), a)
        src, tl = self.image(t)
        if src not in self.cache:
            self.cache = {src: self.cadre(src(), self.hauteur_max())}
        tel, marge = self.cache[src]
        a, dy = entrée(tel, ta, 0.2, dy=80)
        x = (W - tel.width) // 2
        if self.retourne and t >= 0.2:
            # demi-tour sur l'axe vertical, puis la carte se pose
            sx = 0.08 + 0.92 * ease(tl / 0.32)
            w = max(1, round(tel.width * sx))
            tel = tel.resize((w, tel.height), Image.BILINEAR)
            x = (W - w) // 2
        poser(im, tel, (x, self.y_tel - marge + dy), a)
        return im.convert("RGB")


class ÉcranDictée(Écran):
    """La dictée par son motif, retenu par le propriétaire le 2026-10-20 pour
    toutes les créations : grand micro rouge à halos qui pulse légèrement, et
    la phrase attestée qui s'écrit mot à mot dans sa bulle. Remplace la
    séquence reprise de la capture de l'écoute LuxASR."""

    PHRASE = "«\u00a0Op der Kiermes iessen ech ëmmer Gromperekichelcher mat Äppelkompott.\u00a0»"
    LARGEUR, HAUTEUR = 960, 620
    R = 124  # rayon du disque rouge au repos
    MOTS_PAR_S = 5.0

    def __init__(self, kicker, titre, sous, duree):
        fantome = Image.new("RGB", (self.LARGEUR, self.HAUTEUR))
        super().__init__(kicker, titre, sous, [(lambda: fantome, duree)], self.HAUTEUR,
                         cadre=lambda im, h: (Image.new("RGBA", (im.width + 120, im.height + 120)), 60))
        self.mots = self.PHRASE.split(" ")
        self.f = ImageFont.truetype(POLICE.format("BoldItalic"), 50)

    def panneau(self):
        """Le cadre blanc et son ombre, fixes."""
        L, Hh, m = self.LARGEUR, self.HAUTEUR, 60
        out = Image.new("RGBA", (L + 2 * m, Hh + 2 * m))
        ombre = Image.new("L", out.size, 0)
        ImageDraw.Draw(ombre).rounded_rectangle((m, m + 22, m + L, m + Hh + 22), 44, fill=90)
        out.paste(ENCRE, (0, 0, *out.size), ombre.filter(ImageFilter.GaussianBlur(26)))
        ImageDraw.Draw(out).rounded_rectangle((m, m, m + L, m + Hh), 44, fill=(255, 255, 255))
        return out, m

    def micro(self, t):
        """Le micro et ses deux halos ; une pulsation de 1,2 s, à peine marquée."""
        k = 2  # suréchantillonnage
        pul = 0.5 - 0.5 * math.cos(2 * math.pi * t / 1.2)
        r = self.R * (1 + 0.05 * pul)
        h1, h2 = r + 22 + 7 * pul, r + 46 + 14 * pul
        S = int(2 * (self.R * 1.05 + 62 + 14)) * k
        c = Image.new("RGBA", (S, S))
        d = ImageDraw.Draw(c)
        o = S / 2
        for rr, a in ((h2, int(255 * .07)), (h1, int(255 * (.15 + .04 * pul)))):
            d.ellipse((o - rr * k, o - rr * k, o + rr * k, o + rr * k), fill=(*ROUGE, a))
        d.ellipse((o - r * k, o - r * k, o + r * k, o + r * k), fill=(*ROUGE, 255))
        u = r * k / 96  # le pictogramme suit le disque
        blanc = (255, 255, 255, 255)
        d.rounded_rectangle((o - 17 * u, o - 46 * u, o + 17 * u, o + 10 * u), radius=17 * u, fill=blanc)
        d.arc((o - 32 * u, o - 22 * u, o + 32 * u, o + 30 * u), start=0, end=180, fill=blanc, width=round(8 * u))
        d.line((o, o + 30 * u, o, o + 44 * u), fill=blanc, width=round(8 * u))
        return c.resize((S // k, S // k), Image.LANCZOS)

    def bulle(self, n):
        """La bulle, avec les n premiers mots ; la place des suivants est déjà prise."""
        L = 500
        lignes_mots, ligne = [], []
        for mot in self.mots:
            essai = " ".join(ligne + [mot])
            if ligne and self.f.getlength(essai) > L - 56:
                lignes_mots.append(ligne)
                ligne = [mot]
            else:
                ligne.append(mot)
        lignes_mots.append(ligne)
        il = 66
        Hh = 40 + il * len(lignes_mots)
        b = Image.new("RGBA", (L, Hh))
        d = ImageDraw.Draw(b)
        d.rounded_rectangle((0, 0, L - 1, Hh - 1), 30, fill=(238, 244, 248, 255))
        d.rectangle((0, Hh - 30, 30, Hh - 1), fill=(238, 244, 248, 255))
        d.rounded_rectangle((0, Hh - 30, 30, Hh - 1), 8, fill=(238, 244, 248, 255))
        vu = 0
        y = 20
        for lm in lignes_mots:
            x = 28
            for mot in lm:
                if vu < n:
                    d.text((x, y), mot, font=self.f, fill=(43, 55, 66))
                vu += 1
                x += self.f.getlength(mot + " ")
            y += il
        return b

    def rendu(self, t):
        ta = t if self.apparition else t + 10
        im = fond().convert("RGBA")
        poser(im, calque_entete(), (0, 0), 1)
        for i, (c, y) in enumerate(self.textes):
            a, dy = entrée(c, ta, 0.08 * i)
            poser(im, c, (0, y + dy), a)
        if "panneau" not in self.cache:
            self.cache = {"panneau": self.panneau()}
        pan, m = self.cache["panneau"]
        calque = pan.copy()
        mic = self.micro(t)
        cy = m + self.HAUTEUR // 2
        calque.alpha_composite(mic, (m + 222 - mic.width // 2, cy - mic.height // 2))
        n = int(max(0.0, t - 0.35) * self.MOTS_PAR_S)
        bl = self.bulle(n)
        calque.alpha_composite(bl, (m + self.LARGEUR - 34 - bl.width, cy - bl.height // 2))
        a, dy = entrée(calque, ta, 0.2, dy=80)
        poser(im, calque, ((W - calque.width) // 2, self.y_tel - m + dy), a)
        return im.convert("RGB")


class Accroche:
    """Première image, qui sert aussi de vignette : le message, et dessous le
    clavier qui propose « Schueberfouer » après « schue ». Entière dès la première
    image, sans fondu."""

    def __init__(self, duree, dossier):
        self.duree = duree
        self.calques = [icone(180)]
        for texte, f, coul, il in (("Lëtzebuergesch Clavier", police(44, True), GRIS, 56),
                                   ("Vous le parlez.\nMaintenant, vous l'écrivez.", police(78, True), ENCRE, 88),
                                   ("Le clavier luxembourgeois pour Android", police(46, True), BLEU, 58)):
            c = Image.new("RGBA", (W, 400))
            bas = bloc_texte(ImageDraw.Draw(c), 0, texte, f, coul, il, centre=True, largeur=940)
            self.calques.append(c.crop((0, 0, W, bas)))
        self.gaps = [36, 18, 26, 0]
        # image 242 de la vidéo source : « schue » tapé, Schueberfouer proposé.
        # Le disque bleu de l'appui sur le e est effacé avec la touche d'une
        # image plus tardive, le clavier n'ayant pas bougé.
        clavier = Image.open(dossier / "0242.png").convert("RGB")
        boite = (100, 320, 280, 460)
        clavier.paste(Image.open(dossier / "0252.png").convert("RGB").crop(boite), boite[:2])
        self.tel, self.marge = téléphone(clavier, 820)

    def rendu(self, t):
        im = fond().convert("RGBA")
        y = 170
        for c, gap in zip(self.calques, self.gaps):
            im.alpha_composite(c, ((W - c.width) // 2, y))
            y += c.height + gap
        y_tel = max(y + 60, BAS - (self.tel.height - 2 * self.marge))
        im.alpha_composite(self.tel, ((W - self.tel.width) // 2, y_tel - self.marge))
        return im.convert("RGB")


class Appel:
    """Fin : l'appel à télécharger. Le badge Google Play bat doucement, et une
    barre de recherche tape le nom à chercher dans le Play Store : la vidéo se
    suffit à elle-même, sans lien dans la bio."""

    NOM = "Lëtzebuergesch Clavier"

    def __init__(self, duree):
        self.duree = duree
        self.calques = [icone(200)]
        for texte, f, coul, il in (("Téléchargez-le gratuitement", police(84, True), ENCRE, 92),):
            c = Image.new("RGBA", (W, 400))
            bas = bloc_texte(ImageDraw.Draw(c), 0, texte, f, coul, il, centre=True, largeur=940)
            self.calques.append(c.crop((0, 0, W, bas)))
        b = Image.open(BADGE).convert("RGBA")
        self.badge = b.resize((780, round(b.height * 780 / b.width)), Image.LANCZOS)
        self.calques.append(self.badge)
        c = Image.new("RGBA", (W, 400))
        bas = bloc_texte(ImageDraw.Draw(c), 0, "Cherchez :", police(44, True), GRIS, 56, centre=True)
        self.calques.append(c.crop((0, 0, W, bas)))
        self.recherche = self.barre(len(self.NOM), False)  # taille de la barre
        self.calques.append(self.recherche)
        self.gaps = [44, 56, 64, 18, 0]

    def barre(self, n, curseur):
        """Barre de recherche, les n premières lettres du nom tapées."""
        f = police(54, True)
        w, h = 820, 120
        out = Image.new("RGBA", (w + 40, h + 40))
        ombre = Image.new("L", out.size, 0)
        ImageDraw.Draw(ombre).rounded_rectangle((20, 28, 20 + w, 28 + h), h // 2, fill=70)
        out.paste(ENCRE, (0, 0, *out.size), ombre.filter(ImageFilter.GaussianBlur(12)))
        d = ImageDraw.Draw(out)
        d.rounded_rectangle((20, 20, 20 + w, 20 + h), h // 2, fill=(255, 255, 255), outline=BLEU, width=5)
        # loupe
        cx, cy = 20 + 70, 20 + h // 2 - 6
        d.ellipse((cx - 22, cy - 22, cx + 22, cy + 22), outline=GRIS, width=7)
        d.line((cx + 15, cy + 15, cx + 34, cy + 34), fill=GRIS, width=9)
        texte = self.NOM[:n]
        x = 20 + 130
        d.text((x, 20 + (h - 66) // 2), texte, font=f, fill=ENCRE)
        if curseur:
            xc = x + f.getlength(texte) + 4
            d.rectangle((xc, 20 + 30, xc + 5, 20 + h - 30), fill=BLEU)
        return out

    def rendu(self, t):
        import math
        im = fond().convert("RGBA")
        total = sum(c.height for c in self.calques) + sum(self.gaps)
        y = 150 + (BAS - 150 - total) // 2
        for i, c in enumerate(self.calques):
            a, dy = entrée(c, t, 0.1 * i)
            if c is self.badge and t > 0.9:
                k = 1 + 0.035 * math.sin((t - 0.9) * 2 * math.pi / 1.1) ** 2
                g = c.resize((round(c.width * k), round(c.height * k)), Image.BICUBIC)
                poser(im, g, ((W - g.width) // 2, y - (g.height - c.height) // 2 + dy), a)
            elif c is self.recherche:
                # le nom se tape à partir de 0,6 s, puis le curseur clignote
                n = max(0, min(len(self.NOM), int((t - 0.6) / 0.05)))
                curseur = n < len(self.NOM) or int(t / 0.5) % 2 == 0
                poser(im, self.barre(n, curseur), ((W - c.width) // 2, y + dy), a)
            else:
                poser(im, c, ((W - c.width) // 2, y + dy), a)
            y += c.height + self.gaps[i]
        return im.convert("RGB")


def séquence(dossier, debut, fin, vitesse, tenue):
    """Images [debut, fin] de la vidéo source, accélérées, la dernière tenue."""
    pas = []
    f = float(debut)
    while f <= fin:
        pas.append(int(f))
        f += vitesse
    out = [(lambda n=n: Image.open(dossier / f"{n:04d}.png"), 1 / FPS) for n in pas]
    out[-1] = (out[-1][0], tenue)
    return out


def capture(nom, d):
    return (lambda: Image.open(CAP / f"{nom}.png"), d)


def cliché(nom, boite, d):
    return (lambda: Image.open(SHOTS / nom).convert("RGB").crop(boite), d)


def main():
    with tempfile.TemporaryDirectory() as tmp:
        tmp = pathlib.Path(tmp)
        x0, y0, x1, y1 = ÉCRAN_SOURCE
        subprocess.run(["ffmpeg", "-v", "error", "-i", str(SOURCE), "-vf",
                        f"crop={x1 - x0}:{y1 - y0}:{x0}:{y0}", "-start_number", "0",
                        str(tmp / "%04d.png")], check=True)

        scenes = [
            Accroche(0.1, tmp),
            Écran("Suggestions", "Il vous souffle les mots",
                  "Tapez « schue », il propose « Schueberfouer ». Après un espace, il devine la suite.",
                  séquence(tmp, 110, 425, 2.2, 1.2), 1120),
            ÉcranDictée("Dictée vocale", "Parlez, il écrit pour vous",
                        "Touchez le micro et dictez en luxembourgeois : le texte s'écrit tout seul.",
                        3.6),
            Écran("Wierderbuch", "Un dictionnaire dans la poche",
                  "Le sens des mots et des exemples. Même sans connexion.",
                  [capture("05-wierderbuch-recherche-gromperekichelchen", 1.0),
                   capture("06-wierderbuch-fiche-gromperekichelchen", 1.8)], 1120),
            Écran("Jeux", "Apprenez en jouant",
                  "Sept jeux pour retrouver, écrire et placer les mots.",
                  [capture("13-jeu-wuertriet-mot-de-5-lettres", 1.0),
                   capture("16-jeu-kraizwuert-mots-croises", 0.9),
                   capture("11-jeu-wuertsich-mots-caches", 1.0)], 1120),
            Écran("Carnet", "Chaque mot gagné devient une carte à collectionner", "",
                  [cliché("lux_carnet_serie_paysages.png", (567, 73, 1051, 781), 0.9),
                   cliché("lux_carnet_paysages.png", (568, 72, 1052, 781), 1.0),
                   cliché("lux_carnet_paysages.png", (0, 0, 540, 872), 1.1),
                   cliché("lux_carnet_serie_paysages.png", (0, 0, 540, 872), 1.3)],
                  1060, cadre=carte, retourne=True),
            Appel(4.8),
        ]

        ff = subprocess.Popen(
            ["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24",
             "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
             "-f", "lavfi", "-i", "anullsrc=r=44100:cl=stereo", "-shortest",
             "-c:v", "libx264", "-preset", "slow", "-crf", "17", "-pix_fmt", "yuv420p",
             "-profile:v", "high", "-c:a", "aac", "-b:a", "128k",
             "-movflags", "+faststart", str(SORTIE)],
            stdin=subprocess.PIPE)
        nf = round(FONDU * FPS)
        précédente = None
        for s in scenes:
            n = round(s.duree * FPS)
            for i in range(n):
                im = s.rendu(i / FPS)
                if précédente is not None and i < nf:
                    im = Image.blend(précédente.rendu(précédente.duree + i / FPS), im, (i + 1) / (nf + 1))
                ff.stdin.write(im.tobytes())
            précédente = s
        ff.stdin.close()
        ff.wait()
    print(SORTIE)


if __name__ == "__main__":
    main()
