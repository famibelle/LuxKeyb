package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.stt.RetardEnvoi
import com.example.kreyolkeyboard.stt.RetardEnvoi.Verdict
import org.junit.Assert.assertEquals
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
    fun sixSecondesEnSouffranceArretentLaDictee() {
        assertEquals(Verdict.LENT, RetardEnvoi.juger(6 * seconde - 1, etaitLent = true))
        assertEquals(Verdict.ABANDON, RetardEnvoi.juger(6 * seconde, etaitLent = true))
        assertEquals(Verdict.ABANDON, RetardEnvoi.juger(6 * seconde, etaitLent = false))
    }

    @Test
    fun leTamponSystemeNeMasquePasPlusDeDeuxSecondes() {
        // Linux double la valeur demandée : c'est ce double qui compte.
        assertTrue(2 * RetardEnvoi.TAMPON_SYSTEME_OCTETS <= 2.1 * seconde)
    }
}
