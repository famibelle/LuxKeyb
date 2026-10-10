package com.example.kreyolkeyboard

import android.content.SharedPreferences

/**
 * Des préférences qui ne quittent pas la mémoire : ce que le clavier lit et
 * écrit avant le premier déverrouillage du téléphone.
 *
 * Le service est `directBootAware` depuis la 34.5.0, pour qu'Android cesse
 * d'afficher, à l'activation, un second avertissement (« après le redémarrage,
 * vous devez déverrouiller… »). Il peut donc démarrer avant que le stockage
 * chiffré de l'utilisateur soit ouvert, et y lire une préférence lèverait une
 * exception. Il lit alors celles-ci, vides : le clavier tourne avec ses
 * réglages par défaut, et rien de ce qu'on y écrit (emojis récents, tunnel)
 * n'atteint le disque. Au déverrouillage, le processus repart et retrouve les
 * vraies.
 */
class PreferencesEnMemoire : SharedPreferences {

    private val valeurs = HashMap<String, Any?>()
    private val ecouteurs = LinkedHashSet<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): Map<String, *> = synchronized(valeurs) { HashMap(valeurs) }

    override fun getString(key: String?, defValue: String?): String? =
        lire(key) as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? =
        (lire(key) as? Set<String>)?.toSet() ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = lire(key) as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = lire(key) as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = lire(key) as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        lire(key) as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = synchronized(valeurs) { valeurs.containsKey(key) }

    override fun edit(): SharedPreferences.Editor = Edition()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        synchronized(ecouteurs) { ecouteurs.add(listener) }
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        synchronized(ecouteurs) { ecouteurs.remove(listener) }
    }

    private fun lire(key: String?): Any? = synchronized(valeurs) { valeurs[key] }

    private inner class Edition : SharedPreferences.Editor {
        private val changements = HashMap<String, Any?>()
        private val retraits = HashSet<String>()
        private var toutEffacer = false

        override fun putString(key: String, value: String?) = poser(key, value)
        override fun putStringSet(key: String, values: Set<String>?) = poser(key, values?.toSet())
        override fun putInt(key: String, value: Int) = poser(key, value)
        override fun putLong(key: String, value: Long) = poser(key, value)
        override fun putFloat(key: String, value: Float) = poser(key, value)
        override fun putBoolean(key: String, value: Boolean) = poser(key, value)

        override fun remove(key: String) = apply { retraits.add(key) }

        override fun clear() = apply { toutEffacer = true }

        override fun commit(): Boolean {
            appliquer()
            return true
        }

        override fun apply() {
            appliquer()
        }

        private fun poser(key: String, value: Any?) = apply {
            // Comme Android : écrire null revient à retirer la clé.
            if (value == null) retraits.add(key) else changements[key] = value
        }

        private fun appliquer() {
            val modifiees = synchronized(valeurs) {
                if (toutEffacer) valeurs.clear()
                retraits.forEach { valeurs.remove(it) }
                valeurs.putAll(changements)
                retraits + changements.keys
            }
            val aPrevenir = synchronized(ecouteurs) { ecouteurs.toList() }
            for (cle in modifiees) {
                for (e in aPrevenir) e.onSharedPreferenceChanged(this@PreferencesEnMemoire, cle)
            }
        }
    }
}
