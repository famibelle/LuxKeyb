package com.example.kreyolkeyboard

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * La langue de l'interface, choisie dans Réglages du clavier.
 *
 * Android 13 offre déjà ce choix (Paramètres › Applications › Lëtzebuergesch
 * Clavier › Langue, déclaré par `locales_config.xml`), mais personne ne va le
 * chercher là, et il n'existe pas avant Android 13 : un Portugais du
 * Luxembourg dont le téléphone est en français n'avait aucun moyen d'avoir
 * l'appli en portugais.
 *
 * [AppCompatDelegate.setApplicationLocales] fait l'essentiel : à partir
 * d'Android 13 il écrit dans le réglage système (les deux restent donc
 * d'accord), avant il retient le choix lui-même (`autoStoreLocales` dans le
 * manifeste) et l'applique aux activités AppCompat. Les gloses suivent sans
 * rien de plus, puisqu'elles sont choisies par la ressource
 * `langue_traductions`.
 *
 * Reste, avant Android 13, ce qui n'est pas une activité AppCompat : le
 * clavier lui-même (touche Entrée, messages de la dictée) et l'écran de
 * permission du micro. Ils lisent la langue dans [KeyboardPreferences] via
 * [ressources] et [contexte]. Le clavier n'enveloppe pas son contexte de base,
 * il remplace ses `Resources` : depuis Android 12, `InputMethodService` est un
 * service de fenêtre dont le contexte de base porte le jeton de fenêtre, qu'un
 * `createConfigurationContext` ne transmet pas.
 */
object LangueInterface {

    /**
     * Les langues proposées, dans l'ordre de l'écran : les trois du pays, puis
     * l'anglais et le portugais. Chacune est écrite dans sa propre langue, pour
     * qu'on retrouve la sienne même dans une interface qu'on ne lit pas.
     */
    enum class Langue(val tag: String, val nom: String) {
        LB("lb", "Lëtzebuergesch"),
        FR("fr", "Français"),
        DE("de", "Deutsch"),
        EN("en", "English"),
        PT("pt", "Português");

        companion object {
            fun depuisTag(tag: String?): Langue? {
                val langue = tag?.substringBefore('-')?.substringBefore('_') ?: return null
                return entries.firstOrNull { it.tag == langue }
            }
        }
    }

    private val moderne = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * La langue choisie, `null` pour « comme le téléphone ». À partir
     * d'Android 13 on la lit dans le système, où l'utilisateur a pu la changer
     * sans passer par l'appli.
     */
    fun actuelle(context: Context): Langue? =
        if (moderne) Langue.depuisTag(AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag())
        else Langue.depuisTag(KeyboardPreferences.langueInterface(context))

    fun choisir(context: Context, langue: Langue?) {
        KeyboardPreferences.setLangueInterface(context, langue?.tag)
        ressourcesEnCache = null
        AppCompatDelegate.setApplicationLocales(
            if (langue == null) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(langue.tag)
        )
    }

    /**
     * Le dernier calcul de [ressources] : `null` tant qu'il n'a pas été fait,
     * sinon les `Resources` à rendre (`null` dedans pour « celles d'Android »).
     * `getResources()` est appelé à chaque conversion de dp ; relire les
     * préférences à chaque fois serait gratuit en apparence seulement.
     */
    @Volatile private var ressourcesEnCache: Array<Resources?>? = null

    /**
     * Les `Resources` du clavier dans la langue choisie, avant Android 13.
     * Ailleurs, ou sans choix, celles d'Android telles quelles.
     */
    fun ressources(context: Context, base: Resources): Resources {
        if (moderne) return base
        ressourcesEnCache?.let { return it[0] ?: base }
        val tag = KeyboardPreferences.langueInterface(context)
        val res = tag?.let {
            context.createConfigurationContext(configuration(base.configuration, it)).resources
        }
        ressourcesEnCache = arrayOf(res)
        return res ?: base
    }

    /** À appeler quand la configuration change (rotation, mode sombre). */
    fun oublierRessources() {
        ressourcesEnCache = null
    }

    /** Le contexte d'une activité qui n'est pas AppCompat, avant Android 13. */
    fun contexte(base: Context): Context {
        if (moderne) return base
        val tag = KeyboardPreferences.langueInterface(base) ?: return base
        return base.createConfigurationContext(configuration(base.resources.configuration, tag))
    }

    private fun configuration(base: Configuration, tag: String) =
        Configuration(base).apply { setLocale(Locale.forLanguageTag(tag)) }
}
