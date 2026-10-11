package com.example.kreyolkeyboard.carnet

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * La saison et la fête que la carte retient du jour de sa capture.
 *
 * La carte ne montre jamais la date : ce test est le seul endroit où l'on
 * vérifie qu'elle est bien lue.
 */
class SouvenirTest {

    @Test
    fun lesSaisonsSontParMoisEntiers() {
        val attendu = listOf(
            Saison.WANTER, Saison.WANTER, Saison.FREIJOER, Saison.FREIJOER, Saison.FREIJOER,
            Saison.SUMMER, Saison.SUMMER, Saison.SUMMER, Saison.HIERSCHT, Saison.HIERSCHT,
            Saison.HIERSCHT, Saison.WANTER
        )
        for (mois in 1..12) assertEquals("mois $mois", attendu[mois - 1], Souvenir.du(2026, mois, 15).saison)
    }

    @Test
    fun paquesSurDesAnneesConnues() {
        assertArrayEquals(intArrayOf(4, 5), Souvenir.paques(2026))
        assertArrayEquals(intArrayOf(3, 31), Souvenir.paques(2024))
        assertArrayEquals(intArrayOf(4, 20), Souvenir.paques(2025))
        assertArrayEquals(intArrayOf(3, 23), Souvenir.paques(2008))
        assertArrayEquals(intArrayOf(4, 25), Souvenir.paques(2038))
    }

    /** Six semaines avant Pâques, à travers le changement d'heure. */
    @Test
    fun leBuergbrennenEstLePremierDimancheDeCareme() {
        assertArrayEquals(intArrayOf(2, 22), Souvenir.buergbrennen(2026))
        assertArrayEquals(intArrayOf(2, 18), Souvenir.buergbrennen(2024))
        assertArrayEquals(intArrayOf(3, 9), Souvenir.buergbrennen(2025))
        assertEquals(Fete.BUERGBRENNEN, Souvenir.du(2026, 2, 22).fete)
        assertNull(Souvenir.du(2026, 2, 21).fete)
    }

    @Test
    fun lesFetesEtLeursBornes() {
        assertEquals(Fete.NEIJOERSCHDAG, Souvenir.du(2026, 1, 1).fete)
        assertNull(Souvenir.du(2026, 1, 2).fete)
        assertNull(Souvenir.du(2026, 6, 21).fete)
        assertEquals(Fete.NATIONALFEIERDAG, Souvenir.du(2026, 6, 22).fete)
        assertEquals(Fete.NATIONALFEIERDAG, Souvenir.du(2026, 6, 23).fete)
        assertNull(Souvenir.du(2026, 8, 21).fete)
        assertEquals(Fete.SCHUEBERFOUER, Souvenir.du(2026, 8, 22).fete)
        assertEquals(Fete.SCHUEBERFOUER, Souvenir.du(2026, 9, 11).fete)
        assertNull(Souvenir.du(2026, 9, 12).fete)
        assertEquals(Fete.CHRESCHTMAART, Souvenir.du(2026, 11, 22).fete)
        assertEquals(Fete.CHRESCHTMAART, Souvenir.du(2026, 12, 24).fete)
        assertNull(Souvenir.du(2026, 12, 25).fete)
    }

    @Test
    fun leCachetPorteLaFeteSinonLaSaison() {
        assertEquals("Schueberfouer", Souvenir.du(2026, 8, 28).nom)
        assertEquals(Saison.SUMMER, Souvenir.du(2026, 8, 28).saison)
        assertEquals("Hierscht", Souvenir.du(2026, 10, 11).nom)
        assertEquals("Fréijoer", Souvenir.du(2026, 4, 20).nom)
    }

    /** Lu dans le fuseau de l'appareil : 23 h 30 le 31 décembre à Luxembourg n'est pas le Nouvel An. */
    @Test
    fun lInstantSeLitDansLeFuseauLocal() {
        val lux = TimeZone.getTimeZone("Europe/Luxembourg")
        val cal = Calendar.getInstance(lux)
        cal.clear()
        cal.set(2026, Calendar.DECEMBER, 31, 23, 30)
        assertEquals(Souvenir(Saison.WANTER, null), Souvenir.de(cal.timeInMillis, lux))
        cal.add(Calendar.HOUR_OF_DAY, 1)
        assertEquals(Fete.NEIJOERSCHDAG, Souvenir.de(cal.timeInMillis, lux)?.fete)
    }

    /** Un champ absent n'est pas le 1er janvier 1970 : pas de feu d'artifice sur une carte qui n'a rien vu. */
    @Test
    fun uneDateAbsenteNeDonneAucunSouvenir() {
        assertNull(Souvenir.de(0L))
        assertNull(Souvenir.de(-5L))
    }
}
