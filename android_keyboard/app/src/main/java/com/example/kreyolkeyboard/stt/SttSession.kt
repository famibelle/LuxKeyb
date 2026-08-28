package com.example.kreyolkeyboard.stt

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Orchestre une dictée : capture, découpage, transcriptions successives.
 *
 * Whisper n'est pas un modèle streaming — il consomme une fenêtre mel de 30 s,
 * point. Le « temps réel » est donc obtenu ici, en re-transcrivant à intervalle
 * régulier la totalité de l'énoncé accumulé depuis l'appui sur le micro. Chaque
 * passe rend une hypothèse complète qui remplace la précédente, ce qui colle
 * exactement à la sémantique du texte en composition d'un IME
 * (setComposingText), et évite d'avoir à recoller des fragments entre eux.
 *
 * Conséquence assumée : le coût d'une passe croît avec la durée de l'énoncé.
 * C'est ce que borne [MAX_UTTERANCE_SAMPLES], au-delà duquel la dictée se
 * termine d'elle-même plutôt que de se dégrader.
 */
class SttSession(
    private val context: Context,
    private val listener: Listener
) {

    interface Listener {
        /** Hypothèse intermédiaire, destinée au texte en composition. */
        fun onPartial(text: String)
        /** Transcription définitive ; le texte en composition doit être validé. */
        fun onFinal(text: String)
        fun onStateChanged(state: State)
        fun onError(error: Error)
    }

    enum class State { IDLE, LOADING, LISTENING, FINALIZING }

    enum class Error {
        /** ABI non couverte, asset manquant, ou mémoire insuffisante. */
        MODEL_UNAVAILABLE,
        /** Permission refusée, micro déjà pris, format refusé. */
        MIC_UNAVAILABLE
    }

    private val engine = SttEngine()
    private val recorder = AudioRecorder()

    // Un seul thread : il sérialise chargement, passes partielles et passe
    // finale. Deux passes whisper concurrentes sur le même contexte
    // corrompraient son état interne, et deux contextes coûteraient 165 Mo de
    // tampons de plus.
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "lux-stt-worker").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())

    /** Tampon de l'énoncé, préalloué : 30 s à 16 kHz, soit ~1,9 Mo. */
    private val utterance = FloatArray(MAX_UTTERANCE_SAMPLES)
    private var written = 0
    private val bufferLock = Any()

    private val transcribing = AtomicBoolean(false)
    @Volatile private var state = State.IDLE
    @Volatile private var lastPartialAt = 0

    /**
     * Numéro de la dictée en cours, incrémenté à chaque [start].
     *
     * Les passes whisper sont soumises à un worker et peuvent rendre leur
     * résultat longtemps après avoir été demandées — jusqu'à plusieurs secondes
     * pour une passe finale sur 30 s d'audio. Sans ce compteur, le résultat
     * d'une dictée abandonnée ou terminée venait écraser l'état de la suivante
     * et injecter son texte dans le champ. Toute tâche compare la génération
     * qu'elle a capturée au départ et se tait si elle a changé.
     */
    @Volatile private var generation = 0

    /** Compteurs du détecteur d'activité vocale, en échantillons. */
    private var speechSeen = false
    private var silenceRun = 0

    /** Le micro est ouvert (ou sur le point de l'être) : un appui doit arrêter. */
    val isActive: Boolean
        get() = state == State.LISTENING || state == State.LOADING

    /**
     * La session n'est pas revenue au repos, finalisation comprise. C'est
     * [isBusy] et non [isActive] qui garde [start] : la finalisation ne tient
     * plus le micro mais tient encore le worker, et en démarrer une seconde
     * par-dessus faisait diverger les deux.
     */
    val isBusy: Boolean
        get() = state != State.IDLE

    fun start() {
        if (isBusy) return

        synchronized(bufferLock) { written = 0 }
        lastPartialAt = 0
        speechSeen = false
        silenceRun = 0
        val gen = ++generation

        setState(State.LOADING)

        submit("démarrage") {
            if (gen != generation) return@submit

            if (!engine.load(context)) {
                setState(State.IDLE)
                main.post { listener.onError(Error.MODEL_UNAVAILABLE) }
                return@submit
            }

            // Le micro n'est ouvert qu'une fois le modèle prêt : l'inverse
            // capturait pendant la seconde de chargement une amorce que
            // l'utilisateur croyait entendue alors qu'il ne parlait pas encore.
            if (!recorder.start(::onAudioChunk)) {
                setState(State.IDLE)
                main.post { listener.onError(Error.MIC_UNAVAILABLE) }
                return@submit
            }

            setState(State.LISTENING)
        }
    }

    /** Termine la dictée et demande la transcription définitive. */
    fun stop() {
        if (state != State.LISTENING && state != State.LOADING) return

        setState(State.FINALIZING)
        recorder.stop()

        // Coupe court à la passe partielle éventuellement en vol : sans cela,
        // la passe finale attendrait derrière elle dans la file du worker.
        engine.abort()

        val gen = generation
        submit("finalisation") {
            val samples = snapshot()
            val text = if (samples.size >= MIN_FINAL_SAMPLES) {
                engine.transcribe(samples, singleSegment = false)
            } else {
                // Moins de 300 ms captés : un appui accidentel sur le micro, que
                // whisper transformerait volontiers en une hallucination courte.
                ""
            }
            // Une annulation ou une nouvelle dictée a pu survenir pendant la
            // passe : ni l'état ni le texte de celle-ci ne la concernent.
            if (gen != generation) return@submit
            setState(State.IDLE)
            main.post { listener.onFinal(text) }
        }
    }

    /** Abandonne la dictée sans rien restituer (retour arrière, changement de champ). */
    fun cancel() {
        if (state == State.IDLE) return
        // Incrémenter d'abord : toute passe déjà soumise devient caduque et ne
        // publiera ni son texte ni son état.
        generation++
        recorder.stop()
        engine.abort()
        synchronized(bufferLock) { written = 0 }
        setState(State.IDLE)
    }

    /**
     * Libère les ~165 Mo de tampons de whisper. À appeler dès que le clavier
     * quitte l'écran : les garder pendant la frappe normale fait du processus
     * IME une cible de choix pour le tueur de mémoire du système.
     */
    fun releaseModel() {
        worker.execute { engine.release() }
    }

    fun shutdown() {
        cancel()
        worker.execute { engine.release() }
        worker.shutdown()
    }

    // --- Capture ------------------------------------------------------------

    private fun onAudioChunk(chunk: FloatArray) {
        val total = synchronized(bufferLock) {
            val room = MAX_UTTERANCE_SAMPLES - written
            if (room <= 0) return@synchronized written
            val n = minOf(room, chunk.size)
            System.arraycopy(chunk, 0, utterance, written, n)
            written += n
            written
        }

        updateVoiceActivity(chunk)

        if (total >= MAX_UTTERANCE_SAMPLES) {
            // Fenêtre pleine : on rend ce qui a été dit plutôt que de tronquer
            // silencieusement la suite, que whisper ne verrait jamais.
            main.post { stop() }
            return
        }

        if (speechSeen && silenceRun >= AUTO_STOP_SILENCE_SAMPLES) {
            main.post { stop() }
            return
        }

        if (total - lastPartialAt >= PARTIAL_STEP_SAMPLES) {
            lastPartialAt = total
            schedulePartial()
        }
    }

    /**
     * Détecteur d'activité vocale à seuil d'énergie. Volontairement fruste : il
     * ne sert qu'à terminer la dictée après un silence franc, pas à segmenter.
     * Un VAD trop fin couperait au milieu des pauses qu'un locuteur fait
     * naturellement en cherchant ses mots.
     */
    private fun updateVoiceActivity(chunk: FloatArray) {
        var sum = 0.0
        for (s in chunk) sum += s.toDouble() * s
        val rms = Math.sqrt(sum / chunk.size)

        if (rms >= SPEECH_RMS_THRESHOLD) {
            speechSeen = true
            silenceRun = 0
        } else if (speechSeen) {
            silenceRun += chunk.size
        }
    }

    private fun schedulePartial() {
        // Si une passe tourne encore, on saute ce tour : empiler les passes
        // ferait diverger l'affichage du micro, chaque hypothèse arrivant de
        // plus en plus en retard sur ce qui est dit.
        if (!transcribing.compareAndSet(false, true)) return

        val gen = generation
        submit("hypothèse") {
            try {
                if (gen != generation || state != State.LISTENING) return@submit
                val samples = snapshot()
                if (samples.size < MIN_PARTIAL_SAMPLES) return@submit

                val text = engine.transcribe(samples, singleSegment = true)
                if (text.isNotEmpty() && gen == generation && state == State.LISTENING) {
                    main.post { listener.onPartial(text) }
                }
            } finally {
                transcribing.set(false)
            }
        }
    }

    private fun snapshot(): FloatArray = synchronized(bufferLock) {
        utterance.copyOf(written)
    }

    /**
     * Soumet une tâche au worker en journalisant ce qui s'en échapperait.
     *
     * Une exception non rattrapée dans un ExecutorService tue son thread sans
     * rien écrire : la dictée resterait alors bloquée dans son état courant,
     * micro éteint et bouton figé, sans la moindre trace pour le comprendre.
     */
    private fun submit(what: String, task: () -> Unit) {
        worker.execute {
            try {
                task()
            } catch (e: Throwable) {
                Log.e(TAG, "❌ tâche « $what » interrompue: ${e.message}", e)
                setState(State.IDLE)
            }
        }
    }

    private fun setState(next: State) {
        if (state == next) return
        state = next
        Log.d(TAG, "état → $next")
        main.post { listener.onStateChanged(next) }
    }

    private companion object {
        const val TAG = "SttSession"

        private const val RATE = SttEngine.SAMPLE_RATE

        /** Fenêtre mel de whisper : au-delà, l'audio serait ignoré. */
        const val MAX_UTTERANCE_SAMPLES = 30 * RATE

        /**
         * Intervalle entre deux hypothèses. 900 ms est le compromis mesuré :
         * plus court, les passes se chevauchent dès que l'énoncé s'allonge sur
         * un appareil lent ; plus long, le texte affiché décroche visiblement
         * de la parole.
         */
        const val PARTIAL_STEP_SAMPLES = (0.9 * RATE).toInt()

        /** En deçà, whisper hallucine plus qu'il ne transcrit. */
        const val MIN_PARTIAL_SAMPLES = (0.6 * RATE).toInt()
        const val MIN_FINAL_SAMPLES = (0.3 * RATE).toInt()

        /** Silence après lequel la dictée se termine seule. */
        const val AUTO_STOP_SILENCE_SAMPLES = (1.6 * RATE).toInt()

        /**
         * Seuil d'énergie du VAD. Bas à dessein : il vaut mieux laisser tourner
         * une dictée sur du bruit de fond que la couper sur une voix douce.
         */
        const val SPEECH_RMS_THRESHOLD = 0.015
    }
}
