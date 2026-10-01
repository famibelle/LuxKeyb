package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.KeyboardLayoutManager.ChampAdresse

/**
 * Disposition de la page des lettres, au choix dans les réglages (v29.5.0).
 *
 * Les deux sont en QWERTZ et non en AZERTY : c'est la disposition des claviers
 * physiques au Luxembourg et celle que partagent l'allemand et le
 * luxembourgeois écrit. L'AZERTY était un héritage créole, pas un choix
 * luxembourgeois — remplacé sans repli, l'application n'étant pas encore
 * publiée.
 *
 * Elles ne diffèrent que par les diacritiques en touches directes, comme les
 * deux claviers physiques qu'on trouve au Luxembourg. Le suisse-français,
 * clavier officiel du pays, donne « é » à droite du « l » ; le suisse
 * allemand y donne « ö » et « ä », et « ü » à droite du « p ». Des utilisateurs
 * luxembourgeois habitués au second ont demandé à le retrouver.
 *
 * Données pures, sans vue : les rangées et les largeurs se vérifient hors
 * appareil (DispositionClavierTest).
 */
enum class DispositionClavier(val cle: String, val libelle: String) {

    /**
     * Le clavier d'origine, dix touches par rangée.
     *
     * « é » occupe la case immédiatement à droite du « l », exactement là où le
     * QWERTZ suisse-français la place : c'est la diacritique n°1 du
     * luxembourgeois et cette position complète la rangée d'accueil à 10
     * touches, alignée sur les rangées 1 et 3.
     *
     * Les deux autres diacritiques porteuses gardent leur touche dédiée, autour
     * de la barre d'espace. Comptages sur le corpus brut
     * POTOMITAN/luxembourgish-corpus (158 documents, 204 366 caractères) et non
     * sur luxemburgish_dict.json, dont les fréquences sont cumulées d'une
     * régénération à l'autre :
     *   é 2596 · ë 1251 · ä 1004 | ü 155 · à 55 · ö 48 · ê 48 · è 33
     * Le décrochage après ä (4× moins fréquent que ü) est ce qui justifie trois
     * touches dédiées et pas quatre ; ü et les suivantes restent en appui long
     * sur « u », « a », « o » et « e ».
     */
    LUXEMBOURG("luxembourg", "Luxembourg (é, ä et ë en touches)") {
        override fun rangeesLettres(champ: ChampAdresse): List<Array<String>> = listOf(
            arrayOf("q", "w", "e", "r", "t", "z", "u", "i", "o", "p"),
            arrayOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "é"),
            arrayOf("⇧", "y", "x", "c", "v", "b", "n", "m", "⌫"),
            rangeeDuBas(champ, gauche = "ä", droite = "ë")
        )

