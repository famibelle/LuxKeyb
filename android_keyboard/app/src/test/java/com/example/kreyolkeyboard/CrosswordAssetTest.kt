package com.example.kreyolkeyboard

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contrôles des grilles de mots croisés livrées (« Kräizwuert »).
 *
 * L'actif est produit hors du build par `Dictionnaires/generate_crossword.py`,
 * donc rien dans la compilation ne garantit sa forme. Les défauts d'une grille
 * ne se voient pas à l'œil sur un fichier JSON valide : deux mots parallèles
 * collés fabriquent une colonne que personne n'a écrite, un croisement
 * contradictoire rend une grille insoluble, une définition qui contient sa
 * réponse en fait un cadeau. Tout cela s'affiche normalement.
 *
 * Ce test rejoue donc côté Kotlin la validation que le script fait côté Python.
 * La redondance est voulue : c'est la seule barrière qui reste si quelqu'un
 * régénère l'actif avec un script modifié.
 */
class CrosswordAssetTest {

    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZÄËÉÖÜ".toSet()

    private fun charger(): JSONObject {
        val fichier = File("src/main/assets/luxemburgish_crossword.json")
        assertTrue(
            "luxemburgish_crossword.json manquant — lancez " +
                "Dictionnaires/generate_crossword.py",
            fichier.exists()
        )
        return JSONObject(fichier.readText())
    }

    private fun grilles(): JSONArray = charger().getJSONArray("grilles")

    /** Les cases écrites d'une grille : (ligne, colonne) → lettre. */
    private fun cases(grille: JSONObject): Map<Pair<Int, Int>, Char> {
        val table = HashMap<Pair<Int, Int>, Char>()
        val mots = grille.getJSONArray("mots")
        for (i in 0 until mots.length()) {
            val mot = mots.getJSONObject(i)
            val texte = mot.getString("m")
            val horizontal = mot.getString("d") == "H"
            for (j in texte.indices) {
                val ligne = if (horizontal) mot.getInt("r") else mot.getInt("r") + j
                val colonne = if (horizontal) mot.getInt("c") + j else mot.getInt("c")
                val presente = table[ligne to colonne]
                assertTrue(
                    "croisement contradictoire en ($ligne,$colonne) : " +
                        "$presente puis ${texte[j]}",
                    presente == null || presente == texte[j]
                )
                table[ligne to colonne] = texte[j]
            }
        }
        return table
    }

    @Test
    fun `l'actif porte l'attribution de ses sources`() {
        val sources = charger().getJSONArray("attribution")
        val texte = (0 until sources.length()).joinToString(" ") { sources.getString(it) }
        // Les définitions viennent du LOD (CC0, le ZLS est cité par courtoisie),
        // les mots des deux corpus dont la citation est une obligation de
        // licence — CC BY-NC pour LuxAlign, CC BY pour LETZ.
        assertTrue("LOD non cité", texte.contains("LOD"))
        assertTrue("LuxAlign non cité", texte.contains("LuxAlign"))
        assertTrue("LETZ non cité", texte.contains("LETZ"))
    }

    @Test
    fun `le volume livre couvre les trois difficultes`() {
        val grilles = grilles()
        assertTrue("moins de 250 grilles livrées : ${grilles.length()}", grilles.length() >= 250)

        val parNiveau = mutableMapOf<Int, Int>()
        for (i in 0 until grilles.length()) {
            val niveau = grilles.getJSONObject(i).getInt("l")
            parNiveau[niveau] = (parNiveau[niveau] ?: 0) + 1
        }
        for (niveau in 1..3) {
            assertTrue(
                "difficulté $niveau presque vide : ${parNiveau[niveau] ?: 0} grilles",
                (parNiveau[niveau] ?: 0) >= 80
            )
        }
    }

    @Test
    fun `chaque grille est jouable`() {
        val grilles = grilles()
        for (i in 0 until grilles.length()) {
            val grille = grilles.getJSONObject(i)
            val mots = grille.getJSONArray("mots")
            assertTrue("grille #$i : ${mots.length()} mots seulement", mots.length() >= 6)

            val vus = mutableSetOf<String>()
            for (j in 0 until mots.length()) {
                val mot = mots.getJSONObject(j)
                val reponse = mot.getString("m")
                val canonique = mot.getString("f")
                val definition = mot.getString("g")

                assertTrue("grille #$i : « $reponse » trop court", reponse.length >= 3)
                assertTrue(
                    "grille #$i : « $reponse » sort de l'alphabet du pavé",
                    reponse.all { it in alphabet }
                )
                // La grille est en capitales, la forme canonique est ce que le
                // jeu enseigne : les deux doivent désigner le même mot, sans
                // quoi le message de félicitation apprendrait autre chose que
                // ce qui vient d'être écrit.
                assertEquals(
                    "grille #$i : « $reponse » ne majuscule pas « $canonique »",
                    canonique.uppercase(), reponse
                )
                assertTrue("grille #$i : « $reponse » sans définition", definition.isNotBlank())
                assertTrue(
                    "grille #$i : la définition de « $reponse » la contient — $definition",
                    !contientLeMot(definition, canonique)
                )
                assertTrue("grille #$i : « $reponse » posé deux fois", vus.add(reponse))

                val horizontal = mot.getString("d") == "H"
                val finLigne = mot.getInt("r") + if (horizontal) 0 else reponse.length - 1
                val finColonne = mot.getInt("c") + if (horizontal) reponse.length - 1 else 0
                assertTrue(
                    "grille #$i : « $reponse » déborde de la grille",
                    mot.getInt("r") >= 0 && mot.getInt("c") >= 0 &&
                        finLigne < grille.getInt("h") && finColonne < grille.getInt("w")
                )
            }
        }
    }

