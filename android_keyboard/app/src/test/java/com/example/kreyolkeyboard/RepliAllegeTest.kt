package com.example.kreyolkeyboard

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Ce qui rend sûr le repli de correction allégé du 2026-10-03.
 *
 * Trois changements, chacun avec sa preuve : la distance en bande sans
 * allocation donne les mêmes valeurs que l'ancienne, le second passage sans
 * normalisation ne trouve jamais rien que le premier aurait manqué, et une
 * recherche annulée s'arrête.
 */
class RepliAllegeTest {

    /** Le dictionnaire tel que le moteur le parcourt : corpus puis LOD. */
    private val dico: List<Pair<String, Int>> by lazy {
        val t = JSONArray(File("src/main/assets/luxemburgish_dict.json").readText())
        val corpus = (0 until t.length())
            .map { t.getJSONArray(it).let { p -> p.getString(0) to p.getInt(1) } }
            .sortedByDescending { it.second }
        val lod = JSONObject(File("src/main/assets/luxemburgish_lod_forms.json").readText())
            .getJSONArray("suggest")
        corpus + (0 until lod.length()).map { lod.getString(it) to 1 }
    }

    private val normalise = { s: String -> AccentTolerantMatcher.normalize(s) }
    private val normalises: List<String> by lazy { dico.map { normalise(it.first) } }

    /** Fautes réelles, mots étrangers, noms : ce qui déclenche le repli. */
    private val saisies = listOf(
        "Haux", "schaffn", "geschwat", "Freidg", "Moein", "Lëtzbuerg", "Kanner",
        "Schoull", "wéi", "geet", "dech", "unserer", "deshalb", "voulais",
        "processus", "kryptowaerung", "miteinander", "Bettel", "abrogeassions",
        "ECH", "Dag", "Owen", "Ament", "zesumme", "Regierng", "Minster"
    )

    @Test
    fun laBandeDonneLaMemeDistanceQueLeCalculComplet() {
        val formes = dico.map { it.first }.filterIndexed { i, _ -> i % 37 == 0 }
        for (max in 0..3) {
            for (saisie in saisies) {
                val entree = CharArray(saisie.length) { saisie[it].lowercaseChar() }
                val l1 = IntArray(saisie.length + 1)
                val l2 = IntArray(saisie.length + 1)
                for (forme in formes) {
                    val attendu = LevenshteinDistance.calculateBounded(saisie, forme, max)
                        .coerceAtMost(max + 1)
                    val obtenu = LevenshteinDistance.distanceBornee(entree, forme, max, l1, l2)
                    assertEquals("'$saisie' / '$forme' au seuil $max", attendu, obtenu)
                }
            }
        }
    }

    @Test
    fun leSecondPassageNeTrouveRienQuandLePremierEchoue() {
        // Le raisonnement du moteur suppose un dictionnaire sans « ß » ; s'il
        // en gagnait un, le moteur referait le second passage de lui-même.
        assertTrue(dico.none { 'ß' in it.first })
        for (saisie in saisies) {
            val normalisee = LevenshteinDistance.findClosestMatchesNormalized(
                saisie, dico, normalises, normalise, 2, 5)
            if (normalisee.isNotEmpty()) continue
            val directe = LevenshteinDistance.findClosestMatches(saisie, dico, 2, 5)
            assertTrue("'$saisie' : le passage direct trouve $directe", directe.isEmpty())
        }
    }

    @Test
    fun uneRechercheAnnuleeSArreteSansRien() {
        val resultat = LevenshteinDistance.findClosestMatchesNormalized(
            "Haux", dico, normalises, normalise, 2, 5, estAnnule = { true })
        assertTrue(resultat.isEmpty())
    }
}
