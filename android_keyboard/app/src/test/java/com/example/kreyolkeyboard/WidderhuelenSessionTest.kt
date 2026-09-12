package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.carnet.CarteMot
import com.example.kreyolkeyboard.carnet.ContenuCarte
import com.example.kreyolkeyboard.carnet.FormeQuestion
import com.example.kreyolkeyboard.carnet.JeuCarte
import com.example.kreyolkeyboard.carnet.Rarete
import com.example.kreyolkeyboard.carnet.SessionWidderhuelen
import com.example.kreyolkeyboard.carnet.Verdict
import com.example.kreyolkeyboard.carnet.Widderhuelen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les règles d'une session de révision.
 *
 * Aucune ne se voit à l'écran quand elle casse : une question posée dans la
 * mauvaise forme, un échec qui ne repasse pas, une réponse juste refusée parce
 * qu'elle était comparée à une seule chaîne. Le jeu continue de tourner, il
 * enseigne simplement de travers.
 */
class WidderhuelenSessionTest {

    private fun contenu(
        forme: String,
        boite: Int = 0,
        glose: String = "sens de $forme",
        exemple: String? = null,
        autresFormes: List<String> = emptyList(),
        numero: Int = 1
    ) = ContenuCarte(
        carte = CarteMot(
            forme = forme,
            premiereFois = 0L,
            rencontres = 1,
            numero = numero,
            jeux = setOf(JeuCarte.WUERTPLAZ),
            boite = boite,
            jourEcheance = 0
        ),
        rarete = Rarete.COMMUN,
        rang = 42,
        glose = glose,
        autresFormes = autresFormes,
        exemple = exemple
    )

    // ------------------------------------------------- la boîte fait la question

    @Test
    fun `les deux premieres boites reconnaissent, les suivantes produisent`() {
        val session = SessionWidderhuelen(
            listOf(
                contenu("Haus", boite = 0, exemple = "mir bauen en Haus", numero = 1),
                contenu("Haus", boite = 1, exemple = "mir bauen en Haus", numero = 2),
                contenu("Haus", boite = 2, exemple = "mir bauen en Haus", numero = 3)
            )
        )
        assertEquals(FormeQuestion.RECONNAISSANCE, session.courante?.forme)
        session.repondre(true)
        assertEquals(FormeQuestion.RECONNAISSANCE, session.courante?.forme)
        session.repondre(true)
        assertEquals(FormeQuestion.PHRASE_A_TROUS, session.courante?.forme)
        assertEquals(SessionWidderhuelen.BOITE_PRODUCTION, 2)
    }

    @Test
    fun `une carte de production sans phrase retombe sur la glose`() {
        val session = SessionWidderhuelen(listOf(contenu("Forschett", boite = 3, exemple = null)))
        assertEquals(FormeQuestion.GLOSE, session.courante?.forme)
    }

    @Test
    fun `une phrase qui ne contient pas le mot retombe aussi sur la glose`() {
        // Mesuré sur les actifs livrés : 97,2 % des formes des trois viviers de
        // contenu ont une phrase, mais seules 93,3 % en ont une où le mot
        // rencontré se retrouve tel quel. La phrase du LOD est rangée sous le
        // représentant de la famille, et elle peut porter une flexion que la
        // famille ne liste pas.
        val session = SessionWidderhuelen(
            listOf(contenu("gezunn", boite = 2, exemple = "zéi d'Jupe nach riicht"))
        )
        assertEquals(FormeQuestion.GLOSE, session.courante?.forme)
    }

    // -------------------------------------------------------- le troage lui-même

    @Test
    fun `le troage remplace le mot rencontre`() {
        val trouee = SessionWidderhuelen.phraseATrous(
            "mir bauen en Haus am Duerf", "Haus", emptyList()
        )
        assertEquals("mir bauen en ${SessionWidderhuelen.TROU} am Duerf", trouee?.texte)
        assertEquals("Haus", trouee?.motMasque)
    }

