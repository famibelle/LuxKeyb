"""Annonces Reddit : images, carrousel et vidéos, en français et en anglais.

    python3 reddit.py            # tout
    python3 reddit.py images     # ou : carrousel, videos

Formats Reddit Ads (vérifiés le 2026-10-18) : image 1:1, 4:5 ou 16:9, 3 Mo au
plus ; carrousel de 2 à 6 cartes carrées ; vidéo 1:1, 4:5 ou 16:9 (le 9:16
n'existe que pour les achats gérés), 5 à 30 s conseillées.

Les pages sont rendues par Chrome comme le reste des visuels du dossier
(build_graphics.render). Les captures de sources/ viennent de l'émulateur
pixel9. La dictée n'est plus montrée par une capture : depuis le 2026-10-20,
elle prend le motif du grand micro rouge à halos et de la phrase en bulle,
retenu par le propriétaire pour toutes les créations (fonction motif).

Aucune promesse de confidentialité sur ces visuels : la dictée envoie la voix
à LuxASR, et une annonce n'a pas la place de le dire honnêtement.
"""
import pathlib
import subprocess
import sys
import tempfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from build_graphics import render  # noqa: E402

ICI = pathlib.Path(__file__).resolve().parent
SRC = ICI / "sources"
# Le tampon « 100 % gratuit » des supports (docs/assets/tampon-gratuit.css),
# à la place de « Gratuit · sans publicité · Android » depuis le 2026-10-20 :
# l'argument « sans publicité » ne répond à aucune douleur.
TAMPON_CSS = ICI.parents[2] / "docs" / "assets" / "tampon-gratuit.css"
TAMPON = ('<span class="tampon"><span class="tampon-chiffre">100&nbsp;%%</span>'
          '<span class="tampon-mot">%s</span></span>')
REEL = ICI.parent / "video" / "Letzebuergesch_Clavier_reel_fr.mp4"
FOND_REEL = "#F4F4F1"  # fond du Reel, relevé sur ses images

TEXTES = {
    "fr": {
        "slogan": "Écrivez le luxembourgeois <span>comme vous le parlez.</span>",
        "tapez": "<b>Tapez</b>, il devine vos mots.",
        "parlez": "<b>Parlez</b>, il écrit ce que vous dites.",
        "pied": TAMPON % "gratuit",
        # Dans l'ordre des promesses (propriétaire, 2026-10-18) : l'écriture,
        # la dictée, la collection de cartes, les jeux. La dernière carte
        # n'a pas de capture : c'est l'appel à installer.
        "cartes": [
            ("Tapez, <span>il devine vos mots.</span>",
             "Après « meng », il propose Mamm, Schwëster, Frëndin.", "prediction.png"),
            ("ë, ä, é <span>sous le pouce.</span>",
             "Chaque accent a sa touche. Les autres sont sous un appui long.", "accents.png"),
            ("Parlez, <span>il écrit ce que vous dites.</span>",
             "Appuyez sur le micro : le texte s'écrit, ponctué, avec les majuscules.", "dictee.png"),
            ("Collectionnez <span>les mots en cartes.</span>",
             "Chaque mot gagné devient une carte, avec sa traduction et une phrase d'exemple.",
             "carte_fr.png"),
            ("Sept jeux <span>pour les gagner.</span>",
             "Wuertplaz : placez les mots dans la grille, sans connaître la langue.", "wuertplaz_fr.png"),
            ("", "", None),
        ],
    },
    "en": {
        "slogan": "Write Luxembourgish <span>the way you speak it.</span>",
        "tapez": "<b>Type</b>, and it guesses your words.",
        "parlez": "<b>Speak</b>, and it writes what you say.",
        "pied": TAMPON % "free",
        "cartes": [
            ("Type, <span>and it guesses your words.</span>",
             "After “meng”, it offers Mamm, Schwëster, Frëndin.", "prediction.png"),
            ("ë, ä, é <span>right under your thumb.</span>",
             "Every accent has its own key. The rest are a long press away.", "accents.png"),
            ("Speak, <span>and it writes what you say.</span>",
             "Tap the mic: the text appears as you talk, with punctuation and capitals.", "dictee.png"),
            ("Collect words <span>as cards.</span>",
             "Every word you win becomes a card, with its translation and an example sentence.",
             "carte_en.png"),
            ("Seven games <span>to win them.</span>",
             "Wuertplaz: fit the words into the grid, no Luxembourgish needed.", "wuertplaz_en.png"),
            ("", "", None),
        ],
    },
}

