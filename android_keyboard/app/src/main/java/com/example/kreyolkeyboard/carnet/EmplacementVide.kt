package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/**
 * La place d'une carte qui manque encore à une série.
 *
 * Elle a la taille et l'arrondi d'une vignette, pour que la page d'album se
 * lise comme une grille dont on n'a pas encore tout collé, et elle porte un
 * **indice** : le nombre en chiffres, la silhouette du dessin, l'emblème du
 * jeu. Jamais le mot : c'est lui qu'on gagne.
 *
 * Elle est dessinée et non composée de vues, parce qu'une page des
 * enluminures en pose une centaine.
 */
class EmplacementVide(
    context: Context,
    private val serie: Serie,
    private val cle: String
) : View(context) {

    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cadre = RectF()
    private val pointilles = DashPathEffect(floatArrayOf(10f, 7f), 0f)

    /** Le dessin, mis à l'échelle à la première trame qui connaît la taille. */
    private var silhouette: Path? = null
    private var silhouettePour = -1

    private val jeu: JeuCarte? = if (serie == Serie.JEUX) JeuCarte.parId(cle) else null

    init {
        // Le lecteur d'écran dit ce qui manque, comme l'œil le voit.
        contentDescription = when (serie) {
            Serie.NOMBRES -> context.getString(com.example.kreyolkeyboard.R.string.emplacement_nombre, cle)
            Serie.JEUX -> context.getString(
                com.example.kreyolkeyboard.R.string.emplacement_jeu, jeu?.libelle(context) ?: cle
            )
            Serie.ENLUMINURES -> context.getString(com.example.kreyolkeyboard.R.string.emplacement_dessin)
        }
    }

    override fun onMeasure(largeurSpec: Int, hauteurSpec: Int) {
        // Carré, comme la vignette (300 × 300 unités de carte).
        val l = MeasureSpec.getSize(largeurSpec)
        setMeasuredDimension(l, l)
    }

    override fun onDraw(canvas: Canvas) {
        val l = width.toFloat()
        if (l <= 0f) return
        val d = resources.displayMetrics.density
        val rayon = l * Ornement.RAYON / Ornement.LARGEUR

        cadre.set(d, d, l - d, l - d)
        pinceau.style = Paint.Style.FILL
        pinceau.pathEffect = null
        pinceau.color = FOND
        canvas.drawRoundRect(cadre, rayon, rayon, pinceau)

        pinceau.style = Paint.Style.STROKE
        pinceau.strokeWidth = 1.5f * d
        pinceau.color = TRAIT
        pinceau.pathEffect = pointilles
        canvas.drawRoundRect(cadre, rayon, rayon, pinceau)
        pinceau.pathEffect = null
        pinceau.style = Paint.Style.FILL

        when (serie) {
            Serie.NOMBRES -> texte(canvas, cle, l * 0.34f, l / 2f, gras = true)
            Serie.ENLUMINURES -> dessin(canvas, l)
            Serie.JEUX -> {
                texte(canvas, jeu?.emoji ?: "?", l * 0.26f, l * 0.46f, gras = false, opaque = true)
                texte(canvas, jeu?.libelle(context) ?: cle, l * 0.09f, l * 0.74f, gras = true)
            }
        }
    }

    private fun texte(
        c: Canvas, contenu: String, corps: Float, y: Float, gras: Boolean, opaque: Boolean = false
    ) {
        pinceau.textAlign = Paint.Align.CENTER
        pinceau.textSize = corps
        pinceau.typeface = if (gras) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        pinceau.color = TRAIT
        // Un emoji garde ses couleurs, voilées : un emblème gris ne se reconnaît plus.
        pinceau.alpha = if (opaque) 110 else 255
        val m = pinceau.fontMetrics
        c.drawText(contenu, width / 2f, y - (m.ascent + m.descent) / 2f, pinceau)
        pinceau.alpha = 255
    }

    /** La silhouette du meuble, à plat et sans gravure : une forme en creux. */
    private fun dessin(c: Canvas, l: Float) {
        val taille = l.toInt()
        if (silhouettePour != taille) {
            silhouettePour = taille
            silhouette = Meubles.chemin(cle)?.let { brut ->
                val cote = l * 0.58f
                val m = Matrix().apply {
                    setScale(cote / Meubles.COTE, cote / Meubles.COTE)
                    postTranslate(l / 2f, l / 2f)
                }
                Path().also {
                    brut.transform(m, it)
                    it.fillType = Path.FillType.EVEN_ODD
                }
            }
        }
        val p = silhouette ?: return texte(c, "?", l * 0.3f, l / 2f, gras = true)
        pinceau.color = SILHOUETTE
        c.drawPath(p, pinceau)
    }

    private companion object {
        val FOND = Color.parseColor("#EFEBE4")
        val TRAIT = Color.parseColor("#A89F90")
        val SILHOUETTE = Color.parseColor("#D3CBBE")
    }
}
