package com.example.kreyolkeyboard

/**
 * Formes que l'application ne propose jamais **d'elle-même**.
 *
 * Le clavier ne prend pas parti et ne cherche pas à choquer. Le corpus, lui,
 * est fait de dépêches RTL : « Ramadan », « CSV », « Kokain » ou
 * « Vergewaltegung » y figurent comme n'importe quel mot de l'actualité, et le
 * tirage aléatoire les remontait tels quels.
 *
 * **Ce n'est pas un filtre de saisie.** Ces mots restent dans le dictionnaire,
 * dans les suggestions, dans le correcteur et dans la recherche du Wierderbuch :
 * qui veut écrire « Kierch » ou « Drogen » l'écrit, et qui cherche leur sens le
 * trouve. Un clavier qui refuse des mots n'est pas neutre, il est cassé. La
 * règle porte uniquement sur ce que l'application choisit de montrer sans qu'on
 * le lui demande : mot du jour, mots à découvrir, mots tirés par les jeux, et
 * phrases de Wuertlück.
 *
 * ## Où passe la ligne
 *
 * Le critère n'est pas le thème mais la posture : **est écarté ce qui prend
 * parti ou ce qui choque**, pas ce qui parle du monde. C'est pourquoi restent
 * proposables les mots des institutions et de la vie ordinaire, y compris quand
 * ils sont graves — `Regierung`, `Minister`, `Chamber`, `Deputéiert`, `Wahlen`,
 * `Partei`, `Oppositioun`, `Gewerkschaft`, `Streik`, `Police`, `Geriicht`,
 * `Prisong`, `Riichter`, `Affekot`, `Krich`, `Zaldot`, `Waff`, `Doud`,
 * `Spidol`, `krank`, `Kriibs`, `Aids`. Un apprenant a besoin de ces mots-là ;
 * les écarter viderait le jeu sans rien régler.
 *
 * Sont écartés, à l'inverse : les **noms de partis** et les **étiquettes
 * idéologiques**, les **questions morales disputées**, et un **registre qui
 * choque** — violences sexuelles, drogues dures, meurtre, terrorisme.
 *
 * ## Trois choses à ne pas refaire
 *
 * **Les catégories `RELIOUN` et `KIERCHESPR` du LOD ne servent à rien ici.**
 * Elles marquent 88 articles, dont `ginn`, `huet` et `hunn` — les auxiliaires
 * des phrases d'exemple — et laissent dehors `Kierch`, `Gott`, `Bibel`,
 * `Poopst`, `Relioun`, `Islam` et `Moschee`. S'y fier écarterait trois des
 * verbes les plus fréquents de la langue sans écarter un seul des mots visés.
 * Les formes ci-dessous ont été relevées sur les gloses françaises du LOD,
 * acception par acception, puis triées à la main.
 *
 * **La comparaison porte sur des formes entières, jamais sur un préfixe.**
 * `Kierch` est un préfixe de `Kierchbierg`, le quartier d'affaires ; `Drog` en
 * est un de `Drogerie`. Les formes fléchies sont donc énumérées.
 *
 * **Un mot dont le sens religieux ou violent n'est pas le sens courant reste
 * dedans** : `bekannt` (connu / confesser), `Här` (monsieur / curé), `Mass`
 * (masse / messe), `Kräiz` (croix / région lombaire), `Wonner` (miracle au sens
 * de merveille), `Por` (pore / paroisse), `Sënn` (sens / péché), `Séil`,
 * `Testament`, `Laien`, `weien` (peser / consacrer), `Geschlecht` (sexe /
 * famille, et le genre grammatical), `Gréng` (vert — la couleur avant le
 * parti). Restent dedans aussi les **fêtes du calendrier civil**,
 * `Chrëschtdag`, `Ouschteren`, `Kleeschen` et `Oktav`, jours fériés et repères
 * de l'année luxembourgeoise avant d'être des fêtes religieuses.
 *
 * La comparaison passe par [AccentTolerantMatcher.normalize] : ni la casse ni
 * les accents ne comptent, ce qui attrape au passage `Attentäter` avec
 * `Attentater`.
 */
