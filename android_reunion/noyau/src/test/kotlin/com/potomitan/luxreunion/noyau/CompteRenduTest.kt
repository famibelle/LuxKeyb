package com.potomitan.luxreunion.noyau

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CompteRenduTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `mots pour LuxASR - participants d'abord, 900 caractères au plus, sans couper un mot`() {
        assertNull(ConsignesCompteRendu.promptLuxAsr(emptyList(), "  "))
        val longs = (1..200).joinToString("\n") { "Terme$it" }
        val p = ConsignesCompteRendu.promptLuxAsr(listOf("Anne Wagner"), longs)!!
        assertTrue(p.length <= 900)
        assertTrue(p.startsWith("Anne Wagner, Terme1, Terme2"))
        assertTrue(p.endsWith("."))
        assertTrue(Regex("Terme\\d+\\.$").containsMatchIn(p))
        assertEquals(listOf("BCEE", "Kirchberg", "Comité"), ConsignesCompteRendu.listeMots("BCEE, Kirchberg.\nComité;"))
    }

    @Test
    fun `consignes - réunion mixte, texte brut, modèle de la secrétaire`() {
        val s = ConsignesCompteRendu.systeme("")
        assertTrue(s.contains("luxembourgeois et le français"))
        assertTrue(s.contains("pas de Markdown"))
        assertTrue(s.contains("N'invente rien"))
        assertFalse(s.contains("<<<"))
        val avecModele = ConsignesCompteRendu.systeme("PROCÈS-VERBAL\nPrésents : …")
        assertTrue(avecModele.contains("<<<\nPROCÈS-VERBAL"))

        val infos = InfosReunion("Comité de direction", "jeudi 29 octobre 2026, 14 h 05", 4980.0,
                                 listOf("Anne Wagner", "Jos Weber"), "1. Budget\n2. Divers",
                                 glossaire = "BCEE\nKirchberg", redacteur = "Marie")
        val m = ConsignesCompteRendu.message(infos, "[0:00] Locuteur 1 : Moien.", ConsignesCompteRendu.Source.VOIX)
        assertTrue(m.contains("Durée : 1 h 23"))
        assertTrue(m.contains("Participants annoncés : Anne Wagner, Jos Weber"))
        assertTrue(m.contains("Mots et noms propres à bien orthographier : BCEE, Kirchberg"))
        assertTrue(m.contains("--- Début de la transcription ---\n[0:00] Locuteur 1 : Moien.\n--- Fin"))
        assertEquals("45 min", ConsignesCompteRendu.duree(2700.0))
    }

    @Test
    fun `réunion - écrite puis relue, direct compris`() {
        val reg = Registre(tmp.root)
        val r = reg.nouvelle(1_000L, Reunion.Mode.DIRECT).apply {
            titre = "Comité"
            participants = listOf("Anne", "Jos")
            etape = Reunion.Etape.TRANSCRIPTION
            parties = listOf(Reunion.Partie("partie-1.m4a", "audio/mp4", 0.0, job = "j1"))
            enregistrer()
            enregistrerDirect(listOf(Paragraphe(1.0, "Moien.", traduction = "Bonjour.", lacune = true)))
        }
        val relue = reg.lire(r.id)!!
        assertEquals("Comité", relue.titre)
        assertEquals(Reunion.Etape.TRANSCRIPTION, relue.etape)
        assertEquals("j1", relue.parties.single().job)
        assertNull(relue.parties.single().resultat)
        assertNull(relue.erreur)
        assertEquals(Paragraphe(1.0, "Moien.", traduction = "Bonjour.", lacune = true), relue.lireDirect().single())
        assertEquals(1, reg.toutes().size)
        assertEquals("[…] [0:01] Moien.\n", TexteTranscription.depuisDirect(relue.lireDirect()))
    }
}
