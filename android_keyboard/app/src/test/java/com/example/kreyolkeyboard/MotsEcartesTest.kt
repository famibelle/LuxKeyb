package com.example.kreyolkeyboard

import org.json.JSONArray
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La liste de [MotsEcartes] est tenue à la main : elle se relit donc ici, où
 * une erreur se voit.
 *
 * Deux régressions muettes sont possibles et n'échouent nulle part ailleurs :
 * un mot visé qui repasse (l'application se remet à proposer du vocabulaire
 * confessionnel), et un mot courant qui se fait prendre au passage (le jeu
 * perd « bekannt » ou « Här » sans que personne le remarque).
 */
class MotsEcartesTest {

    private val vises = listOf(
        "Ramadan", "Kierch", "Kierchen", "Moschee", "Synagog", "Poopst",
        "Bëschof", "Relioun", "Islam", "Koran", "Bibel", "Gott", "Chrëscht",
        "kathoulesch", "jiddesch", "Judden", "Moslemen", "Gebiet", "bieden",
        "Engel", "Däiwel", "Kommioun", "Klouschter", "Kapell", "Abtei"
    )

    /**
     * Mots dont le sens religieux existe mais n'est pas le sens courant. Les
     * écarter appauvrirait les jeux sans rien régler — et `bekannt`, `Här` et
     * `Mass` comptent parmi les formes les plus fréquentes du dictionnaire.
     */
    private val gardes = listOf(
        "bekannt", "bekannten", "Här", "Hären", "Mass", "Massen", "Kräiz",
        "Wonner", "Por", "Sënn", "Séil", "Testament", "Seminaire", "Laien",
        "weien", "widmen", "Chrëschtdag", "Ouschteren", "Oktav", "Kierchbierg"
    )

    @Test
    fun `les formes visees sont ecartees`() {
        for (mot in vises) {
            assertTrue("« $mot » devrait être écarté", MotsEcartes.estEcarte(mot))
        }
    }

    @Test
    fun `les mots courants restent proposables`() {
        for (mot in gardes) {
            assertFalse("« $mot » ne devrait pas être écarté", MotsEcartes.estEcarte(mot))
        }
    }

    @Test
    fun `la casse et les accents ne comptent pas`() {
        assertTrue(MotsEcartes.estEcarte("RAMADAN"))
        assertTrue(MotsEcartes.estEcarte("kierch"))
        assertTrue(MotsEcartes.estEcarte("BESCHOF"))
    }

    @Test
    fun `une phrase est ecartee sur un seul mot`() {
        assertTrue(MotsEcartes.phraseEcartee("E Samschdeg gouf et am jiddesche Musée eng Attack."))
        assertFalse(MotsEcartes.phraseEcartee("De Kierchbierg ass e Quartier vun der Stad."))
    }

    /**
     * La liste vaut par ce qu'elle retire, mais elle ne doit pas amputer le
     * dictionnaire : ces formes restent tapables, suggérables et cherchables.
     * Si la part écartée dépassait le pour-mille, c'est que le filtre aurait
     * débordé de son rôle.
     */
    @Test
    fun `la part ecartee du dictionnaire reste marginale`() {
        val fichier = File("src/main/assets/luxemburgish_dict.json")
        assertTrue("luxemburgish_dict.json manquant", fichier.exists())
        val tableau = JSONArray(fichier.readText())

        var total = 0L
        var ecarte = 0L
        for (i in 0 until tableau.length()) {
            val entree = tableau.getJSONArray(i)
            val n = entree.getLong(1)
            total += n
            if (MotsEcartes.estEcarte(entree.getString(0))) ecarte += n
        }
        assertTrue("dictionnaire vide", total > 0)
        assertTrue(
            "$ecarte occurrences écartées sur $total : le filtre déborde",
            ecarte * 1000 < total
        )
    }
}
