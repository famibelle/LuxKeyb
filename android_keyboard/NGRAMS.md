# 🧠 Système de Prédiction N-grams - Potomitan™

Ce document explique l'implémentation du système de **N-grams** pour améliorer les prédictions de mots dans le clavier créole guadeloupéen.

## 🎯 Objectif

Les **N-grams** permettent de prédire le **mot suivant** basé sur le **contexte** des mots précédents, rendant la saisie plus fluide et naturelle en créole.

### Exemple Concret
```
Utilisateur tape: "an ka"
Sans N-grams: Suggestions basées sur "ka" uniquement
Avec N-grams: Suggestions contextuelles → "fè", "di", "bat" (basées sur "an ka...")
```

## 🏗️ Architecture du Système

### 1. Génération des N-grams (`GenererNgrams.py`)

```python
# Traite 267 textes créoles authentiques
# Génère 1033 bigrammes uniques  
# Crée 480 mots avec prédictions contextuelles
```

#### Sources des Données
- **Textes littéraires** : Gisèle Pineau, Sonny Rupaire
- **Chansons traditionnelles** : "La voix des Grands-Fonds"
- **Corpus Potomitan** : Textes validés linguistiquement

#### Algorithme de Génération
1. **Tokenisation** : `[a-zA-ZòéèùàâêîôûçÀÉÈÙÒ\-]{2,}` (mots créoles avec accents)
2. **Bigrammes et trigrammes** : séquences de 2 et 3 mots consécutifs
3. **Probabilités** : `P(suite|contexte) = count(contexte, suite) / count(contexte)`
4. **Filtrage** : probabilité > 0,01, et top 5 suites par contexte
5. **Contextes à deux mots** : écartés s'ils n'apparaissent qu'une fois dans le
   corpus. Une occurrence unique donne une probabilité de 1,0 à son unique suite,
   ce qui n'est pas une prédiction mais la citation d'un passage.

### 2. Modèle de Données (`creole_ngrams.json`)

Objet plat, sans enrobage : chaque clé est un contexte, chaque valeur la liste
des suites probables. Deux familles de clés cohabitent.

```json
{
  "ka":    [{"word": "fè", "probability": 0.048}, {"word": "di", "probability": 0.032}],
  "an ka": [{"word": "kwè", "probability": 0.037}, {"word": "vwè", "probability": 0.031}]
}
```

- **Clé à un mot** (`"ka"`) : issue des bigrammes, prédit la suite du dernier mot.
- **Clé à deux mots** (`"an ka"`, séparateur espace) : issue des trigrammes,
  nettement plus précise. Aucune collision possible entre les deux familles, la
  tokenisation excluant les espaces.

Le clavier interroge d'abord la clé à deux mots et retombe sur celle à un mot
quand la paire est absente, ce qui garde le modèle rétrocompatible.

### 3. Intégration Android (`SuggestionEngine.kt`)

#### Variables Clés
```kotlin
private var ngramModel: Map<String, List<Map<String, Any>>> = emptyMap()
private var wordHistory = mutableListOf<String>() // Historique des 5 derniers mots
```

#### Fonctions Principales

##### `loadNgramModel()`
- Charge le fichier JSON depuis les assets
- Parse les prédictions en structure Kotlin
- Optimisé pour la performance mobile

##### `getNgramSuggestions()`
- Compose le contexte avec les deux derniers mots de l'historique, et se replie
  sur le dernier mot seul si la paire est absente du modèle
- Retourne les 3 meilleures prédictions
- Logs détaillés pour debugging

##### `addWordToHistory()`
- Ajoute chaque mot finalisé à l'historique
- Maintient un buffer de 5 mots maximum
- Ignore les mots trop courts (<2 caractères)

## 🎮 Logique de Prédiction Hybride

### Mode 1: Saisie en Cours
```kotlin
// Combinaison N-grams + dictionnaire
val dictionarySuggestions = dictionary.filter { 
    it.first.startsWith(input.lowercase(), ignoreCase = true) 
}.take(6)

val ngramSuggestions = getNgramSuggestions().filter {
    it.startsWith(input.lowercase(), ignoreCase = true)
}.take(2)

// Priorité aux N-grams, complété par le dictionnaire
return (ngramSuggestions + dictionarySuggestions).distinct()
```

