package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.carnet.Essentiels
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Les essentiels (`luxemburgish_essentiels.json`, écrit par
 * `Dictionnaires/generate_essentiels.py`) : la liste du propriétaire, et la
 * preuve que sa série se finit.
 *
 * Une case impossible ne se verrait pas : elle resterait vide pour toujours.
 * D'où ce test, mot par mot : chaque essentiel a un sens dans les quatre
 * langues de glose, et au moins un jeu qui le donne.
 */
class EssentielsAssetTest {

    private val donnees by lazy { Essentiels.lire(File("src/main/assets/${Essentiels.ACTIF}").readText()) }

    private fun traductions(langue: String): Map<String, String> {
        val nom = if (langue == "fr") "luxemburgish_translations.json" else "luxemburgish_translations_$langue.json"
        val t = JSONObject(File("src/main/assets/$nom").readText()).getJSONObject("translations")
        return t.keys().asSequence().associateWith { t.getString(it) }
    }

    @Test
    fun laListeEstCelleDuProprietaire() {
        assertEquals(22, donnees.themes.size)
        assertEquals(271, donnees.themes.sumOf { it.mots.size })
        assertEquals("ids en double", donnees.themes.size, donnees.themes.map { it.id }.toSet().size)
        for (t in donnees.themes) {
            assertTrue("« ${t.id} » est vide", t.mots.isNotEmpty())
            for (l in listOf("en", "fr", "de", "lb", "pt")) {
                assertTrue("« ${t.id} » n'a pas de titre en $l", !t.titres[l].isNullOrBlank())
            }
        }
        // Quelques mots qui doivent y être, à la forme de leur carte.
        val mots = donnees.mots.toSet()
        for (m in listOf("Moien", "Gudde Moien", "jo", "Mee", "Ee", "A", "Méindeg", "zu Fouss", "Wéi vill kascht dat?")) {
            assertTrue("« $m » manque", m in mots)
        }
    }

    @Test
    fun chaqueMotADuSensDansLesQuatreLangues() {
        for (l in listOf("fr", "de", "en", "pt")) {
            val propres = donnees.gloses[l].orEmpty()
            val table = traductions(l)
            val minuscules = table.entries.associate { it.key.lowercase() to it.value }
            for (m in donnees.mots) {
                val glose = propres[m] ?: table[m] ?: minuscules[m.lowercase()]
                assertTrue("« $m » n'a pas de sens en $l", !glose.isNullOrBlank())
            }
        }
    }

    @Test
    fun lesHomographesOntLeurPropreSens() {
        // Sans le sens propre, « Mee » serait « mais » et « Ee » un adverbe.
        assertEquals("mai", donnees.gloses.getValue("fr")["Mee"])
        assertEquals("œuf", donnees.gloses.getValue("fr")["Ee"])
        assertEquals("œil", donnees.gloses.getValue("fr")["A"])
        assertEquals("fête", donnees.gloses.getValue("fr")["Fest"])
        for (m in donnees.isoles) {
            assertTrue("« $m » isolé mais sans sens propre", donnees.gloses.values.all { m in it })
            assertTrue("« $m » isolé mais sans catégorie", m in donnees.categories)
        }
    }

    @Test
    fun lesExpressionsSontMarquees() {
        val expressions = donnees.mots.filter { Essentiels.estExpression(it) }
        assertEquals(11, expressions.size)
        for (e in expressions) assertEquals("« $e »", "EXPR", donnees.categories[e])
    }

    @Test
    fun chaqueCaseAUnJeuQuiLaDonne() {
        for (m in donnees.mots) {
            assertTrue("« $m » : aucun jeu ne peut le donner, la série ne se finirait pas",
                Essentiels.chemins(m).isNotEmpty())
        }
    }

    @Test
    fun aucunEssentielNEstTenuALEcart() {
        for (m in donnees.mots.filterNot { Essentiels.estExpression(it) }) {
            assertTrue("« $m » est dans MotsEcartes : les jeux ne devraient pas le proposer", !MotsEcartes.estEcarte(m))
        }
    }
}
