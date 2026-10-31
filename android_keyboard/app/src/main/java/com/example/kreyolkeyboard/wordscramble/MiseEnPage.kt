package com.example.kreyolkeyboard.wordscramble

/**
 * La mise en page d'un mot de Wuertmix, ou d'une expression des essentiels
 * (« Gudde Moien », « Wéi vill kascht dat? »).
 *
 * Une expression ne se mélange pas comme un mot : ses espaces et sa
 * ponctuation sont donnés d'avance, et chacun de ses mots commence une
 * rangée de la grille de réponse, pour qu'on la lise comme on l'écrit. Un
 * mot seul garde la grille d'avant : cinq colonnes au plus.
 *
 * Sans `Context` ni vue, pour être testé sur la JVM.
 */
object MiseEnPage {

    /**
     * Les positions données d'avance : tout ce qui n'est pas une lettre, et la
     * première et la dernière lettre dès qu'il y en a quatre. En dessous, il
     * ne resterait rien à remettre dans l'ordre.
     */
    fun fixes(mot: String): Set<Int> {
        val lettres = mot.indices.filter { mot[it].isLetter() }
        val fixes = mot.indices.filterTo(HashSet()) { !mot[it].isLetter() }
        if (lettres.size >= 4) {
            fixes += lettres.first()
            fixes += lettres.last()
        }
        return fixes
    }

    /**
     * Les cases de la grille de réponse et son nombre de colonnes. Une case
     * porte l'indice de son caractère dans [mot], ou -1 pour le blanc qui
     * termine la rangée d'un mot plus court que la plus longue.
     */
    fun cases(mot: String): Pair<List<Int>, Int> {
        if (' ' !in mot) return mot.indices.toList() to minOf(mot.length, 5).coerceAtLeast(1)
        val morceaux = mutableListOf<List<Int>>()
        var courant = mutableListOf<Int>()
        for (i in mot.indices) {
            if (mot[i] == ' ') {
                if (courant.isNotEmpty()) morceaux += courant
                courant = mutableListOf()
            } else courant += i
        }
        if (courant.isNotEmpty()) morceaux += courant
        val colonnes = morceaux.maxOf { it.size }.coerceIn(5, 10)
        val cases = morceaux.flatMap { m ->
            val reste = (colonnes - m.size % colonnes) % colonnes
            m + List(reste) { -1 }
        }
        return cases to colonnes
    }
}
