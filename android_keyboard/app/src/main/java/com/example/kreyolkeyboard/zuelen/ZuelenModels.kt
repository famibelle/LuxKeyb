package com.example.kreyolkeyboard.zuelen

import androidx.annotation.StringRes
import com.example.kreyolkeyboard.R
import com.example.kreyolkeyboard.TexteRessource
import kotlin.random.Random

/**
 * Modèles du jeu « Zuelwuert » : un nombre, et quatre façons de l'écrire dont
 * une seule est la bonne.
 *
 * `Zuelwuert` est le mot du LOD pour « numéral » — littéralement le mot-nombre,
 * ce que le jeu demande d'écrire.
 *
 * L'exercice est un **transcodage** : passer du chiffre à la lettre. Ce n'est
 * pas un jeu de calcul — le nombre est donné en clair sauf au niveau le plus
 * dur — mais un jeu d'orthographe, et les leurres sont les fautes qu'on fait
 * réellement : l'allemand, l'ordre des chiffres, le trait d'union, la règle
 * d'Eifel et la finale -ig.
 *
 * Le jeu couvre désormais les **101 nombres de 0 à 100**, et la série
 * des nombres du carnet avec lui. Les tables de 2 à 10 ne donnent que 37
 * produits : 22, 23, 29, 31 ou 97 ne sont le résultat d'aucune. Le nombre est
 * donc tiré d'abord, et l'opération se fabrique ensuite autour de lui — une
 * multiplication quand il en est une, sinon une addition.
 *
 * Toutes les formes viennent de [ZuelenSpeller], vérifié contre le LOD.
 */

enum class ZuelenDifficulty(
    val level: Int,
    @StringRes val label: Int,
    /** Tables des multiplications, des deux côtés du signe ×. */
    val tables: IntRange,
    /**
     * Vrai : seuls les nombres qui s'écrivent en un mot composé sont tirés,
     * les 72 de 21 à 99 hors dizaines rondes.
     *
     * En dessous de 21, et sur les dizaines rondes, il n'y a rien à composer —
     * « aacht » ne se coupe nulle part, aucune règle d'orthographe ne s'y
     * applique. Ces 29 nombres ont leur place en Facile, et c'est là seulement
     * qu'ils se gagnent : la série des nombres passe par ce niveau.
     */
    val composesSeulement: Boolean,
    /** Vrai : une opération est posée ; faux : le nombre seul, en chiffres. */
    val avecOperation: Boolean,
    /** Faux : le résultat est caché, il faut le calculer avant de l'écrire. */
    val montreLeProduit: Boolean,
    /** Familles de leurres autorisées, par ordre indifférent. */
    val leurres: List<String>
) {
    FACILE(
        1, R.string.niveau_facile, 2..10, false, false, true,
        listOf("voisin", "allemand", "traitDUnion", "espaces")
    ),
    NORMALE(
        2, R.string.niveau_normal, 2..10, true, true, true,
        listOf("inversion", "allemand", "traitDUnion", "espaces", "finaleIg")
    ),
    DIFFICILE(
        3, R.string.niveau_difficile, 2..10, true, true, false,
        listOf("eifel", "finaleIg", "accent", "inversion", "allemand")
    );

    companion object {
        fun fromLevel(level: Int): ZuelenDifficulty =
            values().firstOrNull { it.level == level } ?: NORMALE
    }
}

/**
 * Une proposition. [raison] n'est lue que si le joueur la choisit à tort :
 * dire « c'est de l'allemand » vaut mieux qu'un carré rouge.
 */
data class ZuelenOption(
    val texte: String,
    val juste: Boolean,
    val raison: TexteRessource
)

/** Ce qui est posé autour du nombre. */
enum class ZuelenOperation(val signe: String) { AUCUNE(""), FOIS("×"), PLUS("+") }

data class ZuelenQuestion(
    val gauche: Int,
    val droite: Int,
    val operation: ZuelenOperation,
    /** Le nombre à écrire : le résultat de l'opération, quand il y en a une. */
    val nombre: Int,
    val montreLeProduit: Boolean,
    val options: List<ZuelenOption>
) {
    /** « 56 », « 7 × 8 = 56 », « 29 + 27 = 56 », ou « 7 × 8 = ? » au niveau difficile. */
    val enonce: String
        get() = if (operation == ZuelenOperation.AUCUNE) "$nombre"
        else "$gauche ${operation.signe} $droite = " + if (montreLeProduit) "$nombre" else "?"

    /** L'énoncé une fois répondu : le résultat n'a plus de raison d'être tu. */
    val solution: String
        get() = if (operation == ZuelenOperation.AUCUNE) "$nombre"
        else "$gauche ${operation.signe} $droite = $nombre"

    val reponse: String get() = options.first { it.juste }.texte
}

