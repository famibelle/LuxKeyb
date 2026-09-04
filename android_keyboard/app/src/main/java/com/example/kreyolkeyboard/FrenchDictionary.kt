package com.example.kreyolkeyboard

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.IOException

/**
 * Dictionnaire français du clavier, en deux paliers.
 *
 * Le but n'est pas d'écrire en français mais d'**insérer des mots français dans
 * une frappe luxembourgeoise**, et les deux consommateurs n'ont donc pas les
 * mêmes besoins :
 *
 * - `suggest` (71 586 formes, avec leur fréquence) remplit la seconde rangée de
 *   suggestions, à partir de [MIN_ACTIVATION_LENGTH] lettres ;
 * - `spellcheck` (53 762 formes verbales rares, sans fréquence) n'est jamais
 *   proposé, mais [containsWord] l'accepte — c'est ce qui empêche le correcteur
 *   système de souligner du français correct, la locale `fr` étant déclarée
 *   dans `res/xml/kreyol_spellchecker.xml`.
 *
 * Le partage est **grammatical et non fréquentiel**, et il est mesuré : sur les
 * insertions françaises relevées dans un corpus luxembourgeois, écarter les
 * formes verbales rares (`réagissent`, `chanteriez`, `finissions`) coûte
 * 3 points de propositions, un seuil de fréquence à 2 en coûte 12 —
 * `résilience`, `incitatif` et `législation` sont au plancher de fréquence et
 * sont précisément ce qu'on insère.
 *
 * ## Ce que les structures évitent
 *
 * Ce chargeur tient dans le processus de saisie, qui est tué en arrière-plan
 * quand il pèse trop : le clavier doit alors tout recharger au prochain champ,
 * et l'utilisateur voit un blanc. D'où trois choix :
 *
 * - **pas de `Pair` ni d'`Int` boxé** : deux tableaux parallèles, et un index
 *   de préfixes qui ne porte que des indices ;
 * - **pas de liste de mots pour le correcteur** : un filtre de Bloom de 146 Ko
 *   remplace 125 348 chaînes, soit ~7 Mo. Voir [containsWord] ;
 * - **pas d'arbre JSON par entrée** : l'actif livre trois tableaux plats, si
 *   bien qu'`org.json` n'alloue pas 125 348 objets intermédiaires au moment
 *   précis où le clavier doit s'afficher.
 */
class FrenchDictionary(private val context: Context) {

    companion object {
        private const val TAG = "FrenchDictionary"
        private const val FRENCH_DICT_FILE = "french_simple_dict.json"
        private const val MIN_ACTIVATION_LENGTH = 3
        private const val MAX_FRENCH_SUGGESTIONS = 2  // Maximum 2 suggestions françaises
        private const val MAX_CACHE_ENTRIES = 1000

        // Le hachage et le décodage vivent dans `BloomFilter`, partagés avec
        // le filtre luxembourgeois : une seule implémentation côté Kotlin,
        // une seule côté Python.
        internal fun bloomContient(
            mot: String, bloom: ByteArray, bits: Long, hachages: Int
        ): Boolean = BloomFilter.contient(mot.lowercase(), bloom, bits, hachages)

        internal fun decoderBase64(texte: String): ByteArray =
            BloomFilter.decoderBase64(texte)
    }

    // Palier proposable, livré trié par fréquence décroissante : les deux
    // tableaux sont alignés indice à indice.
    private var suggestMots: Array<String> = emptyArray()
    private var suggestFreq: IntArray = IntArray(0)

    // Indices dans `suggestMots`, groupés par les MIN_ACTIVATION_LENGTH
    // premières lettres — toujours disponibles, le français ne s'activant pas
    // avant. Chaque seau garde l'ordre de fréquence décroissante de la source.
    private var prefixIndex: Map<String, IntArray> = emptyMap()

