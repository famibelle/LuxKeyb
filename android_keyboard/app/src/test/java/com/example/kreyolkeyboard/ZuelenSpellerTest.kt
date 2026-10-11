package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.zuelen.ZuelenData
import com.example.kreyolkeyboard.zuelen.ZuelenOperation
import com.example.kreyolkeyboard.zuelen.ZuelenDifficulty
import com.example.kreyolkeyboard.zuelen.ZuelenSpeller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

/**
 * Le Zuelwuert enseigne une orthographe : il n'a donc pas le droit de se
 * tromper. Ces tests figent les 101 formes de 0 à 100.
 *
 * **Elles ne sont pas de nous.** Chacune a été relevée dans l'index de
 * recherche du Lëtzebuerger Online Dictionnaire (`new_lod-search.xml`,
 * data.public.lu, CC0) parmi les graphies marquées `suggest="true"`, le
 * 2026-09-02. Vérification faite dans les deux sens : aucune forme produite ici
 * n'est absente du LOD, et aucune graphie composée que le LOD suggère n'échappe
 * à `variantes()`.
 *
 * Corriger une de ces chaînes sans l'avoir retrouvée dans le LOD, c'est
 * apprendre une faute aux joueurs.
 */
class ZuelenSpellerTest {

    private val attendu = listOf(
        "null", "eent", "zwee", "dräi",
        "véier", "fënnef", "sechs", "siwen",
        "aacht", "néng", "zéng", "eelef",
        "zwielef", "dräizéng", "véierzéng", "fofzéng",
        "siechzéng", "siwwenzéng", "uechtzéng", "nonzéng",
        "zwanzeg", "eenanzwanzeg", "zweeanzwanzeg", "dräianzwanzeg",
        "véieranzwanzeg", "fënnefanzwanzeg", "sechsanzwanzeg", "siwenanzwanzeg",
        "aachtanzwanzeg", "nénganzwanzeg", "drësseg", "eenandrësseg",
        "zweeandrësseg", "dräiandrësseg", "véierandrësseg", "fënnefandrësseg",
        "sechsandrësseg", "siwenandrësseg", "aachtandrësseg", "néngandrësseg",
        "véierzeg", "eenavéierzeg", "zweeavéierzeg", "dräiavéierzeg",
        "véieravéierzeg", "fënnefavéierzeg", "sechsavéierzeg", "siwenavéierzeg",
        "aachtavéierzeg", "néngavéierzeg", "fofzeg", "eenafofzeg",
        "zweeafofzeg", "dräiafofzeg", "véierafofzeg", "fënnefafofzeg",
        "sechsafofzeg", "siwenafofzeg", "aachtafofzeg", "néngafofzeg",
        "sechzeg", "eenasechzeg", "zweeasechzeg", "dräiasechzeg",
        "véierasechzeg", "fënnefasechzeg", "sechsasechzeg", "siwenasechzeg",
        "aachtasechzeg", "néngasechzeg", "siwwenzeg", "eenasiwwenzeg",
        "zweeasiwwenzeg", "dräiasiwwenzeg", "véierasiwwenzeg", "fënnefasiwwenzeg",
        "sechsasiwwenzeg", "siwenasiwwenzeg", "aachtasiwwenzeg", "néngasiwwenzeg",
        "achtzeg", "eenanachtzeg", "zweeanachtzeg", "dräianachtzeg",
        "véieranachtzeg", "fënnefanachtzeg", "sechsanachtzeg", "siwenanachtzeg",
        "aachtanachtzeg", "nénganachtzeg", "nonzeg", "eenannonzeg",
        "zweeannonzeg", "dräiannonzeg", "véierannonzeg", "fënnefannonzeg",
        "sechsannonzeg", "siwenannonzeg", "aachtannonzeg", "néngannonzeg",
        "honnert"
    )

    @Test
    fun testLesCentUnNombresSontCeuxDuLod() {
        assertEquals(101, attendu.size)
        for (n in 0..100) {
            assertEquals("nombre $n", attendu[n], ZuelenSpeller.enLettres(n))
        }
    }

