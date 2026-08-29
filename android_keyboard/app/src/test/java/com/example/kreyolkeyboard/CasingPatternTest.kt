package com.example.kreyolkeyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de applyCasingPattern — reproduction du bug découvert lors de la
 * simulation « suggestions uniquement » du 2026-07-11 : un tap de suggestion
 * sous majuscule automatique (une seule lettre tapée, en majuscule) mettait
 * toute la suggestion en capitales ("B" → "BÈL" au lieu de "Bèl").
 */
class CasingPatternTest {

    @Test
    fun `une seule majuscule initiale donne une casse de titre, pas du tout-majuscules`() {
        assertEquals("Bonjou", SuggestionEngine.applyCasingPattern("B", "bonjou"))
        assertEquals("Bèl", SuggestionEngine.applyCasingPattern("B", "bèl"))
        assertEquals("An", SuggestionEngine.applyCasingPattern("A", "an"))
    }

    @Test
    fun `deux majuscules ou plus donnent du tout-majuscules`() {
        assertEquals("BONJOU", SuggestionEngine.applyCasingPattern("BO", "bonjou"))
        assertEquals("BONJOU", SuggestionEngine.applyCasingPattern("BONJ", "bonjou"))
        assertEquals("BÈL", SuggestionEngine.applyCasingPattern("BÈ", "bèl"))
    }

    @Test
    fun `premiere lettre majuscule suivie de minuscules donne une casse de titre`() {
        assertEquals("Bonjou", SuggestionEngine.applyCasingPattern("Bon", "bonjou"))
        assertEquals("Kréyòl", SuggestionEngine.applyCasingPattern("Kré", "kréyòl"))
    }

    @Test
    fun `tout minuscules reste minuscules`() {
        assertEquals("bonjou", SuggestionEngine.applyCasingPattern("bon", "bonjou"))
        assertEquals("bèl", SuggestionEngine.applyCasingPattern("b", "bèl"))
    }

    @Test
    fun `pattern mixte applique caractere par caractere`() {
        assertEquals("kaBrit", SuggestionEngine.applyCasingPattern("kaBr", "kabrit"))
    }

    @Test
    fun `input ou suggestion vide rend la suggestion inchangee`() {
        assertEquals("bonjou", SuggestionEngine.applyCasingPattern("", "bonjou"))
        assertEquals("", SuggestionEngine.applyCasingPattern("bon", ""))
    }

    @Test
    fun `apostrophe avec une seule majuscule reste en casse de titre`() {
        // "A'" : une lettre majuscule + ponctuation — ne doit pas passer en tout-majuscules
        assertEquals("An'w", SuggestionEngine.applyCasingPattern("A'", "an'w"))
    }

    // ===== Groussschreiwung : la casse du dictionnaire fait foi =====
    //
    // Le luxembourgeois capitalise tous les substantifs, et le dictionnaire
    // livre désormais la casse canonique de chaque forme. Une frappe en
    // minuscules n'est pas une demande de minuscules : c'est l'absence de
    // signal, et le mot doit arriver avec sa majuscule.

    @Test
    fun `une frappe en minuscules ne detruit pas la majuscule du substantif`() {
        assertEquals("Haus", SuggestionEngine.applyCasingPattern("hau", "Haus"))
        assertEquals("Joer", SuggestionEngine.applyCasingPattern("j", "Joer"))
        assertEquals("Lëtzebuerg", SuggestionEngine.applyCasingPattern("lëtze", "Lëtzebuerg"))
    }

    @Test
    fun `les acronymes gardent leurs capitales sous une frappe en minuscules`() {
        assertEquals("RTL", SuggestionEngine.applyCasingPattern("rt", "RTL"))
        assertEquals("CFL", SuggestionEngine.applyCasingPattern("cf", "CFL"))
    }

    @Test
    fun `une majuscule demandee par l'utilisateur reste respectee`() {
        // Le mot est déjà capitalisé : la demande ne change rien.
        assertEquals("Haus", SuggestionEngine.applyCasingPattern("Hau", "Haus"))
        // Le mot ne l'est pas : l'utilisateur l'emporte (début de phrase,
        // nom propre absent du corpus).
        assertEquals("An", SuggestionEngine.applyCasingPattern("A", "an"))
        assertEquals("Froen", SuggestionEngine.applyCasingPattern("Fro", "froen"))
    }

    @Test
    fun `le tout-majuscules l'emporte sur la casse du dictionnaire`() {
        assertEquals("HAUS", SuggestionEngine.applyCasingPattern("HAU", "Haus"))
        assertEquals("JOER", SuggestionEngine.applyCasingPattern("JO", "Joer"))
    }

    @Test
    fun `les mots outils ne recoivent aucune majuscule parasite`() {
        assertEquals("an", SuggestionEngine.applyCasingPattern("a", "an"))
        assertEquals("dass", SuggestionEngine.applyCasingPattern("da", "dass"))
        assertEquals("net", SuggestionEngine.applyCasingPattern("ne", "net"))
    }
}
