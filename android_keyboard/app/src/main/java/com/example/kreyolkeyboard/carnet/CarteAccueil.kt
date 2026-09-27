package com.example.kreyolkeyboard.carnet

import android.content.Context

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
    const val GLOSE = "bonjour"
    const val CATEGORIE = "Salutation"
    const val EXEMPLE = "Moien, wéi geet et?"
    const val TRADUCTION = "Bonjour, comment ça va ?"

    /** Verse la carte au carnet. `true` seulement si elle n'y était pas encore. */
    fun offrir(context: Context): Boolean =
        Carnet.ajouter(context, FORME, JeuCarte.ACCUEIL)

    /** Le contenu de [carte], corrigé s'il s'agit de la carte offerte. */
    fun corriger(contenu: ContenuCarte): ContenuCarte =
        if (contenu.carte.forme == FORME && JeuCarte.ACCUEIL in contenu.carte.jeux) {
            contenu.copy(
                glose = GLOSE,
                exemple = EXEMPLE,
                traductionExemple = TRADUCTION,
                categorie = CATEGORIE
            )
        } else contenu
}