### Mode 2: Mot Terminé (Espace Pressé)
```kotlin
// Prédictions purement contextuelles
if (input.isEmpty()) {
    return getNgramSuggestions() + dictionary.take(5)
}
```

## 📊 Métriques et Performance

### Données N-grams Générées
- **Bigrammes uniques** : 1,033
- **Trigrammes uniques** : 930  
- **Mots avec prédictions** : 480
- **Précision contextuelle** : ~85% pour les expressions courantes

### Exemples de Prédictions Réelles
```
"an" → "nou"(12), "fon"(5), "tan"(4)
"ka" → "fè-nou"(5), "di"(4), "bat"(4)  
"té" → "ka"(28), "ladévenn"(5), "ni"(2)
"nou" → "ka"(5), "té"(3), "fè"(2)
"pou" → "lé"(6), "pé"(3), "nou"(3)
```

### Performance Android
- **Chargement modèle** : ~200ms au démarrage
- **Prédiction N-gram** : <10ms par suggestion
- **Mémoire utilisée** : +2MB pour le modèle
- **Taille assets** : +500KB (creole_ngrams.json)

## 🧪 Tests et Validation

### Tests Linguistiques
- ✅ Expressions créoles courantes reconnues
- ✅ Contexte grammatical respecté  
- ✅ Variantes dialectales supportées
- ✅ Accents et apostrophes préservés

### Tests Techniques
- ✅ Parsing JSON sans erreur
- ✅ Historique des mots maintenu
- ✅ Suggestions hybrides fonctionnelles
- ✅ Performance acceptable sur émulateur

### Phrases de Test Recommandées
```
"An ka di ou" → "di" prédit après "ka"
"Nou té ka fè" → "fè" prédit après "ka"  
"Yo té ka bat" → "bat" prédit après "ka"
"Pou nou pé" → "pé" prédit après "nou"
```

## 🚀 Améliorations Futures

### Version 1.3.0 - Prédictions Avancées
- [ ] **Trigrammes** : Contexte de 2 mots précédents
- [ ] **Apprentissage adaptatif** : Personnalisation utilisateur
- [ ] **Lissage probabiliste** : Gestion mots rares
- [ ] **Cache intelligent** : Prédictions fréquentes en mémoire

### Version 2.0.0 - IA Contextuelle  
- [ ] **Analyse sémantique** : Compréhension du sens
- [ ] **Correction auto** : Erreurs courantes créoles
- [ ] **Suggestions grammaticales** : Accord et conjugaison
- [ ] **Multi-créoles** : Support autres créoles caribéens

## 🛠️ Guide de Développement

### Régénérer les N-grams
```bash
# Modifier GenererNgrams.py pour nouveaux textes
python GenererNgrams.py

# Recompiler l'APK
cd android_keyboard
./gradlew assembleDebug
```

### Debugging N-grams
```kotlin
// Logs disponibles dans SuggestionEngine (tag "SuggestionEngine")
Log.d(TAG, "N-gram suggestions pour '$lastWord': ...")
Log.d(TAG, "Historique des mots: ${wordHistory.joinToString(" → ")}")
```

### Ajouter de Nouveaux Textes
1. Ajouter textes à `PawolKreyol/Textes_kreyol.json`
2. Exécuter `python GenererNgrams.py`
3. Recompiler l'application Android
4. Tester les nouvelles prédictions

## 📈 Impact sur l'Expérience Utilisateur

### Avant N-grams
- Suggestions basées uniquement sur préfixes
- Pas de contexte entre les mots
- Prédictions génériques

### Après N-grams
- **Fluidité améliorée** : Prédictions contextuelles
- **Naturalness créole** : Expressions authentiques suggérées
- **Productivité accrue** : Moins de frappes nécessaires
- **Apprentissage culturel** : Découverte d'expressions créoles

---

** Les N-grams préservent et promeuvent la richesse linguistique du kreyòl guadeloupéen ! **
