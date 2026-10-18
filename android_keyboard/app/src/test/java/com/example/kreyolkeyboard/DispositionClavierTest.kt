package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.KeyboardLayoutManager.ChampAdresse
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests des dispositions de la page des lettres (v29.5.0).
 *
 * Ce qu'ils couvrent : le contenu des rangées, l'alignement des largeurs et les
 * touches directes qui filtrent les aperçus en coin. Ce qu'ils ne peuvent pas
 * couvrir : le rendu des touches à onze par rangée, qui se vérifie sur appareil.
 */
class DispositionClavierTest {

    private val luxembourg = DispositionClavier.LUXEMBOURG
    private val suisse = DispositionClavier.SUISSE_ALLEMAND

    private fun largeur(disposition: DispositionClavier, rangee: Array<String>): Float =
        rangee.sumOf { disposition.poidsTouche(it).toDouble() }.toFloat()

    @Test
    fun `le defaut est le suisse allemand`() {
        assertEquals(suisse, DispositionClavier.DEFAUT)
        // Aucun choix enregistré : c'est le cas de toutes les installations
        // antérieures à la v29.5.0, qui passent donc au suisse allemand.
        assertEquals(suisse, DispositionClavier.depuisCle(null))
        assertEquals(suisse, DispositionClavier.depuisCle("azerty"))
    }

    @Test
    fun `le defaut est propose en premier dans les reglages`() {
        assertEquals(DispositionClavier.DEFAUT, DispositionClavier.entries.first())
    }

    @Test
    fun `un choix luxembourgeois enregistre est respecte`() {
        assertEquals(luxembourg, DispositionClavier.depuisCle("luxembourg"))
    }

    @Test
    fun `chaque disposition se relit a l'identique`() {
        DispositionClavier.entries.forEach {
            assertEquals(it, DispositionClavier.depuisCle(it.cle))
        }
    }

    @Test
    fun `la disposition luxembourgeoise est inchangee`() {
        val rangees = luxembourg.rangeesLettres(ChampAdresse.AUCUN)
        assertArrayEquals(arrayOf("q", "w", "e", "r", "t", "z", "u", "i", "o", "p"), rangees[0])
        assertArrayEquals(arrayOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "é"), rangees[1])
        assertArrayEquals(arrayOf("⇧", "y", "x", "c", "v", "b", "n", "m", "⌫"), rangees[2])
        assertArrayEquals(arrayOf("123", ",", "ä", " ", "ë", "'", "-", ".", "EMOJI", "⏎"), rangees[3])
    }

    @Test
    fun `le suisse allemand met u, o et a infléchis à droite comme le clavier physique`() {
        val rangees = suisse.rangeesLettres(ChampAdresse.AUCUN)
        assertEquals("ü", rangees[0].last())
        assertEquals(listOf("ö", "ä"), rangees[1].takeLast(2))
        assertEquals(11, rangees[0].size)
        assertEquals(11, rangees[1].size)
    }

    @Test
    fun `le suisse allemand garde é, ë et ä en touches directes`() {
        listOf("é", "ë", "ä", "ü", "ö").forEach {
            assertTrue("$it doit avoir sa touche", it in suisse.touchesDirectes)
        }
        listOf("é", "ë", "ä").forEach {
            assertTrue("$it doit avoir sa touche", it in luxembourg.touchesDirectes)
        }
        assertFalse("ü" in luxembourg.touchesDirectes)
    }

    @Test
    fun `aucune diacritique n'apparait deux fois`() {
        DispositionClavier.entries.forEach { disposition ->
            ChampAdresse.entries.forEach { champ ->
                val touches = disposition.rangeesLettres(champ).flatMap { it.asList() }
                assertEquals("$disposition, $champ", touches.distinct(), touches)
            }
        }
    }

    @Test
    fun `les trois rangees de lettres ont la meme largeur`() {
        DispositionClavier.entries.forEach { disposition ->
            val rangees = disposition.rangeesLettres(ChampAdresse.AUCUN)
            val reference = largeur(disposition, rangees[0])
            (1..2).forEach { i ->
                assertEquals("$disposition, rangée ${i + 1}", reference, largeur(disposition, rangees[i]), 0.001f)
            }
        }
    }

    @Test
    fun `la rangee du bas suit le champ d'adresse dans les deux dispositions`() {
        DispositionClavier.entries.forEach { disposition ->
            val email = disposition.rangeesLettres(ChampAdresse.EMAIL)[3]
            val web = disposition.rangeesLettres(ChampAdresse.WEB)[3]
            assertTrue("@" in email && ".lu" in email && "'" !in email)
            assertTrue("/" in web && ".lu" in web && "," !in web)
            assertEquals(10, email.size)
            assertEquals(10, web.size)
        }
    }

    @Test
    fun `les autres pages gardent la largeur luxembourgeoise de la touche effacement`() {
        // createKeyboardRow() prend ce poids par défaut pour les pages 123 et emoji.
        assertEquals(1.5f, DispositionClavier.poidsPagesSymboles("⌫"), 0f)
        assertEquals(luxembourg.poidsTouche("⌫"), DispositionClavier.poidsPagesSymboles("⌫"), 0f)
    }

    @Test
    fun `le trait d'union a sa touche sans rien coûter aux autres`() {
        DispositionClavier.entries.forEach { disposition ->
            assertTrue("$disposition", "-" in disposition.touchesDirectes)
            ChampAdresse.entries.forEach { champ ->
                val bas = disposition.rangeesLettres(champ)[3]
                assertEquals("$disposition, $champ", ".", bas[bas.indexOf("-") + 1])
                // La barre d'espace a cédé la largeur de la nouvelle touche :
                // la rangée garde ses 12 unités, et la ponctuation sa largeur.
                assertEquals("$disposition, $champ", 12f, largeur(disposition, bas), 0.001f)
            }
        }
        // Les pages 123 et emoji n'ont rien cédé.
        assertEquals(4f, DispositionClavier.poidsPagesSymboles(" "), 0f)
    }
}
