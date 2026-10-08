# Fiche Play Store — Lëtzebuergesch Clavier

Les textes à coller dans la Play Console (Développer la présence → Fiche
Play Store principale, puis une traduction par langue) sont rangés **un dossier
par langue de l'interface**, un fichier par champ, selon la disposition de
fastlane `supply` :

```
fr-FR/  lb/  de-DE/  en-US/  pt-PT/
  title.txt               Nom de l'application (30)
  short_description.txt   Brève description (80)
  full_description.txt    Description complète (4 000)
  changelogs/340100.txt   Nouveautés de la version, par versionCode (500)
```

Le français (`fr-FR/`) est la fiche principale et la référence : les autres
langues en sont la traduction, section pour section. Ce fichier-ci garde les
raisons de chaque choix. Version de référence : **34.3.0**
(`versionCode` 340300), la troisième adaptée aux tablettes, `applicationId` `com.potomitan.luxkeyboard`.

La Play Console **n'interprète pas le markdown** : pas de `**gras**`, pas de
`#` — les astérisques s'afficheraient tels quels. Tous les blocs ci-dessous
sont déjà en texte brut, émojis compris.

---

## Nom de l'application

*30 caractères maximum.*

```
Lëtzebuergesch Clavier
```

22 caractères. Identique à `app_name` dans `strings.xml`, donc le nom sous
l'icône du téléphone et le nom sur le Store coïncident.

Les 8 caractères restants ne sont pas utilisés volontairement : le mot-clé
recherché — « clavier luxembourgeois » — est couvert par la brève description
et la description complète, que l'algorithme du Store indexe aussi, alors
qu'un titre à rallonge est tronqué dans les listes de résultats.

## Brève description

*80 caractères maximum. C'est la seule ligne visible avant « Plus ».*

Texte : [`fr-FR/short_description.txt`](fr-FR/short_description.txt).

74 caractères. « Clavier luxembourgeois » est en tête, c'est la partie qui
survit à la troncature sur petits écrans. « 100 % hors ligne » a laissé sa
place à « dictée vocale » avec l'arrivée de la dictée LuxASR : il n'est plus
vrai de l'application entière, seulement de la frappe, et la dictée est à la
fois la nouveauté et un mot-clé recherché.

## Description complète

*4000 caractères maximum.*

Texte : [`fr-FR/full_description.txt`](fr-FR/full_description.txt).

Longueurs en unités UTF-16 (c'est ainsi que compte la Play Console, les
drapeaux et quelques émojis valant 2 chacun) : fr 3 405, lb 3 445, de 3 530,
en 3 131, pt 3 167, toutes sous les 4 000.

Le 2026-10-05, pour la 33.1.0 : la section « Dans votre langue » dit où
choisir la langue (en bas des Réglages du clavier), et l'avertissement
d'Android renvoie au « guide de l'application », puisque le Guide n'est plus
un onglet.

Réécrite le 2026-10-04 pour une lectrice qui n'est pas technophile : chaque
section part d'une gêne concrète et répond par un bénéfice en mots de tous les
jours. Le slogan des supports imprimés, « Vous le parlez. Maintenant, vous
l'écrivez. », ouvre le texte, et la dictée vient juste après les suggestions :
ce sont les deux réponses du clavier. Ce qui a changé par rapport à la version
précédente, et pourquoi :

- **Plus de chiffres de fiche technique** (123 297 formes, 27 746 contextes,
  88 883 mots). Ils ne disent rien à la cible et ont été faux longtemps sans
  que personne le voie : la fiche en ligne annonçait encore 8 792 mots et 3 Mo
  le 2026-10-04. Un exemple qui se voit (« Schueb » → « Schueberfouer », le
  même que sur les affiches) les remplace.
- **Plus de jargon** : « diacritiques », « contextes de prédiction »,
  « correcteur orthographique système », « corpus », « Boîte de Leitner »,
  « licence MIT ». La licence reste dans le dépôt (`NOTICE.md`), la fiche dit
  seulement que le code est ouvert.
