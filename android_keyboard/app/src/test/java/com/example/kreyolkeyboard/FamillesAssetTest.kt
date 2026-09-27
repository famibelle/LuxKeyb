package com.example.kreyolkeyboard

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contrôles des familles de formes (`luxemburgish_familles.json`).
 *
 * L'actif est produit hors du build par `Dictionnaires/generate_translations.py`
 * en même temps que les traductions, et il regroupe sous un représentant les
 * formes qui mènent au même article du LOD. C'est lui qui fait qu'une recherche
 * de « manger » rend neuf mots et non quarante flexions.
 *
 * Trois régressions passeraient inaperçues, parce qu'aucune ne fait planter :
 *
 * - un fichier absent ou vide ramène le Wierderbuch à son ancien
 *   comportement — `TranslationDictionary.chargerFamilles` avale l'erreur
 *   exprès, pour qu'une famille manquante n'éteigne pas la recherche ;
 * - un représentant qui ne serait pas glosé afficherait une ligne sans
 *   traduction, la recherche montrant la glose du représentant ;
 * - une forme rattachée à deux familles ferait dépendre le résultat de l'ordre
 *   de lecture du fichier.
 */
class FamillesAssetTest {

    private fun familles(): JSONObject {
        val fichier = File("src/main/assets/luxemburgish_familles.json")
        assertTrue(
            "luxemburgish_familles.json manquant — lancez " +
                "Dictionnaires/generate_translations.py",
            fichier.exists()
        )
        return JSONObject(fichier.readText()).getJSONObject("familles")
    }

    private fun traductions(): JSONObject {
        val fichier = File("src/main/assets/luxemburgish_translations.json")
        assertTrue("luxemburgish_translations.json manquant", fichier.exists())
        return JSONObject(fichier.readText()).getJSONObject("translations")
    }

    @Test
    fun leRegroupementResteMassif() {
        val familles = familles()
        assertTrue(
            "Seulement ${familles.length()} familles : le regroupement du " +
                "Wierderbuch serait inopérant et la liste répéterait les flexions",
            familles.length() >= 10_000
        )
    }

    @Test
    fun chaqueFormeEstGloseeEtNAppartientQuAUneFamille() {
        val familles = familles()
        val traductions = traductions()
        val vues = HashMap<String, String>()

        val cles = familles.keys()
        while (cles.hasNext()) {
            val representant = cles.next()
            assertTrue(
                "Le représentant « $representant » n'est pas glosé : sa ligne " +
                    "s'afficherait sans traduction",
                traductions.has(representant)
            )
            val autres = familles.getString(representant)
                .split(" ").filter { it.isNotEmpty() }
            assertTrue(
                "La famille de « $representant » n'a aucune autre forme",
                autres.isNotEmpty()
            )
            for (forme in autres) {
                assertTrue(
                    "La forme « $forme » de la famille « $representant » n'est " +
                        "pas glosée",
                    traductions.has(forme)
                )
                val deja = vues.put(forme, representant)
                assertEquals(
                    "La forme « $forme » appartient à deux familles",
                    null, deja
                )
                assertTrue(
                    "« $forme » est aussi représentant d'une autre famille",
                    !familles.has(forme)
                )
            }
        }
    }

    /**
     * Le cas qui a motivé le regroupement : le singulier et le pluriel de
     * « fourchette » se suivaient dans la liste, glosés à l'identique.
     */
    @Test
    fun leSingulierEtLePlurielSontUnSeulMot() {
        val familles = familles()
        assertTrue(
            "« Forschett » n'a plus de famille : « Forschetten » redeviendrait " +
                "une ligne à part",
            familles.optString("Forschett", "").split(" ").contains("Forschetten")
        )
    }
}
