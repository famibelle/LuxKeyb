# Banc de frappe — Lëtzebuergesch Clavier contre Clavier Samsung et Gboard

*Mesures du 8 septembre 2026. Ce fichier est à la racine du dépôt, comme
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

## Le banc d'émulateur, et pourquoi ses chiffres sont écartés

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

Les scripts et les relevés sont dans `banc-emulateur/` : `bench_tel.py` conduit
la frappe sur téléphone, `bench2.py` sur émulateur, `score.py` lit les captures
par OCR, `compare.py` apparie deux claviers. Ils dépendent des coordonnées de
l'écran et sont à réétalonner sur un autre appareil.

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