object ZuelenData {

    /** Nombre de questions d'une manche. */
    const val QUESTIONS_PER_ROUND = 10

    /** Nombre de propositions affichées, réponse comprise. */
    const val OPTIONS_PER_QUESTION = 4

    /**
     * Au plus tant de questions d'une manche portent sur des nombres qui
     * manquent au carnet. Le reste est tiré au hasard : une manche faite
     * uniquement de nombres neufs ne repasserait jamais sur ceux qu'on a
     * gagnés, et la révision est aussi le but.
     */
    const val MANQUANTS_PAR_MANCHE = 6

    /** Les nombres qu'un niveau peut tirer. */
    fun nombres(difficulty: ZuelenDifficulty): List<Int> =
        (0..ZuelenSpeller.MAXIMUM).filter { !difficulty.composesSeulement || estCompose(it) }

    /**
     * Une manche de [QUESTIONS_PER_ROUND] nombres distincts.
     *
     * [manquants] sont les nombres que le carnet n'a pas encore : ils passent
     * devant, à concurrence de [MANQUANTS_PAR_MANCHE]. Sans cela, finir la série
     * des 101 nombres demanderait une cinquantaine de manches de Facile, le
     * temps qu'un tirage uniforme tombe sur les derniers.
     */
    fun newRound(
        difficulty: ZuelenDifficulty,
        random: Random = Random.Default,
        manquants: Set<Int> = emptySet()
    ): List<ZuelenQuestion> {
        val possibles = nombres(difficulty)
        val prioritaires = possibles.filter { it in manquants }.shuffled(random)
            .take(MANQUANTS_PAR_MANCHE)
        val reste = (possibles - prioritaires.toSet()).shuffled(random)
        return (prioritaires + reste)
            .take(QUESTIONS_PER_ROUND)
            .shuffled(random)
            .map { construire(it, difficulty, random) }
    }

    /**
     * Vrai si le nombre s'écrit unité + liaison + dizaine, c'est-à-dire s'il y
     * a une orthographe à discuter.
     */
    internal fun estCompose(nombre: Int): Boolean =
        nombre in 21..99 && nombre % 10 != 0

    /**
     * L'opération posée autour de [nombre] : une multiplication des tables
     * quand il en est le produit, sinon une addition de deux nombres qui ne
     * soient ni 0 ni 1 — « 55 + 1 » ne demanderait aucun calcul en Difficile.
     */
    private fun operation(
        nombre: Int,
        difficulty: ZuelenDifficulty,
        random: Random
    ): Triple<ZuelenOperation, Int, Int> {
        if (!difficulty.avecOperation) return Triple(ZuelenOperation.AUCUNE, 0, 0)
        val facteurs = difficulty.tables.flatMap { g ->
            difficulty.tables.filter { d -> g * d == nombre }.map { d -> g to d }
        }
        if (facteurs.isNotEmpty()) {
            val (g, d) = facteurs.random(random)
            return Triple(ZuelenOperation.FOIS, g, d)
        }
        val bas = maxOf(2, nombre / 5)
        val gauche = random.nextInt(bas, nombre - bas + 1)
        return Triple(ZuelenOperation.PLUS, gauche, nombre - gauche)
    }

    private fun construire(
        produit: Int,
        difficulty: ZuelenDifficulty,
        random: Random
    ): ZuelenQuestion {
        val (operation, gauche, droite) = operation(produit, difficulty, random)
        val juste = ZuelenSpeller.enLettres(produit)

        // Les variantes du LOD sont interdites de leurre : « siechzeg » vaut
        // « sechzeg », l'afficher en face donnerait deux bonnes réponses.
        val interdits = ZuelenSpeller.variantes(produit).toMutableSet()
        val options = mutableListOf<ZuelenOption>()

        for (cle in difficulty.leurres.shuffled(random)) {
            if (options.size >= OPTIONS_PER_QUESTION - 1) break
            val leurre = fabriquer(cle, produit, random) ?: continue
            if (!interdits.add(leurre.texte)) continue
            options.add(leurre)
        }

        // Complément : d'autres nombres, du plus proche au plus lointain. Il
        // sert pour les petits produits, où ni le trait d'union ni la règle
        // d'Eifel n'ont de prise — « aacht » ne se coupe nulle part.
        if (options.size < OPTIONS_PER_QUESTION - 1) {
            for (voisin in voisinage(produit)) {
                if (options.size >= OPTIONS_PER_QUESTION - 1) break
                val texte = ZuelenSpeller.enLettres(voisin)
                if (texte.isEmpty() || !interdits.add(texte)) continue
                options.add(ZuelenOption(texte, false, TexteRessource(R.string.zuelen_cest, texte, voisin)))
            }
        }

        options.add(
            ZuelenOption(juste, true, ZuelenSpeller.decomposition(produit)!!)
        )
        return ZuelenQuestion(
            gauche, droite, operation, produit, difficulty.montreLeProduit,
            options.shuffled(random)
        )
    }

