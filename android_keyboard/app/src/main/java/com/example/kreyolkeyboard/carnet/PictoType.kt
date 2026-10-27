package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View
import androidx.annotation.StringRes
import com.example.kreyolkeyboard.R

/**
 * Le type d'une carte imprimée, en pictogramme, dans l'écu TYPE.
 *
 * Le type dit ce que fait la carte au Wuertduell (un nom se pose et attaque,
 * un verbe agit, un adjectif renforce, un nombre donne un symbole de plus :
 * `docs/regle-du-jeu.md`), et c'est un enfant de
 * 6 ans qui doit le lire. Écrit en toutes lettres, « SUBSTANTIV » devait
 * rétrécir pour tenir dans l'écu et changeait de longueur avec la langue de
 * l'édition. Un pictogramme se lit sans savoir lire, et c'est le même dans
 * les cinq langues (idée reprise des cartes à collectionner, qui disent le
 * poste d'un joueur par un petit terrain, 2026-10-27).
 *
 * Les formes sont les **symboles de grammaire Montessori**, que des
 * enseignants connaissent déjà : grand triangle noir pour le nom, rond rouge
 * pour le verbe, triangle bleu plus petit pour l'adjectif. Le bleu est plus
 * clair que le bleu nuit de Montessori : en noir et blanc, il doit rester
 * distinct du noir du nom, en plus d'être plus petit. Le nombre, que
 * Montessori ne code pas, s'écrit « 123 ».
 *
 * Forme et couleur codent ensemble (skill designer-cartes § 2.3) : un enfant
 * daltonien et une photocopie distinguent encore les quatre. Le nom de la
 * carte reste écrit en toutes lettres sur la pastille de nature, et la vue
 * porte le type pour les lecteurs d'écran.
 *
 * Tout se dessine en unités de carte, rapportées à la hauteur de la case
 * ([Ornement.ECU_D_IMPRIME_TEXTE], 18 unités).
 */
class PictoType(context: Context, private val forme: Forme, private val trait: Int) : View(context) {

    enum class Forme { NOM, VERBE, ADJECTIF, NOMBRE }

    companion object {
        /** La hauteur de la case en unités de carte : celle de [Ornement.ECU_D_IMPRIME_TEXTE]. */
        private const val CASE = 18f

        private const val NOIR = 0xFF15120E.toInt()
        private const val ROUGE = 0xFFD32F2F.toInt()
        private const val BLEU = 0xFF2F6FD6.toInt()

        /** La forme d'un type de [ImpressionCarte.type], ou `null` pour « Mot », qui reste écrit. */
        fun pour(@StringRes type: Int): Forme? = when (type) {
            R.string.cat_nom -> Forme.NOM
            R.string.cat_verbe -> Forme.VERBE
            R.string.cat_adjectif -> Forme.ADJECTIF
            R.string.cat_nombre -> Forme.NOMBRE
            else -> null
        }
    }

    private val plein = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val contour = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val chiffres = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        val u = height / CASE
        val cx = width / 2f
        // Les deux triangles posent sur la même ligne : l'adjectif se voit
        // plus petit que le nom même quand on n'a qu'une carte sous les yeux.
        // Pas plus petit que 12,5 (2,4 mm) : à 11, le triangle bleu se
        // perdait sur l'argent d'une commune (planche de contrôle, 2026-10-27).
        val base = 17f * u
        contour.color = trait
        contour.strokeWidth = 1.1f * u
        when (forme) {
            Forme.NOM -> triangle(canvas, cx, base, 16f * u, NOIR)
            Forme.ADJECTIF -> triangle(canvas, cx, base, 12.5f * u, BLEU)
            Forme.VERBE -> {
                plein.color = ROUGE
                canvas.drawCircle(cx, 9f * u, 7.5f * u, plein)
                canvas.drawCircle(cx, 9f * u, 7.5f * u, contour)
            }
            Forme.NOMBRE -> {
                chiffres.color = trait
                chiffres.textSize = 15f * u
                // Ligne de base à 0,36 em sous le milieu, comme les textes de la carte.
                canvas.drawText("123", cx, 9f * u + 15f * u * 0.36f, chiffres)
            }
        }
    }

    /** Un triangle équilatéral pointe en haut, de hauteur [h], posé sur [base]. */
    private fun triangle(canvas: Canvas, cx: Float, base: Float, h: Float, couleur: Int) {
        val demi = h / 1.732f
        val p = Path().apply {
            moveTo(cx, base - h)
            lineTo(cx + demi, base)
            lineTo(cx - demi, base)
            close()
        }
        plein.color = couleur
        canvas.drawPath(p, plein)
        canvas.drawPath(p, contour)
    }
}
