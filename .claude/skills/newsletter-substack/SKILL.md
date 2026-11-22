---
name: newsletter-substack
description: Rédiger la newsletter du Lëtzebuergesch Clavier ou une publication Substack isolée (un article sur une nouveauté). À utiliser dès que Medhi demande une newsletter, une lettre d'information, un article ou un post Substack sur les nouveautés de l'appli.
---

# Newsletter et publications Substack

Deux usages :

- **La newsletter** : les vrais apports d'usage depuis la dernière lettre.
- **Une publication isolée** : un article sur une seule nouveauté, par exemple la dictée ou le carnet.

Les deux suivent les mêmes règles de fond. Seuls l'ampleur et le titre changent.

Tout est en français. La première édition (octobre 2026, jusqu'à la 37.5.0), validée par Medhi, sert de modèle : `modele-octobre-2026.html`, dans ce dossier.

## 1. Partir de la dernière édition

Lire `editions.md` dans ce dossier : il dit jusqu'à quelle version la dernière lettre est allée. Ensuite, lire `android_keyboard/CHANGELOG.md` depuis la version suivante jusqu'à la plus récente publiée. Ne jamais couvrir une version qui n'est pas taguée et publiée par la CI.

## 2. Sélectionner : seulement les vrais plus

Garder ce qui change l'usage : une chose qu'on ne pouvait pas faire et qu'on peut faire maintenant, ou une chose nettement plus facile.

- **Écarter** :
  - les corrections de bugs ;
  - les gains de vitesse ;
  - les retouches ;
  - ce qui n'est que technique : signature, version minimale d'Android, tests.
- **Écarter les ajouts de faible valeur ajoutée.** Medhi a retiré « L'application parle cinq langues » de la première lettre pour cette raison. En cas de doute sur une rubrique, la proposer en dernier, ou demander.
- **Le visuel des cartes** (paysages, saisons, reflets, cérémonies) tient dans **un seul encadré** en fin de lettre, « Et pour le plaisir des yeux ». Il ne prend jamais une rubrique entière, sauf dans une publication isolée qui lui est consacrée.
- **L'ordre suit la valeur pour l'utilisateur.** Ordre validé en octobre 2026 : dictée, fautes de frappe, carnet, tablettes, Zuelwuert, installation. Le carnet passe avant les tablettes.
- **Pas de fonction en test fermé ni sur une branche** : les Labs, la dictée avant sa fusion, les actualités de l'INLL. Pas de promesse sur une fonction à venir.

## 3. Écrire

Règles communes à tous les textes publics (interdits, typographie, ce qu'on peut affirmer) : skill **textesPublics**.

Règles de fond, toutes données par Medhi :

- **La cible n'est pas technophile** (sa maman utilise l'appli). Chaque phrase dit un bénéfice concret, en mots de tous les jours.
- **Proscrits** :
  - les chiffres de fiche technique, les millisecondes et les noms de fichiers ;
  - le vocabulaire de clavier : QWERTZ, n-grammes, correcteur système, Levenshtein ;
  - les promesses en négatif (« 0 mot envoyé ») et l'argument « sans publicité ».
- **Un exemple qui se voit vaut mieux qu'une affirmation** : « echhunn → ech hunn », « haud → Haus ». Prendre ces exemples dans le CHANGELOG, où ils ont été vérifiés.
- **Le luxembourgeois** :
  - **jamais de phrase inventée** ;
  - n'utiliser que des phrases attestées : exemples du LOD (`luxemburgish_exemples.json`), phrases déjà montrées dans les captures du site, exemples du CHANGELOG ;
  - même règle pour les noms de lieux et de fêtes : ceux du code et du CHANGELOG, pas d'autres.
- **Jamais de tiret cadratin (—).** Utiliser les deux-points, les virgules, les parenthèses ou une nouvelle phrase.
- **Le slogan** « Vous le parlez. Maintenant, vous l'écrivez. » vient avant l'appel à l'action.
- **La confidentialité reste en petit**, dans le corps d'une rubrique (la dictée explique où part la voix), jamais en titre.
- **Le pied de lettre** :
  - « gratuit sur Android, pour les 16 ans et plus » et le numéro de version ;
  - le lien vers le formulaire `https://famibelle.github.io/LuxKeyb/feedbacks_form.html` ;
  - le lien vers le simulateur `https://famibelle.github.io/LuxKeyb/simulateur.html`.
- **L'appel à l'action** mène à la fiche Play Store `https://play.google.com/store/apps/details?id=com.potomitan.luxkeyboard`. Vérifier que la version annoncée y est publiée ; sinon le dire à Medhi.
- **LuxKeyb n'a aucun lien avec Potomitan** : ne jamais citer Potomitan, malgré l'identifiant de l'appli.
- Les noms des jeux, des onglets et des niveaux restent en luxembourgeois : Zuelwuert, Wierderbuch, Spiller.

Forme d'une rubrique :

- une étiquette courte (le thème) ;
- un titre-bénéfice : « Parlez, le texte s'écrit », « Le clavier rattrape vos doigts pressés » ;
- deux ou trois phrases ou une courte liste ;
- une image avec une légende d'une ligne.

## 4. Les images

- **D'abord `docs/Screenshots/lux_*`** pour le clavier, les jeux et les écrans (pas pour les cartes, voir le filigrane plus bas). Les GIF existants (`lux_dictee_clavier.gif`, `lux_correction_frappe.gif`…) font de très bonnes illustrations. Vérifier qu'elles montrent l'état actuel de l'appli. Une capture plus ancienne qu'une refonte visuelle (par exemple une carte d'avant la 37.5) doit être signalée ou refaite.
- **Les cartes neuves se capturent sur le A21s quand il est connecté.** Sinon, sur l'émulateur `pixel9` (avec sa fenêtre visible, `./gradlew --stop` avant).
  - Une activité de banc temporaire sous `android_keyboard/app/src/debug/`, **jamais commitée**, supprimée après usage. Elle construit la carte avec `CarteCarnet.contenu(this, CarteMot(...))`, puis la rend avec `CarteCarnet.complete`.
  - **La rareté affichée doit être la vraie** : passer le rang réel du mot (son index dans `luxemburgish_dict.json`) et `Rarete.pourRang(rang)`, sinon le pied de carte affiche « hors corpus ».
  - Choisir des mots dont le rang tombe dans le palier voulu. Seuils : commun < 3000, peu commun < 6500, rare < 9000. Exemples validés : Bam, Sonn, Brëll, Kran.
  - La date de capture donne la saison.
