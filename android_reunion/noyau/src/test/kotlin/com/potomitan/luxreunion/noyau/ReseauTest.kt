package com.potomitan.luxreunion.noyau

import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.ByteString
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Les vrais clients (OkHttp, SDK Claude) contre de faux services locaux. */
class ReseauTest {

    @get:Rule val tmp = TemporaryFolder()
    private lateinit var serveur: MockWebServer

    @Before fun demarrer() { serveur = MockWebServer().apply { start() } }
    @After fun arreter() { serveur.shutdown() }

    private fun base() = serveur.url("/").toString().trimEnd('/')

    // --- Lots -------------------------------------------------------------------

    @Test
    fun `lots - octets bruts, parametres, suivi de la file, resultat ecrit une fois`() {
        val sondages = AtomicInteger()
        serveur.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path!!.startsWith("/asr2") ->
                    MockResponse().setResponseCode(202).setBody("""{"job_id":"j42","status":"queued"}""")
                request.path == "/v3/asr/jobs/j42" -> when (sondages.incrementAndGet()) {
                    1 -> MockResponse().setBody("""{"job_id":"j42","status":"queued","queue_position":2,"eta_seconds":130}""")
                    2 -> MockResponse().setBody("""{"job_id":"j42","status":"processing","eta_seconds":20}""")
                    else -> MockResponse().setBody("""{"job_id":"j42","status":"completed"}""")
                }
                request.path == "/v3/asr/jobs/j42/result" ->
                    MockResponse().setBody("""[{"speaker":"SPEAKER_00","start":0,"end":1,"text":"Moien."}]""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        val audio = tmp.newFile("partie-1.m4a").apply { writeBytes(ByteArray(100_000) { it.toByte() }) }
        val lots = LotsLuxAsr(base(), pause = {})
        val prompt = ConsignesCompteRendu.promptLuxAsr(listOf("Anne Wagner", "Jos Weber"), "Iechternach")
        var progres = 0.0
        val job = lots.soumettre(audio, "audio/mp4", LotsLuxAsr.Options(locuteurs = 2, prompt = prompt)) { progres = it }
        assertEquals("j42", job)
        assertEquals(1.0, progres, 0.0)

        val envoi = serveur.takeRequest()
        assertEquals("POST", envoi.method)
        assertEquals("audio/mp4", envoi.getHeader("Content-Type"))
        assertEquals("partie-1.m4a", envoi.getHeader("X-Filename"))
        assertEquals(100_000L, envoi.bodySize)
        val url = envoi.requestUrl!!
        assertEquals("lb", url.queryParameter("language"))
        assertEquals("Enabled", url.queryParameter("diarization"))
        assertEquals("json", url.queryParameter("outfmt"))
        assertEquals("2", url.queryParameter("num_speakers"))
        assertEquals("Anne Wagner, Jos Weber, Iechternach.", url.queryParameter("prompt"))

        val etats = mutableListOf<LotsLuxAsr.Etat>()
        val sortie = tmp.root.resolve("lots-1.json")
        lots.attendre(job, sortie, { etats += it })
        assertEquals(listOf(LotsLuxAsr.Etat.File(2, 130.0), LotsLuxAsr.Etat.Traitement(20.0)), etats)
        assertEquals("Moien.", Normaliseur.normaliser(sortie.readText()).segments.single().texte)
    }

    @Test
    fun `lots - travail expire, travail echoue, refus`() {
        serveur.enqueue(MockResponse().setResponseCode(404).setBody("""{"status":"not_found"}"""))
        val lots = LotsLuxAsr(base(), pause = {})
        try { lots.attendre("x", tmp.root.resolve("a.json")); fail() }
        catch (e: LotsLuxAsr.Erreur) { assertEquals(LotsLuxAsr.Code.INTROUVABLE, e.code) }

        serveur.enqueue(MockResponse().setBody("""{"status":"failed","error":"no audio track"}"""))
        try { lots.attendre("y", tmp.root.resolve("b.json")); fail() }
        catch (e: LotsLuxAsr.Erreur) { assertEquals(LotsLuxAsr.Code.ECHEC, e.code) }

        serveur.enqueue(MockResponse().setResponseCode(415).setBody("""{"error":"unsupported media"}"""))
        try { lots.soumettre(tmp.newFile("x.wav"), "audio/wav", LotsLuxAsr.Options()); fail() }
        catch (e: LotsLuxAsr.Erreur) {
            assertEquals(LotsLuxAsr.Code.REFUS, e.code)
            assertTrue(e.message!!, e.message!!.contains("unsupported media"))
        }
    }

    // --- Claude -----------------------------------------------------------------

    private fun sse(vararg evenements: Pair<String, String>) =
        evenements.joinToString("") { (type, data) -> "event: $type\ndata: $data\n\n" }

    @Test
    fun `claude - requete en flux, texte reconstitue`() {
        val corps = sse(
            "message_start" to """{"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":10,"output_tokens":1}}}""",
            "content_block_start" to """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""",
            "content_block_delta" to """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"COMPTE RENDU — "}}""",
            "content_block_delta" to """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Comité"}}""",
            "content_block_stop" to """{"type":"content_block_stop","index":0}""",
            "message_delta" to """{"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":5}}""",
            "message_stop" to """{"type":"message_stop"}"""
        )
        serveur.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(corps))
        val vus = mutableListOf<String>()
        val texte = RedacteurClaude("sk-test", baseUrl = base()).rediger("Système", "Transcription") { vus += it }
        assertEquals("COMPTE RENDU — Comité", texte)
        assertEquals(listOf("COMPTE RENDU — ", "COMPTE RENDU — Comité"), vus)

        val req = serveur.takeRequest()
        assertEquals("/v1/messages", req.path)
        assertTrue((req.getHeader("x-api-key") ?: req.getHeader("Authorization") ?: "").endsWith("sk-test"))
        val o = JSONObject(req.body.readUtf8())
        assertEquals("claude-opus-5-5", o.getString("model"))
        assertEquals(32_000, o.getInt("max_tokens"))
        assertTrue(o.getBoolean("stream"))
        assertEquals("Système", o.getString("system"))
        assertEquals("Transcription", o.getJSONArray("messages").getJSONObject(0).getString("content"))
    }

    @Test
    fun `claude - cle refusee`() {
        serveur.enqueue(MockResponse().setResponseCode(401)
            .setBody("""{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""))
        try { RedacteurClaude("mauvaise", baseUrl = base()).verifierCle(); fail() }
        catch (e: RedacteurClaude.Erreur) { assertEquals(RedacteurClaude.Code.CLE, e.code) }
    }

    // --- Direct sur un vrai WebSocket ---------------------------------------------

    @Test
    fun `direct - config, audio et texte sur un vrai WebSocket`() {
        val config = CountDownLatch(1)
        val octets = AtomicInteger()
        var recu: JSONObject? = null
        serveur.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"type":"connected","session_id":"s1"}""")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val m = JSONObject(text)
                when (m.getString("type")) {
                    "config" -> { recu = m; config.countDown() }
                    "stop" -> {
                        webSocket.send("""{"type":"transcription","accumulated_text":"Moien alleguer.","partial_text":"","send_reason":"silence_commit"}""")
                        webSocket.send("""{"type":"translation","success":true,"accumulated_translation":"Bonjour à tous."}""")
                        webSocket.send("""{"type":"recording_stopped"}""")
                    }
                }
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) { octets.addAndGet(bytes.size) }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
        }))

        val fil = Executors.newSingleThreadScheduledExecutor()
        val planif = object : Planificateur {
            override fun plus(ms: Long, action: () -> Unit): Annulable {
                val f = fil.schedule(action, ms, TimeUnit.MILLISECONDS)
                return Annulable { f.cancel(false) }
            }
            override fun maintenant() = System.nanoTime() / 1_000_000
        }
        val fini = CountDownLatch(1)
        var resultat: List<Paragraphe> = emptyList()
        val url = serveur.url("/prod/ws/transcribe").toString().replace("http", "ws")
        lateinit var direct: DirectLuxAsr
        direct = DirectLuxAsr(object : DirectLuxAsr.Ecouteur {
            override fun surEtat(etat: DirectLuxAsr.Etat) {}
            override fun surTexte(paragraphes: List<Paragraphe>) {}
            override fun surFin(paragraphes: List<Paragraphe>, raison: DirectLuxAsr.Raison?) {
                resultat = paragraphes; fini.countDown()
            }
        }, FabriqueOkHttp(planif, url), planif)
        fil.submit { direct.start() }
        assertTrue(config.await(5, TimeUnit.SECONDS))
        assertEquals("fr", recu!!.getString("translation_target"))
        repeat(10) { fil.submit { direct.audio(ByteArray(5120)) }.get() }
        Thread.sleep(300)
        fil.submit { direct.stop() }
        assertTrue(fini.await(10, TimeUnit.SECONDS))
        assertEquals(51_200, octets.get())
        assertEquals("Moien alleguer.", resultat.single().texte)
        assertEquals("Bonjour à tous.", resultat.single().traduction)
        fil.shutdownNow()
    }
}
