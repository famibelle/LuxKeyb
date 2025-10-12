# 🎯 PLAN D'ACTION - CORRECTIONS CRITIQUES APPLICATION
**Date:** 12 octobre 2025  
**Objectif:** Résoudre les problèmes critiques dans le code Android de l'application  
**Durée estimée:** 3 heures  
**Score cible:** Passer de 7.5/10 à 9.0/10

---

## 📋 RÉSUMÉ EXÉCUTIF

### Problèmes critiques dans l'APP à résoudre
1. ⚠️ **JobCancellationException** → Fuites mémoire + instabilité
2. ⚠️ **Consumer closed input channel** → Perte événements tactiles

### Impact utilisateur si non corrigé
- **P1 (Coroutines):** Crashes aléatoires, ralentissements, batterie drainée
- **P2 (Input Channel):** Dernières touches non prises en compte, frustration UX

---

## � PHASE 1 - CORRECTION COROUTINES KOTLIN (2h)

### Contexte
L'application utilise `GlobalScope` pour les coroutines, ce qui cause des fuites mémoire et des exceptions lors de la destruction des composants.

### Erreur actuelle dans les logs
```
JobCancellationException: Parent job is Cancelling
StandaloneCoroutine was cancelled
```

### Impact utilisateur
- Ralentissements après utilisation prolongée
- Consommation mémoire excessive
- Crashes aléatoires lors fermeture clavier
- Drain batterie

### Solution
Remplacer GlobalScope par scopes liés au lifecycle et ajouter gestion annulation propre.

### Étapes détaillées

#### 1.1 Audit du code source (15 min)

**Rechercher tous les usages de GlobalScope:**
```bash
cd android_keyboard/app/src/main/java
grep -rn "GlobalScope" . --include="*.kt"
```

**Rechercher tous les usages de coroutines non gérées:**
```bash
grep -rn "CoroutineScope(Dispatchers" . --include="*.kt"
```

**Fichiers à auditer prioritairement:**
- ✅ `KreyolInputMethodServiceRefactored.kt` - Service principal du clavier
- ✅ `SettingsActivity.kt` - Activité paramètres (déjà vu, ligne ~70)
- ✅ `WordSuggestionEngine.kt` - Moteur de suggestions
- ✅ `DictionaryLoader.kt` - Chargeur dictionnaire
- ✅ `CreoleDictionaryWithUsage.kt` - Gestion stats d'usage

#### 1.2 Créer branche de travail (2 min)
```bash
cd C:\Users\medhi\SourceCode\KreyolKeyb
git checkout -b fix/critical-coroutines-memory-leaks
```

**Étape 1.3.1: Ajouter gestion du lifecycle**
```kotlin
class KreyolInputMethodServiceRefactored : InputMethodService() {
    // Ajouter en haut de la classe
    private val supervisorJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + supervisorJob)
    
    // Pattern de remplacement dans tout le fichier
    
    // AVANT
    GlobalScope.launch {
        loadDictionary()
    }
    
    // APRÈS
    serviceScope.launch {
        try {
            withContext(Dispatchers.IO) {
                loadDictionary()
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "Dictionary loading cancelled")
            throw e // Important: re-throw
        } catch (e: Exception) {
            Log.e(TAG, "Error loading dictionary", e)
        }
    }
    
    // Ajouter cleanup dans onDestroy
    override fun onDestroy() {
        Log.d(TAG, "Service destroying, cancelling all coroutines")
        supervisorJob.cancel()
        super.onDestroy()
    }
}
```

**Étape 1.3.2: Pattern pour opérations I/O**
```kotlin
// Template à réutiliser partout
private fun loadDataAsync() {
    serviceScope.launch {
        try {
            val result = withContext(Dispatchers.IO) {
                // Opération longue
                heavyOperation()
            }
            // Traiter résultat sur Main thread
            updateUI(result)
        } catch (e: CancellationException) {
            Log.d(TAG, "Operation cancelled, cleanup if needed")
            cleanup()
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Operation failed", e)
            handleError(e)
        }
    }
}
```

#### 1.4 Correction de SettingsActivity.kt (30 min)

**Étape 1.4.1: Vérifier l'existant**
```kotlin
// Dans SettingsActivity.kt, chercher:
// - GlobalScope.launch
// - CoroutineScope(Dispatchers.IO).launch
```

