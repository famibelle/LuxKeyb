package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.carnet.UniteRythme
import com.example.kreyolkeyboard.carnet.Widderhuelen
import com.example.kreyolkeyboard.carnet.arrondiRythme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les légendes gravées sous les casiers de la boîte.
 *
 * Elles sont dérivées des intervalles, donc une suite retouchée les change sans
 * rien casser : ce test fige l'arrondi que lit le joueur. Les mots eux-mêmes
 * (« 1 sem. », « 3 mois ») vivent dans les ressources, une par langue, et
 * leur longueur sous une fente est vérifiée par TraductionsTest.
 */
class RythmeCasierTest {

    @Test
    fun lesIntervallesLivresSArrondissentALaBonneUnite() {
        assertEquals(
            listOf(
                1 to UniteRythme.JOUR, 3 to UniteRythme.JOUR,
                1 to UniteRythme.SEMAINE, 2 to UniteRythme.SEMAINE,
                1 to UniteRythme.MOIS, 3 to UniteRythme.MOIS
            ),
            Widderhuelen.INTERVALLES.map { arrondiRythme(it) }
        )
    }

    @Test
    fun aucunArrondiNeDepasseDeuxChiffres() {
        for (jours in 1..400) {
            assertTrue("$jours → ${arrondiRythme(jours)}", arrondiRythme(jours).first in 1..99)
        }
    }
}