    @Test
    fun `le troage accepte une autre forme de la famille, et la reclame`() {
        // La phrase est rangée sous le représentant : elle peut porter
        // « Haiser » là où le joueur a gagné « Haus ». C'est alors « Haiser »
        // que la question demande — creuser le trou à « Haiser » en attendant
        // « Haus » réclamait un mot que la phrase ne veut pas, et refusait le
        // seul qui la complète.
        val trouee = SessionWidderhuelen.phraseATrous(
            "d'Haiser sinn deier", "Haus", listOf("Haiser", "Haises")
        )
        assertEquals("d'${SessionWidderhuelen.TROU} sinn deier", trouee?.texte)
        assertEquals("Haiser", trouee?.motMasque)
    }

    @Test
    fun `la forme de la carte passe avant celle de la famille`() {
        // Quand la phrase porte les deux, c'est le mot que le joueur a gagné
        // qui est demandé, et lui seul : l'autre reste en clair, il fait partie
        // de la phrase que le joueur doit lire.
        val trouee = SessionWidderhuelen.phraseATrous(
            "en Haus, zwee Haiser", "Haus", listOf("Haiser")
        )
        assertEquals("en ${SessionWidderhuelen.TROU}, zwee Haiser", trouee?.texte)
        assertEquals("Haus", trouee?.motMasque)
    }

    @Test
    fun `le troage compare des mots entiers, jamais des morceaux`() {
        // « an » est un des mots les plus fréquents de la langue, et le
        // chercher dans « Land » trouerait un mot qui n'est pas celui-là. Un
        // simple replace sur la chaîne ferait exactement cela.
        assertNull(SessionWidderhuelen.phraseATrous("en schéint Land", "an", emptyList()))
        assertEquals(
            "${SessionWidderhuelen.TROU} schéint Land",
            SessionWidderhuelen.phraseATrous("en schéint Land", "en", emptyList())?.texte
        )
    }

    @Test
    fun `le troage ignore accents et casse pour trouver le mot`() {
        val trouee = SessionWidderhuelen.phraseATrous(
            "d'Haus ass gréng", "Gréng", listOf()
        )
        assertEquals("d'Haus ass ${SessionWidderhuelen.TROU}", trouee?.texte)
        // Le mot réclamé est la forme de la carte, pas la casse de la phrase.
        assertEquals("Gréng", trouee?.motMasque)
    }

    @Test
    fun `toutes les occurrences sont trouees`() {
        // Une phrase qui répète le mot donnerait sinon sa propre réponse.
        val trouee = SessionWidderhuelen.phraseATrous(
            "en Haus ass en Haus", "Haus", emptyList()
        )
        assertEquals(
            "en ${SessionWidderhuelen.TROU} ass en ${SessionWidderhuelen.TROU}",
            trouee?.texte
        )
    }

    // ------------------------------------------------------------- la notation

    @Test
    fun `la forme exacte est exacte`() {
        assertEquals(Verdict.EXACT, SessionWidderhuelen.verdict("Haus", "Haus"))
    }

    @Test
    fun `un accent ou une majuscule manquante vaut presque`() {
        // C'est le cœur de la leçon : compté comme réussi, mais la carte ne
        // monte pas de boîte, et la différence est montrée. Laisser passer
        // « greng » pour « gréng » enseignerait la faute que le jeu corrige.
        assertEquals(Verdict.DETAIL, SessionWidderhuelen.verdict("greng", "gréng"))
        assertEquals(Verdict.DETAIL, SessionWidderhuelen.verdict("haus", "Haus"))
        assertEquals(Verdict.DETAIL, SessionWidderhuelen.verdict("Forschett", "forschett"))
    }

    @Test
    fun `autre chose est faux, et le vide aussi`() {
        assertEquals(Verdict.FAUX, SessionWidderhuelen.verdict("Baum", "Haus"))
        assertEquals(Verdict.FAUX, SessionWidderhuelen.verdict("", "Haus"))
        assertEquals(Verdict.FAUX, SessionWidderhuelen.verdict("   ", "Haus"))
    }

