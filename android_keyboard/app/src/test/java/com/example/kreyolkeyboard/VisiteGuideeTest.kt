package com.example.kreyolkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La visite guidée (23.3.0) : quand elle se propose d'elle-même, et comment
 * elle avance. Le dessin du voile se vérifie à l'écran, pas ici.
 */
class VisiteGuideeTest {

    @Test
    fun `la visite de l'application se propose une fois, sur l'accueil du jour`() {
        assertTrue(VisiteGuidee.doitSeLancer(dejaFaite = false, accueilDuJour = true, autreChoseOuvert = false))
        // Faite ou passée : elle ne revient que par « Visite guidée »
        assertFalse(VisiteGuidee.doitSeLancer(dejaFaite = true, accueilDuJour = true, autreChoseOuvert = false))
        // Tant que le clavier n'est pas installé, l'installation passe d'abord
        assertFalse(VisiteGuidee.doitSeLancer(dejaFaite = false, accueilDuJour = false, autreChoseOuvert = false))
        // Jamais par-dessus la pochette de bienvenue, un jeu ou le carnet
        assertFalse(VisiteGuidee.doitSeLancer(dejaFaite = false, accueilDuJour = true, autreChoseOuvert = true))
    }

    @Test
    fun `la visite du carnet attend une carte a montrer`() {
        assertTrue(VisiteGuidee.carnetDoitSeLancer(dejaFaite = false, cartes = 1, carteDemandee = false))
        assertFalse(VisiteGuidee.carnetDoitSeLancer(dejaFaite = false, cartes = 0, carteDemandee = false))
        assertFalse(VisiteGuidee.carnetDoitSeLancer(dejaFaite = true, cartes = 12, carteDemandee = false))
        // Le carnet qui s'ouvre sur une carte reçue la montre d'abord
        assertFalse(VisiteGuidee.carnetDoitSeLancer(dejaFaite = false, cartes = 12, carteDemandee = true))
    }

    @Test
    fun `le deroule va jusqu'a la derniere etape et s'y arrete`() {
        val d = VisiteGuidee.Deroule(3)
        assertEquals(0, d.index)
        assertFalse(d.derniere)
        assertTrue(d.suivante())
        assertTrue(d.suivante())
        assertTrue(d.derniere)
        assertFalse(d.suivante())
        assertEquals(2, d.index)
    }

    @Test
    fun `une visite reprise apres rotation repart de son etape, bornee`() {
        assertEquals(4, VisiteGuidee.Deroule(7, depart = 4).index)
        assertEquals(6, VisiteGuidee.Deroule(7, depart = 40).index)
        assertEquals(0, VisiteGuidee.Deroule(7, depart = -3).index)
    }

    @Test
    fun `le compteur part de un`() {
        assertEquals("1 / 7", VisiteGuidee.compteur(0, 7))
        assertEquals("7 / 7", VisiteGuidee.compteur(6, 7))
    }

    @Test
    fun `chaque sujet du sommaire tient dans sa visite`() {
        VisiteGuidee.Sujet.entries.forEach { sujet ->
            val total = if (sujet.visite == VisiteGuidee.Visite.APPLICATION) VisiteGuidee.ETAPES_APPLICATION
            else VisiteGuidee.ETAPES_CARNET
            assertTrue("$sujet", sujet.etapes.first >= 0 && sujet.etapes.last < total && !sujet.etapes.isEmpty())
        }
    }

    @Test
    fun `la dictee et le partage d'une carte ont chacun leur sujet`() {
        assertEquals(1..1, VisiteGuidee.Sujet.DICTEE.etapes)
        assertEquals(VisiteGuidee.Visite.CARNET, VisiteGuidee.Sujet.PARTAGER.visite)
        assertEquals(1..1, VisiteGuidee.Sujet.PARTAGER.etapes)
    }

    @Test
    fun `donner ou recevoir rejoue tout l'echange, jusqu'a l'appareil photo`() {
        // Céder, l'échange pas à pas, Recevoir, l'appareil photo (37.7.7)
        assertEquals(VisiteGuidee.Visite.CARNET, VisiteGuidee.Sujet.DONNER_OU_RECEVOIR.visite)
        assertEquals(2..5, VisiteGuidee.Sujet.DONNER_OU_RECEVOIR.etapes)
        assertEquals(VisiteGuidee.ETAPES_CARNET - 1, VisiteGuidee.Sujet.DONNER_OU_RECEVOIR.etapes.last)
    }

    @Test
    fun `le clavier ouvre la visite entiere`() {
        assertEquals(0..0, VisiteGuidee.Sujet.GESTES.etapes)
        assertEquals(1..1, VisiteGuidee.Sujet.DICTEE.etapes)
    }

    @Test
    fun `passer mene au rappel, puis ferme`() {
        // Visite entière de 9 bulles, rappel en dernier
        assertEquals(8, VisiteGuidee.passerVers(index = 2, rappel = 8))
        assertEquals(null, VisiteGuidee.passerVers(index = 8, rappel = 8))
        // Un sujet n'a pas de rappel : « Passer » ferme
        assertEquals(null, VisiteGuidee.passerVers(index = 0, rappel = null))
    }

    @Test
    fun `passer disparait sur une bulle seule et sur le rappel`() {
        assertFalse(VisiteGuidee.passerVisible(nombre = 1, index = 0, rappel = null))
        assertFalse(VisiteGuidee.passerVisible(nombre = 9, index = 8, rappel = 8))
        assertTrue(VisiteGuidee.passerVisible(nombre = 9, index = 3, rappel = 8))
        assertTrue(VisiteGuidee.passerVisible(nombre = 3, index = 0, rappel = null))
    }

    @Test
    fun `deroule saute au rang demande, borne`() {
        val d = VisiteGuidee.Deroule(9)
        d.allerA(8)
        assertTrue(d.derniere)
        d.allerA(40)
        assertEquals(8, d.index)
    }

    @Test
    fun `la bulle se pose sous une cible du haut, au-dessus d'une cible du bas`() {
        // Un onglet en haut : dessous
        assertEquals(true to 216, VisiteGuidee.placement(100f, 200f, 1600, 400, 16, 40))
        // Un bouton en bas : dessus, retrait depuis le bas
        assertEquals(false to 316, VisiteGuidee.placement(1300f, 1400f, 1600, 400, 16, 40))
    }

    @Test
    fun `une cible trop grande laisse la bulle en bas de l'ecran`() {
        // La carte entière du carnet sur le A21s : ni dessous ni dessus
        assertEquals(false to 40, VisiteGuidee.placement(100f, 1180f, 1600, 600, 16, 40))
    }
}
