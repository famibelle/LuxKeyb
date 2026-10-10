package com.example.kreyolkeyboard

import org.junit.Assert.*
import org.junit.Test

/**
 * Les préférences du clavier avant le premier déverrouillage. Une erreur ici
 * ne se voit qu'avant le déverrouillage, sur l'écran de verrouillage, là où
 * personne ne pense à tester.
 */
class PreferencesEnMemoireTest {

    @Test
    fun videsElleRendentLesValeursParDefaut() {
        val p = PreferencesEnMemoire()
        assertEquals("défaut", p.getString("langue", "défaut"))
        assertTrue(p.getBoolean("majuscules", true))
        assertEquals(7, p.getInt("n", 7))
        assertEquals(7L, p.getLong("t", 7L))
        assertEquals(1.5f, p.getFloat("f", 1.5f), 0f)
        assertNull(p.getStringSet("s", null))
        assertFalse(p.contains("langue"))
    }

    @Test
    fun ceQuiEstEcritSeRelit() {
        val p = PreferencesEnMemoire()
        p.edit().putString("a", "x").putBoolean("b", false).putLong("c", 3L).apply()
        assertEquals("x", p.getString("a", null))
        assertFalse(p.getBoolean("b", true))
        assertEquals(3L, p.getLong("c", 0L))
        assertTrue(p.contains("a"))
    }

    @Test
    fun rienNEstVisibleAvantApply() {
        val p = PreferencesEnMemoire()
        val e = p.edit().putString("a", "x")
        assertFalse(p.contains("a"))
        e.apply()
        assertTrue(p.contains("a"))
    }

    @Test
    fun unTypeInattenduRendLaValeurParDefaut() {
        // Android lèverait ClassCastException ; ici le clavier doit continuer.
        val p = PreferencesEnMemoire()
        p.edit().putString("n", "pas un nombre").apply()
        assertEquals(4, p.getInt("n", 4))
    }

    @Test
    fun removeNullEtClearRetirent() {
        val p = PreferencesEnMemoire()
        p.edit().putString("a", "x").putString("b", "y").putString("c", "z").apply()
        p.edit().remove("a").putString("b", null).apply()
        assertFalse(p.contains("a"))
        assertFalse(p.contains("b"))
        assertTrue(p.contains("c"))
        p.edit().clear().putString("d", "w").apply()
        assertEquals(setOf("d"), p.all.keys)
    }

    @Test
    fun unEnsembleEcritNeChangePasApresCoup() {
        val p = PreferencesEnMemoire()
        val ensemble = mutableSetOf("😀")
        p.edit().putStringSet("recents", ensemble).apply()
        ensemble.add("🎉")
        assertEquals(setOf("😀"), p.getStringSet("recents", null))
    }

    @Test
    fun lesEcouteursSontPrevenus() {
        val p = PreferencesEnMemoire()
        val vues = mutableListOf<String?>()
        val ecouteur = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, cle ->
            vues.add(cle)
        }
        p.registerOnSharedPreferenceChangeListener(ecouteur)
        p.edit().putInt("x", 1).commit()
        p.unregisterOnSharedPreferenceChangeListener(ecouteur)
        p.edit().putInt("y", 2).commit()
        assertEquals(listOf("x"), vues)
    }
}
