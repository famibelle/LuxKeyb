package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.wuertriet.WuertrietData
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Les deux populations de Wuertriet : ce qu'il **tire** et ce qu'il **accepte**.
 *
 * Le jeu ne lisait que `luxemburgish_dict.json`, le relevé de fréquences du
 * corpus. Il refusait donc, comme « pas au dictionnaire », les mots de cinq
 * lettres que le LOD donne au clavier : une joueuse a touché une pastille, lu
 * ce refus et arrêté de jouer (retour du 27 novembre 2026). La validation lit
 * maintenant l'union des deux ; le tirage, lui, ne bouge pas, parce qu'une
 * forme du LOD seul n'a ni fréquence, ni rang de rareté, ni glose garantie.
 *
 * Rien de tout cela ne casse quoi que ce soit en se perdant : un palier
 * `suggest` vidé par une régénération ramènerait simplement le jeu à son
 * ancien périmètre, en silence, et c'est ce que ce test refuse.
 *
 * Le [WuertrietData.WORD_LENGTH] du jeu passe par [Context][android.content.Context],
 * que la suite unitaire n'a pas : les règles sont rejouées ici sur les actifs
 * livrés, à l'identique de [WuertrietData.loadWords] et de
 * [WuertrietData.chargerMotsAcceptes].
 */
class WuertrietAcceptationTest {

    private val longueur = WuertrietData.WORD_LENGTH

    private fun actif(nom: String): File {
        val fichier = File("src/main/assets/$nom")
        assertTrue("$nom manquant", fichier.exists())
        return fichier
    }

    /** Les mots que le jeu tire : le dictionnaire du corpus, et lui seul. */
    private fun dictionnaire(): Set<String> {
        val tableau = JSONArray(actif("luxemburgish_dict.json").readText())
        val mots = HashSet<String>()
        for (i in 0 until tableau.length()) {
            val forme = tableau.getJSONArray(i).getString(0).lowercase()
            if (forme.length == longueur && forme.all { it.isLetter() }) mots.add(forme)
        }
        return mots
    }

    /** Ce que le LOD ajoute : le palier que la barre de suggestions propose. */
    private fun palierLod(): Set<String> {
        val tableau = JSONObject(actif("luxemburgish_lod_forms.json").readText())
            .getJSONArray("suggest")
        val mots = HashSet<String>()
        for (i in 0 until tableau.length()) {
            val forme = tableau.getString(i).lowercase()
            if (forme.length == longueur && forme.all { it.isLetter() }) mots.add(forme)
        }
        return mots
    }

    @Test
    fun `le tirage garde sa reserve`() {
        val tires = dictionnaire()
        assertTrue("seulement ${tires.size} mots de $longueur lettres", tires.size >= 2000)
    }

    @Test
    fun `le LOD elargit l'acceptation de moitie`() {
        val tires = dictionnaire()
        val ajoutes = palierLod()
        assertTrue("seulement ${ajoutes.size} formes ajoutées", ajoutes.size >= 1200)
        // Le palier est le complément du dictionnaire, par construction de
        // generate_lod_forms.py : aucune forme n'y est deux fois.
        assertTrue("formes communes aux deux listes", tires.intersect(ajoutes).isEmpty())
        val acceptes = tires + ajoutes
        assertTrue(
            "l'acceptation n'a gagné que ${acceptes.size - tires.size} mots",
            acceptes.size >= tires.size * 14 / 10
        )
    }

    @Test
    fun `les mots que le clavier propose sont acceptes`() {
        // Des mots que la barre de suggestions écrit et que le jeu refusait :
        // tous au LOD, aucun au dictionnaire du corpus.
        val temoins = listOf("Dronk", "Kojot", "Parka", "binär", "zaapt", "ritzt", "Flapp")
        val tires = dictionnaire()
        val acceptes = tires + palierLod()
        for (mot in temoins) {
            val forme = mot.lowercase()
            assertTrue("$mot refusé par le jeu", forme in acceptes)
            assertTrue("$mot est entré au dictionnaire, témoin à remplacer", forme !in tires)
        }
    }

    @Test
    fun `ce qui n'est pas un mot reste refuse`() {
        val acceptes = dictionnaire() + palierLod()
        for (faux in listOf("zzzzz", "aaaaa", "qwert")) {
            assertTrue("$faux accepté", faux !in acceptes)
        }
        assertTrue("wuert refusé", "wuert" in acceptes)
    }

    /**
     * Les mots du niveau A1 que le tirage ne doit pas atteindre.
     *
     * [WuertrietData.pickRandomWord] tire une carte manquante en passant par
     * [WuertrietData.estAuDictionnaire], pas par
     * [WuertrietData.isValidWord] : ces neuf mots sont acceptables en
     * proposition mais pas tirables, faute de fréquence (la carte serait
     * rangée parmi les plus rares) et de forme canonique (« mango » à côté
     * d'un futur « Mango »). Élargir le tirage se fait en leur donnant
     * d'abord ces deux choses.
     */
    @Test
    fun `les essentiels sans frequence ne sont pas tirables`() {
        val a1 = JSONObject(actif("luxemburgish_niveau_a1.json").readText())
            .getJSONArray("themes")
        val mots = mutableListOf<String>()
        for (i in 0 until a1.length()) {
            val theme = a1.getJSONObject(i).getJSONArray("mots")
            for (j in 0 until theme.length()) mots.add(theme.getString(j))
        }
        val tires = dictionnaire()
        val horsDico = mots
            .filter { it.length == longueur && it.all { c -> c.isLetter() } }
            .filter { it.lowercase() !in tires }
        assertEquals(
            "la liste A1 a changé : revoir le tirage des cartes manquantes",
            listOf("Awuer", "Drauf", "Fritt", "Härin", "Kraut", "Mango", "Muert", "Müsli", "Tomat"),
            horsDico.sorted()
        )
        val acceptes = tires + palierLod()
        for (mot in horsDico) {
            assertTrue("$mot n'est même pas acceptable", mot.lowercase() in acceptes)
        }
    }
}
