package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.carnet.SessionWidderhuelen
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Une carte du carnet peut-elle devenir une carte de révision ?
 *
 * La révision ne lit aucun actif à elle : elle se sert de ce que le carnet
 * affiche déjà, la glose de `luxemburgish_translations.json` et la phrase de
 * `luxemburgish_exemples.json`. C'est précisément ce qui la rend fragile en
 * silence. Une régénération qui perdrait les phrases d'exemple ferait basculer
 * **toutes** les cartes de production sur le repli français sans rien casser :
 * l'application tournerait, les sessions se joueraient, et la seule forme de
 * question qui fait produire l'orthographe aurait disparu.
 *
 * Les seuils sont sous les valeurs mesurées le 12 septembre 2026 sur les trois
 * viviers de contenu (3 246 formes des grilles de Kräizwuert et de Wuertplaz, et
 * des réponses de Wuertlück) : 97,3 % glosées, 89,1 % à glose instructive,
 * 97,2 % illustrées, 93,3 % dont la phrase peut être trouée. Ils laissent de la
 * marge à une régénération normale et ne survivent pas à un effondrement.
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

        fun autresFormes(mot: String): List<String> {
            val rep = representant(mot)
            return (listOf(rep) + (formesDe[rep] ?: emptyList())).distinct().filter { it != mot }
        }

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
        var trouables = 0
        var surLaFormeMeme = 0
        formes.forEach { forme ->
            val glose = dico.glose(forme)
            if (glose.isNotEmpty()) {
                glosees++
                if (instructive(forme, glose)) instructives++
            }
            val phrase = dico.premierExemple(forme)
            if (phrase != null) {
                illustrees++
                // La vraie question n'est pas « y a-t-il une phrase » mais
                // « peut-on la trouer » : c'est la fonction livrée qui répond,
                // rejouée ici sur les phrases réelles.
                val trouee = SessionWidderhuelen.phraseATrous(
                    phrase, forme, dico.autresFormes(forme)
                )
                if (trouee != null) {
                    trouables++
                    // Et la question la plus utile : le trou porte-t-il sur le
                    // mot de la carte, ou sur une forme sœur ? Le second cas
                    // reste une vraie question — la phrase réclame alors la
                    // forme qu'elle porte — mais il est bien plus fréquent que
                    // le taux de « trouables » ne le laisse croire, et c'est ce
                    // que ce compteur garde sous les yeux.
                    if (trouee.motMasque == forme) surLaFormeMeme++
                }
            }
        }

        val n = formes.size
        fun part(x: Int) = 100.0 * x / n
        assertTrue("glosées : ${part(glosees)} %", part(glosees) >= 93.0)
        assertTrue("gloses instructives : ${part(instructives)} %", part(instructives) >= 85.0)
        assertTrue("illustrées : ${part(illustrees)} %", part(illustrees) >= 93.0)
        assertTrue("phrases trouables : ${part(trouables)} %", part(trouables) >= 88.0)
        // Mesuré à l'écriture : 57 % des formes voient leur premier exemple
        // porter leur propre graphie ; pour le reste, la phrase illustre une
        // forme sœur et c'est elle que la question réclame. Le seuil est bas
        // exprès — il n'est pas là pour exiger mieux, mais pour signaler si la
        // proportion s'effondrait, auquel cas presque toutes les questions
        // porteraient sur un autre mot que celui de la carte.
        assertTrue(
            "trous portant sur la forme de la carte : ${part(surLaFormeMeme)} %",
            part(surLaFormeMeme) >= 45.0
        )
    }

    @Test
    fun `une phrase trouee ne laisse jamais le mot en clair`() {
        // Le défaut qu'un troage raté produirait : une question dont la réponse
        // est écrite dedans. Vérifié sur les phrases réelles des grilles, où
        // les répétitions et les mots composés sont fréquents.
        val dico = dictionnaire()
        val formes = formesDeGrilles("luxemburgish_crossword.json").take(400)
        var verifiees = 0
        formes.forEach { forme ->
            val phrase = dico.premierExemple(forme) ?: return@forEach
            val trouee = SessionWidderhuelen.phraseATrous(phrase, forme, emptyList())
                ?: return@forEach
            verifiees++
            val motsRestants = trouee.texte.split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
            assertTrue(
                "« $forme » reste en clair dans « ${trouee.texte} »",
                motsRestants.none { AccentTolerantMatcher.normalize(it) == AccentTolerantMatcher.normalize(forme) }
            )
        }
        assertTrue("aucune phrase vérifiée", verifiees > 100)
    }
}
