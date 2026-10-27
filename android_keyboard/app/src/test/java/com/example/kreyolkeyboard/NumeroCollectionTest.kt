package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.carnet.ImpressionCarte
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Le numéro de collection des cartes imprimées (`ImpressionCarte.numero`).
 *
 * Il se lit sur l'ordre des listes de `cartes_imprimees.json` : si
 * `scripts/candidats_cartes.py` réordonne la boîte, chaque carte change de
 * numéro et une liste à cocher déjà imprimée ne correspond plus aux cartes.
 * Ce test le fait voir avant l'impression.
 */
class NumeroCollectionTest {

    private fun ensembles(): Map<String, List<String>> {
        val racine = JSONObject(File("src/main/assets/cartes_imprimees.json").readText()).getJSONObject("ensembles")
        return racine.keys().asSequence().associateWith { id ->
            val cartes = racine.getJSONObject(id).getJSONArray("cartes")
            (0 until cartes.length()).map { cartes.getString(it) }
        }
    }

    @Test
    fun laBoiteVaDeUnACinquanteDeux() {
        val e = ensembles()
        assertEquals(1, ImpressionCarte.numero("Kaz", null, e))
        assertEquals(24, ImpressionCarte.numero("eent", 1, e))
        assertEquals(52, ImpressionCarte.numero("sechs", 6, e))
    }

    @Test
    fun lesExtensionsSuiventLaBoite() {
        val e = ensembles()
        assertEquals(53, ImpressionCarte.numero("Buch", null, e))
        assertEquals(77, ImpressionCarte.numero("Bam", null, e))
        assertEquals(100, ImpressionCarte.numero("giel", null, e))
    }

    @Test
    fun horsCollectionPasDeNumero() {
        val e = ensembles()
        assertNull("la carte promo n'est pas numérotée", ImpressionCarte.numero("Fierkelsmaart", null, e))
        assertNull(ImpressionCarte.numero("Haus", null, e))
        // Un numéral se reconnaît à sa valeur, pas à sa seule forme.
        assertNull(ImpressionCarte.numero("eent", null, e))
    }

    @Test
    fun unEnsembleManquantNeDecalePasLesNumeros() {
        val e = ensembles() - "B1"
        assertNull(ImpressionCarte.numero("Buch", null, e))
    }

    @Test
    fun chaqueCarteDeLaCollectionASonPropreNumero() {
        val e = ensembles()
        val cles = ImpressionCarte.COLLECTION.flatMap { id ->
            assertTrue("ensemble $id absent de cartes_imprimees.json", id in e)
            e.getValue(id)
        }
        assertEquals("une carte figure dans deux ensembles", cles.size, cles.toSet().size)
        val numeros = cles.map { cle ->
            val (forme, nombre) = cle.split('#').let { it[0] to it.getOrNull(1)?.toInt() }
            ImpressionCarte.numero(forme, nombre, e)
        }
        assertEquals((1..cles.size).toList(), numeros)
    }
}
