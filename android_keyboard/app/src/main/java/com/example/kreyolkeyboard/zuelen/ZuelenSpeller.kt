package com.example.kreyolkeyboard.zuelen

/**
 * Écriture des nombres en toutes lettres, en luxembourgeois.
 *
 * Rien n'est lu ici : contrairement aux quatre autres jeux, le Zuelwuert ne
 * dépend d'aucun actif. Les nombres se calculent, et une table livrée aurait
 * demandé un générateur, une garde de CI et 100 lignes de JSON pour ce que
 * trois règles décrivent entièrement.
 *
 * **Les formes ont été vérifiées une à une contre le LOD** (Lëtzebuerger
 * Online Dictionnaire, ZLS, CC0) : les 101 nombres de 0 à 100 produits par
 * [enLettres] figurent tous dans l'index de recherche du LOD parmi les
 * graphies marquées `suggest="true"`, et aucune graphie composée que le LOD
 * suggère n'échappe à [variantes]. `ZuelenSpellerTest` fige ce résultat.
 *
 * La borne est 100, et c'est délibéré : le LOD n'atteste aucun composé de
 * `honnert` au-delà de `honnertdausend`, si bien que « cent un » ne pourrait
 * pas être livré sous une orthographe vérifiée. Les tables de 1 à 10 tiennent
 * exactement dans cette borne.
 */
object ZuelenSpeller {

    /** Plus grand nombre que cet objet sait écrire. */
    const val MAXIMUM = 100

    /**
     * Formes isolées de 0 à 19. Elles ne se déduisent de rien : `eelef` et
     * `zwielef` sont irrégulières, et les adolescentes composent sur `-zéng`
     * avec des radicaux qui ne sont pas ceux des unités (`sech-` → `siech-`,
     * `siwen-` → `siwwen-`, `aacht-` → `uecht-`).
     */
    private val ISOLEES = mapOf(
        0 to "null", 1 to "eent", 2 to "zwee", 3 to "dräi", 4 to "véier",
        5 to "fënnef", 6 to "sechs", 7 to "siwen", 8 to "aacht", 9 to "néng",
        10 to "zéng", 11 to "eelef", 12 to "zwielef", 13 to "dräizéng",
        14 to "véierzéng", 15 to "fofzéng", 16 to "siechzéng",
        17 to "siwwenzéng", 18 to "uechtzéng", 19 to "nonzéng"
    )

    /**
     * Unités telles qu'elles entrent dans un composé.
     *
     * Seul 1 change : `eent` compté seul, `een` dans `eenanzwanzeg`.
     */
    private val COMPOSANTES = mapOf(
        1 to "een", 2 to "zwee", 3 to "dräi", 4 to "véier", 5 to "fënnef",
        6 to "sechs", 7 to "siwen", 8 to "aacht", 9 to "néng"
    )

    /** Dizaines rondes, indexées par leur chiffre des dizaines. */
    private val DIZAINES = mapOf(
        2 to "zwanzeg", 3 to "drësseg", 4 to "véierzeg", 5 to "fofzeg",
        6 to "sechzeg", 7 to "siwwenzeg", 8 to "achtzeg", 9 to "nonzeg"
    )

    /**
     * Variantes que le LOD suggère au même titre que la forme retenue.
     *
     * Elles existent vraiment — `siechzeg` vaut `sechzeg`, `ning` vaut `néng` —
     * et c'est ce qui les rend dangereuses : proposées comme leurre, elles
     * donneraient à une question deux bonnes réponses. [variantes] sert
     * uniquement à les exclure du tirage des leurres.
     */
    private val VARIANTES_UNITE = mapOf(9 to "ning")
    private val VARIANTES_DIZAINE = mapOf(6 to "siechzeg", 8 to "uechtzeg")

    /**
     * Consonnes et voyelles devant lesquelles le n final se maintient.
     *
     * C'est la règle d'Eifel, qui vaut dans toute la langue et pas seulement
     * dans les nombres : le -n tombe sauf devant n, d, t, z, h et une voyelle.
     * Elle explique à elle seule pourquoi 36 s'écrit `sechsandrësseg` et 56
     * `sechsafofzeg`.
     */
    private const val N_MAINTENU = "ndtzhaeiouäëéèáàöüû"

    /** La liaison entre l'unité et la dizaine : « an » ou « a ». */
    fun liaison(dizaine: String): String =
        if (dizaine.isNotEmpty() && dizaine[0].lowercaseChar() in N_MAINTENU) "an" else "a"

