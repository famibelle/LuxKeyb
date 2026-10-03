package com.example.kreyolkeyboard.carnet

import android.content.Context
import com.example.kreyolkeyboard.R

/**
 * La carte offerte à la fin de l'installation : « Moien ».
 *
 * C'est une vraie carte du carnet, qui entre dans la révision comme les autres,
 * mais son sens est écrit ici et non lu au LOD : l'article `Moien` y est glosé
 * « matin » (`de Moien`), alors que le mot qu'on offre est le bonjour. Une carte
 * de bienvenue qui dit « matin » serait un contresens.
 *
 * Sa rareté reste celle de son rang de fréquence (210, commune) : la carte n'a
 * pas à se faire passer pour ce qu'elle n'est pas.
 */
object CarteAccueil {

    const val FORME = "Moien"
    val CATEGORIE = R.string.cat_salutation
    const val EXEMPLE = "Moien, wéi geet et?"

    /** Verse la carte au carnet. `true` seulement si elle n'y était pas encore. */
    fun offrir(context: Context): Boolean =
        Carnet.ajouter(context, FORME, JeuCarte.ACCUEIL)

    /**
     * Le contenu de [carte], corrigé s'il s'agit de la carte offerte. Son sens
     * et la traduction de sa phrase sont dans la langue de l'interface, comme
     * les gloses du LOD des autres cartes.
     */
    fun corriger(context: Context, contenu: ContenuCarte): ContenuCarte = corriger(
        contenu,
        context.getString(R.string.accueil_glose),
        context.getString(R.string.accueil_traduction)
    )

    /** La règle seule, sans ressources : testable hors appareil. */
    fun corriger(contenu: ContenuCarte, glose: String, traduction: String): ContenuCarte =
        if (contenu.carte.forme == FORME && JeuCarte.ACCUEIL in contenu.carte.jeux) {
            contenu.copy(
                glose = glose,
                exemple = EXEMPLE,
                traductionExemple = traduction,
                categorie = CATEGORIE
            )
        } else contenu
}
