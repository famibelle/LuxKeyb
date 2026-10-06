#!/usr/bin/env python3
"""Décline les supports imprimables (tract, affiche, triptyque) dans les langues
de l'interface du clavier : lb, de, en, pt.

Les pages françaises restent la source. Chaque traduction est une table de
fragments HTML exacts, français → langue ; le script les remplace dans la page
source et écrit `<support>-<langue>.html` à côté. Il refuse d'écrire une page
dès qu'un fragment de la table manque dans sa source (la page française a
bougé, la table est périmée) ou qu'un texte français visible survit dans la
traduction (la page française a gagné une phrase que la table ignore). On
modifie donc d'abord la page française, puis la table, puis on relance :

    python docs/scripts/traduire_supports.py

Les langues de A_VALIDER portent un bandeau « À faire valider » imprimé en
travers de chaque page, tant qu'un locuteur natif n'a pas relu le texte. Le
retirer d'une langue, c'est retirer son code de cet ensemble.
"""
import html
import re
import sys
from html.parser import HTMLParser
from pathlib import Path

DOCS = Path(__file__).resolve().parent.parent
SUPPORTS = ["tract", "tract-eco", "affiche", "affiche-eco", "triptyque", "triptyque-eco"]
LANGUES = ["lb", "de", "en", "pt"]
A_VALIDER = {"lb", "de", "pt"}
# Google ne publie pas de badge en luxembourgeois : la fiche luxembourgeoise
# garde le badge français, la langue du Play Store la plus courante au pays.
BADGE = {"lb": "fr", "de": "de", "en": "en", "pt": "pt"}

# Captures refaites sur l'émulateur, système et appli dans la langue du
# support (le carnet lit la langue du système, voir le compte rendu du
# 2026-10-04) ; lux_onboarding_<langue> reprend la capture « tout est prêt »
# du guide de l'appli (res/drawable-<langue>-nodpi/guide_screenshot_install_done.png).
CAPTURES_TRADUITES = ["lux_carte_stad", "lux_wierderbuch_gromperekichelchen", "lux_wuertsich_trouves", "lux_onboarding"]

BANDEAU = {
    "lb": "Nach net vun engem Mammesproochler nogekuckt",
    "de": "Noch nicht von Muttersprachlern geprüft",
    "pt": "Ainda não revisto por um falante nativo",
}

