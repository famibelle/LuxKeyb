package com.example.kreyolkeyboard.gamification

/**
 * Statistiques globales du vocabulaire créole de l'utilisateur
 */
data class VocabularyStats(
    val coveragePercentage: Float,              // Pourcentage du dictionnaire utilisé (0-100)
    val wordsDiscovered: Int,                   // Nombre de mots différents utilisés au moins 1 fois
    val totalWords: Int,                        // Nombre total de mots dans le dictionnaire
    val totalUsages: Int,                       // Somme de tous les compteurs d'utilisation
    val topWords: List<WordUsageStats>,         // Top 10 des mots les plus utilisés
    val recentWords: List<String>,              // Mots récemment découverts (utilisés 1-3 fois)
    val masteredWords: Int                      // Nombre de mots maîtrisés (utilisés 10+ fois)
) {
    /**
     * Niveau de maîtrise de l'utilisateur basé sur le pourcentage de couverture
     */
    val masteryLevel: MasteryLevel
        get() = when {
            coveragePercentage >= 80 -> MasteryLevel.LEGEND
            coveragePercentage >= 60 -> MasteryLevel.MASTER
            coveragePercentage >= 40 -> MasteryLevel.EXPERT
            coveragePercentage >= 20 -> MasteryLevel.INTERMEDIATE
            coveragePercentage >= 5 -> MasteryLevel.BEGINNER
            else -> MasteryLevel.NOVICE
        }
}

/**
 * Niveaux de maîtrise du vocabulaire créole
 */
enum class MasteryLevel(val displayName: String, val emoji: String) {
    NOVICE("Novice", "🌱"),
    BEGINNER("Débutant", "🌿"),
    INTERMEDIATE("Intermédiaire", "🌳"),
    EXPERT("Expert", "🏝️"),
    MASTER("Maître", "👑"),
    LEGEND("Légende", "💎")
}
