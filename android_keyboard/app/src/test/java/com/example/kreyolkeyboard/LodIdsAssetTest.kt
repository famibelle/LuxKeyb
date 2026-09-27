package com.example.kreyolkeyboard

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contrôles de la table des articles du LOD (`luxemburgish_lod_ids.json`).
 *
 * Elle sert un seul bouton — « Voir sur le dictionnaire officiel » — mais ce
 * bouton n'a pas d'autre issue : la route de recherche de lod.lu,
 * `/sich/<langue>/<mot>`, ne fonctionne pas à l'ouverture à froid, son
 * composant émettant sa requête sur un bus d'événements avant que rien
 * n'écoute. Seule `/artikel/<id>` arrive sur l'article, et l'identifiant ne
 * se devine pas.
 *
 * Trois régressions passeraient sans rien casser à la compilation :
 *
 * - un fichier absent ou vide fait retomber chaque mot sur la recherche, donc
 *   sur la page « proposez ce mot » : le bouton ne mène plus nulle part, et
 *   `chargerArticles` avale l'erreur exprès pour ne pas éteindre la fiche ;
 * - une clé qui ne serait pas une forme que la recherche affiche porterait un
 *   identifiant que personne n'atteindrait — la table est indexée par le
 *   représentant de famille, comme les exemples ;
 * - un identifiant hors du format du LOD (majuscules sans accents, suivies
 *   d'un rang) produirait une URL valide menant à une page d'erreur.
 */
class LodIdsAssetTest {

    private fun articles(): JSONObject {
        val fichier = File("src/main/assets/luxemburgish_lod_ids.json")
        assertTrue(
            "luxemburgish_lod_ids.json manquant — lancez " +
                "Dictionnaires/generate_translations.py",
            fichier.exists()
        )
        return JSONObject(fichier.readText()).getJSONObject("articles")
    }

    private fun table(nom: String, champ: String): JSONObject {
        val fichier = File("src/main/assets/$nom")
        assertTrue("$nom manquant", fichier.exists())
        return JSONObject(fichier.readText()).getJSONObject(champ)
    }

    @Test
    fun laCouvertureResteMassive() {
        val articles = articles()
        assertTrue(
            "Seulement ${articles.length()} articles indexés : le bouton " +
                "lod.lu retomberait sur une recherche qui ne donne rien",
            articles.length() >= 20_000
        )
    }

    /**
     * Même contrat que les exemples : la fiche passe `Resultat.mot`, qui est
     * le représentant de la famille, jamais une flexion.
     */
    @Test
    fun chaqueCleEstUnMotAffichable() {
        val articles = articles()
        val traductions = table("luxemburgish_translations.json", "translations")
        val familles = table("luxemburgish_familles.json", "familles")
        val flexions = HashSet<String>()
        val representants = familles.keys()
        while (representants.hasNext()) {
            familles.getString(representants.next())
                .split(" ").filterTo(flexions) { it.isNotEmpty() }
        }

        val cles = articles.keys()
        while (cles.hasNext()) {
            val mot = cles.next()
            assertTrue(
                "« $mot » porte un identifiant mais n'est pas glosé : la " +
                    "fiche ne s'ouvre jamais sur lui",
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
     * Le format du LOD : le lemme plié en majuscules sans accent ni signe,
     * suivi du rang qui départage les homographes (`HAUS1`, `AACHT2`).
     */
    @Test
    fun lesIdentifiantsOntLeFormatDuLod() {
        val articles = articles()
        val attendu = Regex("^[A-Z0-9]+[0-9]$")
        val cles = articles.keys()
        while (cles.hasNext()) {
            val mot = cles.next()
            val identifiant = articles.getString(mot)
            assertTrue(
                "« $mot » → « $identifiant » n'a pas le format d'un article " +
                    "du LOD : l'URL mènerait à une page d'erreur",
                attendu.matches(identifiant)
            )
        }
    }

    /**
     * Le cas qui a motivé la table : un utilisateur a signalé que le bouton
     * menait à « proposez ce mot » pour `Mëttwochowend`. Le mot est pourtant
     * dans le LOD, sous cet identifiant-là.
     */
    @Test
    fun lesMotsSignalesOntBienLeurArticle() {
        val articles = articles()
        for ((mot, identifiant) in listOf(
            "Mëttwochowend" to "METTWOCHOWEND1",
            "Haus" to "HAUS1"
        )) {
            assertTrue(
                "« $mot » n'a plus d'identifiant d'article",
                articles.has(mot)
            )
            assertTrue(
                "« $mot » pointe sur « ${articles.getString(mot)} » au lieu " +
                    "de « $identifiant »",
                articles.getString(mot) == identifiant
            )
        }
    }
}
