# Guide d'Optimisation Framework Android - Samsung A21s
# Objectif: Réduire l'empreinte mémoire de 50MB vers 30-35MB

## 🎯 PLAN D'OPTIMISATION

### Phase 1: Optimisation des Dépendances (Réduction estimée: 8-12MB)

#### A. Nettoyage build.gradle
```gradle
dependencies {
    // ❌ SUPPRIMER les librairies lourdes
    // implementation 'com.google.android.material:material:1.12.0' // ~8MB
    // implementation 'androidx.multidex:multidex:2.0.1' // ~2MB
    
    // ✅ GARDER uniquement l'essentiel
    implementation 'androidx.core:core-ktx:1.13.1'        // ~3MB
    implementation 'androidx.appcompat:appcompat:1.7.0'    // ~4MB (minimal nécessaire)
    
    // ✅ REMPLACER par versions allégées
    implementation 'androidx.lifecycle:lifecycle-common:2.8.6' // au lieu de runtime-ktx
}
```

#### B. ProGuard Optimisé pour A21s
```gradle
buildTypes {
    release {
        minifyEnabled = true
        shrinkResources = true
        useProguard = true
        proguardFiles 'proguard-a21s-optimize.txt'
    }
}
```

### Phase 2: Optimisation InputMethodService (Réduction: 6-10MB)

#### A. Lazy Loading des Composants
```kotlin
class KreyolInputMethodServiceRefactored : InputMethodService() {
    
    // ✅ Composants en lazy loading
    private val keyboardLayoutManager: KeyboardLayoutManager by lazy {
        KeyboardLayoutManager(this, this).apply {
            // Initialisation légère uniquement
        }
    }
    
    private val suggestionEngine: SuggestionEngine by lazy {
        SuggestionEngine(this, this).apply {
            // Charger dictionnaire seulement si nécessaire
            if (isLowEndDevice()) {
                loadMinimalDictionary()
            } else {
                loadFullDictionary()
            }
        }
    }
    
    // ❌ ÉVITER l'initialisation immédiate
    // private lateinit var keyboardLayoutManager: KeyboardLayoutManager
}
```

#### B. Mode Low-End Spécialisé
```kotlin
private fun isLowEndDevice(): Boolean {
    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)
    
    return activityManager.isLowRamDevice || 
           memInfo.totalMem < 3L * 1024 * 1024 * 1024 || // < 3GB
           activityManager.memoryClass <= 256
}

private fun initializeLowEndMode() {
    // Réduire suggestions à 2 au lieu de 3
    maxSuggestions = 2
    
    // Désactiver animations
    enableAnimations = false
    
    // Cache minimal
    maxCacheSize = 50 // au lieu de 200
    
    // Fréquence monitoring réduite
    memoryCheckInterval = 10000L // 10s au lieu de 5s
}
```

### Phase 3: Optimisation des Vues (Réduction: 8-15MB)

#### A. ViewHolder Pattern pour Clavier
```kotlin
class KeyboardLayoutManager {
    private val viewCache = mutableMapOf<String, TextView>()
    
    private fun createKeyButton(key: String): TextView {
        // ✅ Réutiliser les vues existantes
        return viewCache.getOrPut(key) {
            TextView(context).apply {
                text = key
                // Configuration minimale
                setPadding(8, 8, 8, 8)
                // ❌ Éviter les drawables complexes
                setBackgroundResource(android.R.drawable.btn_default)
            }
        }
    }
}
```

#### B. Suggestions Légères
```kotlin
class SuggestionEngine {
    private val suggestionViews = mutableListOf<TextView>()
    
    private fun createSuggestionView(): TextView {
        return TextView(context).apply {
            // ❌ Pas de background drawable custom
            setBackgroundColor(Color.WHITE)
            // ❌ Pas de animations
            // ❌ Pas de ripple effects
            setPadding(16, 8, 16, 8)
        }
    }
}
```

### Phase 4: Optimisation Mémoire Runtime (Réduction: 5-8MB)

#### A. Gestion Coroutines Optimisée
```kotlin
class KreyolInputMethodServiceRefactored {
    
    // ✅ Scope limité avec cleanup automatique
    private val serviceScope = CoroutineScope(
        Dispatchers.Main.immediate + 
        SupervisorJob() +
        CoroutineExceptionHandler { _, exception ->
            Log.e(TAG, "Coroutine error on A21s", exception)
            // Cleanup automatique en cas d'erreur
            cleanupMemory()
        }
    )
    
    override fun onDestroy() {
        serviceScope.cancel() // ✅ Libération immédiate
        cleanupMemory()
        super.onDestroy()
    }
    
    private fun cleanupMemory() {
        viewCache.clear()
        suggestionCache.clear()
        System.gc() // Force garbage collection sur A21s
    }
}
```

#### B. Monitoring Mémoire Allégé
```kotlin
private fun startLowEndMemoryMonitoring() {
    if (!isLowEndDevice()) return
    
    memoryMonitoringJob = serviceScope.launch {
        while (isActive) {
            val memInfo = getMemoryInfo()
            val usedMemory = memInfo.totalMem - memInfo.availMem
            val memoryPercent = (usedMemory * 100) / memInfo.totalMem
            
            if (memoryPercent > 80) { // Seuil critique A21s
                Log.w(TAG, "🚨 Mémoire critique A21s: ${memoryPercent}%")
                emergencyMemoryCleanup()
            }
            
            delay(30000L) // Check toutes les 30s seulement
        }
    }
}

private fun emergencyMemoryCleanup() {
    // Vider caches non essentiels
    suggestionEngine.clearCache()
    accentHandler.clearCache()
    
    // Forcer GC
    System.runFinalization()
    System.gc()
}
```

### Phase 5: Configuration Build Optimisée

#### A. Proguard A21s Spécialisé
```proguard
# proguard-a21s-optimize.txt

# Optimisations agressives pour A21s
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Supprimer code debug/logging
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Optimisations spécifiques InputMethodService
-keep class * extends android.inputmethodservice.InputMethodService
-keepclassmembers class * extends android.inputmethodservice.InputMethodService {
    public *;
}

# Réduire taille des ressources
-adaptresourcefilenames *.png,*.jpg
-adaptresourcefilecontents **.xml
```

## 📊 RÉSULTATS ATTENDUS

### Réduction Mémoire Estimée:
- **Dépendances optimisées**: -10MB
- **InputMethodService allégé**: -8MB  
- **Vues optimisées**: -12MB
- **Runtime optimisé**: -6MB
- **Build optimisé**: -4MB

### **TOTAL: -40MB → Objectif 50MB → 30-35MB atteint** ✅

### Tests de Validation:
```bash
# Avant optimisation
adb shell "dumpsys meminfo com.potomitan.kreyolkeyboard" | grep "TOTAL"
# Résultat attendu: ~51MB

# Après optimisation  
adb shell "dumpsys meminfo com.potomitan.kreyolkeyboard" | grep "TOTAL"
# Résultat cible: ~32MB
```

## 🚀 PLAN D'IMPLÉMENTATION

1. **Semaine 1**: Optimiser build.gradle et ProGuard
2. **Semaine 2**: Refactorer InputMethodService (lazy loading)
3. **Semaine 3**: Optimiser les vues et layouts
4. **Semaine 4**: Implémenter monitoring mémoire A21s
5. **Semaine 5**: Tests et validation sur vrais A21s

Cette approche devrait réduire significativement l'empreinte mémoire tout en maintenant les fonctionnalités du clavier Kreyòl.