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
        private const val MAX_CACHE_ENTRIES = 1000
    }
    
    // Données du dictionnaire français
    private var frenchWords: List<Pair<String, Int>> = emptyList()
    private var isLoaded = false

    // Fréquence par mot, pour que containsWord() et getWordFrequency() ne
    // parcourent pas la liste. Le second est appelé une fois par suggestion
    // retenue, le premier une fois par mot examiné par le correcteur
    // orthographique système — qui, la locale `fr` étant déclarée, remplace
    // celui du français. À 125 000 formes, deux balayages linéaires par mot
    // tapé n'étaient plus tenables : c'est le même défaut que le palier LOD
    // côté luxembourgeois, et la même correction.
    private var frequencyByWord: Map<String, Int> = emptyMap()

    // Index des candidats par trois premières lettres. Les suggestions
    // françaises ne s'activent qu'à partir de MIN_ACTIVATION_LENGTH lettres,
    // donc ce préfixe existe toujours au moment de la recherche : au lieu de
    // filtrer les 125 000 formes, on ne regarde que le seau correspondant,
    // quelques dizaines d'entrées déjà triées par fréquence décroissante.
    private var prefixIndex: Map<String, List<Pair<String, Int>>> = emptyMap()

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
            // Une seule entrée par graphie : l'actif n'en livre pas de doublon,
            // mais associateBy laisse passer un doublon éventuel sans exploser.
            frequencyByWord = frenchWords.associate { it }
            prefixIndex = frenchWords
                .filter { it.first.length >= MIN_ACTIVATION_LENGTH }
                .groupBy { it.first.substring(0, MIN_ACTIVATION_LENGTH) }
            isLoaded = true

            Log.d(TAG, "✅ Dictionnaire français chargé: ${frenchWords.size} mots, ${prefixIndex.size} préfixes")
            
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
        
        // Mettre en cache le résultat. Le cache est vidé au-delà d'un millier
        // d'entrées : il sert à absorber les frappes répétées d'une même
        // saisie, pas à retenir toute une session — et une clé par préfixe
        // tapé finirait par peser sur un processus de saisie déjà chargé de
        // deux dictionnaires.
        if (suggestionCache.size >= MAX_CACHE_ENTRIES) suggestionCache.clear()
        suggestionCache[cacheKey] = suggestions
        
        Log.d(TAG, "🔵 Suggestions françaises pour '$prefix': $suggestions")
        return suggestions
    }
    
    /**
     * Recherche des mots français par préfixe, dans le seul seau du préfixe.
     *
     * L'ordre est celui d'avant — fréquence décroissante, puis mots courts
     * préférés à égalité — mais il ne s'applique qu'aux candidats du seau. Le
     * tri reste donc négligeable là où il portait sur toutes les formes
     * commençant par ces lettres.
     */
    private fun searchFrenchWords(prefix: String): List<String> {
        val prefixLower = prefix.lowercase()
        val seau = prefixIndex[prefixLower.substring(0, MIN_ACTIVATION_LENGTH)]
            ?: return emptyList()

        return seau
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
        
        return frequencyByWord.containsKey(word.lowercase())
    }
    
    /**
     * Obtient la fréquence d'un mot français
     */
    fun getWordFrequency(word: String): Int {
        if (!isLoaded) return 0
        
        return frequencyByWord[word.lowercase()] ?: 0
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
        frequencyByWord = emptyMap()
        prefixIndex = emptyMap()
        clearCache()
        isLoaded = false
        Log.d(TAG, "Dictionnaire français nettoyé")
    }
}