CSS = """
:root{ --rouge:#ED2939; --bleu:#00A1DE; --encre:#1F2933; --gris:#3A4552; --papier:#F5F5F3; }
*{ box-sizing:border-box; margin:0; padding:0; }
html,body{ width:%(w)dpx; height:%(h)dpx; overflow:hidden; }
body{ background:var(--papier); color:var(--encre); position:relative;
      font-family:"Carlito","Liberation Sans","DejaVu Sans",Arial,sans-serif; }
.drapeau{ position:absolute; left:0; right:0; top:0; height:%(bande)dpx; display:flex; }
.drapeau i{ flex:1; } .drapeau i:nth-child(1){background:var(--rouge);}
.drapeau i:nth-child(2){background:#fff;} .drapeau i:nth-child(3){background:var(--bleu);}
.tete{ display:flex; align-items:center; gap:%(g)dpx; }
.tete img{ width:%(logo)dpx; height:%(logo)dpx; }
.nom{ font-weight:700; font-size:%(nom)dpx; line-height:1; }
.nom span{ color:var(--rouge); display:block; }
.slogan{ font-weight:700; font-size:%(slogan)dpx; line-height:1.05; letter-spacing:-.01em; }
.slogan span{ color:var(--bleu); }
.claim{ font-weight:600; font-size:%(claim)dpx; line-height:1.35; color:var(--gris); }
.claim b{ color:var(--encre); }
.ecran{ border-radius:%(r)dpx; overflow:hidden; background:#fff;
        box-shadow:0 18px 44px rgba(31,41,51,.28), 0 0 0 1px rgba(31,41,51,.08); }
.ecran img{ display:block; width:100%%; }
.pied{ display:flex; align-items:center; gap:%(g)dpx; }
.pied img{ height:%(badge)dpx; }
.pied p{ font-size:%(pied)dpx; font-weight:600; color:var(--gris); }
.tampon[class]{ font-size:%(tampon)dpx; margin:10px 0 0 12px; }
"""


def page(w, h, corps, **taille):
    t = dict(w=w, h=h, bande=12, g=18, logo=84, nom=34, slogan=72, claim=36,
             r=28, badge=86, pied=28, tampon=26)
    t.update(taille)
    return (f'<!doctype html><meta charset="utf-8">'
            f'<link rel="stylesheet" href="{TAMPON_CSS.as_uri()}"><style>{CSS % t}</style>'
            f'<div class="drapeau"><i></i><i></i><i></i></div>{corps}')


def tete(l):
    return (f'<div class="tete"><img src="{SRC/"logo.png"}">'
            f'<div class="nom"><span>Lëtzebuergesch</span>Clavier</div></div>')


def pied(l):
    return (f'<div class="pied"><img src="{SRC/f"badge_{l}.svg"}">'
            f'<p>{TEXTES[l]["pied"]}</p></div>')


def produire(html, sortie, w, h, jpeg=False):
    with tempfile.TemporaryDirectory() as d:
        src = pathlib.Path(d) / "page.html"
        src.write_text(html, encoding="utf-8")
        png = pathlib.Path(d) / "rendu.png"
        render(src, png, w, h)
        if jpeg:
            subprocess.run(["convert", str(png), "-quality", "90", str(sortie)], check=True)
        else:
            png.replace(sortie)
    print(f"{sortie.relative_to(ICI)}  ok")


