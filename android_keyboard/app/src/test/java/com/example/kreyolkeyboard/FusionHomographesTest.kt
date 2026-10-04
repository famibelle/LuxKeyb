package com.example.kreyolkeyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La fusion des suggestions garde les deux formes d'un homographe.
 *
 * En luxembourgeois la casse distingue deux mots : « Iessen » (le repas) et
 * « iessen » (manger). Jusqu'à la 33.0.0 la fusion dédoublonnait en minuscules
 * et jetait la forme la moins fréquente : taper « iess » ne proposait jamais le
 * verbe, relevé en tapant l'exemple du LOD « Op der Kiermes iessen ech ëmmer
 * Gromperekichelcher mat Äppelkompott » sur l'émulateur.
 */
class FusionHomographesTest {

    private fun lb(mot: String, score: Float) =
        BilingualSuggestion(mot, score, SuggestionLanguage.LUXEMBOURGISH)

    private fun fr(mot: String, score: Float) =
        BilingualSuggestion(mot, score, SuggestionLanguage.FRENCH)

    private fun fusion(lux: List<BilingualSuggestion>, fra: List<BilingualSuggestion> = emptyList()) =
        SuggestionEngine.fusionnerLuxDabord(lux, fra, 5).map { it.word }

    @Test
    fun lesDeuxFormesDUnHomographeSontProposees() {
        assertEquals(
            listOf("Iessen", "iessen", "Iesse", "iesse"),
            fusion(listOf(lb("Iessen", 105f), lb("iessen", 87f), lb("Iesse", 34f), lb("iesse", 25f)))
        )
    }

    @Test
    fun deuxFormesRenduesIdentiquesParLaCasseRestentUnDoublon() {
        // « Ies » : applyCasingPattern met une capitale aux deux formes.
        assertEquals(
            listOf("Iessen", "Iesse"),
            fusion(listOf(lb("Iessen", 105f), lb("Iessen", 87f), lb("Iesse", 34f)))
        )
    }

    @Test
    fun unMotFrancaisDejaProposeEnLuxembourgeoisNeSeRepetePas() {
        assertEquals(
            listOf("moien", "moi", "Moies", "Moins"),
            fusion(
                listOf(lb("moien", 9f), lb("moi", 8f), lb("Moies", 7f)),
                listOf(fr("Moi", 6f), fr("Moins", 5f))
            )
        )
    }

    @Test
    fun leLuxembourgeoisGardeLesTroisPremieresPlaces() {
        assertEquals(
            listOf("a", "b", "c", "x", "y"),
            fusion(
                listOf(lb("a", 1f), lb("b", 1f), lb("c", 1f), lb("d", 1f)),
                listOf(fr("x", 99f), fr("y", 99f))
            )
        )
    }
}
