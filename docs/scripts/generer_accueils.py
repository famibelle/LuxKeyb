#!/usr/bin/env python3
"""Génère les accueils anglais, allemand et portugais depuis l'accueil français.

    python docs/scripts/generer_accueils.py

Le français est la source (docs/index.html) : en.html, de.html et pt.html en
sont des copies dont chaque texte est remplacé par sa traduction. Ne jamais
modifier ces trois fichiers à la main : changer index.html, compléter les
tables ci-dessous, puis relancer.

Chaque texte français cherché doit être présent dans index.html (les blancs et
retours à la ligne comptent pour un seul blanc) : quand l'accueil français
change, le script s'arrête sur le premier texte introuvable au lieu de laisser
passer une page à moitié française. Il vérifie aussi, à la fin, qu'aucun mot
français courant ne reste dans le texte visible.

Les traductions sont écrites par Claude (2026-11-25) : à faire relire par un
locuteur de chaque langue. Typographie : “ ” en anglais, „ “ en allemand,
« » sans espace en portugais (skill textesPublics).
"""
import pathlib
import re
import sys

DOCS = pathlib.Path(__file__).resolve().parents[1]
SOURCE = DOCS / "index.html"
BASE = "https://famibelle.github.io/LuxKeyb/"

# Pages liées qui n'existent qu'en français : une pastille « FR » le signale.
PASTILLE_FR = ' <small class="langue-fr" title="{titre}">FR</small>'

