package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.graphics.Color
import android.util.Log
import com.example.kreyolkeyboard.zuelen.ZuelenSpeller
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Les sept jeux, du point de vue du carnet.
 *
 * Une carte porte **le ou les jeux où elle a été gagnée**, et c'est la seule
 * chose que le carnet sait de sa provenance. L'identifiant court est ce qui
 * part dans les préférences : le nom d'énumération peut changer, le stockage
 * non.
 *
 * Les couleurs reprennent, à l'unité près, celles des cartes du hub : un
 * joueur doit reconnaître d'où vient une carte avant d'avoir lu son étiquette.
 */
enum class JeuCarte(
    val id: String,
    val nom: String,
    val emoji: String,
    val couleur: Int
) {
    WUERTSICH("ws", "Wuertsich", "🎲", 0xFF9C27B0.toInt()),
    WUERTMIX("wm", "Wuertmix", "🔤", 0xFF1976D2.toInt()),
    WUERTRIET("wr", "Wuertriet", "🟩", 0xFF4CAF50.toInt()),
    WUERTLUECK("wl", "Wuertlück", "📝", 0xFFFF8C00.toInt()),
    ZUELWUERT("zw", "Zuelwuert", "🔢", 0xFF00897B.toInt()),
    KRAIZWUERT("kw", "Kräizwuert", "🧩", 0xFFC2185B.toInt()),
    WUERTPLAZ("wp", "Wuertplaz", "🔡", 0xFF00796B.toInt());

    /**
     * Le sigle du jeu, pour la ligne de série d'une carte.
     *
     * Sept jeux, sept extensions : c'est ce que le sigle dit, et il tient là
     * où le nom complet déborderait. Il dérive de [id] plutôt que d'être une
     * huitième colonne à tenir à jour.
     */
    val sigle: String get() = id.uppercase()

    companion object {
        private val PAR_ID = values().associateBy { it.id }
        fun parId(id: String): JeuCarte? = PAR_ID[id]
    }
}

/**
 * Le carnet : les mots que le joueur a gagnés, et de quoi en faire des cartes
 * à collectionner.
 *
 * Il est né dans Wuertplaz, seul jeu où l'on croise un mot **avant** d'en
 * connaître le sens, et où la traduction est la récompense du verrouillage.
 * Mais ce que le carnet répare — une récompense qui disparaît avec la grille —
 * n'avait rien de propre à ce jeu : les six autres apprenaient tout autant et
 * ne gardaient rien. Le carnet est donc **commun aux sept**, et une carte sait
 * d'où elle vient.
 *
 * Quatre choix qui portent le reste :
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
 *   cherchait. Les numéraux de Zuelwuert, eux, ne sont pas dans le corpus et
 *   se lisent autrement — voir [Rarete.pourNombre].
 * - **Les rangs sont lus à l'ouverture du carnet, jamais pendant la partie.**
 *   Et par un balayage du texte brut plutôt qu'un `JSONArray` : l'actif fait
 *   1,27 Mo pour 38 442 formes, dont l'arbre complet ferait 77 000 objets pour
 *   les quelques dizaines de rangs dont le carnet a besoin. C'est le même
 *   piège que celui documenté pour le dictionnaire français.
 * - **Un mot gagné dans deux jeux reste une carte.** Ce sont les mêmes mots :
 *   en faire deux cartes doublerait la collection sans rien lui apprendre. Le
 *   deuxième jeu s'ajoute à la carte, et le compteur de rencontres monte.
 */
object Carnet {

    /**
     * La couleur du carnet.
     *
     * Il en fallait une qui ne soit celle d'aucun des sept jeux : le carnet
     * n'appartient plus à Wuertplaz, dont il portait le vert-bleu, et le
     * reprendre laisserait croire que la collection est encore la sienne.
     */
    const val COULEUR = 0xFF5E35B1.toInt()

