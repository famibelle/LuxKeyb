package com.example.kreyolkeyboard

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Affichage bord à bord, imposé par Android 15 aux applis qui ciblent le SDK 35.
 *
 * La fenêtre passe alors sous la barre d'état et la barre de navigation, et
 * `adjustResize` ne rétrécit plus le contenu quand le clavier s'ouvre : c'est à
 * l'appli de s'écarter. On ne passe pas par `enableEdgeToEdge()` d'androidx :
 * pour les versions antérieures il appelle `Window.setStatusBarColor` et
 * `setNavigationBarColor`, que la Play Console signale comme obsolètes (retirés
 * en 27.0.0 avec la bibliothèque Material).
 *
 * Avant Android 15 la fenêtre n'est pas bord à bord, le système a déjà écarté le
 * contenu et les encarts reçus sont nuls : ces marges ne changent rien.
 */
object BordABord {

    /**
     * [haut] reçoit la hauteur de la barre d'état en marge intérieure, pour que
     * son fond se prolonge dessous ; avec [couleurHaut], cette bande est peinte
     * de cette couleur plutôt que du fond de [haut]. [bas] reçoit la hauteur de
     * la barre de navigation, ou du clavier quand il est ouvert. [racine]
     * reçoit les encoches latérales, en paysage.
     */
    fun appliquer(racine: View, haut: View, bas: View = racine, couleurHaut: Int? = null) {
        val hautInitial = haut.paddingTop
        val basInitial = bas.paddingBottom
        val fondInitial: Drawable? = haut.background
        ViewCompat.setOnApplyWindowInsetsListener(racine) { _, insets ->
            val barres = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val clavier = insets.getInsets(WindowInsetsCompat.Type.ime())
            racine.setPadding(barres.left, racine.paddingTop, barres.right, racine.paddingBottom)
            haut.setPadding(haut.paddingLeft, hautInitial + barres.top, haut.paddingRight, haut.paddingBottom)
            if (couleurHaut != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                haut.background = if (barres.top == 0) fondInitial else
                    LayerDrawable(arrayOf(fondInitial ?: ColorDrawable(0), ColorDrawable(couleurHaut))).apply {
                        setLayerGravity(1, Gravity.TOP or Gravity.FILL_HORIZONTAL)
                        setLayerHeight(1, barres.top)
                    }
            }
            bas.setPadding(
                bas.paddingLeft, bas.paddingTop, bas.paddingRight,
                basInitial + maxOf(barres.bottom, clavier.bottom)
            )
            insets
        }
    }
}
