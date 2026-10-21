package com.example.kreyolkeyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.view.animation.LinearInterpolator
import com.example.kreyolkeyboard.MiniClavier.Companion.entre
import com.example.kreyolkeyboard.MiniClavier.Companion.fondu
import com.example.kreyolkeyboard.MiniClavier.Companion.lisse
import kotlin.math.min

/**
 * Le Wierderbuch en miniature, cherché dans les deux sens : l'illustration de
 * la bulle « Wierderbuch » de la visite guidée (37.7.9).
 *
 * Même famille que [AnimationGestes] et [AnimationDictee]. La boucle tape le
 * sens de « Kaz » dans la langue de l'interface (« chat » en français,
 * « Katze » en allemand), le doigt touche « Kaz » et sa fiche monte, avec sa
 * phrase d'exemple ; la fiche redescend, et « Kaz » tapé à son tour retrouve
 * son sens. Le texte de la bulle dit les deux sens et l'exemple : l'image les
 * montre.
 *
 * **Rien n'est écrit en dur** : les lignes, la glose, la phrase et sa
 * traduction viennent de [TranslationDictionary], chargé hors du fil
 * principal, exactement comme l'onglet les afficherait. « Kaz » plutôt que
 * « Haus » parce que sa glose allemande, « Katze », ne se confond pas avec le
 * mot luxembourgeois. Tant que le dictionnaire charge, le champ reste vide :
 * le mot du jour de l'accueil le charge dès l'ouverture de l'application, et
 * il est prêt bien avant qu'on arrive à cette bulle.
 *
 * Animations désactivées dans le système : la fiche ouverte. Muette pour
 * l'accessibilité, le texte de la bulle dit déjà tout.
 */
