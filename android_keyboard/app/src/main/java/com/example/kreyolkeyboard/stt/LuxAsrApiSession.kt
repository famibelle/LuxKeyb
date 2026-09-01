package com.example.kreyolkeyboard.stt

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Dictée déléguée à l'API par lots de LuxASR — le flux `/asr2` en file
 * d'attente, celui que l'Université documente et soutient.
 *
 * On enregistre l'énoncé entier, on l'envoie d'un bloc à l'arrêt, on interroge
 * le travail jusqu'à son terme, on insère le texte. Pas de flux, pas
 * d'hypothèses intermédiaires.
 *
 * **Pourquoi ce chemin plutôt que le WebSocket de [LuxAsrSession].** Les deux
 * points de terminaison servent le même modèle : sur 62 énoncés mesurés le
 * 1er septembre 2026, 43 transcriptions sont identiques au caractère près et
 * l'écart médian de WER est nul (25,2 % contre 26,4 % en pondéré, à l'avantage
 * de l'API). Le délai final est le même — 1,7 s après l'arrêt en médiane contre
 * 1,8 s — avec un plafond bien plus serré, 1,8 s contre 4,4 s. À qualité et à
 * délai égaux, l'API supprime tout ce que le flux imposait de construire :
 * détection de fin d'énoncé côté serveur, `chunk_params`, robinet audio, texte
 * de composition réécrit à chaque passe. Et elle décode l'énoncé **d'un seul
 * tenant**, ce qui répond à l'objection qui a lancé ce travail : les pauses ne
 * sont pas un indicateur fiable de fin de phrase, donc mieux vaut ne pas
 * segmenter du tout.
 *
 * Ce qu'on perd : l'aperçu pendant qu'on parle. Le champ reste vide jusqu'au
 * bout, d'où l'indicateur animé posé par l'IME en texte de composition.
 *
 * Ce qui reste du flux : rien ici, mais [LuxAsrSession] est conservée sur la
 * branche comme voie de repli. Les 1,7 s ont été mesurées sur un service au
 * repos avec un seul client ; si leur file s'allonge en conditions réelles, le
 * flux a l'avantage de dégrader progressivement au lieu de faire attendre.
 */