- **Cinq accents en touche directe**, plus trois : depuis la 29.5.0 la
  disposition par défaut est le Suisse allemand, où `ü`, `ö` et `ä` ont leur
  touche et `é`, `ë` encadrent la barre d'espace. La disposition Luxembourg, en
  option, n'en a que trois ; le texte décrit celle que l'on voit en installant.
- **Le français est présenté comme une option** : la rangée bleue est éteinte
  par défaut depuis la 30.3.0. La reconnaissance, elle, reste toujours active,
  d'où « vos mots français non plus ».
- **« Aucune collecte » a disparu** de la section confidentialité : depuis la
  dictée, la section Sécurité des données déclare l'audio comme collecté et
  partagé (voir plus bas), et la fiche ne doit pas la contredire.
- **Les langues de l'application** (32.0.0) et les traductions du Wierderbuch
  en allemand, anglais et portugais sont nouvelles dans le texte.
- **Aucun tiret cadratin.**

Quatre choses sont **délibérément absentes** de ce texte, contrairement à la
fiche du Klavyé Kréyòl dont elle reprend la structure :

- **aucune citation de presse** : les mentions Canal 10 / Guadeloupe la 1ère
  concernent l'autre application et seraient trompeuses ici ;
- **aucune liste d'auteurs**, le corpus luxembourgeois étant un jeu de données
  agrégé et non une anthologie d'auteurs identifiés ;
- **aucune promesse de saisie glissée** : la section « ce qu'il ne fait pas
  encore » évite les avis 1 étoile de déception, qui pèsent lourd sur une
  fiche à faible volume. Elle dit aussi que la dictée exige une connexion,
  pour la même raison ;
- **aucune taille d'application** : l'APK de release pèse environ 14 Mo
  (33.0.1), mais le Store livre un AAB découpé par appareil, dont la taille de
  téléchargement est plus petite et varie ; la Console l'affiche elle-même.

## Nouveautés de cette version

*500 caractères maximum. La 33.0.0 n'étant jamais passée
en production, le texte annonce aussi la dictée, et le dit en deuxième ligne, pas en
petits caractères : jusque-là rien ne quittait le téléphone, désormais la voix
part à l'Université du Luxembourg quand on dicte.*

Texte de la 34.3.0 : [`fr-FR/changelogs/340300.txt`](fr-FR/changelogs/340300.txt).
C'est celui de la 34.2.0 dont la ligne 📱 annonce maintenant le carnet en album
et l'éventail de la boîte sur tablette. Longueurs : fr 485, lb 496, de 499,
en 462, pt 461, sur 500. Les lignes lb, de et pt sont à faire relire par un
locuteur.

Texte de la 34.2.0, gardé pour mémoire : [`fr-FR/changelogs/340200.txt`](fr-FR/changelogs/340200.txt).
C'est celui de la 34.0.0 précédé d'une ligne 📱 sur les tablettes : clavier
plus grand, jeux à grille et Wierderbuch côte à côte en paysage, et la partie
gardée quand on tourne l'écran (ce dernier point vaut aussi pour les
téléphones). Longueurs : fr 463, lb 472, de 476, en 442, pt 428, sur 500.
Les lignes lb, de et pt sont à faire relire par un locuteur.

Texte de la 34.0.0, gardé pour mémoire : [`fr-FR/changelogs/340000.txt`](fr-FR/changelogs/340000.txt).
C'est celui de la 33.2.1 sans sa ligne 📰 : les actualités de l'INLL, apparues
en 33.2.0 et restées en test fermé, sont retirées de l'application faute
d'autorisation écrite de l'INLL (ses conditions générales interdisent toute
reproduction de ses contenus sans accord préalable). Le code reste en place,
masqué par `ACTUALITES_INLL = false` dans `SettingsActivity`, et le flux mis en
cache par les 33.2.x est effacé au lancement. La politique de confidentialité
(version 3.2) redit que seule la dictée utilise Internet.

