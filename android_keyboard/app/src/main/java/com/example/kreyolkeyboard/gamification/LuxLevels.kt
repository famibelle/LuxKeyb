package com.example.kreyolkeyboard.gamification

/**
 * Les huit niveaux culturels du parcours « Mäi Lëtzebuergesch ».
 *
 * Cette logique vivait dans `SettingsActivity`, en `private`, donc hors de
 * portée du service de saisie : impossible d'y détecter un passage de niveau
 * au moment où l'utilisateur tape réellement ses mots. Elle est extraite ici
 * pour être appelable des deux côtés, et testable sans Android.
 *
 * L'échelle monte du générique vers les figures du pays, comme le faisait la
 * version créole : le Renert, le renard du roman national de Michel Rodange,
 * puis le Roude Léiw des armoiries, avant les deux rangs de maîtrise de la
 * langue. Elle remplace deux échelles qui coexistaient et se contredisaient —
 * l'une dans le code (« Aquila », « Hirsch », du latin et de l'allemand),
 * l'autre dans docs/README.md.
 *
 * Aucune dépendance au `Context` : la taille du dictionnaire est passée en
 * paramètre par l'appelant, qui seul sait comment la charger.
 */
object LuxLevels {

    data class Level(val emoji: String, val name: String) {
        /** Libellé complet tel qu'affiché dans l'onglet statistiques. */
        val label: String get() = "$emoji $name"
    }

    /** Du plus bas (index 0) au plus haut (index 7). */
    val LEVELS = listOf(
        Level("🌍", "Ufänker"),
        Level("🌱", "Klengen"),
        Level("🔥", "Fléisseg"),
        Level("💎", "Geschéit"),
        Level("🦊", "Renert"),
        Level("🦁", "Roude Léiw"),
        Level("👑", "Sproochenkënner"),
        Level("🧙", "Sproochenmeeschter")
    )

    const val MAX_INDEX = 7

    /**
     * Part du dictionnaire à découvrir pour atteindre chaque niveau.
     * Progression volontairement resserrée en bas (1,5 % suffit pour quitter
     * Ufänker) afin que les premiers paliers restent atteignables.
     */
    private val PERCENTAGES = doubleArrayOf(
        0.0,    // 0: Ufänker (démarrage)
        0.015,  // 1: Klengen (1.5% - premiers pas encourageants)
        0.05,   // 2: Fléisseg (5% - assidu)
        0.12,   // 3: Geschéit (12% - au milieu)
        0.25,   // 4: Renert (25% - quart du chemin)
        0.45,   // 5: Roude Léiw (45% - presque la moitié)
        0.70,   // 6: Sproochenkënner (70% - expert confirmé)
        1.0     // 7: Sproochenmeeschter (100% - tous les mots!)
    )

    /** Nombre de mots distincts requis pour chaque niveau. */
    fun thresholds(totalWords: Int): IntArray = IntArray(8) { index ->
        // Dernier niveau = tous les mots exactement, sans arrondi à la baisse
        if (index == MAX_INDEX) totalWords else (totalWords * PERCENTAGES[index]).toInt()
    }

    /** Index du niveau atteint avec `wordsDiscovered` mots distincts. */
    fun indexFor(wordsDiscovered: Int, totalWords: Int): Int {
        val thresholds = thresholds(totalWords)
        for (index in MAX_INDEX downTo 1) {
            if (wordsDiscovered >= thresholds[index]) return index
        }
        return 0
    }

    /** Libellé complet (« 🌱 Klengen ») du niveau atteint. */
    fun labelFor(wordsDiscovered: Int, totalWords: Int): String =
        LEVELS[indexFor(wordsDiscovered, totalWords)].label

    /**
     * Nom du niveau suivant et nombre de mots restants pour l'atteindre.
     * Au niveau maximum, renvoie « Sproochenmeeschter » et 0.
     */
    fun nextLevelInfo(wordsDiscovered: Int, totalWords: Int): Pair<String, Int> {
        val currentIndex = indexFor(wordsDiscovered, totalWords)
        if (currentIndex == MAX_INDEX) return Pair(LEVELS[MAX_INDEX].name, 0)
        val nextIndex = currentIndex + 1
        return Pair(LEVELS[nextIndex].name, thresholds(totalWords)[nextIndex] - wordsDiscovered)
    }
}
