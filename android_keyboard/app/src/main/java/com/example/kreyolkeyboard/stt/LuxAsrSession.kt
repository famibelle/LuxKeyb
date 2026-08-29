package com.example.kreyolkeyboard.stt

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Dictée par le service en ligne LuxASR de l'Université du Luxembourg.
 *
 * Existe parce que la dictée embarquée ne tient pas ses promesses. Mesuré le
 * 28 août 2026 sur 50 min de luxembourgeois : `whisper-tiny`, le modèle
 * embarqué, rend 72 % de WER sur des énoncés de longueur clavier ; `whisper-base`
 * 36 % ; les wav2vec 2.0 luxembourgeois 44 % et 35 %. Il en faudrait moins de
 * 15 % pour qu'une dictée fasse gagner du temps plutôt qu'en coûter. Et sur un
 * Samsung ancien, une passe prend 6,2 s quelle que soit la durée de l'énoncé,
 * parce que whisper encode toujours une fenêtre de 30 s.
 *
 * Le même énoncé passé au service LuxASR revient correct, ponctué et
 * capitalisé, en ~270 ms de traitement — il y tourne `large-v3-turbo`, que sa
 * licence ne permet pas d'embarquer.
 *
 * **Ce que ça coûte, et qui doit rester explicite.** L'audio quitte l'appareil.
 * C'est l'exact contraire de ce que promet la politique de confidentialité
 * publiée, et la raison pour laquelle l'API distante avait été écartée au
 * départ. Cette classe n'existe donc que sur une branche de démonstration, le
 * bandeau de dictée affiche sans ambiguïté que la transcription est en ligne, et
 * rien de tout ceci ne doit atteindre une version publiée avant, dans l'ordre :
 * l'accord de LuxASR — leur site demande explicitement qu'on les contacte avant
 * toute intégration — et une politique de confidentialité réécrite.
 *
 * Protocole, relevé dans leur client `scriptrt.js` v2.1.0 et vérifié contre le
 * service v2.3.0 : PCM 16 bits little-endian, 16 kHz mono, en trames binaires ;
 * messages de contrôle en JSON ; le serveur découpe lui-même sur les silences et
 * gère le contexte inter-segments.
 */
