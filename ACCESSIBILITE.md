# Accessibilité : ce qu'il reste à faire

État au 17 août 2026. **Aucun des points listés ici n'est implémenté.** Ce fichier
est la contrepartie technique de [`docs/ergotherapie.html`](docs/ergotherapie.html),
la fiche publique destinée aux ergothérapeutes : cette page annonce des limites,
celle-ci dit ce qu'il faudrait changer pour les lever, où, et comment le vérifier.

Il vit à la racine et non dans `android_keyboard/`, bien qu'il ne parle que du
clavier Android : le filtre de chemins de `build-apk.yml` couvre
`android_keyboard/**`, donc l'y déposer déclencherait un build APK et AAB complet,
pipeline Python du dictionnaire compris, à chaque correction de typo dans un
document qui ne part pas dans l'application.

## Le profil visé

Une personne qui sait le mot mais ne peut pas exécuter les appuis : hémiplégie
après un AVC, spasticité, tremblement, faiblesse, douleur articulaire. Pour elle,
le clavier alphabétique n'est pas le moyen de saisie, c'est un filtre. La saisie
se fait dans la barre de propositions, et le clavier est déjà, sans l'avoir
cherché, un outil d'économie de gestes :

- les propositions créoles apparaissent dès la première lettre (`SuggestionEngine.MIN_WORD_LENGTH = 1`) ;
- un appui sur une proposition écrit le mot, l'espace et la majuscule (`InputProcessor.processSuggestionSelection()`) ;
- les n-grammes proposent le mot suivant sans qu'aucune lettre soit tapée ;
- le comptage d'usage personnel remonte les mots de la personne (`calculateDictionaryScore()`, `USAGE_WEIGHT = 5.0`, plafonné à `MAX_COUNTED_USAGES = 20`).

Tout ce qui suit a donc un seul objectif : rendre la barre de propositions fiable
à viser, et supprimer les blocages sans échappatoire.

## Invariants à ne pas casser

Ces comportements servent ce profil aujourd'hui. Deux d'entre eux existent par
accident (une fonctionnalité absente qui protège), ce qui les rend faciles à
détruire en croyant améliorer le clavier.

| Invariant | Où | Pourquoi il compte |
|---|---|---|
| La suppression n'efface qu'un glyphe, sans répétition automatique | `InputProcessor.handleBackspace()` | Un doigt qui ne se relève pas n'efface pas la phrase. Ajouter une répétition sur appui long serait une régression pour ce profil : si elle est ajoutée un jour, elle doit être désactivable. |
| Les rangées ne se décalent pas pendant la frappe | `frenchRowScroll` passe en `INVISIBLE`, jamais en `GONE` (correctif du 23/07/2026) | Une cible visée reste au même endroit le temps que le geste aboutisse. |
| La lettre sans accent figure en premier dans la popup d'accents | `AccentHandler.createAccentButton(..., isBase = true)` | Un appui long involontaire (relâchement lent) reste récupérable en un appui, sans caractère faux. |
| Les puces de suggestion réagissent au clic, pas à `ACTION_DOWN` | `addSuggestionChip()`, `setOnClickListener` | Poser le doigt sur la mauvaise puce puis glisser en dehors annule. Passer la sélection sur `ACTION_DOWN` supprimerait la seule sortie de secours. |
| Aucune correction automatique | `InputProcessor.handleSpace()` finalise sans réécrire | Le texte affiché est le texte saisi, ce qui compte quand la relecture est difficile. |
| Aucun signal intrusif pendant la frappe | pas de pastille ni d'animation dans la barre de suggestions | Décision déjà prise pour d'autres raisons, mais c'est aussi un acquis d'accessibilité. |

## Prérequis commun aux points 2 à 6

Il n'existe aujourd'hui **aucun écran de réglages du clavier** : `SettingsActivity`
est l'écran d'accueil de l'application, et l'IME ne lit aucune préférence de
comportement. Cinq des sept points ci-dessous en ont besoin. Le travail de
plomberie est donc à faire une seule fois :

- un écran de réglages dédié, ou une section dans `SettingsActivity` ;
- des `SharedPreferences` lues par `KreyolInputMethodServiceRefactored` ;
- une reconstruction du layout à la prise de focus (`onStartInputView`) pour que
  le changement s'applique sans redémarrer le téléphone, ce que personne ne fera.

Tant que cette plomberie n'existe pas, chaque point isolé coûte plus cher qu'il
n'en a l'air. Le faire d'abord, puis ajouter les réglages, est le bon ordre.

## 1. Écart et zone neutre entre les puces de suggestion

