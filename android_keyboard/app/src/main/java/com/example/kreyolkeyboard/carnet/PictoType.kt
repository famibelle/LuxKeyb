package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.graphics.BlurMaskFilter
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

/**
 * Les émaux des deux écus de la carte imprimée : le type ([PictoType]) et le
 * coût ([PictoCout]).
 *
 * Une forme plate, noire ou rouge, posée sur l'or ciselé du cadre, était le
 * seul élément de la carte qui n'était pas « travaillé », et le rond du coût
 * et le triangle du type n'avaient pas la même taille (propriétaire,
 * 2026-10-27). Chaque symbole est donc un émail serti, comme les gemmes et le
 * médaillon : une ombre portée, un dégradé clair en haut à gauche, un reflet,
 * une sertissure sombre. Tous tiennent dans la même hauteur, 11 à 13,6 unités
 * (environ 2,5 mm).
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
}

/**
 * Une pièce d'or : le coût d'une carte se paie en pièces ([PictoCout]). Un
 * émail d'or plus chaud que le métal de l'écu, cerclé de sombre pour s'en
 * détacher, avec l'anneau gravé d'une pièce.
 */
internal fun piece(c: Canvas, p: Paint, x: Float, y: Float, r: Float, trait: Int, u: Float) {
    Email.peindre(c, p, Path().apply { addCircle(x, y, r, Path.Direction.CW) }, 0xFFF2B705.toInt(), trait, u)
    p.style = Paint.Style.STROKE
    p.strokeWidth = 0.6f * u
    p.color = 0xFF9A6400.toInt()
    c.drawCircle(x, y, r * 0.68f, p)
    p.style = Paint.Style.FILL
}

/**
 * Le type d'une carte imprimée, en pictogramme, dans l'écu TYPE.
 *
 * Le type dit ce que fait la carte au Wuertduell (`docs/regle-du-jeu.md`), et
 * le public commence à 6 ans : le pictogramme montre donc **ce que la carte
 * fait**, pas ce qu'elle est en grammaire.
 *
 * - **nom** : un personnage (rouge). La règle le dit : « un nom est un
 *   personnage », il se pose devant toi, attaque et bloque ;
 * - **verbe** : un éclair (orange), l'action d'un tour ;
 * - **adjectif** : un bouclier marqué « + » (bleu), le renfort qui reste ;
 * - **nombre** : « 123 » sur un badge vert. Une pièce « +1 » (ce que le
 *   nombre rapporte) se confondait avec les pièces du coût, juste à côté.
 *
 * Les symboles Montessori d'abord retenus (grand triangle noir, rond rouge,
 * petit triangle bleu) ne se comprenaient pas sans les avoir appris : le
 * propriétaire lui-même ne les lisait pas (2026-10-27). Un enfant de 6 ans
 * reconnaît un bonhomme, un éclair, un bouclier, des chiffres.
 *
 * Quatre silhouettes différentes et quatre couleurs : un enfant daltonien et
 * une photocopie les distinguent encore (skill designer-cartes § 2.3). La
 * nature reste écrite en toutes lettres sur la pastille, et la vue porte le
 * type pour les lecteurs d'écran. Le rendu est celui de [Email].
 */
class PictoType(context: Context, private val forme: Forme, private val trait: Int) : View(context) {

    enum class Forme { NOM, VERBE, ADJECTIF, NOMBRE }

    companion object {
        internal const val ROUGE = 0xFFD32F2F.toInt()
        internal const val ORANGE = 0xFFF57C00.toInt()
        internal const val BLEU = 0xFF2F6FD6.toInt()
        internal const val VERT = 0xFF00897B.toInt()

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
        dessiner(canvas, p, forme, width / 2f, 0f, height / Email.CASE, trait)
    }
}

/**
 * L'image d'un type ([PictoType.Forme]), dans une case de [Email.CASE] unités
 * dont le haut est en [haut], centrée en [cx] ; [u] est la taille d'une unité.
 * Partagée par l'écu TYPE ([PictoType]) et l'index du coin ([IndexJeu]).
 */
