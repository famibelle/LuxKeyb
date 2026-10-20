#!/usr/bin/env python3
"""Régénère les images de partage de l'accueil, une par langue.

    python docs/scripts/generate_og.py

Une image de partage (1200 × 630, balise og:image) est ce qu'affichent
LinkedIn, WhatsApp ou Facebook quand on colle le lien d'une page : partage.png
(index.md, français), partage-en.png, partage-de.png et partage-pt.png.

Depuis le 2026-10-20, la page est composée en HTML et photographiée par
Chrome sans fenêtre (google-chrome --headless). À droite, les deux atouts au
même rang : en haut le vrai clavier qui propose le mot (source-clavier.png,
rognée d'une capture), en dessous le motif de la dictée, grand micro rouge à
halos et phrase attestée en bulle, validé par le propriétaire pour toutes les
créations. À gauche, le slogan, l'accroche, les deux pastilles dans le même
ordre que les visuels (taper, puis parler) et le bouton. Polices Noto Sans
du système. La fenêtre sans tête est plus haute que la zone affichée : on la
prend à 800 px et on rogne à 630.
"""

import pathlib
import subprocess
import tempfile
from PIL import Image

OG = pathlib.Path(__file__).resolve().parents[1] / "assets" / "og"
TMP = pathlib.Path(tempfile.mkdtemp(prefix="og-"))
T={
 "fr":("partage.png",["Vous le parlez.","Maintenant,","vous l'écrivez."],"Ce n'est pas vous qui écrivez mal le luxembourgeois : c'est votre clavier.",["Tapez : il trouve le mot et l'accent","Parlez : il écrit pour vous"],"Gratuit sur Google Play"),
 "en":("partage-en.png",["You speak it.","Now you","write it."],"You're not bad at writing Luxembourgish: your keyboard just doesn't know it.",["Type: it finds the word and the accent","Speak: it writes for you"],"Free on Google Play"),
 "de":("partage-de.png",["Sie sprechen es.","Jetzt schreiben","Sie es."],"Nicht Sie schreiben schlecht Luxemburgisch: Ihre Tastatur kennt es nicht.",["Tippen: Sie findet Wort und Akzent","Diktieren: Sie schreibt mit"],"Kostenlos bei Google Play"),
 "pt":("partage-pt.png",["Fala-o.","Agora,","escreve-o."],"O problema não é a sua escrita em luxemburguês: é o seu teclado.",["Escreva: ele acha a palavra e o acento","Fale: ele escreve por si"],"Grátis no Google Play"),
}
MIC='<svg viewBox="0 0 24 24"><path d="M12 15a3 3 0 0 0 3-3V6a3 3 0 1 0-6 0v6a3 3 0 0 0 3 3Zm5-3a5 5 0 0 1-10 0H5a7 7 0 0 0 6 6.9V21h2v-2.1A7 7 0 0 0 19 12h-2Z"/></svg>'
KB='<svg viewBox="0 0 24 24"><path d="M3 6h18a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1Zm1 2v8h16V8H4Zm2 1h2v2H6V9Zm4 0h2v2h-2V9Zm4 0h2v2h-2V9Zm-7 4h10v2H7v-2Z"/></svg>'
for l,(f,titre,acc,pastilles,bouton) in T.items():
    html=f'''<!doctype html><html lang="{l}"><head><meta charset="utf-8"><style>
html,body{{margin:0;width:1200px;height:630px;overflow:hidden}}
body{{background:#0f1b2d;font-family:"Noto Sans",sans-serif;color:#fff;position:relative}}
.bande{{position:absolute;left:0;top:0;bottom:0;width:16px;display:flex;flex-direction:column}}.bande span{{flex:1}}
.g{{position:absolute;left:60px;top:46px;width:490px}}
h1{{font-weight:700;font-size:54px;line-height:1.12;margin:0 0 16px;letter-spacing:-.5px}}
.acc{{font-size:24px;line-height:1.32;color:#cbd5e1;margin:0 0 18px}}
.p{{display:flex;flex-direction:column;gap:12px;align-items:flex-start}}
.p span{{display:flex;align-items:center;gap:10px;background:#00a1de;border-radius:24px;padding:7px 18px 7px 12px;font-weight:700;font-size:22px}}
.p svg{{width:26px;height:26px;fill:#fff}}
.btn{{display:inline-block;margin-top:24px;background:#ed2939;border-radius:28px;padding:11px 24px;font-weight:700;font-size:25px}}
.sig{{position:absolute;left:60px;bottom:34px;font-size:19px;color:#94a3b8}}
.d{{position:absolute;left:575px;top:35px;width:589px}}
.kb{{display:block;width:589px;border-radius:20px 20px 0 0}}
.cache{{background:#f1f3f6;height:14px;border-radius:0 0 20px 20px}}
.motif{{margin-top:18px;box-sizing:border-box;background:#fff;border-radius:20px;display:flex;align-items:center;gap:28px;padding:22px 26px 22px 40px;box-shadow:0 12px 30px rgba(0,0,0,.35)}}
.micro{{flex:none;width:84px;height:84px;border-radius:50%;background:#ed2939;display:grid;place-items:center;box-shadow:0 0 0 10px rgba(237,41,57,.18),0 0 0 20px rgba(237,41,57,.08)}}
.micro svg{{width:42px;height:42px;fill:#fff}}
.bulle{{background:#eef4f8;border-radius:16px 16px 16px 5px;padding:14px 18px;font-weight:600;font-style:italic;font-size:23px;line-height:1.28;color:#2b3742}}
</style></head><body>
<div class="bande"><span style="background:#ed2939"></span><span style="background:#fff"></span><span style="background:#00a1de"></span></div>
<div class="g"><h1>{"<br>".join(titre)}</h1><p class="acc">{acc}</p>
<div class="p"><span>{KB}{pastilles[0]}</span><span>{MIC}{pastilles[1]}</span></div>
<span class="btn">{bouton}</span></div>
<div class="sig">Lëtzebuergesch Clavier</div>
<div class="d"><img class="kb" src="{(OG/"source-clavier.png").as_uri()}"><div class="cache"></div>
<div class="motif"><span class="micro">{MIC}</span><div class="bulle">«&nbsp;Op der Kiermes iessen ech ëmmer Gromperekichelcher&nbsp;»</div></div></div>
</body></html>'''
    page=TMP/f'og_{l}.html'; page.write_text(html, encoding='utf-8')
    subprocess.run(['google-chrome','--headless=new','--disable-gpu','--no-sandbox','--hide-scrollbars','--window-size=1200,800','--virtual-time-budget=3000',f'--screenshot={TMP}/brut_{l}.png',page.as_uri()],capture_output=True,timeout=60)
    Image.open(TMP/f'brut_{l}.png').crop((0,0,1200,630)).save(OG/f)
    print(l,'ok')
