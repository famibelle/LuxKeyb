package com.example.kreyolkeyboard

import android.content.ContextWrapper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Une lettre doublée tapée une seule fois : « Kafi » pour « Kaffi ».
 *
 * Le luxembourgeois double ses voyelles longues (« Deel », « nees »,
 * « Kaart ») et beaucoup de consonnes (« konnt », « alles »). Pour la
 * distance d'édition c'est une lettre oubliée comme une autre ; le moteur la
 * rend plus probable, voir [SuggestionEngine.facteurDoublement].
 */
class LettreDoubleeTest {

    private val moteur: SuggestionEngine by lazy {
        SuggestionEngine(ContextWrapper(null)).also { m ->
            m.ouvrirActif = { File("src/main/assets", it).inputStream() }
            runBlocking { m.chargerLuxembourgeois() }
        }
    }

    @Test
    fun reconnaitLaLettreDoublee() {
        assertTrue(SuggestionEngine.parDoublement("Kafi", "Kaffi"))
        assertTrue(SuggestionEngine.parDoublement("kont", "konnt"))
        assertTrue(SuggestionEngine.parDoublement("acht", "aacht"))   // en tête de mot
        assertTrue(SuggestionEngine.parDoublement("wel", "well"))     // en fin de mot
        assertTrue(SuggestionEngine.parDoublement("kenen", "kënnen")) // accent replié
        assertFalse(SuggestionEngine.parDoublement("Kafi", "Kafi"))   // rien d'oublié
        assertFalse(SuggestionEngine.parDoublement("moen", "Moien"))  // lettre oubliée, pas doublée
        assertFalse(SuggestionEngine.parDoublement("kont", "konnten"))
    }

    @Test
    fun laLettreDoubleeSeCorrige() {
        assertEquals("Kaffi", moteur.rangeeLuxembourgeoise("Kafi").first())
        // Devant des complétions : « Energie », « Alessandra », « keng ».
        assertEquals("ënner", moteur.rangeeLuxembourgeoise("ener").first())
        assertEquals("alles", moteur.rangeeLuxembourgeoise("ales").first())
        assertEquals("kënne", moteur.rangeeLuxembourgeoise("kene").first())
        assertEquals("jidde", moteur.rangeeLuxembourgeoise("jide").first())
    }

    @Test
    fun unMotJusteNEstPasDouble() {
        // « den » est un mot : il reste, même si « deen » existe. Une forme
        // connue du LOD n'est jamais corrigée non plus (« nes », « kont »).
        assertEquals("den", moteur.rangeeLuxembourgeoise("den").first())
        assertEquals("Haus", moteur.rangeeLuxembourgeoise("Haus").first())
    }
}
