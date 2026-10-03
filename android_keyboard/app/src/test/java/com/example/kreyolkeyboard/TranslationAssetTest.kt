package com.example.kreyolkeyboard

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
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

    /** Les noms propres que l'actif livre, ceux que TranslationDictionary retire du tirage. */
    private fun nomsPropres(): Set<String> {
        val liste = charger().getJSONArray("noms_propres")
        return (0 until liste.length()).map { liste.getString(it) }.toHashSet()
    }

    /**
     * Les trois jeux qui tirent un mot ne tirent pas de noms propres.
     *
     * [TranslationDictionary.gloseInstructive] ne suffisait pas et c'est ce qui
     * a laissé passer le défaut : elle ne rejette qu'un mot glosé par
     * lui-même, donc elle attrape « Käerjeng » et laisse « Beetebuerg » →
     * Bettembourg, dont les deux graphies diffèrent. 757 des 19 350 formes
     * tirables étaient dans ce cas au 2026-09-07, et Wuertriet pouvait
     * demander « Athen » ou « Basel ».
     *
     * Le critère est le drapeau `NP` du LOD, porté par l'actif depuis le
     * 2026-10-03 : un mot est un nom propre quand **tous** les articles qui le
     * glosent en sont. Il a remplacé « toutes les acceptions en majuscule »,
     * qui ne valait qu'en français et écartait aussi « Fransous » ou « Staat ».
     * Les homographes restent : « Stroossen » est aussi le pluriel de rue.
     */
    @Test
    fun `les jeux ne tirent pas de noms propres`() {
        val table = charger().getJSONObject("translations")
        val duDictionnaire = dictionnaire().let { tableau ->
            (0 until tableau.length())
                .map { tableau.getJSONArray(it).getString(0) }
                .toHashSet()
        }

        val propresLivres = nomsPropres()
        for (attendu in listOf("Beetebuerg", "Houwald", "Miersch", "Frankräich", "José", "York")) {
            if (!table.has(attendu)) continue
            assertTrue("« $attendu » devrait être vu comme un nom propre", attendu in propresLivres)
        }
        for (garde in listOf("Haus", "Brout", "Aarbecht", "schaffen",
                             "Fransous", "Staat", "Internet", "Stroossen", "Polen")) {
            if (!table.has(garde)) continue
            assertFalse("« $garde » n'est pas un nom propre", garde in propresLivres)
        }

        var tirables = 0
        var propres = 0
        for (forme in table.keys()) {
            if (forme !in duDictionnaire) continue
            val glose = table.getString(forme)
            if (!instructive(forme, glose)) continue
            tirables++
            if (forme in propresLivres) propres++
        }
        // Le filtre vit dans TranslationDictionary, pas dans l'actif : la table
        // garde ses noms propres, un mot croisé ailleurs affiche toujours sa
        // glose. Ce test mesure donc ce que le filtre a à retirer, et échoue
        // si la population explosait au point de vider les viviers.
        assertTrue("aucune forme tirable", tirables > 10000)
        assertTrue(
            "$propres noms propres sur $tirables formes tirables : le filtre " +
                "de TranslationDictionary retirerait trop",
            propres * 10 < tirables
        )
    }

    @Test
    fun `le volume livre suffit aux trois jeux qui tirent des mots`() {
        val table = charger().getJSONObject("translations")
        assertTrue("table quasi vide : ${table.length()} entrées", table.length() >= 15000)

        // Le comptage se restreint aux formes du dictionnaire : les trois jeux
        // tirent leurs mots de `luxemburgish_dict.json`, et non des formes que
        // le LOD ajoute pour la seule complétion. Compter la table entière
        // annoncerait une réserve cinq fois plus large que celle qu'ils voient.
        // Les noms propres sont retirés du compte pour la même raison : depuis
        // le 2026-09-07 le filtre les écarte du tirage.
        val duDictionnaire = dictionnaire().let { tableau ->
            (0 until tableau.length())
                .map { tableau.getJSONArray(it).getString(0) }
                .toHashSet()
        }

        val propresLivres = nomsPropres()
        var wuertsich = 0   // grille 8x8 : 3 à 8 lettres
        var wuertmix = 0    // lettres mélangées : 4 à 10 lettres
        var wuertriet = 0   // wordle luxembourgeois : exactement 5 lettres
        for (forme in table.keys()) {
            if (forme !in duDictionnaire) continue
            if (!instructive(forme, table.getString(forme))) continue
            if (forme in propresLivres) continue
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
    fun `les cles sont des formes que le clavier connait`() {
        val table = charger().getJSONObject("translations")
        val formes = dictionnaire().let { tableau ->
            (0 until tableau.length())
                .map { tableau.getJSONArray(it).getString(0) }
                .toHashSet()
        }
        // Depuis 2026-09-02 la table glose aussi ce que le LOD apporte au
        // clavier par-dessus le corpus, sinon l'onglet Wierderbuch chercherait
        // dans 38 000 mots pendant que la complétion en connaît 123 000.
        val fichierLod = File("src/main/assets/luxemburgish_lod_forms.json")
        if (fichierLod.exists()) {
            val suggest = JSONObject(fichierLod.readText()).getJSONArray("suggest")
            (0 until suggest.length()).forEach { formes.add(suggest.getString(it)) }
        }

        val intruses = table.keys().asSequence().filterNot { formes.contains(it) }.take(5).toList()
        // Le script n'écrit que des formes lues dans l'un des deux actifs : une
        // seule intruse signale qu'ils ont été régénérés séparément.
        assertTrue(
            "clés absentes du dictionnaire et des formes LOD : $intruses",
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

    /**
     * Les deux jeux à grilles n'y casent aucun nom propre du LOD.
     *
     * Les grilles sont construites hors ligne par `generate_crossword.py` et
     * `generate_chassecroise.py`, qui lisent la même liste `noms_propres` que
     * l'application. Une grille régénérée avec un actif qui l'aurait perdue
     * passerait tous les autres tests et caserait de nouveau des communes.
     */
    @Test
    fun `aucune grille ne case un nom propre du LOD`() {
        val propres = nomsPropres()
        assertTrue("liste des noms propres quasi vide : ${propres.size}", propres.size >= 1000)
        for (actif in listOf("luxemburgish_crossword.json", "luxemburgish_chassecroise.json")) {
            val grilles = JSONObject(File("src/main/assets/$actif").readText()).getJSONArray("grilles")
            val fautifs = sortedSetOf<String>()
            for (i in 0 until grilles.length()) {
                val mots = grilles.getJSONObject(i).getJSONArray("mots")
                for (j in 0 until mots.length()) {
                    val forme = mots.getJSONObject(j).getString("f")
                    if (forme in propres || forme.lowercase() in propres) fautifs.add(forme)
                }
            }
            assertTrue("$actif case des noms propres : $fautifs", fautifs.isEmpty())
        }
    }
}
