package com.example.kreyolkeyboard

import android.util.Log

/**
 * 🎯 AccentTolerantMatcher - Recherche insensible aux accents pour le créole guadeloupéen
 * 
 * Permet aux utilisateurs de taper "kre" et obtenir des suggestions pour "kréyòl"
 * Essentiel pour une expérience fluide du clavier créole sans chercher les accents
 * 
 * Fonctionnalités:
 * - Normalisation des accents créoles (à, é, è, ò, etc.)
 * - Recherche flexible dans le dictionnaire
 * - Support des caractères spéciaux guadeloupéens
 * 
 * @author Médhi Famibelle - Potomitan™
 */
object AccentTolerantMatcher {
    
    private const val TAG = "AccentTolerantMatcher"
    
    /**
     * Normalise une chaîne en supprimant tous les accents
     * Optimisé pour le créole guadeloupéen
     */
    fun normalize(text: String): String {
        if (text.isEmpty()) return text
        
        return text
            // Voyelles a
            .replace(Regex("[àáâäãåāăą]"), "a")
            // Voyelles e
            .replace(Regex("[èéêëēėęě]"), "e")
            // Voyelles i
            .replace(Regex("[ìíîïīįĩ]"), "i")
            // Voyelles o
            .replace(Regex("[òóôöõøōőœ]"), "o")
            // Voyelles u
            .replace(Regex("[ùúûüūůũűų]"), "u")
            // Voyelles y
            .replace(Regex("[ýÿŷ]"), "y")
            // Consonnes spéciales
            .replace(Regex("[ç]"), "c")
            .replace(Regex("[ñ]"), "n")
            .replace(Regex("[ß]"), "ss")
            // Conversion en minuscules
            .lowercase()
    }
    
    /**
     * Vérifie si deux mots correspondent après normalisation des accents
     */
    fun matches(input: String, target: String): Boolean {
        return normalize(input) == normalize(target)
    }
    
    /**
     * Vérifie si un mot du dictionnaire commence par l'input normalisé
     */
    fun startsWith(input: String, dictionaryWord: String): Boolean {
        val normalizedInput = normalize(input)
        val normalizedWord = normalize(dictionaryWord)
        return normalizedWord.startsWith(normalizedInput)
    }
    
    /**
     * Trouve toutes les suggestions insensibles aux accents dans une liste de mots
     */
    fun findAccentTolerantSuggestions(
        input: String,
        dictionary: List<Pair<String, Int>>,
        maxResults: Int = 10
    ): List<Pair<String, Int>> {
        
        if (input.length < 2) {
            Log.d(TAG, "Input trop court: '$input'")
            return emptyList()
        }
        
        val normalizedInput = normalize(input)
        
        val matches = dictionary.filter { (word, _) ->
            val normalizedWord = normalize(word)
            normalizedWord.startsWith(normalizedInput)
        }
        
        Log.d(TAG, "Recherche '$input' → '$normalizedInput': ${matches.size} résultats trouvés")
        
        return matches
            .sortedByDescending { it.second } // Trier par fréquence
            .take(maxResults)
    }
    
    /**
     * Calcule un score de pertinence pour un match insensible aux accents
     * Plus le match est proche (longueur, position), plus le score est élevé
     */
    fun calculateMatchScore(input: String, matchedWord: String, frequency: Int): Double {
        val normalizedInput = normalize(input)
        val normalizedMatch = normalize(matchedWord)
        
        var score = frequency.toDouble()
        
        // Bonus pour correspondance exacte après normalisation
        if (normalizedInput == normalizedMatch) {
            score += 100.0
            Log.d(TAG, "Match exact normalisé: '$input' = '$matchedWord' (+100)")
        }
        // Bonus pour début de mot
        else if (normalizedMatch.startsWith(normalizedInput)) {
            val prefixBonus = (normalizedInput.length.toDouble() / normalizedMatch.length) * 50.0
            score += prefixBonus
            Log.d(TAG, "Début de mot: '$input' dans '$matchedWord' (+${prefixBonus.toInt()})")
        }
        
        // Bonus pour mots courts (plus faciles à taper)
        if (matchedWord.length <= 6) {
            score += 10.0
        }
        
        // Malus pour mots très longs
        if (matchedWord.length > 12) {
            score -= 5.0
        }
        
        // Bonus spécial pour mots avec accents (encourage l'apprentissage)
        if (hasAccents(matchedWord)) {
            score += 5.0
        }
        
        return score
    }
    
    /**
     * Vérifie si un mot contient des accents
     */
    fun hasAccents(word: String): Boolean {
        return word != normalize(word)
    }
    
    /**
     * Retourne des informations de debug sur la normalisation
     */
    fun getDebugInfo(input: String, matches: List<String>): String {
        val normalizedInput = normalize(input)
        val normalizedMatches = matches.map { "$it → ${normalize(it)}" }
        
        return """
        |Input: '$input' → '$normalizedInput'
        |Matches found: ${matches.size}
        |${normalizedMatches.joinToString("\n")}
        """.trimMargin()
    }
    
    /**
     * Tests unitaires intégrés pour vérifier le bon fonctionnement
     */
    fun runTests(): Boolean {
        val testCases = listOf(
            // Test créole guadeloupéen de base
            Triple("kre", "kréyòl", true),
            Triple("fe", "fè", true),
            Triple("te", "té", true),
            Triple("bon", "bon", true),
            Triple("bon", "bòn", true),
            
            // Test avec caractères spéciaux
            Triple("creole", "créole", true),
            Triple("epi", "épi", true),
            Triple("ou", "où", true),
            
            // Tests négatifs
            Triple("abc", "xyz", false),
            Triple("k", "kréyòl", false), // Trop court
            
            // Tests edge cases
            Triple("", "", true),
            Triple("KREYOL", "kréyòl", true), // Insensible à la casse
        )
        
        var allPassed = true
        
        for ((input, target, expectedMatch) in testCases) {
            val actualMatch = if (input.length < 2 && expectedMatch) {
                input == target
            } else {
                startsWith(input, target)
            }
            
            if (actualMatch != expectedMatch) {
                Log.e(TAG, "❌ Test échoué: '$input' vs '$target' - Attendu: $expectedMatch, Obtenu: $actualMatch")
                allPassed = false
            } else {
                Log.d(TAG, "✅ Test réussi: '$input' vs '$target' = $expectedMatch")
            }
        }
        
        Log.i(TAG, if (allPassed) "🎯 Tous les tests réussis!" else "⚠️ Certains tests ont échoué")
        return allPassed
    }
}