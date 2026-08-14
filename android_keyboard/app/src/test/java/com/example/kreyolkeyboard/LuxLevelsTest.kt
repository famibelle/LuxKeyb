package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.gamification.LuxLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La logique de niveaux vit dans LuxLevels plutôt que dans SettingsActivity,
 * où elle était `private` et donc hors de portée du service de saisie.
 * Ces tests fixent l'échelle et les seuils : les faire bouger ferait changer
 * de niveau tous les utilisateurs du jour au lendemain, sans qu'ils aient
 * tapé un mot de plus.
 */
class LuxLevelsTest {

    /** Taille réelle du dictionnaire luxembourgeois embarqué au moment du test. */
    private val totalWords = 6342

    @Test
    fun `les huit niveaux sont dans l'ordre culturel attendu`() {
        assertEquals(8, LuxLevels.LEVELS.size)
        assertEquals(
            listOf(
                "Ufänker", "Klengen", "Fléisseg", "Geschéit",
                "Renert", "Roude Léiw", "Sproochenkënner", "Sproochenmeeschter"
            ),
            LuxLevels.LEVELS.map { it.name }
        )
    }

    @Test
    fun `les seuils reprennent les pourcentages d'origine`() {
        val thresholds = LuxLevels.thresholds(totalWords)

        assertEquals(0, thresholds[0])                        // Ufänker : départ
        assertEquals((totalWords * 0.015).toInt(), thresholds[1])
        assertEquals((totalWords * 0.05).toInt(), thresholds[2])
        assertEquals((totalWords * 0.12).toInt(), thresholds[3])
        assertEquals((totalWords * 0.25).toInt(), thresholds[4])
        assertEquals((totalWords * 0.45).toInt(), thresholds[5])
        assertEquals((totalWords * 0.70).toInt(), thresholds[6])
        // Sproochenmeeschter exige le dictionnaire entier, sans arrondi à la baisse
        assertEquals(totalWords, thresholds[7])
    }

    @Test
    fun `un dictionnaire vierge laisse au niveau Ufänker`() {
        assertEquals(0, LuxLevels.indexFor(0, totalWords))
        assertEquals("🌍 Ufänker", LuxLevels.labelFor(0, totalWords))
    }

    @Test
    fun `franchir un seuil fait changer de niveau, un mot avant non`() {
        val klengen = LuxLevels.thresholds(totalWords)[1]

        assertEquals(0, LuxLevels.indexFor(klengen - 1, totalWords))
        assertEquals(1, LuxLevels.indexFor(klengen, totalWords))
        assertEquals("🌱 Klengen", LuxLevels.labelFor(klengen, totalWords))
    }

    @Test
    fun `le dictionnaire entier donne Sproochenmeeschter`() {
        assertEquals(LuxLevels.MAX_INDEX, LuxLevels.indexFor(totalWords, totalWords))
        assertTrue(LuxLevels.labelFor(totalWords, totalWords).endsWith("Sproochenmeeschter"))
    }

    @Test
    fun `le niveau suivant annonce le bon nom et le bon reste`() {
        val klengen = LuxLevels.thresholds(totalWords)[1]
        val (nextName, remaining) = LuxLevels.nextLevelInfo(klengen - 10, totalWords)

        assertEquals("Klengen", nextName)
        assertEquals(10, remaining)
    }

    @Test
    fun `au niveau maximum il ne reste rien a decouvrir`() {
        val (nextName, remaining) = LuxLevels.nextLevelInfo(totalWords, totalWords)

        assertEquals("Sproochenmeeschter", nextName)
        assertEquals(0, remaining)
    }
}
