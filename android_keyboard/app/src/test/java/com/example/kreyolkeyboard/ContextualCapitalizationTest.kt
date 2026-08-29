package com.example.kreyolkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Correction de la Groussschreiwung à la frappe, décidée par le contexte.
 *
 * Le luxembourgeois capitalise tous ses substantifs, et quelqu'un qui tape vite
 * ne le fait pas. Capitaliser d'office tout mot dont le dictionnaire connaît la
 * forme majuscule serait pourtant intenable : mesuré sur ParaLux, cela abîmerait
 * 3,5 % des phrases correctement écrites, et surtout capitaliserait 161 des
 * 662 mots du dictionnaire français de secours — « rue », « moment »,
 * « centre », « chambre », « santé », « café »… Au Luxembourg, un message écrit
 * en français y perdrait un mot sur quatre.
 *
 * D'où la règle : **on ne capitalise que ce que le corpus a vu capitalisé après
 * le mot précédent**. Le contexte écarte le français tout seul — dans « la rue
 * de la gare », `la` n'est pas un contexte luxembourgeois connu, donc rien ne se
 * déclenche.
 *
 * Mesuré ainsi sur ParaLux, en simulant quelqu'un qui tape tout en minuscules :
 * 126 majuscules rétablies, **zéro imposée à tort**, 818 manquées. Prudent, donc,
 * et c'est le but : une majuscule manquée ne coûte presque rien, une majuscule
 * imposée à tort oblige à revenir en arrière.
 */
class ContextualCapitalizationTest {

    /** Ce que le contexte « an der » propose réellement dans le modèle livré. */
    private val apresAnDer = listOf("Rue", "Stad", "Nuecht", "Chamber", "Belsch")

    @Test
    fun `le contexte impose la majuscule qu'il atteste`() {
        assertEquals("Rue", SuggestionEngine.pickContextualCapitalization("rue", apresAnDer))
        assertEquals("Stad", SuggestionEngine.pickContextualCapitalization("stad", apresAnDer))
    }

    @Test
    fun `un mot que le contexte ne connait pas n'est pas touche`() {
        assertNull(SuggestionEngine.pickContextualCapitalization("haus", apresAnDer))
    }

    @Test
    fun `un mot que le contexte donne en minuscules n'est pas touche`() {
        // « als nei gréng » : le contexte atteste la couleur, pas le parti.
        val apresAlsNei = listOf("gréng", "Auto", "Regierung")
        assertNull(SuggestionEngine.pickContextualCapitalization("gréng", apresAlsNei))
    }

    @Test
    fun `la casse voulue par l'utilisateur est intouchable`() {
        // Une seule majuscule suffit à faire taire la correction : ce que
        // l'utilisateur a tapé lui appartient.
        assertNull(SuggestionEngine.pickContextualCapitalization("Rue", apresAnDer))
        assertNull(SuggestionEngine.pickContextualCapitalization("RUE", apresAnDer))
        assertNull(SuggestionEngine.pickContextualCapitalization("rUe", apresAnDer))
    }

    @Test
    fun `les mots d'une lettre sont ignores`() {
        assertNull(SuggestionEngine.pickContextualCapitalization("a", listOf("A", "an")))
    }

    @Test
    fun `un acronyme est retabli comme le corpus l'atteste`() {
        assertEquals("RTL", SuggestionEngine.pickContextualCapitalization("rtl", listOf("RTL", "de")))
    }

    @Test
    fun `sans candidat aucune correction`() {
        assertNull(SuggestionEngine.pickContextualCapitalization("rue", emptyList()))
    }

    /**
     * C'est la forme la PLUS PROBABLE du contexte qui décide, pas la première
     * majuscule rencontrée.
     *
     * Les deux orthographes d'un homographe cohabitent dans le modèle depuis
     * que le contexte choisit la casse. Après « oppe », `Froen` arrive en tête
     * et la correction s'applique ; après « mee mir », c'est `froen`, et il n'y
     * a rien à corriger — aller chercher plus loin la variante capitalisée
     * imposerait une majuscule que le contexte ne demande pas.
     */
    @Test
    fun `la forme la plus probable du contexte decide`() {
        assertEquals("Froen",
            SuggestionEngine.pickContextualCapitalization("froen", listOf("Froen", "froen")))
        assertNull(
            SuggestionEngine.pickContextualCapitalization("froen", listOf("froen", "Froen")))
    }
}
