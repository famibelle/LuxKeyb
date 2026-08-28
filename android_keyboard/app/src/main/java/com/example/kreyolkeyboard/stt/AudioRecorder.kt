package com.example.kreyolkeyboard.stt

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlin.concurrent.thread

/**
 * Capture micro en 16 kHz mono, convertie en flottants [-1, 1] — le seul format
 * que whisper accepte.
 *
 * L'appelant est responsable d'avoir obtenu RECORD_AUDIO : un IME ne peut pas
 * demander une permission depuis sa vue de saisie (voir MicPermissionActivity).
 */
class AudioRecorder {

    private var record: AudioRecord? = null
    @Volatile private var running = false
    private var readerThread: Thread? = null

    /**
     * Démarre la capture. [onChunk] est appelé depuis le thread de lecture, à
     * chaque bloc lu ; il doit rendre la main vite, sans quoi le tampon interne
     * d'AudioRecord déborde et l'audio se met à sauter.
     *
     * @return false si le micro n'a pas pu être ouvert (permission refusée,
     *   micro déjà pris par une autre application, format non supporté).
     */
    @SuppressLint("MissingPermission")
    fun start(onChunk: (FloatArray) -> Unit): Boolean {
        if (running) return true

        val minBuffer = AudioRecord.getMinBufferSize(
            SttEngine.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            Log.e(TAG, "❌ 16 kHz mono non supporté (getMinBufferSize=$minBuffer)")
            return false
        }

        // Quatre fois le minimum : la passe de transcription monopolise le CPU
        // par à-coups, et un tampon au ras du minimum perdait des blocs
        // exactement pendant ces pointes.
        val bufferSize = minBuffer * 4

        val rec = try {
            AudioRecord(
                // VOICE_RECOGNITION plutôt que MIC : la chaîne de traitement
                // système y est réglée pour la reconnaissance et n'applique pas
                // l'AGC ni la réduction de bruit agressive du mode téléphonie,
                // qui abîment les fricatives dont whisper a besoin.
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SttEngine.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (e: Throwable) {
            Log.e(TAG, "❌ Création d'AudioRecord impossible: ${e.message}", e)
            return false
        }

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "❌ AudioRecord non initialisé (state=${rec.state})")
            rec.release()
            return false
        }

        record = rec
        running = true

        try {
            rec.startRecording()
        } catch (e: Throwable) {
            Log.e(TAG, "❌ startRecording a échoué: ${e.message}", e)
            running = false
            rec.release()
            record = null
            return false
        }

        // Une capture qui démarre mais reste en STATE_STOPPED ne lève rien et
        // ne rend que du silence : c'est ce que produit un refus de permission
        // côté système sur certaines surcouches.
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            Log.e(TAG, "❌ Micro non actif après startRecording")
            stop()
            return false
        }

        readerThread = thread(name = "lux-stt-audio", isDaemon = true) {
            val shorts = ShortArray(minBuffer / 2)
            while (running) {
                val read = rec.read(shorts, 0, shorts.size)
                if (read <= 0) {
                    if (read < 0) Log.w(TAG, "lecture micro en erreur ($read)")
                    continue
                }
                val floats = FloatArray(read)
                for (i in 0 until read) {
                    floats[i] = shorts[i] / PCM16_FULL_SCALE
                }
                onChunk(floats)
            }
        }

        Log.i(TAG, "🎙️ Capture démarrée (tampon $bufferSize octets)")
        return true
    }

    fun stop() {
        if (!running && record == null) return
        running = false

        readerThread?.join(THREAD_JOIN_MS)
        readerThread = null

        record?.let { rec ->
            try {
                if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) rec.stop()
            } catch (e: Throwable) {
                Log.w(TAG, "stop() du micro en erreur: ${e.message}")
            }
            rec.release()
        }
        record = null
        Log.i(TAG, "🎙️ Capture arrêtée")
    }

    private companion object {
        const val TAG = "AudioRecorder"

        /**
         * 32768 et non 32767 : diviser par la valeur maximale positive ferait
         * dépasser -1 au minimum négatif (-32768), hors de l'intervalle attendu.
         */
        const val PCM16_FULL_SCALE = 32768.0f

        const val THREAD_JOIN_MS = 500L
    }
}
