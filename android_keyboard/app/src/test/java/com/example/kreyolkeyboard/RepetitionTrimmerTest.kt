package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.stt.RepetitionTrimmer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les cas de ce test ne sont pas inventés : ce sont les transcriptions relevées
 * sur téléphone le 29 août 2026, service LuxASR v2.3.0, sur un extrait de dix
 * secondes de conférence de presse rejoué au haut-parleur.
 */
class RepetitionTrimmerTest {

    @Test
    fun `la boucle de decodage relevee sur le telephone est coupee`() {
        val brut = "Ech mengen, souwäit ech mech elo erënnere kann, vu virun enger " +
            "hallwer Stonn huet d'Majoritéit vun den Deputéierte géint déi Motioun " +
            "a wat dat bedenkt, dat ass net nëmmeen, dat ass net nëmmen, dat ass net " +
            "nëmmen, dat ass net nëmmen, dat ass net nëmmen, dat ass net"

        val propre = RepetitionTrimmer.trim(brut)

        // Le corps de l'énoncé est intact…
        assertTrue("le début de l'énoncé a été perdu",
            propre.startsWith("Ech mengen, souwäit ech mech elo erënnere kann"))
        assertTrue("le corps de l'énoncé a été perdu",
            propre.contains("d'Majoritéit vun den Deputéierte géint déi Motioun"))
        // …et la boucle n'apparaît plus qu'une fois.
        assertEquals("la boucle n'a pas été coupée", 1,
            Regex("dat ass net nëmmen").findAll(propre).count())
        assertTrue("la queue est plus longue que le texte utile",
            propre.length < brut.length - 60)
    }

    @Test
    fun `un texte sans repetition n'est pas touche`() {
        val texte = "Näischt. Ech mengen, souwäit ech mech elo erënnere kann, vu virun " +
            "enger hallwer Stonn huet d'Majoritéit vun den Deputéierte géint déi " +
            "Motioun gestëmmt. A wat dat betrëfft."
        assertEquals(texte, RepetitionTrimmer.trim(texte))
    }

    @Test
    fun `une insistance humaine est preservee`() {
        // Trois fois le même mot : parfaitement dicible. C'est pourquoi un motif
        // d'un seul mot demande quatre occurrences, pas trois.
        assertEquals("Jo, jo, jo, dat ass richteg.",
            RepetitionTrimmer.trim("Jo, jo, jo, dat ass richteg."))
    }

    @Test
    fun `un mot repete sans fin est ramene a une occurrence`() {
        val propre = RepetitionTrimmer.trim("Dat ass gutt, gutt, gutt, gutt, gutt, gutt")
        assertEquals("Dat ass gutt", propre)
    }

    @Test
    fun `un groupe repete trois fois est coupe`() {
        val propre = RepetitionTrimmer.trim(
            "Ech ginn haut op d'Aarbecht, op d'Aarbecht, op d'Aarbecht")
        assertEquals("Ech ginn haut op d'Aarbecht", propre)
    }

    @Test
    fun `la casse et la ponctuation ne cachent pas la boucle`() {
        val propre = RepetitionTrimmer.trim("Merci. Merci, merci ! Merci, merci")
        assertEquals("Merci", propre)
    }

    @Test
    fun `texte vide ou d'un seul mot`() {
        assertEquals("", RepetitionTrimmer.trim(""))
        assertEquals("Moien", RepetitionTrimmer.trim("Moien"))
    }
}
