package com.example.kreyolkeyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import com.example.kreyolkeyboard.MiniClavier.Companion.entre
import com.example.kreyolkeyboard.MiniClavier.Companion.lisse
import kotlin.math.min

/**
 * Les gestes du clavier, mimés sur un clavier miniature : l'illustration
 * de la bulle « Le clavier en trois gestes » de la visite guidée.
 *
 * Même principe que l'échange d'une carte ([carnet.AnimationEchange], 37.7.7) :
 * la bulle décrit des gestes que rien ne montre à l'écran, puisque la visite
 * se joue dans l'application et non au-dessus du clavier. La boucle suit deux
 * des trois temps numérotés du texte, et le numéro en cours s'allume dessous :
 * 1. « Moi » est tapé, le doigt touche « Moien » : le mot s'écrit en entier ;
 *    puis il tape « wéi », « é » compris, touche directe de la disposition ;
 * 3. le doigt glisse sur la barre d'espace, le curseur recule dans la phrase.
 *
 * L'appui long (2) n'est pas mimé : tout le monde le connaît d'instinct
 * (Medhi, 2026-10-21), le texte suffit. Les suggestions sont celles que le
 * clavier propose vraiment pour « Moi ».
 *
 * Animations désactivées dans le système : le doigt posé sur la barre
 * d'espace, curseur à mi-chemin. Muette pour l'accessibilité, le texte de la
 * bulle dit déjà tout.
 */
