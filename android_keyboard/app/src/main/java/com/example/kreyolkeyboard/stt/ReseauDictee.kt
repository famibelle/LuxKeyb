package com.example.kreyolkeyboard.stt

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Ce que la dictée en ligne sait du réseau, sans jamais le mesurer.
 *
 * Un test de débit consommerait des données, ferait attendre avant chaque
 * dictée, et ne dirait rien de la minute suivante. Deux questions suffisent,
 * et aucune ne demande d'autorisation au-delà d'`ACCESS_NETWORK_STATE`,
 * accordée à l'installation sans rien demander à l'utilisateur :
 *
 * - **y a-t-il un réseau ?** C'est ce que répond cette classe, pour griser le
 *   micro et refuser tout de suite au lieu de laisser l'utilisateur fixer
 *   « LuxASR verbannen… » jusqu'à l'échec de la connexion ;
 * - **le réseau suit-il ?** C'est [RetardEnvoi], qui juge sur l'audio réellement
 *   en souffrance pendant la dictée.
 *
 * Un réseau présent mais non validé par Android (portail captif d'hôtel,
 * réseau d'entreprise qui bloque le test de Google) compte comme présent :
 * le service de l'Université peut très bien y répondre, et l'échec éventuel
 * a son propre message. Griser le micro sur ce seul indice l'interdirait à
 * tort à qui n'a jamais de réseau « validé ».
 */
class ReseauDictee(context: Context, private val onChange: (Boolean) -> Unit) {

    private val cm = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val main = Handler(Looper.getMainLooper())
    private var callback: ConnectivityManager.NetworkCallback? = null

    /** Vrai s'il existe un réseau censé mener à Internet. */
    fun disponible(): Boolean {
        val cm = cm ?: return true   // dans le doute, on tente
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val reseau = cm.activeNetwork ?: return false
                cm.getNetworkCapabilities(reseau)
                    ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            } else {
                @Suppress("DEPRECATION")
                cm.activeNetworkInfo?.isConnected == true
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "état du réseau illisible", e)
            true
        }
    }

    /**
     * Suit les apparitions et disparitions de réseau tant que le clavier est
     * affiché, pour que le micro change d'aspect sous les yeux de l'utilisateur
     * quand il sort d'un tunnel. Sans effet avant Android 7, où le micro n'est
     * réévalué qu'à l'affichage du clavier et à l'appui.
     */
    fun ecouter() {
        val cm = cm ?: return
        if (callback != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        // Le rappel suit le réseau *par défaut* : quand le wifi tombe et que la
        // 4G prend le relais, il reçoit onAvailable pour elle, et onLost ne
        // veut donc dire « plus rien » que lorsqu'aucun relais n'existe. On s'y
        // fie tel quel. Relire activeNetwork dans onLost ne marche pas :
        // mesuré sur l'émulateur Android 16, il désigne encore le réseau
        // perdu à cet instant, et le micro restait affiché comme disponible.
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = signaler(true)
            override fun onLost(network: Network) = signaler(false)
        }
        try {
            cm.registerDefaultNetworkCallback(cb)
            callback = cb
        } catch (e: RuntimeException) {
            // SecurityException, ou TooManyRequestsException (100 rappels par
            // application) : on se contente de l'état relu à l'affichage.
            Log.w(TAG, "suivi du réseau impossible", e)
        }
    }

    fun arreter() {
        val cb = callback ?: return
        callback = null
        try { cm?.unregisterNetworkCallback(cb) } catch (_: IllegalArgumentException) {}
    }

    private fun signaler(present: Boolean) {
        main.post { onChange(present) }
    }

    companion object {
        private const val TAG = "ReseauDictee"
    }
}

