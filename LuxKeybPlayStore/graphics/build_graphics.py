#!/usr/bin/env python3
"""Fabrique les éléments graphiques de la fiche Play Store.

    python3 build_graphics.py            # tout
    python3 build_graphics.py icon       # icône 512 seule
    python3 build_graphics.py feature    # image mise en avant seule
    python3 build_graphics.py shots      # captures téléphone seules, dans les cinq langues
    python3 build_graphics.py jeux       # un visuel par jeu (hors des 8 de la Console)
    python3 build_graphics.py carnet     # boîte de Leitner et éventail de cartes
    python3 build_graphics.py check      # vérifie les contraintes Play Console

Produit, dans `feature-graphic/`, les fichiers à envoyer à la Play Console.
Chacun porte le nom de l'emplacement du formulaire où il va, pour qu'il n'y ait
rien à retrouver au moment de l'envoi :

  Icône de l'application.png                        depuis Logos/luxembourg-logo-hd.png
  Image de présentation.png                          depuis feature_graphic_source.html
  <langue>/Captures d'écran pour téléphone 1 (Suggestions).png .. 8 (Installation).png
                                                      depuis captures-emulateur-pixel9/

`<langue>` est l'une des cinq langues de l'interface, nommée comme les textes
(`texts/fr-FR/` va avec `feature-graphic/fr-FR/`). Le numéro des captures est
leur ordre d'envoi ; le nom dit aussi ce que chacune montre, en un mot et en
français dans toutes les langues (le tableau `CAPTURES` ci-dessous), la légende
incrustée étant, elle, dans la langue de la fiche (`LEGENDES`).

Contraintes de la Console, toutes vérifiables avec `check` :
icône 512x512 et moins de 1 Mo ; image de présentation 1024x500 et moins de
15 Mo ; 2 à 8 captures en 16:9 ou 9:16, chaque côté entre 320 et 3840 px et
moins de 8 Mo. Les huit captures font 1080x1920, donc au-dessus du 1080x1080
exigé pour que l'application soit promouvable : il en faut au moins quatre.

Les captures sources sont dans `captures-emulateur-pixel9/`, natives 1080 px de
large (émulateur Pixel 9, 1080x2424, sous la 29.2.0) : rien n'est agrandi ici.
Sur la branche de la dictée, les trois captures de clavier ont été refaites
le 2 octobre 2026 sous la 30.1.0 avec le micro, dans Messages. La capture de
la dictée (`20-clavier-dictee-luxasr`, 3 octobre, 33.0.0) assemble deux
écrans réels : le bandeau LuxASR et le micro d'une dictée en cours, et la
phrase de la bulle, l'émulateur ne pouvant pas entendre de voix. Depuis la
33.0.0 elle prend la deuxième place et le clavier numérique sort des huit
emplacements : la Console n'en accepte pas plus.
Leur nom dit ce qu'elles montrent, dans l'ordre d'envoi. Les trois captures de
clavier (suggestions, accents, numérique) sont prises dans le champ d'essai de
l'onglet Démarrage puis recadrées (`-recadre`, 1080x1026) sur champ de saisie +
barre de suggestions + clavier ; l'original plein écran reste à côté. Les cinq
autres sont des écrans entiers, dont deux ouvrent une fiche ou une carte en
feuille du bas (arrière-plan assombri, c'est l'état réel de l'application). À
refaire à chaque changement visible.

Ces cinq écrans de l'application existent dans chaque langue, sous
`captures-emulateur-pixel9/<langue>/` (même nom de fichier que leur ancêtre
français de la racine, qui ne sert plus qu'aux visuels hors Console). Ils ont
été pris le 5 octobre 2026 sous la 33.1.0, la langue imposée par
`adb shell cmd locale set-app-locales com.potomitan.luxkeyboard --locales <l>`,
avec le même carnet dans les cinq langues. L'installation montre un premier
lancement, clavier activé mais pas encore choisi : une fois le clavier choisi,
l'application passe d'elle-même sur l'accueil « Haut » et l'ancien écran
« 2/3 » n'existe plus.

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
SHOTS = HERE / "captures-emulateur-pixel9"
LOGO = REPO / "Logos" / "luxembourg-logo-hd.png"
OUT = HERE / "feature-graphic"
ICON = OUT / "Icône de l'application.png"

# marge de rendu qui absorbe la hauteur de fenêtre non peinte par Chrome
CHROME_GUTTER = 200

ROUGE, BLEU, ENCRE, PAPIER = "#ED2939", "#00A1DE", "#1F2933", "#F5F5F3"

# Les huit captures de la Console, dans l'ordre d'envoi : (nom, source). Le nom
# de sortie porte le contenu entre parenthèses, en français dans toutes les
# langues : la Console lit le numéro (ordre d'envoi), quiconque parcourt le
# dossier lit le contenu.
CAPTURES = [
    ("Captures d'écran pour téléphone 1 (Suggestions)", "08-clavier-suggestions-lb-fr-recadre.png"),
    ("Captures d'écran pour téléphone 2 (Dictée)", "20-clavier-dictee-luxasr-recadre.png"),
    ("Captures d'écran pour téléphone 3 (Carnet)", "07-carnet-carte-moien.png"),
    ("Captures d'écran pour téléphone 4 (Jeux)", "02-jeux-onglet-spiller.png"),
    ("Captures d'écran pour téléphone 5 (Wierderbuch)", "06-wierderbuch-fiche-gromperekichelchen.png"),
    ("Captures d'écran pour téléphone 6 (Accents)", "09-clavier-diacritiques-appui-long-recadre.png"),
    ("Captures d'écran pour téléphone 7 (Progression)", "04-progression-onglet-mai-letzebuergesch.png"),
    ("Captures d'écran pour téléphone 8 (Installation)", "01-installation-onglet-demarrage.png"),
]

# Une fiche par langue de l'interface, rangée comme les textes (`texts/<langue>/`).
# Chaque langue a ses captures d'écrans de l'application dans
# `captures-emulateur-pixel9/<langue>/` ; les captures de clavier (1, 2, 6) sont
# communes et restent à la racine de ce dossier : le clavier est luxembourgeois
# quelle que soit la langue de l'application, et Messages suit celle du
# téléphone. Pour chaque capture : (kicker, titre, sous-titre), puis le pied.
LEGENDES = {
    "fr-FR": ([
        ("Suggestions", "Il vous souffle les mots",
         "Le luxembourgeois d'abord, et en option le français pour les emprunts."),
        ("Dictée", "Parlez, il écrit en luxembourgeois",
         "Votre voix part à LuxASR, Université du Luxembourg, seulement quand vous touchez le micro."),
        ("Carnet", "Chaque mot appris devient une carte",
         "Sens, phrase d'exemple et traduction officielle, sur une carte à collectionner."),
        ("Jeux", "Sept jeux pour élargir son vocabulaire",
         "Tous les jeux versent leurs mots dans le même carnet, révisable à intervalle régulier."),
        ("Wierderbuch", "Un dictionnaire dans le clavier",
         "Près de 89 000 mots, luxembourgeois et français, avec des phrases d'exemple officielles."),
        ("Accents", "é ä ë ö ü ont leur propre touche",
         "Les autres accents (è, à, ê, ô) restent sous un appui long."),
        ("Progression", "Chaque mot fait monter votre niveau",
         "D'Ufänker à Sproochenmeeschter, selon la part du dictionnaire déjà employée."),
        ("Installation", "Trois étapes, un clavier d'essai",
         "L'application ouvre elle-même les bons écrans de réglages Android."),
    ], "gratuit, sans publicité"),
    "lb": ([
        ("Virschléi", "Si seet Iech d'Wierder vir",
         "Fir d'éischt Lëtzebuergesch, an op Wonsch Franséisch fir déi geléinte Wierder."),
        ("Diktat", "Schwätzt, si schreift op Lëtzebuergesch",
         "Är Stëmm geet un LuxASR, Universitéit Lëtzebuerg, just wann Dir op de Mikro dréckt."),
        ("Carnet", "All geléiert Wuert gëtt eng Kaart",
         "Bedeitung, Beispillsaz an offiziell Iwwersetzung, op enger Kaart zum Sammelen."),
        ("Spiller", "Siwe Spiller fir méi Wierder ze kennen",
         "All Spiller fëllen deeselwechte Carnet, deen Dir regelméisseg widderhuele kënnt."),
        ("Wierderbuch", "En Dictionnaire an der Tastatur",
         "Bal 89 000 Wierder, Lëtzebuergesch a Franséisch, mat offiziellen Beispillsätz."),
        ("Akzenter", "é ä ë ö ü hunn hir eegen Tast",
         "Déi aner Akzenter (è, à, ê, ô) sinn ënner engem laangen Drock."),
        ("Fortschrëtt", "All Wuert bréngt Iech eng Stuf méi héich",
         "Vum Ufänker bis zum Sproochenmeeschter, jee nodeem wéi vill vum Wierderbuch Dir scho benotzt."),
        ("Installatioun", "Dräi Schrëtt, eng Test-Tastatur",
         "D'App mécht selwer déi richteg Android-Astellungen op."),
    ], "gratis, ouni Reklamm"),
    "de-DE": ([
        ("Vorschläge", "Sie sagt Ihnen die Wörter vor",
         "Zuerst Luxemburgisch, auf Wunsch auch Französisch für die Lehnwörter."),
        ("Diktat", "Sprechen Sie, sie schreibt Luxemburgisch",
         "Ihre Stimme geht an LuxASR, Universität Luxemburg, nur wenn Sie das Mikrofon antippen."),
        ("Sammlung", "Jedes gelernte Wort wird zur Karte",
         "Bedeutung, Beispielsatz und offizielle Übersetzung, auf einer Sammelkarte."),
        ("Spiele", "Sieben Spiele für mehr Wortschatz",
         "Alle Spiele füllen dieselbe Sammlung, die Sie regelmäßig wiederholen können."),
        ("Wierderbuch", "Ein Wörterbuch in der Tastatur",
         "Fast 89 000 Wörter, Luxemburgisch und Deutsch, mit offiziellen Beispielsätzen."),
        ("Akzente", "é ä ë ö ü haben eine eigene Taste",
         "Die übrigen Akzente (è, à, ê, ô) liegen unter langem Drücken."),
        ("Fortschritt", "Jedes Wort bringt Sie eine Stufe weiter",
         "Vom Ufänker zum Sproochenmeeschter, je nachdem, wie viel vom Wörterbuch Sie schon benutzen."),
        ("Einrichtung", "Drei Schritte, eine Testtastatur",
         "Die App öffnet selbst die richtigen Android-Einstellungen."),
    ], "kostenlos, ohne Werbung"),
    "en-US": ([
        ("Suggestions", "It suggests the words",
         "Luxembourgish first, and French as an option for borrowed words."),
        ("Dictation", "Speak, it writes in Luxembourgish",
         "Your voice goes to LuxASR, University of Luxembourg, only when you tap the microphone."),
        ("Collection", "Every word you learn becomes a card",
         "Meaning, example sentence and official translation, on a card to collect."),
        ("Games", "Seven games to grow your vocabulary",
         "Every game adds its words to the same collection, ready for regular review."),
        ("Wierderbuch", "A dictionary inside the keyboard",
         "Over 85,000 words, Luxembourgish and English, with official example sentences."),
        ("Accents", "é ä ë ö ü have their own key",
         "The other accents (è, à, ê, ô) are a long press away."),
        ("Progress", "Every word raises your level",
         "From Ufänker to Sproochenmeeschter, by how much of the dictionary you already use."),
        ("Setup", "Three steps, a practice keyboard",
         "The app opens the right Android settings screens for you."),
    ], "free, no ads"),
    "pt-PT": ([
        ("Sugestões", "Ele sopra-lhe as palavras",
         "Primeiro o luxemburguês e, se quiser, o francês para as palavras emprestadas."),
        ("Ditado", "Fale, ele escreve em luxemburguês",
         "A sua voz vai para o LuxASR, Universidade do Luxemburgo, só quando toca no microfone."),
        ("Coleção", "Cada palavra aprendida torna-se uma carta",
         "Significado, frase de exemplo e tradução oficial, numa carta para colecionar."),
        ("Jogos", "Sete jogos para alargar o vocabulário",
         "Todos os jogos juntam as palavras na mesma coleção, para rever regularmente."),
        ("Wierderbuch", "Um dicionário dentro do teclado",
         "Quase 88 000 palavras, luxemburguês e português, com frases de exemplo oficiais."),
        ("Acentos", "é ä ë ö ü têm a sua própria tecla",
         "Os outros acentos (è, à, ê, ô) ficam a um toque longo."),
        ("Progresso", "Cada palavra faz subir o seu nível",
         "De Ufänker a Sproochenmeeschter, conforme a parte do dicionário que já usa."),
        ("Instalação", "Três passos, um teclado de teste",
         "A aplicação abre sozinha os ecrãs certos das definições do Android."),
    ], "gratuito, sem publicidade"),
}
LANGUES = list(LEGENDES)


def specs_de(langue: str):
    """Les huit (sortie, source, frame, kicker, titre, sous-titre) d'une langue."""
    legendes, _ = LEGENDES[langue]
    return [(nom, src, None, *leg) for (nom, src), leg in zip(CAPTURES, legendes)]


