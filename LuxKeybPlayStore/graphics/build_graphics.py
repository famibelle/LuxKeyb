#!/usr/bin/env python3
"""Fabrique les éléments graphiques de la fiche Play Store.

    python3 build_graphics.py            # tout
    python3 build_graphics.py icon       # icône 512 seule
    python3 build_graphics.py feature    # image mise en avant seule
    python3 build_graphics.py shots      # captures téléphone seules

Produit, à partir des sources du dépôt :

  app-icon/luxkeyb-icon-512.png            depuis Logos/luxembourg-logo-hd.png
  feature-graphic/luxkeyb-feature-1024x500.png  depuis feature_graphic_source.html
  screenshots-phone/0*.png                 depuis docs/Screenshots/lux_*.png

Les captures sources sont natives 1080 px de large (recapturées sur émulateur
1080x2340 le 2026-08-25, sous la 10.14.0) : rien n'est agrandi ici.

Dépendances : google-chrome (rendu HTML) et ImageMagick (`convert`).

Piège Chrome headless : le viewport rendu fait 87 px de moins que le
`--window-size` demandé (hauteur de la barre de fenêtre), et le bas de la page
est alors laissé vide. On rend donc plus haut que nécessaire, puis on recadre —
voir `render()`.

La Play Console refuse la transparence sur l'icône et l'image mise en avant :
tout est aplati sur blanc en sortie.
"""

import base64
import pathlib
import shutil
import subprocess
import sys
import tempfile

HERE = pathlib.Path(__file__).resolve().parent
REPO = HERE.parents[1]
SHOTS = REPO / "docs" / "Screenshots"
LOGO = REPO / "Logos" / "luxembourg-logo-hd.png"
ICON = HERE / "app-icon" / "luxkeyb-icon-512.png"

# marge de rendu qui absorbe la hauteur de fenêtre non peinte par Chrome
CHROME_GUTTER = 200

ROUGE, BLEU, ENCRE, PAPIER = "#ED2939", "#00A1DE", "#1F2933", "#F5F5F3"

# (sortie, source, index de frame si GIF, kicker, titre, sous-titre)
SPECS = [
    ("01_suggestions", "lux_suggestions.png", None, "Suggestions",
     "Il vous souffle les mots",
     "Le luxembourgeois d'abord, le français pour les emprunts — sans changer de clavier."),
    ("02_accents", "lux_accents.png", None, "Diacritiques",
     "ë ä é ont leur propre touche",
     "Les autres accents (ü, è, à, ê, ö) restent sous un appui long."),
    ("03_niveaux", "lux_niveaux.png", None, "Progression",
     "Chaque mot fait monter votre niveau",
     "D'Ufänker à Sproochenmeeschter, selon la part du dictionnaire déjà employée."),
    ("04_wuertsich", "lux_wuertsich.png", None, "Jeux",
     "Trois jeux pour élargir son vocabulaire",
     "Wuertsich, Wuertmix et Wuertriet, tirés du dictionnaire du clavier."),
    ("05_onboarding", "lux_onboarding.png", None, "Installation",
     "Trois étapes, un clavier d'essai",
     "L'application ouvre elle-même les bons écrans de réglages Android."),
    ("06_numerique", "lux_numerique.png", None, "Clavier numérique",
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
    src = HERE / "feature-graphic" / "feature_graphic_source.html"
    out = HERE / "feature-graphic" / "luxkeyb-feature-1024x500.png"
    render(src, out, 1024, 500)
    print(f"{out.relative_to(HERE)}  ok")


def build_shots() -> None:
    out_dir = HERE / "screenshots-phone"
    out_dir.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmpdir:
        tmp = pathlib.Path(tmpdir)
        small_icon = tmp / "icon.png"
        magick(str(ICON), "-resize", "104x104", str(small_icon))
        icon = b64(small_icon)

        for name, src, frame, kicker, title, sub in SPECS:
            source = SHOTS / src
            if not source.exists():
                sys.exit(f"source manquante : {source}")
            shot = tmp / f"{name}.png"
            magick(f"{source}[{frame}]" if frame is not None else str(source),
                   "-resize", "1600x", str(shot))

            html = tmp / f"{name}.html"
            html.write_text(SHOT_TEMPLATE.format(
                papier=PAPIER, encre=ENCRE, rouge=ROUGE, bleu=BLEU,
                kicker=kicker, title=title, sub=sub,
                shot=b64(shot), icon=icon), encoding="utf-8")

            out = out_dir / f"{name}.png"
            render(html, out, 1080, 1920)
            print(f"{out.relative_to(HERE)}  ok")


def main(argv: list[str]) -> int:
    for tool in ("google-chrome", "convert"):
        if not shutil.which(tool):
            sys.exit(f"{tool} introuvable")
    targets = argv[1:] or ["icon", "feature", "shots"]
    known = {"icon": build_icon, "feature": build_feature, "shots": build_shots}
    for target in targets:
        if target not in known:
            sys.exit(f"cible inconnue : {target} (icon | feature | shots)")
        known[target]()
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
