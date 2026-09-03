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
