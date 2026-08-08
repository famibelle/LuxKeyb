package com.example.kreyolkeyboard.gamification

import android.content.Context
import android.util.Log
import com.example.kreyolkeyboard.gamification.WordUsageStats
import com.example.kreyolkeyboard.gamification.VocabularyStats
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gestionnaire du dictionnaire créole avec tracking d'utilisation utilisateur
 * 
 * RESPECT DE LA VIE PRIVÉE :
 * - Seuls les mots qui existent dans le dictionnaire créole sont trackés
 * - Les mots personnels, mots de passe, etc. sont automatiquement ignorés
 * - Aucun texte complet n'est stocké, seulement les compteurs par mot du dictionnaire
 * - Toutes les données restent sur l'appareil (pas de synchronisation cloud)
 * 
 * Structure des données :
 * {
 *   "bonjou": {"frequency": 450, "user_count": 127},
 *   "kréyòl": {"frequency": 89, "user_count": 45},
 *   ...
 * }
 */
class CreoleDictionaryWithUsage(private val context: Context) {
    
    companion object {
        private const val TAG = "CreoleDictUsage"
        private const val DICT_FILE = "creole_dict_with_usage.json"
        private const val ORIGINAL_DICT = "creole_dict.json"
        // 2 : le dictionnaire créole contient des mots réels et très fréquents de 2 lettres
        // (ka, ou, on, an, sa, wi...) qui ne comptaient jamais dans wordsDiscovered avant ce
        // correctif (23/07/2026). Le vrai filtre de vie privée reste dictionary.has(normalized)
        // plus bas : un mot hors dictionnaire n'est de toute façon jamais tracké, quelle que
        // soit sa longueur.
        private const val MIN_WORD_LENGTH = 2
    }

    private var dictionary: JSONObject = JSONObject()
    private var unsavedChanges = 0  // Changements non encore écrits sur le disque

