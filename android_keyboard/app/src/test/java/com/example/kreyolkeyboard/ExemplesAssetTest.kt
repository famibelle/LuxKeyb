package com.example.kreyolkeyboard

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contrôles des phrases d'exemple (`luxemburgish_exemples.json`).
 *
 * L'actif est produit hors du build par `Dictionnaires/generate_translations.py`
 * à partir des `<example>` du LOD, et c'est lui qui fait qu'une fiche du
 * Wierderbuch montre l'emploi du mot et pas seulement son sens.
 *
 * Trois régressions passeraient inaperçues, aucune ne faisant planter :
 *
 * - un fichier absent ou vide vide simplement la section « EXEMPLES » —
 *   `TranslationDictionary.chargerExemples` avale l'erreur exprès, pour qu'un
 *   exemple manquant n'éteigne pas la recherche ;
 * - une clé qui ne serait pas un mot affiché par la recherche porterait des
 *   phrases que personne n'atteindrait jamais : les exemples sont indexés par
 *   le représentant de famille, pas par flexion ;
 * - une phrase recollée sans ses règles d'élision (« an d' Ae gekuckt ») se lit
 *   comme une transcription, et le contrôle de format ne verrait rien.
 */
class ExemplesAssetTest {

    private fun exemples(): JSONObject {
        val fichier = File("src/main/assets/luxemburgish_exemples.json")
        assertTrue(
            "luxemburgish_exemples.json manquant — lancez " +
                "Dictionnaires/generate_translations.py",
            fichier.exists()
        )
        return JSONObject(fichier.readText()).getJSONObject("exemples")
    }

    private fun traductions(): JSONObject {
        val fichier = File("src/main/assets/luxemburgish_translations.json")
        assertTrue("luxemburgish_translations.json manquant", fichier.exists())
        return JSONObject(fichier.readText()).getJSONObject("translations")
    }

    private fun familles(): JSONObject {
        val fichier = File("src/main/assets/luxemburgish_familles.json")
        assertTrue("luxemburgish_familles.json manquant", fichier.exists())
        return JSONObject(fichier.readText()).getJSONObject("familles")
    }

    @Test
    fun laCouvertureResteMassive() {
        val exemples = exemples()
        assertTrue(
            "Seulement ${exemples.length()} mots illustrés : les fiches du " +
                "Wierderbuch n'auraient plus d'exemple",
            exemples.length() >= 10_000
        )
    }

    /**
     * Chaque clé doit être un mot que la recherche affiche, c'est-à-dire une
     * forme glosée — et jamais une flexion rangée dans une famille, puisque la
     * liste ne montre que le représentant.
     */
    @Test
    fun chaqueCleEstUnMotAffichable() {
        val exemples = exemples()
        val traductions = traductions()
        val familles = familles()
        val flexions = HashSet<String>()
        val representants = familles.keys()
        while (representants.hasNext()) {
            val representant = representants.next()
            familles.getString(representant)
                .split(" ").filterTo(flexions) { it.isNotEmpty() }
        }

        val cles = exemples.keys()
        while (cles.hasNext()) {
            val mot = cles.next()
            assertTrue(
                "« $mot » porte des exemples mais n'est pas glosé : la fiche " +
                    "ne s'ouvre jamais sur lui",
                traductions.has(mot)
            )
            assertTrue(
                "« $mot » est une flexion rangée dans une famille : la " +
                    "recherche affiche son représentant, pas lui",
                mot !in flexions
            )
        }
    }

    /**
     * Le plafond de deux phrases n'est pas cosmétique : au-delà, la fiche
     * pousse ses deux boutons hors de l'écran d'un téléphone.
     */
    @Test
    fun auPlusDeuxPhrasesEtAucuneNEstUnParagraphe() {
        val exemples = exemples()
        val cles = exemples.keys()
        while (cles.hasNext()) {
            val mot = cles.next()
            val phrases = exemples.getJSONArray(mot)
            assertTrue(
                "« $mot » porte ${phrases.length()} phrases",
                phrases.length() in 1..2
            )
            for (i in 0 until phrases.length()) {
                val phrase = phrases.getString(i)
                assertTrue(
                    "Phrase de « $mot » trop longue (${phrase.length}) : " +
                        "« $phrase »",
                    phrase.length in 15..110
                )
                assertTrue(
                    "Phrase de « $mot » recollée sans ses élisions : " +
                        "« $phrase »",
                    !phrase.contains("' ") && !phrase.contains(" ,")
                )
            }
        }
    }