    // Filtre de Bloom sur **toutes** les formes, les deux paliers confondus.
    // Voir [containsWord] pour pourquoi un filtre plutôt qu'une liste de mots.
    private var bloom: ByteArray = ByteArray(0)
    private var bloomBits: Long = 0
    private var bloomHachages: Int = 0
    private var nombreFormes: Int = 0

    private var isLoaded = false

    // Cache pour optimiser les recherches répétées
    private val suggestionCache = mutableMapOf<String, List<String>>()

    /**
     * Initialise le dictionnaire français de manière asynchrone
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isLoaded) return@withContext

        try {
            Log.d(TAG, "🇫🇷 Chargement dictionnaire français...")

            val jsonString = context.assets.open(FRENCH_DICT_FILE)
                .bufferedReader().use { it.readText() }

            val racine = JSONObject(jsonString)
            val mots = racine.getJSONArray("suggest_mots")
            val freqs = racine.getJSONArray("suggest_freq")

            val n = minOf(mots.length(), freqs.length())
            val lesMots = Array(n) { mots.getString(it).lowercase() }
            val lesFreqs = IntArray(n) { freqs.optInt(it, 1) }

            // Un seau par préfixe, construit en deux passes pour n'allouer
            // qu'un IntArray de la taille exacte : les listes intermédiaires
            // d'un groupBy coûteraient plus cher que l'index lui-même.
            val tailles = HashMap<String, Int>()
            for (mot in lesMots) {
                if (mot.length >= MIN_ACTIVATION_LENGTH) {
                    val cle = mot.substring(0, MIN_ACTIVATION_LENGTH)
                    tailles[cle] = (tailles[cle] ?: 0) + 1
                }
            }
            val seaux = HashMap<String, IntArray>(tailles.size * 2)
            val remplis = HashMap<String, Int>(tailles.size * 2)
            for ((cle, taille) in tailles) seaux[cle] = IntArray(taille)
            for (i in lesMots.indices) {
                val mot = lesMots[i]
                if (mot.length < MIN_ACTIVATION_LENGTH) continue
                val cle = mot.substring(0, MIN_ACTIVATION_LENGTH)
                val pos = remplis[cle] ?: 0
                seaux[cle]!![pos] = i
                remplis[cle] = pos + 1
            }

            suggestMots = lesMots
            suggestFreq = lesFreqs
            prefixIndex = seaux
            bloom = decoderBase64(racine.getString("bloom"))
            bloomBits = racine.getLong("bloom_bits")
            bloomHachages = racine.getInt("bloom_hachages")
            nombreFormes = racine.optInt("word_count", n)
            isLoaded = bloomBits > 0 && bloomHachages > 0 && bloom.isNotEmpty()

            Log.d(TAG, "✅ Dictionnaire français chargé : ${lesMots.size} proposables, " +
                "$nombreFormes reconnues via un filtre de ${bloom.size / 1024} Ko, " +
                "${seaux.size} préfixes")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur chargement dictionnaire français: ${e.message}", e)
            suggestMots = emptyArray()
            suggestFreq = IntArray(0)
            prefixIndex = emptyMap()
            bloom = ByteArray(0)
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
            return emptyList()
        }

        if (!isLoaded || suggestMots.isEmpty()) {
            return emptyList()
        }

        val cacheKey = prefix.lowercase()
        suggestionCache[cacheKey]?.let { return it }

        val suggestions = searchFrenchWords(cacheKey)

        // Le cache absorbe les frappes répétées d'une même saisie, pas une
        // session entière : au-delà d'un millier d'entrées il repart de zéro.
        if (suggestionCache.size >= MAX_CACHE_ENTRIES) suggestionCache.clear()
        suggestionCache[cacheKey] = suggestions

        return suggestions
    }

    /**
     * Les deux meilleures formes du seau du préfixe : fréquence décroissante
     * d'abord, mots courts préférés à égalité — l'ordre d'avant, mais choisi
     * en une passe au lieu d'un tri.
     */
    private fun searchFrenchWords(prefixLower: String): List<String> {
        val seau = prefixIndex[prefixLower.substring(0, MIN_ACTIVATION_LENGTH)]
            ?: return emptyList()

        var i1 = -1
        var i2 = -1
        for (i in seau) {
            val mot = suggestMots[i]
            if (!mot.startsWith(prefixLower)) continue
            when {
                i1 < 0 -> i1 = i
                meilleur(i, i1) -> { i2 = i1; i1 = i }
                i2 < 0 || meilleur(i, i2) -> i2 = i
            }
        }
        return when {
            i1 < 0 -> emptyList()
            i2 < 0 -> listOf(suggestMots[i1])
            else -> listOf(suggestMots[i1], suggestMots[i2])
        }
    }

