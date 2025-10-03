package com.example.kreyolkeyboard

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Dictionnaire français simple pour support bilingue
 * Fournit des suggestions françaises à partir de 3 lettres
 */
class FrenchDictionary(private val context: Context) {
    
    companion object {
        private const val TAG = "FrenchDictionary"
        private const val FRENCH_DICT_FILE = "french_simple_dict.json"
        private const val MIN_ACTIVATION_LENGTH = 3
        private const val MAX_FRENCH_SUGGESTIONS = 2  // Maximum 2 suggestions françaises
    }
    
    // Données du dictionnaire français
    private var frenchWords: List<Pair<String, Int>> = emptyList()
    private var isLoaded = false
    
    // Cache pour optimiser les recherches répétées
    private val suggestionCache = mutableMapOf<String, List<String>>()
    
    /**
     * Initialise le dictionnaire français de manière asynchrone
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isLoaded) return@withContext
        
        try {
            Log.d(TAG, "🇫🇷 Chargement dictionnaire français simple...")
            
            val jsonString = context.assets.open(FRENCH_DICT_FILE)
                .bufferedReader().use { it.readText() }
            
            val jsonObject = JSONObject(jsonString)
            val wordsArray = jsonObject.getJSONArray("words")
            
            val loadedWords = mutableListOf<Pair<String, Int>>()
            
            for (i in 0 until wordsArray.length()) {
                val wordArray = wordsArray.getJSONArray(i)
                val word = wordArray.getString(0).lowercase()
                val frequency = wordArray.optInt(1, 1)
                loadedWords.add(Pair(word, frequency))
            }
            
            // Trier par fréquence décroissante pour optimiser les suggestions
            frenchWords = loadedWords.sortedByDescending { it.second }
            isLoaded = true
            
            Log.d(TAG, "✅ Dictionnaire français chargé: ${frenchWords.size} mots")
            
        } catch (e: IOException) {
            Log.e(TAG, "❌ Erreur chargement dictionnaire français: ${e.message}", e)
            frenchWords = emptyList()
            isLoaded = false
        }
    }
    
    /**
     * Génère des suggestions françaises pour un préfixe donné
     * Activé uniquement à partir de 3 lettres (logique principale)
     */
    fun getSuggestions(prefix: String): List<String> {
        // 🎯 RÈGLE PRINCIPALE: Français activé seulement à partir de 3 lettres
        if (prefix.length < MIN_ACTIVATION_LENGTH) {
            Log.d(TAG, "Prefix trop court pour français: '$prefix' (${prefix.length} < $MIN_ACTIVATION_LENGTH)")
            return emptyList()
        }
        
        if (!isLoaded || frenchWords.isEmpty()) {
            Log.d(TAG, "Dictionnaire français pas encore chargé")
            return emptyList()
        }
        
        // Vérifier cache d'abord
        val cacheKey = prefix.lowercase()
        suggestionCache[cacheKey]?.let { cached ->
            Log.d(TAG, "Cache hit pour '$prefix': $cached")
            return cached
        }
        
        // Recherche dans le dictionnaire
        val suggestions = searchFrenchWords(prefix)
        
        // Mettre en cache le résultat
        suggestionCache[cacheKey] = suggestions
        
        Log.d(TAG, "🔵 Suggestions françaises pour '$prefix': $suggestions")
        return suggestions
    }
    
    /**
     * Recherche des mots français par préfixe
     */
    private fun searchFrenchWords(prefix: String): List<String> {
        val prefixLower = prefix.lowercase()
        
        return frenchWords
            .filter { it.first.startsWith(prefixLower) }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second } // Fréquence d'abord
                .thenBy { it.first.length })  // Puis mots courts préférés
            .take(MAX_FRENCH_SUGGESTIONS)  // Maximum 2 suggestions françaises
            .map { it.first }
    }
    
    /**
     * Vérifie si un mot existe dans le dictionnaire français
     */
    fun containsWord(word: String): Boolean {
        if (!isLoaded) return false
        
        val wordLower = word.lowercase()
        return frenchWords.any { it.first == wordLower }
    }
    
    /**
     * Obtient la fréquence d'un mot français
     */
    fun getWordFrequency(word: String): Int {
        if (!isLoaded) return 0
        
        val wordLower = word.lowercase()
        return frenchWords.find { it.first == wordLower }?.second ?: 0
    }
    
    /**
     * Détermine si le préfixe devrait activer les suggestions françaises
     */
    fun shouldActivateFrench(input: String): Boolean {
        return input.length >= MIN_ACTIVATION_LENGTH && isLoaded
    }

    /**
     * Retourne le nombre de mots chargés dans le dictionnaire
     */
    fun getLoadedWordCount(): Int {
        return frenchWords.size
    }
    
    /**
     * Vide le cache des suggestions
     */
    fun clearCache() {
        suggestionCache.clear()
        Log.d(TAG, "Cache français vidé")
    }
    
    /**
     * Obtient des statistiques du dictionnaire
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "loaded" to isLoaded,
            "word_count" to frenchWords.size,
            "cache_size" to suggestionCache.size,
            "min_activation_length" to MIN_ACTIVATION_LENGTH,
            "max_suggestions" to MAX_FRENCH_SUGGESTIONS
        )
    }
    
    /**
     * Nettoie les ressources
     */
    fun cleanup() {
        frenchWords = emptyList()
        clearCache()
        isLoaded = false
        Log.d(TAG, "Dictionnaire français nettoyé")
    }
}