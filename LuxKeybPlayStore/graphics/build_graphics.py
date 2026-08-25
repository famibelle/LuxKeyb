#!/usr/bin/env python3
"""Fabrique les éléments graphiques de la fiche Play Store.

    python3 build_graphics.py            # tout
    python3 build_graphics.py icon       # icône 512 seule
    python3 build_graphics.py feature    # image mise en avant seule
    python3 build_graphics.py shots      # captures téléphone seules
    python3 build_graphics.py check      # vérifie les contraintes Play Console

Produit, dans `feature-graphic/`, les huit fichiers à envoyer à la Play
Console. Chacun porte le nom de l'emplacement du formulaire où il va, pour
qu'il n'y ait rien à retrouver au moment de l'envoi :

  Icône de l'application.png              depuis Logos/luxembourg-logo-hd.png
  Image de présentation.png               depuis feature_graphic_source.html
  Captures d'écran pour téléphone 1-6.png depuis docs/Screenshots/lux_*.png

Le numéro des captures est leur ordre d'envoi ; le tableau `SPECS` dit lequel
montre quoi.

Contraintes de la Console, toutes vérifiables avec `check` :
icône 512x512 et moins de 1 Mo ; image de présentation 1024x500 et moins de
15 Mo ; 2 à 8 captures en 16:9 ou 9:16, chaque côté entre 320 et 3840 px et
moins de 8 Mo. Les six captures font 1080x1920, donc au-dessus du 1080x1080
exigé pour que l'application soit promouvable — il en faut au moins quatre.

Les captures sources sont natives 1080 px de large (recapturées sur émulateur
1080x2340 le 2026-08-25, sous la 10.14.0) : rien n'est agrandi ici.

Dépendances : google-chrome (rendu HTML) et ImageMagick (`convert`).

Deux pièges de Chrome headless :

- le viewport rendu fait 87 px de moins que le `--window-size` demandé (hauteur
  de la barre de fenêtre), et le bas de la page est alors laissé vide. On rend
  donc plus haut que nécessaire, puis on recadre — voir `render()` ;
- Chrome n'ouvre pas un fichier HTML dont le chemin porte une apostrophe : il
  sort sans rien écrire et sans message. Les HTML intermédiaires sont donc
  nommés par leur numéro, jamais d'après la capture. Le `--screenshot=`, lui,
  accepte l'apostrophe, ce qui laisse les noms de sortie libres.

La Play Console refuse la transparence sur l'icône et l'image mise en avant :
tout est aplati sur blanc en sortie.
"""

import base64
import pathlib
import shutil
import struct
import subprocess
import sys
import tempfile

HERE = pathlib.Path(__file__).resolve().parent
REPO = HERE.parents[1]
SHOTS = REPO / "docs" / "Screenshots"
LOGO = REPO / "Logos" / "luxembourg-logo-hd.png"
OUT = HERE / "feature-graphic"
ICON = OUT / "Icône de l'application.png"

# marge de rendu qui absorbe la hauteur de fenêtre non peinte par Chrome
CHROME_GUTTER = 200

ROUGE, BLEU, ENCRE, PAPIER = "#ED2939", "#00A1DE", "#1F2933", "#F5F5F3"

# (sortie, source, index de frame si GIF, kicker, titre, sous-titre)
SPECS = [
    ("Captures d'écran pour téléphone 1", "lux_suggestions.png", None, "Suggestions",
     "Il vous souffle les mots",
     "Le luxembourgeois d'abord, le français pour les emprunts — sans changer de clavier."),
    ("Captures d'écran pour téléphone 2", "lux_accents.png", None, "Diacritiques",
     "ë ä é ont leur propre touche",
     "Les autres accents (ü, è, à, ê, ö) restent sous un appui long."),
    ("Captures d'écran pour téléphone 3", "lux_niveaux.png", None, "Progression",
     "Chaque mot fait monter votre niveau",
     "D'Ufänker à Sproochenmeeschter, selon la part du dictionnaire déjà employée."),
    ("Captures d'écran pour téléphone 4", "lux_wuertsich.png", None, "Jeux",
     "Trois jeux pour élargir son vocabulaire",
     "Wuertsich, Wuertmix et Wuertriet, tirés du dictionnaire du clavier."),
    ("Captures d'écran pour téléphone 5", "lux_onboarding.png", None, "Installation",
     "Trois étapes, un clavier d'essai",
     "L'application ouvre elle-même les bons écrans de réglages Android."),
    ("Captures d'écran pour téléphone 6", "lux_numerique.png", None, "Clavier numérique",
     "Chiffres, symboles et ponctuation",
     "La ponctuation la plus fréquente du corpus est déjà sur le clavier de lettres."),
]

