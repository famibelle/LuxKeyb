package com.example.kreyolkeyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ce que le clavier déduit du champ où l'on tape (v29.3.0) : la page qui
 * s'ouvre, la majuscule d'office, l'espace après une suggestion, l'icône et
 * l'action de la touche Entrée, et les signes devant lesquels l'espace posée
 * par une suggestion disparaît.
 */
class ChampDeSaisieTest {

    private val texte = InputType.TYPE_CLASS_TEXT
    private fun texteVariation(v: Int) = InputType.TYPE_CLASS_TEXT or v

    @Test
    fun lesChampsDeChiffresOuvrentLaPage123() {
        assertTrue(InputProcessor.ouvreLePaveNumerique(InputType.TYPE_CLASS_NUMBER))
        assertTrue(InputProcessor.ouvreLePaveNumerique(
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
        assertTrue(InputProcessor.ouvreLePaveNumerique(InputType.TYPE_CLASS_PHONE))
        assertTrue(InputProcessor.ouvreLePaveNumerique(InputType.TYPE_CLASS_DATETIME))
        assertFalse(InputProcessor.ouvreLePaveNumerique(texte))
        assertFalse(InputProcessor.ouvreLePaveNumerique(
            texteVariation(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)))
        assertFalse(InputProcessor.ouvreLePaveNumerique(InputType.TYPE_NULL))
    }

    @Test
    fun lesAdressesSontReconnues() {
        assertTrue(InputProcessor.estChampAdresse(texteVariation(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)))
        assertTrue(InputProcessor.estChampAdresse(texteVariation(InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)))
        assertTrue(InputProcessor.estChampAdresse(texteVariation(InputType.TYPE_TEXT_VARIATION_URI)))
        assertFalse(InputProcessor.estChampAdresse(texte))
        assertFalse(InputProcessor.estChampAdresse(texteVariation(InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE)))
        // Une variation n'a de sens que dans la classe texte
        assertFalse(InputProcessor.estChampAdresse(
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_TEXT_VARIATION_URI))
    }

    @Test
    fun laMajusculeAutoNeVautQuePourDuTexteCourant() {
        assertTrue(InputProcessor.accepteLaMajusculeAuto(texte))
        assertTrue(InputProcessor.accepteLaMajusculeAuto(
            texteVariation(InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE) or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
        assertTrue(InputProcessor.accepteLaMajusculeAuto(texteVariation(InputType.TYPE_TEXT_VARIATION_PERSON_NAME)))

        assertFalse(InputProcessor.accepteLaMajusculeAuto(texteVariation(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)))
        assertFalse(InputProcessor.accepteLaMajusculeAuto(texteVariation(InputType.TYPE_TEXT_VARIATION_URI)))
        assertFalse(InputProcessor.accepteLaMajusculeAuto(texteVariation(InputType.TYPE_TEXT_VARIATION_PASSWORD)))
        assertFalse(InputProcessor.accepteLaMajusculeAuto(texteVariation(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)))
        assertFalse(InputProcessor.accepteLaMajusculeAuto(texteVariation(InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)))
        assertFalse(InputProcessor.accepteLaMajusculeAuto(InputType.TYPE_CLASS_NUMBER))
        assertFalse(InputProcessor.accepteLaMajusculeAuto(InputType.TYPE_CLASS_PHONE))
    }

    @Test
    fun laToucheEntreeSuitLActionDuChamp() {
        assertEquals(EditorInfo.IME_ACTION_SEARCH,
            InputProcessor.actionEntree(texte, EditorInfo.IME_ACTION_SEARCH))
        assertEquals(EditorInfo.IME_ACTION_SEND,
            InputProcessor.actionEntree(texte, EditorInfo.IME_ACTION_SEND))
        assertEquals(EditorInfo.IME_ACTION_DONE,
            InputProcessor.actionEntree(texte, EditorInfo.IME_ACTION_DONE))
        assertEquals(EditorInfo.IME_ACTION_NEXT,
            InputProcessor.actionEntree(texte, EditorInfo.IME_ACTION_NEXT))
    }

    @Test
    fun laToucheEntreeVaALaLigneQuandLeChampLeDemande() {
        // Multiligne sans action : un message long
        assertNull(InputProcessor.actionEntree(
            texte or InputType.TYPE_TEXT_FLAG_MULTI_LINE, EditorInfo.IME_ACTION_UNSPECIFIED))
        // Action désactivée explicitement par l'application
        assertNull(InputProcessor.actionEntree(
            texte, EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION))
        assertNull(InputProcessor.actionEntree(texte, EditorInfo.IME_ACTION_NONE))
        assertNull(InputProcessor.actionEntree(texte, EditorInfo.IME_ACTION_UNSPECIFIED))
    }

    @Test
    fun laPonctuationSeColleAuMotPrecedent() {
        listOf(".", ",", "?", "!", ":", ";", ")", "…", "'", "’", "”").forEach {
            assertTrue("« $it » doit se coller", InputProcessor.colleAuMotPrecedent(it))
        }
        // Le tiret ouvre une incise, « % » prend une espace, et une lettre ou
        // un chiffre commencent le mot suivant
        listOf("-", "%", "(", "a", "É", "1", "😀", "“").forEach {
            assertFalse("« $it » ne doit pas se coller", InputProcessor.colleAuMotPrecedent(it))
        }
    }

    @Test
    fun lesAdressesWebSontDistingueesDesEmails() {
        assertTrue(InputProcessor.estChampWeb(texteVariation(InputType.TYPE_TEXT_VARIATION_URI)))
        assertFalse(InputProcessor.estChampWeb(texteVariation(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)))
        assertFalse(InputProcessor.estChampWeb(texte))
    }

    @Test
    fun laMajusculeEstAttendueEnDebutDeChampEtDePhrase() {
        assertTrue(InputProcessor.majusculeAttendue(texte, ""))
        assertTrue(InputProcessor.majusculeAttendue(texte, "Moien. "))
        assertTrue(InputProcessor.majusculeAttendue(texte, "Wéi geet et? "))
        assertFalse(InputProcessor.majusculeAttendue(texte, "Moien "))
        assertFalse(InputProcessor.majusculeAttendue(
            texteVariation(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS), ""))
        assertFalse(InputProcessor.majusculeAttendue(InputType.TYPE_CLASS_NUMBER, ""))
    }

    @Test
    fun unChampDeNomMetUneMajusculeAChaqueMot() {
        val nom = texteVariation(InputType.TYPE_TEXT_VARIATION_PERSON_NAME) or
            InputType.TYPE_TEXT_FLAG_CAP_WORDS
        assertTrue(InputProcessor.majusculeAttendue(nom, "Jean "))
        assertFalse(InputProcessor.majusculeAttendue(nom, "Jean"))
        // Sans le drapeau, une espace ne suffit pas
        assertFalse(InputProcessor.majusculeAttendue(texte, "Jean "))
        assertTrue(InputProcessor.majusculeAttendue(
            texte or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, "ab"))
    }

    @Test
    fun unChampSansClasseMaisAvecDesDrapeauxDeTexteEstDuTexte() {
        // Le prénom de Google Contacts, relevé sur l'émulateur : 0x2060
        val prenomContacts = 0x2060
        assertTrue(InputProcessor.majusculeAttendue(prenomContacts, ""))
        assertTrue(InputProcessor.majusculeAttendue(prenomContacts, "Jean "))
        // Un champ vraiment nu (terminal) ne prend pas de majuscule
        assertFalse(InputProcessor.majusculeAttendue(InputType.TYPE_NULL, ""))
    }
}