    /** Les autres nombres écrivables, du plus proche du produit au plus loin. */
    private fun voisinage(produit: Int): List<Int> =
        (0..ZuelenSpeller.MAXIMUM)
            .filter { it != produit }
            .sortedBy { kotlin.math.abs(it - produit) }

    /**
     * Fabrique un leurre d'une famille donnée, ou null si la famille n'a pas de
     * prise sur ce nombre.
     */
    private fun fabriquer(cle: String, produit: Int, random: Random): ZuelenOption? {
        val juste = ZuelenSpeller.enLettres(produit)
        if (juste.isEmpty()) return null

        val unite = produit % 10
        val chiffreDizaine = produit / 10
        val compose = estCompose(produit)
        // Le composé se coupe après la liaison : c'est le seul endroit où les
        // trois familles de segmentation ont quelque chose à dire.
        val dizaine = if (compose) ZuelenSpeller.enLettres(chiffreDizaine * 10) else ""
        val liaison = if (compose) ZuelenSpeller.liaison(dizaine) else ""
        val tete = if (compose) juste.dropLast(dizaine.length + liaison.length) else ""

        return when (cle) {

            // L'ordre germanique : l'unité se dit avant la dizaine. Un
            // francophone qui lit « 56 » de gauche à droite entend d'abord
            // cinq, et choisit le mot qui commence par fënnef — c'est-à-dire 65.
            "inversion" -> {
                if (!compose || unite == chiffreDizaine) return null
                val inverse = unite * 10 + chiffreDizaine
                if (inverse !in 10..ZuelenSpeller.MAXIMUM) return null
                val texte = ZuelenSpeller.enLettres(inverse)
                if (texte.isEmpty()) return null
                ZuelenOption(
                    texte, false,
                    TexteRessource(R.string.zuelen_inversion, texte, inverse)
                )
            }

            "allemand" -> {
                val texte = ZuelenSpeller.enAllemand(produit)
                if (texte.isEmpty() || texte == juste) return null
                ZuelenOption(texte, false, TexteRessource(R.string.zuelen_allemand, texte))
            }

            "traitDUnion" -> {
                if (!compose) return null
                ZuelenOption(
                    "$tete$liaison-$dizaine", false,
                    TexteRessource(R.string.zuelen_trait_union)
                )
            }

            "espaces" -> {
                if (!compose) return null
                ZuelenOption(
                    "$tete $liaison $dizaine", false,
                    TexteRessource(R.string.zuelen_espaces)
                )
            }

            // Règle d'Eifel : le n de la liaison tombe devant f, s, v… et se
            // maintient devant n, d, t, z, h et les voyelles. C'est la faute la
            // plus fine du jeu, et la seule qui s'entend à l'oral.
            "eifel" -> {
                if (!compose) return null
                val fautive = if (liaison == "an") "a" else "an"
                ZuelenOption(
                    "$tete$fautive$dizaine", false,
                    TexteRessource(
                        if (liaison == "an") R.string.zuelen_eifel_maintient else R.string.zuelen_eifel_tombe,
                        dizaine.first().toString(), juste
                    )
                )
            }

            // -ig est la finale allemande ; le luxembourgeois écrit -eg.
            "finaleIg" -> {
                if (!juste.endsWith("eg")) return null
                val texte = juste.dropLast(2) + "ig"
                if (texte == ZuelenSpeller.enAllemand(produit)) return null
                ZuelenOption(
                    texte, false,
                    TexteRessource(R.string.zuelen_finale_ig)
                )
            }

            "accent" -> {
                val texte = when {
                    juste.contains("é") -> juste.replaceFirst("é", "e")
                    juste.contains("ë") -> juste.replaceFirst("ë", "e")
                    juste.contains("ä") -> juste.replaceFirst("ä", "a")
                    else -> return null
                }
                ZuelenOption(texte, false, TexteRessource(R.string.zuelen_accent, juste))
            }

            "voisin" -> {
                val ecart = listOf(-2, -1, 1, 2, -10, 10).shuffled(random)
                for (delta in ecart) {
                    val candidat = produit + delta
                    if (candidat !in 0..ZuelenSpeller.MAXIMUM) continue
                    val texte = ZuelenSpeller.enLettres(candidat)
                    if (texte.isEmpty()) continue
                    return ZuelenOption(texte, false, TexteRessource(R.string.zuelen_cest, texte, candidat))
                }
                null
            }

            else -> null
        }
    }
}