- **Le A21s** : contrôler le premier plan (`dumpsys window | grep mCurrentFocus`) avant toute entrée injectée, ne jamais toucher au wifi, remettre notre clavier par défaut s'il a sauté, ne pas réinstaller de version après.
- **L'émulateur** : `emu sensor set` pour incliner la carte a fait planter qemu (2026-10-20). Filmer la lumière qui voyage sur le A21s, pas sur l'émulateur.
- **Aucune image à filigrane dans la newsletter** : Medhi trouve le filigrane laid (2026-10-20), sur les cartes comme sur les animations. Ne pas reprendre telles quelles les captures de cartes de `docs/Screenshots`, qui le portent presque toutes. Chercher l'original sans filigrane dans l'historique git ; seules `lux_carnet_metaux`, `lux_carnet_saisons` et `lux_carnet_saisons_album` en ont un (avant le commit `8b6d2556`). Sinon, recapturer sans filigrane ou se passer de l'image. Le libellé « LuxKeyb™ » de la barre d'espace, sur les captures du clavier, n'est pas un filigrane : il est bienvenu.
- **Le filigrane, pour le site seulement** : toute capture de carte neuve destinée à `docs/` passe **une seule fois** par `python docs/scripts/filigrane_cartes.py <png>`. Le script trouve les cartes sur un fond `#F5F5F5`, donc il faut monter les cartes sur ce fond avant de le lancer.
- **Le format** : WebP pour les images fixes (largeur 480 à 1000 px), GIF tels quels. Le tout, prévisualisation comprise, doit rester sous quelques Mo.

## 5. Ce que la skill produit

1. **La prévisualisation** : un artefact HTML dans la charte de `modele-octobre-2026.html`.
   - Une colonne de 660 px, la bande du drapeau en haut.
   - Les polices Bricolage Grotesque (titres) et Literata (texte).
   - Le rouge `#ED2939` et le bleu `#00A1DE`.
   - Les thèmes clair et sombre.
   - L'éventail de quatre cartes (étain, bronze, argent, or) se place **juste avant la rubrique du carnet**, pas en tête. Sa légende dit que les cartes servent à découvrir le luxembourgeois.
   - Les images sont publiées comme fichiers de l'artefact (`files` avec `root`), pas en data URI.
   - Medhi a validé cette charte : la reprendre, ne pas la réinventer.
2. **La version Substack**. L'éditeur de Substack ne garde que les titres, les paragraphes, le gras, les liens, les listes, les citations, les images et les boutons : ni couleurs, ni colonnes, ni éventail. Publier un second artefact, sobre, qui contient :
   - le **titre** (60 caractères au plus) et le **sous-titre** (une phrase), à copier à part ;
   - le **corps** en HTML simple (h2, p, ul, strong, a), sans CSS, à sélectionner et coller dans l'éditeur. Un bouton « Copier » utilise `navigator.clipboard.write` avec du `text/html`, et revient à la sélection du texte s'il est refusé ;
   - à la place de chaque image, un repère « [Image 3 : lux_correction_frappe.gif, légende : …] ». Les images sont aussi listées dans l'ordre, pour que Medhi les insère une à une. Substack n'importe pas les images collées depuis une autre page ;
   - le bouton d'appel, décrit comme un bouton Substack : texte et lien ;
   - **une note courte** (Substack Notes ou réseaux sociaux) : trois phrases au plus et le lien.
