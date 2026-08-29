package com.example.kreyolkeyboard

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Moteur de suggestions bilingue pour le clavier créole
 * Gère le dictionnaire luxembourgeois, les N-grams et le support français
 * 🎯 PRIORITÉ LUXEMBOURGEOIS: Français activé seulement à partir de 3 lettres
 * 
 * À la mémoire de mon père, Saint-Ange Corneille Famibelle
 */
class SuggestionEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "SuggestionEngine"
        private const val MAX_SUGGESTIONS = 5  // Augmenté pour bilingue (3 luxembourgeois + 2 français)
        private const val MAX_WORD_HISTORY = 5
        private const val MIN_WORD_LENGTH = 1  // Le kréyòl a des mots très fréquents dès 1-2 lettres (ka, an, sé)

        // Poids d'une utilisation personnelle dans le score d'une suggestion.
        // Calibré sur la distribution réelle du dictionnaire : entre candidats
        // partageant un préfixe, l'écart de fréquence corpus se compte en dizaines
        // ("bon" 97 contre "bonjou" 17). À 5 points par utilisation, il faut une
        // quinzaine de frappes pour faire remonter le mot réellement employé.
        //
        // Troisième constante de ce fichier calibrée sur une échelle de
        // fréquences qui n'existe plus. Elle valait 5, pour un plafond de 20
        // validations comptées, soit **+100 au maximum** — et le commentaire qui
        // la justifiait invoquait un 99e centile à 88 et des mots créoles
        // culminant à 1 800. Sur le dictionnaire livré aujourd'hui, le 99e
        // centile est à 874 et le maximum à 100 105.
        //
        // Ce qu'un bonus permet, mesuré sur les assets réels : part des préfixes
        // où un mot placé au rang k entre dans les trois suggestions affichées.
        //
        // ```
        // bonus   rang 4  rang 8  rang 12  rang 20  rang 40
        //   +25      73 %    38 %     21 %      8 %      0 %
        //  +100      88 %    69 %     58 %     42 %     16 %   ← ancien plafond
        //  +250      94 %    83 %     76 %     65 %     44 %
        // +1000      99 %    95 %     94 %     91 %     84 %   ← nouveau plafond
        // ```
        //
        // Autrement dit, avec l'ancien réglage, un mot que l'utilisateur avait
        // validé **vingt fois** échouait encore à entrer dans les suggestions
        // depuis le rang 20 dans 58 % des cas. La rampe était trop lente pour que
        // la personnalisation se remarque, alors même que toute la gamification
        // existe pour l'alimenter.
        //
        // La valeur retenue est 20, et non davantage, à cause d'une contrainte
        // que le fichier de tests portait déjà : « quelques utilisations ne
        // suffisent pas à bouleverser le classement » — trois frappes ne doivent
        // pas déloger un mot nettement plus fréquent, le signal personnel doit
        // se confirmer avant de peser. À 50, trois validations (+150) passaient
        // devant un écart de fréquence de 80 et cassaient cette règle. À 20,
        // trois validations valent +60 et la règle tient, tandis que vingt
        // valent +400 — quatre fois l'ancien plafond, et le rang 20 devient
        // joignable dans environ trois cas sur quatre au lieu de deux sur cinq.
        //
        // Le plafond ne change pas : le grief portait sur la lenteur des
        // premières frappes, pas sur les gros utilisateurs.
        private const val USAGE_WEIGHT = 20.0

        // Plafond du bonus d'usage, exprimé en nombre d'utilisations comptées.
        // Il fixe la hiérarchie des trois poids de ce fichier, et cet ordre est
        // délibéré :
        //
        //   usage (≤ 1 000) < contexte (150 000) < distance d'édition (1 000 000)
        //
        // Ce qu'on est en train d'écrire l'emporte sur ce qu'on écrit
        // d'habitude, et une correction orthographique proche l'emporte sur les
        // deux. `SuggestionScoringTest` verrouille cet ordre.
        private const val MAX_COUNTED_USAGES = 20

        /**
         * Écart de score entre deux distances d'édition consécutives.
         *
         * Doit rester strictement supérieur à la plus haute fréquence du
         * dictionnaire livré (~100 000 aujourd'hui) pour que la distance prime
         * toujours sur la fréquence. Voir calculateDictionaryScore().
         */
        internal const val EDIT_DISTANCE_WEIGHT = 1_000_000.0

        /**
         * Poids d'une correspondance avec le contexte n-gramme.
         *
         * Même piège qu'[EDIT_DISTANCE_WEIGHT], découvert de la même façon :
         * le bonus valait 50 face à des fréquences qui montaient à 15 519 en
         * créole — déjà ténu — et qui montent à 100 105 ici. Le score étant
         * dominé par la fréquence corpus, un bonus de 50 ne réordonnait
         * strictement rien : le classement se réduisait à « trier par
         * fréquence », et les n-grammes, tout le travail de la pipeline pour
         * les produire et les 5 Mo qu'ils pèsent dans l'APK, ne servaient plus
         * qu'au mode prédiction pure, quand rien n'est encore tapé.
         *
         * Mesuré sur ParaLux, jeu dont aucune phrase n'est dans le corpus
         * d'entraînement, 1 755 mots en contexte, « le mot est-il proposé dans
         * les trois premiers après k frappes » :
         *
         * ```
         * bonus       @1 frappe   @2 frappes  @3 frappes
         *       0       12,99 %     31,11 %     55,50 %
         *      50       12,99 %     31,40 %     55,84 %   ← livré jusqu'ici
         *   5 000       16,64 %     34,70 %     57,44 %
         *  50 000       21,99 %     35,67 %     57,44 %
         * 150 000       22,22 %     35,67 %     57,44 %   ← retenu
         * ```
         *
         * Entre 0 et 50 l'écart est du bruit : le bonus livré ne faisait rien.
         * La courbe sature une fois le poids passé au-dessus de la fréquence
         * maximale du dictionnaire (100 105), ce qui est exactement l'invariant
         * à tenir : au-delà, seul l'ordre compte et il ne change plus. 150 000
         * garde la même marge que celle prise pour [EDIT_DISTANCE_WEIGHT].
         *
         * Le bonus ne s'applique qu'aux candidats qui correspondent déjà au
         * préfixe tapé : il réordonne des mots que le clavier aurait proposés
         * de toute façon, il n'en introduit aucun. Et il reste très inférieur à
         * [EDIT_DISTANCE_WEIGHT], de sorte qu'une correction orthographique
         * proche continue de primer.
         */
        internal const val NGRAM_CONTEXT_WEIGHT = 150_000.0

        // Nombre de correspondances par préfixe retenues avant scoring. Le
        // dictionnaire est parcouru par fréquence corpus décroissante : une fenêtre
        // trop étroite écarterait un mot rare dans le corpus mais très utilisé par
        // l'utilisateur avant même que son bonus d'usage puisse jouer.
        private const val CANDIDATE_POOL_SIZE = 40
        
        /**
         * Concilie la casse voulue par l'utilisateur et celle que porte le mot
         * du dictionnaire.
         *
         * Le luxembourgeois capitalise tous les substantifs, et le dictionnaire
         * livre depuis le 2026-08-29 la casse canonique de chaque forme
         * (« Joer », « RTL », « an »). La règle est donc : un signal EXPLICITE
         * de l'utilisateur l'emporte, sinon le dictionnaire fait foi.
         *
         * L'ancienne version recopiait systématiquement la casse de la frappe
         * sur la suggestion. C'était juste tant que le dictionnaire était en
         * minuscules ; avec la casse canonique, cela détruisait la majuscule du
         * substantif dès que l'utilisateur tapait en minuscules — « hau »
         * aurait rendu « haus » au lieu de « Haus », et la Groussschreiwung
         * n'aurait jamais été visible.
         *
         * Exemples:
         * - input="hau",  suggestion="Haus"   → "Haus"   (le dictionnaire décide)
         * - input="Hau",  suggestion="Haus"   → "Haus"
         * - input="HAU",  suggestion="Haus"   → "HAUS"
         * - input="bon",  suggestion="bonjou" → "bonjou"
         * - input="Bon",  suggestion="bonjou" → "Bonjou" (majuscule demandée)
         * - input="kaBr", suggestion="kabrit" → "kaBrit" (motif mixte respecté)
         */
        internal fun applyCasingPattern(input: String, suggestion: String): String {
            if (input.isEmpty() || suggestion.isEmpty()) return suggestion

            // Cas 1: Tout en majuscules — au moins 2 lettres, sinon une seule
            // majuscule initiale (shift automatique) mettrait toute la
            // suggestion en capitales ("B" → "BÈL" au lieu de "Bèl")
            val letters = input.filter { it.isLetter() }
            if (letters.length >= 2 && letters.all { it.isUpperCase() }) {
                return suggestion.uppercase()
            }

            // Cas 2: Première lettre majuscule seulement. L'utilisateur réclame
            // une capitale que le mot n'a peut-être pas (début de phrase, nom
            // propre absent du corpus) : on la lui donne.
            if (input[0].isUpperCase() &&
                input.drop(1).all { it.isLowerCase() || !it.isLetter() }) {
                return suggestion.replaceFirstChar { it.uppercase() }
            }

            // Cas 3: Motif mixte — une majuscule ailleurs qu'en tête. Rare, mais
            // c'est un choix délibéré de l'utilisateur, qu'on recopie
            // caractère par caractère comme avant.
            if (input.drop(1).any { it.isUpperCase() }) {
                val result = StringBuilder()
                for (i in suggestion.indices) {
                    if (i < input.length) {
                        val inputChar = input[i]
                        val suggestionChar = suggestion[i]
                        result.append(
                            when {
                                inputChar.isUpperCase() -> suggestionChar.uppercase()
                                inputChar.isLowerCase() -> suggestionChar.lowercase()
                                else -> suggestionChar
                            }
                        )
                    } else {
                        // Au-delà de la longueur de l'input, garder la casse originale
                        result.append(suggestion[i])
                    }
                }
                return result.toString()
            }

            // Cas 4: frappe entièrement en minuscules — aucun signal. La forme
            // du dictionnaire est livrée telle quelle : c'est elle qui porte la
            // Groussschreiwung.
            return suggestion
        }

        /**
         * Calcule un score de pertinence pour une suggestion du dictionnaire
         * `internal` (et non private) pour être testable en JVM sans Context
         *
         * @param levenshteinDistance 0 pour une correspondance par préfixe ;
         *        > 0 pour une correction orthographique (distance d'édition)
         * @param usageCount nombre de fois que l'utilisateur a déjà validé ce mot,
         *        0 quand l'usage personnel n'est pas connu (mots français, tests)
         */
        internal fun calculateDictionaryScore(
            word: String,
            input: String,
            frequency: Int,
            levenshteinDistance: Int = 0,
            usageCount: Int = 0
        ): Double {
            var score = frequency.toDouble()

            // Usage personnel : la fréquence corpus dit ce que le kréyòl écrit
            // emploie en général, pas ce que cet utilisateur-ci écrit. Le compteur
            // alimenté par la gamification (CreoleDictionaryWithUsage) corrige ce
            // décalage. Tout reste sur l'appareil : aucune donnée ne sort, et seuls
            // les mots déjà présents au dictionnaire sont comptés.
            score += minOf(usageCount, MAX_COUNTED_USAGES) * USAGE_WEIGHT

            // Corrections orthographiques : la distance prime sur tout le reste.
            // Une correction à 1 édition doit battre une correction à 2 éditions
            // quelle que soit leur fréquence ("mesli" → "mèsi" avant "mésyé").
            //
            // L'écart entre deux distances vaut EDIT_DISTANCE_WEIGHT ; il doit
            // donc rester supérieur à la plus haute fréquence du dictionnaire,
            // sans quoi un mot très fréquent à 2 éditions repasse devant un mot
            // rare à 1 édition. Le poids valait 100 000 pour un dictionnaire
            // plafonnant à ~15 500 — confortable en créole, mais franchi par le
            // corpus luxembourgeois actuel, où « an » culmine à 100 105 : une
            // correction à 2 éditions vers « an » (200 105) passait devant toute
            // correction à 1 édition d'un mot de fréquence < 105 (200 003).
            // SuggestionScoringTest verrouille l'invariant contre le
            // dictionnaire réellement livré.
            if (levenshteinDistance > 0) {
                score += (3 - levenshteinDistance) * EDIT_DISTANCE_WEIGHT
            }

            // Bonus si le mot commence par l'input, comparaison insensible aux accents :
            // taper "fe" doit favoriser "fè" autant que "fenmen", sinon les graphies
            // créoles correctes sont systématiquement déclassées
            if (AccentTolerantMatcher.startsWith(input, word)) {
                score += 50.0  // Augmenté pour favoriser les correspondances par préfixe
            }

            // Bonus pour les mots courts (plus faciles à taper)
            if (word.length <= 6) {
                score += 10.0
            }

            // Malus pour les mots très longs
            if (word.length > 12) {
                score -= 10.0
            }

            // Bonus pour les mots avec accents (encourage l'apprentissage de l'orthographe correcte)
            if (AccentTolerantMatcher.hasAccents(word)) {
                score += 5.0
            }

            return score
        }

        /**
         * Choisit la clé à interroger dans le modèle N-gram : le contexte à deux mots
         * ("an ka") s'il est présent, sinon le dernier mot seul ("ka").
         * `internal` (et non private) pour être testable en JVM sans Context.
         *
         * @param hasKey test de présence dans le modèle chargé
         */
        internal fun resolveNgramContext(
            previousWord: String?,
            lastWord: String,
            hasKey: (String) -> Boolean
        ): String {
            val twoWordContext = previousWord?.let { "$it $lastWord" }
            return twoWordContext?.takeIf(hasKey) ?: lastWord
        }

        /**
         * Correspondance EXACTE (insensible aux accents) d'un mot dans une liste de formes
         * déjà normalisées. `internal` (et non private) pour être testable en JVM sans
         * Context — cœur logique de `isKnownWord()`.
         */
        /**
         * Choisit, parmi les [candidats] qu'un contexte n-gramme propose, la
         * forme capitalisée correspondant à [word], ou `null`.
         *
         * Cœur de décision de la correction de la Groussschreiwung, isolé du
         * `Context` Android pour rester vérifiable en JVM. La règle : on ne
         * capitalise que ce que le corpus a vu capitalisé **après le mot
         * précédent**, et jamais si l'utilisateur a lui-même mis une majuscule
         * quelque part — ce qu'il a tapé lui appartient.
         */
        internal fun pickContextualCapitalization(
            word: String,
            candidats: List<String>
        ): String? {
            if (word.length < 2) return null
            if (word.any { it.isUpperCase() }) return null

            // Le PREMIER candidat correspondant fait foi, quelle que soit sa
            // casse : les candidats arrivent triés par probabilité
            // décroissante, donc c'est la forme que ce contexte attend. Si
            // c'est la minuscule, il n'y a rien à corriger — aller chercher
            // plus loin une variante capitalisée moins probable reviendrait à
            // imposer une majuscule que le contexte ne demande pas.
            val attendu = candidats.firstOrNull { it.lowercase() == word } ?: return null
            return if (attendu == word) null else attendu
        }

        internal fun isWordKnown(word: String, normalizedWords: List<String>): Boolean {
            if (word.isBlank()) return true // ponctuation/chiffres isolés : ne pas souligner
            return normalizedWords.contains(AccentTolerantMatcher.normalize(word))
        }
    }
    
    // Données du moteur luxembourgeois (existant)
    private var dictionary: List<Pair<String, Int>> = emptyList()
    // Formes normalisées (sans accents) alignées index à index avec `dictionary`,
    // précalculées au chargement pour éviter de normaliser 3600+ mots à chaque frappe
    private var normalizedWords: List<String> = emptyList()
    private var ngramModel: Map<String, List<Map<String, Any>>> = emptyMap()
    private val wordHistory = mutableListOf<String>()

    // Compteur d'utilisations personnelles, fourni par le service IME plutôt
    // qu'instancié ici : le moteur n'a pas à dépendre du paquet gamification, et
    // reste ainsi testable sans Context. Absent (null) tant qu'il n'est pas
    // branché, auquel cas le score se réduit à son ancienne formule.
    private var usageCountProvider: ((String) -> Int)? = null

    /**
     * Branche la source des compteurs d'utilisation personnelle
     * (CreoleDictionaryWithUsage côté service).
     */
    fun setUsageCountProvider(provider: (String) -> Int) {
        usageCountProvider = provider
        Log.d(TAG, "📊 Compteurs d'usage personnel branchés sur le scoring")
    }

    private fun usageCountOf(word: String): Int = usageCountProvider?.invoke(word) ?: 0
    
    // 🇫🇷 Support français (nouveau)
    private lateinit var frenchDictionary: FrenchDictionary
    private var bilingualConfig = BilingualConfig()
    private var isBilingualEnabled = false
    
    // Coroutines pour les opérations asynchrones
    private val suggestionScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Job de la génération en cours : annulé à chaque nouvelle frappe pour
    // qu'un calcul lent (ex: Levenshtein) ne puisse pas écraser un résultat plus récent
    private var suggestionJob: Job? = null
    
    // Modes de suggestion
    enum class SuggestionMode {
        DICTIONARY,    // Suggestions basées sur le dictionnaire (pendant frappe)
        CONTEXTUAL,    // Prédictions contextuelles N-gram (après espace)
        MIXED         // Mode mixte (comportement original)
    }
    
    private var currentMode = SuggestionMode.MIXED
    
    // Callbacks (étendus pour support bilingue)
    interface SuggestionListener {
        fun onSuggestionsReady(suggestions: List<String>)  // Compatibilité existante
        fun onBilingualSuggestionsReady(suggestions: List<BilingualSuggestion>) // Nouveau bilingue
        fun onDictionaryLoaded(wordCount: Int)
        fun onNgramModelLoaded()
        fun onFrenchDictionaryLoaded(wordCount: Int)  // Nouveau
        fun onModeChanged(newMode: SuggestionMode)
    }
    
    private var suggestionListener: SuggestionListener? = null
    
    fun setSuggestionListener(listener: SuggestionListener) {
        this.suggestionListener = listener
    }
    
    /**
     * Change le mode de suggestion
     */
    fun setSuggestionMode(mode: SuggestionMode) {
        if (currentMode != mode) {
            Log.d(TAG, "Changement de mode: $currentMode -> $mode")
            currentMode = mode
            suggestionListener?.onModeChanged(mode)
        }
    }
    
    /**
     * Obtient le mode actuel
     */
    fun getCurrentMode(): SuggestionMode = currentMode
    
    /**
     * Bascule automatiquement vers le mode approprié selon le contexte
     */
    fun switchToAppropriateMode(isTyping: Boolean) {
        val targetMode = if (isTyping) SuggestionMode.DICTIONARY else SuggestionMode.CONTEXTUAL
        setSuggestionMode(targetMode)
    }
    
    /**
     * Initialise le moteur de suggestions (luxembourgeois + français)
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🚀 Initialisation du moteur bilingue...")
            
            // 1. Initialiser dictionnaire français d'abord
            frenchDictionary = FrenchDictionary(context)
            
            // 2. Chargement en parallèle de tous les dictionnaires
            val luxDictDeferred = async { loadDictionary() }
            val ngramDeferred = async { loadNgramModel() }
            val frenchDictDeferred = async { frenchDictionary.initialize() }
            
            // 3. Attendre que tout soit chargé
            luxDictDeferred.await()
            ngramDeferred.await()
            frenchDictDeferred.await()

            removeLegacyPersonalDictionary()

            Log.d(TAG, "✅ Moteur bilingue initialisé:")
            Log.d(TAG, "   🟢 Lëtzebuergesch: ${dictionary.size} mots + ${ngramModel.size} N-grams")
            Log.d(TAG, "   🔵 Français: ${frenchDictionary.getStats()["word_count"]} mots")
            
            // Notifier le chargement du dictionnaire français
            withContext(Dispatchers.Main) {
                val frenchWordCount = frenchDictionary.getStats()["word_count"] as Int
                suggestionListener?.onFrenchDictionaryLoaded(frenchWordCount)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur lors de l'initialisation bilingue: ${e.message}", e)
        }
    }
    
    /**
     * Génère des suggestions pour un texte d'entrée (méthode générale - conservée pour compatibilité)
     */
    fun generateSuggestions(input: String) {
        // 🎯 REDIRECTION: Si mode bilingue activé, utiliser la logique bilingue
        if (isBilingualEnabled) {
            generateBilingualSuggestions(input)
            return
        }
        
        // Logique originale pour rétrocompatibilité
        if (input.length < MIN_WORD_LENGTH) {
            suggestionListener?.onSuggestionsReady(emptyList())
            return
        }
        
        suggestionJob?.cancel()
        suggestionJob = suggestionScope.launch {
            val suggestions = withContext(Dispatchers.Default) {
                val dictionarySuggestions = getDictionarySuggestions(input)
                val ngramSuggestions = getNgramSuggestions()

                // Fusion et déduplication des suggestions
                mergeAndRankSuggestions(dictionarySuggestions, ngramSuggestions, input)
            }
            
            // Appliquer la casse de l'input aux suggestions
            val casedSuggestions = suggestions.map { applyCasingPattern(input, it) }
            
            suggestionListener?.onSuggestionsReady(casedSuggestions)
        }
    }
    

    
    /**
     * 🎯 Active le support bilingue Lëtzebuergesch + Français
     */
    fun enableBilingualSupport() {
        isBilingualEnabled = true
        Log.d(TAG, "🟢🔵 Support bilingue activé - Dictionnaire français: ${frenchDictionary.getLoadedWordCount()} mots")
    }

    /**
     * 🎯 NOUVELLE MÉTHODE PRINCIPALE: Génère des suggestions bilingues intelligentes
     * Logique: Lëtzebuergesch prioritaire, Français à partir de 3 lettres
     */
    fun generateBilingualSuggestions(input: String) {
        if (input.length < MIN_WORD_LENGTH) {
            suggestionListener?.onSuggestionsReady(emptyList())
            suggestionListener?.onBilingualSuggestionsReady(emptyList())
            return
        }
        
        suggestionJob?.cancel()
        suggestionJob = suggestionScope.launch {
            val suggestions = withContext(Dispatchers.Default) {
                createBilingualSuggestions(input)
            }
            
            // Appliquer la casse de l'input aux suggestions
            val casedSuggestions = suggestions.map { suggestion ->
                suggestion.copy(word = applyCasingPattern(input, suggestion.word))
            }
            
            // Notifier avec les deux formats pour compatibilité
            val simpleWords = casedSuggestions.map { it.word }
            suggestionListener?.onSuggestionsReady(simpleWords)
            suggestionListener?.onBilingualSuggestionsReady(casedSuggestions)
            
            Log.d(TAG, "🎯 Suggestions bilingues pour '$input': ${simpleWords}")
        }
    }
    
    /**
     * Crée les suggestions bilingues selon la stratégie Lëtzebuergesch-First
     * 💙 PRIORITÉ ABSOLUE: Détection séquences mémoire pour papa Saint-Ange
     */
    private fun createBilingualSuggestions(input: String): List<BilingualSuggestion> {
        // 1. 🟢 TOUJOURS obtenir suggestions luxembourgeoises (priorité absolue)
        val luxSuggestions = getLuxSuggestions(input)
        
        // 2. 🔵 Obtenir suggestions françaises SEULEMENT si 3+ lettres
        val frenchSuggestions = if (bilingualConfig.shouldActivateFrench(input)) {
            getFrenchSuggestions(input)
        } else {
            Log.d(TAG, "Français désactivé pour '$input' (${input.length} < ${bilingualConfig.frenchActivationThreshold} lettres)")
            emptyList()
        }
        
        // 3. 🎯 Fusion avec priorité luxembourgeoise stricte
        return mergeSuggestionsLuxFirst(luxSuggestions, frenchSuggestions)
    }
    
    /**
     * Obtient les suggestions luxembourgeoises (existant + adapté)
     */
    private fun getLuxSuggestions(input: String): List<BilingualSuggestion> {
        val dictionaryMatches = getDictionarySuggestions(input)
        val ngramMatches = if (wordHistory.isNotEmpty()) getNgramSuggestions() else emptyList()
        
        // Fusionner dictionnaire + n-grams luxembourgeois
        val allLux = mutableMapOf<String, Float>()
        
        // Ajouter suggestions dictionnaire
        dictionaryMatches.forEach { (word, frequency, distance) ->
            val score = calculateDictionaryScore(word, input, frequency, distance, usageCountOf(word))
            allLux[word] = score.toFloat()
        }
        
        // Ajouter suggestions n-gram avec bonus (uniquement si le mot correspond
        // au préfixe tapé : le n-gramme prédit le mot suivant probable d'après le
        // contexte, ce qui n'a aucun rapport avec ce que l'utilisateur est en train
        // de taper — sans ce filtre, un mot sans aucune correspondance kréyòl
        // (ex. "Ordinateur") affichait quand même 3 mots kréyòl sans rapport)
        ngramMatches
            .filter { it.startsWith(input, ignoreCase = true) }
            .forEach { word ->
                val currentScore = allLux[word] ?: 0f
                allLux[word] = currentScore + NGRAM_CONTEXT_WEIGHT.toFloat()
            }
        
        // Convertir en BilingualSuggestion et appliquer boost luxembourgeois + casse
        return allLux.entries
            .map { (word, score) ->
                val casedWord = applyCasingPattern(input, word)
                val adjustedScore = bilingualConfig.adjustScoreByLanguage(score, SuggestionLanguage.LUXEMBOURGISH)
                BilingualSuggestion(casedWord, adjustedScore, SuggestionLanguage.LUXEMBOURGISH, SuggestionSource.HYBRID)
            }
            .sortedByDescending { it.score }
            .take(bilingualConfig.maxLuxSuggestions)
    }
    
    /**
     * Obtient les suggestions françaises (nouveau)
     */
    private fun getFrenchSuggestions(input: String): List<BilingualSuggestion> {
        if (!::frenchDictionary.isInitialized) {
            Log.w(TAG, "Dictionnaire français non initialisé")
            return emptyList()
        }
        
        val frenchWords = frenchDictionary.getSuggestions(input)
        
        return frenchWords.map { word ->
            val casedWord = applyCasingPattern(input, word)
            val frequency = frenchDictionary.getWordFrequency(word)
            val baseScore = calculateDictionaryScore(word, input, frequency)
            val adjustedScore = bilingualConfig.adjustScoreByLanguage(baseScore.toFloat(), SuggestionLanguage.FRENCH)
            
            BilingualSuggestion(casedWord, adjustedScore, SuggestionLanguage.FRENCH, SuggestionSource.DICTIONARY)
        }.sortedByDescending { it.score }
    }
    
    /**
     * 🎯 FUSION LUXEMBOURGEOIS-FIRST: Positions 1-3 réservées au luxembourgeois, 4-5 français optionnel
     */
    private fun mergeSuggestionsLuxFirst(
        luxSuggs: List<BilingualSuggestion>,
        frenchSuggs: List<BilingualSuggestion>
    ): List<BilingualSuggestion> {
        
        val result = mutableListOf<BilingualSuggestion>()
        val usedWords = mutableSetOf<String>()
        
        // 1. 🟢 POSITIONS 1-3: Toujours luxembourgeois d'abord
        luxSuggs.take(3).forEach { suggestion ->
            if (!usedWords.contains(suggestion.word.lowercase())) {
                result.add(suggestion)
                usedWords.add(suggestion.word.lowercase())
            }
        }
        
        // 2. 🔵 POSITIONS 4-5: Français si disponible et pertinent
        frenchSuggs.take(2).forEach { suggestion ->
            if (result.size < MAX_SUGGESTIONS && 
                !usedWords.contains(suggestion.word.lowercase())) {
                result.add(suggestion)
                usedWords.add(suggestion.word.lowercase())
            }
        }
        
        // 3. 🟢 COMPLÉTER avec plus de luxembourgeois si pas assez de français
        luxSuggs.drop(3).forEach { suggestion ->
            if (result.size < MAX_SUGGESTIONS && 
                !usedWords.contains(suggestion.word.lowercase())) {
                result.add(suggestion)
                usedWords.add(suggestion.word.lowercase())
            }
        }
        
        Log.d(TAG, "🎯 Fusion finale: ${result.size} suggestions (Lëtzebuergesch: ${result.count { it.language == SuggestionLanguage.LUXEMBOURGISH }}, Français: ${result.count { it.language == SuggestionLanguage.FRENCH }})")
        
        return result
    }

    /**
     * Génère des suggestions basées uniquement sur le dictionnaire (mode frappe)
     * Optimisé pour la saisie en temps réel pendant que l'utilisateur tape
     * ⚠️  DEPRECATED: Utiliser generateBilingualSuggestions() à la place
     */
    fun generateDictionarySuggestions(input: String) {
        // 🎯 REDIRECTION: Si mode bilingue activé, utiliser la logique bilingue
        if (isBilingualEnabled) {
            generateBilingualSuggestions(input)
            return
        }
        
        // Logique originale pour rétrocompatibilité
        if (input.length < MIN_WORD_LENGTH) {
            suggestionListener?.onSuggestionsReady(emptyList())
            return
        }
        
        suggestionJob?.cancel()
        suggestionJob = suggestionScope.launch {
            val suggestions = withContext(Dispatchers.Default) {
                val dictionaryMatches = getDictionarySuggestions(input)
                
                // Trier uniquement par score de dictionnaire (fréquence + proximité + distance)
                dictionaryMatches
                    .map { (word, frequency, distance) ->
                        Pair(word, calculateDictionaryScore(word, input, frequency, distance, usageCountOf(word)))
                    }
                    .sortedByDescending { it.second }
                    .take(MAX_SUGGESTIONS)
                    .map { applyCasingPattern(input, it.first) }
            }
            
            Log.d(TAG, "Suggestions dictionnaire: $suggestions")
            suggestionListener?.onSuggestionsReady(suggestions)
        }
    }
    
    /**
     * Génère des prédictions contextuelles basées sur les N-grams (mode prédiction)
     * Utilisé après qu'un mot soit complété pour prédire le mot suivant
     */
    fun generateContextualSuggestions() {
        suggestionJob?.cancel()
        suggestionJob = suggestionScope.launch {
            val predictions = withContext(Dispatchers.Default) {
                if (wordHistory.isEmpty() || ngramModel.isEmpty()) {
                    emptyList()
                } else {
                    getNgramSuggestions()
                }
            }
            
            Log.d(TAG, "Prédictions contextuelles: $predictions")
            suggestionListener?.onSuggestionsReady(predictions)
        }
    }
    
    /**
     * Forme capitalisée que le contexte atteste pour [word], ou `null`.
     *
     * Sert la correction automatique de la Groussschreiwung à la frappe. Le
     * garde-fou tient en une phrase : **on ne capitalise que ce que le corpus a
     * réellement vu capitalisé après le mot précédent**, jamais sur la seule
     * casse canonique du dictionnaire.
     *
     * La différence n'est pas théorique. Appliquer la casse canonique à tout mot
     * connu abîmerait 3,5 % des phrases correctement écrites (mesuré sur
     * ParaLux), et surtout capitaliserait **161 des 662 mots du dictionnaire
     * français de secours** — `rue`, `moment`, `centre`, `chambre`, `route`,
     * `santé`, `café`… Au Luxembourg, où l'on passe d'une langue à l'autre dans
     * la même conversation, un mot sur quatre d'un message en français se
     * retrouverait capitalisé au milieu de la phrase.
     *
     * Le contexte filtre ce cas tout seul : dans « la rue de la gare », le mot
     * précédent est `la`, qui n'est pas un contexte luxembourgeois connu, donc
     * rien ne se déclenche. Dans « an der rue », le contexte `an der` atteste
     * `Rue`, et la correction tombe juste.
     *
     * Rend `null` dès que l'utilisateur a donné le moindre signal de casse : ce
     * qu'il a tapé en majuscules lui appartient.
     */
    fun contextualCapitalization(word: String): String? {
        if (ngramModel.isEmpty()) return null
        val lastWord = wordHistory.lastOrNull() ?: return null
        val previousWord = wordHistory.getOrNull(wordHistory.size - 2)
        val context = resolveNgramContext(previousWord, lastWord) { ngramModel.containsKey(it) }
        val candidats = ngramModel[context] ?: return null
        return pickContextualCapitalization(
            word,
            candidats.mapNotNull { it["word"] as? String }
        )
    }

    /**
     * Ajoute un mot à l'historique pour les N-grams
     */
    fun addWordToHistory(word: String) {
        val cleanWord = word.lowercase().trim()
        if (cleanWord.isNotEmpty() && cleanWord.length >= MIN_WORD_LENGTH) {
            wordHistory.add(cleanWord)
            
            // Maintenir l'historique à une taille raisonnable
            if (wordHistory.size > MAX_WORD_HISTORY) {
                wordHistory.removeAt(0)
            }
            
            Log.d(TAG, "Mot ajouté à l'historique: $cleanWord")
        }
    }
    
    /**
     * Efface l'historique des mots
     */
    fun clearHistory() {
        wordHistory.clear()
    }
    
    /**
     * Efface le dictionnaire personnel laissé par la 10.5.0.
     *
     * Cette version apprenait les mots absents du corpus pour les suggérer. La
     * fonction a été retirée en 10.6.0 : un clavier qui conserve des mots tapés
     * par l'utilisateur, si encadré soit-il, reste un clavier qui conserve ce
     * qu'on écrit. Retirer le code ne suffit pas, il faut aussi effacer ce qui a
     * déjà été écrit sur les appareils l'ayant installée.
     */
    private fun removeLegacyPersonalDictionary() {
        try {
            val legacyFile = java.io.File(context.filesDir, "personal_dict.json")
            if (legacyFile.exists() && legacyFile.delete()) {
                Log.d(TAG, "🧹 Dictionnaire personnel de la 10.5.0 effacé")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Effacement du dictionnaire personnel impossible: ${e.message}", e)
        }
    }

    /**
     * Correspondance EXACTE (insensible aux accents) dans le dictionnaire créole OU
     * français — contrairement à getDictionarySuggestions() qui fait une recherche par
     * préfixe. Utilisé par KreyolSpellCheckerService pour décider si un mot doit être
     * souligné comme faute par le correcteur orthographique système.
     */
    fun isKnownWord(word: String): Boolean {
        if (isWordKnown(word, normalizedWords)) return true
        return ::frenchDictionary.isInitialized && frenchDictionary.containsWord(word)
    }

    /**
     * Suggestions de correction pour un mot absent des deux dictionnaires, en réutilisant
     * telle quelle la logique Levenshtein + scoring existante (chantier G) — la casse de
     * `word` est reportée sur chaque suggestion, comme pour la frappe normale.
     */
    fun getSpellingSuggestions(word: String, maxResults: Int = MAX_SUGGESTIONS): List<String> {
        return getSpellCorrectionSuggestions(word)
            .take(maxResults)
            .map { applyCasingPattern(word, it.first) }
    }

    /**
     * Charge le dictionnaire depuis les assets
     */
    private suspend fun loadDictionary() = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.assets.open("luxemburgish_dict.json").bufferedReader().use { it.readText() }
            val wordsArray = JSONArray(jsonString)
            
            val loadedDictionary = mutableListOf<Pair<String, Int>>()
            
            for (i in 0 until wordsArray.length()) {
                val wordArray = wordsArray.getJSONArray(i)
                // La casse livrée est conservée : c'est elle qui porte la
                // Groussschreiwung (« Joer », « RTL »). Les comparaisons se
                // font par ailleurs sur `normalizedWords`, qui replie déjà.
                val word = wordArray.getString(0)
                val frequency = wordArray.optInt(1, 1)
                loadedDictionary.add(Pair(word, frequency))
            }
            
            // Trier par fréquence décroissante
            dictionary = loadedDictionary.sortedByDescending { it.second }
            normalizedWords = dictionary.map { AccentTolerantMatcher.normalize(it.first) }

            withContext(Dispatchers.Main) {
                suggestionListener?.onDictionaryLoaded(dictionary.size)
            }
            
            Log.d(TAG, "Dictionnaire chargé: ${dictionary.size} mots")

        } catch (e: Exception) {
            // Pas seulement IOException : un format inattendu (ex. objet {mot: fréquence}
            // au lieu du tableau [[mot, fréquence], ...] attendu) lève une JSONException,
            // qui n'est pas une IOException et passait donc au travers, laissant le
            // dictionnaire vide sans aucune suggestion ni erreur visible (v10.2.6).
            Log.e(TAG, "Erreur lors du chargement du dictionnaire: ${e.message}", e)
        }
    }
    
    /**
     * Charge le modèle N-gram depuis les assets
     */
    private suspend fun loadNgramModel() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Chargement du modèle N-grams...")
        try {
            val inputStream = context.assets.open("luxemburgish_ngrams.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            inputStream.close()

            val jsonObject = JSONObject(jsonString)
            
            // Le fichier JSON est directement {mot: [{word, probability}]}
            // Pas besoin de wrapper "predictions"
            val tempMap = mutableMapOf<String, List<Map<String, Any>>>()
            
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val predictionsArray = jsonObject.getJSONArray(key)
                val predictions = mutableListOf<Map<String, Any>>()
                
                for (i in 0 until predictionsArray.length()) {
                    val predictionObj = predictionsArray.getJSONObject(i)
                    val prediction = mapOf(
                        "word" to predictionObj.getString("word"),
                        "probability" to predictionObj.getDouble("probability")
                    )
                    predictions.add(prediction)
                }
                
                tempMap[key] = predictions
            }
            
            ngramModel = tempMap.toMap()
            
            Log.d(TAG, "Modèle N-grams chargé avec ${ngramModel.size} entrées")
            
            withContext(Dispatchers.Main) {
                suggestionListener?.onNgramModelLoaded()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du chargement des N-grams", e)
        }
    }
    
    /**
     * Obtient les suggestions depuis le dictionnaire (recherche par préfixe insensible aux accents)
     * avec correction orthographique en secours
     */
    private fun getDictionarySuggestions(input: String): List<Triple<String, Int, Int>> {
        if (input.length < MIN_WORD_LENGTH) return emptyList()

        // Recherche préfixe sur les formes normalisées précalculées.
        // `dictionary` est trié par fréquence décroissante, mais on ne peut plus
        // s'arrêter aux tout premiers matches depuis que l'usage personnel entre
        // dans le score : un mot rare dans le corpus et pourtant très employé par
        // l'utilisateur se trouve loin dans ce classement, et une fenêtre étroite
        // l'écarterait avant même que son bonus puisse jouer.
        // Distance 0 = correspondance par préfixe (pas une correction).
        val normalizedInput = AccentTolerantMatcher.normalize(input)
        val matches = mutableListOf<Triple<String, Int, Int>>()
        for (i in dictionary.indices) {
            if (normalizedWords[i].startsWith(normalizedInput)) {
                matches.add(Triple(dictionary[i].first, dictionary[i].second, 0))
                if (matches.size >= CANDIDATE_POOL_SIZE) break
            }
        }

        // ✨ Si aucune correspondance par préfixe, essayer la correction orthographique
        if (matches.isEmpty() && input.length >= 3) {
            return getSpellCorrectionSuggestions(input)
        }

        return matches
    }
    
    /**
     * Obtient les suggestions de correction orthographique en utilisant la distance de Levenshtein
     * 🔧 CORRECTION ORTHOGRAPHIQUE: Trouve les mots les plus proches même avec des fautes
     * 
     * Utilisé comme solution de secours lorsque la recherche par préfixe ne retourne rien.
     * Détecte et corrige:
     * - Les lettres mélangées: "bonjo" → "bonjou"
     * - Les fautes d'orthographe: "mesli" → "mèsi"
     * - Les lettres manquantes/en trop: "letzebuergesch" → "lëtzebuergesch"
     * 
     * @param input Le mot saisi par l'utilisateur (potentiellement mal orthographié)
     * @return Liste de (mot, fréquence, distance) triée par pertinence (distance + fréquence)
     */
    private fun getSpellCorrectionSuggestions(input: String): List<Triple<String, Int, Int>> {
        if (input.length < 3) return emptyList()
        
        // Essayer d'abord avec la normalisation des accents (combinaison puissante)
        val normalizedMatches = LevenshteinDistance.findClosestMatchesNormalized(
            input = input,
            dictionary = dictionary,
            normalizer = { str -> AccentTolerantMatcher.normalize(str) },
            maxDistance = 2,
            maxResults = MAX_SUGGESTIONS
        )
        
        // Si on trouve des correspondances normalisées, les retourner
        if (normalizedMatches.isNotEmpty()) {
            Log.d(TAG, "✓ Correction orthographique (normalisée) pour '$input': ${normalizedMatches.take(3).map { it.first }}")
            return normalizedMatches
        }
        
        // Sinon, essayer sans normalisation (peut détecter d'autres types d'erreurs)
        val directMatches = LevenshteinDistance.findClosestMatches(
            input = input,
            dictionary = dictionary,
            maxDistance = 2,
            maxResults = MAX_SUGGESTIONS
        )
        
        if (directMatches.isNotEmpty()) {
            Log.d(TAG, "✓ Correction orthographique (directe) pour '$input': ${directMatches.take(3).map { it.first }}")
        }
        
        return directMatches
    }
    
    /**
     * Obtient les suggestions depuis le modèle N-gram (optimisé pour mode contextuel)
     *
     * Le modèle porte deux familles de clés : un mot ("ka") et deux mots séparés par
     * une espace ("an ka"). Le contexte à deux mots est essayé en premier car il est
     * nettement plus précis, avec repli sur un seul mot quand la paire est absente.
     * Aucune collision n'est possible entre les deux familles, la tokenisation du
     * pipeline excluant les espaces.
     *
     * L'historique retenait déjà cinq mots (MAX_WORD_HISTORY) alors que seul le
     * dernier servait ; le modèle n'exportait par ailleurs que des bigrammes, les
     * trigrammes étant calculés puis jetés côté pipeline.
     */
    private fun getNgramSuggestions(): List<String> {
        val lastWord = wordHistory.lastOrNull() ?: return emptyList()
        val previousWord = wordHistory.getOrNull(wordHistory.size - 2)

        val context = resolveNgramContext(previousWord, lastWord) { ngramModel.containsKey(it) }
        val ngramList = ngramModel[context] ?: return emptyList()

        Log.d(TAG, "N-gram contexte retenu: '$context'")

        val suggestions = mutableListOf<Pair<String, Double>>()
        ngramList.forEach { ngramEntry ->
            val word = ngramEntry["word"] as? String
            val prob = (ngramEntry["probability"] as? Number)?.toDouble() ?: 0.0

            if (word != null && suggestions.none { it.first == word }) {
                suggestions.add(Pair(word, prob))
            }
        }

        // Trier par probabilité décroissante et retourner les meilleures
        return suggestions
            .sortedByDescending { it.second }
            .take(MAX_SUGGESTIONS)
            .map { it.first }
    }
    
    /**
     * Fusionne et classe les suggestions par pertinence
     */
    private fun mergeAndRankSuggestions(
        dictionarySuggestions: List<Triple<String, Int, Int>>,
        ngramSuggestions: List<String>,
        input: String
    ): List<String> {
        val allSuggestions = mutableMapOf<String, Double>()

        // Ajouter les suggestions du dictionnaire avec score basé sur la fréquence et la position
        dictionarySuggestions.forEach { (word, frequency, distance) ->
            val casedWord = applyCasingPattern(input, word)
            val score = calculateDictionaryScore(word, input, frequency, distance, usageCountOf(word))
            allSuggestions[casedWord] = score
        }
        
        // Ajouter les suggestions N-gram avec un bonus de contexte
        ngramSuggestions.forEach { word ->
            val casedWord = applyCasingPattern(input, word)
            val currentScore = allSuggestions[casedWord] ?: 0.0
            allSuggestions[casedWord] = currentScore + NGRAM_CONTEXT_WEIGHT
        }
        
        // Trier par score et retourner les meilleures
        return allSuggestions
            .entries
            .sortedByDescending { it.value }
            .take(MAX_SUGGESTIONS)
            .map { it.key }
    }
    
    /**
     * 🔧 Configuration du mode bilingue
     */
    fun setBilingualConfig(config: BilingualConfig) {
        bilingualConfig = config
        Log.d(TAG, "Configuration bilingue mise à jour: français activé=${config.enableFrenchSupport}, seuil=${config.frenchActivationThreshold}")
    }
    
    fun getBilingualConfig(): BilingualConfig = bilingualConfig
    
    /**
     * Active/désactive le support français
     */
    fun setFrenchSupport(enabled: Boolean) {
        bilingualConfig = bilingualConfig.copy(enableFrenchSupport = enabled)
        Log.d(TAG, "Support français: $enabled")
    }
    
    /**
     * Active/désactive le mode Lëtzebuergesch uniquement
     */
    fun setLuxOnlyMode(luxOnly: Boolean) {
        bilingualConfig = bilingualConfig.copy(luxOnlyMode = luxOnly)
        Log.d(TAG, "Mode Lëtzebuergesch seul: $luxOnly")
    }
    
    /**
     * Définit le seuil d'activation du français (nombre de lettres)
     */
    fun setFrenchActivationThreshold(threshold: Int) {
        bilingualConfig = bilingualConfig.copy(frenchActivationThreshold = threshold)
        Log.d(TAG, "Seuil activation français: $threshold lettres")
    }
    
    /**
     * Obtient les statistiques du moteur bilingue
     */
    fun getBilingualStats(): Map<String, Any> {
        val frenchStats = if (::frenchDictionary.isInitialized) {
            frenchDictionary.getStats()
        } else {
            mapOf("loaded" to false, "word_count" to 0)
        }
        
        return mapOf(
            "lux_words" to dictionary.size,
            "lux_ngrams" to ngramModel.size,
            "french_loaded" to (frenchStats["loaded"] as Boolean),
            "french_words" to (frenchStats["word_count"] as Int),
            "config" to mapOf(
                "french_support" to bilingualConfig.enableFrenchSupport,
                "activation_threshold" to bilingualConfig.frenchActivationThreshold,
                "lux_only" to bilingualConfig.luxOnlyMode
            )
        )
    }

    /**
     * Nettoie les ressources (luxembourgeois + français)
     */
    fun cleanup() {
        suggestionScope.cancel()
        dictionary = emptyList()
        normalizedWords = emptyList()
        ngramModel = emptyMap()
        wordHistory.clear()
        
        // Nettoyer ressources françaises
        if (::frenchDictionary.isInitialized) {
            frenchDictionary.cleanup()
        }
        
        suggestionListener = null
        Log.d(TAG, "Moteur bilingue nettoyé")
    }
}
