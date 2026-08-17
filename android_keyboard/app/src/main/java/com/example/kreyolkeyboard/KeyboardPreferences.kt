package com.example.kreyolkeyboard

import android.content.Context

/**
 * Réglages de comportement du clavier, partagés entre l'écran de l'application
 * (SettingsActivity) et le service de saisie (KreyolInputMethodServiceRefactored).
 *
 * Le service et l'activité vivent dans le même processus (aucun `android:process`
 * dans le manifeste), donc l'instance de SharedPreferences est la même des deux
 * côtés : une écriture depuis l'écran de réglages est visible immédiatement, sans
 * MODE_MULTI_PROCESS ni diffusion.
 *
 * ### Pourquoi ces réglages existent
 *
 * La v10.11.5 avait retiré `FLAG_IGNORE_GLOBAL_SETTING` du retour haptique pour que
 * le clavier obéisse au téléphone, et n'avait ajouté aucun réglage : le système
 * semblait déjà offrir l'interrupteur. C'était faux en pratique, constaté sur un
 * Samsung réel en 10.11.6 : **sur One UI, « Vibration au toucher » ne pilote que le
 * clavier Samsung**, et aucun réglage accessible ne couvre les claviers tiers. Le
 * clavier était donc devenu muet, sans aucun moyen de le rallumer.
 *
 * D'où ce retour en arrière assumé : le clavier reprend la main sur son retour de
 * frappe, comme le font Gboard et SwiftKey, qui ont eux aussi leurs propres
 * interrupteurs et ne dépendent pas du réglage générique du téléphone. Ces deux
 * réglages ne dupliquent donc pas un interrupteur du système, ils remplacent un
 * interrupteur qui n'existe pas.
 *
 * Ils restent indispensables : sans eux, forcer la vibration redeviendrait le
 * blocage sans échappatoire décrit au point 2 de ACCESSIBILITE.md.
 */
object KeyboardPreferences {

    private const val PREFS_NAME = "kreyol_clavier_prefs"
    private const val KEY_HAPTIC_ENABLED = "haptic_enabled"
    private const val KEY_SOUND_ENABLED = "sound_enabled"

    /** Les deux retours sont actifs par défaut, comme sur les autres claviers. */
    private const val DEFAULT_ENABLED = true

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hapticEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HAPTIC_ENABLED, DEFAULT_ENABLED)

    fun soundEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SOUND_ENABLED, DEFAULT_ENABLED)

    fun setHapticEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HAPTIC_ENABLED, enabled).apply()
        KeyFeedback.refresh(context)
    }

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
        KeyFeedback.refresh(context)
    }
}
