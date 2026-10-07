package com.example.kreyolkeyboard.actualites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La lecture du flux RSS de l'INLL, sur un extrait du vrai flux. */
class FluxInllTest {

    private val extrait = """<?xml version="1.0" encoding="UTF-8"?><rss version="2.0"
        xmlns:content="http://purl.org/rss/1.0/modules/content/"
        xmlns:dc="http://purl.org/dc/elements/1.1/">
    <channel>
        <title>INLL</title>
        <item>
            <title>Walk &#038; Talk Belval</title>
            <link>https://www.inll.lu/fr/walk-talk-belval-7/</link>
            <dc:creator><![CDATA[Goedert Jil]]></dc:creator>
            <pubDate>Wed, 07 Oct 2026 06:43:01 +0000</pubDate>
            <category><![CDATA[tv]]></category>
            <description><![CDATA[<p>Marchez, échangez et progressez dans une langue étrangère [&#8230;]</p>
<p>L’article <a href="https://x/">Walk</a> est apparu en premier sur <a href="https://x">INLL</a>.</p>]]></description>
        </item>
        <item>
            <title></title>
            <link>https://www.inll.lu/fr/sans-titre/</link>
        </item>
        <item>
            <title>Recrutement : Chef(fe) de service adjoint du service des finances (m/f)</title>
            <link>https://www.inll.lu/fr/recrutement/</link>
        </item>
        <item>
            <title>Poterkëscht</title>
            <link>https://www.inll.lu/fr/poterkescht-2/</link>
            <pubDate>pas une date</pubDate>
        </item>
    </channel>
    </rss>"""

    private val actualites = FluxInll.lire(extrait.byteInputStream())

    @Test
    fun uneEntreeSansTitreOuUneOffreDEmploiEstIgnoree() {
        assertEquals(listOf("Walk & Talk Belval", "Poterkëscht"), actualites.map { it.titre })
    }

    @Test
    fun laDateEstLueEnRfc822() {
        // 2026-10-07T06:43:01Z
        assertEquals(1_791_355_381_000L, actualites[0].date)
        assertEquals(null, actualites[1].date)
    }

    @Test
    fun leResumePerdLeHtmlEtLaSignatureWordPress() {
        val resume = actualites[0].resume
        assertEquals("Marchez, échangez et progressez dans une langue étrangère …", resume)
        assertTrue(actualites[1].resume.isEmpty())
    }

    @Test
    fun lAdresseSuitLaLangue() {
        assertEquals("https://www.inll.lu/lu/feed/", FluxInll.adresse("lu"))
        assertNotNull(actualites[0].lien)
    }

    @Test
    fun uneOffreDEmploiSeReconnaitAuDebutDuTitre() {
        assertTrue(FluxInll.estOffreEmploi("Recrutement : Chef(fe) de service"))
        assertTrue(FluxInll.estOffreEmploi("  recruitment: teacher"))
        assertEquals(false, FluxInll.estOffreEmploi("Communiqué de presse : recrutement des formateurs"))
        assertEquals(false, FluxInll.estOffreEmploi("Sproochentest"))
    }
}
