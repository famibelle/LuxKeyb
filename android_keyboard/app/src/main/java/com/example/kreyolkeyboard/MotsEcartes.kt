package com.example.kreyolkeyboard

/**
 * Formes que l'application ne propose jamais **d'elle-même**.
 *
 * Le clavier ne prend pas parti : il n'enseigne pas de vocabulaire confessionnel
 * et ne met pas de religion en avant dans un jeu ou un mot du jour. Le corpus,
 * lui, est fait de dépêches RTL, où « Ramadan », « Poopst » ou « Moschee »
 * apparaissent comme n'importe quel mot de l'actualité — et le tirage aléatoire
 * les remontait tels quels.
 *
 * **Ce n'est pas un filtre de saisie.** Ces mots restent dans le dictionnaire,
 * dans les suggestions, dans le correcteur et dans la recherche du Wierderbuch :
 * qui veut écrire « Kierch » l'écrit, et qui cherche son sens le trouve. Un
 * clavier qui refuse des mots n'est pas neutre, il est cassé. La règle porte
 * uniquement sur ce que l'application choisit de montrer sans qu'on le lui
 * demande.
 *
 * **La liste est faite à la main, et c'est voulu.** Le LOD porte bien des
 * catégories `RELIOUN` et `KIERCHESPR`, mais elles marquent 88 articles dont
 * `ginn`, `huet` et `hunn` — les auxiliaires des phrases d'exemple — et laissent
 * dehors `Kierch`, `Gott`, `Bibel`, `Poopst`, `Relioun`, `Islam` et `Moschee`.
 * S'y fier écarterait trois des verbes les plus fréquents de la langue sans
 * écarter un seul des mots visés. Les formes ci-dessous ont donc été relevées
 * sur les gloses françaises du LOD, acception par acception, puis triées.
 *
 * **Ce qui reste volontairement dedans**, parce que le sens courant l'emporte
 * sur le sens religieux et qu'écarter ces mots appauvrirait le jeu pour rien :
 * `bekannt` (connu / confesser), `Här` (monsieur / curé), `Mass` (masse /
 * messe), `Kräiz` (croix / région lombaire), `Wonner` (miracle au sens de
 * merveille), `Por` (pore / paroisse), `Sënn` (sens / péché), `Séil` (âme),
 * `Testament`, `Seminaire`, `Laien`, `weien` (peser / consacrer), `widmen`
 * (dédier). Restent dedans aussi les **fêtes du calendrier civil** —
 * `Chrëschtdag`, `Ouschteren`, `Kleeschen`, `Oktav` — qui sont des jours fériés
 * et des repères de l'année luxembourgeoise avant d'être des fêtes religieuses.
 *
 * La comparaison passe par [AccentTolerantMatcher.normalize], donc la casse et
 * les accents ne comptent pas ; les formes fléchies sont énumérées, faute de
 * quoi il faudrait comparer par préfixe et `Kierch` emporterait `Kierchbierg`.
 */
object MotsEcartes {

    private val FORMES: Set<String> = listOf(
        // Lieux de culte
        "Kierch", "Kierchen", "Kierche", "Kathedral", "Basilika", "Abtei",
        "Klouschter", "Kapell", "Kapellen", "Moschee", "Synagog", "Tempel",
        "Tempelen", "Altor", "Doum", "Paschtoueschhaus", "Bistum",
        // Clergé
        "Paschtouer", "Paschtéier", "Bëschof", "Äerzbëschof", "Kardinol",
        "Kardineel", "Poopst", "Abbé", "Imam", "Pilger",
        // Religions, confessions, fidèles
        "Relioun", "Reliounen", "Relioune", "reliéis", "reliéise", "reliéisen",
        "reliéiser", "kierchlech", "kierchleche", "Islam", "Koran", "Bibel",
        "Gott", "Gottes", "Chrëscht", "Chrëschten", "Chrëschte", "chrëschtlech",
        "chrëschtleche", "Chrëschtleche", "Chrëschtlech", "kathoulesch",
        "kathoulescher", "Kathoulesch", "jiddesch", "jiddesche", "jiddescher",
        "Judd", "Judde", "Judden", "Moslem", "Moslemen", "Mosleme",
        "muslimesch", "Atheisten", "Gleewegen", "Glawen", "Glawe",
        // Rites, objets et notions
        "Gebiet", "bieden", "biet", "biede", "gebiet", "Kommioun",
        "kommunizéieren", "kommunizéiert", "kommunizéiere", "Consecratioun",
        "Engel", "Engelen", "Däiwel", "Häll", "helleg", "hellege", "Hellege",
        "Hellegen", "Helleger", "Muttergottes", "Jongfra", "Ramadan", "Daf",
        "Kanddaf", "gedeeft", "Seegen", "geseent", "Kulten", "leieren",
        "Faaschten"
    ).mapTo(HashSet()) { AccentTolerantMatcher.normalize(it) }

    /** Vrai si l'application doit s'abstenir de proposer ce mot. */
    fun estEcarte(mot: String): Boolean =
        AccentTolerantMatcher.normalize(mot) in FORMES

    /**
     * Vrai si une phrase contient une forme écartée.
     *
     * Sert aux phrases de Wuertlück, qui viennent des dépêches et parlent donc
     * parfois d'autre chose que du mot à trouver.
     */
    fun phraseEcartee(phrase: String): Boolean =
        phrase.split(Regex("[^\\p{L}]+")).any { it.isNotEmpty() && estEcarte(it) }
}
