package com.example.kreyolkeyboard

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Une carte du carnet peut-elle devenir une carte de révision ?
 *
 * La révision ne lit aucun actif à elle : la carte qu'elle révèle affiche la
 * glose de `luxemburgish_translations.json` et la phrase de
 * `luxemburgish_exemples.json`. C'est précisément ce qui la rend fragile en
 * silence : une régénération qui perdrait l'une ou l'autre retournerait des
 * cartes muettes sans rien casser.
 *
 * Les seuils sont sous les valeurs mesurées le 12 septembre 2026 sur les trois
 * viviers de contenu (3 246 formes des grilles de Kräizwuert et de Wuertplaz, et
 * des réponses de Wuertlück) : 97,3 % glosées, 89,1 % à glose instructive,
 * 97,2 % illustrées. Ils laissent de la marge à une régénération normale et ne
 * survivent pas à un effondrement.
 */
class CarnetRevisionAssetTest {

    private fun actif(nom: String): JSONObject {
        val fichier = File("src/main/assets/$nom")
        assertTrue("$nom manquant", fichier.exists())
        return JSONObject(fichier.readText())
    }

    /** Les formes des grilles livrées, celles que le carnet reçoit. */
    private fun formesDeGrilles(nom: String): Set<String> {
        val grilles = actif(nom).getJSONArray("grilles")
        val formes = HashSet<String>()
        for (i in 0 until grilles.length()) {
            val mots = grilles.getJSONObject(i).getJSONArray("mots")
            for (j in 0 until mots.length()) formes.add(mots.getJSONObject(j).getString("f"))
        }
        return formes
    }

    private fun reponsesDeWuertlueck(): Set<String> {
        val items = actif("luxemburgish_cloze.json").getJSONArray("items")
        val formes = HashSet<String>()
        for (i in 0 until items.length()) formes.add(items.getJSONObject(i).getString("a"))
        return formes
    }

    /**
     * La fiche d'une forme, telle que `TranslationDictionary.fiche` la rend :
     * le représentant de la famille, la glose, et les autres formes.
     *
     * Rejouée ici sur les actifs plutôt qu'appelée : `TranslationDictionary`
     * demande un `Context`, et il n'y a pas d'`androidTest/` dans ce dépôt.
     */
    private class Dictionnaire {
        val traductions = HashMap<String, String>()
        val traductionsMinuscules = HashMap<String, String>()
        val representantDe = HashMap<String, String>()
        val formesDe = HashMap<String, List<String>>()
        val exemples = HashMap<String, List<String>>()

        fun glose(mot: String): String {
            val rep = representantDe[mot] ?: representantDe[mot.lowercase()] ?: mot
            return traductions[mot]
                ?: traductionsMinuscules[mot.lowercase()]
                ?: traductions[rep]
                ?: ""
        }

        fun representant(mot: String): String =
            representantDe[mot] ?: representantDe[mot.lowercase()] ?: mot

        fun premierExemple(mot: String): String? {
            val rep = representant(mot)
            return (exemples[rep] ?: exemples[mot])?.firstOrNull()
        }
    }

    private fun dictionnaire(): Dictionnaire {
        val d = Dictionnaire()
        val trad = actif("luxemburgish_translations.json").getJSONObject("translations")
        trad.keys().forEach { forme ->
            val glose = trad.getString(forme)
            d.traductions[forme] = glose
            d.traductionsMinuscules.putIfAbsent(forme.lowercase(), glose)
        }
        val familles = actif("luxemburgish_familles.json").getJSONObject("familles")
        familles.keys().forEach { rep ->
            val formes = familles.getString(rep).split(" ").filter { it.isNotEmpty() }
            d.formesDe[rep] = formes
            (listOf(rep) + formes).forEach { d.representantDe.putIfAbsent(it, rep) }
        }
        val exemples = actif("luxemburgish_exemples.json").getJSONObject("exemples")
        exemples.keys().forEach { mot ->
            val phrases = exemples.getJSONArray(mot)
            d.exemples[mot] = (0 until phrases.length()).map { phrases.getString(it) }
        }
        return d
    }

    /** La glose apprend-elle quelque chose ? Même règle que `gloseInstructive`. */
    private fun instructive(mot: String, glose: String): Boolean {
        val plie = AccentTolerantMatcher.normalize(mot)
        return glose.split(",").any { AccentTolerantMatcher.normalize(it.trim()) != plie }
    }

    @Test
    fun `les cartes que les jeux versent au carnet sont revisables`() {
        val dico = dictionnaire()
        val formes = formesDeGrilles("luxemburgish_crossword.json") +
            formesDeGrilles("luxemburgish_chassecroise.json") +
            reponsesDeWuertlueck()
        assertTrue("vivier anormalement petit : ${formes.size}", formes.size >= 2500)

        var glosees = 0
        var instructives = 0
        var illustrees = 0
        formes.forEach { forme ->
            val glose = dico.glose(forme)
            if (glose.isNotEmpty()) {
                glosees++
                if (instructive(forme, glose)) instructives++
            }
            if (dico.premierExemple(forme) != null) illustrees++
        }

        val n = formes.size
        fun part(x: Int) = 100.0 * x / n
        assertTrue("glosées : ${part(glosees)} %", part(glosees) >= 93.0)
        assertTrue("gloses instructives : ${part(instructives)} %", part(instructives) >= 85.0)
        assertTrue("illustrées : ${part(illustrees)} %", part(illustrees) >= 93.0)
    }
}