SHOT_TEMPLATE = """<meta charset="utf-8">
<style>
  *{{ box-sizing:border-box; margin:0; padding:0; }}
  html,body{{ width:1080px; height:1920px; overflow:hidden; }}
  body{{
    background:{papier}; color:{encre};
    font-family:"Carlito","Liberation Sans","DejaVu Sans",Arial,sans-serif;
    display:flex; flex-direction:column;
  }}
  .flag{{ height:14px; display:flex; flex:0 0 auto; }}
  .flag i{{ flex:1; }}
  .flag i:nth-child(1){{ background:{rouge}; }}
  .flag i:nth-child(2){{ background:#fff; }}
  .flag i:nth-child(3){{ background:{bleu}; }}

  header{{ padding:76px 84px 48px; flex:0 0 auto; }}
  .kicker{{
    font-size:28px; font-weight:700; letter-spacing:.14em; text-transform:uppercase;
    color:{bleu}; margin-bottom:20px;
  }}
  h1{{ font-size:70px; font-weight:700; line-height:1.1; letter-spacing:-.01em; }}
  .sub{{ font-size:34px; line-height:1.42; color:#54606E; margin-top:24px; max-width:900px; }}

  .stage{{ flex:1 1 auto; display:flex; align-items:center; justify-content:center;
           padding:0 84px 20px; min-height:0; }}
  .stage img{{
    max-width:100%; max-height:100%; width:auto; height:auto;
    border:12px solid {encre}; border-radius:34px;
    box-shadow:0 26px 60px rgba(31,41,51,.28);
  }}

  footer{{
    flex:0 0 auto; display:flex; align-items:center; justify-content:center; gap:18px;
    padding:30px 0 44px; font-size:28px; font-weight:700; color:#7A8593;
  }}
  footer img{{ width:52px; height:52px; }}
</style>
<div class="flag"><i></i><i></i><i></i></div>
<header>
  <div class="kicker">{kicker}</div>
  <h1>{title}</h1>
  <div class="sub">{sub}</div>
</header>
<div class="stage"><img src="data:image/png;base64,{shot}"></div>
<footer><img src="data:image/png;base64,{icon}">Lëtzebuergesch Clavier · gratuit, hors ligne</footer>
"""


def b64(path: pathlib.Path) -> str:
    return base64.b64encode(path.read_bytes()).decode()


def magick(*args: str) -> None:
    subprocess.run(["convert", *args], check=True)


def render(html: pathlib.Path, out: pathlib.Path, width: int, height: int) -> None:
    """Rend `html` en PNG opaque de width x height, sans le bas tronqué de Chrome."""
    subprocess.run([
        "google-chrome", "--headless", "--disable-gpu", "--no-sandbox",
        "--hide-scrollbars", "--force-device-scale-factor=1",
        f"--window-size={width},{height + CHROME_GUTTER}",
        f"--screenshot={out}", str(html),
    ], check=True, capture_output=True)
    magick(str(out), "-crop", f"{width}x{height}+0+0", "+repage",
           "-background", "white", "-alpha", "remove", "-alpha", "off", f"PNG24:{out}")


def build_icon() -> None:
    ICON.parent.mkdir(parents=True, exist_ok=True)
    magick(str(LOGO), "-background", "white", "-alpha", "remove", "-alpha", "off",
           "-resize", "512x512", "-depth", "8", f"PNG24:{ICON}")
    print(f"{ICON.relative_to(HERE)}  ok")


def build_feature() -> None:
    src = OUT / "feature_graphic_source.html"
    out = OUT / "Image de présentation.png"
    render(src, out, 1024, 500)
    print(f"{out.relative_to(HERE)}  ok")


