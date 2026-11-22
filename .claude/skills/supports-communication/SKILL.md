---
name: supports-communication
description: Créer ou mettre à jour les supports de communication imprimables du Lëtzebuergesch Clavier (tract, affiche, triptyque, versions éco encre, déclinaisons lb/de/en/pt, PDF). À utiliser dès que Medhi parle d'un tract, d'une affiche, d'un dépliant, d'un triptyque, d'un flyer, d'un support imprimé ou de la page « Supports de communication ».
---

# Supports de communication imprimables

Trois supports, chacun en deux versions et cinq langues, soit 30 pages et 30 PDF :

| Support | Page source (FR) | Format |
|---|---|---|
| Tract | `docs/tract.html` | A5, deux par feuille A4 |
| Affiche | `docs/affiche.html` | A4 portrait |
| Triptyque | `docs/triptyque.html` | A4 paysage recto-verso, plié en trois |

Chaque support a une version **éco encre** (`<support>-eco.html`) : fond blanc, sans aplat, la couleur ne garde que les titres, les puces et les traits. Les langues `lb`, `de`, `en` et `pt` sont **générées**, jamais éditées à la main.

Ils sont présentés sur `docs/supports-communication.html` et `docs/ambassadeurs.html`, avec une description d'une ligne et un aperçu `docs/assets/supports-<support>-1.jpg`.

## 1. Le fond : un bénéfice face à une douleur