    @Test
    fun testHorsBornesRendUneChaineVide() {
        assertEquals("", ZuelenSpeller.enLettres(-1))
        assertEquals("", ZuelenSpeller.enLettres(101))
    }

    /**
     * La règle d'Eifel est le cœur du jeu : le n de la liaison se maintient
     * devant n, d, t, z, h et les voyelles, il tombe partout ailleurs.
     */
    @Test
    fun testRegleDEifel() {
        assertEquals("sechsandrësseg", ZuelenSpeller.enLettres(36))   // d : n gardé
        assertEquals("sechsafofzeg", ZuelenSpeller.enLettres(56))     // f : n tombé
        assertEquals("sechsanzwanzeg", ZuelenSpeller.enLettres(26))   // z : n gardé
        assertEquals("sechsavéierzeg", ZuelenSpeller.enLettres(46))   // v : n tombé
        assertEquals("sechsasechzeg", ZuelenSpeller.enLettres(66))    // s : n tombé
        assertEquals("sechsasiwwenzeg", ZuelenSpeller.enLettres(76))  // s : n tombé
        assertEquals("sechsanachtzeg", ZuelenSpeller.enLettres(86))   // a : n gardé
        assertEquals("sechsannonzeg", ZuelenSpeller.enLettres(96))    // n : n gardé
    }

    /** 1 se dit `eent` compté seul et `een` dans un composé. */
    @Test
    fun testUniteIsoleeEtComposee() {
        assertEquals("eent", ZuelenSpeller.enLettres(1))
        assertEquals("eenanzwanzeg", ZuelenSpeller.enLettres(21))
    }

    @Test
    fun testVariantesAcceptees() {
        assertTrue("siechzeg" in ZuelenSpeller.variantes(60))
        assertTrue("uechtzeg" in ZuelenSpeller.variantes(80))
        assertTrue("ning" in ZuelenSpeller.variantes(9))
        assertTrue("zwou" in ZuelenSpeller.variantes(2))
        // Le composé croise les deux axes : néng/ning × sechzeg/siechzeg.
        val quatreVingtNeuf = ZuelenSpeller.variantes(69)
        assertTrue("néngasechzeg" in quatreVingtNeuf)
        assertTrue("ningasiechzeg" in quatreVingtNeuf)
    }

    @Test
    fun testAllemandDiffereDuLuxembourgeois() {
        assertEquals("sechsundfünfzig", ZuelenSpeller.enAllemand(56))
        assertEquals("einundzwanzig", ZuelenSpeller.enAllemand(21))
        assertEquals("dreißig", ZuelenSpeller.enAllemand(30))
    }

    /**
     * Le piège que ce test garde : un leurre qui serait une graphie que le LOD
     * accepte pour le même nombre donnerait deux bonnes réponses à la question.
     */
    @Test
    fun testAucunLeurreNEstUneVarianteAcceptee() {
        val random = Random(20260902)
        for (niveau in ZuelenDifficulty.values()) {
            repeat(200) {
                for (question in ZuelenData.newRound(niveau, random)) {
                    val acceptees = ZuelenSpeller.variantes(question.nombre)
                    val justes = question.options.filter { o -> o.juste }
                    assertEquals("une seule bonne réponse", 1, justes.size)
                    assertEquals(
                        "la bonne réponse est la forme retenue",
                        ZuelenSpeller.enLettres(question.nombre), justes[0].texte
                    )
                    for (option in question.options.filter { o -> !o.juste }) {
                        assertFalse(
                            "leurre « ${option.texte} » accepté pour ${question.nombre}",
                            option.texte in acceptees
                        )
                    }
                }
            }
        }
    }

    @Test
    fun testChaqueQuestionADesPropositionsDistinctes() {
        val random = Random(7)
        for (niveau in ZuelenDifficulty.values()) {
            repeat(200) {
                val manche = ZuelenData.newRound(niveau, random)
                assertEquals(ZuelenData.QUESTIONS_PER_ROUND, manche.size)
                // Deux fois le même produit poserait deux fois la même
                // question d'orthographe dans la même manche.
                assertEquals(
                    manche.size, manche.map { q -> q.nombre }.toSet().size
                )
                for (question in manche) {
                    assertEquals(
                        ZuelenData.OPTIONS_PER_QUESTION, question.options.size
                    )
                    assertEquals(
                        "propositions dupliquées pour ${question.nombre}",
                        question.options.size,
                        question.options.map { o -> o.texte }.toSet().size
                    )
                    assertTrue(question.nombre in 0..ZuelenSpeller.MAXIMUM)
                }
            }
        }
    }

