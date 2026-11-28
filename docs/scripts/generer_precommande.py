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
            ("Wuertduell, le jeu de cartes pour apprendre le luxembourgeois · La boutique", "Wuertduell, the card game to learn Luxembourgish · The shop"),
            ("Le Wuertduell, le jeu de cartes pour apprendre le luxembourgeois en jouant à deux : quatre boîtes de 54 cartes à 9,90 €, ou 34,90 € les quatre. Réservez sans rien payer.",
             "The Wuertduell, the card game to learn Luxembourgish by playing, for two: four boxes of 54 cards at €9.90, or €34.90 for all four. Reserve without paying anything."),
            ('<p class="surtitre">La boutique du Wuertduell · Précommande</p>', '<p class="surtitre">The Wuertduell shop · Pre-order</p>'),
            ("<h1>Les boîtes <span>du Wuertduell</span></h1>", "<h1>The <span>Wuertduell boxes</span></h1>"),
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
            ("Le présentoir <span class=\"accent\">de comptoir</span></h2>",
             "The counter <span class=\"accent\">display</span></h2>"),
            (f"Douze boîtes, trois de chaque sorte, dans un présentoir en carton prêt à poser sur un comptoir{NB}: pour une librairie, un magasin, une bibliothèque ou un stand.",
             "Twelve boxes, three of each kind, in a cardboard display ready to place on a counter: for a bookshop, a shop, a library or a stand."),
            ("Le prix se fixe avec vous, selon le nombre de présentoirs.", "We set the price with you, depending on the number of displays."),
            (">Nous écrire</a>", ">Write to us</a>"),
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
            ("Wuertduell, le jeu de cartes pour apprendre le luxembourgeois · La boutique", "Wuertduell, das Kartenspiel zum Luxemburgischlernen · Der Shop"),
            ("Le Wuertduell, le jeu de cartes pour apprendre le luxembourgeois en jouant à deux : quatre boîtes de 54 cartes à 9,90 €, ou 34,90 € les quatre. Réservez sans rien payer.",
             "Das Wuertduell, das Kartenspiel, um Luxemburgisch zu zweit spielend zu lernen: vier Schachteln mit je 54 Karten zu 9,90 €, oder 34,90 € für alle vier. Ohne Bezahlung reservieren."),
            ('<p class="surtitre">La boutique du Wuertduell · Précommande</p>', '<p class="surtitre">Der Wuertduell-Shop · Vorbestellung</p>'),
            ("<h1>Les boîtes <span>du Wuertduell</span></h1>", "<h1>Die Schachteln <span>des Wuertduell</span></h1>"),
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
            ("Le présentoir <span class=\"accent\">de comptoir</span></h2>",
             "Das <span class=\"accent\">Thekendisplay</span></h2>"),
            (f"Douze boîtes, trois de chaque sorte, dans un présentoir en carton prêt à poser sur un comptoir{NB}: pour une librairie, un magasin, une bibliothèque ou un stand.",
             "Zwölf Schachteln, drei von jeder Sorte, in einem Display aus Karton, bereit für die Theke: für eine Buchhandlung, ein Geschäft, eine Bibliothek oder einen Stand."),
            ("Le prix se fixe avec vous, selon le nombre de présentoirs.", "Den Preis legen wir mit Ihnen fest, je nach Anzahl der Displays."),
            (">Nous écrire</a>", ">Schreiben Sie uns</a>"),
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
            ("Wuertduell, le jeu de cartes pour apprendre le luxembourgeois · La boutique", "Wuertduell, o jogo de cartas para aprender luxemburguês · A loja"),
            ("Le Wuertduell, le jeu de cartes pour apprendre le luxembourgeois en jouant à deux : quatre boîtes de 54 cartes à 9,90 €, ou 34,90 € les quatre. Réservez sans rien payer.",
             "O Wuertduell, o jogo de cartas para aprender luxemburguês a jogar a dois: quatro caixas de 54 cartas a 9,90 €, ou 34,90 € as quatro. Reserve sem pagar nada."),
            ('<p class="surtitre">La boutique du Wuertduell · Précommande</p>', '<p class="surtitre">A loja do Wuertduell · Pré-encomenda</p>'),
            ("<h1>Les boîtes <span>du Wuertduell</span></h1>", "<h1>As caixas <span>do Wuertduell</span></h1>"),
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
            ("Le présentoir <span class=\"accent\">de comptoir</span></h2>",
             "O expositor <span class=\"accent\">de balcão</span></h2>"),
            (f"Douze boîtes, trois de chaque sorte, dans un présentoir en carton prêt à poser sur un comptoir{NB}: pour une librairie, un magasin, une bibliothèque ou un stand.",
             "Doze caixas, três de cada tipo, num expositor de cartão pronto a pousar num balcão: para uma livraria, uma loja, uma biblioteca ou um stand."),
            ("Le prix se fixe avec vous, selon le nombre de présentoirs.", "O preço é definido consigo, conforme o número de expositores."),
            (">Nous écrire</a>", ">Escreva-nos</a>"),
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


