package com.example.kreyolkeyboard.carnet

/**
 * Le code-barres EAN-13 d'un paquet, tel qu'il est imprimé au dos (voir la
 * skill `imprimeur`) : treize chiffres, dont le dernier est une clé de
 * contrôle.
 *
 * Un EAN ne porte aucune carte : il se lit en librairie, il est public. Il
 * identifie seulement le paquet. Les cartes restent livrées par le code signé
 * de [CartesPapier]. Un EAN lu ici ne donne donc rien à gagner.
 *
 * Sans `Context`, pour être testé sur la JVM.
 */
object CodeBarres {

    /** Les chiffres de [texte] si c'est un EAN-13 valide, sinon `null`. */
    fun ean13(texte: String): String? {
        val t = texte.trim()
        if (t.length != 13 || !t.all { it in '0'..'9' }) return null
        var somme = 0
        for (i in 0 until 12) {
            val chiffre = t[i] - '0'
            val poids = if (i % 2 == 0) 1 else 3
            somme += chiffre * poids
        }
        val controle = (10 - somme % 10) % 10
        return if (controle == t[12] - '0') t else null
    }
}
