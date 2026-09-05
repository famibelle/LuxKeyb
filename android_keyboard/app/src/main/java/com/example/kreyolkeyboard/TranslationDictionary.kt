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
 * rien à faire dans le service de saisie, où il coûterait 2,7 Mo de mémoire à
 * chaque ouverture du clavier pour un usage nul. Il n'est chargé que par
 * l'écran des jeux et l'onglet Wierderbuch.
 *
 * La table glose deux populations : les 38 410 formes du dictionnaire de
 * fréquences, à hauteur de 20 604, et les 84 855 formes que le LOD apporte au
 * clavier par-dessus le corpus (`luxemburgish_lod_forms.json`), à hauteur de
 * 68 248. Ce qui reste sans glose est essentiellement des noms propres
 * (« Esch », « Bettel », « RTL ») que le LOD n'a aucune raison de gloser.
 *
 * Les jeux, eux, ne tirent que parmi les formes du **dictionnaire** qui sont
 * glosées — voir [filtrerMotsTraduits] : leur réserve n'a pas changé.
 */
object TranslationDictionary {

    private const val ASSET = "luxemburgish_translations.json"
    private const val ASSET_FAMILLES = "luxemburgish_familles.json"
    private const val ASSET_EXEMPLES = "luxemburgish_exemples.json"
    private const val ASSET_LOD_IDS = "luxemburgish_lod_ids.json"
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
     * Il est construit au premier appel de [rechercher] et non au chargement.
     * Depuis que la table glose aussi les formes que le LOD apporte au clavier
     * (88 852 entrées contre 20 604), le construire d'office coûterait deux
     * repliages et une allocation par forme sur le fil principal, à l'ouverture
     * de l'onglet statistiques — qui, lui, ne cherche rien. Une recherche
     * parcourt ensuite l'index linéairement : c'est assez rapide pour un champ
     * de saisie débounçé, et cela évite d'entretenir deux tables inversées dont
     * l'une, côté français, aurait de toute façon dû être parcourue.
     */
    private class Entree(
        val forme: String,
        val glose: String,
        val formePliee: String,
        val glosePliee: String
    )

    private var index: List<Entree>? = null

    /**
     * Les familles du LOD : toutes les formes d'un même article, rattachées à
     * l'une d'entre elles.
     *
     * Le dictionnaire glose 88 852 formes pour 26 000 articles environ — 3,4
     * formes par mot. Sans regroupement, chercher « manger » remplit l'écran de
     * « iessen », « iesse », « giess », « ësst » : quarante lignes pour neuf
     * mots. Ces tables ramènent une entrée par mot, les autres formes passant
     * dans la fiche.
     *
     * Actif séparé, et chargé au premier appel de [rechercher] seulement : les
     * jeux et les statistiques n'ont que faire d'un mégaoctet de flexions.
     */
    private var representantDe: Map<String, String> = emptyMap()
    private var formesDe: Map<String, List<String>> = emptyMap()
    private var famillesChargees = false

    /**
     * Les phrases d'exemple du LOD, indexées par le mot que la fiche affiche.
     *
     * Une glose dit ce qu'un mot veut dire, jamais comment il s'emploie :
     * « Haus = maison » ne fait pas deviner « ech ginn heem ». Le ZLS écrit
     * ces phrases pour cela, et elles sont en CC0 comme le reste du LOD.
     *
     * Troisième actif chargé à part, et le plus tardif des trois : seule la
     * fiche d'un mot en montre, c'est-à-dire seulement après qu'on a touché un
     * résultat de recherche. Les jeux, le mot du jour et les mots à découvrir
     * n'en affichent aucune et n'ont pas à analyser 2,6 Mo pour cela.
     *
     * Le verrou lui est propre : synchroniser sur l'objet ferait attendre une
     * recherche pendant que le préchargement analyse le fichier, et le champ
     * de saisie se figerait le temps d'une frappe.
     */
    private var exemples: Map<String, List<String>> = emptyMap()
    private var exemplesCharges = false
    private val verrouExemples = Any()

