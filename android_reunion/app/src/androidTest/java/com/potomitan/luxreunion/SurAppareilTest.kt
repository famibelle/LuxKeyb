package com.potomitan.luxreunion

import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.HandlerThread
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.potomitan.luxreunion.noyau.DirectLuxAsr
import com.potomitan.luxreunion.noyau.FabriqueOkHttp
import com.potomitan.luxreunion.noyau.LotsLuxAsr
import com.potomitan.luxreunion.noyau.Normaliseur
import com.potomitan.luxreunion.noyau.Paragraphe
import com.potomitan.luxreunion.noyau.RedacteurClaude
import com.potomitan.luxreunion.noyau.Reunion
import com.potomitan.luxreunion.noyau.TexteTranscription
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.sin

/**
 * Ce que la JVM ne peut pas garantir : le SDK Claude (Jackson, kotlin-reflect),
 * OkHttp et l'encodeur AAC sur un vrai Android, et des écrans qui s'ouvrent.
 * Les services restent faux et locaux : aucun appel à LuxASR ni à Claude.
 */
@RunWith(AndroidJUnit4::class)
class SurAppareilTest {

    private val instr = InstrumentationRegistry.getInstrumentation()
    private val app get() = ReunionApp.instance
    private lateinit var serveur: MockWebServer

    @Before fun demarrer() { serveur = MockWebServer().apply { start() } }
    @After fun arreter() { serveur.shutdown() }

    private fun base() = serveur.url("/").toString().trimEnd('/')

    @Test
    fun normaliseurSurUnReleveReel() {
        val brut = instr.context.assets.open("releves/resultat-2voix.json").reader().readText()
        val d = Normaliseur.normaliser(brut)
        assertEquals(listOf("SPEAKER_01", "SPEAKER_00"), d.locuteurs)
        assertEquals(1, d.boucles.size)
        val (texte, _) = TexteTranscription.depuisLots(listOf(d to 0.0))
        assertTrue(texte, texte.startsWith("[0:00] Voix 1 : "))
    }

    @Test
    fun sdkClaudeEnFlux() {
        val sse = listOf(
            "message_start" to """{"type":"message_start","message":{"id":"m","type":"message","role":"assistant","model":"claude-opus-5-5","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":3,"output_tokens":1}}}""",
            "content_block_start" to """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""",
            "content_block_delta" to """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"COMPTE RENDU"}}""",
            "content_block_stop" to """{"type":"content_block_stop","index":0}""",
            "message_delta" to """{"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":2}}""",
            "message_stop" to """{"type":"message_stop"}"""
        ).joinToString("") { (t, d) -> "event: $t\ndata: $d\n\n" }
        serveur.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(sse))
        assertEquals("COMPTE RENDU", RedacteurClaude("sk-test", baseUrl = base()).rediger("Système", "Texte"))

