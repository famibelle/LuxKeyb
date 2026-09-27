# Banc de frappe — Lëtzebuergesch Clavier contre Clavier Samsung et Gboard

*Mesures du 8 septembre 2026, complétées le 20 septembre par Gboard 18.2.4, la vitesse et
la correction automatique (section « Mesures du 20 septembre 2026 » plus bas). Ce fichier
est à la racine du dépôt, comme
`ACCESSIBILITE.md` et `SWIPE-ESPACE.md`, pour ne pas déclencher la construction
de l'APK à chaque correction.*

## Ce qui est mesuré

**La prédiction du mot suivant, sans qu'une seule lettre du mot cherché ait été
tapée.** À chaque frontière de mot d'une phrase, on regarde si le mot réellement
écrit dans le corpus figure parmi les trois premières suggestions affichées.

Ce n'est **pas** la même chose que les 18,8 % publiés sur ParaLux ni que les
18,1 % publiés sur ZLS : ces chiffres-là comptent aussi les positions où
l'utilisateur a déjà entré des lettres, ce qui est beaucoup plus facile. Le
présent banc mesure le cas le plus dur, et ses valeurs absolues ne valent qu'en
comparaison entre les claviers testés.

## Le banc de référence : sur téléphone réel

- **Appareil** : Samsung Galaxy A21s (SM-A217F), Android 12, 2,7 Go de RAM.
- **Claviers** : Clavier Samsung 5.4.85.4, celui d'origine, avec le luxembourgeois
  déjà activé (la barre d'espace affiche « Lëtzebuergesch ») ; Lëtzebuergesch
  Clavier 20.3.0, version release publiée.
- **Corpus** : *Méisproochegen Iwwersetzungskorpus* (ZLS, CC0), 40 phrases tirées
  au sort parmi celles de 8 à 15 mots, 341 positions. Deux séries : 20 phrases
  sans diacritique (166 positions), puis 20 phrases contenant `é` ou `ë`
  (175 positions). `adb shell input text` ne sait pas écrire « ë » : la seconde
  série tape ces lettres sur le clavier lui-même, touche dédiée chez nous, appui
  long puis glissement chez Samsung.
- **Protocole** : les mots sont écrits l'un après l'autre dans un même champ,
  sans jamais rouvrir la session ; la barre d'espace du clavier testé est frappée
  et la barre de suggestions photographiée, une fois stabilisée, puis lue par OCR.
  Casse et diacritiques repliées des deux côtés.

### Qualité des prédictions

Deux séries, la première sur des phrases sans diacritique, la seconde sur des
phrases en contenant, chaque `é` et chaque `ë` étant réellement frappé sur le
clavier testé.

| | positions | barre vide | top-1 | top-3 |
|---|---|---|---|---|
| **Lëtzebuergesch Clavier 20.3.0** | 341 | 11,1 % | 10,0 % | **20,2 %** |
| **Clavier Samsung 5.4.85.4** | 341 | 0,0 % | 7,9 % | 17,6 % |
| *dont série sans diacritique* | *166* | *9,0 / 0,0 %* | *10,8 / 9,0 %* | *21,1 / 16,9 %* |
| *dont série accentuée* | *175* | *13,1 / 0,0 %* | *9,1 / 6,9 %* | *19,4 / 18,3 %* |

**L'écart de 2,6 points n'est pas significatif.** Les deux claviers trouvent le
mot 46 fois ensemble, nous seuls 23 fois, Samsung seul 14 fois ; le khi² de
McNemar sur ces discordances vaut 1,73 pour un seuil de 3,84 à 5 %. Sur la
prédiction pure, en luxembourgeois, les deux moteurs font jeu égal, et il faut
l'écrire ainsi.

Ce qui se voit en revanche sans test statistique :

- **la barre vide** : 0,0 % chez Samsung contre 11,1 % chez nous. Faute de
  contexte, il affiche *de · an · der*, les trois mots les plus fréquents de la
  langue ; nos propres mesures créditent cette stratégie de 2,7 %. Sur nos 38
  positions muettes, il ne trouve le mot que 7 fois ;
- **la fidélité au modèle** : `luxemburgish_ngrams.json`, interrogé hors ligne
  sur les mêmes positions, donne 21,1 % puis 19,4 %, exactement les chiffres
  relevés à l'écran. Rien ne se perd entre l'asset et la barre ;
- **le coût d'un accent**, mesuré en construisant la seconde série : chez nous un
  appui sur une touche dédiée, chez Samsung un appui long sur `e` suivi d'un
  glissement jusqu'à la quatrième case du menu `è é ê ë ē`, soit 1,2 s de geste.
  Le corpus écrit `ë` 142 374 fois et `é` 269 749 fois.

Le remplissage systématique explique une partie des victoires de Samsung :
`huel` → der, `fuer` → der, `Fangeren` → an, toutes obtenues par le même triplet
servi à l'aveugle.

### Mémoire, processeur, encombrement

Mesuré clavier ouvert dans un champ **neutre** (formulaire de contact vide) :
notre application héberge le clavier *et* ses écrans dans un seul processus, si
bien qu'un champ pris dans notre propre application gonflait le relevé de 80 Mo
et d'un facteur cinq sur le processeur, la recherche du Wierderbuch tournant au
même endroit.

| | mémoire (PSS, clavier ouvert) | processeur au repos | processeur par mot | paquet installé |
|---|---|---|---|---|
| **Lëtzebuergesch Clavier 20.3.0** | **105 à 137 Mo** | **0 ms / 30 s** | **886 ms** | **8,4 Mo** |
| **Clavier Samsung 5.4.85.4** | 158 à 171 Mo | 70 ms / 30 s | 1 262 ms | 110,1 Mo |

Le processeur par mot inclut le rendu du clavier à chaque appui, identique des
deux côtés ; il ne mesure pas la seule prédiction.

### Le luxembourgeois chez Samsung, vérifié sur l'appareil

Le catalogue de langues du Clavier Samsung 5.4.85.4 (`res/raw/language.json`,
377 entrées) contient `lb` « Luxembourgish », QWERTZ par défaut, avec prédiction,
texte prédictif, correction automatique et saisie glissée — **mais sans
`spell_check`** sur cette version, là où le catalogue du Galaxy S24 Ultra (One UI
7, 691 entrées) le déclare.

## Mesures du 20 septembre 2026 : Gboard, la vitesse, la correction automatique

Le premier banc comparait notre clavier à Samsung. Celui-ci ajoute **Gboard 18.2.4**, la
version installée sur le téléphone de test, et mesure trois choses de plus : la vitesse, la
correction automatique et la majuscule des noms. Les chiffres du 8 septembre ne changent pas.

### Prédiction : 322 positions, pas 341

Les 40 phrases et les 341 positions sont les mêmes. Deux corrections ont été nécessaires, et
elles portent sur Gboard seul.

- **Gboard apprend ce qu'on lui écrit.** Deux phrases avaient été retapées une dizaine de
  fois pendant la mise au point. Sur ces 19 positions, Gboard retrouve le mot 15 fois (79 %),
  contre 13 % ailleurs. On les écarte pour les trois claviers : 322 positions.
- **Sa meilleure suggestion est au centre.** Le mot juste tombe dans la case du milieu 28
  fois (téléphone), 26 (émulateur, 18.2.4) et 20 (émulateur, 12.4), contre 20, 16 et 15 à
  gauche. Chez nous et chez Samsung, c'est la gauche (34 et 27). Le top-1 de Gboard est donc sa
  case centrale ; le top-3 ne dépend pas de l'ordre.
- **L'OCR lisait l'icône de grille de Gboard comme un mot** (« 88 »), ce qui annulait son top-1
  et lui prenait une case du top-3. Elle est écartée dans `score_final.py`.

| Galaxy A21s, 322 positions | barre vide | top-1 | top-3 |
|---|---|---|---|
| Lëtzebuergesch Clavier 20.3.0 | 11,5 % | 10,2 % | **20,2 %** |
| Clavier Samsung 5.4.85.4 | 0,0 % | 8,1 % | 17,7 % |
| Gboard 18.2.4 | 0,0 % | 6,2 % | 13,4 % |

McNemar (seuil 3,84, top-3) : nous contre Samsung 1,36, **non significatif** ; nous contre
Gboard 9,19 ; Samsung contre Gboard 4,45. En top-1 : nous contre Gboard 5,33.

Sur émulateur (Pixel 7 Pro, Android 16, 3 Go, Gboard avec le luxembourgeois seul) : nous 20,2 %,
Gboard 18.2.4 14,9 %, Gboard 12.4 12,4 %. Notre clavier 26.2.0 y redonne exactement les 11,1 %,
10,0 % et 20,2 % de la 20.3.0 sur téléphone, sur les 341 positions. **Le premier banc d'émulateur
était écarté à cause de ses reposes de contexte ; celui-ci tape chaque lettre sur sa touche, `é`
`ä` `ë` compris, dans une session continue, et ses chiffres se recoupent avec ceux du
téléphone.** Il reste un émulateur : il confirme l'ordre, il ne remplace pas la mesure sur appareil.

### Vitesse et légèreté (scripts `bench_tel_latence.py` et `bench_tel_vitesse.py`)

Dans un champ neutre (Samsung Messages), jamais dans notre application : son processus est
partagé avec le clavier.

| Galaxy A21s | latence (médiane) | CPU par appui | CPU au repos, 30 s | mémoire | taille installée |
|---|---|---|---|---|---|
| Lëtzebuergesch Clavier 26.0.3 | **0 ms** | 40 ms | **10 à 20 ms** | 124 Mo | **9,4 Mo** |
| Clavier Samsung 5.4.85.4 | 41 ms | 31 ms | 230 à 250 ms | 131 Mo | 110,1 Mo |
| Gboard 18.2.4 | 34 ms | **23 ms** | 40 à 80 ms | 119 Mo | 130,5 Mo |

- **Latence** : une vidéo d'écran (`screenrecord`, une image tous les 17 ms autour d'un changement),
  dans laquelle on lit l'instant où une lettre apparaît dans le champ puis celui où la barre
  change. Aucune horloge à synchroniser. Trois répétitions, ordre inversé une fois sur deux : 41, 52
  et 49 mesures. Notre barre change dans la même image que la lettre 88 % du temps, Samsung 40 %,
  Gboard 41 %. Le détecteur compte parfois trop de lettres (16, 23 et 22 pour 15 réelles), ce qui
  gonfle les queues : on ne publie que la médiane.