internal fun dessiner(canvas: Canvas, p: Paint, forme: PictoType.Forme, cx: Float, haut: Float, u: Float, trait: Int) {
        // Tout tient entre 1,2 et 14,8 unités de la case : 13,6 de haut.
        fun x(v: Float) = cx + v * u
        fun y(v: Float) = haut + v * u
        when (forme) {
            PictoType.Forme.NOM -> {
                // La tête et le buste, séparés d'un filet d'or.
                val bonhomme = Path().apply {
                    addCircle(x(0f), y(4.3f), 3.1f * u, Path.Direction.CW)
                    moveTo(x(-5.6f), y(14.8f))
                    lineTo(x(-5.6f), y(12.2f))
                    cubicTo(x(-5.6f), y(9f), x(-3.2f), y(8.2f), x(0f), y(8.2f))
                    cubicTo(x(3.2f), y(8.2f), x(5.6f), y(9f), x(5.6f), y(12.2f))
                    lineTo(x(5.6f), y(14.8f))
                    close()
                }
                Email.peindre(canvas, p, bonhomme, PictoType.ROUGE, trait, u)
            }
            PictoType.Forme.VERBE -> {
                // Large : à 1,8 unité d'épaisseur, l'éclair se lisait comme un trait.
                val eclair = Path().apply {
                    moveTo(x(0.4f), y(1.2f))
                    lineTo(x(-5f), y(9.2f))
                    lineTo(x(-0.6f), y(9.2f))
                    lineTo(x(-2.6f), y(14.8f))
                    lineTo(x(5f), y(6.2f))
                    lineTo(x(0.8f), y(6.2f))
                    lineTo(x(3.8f), y(1.2f))
                    close()
                }
                Email.peindre(canvas, p, eclair, PictoType.ORANGE, trait, u)
            }
            PictoType.Forme.ADJECTIF -> {
                val bouclier = Path().apply {
                    moveTo(x(-5.4f), y(1.6f))
                    lineTo(x(5.4f), y(1.6f))
                    lineTo(x(5.4f), y(7.4f))
                    cubicTo(x(5.4f), y(11.2f), x(2.6f), y(13.4f), x(0f), y(14.8f))
                    cubicTo(x(-2.6f), y(13.4f), x(-5.4f), y(11.2f), x(-5.4f), y(7.4f))
                    close()
                }
                Email.peindre(canvas, p, bouclier, PictoType.BLEU, trait, u)
                // Le « + » blanc du renfort.
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                p.strokeWidth = 1.7f * u
                p.color = Color.WHITE
                canvas.drawLine(x(-2.6f), y(7.4f), x(2.6f), y(7.4f), p)
                canvas.drawLine(x(0f), y(4.8f), x(0f), y(10f), p)
                p.strokeCap = Paint.Cap.BUTT
                p.style = Paint.Style.FILL
            }
            PictoType.Forme.NOMBRE -> {
                // Un badge émaillé qui porte « 123 ». Une pièce « +1 » disait
                // ce que le nombre rapporte, mais se confondait avec les
                // pièces du coût, juste à côté (propriétaire, 2026-10-27).
                val badge = Path().apply {
                    addRoundRect(x(-7f), y(2.6f), x(7f), y(13.4f), 2.8f * u, 2.8f * u, Path.Direction.CW)
                }
                Email.peindre(canvas, p, badge, PictoType.VERT, trait, u)
                p.typeface = Typeface.DEFAULT_BOLD
                p.textAlign = Paint.Align.CENTER
                p.textSize = 7.4f * u
                p.color = Color.WHITE
                canvas.drawText("123", cx, y(8f) + 7.4f * u * 0.36f, p)
            }
        }
}

