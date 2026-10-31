package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.util.Log
import com.example.kreyolkeyboard.R
import com.example.kreyolkeyboard.zuelen.ZuelenSpeller
import org.json.JSONObject

/**
 * Les essentiels : le vocabulaire de base que le carnet doit **au moins**
 * contenir, rangé par thème (propriétaire, 2026-10-31).
 *
 * La liste est celle du propriétaire, telle quelle : salutations, identité,
 * famille, nombres, temps, jours, mois… 22 thèmes, 271 emplacements, dont
 * onze expressions de plusieurs mots (« Gudde Moien », « Wéi vill kascht
 * dat? »). Elle est écrite et vérifiée par `Dictionnaires/generate_essentiels.py`,
 * qui produit `luxemburgish_essentiels.json`.
 *
 * Trois choses en découlent :
 *
 * - **La série** [Serie.ESSENTIELS], une page d'album par thème : finir « Les
 *   jours » se fête vite, finir les 271 serait une promesse trop lointaine.
 * - **Les jeux font passer devant ce qui manque.** Wuertsich, Wuertmix et
 *   Wuertriet tiraient leurs mots au hasard parmi neuf mille : on pouvait jouer
 *   longtemps sans croiser « Méindeg ». Ils glissent maintenant, à chaque
 *   partie, quelques essentiels que le carnet n'a pas encore, comme Zuelwuert
 *   le fait déjà pour ses nombres.
 * - **Les sens propres à cette liste** : le corpus ne connaît que le « mee »
 *   (mais) et le « ee » adverbe, pas le mois de mai ni l'œuf. L'actif porte
 *   ces sens, et [com.example.kreyolkeyboard.TranslationDictionary] les fait
 *   passer avant les siens, partout où une glose s'affiche.
 *
 * Une série doit être **prouvée complétable** : chaque essentiel a un chemin
 * vers le carnet ([chemins]), et `EssentielsAssetTest` le vérifie mot par mot.
 */
object Essentiels {

    private const val TAG = "Essentiels"
    const val ACTIF = "luxemburgish_essentiels.json"

    data class Theme(val id: String, val titres: Map<String, String>, val mots: List<String>) {
        /** Le titre dans la langue de l'interface, l'anglais à défaut. */
        fun titre(langue: String): String = titres[langue] ?: titres["en"] ?: id
    }

    data class Donnees(
        val themes: List<Theme>,
        /** langue de glose → forme → sens. */
        val gloses: Map<String, Map<String, String>>,
        /** forme → code de catégorie du LOD (`SUBST M`, `EXPR`…). */
        val categories: Map<String, String>,
        /** Les homographes dont la carte ne prend rien à l'autre mot. */
        val isoles: Set<String>
    ) {
        /** Les mots, sans doublon (« Moien » salue et dit le matin). */
        val mots: List<String> = themes.flatMap { it.mots }.distinct()
    }

    private val VIDE = Donnees(emptyList(), emptyMap(), emptyMap(), emptySet())

    @Volatile
    private var donnees: Donnees? = null

    /** L'actif lu une fois ; illisible, il donne une liste vide et le carnet fait comme avant. */
    fun donnees(context: Context): Donnees {
        donnees?.let { return it }
        val lues = try {
            lire(context.assets.open(ACTIF).bufferedReader().use { it.readText() })
        } catch (e: Exception) {
            Log.e(TAG, "Actif $ACTIF illisible: ${e.message}", e)
            VIDE
        }
        donnees = lues
        return lues
    }

    /** Sans `Context`, pour le test d'actif. */
    fun lire(json: String): Donnees {
        val racine = JSONObject(json)
        val themes = racine.getJSONArray("themes").let { t ->
            List(t.length()) { i ->
                val o = t.getJSONObject(i)
                val titres = o.getJSONObject("titres").let { ti ->
                    ti.keys().asSequence().associateWith { ti.getString(it) }
                }
                val mots = o.getJSONArray("mots").let { m -> List(m.length()) { m.getString(it) } }
                Theme(o.getString("id"), titres, mots)
            }
        }
        val gloses = racine.getJSONObject("gloses").let { g ->
            g.keys().asSequence().associateWith { l ->
                val table = g.getJSONObject(l)
                table.keys().asSequence().associateWith { table.getString(it) }
            }
        }
        val categories = racine.getJSONObject("categories").let { c ->
            c.keys().asSequence().associateWith { c.getString(it) }
        }
        val isoles = racine.getJSONArray("isoles").let { a -> List(a.length()) { a.getString(it) }.toSet() }
        return Donnees(themes, gloses, categories, isoles)
    }

    /** La langue de l'interface, pour les titres des thèmes. */
    fun langue(context: Context): String = context.getString(R.string.code_langue)

    /** Une expression : plusieurs mots, ou une ponctuation (« Wéi vill kascht dat? »). */
    fun estExpression(mot: String): Boolean = mot.any { !it.isLetter() }

    // -- les chemins vers le carnet ------------------------------------------
    // Les mêmes bornes que les jeux : la grille de Wuertsich prend de trois à
    // sept lettres (huit cases, un mot n'en remplit jamais toute une ligne),
    // Wuertmix range ses mots par longueur selon la difficulté. Les essentiels
    // trop courts ou trop longs pour un niveau vont au plus proche, pour qu'il
    // n'en reste aucun sans chemin.

    /** Un mot que la grille de Wuertsich peut poser. */
    fun pourWuertsich(mot: String): Boolean = !estExpression(mot) && mot.length in 3..7

    /** Un mot que Wuertmix peut proposer au [niveau] : 0 facile, 1 normal, 2 difficile. */
    fun pourWuertmix(mot: String, niveau: Int): Boolean = when {
        estExpression(mot) -> true
        niveau == 0 -> mot.length <= 5
        niveau == 1 -> mot.length in 5..7
        else -> mot.length >= 7
    }

    /** Le cinq-lettres de Wuertriet. */
    fun pourWuertriet(mot: String): Boolean = !estExpression(mot) && mot.length == 5

    /** Les numéraux que Zuelwuert écrit, de 0 à 100. */
    private val zuelwuert: Set<String> by lazy {
        (0..ZuelenSpeller.MAXIMUM).mapTo(HashSet()) { ZuelenSpeller.enLettres(it) }
    }

    /** Les jeux qui peuvent donner [mot] : vide voudrait dire une case impossible. */
    fun chemins(mot: String): List<JeuCarte> = buildList {
        if (pourWuertsich(mot)) add(JeuCarte.WUERTSICH)
        if ((0..2).any { pourWuertmix(mot, it) }) add(JeuCarte.WUERTMIX)
        if (pourWuertriet(mot)) add(JeuCarte.WUERTRIET)
        if (mot in zuelwuert) add(JeuCarte.ZUELWUERT)
    }

    // -- ce qui manque, pour les jeux ----------------------------------------

    /** Les essentiels que le carnet n'a pas encore, dans un ordre tiré au hasard. */
    fun manquants(context: Context): List<String> {
        val d = donnees(context)
        if (d.mots.isEmpty()) return emptyList()
        return d.mots.filterNot { Carnet.contient(context, it) }.shuffled()
    }

    /** Au plus [n] essentiels manquants qui conviennent à un jeu. */
    fun manquantsPour(context: Context, n: Int, convient: (String) -> Boolean): List<String> =
        manquants(context).filter(convient).take(n)
}
