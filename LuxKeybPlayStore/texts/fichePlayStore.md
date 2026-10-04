# Fiche Play Store — Lëtzebuergesch Clavier

Textes à coller tels quels dans la Play Console (Développer la présence →
Fiche Play Store principale). Version de référence : **33.0.1**
(`versionCode` 330001), la première envoyée en production avec la dictée vocale, `applicationId` `com.potomitan.luxkeyboard`.

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

```
Clavier luxembourgeois : suggestions de mots, touches ë ä é, dictée vocale
```

74 caractères. « Clavier luxembourgeois » est en tête, c'est la partie qui
survit à la troncature sur petits écrans. « 100 % hors ligne » a laissé sa
place à « dictée vocale » avec l'arrivée de la dictée LuxASR : il n'est plus
vrai de l'application entière, seulement de la frappe, et la dictée est à la
fois la nouveauté et un mot-clé recherché.

## Description complète

*4000 caractères maximum.*

```
Vous le parlez. Maintenant, vous l'écrivez.

Lëtzebuergesch Clavier est un clavier Android gratuit et sans publicité, fait pour une seule langue : le lëtzebuergesch.

🛠️ Votre luxembourgeois est un peu rouillé ?
😤 Votre téléphone souligne en rouge tous vos mots ?
🤔 Vous hésitez sur l'orthographe à chaque message ?
➡️ Ce clavier est fait pour vous.

⌨️ LES ACCENTS SOUS LE POUCE

é, ä, ë, ö et ü ont chacun leur touche, directement sur le clavier. Plus besoin de chercher un ë dans un menu caché. Les autres (è, à, ô, ê) restent sous un appui long.

🧠 IL VOUS SOUFFLE LES MOTS

Tapez « Schueb », il propose « Schueberfouer ». Après un espace, il devine le mot qui vient ensuite. Les mots que vous employez souvent remontent d'eux-mêmes.

🎙️ QUAND ÉCRIRE EST TROP LONG, DICTEZ

Appuyez sur le micro et parlez : le texte s'écrit pendant que vous parlez, ponctué, avec les majuscules. La reconnaissance est assurée par LuxASR, de l'Université du Luxembourg. Il faut une connexion Internet.

✍️ IL PARDONNE LES FAUTES DE FRAPPE

Une lettre oubliée, une touche voisine : le bon mot arrive quand même. Écrivez « letzebuergesch » sans accents, il vous propose « lëtzebuergesch ».

✅ FINI LE ROUGE PARTOUT

Activé dans les réglages d'Android, il arrête de souligner vos mots luxembourgeois en rouge, dans WhatsApp, vos SMS ou vos e-mails. Vos mots français non plus.

🇫🇷 UN MOT FRANÇAIS AU MILIEU ?

« ech hunn eng réunion muer » s'écrit sans changer de clavier. Si vous le souhaitez, une seconde rangée vous propose aussi les mots français.

📖 UN DICTIONNAIRE DANS LA POCHE

Le Wierderbuch traduit les mots luxembourgeois en français, allemand, anglais ou portugais, et dans l'autre sens, avec des phrases d'exemple du dictionnaire officiel. Même sans connexion.

🎮 APPRENDRE EN JOUANT

Chaque mot écrit fait monter votre niveau, d'Ufänker 🌍 à Sproochenmeeschter 🧙. Sept jeux vous font chercher, écrire ou replacer des mots : Wuertsich, Wuertmix, Wuertriet, Wuertlück, Zuelwuert, Kräizwuert, Wuertplaz.

🎴 UN CARNET POUR NE PLUS OUBLIER

Chaque mot gagné devient une carte à retourner, avec son sens et un exemple. Le carnet vous les fait réviser au bon moment, jusqu'à ce qu'ils restent.

🌍 DANS VOTRE LANGUE

L'application parle français, allemand, anglais, portugais ou luxembourgeois.

🔒 VOS MOTS RESTENT CHEZ VOUS

• Ce que vous tapez ne quitte jamais votre téléphone
• La dictée envoie votre voix à l'Université du Luxembourg, seulement quand vous appuyez sur le micro et après votre accord ; elle est écrite, pas conservée
• Aucun compte, aucune publicité
• Vos mots de passe, les noms et les numéros ne sont jamais enregistrés
• Code source ouvert, consultable par tous

📱 PARTOUT SUR VOTRE TÉLÉPHONE

• WhatsApp, SMS, e-mail, réseaux sociaux : il marche dans toutes les applications
• Android 5.0 et plus récent, thème clair ou sombre
• Installation guidée en trois étapes, avec un clavier d'essai
• Android affiche un avertissement au moment de l'activer : il le fait pour tous les claviers, c'est normal, l'onglet Guide l'explique

🙋 CE QU'IL NE FAIT PAS ENCORE

Pas d'écriture en glissant le doigt d'une lettre à l'autre. La dictée ne marche pas sans connexion.

💬 UN MOT MANQUE ?

Signalez-le : github.com/famibelle/LuxKeyb. Le dictionnaire s'enrichit à chaque version.
```

