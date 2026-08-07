package com.example.kreyolkeyboard

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Dictionnaire personnel : les mots que l'utilisateur emploie et que le corpus
 * littéraire ne contient pas (prénoms, toponymes, néologismes, mots de famille).
 *
 * Sans lui, un tel mot n'est jamais suggéré et reste souligné comme faute, quel
 * que soit le nombre de fois qu'on le tape. L'ancien service IME tentait de
 * passer par `UserDictionary.Words.addWord()`, l'API du dictionnaire partagé
 * d'Android, mais son propre commentaire notait déjà que l'écriture y est
 * réservée aux applications système. On stocke donc dans l'espace privé de
 * l'application, ce qui a l'avantage de ne rien exposer aux autres apps.
 *
 * RESPECT DE LA VIE PRIVÉE, dans le même esprit que CreoleDictionaryWithUsage :
 * - rien ne quitte l'appareil
 * - aucun texte complet n'est conservé, seulement des mots isolés et leur compte
 * - les mots des champs de mot de passe ne sont jamais soumis (filtré en amont)
 * - un mot n'est retenu qu'après plusieurs emplois, et les candidats en attente
 *   ne sont comptés qu'en mémoire : une chaîne tapée une seule fois n'est jamais
 *   écrite sur le disque
 */
class PersonalDictionary(private val context: Context) {

    companion object {
        private const val TAG = "PersonalDictionary"
        private const val FILE_NAME = "personal_dict.json"

        /**
         * Nombre d'emplois nécessaires avant qu'un mot inconnu soit retenu. Deux
         * suffisent : une faute de frappe se répète rarement à l'identique, alors
         * qu'un prénom ou un toponyme revient vite.
         */
        internal const val LEARNING_THRESHOLD = 2

        // En deçà, le mot n'apprend rien d'utile et le risque de retenir une
        // amorce de frappe l'emporte
        private const val MIN_WORD_LENGTH = 3

        // Un mot appris ne doit pas rivaliser avec le vocabulaire courant du
        // corpus : sa fréquence reste plafonnée bien en dessous des mots très
        // fréquents (ka 1800, pa 664), le bonus d'usage personnel se chargeant
        // de le faire remonter s'il est vraiment employé.
        internal const val MAX_LEARNED_FREQUENCY = 40

        /**
         * Décide si un mot peut entrer au dictionnaire personnel.
         * `internal` (et non private) pour être testable en JVM sans Context.
         *
         * Les mêmes garde-fous que le suivi d'usage : pas de chiffres (codes,
         * mots de passe), pas d'URL ni d'adresse e-mail, et rien de trop court.
         * Un mot déjà connu du moteur n'a évidemment rien à faire ici.
         */
        internal fun isLearnable(word: String, isAlreadyKnown: Boolean): Boolean {
            if (isAlreadyKnown) return false
            val normalized = word.trim()
            if (normalized.length < MIN_WORD_LENGTH) return false
            if (!normalized.all { it.isLetter() || it == '-' || it == '\'' }) return false
            if (!normalized.any { it.isLetter() }) return false
            if (normalized.contains("http", ignoreCase = true)) return false
            if (normalized.contains("www", ignoreCase = true)) return false
            return true
        }

        /**
         * Fréquence attribuée à un mot appris, à partir du nombre d'emplois.
         * `internal` pour la même raison que ci-dessus.
         */
        internal fun frequencyFor(useCount: Int): Int =
            useCount.coerceIn(1, MAX_LEARNED_FREQUENCY)
    }

    // Mots retenus, persistés : mot normalisé en minuscules -> nombre d'emplois
    private val learned = mutableMapOf<String, Int>()

    // Candidats en attente du seuil. Volontairement en mémoire seulement : tant
    // qu'un mot n'a pas fait ses preuves, il ne touche pas le disque.
    private val pending = mutableMapOf<String, Int>()

    init {
        load()
    }

    /**
     * Soumet un mot validé par l'utilisateur.
     *
     * @return le mot retenu si ce dépôt vient de le faire entrer au dictionnaire
     *         (ou d'incrémenter un mot déjà retenu), `null` s'il est ignoré ou
     *         encore en attente du seuil
     */
    @Synchronized
    fun offer(word: String, isAlreadyKnown: Boolean): String? {
        if (!isLearnable(word, isAlreadyKnown)) return null

        val key = word.lowercase().trim()

        learned[key]?.let { previous ->
            learned[key] = previous + 1
            save()
            Log.d(TAG, "Mot personnel réemployé: '$key' (${previous + 1})")
            return key
        }

        val count = (pending[key] ?: 0) + 1
        if (count < LEARNING_THRESHOLD) {
            pending[key] = count
            return null
        }

        pending.remove(key)
        learned[key] = count
        save()
        Log.d(TAG, "Nouveau mot personnel retenu: '$key'")
        return key
    }

    /**
     * Mots retenus sous la forme attendue par le moteur de suggestions.
     */
    @Synchronized
    fun entries(): List<Pair<String, Int>> =
        learned.map { (word, count) -> word to frequencyFor(count) }

    @Synchronized
    fun contains(word: String): Boolean = learned.containsKey(word.lowercase().trim())

    @Synchronized
    fun size(): Int = learned.size

    /**
     * Oublie tout. Prévu pour un futur bouton de réglages : l'utilisateur doit
     * pouvoir effacer ce que le clavier a retenu de lui.
     */
    @Synchronized
    fun forgetAll() {
        learned.clear()
        pending.clear()
        save()
        Log.d(TAG, "Dictionnaire personnel effacé")
    }

    private fun load() {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return
        try {
            val json = JSONObject(file.readText())
            for (key in json.keys()) {
                learned[key] = json.optInt(key, 1)
            }
            Log.d(TAG, "Dictionnaire personnel chargé: ${learned.size} mots")
        } catch (e: Exception) {
            // Fichier corrompu ou format inattendu : repartir d'un dictionnaire
            // vide vaut mieux que priver l'utilisateur de toute suggestion
            Log.e(TAG, "Lecture impossible, réinitialisation: ${e.message}", e)
            learned.clear()
        }
    }

    private fun save() {
        try {
            val json = JSONObject()
            learned.forEach { (word, count) -> json.put(word, count) }
            File(context.filesDir, FILE_NAME).writeText(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Écriture impossible: ${e.message}", e)
        }
    }
}
