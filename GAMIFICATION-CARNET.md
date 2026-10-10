# Donner au carnet de quoi jouer : séries, sceau, partage

Note du 10 octobre 2026, écrite **avant** toute ligne de code, comme
[`REVISION-CARNET.md`](REVISION-CARNET.md) et pour la même raison : à la racine,
elle ne déclenche pas le build APK que le filtre `android_keyboard/**` de
`build-apk.yml` lancerait.

---

## 1. Ce qui manque

Le carnet a déjà presque tout ce qu'un jeu de cartes possède :
- la pochette qui paie la partie ;
- quatre paliers de rareté lus sur la fréquence ;
- un cadre qui s'orne avec le palier ;
- un dessin sur environ 4 % des cartes ;
- la boîte de Leitner en bois ;
- la montée de boîte quand le mot a été écrit au clavier.

Il lui manque trois choses, et ce sont les trois ressorts les plus forts d'une
collection :

1. **Rien ne se termine.** On accumule sans objectif. Une collection sans
   emplacement vide n'a pas de « encore trois ».
2. **Savoir un mot ne change pas la carte.** Une carte acquise ne se distingue que
   par un `✓` dans la ligne de série, en bas de la marge.
3. **Rien ne se montre.** La seule chose partageable de l'application est la
   carte de niveau de `LuxLevels`.

## 2. Ce que les actifs livrés permettent, mesuré

Avant de promettre une série, il faut savoir si elle peut se finir. Une série
impossible à compléter est pire que pas de série : le joueur croit qu'il lui
manque quelque chose alors que le jeu le lui refuse.

### Zuelwuert ne donne que 18 nombres, pas 101

L'idée de départ était une série « les nombres de 0 à 100 ». **Elle est
infaisable** :
- `ZuelenData.newRound` ne tire que des produits des tables de 2 à 10, soit 37
  valeurs distinctes ;
- le jeu tire d'abord les produits **composés** (de 21 à 99, hors dizaines
  rondes). Il y en a 18, et une manche n'en demande que 10. La seconde passe,
  celle des produits simples, ne s'exécute donc **jamais**, en Facile comme
  ailleurs.

Le carnet ne peut donc recevoir que ces 18 nombres :
`21 24 25 27 28 32 35 36 42 45 48 49 54 56 63 64 72 81`.

Ce sont exactement les 18 orthographes où la règle d'Eifel décide de quelque
chose. Selon que la dizaine garde ou fait tomber le n, la moitié est *Rare*
(`zwanzeg`, `drësseg`, `achtzeg`) et l'autre moitié *Très rare* (`véierzeg`,
`fofzeg`, `sechzeg`, `siwwenzeg`). C'est une série courte, entièrement composée
de cartes distinguées, et qui enseigne une seule règle. C'est un bon format.

Constat annexe, qui n'est pas l'objet de cette note : la KDoc de
`composesSeulement` dit que les produits simples « ont leur place en Facile ».
Le code ne les tire jamais. Il faut soit corriger la KDoc, soit faire de la
place à ces produits en Facile. Si la seconde option est retenue, la série
grandit d'autant.

### Les cartes illustrées : 107 dessins sur 111 sont atteignables

Les dessins sont attribués par lemme dans `luxemburgish_blasons.json` (120 lemmes,
111 dessins distincts, sept dessins partagés entre plusieurs mots :
`horloge` ← Auer, Zäit ; `piece` ← Cent, Euro, Su ; etc.). Un lemme est
**gagnable** si au moins une forme de sa famille peut sortir d'un jeu :
- grilles de Kräizwuert et de Wuertplaz ;
- réponses de Wuertlück ;
- ou réserve de tirage de Wuertsich (3 à 8 lettres), Wuertmix (4 à 10) ou
  Wuertriet (5) : forme du dictionnaire, glose instructive, ni nom propre ni
  fragment, ni mot écarté par `MotsEcartes`.

Résultat :