class AnimationDictionnaire(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity
    private val clavier = MiniClavier(resources)
    private var t = 0f

    /** Ce que la boucle montre, lu dans le dictionnaire ; `null` tant qu'il charge. */
    private class Donnees(
        val requete: String,
        val lignes: List<TranslationDictionary.Resultat>,
        val fiche: TranslationDictionary.Resultat,
        val exemple: TranslationDictionary.Exemple?,
        val retour: List<TranslationDictionary.Resultat>
    )

    @Volatile private var donnees: Donnees? = null
    private var miseEnPage: StaticLayout? = null
    private var largeurMiseEnPage = 0

    private val animateur = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = DUREE_MS
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
    }

    private val fondEcran = Paint().apply { color = Color.parseColor("#F3F3F3") }
    private val blanc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val ligneClaire = Paint().apply { color = Color.parseColor("#F9F9F9") }
    private val ligneTouchee = Paint().apply { color = Color.parseColor("#E4E4E4") }
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
    private val mot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1C1C1C")
        textSize = 12f * sp
        typeface = Typeface.DEFAULT_BOLD
    }
    private val glose = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#555555")
        textSize = 10.5f * sp
    }
    private val chevron = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAAAAA")
        textSize = 13f * sp
        textAlign = Paint.Align.RIGHT
    }
    private val motFiche = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1C1C1C")
        textSize = 17f * sp
        typeface = Typeface.DEFAULT_BOLD
    }
    private val rubrique = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9A9A9A")
        textSize = 8f * sp
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.1f
    }
    private val cadreExemple = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F3F4F6") }
    private val ombre = Paint().apply { color = Color.BLACK }
    private val phrase = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1C1C1C")
        textSize = 10.5f * sp
    }

    private val titreSens = context.getString(R.string.sa_en_francais)
    private val titreExemple = resources.getQuantityString(R.plurals.fiche_exemples, 1)

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
        if (donnees == null) {
            // Le contexte de l'application dans la langue de l'interface, comme
            // l'onglet : le dictionnaire est partagé, et le premier à le
            // charger choisit la langue des gloses pour tous.
            val appli = context.applicatifDansLaLangue()
            Thread {
                val lues = runCatching { lire(appli) }.getOrNull()
                post { donnees = lues; invalidate() }
            }.start()
        }
    }

    override fun onDetachedFromWindow() {
        animateur.cancel()
        super.onDetachedFromWindow()
    }

    /** Ce que l'onglet afficherait pour le sens de « Kaz », puis pour « Kaz ». */
    private fun lire(appli: Context): Donnees? {
        val sens = TranslationDictionary.traduire(appli, MOT)?.split(",")?.firstOrNull()?.trim()
            ?.takeIf { it.isNotEmpty() } ?: return null
        val lignes = TranslationDictionary.rechercher(appli, sens, LIGNES)
        val fiche = lignes.firstOrNull { it.mot == MOT } ?: return null
        val exemple = TranslationDictionary.exemplesTraduits(appli, fiche).firstOrNull()
        val retour = TranslationDictionary.rechercher(appli, MOT, LIGNES)
        return Donnees(sens, lignes, fiche, exemple, retour)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val pw = min(w - 12f * d, 300f * d)
        val gauche = (w - pw) / 2
        val ecran = RectF(gauche - 4f * d, 0f, gauche + pw + 4f * d, h)
        canvas.drawRoundRect(ecran, 6f * d, 6f * d, fondEcran)

        val champ = RectF(gauche, 6f * d, gauche + pw, 32f * d)
        val haut = champ.bottom + 6f * d
        val hauteurLigne = (h - haut - 4f * d) / LIGNES
        val dd = donnees

        // Deux recherches : le sens de « Kaz », puis « Kaz ».
        val retour = t >= T_EFFACE
        val requete = when {
            dd == null -> ""
            !retour -> dd.requete.take((fondu(t, 0.03f, 0.12f) * (dd.requete.length + 0.99f)).toInt())
            else -> MOT.take((fondu(t, T_EFFACE + 0.02f, 0.08f) * (MOT.length + 0.99f)).toInt())
        }
        canvas.drawRect(champ, blanc)
        canvas.drawRect(champ, champBord)
        val base = champ.centerY() - (texte.descent() + texte.ascent()) / 2
        canvas.drawText(requete, champ.left + 8f * d, base, texte)
        val xc = champ.left + 8f * d + texte.measureText(requete) + 1.5f * d
        canvas.drawLine(xc, champ.top + 6f * d, xc, champ.bottom - 6f * d, curseur)
        if (dd == null) return

        val lignes = when {
            !retour && t >= 0.17f -> dd.lignes
            retour && t >= T_EFFACE + 0.13f -> dd.retour
            else -> emptyList()
        }
        val touchee = dd.lignes.indexOf(dd.fiche)
        val rects = (0 until LIGNES).map { i ->
            RectF(gauche, haut + i * hauteurLigne, gauche + pw, haut + (i + 1) * hauteurLigne - 1f * d)
        }
        lignes.forEachIndexed { i, r ->
            val enfoncee = !retour && i == touchee && t >= 0.29f && t < 0.33f
            canvas.drawRect(rects[i], if (enfoncee) ligneTouchee else if (i % 2 == 0) blanc else ligneClaire)
            val x = rects[i].left + 10f * d
            canvas.drawText(r.mot, x, rects[i].top + hauteurLigne * 0.44f, mot)
            canvas.drawText(couper(r.glose, glose, pw - 40f * d), x, rects[i].top + hauteurLigne * 0.80f, glose)
            canvas.drawText("›", rects[i].right - 8f * d, rects[i].centerY() + 4f * d, chevron)
        }

        // La fiche, qui monte du bas de l'écran.
        val montee = when {
            t < 0.33f -> 0f
            t < 0.38f -> lisse(fondu(t, 0.33f, 0.05f))
            t < 0.62f -> 1f
            t < 0.67f -> 1f - lisse(fondu(t, 0.62f, 0.05f))
            else -> 0f
        }
        if (montee > 0f) fiche(canvas, dd, ecran, champ.top + 4f * d, montee)

        // Le doigt, qui vient toucher la ligne de « Kaz ».
        if (touchee >= 0 && t >= 0.20f && t < 0.36f) {
            val cible = rects[touchee]
            val p = lisse(fondu(t, 0.20f, 0.08f))
            val x = entre(ecran.centerX() + pw * 0.25f, cible.left + pw * 0.3f, p)
            val y = entre(h - 6f * d, cible.centerY(), p)
            clavier.doigt(canvas, x, y, t >= 0.29f && t < 0.33f)
        }
    }

    private fun fiche(c: Canvas, dd: Donnees, ecran: RectF, haut: Float, montee: Float) {
        val decalage = (1f - montee) * (ecran.bottom - haut)
        ombre.alpha = (70 * montee).toInt()
        c.drawRoundRect(ecran, 6f * d, 6f * d, ombre)
        val feuille = RectF(ecran.left, haut + decalage, ecran.right, ecran.bottom + 8f * d)
        c.save()
        c.clipRect(ecran)
        c.drawRoundRect(feuille, 8f * d, 8f * d, blanc)
        val x = feuille.left + 12f * d
        var y = feuille.top + 20f * d
        c.drawText(dd.fiche.mot, x, y, motFiche)
        y += 13f * d
        c.drawText(titreSens, x, y, rubrique)
        y += 13f * d
        c.drawText("•  " + couper(dd.fiche.glose, glose, feuille.width() - 36f * d), x, y, glose)
        val exemple = dd.exemple
        if (exemple != null) {
            y += 14f * d
            c.drawText(titreExemple.uppercase(), x, y, rubrique)
            y += 5f * d
            val largeur = (feuille.width() - 40f * d).toInt()
            val page = miseEnPage?.takeIf { largeurMiseEnPage == largeur } ?: mettreEnPage(dd, exemple, largeur)
            val cadre = RectF(x - 2f * d, y, feuille.right - 10f * d, y + page.height + 10f * d)
            c.drawRoundRect(cadre, 5f * d, 5f * d, cadreExemple)
            c.save()
            c.translate(x + 6f * d, y + 5f * d)
            page.draw(c)
            c.restore()
        }
        c.restore()
    }

    /** La phrase d'exemple, les formes du mot en gras comme sur la fiche, et sa traduction en gris dessous. */
    @Suppress("DEPRECATION")
    private fun mettreEnPage(dd: Donnees, exemple: TranslationDictionary.Exemple, largeur: Int): StaticLayout {
        val formes = (dd.fiche.formes + dd.fiche.mot).mapTo(HashSet()) { AccentTolerantMatcher.normalize(it) }
        val traduction = exemple.traduction?.takeIf { it.isNotBlank() }
        val tout = SpannableString(if (traduction == null) exemple.phrase else exemple.phrase + "\n" + traduction)
        Regex("\\p{L}+").findAll(exemple.phrase).forEach { m ->
            if (AccentTolerantMatcher.normalize(m.value) in formes) {
                tout.setSpan(StyleSpan(Typeface.BOLD), m.range.first, m.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        if (traduction != null) {
            val debut = exemple.phrase.length + 1
            tout.setSpan(RelativeSizeSpan(0.9f), debut, tout.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            tout.setSpan(ForegroundColorSpan(Color.parseColor("#777777")), debut, tout.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return StaticLayout(tout, phrase, largeur, Layout.Alignment.ALIGN_NORMAL, 1.1f, 0f, false).also {
            miseEnPage = it
            largeurMiseEnPage = largeur
        }
    }

    private fun couper(s: String, p: Paint, largeur: Float): String =
        TextUtils.ellipsize(s, TextPaint(p), largeur, TextUtils.TruncateAt.END).toString()

    private companion object {
        const val DUREE_MS = 12_000L
        const val HAUTEUR_DP = 150f
        const val MOT = "Kaz"
        const val LIGNES = 3

        /** La fiche redescendue, le champ vidé : « Kaz » se tape à son tour. */
        const val T_EFFACE = 0.70f

        /** L'image montrée sans animation : la fiche ouverte. */
        const val FIGEE = 0.50f
    }
}
