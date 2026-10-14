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

    def __init__(self, kicker, titre, sous, images, hauteur_tel, cadre=téléphone, retourne=False):
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
        im = fond().convert("RGBA")
        poser(im, calque_entete(), (0, 0), 1)
        for i, (c, y) in enumerate(self.textes):
            a, dy = entrée(c, t, 0.08 * i)
            poser(im, c, (0, y + dy), a)
        src, tl = self.image(t)
        if src not in self.cache:
            self.cache = {src: self.cadre(src(), self.hauteur_max())}
        tel, marge = self.cache[src]
        a, dy = entrée(tel, t, 0.2, dy=80)
        x = (W - tel.width) // 2
        if self.retourne and t >= 0.2:
            # demi-tour sur l'axe vertical, puis la carte se pose
            sx = 0.08 + 0.92 * ease(tl / 0.32)
            w = max(1, round(tel.width * sx))
            tel = tel.resize((w, tel.height), Image.BILINEAR)
            x = (W - w) // 2
        poser(im, tel, (x, self.y_tel - marge + dy), a)
        return im.convert("RGB")


class Titre:
    def __init__(self, sur, titre, sous, duree, pied=None, apparition=True):
        self.duree = duree
        # la première image sert de vignette (Instagram, GitHub) : l'accroche
        # doit y être entière, pas encore en train d'apparaître
        self.apparition = apparition
        self.calques = [(icone(220), None)]
        for texte, f, coul, il in ((sur, police(44, True), GRIS, 56),
                                   (titre, police(78, True), ENCRE, 88),
                                   (sous, police(46, True), BLEU, 58)):
            c = Image.new("RGBA", (W, 400))
            bas = bloc_texte(ImageDraw.Draw(c), 0, texte, f, coul, il, centre=True, largeur=940)
            self.calques.append((c.crop((0, 0, W, bas)), None))
        self.pied = None
        if pied:
            c = Image.new("RGBA", (W, 200))
            bas = bloc_texte(ImageDraw.Draw(c), 0, pied, police(38), GRIS, 50, centre=True)
            self.pied = c.crop((0, 0, W, bas))

    def rendu(self, t):
        if not self.apparition:
            t += 10
        im = fond().convert("RGBA")
        hauteurs = [c.height for c, _ in self.calques]
        gaps = [50, 20, 36]
        total = sum(hauteurs) + sum(gaps)
        y = (H - total) // 2 - 120
        for i, (c, _) in enumerate(self.calques):
            a, dy = entrée(c, t, 0.12 * i)
            poser(im, c, ((W - c.width) // 2, y + dy), a)
            y += c.height + (gaps[i] if i < len(gaps) else 0)
        if self.pied:
            a, dy = entrée(self.pied, t, 0.6)
            poser(im, self.pied, (0, y + 70 + dy), a)
        return im.convert("RGB")


class Appel:
    """Fin : l'appel à télécharger, badge Google Play qui bat doucement."""

    def __init__(self, duree):
        self.duree = duree
        self.calques = [icone(200)]
        for texte, f, coul, il in (("Lëtzebuergesch Clavier", police(44, True), GRIS, 56),
                                   ("Téléchargez-le gratuitement", police(84, True), ENCRE, 92)):
            c = Image.new("RGBA", (W, 400))
            bas = bloc_texte(ImageDraw.Draw(c), 0, texte, f, coul, il, centre=True, largeur=940)
            self.calques.append(c.crop((0, 0, W, bas)))
        b = Image.open(BADGE).convert("RGBA")
        self.badge = b.resize((780, round(b.height * 780 / b.width)), Image.LANCZOS)
        self.calques.append(self.badge)
        f = police(48, True)
        texte = "Lien dans la bio"
        w = round(f.getlength(texte)) + 110
        pastille = Image.new("RGBA", (w, 96))
        d = ImageDraw.Draw(pastille)
        d.rounded_rectangle((0, 0, w - 1, 95), 48, fill=BLEU)
        d.text((55, 20), texte, font=f, fill=(255, 255, 255))
        self.calques.append(pastille)
        c = Image.new("RGBA", (W, 60))
        bloc_texte(ImageDraw.Draw(c), 0, "Sans publicité, sans traceur", police(38), GRIS, 50, centre=True)
        self.calques.append(c)
        self.gaps = [44, 16, 64, 48, 36, 0]

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
            Titre("Lëtzebuergesch Clavier", "Vous le parlez.\nMaintenant, vous l'écrivez.",
                  "Le clavier luxembourgeois pour Android", 2.8, apparition=False),
            Écran("Suggestions", "Il vous souffle les mots",
                  "Tapez « schue », il propose « Schueberfouer ». Après un espace, il devine la suite.",
                  séquence(tmp, 110, 425, 1.6, 1.4), 1120),
            Écran("Dictée", "Parlez, il écrit",
                  "En luxembourgeois, avec LuxASR de l'Université du Luxembourg.",
                  séquence(tmp, 616, 743, 1.0, 1.4), 1120),
            Écran("Wierderbuch", "Un dictionnaire dans la poche",
                  "Le sens des mots et des exemples. Même sans connexion.",
                  [capture("05-wierderbuch-recherche-gromperekichelchen", 1.3),
                   capture("06-wierderbuch-fiche-gromperekichelchen", 2.2)], 1120),
            Écran("Jeux", "Apprenez en jouant",
                  "Sept jeux pour retrouver, écrire et placer les mots.",
                  [capture("13-jeu-wuertriet-mot-de-5-lettres", 1.2),
                   capture("16-jeu-kraizwuert-mots-croises", 1.1),
                   capture("11-jeu-wuertsich-mots-caches", 1.3)], 1120),
            Écran("Carnet", "Chaque mot gagné devient une carte",
                  "Commune, rare ou très rare : à collectionner, puis à réviser au bon moment.",
                  [cliché("lux_carte_stad.png", (17, 17, 783, 1143), 1.3),
                   cliché("lux_carte_tres_rare.png", (17, 17, 783, 1143), 1.5),
                   cliché("lux_carnet_paysages.png", (568, 72, 1052, 781), 1.4),
                   cliché("lux_carnet_paysages.png", (0, 0, 540, 872), 1.9)],
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