| | Lemmes | Dessins |
|---|---:|---:|
| Illustrés | 120 | 111 |
| Gagnables | **116** | **107** |
| Jamais atteignables | 4 | 4 |

Les quatre dessins perdus, et pourquoi :

| Lemme | Dessin | Cause |
|---|---|---|
| `Engel` | ange | `MotsEcartes`, religion : jamais proposé |
| `Kierch` | église | `MotsEcartes`, religion : jamais proposé |
| `Paräis` | tour Eiffel | glosé « Paris » : nom propre, hors des tirages |
| `Coronavirus` | virus | 11 lettres, glosé par lui-même : hors de tout vivier |

Quatre lemmes ne se gagnent que sous une forme fléchie (`Pizza` par `Pizzaen`,
`Restaurant` par `Restaurante`, `Su` par `Suen`, `Tunnel` par `Tunnellen`).
C'est sans conséquence : `Armorial.pour` reçoit le lemme, la carte fléchie porte
donc le dessin.

La répartition des 116 lemmes gagnables est saine pour une série :
- 65 communs, 24 peu communs, 17 rares et 10 très rares ;
- 111 d'entre eux sortent d'au moins deux jeux, et seuls 5 d'un seul.

Le script de mesure n'est pas versionné. Il sera réécrit dans
`generate_blasons.py` (section 6), là où cette vérification doit vivre.

## 3. Les séries

Trois séries, toutes **finies, connues d'avance et prouvées complétables** :

| Série | Emplacements | Ce que montre un emplacement vide |
|---|---:|---|
| Les nombres | 18 | le nombre en chiffres, « 56 », jamais en lettres |
| Les enluminures | 107 | la silhouette du dessin, en creux, sans le mot |
| Les sept jeux | 7 | l'emoji et la couleur du jeu |

Quatre règles :

- **Une série compte des dessins, pas des mots.** Gagner `Handy` remplit
  l'emplacement « téléphone ». `Smartphone` et `Telefon` y ajoutent un compteur,
  pas un emplacement. Sans cette règle, la série paraîtrait avoir trois trous
  pour un seul dessin.
- **L'emplacement vide est un indice, jamais la réponse.** « 56 » en chiffres est
  précisément la question que Zuelwuert pose. Une silhouette de vélo dit ce
  qu'il faut chercher, pas comment ça s'écrit.
- **La liste des emplacements est un actif, pas un calcul à l'exécution.**
  `generate_blasons.py` marque les dessins atteignables et refuse de sortir
  si un dessin déclaré ne l'est pas. Un test d'actif rejoue la vérification. Le
  générateur des grilles peut faire disparaître un mot du vivier sans rien
  casser de visible, et la série deviendrait infinissable en silence. C'est la
  même discipline que `CrosswordAssetTest`.
- **La série des nombres est en dur dans le Kotlin**, puisque Zuelwuert ne lit
  aucun actif. Un test vérifie qu'elle coïncide avec ce que `newRound` peut
  tirer. Si quelqu'un ouvre les produits simples en Facile, le test échoue et
  rappelle d'agrandir la série.

**Où.** Dans le carnet, une rangée « Séries » au-dessus de la grille ou du
casier : trois pastilles « 12 / 18 », « 9 / 107 », « 5 / 7 ». Un toucher ouvre la
page de la série, en emplacements, dans le style d'album déjà utilisé sur
tablette (`6c84e330`). Une carte gagnée dans un emplacement s'ouvre comme
partout ailleurs, par `LecteurCartes`.

**Au bout.** Une série complète est célébrée une seule fois, dans la pochette
qui l'a complétée, après la dernière carte : « Série complète : les nombres ».
Elle ne donne aucune récompense matérielle. Ce qui a de la valeur, c'est
d'avoir rempli la page.

## 4. Le sceau : la maîtrise se voit sur la carte