    /**
     * L'invariant central : rien ne se lit dans une grille qui ne soit un mot
     * posé.
     *
     * Deux mots parallèles collés produisent, dans l'autre sens, des paires de
     * lettres que le joueur lit comme des mots — et il les corrige, croyant
     * s'être trompé. Rien d'autre ne le signalerait : la grille est valide, les
     * définitions sont bonnes, les croisements concordent.
     */
    @Test
    fun `toute suite de deux lettres est un mot pose`() {
        val grilles = grilles()
        for (i in 0 until grilles.length()) {
            val grille = grilles.getJSONObject(i)
            val table = cases(grille)
            val mots = grille.getJSONArray("mots")
            val poses = (0 until mots.length())
                .map { mots.getJSONObject(it).getString("m") }
                .groupingBy { it }.eachCount()

            val lues = ArrayList<String>()
            for (horizontal in listOf(true, false)) {
                val externes = if (horizontal) grille.getInt("h") else grille.getInt("w")
                val internes = if (horizontal) grille.getInt("w") else grille.getInt("h")
                for (a in 0 until externes) {
                    val courant = StringBuilder()
                    for (b in 0..internes) {
                        val lettre = if (b == internes) null
                        else table[if (horizontal) a to b else b to a]
                        if (lettre != null) {
                            courant.append(lettre)
                        } else {
                            if (courant.length >= 2) lues.add(courant.toString())
                            courant.setLength(0)
                        }
                    }
                }
            }

            assertEquals(
                "grille #$i : ce qui se lit dans la grille ne correspond pas aux " +
                    "mots posés",
                poses, lues.groupingBy { it }.eachCount()
            )
        }
    }

    /**
     * Une grille doit être d'un seul tenant. Deux blocs séparés sont deux
     * demi-grilles : les lettres trouvées dans l'un n'aident jamais dans
     * l'autre, ce qui est précisément ce qu'on attend de mots croisés.
     */
    @Test
    fun `chaque grille est d'un seul tenant`() {
        val grilles = grilles()
        for (i in 0 until grilles.length()) {
            val mots = grilles.getJSONObject(i).getJSONArray("mots")
            val emprises = (0 until mots.length()).map { j ->
                val mot = mots.getJSONObject(j)
                val horizontal = mot.getString("d") == "H"
                (mot.getString("m").indices).map { k ->
                    if (horizontal) mot.getInt("r") to mot.getInt("c") + k
                    else mot.getInt("r") + k to mot.getInt("c")
                }.toSet()
            }

            val atteints = mutableSetOf(0)
            val frontiere = ArrayDeque(listOf(0))
            while (frontiere.isNotEmpty()) {
                val courant = frontiere.removeLast()
                emprises.indices.forEach { autre ->
                    if (autre !in atteints && emprises[courant].any { it in emprises[autre] }) {
                        atteints.add(autre)
                        frontiere.addLast(autre)
                    }
                }
            }
            assertEquals(
                "grille #$i : la grille est en plusieurs morceaux",
                emprises.size, atteints.size
            )
        }
    }

    /**
     * Les réponses sont des formes du dictionnaire livré, casse comprise.
     *
     * C'est la même exigence que pour Wuertlück : proposer « joer » là où le
     * corpus écrit « Joer » apprendrait une faute, et c'est ici plus grave
     * puisque le jeu affiche cette forme comme la bonne orthographe.
     */
    @Test
    fun `les reponses sont des mots du dictionnaire livre`() {
        val dictionnaire = File("src/main/assets/luxemburgish_dict.json")
        assertTrue("luxemburgish_dict.json manquant", dictionnaire.exists())
        val formes = JSONArray(dictionnaire.readText()).let { tableau ->
            (0 until tableau.length())
                .map { tableau.getJSONArray(it).getString(0) }
                .toSet()
        }

        val grilles = grilles()
        for (i in 0 until grilles.length()) {
            val mots = grilles.getJSONObject(i).getJSONArray("mots")
            for (j in 0 until mots.length()) {
                val canonique = mots.getJSONObject(j).getString("f")
                assertTrue(
                    "grille #$i : « $canonique » hors dictionnaire",
                    formes.contains(canonique)
                )
            }
        }
    }