**Constat.** C'est le défaut le plus gênant pour ce profil. Contrairement à ce
qu'on suppose, les puces ne sont pas minuscules : avec `setPadding(dpToPx(14), …)`
de part et d'autre, une puce de deux lettres fait environ 42 dp de large et la
hauteur de la rangée, soit 44 dp en portrait (`SUGGESTION_ROW_HEIGHT_DP`), donc
près de 7 mm de côté. Le problème est ailleurs : `setMargins(dpToPx(3), 0, dpToPx(4), 0)`
laisse **7 dp entre deux puces, soit un peu plus d'un millimètre, sans zone
neutre**. Un appui qui manque sa cible ne tombe pas dans le vide, il valide le
mot voisin. Et corriger une mauvaise validation coûte bien plus que le geste
économisé, ce qui annule le bénéfice de toute la barre.

En paysage la rangée tombe à 38 dp (`SUGGESTION_ROW_HEIGHT_LANDSCAPE_DP`) et il
n'y en a plus qu'une (`suggestionRowCount()`).

**Où.** `KreyolInputMethodServiceRefactored.addSuggestionChip()`.

**Quoi.** Deux pistes, la seconde étant préférable :

- élargir les marges à 8 dp et poser un `minWidth` de 56 à 64 dp sur la puce, ce
  qui écarte les cibles sans changer la structure ;
- ou insérer entre les puces un espace **non cliquable** de quelques dp,
  c'est à dire une vraie zone morte où un appui ne fait rien. Ne rien faire est
  ici le bon comportement : l'appui perdu coûte un geste, l'appui sur le mauvais
  mot en coûte cinq.

**Vérification.** Écrire une phrase en visant délibérément à un ou deux
millimètres à côté de chaque puce et compter les validations erronées, avant et
après.

## 2. Vibration désactivable

**Constat.** `performHapticFeedback()` passe
`HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING` : le clavier vibre même
quand l'utilisateur a coupé les vibrations dans les réglages du téléphone, et
l'application n'offre aucun interrupteur. C'est le seul point de cette liste qui
soit un blocage total sans échappatoire, pour une hypersensibilité sensorielle ou
quand la vibration perturbe le geste.

**Où.** `KeyboardLayoutManager.performHapticFeedback()`.

**Quoi.** Retirer le drapeau, ce qui suffit à respecter le réglage système, et
ajouter par dessus un réglage applicatif à trois positions (système, toujours,
jamais).

**Vérification.** Couper les vibrations dans les réglages Android, taper, et
constater le silence.

## 3. Hauteur de touche réglable, et plancher en paysage

**Constat.** `BUTTON_HEIGHT_DP = 48` en portrait (environ 7,6 mm) et
`BUTTON_HEIGHT_LANDSCAPE_DP = BUTTON_MIN_HEIGHT_DP = 32` en paysage (environ
5 mm), sans aucun réglage. 32 dp est nettement sous la cible minimale
recommandée par Android, et inutilisable pour un doigt unique instable ou un
stylet tenu maladroitement.

**Où.** `KeyboardLayoutManager.keyHeightPx()` est le point de passage unique,
donc un seul endroit à modifier.

**Quoi.** Un réglage à trois ou quatre crans (48, 56, 64 dp) appliqué à la
hauteur nominale, et ne pas descendre le plancher paysage sous 44 dp. Attention :
la hauteur totale du clavier est déjà contrainte
(`setAvailableRowsHeight()` puis `coerceIn`), donc agrandir les touches réduit
ce qui reste pour l'application au dessus. C'est un compromis à assumer
explicitement, pas un bug.

**Vérification.** Sur émulateur, les 18 appareils de la campagne multi-appareils
du 16/08/2026, en portrait et en paysage.

## 4. Délai d'appui long réglable

**Constat.** `AccentHandler.LONG_PRESS_DELAY = 500` et
`KeyboardLayoutManager.SPACE_LONG_PRESS_DELAY = 1000` sont fixes. 500 ms est
court quand le relâchement est lent : la popup d'accents s'ouvre alors sans que
ce soit voulu. L'invariant de la lettre de base (voir plus haut) rend l'erreur
récupérable, mais au prix d'un appui supplémentaire à chaque fois, ce qui va
contre tout l'objectif.

**Où.** les deux constantes ci-dessus.

**Quoi.** Un réglage à trois crans (500, 800, 1200 ms) appliqué aux deux.

**Vérification.** Maintenir une touche à accents environ une seconde et constater
qu'aucune popup ne s'ouvre au cran le plus long.

## 5. Nombre de propositions affichées

**Constat.** `MAX_SUGGESTIONS = 5` en interne (3 kréyòl et 2 français) et trois
propositions affichées. Ce réglage suppose une frappe rapide et peu coûteuse :
au delà de trois, on suppose qu'il est plus rapide de continuer à taper que de
lire. Pour ce profil l'arbitrage s'inverse, puisque chaque appui est cher.

**Où.** `SuggestionEngine.MAX_SUGGESTIONS` et la construction des rangées dans
`KreyolInputMethodServiceRefactored`.

