package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.carnet.CarteAccueil
import com.example.kreyolkeyboard.carnet.CarteMot
import com.example.kreyolkeyboard.carnet.ContenuCarte
import com.example.kreyolkeyboard.carnet.JeuCarte
import com.example.kreyolkeyboard.carnet.Rarete
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La carte « Moien » offerte à la fin de l'installation.
 *
 * Le LOD glose `Moien` « matin » : sans la correction, la carte de bienvenue
 * dirait « matin » et la révision interrogerait sur un contresens.
 */
class CarteAccueilTest {

    private fun contenu(forme: String, jeux: Set<JeuCarte>) = ContenuCarte(
        carte = CarteMot(forme = forme, premiereFois = 0L, rencontres = 1, numero = 1, jeux = jeux),
        rarete = Rarete.values().first(),
        rang = 210,
        glose = "matin",
        autresFormes = emptyList(),
        exemple = "erwäch mech muer de Moien ëm siwen Auer!"
    )

    @Test
    fun `la carte offerte dit bonjour et non matin`() {
        val c = CarteAccueil.corriger(contenu("Moien", setOf(JeuCarte.ACCUEIL)))
        assertEquals("bonjour", c.glose)
        assertEquals(CarteAccueil.EXEMPLE, c.exemple)
        assertEquals(CarteAccueil.TRADUCTION, c.traductionExemple)
    }

    @Test
    fun `un Moien gagne dans un jeu seul garde le sens du dictionnaire`() {
        val c = CarteAccueil.corriger(contenu("Moien", setOf(JeuCarte.WUERTPLAZ)))
        assertEquals("matin", c.glose)
    }

    @Test
    fun `une autre carte de bienvenue n'est pas touchee`() {
        val c = CarteAccueil.corriger(contenu("Haus", setOf(JeuCarte.ACCUEIL)))
        assertEquals("matin", c.glose)
    }

    @Test
    fun `la forme offerte est dans le dictionnaire de frequences`() {
        val fichier = File("src/main/assets/luxemburgish_dict.json")
        assertTrue("dictionnaire manquant", fichier.exists())
        val dico = JSONArray(fichier.readText())
        val formes = (0 until dico.length()).map { dico.getJSONArray(it).getString(0) }
        assertTrue("« ${CarteAccueil.FORME} » doit avoir un rang", CarteAccueil.FORME in formes)
    }
}
