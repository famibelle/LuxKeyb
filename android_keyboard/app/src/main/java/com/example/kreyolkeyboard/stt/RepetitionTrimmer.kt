package com.example.kreyolkeyboard.stt

/**
 * Coupe la queue répétitive que les modèles de la famille whisper produisent
 * quand le décodage boucle en fin d'énoncé.
 *
 * Mesuré sur téléphone le 29 août 2026, service LuxASR v2.3.0, sur un extrait
 * de dix secondes rejoué au haut-parleur : trois passes, trois queues
 * différentes, dont « dat ass net nëmmen » répété cinq fois. Le corps de la
 * transcription était bon (≈ 11 % de WER sur la meilleure passe) ; c'est la
 * queue qui portait le score à 103,7 %.
 *
 * Ce n'était pas un problème de silence : arrêter la capture 1,5 s après la fin
 * de la parole, au lieu de laisser tourner dix secondes de blanc, a changé la
 * forme de la queue sans la faire disparaître. Le modèle boucle sur la fin de
 * l'énoncé lui-même, et c'est un défaut connu de whisper — whisper.cpp et
 * faster-whisper filtrent tous deux en aval de leur décodeur, faute de pouvoir
 * l'empêcher.
 *
 * Le filtre s'applique au texte **entier** rendu par le service
 * (`accumulated_text`), jamais à des fragments : le client ne recolle rien, et
 * ce n'est pas ici qu'il commencerait.
 */
object RepetitionTrimmer {

    /** Longueur maximale du motif cherché, en mots. */
    private const val MAX_BLOCK = 8

    /**
     * Nombre d'occurrences à partir duquel on considère qu'il y a bouclage.
     *
     * Plus exigeant pour un motif d'un seul mot : « jo, jo, jo » est une
     * insistance parfaitement humaine, alors qu'un groupe de plusieurs mots
     * répété trois fois d'affilée ne l'est pas.
     */
    private fun seuil(longueurBloc: Int) = if (longueurBloc == 1) 4 else 3

    /**
     * Rend [texte] amputé de sa queue répétitive, ou tel quel s'il n'y en a pas.
     *
     * La première occurrence du motif est conservée : elle appartient
     * généralement à ce qui a réellement été dit, c'est la suite qui est
     * inventée.
     */
    fun trim(texte: String): String {
        val mots = decouper(texte)
        if (mots.size < 2) return texte

        for (bloc in 1..MAX_BLOCK) {
            if (mots.size < bloc * 2) break

            // Recule tant que le texte est périodique de période `bloc` : `i`
            // s'arrête sur le premier mot qui rompt la périodicité.
            var i = mots.size - 1
            while (i - bloc >= 0 && mots[i].cle == mots[i - bloc].cle) i--

            val longueurRepetee = mots.size - 1 - i
            if (longueurRepetee < bloc * (seuil(bloc) - 1)) continue

            // `i` est le dernier mot de la première occurrence du motif : tout
            // ce qui suit n'en est que la répétition. On coupe là.
            if (i >= mots.size - 1) continue
            return texte.substring(0, mots[i].fin).trimEnd()
        }
        return texte
    }

    /** Un mot du texte : sa position de fin, et sa forme comparable. */
    private class Mot(val fin: Int, val cle: String)

    /**
     * Découpe en mots comparables. La casse et la ponctuation sont ignorées
     * pour la comparaison — une boucle ne se répète pas toujours avec la même
     * virgule — mais les positions renvoyées sont celles du texte d'origine,
     * qui est seul livré à l'utilisateur.
     */
    private fun decouper(texte: String): List<Mot> {
        val mots = mutableListOf<Mot>()
        var debut = -1
        for (i in texte.indices) {
            val c = texte[i]
            val estMot = c.isLetterOrDigit() || c == '\'' || c == '’' || c == '-'
            if (estMot && debut < 0) debut = i
            if (!estMot && debut >= 0) {
                ajouter(mots, texte, debut, i); debut = -1
            }
        }
        if (debut >= 0) ajouter(mots, texte, debut, texte.length)
        return mots
    }

    private fun ajouter(mots: MutableList<Mot>, texte: String, debut: Int, fin: Int) {
        val brut = texte.substring(debut, fin)
        val cle = brut.lowercase().trim('-', '\'', '’')
        if (cle.isNotEmpty()) mots.add(Mot(fin, cle))
    }
}