    /**
     * Les traductions viennent du corpus du ZLS, rangées à part et alignées
     * sur les phrases. Deux régressions muettes : un appariement cassé vide
     * la table (les phrases s'affichent seules), et un décalage d'alignement
     * poserait sous une phrase la traduction de sa voisine.
     */
    @Test
    fun lesTraductionsSontAligneesSurLeursPhrases() {
        val racine = JSONObject(File("src/main/assets/luxemburgish_exemples.json").readText())
        val exemples = racine.getJSONObject("exemples")
        val traductions = racine.optJSONObject("traductions")
        assertTrue("luxemburgish_exemples.json ne porte plus de traductions", traductions != null)
        assertTrue(
            "Seulement ${traductions!!.length()} mots dont un exemple est traduit : " +
                "l'appariement avec le corpus du ZLS ne fonctionne plus",
            traductions.length() >= 2_000
        )

        val cles = traductions.keys()
        while (cles.hasNext()) {
            val mot = cles.next()
            assertTrue("« $mot » a des traductions mais pas de phrases", exemples.has(mot))
            val phrases = exemples.getJSONArray(mot)
            val francais = traductions.getJSONArray(mot)
            assertTrue(
                "« $mot » : ${francais.length()} traductions pour ${phrases.length()} phrases",
                francais.length() == phrases.length()
            )
            assertTrue(
                "« $mot » figure parmi les traductions sans en avoir aucune",
                (0 until francais.length()).any { francais.getString(it).isNotEmpty() }
            )
            for (i in 0 until francais.length()) {
                assertTrue(
                    "« $mot » : la traduction recopie la phrase luxembourgeoise",
                    francais.getString(i) != phrases.getString(i)
                )
            }
        }
    }

    /**
     * Les bornes du mot illustré, celles que la fiche et la carte mettent en
     * gras (`<inflectedHeadword>` du LOD).
     *
     * Deux manières de les perdre sans rien casser : un décalage d'un
     * caractère à la recomposition de la phrase, et le gras se poserait à
     * côté du mot ; un rang qui glisse, et la phrase 2 porterait les bornes
     * de la phrase 1. Les deux donnent du gras sur une syllabe au hasard,
     * personne ne lève d'exception, et aucun autre test ne le verrait.
     *
     * Le repli est volontairement permis : une phrase sans bornes retombe sur
     * la reconnaissance des formes de la famille. Ce qui ne l'est pas, c'est
     * une borne qui ne tombe pas sur un mot entier de la phrase.
     */
    @Test
    fun lesVedettesDesignentUnMotEntierDeLeurPhrase() {
        val racine = JSONObject(File("src/main/assets/luxemburgish_exemples.json").readText())
        val exemples = racine.getJSONObject("exemples")
        val vedettes = racine.optJSONObject("vedettes")
        assertTrue(
            "luxemburgish_exemples.json ne porte plus de vedettes : le gras " +
                "de la fiche et de la carte retomberait sur la famille",
            vedettes != null
        )
        assertTrue(
            "Seulement ${vedettes!!.length()} mots dont une phrase désigne son " +
                "mot illustré, sur ${exemples.length()} illustrés",
            vedettes.length() >= 20_000
        )

        val cles = vedettes.keys()
        while (cles.hasNext()) {
            val mot = cles.next()
            assertTrue("« $mot » a des vedettes mais pas de phrases", exemples.has(mot))
            val phrases = exemples.getJSONArray(mot)
            val parPhrase = vedettes.getJSONArray(mot)
            assertTrue(
                "« $mot » : ${parPhrase.length()} rangs de vedettes pour " +
                    "${phrases.length()} phrases",
                parPhrase.length() == phrases.length()
            )
            for (rang in 0 until parPhrase.length()) {
                val phrase = phrases.getString(rang)
                val bornes = parPhrase.getJSONArray(rang)
                for (i in 0 until bornes.length()) {
                    val couple = bornes.getJSONArray(i)
                    val debut = couple.getInt(0)
                    val fin = couple.getInt(1)
                    assertTrue(
                        "« $mot » : bornes [$debut, $fin] hors de « $phrase »",
                        debut in 0 until fin && fin <= phrase.length
                    )
                    val designe = phrase.substring(debut, fin)
                    assertTrue(
                        "« $mot » : la vedette « $designe » n'est pas un mot " +
                            "entier de « $phrase »",
                        designe.isNotEmpty() && designe == designe.trim() &&
                            (debut == 0 || !phrase[debut - 1].isLetter()) &&
                            (fin == phrase.length || !phrase[fin].isLetter())
                    )
                }
            }
        }
    }

    /**
     * Le cas qui a motivé la fonction : lire « Haus = maison » n'apprend pas à
     * employer le mot, et une phrase du LOD, elle, l'emploie.
     */
    @Test
    fun unMotCourantEstIllustreParUnePhraseQuiLEmploie() {
        val exemples = exemples()
        val phrases = exemples.optJSONArray("Haus")
        assertTrue("« Haus » n'a plus d'exemple", phrases != null)
        val formes = setOf("haus", "haiser", "haus'", "heiser")
        val emploie = (0 until phrases!!.length()).any { i ->
            TranslationDictionary.decouperEnMots(phrases.getString(i))
                .any { it.lowercase() in formes }
        }
        assertTrue(
            "Aucune des phrases de « Haus » n'emploie le mot : elles " +
                "illustreraient autre chose",
            emploie
        )
    }
}
