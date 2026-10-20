"""Monte la vidéo des annonces Reddit : 1080 x 1350 (4:5), 30 i/s, muette,
en français et en anglais.

    python3 video_reddit.py   ->  reddit_video_annonce_fr.mp4, reddit_video_annonce_en.mp4

Ce n'est pas le Reel recadré. Trois choses changent pour Reddit :

- l'ordre suit les promesses de l'application, fixé par le propriétaire le
  2026-10-18 : l'écriture (mots devinés, accents), la dictée, la collection
  de cartes, les jeux ;
- le format est le 4:5, celui que Reddit conseille sur téléphone, et rien ne
  se cache en bas : Reddit ne pose pas de légende sur la vidéo ;
- la dictée prend le motif retenu pour toutes les créations le 2026-10-20 :
  le grand micro rouge qui pulse et la phrase qui s'écrit mot à mot dans sa
  bulle, la même scène que le Reel (`reel.ÉcranDictée`).

Les briques (fond, téléphone, carte, titres qui arrivent, appel final) sont
celles de `../video/reel.py`, réglées à la nouvelle hauteur.
"""
import pathlib
import subprocess
import sys
import tempfile

from PIL import Image

ICI = pathlib.Path(__file__).resolve().parent
G = ICI.parent
sys.path.insert(0, str(G / "video"))
import reel  # noqa: E402

reel.W, reel.H, reel.BAS = 1080, 1350, 1290
W, H, FPS = reel.W, reel.H, reel.FPS
CAP = G / "captures-emulateur-pixel9"
SHOTS = reel.SHOTS
SRC = ICI / "sources"
JEU = (0, 540, 1080, 2330)  # sous les onglets, au-dessus de la barre de navigation

TEXTES = {
    "fr": dict(
        accroche=("Écrivez le luxembourgeois\ncomme vous le parlez.", "Le clavier luxembourgeois pour Android"),
        ecriture=("Écrire", "Tapez, il devine vos mots",
                  "Tapez « schue », il propose « Schueberfouer ». Après un espace, il devine la suite."),
        accents=("Écrire", "ë, ä, é sous le pouce",
                 "Chaque accent a sa touche. Les autres attendent sous un appui long."),
        dictee=("Dicter", "Parlez, il écrit ce que vous dites",
                "Touchez le micro : le texte s'écrit, ponctué, avec les majuscules."),
        cartes=("Collectionner", "Chaque mot gagné devient une carte",
                "Avec sa traduction et une phrase d'exemple."),
        jeux=("Jouer", "Sept jeux pour les gagner",
              "Placer, deviner, retrouver les mots."),
        appel=("100 % gratuit", "Cherchez :"),
    ),
    "en": dict(
        accroche=("Write Luxembourgish\nthe way you speak it.", "The Luxembourgish keyboard for Android"),
        ecriture=("Write", "Type, and it guesses your words",
                  "Type “schue” and it offers “Schueberfouer”. After a space, it guesses the next word."),
        accents=("Write", "ë, ä, é under your thumb",
                 "Every accent has its own key. The rest are a long press away."),
        dictee=("Speak", "Speak, and it writes what you say",
                "Tap the mic: the text appears, with punctuation and capitals."),
        cartes=("Collect", "Every word you win becomes a card",
                "With its translation and an example sentence."),
        jeux=("Play", "Seven games to win them",
              "Place, guess and find the words."),
        appel=("100% free", "Search for:"),
    ),
}