/**
 * Le coût d'une carte imprimée, dans l'écu COÛT : une **jauge de quatre places
 * de pièce**, dont autant sont remplies d'une pièce d'or que la carte coûte
 * (`ImpressionCarte.cout`, 0 à 4), de gauche à droite.
 *
 * Quatre places sur toutes les cartes (proposition du propriétaire,
 * 2026-10-27) : le coût est toujours au même endroit, une jauge pleine se lit
 * « cher » d'un coup d'œil, le maximum se voit, et le coût nul n'a plus besoin
 * de chiffre : ses quatre places sont vides. Jusqu'à quatre, un enfant voit le
 * nombre de pièces sans les compter. Les places vides sont un creux discret,
 * pour qu'on ne les compte pas comme des pièces.
 */
class PictoCout(context: Context, private val cout: Int, private val trait: Int) : View(context) {

    companion object {
        /** Le coût le plus haut de la règle, donc le nombre de places. */
        const val PLACES = 4
        /** Le diamètre d'une pièce, en unités de carte. */
        private const val DIAMETRE = 10.5f
        /** De centre à centre : 2,5 unités d'or entre deux places. */
        private const val PAS = 13f
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ovale = RectF()

    override fun onDraw(canvas: Canvas) {
        val u = height / Email.CASE
        val cy = 8f * u
        val r = DIAMETRE / 2f * u
        val depart = width / 2f - (PLACES - 1) * PAS * u / 2f
        for (k in 0 until PLACES) {
            val x = depart + k * PAS * u
            if (k < cout) piece(canvas, p, x, cy, r, trait, u) else place(canvas, x, cy, r, u)
        }
    }

    private fun place(c: Canvas, x: Float, y: Float, r: Float, u: Float) = placeVide(c, p, ovale, x, y, r, u)
}

/** Une place de pièce vide : le creux d'une pièce, son ombre en haut, sa lèvre claire en bas. */
internal fun placeVide(c: Canvas, p: Paint, ovale: RectF, x: Float, y: Float, r: Float, u: Float) {
        ovale.set(x - r, y - r, x + r, y + r)
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = 0x24000000
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.8f * u
        p.color = 0x59000000
        c.drawArc(ovale, 190f, 160f, false, p)
        p.color = 0x99FFFFFF.toInt()
        c.drawArc(ovale, 10f, 160f, false, p)
        p.style = Paint.Style.FILL
}

/**
 * La rareté d'une carte imprimée, sur la ligne du bas : une **jauge de cinq
 * places d'étoile**, dont autant sont remplies que le palier le dit (★ commun,
 * ★★ peu commun, ★★★ rare, ★★★★ très rare, ★★★★★ l'ultra rare).
 *
 * Même principe que la jauge du coût ([PictoCout], propriétaire,
 * 2026-10-27) : le maximum se voit, et cinq étoiles sont le maximum que tout
 * le monde connaît. La cinquième place, vide sur presque toutes les cartes,
 * dit qu'il existe plus rare encore. Étoile pleine : l'émail d'or des pièces ;
 * étoile vide : un léger creux dans le métal. Calée à droite de la ligne, à la
 * même place sur toutes les cartes ; le mot du palier vient juste avant.
 */
class EtoilesRarete(context: Context, private val nombre: Int, private val trait: Int) : View(context) {

    companion object {
        const val PLACES = 5
        /** La largeur d'une place, en unités de carte. */
        const val PAS = 8.2f
        /** La hauteur de la ligne du bas, en unités de carte (Ornement.SERIE_D). */
        private const val HAUTEUR = 9.5f
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val u = height / HAUTEUR
        val pas = width / PLACES.toFloat()
        val rayon = minOf(pas * 0.48f, height * 0.5f)
        val cy = height / 2f
        for (k in 0 until PLACES) {
            val etoile = etoile(pas * (k + 0.5f), cy + rayon * 0.06f, rayon, rayon * 0.42f)
            if (k < nombre) Email.peindre(canvas, p, etoile, 0xFFF2B705.toInt(), trait, u) else creux(canvas, etoile, u)
        }
    }