- **CPU par appui** : `utime + stime` du processus pendant 60 appuis enchaînés, quatre séries. Il
  varie beaucoup chez nous (24 à 58 ms) et chez Samsung (22 à 53 ms), peu chez Gboard (19 à 27 ms).
  **Notre clavier est le moins sobre par appui**, et c'est un défaut à corriger.
- **Mémoire** : PSS du processus après la série. **Elle avait été publiée à 105 à 137 Mo contre 158 à
  171 pour Samsung : cela ne se reproduit pas.** LuxKeyb mesuré au premier essai à 205 Mo l'était avec
  son application ouverte dans le même processus ; après un `am force-stop`, 124 Mo.
- **Temps de traitement d'un appui** (60 appuis enchaînés moins 60 appuis dans une zone vide) : 2 à 11
  ms en moyenne pour les trois, dans le bruit de l'outil. Ne départage rien.

Ce qui n'a pas marché, pour ne pas le refaire : `dumpsys gfxinfo framestats` ne garde presque
aucune image de la fenêtre du clavier (2 images pour 6 appuis) et Android 12 n'y écrit pas
l'instant de l'événement tactile (`OldestInputEvent`) ; les taux d'images ratées ne sont pas
comparables d'un clavier à l'autre (Gboard en dessine deux fois moins) ; le curseur du champ
clignote de plusieurs milliers de pixels toutes les 0,5 s, ce qui noie une lettre si l'on compte
les pixels changés (on suit l'abscisse du dernier pixel sombre) ; LuxKeyb n'a pas d'effet visuel de
toucher détectable, donc on ne peut pas partir de l'appui.

### Correction automatique et majuscule des noms

20 phrases sans diacritique, 186 mots, chaque lettre tapée sur sa touche puis une espace,
réglages par défaut (`bench_tel_correction_touches.py`, `bench_emu_correction.py`). Le texte final du
champ est comparé à ce qui a été tapé.

| | mots remplacés | noms mis en majuscule à raison | majuscules à tort |
|---|---|---|---|
| Lëtzebuergesch Clavier 26.0.3 (téléphone) | 0 sur 186 | 7 sur 40 (18 %) | 0 |
| Clavier Samsung 5.4.85.4 (téléphone) | 2 sur 186 | 37 sur 40 (92 %) | 1 |
| Gboard 18.2.4 (émulateur, luxembourgeois seul) | 2 sur 177 | 31 sur 38 (82 %) | 1 |

- **Notre règle de majuscule est prudente à l'excès.** `SuggestionEngine.contextualCapitalization`
  n'en met une que si le corpus l'a vue après le mot précédent : aucune majuscule à tort, mais 82 %
  des noms manqués. Samsung et Gboard font mieux avec autant de majuscules à tort. C'est un défaut
  mesuré, pas un choix qu'on puisse défendre par le chiffre.
- **Gboard sur le téléphone de test a modifié 86 mots sur 186** (`der` devenu `www`), avec le
  français et le créole guadeloupéen à côté du luxembourgeois. Le même test sur émulateur, avec le
  luxembourgeois seul, n'en change que 2. On ne publie pas le premier chiffre, on le signale : la
  configuration et des appuis synthétiques y sont pour beaucoup, sans qu'on sache lesquels.
- **`input text` contourne la correction.** Avec cette méthode (`bench_tel_correction.py`), Gboard n'a
  presque rien modifié : le mot arrive sans passer par sa saisie ordinaire. Pour mesurer une
  correction, il faut taper les touches.
- Un `h` tapé au centre de sa touche est sorti `s` chez Gboard dans un contexte donné (`schonn` devenu
  `scsonn`), quelle que soit la cadence des appuis : c'est le décodage des points de contact, pas la
  correction automatique.

### Pièges

- Le numéro de série Wi-Fi du téléphone change à chaque connexion : `TEL_SERIE=ip:port`.
- Le Gboard préinstallé d'un AVD `google_apis` n'a pas la signature de Google et refuse la mise à
  jour par l'APK du téléphone. Un AVD `google_apis_playstore` l'accepte ; l'ARM y est traduit, et il
  faut 3 Go de RAM : à 2 Go, Messages est tué et le clavier meurt (`mem-pressure-event`).
- L'AVD Android 16 place le clavier autrement que l'ancien : les coordonnées de touches de LuxKeyb
  y sont fausses (le texte sort en désordre) et sont à relever de nouveau.
- Au démarrage d'un test de correction, le champ doit être vide : un reste de la phrase précédente
  se lit comme des mots « insérés ».
- Quand on tape sur le téléphone de quelqu'un d'autre, on note son état avant (clavier par défaut,
  claviers activés, langues de Gboard, correction automatique) et on le restaure après.

## Le banc d'émulateur du 8 septembre, et pourquoi ses chiffres sont écartés

Une première série a été menée sur AVD (Android 14, rendu logiciel) contre Gboard
12.4 en saisie multilingue LB + FR : LuxKeyb 12,0 % de top-3 et 30,3 % de barres
vides, Gboard 13,1 % et 0,0 %. **Ces chiffres sous-estiment notre clavier** et ne
sont conservés que pour mémoire :

- le banc reposait le contexte dans le champ à chaque mot accentué, faute de
  pouvoir taper « ë » ; Gboard s'en accommode, il relit le champ, alors que notre
  clavier repart sans historique sur une session neuve ;
- sur le téléphone, où la frappe est continue, le même clavier passe à 21,1 % et
  rejoint exactement son propre modèle.

## Deux dettes techniques sorties de ce banc

1. **Le contexte n'est pas reconstruit sur un champ déjà rempli.** Ouvrez un
   champ contenant un brouillon, tapez espace : notre barre reste vide, quand
   Gboard et le Clavier Samsung proposent la suite. Vérifié sur « an der », qui
   donne *Stad · Police · Rue* si la phrase a été frappée dans la session en
   cours, et rien si elle était déjà là.
2. **Le processus est partagé entre le clavier et l'application.** Une recherche
   dans le Wierderbuch pendant que le clavier est ouvert consomme dans le même
   processus, ce qui se voit à la mesure : 216 Mo au lieu de 137, et cinq fois
   plus de processeur.

## Reproduire

Les scripts et les relevés du 8 septembre sont dans `banc-emulateur/` : `bench_tel.py` conduit
la frappe sur téléphone, `bench2.py` sur émulateur, `score.py` lit les captures
par OCR, `compare.py` apparie deux claviers. Ils dépendent des coordonnées de
l'écran et sont à réétalonner sur un autre appareil.

Ceux du 20 septembre, dans le même dossier : `bench_emu_touches.py` (prédiction sur émulateur,
touche par touche), `bench_tel_gboard.py` (prédiction de Gboard sur téléphone),
`score_final.py` (le dépouillement de référence : case du centre de Gboard, icône écartée,
appariements, tests de McNemar), `bench_tel_latence.py` et `bench_tel_vitesse.py` (vitesse, avec
`geometrie_vitesse.json` pour les touches), `bench_tel_correction_touches.py` et
`bench_emu_correction.py` (mots modifiés). Les dossiers de captures (`emu_*`, `tel_*`) et les
vidéos (`lat_*.mp4`) pèsent une vingtaine de Mo et ne sont pas destinés au dépôt.

Points à connaître si l'on refait la manipulation :

- `adb shell input text` **ne passe pas les caractères non ASCII** : `ë` fait
  échouer la commande, et les espaces doivent s'écrire `%s`.
- `adb shell sh -c "…"` **casse le passage d'arguments** : adb recolle les
  arguments avec des espaces et `input` n'en reçoit qu'un. Il faut envoyer la
  séquence entière comme une chaîne unique.
- La fenêtre du clavier **n'apparaît pas dans `uiautomator dump`** : la barre de
  suggestions se lit par capture d'écran et OCR, pas par l'arbre d'accessibilité.
- Un aller-retour adb coûte près d'une seconde : chaque position tient en un seul
  appel, les attentes se faisant sur l'appareil.
