package com.example.kreyolkeyboard.carnet

import com.example.kreyolkeyboard.AccentTolerantMatcher

/**
 * Ce qu'une carte demande, et c'est **la boîte qui en décide**, pas le joueur.
 *
 * L'ordre suit celui que la mémoire supporte : reconnaître avant de produire.
 * Choisir soi-même reviendrait à choisir la question facile, ce qui est
 * exactement ce qu'une révision ne doit pas permettre.
 */
enum class FormeQuestion {
    /** Boîtes 0 et 1 : la forme seule, on retourne la carte, on s'autonote. */
    RECONNAISSANCE,

    /** Boîtes 2 et plus : la phrase du LOD avec le mot en cases, à taper. */
    PHRASE_A_TROUS,

    /** Repli de production : la glose française, le mot à taper. */
    GLOSE
}

/** Une question posée : la carte, sa forme, et la phrase trouée s'il y en a une. */
data class QuestionRevision(
    val contenu: ContenuCarte,
    val forme: FormeQuestion,
    val phraseTrouee: String? = null
) {
    val motAttendu: String get() = contenu.carte.forme
    val tapee: Boolean get() = forme != FormeQuestion.RECONNAISSANCE
}

/**
 * Le résultat d'une réponse tapée.
 *
 * [DETAIL] est le cœur de la leçon : juste à un accent ou à la majuscule près
 * compte comme réussi, mais **ne fait pas monter la carte de boîte**. C'est le
 * seul endroit de l'application où la majuscule de substantif et l'accent sont
 * l'objet de la question plutôt qu'un détail de rendu, et laisser passer
 * `greng` pour `gréng` enseignerait la faute que le jeu existe pour corriger.
 */
enum class Verdict { EXACT, DETAIL, FAUX }

/**
 * Une session de révision : la file, la question courante, et le sort de chaque
 * carte.
 *
 * Toute la logique est ici et **aucune vue ne l'est**, pour la même raison que
 * `ChasseCroiseSession` : ce sont les règles qui se cassent en silence, pas les
 * pixels. `WidderhuelenSessionTest` les rejoue.
 *
 * Deux règles qui ne se voient pas mais qui font la session :
 *
 * - **Un échec repasse une fois, en fin de session.** Sans cela on échoue sur un
 *   mot et on ne le revoit que le lendemain : la session n'aurait rien appris,
 *   elle aurait seulement constaté. Le repassage ne change pas la boîte (elle
 *   est déjà retombée à zéro au premier échec) : il existe pour que le joueur
 *   parte en ayant retrouvé le mot, pas pour rattraper le point perdu.
 * - **Une carte n'est jamais vue deux fois autrement.** Le plafond de la file
 *   vaut douze cartes ; les rejouer en boucle jusqu'à réussite ferait durer une
 *   session d'une minute et demie jusqu'à l'abandon.
 */
class SessionWidderhuelen(file: List<ContenuCarte>) {

    private val restantes: ArrayDeque<QuestionRevision> = ArrayDeque(file.map { question(it) })
    private val dejaRepassees = HashSet<String>()

    /** Cartes prévues au départ, repassages non comptés. */
    val total: Int = file.size

    var courante: QuestionRevision? = restantes.removeFirstOrNull()
        private set

    /** Formes réussies, dans l'ordre, le repassage d'un échec exclu. */
    val reussies: MutableList<String> = ArrayList()

    /** Formes ratées, dans l'ordre. Une forme ne peut y figurer qu'une fois. */
    val ratees: MutableList<String> = ArrayList()

    val fini: Boolean get() = courante == null

    /** Rang de la question courante, pour l'afficher « 3 / 12 ». */
    val rang: Int get() = (reussies.size + ratees.size + 1).coerceAtMost(total)

    /**
     * Enregistre la réponse à la question courante et passe à la suivante.
     *
     * Retourne vrai si la carte doit être notée dans le carnet, c'est-à-dire
     * hors repassage : un repassage a déjà été noté à son échec.
     */
    fun repondre(reussi: Boolean): Boolean {
        val q = courante ?: return false
        val forme = q.motAttendu
        val repassage = forme in dejaRepassees

        if (!repassage) {
            if (reussi) reussies.add(forme) else ratees.add(forme)
            if (!reussi) {
                dejaRepassees.add(forme)
                // En fin de file, pas tout de suite : redemander le mot qu'on
                // vient de révéler ne teste que la mémoire de l'instant.
                restantes.addLast(q)
            }
        }

        courante = restantes.removeFirstOrNull()
        return !repassage
    }