Il reprend, raccourcies, les lignes de la 33.1.0 : la dictée, la
confidentialité et le choix de la langue, pour le cas où aucune version avec la
dictée ne serait encore passée en production. Si la 33.1.0 est déjà en
production, ne garder que la ligne 🌍.

Longueurs : fr 310, lb 324, de 317, en 300, pt 296, sur 500.

Texte de la 33.2.1, gardé pour mémoire : [`fr-FR/changelogs/330201.txt`](fr-FR/changelogs/330201.txt).

Texte de la 33.1.0, gardé pour mémoire : [`fr-FR/changelogs/330100.txt`](fr-FR/changelogs/330100.txt).

**À envoyer en même temps que l'AAB qui apporte la dictée, ni avant ni après**, avec la
description et la section Sécurité des données : tant que la version en
production n'a pas la dictée, l'ancienne fiche (« entièrement hors ligne »,
« aucune donnée collectée ») reste vraie pour elle, et la nouvelle deviendrait
fausse.

*Texte précédent, celui de la 26.3.1, gardé pour mémoire :*

*500 caractères maximum. Version de référence : 26.3.1, premier envoi en
production — résume l'été (12.0.0 à 26.3.1) plutôt qu'un seul correctif,
puisque les testeurs fermés n'ont pas vu chaque version intermédiaire passer
sur cette fiche. Une fois en production, revenir à un résumé de la seule
version livrée.*

```
🎴 Un carnet de cartes à collectionner : chaque mot appris devient une carte, avec un relief qu'on sent du bout du doigt.
⚡ Révisez avec la Boîte de Leitner, à intervalle espacé, pour retenir vos mots pour de bon.
📖 Wierderbuch : cherchez un mot en lëtzebuergesch ou en français, dans les deux sens.
🎮 Sept jeux pour apprendre en s'amusant, dont Kräizwuert et Wuertplaz, tout nouveaux.
🔠 Le clavier met tout seul la majuscule aux noms, comme l'exige la langue.
```

463 unités UTF-16 sur 500, marge de 37. Ce champ n'est pas affiché aux
nouveaux visiteurs d'une première publication, mais il l'est à chaque mise à
jour, et l'application étant déjà en test fermé, il est lu par les testeurs
dès maintenant. Les textes des versions antérieures sont dans l'historique
git de ce fichier.

---

## Le reste du formulaire

### Paramètres de la fiche

| Champ | Valeur |
|---|---|
| Catégorie d'application | Outils *(c'est la catégorie des claviers : Gboard, SwiftKey et HeliBoard y sont)* |
| Tags | Clavier · Productivité · Éducation |
| E-mail du développeur | medhi.famibelle@gmail.com |
| Site Web | https://famibelle.github.io/LuxKeyb/ |
| Politique de confidentialité | https://famibelle.github.io/LuxKeyb/privacy/privacy-policy.html |
| Langue par défaut de la fiche | Français (France) — l'interface de l'application est en français |
| Pays de diffusion | Luxembourg, Belgique, France, Allemagne, et diaspora (aucune raison de restreindre) |
| Contenu | Classification IARC : tout public (PEGI 3) ; le questionnaire ne déclenche rien : pas d'achat, pas de pub, pas de contenu généré par l'utilisateur, pas de partage de localisation |
| Public cible | **16 ans et plus** (décision du 1er octobre 2026). Avec des moins de 13 ans dans le public cible, le règlement Familles s'appliquerait à la dictée, qui envoie la voix à un tiers ; 16 ans est l'âge du consentement numérique retenu par le Luxembourg (article 8 du RGPD). À changer dans une mise à jour à part, avant celle qui apporte la dictée |

### Sécurité des données

