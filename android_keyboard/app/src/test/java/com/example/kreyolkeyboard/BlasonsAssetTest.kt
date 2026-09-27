package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.carnet.Armorial
import com.example.kreyolkeyboard.carnet.Champ
import com.example.kreyolkeyboard.carnet.Meubles
import com.example.kreyolkeyboard.carnet.Nature
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contrôles du classement des blasons (`luxemburgish_blasons.json`).
 *
 * L'actif est produit hors du build par `Dictionnaires/generate_blasons.py` et
 * il décide de la teinte de sept cartes sur dix, plus du dessin de trois sur
 * cent. Quatre régressions passeraient inaperçues, parce qu'aucune ne fait
 * planter — `Armorial` avale exprès toute erreur de lecture pour qu'un actif
 * abîmé n'éteigne pas le carnet :
 *
 * - un **champ inconnu** dans l'actif rendrait la carte à sa teinte de lettres,
 *   silencieusement, et on croirait le classement incomplet plutôt que faux ;
 * - un **meuble non dessiné** donnerait une fenêtre qui retombe sur le tracé —
 *   invisible à la relecture du seul JSON, et c'est exactement l'erreur qu'on
 *   fait en renommant une silhouette ;
 * - un **meuble attribué à un mot qu'aucun jeu ne donne** ne se verrait jamais :
 *   aucune carte ne peut porter ce lemme, et le dessin serait du travail perdu ;
 * - une **couverture qui s'effondre** — un lexique cassé rend un actif
 *   parfaitement valide et presque vide.
 */
class BlasonsAssetTest {

    private fun actif(nom: String): JSONObject {
        val fichier = File("src/main/assets/$nom")
        assertTrue(
            "$nom manquant — lancez Dictionnaires/generate_blasons.py",
            fichier.exists()
        )
        return JSONObject(fichier.readText())
    }

    private fun blasons(): JSONObject =
        actif("luxemburgish_blasons.json").getJSONObject("blasons")

    /**
     * Les lemmes qu'un jeu peut réellement donner en carte.
     *
     * Les deux jeux de grilles **et** le vivier de Wuertlück : `Brëll`,
     * `Fändel`, `Helikopter`, `Kierch`, `Krunn` et `Pilier` ne sont dans aucune
     * grille et n'arrivent que par un texte à trous, ce qui en fait des cartes
     * aussi valables que les autres.
     */
    private fun lemmesJouables(): Set<String> {
        val representant = HashMap<String, String>()
        val familles = actif("luxemburgish_familles.json").getJSONObject("familles")
        for (tete in familles.keys()) {
            representant[tete] = tete
            for (forme in familles.getString(tete).split(" ")) {
                if (forme.isNotEmpty()) representant.putIfAbsent(forme, tete)
            }
        }
        val lemmes = HashSet<String>()
        for (nom in listOf("luxemburgish_crossword.json", "luxemburgish_chassecroise.json")) {
            val grilles = actif(nom).getJSONArray("grilles")
            for (i in 0 until grilles.length()) {
                val mots = grilles.getJSONObject(i).getJSONArray("mots")
                for (j in 0 until mots.length()) {
                    val forme = mots.getJSONObject(j).getString("f")
                    lemmes.add(representant[forme] ?: representant[forme.lowercase()] ?: forme)
                }
            }
        }
        val items = actif("luxemburgish_cloze.json").getJSONArray("items")
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val mots = mutableListOf(item.getString("a"))
            val leurres = item.optJSONArray("d")
            if (leurres != null) for (j in 0 until leurres.length()) mots.add(leurres.getString(j))
            for (forme in mots) {
                lemmes.add(representant[forme] ?: representant[forme.lowercase()] ?: forme)
            }
        }
        return lemmes
    }

    @Test
    fun `les huit champs de l'actif sont ceux du code`() {
        val declares = actif("luxemburgish_blasons.json").getJSONArray("champs")
        val vus = (0 until declares.length()).map { declares.getString(it) }.toSet()
        assertEquals(
            "l'actif et l'énumération Champ ne listent pas les mêmes champs",
            Champ.values().map { it.id }.toSet(),
            vus
        )
    }

    @Test
    fun `tout champ attribué est un champ connu`() {
        val blasons = blasons()
        for (lemme in blasons.keys()) {
            val id = blasons.getJSONObject(lemme).optString("c")
            if (id.isEmpty()) continue
            assertTrue(
                "champ inconnu « $id » sur « $lemme »",
                Champ.parId(id) != null
            )
        }
    }

    @Test
    fun `tout meuble attribué est dessiné dans Meubles`() {
        val blasons = blasons()
        val dessines = Meubles.noms()
        for (lemme in blasons.keys()) {
            val meuble = blasons.getJSONObject(lemme).optString("m")
            if (meuble.isEmpty()) continue
            assertTrue(
                "« $lemme » porte le meuble « $meuble », que Meubles.kt ne dessine pas",
                meuble in dessines
            )
        }
    }

    @Test
    fun `tout meuble attribué peut réellement sortir d'un jeu`() {
        val blasons = blasons()
        val jouables = lemmesJouables()
        for (lemme in blasons.keys()) {
            if (blasons.getJSONObject(lemme).optString("m").isEmpty()) continue
            assertTrue(
                "« $lemme » est enluminé mais aucun jeu ne le donne : " +
                    "aucune carte ne pourra le porter",
                lemme in jouables
            )
        }
    }

    @Test
    fun `le classement couvre encore la majorité du carnet`() {
        val blasons = blasons()
        val jouables = lemmesJouables()
        val classes = jouables.count { blasons.optJSONObject(it)?.optString("c")?.isNotEmpty() == true }
        // Mesuré à 71 % des 2 798 emplacements du carnet au moment de l'écriture ;
        // le seuil est bas exprès, il garde contre l'effondrement, pas contre
        // l'ajustement du lexique.
        assertTrue(
            "couverture tombée à ${100 * classes / jouables.size} % des lemmes jouables",
            classes * 100 / jouables.size >= 55
        )
    }

    @Test
    fun `la partition lit la nature sur la forme seule`() {
        // La majuscule est l'étiquette du substantif en luxembourgeois.
        assertEquals(Nature.NOM, Armorial.natureDe("Haus"))
        assertEquals(Nature.NOM, Armorial.natureDe("Männer"))
        // L'infinitif régulier se termine en -en.
        assertEquals(Nature.VERBE, Armorial.natureDe("verdéngen"))
        // Les irréguliers en -nn sont rattrapés…
        assertEquals(Nature.VERBE, Armorial.natureDe("sinn"))
        assertEquals(Nature.VERBE, Armorial.natureDe("gesinn"))
        // …mais pas les trois adverbes qui portent la même finale.
        assertEquals(Nature.AUTRE, Armorial.natureDe("schonn"))
        assertEquals(Nature.AUTRE, Armorial.natureDe("dann"))
        assertEquals(Nature.AUTRE, Armorial.natureDe("geschwënn"))
        // Le reste ne prétend rien.
        assertEquals(Nature.AUTRE, Armorial.natureDe("direkt"))
        assertEquals(Nature.AUTRE, Armorial.natureDe(""))
    }
}
