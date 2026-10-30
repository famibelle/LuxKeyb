package com.potomitan.luxreunion.noyau

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.PriorityQueue

/** Une réunion jouée sur une horloge simulée, contre un faux service. */
class DirectTest {

    class HorlogeSimulee : Planificateur {
        private class Tache(val quand: Long, val n: Long, val action: () -> Unit) { var annulee = false }
        var t = 0L
        private var n = 0L
        private val taches = PriorityQueue<Tache>(compareBy<Tache>({ it.quand }, { it.n }))
        override fun plus(ms: Long, action: () -> Unit): Annulable {
            val ta = Tache(t + ms, n++, action)
            taches.add(ta)
            return Annulable { ta.annulee = true }
        }
        override fun maintenant() = t
        fun avancer(ms: Long) {
            val fin = t + ms
            while (true) {
                val p = taches.peek() ?: break
                if (p.quand > fin) break
                taches.poll()
                t = p.quand
                if (!p.annulee) p.action()
            }
            t = fin
        }
    }

    class FausseLiaison(val ecouteur: EcouteurLiaison) : Liaison {
        val textes = mutableListOf<String>()
        var octets = 0L
        var fermee = false
        var attente = 0L
        override fun envoyerTexte(texte: String): Boolean { textes += texte; return !fermee }
        override fun envoyerOctets(octets: ByteArray): Boolean { this.octets += octets.size; return !fermee }
        override fun fermer() { fermee = true }
        override fun enAttente() = attente
        fun ouvrir() = ecouteur.surOuverture()
        fun recevoir(o: JSONObject) = ecouteur.surMessage(o.toString())
        fun stopDemande() = textes.any { it.contains("\"stop\"") }
        fun transcription(acc: String, partiel: String = "", commit: Boolean = false) =
            recevoir(JSONObject().put("type", "transcription").put("accumulated_text", acc).put("partial_text", partiel)
                .put("send_reason", if (commit) "silence_commit" else "update"))
        fun traduction(acc: String, source: String? = null) =
            recevoir(JSONObject().put("type", "translation").put("success", true).put("accumulated_translation", acc)
                .apply { if (source != null) put("source_text", source) })
    }

    private lateinit var horloge: HorlogeSimulee
    private val liaisons = mutableListOf<FausseLiaison>()
    private val etats = mutableListOf<DirectLuxAsr.Etat>()
    private var dernier: List<Paragraphe> = emptyList()
    private val phrases = mutableListOf<Pair<String, String>>()
    private var fin: List<Paragraphe>? = null
    private var raison: DirectLuxAsr.Raison? = null
    private lateinit var direct: DirectLuxAsr

    @Before
    fun preparer() {
        horloge = HorlogeSimulee()
        direct = DirectLuxAsr(object : DirectLuxAsr.Ecouteur {
            override fun surEtat(etat: DirectLuxAsr.Etat) { etats += etat }
            override fun surTexte(paragraphes: List<Paragraphe>) { dernier = paragraphes }
            override fun surPhrase(texte: String, traduction: String) { phrases += texte to traduction }
            override fun surFin(paragraphes: List<Paragraphe>, raison: DirectLuxAsr.Raison?) {
                fin = paragraphes; this@DirectTest.raison = raison
            }
        }, FabriqueLiaison { e -> FausseLiaison(e).also { liaisons += it } }, horloge)
    }

    /** [s] secondes de parole, par blocs de 160 ms comme le micro. */
    private fun parler(s: Double) {
        repeat((s / 0.16).toInt()) {
            direct.audio(ByteArray(5120))
            horloge.avancer(160)
        }
    }

    @Test
    fun `une phrase, sa traduction, puis l'arrêt`() {
        direct.start()
        assertEquals(DirectLuxAsr.Etat.CONNEXION, direct.etat)
        parler(0.5)                                  // capté avant l'ouverture
        val l = liaisons.single()
        l.ouvrir()
        val config = JSONObject(l.textes.first())
        assertEquals("config", config.getString("type"))
        assertEquals("lb", config.getString("language"))
        assertTrue(config.getBoolean("translation_enabled"))
        assertEquals("fr", config.getString("translation_target"))
        assertEquals(3 * 5120L, l.octets)            // l'audio en attente est parti d'un trait
        assertEquals(DirectLuxAsr.Etat.ECOUTE, direct.etat)

        parler(2.0)
        l.transcription("", "Moien")
        assertEquals("Moien", dernier.single().partiel)
        l.transcription("Moien, wéi geet et?", "", commit = true)
        assertEquals("Moien, wéi geet et?", dernier.single().texte)
        l.traduction("Bonjour, comment ça va ?")
        assertEquals("Bonjour, comment ça va ?", dernier.single().traduction)

        direct.stop()
        assertTrue(l.stopDemande())
        l.recevoir(JSONObject().put("type", "recording_stopped"))
        assertEquals(DirectLuxAsr.Etat.REPOS, direct.etat)
        assertEquals("Bonjour, comment ça va ?", fin!!.single().traduction)
        assertNull(raison)
    }