class AnimationGestes(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity
    private val clavier = MiniClavier(resources)
    private var t = 0f

    private val animateur = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = DUREE_MS
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
    }

    private val champ = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val champBord = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0B0B0")
        style = Paint.Style.STROKE
        strokeWidth = 1f * d
    }
    private val texte = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A1A")
        textSize = 13f * sp
    }
    private val curseur = Paint().apply {
        color = clavier.bleu
        strokeWidth = 1.5f * d
    }
    private val barre = Paint().apply { color = Color.parseColor("#ECECEC") }
    private val puce = Paint(Paint.ANTI_ALIAS_FLAG)
    private val encrePuce = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f * sp
        textAlign = Paint.Align.CENTER
    }
    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        if (!ValueAnimator.areAnimatorsEnabled()) t = FIGEE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (HAUTEUR_DP * d).toInt())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (ValueAnimator.areAnimatorsEnabled()) animateur.start()
    }

    override fun onDetachedFromWindow() {
        animateur.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val kw = min(w - 12f * d, 300f * d)
        val gauche = (w - kw) / 2
        val zoneChamp = RectF(gauche, 2f * d, gauche + kw, 26f * d)
        val zoneBarre = RectF(gauche - 4f * d, zoneChamp.bottom + 8f * d, gauche + kw + 4f * d, zoneChamp.bottom + 30f * d)
        val zoneClavier = RectF(gauche, zoneBarre.bottom + 4f * d, gauche + kw, zoneBarre.bottom + 4f * d + clavier.hauteur())
        val touches = clavier.placer(zoneClavier)
        fun cle(c: String) = touches.flatten().first { it.first == c }.second

        // Les trois suggestions de « Moi », la première touchée.
        val largeurPuce = (zoneBarre.width() - 16f * d) / 3
        val puces = SUGGESTIONS.indices.map { i ->
            val x = zoneBarre.left + 4f * d + i * (largeurPuce + 4f * d)
            RectF(x, zoneBarre.top + 3f * d, x + largeurPuce, zoneBarre.bottom - 3f * d)
        }
        val espace = cle(" ")

        // Le doigt, d'un point au suivant. Chaque point : instant, position, appui.
        val p0 = Point(0f, cle("h").centerX(), cle("h").centerY(), false)
        val trajet = listOf(
            p0,
            Point(0.04f, p0.x, p0.y, false),
            Point(0.12f, puces[0].centerX(), puces[0].centerY(), false),
            Point(0.12f, puces[0].centerX(), puces[0].centerY(), true),
            Point(0.16f, puces[0].centerX(), puces[0].centerY(), false),
            Point(0.30f, puces[0].centerX(), puces[0].centerY(), false),
            Point(0.36f, cle("w").centerX(), cle("w").centerY(), true),
            Point(0.38f, cle("w").centerX(), cle("w").centerY(), false),
            Point(0.42f, cle("é").centerX(), cle("é").centerY(), true),
            Point(0.44f, cle("é").centerX(), cle("é").centerY(), false),
            Point(0.48f, cle("i").centerX(), cle("i").centerY(), true),
            Point(0.50f, cle("i").centerX(), cle("i").centerY(), false),
            Point(0.62f, espace.right - espace.width() * 0.15f, espace.centerY(), true),
            Point(T_GLISSE, espace.right - espace.width() * 0.15f, espace.centerY(), true),
            Point(T_LACHE, espace.right - espace.width() * 0.75f, espace.centerY(), true),
            Point(T_LACHE + 0.01f, espace.right - espace.width() * 0.75f, espace.centerY(), false),
            Point(1f, espace.right - espace.width() * 0.75f, espace.centerY(), false)
        )
        val doigt = position(trajet)

        val phrase = when {
            t < 0.16f -> "Moi"
            t < 0.38f -> "Moien "
            t < 0.44f -> "Moien w"
            t < 0.50f -> "Moien wé"
            else -> "Moien wéi"
        }
        // Le curseur recule d'une lettre à la fois pendant le glissement.
        val recul = (MiniClavier.fondu(t, T_GLISSE, T_LACHE - T_GLISSE) * 4).toInt()
        val avantCurseur = phrase.substring(0, phrase.length - recul)

        canvas.drawRoundRect(zoneChamp, 4f * d, 4f * d, champ)
        canvas.drawRoundRect(zoneChamp, 4f * d, 4f * d, champBord)
        val base = zoneChamp.centerY() - (texte.descent() + texte.ascent()) / 2
        canvas.drawText(phrase, zoneChamp.left + 8f * d, base, texte)
        val xc = zoneChamp.left + 8f * d + texte.measureText(avantCurseur) + 1f * d
        canvas.drawLine(xc, zoneChamp.top + 5f * d, xc, zoneChamp.bottom - 5f * d, curseur)

        canvas.drawRect(zoneBarre, barre)
        if (t < 0.16f) {
            val touchee = t in 0.12f..0.16f
            SUGGESTIONS.forEachIndexed { i, mot ->
                puce.color = if (touchee && i == 0) Color.parseColor("#BE2130") else clavier.rouge
                canvas.drawRoundRect(puces[i], 8f * d, 8f * d, puce)
                canvas.drawText(mot, puces[i].centerX(), puces[i].centerY() - (encrePuce.descent() + encrePuce.ascent()) / 2, encrePuce)
            }
        }

        val appuyee = if (!doigt.appui) null else touches.flatten().firstOrNull { it.second.contains(doigt.x, doigt.y) }?.first
        clavier.dessiner(canvas, zoneClavier, touches, appuyee)

        if (t >= 0.02f) clavier.doigt(canvas, doigt.x, doigt.y, doigt.appui)

        clavier.pastilles(canvas, w / 2, zoneClavier.bottom + 18f * d, listOf(1, 3), if (t < 0.55f) 1 else 3)
    }

    private class Point(val t: Float, val x: Float, val y: Float, val appui: Boolean)

    /**
     * Où est le doigt à l'instant [t] : entre deux points, il glisse. Un point
     * qui appuie appuie jusqu'au suivant, ce qui fait du glissement sur la
     * barre d'espace un seul geste.
     */
    private fun position(trajet: List<Point>): Point {
        val i = trajet.indexOfFirst { it.t > t }
        if (i <= 0) return trajet.last()
        val a = trajet[i - 1]
        val b = trajet[i]
        val p = lisse(((t - a.t) / (b.t - a.t)).coerceIn(0f, 1f))
        return Point(t, entre(a.x, b.x, p), entre(a.y, b.y, p), a.appui)
    }

    private companion object {
        const val DUREE_MS = 10_000L
        const val HAUTEUR_DP = 176f

        val SUGGESTIONS = listOf("Moien", "Moie", "Moies")

        const val T_GLISSE = 0.65f
        const val T_LACHE = 0.84f

        /** L'image montrée sans animation : le doigt sur la barre d'espace, curseur à mi-chemin. */
        const val FIGEE = 0.76f
    }
}