    private const val PREFS = "carnet_cartes"
    private const val PREFS_HERITE = "wuertplaz_carnet"
    private const val CLE_CARTES = "cartes"
    private const val ASSET_DICO = "luxemburgish_dict.json"
    private const val TAG = "Carnet"

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
            val brut = lireBrut(context) ?: return
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
                        numero = cartes.size + 1,
                        jeux = lireJeux(o.optString("g")),
                        nombre = if (o.has("v")) o.optInt("v") else null,
                        boite = o.optInt("b", 0),
                        jourEcheance = o.optInt("j", Widderhuelen.JAMAIS_PLANIFIEE)
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
     * Le contenu stocké, en reprenant au besoin celui du carnet d'avant.
     *
     * Les premières versions ne connaissaient que Wuertplaz et rangeaient tout
     * dans `wuertplaz_carnet`. Le nom était juste tant que le carnet l'était ;
     * il ne l'est plus. La reprise est faite **une fois**, à la première
     * lecture, et l'ancien domaine est effacé derrière elle — une collection
     * ne doit pas se retrouver en double si un jour quelqu'un relit l'ancien
     * nom.
     */
    private fun lireBrut(context: Context): String? {
        val prefs = prefs(context)
        prefs.getString(CLE_CARTES, null)?.let { return it }

        val ancien = context.getSharedPreferences(PREFS_HERITE, Context.MODE_PRIVATE)
        val herite = ancien.getString(CLE_CARTES, null) ?: return null
        Log.d(TAG, "Reprise du carnet Wuertplaz")
        prefs.edit().putString(CLE_CARTES, herite).apply()
        ancien.edit().remove(CLE_CARTES).apply()
        return herite
    }

    /**
     * Les jeux d'une carte stockée.
     *
     * Vide veut dire « carte d'avant la généralisation » : elle ne pouvait
     * venir que de Wuertplaz, le seul jeu qui alimentait alors le carnet.
     */
    private fun lireJeux(brut: String): Set<JeuCarte> {
        if (brut.isEmpty()) return setOf(JeuCarte.WUERTPLAZ)
        val jeux = brut.split(',').mapNotNull { JeuCarte.parId(it) }
        return if (jeux.isEmpty()) setOf(JeuCarte.WUERTPLAZ) else LinkedHashSet(jeux)
    }

