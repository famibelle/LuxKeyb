# Glisser sur la barre d'espace : l'état de l'art, la décision, et le plan

Note du 3 septembre 2026, écrite **avant** toute ligne de code, à la suite d'une
observation de terrain : un utilisateur a été vu tentant de glisser le doigt sur
la barre d'espace de LuxKeyb. Le geste ne fait rien aujourd'hui.

Ce fichier vit à la racine et non dans `android_keyboard/`, pour la même raison
que [`ACCESSIBILITE.md`](ACCESSIBILITE.md) : le filtre de chemins de
`build-apk.yml` couvre `android_keyboard/**`, donc l'y déposer déclencherait un
build APK + AAB complet, pipeline Python compris, à chaque correction de typo.

---

## 1. Lever l'ambiguïté d'abord

« Alterner entre les claviers » recouvre deux fonctions qui n'ont rien à voir :

- **Changer d'IME** — quitter LuxKeyb pour Gboard, SwiftKey, le clavier Samsung.
  C'est déjà fait, par l'appui long d'une seconde sur l'espace :
  `SPACE_LONG_PRESS_DELAY = 1000L` (`KeyboardLayoutManager.kt:97`) puis
  `showKeyboardPicker()` (`KreyolInputMethodServiceRefactored.kt:1466`).
- **Changer de langue ou de disposition à l'intérieur du clavier** — ce que font
  SwiftKey et Samsung. **Nous n'avons rien à alterner** : une seule disposition
  QWERTZ, et les modes `123` et emoji ont déjà leurs propres touches en rangée 4.

Autrement dit, la moitié du besoin est couverte et l'autre moitié n'existe pas
chez nous. Ce qui reste à décider, c'est à quoi affecter le geste libre.

## 2. Ce que fait le marché

