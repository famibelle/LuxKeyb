package com.example.kreyolkeyboard.carnet

import com.example.kreyolkeyboard.wordscramble.MiseEnPage
import org.junit.Assert.assertEquals
import org.junit.Test

/** La grille de Wuertmix pour un mot, un mot très court et une expression. */
class MiseEnPageTest {

    @Test
    fun unMotGardeSaGrilleDAvant() {
        assertEquals(setOf(0, 3), MiseEnPage.fixes("Haus"))
        assertEquals(listOf(0, 1, 2, 3) to 4, MiseEnPage.cases("Haus"))
        assertEquals(5, MiseEnPage.cases("Méindeg").second)
    }

    @Test
    fun unMotTropCourtNeDonneRienDAvance() {
        assertEquals(emptySet<Int>(), MiseEnPage.fixes("A"))
        assertEquals(emptySet<Int>(), MiseEnPage.fixes("Ee"))
    }

    @Test
    fun uneExpressionPoseUnMotParRangee() {
        // « Gudde Moien » : l'espace et les deux lettres du bout sont donnés.
        assertEquals(setOf(0, 5, 10), MiseEnPage.fixes("Gudde Moien"))
        assertEquals(listOf(0, 1, 2, 3, 4, 6, 7, 8, 9, 10) to 5, MiseEnPage.cases("Gudde Moien"))
        // « Wéi vill kascht dat? » : six colonnes, la plus longue ; les mots
        // courts finissent leur rangée par des blancs, le point d'interrogation est donné.
        val (cases, colonnes) = MiseEnPage.cases("Wéi vill kascht dat?")
        assertEquals(6, colonnes)
        assertEquals(24, cases.size)
        assertEquals(listOf(0, 1, 2, -1, -1, -1), cases.take(6))
        assertEquals(true, 19 in MiseEnPage.fixes("Wéi vill kascht dat?"))
    }
}
