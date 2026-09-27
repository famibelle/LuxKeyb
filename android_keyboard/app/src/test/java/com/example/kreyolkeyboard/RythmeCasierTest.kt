package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.carnet.Widderhuelen
import com.example.kreyolkeyboard.carnet.rythmeCourt
import com.example.kreyolkeyboard.carnet.rythmeLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les légendes gravées sous les casiers de la boîte.
 *
 * Elles sont dérivées des intervalles, donc une suite retouchée les change sans
 * rien casser : ce test fige ce que lit le joueur, et qu'aucune ne soit trop
 * longue pour tenir sous une fente.
 */
class RythmeCasierTest {

    @Test
    fun lesIntervallesLivresSeDisentEnMotsCourts() {
        assertEquals(
            listOf("1 jour", "3 jours", "1 sem.", "2 sem.", "1 mois", "3 mois"),
            Widderhuelen.INTERVALLES.map { rythmeCourt(it) }
        )
    }

    @Test
    fun laFormeLongueDitLeMemeDelaiQueLaCourte() {
        assertEquals(
            listOf("1 jour", "3 jours", "1 semaine", "2 semaines", "1 mois", "3 mois"),
            Widderhuelen.INTERVALLES.map { rythmeLong(it) }
        )
    }

    @Test
    fun aucuneLegendeNeDepasseSeptSignes() {
        for (jours in 1..400) {
            assertTrue("$jours → ${rythmeCourt(jours)}", rythmeCourt(jours).length <= 7)
        }
    }
}