MICRO = ('<svg viewBox="0 0 24 24"><path d="M12 15a3 3 0 0 0 3-3V6a3 3 0 1 0-6 0v6a3 3 0 0 0 3 3Zm5-3a5 5 0 0 1-10 0H5a7 7 0 0 0 6 6.9V21h2v-2.1A7 7 0 0 0 19 12h-2Z"/></svg>')
PHRASE = "«&nbsp;Op der Kiermes iessen ech ëmmer Gromperekichelcher&nbsp;»"


def motif(largeur, style=""):
    """Le motif de la dictée, validé par le propriétaire le 2026-10-20 pour
    toutes les créations : grand micro rouge à halos et phrase attestée en
    bulle. Toutes les mesures suivent la largeur."""
    u = largeur / 100
    return (f'<div style="box-sizing:border-box;width:{largeur}px;display:flex;align-items:center;'
            f'gap:{5*u:.0f}px;padding:{4.5*u:.0f}px {4.5*u:.0f}px {4.5*u:.0f}px {7*u:.0f}px;background:#fff;'
            f'border-radius:{4.5*u:.0f}px;box-shadow:0 {3*u:.0f}px {7*u:.0f}px rgba(31,41,51,.30);{style}">'
            f'<span style="flex:none;width:{17*u:.0f}px;height:{17*u:.0f}px;border-radius:50%;background:#ED2939;'
            f'display:grid;place-items:center;box-shadow:0 0 0 {2*u:.0f}px rgba(237,41,57,.16),'
            f'0 0 0 {4*u:.0f}px rgba(237,41,57,.07)">'
            f'<span style="display:block;width:{8.5*u:.0f}px;height:{8.5*u:.0f}px;fill:#fff">{MICRO.replace("<svg ", "<svg style=&quot;width:100%;height:100%;fill:#fff&quot; ").replace("&quot;", chr(34))}</span></span>'
            f'<div style="background:#EEF4F8;border-radius:{3.4*u:.0f}px {3.4*u:.0f}px {3.4*u:.0f}px {1.1*u:.0f}px;'
            f'padding:{2.4*u:.0f}px {3*u:.0f}px;font:italic 700 {4.9*u:.1f}px/1.28 Carlito,sans-serif;color:#2B3742">'
            f'{PHRASE}</div></div>')


def visuel(largeur):
    """Le clavier qui devine le mot suivant, et la dictée posée dessus : les
    deux premières promesses, dans leur ordre. La suggestion en vraie capture,
    la dictée par son motif. Hauteur : environ 1,2 fois la largeur."""
    return (f'<div style="position:relative;width:{largeur}px">'
            f'<div class="ecran"><img src="{SRC/"prediction.png"}"></div>'
            + motif(round(largeur*.86), f'position:absolute;right:{-round(largeur*.08)}px;'
                    f'bottom:{-round(largeur*.14)}px;transform:rotate(-3deg)')
            + '</div>')