LANGUES = {
    "en": {
        "locale": "en_GB",
        "ancre_installer": "install",
        "fr_titre": "in French",
        "theme": ("Switch to dark mode", "🌙 Dark"),
        "labs": "labs-en.html",
        "textes": [
            # ---- en-tête du document ----
            ("De Lëtzebuerger Clavier · Vous le parlez. Maintenant, vous l'écrivez.",
             "De Lëtzebuerger Clavier · You speak it. Now you write it."),
            ("Le clavier qui écrit le luxembourgeois avec vous : il propose les mots pendant que vous tapez, met les ë et les é à leur place et écrit ce que vous lui dites. Gratuit sur Android.",
             "The keyboard that writes Luxembourgish with you: it suggests words as you type, puts the ë and é in the right place and writes down what you say. Free on Android."),
            ('content="Vous le parlez. Maintenant, vous l\'écrivez. 🇱🇺"',
             'content="You speak it. Now you write it. 🇱🇺"'),
            ("Tapez ou dictez vos messages en luxembourgeois, directement sur votre téléphone. Gratuit sur Android.",
             "Type or dictate your messages in Luxembourgish, right on your phone. Free on Android."),
            # ---- haut de page ----
            ('aria-label="Langue"', 'aria-label="Language"'),
            ("Le clavier luxembourgeois</p>", "The Luxembourgish keyboard</p>"),
            ("<h1>Vous le parlez. <span>Maintenant, vous l'écrivez.</span></h1>",
             "<h1>You speak it. <span>Now you write it.</span></h1>"),
            ("Le clavier vous propose les mots luxembourgeois pendant que vous tapez, met les ë et les é à leur place, et écrit pour vous ce que vous lui dites.",
             "The keyboard suggests Luxembourgish words as you type, puts the ë and é in the right place, and writes down what you say to it."),
            ('alt="Disponible sur Google Play"', 'alt="Get it on Google Play"'),
            ("Gratuit · Android 7.0 et plus récent", "Free · Android 7.0 or later"),
            ('alt="Bientôt sur l\'App Store"', 'alt="Coming soon to the App Store"'),
            (">S'inscrire à la newsletter</a>", ">Subscribe to the newsletter</a>"),
            ('aria-label="Installer de Lëtzebuerger Clavier depuis Google Play"',
             'aria-label="Install de Lëtzebuerger Clavier from Google Play"'),
            ("La phrase « Ech hunn op der Schueberfouer Gromperekichelcher giess » tapée sur le clavier : après « op der », la barre propose la suite, et les mots longs se complètent en un appui",
             "The sentence “Ech hunn op der Schueberfouer Gromperekichelcher giess” typed on the keyboard: after “op der”, the bar suggests what comes next, and long words are completed in one tap"),
            ("<figcaption>«&nbsp;Ech hunn op der Schueberfouer Gromperekichelcher giess&nbsp;»</figcaption>",
             "<figcaption>“Ech hunn op der Schueberfouer Gromperekichelcher giess”</figcaption>"),
            # ---- écrire comme on parle ----
            ('Écrire <span class="accent">comme on parle</span>',
             'Write <span class="accent">the way you speak</span>'),
            ("Vous savez le dire, mais l'écrire, c'est autre chose&nbsp;: où va le ë, faut-il une majuscule, un e ou deux&nbsp;? Le clavier vous répond pendant que vous tapez. Regardez-le écrire, puis prenez la main&nbsp;: touchez une lettre du clavier d'essai, ou son micro pour lui parler.",
             "You know how to say it, but writing it is another matter: where does the ë go, does it take a capital, one e or two? The keyboard answers as you type. Watch it write, then take over: tap a letter on the test keyboard, or its microphone to speak to it."),
            ("<h3>Il devine la suite</h3>", "<h3>It guesses what comes next</h3>"),
            ("Après «&nbsp;nach eng&nbsp;», il propose déjà «&nbsp;Kéier&nbsp;». Il tient compte de ce que vous venez d'écrire&nbsp;: un appui, et le mot suivant est posé.",
             "After “nach eng”, it already suggests “Kéier”. It takes into account what you have just written: one tap, and the next word is in place."),
            ("<h3>Il met les accents à leur place</h3>", "<h3>It puts the accents in place</h3>"),
            ("Tapez sans vous soucier des ä, ë et é&nbsp;: il propose le mot bien écrit. Si vous préférez les taper vous-même, ils ont chacun leur touche.",
             "Type without worrying about ä, ë and é: it suggests the correctly spelled word. If you prefer to type them yourself, each one has its own key."),
            ("<h3>Il rattrape les fautes de frappe</h3>", "<h3>It catches typos</h3>"),
            ("Une lettre oubliée, une lettre en trop, une touche voisine&nbsp;: le bon mot arrive quand même.",
             "A missing letter, an extra letter, a neighbouring key: the right word still comes up."),
            ("<h3>Il connaît l'orthographe</h3>", "<h3>It knows the spelling</h3>"),
            ("Les noms prennent leur majuscule, comme le veut l'orthographe, et les mots longs s'écrivent en un appui. Le clavier s'appuie sur le dictionnaire officiel de la langue&nbsp;: vous n'inventez rien, vous choisissez.",
             "Nouns get their capital letter, as Luxembourgish spelling requires, and long words are written in one tap. The keyboard relies on the official dictionary of the language: you don't make anything up, you just choose."),
            ("Clavier d'essai : il écrit tout seul des phrases en luxembourgeois ; touchez une lettre ou le micro pour écrire à votre tour",
             "Test keyboard: it writes Luxembourgish sentences on its own; tap a letter or the microphone to write yourself"),
            ("Le micro envoie votre voix à l'Université du Luxembourg, qui l'écrit sans la garder.",
             "The microphone sends your voice to the University of Luxembourg, which writes it down without keeping it."),
            # ---- dictée ----
            ('Parlez en luxembourgeois, <span class="accent">il écrit</span>',
             'Speak Luxembourgish, <span class="accent">it writes</span>'),
            ("Plus besoin de chercher l'orthographe&nbsp;: dites votre phrase, le clavier l'écrit pour vous, avec les majuscules et la ponctuation.",
             "No more hunting for the spelling: say your sentence and the keyboard writes it for you, with capitals and punctuation."),
            ("<strong>Touchez le micro</strong>, à droite des suggestions.",
             "<strong>Tap the microphone</strong>, to the right of the suggestions."),
            ("<strong>Parlez normalement.</strong> Le texte s'écrit pendant que vous parlez, et le micro se referme tout seul quand vous vous arrêtez.",
             "<strong>Speak normally.</strong> The text appears as you speak, and the microphone closes by itself when you stop."),
            ("<strong>Relisez</strong>, puis envoyez.", "<strong>Read it over</strong>, then send."),
            ("Le micro fait partie du clavier&nbsp;: il vous suit partout où vous écrivez.",
             "The microphone is part of the keyboard: it goes with you wherever you write."),
            ('aria-label="Exemples d\'applications"', 'aria-label="Examples of apps"'),
            ("<span>Mails</span>", "<span>Email</span>"),
            ("<strong>Votre voix ne part que lorsque vous touchez le micro</strong>, vers le service de reconnaissance vocale de l'Université du Luxembourg, qui la transforme en texte sans la garder. Il faut donc une connexion Internet pour dicter. Ce que vous tapez, lui, reste sur votre téléphone.",
             "<strong>Your voice only leaves your phone when you tap the microphone</strong>, for the speech recognition service of the University of Luxembourg, which turns it into text without keeping it. So you need an Internet connection to dictate. What you type stays on your phone."),
            ("Un micro rouge pulse pendant la dictée, et les mots de la phrase « Op der Kiermes iessen ech ëmmer Gromperekichelcher mat Äppelkompott. » apparaissent dans la bulle, un à un",
             "A red microphone pulses during dictation, and the words of the sentence “Op der Kiermes iessen ech ëmmer Gromperekichelcher mat Äppelkompott.” appear in the bubble, one by one"),
            ("«&nbsp;À la kermesse, je mange toujours des galettes de pommes de terre avec de la compote.&nbsp;» Le texte s'écrit pendant qu'on parle.",
             "“At the fair, I always eat potato fritters with apple sauce.” The text appears as you speak."),
            # ---- cartes ----
            ("Trois cartes en éventail : Hond (chien) en bronze, Kuch (gâteau) en argent, Kaz (chat) en or. Chacune porte un dessin devant un lieu du pays, le mot, sa nature, son sens en français et une phrase d'exemple",
             "Three cards fanned out: Hond (dog) in bronze, Kuch (cake) in silver, Kaz (cat) in gold. Each shows a drawing in front of a place in the country, the word, its part of speech, its meaning in French and an example sentence"),
            ('<p class="surtitre-clair">En prime</p>', '<p class="surtitre-clair">Bonus</p>'),
            ("Chaque mot trouvé <span class=\"accent\">devient une carte</span>", "Every word you find <span class=\"accent\">becomes a card</span>"),
            ("Sept petits jeux vous font rencontrer des mots nouveaux. Chaque mot gagné rejoint votre carnet, sur une carte ouverte sur un lieu du pays, et revient de lui-même jusqu'à ce que vous le reteniez.",
             "Seven little games help you meet new words. Each word you win goes into your notebook, on a card that opens onto a place in the country, and comes back by itself until you remember it."),
            ('aria-label="Les sept jeux"', 'aria-label="The seven games"'),
            ("<small>mots mêlés</small>", "<small>word search</small>"),
            ("<small>mots mélangés</small>", "<small>scrambled words</small>"),
            ("<small>mot en six essais</small>", "<small>a word in six tries</small>"),
            ("<small>le mot qui manque</small>", "<small>the missing word</small>"),
            ("<small>les nombres</small>", "<small>numbers</small>"),
            ("<small>mots croisés</small>", "<small>crossword</small>"),
            ("<small>la grille à remplir</small>", "<small>fill the grid</small>"),
            ('<a href="regle-du-jeu.html">Les cartes se jouent aussi sur la table, à deux</a>',
             '<a href="regle-du-jeu.html" hreflang="fr">The cards can also be played on the table, for two{FR}</a>'),
            # ---- questions ----
            # ---- jeu de cartes en préparation ----
            # ---- annonce et précommande du jeu de cartes ----
            ('Précommandez le Wuertduell, le jeu de cartes pour apprendre le luxembourgeois en jouant.',
             'Pre-order the Wuertduell, the card game for learning Luxembourgish as you play.'),
            ('<span class="annonce-nouveau">Nouveau</span>',
             '<span class="annonce-nouveau">New</span>'),
            ('<span class="annonce-lien">Réserver sans payer&nbsp;→</span>',
             '<span class="annonce-lien">Reserve without paying&nbsp;→</span>'),
            ('<p class="surtitre-clair">Précommande</p>',
             '<p class="surtitre-clair">Pre-order</p>'),
            ('Précommandez <span class=\"accent\">votre jeu de cartes</span>',
             'Pre-order <span class=\"accent\">your card game</span>'),
            ("Le Wuertduell, c'est le luxembourgeois qui s'apprend en jouant, à deux autour d'une table.",
             'The Wuertduell is Luxembourgish you learn by playing, for two around a table.'),
            ('<strong>Des mots qui restent.</strong> Vous les lisez à voix haute, vous les jouez, vous les retenez.',
             '<strong>Words that stick.</strong> You read them aloud, you play them, you remember them.'),
            ("<strong>Une collection qui continue sur le téléphone.</strong> Le code de la boîte ajoute ses cartes à votre carnet dans l'application.",
             '<strong>A collection that carries on in your phone.</strong> The code in the box adds its cards to your notebook in the app.'),
            ("<strong>Rien à payer aujourd'hui.</strong> Vous réservez maintenant, et vous payez seulement quand l'impression se prépare.",
             '<strong>Nothing to pay today.</strong> Reserve now, and pay only when printing is being prepared.'),
            ('Quatre boîtes de 54\xa0cartes, <strong>9,90\xa0€ la boîte</strong>, ou <strong>34,90\xa0€ les quatre</strong>.',
             'Four boxes of 54\xa0cards, <strong>€9.90 a box</strong>, or <strong>€34.90 for all four</strong>.'),
            ('<a class="reserver" href="precommande.html">Précommander mon jeu</a>',
             '<a class="reserver" href="precommande-en.html">Pre-order my game</a>'),
            ('<a class="annonce" href="precommande.html">', '<a class="annonce" href="precommande-en.html">'),
            ('<a class="vers-precommande" href="precommande.html"', '<a class="vers-precommande" href="precommande-en.html"'),
            ('aria-label="Voir les boîtes et réserver"',
             'aria-label="See the boxes and reserve"'),
            ('<a href="regle-du-jeu.html">Lire la règle du jeu</a>',
             '<a href="regle-du-jeu.html" hreflang="fr">Read the rules{FR}</a>'),
            ("Maquette de la boîte de départ du Wuertduell, ouverte, bleu nuit et or. À côté, posés sur la table\xa0: le dos d'une carte, la carte de règle et trois cartes, Hond (chien), Kaz (chat) et Kéis (fromage)",
             'Mock-up of the Wuertduell starter box, open, in midnight blue and gold. Next to it on the table: the back of a card, the rules card and three cards, Hond (dog), Kaz (cat) and Kéis (cheese)'),
            ('Une maquette de la boîte de départ\xa0: les cartes, leur dos et la carte de règle.',
             'A mock-up of the starter box: the cards, their back and the rules card.'),
            ("Vos <span class=\"accent\">questions</span>", "Your <span class=\"accent\">questions</span>"),
            ("«&nbsp;Mon téléphone souligne mes mots en rouge.&nbsp;»", "“My phone underlines my words in red.”"),
            ("Activez le correcteur du clavier&nbsp;: vos mots luxembourgeois sont reconnus dans vos messages, vos notes et vos mails.",
             "Turn on the keyboard's spell checker: your Luxembourgish words are recognised in your messages, notes and emails."),
            ("«&nbsp;Je mélange avec le français.&nbsp;»", "“I mix in French.”"),
            ("Allumez «&nbsp;Propositions en français&nbsp;» dans les réglages du clavier&nbsp;: «&nbsp;ech hunn eng réunion muer&nbsp;» s'écrit sans changer de clavier.",
             "Turn on “French suggestions” in the keyboard settings: “ech hunn eng réunion muer” can be written without switching keyboards."),
            ("«&nbsp;Android m'affiche un avertissement.&nbsp;»", "“Android shows me a warning.”"),
            ("Il s'affiche pour tous les claviers, sans exception, au moment de les activer. Celui-ci n'envoie rien de ce que vous tapez.",
             "It appears for every keyboard, without exception, when you turn it on. This one sends nothing of what you type."),
            ("«&nbsp;J'ai un iPhone.&nbsp;»", "“I have an iPhone.”"),
            ("Le clavier existe pour l'instant sur Android. Sur iPhone ou sur ordinateur, essayez-le plus haut sur cette page&nbsp;: touchez une lettre du clavier d'essai.",
             "For now the keyboard is available on Android. On an iPhone or a computer, try it higher up on this page: tap a letter on the test keyboard."),
            ('<a href="faq.html">Toutes les réponses</a>', '<a href="faq.html" hreflang="fr">All the answers{FR}</a>'),
            # ---- installer ----
            ("Installer <span class=\"accent\">en trois étapes</span>", "Install <span class=\"accent\">in three steps</span>"),
            ("<strong>Installez l'application</strong> depuis Google Play.",
             "<strong>Install the app</strong> from Google Play."),
            ("<strong>Suivez le guide</strong>&nbsp;: elle vous montre comment activer le clavier, étape par étape.",
             "<strong>Follow the guide</strong>: it shows you how to turn on the keyboard, step by step."),
            ("<strong>Essayez-le sur place</strong>, puis écrivez votre premier message.",
             "<strong>Try it out right there</strong>, then write your first message."),
            ('aria-label="Ouvrir la page de l\'application sur Google Play"',
             'aria-label="Open the app\'s page on Google Play"'),
            ('alt="Code QR ouvrant la page de l\'application sur Google Play"',
             'alt="QR code opening the app\'s page on Google Play"'),
            ("Scannez avec votre téléphone", "Scan with your phone"),
            # ---- pied de page ----
            ('aria-label="Pour aller plus loin"', 'aria-label="Find out more"'),
            ("<h4>Bien démarrer</h4>", "<h4>Getting started</h4>"),
            ('<a href="en-detail.html">Le clavier en détail</a>', '<a href="en-detail-en.html">The keyboard in detail</a>'),
            ('<a href="guide.html">Le guide</a>', '<a href="guide.html" hreflang="fr">The guide{FR}</a>'),
            ('<a href="faq.html">Aide</a>', '<a href="faq.html" hreflang="fr">Help{FR}</a>'),
            ('<a href="nouveautes.html">Nouveautés</a>', '<a href="nouveautes-en.html">What\'s new</a>'),
            ("<h4>Le projet</h4>", "<h4>The project</h4>"),
            ('<a href="regle-du-jeu.html">Règle du jeu des cartes</a>', '<a href="regle-du-jeu.html" hreflang="fr">Card game rules{FR}</a>'),
            ('<a href="presse.html">Presse</a>', '<a href="presse.html" hreflang="fr">Press{FR}</a>'),
            ('<a href="ambassadeurs.html">Ambassadeurs</a>', '<a href="ambassadeurs.html" hreflang="fr">Ambassadors{FR}</a>'),
            ("<h4>Pour les curieux</h4>", "<h4>For the curious</h4>"),
            ('<a href="comparatif.html">Face aux autres claviers</a>', '<a href="comparatif.html" hreflang="fr">Compared with other keyboards{FR}</a>'),
            ('<a href="corpus.html">D\'où viennent les mots</a>', '<a href="corpus.html" hreflang="fr">Where the words come from{FR}</a>'),
            ('<a href="labs.html">Labs</a>', '<a href="labs-en.html">Labs</a>'),
            ('<a href="privacy/privacy-policy.html">Confidentialité</a>', '<a href="privacy/privacy-policy.html" hreflang="fr">Privacy{FR}</a>'),
            ("De Lëtzebuerger Clavier est un projet indépendant. Les mots viennent du Lëtzebuerger Online Dictionnaire (Zenter fir d'Lëtzebuerger Sprooch) et de textes luxembourgeois ouverts&nbsp;; la dictée passe par le service de reconnaissance vocale de l'Université du Luxembourg.",
             "De Lëtzebuerger Clavier is an independent project. The words come from the Lëtzebuerger Online Dictionnaire (Zenter fir d'Lëtzebuerger Sprooch) and from open Luxembourgish texts; dictation goes through the speech recognition service of the University of Luxembourg. Pages marked FR are in French."),
        ],
    },
    "de": {
        "locale": "de_DE",
        "ancre_installer": "installieren",
        "fr_titre": "auf Französisch",
        "theme": ("Zum dunklen Modus wechseln", "🌙 Dunkel"),
        "labs": "labs.html",
        "textes": [
            ("De Lëtzebuerger Clavier · Vous le parlez. Maintenant, vous l'écrivez.",
             "De Lëtzebuerger Clavier · Sie sprechen es. Jetzt schreiben Sie es."),
            ("Le clavier qui écrit le luxembourgeois avec vous : il propose les mots pendant que vous tapez, met les ë et les é à leur place et écrit ce que vous lui dites. Gratuit sur Android.",
             "Die Tastatur, die mit Ihnen Luxemburgisch schreibt: Sie schlägt beim Tippen Wörter vor, setzt ë und é an die richtige Stelle und schreibt, was Sie ihr sagen. Kostenlos für Android."),
            ('content="Vous le parlez. Maintenant, vous l\'écrivez. 🇱🇺"',
             'content="Sie sprechen es. Jetzt schreiben Sie es. 🇱🇺"'),
            ("Tapez ou dictez vos messages en luxembourgeois, directement sur votre téléphone. Gratuit sur Android.",
             "Tippen oder diktieren Sie Ihre Nachrichten auf Luxemburgisch, direkt auf dem Handy. Kostenlos für Android."),
            ('aria-label="Langue"', 'aria-label="Sprache"'),
            ("Le clavier luxembourgeois</p>", "Die luxemburgische Tastatur</p>"),
            ("<h1>Vous le parlez. <span>Maintenant, vous l'écrivez.</span></h1>",
             "<h1>Sie sprechen es. <span>Jetzt schreiben Sie es.</span></h1>"),
            ("Le clavier vous propose les mots luxembourgeois pendant que vous tapez, met les ë et les é à leur place, et écrit pour vous ce que vous lui dites.",
             "Die Tastatur schlägt Ihnen beim Tippen luxemburgische Wörter vor, setzt ë und é an die richtige Stelle und schreibt für Sie, was Sie ihr sagen."),
            ('alt="Disponible sur Google Play"', 'alt="Jetzt bei Google Play"'),
            ("Gratuit · Android 7.0 et plus récent", "Kostenlos · Android 7.0 oder neuer"),
            ('alt="Bientôt sur l\'App Store"', 'alt="Demnächst im App Store"'),
            (">S'inscrire à la newsletter</a>", ">Newsletter abonnieren</a>"),
            ('aria-label="Installer de Lëtzebuerger Clavier depuis Google Play"',
             'aria-label="De Lëtzebuerger Clavier bei Google Play installieren"'),
            ("La phrase « Ech hunn op der Schueberfouer Gromperekichelcher giess » tapée sur le clavier : après « op der », la barre propose la suite, et les mots longs se complètent en un appui",
             "Der Satz „Ech hunn op der Schueberfouer Gromperekichelcher giess“ wird auf der Tastatur getippt: Nach „op der“ schlägt die Leiste die Fortsetzung vor, und lange Wörter werden mit einem Tippen ergänzt"),
            ("<figcaption>«&nbsp;Ech hunn op der Schueberfouer Gromperekichelcher giess&nbsp;»</figcaption>",
             "<figcaption>„Ech hunn op der Schueberfouer Gromperekichelcher giess“</figcaption>"),
            ('Écrire <span class="accent">comme on parle</span>',
             'Schreiben, <span class="accent">wie man spricht</span>'),
            ("Vous savez le dire, mais l'écrire, c'est autre chose&nbsp;: où va le ë, faut-il une majuscule, un e ou deux&nbsp;? Le clavier vous répond pendant que vous tapez. Regardez-le écrire, puis prenez la main&nbsp;: touchez une lettre du clavier d'essai, ou son micro pour lui parler.",
             "Sie können es sagen, aber es zu schreiben, ist etwas anderes: Wohin kommt das ë, groß oder klein, ein e oder zwei? Die Tastatur antwortet Ihnen beim Tippen. Sehen Sie ihr beim Schreiben zu und übernehmen Sie dann: Tippen Sie auf einen Buchstaben der Testtastatur oder auf ihr Mikrofon, um mit ihr zu sprechen."),
            ("<h3>Il devine la suite</h3>", "<h3>Sie errät, was folgt</h3>"),
            ("Après «&nbsp;nach eng&nbsp;», il propose déjà «&nbsp;Kéier&nbsp;». Il tient compte de ce que vous venez d'écrire&nbsp;: un appui, et le mot suivant est posé.",
             "Nach „nach eng“ schlägt sie schon „Kéier“ vor. Sie berücksichtigt, was Sie gerade geschrieben haben: ein Tippen, und das nächste Wort steht da."),
            ("<h3>Il met les accents à leur place</h3>", "<h3>Sie setzt die Akzente</h3>"),
            ("Tapez sans vous soucier des ä, ë et é&nbsp;: il propose le mot bien écrit. Si vous préférez les taper vous-même, ils ont chacun leur touche.",
             "Tippen Sie, ohne sich um ä, ë und é zu kümmern: Sie schlägt das richtig geschriebene Wort vor. Wenn Sie sie lieber selbst tippen, hat jedes seine eigene Taste."),
            ("<h3>Il rattrape les fautes de frappe</h3>", "<h3>Sie fängt Tippfehler ab</h3>"),
            ("Une lettre oubliée, une lettre en trop, une touche voisine&nbsp;: le bon mot arrive quand même.",
             "Ein vergessener Buchstabe, ein Buchstabe zu viel, eine Nachbartaste: Das richtige Wort kommt trotzdem."),
            ("<h3>Il connaît l'orthographe</h3>", "<h3>Sie kennt die Rechtschreibung</h3>"),
            ("Les noms prennent leur majuscule, comme le veut l'orthographe, et les mots longs s'écrivent en un appui. Le clavier s'appuie sur le dictionnaire officiel de la langue&nbsp;: vous n'inventez rien, vous choisissez.",
             "Substantive werden großgeschrieben, wie es die Rechtschreibung verlangt, und lange Wörter schreiben sich mit einem Tippen. Die Tastatur stützt sich auf das offizielle Wörterbuch der Sprache: Sie erfinden nichts, Sie wählen aus."),
            ("Clavier d'essai : il écrit tout seul des phrases en luxembourgeois ; touchez une lettre ou le micro pour écrire à votre tour",
             "Testtastatur: Sie schreibt von selbst Sätze auf Luxemburgisch; tippen Sie auf einen Buchstaben oder das Mikrofon, um selbst zu schreiben"),
            ("Le micro envoie votre voix à l'Université du Luxembourg, qui l'écrit sans la garder.",
             "Das Mikrofon schickt Ihre Stimme an die Universität Luxemburg, die sie aufschreibt, ohne sie zu speichern."),
            ('Parlez en luxembourgeois, <span class="accent">il écrit</span>',
             'Sprechen Sie Luxemburgisch, <span class="accent">sie schreibt</span>'),
            ("Plus besoin de chercher l'orthographe&nbsp;: dites votre phrase, le clavier l'écrit pour vous, avec les majuscules et la ponctuation.",
             "Sie müssen die Schreibweise nicht mehr suchen: Sprechen Sie Ihren Satz, die Tastatur schreibt ihn für Sie, mit Großschreibung und Satzzeichen."),
            ("<strong>Touchez le micro</strong>, à droite des suggestions.",
             "<strong>Tippen Sie auf das Mikrofon</strong>, rechts neben den Vorschlägen."),
            ("<strong>Parlez normalement.</strong> Le texte s'écrit pendant que vous parlez, et le micro se referme tout seul quand vous vous arrêtez.",
             "<strong>Sprechen Sie ganz normal.</strong> Der Text erscheint, während Sie sprechen, und das Mikrofon schließt sich von selbst, wenn Sie aufhören."),
            ("<strong>Relisez</strong>, puis envoyez.", "<strong>Lesen Sie es durch</strong> und senden Sie ab."),
            ("Le micro fait partie du clavier&nbsp;: il vous suit partout où vous écrivez.",
             "Das Mikrofon gehört zur Tastatur: Es begleitet Sie überall, wo Sie schreiben."),
            ('aria-label="Exemples d\'applications"', 'aria-label="Beispiele für Apps"'),
            ("<span>Mails</span>", "<span>E-Mail</span>"),
            ("<span>Notes</span>", "<span>Notizen</span>"),
            ("<strong>Votre voix ne part que lorsque vous touchez le micro</strong>, vers le service de reconnaissance vocale de l'Université du Luxembourg, qui la transforme en texte sans la garder. Il faut donc une connexion Internet pour dicter. Ce que vous tapez, lui, reste sur votre téléphone.",
             "<strong>Ihre Stimme verlässt das Handy nur, wenn Sie auf das Mikrofon tippen</strong>, und geht an den Spracherkennungsdienst der Universität Luxemburg, der sie in Text umwandelt, ohne sie zu speichern. Zum Diktieren brauchen Sie deshalb eine Internetverbindung. Was Sie tippen, bleibt auf Ihrem Handy."),
            ("Un micro rouge pulse pendant la dictée, et les mots de la phrase « Op der Kiermes iessen ech ëmmer Gromperekichelcher mat Äppelkompott. » apparaissent dans la bulle, un à un",
             "Ein rotes Mikrofon pulsiert während des Diktats, und die Wörter des Satzes „Op der Kiermes iessen ech ëmmer Gromperekichelcher mat Äppelkompott.“ erscheinen nacheinander in der Sprechblase"),
            ("«&nbsp;À la kermesse, je mange toujours des galettes de pommes de terre avec de la compote.&nbsp;» Le texte s'écrit pendant qu'on parle.",
             "„Auf der Kirmes esse ich immer Kartoffelpuffer mit Apfelmus.“ Der Text erscheint, während man spricht."),
            ("Trois cartes en éventail : Hond (chien) en bronze, Kuch (gâteau) en argent, Kaz (chat) en or. Chacune porte un dessin devant un lieu du pays, le mot, sa nature, son sens en français et une phrase d'exemple",
             "Drei Karten als Fächer: Hond (Hund) in Bronze, Kuch (Kuchen) in Silber, Kaz (Katze) in Gold. Jede zeigt eine Zeichnung vor einem Ort des Landes, das Wort, seine Wortart, seine Bedeutung auf Französisch und einen Beispielsatz"),
            ('<p class="surtitre-clair">En prime</p>', '<p class="surtitre-clair">Als Zugabe</p>'),
            ("Chaque mot trouvé <span class=\"accent\">devient une carte</span>", "Jedes gefundene Wort <span class=\"accent\">wird zur Karte</span>"),
            ("Sept petits jeux vous font rencontrer des mots nouveaux. Chaque mot gagné rejoint votre carnet, sur une carte ouverte sur un lieu du pays, et revient de lui-même jusqu'à ce que vous le reteniez.",
             "Sieben kleine Spiele lassen Sie neue Wörter entdecken. Jedes gewonnene Wort kommt in Ihr Sammelheft, auf eine Karte mit einem Ort des Landes, und kehrt von selbst zurück, bis Sie es sich gemerkt haben."),
            ('aria-label="Les sept jeux"', 'aria-label="Die sieben Spiele"'),
            ("<small>mots mêlés</small>", "<small>Buchstabensalat</small>"),
            ("<small>mots mélangés</small>", "<small>Wörter entwirren</small>"),
            ("<small>mot en six essais</small>", "<small>ein Wort in sechs Versuchen</small>"),
            ("<small>le mot qui manque</small>", "<small>das fehlende Wort</small>"),
            ("<small>les nombres</small>", "<small>die Zahlen</small>"),
            ("<small>mots croisés</small>", "<small>Kreuzworträtsel</small>"),
            ("<small>la grille à remplir</small>", "<small>das Raster füllen</small>"),
            ('<a href="regle-du-jeu.html">Les cartes se jouent aussi sur la table, à deux</a>',
             '<a href="regle-du-jeu.html" hreflang="fr">Die Karten lassen sich auch am Tisch spielen, zu zweit{FR}</a>'),
            # ---- jeu de cartes en préparation ----
            # ---- annonce et précommande du jeu de cartes ----
            ('Précommandez le Wuertduell, le jeu de cartes pour apprendre le luxembourgeois en jouant.',
             'Bestellen Sie das Wuertduell vor, das Kartenspiel, mit dem Sie Luxemburgisch spielend lernen.'),
            ('<span class="annonce-nouveau">Nouveau</span>',
             '<span class="annonce-nouveau">Neu</span>'),
            ('<span class="annonce-lien">Réserver sans payer&nbsp;→</span>',
             '<span class="annonce-lien">Ohne Bezahlung reservieren&nbsp;→</span>'),
            ('<p class="surtitre-clair">Précommande</p>',
             '<p class="surtitre-clair">Vorbestellung</p>'),
            ('Précommandez <span class=\"accent\">votre jeu de cartes</span>',
             'Bestellen Sie <span class=\"accent\">Ihr Kartenspiel vor</span>'),
            ("Le Wuertduell, c'est le luxembourgeois qui s'apprend en jouant, à deux autour d'une table.",
             'Mit dem Wuertduell lernen Sie Luxemburgisch spielend, zu zweit am Tisch.'),
            ('<strong>Des mots qui restent.</strong> Vous les lisez à voix haute, vous les jouez, vous les retenez.',
             '<strong>Wörter, die bleiben.</strong> Sie lesen sie laut vor, spielen sie und merken sie sich.'),
            ("<strong>Une collection qui continue sur le téléphone.</strong> Le code de la boîte ajoute ses cartes à votre carnet dans l'application.",
             '<strong>Eine Sammlung, die auf dem Handy weitergeht.</strong> Der Code in der Schachtel fügt ihre Karten Ihrem Heft in der App hinzu.'),
            ("<strong>Rien à payer aujourd'hui.</strong> Vous réservez maintenant, et vous payez seulement quand l'impression se prépare.",
             '<strong>Heute nichts zu bezahlen.</strong> Sie reservieren jetzt und bezahlen erst, wenn der Druck vorbereitet wird.'),
            ('Quatre boîtes de 54\xa0cartes, <strong>9,90\xa0€ la boîte</strong>, ou <strong>34,90\xa0€ les quatre</strong>.',
             'Vier Schachteln mit je 54\xa0Karten, <strong>9,90\xa0€ die Schachtel</strong>, oder <strong>34,90\xa0€ für alle vier</strong>.'),
            ('<a class="reserver" href="precommande.html">Précommander mon jeu</a>',
             '<a class="reserver" href="precommande-de.html">Mein Spiel vorbestellen</a>'),
            ('<a class="annonce" href="precommande.html">', '<a class="annonce" href="precommande-de.html">'),
            ('<a class="vers-precommande" href="precommande.html"', '<a class="vers-precommande" href="precommande-de.html"'),
            ('aria-label="Voir les boîtes et réserver"',
             'aria-label="Die Schachteln ansehen und reservieren"'),
            ('<a href="regle-du-jeu.html">Lire la règle du jeu</a>',
             '<a href="regle-du-jeu.html" hreflang="fr">Die Spielregeln lesen{FR}</a>'),
            ("Maquette de la boîte de départ du Wuertduell, ouverte, bleu nuit et or. À côté, posés sur la table\xa0: le dos d'une carte, la carte de règle et trois cartes, Hond (chien), Kaz (chat) et Kéis (fromage)",
             'Modell der Startschachtel des Wuertduell, geöffnet, in Nachtblau und Gold. Daneben auf dem Tisch: die Rückseite einer Karte, die Regelkarte und drei Karten, Hond (Hund), Kaz (Katze) und Kéis (Käse)'),
            ('Une maquette de la boîte de départ\xa0: les cartes, leur dos et la carte de règle.',
             'Ein Modell der Startschachtel: die Karten, ihre Rückseite und die Regelkarte.'),
            ("Vos <span class=\"accent\">questions</span>", "Ihre <span class=\"accent\">Fragen</span>"),
            ("«&nbsp;Mon téléphone souligne mes mots en rouge.&nbsp;»", "„Mein Handy unterstreicht meine Wörter rot.“"),
            ("Activez le correcteur du clavier&nbsp;: vos mots luxembourgeois sont reconnus dans vos messages, vos notes et vos mails.",
             "Aktivieren Sie die Rechtschreibprüfung der Tastatur: Ihre luxemburgischen Wörter werden in Ihren Nachrichten, Notizen und E-Mails erkannt."),
            ("«&nbsp;Je mélange avec le français.&nbsp;»", "„Ich mische mit Französisch.“"),
            ("Allumez «&nbsp;Propositions en français&nbsp;» dans les réglages du clavier&nbsp;: «&nbsp;ech hunn eng réunion muer&nbsp;» s'écrit sans changer de clavier.",
             "Schalten Sie „Französische Vorschläge“ in den Einstellungen der Tastatur ein: „ech hunn eng réunion muer“ schreibt sich, ohne die Tastatur zu wechseln."),
            ("«&nbsp;Android m'affiche un avertissement.&nbsp;»", "„Android zeigt mir eine Warnung.“"),
            ("Il s'affiche pour tous les claviers, sans exception, au moment de les activer. Celui-ci n'envoie rien de ce que vous tapez.",
             "Sie erscheint bei jeder Tastatur, ohne Ausnahme, wenn man sie aktiviert. Diese hier sendet nichts von dem, was Sie tippen."),
            ("«&nbsp;J'ai un iPhone.&nbsp;»", "„Ich habe ein iPhone.“"),
            ("Le clavier existe pour l'instant sur Android. Sur iPhone ou sur ordinateur, essayez-le plus haut sur cette page&nbsp;: touchez une lettre du clavier d'essai.",
             "Die Tastatur gibt es vorerst für Android. Auf dem iPhone oder am Computer probieren Sie sie weiter oben auf dieser Seite aus: Tippen Sie auf einen Buchstaben der Testtastatur."),
            ('<a href="faq.html">Toutes les réponses</a>', '<a href="faq.html" hreflang="fr">Alle Antworten{FR}</a>'),
            ("Installer <span class=\"accent\">en trois étapes</span>", "In drei Schritten <span class=\"accent\">installieren</span>"),
            ("<strong>Installez l'application</strong> depuis Google Play.",
             "<strong>Installieren Sie die App</strong> über Google Play."),
            ("<strong>Suivez le guide</strong>&nbsp;: elle vous montre comment activer le clavier, étape par étape.",
             "<strong>Folgen Sie der Anleitung</strong>: Die App zeigt Ihnen Schritt für Schritt, wie Sie die Tastatur aktivieren."),
            ("<strong>Essayez-le sur place</strong>, puis écrivez votre premier message.",
             "<strong>Probieren Sie sie direkt aus</strong> und schreiben Sie dann Ihre erste Nachricht."),
            ('aria-label="Ouvrir la page de l\'application sur Google Play"',
             'aria-label="Die Seite der App bei Google Play öffnen"'),
            ('alt="Code QR ouvrant la page de l\'application sur Google Play"',
             'alt="QR-Code, der die Seite der App bei Google Play öffnet"'),
            ("Scannez avec votre téléphone", "Mit dem Handy scannen"),
            ('aria-label="Pour aller plus loin"', 'aria-label="Mehr erfahren"'),
            ("<h4>Bien démarrer</h4>", "<h4>Erste Schritte</h4>"),
            ('<a href="en-detail.html">Le clavier en détail</a>', '<a href="en-detail-de.html">Die Tastatur im Detail</a>'),
            ('<a href="guide.html">Le guide</a>', '<a href="guide.html" hreflang="fr">Die Anleitung{FR}</a>'),
            ('<a href="faq.html">Aide</a>', '<a href="faq.html" hreflang="fr">Hilfe{FR}</a>'),
            ('<a href="nouveautes.html">Nouveautés</a>', '<a href="nouveautes-de.html">Neuigkeiten</a>'),
            ("<h4>Le projet</h4>", "<h4>Das Projekt</h4>"),
            ('<a href="regle-du-jeu.html">Règle du jeu des cartes</a>', '<a href="regle-du-jeu.html" hreflang="fr">Spielregeln der Karten{FR}</a>'),
            ('<a href="presse.html">Presse</a>', '<a href="presse.html" hreflang="fr">Presse{FR}</a>'),
            ('<a href="ambassadeurs.html">Ambassadeurs</a>', '<a href="ambassadeurs.html" hreflang="fr">Botschafter{FR}</a>'),
            ("<h4>Pour les curieux</h4>", "<h4>Für Neugierige</h4>"),
            ('<a href="comparatif.html">Face aux autres claviers</a>', '<a href="comparatif.html" hreflang="fr">Im Vergleich mit anderen Tastaturen{FR}</a>'),
            ('<a href="corpus.html">D\'où viennent les mots</a>', '<a href="corpus.html" hreflang="fr">Woher die Wörter kommen{FR}</a>'),
            ('<a href="labs.html">Labs</a>', '<a href="labs.html" hreflang="fr">Labs{FR}</a>'),
            ('<a href="privacy/privacy-policy.html">Confidentialité</a>', '<a href="privacy/privacy-policy.html" hreflang="fr">Datenschutz{FR}</a>'),
            ("De Lëtzebuerger Clavier est un projet indépendant. Les mots viennent du Lëtzebuerger Online Dictionnaire (Zenter fir d'Lëtzebuerger Sprooch) et de textes luxembourgeois ouverts&nbsp;; la dictée passe par le service de reconnaissance vocale de l'Université du Luxembourg.",
             "De Lëtzebuerger Clavier ist ein unabhängiges Projekt. Die Wörter stammen aus dem Lëtzebuerger Online Dictionnaire (Zenter fir d'Lëtzebuerger Sprooch) und aus offenen luxemburgischen Texten; das Diktat läuft über den Spracherkennungsdienst der Universität Luxemburg. Mit FR markierte Seiten sind auf Französisch."),
        ],
    },
    "pt": {
        "locale": "pt_PT",
        "ancre_installer": "instalar",
        "fr_titre": "em francês",
        "theme": ("Mudar para o modo escuro", "🌙 Escuro"),
        "labs": "labs.html",
        "textes": [
            ("De Lëtzebuerger Clavier · Vous le parlez. Maintenant, vous l'écrivez.",
             "De Lëtzebuerger Clavier · Fala-o. Agora, escreve-o."),
            ("Le clavier qui écrit le luxembourgeois avec vous : il propose les mots pendant que vous tapez, met les ë et les é à leur place et écrit ce que vous lui dites. Gratuit sur Android.",
             "O teclado que escreve luxemburguês consigo: sugere as palavras enquanto escreve, põe o ë e o é no sítio certo e escreve o que lhe diz. Grátis no Android."),
            ('content="Vous le parlez. Maintenant, vous l\'écrivez. 🇱🇺"',
             'content="Fala-o. Agora, escreve-o. 🇱🇺"'),
            ("Tapez ou dictez vos messages en luxembourgeois, directement sur votre téléphone. Gratuit sur Android.",
             "Escreva ou dite as suas mensagens em luxemburguês, diretamente no telemóvel. Grátis no Android."),
            ('aria-label="Langue"', 'aria-label="Idioma"'),
            ("Le clavier luxembourgeois</p>", "O teclado luxemburguês</p>"),
            ("<h1>Vous le parlez. <span>Maintenant, vous l'écrivez.</span></h1>",
             "<h1>Fala-o. <span>Agora, escreve-o.</span></h1>"),
            ("Le clavier vous propose les mots luxembourgeois pendant que vous tapez, met les ë et les é à leur place, et écrit pour vous ce que vous lui dites.",
             "O teclado sugere-lhe as palavras luxemburguesas enquanto escreve, põe o ë e o é no sítio certo e escreve por si o que lhe diz."),
            ('alt="Disponible sur Google Play"', 'alt="Disponível no Google Play"'),
            ("Gratuit · Android 7.0 et plus récent", "Grátis · Android 7.0 ou mais recente"),
            ('alt="Bientôt sur l\'App Store"', 'alt="Em breve na App Store"'),
            (">S'inscrire à la newsletter</a>", ">Inscrever-se na newsletter</a>"),
            ('aria-label="Installer de Lëtzebuerger Clavier depuis Google Play"',
             'aria-label="Instalar de Lëtzebuerger Clavier a partir do Google Play"'),
            ("La phrase « Ech hunn op der Schueberfouer Gromperekichelcher giess » tapée sur le clavier : après « op der », la barre propose la suite, et les mots longs se complètent en un appui",
             "A frase «Ech hunn op der Schueberfouer Gromperekichelcher giess» escrita no teclado: depois de «op der», a barra sugere a continuação, e as palavras longas completam-se com um toque"),
            ("<figcaption>«&nbsp;Ech hunn op der Schueberfouer Gromperekichelcher giess&nbsp;»</figcaption>",
             "<figcaption>«Ech hunn op der Schueberfouer Gromperekichelcher giess»</figcaption>"),
            ('Écrire <span class="accent">comme on parle</span>',
             'Escrever <span class="accent">como se fala</span>'),
            ("Vous savez le dire, mais l'écrire, c'est autre chose&nbsp;: où va le ë, faut-il une majuscule, un e ou deux&nbsp;? Le clavier vous répond pendant que vous tapez. Regardez-le écrire, puis prenez la main&nbsp;: touchez une lettre du clavier d'essai, ou son micro pour lui parler.",
             "Sabe dizê-lo, mas escrevê-lo é outra coisa: onde fica o ë, leva maiúscula, um e ou dois? O teclado responde-lhe enquanto escreve. Veja-o escrever e depois assuma o controlo: toque numa letra do teclado de teste, ou no microfone para lhe falar."),
            ("<h3>Il devine la suite</h3>", "<h3>Adivinha a continuação</h3>"),
            ("Après «&nbsp;nach eng&nbsp;», il propose déjà «&nbsp;Kéier&nbsp;». Il tient compte de ce que vous venez d'écrire&nbsp;: un appui, et le mot suivant est posé.",
             "Depois de «nach eng», já sugere «Kéier». Tem em conta o que acabou de escrever: um toque, e a palavra seguinte fica escrita."),
            ("<h3>Il met les accents à leur place</h3>", "<h3>Põe os acentos no sítio</h3>"),
            ("Tapez sans vous soucier des ä, ë et é&nbsp;: il propose le mot bien écrit. Si vous préférez les taper vous-même, ils ont chacun leur touche.",
             "Escreva sem se preocupar com o ä, o ë e o é: sugere a palavra bem escrita. Se preferir escrevê-los, cada um tem a sua tecla."),
            ("<h3>Il rattrape les fautes de frappe</h3>", "<h3>Corrige as gralhas</h3>"),
            ("Une lettre oubliée, une lettre en trop, une touche voisine&nbsp;: le bon mot arrive quand même.",
             "Uma letra esquecida, uma letra a mais, uma tecla ao lado: a palavra certa aparece na mesma."),
            ("<h3>Il connaît l'orthographe</h3>", "<h3>Conhece a ortografia</h3>"),
            ("Les noms prennent leur majuscule, comme le veut l'orthographe, et les mots longs s'écrivent en un appui. Le clavier s'appuie sur le dictionnaire officiel de la langue&nbsp;: vous n'inventez rien, vous choisissez.",
             "Os substantivos levam maiúscula, como manda a ortografia luxemburguesa, e as palavras longas escrevem-se com um toque. O teclado baseia-se no dicionário oficial da língua: não inventa nada, escolhe."),
            ("Clavier d'essai : il écrit tout seul des phrases en luxembourgeois ; touchez une lettre ou le micro pour écrire à votre tour",
             "Teclado de teste: escreve sozinho frases em luxemburguês; toque numa letra ou no microfone para escrever"),
            ("Le micro envoie votre voix à l'Université du Luxembourg, qui l'écrit sans la garder.",
             "O microfone envia a sua voz para a Universidade do Luxemburgo, que a transcreve sem a guardar."),
            ('Parlez en luxembourgeois, <span class="accent">il écrit</span>',
             'Fale luxemburguês, <span class="accent">ele escreve</span>'),
            ("Plus besoin de chercher l'orthographe&nbsp;: dites votre phrase, le clavier l'écrit pour vous, avec les majuscules et la ponctuation.",
             "Deixe de procurar a ortografia: diga a sua frase e o teclado escreve-a por si, com maiúsculas e pontuação."),
            ("<strong>Touchez le micro</strong>, à droite des suggestions.",
             "<strong>Toque no microfone</strong>, à direita das sugestões."),
            ("<strong>Parlez normalement.</strong> Le texte s'écrit pendant que vous parlez, et le micro se referme tout seul quand vous vous arrêtez.",
             "<strong>Fale normalmente.</strong> O texto vai aparecendo enquanto fala, e o microfone fecha-se sozinho quando para."),
            ("<strong>Relisez</strong>, puis envoyez.", "<strong>Releia</strong> e envie."),
            ("Le micro fait partie du clavier&nbsp;: il vous suit partout où vous écrivez.",
             "O microfone faz parte do teclado: acompanha-o em todo o lado onde escreve."),
            ('aria-label="Exemples d\'applications"', 'aria-label="Exemplos de aplicações"'),
            ("<span>Mails</span>", "<span>E-mail</span>"),
            ("<span>Notes</span>", "<span>Notas</span>"),
            ("<strong>Votre voix ne part que lorsque vous touchez le micro</strong>, vers le service de reconnaissance vocale de l'Université du Luxembourg, qui la transforme en texte sans la garder. Il faut donc une connexion Internet pour dicter. Ce que vous tapez, lui, reste sur votre téléphone.",
             "<strong>A sua voz só sai do telemóvel quando toca no microfone</strong>, para o serviço de reconhecimento de voz da Universidade do Luxemburgo, que a transforma em texto sem a guardar. Por isso, precisa de uma ligação à Internet para ditar. O que escreve no teclado fica no seu telemóvel."),
            ("Un micro rouge pulse pendant la dictée, et les mots de la phrase « Op der Kiermes iessen ech ëmmer Gromperekichelcher mat Äppelkompott. » apparaissent dans la bulle, un à un",
             "Um microfone vermelho pulsa durante o ditado, e as palavras da frase «Op der Kiermes iessen ech ëmmer Gromperekichelcher mat Äppelkompott.» aparecem no balão, uma a uma"),
            ("«&nbsp;À la kermesse, je mange toujours des galettes de pommes de terre avec de la compote.&nbsp;» Le texte s'écrit pendant qu'on parle.",
             "«Na feira, como sempre panquecas de batata com puré de maçã.» O texto aparece enquanto se fala."),
            ("Trois cartes en éventail : Hond (chien) en bronze, Kuch (gâteau) en argent, Kaz (chat) en or. Chacune porte un dessin devant un lieu du pays, le mot, sa nature, son sens en français et une phrase d'exemple",
             "Três cartas em leque: Hond (cão) em bronze, Kuch (bolo) em prata, Kaz (gato) em ouro. Cada uma mostra um desenho diante de um lugar do país, a palavra, a sua classe, o seu significado em francês e uma frase de exemplo"),
            ('<p class="surtitre-clair">En prime</p>', '<p class="surtitre-clair">Como bónus</p>'),
            ("Chaque mot trouvé <span class=\"accent\">devient une carte</span>", "Cada palavra encontrada <span class=\"accent\">torna-se uma carta</span>"),
            ("Sept petits jeux vous font rencontrer des mots nouveaux. Chaque mot gagné rejoint votre carnet, sur une carte ouverte sur un lieu du pays, et revient de lui-même jusqu'à ce que vous le reteniez.",
             "Sete pequenos jogos fazem-no descobrir palavras novas. Cada palavra ganha entra na sua caderneta, numa carta aberta sobre um lugar do país, e volta por si até a fixar."),
            ('aria-label="Les sept jeux"', 'aria-label="Os sete jogos"'),
            ("<small>mots mêlés</small>", "<small>sopa de letras</small>"),
            ("<small>mots mélangés</small>", "<small>palavras baralhadas</small>"),
            ("<small>mot en six essais</small>", "<small>uma palavra em seis tentativas</small>"),
            ("<small>le mot qui manque</small>", "<small>a palavra que falta</small>"),
            ("<small>les nombres</small>", "<small>os números</small>"),
            ("<small>mots croisés</small>", "<small>palavras cruzadas</small>"),
            ("<small>la grille à remplir</small>", "<small>a grelha para preencher</small>"),
            ('<a href="regle-du-jeu.html">Les cartes se jouent aussi sur la table, à deux</a>',
             '<a href="regle-du-jeu.html" hreflang="fr">As cartas também se jogam à mesa, a dois{FR}</a>'),
            # ---- jeu de cartes en préparation ----
            # ---- annonce et précommande du jeu de cartes ----
            ('Précommandez le Wuertduell, le jeu de cartes pour apprendre le luxembourgeois en jouant.',
             'Pré-encomende o Wuertduell, o jogo de cartas para aprender luxemburguês a jogar.'),
            ('<span class="annonce-nouveau">Nouveau</span>',
             '<span class="annonce-nouveau">Novo</span>'),
            ('<span class="annonce-lien">Réserver sans payer&nbsp;→</span>',
             '<span class="annonce-lien">Reservar sem pagar&nbsp;→</span>'),
            ('<p class="surtitre-clair">Précommande</p>',
             '<p class="surtitre-clair">Pré-encomenda</p>'),
            ('Précommandez <span class=\"accent\">votre jeu de cartes</span>',
             'Pré-encomende <span class=\"accent\">o seu jogo de cartas</span>'),
            ("Le Wuertduell, c'est le luxembourgeois qui s'apprend en jouant, à deux autour d'une table.",
             'O Wuertduell é o luxemburguês que se aprende a jogar, a dois à volta de uma mesa.'),
            ('<strong>Des mots qui restent.</strong> Vous les lisez à voix haute, vous les jouez, vous les retenez.',
             '<strong>Palavras que ficam.</strong> Lê-as em voz alta, joga-as e memoriza-as.'),
            ("<strong>Une collection qui continue sur le téléphone.</strong> Le code de la boîte ajoute ses cartes à votre carnet dans l'application.",
             '<strong>Uma coleção que continua no telemóvel.</strong> O código da caixa junta as suas cartas ao seu caderno na aplicação.'),
            ("<strong>Rien à payer aujourd'hui.</strong> Vous réservez maintenant, et vous payez seulement quand l'impression se prépare.",
             '<strong>Nada a pagar hoje.</strong> Reserve agora e pague só quando a impressão estiver a ser preparada.'),
            ('Quatre boîtes de 54\xa0cartes, <strong>9,90\xa0€ la boîte</strong>, ou <strong>34,90\xa0€ les quatre</strong>.',
             'Quatro caixas de 54\xa0cartas, <strong>9,90\xa0€ a caixa</strong>, ou <strong>34,90\xa0€ as quatro</strong>.'),
            ('<a class="reserver" href="precommande.html">Précommander mon jeu</a>',
             '<a class="reserver" href="precommande-pt.html">Pré-encomendar o meu jogo</a>'),
            ('<a class="annonce" href="precommande.html">', '<a class="annonce" href="precommande-pt.html">'),
            ('<a class="vers-precommande" href="precommande.html"', '<a class="vers-precommande" href="precommande-pt.html"'),
            ('aria-label="Voir les boîtes et réserver"',
             'aria-label="Ver as caixas e reservar"'),
            ('<a href="regle-du-jeu.html">Lire la règle du jeu</a>',
             '<a href="regle-du-jeu.html" hreflang="fr">Ler as regras do jogo{FR}</a>'),
            ("Maquette de la boîte de départ du Wuertduell, ouverte, bleu nuit et or. À côté, posés sur la table\xa0: le dos d'une carte, la carte de règle et trois cartes, Hond (chien), Kaz (chat) et Kéis (fromage)",
             'Maquete da caixa inicial do Wuertduell, aberta, em azul-noite e dourado. Ao lado, na mesa: o verso de uma carta, a carta de regras e três cartas, Hond (cão), Kaz (gato) e Kéis (queijo)'),
            ('Une maquette de la boîte de départ\xa0: les cartes, leur dos et la carte de règle.',
             'Uma maquete da caixa inicial: as cartas, o verso e a carta de regras.'),
            ("Vos <span class=\"accent\">questions</span>", "As suas <span class=\"accent\">perguntas</span>"),
            ("«&nbsp;Mon téléphone souligne mes mots en rouge.&nbsp;»", "«O meu telemóvel sublinha as minhas palavras a vermelho.»"),
            ("Activez le correcteur du clavier&nbsp;: vos mots luxembourgeois sont reconnus dans vos messages, vos notes et vos mails.",
             "Ative o corretor do teclado: as suas palavras luxemburguesas passam a ser reconhecidas nas mensagens, nas notas e nos e-mails."),
            ("«&nbsp;Je mélange avec le français.&nbsp;»", "«Misturo com francês.»"),
            ("Allumez «&nbsp;Propositions en français&nbsp;» dans les réglages du clavier&nbsp;: «&nbsp;ech hunn eng réunion muer&nbsp;» s'écrit sans changer de clavier.",
             "Ative «Sugestões em francês» nas definições do teclado: «ech hunn eng réunion muer» escreve-se sem mudar de teclado."),
            ("«&nbsp;Android m'affiche un avertissement.&nbsp;»", "«O Android mostra-me um aviso.»"),
            ("Il s'affiche pour tous les claviers, sans exception, au moment de les activer. Celui-ci n'envoie rien de ce que vous tapez.",
             "Aparece para todos os teclados, sem exceção, no momento de os ativar. Este não envia nada do que escreve."),
            ("«&nbsp;J'ai un iPhone.&nbsp;»", "«Tenho um iPhone.»"),
            ("Le clavier existe pour l'instant sur Android. Sur iPhone ou sur ordinateur, essayez-le plus haut sur cette page&nbsp;: touchez une lettre du clavier d'essai.",
             "Por agora, o teclado existe para Android. No iPhone ou no computador, experimente-o mais acima nesta página: toque numa letra do teclado de teste."),
            ('<a href="faq.html">Toutes les réponses</a>', '<a href="faq.html" hreflang="fr">Todas as respostas{FR}</a>'),
            ("Installer <span class=\"accent\">en trois étapes</span>", "Instalar <span class=\"accent\">em três passos</span>"),
            ("<strong>Installez l'application</strong> depuis Google Play.",
             "<strong>Instale a aplicação</strong> a partir do Google Play."),
            ("<strong>Suivez le guide</strong>&nbsp;: elle vous montre comment activer le clavier, étape par étape.",
             "<strong>Siga o guia</strong>: a aplicação mostra-lhe como ativar o teclado, passo a passo."),
            ("<strong>Essayez-le sur place</strong>, puis écrivez votre premier message.",
             "<strong>Experimente-o logo ali</strong> e depois escreva a sua primeira mensagem."),
            ('aria-label="Ouvrir la page de l\'application sur Google Play"',
             'aria-label="Abrir a página da aplicação no Google Play"'),
            ('alt="Code QR ouvrant la page de l\'application sur Google Play"',
             'alt="Código QR que abre a página da aplicação no Google Play"'),
            ("Scannez avec votre téléphone", "Digitalize com o telemóvel"),
            ('aria-label="Pour aller plus loin"', 'aria-label="Saber mais"'),
            ("<h4>Bien démarrer</h4>", "<h4>Primeiros passos</h4>"),
            ('<a href="en-detail.html">Le clavier en détail</a>', '<a href="en-detail-pt.html">O teclado em detalhe</a>'),
            ('<a href="guide.html">Le guide</a>', '<a href="guide.html" hreflang="fr">O guia{FR}</a>'),
            ('<a href="faq.html">Aide</a>', '<a href="faq.html" hreflang="fr">Ajuda{FR}</a>'),
            ('<a href="nouveautes.html">Nouveautés</a>', '<a href="nouveautes-pt.html">Novidades</a>'),
            ("<h4>Le projet</h4>", "<h4>O projeto</h4>"),
            ('<a href="regle-du-jeu.html">Règle du jeu des cartes</a>', '<a href="regle-du-jeu.html" hreflang="fr">Regras do jogo de cartas{FR}</a>'),
            ('<a href="presse.html">Presse</a>', '<a href="presse.html" hreflang="fr">Imprensa{FR}</a>'),
            ('<a href="ambassadeurs.html">Ambassadeurs</a>', '<a href="ambassadeurs.html" hreflang="fr">Embaixadores{FR}</a>'),
            ("<h4>Pour les curieux</h4>", "<h4>Para curiosos</h4>"),
            ('<a href="comparatif.html">Face aux autres claviers</a>', '<a href="comparatif.html" hreflang="fr">Face aos outros teclados{FR}</a>'),
            ('<a href="corpus.html">D\'où viennent les mots</a>', '<a href="corpus.html" hreflang="fr">De onde vêm as palavras{FR}</a>'),
            ('<a href="labs.html">Labs</a>', '<a href="labs.html" hreflang="fr">Labs{FR}</a>'),
            ('<a href="privacy/privacy-policy.html">Confidentialité</a>', '<a href="privacy/privacy-policy.html" hreflang="fr">Privacidade{FR}</a>'),
            ("De Lëtzebuerger Clavier est un projet indépendant. Les mots viennent du Lëtzebuerger Online Dictionnaire (Zenter fir d'Lëtzebuerger Sprooch) et de textes luxembourgeois ouverts&nbsp;; la dictée passe par le service de reconnaissance vocale de l'Université du Luxembourg.",
             "De Lëtzebuerger Clavier é um projeto independente. As palavras vêm do Lëtzebuerger Online Dictionnaire (Zenter fir d'Lëtzebuerger Sprooch) e de textos luxemburgueses abertos; o ditado passa pelo serviço de reconhecimento de voz da Universidade do Luxemburgo. As páginas marcadas com FR estão em francês."),
        ],
    },
}

