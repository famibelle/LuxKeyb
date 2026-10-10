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
 * Le protocole de cession par codes QR, version 2 (`CESSION-CARTES.md`) :
 * le receveur demande, le donneur lâche la carte et remet, le receveur prend.
 *
 * Une faute ici ne casse rien à l'écran : un code mal lu affiche un refus
 * poli, un code trop permissif fait exister une carte deux fois. Les deux se
 * jouent dans ces quelques fonctions de texte.
 */
class CessionTest {

    private val jeton = "0123456789abcdef"

    @Test
    fun uneDemandeSeRelitTelleQuelle() {
        assertEquals(jeton, Cession.lireDemande(Cession.demande(jeton)))
    }

    @Test
    fun uneRemiseSeRelitTelleQuelle() {
        val r = Cession.Remise(jeton, "Kaz", null)
        assertEquals(r, Cession.lireRemise(Cession.remise(r)))
    }

    /** Les accents et la majuscule du substantif sont la carte : ils doivent passer. */
    @Test
    fun lesAccentsEtLesDeuxPointsPassent() {
        for (forme in listOf("Bréif", "véieranzwanzeg", "Kënnt:ech")) {
            assertEquals(forme, Cession.lireRemise(Cession.remise(Cession.Remise(jeton, forme, null)))?.forme)
        }
    }

    /** Un numéral voyage avec sa valeur, sans quoi sa rareté se perd. */
    @Test
    fun unNumeralGardeSaValeur() {
        val lu = Cession.lireRemise(Cession.remise(Cession.Remise(jeton, "sechsafofzeg", 56)))
        assertEquals(56, lu?.nombre)
    }

    /**
     * Une demande n'est pas une remise, ni l'inverse : sans cela un téléphone
     * pourrait « recevoir » en scannant sa propre demande.
     */
    @Test
    fun demandeEtRemiseNeSeConfondentPas() {
        assertNull(Cession.lireRemise(Cession.demande(jeton)))
        assertNull(Cession.lireDemande(Cession.remise(Cession.Remise(jeton, "Kaz", null))))
    }

    @Test
    fun unAutreCodeNestPasUneCarte() {
        for (texte in listOf(
            "https://play.google.com/store/apps/details?id=com.potomitan.luxkeyboard",
            "LUXKEYB:C:3:$jeton::Kaz",
            "LUXKEYB:C:2:pasunjeton::Kaz",
            "LUXKEYB:C:2:$jeton::",
            "LUXKEYB:C:2:$jeton:douze:Kaz",
            "LUXKEYB:D:2:0123",
            ""
        )) {
            assertNull(texte, Cession.lireRemise(texte))
            assertNull(texte, Cession.lireDemande(texte))
        }
    }

    /** Un téléphone en 35.0.x parle la version 1 : on le reconnaît pour le dire. */
    @Test
    fun laVersionUnEstReconnue() {
        assertTrue(Cession.estAncienneVersion("LUXKEYB:O:1:$jeton:1800000000::Kaz"))
        assertTrue(Cession.estAncienneVersion("LUXKEYB:R:1:$jeton"))
        assertFalse(Cession.estAncienneVersion(Cession.demande(jeton)))
        assertNull(Cession.lireRemise("LUXKEYB:O:1:$jeton:1800000000::Kaz"))
    }

    @Test
    fun lesJetonsNeSeRepetentPas() {
        val jetons = List(500) { Cession.nouveauJeton() }
        assertEquals(500, jetons.toSet().size)
        assertTrue(jetons.all { it.length == 16 && it.all { c -> c in "0123456789abcdef" } })
    }

    /** Une demande vit une semaine, puis ne peut plus rien recevoir. */
    @Test
    fun lesDemandesPerimeesSOublient() {
        val maintenant = 1_800_000_000L
        val brut = "$jeton@${maintenant - 3600},fedcba9876543210@${maintenant - Cession.VIE_DEMANDE_S - 1},abime"
        assertEquals(listOf(jeton), Cession.demandesValides(brut, maintenant).map { it.first })
    }

    /**
     * Les remises en attente survivent à l'appli fermée : c'est ce qui évite
     * qu'une carte sortie du carnet se perde parce qu'on a fermé l'écran.
     */
    @Test
    fun lesRemisesSeRelisentEtExpirent() {
        val maintenant = 1_800_000_000L
        val gardee = Cession.Remise(jeton, "sechsafofzeg", 56, maintenant - 3600)
        val perimee = Cession.Remise("fedcba9876543210", "Kaz", null, maintenant - Cession.VIE_REMISE_S - 1)
        val relues = Cession.remisesValides(Cession.encoderRemises(listOf(gardee, perimee)), maintenant)
        assertEquals(listOf(gardee), relues)
        assertEquals(emptyList<Cession.Remise>(), Cession.remisesValides("pas du json", maintenant))
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