class LuxAsrApiSession(
    private val listener: SttSession.Listener
) : DictationSession {

    private val recorder = AudioRecorder()
    private val main = Handler(Looper.getMainLooper())
    private val reseau = Executors.newSingleThreadExecutor()

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Audio de l'énoncé, accumulé bloc par bloc depuis le thread du micro. */
    private val blocs = ArrayList<FloatArray>()

    @Volatile private var state = SttSession.State.IDLE
    @Volatile private var startedAt = 0L
    private var generation = 0

    // Détection de parole, reprise telle quelle de LuxAsrSession : elle sert
    // ici à deux choses seulement — arrêter la dictée quand la personne a fini,
    // et savoir où commence et où finit la parole pour ne pas envoyer le
    // silence qui l'entoure.
    @Volatile private var heardSpeech = false
    @Volatile private var lastSpeechAt = 0L
    @Volatile private var noiseFloor = 0.0
    @Volatile private var premierSon = -1
    @Volatile private var dernierSon = -1
    @Volatile private var echantillons = 0

    override val isActive: Boolean
        get() = state == SttSession.State.LISTENING
    override val isBusy: Boolean
        get() = state != SttSession.State.IDLE

    override fun start() {
        if (isBusy) return
        ++generation
        synchronized(blocs) { blocs.clear() }
        heardSpeech = false
        lastSpeechAt = 0L
        noiseFloor = 0.0
        premierSon = -1
        dernierSon = -1
        echantillons = 0

        if (!recorder.start(::onAudioChunk)) {
            setState(SttSession.State.IDLE)
            main.post { listener.onError(SttSession.Error.MIC_UNAVAILABLE) }
            return
        }
        startedAt = SystemClock.elapsedRealtime()
        setState(SttSession.State.LISTENING)
    }

    override fun stop() {
        if (state != SttSession.State.LISTENING) return
        recorder.stop()
        setState(SttSession.State.FINALIZING)
        val gen = generation
        val audio = decouperSurLaParole()
        if (audio.isEmpty()) {
            // Rien d'audible : on rend la main sans déranger le service.
            main.post { listener.onFinal("") }
            setState(SttSession.State.IDLE)
            return
        }
        reseau.execute { transcrire(gen, audio) }
    }

    override fun cancel() {
        if (state == SttSession.State.IDLE) return
        generation++
        recorder.stop()
        synchronized(blocs) { blocs.clear() }
        setState(SttSession.State.IDLE)
    }

    /** Rien à libérer : le modèle est chez eux, pas chez nous. */
    override fun releaseModel() = Unit

    override fun shutdown() {
        cancel()
        reseau.shutdown()
        http.dispatcher.executorService.shutdown()
    }

    // --- Capture --------------------------------------------------------

    private fun onAudioChunk(chunk: FloatArray) {
        if (state != SttSession.State.LISTENING) return

        var sum = 0.0
        for (s in chunk) sum += s.toDouble() * s
        val rms = Math.sqrt(sum / chunk.size)

        val debut = echantillons
        synchronized(blocs) { blocs.add(chunk) }
        echantillons += chunk.size

        val level = Math.sqrt((rms / LuxAsrSession.LEVEL_FULL_SCALE)
            .coerceIn(0.0, 1.0)).toFloat()
        main.post { listener.onLevel(level) }

        val now = SystemClock.elapsedRealtime()
        if (estParole(rms)) {
            if (premierSon < 0) premierSon = debut
            dernierSon = debut + chunk.size
            heardSpeech = true
            lastSpeechAt = now
            return
        }
        detecterFinDEnonce(now)
    }

    /** Plancher de bruit adaptatif — voir [LuxAsrSession.estParole]. */
    private fun estParole(rms: Double): Boolean {
        noiseFloor = if (rms < noiseFloor) rms
                     else noiseFloor + (rms - noiseFloor) * LuxAsrSession.NOISE_RISE
        return rms >= maxOf(LuxAsrSession.SPEECH_FLOOR_RMS,
                            noiseFloor * LuxAsrSession.SPEECH_MARGIN)
    }

    private fun detecterFinDEnonce(now: Long) {
        if (startedAt != 0L && now - startedAt >= LuxAsrSession.MAX_UTTERANCE_MS) {
            Log.i(TAG, "⏱️ énoncé plafonné à ${LuxAsrSession.MAX_UTTERANCE_MS / 1000} s")
            main.post { if (state == SttSession.State.LISTENING) stop() }
            return
        }
        if (!heardSpeech || now - lastSpeechAt < LuxAsrSession.SILENCE_HANGOVER_MS) return
        Log.i(TAG, "🔇 fin d'énoncé")
        main.post { if (state == SttSession.State.LISTENING) stop() }
    }

    /**
     * Ne garde que la parole, avec une marge de part et d'autre.
     *
     * C'est ce qui reste du robinet audio du flux, et pour la même raison :
     * whisper hallucine sur le silence — dix secondes de blanc lui faisaient
     * inventer une phrase entière, et un énoncé plafonné à 90 s rendait 270
     * mots pour 192 attendus. En lot, il n'y a pas de robinet à ouvrir et à
     * fermer, il suffit de ne pas mettre le silence dans le fichier.
     *
     * Les pauses **internes** sont conservées : les retirer recollerait des
     * mots que le locuteur a séparés, et le modèle décode de toute façon
     * l'énoncé entier d'un coup. Seuls les bords sont coupés.
     */
    private fun decouperSurLaParole(): FloatArray {
        val tout: FloatArray
        synchronized(blocs) {
            if (blocs.isEmpty()) return FloatArray(0)
            tout = FloatArray(echantillons)
            var i = 0
            for (b in blocs) { b.copyInto(tout, i); i += b.size }
        }
        if (premierSon < 0 || dernierSon <= premierSon) return FloatArray(0)
        val marge = (MARGE_MS * SttEngine.SAMPLE_RATE / 1000).toInt()
        val d = maxOf(0, premierSon - marge)
        val f = minOf(tout.size, dernierSon + marge)
        return tout.copyOfRange(d, f)
    }

    // --- Protocole ------------------------------------------------------

    private fun transcrire(gen: Int, audio: FloatArray) {
        val t0 = SystemClock.elapsedRealtime()
        try {
            val corps = wav(audio).toRequestBody(WAV)
            val url = "$BASE/asr2?language=lb&diarization=Disabled&outfmt=text"
            val post = http.newCall(Request.Builder().url(url)
                .header("X-Filename", "dictee.wav")
                .post(corps).build()).execute()
            val job = post.use {
                if (!it.isSuccessful) throw RuntimeException("asr2 ${it.code}")
                JSONObject(it.body?.string().orEmpty()).getString("job_id")
            }

            while (true) {
                if (gen != generation) return
                if (SystemClock.elapsedRealtime() - t0 > TIMEOUT_MS)
                    throw RuntimeException("job $job toujours en cours")
                Thread.sleep(SONDAGE_MS)
                val etat = http.newCall(Request.Builder()
                    .url("$BASE/v3/asr/jobs/$job").build()).execute().use {
                        JSONObject(it.body?.string().orEmpty()).optString("status")
                    }
                if (etat == "completed") break
                if (etat == "failed") throw RuntimeException("job $job en échec")
            }

            val texte = http.newCall(Request.Builder()
                .url("$BASE/v3/asr/jobs/$job/result").build()).execute().use {
                    if (!it.isSuccessful) throw RuntimeException("result ${it.code}")
                    it.body?.string().orEmpty().trim()
                }

            val ms = SystemClock.elapsedRealtime() - t0
            Log.i(TAG, "✅ ${audio.size / SttEngine.SAMPLE_RATE} s → $ms ms")
            if (gen != generation) return
            main.post {
                listener.onPassTiming(
                    audio.size.toFloat() / SttEngine.SAMPLE_RATE, ms, false)
                listener.onFinal(texte)
                setState(SttSession.State.IDLE)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "❌ ${e.message}", e)
            if (gen != generation) return
            main.post {
                listener.onFinal("")
                listener.onError(SttSession.Error.SERVICE_UNREACHABLE)
                setState(SttSession.State.IDLE)
            }
        }
    }

    /**
     * float32 [-1, 1] → conteneur WAV PCM 16 bits.
     *
     * L'API veut les octets **bruts** du fichier dans le corps de la requête,
     * pas un envoi `multipart/form-data` : elle vérifie que la charge utile est
     * un média décodable, donc il lui faut un vrai conteneur et pas du PCM nu.
     */
    private fun wav(samples: FloatArray): ByteArray {
        val rate = SttEngine.SAMPLE_RATE
        val out = ByteArrayOutputStream(44 + samples.size * 2)
        fun i32(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
            out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF)
        }
        fun i16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        val octets = samples.size * 2
        out.write("RIFF".toByteArray()); i32(36 + octets)
        out.write("WAVEfmt ".toByteArray()); i32(16); i16(1); i16(1)
        i32(rate); i32(rate * 2); i16(2); i16(16)
        out.write("data".toByteArray()); i32(octets)
        for (s in samples) i16((s.coerceIn(-1f, 1f) * 32767f).toInt())
        return out.toByteArray()
    }

    private fun setState(next: SttSession.State) {
        if (state == next) return
        state = next
        main.post { listener.onStateChanged(next) }
    }

    companion object {
        private const val TAG = "LuxAsrApiSession"
        const val BASE = "https://luxasr.uni.lu"
        private val WAV = "audio/wav".toMediaType()

        /** Période d'interrogation du travail. */
        private const val SONDAGE_MS = 200L

        /**
         * Au-delà, on abandonne. Large : la file est partagée, et le pire
         * mesuré sur 62 énoncés est 1,8 s — mais c'était un service au repos.
         */
        private const val TIMEOUT_MS = 30_000L

        /** Marge gardée de part et d'autre de la parole avant l'envoi. */
        private const val MARGE_MS = 300L
    }
}