    /**
     * Écriture du dictionnaire hors du thread appelant.
     *
     * Mesuré le 08/08/2026 sur émulateur : sauvegarder à chaque mot validé,
     * de façon synchrone, coûtait 116 à 500 ms sur le thread principal du
     * service de saisie — assez pour faire sauter des images en pleine frappe.
     * Le coût venait du couple sérialisation + écriture des 5296 entrées, et il
     * était payé à chaque espace tapé.
     *
     * Un exécuteur à un seul thread suffit : les écritures ne peuvent pas se
     * chevaucher, et [savePending] les fusionne quand plusieurs mots arrivent
     * pendant qu'une écriture est en cours. Le thread est daemon pour ne jamais
     * retenir le processus.
     */
    private val saveExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "KreyolDictSave").apply { isDaemon = true }
    }
    private val savePending = AtomicBoolean(false)

    /**
     * Nombre de mots distincts déjà employés au moins une fois, tenu à jour de
     * façon incrémentale.
     *
     * Le comptage complet parcourt les ~5300 entrées du dictionnaire : c'est
     * acceptable à l'ouverture d'un écran de statistiques, pas à chaque mot
     * validé. Or le service de saisie doit désormais vérifier un éventuel
     * passage de niveau sur le thread principal, à chaque mot — d'où ce cache,
     * initialisé une fois au chargement puis incrémenté à chaque découverte.
     * -1 tant qu'il n'a pas été calculé.
     */
    private var cachedDiscoveredCount = -1

    init {
        loadDictionary()
    }
    
    /**
     * Méthode utilitaire pour accéder aux données d'un mot avec migration automatique
     * Gère les deux formats : entier direct ou objet JSON complet
     */
    private fun getWordDataSafe(word: String): JSONObject? {
        if (!dictionary.has(word)) return null
        
        return try {
            val rawValue = dictionary.get(word)
            when (rawValue) {
                is Int -> {
                    // Format simplifié: "mot": 1 -> migrer vers objet JSON
                    val newData = JSONObject().apply {
                        put("frequency", 0)
                        put("user_count", rawValue)
                    }
                    dictionary.put(word, newData)
                    Log.d(TAG, "🔄 Migration auto '$word': $rawValue -> objet JSON")
                    newData
                }
                is JSONObject -> {
                    // Format standard: "mot": {"frequency": X, "user_count": Y}
                    rawValue
                }
                else -> {
                    Log.e(TAG, "❌ Format invalide pour '$word': ${rawValue::class.java}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur lors de l'accès à '$word'", e)
            null
        }
    }
    
    /**
     * Charge le dictionnaire (avec migration automatique si nécessaire)
     */
    private fun loadDictionary() {
        val file = File(context.filesDir, DICT_FILE)
        
        Log.d(TAG, "📂 Fichier dictionnaire existe: ${file.exists()}")
        Log.d(TAG, "📂 Chemin: ${file.absolutePath}")
        
        if (file.exists()) {
            val content = file.readText()
            Log.d(TAG, "📄 Taille fichier: ${content.length} chars")
            Log.d(TAG, "📄 Aperçu contenu: ${content.take(200)}...")
            
            dictionary = if (content.trim().isEmpty() || content.trim() == "{}") {
                // Fichier vide ou reset - forcer la migration
                Log.d(TAG, "🔄 Fichier vide détecté - Force migration...")
                migrateDictionary()
            } else {
                // Charger le dictionnaire existant avec compteurs
                Log.d(TAG, "📖 Chargement du dictionnaire existant avec compteurs...")
                JSONObject(content)
            }
        } else {
            // Première utilisation : migrer le dictionnaire original
            Log.d(TAG, "🔄 Première utilisation - Migration du dictionnaire...")
            dictionary = migrateDictionary()
        }
        
        Log.d(TAG, "✅ Dictionnaire chargé : ${dictionary.length()} mots")
    }
    
    /**
     * Migre le dictionnaire original en ajoutant les compteurs user_count
     * Le dictionnaire original est un array: [["mot", frequency], ...]
     */
    private fun migrateDictionary(): JSONObject {
        val migratedDict = JSONObject()
        
        try {
            // Charger le dictionnaire original depuis les assets
            val json = context.assets.open(ORIGINAL_DICT)
                .bufferedReader()
                .use { it.readText() }
            val originalArray = org.json.JSONArray(json)
            
            // Transformer chaque entrée du array en objet
            var count = 0
            for (i in 0 until originalArray.length()) {
                val entry = originalArray.getJSONArray(i)
                val word = entry.getString(0)
                val frequency = entry.getInt(1)
                
                // Créer la nouvelle structure avec user_count à 0
                val wordData = JSONObject().apply {
                    put("frequency", frequency)
                    put("user_count", 0)
                }
                
                migratedDict.put(word, wordData)
                count++
            }
            
            // Sauvegarder le dictionnaire migré
            saveDictionaryToFile(migratedDict)
            Log.d(TAG, "✅ Migration réussie : $count mots transformés depuis array")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur lors de la migration du dictionnaire", e)
        }
        
        return migratedDict
    }
    
    /**
     * Résultat d'un comptage : le mot a-t-il été tracké, et venait-il d'être
     * employé pour la première fois. `newlyDiscovered` est ce qui permet au
     * service de saisie de ne tester un passage de niveau que sur une vraie
     * découverte, au lieu de le faire à chaque mot validé.
     */
    data class TrackResult(val tracked: Boolean, val newlyDiscovered: Boolean)

    /**
     * Incrémente le compteur d'utilisation d'un mot
     *
     * @param word Le mot tapé par l'utilisateur
     * @return true si le mot a été tracké, false sinon (mot ignoré)
     */
    fun incrementWordUsage(word: String): Boolean = trackWordUsage(word).tracked

    /**
     * Variante de [incrementWordUsage] qui signale en plus les premières
     * découvertes. Même verrou, même comportement par ailleurs.
     */
    fun trackWordUsage(word: String): TrackResult = synchronized(this) {
        Log.d(TAG, "📥 incrementWordUsage appelé avec: '$word'")
        Log.d(TAG, "📂 CreoleDictionary contexte: ${context.filesDir.absolutePath}")
        
        // Normalisation basique (lowercase + trim)
        val normalized = word.lowercase().trim()
        Log.d(TAG, "🔄 Mot normalisé: '$word' -> '$normalized'")
        
        // Filtres de sécurité et vie privée
        if (!isValidForTracking(normalized)) {
            Log.d(TAG, "🔒 Mot ignoré (filtres de sécurité): '$normalized'")
            return TrackResult(tracked = false, newlyDiscovered = false)
        }
        
        // Vérifier que le mot existe dans le dictionnaire créole
        return if (dictionary.has(normalized)) {
            try {
                // Gérer les deux formats possibles : entier direct ou objet JSON
                val rawValue = dictionary.get(normalized)
                val wordData = when (rawValue) {
                    is Int -> {
                        // Format simplifié du système optimisé: "mot": 1
                        // Migrer vers format complet
                        val newData = JSONObject().apply {
                            put("frequency", 0)  // Pas de données de fréquence originale disponibles
                            put("user_count", rawValue)
                        }
                        dictionary.put(normalized, newData)
                        Log.d(TAG, "🔄 Migration auto de '$normalized': $rawValue -> objet JSON")
                        newData
                    }
                    is JSONObject -> {
                        // Format standard: "mot": {"frequency": X, "user_count": Y}
                        rawValue
                    }
                    else -> {
                        Log.e(TAG, "❌ Format invalide pour '$normalized': ${rawValue::class.java}")
                        return TrackResult(tracked = false, newlyDiscovered = false)
                    }
                }
                
                val currentCount = wordData.getInt("user_count")
                wordData.put("user_count", currentCount + 1)

                // Première apparition de ce mot : le cache de mots découverts
                // suit le mouvement sans reparcourir tout le dictionnaire. Si
                // le cache n'a jamais été calculé, on le calcule maintenant —
                // l'écriture ci-dessus étant déjà faite, le total obtenu inclut
                // ce mot et ne doit donc pas être incrémenté en plus.
                val newlyDiscovered = currentCount == 0
                if (newlyDiscovered) {
                    if (cachedDiscoveredCount < 0) {
                        cachedDiscoveredCount = computeDiscoveredWordsCount()
                    } else {
                        cachedDiscoveredCount++
                    }
                }

                unsavedChanges++
                Log.d(TAG, "✅ '$normalized' utilisé ${currentCount + 1} fois")

                // Sauvegarde après chaque mot, mais hors du thread appelant :
                // rien n'est différé ni regroupé, donc rien ne peut être perdu
                // si le service est tué, et la frappe ne paie plus l'écriture.
                scheduleSave()

                TrackResult(tracked = true, newlyDiscovered = newlyDiscovered)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur lors du tracking de '$normalized'", e)
                TrackResult(tracked = false, newlyDiscovered = false)
            }
        } else {
            Log.d(TAG, "🔒 '$normalized' ignoré (pas dans le dictionnaire créole)")
            TrackResult(tracked = false, newlyDiscovered = false)
        }
    }
    
    /**
     * Valide si un mot peut être tracké (filtres de vie privée)
     */
    private fun isValidForTracking(word: String): Boolean {
        // Ignorer les mots trop courts (< 3 lettres)
        if (word.length < MIN_WORD_LENGTH) {
            return false
        }
        
        // Ignorer les mots contenant des chiffres (potentiellement des codes/mots de passe)
        if (word.any { it.isDigit() }) {
            return false
        }
        
        // Ignorer les URLs
        if (word.contains("http") || word.contains("www") || word.contains(".com")) {
            return false
        }
        
        // Ignorer les emails
        if (word.contains("@")) {
            return false
        }
        
        return true
    }
    
    /**
     * Obtient le nombre d'utilisations d'un mot
     *
     * `synchronized` avec incrementWordUsage() : depuis que le moteur de
     * suggestions se sert de ce compteur pour classer les propositions, la lecture
     * se fait sur un thread de fond pendant la frappe, en concurrence avec
     * l'écriture faite sur le thread principal à chaque mot validé. JSONObject
     * n'est pas thread-safe, et getWordDataSafe() écrit lui-même dans la map quand
     * il migre une entrée à l'ancien format.
     */
    fun getWordUsageCount(word: String): Int = synchronized(this) {
        val normalized = word.lowercase().trim()
        val wordData = getWordDataSafe(normalized)
        return wordData?.getInt("user_count") ?: 0
    }
    
    /**
     * Obtient la fréquence corpus d'un mot
     */
    fun getWordFrequency(word: String): Int {
        val normalized = word.lowercase().trim()
        val wordData = getWordDataSafe(normalized)
        return wordData?.getInt("frequency") ?: 0
    }
    
    /**
     * Calcule le pourcentage de couverture du dictionnaire
     */
    fun getCoveragePercentage(): Float {
        var wordsUsed = 0
        val totalWords = dictionary.length()
        
        val keys = dictionary.keys()
        while (keys.hasNext()) {
            val word = keys.next()
            val wordData = getWordDataSafe(word)
            if (wordData != null && wordData.getInt("user_count") > 0) {
                wordsUsed++
            }
        }
        
        return if (totalWords > 0) {
            (wordsUsed.toFloat() / totalWords) * 100
        } else {
            0f
        }
    }
    
    /**
     * Obtient le nombre de mots découverts (utilisés au moins 1 fois)
     */
    /** Taille du dictionnaire chargé, base de calcul des seuils de niveau. */
    fun getTotalWords(): Int = dictionary.length()

    fun getDiscoveredWordsCount(): Int = synchronized(this) {
        if (cachedDiscoveredCount < 0) {
            cachedDiscoveredCount = computeDiscoveredWordsCount()
        }
        cachedDiscoveredCount
    }

    /** Comptage complet, en O(taille du dictionnaire). Réservé à l'amorçage du cache. */
    private fun computeDiscoveredWordsCount(): Int {
        var count = 0
        val keys = dictionary.keys()
        while (keys.hasNext()) {
            val word = keys.next()
            val wordData = getWordDataSafe(word)
            if (wordData != null && wordData.getInt("user_count") > 0) {
                count++
            }
        }
        return count
    }
    
    /**
     * Obtient le nombre total d'utilisations (somme de tous les compteurs)
     */
    fun getTotalUsageCount(): Int {
        var total = 0
        val keys = dictionary.keys()
        while (keys.hasNext()) {
            val word = keys.next()
            val wordData = getWordDataSafe(word)
            if (wordData != null) {
                total += wordData.getInt("user_count")
            }
        }
        return total
    }
    
    /**
     * Obtient les mots les plus utilisés
     */
    fun getTopUsedWords(limit: Int = 10): List<WordUsageStats> {
        val wordStats = mutableListOf<WordUsageStats>()
        
        val keys = dictionary.keys()
        while (keys.hasNext()) {
            val word = keys.next()
            val wordData = getWordDataSafe(word)
            
            if (wordData != null) {
                val userCount = wordData.getInt("user_count")
                if (userCount > 0) {
                    wordStats.add(
                        WordUsageStats(
                            word = word,
                            userCount = userCount,
                            frequency = wordData.getInt("frequency")
                        )
                    )
                }
            }
        }
        
        return wordStats
            .sortedByDescending { it.userCount }
            .take(limit)
    }
    
    /**
     * Obtient les mots récemment découverts (utilisés 1-3 fois)
     */
    fun getRecentlyDiscoveredWords(limit: Int = 5): List<String> {
        val recentWords = mutableListOf<String>()
        
        val keys = dictionary.keys()
        while (keys.hasNext()) {
            val word = keys.next()
            val wordData = getWordDataSafe(word)
            
            if (wordData != null) {
                val userCount = wordData.getInt("user_count")
                if (userCount in 1..3) {
                    recentWords.add(word)
                }
            }
        }
        
        return recentWords.take(limit)
    }
    
    /**
     * Obtient le nombre de mots maîtrisés (utilisés 10+ fois)
     */
    fun getMasteredWordsCount(): Int {
        var count = 0
        val keys = dictionary.keys()
        while (keys.hasNext()) {
            val word = keys.next()
            val wordData = getWordDataSafe(word)
            if (wordData != null && wordData.getInt("user_count") >= 10) {
                count++
            }
        }
        return count
    }
    
    /**
     * Obtient les statistiques complètes du vocabulaire
     */
    fun getVocabularyStats(): VocabularyStats {
        return VocabularyStats(
            coveragePercentage = getCoveragePercentage(),
            wordsDiscovered = getDiscoveredWordsCount(),
            totalWords = dictionary.length(),
            totalUsages = getTotalUsageCount(),
            topWords = getTopUsedWords(),
            recentWords = getRecentlyDiscoveredWords(),
            masteredWords = getMasteredWordsCount()
        )
    }
    
    /**
     * Sauvegarde le dictionnaire sur le disque, sur le thread appelant.
     *
     * À réserver aux points de sortie ([forceSave], [onDestroy]) : en pleine
     * frappe, passer par [scheduleSave].
     */
    fun saveDictionary() {
        try {
            saveDictionaryToFile(dictionary)
            Log.d(TAG, "💾 Dictionnaire sauvegardé (${unsavedChanges} changements)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur lors de la sauvegarde", e)
        }
    }

    /**
     * Demande une écriture du dictionnaire sur le thread de sauvegarde.
     *
     * Les demandes qui arrivent pendant qu'une écriture est déjà programmée
     * sont fusionnées : le drapeau est baissé à l'entrée de la tâche, si bien
     * qu'un mot validé entre-temps en reprogramme une et n'est jamais perdu.
     * La sérialisation prend le verrou de l'objet, l'écriture disque non.
     */
    private fun scheduleSave() {
        if (!savePending.compareAndSet(false, true)) return

        try {
            saveExecutor.execute {
                savePending.set(false)
                val (snapshot, changes) = synchronized(this) {
                    val json = dictionary.toString()
                    val pending = unsavedChanges
                    unsavedChanges = 0
                    json to pending
                }
                try {
                    File(context.filesDir, DICT_FILE).writeText(snapshot)
                    Log.d(TAG, "💾 Dictionnaire sauvegardé en tâche de fond ($changes changements)")
                } catch (e: Exception) {
                    // Le compteur repart de zéro quoi qu'il arrive : le prochain
                    // mot validé reprogrammera une écriture complète.
                    Log.e(TAG, "❌ Erreur lors de la sauvegarde en tâche de fond", e)
                }
            }
        } catch (e: Exception) {
            // Exécuteur déjà arrêté (service détruit) : repli synchrone.
            savePending.set(false)
            Log.w(TAG, "Sauvegarde de fond indisponible, repli synchrone", e)
            saveDictionary()
            unsavedChanges = 0
        }
    }

    /**
     * Force la sauvegarde immédiate en contournant le système de batch
     */
    fun forceSave() {
        synchronized(this) {
            try {
                saveDictionary()
                unsavedChanges = 0
                Log.d(TAG, "🔥 Sauvegarde immédiate forcée - ${dictionary.length()} mots")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur lors de la sauvegarde forcée", e)
            }
        }
    }
    
    /**
     * Sauvegarde un objet JSON dans le fichier du dictionnaire
     */
    private fun saveDictionaryToFile(dict: JSONObject) {
        val file = File(context.filesDir, DICT_FILE)
        // Sans indentation : le fichier n'est pas destiné à être lu à l'œil, et
        // l'indentation de 2 le faisait passer de 244 à 318 Ko, soit autant de
        // sérialisation et d'écriture payées à chaque mot validé.
        file.writeText(dict.toString())
    }
    
    /**
     * Reset tous les compteurs utilisateur (pour debug/testing uniquement)
     */
    fun resetAllUserCounts() {
        val keys = dictionary.keys()
        while (keys.hasNext()) {
            val word = keys.next()
            val wordData = getWordDataSafe(word)
            if (wordData != null) {
                wordData.put("user_count", 0)
            }
        }
        saveDictionary()
        unsavedChanges = 0
        Log.d(TAG, "🔄 Tous les compteurs réinitialisés")
    }
    
    /**
     * Appelé quand l'app se termine pour sauvegarder les changements non sauvegardés
     */
    fun onDestroy() {
        // L'exécuteur est arrêté d'abord : plus aucune écriture de fond ne peut
        // démarrer, et celle éventuellement en cours a le temps de finir avant
        // la sauvegarde finale, qui écrirait sinon par-dessus.
        saveExecutor.shutdown()
        try {
            saveExecutor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        synchronized(this) {
            if (unsavedChanges > 0) {
                saveDictionary()
                Log.d(TAG, "💾 Sauvegarde finale (${unsavedChanges} changements non sauvegardés)")
                unsavedChanges = 0
            }
        }
    }
}
