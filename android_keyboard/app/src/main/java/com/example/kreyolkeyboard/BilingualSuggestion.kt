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
            SuggestionLanguage.LUXEMBOURGISH -> KeyboardColors.LUX_RED
            SuggestionLanguage.FRENCH -> KeyboardColors.LUX_BLUE
        }
    }

    /**
     * Couleur du texte de la puce, choisie sur le contraste réellement mesuré :
     * blanc sur le rouge du drapeau donne 4,2:1, mais seulement 2,9:1 sur son
     * bleu ciel, qui demande donc une encre sombre (5,9:1).
     */
    fun getTextColor(): Int {
        return when (language) {
            SuggestionLanguage.LUXEMBOURGISH -> KeyboardColors.CHIP_TEXT_ON_RED
            SuggestionLanguage.FRENCH -> KeyboardColors.CHIP_TEXT_ON_BLUE
        }
    }
    
    /**
     * Obtient le nom lisible de la langue
     */
    fun getLanguageName(): String {
        return when (language) {
            SuggestionLanguage.LUXEMBOURGISH -> "Lëtzebuergesch"
            SuggestionLanguage.FRENCH -> "Français"
        }
    }

    /**
     * Micro-label affiché avant le mot dans la puce de suggestion (KR/FR)
     */
    fun getShortLabel(): String {
        return when (language) {
            SuggestionLanguage.LUXEMBOURGISH -> "LB"
            SuggestionLanguage.FRENCH -> "FR"
        }
    }
}

/**
 * Types de langues supportées
 */
enum class SuggestionLanguage {
    LUXEMBOURGISH,
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
    // Couleurs du drapeau luxembourgeois, comme les touches : le luxembourgeois
    // prend le rouge, le français le bleu ciel.
    //
    // Ces quatre-là ne figurent pas dans KeyboardTheme et n'ont pas à y figurer :
    // ce sont les couleurs du drapeau et leurs encres mesurées, identiques en
    // thème clair et en thème sombre. Tout ce qui bascule vit dans KeyboardTheme.
    val LUX_RED = Color.parseColor("#ED2939")
    val LUX_BLUE = Color.parseColor("#00A1DE")
    val CHIP_TEXT_ON_RED = Color.parseColor("#FFFFFF")    // 4,2:1 sur le rouge
    val CHIP_TEXT_ON_BLUE = Color.parseColor("#1A1A1A")   // 5,9:1 sur le bleu ciel

    // BACKGROUND_NEUTRAL, BORDER_LIGHT, TEXT_PRIMARY et TEXT_SECONDARY ont été
    // retirés avec l'arrivée du thème : les trois premiers n'avaient aucun
    // appelant, et le quatrième colorait l'étiquette de langue de la barre de
    // suggestions, qui lit désormais KeyboardTheme.palette().encreAttenuee — sans
    // quoi elle serait restée gris moyen sur le fond sombre.
}

/**
 * Configuration du mode bilingue
 */
data class BilingualConfig(
    val frenchActivationThreshold: Int = 3,        // Activer français à partir de 3 lettres
    val maxLuxSuggestions: Int = 3,             // Maximum 3 suggestions luxembourgeoises
    val maxFrenchSuggestions: Int = 2,             // Maximum 2 suggestions françaises
    val luxPriorityBoost: Float = 1.5f,         // Bonus score pour luxembourgeois (+50%)
    val frenchPenalty: Float = 0.8f,               // Malus pour français (-20%)
    val enableFrenchSupport: Boolean = true,       // Support français activé
    val luxOnlyMode: Boolean = false,           // Mode 100% luxembourgeois
    val showLanguageIndicators: Boolean = true      // Afficher couleurs langues
) {
    /**
     * Vérifie si le français doit être activé pour cette saisie
     */
    fun shouldActivateFrench(input: String): Boolean {
        return enableFrenchSupport && 
               !luxOnlyMode && 
               input.length >= frenchActivationThreshold
    }
    
    /**
     * Calcule le score ajusté selon la langue
     */
    fun adjustScoreByLanguage(score: Float, language: SuggestionLanguage): Float {
        return when (language) {
            SuggestionLanguage.LUXEMBOURGISH -> score * luxPriorityBoost
            SuggestionLanguage.FRENCH -> score * frenchPenalty
        }
    }
}