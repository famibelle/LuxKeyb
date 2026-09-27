package com.example.kreyolkeyboard

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contrôles de la table des catégories (`luxemburgish_categories.json`), qui
 * donne sa ligne de type à chaque carte du carnet.
 *
 * Une table absente ou vide ne casse rien à l'écran : `chargerCategories`
 * avale l'erreur et la carte retombe sur la majuscule du nom. C'est pour cela
 * que la régression doit échouer ici.
 */
class CategoriesAssetTest {

    private fun table(nom: String, champ: String): JSONObject {
        val fichier = File("src/main/assets/$nom")
        assertTrue(
            "$nom manquant — lancez Dictionnaires/generate_translations.py",
            fichier.exists()
        )
        return JSONObject(fichier.readText()).getJSONObject(champ)
    }

    private fun categories() = table("luxemburgish_categories.json", "categories")

    @Test
    fun laCouvertureResteMassive() {
        val n = categories().length()
        assertTrue("Seulement $n mots catégorisés", n >= 20_000)
    }

    /** Un code que la carte ne sait pas nommer s'afficherait comme rien. */
    @Test
    fun chaqueCodeANomEnClair() {
        val categories = categories()
        val cles = categories.keys()
        while (cles.hasNext()) {
            val mot = cles.next()
            val code = categories.getString(mot)
            assertNotNull(
                "« $mot » → « $code » n'a pas de libellé",
                TranslationDictionary.libelleCategorie(code)
            )
        }
    }

    /** Même clé que les identifiants d'article : le mot que la fiche affiche. */
    @Test
    fun chaqueCleEstUnMotAffichable() {
        val categories = categories()
        val articles = table("luxemburgish_lod_ids.json", "articles")
        val cles = categories.keys()
        while (cles.hasNext()) {
            val mot = cles.next()
            assertTrue(
                "« $mot » porte une catégorie mais aucune fiche ne l'affiche",
                articles.has(mot)
            )
        }
    }

    @Test
    fun desMotsConnusOntLeurCategorie() {
        val categories = categories()
        for ((mot, attendu) in listOf(
            "Aarbecht" to "Nom féminin",
            "Stad" to "Nom féminin",
            "Haus" to "Nom neutre",
            "gutt" to "Adjectif"
        )) {
            assertTrue("« $mot » n'a plus de catégorie", categories.has(mot))
            assertEquals(
                "« $mot »",
                attendu,
                TranslationDictionary.libelleCategorie(categories.getString(mot))
            )
        }
    }

    @Test
    fun lesLibellesSontEnClair() {
        assertEquals("Nom masculin", TranslationDictionary.libelleCategorie("SUBST M"))
        assertEquals("Nom", TranslationDictionary.libelleCategorie("SUBST"))
        assertEquals("Verbe", TranslationDictionary.libelleCategorie("VRB"))
        assertEquals(null, TranslationDictionary.libelleCategorie("(bei Pronominaladverben)"))
    }
}
