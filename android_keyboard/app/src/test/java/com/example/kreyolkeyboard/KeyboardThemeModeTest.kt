package com.example.kreyolkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La résolution du mode de thème depuis sa clé stockée.
 *
 * Seule cette partie de [KeyboardTheme] est vérifiable ici : les palettes passent
 * par `android.graphics.Color`, que `unitTests.returnDefaultValues` réduit à zéro
 * sur le classpath de test. Leurs contrastes sont mesurés et consignés dans la
 * documentation de la classe, pas dans une assertion.
 *
 * Ce qui est testé est ce qui casserait silencieusement : la clé écrite en
 * préférences est une chaîne, et une valeur qu'on ne sait plus lire ne doit pas
 * faire tomber le clavier au démarrage.
 */
class KeyboardThemeModeTest {

    @Test
    fun `chaque mode se relit depuis sa propre cle`() {
        KeyboardTheme.Mode.entries.forEach { mode ->
            assertEquals(mode, KeyboardTheme.Mode.depuisCle(mode.cle))
        }
    }

    @Test
    fun `une cle absente donne le suivi du systeme`() {
        // Le cas du premier lancement : rien n'a encore été écrit.
        assertEquals(KeyboardTheme.Mode.SYSTEME, KeyboardTheme.Mode.depuisCle(null))
    }

    @Test
    fun `une cle inconnue retombe sur le suivi du systeme`() {
        // Le cas d'une préférence écrite par une version ultérieure, puis relue
        // après un retour en arrière : mieux vaut un thème par défaut qu'un plantage.
        assertEquals(KeyboardTheme.Mode.SYSTEME, KeyboardTheme.Mode.depuisCle("sepia"))
        assertEquals(KeyboardTheme.Mode.SYSTEME, KeyboardTheme.Mode.depuisCle(""))
    }

    @Test
    fun `les cles sont distinctes et stables`() {
        val cles = KeyboardTheme.Mode.entries.map { it.cle }
        assertEquals(cles.size, cles.toSet().size)
        // Écrites en dur : ce sont les valeurs déjà stockées sur les appareils.
        // Les renommer réinitialiserait le choix de chaque utilisateur.
        assertEquals(listOf("systeme", "clair", "sombre"), cles)
    }

    @Test
    fun `chaque mode a un libelle affichable`() {
        KeyboardTheme.Mode.entries.forEach { mode ->
            assertTrue("libellé vide pour ${mode.cle}", mode.libelle.isNotBlank())
        }
    }
}