    /**
     * Le nombre en toutes lettres, ou chaîne vide hors de [0, MAXIMUM].
     */
    fun enLettres(nombre: Int): String {
        ISOLEES[nombre]?.let { return it }
        if (nombre == MAXIMUM) return "honnert"
        if (nombre !in 0..MAXIMUM) return ""

        val unite = nombre % 10
        val dizaine = DIZAINES[nombre / 10] ?: return ""
        if (unite == 0) return dizaine
        return COMPOSANTES[unite] + liaison(dizaine) + dizaine
    }

    /**
     * Toutes les graphies que le LOD accepte pour ce nombre, forme retenue
     * comprise. Un leurre ne doit jamais tomber dedans.
     */
    fun variantes(nombre: Int): Set<String> {
        val retenue = enLettres(nombre)
        if (retenue.isEmpty()) return emptySet()

        val formes = mutableSetOf(retenue)
        when (nombre) {
            1 -> formes += "een"                 // `eent` compté, `een` déterminant
            2 -> formes += listOf("zwou", "zwéin")  // féminin, masculin
            9 -> formes += "ning"
            60 -> formes += "siechzeg"
            80 -> formes += "uechtzeg"
        }
        if (nombre in 21..99 && nombre % 10 != 0) {
            val unite = nombre % 10
            val chiffreDizaine = nombre / 10
            val unites = listOfNotNull(COMPOSANTES[unite], VARIANTES_UNITE[unite])
            val dizaines = listOfNotNull(
                DIZAINES[chiffreDizaine], VARIANTES_DIZAINE[chiffreDizaine]
            )
            for (u in unites) for (d in dizaines) formes += u + liaison(d) + d
        }
        return formes
    }

    /**
     * Le nombre, écrit puis expliqué : « 56 = sechs + a + fofzeg ».
     *
     * C'est le texte que voit un joueur qui a répondu juste. Le montrer même
     * en cas de réussite n'est pas décoratif : la décomposition est ce qui se
     * retient, la forme entière ne se retient qu'une fois.
     */
    fun decomposition(nombre: Int): String {
        val retenue = enLettres(nombre)
        if (retenue.isEmpty()) return ""
        if (nombre !in 21..99 || nombre % 10 == 0) {
            return "$nombre s'écrit « $retenue »."
        }
        val dizaine = enLettres((nombre / 10) * 10)
        val liaison = liaison(dizaine)
        val unite = retenue.dropLast(dizaine.length + liaison.length)
        return "$nombre = $unite + $liaison + $dizaine — l'unité d'abord."
    }

    /**
     * Le même nombre en allemand.
     *
     * L'allemand est la faute la plus intéressante à faire commettre : il est
     * lu et écrit tous les jours au Luxembourg, il construit ses nombres
     * exactement dans le même ordre, et `sechsundfünfzig` ressemble assez à
     * `sechsafofzeg` pour qu'on le choisisse sans y penser.
     */
    fun enAllemand(nombre: Int): String {
        val isolees = mapOf(
            0 to "null", 1 to "eins", 2 to "zwei", 3 to "drei", 4 to "vier",
            5 to "fünf", 6 to "sechs", 7 to "sieben", 8 to "acht", 9 to "neun",
            10 to "zehn", 11 to "elf", 12 to "zwölf", 13 to "dreizehn",
            14 to "vierzehn", 15 to "fünfzehn", 16 to "sechzehn",
            17 to "siebzehn", 18 to "achtzehn", 19 to "neunzehn"
        )
        isolees[nombre]?.let { return it }
        if (nombre == 100) return "hundert"
        if (nombre !in 0..100) return ""

        val dizaines = mapOf(
            2 to "zwanzig", 3 to "dreißig", 4 to "vierzig", 5 to "fünfzig",
            6 to "sechzig", 7 to "siebzig", 8 to "achtzig", 9 to "neunzig"
        )
        val composantes = mapOf(
            1 to "ein", 2 to "zwei", 3 to "drei", 4 to "vier", 5 to "fünf",
            6 to "sechs", 7 to "sieben", 8 to "acht", 9 to "neun"
        )
        val dizaine = dizaines[nombre / 10] ?: return ""
        val unite = nombre % 10
        if (unite == 0) return dizaine
        return composantes[unite] + "und" + dizaine
    }
}
