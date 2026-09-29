package com.example.kreyolkeyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La recherche du Wierderbuch ignore ce que le clavier ou le doigt ajoutent
 * autour du mot (v29.3.0) : « Gromper . » ne trouvait rien.
 */
class WierderbuchRequeteTest {

    @Test
    fun laPonctuationAutourDuMotEstRetiree() {
        assertEquals("Gromper", TranslationDictionary.nettoyerRequete("Gromper . "))
        assertEquals("Gromper", TranslationDictionary.nettoyerRequete("Gromper "))
        assertEquals("maison", TranslationDictionary.nettoyerRequete("« maison » ?"))
        assertEquals("Haus", TranslationDictionary.nettoyerRequete("  ,Haus!"))
    }

    @Test
    fun lInterieurDuMotEstGarde() {
        assertEquals("pomme de terre", TranslationDictionary.nettoyerRequete("pomme  de terre."))
        assertEquals("tape-à-l'œil", TranslationDictionary.nettoyerRequete("tape-à-l'œil"))
        assertEquals("d'Land", TranslationDictionary.nettoyerRequete("d'Land"))
    }

    @Test
    fun uneRequeteSansLettreDevientVide() {
        assertEquals("", TranslationDictionary.nettoyerRequete(" . , "))
    }
}
