# TODO - CORRECTIONS PRIORITAIRES CLAVIER KREYOL
Basé sur analyse des logs du 12 octobre 2025

## 🔴 CRITIQUE - À FAIRE IMMÉDIATEMENT

### [ ] 1. Corriger ID Package IME dans scripts de test
**Priorité:** HAUTE  
**Temps estimé:** 1 heure  
**Assigné à:** _________  

**Problème:**
Les scripts utilisent `com.example.kreyolkeyboard` mais l'app utilise `com.potomitan.kreyolkeyboard`

**Fichiers à modifier:**
- [ ] `tests/scenarios/test-advanced-input.ps1`
- [ ] `tests/scenarios/test-potomitan-interactive.ps1`
- [ ] `tests/scenarios/level1-basic-keyboard.ps1`
- [ ] `tests/scenarios/level2-advanced-features.ps1`
- [ ] `tests/utils/adb-helpers.ps1`

**Changement à faire:**
```powershell
# AVANT (ligne ~20-30 dans chaque fichier)
$imeId = "com.example.kreyolkeyboard/.KreyolInputMethodServiceRefactored"

# APRÈS
$imeId = "com.potomitan.kreyolkeyboard/com.example.kreyolkeyboard.KreyolInputMethodServiceRefactored"
```

**Tests de validation:**
```powershell
# Vérifier activation via ADB
adb shell ime enable com.potomitan.kreyolkeyboard/com.example.kreyolkeyboard.KreyolInputMethodServiceRefactored
adb shell ime set com.potomitan.kreyolkeyboard/com.example.kreyolkeyboard.KreyolInputMethodServiceRefactored
```

---

### [ ] 2. Corriger gestion des Coroutines Kotlin
**Priorité:** HAUTE  
**Temps estimé:** 3 heures  
**Assigné à:** _________  

**Problème:**
`JobCancellationException` - Coroutines non liées au lifecycle, risque de fuites mémoire

**Fichiers à vérifier/modifier:**
- [ ] `android_keyboard/app/src/main/java/.../KreyolInputMethodServiceRefactored.kt`
- [ ] `android_keyboard/app/src/main/java/.../WordSuggestionEngine.kt`
- [ ] `android_keyboard/app/src/main/java/.../DictionaryLoader.kt`
- [ ] Tous les fichiers utilisant `GlobalScope.launch`

**Changements à faire:**

#### 2.1 Remplacer GlobalScope par lifecycleScope
```kotlin
// AVANT
GlobalScope.launch {
    loadDictionary()
}

// APRÈS
lifecycleScope.launch {
    loadDictionary()
}
```

#### 2.2 Ajouter gestion d'annulation propre
```kotlin
lifecycleScope.launch {
    try {
        withContext(Dispatchers.IO) {
            loadDictionary()
        }
    } catch (e: CancellationException) {
        Log.d(TAG, "Operation cancelled, cleaning up...")
        cleanup()
        throw e // Important: re-throw
    } catch (e: Exception) {
        Log.e(TAG, "Error loading dictionary", e)
    }
}
```

#### 2.3 Utiliser SupervisorJob
```kotlin
class KreyolInputMethodServiceRefactored : InputMethodService() {
    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + supervisorJob)
    
    override fun onDestroy() {
        supervisorJob.cancel()
        super.onDestroy()
    }
    
    // Utiliser 'scope' au lieu de lifecycleScope pour opérations longues
    fun loadData() {
        scope.launch {
            // ...
        }
    }
}
```

**Tests de validation:**
1. Ouvrir/fermer le clavier rapidement 10 fois
2. Vérifier aucune exception dans logcat
3. Utiliser Memory Profiler - vérifier pas de leaks

---

## 🟡 IMPORTANT - À PLANIFIER

### [ ] 3. Améliorer fermeture des canaux d'entrée
**Priorité:** MOYENNE  
**Temps estimé:** 2 heures  
**Assigné à:** _________  

**Problème:**
"Consumer closed input channel" - Derniers événements tactiles perdus

**Fichiers à modifier:**
- [ ] `android_keyboard/app/src/main/java/.../SettingsActivity.kt`
- [ ] `android_keyboard/app/src/main/java/.../KeyboardView.kt`
- [ ] `android_keyboard/app/src/main/java/.../PopupHandler.kt` (si existe)

**Changements à faire:**

#### 3.1 SettingsActivity - Délai avant fermeture
```kotlin
override fun onBackPressed() {
    // Attendre traitement des événements en cours
    Handler(Looper.getMainLooper()).postDelayed({
        super.onBackPressed()
    }, 100) // 100ms suffisant
}
```

