package com.example.kreyolkeyboard

import android.service.textservice.SpellCheckerService
import android.util.Log
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import kotlinx.coroutines.runBlocking

/**
 * Service système de vérification orthographique (SpellCheckerService), distinct du
 * clavier IME : n'importe quel champ de texte (Messages, Notes, WhatsApp...) peut
 * interroger ce service une fois sélectionné dans Paramètres > Langues et saisie >
 * Vérification orthographique.
 *
 * Réutilise SuggestionEngine (dictionnaires kréyòl + français, correction Levenshtein)
 * sans dupliquer ni le chargement du JSON ni le scoring.
 */
class KreyolSpellCheckerService : SpellCheckerService() {

    companion object {
        private const val TAG = "KreyolSpellChecker"
        private const val DEFAULT_SUGGESTIONS_LIMIT = 5

        /**
         * Décide si un mot doit être souligné comme faute.
         * `internal` (et non private) pour être testable en JVM sans Context.
         *
         * Un mot inconnu n'est signalé que si une correction plausible existe,
         * c'est-à-dire un mot du dictionnaire à deux éditions ou moins. Sans cette
         * réserve, le service soulignerait tout ce qu'il ignore, ce qui serait pire
         * que l'absence de correcteur : il est déclaré pour la locale française, où
         * il se substitue donc au correcteur du système, alors que sa couverture du
         * français est mince (quelques centaines de mots contre plusieurs milliers
         * en kréyòl). Un mot français courant absent de notre dictionnaire n'a
         * aucun voisin kréyòl proche, il passe donc sans être marqué, tandis
         * qu'une vraie faute de frappe kréyòl reste détectée et corrigée.
         */
        internal fun shouldFlagAsTypo(isKnown: Boolean, corrections: List<String>): Boolean =
            !isKnown && corrections.isNotEmpty()
    }

    override fun createSession(): Session = KreyolSpellCheckerSession()

    private inner class KreyolSpellCheckerSession : Session() {

        private lateinit var suggestionEngine: SuggestionEngine

        override fun onCreate() {
            suggestionEngine = SuggestionEngine(applicationContext)
            // onCreate()/onGetSuggestions*() tournent sur un handler thread interne à
            // SpellCheckerService (jamais le thread UI de l'app dans laquelle on tape) :
            // un chargement bloquant du dictionnaire ici est le pattern standard.
            runBlocking { suggestionEngine.initialize() }
            Log.d(TAG, "Session créée, dictionnaires chargés")
        }

        override fun onGetSuggestions(textInfo: TextInfo?, suggestionsLimit: Int): SuggestionsInfo {
            return buildSuggestionsInfo(textInfo, suggestionsLimit)
        }

        override fun onGetSuggestionsMultiple(
            textInfos: Array<TextInfo>?,
            suggestionsLimit: Int,
            sequentialWords: Boolean
        ): Array<SuggestionsInfo> {
            return textInfos?.map { buildSuggestionsInfo(it, suggestionsLimit) }?.toTypedArray()
                ?: emptyArray()
        }

        // onGetSentenceSuggestionsMultiple() n'est PAS surchargée : l'implémentation par
        // défaut de la classe de base tokenize la phrase et délègue déjà à
        // onGetSuggestionsMultiple() ci-dessus — c'est le chemin réel emprunté par
        // TextView via SpellCheckerSession.getSentenceSuggestions() sur API 21+.

        private fun buildSuggestionsInfo(textInfo: TextInfo?, suggestionsLimit: Int): SuggestionsInfo {
            val word = textInfo?.text?.trim().orEmpty()

            if (word.isEmpty() || word.none { it.isLetter() }) {
                return inDictionary(textInfo)
            }

            val isKnown = suggestionEngine.isKnownWord(word)
            val limit = if (suggestionsLimit > 0) suggestionsLimit else DEFAULT_SUGGESTIONS_LIMIT
            val corrections = if (isKnown) {
                emptyList()
            } else {
                suggestionEngine.getSpellingSuggestions(word, limit)
            }

            if (!shouldFlagAsTypo(isKnown, corrections)) {
                return inDictionary(textInfo)
            }

            Log.d(TAG, "Faute signalée: '$word' -> $corrections")
            return SuggestionsInfo(
                SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO,
                corrections.toTypedArray()
            ).withOriginOf(textInfo)
        }

        private fun inDictionary(textInfo: TextInfo?): SuggestionsInfo =
            SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, emptyArray())
                .withOriginOf(textInfo)

        /**
         * Reporte sur la réponse le couple (cookie, séquence) porté par la demande.
         *
         * C'est ce couple qui permet au client de rattacher un résultat à la requête
         * qui l'a produit. Sans lui, TextView reçoit bien nos verdicts mais ne peut
         * pas les associer aux mots analysés : les fautes détectées n'étaient jamais
         * soulignées, alors même que le service tournait et les signalait.
         */
        private fun SuggestionsInfo.withOriginOf(textInfo: TextInfo?): SuggestionsInfo = apply {
            if (textInfo != null) {
                setCookieAndSequence(textInfo.cookie, textInfo.sequence)
            }
        }
    }
}