    @Test
    fun `une question a la glose accepte toute carte du paquet portant cette glose`() {
        // Mesuré sur la réunion des viviers : 36,2 % des familles glosées
        // partagent leur premier sens, neuf mots se glosant « présenter ».
        // Comparer à une seule chaîne refuserait une réponse que le joueur
        // avait toute raison de donner.
        val paquet = listOf(
            contenu("Accord", boite = 3, glose = "accord", numero = 1),
            contenu("Akkord", boite = 3, glose = "accord", numero = 2),
            contenu("Haus", boite = 3, glose = "maison", numero = 3)
        )
        val session = SessionWidderhuelen(paquet)
        val question = session.courante!!
        assertEquals(FormeQuestion.GLOSE, question.forme)

        val acceptees = SessionWidderhuelen.acceptees(question, paquet)
        assertEquals(setOf("Accord", "Akkord"), acceptees)
        assertEquals(Verdict.EXACT, SessionWidderhuelen.verdict("Akkord", "Accord", acceptees))
        assertEquals(Verdict.DETAIL, SessionWidderhuelen.verdict("akkord", "Accord", acceptees))
        assertEquals(Verdict.FAUX, SessionWidderhuelen.verdict("Haus", "Accord", acceptees))
    }

    @Test
    fun `une phrase trouee n'accepte que son mot`() {
        // La phrase désigne son mot : accepter un synonyme du paquet
        // reviendrait à accepter une phrase fausse.
        val paquet = listOf(
            contenu("Accord", boite = 3, glose = "accord", exemple = "en Accord fannen", numero = 1),
            contenu("Akkord", boite = 3, glose = "accord", numero = 2)
        )
        val session = SessionWidderhuelen(paquet)
        val question = session.courante!!
        assertEquals(FormeQuestion.PHRASE_A_TROUS, question.forme)
        assertTrue(SessionWidderhuelen.acceptees(question, paquet).isEmpty())
    }

    // -------------------------------------------------------------- la file

    @Test
    fun `un echec repasse une fois, en fin de session`() {
        val session = SessionWidderhuelen(
            listOf(
                contenu("rate", numero = 1),
                contenu("juste", numero = 2)
            )
        )
        assertEquals(2, session.total)

        assertEquals("rate", session.courante?.motAttendu)
        assertTrue("un premier échec doit être noté au carnet", session.repondre(false))

        assertEquals("juste", session.courante?.motAttendu)
        assertTrue(session.repondre(true))

        // Le mot raté revient, mais en fin de file : le redemander aussitôt ne
        // testerait que la mémoire de l'instant.
        assertEquals("rate", session.courante?.motAttendu)
        assertFalse("un repassage ne se note pas deux fois", session.repondre(true))

        assertTrue(session.fini)
        assertEquals(listOf("juste"), session.reussies)
        assertEquals(listOf("rate"), session.ratees)
    }

    @Test
    fun `un echec au repassage ne relance pas la carte indefiniment`() {
        val session = SessionWidderhuelen(listOf(contenu("rate")))
        session.repondre(false)
        assertEquals("rate", session.courante?.motAttendu)
        session.repondre(false)
        // Rejouer jusqu'à réussite ferait durer une session d'une minute et
        // demie jusqu'à l'abandon.
        assertTrue(session.fini)
        assertEquals(1, session.ratees.size)
    }

    @Test
    fun `une session vide est finie d'emblee`() {
        val session = SessionWidderhuelen(emptyList())
        assertTrue(session.fini)
        assertEquals(0, session.total)
        assertNull(session.courante)
        assertFalse(session.repondre(true))
    }

    @Test
    fun `le rang avance avec les reponses et ne depasse pas le total`() {
        val session = SessionWidderhuelen(
            listOf(contenu("un", numero = 1), contenu("deux", numero = 2))
        )
        assertEquals(1, session.rang)
        session.repondre(false)
        assertEquals(2, session.rang)
        session.repondre(true)
        // Le repassage du premier mot n'ajoute pas une question au compteur.
        assertEquals(2, session.rang)
        assertEquals(2, session.total)
    }

    @Test
    fun `une carte de la file arrive dans l'ordre du calendrier`() {
        // La session prend la file telle que [Widderhuelen.file] la rend : ce
        // test est là pour que le lien entre les deux ne soit pas coupé par
        // une refonte de l'une sans l'autre.
        val cartes = listOf(
            contenu("ancienne", numero = 1).carte.copy(jourEcheance = 90),
            contenu("recente", numero = 2).carte.copy(jourEcheance = 100)
        )
        val file = Widderhuelen.file(cartes, 100)
        assertEquals(listOf("ancienne", "recente"), file.map { it.forme })
    }
}
