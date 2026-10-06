"""Rend les fonds 1920x1080 de chaque scène (texte à gauche, place du téléphone à droite)."""
import base64, pathlib, subprocess, json

V = pathlib.Path(__file__).parent
LOGO = pathlib.Path("/home/medhi/SourceCode/LuxKeyb/Logos/luxembourg-logo-hd.png")
ROUGE, BLEU, ENCRE, PAPIER = "#ED2939", "#00A1DE", "#1F2933", "#F5F5F3"

SCENES = json.loads((V / "scenes.json").read_text())

BASE = """<meta charset="utf-8"><style>
*{{box-sizing:border-box;margin:0;padding:0}}
html,body{{width:1920px;height:1080px;overflow:hidden}}
body{{background:{papier};color:{encre};font-family:"Carlito","Liberation Sans",Arial,sans-serif;position:relative}}
.flag{{position:absolute;top:0;left:0;right:0;height:16px;display:flex}}
.flag i{{flex:1}} .flag i:nth-child(1){{background:{rouge}}} .flag i:nth-child(2){{background:#fff}} .flag i:nth-child(3){{background:{bleu}}}
{css}
</style><div class="flag"><i></i><i></i><i></i></div>{corps}"""

CSS_SCENE = """
.txt{position:absolute;left:130px;top:0;bottom:0;width:{w}px;display:flex;flex-direction:column;justify-content:center}
.kicker{font-size:34px;font-weight:700;letter-spacing:.14em;text-transform:uppercase;color:#00A1DE;margin-bottom:26px}
h1{font-size:86px;font-weight:700;line-height:1.06;letter-spacing:-.01em}
.sub{font-size:40px;line-height:1.38;color:#54606E;margin-top:30px}
.pied{position:absolute;left:130px;bottom:56px;display:flex;align-items:center;gap:18px;font-size:30px;font-weight:700;color:#7A8593}
.pied img{width:56px;height:56px}
"""

CSS_TITRE = """
.c{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center}
.c img{width:230px;height:230px;margin-bottom:34px}
.nom{font-size:58px;font-weight:700;color:#54606E;letter-spacing:.02em}
h1{font-size:96px;font-weight:700;line-height:1.08;margin-top:22px}
.sub{font-size:44px;color:#00A1DE;font-weight:700;margin-top:34px}
"""


def html_scene(s, logo):
    corps = f"""<div class="txt"><div class="kicker">{s['kicker']}</div><h1>{s['titre']}</h1>
<div class="sub">{s['sous']}</div></div>
<div class="pied"><img src="data:image/png;base64,{logo}">Lëtzebuergesch Clavier</div>"""
    return BASE.format(papier=PAPIER, encre=ENCRE, rouge=ROUGE, bleu=BLEU,
                       css=CSS_SCENE.replace("{w}", str(s["largeur_texte"])), corps=corps)


def html_titre(s, logo):
    corps = f"""<div class="c"><img src="data:image/png;base64,{logo}"><div class="nom">Lëtzebuergesch Clavier</div>
<h1>{s['titre']}</h1><div class="sub">{s['sous']}</div></div>"""
    return BASE.format(papier=PAPIER, encre=ENCRE, rouge=ROUGE, bleu=BLEU, css=CSS_TITRE, corps=corps)


def rendre(html: str, sortie: pathlib.Path):
    src = V / "panneau.html"
    src.write_text(html, encoding="utf-8")
    subprocess.run(["google-chrome", "--headless", "--disable-gpu", "--no-sandbox", "--hide-scrollbars",
                    "--force-device-scale-factor=1", "--window-size=1920,1280",
                    f"--screenshot={sortie}", str(src)], check=True, capture_output=True)
    subprocess.run(["convert", str(sortie), "-crop", "1920x1080+0+0", "+repage",
                    "-alpha", "off", str(sortie)], check=True)


def main():
    subprocess.run(["convert", str(LOGO),
                    "-resize", "256x256", str(V / "logo.png")], check=True)
    logo = base64.b64encode((V / "logo.png").read_bytes()).decode()
    for i, s in enumerate(SCENES):
        html = html_titre(s, logo) if s["type"] == "titre" else html_scene(s, logo)
        rendre(html, V / f"fond_{i:02d}.png")
        print("fond", i, s.get("kicker", s["titre"]))


if __name__ == "__main__":
    main()
