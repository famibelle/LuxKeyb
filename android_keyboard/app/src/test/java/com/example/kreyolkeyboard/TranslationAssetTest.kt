package com.example.kreyolkeyboard

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contrôles de la table des traductions livrée (`luxemburgish_translations.json`).
 *
 * L'actif est produit hors du build par `Dictionnaires/generate_translations.py`
 * à partir du Lëtzebuerger Online Dictionnaire. Deux régressions passeraient
 * autrement inaperçues, parce qu'aucune ne fait planter quoi que ce soit :
 *
 * - une table vide ou effondrée n'éteint pas les jeux, elle les fait retomber
 *   sur le repli de `TranslationDictionary.filtrerMotsTraduits` — le jeu tourne,
 *   simplement plus rien n'est traduit, ce qui est exactement le défaut qu'on
 *   corrigeait ;
 * - une table dont les clés ne seraient plus celles du dictionnaire livré
 *   (casse recanonisée, formes lemmatisées) rendrait chaque recherche vaine
 *   tout en gardant un fichier de bonne taille.
 *
 * Le fichier étant versionné, son absence est une régression et non une
 * configuration locale : ce test échoue au lieu de se taire.
 */
class TranslationAssetTest {

    private fun charger(): JSONObject {
        val fichier = File("src/main/assets/luxemburgish_translations.json")
        assertTrue(
            "luxemburgish_translations.json manquant — lancez " +
                "Dictionnaires/generate_translations.py",
            fichier.exists()
        )
        return JSONObject(fichier.readText())
    }

    private fun dictionnaire(): JSONArray {
        val fichier = File("src/main/assets/luxemburgish_dict.json")
        assertTrue("luxemburgish_dict.json manquant", fichier.exists())
        return JSONArray(fichier.readText())
    }

    @Test
    fun `les emprunts gloses par eux-memes restent minoritaires`() {
        val table = charger().getJSONObject("translations")
        val repetitives = table.keys().asSequence()
            .count { !instructive(it, table.getString(it)) }
        // 1 278 sur 20 604 au moment de l'écriture. Le luxembourgeois emprunte
        // au français, donc ce nombre ne sera jamais nul ; s'il explosait, ce
        // serait le signe que l'appariement renvoie la forme au lieu du sens.
        assertTrue(
            "$repetitives gloses sur ${table.length()} ne font que répéter le mot",
            repetitives * 4 < table.length()
        )
    }

    @Test
    fun `l'actif cite le LOD`() {
        val racine = charger()
        val attribution = racine.getJSONArray("attribution")
        val texte = (0 until attribution.length())
            .joinToString(" ") { attribution.getString(it) }
        // Le LOD est en CC0 : citer le ZLS n'est pas une obligation de licence,
        // c'est la contrepartie qu'on s'impose pour republier leur travail.
        assertTrue("le ZLS n'est pas cité", texte.contains("Zenter fir d'Lëtzebuerger Sprooch"))
        assertTrue("licence non déclarée", racine.getString("licence").contains("CC0"))
    }

    /**
     * Même règle que `TranslationDictionary.gloseInstructive` : une glose qui
     * répète le mot n'alimente pas le tirage des jeux. Compter sans elle
     * donnerait une réserve plus large que celle que le jeu voit réellement.
     */
    private fun instructive(forme: String, glose: String): Boolean {
        val motPlie = AccentTolerantMatcher.normalize(forme)
        return glose.split(",").any { AccentTolerantMatcher.normalize(it.trim()) != motPlie }
    }

    @Test
    fun `le volume livre suffit aux trois jeux qui tirent des mots`() {
        val table = charger().getJSONObject("translations")
        assertTrue("table quasi vide : ${table.length()} entrées", table.length() >= 15000)

        var wuertsich = 0   // grille 8x8 : 3 à 8 lettres
        var wuertmix = 0    // lettres mélangées : 4 à 10 lettres
        var wuertriet = 0   // wordle luxembourgeois : exactement 5 lettres
        for (forme in table.keys()) {
            if (!instructive(forme, table.getString(forme))) continue
            if (forme.length in 3..8) wuertsich++
            if (forme.length in 4..10) wuertmix++
            if (forme.length == 5 && forme.all { it.isLetter() }) wuertriet++
        }

        // Wuertriet est la réserve la plus étroite : il n'accepte qu'une seule
        // longueur. C'est elle qui décide si le filtre par la traduction est
        // tenable, et c'est donc elle qu'il faut surveiller.
        assertTrue("réserve Wuertsich trop maigre : $wuertsich", wuertsich >= 3000)
        assertTrue("réserve Wuertmix trop maigre : $wuertmix", wuertmix >= 3000)
        assertTrue("réserve Wuertriet trop maigre : $wuertriet", wuertriet >= 300)
    }

    @Test
    fun `les cles sont des formes du dictionnaire livre`() {
        val table = charger().getJSONObject("translations")
        val formes = dictionnaire().let { tableau ->
            (0 until tableau.length())
                .map { tableau.getJSONArray(it).getString(0) }
                .toHashSet()
        }

        val intruses = table.keys().asSequence().filterNot { formes.contains(it) }.take(5).toList()
        // Le script n'écrit que des formes lues dans le dictionnaire : une seule
        // intruse signale que les deux actifs ont été régénérés séparément.
        assertTrue(
            "clés absentes du dictionnaire : $intruses",
            intruses.isEmpty()
        )
    }

    @Test
    fun `les mots les plus courants sont gloses`() {
        val table = charger().getJSONObject("translations")
        val tableau = dictionnaire()
        // Le millier de formes les plus fréquentes est ce que les jeux tirent le
        // plus souvent. En couvrir moins de la moitié signifie que l'appariement
        // avec le LOD s'est cassé quelque part, même si le fichier reste gros.
        val cent = (0 until minOf(1000, tableau.length()))
            .map { tableau.getJSONArray(it).getString(0) }
        val gloses = cent.count { table.has(it) || table.has(it.lowercase()) }
        assertTrue(
            "seules $gloses des ${cent.size} formes les plus fréquentes sont glosées",
            gloses >= cent.size / 2
        )
    }
}
