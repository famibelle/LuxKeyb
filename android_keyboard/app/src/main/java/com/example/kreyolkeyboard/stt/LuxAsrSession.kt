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

    // Détection de fin d'énoncé (voir detecterFinDEnonce)
    @Volatile private var heardSpeech = false
    @Volatile private var lastSpeechAt = 0L
    @Volatile private var noiseFloor = 0.0

    // Robinet audio (voir robinet) : ouvert pendant la parole, fermé pendant
    // les pauses. `amorce` retient les derniers blocs non émis pour que la
    // reprise ne coupe pas l'attaque du mot suivant.
    private var robinetOuvert = false
    private var robinetForce = false
    private val amorce = ArrayDeque<ByteArray>()
    private var amorceOctets = 0

    override val isActive: Boolean
        get() = state == SttSession.State.LISTENING || state == SttSession.State.LOADING
    override val isBusy: Boolean
        get() = state != SttSession.State.IDLE

    override fun start() {
        if (isBusy) return
        val gen = ++generation
        accumulated = ""
        heardSpeech = false
        lastSpeechAt = 0L
        noiseFloor = 0.0
        robinetOuvert = false
        robinetForce = false
        amorce.clear()
        amorceOctets = 0
        setState(SttSession.State.LOADING)

        val request = Request.Builder().url(ENDPOINT).build()
        socket = http.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                if (gen != generation) { ws.close(1000, null); return }
                // Réglages envoyés avant la première trame : le serveur applique
                // la configuration à ce qu'il reçoit ensuite, pas à ce qu'il a
                // déjà mis de côté.
                ws.send(JSONObject()
                    .put("type", "config")
                    .put("language", "lb")
                    .put("chunk_params", JSONObject()
                        .put("periodic_send_interval", CHUNK_INTERVAL_S)
                        .put("silence_threshold", CHUNK_SILENCE_S)
                        .put("max_chunk_duration", CHUNK_MAX_S))
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
        val rms = Math.sqrt(sum / chunk.size)
        val level = Math.sqrt((rms / LEVEL_FULL_SCALE).coerceIn(0.0, 1.0)).toFloat()
        main.post { listener.onLevel(level) }

        val now = SystemClock.elapsedRealtime()
        val parle = estParole(rms)
        for (trame in robinet(parle, now, pcm)) ws.send(trame.toByteString())
        detecterFinDEnonce(parle, now)
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
     * Décide ce qui part sur le réseau. Pendant une pause, on cesse d'émettre
     * sans fermer la session : le silence n'est jamais donné au modèle, et le
     * contexte de la dictée est conservé.
     *
     * Ce que ça achète, mesuré sur banc (`stt/bench/probe_gap.py`, 3 fichiers
     * × 5 conditions × 2 passages, 1er septembre 2026) : le découpage du service
     * suit les échantillons reçus, pas l'horloge — sur la même fenêtre, 8 s de
     * silence émis produisent 3 hypothèses, 8 s de flux suspendu n'en produisent
     * aucune. Suspendre ne perd pas le contexte (36,6 % de WER contre 37,0 % en
     * émettant le silence) et **place la frontière sur une vraie pause du
     * locuteur**, ce qui vaut 3,5 points contre une frontière arbitraire
     * (36,6 % contre 40,1 % sans pause). Le banc de parole enchaînée
     * (`bench_continu.py`, 22 énoncés de 8 à 22 s) montre l'autre moitié : sur
     * ce format, couper la session pour obtenir cette frontière ne gagnait rien
     * en exactitude et interrompait 9 % des énoncés en pleine phrase. Le robinet
     * donne la frontière sans l'interruption.
     *
     * Deux détails sans lesquels ça se retourne :
     *
     * - On continue d'émettre [TAP_HANGOVER_MS] après la dernière parole. Il en
     *   faut plus que [CHUNK_SILENCE_S] pour que le service voie lui-même la
     *   pause et close son morceau ; sans ça il garderait le dernier fragment en
     *   attente, et les consonnes finales seraient rognées.
     * - On garde [AMORCE_MS] d'audio non émis sous le coude. L'attaque d'un mot
     *   passe sous le seuil avant de le franchir ; reprendre l'émission au bloc
     *   qui déclenche coûterait la première consonne.
     */
    private fun robinet(parle: Boolean, now: Long, pcm: ByteArray): List<ByteArray> {
        // Filet de sécurité : le seuil de parole n'était jusqu'ici qu'une
        // heuristique d'arrêt, une erreur coûtait un mot ; il commande
        // désormais l'émission, et une erreur coûterait toute la dictée. Si
        // rien n'a franchi le seuil au bout de [FAIL_OPEN_MS] — voix faible
        // dans une pièce bruyante, micro au gain inhabituel — on ouvre en
        // grand pour le reste de la session : un texte imparfait vaut mieux
        // qu'un blanc.
        if (!robinetForce && !robinetOuvert && !heardSpeech && !parle &&
            startedAt != 0L && now - startedAt >= FAIL_OPEN_MS) {
            Log.w(TAG, "🚰 aucune parole détectée en $FAIL_OPEN_MS ms — robinet forcé")
            robinetForce = true
        }
        if (robinetForce) return listOf(pcm)

        if (parle) {
            if (robinetOuvert) return listOf(pcm)
            robinetOuvert = true
            val reprise = amorce.toMutableList()
            reprise.add(pcm)
            amorce.clear()
            amorceOctets = 0
            return reprise
        }

        if (robinetOuvert) {
            if (now - lastSpeechAt < TAP_HANGOVER_MS) return listOf(pcm)
            robinetOuvert = false
            Log.i(TAG, "🚰 robinet fermé, la session reste ouverte")
            return emptyList()
        }

        amorce.addLast(pcm)
        amorceOctets += pcm.size
        while (amorceOctets > AMORCE_MS * OCTETS_PAR_MS) {
            amorceOctets -= amorce.removeFirst().size
        }
        return emptyList()
    }

    /**
     * Termine l'énoncé quand la parole s'arrête, plutôt que d'attendre que
     * l'utilisateur pense à appuyer sur stop.
     *
     * Ce seuil ne protège plus de l'hallucination — c'est le robinet qui s'en
     * charge, en n'envoyant pas le silence. Il ne répond plus qu'à une question
     * d'usage : à partir de quand considère-t-on que la personne a fini. D'où un
     * délai bien plus long qu'avant, où il fallait couper vite sous peine de
     * laisser le service broder sur le blanc.
     *
     * Pour mémoire, ce que coûtait l'ancien réglage, mesuré sur téléphone le
     * 29 août 2026 sur le même extrait rejoué au haut-parleur : couper deux
     * secondes après la fin de la parole tronque le dernier segment (18,5 % de
     * WER, « gestëmmt » perdu) ; laisser tourner dix secondes de silence fait
     * **inventer** le service, qui re-segmente le blanc — « A wat dat bedo - déi
     * Motioun gestëmmt. -6 -0, Marie -Cole. » sur un extrait qui n'en contient
     * rien. 14,8 % de WER sur le texte utile, 40,7 % en comptant cette queue.
     * Il n'y avait pas de bon réglage entre les deux ; il y avait un robinet.
     *
     * Filtrer la queue après coup n'est toujours pas possible proprement : le
     * service renvoie `accumulated_text`, l'énoncé entier réécrit à chaque
     * passe, et non un segment ajouté. Refuser un suffixe supposerait de
     * diffuser le texte par fragments et de les recoller — exactement ce que ce
     * client évite.
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
                // `accumulated_text` porte tout l'énoncé depuis le début ; c'est
                // exactement la sémantique du texte en composition d'un IME, qui
                // remplace en bloc plutôt que de recoller des fragments.
                accumulated = json.optString("accumulated_text", accumulated)
                    .ifEmpty { accumulated }
                val ms = SystemClock.elapsedRealtime() - startedAt
                val proc = json.optJSONObject("metrics")?.optDouble("processing_time", 0.0) ?: 0.0
                // Le texte livré est filtré, celui qu'on retient ne l'est pas :
                // le service réécrit `accumulated_text` en entier à chaque
                // passe, et une passe ultérieure peut très bien lever
                // l'ambiguïté d'une boucle naissante. Filtrer au stockage
                // ferait diverger notre état du sien.
                val propre = RepetitionTrimmer.trim(accumulated)
                main.post {
                    listener.onPassTiming((ms / 1000f), (proc * 1000).toLong(), true)
                    if (propre.isNotEmpty()) listener.onPartial(propre)
                }
            }

            "recording_stopped" -> finish()
            "error" -> Log.e(TAG, "erreur du service: ${json.optString("message")}")
        }
    }

    private fun finish() {
        if (state == SttSession.State.IDLE) return
        val text = RepetitionTrimmer.trim(accumulated)
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
     * [LuxAsrApiSession], qui est aujourd'hui le chemin en ligne retenu, s'en
     * sert aussi : ce sont les mêmes seuils, calibrés sur les mêmes mesures, et
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
         * Cadence à laquelle le service transcrit ce qu'il a reçu.
         *
         * Le grain de la dictée n'est pas chez nous : `accumulated_text` arrive
         * quand le serveur décide de décoder, et par défaut il ne décode que
         * toutes les 5 s d'audio ou sur une pause de 0,8 s. Sur des énoncés de
         * longueur clavier — 6 s — cela ne fait qu'une seule passe, donc aucun
         * mot ne s'affiche avant la fin : mesuré le 29 août, du texte
         * apparaissait avant la fin de la parole dans 11 énoncés sur 20
         * seulement.
         *
         * Ces paramètres sont réglables, ce que leur propre client web n'utilise
         * pas : il n'envoie que `language`, `use_context` et les options de
         * traduction. Le serveur accepte pourtant `chunk_params` dans un message
         * `config` et le réémet en accusé — mais **uniquement sous cette forme
         * imbriquée** ; les mêmes clés à plat sont ignorées en silence. C'est
         * donc hors du protocole documenté, sur une API déjà non authentifiée :
         * si un jour le serveur cesse de les lire, on retombe simplement sur son
         * défaut, sans rien casser.
         *
         * Ce que ça coûte, mesuré le 30 août sur dix tranches, même audio et
         * mêmes références, WER par alignement d'infixe :
         *
         *     défaut 5,0 / 0,8   WER 30,3 %   1er texte 7,25 s   14 passes
         *     2,0 / 0,5          WER 37,3 %   1er texte 4,47 s   22 passes
         *     1,2 / 0,3          WER 38,6 %   1er texte 4,46 s   28 passes
         *
         * Environ 2,8 s gagnées sur le premier mot contre 7 points de WER —
         * whisper décode une fenêtre et un morceau de 2 s lui laisse moins de
         * contexte, que le recouvrement et les 80 tokens de contexte du service
         * ne rattrapent qu'en partie. Descendre sous 2 s n'achète plus de
         * latence, le plancher étant le temps de décodage, mais coûte encore en
         * exactitude : d'où 2,0 et pas moins.
         *
         * Vérifié le même jour sur le téléphone, deux passages dos à dos sur les
         * mêmes 19 tranches, seul l'APK changeant : le texte arrive avant la fin
         * de la parole dans 13 énoncés sur 19 au lieu de 6, le premier texte
         * tombe à 4,32 s au lieu de 6,53 s, et le nombre de mises à jour passe
         * de 34 à 49 — sur un énoncé de 15 s, 7 au lieu de 3. Le prix y est plus
         * doux qu'en laboratoire : l'écart apparié a une **médiane nulle**, la
         * moyenne perdant 4,9 points parce que quelques tranches se dégradent
         * franchement quand la coupure tombe au milieu d'un mot. C'est la forme
         * de l'arbitrage : la dictée devient vivante, et de temps en temps plus
         * fausse.
         */
        const val CHUNK_INTERVAL_S = 2.0

        /** Pause qui déclenche une transcription anticipée. */
        const val CHUNK_SILENCE_S = 0.5

        /** Plafond d'un morceau, laissé au défaut du service. */
        const val CHUNK_MAX_S = 30.0

        /**
         * Silence qui termine la dictée. Cinq secondes, là où il fallait couper
         * à 1,5 s avant le robinet : le silence n'étant plus émis, l'attendre ne
         * coûte plus rien au texte. Le banc de parole enchaînée du
         * 1er septembre 2026 montrait qu'à 1,5 s, 9 % des énoncés de une à trois
         * phrases étaient coupés en pleine phrase — sans le moindre gain
         * d'exactitude en échange.
         */
        const val SILENCE_HANGOVER_MS = 5_000L

        /**
         * Émission maintenue après la dernière parole, avant de fermer le
         * robinet. Doit dépasser [CHUNK_SILENCE_S] pour que le service voie
         * lui-même la pause et close son morceau plutôt que de retenir le
         * dernier fragment.
         */
        const val TAP_HANGOVER_MS = 700L

        /**
         * Audio retenu pendant que le robinet est fermé, réémis à la reprise :
         * l'attaque d'un mot passe sous le seuil avant de le franchir.
         */
        const val AMORCE_MS = 400L

        /**
         * Délai au bout duquel un robinet qui n'a jamais vu de parole s'ouvre
         * en grand. Plus long que le temps de réaction d'un utilisateur qui
         * vient d'appuyer sur le micro, sinon le filet se déclencherait à
         * chaque hésitation.
         */
        const val FAIL_OPEN_MS = 4_000L

        /** PCM 16 bits à 16 kHz : deux octets par échantillon. */
        const val OCTETS_PAR_MS = 32

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
