package com.example.kreyolkeyboard

import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Garde de performance du repli de correction orthographique.
 *
 * Ce chemin se déclenche dès qu'aucune forme luxembourgeoise ne commence par
 * ce qui est tapé — un mot allemand, un nom propre, un composé, une vraie
 * faute. Mesuré sur un Galaxy A21s, il coûtait **670 à 1 180 ms** par frappe
 * là où une frappe trouvant un préfixe en prend 46 à 93.
 *
 * Le test ne prétend pas reproduire ces durées : une JVM de bureau est bien
 * plus rapide qu'un téléphone d'entrée de gamme. Il verrouille un **ordre de
 * grandeur** sur le dictionnaire réellement livré, ce qui suffit à rattraper
 * une régression d'un facteur dix — le genre qu'introduit une normalisation
 * remise avant le filtre de longueur, ou une matrice réallouée par mot.
 */
class LevenshteinPerformanceTest {

    private fun dictionnaire(): List<Pair<String, Int>> {
        val fichier = File("src/main/assets/luxemburgish_dict.json")
        assertTrue("luxemburgish_dict.json manquant", fichier.exists())
        val tableau = JSONArray(fichier.readText())
        return (0 until tableau.length()).map {
            val paire = tableau.getJSONArray(it)
            paire.getString(0) to paire.getInt(1)
        }
    }

    /** Des mots qu'aucun préfixe luxembourgeois ne couvre : le cas du repli. */
    private val inconnus = listOf(
        "resilienzstrategie", "miteinander", "unserer", "deshalb",
        "kryptowaerung", "voulais", "processus", "abrogeassions"
    )

    /**
     * L'implémentation d'avant le 2026-09-04, reproduite ici pour que la
     * comparaison se fasse dans la même JVM, sur le même dictionnaire, au même
     * moment : une matrice complète par mot comparé, la casse repliée à chaque
     * cellule, et surtout `normalizer(word)` appelé sur **chaque** forme avant
     * le filtre de longueur.
     */
    private fun repliNaif(
        input: String,
        dico: List<Pair<String, Int>>,
        normalise: (String) -> String,
        maxDistance: Int = 2
    ): Int {
        val entree = normalise(input)
        var trouves = 0
        for ((mot, _) in dico) {
            val normalise2 = normalise(mot)
            if (kotlin.math.abs(normalise2.length - entree.length) > 2) continue
            val l1 = entree.length
            val l2 = normalise2.length
            val dp = Array(l1 + 1) { IntArray(l2 + 1) }
            for (i in 0..l1) dp[i][0] = i
            for (j in 0..l2) dp[0][j] = j
            for (i in 1..l1) for (j in 1..l2) {
                val cout = if (entree[i - 1].lowercaseChar() == normalise2[j - 1].lowercaseChar()) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cout)
            }
            if (dp[l1][l2] <= maxDistance) trouves++
        }
        return trouves
    }

    @Test
    fun leRepliEstPlusRapideQuAvant() {
        val dico = dictionnaire()
        val normalise = { s: String -> AccentTolerantMatcher.normalize(s) }
        val precalcule = dico.map { normalise(it.first) }

        repliNaif(inconnus[0], dico, normalise)
        LevenshteinDistance.findClosestMatchesNormalized(
            inconnus[0], dico, precalcule, normalise)

        var t0 = System.nanoTime()
        inconnus.forEach { repliNaif(it, dico, normalise) }
        val avant = (System.nanoTime() - t0) / 1_000_000.0 / inconnus.size

        t0 = System.nanoTime()
        inconnus.forEach {
            LevenshteinDistance.findClosestMatchesNormalized(it, dico, precalcule, normalise)
        }
        val apres = (System.nanoTime() - t0) / 1_000_000.0 / inconnus.size

        println("repli — avant %.1f ms | après %.1f ms | facteur %.1f"
            .format(avant, apres, avant / apres))
        assertTrue(
            "Le repli optimisé (%.1f ms) devrait battre l'ancien (%.1f ms) d'au moins un facteur 3."
                .format(apres, avant),
            avant / apres >= 3.0
        )
    }

    @Test
    fun leRepliResteDansUnOrdreDeGrandeurTenable() {
        val dico = dictionnaire()
        val normalise = { s: String -> AccentTolerantMatcher.normalize(s) }
        val precalcule = dico.map { normalise(it.first) }

        // Une passe à blanc : la première paie le chargement des classes.
        LevenshteinDistance.findClosestMatchesNormalized(
            inconnus[0], dico, precalcule, normalise)

        val debut = System.nanoTime()
        inconnus.forEach {
            LevenshteinDistance.findClosestMatchesNormalized(it, dico, precalcule, normalise)
        }
        val parMot = (System.nanoTime() - debut) / 1_000_000.0 / inconnus.size

        println("repli Levenshtein : %.1f ms par mot sur %d formes"
            .format(parMot, dico.size))
        assertTrue(
            "Le repli prend %.1f ms par mot sur la JVM : c'est un ordre de grandeur de trop."
                .format(parMot),
            parMot < 25.0
        )
    }
}
