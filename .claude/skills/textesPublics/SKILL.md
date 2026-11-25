---
name: textesPublics
description: Écrire ou modifier tout texte que lisent les utilisateurs ou le public du Lëtzebuergesch Clavier (LuxKeyb), c'est-à-dire les libellés et messages de l'application (strings.xml en cinq langues), le CHANGELOG, les pages du site docs/, la fiche Play, les comparatifs, la presse et les dossiers institutionnels. Fixe le ton, les interdits, la typographie française, les cinq langues et la règle « montrer avant d'appliquer ». À utiliser dès qu'un texte visible est créé, reformulé ou traduit ; les skills guideUtilisateur, newsletter-substack, supports-communication et publierVersion s'appuient dessus.
---

# Les textes publics

## 1. Pour qui on écrit

**L'utilisatrice de référence est la maman de Medhi**, qui se sert vraiment de l'application et n'est pas technophile. Avant d'écrire une ligne, se demander ce qu'elle apprend à quelqu'un qui ne sait pas comment l'application est faite.

- **Le bénéfice, pas la technique.** Dire ce qu'on peut faire (« Tapez « Schueb », il propose « Schueberfouer » »), pas comment c'est fait. Un exemple qui se voit vaut mieux qu'une affirmation.
- **La solution, pas le problème.** « Fini les mots soulignés en rouge » a été refusé comme négatif. Pas de promesse en négatif (« 0 mot envoyé ») ni d'argument « sans publicité ».
- **Jargon interdit à l'écran** : corpus, n-grammes, LOD, Bloom, IME, QWERTZ, diacritiques, Levenshtein, « il complète », compteurs d'inventaire (« 88 852 mots traduits »). Ce savoir reste dans le code et les commentaires. Ne pas non plus le remplacer par un libellé adouci qui serait un jugement déguisé (« mot courant »).
- **Le slogan** : « Vous le parlez. Maintenant, vous l'écrivez. » Les deux réponses du clavier sont l'écriture fluide (les suggestions) et la dictée ; les cartes viennent au second rang. La confidentialité tient en une ligne, jamais en un volet entier.
- **Les institutions** (ministère, ZLS, Uni.lu) ont leur propre angle : le clavier valorise les ressources publiques luxembourgeoises. Voir le dossier institutionnel.

## 2. Ce qu'on a le droit d'affirmer