def build_shots() -> None:
    out_dir = OUT
    out_dir.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmpdir:
        tmp = pathlib.Path(tmpdir)
        small_icon = tmp / "icon.png"
        magick(str(ICON), "-resize", "104x104", str(small_icon))
        icon = b64(small_icon)

        for index, (name, src, frame, kicker, title, sub) in enumerate(SPECS, 1):
            source = SHOTS / src
            if not source.exists():
                sys.exit(f"source manquante : {source}")
            shot = tmp / f"{index:02d}.png"
            magick(f"{source}[{frame}]" if frame is not None else str(source),
                   "-resize", "1600x", str(shot))

            # nom neutre : Chrome n'ouvre pas un fichier dont le chemin porte
            # une apostrophe, or les captures s'appellent « Captures d'écran… »
            html = tmp / f"{index:02d}.html"
            html.write_text(SHOT_TEMPLATE.format(
                papier=PAPIER, encre=ENCRE, rouge=ROUGE, bleu=BLEU,
                kicker=kicker, title=title, sub=sub,
                shot=b64(shot), icon=icon), encoding="utf-8")

            out = out_dir / f"{name}.png"
            render(html, out, 1080, 1920)
            print(f"{out.relative_to(HERE)}  ok")


def png_header(path: pathlib.Path) -> tuple[int, int, bool]:
    """(largeur, hauteur, transparence) lus dans l'en-tête IHDR."""
    head = path.open("rb").read(26)
    if head[:8] != b"\x89PNG\r\n\x1a\n":
        sys.exit(f"{path.name} : ce n'est pas un PNG")
    width, height = struct.unpack(">II", head[16:24])
    return width, height, head[25] in (4, 6)


def build_check() -> None:
    """Confronte les fichiers produits aux contraintes de la Play Console."""
    shots = [OUT / f"{name}.png" for name, *_ in SPECS]
    expected = [(ICON, 512, 512, 1),
                (OUT / "Image de présentation.png", 1024, 500, 15)]
    problems = []

    for path, want_w, want_h, max_mo in expected:
        if not path.exists():
            problems.append(f"{path.name} : absent")
            continue
        w, h, alpha = png_header(path)
        if (w, h) != (want_w, want_h):
            problems.append(f"{path.name} : {w}x{h}, attendu {want_w}x{want_h}")
        if alpha:
            problems.append(f"{path.name} : transparence, la Console la refuse")
        if path.stat().st_size > max_mo * 1024 * 1024:
            problems.append(f"{path.name} : plus de {max_mo} Mo")

    if not 2 <= len(shots) <= 8:
        problems.append(f"{len(shots)} captures, la Console en veut 2 à 8")
    promouvables = 0
    for path in shots:
        if not path.exists():
            problems.append(f"{path.name} : absent")
            continue
        w, h, _ = png_header(path)
        if (w, h) not in ((1080, 1920), (1920, 1080)):
            problems.append(f"{path.name} : {w}x{h}, attendu du 9:16 ou du 16:9")
        if not (320 <= w <= 3840 and 320 <= h <= 3840):
            problems.append(f"{path.name} : côté hors des bornes 320-3840 px")
        if path.stat().st_size > 8 * 1024 * 1024:
            problems.append(f"{path.name} : plus de 8 Mo")
        if w >= 1080 and h >= 1080:
            promouvables += 1
    if promouvables < 4:
        problems.append(f"{promouvables} captures au moins 1080x1080, il en faut 4 "
                        "pour que l'application soit promouvable")

    for problem in problems:
        print(f"  ✗ {problem}")
    if problems:
        sys.exit(f"{len(problems)} problème(s)")
    print(f"check  ok — icône, image de présentation et {len(shots)} captures conformes")


def main(argv: list[str]) -> int:
    for tool in ("google-chrome", "convert"):
        if not shutil.which(tool):
            sys.exit(f"{tool} introuvable")
    targets = argv[1:] or ["icon", "feature", "shots", "check"]
    known = {"icon": build_icon, "feature": build_feature,
             "shots": build_shots, "check": build_check}
    for target in targets:
        if target not in known:
            sys.exit(f"cible inconnue : {target} (icon | feature | shots | check)")
        known[target]()
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