Règles communes à tous les textes publics (interdits, typographie, ce qu'on peut affirmer) : skill **textesPublics**.

La cible est une personne qui parle luxembourgeois sans l'écrire et qui n'aime pas la technique. L'utilisatrice de référence est la maman de Medhi. Elle prend le support pour trouver une solution à sa vie de tous les jours, et chaque volet doit lui en apporter une.

- **Slogan** : « Vous le parlez. Maintenant, vous l'écrivez. »
- **Les deux réponses**, au même rang :
  - **Tapez, il propose** : la suggestion, montrée par la vraie capture (« Schueb » → « Schueberfouer »).
  - **Parlez, il écrit** : la dictée, montrée par le motif du micro (voir 3).
- **Un exemple qui se voit vaut mieux qu'une affirmation.** Par exemple « Kafi » devient « Kaffi » pour la correction des fautes.
- **Interdit** :
  - le jargon de fabrication : corpus, LuxAlign, nombre de mots, QWERTZ, n-grammes, correcteur système, « il complète » ;
  - les promesses en négatif : « plus soulignés en rouge », « 0 mot envoyé » ;
  - l'argument « sans publicité » ;
  - le tiret cadratin.
- **Pas de volet entier pour « Comment nous aider », la confiance ou la confidentialité.** Medhi l'a refusé le 2026-10-20. L'aide au projet et la confidentialité tiennent en une ligne de mentions, par exemple « Gratuit · Ce que vous tapez reste sur votre téléphone ».
- **Le dictionnaire se décrit dans la langue du lecteur.** Les traductions suivent la langue du téléphone, donc « en français ou en luxembourgeois » devient « auf Deutsch oder auf Luxemburgisch » en allemand.
- **Montrer le texte complet à Medhi avant de l'appliquer**, quand il s'agit d'un nouveau texte ou d'une refonte. Une retouche purement visuelle se fait directement.

## 2. Le triptyque, volet par volet (état du 2026-10-20)

| Face | Volet | Contenu |
|---|---|---|
| Recto | 1, dos (foncé) | « Le luxembourgeois, à chaque moment de votre journée » : cinq moments (message du matin, mains prises, doigt qui glisse, mot qui échappe, jeu du soir), puis une ligne de mentions |
| Recto | 2 | « Pourquoi un clavier luxembourgeois ? » : quatre douleurs, « Ce clavier est fait pour vous », la capture du clavier puis, dessous, le motif du micro en grand (la capture des accents a été retirée le 2026-10-20, redondante), encadré « Ouvrez le dépliant » |
| Recto | 3, couverture | Slogan, trois pastilles, capture du clavier avec le motif du micro, carte « Kran » qui dépasse, QR et badge Google Play |
| Verso | 4 | Installation en 4 étapes, QR à l'étape 1 |
| Verso | 5 | Démonstration : 1 Tapez, il propose (capture) ; 2 Parlez, il écrit (motif) |
| Verso | 6 | Apprendre en tapant : jeux, carnet avec l'éventail de cartes, dictionnaire, captures Jeux et Dictionnaire, appel à installer |

L'échelle des huit niveaux a été retirée le 2026-10-20 pour laisser la place à l'éventail : c'est le carnet qui porte la progression.

Contraintes physiques :
- La feuille est imprimée à **100 %**, « Taille réelle ».
- Le volet étroit est en `:first-child` au recto et en `:last-child` au verso, pour que les rainures coïncident après retournement par le bord court.
- Chaque PDF doit faire exactement **2 pages**. Une page de plus veut dire qu'un volet déborde : vérifier que le dernier bloc du volet 6 n'est pas coupé.

## 3. Les visuels

- **La dictée** se montre toujours par le motif : grand micro rouge `#ED2939` à deux halos, et une bulle `#EEF4F8` contenant « Op der Kiermes iessen ech ëmmer Gromperekichelcher ». Ne jamais utiliser de capture de la dictée. En HTML, c'est le bloc `.dictee` : `.micro` avec son SVG, et `.bulle`. Ses dimensions se règlent par les variables `--dw --dp --dg --dm --df`. La référence est `.claude/skills/newsletter-substack/motif-dictee.html`.
- **La suggestion** se montre par la vraie capture : `docs/Screenshots/lux_supports_clavier.png`, disposition Suisse allemand.
- **Les cartes du carnet** sont les quatre cartes de l'étain à l'or capturées le 2026-10-31 avec les dessins du domaine public, jamais une capture plus ancienne (celles de la newsletter d'octobre, `docs/newsletter/2026-10/v_*.webp`, restent à leur place pour la newsletter) :
  - une carte seule : `docs/assets/cartes/carte_kran.webp` (or, très rare, nuit d'hiver). Aussi `carte_bam`, `carte_sonn`, `carte_brell`. Elles portent le filigrane « LuxKeyb » ;
  - l'éventail : `docs/assets/cartes-eventail.png`, en PNG transparent, composé avec PIL à partir des quatre `carte_*.webp` ; la capture 7 du Play Store (`22-cartes-quatre-metaux.png`) est faite des mêmes quatre cartes. Les coins sont arrondis par un masque de rayon 30, car les webp ont un fond sombre dans les angles. Recomposé le 2026-10-20 par `eventail.py` (dans ce dossier, à lancer depuis la racine du dépôt) : la première version coupait le bord droit de la carte Kran à l'impression ; le script recadre sur l'alpha avec une marge. Recomposé le 2026-10-31 avec les nouvelles cartes. Ne pas utiliser `newsletter/2026-10/mail/cartes.jpg`, dont le fond blanc fait un rectangle sur les volets teintés ;
  - **une carte n'affiche jamais de date.** `Screenshots/lux_carte_stad*.png` est une capture du 2026-09 avec la date et sans paysage : **périmée**, retirée de tous les supports le 2026-10-20 (et de `CAPTURES_TRADUITES`).
- **« 100 % gratuit » est un tampon rouge**, jamais une ligne de texte ni un cercle (choix de Medhi, 2026-10-20, d'après un tampon encreur) : `docs/assets/tampon-gratuit.css`, à lier après `fredoka-embed.css`. Balisage : `<span class="tampon"><span class="tampon-chiffre">100&nbsp;%</span><span class="tampon-mot">gratuit</span></span>`, taille par `font-size` sur `.tampon[class]`. Police Stardos Stencil 700 (OFL) embarquée et réduite aux lettres de 100 % GRATUIT / GRATIS / FREE / GRÁTIS : un autre mot demande de régénérer le sous-ensemble (Google Fonts `css2?family=Stardos+Stencil:wght@700&text=…`). Les sélecteurs sont en `[class]` pour passer devant les règles génériques (`.stamp span`, `.txt b`). Un seul tampon par face visible : couverture et encart du volet 6 sur le triptyque, à la place du cercle sur le tract et l'affiche. Vérifier avec `pdffonts` que « Stardos » est dans chaque PDF.
- **La recherche Play Store est un champ de recherche dessiné**, pas la phrase « Cherchez « Lëtzebuergesch Clavier » » (choix de Medhi, 2026-10-20) : `span.recherche` = libellé « Cherchez : » au-dessus d'une pilule blanche bordée de bleu, loupe SVG, texte en Fredoka gras et curseur bleu. Encart du volet 6 du triptyque ; il a fallu ramener les captures Jeux/Dictionnaire à 32 mm de haut pour que l'encart tienne en lb, de et pt.
- Les captures qui contiennent du texte d'interface existent dans chaque langue (`<capture>_<langue>.png`). Leur liste est `CAPTURES_TRADUITES` dans le script. Les cartes de la newsletter n'existent qu'en français.

## 4. La chaîne de fabrication

1. **Modifier la page française**, puis porter le même changement dans `-eco.html` : même HTML, couleurs adaptées au fond blanc.
2. **Mettre à jour la table** de `docs/scripts/traduire_supports.py`. Elle contient des fragments HTML **exacts** français → `{lb, de, en, pt}`. Les guillemets se changent selon la langue : „…“ en lb et en de, “…” en en, «…» sans espace en pt. Le luxembourgeois reste tel quel.
3. **Lancer** `python3 docs/scripts/traduire_supports.py`. Le script refuse d'écrire si :
   - un fragment de la table n'existe plus dans aucune page source (fragment périmé) : le retirer de la table ;
   - un texte français visible survit dans une traduction : ajouter le fragment, ou le mettre dans `INVARIANTS` s'il est en luxembourgeois ou s'il s'agit d'un nom propre.

   Pour retirer en masse les entrées périmées, `ast.parse` sur le script donne les lignes de chaque tuple de `T`.
4. **Refaire les PDF**, depuis `docs/` :
   ```bash
   for s in triptyque triptyque-eco; do for l in "" -lb -de -en -pt; do
     timeout 120 google-chrome --headless=new --disable-gpu --no-sandbox --no-pdf-header-footer \
       --virtual-time-budget=8000 --print-to-pdf=assets/$s$l-letzebuergesch-clavier-A4.pdf file://$PWD/$s$l.html
   done; done
   for f in assets/triptyque*-A4.pdf; do echo "$(pdfinfo $f | awk '/Pages/{print $2}') $f"; done
   ```
5. **Refaire l'aperçu** `assets/supports-<support>-1.jpg` : page 1 du PDF français, `pdftoppm -r 150`, redimensionnée à la taille de l'ancien fichier, en JPEG qualité 85.
6. **Vérifier à l'œil**, avec `pdftoppm -r 60` sur les deux pages, le français et au moins une traduction. Une capture d'écran Chrome headless de la page n'est pas fiable pour la hauteur, il lui manque 87 px.
7. **Mettre à jour les descriptions** d'une ligne dans `supports-communication.html` et `ambassadeurs.html` si le contenu a changé.

## 5. Publier

- Vérifier que la branche est `main`, car une autre session peut en avoir changé.
- Ajouter uniquement les fichiers `docs/` concernés. Ne jamais embarquer `SuggestionEngine.kt`, `BancEfficaciteTest.kt` ou un fichier d'une autre session.
- Commit en français, du type `docs(supports): …`, poussé directement sur `main` sans PR. Commiter et pousser seulement quand Medhi le demande.
- Vérifier la mise en ligne par `curl` sur `https://famibelle.github.io/LuxKeyb/<page>.html`, en cherchant un marqueur du nouveau contenu.

## 6. Les autres supports, mis au même niveau le 2026-10-20

- **Publicités** (`docs/publicites.html`, tableau `CREA`, `python3 docs/assets/ads/generate.py`) : axe A sur le slogan, axe B en luxembourgeois ; badge Google Play et tampon au pied des formats fil et Google, pas sur les formats verticaux (les régies posent leur bouton). Vérifier chaque export à l'œil : trois formats débordaient (titres trop larges).
- **Play Store** (`LuxKeybPlayStore/graphics/build_graphics.py`, `shots`, `hors`, `tablette`, `hors-tablette`, puis `check`) : pied « gratuit » seul ; capture 1 = `21-clavier-suggestions-schueb-recadre.png` (copie de `docs/Screenshots/lux_supports_clavier.png`) ; capture 7 = `22-cartes-quatre-metaux.png`, posée sans cadre (`SANS_CADRE`). La fiche (`texts/<langue>/full_description.txt`) commence par suggestion, dictée, fautes de frappe ; après toute modification, régénérer `texts/import-console-traductions.txt` et reporter le texte à la main dans la Play Console.
- **Reddit** (`LuxKeybPlayStore/graphics/reddit/`, `reddit.py` puis `video_reddit.py`) : tampon au pied, dictée par `reel.ÉcranDictée`. **Les copies du site ne se font pas toutes seules** : copier les vidéos dans `docs/assets/reddit/`, refaire les deux zip (images + README) et `docs/assets/supports-reddit-1.jpg`. Elles étaient restées au 18 octobre.
- **Reel** (`LuxKeybPlayStore/graphics/video/reel.py`) : déjà au motif du micro qui pulse ; vérifier que `docs/assets/reel-letzebuergesch-clavier-fr.mp4` a le même md5 que la sortie.
- Hors champ : le dossier de presse et la note institutionnelle, dont les chiffres et « sans publicité » servent un autre public.

## 7. Reste à faire

- Reporter dans la Play Console la nouvelle fiche (5 langues) et les captures 1, 2, 5 et 7.
- Les traductions lb, de et pt portent le bandeau « À faire valider » tant qu'un locuteur natif ne les a pas relues. Pour retirer le bandeau d'une langue, la retirer de `A_VALIDER`.