Le formulaire attend une réponse par catégorie. Depuis la dictée vocale, **une
seule** catégorie passe à oui ; toutes les autres restent à **non**.

| Question de la Console | Réponse |
|---|---|
| Audio › Enregistrements vocaux : collectés ? | **Oui** (au sens de Google, toute donnée qui quitte l'appareil est « collectée », même si elle ne nous parvient jamais) |
| Partagés ? | **Oui**, avec l'Université du Luxembourg (service LuxASR), qui en est responsable de traitement : ce n'est pas un prestataire agissant pour notre compte |
| Traités de façon éphémère ? | **Oui** : transcrits sur le moment, puis effacés |
| Obligatoires ou facultatifs ? | **Facultatifs** : seulement quand l'utilisateur appuie sur le micro |
| Finalité | Fonctionnalité de l'application |
| Chiffrés en transit ? | **Oui** (TLS, `wss://luxasr.uni.lu`) |
| Suppression sur demande ? | Sans objet : rien n'est conservé ; la politique renvoie au DPO de l'Université |

- Mieux vaut déclarer trop que pas assez : une déclaration contredite par
  l'application est la première cause de refus d'une mise à jour.
- Les permissions `RECORD_AUDIO`, `INTERNET` et `ACCESS_NETWORK_STATE`
  n'existent que pour la dictée ; la politique de confidentialité (v3.0) les
  documente une par une. La Console exige en plus, pour l'audio, un écran
  d'information **dans l'application**, montré avant la demande d'accès au
  micro : c'est `MicPermissionActivity`, depuis la 31.0.0 (« J'accepte » /
  « Non merci », rien n'est envoyé avant l'accord).
- Le compteur de progression écrit dans `filesDir`, sur l'appareil, et ne
  compte que des mots déjà présents dans le dictionnaire livré : ni les mots
  de passe, ni les noms propres, ni les numéros n'y entrent. C'est ce point
  que la politique de confidentialité détaille, et il faut qu'elle continue de
  le refléter si `CreoleDictionaryWithUsage` change.
- Le questionnaire pose une question spécifique aux claviers sur la saisie de
  texte : répondre que la saisie n'est ni collectée ni transmise.

### Traductions de la fiche

Une fiche par langue de l'interface (`LangueInterface.Langue`) : français,
luxembourgeois, allemand, anglais, portugais. Le portugais vise la communauté
portugaise du Luxembourg, d'où `pt-PT` et « telemóvel » plutôt que le
brésilien. Le nom ne change pas d'une langue à l'autre : c'est le nom de
l'application.

Chaque traduction reprend les libellés de l'application dans sa langue
(« Tastatureinstellungen », « Definições do teclado », « Astellunge vun der
Tastatur », « Carnet », « Sammlung », « coleção »), pour que ce que promet le
Store se retrouve à l'écran. Le Wierderbuch cite d'abord la langue de la
fiche parmi ses langues de traduction.

À vérifier dans la Console : le luxembourgeois ne figure peut-être pas parmi
les langues de fiche proposées. Dans ce cas, `lb/` reste disponible pour le
site et les supports imprimés, et les visiteurs luxembourgeois voient la
fiche allemande ou française selon la langue de leur téléphone.

**Pour la Console** : Fiche Play Store › Traductions › « Importez un fichier »
prend [`import-console-traductions.txt`](import-console-traductions.txt), qui
rassemble nom, brève description et description complète des quatre
traductions (le français, langue par défaut, se colle à part). La Console en
détecte les langues. Ce fichier est fabriqué par `python3 build_import.py`
depuis les dossiers de langue : le refaire après toute modification d'un texte,
jamais l'éditer à la main.

Les textes `lb/`, `de-DE/` et `pt-PT/` **doivent être relus par un locuteur
natif** avant publication : une faute dans la vitrine d'un clavier
luxembourgeois coûte plus cher qu'ailleurs. L'anglais aussi, dans une moindre
mesure.

---

