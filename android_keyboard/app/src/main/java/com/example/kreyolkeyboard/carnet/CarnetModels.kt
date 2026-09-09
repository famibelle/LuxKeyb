package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Le carnet : les mots que le joueur a rencontrés, et de quoi en faire des
 * cartes à collectionner.
 *
 * Wuertplaz est le seul jeu où l'on croise un mot **avant** d'en connaître le
 * sens ; la traduction y est la récompense du verrouillage. Ce qu'il manquait
 * est ce qu'on fait de cette récompense une fois la grille finie : elle
 * disparaissait avec la grille. Le carnet la garde.
 *
 * Trois choix qui portent le reste :
 *
 * - **Il ne fait que grandir, et il survit à l'application.** Une collection
 *   qui repart de zéro à chaque partie n'est pas une collection. Le stockage
 *   est un `SharedPreferences`, domaine que `backup_rules.xml` et
 *   `data_extraction_rules.xml` incluent tous les deux : le carnet est donc
 *   sauvegardé dans le nuage et transféré d'un téléphone à l'autre. Ce sont
 *   des mots de dictionnaire, déjà passés par [com.example.kreyolkeyboard.MotsEcartes]
 *   au chargement des grilles — rien de personnel n'y entre, contrairement au
 *   fichier d'usage de la gamification, qui vit dans `filesDir` et reste
 *   délibérément hors sauvegarde.
 * - **La rareté est la fréquence, pas une invention.** `luxemburgish_dict.json`
 *   est trié par fréquence décroissante : le rang d'une forme *est* sa rareté,
 *   sans qu'il faille fabriquer la moindre statistique. Poser des « points de
 *   vie » sur une vraie langue apprendrait quelque chose de faux ; un rang de
 *   fréquence est vérifiable et se trouve être exactement la mécanique qu'on
 *   cherchait.
 * - **Les rangs sont lus à l'ouverture du carnet, jamais pendant la partie.**
 *   Et par un balayage du texte brut plutôt qu'un `JSONArray` : l'actif fait
 *   1,27 Mo pour 38 442 formes, dont l'arbre complet ferait 77 000 objets pour
 *   les quelques dizaines de rangs dont le carnet a besoin. C'est le même
 *   piège que celui documenté pour le dictionnaire français.
 */
object CarnetWuertplaz {

    private const val PREFS = "wuertplaz_carnet"
    private const val CLE_CARTES = "cartes"
    private const val ASSET_DICO = "luxemburgish_dict.json"
    private const val TAG = "CarnetWuertplaz"

    /** Ordre de capture. La liste ne perd jamais d'entrée. */
    private var cartes: MutableList<CarteMot> = ArrayList()
    private var parForme: MutableMap<String, Int> = HashMap()
    private var charge = false

    /** Rangs de fréquence des seules formes du carnet, lus à la demande. */
    private var rangs: Map<String, Int> = emptyMap()
    private var rangsPour: Int = -1

    @Synchronized
    fun charger(context: Context) {
        if (charge) return
        charge = true
        try {
            val brut = prefs(context).getString(CLE_CARTES, null) ?: return
            val tableau = JSONArray(brut)
            for (i in 0 until tableau.length()) {
                val o = tableau.getJSONObject(i)
                val forme = o.optString("m")
                if (forme.isEmpty()) continue
                parForme[forme] = cartes.size
                cartes.add(
                    CarteMot(
                        forme = forme,
                        premiereFois = o.optLong("d"),
                        rencontres = o.optInt("n", 1),
                        numero = cartes.size + 1
                    )
                )
            }
            Log.d(TAG, "${cartes.size} cartes chargées")
        } catch (e: Exception) {
            // Un carnet illisible ne doit pas empêcher de jouer : on repart
            // d'un carnet vide, que la première capture réécrira.
            Log.e(TAG, "Carnet illisible: ${e.message}", e)
            cartes = ArrayList()
            parForme = HashMap()
        }
    }

    /**
     * Enregistre une rencontre. Retourne vrai si la carte est neuve.
     *
     * Une forme déjà là n'est pas dupliquée : son compteur monte, et c'est ce
     * qui permet de dire « nouveau » ou « revu » au bilan de fin de grille.
     */
    @Synchronized
    fun ajouter(context: Context, forme: String): Boolean {
        charger(context)
        if (forme.isBlank()) return false
        val existant = parForme[forme]
        if (existant != null) {
            val c = cartes[existant]
            cartes[existant] = c.copy(rencontres = c.rencontres + 1)
            enregistrer(context)
            return false
        }
        parForme[forme] = cartes.size
        cartes.add(
            CarteMot(
                forme = forme,
                premiereFois = System.currentTimeMillis(),
                rencontres = 1,
                numero = cartes.size + 1
            )
        )
        enregistrer(context)
        return true
    }

    @Synchronized
    fun cartes(context: Context): List<CarteMot> {
        charger(context)
        return ArrayList(cartes)
    }

    @Synchronized
    fun taille(context: Context): Int {
        charger(context)
        return cartes.size
    }

    @Synchronized
    fun contient(context: Context, forme: String): Boolean {
        charger(context)
        return forme in parForme
    }

