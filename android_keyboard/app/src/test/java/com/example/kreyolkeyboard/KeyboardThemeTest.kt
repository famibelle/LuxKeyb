package com.example.kreyolkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de non-régression du thème clair/sombre.
 *
 * Ce qu'ils couvrent : la table de décision de [KeyboardTheme.resoudre] et la
 * lecture de la préférence enregistrée. Ce qu'ils ne peuvent pas couvrir : les
 * couleurs elles-mêmes. `android.graphics.Color` n'est pas implémenté hors
 * appareil et `unitTests.returnDefaultValues` fait renvoyer 0 à `parseColor`,
 * donc les deux palettes sont ici indiscernables champ par champ. Elles restent
 * deux objets distincts, et les tests ci-dessous raisonnent uniquement sur leur
 * identité. Le rendu, lui, se vérifie sur appareil.
 */
class KeyboardThemeTest {

    private val clair = KeyboardTheme.Mode.CLAIR
    private val sombre = KeyboardTheme.Mode.SOMBRE
    private val systeme = KeyboardTheme.Mode.SYSTEME

    @Test
    fun `les deux themes sont bien deux palettes distinctes`() {
        assertNotSame(
            KeyboardTheme.resoudre(clair, systemeEnSombre = false),
            KeyboardTheme.resoudre(sombre, systemeEnSombre = false)
        )
    }

    @Test
    fun `le mode systeme suit le telephone`() {
        assertSame(
            KeyboardTheme.resoudre(sombre, systemeEnSombre = false),
            KeyboardTheme.resoudre(systeme, systemeEnSombre = true)
        )
        assertSame(
            KeyboardTheme.resoudre(clair, systemeEnSombre = false),
            KeyboardTheme.resoudre(systeme, systemeEnSombre = false)
        )
    }

    /**
     * Le cœur du réglage : les deux positions explicites doivent l'emporter sur
     * le mode nuit du téléphone, sans quoi elles ne serviraient à rien sur les
     * surcouches où celui-ci ne descend pas jusqu'aux claviers tiers.
     */
    @Test
    fun `un choix explicite l'emporte sur le mode nuit du telephone`() {
        assertSame(
            "« Toujours clair » doit rester clair même téléphone en sombre",
            KeyboardTheme.resoudre(clair, systemeEnSombre = false),
            KeyboardTheme.resoudre(clair, systemeEnSombre = true)
        )
        assertSame(
            "« Toujours sombre » doit rester sombre même téléphone en clair",
            KeyboardTheme.resoudre(sombre, systemeEnSombre = true),
            KeyboardTheme.resoudre(sombre, systemeEnSombre = false)
        )
    }

    @Test
    fun `une preference absente ou inconnue retombe sur le suivi du telephone`() {
        assertEquals(systeme, KeyboardTheme.Mode.depuisCle(null))
        assertEquals(systeme, KeyboardTheme.Mode.depuisCle(""))
        // Clé d'une version future, ou fichier de préférences recopié à la main :
        // le clavier doit démarrer, pas jeter.
        assertEquals(systeme, KeyboardTheme.Mode.depuisCle("auto"))
        assertEquals(systeme, KeyboardTheme.Mode.depuisCle("Clair"))
    }

    @Test
    fun `chaque mode se relit depuis la cle qu'il enregistre`() {
        KeyboardTheme.Mode.entries.forEach { mode ->
            assertEquals(mode, KeyboardTheme.Mode.depuisCle(mode.cle))
        }
    }

    /**
     * Les clés partent dans SharedPreferences et les libellés dans l'écran de
     * réglages : deux modes qui partageraient l'un ou l'autre rendraient le
     * groupe de boutons radio incohérent.
     */
    @Test
    fun `les cles et les libelles sont uniques et non vides`() {
        val modes = KeyboardTheme.Mode.entries
        assertEquals(3, modes.size)
        assertEquals(modes.size, modes.map { it.cle }.distinct().size)
        assertEquals(modes.size, modes.map { it.libelle }.distinct().size)
        assertTrue(modes.all { it.cle.isNotBlank() && it.libelle.isNotBlank() })
    }
}
