#!/usr/bin/env python3
"""Génère les pages anglaise, allemande et portugaise de la vitrine de précommande.

    python docs/scripts/generer_precommande.py

Le français est la source (docs/precommande.html) : precommande-en.html,
precommande-de.html et precommande-pt.html en sont des copies dont chaque
texte est remplacé par sa traduction. Ne jamais modifier ces trois fichiers à
la main : changer precommande.html, compléter les tables ci-dessous, relancer.

Comme generer_accueils.py : chaque texte français cherché doit être présent
(les blancs comptent pour un seul blanc), sinon le script s'arrête ; à la fin,
il vérifie qu'aucun mot français courant ne reste dans le texte visible. Les
textes du formulaire qui s'ouvre (total, messages) sont dans le script de la
page, dans les quatre langues.

Traductions écrites par Claude (2026-11-27), à faire relire par un locuteur de
chaque langue. Les sens des mots sur les cartes sont en français : les pages
traduites le disent, et les liens vers des pages en français seulement le
signalent.
"""
import pathlib
import re
import sys

DOCS = pathlib.Path(__file__).resolve().parents[1]
SOURCE = DOCS / "precommande.html"
NB = " "

# Textes communs aux quatre fiches de boîte (remplacés partout où ils sont).
LANGUES = {
    "en": {
        "accueil": "en.html",
        "theme": ("Switch to dark mode", "🌙 Dark"),
        "langue": "Language",
        "textes": [
            ("Les boîtes du Wuertduell · Précommande", "Wuertduell boxes · Pre-order"),
            ("Le Wuertduell, le jeu de cartes en luxembourgeois : quatre boîtes de 54 cartes à 9,90 € la boîte, à réserver sans rien payer.",
             "The Wuertduell, the card game in Luxembourgish: four boxes of 54 cards at €9.90 a box, to reserve without paying anything."),
            ('<p class="surtitre">Précommande</p>', '<p class="surtitre">Pre-order</p>'),
            ("<h1>Les boîtes du Wuertduell</h1>", "<h1>The Wuertduell boxes</h1>"),
            (f"Le jeu de cartes en luxembourgeois, à jouer à deux. Réservez vos boîtes sans rien payer{NB}: l'impression part dès que 350{NB}boîtes sont réservées.",
             "The card game in Luxembourgish, for two players. Reserve your boxes without paying anything: printing starts as soon as 350 boxes are reserved."),
            ('aria-label="Les quatre boîtes"', 'aria-label="The four boxes"'),
            (f"<strong>Précommande</strong><span>Sortie{NB}: date à venir</span>", "<strong>Pre-order</strong><span>Release date to be announced</span>"),
            ('title="Sens des mots en français"', 'title="Word meanings in French"'),
            ('<span class="etat">Réservation ouverte</span>', '<span class="etat">Reservations open</span>'),
            ("La boîte de départ du Wuertduell, bleu nuit, avec son bandeau rouge et les cartes Hond, Kaz et Brout",
             "The Wuertduell starter box, midnight blue, with its red band and the cards Hond, Kaz and Brout"),
            ("L'extension du Wuertduell, bleu nuit, avec son bandeau vert et les cartes Buch, Mound et Sonn",
             "The Wuertduell expansion, midnight blue, with its green band and the cards Buch, Mound and Sonn"),
            ("La boîte Niveau A1 du Wuertduell, bleu nuit, avec son bandeau bleu et les cartes Kaffi, Apel et Bus",
             "The Wuertduell Level A1 box, midnight blue, with its blue band and the cards Kaffi, Apel and Bus"),
            ("La boîte Niveau A2 du Wuertduell, bleu nuit, avec son bandeau violet et trois cartes, dont Schnéi (neige)",
             "The Wuertduell Level A2 box, midnight blue, with its purple band and three cards, including Schnéi (snow)"),
            ("Les quatre boîtes du Wuertduell côte à côte, bandeaux rouge, vert, bleu et violet",
             "The four Wuertduell boxes side by side, with red, green, blue and purple bands"),
            (f"Maquette du présentoir de comptoir en carton bleu nuit{NB}: en haut, le panneau WUERTDUELL avec quatre cartes et un code pour lire la règle{NB}; en bas, douze boîtes en quatre colonnes de couleur",
             "Mock-up of the midnight-blue cardboard counter display: at the top, the WUERTDUELL panel with four cards and a code to read the rules; at the bottom, twelve boxes in four coloured columns"),
            ("La boîte de départ</h2>", "The starter box</h2>"),
            ("L'extension</h2>", "The expansion</h2>"),
            ("Niveau A1</h2>", "Level A1</h2>"),
            ("Niveau A2</h2>", "Level A2</h2>"),
            (f"Déieren contre Iessen, les animaux contre ce qu'on mange. 54{NB}cartes.", "Déieren versus Iessen, animals versus the things we eat. 54 cards."),
            (f"Schoul contre Natur, l'école contre la nature. 54{NB}cartes.", "Schoul versus Natur, school versus nature. 54 cards."),
            (f"Doheem contre Dobaussen, les premiers mots, à la maison et dehors. 54{NB}cartes.", "Doheem versus Dobaussen, first words, at home and outside. 54 cards."),
            (f"Doheem contre Dobaussen, la suite, pour aller plus loin. 54{NB}cartes.", "Doheem versus Dobaussen, the next step, to go further. 54 cards."),
            (f"<s>39,60{NB}€</s>34,90{NB}€", "<s>€39.60</s>€34.90"),
            (f'<p class="prix">9,90{NB}€</p>', '<p class="prix">€9.90</p>'),
            (f"4,70{NB}€ d'économie", "Save €4.70"),
            ("Réserver la collection</a>", "Reserve the collection</a>"),
            ('">Réserver</a>', '">Reserve</a>'),
            ("<h2>La collection complète</h2>", "<h2>The complete collection</h2>"),
            (f"Les quatre boîtes, 216{NB}cartes{NB}: la boîte de départ, l'extension, Niveau{NB}A1 et Niveau{NB}A2. Pour ceux qui veulent tout, tout de suite.",
             "All four boxes, 216 cards: the starter box, the expansion, Level A1 and Level A2. For those who want it all, right away."),
            (">Commerces et associations</p>", ">Shops and associations</p>"),
            ("Le présentoir de comptoir</h2>", "The counter display</h2>"),
            (f"Douze boîtes, trois de chaque sorte, dans un présentoir en carton prêt à poser sur un comptoir{NB}: pour une librairie, un magasin, une bibliothèque ou un stand.",
             "Twelve boxes, three of each kind, in a cardboard display ready to place on a counter: for a bookshop, a shop, a library or a stand."),
            ("Le prix se fixe avec vous, selon le nombre de présentoirs.", "We set the price with you, depending on the number of displays."),
            (">Nous écrire</a>", ">Write to us</a>"),
            ('aria-label="Comment marche la précommande"', 'aria-label="How the pre-order works"'),
            (f"<strong>Vous ne payez rien aujourd'hui.</strong> Dès que 350{NB}boîtes sont réservées, nous vous écrivons pour confirmer et payer votre réservation. Le port s'ajoute au prix de la boîte.",
             "<strong>You pay nothing today.</strong> As soon as 350 boxes are reserved, we write to you to confirm and pay for your reservation. Shipping is added to the price of the box."),
            ("Chaque boîte se joue seule, dès qu'on l'ouvre, ou avec les autres. Un code dans la boîte ajoute ses cartes à votre carnet dans l'application. <a href=\"commander.html\">Tout savoir sur la précommande</a>",
             "Each box can be played on its own, as soon as you open it, or with the others. A code in the box adds its cards to your notebook in the app. The word meanings on the cards are in French. <a href=\"commander.html\" hreflang=\"fr\">All about the pre-order (in French)</a>"),
            ('<a href="./">Accueil</a> · <a href="regle-du-jeu.html">Règle du jeu</a> · <a href="faq.html">Aide</a>',
             '<a href="en.html">Home</a> · <a href="regle-du-jeu.html" hreflang="fr">Rules (in French)</a> · <a href="faq.html" hreflang="fr">Help (in French)</a>'),
            # ---- le formulaire qui s'ouvre dans la fiche ----
            ('name="page" value="vitrine"', 'name="page" value="vitrine-en"'),
            (f">Combien{NB}?</label>", ">How many?</label>"),
            ('aria-label="Une de moins"', 'aria-label="One fewer"'),
            ('aria-label="Une de plus"', 'aria-label="One more"'),
            (">Votre e-mail</label>", ">Your email</label>"),
            (">Pays de livraison</label>", ">Delivery country</label>"),
            ("<option>Luxembourg</option><option>Belgique</option><option>France</option><option>Allemagne</option><option>Un autre pays</option>",
             "<option>Luxembourg</option><option>Belgium</option><option>France</option><option>Germany</option><option>Another country</option>"),
            ("<legend>Comment recevoir vos boîtes&nbsp;?</legend>", "<legend>How would you like to receive your boxes?</legend>"),
            ("Livrées chez moi (port en plus)", "Delivered to my home (shipping extra)"),
            ("Retirées lors d'une animation ou d'un stand au Luxembourg (sans frais)", "Collected at an event or a stand in Luxembourg (free)"),
            (">Réserver, sans payer</button>", ">Reserve, without paying</button>"),
            ("Rien à payer aujourd'hui, et réserver ne vous engage à rien. Votre adresse sert seulement à vous écrire quand l'impression se prépare, puis elle est effacée. Formulaire hébergé par",
             "Nothing to pay today, and reserving commits you to nothing. Your address is used only to write to you when printing is being prepared, then it is deleted. Form hosted by"),
        ],
    },
    "de": {
        "accueil": "de.html",
        "theme": ("Zum dunklen Modus wechseln", "🌙 Dunkel"),
        "langue": "Sprache",
        "textes": [
            ("Les boîtes du Wuertduell · Précommande", "Die Schachteln des Wuertduell · Vorbestellung"),
            ("Le Wuertduell, le jeu de cartes en luxembourgeois : quatre boîtes de 54 cartes à 9,90 € la boîte, à réserver sans rien payer.",
             "Das Wuertduell, das Kartenspiel auf Luxemburgisch: vier Schachteln mit je 54 Karten zu 9,90 € die Schachtel, ohne Bezahlung zu reservieren."),
            ('<p class="surtitre">Précommande</p>', '<p class="surtitre">Vorbestellung</p>'),
            ("<h1>Les boîtes du Wuertduell</h1>", "<h1>Die Schachteln des Wuertduell</h1>"),
            (f"Le jeu de cartes en luxembourgeois, à jouer à deux. Réservez vos boîtes sans rien payer{NB}: l'impression part dès que 350{NB}boîtes sont réservées.",
             "Das Kartenspiel auf Luxemburgisch, für zwei Personen. Reservieren Sie Ihre Schachteln, ohne etwas zu bezahlen: Der Druck beginnt, sobald 350 Schachteln reserviert sind."),
            ('aria-label="Les quatre boîtes"', 'aria-label="Die vier Schachteln"'),
            (f"<strong>Précommande</strong><span>Sortie{NB}: date à venir</span>", "<strong>Vorbestellung</strong><span>Erscheinungsdatum folgt</span>"),
            ('title="Sens des mots en français"', 'title="Wortbedeutungen auf Französisch"'),
            ('<span class="etat">Réservation ouverte</span>', '<span class="etat">Reservierung offen</span>'),
            ("La boîte de départ du Wuertduell, bleu nuit, avec son bandeau rouge et les cartes Hond, Kaz et Brout",
             "Die Startschachtel des Wuertduell, nachtblau, mit rotem Band und den Karten Hond, Kaz und Brout"),
            ("L'extension du Wuertduell, bleu nuit, avec son bandeau vert et les cartes Buch, Mound et Sonn",
             "Die Erweiterung des Wuertduell, nachtblau, mit grünem Band und den Karten Buch, Mound und Sonn"),
            ("La boîte Niveau A1 du Wuertduell, bleu nuit, avec son bandeau bleu et les cartes Kaffi, Apel et Bus",
             "Die Schachtel Niveau A1 des Wuertduell, nachtblau, mit blauem Band und den Karten Kaffi, Apel und Bus"),
            ("La boîte Niveau A2 du Wuertduell, bleu nuit, avec son bandeau violet et trois cartes, dont Schnéi (neige)",
             "Die Schachtel Niveau A2 des Wuertduell, nachtblau, mit violettem Band und drei Karten, darunter Schnéi (Schnee)"),
            ("Les quatre boîtes du Wuertduell côte à côte, bandeaux rouge, vert, bleu et violet",
             "Die vier Schachteln des Wuertduell nebeneinander, mit rotem, grünem, blauem und violettem Band"),
            (f"Maquette du présentoir de comptoir en carton bleu nuit{NB}: en haut, le panneau WUERTDUELL avec quatre cartes et un code pour lire la règle{NB}; en bas, douze boîtes en quatre colonnes de couleur",
             "Modell des Thekendisplays aus nachtblauem Karton: oben die Tafel WUERTDUELL mit vier Karten und einem Code zu den Spielregeln; unten zwölf Schachteln in vier farbigen Reihen"),
            ("La boîte de départ</h2>", "Die Startschachtel</h2>"),
            ("L'extension</h2>", "Die Erweiterung</h2>"),
            (f"Déieren contre Iessen, les animaux contre ce qu'on mange. 54{NB}cartes.", "Déieren gegen Iessen, die Tiere gegen das, was wir essen. 54 Karten."),
            (f"Schoul contre Natur, l'école contre la nature. 54{NB}cartes.", "Schoul gegen Natur, die Schule gegen die Natur. 54 Karten."),
            (f"Doheem contre Dobaussen, les premiers mots, à la maison et dehors. 54{NB}cartes.", "Doheem gegen Dobaussen, die ersten Wörter, zu Hause und draußen. 54 Karten."),
            (f"Doheem contre Dobaussen, la suite, pour aller plus loin. 54{NB}cartes.", "Doheem gegen Dobaussen, die Fortsetzung, um weiterzukommen. 54 Karten."),
            (f"4,70{NB}€ d'économie", "4,70 € gespart"),
            ("Réserver la collection</a>", "Die Sammlung reservieren</a>"),
            ('">Réserver</a>', '">Reservieren</a>'),
            ("<h2>La collection complète</h2>", "<h2>Die komplette Sammlung</h2>"),
            (f"Les quatre boîtes, 216{NB}cartes{NB}: la boîte de départ, l'extension, Niveau{NB}A1 et Niveau{NB}A2. Pour ceux qui veulent tout, tout de suite.",
             "Alle vier Schachteln, 216 Karten: die Startschachtel, die Erweiterung, Niveau A1 und Niveau A2. Für alle, die gleich alles wollen."),
            (">Commerces et associations</p>", ">Geschäfte und Vereine</p>"),
            ("Le présentoir de comptoir</h2>", "Das Thekendisplay</h2>"),
            (f"Douze boîtes, trois de chaque sorte, dans un présentoir en carton prêt à poser sur un comptoir{NB}: pour une librairie, un magasin, une bibliothèque ou un stand.",
             "Zwölf Schachteln, drei von jeder Sorte, in einem Display aus Karton, bereit für die Theke: für eine Buchhandlung, ein Geschäft, eine Bibliothek oder einen Stand."),
            ("Le prix se fixe avec vous, selon le nombre de présentoirs.", "Den Preis legen wir mit Ihnen fest, je nach Anzahl der Displays."),
            (">Nous écrire</a>", ">Schreiben Sie uns</a>"),
            ('aria-label="Comment marche la précommande"', 'aria-label="So funktioniert die Vorbestellung"'),
            (f"<strong>Vous ne payez rien aujourd'hui.</strong> Dès que 350{NB}boîtes sont réservées, nous vous écrivons pour confirmer et payer votre réservation. Le port s'ajoute au prix de la boîte.",
             "<strong>Heute bezahlen Sie nichts.</strong> Sobald 350 Schachteln reserviert sind, schreiben wir Ihnen, damit Sie Ihre Reservierung bestätigen und bezahlen. Der Versand kommt zum Preis der Schachtel hinzu."),
            ("Chaque boîte se joue seule, dès qu'on l'ouvre, ou avec les autres. Un code dans la boîte ajoute ses cartes à votre carnet dans l'application. <a href=\"commander.html\">Tout savoir sur la précommande</a>",
             "Jede Schachtel lässt sich allein spielen, sobald man sie öffnet, oder mit den anderen. Ein Code in der Schachtel fügt ihre Karten Ihrem Heft in der App hinzu. Die Wortbedeutungen auf den Karten sind auf Französisch. <a href=\"commander.html\" hreflang=\"fr\">Alles zur Vorbestellung (auf Französisch)</a>"),
            ('<a href="./">Accueil</a> · <a href="regle-du-jeu.html">Règle du jeu</a> · <a href="faq.html">Aide</a>',
             '<a href="de.html">Startseite</a> · <a href="regle-du-jeu.html" hreflang="fr">Spielregeln (auf Französisch)</a> · <a href="faq.html" hreflang="fr">Hilfe (auf Französisch)</a>'),
            ('name="page" value="vitrine"', 'name="page" value="vitrine-de"'),
            (f">Combien{NB}?</label>", ">Wie viele?</label>"),
            ('aria-label="Une de moins"', 'aria-label="Eine weniger"'),
            ('aria-label="Une de plus"', 'aria-label="Eine mehr"'),
            (">Votre e-mail</label>", ">Ihre E-Mail-Adresse</label>"),
            (">Pays de livraison</label>", ">Lieferland</label>"),
            ("<option>Luxembourg</option><option>Belgique</option><option>France</option><option>Allemagne</option><option>Un autre pays</option>",
             "<option>Luxemburg</option><option>Belgien</option><option>Frankreich</option><option>Deutschland</option><option>Ein anderes Land</option>"),
            ("<legend>Comment recevoir vos boîtes&nbsp;?</legend>", "<legend>Wie möchten Sie Ihre Schachteln erhalten?</legend>"),
            ("Livrées chez moi (port en plus)", "Nach Hause geliefert (zuzüglich Versand)"),
            ("Retirées lors d'une animation ou d'un stand au Luxembourg (sans frais)", "Abgeholt bei einer Veranstaltung oder an einem Stand in Luxemburg (kostenlos)"),
            (">Réserver, sans payer</button>", ">Reservieren, ohne zu bezahlen</button>"),
            ("Rien à payer aujourd'hui, et réserver ne vous engage à rien. Votre adresse sert seulement à vous écrire quand l'impression se prépare, puis elle est effacée. Formulaire hébergé par",
             "Heute nichts zu bezahlen, und die Reservierung verpflichtet Sie zu nichts. Ihre Adresse dient nur dazu, Ihnen zu schreiben, wenn der Druck vorbereitet wird; danach wird sie gelöscht. Formular gehostet von"),
        ],
    },
    "pt": {
        "accueil": "pt.html",
        "theme": ("Mudar para o modo escuro", "🌙 Escuro"),
        "langue": "Idioma",
        "textes": [
            ("Les boîtes du Wuertduell · Précommande", "As caixas do Wuertduell · Pré-encomenda"),
            ("Le Wuertduell, le jeu de cartes en luxembourgeois : quatre boîtes de 54 cartes à 9,90 € la boîte, à réserver sans rien payer.",
             "O Wuertduell, o jogo de cartas em luxemburguês: quatro caixas de 54 cartas a 9,90 € a caixa, para reservar sem pagar nada."),
            ('<p class="surtitre">Précommande</p>', '<p class="surtitre">Pré-encomenda</p>'),
            ("<h1>Les boîtes du Wuertduell</h1>", "<h1>As caixas do Wuertduell</h1>"),
            (f"Le jeu de cartes en luxembourgeois, à jouer à deux. Réservez vos boîtes sans rien payer{NB}: l'impression part dès que 350{NB}boîtes sont réservées.",
             "O jogo de cartas em luxemburguês, para jogar a dois. Reserve as suas caixas sem pagar nada: a impressão começa assim que 350 caixas estiverem reservadas."),
            ('aria-label="Les quatre boîtes"', 'aria-label="As quatro caixas"'),
            (f"<strong>Précommande</strong><span>Sortie{NB}: date à venir</span>", "<strong>Pré-encomenda</strong><span>Data de lançamento a anunciar</span>"),
            ('title="Sens des mots en français"', 'title="Significados das palavras em francês"'),
            ('<span class="etat">Réservation ouverte</span>', '<span class="etat">Reservas abertas</span>'),
            ("La boîte de départ du Wuertduell, bleu nuit, avec son bandeau rouge et les cartes Hond, Kaz et Brout",
             "A caixa inicial do Wuertduell, azul-noite, com a faixa vermelha e as cartas Hond, Kaz e Brout"),
            ("L'extension du Wuertduell, bleu nuit, avec son bandeau vert et les cartes Buch, Mound et Sonn",
             "A extensão do Wuertduell, azul-noite, com a faixa verde e as cartas Buch, Mound e Sonn"),
            ("La boîte Niveau A1 du Wuertduell, bleu nuit, avec son bandeau bleu et les cartes Kaffi, Apel et Bus",
             "A caixa Nível A1 do Wuertduell, azul-noite, com a faixa azul e as cartas Kaffi, Apel e Bus"),
            ("La boîte Niveau A2 du Wuertduell, bleu nuit, avec son bandeau violet et trois cartes, dont Schnéi (neige)",
             "A caixa Nível A2 do Wuertduell, azul-noite, com a faixa violeta e três cartas, entre elas Schnéi (neve)"),
            ("Les quatre boîtes du Wuertduell côte à côte, bandeaux rouge, vert, bleu et violet",
             "As quatro caixas do Wuertduell lado a lado, com faixas vermelha, verde, azul e violeta"),
            (f"Maquette du présentoir de comptoir en carton bleu nuit{NB}: en haut, le panneau WUERTDUELL avec quatre cartes et un code pour lire la règle{NB}; en bas, douze boîtes en quatre colonnes de couleur",
             "Maquete do expositor de balcão em cartão azul-noite: em cima, o painel WUERTDUELL com quatro cartas e um código para ler as regras; em baixo, doze caixas em quatro colunas de cor"),
            ("La boîte de départ</h2>", "A caixa inicial</h2>"),
            ("L'extension</h2>", "A extensão</h2>"),
            ("Niveau A1</h2>", "Nível A1</h2>"),
            ("Niveau A2</h2>", "Nível A2</h2>"),
            (f"Déieren contre Iessen, les animaux contre ce qu'on mange. 54{NB}cartes.", "Déieren contra Iessen, os animais contra o que se come. 54 cartas."),
            (f"Schoul contre Natur, l'école contre la nature. 54{NB}cartes.", "Schoul contra Natur, a escola contra a natureza. 54 cartas."),
            (f"Doheem contre Dobaussen, les premiers mots, à la maison et dehors. 54{NB}cartes.", "Doheem contra Dobaussen, as primeiras palavras, em casa e lá fora. 54 cartas."),
            (f"Doheem contre Dobaussen, la suite, pour aller plus loin. 54{NB}cartes.", "Doheem contra Dobaussen, a continuação, para ir mais longe. 54 cartas."),
            (f"4,70{NB}€ d'économie", "Poupe 4,70 €"),
            ("Réserver la collection</a>", "Reservar a coleção</a>"),
            ('">Réserver</a>', '">Reservar</a>'),
            ("<h2>La collection complète</h2>", "<h2>A coleção completa</h2>"),
            (f"Les quatre boîtes, 216{NB}cartes{NB}: la boîte de départ, l'extension, Niveau{NB}A1 et Niveau{NB}A2. Pour ceux qui veulent tout, tout de suite.",
             "As quatro caixas, 216 cartas: a caixa inicial, a extensão, Nível A1 e Nível A2. Para quem quer tudo, já."),
            (">Commerces et associations</p>", ">Lojas e associações</p>"),
            ("Le présentoir de comptoir</h2>", "O expositor de balcão</h2>"),
            (f"Douze boîtes, trois de chaque sorte, dans un présentoir en carton prêt à poser sur un comptoir{NB}: pour une librairie, un magasin, une bibliothèque ou un stand.",
             "Doze caixas, três de cada tipo, num expositor de cartão pronto a pousar num balcão: para uma livraria, uma loja, uma biblioteca ou um stand."),
            ("Le prix se fixe avec vous, selon le nombre de présentoirs.", "O preço é definido consigo, conforme o número de expositores."),
            (">Nous écrire</a>", ">Escreva-nos</a>"),
            ('aria-label="Comment marche la précommande"', 'aria-label="Como funciona a pré-encomenda"'),
            (f"<strong>Vous ne payez rien aujourd'hui.</strong> Dès que 350{NB}boîtes sont réservées, nous vous écrivons pour confirmer et payer votre réservation. Le port s'ajoute au prix de la boîte.",
             "<strong>Hoje não paga nada.</strong> Assim que 350 caixas estiverem reservadas, escrevemos-lhe para confirmar e pagar a sua reserva. O envio acresce ao preço da caixa."),
            ("Chaque boîte se joue seule, dès qu'on l'ouvre, ou avec les autres. Un code dans la boîte ajoute ses cartes à votre carnet dans l'application. <a href=\"commander.html\">Tout savoir sur la précommande</a>",
             "Cada caixa joga-se sozinha, assim que se abre, ou com as outras. Um código na caixa junta as suas cartas ao seu caderno na aplicação. Os significados das palavras nas cartas estão em francês. <a href=\"commander.html\" hreflang=\"fr\">Tudo sobre a pré-encomenda (em francês)</a>"),
            ('<a href="./">Accueil</a> · <a href="regle-du-jeu.html">Règle du jeu</a> · <a href="faq.html">Aide</a>',
             '<a href="pt.html">Início</a> · <a href="regle-du-jeu.html" hreflang="fr">Regras (em francês)</a> · <a href="faq.html" hreflang="fr">Ajuda (em francês)</a>'),
            ('name="page" value="vitrine"', 'name="page" value="vitrine-pt"'),
            (f">Combien{NB}?</label>", ">Quantas?</label>"),
            ('aria-label="Une de moins"', 'aria-label="Menos uma"'),
            ('aria-label="Une de plus"', 'aria-label="Mais uma"'),
            (">Votre e-mail</label>", ">O seu e-mail</label>"),
            (">Pays de livraison</label>", ">País de entrega</label>"),
            ("<option>Luxembourg</option><option>Belgique</option><option>France</option><option>Allemagne</option><option>Un autre pays</option>",
             "<option>Luxemburgo</option><option>Bélgica</option><option>França</option><option>Alemanha</option><option>Outro país</option>"),
            ("<legend>Comment recevoir vos boîtes&nbsp;?</legend>", "<legend>Como quer receber as suas caixas?</legend>"),
            ("Livrées chez moi (port en plus)", "Entregues em casa (envio à parte)"),
            ("Retirées lors d'une animation ou d'un stand au Luxembourg (sans frais)", "Levantadas numa animação ou num stand no Luxemburgo (sem custos)"),
            (">Réserver, sans payer</button>", ">Reservar, sem pagar</button>"),
            ("Rien à payer aujourd'hui, et réserver ne vous engage à rien. Votre adresse sert seulement à vous écrire quand l'impression se prépare, puis elle est effacée. Formulaire hébergé par",
             "Nada a pagar hoje, e reservar não o compromete a nada. O seu endereço serve apenas para lhe escrevermos quando a impressão estiver a ser preparada, e depois é apagado. Formulário alojado pela"),
        ],
    },
}

