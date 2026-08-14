# Pistes d'amélioration du clavier Klavyé Kréyòl

**Date :** 6 août 2026
**Version analysée :** 10.3.2 (`versionCode` 100302)
**Méthode :** lecture du code du service IME actif, du moteur de suggestions, du pipeline dictionnaire et des assets JSON.

Les pistes sont classées du meilleur rapport effort/impact au plus lourd. Chaque constat renvoie au fichier et à la ligne concernés.

---

## 1. Le curseur n'est pas suivi (bug de fond)

**Constat.** `onUpdateSelection()` n'existe que dans `KreyolInputMethodService.kt:1497`, c'est-à-dire la version legacy monolithique. Le service actif, `KreyolInputMethodServiceRefactored.kt`, ne le surcharge pas.

Conséquence : `InputProcessor.currentWord` (`InputProcessor.kt:22`) est un état purement interne, alimenté uniquement par les frappes, et qui ne se resynchronise jamais avec le texte réel du champ de saisie.

**Trois comportements cassés au quotidien :**

| Situation | Ce qui se passe | Pourquoi |
|---|---|---|
| L'utilisateur touche le milieu d'un mot déjà écrit et continue de taper | Aucune suggestion | `currentWord` est vide, le moteur ne reçoit pas le préfixe réel |
| Il efface jusque dans un mot précédent | Aucune suggestion | `handleBackspace()` (`InputProcessor.kt:124`) décrémente un `currentWord` déjà vide |
| Il déplace le curseur ailleurs puis tape | Suggestions sur le mauvais mot | Le préfixe périmé d'avant le déplacement est conservé |

