package com.example.kreyolkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le clavier scindé d'une tablette couchée : où tombe la coupure, et ce que
 * devient la barre d'espace.
 */
class ClavierScindeTest {

    private val disposition = DispositionClavier.SUISSE_ALLEMAND
    private val poids = disposition::poidsTouche

    private fun moities(rangee: Array<String>): Pair<List<String>, List<String>> {
        val coupee = KeyboardLayoutManager.scinder(rangee, poids)
        val vide = coupee.indexOfFirst { it.first == null }
        return coupee.take(vide).map { it.first!! } to coupee.drop(vide + 1).map { it.first!! }
    }

    @Test
    fun chaqueRangeeALettresNaQuUnVide() {
        disposition.rangeesLettres(KeyboardLayoutManager.ChampAdresse.AUCUN).forEach { rangee ->
            val coupee = KeyboardLayoutManager.scinder(rangee, poids)
            assertEquals(rangee.joinToString(), 1, coupee.count { it.first == null })
        }
    }

    @Test
    fun laPremiereRangeeSeCoupeEntreZEtU() {
        val (gauche, droite) = moities(arrayOf("q", "w", "e", "r", "t", "z", "u", "i", "o", "p", "ü"))
        assertEquals(listOf("q", "w", "e", "r", "t", "z"), gauche)
        assertEquals(listOf("u", "i", "o", "p", "ü"), droite)
    }

    @Test
    fun laBarreDEspaceEstDedoubleeEtGardeSaLargeur() {
        val rangee = disposition.rangeesLettres(KeyboardLayoutManager.ChampAdresse.AUCUN).last()
        val coupee = KeyboardLayoutManager.scinder(rangee, poids)
        val espaces = coupee.filter { it.first == " " }
        assertEquals(2, espaces.size)
        assertEquals(poids(" "), espaces.sumOf { it.second.toDouble() }.toFloat(), 0.001f)
        val vide = coupee.indexOfFirst { it.first == null }
        assertEquals(" ", coupee[vide - 1].first)
        assertEquals(" ", coupee[vide + 1].first)
    }

    @Test
    fun aucuneToucheNePerdNiNeGagneDeLaLargeur() {
        disposition.rangeesLettres(KeyboardLayoutManager.ChampAdresse.AUCUN).forEach { rangee ->
            val coupee = KeyboardLayoutManager.scinder(rangee, poids)
            val touches = coupee.filter { it.first != null }.sumOf { it.second.toDouble() }
            assertEquals(rangee.sumOf { poids(it).toDouble() }, touches, 0.001)
            val vide = coupee.first { it.first == null }.second
            val part = vide / (touches + vide)
            assertTrue(Math.abs(part - KeyboardLayoutManager.PART_VIDE_SCINDE) < 0.001)
        }
    }
}
