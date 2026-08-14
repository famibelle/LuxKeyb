package com.example.kreyolkeyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la décision de soulignement du correcteur orthographique système.
 *
 * Le service ne déclarait qu'un sous-type de locale "ht", que ne porte jamais un
 * téléphone en Guadeloupe : le système ne créait donc aucune session et le
 * service n'était jamais appelé, laissant le correcteur de Google souligner en
 * rouge tous les mots kréyòl. Il déclare maintenant "fr", ce qui le rend
 * responsable de l'ensemble du texte français, d'où la réserve testée ici.
 */
class SpellCheckerFlaggingTest {

    @Test
    fun `un mot connu n'est jamais souligne`() {
        assertFalse(KreyolSpellCheckerService.shouldFlagAsTypo(isKnown = true, corrections = emptyList()))
    }

    @Test
    fun `une faute de frappe kreyol est signalee avec ses corrections`() {
        // "bonjuo" est inconnu mais "bonjou" est à une édition : c'est le cas que le
        // correcteur doit attraper
        assertTrue(
            KreyolSpellCheckerService.shouldFlagAsTypo(
                isKnown = false,
                corrections = listOf("bonjou")
            )
        )
    }

    @Test
    fun `un mot inconnu sans correction plausible n'est pas souligne`() {
        // Un mot français absent de notre dictionnaire n'a aucun voisin kréyòl
        // proche. Le souligner reviendrait à marquer en rouge la moitié française
        // d'un message bilingue, ce qui serait pire que pas de correcteur du tout.
        assertFalse(KreyolSpellCheckerService.shouldFlagAsTypo(isKnown = false, corrections = emptyList()))
    }

    @Test
    fun `un nom propre inconnu n'est pas souligne`() {
        // Même raisonnement : sans voisin proche, on s'abstient plutôt que d'accuser
        assertFalse(KreyolSpellCheckerService.shouldFlagAsTypo(isKnown = false, corrections = emptyList()))
    }

    @Test
    fun `la reserve ne s'applique pas au detriment d'une vraie faute`() {
        // Plusieurs corrections possibles : le mot reste signalé
        assertTrue(
            KreyolSpellCheckerService.shouldFlagAsTypo(
                isKnown = false,
                corrections = listOf("mèsi", "mésyé", "mès")
            )
        )
    }
}
