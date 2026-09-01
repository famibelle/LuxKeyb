package com.example.kreyolkeyboard

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * Anneau tournant dessiné derrière le bouton micro pendant la dictée.
 *
 * Il répond au même besoin que le témoin posé dans le champ de saisie pendant
 * la transcription, à l'autre bout de la dictée : dire que quelque chose est en
 * cours. Le micro pulsait déjà au rythme de la voix, mais c'est un retour qui
 * disparaît précisément quand on doute — entre deux phrases, dans une pièce
 * calme, quand on hésite. L'anneau, lui, tourne tant que le micro écoute.
 *
 * Les deux animations sont volontairement de la même famille — un arc qui fait
 * le tour — pour que l'utilisateur lise « ça enregistre » puis « ça
 * transcrit » comme deux temps d'une même chose, et non comme deux états sans
 * rapport.
 *
 * Le fond est un anneau complet très atténué : sans lui, l'arc semble flotter
 * et sa position de départ paraît arbitraire.
 */
class MicRingDrawable(
    private var couleur: Int,
    private val epaisseurPx: Float
) : Drawable() {

    /** Position de la tête de l'arc, en degrés. */
    var angle = 0f
        set(value) {
            field = value
            invalidateSelf()
        }

    private val trait = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = epaisseurPx
    }
    private val cadre = RectF()

    fun teinte(c: Int) {
        couleur = c
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        // L'anneau est inscrit dans le carré du bouton, retiré d'une demi
        // épaisseur pour que le trait ne soit pas rogné par les bords.
        val cote = minOf(b.width(), b.height()) - epaisseurPx
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        cadre.set(cx - cote / 2f, cy - cote / 2f, cx + cote / 2f, cy + cote / 2f)

        trait.color = couleur
        trait.alpha = ALPHA_FOND
        canvas.drawArc(cadre, 0f, 360f, false, trait)

        trait.alpha = 255
        canvas.drawArc(cadre, angle, ARC_DEGRES, false, trait)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("imposé par Drawable", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    companion object {
        /** Longueur de l'arc vif. Assez court pour qu'on voie qu'il tourne. */
        const val ARC_DEGRES = 100f
        private const val ALPHA_FOND = 45
    }
}