# (fragment français exact, {langue: traduction})
T = [
    # ---- En-têtes des pages web (hors impression) ----
    ("Tract à imprimer · Lëtzebuergesch Clavier", {
        "lb": "Fluchblat fir ze drécken · Lëtzebuergesch Clavier",
        "de": "Flyer zum Ausdrucken · Lëtzebuergesch Clavier",
        "en": "Printable flyer · Lëtzebuergesch Clavier",
        "pt": "Folheto para imprimir · Lëtzebuergesch Clavier"}),
    ("Tract éco encre à imprimer · Lëtzebuergesch Clavier", {
        "lb": "Fluchblat, Spuerversioun · Lëtzebuergesch Clavier",
        "de": "Flyer, tintensparende Version · Lëtzebuergesch Clavier",
        "en": "Flyer, ink-saving version · Lëtzebuergesch Clavier",
        "pt": "Folheto, versão económica · Lëtzebuergesch Clavier"}),
    ("Affiche à imprimer · Lëtzebuergesch Clavier", {
        "lb": "Plakat fir ze drécken · Lëtzebuergesch Clavier",
        "de": "Plakat zum Ausdrucken · Lëtzebuergesch Clavier",
        "en": "Printable poster · Lëtzebuergesch Clavier",
        "pt": "Cartaz para imprimir · Lëtzebuergesch Clavier"}),
    ("Affiche éco encre à imprimer · Lëtzebuergesch Clavier", {
        "lb": "Plakat, Spuerversioun · Lëtzebuergesch Clavier",
        "de": "Plakat, tintensparende Version · Lëtzebuergesch Clavier",
        "en": "Poster, ink-saving version · Lëtzebuergesch Clavier",
        "pt": "Cartaz, versão económica · Lëtzebuergesch Clavier"}),
    ("Triptyque à imprimer · Lëtzebuergesch Clavier", {
        "lb": "Faltblat fir ze drécken · Lëtzebuergesch Clavier",
        "de": "Faltblatt zum Ausdrucken · Lëtzebuergesch Clavier",
        "en": "Printable leaflet · Lëtzebuergesch Clavier",
        "pt": "Desdobrável para imprimir · Lëtzebuergesch Clavier"}),
    ("Triptyque éco encre à imprimer · Lëtzebuergesch Clavier", {
        "lb": "Faltblat, Spuerversioun · Lëtzebuergesch Clavier",
        "de": "Faltblatt, tintensparende Version · Lëtzebuergesch Clavier",
        "en": "Leaflet, ink-saving version · Lëtzebuergesch Clavier",
        "pt": "Desdobrável, versão económica · Lëtzebuergesch Clavier"}),
    ("Tract A5 prêt à imprimer (2 par feuille A4) pour faire connaître le clavier luxembourgeois : à déposer au comptoir d'une boulangerie, d'un café, d'une maison des jeunes. QR code vers la fiche Google Play.", {
        "lb": "A5-Fluchblat fir ze drécken (2 pro A4-Blat), fir d'lëtzebuergesch Tastatur bekannt ze maachen. QR-Code op d'Säit am Google Play.",
        "de": "A5-Flyer zum Ausdrucken (2 pro A4-Blatt), um die luxemburgische Tastatur bekannt zu machen. QR-Code zur Google-Play-Seite.",
        "en": "Printable A5 flyer (2 per A4 sheet) to spread the word about the Luxembourgish keyboard. QR code to the Google Play page.",
        "pt": "Folheto A5 para imprimir (2 por folha A4) para dar a conhecer o teclado luxemburguês. Código QR para a página do Google Play."}),
    ("Version éco encre du tract A5 du clavier luxembourgeois : fond blanc, deux tracts par feuille A4, lisible en noir et blanc comme en couleur.", {
        "lb": "Spuerversioun vum A5-Fluchblat: wäisse Fong, zwee Fluchblieder pro A4-Blat, liesbar a schwaarz-wäiss wéi a Faarf.",
        "de": "Tintensparende Version des A5-Flyers: weißer Hintergrund, zwei Flyer pro A4-Blatt, schwarz-weiß wie farbig lesbar.",
        "en": "Ink-saving version of the A5 flyer: white background, two flyers per A4 sheet, readable in black and white or colour.",
        "pt": "Versão económica do folheto A5: fundo branco, dois folhetos por folha A4, legível a preto e branco ou a cores."}),
    ("Affiche A4 prête à imprimer pour faire connaître le clavier luxembourgeois : à afficher dans un hall d'association, une école, une maison des jeunes, une vitrine. QR code vers la fiche Google Play.", {
        "lb": "A4-Plakat fir ze drécken, fir d'lëtzebuergesch Tastatur bekannt ze maachen. QR-Code op d'Säit am Google Play.",
        "de": "A4-Plakat zum Ausdrucken, um die luxemburgische Tastatur bekannt zu machen. QR-Code zur Google-Play-Seite.",
        "en": "Printable A4 poster to spread the word about the Luxembourgish keyboard. QR code to the Google Play page.",
        "pt": "Cartaz A4 para imprimir para dar a conhecer o teclado luxemburguês. Código QR para a página do Google Play."}),
    ("Version éco encre de l'affiche A4 du clavier luxembourgeois : fond blanc, lisible en noir et blanc, pour une imprimante de bureau ordinaire.", {
        "lb": "Spuerversioun vum A4-Plakat: wäisse Fong, liesbar a schwaarz-wäiss, fir en normale Bürosdrécker.",
        "de": "Tintensparende Version des A4-Plakats: weißer Hintergrund, schwarz-weiß lesbar, für einen normalen Bürodrucker.",
        "en": "Ink-saving version of the A4 poster: white background, readable in black and white, for an ordinary office printer.",
        "pt": "Versão económica do cartaz A4: fundo branco, legível a preto e branco, para uma impressora de escritório comum."}),
    ("Dépliant trois volets A4 paysage, recto-verso, prêt à imprimer : présentation du clavier luxembourgeois, installation pas à pas, fonctionnalités et progression. Pour un stand ou un comptoir.", {
        "lb": "Faltblat mat dräi Deeler, A4 quer, béid Säiten: d'lëtzebuergesch Tastatur, d'Installatioun Schrëtt fir Schrëtt, d'Funktiounen an d'Progressioun.",
        "de": "Dreiteiliges Faltblatt, A4 quer, beidseitig: die luxemburgische Tastatur, Installation Schritt für Schritt, Funktionen und Fortschritt.",
        "en": "Three-panel A4 landscape leaflet, double-sided: the Luxembourgish keyboard, step-by-step installation, features and progress.",
        "pt": "Desdobrável de três painéis, A4 horizontal, frente e verso: o teclado luxemburguês, instalação passo a passo, funcionalidades e progressão."}),
    ("Version éco encre du dépliant trois volets du clavier luxembourgeois : fonds blancs, repères de pliage, lisible en noir et blanc.", {
        "lb": "Spuerversioun vum Faltblat: wäiss Fongen, Faltmarken, liesbar a schwaarz-wäiss.",
        "de": "Tintensparende Version des Faltblatts: weiße Flächen, Falzmarken, schwarz-weiß lesbar.",
        "en": "Ink-saving version of the leaflet: white panels, fold marks, readable in black and white.",
        "pt": "Versão económica do desdobrável: fundos brancos, marcas de dobragem, legível a preto e branco."}),
    ("Lëtzebuergesch Clavier · kit ambassadeur", {
        "lb": "Lëtzebuergesch Clavier · Ambassadeursmaterial",
        "de": "Lëtzebuergesch Clavier · Botschafter-Kit",
        "en": "Lëtzebuergesch Clavier · ambassador kit",
        "pt": "Lëtzebuergesch Clavier · kit de embaixador"}),
    ("Un tract à distribuer 🪧", {
        "lb": "E Fluchblat fir ze verdeelen 🪧", "de": "Ein Flyer zum Verteilen 🪧",
        "en": "A flyer to hand out 🪧", "pt": "Um folheto para distribuir 🪧"}),
    ("Le tract, version éco encre 💧", {
        "lb": "D'Fluchblat, Spuerversioun 💧", "de": "Der Flyer, tintensparend 💧",
        "en": "The flyer, ink-saving version 💧", "pt": "O folheto, versão económica 💧"}),
    ("Une affiche à imprimer 🎨", {
        "lb": "E Plakat fir ze drécken 🎨", "de": "Ein Plakat zum Ausdrucken 🎨",
        "en": "A poster to print 🎨", "pt": "Um cartaz para imprimir 🎨"}),
    ("L'affiche, version éco encre 💧", {
        "lb": "De Plakat, Spuerversioun 💧", "de": "Das Plakat, tintensparend 💧",
        "en": "The poster, ink-saving version 💧", "pt": "O cartaz, versão económica 💧"}),
    ("Un dépliant trois volets 📖", {
        "lb": "E Faltblat mat dräi Deeler 📖", "de": "Ein dreiteiliges Faltblatt 📖",
        "en": "A three-panel leaflet 📖", "pt": "Um desdobrável de três painéis 📖"}),
    ("Le triptyque, version éco encre 💧", {
        "lb": "D'Faltblat, Spuerversioun 💧", "de": "Das Faltblatt, tintensparend 💧",
        "en": "The leaflet, ink-saving version 💧", "pt": "O desdobrável, versão económica 💧"}),
    ("Deux tracts A5 par feuille A4, à découper le long des pointillés. À laisser au comptoir d'une boulangerie, d'un café, d'une maison des jeunes ou d'un cours du soir : le QR code ouvre la fiche Google Play, où le clavier s'installe en un geste.", {
        "lb": "Zwee A5-Fluchblieder pro A4-Blat, laanscht d'Punktlinn auszeschneiden. Fir op d'Comptoir vun enger Bäckerei, engem Café, engem Jugendhaus oder engem Owescours: de QR-Code mécht d'Säit am Google Play op, wou d'Tastatur sech mat engem Touch installéiert.",
        "de": "Zwei A5-Flyer pro A4-Blatt, entlang der gepunkteten Linie auszuschneiden. Für die Theke einer Bäckerei, eines Cafés, eines Jugendhauses oder eines Abendkurses: Der QR-Code öffnet die Google-Play-Seite, auf der sich die Tastatur mit einem Fingertipp installieren lässt.",
        "en": "Two A5 flyers per A4 sheet, to cut along the dotted line. Leave them on the counter of a bakery, a café, a youth centre or an evening class: the QR code opens the Google Play page, where the keyboard installs in one tap.",
        "pt": "Dois folhetos A5 por folha A4, a recortar pelo tracejado. Para deixar no balcão de uma padaria, de um café, de uma casa da juventude ou de um curso noturno: o código QR abre a página do Google Play, onde o teclado se instala com um toque."}),
    ("Le même tract, sur fond blanc. Il consomme une fraction de l'encre de la version en aplat, ne gondole pas la feuille et reste lisible photocopié en noir et blanc, le cas le plus fréquent quand une association ou une école reprend le tract à son compte.", {
        "lb": "Dat selwecht Fluchblat op wäissem Fong. Et brauch nëmmen e Bruchdeel vun der Tënt, d'Blat bleift flaach, an et ass och a schwaarz-wäiss kopéiert nach gutt ze liesen.",
        "de": "Derselbe Flyer auf weißem Grund. Er braucht nur einen Bruchteil der Tinte, das Blatt wellt sich nicht, und er bleibt auch schwarz-weiß kopiert gut lesbar.",
        "en": "The same flyer on a white background. It uses a fraction of the ink, the sheet stays flat, and it remains readable when photocopied in black and white.",
        "pt": "O mesmo folheto em fundo branco. Gasta uma fração da tinta, a folha não enruga e continua legível fotocopiada a preto e branco."}),
    ("Format A4, prête à afficher dans le hall d'une association, d'une maison des jeunes, d'un cours du soir, ou en vitrine. Le QR code ouvre la fiche Google Play, où le clavier s'installe en un geste.", {
        "lb": "A4-Format, fir an de Gank vun engem Veräin, engem Jugendhaus, engem Owescours oder an eng Vitrine. De QR-Code mécht d'Säit am Google Play op, wou d'Tastatur sech mat engem Touch installéiert.",
        "de": "A4-Format, zum Aufhängen im Flur eines Vereins, eines Jugendhauses, eines Abendkurses oder im Schaufenster. Der QR-Code öffnet die Google-Play-Seite, auf der sich die Tastatur mit einem Fingertipp installieren lässt.",
        "en": "A4 format, ready to put up in the hall of a club, a youth centre, an evening class or a shop window. The QR code opens the Google Play page, where the keyboard installs in one tap.",
        "pt": "Formato A4, para afixar no átrio de uma associação, de uma casa da juventude, de um curso noturno ou numa montra. O código QR abre a página do Google Play, onde o teclado se instala com um toque."}),
    ("La même affiche sans aplat de couleur : le drapeau se réduit à un bandeau et à des filets, le reste est du papier. Une imprimante de bureau la sort sans bavure, et elle reste lisible photocopiée en noir et blanc.", {
        "lb": "Dee selwechte Plakat ouni Faarfflächen: de Fändel gëtt e Bändchen an e puer Linnen. En normale Bürosdrécker kritt en ouni Flecken hin, an e bleift och a schwaarz-wäiss kopéiert liesbar.",
        "de": "Dasselbe Plakat ohne Farbflächen: Die Flagge schrumpft auf ein Band und ein paar Linien. Ein Bürodrucker druckt es ohne Schlieren, und es bleibt schwarz-weiß kopiert lesbar.",
        "en": "The same poster without solid colour: the flag shrinks to a band and a few lines. An office printer handles it cleanly, and it stays readable when photocopied in black and white.",
        "pt": "O mesmo cartaz sem manchas de cor: a bandeira reduz-se a uma faixa e a algumas linhas. Uma impressora de escritório imprime-o sem borrões, e continua legível fotocopiado a preto e branco."}),
    ("A4 paysage recto-verso, plié en trois. À poser sur un stand, un comptoir d'association ou une table de cours du soir : le premier volet donne envie, l'intérieur explique l'installation, les fonctions et la progression, le dos dit comment aider.", {
        "lb": "A4 quer, béid Säiten, an dräi gefalt. Fir op e Stand, e Veräinscomptoir oder en Dësch vun engem Owescours: den éischten Deel mécht Loscht, bannen ass d'Installatioun, d'Funktiounen an d'Progressioun erkläert, hannen steet, wéi ee kann hëllefen.",
        "de": "A4 quer, beidseitig, dreifach gefalzt. Für einen Stand, eine Vereinstheke oder den Tisch eines Abendkurses: Die Vorderseite macht neugierig, innen werden Installation, Funktionen und Fortschritt erklärt, die Rückseite sagt, wie man helfen kann.",
        "en": "A4 landscape, double-sided, folded in three. For a stand, a club counter or an evening-class table: the front panel draws people in, the inside explains installation, features and progress, the back says how to help.",
        "pt": "A4 horizontal, frente e verso, dobrado em três. Para um stand, um balcão de associação ou uma mesa de curso noturno: o primeiro painel desperta interesse, o interior explica a instalação, as funções e a progressão, o verso diz como ajudar."}),
    ("Le même dépliant sans aplat : les six volets sont sur papier, la couleur ne tient plus que les titres, les puces et les traits. Six panneaux d'aplat coûtent une cartouche à eux seuls : c'est la version à imprimer quand le tirage se compte en dizaines.", {
        "lb": "Dat selwecht Faltblat ouni Faarfflächen: d'Faarf bleift nëmmen bei den Titelen, de Punkten an de Linnen. Dat ass d'Versioun fir ze drécken, wann et ëm Dosende vun Exemplairen geet.",
        "de": "Dasselbe Faltblatt ohne Farbflächen: Farbe tragen nur noch Titel, Aufzählungszeichen und Linien. Das ist die Version für Auflagen von mehreren Dutzend.",
        "en": "The same leaflet without solid colour: only the titles, bullets and lines keep their colour. This is the version to print when the run is counted in dozens.",
        "pt": "O mesmo desdobrável sem manchas de cor: só os títulos, os marcadores e as linhas mantêm a cor. É a versão a imprimir quando a tiragem se conta às dezenas."}),
    ("📄 Télécharger le PDF (A4, 2 tracts A5)", {
        "lb": "📄 PDF eroflueden (A4, 2 Fluchblieder A5)", "de": "📄 PDF herunterladen (A4, 2 Flyer A5)",
        "en": "📄 Download the PDF (A4, 2 A5 flyers)", "pt": "📄 Descarregar o PDF (A4, 2 folhetos A5)"}),
    ("📄 Télécharger le PDF (A4, haute résolution)", {
        "lb": "📄 PDF eroflueden (A4, héich Opléisung)", "de": "📄 PDF herunterladen (A4, hohe Auflösung)",
        "en": "📄 Download the PDF (A4, high resolution)", "pt": "📄 Descarregar o PDF (A4, alta resolução)"}),
    ("📄 Télécharger le PDF (A4 paysage, recto-verso)", {
        "lb": "📄 PDF eroflueden (A4 quer, béid Säiten)", "de": "📄 PDF herunterladen (A4 quer, beidseitig)",
        "en": "📄 Download the PDF (A4 landscape, double-sided)", "pt": "📄 Descarregar o PDF (A4 horizontal, frente e verso)"}),
    ("🖨️ Imprimer directement", {
        "lb": "🖨️ Direkt drécken", "de": "🖨️ Direkt drucken", "en": "🖨️ Print now", "pt": "🖨️ Imprimir já"}),
    ("💧 Version éco encre (fond blanc)", {
        "lb": "💧 Spuerversioun (wäisse Fong)", "de": "💧 Tintensparende Version (weißer Grund)",
        "en": "💧 Ink-saving version (white background)", "pt": "💧 Versão económica (fundo branco)"}),
    ("🎨 Version en couleur (fond drapeau)", {
        "lb": "🎨 Faarfversioun (Fändel am Hannergrond)", "de": "🎨 Farbversion (Flaggenhintergrund)",
        "en": "🎨 Colour version (flag background)", "pt": "🎨 Versão a cores (fundo bandeira)"}),
    ("🎨 Version en couleur<", {
        "lb": "🎨 Faarfversioun<", "de": "🎨 Farbversion<", "en": "🎨 Colour version<", "pt": "🎨 Versão a cores<"}),
    ("← Retour à l'espace ambassadeurs", {
        "lb": "← Zréck bei d'Ambassadeuren (op Franséisch)", "de": "← Zurück zum Botschafterbereich (auf Französisch)",
        "en": "← Back to the ambassador area (in French)", "pt": "← Voltar ao espaço dos embaixadores (em francês)"}),
    ("🖨️ Imprimer le recto", {"lb": "🖨️ Virsäit drécken", "de": "🖨️ Vorderseite drucken", "en": "🖨️ Print the front", "pt": "🖨️ Imprimir a frente"}),
    ("🖨️ Imprimer le verso", {"lb": "🖨️ Récksäit drécken", "de": "🖨️ Rückseite drucken", "en": "🖨️ Print the back", "pt": "🖨️ Imprimir o verso"}),
    ("🖨️ Imprimer les deux faces", {"lb": "🖨️ Béid Säiten drécken", "de": "🖨️ Beide Seiten drucken", "en": "🖨️ Print both sides", "pt": "🖨️ Imprimir as duas faces"}),
    ("Astuce : dans les options d'impression, choisissez « Taille réelle » (100 %) et désactivez les en-têtes/pieds de page. Une imprimante de bureau laisse un liseré blanc autour de la feuille : c'est prévu, le QR code reste à l'écart des bords. Chaque feuille A4 donne deux tracts identiques : coupez le long des pointillés ✂ et laissez une petite pile près de la caisse.", {
        "lb": "Tipp: An den Drockoptiounen „Tatsächlech Gréisst“ (100 %) wielen an d'Kapp- a Fousszeilen ausschalten. E Bürosdrécker léisst e wäisse Rand ronderëm d'Blat: dat ass virgesinn, de QR-Code bleift wäit genuch vum Rand. All A4-Blat gëtt zwee identesch Fluchblieder: laanscht d'Punktlinn ✂ schneiden an e klenge Koup bei d'Keess leeën.",
        "de": "Tipp: In den Druckoptionen „Tatsächliche Größe“ (100 %) wählen und Kopf- und Fußzeilen ausschalten. Ein Bürodrucker lässt einen weißen Rand um das Blatt: Das ist eingeplant, der QR-Code hält Abstand zu den Rändern. Jedes A4-Blatt ergibt zwei identische Flyer: entlang der gepunkteten Linie ✂ schneiden und einen kleinen Stapel an die Kasse legen.",
        "en": "Tip: in the print options, choose “Actual size” (100 %) and turn off headers and footers. An office printer leaves a white border around the sheet: that is expected, the QR code stays clear of the edges. Each A4 sheet gives two identical flyers: cut along the dotted line ✂ and leave a small pile by the till.",
        "pt": "Dica: nas opções de impressão, escolha «Tamanho real» (100 %) e desative os cabeçalhos e rodapés. Uma impressora de escritório deixa uma margem branca à volta da folha: está previsto, o código QR fica afastado das margens. Cada folha A4 dá dois folhetos iguais: recorte pelo tracejado ✂ e deixe uma pequena pilha junto à caixa."}),
    ("Cette version se contente d'une imprimante de bureau ordinaire : pas d'aplat à couvrir, donc pas de bavure ni de feuille gondolée. Chaque feuille A4 donne deux tracts identiques : coupez le long des pointillés ✂.", {
        "lb": "Dës Versioun geet mat engem normale Bürosdrécker: keng Faarfflächen, also keng Flecken a keen ondulléiert Blat. All A4-Blat gëtt zwee identesch Fluchblieder: laanscht d'Punktlinn ✂ schneiden.",
        "de": "Diese Version kommt mit einem normalen Bürodrucker aus: keine Farbflächen, also keine Schlieren und kein welliges Blatt. Jedes A4-Blatt ergibt zwei identische Flyer: entlang der gepunkteten Linie ✂ schneiden.",
        "en": "This version needs only an ordinary office printer: no solid areas, so no smudges and no curled sheet. Each A4 sheet gives two identical flyers: cut along the dotted line ✂.",
        "pt": "Esta versão basta-se com uma impressora de escritório comum: sem manchas de cor, portanto sem borrões nem folha enrugada. Cada folha A4 dá dois folhetos iguais: recorte pelo tracejado ✂."}),
    ("Astuce : dans les options d'impression, choisissez « Aucune marge » et désactivez les en-têtes/pieds de page pour un rendu plein cadre. L'affiche reste lisible réduite en A5 à 70 %, et le QR code garde sa correction d'erreur maximale : il se scanne encore photocopié de travers.", {
        "lb": "Tipp: An den Drockoptiounen „Keng Rand“ wielen an d'Kapp- a Fousszeilen ausschalten. De Plakat bleift och op A5 verklengert liesbar, an de QR-Code léisst sech och schief kopéiert nach scannen.",
        "de": "Tipp: In den Druckoptionen „Keine Ränder“ wählen und Kopf- und Fußzeilen ausschalten. Das Plakat bleibt auch auf A5 verkleinert lesbar, und der QR-Code lässt sich selbst schief kopiert noch scannen.",
        "en": "Tip: in the print options, choose “No margins” and turn off headers and footers. The poster stays readable shrunk to A5, and the QR code still scans even when photocopied askew.",
        "pt": "Dica: nas opções de impressão, escolha «Sem margens» e desative os cabeçalhos e rodapés. O cartaz continua legível reduzido a A5, e o código QR ainda se lê mesmo fotocopiado torto."}),
    ("Cette version tient sur une imprimante de bureau et sur du papier ordinaire. Le seul aplat conservé est le bandeau de titre : sans lui, l'affiche perdait ce qui la fait repérer de loin dans un hall.", {
        "lb": "Dës Versioun geet mat engem Bürosdrécker an normalem Pabeier. Déi eenzeg Faarffläch, déi bleift, ass den Titelband: Ouni hien gesäit een de Plakat net méi vu wäitem.",
        "de": "Diese Version kommt mit einem Bürodrucker und normalem Papier aus. Die einzige Farbfläche ist das Titelband: Ohne es würde man das Plakat nicht mehr von weitem sehen.",
        "en": "This version works on an office printer and ordinary paper. The only solid area kept is the title band: without it, the poster could no longer be spotted from across a hall.",
        "pt": "Esta versão basta-se com uma impressora de escritório e papel comum. A única mancha de cor mantida é a faixa do título: sem ela, o cartaz deixava de se ver ao longe."}),
    ("Impression : A4 <b>paysage</b>, « Aucune marge », en-têtes et pieds de page désactivés. Pour le recto-verso manuel, imprimez d'abord le recto, remettez la feuille en la retournant <b>par le bord court</b>, puis imprimez le verso : les deux rainures tomberont l'une sur l'autre. Pliage : rabattre le volet gauche vers l'intérieur, puis le droit par-dessus.", {
        "lb": "Drécken: A4 <b>quer</b>, „Keng Rand“, Kapp- a Fousszeilen aus. Fir béid Säite mat der Hand: fir d'éischt d'Virsäit drécken, d'Blat <b>iwwer déi kuerz Säit</b> ëmdréinen an nees aleeën, dann d'Récksäit drécken. Falen: de lénken Deel no bannen, dann de rietsen driwwer.",
        "de": "Druck: A4 <b>quer</b>, „Keine Ränder“, Kopf- und Fußzeilen aus. Für manuellen Duplexdruck zuerst die Vorderseite drucken, das Blatt <b>über die kurze Kante</b> wenden und wieder einlegen, dann die Rückseite drucken. Falzen: das linke Feld nach innen, dann das rechte darüber.",
        "en": "Printing: A4 <b>landscape</b>, “No margins”, headers and footers off. For manual double-sided printing, print the front first, turn the sheet over <b>along the short edge</b> and reload it, then print the back. Folding: fold the left panel in, then the right one over it.",
        "pt": "Impressão: A4 <b>horizontal</b>, «Sem margens», cabeçalhos e rodapés desativados. Para frente e verso manual, imprima primeiro a frente, volte a pôr a folha virando-a <b>pelo lado curto</b> e imprima o verso. Dobragem: dobre o painel esquerdo para dentro e depois o direito por cima."}),
    ("Recto, panneaux 1 · 2 · 3 (le 3 est la couverture)", {
        "lb": "Virsäit, Deeler 1 · 2 · 3 (den 3 ass d'Couverture)", "de": "Vorderseite, Felder 1 · 2 · 3 (3 ist die Titelseite)",
        "en": "Front, panels 1 · 2 · 3 (3 is the cover)", "pt": "Frente, painéis 1 · 2 · 3 (o 3 é a capa)"}),
    ("Verso, panneaux 4 · 5 · 6 (lecture intérieure de gauche à droite)", {
        "lb": "Récksäit, Deeler 4 · 5 · 6 (bannen, vu lénks no riets)", "de": "Rückseite, Felder 4 · 5 · 6 (Innenseite, von links nach rechts)",
        "en": "Back, panels 4 · 5 · 6 (inside, left to right)", "pt": "Verso, painéis 4 · 5 · 6 (interior, da esquerda para a direita)"}),

    # ---- Textes alternatifs ----
    ('alt="Une carte du carnet : le mot Stad, son sens et une phrase du dictionnaire officiel"', {
        "lb": 'alt="Eng Kaart aus dem Carnet: d\'Wuert Stad, seng Bedeitung an e Saz aus dem offiziellen Dictionnaire"',
        "de": 'alt="Eine Karte aus dem Kartenheft: das Wort Stad, seine Bedeutung und ein Satz aus dem offiziellen Wörterbuch"',
        "en": 'alt="A card from the deck: the word Stad, its meaning and a sentence from the official dictionary"',
        "pt": 'alt="Um cartão do caderno: a palavra Stad, o seu significado e uma frase do dicionário oficial"'}),
    ('alt="Le jeu Wuertsich : trois mots trouvés dans la grille, chacun avec sa traduction"', {
        "lb": 'alt="D\'Spill Wuertsich: dräi Wierder am Gitter fonnt, all mat senger Iwwersetzung"',
        "de": 'alt="Das Spiel Wuertsich: drei gefundene Wörter im Gitter, jedes mit seiner Übersetzung"',
        "en": 'alt="The Wuertsich game: three words found in the grid, each with its translation"',
        "pt": 'alt="O jogo Wuertsich: três palavras encontradas na grelha, cada uma com a sua tradução"'}),
    ('alt="La fiche Gromperekichelchen du dictionnaire : galette de pommes de terre, une phrase d\'exemple et ses autres formes"', {
        "lb": 'alt="D\'Fiche Gromperekichelchen am Dictionnaire: galette de pommes de terre, e Beispillsaz an déi aner Formen"',
        "de": 'alt="Der Wörterbucheintrag Gromperekichelchen: Kartoffelpuffer, ein Beispielsatz und die anderen Formen"',
        "en": 'alt="The dictionary entry for Gromperekichelchen: potato fritter, an example sentence and its other forms"',
        "pt": 'alt="A entrada Gromperekichelchen do dicionário: panqueca de batata ralada, uma frase de exemplo e as outras formas"'}),
    ('alt="Le clavier pendant la frappe : après « Ech hunn op der Schueb », la barre propose « Schueberfouer »"', {
        "lb": 'alt="D\'Tastatur beim Tippen: no „Ech hunn op der Schueb“ proposéiert d\'Leescht „Schueberfouer“"',
        "de": 'alt="Die Tastatur beim Tippen: nach „Ech hunn op der Schueb“ schlägt die Leiste „Schueberfouer“ vor"',
        "en": 'alt="The keyboard while typing: after “Ech hunn op der Schueb”, the bar suggests “Schueberfouer”"',
        "pt": 'alt="O teclado durante a escrita: depois de «Ech hunn op der Schueb», a barra sugere «Schueberfouer»"'}),
    ('alt="Appui long sur la touche e : le choix é, ë, è et ê s\'ouvre"', {
        "lb": 'alt="Laang op d\'Tast e drécken: é, ë, è an ê ginn op"',
        "de": 'alt="Langes Drücken auf die Taste e: é, ë, è und ê erscheinen"',
        "en": 'alt="Long press on the e key: é, ë, è and ê appear"',
        "pt": 'alt="Toque longo na tecla e: aparecem é, ë, è e ê"'}),
    ('alt="Le parcours d\'installation dans l\'application : les étapes cochées et le champ d\'essai du clavier"', {
        "lb": 'alt="D\'Installatioun an der App: déi dräi Schrëtt sinn ofgehaakt"',
        "de": 'alt="Die Einrichtung in der App: alle drei Schritte sind abgehakt"',
        "en": 'alt="Setup in the app: all three steps are ticked"',
        "pt": 'alt="A configuração na aplicação: os três passos estão assinalados"'}),
    ('alt="Disponible sur Google Play"', {
        "lb": 'alt="Disponible sur Google Play"', "de": 'alt="Jetzt bei Google Play"',
        "en": 'alt="Get it on Google Play"', "pt": 'alt="Disponível no Google Play"'}),
    ('alt="QR code vers la fiche Google Play du Lëtzebuergesch Clavier"', {
        "lb": 'alt="QR-Code op d\'Säit vum Lëtzebuergesch Clavier am Google Play"',
        "de": 'alt="QR-Code zur Google-Play-Seite von Lëtzebuergesch Clavier"',
        "en": 'alt="QR code to the Lëtzebuergesch Clavier Google Play page"',
        "pt": 'alt="Código QR para a página do Lëtzebuergesch Clavier no Google Play"'}),
    ('alt="Drapeau du Luxembourg"', {
        "lb": 'alt="Lëtzebuerger Fändel"', "de": 'alt="Flagge Luxemburgs"',
        "en": 'alt="Flag of Luxembourg"', "pt": 'alt="Bandeira do Luxemburgo"'}),

    # ---- Tract ----
    ("<li><span>Votre téléphone «&nbsp;corrige&nbsp;» votre Lëtzebuergesch en allemand ou en français&nbsp;?</span></li>", {
        "lb": "<li><span>Korrigéiert Ären Telefon Äert Lëtzebuergesch op Däitsch oder op Franséisch?</span></li>",
        "de": "<li><span>Ihr Handy „korrigiert“ Ihr Luxemburgisch ins Deutsche oder Französische?</span></li>",
        "en": "<li><span>Does your phone “correct” your Luxembourgish into German or French?</span></li>",
        "pt": "<li><span>O seu telemóvel «corrige» o seu luxemburguês para alemão ou francês?</span></li>"}),
    ("<li><span>Vous cherchez le ë et le ä dans un menu, à chaque mot&nbsp;?</span></li>", {
        "lb": "<li><span>Sicht Dir den ë an den ä an engem Menü, bei all Wuert?</span></li>",
        "de": "<li><span>Sie suchen ë und ä bei jedem Wort in einem Menü?</span></li>",
        "en": "<li><span>Hunting for ë and ä in a menu, word after word?</span></li>",
        "pt": "<li><span>Procura o ë e o ä num menu, palavra a palavra?</span></li>"}),
    ("<li><span>Vous savez le dire, mais pas l'écrire&nbsp;?</span></li>", {
        "lb": "<li><span>Dir kënnt et soen, mee net schreiwen?</span></li>",
        "de": "<li><span>Sie können es sprechen, aber nicht schreiben?</span></li>",
        "en": "<li><span>You can say it, but not write it?</span></li>",
        "pt": "<li><span>Sabe dizê-lo, mas não escrevê-lo?</span></li>"}),
    ('<p class="answer">Vous le parlez. Maintenant, vous l\'écrivez.</p>', {
        "lb": '<p class="answer">Dir schwätzt et. Elo schreift Dir et och.</p>',
        "de": '<p class="answer">Sie sprechen es. Jetzt schreiben Sie es auch.</p>',
        "en": '<p class="answer">You speak it. Now you can write it.</p>',
        "pt": '<p class="answer">Já o fala. Agora também o escreve.</p>'}),
    ('<div class="fact"><span class="num">Tapez «&nbsp;Schueb&nbsp;»</span><span class="txt">il propose «&nbsp;Schueberfouer&nbsp;»</span></div>', {
        "lb": '<div class="fact"><span class="num">Tippt „Schueb“</span><span class="txt">en proposéiert „Schueberfouer“</span></div>',
        "de": '<div class="fact"><span class="num">Tippen Sie „Schueb“</span><span class="txt">die Tastatur schlägt „Schueberfouer“ vor</span></div>',
        "en": '<div class="fact"><span class="num">Type “Schueb”</span><span class="txt">it suggests “Schueberfouer”</span></div>',
        "pt": '<div class="fact"><span class="num">Escreva «Schueb»</span><span class="txt">o teclado sugere «Schueberfouer»</span></div>'}),
    ('<span class="txt">chacun sa touche, plus besoin de les chercher</span>', {
        "lb": '<span class="txt">all Buschtaf huet seng Tast, Dir musst se net méi sichen</span>',
        "de": '<span class="txt">jeder Buchstabe hat seine Taste, kein Suchen mehr</span>',
        "en": '<span class="txt">each has its own key, no more hunting</span>',
        "pt": '<span class="txt">cada um tem a sua tecla, sem procurar</span>'}),
    ('<div class="fact"><span class="num">Parlez</span><span class="txt">touchez le micro, dites votre phrase, elle s\'écrit</span></div>', {
        "lb": '<div class="fact"><span class="num">Schwätzt</span><span class="txt">dréckt op de Mikro, sot Äre Saz, an e steet do</span></div>',
        "de": '<div class="fact"><span class="num">Sprechen</span><span class="txt">Mikro antippen, Satz sagen, schon steht er da</span></div>',
        "en": '<div class="fact"><span class="num">Speak</span><span class="txt">tap the mic, say your sentence, there it is</span></div>',
        "pt": '<div class="fact"><span class="num">Fale</span><span class="txt">toque no microfone, diga a sua frase, e ela fica escrita</span></div>'}),
    ('<div class="fact"><span class="num">Vous apprenez ?</span><span class="txt">7 jeux, un carnet de cartes et un dictionnaire vous attendent dans l\'appli</span></div>', {
        "lb": '<div class="fact"><span class="num">Dir léiert?</span><span class="txt">7 Spiller, e Kaartecarnet an en Dictionnaire waarden an der App op Iech</span></div>',
        "de": '<div class="fact"><span class="num">Sie lernen?</span><span class="txt">7 Spiele, ein Kartenheft und ein Wörterbuch warten in der App</span></div>',
        "en": '<div class="fact"><span class="num">Learning?</span><span class="txt">7 games, a card deck and a dictionary are waiting in the app</span></div>',
        "pt": '<div class="fact"><span class="num">Está a aprender?</span><span class="txt">7 jogos, um caderno de cartões e um dicionário esperam por si na app</span></div>'}),
    ("<span>Flashez, installez, écrivez</span>", {
        "lb": "<span>Scannen, installéieren, schreiwen</span>", "de": "<span>Scannen, installieren, schreiben</span>",
        "en": "<span>Scan, install, write</span>", "pt": "<span>Aponte, instale, escreva</span>"}),
    ('<p class="promises">Sans publicité · Sans compte<br>Ce que vous tapez reste chez vous</p>', {
        "lb": '<p class="promises">Ouni Reklamm · Ouni Kont<br>Wat Dir tippt, bleift bei Iech</p>',
        "de": '<p class="promises">Ohne Werbung · Ohne Konto<br>Was Sie tippen, bleibt bei Ihnen</p>',
        "en": '<p class="promises">No ads · No account<br>What you type stays with you</p>',
        "pt": '<p class="promises">Sem publicidade · Sem conta<br>O que escreve fica consigo</p>'}),
    ('<span class="big">100&nbsp;%<br>gratuit</span>', {
        "lb": '<span class="big">100&nbsp;%<br>gratis</span>', "de": '<span class="big">100&nbsp;%<br>gratis</span>',
        "en": '<span class="big">100&nbsp;%<br>free</span>', "pt": '<span class="big">100&nbsp;%<br>grátis</span>'}),
    ('<span class="small">zéro pub</span>', {
        "lb": '<span class="small">keng Reklamm</span>', "de": '<span class="small">null Werbung</span>',
        "en": '<span class="small">zero ads</span>', "pt": '<span class="small">zero anúncios</span>'}),

    # ---- Affiche ----
    ('<p class="subtitle">Vous le parlez.<br>Maintenant, vous l\'écrivez.</p>', {
        "lb": '<p class="subtitle">Dir schwätzt et.<br>Elo schreift Dir et och.</p>',
        "de": '<p class="subtitle">Sie sprechen es.<br>Jetzt schreiben Sie es auch.</p>',
        "en": '<p class="subtitle">You speak it.<br>Now you can write it.</p>',
        "pt": '<p class="subtitle">Já o fala.<br>Agora também o escreve.</p>'}),
    ("<li><b>Il vous souffle le mot.</b> Tapez «&nbsp;Schueb&nbsp;», il propose «&nbsp;Schueberfouer&nbsp;».</li>", {
        "lb": "<li><b>En hëlleft Iech mam Wuert.</b> Tippt „Schueb“, en proposéiert „Schueberfouer“.</li>",
        "de": "<li><b>Sie sagt Ihnen das Wort vor.</b> Tippen Sie „Schueb“, sie schlägt „Schueberfouer“ vor.</li>",
        "en": "<li><b>It whispers the word.</b> Type “Schueb”, it suggests “Schueberfouer”.</li>",
        "pt": "<li><b>Ele sopra-lhe a palavra.</b> Escreva «Schueb», ele sugere «Schueberfouer».</li>"}),
    ("<li><b>é, ë, ä, ö, ü sous le doigt.</b> Plus de menu d'accents à chaque mot.</li>", {
        "lb": "<li><b>é, ë, ä, ö, ü ënnert dem Fanger.</b> Kee Menü méi mat Akzenter bei all Wuert.</li>",
        "de": "<li><b>é, ë, ä, ö, ü direkt unter dem Finger.</b> Kein Akzentmenü mehr bei jedem Wort.</li>",
        "en": "<li><b>é, ë, ä, ö, ü at your fingertips.</b> No more accent menu for every word.</li>",
        "pt": "<li><b>é, ë, ä, ö, ü à mão.</b> Acabou o menu de acentos a cada palavra.</li>"}),
    ("<li><b>Les mots que vous tapez font avancer votre carnet.</b> Sept jeux pour en gagner d'autres.</li>", {
        "lb": "<li><b>Är Wierder fëllen Äre Carnet.</b> Siwe Spiller, fir der méi ze gewannen.</li>",
        "de": "<li><b>Die Wörter, die Sie tippen, füllen Ihr Kartenheft.</b> Sieben Spiele, um weitere zu gewinnen.</li>",
        "en": "<li><b>The words you type grow your card deck.</b> Seven games to win more.</li>",
        "pt": "<li><b>As palavras que escreve fazem avançar o seu caderno.</b> Sete jogos para ganhar outras.</li>"}),
    ("<li><b>Envie de parler&nbsp;?</b> Touchez le micro&nbsp;: il écrit ce que vous dites.</li>", {
        "lb": "<li><b>Léiwer schwätzen?</b> Dréckt op de Mikro: en schreift, wat Dir sot.</li>",
        "de": "<li><b>Lieber sprechen?</b> Tippen Sie aufs Mikro: Sie schreibt, was Sie sagen.</li>",
        "en": "<li><b>Rather speak?</b> Tap the mic: it writes what you say.</li>",
        "pt": "<li><b>Prefere falar?</b> Toque no microfone: ele escreve o que diz.</li>"}),
    ('<p class="promises">Sans publicité<span class="sep">·</span>Sans compte<span class="sep">·</span>Ce que vous tapez reste chez vous</p>', {
        "lb": '<p class="promises">Ouni Reklamm<span class="sep">·</span>Ouni Kont<span class="sep">·</span>Wat Dir tippt, bleift bei Iech</p>',
        "de": '<p class="promises">Ohne Werbung<span class="sep">·</span>Ohne Konto<span class="sep">·</span>Was Sie tippen, bleibt bei Ihnen</p>',
        "en": '<p class="promises">No ads<span class="sep">·</span>No account<span class="sep">·</span>What you type stays with you</p>',
        "pt": '<p class="promises">Sem publicidade<span class="sep">·</span>Sem conta<span class="sep">·</span>O que escreve fica consigo</p>'}),
    ('<p class="subtitle">📲 Flashez, installez, écrivez</p>', {
        "lb": '<p class="subtitle">📲 Scannen, installéieren, schreiwen</p>', "de": '<p class="subtitle">📲 Scannen, installieren, schreiben</p>',
        "en": '<p class="subtitle">📲 Scan, install, write</p>', "pt": '<p class="subtitle">📲 Aponte, instale, escreva</p>'}),
    ("<span>ou cherchez<br><b>Lëtzebuergesch Clavier</b></span>", {
        "lb": "<span>oder sicht<br><b>Lëtzebuergesch Clavier</b></span>", "de": "<span>oder suchen Sie<br><b>Lëtzebuergesch Clavier</b></span>",
        "en": "<span>or search for<br><b>Lëtzebuergesch Clavier</b></span>", "pt": "<span>ou procure<br><b>Lëtzebuergesch Clavier</b></span>"}),
    ("famibelle.github.io/LuxKeyb · Fait au Luxembourg", {
        "lb": "famibelle.github.io/LuxKeyb · Gemaach zu Lëtzebuerg", "de": "famibelle.github.io/LuxKeyb · Gemacht in Luxemburg",
        "en": "famibelle.github.io/LuxKeyb · Made in Luxembourg", "pt": "famibelle.github.io/LuxKeyb · Feito no Luxemburgo"}),

    # ---- Triptyque, volet 1 ----
    ("<p class=\"mission\">Le dictionnaire de ce clavier n'est pas une liste figée : il est reconstruit à chaque publication à partir de deux corpus publics : <b>LuxAlign</b>, 180 342 phrases de presse, et <b>LETZ</b>, les phrases d'exemple du <i>Lëtzebuerger Online Dictionnaire</i>. La langue qu'il propose est celle qui s'écrit, pas celle d'un dictionnaire arrêté une fois pour toutes.</p>", {
        "lb": "<p class=\"mission\">Den Dictionnaire vun dëser Tastatur ass keng fix Lëscht: e gëtt bei all Versioun nei opgebaut, aus zwee ëffentleche Corpussen: <b>LuxAlign</b>, 180 342 Sätz aus der Press, an <b>LETZ</b>, d'Beispillsätz vum <i>Lëtzebuerger Online Dictionnaire</i>. D'Sprooch, déi en proposéiert, ass déi, déi geschriwwe gëtt, net déi vun engem Dictionnaire, deen eemol fir ëmmer fäerdeg ass.</p>",
        "de": "<p class=\"mission\">Das Wörterbuch dieser Tastatur ist keine feste Liste: Es wird bei jeder Version neu aufgebaut, aus zwei öffentlichen Korpora: <b>LuxAlign</b>, 180 342 Sätze aus der Presse, und <b>LETZ</b>, die Beispielsätze des <i>Lëtzebuerger Online Dictionnaire</i>. Die Sprache, die es vorschlägt, ist die, die geschrieben wird, nicht die eines ein für alle Mal abgeschlossenen Wörterbuchs.</p>",
        "en": "<p class=\"mission\">This keyboard's dictionary is not a fixed list: it is rebuilt with every release from two public corpora: <b>LuxAlign</b>, 180,342 sentences from the press, and <b>LETZ</b>, the example sentences of the <i>Lëtzebuerger Online Dictionnaire</i>. The language it suggests is the one people write, not that of a dictionary frozen once and for all.</p>",
        "pt": "<p class=\"mission\">O dicionário deste teclado não é uma lista fixa: é reconstruído a cada versão a partir de dois corpora públicos: <b>LuxAlign</b>, 180 342 frases de imprensa, e <b>LETZ</b>, as frases de exemplo do <i>Lëtzebuerger Online Dictionnaire</i>. A língua que propõe é a que se escreve, não a de um dicionário fechado de uma vez por todas.</p>"}),
    ('<div class="quote">&laquo;&nbsp;Mir wëlle bleiwe wat mir sinn.&nbsp;&raquo;</div>', {
        "lb": '<div class="quote">„Mir wëlle bleiwe wat mir sinn.“</div>',
        "de": '<div class="quote">„Mir wëlle bleiwe wat mir sinn.“</div>',
        "en": '<div class="quote">“Mir wëlle bleiwe wat mir sinn.”</div>',
        "pt": '<div class="quote">«Mir wëlle bleiwe wat mir sinn.»</div>'}),
    ('<div class="qr-label">Flashez le code</div>', {
        "lb": '<div class="qr-label">Code scannen</div>', "de": '<div class="qr-label">Code scannen</div>',
        "en": '<div class="qr-label">Scan the code</div>', "pt": '<div class="qr-label">Leia o código</div>'}),
    ('<div class="help-title">Comment nous aider</div>', {
        "lb": '<div class="help-title">Wéi Dir eis hëllefe kënnt</div>', "de": '<div class="help-title">Wie Sie uns helfen können</div>',
        "en": '<div class="help-title">How to help us</div>', "pt": '<div class="help-title">Como nos ajudar</div>'}),
    ("<li>Notez-le sur Google Play</li>", {
        "lb": "<li>Bewäert en am Google Play</li>", "de": "<li>Bewerten Sie sie bei Google Play</li>",
        "en": "<li>Rate it on Google Play</li>", "pt": "<li>Avalie-o no Google Play</li>"}),
    ("<li>Parlez-en autour de vous</li>", {
        "lb": "<li>Schwätzt mat Äre Leit driwwer</li>", "de": "<li>Erzählen Sie davon weiter</li>",
        "en": "<li>Tell the people around you</li>", "pt": "<li>Fale dele a quem conhece</li>"}),
    ("<li>Signalez un mot qui manque</li>", {
        "lb": "<li>Mellt e Wuert, dat feelt</li>", "de": "<li>Melden Sie ein fehlendes Wort</li>",
        "en": "<li>Report a missing word</li>", "pt": "<li>Assinale uma palavra que falte</li>"}),
    ("<div><span>Kontakt</span> Formulaire de retours sur le site</div>", {
        "lb": "<div><span>Kontakt</span> Feedback-Formulaire op der Websäit</div>",
        "de": "<div><span>Kontakt</span> Feedback-Formular auf der Website</div>",
        "en": "<div><span>Contact</span> Feedback form on the website</div>",
        "pt": "<div><span>Contacto</span> Formulário de comentários no site</div>"}),
    ('<div class="promises">Gratuit · Sans publicité · Sans compte · Dictée vocale</div>', {
        "lb": '<div class="promises">Gratis · Ouni Reklamm · Ouni Kont · Diktat</div>',
        "de": '<div class="promises">Kostenlos · Ohne Werbung · Ohne Konto · Spracheingabe</div>',
        "en": '<div class="promises">Free · No ads · No account · Voice typing</div>',
        "pt": '<div class="promises">Grátis · Sem publicidade · Sem conta · Ditado por voz</div>'}),
    ("Lëtzebuergesch Clavier est un projet citoyen publié par Médhi Famibelle. Aucune collecte de données personnelles : ce que vous tapez reste sur l'appareil. Seule la dictée vocale, quand on touche le micro, envoie la voix au service LuxASR de l'Université du Luxembourg, qui la transcrit sans la conserver.", {
        "lb": "Lëtzebuergesch Clavier ass e Bierger-Projet, publizéiert vum Médhi Famibelle. Et gi keng perséinlech Donnéeë gesammelt: wat Dir tippt, bleift um Apparat. Nëmmen d'Diktat, wann een op de Mikro dréckt, schéckt d'Stëmm un de Service LuxASR vun der Universitéit Lëtzebuerg, deen se ëmschreift, ouni se ze späicheren.",
        "de": "Lëtzebuergesch Clavier ist ein Bürgerprojekt, veröffentlicht von Médhi Famibelle. Es werden keine personenbezogenen Daten erhoben: Was Sie tippen, bleibt auf dem Gerät. Nur die Spracheingabe schickt die Stimme, wenn man das Mikro antippt, an den Dienst LuxASR der Universität Luxemburg, der sie verschriftlicht, ohne sie zu speichern.",
        "en": "Lëtzebuergesch Clavier is a citizen project published by Médhi Famibelle. No personal data is collected: what you type stays on the device. Only voice typing, when you tap the mic, sends your voice to the University of Luxembourg's LuxASR service, which transcribes it without keeping it.",
        "pt": "O Lëtzebuergesch Clavier é um projeto cidadão publicado por Médhi Famibelle. Nenhum dado pessoal é recolhido: o que escreve fica no aparelho. Só o ditado por voz, quando se toca no microfone, envia a voz ao serviço LuxASR da Universidade do Luxemburgo, que a transcreve sem a guardar."}),

    # ---- Triptyque, volet 2 ----
    ('<div class="kicker">Pourquoi</div>', {
        "lb": '<div class="kicker">Firwat</div>', "de": '<div class="kicker">Warum</div>',
        "en": '<div class="kicker">Why</div>', "pt": '<div class="kicker">Porquê</div>'}),
    ("<h2>Pourquoi un clavier luxembourgeois&nbsp;?</h2>", {
        "lb": "<h2>Firwat eng lëtzebuergesch Tastatur?</h2>", "de": "<h2>Warum eine luxemburgische Tastatur?</h2>",
        "en": "<h2>Why a Luxembourgish keyboard?</h2>", "pt": "<h2>Porquê um teclado luxemburguês?</h2>"}),
    ("<li><span>Votre téléphone «&nbsp;corrige&nbsp;» votre Lëtzebuergesch en allemand ou en français.</span></li>", {
        "lb": "<li><span>Ären Telefon korrigéiert Äert Lëtzebuergesch op Däitsch oder op Franséisch.</span></li>",
        "de": "<li><span>Ihr Handy „korrigiert“ Ihr Luxemburgisch ins Deutsche oder Französische.</span></li>",
        "en": "<li><span>Your phone “corrects” your Luxembourgish into German or French.</span></li>",
        "pt": "<li><span>O seu telemóvel «corrige» o seu luxemburguês para alemão ou francês.</span></li>"}),
    ("<li><span>Le ë et le ä se cachent dans un menu d'accents, à chaque mot.</span></li>", {
        "lb": "<li><span>Den ë an den ä verstoppe sech an engem Menü, bei all Wuert.</span></li>",
        "de": "<li><span>ë und ä verstecken sich bei jedem Wort in einem Akzentmenü.</span></li>",
        "en": "<li><span>ë and ä hide in an accent menu, word after word.</span></li>",
        "pt": "<li><span>O ë e o ä escondem-se num menu de acentos, a cada palavra.</span></li>"}),
    ("<li><span>Vous hésitez sur l'orthographe à chaque message.</span></li>", {
        "lb": "<li><span>Dir sidd Iech bei all Message net sécher mat der Schreifweis.</span></li>",
        "de": "<li><span>Sie zögern bei jeder Nachricht bei der Rechtschreibung.</span></li>",
        "en": "<li><span>You doubt your spelling in every message.</span></li>",
        "pt": "<li><span>Hesita na ortografia a cada mensagem.</span></li>"}),
    ("<li><span>Vous savez le dire, mais pas l'écrire.</span></li>", {
        "lb": "<li><span>Dir kënnt et soen, mee net schreiwen.</span></li>",
        "de": "<li><span>Sie können es sprechen, aber nicht schreiben.</span></li>",
        "en": "<li><span>You can say it, but not write it.</span></li>",
        "pt": "<li><span>Sabe dizê-lo, mas não escrevê-lo.</span></li>"}),
    ("<li><span><b>Ce clavier est fait pour vous.</b></span></li>", {
        "lb": "<li><span><b>Dës Tastatur ass fir Iech gemaach.</b></span></li>",
        "de": "<li><span><b>Diese Tastatur ist für Sie gemacht.</b></span></li>",
        "en": "<li><span><b>This keyboard is made for you.</b></span></li>",
        "pt": "<li><span><b>Este teclado foi feito para si.</b></span></li>"}),
    ("À l'intérieur : les accents sous le doigt, la dictée, des mots qui ne sont plus soulignés en rouge, sept jeux avec leur carnet de cartes, et huit niveaux de progression. Retournez le dépliant.", {
        "lb": "Bannen: d'Akzenter ënnert dem Fanger, d'Diktat, Wierder, déi net méi rout ënnerstrach sinn, siwe Spiller mat hirem Kaartecarnet an aacht Niveauen. Dréit d'Faltblat ëm.",
        "de": "Innen: Akzente direkt unter dem Finger, die Spracheingabe, Wörter, die nicht mehr rot unterstrichen sind, sieben Spiele mit ihrem Kartenheft und acht Stufen. Blättern Sie um.",
        "en": "Inside: accents at your fingertips, voice typing, words no longer underlined in red, seven games with their card deck, and eight levels. Turn the leaflet over.",
        "pt": "No interior: os acentos à mão, o ditado, palavras que deixam de estar sublinhadas a vermelho, sete jogos com o seu caderno de cartões e oito níveis. Vire o desdobrável."}),

    # ---- Triptyque, couverture ----
    ('<h1 class="h">Vous le parlez.<br>Maintenant, vous l\'écrivez.</h1>', {
        "lb": '<h1 class="h">Dir schwätzt et.<br>Elo schreift Dir et och.</h1>',
        "de": '<h1 class="h">Sie sprechen es.<br>Jetzt schreiben Sie es auch.</h1>',
        "en": '<h1 class="h">You speak it.<br>Now you can write it.</h1>',
        "pt": '<h1 class="h">Já o fala.<br>Agora também o escreve.</h1>'}),
    ('<div class="press-card"><span class="src">Tapez «&nbsp;Schueb&nbsp;»</span><span class="txt">il propose «&nbsp;Schueberfouer&nbsp;»</span></div>', {
        "lb": '<div class="press-card"><span class="src">Tippt „Schueb“</span><span class="txt">en proposéiert „Schueberfouer“</span></div>',
        "de": '<div class="press-card"><span class="src">Tippen Sie „Schueb“</span><span class="txt">die Tastatur schlägt „Schueberfouer“ vor</span></div>',
        "en": '<div class="press-card"><span class="src">Type “Schueb”</span><span class="txt">it suggests “Schueberfouer”</span></div>',
        "pt": '<div class="press-card"><span class="src">Escreva «Schueb»</span><span class="txt">o teclado sugere «Schueberfouer»</span></div>'}),
    ('<div class="press-card"><span class="src">Parlez</span><span class="txt">touchez le micro, dites votre phrase, elle s\'écrit</span></div>', {
        "lb": '<div class="press-card"><span class="src">Schwätzt</span><span class="txt">dréckt op de Mikro, sot Äre Saz, an e steet do</span></div>',
        "de": '<div class="press-card"><span class="src">Sprechen</span><span class="txt">Mikro antippen, Satz sagen, schon steht er da</span></div>',
        "en": '<div class="press-card"><span class="src">Speak</span><span class="txt">tap the mic, say your sentence, there it is</span></div>',
        "pt": '<div class="press-card"><span class="src">Fale</span><span class="txt">toque no microfone, diga a sua frase, e ela fica escrita</span></div>'}),
    ('<div class="txt"><b>Flashez le code</b>', {
        "lb": '<div class="txt"><b>Code scannen</b>', "de": '<div class="txt"><b>Code scannen</b>',
        "en": '<div class="txt"><b>Scan the code</b>', "pt": '<div class="txt"><b>Leia o código</b>'}),
    ('<span class="free">100&nbsp;% gratuit</span>', {
        "lb": '<span class="free">100&nbsp;% gratis</span>', "de": '<span class="free">100&nbsp;% kostenlos</span>',
        "en": '<span class="free">100&nbsp;% free</span>', "pt": '<span class="free">100&nbsp;% grátis</span>'}),

    # ---- Triptyque, volet 4 : installation ----
    ('<div class="kicker">C\'est parti</div>', {
        "lb": '<div class="kicker">Lass geet et</div>', "de": '<div class="kicker">Los geht\'s</div>',
        "en": '<div class="kicker">Let\'s go</div>', "pt": '<div class="kicker">Vamos lá</div>'}),
    ("<h2>Installation en 4 étapes</h2>", {
        "lb": "<h2>Installatioun a 4 Schrëtt</h2>", "de": "<h2>Installation in 4 Schritten</h2>",
        "en": "<h2>Setup in 4 steps</h2>", "pt": "<h2>Instalação em 4 passos</h2>"}),
    ("<b>Ouvrez la fiche Google Play, c'est gratuit</b>", {
        "lb": "<b>Maacht d'Säit am Google Play op, et ass gratis</b>", "de": "<b>Öffnen Sie die Google-Play-Seite, es ist kostenlos</b>",
        "en": "<b>Open the Google Play page, it's free</b>", "pt": "<b>Abra a página do Google Play, é grátis</b>"}),
    ("<span class=\"dl-txt\"><b>Flashez ce code</b>Visez-le avec l'appareil photo : la fiche Google Play s'ouvre, il ne reste qu'à toucher Installer</span>", {
        "lb": "<span class=\"dl-txt\"><b>Scannt dëse Code</b>Mat der Kamera drop zillen: d'Säit am Google Play geet op, Dir musst nëmmen nach op Installéieren drécken</span>",
        "de": "<span class=\"dl-txt\"><b>Scannen Sie diesen Code</b>Mit der Kamera darauf zielen: Die Google-Play-Seite öffnet sich, Sie müssen nur noch auf Installieren tippen</span>",
        "en": "<span class=\"dl-txt\"><b>Scan this code</b>Point your camera at it: the Google Play page opens, all that's left is to tap Install</span>",
        "pt": "<span class=\"dl-txt\"><b>Leia este código</b>Aponte a câmara: a página do Google Play abre-se, só falta tocar em Instalar</span>"}),
    ("<li><b>Installez l'application</b>Depuis Google Play, comme n'importe quelle autre application : installation et mises à jour automatiques.</li>", {
        "lb": "<li><b>Installéiert d'App</b>Iwwer Google Play, wéi all aner App: Installatioun an Updates automatesch.</li>",
        "de": "<li><b>Installieren Sie die App</b>Über Google Play, wie jede andere App: Installation und Updates automatisch.</li>",
        "en": "<li><b>Install the app</b>From Google Play, like any other app: installation and updates are automatic.</li>",
        "pt": "<li><b>Instale a aplicação</b>Pelo Google Play, como qualquer outra aplicação: instalação e atualizações automáticas.</li>"}),
    ("<li><b>Activez le clavier dans les réglages</b>Un bouton de l'application mène droit au bon écran Android. Le système affiche alors un avertissement sur la saisie : il est générique, Android le montre pour <i>tout</i> clavier tiers, et celui-ci n'envoie rien de ce que vous tapez.</li>", {
        "lb": "<li><b>Aktivéiert d'Tastatur an den Astellungen</b>E Knäppchen an der App féiert direkt op de richtegen Android-Écran. De System weist dann eng Warnung iwwer d'Agab: déi ass allgemeng, Android weist se fir <i>all</i> Tastatur vun aneren Hiersteller, an dës hei schéckt näischt vun deem, wat Dir tippt.</li>",
        "de": "<li><b>Aktivieren Sie die Tastatur in den Einstellungen</b>Ein Knopf in der App führt direkt zum richtigen Android-Bildschirm. Das System zeigt dann eine Warnung zur Eingabe: Sie ist allgemein, Android zeigt sie bei <i>jeder</i> Drittanbieter-Tastatur, und diese hier sendet nichts von dem, was Sie tippen.</li>",
        "en": "<li><b>Turn the keyboard on in settings</b>A button in the app goes straight to the right Android screen. The system then shows a warning about typing: it is generic, Android shows it for <i>every</i> third-party keyboard, and this one sends nothing you type.</li>",
        "pt": "<li><b>Ative o teclado nas definições</b>Um botão da aplicação leva diretamente ao ecrã certo do Android. O sistema mostra então um aviso sobre a escrita: é genérico, o Android mostra-o para <i>qualquer</i> teclado de terceiros, e este não envia nada do que escreve.</li>"}),
    ("<li><b>Choisissez-le et écrivez</b>Le sélecteur de clavier s'ouvre : choisissez Lëtzebuergesch Clavier et tapez votre premier mot.</li>", {
        "lb": "<li><b>Wielt se a schreift</b>D'Auswiel vun den Tastaturen geet op: wielt Lëtzebuergesch Clavier a tippt Äert éischt Wuert.</li>",
        "de": "<li><b>Auswählen und schreiben</b>Die Tastaturauswahl öffnet sich: Wählen Sie Lëtzebuergesch Clavier und tippen Sie Ihr erstes Wort.</li>",
        "en": "<li><b>Pick it and write</b>The keyboard picker opens: choose Lëtzebuergesch Clavier and type your first word.</li>",
        "pt": "<li><b>Escolha-o e escreva</b>O seletor de teclado abre-se: escolha Lëtzebuergesch Clavier e escreva a sua primeira palavra.</li>"}),

    # ---- Triptyque, volet 5 : fonctionnalités ----
    ('<div class="kicker">Fonctionnalités</div>', {
        "lb": '<div class="kicker">Funktiounen</div>', "de": '<div class="kicker">Funktionen</div>',
        "en": '<div class="kicker">Features</div>', "pt": '<div class="kicker">Funcionalidades</div>'}),
    ("<h2>Ce qu'il fait pour vous</h2>", {
        "lb": "<h2>Wat se fir Iech mécht</h2>", "de": "<h2>Was sie für Sie tut</h2>",
        "en": "<h2>What it does for you</h2>", "pt": "<h2>O que faz por si</h2>"}),
    ("<li><span>Le <b>mot luxembourgeois</b> arrive avant que vous ayez fini de le taper</span></li>", {
        "lb": "<li><span>D'<b>lëtzebuergescht Wuert</b> kënnt, ier Dir et fäerdeg getippt hutt</span></li>",
        "de": "<li><span>Das <b>luxemburgische Wort</b> erscheint, bevor Sie es zu Ende getippt haben</span></li>",
        "en": "<li><span>The <b>Luxembourgish word</b> appears before you finish typing it</span></li>",
        "pt": "<li><span>A <b>palavra luxemburguesa</b> aparece antes de acabar de a escrever</span></li>"}),
    ("<li><span><b>é, ä, ë</b> ont leur touche ; pour les autres accents, gardez le doigt appuyé</span></li>", {
        "lb": "<li><span><b>é, ä, ë</b> hunn hir Tast; fir déi aner Akzenter, haalt de Fanger drop</span></li>",
        "de": "<li><span><b>é, ä, ë</b> haben eigene Tasten; für weitere Akzente den Finger gedrückt halten</span></li>",
        "en": "<li><span><b>é, ä, ë</b> have their own keys; for other accents, hold your finger down</span></li>",
        "pt": "<li><span><b>é, ä, ë</b> têm tecla própria; para os outros acentos, mantenha o dedo premido</span></li>"}),
    ("<li><span>Il comprend même <b>sans les accents</b> : «&nbsp;letzebuergesch&nbsp;» propose «&nbsp;Lëtzebuergesch&nbsp;»</span></li>", {
        "lb": "<li><span>En versteet Iech och <b>ouni Akzenter</b>: „letzebuergesch“ proposéiert „Lëtzebuergesch“</span></li>",
        "de": "<li><span>Sie versteht Sie auch <b>ohne Akzente</b>: „letzebuergesch“ ergibt „Lëtzebuergesch“</span></li>",
        "en": "<li><span>It understands you <b>without accents</b> too: “letzebuergesch” suggests “Lëtzebuergesch”</span></li>",
        "pt": "<li><span>Percebe-o mesmo <b>sem acentos</b>: «letzebuergesch» sugere «Lëtzebuergesch»</span></li>"}),
    ("<li><span>Vos mots luxembourgeois ne sont <b>plus soulignés en rouge</b>, dans toutes vos applications</span></li>", {
        "lb": "<li><span>Är lëtzebuergesch Wierder sinn <b>net méi rout ënnerstrach</b>, an all Ären Apps</span></li>",
        "de": "<li><span>Ihre luxemburgischen Wörter werden in keiner App mehr <b>rot unterstrichen</b></span></li>",
        "en": "<li><span>Your Luxembourgish words are <b>no longer underlined in red</b>, in any app</span></li>",
        "pt": "<li><span>As suas palavras em luxemburguês <b>deixam de ficar sublinhadas a vermelho</b>, em todas as apps</span></li>"}),
    ("<li><span>Touchez le <b>micro</b> et parlez : il écrit en luxembourgeois (connexion Internet nécessaire)</span></li>", {
        "lb": "<li><span>Dréckt op de <b>Mikro</b> a schwätzt: en schreift op Lëtzebuergesch (Internet néideg)</span></li>",
        "de": "<li><span><b>Mikro</b> antippen und sprechen: Sie schreibt auf Luxemburgisch (Internet nötig)</span></li>",
        "en": "<li><span>Tap the <b>mic</b> and speak: it writes in Luxembourgish (internet connection needed)</span></li>",
        "pt": "<li><span>Toque no <b>microfone</b> e fale: ele escreve em luxemburguês (é preciso ligação à internet)</span></li>"}),
    ('<div class="callout">Il devine aussi le mot suivant&nbsp;: après «&nbsp;op der&nbsp;», il propose déjà «&nbsp;Stad&nbsp;», «&nbsp;Police&nbsp;», «&nbsp;Rue&nbsp;». Moins de lettres à taper, moins de fautes.</div>', {
        "lb": '<div class="callout">En errode och dat nächst Wuert: no „op der“ proposéiert en schonn „Stad“, „Police“, „Rue“. Manner Buschtawen ze tippen, manner Feeler.</div>',
        "de": '<div class="callout">Sie errät auch das nächste Wort: Nach „op der“ schlägt sie schon „Stad“, „Police“, „Rue“ vor. Weniger tippen, weniger Fehler.</div>',
        "en": '<div class="callout">It also guesses the next word: after “op der”, it already suggests “Stad”, “Police”, “Rue”. Fewer letters to type, fewer mistakes.</div>',
        "pt": '<div class="callout">Também adivinha a palavra seguinte: depois de «op der», já sugere «Stad», «Police», «Rue». Menos letras para escrever, menos erros.</div>'}),

    # ---- Triptyque, volet 6 : apprendre ----
    ('<div class="kicker">Et pour apprendre</div>', {
        "lb": '<div class="kicker">A fir ze léieren</div>', "de": '<div class="kicker">Und zum Lernen</div>',
        "en": '<div class="kicker">And for learning</div>', "pt": '<div class="kicker">E para aprender</div>'}),
    ("<h2>Apprendre en tapant</h2>", {
        "lb": "<h2>Léieren, während Dir tippt</h2>", "de": "<h2>Lernen beim Tippen</h2>",
        "en": "<h2>Learn as you type</h2>", "pt": "<h2>Aprender a escrever</h2>"}),
    ('<div class="bloc"><b>🎮 Sept jeux</b> mots mêlés, anagrammes, mot en six essais, phrase à trous, nombres, mots croisés, mots casés.</div>', {
        "lb": '<div class="bloc"><b>🎮 Siwe Spiller</b> Wierder sichen, Anagrammen, e Wuert a sechs Versich, Lückesätz, Zuelen, Kräizwuerträtselen, Wierder placéieren.</div>',
        "de": '<div class="bloc"><b>🎮 Sieben Spiele</b> Wortsuche, Anagramme, ein Wort in sechs Versuchen, Lückensätze, Zahlen, Kreuzworträtsel, Wörter einsetzen.</div>',
        "en": '<div class="bloc"><b>🎮 Seven games</b> word search, anagrams, a word in six tries, fill-in-the-blanks, numbers, crosswords, word fill-ins.</div>',
        "pt": '<div class="bloc"><b>🎮 Sete jogos</b> sopa de letras, anagramas, palavra em seis tentativas, frases com lacunas, números, palavras cruzadas, palavras encaixadas.</div>'}),
    ('<div class="bloc"><b>🃏 Un carnet de cartes</b> chaque mot gagné devient une carte, et les mots que vous tapez la font avancer.</div>', {
        "lb": '<div class="bloc"><b>🃏 E Kaartecarnet</b> all gewonnent Wuert gëtt eng Kaart, an d\'Wierder, déi Dir tippt, bréngen se weider.</div>',
        "de": '<div class="bloc"><b>🃏 Ein Kartenheft</b> jedes gewonnene Wort wird eine Karte, und die Wörter, die Sie tippen, bringen sie voran.</div>',
        "en": '<div class="bloc"><b>🃏 A card deck</b> every word you win becomes a card, and the words you type move it forward.</div>',
        "pt": '<div class="bloc"><b>🃏 Um caderno de cartões</b> cada palavra ganha torna-se um cartão, e as palavras que escreve fazem-no avançar.</div>'}),
    ('<div class="bloc"><b>📖 Un dictionnaire FR ↔ LB</b> un mot vous échappe&nbsp;? Il le traduit dans les deux sens, sans réseau.</div>', {
        "lb": '<div class="bloc"><b>📖 En Dictionnaire FR ↔ LB</b> e Wuert fält Iech net an? En iwwersetzt et an déi zwou Richtungen, ouni Netz.</div>',
        "de": '<div class="bloc"><b>📖 Ein Wörterbuch DE ↔ LB</b> ein Wort fällt Ihnen nicht ein? Es übersetzt in beide Richtungen, ohne Netz.</div>',
        "en": '<div class="bloc"><b>📖 An EN ↔ LB dictionary</b> a word escapes you? It translates both ways, offline.</div>',
        "pt": '<div class="bloc"><b>📖 Um dicionário PT ↔ LB</b> falta-lhe uma palavra? Traduz nos dois sentidos, sem rede.</div>'}),
    ("<figcaption>Jeux</figcaption>", {"lb": "<figcaption>Spiller</figcaption>", "de": "<figcaption>Spiele</figcaption>", "en": "<figcaption>Games</figcaption>", "pt": "<figcaption>Jogos</figcaption>"}),
    ("<figcaption>Carnet</figcaption>", {"lb": "<figcaption>Carnet</figcaption>", "de": "<figcaption>Kartenheft</figcaption>", "en": "<figcaption>Card deck</figcaption>", "pt": "<figcaption>Caderno</figcaption>"}),
    ("<figcaption>Dictionnaire</figcaption>", {"lb": "<figcaption>Dictionnaire</figcaption>", "de": "<figcaption>Wörterbuch</figcaption>", "en": "<figcaption>Dictionary</figcaption>", "pt": "<figcaption>Dicionário</figcaption>"}),
    ('<div class="txt"><b>Installez maintenant</b>Cherchez «&nbsp;Lëtzebuergesch Clavier&nbsp;»', {
        "lb": '<div class="txt"><b>Elo installéieren</b>Sicht „Lëtzebuergesch Clavier“',
        "de": '<div class="txt"><b>Jetzt installieren</b>Suchen Sie „Lëtzebuergesch Clavier“',
        "en": '<div class="txt"><b>Install now</b>Search for “Lëtzebuergesch Clavier”',
        "pt": '<div class="txt"><b>Instale já</b>Procure «Lëtzebuergesch Clavier»'}),
]

