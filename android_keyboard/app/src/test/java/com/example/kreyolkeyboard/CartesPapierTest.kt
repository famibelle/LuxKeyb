package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.carnet.Blasonnement
import com.example.kreyolkeyboard.carnet.CarteFierkelsmaart
import com.example.kreyolkeyboard.carnet.CarteMot
import com.example.kreyolkeyboard.carnet.CartesPapier
import com.example.kreyolkeyboard.carnet.CartesPapier.CarteImprimee
import com.example.kreyolkeyboard.carnet.Champ
import com.example.kreyolkeyboard.carnet.ContenuCarte
import com.example.kreyolkeyboard.carnet.JeuCarte
import com.example.kreyolkeyboard.carnet.Lieu
import com.example.kreyolkeyboard.carnet.Rarete
import com.example.kreyolkeyboard.carnet.Series
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
 * Le code [mots] a été signé par `scripts/codes_cartes.py` avec la vraie clé
 * privée : s'il ne se lit plus, le script et l'appli ne parlent plus le même
 * format, ou la clé publique a changé, et toutes les cartes codes déjà
 * imprimées deviennent muettes. Il ne donne que deux cartes qui se gagnent au
 * clavier : **aucun autre code signé ne doit entrer dans le dépôt**, qui est
 * public (un code de la boîte ou de la Fierkelsmaart y serait offert à tous).
 */
class CartesPapierTest {

    private val mots =
        "LUXKEYB:P:1:d8f59e7b2edc:Kaz,eent#1:MEUCIQCJspmIxqgXIjLIT-Trn2C5uAYTy6ckO3GR11bCJuDJTgIgdbSQmqui2koeaA5GfDKXZjubUaLZ-08MG9nm_ugB0w4"

    private fun ensembles(): Map<String, List<String>> {
        val racine = JSONObject(File("src/main/assets/cartes_imprimees.json").readText()).getJSONObject("ensembles")
        return racine.keys().asSequence().associateWith { id ->
            val cartes = racine.getJSONObject(id).getJSONArray("cartes")
            (0 until cartes.length()).map { cartes.getString(it) }
        }
    }

    @Test
    fun unCodeDuScriptSeLit() {
        assertEquals(CartesPapier.Code("d8f59e7b2edc", listOf("Kaz", "eent#1")), CartesPapier.lire(mots))
    }

    @Test
    fun laBoiteDonneSesCinquanteDeuxCartes() {
        val cartes = CartesPapier.cartes(CartesPapier.Code("000000000000", listOf("@B1")), ensembles())!!
        assertEquals(52, cartes.size)
        assertEquals((1..6).toList(), cartes.mapNotNull { it.nombre })
        assertTrue(CarteImprimee("Kaz", null) in cartes)
    }

    @Test
    fun laFierkelsmaartSeDonneParSonCode() {
        assertEquals(
            listOf(CarteImprimee("Fierkelsmaart", null)),
            CartesPapier.cartes(CartesPapier.Code("000000000000", listOf("@FK27")), ensembles())
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
        // La signature de deux cartes du clavier, sur la Fierkelsmaart : pour rien.
        assertNull(CartesPapier.lire(mots.replace(":Kaz,eent#1:", ":@FK27:")))
        assertNull(CartesPapier.lire(mots.replace("d8f59e7b2edc", "d8f59e7b2edd")))
        assertNull(CartesPapier.lire(mots.replace("Kaz,", "Hond,")))
        assertNull(CartesPapier.lire(mots.dropLast(4)))
        assertNull(CartesPapier.lire("LUXKEYB:P:1:d8f59e7b2edc:@B1"))
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
        assertTrue(CartesPapier.estCode(" $mots\n"))
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

    private fun contenu(forme: String, jeux: Set<JeuCarte>, champ: Champ? = Champ.VIE) = ContenuCarte(
        carte = CarteMot(forme = forme, premiereFois = 0L, rencontres = 1, numero = 1, jeux = jeux),
        rarete = Rarete.COMMUN,
        rang = 100,
        glose = "",
        autresFormes = emptyList(),
        exemple = null,
        blason = Blasonnement(champ = champ)
    )

    /** Le Breedewee est le lieu du papier, et le papier l'emporte sur les jeux. */
    @Test
    fun uneCarteVenueDuPapierMontreLeBreedewee() {
        for (jeux in listOf(setOf(JeuCarte.PAPIER), setOf(JeuCarte.WUERTSICH, JeuCarte.PAPIER))) {
            val c = CartesPapier.corriger(contenu("Kaz", jeux))
            assertEquals(Lieu.BREEDEWEE, Lieu.de(c.blason))
            assertEquals(Champ.VIE, c.blason.champ)
        }
    }

    @Test
    fun uneCarteGagneeOuOfferteGardeLeLieuDeSonChamp() {
        for (jeu in JeuCarte.values().filter { it != JeuCarte.PAPIER }) {
            val c = CartesPapier.corriger(contenu("Kaz", setOf(jeu)))
            assertEquals(Lieu.MELLERDALL, Lieu.de(c.blason))
        }
    }

    /** Scanner une carte ne déplace pas sa case dans l'album : c'est celle de son champ. */
    @Test
    fun laCaseDeLAlbumResteCelleDuChamp() {
        val c = CartesPapier.corriger(contenu("Kaz", setOf(JeuCarte.PAPIER)))
        assertEquals(Series.cleDePaysage(Lieu.MELLERDALL, Rarete.COMMUN), Series.paysage(c))
    }

    @Test
    fun laFierkelsmaartGardeSaFoire() {
        val c = CartesPapier.corriger(
            CarteFierkelsmaart.corriger(contenu(CarteFierkelsmaart.FORME, setOf(JeuCarte.PAPIER)), "g", "t")
        )
        assertEquals(Lieu.FOUER, Lieu.de(c.blason))
    }
}
