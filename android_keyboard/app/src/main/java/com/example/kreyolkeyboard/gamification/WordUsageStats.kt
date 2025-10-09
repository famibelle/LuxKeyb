package com.example.kreyolkeyboard.gamification

/**
 * Statistiques d'utilisation d'un mot du dictionnaire créole
 */
data class WordUsageStats(
    val word: String,           // Le mot du dictionnaire
    val userCount: Int,         // Nombre de fois que l'utilisateur a tapé ce mot
    val frequency: Int          // Fréquence du mot dans le corpus créole (depuis creole_dict.json)
) {
    /**
     * Le mot est considéré comme "maîtrisé" s'il a été utilisé au moins 10 fois
     */
    val isMastered: Boolean
        get() = userCount >= 10
    
    /**
     * Le mot est considéré comme "récemment découvert" s'il a été utilisé entre 1 et 3 fois
     */
    val isRecentlyDiscovered: Boolean
        get() = userCount in 1..3
}
