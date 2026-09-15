package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ScrollView
import kotlin.math.abs
import kotlin.math.sign

/**
 * Le voile d'une carte ouverte depuis l'éventail : on la chasse vers le haut ou
 * vers le bas pour revenir aux cartes, comme on repose une carte sur la pile.
 *
 * Le geste n'est pris qu'une fois franchement vertical, et seulement quand la
 * carte ne peut plus défiler dans ce sens : une glose plus haute que l'écran se
 * lit d'abord jusqu'au bout, et c'est le glissé suivant qui la renvoie. L'appui
 * simple reste un clic, qui referme aussi.
 *
 * [surEnvol] reçoit le sens du jet : -1 vers le haut, 1 vers le bas.
 */
internal class VoileGlissable(
    context: Context,
    private val surEnvol: (Int) -> Unit
) : FrameLayout(context) {

    private val seuil = ViewConfiguration.get(context).scaledTouchSlop
    private val vitesseJet = 900f * resources.displayMetrics.density
    private var x0 = 0f
    private var y0 = 0f
    private var glisse = false
    private var suivi: VelocityTracker? = null

    private val contenu: View? get() = if (childCount > 0) getChildAt(0) else null

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            x0 = e.rawX
            y0 = e.rawY
            glisse = false
            suivi?.recycle()
            suivi = VelocityTracker.obtain()
        }
        // Coordonnées brutes : la carte suit le doigt, des coordonnées locales
        // mesureraient son propre déplacement.
        val brut = MotionEvent.obtain(e).apply { setLocation(e.rawX, e.rawY) }
        suivi?.addMovement(brut)
        brut.recycle()
        return super.dispatchTouchEvent(e)
    }

    private fun devientVertical(e: MotionEvent): Boolean {
        val dx = e.rawX - x0
        val dy = e.rawY - y0
        if (abs(dy) <= seuil || abs(dy) <= 1.5f * abs(dx)) return false
        val defile = contenu as? ScrollView
        return defile?.canScrollVertically(if (dy < 0) 1 else -1) != true
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_MOVE && !glisse && devientVertical(e)) {
            glisse = true
        }
        return glisse
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!glisse && e.actionMasked == MotionEvent.ACTION_MOVE && devientVertical(e)) {
            glisse = true
            // L'appui commencé sur le voile ne doit plus devenir un clic.
            super.onTouchEvent(MotionEvent.obtain(e).apply { action = MotionEvent.ACTION_CANCEL })
        }
        if (!glisse) return super.onTouchEvent(e)

        val dy = e.rawY - y0
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> suivre(dy)
            MotionEvent.ACTION_UP -> {
                suivi?.computeCurrentVelocity(1000)
                val vy = suivi?.yVelocity ?: 0f
                val loin = abs(dy) > height * 0.18f
                val jet = abs(vy) > vitesseJet && sign(vy) == sign(dy)
                if ((loin || jet) && dy != 0f) surEnvol(sign(dy).toInt()) else rappeler()
                finir()
            }
            MotionEvent.ACTION_CANCEL -> {
                rappeler()
                finir()
            }
        }
        return true
    }

    private fun suivre(dy: Float) {
        contenu?.translationY = dy
        val part = (abs(dy) / height.coerceAtLeast(1)).coerceIn(0f, 1f)
        background?.alpha = (255 * (1f - part)).toInt()
    }

    private fun rappeler() {
        contenu?.animate()?.translationY(0f)?.setDuration(200)
            ?.setInterpolator(DecelerateInterpolator())?.start()
        background?.alpha = 255
    }

    private fun finir() {
        glisse = false
        suivi?.recycle()
        suivi = null
    }
}
