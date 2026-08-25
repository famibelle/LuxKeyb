# Pack ASO — Lëtzebuergesch Clavier 10.14.0

Complète [`fichePlayStore.md`](fichePlayStore.md), qui porte les textes à
coller. Ce fichier-ci porte les décisions : le titre, les canaux, le suivi des
installations, les avis. Adapté du pack créole
(`KreyolKeybPlayStore/texts/aso_pack_7.0.7.md` et `aso_pack_9.0.0.md`), dont
les recettes tiennent, mais dont les contenus — presse locale, auteurs,
créolophonie — ne se transposent pas.

`applicationId` : `com.potomitan.luxkeyboard`. L'application **n'est pas encore
publiée** : rien de ce qui suit n'a été mesuré, tout est à vérifier après le
premier envoi.

## Titre (30 caractères maximum)

```
Lëtzebuergesch Clavier
```

22 caractères, identique à `app_name` : le nom sous l'icône et le nom sur le
Store coïncident.

La leçon du pack créole s'applique en partie seulement. Là-bas, le titre
« Klavyé Kréyòl Guadeloupe » ratait le mot que les gens tapent réellement
(« clavier créole ») et il a fallu le réécrire. Ici les deux mots-clés
plausibles ne tiennent pas dans un même titre lisible :

| Requête probable | Où elle est couverte |
|---|---|
| `clavier luxembourgeois` | brève description (en tête) + description complète |
| `Lëtzebuergesch Tastatur` | titre (moitié) + brève description lb/de |
| `luxembourgish keyboard` | brève description en |

La Play Console autorise **un titre par langue**. Tentation : mettre
`Lëtzebuergesch Tastatur` sur les fiches lb et de. À ne pas faire pour le
premier envoi — le nom affiché sous l'icône, lui, ne change pas, et un titre
Store différent du nom de l'application brouille la reconnaissance au moment
précis où il n'y a encore aucune notoriété à exploiter. À rouvrir quand la
Search Console de Play montrera du volume sur « Tastatur ».

## Brève description

Voir `fichePlayStore.md` pour les quatre langues. Le principe : « Clavier
luxembourgeois » ou son équivalent en tête de phrase — c'est la partie qui
survit à la troncature — et « 100 % hors ligne » en fin, qui sert à la fois
d'argument de confidentialité et de mot-clé.

## Liens UTM par canal

Un lien par canal : la Play Console (Acquisition → Analyse des conversions)
montre alors d'où viennent les installations. Le paramètre `referrer` doit
rester encodé (`%3D` pour `=`, `%26` pour `&`), sinon Play le tronque.

Base : `https://play.google.com/store/apps/details?id=com.potomitan.luxkeyboard&referrer=`

| Canal | Valeur de `referrer` |
|---|---|
| WhatsApp | `utm_source%3Dwhatsapp%26utm_campaign%3Dlaunch_lu` |
| Facebook | `utm_source%3Dfacebook%26utm_campaign%3Dlaunch_lu` |
| Reddit r/Luxembourg | `utm_source%3Dreddit%26utm_campaign%3Dlaunch_lu` |
| Presse (RTL, Wort, Paperjam) | `utm_source%3Dpresse%26utm_campaign%3Dlaunch_lu` |
| Hacker News | `utm_source%3Dhackernews%26utm_campaign%3Dlaunch_lu` |
| Instagram / TikTok | `utm_source%3Dsocial_video%26utm_campaign%3Dlaunch_lu` |
| Cours de langue, écoles, INL | `utm_source%3Deducation%26utm_campaign%3Dlaunch_lu` |
| Site GitHub Pages | `utm_source%3Dlanding%26utm_campaign%3Dlaunch_lu` |
| Flyer imprimé (QR code) | `utm_source%3Dflyer%26utm_campaign%3Dlaunch_lu` |

**Les liens émis par l'application sont alignés dessus** depuis le
2026-08-25. Ils portaient `launch10k` — l'objectif créole des 10 000
installations — hérité tel quel ; tout est passé à `launch_lu` pendant qu'aucune
donnée n'était encore accumulée :

