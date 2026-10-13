package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.KeyboardLayoutManager.ChampAdresse

/**
 * Quelles touches se touchent sur l'écran, dans une disposition donnée.
 *
 * Sur un téléphone, la faute de frappe la plus courante est le doigt qui tombe
 * sur la touche d'à côté. La distance d'édition seule ne le sait pas : pour
 * « haud », « Haut » et « Haus » sont tous deux à une lettre, et le plus
 * fréquent passait devant, alors que le s touche le d et que le t en est loin.
 * Une correction qui ne fait que remplacer des lettres par leurs voisines
 * reçoit donc un bonus dans le classement (voir
 * [SuggestionEngine.calculateDictionaryScore]).
 *
 * Les voisines sont calculées depuis les rangées de [DispositionClavier],
 * jamais écrites à la main : une disposition modifiée ou ajoutée les
 * recalcule d'elle-même. Les accents sont repliés
 * ([AccentTolerantMatcher.normalize]) des deux côtés, si bien qu'une lettre
 * dont seul l'accent diffère n'est ni pénalisée ni récompensée ici : c'est le
 * travail du passage normalisé.
 */
class VoisinsClavier private constructor(private val paires: Set<Int>) {

    fun sontVoisines(a: Char, b: Char): Boolean = cle(a, b) in paires

    /**
     * `true` quand [mot] ne diffère de [saisie] que par des lettres remplacées
     * par une voisine, à la même place, et qu'il en diffère au moins par une.
     */
    fun parVoisines(saisie: String, mot: String): Boolean {
        if (saisie.length != mot.length) return false
        var differences = 0
        for (i in saisie.indices) {
            val a = replie(saisie[i])
            val b = replie(mot[i])
            if (a == b) continue
            if (!sontVoisines(a, b)) return false
            differences++
        }
        return differences > 0
    }

    companion object {
        private val parDisposition = HashMap<DispositionClavier, VoisinsClavier>()

        @Synchronized
        fun de(disposition: DispositionClavier): VoisinsClavier =
            parDisposition.getOrPut(disposition) { calculer(disposition) }

        private fun cle(a: Char, b: Char): Int = (a.code shl 16) or b.code

        private fun replie(c: Char): Char {
            val r = AccentTolerantMatcher.normalize(c.toString())
            return if (r.length == 1) r[0] else c.lowercaseChar()
        }

        /**
         * Centre de chaque lettre en largeurs de touche, ligne par ligne. Les
         * rangées n'ont pas toutes le même nombre d'unités (la rangée du bas en
         * compte une de plus) mais chacune occupe toute la largeur de l'écran :
         * l'abscisse est donc rapportée à la largeur de sa rangée, puis
         * exprimée dans l'unité de la première.
         */
        private fun calculer(disposition: DispositionClavier): VoisinsClavier {
            val rangees = disposition.rangeesLettres(ChampAdresse.AUCUN)
            val unite = rangees.first().sumOf { disposition.poidsTouche(it).toDouble() }
            data class Touche(val lettre: Char, val x: Double, val rangee: Int)
            val touches = ArrayList<Touche>()
            rangees.forEachIndexed { r, rangee ->
                val total = rangee.sumOf { disposition.poidsTouche(it).toDouble() }
                var gauche = 0.0
                for (touche in rangee) {
                    val poids = disposition.poidsTouche(touche).toDouble()
                    // Les touches accentuées (ä, ö, ü, é, ë) sont laissées de
                    // côté : repliées, elles prêteraient leurs voisines à la
                    // lettre de base, et « a » deviendrait voisin du « l ».
                    val lettre = touche.singleOrNull()
                    if (lettre != null && lettre.isLetter() && replie(lettre) == lettre.lowercaseChar()) {
                        val centre = (gauche + poids / 2) / total * unite
                        touches.add(Touche(touche[0].lowercaseChar(), centre, r))
                    }
                    gauche += poids
                }
            }
            val paires = HashSet<Int>()
            for (a in touches) for (b in touches) {
                if (a === b || kotlin.math.abs(a.rangee - b.rangee) > 1) continue
                // Une largeur de touche, plus une marge pour les rangées dont
                // les touches ne tombent pas pile l'une sous l'autre.
                if (kotlin.math.abs(a.x - b.x) > 1.05) continue
                val ra = replie(a.lettre)
                val rb = replie(b.lettre)
                if (ra != rb) paires.add(cle(ra, rb))
            }
            return VoisinsClavier(paires)
        }
    }
}