**Correctif proposé.** Surcharger `onUpdateSelection()` dans le service refactorisé, relire le mot entourant le curseur via `getTextBeforeCursor()` / `getTextAfterCursor()`, puis appeler `InputProcessor.updateCurrentWordSilently()` (`InputProcessor.kt:503`, déjà prévue pour ce genre de mise à jour sans cascade d'événements).

**Effort :** faible. **Impact :** élevé, c'est probablement le meilleur rapport de toute la liste.

---

## 2. Aucune préférence clavier n'est exposée à l'utilisateur

**Constat.** Les 7 onglets de `SettingsActivity.kt` sont : Démarrage, Kréyòl an mwen, Mots Mêlés, Mots Mélangés, Mo an Karénaj, Guide, À Propos (`SettingsActivity.kt:731-737`). Aucun onglet ne concerne le clavier lui-même.

Une recherche sur `vibrat`, `haptic`, `sound`, `theme` et `keyHeight` dans `SettingsActivity.kt`, `KeyboardLayoutManager.kt` et le service IME ne renvoie rien. Les seules `SharedPreferences` du projet servent à l'onboarding (`kreyol_onboarding_prefs`) et à la gamification (`kreyol_gamification_prefs`).

**Ce qui manque :**

- Retour haptique à la frappe, avec intensité réglable
- Son de frappe optionnel
- Thème clair / sombre
- Hauteur du clavier ajustable

**Pourquoi ça compte.** Le retour haptique est la première chose que l'on remarque en changeant de clavier. Son absence donne une impression de prototype même quand le moteur de suggestions est bon, ce qui est le cas ici.

**Effort :** moyen (un nouvel onglet plus la lecture des préférences dans `KeyboardLayoutManager`). **Impact :** élevé sur la perception de qualité.

---

## 3. Trois mécanismes déjà à moitié construits qu'il suffit de brancher

### 3.1 Les compteurs d'usage ne servent qu'à l'affichage

`CreoleDictionaryWithUsage` (`gamification/CreoleDictionaryWithUsage.kt:26`) enregistre chaque mot validé via `WordCommitListener`, mais `SuggestionEngine.calculateDictionaryScore()` (`SuggestionEngine.kt:79`) ne connaît que la fréquence issue du corpus. Les deux systèmes ne communiquent pas.

**Proposition.** Ajouter au score un bonus proportionnel à l'usage personnel du mot. Le vocabulaire propre à chaque utilisateur remonterait naturellement dans les suggestions.

Cela reste 100 % local et ne ressemble en rien à de l'IA, ce qui est cohérent avec le positionnement du produit : un clavier simple pour locuteurs natifs.

### 3.2 Les trigrammes sont calculés puis jetés

`Dictionnaires/KreyolComplet.py:375` compte bien les trigrammes du corpus, mais la boucle d'export ligne 386 ne parcourt que les bigrammes. Les trigrammes n'atteignent jamais `creole_ngrams.json`.

Côté application, le problème est symétrique : `getNgramSuggestions()` (`SuggestionEngine.kt:719`) n'utilise que `wordHistory.lastOrNull()`, alors que `MAX_WORD_HISTORY = 5` (`SuggestionEngine.kt:21`). Quatre mots d'historique sur cinq sont maintenus pour rien.

**Proposition.** Exporter les trigrammes depuis le pipeline Python, puis les consommer côté Kotlin. On passerait de « quel mot suit ce mot » à de vraies prédictions contextuelles.

### 3.3 `addWordToDictionary()` n'est appelée nulle part

La fonction existe (`SuggestionEngine.kt:516`) mais aucun appelant ne la référence dans tout le dépôt. Et même si elle était appelée, elle ne persisterait rien : `dictionary` vit uniquement en mémoire et est rechargé depuis les assets à chaque démarrage.

Conséquence : un prénom, un toponyme ou un mot absent du corpus ne sera jamais appris, quel que soit le nombre de fois qu'on le tape.

**Proposition.** Brancher la fonction sur un geste explicite (appui long sur un mot inconnu, par exemple) et persister le dictionnaire personnel sur disque.

---

## 4. Le dictionnaire français est très mince

**Constat.** `french_simple_dict.json` contient 662 mots, face aux 5292 entrées de `creole_dict.json`.

Pour quelqu'un qui alterne les deux langues dans un même message, ce qui est la situation normale en Guadeloupe, le français décroche très vite.

**Proposition.** Passer à quelques milliers de mots français. La priorité kréyòl n'est pas menacée : elle est déjà solidement garantie par `mergeSuggestionsKreyolFirst()` (`SuggestionEngine.kt:392`), qui réserve les positions 1 à 3 au kréyòl, et par le seuil d'activation à 3 lettres.

---

## 5. Points secondaires

### 5.1 Pas de correction automatique à la validation

`handleSpace()` (`InputProcessor.kt:311`) valide le mot courant tel quel. La correction orthographique par distance de Levenshtein existe (`getSpellCorrectionSuggestions()`, `SuggestionEngine.kt:681`) mais reste cantonnée aux suggestions affichées.

Corriger le dernier mot à l'appui sur espace, avec annulation par retour arrière, est une convention que les utilisateurs attendent d'un clavier moderne. À introduire prudemment, car une correction non désirée est très irritante.

### 5.2 Documentation dérivée

`CLAUDE.md` annonce « ~1867 mots » pour `creole_dict.json`, qui en contient en réalité 5292. Le fichier compte par ailleurs 4601 clés de n-grammes. À corriger pour éviter de raisonner sur de mauvais ordres de grandeur.

---

## Ordre d'attaque suggéré

1. Suivi du curseur (§1) : court, structurant, débloque les suggestions dans tous les cas d'édition
2. Bonus d'usage personnel dans le score (§3.1) : quasi gratuit, les données existent déjà
3. Onglet réglages du clavier avec retour haptique (§2) : le plus visible pour l'utilisateur final
4. Trigrammes de bout en bout (§3.2) : touche au pipeline Python et au Kotlin
5. Dictionnaire personnel persistant (§3.3)
6. Élargissement du dictionnaire français (§4)
