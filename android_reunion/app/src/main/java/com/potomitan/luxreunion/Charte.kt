package com.potomitan.luxreunion

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * La charte graphique de Lëtzebuergesch Clavier, reprise à l'identique :
 * barre de titre bleue #0080FF en texte blanc gras, fond gris clair #F5F5F5,
 * cartes blanches aux coins arrondis, boutons bleus pleins, accent orange
 * #FF8C00 sur l'onglet choisi, vert #4CAF50 pour ce qui est fait. Comme dans
 * le clavier, l'interface est construite en code (SettingsActivity.kt).
 *
 * Pensée pour quelqu'un qui n'est pas à l'aise avec la technique : des mots
 * plutôt que des icônes, des boutons larges, du texte assez grand.
 */
object Charte {
    const val BLEU = 0xFF0080FF.toInt()
    const val BLEU_PALE = 0xFFE3F2FD.toInt()
    const val ENCRE = 0xFF1C1C1C.toInt()
    const val TEXTE = 0xFF333333.toInt()
    const val GRIS = 0xFF666666.toInt()
    const val GRIS_CLAIR = 0xFF999999.toInt()
    const val TRAIT = 0xFFE0E0E0.toInt()
    const val FOND = 0xFFF5F5F5.toInt()
    const val BLANC = 0xFFFFFFFF.toInt()
    const val VERT = 0xFF4CAF50.toInt()
    const val VERT_PALE = 0xFFE8F5E9.toInt()
    const val ORANGE = 0xFFFF8C00.toInt()
    const val ORANGE_PALE = 0xFFFFF3E0.toInt()
    const val ROUGE = 0xFFE53935.toInt()
    const val ROUGE_PALE = 0xFFFFEBEE.toInt()

    fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()
    fun Context.dp(v: Float): Float = v * resources.displayMetrics.density

    /** Une tablette : assez large pour mettre le luxembourgeois et le français côte à côte. */
    fun Context.large(): Boolean = resources.configuration.screenWidthDp >= 720

    fun fond(ctx: Context, couleur: Int, rayonDp: Int, bordure: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(couleur)
            cornerRadius = ctx.dp(rayonDp.toFloat())
            if (bordure != null) setStroke(ctx.dp(1), bordure)
        }

    /** Un fond qui réagit au toucher, comme les boutons Material. */
    fun touchable(base: Drawable, clair: Boolean = true): Drawable =
        RippleDrawable(ColorStateList.valueOf(if (clair) 0x33FFFFFF else 0x220080FF), base, base)

    // --- Page -------------------------------------------------------------------

