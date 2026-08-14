package com.example.kreyolkeyboard

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests des prédictions contextuelles à deux mots.
 *
 * Le pipeline comptait les trigrammes puis n'exportait que les bigrammes, et le
 * clavier n'interrogeait le modèle qu'avec le dernier mot saisi alors qu'il
 * gardait déjà cinq mots d'historique. La prédiction se résumait donc à « quel
 * mot suit ce mot ».
 */
class NgramContextTest {

    // ===== Résolution du contexte =====

    @Test
    fun `le contexte a deux mots est prefere quand le modele le connait`() {
        val modele = setOf("an ka", "ka")
        assertEquals("an ka", SuggestionEngine.resolveNgramContext("an", "ka", modele::contains))
    }

    @Test
    fun `repli sur le dernier mot quand la paire est absente`() {
        val modele = setOf("ka")
        assertEquals("ka", SuggestionEngine.resolveNgramContext("zwazo", "ka", modele::contains))
    }

    @Test
    fun `le premier mot d'une phrase n'a pas de contexte precedent`() {
        val modele = setOf("ka", "an ka")
        assertEquals("ka", SuggestionEngine.resolveNgramContext(null, "ka", modele::contains))
    }

    // ===== Format du modèle livré =====

    private fun loadModel(): JSONObject? {
        val file = File("src/main/assets/luxemburgish_ngrams.json")
        return if (file.exists()) JSONObject(file.readText()) else null
    }

    @Test
    fun `le modele livre contient les deux familles de cles`() {
        val model = loadModel() ?: return

        var unMot = 0
        var deuxMots = 0
        for (key in model.keys()) {
            if (key.contains(' ')) deuxMots++ else unMot++
        }

        assertTrue("aucune clé à un mot dans le modèle", unMot > 0)
        assertTrue("aucune clé à deux mots : les trigrammes ne sont pas exportés", deuxMots > 0)
    }

    @Test
    fun `aucune cle ne comporte plus de deux mots`() {
        val model = loadModel() ?: return

        for (key in model.keys()) {
            assertTrue(
                "clé inattendue à plus de deux mots : '$key'",
                key.count { it == ' ' } <= 1
            )
        }
    }

    @Test
    fun `chaque contexte propose des suites correctement formees`() {
        val model = loadModel() ?: return

        for (key in model.keys()) {
            val candidates = model.getJSONArray(key)
            assertTrue("contexte vide : '$key'", candidates.length() > 0)
            for (i in 0 until candidates.length()) {
                val candidate = candidates.getJSONObject(i)
                val probability = candidate.getDouble("probability")
                assertTrue("mot vide dans '$key'", candidate.getString("word").isNotBlank())
                assertTrue("probabilité hors bornes dans '$key' : $probability", probability in 0.0..1.0)
            }
        }
    }

    @Test
    fun `les suites d'un contexte sont triees par probabilite decroissante`() {
        val model = loadModel() ?: return

        for (key in model.keys()) {
            val candidates = model.getJSONArray(key)
            var previous = 1.0
            for (i in 0 until candidates.length()) {
                val probability = candidates.getJSONObject(i).getDouble("probability")
                assertTrue("suites non triées pour '$key'", probability <= previous)
                previous = probability
            }
        }
    }

    @Test
    fun `un contexte a deux mots affine bien la prediction du mot seul`() {
        val model = loadModel() ?: return

        // Recherche d'un cas réel où la paire propose autre chose que le mot seul :
        // c'est tout l'intérêt du contexte élargi
        var affinements = 0
        for (key in model.keys()) {
            if (!key.contains(' ')) continue
            val lastWord = key.substringAfter(' ')
            if (!model.has(lastWord)) continue

            val depuisPaire = model.getJSONArray(key).getJSONObject(0).getString("word")
            val depuisMotSeul = model.getJSONArray(lastWord).getJSONObject(0).getString("word")
            if (depuisPaire != depuisMotSeul) affinements++
        }

        assertTrue(
            "aucun contexte à deux mots ne change la prédiction : le modèle n'apporte rien",
            affinements > 0
        )
    }
}
