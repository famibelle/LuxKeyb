package com.example.kreyolkeyboard

import org.json.JSONArray
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Les coquilles du corpus ne sont pas livrées.
 *
 * Une faute présente dans le dictionnaire n'est jamais corrigée et elle est
 * proposée : « hunnn » venait en deuxième place sous « hunn ». La liste vit
 * dans `LuxembourgishComplet.py` (`FAUTES_DU_CORPUS`) et ce test la lit là,
 * pour qu'il n'y en ait qu'une.
 */
class FautesDuCorpusTest {

    private val fautes: List<String> by lazy {
        val source = File("../../Dictionnaires/LuxembourgishComplet.py").readText()
        val debut = source.indexOf("FAUTES_DU_CORPUS = frozenset({")
        assertTrue("FAUTES_DU_CORPUS introuvable dans le pipeline", debut >= 0)
        val bloc = source.substring(debut, source.indexOf("})", debut))
        Regex("\"([^\"]+)\"").findAll(bloc).map { it.groupValues[1] }.toList()
    }

    private val formes: Set<String> by lazy {
        val t = JSONArray(File("src/main/assets/luxemburgish_dict.json").readText())
        (0 until t.length()).map { t.getJSONArray(it).getString(0).lowercase() }.toSet()
    }

    @Test
    fun aucuneCoquilleNEstLivree() {
        assertTrue("liste vide ou mal lue : $fautes", fautes.size >= 10)
        for (faute in fautes) assertFalse("'$faute' est encore livré", faute in formes)
    }

    @Test
    fun laBonneFormeResteLivree() {
        for (bonne in listOf("hunn", "vum", "méiglech", "näischt", "nees", "dobäi",
                "allerdéngs", "tëscht", "kéier", "nëmmen", "beim", "sengem", "eréischt",
                "presentéiert")) {
            assertTrue("'$bonne' a disparu", bonne in formes)
        }
    }

    @Test
    fun hunnSeCorrigeEnHunn() {
        val t = JSONArray(File("src/main/assets/luxemburgish_dict.json").readText())
        val dico = (0 until t.length()).map { t.getJSONArray(it).let { p -> p.getString(0) to p.getInt(1) } }
        val trouves = LevenshteinDistance.findClosestMatches("hunnn", dico, 2, 3)
        assertTrue("$trouves", trouves.first().first == "hunn")
    }
}