#### 3.2 PopupWindow - Vérifier état avant fermer
```kotlin
private fun dismissPopup() {
    if (popupWindow.isShowing) {
        // S'assurer que le InputConnection est prêt
        currentInputConnection?.finishComposingText()
        
        Handler(Looper.getMainLooper()).postDelayed({
            popupWindow.dismiss()
        }, 50)
    }
}
```

#### 3.3 Vérifier flags de fenêtre
```kotlin
// Dans onCreate de PopupWindow
val params = window.attributes
params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
window.attributes = params
```

**Tests de validation:**
1. Ouvrir Settings et fermer rapidement avec Back
2. Faire appui long → popup diacritiques → Back rapide
3. Vérifier aucun warning "Consumer closed" dans logs

---

### [ ] 4. Nettoyer tags de log
**Priorité:** FAIBLE  
**Temps estimé:** 30 minutes  
**Assigné à:** _________  

**Problème:**
Tag `KreyolIME-Potomitan💚` contient emoji, problèmes d'encodage possibles

**Fichiers à modifier:**
- [ ] Rechercher tous les fichiers avec `KreyolIME-Potomitan💚`
- [ ] Remplacer par `KreyolIME-Potomitan`

**Commande de recherche:**
```bash
grep -r "KreyolIME-Potomitan💚" android_keyboard/app/src/
```

**Changement:**
```kotlin
// AVANT
private const val TAG = "KreyolIME-Potomitan💚"

// APRÈS
private const val TAG = "KreyolIME-Potomitan"
```

---

## ✅ OPTIONNEL - Backlog

### [ ] 5. Optimiser accessibilité (TalkBack)
**Priorité:** BASSE  
**Temps estimé:** 4 heures  

**Tâches:**
- [ ] Marquer éléments cachés avec `importantForAccessibility="no"`
- [ ] Ajouter `contentDescription` sur tous les boutons du clavier
- [ ] Tester avec TalkBack activé
- [ ] Vérifier annonces vocales correctes

---

### [ ] 6. Ajouter monitoring en production
**Priorité:** BASSE  
**Temps estimé:** 2 heures  

**Tâches:**
- [ ] Intégrer Firebase Crashlytics
- [ ] Ajouter custom logging pour coroutines
- [ ] Configurer alertes pour erreurs critiques
- [ ] Dashboard de monitoring

---

## 📊 SUIVI

### Sprint 1 (Semaine en cours)
- [ ] Tâche 1: ID Package IME
- [ ] Tâche 2: Coroutines Kotlin
- **Objectif:** Résoudre les 2 problèmes critiques

### Sprint 2 (Semaine prochaine)
- [ ] Tâche 3: Canaux d'entrée
- [ ] Tâche 4: Tags de log
- **Objectif:** Améliorations qualité code

### Sprint 3+ (Futur)
- [ ] Tâche 5: Accessibilité
- [ ] Tâche 6: Monitoring
- **Objectif:** Features additionnelles

---

## ✅ CHECKLIST FINALE (Avant Release)

Avant de publier la prochaine version:

### Tests obligatoires
- [ ] Tous les tests automatisés passent (12/12)
- [ ] Aucune erreur critique dans logcat
- [ ] Aucune fuite mémoire détectée (Memory Profiler)
- [ ] Tests manuels UX (10 scénarios minimum)

### Code review
- [ ] Pas de GlobalScope utilisé
- [ ] Tous les tags de log propres (pas d'emoji)
- [ ] Gestion d'erreurs présente partout
- [ ] Documentation à jour

### Validation
- [ ] Tests sur au moins 3 devices différents
- [ ] Tests avec TalkBack (accessibilité)
- [ ] Tests de performance (charge)
- [ ] Beta test avec 10 utilisateurs minimum

---

## 📝 NOTES

**Créé:** 12 octobre 2025  
**Basé sur:** Analyse de 360 MB de logs  
**Tests effectués:** 12 scénarios avancés  
**Score actuel:** 7.5/10  
**Score cible:** 9.0/10  

**Références:**
- `tests/reports/ANALYSE_LOGS_PROBLEMES.md` - Analyse détaillée
- `tests/reports/RAPPORT_COMPLET_TESTS_AVANCES.md` - Résultats des tests

---

## 🎯 OBJECTIF FINAL

**Atteindre score 9.0/10:**
- Fonctionnalité: 10/10 ✅ (déjà atteint)
- Stabilité: 9/10 (actuellement 8/10) 🔧
- Performance: 9/10 (actuellement 8/10) 🔧
- Code quality: 8/10 (actuellement 6/10) 🔧

**Estimation temps total:** 6-7 heures de développement
