package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import androidx.annotation.StringRes
import com.example.kreyolkeyboard.R
import kotlin.math.cos
import kotlin.math.sin

/**
 * Les émaux des deux écus de la carte imprimée : le type ([PictoType]) et le
 * coût ([PictoCout]).
 *
 * Une forme plate, noire ou rouge, posée sur l'or ciselé du cadre, était le
 * seul élément de la carte qui n'était pas « travaillé », et le rond du coût
 * et le triangle du type n'avaient pas la même taille (propriétaire,
 * 2026-10-27). Chaque symbole est donc un émail serti, comme les gemmes et le
 * médaillon : une ombre portée, un dégradé clair en haut à gauche, un reflet,
 * une sertissure sombre. Tous tiennent dans la même hauteur, 12 à 13,5 unités
 * (environ 2,5 mm), réglée à l'œil : un triangle paraît plus petit qu'un rond
 * de même hauteur.
 *
 * Tout se dessine en unités de carte, rapportées à la hauteur de la case
 * ([Ornement.ECU_D_IMPRIME_TEXTE], [CASE] unités).
 */
internal object Email {

    /** La hauteur de la case des émaux, en unités de carte. */
    const val CASE = 16f

    private val boite = RectF()

    fun melange(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
    )

    /** Peint [chemin], déjà en pixels, comme un émail serti de [couleur]. */
    fun peindre(c: Canvas, p: Paint, chemin: Path, couleur: Int, trait: Int, u: Float) {
        chemin.computeBounds(boite, true)
        p.shader = null
        p.style = Paint.Style.FILL
        // L'ombre portée, vers le bas à droite comme la lumière du carnet.
        p.color = 0x59000000
        c.save()
        c.translate(0.5f * u, 0.7f * u)
        c.drawPath(chemin, p)
        c.restore()
        // L'émail bombé.
        p.color = Color.BLACK
        p.shader = LinearGradient(
            boite.left, boite.top, boite.right, boite.bottom,
            intArrayOf(melange(couleur, Color.WHITE, 0.45f), couleur, melange(couleur, Color.BLACK, 0.45f)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.drawPath(chemin, p)
        p.shader = null
        // Le reflet, dans le quart haut gauche, sans sortir de la forme.
        c.save()
        c.clipPath(chemin)
        p.color = 0x80FFFFFF.toInt()
        c.drawOval(
            boite.left + boite.width() * 0.14f, boite.top + boite.height() * 0.1f,
            boite.left + boite.width() * 0.56f, boite.top + boite.height() * 0.4f, p
        )
        c.restore()
        // La sertissure.
        p.style = Paint.Style.STROKE
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = 0.9f * u
        p.color = trait
        c.drawPath(chemin, p)
        p.style = Paint.Style.FILL
    }

    /** Des chiffres gravés : une lèvre claire dessous, le creux sombre dessus, comme [MotGrave]. */
    fun graver(c: Canvas, p: Paint, texte: String, cx: Float, cy: Float, taille: Float, trait: Int, u: Float) {
        p.shader = null
        p.style = Paint.Style.FILL
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = taille * u
        val base = cy + taille * u * 0.36f
        p.color = 0xFFFFF4D6.toInt()
        c.drawText(texte, cx, base + 0.7f * u, p)
        p.color = trait
        c.drawText(texte, cx, base, p)
    }

    /** Un triangle équilatéral pointe en haut, de hauteur [h], posé sur [base]. */
    fun triangle(cx: Float, base: Float, h: Float): Path = Path().apply {
        val demi = h / 1.732f
        moveTo(cx, base - h)
        lineTo(cx + demi, base)
        lineTo(cx - demi, base)
        close()
    }

    /** Une étoile à [branches] branches, pointe en haut. */
    fun etoile(cx: Float, cy: Float, branches: Int, rayon: Float, creux: Float): Path = Path().apply {
        for (k in 0 until 2 * branches) {
            val a = k * Math.PI.toFloat() / branches - Math.PI.toFloat() / 2f
            val r = if (k % 2 == 0) rayon else creux
            if (k == 0) moveTo(cx + cos(a) * r, cy + sin(a) * r) else lineTo(cx + cos(a) * r, cy + sin(a) * r)
        }
        close()
    }
}

/**
 * Le type d'une carte imprimée, en pictogramme, dans l'écu TYPE.
 *
 * Le type dit ce que fait la carte au Wuertduell (un nom se pose et attaque,
 * un verbe agit, un adjectif renforce, un nombre donne un symbole de plus :
 * `docs/regle-du-jeu.md`), et c'est un enfant de 6 ans qui doit le lire.
 * Écrit en toutes lettres, « SUBSTANTIV » devait rétrécir pour tenir dans
 * l'écu et changeait de longueur avec la langue de l'édition. Un pictogramme
 * se lit sans savoir lire, et c'est le même dans les cinq langues (idée
 * reprise des cartes à collectionner, qui disent le poste d'un joueur par un
 * petit terrain, 2026-10-27).
 *
 * Les formes sont les **symboles de grammaire Montessori**, que des
 * enseignants connaissent déjà : grand triangle noir pour le nom, rond rouge
 * pour le verbe, triangle bleu plus petit pour l'adjectif. Le bleu est plus
 * clair que le bleu nuit de Montessori : en noir et blanc, il doit rester
 * distinct du noir du nom, en plus d'être plus petit. Le nombre, que
 * Montessori ne code pas, est gravé « 123 ».
 *
 * Forme et couleur codent ensemble (skill designer-cartes § 2.3) : un enfant
 * daltonien et une photocopie distinguent encore les quatre. Le nom de la
 * carte reste écrit en toutes lettres sur la pastille de nature, et la vue
 * porte le type pour les lecteurs d'écran. Le rendu est celui de [Email].
 */
class PictoType(context: Context, private val forme: Forme, private val trait: Int) : View(context) {

    enum class Forme { NOM, VERBE, ADJECTIF, NOMBRE }

    companion object {
        private const val NOIR = 0xFF2A2622.toInt()
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

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val u = height / Email.CASE
        val cx = width / 2f
        // Les deux triangles posent sur la même ligne : l'adjectif se voit
        // plus petit que le nom même quand on n'a qu'une carte sous les yeux.
        // Pas plus petit que 10,5 (2 mm) : à 9, le triangle bleu se perdait
        // sur l'argent d'une commune (planche de contrôle, 2026-10-27).
        val base = 14.5f * u
        when (forme) {
            Forme.NOM -> Email.peindre(canvas, p, Email.triangle(cx, base, 13.5f * u), NOIR, trait, u)
            Forme.ADJECTIF -> Email.peindre(canvas, p, Email.triangle(cx, base, 10.5f * u), BLEU, trait, u)
            Forme.VERBE -> Email.peindre(
                canvas, p, Path().apply { addCircle(cx, 8f * u, 6.25f * u, Path.Direction.CW) }, ROUGE, trait, u
            )
            Forme.NOMBRE -> Email.graver(canvas, p, "123", cx, 8f * u, 13f, trait, u)
        }
    }
}

/**
 * Le coût d'une carte imprimée, dans l'écu COÛT : autant de symboles que la
 * carte coûte (● ◆◆ ★★★ ✦✦✦✦, `ImpressionCarte.cout`), chacun en émail serti
 * sombre ([Email]), ou un « 0 » gravé pour un nombre, qui ne coûte rien.
 */
class PictoCout(context: Context, private val cout: String, private val trait: Int) : View(context) {

    companion object {
        private const val NIELLE = 0xFF3B2A12.toInt()
        /** L'écart entre deux symboles, de centre à centre : quatre tiennent dans les 60 unités de l'écu. */
        private const val PAS = 13f
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val u = height / Email.CASE
        val cy = 8f * u
        if (cout == "0" || cout.isEmpty()) {
            Email.graver(canvas, p, "0", width / 2f, cy, 13f, trait, u)
            return
        }
        val n = cout.length
        val depart = width / 2f - (n - 1) * PAS * u / 2f
        for (k in 0 until n) {
            val x = depart + k * PAS * u
            val chemin = when (cout[k]) {
                '●' -> Path().apply { addCircle(x, cy, 5.75f * u, Path.Direction.CW) }
                '◆' -> Path().apply {
                    moveTo(x, cy - 6.5f * u); lineTo(x + 4.75f * u, cy)
                    lineTo(x, cy + 6.5f * u); lineTo(x - 4.75f * u, cy); close()
                }
                '★' -> Email.etoile(x, cy + 0.6f * u, 5, 6.9f * u, 2.9f * u)
                else -> Email.etoile(x, cy, 4, 6.8f * u, 2.3f * u)
            }
            Email.peindre(canvas, p, chemin, NIELLE, trait, u)
        }
    }
}