class LuxAsrSession(
    private val listener: SttSession.Listener
) : DictationSession {

    private val recorder = AudioRecorder()
    private val main = Handler(Looper.getMainLooper())

    private val http = OkHttpClient.Builder()
        // Le service tient la connexion ouverte entre deux énoncés ; sans ping
        // les intermédiaires réseau la coupent en silence, et l'échec ne se
        // découvre qu'au premier appui sur le micro.
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var state = SttSession.State.IDLE
    @Volatile private var accumulated = ""
    @Volatile private var startedAt = 0L
    private var generation = 0

    override val isActive: Boolean
        get() = state == SttSession.State.LISTENING || state == SttSession.State.LOADING
    override val isBusy: Boolean
        get() = state != SttSession.State.IDLE

    override fun start() {
        if (isBusy) return
        val gen = ++generation
        accumulated = ""
        setState(SttSession.State.LOADING)

        val request = Request.Builder().url(ENDPOINT).build()
        socket = http.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                if (gen != generation) { ws.close(1000, null); return }
                // Le micro n'est ouvert qu'une fois la connexion établie :
                // l'inverse capturerait une amorce que le serveur ne verrait
                // jamais, et que l'utilisateur croirait pourtant dictée.
                if (!recorder.start(::onAudioChunk)) {
                    setState(SttSession.State.IDLE)
                    main.post { listener.onError(SttSession.Error.MIC_UNAVAILABLE) }
                    ws.close(1000, null)
                    return
                }
                startedAt = SystemClock.elapsedRealtime()
                setState(SttSession.State.LISTENING)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                if (gen != generation) return
                handleMessage(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "❌ WebSocket: ${t.message}", t)
                if (gen != generation) return
                recorder.stop()
                setState(SttSession.State.IDLE)
                main.post { listener.onError(SttSession.Error.SERVICE_UNREACHABLE) }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (gen != generation) return
                finish()
            }
        })
    }

    /** Termine la dictée : le serveur vide ce qu'il retenait, puis on conclut. */
    override fun stop() {
        if (!isBusy) return
        recorder.stop()
        setState(SttSession.State.FINALIZING)
        val ws = socket
        if (ws == null) { finish(); return }
        ws.send(JSONObject().put("type", "stop").toString())
        // Le serveur répond par un dernier segment puis `recording_stopped`. Si
        // rien n'arrive, on rend quand même ce qui a été accumulé : mieux vaut un
        // texte partiel qu'un bandeau figé.
        main.postDelayed({ if (state == SttSession.State.FINALIZING) finish() },
                         FINAL_GRACE_MS)
    }

    override fun cancel() {
        if (state == SttSession.State.IDLE) return
        generation++
        recorder.stop()
        socket?.close(1000, null)
        socket = null
        accumulated = ""
        setState(SttSession.State.IDLE)
    }

    /** Rien à libérer : le modèle est chez eux, pas chez nous. */
    override fun releaseModel() = Unit

    override fun shutdown() {
        cancel()
        http.dispatcher.executorService.shutdown()
    }

    // --- Protocole ----------------------------------------------------------

    private fun onAudioChunk(chunk: FloatArray) {
        val ws = socket ?: return
        if (state != SttSession.State.LISTENING) return

        var sum = 0.0
        val pcm = ByteArray(chunk.size * 2)
        for (i in chunk.indices) {
            val s = chunk[i]
            sum += s.toDouble() * s
            // PCM 16 bits little-endian, le seul format que le service accepte.
            val v = (s.coerceIn(-1f, 1f) * 32767f).toInt()
            pcm[i * 2] = (v and 0xFF).toByte()
            pcm[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        ws.send(pcm.toByteString())

        val rms = Math.sqrt(sum / chunk.size)
        val level = Math.sqrt((rms / LEVEL_FULL_SCALE).coerceIn(0.0, 1.0)).toFloat()
        main.post { listener.onLevel(level) }
    }

    private fun handleMessage(text: String) {
        val json = try { JSONObject(text) } catch (e: Throwable) {
            Log.w(TAG, "message illisible: ${text.take(120)}"); return
        }
        when (json.optString("type")) {
            "connected" -> Log.i(TAG, "✅ session ${json.optString("session_id")} " +
                "· service v${json.optString("version")}")

            "transcription" -> {
                // `accumulated_text` porte tout l'énoncé depuis le début ; c'est
                // exactement la sémantique du texte en composition d'un IME, qui
                // remplace en bloc plutôt que de recoller des fragments.
                accumulated = json.optString("accumulated_text", accumulated)
                    .ifEmpty { accumulated }
                val ms = SystemClock.elapsedRealtime() - startedAt
                val proc = json.optJSONObject("metrics")?.optDouble("processing_time", 0.0) ?: 0.0
                main.post {
                    listener.onPassTiming((ms / 1000f), (proc * 1000).toLong(), true)
                    if (accumulated.isNotEmpty()) listener.onPartial(accumulated)
                }
            }

            "recording_stopped" -> finish()
            "error" -> Log.e(TAG, "erreur du service: ${json.optString("message")}")
        }
    }

    private fun finish() {
        if (state == SttSession.State.IDLE) return
        val text = accumulated
        setState(SttSession.State.IDLE)
        main.post { listener.onFinal(text) }
    }

    private fun setState(next: SttSession.State) {
        if (state == next) return
        state = next
        Log.d(TAG, "état → $next")
        main.post { listener.onStateChanged(next) }
    }

    private companion object {
        const val TAG = "LuxAsrSession"

        /**
         * Point d'accès temps réel de LuxASR, relevé dans leur client web.
         * Non documenté publiquement et non authentifié : à traiter comme une
         * démonstration susceptible de changer, pas comme une API stable.
         */
        const val ENDPOINT = "wss://luxasr.uni.lu/prod/ws/transcribe"

        /** Attente maximale du dernier segment après « stop ». */
        const val FINAL_GRACE_MS = 4_000L

        /** Même échelle que SttSession, pour que le micro respire pareil. */
        const val LEVEL_FULL_SCALE = 0.18
    }
}