object MotsEcartes {

    /** Confessions, lieux de culte, clergé, rites. */
    private val RELIGION = listOf(
        "Kierch", "Kierchen", "Kierche", "Kathedral", "Basilika", "Abtei",
        "Klouschter", "Kapell", "Kapellen", "Moschee", "Synagog", "Tempel",
        "Tempelen", "Altor", "Doum", "Paschtoueschhaus", "Bistum",
        "Paschtouer", "Paschtéier", "Bëschof", "Äerzbëschof", "Kardinol",
        "Kardineel", "Poopst", "Abbé", "Imam", "Pilger",
        "Relioun", "Reliounen", "Relioune", "reliéis", "reliéise", "reliéisen",
        "reliéiser", "kierchlech", "kierchleche", "Islam", "Koran", "Bibel",
        "Gott", "Gottes", "Chrëscht", "Chrëschten", "Chrëschte", "chrëschtlech",
        "chrëschtleche", "Chrëschtleche", "Chrëschtlech", "kathoulesch",
        "kathoulescher", "Kathoulesch", "jiddesch", "jiddesche", "jiddescher",
        "Judd", "Judde", "Judden", "Moslem", "Moslemen", "Mosleme",
        "muslimesch", "Atheisten", "Gleewegen", "Glawen", "Glawe",
        "Gebiet", "bieden", "biet", "biede", "gebiet", "Kommioun",
        "kommunizéieren", "kommunizéiert", "kommunizéiere", "Consecratioun",
        "Engel", "Engelen", "Däiwel", "Häll", "helleg", "hellege", "Hellege",
        "Hellegen", "Helleger", "Muttergottes", "Jongfra", "Ramadan", "Daf",
        "Kanddaf", "gedeeft", "Seegen", "geseent", "Kulten",
        "Faaschten"
        // Pas de `leieren` (psalmodier) : le repli des accents le confond avec
        // `léieren` (apprendre), 184 occurrences, et privait les jeux comme le
        // mot du jour de l'un des verbes les plus utiles de la langue. Le LOD
        // en fait bien deux articles (LEIEREN1, LEIEREN2), mais
        // [estEcarte] compare des formes repliées : la seule façon de garder
        // l'un sans perdre l'autre serait de comparer avec les accents, ce qui
        // ferait passer au travers tout ce que l'on tape sans accent.
    )

    /**
     * Partis et étiquettes idéologiques.
     *
     * Les sigles sont surtout utiles au filtre des phrases : n'étant pas glosés,
     * ils étaient déjà hors des réserves des jeux. Mais une phrase de Wuertlück
     * sur trois parlait d'un député et le nommait par son parti.
     */
    private val PARTIS = listOf(
        "CSV", "LSAP", "ADR", "DP", "Piraten", "Piratendeputéierte",
        "Nazien", "Nazie",
        "Rassismus", "Rassist", "Rassisten", "rassistesch", "rassistesche",
        "rassisteschen", "Antisemitismus", "antisemittesch", "antisemitesch",
        "Faschismus", "faschistesch", "faschistesche",
        "Extremismus", "Extremist", "Extremisten", "Extremiste",
        "Populismus", "Populisten", "populistesch", "populistesche"
    )

