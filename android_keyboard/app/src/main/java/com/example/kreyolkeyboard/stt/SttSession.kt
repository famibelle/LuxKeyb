package com.example.kreyolkeyboard.stt

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
) : DictationSession {

    interface Listener {
        /** Hypothèse intermédiaire, destinée au texte en composition. */
        fun onPartial(text: String)
        /**
         * Énergie du dernier bloc capté, dans [0, 1] après compression.
         * Publié ~25 fois par seconde pendant l'écoute : c'est le seul retour
         * immédiat possible, whisper ne rendant sa première hypothèse qu'après
         * une bonne seconde.
         */
        fun onLevel(level: Float)
        /** Transcription définitive ; le texte en composition doit être validé. */
        fun onFinal(text: String)
        /**
         * Durée mesurée d'une passe whisper : [audioSeconds] d'audio soumis
         * transcrits en [ms] millisecondes, [partial] distinguant une
         * hypothèse d'une passe finale.
         *
         * Existe pour une raison précise : la latence de la dictée n'a jamais
         * été mesurée sur un appareil réel, seulement sur un hôte x86 où elle
         * ne veut rien dire. Un banc d'essai déporté ne la donnerait pas non
         * plus tout à fait — il tourne dans un processus isolé, là où l'IME
         * subit en plus le rendu du clavier et le throttling thermique. La
         * seule mesure juste est celle prise ici, dans le processus qui rend
         * le service.
         */
        fun onPassTiming(audioSeconds: Float, ms: Long, partial: Boolean)
        fun onStateChanged(state: State)
        fun onError(error: Error)
    }

    enum class State { IDLE, LOADING, LISTENING, FINALIZING }

    enum class Error {
        /** ABI non couverte, asset manquant, ou mémoire insuffisante. */
        MODEL_UNAVAILABLE,
        /** Permission refusée, micro déjà pris, format refusé. */
        MIC_UNAVAILABLE,
        /** Dictée en ligne seulement : réseau absent ou service muet. */
        SERVICE_UNREACHABLE
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

    /**
     * L'appareil tient-il le rythme des hypothèses intermédiaires ?
     *
     * Mesuré sur un Samsung ancien : une passe y coûte ~6,2 s quelle que soit
     * la longueur de l'énoncé, parce que whisper encode toujours une fenêtre de
     * 30 s. Aucune hypothèse n'apparaît donc avant la fin de la phrase, et
     * chacune monopolise le CPU que la passe finale attend — celle-là même que
     * l'utilisateur regarde. On les abandonne dès qu'une passe dure plus
     * longtemps que la parole qu'elle transcrit : à ce régime elles ne
     * rattraperont jamais leur retard, il ne fera que croître.
     *
     * Remis à vrai à chaque dictée : un appareil peut être lent parce qu'il
     * était occupé, pas parce qu'il est incapable.
     */
    @Volatile private var partialsViable = true

    /** Compteurs du détecteur d'activité vocale, en échantillons. */
    private var speechSeen = false
    private var silenceRun = 0

    /** Le micro est ouvert (ou sur le point de l'être) : un appui doit arrêter. */
    override val isActive: Boolean
        get() = state == State.LISTENING || state == State.LOADING

    /**
     * La session n'est pas revenue au repos, finalisation comprise. C'est
     * [isBusy] et non [isActive] qui garde [start] : la finalisation ne tient
     * plus le micro mais tient encore le worker, et en démarrer une seconde
     * par-dessus faisait diverger les deux.
     */
    override val isBusy: Boolean
        get() = state != State.IDLE

    override fun start() {
        if (isBusy) return

        synchronized(bufferLock) { written = 0 }
        lastPartialAt = 0
        speechSeen = false
        silenceRun = 0
        partialsViable = true
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
    override fun stop() {
        if (state != State.LISTENING && state != State.LOADING) return

        setState(State.FINALIZING)
        recorder.stop()

        // Coupe court à la passe partielle éventuellement en vol : sans cela,
        // la passe finale attendrait derrière elle dans la file du worker.
        engine.abort()

        val gen = generation
        submit("finalisation") {
            val samples = snapshot()
            val startedAt = SystemClock.elapsedRealtime()
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
            reportTiming(samples.size, startedAt, partial = false)
            setState(State.IDLE)
            main.post { listener.onFinal(text) }
        }
    }

    /** Abandonne la dictée sans rien restituer (retour arrière, changement de champ). */
    override fun cancel() {
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
    override fun releaseModel() {
        worker.execute { engine.release() }
    }

    override fun shutdown() {
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
            schedulePartial(total)
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

        if (state == State.LISTENING) {
            // Racine plutôt que valeur brute : l'échelle linéaire d'un RMS de
            // parole tient dans les 5 % du bas de l'intervalle, où aucun
            // mouvement n'est visible à l'œil.
            val level = Math.sqrt(
                (rms / LEVEL_FULL_SCALE).coerceIn(0.0, 1.0)
            ).toFloat()
            main.post { listener.onLevel(level) }
        }

        if (rms >= SPEECH_RMS_THRESHOLD) {
            speechSeen = true
            silenceRun = 0
        } else if (speechSeen) {
            silenceRun += chunk.size
        }
    }

    private fun schedulePartial(at: Int) {
        // Si une passe tourne encore, on saute ce tour : empiler les passes
        // ferait diverger l'affichage du micro, chaque hypothèse arrivant de
        // plus en plus en retard sur ce qui est dit.
        //
        // Le pas n'est consommé qu'ici, une fois la passe réellement lancée.
        // Le décompter à la tentative faisait attendre un pas entier de plus
        // après chaque tour sauté : sur un appareil où une passe dure plus
        // longtemps que le pas — le cas courant — les hypothèses se
        // raréfiaient au lieu de s'enchaîner aussi vite que possible.
        // Sous le minimum on ne consomme pas le pas : le bloc suivant, 60 ms
        // plus tard, retentera au lieu d'attendre un pas entier pour rien.
        if (at < MIN_PARTIAL_SAMPLES) return
        if (!partialsViable) return
        if (!transcribing.compareAndSet(false, true)) return
        lastPartialAt = at

        val gen = generation
        submit("hypothèse") {
            try {
                if (gen != generation || state != State.LISTENING) return@submit
                val samples = snapshot()
                if (samples.size < MIN_PARTIAL_SAMPLES) return@submit

                val startedAt = SystemClock.elapsedRealtime()
                val text = engine.transcribe(samples, singleSegment = true)
                val took = SystemClock.elapsedRealtime() - startedAt
                if (took > SLOW_PASS_MS) {
                    Log.i(TAG, "hypothèses abandonnées : une passe a pris $took ms")
                    partialsViable = false
                }
                reportTiming(samples.size, startedAt, partial = true)
                if (text.isNotEmpty() && gen == generation && state == State.LISTENING) {
                    main.post { listener.onPartial(text) }
                }
            } finally {
                transcribing.set(false)
            }
        }
    }

    /** Publie la durée de la passe qui vient de se terminer. */
    private fun reportTiming(samples: Int, startedAt: Long, partial: Boolean) {
        val ms = SystemClock.elapsedRealtime() - startedAt
        val secs = samples.toFloat() / RATE
        main.post { listener.onPassTiming(secs, ms, partial) }
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
         * Audio minimal accumulé entre deux hypothèses. Volontairement court :
         * ce n'est plus lui qui cadence l'affichage depuis que le pas n'est
         * consommé qu'au lancement effectif d'une passe — c'est la durée d'une
         * passe qui le fait, et elle dépend de l'appareil. Le pas ne sert donc
         * plus qu'à éviter de relancer whisper sur 60 ms d'audio de plus.
         */
        const val PARTIAL_STEP_SAMPLES = (0.6 * RATE).toInt()

        /** En deçà, whisper hallucine plus qu'il ne transcrit. */
        const val MIN_PARTIAL_SAMPLES = (0.6 * RATE).toInt()

        /**
         * RMS considéré comme « à fond » pour l'indicateur de niveau. Une voix
         * proche du micro tourne autour de 0,1 ; au-delà on sature l'affichage
         * plutôt que de comprimer tout le reste vers le bas.
         */
        const val LEVEL_FULL_SCALE = 0.18
        const val MIN_FINAL_SAMPLES = (0.3 * RATE).toInt()

        /**
         * Au-delà, les hypothèses intermédiaires sont abandonnées pour l'énoncé
         * en cours.
         *
         * Le seuil est absolu et non relatif à l'audio transcrit : la première
         * passe porte toujours sur 0,6 s et coûte pourtant ~800 ms même sur une
         * machine rapide, où les hypothèses s'enchaînent ensuite très bien. Ce
         * qui les rend inutiles n'est pas qu'une passe dure plus que son audio,
         * c'est qu'elle dure plus qu'un énoncé entier — 2,5 s, quand la médiane
         * mesurée sur corpus est de 4,2 s. À ce régime l'utilisateur voit au
         * mieux une hypothèse, arrivée après qu'il a fini de parler, et payée
         * par un retard équivalent sur la passe finale qu'il attend vraiment.
         */
        const val SLOW_PASS_MS = 2_500L

        /** Silence après lequel la dictée se termine seule. */
        const val AUTO_STOP_SILENCE_SAMPLES = (1.6 * RATE).toInt()

        /**
         * Seuil d'énergie du VAD. Bas à dessein : il vaut mieux laisser tourner
         * une dictée sur du bruit de fond que la couper sur une voix douce.
         */
        const val SPEECH_RMS_THRESHOLD = 0.015
    }
}
