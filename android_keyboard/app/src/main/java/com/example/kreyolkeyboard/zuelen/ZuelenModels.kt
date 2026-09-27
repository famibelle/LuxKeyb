package com.example.kreyolkeyboard.zuelen

import kotlin.random.Random

/**
 * Modèles du jeu « Zuelwuert » : une multiplication, et quatre façons d'écrire
 * son résultat dont une seule est la bonne.
 *
 * `Zuelwuert` est le mot du LOD pour « numéral » — littéralement le mot-nombre,
 * ce que le jeu demande d'écrire.
 *
 * L'exercice est un **transcodage** : passer du chiffre à la lettre. Ce n'est
 * pas un jeu de calcul — le produit est donné en clair sauf au niveau le plus
 * dur — mais un jeu d'orthographe, et les leurres sont les fautes qu'on fait
 * réellement : l'allemand, l'ordre des chiffres, le trait d'union, la règle
 * d'Eifel et la finale -ig.
 *
 * Toutes les formes viennent de [ZuelenSpeller], vérifié contre le LOD.
 */

enum class ZuelenDifficulty(
    val level: Int,
    val label: String,
    /** Tables tirées, des deux côtés du signe ×. */
    val tables: IntRange,
    /**
     * Vrai : seuls les produits qui s'écrivent en un mot composé sont tirés.
     *
     * En dessous de 21, et sur les dizaines rondes, il n'y a rien à composer —
     * « aacht » ne se coupe nulle part, aucune règle d'orthographe ne s'y
     * applique, et la question retombe sur « lequel de ces quatre nombres
     * vaut 8 ». C'est une question d'arithmétique, pas de langue : elle a sa
     * place en Facile et nulle part ailleurs.
     */
    val composesSeulement: Boolean,
    /** Faux : le produit est caché, il faut le calculer avant de l'écrire. */
    val montreLeProduit: Boolean,
    /** Familles de leurres autorisées, par ordre indifférent. */
    val leurres: List<String>
) {
    FACILE(
        1, "Facile", 2..10, false, true,
        listOf("voisin", "allemand", "traitDUnion", "espaces")
    ),
    NORMALE(
        2, "Normal", 2..10, true, true,
        listOf("inversion", "allemand", "traitDUnion", "espaces", "finaleIg")
    ),
    DIFFICILE(
        3, "Difficile", 2..10, true, false,
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
    val raison: String
)

data class ZuelenQuestion(
    val gauche: Int,
    val droite: Int,
    val produit: Int,
    val montreLeProduit: Boolean,
    val options: List<ZuelenOption>
) {
    /** « 7 × 8 = 56 », ou « 7 × 8 = ? » au niveau difficile. */
    val enonce: String
        get() = "$gauche × $droite = " + if (montreLeProduit) "$produit" else "?"

    val reponse: String get() = options.first { it.juste }.texte
}

object ZuelenData {

    /** Nombre de questions d'une manche. */
    const val QUESTIONS_PER_ROUND = 10

    /** Nombre de propositions affichées, réponse comprise. */
    const val OPTIONS_PER_QUESTION = 4

    /**
     * Une manche de [QUESTIONS_PER_ROUND] multiplications distinctes.
     *
     * Distinctes par leur *produit*, pas par leur opération : 6 × 8 et 8 × 6
     * poseraient deux fois la même question d'orthographe, qui est la seule
     * que le jeu pose.
     */
    fun newRound(
        difficulty: ZuelenDifficulty,
        random: Random = Random.Default
    ): List<ZuelenQuestion> {
        val operations = mutableListOf<Pair<Int, Int>>()
        for (g in difficulty.tables) for (d in difficulty.tables) {
            operations.add(g to d)
        }

        val vus = mutableSetOf<Int>()
        val questions = mutableListOf<ZuelenQuestion>()
        // Deux passes : les produits composés d'abord, les autres seulement si
        // la manche n'est pas pleine — ce qui n'arrive qu'en Facile, où ils
        // sont autorisés. Les tables de 2 à 10 offrent dix-huit produits
        // composés, assez pour une manche sans jamais tirer deux fois le même.
        for (composesDabord in listOf(true, false)) {
            if (composesDabord.not() && difficulty.composesSeulement) break
            for ((g, d) in operations.shuffled(random)) {
                if (questions.size >= QUESTIONS_PER_ROUND) break
                val produit = g * d
                if (estCompose(produit) != composesDabord) continue
                if (!vus.add(produit)) continue
                questions.add(construire(g, d, difficulty, random))
            }
        }
        return questions
    }

    /**
     * Vrai si le nombre s'écrit unité + liaison + dizaine, c'est-à-dire s'il y
     * a une orthographe à discuter.
     */
    private fun estCompose(nombre: Int): Boolean =
        nombre in 21..99 && nombre % 10 != 0

    private fun construire(
        gauche: Int,
        droite: Int,
        difficulty: ZuelenDifficulty,
        random: Random
    ): ZuelenQuestion {
        val produit = gauche * droite
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
                options.add(ZuelenOption(texte, false, "« $texte », c'est $voisin."))
            }
        }

        options.add(
            ZuelenOption(juste, true, ZuelenSpeller.decomposition(produit))
        )
        return ZuelenQuestion(
            gauche, droite, produit, difficulty.montreLeProduit,
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
                    "« $texte », c'est $inverse : en luxembourgeois l'unité " +
                        "se dit avant la dizaine, comme en allemand."
                )
            }

            "allemand" -> {
                val texte = ZuelenSpeller.enAllemand(produit)
                if (texte.isEmpty() || texte == juste) return null
                ZuelenOption(texte, false, "« $texte » est l'allemand, pas le luxembourgeois.")
            }

            "traitDUnion" -> {
                if (!compose) return null
                ZuelenOption(
                    "$tete$liaison-$dizaine", false,
                    "Les nombres s'écrivent d'un seul tenant, sans trait d'union."
                )
            }

            "espaces" -> {
                if (!compose) return null
                ZuelenOption(
                    "$tete $liaison $dizaine", false,
                    "Les nombres s'écrivent d'un seul tenant, sans espace."
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
                    "Règle d'Eifel : le n de la liaison " +
                        (if (liaison == "an") "se maintient" else "tombe") +
                        " devant « ${dizaine.first()} », donc « $juste »."
                )
            }

            // -ig est la finale allemande ; le luxembourgeois écrit -eg.
            "finaleIg" -> {
                if (!juste.endsWith("eg")) return null
                val texte = juste.dropLast(2) + "ig"
                if (texte == ZuelenSpeller.enAllemand(produit)) return null
                ZuelenOption(
                    texte, false,
                    "La finale luxembourgeoise est -eg ; -ig est celle de l'allemand."
                )
            }

            "accent" -> {
                val texte = when {
                    juste.contains("é") -> juste.replaceFirst("é", "e")
                    juste.contains("ë") -> juste.replaceFirst("ë", "e")
                    juste.contains("ä") -> juste.replaceFirst("ä", "a")
                    else -> return null
                }
                ZuelenOption(texte, false, "Un accent manque : « $juste ».")
            }

            "voisin" -> {
                val ecart = listOf(-2, -1, 1, 2, -10, 10).shuffled(random)
                for (delta in ecart) {
                    val candidat = produit + delta
                    if (candidat !in 0..ZuelenSpeller.MAXIMUM) continue
                    val texte = ZuelenSpeller.enLettres(candidat)
                    if (texte.isEmpty()) continue
                    return ZuelenOption(texte, false, "« $texte », c'est $candidat.")
                }
                null
            }

            else -> null
        }
    }
}
