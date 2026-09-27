package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.zuelen.ZuelenData
import com.example.kreyolkeyboard.zuelen.ZuelenDifficulty
import com.example.kreyolkeyboard.zuelen.ZuelenSpeller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
                    val acceptees = ZuelenSpeller.variantes(question.produit)
                    val justes = question.options.filter { o -> o.juste }
                    assertEquals("une seule bonne réponse", 1, justes.size)
                    assertEquals(
                        "la bonne réponse est la forme retenue",
                        ZuelenSpeller.enLettres(question.produit), justes[0].texte
                    )
                    for (option in question.options.filter { o -> !o.juste }) {
                        assertFalse(
                            "leurre « ${option.texte} » accepté pour ${question.produit}",
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
                    manche.size, manche.map { q -> q.produit }.toSet().size
                )
                for (question in manche) {
                    assertEquals(
                        ZuelenData.OPTIONS_PER_QUESTION, question.options.size
                    )
                    assertEquals(
                        "propositions dupliquées pour ${question.produit}",
                        question.options.size,
                        question.options.map { o -> o.texte }.toSet().size
                    )
                    assertTrue(question.produit in 1..ZuelenSpeller.MAXIMUM)
                }
            }
        }
    }

    /** Le produit n'est caché qu'au niveau le plus dur. */
    @Test
    fun testLeProduitEstCacheEnDifficile() {
        assertTrue(
            ZuelenData.newRound(ZuelenDifficulty.NORMALE, Random(1))
                .all { q -> q.enonce.endsWith("= ${q.produit}") }
        )
        assertTrue(
            ZuelenData.newRound(ZuelenDifficulty.DIFFICILE, Random(1))
                .all { q -> q.enonce.endsWith("= ?") }
        )
    }
}