    /**
     * Questions morales disputées, et le registre qui choque : violences
     * sexuelles, drogues dures, meurtre, terrorisme.
     *
     * `Waff`, `Krich`, `Doud`, `Police` et `Prisong` n'en sont pas : ce sont les
     * mots ordinaires de la langue, et un apprenant en a besoin.
     */
    private val REGISTRE = listOf(
        "Ofdreiwung", "Ofdreiwungen", "ofdreiwen", "Ofdreiwungsgesetz",
        "Vergewaltegung", "Vergewaltegungen", "vergewaltegt", "vergewaltegen",
        "Vergewalteger", "Prostitutioun", "Prostituéiert", "Prostituéierten",
        "Prostituéierter", "prostituéieren", "sexuell", "sexuelle", "sexuellen",
        "sexueller", "sexuellem", "Sex", "pedophil", "Pedophilie", "Pedophiller",
        "pornographesch", "pornographeschem", "Kondom", "Kondomer",
        "Drog", "Drogen", "Droge", "Drogendealer", "Drogenhandel",
        "Drogekriminalitéit", "Drogendelikter", "Kokain", "Heroin",
        "drogenofhängeg", "Drogenofhängeger", "Dopping",
        "Attentat", "Attentater", "Terror", "Terrorismus", "Terrorissem",
        "Terrorist", "Terroristen", "Terroriste", "terroristesch",
        "terroristeschen", "terroristescher", "terroristesche",
        "Terrororganisatioun", "Terrormiliz",
        "Mord", "Morde", "Morden", "Mordfäll", "Mordversuch", "Selbstmord"
    )