/**
 * Juge si le réseau suit la dictée, sur la seule mesure qui ne ment pas :
 * l'audio capté qui n'est pas encore parti.
 *
 * La dictée envoie du PCM 16 bits à 16 kHz, soit 32 000 octets par seconde,
 * environ 256 kbit/s montants. Un réseau qui n'y suffit pas ne fait pas
 * échouer la connexion : l'audio s'empile dans la file d'envoi d'OkHttp
 * (`WebSocket.queueSize()`), et le texte arrive de plus en plus en retard
 * sans que rien ne le dise. C'est cette file qu'on regarde.
 *
 * Elle ne voit pas ce que le système a déjà accepté dans son tampon d'envoi
 * TCP, qui peut atteindre plusieurs centaines de kilo-octets et masquerait
 * plus de dix secondes de retard. [LuxAsrSession] le réduit donc à
 * [TAMPON_SYSTEME_OCTETS] : la file d'OkHttp grossit alors dès que le réseau
 * décroche, et ce plafond ne bride rien, puisque même à 500 ms d'aller-retour
 * il laisse passer quatre fois le débit de la dictée.
 *
 * Sans `Context` ni Android, pour rester testable sur la JVM.
 */
object RetardEnvoi {
    /** PCM 16 bits, 16 kHz, mono. */
    const val OCTETS_PAR_SECONDE = 32_000L

    /** Tampon d'envoi TCP demandé au système (Linux le double). */
    const val TAMPON_SYSTEME_OCTETS = 32 * 1024

    /** Au-delà d'une seconde d'audio en souffrance, on prévient. */
    const val LENT_OCTETS = OCTETS_PAR_SECONDE

    /**
     * L'avertissement ne s'éteint que sous un quart de seconde : sans cet
     * écart, un réseau à la limite ferait clignoter le bandeau.
     */
    const val RETABLI_OCTETS = OCTETS_PAR_SECONDE / 4

    /**
     * Huit secondes de retard, en plus des deux que peut tenir le tampon
     * système : on coupe le micro, sans rien abandonner. Ce qui attend finit
     * de partir (voir [FINALISATION_MAX_MS]) et le texte arrive en entier, en
     * retard.
     *
     * C'était six secondes et un abandon pur jusqu'au 2 octobre 2026. Mesuré
     * sur téléphone la veille, avec un débit bridé : un creux de 10 s à
     * 64 kbit/s — un tunnel — coupait la dictée 0,6 s avant le retour du
     * réseau, et à 192 kbit/s la fin de l'extrait était perdue (69 mots sur
     * 99), alors que le réseau était lent mais vivant. Huit secondes laissent
     * passer ce creux ; couper le micro sans abandonner sauve le texte.
     */
    const val COUPER_MICRO_OCTETS = 8 * OCTETS_PAR_SECONDE

    /**
     * Plus rien ne part depuis cinq secondes alors que de l'audio attend :
     * le réseau n'est plus lent, il est bloqué (zone blanche, wifi sans
     * Internet). Là seulement on abandonne, en gardant le texte déjà rendu.
     */
    const val BLOCAGE_MS = 5_000L

    /**
     * Temps laissé, une fois le micro fermé, pour finir d'envoyer ce qui
     * attend. Au-delà, on rend ce qu'on a : à 96 kbit/s, huit secondes de
     * retard se vident en un peu moins de trente secondes.
     */
    const val FINALISATION_MAX_MS = 30_000L

    enum class Verdict { FLUIDE, LENT, COUPER_MICRO }

    fun juger(enAttente: Long, etaitLent: Boolean): Verdict = when {
        enAttente >= COUPER_MICRO_OCTETS -> Verdict.COUPER_MICRO
        enAttente >= LENT_OCTETS -> Verdict.LENT
        etaitLent && enAttente > RETABLI_OCTETS -> Verdict.LENT
        else -> Verdict.FLUIDE
    }

    /**
     * Lent ou bloqué ? Lent, l'audio part, moins vite qu'on ne parle. Bloqué,
     * plus un octet ne part depuis [BLOCAGE_MS]. Une file vide n'est jamais
     * bloquée : il n'y a rien à envoyer.
     */
    fun bloque(enAttente: Long, depuisDernierEnvoiMs: Long): Boolean =
        enAttente > 0 && depuisDernierEnvoiMs >= BLOCAGE_MS
}