3293 unités UTF-16 sur 4 000 (c'est ainsi que compte la Play Console, les
drapeaux et quelques émojis valant 2 chacun), soit 707 de marge.

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

*500 caractères maximum. Texte de la 33.0.1. La 33.0.0 n'étant jamais passée
en production, il annonce aussi la dictée, et le dit en deuxième ligne, pas en
petits caractères : jusque-là rien ne quittait le téléphone, désormais la voix
part à l'Université du Luxembourg quand on dicte.*

```
🎙️ Dictez en luxembourgeois : appuyez sur le micro, parlez, le texte s'écrit pendant que vous parlez, ponctué. La reconnaissance est assurée par LuxASR, de l'Université du Luxembourg.
🔒 Ce que vous tapez ne quitte toujours pas votre téléphone. Seule la dictée envoie votre voix, quand vous appuyez sur le micro.
🍽️ « iessen » (manger) revient dans les suggestions, à côté de « Iessen » (le repas).
🌍 Le carnet donne le sens des mots dans la langue de l'appli.
```

463 unités UTF-16 sur 500, marge de 37.

**À envoyer en même temps que l'AAB 33.0.1, ni avant ni après**, avec la
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

Le Store permet une fiche par langue. Le marché visé est trilingue ; par ordre
de rendement :

| Langue | Nom | Brève description |
|---|---|---|
| Luxembourgeois (lb) | `Lëtzebuergesch Clavier` | `Lëtzebuergesch Tastatur: Wuertvirschléi, ë ä é Tasten, Spriechdiktat` |
| Allemand (de) | `Lëtzebuergesch Clavier` | `Luxemburgische Tastatur: Wortvorschläge, ë ä é Tasten, Sprachdiktat` |
| Anglais (en) | `Lëtzebuergesch Clavier` | `Luxembourgish keyboard: word suggestions, ë ä é keys, voice typing` |

Le nom ne change pas d'une langue à l'autre : c'est le nom de l'application.

Les brèves descriptions luxembourgeoise et allemande **doivent être relues par
un locuteur natif** avant publication — une faute dans la vitrine d'un clavier
luxembourgeois coûte plus cher qu'ailleurs. Les descriptions complètes dans
ces langues restent à écrire ; tant qu'elles manquent, la fiche française
s'affiche par défaut, ce qui n'est pas bloquant.

---

## Éléments graphiques à fournir

Les dix fichiers à envoyer sont dans
[`../graphics/feature-graphic/`](../graphics/feature-graphic/). Chacun porte le
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
| Captures d'écran pour téléphone | 2 à 8, 16:9 ou 9:16, côté entre 320 et 3840 px, moins de 8 Mo pièce | `Captures d'écran pour téléphone 1 (Suggestions).png` … `8 (Installation).png`, 1080 × 1920, de 227 à 301 Ko, légende incrustée |
| Captures tablette | facultatif | Non prévu |
| Vidéo YouTube | facultatif | Aucune. `docs/Screenshots/lux_clavier_demo.gif` n'est pas utilisable : le Store ne prend **pas** les GIF |

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
6. l'appui long sur `e`
7. la progression et le mot du jour, avec sa traduction
8. l'installation guidée, configuration terminée

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

**Définition.** Les captures sources sont natives : les trois captures de
clavier (suggestions, accents, numérique) et celle de l'installation ont été
reprises le 2026-09-22 sur l'émulateur `kreyol_test` (1080 × 2340) sous la
26.3.0, les quatre autres (carte, jeux, fiche, progression) le même jour sur
le même émulateur ; rien n'est agrandi. Les trois premières sont partagées
avec le guide intégré (`res/drawable-nodpi/guide_screenshot_*.png`) : les
refaire là-bas d'abord, puis les recopier ici. Refaire l'ensemble après tout
changement d'interface visible : recapturer dans `graphics/captures-emulateur-pixel9/`,
puis `python3 build_graphics.py shots`.

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
