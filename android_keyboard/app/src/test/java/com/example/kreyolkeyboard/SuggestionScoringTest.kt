package com.example.kreyolkeyboard

import org.junit.Test
import org.junit.Assert.*
import org.json.JSONArray
import java.io.File

/**
 * Tests du score de pertinence des suggestions, en particulier la propagation
 * de la distance de Levenshtein : une correction à 1 édition doit toujours
 * battre une correction à 2 éditions, quelle que soit la fréquence.
 */
class SuggestionScoringTest {

    /**
     * Le classement par distance ne doit pas dépendre de l'échelle de
     * fréquences du corpus du moment.
     *
     * calculateDictionaryScore() additionne la fréquence et un poids fonction
     * de la distance : la distance ne prime que tant que l'écart entre deux
     * distances dépasse la plus haute fréquence livrée. Les autres tests de ce
     * fichier utilisent des fréquences créoles (15 000 max) et resteraient
     * verts après un changement de corpus qui casserait l'invariant — c'est
     * exactement ce qui est arrivé en passant à LuxAlign, où « an » atteint
     * 100 105 alors que le poids valait 100 000.
     *
     * Ce test lit donc le dictionnaire réellement livré et confronte ses deux
     * extrêmes : le mot le plus fréquent à 2 éditions ne doit jamais passer
     * devant le mot le plus rare à 1 édition.
     */
    @Test
    fun testDistanceBeatsFrequencyAtShippedDictionaryScale() {
        val asset = File("src/main/assets/luxemburgish_dict.json")
        assertTrue(
            "Dictionnaire introuvable : ${asset.absolutePath}",
            asset.exists()
        )

        val entries = JSONArray(asset.readText())
        assertTrue("Dictionnaire vide", entries.length() > 0)

        var frequenceMax = 0
        var frequenceMin = Int.MAX_VALUE
        for (i in 0 until entries.length()) {
            val frequence = entries.getJSONArray(i).getInt(1)
            if (frequence > frequenceMax) frequenceMax = frequence
            if (frequence < frequenceMin) frequenceMin = frequence
        }

        val correctionProche =
            SuggestionEngine.calculateDictionaryScore("rare", "raree", frequenceMin, 1)
        val correctionLointaine =
            SuggestionEngine.calculateDictionaryScore("frequent", "raree", frequenceMax, 2)

        assertTrue(
            "Une correction à 1 édition du mot le plus rare (f=$frequenceMin) doit " +
                "battre une correction à 2 éditions du mot le plus fréquent " +
                "(f=$frequenceMax) : $correctionProche vs $correctionLointaine. " +
                "Relever EDIT_DISTANCE_WEIGHT au-dessus de $frequenceMax.",
            correctionProche > correctionLointaine
        )
    }

    @Test
    fun testCorrectionCloserDistanceBeatsHigherFrequency() {
        // Cas réel observé : "mesli" → "mèsi" (distance 1, fréquence modeste)
        // doit battre "mésyé" (distance 2, fréquence plus élevée)
        val scoreMesi = SuggestionEngine.calculateDictionaryScore("mèsi", "mesli", 650, 1)
        val scoreMesye = SuggestionEngine.calculateDictionaryScore("mésyé", "mesli", 15_000, 2)

        assertTrue(
            "mèsi (d=1, f=650) devrait battre mésyé (d=2, f=15000) : $scoreMesi vs $scoreMesye",
            scoreMesi > scoreMesye
        )
    }

    @Test
    fun testSameDistanceRankedByFrequency() {
        // À distance égale, la fréquence départage
        val scoreFrequent = SuggestionEngine.calculateDictionaryScore("mès", "mesli", 900, 2)
        val scoreRare = SuggestionEngine.calculateDictionaryScore("mélé", "mesli", 100, 2)

        assertTrue(scoreFrequent > scoreRare)
    }

    @Test
    fun testPrefixMatchesRankedByFrequency() {
        // Correspondances par préfixe (distance 0) : la fréquence domine
        val scoreKa = SuggestionEngine.calculateDictionaryScore("ka", "k", 15_519, 0)
        val scoreKijan = SuggestionEngine.calculateDictionaryScore("kijan", "k", 500, 0)

        assertTrue(scoreKa > scoreKijan)
    }

    @Test
    fun testAccentedWordGetsPrefixBonus() {
        // "fe" tapé : "fè" doit recevoir le bonus préfixe malgré l'accent,
        // et donc battre un mot sans accent nettement moins fréquent
        val scoreFe = SuggestionEngine.calculateDictionaryScore("fè", "fe", 3274, 0)
        val scoreFenmen = SuggestionEngine.calculateDictionaryScore("fenmen", "fe", 100, 0)

        assertTrue(scoreFe > scoreFenmen)
    }

    /**
     * Le bonus de contexte doit peser plus que l'écart de fréquence du
     * dictionnaire livré, sinon il ne réordonne rien.
     *
     * Même piège qu'au-dessus, sur une autre constante. Le bonus valait 50
     * face à des fréquences montant à 100 105 : mesuré sur ParaLux, le passer
     * de 50 à 0 ne changeait pratiquement rien (31,40 % → 31,11 % de bons mots
     * dans les trois premiers après deux frappes), preuve qu'il ne servait à
     * rien. Ce test l'ancre sur l'asset réel, pour qu'un futur changement de
     * corpus qui repousse la fréquence maximale échoue ici plutôt qu'en
     * silence.
     */
    @Test
    fun testNgramContextOutweighsFrequencyAtShippedDictionaryScale() {
        val asset = File("src/main/assets/luxemburgish_dict.json")
        assertTrue("Dictionnaire introuvable : ${asset.absolutePath}", asset.exists())

        val entries = JSONArray(asset.readText())
        var frequenceMax = 0
        for (i in 0 until entries.length()) {
            val frequence = entries.getJSONArray(i).getInt(1)
            if (frequence > frequenceMax) frequenceMax = frequence
        }

        // Le mot le plus rare, attendu par le contexte, contre le mot le plus
        // fréquent du lexique, que le contexte n'attend pas.
        val attenduParLeContexte =
            SuggestionEngine.calculateDictionaryScore("rar", "ra", 1) +
                SuggestionEngine.NGRAM_CONTEXT_WEIGHT
        val simplementFrequent =
            SuggestionEngine.calculateDictionaryScore("frequent", "ra", frequenceMax)

        assertTrue(
            "Un mot attendu par le contexte doit passer devant un mot seulement " +
                "fréquent (f=$frequenceMax) : $attenduParLeContexte vs " +
                "$simplementFrequent. Relever NGRAM_CONTEXT_WEIGHT.",
            attenduParLeContexte > simplementFrequent
        )
    }
}
