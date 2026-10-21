package com.example.kreyolkeyboard

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Le clavier en miniature, dessiné dans les bulles de la visite guidée
 * ([AnimationGestes], [AnimationDictee]) : la disposition par défaut, ses
 * poids et les couleurs du thème clair, pour que l'image ressemble à ce que
 * l'utilisateur trouvera en tapant. Avec le doigt qui touche et les pastilles
 * numérotées qui suivent les temps du texte, comme dans [carnet.AnimationEchange].
 *
 * Les bulles restent claires quel que soit le thème du téléphone : le clavier
 * miniature aussi.
 */
class MiniClavier(resources: Resources) {

    private val d = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity

    val rangees: List<Array<String>> =
        DispositionClavier.DEFAUT.rangeesLettres(KeyboardLayoutManager.ChampAdresse.AUCUN)

    val rouge = Color.parseColor("#ED2939")
    val bleu = Color.parseColor("#00A1DE")

    private val fond = Paint().apply { color = Color.parseColor("#F5F5F5") }
    private val touche = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bordure = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D6D6D6")
        style = Paint.Style.STROKE
        strokeWidth = 0.8f * d
    }
    private val lettre = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 10f * sp
    }
    private val doigtFond = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val doigtBord = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * d
    }
    private val pastille = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chiffre = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * sp
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    /** Hauteur d'une rangée et écart entre deux rangées. */
    val hauteurRangee = 20f * d
    val ecart = 3f * d

    /** Les touches posées dans [zone], rangée par rangée, dans l'ordre de [rangees]. */
    fun placer(zone: RectF): List<List<Pair<String, RectF>>> = rangees.mapIndexed { i, rangee ->
        val total = rangee.sumOf { DispositionClavier.DEFAUT.poidsTouche(it).toDouble() }.toFloat()
        val unite = zone.width() / total
        var x = zone.left
        val y = zone.top + i * (hauteurRangee + ecart)
        rangee.map { cle ->
            val l = unite * DispositionClavier.DEFAUT.poidsTouche(cle)
            val r = RectF(x + 1.2f * d, y, x + l - 1.2f * d, y + hauteurRangee)
            x += l
            cle to r
        }
    }

    fun hauteur(rangeesMontrees: Int = rangees.size) =
        rangeesMontrees * hauteurRangee + (rangeesMontrees - 1) * ecart

    /** Le fond du clavier et ses touches ; [appuyee] s'assombrit, [alpha] estompe le tout. */
    fun dessiner(c: Canvas, zone: RectF, touches: List<List<Pair<String, RectF>>>, appuyee: String? = null, alpha: Int = 255) {
        fond.alpha = alpha
        c.drawRect(zone.left - 4f * d, zone.top - 4f * d, zone.right + 4f * d, zone.bottom + 4f * d, fond)
        for (rangee in touches) for ((cle, r) in rangee) {
            val couleur = when (cle) {
                "⏎", "123", "EMOJI" -> rouge
                " ", ",", ".", "'" -> bleu
                else -> Color.WHITE
            }
            touche.color = if (cle == appuyee) assombrir(couleur) else couleur
            touche.alpha = alpha
            c.drawRoundRect(r, 3f * d, 3f * d, touche)
            if (couleur == Color.WHITE) {
                bordure.alpha = alpha
                c.drawRoundRect(r, 3f * d, 3f * d, bordure)
            }
            val libelle = when (cle) {
                " " -> ""
                "EMOJI" -> "☺"
                else -> cle
            }
            lettre.color = if (couleur == Color.WHITE) Color.parseColor("#1A1A1A") else Color.WHITE
            lettre.alpha = alpha
            lettre.textSize = (if (libelle.length > 1) 7f else 10f) * sp
            c.drawText(libelle, r.centerX(), r.centerY() - (lettre.descent() + lettre.ascent()) / 2, lettre)
        }
    }

    /** Le doigt, comme l'affichage des appuis d'Android : plus petit et plus sombre quand il appuie. */
    fun doigt(c: Canvas, x: Float, y: Float, appuie: Boolean) {
        val rayon = (if (appuie) 8f else 10f) * d
        doigtFond.alpha = if (appuie) 110 else 70
        c.drawCircle(x, y, rayon, doigtFond)
        c.drawCircle(x, y, rayon, doigtBord)
    }

    /** Les temps numérotés du texte que l'animation montre, [courant] en rouge. */
    fun pastilles(c: Canvas, cx: Float, cy: Float, numeros: List<Int>, courant: Int) {
        val pas = 26f * d
        numeros.forEachIndexed { i, k ->
            val x = cx + (i - (numeros.size - 1) / 2f) * pas
            val allume = k == courant
            pastille.color = if (allume) rouge else Color.parseColor("#E3E3E3")
            c.drawCircle(x, cy, 9f * d, pastille)
            chiffre.color = if (allume) Color.WHITE else Color.parseColor("#6B6B6B")
            c.drawText(k.toString(), x, cy - (chiffre.descent() + chiffre.ascent()) / 2, chiffre)
        }
    }

    private fun assombrir(couleur: Int): Int {
        val f = 0.8f
        return Color.rgb((Color.red(couleur) * f).toInt(), (Color.green(couleur) * f).toInt(), (Color.blue(couleur) * f).toInt())
    }

    companion object {
        fun fondu(t: Float, debut: Float, duree: Float) = ((t - debut) / duree).coerceIn(0f, 1f)
        fun lisse(p: Float) = p * p * (3 - 2 * p)
        fun entre(a: Float, b: Float, p: Float) = a + (b - a) * p
    }
}
