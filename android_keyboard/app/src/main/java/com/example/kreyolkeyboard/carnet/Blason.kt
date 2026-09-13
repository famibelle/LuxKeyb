package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Le blason d'une carte : le champ de son sens, et le meuble des rares.
 *
 * ## Ce que la fenêtre d'une carte dit, et d'où ça vient
 *
 * Trois sources, et elles ne se mélangent jamais :
 *
 * | ce qu'on voit | d'où ça vient | ce que ça coûte |
 * |---|---|---|
 * | la **matière** — grain, halo, irisation | le palier, donc le rang de fréquence | rien |
 * | la **partition** — la division du champ | la nature du mot, lue sur sa majuscule | rien |
 * | la **teinte** — le champ | le sens, classé hors ligne | 52 ko |
 * | le **sujet** — tracé ou meuble | le mot | 74 silhouettes |
 *
 * ## Pourquoi le champ et pas l'image
 *
 * La demande d'origine était une image par mot. Elle a été mesurée et elle est
 * hors de portée : **1,12 mot par dessin** sur ce carnet, 89 % des têtes de
 * glose ne servant qu'un seul mot. Il n'y a pas de Pareto à exploiter — la
 * courbe de couverture colle à la diagonale.
 *
 * Le champ, lui, amortit par construction : huit valeurs pour 2 798
 * emplacements, un facteur trois cent cinquante. Il tient la promesse qui
 * comptait — feuilleter le carnet cesse d'être un nuancier aléatoire — sans
 * demander un seul dessin. Huit et non quatorze : au-delà, deux teintes
 * voisines ne se séparent plus sur une vignette de 160 dp.
 *
 * ## Ce qu'un mot sans blason devient
 *
 * Rien de spécial, et c'est le point. Il garde la teinte de ses trois
 * premières lettres et son tracé, c'est-à-dire exactement l'état où toutes les
 * cartes étaient en 22.6.0. Aucune version intermédiaire du classement n'est
 * laide, donc il peut s'enrichir par vagues sans jamais bloquer une livraison.
 *
 * L'actif couvre aujourd'hui **71 %** des emplacements du carnet ; le reste est
 * pour l'essentiel des mots-outils — pronoms, déterminants, adverbes — qui
 * n'ont pas de domaine et qu'il serait faux de ranger de force.
 */
enum class Champ(val id: String, val nom: String, val hue: Float) {
    // Les teintes sont posées à intervalles inégaux sur le cercle : l'œil
    // sépare mal deux bleus voisins et très bien deux verts, et c'est la
    // séparation perçue qui compte, pas la régularité du calcul.
    MOUVEMENT("mouvement", "Mouvement", 20f),
    TEMPS("temps", "Temps et mesure", 52f),
    TERRITOIRE("territoire", "Territoire", 96f),
    ECONOMIE("economie", "Économie", 158f),
    SAVOIR("savoir", "Savoir et parole", 205f),
    PUBLIC("public", "Chose publique", 268f),
    SOCIETE("societe", "Société", 312f),
    VIE("vie", "Vie et corps", 352f);

    companion object {
        private val PAR_ID = values().associateBy { it.id }
        fun parId(id: String?): Champ? = id?.let { PAR_ID[it] }
    }
}

/**
 * La nature du mot, telle que la partition la montre.
 *
 * Elle se lit **sans aucune donnée** : en luxembourgeois la majuscule *est*
 * l'étiquette du substantif, et l'infinitif se termine en `-en`. C'est la
 * seule information de cette classe qui ne coûte ni actif ni classement, et
 * c'est aussi celle qui rend les huit teintes lisibles — une forme qui double
 * la couleur sauve deux verts voisins, y compris en deutéranopie. Le même
 * raisonnement que les insignes de rareté, qui comptent des symboles plutôt
 * que de se fier au vert et au bleu-gris.
 */
enum class Nature { NOM, VERBE, AUTRE }

/**
 * Ce que le carnet sait du sens d'un mot : son champ, et son meuble s'il en a.
 *
 * [nature] n'est pas lue dans l'actif — elle se calcule sur la forme, ce qui
 * la rend disponible même pour les mots que le classement ne connaît pas.
 */
data class Blasonnement(
    val champ: Champ? = null,
    val meuble: String? = null,
    val nature: Nature = Nature.AUTRE
) {
    companion object {
        val AUCUN = Blasonnement()
    }
}

