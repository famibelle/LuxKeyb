package com.example.kreyolkeyboard

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Retour de frappe du clavier : vibration et son, au même endroit.
 *
 * Il existe parce que trois classes en ont besoin (les touches dans
 * [KeyboardLayoutManager], les puces de suggestion dans le service, les caractères
 * accentués dans [AccentHandler]) et qu'une politique de retour dupliquée finit
 * toujours par diverger. Tout ce qui écrit du texte passe par ici.
 *
 * **Le son n'était pas voulu et n'était pas le bon.** Aucune ligne du projet ne
 * demandait de son, mais `View.performClick()` joue de lui-même
 * `SoundEffectConstants.CLICK`, soit `Effect_Tick.ogg`, le clic d'interface d'un
 * bouton quelconque. Les touches lettres cliquaient donc comme un bouton de
 * formulaire, tandis que la barre d'espace restait muette faute de
 * `OnClickListener` (elle n'en a pas, pour éviter le double espace décrit dans
 * `setupButtonInteractions`). Android embarque pourtant quatre sons de clavier
 * distincts, que [effetPour] choisit désormais explicitement.
 *
 * Les appelants doivent poser `isSoundEffectsEnabled = false` sur leurs vues, sinon
 * le son implicite de `performClick()` s'ajoute à celui joué ici.
 *
 * Vibration comme son suivent les réglages du téléphone, retour tactile d'un côté
 * et sons tactiles de l'autre : `AudioManager.playSoundEffect` les consulte
 * lui-même, et l'absence de `FLAG_IGNORE_GLOBAL_SETTING` laisse le système décider
 * pour la vibration (cf. ACCESSIBILITE.md, point 2).
 */
object KeyFeedback {

    private const val TAG = "KeyFeedback"

    // Conservé entre les frappes : le service de son se cherche une fois, pas à
    // chaque touche. Le contexte d'application est utilisé pour ne pas retenir une
    // vue ni la fenêtre de saisie.
    private var audioManager: AudioManager? = null

    /**
     * Retour complet d'une frappe : vibration puis son.
     *
     * @param key la touche frappée, qui choisit le son. `null` ou toute autre valeur
     *   qu'espace, suppression et entrée donnent le son de frappe standard, ce qui
     *   couvre les lettres, la ponctuation, les touches de mode, les caractères
     *   accentués et les puces de suggestion.
     */
    fun onKeyPress(view: View, key: String? = null) {
        vibrate(view)
        playSound(view.context, key)
    }

    private fun vibrate(view: View) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Feedback haptique non disponible: ${e.message}")
        }
    }

    private fun playSound(context: Context, key: String?) {
        try {
            val manager = audioManager ?: (context.applicationContext
                .getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
                ?.also { audioManager = it }
            manager?.playSoundEffect(effetPour(key))
        } catch (e: Exception) {
            Log.d(TAG, "Son de frappe non disponible: ${e.message}")
        }
    }

    /** Le son de frappe d'Android correspondant à la touche. */
    private fun effetPour(key: String?): Int = when (key) {
        " " -> AudioManager.FX_KEYPRESS_SPACEBAR
        "⌫" -> AudioManager.FX_KEYPRESS_DELETE
        "⏎" -> AudioManager.FX_KEYPRESS_RETURN
        else -> AudioManager.FX_KEYPRESS_STANDARD
    }
}
