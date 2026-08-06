package com.example.kreyolkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests du suivi du mot autour du curseur (chantier « suivi du curseur »).
 *
 * Jusqu'à la v10.3.2, le service IME actif ne surchargeait pas
 * onUpdateSelection() : le mot suivi par InputProcessor n'était alimenté que par
 * les frappes et divergeait du texte réel dès que le curseur bougeait autrement.
 * Revenir éditer un mot déjà écrit ne produisait alors plus aucune suggestion.
 */
class CursorWordTrackingTest {

    // ===== resolveCurrentWord =====

    @Test
    fun `le mot courant est la suite de lettres precedant le curseur`() {
        assertEquals("bonjou", InputProcessor.resolveCurrentWord("bonjou", false))
        assertEquals("bonjou", InputProcessor.resolveCurrentWord("An ka di bonjou", false))
    }

    @Test
    fun `un curseur pose apres un separateur ne designe aucun mot`() {
        assertEquals("", InputProcessor.resolveCurrentWord("bonjou ", false))
        assertEquals("", InputProcessor.resolveCurrentWord("bonjou.", false))
        assertEquals("", InputProcessor.resolveCurrentWord("", false))
    }

    @Test
    fun `le curseur pose au milieu d'un mot ne retient que la partie qui precede`() {
        // « bon|jou » : c'est bien "bon" qui sert de préfixe aux suggestions,
        // la fin du mot est retirée au moment de la sélection (voir plus bas)
        assertEquals("bon", InputProcessor.resolveCurrentWord("An ka di bon", false))
    }

    @Test
    fun `les lettres accentuees kreyol font partie du mot`() {
        assertEquals("kréyòl", InputProcessor.resolveCurrentWord("An ka palé kréyòl", false))
        assertEquals("mèsi", InputProcessor.resolveCurrentWord("mèsi", false))
    }

    @Test
    fun `une majuscule accentuee ne coupe pas le mot`() {
        // L'ancienne classe de caractères ne listait que les minuscules accentuées :
        // « É », fréquent en début de phrase kréyòl, était pris pour un séparateur
        assertEquals("Épi", InputProcessor.resolveCurrentWord("Épi", false))
        assertEquals("ÈVÈ", InputProcessor.resolveCurrentWord("ÈVÈ", false))
    }

    @Test
    fun `une selection active ne designe aucun mot en cours`() {
        // Remplacer une sélection par une suggestion effacerait un texte que
        // l'utilisateur a désigné explicitement
        assertEquals("", InputProcessor.resolveCurrentWord("bonjou", true))
    }

    @Test
    fun `un emoji avant le curseur ne fait pas partie du mot`() {
        assertEquals("", InputProcessor.resolveCurrentWord("bonjou 🥭", false))
    }

    // ===== trailingWordLength =====

    @Test
    fun `la fin du mot apres le curseur est mesuree jusqu'au premier separateur`() {
        assertEquals(3, InputProcessor.trailingWordLength("jou"))
        assertEquals(3, InputProcessor.trailingWordLength("jou an mwen"))
        assertEquals(0, InputProcessor.trailingWordLength(" an mwen"))
        assertEquals(0, InputProcessor.trailingWordLength(""))
    }

    @Test
    fun `la fin du mot compte aussi les lettres accentuees`() {
        assertEquals(4, InputProcessor.trailingWordLength("éyòl"))
    }

    // ===== isWordCharacter =====

    @Test
    fun `seules les lettres appartiennent a un mot`() {
        assertTrue(InputProcessor.isWordCharacter('a'))
        assertTrue(InputProcessor.isWordCharacter('ò'))
        assertTrue(InputProcessor.isWordCharacter('É'))
        assertFalse(InputProcessor.isWordCharacter(' '))
        assertFalse(InputProcessor.isWordCharacter('.'))
        assertFalse(InputProcessor.isWordCharacter('\''))
        assertFalse(InputProcessor.isWordCharacter('7'))
    }

    // ===== Scénarios de régression =====

    @Test
    fun `effacer l'espace apres un mot valide redonne ce mot comme prefixe`() {
        // Scénario cassé avant le correctif : après « bonjou » puis espace, le mot
        // courant était vidé ; le retour arrière supprimait l'espace sans jamais
        // rétablir « bonjou », donc plus aucune suggestion sur ce mot
        assertEquals("", InputProcessor.resolveCurrentWord("bonjou ", false))
        assertEquals("bonjou", InputProcessor.resolveCurrentWord("bonjou", false))
    }

    @Test
    fun `taper a la fin d'un mot existant en reprend le prefixe complet`() {
        // L'utilisateur tape dans le texte à la fin de « kréy » pour le compléter
        assertEquals("kréy", InputProcessor.resolveCurrentWord("An ka palé kréy", false))
    }
}
