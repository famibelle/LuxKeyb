# État du Projet Clavier Créole Potomitan

## 🎯 Applications Disponibles

### 1. **Application Android** (Fonctionnelle)
**Localisation** : `android_keyboard/`
- ✅ **Code complet** : KreyolInputMethodService.kt (1426 lignes)
- ✅ **Dictionnaire** : `creole_dict.json` (2374 mots)
- ✅ **N-grams** : `creole_ngrams.json` (suggestions contextuelles)
- ✅ **Interface utilisateur** : Clavier virtuel avec suggestions
- ✅ **Fonctionnalités** : Prédiction de mots, correction automatique

**Dictionnaire utilisé** :
```
android_keyboard/app/src/main/assets/creole_dict.json
```

### 2. **Application Flutter** (En développement)
**Localisation** : `clavier_creole/`
- ⚠️ **État** : Projet vide (`main.dart` sans contenu)
- ❌ **Dictionnaire** : Aucun asset configuré
- ❌ **Fonctionnalités** : Non implémentées

## 🔧 Programme Principal Actuel

**Le clavier fonctionne sur l'application Android** qui :

1. **Charge le dictionnaire** depuis `android_keyboard/app/src/main/assets/creole_dict.json`
2. **Utilise les n-grams** depuis `android_keyboard/app/src/main/assets/creole_ngrams.json`
3. **Propose des suggestions** en temps réel basées sur :
   - Correspondance de préfixes
   - Fréquence des mots
   - Contexte (n-grams)

## 📊 Configuration Actuelle

```kotlin
// Dans KreyolInputMethodService.kt
private fun loadDictionary() {
    val inputStream = assets.open("creole_dict.json")  // ← Fichier principal
    // ... chargement du dictionnaire
}
```

## ✅ Conclusion

**Le programme du clavier s'appuie sur** :
- **Dictionnaire principal** : `android_keyboard/app/src/main/assets/creole_dict.json`
- **Suggestions contextuelles** : `android_keyboard/app/src/main/assets/creole_ngrams.json`
- **Plateforme** : Application Android native
- **État** : Fonctionnel et prêt à l'utilisation

L'application Flutter est pour l'instant un projet vide en attente de développement.