    /**
     * Enregistre une rencontre. Retourne vrai si la carte est neuve.
     *
     * Une forme déjà là n'est pas dupliquée : son compteur monte, le jeu
     * s'ajoute à sa provenance, et c'est ce qui permet de dire « nouveau » ou
     * « revu » au bilan de fin de partie.
     *
     * [nombre] n'est rempli que par Zuelwuert : c'est la valeur du numéral, la
     * seule chose qui permette de lire sa rareté, puisque le corpus ne contient
     * pas les composés.
     */
    @Synchronized
    fun ajouter(
        context: Context,
        forme: String,
        jeu: JeuCarte,
        nombre: Int? = null
    ): Boolean {
        charger(context)
        if (forme.isBlank()) return false
        val existant = parForme[forme]
        if (existant != null) {
            val c = cartes[existant]
            cartes[existant] = c.copy(
                rencontres = c.rencontres + 1,
                jeux = if (jeu in c.jeux) c.jeux else LinkedHashSet(c.jeux).apply { add(jeu) },
                nombre = c.nombre ?: nombre
            )
            enregistrer(context)
            return false
        }
        parForme[forme] = cartes.size
        cartes.add(
            CarteMot(
                forme = forme,
                premiereFois = System.currentTimeMillis(),
                rencontres = 1,
                numero = cartes.size + 1,
                jeux = setOf(jeu),
                nombre = nombre
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

    /** Les jeux dont au moins une carte est au carnet, dans l'ordre du hub. */
    @Synchronized
    fun jeuxRepresentes(context: Context): List<JeuCarte> {
        charger(context)
        val vus = cartes.flatMapTo(HashSet()) { it.jeux }
        return JeuCarte.values().filter { it in vus }
    }

    /**
     * Donne une échéance aux cartes qu'aucune session n'a encore planifiées,
     * en les étalant sur les jours suivants.
     *
     * Appelée à l'ouverture de la révision, donc aussi bien pour la reprise
     * d'un carnet existant que pour les cartes gagnées depuis la dernière
     * session : dans les deux cas le problème est le même, un paquet de cartes
     * qui deviendraient toutes dues le même jour. Voir
     * [Widderhuelen.echeanceDEtalement].
     *
     * Retourne le nombre de cartes planifiées, zéro si rien n'a bougé (le cas
     * courant, qui n'écrit alors pas les préférences).
     */
    @Synchronized
    fun planifier(context: Context, aujourdHui: Int = Widderhuelen.aujourdHui()): Int {
        charger(context)
        var position = 0
        cartes.forEachIndexed { i, c ->
            if (c.jourEcheance == Widderhuelen.JAMAIS_PLANIFIEE) {
                cartes[i] = c.copy(
                    jourEcheance = Widderhuelen.echeanceDEtalement(aujourdHui, position)
                )
                position++
            }
        }
        if (position > 0) enregistrer(context)
        return position
    }

    /** La file de la prochaine session : au plus [Widderhuelen.PLAFOND_SESSION] cartes. */
    @Synchronized
    fun file(context: Context, aujourdHui: Int = Widderhuelen.aujourdHui()): List<CarteMot> {
        charger(context)
        return Widderhuelen.file(cartes, aujourdHui)
    }

    /**
     * Combien de cartes la prochaine session proposerait.
     *
     * C'est la taille de la file, donc un nombre **plafonné** : la bannière du
     * hub annonce ce que la session contient, jamais l'arriéré. Se lit dans les
     * préférences seules, sans toucher aux actifs, ce que la bannière exige.
     */
    @Synchronized
    fun aRevoir(context: Context, aujourdHui: Int = Widderhuelen.aujourdHui()): Int =
        file(context, aujourdHui).size

    /**
     * Enregistre le résultat d'une carte révisée.
     *
     * [reussi] à faux ramène la carte en boîte 0 ; la session, elle, se charge
     * de la repasser avant la fin, sans quoi on quitterait sur un échec jamais
     * rejoué.
     */
    @Synchronized
    fun noter(
        context: Context,
        forme: String,
        reussi: Boolean,
        aujourdHui: Int = Widderhuelen.aujourdHui()
    ) {
        charger(context)
        val index = parForme[forme] ?: return
        val c = cartes[index]
        val boite = if (reussi) Widderhuelen.apresReussite(c.boite)
        else Widderhuelen.apresEchec(c.boite)
        cartes[index] = c.copy(
            boite = boite,
            jourEcheance = Widderhuelen.echeance(aujourdHui, boite)
        )
        enregistrer(context)
    }

    /**
     * Rareté d'une carte.
     *
     * Un numéral de Zuelwuert se lit sur sa valeur, tout le reste sur le rang
     * de fréquence de sa forme. Les rangs sont chargés à la première demande et
     * remis en cache tant que le carnet ne grandit pas — ouvrir le carnet ne
     * relit donc l'actif qu'une fois.
     */
    @Synchronized
    fun rarete(context: Context, carte: CarteMot): Rarete {
        carte.nombre?.let { return Rarete.pourNombre(it) }
        assurerRangs(context)
        return Rarete.pourRang(rangs[carte.forme])
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
        context.getSharedPreferences(PREFS_HERITE, Context.MODE_PRIVATE)
            .edit().remove(CLE_CARTES).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun enregistrer(context: Context) {
        val tableau = JSONArray()
        cartes.forEach {
            val o = JSONObject()
                .put("m", it.forme)
                .put("d", it.premiereFois)
                .put("n", it.rencontres)
                .put("g", it.jeux.joinToString(",") { j -> j.id })
            it.nombre?.let { v -> o.put("v", v) }
            // La révision n'écrit que ces deux entiers, et c'est ce qui tient
            // la promesse de [PreuveDeFrappe] : une échéance repoussée parce
            // que le mot a été écrit au clavier est indiscernable d'une
            // échéance repoussée par une carte réussie. Aucun compteur de
            // frappe n'entre dans un domaine sauvegardé.
            if (it.boite != 0) o.put("b", it.boite)
            if (it.jourEcheance != Widderhuelen.JAMAIS_PLANIFIEE) o.put("j", it.jourEcheance)
            tableau.put(o)
        }
        prefs(context).edit().putString(CLE_CARTES, tableau.toString()).apply()
    }

    private fun assurerRangs(context: Context) {
        charger(context)
        if (rangsPour == cartes.size) return
        rangsPour = cartes.size
        rangs = if (cartes.isEmpty()) emptyMap()
        else lireRangs(context, cartes.filter { it.nombre == null }.mapTo(HashSet()) { it.forme })
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
        if (formes.isEmpty()) return emptyMap()
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
 * dictionnaire. [jeux] garde les jeux où la carte a été gagnée, dans l'ordre
 * où ils l'ont donnée : le premier est celui qui l'a fait entrer au carnet.
 * [nombre] n'est rempli que pour les numéraux de Zuelwuert.
 */
data class CarteMot(
    val forme: String,
    val premiereFois: Long,
    val rencontres: Int,
    val numero: Int,
    val jeux: Set<JeuCarte> = setOf(JeuCarte.WUERTPLAZ),
    val nombre: Int? = null,
    val boite: Int = 0,
    val jourEcheance: Int = Widderhuelen.JAMAIS_PLANIFIEE
) {
    /** Le jeu qui a fait entrer la carte au carnet. */
    val origine: JeuCarte get() = jeux.firstOrNull() ?: JeuCarte.WUERTPLAZ

    /** La carte a franchi toutes les boîtes : elle ne revient plus. */
    val acquise: Boolean get() = boite >= Widderhuelen.BOITE_ACQUISE
}

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
 * Un rang inconnu (forme absente du dictionnaire de fréquences) est traité
 * comme le plus rare : c'est la lecture juste, une forme que le corpus ne
 * connaît pas est plus rare que tout ce qu'il connaît.
 */
enum class Rarete(val libelle: String, val symbole: String, val couleur: Int) {
    COMMUN("Commun", "●", 0xFF78909C.toInt()),
    PEU_COMMUN("Peu commun", "◆", 0xFF43A047.toInt()),
    RARE("Rare", "★", 0xFF1E88E5.toInt()),
    TRES_RARE("Très rare", "✦", 0xFF8E24AA.toInt());

    /**
     * Le symbole, répété autant de fois que le palier est haut.
     *
     * La couleur seule ne suffit pas à séparer les paliers : le vert de
     * *Peu commun* et le bleu-gris de *Commun* se confondent en deutéranopie,
     * et une vignette de 160 dp ne laisse pas la place à un libellé. Compter
     * des symboles, en revanche, se fait sans couleur — c'est la convention
     * de tous les jeux de cartes, et elle ne coûte que trois caractères.
     */
    val insigne: String get() = symbole.repeat(ordinal + 1)

    /**
     * Le palier a-t-il droit aux marques réservées aux cartes rares ?
     *
     * Un seul endroit décide, parce que ces marques — le coin, le double
     * filet, le halo, l'éclat — doivent toutes apparaître au même palier :
     * une carte qui gagne le coin mais pas le halo se lit comme un bug.
     */
    val distinguee: Boolean get() = this == RARE || this == TRES_RARE

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

        /**
         * La rareté d'un numéral, lue sur **ce que son orthographe demande**.
         *
         * Le rang de fréquence ne dit rien ici : mesuré sur l'actif livré, 86
         * des 101 nombres de 0 à 100 sont absents du corpus, et les quinze
         * présents sautent de `zwee` (rang 105) à `fofzeg` (36 954) sans rien
         * entre les deux. Appliquer [pourRang] rendrait « très rares » cinq
         * cartes sur six et viderait le palier de son sens, dans le seul jeu
         * où toutes les cartes se ressemblent déjà.
         *
         * La graduation suit donc les règles que [ZuelenSpeller] décrit, et qui
         * sont exactement ce que Zuelwuert enseigne :
         *
         * - **Commun** : 0 à 19 et `honnert` — formes isolées, rien à composer.
         * - **Peu commun** : les dizaines rondes, une forme et pas de liaison.
         * - **Rare** : les composés dont le n de liaison se maintient
         *   (`eenanzwanzeg`), la lecture qu'on devine.
         * - **Très rare** : les composés où la règle d'Eifel fait tomber le n
         *   (`sechsafofzeg`), celle qu'on n'invente pas.
         *
         * Hors de [0, ZuelenSpeller.MAXIMUM] on ne sait rien, et on retombe sur
         * le repli commun à tout le carnet : le plus rare.
         */
        fun pourNombre(valeur: Int): Rarete {
            if (valeur !in 0..ZuelenSpeller.MAXIMUM) return TRES_RARE
            if (valeur < 20 || valeur == ZuelenSpeller.MAXIMUM) return COMMUN
            if (valeur % 10 == 0) return PEU_COMMUN
            val dizaine = ZuelenSpeller.enLettres(valeur - valeur % 10)
            return if (ZuelenSpeller.liaison(dizaine) == "an") RARE else TRES_RARE
        }
    }
}

/**
 * Une couleur ramenée vers le blanc.
 *
 * Les dégradés du carnet — le dos d'une carte, le cadre d'une très rare, le
 * halo qui la précède — se fabriquent tous à partir d'une seule couleur, celle
 * du jeu ou celle du palier. Les deux fonctions ci-dessous sont ce qui en tire
 * une famille : la même teinte, une fois levée, une fois posée.
 */
internal fun eclaircir(couleur: Int, part: Float): Int = Color.rgb(
    (Color.red(couleur) + (255 - Color.red(couleur)) * part).toInt(),
    (Color.green(couleur) + (255 - Color.green(couleur)) * part).toInt(),
    (Color.blue(couleur) + (255 - Color.blue(couleur)) * part).toInt()
)

/** Une couleur ramenée vers le noir. Voir [eclaircir]. */
internal fun assombrir(couleur: Int, part: Float): Int = Color.rgb(
    (Color.red(couleur) * (1 - part)).toInt(),
    (Color.green(couleur) * (1 - part)).toInt(),
    (Color.blue(couleur) * (1 - part)).toInt()
)
