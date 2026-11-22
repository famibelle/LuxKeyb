#!/usr/bin/env python3
"""Décline la newsletter d'octobre 2026 en anglais, allemand et portugais.

La page française (docs/newsletter/2026-10.html) reste la source. Chaque
fragment français exact est remplacé par sa traduction ; le script refuse
d'écrire une page si un fragment manque dans la source.

Termes repris de l'application (res/values*, strings.xml) : notebook / Heft /
caderno pour le carnet, quill / Feder / pena pour la plume, les raretés
Common / Häufig / Comum… L'allemand vouvoie (Sie), le portugais est celui
du Portugal.

    python3 .claude/skills/newsletter-substack/traduire_newsletter_2026_10.py
"""
import pathlib
import sys

ICI = pathlib.Path(__file__).resolve()
NL = ICI.parents[3] / "docs" / "newsletter"
SOURCE = NL / "2026-10.html"
LANGUES = ["en", "de", "pt"]

T = [
    ('<html lang="fr">', {"en": '<html lang="en">', "de": '<html lang="de">', "pt": '<html lang="pt">'}),
    ('<title>Lëtzebuergesch Clavier, octobre 2026</title>', {
        "en": '<title>Lëtzebuergesch Clavier, October 2026</title>',
        "de": '<title>Lëtzebuergesch Clavier, Oktober 2026</title>',
        "pt": '<title>Lëtzebuergesch Clavier, outubro de 2026</title>'}),
    ("Tapez, il propose le mot. Parlez, il écrit. Et des cartes à collectionner pour découvrir le luxembourgeois : les nouveautés d'octobre du clavier luxembourgeois pour Android.", {
        "en": "Type, it suggests the word. Speak, it writes. And collectible cards to discover Luxembourgish: what's new in October in the Luxembourgish keyboard for Android.",
        "de": "Tippen Sie, und es schlägt das Wort vor. Sprechen Sie, und es schreibt. Dazu Sammelkarten, um Luxemburgisch zu entdecken: die Neuigkeiten der luxemburgischen Tastatur für Android im Oktober.",
        "pt": "Escreva, e ele sugere a palavra. Fale, e ele escreve. E cartas colecionáveis para descobrir o luxemburguês: as novidades de outubro do teclado luxemburguês para Android."}),
    ('content="fr_FR"', {"en": 'content="en_GB"', "de": 'content="de_DE"', "pt": 'content="pt_PT"'}),
    ('content="Écrire en luxembourgeois devient encore plus simple"', {
        "en": 'content="Writing Luxembourgish just got even easier"',
        "de": 'content="Luxemburgisch schreiben wird noch einfacher"',
        "pt": 'content="Escrever em luxemburguês ficou ainda mais simples"'}),
    ("newsletter/2026-10.html", {"en": "newsletter/2026-10-en.html", "de": "newsletter/2026-10-de.html", "pt": "newsletter/2026-10-pt.html"}),
    ("newsletter/2026-10/partage.png", {"en": "newsletter/2026-10/partage-en.png", "de": "newsletter/2026-10/partage-de.png", "pt": "newsletter/2026-10/partage-pt.png"}),
    ("Tapez, il propose le mot. Parlez, il écrit. Et des cartes à collectionner pour découvrir la langue.", {
        "en": "Type, it suggests the word. Speak, it writes. And collectible cards to discover the language.",
        "de": "Tippen, es schlägt das Wort vor. Sprechen, es schreibt. Und Sammelkarten, um die Sprache zu entdecken.",
        "pt": "Escreva, ele sugere a palavra. Fale, ele escreve. E cartas colecionáveis para descobrir a língua."}),

    # ---- Sélecteur de langue ----
    ('aria-label="Langue"><a href="2026-10.html" lang="fr" aria-current="page">FR</a><a href="2026-10-en.html" lang="en">EN</a><a href="2026-10-de.html" lang="de">DE</a><a href="2026-10-pt.html" lang="pt">PT</a>', {
        "en": 'aria-label="Language"><a href="2026-10.html" lang="fr">FR</a><a href="2026-10-en.html" lang="en" aria-current="page">EN</a><a href="2026-10-de.html" lang="de">DE</a><a href="2026-10-pt.html" lang="pt">PT</a>',
        "de": 'aria-label="Sprache"><a href="2026-10.html" lang="fr">FR</a><a href="2026-10-en.html" lang="en">EN</a><a href="2026-10-de.html" lang="de" aria-current="page">DE</a><a href="2026-10-pt.html" lang="pt">PT</a>',
        "pt": 'aria-label="Língua"><a href="2026-10.html" lang="fr">FR</a><a href="2026-10-en.html" lang="en">EN</a><a href="2026-10-de.html" lang="de">DE</a><a href="2026-10-pt.html" lang="pt" aria-current="page">PT</a>'}),

    # ---- En-tête ----
    ("<span>Octobre 2026</span>", {"en": "<span>October 2026</span>", "de": "<span>Oktober 2026</span>", "pt": "<span>Outubro de 2026</span>"}),
    ("<h1>Écrire en luxembourgeois devient <em>encore plus simple</em></h1>", {
        "en": "<h1>Writing Luxembourgish just got <em>even easier</em></h1>",
        "de": "<h1>Luxemburgisch schreiben wird <em>noch einfacher</em></h1>",
        "pt": "<h1>Escrever em luxemburguês ficou <em>ainda mais simples</em></h1>"}),
    ("En un mois, le clavier a appris à vous écouter, à rattraper vos fautes de frappe et à prendre toute la place d'une tablette. Voici ce qui change pour vous.", {
        "en": "In one month, the keyboard has learned to listen to you, to catch your typos and to make full use of a tablet. Here is what changes for you.",
        "de": "In einem Monat hat die Tastatur gelernt, Ihnen zuzuhören, Ihre Tippfehler aufzufangen und den ganzen Platz eines Tablets zu nutzen. Das ändert sich für Sie.",
        "pt": "Num mês, o teclado aprendeu a ouvi-lo, a corrigir os seus erros de escrita e a ocupar todo o espaço de um tablet. Eis o que muda para si."}),

    # ---- Dictée ----
    ('<span class="version">Dictée vocale</span>', {
        "en": '<span class="version">Voice dictation</span>',
        "de": '<span class="version">Spracheingabe</span>',
        "pt": '<span class="version">Ditado por voz</span>'}),
    ("<h2>Parlez, le texte s'écrit</h2>", {
        "en": "<h2>Speak, and the text writes itself</h2>",
        "de": "<h2>Sprechen Sie, der Text schreibt sich</h2>",
        "pt": "<h2>Fale, e o texto escreve-se</h2>"}),
    ("Touchez le micro à droite de la barre de suggestions et parlez en luxembourgeois. Le texte s'écrit pendant que vous parlez, avec la ponctuation et les majuscules, et le micro se referme seul quand vous vous taisez.", {
        "en": "Tap the microphone to the right of the suggestion bar and speak Luxembourgish. The text appears as you talk, with punctuation and capitals, and the microphone closes by itself when you stop speaking.",
        "de": "Tippen Sie auf das Mikrofon rechts neben der Vorschlagsleiste und sprechen Sie Luxemburgisch. Der Text erscheint, während Sie sprechen, mit Satzzeichen und Großschreibung, und das Mikrofon schließt sich von selbst, wenn Sie schweigen.",
        "pt": "Toque no microfone à direita da barra de sugestões e fale em luxemburguês. O texto aparece enquanto fala, com pontuação e maiúsculas, e o microfone fecha-se sozinho quando se cala."}),
    ('aria-label="Le micro du clavier et la phrase dictée', {
        "en": 'aria-label="The keyboard microphone and the dictated sentence',
        "de": 'aria-label="Das Mikrofon der Tastatur und der diktierte Satz',
        "pt": 'aria-label="O microfone do teclado e a frase ditada'}),
    ("<figcaption>Dicté, pas tapé.</figcaption>", {
        "en": "<figcaption>Spoken, not typed.</figcaption>",
        "de": "<figcaption>Gesprochen, nicht getippt.</figcaption>",
        "pt": "<figcaption>Ditado, não escrito.</figcaption>"}),
    ("La reconnaissance est assurée par LuxASR, le service de l'Université du Luxembourg. Avant la première dictée, un écran vous explique où part votre voix ; rien n'est envoyé avant que vous acceptiez. Ce que vous tapez reste sur votre téléphone.", {
        "en": "Recognition is provided by LuxASR, the University of Luxembourg's service. Before your first dictation, a screen explains where your voice goes; nothing is sent until you agree. What you type stays on your phone.",
        "de": "Die Erkennung übernimmt LuxASR, der Dienst der Universität Luxemburg. Vor dem ersten Diktat erklärt ein Bildschirm, wohin Ihre Stimme geht; nichts wird gesendet, bevor Sie zustimmen. Was Sie tippen, bleibt auf Ihrem Telefon.",
        "pt": "O reconhecimento é feito pelo LuxASR, o serviço da Universidade do Luxemburgo. Antes do primeiro ditado, um ecrã explica para onde vai a sua voz; nada é enviado antes de aceitar. O que escreve fica no seu telemóvel."}),

    # ---- Fautes de frappe ----
    ('<span class="version">Fautes de frappe</span>', {
        "en": '<span class="version">Typos</span>',
        "de": '<span class="version">Tippfehler</span>',
        "pt": '<span class="version">Erros de escrita</span>'}),
    ("<h2>Le clavier rattrape vos doigts pressés</h2>", {
        "en": "<h2>The keyboard catches your hurried fingers</h2>",
        "de": "<h2>Die Tastatur fängt eilige Finger auf</h2>",
        "pt": "<h2>O teclado apanha os seus dedos apressados</h2>"}),
    ("Un pouce qui glisse, deux lettres inversées, un espace oublié : la barre propose maintenant le bon mot.", {
        "en": "A slipping thumb, two swapped letters, a forgotten space: the bar now suggests the right word.",
        "de": "Ein abrutschender Daumen, zwei vertauschte Buchstaben, ein vergessenes Leerzeichen: Die Leiste schlägt jetzt das richtige Wort vor.",
        "pt": "Um polegar que escorrega, duas letras trocadas, um espaço esquecido: a barra sugere agora a palavra certa."}),
    ('aria-label="Exemples de corrections"', {
        "en": 'aria-label="Correction examples"', "de": 'aria-label="Beispiele für Korrekturen"', "pt": 'aria-label="Exemplos de correções"'}),
    ('alt="Une phrase tapée avec des fautes, corrigée mot par mot en touchant la première suggestion"', {
        "en": 'alt="A sentence typed with mistakes, corrected word by word by tapping the first suggestion"',
        "de": 'alt="Ein mit Fehlern getippter Satz, Wort für Wort korrigiert durch Antippen des ersten Vorschlags"',
        "pt": 'alt="Uma frase escrita com erros, corrigida palavra a palavra tocando na primeira sugestão"'}),
    ("<figcaption>Tapé « echhunn geschter an der schiul gescahfft », corrigé d'un toucher à chaque faute.</figcaption>", {
        "en": "<figcaption>Typed “echhunn geschter an der schiul gescahfft”, fixed with one tap per mistake.</figcaption>",
        "de": "<figcaption>Getippt: „echhunn geschter an der schiul gescahfft“, korrigiert mit einem Tipp pro Fehler.</figcaption>",
        "pt": "<figcaption>Escrito «echhunn geschter an der schiul gescahfft», corrigido com um toque por erro.</figcaption>"}),
    ("<li>Deux mots collés se séparent, sans couper les mots composés comme « Schoulbus ».</li>", {
        "en": "<li>Two words typed together are split apart, without breaking compound words like “Schoulbus”.</li>",
        "de": "<li>Zwei zusammengeschriebene Wörter werden getrennt, ohne zusammengesetzte Wörter wie „Schoulbus“ zu zerlegen.</li>",
        "pt": "<li>Duas palavras coladas separam-se, sem cortar palavras compostas como «Schoulbus».</li>"}),
    ("<li>La touche voisine est reconnue : « haud » donne d'abord « Haus », parce que le s touche le d.</li>", {
        "en": "<li>The neighbouring key is recognised: “haud” gives “Haus” first, because s sits next to d.</li>",
        "de": "<li>Die Nachbartaste wird erkannt: „haud“ ergibt zuerst „Haus“, weil das s neben dem d liegt.</li>",
        "pt": "<li>A tecla vizinha é reconhecida: «haud» dá primeiro «Haus», porque o s fica ao lado do d.</li>"}),
    ("<li>La lettre doublée tapée une seule fois est retrouvée : « ales » donne « alles ».</li>", {
        "en": "<li>A double letter typed only once is found again: “ales” gives “alles”.</li>",
        "de": "<li>Ein nur einmal getippter Doppelbuchstabe wird erkannt: „ales“ ergibt „alles“.</li>",
        "pt": "<li>A letra dupla escrita uma só vez é recuperada: «ales» dá «alles».</li>"}),

    # ---- Éventail ----
    ('alt="Carte commune « Bam » (arbre), cadre d\'étain, paysage de printemps"', {
        "en": 'alt="Common card “Bam” (tree), pewter frame, spring landscape"',
        "de": 'alt="Häufige Karte „Bam“ (Baum), Zinnrahmen, Frühlingslandschaft"',
        "pt": 'alt="Carta comum «Bam» (árvore), moldura de estanho, paisagem de primavera"'}),
    ('alt="Carte peu commune « Sonn » (soleil), cadre de bronze, paysage d\'été"', {
        "en": 'alt="Uncommon card “Sonn” (sun), bronze frame, summer landscape"',
        "de": 'alt="Ungewöhnliche Karte „Sonn“ (Sonne), Bronzerahmen, Sommerlandschaft"',
        "pt": 'alt="Carta pouco comum «Sonn» (sol), moldura de bronze, paisagem de verão"'}),
    ('alt="Carte rare « Brëll » (lunettes), cadre d\'argent, ville au soir d\'automne"', {
        "en": 'alt="Rare card “Brëll” (glasses), silver frame, town on an autumn evening"',
        "de": 'alt="Seltene Karte „Brëll“ (Brille), Silberrahmen, Stadt an einem Herbstabend"',
        "pt": 'alt="Carta rara «Brëll» (óculos), moldura de prata, cidade numa tarde de outono"'}),
    ('alt="Carte très rare « Kran » (grue), cadre d\'or, nuit d\'hiver étoilée"', {
        "en": 'alt="Very rare card “Kran” (crane), gold frame, starry winter night"',
        "de": 'alt="Sehr seltene Karte „Kran“ (Kran), Goldrahmen, sternklare Winternacht"',
        "pt": 'alt="Carta muito rara «Kran» (grua), moldura de ouro, noite de inverno estrelada"'}),
    ("Des cartes pour découvrir le luxembourgeois : chaque mot gagné en jouant devient une carte, avec son sens, une phrase du dictionnaire officiel et un paysage du pays. Plus le mot est rare, plus le métal est noble, de l'étain à l'or.", {
        "en": "Cards to discover Luxembourgish: every word you win while playing becomes a card, with its meaning, a sentence from the official dictionary and a landscape of the country. The rarer the word, the nobler the metal, from pewter to gold.",
        "de": "Karten, um Luxemburgisch zu entdecken: Jedes beim Spielen gewonnene Wort wird zu einer Karte, mit seiner Bedeutung, einem Satz aus dem offiziellen Wörterbuch und einer Landschaft des Landes. Je seltener das Wort, desto edler das Metall, von Zinn bis Gold.",
        "pt": "Cartas para descobrir o luxemburguês: cada palavra ganha a jogar torna-se uma carta, com o seu significado, uma frase do dicionário oficial e uma paisagem do país. Quanto mais rara a palavra, mais nobre o metal, do estanho ao ouro."}),

    # ---- Carnet ----
    ('<span class="version">Le carnet</span>', {
        "en": '<span class="version">The notebook</span>',
        "de": '<span class="version">Das Heft</span>',
        "pt": '<span class="version">O caderno</span>'}),
    ("<h2>Des cartes à collectionner et à offrir</h2>", {
        "en": "<h2>Cards to collect and to give away</h2>",
        "de": "<h2>Karten zum Sammeln und Verschenken</h2>",
        "pt": "<h2>Cartas para colecionar e oferecer</h2>"}),
    ("<p>Vos cartes se rangent dans un carnet, qui est devenu un vrai album.</p>", {
        "en": "<p>Your cards go into a notebook, which has become a real album.</p>",
        "de": "<p>Ihre Karten kommen in ein Heft, das zu einem echten Album geworden ist.</p>",
        "pt": "<p>As suas cartas guardam-se num caderno, que se tornou um verdadeiro álbum.</p>"}),
    ("<li><strong>Des séries à compléter</strong> : les nombres, les dessins, les jeux, les paysages. Une case vide donne un indice, jamais la réponse.</li>", {
        "en": "<li><strong>Series to complete</strong>: numbers, drawings, games, landscapes. An empty slot gives a hint, never the answer.</li>",
        "de": "<li><strong>Serien zum Vervollständigen</strong>: Zahlen, Zeichnungen, Spiele, Landschaften. Ein leeres Feld gibt einen Hinweis, nie die Antwort.</li>",
        "pt": "<li><strong>Séries para completar</strong>: os números, os desenhos, os jogos, as paisagens. Um espaço vazio dá uma pista, nunca a resposta.</li>"}),
    ("<li><strong>Offrir une carte de la main à la main</strong> : les deux téléphones côte à côte, un code à scanner, et la carte passe d'un carnet à l'autre. Elle quitte le vôtre, comme une vraie carte qu'on tend.</li>", {
        "en": "<li><strong>Give a card away, hand to hand</strong>: the two phones side by side, a code to scan, and the card moves from one notebook to the other. It leaves yours, like a real card you hand over.</li>",
        "de": "<li><strong>Eine Karte von Hand zu Hand verschenken</strong>: die beiden Telefone nebeneinander, ein Code zum Scannen, und die Karte wechselt von einem Heft ins andere. Sie verlässt Ihres, wie eine echte Karte, die man überreicht.</li>",
        "pt": "<li><strong>Oferecer uma carta de mão em mão</strong>: os dois telemóveis lado a lado, um código para ler, e a carta passa de um caderno para o outro. Sai do seu, como uma carta verdadeira que se entrega.</li>"}),
    ("<li><strong>Partager une carte en image</strong>, avec le lien pour installer le clavier.</li>", {
        "en": "<li><strong>Share a card as a picture</strong>, with the link to install the keyboard.</li>",
        "de": "<li><strong>Eine Karte als Bild teilen</strong>, mit dem Link zur Installation der Tastatur.</li>",
        "pt": "<li><strong>Partilhar uma carta em imagem</strong>, com a ligação para instalar o teclado.</li>"}),
    ("<li><strong>Un sceau sur les mots appris</strong>, et une petite plume sur ceux que vous avez tapés vous-même dans un vrai message.</li>", {
        "en": "<li><strong>A seal on the words you have learned</strong>, and a small quill on those you typed yourself in a real message.</li>",
        "de": "<li><strong>Ein Siegel auf gelernten Wörtern</strong>, und eine kleine Feder auf denen, die Sie selbst in einer echten Nachricht getippt haben.</li>",
        "pt": "<li><strong>Um selo nas palavras aprendidas</strong>, e uma pequena pena nas que escreveu numa mensagem verdadeira.</li>"}),

    # ---- Tablettes ----
    ('<span class="version">Tablettes</span>', {
        "en": '<span class="version">Tablets</span>', "de": '<span class="version">Tablets</span>', "pt": '<span class="version">Tablets</span>'}),
    ("<h2>Le clavier à la taille d'une tablette</h2>", {
        "en": "<h2>A keyboard sized for tablets</h2>",
        "de": "<h2>Die Tastatur in Tablet-Größe</h2>",
        "pt": "<h2>O teclado à medida de um tablet</h2>"}),
    ("<li>Des touches plus hautes, et un clavier scindé en deux en paysage, à portée des pouces.</li>", {
        "en": "<li>Taller keys, and in landscape a keyboard split in two, within reach of your thumbs.</li>",
        "de": "<li>Höhere Tasten und im Querformat eine geteilte Tastatur, in Reichweite der Daumen.</li>",
        "pt": "<li>Teclas mais altas e, na horizontal, um teclado dividido em dois, ao alcance dos polegares.</li>"}),
    ("<li>Avec un clavier Bluetooth ou un étui-clavier, seule la barre de suggestions reste à l'écran.</li>", {
        "en": "<li>With a Bluetooth keyboard or a keyboard case, only the suggestion bar stays on screen.</li>",
        "de": "<li>Mit einer Bluetooth-Tastatur oder einer Tastaturhülle bleibt nur die Vorschlagsleiste auf dem Bildschirm.</li>",
        "pt": "<li>Com um teclado Bluetooth ou uma capa com teclado, só a barra de sugestões fica no ecrã.</li>"}),
    ("<li>Les jeux à grille et le dictionnaire s'ouvrent côte à côte : la grille d'un côté, les mots de l'autre.</li>", {
        "en": "<li>The grid games and the dictionary open side by side: the grid on one side, the words on the other.</li>",
        "de": "<li>Die Gitterspiele und das Wörterbuch öffnen sich nebeneinander: das Gitter auf der einen Seite, die Wörter auf der anderen.</li>",
        "pt": "<li>Os jogos de grelha e o dicionário abrem lado a lado: a grelha de um lado, as palavras do outro.</li>"}),
    ("<li>Tourner l'écran ne fait plus perdre la partie.</li>", {
        "en": "<li>Rotating the screen no longer loses your game.</li>",
        "de": "<li>Beim Drehen des Bildschirms geht das Spiel nicht mehr verloren.</li>",
        "pt": "<li>Rodar o ecrã já não faz perder o jogo.</li>"}),
    ('alt="Tablette en paysage, le clavier scindé en deux blocs aux bords de l\'écran"', {
        "en": 'alt="Tablet in landscape, the keyboard split into two blocks at the edges of the screen"',
        "de": 'alt="Tablet im Querformat, die Tastatur in zwei Blöcke an den Bildschirmrändern geteilt"',
        "pt": 'alt="Tablet na horizontal, o teclado dividido em dois blocos nas bordas do ecrã"'}),
    ("<figcaption>Sur tablette en paysage, le clavier se partage entre les deux pouces.</figcaption>", {
        "en": "<figcaption>On a tablet in landscape, the keyboard is shared between both thumbs.</figcaption>",
        "de": "<figcaption>Auf dem Tablet im Querformat teilt sich die Tastatur zwischen beiden Daumen auf.</figcaption>",
        "pt": "<figcaption>Num tablet na horizontal, o teclado divide-se entre os dois polegares.</figcaption>"}),

    # ---- Zuelwuert ----
    ("<h2>Tous les nombres de 0 à 100</h2>", {
        "en": "<h2>Every number from 0 to 100</h2>",
        "de": "<h2>Alle Zahlen von 0 bis 100</h2>",
        "pt": "<h2>Todos os números de 0 a 100</h2>"}),
    ("Le jeu des nombres couvre désormais toute la centaine. En Facile, il montre le nombre seul ; ensuite, une multiplication ou une addition. Vous apprenez à écrire « sechsafofzeg » et « sechsandrësseg », et à savoir pourquoi l'un perd son n.", {
        "en": "The numbers game now covers the whole hundred. In Easy, it shows the number alone; after that, a multiplication or an addition. You learn to write “sechsafofzeg” and “sechsandrësseg”, and why one of them loses its n.",
        "de": "Das Zahlenspiel deckt jetzt das ganze Hundert ab. In „Leicht“ zeigt es nur die Zahl, danach eine Multiplikation oder eine Addition. Sie lernen, „sechsafofzeg“ und „sechsandrësseg“ zu schreiben, und warum das eine sein n verliert.",
        "pt": "O jogo dos números cobre agora toda a centena. No nível Fácil, mostra só o número; depois, uma multiplicação ou uma adição. Aprende a escrever «sechsafofzeg» e «sechsandrësseg», e porque é que uma delas perde o n."}),

    # ---- Installation ----
    ('<span class="version">Installation</span>', {
        "en": '<span class="version">Setup</span>', "de": '<span class="version">Installation</span>', "pt": '<span class="version">Instalação</span>'}),
    ("<h2>Prêt en moins d'étapes</h2>", {
        "en": "<h2>Ready in fewer steps</h2>",
        "de": "<h2>Schneller startklar</h2>",
        "pt": "<h2>Pronto em menos passos</h2>"}),
    ("Le bouton d'activation ouvre directement le bon écran d'Android, l'appli vous dit quoi répondre à son avertissement, et le premier mot que vous tapez suffit à tout valider.", {
        "en": "The activation button opens the right Android screen directly, the app tells you how to answer Android's warning, and the first word you type is enough to confirm everything.",
        "de": "Die Aktivierungstaste öffnet direkt den richtigen Android-Bildschirm, die App sagt Ihnen, wie Sie auf die Warnung antworten, und das erste getippte Wort genügt, um alles zu bestätigen.",
        "pt": "O botão de ativação abre diretamente o ecrã certo do Android, a app diz-lhe o que responder ao aviso, e a primeira palavra que escreve basta para validar tudo."}),

    # ---- Plaisir des yeux ----
    ("<h2>Et pour le plaisir des yeux</h2>", {
        "en": "<h2>And a treat for the eyes</h2>",
        "de": "<h2>Und etwas fürs Auge</h2>",
        "pt": "<h2>E um prazer para os olhos</h2>"}),
    ("Chaque carte ouvre une fenêtre sur un lieu du pays, de la Rout Bréck à la Musel. Elle se souvient de la saison où vous l'avez gagnée : neige en hiver, feuilles en automne, feux d'artifice à la Fête nationale. Penchez le téléphone : le paysage prend du relief et la lumière court sur le cadre, de l'étain des cartes communes à l'or des très rares.", {
        "en": "Each card opens a window onto a place in the country, from the Rout Bréck to the Musel. It remembers the season you won it in: snow in winter, leaves in autumn, fireworks on National Day. Tilt the phone: the landscape gains depth and light runs across the frame, from the pewter of common cards to the gold of very rare ones.",
        "de": "Jede Karte öffnet ein Fenster auf einen Ort des Landes, von der Rout Bréck bis zur Musel. Sie erinnert sich an die Jahreszeit, in der Sie sie gewonnen haben: Schnee im Winter, Blätter im Herbst, Feuerwerk am Nationalfeiertag. Neigen Sie das Telefon: Die Landschaft gewinnt an Tiefe, und das Licht wandert über den Rahmen, vom Zinn der häufigen Karten bis zum Gold der sehr seltenen.",
        "pt": "Cada carta abre uma janela sobre um lugar do país, da Rout Bréck à Musel. Lembra-se da estação em que a ganhou: neve no inverno, folhas no outono, fogo de artifício no Dia Nacional. Incline o telemóvel: a paisagem ganha relevo e a luz corre pela moldura, do estanho das cartas comuns ao ouro das muito raras."}),
    ('alt="Deux cartes : « Euro » sous les fanions de la Fête nationale, « jour » dans un paysage d\'automne"', {
        "en": 'alt="Two cards: “Euro” under National Day bunting, “jour” in an autumn landscape"',
        "de": 'alt="Zwei Karten: „Euro“ unter den Wimpeln des Nationalfeiertags, „jour“ in einer Herbstlandschaft"',
        "pt": 'alt="Duas cartas: «Euro» sob as bandeirolas do Dia Nacional, «jour» numa paisagem de outono"'}),
    ("<figcaption>Une carte gagnée à la Fête nationale garde ses fanions, une carte d'automne ses feuilles rousses.</figcaption>", {
        "en": "<figcaption>A card won on National Day keeps its bunting, an autumn card its russet leaves.</figcaption>",
        "de": "<figcaption>Eine am Nationalfeiertag gewonnene Karte behält ihre Wimpel, eine Herbstkarte ihre rostroten Blätter.</figcaption>",
        "pt": "<figcaption>Uma carta ganha no Dia Nacional guarda as suas bandeirolas, uma carta de outono as suas folhas ruivas.</figcaption>"}),

    # ---- Appel et pied ----
    ("Vous le parlez. Maintenant, vous l'écrivez.", {
        "en": "You speak it. Now you write it.",
        "de": "Sie sprechen es. Jetzt schreiben Sie es.",
        "pt": "Fala-o. Agora, escreve-o."}),
    ('href="https://play.google.com/store/apps/details?id=com.potomitan.luxkeyboard">Mettre à jour sur Google Play</a>', {
        "en": 'href="https://play.google.com/store/apps/details?id=com.potomitan.luxkeyboard&amp;hl=en">Update on Google Play</a>',
        "de": 'href="https://play.google.com/store/apps/details?id=com.potomitan.luxkeyboard&amp;hl=de">Bei Google Play aktualisieren</a>',
        "pt": 'href="https://play.google.com/store/apps/details?id=com.potomitan.luxkeyboard&amp;hl=pt">Atualizar no Google Play</a>'}),
    ("<p>Lëtzebuergesch Clavier, gratuit sur Android, pour les 16 ans et plus. Version 37.5.0.</p>", {
        "en": "<p>Lëtzebuergesch Clavier, free on Android, for ages 16 and up. Version 37.5.0.</p>",
        "de": "<p>Lëtzebuergesch Clavier, kostenlos für Android, ab 16 Jahren. Version 37.5.0.</p>",
        "pt": "<p>Lëtzebuergesch Clavier, gratuito para Android, a partir dos 16 anos. Versão 37.5.0.</p>"}),
    ("<p>Une idée, un mot qui manque ? <a href=\"https://famibelle.github.io/LuxKeyb/feedbacks_form.html\">Écrivez-nous</a>. Essayez le clavier sans l'installer avec le <a href=\"https://famibelle.github.io/LuxKeyb/simulateur.html\">simulateur</a>.</p>", {
        "en": "<p>An idea, a missing word? <a href=\"https://famibelle.github.io/LuxKeyb/feedbacks_form.html\">Write to us</a>. Try the keyboard without installing it in the <a href=\"https://famibelle.github.io/LuxKeyb/simulateur.html\">simulator</a>.</p>",
        "de": "<p>Eine Idee, ein fehlendes Wort? <a href=\"https://famibelle.github.io/LuxKeyb/feedbacks_form.html\">Schreiben Sie uns</a>. Probieren Sie die Tastatur ohne Installation im <a href=\"https://famibelle.github.io/LuxKeyb/simulateur.html\">Simulator</a> aus.</p>",
        "pt": "<p>Uma ideia, uma palavra em falta? <a href=\"https://famibelle.github.io/LuxKeyb/feedbacks_form.html\">Escreva-nos</a>. Experimente o teclado sem o instalar no <a href=\"https://famibelle.github.io/LuxKeyb/simulateur.html\">simulador</a>.</p>"}),
    ('<p><a href="./">Toutes les newsletters</a></p>', {
        "en": '<p><a href="./">All newsletters</a></p>',
        "de": '<p><a href="./">Alle Newsletter</a></p>',
        "pt": '<p><a href="./">Todas as newsletters</a></p>'}),
]


def main():
    source = SOURCE.read_text(encoding="utf-8")
    manquants = [fr for fr, _ in T if fr not in source]
    if manquants:
        raise SystemExit("Fragments introuvables dans la page française :\n  " + "\n  ".join(m[:90] for m in manquants))
    for langue in LANGUES:
        sortie = source
        for fr, trad in T:
            sortie = sortie.replace(fr, trad[langue])
        (NL / f"2026-10-{langue}.html").write_text(sortie, encoding="utf-8")
        print(f"2026-10-{langue}.html")


if __name__ == "__main__":
    sys.exit(main())
