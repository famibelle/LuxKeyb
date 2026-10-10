package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.carnet.CarteMot
import com.example.kreyolkeyboard.carnet.JeuCarte
import com.example.kreyolkeyboard.carnet.Serie
import com.example.kreyolkeyboard.carnet.Series
import com.example.kreyolkeyboard.zuelen.ZuelenData
import com.example.kreyolkeyboard.zuelen.ZuelenDifficulty
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Les séries du carnet : finies, complétables, et comptées par emplacement.
 *
 * Une série qu'on ne peut pas finir ne casse rien à l'écran : elle affiche
 * simplement un emplacement qui reste vide pour toujours. C'est pour cela que
 * la preuve est ici, et pas dans un essai à la main.
 */
class SeriesTest {

    private fun carte(
        forme: String,
        numero: Int,
        jeux: Set<JeuCarte> = setOf(JeuCarte.WUERTSICH),
        nombre: Int? = null
    ) = CarteMot(forme, 0L, 1, numero, jeux, nombre)

    /**
     * La série des nombres est exactement ce que Zuelwuert peut donner.
     *
     * Plus grande, elle aurait des emplacements impossibles ; plus petite, des
     * cartes sans place. Mille manches par niveau suffisent largement : chaque
     * manche tire dix des dix-huit produits.
     */
    @Test
    fun lesNombresSontCeuxQueZuelwuertTire() {
        val tires = HashSet<Int>()
        val alea = Random(7)
        ZuelenDifficulty.values().forEach { niveau ->
            repeat(1000) { ZuelenData.newRound(niveau, alea).forEach { tires.add(it.produit) } }
        }
        assertEquals(
            "Zuelwuert tire d'autres nombres que la série : agrandir ou réduire Series.NOMBRES",
            Series.NOMBRES.toSortedSet(), tires.toSortedSet()
        )
    }

    /** Un dessin porté par trois mots est un seul emplacement. */
    @Test
    fun unDessinPartageNeCompteQuUneFois() {
        val cartes = listOf(carte("Handy", 1), carte("Telefon", 2), carte("Bam", 3))
        val meubles = mapOf("Handy" to "telephone", "Telefon" to "telephone", "Bam" to "arbre")
        val a = Series.avancement(
            Serie.ENLUMINURES, cartes, { it }, { meubles[it.forme] },
            listOf("telephone", "arbre", "velo")
        )
        assertEquals(3, a.total)
        assertEquals(2, a.remplis)
        assertFalse(a.complete)
        // La première gagnée occupe la place.
        assertEquals("Handy", a.emplacements.first { it.cle == "telephone" }.cartes.first().forme)
    }

    /** La carte de bienvenue n'est pas un jeu : elle ne remplit rien. */
    @Test
    fun laSerieDesJeuxIgnoreLaBienvenue() {
        val cartes = listOf(carte("Moien", 1, setOf(JeuCarte.ACCUEIL)))
        val a = Series.avancement(Serie.JEUX, cartes, { it }, { null }, emptyList())
        assertEquals(7, a.total)
        assertEquals(0, a.remplis)
    }

    @Test
    fun unNumeralRemplitSaPlace() {
        val cartes = Series.NOMBRES.mapIndexed { i, n ->
            carte("n$n", i + 1, setOf(JeuCarte.ZUELWUERT), n)
        }
        val a = Series.avancement(Serie.NOMBRES, cartes, { it }, { null }, emptyList())
        assertTrue(a.complete)
    }

    /**
     * L'actif de la série : présent, assez grand, et ne citant que des dessins
     * que les blasons attribuent. Le générateur prouve que chacun se gagne ;
     * ce test garde qu'on ne livre pas une liste écrite à la main.
     */
    @Test
    fun lActifDesEnluminuresEstCoherent() {
        val fichier = File("src/main/assets/luxemburgish_series.json")
        assertTrue(
            "luxemburgish_series.json manquant — lancez Dictionnaires/generate_series.py",
            fichier.exists()
        )
        val serie = JSONObject(fichier.readText()).getJSONArray("enluminures")
        val dessins = List(serie.length()) { serie.getString(it) }
        assertTrue("Seulement ${dessins.size} dessins dans la série", dessins.size >= 100)
        assertEquals("Un dessin en double", dessins.size, dessins.toSet().size)

        val blasons = JSONObject(File("src/main/assets/luxemburgish_blasons.json").readText())
            .getJSONObject("blasons")
        val attribues = blasons.keys().asSequence()
            .mapNotNull { blasons.getJSONObject(it).optString("m").ifEmpty { null } }
            .toSet()
        val inconnus = dessins.filter { it !in attribues }
        assertTrue("Dessins de série qu'aucun mot ne porte : $inconnus", inconnus.isEmpty())
    }
}
