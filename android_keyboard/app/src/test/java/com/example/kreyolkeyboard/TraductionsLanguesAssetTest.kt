package com.example.kreyolkeyboard

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Les gloses dans la langue de l'interface : un actif par langue autre que le
 * français (`luxemburgish_translations_<langue>.json`), tiré du LOD comme
 * l'actif français.
 *
 * Un actif de langue effondré ne casse rien de visible : TranslationDictionary
 * ne trouve plus de glose, les jeux retombent sur leur repli et les cartes
 * deviennent muettes, dans une seule langue, celle que le développeur ne
 * regarde pas. D'où ces gardes, sur les fichiers livrés.
 */
class TraductionsLanguesAssetTest {

    private val assets = File("src/main/assets")
    private val langues = listOf("de", "en", "pt")

    private fun actif(langue: String) =
        JSONObject(File(assets, "luxemburgish_translations_$langue.json").readText())

    private fun formesDuDictionnaire(): Set<String> {
        val tableau = org.json.JSONArray(File(assets, "luxemburgish_dict.json").readText())
        return (0 until tableau.length()).map { tableau.getJSONArray(it).getString(0) }.toHashSet()
    }

    @Test
    fun `chaque langue glose le dictionnaire`() {
        val dictionnaire = formesDuDictionnaire()
        for (langue in langues) {
            val racine = actif(langue)
            assertEquals(langue, racine.getString("langue"))
            val table = racine.getJSONObject("translations")
            val glosees = table.keys().asSequence().count { it in dictionnaire }
            assertTrue("$langue : $glosees formes du dictionnaire glosées", glosees >= 15000)
            val propres = racine.getJSONArray("noms_propres")
            assertTrue("$langue : ${propres.length()} noms propres", propres.length() >= 1000)
            assertTrue("$langue : crédit du LOD absent", racine.getJSONArray("attribution").length() >= 1)
        }
    }

    /**
     * Les traductions d'exemples sont alignées, phrase pour phrase, sur les
     * phrases de luxemburgish_exemples.json : la fiche les apparie par rang.
     * Un décalage afficherait sous une phrase la traduction d'une autre.
     */
    @Test
    fun `les traductions d'exemples sont alignees sur les phrases`() {
        val exemples = JSONObject(File(assets, "luxemburgish_exemples.json").readText())
            .getJSONObject("exemples")
        for (langue in langues) {
            val traduites = actif(langue).getJSONObject("exemples_traductions")
            for (mot in traduites.keys()) {
                assertTrue("$langue : « $mot » n'a pas d'exemples", exemples.has(mot))
                assertEquals(
                    "$langue : « $mot » désaligné",
                    exemples.getJSONArray(mot).length(),
                    traduites.getJSONArray(mot).length()
                )
            }
            // Le ZLS traduit en allemand et en anglais, pas en portugais.
            if (langue != "pt") {
                assertTrue("$langue : ${traduites.length()} mots traduits", traduites.length() >= 2000)
            }
        }
    }

    /** Chaque langue de l'interface désigne un actif de gloses qui existe. */
    @Test
    fun `chaque interface a ses gloses`() {
        val res = File("src/main/res")
        val dossiers = res.listFiles { f -> f.isDirectory && f.name.matches(Regex("values(-[a-z]{2})?")) }!!
        assertTrue(dossiers.size >= 5)
        for (dossier in dossiers) {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File(dossier, "strings.xml"))
            val noeuds = doc.getElementsByTagName("string")
            val langue = (0 until noeuds.length).map { noeuds.item(it) as org.w3c.dom.Element }
                .first { it.getAttribute("name") == "langue_traductions" }.textContent
            val nom = if (langue == "fr") "luxemburgish_translations.json"
                      else "luxemburgish_translations_$langue.json"
            assertTrue("${dossier.name} → $nom introuvable", File(assets, nom).exists())
        }
    }
}
