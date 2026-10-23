package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.carnet.CarteFierkelsmaart
import com.example.kreyolkeyboard.carnet.CarteMot
import com.example.kreyolkeyboard.carnet.Champ
import com.example.kreyolkeyboard.carnet.ContenuCarte
import com.example.kreyolkeyboard.carnet.JeuCarte
import com.example.kreyolkeyboard.carnet.Lieu
import com.example.kreyolkeyboard.carnet.Rarete
import com.example.kreyolkeyboard.carnet.Series
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La carte ultra rare de la Fierkelsmaart : un mot ajouté à la main, que la
 * carte doit corriger comme la bienvenue corrige « Moien ».
 */
class CarteFierkelsmaartTest {

    private fun contenu(forme: String, jeux: Set<JeuCarte> = setOf(JeuCarte.CADEAU), nombre: Int? = null) = ContenuCarte(
        carte = CarteMot(forme = forme, premiereFois = 0L, rencontres = 1, numero = 1, jeux = jeux, nombre = nombre),
        rarete = Rarete.COMMUN,
        rang = 32_597,
        glose = "",
        autresFormes = emptyList(),
        exemple = null
    )

    @Test
    fun `la carte est tres rare, glosee et rangee a la Schueberfouer`() {
        val c = CarteFierkelsmaart.corriger(contenu("Fierkelsmaart"), "marché aux porcelets", "Nous y serons !")
        assertEquals(Rarete.TRES_RARE, c.rarete)
        assertEquals("marché aux porcelets", c.glose)
        assertEquals(CarteFierkelsmaart.EXEMPLE, c.exemple)
        assertEquals(Champ.SOCIETE, c.blason.champ)
        assertEquals(Lieu.FOUER, Lieu.de(c.blason.champ))
        assertEquals(Series.cleDePaysage(Lieu.FOUER, Rarete.TRES_RARE), Series.paysage(c))
    }

    @Test
    fun `elle reste ultra rare quelle que soit sa provenance`() {
        for (jeu in JeuCarte.values()) {
            val c = CarteFierkelsmaart.corriger(contenu("Fierkelsmaart", setOf(jeu)), "g", "t")
            assertEquals(Rarete.TRES_RARE, c.rarete)
        }
    }

    @Test
    fun `une autre carte n'est pas touchee`() {
        val c = CarteFierkelsmaart.corriger(contenu("Fierkel"), "g", "t")
        assertEquals(Rarete.COMMUN, c.rarete)
        assertEquals("", c.glose)
        assertFalse(CarteFierkelsmaart.est("fierkelsmaart"))
    }

    @Test
    fun `l'exemple n'est pas une phrase du corpus RTL`() {
        // La carte doit pouvoir s'imprimer et se vendre : sa phrase ne peut
        // pas venir de luxemburgish_cloze.json, sous CC BY-NC.
        val cloze = File("src/main/assets/luxemburgish_cloze.json").readText()
        assertFalse(cloze.contains(CarteFierkelsmaart.EXEMPLE))
    }

    @Test
    fun `le mot est au dictionnaire et survit a sa regeneration`() {
        val dico = JSONArray(File("src/main/assets/luxemburgish_dict.json").readText())
        val formes = (0 until dico.length()).map { dico.getJSONArray(it).getString(0) }
        assertTrue(CarteFierkelsmaart.FORME in formes)
        val pipeline = File("../../Dictionnaires/LuxembourgishComplet.py").readText()
        assertTrue(
            "le mot doit rester dans MOTS_HORS_CORPUS",
            Regex("MOTS_HORS_CORPUS = \\([^)]*\"${CarteFierkelsmaart.FORME}\"").containsMatchIn(pipeline)
        )
    }
}