La rareté reste lue sur la fréquence, sans exception. La maîtrise devient un
**second axe**, porté par la carte elle-même et non par la ligne de série :
- **Boîtes 0 à 5** : rien ne change ;
- **Acquise** (boîte 6) : un **sceau de cire rouge** posé sur l'angle bas droit
  de l'illustration, « GELÉIERT » en arc au-dessus d'une coche, sans
  traduction ; sur la vignette, la cire et la coche seules. Dessiné d'après la
  maquette du propriétaire du 10 octobre 2026, qui remplace le premier sceau à
  la couleur du carnet. Le sceau est le même à tous les paliers,
  pour que les deux échelles ne se mélangent pas. Une commune acquise et une
  très rare acquise portent le même sceau sur des cadres différents.

Le sceau ne coûte aucun stockage : `CarteMot.acquise` existe déjà. Il donne enfin
à la boîte de Leitner un résultat qui se collectionne : une page de carnet qui se
couvre de sceaux.

### La plume (accordée par le propriétaire le 10 octobre 2026)

Une seconde marque, une **plume**, distingue la carte qui est montée parce
que le mot a été écrit dans un vrai message (`PreuveDeFrappe`). Aucune application
de cartes ne peut proposer ça. Elle est stockée dans `filesDir`, à côté de
`carnet_vu.json`, et jamais dans les préférences. Les deux questions qu'elle
posait sont tranchées plus bas.

Livrée le 10 octobre 2026. D'abord un second cachet de cire, bleu, en miroir
du sceau ; deux cachets sur l'illustration surchargeaient la carte, et le
propriétaire l'a remplacé le jour même par une **petite plume gravée dans le
métal du cadre** : devant le numéro de série sur la carte ouverte, dans la
marge basse gauche sur la vignette. Une mention d'inventaire, pas un
ornement. La liste des formes vit dans `files/carnet_plumes.json` et ne fait que grandir.
Une limite à connaître : la preuve n'est lue qu'au lancement d'une révision et
sur les seules cartes dues ce jour-là (c'est ainsi que `PreuveDeFrappe`
fonctionne depuis la 22.0.0). Un mot écrit au clavier ne reçoit donc sa plume
qu'à sa prochaine échéance.

Les deux questions :

- **Le stockage.** La plume devrait être mémorisée, alors que `PreuveDeFrappe`
  a été conçu pour que le carnet ne reçoive *que* l'échéance. La règle de
  confidentialité n'est plus en jeu depuis la 26.0.2 : `carnet_cartes` n'est plus
  sauvegardé dans le nuage, et `BackupRulesTest` le garantit. Mais un indicateur
  « ce mot a été tapé » dans les préférences change la nature du fichier. Une
  solution qui respecte la conception d'origine : mémoriser la plume dans
  `filesDir`, à côté de `carnet_vu.json`, qui est déjà le domaine des données de
  frappe.
- **L'annonce.** C'est la question n° 3 restée ouverte dans
  `REVISION-CARNET.md` : dire à l'écran que l'application sait ce qui a été
  tapé. L'onglet de statistiques le fait déjà.

## 5. Partager une carte

Un bouton « Partager » sur la carte ouverte, et non sur les vignettes.

- **L'image** est la carte complète (`CarteCarnet.complete`) dessinée dans une
  bitmap 1080 × 1350, le format portrait de `buildLevelCardBitmap`, sur le fond
  du carnet et avec le nom de l'application en pied.
- **La tuyauterie existe déjà** : `shareLevelCard` écrit la PNG dans
  `cacheDir/images`, la sert par `FileProvider` (`file_paths.xml` couvre ce
  dossier) et passe l'URI en `ClipData`. Le commentaire sur Android 14 explique
  pourquoi ce dernier point n'est pas optionnel. On extrait cette fonction plutôt
  que d'en écrire une seconde.
- **Le texte** : le mot, son sens, et le lien Play Store avec un `utm_source`
  propre (`utm_medium=carte`). Il est dans la langue de l'interface.
- **Rien n'est collecté.** C'est l'utilisateur qui déclenche l'envoi, et la carte
  ne porte que des données de dictionnaire. Le LOD et le corpus ZLS sont en CC0 :
  la phrase et sa traduction peuvent figurer sur l'image.
