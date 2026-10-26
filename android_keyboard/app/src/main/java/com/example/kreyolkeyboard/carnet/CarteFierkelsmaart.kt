package com.example.kreyolkeyboard.carnet

import android.content.Context
import com.example.kreyolkeyboard.R

/**
 * La carte de la Fierkelsmaart : la seule **ultra rare** du carnet.
 *
 * Le mot n'est pas au LOD ni au corpus : il a été ajouté au dictionnaire à la
 * main (`MOTS_HORS_CORPUS` dans `Dictionnaires/LuxembourgishComplet.py`), à la
 * demande du propriétaire, le 2026-10-23, pour le stand d'avril 2027. Aucune
 * grille ne le contient : la carte ne se gagne donc pas en jouant.
 *
 * ## Ultra rare sans cinquième palier
 *
 * [Rarete] a quatre paliers et tout le carnet en dépend : le métal, l'heure du
 * paysage, les trente-six cases de la série des paysages. Un cinquième palier
 * ouvrirait neuf cases de série qu'aucune carte ne pourrait remplir. La carte
 * est donc **très rare** pour tout ce qui compte (métal d'or, nuit, irisation),
 * et ce qui la fait ultra rare tient en deux choses qu'elle est seule à avoir :
 * son dessin en couleurs ([Ornement] : `Porcelet`) et la mention « Ultra rare »
 * au bas de la carte.
 *
 * Elle se reconnaît à sa **forme**, et non à sa provenance comme la carte
 * d'accueil : une cession ne transmet que la forme, et la carte doit rester
 * ultra rare quand elle passe de main en main.
 *
 * Le dessin s'inspire du logo de la Fierkelsmaart (un porcelet devant un
 * médaillon vert couronné de laurier, un ruban doré) sans le recopier : c'est
 * le logo de l'organisateur, pas le nôtre.
 */
object CarteFierkelsmaart {

    const val FORME = "Fierkelsmaart"

    /** L'année écrite sur le ruban : une édition, comme une carte de collection. */
    const val EDITION = "2027"

    /** Phrase écrite pour la carte, pas tirée du corpus RTL : elle peut s'imprimer. */
    const val EXEMPLE = "Fierkelsmaart 2027: mir sinn dobäi!"

    /** `Maart` et `Chrëschtmaart` sont masculins au LOD. */
    val CATEGORIE = R.string.cat_nom_m

    fun est(forme: String): Boolean = forme == FORME

    /**
     * Le contenu de la carte pour qui ne la possède pas : son fichier
     * d'impression se fait avant que le moindre joueur l'ait (voir
     * [ImpressionCarte]).
     */
    fun contenu(context: Context): ContenuCarte = CarteCarnet.contenu(
        context,
        CarteMot(forme = FORME, premiereFois = 0L, rencontres = 1, numero = 0, jeux = setOf(JeuCarte.CADEAU))
    )

    /** Le contenu de [contenu], corrigé s'il s'agit de la carte de la Fierkelsmaart. */
    fun corriger(context: Context, contenu: ContenuCarte): ContenuCarte = corriger(
        contenu,
        context.getString(R.string.fierkelsmaart_glose),
        context.getString(R.string.fierkelsmaart_traduction)
    )

    /**
     * La règle seule, sans ressources : testable hors appareil.
     *
     * Le champ est la société, dont le lieu est la Schueberfouer : une foire
     * pour une foire.
     */
    fun corriger(contenu: ContenuCarte, glose: String, traduction: String): ContenuCarte =
        if (est(contenu.carte.forme) && contenu.carte.nombre == null) {
            contenu.copy(
                rarete = Rarete.TRES_RARE,
                glose = glose,
                exemple = EXEMPLE,
                traductionExemple = traduction,
                categorie = CATEGORIE,
                blason = contenu.blason.copy(champ = Champ.SOCIETE, meuble = null, nature = Nature.NOM)
            )
        } else contenu
}