# Les visuels de jeux et du carnet, hors Console, restent en français.
SPECS = specs_de("fr-FR")

# Hors des huit emplacements de la Console (limite de 8) : un visuel par jeu,
# pour le site, les réseaux et la fiche complète. Même gabarit, même format.
JEUX = [
    ("Jeu 1 (Wuertsich)", "11-jeu-wuertsich-mots-caches.png", None, "Jeu",
     "Retrouvez les mots cachés",
     "Une grille de lettres, des mots à faire glisser du doigt, avec leur traduction française."),
    ("Jeu 2 (Wuertmix)", "12-jeu-wuertmix-lettres-dans-l-ordre.png", None, "Jeu",
     "Remettez les lettres dans l'ordre",
     "La première et la dernière lettre sont données, le sens en français sert d'indice."),
    ("Jeu 3 (Wuertriet)", "13-jeu-wuertriet-mot-de-5-lettres.png", None, "Jeu",
     "Devinez le mot en six essais",
     "Cinq lettres, trois couleurs, et un mot qui doit exister dans le dictionnaire luxembourgeois."),
    ("Jeu 4 (Wuertlück)", "14-jeu-wuertlueck-phrase-a-trou.png", None, "Jeu",
     "Complétez la vraie phrase",
     "Une phrase réelle du corpus, un mot manquant, quatre propositions dont une seule est de l'auteur."),
    ("Jeu 5 (Zuelwuert)", "15-jeu-zuelwuert-nombre-en-lettres.png", None, "Jeu",
     "Écrivez le résultat en toutes lettres",
     "Une multiplication, quatre orthographes : la règle d'Eifel fait toute la difficulté."),
    ("Jeu 6 (Kräizwuert)", "16-jeu-kraizwuert-mots-croises.png", None, "Jeu",
     "Des mots croisés à écrire soi-même",
     "Définitions en français, accents et majuscules s'apprennent en les écrivant."),
    ("Jeu 7 (Wuertplaz)", "17-jeu-wuertplaz-mots-a-placer.png", None, "Jeu",
     "Placez les mots dans la grille",
     "Aucune définition : les longueurs et les croisements suffisent, le sens se révèle une fois le mot placé."),
]

