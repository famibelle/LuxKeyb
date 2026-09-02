package com.example.kreyolkeyboard

import android.util.Log

/**
 * Utility class for calculating Levenshtein distance and finding spell corrections
 * 
 * The Levenshtein distance measures the minimum number of single-character edits
 * (insertions, deletions, or substitutions) required to change one word into another.
 * 
 * This is used for spell correction when users make typos or mix up letters.
 * 
 * Example:
 * - "bonjo" → "bonjou" (distance = 1, missing 'u')
 * - "letzebuergesch" → "lëtzebuergesch" (distance = 1, accent manquant)
 * - "mesli" → "mèsi" (distance = 1, extra 'l')
 */
object LevenshteinDistance {
    
    private const val TAG = "LevenshteinDistance"
    
    /**
     * Calculates the Levenshtein distance between two strings
     * using dynamic programming for optimal performance.
     * 
     * @param s1 First string
     * @param s2 Second string
     * @return The minimum number of edits needed to transform s1 into s2
     */
    fun calculate(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        
        // Handle empty strings
        if (len1 == 0) return len2
        if (len2 == 0) return len1
        
        // Create distance matrix (using dynamic programming)
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }
        
        // Initialize first row and column (base cases)
        for (i in 0..len1) dp[i][0] = i  // Cost of deleting all characters from s1
        for (j in 0..len2) dp[0][j] = j  // Cost of inserting all characters from s2
        
        // Fill the matrix using the recurrence relation
        for (i in 1..len1) {
            for (j in 1..len2) {
                // If characters match, no cost; otherwise substitution costs 1
                val cost = if (s1[i - 1].lowercaseChar() == s2[j - 1].lowercaseChar()) 0 else 1
                
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,       // Deletion
                    dp[i][j - 1] + 1,       // Insertion
                    dp[i - 1][j - 1] + cost // Substitution (or match if cost=0)
                )
            }
        }
        
        return dp[len1][len2]
    }
    
    /**
     * Calculates Levenshtein distance with accent normalization.
     * This combines spell correction with accent-tolerant matching.
     * 
     * @param s1 First string
     * @param s2 Second string
     * @param normalizer Function to normalize accents (optional)
     * @return The minimum number of edits needed (ignoring accent differences)
     */
    fun calculateNormalized(
        s1: String, 
        s2: String,
        normalizer: (String) -> String = { it }
    ): Int {
        val normalized1 = normalizer(s1)
        val normalized2 = normalizer(s2)
        return calculate(normalized1, normalized2)
    }
    
    /**
     * Finds the closest matching words from a dictionary using Levenshtein distance.
     * 
     * This method:
     * 1. Pre-filters by word length (performance optimization)
     * 2. Calculates distance for remaining candidates
     * 3. Filters by maximum allowed distance
     * 4. Sorts by distance (closest first), then by frequency
     * 
     * @param input The user's typed word (potentially misspelled)
     * @param dictionary List of (word, frequency) pairs
     * @param maxDistance Maximum allowed Levenshtein distance (default: 2)
     * @param maxResults Maximum number of suggestions to return (default: 5)
     * @param lengthTolerance Maximum length difference to consider (default: 2)
     * @return List of (word, frequency, distance) triples sorted by relevance
     */
    fun findClosestMatches(
        input: String,
        dictionary: List<Pair<String, Int>>,
        maxDistance: Int = 2,
        maxResults: Int = 5,
        lengthTolerance: Int = 2
    ): List<Triple<String, Int, Int>> {
        
        if (input.isEmpty()) return emptyList()
        
        val inputLength = input.length
        
        // Un seul parcours, sans liste intermédiaire : le filtre par longueur
        // puis le calcul de distance allouaient chacun une copie du
        // dictionnaire. Sans conséquence sur 38 000 entrées, mesurable depuis
        // que les formes du LOD en portent le total à 123 000 — et ce chemin
        // est celui de la correction orthographique, appelé dès qu'aucun
        // préfixe ne correspond.
        val matches = ArrayList<Triple<String, Int, Int>>()
        var examined = 0
        for ((word, freq) in dictionary) {
            if (kotlin.math.abs(word.length - inputLength) > lengthTolerance) continue
            examined++
            val distance = calculate(input, word)
            if (distance <= maxDistance) matches.add(Triple(word, freq, distance))
        }
        
        Log.d(TAG, "Spell check '$input': $examined/${dictionary.size} candidates after length filter")
        
        val ranked = matches
            .sortedWith(
                compareBy<Triple<String, Int, Int>> { it.third }  // Sort by distance (lower is better)
                    .thenByDescending { it.second }  // Then by frequency (higher is better)
            )
            .take(maxResults)

        if (ranked.isNotEmpty()) {
            Log.d(TAG, "✓ Found ${ranked.size} corrections for '$input': ${ranked.take(3).map { it.first }}")
        } else {
            Log.d(TAG, "✗ No corrections found for '$input' (within distance $maxDistance)")
        }
        
        return ranked
    }
    
    /**
     * Finds closest matches with accent normalization.
     * Combines spell correction with accent-tolerant matching.
     * 
     * @param input The user's typed word
     * @param dictionary List of (word, frequency) pairs
     * @param normalizer Function to normalize accents
     * @param maxDistance Maximum allowed distance
     * @param maxResults Maximum number of suggestions
     * @return List of (word, frequency, distance) triples sorted by relevance
     */
    fun findClosestMatchesNormalized(
        input: String,
        dictionary: List<Pair<String, Int>>,
        normalizer: (String) -> String,
        maxDistance: Int = 2,
        maxResults: Int = 5
    ): List<Triple<String, Int, Int>> {
        
        if (input.isEmpty()) return emptyList()
        
        val normalizedInput = normalizer(input)
        val inputLength = normalizedInput.length
        
        // Un seul parcours, et surtout un seul appel à `normalizer` par mot :
        // le filtre par longueur et le calcul de distance normalisaient chacun
        // la même forme, soit 246 000 chaînes construites par recours depuis
        // que le LOD porte le dictionnaire à 123 000 entrées.
        val matches = ArrayList<Triple<String, Int, Int>>()
        for ((word, freq) in dictionary) {
            val normalizedWord = normalizer(word)
            if (kotlin.math.abs(normalizedWord.length - inputLength) > 2) continue
            val distance = calculate(normalizedInput, normalizedWord)
            if (distance <= maxDistance) matches.add(Triple(word, freq, distance))
        }

        val ranked = matches
            .sortedWith(
                compareBy<Triple<String, Int, Int>> { it.third }
                    .thenByDescending { it.second }
            )
            .take(maxResults)

        Log.d(TAG, "Normalized spell check '$input': ${ranked.size} matches found")
        
        return ranked
    }
    
}
