package com.example.kreyolkeyboard.wuertriet

import android.content.Context
import android.graphics.Color
import com.example.kreyolkeyboard.Encres
import com.example.kreyolkeyboard.TranslationDictionary
import com.example.kreyolkeyboard.carnet.Essentiels
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Modèles de données pour le jeu "Wuertriet"
 */

enum class LetterState {
    EMPTY, ABSENT, PRESENT, CORRECT
}

fun LetterState.color(): Int = when (this) {
    LetterState.CORRECT -> Color.parseColor(Encres.VERT)
    LetterState.PRESENT -> Color.parseColor("#FFC107")
    LetterState.ABSENT -> Color.parseColor("#9E9E9E")
    LetterState.EMPTY -> Color.parseColor("#E0E0E0")
}

data class WuertrietRow(
    val letters: List<Char?>,
    val states: List<LetterState>
)

object WuertrietData {

    const val WORD_LENGTH = 5
    const val MAX_ATTEMPTS = 6

    /** Les formes que le LOD ajoute au clavier, par-dessus celles du corpus. */
    private const val ASSET_FORMES_LOD = "luxemburgish_lod_forms.json"

    private var cachedWords: List<String>? = null
    private var formesCanoniques: Map<String, String> = emptyMap()
    private var motsAcceptes: Set<String>? = null

