package com.example.kreyolkeyboard

/**
 * 🎯 AccentTolerantMatcher - Recherche insensible aux accents pour le créole guadeloupéen
 * 
 * Permet aux utilisateurs de taper "kre" et obtenir des suggestions pour "kréyòl"
 * Essentiel pour une expérience fluide du clavier créole sans chercher les accents
 * 
 * Fonctionnalités:
 * - Normalisation des accents créoles (à, é, è, ò, etc.)
 * - Recherche flexible dans le dictionnaire
 * - Support des caractères spéciaux guadeloupéens
 * 
 * @author Médhi Famibelle - Potomitan™
 */
object AccentTolerantMatcher {

    // Table char → char précalculée : normalize() est appelé sur le chemin chaud
    // (chaque frappe), toute compilation de Regex ici est interdite
    private val NORMALIZATION_MAP: Map<Char, Char> = buildMap {
        "àáâäãåāăą".forEach { put(it, 'a') }
        "èéêëēėęě".forEach { put(it, 'e') }
        "ìíîïīįĩ".forEach { put(it, 'i') }
        "òóôöõøōőœ".forEach { put(it, 'o') }
        "ùúûüūůũűų".forEach { put(it, 'u') }
        "ýÿŷ".forEach { put(it, 'y') }
        put('ç', 'c')
        put('ñ', 'n')
    }

    /**
     * Normalise une chaîne en supprimant tous les accents
     * Optimisé pour le créole guadeloupéen
     */
    fun normalize(text: String): String {
        if (text.isEmpty()) return text

        // Une forme déjà pliée (la plupart) ressort telle quelle, sans copie.
        val n = text.length
        var i = 0
        while (i < n) {
            val c = text[i]
            if (c == 'ß' || plier(c) != c) break
            i++
        }
        if (i == n) return text

        val result = StringBuilder(n + 1)
        result.append(text, 0, i)
        while (i < n) {
            val lower = text[i].lowercaseChar()
            if (lower == 'ß') result.append("ss") else result.append(plier(lower))
            i++
        }
        return result.toString()
    }

    /**
     * La même table que [NORMALIZATION_MAP], indexée par le code du
     * caractère : la Map emballait chaque caractère dans un objet, et le
     * carnet plie les 88 852 formes glosées à chaque ouverture (5,5 s sur un
     * Galaxy A21s, 2026-10-12). Toutes ses clés sont sous U+0180.
     */
    private val TABLE = CharArray(0x180) { it.toChar() }.also { t ->
        NORMALIZATION_MAP.forEach { (de, vers) -> t[de.code] = vers }
    }

    private fun plier(c: Char): Char {
        val lower = c.lowercaseChar()
        return if (lower.code < 0x180) TABLE[lower.code] else lower
    }

    /**
     * Vérifie si deux mots correspondent après normalisation des accents
     */
    fun matches(input: String, target: String): Boolean {
        return normalize(input) == normalize(target)
    }
    
    /**
     * Vérifie si un mot du dictionnaire commence par l'input normalisé
     */
    fun startsWith(input: String, dictionaryWord: String): Boolean {
        val normalizedInput = normalize(input)
        val normalizedWord = normalize(dictionaryWord)
        return normalizedWord.startsWith(normalizedInput)
    }
    
    /**
     * Vérifie si un mot contient des accents
     */
    fun hasAccents(word: String): Boolean {
        return word != normalize(word)
    }
}