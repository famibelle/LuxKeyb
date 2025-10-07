package com.example.kreyolkeyboard

import android.content.Context
import android.util.Log

/**
 * Tests de l'AccentTolerantMatching
 * À exécuter sur l'émulateur pour valider le fonctionnement
 */
class AccentTolerantMatchingTest(private val context: Context) {
    
    companion object {
        private const val TAG = "AccentTolerantTest"
    }
    
    /**
     * Exécute tous les tests et affiche les résultats
     */
    suspend fun runAllTests(): Boolean {
        Log.i(TAG, "🧪 Début des tests AccentTolerantMatching...")
        
        var allTestsPassed = true
        
        // Test 1: Tests unitaires de base
        if (!testBasicNormalization()) {
            allTestsPassed = false
        }
        
        // Test 2: Tests avec le dictionnaire réel
        if (!testWithRealDictionary()) {
            allTestsPassed = false
        }
        
        // Test 3: Tests intégrés dans AccentTolerantMatcher
        if (!AccentTolerantMatcher.runTests()) {
            allTestsPassed = false
        }
        
        // Test 4: Tests avec SuggestionEngine
        if (!testSuggestionEngineIntegration()) {
            allTestsPassed = false
        }
        
        Log.i(TAG, if (allTestsPassed) "✅ Tous les tests sont réussis!" else "❌ Certains tests ont échoué")
        
        return allTestsPassed
    }
    
    /**
     * Test de la normalisation de base
     */
    private fun testBasicNormalization(): Boolean {
        Log.d(TAG, "Test 1: Normalisation de base")
        
        val testCases = mapOf(
            "kréyòl" to "kreyol",
            "fè" to "fe", 
            "té" to "te",
            "épi" to "epi",
            "bòn" to "bon",
            "où" to "ou",
            "café" to "cafe",
            "créole" to "creole"
        )
        
        var passed = true
        
        for ((input, expected) in testCases) {
            val normalized = AccentTolerantMatcher.normalize(input)
            if (normalized != expected) {
                Log.e(TAG, "❌ Normalisation échouée: '$input' → '$normalized' (attendu: '$expected')")
                passed = false
            } else {
                Log.d(TAG, "✅ '$input' → '$normalized'")
            }
        }
        
        return passed
    }
    
    /**
     * Test avec un mini dictionnaire simulé
     */
    private fun testWithRealDictionary(): Boolean {
        Log.d(TAG, "Test 2: Recherche dans dictionnaire simulé")
        
        // Créer un mini dictionnaire pour test
        val testDictionary = listOf(
            Pair("kréyòl", 42),
            Pair("fè", 216),
            Pair("té", 622),
            Pair("bon", 150),
            Pair("bòn", 100),
            Pair("épi", 42),
            Pair("kreyol", 25), // Version sans accents
            Pair("bonjou", 80)
        )
        
        val testCases = listOf(
            "kre" to listOf("kréyòl", "kreyol"),
            "fe" to listOf("fè"),
            "te" to listOf("té"),
            "bon" to listOf("bon", "bòn", "bonjou"),
            "epi" to listOf("épi")
        )
        
        var passed = true
        
        for ((input, expectedWords) in testCases) {
            val results = AccentTolerantMatcher.findAccentTolerantSuggestions(input, testDictionary, 5)
            val foundWords = results.map { it.first }
            
            val allExpectedFound = expectedWords.all { expectedWord ->
                foundWords.any { foundWord -> 
                    AccentTolerantMatcher.startsWith(input, foundWord) &&
                    (foundWord == expectedWord || AccentTolerantMatcher.normalize(foundWord) == AccentTolerantMatcher.normalize(expectedWord))
                }
            }
            
            if (allExpectedFound) {
                Log.d(TAG, "✅ '$input' → ${foundWords}")
            } else {
                Log.e(TAG, "❌ '$input' → ${foundWords} (attendu: contenant $expectedWords)")
                passed = false
            }
        }
        
        return passed
    }
    
    /**
     * Test d'intégration avec SuggestionEngine
     */
    private suspend fun testSuggestionEngineIntegration(): Boolean {
        Log.d(TAG, "Test 4: Intégration SuggestionEngine")
        
        return try {
            val suggestionEngine = SuggestionEngine(context)
            
            // Initialiser le moteur (charge le vrai dictionnaire)
            suggestionEngine.initialize()
            
            // Tester quelques cas avec le vrai dictionnaire
            val testInputs = listOf("kre", "fe", "te", "bon")
            
            var passed = true
            
            for (input in testInputs) {
                // Note: Ici on teste juste que ça ne crash pas
                // Les vraies suggestions seront testées via l'interface utilisateur
                Log.d(TAG, "Test suggestions pour: '$input'")
                suggestionEngine.generateSuggestions(input)
                Log.d(TAG, "✅ Pas de crash pour '$input'")
            }
            
            suggestionEngine.cleanup()
            
            passed
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur dans test SuggestionEngine: ${e.message}", e)
            false
        }
    }
    
    /**
     * Test de performance simple
     */
    fun testPerformance(): Long {
        val testDictionary = (1..1000).map { i ->
            Pair("mot$i", i)
        }
        
        val startTime = System.currentTimeMillis()
        
        // Faire 100 recherches
        repeat(100) {
            AccentTolerantMatcher.findAccentTolerantSuggestions("mo", testDictionary, 10)
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        Log.d(TAG, "⏱️ Performance: 100 recherches en ${duration}ms")
        
        return duration
    }
}