    /**
     * Charge et met en cache les mots de 5 lettres (lettres accentuées créoles autorisées,
     * mots composés avec tiret exclus) depuis le dictionnaire.
     *
     * Ce sont les mots que le jeu **tire**, et eux seuls : ceux qu'il accepte
     * sont dans [chargerMotsAcceptes], qui est une liste plus large.
     *
     * `@Synchronized` parce que [chargerMotsAcceptes] est préchargé sur un fil
     * de fond au début de la partie, pendant que le fil principal peut déjà
     * valider une proposition.
     */
    @Synchronized
    fun loadWords(context: Context): List<String> {
        cachedWords?.let { return it }

        val words = mutableListOf<String>()
        try {
            val inputStream = context.assets.open("luxemburgish_dict.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonContent = reader.readText()
            reader.close()

            val jsonArray = JSONArray(jsonContent)
            val canoniques = HashMap<String, String>()
            for (i in 0 until jsonArray.length()) {
                val forme = jsonArray.getJSONArray(i).getString(0)
                val word = forme.lowercase()
                if (word.length == WORD_LENGTH && word.all { it.isLetter() }) {
                    words.add(word)
                    // La forme en minuscules l'emporte quand elle existe : c'est
                    // un vrai mot (« froen » à côté de « Froen »), et c'est
                    // elle que la glose de fin de partie traduit.
                    if (forme == word) canoniques[word] = forme
                    else canoniques.putIfAbsent(word, forme)
                }
            }
            formesCanoniques = canoniques
        } catch (e: Exception) {
            e.printStackTrace()
            return listOf("kéier", "ëmmer", "wäert", "gesot", "kënne")
        }

        cachedWords = words
        return words
    }

    /**
     * Les mots que le jeu **accepte** : ceux du dictionnaire, et en plus les
     * formes de cinq lettres que le LOD donne au clavier.
     *
     * Le clavier reconnaît deux vocabulaires. `luxemburgish_dict.json` est un
     * relevé de fréquences du corpus ; le niveau `suggest` de
     * `luxemburgish_lod_forms.json` est le rattrapage de couverture du LOD,
     * les mots que le corpus journalistique n'écrit jamais mais que la barre
     * de suggestions propose. Le jeu ne lisait que le premier, et refusait
     * donc 1 771 mots de cinq lettres parfaitement luxembourgeois, que notre
     * Wierderbuch traduit et que notre correcteur ne souligne pas. C'est ce
     * qui est arrivé à une joueuse qui avait touché une pastille du clavier
     * (retour du 27 novembre 2026) : « ce mot n'est pas au dictionnaire » sur
     * un mot qui y est. Une pastille de la bonne longueur était refusée une
     * fois sur trois.
     *
     * **Accepter n'est pas proposer**, et c'est pourquoi le tirage ne touche
     * pas à cette liste : une forme du LOD seul n'a ni fréquence, ni rang de
     * rareté, ni glose garantie, alors que la fin de partie doit annoncer ce
     * que le mot voulait dire et verser une carte dont la rareté vient du
     * rang. Même règle que les mots écartés, qui restent tapables sans jamais
     * être offerts.
     *
     * Les deux erreurs ne coûtent pas le même prix : accepter un mot de trop
     * coûte un essai à qui a écrit un mot qui existe, refuser un mot juste
     * fait arrêter de jouer. Même asymétrie que les filtres de Bloom du
     * français et du LOD.
     *
     * Le niveau `spellcheck` (la règle de l'Eifel, 700 formes de cinq lettres)
     * n'y est pas : le clavier ne les propose jamais, elles ne causent pas ce
     * refus. À décider à part.
     *
     * L'asset fait 1,8 Mo et rien d'autre ne le lit dans ce processus, d'où le
     * préchargement sur un fil de fond au début de la partie. S'il manque, le
     * jeu reste jouable sur le seul dictionnaire, comme avant.
     */
    @Synchronized
    fun chargerMotsAcceptes(context: Context): Set<String> {
        motsAcceptes?.let { return it }

        val acceptes = HashSet(loadWords(context))
        try {
            val contenu = context.assets.open(ASSET_FORMES_LOD)
                .bufferedReader().use { it.readText() }
            val suggest = JSONObject(contenu).optJSONArray("suggest")
            if (suggest != null) {
                for (i in 0 until suggest.length()) {
                    val forme = suggest.optString(i, "").lowercase()
                    if (forme.length == WORD_LENGTH && forme.all { it.isLetter() }) {
                        acceptes.add(forme)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        motsAcceptes = acceptes
        return acceptes
    }

    /**
     * Tire le mot à deviner parmi les seuls mots traduits : la fin de partie
     * annonce ce qu'il voulait dire, et un mot sans glose ne l'apprendrait à
     * personne.
     *
     * Le filtre ne s'applique qu'ici, jamais à [isValidWord] : une proposition
     * du joueur doit rester acceptée dès lors que le clavier connaît son mot,
     * même si le LOD ne la glose pas.
     *
     * L'essentiel tiré, lui, passe par [estAuDictionnaire] et non par
     * [isValidWord] : il lui faut une fréquence et une forme canonique, que
     * les formes du LOD seul n'ont pas. Neuf mots du niveau A1 sont dans ce
     * cas (Mango, Tomat, Muert, Kraut, Fritt, Müsli, Awuer, Härin, Drauf) :
     * les tirer donnerait une carte en minuscules et sans rang, donc rangée
     * parmi les plus rares.
     */
    fun pickRandomWord(context: Context): String {
        // Une partie sur deux, un essentiel de cinq lettres que le carnet n'a
        // pas encore, s'il en reste un que le jeu accepte. Voir [Essentiels].
        if (kotlin.random.Random.nextBoolean()) {
            Essentiels.manquantsPour(context, 1) { Essentiels.pourWuertriet(it) && estAuDictionnaire(context, it) }
                .firstOrNull()?.let { return it.lowercase() }
        }
        val words = TranslationDictionary.filtrerMotsTraduits(context, loadWords(context))
        return words.random()
    }

    /**
     * La forme du dictionnaire d'un mot de la grille, majuscule comprise.
     *
     * La partie se joue en minuscules, mais le carnet range ses cartes par
     * forme exacte : verser « affer » quand les autres jeux versent « Affer »
     * faisait deux cartes du même mot, la seconde sans rang de fréquence et
     * donc classée parmi les plus rares.
     */
    fun formeCanonique(context: Context, word: String): String {
        loadWords(context)
        return formesCanoniques[word.lowercase()] ?: word
    }

    /**
     * La proposition du joueur est-elle un mot ? Le dictionnaire **et** les
     * formes du LOD, voir [chargerMotsAcceptes].
     */
    fun isValidWord(context: Context, word: String): Boolean {
        return chargerMotsAcceptes(context).contains(word.lowercase())
    }

    /**
     * Le mot est-il au dictionnaire, celui qui porte les fréquences, les
     * gloses et les rangs de rareté ? C'est la condition pour être **tiré**,
     * jamais pour être accepté.
     */
    fun estAuDictionnaire(context: Context, word: String): Boolean {
        return loadWords(context).contains(word.lowercase())
    }

    /**
     * Évalue une proposition par rapport au mot cible, à la façon du jeu Wuertriet :
     * deux passes pour gérer correctement les lettres répétées.
     */
    fun evaluateGuess(target: String, guess: String): List<LetterState> {
        val targetLower = target.lowercase()
        val guessLower = guess.lowercase()
        val n = targetLower.length
        val states = MutableList(n) { LetterState.ABSENT }
        val targetUsed = BooleanArray(n)
        val guessMatched = BooleanArray(n)

        for (i in 0 until n) {
            if (guessLower[i] == targetLower[i]) {
                states[i] = LetterState.CORRECT
                targetUsed[i] = true
                guessMatched[i] = true
            }
        }

        for (i in 0 until n) {
            if (guessMatched[i]) continue
            for (j in 0 until n) {
                if (!targetUsed[j] && guessLower[i] == targetLower[j]) {
                    states[i] = LetterState.PRESENT
                    targetUsed[j] = true
                    break
                }
            }
        }

        return states
    }
}