def images():
    for l, t in TEXTES.items():
        claim = f'<p class="claim">{t["tapez"]}<br>{t["parlez"]}</p>'
        # 1:1 : le slogan en haut, puis le texte à gauche et le téléphone à droite
        corps = (f'<div style="position:absolute;left:64px;right:64px;top:52px;display:flex;'
                 f'flex-direction:column;gap:26px">{tete(l)}<div class="slogan">{t["slogan"]}</div></div>'
                 f'<div style="position:absolute;left:64px;top:420px;width:420px;display:flex;'
                 f'flex-direction:column;gap:40px">{claim}'
                 f'<img src="{SRC/f"badge_{l}.svg"}" style="width:300px">'
                 f'<p class="claim" style="font-size:26px">{t["pied"].replace(" · ", "<br>")}</p></div>'
                 f'<div style="position:absolute;left:500px;top:430px">{visuel(500)}</div>')
        produire(page(1080, 1080, corps, slogan=66, claim=34),
                 ICI / f"reddit_image_1x1_{l}.jpg", 1080, 1080, jpeg=True)
        # 4:5 : texte en haut, téléphone au milieu, badge en bas
        corps = (f'<div style="position:absolute;left:70px;right:70px;top:52px;display:flex;'
                 f'flex-direction:column;gap:24px">{tete(l)}'
                 f'<div class="slogan">{t["slogan"]}</div>{claim}</div>'
                 f'<div style="position:absolute;left:180px;top:540px">{visuel(620)}</div>'
                 f'<div style="position:absolute;left:70px;bottom:48px">{pied(l)}</div>')
        produire(page(1080, 1350, corps, slogan=74, claim=36),
                 ICI / f"reddit_image_4x5_{l}.jpg", 1080, 1350, jpeg=True)
        # 16:9 : texte à gauche, téléphone à droite
        corps = (f'<div style="position:absolute;left:96px;top:0;bottom:0;width:900px;display:flex;'
                 f'flex-direction:column;justify-content:center;gap:36px">{tete(l)}'
                 f'<div class="slogan">{t["slogan"]}</div>{claim}{pied(l)}</div>'
                 f'<div style="position:absolute;left:1080px;top:150px">{visuel(700)}</div>')
        produire(page(1920, 1080, corps, slogan=92, claim=44, logo=96, nom=38, badge=100, pied=30),
                 ICI / f"reddit_image_16x9_{l}.jpg", 1920, 1080, jpeg=True)


def identifier(img):
    """Rapport largeur / hauteur d'une image."""
    w, h = subprocess.run(["identify", "-format", "%w %h", str(img)], check=True,
                          capture_output=True, text=True).stdout.split()
    return int(w) / int(h)


def carrousel():
    """Six cartes, le maximum de Reddit. Les captures larges (clavier) passent
    sous le titre ; les hautes (carte du carnet, Wuertplaz) à gauche, le texte à
    droite. La dernière, sans capture, porte l'appel à installer."""
    for l, t in TEXTES.items():
        n = len(t["cartes"])
        for i, (titre, sous, img) in enumerate(t["cartes"], 1):
            if img is None:
                corps = (f'<div style="position:absolute;left:90px;right:90px;top:0;bottom:0;display:flex;'
                         f'flex-direction:column;justify-content:center;gap:44px">{tete(l)}'
                         f'<div class="slogan">{t["slogan"]}</div>'
                         f'<p class="claim">{t["tapez"]}<br>{t["parlez"]}</p>'
                         f'<img src="{SRC/f"badge_{l}.svg"}" style="width:400px">'
                         f'<p class="claim" style="font-size:30px">{t["pied"]}</p></div>')
                produire(page(1200, 1200, corps, slogan=84, claim=40, logo=110, nom=44, tampon=34),
                         ICI / f"reddit_carrousel_{i}_{l}.jpg", 1200, 1200, jpeg=True)
                continue
            if img == "dictee.png":
                # la dictée : le motif en grand, à la place de la capture de l'écoute
                corps = (f'<div style="position:absolute;left:80px;right:80px;top:70px;display:flex;'
                         f'flex-direction:column;gap:20px"><div class="slogan">{titre}</div>'
                         f'<p class="claim">{sous}</p></div>'
                         + motif(900, 'position:absolute;left:150px;top:470px'))
                produire(page(1200, 1200, corps + (f'<p style="position:absolute;right:56px;top:34px;'
                         f'font:600 26px Carlito,sans-serif;color:#8A949E">{i}/{n}</p>'), slogan=66, claim=32),
                         ICI / f"reddit_carrousel_{i}_{l}.jpg", 1200, 1200, jpeg=True)
                continue
            haute = img.startswith(("carte", "wuertplaz"))
            numero = (f'<p style="position:absolute;right:56px;top:34px;font:600 26px Carlito,sans-serif;'
                      f'color:#8A949E">{i}/{n}</p>')
            sous_html = f'<p class="claim">{sous}</p>' if sous else ""
            if not haute:
                corps = (f'<div style="position:absolute;left:80px;right:80px;top:70px;display:flex;'
                         f'flex-direction:column;gap:20px"><div class="slogan">{titre}</div>{sous_html}</div>'
                         f'<div class="ecran" style="position:absolute;width:760px;left:220px;top:{300 if sous else 260}px">'
                         f'<img src="{SRC/img}"></div>')
            else:
                largeur = round(identifier(SRC / img) * 940)
                corps = (f'<div class="ecran" style="position:absolute;width:{largeur}px;left:70px;top:130px">'
                         f'<img src="{SRC/img}"></div>'
                         f'<div style="position:absolute;left:{70 + largeur + 56}px;right:56px;top:0;bottom:0;'
                         f'display:flex;flex-direction:column;justify-content:center;gap:30px">'
                         f'<div class="slogan">{titre}</div>{sous_html}</div>')
            produire(page(1200, 1200, corps + numero, slogan=66 if not haute else 54, claim=32),
                     ICI / f"reddit_carrousel_{i}_{l}.jpg", 1200, 1200, jpeg=True)