    /** Une place vide : le creux d'une étoile, sa lèvre claire en bas. */
    private fun creux(c: Canvas, chemin: Path, u: Float) {
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = 0x33000000
        c.drawPath(chemin, p)
        p.style = Paint.Style.STROKE
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = 0.5f * u
        p.color = 0x99FFFFFF.toInt()
        c.save()
        c.translate(0f, 0.45f * u)
        c.drawPath(chemin, p)
        c.restore()
        p.color = 0x59000000
        p.strokeWidth = 0.45f * u
        c.drawPath(chemin, p)
        p.style = Paint.Style.FILL
    }

    private fun etoile(cx: Float, cy: Float, rayon: Float, creux: Float): Path = Path().apply {
        for (k in 0 until 10) {
            val a = k * Math.PI.toFloat() / 5f - Math.PI.toFloat() / 2f
            val r = if (k % 2 == 0) rayon else creux
            val x = cx + kotlin.math.cos(a) * r
            val y = cy + kotlin.math.sin(a) * r
            if (k == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

/**
 * L'index du coin de la carte imprimée : une languette du métal de la carte,
 * suspendue sous la gemme de Force, qui répète en petit le type (son image)
 * et le coût (une jauge verticale de quatre places).
 *
 * C'est l'index des cartes à jouer. Tenue en éventail, une carte recouverte
 * par la suivante ne montre qu'une bande d'environ 49 unités à gauche : la
 * planche de contrôle l'a montré (2026-10-27), on n'y voyait que la Force,
 * pas ce que la carte coûte ni ce qu'elle fait. Les cartes de football à
 * collectionner rangent leurs chiffres le long de ce bord pour la même raison.
 * Les écus du bas restent la référence, en grand ; l'index sert la main.
 */
class IndexJeu(
    context: Context,
    private val forme: PictoType.Forme?,
    private val cout: Int,
    private val metal: Ornement.Metal,
    /** Le palier de rareté, 0 à 3 : il choisit la chaîne ([chaine]). */
    private val palier: Int = 0
) : View(context) {

    companion object {
        /**
         * La place de la vue, en unités de carte : la chaîne qui part sous
         * la gemme de Force (11–65), la languette (27–49 × 77–125) et
         * autour d'elle la marge de son ombre portée.
         */
        val CADRE = RectF(25f, 58f, 54f, 130f)

        /** La languette dans la vue, en unités de carte. */
        private val LANGUETTE = RectF(2f, 19f, 24f, 67f)

        /** Le haut de la chaîne dans la vue, juste sous le bord de la gemme. */
        private const val CHAINE_HAUT = 5.5f
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ovale = RectF()
    private val cadre = RectF()

    init {
        // Le flou de l'ombre ne se dessine qu'en logiciel.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        val u = width / CADRE.width()
        cadre.set(LANGUETTE.left * u, LANGUETTE.top * u, LANGUETTE.right * u, LANGUETTE.bottom * u)
        val rayon = 3f * u
        val cx = cadre.centerX()
        chaine(canvas, cx, CHAINE_HAUT * u, cadre.top + 1.5f * u, u)
        // Posée sur la carte, pas creusée dedans (propriétaire, 2026-10-31) :
        // une ombre portée en bas à droite, la lumière venant d'en haut à
        // gauche comme pour le reste du cadre, et un métal bombé.
        p.style = Paint.Style.FILL
        p.color = 0x99000000.toInt()
        p.maskFilter = BlurMaskFilter(1.8f * u, BlurMaskFilter.Blur.NORMAL)
        canvas.drawRoundRect(
            RectF(cadre.left + 1.8f * u, cadre.top + 2.8f * u, cadre.right + 1.8f * u, cadre.bottom + 2.8f * u),
            rayon, rayon, p
        )
        p.maskFilter = null
        // Le bombé : clair au tiers gauche, sombre au bord droit.
        p.shader = LinearGradient(
            cadre.left, 0f, cadre.right, 0f,
            intArrayOf(metal.mid, metal.hi, metal.mid, metal.lo), floatArrayOf(0f, 0.3f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        p.color = Color.BLACK
        canvas.drawRoundRect(cadre, rayon, rayon, p)
        // Le haut prend la lumière, le pied passe dans l'ombre.
        p.shader = LinearGradient(
            0f, cadre.top, 0f, cadre.bottom,
            intArrayOf(0x55FFFFFF, 0x00FFFFFF, 0x00000000, 0x40000000), floatArrayOf(0f, 0.25f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(cadre, rayon, rayon, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.1f * u
        p.color = metal.trait
        canvas.drawRoundRect(cadre, rayon, rayon, p)
        // Le liseré clair du bord éclairé, en haut et à gauche seulement.
        p.strokeWidth = 0.7f * u
        p.color = 0xB0FFFFFF.toInt()
        val arete = Path().apply {
            moveTo(cadre.left + 1.3f * u, cadre.bottom - rayon)
            lineTo(cadre.left + 1.3f * u, cadre.top + rayon)
            quadTo(cadre.left + 1.3f * u, cadre.top + 1.3f * u, cadre.left + rayon, cadre.top + 1.3f * u)
            lineTo(cadre.right - rayon, cadre.top + 1.3f * u)
        }
        canvas.drawPath(arete, p)
        p.style = Paint.Style.FILL
        // Le type, aux quatre cinquièmes de sa taille dans l'écu.
        forme?.let { dessiner(canvas, p, it, cx, cadre.top + 2.6f * u, 0.8f * u, metal.trait) }
        // Le coût : quatre places de haut en bas, remplies d'autant de pièces.
        val r = 3f * u
        for (k in 0 until PictoCout.PLACES) {
            val y = cadre.top + (19f + k * 7.3f) * u
            if (k < cout) piece(canvas, p, cx, y, r, metal.trait, u) else placeVide(canvas, p, ovale, cx, y, r, u)
        }
    }


    /** La perle de l'or, au milieu de sa chaîne. */
    private fun perle(canvas: Canvas, cx: Float, y: Float, u: Float) {
        val r = 2.6f * u
        p.color = metal.trait
        canvas.drawCircle(cx, y, r + 0.6f * u, p)
        p.shader = android.graphics.RadialGradient(
            cx - 0.6f * r, y - 0.6f * r, 1.6f * r,
            intArrayOf(Color.WHITE, metal.joyau, metal.trait), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, y, r, p)
        p.shader = null
    }

    /**
     * La chaîne qui suspend la languette à la gemme, de la noblesse du palier
     * (propriétaire, 2026-10-31) : son métal est déjà celui de la carte, sa
     * façon dit en plus le rang. Le fer est un seul gros anneau de forge ; le
     * bronze, deux maillons ; l'argent, trois maillons fins ; l'or, deux
     * maillons fins de part et d'autre d'une perle de la couleur du joyau.
     *
     * Les maillons alternent de face (un anneau ovale) et de profil (une
     * barre) : c'est ce qui fait lire une chaîne et non des perles enfilées.
     */
    private fun chaine(canvas: Canvas, cx: Float, haut: Float, bas: Float, u: Float) {
        val maillons = intArrayOf(1, 2, 3, 3)[palier.coerceIn(0, 3)]
        val fil = floatArrayOf(1.7f, 1.35f, 1.05f, 0.95f)[palier.coerceIn(0, 3)] * u
        val pas = (bas - haut) / maillons
        val ovale = RectF()
        for (k in 0 until maillons) {
            val y0 = haut + k * pas - 0.35f * pas
            val y1 = haut + (k + 1) * pas + 0.35f * pas
            // La perle de l'or prend la place du maillon du milieu.
            if (palier >= 3 && k == 1) continue
            val deFace = k % 2 == (maillons - 1) % 2
            val demi = if (deFace) 2.4f * u + fil / 2f else fil * 0.75f
            ovale.set(cx - demi, y0, cx + demi, y1)
            p.style = Paint.Style.STROKE
            // L'ombre du maillon sur la carte, puis le contour, puis le métal.
            p.strokeWidth = fil + 0.9f * u
            p.color = metal.trait
            if (deFace) canvas.drawOval(ovale, p)
            else canvas.drawRoundRect(ovale, demi, demi, p)
            p.strokeWidth = fil
            p.shader = LinearGradient(
                ovale.left, ovale.top, ovale.right, ovale.bottom,
                intArrayOf(metal.hi, metal.mid, metal.lo), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
            )
            if (deFace) canvas.drawOval(ovale, p)
            else canvas.drawRoundRect(ovale, demi, demi, p)
            p.shader = null
        }
        p.style = Paint.Style.FILL
        if (palier >= 3) perle(canvas, cx, haut + 1.5f * pas, u)
    }
}

/**
 * Un nombre gravé dans un écu de la carte de l'appli (l'écu VUES) : une lèvre
 * claire sous un creux du ton sombre du métal, comme le nom dans la plaque.
 * En blanc, comme avant, il se lisait mal sur l'or clair : c'est la leçon du
 * premier BAT de la carte imprimée (2026-10-26), reprise ici le 2026-10-27.
 */
class ChiffreGrave(context: Context, private val texte: String, private val metal: Ornement.Metal) : View(context) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        val u = height / Email.CASE
        val taille = 12.5f * u
        p.textSize = taille
        val dispo = width - 8f * u
        if (p.measureText(texte) > dispo) p.textSize = taille * dispo / p.measureText(texte)
        val base = 7.6f * u + p.textSize * 0.36f
        p.color = metal.hi
        canvas.drawText(texte, width / 2f, base + 0.7f * u, p)
        p.color = metal.trait
        canvas.drawText(texte, width / 2f, base, p)
    }
}

/**
 * Le niveau d'une carte de l'appli, dans l'écu NIVEAU : une jauge de six
 * cases, une par casier de la Boîte de Leitner. Une case se remplit à chaque
 * casier franchi : vide pour une carte neuve, les six pleines quand le mot
 * est acquis. Remplir jusqu'au casier courant aurait rempli la jauge dès le
 * dernier casier, et une carte acquise n'aurait pas eu l'air différente.
 *
 * L'idée vient de la jauge de pièces de la carte imprimée (propriétaire,
 * 2026-10-27) : le chiffre « 3 » ne disait pas sur combien, la jauge montre
 * le chemin parcouru et ce qui reste. Des cases plutôt que des ronds : ce
 * sont les casiers de la boîte. Remplies de l'or des étoiles de rareté, vides
 * en creux dans le métal.
 */
class JaugeNiveau(context: Context, private val remplies: Int, private val trait: Int) : View(context) {

    companion object {
        /** Autant de cases que de casiers : Widderhuelen.BOITE_ACQUISE. */
        val CASES = Widderhuelen.BOITE_ACQUISE
        private const val COTE = 4.6f
        private const val PAS = 6f
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val carre = RectF()

    override fun onDraw(canvas: Canvas) {
        val u = height / Email.CASE
        // Centrée une unité au-dessus du milieu de la case : plus bas, les
        // cases des bords mordaient sur l'arrondi de l'écu de l'appli.
        val cy = 7f * u
        val depart = width / 2f - (CASES - 1) * PAS * u / 2f
        val demi = COTE / 2f * u
        for (k in 0 until CASES) {
            val x = depart + k * PAS * u
            carre.set(x - demi, cy - demi, x + demi, cy + demi)
            val chemin = Path().apply { addRoundRect(carre, 1f * u, 1f * u, Path.Direction.CW) }
            if (k < remplies) Email.peindre(canvas, p, chemin, 0xFFF2B705.toInt(), trait, u)
            else {
                p.shader = null
                p.style = Paint.Style.FILL
                p.color = 0x33000000
                canvas.drawPath(chemin, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 0.5f * u
                p.color = 0x99FFFFFF.toInt()
                canvas.save()
                canvas.translate(0f, 0.4f * u)
                canvas.drawPath(chemin, p)
                canvas.restore()
                p.color = 0x59000000
                canvas.drawPath(chemin, p)
                p.style = Paint.Style.FILL
            }
        }
    }
}