    /**
     * La question d'une carte : la boîte décide, la phrase disponible arbitre.
     *
     * Une carte de production dont la phrase ne peut pas être trouée (aucun
     * mot de la phrase ne correspond à la forme ni à sa famille) retombe sur la
     * glose : mesuré sur les actifs livrés, 99,3 % des formes ont une phrase,
     * mais rien ne garantit qu'elle contienne la forme telle que le joueur l'a
     * rencontrée.
     */
    private fun question(contenu: ContenuCarte): QuestionRevision {
        if (contenu.carte.boite < BOITE_PRODUCTION) {
            return QuestionRevision(contenu, FormeQuestion.RECONNAISSANCE)
        }
        val trouee = contenu.exemple?.let {
            phraseATrous(it, contenu.carte.forme, contenu.autresFormes)
        }
        return if (trouee != null) {
            QuestionRevision(contenu, FormeQuestion.PHRASE_A_TROUS, trouee)
        } else {
            QuestionRevision(contenu, FormeQuestion.GLOSE)
        }
    }

    companion object {

        /** Première boîte où l'on produit l'orthographe au lieu de la reconnaître. */
        const val BOITE_PRODUCTION = 2

        /** Ce qui remplace le mot dans une phrase trouée. */
        const val TROU = "……"

        /**
         * La phrase du LOD, le mot remplacé par [TROU], ou `null` si le mot n'y
         * est pas.
         *
         * La phrase est rangée sous le représentant de la famille : elle peut
         * porter `Haiser` là où le joueur a gagné `Haus`, et les formes de la
         * famille sont donc des cibles comme la forme elle-même.
         *
         * **Toutes** les occurrences sont trouées, pas seulement la première.
         * Une phrase qui répète le mot donnerait sinon sa propre réponse, et
         * une question dont la réponse est écrite dedans est pire qu'une phrase
         * un peu nue. `CarnetRevisionAssetTest` le vérifie sur les phrases
         * réelles, où les répétitions ne sont pas rares.
         *
         * La comparaison se fait mot par mot, jamais par `replace` sur la
         * chaîne : `an` est un mot très fréquent et le trouver dans `Land`
         * troue un mot qui n'est pas celui-là.
         */
        fun phraseATrous(phrase: String, forme: String, autresFormes: List<String>): String? {
            val cibles = LinkedHashSet<String>()
            cibles.add(AccentTolerantMatcher.normalize(forme))
            autresFormes.forEach { cibles.add(AccentTolerantMatcher.normalize(it)) }

            val sortie = StringBuilder(phrase.length)
            var trouve = false
            var i = 0
            while (i < phrase.length) {
                if (!phrase[i].isLetter()) {
                    sortie.append(phrase[i])
                    i++
                    continue
                }
                var fin = i
                while (fin < phrase.length && phrase[fin].isLetter()) fin++
                val mot = phrase.substring(i, fin)
                if (AccentTolerantMatcher.normalize(mot) in cibles) {
                    sortie.append(TROU)
                    trouve = true
                } else {
                    sortie.append(mot)
                }
                i = fin
            }
            return if (trouve) sortie.toString() else null
        }

        /**
         * Le verdict d'une réponse tapée.
         *
         * [acceptees] porte les autres réponses justes : sur une question à la
         * glose, toute carte du paquet portant la glose affichée. Mesuré sur la
         * réunion des viviers, 36,2 % des familles glosées partagent leur
         * premier sens (neuf mots se glosent « présenter »), donc comparer à une
         * seule chaîne refuserait une réponse juste. La phrase trouée, elle,
         * désigne son mot : elle n'accepte que lui.
         */
        fun verdict(saisie: String, attendue: String, acceptees: Set<String> = emptySet()): Verdict {
            val propre = saisie.trim()
            if (propre.isEmpty()) return Verdict.FAUX
            if (propre == attendue || propre in acceptees) return Verdict.EXACT
            val plie = AccentTolerantMatcher.normalize(propre)
            if (plie == AccentTolerantMatcher.normalize(attendue)) return Verdict.DETAIL
            if (acceptees.any { plie == AccentTolerantMatcher.normalize(it) }) return Verdict.DETAIL
            return Verdict.FAUX
        }

        /**
         * Les autres réponses justes d'une question, prises dans le paquet du
         * joueur.
         *
         * Le paquet, et non le dictionnaire : accepter n'importe quel synonyme
         * de la langue demanderait une table de sens que nous n'avons pas, alors
         * qu'une carte du carnet portant la même glose est, elle, une réponse que
         * le joueur avait toute raison de donner.
         */
        fun acceptees(question: QuestionRevision, paquet: List<ContenuCarte>): Set<String> {
            if (question.forme != FormeQuestion.GLOSE) return emptySet()
            val glose = question.contenu.glose
            if (glose.isEmpty()) return emptySet()
            return paquet.filterTo(HashSet()) { it.glose == glose }.mapTo(HashSet()) { it.carte.forme }
        }
    }
}
