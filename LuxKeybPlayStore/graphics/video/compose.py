"""Assemble la vidéo 1920x1080 à 30 i/s à partir des fonds et des captures.

Chaque scène « écran » pose le téléphone à droite, dans un cadre sombre à coins
arrondis, et fait défiler ses captures au rythme du storyboard ; un disque bleu
marque chaque appui. Les scènes s'enchaînent par un fondu de 0,35 s.
"""
import json, pathlib, subprocess, sys
from PIL import Image, ImageDraw, ImageFilter

V = pathlib.Path(__file__).parent
FPS = 30
FONDU = 0.35
W, H = 1920, 1080
MARGE_DROITE = 150
BORD = 12
RAYON = 34

SCENES = json.loads((V / "scenes.json").read_text())


def beats_de(s):
    log = json.loads((V / s["dossier"] / "log.json").read_text())
    if s["beats"] == "auto":
        n = len(log)
        sp = {(int(k) % n): v for k, v in s["speciaux"].items()}
        paires = [(i, sp.get(i, s["defaut"])) for i in range(n)]
    else:
        paires = s["beats"]
    return [(log[i]["img"], d, log[i]["tap"]) for i, d in paires]


class ScèneÉcran:
    def __init__(self, i, s):
        self.fond = Image.open(V / f"fond_{i:02d}.png").convert("RGB")
        x0, y0, x1, y1 = s["crop"]
        self.crop = (x0, y0, x1, y1)
        self.k = s["hauteur"] / (y1 - y0)
        self.w = round((x1 - x0) * self.k)
        self.h = s["hauteur"]
        self.x = W - MARGE_DROITE - self.w
        self.y = (H - self.h) // 2
        self.beats = beats_de(s)
        self.duree = sum(d for _, d, _ in self.beats)
        self.cache = {}
        # cadre et ombre, dessinés une fois
        cadre = self.fond.copy()
        ombre = Image.new("L", (W, H), 0)
        ImageDraw.Draw(ombre).rounded_rectangle(
            (self.x - BORD, self.y - BORD + 24, self.x + self.w + BORD, self.y + self.h + BORD + 24),
            RAYON, fill=90)
        ombre = ombre.filter(ImageFilter.GaussianBlur(28))
        cadre.paste((31, 41, 51), (0, 0, W, H), ombre)
        ImageDraw.Draw(cadre).rounded_rectangle(
            (self.x - BORD, self.y - BORD, self.x + self.w + BORD, self.y + self.h + BORD),
            RAYON, fill=(31, 41, 51))
        self.base = cadre
        self.masque = Image.new("L", (self.w, self.h), 0)
        ImageDraw.Draw(self.masque).rounded_rectangle((0, 0, self.w - 1, self.h - 1), RAYON - BORD, fill=255)

    def ecran(self, chemin):
        if chemin not in self.cache:
            im = Image.open(chemin).convert("RGB").crop(self.crop)
            self.cache[chemin] = im.resize((self.w, self.h), Image.LANCZOS)
        return self.cache[chemin]

    def image(self, t):
        acc = 0
        for chemin, d, tap in self.beats:
            if t < acc + d or (chemin, d, tap) == self.beats[-1]:
                break
            acc += d
        dt = t - acc
        im = self.base.copy()
        im.paste(self.ecran(chemin), (self.x, self.y), self.masque)
        if tap and dt < 0.4:
            x0, y0, _, _ = self.crop
            cx = self.x + (tap[0] - x0) * self.k
            cy = self.y + (tap[1] - y0) * self.k
            a = 1 - dt / 0.4
            r = 34
            calque = Image.new("RGBA", (W, H), (0, 0, 0, 0))
            dr = ImageDraw.Draw(calque)
            dr.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(79, 195, 247, int(150 * a)),
                       outline=(1, 87, 155, int(230 * a)), width=4)
            im = Image.alpha_composite(im.convert("RGBA"), calque).convert("RGB")
        return im


class ScèneTitre:
    def __init__(self, i, s):
        self.fond = Image.open(V / f"fond_{i:02d}.png").convert("RGB")
        self.duree = s["duree"]

    def image(self, t):
        return self.fond


def main(sortie):
    scenes = [ScèneTitre(i, s) if s["type"] == "titre" else ScèneÉcran(i, s)
              for i, s in enumerate(SCENES)]
    total = sum(s.duree for s in scenes)
    print(f"{total:.1f} s, {len(scenes)} scènes", file=sys.stderr)
    ff = subprocess.Popen(
        ["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24",
         "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
         "-f", "lavfi", "-i", "anullsrc=channel_layout=stereo:sample_rate=48000",
         "-shortest", "-c:v", "libx264", "-preset", "slow", "-crf", "17",
         "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart",
         str(sortie)], stdin=subprocess.PIPE)
    for n, s in enumerate(scenes):
        suivante = scenes[n + 1].image(0) if n + 1 < len(scenes) else None
        nb = round(s.duree * FPS)
        for f in range(nb):
            t = f / FPS
            im = s.image(t)
            reste = s.duree - t
            if suivante is not None and reste < FONDU:
                im = Image.blend(im, suivante, 1 - reste / FONDU)
            if n == 0 and t < 0.5:  # ouverture depuis le blanc
                im = Image.blend(Image.new("RGB", (W, H), (245, 245, 243)), im, t / 0.5)
            ff.stdin.write(im.tobytes())
    ff.stdin.close()
    ff.wait()


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else V / "video.mp4")