## Éléments graphiques à fournir

L'icône et l'image de présentation sont dans
[`../graphics/feature-graphic/icone-et-presentation/`](../graphics/feature-graphic/icone-et-presentation/),
les huit captures dans
[`../graphics/feature-graphic/captures/<langue>/`](../graphics/feature-graphic/captures/),
au nom de la langue de la fiche (`fr-FR`, `lb`, `de-DE`, `en-US`, `pt-PT`, les
mêmes noms que les dossiers de textes). `feature-graphic/hors-console/<langue>/` ne part
pas à la Console : ce sont les visuels de jeux, du carnet et de la voix, pour le site. Les noms de fichiers sont les mêmes dans toutes
les langues ; seule la légende incrustée change. Chacun porte le
nom de l'emplacement du formulaire de la Console où il va, il n'y a donc rien à
retrouver au moment de l'envoi ; les captures portent en plus, entre
parenthèses, ce qu'elles montrent. Tous sont fabriqués par
[`../graphics/build_graphics.py`](../graphics/build_graphics.py) à partir des
sources du dépôt (le logo de `Logos/`, les captures réelles de
`graphics/captures-emulateur-pixel9/`) ; `python3 build_graphics.py check` les reconfronte aux
contraintes ci-dessous sans rien refabriquer. Les graphiques créoles dont ils
reprennent le gabarit ne sont plus dans le dépôt.