    /**
     * Le filtre de neutralité écarte des grilles entières, et il faut qu'il en
     * reste assez.
     *
     * Une grille part dès qu'un seul de ses mots est de ceux que l'application
     * ne propose pas d'elle-même. C'est le prix d'un filtre appliqué au
     * chargement plutôt qu'à la génération — la contrepartie étant qu'un
     * allongement de la liste porte aussi sur les actifs déjà livrés. Rien ne
     * signalerait que la réserve d'un niveau a fondu : le jeu servirait les
     * mêmes grilles de plus en plus souvent.
     */
    @Test
    fun `il reste des grilles apres le filtre de neutralite`() {
        val grilles = grilles()
        val restantes = mutableMapOf<Int, Int>()
        for (i in 0 until grilles.length()) {
            val grille = grilles.getJSONObject(i)
            val mots = grille.getJSONArray("mots")
            val ecartee = (0 until mots.length()).any {
                MotsEcartes.estEcarte(mots.getJSONObject(it).getString("f"))
            }
            if (!ecartee) {
                val niveau = grille.getInt("l")
                restantes[niveau] = (restantes[niveau] ?: 0) + 1
            }
        }
        for (niveau in 1..3) {
            assertTrue(
                "difficulté $niveau : plus que ${restantes[niveau] ?: 0} grilles " +
                    "une fois les mots écartés retirés",
                (restantes[niveau] ?: 0) >= 60
            )
        }
    }

    /**
     * Aucune définition n'est un nom propre.
     *
     * Demander « BEETEBUERG » sous la définition « Bettembourg » ne fait pas
     * apprendre un mot, cela fait recopier une carte. La livraison du
     * 2026-09-07 en portait 54 sur 1 527 formes, dont « CAFÉ » sous
     * « Eschweiler-Halte » : le mot est glosé « café, Lëtzebuerg City Museum »,
     * et `definition_de()` retire l'acception qui répète le mot, ne laissant
     * que le lieu-dit. C'est pourquoi le générateur applique la règle deux
     * fois, sur la glose source puis sur la définition retenue.
     *
     * Le repérage tient à ce que le LOD écrit ses gloses en français : un nom
     * commun français est en minuscules, donc une définition dont toutes les
     * acceptions commencent par une majuscule désigne un nom propre.
     */
    @Test
    fun `aucune definition n'est un nom propre`() {
        val grilles = grilles()
        val fautifs = mutableSetOf<String>()
        for (i in 0 until grilles.length()) {
            val mots = grilles.getJSONObject(i).getJSONArray("mots")
            for (j in 0 until mots.length()) {
                val mot = mots.getJSONObject(j)
                val acceptions = mot.getString("g").split(",")
                    .map { it.trim() }.filter { it.isNotEmpty() }
                if (acceptions.isNotEmpty() &&
                    acceptions.all { it.first().isUpperCase() }
                ) {
                    fautifs.add("${mot.getString("f")} : ${mot.getString("g")}")
                }
            }
        }
        assertTrue(
            "définitions entièrement capitalisées, donc noms propres : " +
                fautifs.sorted().joinToString(" · "),
            fautifs.isEmpty()
        )
    }

    /**
     * Le pavé du jeu porte toutes les lettres qu'il faut écrire.
     *
     * Le pavé suit la disposition du clavier, donc une rangée réécrite à la
     * main peut perdre une lettre — et rien ne le signalerait : la grille
     * s'affiche, les définitions sont bonnes, mais les mots qui emploient cette
     * lettre deviennent inachevables. C'est le seul contrôle qui relie
     * l'alphabet du générateur à celui de l'écran.
     */
    @Test
    fun `le pave porte toutes les lettres des grilles`() {
        val touches = com.example.kreyolkeyboard.crossword.CrosswordData
            .RANGEES.joinToString("").toSet()
        assertEquals(
            "le pavé ne porte pas l'alphabet attendu",
            alphabet, touches
        )

        val grilles = grilles()
        for (i in 0 until grilles.length()) {
            val mots = grilles.getJSONObject(i).getJSONArray("mots")
            for (j in 0 until mots.length()) {
                val reponse = mots.getJSONObject(j).getString("m")
                val absentes = reponse.toSet() - touches
                assertTrue(
                    "grille #$i : « $reponse » demande des lettres absentes du " +
                        "pavé : $absentes",
                    absentes.isEmpty()
                )
            }
        }
    }

    /** La requête apparaît-elle comme mot entier dans le texte, accents pliés ? */
    private fun contientLeMot(texte: String, mot: String): Boolean {
        val cible = AccentTolerantMatcher.normalize(mot)
        return AccentTolerantMatcher.normalize(texte)
            .split(Regex("[^\\p{L}]+"))
            .any { it == cible }
    }
}
