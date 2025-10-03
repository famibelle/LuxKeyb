package com.example.kreyolkeyboard

import android.graphics.Color

/**
 * Représente une suggestion avec sa langue et couleur
 */
data class BilingualSuggestion(
    val word: String,
    val score: Float,
    val language: SuggestionLanguage,
    val source: SuggestionSource = SuggestionSource.DICTIONARY
) {
    /**
     * Obtient la couleur associée à cette suggestion
     */
    fun getColor(): Int {
        return when (language) {
            SuggestionLanguage.KREYOL -> KeyboardColors.KREYOL_GREEN
            SuggestionLanguage.FRENCH -> KeyboardColors.FRENCH_BLUE
        }
    }
    
    /**
     * Obtient le nom lisible de la langue
     */
    fun getLanguageName(): String {
        return when (language) {
            SuggestionLanguage.KREYOL -> "Kreyòl"
            SuggestionLanguage.FRENCH -> "Français"
        }
    }
}

/**
 * Types de langues supportées
 */
enum class SuggestionLanguage {
    KREYOL,
    FRENCH
}

/**
 * Sources des suggestions
 */
enum class SuggestionSource {
    DICTIONARY,    // Dictionnaire statique
    NGRAM,        // Modèle N-gram
    LEARNED,      // Apprentissage utilisateur
    HYBRID        // Combinaison de sources
}

/**
 * Couleurs du clavier
 */
object KeyboardColors {
    // 🟢 Vert pour Guadeloupéen/Kreyòl (authenticité tropicale)
    val KREYOL_GREEN = Color.parseColor("#50C878")  // Vert émeraude
    
    // 🔵 Bleu pour Français (couleur officielle France)
    val FRENCH_BLUE = Color.parseColor("#4A90E2")   // Bleu France
    
    // Couleurs d'interface
    val BACKGROUND_NEUTRAL = Color.parseColor("#F8F9FA")  // Fond neutre
    val BORDER_LIGHT = Color.parseColor("#E9ECEF")        // Bordures subtiles
    val TEXT_PRIMARY = Color.parseColor("#212529")        // Texte principal
    val TEXT_SECONDARY = Color.parseColor("#6C757D")      // Texte secondaire
}

/**
 * Configuration du mode bilingue
 */
data class BilingualConfig(
    val frenchActivationThreshold: Int = 3,        // Activer français à partir de 3 lettres
    val maxKreyolSuggestions: Int = 3,             // Maximum 3 suggestions kreyòl
    val maxFrenchSuggestions: Int = 2,             // Maximum 2 suggestions françaises
    val kreyolPriorityBoost: Float = 1.5f,         // Bonus score pour kreyòl (+50%)
    val frenchPenalty: Float = 0.8f,               // Malus pour français (-20%)
    val enableFrenchSupport: Boolean = true,       // Support français activé
    val kreyolOnlyMode: Boolean = false,           // Mode 100% kreyòl
    val showLanguageIndicators: Boolean = true      // Afficher couleurs langues
) {
    /**
     * Vérifie si le français doit être activé pour cette saisie
     */
    fun shouldActivateFrench(input: String): Boolean {
        return enableFrenchSupport && 
               !kreyolOnlyMode && 
               input.length >= frenchActivationThreshold
    }
    
    /**
     * Calcule le score ajusté selon la langue
     */
    fun adjustScoreByLanguage(score: Float, language: SuggestionLanguage): Float {
        return when (language) {
            SuggestionLanguage.KREYOL -> score * kreyolPriorityBoost
            SuggestionLanguage.FRENCH -> score * frenchPenalty
        }
    }
}