package com.example.kreyolkeyboard.carnet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Le contrôle EAN-13 : chiffres, longueur et clé de contrôle. */
class CodeBarresTest {

    @Test
    fun ean_valide_est_accepte() {
        // Somme pondérée 112 : clé 8.
        assertEquals("5401234567898", CodeBarres.ean13("5401234567898"))
    }

    @Test
    fun espaces_autour_sont_ignores() {
        assertEquals("5401234567898", CodeBarres.ean13("  5401234567898\n"))
    }

    @Test
    fun mauvaise_cle_est_refusee() {
        assertNull(CodeBarres.ean13("5401234567890"))
    }

    @Test
    fun mauvaise_longueur_ou_caracteres_refuses() {
        assertNull(CodeBarres.ean13("12345"))
        assertNull(CodeBarres.ean13("540123456789X"))
        assertNull(CodeBarres.ean13("LUXKEYB:P:1:abc"))
    }
}