# Refonte du 2026-11-27 : en-tête avec photo, jeux, étapes, appli, argent, partage.
# Passées avant les autres paires (des textes plus longs qui contiennent « Réserver »).
FR_REFONTE = [
    '<link rel="canonical" href="https://famibelle.github.io/LuxKeyb/precommande.html">',
    '"url":"https://famibelle.github.io/LuxKeyb/precommande.html"',
    'aria-label="Raccourcis"',
    '>Réserver mes boîtes</a>',
    ">Installer l'appli</a>",
    'content="Le Wuertduell, le jeu de cartes en luxembourgeois"',
    "content=\"Le luxembourgeois qui s'apprend en jouant, à deux. Réservez vos boîtes sans rien payer.\"",
    'content="https://famibelle.github.io/LuxKeyb/precommande.html"',
    f"Le luxembourgeois qui s'apprend en jouant, à deux autour d'une table. Réservez vos boîtes aujourd'hui sans rien payer{NB}: l'impression part dès que 350{NB}boîtes sont réservées.",
    "<li>Rien à payer aujourd'hui</li>",
    "<li>Sans engagement</li>",
    "<li>Les cartes aussi dans l'application</li>",
    ">Choisir mes boîtes</a>",
    ">Comment on joue</a>",
    "alt=\"La boîte de départ du Wuertduell, ouverte, bleu nuit et or, avec le dos d'une carte, la carte de règle et trois cartes, Hond (chien), Kaz (chat) et Kéis (fromage)\"",
    ">Choisissez <span class=\"accent\">vos boîtes</span></h2>",
    f"Chaque boîte se joue seule, dès qu'on l'ouvre, ou avec les autres{NB}: aucun mot n'est dans deux boîtes. Touchez «{NB}Réserver{NB}», tout se fait ici, en une minute.",
    '<span class="etat">Le meilleur prix</span>',
    ">Deux jeux <span class=\"accent\">dans chaque boîte</span></h2>",
    "On joue à deux, sans rien préparer. On lit les mots à voix haute, et ils restent.",
    "<h3>La Bataille des mots</h3>",
    "<h3>Le Wuertduell</h3>",
    "2 joueurs · 15 minutes",
    "2 joueurs · 5 minutes",
    "Chacun retourne une carte et lit son mot à voix haute. Le mot le plus long gagne les deux cartes.",
    "Posez vos noms, renforcez-les avec un adjectif ou un verbe, puis attaquez. Dire le sens du mot à voix haute donne un point de plus.",
    '<a href="regle-du-jeu.html">Lire la règle complète</a>',
    ">Comment marche <span class=\"accent\">la précommande</span></h2>",
    "<strong>Vous réservez</strong> en une minute, sans payer. Réserver ne vous engage à rien.",
    f"<strong>Nous vous écrivons</strong> dès que 350{NB}boîtes sont réservées, pour confirmer et payer votre réservation. Le port s'ajoute au prix de la boîte.",
    "<strong>Vous recevez vos boîtes</strong> six à huit semaines après le lancement de l'impression, chez vous ou lors d'une animation au Luxembourg.",
    '<a href="commander.html">Tout savoir sur la précommande</a>',
    '<p class="surtitre">Gratuit sur Android</p>',
    ">Vos cartes continuent <span class=\"accent\">sur le téléphone</span></h2>",
    f"Dans chaque boîte, un code ajoute ses cartes à votre carnet dans l'application De Lëtzebuerger Clavier. Installez-la dès aujourd'hui{NB}: sept petits jeux vous font déjà gagner des cartes, et le clavier vous aide à écrire en luxembourgeois.",
    "utm_medium%3Dprecommande%26",
    'src="assets/google-play-badge-fr.svg" alt="Disponible sur Google Play"',
    'alt="Trois cartes du carnet de l\'application en éventail : Hond (chien), Kuch (gâteau) et Kaz (chat)"',
    f">Où va <span class=\"accent\">l'argent{NB}?</span></h2>",
    f"De Lëtzebuerger Clavier deviendra une association sans but lucratif (asbl) avant la première vente. Personne ne touche de part des bénéfices{NB}: tout reste dans le projet. Ce que rapporte une boîte sert, dans cet ordre{NB}:",
    f"<li>payer l'impression, et garder de quoi lancer le tirage suivant{NB};</li>",
    f"<li>baisser le prix des boîtes dès que les tirages le permettent{NB};</li>",
    f"<li>améliorer l'application, qui reste gratuite{NB};</li>",
    "<li>créer de nouvelles boîtes et de nouvelles cartes.</li>",
    ">Aidez l'impression <span class=\"accent\">à partir plus vite</span></h2>",
    "Plus il y a de réservations, plus vite les boîtes arrivent. Envoyez cette page à quelqu'un qui apprend le luxembourgeois, ou qui aimerait s'y mettre.",
    ">Copier le lien</button>",
    ">Partager…</button>",
]
TRADUCTIONS_REFONTE = {
    "en": [
        '<link rel="canonical" href="https://famibelle.github.io/LuxKeyb/precommande-en.html">',
        '"url":"https://famibelle.github.io/LuxKeyb/precommande-en.html"',
        'aria-label="Shortcuts"',
        '>Reserve my boxes</a>',
        ">Install the app</a>",
        'content="The Wuertduell, the card game in Luxembourgish"',
        'content="Luxembourgish you learn by playing, for two. Reserve your boxes without paying anything."',
        'content="https://famibelle.github.io/LuxKeyb/precommande-en.html"',
        "Luxembourgish you learn by playing, two people around a table. Reserve your boxes today without paying anything: printing starts as soon as 350 boxes are reserved.",
        "<li>Nothing to pay today</li>",
        "<li>No commitment</li>",
        "<li>The cards in the app too</li>",
        ">Choose my boxes</a>",
        ">How to play</a>",
        'alt="The Wuertduell starter box, open, midnight blue and gold, with a card back, the rules card and three cards, Hond (dog), Kaz (cat) and Kéis (cheese)"',
        ">Choose <span class=\"accent\">your boxes</span></h2>",
        "Each box can be played on its own, as soon as you open it, or with the others: no word is in two boxes. The word meanings on the cards are in French. Tap “Reserve”, everything happens here, in one minute.",
        '<span class="etat">Best price</span>',
        ">Two games <span class=\"accent\">in every box</span></h2>",
        "Two players, nothing to prepare. You read the words out loud, and they stay with you.",
        "<h3>The Word Battle</h3>",
        "<h3>The Wuertduell</h3>",
        "2 players · 15 minutes",
        "2 players · 5 minutes",
        "Each player turns over a card and reads the word out loud. The longest word wins both cards.",
        "Lay down your nouns, make them stronger with an adjective or a verb, then attack. Saying the meaning of the word out loud gives one extra point.",
        '<a href="regle-du-jeu.html" hreflang="fr">Read the full rules (in French)</a>',
        ">How <span class=\"accent\">the pre-order works</span></h2>",
        "<strong>You reserve</strong> in one minute, without paying. Reserving does not commit you to anything.",
        "<strong>We write to you</strong> as soon as 350 boxes are reserved, to confirm and pay for your reservation. Shipping is added to the price of the box.",
        "<strong>You receive your boxes</strong> six to eight weeks after printing starts, at home or at one of our events in Luxembourg.",
        '<a href="commander.html" hreflang="fr">All about the pre-order (in French)</a>',
        '<p class="surtitre">Free on Android</p>',
        ">Your cards carry on <span class=\"accent\">in your phone</span></h2>",
        "In every box, a code adds its cards to your notebook in the De Lëtzebuerger Clavier app. Install it today: seven little games already let you win cards, and the keyboard helps you write in Luxembourgish.",
        "utm_medium%3Dprecommande-en%26",
        'src="assets/google-play-badge-en.svg" alt="Get it on Google Play"',
        'alt="Three cards from the app\'s notebook in a fan: Hond (dog), Kuch (cake) and Kaz (cat)"',
        ">Where does <span class=\"accent\">the money go?</span></h2>",
        "De Lëtzebuerger Clavier will become a non-profit association (asbl) before the first sale. Nobody receives a share of the profits: everything stays in the project. What a box brings in is used, in this order:",
        "<li>to pay for printing, and keep enough to start the next print run;</li>",
        "<li>to lower the price of the boxes as soon as print runs allow;</li>",
        "<li>to improve the app, which stays free;</li>",
        "<li>to create new boxes and new cards.</li>",
        ">Help printing <span class=\"accent\">start sooner</span></h2>",
        "The more reservations, the sooner the boxes arrive. Send this page to someone who is learning Luxembourgish, or would like to start.",
        ">Copy the link</button>",
        ">Share…</button>",
    ],
    "de": [
        '<link rel="canonical" href="https://famibelle.github.io/LuxKeyb/precommande-de.html">',
        '"url":"https://famibelle.github.io/LuxKeyb/precommande-de.html"',
        'aria-label="Schnellzugriff"',
        '>Meine Schachteln reservieren</a>',
        ">App installieren</a>",
        'content="Das Wuertduell, das Kartenspiel auf Luxemburgisch"',
        'content="Luxemburgisch spielend lernen, zu zweit. Reservieren Sie Ihre Schachteln, ohne etwas zu bezahlen."',
        'content="https://famibelle.github.io/LuxKeyb/precommande-de.html"',
        "Luxemburgisch spielend lernen, zu zweit am Tisch. Reservieren Sie Ihre Schachteln heute, ohne etwas zu bezahlen: Der Druck beginnt, sobald 350 Schachteln reserviert sind.",
        "<li>Heute nichts zu bezahlen</li>",
        "<li>Unverbindlich</li>",
        "<li>Die Karten auch in der App</li>",
        ">Meine Schachteln wählen</a>",
        ">So wird gespielt</a>",
        'alt="Die Startschachtel des Wuertduell, geöffnet, nachtblau und gold, mit einer Kartenrückseite, der Regelkarte und drei Karten: Hond (Hund), Kaz (Katze) und Kéis (Käse)"',
        ">Wählen Sie <span class=\"accent\">Ihre Schachteln</span></h2>",
        "Jede Schachtel lässt sich allein spielen, sobald man sie öffnet, oder mit den anderen: Kein Wort steht in zwei Schachteln. Die Wortbedeutungen auf den Karten sind auf Französisch. Tippen Sie auf „Reservieren“, alles geht hier, in einer Minute.",
        '<span class="etat">Bester Preis</span>',
        ">Zwei Spiele <span class=\"accent\">in jeder Schachtel</span></h2>",
        "Zu zweit, ohne Vorbereitung. Man liest die Wörter laut vor, und sie bleiben hängen.",
        "<h3>Die Wörterschlacht</h3>",
        "<h3>Das Wuertduell</h3>",
        "2 Spieler · 15 Minuten",
        "2 Spieler · 5 Minuten",
        "Jeder deckt eine Karte auf und liest sein Wort laut vor. Das längste Wort gewinnt beide Karten.",
        "Legen Sie Ihre Nomen aus, stärken Sie sie mit einem Adjektiv oder einem Verb und greifen Sie dann an. Wer die Bedeutung des Wortes laut sagt, bekommt einen Punkt mehr.",
        '<a href="regle-du-jeu.html" hreflang="fr">Die vollständigen Regeln lesen (auf Französisch)</a>',
        ">So funktioniert <span class=\"accent\">die Vorbestellung</span></h2>",
        "<strong>Sie reservieren</strong> in einer Minute, ohne zu bezahlen. Die Reservierung verpflichtet Sie zu nichts.",
        "<strong>Wir schreiben Ihnen</strong>, sobald 350 Schachteln reserviert sind, damit Sie Ihre Reservierung bestätigen und bezahlen. Der Versand kommt zum Preis der Schachtel hinzu.",
        "<strong>Sie erhalten Ihre Schachteln</strong> sechs bis acht Wochen nach Druckbeginn, zu Hause oder bei einer unserer Veranstaltungen in Luxemburg.",
        '<a href="commander.html" hreflang="fr">Alles zur Vorbestellung (auf Französisch)</a>',
        '<p class="surtitre">Kostenlos für Android</p>',
        ">Ihre Karten gehen <span class=\"accent\">auf dem Handy weiter</span></h2>",
        "In jeder Schachtel fügt ein Code ihre Karten Ihrem Heft in der App De Lëtzebuerger Clavier hinzu. Installieren Sie sie schon heute: Mit sieben kleinen Spielen gewinnen Sie bereits Karten, und die Tastatur hilft Ihnen, auf Luxemburgisch zu schreiben.",
        "utm_medium%3Dprecommande-de%26",
        'src="assets/google-play-badge-de.svg" alt="Jetzt bei Google Play"',
        'alt="Drei Karten aus dem Heft der App, aufgefächert: Hond (Hund), Kuch (Kuchen) und Kaz (Katze)"',
        ">Wohin geht <span class=\"accent\">das Geld?</span></h2>",
        "De Lëtzebuerger Clavier wird vor dem ersten Verkauf ein gemeinnütziger Verein (asbl). Niemand erhält einen Anteil am Gewinn: Alles bleibt im Projekt. Was eine Schachtel einbringt, dient in dieser Reihenfolge dazu,",
        "<li>den Druck zu bezahlen und genug für die nächste Auflage zurückzulegen;</li>",
        "<li>den Preis der Schachteln zu senken, sobald die Auflagen es erlauben;</li>",
        "<li>die App zu verbessern, die kostenlos bleibt;</li>",
        "<li>neue Schachteln und neue Karten zu schaffen.</li>",
        ">Helfen Sie, <span class=\"accent\">dass der Druck früher startet</span></h2>",
        "Je mehr Reservierungen, desto schneller kommen die Schachteln. Schicken Sie diese Seite an jemanden, der Luxemburgisch lernt oder gern damit anfangen würde.",
        ">Link kopieren</button>",
        ">Teilen…</button>",
    ],
    "pt": [
        '<link rel="canonical" href="https://famibelle.github.io/LuxKeyb/precommande-pt.html">',
        '"url":"https://famibelle.github.io/LuxKeyb/precommande-pt.html"',
        'aria-label="Atalhos"',
        '>Reservar as minhas caixas</a>',
        ">Instalar a app</a>",
        'content="O Wuertduell, o jogo de cartas em luxemburguês"',
        'content="O luxemburguês que se aprende a jogar, a dois. Reserve as suas caixas sem pagar nada."',
        'content="https://famibelle.github.io/LuxKeyb/precommande-pt.html"',
        "O luxemburguês que se aprende a jogar, a dois, à volta de uma mesa. Reserve hoje as suas caixas sem pagar nada: a impressão arranca assim que 350 caixas estiverem reservadas.",
        "<li>Nada a pagar hoje</li>",
        "<li>Sem compromisso</li>",
        "<li>As cartas também na aplicação</li>",
        ">Escolher as minhas caixas</a>",
        ">Como se joga</a>",
        'alt="A caixa inicial do Wuertduell, aberta, azul-noite e dourada, com o verso de uma carta, a carta de regras e três cartas: Hond (cão), Kaz (gato) e Kéis (queijo)"',
        ">Escolha <span class=\"accent\">as suas caixas</span></h2>",
        "Cada caixa joga-se sozinha, assim que se abre, ou com as outras: nenhuma palavra está em duas caixas. Os significados das palavras nas cartas estão em francês. Toque em «Reservar», tudo se faz aqui, num minuto.",
        '<span class="etat">Melhor preço</span>',
        ">Dois jogos <span class=\"accent\">em cada caixa</span></h2>",
        "Joga-se a dois, sem preparar nada. Lêem-se as palavras em voz alta, e elas ficam.",
        "<h3>A Batalha das palavras</h3>",
        "<h3>O Wuertduell</h3>",
        "2 jogadores · 15 minutos",
        "2 jogadores · 5 minutos",
        "Cada um vira uma carta e lê a sua palavra em voz alta. A palavra mais comprida ganha as duas cartas.",
        "Ponha os seus nomes em jogo, reforce-os com um adjetivo ou um verbo, e ataque. Dizer o significado da palavra em voz alta dá um ponto a mais.",
        '<a href="regle-du-jeu.html" hreflang="fr">Ler as regras completas (em francês)</a>',
        ">Como funciona <span class=\"accent\">a pré-encomenda</span></h2>",
        "<strong>Reserva</strong> num minuto, sem pagar. Reservar não o compromete a nada.",
        "<strong>Escrevemos-lhe</strong> assim que 350 caixas estiverem reservadas, para confirmar e pagar a sua reserva. O envio acresce ao preço da caixa.",
        "<strong>Recebe as suas caixas</strong> seis a oito semanas depois do arranque da impressão, em casa ou numa das nossas animações no Luxemburgo.",
        '<a href="commander.html" hreflang="fr">Tudo sobre a pré-encomenda (em francês)</a>',
        '<p class="surtitre">Grátis para Android</p>',
        ">As suas cartas continuam <span class=\"accent\">no telemóvel</span></h2>",
        "Em cada caixa, um código junta as suas cartas ao seu caderno na aplicação De Lëtzebuerger Clavier. Instale-a já hoje: sete pequenos jogos já o deixam ganhar cartas, e o teclado ajuda-o a escrever em luxemburguês.",
        "utm_medium%3Dprecommande-pt%26",
        'src="assets/google-play-badge-pt.svg" alt="Disponível no Google Play"',
        'alt="Três cartas do caderno da aplicação em leque: Hond (cão), Kuch (bolo) e Kaz (gato)"',
        ">Para onde vai <span class=\"accent\">o dinheiro?</span></h2>",
        "De Lëtzebuerger Clavier vai tornar-se uma associação sem fins lucrativos (asbl) antes da primeira venda. Ninguém recebe uma parte dos lucros: tudo fica no projeto. O que uma caixa rende serve, por esta ordem, para:",
        "<li>pagar a impressão, e guardar o necessário para lançar a tiragem seguinte;</li>",
        "<li>baixar o preço das caixas assim que as tiragens o permitam;</li>",
        "<li>melhorar a aplicação, que continua gratuita;</li>",
        "<li>criar novas caixas e novas cartas.</li>",
        ">Ajude a impressão <span class=\"accent\">a arrancar mais cedo</span></h2>",
        "Quanto mais reservas, mais depressa chegam as caixas. Envie esta página a alguém que está a aprender luxemburguês, ou que gostaria de começar.",
        ">Copiar o link</button>",
        ">Partilhar…</button>",
    ],
}
for _langue, _trad in TRADUCTIONS_REFONTE.items():
    assert len(_trad) == len(FR_REFONTE), _langue
    LANGUES[_langue]["textes"][:0] = list(zip(FR_REFONTE, _trad))

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
