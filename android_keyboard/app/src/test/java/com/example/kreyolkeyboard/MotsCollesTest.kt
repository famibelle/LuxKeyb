package com.example.kreyolkeyboard

import android.content.ContextWrapper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Deux mots tapés sans espace se recollent ; un mot juste inconnu, non.
 *
 * Le banc de correction donnait 0 % sur les mots collés jusqu'au
 * 2026-10-13. Les cas « ne pas couper » sont ceux que le banc a relevés :
 * une terminaison prise pour un mot, une particule de verbe détachée.
 */
class MotsCollesTest {

    private val moteur: SuggestionEngine by lazy {
        SuggestionEngine(ContextWrapper(null)).also { m ->
            m.ouvrirActif = { File("src/main/assets", it).inputStream() }
            runBlocking { m.chargerLuxembourgeois() }
        }
    }

    @Test
    fun deuxMotsCollesSeSeparent() {
        assertEquals("ech hunn", moteur.rangeeLuxembourgeoise("echhunn").first())
        assertEquals("ech war", moteur.rangeeLuxembourgeoise("echwar").first())
        assertEquals("och gëschter", moteur.rangeeLuxembourgeoise("ochgëschter").first())
    }

    @Test
    fun unMotJusteInconnuNeSeCoupePas() {
        for (mot in listOf("honorabelen", "virleienden", "ausbruecht", "opkommend", "iwwerreguléiert")) {
            val barre = moteur.rangeeLuxembourgeoise(mot)
            assertTrue("$mot : $barre", barre.none { ' ' in it })
        }
    }
}