def videos():
    """Le Reel, posé dans les formats que Reddit accepte. En français seulement :
    le Reel lui-même est en français."""
    l, t = "fr", TEXTES["fr"]
    for nom, w, h in (("4x5", 1080, 1350), ("1x1", 1080, 1080)):
        sortie = ICI / f"reddit_video_{nom}_fr.mp4"
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", str(REEL), "-vf",
                        f"scale=-2:{h},pad={w}:{h}:(ow-iw)/2:0:{FOND_REEL}", "-c:v", "libx264",
                        "-pix_fmt", "yuv420p", "-crf", "20", "-c:a", "copy", str(sortie)], check=True)
        print(f"{sortie.relative_to(ICI)}  ok")
    # 16:9 : un panneau de texte à gauche, le Reel à droite
    with tempfile.TemporaryDirectory() as d:
        fond = pathlib.Path(d) / "fond.png"
        corps = (f'<div style="position:absolute;left:96px;top:0;bottom:0;width:980px;display:flex;'
                 f'flex-direction:column;justify-content:center;gap:34px">{tete(l)}'
                 f'<div class="slogan">{t["slogan"]}</div>'
                 f'<p class="claim">{t["tapez"]}<br>{t["parlez"]}</p>{pied(l)}</div>')
        html = page(1920, 1080, corps, slogan=92, claim=44, logo=96, nom=38, badge=96, pied=30)
        html = html.replace("background:var(--papier)", f"background:{FOND_REEL}")
        src = pathlib.Path(d) / "page.html"
        src.write_text(html, encoding="utf-8")
        render(src, fond, 1920, 1080)
        sortie = ICI / "reddit_video_16x9_fr.mp4"
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-loop", "1", "-i", str(fond),
                        "-i", str(REEL), "-filter_complex",
                        "[1:v]scale=-2:1080[r];[0:v][r]overlay=W-w-90:0:shortest=1",
                        "-map", "1:a?", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "20",
                        "-c:a", "copy", str(sortie)], check=True)
        print(f"{sortie.relative_to(ICI)}  ok")
    # vignette 1200 x 628, conseillée pour les vidéos
    corps = (f'<div style="position:absolute;left:60px;top:0;bottom:0;width:640px;display:flex;'
             f'flex-direction:column;justify-content:center;gap:20px">{tete(l)}'
             f'<div class="slogan">{t["slogan"]}</div></div>'
             f'<div style="position:absolute;left:740px;top:70px">{visuel(400)}</div>')
    produire(page(1200, 628, corps, slogan=56, logo=64, nom=28, r=20),
             ICI / "reddit_video_vignette_fr.jpg", 1200, 628, jpeg=True)


if __name__ == "__main__":
    quoi = sys.argv[1:] or ["images", "carrousel", "videos"]
    for q in quoi:
        {"images": images, "carrousel": carrousel, "videos": videos}[q]()