# Textes visibles qui restent identiques dans toutes les langues : nom de
# l'application, exemples tapés en luxembourgeois, noms propres.
INVARIANTS = {
    "Lëtzebuergesch Clavier", "LËTZEBUERGESCH CLAVIER", "Schreif Lëtzebuergesch.", "Schreif",
    "Lëtzebuergesch.", "ë · ä · é", "é, ä, ë", "Mir schwätze Lëtzebuergesch, elo schreiwe mir et och.",
    "Mir wëlle bleiwe wat mir sinn.", "Däi Lëtzebuergesch, op dengem Telefon.", "famibelle.github.io/LuxKeyb",
    "· famibelle.github.io/LuxKeyb", "Web", "Ufänker", "Sproochenmeeschter", "LuxAlign", "LETZ",
    "Lëtzebuerger Online Dictionnaire", "Schueb", "Schueberfouer", "op der", "Stad", "Police", "Rue",
    "letzebuergesch", "Lëtzebuergesch", "Changer de thème", "Kontakt",
    # Identiques en luxembourgeois, et traduits par leur entrée dans les autres langues.
    "Carnet", "Dictionnaire", "Disponible sur Google Play",
}

CSS_BANDEAU = """
/* Bandeau « À faire valider » : ajouté par docs/scripts/traduire_supports.py
   tant que la traduction n'a pas été relue par un locuteur natif. Il est
   imprimé exprès : un PDF qui circule doit porter son statut. */
.a-valider{position:absolute;inset:0;z-index:50;pointer-events:none;display:flex;align-items:center;justify-content:center;overflow:hidden;}
.a-valider div{transform:rotate(-28deg);width:160%;padding:3mm 0;text-align:center;background:rgba(255,212,0,.62);
  border-top:1.2mm solid rgba(0,0,0,.55);border-bottom:1.2mm solid rgba(0,0,0,.55);color:rgba(0,0,0,.78);
  font-family:"Fredoka",-apple-system,"Segoe UI",Arial,sans-serif;line-height:1.15;}
.a-valider b{display:block;font-size:30px;letter-spacing:.06em;text-transform:uppercase;}
.a-valider span{display:block;font-size:15px;font-weight:600;}
"""