    /**
     * Installe une page : la barre bleue (qui passe sous la barre d'état, comme
     * dans le clavier), le contenu, et une barre basse facultative pour le
     * bouton principal. Bord à bord sur toutes les versions : Android 15+
     * l'impose, et les marges suivent les barres système et le clavier.
     */
    fun page(
        activite: Activity,
        titre: String,
        contenu: View,
        retour: Boolean = true,
        action: Pair<String, () -> Unit>? = null,
        bas: View? = null
    ): TextView {
        val ctx: Context = activite
        val racine = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(FOND)
        }
        val barre = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(BLEU)
            minimumHeight = ctx.dp(64)
            elevation = ctx.dp(4f)
        }
        val gauche = TextView(ctx).apply {
            text = if (retour) "←" else ""
            setTextColor(BLANC)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            gravity = Gravity.CENTER
            if (retour) {
                background = touchable(GradientDrawable().apply { setColor(Color.TRANSPARENT) })
                contentDescription = "Retour"
                setOnClickListener { activite.finish() }
            }
        }
        barre.addView(gauche, LinearLayout.LayoutParams(ctx.dp(56), ctx.dp(64)))
        val vueTitre = TextView(ctx).apply {
            text = titre
            setTextColor(BLANC)
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        barre.addView(vueTitre, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val droite = TextView(ctx).apply {
            setTextColor(BLANC)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
            if (action != null) {
                text = action.first
                setPadding(ctx.dp(12), 0, ctx.dp(12), 0)
                background = touchable(GradientDrawable().apply { setColor(Color.TRANSPARENT) })
                setOnClickListener { action.second() }
            }
        }
        barre.addView(droite, LinearLayout.LayoutParams(
            if (action != null) ViewGroup.LayoutParams.WRAP_CONTENT else ctx.dp(56), ctx.dp(64)))
        racine.addView(barre, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        racine.addView(contenu, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        if (bas != null) {
            bas.setBackgroundColor(BLANC)
            bas.elevation = ctx.dp(8f)
            racine.addView(bas, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val basPadding = bas?.let { intArrayOf(it.paddingLeft, it.paddingTop, it.paddingRight, it.paddingBottom) }
        racine.setOnApplyWindowInsetsListener { _, insets ->
            val e = encarts(insets)
            barre.setPadding(e[0], e[1], e[2], 0)
            if (bas != null && basPadding != null) {
                bas.setPadding(basPadding[0] + e[0], basPadding[1], basPadding[2] + e[2], basPadding[3] + e[3])
                contenu.setPadding(e[0], 0, e[2], 0)
            } else {
                contenu.setPadding(e[0], 0, e[2], e[3])
            }
            insets
        }
        activite.setContentView(racine)
        bordABord(activite)
        return vueTitre
    }

    /** Gauche, haut, droite, bas : barres système, encoche et clavier à l'écran. */
    private fun encarts(i: WindowInsets): IntArray {
        if (Build.VERSION.SDK_INT >= 30) {
            val s = i.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val ime = i.getInsets(WindowInsets.Type.ime())
            return intArrayOf(s.left, s.top, s.right, max(s.bottom, ime.bottom))
        }
        @Suppress("DEPRECATION")
        return intArrayOf(i.systemWindowInsetLeft, i.systemWindowInsetTop, i.systemWindowInsetRight, i.systemWindowInsetBottom)
    }

    @Suppress("DEPRECATION")
    private fun bordABord(activite: Activity) {
        val w = activite.window
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false)
            // Icônes claires en haut (sur le bleu), sombres en bas (sur le gris clair).
            w.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS or WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
        } else {
            w.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    // --- Éléments -----------------------------------------------------------------

    /** Une carte blanche, comme celles de l'onglet « Haut » du clavier. */
    fun carte(ctx: Context, marge: Boolean = true): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = fond(ctx, BLANC, 16)
        elevation = ctx.dp(1f)
        setPadding(ctx.dp(20), ctx.dp(18), ctx.dp(20), ctx.dp(18))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            if (marge) setMargins(ctx.dp(16), ctx.dp(8), ctx.dp(16), ctx.dp(8))
        }
    }

    fun texte(ctx: Context, t: CharSequence, taille: Float = 16f, couleur: Int = TEXTE, gras: Boolean = false): TextView =
        TextView(ctx).apply {
            text = t
            setTextColor(couleur)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, taille)
            if (gras) setTypeface(Typeface.DEFAULT_BOLD)
            setLineSpacing(0f, 1.2f)
        }

    fun titre(ctx: Context, t: CharSequence, taille: Float = 20f): TextView = texte(ctx, t, taille, ENCRE, gras = true)

    /** Un intertitre en petites capitales grises, comme « MOT DU JOUR ». */
    fun rubrique(ctx: Context, t: String): TextView = texte(ctx, t.uppercase(), 13f, GRIS, gras = true).apply {
        letterSpacing = 0.08f
        setPadding(ctx.dp(20), ctx.dp(20), ctx.dp(20), ctx.dp(4))
    }

    fun boutonPrincipal(ctx: Context, t: String, couleur: Int = BLEU, action: () -> Unit): Button =
        Button(ctx).apply {
            text = t
            isAllCaps = false
            setTextColor(BLANC)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(Typeface.DEFAULT_BOLD)
            background = touchable(fond(ctx, couleur, 14))
            stateListAnimator = null
            minHeight = ctx.dp(58)
            setPadding(ctx.dp(16), 0, ctx.dp(16), 0)
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

    fun boutonSecondaire(ctx: Context, t: String, couleur: Int = BLEU, action: () -> Unit): Button =
        Button(ctx).apply {
            text = t
            isAllCaps = false
            setTextColor(couleur)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(Typeface.DEFAULT_BOLD)
            background = touchable(fond(ctx, BLANC, 14, bordure = couleur), clair = false)
            stateListAnimator = null
            minHeight = ctx.dp(52)
            setPadding(ctx.dp(14), 0, ctx.dp(14), 0)
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

    fun champ(ctx: Context, indice: String, lignes: Int = 1, valeur: String = ""): EditText = EditText(ctx).apply {
        hint = indice
        setText(valeur)
        setTextColor(ENCRE)
        setHintTextColor(GRIS_CLAIR)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        background = fond(ctx, FOND, 10)
        setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(14), ctx.dp(12))
        if (lignes > 1) {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = lignes
            gravity = Gravity.TOP or Gravity.START
        } else {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 1
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = ctx.dp(10) }
    }

    /** Un encadré coloré : conseil (orange), succès (vert), erreur (rouge). */
    fun encadre(ctx: Context, t: CharSequence, fondCouleur: Int, texteCouleur: Int = TEXTE): TextView =
        texte(ctx, t, 15f, texteCouleur).apply {
            background = fond(ctx, fondCouleur, 12)
            setPadding(ctx.dp(16), ctx.dp(14), ctx.dp(16), ctx.dp(14))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(ctx.dp(16), ctx.dp(8), ctx.dp(16), ctx.dp(8)) }
        }

    /** Une pastille d'état, comme celles des réunions dans la liste. */
    fun pastille(ctx: Context, t: String, couleur: Int, fondCouleur: Int): TextView =
        texte(ctx, t, 13f, couleur, gras = true).apply {
            background = fond(ctx, fondCouleur, 20)
            setPadding(ctx.dp(10), ctx.dp(4), ctx.dp(10), ctx.dp(4))
        }

    fun espace(ctx: Context, hauteurDp: Int): View = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(1, ctx.dp(hauteurDp))
    }

    fun marge(v: View, haut: Int = 0, bas: Int = 0) {
        val lp = (v.layoutParams as? ViewGroup.MarginLayoutParams)
            ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = v.context.dp(haut)
        lp.bottomMargin = v.context.dp(bas)
        v.layoutParams = lp
    }

    /** La barre basse d'une page, qui porte le bouton principal. */
    fun barreBasse(ctx: Context, vararg vues: View): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ctx.dp(16), ctx.dp(12), ctx.dp(16), ctx.dp(12))
        vues.forEachIndexed { i, v ->
            if (i > 0) marge(v, haut = 8)
            addView(v)
        }
    }
}
