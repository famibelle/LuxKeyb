package com.potomitan.luxreunion.noyau

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les mêmes cas que docs/scripts/test_luxasr_transcription.js, sur les mêmes
 * relevés réels du 29 octobre 2026 : le normaliseur Kotlin doit rendre
 * exactement ce que rend celui de la page.
 */
class NormaliseurTest {

    private fun releve(nom: String) = javaClass.getResource("/releves/$nom")!!.readText()
    private fun mots(t: String) = t.split(Regex("\\s+")).count { it.isNotEmpty() }

    @Test
    fun `réel à deux voix - la boucle « deen vun » réduite, les neuf changements de voix gardés`() {
        val brut = releve("resultat-2voix.json")
        val source = JSONArray(brut)
        val d = Normaliseur.normaliser(brut)
        assertEquals(listOf("SPEAKER_01", "SPEAKER_00"), d.locuteurs)
        assertEquals(1, d.boucles.size)
        val b = d.boucles[0]
        assertTrue(b.motif, b.motif == "deen vun" || b.motif == "vun deen")
        assertTrue(b.fois >= 15)
        assertTrue("${b.debut}", b.debut!! > 27 && b.debut!! < 29)
        val sortie = d.segments.joinToString(" ") { it.texte }
        assertFalse(Regex("(deen vun ){3}").containsMatchIn(sortie))
        assertTrue(sortie.contains("eent vun deen v fir Arthros."))
        val texteSource = (0 until source.length()).joinToString(" ") { source.getJSONObject(it).getString("text") }
        assertEquals((b.fois - 1) * mots(b.motif), mots(texteSource) - mots(sortie))
        val changements = d.segments.indices.count { it > 0 && d.segments[it].locuteur != d.segments[it - 1].locuteur }
        assertEquals(9, changements)
    }

    @Test
    fun `réel à une voix - deux minutes coupées en phrases, élisions intactes`() {
        val brut = releve("resultat.json")
        val source = JSONArray(brut).getJSONObject(0)
        val d = Normaliseur.normaliser(brut)
        assertEquals("json", d.format)
        assertEquals(listOf("SPEAKER_00"), d.locuteurs)
        assertTrue("${d.segments.size} phrases", d.segments.size >= 10)
        assertEquals(source.getString("text").replace(Regex("\\s+"), " ").trim(),
                     d.segments.joinToString(" ") { it.texte })
        assertTrue(d.segments.any { it.texte.contains("d'Leit") })
        for (s in d.segments) {
            assertTrue("phrase de ${s.fin!! - s.debut!!} s", s.fin!! - s.debut!! <= 9)
            assertTrue("phrase de ${s.texte.length} caractères", s.texte.length <= 115)
        }
        assertEquals(source.getDouble("start"), d.segments.first().debut!!, 0.0)
        assertEquals(source.getDouble("end"), d.segments.last().fin!!, 0.0)
        assertTrue(Normaliseur.tours(d).size >= 3)
        assertEquals(emptyList<Boucle>(), d.boucles)
    }

    @Test
    fun `boucles - cinq répétitions au moins, la vraie répétition intacte`() {
        assertEquals("Nee, nee, nee, nee. Gutt.", Normaliseur.reduireBoucles("Nee, nee, nee, nee. Gutt."))
        assertEquals("Mir hunn et gesot, merci.",
                     Normaliseur.reduireBoucles("Mir hunn et gesot gesot gesot gesot gesot gesot, merci."))
    }

    @Test
    fun `multi-format, texte nu, réponse inconnue`() {
        val multi = """{"text":"Moien","json":[{"start":0.0,"end":1.2,"text":"Moien","speaker":"SPEAKER_00"}]}"""
        val d = Normaliseur.normaliser(multi)
        assertEquals("json", d.format)
        assertEquals("Moien", d.segments.single().texte)
        val t = Normaliseur.normaliser("SPEAKER_00: Moien.\nWéi geet et?\n\nSPEAKER_01: Gutt.")
        assertEquals(listOf("SPEAKER_00", "SPEAKER_01"), t.locuteurs)
        assertEquals("Moien. Wéi geet et?", t.segments[0].texte)
        assertEquals("inconnu", Normaliseur.normaliser("""{"foo": 3}""").format)
    }

    @Test
    fun `horloge et texte lisible`() {
        assertEquals("0:00", Normaliseur.horloge(null))
        assertEquals("2:03", Normaliseur.horloge(123.9))
        assertEquals("1:02:03", Normaliseur.horloge(3723.0))
        val d = Normaliseur.normaliser("""[{"speaker":"A","start":0,"end":2,"text":"Moien."},
            {"speaker":"B","start":3,"end":5,"text":"Salut."}]""")
        assertEquals("[0:00] Anna : Moien.\n\n[0:03] B : Salut.\n",
                     Normaliseur.versTexte(d) { if (it == "A") "Anna" else it })
    }

    @Test
    fun `texte pour Claude - locuteurs numérotés, parties décalées dans le temps`() {
        val un = Normaliseur.normaliser(releve("resultat-2voix.json"))
        val (texte, source) = TexteTranscription.depuisLots(listOf(un to 0.0))
        assertEquals(ConsignesCompteRendu.Source.VOIX, source)
        assertTrue(texte.startsWith("[0:00] Locuteur 1 : "))
        assertTrue(texte.contains("] Locuteur 2 : "))
        assertFalse(texte.contains("SPEAKER"))

        val (deux, _) = TexteTranscription.depuisLots(listOf(un to 0.0, un to 5400.0))
        assertTrue(deux.contains("[1:30:00] Locuteur 1 (partie 2) : "))

        val seul = Normaliseur.normaliser(releve("resultat.json"))
        val (texteSeul, sourceSeul) = TexteTranscription.depuisLots(listOf(seul to 0.0))
        assertEquals(ConsignesCompteRendu.Source.SANS_VOIX, sourceSeul)
        assertFalse(texteSeul.contains("Locuteur"))
    }
}