3. **La version en ligne sur le site**, une fois l'édition validée : `docs/newsletter/AAAA-MM.html`, une page HTML complète (doctype, head) reprenant la prévisualisation. Les images vont dans `docs/newsletter/AAAA-MM/`, les GIF déjà dans `docs/Screenshots` sont référencés par `../Screenshots/`. Le head porte les balises `og:` et `twitter:` (`summary_large_image`) avec `og:image` = `docs/newsletter/AAAA-MM/partage.png`. C'est la seule version où la mise en forme est intacte : le mail et Substack y renvoient. Commit des seuls chemins `docs/newsletter/…` sur `main`, push direct (règle du site), puis vérifier que l'adresse répond 200.
   **La version mail** : `docs/newsletter/AAAA-MM-mail.html` (`noindex`), modèle `docs/newsletter/2026-10-mail.html`. Largeur 600, tableaux, styles en ligne uniquement (pas de `<style>` : Gmail le perd au collage), polices Arial/Georgia, adresses absolues `https://famibelle.github.io/LuxKeyb/...`. Images dans `docs/newsletter/AAAA-MM/mail/` en JPG ou GIF, jamais WebP : le motif de la dictée en GIF animé qui pulse (16 images de l'animation figée par `animation-delay` négatif + `paused`, rendu Chrome à 2×, quasi-blancs forcés à 255 avant la palette, sinon un cadre gris apparaît), l'éventail rendu d'un bloc en JPG. Guillemets avec `&nbsp;`. Mode d'emploi pour l'utilisateur : ouvrir l'adresse, Ctrl+A, Ctrl+C, coller dans un nouveau message Gmail ; ou importer la source dans Brevo/Mailchimp.
   **L'archive** : ajouter l'édition en tête de la liste de `docs/newsletter/index.html` (un `<li class="edition">` : vignette `AAAA-MM/partage.png`, mois et année, titre, une phrase, lien), dans le même commit que la page. Le menu de toutes les pages du site mène à cette archive (« 📰 Newsletters »).
   **Les traductions** (depuis le 2026-10-20) : chaque édition existe aussi en anglais, allemand et portugais, `AAAA-MM-en.html`, `-de.html`, `-pt.html`, produites depuis la page française par une table de fragments (modèle : `traduire_newsletter_2026_10.py`, qui refuse d'écrire si un fragment manque). Termes de l'appli : notebook / Heft / caderno, quill / Feder / pena ; l'allemand vouvoie, le portugais est celui du Portugal ; slogans « You speak it. Now you write it. », « Sie sprechen es. Jetzt schreiben Sie es. », « Fala-o. Agora, escreve-o. ». Chaque version a sa vignette (`partage-<l>.png`, modèles `vignette5_<l>.html`) et un sélecteur FR · EN · DE · PT en tête ; l'archive liste les traductions sous la carte de l'édition. Ces traductions n'ont pas été relues par un locuteur natif : le dire à Medhi.
4. **La vignette de partage** : 1200 × 630, une page HTML rendue par `google-chrome --headless=new --window-size=1200,800 --screenshot` puis rognée à 630 (la fenêtre sans tête est plus haute que la zone affichée). Hiérarchie des messages fixée par Medhi (2026-10-20) : **au premier rang, à égalité, la suggestion des mots et la dictée** (leur valeur est presque la même), **au second rang les cartes à collectionner**. **La hiérarchie visuelle suit celle des messages** (Medhi, 2026-10-20) : la taille et la place à l'image disent le rang. Modèle `modele-vignette-2026-10.html` : la bande du drapeau, une ligne de tête (date à gauche, slogan à droite) ; puis deux colonnes égales qui occupent les deux tiers de l'image. À gauche « Tapez, il propose le mot » au-dessus d'une **vraie capture du clavier en action** (Medhi : il faut voir le clavier) : `lux_supports_clavier.png` rognée à (0, 70, 1080, 672), Schueb → Schueberfouer. À droite « Parlez, il écrit » au-dessus du **motif dictée** (`motif-dictee.html`), de la même hauteur que la capture. Enfin un bandeau bas discret : petit éventail des quatre cartes et « Et des cartes à collectionner pour découvrir la langue ».

**Le motif dictée est un élément d'identité, à garder sur toutes les créations, existantes et à venir** (Medhi, 2026-10-20 : « je suis très fan, garde-la bien ») : grand micro rouge avec deux halos et phrase attestée en bulle, code dans `motif-dictee.html`. Ne pas le remplacer par une capture de la dictée, ni le redessiner. Les cartes ne doivent jamais être l'élément le plus gros de la vignette. La même hiérarchie vaut pour le titre et la description `og:`. Contrôler qu'aucune carte ne touche le titre ni le bord droit.
5. **Rien n'est publié sur Substack par Claude.** Il n'y a pas d'API officielle, et une publication est irréversible pour les abonnés. Medhi colle et publie lui-même.

## 6. Avant de rendre

- Relire chaque phrase pour les tirets cadratins et le jargon.
- Relire chaque phrase luxembourgeoise : source vérifiée ?
- Lister à Medhi les rubriques retenues et celles écartées, en une ligne chacune, et dire quelles captures sont anciennes.
- Une fois l'édition validée, ajouter une ligne à `editions.md` : la date, la version couverte, les rubriques et l'adresse de l'artefact.