        // 1,5 et non 1,25 : c'est ce qui pose la rangée 3 à exactement 10 unités
        // (1,5 + 7 lettres + 1,5), donc à la même largeur de touche que les
        // rangées 1 et 2, qui en comptent dix. La v10.11.4 les avait réduites à
        // 1,25 pour financer l'apostrophe alors ajoutée en rangée 3 ; celle-ci
        // vit désormais en rangée 4, mais la réduction était restée et laissait
        // la rangée 3 à 9,5 unités — ses lettres 5,3 % plus larges que celles
        // des rangées du dessus, soit l'inverse du désalignement qu'on voulait
        // éviter. Ces deux touches sont par ailleurs aux extrémités de la
        // rangée, là où la visée du pouce est la plus mauvaise : les garder les
        // plus larges de leur rangée sert aussi à ça.
        override val poidsShiftEtEffacement = 1.5f
    },

    /**
     * Le suisse allemand, onze touches par rangée comme Gboard en allemand :
     * « ü » à droite du « p », « ö » et « ä » à droite du « l ».
     *
     * « é » et « ë » prennent alors, autour de la barre d'espace, les places que
     * « ä » et « ë » occupent sur la disposition luxembourgeoise. Les trois
     * diacritiques les plus fréquentes du luxembourgeois (é, ë, ä) restent en
     * touches directes, « ü » et « ö » s'y ajoutent : ce clavier ne sacrifie
     * rien de luxembourgeois à l'allemand.
     */
    SUISSE_ALLEMAND("suisse_allemand", "Suisse allemand (ü, ö et ä à droite)") {
        override fun rangeesLettres(champ: ChampAdresse): List<Array<String>> = listOf(
            arrayOf("q", "w", "e", "r", "t", "z", "u", "i", "o", "p", "ü"),
            arrayOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "ö", "ä"),
            arrayOf("⇧", "y", "x", "c", "v", "b", "n", "m", "⌫"),
            rangeeDuBas(champ, gauche = "é", droite = "ë")
        )

        // 2 + 7 lettres + 2 = 11 unités, la largeur des rangées 1 et 2 : les
        // lettres des trois rangées gardent la même largeur.
        override val poidsShiftEtEffacement = 2.0f
    };

    /** Les quatre rangées de la page des lettres, la dernière selon le champ. */
    abstract fun rangeesLettres(champ: ChampAdresse): List<Array<String>>

    /** Largeur de « ⇧ » et « ⌫ », qui alignent la rangée 3 sur les deux autres. */
    protected abstract val poidsShiftEtEffacement: Float

    /** Largeur relative d'une touche dans sa rangée, une lettre valant 1. */
    fun poidsTouche(key: String): Float = when (key) {
        " " -> 4.0f      // Barre d'espace plus large
        "⇧", "⌫" -> poidsShiftEtEffacement
        else -> 1.0f     // Touches normales
    }

    /**
     * Les caractères que cette disposition donne en un seul appui. Les aperçus
     * d'appui long dans les coins des touches les écartent : annoncer « ü »
     * sous le « u » ne sert à rien quand il a sa propre touche.
     */
    val touchesDirectes: Set<String> by lazy {
        rangeesLettres(ChampAdresse.AUCUN).flatMap { it.asList() }.toSet()
    }

    companion object {
        val DEFAUT = LUXEMBOURG

        /** Tolérante : une clé inconnue retombe sur le défaut plutôt que de jeter. */
        fun depuisCle(cle: String?): DispositionClavier =
            entries.firstOrNull { it.cle == cle } ?: DEFAUT

        /**
         * Rangée de la barre d'espace, commune aux deux dispositions à ses deux
         * diacritiques près.
         *
         * L'apostrophe a sa touche dédiée, que réclamaient les retours
         * utilisateurs, et le corpus le confirme : 649 occurrences (469 en ’
         * typographique, 180 en ' ASCII), soit plus que ü et 4,5× le trait
         * d'union (143). L'élision est structurelle en luxembourgeois — d'Land,
         * s'Kanner, hunn's. Attention, luxemburgish_dict.json l'affiche à zéro
         * et ce zéro ne veut rien dire : le tokenizer de LuxembourgishComplet.py
         * coupe les mots dessus. Le trait d'union reste donc en appui long sur
         * « . » — 143 occurrences ici contre 21,7 % des mots en créole, où il
         * avait une touche à lui.
         *
         * v29.3.1 : dans une adresse, la virgule et l'apostrophe ne servent à
         * rien et « @ », « / », « .lu » obligeaient à passer par la page 123.
         * Ils prennent leurs places, sans toucher à la largeur de rien.
         */
        private fun rangeeDuBas(champ: ChampAdresse, gauche: String, droite: String): Array<String> =
            when (champ) {
                ChampAdresse.EMAIL -> arrayOf("123", "@", gauche, " ", droite, ".lu", ".", "EMOJI", "⏎")
                ChampAdresse.WEB -> arrayOf("123", "/", gauche, " ", droite, ".lu", ".", "EMOJI", "⏎")
                ChampAdresse.AUCUN -> arrayOf("123", ",", gauche, " ", droite, "'", ".", "EMOJI", "⏎")
            }
    }
}
