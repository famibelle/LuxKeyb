package com.example.kreyolkeyboard.carnet

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.View

/**
 * L'écran ne tourne plus tant qu'une carte est montrée.
 *
 * La pochette, la carte ouverte, l'éventail d'un casier, la révision et la
 * cession sont des vues posées par-dessus l'écran. Une rotation recrée
 * l'activité et elles disparaissaient avec elle : la pochette et ses cartes,
 * la carte qu'on lisait, la carte de révision qu'on venait de retourner
 * (2026-10-12). Et c'est en inclinant le téléphone pour faire jouer une carte
 * qu'on le fait tourner : on la perdait au moment même où on la regardait.
 *
 * Le verrou ne suffit plus sur grand écran : à partir d'Android 16, une
 * tablette ou un pliable ouvert ignore toute demande d'orientation, et la
 * carte disparaissait de nouveau. `SettingsActivity` déclare donc elle-même
 * les changements de taille et d'orientation, et se recrée à la main
 * ([differer]) sauf quand une carte est tenue : la carte se remet alors à la
 * nouvelle taille, et la recréation attend qu'elle soit refermée.
 */
internal object FigerOrientation {

    /**
     * Fige l'orientation de l'activité qui porte [vue], et la rend quand [vue]
     * quitte la fenêtre. Rendue au détachement et pas à une fermeture : les
     * écrans retirent aussi ces vues eux-mêmes (nouvelle partie, écran quitté),
     * et l'appli resterait figée.
     */
    fun tantQue(vue: View) {
        val activite = activiteDe(vue.context) ?: return
        val avant = activite.requestedOrientation
        activite.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        vue.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                // Deux vues figées l'une sur l'autre (la carte ouverte depuis
                // l'éventail) : la dernière refermée rend la main, pas la
                // première, qui rendrait un verrou que l'autre tient encore.
                if (--tenues == 0) {
                    activite.requestedOrientation = rendue
                    if (rotationDifferee) {
                        rotationDifferee = false
                        // Après le détachement, pas pendant : on ne recrée pas
                        // une activité au milieu d'un retrait de vue. Une
                        // activité qui se ferme, elle, n'a rien à rattraper.
                        activite.window.decorView.post {
                            if (!activite.isFinishing && !activite.isDestroyed) activite.recreate()
                        }
                    }
                }
            }
        })
        if (tenues++ == 0) rendue = avant
    }

    /**
     * Sur téléphone, l'appli ne se tient qu'en portrait (décision du
     * propriétaire, 2026-10-13). À l'italienne, l'en-tête, les onglets et le
     * bandeau d'un jeu prenaient presque toute la hauteur : la révision n'y
     * montrait plus qu'une icône à la place de la carte. Une tablette garde
     * les deux sens, elle a la place.
     *
     * À appeler avant `super.onCreate` : l'activité ouverte à l'italienne n'est
     * alors recréée qu'une fois, avant d'avoir rien montré.
     */
    fun portraitSurTelephone(activite: Activity) {
        if (activite.resources.configuration.smallestScreenWidthDp < 600) {
            activite.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        }
    }

    /**
     * Vrai si une carte est tenue : la recréation qu'appelle ce changement de
     * configuration est alors reportée à sa fermeture. Faux sinon, et
     * l'activité se recrée tout de suite, comme si elle n'avait rien déclaré.
     */
    fun differer(): Boolean {
        if (tenues == 0) return false
        rotationDifferee = true
        return true
    }

    private var tenues = 0
    private var rotationDifferee = false
    private var rendue = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    private fun activiteDe(ctx: Context): Activity? {
        var c: Context? = ctx
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}