| Où | `utm_source` | `utm_campaign` |
|---|---|---|
| `SettingsActivity.shareActivationSuccess()` | `activation_share` | `launch_lu` |
| Bouton « partager l'application », `shareApp()` | `in_app_share` | `launch_lu` |
| Partage d'une carte de niveau, `shareLevelCard()` | `level_share` | `launch_lu` |

La source du partage d'activation est passée de `in_app_share` à
`activation_share` : les deux partageaient le même nom et ne se
distinguaient que par leur campagne, que cet alignement vient justement de
rendre identique.

Le QR code du flyer (`graphics/flyer-triptyque/`) pointe pour l'instant sur
`famibelle.github.io/LuxKeyb`, l'application n'étant pas publiée. Après
publication, le regénérer sur le lien Play tracké `utm_source=flyer` — c'est
le seul canal où la mesure est autrement impossible.

## Le vrai moteur, ce sont les avis

Le classement Play dépend surtout du nombre et de la note des avis, et une
fiche qui part de zéro n'a rien d'autre. Deux mécanismes sont **déjà livrés**
dans l'application, il n'y a rien à coder :

- **Demande d'avis officielle Google (In-App Review)** — `maybeAskForReview()`
  dans `SettingsActivity`. Déclenchée seulement après un usage réel du clavier
  et à partir de la 2ᵉ ouverture de l'application, une fois par installation.
  Elle ne fonctionne **que pour une application installée depuis le Play
  Store** : inutile de la tester sur un APK de développement, le flux est
  silencieusement indisponible.
- **Partage après activation** — carte « 🎉 … ass aktivéiert ! » affichée une
  seule fois, juste après le passage par les réglages système, avec un message
  pré-rédigé et un lien tracké.

Le texte de partage d'une carte de niveau était **resté en créole** —
« An rivé nivo X asi Lëtzebuergesch Clavier ! É vou menm, ki nivo a'w ? »,
hérité du Klavyé Kréyòl et jamais traduit, alors que c'est le seul message que
l'application fait publier par ses utilisateurs. Corrigé le 2026-08-25 :
« Ech sinn um Niveau X am Lëtzebuergesch Clavier ! A du, wéi wäit bass du ? »,
suivi d'une ligne française. Le pied de la carte partagée disait de même
« Klavyé gratui asi Google Play » ; il dit maintenant « Gratis Tastatur um
Google Play ».

Reste, sur cette carte, un fond en dégradé turquoise décrit dans le code comme
« mer des Caraïbes », avec son soleil décoratif : c'est de l'identité
guadeloupéenne sur une carte luxembourgeoise. Le repeindre aux couleurs du
drapeau est un changement visuel, laissé à décider.

Reste la partie humaine : demander un avis aux premiers utilisateurs
convaincus. À 0 avis, cinq avis 5 étoiles déplacent la fiche ; à 500, non.

## Ce que le pack créole avait et que celui-ci n'a pas

- **Aucune couverture presse.** Le créole a eu Canal 10 et le JT de Guadeloupe
  la 1ère en juillet 2026, et la fiche s'en sert comme preuve sociale. Aucun
  équivalent ici, et reprendre ces mentions serait trompeur — l'application
  n'est pas la même. La presse luxembourgeoise (RTL Lëtzebuerg, Luxemburger
  Wort, Paperjam, Reporter.lu) est un canal à travailler, pas un acquis.
- **Aucune liste d'auteurs.** Le corpus luxembourgeois est un jeu de données
  agrégé, pas une anthologie d'auteurs identifiés : rien à citer.
- **Aucun chiffre d'installations, aucune note.** Tout le tableau de bord est
  à zéro tant que l'application n'est pas publiée.

## À vérifier après le premier envoi (accès Play Console requis)

1. Le titre enregistré fait-il bien 22 caractères, non tronqué ?
2. Les fiches lb / de / en sont-elles bien créées avec leur brève description
   relue par un locuteur natif — la fiche française reste servie par défaut
   tant qu'elles manquent ;
3. La déclaration « Sécurité des données » est-elle bien à *aucune donnée
   collectée* (voir `fichePlayStore.md`) ;
4. Le flux In-App Review est-il réellement servi (il ne l'est que sur une
   installation venant du Store) ;
5. Les liens UTM remontent-ils dans Acquisition → Analyse des conversions ?
   Sinon, c'est presque toujours un `referrer` non encodé.
