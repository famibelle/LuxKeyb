package com.example.kreyolkeyboard

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contrôles du dictionnaire français (`french_simple_dict.json`).
 *
 * Cet actif a deux consommateurs, et le second est le plus exposé :
 *
 * - `FrenchDictionary.getSuggestions()` remplit la seconde rangée de la barre
 *   de suggestions, à partir de trois lettres ;
 * - `FrenchDictionary.containsWord()` alimente `SuggestionEngine.isKnownWord()`,
 *   que `KreyolSpellCheckerService` interroge pour décider s'il souligne un mot.
 *   `res/xml/kreyol_spellchecker.xml` déclarant la locale `fr`, le clavier
 *   remplace le correcteur français du système : un dictionnaire trop maigre
 *   souligne du français correct dans **toutes** les applications.
 *
 * Il a justement été livré maigre pendant longtemps — 662 mots aux fréquences
 * écrites à la main, héritées du clavier créole — et rien ne le signalait :
 * le JSON restait valide, le moteur chargeait, la rangée s'affichait. D'où ces
 * contrôles, qui portent sur le volume et la provenance autant que sur la
 * forme.
 */
class FrenchDictAssetTest {

    private fun actif(): JSONObject {
        val fichier = File("src/main/assets/french_simple_dict.json")
        assertTrue(
            "french_simple_dict.json manquant — lancez " +
                "Dictionnaires/generate_french_dict.py",
            fichier.exists()
        )
        return JSONObject(fichier.readText())
    }

    private fun formes(): List<Pair<String, Int>> {
        val mots = actif().getJSONArray("words")
        return (0 until mots.length()).map {
            val paire = mots.getJSONArray(it)
            paire.getString(0) to paire.getInt(1)
        }
    }

    /**
     * Le bouchon créole tenait en 662 mots. Lexique 3.83 en donne plus de
     * 125 000 ; on refuse en dessous de la moitié, seuil qui distingue sans
     * ambiguïté une régénération dégradée d'un retour du bouchon.
     */
    @Test
    fun couvertureSuffisantePourLeCorrecteur() {
        val mots = formes()
        assertTrue(
            "Dictionnaire français trop pauvre : ${mots.size} formes. " +
                "Le correcteur soulignerait du français correct.",
            mots.size >= 60_000
        )
        assertEquals(
            "word_count doit décrire le tableau qu'il accompagne",
            mots.size,
            actif().getInt("word_count")
        )
    }

    /**
     * `FrenchDictionary.searchFrenchWords()` indexe par les trois premières
     * lettres et trie par fréquence décroissante ; une fréquence nulle ou
     * négative ferait disparaître une forme du classement sans la retirer du
     * dictionnaire.
     */
    @Test
    fun frequencesToutesPositives() {
        val fautives = formes().filter { it.second < 1 }
        assertTrue(
            "Fréquences nulles ou négatives : ${fautives.take(5)}",
            fautives.isEmpty()
        )
    }

    /**
     * Le fichier est livré trié, et le chargeur s'appuie dessus : `groupBy`
     * conserve l'ordre de la source, donc chaque seau de préfixe est déjà
     * classé par fréquence décroissante.
     */
    @Test
    fun ordreDecroissant() {
        val frequences = formes().map { it.second }
        val rupture = (1 until frequences.size).firstOrNull {
            frequences[it] > frequences[it - 1]
        }
        assertTrue(
            "Fichier non trié à l'indice $rupture",
            rupture == null
        )
    }

    /**
     * Une locution ne se tape pas comme un mot : la complétion par préfixe
     * s'arrêterait au premier espace, et le correcteur ne verrait jamais la
     * forme entière. Lexique en contient 319, que le générateur écarte.
     */
    @Test
    fun aucuneLocution() {
        val locutions = formes().filter { " " in it.first }
        assertTrue("Locutions livrées : ${locutions.take(5)}", locutions.isEmpty())
    }

    /**
     * Un échantillon de ce que le bouchon ne pouvait pas contenir : formes
     * fléchies et vocabulaire courant. Leur absence signerait un retour à une
     * liste de mots-outils.
     */
    @Test
    fun formesFlechiesPresentes() {
        val connues = formes().map { it.first }.toSet()
        listOf(
            "bonjour", "maison", "mangeons", "mangeaient",
            "aujourd'hui", "peut-être", "cependant", "téléphone"
        ).forEach {
            assertTrue("Forme absente du dictionnaire français : $it", it in connues)
        }
    }

    /**
     * Lexique est publié sous CC BY-SA 4.0 : l'attribution est une obligation
     * de licence, pas une politesse. Elle est aussi affichée dans la carte
     * « Sources » de l'application.
     */
    @Test
    fun attributionPresente() {
        val actif = actif()
        assertTrue(
            "Licence manquante",
            actif.optString("licence").contains("BY-SA")
        )
        assertTrue(
            "Attribution manquante ou incomplète",
            actif.optString("attribution").contains("Lexique")
        )
    }
}