    /**
     * Forme affichée → identifiant de l'article du LOD, pour le bouton « Voir
     * sur le dictionnaire officiel ».
     *
     * Chargé et verrouillé à part pour la même raison que les exemples : c'est
     * la fiche seule qui s'en sert, et l'onglet le précharge sur un fil de
     * fond pendant que l'on tape.
     */
    private var articlesLod: Map<String, String> = emptyMap()
    private var articlesCharges = false
    private val verrouArticles = Any()

    /**
     * Formes pliées que l'application peut proposer d'elle-même, calculées une
     * fois au chargement.
     *
     * Le mot du jour ne teste que quelques mots, mais « Mots à découvrir »
     * parcourt les 37 734 clés du fichier d'usage à chaque ouverture de
     * l'onglet. Redécouper une glose sur des virgules et replier chaque
     * acception 37 734 fois, sur le fil principal, se voit à l'écran.
     */
    private var proposables: Set<String> = emptySet()

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
            index = null

            proposables = exactes.asSequence()
                .filter { (forme, glose) ->
                    gloseInstructive(forme, glose) && !MotsEcartes.estEcarte(forme)
                }
                .mapTo(HashSet()) { AccentTolerantMatcher.normalize(it.key) }

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
            index = null
            proposables = emptySet()
        }
    }

    /**
     * L'index de recherche, construit au premier besoin. Voir [Entree] : seul
     * l'onglet Wierderbuch s'en sert, et il pèse quatre chaînes par forme.
     */
    @Synchronized
    private fun indexRecherche(): List<Entree> {
        index?.let { return it }
        val construit = traductions.map { (forme, glose) ->
            Entree(
                forme, glose,
                AccentTolerantMatcher.normalize(forme),
                AccentTolerantMatcher.normalize(glose)
            )
        }
        index = construit
        return construit
    }

    @Synchronized
    private fun chargerFamilles(context: Context) {
        if (famillesChargees) return
        famillesChargees = true

        try {
            val contenu = BufferedReader(
                InputStreamReader(context.assets.open(ASSET_FAMILLES))
            ).use { it.readText() }

            val table = JSONObject(contenu).getJSONObject("familles")
            val vers = HashMap<String, String>(table.length() * 5)
            val depuis = HashMap<String, List<String>>(table.length())

            val cles = table.keys()
            while (cles.hasNext()) {
                val representant = cles.next()
                val autres = table.getString(representant)
                    .split(" ").filter { it.isNotEmpty() }
                depuis[representant] = autres
                // Le représentant se désigne lui-même : la recherche peut
                // alors interroger la table sans distinguer les deux cas.
                vers[representant] = representant
                autres.forEach { vers[it] = representant }
            }

            representantDe = vers
            formesDe = depuis
            Log.d(TAG, "${depuis.size} familles chargées (${vers.size} formes)")
        } catch (e: Exception) {
            // Sans familles la recherche fonctionne, elle répète simplement les
            // flexions comme avant : une dégradation, pas une panne.
            Log.e(TAG, "Actif $ASSET_FAMILLES illisible: ${e.message}", e)
            representantDe = emptyMap()
            formesDe = emptyMap()
        }
    }

    /**
     * Charge les phrases d'exemple. Sans effet si elles le sont déjà.
     *
     * Publique parce que l'onglet Wierderbuch la lance sur un fil de fond dès
     * qu'il s'ouvre : l'analyse dure le temps d'un clignement, mais elle
     * tomberait sinon sur le premier mot touché, c'est-à-dire au moment précis
     * où la fiche doit s'afficher.
     */
    fun chargerExemples(context: Context) {
        synchronized(verrouExemples) {
            if (exemplesCharges) return
            exemplesCharges = true

            try {
                val contenu = BufferedReader(
                    InputStreamReader(context.assets.open(ASSET_EXEMPLES))
                ).use { it.readText() }

                val table = JSONObject(contenu).getJSONObject("exemples")
                val lues = HashMap<String, List<String>>(table.length())

                val cles = table.keys()
                while (cles.hasNext()) {
                    val mot = cles.next()
                    val phrases = table.getJSONArray(mot)
                    lues[mot] = (0 until phrases.length()).map { phrases.getString(it) }
                }

                exemples = lues
                Log.d(TAG, "${lues.size} mots illustrés")
            } catch (e: Exception) {
                // Sans exemples la fiche garde son sens et ses formes : la
                // section disparaît, rien d'autre ne change.
                Log.e(TAG, "Actif $ASSET_EXEMPLES illisible: ${e.message}", e)
                exemples = emptyMap()
            }
        }
    }

    /**
     * Charge la table des identifiants d'article. Sans effet si elle l'est
     * déjà.
     *
     * Préchargée avec les exemples, sur le même fil : elle ne sert qu'au
     * dernier geste de la fiche, mais l'analyser à ce moment-là ferait
     * attendre le navigateur.
     */
    fun chargerArticles(context: Context) {
        synchronized(verrouArticles) {
            if (articlesCharges) return
            articlesCharges = true

            try {
                val contenu = BufferedReader(
                    InputStreamReader(context.assets.open(ASSET_LOD_IDS))
                ).use { it.readText() }

                val table = JSONObject(contenu).getJSONObject("articles")
                val lues = HashMap<String, String>(table.length())

                val cles = table.keys()
                while (cles.hasNext()) {
                    val mot = cles.next()
                    lues[mot] = table.getString(mot)
                }

                articlesLod = lues
                Log.d(TAG, "${lues.size} articles du LOD indexés")
            } catch (e: Exception) {
                // Sans la table, le bouton retombe sur la recherche du LOD :
                // dégradé, pas cassé.
                Log.e(TAG, "Actif $ASSET_LOD_IDS illisible: ${e.message}", e)
                articlesLod = emptyMap()
            }
        }
    }

    /**
     * Identifiant de l'article du LOD qui a fourni la glose de [mot], ou
     * `null` si l'on ne le connaît pas.
     *
     * Sert à fabriquer `lod.lu/artikel/<id>`, la seule adresse de lod.lu qui
     * arrive sur l'article. Sa route de recherche, `/sich/<langue>/<mot>`,
     * n'est pas utilisable depuis l'extérieur : le composant qui la sert émet
     * sa requête sur un bus d'événements au moment où il se monte, et lors
     * d'une ouverture à froid — c'est-à-dire chaque fois qu'on y arrive par un
     * lien — l'écouteur n'existe pas encore. La recherche est perdue et la
     * page propose d'ajouter le mot au dictionnaire, même pour « Haus ».
     */
    fun articleLod(context: Context, mot: String): String? {
        chargerArticles(context)
        return articlesLod[mot]
    }

    /**
     * Phrases illustrant un résultat de recherche, au plus deux.
     *
     * Le filtre est celui de tout le reste de l'application, à une nuance
     * près : une phrase est écartée si elle contient une forme mise de côté
     * **autre que le mot cherché**. Chercher « Kierch » donne donc bien la
     * phrase du LOD qui l'emploie — c'est une réponse à une question posée,
     * pas une proposition — mais la fiche de « Mass » ne ramène pas la messe
     * par la bande.
     *
     * C'est [MotsEcartes.estEcarte] et non `phraseEcartee` : ces phrases sont
     * écrites par un dictionnaire pour illustrer un mot, pas tirées de
     * dépêches comme celles de Wuertlück. Écarter tout ce que touche un fait
     * divers priverait « Police » ou « Accident » de leur exemple, alors que
     * c'est justement l'emploi de ces mots-là qu'il s'agit de montrer.
     */
    fun exemples(context: Context, resultat: Resultat): List<String> {
        chargerExemples(context)
        val phrases = exemples[resultat.mot] ?: return emptyList()
        val duMot = (resultat.formes + resultat.mot)
            .mapTo(HashSet()) { AccentTolerantMatcher.normalize(it) }
        return phrases.filter { phrase ->
            decouperEnMots(phrase).none { mot ->
                AccentTolerantMatcher.normalize(mot) !in duMot &&
                    MotsEcartes.estEcarte(mot)
            }
        }
    }

    /** Les mots d'une phrase, la ponctuation et les élisions retirées. */
    fun decouperEnMots(phrase: String): List<String> =
        phrase.split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }

    /**
     * Un résultat de recherche : le mot, sa glose, et ses autres formes.
     *
     * [formes] est vide quand le mot n'a pas de famille — un nom propre, un
     * invariable, ou une forme que l'index du LOD ne rattache à rien.
     */
    data class Resultat(
        val mot: String,
        val glose: String,
        val formes: List<String> = emptyList()
    )

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
     * Cinq rangs ensuite, du plus sûr au plus lâche : la glose exacte (une
     * acception entière, pas un fragment) quand la requête est française, la
     * forme luxembourgeoise exacte, le préfixe luxembourgeois, la glose où la
     * requête **commence un mot**, puis la glose où elle n'est qu'un morceau.
     * À rang égal, le mot le plus court d'abord : c'est presque toujours le
     * lemme plutôt qu'un composé.
     *
     * Ce dernier rang était confondu avec le précédent, et c'est ce qui rendait
     * les mots courts inutilisables. Mesuré sur l'actif livré : « eau » donnait
     * 38 lignes sur 40 où le mot n'est qu'un fragment — « beaucoup »,
     * « nouveau », « de nouveau » ; « chat » en donnait 26, tous tirés de
     * « achat » et « châtaigne » (le repli des accents efface l'accent
     * circonflexe). Ces résultats ne sont pas écartés, seulement relégués : un
     * « maisonnette » reste trouvable, et une requête qui n'a que cela vaut
     * mieux qu'un écran vide.
     *
     * Les résultats sont enfin **regroupés par famille** : une ligne par mot du
     * LOD, portant le représentant, et non une ligne par flexion.
     */
    fun rechercher(context: Context, requete: String, maximum: Int = 40): List<Resultat> {
        charger(context)
        chargerFamilles(context)
        val pliee = AccentTolerantMatcher.normalize(requete.trim())
        if (pliee.isEmpty()) return emptyList()

        val index = indexRecherche()

        fun sensExact(entree: Entree) =
            entree.glosePliee.split(",").any { it.trim() == pliee }

        val requeteFrancaise = index.any { sensExact(it) }
        val rangFormeExacte = if (requeteFrancaise) 1 else 0

        val trouves = ArrayList<Pair<Int, Entree>>()
        for (entree in index) {
            // `parLaGlose` distingue « l'utilisateur a tapé ce mot » de
            // « l'application le propose » : le rang ne suffit pas, puisque 0
            // et 1 se confondent quand la requête n'est pas française.
            var parLaGlose = false
            val rang = when {
                // N'arrive que si la requête est française : sinon aucune glose
                // ne lui est exactement égale.
                sensExact(entree) -> { parLaGlose = true; 0 }
                entree.formePliee == pliee -> rangFormeExacte
                entree.formePliee.startsWith(pliee) -> 2
                debuteUnMot(entree.glosePliee, pliee) -> { parLaGlose = true; 3 }
                entree.glosePliee.contains(pliee) -> { parLaGlose = true; 4 }
                else -> continue
            }
            // Une grossièreté atteinte par son sens français est une
            // proposition de l'application — chercher « chat » sortait
            // « Fotz », glosé « chatte, salope ». Atteinte par sa forme, elle
            // reste : le dictionnaire répond à qui l'interroge. C'est la même
            // ligne que partout ailleurs, jamais proposé, toujours trouvable.
            if (parLaGlose && MotsEcartes.estGrossier(entree.forme)) continue
            trouves.add(rang to entree)
        }

        // Une entrée par famille, au meilleur rang qu'une de ses formes ait
        // atteint : chercher « Haiser » doit mener à « Haus » aussi sûrement
        // que chercher « Haus », et ne le montrer qu'une fois.
        val meilleurs = LinkedHashMap<String, Pair<Int, Entree>>()
        for ((rang, entree) in trouves) {
            val cle = representantDe[entree.forme] ?: entree.forme
            val actuel = meilleurs[cle]
            if (actuel == null || rang < actuel.first) meilleurs[cle] = rang to entree
        }

        return meilleurs.entries
            .sortedWith(compareBy({ it.value.first }, { it.key.length }, { it.key }))
            .take(maximum)
            .map { (representant, trouve) ->
                Resultat(
                    representant,
                    // La glose montrée est celle du représentant, pour qu'elle
                    // s'accorde au mot affiché ; celle de la forme trouvée ne
                    // sert que si le représentant n'est pas glosé.
                    traductions[representant] ?: trouve.second.glose,
                    formesDe[representant] ?: emptyList()
                )
            }
    }

    /**
     * La requête commence-t-elle un mot du texte ?
     *
     * Écrit à la main plutôt qu'avec une expression régulière : la boucle
     * tourne sur 88 852 gloses à chaque frappe, et compiler un motif par
     * recherche coûterait plus que la recherche elle-même. Un mot commence là
     * où le caractère précédent n'est pas une lettre — les deux textes sont
     * déjà repliés en minuscules sans accents.
     */
    private fun debuteUnMot(texte: String, motif: String): Boolean {
        var depuis = 0
        while (true) {
            val i = texte.indexOf(motif, depuis)
            if (i < 0) return false
            if (i == 0 || !texte[i - 1].isLetter()) return true
            depuis = i + 1
        }
    }

    /** Glose française d'un mot, ou null s'il n'en a pas. */
    fun traduire(context: Context, mot: String): String? {
        charger(context)
        if (mot.isEmpty()) return null
        return traductions[mot] ?: traductionsMinuscules[mot.lowercase()]
    }

    /**
     * Glose prête à afficher à côté du mot, ou chaîne vide.
     * Le point médian sépare mieux que les parenthèses sur une seule ligne.
     */
    fun libelle(context: Context, mot: String, prefixe: String = "· "): String {
        val glose = traduire(context, mot) ?: return ""
        return "$prefixe$glose"
    }

    /**
     * Vrai si la glose apprend quelque chose, c'est-à-dire si au moins une de
     * ses acceptions diffère du mot lui-même.
     *
     * Le luxembourgeois emprunte massivement au français, si bien que 2 744 des
     * 88 852 formes glosées le sont par elles-mêmes : « Accident » → accident,
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
     * Vrai si l'application peut proposer ce mot d'elle-même.
     *
     * Deux conditions, et c'est le point de passage unique du mot du jour, des
     * mots à découvrir et des trois jeux qui tirent un mot : la glose apprend
     * quelque chose (voir [gloseInstructive]), et le mot n'est pas de ceux que
     * [MotsEcartes] tient à l'écart. Une glose absente laisse une ligne vide,
     * une glose égale au mot laisse une ligne inutile, et les deux se lisent de
     * la même façon — le mot n'est pas traduit.
     *
     * Le calcul est fait une fois pour toutes au chargement : voir
     * [proposables].
     */
    fun estProposable(context: Context, mot: String): Boolean {
        charger(context)
        return AccentTolerantMatcher.normalize(mot) in proposables
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
        val traduits = mots.filter { estProposable(context, it) }
        return if (traduits.size >= minimum) traduits else mots
    }

    /** Crédits du LOD, tels qu'ils voyagent dans l'actif lui-même. */
    fun attribution(context: Context): String {
        charger(context)
        return attribution
    }
}
