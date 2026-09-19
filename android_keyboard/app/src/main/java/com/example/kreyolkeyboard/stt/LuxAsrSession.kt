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
 * Protocole : PCM 16 bits little-endian, 16 kHz mono, en trames binaires ;
 * messages de contrôle en JSON. Depuis le 16 septembre 2026 le service tourne
 * le moteur « à tampon croissant » publié dans `PeterGilles/LuxASRlive`
 * (réponses `engine: "whisperlive_buffer"`) : il re-décode tout l'énoncé non
 * engagé toutes les 0,5 à 1 s, n'**engage** un mot qu'après l'avoir vu à la
 * même place dans trois hypothèses de suite, et renvoie la queue encore
 * instable à part. `accumulated_text` ne fait donc plus que grandir, et
 * `partial_text` est ce qui peut encore bouger.
 *
 * Ce que ce moteur a changé, mesuré le 19 septembre 2026 sur les 22 énoncés de
 * 8 à 22 s du banc du 1er septembre (même audio, même WER infixe) :
 *
 *     WebSocket   38,3 % → 25,4 %   texte final 0,23 s après l'arrêt (1,87 s)
 *     API /asr2   26,8 % → 26,9 %   texte final 1,30 s après l'arrêt
 *
 * Le flux a rattrapé l'API par lots, et il affiche le premier aperçu 1,1 s
 * après le début de la parole, là où l'API ne montre rien avant la fin.
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
    /** Texte engagé par le service : il ne fait que grandir. */
    @Volatile private var engage = ""
    /** Queue encore instable, remplacée à chaque passe. */
    @Volatile private var queue = ""
    @Volatile private var startedAt = 0L
    private var generation = 0

    // Détection de fin d'énoncé (voir detecterFinDEnonce)
    @Volatile private var heardSpeech = false
    @Volatile private var lastSpeechAt = 0L
    @Volatile private var noiseFloor = 0.0

    override val isActive: Boolean
        get() = state == SttSession.State.LISTENING || state == SttSession.State.LOADING
    override val isBusy: Boolean
        get() = state != SttSession.State.IDLE

    override fun start() {
        if (isBusy) return
        val gen = ++generation
        engage = ""
        queue = ""
        heardSpeech = false
        lastSpeechAt = 0L
        noiseFloor = 0.0
        setState(SttSession.State.LOADING)

        val request = Request.Builder().url(ENDPOINT).build()
        socket = http.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                if (gen != generation) { ws.close(1000, null); return }
                // Réglages envoyés avant la première trame : le serveur applique
                // la configuration à ce qu'il reçoit ensuite, pas à ce qu'il a
                // déjà mis de côté. La langue est le seul réglage qui nous
                // concerne ; l'ancien `chunk_params` n'est plus lu depuis que
                // le service ne découpe plus en morceaux.
                ws.send(JSONObject()
                    .put("type", "config")
                    .put("language", "lb")
                    .toString())
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
        engage = ""
        queue = ""
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
        val rms = Math.sqrt(sum / chunk.size)
        val level = Math.sqrt((rms / LEVEL_FULL_SCALE).coerceIn(0.0, 1.0)).toFloat()
        main.post { listener.onLevel(level) }

        // Tout part, silences compris. Le service a désormais sa propre
        // détection de parole (Silero) et s'appuie sur les pauses qu'il reçoit
        // pour engager le texte : les lui cacher, comme le faisait l'ancien
        // robinet, retarderait l'engagement sans rien protéger. Vérifié le
        // 19 septembre sur 8 énoncés suivis de 5 s de bruit de fond, soit
        // exactement ce que voit le service avant l'arrêt automatique : aucun
        // mot inventé, WER égal ou meilleur, texte final 0,02 s après l'arrêt.
        ws.send(pcm.toByteString())
        detecterFinDEnonce(estParole(rms), SystemClock.elapsedRealtime())
    }

    /**
     * Vrai quand ce bloc porte de la parole, selon un plancher de bruit
     * adaptatif : il redescend d'un coup sur le silence et ne remonte que
     * lentement, de sorte qu'une pièce bruyante relève le seuil sans qu'une
     * voyelle tenue le fasse. Un seuil fixe fonctionnerait sur l'appareil où il
     * a été réglé et nulle part ailleurs : le gain du micro varie d'un téléphone
     * à l'autre, et VOICE_RECOGNITION applique en plus son propre traitement.
     */
    private fun estParole(rms: Double): Boolean {
        noiseFloor = if (rms < noiseFloor) rms
                     else noiseFloor + (rms - noiseFloor) * NOISE_RISE
        return rms >= maxOf(SPEECH_FLOOR_RMS, noiseFloor * SPEECH_MARGIN)
    }

    /**
     * Termine l'énoncé quand la parole s'arrête, plutôt que d'attendre que
     * l'utilisateur pense à appuyer sur stop.
     *
     * Ce seuil ne répond qu'à une question d'usage : à partir de quand
     * considère-t-on que la personne a fini. Il ne protège pas de
     * l'hallucination ; le service s'en charge lui-même depuis son moteur du
     * 16 septembre 2026, qui ne décode pas le silence.
     *
     * Pour mémoire, l'ancien moteur, lui, brodait sur le blanc : mesuré sur
     * téléphone le 29 août 2026, dix secondes de silence lui faisaient écrire
     * « A wat dat bedo - déi Motioun gestëmmt. -6 -0, Marie -Cole. » sur un
     * extrait qui n'en contient rien. D'où un robinet qui cessait d'émettre
     * pendant les pauses, retiré le 19 septembre avec la mesure qui le rendait
     * inutile (voir [onAudioChunk]).
     */
    private fun detecterFinDEnonce(parle: Boolean, now: Long) {
        // Garde-fou de durée : si le seuil ne se déclenche jamais — pièce
        // bruyante, micro qui souffle — on ne diffuse pas indéfiniment l'audio
        // d'un utilisateur vers un service tiers.
        if (startedAt != 0L && now - startedAt >= MAX_UTTERANCE_MS) {
            Log.i(TAG, "⏱️ énoncé plafonné à ${MAX_UTTERANCE_MS / 1000} s")
            main.post { if (state == SttSession.State.LISTENING) stop() }
            return
        }

        if (parle) {
            heardSpeech = true
            lastSpeechAt = now
            return
        }

        // Rien n'a encore été dit : on laisse à l'utilisateur le temps de
        // commencer, sans quoi le micro se refermerait aussitôt ouvert.
        if (!heardSpeech || now - lastSpeechAt < SILENCE_HANGOVER_MS) return

        Log.i(TAG, "🔇 fin d'énoncé après ${SILENCE_HANGOVER_MS} ms de silence")
        main.post { if (state == SttSession.State.LISTENING) stop() }
    }

    private fun handleMessage(text: String) {
        val json = try { JSONObject(text) } catch (e: Throwable) {
            Log.w(TAG, "message illisible: ${text.take(120)}"); return
        }
        when (json.optString("type")) {
            "connected" -> Log.i(TAG, "✅ session ${json.optString("session_id")} " +
                "· service v${json.optString("version")}")

            "transcription" -> {
                // Deux champs, deux natures : `accumulated_text` est engagé et
                // ne fait que grandir, `partial_text` est la queue que la passe
                // suivante peut encore réécrire. Le texte de composition de
                // l'IME les montre ensemble, puisqu'il est remplacé en bloc.
                engage = json.optString("accumulated_text", engage).ifEmpty { engage }
                queue = json.optString("partial_text", "")
                val ms = SystemClock.elapsedRealtime() - startedAt
                val proc = json.optJSONObject("metrics")?.optDouble("processing_time", 0.0) ?: 0.0
                // Le texte livré est filtré, celui qu'on retient ne l'est pas :
                // la queue d'une passe ultérieure peut très bien lever
                // l'ambiguïté d'une boucle naissante. Filtrer au stockage
                // ferait diverger notre état du sien.
                val propre = RepetitionTrimmer.trim(texteVisible())
                main.post {
                    listener.onPassTiming((ms / 1000f), (proc * 1000).toLong(), true)
                    if (propre.isNotEmpty()) listener.onPartial(propre)
                }
            }

            "recording_stopped" -> finish()
            "error" -> Log.e(TAG, "erreur du service: ${json.optString("message")}")
        }
    }

    /** Engagé puis instable, ce que l'utilisateur doit voir à cet instant. */
    private fun texteVisible(): String =
        if (queue.isBlank()) engage else "$engage ${queue.trim()}".trim()

    private fun finish() {
        if (state == SttSession.State.IDLE) return
        // Après `stop`, le service engage toute la queue avant de répondre
        // `recording_stopped`, qui n'en laisse donc plus. Si c'est le délai de
        // grâce qui conclut, on garde la queue : c'est ce qui était affiché.
        val text = RepetitionTrimmer.trim(texteVisible())
        setState(SttSession.State.IDLE)
        main.post { listener.onFinal(text) }
    }

    private fun setState(next: SttSession.State) {
        if (state == next) return
        state = next
        Log.d(TAG, "état → $next")
        main.post { listener.onStateChanged(next) }
    }

    /**
     * Réglages de la détection de parole. Publics parce que
     * [LuxAsrApiSession], l'autre chemin en ligne, s'en sert aussi : ce sont les mêmes seuils, calibrés sur les mêmes mesures, et
     * les dupliquer garantirait qu'ils divergent au premier réglage.
     */
    companion object {
        const val TAG = "LuxAsrSession"

        /**
         * Point d'accès temps réel de LuxASR, relevé dans leur client web.
         * Non documenté publiquement et non authentifié : à traiter comme une
         * démonstration susceptible de changer, pas comme une API stable.
         */
        const val ENDPOINT = "wss://luxasr.uni.lu/prod/ws/transcribe"

        /** Attente maximale du dernier segment après « stop ». */
        const val FINAL_GRACE_MS = 4_000L

        /**
         * Silence qui termine la dictée. Cinq secondes : le service ne décodant
         * pas le silence, l'attendre ne coûte rien au texte (mesuré le
         * 19 septembre 2026 avec exactement ces 5 s). Le banc de parole enchaînée du
         * 1er septembre 2026 montrait qu'à 1,5 s, 9 % des énoncés de une à trois
         * phrases étaient coupés en pleine phrase — sans le moindre gain
         * d'exactitude en échange.
         */
        const val SILENCE_HANGOVER_MS = 5_000L

        /**
         * Plancher absolu sous lequel aucune énergie n'est prise pour de la
         * parole, quel que soit le plancher de bruit observé — sans lui, une
         * pièce parfaitement silencieuse ferait descendre le seuil adaptatif
         * jusqu'au bruit de quantification.
         */
        const val SPEECH_FLOOR_RMS = 0.012

        /** Marge au-dessus du plancher de bruit pour déclarer « parole ». */
        const val SPEECH_MARGIN = 2.5

        /** Vitesse de remontée du plancher de bruit, par bloc de ~160 ms. */
        const val NOISE_RISE = 0.02

        /** Durée maximale d'un énoncé, garde-fou si le silence n'arrive jamais. */
        const val MAX_UTTERANCE_MS = 90_000L

        /** Même échelle que SttSession, pour que le micro respire pareil. */
        const val LEVEL_FULL_SCALE = 0.18
    }
}
