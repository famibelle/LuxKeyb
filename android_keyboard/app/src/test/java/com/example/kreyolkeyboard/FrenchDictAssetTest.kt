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

    private fun proposables(): List<Pair<String, Int>> {
        val actif = actif()
        val mots = actif.getJSONArray("suggest_mots")
        val freqs = actif.getJSONArray("suggest_freq")
        assertEquals(
            "suggest_mots et suggest_freq doivent être alignés indice à indice",
            mots.length(), freqs.length()
        )
        return (0 until mots.length()).map { mots.getString(it) to freqs.getInt(it) }
    }

    private fun filtre(): Triple<ByteArray, Long, Int> {
        val a = actif()
        return Triple(
            FrenchDictionary.decoderBase64(a.getString("bloom")),
            a.getLong("bloom_bits"),
            a.getInt("bloom_hachages")
        )
    }

    private fun reconnu(mot: String): Boolean {
        val (b, bits, k) = filtre()
        return FrenchDictionary.bloomContient(mot, b, bits, k)
    }

    /**
     * Le bouchon créole tenait en 662 mots. Lexique 3.83 en donne plus de
     * 125 000 ; on refuse en dessous de la moitié, seuil qui distingue sans
     * ambiguïté une régénération dégradée d'un retour du bouchon.
     */
    @Test
    fun couvertureSuffisantePourLeCorrecteur() {
        assertTrue(
            "Dictionnaire français trop pauvre : le correcteur soulignerait " +
                "du français correct.",
            actif().getInt("word_count") >= 60_000
        )
    }

    /**
     * **Le contrôle central.** Le filtre est écrit en Python et relu en
     * Kotlin ; une divergence de hachage entre les deux ferait souligner tout
     * le français d'un coup, sans rien casser d'autre — le JSON resterait
     * valide, le clavier chargerait, la rangée bleue s'afficherait.
     *
     * On rejoue donc le filtre livré sur les formes livrées : un filtre de
     * Bloom ne peut jamais rejeter ce qu'on y a mis, donc une seule forme
     * refusée prouve que les deux implémentations ont divergé.
     */
    @Test
    fun leFiltreReconnaitToutesLesFormesLivrees() {
        val (b, bits, k) = filtre()
        assertTrue("Filtre de Bloom vide", b.isNotEmpty() && bits > 0 && k > 0)
        val manquantes = proposables().map { it.first }
            .filterNot { FrenchDictionary.bloomContient(it, b, bits, k) }
        assertTrue(
            "Le filtre rejette ${manquantes.size} formes qu'il contient " +
                "(${manquantes.take(5)}) : le hachage Kotlin a divergé du Python.",
            manquantes.isEmpty()
        )
    }

    /**
     * Le filtre accepte parfois à tort, et c'est admis : ne pas souligner une
     * faute est bénin. Mais au-delà de quelques pour cent il n'accepterait
     * plus rien de significatif et le correcteur français deviendrait inerte.
     */
    @Test
    fun tauxDeFauxPositifsRaisonnable() {
        val (b, bits, k) = filtre()
        val alea = java.util.Random(20260903)
        val lettres = "abcdefghijklmnopqrstuvwxyz"
        var faux = 0
        val essais = 20_000
        repeat(essais) {
            val n = 6 + alea.nextInt(7)
            val mot = buildString { repeat(n) { append(lettres[alea.nextInt(26)]) } }
            if (FrenchDictionary.bloomContient(mot, b, bits, k)) faux++
        }
        val taux = 100.0 * faux / essais
        assertTrue("Faux positifs : %.2f %%, filtre sous-dimensionné".format(taux),
            taux < 3.0)
    }

    /**
     * Le palier proposable est ce qui alimente la rangée bleue. Le partage
     * étant grammatical, un bug de catégorisation le viderait sans que le
     * total bouge — d'où un contrôle séparé.
     */
    @Test
    fun palierProposableSuffisant() {
        val p = proposables()
        assertTrue(
            "Seulement ${p.size} formes proposables : la rangée bleue se viderait.",
            p.size >= 40_000
        )
        val counts = actif().getJSONObject("counts")
        assertTrue(
            "Trop peu de formes écartées de la rangée bleue : le tri par " +
                "catégorie grammaticale n'a probablement pas fonctionné.",
            counts.getInt("spellcheck") >= 10_000
        )
    }

    /**
     * Les deux paliers sont disjoints : une forme des deux côtés serait
     * comptée deux fois par word_count et proposée alors qu'elle est censée
     * ne pas l'être.
     */
    /**
     * Les formes verbales rares ne sont plus livrées en clair : elles
     * n'existent que dans le filtre. Le correcteur doit malgré tout les
     * accepter, sans quoi l'allègement aurait réintroduit le défaut qu'il
     * était censé préserver.
     */
    @Test
    fun lesFormesVerbalesRaresRestentReconnues() {
        listOf("réagissent", "chanteriez", "finissions", "abrogée")
            .forEach {
                assertTrue("Forme verbale rare rejetée par le correcteur : $it",
                    reconnu(it))
            }
    }

    /**
     * `FrenchDictionary.searchFrenchWords()` indexe par les trois premières
     * lettres et trie par fréquence décroissante ; une fréquence nulle ou
     * négative ferait disparaître une forme du classement sans la retirer du
     * dictionnaire.
     */
    @Test
    fun frequencesToutesPositives() {
        val fautives = proposables().filter { it.second < 1 }
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
        // Le chargeur s'appuie sur cet ordre : chaque seau de préfixe hérite
        // du classement de la source, ce qui évite un tri par frappe.
        val frequences = proposables().map { it.second }
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
        val locutions = proposables().map { it.first }.filter { " " in it }
        assertTrue("Locutions livrées : ${locutions.take(5)}", locutions.isEmpty())
    }

    /**
     * Un échantillon de ce que le bouchon ne pouvait pas contenir : formes
     * fléchies et vocabulaire courant. Leur absence signerait un retour à une
     * liste de mots-outils.
     */
    @Test
    fun formesFlechiesPresentes() {
        val connues = proposables().map { it.first }.toSet()
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
