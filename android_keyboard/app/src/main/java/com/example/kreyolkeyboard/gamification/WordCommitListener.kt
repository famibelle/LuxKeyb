package com.example.kreyolkeyboard.gamification

/**
 * Interface pour notifier quand un mot est committé (validé) par l'utilisateur
 * 
 * Un mot est considéré comme "committé" dans les cas suivants :
 * - L'utilisateur tape un séparateur (espace, ponctuation, entrée)
 * - L'utilisateur sélectionne une suggestion
 * 
 * Cette interface permet de tracker l'utilisation des mots créoles
 * tout en respectant la vie privée (seuls les mots du dictionnaire sont trackés)
 */
interface WordCommitListener {
    /**
     * Appelé quand un mot est validé par l'utilisateur
     * 
     * @param word Le mot qui a été committé
     */
    fun onWordCommitted(word: String)
}
