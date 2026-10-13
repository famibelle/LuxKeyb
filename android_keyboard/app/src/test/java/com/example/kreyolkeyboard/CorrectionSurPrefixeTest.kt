package com.example.kreyolkeyboard

import android.content.ContextWrapper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Une faute qui est aussi le début d'un autre mot se corrige quand même.
 *
 * Avant le 2026-10-13, la correction ne se cherchait que si rien ne
 * commençait par ce qui était tapé : « gir » (pour « fir », le g touche le
 * f) ne proposait que des formes rares en « gir… ». Le seuil est réglé sur
 * le banc de correction, voir [SuggestionEngine.ratioCorrectionSurPrefixe].
 */
class CorrectionSurPrefixeTest {

    private val moteur: SuggestionEngine by lazy {
        SuggestionEngine(ContextWrapper(null)).also { m ->
            m.ouvrirActif = { File("src/main/assets", it).inputStream() }
            runBlocking { m.chargerLuxembourgeois() }
        }
    }

    @Test
    fun uneFauteQuiCommenceUnMotSeCorrige() {
        assertEquals("vun", moteur.rangeeLuxembourgeoise("vul").first())
        assertEquals("fir", moteur.rangeeLuxembourgeoise("gir").first())
    }

    @Test
    fun unMotJusteResteEnTete() {
        assertEquals("Haus", moteur.rangeeLuxembourgeoise("Haus").first())
        assertEquals("Regierung", moteur.rangeeLuxembourgeoise("Regierun").first())
    }

    @Test
    fun uneCorrectionTropRareNeChassePasLesCompletions() {
        // « muer » (152) ne pèse pas 64 fois « Marc » (1 248) : « mar »
        // reste une complétion. C'est le prix du seuil, mesuré.
        assertEquals("Marc", moteur.rangeeLuxembourgeoise("Mar").first())
    }
}