    /**
     * Rareté d'une forme, d'après son rang dans le dictionnaire de fréquences.
     *
     * Les rangs sont chargés à la première demande et remis en cache tant que
     * le carnet ne grandit pas — ouvrir le carnet ne relit donc l'actif
     * qu'une fois.
     */
    @Synchronized
    fun rarete(context: Context, forme: String): Rarete {
        assurerRangs(context)
        return Rarete.pourRang(rangs[forme])
    }

    @Synchronized
    fun rang(context: Context, forme: String): Int? {
        assurerRangs(context)
        return rangs[forme]
    }

    /** Remet le carnet à zéro. N'existe que pour les tests et le débogage. */
    @Synchronized
    fun vider(context: Context) {
        cartes = ArrayList()
        parForme = HashMap()
        rangs = emptyMap()
        rangsPour = -1
        charge = true
        prefs(context).edit().remove(CLE_CARTES).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun enregistrer(context: Context) {
        val tableau = JSONArray()
        cartes.forEach {
            tableau.put(
                JSONObject()
                    .put("m", it.forme)
                    .put("d", it.premiereFois)
                    .put("n", it.rencontres)
            )
        }
        prefs(context).edit().putString(CLE_CARTES, tableau.toString()).apply()
    }

    private fun assurerRangs(context: Context) {
        charger(context)
        if (rangsPour == cartes.size) return
        rangsPour = cartes.size
        rangs = if (cartes.isEmpty()) emptyMap()
        else lireRangs(context, cartes.mapTo(HashSet()) { it.forme })
    }

    /**
     * Le rang de chaque forme demandée dans `luxemburgish_dict.json`.
     *
     * L'actif est un tableau de paires `[["d", 111367], ["an", 100105], …]`
     * trié par fréquence décroissante : le rang est la position, et il suffit
     * de compter les ouvertures de paire en relevant la chaîne de tête. On
     * balaye donc le texte une fois, sans construire le moindre objet JSON —
     * voir la note de classe. Les formes du dictionnaire ne contiennent ni
     * guillemet ni contre-oblique, la lecture est donc sûre telle quelle.
     */
    private fun lireRangs(context: Context, formes: Set<String>): Map<String, Int> {
        return try {
            val texte = BufferedReader(
                InputStreamReader(context.assets.open(ASSET_DICO))
            ).use { it.readText() }

            val trouves = HashMap<String, Int>(formes.size)
            var i = 0
            var rang = 0
            val n = texte.length
            while (i < n && trouves.size < formes.size) {
                // Début d'une paire : le premier guillemet qui suit un '['.
                val crochet = texte.indexOf('[', i)
                if (crochet < 0) break
                val ouvre = texte.indexOf('"', crochet)
                if (ouvre < 0) break
                val ferme = texte.indexOf('"', ouvre + 1)
                if (ferme < 0) break
                val forme = texte.substring(ouvre + 1, ferme)
                if (forme in formes) trouves[forme] = rang
                rang++
                i = ferme + 1
            }
            trouves
        } catch (e: Exception) {
            // Sans rangs, tout est « commun » : le carnet reste consultable,
            // il perd seulement sa hiérarchie.
            Log.e(TAG, "Rangs illisibles: ${e.message}", e)
            emptyMap()
        }
    }
}

/**
 * Une carte du carnet.
 *
 * [numero] est l'ordre de capture, celui qu'affiche le pied de carte — la
 * collection se raconte dans l'ordre où on l'a faite, pas dans celui du
 * dictionnaire.
 */
data class CarteMot(
    val forme: String,
    val premiereFois: Long,
    val rencontres: Int,
    val numero: Int
)

/**
 * Les quatre paliers de rareté, lus sur le rang de fréquence.
 *
 * Les seuils sont **mesurés, pas choisis au jugé**. Sur les 1 963 formes que
 * portent les 300 grilles de Wuertplaz, 3 000 / 6 500 / 9 000 donnent
 * 37 / 34 / 20 / 8 % — la courbe d'un jeu de cartes, où le commun domine et où
 * la dernière catégorie se mérite. Les mêmes seuils appliqués au vivier de
 * Kräizwuert donnent 58 / 21 / 13 / 6 %, ce qui est cohérent : ses grilles
 * faciles plafonnent volontairement dans les mots les plus fréquents.
 *
 * Un rang inconnu (forme absente du dictionnaire de fréquences, ce qui
 * n'arrive pas aujourd'hui mais arriverait si un jeu alimentait le carnet
 * depuis le vivier du LOD) est traité comme le plus rare : c'est la lecture
 * juste, une forme que le corpus ne connaît pas est plus rare que tout ce
 * qu'il connaît.
 */
enum class Rarete(val libelle: String, val symbole: String, val couleur: Int) {
    COMMUN("Commun", "●", 0xFF78909C.toInt()),
    PEU_COMMUN("Peu commun", "◆", 0xFF43A047.toInt()),
    RARE("Rare", "★", 0xFF1E88E5.toInt()),
    TRES_RARE("Très rare", "✦", 0xFF8E24AA.toInt());

    companion object {
        const val SEUIL_COMMUN = 3000
        const val SEUIL_PEU_COMMUN = 6500
        const val SEUIL_RARE = 9000

        fun pourRang(rang: Int?): Rarete = when {
            rang == null -> TRES_RARE
            rang < SEUIL_COMMUN -> COMMUN
            rang < SEUIL_PEU_COMMUN -> PEU_COMMUN
            rang < SEUIL_RARE -> RARE
            else -> TRES_RARE
        }
    }
}
