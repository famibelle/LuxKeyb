package com.potomitan.luxreunion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.provider.Settings
import android.view.View
import com.potomitan.luxreunion.Charte.dp

/**
 * Le micro du direct : un rond rouge, et deux halos qui s'élargissent et
 * s'effacent tant que la réunion s'enregistre. C'est le motif de la dictée du
 * clavier (AnimationDictee.kt, et les créations de la campagne). Les halos
 * respirent légèrement dans le silence et s'amplifient avec la voix : la
 * secrétaire voit d'un coup d'œil que l'application entend.
 *
 * Sans réseau, l'enregistrement continue : le micro pulse toujours, seul le
 * bandeau change. Il ne s'éteint (gris, immobile) que si le micro est en
 * panne ou que la réunion se termine. Animations du système coupées : une
 * image fixe, un halo.
 */
class MicroPulsant(context: Context) : View(context) {

    /** La réunion s'enregistre : rouge et halos. Sinon, gris et immobile. */
    var actif = true
        set(v) { if (field != v) { field = v; invalidate() } }

    /** Le niveau du son, de 0 à 1 (EtatDirect.niveau). */
    var niveau = 0f

    private var lisse = 0f
    private val debut = System.currentTimeMillis()
    private val fixe = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    private val rond = Paint(Paint.ANTI_ALIAS_FLAG)
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Charte.ROUGE }
    private val trait = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Charte.BLANC
    }
    private val plein = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Charte.BLANC }
    private val r = RectF()

    init {
        contentDescription = "Micro"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onMeasure(largeur: Int, hauteur: Int) {
        val c = context.dp(TAILLE_DP)
        setMeasuredDimension(resolveSize(c, largeur), resolveSize(c, hauteur))
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val rayon = minOf(width, height) * 0.27f

        if (actif) {
            // Le niveau monte vite et redescend doucement, comme une jauge.
            lisse = if (niveau > lisse) lisse + (niveau - lisse) * 0.5f else lisse + (niveau - lisse) * 0.12f
            val ampleur = 0.35f + 0.65f * lisse
            if (fixe) {
                halo.alpha = 80
                canvas.drawCircle(cx, cy, rayon * 1.45f, halo)
            } else {
                val cycle = ((System.currentTimeMillis() - debut) % PERIODE_MS) / PERIODE_MS.toFloat()
                for (k in 0..1) {
                    val p = (cycle + k * 0.5f) % 1f
                    halo.alpha = (120 * (1 - p) * ampleur).toInt()
                    canvas.drawCircle(cx, cy, rayon * (1f + 0.85f * p * ampleur), halo)
                }
            }
        }
        rond.color = if (actif) Charte.ROUGE else Charte.GRIS_CLAIR
        canvas.drawCircle(cx, cy, rayon, rond)

        // Le micro : la capsule, l'arceau, le pied.
        val u = rayon / 10f
        trait.strokeWidth = 1.6f * u
        r.set(cx - 2.6f * u, cy - 5.6f * u, cx + 2.6f * u, cy + 1.6f * u)
        canvas.drawRoundRect(r, 2.6f * u, 2.6f * u, plein)
        r.set(cx - 4.6f * u, cy - 3.4f * u, cx + 4.6f * u, cy + 3.8f * u)
        canvas.drawArc(r, 20f, 140f, false, trait)
        canvas.drawLine(cx, cy + 3.8f * u, cx, cy + 6.2f * u, trait)
        canvas.drawLine(cx - 2.4f * u, cy + 6.2f * u, cx + 2.4f * u, cy + 6.2f * u, trait)

        if (actif && !fixe) postInvalidateOnAnimation()
    }

    private companion object {
        const val TAILLE_DP = 52
        const val PERIODE_MS = 1_400L
    }
}