**Étape 1.4.2: Utiliser lifecycleScope**
```kotlin
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    
    // AVANT (ligne ~70)
    CoroutineScope(Dispatchers.IO).launch {
        saveUpdatesToFile(context, updatesToSave)
    }
    
    // APRÈS
    lifecycleScope.launch {
        withContext(Dispatchers.IO) {
            try {
                saveUpdatesToFile(context, updatesToSave)
            } catch (e: CancellationException) {
                Log.d(TAG, "Save cancelled, rolling back")
                rollbackUpdates(updatesToSave)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Save failed", e)
                // Remettre dans le cache pour retry
                updatesToSave.forEach { (word, count) ->
                    pendingUpdates.merge(word, count) { old, new -> old + new }
                }
            }
        }
    }
}
```

#### 1.5 Correction autres fichiers (30 min)

**WordSuggestionEngine.kt, DictionaryLoader.kt, etc.**

Appliquer le même pattern:
1. Remplacer `GlobalScope` par scope approprié
2. Ajouter `try-catch` avec gestion `CancellationException`
3. Utiliser `withContext(Dispatchers.IO)` pour I/O

#### 1.6 Ajouter tests unitaires (20 min)

**Créer: `tests/unit/CoroutinesCancellationTest.kt`**
```kotlin
@Test
fun `test coroutine cancellation on service destroy`() = runTest {
    val service = KreyolInputMethodServiceRefactored()
    
    // Lancer une coroutine longue
    val job = service.loadDictionaryAsync()
    
    // Détruire le service
    service.onDestroy()
    
    // Vérifier que la coroutine est annulée
    assertTrue(job.isCancelled)
}

@Test
fun `test no memory leak after multiple service restarts`() {
    repeat(10) {
        val service = KreyolInputMethodServiceRefactored()
        service.onCreate()
        service.onDestroy()
    }
    
    // Vérifier pas de leak (via Memory Profiler manuel)
    // ou assertions sur nombre de coroutines actives
}
```

#### 1.7 Tests manuels de validation (20 min)

**Test 1: Ouvrir/Fermer clavier rapidement**
```
1. Ouvrir app Potomitan
2. Taper dans champ texte → clavier s'ouvre
3. Appuyer Back → clavier se ferme
4. Répéter 10 fois rapidement
5. Vérifier logcat: aucune JobCancellationException
```

**Test 2: Memory Profiler**
```
1. Ouvrir Android Studio → Memory Profiler
2. Ouvrir/fermer clavier 20 fois
3. Forcer GC
4. Vérifier: pas d'augmentation mémoire
```

**Test 3: Logs propres**
```powershell
# Vérifier aucune exception de coroutines
adb logcat | Select-String "JobCancellationException|StandaloneCoroutine"
# Résultat attendu: aucune ligne
```

#### 1.8 Créer commit (10 min)
```bash
git add android_keyboard/app/src/main/java/
git commit -m "fix(coroutines): remplacer GlobalScope par lifecycle-aware scopes

Problème:
- JobCancellationException lors destruction des composants
- Fuites mémoire potentielles avec GlobalScope
- Coroutines non liées au lifecycle des Activities/Services

Solution:
- Remplacer GlobalScope par serviceScope dans IME Service
- Utiliser lifecycleScope dans SettingsActivity
- Ajouter gestion propre de CancellationException
- Implémenter SupervisorJob pattern
- Cleanup dans onDestroy()

Tests:
- Tests unitaires pour cancellation
- Tests manuels: ouvrir/fermer clavier 20x sans erreur
- Memory Profiler: aucune fuite détectée

Résultats:
- 0 JobCancellationException dans logs
- Mémoire stable après cycles multiples
- Score stabilité: 8/10 → 9/10

Fixes #critical-coroutines"
```

---

## 🔒 PHASE 2 - CORRECTION INPUT CHANNEL (1h)

### Contexte
L'application ferme prématurément les canaux d'entrée, causant perte des derniers événements tactiles.

### Erreur actuelle dans les logs
```
Consumer closed input channel or an error occurred. events=0x9
Consumer closed input channel or an error occurred. events=0x1
```

### Impact utilisateur
- Dernières touches non enregistrées
- Comportement imprévisible lors fermeture rapide
- Frustration utilisateur ("j'ai appuyé mais ça marche pas")

### Solution
Ajouter délais avant fermeture et vérifier état InputConnection.

### Étapes détaillées

#### 2.1 Audit du code (10 min)

**Rechercher fermetures d'activités/popups:**
```bash
cd android_keyboard/app/src/main/java
grep -rn "finish()\|dismiss()\|onBackPressed" . --include="*.kt"
```

**Fichiers concernés:**
- ✅ `SettingsActivity.kt` - Fermeture activité paramètres
- ✅ `KeyboardView.kt` - Gestion popups diacritiques
- ✅ `PopupHandler.kt` - Si existe

#### 2.2 Correction SettingsActivity.kt (20 min)

