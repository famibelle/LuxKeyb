package com.example.kreyolkeyboard

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Glose française des mots luxembourgeois, pour les jeux.
 *
 * L'actif `luxemburgish_translations.json` est produit par
 * `Dictionnaires/generate_translations.py` à partir du Lëtzebuerger Online
 * Dictionnaire (LOD, Zenter fir d'Lëtzebuerger Sprooch, CC0). Rien n'est
 * traduit ici : ce fichier ne fait que lire et chercher.
 *
 * Le clavier lui-même ne s'en sert pas — un dictionnaire de traduction n'a
 * rien à faire dans le service de saisie, où il coûterait 600 Ko de mémoire à
 * chaque ouverture du clavier pour un usage nul. Il n'est chargé que par
 * l'écran des jeux.
 *
 * Deux tiers seulement du dictionnaire y figurent : le reste est
 * essentiellement des noms propres (« Esch », « Bettel », « RTL ») que le LOD
 * n'a aucune raison de gloser. C'est pourquoi les jeux tirent leurs mots parmi
 * les seules formes glosées — voir [filtrerMotsTraduits].
 */
object TranslationDictionary {

    private const val ASSET = "luxemburgish_translations.json"
    private const val TAG = "TranslationDictionary"

    /** Forme telle que livrée par le dictionnaire → glose française. */
    private var traductions: Map<String, String> = emptyMap()

    /**
     * Même table, clés en minuscules. Les jeux manipulent tantôt la casse
     * canonique du dictionnaire, tantôt une forme minuscule (Wuertriet) ou
     * majuscule (la grille de Wuertsich) : chercher deux fois coûte moins cher
     * que d'imposer une casse aux quatre jeux.
     */
    private var traductionsMinuscules: Map<String, String> = emptyMap()

    private var attribution: String = ""
    private var estCharge = false

    /**
     * Index de recherche : une entrée par forme, avec ses deux versions pliées
     * (casse et accents retirés) déjà calculées.
     *
     * Il est construit une fois au chargement plutôt qu'à chaque frappe. Une
     * recherche parcourt les 20 604 entrées linéairement — c'est assez rapide
     * pour un champ de saisie, et cela évite d'entretenir deux tables inversées
     * dont l'une, côté français, aurait de toute façon dû être parcourue.
     */
    private class Entree(
        val forme: String,
        val glose: String,
        val formePliee: String,
        val glosePliee: String
    )

    private var index: List<Entree> = emptyList()

    @Synchronized
    fun charger(context: Context) {
        if (estCharge) return
        estCharge = true

        try {
            val contenu = BufferedReader(
                InputStreamReader(context.assets.open(ASSET))
            ).use { it.readText() }

            val racine = JSONObject(contenu)
            val table = racine.getJSONObject("translations")
            val exactes = HashMap<String, String>(table.length())
            val minuscules = HashMap<String, String>(table.length())

            val cles = table.keys()
            while (cles.hasNext()) {
                val forme = cles.next()
                val glose = table.getString(forme)
                exactes[forme] = glose
                // Premier arrivé, premier servi : le fichier est trié par
                // fréquence décroissante, donc en cas d'homographe séparé par
                // la casse (« Froen » / « froen ») c'est la forme la plus
                // courante qui sert de repli.
                // (putIfAbsent est API 24, minSdk vaut 21)
                val cle = forme.lowercase()
                if (!minuscules.containsKey(cle)) minuscules[cle] = glose
            }

            traductions = exactes
            traductionsMinuscules = minuscules
            index = exactes.map { (forme, glose) ->
                Entree(
                    forme, glose,
                    AccentTolerantMatcher.normalize(forme),
                    AccentTolerantMatcher.normalize(glose)
                )
            }

            val sources = racine.optJSONArray("attribution")
            attribution = if (sources == null) "" else
                (0 until sources.length()).joinToString("\n") { sources.getString(it) }

            Log.d(TAG, "${exactes.size} traductions chargées")
        } catch (e: Exception) {
            // Une glose manquante dégrade l'affichage, elle ne casse aucun jeu :
            // les mots restent jouables, ils ne sont simplement plus traduits.
            Log.e(TAG, "Actif $ASSET illisible: ${e.message}", e)
            traductions = emptyMap()
            traductionsMinuscules = emptyMap()
            index = emptyList()
        }
    }

    /** Un résultat de recherche : le mot luxembourgeois et sa glose. */
    data class Resultat(val mot: String, val glose: String)

    /**
     * Cherche dans les deux sens : un mot luxembourgeois comme un mot français.
     *
     * L'utilisateur qui ouvre un dictionnaire ne sait pas toujours de quel côté
     * il se tient — il tape « maison » aussi souvent que « Haus ». Distinguer
     * les deux champs de saisie aurait demandé de lui poser la question ; les
     * deux sens partagent donc le même champ, et le classement fait le tri.
     *
     * Le sens de la requête est **déduit, pas demandé** : si un mot du
     * dictionnaire se glose exactement par ce qui est tapé, alors ce qui est
     * tapé est du français, et les mots dont c'est le sens passent devant.
     *
     * Cette passe préalable n'est pas un raffinement. Le luxembourgeois a
     * emprunté « Maison » au français — c'est une forme du dictionnaire à part
     * entière, glosée « maison médicale de garde, maison relais ». Une
     * recherche de « maison » la trouvait donc comme forme exacte, rang le plus
     * fort, et reléguait « Haus » derrière elle. Le même piège attend
     * « Accident », « Budget », « Service » : tous les emprunts, c'est-à-dire
     * précisément les mots qu'un francophone tape en premier.
     *
     * Quatre rangs ensuite, du plus sûr au plus lâche : la glose exacte (une
     * acception entière, pas un fragment) quand la requête est française, la
     * forme luxembourgeoise exacte, le préfixe luxembourgeois, puis le reste.
     * À rang égal, le mot le plus court d'abord : c'est presque toujours le
     * lemme plutôt qu'un composé.
     */
    fun rechercher(context: Context, requete: String, maximum: Int = 40): List<Resultat> {
        charger(context)
        val pliee = AccentTolerantMatcher.normalize(requete.trim())
        if (pliee.isEmpty()) return emptyList()

        fun sensExact(entree: Entree) =
            entree.glosePliee.split(",").any { it.trim() == pliee }

        val requeteFrancaise = index.any { sensExact(it) }
        val rangFormeExacte = if (requeteFrancaise) 1 else 0

        val trouves = ArrayList<Pair<Int, Entree>>()
        for (entree in index) {
            val rang = when {
                // N'arrive que si la requête est française : sinon aucune glose
                // ne lui est exactement égale.
                sensExact(entree) -> 0
                entree.formePliee == pliee -> rangFormeExacte
                entree.formePliee.startsWith(pliee) -> 2
                entree.glosePliee.contains(pliee) -> 3
                else -> continue
            }
            trouves.add(rang to entree)
        }

        return trouves
            .sortedWith(compareBy({ it.first }, { it.second.forme.length }, { it.second.forme }))
            .take(maximum)
            .map { Resultat(it.second.forme, it.second.glose) }
    }

    /** Combien de mots la table peut traduire, pour l'afficher à l'écran. */
    fun taille(context: Context): Int {
        charger(context)
        return traductions.size
    }

    /** Glose française d'un mot, ou null s'il n'en a pas. */
    fun traduire(context: Context, mot: String): String? {
        charger(context)
        if (mot.isEmpty()) return null
        return traductions[mot] ?: traductionsMinuscules[mot.lowercase()]
    }

    fun aUneTraduction(context: Context, mot: String): Boolean =
        traduire(context, mot) != null

    /**
     * Glose prête à afficher à côté du mot, ou chaîne vide.
     * Le tiret cadratin sépare mieux que les parenthèses sur une seule ligne.
     */
    fun libelle(context: Context, mot: String, prefixe: String = "— "): String {
        val glose = traduire(context, mot) ?: return ""
        return "$prefixe$glose"
    }

    /**
     * Vrai si la glose apprend quelque chose, c'est-à-dire si au moins une de
     * ses acceptions diffère du mot lui-même.
     *
     * Le luxembourgeois emprunte massivement au français, si bien que 1 278 des
     * 20 604 formes glosées le sont par elles-mêmes : « Accident » → accident,
     * « Budget » → budget, « Ministère » → ministère. La glose est exacte, elle
     * n'est simplement d'aucun secours — et fait passer le jeu pour cassé. Même
     * chose pour les localités dont le nom ne se traduit pas (« Käerjeng »).
     *
     * La comparaison passe par [AccentTolerantMatcher.normalize], qui plie déjà
     * la casse et les accents pour le correcteur : sans cela « Enquête » →
     * « enquête » passerait au travers.
     */
    private fun gloseInstructive(mot: String, glose: String): Boolean {
        val motPlie = AccentTolerantMatcher.normalize(mot)
        return glose.split(",").any { AccentTolerantMatcher.normalize(it.trim()) != motPlie }
    }

    /**
     * Restreint une réserve de mots à ceux dont la traduction apprend quelque
     * chose. C'est ce qui alimente le tirage des trois jeux qui choisissent un
     * mot ; la table complète, elle, reste consultable — un mot glosé par
     * lui-même croisé ailleurs (une réponse de Wuertlück, par exemple) affiche
     * toujours sa glose.
     *
     * Le repli n'est pas décoratif : si une régénération du dictionnaire ou un
     * actif manquant vidait la table, filtrer rendrait les trois jeux
     * injouables sans le moindre message. En dessous de [minimum] mots on rend
     * donc la liste d'origine, non traduite mais jouable.
     */
    fun filtrerMotsTraduits(
        context: Context,
        mots: List<String>,
        minimum: Int = 50
    ): List<String> {
        charger(context)
        val traduits = mots.filter { mot ->
            val glose = traduire(context, mot)
            glose != null && gloseInstructive(mot, glose)
        }
        return if (traduits.size >= minimum) traduits else mots
    }

    /** Crédits du LOD, tels qu'ils voyagent dans l'actif lui-même. */
    fun attribution(context: Context): String {
        charger(context)
        return attribution
    }
}
