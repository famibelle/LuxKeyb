package com.example.kreyolkeyboard

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Le doigt tombé sur la touche d'à côté passe devant la fréquence.
 *
 * Constaté sur l'émulateur le 2026-10-13 : « haud » proposait « Haut » avant
 * « Haus », alors que le s touche le d et que le t en est loin.
 */
class VoisinsClavierTest {

    private val suisse = VoisinsClavier.de(DispositionClavier.SUISSE_ALLEMAND)
    private val luxembourg = VoisinsClavier.de(DispositionClavier.LUXEMBOURG)

    @Test
    fun lesVoisinesSontCellesDeLEcran() {
        for (v in listOf(suisse, luxembourg)) {
            assertTrue(v.sontVoisines('d', 's'))
            assertTrue(v.sontVoisines('d', 'f'))
            assertTrue(v.sontVoisines('d', 'e'))      // rangée du dessus
            assertTrue(v.sontVoisines('n', 'm'))
            assertTrue(v.sontVoisines('s', 'd'))      // symétrique
            assertFalse(v.sontVoisines('d', 't'))
            assertFalse(v.sontVoisines('a', 'l'))
            assertFalse(v.sontVoisines('q', 'p'))
        }
    }

    @Test
    fun uneCorrectionParVoisinesSeReconnait() {
        assertTrue(suisse.parVoisines("haud", "Haus"))
        assertFalse(suisse.parVoisines("haud", "Haut"))
        assertFalse(suisse.parVoisines("haud", "haud"))       // rien à corriger
        assertFalse(suisse.parVoisines("komen", "Kommen"))    // longueurs différentes
        assertTrue(suisse.parVoisines("schiul", "Schoul"))     // i et o se touchent
        // Un accent seul ne compte ni pour ni contre.
        assertTrue(suisse.parVoisines("gwschter", "Gëschter"))
    }

    @Test
    fun leBonusRespecteLaDistanceEtPeseSurLaFrequence() {
        val t = JSONArray(File("src/main/assets/luxemburgish_dict.json").readText())
        val max = (0 until t.length()).maxOf { t.getJSONArray(it).getInt(1) }
        // Haus (772, voisine) devant haut (1 167) ; Noen (57, voisine) derrière
        // Moien (1 703, lettre oubliée).
        val haus = SuggestionEngine.calculateDictionaryScore("Haus", "haud", 772, 1, 0, facteurFrappe = SuggestionEngine.VOISINAGE_FACTEUR)
        val haut = SuggestionEngine.calculateDictionaryScore("haut", "haud", 1167, 1)
        assertTrue("$haus vs $haut", haus > haut)
        val noen = SuggestionEngine.calculateDictionaryScore("Noen", "moen", 57, 1, 0, facteurFrappe = SuggestionEngine.VOISINAGE_FACTEUR)
        val moien = SuggestionEngine.calculateDictionaryScore("Moien", "moen", 1703, 1)
        assertTrue("$moien vs $noen", moien > noen)
        // Deux fautes voisines, mot le plus fréquent, contre une faute
        // quelconque du mot le plus rare : la distance garde le dernier mot.
        val deuxVoisines = SuggestionEngine.calculateDictionaryScore("freq", "xxxx", max, 2, 1000, facteurFrappe = SuggestionEngine.VOISINAGE_FACTEUR)
        val uneFaute = SuggestionEngine.calculateDictionaryScore("rare", "xxxx", 3, 1)
        assertTrue("$uneFaute vs $deuxVoisines", uneFaute > deuxVoisines)
    }

    @Test
    fun lesCorrectionsVuesSurLEmulateur() {
        val t = JSONArray(File("src/main/assets/luxemburgish_dict.json").readText())
        val corpus = (0 until t.length()).map { t.getJSONArray(it).let { p -> p.getString(0) to p.getInt(1) } }
        val lod = JSONObject(File("src/main/assets/luxemburgish_lod_forms.json").readText()).getJSONArray("suggest")
        val dico = corpus + (0 until lod.length()).map { lod.getString(it) to 1 }
        val normalise = { s: String -> AccentTolerantMatcher.normalize(s) }
        val normalises = dico.map { normalise(it.first) }
        fun trouves(saisie: String) = LevenshteinDistance.findClosestMatchesNormalized(
            saisie, dico, normalises, normalise, 2, 5,
            multiplicateur = { if (suisse.parVoisines(saisie, it)) SuggestionEngine.VOISINAGE_FACTEUR else 1 })
        assertEquals("Haus", trouves("haud").first().first)
        assertEquals("Schoul", trouves("schiul").first().first)
        // La lettre oubliée garde sa place face aux voisines rares (Noen).
        assertTrue(trouves("moen").take(3).any { it.first == "Moien" })
    }
}