class Textes(HTMLParser):
    """Collecte les textes visibles (hors nav, style, script) et les alt."""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.ignore = 0
        self.textes = set()

    def handle_starttag(self, tag, attrs):
        if tag in ("style", "script", "nav"):
            self.ignore += 1
        alt = dict(attrs).get("alt")
        if alt:
            self.textes.add(alt.strip())

    def handle_endtag(self, tag):
        if tag in ("style", "script", "nav"):
            self.ignore -= 1

    def handle_data(self, data):
        t = " ".join(data.split())
        if not self.ignore and re.search(r"[A-Za-zÀ-ÿ]", t):
            self.textes.add(t)


def textes(source):
    p = Textes()
    p.feed(source)
    return p.textes


def traduire(support, langue):
    source = (DOCS / f"{support}.html").read_text(encoding="utf-8")
    sortie = source
    for fr, trad in T:
        if fr in sortie:
            sortie = sortie.replace(fr, trad[langue])

    # Liens entre pages et PDF de la même langue.
    sortie = re.sub(r'href="(tract|affiche|triptyque)(-eco)?\.html"',
                    lambda m: f'href="{m.group(1)}{m.group(2) or ""}-{langue}.html"', sortie)
    sortie = re.sub(r'href="assets/(tract|affiche|triptyque)(-eco)?-letzebuergesch-clavier-A4\.pdf"',
                    lambda m: f'href="assets/{m.group(1)}{m.group(2) or ""}-{langue}-letzebuergesch-clavier-A4.pdf"', sortie)
    sortie = sortie.replace('<html lang="fr">', f'<html lang="{langue}">')
    sortie = sortie.replace("assets/google-play-badge-fr.svg", f"assets/google-play-badge-{BADGE[langue]}.svg")
    # Les captures qui montrent du texte d'interface ou un sens traduit
    # existent dans chaque langue ; celles du clavier seul restent communes.
    for capture in CAPTURES_TRADUITES:
        sortie = sortie.replace(f"Screenshots/{capture}.png", f"Screenshots/{capture}_{langue}.png")

    if langue in A_VALIDER:
        sortie = sortie.replace("</style>", CSS_BANDEAU + "</style>", 1)
        bandeau = (f'<div class="a-valider" aria-hidden="true"><div><b>À faire valider</b>'
                   f'<span>{BANDEAU[langue]}</span></div></div>')
        # Un bandeau par tract et non par feuille : la feuille A4 est découpée
        # en deux tracts, et chacun doit porter le sien en entier.
        conteneur = r'<div class="tract">' if '<div class="tract">' in sortie else r'<div class="page"[^>]*>'
        sortie = re.sub(f'({conteneur})', lambda m: m.group(1) + bandeau, sortie)

    # Contrôle : aucun texte français visible ne doit survivre.
    restants = (textes(source) & textes(sortie)) - INVARIANTS
    if restants:
        raise SystemExit(f"{support}-{langue}.html : textes français non traduits :\n  "
                         + "\n  ".join(sorted(restants)))
    return sortie


def main():
    # Une entrée de la table qui ne se trouve dans aucune page est périmée.
    sources = "".join((DOCS / f"{s}.html").read_text(encoding="utf-8") for s in SUPPORTS)
    perimees = [fr for fr, _ in T if fr not in sources]
    if perimees:
        raise SystemExit("Fragments introuvables dans les pages françaises :\n  " + "\n  ".join(perimees))
    for fr, trad in T:
        manque = set(LANGUES) - set(trad)
        if manque:
            raise SystemExit(f"Traduction manquante ({', '.join(sorted(manque))}) pour : {fr[:70]}")

    for langue in LANGUES:
        for support in SUPPORTS:
            (DOCS / f"{support}-{langue}.html").write_text(traduire(support, langue), encoding="utf-8")
    print(f"{len(LANGUES) * len(SUPPORTS)} pages écrites ({', '.join(LANGUES)}).")


if __name__ == "__main__":
    sys.exit(main())
