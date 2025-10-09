# 🎮 Gamification - Tracking du Vocabulaire Créole

## 📋 Vue d'ensemble

Cette fonctionnalité permet de tracker l'utilisation du vocabulaire créole par l'utilisateur tout en respectant totalement sa vie privée.

## 🔒 Respect de la Vie Privée

**Principe fondamental :** Seuls les mots qui existent dans le dictionnaire créole sont trackés.

### Ce qui est tracké :
- ✅ Compteur d'utilisation pour chaque mot du dictionnaire créole
- ✅ Statistiques anonymes (couverture du dictionnaire, mots favoris)

### Ce qui N'EST PAS tracké :
- ❌ Mots personnels (noms, prénoms)
- ❌ Mots de passe
- ❌ Messages complets
- ❌ Données sensibles
- ❌ Tout mot qui n'est pas dans le dictionnaire créole

## 🏗️ Architecture

### Fichiers créés :

1. **`CreoleDictionaryWithUsage.kt`**
   - Classe principale de gestion du dictionnaire avec compteurs
   - Migration automatique du dictionnaire au premier lancement
   - Sauvegarde par batch (toutes les 10 utilisations) pour performance
   - Filtres de sécurité et vie privée

2. **`WordUsageStats.kt`**
   - Data class pour statistiques par mot
   - Propriétés : word, userCount, frequency
   - Helper : isMastered, isRecentlyDiscovered

3. **`VocabularyStats.kt`**
   - Data class pour statistiques globales
   - Métriques : coverage%, wordsDiscovered, totalUsages, topWords, etc.
   - Système de niveaux de maîtrise (Novice → Légende)

4. **`WordCommitListener.kt`**
   - Interface pour notifier quand un mot est committé

### Fichiers modifiés :

1. **`InputProcessor.kt`**
   - Ajout du `WordCommitListener`
   - Tracking dans `finalizeCurrentWord()` (séparateurs)
   - Tracking dans `processSuggestionSelection()` (suggestions)

2. **`KreyolInputMethodServiceRefactored.kt`**
   - Initialisation de `CreoleDictionaryWithUsage`
   - Connexion du listener de tracking
   - Sauvegarde finale dans `onDestroy()`

## 📊 Structure des Données

### Dictionnaire Original (`creole_dict.json`) :
```json
{
  "bonjou": 450,
  "kréyòl": 89,
  "mèsi": 200
}
```

### Dictionnaire avec Compteurs (`creole_dict_with_usage.json`) :
```json
{
  "bonjou": {
    "frequency": 450,
    "user_count": 127
  },
  "kréyòl": {
    "frequency": 89,
    "user_count": 45
  },
  "mèsi": {
    "frequency": 200,
    "user_count": 64
  }
}
```

## 🎯 Quand un Mot est Tracké

Un mot est considéré comme "committé" (validé) dans les cas suivants :

1. **Séparateur tapé** : Espace, ponctuation (. , ! ? ;), Entrée
2. **Suggestion sélectionnée** : Clic sur une suggestion

## 🚫 Filtres de Sécurité

Les mots sont **ignorés** si :
- Longueur < 3 caractères
- Contiennent des chiffres (possibles codes/mots de passe)
- Contiennent "http", "www", ".com" (URLs)
- Contiennent "@" (emails)
- Ne sont pas dans le dictionnaire créole

## ⚡ Performance

- **Sauvegarde par batch** : Toutes les 10 utilisations de mots
- **Sauvegarde finale** : Dans `onDestroy()` pour changements non sauvegardés
- **Impact UX** : Négligeable, aucun ralentissement perceptible

## 📈 Statistiques Disponibles

### Méthodes de `CreoleDictionaryWithUsage` :

```kotlin
// Incrémenter l'utilisation d'un mot
fun incrementWordUsage(word: String): Boolean

// Obtenir le compteur d'un mot
fun getWordUsageCount(word: String): Int

// Statistiques globales
fun getCoveragePercentage(): Float
fun getDiscoveredWordsCount(): Int
fun getTotalUsageCount(): Int

// Top mots
fun getTopUsedWords(limit: Int = 10): List<WordUsageStats>

// Mots récents
fun getRecentlyDiscoveredWords(limit: Int = 5): List<String>

// Mots maîtrisés (10+ utilisations)
fun getMasteredWordsCount(): Int

// Tout en un
fun getVocabularyStats(): VocabularyStats
```

## 🎮 Niveaux de Maîtrise

Basés sur le pourcentage de couverture du dictionnaire :

| Niveau | Couverture | Emoji |
|--------|------------|-------|
| Novice | < 5% | 🌱 |
| Débutant | 5-20% | 🌿 |
| Intermédiaire | 20-40% | 🌳 |
| Expert | 40-60% | 🏝️ |
| Maître | 60-80% | 👑 |
| Légende | 80%+ | 💎 |

## 🔧 Utilisation dans le Code

### Accéder aux statistiques :

```kotlin
// Dans KreyolInputMethodServiceRefactored
val stats = dictionaryWithUsage.getVocabularyStats()

Log.d(TAG, "Coverage: ${stats.coveragePercentage}%")
Log.d(TAG, "Mots découverts: ${stats.wordsDiscovered}/${stats.totalWords}")
Log.d(TAG, "Niveau: ${stats.masteryLevel.displayName} ${stats.masteryLevel.emoji}")

// Top 5 mots favoris
stats.topWords.take(5).forEach { wordStat ->
    Log.d(TAG, "${wordStat.word}: ${wordStat.userCount}×")
}
```

## 🧪 Tests et Debugging

### Logs à surveiller :

```
🔄 Première utilisation - Migration du dictionnaire...
✅ Migration réussie : 2000 mots transformés
✅ Gamification initialisée avec tracking du vocabulaire
🎮 Mot committé pour tracking: 'bonjou'
✅ 'bonjou' utilisé 1 fois
📊 Coverage: 0.1% (1/2000 mots)
💾 Dictionnaire sauvegardé (10 changements)
```

### Reset des compteurs (debug) :

```kotlin
dictionaryWithUsage.resetAllUserCounts()
```

## 🚀 Prochaines Étapes

### MVP Implémenté ✅
- [x] Backend de tracking
- [x] Migration automatique du dictionnaire
- [x] Sauvegarde par batch
- [x] Filtres de vie privée
- [x] Statistiques complètes

### Futures Améliorations 📋
- [ ] Interface de dashboard (DashboardActivity)
- [ ] Visualisations graphiques (camemberts, histogrammes)
- [ ] Système de badges et réalisations
- [ ] Défis quotidiens
- [ ] Partage de progression

## 📝 Décisions de Design

### Normalisation : NON
- "bonjou" ≠ "bonjòu" (comptés séparément)
- Respecte l'orthographe exacte du dictionnaire

### Mots courts : IGNORÉS
- Mots < 3 lettres ne sont pas trackés
- Réduit le bruit et les faux positifs

### Focus perdu : NON
- Ne compte pas un mot si l'utilisateur quitte sans valider
- Seule validation explicite compte

### Source du commit : NON DIFFÉRENCIÉE
- Pas besoin de distinguer frappe vs suggestion
- Simplifie l'implémentation

### Sauvegarde : PAR BATCH
- Toutes les 10 utilisations + onDestroy
- Impact UX négligeable

## 📚 Références

- Branche : `feature/gamification-word-tracking`
- Package : `com.example.kreyolkeyboard.gamification`
- Fichier dictionnaire : `filesDir/creole_dict_with_usage.json`

---

**🇭🇹 Potomitan Kreyòl Keyboard - Gamification avec Respect de la Vie Privée** 🔒