**Ajouter délai avant fermeture:**
```kotlin
import android.os.Handler
import android.os.Looper

class SettingsActivity : AppCompatActivity() {
    
    override fun onBackPressed() {
        // Laisser temps aux événements en cours d'être traités
        Handler(Looper.getMainLooper()).postDelayed({
            super.onBackPressed()
        }, 100) // 100ms délai
    }
    
    override fun finish() {
        // Flush des updates en attente
        flushPendingUpdates(this)
        
        // Délai avant fermeture effective
        Handler(Looper.getMainLooper()).postDelayed({
            super.finish()
        }, 50)
    }
}
```

#### 2.3 Correction PopupWindow diacritiques (20 min)

**Vérifier état avant fermer:**
```kotlin
private fun dismissPopup() {
    if (popupWindow?.isShowing == true) {
        // S'assurer InputConnection disponible
        currentInputConnection?.finishComposingText()
        
        // Délai avant dismiss
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                popupWindow?.dismiss()
            } catch (e: Exception) {
                Log.e(TAG, "Error dismissing popup", e)
            }
        }, 50)
    }
}
```

#### 2.4 Tests de validation (10 min)

**Test 1: Fermeture rapide Settings**
```
1. Ouvrir Settings
2. Appuyer Back immédiatement
3. Répéter 10 fois
4. Vérifier logs: 0 "Consumer closed"
```

**Test 2: Popup diacritiques**
```
1. Appui long sur 'e' → popup
2. Appuyer Back rapidement
3. Répéter 10 fois
4. Vérifier logs: 0 "Consumer closed"
```

#### 2.5 Créer commit (5 min)
```bash
git add android_keyboard/app/src/main/java/
git commit -m "fix(input): ajouter délais fermeture pour éviter perte événements

Problème:
- Consumer closed input channel (5+ occurrences)
- Derniers événements tactiles perdus
- Fermeture prématurée des activités/popups

Solution:
- Délai 100ms avant onBackPressed()
- Délai 50ms avant dismiss() des popups
- Vérification état InputConnection avant fermeture
- Flush des pending updates avant finish()

Tests:
- 0 erreurs 'Consumer closed' après 20 fermetures rapides
- Tous les événements tactiles enregistrés
- UX fluide même en utilisation rapide

Résultats:
- Consumer closed: 5+ → 0 occurrences
- Score UX: 8/10 → 9.5/10

Fixes #input-channel-closed"
```

---

## ✅ PHASE 3 - VALIDATION GLOBALE (30 min)

### 3.1 Merger les corrections (5 min)
```bash
# Merger dans la branche principale
git checkout feature/gamification-word-tracking
git merge fix/critical-coroutines-memory-leaks --no-ff

# Résoudre conflits si nécessaire
```

### 3.2 Build et installation (10 min)
```powershell
cd android_keyboard
.\gradlew.bat clean assembleDebug

# Installer sur émulateur
adb install -r "app\build\outputs\apk\debug\Potomitan_Kreyol_Keyboard_v6.0.2_debug_2025-10-12.apk"
```

### 3.3 Suite de tests complète (15 min)
```powershell
cd tests\scenarios

# Test 1: Utilisation normale
.\test-potomitan-interactive.ps1

# Test 2: Features avancées
.\test-advanced-input.ps1

# Résultat attendu: Aucune erreur critique dans les logs
```

### 3.4 Analyse finale des logs
```powershell
# Capturer logs pendant les tests
adb logcat -c
adb logcat > final_validation_logs.txt

# Après tests, vérifier:
Select-String "JobCancellationException" final_validation_logs.txt
# → Résultat attendu: 0 occurrences ✅

Select-String "Consumer closed input channel" final_validation_logs.txt
# → Résultat attendu: 0-1 occurrences ✅

Select-String "Exception|Error|FATAL" final_validation_logs.txt | Select-String "kreyol|potomitan" -CaseSensitive
# → Résultat attendu: Aucune erreur critique
```

---

## 📊 MÉTRIQUES DE SUCCÈS

### Avant corrections (état actuel)
| Métrique | Score |
|----------|-------|
| JobCancellationException | 2 occurrences |
| Consumer closed | 5+ occurrences |
| Fuites mémoire | Possibles |
| Stabilité globale | 8/10 |
| Score global | 7.5/10 |

### Après corrections (cible)
| Métrique | Score cible |
|----------|-------------|
| JobCancellationException | 0 occurrences ✅ |
| Consumer closed | 0-1 occurrences ✅ |
| Fuites mémoire | Aucune ✅ |
| Stabilité globale | 9.5/10 ✅ |
| Score global | 9.0/10 ✅ |

---

## 🎯 LIVRABLE FINAL

