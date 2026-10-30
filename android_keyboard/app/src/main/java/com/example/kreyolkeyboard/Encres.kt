package com.example.kreyolkeyboard

/**
 * L'orange et le vert de l'appli, dans la version qui peut porter du texte.
 *
 * L'orange `#FF8C00` (et ses voisins `#FF9800`, `#F57C00`) et le vert
 * `#4CAF50` sont trop clairs pour cela : 2,33:1 et 2,78:1 contre le blanc,
 * que le texte soit écrit dans la couleur sur fond clair ou en blanc sur un
 * aplat de la couleur. Même le seuil du texte large (3:1) n'est pas atteint ;
 * libellés d'onglets, bandeau « Tout est prêt », boutons « Valider » et
 * cases justes de Wuertriet étaient sous le plancher.
 *
 * Ces deux teintes restent dans leur famille et tiennent dans les deux sens
 * (couleur sur blanc, ou blanc sur la couleur), y compris sur les fonds pâles
 * où elles sont posées :
 *
 * | | sur blanc | sur son fond pâle |
 * |---|---|---|
 * | [ORANGE] | 5,11:1 | 4,74:1 sur `#FFF5E6` (onglet actif), 4,86:1 sur `#FFF9E6` (astuce) |
 * | [VERT] | 5,13:1 | 4,56:1 sur `#E8F5E9` (correcteur actif) |
 *
 * `#B35900`, essayé d'abord, tombait à 4,48:1 sur le fond de l'onglet actif.
 *
 * Les teintes claires gardent leur place là où rien n'est écrit : le trait de
 * l'onglet actif, la pastille de nouveau palier, les confettis, l'anneau de
 * chargement. Mesurer avec `.claude/skills/design-application/contraste.py`.
 *
 * Des chaînes, comme les littéraux qu'elles remplacent : les écrans les
 * passent à `Color.parseColor` ou à `avecOpacite`. [com.example.kreyolkeyboard.carnet.JeuCarte]
 * reprend les mêmes valeurs en entiers, pour rester lisible hors appareil.
 */
object Encres {
    const val ORANGE = "#AD5600"
    const val VERT = "#2E7D32"
}