    @Test
    fun `chaque traduction arrive avec sa phrase, pour les sous-titres`() {
        direct.start()
        val l = liaisons.single()
        l.ouvrir()
        parler(2.0)
        l.transcription("Éischte Saz. Zweete", commit = true)
        l.traduction("Première phrase.", source = "Éischte Saz.")
        l.transcription("Éischte Saz. Zweete Saz.", commit = true)
        l.traduction("Première phrase. Deuxième phrase.", source = "Zweete Saz.")
        assertEquals(listOf("Éischte Saz." to "Première phrase.", "Zweete Saz." to "Deuxième phrase."), phrases)
        // Sans source_text (ancien service), pas de paire, mais la traduction reste rangée.
        l.traduction("Première phrase. Deuxième phrase. Troisième.")
        assertEquals(2, phrases.size)
        assertEquals("Première phrase. Deuxième phrase. Troisième.", dernier.last().traduction)
    }

    @Test
    fun `une pause de plus de 2,5 s ouvre un paragraphe, chacun reçoit sa traduction`() {
        direct.start()
        val l = liaisons.single()
        l.ouvrir()
        parler(2.0)
        l.transcription("Éischte Saz.", commit = true)
        l.traduction("Première phrase.")
        horloge.avancer(3000)
        parler(1.0)
        l.transcription("Éischte Saz. Zweete Saz.", commit = true)
        l.traduction("Première phrase. Deuxième phrase.")
        assertEquals(listOf("Éischte Saz.", "Zweete Saz."), dernier.map { it.texte })
        assertEquals(listOf("Première phrase.", "Deuxième phrase."), dernier.map { it.traduction })
    }

    @Test
    fun `la liaison est renouvelée passé deux minutes, sans rien perdre`() {
        direct.start()
        val l1 = liaisons.single()
        l1.ouvrir()
        parler(121.0)
        l1.transcription("Eng laang Ried.", commit = true)
        assertTrue("stop envoyé pour renouveler", l1.stopDemande())
        parler(1.0)                                  // capté pendant le raccord
        l1.recevoir(JSONObject().put("type", "recording_stopped"))
        val l2 = liaisons[1]
        horloge.avancer(10)
        l2.ouvrir()
        assertTrue("l'audio du raccord part sur la nouvelle liaison", l2.octets >= 6 * 5120L)
        l2.transcription("Weider.", commit = true)
        assertEquals("Eng laang Ried. Weider.", dernier.joinToString(" ") { it.texte })
        assertEquals(DirectLuxAsr.Etat.ECOUTE, direct.etat)
    }

    @Test
    fun `coupures - reprise aussitôt, puis hors ligne, puis retour avec une lacune signalée`() {
        direct.start()
        liaisons[0].ouvrir()
        parler(1.0)
        liaisons[0].transcription("Virun der Pann.", commit = true)
        // Le service tombe : trois reprises aussitôt, puis HORS_LIGNE.
        repeat(4) { liaisons.last().ecouteur.surFermeture() }
        assertEquals(DirectLuxAsr.Etat.HORS_LIGNE, direct.etat)
        assertEquals(4, liaisons.size)
        // L'enregistrement continue ; le direct ne garde que 30 s d'audio en
        // souffrance. À 15 s, un essai qui ne répond pas (8 s), puis 30 s d'attente.
        parler(40.0)
        assertEquals(5, liaisons.size)
        assertEquals(DirectLuxAsr.Etat.HORS_LIGNE, direct.etat)
        horloge.avancer(13_300)
        assertEquals(6, liaisons.size)
        liaisons.last().ouvrir()
        assertEquals(DirectLuxAsr.Etat.ECOUTE, direct.etat)
        assertTrue(liaisons.last().octets <= 30 * 32_000L + 5120)
        liaisons.last().transcription("No der Pann.", commit = true)
        assertEquals(listOf("Virun der Pann.", "No der Pann."), dernier.map { it.texte })
        assertFalse(dernier[0].lacune)
        assertTrue("le paragraphe d'après porte la lacune", dernier[1].lacune)
    }

    @Test
    fun `arrêt pendant une coupure - le texte reçu est rendu aussitôt`() {
        direct.start()
        liaisons[0].ouvrir()
        parler(1.0)
        liaisons[0].transcription("Bis elo.", commit = true)
        repeat(4) { liaisons.last().ecouteur.surFermeture() }
        direct.stop()
        assertEquals(DirectLuxAsr.Etat.REPOS, direct.etat)
        assertEquals("Bis elo.", fin!!.single().texte)
    }

    @Test
    fun `la traduction de la dernière phrase est attendue après l'arrêt`() {
        direct.start()
        val l = liaisons.single()
        l.ouvrir()
        parler(2.0)
        l.transcription("Lescht Wuert.", commit = true)
        direct.stop()
        l.recevoir(JSONObject().put("type", "recording_stopped"))
        assertNull("pas encore fini : la traduction est attendue", fin)
        horloge.avancer(1500)
        l.traduction("Dernier mot.")
        assertEquals("Dernier mot.", fin!!.single().traduction)
    }

    @Test
    fun `le service ne répond pas à la connexion - délai, puis reprise`() {
        direct.start()
        horloge.avancer(8_000)
        assertEquals(2, liaisons.size)
        assertEquals(DirectLuxAsr.Etat.CONNEXION, direct.etat)
    }

    @Test
    fun `réseau trop lent - la liaison est coupée et le direct repart au présent`() {
        direct.start()
        val l = liaisons.single()
        l.ouvrir()
        parler(1.0)
        l.attente = 31 * 32_000L
        parler(0.2)
        assertTrue(l.fermee)
        assertEquals(2, liaisons.size)
    }
}