- **Une carte encore en révision se partage aussi.** Attendre le sceau ferait
  perdre le moment où le joueur en a envie : celui de l'ouverture de la
  pochette.

C'est la proposition qui demande le moins de code pour le plus d'effet hors de
l'application : une carte très rare envoyée sur WhatsApp fait la publicité de
l'application auprès du public visé.

## 6. Plan, fichier par fichier

**Premier lot : séries, sceau, partage.**

| Fichier | Ce qu'il reçoit |
|---|---|
| `Dictionnaires/generate_blasons.py` | La vérification de la section 2, une clé `serie` par dessin atteignable, et un refus de sortir si un dessin déclaré est inatteignable. Les quatre dessins perdus restent dessinés, mais hors série. |
| `carnet/Series.kt` (nouveau) | Les trois séries et leur avancement, calculé depuis `Carnet.cartes`. **Sans `Context`** dans la partie calcul, pour être testable sur la JVM comme `Widderhuelen.kt`. |
| `carnet/CarnetFragment.kt` | La rangée de pastilles, la page d'album d'une série. |
| `carnet/Booster.kt` | La ligne « Série complète » après la dernière carte, une seule fois. Un ensemble `series_celebrees` dans les préférences du carnet, qui ne contient que des identifiants de séries. |
| `carnet/CadreOrne.kt` | Le sceau sur l'agrafe. |
| `carnet/LecteurCartes.kt` | Le bouton Partager. |
| `SettingsActivity.kt` | `shareLevelCard` extrait en une fonction commune. |

Tests :

- `SeriesAssetTest` : chaque dessin de la série des enluminures est gagnable
  selon les règles de vivier des sept jeux, sur les actifs livrés.
- `SeriesTest` : un dessin partagé compte une fois, la célébration ne se
  déclenche qu'une fois, la série des nombres coïncide avec ce que
  `ZuelenData.newRound` peut produire (en rejouant un grand nombre de manches à
  graine fixe).

**La plume** (section 4) entre dans le même lot : `carnet/PreuveDeFrappe.kt`
mémorise les formes promues par le clavier dans un fichier de `filesDir`, et
`CadreOrne.kt` la dessine à côté du sceau.

## 7. Ce qu'on ne fait pas

- **Pas de série « de 0 à 100 »** tant que Zuelwuert ne tire que 18 nombres
  (section 2).
- **Pas de série par champ sémantique** (les huit teintes du blason). Ces séries
  sont ouvertes, avec des centaines de mots chacune, et le classement
  automatique est approximatif de l'aveu même de `generate_blasons.py`. Un
  emplacement mal rangé serait une faute visible.
- **Pas de finition brillante tirée au hasard.** La rareté ne s'invente pas
  (note de classe de `Carnet`), même sous forme cosmétique.
- **Pas de monnaie, pas d'échange de doublons, pas de fabrication.** C'est
  beaucoup de mécanique pour un clavier, et l'utilisatrice de référence n'en
  tirerait rien.
- **Pas de classement entre joueurs.** Rien ne quitte l'appareil.
- **Pas de série de jours consécutifs, pas de rappel insistant**, pour les
  raisons de la section 8 de `REVISION-CARNET.md`.
- **Pas de carte gagnée en tapant un mot au clavier.** La règle est inchangée :
  le clavier fait avancer le calendrier, il ne remplit pas le carnet.

## 8. Ce qui reste à trancher par le propriétaire

1. ~~**La plume**~~ : accordée le 10 octobre 2026, stockée dans `filesDir`
   (section 4).
2. **Zuelwuert en Facile** : corriger la KDoc, ou ouvrir les produits simples, ce
   qui agrandirait la série des nombres (section 2).
3. **Les noms des séries** à l'écran, en luxembourgeois comme les jeux ou dans la
   langue de l'interface. Ma proposition est la langue de l'interface, parce
   qu'une série se lit comme une consigne.