class Accroche:
    """Première image : le message, et le clavier qui propose Mamm, Schwëster,
    Frëndin. Entière dès la première image : c'est elle que Reddit montre avant
    la lecture."""

    def __init__(self, duree, l):
        self.duree = duree
        titre, sous = TEXTES[l]["accroche"]
        self.calques = [reel.icone(130)]
        for texte, f, coul, il in ((titre, reel.police(76, True), reel.ENCRE, 86),
                                   (sous, reel.police(42, True), reel.BLEU, 54)):
            c = Image.new("RGBA", (W, 400))
            from PIL import ImageDraw
            bas = reel.bloc_texte(ImageDraw.Draw(c), 0, texte, f, coul, il, centre=True, largeur=960)
            self.calques.append(c.crop((0, 0, W, bas)))
        self.gaps = [30, 20, 0]
        self.tel, self.marge = reel.téléphone(Image.open(SRC / "prediction.png"), 740)

    def rendu(self, t):
        im = reel.fond().convert("RGBA")
        y = 70
        for c, gap in zip(self.calques, self.gaps):
            im.alpha_composite(c, ((W - c.width) // 2, y))
            y += c.height + gap
        y_tel = max(y + 40, reel.BAS - (self.tel.height - 2 * self.marge))
        im.alpha_composite(self.tel, ((W - self.tel.width) // 2, y_tel - self.marge))
        return im.convert("RGB")


class Appel(reel.Appel):
    """L'appel final du Reel, dans la langue de l'annonce."""

    def __init__(self, duree, l):
        titre, cherchez = TEXTES[l]["appel"]
        badge = SRC / "badge_en.png" if l == "en" else reel.BADGE
        ancien = reel.BADGE
        reel.BADGE = badge
        try:
            super().__init__(duree)
        finally:
            reel.BADGE = ancien
        from PIL import ImageDraw
        for i, (texte, taille, coul) in ((1, (titre, 84, reel.ENCRE)), (3, (cherchez, 44, reel.GRIS))):
            c = Image.new("RGBA", (W, 400))
            bas = reel.bloc_texte(ImageDraw.Draw(c), 0, texte, reel.police(taille, True), coul,
                                  round(taille * 1.12), centre=True, largeur=940)
            self.calques[i] = c.crop((0, 0, W, bas))
        self.calques[0] = reel.icone(170)


def dictee_mot_a_mot():
    """La dictée composite (champ tapé + bandeau LuxASR), mots révélés un à un.
    Les lignes et les blancs entre les mots sont lus dans l'image elle-même."""
    img = Image.open(SRC / "dictee.png").convert("RGB")
    px = img.load()
    x0, x1, y0, y1 = 110, 640, 20, 215
    fond = img.getpixel((x0 + 2, y0 + 2))
    encre = lambda x, y: sum(px[x, y]) < 450
    lignes, debut = [], None
    for y in range(y0, y1):
        a = any(encre(x, y) for x in range(x0, x1))
        if a and debut is None:
            debut = y
        elif not a and debut is not None:
            if y - debut > 8:
                lignes.append((debut - 3, y + 3))
            debut = None
    mots = []
    for li, (a, b) in enumerate(lignes):
        vide, fin = 0, None
        for i, x in enumerate(range(x0, x1)):
            if any(encre(x, y) for y in range(a, b)):
                if fin is not None and vide >= 10:
                    mots.append((li, x0 + fin + 1))
                vide, fin = 0, i
            else:
                vide += 1
        if fin is not None:
            mots.append((li, x0 + fin + 1))

    def etat(k):
        im = img.copy()
        for li, (a, b) in enumerate(lignes):
            sur = [x for l, x in mots[:k] if l == li]
            depart = max(sur) + 2 if sur else x0 - 4
            im.paste(fond, (depart, a, x1 + 20, b))
        return im

    images = [(lambda: etat(0), 0.6)]
    images += [(lambda k=k: etat(k), 0.17) for k in range(1, len(mots))]
    images.append((lambda: img, 1.8))
    return images


def capture(chemin, d, boite=None):
    def charger():
        im = Image.open(chemin).convert("RGB")
        return im.crop(boite) if boite else im
    return (charger, d)


def scenes(l, tmp):
    t = TEXTES[l]
    cap = CAP / "en-US" if l == "en" else CAP
    if l == "fr":
        cartes = [capture(SHOTS / "lux_carnet_serie_paysages.png", 0.8, (567, 73, 1051, 781)),
                  capture(SHOTS / "lux_carnet_paysages.png", 0.9, (568, 72, 1052, 781)),
                  capture(SRC / "carte_fr.png", 1.6)]
    else:
        # les autres cartes au design de la 37 n'existent qu'en français
        cartes = [capture(SRC / "carte_en.png", 3.3)]
    return [
        Accroche(1.6, l),
        reel.Écran(*t["ecriture"], reel.séquence(tmp, 110, 330, 2.4, 1.1), 860),
        reel.Écran(*t["accents"], reel.séquence(tmp, 462, 575, 1.6, 0.9), 860),
        reel.ÉcranDictée(*t["dictee"], 3.8),
        reel.Écran(*t["cartes"], cartes, 900, cadre=reel.carte, retourne=True),
        # les jeux sans la barre d'état ni les onglets : le téléphone entier
        # serait trop étroit dans le 4:5
        reel.Écran(*t["jeux"], [capture(cap / "17-jeu-wuertplaz-mots-a-placer.png", 1.2, JEU),
                                capture(cap / "13-jeu-wuertriet-mot-de-5-lettres.png", 0.9, JEU),
                                capture(cap / "11-jeu-wuertsich-mots-caches.png", 1.1, JEU)], 960),
        Appel(3.6, l),
    ]


def monter(l, tmp):
    sortie = ICI / f"reddit_video_annonce_{l}.mp4"
    ff = subprocess.Popen(
        ["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24",
         "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
         "-f", "lavfi", "-i", "anullsrc=r=44100:cl=stereo", "-shortest",
         "-c:v", "libx264", "-preset", "slow", "-crf", "19", "-pix_fmt", "yuv420p",
         "-profile:v", "high", "-c:a", "aac", "-b:a", "128k",
         "-movflags", "+faststart", str(sortie)],
        stdin=subprocess.PIPE)
    nf = round(reel.FONDU * FPS)
    précédente = None
    for s in scenes(l, tmp):
        for i in range(round(s.duree * FPS)):
            im = s.rendu(i / FPS)
            if précédente is not None and i < nf:
                im = Image.blend(précédente.rendu(précédente.duree + i / FPS), im, (i + 1) / (nf + 1))
            ff.stdin.write(im.tobytes())
        précédente = s
    ff.stdin.close()
    ff.wait()
    print(f"{sortie.relative_to(ICI)}  ok")


def main():
    langues = sys.argv[1:] or ["fr", "en"]
    with tempfile.TemporaryDirectory() as tmp:
        tmp = pathlib.Path(tmp)
        x0, y0, x1, y1 = reel.ÉCRAN_SOURCE
        subprocess.run(["ffmpeg", "-v", "error", "-i", str(reel.SOURCE), "-vf",
                        f"crop={x1 - x0}:{y1 - y0}:{x0}:{y0}", "-start_number", "0",
                        str(tmp / "%04d.png")], check=True)
        for l in langues:
            monter(l, tmp)


if __name__ == "__main__":
    main()
