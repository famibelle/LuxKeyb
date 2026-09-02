package com.example.kreyolkeyboard

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contrôles de `luxemburgish_lod_forms.json`, le complément du dictionnaire.
 *
 * L'actif est produit hors du build par `Dictionnaires/generate_lod_forms.py`
 * à partir de l'index de recherche du Lëtzebuerger Online Dictionnaire. Il
 * porte les formes que le corpus ne peut pas donner : LuxAlign est du
 * journalisme RTL, qui n'écrit ni « Läffelen », ni « sprang », ni « denks ».
 *
 * Trois régressions passeraient inaperçues sans ces contrôles, parce qu'aucune
 * ne fait planter quoi que ce soit :
 *
 * - un actif absent ou vide fait simplement retomber le clavier sur la
 *   couverture du corpus (`SuggestionEngine.loadLodForms` journalise et
 *   continue) : l'APK s'installe, les suggestions marchent, elles connaissent
 *   juste trois fois moins de mots ;
 * - une confusion des deux paliers ferait proposer à la frappe les variantes
 *   de la règle d'Eifel, qui ne sont correctes qu'en contexte ;
 * - un doublon entre l'actif et le dictionnaire ferait charger deux fois la
 *   même forme, et afficher deux fois la même suggestion.
 */
class LodFormsAssetTest {

    private fun charger(): JSONObject {
        val fichier = File("src/main/assets/luxemburgish_lod_forms.json")
        assertTrue(
            "luxemburgish_lod_forms.json manquant — lancez " +
                "Dictionnaires/generate_lod_forms.py",
            fichier.exists()
        )
        return JSONObject(fichier.readText())
    }

    private fun liste(cle: String): List<String> {
        val tableau = charger().getJSONArray(cle)
        return (0 until tableau.length()).map { tableau.getString(it) }
    }

    private fun dictionnaire(): JSONArray =
        JSONArray(File("src/main/assets/luxemburgish_dict.json").readText())

    @Test
    fun `le volume livre triple la couverture`() {
        val proposables = liste("suggest")
        val connues = liste("spellcheck")
        // Mesuré à 84 855 et 26 424 le 2026-09-02. Sous la moitié, c'est que
        // l'index du LOD a changé de structure ou que le filtre a dérapé.
        assertTrue("seulement ${proposables.size} formes proposables", proposables.size >= 40000)
        assertTrue("seulement ${connues.size} formes connues", connues.size >= 10000)
    }

    @Test
    fun `aucune forme n'est deja dans le dictionnaire`() {
        val tableau = dictionnaire()
        val duCorpus = (0 until tableau.length())
            .mapTo(HashSet()) { tableau.getJSONArray(it).getString(0).lowercase() }

        // La casse du corpus fait foi : elle est élue sur les occurrences
        // réelles (Groussschreiwung), là où le LOD lemmatise à la sienne.
        val doublons = liste("suggest").filter { it.lowercase() in duCorpus }.take(5)
        assertTrue("formes déjà au dictionnaire : $doublons", doublons.isEmpty())
    }

    @Test
    fun `les deux paliers sont disjoints`() {
        val proposables = liste("suggest").mapTo(HashSet()) { it.lowercase() }
        val intruses = liste("spellcheck").filter { it.lowercase() in proposables }.take(5)
        // Une forme des deux côtés serait chargée deux fois par le moteur, et
        // proposée alors qu'elle n'est censée qu'être reconnue.
        assertTrue("formes dans les deux paliers : $intruses", intruses.isEmpty())
    }

    @Test
    fun `les formes sont des mots simples`() {
        val suspectes = (liste("suggest") + liste("spellcheck"))
            .filter { forme ->
                forme.length < 2 ||
                    !forme.first().isLetter() ||
                    forme.any { !it.isLetter() && it != '-' && it != '\'' }
            }
            .take(10)
        // Le moteur complète des mots, pas des locutions : « virun Ae féieren »
        // ou « 3D-Drucker » n'y ont rien à faire.
        assertTrue("formes non complétables : $suspectes", suspectes.isEmpty())
    }

    @Test
    fun `les manques signales par les locuteurs sont couverts`() {
        val toutes = (liste("suggest") + liste("spellcheck")).mapTo(HashSet()) { it.lowercase() }
        // Le motif exact du chantier : des formes ordinaires que le corpus de
        // dépêches n'écrit jamais. Si l'appariement se casse, ce test le dit
        // avec des mots plutôt qu'avec un volume.
        val attendues = listOf("läffelen", "forschetten", "telleren", "sprang", "denks")
        val absentes = attendues.filterNot { it in toutes }
        assertTrue("formes toujours absentes : $absentes", absentes.isEmpty())
    }

    @Test
    fun `la fusion reste triee par frequence decroissante`() {
        // Ce que fait `SuggestionEngine.loadDictionary` : le corpus trié, puis
        // les formes LOD à LOD_FREQUENCY. La fenêtre CANDIDATE_POOL_SIZE de
        // getDictionarySuggestions() coupe après 40 correspondances en
        // supposant le classement décroissant ; si une forme LOD passait devant
        // un mot du corpus, elle occuperait une place dans les suggestions au
        // lieu de simplement compléter les préfixes rares.
        val tableau = dictionnaire()
        val frequences = (0 until tableau.length())
            .map { tableau.getJSONArray(it).getInt(1) }
            .sortedDescending() +
            List(liste("suggest").size) { SuggestionEngine.LOD_FREQUENCY }

        val minimumCorpus = (0 until tableau.length())
            .minOf { tableau.getJSONArray(it).getInt(1) }
        assertTrue(
            "une entrée du corpus descend à $minimumCorpus, soit au niveau des " +
                "formes LOD (${SuggestionEngine.LOD_FREQUENCY}) : le marqueur " +
                "de provenance ne distingue plus rien",
            minimumCorpus > SuggestionEngine.LOD_FREQUENCY
        )
        val rupture = (1 until frequences.size).firstOrNull {
            frequences[it] > frequences[it - 1]
        }
        assertTrue("classement rompu à l'index $rupture", rupture == null)
    }

    @Test
    fun `l'actif cite le LOD`() {
        val racine = charger()
        val attribution = racine.getJSONArray("attribution")
        val texte = (0 until attribution.length())
            .joinToString(" ") { attribution.getString(it) }
        // CC0 n'impose rien : le crédit est la contrepartie qu'on s'impose.
        assertTrue("le ZLS n'est pas cité", texte.contains("Zenter fir d'Lëtzebuerger Sprooch"))
        assertTrue("licence non déclarée", racine.getString("licence").contains("CC0"))
    }

    @Test
    fun `les compteurs annonces correspondent aux listes`() {
        val comptes = charger().getJSONObject("counts")
        assertEquals(liste("suggest").size, comptes.getInt("suggest"))
        assertEquals(liste("spellcheck").size, comptes.getInt("spellcheck"))
    }
}