        serveur.enqueue(MockResponse().setResponseCode(401)
            .setBody("""{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""))
        try { RedacteurClaude("x", baseUrl = base()).verifierCle(); fail() }
        catch (e: RedacteurClaude.Erreur) { assertEquals(RedacteurClaude.Code.CLE, e.code) }
    }

    @Test
    fun lotsSurAndroid() {
        serveur.enqueue(MockResponse().setResponseCode(202).setBody("""{"job_id":"j1","status":"queued"}"""))
        serveur.enqueue(MockResponse().setBody("""{"job_id":"j1","status":"completed"}"""))
        serveur.enqueue(MockResponse().setBody("""[{"speaker":"SPEAKER_00","start":0,"end":1,"text":"Moien."}]"""))
        val dossier = instr.targetContext.cacheDir.resolve("lots").apply { mkdirs() }
        val audio = dossier.resolve("partie-1.m4a").apply { writeBytes(ByteArray(10_000)) }
        val lots = LotsLuxAsr(base(), pause = {})
        val job = lots.soumettre(audio, "audio/mp4", LotsLuxAsr.Options(locuteurs = 2))
        val sortie = dossier.resolve("lots-1.json")
        lots.attendre(job, sortie)
        assertEquals("Moien.", Normaliseur.normaliser(sortie.readText()).segments.single().texte)
        assertEquals(10_000L, serveur.takeRequest().bodySize)
    }

    @Test
    fun directSurUnVraiWebSocket() {
        serveur.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("\"stop\"")) {
                    webSocket.send("""{"type":"transcription","accumulated_text":"Moien.","partial_text":"","send_reason":"silence_commit"}""")
                    webSocket.send("""{"type":"translation","success":true,"accumulated_translation":"Bonjour."}""")
                    webSocket.send("""{"type":"recording_stopped"}""")
                }
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
            override fun onOpen(webSocket: WebSocket, response: Response) {}
        }))
        val fil = HandlerThread("test-direct").apply { start() }
        val planif = PlanificateurAndroid(fil.looper)
        val fini = CountDownLatch(1)
        var resultat: List<Paragraphe> = emptyList()
        val url = serveur.url("/prod/ws/transcribe").toString().replaceFirst("http", "ws")
        val direct = DirectLuxAsr(object : DirectLuxAsr.Ecouteur {
            override fun surEtat(etat: DirectLuxAsr.Etat) {}
            override fun surTexte(paragraphes: List<Paragraphe>) {}
            override fun surFin(paragraphes: List<Paragraphe>, raison: DirectLuxAsr.Raison?) {
                resultat = paragraphes; fini.countDown()
            }
        }, FabriqueOkHttp(planif, url), planif)
        planif.plus(0) { direct.start() }
        repeat(5) { planif.plus(100L * it) { direct.audio(ByteArray(5120)) } }
        planif.plus(1500) { direct.stop() }
        assertTrue(fini.await(15, TimeUnit.SECONDS))
        assertEquals("Moien.", resultat.single().texte)
        assertEquals("Bonjour.", resultat.single().traduction)
        fil.quitSafely()
    }

    @Test
    fun encodeurAac() {
        val r = app.registre.nouvelle(System.currentTimeMillis(), Reunion.Mode.DIRECT)
        try {
            // Cinq secondes d'un la à 440 Hz.
            val n = 5 * Capture.RATE
            val pcm = ByteArray(n * 2)
            for (i in 0 until n) {
                val v = (sin(2 * PI * 440 * i / Capture.RATE) * 8000).toInt()
                pcm[2 * i] = (v and 0xFF).toByte()
                pcm[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
            }
            Encodeur.ajouter(r.fichierPcm, pcm)
            val parties = Encodeur.parties(r) {}
            assertEquals(1, parties.size)
            assertEquals("audio/mp4", parties[0].type)
            val ex = MediaExtractor()
            ex.setDataSource(r.fichier(parties[0].fichier).path)
            assertEquals(1, ex.trackCount)
            val f = ex.getTrackFormat(0)
            assertEquals(MediaFormat.MIMETYPE_AUDIO_AAC, f.getString(MediaFormat.KEY_MIME))
            val duree = f.getLong(MediaFormat.KEY_DURATION) / 1e6
            assertTrue("durée $duree s", duree in 4.7..5.3)
            ex.release()
        } finally {
            app.registre.supprimer(r)
        }
    }

    @Test
    fun cleChiffreeParLeKeystore() {
        val reglages = app.reglages
        val avant = reglages.cle
        reglages.cle = "sk-ant-essai"
        assertEquals("sk-ant-essai", reglages.cle)
        reglages.cle = avant
    }

    @Test
    fun lesEcransSOuvrent() {
        val ctx = instr.targetContext
        ActivityScenario.launch(AccueilActivity::class.java).use { assertEquals(Lifecycle.State.RESUMED, it.state) }
        ActivityScenario.launch<ReglagesActivity>(Intent(ctx, ReglagesActivity::class.java)
            .putExtra(ReglagesActivity.EXTRA_ADMIN, true)).use { assertEquals(Lifecycle.State.RESUMED, it.state) }
        for (mode in Reunion.Mode.values()) {
            ActivityScenario.launch<PreparationActivity>(Intent(ctx, PreparationActivity::class.java)
                .putExtra(PreparationActivity.EXTRA_MODE, mode.name)).use { assertEquals(Lifecycle.State.RESUMED, it.state) }
        }
        val r = app.registre.nouvelle(System.currentTimeMillis(), Reunion.Mode.DIRECT).apply {
            titre = "Comité de direction"
            dureeS = 4980.0
            etape = Reunion.Etape.PRETE
            enregistrer()
            enregistrerDirect(listOf(Paragraphe(0.0, "Moien.", traduction = "Bonjour.")))
            Reunion.ecrireAtomique(fichierCompteRendu, "COMPTE RENDU — Comité de direction\n")
            Reunion.ecrireAtomique(fichierTranscription, "[0:00] Voix 1 : Moien.\n")
        }
        try {
            ActivityScenario.launch<ReunionActivity>(Intent(ctx, ReunionActivity::class.java)
                .putExtra(ReunionActivity.EXTRA_ID, r.id)).use { assertEquals(Lifecycle.State.RESUMED, it.state) }
        } finally {
            app.registre.supprimer(r)
        }
    }
}