- **Vérifier chaque fait dans le code avant de l'écrire**, surtout la confidentialité. Le guide a dit pendant des semaines « rien n'est envoyé », alors que la dictée envoie la voix à l'Université du Luxembourg. La formule juste : ce que vous tapez ne quitte jamais le téléphone ; seule la dictée envoie votre voix.
- **Les autres claviers** : on ne parle que du luxembourgeois. Sans source qui annonce explicitement une fonction *en luxembourgeois*, on écrit « Non », comme un fait, avec les sources consultées en note. Jamais « non vérifié ». Mais un chiffre que nous n'avons pas mesuré reste « non mesuré ».
- **Ne jamais parler, dans un texte public, des conditions d'accès à LuxASR** : dire seulement que la dictée passe par le service de l'Université du Luxembourg.
- **Sur le site, le nom s'écrit « de Lëtzebuerger Clavier »** (choix de Medhi, 2026-11-25), avec l'article dans toutes les langues : « De » en tête de phrase, de titre ou de libellé ; il remplace l'article de la langue (« le », « the », « o » tombent ; « du » et « do » donnent « de de Lëtzebuerger Clavier ») ; en luxembourgeois l'article se décline (« vum Lëtzebuerger Clavier »). Depuis le 2026-11-25, l'application (`app_name`, nom du clavier dans le sélecteur) et la fiche Google Play portent aussi « De Lëtzebuerger Clavier » ; entre guillemets, on cite le libellé exact, avec la majuscule. Seuls gardent « Lëtzebuergesch Clavier » : les newsletters déjà envoyées, la politique de confidentialité (à mettre à jour avec la prochaine version publiée), l'historique des versions (`stats/`) et les noms de fichiers (`letzebuergesch-clavier-A4.pdf`, l'APK), qui sont des adresses.
- **LuxKeyb est un projet personnel de Medhi**, indépendant, sous licence MIT. Ne jamais présenter Potomitan comme éditeur, malgré l'`applicationId`.
- **Aucune mention du créole**, du kréyòl, de la Guadeloupe ni de Karukera sur le site ou dans l'application. La filiation reste invisible (identifiants de code compris : ne pas les renommer).
- **Public cible : 16 ans et plus.** Ne jamais viser les enfants ni les écoles primaires.
- **Les attributions sont des obligations de licence** (LuxAlign CC BY-NC, LETZ CC BY, Lexique CC BY-SA, LOD et ZLS crédités) : ne jamais les retirer d'un écran ou d'une page qui les porte.

## 3. Typographie française

- **Pas de tiret cadratin (—)**, ni de demi-cadratin (–) en remplacement : c'est un marqueur de texte d'IA. Mettre la ponctuation qui porte le sens : virgules pour une apposition, deux-points pour une explication, parenthèses pour un aparté, un point pour couper.
- **Espace insécable (U+00A0)** à l'intérieur des guillemets « » et avant « ? », « ! », « : », « ; ». Sinon un mot se retrouve séparé de son guillemet en bout de ligne sur un petit écran (vu sur « Moien » dans le guide).
- Guillemets, comme dans les `strings.xml` actuels : « » en français et en portugais, “ ” en anglais, „ “ en allemand et en luxembourgeois.
- **Les noms de l'application restent en luxembourgeois** dans toutes les langues : Haut, Spiller, Wierderbuch, Mäi Lëtzebuergesch, Mäi Carnet, et les jeux (Wuertsich, Wuertmix, Wuertriet, Wuertlück, Zuelwuert, Kräizwuert, Wuertplaz). À la première mention, on peut ajouter leur sens dans la langue de l'interface.
- **Citer un libellé de l'écran tel qu'il s'affiche** : le vérifier par `grep` dans le `strings.xml` de chaque langue, jamais de mémoire.

## 4. Cinq langues

**Application** : `res/values/` (anglais, langue par défaut), `values-fr`, `values-lb`, `values-de`, `values-pt`. Une chaîne ajoutée dans une langue l'est dans les cinq.

- Dans `strings.xml` : apostrophe échappée `\'`, retour à la ligne `\n`, `&amp;` pour &, `%1$s` pour un paramètre. Garder les espaces insécables du français telles quelles (caractère U+00A0).
- **Interface en luxembourgeois** : les sens des mots (gloses, traductions des exemples) s'y affichent **en français**, parce qu'il n'existe pas de dictionnaire luxembourgeois-luxembourgeois (ressource `langue_traductions` = `fr` dans `values-lb`).
- **Les textes lb, de et pt sont écrits par Claude.** Le dire à Medhi à chaque fois, et proposer la relecture par la professeure de luxembourgeois pour le lb.
- Un nom luxembourgeois inventé (comme `Wuertplaz`) doit être formé de mots attestés par le LOD, et signalé comme non vérifié par un locuteur natif.

**Site** : le français est la source. **L'accueil** est `docs/index.html` ; `en.html`, `de.html` et `pt.html` en sont **générés** par `docs/scripts/generer_accueils.py`, jamais édités à la main : après toute modification de l'accueil français, compléter les tables du script (il s'arrête sur tout texte français qu'il ne sait pas traduire) puis le relancer. L'ancien contenu détaillé vit dans `en-detail.md` et ses traductions `en-detail-en.md`, `en-detail-de.md`, `en-detail-pt.md`. Les déclinaisons lb, de, en et pt des supports sont **générées** (`docs/scripts/traduire_supports.py`), jamais éditées à la main ; la page Nouveautés se traduit dans `docs/stats/traductions/`. Le français d'abord, puis les traductions.

## 5. Montrer avant d'appliquer

Tout texte nouveau ou réécrit qui sera vu par les utilisateurs se **montre en entier, en français, à Medhi avant d'être appliqué**, sauf s'il a dit de faire directement. Montrer le texte final, pas un résumé : c'est la formulation qu'il juge.

## Liste de contrôle

- [ ] Compréhensible par quelqu'un qui ne sait pas comment l'application est faite ; aucun mot de jargon.
- [ ] Chaque fait vérifié dans le code, confidentialité en tête.
- [ ] Pas de tiret cadratin ; espaces insécables en français.
- [ ] Libellés cités vérifiés par `grep` dans chaque langue.
- [ ] Cinq langues dans l'application ; lb, de et pt signalés comme à relire.
- [ ] Texte montré à Medhi avant d'être appliqué.