# La révision espacée du carnet : la boîte, puis les cartes d'un casier.
CARNET = [
    ("Carnet 1 (Boîte de Leitner)", "18-boite-de-leitner-sept-casiers.png", None, "Boîte de Leitner",
     "Sept casiers, d'un jour à acquis",
     "Une bonne réponse fait avancer la carte : elle revient de plus en plus tard, jusqu'à être acquise."),
    ("Carnet 2 (Éventail de cartes)", "19-boite-de-leitner-eventail-de-cartes.png", None, "Éventail de cartes",
     "Les cartes d'un casier en éventail",
     "Glissez pour parcourir, touchez une carte pour la lire : sens, phrase d'exemple et traduction."),
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
<footer><img src="data:image/png;base64,{icon}">Lëtzebuergesch Clavier · {pied}</footer>
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


def source_de(src: str, langue: str | None) -> pathlib.Path:
    """La capture propre à la langue si elle existe, sinon la capture commune."""
    if langue and (SHOTS / langue / src).exists():
        return SHOTS / langue / src
    return SHOTS / src


def build_shots(specs=None, langue: str | None = None) -> None:
    specs = SPECS if specs is None else specs
    out_dir = OUT / langue if langue else OUT
    pied = LEGENDES[langue or "fr-FR"][1]
    out_dir.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmpdir:
        tmp = pathlib.Path(tmpdir)
        small_icon = tmp / "icon.png"
        magick(str(ICON), "-resize", "104x104", str(small_icon))
        icon = b64(small_icon)

        for index, (name, src, frame, kicker, title, sub) in enumerate(specs, 1):
            source = source_de(src, langue)
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
                kicker=kicker, title=title, sub=sub, pied=pied,
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


def build_langues() -> None:
    for langue in LANGUES:
        build_shots(specs_de(langue), langue)


def build_games() -> None:
    build_shots(JEUX)


def build_carnet() -> None:
    build_shots(CARNET)


def build_check() -> None:
    """Confronte les fichiers produits aux contraintes de la Play Console."""
    shots = [OUT / langue / f"{name}.png" for langue in LANGUES for name, _ in CAPTURES]
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

    if not 2 <= len(CAPTURES) <= 8:
        problems.append(f"{len(CAPTURES)} captures, la Console en veut 2 à 8")
    promouvables = 0
    for path in shots:
        if not path.exists():
            problems.append(f"{path.relative_to(OUT)} : absent")
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
    if promouvables < 4 * len(LANGUES):
        problems.append(f"{promouvables} captures au moins 1080x1080, il en faut 4 "
                        "pour que l'application soit promouvable")

    for name, *_ in JEUX + CARNET:
        path = OUT / f"{name}.png"
        if not path.exists():
            problems.append(f"{path.name} : absent")
        elif png_header(path)[:2] != (1080, 1920):
            problems.append(f"{path.name} : pas en 1080x1920")

    for problem in problems:
        print(f"  ✗ {problem}")
    if problems:
        sys.exit(f"{len(problems)} problème(s)")
    print(f"check  ok : icône, image de présentation, {len(CAPTURES)} captures dans chacune des {len(LANGUES)} langues et {len(JEUX) + len(CARNET)} visuels de jeux et de carnet conformes")


def main(argv: list[str]) -> int:
    for tool in ("google-chrome", "convert"):
        if not shutil.which(tool):
            sys.exit(f"{tool} introuvable")
    targets = argv[1:] or ["icon", "feature", "shots", "jeux", "carnet", "check"]
    known = {"icon": build_icon, "feature": build_feature,
             "shots": build_langues, "jeux": build_games, "carnet": build_carnet, "check": build_check}
    for target in targets:
        if target not in known:
            sys.exit(f"cible inconnue : {target} (icon | feature | shots | jeux | carnet | check)")
        known[target]()
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
