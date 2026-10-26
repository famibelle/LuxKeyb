package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.carnet.CartesPapier
import com.example.kreyolkeyboard.carnet.CartesPapier.CarteImprimee
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlin.random.Random

/**
 * Les codes des cartes en papier (`carnet/CartesPapier.kt`).
 *
 * Les trois premiers codes ont été signés par `scripts/codes_cartes.py` avec
 * la vraie clé privée : s'ils ne se lisent plus, le script et l'appli ne
 * parlent plus le même format, ou la clé publique de l'appli a changé, et
 * toutes les cartes codes déjà imprimées deviennent muettes.
 */
class CartesPapierTest {

    private val boite =
        "LUXKEYB:P:1:c353757374dd:@B1:MEYCIQDr7MluSySmx_91bL0NxolQ6UDcJcQ6qEAlMEBZmib0fwIhAIljKtCYGJA7uoybmkc7ktXl0b9kJjAvz5p2lOmnSZho"
    private val fierkelsmaart =
        "LUXKEYB:P:1:d9931871c4ce:@FK27:MEUCIQCiQgfDdcm1Uu7VqWhCmJVwM2Ul-nWstePh3YlSbtIJSQIgK0IgmK1Ft4Xx4objS7Gegp9nMd9oJOOvENIdgoP9X1E"
    private val mots =
        "LUXKEYB:P:1:abcfc02548fb:Kaz,eent#1:MEQCIE9NbFI8WI8g-9g5YUhfvXz0IIHn1PZITCziD7uAnhF-AiAbt7c-sgddtJHyhkrcKwfQ4f9c0SJtdKQ9opfbcbcKdg"

    private fun ensembles(): Map<String, List<String>> {
        val racine = JSONObject(File("src/main/assets/cartes_imprimees.json").readText()).getJSONObject("ensembles")
        return racine.keys().asSequence().associateWith { id ->
            val cartes = racine.getJSONObject(id).getJSONArray("cartes")
            (0 until cartes.length()).map { cartes.getString(it) }
        }
    }

    @Test
    fun lesCodesDuScriptSeLisent() {
        assertEquals(CartesPapier.Code("c353757374dd", listOf("@B1")), CartesPapier.lire(boite))
        assertEquals(CartesPapier.Code("d9931871c4ce", listOf("@FK27")), CartesPapier.lire(fierkelsmaart))
        assertEquals(CartesPapier.Code("abcfc02548fb", listOf("Kaz", "eent#1")), CartesPapier.lire(mots))
    }

    @Test
    fun laBoiteDonneSesCinquanteDeuxCartes() {
        val cartes = CartesPapier.cartes(CartesPapier.lire(boite)!!, ensembles())!!
        assertEquals(52, cartes.size)
        assertEquals((1..6).toList(), cartes.mapNotNull { it.nombre })
        assertTrue(CarteImprimee("Kaz", null) in cartes)
    }

    @Test
    fun laFierkelsmaartSeDonneParSonCode() {
        assertEquals(
            listOf(CarteImprimee("Fierkelsmaart", null)),
            CartesPapier.cartes(CartesPapier.lire(fierkelsmaart)!!, ensembles())
        )
    }

    @Test
    fun unMotEtUnNumeral() {
        assertEquals(
            listOf(CarteImprimee("Kaz", null), CarteImprimee("eent", 1)),
            CartesPapier.cartes(CartesPapier.lire(mots)!!, emptyMap())
        )
    }

    /** Un ensemble que l'appli ne connaît pas : une boîte plus récente qu'elle. */
    @Test
    fun unEnsembleInconnuNeDonneRien() {
        assertNull(CartesPapier.cartes(CartesPapier.Code("000000000000", listOf("@B9")), ensembles()))
    }

    @Test
    fun unCodeRetoucheEstRefuse() {
        // La signature de la boîte, sur d'autres cartes : la Fierkelsmaart pour rien.
        assertNull(CartesPapier.lire(boite.replace(":@B1:", ":@FK27:")))
        assertNull(CartesPapier.lire(boite.replace("c353757374dd", "c353757374de")))
        assertNull(CartesPapier.lire(mots.replace("Kaz,", "Hond,")))
        assertNull(CartesPapier.lire(boite.dropLast(4)))
        assertNull(CartesPapier.lire("LUXKEYB:P:1:c353757374dd:@B1"))
        assertNull(CartesPapier.lire("Kaz"))
    }

    /** Une autre clé signe un code juste, que la clé de l'appli refuse. */
    @Test
    fun seuleLaCleDeLAppliOuvreUnCode() {
        val paire = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair()
        val texte = "LUXKEYB:P:1:0123456789ab:@FK27"
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(paire.private)
            update(texte.toByteArray())
            sign()
        }
        val code = "$texte:" + Base64.getUrlEncoder().withoutPadding().encodeToString(signature)
        assertNotNull(CartesPapier.lire(code, paire.public))
        assertNull(CartesPapier.lire(code))
    }

    @Test
    fun leBase64MaisonSuitCeluiDuJdk() {
        val r = Random(7)
        repeat(50) {
            val octets = r.nextBytes(r.nextInt(0, 80))
            assertArrayEquals(octets, CartesPapier.base64(Base64.getEncoder().encodeToString(octets)))
            assertArrayEquals(octets, CartesPapier.base64(Base64.getUrlEncoder().withoutPadding().encodeToString(octets)))
        }
    }

    @Test
    fun unCodeDeCarteSeReconnait() {
        assertTrue(CartesPapier.estCode(" $boite\n"))
        assertFalse(CartesPapier.estCode("LUXKEYB:C:2:0123456789abcdef::Kaz"))
    }

    /** Chaque ensemble de l'asset a des cartes, et chaque numéral sa valeur. */
    @Test
    fun lesEnsemblesSontBienFormes() {
        val e = ensembles()
        assertEquals(52, e.getValue("B1").size)
        for ((id, cartes) in e) {
            assertTrue("$id est vide", cartes.isNotEmpty())
            for (c in cartes) assertFalse("$id : « $c » ne passe pas dans un code", ':' in c || ',' in c)
        }
    }
}