| Élément | Format exigé | Fichier |
|---|---|---|
| Icône de l'application | 512 × 512 PNG ou JPEG, moins de 1 Mo, sans transparence | `Icône de l'application.png` (243 Ko) — le lion de `Logos/luxembourg-logo-hd.png` aplati sur blanc |
| Image de présentation | 1024 × 500 PNG ou JPEG, moins de 15 Mo, sans transparence | `Image de présentation.png` (114 Ko), source HTML à côté |
| Captures d'écran pour téléphone | 2 à 8, 16:9 ou 9:16, côté entre 320 et 3840 px, moins de 8 Mo pièce | `<langue>/Captures d'écran pour téléphone 1 (Suggestions).png` … `8 (Installation).png`, 1080 × 1920, légende incrustée dans la langue |
| Captures d'écran pour tablette 7 pouces | facultatif, 16:9 ou 9:16, côté entre 320 et 3840 px | `captures-tablette/<langue>/Captures d'écran pour tablette 1 (Suggestions).png` … `6 (Boîte de Leitner).png`, 1920 × 1080 |
| Captures d'écran pour tablette 10 pouces | facultatif, 16:9 ou 9:16, côté entre 1080 et 7680 px | les mêmes six fichiers : 1920 × 1080 respecte les deux bornes |
| Vidéo YouTube | facultatif ; URL YouTube publique ou non répertoriée, sans pub, sans restriction d'âge, intégration autorisée | `../graphics/video/Letzebuergesch_Clavier_presentation_fr.mp4` (50 s, 1920 × 1080, français), à mettre en ligne sur YouTube puis coller son URL. Le Store ne prend **pas** les GIF |

Les huit captures dépassent toutes 1080 × 1080, et il y en a plus de quatre :
les deux conditions que la Console pose pour que l'application soit
promouvable. Leur numéro est leur ordre d'envoi ; le premier écran est le seul
que voit la plupart des visiteurs :

1. la barre de suggestions bilingue en cours de frappe (la cuvette, 12.0.0)
2. la dictée en cours dans Messages, bandeau « 🌐 LuxASR » ; la légende dit
   où part la voix et quand (33.0.0)
3. une carte du carnet ouverte : plaque gravée, rareté, exemple traduit
4. le hub Spiller : sept jeux et le carnet, sur la barre à quatre onglets
5. la fiche Wierderbuch d'un mot : sens, exemples traduits, autres formes
6. les accents : é ä ë ö ü en touche directe, l'appui long sur `e` pour les autres
7. la progression et le mot du jour, avec sa traduction
8. l'installation guidée au premier lancement, avec le champ pour essayer le clavier

Chiffres et symboles ont quitté les huit emplacements à la 33.0.0 pour faire
place à la dictée : la Console n'en accepte pas plus, et c'était l'écran qui
apprenait le moins sur l'application.

Le nom de chaque capture porte maintenant aussi, entre parenthèses, ce qu'elle
montre (le kicker affiché sur l'image elle-même). La liste ci-dessus et le
tableau `SPECS` de
[`../graphics/build_graphics.py`](../graphics/build_graphics.py) restent la
référence si le contenu d'une capture change sans que son nom soit mis à jour.

La légende est incrustée dans l'image parce que la Play Console n'en fournit
pas, et qu'elle aide beaucoup sur ce type d'application, où la valeur n'est
pas lisible d'un coup d'œil.

**Définition.** Les captures sources sont natives, prises sur l'émulateur
`pixel9` (1080 × 2424) ; rien n'est agrandi. Les trois captures de clavier
(suggestions, dictée, accents) sont communes aux cinq langues : le clavier
reste luxembourgeois quelle que soit la langue de l'application. Les cinq
écrans de l'application (carnet, jeux, Wierderbuch, progression, installation)
ont été repris dans chaque langue le 2026-10-05 sous la 33.1.0, dans
`graphics/captures-emulateur-pixel9/<langue>/`. Refaire l'ensemble après tout
changement d'interface visible, puis `python3 build_graphics.py shots`.

Le flyer triptyque A4 (`graphics/flyer-triptyque/`) ne sert pas à la Play
Console : il est là pour l'impression, adapté du flyer créole.

---

## À régler avant le passage en production

L'application est **déjà publiée en test fermé**. Ce qui suit conditionne le
passage en production, pas un premier envoi.

1. ~~**Douze testeurs pendant quatorze jours consécutifs.**~~ Fait. Palier
   atteint le 2026-09-09 (`docs/stats/testeurs.json`, tenu à la main, il
   n'existe pas d'API publique pour ce chiffre), donc les quatorze jours acquis
   dès le 23 ; sortie publique programmée au 2026-09-28, cinq jours de marge
   ajoutés le 2026-09-22 au-delà du minimum Google.

2. **Écrire à <ai@rtl.lu> avant la publication ouverte.** `luxemburgish_cloze.json`
   redistribue 1 600 phrases entières de RTL.lu, mot pour mot : ce n'est plus une
   statistique agrégée mais de la republication de contenu. CC BY-NC l'autorise
   pour un usage non commercial avec attribution visible — les deux conditions
   sont remplies — mais c'est le seul point où un tiers peut dire non, et les
   auteurs indiquent eux-mêmes ce contact. Voir `NOTICE.md`.

3. ~~**Les captures du guide intégré**~~ Fait le 2026-09-22 pour les trois qui
   montraient encore l'ancienne barre de suggestions (`guide_screenshot_suggestions.png`,
   `_accents.png`, `_numeric.png`, commit `7e01334a`) ; les quatre `install_*`
   n'avaient pas besoin d'être refaits.

4. ~~**Les huit captures du Store**~~ Refaites le 2026-09-22 : barre à quatre
   onglets, cuvette de suggestions, Wierderbuch, sept jeux, carnet et Boîte de
   Leitner. À refaire à nouveau au prochain changement d'interface visible.

5. ~~**Relire les chiffres de la description**~~ Sans objet depuis le
   2026-10-04 : la description ne cite plus de chiffres d'actifs ni de taille.
   Relire en revanche ce qu'elle promet (disposition par défaut, rangée
   française, dictée) à chaque changement de comportement par défaut.

Vérifier enfin que l'AAB envoyé est bien signé avec la clé de release : le
build tombe en signature debug avec un simple `println` si un secret manque
(voir le journal du job CI, pas seulement son statut vert).
