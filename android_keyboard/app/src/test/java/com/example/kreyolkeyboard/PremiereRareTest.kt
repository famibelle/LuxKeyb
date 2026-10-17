package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.carnet.Booster
import com.example.kreyolkeyboard.carnet.Rarete
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La règle « première fois » de la pochette : la version longue ne se joue
 * qu'une fois par palier distingué, pour la première neuve que le carnet ne
 * connaissait pas. Une faute ici ne casse rien de visible, elle rejoue la
 * longue cérémonie à chaque rare ou ne la joue jamais.
 */
class PremiereRareTest {

    private val ordre = listOf(
        "Kran" to Rarete.TRES_RARE,
        "Artist" to Rarete.RARE,
        "Himmel" to Rarete.RARE,
        "Moien" to Rarete.COMMUN
    )
    private val toutes = ordre.map { it.first }.toSet()

    @Test
    fun unCarnetSansRareFeteLaPremiereDeChaquePalier() {
        assertEquals(
            setOf("Kran", "Artist"),
            Booster.premieres(ordre, toutes, possedes = setOf(Rarete.COMMUN))
        )
    }

    @Test
    fun unPalierDejaPossedeNeSeFeteplusLonguement() {
        assertEquals(
            setOf("Kran"),
            Booster.premieres(ordre, toutes, possedes = setOf(Rarete.RARE))
        )
    }

    @Test
    fun uneCarteDejaConnueNestJamaisUnePremiere() {
        // Artist revient, déjà au carnet : seule Himmel, neuve, compte.
        assertEquals(
            setOf("Himmel"),
            Booster.premieres(ordre, setOf("Himmel"), possedes = setOf(Rarete.TRES_RARE))
        )
    }

    @Test
    fun lesCommunesNOntPasDePremiere() {
        assertEquals(
            emptySet<String>(),
            Booster.premieres(listOf("Moien" to Rarete.COMMUN, "Haus" to Rarete.PEU_COMMUN),
                setOf("Moien", "Haus"), possedes = emptySet())
        )
    }
}
