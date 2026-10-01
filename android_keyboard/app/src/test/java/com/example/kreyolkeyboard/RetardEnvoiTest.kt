package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.stt.RetardEnvoi
import com.example.kreyolkeyboard.stt.RetardEnvoi.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La règle qui décide qu'un réseau ne suit plus la dictée en ligne. Elle ne se
 * voit pas sur un bon réseau : une régression ici ne casserait rien au bureau,
 * et laisserait l'utilisateur parler dans le vide dans le train.
 */
class RetardEnvoiTest {

    private val seconde = RetardEnvoi.OCTETS_PAR_SECONDE

    @Test
    fun leDebitEstCeluiDuPcmSeizeBitsASeizeKilohertz() {
        assertEquals(16_000L * 2, seconde)
    }

    @Test
    fun uneFileVideOuCourteEstFluide() {
        assertEquals(Verdict.FLUIDE, RetardEnvoi.juger(0, etaitLent = false))
        assertEquals(Verdict.FLUIDE, RetardEnvoi.juger(seconde - 1, etaitLent = false))
    }

    @Test
    fun uneSecondeEnSouffrancePrevient() {
        assertEquals(Verdict.LENT, RetardEnvoi.juger(seconde, etaitLent = false))
    }

    @Test
    fun lAvertissementNeClignotePasAutourDuSeuil() {
        // Redescendre juste sous une seconde ne suffit pas à l'éteindre…
        assertEquals(Verdict.LENT, RetardEnvoi.juger(seconde / 2, etaitLent = true))
        // …il faut que la file se soit vraiment vidée.
        assertEquals(Verdict.FLUIDE, RetardEnvoi.juger(seconde / 4, etaitLent = true))
    }

    @Test
    fun huitSecondesEnAttenteCoupentLeMicro() {
        assertEquals(Verdict.LENT, RetardEnvoi.juger(8 * seconde - 1, etaitLent = true))
        assertEquals(Verdict.COUPER_MICRO, RetardEnvoi.juger(8 * seconde, etaitLent = true))
        assertEquals(Verdict.COUPER_MICRO, RetardEnvoi.juger(8 * seconde, etaitLent = false))
    }

    @Test
    fun unCreuxDeDixSecondesAUnQuartDuBesoinNeCoupePasLeMicro() {
        // Le tunnel du banc du 1er octobre 2026 : 10 s à 8 000 o/s. Le retard
        // monte de 24 000 o/s, dont 64 Ko absorbés par le tampon système.
        val retard = 10 * (seconde - 8_000) - 2 * RetardEnvoi.TAMPON_SYSTEME_OCTETS
        assertEquals(Verdict.LENT, RetardEnvoi.juger(retard, etaitLent = true))
    }

    @Test
    fun lentNEstPasBloque() {
        // De l'audio attend, mais il en est parti il y a moins de 5 s.
        assertFalse(RetardEnvoi.bloque(10 * seconde, RetardEnvoi.BLOCAGE_MS - 1))
        assertTrue(RetardEnvoi.bloque(10 * seconde, RetardEnvoi.BLOCAGE_MS))
    }

    @Test
    fun uneFileVideNEstJamaisBloquee() {
        assertFalse(RetardEnvoi.bloque(0, 60_000))
    }

    @Test
    fun leRetardQuiCoupeLeMicroSeVideDansLeTempsLaisse() {
        // À 96 kbit/s (12 000 o/s), le pire cas mesuré qui reste utilisable.
        val aVider = RetardEnvoi.COUPER_MICRO_OCTETS + 2 * RetardEnvoi.TAMPON_SYSTEME_OCTETS
        assertTrue(aVider * 1000 / 12_000 <= RetardEnvoi.FINALISATION_MAX_MS)
    }

    @Test
    fun leTamponSystemeNeMasquePasPlusDeDeuxSecondes() {
        // Linux double la valeur demandée : c'est ce double qui compte.
        assertTrue(2 * RetardEnvoi.TAMPON_SYSTEME_OCTETS <= 2.1 * seconde)
    }
}
