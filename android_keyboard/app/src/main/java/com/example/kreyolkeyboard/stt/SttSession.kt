package com.example.kreyolkeyboard.stt

/**
 * Les types qu'une dictée échange avec le clavier : l'écouteur, les états, les
 * erreurs.
 *
 * Ce fichier portait jusqu'au 2 octobre 2026 la dictée **embarquée** :
 * `whisper-tiny` exécuté dans le téléphone par whisper.cpp, en JNI. Elle a été
 * retirée : 72 % de mots erronés mesurés sur 161 énoncés, contre 25 % pour le
 * service LuxASR, et 6 s par passe sur un téléphone ancien. Les modèles plus
 * gros ont été mesurés aussi sur un Galaxy A21s, sans mieux faire que le
 * service (9 à 32 s d'attente par phrase). Seule reste la dictée en ligne,
 * [LuxAsrSession] ; le code embarqué se retrouve dans l'historique git, et le
 * banc `stt/bench` sait toujours le mesurer.
 *
 * Le nom est resté pour ne pas renommer chaque `SttSession.Listener` du
 * clavier.
 */
object SttSession {

    /** Fréquence d'échantillonnage attendue par le service : 16 kHz mono. */
    const val SAMPLE_RATE = 16_000

    interface Listener {
        /** Hypothèse intermédiaire, destinée au texte en composition. */
        fun onPartial(text: String)
        /**
         * Énergie du dernier bloc capté, dans [0, 1] après compression.
         * Publié ~25 fois par seconde pendant l'écoute : c'est le seul retour
         * immédiat possible, le service ne rendant sa première hypothèse
         * qu'après une bonne seconde.
         */
        fun onLevel(level: Float)
        /** Transcription définitive ; le texte en composition doit être validé. */
        fun onFinal(text: String)
        /**
         * Chronométrage d'une réponse du service : [audioSeconds] écoulées
         * depuis l'ouverture du micro, [ms] de traitement annoncées par le
         * service, [partial] distinguant une hypothèse du texte final.
         * Diagnostic du canal Labs, affiché si `SHOW_PASS_TIMING` est vrai.
         */
        fun onPassTiming(audioSeconds: Float, ms: Long, partial: Boolean)
        fun onStateChanged(state: State)
        fun onError(error: Error)
        /**
         * L'audio s'empile faute de réseau ([slow] vrai), ou repart
         * normalement.
         */
        fun onNetworkSlow(slow: Boolean) {}
    }

    enum class State { IDLE, LOADING, LISTENING, FINALIZING }

    enum class Error {
        /** Permission refusée, micro déjà pris, format refusé. */
        MIC_UNAVAILABLE,
        /** Le réseau est là, mais le service ne répond pas. */
        SERVICE_UNREACHABLE,
        /** Aucun réseau, rien n'a été tenté. */
        NO_NETWORK,
        /** La connexion est tombée en cours de dictée. */
        CONNECTION_LOST,
        /** Le réseau ne suivait plus, la dictée a été arrêtée. */
        NETWORK_TOO_SLOW
    }
}
