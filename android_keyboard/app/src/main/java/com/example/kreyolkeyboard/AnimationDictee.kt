package com.example.kreyolkeyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.example.kreyolkeyboard.MiniClavier.Companion.entre
import com.example.kreyolkeyboard.MiniClavier.Companion.fondu
import com.example.kreyolkeyboard.MiniClavier.Companion.lisse
import kotlin.math.min

/**
 * La dictée, mimée sur le haut d'un clavier miniature : l'illustration de la
 * bulle « La dictée » de la visite guidée.
 *
 * Même principe que [AnimationGestes] : le doigt touche le micro, qui passe au
 * rouge et s'entoure de son anneau et de halos (le motif des créations de la
 * dictée), le bandeau de la dictée prend la place des suggestions, et la
 * phrase s'écrit mot à mot dans le champ. Le doigt touche de nouveau le micro,
 * qui s'éteint. Le bandeau est le vrai, `stt_online_listening` : son globe
 * dit, sans un mot de plus, que la voix part sur Internet.
 *
 * Animations désactivées dans le système : le micro qui écoute, la phrase à
 * moitié écrite. Muette pour l'accessibilité, le texte de la bulle dit déjà tout.
 */
class AnimationDictee(context: Context) : View(context) {

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
    private val bandeau = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A1A")
        textSize = 11f * sp
    }
    private val anneau = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = clavier.rouge
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
        strokeCap = Paint.Cap.ROUND
    }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = clavier.rouge }
    private val micro = ContextCompat.getDrawable(context, R.drawable.ic_mic)!!.mutate()
    private val ecoute = context.getString(R.string.stt_online_listening)
    private val r = RectF()

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
        // Plus de marge que les gestes : les halos du micro débordent du bandeau.
        val kw = min(w - 40f * d, 300f * d)
        val gauche = (w - kw) / 2
        val zoneChamp = RectF(gauche, 2f * d, gauche + kw, 26f * d)
        val zoneBarre = RectF(gauche - 4f * d, zoneChamp.bottom + 12f * d, gauche + kw + 4f * d, zoneChamp.bottom + 40f * d)
        val zoneClavier = RectF(gauche, zoneBarre.bottom + 4f * d, gauche + kw, zoneBarre.bottom + 4f * d + clavier.hauteur())
        val touches = clavier.placer(zoneClavier)
        val cote = zoneBarre.height()
        val bouton = RectF(zoneBarre.right - cote, zoneBarre.top, zoneBarre.right, zoneBarre.bottom)
        val ecouteEnCours = t >= T_DEBUT && t < T_FIN

        // La phrase, mot à mot pendant l'écoute ; entière ensuite.
        val ecrits = if (t < T_DEBUT) 0 else MOTS_A.count { t >= it }
        val phrase = PHRASE.take(ecrits).joinToString(" ")
        canvas.drawRoundRect(zoneChamp, 4f * d, 4f * d, champ)
        canvas.drawRoundRect(zoneChamp, 4f * d, 4f * d, champBord)
        val base = zoneChamp.centerY() - (texte.descent() + texte.ascent()) / 2
        canvas.drawText(phrase, zoneChamp.left + 8f * d, base, texte)
        val xc = zoneChamp.left + 8f * d + texte.measureText(phrase) + 2f * d
        canvas.drawLine(xc, zoneChamp.top + 5f * d, xc, zoneChamp.bottom - 5f * d, curseur)

        // Les touches dessous, estompées : ce qui compte ici est au-dessus.
        canvas.save()
        canvas.clipRect(zoneClavier.left - 4f * d, zoneClavier.top - 4f * d, zoneClavier.right + 4f * d,
            zoneClavier.top + clavier.hauteur(2) + 1f * d)
        clavier.dessiner(canvas, zoneClavier, touches, alpha = 110)
        canvas.restore()

        canvas.drawRect(zoneBarre, barre)
        if (ecouteEnCours) {
            canvas.drawText(ecoute, zoneBarre.left + 10f * d,
                zoneBarre.centerY() - (bandeau.descent() + bandeau.ascent()) / 2, bandeau)
            // Deux halos qui s'élargissent et s'effacent, décalés d'une demi-période.
            val cycle = ((t - T_DEBUT) * DUREE_MS / 1400f) % 1f
            for (k in 0..1) {
                val p = (cycle + k * 0.5f) % 1f
                halo.alpha = (70 * (1 - p)).toInt()
                canvas.drawCircle(bouton.centerX(), bouton.centerY(), cote * (0.45f + 0.35f * p), halo)
            }
            // L'anneau qui tourne tant que le micro écoute (MicRingDrawable).
            val rayon = cote / 2 - 2f * d
            r.set(bouton.centerX() - rayon, bouton.centerY() - rayon, bouton.centerX() + rayon, bouton.centerY() + rayon)
            canvas.drawArc(r, ((t - T_DEBUT) * DUREE_MS / 1000f * 360f) % 360f, 100f, false, anneau)
        }
        val marge = (cote * 0.22f).toInt()
        micro.setBounds(bouton.left.toInt() + marge, bouton.top.toInt() + marge, bouton.right.toInt() - marge, bouton.bottom.toInt() - marge)
        micro.setTint(if (ecouteEnCours) clavier.rouge else Color.parseColor("#6B6B6B"))
        micro.alpha = if (ecouteEnCours) 255 else 166
        micro.draw(canvas)

        // Le doigt : vient toucher le micro, s'en va pendant qu'on parle, revient l'arrêter.
        val repos = touches[1].first { it.first == "h" }.second
        val (x, y, visible) = when {
            t < 0.02f -> Triple(repos.centerX(), repos.centerY(), false)
            t < T_DEBUT - 0.03f -> {
                val p = lisse(fondu(t, 0.02f, T_DEBUT - 0.05f))
                Triple(entre(repos.centerX(), bouton.centerX(), p), entre(repos.centerY(), bouton.centerY(), p), true)
            }
            t < T_DEBUT + 0.06f -> Triple(bouton.centerX(), bouton.centerY(), true)
            t < T_FIN - 0.10f -> Triple(0f, 0f, false)
            t < T_FIN + 0.06f -> {
                val p = lisse(fondu(t, T_FIN - 0.10f, 0.07f))
                Triple(entre(repos.centerX(), bouton.centerX(), p), entre(repos.centerY(), bouton.centerY(), p), true)
            }
            else -> Triple(0f, 0f, false)
        }
        val appui = (t >= T_DEBUT - 0.03f && t < T_DEBUT) || (t >= T_FIN - 0.03f && t < T_FIN)
        if (visible) clavier.doigt(canvas, x, y, appui)
    }

    private companion object {
        const val DUREE_MS = 9_000L
        const val HAUTEUR_DP = 120f

        /** Ce qui est dicté, et l'instant où chaque mot arrive. */
        val PHRASE = listOf("Moien,", "wéi", "geet", "et?")
        val MOTS_A = listOf(0.24f, 0.36f, 0.48f, 0.60f)

        /** Le micro touché, puis touché de nouveau. */
        const val T_DEBUT = 0.12f
        const val T_FIN = 0.78f

        /** L'image montrée sans animation : le micro qui écoute, la phrase à moitié écrite. */
        const val FIGEE = 0.52f
    }
}
