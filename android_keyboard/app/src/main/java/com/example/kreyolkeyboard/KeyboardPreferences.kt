package com.example.kreyolkeyboard

import android.content.Context
import androidx.annotation.StringRes

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
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_LONG_PRESS_DELAY = "long_press_delay_ms"
    private const val KEY_DISPOSITION = "disposition"
    private const val KEY_FRENCH_SUGGESTIONS = "french_suggestions_enabled"

    /** Les deux retours sont actifs par défaut, comme sur les autres claviers. */
    private const val DEFAULT_ENABLED = true

    /** La rangée française est coupée par défaut depuis la 30.3.0. */
    private const val DEFAULT_FRENCH_SUGGESTIONS = false

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

    /**
     * Thème du clavier. Par défaut il suit le téléphone.
     *
     * Un choix explicite plutôt que le seul suivi du système, pour la même raison
     * que les deux interrupteurs ci-dessus : sur plusieurs surcouches, le réglage
     * jour/nuit du téléphone ne descend pas jusqu'aux claviers tiers, et
     * l'utilisateur se retrouverait sans moyen d'obtenir le clavier qu'il veut.
     * Gboard et SwiftKey proposent également les trois positions.
     */
    fun themeMode(context: Context): KeyboardTheme.Mode =
        KeyboardTheme.Mode.depuisCle(prefs(context).getString(KEY_THEME_MODE, null))

    fun setThemeMode(context: Context, mode: KeyboardTheme.Mode) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode.cle).apply()
        KeyboardTheme.refresh(context)
    }

    /**
     * Délai avant que l'appui long n'ouvre la popup d'accents d'une touche.
     *
     * Jusqu'à la 29.4.2, ce délai était fixe et s'ajoutait à celui du système :
     * le long-clic natif d'Android (400 ms par défaut, jusqu'à 1,5 s selon le
     * réglage d'accessibilité du téléphone) puis 500 ms d'AccentHandler, soit
     * près d'une seconde avant de voir « ü ». Des utilisateurs l'ont trouvé trop
     * long, et Gboard tourne autour de 300 ms. Le délai part désormais du
     * moment où le doigt se pose, et un seul.
     *
     * Les crans longs restent pour qui relâche lentement (ACCESSIBILITE.md,
     * point 4) : une popup ouverte sans le vouloir coûte un appui de plus.
     */
    enum class DelaiAppuiLong(val ms: Long, @StringRes val libelle: Int) {
        COURT(300L, R.string.delai_court),
        MOYEN(500L, R.string.delai_moyen),
        LONG(800L, R.string.delai_long),
        TRES_LONG(1200L, R.string.delai_tres_long);

        companion object {
            val DEFAUT = COURT

            /** Tolérante, comme [KeyboardTheme.Mode.depuisCle] : une valeur
             *  inconnue retombe sur le défaut plutôt que de jeter. */
            fun depuisMs(ms: Long?): DelaiAppuiLong =
                entries.firstOrNull { it.ms == ms } ?: DEFAUT
        }
    }

    fun delaiAppuiLong(context: Context): DelaiAppuiLong =
        DelaiAppuiLong.depuisMs(prefs(context).getLong(KEY_LONG_PRESS_DELAY, DelaiAppuiLong.DEFAUT.ms))

    fun setDelaiAppuiLong(context: Context, delai: DelaiAppuiLong) {
        prefs(context).edit().putLong(KEY_LONG_PRESS_DELAY, delai.ms).apply()
    }

    /**
     * Disposition de la page des lettres (v29.5.0). Suisse allemand par défaut,
     * voir [DispositionClavier.DEFAUT].
     */
    fun disposition(context: Context): DispositionClavier =
        DispositionClavier.depuisCle(prefs(context).getString(KEY_DISPOSITION, null))

    fun setDisposition(context: Context, disposition: DispositionClavier) {
        prefs(context).edit().putString(KEY_DISPOSITION, disposition.cle).apply()
    }

    /**
     * Rangée bleue des propositions en français (v30.1.0). Coupée par défaut
     * depuis la 30.3.0 (décision du propriétaire, 2026-10-03) : un utilisateur
     * l'a trouvée inutile, et le clavier y gagne une rangée de hauteur. Qui
     * glisse des mots français dans ses phrases la rallume dans les réglages.
     * Le changement vaut aussi pour les installations existantes qui n'ont
     * jamais touché l'interrupteur, comme pour la disposition en 29.5.0.
     *
     * Ce réglage ne cache que les propositions. La reconnaissance du français
     * reste entière : le correcteur orthographique remplace celui du téléphone
     * pour le français, et sans elle il soulignerait tout le français écrit
     * dans les autres applications.
     */
    fun propositionsFrancais(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FRENCH_SUGGESTIONS, DEFAULT_FRENCH_SUGGESTIONS)

    fun setPropositionsFrancais(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_FRENCH_SUGGESTIONS, enabled).apply()
    }
}
