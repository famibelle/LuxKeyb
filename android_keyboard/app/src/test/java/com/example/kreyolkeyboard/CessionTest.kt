package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.carnet.Cession
import com.example.kreyolkeyboard.carnet.JeuCarte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le protocole de cession par codes QR (`CESSION-CARTES.md`).
 *
 * Une faute ici ne casse rien à l'écran : un code mal lu affiche un refus
 * poli, un code trop permissif fait perdre une carte au mauvais moment. Les
 * deux se jouent dans ces quelques fonctions de texte.
 */
class CessionTest {

    private val maintenant = 1_800_000_000L
    private val jeton = "0123456789abcdef"

    private fun offre(forme: String = "Kaz", nombre: Int? = null, echeance: Long = maintenant + Cession.VALIDITE_S) =
        Cession.Offre(jeton, echeance, forme, nombre)

    @Test
    fun uneOffreSeRelitTelleQuelle() {
        val o = offre()
        assertEquals(o, Cession.lireOffre(Cession.offre(o), maintenant))
    }

    /** Les accents et la majuscule du substantif sont la carte : ils doivent passer. */
    @Test
    fun lesAccentsEtLesDeuxPointsPassent() {
        for (forme in listOf("Bréif", "véieranzwanzeg", "Kënnt:ech")) {
            assertEquals(forme, Cession.lireOffre(Cession.offre(offre(forme)), maintenant)?.forme)
        }
    }

    /** Un numéral voyage avec sa valeur, sans quoi sa rareté se perd. */
    @Test
    fun unNumeralGardeSaValeur() {
        val lu = Cession.lireOffre(Cession.offre(offre("sechsafofzeg", 56)), maintenant)
        assertEquals(56, lu?.nombre)
    }

    @Test
    fun uneOffreExpireeEstRefusee() {
        val o = offre(echeance = maintenant - 3600)
        assertNull(Cession.lireOffre(Cession.offre(o), maintenant))
    }

    /** Deux minutes d'écart d'horloge entre deux téléphones ne doivent rien refuser. */
    @Test
    fun unPeuDeDeriveDHorlogeEstToleree() {
        val o = offre(echeance = maintenant)
        assertEquals(o, Cession.lireOffre(Cession.offre(o), maintenant + 90))
    }

    /** Une échéance lointaine trahit une offre fabriquée pour ne jamais expirer. */
    @Test
    fun uneEcheanceTropLointaineEstRefusee() {
        val o = offre(echeance = maintenant + 30 * 24 * 3600)
        assertNull(Cession.lireOffre(Cession.offre(o), maintenant))
    }

    @Test
    fun unAutreCodeNestPasUneCarte() {
        for (texte in listOf(
            "https://play.google.com/store/apps/details?id=com.potomitan.luxkeyboard",
            "LUXKEYB:O:2:$jeton:$maintenant::Kaz",
            "LUXKEYB:O:1:pasunjeton:$maintenant::Kaz",
            "LUXKEYB:O:1:$jeton:$maintenant::",
            "LUXKEYB:R:1:$jeton",
            ""
        )) {
            assertNull(texte, Cession.lireOffre(texte, maintenant))
        }
    }

    /** Le donneur ne lâche sa carte que sur la réception de son offre. */
    @Test
    fun laReceptionPorteLeJetonDeLOffre() {
        assertEquals(jeton, Cession.lireReception(Cession.reception(jeton)))
        assertNull(Cession.lireReception(Cession.offre(offre())))
        assertNull(Cession.lireReception("LUXKEYB:R:1:0123"))
    }

    @Test
    fun lesJetonsNeSeRepetentPas() {
        val jetons = List(500) { Cession.nouveauJeton() }
        assertEquals(500, jetons.toSet().size)
        assertTrue(jetons.all { it.length == 16 && it.all { c -> c in "0123456789abcdef" } })
    }

    /** Le cadeau n'est pas un jeu : il ne compte pas dans la série des sept jeux. */
    @Test
    fun leCadeauNestPasUnJeu() {
        assertFalse(JeuCarte.CADEAU in JeuCarte.JEUX)
        assertEquals(7, JeuCarte.JEUX.size)
        assertNotEquals(JeuCarte.CADEAU, JeuCarte.parId("ac"))
        assertEquals(JeuCarte.CADEAU, JeuCarte.parId("cd"))
    }
}
