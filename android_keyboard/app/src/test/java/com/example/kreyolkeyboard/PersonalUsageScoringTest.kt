package com.example.kreyolkeyboard

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests du bonus d'usage personnel dans le score des suggestions.
 *
 * Les compteurs alimentés par la gamification (CreoleDictionaryWithUsage) ne
 * servaient qu'aux écrans de statistiques : le classement se faisait sur la
 * seule fréquence du corpus, donc sur ce que le kréyòl écrit emploie en général
 * plutôt que sur ce que cet utilisateur-ci écrit.
 *
 * Les fréquences utilisées ici sont celles du dictionnaire réel.
 */
class PersonalUsageScoringTest {

    @Test
    fun `un mot jamais utilise garde exactement son ancien score`() {
        // Garantit qu'activer la fonctionnalité ne déplace rien pour un nouvel
        // utilisateur, dont tous les compteurs sont à zéro
        val sansUsage = SuggestionEngine.calculateDictionaryScore("bonjou", "bon", 164, 0)
        val avecUsageNul = SuggestionEngine.calculateDictionaryScore("bonjou", "bon", 164, 0, 0)

        assertEquals(sansUsage, avecUsageNul, 0.0)
    }

    @Test
    fun `l'usage personnel fait remonter un mot moins frequent dans le corpus`() {
        // "bon" (951) devance largement "bonjou" (164) dans le corpus ; un
        // utilisateur qui écrit constamment "bonjou" doit le voir passer devant
        val bon = SuggestionEngine.calculateDictionaryScore("bon", "bon", 951, 0, 0)
        val bonjouUtilise = SuggestionEngine.calculateDictionaryScore("bonjou", "bon", 164, 0, 20)

        assertTrue(
            "bonjou utilisé 20 fois devrait passer devant bon : $bonjouUtilise vs $bon",
            bonjouUtilise > bon
        )
    }

    @Test
    fun `quelques utilisations ne suffisent pas a bouleverser le classement`() {
        // Deux ou trois frappes ne doivent pas suffire à déloger un mot bien plus
        // fréquent : le signal personnel doit se confirmer avant de peser
        val bon = SuggestionEngine.calculateDictionaryScore("bon", "bon", 951, 0, 0)
        val bonjouPeuUtilise = SuggestionEngine.calculateDictionaryScore("bonjou", "bon", 164, 0, 3)

        assertTrue(bonjouPeuUtilise < bon)
    }

    @Test
    fun `a frequence egale le mot le plus utilise passe devant`() {
        val jamais = SuggestionEngine.calculateDictionaryScore("mandé", "man", 578, 0, 0)
        val souvent = SuggestionEngine.calculateDictionaryScore("manjé", "man", 578, 0, 8)

        assertTrue(souvent > jamais)
    }

    @Test
    fun `le bonus d'usage ne peut jamais devancer une correction orthographique`() {
        // Le poids d'une correction (100 000) doit rester hors d'atteinte : sinon un
        // mot très utilisé mais sans rapport supplanterait la correction attendue
        val correction = SuggestionEngine.calculateDictionaryScore("mèsi", "mesli", 650, 1, 0)
        val motTresUtilise = SuggestionEngine.calculateDictionaryScore("mésyé", "mesli", 15_000, 2, 10_000)

        assertTrue(
            "la correction à 1 édition doit rester devant : $correction vs $motTresUtilise",
            correction > motTresUtilise
        )
    }

    @Test
    fun `le bonus est plafonne pour ne pas deloger les mots hyper frequents`() {
        // "ka" (21806) est le mot le plus fréquent du kréyòl : aucun mot personnel
        // en "ka..." ne doit le faire descendre, quel que soit le nombre de frappes
        val ka = SuggestionEngine.calculateDictionaryScore("ka", "ka", 21_806, 0, 0)
        val kabritMartele = SuggestionEngine.calculateDictionaryScore("kabrit", "ka", 200, 0, 5_000)

        assertTrue(
            "ka doit rester devant malgré 5000 utilisations de kabrit : $ka vs $kabritMartele",
            ka > kabritMartele
        )
    }

    @Test
    fun `le bonus cesse de croitre au dela du plafond`() {
        val auPlafond = SuggestionEngine.calculateDictionaryScore("bonjou", "bon", 164, 0, 20)
        val bienAuDela = SuggestionEngine.calculateDictionaryScore("bonjou", "bon", 164, 0, 900)

        assertEquals(auPlafond, bienAuDela, 0.0)
    }

    @Test
    fun `le bonus croit avec le nombre d'utilisations sous le plafond`() {
        val peu = SuggestionEngine.calculateDictionaryScore("bonjou", "bon", 164, 0, 2)
        val moyen = SuggestionEngine.calculateDictionaryScore("bonjou", "bon", 164, 0, 10)
        val beaucoup = SuggestionEngine.calculateDictionaryScore("bonjou", "bon", 164, 0, 18)

        assertTrue(peu < moyen)
        assertTrue(moyen < beaucoup)
    }
}
