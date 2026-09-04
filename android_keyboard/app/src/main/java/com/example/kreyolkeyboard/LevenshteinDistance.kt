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
    fun calculate(s1: String, s2: String): Int =
        calculateBounded(s1, s2, Int.MAX_VALUE)

    /**
     * Distance de Levenshtein, abandonnée dès qu'elle dépasse [maxDistance].
     *
     * Trois choses la séparent d'une implémentation naïve, et elles comptent
     * parce que cette fonction est appelée pour chaque mot du dictionnaire à
     * chaque frappe qui déclenche le repli de correction :
     *
     * - **deux lignes au lieu d'une matrice.** L'ancienne version allouait
     *   `Array(len1 + 1) { IntArray(len2 + 1) }`, soit une douzaine de tableaux
     *   par mot comparé et des centaines de milliers d'allocations par frappe ;
     * - **la casse repliée une fois par chaîne** et non à chaque cellule.
     *   `lowercaseChar()` était appelé len1 × len2 fois par comparaison ;
     * - **l'abandon anticipé** : si toute une ligne dépasse déjà [maxDistance],
     *   aucune suite ne peut redescendre, et la réponse ne sera de toute façon
     *   pas retenue.
     *
     * Renvoie une valeur strictement supérieure à [maxDistance] quand la
     * distance réelle l'est, sans garantir laquelle : l'appelant ne compare
     * qu'au seuil.
     */
    fun calculateBounded(s1: String, s2: String, maxDistance: Int): Int {
        val len1 = s1.length
        val len2 = s2.length

        if (len1 == 0) return len2
        if (len2 == 0) return len1
        // La différence de longueur est un minorant de la distance : inutile
        // de dérouler la programmation dynamique pour s'en apercevoir.
        if (kotlin.math.abs(len1 - len2) > maxDistance) return maxDistance + 1

        val a = CharArray(len1) { s1[it].lowercaseChar() }
        val b = CharArray(len2) { s2[it].lowercaseChar() }

        var precedente = IntArray(len2 + 1) { it }
        var courante = IntArray(len2 + 1)

        for (i in 1..len1) {
            courante[0] = i
            var minimumLigne = courante[0]
            for (j in 1..len2) {
                val cout = if (a[i - 1] == b[j - 1]) 0 else 1
                val v = minOf(
                    precedente[j] + 1,          // suppression
                    courante[j - 1] + 1,        // insertion
                    precedente[j - 1] + cout    // substitution
                )
                courante[j] = v
                if (v < minimumLigne) minimumLigne = v
            }
            if (minimumLigne > maxDistance) return maxDistance + 1
            val echange = precedente
            precedente = courante
            courante = echange
        }

        return precedente[len2]
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
            val distance = calculateBounded(input, word, maxDistance)
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
        normalizedWords: List<String>,
        normalizer: (String) -> String,
        maxDistance: Int = 2,
        maxResults: Int = 5
    ): List<Triple<String, Int, Int>> {
        
        if (input.isEmpty()) return emptyList()
        
        val normalizedInput = normalizer(input)
        val inputLength = normalizedInput.length

        // `normalizedWords` est la liste précalculée au chargement du moteur,
        // alignée indice à indice avec `dictionary`. Sans elle, cette boucle
        // appelait `normalizer(word)` sur **chacune** des 38 442 formes, avant
        // même le filtre de longueur : le repli reconstruisait le dictionnaire
        // normalisé à chaque frappe. C'était l'essentiel des 670 à 1 180 ms
        // mesurés sur un Galaxy A21s, bien avant le coût de la distance
        // elle-même.
        //
        // Le filtre de longueur passe donc en premier, sur un entier, et rien
        // n'est alloué pour les mots qu'il écarte — l'écrasante majorité.
        val matches = ArrayList<Triple<String, Int, Int>>()
        val n = minOf(dictionary.size, normalizedWords.size)
        for (i in 0 until n) {
            val normalizedWord = normalizedWords[i]
            if (kotlin.math.abs(normalizedWord.length - inputLength) > maxDistance) continue
            val distance = calculateBounded(normalizedInput, normalizedWord, maxDistance)
            if (distance <= maxDistance) {
                val (word, freq) = dictionary[i]
                matches.add(Triple(word, freq, distance))
            }
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
