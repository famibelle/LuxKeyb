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

**Attention à la campagne.** L'application émet déjà ses propres liens, et ils
ne portent pas ce nom de campagne :

| Où | `utm_source` | `utm_campaign` |
|---|---|---|
| `SettingsActivity.shareActivationSuccess()` | `in_app_share` | `activation_success` |
| Bouton « partager l'application » (≈ ligne 2671) | `in_app_share` | `launch10k` |
| Partage d'une carte de niveau (≈ ligne 3417) | `level_share` | `launch10k` |

`launch10k` est l'objectif créole (10 000 installations), hérité tel quel.
Deux options, aucune n'est urgente : garder `launch10k` partout pour ne pas
éclater les rapports, ou remplacer les trois occurrences par `launch_lu` — un
`sed` sur `SettingsActivity.kt` — avant le premier envoi, pendant qu'aucune
donnée n'est encore accumulée. La seconde est plus propre ; c'est celle que
supposent les liens ci-dessus.

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

⚠️ **Le texte de partage d'une carte de niveau est resté en créole.**
`shareLevelCard()` (`SettingsActivity.kt`, ≈ ligne 3414) envoie « An rivé nivo
X asi Lëtzebuergesch Clavier ! É vou menm, ki nivo a'w ? » — hérité du Klavyé
Kréyòl et jamais traduit. C'est le seul message que l'application fait publier
par ses utilisateurs sur les réseaux : à corriger avant toute campagne, une
phrase suffit (« Ech si beim Niveau X am Lëtzebuergesch Clavier ! A du, wéi
wäit bass du ? »). Hors du périmètre de ce dossier, mais bloquant pour lui.

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