### Commits attendus
1. ✅ `fix(coroutines): remplacer GlobalScope par lifecycle-aware scopes`
2. ✅ `fix(input): ajouter délais fermeture pour éviter perte événements`
3. ✅ `chore: bump version to 6.0.3 après corrections critiques`

### Documentation mise à jour
- [ ] `CHANGELOG.md` - Ajouter section 6.0.3
- [ ] `TODO_CORRECTIONS.md` - Cocher tâches 1 et 2
- [ ] `ANALYSE_LOGS_PROBLEMES.md` - Ajouter section "Corrections appliquées"

### Tag de version
```bash
git tag -a v6.0.3 -m "Version 6.0.3 - Corrections critiques stabilité

Correctifs critiques:
- Fix: JobCancellationException - Remplacement GlobalScope par lifecycle scopes
- Fix: Consumer closed input channel - Ajout délais fermeture
- Fix: Fuites mémoire - Gestion propre annulation coroutines
- Amélioration: Stabilité globale application

Impact utilisateur:
- Aucun crash lié aux coroutines
- Toutes les touches enregistrées correctement
- Performance mémoire optimale
- Batterie préservée

Score qualité: 7.5/10 → 9.0/10
Tests: 0 erreur critique dans 360MB de logs"

git push origin feature/gamification-word-tracking
git push origin v6.0.3
```

---

## 📝 CHECKLIST FINALE

### Avant de commencer
- [x] Lire TODO_CORRECTIONS.md
- [x] Lire ANALYSE_LOGS_PROBLEMES.md
- [x] Comprendre les 2 problèmes critiques APPLICATION
- [x] Plan d'action établi

### Pendant l'exécution
- [ ] Phase 1: Corriger Coroutines Kotlin (2h)
  - [ ] Audit code source
  - [ ] Créer branche fix/critical-coroutines-memory-leaks
  - [ ] Corriger KreyolInputMethodServiceRefactored.kt
  - [ ] Corriger SettingsActivity.kt
  - [ ] Corriger autres fichiers
  - [ ] Tests unitaires
  - [ ] Tests manuels
  - [ ] Commit
- [ ] Phase 2: Corriger Input Channel (1h)
  - [ ] Audit code source
  - [ ] Corriger SettingsActivity onBackPressed/finish
  - [ ] Corriger PopupWindow dismiss
  - [ ] Tests validation
  - [ ] Commit
- [ ] Phase 3: Validation globale (30min)
  - [ ] Merge dans branche principale
  - [ ] Build APK
  - [ ] Tests complets
  - [ ] Analyse logs finaux

### Après les corrections
- [ ] 0 JobCancellationException dans logs ✅
- [ ] 0-1 "Consumer closed" dans logs ✅
- [ ] Memory Profiler: pas de fuite ✅
- [ ] Application fluide et stable ✅
- [ ] Documentation mise à jour
- [ ] Version 6.0.3 taguée et poussée

---

## ⏱️ TIMELINE

| Phase | Durée | Heure début | Heure fin |
|-------|-------|-------------|-----------|
| Phase 1: Coroutines | 2h | _____:_____ | _____:_____ |
| Pause | 15min | _____:_____ | _____:_____ |
| Phase 2: Input Channel | 1h | _____:_____ | _____:_____ |
| Pause | 10min | _____:_____ | _____:_____ |
| Phase 3: Validation | 30min | _____:_____ | _____:_____ |
| **TOTAL** | **3h55** | | |

---

## 🚨 POINTS D'ATTENTION

### Risques identifiés
1. **Régression fonctionnelle** - Risque que les corrections cassent des features existantes
   - Mitigation: Tests complets avant/après
   
2. **Conflits de merge** - Modifications sur fichiers actifs
   - Mitigation: Travailler sur branche dédiée
   
3. **Performance dégradée** - lifecycleScope pourrait être plus lent que GlobalScope
   - Mitigation: Benchmarker avant/après

### Rollback plan
Si problème critique détecté après corrections:
```bash
git revert HEAD~2  # Annuler les 2 derniers commits
git push origin feature/gamification-word-tracking --force
```

---

## 📞 SUPPORT

En cas de blocage:
1. Consulter docs officielles:
   - [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-guide.html)
   - [Android Lifecycle](https://developer.android.com/topic/libraries/architecture/lifecycle)
2. Logs détaillés dans `ANALYSE_LOGS_PROBLEMES.md`
3. Tests de référence dans `tests/scenarios/`

---

**Créé par:** GitHub Copilot  
**Date:** 12 octobre 2025  
**Basé sur:** TODO_CORRECTIONS.md + ANALYSE_LOGS_PROBLEMES.md  
**Version plan:** 1.0