/**
 * L'armorial : le classement des lemmes, lu une fois dans l'actif.
 *
 * L'actif est produit hors du build par `Dictionnaires/generate_blasons.py`,
 * qui porte le lexique, la relecture à la main et l'attribution des meubles.
 * Il est petit — 52 ko pour 2 064 lemmes — donc il se lit d'un coup au premier
 * besoin, sans le balayage de texte que `Carnet.lireRangs` doit faire sur le
 * mégaoctet du dictionnaire de fréquences.
 *
 * **La clé est le représentant de la famille**, pas la forme rencontrée : le
 * joueur a gagné « Männer », c'est le blason de « Mann » qu'il faut lui
 * montrer, comme c'est la glose de « Mann » qu'il lit. `ContenuCarte` a déjà
 * ce représentant sous la main — il vient de `TranslationDictionary.fiche` —
 * donc le blason ne coûte pas une recherche de plus.
 */
object Armorial {

    private const val ACTIF = "luxemburgish_blasons.json"

    private var table: Map<String, Blasonnement>? = null

    /**
     * Le blason d'un lemme.
     *
     * [forme] sert uniquement à la nature : c'est le mot que la carte montre,
     * et c'est sa majuscule à lui qui décide de la partition — « Männer » est
     * un nom parce qu'il s'écrit avec une majuscule, pas parce que son
     * représentant en est un.
     */
    fun pour(context: Context, lemme: String, forme: String): Blasonnement {
        val nature = natureDe(forme)
        val trouve = charger(context)[lemme] ?: return Blasonnement(nature = nature)
        return trouve.copy(nature = nature)
    }

    /**
     * La nature d'une forme, lue sur elle seule.
     *
     * La majuscule est sûre : l'orthographe luxembourgeoise l'impose à tout
     * substantif. La finale verbale, elle, est une approximation qu'il faut
     * chiffrer plutôt que taire — sur les 502 mots que la règle appelle verbe,
     * une quinzaine sont en réalité des déterminants ou des pronoms
     * (`sengem`, `hirem`, `deenen`…), soit 3 %. L'erreur ne coûte qu'une
     * division de champ, jamais un contresens : la carte dit toujours son mot
     * en toutes lettres sur la plaque.
     *
     * Les verbes irréguliers en `-nn` sont récupérés à part — `sinn`, `ginn`,
     * `hunn`, `gesinn` et leurs douze composés — moins les trois adverbes qui
     * portent la même finale.
     */
    fun natureDe(forme: String): Nature {
        if (forme.isEmpty()) return Nature.AUTRE
        val tete = forme[0]
        if (tete.isUpperCase()) return Nature.NOM
        return when {
            forme.endsWith("en") || forme.endsWith("em") || forme.endsWith("ën") -> Nature.VERBE
            forme.endsWith("nn") && forme !in ADVERBES_EN_NN -> Nature.VERBE
            else -> Nature.AUTRE
        }
    }

    /** Les trois mots en `-nn` du carnet qui ne sont pas des verbes. */
    private val ADVERBES_EN_NN = setOf("schonn", "dann", "geschwënn")

    private fun charger(context: Context): Map<String, Blasonnement> {
        table?.let { return it }
        val lu = try {
            val texte = BufferedReader(
                InputStreamReader(context.assets.open(ACTIF))
            ).use { it.readText() }
            val blasons = JSONObject(texte).getJSONObject("blasons")
            val hors = HashMap<String, Blasonnement>(blasons.length())
            val cles = blasons.keys()
            while (cles.hasNext()) {
                val lemme = cles.next()
                val entree = blasons.getJSONObject(lemme)
                hors[lemme] = Blasonnement(
                    champ = Champ.parId(entree.optString("c").ifEmpty { null }),
                    meuble = entree.optString("m").ifEmpty { null }
                )
            }
            hors
        } catch (e: Exception) {
            // Un actif absent ou abîmé ne doit pas éteindre le carnet : sans
            // lui, toutes les cartes retombent sur la teinte de leurs trois
            // premières lettres et sur leur tracé, ce qui est présentable.
            Log.w("Armorial", "blasons illisibles, le carnet garde ses teintes de lettres", e)
            emptyMap()
        }
        table = lu
        return lu
    }
}