MOTS_FRANCAIS = ["vous", "votre", "vos", "boîte", "boîtes", "Réserver", "Précommande", "avec",
                 "cartes", "Sortie", "présentoir", "Nous", "Combien", "Accueil", "Aide"]


def motif(texte):
    """Le texte français comme expression régulière : tout blanc (espace insécable compris) vaut \\s+."""
    morceaux = re.split(r"(?:\s|\u00a0|&nbsp;)+", texte.strip())
    return r"(?:\s|\u00a0|&nbsp;)+".join(re.escape(m) for m in morceaux)


def remplacer(page, avant, apres, nom):
    rx = re.compile(motif(avant))
    if not rx.search(page):
        sys.exit(f"[{nom}] texte français introuvable dans precommande.html :\n  {avant[:120]}")
    return rx.sub(lambda _: apres, page)


def generer(langue, cfg, source):
    page = source
    page = remplacer(page, '<html lang="fr">', f'<html lang="{langue}">', langue)
    page = re.sub(r"<!--\s+Vitrine des boîtes en précommande.*?-->",
                  "<!--\n  GÉNÉRÉ par docs/scripts/generer_precommande.py depuis precommande.html :\n"
                  "  ne pas modifier à la main. Traduction écrite par Claude, à faire relire.\n-->",
                  page, count=1, flags=re.S)
    # en-tête : le logo ramène à l'accueil de la langue, le sélecteur se recale
    page = remplacer(page, '<a class="marque" href="./">', f'<a class="marque" href="{cfg["accueil"]}">', langue)
    page = remplacer(page, 'aria-label="Langue"', f'aria-label="{cfg["langue"]}"', langue)
    page = remplacer(page, "<strong>FR</strong>", '<a href="precommande.html" hreflang="fr" lang="fr">FR</a>', langue)
    page = remplacer(page, f'<a href="precommande-{langue}.html" hreflang="{langue}" lang="{langue}">{langue.upper()}</a>',
                     f"<strong>{langue.upper()}</strong>", langue)
    page = remplacer(page, 'aria-label="Passer en mode sombre">🌙 Fond sombre</button>',
                     f'aria-label="{cfg["theme"][0]}">{cfg["theme"][1]}</button>', langue)
    for avant, apres in cfg["textes"]:
        page = remplacer(page, avant, apres, langue)

    visible = re.sub(r"<(style|script)\b.*?</\1>", " ", page, flags=re.S)
    visible = re.sub(r"<!--.*?-->", " ", visible, flags=re.S)
    attributs = " ".join(re.findall(r'(?:alt|title|aria-label|content)="([^"]*)"', visible))
    visible = re.sub(r"<[^>]+>", " ", visible) + " " + attributs
    restes = [m for m in MOTS_FRANCAIS if re.search(rf"(?<![\w']){re.escape(m)}(?![\w])", visible)]
    if restes:
        sys.exit(f"[{langue}] mots français restants dans le texte visible : {restes}")
    return page


def main():
    source = SOURCE.read_text(encoding="utf-8")
    for langue, cfg in LANGUES.items():
        sortie = DOCS / f"precommande-{langue}.html"
        sortie.write_text(generer(langue, cfg, source), encoding="utf-8")
        print(f"{sortie.name} : {sortie.stat().st_size:,} octets")


if __name__ == "__main__":
    main()
