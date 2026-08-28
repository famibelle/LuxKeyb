package com.example.kreyolkeyboard.stt

import android.content.Context
import android.content.res.AssetManager
import android.util.Log

/**
 * Enveloppe Kotlin autour de whisper.cpp (voir src/main/cpp/whisper_jni.cpp).
 *
 * Le modèle pèse ~31 Mo sur disque mais whisper alloue en plus ~165 Mo de
 * tampons de calcul, dimensionnés au pire cas dès l'initialisation et
 * indépendamment des paramètres de décodage. C'est beaucoup pour un processus
 * IME, que le système tue volontiers en arrière-plan : le contexte n'est donc
 * jamais chargé à l'ouverture du clavier, seulement à l'appui sur le micro, et
 * [release] le rend dès la fin de la dictée.
 */
class SttEngine {

    // @Volatile : isLoaded le lit hors verrou.
    @Volatile private var contextPtr: Long = 0L

    /**
     * Sérialise tout ce qui touche au contexte natif.
     *
     * `whisper_context` porte son état de décodage : deux `whisper_full()`
     * concurrents dessus, ou un `whisper_free()` pendant une transcription,
     * corrompent la mémoire du processus. La conception garantit déjà un seul
     * appelant — un exécuteur mono-thread dans SttSession — mais c'est un
     * invariant réparti sur deux classes, qu'un futur appelant casserait sans
     * rien voir. Le verrou le rend local et vérifiable.
     *
     * [abort] est délibérément hors verrou : son rôle est d'interrompre la
     * transcription qui le détient.
     */
    private val nativeLock = Any()

    val isLoaded: Boolean
        get() = contextPtr != 0L

    /**
     * Charge le modèle depuis les assets. Appel bloquant (~0,1 à 1 s selon
     * l'appareil) : à faire sur un thread de travail, jamais sur l'UI.
     *
     * @return true si le contexte est utilisable.
     */
    fun load(context: Context): Boolean {
        if (isLoaded) return true
        if (!libraryAvailable) return false

        return synchronized(nativeLock) {
            if (contextPtr != 0L) return@synchronized true
            try {
                contextPtr = nativeInitFromAsset(context.assets, MODEL_ASSET)
                if (contextPtr == 0L) {
                    Log.e(TAG, "❌ Chargement du modèle de dictée échoué")
                    false
                } else {
                    Log.i(TAG, "✅ Modèle de dictée chargé")
                    true
                }
            } catch (e: Throwable) {
                Log.e(TAG, "❌ Exception au chargement du modèle: ${e.message}", e)
                contextPtr = 0L
                false
            }
        }
    }

    /**
     * Transcrit un tampon PCM mono 16 kHz normalisé dans [-1, 1].
     *
     * @param singleSegment force une sortie en un seul segment, pour les
     *   hypothèses intermédiaires : sans lui, la fenêtre glissante re-découpe la
     *   phrase à chaque passe et le texte en composition clignote.
     * @return le texte transcrit, ou "" si le contexte est absent, la passe a
     *   échoué, ou la transcription a été interrompue par [abort].
     */
    fun transcribe(samples: FloatArray, singleSegment: Boolean): String {
        if (samples.isEmpty()) return ""
        return synchronized(nativeLock) {
            val ptr = contextPtr
            if (ptr == 0L) return@synchronized ""
            try {
                nativeTranscribe(ptr, samples, threadCount(), singleSegment).trim()
            } catch (e: Throwable) {
                Log.e(TAG, "❌ Exception pendant la transcription: ${e.message}", e)
                ""
            }
        }
    }

    /**
     * Demande l'interruption de la passe en cours. whisper_full() rend la main
     * à la prochaine couche décodée, ce qui borne l'attente à quelques dizaines
     * de millisecondes au lieu de la fin du décodage.
     */
    fun abort() {
        if (libraryAvailable) nativeAbort()
    }

    fun release() {
        // Le verrou fait attendre la transcription en cours plutôt que de lui
        // retirer son contexte sous les pieds. abort() a déjà été demandé par
        // l'appelant, donc l'attente se compte en dizaines de millisecondes.
        synchronized(nativeLock) {
            val ptr = contextPtr
            contextPtr = 0L
            if (ptr != 0L) nativeFree(ptr)
        }
    }

    /**
     * Whisper sature au-delà de la moitié des cœurs sur un mobile : les cœurs
     * restants servent à l'IME lui-même, dont la boucle d'entrée doit rester
     * fluide pendant que la dictée tourne.
     */
    private fun threadCount(): Int =
        Runtime.getRuntime().availableProcessors().let { cores ->
            (cores / 2).coerceIn(2, 4)
        }

    private external fun nativeInitFromAsset(assets: AssetManager, assetPath: String): Long
    private external fun nativeTranscribe(
        ctx: Long, audio: FloatArray, nThreads: Int, singleSegment: Boolean
    ): String
    private external fun nativeAbort()
    private external fun nativeFree(ctx: Long)

    companion object {
        private const val TAG = "SttEngine"

        const val MODEL_ASSET = "ggml-lb-tiny-q5_1.bin"

        /** Whisper attend impérativement du 16 kHz mono. */
        const val SAMPLE_RATE = 16_000

        /**
         * Le chargement de la bibliothèque est tenté une fois et son échec est
         * retenu : sur une ABI non couverte, réessayer à chaque appui sur le
         * micro ne ferait que rejouer la même UnsatisfiedLinkError.
         */
        val libraryAvailable: Boolean by lazy {
            try {
                System.loadLibrary("luxstt")
                true
            } catch (e: Throwable) {
                Log.e(TAG, "❌ libluxstt indisponible sur cette ABI: ${e.message}")
                false
            }
        }
    }
}