# Mots français qui ne doivent plus apparaître dans le texte visible d'une page
# traduite. Les exemples luxembourgeois (« ech hunn eng réunion muer »…) n'en
# contiennent aucun.
MOTS_FRANCAIS = ["vous", "votre", "vos", "avec", "pendant", "chaque", "clavier d'essai",
                 "écrit", "Installer", "Gratuit", "toujours", "les mots", "où", "Aide"]


def motif(texte):
    """Le texte français comme expression régulière : tout blanc vaut \\s+."""
    morceaux = re.split(r"\s+", texte.strip())
    return r"\s+".join(re.escape(m) for m in morceaux)


def remplacer(page, avant, apres, tous=False, nom=""):
    rx = re.compile(motif(avant))
    trouve = len(rx.findall(page))
    if trouve == 0:
        sys.exit(f"[{nom}] texte français introuvable dans index.html :\n  {avant[:120]}")
    if trouve > 1 and not tous:
        sys.exit(f"[{nom}] texte français présent {trouve} fois, attendu une seule :\n  {avant[:120]}")
    return rx.sub(lambda _: apres, page)


def generer(langue, cfg, source):
    page = source
    nom = langue
    pastille = PASTILLE_FR.format(titre=cfg["fr_titre"])
    url = f"{BASE}{langue}.html"

    # ---- document ----
    page = remplacer(page, '<html lang="fr">', f'<html lang="{langue}">', nom=nom)
    page = remplacer(page, f'<link rel="canonical" href="{BASE}">', f'<link rel="canonical" href="{url}">', nom=nom)
    page = remplacer(page, '<meta property="og:locale" content="fr_FR">',
                     f'<meta property="og:locale" content="{cfg["locale"]}">', nom=nom)
    page = remplacer(page, f'<meta property="og:url" content="{BASE}">',
                     f'<meta property="og:url" content="{url}">', nom=nom)
    page = remplacer(page, "assets/og/partage.png", f"assets/og/partage-{langue}.png", nom=nom)
    page = re.sub(r"<!--\s+Accueil grand public.*?-->",
                  f"<!--\n  GÉNÉRÉ par docs/scripts/generer_accueils.py depuis index.html : ne pas\n"
                  f"  modifier à la main. Traduction écrite par Claude, à faire relire.\n-->",
                  page, count=1, flags=re.S)
    # Pastille « FR » des liens vers des pages en français seulement
    page = remplacer(page, "</style>",
                     ".langue-fr{font-size:11px;font-weight:700;letter-spacing:.06em;border:1px solid var(--filet);"
                     "border-radius:4px;padding:0 4px;margin-left:6px;vertical-align:1px;color:var(--encre-douce)}\n"
                     ".installer .langue-fr,.ouverture .langue-fr{border-color:rgba(255,255,255,.35);color:var(--sur-nuit-doux)}\n"
                     "</style>", nom=nom)

    # ---- sélecteur de langue et thème ----
    page = remplacer(page, "<strong>FR</strong>", '<a href="./" hreflang="fr" lang="fr">FR</a>', nom=nom)
    page = remplacer(page, f'<a href="{langue}.html" hreflang="{langue}" lang="{langue}">{langue.upper()}</a>',
                     f"<strong>{langue.upper()}</strong>", nom=nom)
    page = remplacer(page, 'aria-label="Passer en mode sombre">🌙 Fond sombre</button>',
                     f'aria-label="{cfg["theme"][0]}">{cfg["theme"][1]}</button>', nom=nom)

    # ---- Google Play : langue de la fiche et suivi par page ----
    page = remplacer(page, "luxkeyboard&amp;referrer=", f"luxkeyboard&amp;hl={langue}&amp;referrer=", tous=True, nom=nom)
    page = remplacer(page, "utm_medium%3Daccueil%26", f"utm_medium%3Daccueil-{langue}%26", tous=True, nom=nom)
    page = remplacer(page, "assets/google-play-badge-fr.svg", f"assets/google-play-badge-{langue}.svg", tous=True, nom=nom)

    # ---- clavier d'essai dans la langue de la page ----
    page = remplacer(page, 'src="simulateur.html?embed=1&amp;demo=auto"',
                     f'src="simulateur.html?embed=1&amp;demo=auto&amp;lang={langue}"', nom=nom)

    # ---- ancre de l'installation, celle des anciennes pages ----
    page = remplacer(page, 'id="installer"', f'id="{cfg["ancre_installer"]}"', nom=nom)

    # ---- textes ----
    for avant, apres in cfg["textes"]:
        page = remplacer(page, avant, apres.replace("{FR}", pastille), tous=True, nom=nom)

    # ---- contrôle : plus de français visible ----
    visible = re.sub(r"<(style|script)\b.*?</\1>", " ", page, flags=re.S)
    visible = re.sub(r"<!--.*?-->", " ", visible, flags=re.S)
    attributs = " ".join(re.findall(r'(?:alt|title|aria-label|content)="([^"]*)"', visible))
    visible = re.sub(r"<[^>]+>", " ", visible) + " " + attributs
    restes = [m for m in MOTS_FRANCAIS if re.search(rf"(?<![\w']){re.escape(m)}(?![\w])", visible)]
    if restes:
        sys.exit(f"[{nom}] mots français restants dans le texte visible : {restes}")
    return page


def main():
    source = SOURCE.read_text(encoding="utf-8")
    for langue, cfg in LANGUES.items():
        page = generer(langue, cfg, source)
        sortie = DOCS / f"{langue}.html"
        sortie.write_text(page, encoding="utf-8")
        print(f"{sortie.relative_to(DOCS)} : {len(page):,} octets")


if __name__ == "__main__":
    main()