    /** Le résultat n'est caché qu'au niveau le plus dur ; en Facile il n'y a que le nombre. */
    @Test
    fun testLeProduitEstCacheEnDifficile() {
        assertTrue(
            ZuelenData.newRound(ZuelenDifficulty.FACILE, Random(1))
                .all { q -> q.enonce == "${q.nombre}" && q.operation == ZuelenOperation.AUCUNE }
        )
        assertTrue(
            ZuelenData.newRound(ZuelenDifficulty.NORMALE, Random(1))
                .all { q -> q.enonce.endsWith("= ${q.nombre}") }
        )
        assertTrue(
            ZuelenData.newRound(ZuelenDifficulty.DIFFICILE, Random(1))
                .all { q -> q.enonce.endsWith("= ?") && q.solution.endsWith("= ${q.nombre}") }
        )
    }

    /**
     * L'opération tombe juste, et elle demande un calcul : une multiplication
     * des tables quand le nombre en est une, sinon une addition sans 0 ni 1.
     */
    @Test
    fun testLOperationDonneLeNombre() {
        val random = Random(37)
        val tables = 2..10
        val produits = tables.flatMap { g -> tables.map { d -> g * d } }.toSet()
        for (niveau in listOf(ZuelenDifficulty.NORMALE, ZuelenDifficulty.DIFFICILE)) {
            repeat(300) {
                for (q in ZuelenData.newRound(niveau, random)) {
                    when (q.operation) {
                        ZuelenOperation.FOIS -> {
                            assertEquals(q.nombre, q.gauche * q.droite)
                            assertTrue(q.gauche in tables && q.droite in tables)
                        }
                        ZuelenOperation.PLUS -> {
                            assertEquals(q.nombre, q.gauche + q.droite)
                            assertTrue("$q", q.gauche >= 2 && q.droite >= 2)
                            assertFalse("${q.nombre} est un produit des tables", q.nombre in produits)
                        }
                        ZuelenOperation.AUCUNE -> fail("une opération est attendue en $niveau")
                    }
                }
            }
        }
    }

    /** Normal et Difficile ne posent que des nombres composés : il y a une orthographe à discuter. */
    @Test
    fun testNormalEtDifficileNeTirentQueDesComposes() {
        val random = Random(5)
        for (niveau in listOf(ZuelenDifficulty.NORMALE, ZuelenDifficulty.DIFFICILE)) {
            repeat(200) {
                ZuelenData.newRound(niveau, random).forEach { q ->
                    assertTrue("${q.nombre}", q.nombre in 21..99 && q.nombre % 10 != 0)
                }
            }
        }
        assertEquals(101, ZuelenData.nombres(ZuelenDifficulty.FACILE).size)
        assertEquals(72, ZuelenData.nombres(ZuelenDifficulty.NORMALE).size)
    }

    /** Les nombres qui manquent au carnet passent devant, sans prendre toute la manche. */
    @Test
    fun testLesNombresManquantsPassentDevant() {
        val random = Random(11)
        val manquants = setOf(0, 13, 22, 47, 70, 83, 100, 99)
        repeat(100) {
            val tires = ZuelenData.newRound(ZuelenDifficulty.FACILE, random, manquants).map { it.nombre }
            // Au moins six : le complément tiré au hasard peut en ramener d'autres.
            assertTrue(tires.count { it in manquants } >= ZuelenData.MANQUANTS_PAR_MANCHE)
        }
        // Deux manquants seulement : les deux sortent, le reste est complété.
        val manche = ZuelenData.newRound(ZuelenDifficulty.FACILE, random, setOf(4, 64)).map { it.nombre }
        assertTrue(4 in manche && 64 in manche)
        assertEquals(ZuelenData.QUESTIONS_PER_ROUND, manche.size)
    }
}
