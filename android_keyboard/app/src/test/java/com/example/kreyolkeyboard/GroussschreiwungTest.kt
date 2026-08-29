package com.example.kreyolkeyboard

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Non-régression de la casse canonique du dictionnaire livré.
 *
 * Le luxembourgeois capitalise tous les substantifs (Groussschreiwung), comme
 * l'allemand : la majuscule est porteuse de sens, pas un accident de saisie.
 * La pipeline repliait pourtant tout le corpus en minuscules, si bien que le
 * clavier ne pouvait jamais proposer « Joer » ni « Haus ». Depuis le
 * 2026-08-29 la casse est élue sur le corpus, en ne comptant que les
 * occurrences situées ailleurs qu'en tête de phrase.
 *
 * Ce que ces tests protègent est invisible autrement : un retour au repli en
 * minuscules produirait un dictionnaire du bon type, de la bonne taille,
 * parfaitement valide — et un clavier muet sur les majuscules. Aucune autre
 * vérification du projet ne le remarquerait.
 */
class GroussschreiwungTest {

    /**
     * Phrases du registre quotidien — celui qu'on tape sur un téléphone —
     * écrites avec la casse attendue. Elles sont volontairement inventées et
     * non reprises d'un corpus : ParaLux sert d'évaluation et rien n'en est
     * redistribué, et citer LuxAlign reviendrait à tester la pipeline sur sa
     * propre sortie.
     */
    private val phrases = listOf(
        "Ech ginn haut mam Auto op d'Aarbecht.",
        "D'Kand ass mat senger Mamm an d'Schoul gaangen.",
        "Mir hunn e schéinen Owend am Haus verbruecht.",
        "Wéi vill kascht dëse Kaffi hei an der Stad?",
        "Den Zuch vun de CFL huet dëst Joer vill Verspéidung.",
        "Ech hu keng Zäit fir déi Fro elo ze beäntweren.",
        "Si huet mir gëschter en interessant Buch geléint.",
        "D'Waasser am Séi ass am Summer ganz waarm.",
        "Mäi Brudder schafft zënter dräi Joer bei enger Lëtzebuerger Firma.",
        "Den Hond vum Noper huet déi ganz Nuecht gebillt.",
    )

    private val motifMot = Regex("[\\p{L}\\-]{2,}")

    // Mêmes caractères transparents que la pipeline : un mot précédé du seul
    // article élidé (« d'Kand ») n'est pas en tête de phrase, sa majuscule
    // vient bien du substantif.
    private val transparents = " \t\r\n\"'«»“”„‘’()[]-–—"
    private val finsDePhrase = ".!?…"

    private fun motsHorsTeteDePhrase(phrase: String): List<String> =
        motifMot.findAll(phrase)
            .map { it.range.first to it.value.trim('-') }
            .filter { (debut, mot) ->
                if (mot.length < 2) return@filter false
                var i = debut - 1
                while (i >= 0 && transparents.contains(phrase[i])) i--
                i >= 0 && !finsDePhrase.contains(phrase[i])
            }
            .map { it.second }
            .toList()

    /** {forme repliée en minuscules -> formes livrées}, depuis l'asset réel. */
    private fun chargerDictionnaire(): Map<String, List<String>> {
        val fichier = File("src/main/assets/luxemburgish_dict.json")
        // Échec explicite, et non repli silencieux : un dictionnaire absent
        // rendrait ces tests verts en ne vérifiant rien.
        assertTrue(
            "asset luxemburgish_dict.json introuvable — la pipeline n'a pas tourné",
            fichier.exists()
        )
        val tableau = JSONArray(fichier.readText())
        val formes = mutableMapOf<String, MutableList<String>>()
        for (i in 0 until tableau.length()) {
            val mot = tableau.getJSONArray(i).getString(0)
            formes.getOrPut(mot.lowercase()) { mutableListOf() }.add(mot)
        }
        return formes
    }

    @Test
    fun `la casse livree suit la Groussschreiwung sur des phrases completes`() {
        val formes = chargerDictionnaire()
        val desaccords = mutableListOf<String>()
        var couverts = 0

        for (phrase in phrases) {
            for (mot in motsHorsTeteDePhrase(phrase)) {
                val livrees = formes[mot.lowercase()] ?: continue
                // Les homographes (Froen/froen) sont livrés dans les deux
                // casses : le dictionnaire ne tranche pas, le contexte le fera.
                if (livrees.size > 1) continue
                couverts++
                if (livrees[0] != mot) desaccords.add("$mot → ${livrees[0]}")
            }
        }

        assertTrue("échantillon trop peu couvert par le dictionnaire ($couverts mots)", couverts >= 60)
        assertEquals("casse divergente entre le dictionnaire et la règle: $desaccords", 0, desaccords.size)
    }

    @Test
    fun `les substantifs courants portent leur majuscule`() {
        val formes = chargerDictionnaire()
        for (nom in listOf("Joer", "Haus", "Kand", "Mamm", "Auto", "Aarbecht",
                           "Stad", "Zäit", "Fro", "Schoul", "Owend", "Lëtzebuerg")) {
            assertEquals("« $nom » n'est pas livré capitalisé",
                listOf(nom), formes[nom.lowercase()])
        }
    }

    @Test
    fun `les mots outils restent en minuscules`() {
        val formes = chargerDictionnaire()
        // « dass » en fait partie : c'est une variante luxembourgeoise
        // légitime, pas un germanisme, et surtout pas un substantif.
        for (outil in listOf("an", "ech", "mir", "net", "dass", "ass", "huet",
                             "op", "mat", "eng", "vun", "fir")) {
            assertEquals("« $outil » a été capitalisé à tort",
                listOf(outil), formes[outil])
        }
    }

    @Test
    fun `les acronymes gardent leurs capitales`() {
        val formes = chargerDictionnaire()
        for (sigle in listOf("RTL", "CFL")) {
            assertEquals("« $sigle » a perdu ses capitales",
                listOf(sigle), formes[sigle.lowercase()])
        }
    }

    @Test
    fun `les homographes sont livres dans les deux casses`() {
        val formes = chargerDictionnaire()
        // Là où la casse distingue deux mots, les deux entrées existent avec
        // leurs fréquences propres : Froen (les questions) et froen
        // (demander), Gréng (le parti) et gréng (la couleur).
        for (paire in listOf("froen", "gréng", "liewen")) {
            val livrees = formes[paire] ?: emptyList()
            assertEquals("« $paire » devrait être livré dans les deux casses, reçu $livrees",
                2, livrees.size)
            assertTrue("variante minuscule manquante pour « $paire »", livrees.contains(paire))
        }
    }

    @Test
    fun `le dictionnaire livre une majorite d'entrees capitalisees`() {
        val formes = chargerDictionnaire()
        val capitalisees = formes.values.flatten().count { it.first().isUpperCase() }
        // Même seuil que le garde-fou de la CI : sous 15 000, la casse n'a pas
        // été élue et le dictionnaire est retombé en minuscules.
        assertTrue(
            "seulement $capitalisees entrées capitalisées : la casse canonique n'a pas été élue",
            capitalisees >= 15000
        )
    }
}
