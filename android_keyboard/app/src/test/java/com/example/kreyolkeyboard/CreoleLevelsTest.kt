package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.gamification.CreoleLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La logique de niveaux a été extraite de SettingsActivity (où elle était
 * `private`, donc hors de portée du service de saisie) vers CreoleLevels.
 * Ces tests fixent le comportement d'origine : les seuils, les noms et les
 * paliers doivent rester identiques à ceux affichés jusqu'ici aux
 * utilisateurs, sans quoi tout le monde changerait de niveau du jour au
 * lendemain sans avoir tapé un mot.
 */
class CreoleLevelsTest {

    /** Taille réelle du dictionnaire créole embarqué au moment du test. */
    private val totalWords = 5296

    @Test
    fun `les huit niveaux sont dans l'ordre culturel attendu`() {
        assertEquals(8, CreoleLevels.LEVELS.size)
        assertEquals(
            listOf(
                "Pipirit", "Ti moun", "Débrouya", "An mitan",
                "Kompè Lapen", "Kompè Zamba", "Potomitan", "Benzo"
            ),
            CreoleLevels.LEVELS.map { it.name }
        )
    }

    @Test
    fun `les seuils reprennent les pourcentages d'origine`() {
        val thresholds = CreoleLevels.thresholds(totalWords)

        assertEquals(0, thresholds[0])                        // Pipirit : départ
        assertEquals((totalWords * 0.015).toInt(), thresholds[1])
        assertEquals((totalWords * 0.05).toInt(), thresholds[2])
        assertEquals((totalWords * 0.12).toInt(), thresholds[3])
        assertEquals((totalWords * 0.25).toInt(), thresholds[4])
        assertEquals((totalWords * 0.45).toInt(), thresholds[5])
        assertEquals((totalWords * 0.70).toInt(), thresholds[6])
        // Benzo exige le dictionnaire entier, sans arrondi à la baisse
        assertEquals(totalWords, thresholds[7])
    }

    @Test
    fun `un dictionnaire vierge laisse au niveau Pipirit`() {
        assertEquals(0, CreoleLevels.indexFor(0, totalWords))
        assertEquals("🌍 Pipirit", CreoleLevels.labelFor(0, totalWords))
    }

    @Test
    fun `franchir un seuil fait changer de niveau, un mot avant non`() {
        val tiMoun = CreoleLevels.thresholds(totalWords)[1]

        assertEquals(0, CreoleLevels.indexFor(tiMoun - 1, totalWords))
        assertEquals(1, CreoleLevels.indexFor(tiMoun, totalWords))
        assertEquals("🌱 Ti moun", CreoleLevels.labelFor(tiMoun, totalWords))
    }

    @Test
    fun `le dictionnaire entier donne Benzo`() {
        assertEquals(CreoleLevels.MAX_INDEX, CreoleLevels.indexFor(totalWords, totalWords))
        assertTrue(CreoleLevels.labelFor(totalWords, totalWords).endsWith("Benzo"))
    }

    @Test
    fun `le niveau suivant annonce le bon nom et le bon reste`() {
        val tiMoun = CreoleLevels.thresholds(totalWords)[1]
        val (nextName, remaining) = CreoleLevels.nextLevelInfo(tiMoun - 10, totalWords)

        assertEquals("Ti moun", nextName)
        assertEquals(10, remaining)
    }

    @Test
    fun `au niveau maximum il ne reste rien a decouvrir`() {
        val (nextName, remaining) = CreoleLevels.nextLevelInfo(totalWords, totalWords)

        assertEquals("Benzo", nextName)
        assertEquals(0, remaining)
    }
}