    /**
     * Grossièretés : le registre obscène et les insultes crues.
     *
     * Signalé par l'usage — « salope » est apparu dans la traduction d'un mot
     * du Wierderbuch. Contrairement au reste de ce fichier, cette liste-là
     * s'applique **aussi aux suggestions du clavier** : voir
     * [SuggestionEngine], qui ne les propose jamais. C'est ce que fait tout
     * clavier du marché, et ce n'est pas un refus de saisie — qui veut écrire
     * ces mots les écrit lettre à lettre, le correcteur ne les souligne pas, et
     * la recherche du Wierderbuch les trouve quand on les tape.
     *
     * **La catégorie `FRECHHEET` du LOD ne convient pas**, exactement comme
     * `RELIOUN` plus haut. Elle marque 187 articles d'injures, dont 476 formes
     * que le clavier connaît — mais parmi elles `Vull` (oiseau), `Sak` (sac),
     * `Kou` (vache), `Geess` (chèvre), `Iesel` (âne), `Noss` (noix), `Quetsch`
     * (quetsche), `See` (lac), `Porrett` (poireau) : des mots parfaitement
     * ordinaires dont l'injure n'est qu'un emploi second. Filtrer `Vull`
     * aurait été une régression grave.
     *
     * La liste est donc relevée sur les **gloses françaises** du LOD, comme les
     * précédentes : 99 articles dont une acception emploie un terme obscène du
     * français, puis triés à la main. Ce tri écarte les collisions —
     * `Sakgaass` (cul-de-sac), `Bëschwaasserleefer` (chevalier cul-blanc),
     * `Zonkmësch` (bruant zizi), `Chili` (chili con carne), `Kuss` et `Bees`
     * (baiser au sens de bise), `Picknick`, `Schlamassel` (pétrin),
     * `gewichst` (cirer) — et garde le mot quand l'obscénité est son sens
     * premier.
     *
     * Deux vérifications valent d'être refaites après toute mise à jour du
     * LOD : `Eesch` est bien la variante de `Aasch` et non la ville `Esch`
     * (absente du corpus sous cette graphie, où `Esch` compte 1 952
     * occurrences) ; et aucune forme de la liste n'est un mot courant du
     * corpus — la plus fréquente est `Aasch`, à 15.
     *
     * `Nenn` (nichon) y figure **au singulier seulement**. Son pluriel se
     * replie sur `nennen` et `nenne` (appeler, nommer — 120 et 16 occurrences),
     * que le clavier a cessé de proposer le temps d'une version. Le singulier,
     * lui, ne heurte rien et reste filtré même quand la casse a été recopiée
     * sur ce que l'utilisateur tape — c'est le repli de la casse qui l'assure.
     */
    private val GROSSIERETES = listOf(
        "Aarsch", "Aarschkrécher", "Aarschkréchesch", "Aarschkréchesche",
        "Aarschkrécheschen", "Aarschlach", "Aarschlächer", "Aasch",
        "Aaschkrécher", "Aaschkréchesch", "Aaschkréchesche",
        "Aaschkrécheschen", "Aaschlach", "Aaschlächer", "Bepisstes",
        "Beseechtes", "Bordell", "Bordelle", "Bordellen", "Drecksak",
        "Drecksäck", "Eesch", "Emmerdeur", "Emmerdeure", "Emmerdeuren",
        "Emmerdeuse", "Emmerdeusen", "Emmerdeusë", "Emmerdéiertes",
        "Fatzert", "Fatzerte", "Fatzerten", "Fotz", "Fotze", "Fotzen",
        "Gefécktes", "Houer", "Houere", "Houeren", "Hourebud",
        "Hourebude", "Hourebuden", "Klut", "Klute", "Kluten",
        "Knaschtert", "Knaschterte", "Knaschterten", "Knaschtsak",
        "Knaschtsäck", "Louder", "Loudere", "Louderen", "Merd", "Nenn",
        "Piss", "Puff", "Puffe", "Puffen",
        "Schläimschësser", "Schläimschëssesch", "Schläimschësseschen",
        "Schäiss", "Schäissdreck", "Schäisserei", "Schäissereie",
        "Schäissereien", "Schäisshaiser", "Schäisshaus",
        "Schäisspabeier", "Seech", "Säckdréier", "Tëtt", "Tëtten",
        "Veraaschtes", "Vullemätti", "Vullemättie", "Vullemättien",
        "bepiss", "bepisse", "bepissen", "bepissend", "bepisst",
        "bepisste", "bepisstem", "bepissten", "bepisstene",
        "bepisstenem", "bepisstenen", "bepisstener", "bepisstent",
        "bepisster", "beschass", "beschäiss", "beschäisse",
        "beschäissen", "beschäissend", "beschäisst", "beseech",
        "beseeche", "beseechen", "beseechend", "beseechs", "beseecht",
        "beseechte", "beseechtem", "beseechten", "beseechtene",
        "beseechtenem", "beseechtenen", "beseechtener", "beseechtent",
        "beseechter", "emmerdéier", "emmerdéiere", "emmerdéieren",
        "emmerdéierend", "emmerdéiers", "emmerdéiert", "emmerdéierte",
        "emmerdéiertem", "emmerdéierten", "emmerdéiertene",
        "emmerdéiertenem", "emmerdéiertenen", "emmerdéiertener",
        "emmerdéiertent", "emmerdéierter", "freck", "fuck", "féck",
        "fécke", "fécken", "féckend", "fécks", "féckt", "geféckt",
        "geféckte", "gefécktem", "geféckten", "gefécktene",
        "gefécktenem", "gefécktenen", "gefécktener", "gefécktent",
        "geféckter", "gehouert", "gepisst", "geschass", "geseecht",
        "houer", "houere", "houeren", "houerend", "houers", "houert",
        "piss", "pisse", "pissen", "pissend", "pisst", "schäiss",
        "schäisse", "schäissegal", "schäissen", "schäissend", "schäisst",
        "seech", "seeche", "seechen", "seechend", "seechs", "seecht",
        "shit", "veraasch", "veraasche", "veraaschen", "veraaschend",
        "veraaschs", "veraascht", "veraaschte", "veraaschtem",
        "veraaschten", "veraaschtene", "veraaschtenem", "veraaschtenen",
        "veraaschtener", "veraaschtent", "veraaschter", "vreck",
        "Äersch"
    )

