package com.example.kreyolkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests des règles du dictionnaire personnel.
 *
 * Sans lui, un mot absent du corpus littéraire (prénom, toponyme, mot de
 * famille) n'était jamais suggéré et restait souligné comme faute, quel que
 * soit le nombre de fois qu'on le tapait : `addWordToDictionary()` existait
 * mais n'était appelée nulle part et ne persistait rien.
 */
class PersonalDictionaryTest {

    // ===== Ce qui peut être appris =====

    @Test
    fun `un mot deja connu du moteur n'est pas reappris`() {
        assertFalse(PersonalDictionary.isLearnable("bonjou", isAlreadyKnown = true))
    }

    @Test
    fun `un prenom inconnu est apprenable`() {
        assertTrue(PersonalDictionary.isLearnable("Karukera", isAlreadyKnown = false))
        assertTrue(PersonalDictionary.isLearnable("Famibelle", isAlreadyKnown = false))
    }

    @Test
    fun `les mots accentues et composes sont apprenables`() {
        // Le kréyòl emploie couramment le trait d'union et l'apostrophe
        assertTrue(PersonalDictionary.isLearnable("Gwadloup", isAlreadyKnown = false))
        assertTrue(PersonalDictionary.isLearnable("tan-lasa", isAlreadyKnown = false))
        assertTrue(PersonalDictionary.isLearnable("mangwòv", isAlreadyKnown = false))
    }

    @Test
    fun `un mot trop court n'est pas appris`() {
        // Sous 3 lettres, on retiendrait surtout des amorces de frappe
        assertFalse(PersonalDictionary.isLearnable("ab", isAlreadyKnown = false))
        assertFalse(PersonalDictionary.isLearnable("z", isAlreadyKnown = false))
        assertFalse(PersonalDictionary.isLearnable("", isAlreadyKnown = false))
    }

    // ===== Garde-fous de vie privée =====

    @Test
    fun `rien contenant un chiffre n'est appris`() {
        // Codes, identifiants, mots de passe partiellement numériques
        assertFalse(PersonalDictionary.isLearnable("secret2024", isAlreadyKnown = false))
        assertFalse(PersonalDictionary.isLearnable("A1b2C3", isAlreadyKnown = false))
    }

    @Test
    fun `ni les adresses e-mail ni les URL ne sont apprises`() {
        assertFalse(PersonalDictionary.isLearnable("medhi@example.com", isAlreadyKnown = false))
        assertFalse(PersonalDictionary.isLearnable("https://potomitan.fr", isAlreadyKnown = false))
        assertFalse(PersonalDictionary.isLearnable("www.potomitan.fr", isAlreadyKnown = false))
    }

    @Test
    fun `la ponctuation et les symboles ne sont pas appris`() {
        assertFalse(PersonalDictionary.isLearnable("!!!", isAlreadyKnown = false))
        assertFalse(PersonalDictionary.isLearnable("mot,mot", isAlreadyKnown = false))
        assertFalse(PersonalDictionary.isLearnable("€uro", isAlreadyKnown = false))
    }

    // ===== Fréquence attribuée =====

    @Test
    fun `un mot appris recoit une frequence modeste`() {
        // Il ne doit pas rivaliser d'emblée avec le vocabulaire courant du corpus
        assertEquals(2, PersonalDictionary.frequencyFor(2))
        assertEquals(7, PersonalDictionary.frequencyFor(7))
    }

    @Test
    fun `la frequence d'un mot appris est plafonnee`() {
        // Même martelé, un mot personnel reste loin des mots les plus fréquents
        // du kréyòl (ka 1800, pa 664)
        val plafond = PersonalDictionary.MAX_LEARNED_FREQUENCY
        assertEquals(plafond, PersonalDictionary.frequencyFor(500))
        assertEquals(plafond, PersonalDictionary.frequencyFor(10_000))
    }

    @Test
    fun `la frequence ne descend jamais sous un`() {
        assertEquals(1, PersonalDictionary.frequencyFor(0))
        assertEquals(1, PersonalDictionary.frequencyFor(-3))
    }

    @Test
    fun `le seuil d'apprentissage exige plus d'un emploi`() {
        // Une faute de frappe se répète rarement à l'identique
        assertTrue(PersonalDictionary.LEARNING_THRESHOLD >= 2)
    }
}
