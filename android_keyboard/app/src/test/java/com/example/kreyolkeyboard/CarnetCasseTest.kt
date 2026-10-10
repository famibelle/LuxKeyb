package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.carnet.Carnet
import com.example.kreyolkeyboard.carnet.CarteMot
import com.example.kreyolkeyboard.carnet.JeuCarte
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La réparation des cartes que Wuertriet versait en minuscules (« affer »
 * pour « Affer »), jusqu'à la 35.0.0.
 *
 * Une faute ici ne se voit pas : une carte fusionnée à tort disparaît du
 * carnet sans un mot, une carte oubliée reste en double. D'où la preuve sur
 * chaque cas.
 */
class CarnetCasseTest {

    private val dico = mapOf("affer" to "Affer", "agang" to "Agang")
    private fun canonique(f: String) = dico[f]

    private fun carte(
        forme: String, numero: Int, jeux: Set<JeuCarte>,
        boite: Int = 0, jour: Int = -1, rencontres: Int = 1, nombre: Int? = null
    ) = CarteMot(forme, numero * 1000L, rencontres, numero, jeux, nombre, boite, jour)

    @Test
    fun leDoublonSeFondDansLaPlusAncienne() {
        val cartes = listOf(
            carte("Affer", 1, setOf(JeuCarte.WUERTPLAZ), boite = 1, jour = 10, rencontres = 2),
            carte("Haus", 2, setOf(JeuCarte.WUERTSICH)),
            carte("affer", 3, setOf(JeuCarte.WUERTRIET), boite = 3, jour = 40)
        )
        val r = Carnet.fusionnerCasse(cartes, ::canonique)
        assertEquals(listOf("Affer", "Haus"), r.map { it.forme })
        val affer = r[0]
        assertEquals(1, affer.numero)
        assertEquals(1000L, affer.premiereFois)
        assertEquals(3, affer.rencontres)
        assertEquals(listOf(JeuCarte.WUERTPLAZ, JeuCarte.WUERTRIET), affer.jeux.toList())
        // La révision la plus avancée survit.
        assertEquals(3, affer.boite)
        assertEquals(40, affer.jourEcheance)
    }

    /** Gagnée d'abord dans Wuertriet : c'est elle qui garde sa place et son numéro. */
    @Test
    fun laCarteDeWuertrietPeutEtreLaPlusAncienne() {
        val cartes = listOf(
            carte("affer", 1, setOf(JeuCarte.WUERTRIET)),
            carte("Affer", 2, setOf(JeuCarte.KRAIZWUERT))
        )
        val r = Carnet.fusionnerCasse(cartes, ::canonique)
        assertEquals(1, r.size)
        assertEquals("Affer", r[0].forme)
        assertEquals(1, r[0].numero)
        assertEquals(listOf(JeuCarte.WUERTRIET, JeuCarte.KRAIZWUERT), r[0].jeux.toList())
    }

    @Test
    fun uneCarteSeuleEstSeulementRenommee() {
        val r = Carnet.fusionnerCasse(listOf(carte("agang", 4, setOf(JeuCarte.WUERTRIET))), ::canonique)
        assertEquals("Agang", r.single().forme)
        assertEquals(4, r.single().numero)
    }

    /** « kënne » n'a pas de forme majuscule au dictionnaire : un vrai mot, intact. */
    @Test
    fun unVraiMotEnMinusculesNeBougePas() {
        val cartes = listOf(
            carte("kënne", 1, setOf(JeuCarte.WUERTRIET)),
            carte("sechsafofzeg", 2, setOf(JeuCarte.ZUELWUERT), nombre = 56)
        )
        assertEquals(cartes, Carnet.fusionnerCasse(cartes, ::canonique))
    }

    @Test
    fun seulesWuertrietEtLeCadeauSontSuspects() {
        val cartes = listOf(
            carte("affer", 1, setOf(JeuCarte.WUERTRIET)),
            carte("agang", 2, setOf(JeuCarte.CADEAU)),
            carte("kënne", 3, setOf(JeuCarte.WUERTMIX)),
            carte("Haus", 4, setOf(JeuCarte.WUERTRIET)),
            carte("sechsafofzeg", 5, setOf(JeuCarte.CADEAU), nombre = 56)
        )
        assertEquals(setOf("affer", "agang"), Carnet.suspectesDeCasse(cartes))
    }
}