| Clavier | Glissement horizontal sur l'espace | Changement de langue / clavier |
|---|---|---|
| **Gboard** | **Déplacer le curseur**, caractère par caractère. Activé par défaut, réglage *Saisie gestuelle › Contrôle du curseur par geste* | Touche globe : appui court = langue suivante, appui long = liste |
| **SwiftKey** | **Au choix de l'utilisateur** : curseur ou langue, réglage *Clavier et saisie › Touches et barre d'espace › Comportement de la barre d'espace* | Glissement sur l'espace, si réglé ainsi |
| **Clavier Samsung** | **Changement de langue**, réglable (« touche de langue » ou « balayage de la barre d'espace ») | idem |
| **iOS** | Appui **long** : tout le clavier devient un pavé tactile pour le curseur | Touche globe |
| **AOSP, OpenBoard, HeliBoard, FlorisBoard** | Curseur (héritage LatinIME) | Globe, ou appui long sur l'espace |

**Le cas SwiftKey est le plus instructif.** Ils avaient historiquement le
changement de langue sur ce geste, ont ajouté le contrôle du curseur en 2020, et
n'ont **pas tranché** : ils ont créé un réglage. Les deux usages se disputent le
même geste et aucun n'a pu déloger l'autre. Gboard a tranché dans l'autre sens :
le curseur prend l'espace, la langue va sur le globe.

Conséquence directe pour nous : le geste que la majorité des utilisateurs
Android attendent en glissant sur une barre d'espace est **le curseur**, parce
que c'est le défaut de Gboard, donc de la plupart des téléphones.

Sources : [Gboard, contrôle du curseur par geste](https://www.hardreset.info/devices/apps/apps-gboard/enable-gesture-cursor-control/) ·
[SwiftKey, plusieurs langues](https://support.microsoft.com/en-us/swiftkey-keyboard/how-to-use-microsoft-swiftkey-keyboard-with-more-than-one-language) ·
[SwiftKey adopte le curseur sur l'espace, 2020](https://www.androidpolice.com/2020/09/14/swiftkey-beta-finally-gets-gboard-style-cursor-control/) ·
[Samsung, méthode de changement de langue](https://www.youtube.com/watch?v=xhiZZymp-tU)

## 3. La décision

**Le glissement horizontal déplace le curseur. Le changement d'IME reste sur
l'appui long.** Trois raisons, dans l'ordre de leur poids :

1. **C'est le geste attendu.** Voir ci-dessus. L'utilisateur observé venait très
   probablement d'un clavier où ce glissement bouge le curseur.
2. **Ça comble un manque réel.** Aujourd'hui, pour replacer le curseur au milieu
   d'un mot, il faut viser du doigt dans le texte. Rien dans le clavier n'aide.
   C'est exactement la difficulté décrite dans `ACCESSIBILITE.md`, et l'ajout
   sert donc deux publics d'un coup.
3. **Mettre la sortie de l'application sur un glissement serait dangereux.** Le
   commentaire de `showKeyboardPicker()` dit déjà pourquoi le sélecteur est
   affiché plutôt qu'une bascule directe : « un changement silencieux d'IME
   après un simple appui d'une seconde surprend l'utilisateur, qui peut se
   retrouver sur un autre clavier sans comprendre pourquoi ». Un glissement est
   bien plus facile à déclencher par accident qu'un appui d'une seconde. Notre
   utilisatrice de référence éjectée sans le vouloir vers Gboard ne saurait pas
   revenir.

**Variante de repli**, si le changement de clavier sur glissement est un jour
réclamé : le poser sur un **glissement vertical vers le haut** depuis l'espace.
Direction distincte, quasi impossible à déclencher en frappant, et qui laisse
l'horizontale au curseur. Ne pas le mettre sur l'horizontale « en plus ».

## 4. Ce qui est déjà en place, et qui rend le travail petit

Trois choses réduisent fortement le coût, et aucune n'était évidente avant
lecture du code :

- **La resynchronisation du mot courant existe déjà.**
  `InputProcessor.syncWordWithCursor()` (`InputProcessor.kt:465`) est appelée par
  `onUpdateSelection()` (`KreyolInputMethodServiceRefactored.kt:1073`) et relit
  le mot avant le curseur depuis l'`InputConnection`, quelle que soit la cause du
  déplacement. C'était le vrai piège du sujet : sans elle, le moteur continuerait
  de proposer des complétions pour un préfixe périmé après chaque déplacement.
  Son propre commentaire cite le cas « tap dans le texte » ; un glissement n'est
  qu'un tap de plus.
- **Le clavier ne compose pas.** Aucun `setComposingText` dans le projet — les
  touches font `commitText` directement. Toute la classe de bugs « le curseur
  bouge pendant qu'un mot est en composition » disparaît.
- **La barre d'espace a déjà son `OnTouchListener` dédié**, `setupSpaceLongPress()`
  (`KeyboardLayoutManager.kt:731`), sans `OnClickListener` concurrent — celui-ci
  a été retiré volontairement pour éviter le double espace, voir le commentaire
  de `setupButtonInteractions()`. Il n'y a rien à démonter, juste une branche à
  ajouter. Le parent est un `LinearLayout` et non un conteneur défilant, donc pas
  besoin de `requestDisallowInterceptTouchEvent`.

## 5. Le plan, fichier par fichier

| Fichier | Travail | Ordre de grandeur |
|---|---|---|
| `KeyboardLayoutManager.kt` | branche `ACTION_MOVE` dans `setupSpaceLongPress()`, seuil `ViewConfiguration.get(context).scaledTouchSlop`, pas exprimé en dp, annulation du minuteur d'appui long dès le seuil franchi, drapeau « un glissement a eu lieu » | ~60 lignes |
| `KeyboardLayoutManager.KeyboardInteractionListener` | une méthode `onSpaceCursorMove(steps: Int)` (l'interface est à `KeyboardLayoutManager.kt:138`, un seul implémenteur) | 1 ligne + implémentation |
| `KreyolInputMethodServiceRefactored.kt` | délégation vers `InputProcessor` | ~10 lignes |
| `InputProcessor.kt` | `moveCursor(steps: Int)` par `sendDownUpKeyEvents(KEYCODE_DPAD_LEFT / KEYCODE_DPAD_RIGHT)` | ~20 lignes |
| `app/src/test/` | la fonction pure de calcul des pas | ~40 lignes |

**Le point à ne pas rater** : supprimer l'insertion de l'espace sur `ACTION_UP`
quand un glissement a eu lieu (`KeyboardLayoutManager.kt:769`, le
`if (!isSpaceLongPressTriggered) interactionListener?.onKeyPress(key)`). Sans
cette garde, chaque déplacement de curseur laisse une espace derrière lui. C'est
une ligne, et c'est celle qui rend la fonction utilisable ou inutilisable.

**Pourquoi `sendDownUpKeyEvents` et non `setSelection`** : `setSelection` exige
une position absolue dans le champ, que l'IME ne connaît pas de façon fiable — il
faudrait passer par `getExtractedText`, plus lourd et pris en défaut par les
champs qui ne l'implémentent pas. Les touches directionnelles laissent
l'application gérer ses propres bornes et son propre découpage. C'est ce que font
les claviers du marché.

**Sur la forme du calcul des pas** : l'extraire en `internal fun` dans le
`companion object`, testable sur la JVM, suit une convention déjà établie ici —
`resolveCurrentWord`, `trailingWordLength` et `calculateBackspaceLength`
(`InputProcessor.kt:35-55`) sont exactement ça. Il n'y a pas d'`androidTest` dans
ce projet, `testDebugUnitTest` est toute la suite, donc c'est le seul moyen de
tester quoi que ce soit de ce geste.

## 6. Les pièges connus

1. **La distance par caractère.** La barre pèse 4 unités sur 12 (`getKeyWeight()`,
   `KeyboardLayoutManager.kt:1014`), soit environ un tiers de la largeur d'écran.
   Trop court par caractère et le curseur part en vrille ; trop long et on ne
   traverse pas un mot d'un geste. Se règle à l'œil sur téléphone, en quelques
   itérations. **En dp, jamais en px** — sinon le comportement change avec la
   densité et entre portrait et paysage.
2. **Le rafraîchissement des suggestions pendant le glissement.**
   `syncWordWithCursor` appelle `setCurrentWord()`, qui déclenche `onWordChanged`
   et donc une régénération des suggestions, à chaque fois que le mot avant le
   curseur change. Sur un balayage rapide, une requête par mot franchi. Si ça
   saccade : suspendre pendant le geste et recalculer une seule fois au
   relâchement, ~15 lignes de plus. À mesurer avant d'écrire, pas l'inverse.
3. **Les champs qui n'honorent pas les touches directionnelles.** WebView et
   éditeurs maison de certaines messageries. À vérifier dans trois ou quatre
   applications réelles.
4. **Le son de frappe part déjà sur `ACTION_DOWN`** (`KeyFeedback.onKeyPress`,
   appelé à `KeyboardLayoutManager.kt:741`). Un glissement fera donc entendre le
   son de l'espace sans insérer d'espace. Acceptable — c'est un accusé de
   réception tactile — mais à constater plutôt qu'à découvrir.
5. **Ne pas vibrer à chaque pas.** Un retour haptique par caractère franchi rend
   le geste désagréable et vide la batterie.
6. **L'annulation automatique de majuscule.** `revertAutoCapitalization()`
   (`InputProcessor.kt:407`) se déclenche sur le retour arrière suivant
   immédiatement une espace. Un glissement qui supprime l'insertion de l'espace
   ne doit pas laisser cet état armé.

## 7. Estimation

- Code : **2 à 3 heures**.
- Vérification sur appareil et réglage de la sensibilité : **autant**. L'historique
  de ce projet dit que le temps passé sur l'émulateur — désinstallation pour cause
  de signature, préférences d'onboarding à écrire à la main, champ à toucher deux
  fois pour qu'il prenne le focus — dépasse souvent le temps de code.
- Plus une ligne de `CHANGELOG.md` et une phrase dans `docs/guide.md` :
  l'utilisatrice de référence ne découvrira pas ce geste toute seule.

Aucune migration, aucun asset, aucune garde CI, aucun risque sur la base de fusion
partagée avec KreyolKeyb : ce sont des ajouts en place dans quatre fichiers déjà
partagés, qui gardent la forme du code amont.

## 8. Ce qui a été écarté, pour ne pas y revenir

- **Le glissement horizontal qui change d'IME** — voir §3, raison 3.
- **Un réglage à la SwiftKey pour choisir entre curseur et clavier** — l'app n'a
  qu'une disposition, il n'y aurait rien à mettre dans la seconde branche du
  réglage. Et `SettingsActivity.kt` fait 4 900 lignes ; y ajouter du code pour
  une alternative vide est le contraire de ce qu'il faut faire.
- **`setSelection` avec `getExtractedText`** — voir §5.