    /**
     * Vocabulaire qui trahit le **sujet d'une dépêche**, et non un mot à
     * proscrire.
     *
     * Ces formes-là ne sont pas écartées du vocabulaire : `Police`, `Accident`,
     * `Geriicht`, `Prisong`, `Affer` sont des mots utiles, et les jeux
     * continuent de les proposer. Mais une phrase de Wuertlück qui les contient
     * est un fait divers — un accident sur la N7, un cambriolage, un verdict —
     * et un jeu de vocabulaire n'a pas à mettre ça en scène. La distinction
     * porte donc sur la phrase, jamais sur le mot.
     *
     * Le repérage par le sens a été essayé et abandonné : passer par les gloses
     * françaises fait sonner `hat` (l'auxiliaire, glosé « fendre, abattre,
     * frapper ») et `gemaach` (glosé « publier, tuer, vider ») sur 68 phrases,
     * soit plus que tous les vrais déclencheurs réunis. La liste est donc
     * relevée sur les formes qui déclenchent réellement, une par une.
     *
     * Coût mesuré : 156 phrases sur 1 529, et la réserve reste à 316 / 583 / 474
     * pour les trois niveaux.
     */
    private val SUJETS = listOf(
        // Police et secours
        "Police", "Polizist", "Polizisten", "Poliziste", "Ambulanz", "Pompjee",
        "Pompjeeën", "Pompjeeë", "Pompjeeen", "Zeienopruff",
        // Justice
        "Geriicht", "Geriichter", "Prozess", "Prozesser", "Riichter",
        "Riichterin", "Riichteren", "Affekot", "Affekote", "Affekoten",
        "Ugeklote", "Ugekloten", "Beschëllegten", "Beschëllegte", "Plainte",
        "Plaintë", "Enquête", "Enquêten", "Enquêteur", "Ermëttlung",
        "Ermëttlungen", "Perquisitioun", "Perquisitiounen", "Prisong",
        "Prisongen", "festgeholl", "verhaft", "Parquet", "Täter",
        "Verdächtegen", "Verdächtegt",
        // Vols et effractions
        "geklaut", "geklaute", "klauen", "Abroch", "Abréch", "Déifstall",
        "Iwwerfall", "Iwwerfäll", "Raiber", "Déif",
        // Accidents, violences, décès
        "Accident", "Accidenter", "blesséiert", "Blesséierten", "Blesséierter",
        "Blessur", "Blessuren", "geschloen", "geschloe", "attackéiert",
        "ugegraff", "Menace", "Menacen", "Pefferspray", "Gewier", "Messer",
        "gestuerwen", "doudeg", "doudege", "doudegen", "Doudeger", "Affer",
        "Doudschlag", "ëmbruecht", "erschoss"
    )

    private val FORMES: Set<String> =
        (RELIGION + PARTIS + REGISTRE + GROSSIERETES).mapTo(HashSet()) {
            AccentTolerantMatcher.normalize(it)
        }

    private val GROSSIER: Set<String> =
        GROSSIERETES.mapTo(HashSet()) { AccentTolerantMatcher.normalize(it) }

    private val FORMES_ET_SUJETS: Set<String> =
        FORMES + SUJETS.map { AccentTolerantMatcher.normalize(it) }

    /** Vrai si l'application doit s'abstenir de proposer ce mot. */
    fun estEcarte(mot: String): Boolean =
        AccentTolerantMatcher.normalize(mot) in FORMES

    /**
     * Vrai si le mot relève du registre obscène — voir [GROSSIERETES].
     *
     * Portée plus large que [estEcarte] : celui-ci ne régit que ce que
     * l'application montre d'elle-même, celui-là s'applique en plus aux
     * suggestions du clavier. Le partage reste le même dans son principe :
     * jamais proposé, toujours saisissable.
     */
    fun estGrossier(mot: String): Boolean =
        AccentTolerantMatcher.normalize(mot) in GROSSIER

    /**
     * Vrai si une phrase ne doit pas être montrée : elle contient une forme
     * écartée, ou un mot qui la désigne comme fait divers (voir [SUJETS]).
     *
     * Sert aux phrases de Wuertlück, qui viennent des dépêches et parlent donc
     * souvent d'autre chose que du mot à trouver. Le découpage est sur les
     * non-lettres, donc `LSAP-Deputéierten` est bien vu comme `LSAP` suivi du
     * reste.
     */
    fun phraseEcartee(phrase: String): Boolean =
        phrase.split(Regex("[^\\p{L}]+"))
            .any { it.isNotEmpty() && AccentTolerantMatcher.normalize(it) in FORMES_ET_SUJETS }
}