    private fun meilleur(a: Int, b: Int): Boolean {
        if (suggestFreq[a] != suggestFreq[b]) return suggestFreq[a] > suggestFreq[b]
        return suggestMots[a].length < suggestMots[b].length
    }

    /**
     * Ce mot est-il du français ? C'est la seule question que pose le
     * correcteur orthographique, et un filtre de Bloom y répond mieux qu'une
     * liste de mots parce que **ses deux erreurs sont asymétriques** :
     *
     * - il ne peut **jamais** rejeter une forme qu'on y a mise, donc il ne
     *   peut pas faire souligner un mot français correct — l'exact défaut que
     *   ce dictionnaire existe pour réparer ;
     * - il accepte à tort environ 1 % des chaînes qu'on n'y a pas mises, donc
     *   il laisse passer une faute de frappe de temps en temps. Dans un clavier
     *   luxembourgeois, ne pas souligner une faute française est sans
     *   conséquence.
     *
     * 146 Ko au lieu des ~7 Mo qu'occupaient 125 348 chaînes, dans un processus
     * de saisie qu'Android tue quand il grossit.
     */
    fun containsWord(word: String): Boolean {
        if (!isLoaded) return false
        return bloomContient(word, bloom, bloomBits, bloomHachages)
    }

    /**
     * Fréquence d'une forme proposable, 0 pour les autres — elles ne sont
     * jamais classées, donc la valeur ne sert à rien.
     */
    fun getWordFrequency(word: String): Int {
        if (!isLoaded) return 0
        val mot = word.lowercase()
        if (mot.length < MIN_ACTIVATION_LENGTH) return 0
        val seau = prefixIndex[mot.substring(0, MIN_ACTIVATION_LENGTH)] ?: return 0
        for (i in seau) if (suggestMots[i] == mot) return suggestFreq[i]
        return 0
    }

    /**
     * Détermine si le préfixe devrait activer les suggestions françaises
     */
    fun shouldActivateFrench(input: String): Boolean {
        return input.length >= MIN_ACTIVATION_LENGTH && isLoaded
    }

    /**
     * Nombre de formes reconnues, les deux paliers confondus.
     */
    fun getLoadedWordCount(): Int = nombreFormes

    /**
     * Vide le cache des suggestions
     */
    fun clearCache() {
        suggestionCache.clear()
    }

    /**
     * Obtient des statistiques du dictionnaire
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "loaded" to isLoaded,
            "word_count" to nombreFormes,
            "suggest_count" to suggestMots.size,
            "cache_size" to suggestionCache.size,
            "min_activation_length" to MIN_ACTIVATION_LENGTH,
            "max_suggestions" to MAX_FRENCH_SUGGESTIONS
        )
    }

    /**
     * Nettoie les ressources
     */
    fun cleanup() {
        suggestMots = emptyArray()
        suggestFreq = IntArray(0)
        prefixIndex = emptyMap()
        bloom = ByteArray(0)
        clearCache()
        isLoaded = false
        Log.d(TAG, "Dictionnaire français nettoyé")
    }
}
