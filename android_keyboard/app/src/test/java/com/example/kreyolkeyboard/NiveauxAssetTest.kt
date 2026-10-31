package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.carnet.Essentiels
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La série « Niveau A1 » (`luxemburgish_niveau_a1.json`, écrit par
 * `Dictionnaires/generate_niveaux.py`) : le vocabulaire du manuel A1 de
 * l'INLL tel que le LOD le marque, et la preuve que la série se finit.
 *
 * Comme pour les essentiels, une case impossible ne se verrait pas : elle
 * resterait vide pour toujours. D'où ce test, mot par mot.
 */
class NiveauxAssetTest {

    private val donnees by lazy { Essentiels.lire(File("src/main/assets/luxemburgish_niveau_a1.json").readText()) }

    private fun traductions(langue: String): Map<String, String> {
        val nom = if (langue == "fr") "luxemburgish_translations.json" else "luxemburgish_translations_$langue.json"
        val t = JSONObject(File("src/main/assets/$nom").readText()).getJSONObject("translations")
        return t.keys().asSequence().associateWith { t.getString(it) }
    }

    private val essentiels by lazy { Essentiels.lire(File("src/main/assets/${Essentiels.ACTIF}").readText()) }

    @Test
    fun laSerieAPresQueTousLesMotsDuManuel() {
        // 845 lemmes marqués A1 ; quelques-uns restent dehors (contractions
        // sans sens, mots écartés, noms propres).
        assertTrue("Seulement ${donnees.mots.size} mots", donnees.mots.size >= 780)
        assertEquals("mot en double", donnees.mots.size, donnees.themes.sumOf { it.mots.size })
        assertEquals("ids en double", donnees.themes.size, donnees.themes.map { it.id }.toSet().size)
        for (t in donnees.themes) {
            assertTrue("« ${t.id} » est vide", t.mots.isNotEmpty())
            assertTrue("« ${t.id} » : ${t.mots.size} cases, trop pour une page", t.mots.size <= 40)
            for (l in listOf("en", "fr", "de", "lb", "pt")) {
                assertTrue("« ${t.id} » n'a pas de titre en $l", !t.titres[l].isNullOrBlank())
            }
        }
        val mots = donnees.mots.toSet()
        for (m in listOf("Famill", "Mamm", "Apel", "Kaffi", "kommen", "schwätzen", "gréng", "Auto", "Moien")) {
            assertTrue("« $m » manque", m in mots)
        }
    }

    @Test
    fun chaqueMotADuSensDansLesQuatreLangues() {
        for (l in listOf("fr", "de", "en", "pt")) {
            val propres = donnees.gloses[l].orEmpty() + essentiels.gloses[l].orEmpty()
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
        // « sauer » est aigre, pas la Sûre ; « Band » le groupe, pas le ruban.
        assertTrue("sauer" in donnees.mots && "Sauer" !in donnees.mots)
        assertEquals("groupe de musique", donnees.gloses.getValue("fr")["Band"])
        for (m in donnees.isoles) {
            assertTrue("« $m » isolé mais sans sens propre", donnees.gloses.values.all { m in it })
            assertTrue("« $m » isolé mais sans catégorie", m in donnees.categories)
        }
    }

    @Test
    fun chaqueCaseAUnJeuQuiLaDonne() {
        for (m in donnees.mots) {
            assertTrue("« $m » : aucun jeu ne peut le donner, la série ne se finirait pas",
                Essentiels.chemins(m).isNotEmpty())
        }
    }

    @Test
    fun aucunMotNEstTenuALEcart() {
        for (m in donnees.mots.filterNot { Essentiels.estExpression(it) }) {
            assertTrue("« $m » est dans MotsEcartes : les jeux ne devraient pas le proposer", !MotsEcartes.estEcarte(m))
        }
    }
}