**Quoi.** Rendre le nombre affiché configurable (3, 4, 5). Point d'attention : la
rangée est déjà en défilement horizontal, donc une quatrième proposition risque
de sortir de l'écran, ce qui la rend pire qu'inexistante puisqu'elle demande un
geste de défilement. Élargir la liste n'a de sens que si tout reste visible sans
défiler, ce qui interagit directement avec le point 1.

**Vérification.** Compter les appuis nécessaires pour écrire la même phrase à 3
puis à 5 propositions.

## 6. Filtre anti-rebond sur les appuis répétés

**Constat.** Rien dans le code ne filtre deux appuis rapprochés sur la même
touche. L'appui rebondi, erreur classique du tremblement et de la spasticité,
écrit donc bien la lettre en double. Le mot reste retrouvé grâce à Levenshtein,
mais **le texte saisi reste faux** jusqu'à ce qu'une proposition soit touchée : la
personne est obligée de passer par la barre pour que sa phrase soit correcte. Les
aides techniques appellent cette fonction « ignorer les touches répétées ».

**Où.** `KeyboardLayoutManager.addTouchAnimation()` pour l'horodatage de
l'`ACTION_DOWN`, `InputProcessor.processKey()` pour le rejet.

**Quoi.** Ignorer un second appui sur la même touche en dessous d'un seuil
réglable (0, 150, 250, 400 ms), désactivé par défaut. À ne surtout pas activer
par défaut : cela casserait la saisie légitime des lettres doublées.

**Vérification.** Taper volontairement en double et constater qu'une seule lettre
est écrite au seuil actif, deux au seuil nul.

## 7. Apprendre les paires de mots, pas seulement les mots

**Constat.** C'est le point le plus lourd, et celui qui rapporterait le plus. Le
comptage d'usage n'influence que le score **lexical** (`calculateDictionaryScore()`
reçoit `usageCountOf(word)`). Les n-grammes, eux, ne s'adaptent jamais : ils
viennent de `creole_ngrams.json`, construit sur un corpus de **textes
littéraires**. Or la fonction la plus rentable pour ce profil est la proposition
du mot suivant sans taper aucune lettre, et c'est justement celle qui est calée
sur une langue écrite littéraire plutôt que sur des conversations. L'adaptation
personnelle est lexicale là où le besoin est contextuel.

**Où.** `SuggestionEngine.generateContextualSuggestions()`, et
`gamification/CreoleDictionaryWithUsage` pour le stockage.

**Quoi.** Enregistrer les paires réellement validées par la personne dans un
fichier de `filesDir`, sur le modèle de `CreoleDictionaryWithUsage`, et les
mélanger aux n-grammes du corpus avec un poids qui monte avec le nombre
d'observations. Deux précautions : l'accès doit rester `synchronized` (le moteur
lit depuis un thread de fond, l'IME écrit sur le thread principal), et le fichier
doit être borné pour ne pas grossir sans fin.

**Vérification.** Écrire vingt fois la même phrase courte et vérifier qu'elle
finit par se composer entièrement par les propositions.

## Vérifier globalement

Aucun de ces points ne se juge sur une capture d'écran. Trois mesures :

1. **Compter les gestes.** Une phrase que la personne écrit vraiment, saisie
   lettre par lettre puis par les propositions. L'écart est la seule mesure qui
   vaille, et elle est propre à chaque personne. Ne pas publier de moyenne.
2. **Compter les validations erronées** en visant délibérément à côté, avant et
   après le point 1.
3. **Faire tester par un ergothérapeute.** Le classement des sept points ci
   dessus est une hypothèse de développeur, pas un constat de terrain. Un seul
   retour professionnel peut le réordonner entièrement, et c'est le but de la
   fiche publique.

## Hors périmètre de ce document

Ce fichier ne traite que le handicap moteur de la main. D'autres chantiers
d'accessibilité existent et ne sont pas couverts ici :

- **basse vision et cécité** : les touches d'icônes portent bien un
  `contentDescription` (`Supprimer`, `Entrée`, `Majuscule`) et les rangées de
  propositions un libellé textuel KR/FR, donc la langue n'est pas signalée par la
  couleur seule ; mais aucun test réel sous TalkBack n'a été fait, il n'y a aucun
  retour sonore de frappe (pas de `playSoundEffect`), et les hauteurs fixes en dp
  face à des tailles de texte en sp n'ont pas été vérifiées à 200 % de police
  système ;
- **troubles cognitifs et attentionnels** : l'absence de signal intrusif et la
  stabilité du layout jouent déjà en leur faveur, rien n'a été étudié au delà ;
- **aphasie et troubles du langage** : c'est un autre public, servi par les mêmes
  fonctions mais avec des priorités différentes, qui relèvent de l'orthophonie et
  non de l'ergothérapie.
