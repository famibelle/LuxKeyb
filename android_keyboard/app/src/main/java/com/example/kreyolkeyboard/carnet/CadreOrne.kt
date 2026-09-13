package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.LruCache
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Le cadre d'une carte du carnet, et **l'échelle d'ornement** qui le fait
 * grandir avec la rareté.
 *
 * ## Pourquoi une échelle, et pas quatre couleurs
 *
 * Le carnet distinguait déjà ses paliers par la matière de l'illustration :
 * une commune mate, un grain oblique, un halo, une irisation. C'était juste,
 * mais ça se jouait entièrement **dans** le panneau, sur 130 dp de hauteur.
 * Une carte à collectionner se reconnaît d'abord à son cadre, et un cadre
 * identique pour les quatre paliers annulait la moitié du travail.
 *
 * Ici, chaque palier **hérite de tout le précédent** et ajoute des éléments
 * nommés. C'est une liste, pas une impression, et c'est ce qui rend l'écart
 * lisible même quand deux cartes ne sont pas côte à côte :
 *
 * - **Commun** — cadre d'étain, ouverture rectangulaire, plaque de nom,
 *   gemme de coût, panneau de texte, écus. Volontairement nu : sans commune
 *   nue, aucun des trois autres paliers ne se verrait.
 * - **Peu commun** — bronze, rivets sertis, filet clair sur l'ouverture.
 * - **Rare** — argent, ouverture **en arche**, rayons en éventail, volutes
 *   aux quatre angles, bandeaux à pointes, couronne de griffes, double filet.
 * - **Très rare** — or, clef de voûte sertie, huit volutes, feuilles
 *   d'acanthe sur les flancs, joyaux satellites, semis d'étincelles, et un
 *   reflet spéculaire qui suit l'inclinaison du téléphone.
 *
 * ## Ce que l'ornement n'a pas le droit de faire
 *
 * Il ne dit rien de neuf. Toute l'information reste celle que
 * [ContenuCarte] portait déjà, et la **teinte du mot** garde ce qui lui
 * appartient — la face intérieure et la gemme de coût. Le métal encadre, il
 * ne recouvre pas. C'est la seule raison pour laquelle deux échelles de
 * couleur peuvent cohabiter sans que la collection cesse d'être variée.
 *
 * ## Le coût, et comment il est payé
 *
 * Une commune demande une trentaine d'ordres de tracé, une très rare près de
 * trois cents. À raison d'un `onDraw` par vignette et de deux colonnes qui
 * défilent, ce serait intenable. Le métal est donc **rendu une fois dans un
 * bitmap mis en cache** ([metal]), partagé par toutes les cartes d'un même
 * palier et d'une même taille : il ne dépend pas du mot. Seuls la face, la
 * gemme, l'illustration et les étincelles se retracent, et ils sont
 * bon marché.
 */
object Ornement {

    /** La carte est dessinée en unités de carte, puis mise à l'échelle. */
    const val LARGEUR = 300f
    const val HAUTEUR = 440f
    const val HAUTEUR_VIGNETTE = 300f

    /**
     * Le rayon des coins, en unités de carte.
     *
     * Il était écrit en clair aux trois endroits qui tracent le bord, et
     * [Booster] en avait un quatrième, en dp, qui ne lui correspondait pas.
     * Un dos et une face qui ne s'arrondissent pas pareil se retournent comme
     * deux objets qui se remplacent, pas comme un carton.
     */
    const val RAYON = 18f

    /**
     * Un palier, un alliage.
     *
     * C'est la convention de tous les jeux de collection, et c'est ce qui se
     * reconnaît à travers la pièce sans lire une étiquette. Les quatre
     * alliages montent en clarté et en chaleur : l'étain est froid et sourd,
     * l'or est chaud et lumineux.
     */
    class Metal(val hi: Int, val mid: Int, val lo: Int, val trait: Int, val joyau: Int)

    private val METAUX = arrayOf(
        Metal(0xFFB9C0C4.toInt(), 0xFF7E878C.toInt(), 0xFF454C50.toInt(), 0xFF2E3437.toInt(), 0xFF9AA6AD.toInt()),
        Metal(0xFFE3B475.toInt(), 0xFFB07C3C.toInt(), 0xFF68441A.toInt(), 0xFF3E2910.toInt(), 0xFF5BB55F.toInt()),
        Metal(0xFFF2F6F9.toInt(), 0xFFB9C4CE.toInt(), 0xFF6E7C88.toInt(), 0xFF3D474F.toInt(), 0xFF3D9BF0.toInt()),
        Metal(0xFFFFF0BC.toInt(), 0xFFE0B74E.toInt(), 0xFF8E6216.toInt(), 0xFF4A3208.toInt(), 0xFFC558E8.toInt())
    )

    fun metal(rarete: Rarete): Metal = METAUX[rarete.ordinal]

    // ---------------------------------------------------------------- slots

    /**
     * Les emplacements, en unités de carte.
     *
     * Ils sont ici et nulle part ailleurs : le tracé du cadre et la pose du
     * texte doivent lire les mêmes nombres, sinon un bandeau finit décalé
     * d'un pixel sous son libellé et personne ne comprend pourquoi.
     */
    val FENETRE = RectF(32f, 74f, 268f, 250f)
    val FENETRE_VIGNETTE = RectF(26f, 26f, 274f, 212f)
    val PLAQUE = RectF(62f, 18f, 276f, 58f)
    val GEMME = RectF(7f, 11f, 61f, 65f)
    val TYPE = RectF(46f, 256f, 254f, 284f)
    val PANNEAU = RectF(38f, 289f, 262f, 384f)
    /** Le texte, en retrait du panneau : le double filet passe entre les deux. */
    val PANNEAU_TEXTE = RectF(48f, 297f, 252f, 378f)
    val ECU_G = RectF(26f, 387f, 78f, 419f)
    val ECU_D = RectF(222f, 387f, 274f, 419f)
    /** Le chiffre d'un écu : sous le libellé gravé, pas par-dessus. */
    val ECU_G_TEXTE = RectF(26f, 395f, 78f, 417f)
    val ECU_D_TEXTE = RectF(222f, 395f, 274f, 417f)
    val SERIE_G = RectF(30f, 421f, 176f, 436f)
    val SERIE_D = RectF(176f, 421f, 270f, 436f)
    val NOM_VIGNETTE = RectF(20f, 218f, 280f, 248f)
    val GLOSE_VIGNETTE = RectF(20f, 249f, 280f, 269f)
    val BOITE_VIGNETTE = RectF(30f, 277f, 270f, 281f)

    // ----------------------------------------------------------- primitives

    /**
     * Une volute : spirale logarithmique dont le trait s'affine en
     * s'enroulant. Une courbe paramétrée plutôt qu'un chemin recopié — c'est
     * ce qui permet de la redimensionner sans qu'elle s'épaississe.
     */
    private fun volute(
        c: Canvas, p: Paint, x: Float, y: Float, taille: Float,
        sx: Float, sy: Float, tours: Float, epais: Float, couleur: Int
    ) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.color = couleur
        p.shader = null
        var px = taille
        var py = 0f
        val n = 40
        for (i in 1..n) {
            val t = i / n.toFloat()
            val ang = t * tours * 2f * Math.PI.toFloat()
            val r = taille * exp(-1.75f * t)
            val nx = cos(ang) * r
            val ny = sin(ang) * r
            p.strokeWidth = epais * (1f - t * 0.8f)
            c.drawLine(x + px * sx, y + py * sy, x + nx * sx, y + ny * sy, p)
            px = nx
            py = ny
        }
    }

    /** Une feuille d'acanthe : une goutte, posée le long des filets. */
    private fun feuille(c: Canvas, p: Paint, x: Float, y: Float, l: Float, angle: Float, couleur: Int) {
        c.save()
        c.translate(x, y)
        c.rotate(angle)
        p.style = Paint.Style.FILL
        p.color = couleur
        p.shader = null
        val chemin = Path()
        chemin.moveTo(0f, 0f)
        chemin.quadTo(l * 0.45f, -l * 0.38f, l, 0f)
        chemin.quadTo(l * 0.45f, l * 0.38f, 0f, 0f)
        c.drawPath(chemin, p)
        c.restore()
    }

    /** Un joyau serti : facette claire en haut à gauche, creux sombre en bas. */
    private fun joyau(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float, couleur: Int, facettes: Int) {
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            cx - r * 0.35f, cy - r * 0.4f, r * 1.35f,
            intArrayOf(0xF2FFFFFF.toInt(), couleur, 0x8C000000.toInt()),
            floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP
        )
        if (facettes >= 3) {
            val chemin = Path()
            for (i in 0 until facettes) {
                val a = -Math.PI.toFloat() / 2f + i.toFloat() / facettes * 2f * Math.PI.toFloat()
                val px = cx + cos(a) * r
                val py = cy + sin(a) * r
                if (i == 0) chemin.moveTo(px, py) else chemin.lineTo(px, py)
            }
            chemin.close()
            c.drawPath(chemin, p)
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = r * 0.14f
            p.color = 0x8CFFFFFF.toInt()
            c.drawPath(chemin, p)
        } else {
            c.drawCircle(cx, cy, r, p)
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = r * 0.14f
            p.color = 0x8CFFFFFF.toInt()
            c.drawCircle(cx, cy, r, p)
        }
        p.shader = null
        p.style = Paint.Style.FILL
    }

    /** Une étincelle à quatre branches : deux courbes en losange étiré. */
    private fun etincelle(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float, alpha: Int) {
        p.style = Paint.Style.FILL
        p.shader = null
        p.color = Color.WHITE
        p.alpha = alpha
        val chemin = Path()
        chemin.moveTo(cx, cy - r)
        chemin.quadTo(cx + r * 0.16f, cy - r * 0.16f, cx + r, cy)
        chemin.quadTo(cx + r * 0.16f, cy + r * 0.16f, cx, cy + r)
        chemin.quadTo(cx - r * 0.16f, cy + r * 0.16f, cx - r, cy)
        chemin.quadTo(cx - r * 0.16f, cy - r * 0.16f, cx, cy - r)
        c.drawPath(chemin, p)
        p.alpha = 255
    }

    /**
     * Un bandeau : plat, à pointes, selon le palier.
     *
     * Les pointes n'arrivent qu'à *Rare*. C'est le genre de détail qui ne se
     * remarque jamais seul et qui fait toute la différence en série : quatre
     * cartes alignées, deux à bords droits et deux à pointes, et l'échelle se
     * lit sans lire un mot.
     */
    private fun bandeau(c: Canvas, p: Paint, r: RectF, pointes: Boolean, m: Metal) {
        val chemin = Path()
        if (pointes) {
            val q = r.height() * 0.55f
            chemin.moveTo(r.left - q, r.centerY())
            chemin.lineTo(r.left, r.top)
            chemin.lineTo(r.right, r.top)
            chemin.lineTo(r.right + q, r.centerY())
            chemin.lineTo(r.right, r.bottom)
            chemin.lineTo(r.left, r.bottom)
            chemin.close()
        } else {
            chemin.addRoundRect(r, 3f, 3f, Path.Direction.CW)
        }
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            0f, r.top, 0f, r.bottom,
            intArrayOf(m.hi, m.mid, m.lo), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.drawPath(chemin, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f
        p.color = m.trait
        c.drawPath(chemin, p)
    }

    /** L'écu d'une statistique : un blason à base arrondie. */
    private fun ecu(c: Canvas, p: Paint, r: RectF, m: Metal, filet: Boolean) {
        val chemin = Path()
        chemin.moveTo(r.left, r.top)
        chemin.lineTo(r.right, r.top)
        chemin.lineTo(r.right, r.top + r.height() * 0.5f)
        chemin.quadTo(r.centerX(), r.bottom + r.height() * 0.16f, r.left, r.top + r.height() * 0.5f)
        chemin.close()
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            0f, r.top, 0f, r.bottom,
            intArrayOf(m.hi, m.mid, m.lo), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        c.drawPath(chemin, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.4f
        p.color = m.trait
        c.drawPath(chemin, p)
        if (filet) {
            p.strokeWidth = 0.9f
            p.color = 0x80FFFFFF.toInt()
            c.drawPath(chemin, p)
        }
    }

    /**
     * L'ouverture de l'illustration : rectangle arrondi, ou plein cintre.
     *
     * L'arche est l'ajout le plus visible de *Rare*, et c'est voulu : c'est
     * la seule modification qui change la **silhouette** de la carte plutôt
     * que d'ajouter un détail à sa surface.
     */
    fun cheminFenetre(r: RectF, arche: Boolean): Path {
        val chemin = Path()
        if (!arche) {
            chemin.addRoundRect(r, 8f, 8f, Path.Direction.CW)
            return chemin
        }
        val fleche = r.width() * 0.30f
        val rayon = 8f
        chemin.moveTo(r.left, r.bottom - rayon)
        chemin.lineTo(r.left, r.top + fleche)
        chemin.arcTo(RectF(r.left, r.top, r.right, r.top + fleche * 2f), 180f, 180f)
        chemin.lineTo(r.right, r.bottom - rayon)
        chemin.arcTo(RectF(r.right - rayon * 2f, r.bottom - rayon * 2f, r.right, r.bottom), 0f, 90f)
        chemin.lineTo(r.left + rayon, r.bottom)
        chemin.arcTo(RectF(r.left, r.bottom - rayon * 2f, r.left + rayon * 2f, r.bottom), 90f, 90f)
        chemin.close()
        return chemin
    }

    // ------------------------------------------------------------- le métal

    /**
     * Le cache des cadres.
     *
     * La clef ne contient **pas le mot** : c'est tout l'intérêt. Le métal ne
     * dépend que du palier et de la taille, donc trente cartes très rares
     * partagent un seul bitmap. Deux mégaoctets suffisent largement pour les
     * huit combinaisons que l'application demande réellement.
     */
    private val CACHE = object : LruCache<String, Bitmap>(2 * 1024 * 1024) {
        override fun sizeOf(cle: String, valeur: Bitmap): Int = valeur.byteCount
    }

    fun metal(rarete: Rarete, largeurPx: Int, vignette: Boolean): Bitmap? =
        couche("m", rarete, largeurPx, vignette) { c -> dessinerMetal(c, rarete, vignette) }

    /**
     * Les rayons en éventail, à partir de *Rare*.
     *
     * Ils passent **sous** l'illustration, donc ils ne peuvent pas voyager
     * dans le bitmap du métal — mais ils ne dépendent pas davantage du mot,
     * et vingt-quatre secteurs retracés à chaque trame pour chacune des
     * trente vignettes d'une grille qui défile étaient de loin le premier
     * poste de dépense. D'où une seconde couche, mise en cache de la même
     * façon.
     */
    fun rayons(rarete: Rarete, largeurPx: Int, vignette: Boolean): Bitmap? {
        if (rarete.ordinal < 2) return null
        return couche("r", rarete, largeurPx, vignette) { c -> dessinerRayons(c, rarete, vignette) }
    }

    private fun couche(
        prefixe: String,
        rarete: Rarete,
        largeurPx: Int,
        vignette: Boolean,
        tracer: (Canvas) -> Unit
    ): Bitmap? {
        if (largeurPx <= 0) return null
        val cle = "${prefixe}_${rarete.ordinal}_${largeurPx}_$vignette"
        CACHE.get(cle)?.let { if (!it.isRecycled) return it }

        val u = largeurPx / LARGEUR
        val hautUnites = if (vignette) HAUTEUR_VIGNETTE else HAUTEUR
        val hauteurPx = (hautUnites * u).toInt()
        if (hauteurPx <= 0) return null

        val bitmap = Bitmap.createBitmap(largeurPx, hauteurPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.scale(u, u)
        tracer(c)
        CACHE.put(cle, bitmap)
        return bitmap
    }

    /**
     * Tout ce qui ne dépend que du palier : le plateau, la sertissure de
     * l'ouverture, les volutes, les bandeaux, les écus, les joyaux.
     *
     * Le centre reste transparent — la face teintée et l'illustration sont
     * peintes dessous, en direct, par [CarteOrnee].
     */
    private fun dessinerMetal(c: Canvas, rarete: Rarete, vignette: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val palier = rarete.ordinal
        val m = metal(rarete)
        val haut = if (vignette) HAUTEUR_VIGNETTE else HAUTEUR
        val bord = 12f + palier * 2f
        val fenetre = if (vignette) FENETRE_VIGNETTE else FENETRE
        val arche = palier >= 2

        // 1. Le plateau : le métal du palier, sur tout le pourtour.
        val plateau = Path()
        plateau.addRoundRect(RectF(0f, 0f, LARGEUR, haut), 18f, 18f, Path.Direction.CW)
        val creux = Path()
        creux.addRoundRect(RectF(bord, bord, LARGEUR - bord, haut - bord), 11f, 11f, Path.Direction.CW)
        plateau.op(creux, Path.Op.DIFFERENCE)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            0f, 0f, LARGEUR * 0.6f, haut,
            intArrayOf(m.hi, m.mid, m.lo, m.mid),
            floatArrayOf(0f, 0.28f, 0.62f, 1f), Shader.TileMode.CLAMP
        )
        c.drawPath(plateau, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.5f
        p.color = m.trait
        c.drawRoundRect(RectF(1.2f, 1.2f, LARGEUR - 1.2f, haut - 1.2f), RAYON, RAYON, p)

        // 2. La sertissure de l'ouverture : un jonc, doublé d'un filet clair
        //    à partir de Peu commun.
        val ouverture = cheminFenetre(fenetre, arche)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f + palier
        p.color = m.mid
        c.drawPath(ouverture, p)
        p.strokeWidth = 1.4f
        p.color = m.trait
        c.drawPath(ouverture, p)
        if (palier >= 1) {
            c.save()
            c.translate(0f, -1.5f)
            p.strokeWidth = 1.2f
            p.color = 0x8CFFFFFF.toInt()
            c.drawPath(ouverture, p)
            c.restore()
        }

        // 3. La clef de voûte, au sommet de l'arche (Très rare).
        if (palier >= 3) {
            val kx = fenetre.centerX()
            val ky = fenetre.top + 2f
            val clef = Path()
            clef.moveTo(kx - 15f, ky + 12f)
            clef.lineTo(kx - 9f, ky - 8f)
            clef.lineTo(kx + 9f, ky - 8f)
            clef.lineTo(kx + 15f, ky + 12f)
            clef.close()
            p.style = Paint.Style.FILL
            p.shader = LinearGradient(0f, ky - 8f, 0f, ky + 12f, m.hi, m.lo, Shader.TileMode.CLAMP)
            c.drawPath(clef, p)
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.2f
            p.color = m.trait
            c.drawPath(clef, p)
            joyau(c, p, kx, ky + 2f, 5.5f, m.joyau, 6)
        }

        // 4. Les volutes d'angle : aucune, puis deux, quatre, huit.
        val volutes = intArrayOf(0, 2, 4, 8)[palier]
        if (volutes > 0) {
            val taille = 13f + palier * 3f
            val coins = arrayOf(
                floatArrayOf(bord + 5f, bord + 5f, 1f, 1f),
                floatArrayOf(LARGEUR - bord - 5f, bord + 5f, -1f, 1f),
                floatArrayOf(bord + 5f, haut - bord - 5f, 1f, -1f),
                floatArrayOf(LARGEUR - bord - 5f, haut - bord - 5f, -1f, -1f)
            )
            for (i in 0 until volutes) {
                val coin = coins[i % 4]
                val x = coin[0]
                val y = coin[1]
                val sx = coin[2]
                val sy = coin[3]
                // Au-delà de quatre, la seconde volute d'un angle se pose en
                // retrait sur le flanc : deux spirales concentriques feraient
                // une tache, deux spirales décalées font une frise.
                val seconde = i >= 4
                val dx = if (seconde) sx * 16f else 0f
                val dy = if (seconde) sy * 3f else 0f
                val t = if (seconde) taille * 0.6f else taille
                volute(c, p, x + dx, y + dy, t, sx, sy, 1.35f, 3.2f, m.hi)
                volute(c, p, x + dx, y + dy + 1f, t, sx, sy, 1.35f, 1.4f, m.trait)
            }
        }

        // 5. Les feuilles d'acanthe sur les flancs (Très rare).
        if (palier >= 3) {
            val depart = fenetre.top + 30f
            val pas = (haut - depart - 80f) / 5f
            for (i in 0 until 5) {
                val y = depart + i * pas
                feuille(c, p, bord + 2f, y, 9f, -28f, m.hi)
                feuille(c, p, LARGEUR - bord - 2f, y, 9f, 208f, m.hi)
            }
        }

        // 6. Les rivets, au milieu des flancs — pas dans les angles, les
        //    volutes y sont déjà.
        if (palier >= 1) {
            for (y in floatArrayOf(haut * 0.42f, haut * 0.70f)) {
                joyau(c, p, bord + 6f, y, 3.2f, m.hi, 0)
                joyau(c, p, LARGEUR - bord - 6f, y, 3.2f, m.hi, 0)
            }
        }

        if (vignette) return

        // 7. La plaque de nom.
        bandeau(c, p, PLAQUE, palier >= 3, m)

        // 8. La sertissure de la gemme de coût, en débord sur la plaque.
        val gx = GEMME.centerX()
        val gy = GEMME.centerY()
        val gr = GEMME.width() / 2f
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(gx, gy - gr, gx, gy + gr, m.hi, m.lo, Shader.TileMode.CLAMP)
        c.drawCircle(gx, gy, gr, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.4f
        p.color = m.trait
        c.drawCircle(gx, gy, gr, p)
        if (palier >= 2) {
            // Une couronne de griffes : c'est ce qui fait « serti » plutôt
            // que « posé ».
            for (i in 0 until 8) {
                val a = i / 8f * 2f * Math.PI.toFloat()
                joyau(c, p, gx + cos(a) * gr, gy + sin(a) * gr, 2.4f, m.hi, 0)
            }
        }

        // 9. La ligne de type.
        bandeau(c, p, TYPE, palier >= 2, m)

        // 10. Le panneau de texte.
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            0f, PANNEAU.top, 0f, PANNEAU.bottom,
            0xF7FFFCF2.toInt(), 0xF7F0EADA.toInt(), Shader.TileMode.CLAMP
        )
        c.drawRoundRect(PANNEAU, 7f, 7f, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.4f
        p.color = m.mid
        c.drawRoundRect(PANNEAU, 7f, 7f, p)
        p.strokeWidth = 1f
        p.color = m.trait
        c.drawRoundRect(PANNEAU, 7f, 7f, p)
        if (palier >= 2) {
            // Le double filet de l'encadreur, déjà utilisé par les cartes
            // rares du carnet : de la profondeur sans une couleur de plus.
            p.strokeWidth = 0.8f
            p.color = 0x29000000
            c.drawRoundRect(
                RectF(PANNEAU.left + 4f, PANNEAU.top + 4f, PANNEAU.right - 4f, PANNEAU.bottom - 4f),
                5f, 5f, p
            )
        }

        // 11. Les deux écus, et leur libellé gravé.
        //
        // Sans libellé, deux chiffres nus dans deux blasons ne veulent rien
        // dire — et comme ils ne dépendent pas du mot, ils entrent dans le
        // bitmap mis en cache au même titre que le métal.
        ecu(c, p, ECU_G, m, palier >= 3)
        ecu(c, p, ECU_D, m, palier >= 3)
        p.style = Paint.Style.FILL
        p.shader = null
        p.color = m.trait
        p.alpha = 190
        p.textAlign = Paint.Align.CENTER
        p.textSize = 7.5f
        p.typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD
        )
        c.drawText("VUES", ECU_G.centerX(), ECU_G.top + 9f, p)
        c.drawText("NIVEAU", ECU_D.centerX(), ECU_D.top + 9f, p)
        p.alpha = 255
    }

    // -------------------------------------------------------------- le vif

    /**
     * La face intérieure : la teinte du mot, et rien d'autre.
     *
     * C'est la pièce qui garantit que la collection reste variée. Le métal
     * dit le palier — quatre valeurs possibles — et la face dit le mot, qui
     * en a trois cent soixante.
     */
    fun degradeFace(teinte: Float, rarete: Rarete, vignette: Boolean): LinearGradient {
        val haut = if (vignette) HAUTEUR_VIGNETTE else HAUTEUR
        val bord = 12f + rarete.ordinal * 2f
        return LinearGradient(
            0f, bord, 0f, haut - bord,
            Color.HSVToColor(floatArrayOf(teinte, 0.10f, 0.99f)),
            Color.HSVToColor(floatArrayOf(teinte, 0.22f, 0.88f)),
            Shader.TileMode.CLAMP
        )
    }

    fun dessinerFace(c: Canvas, p: Paint, degrade: LinearGradient, rarete: Rarete, vignette: Boolean) {
        val haut = if (vignette) HAUTEUR_VIGNETTE else HAUTEUR
        val bord = 12f + rarete.ordinal * 2f
        p.style = Paint.Style.FILL
        p.shader = degrade
        c.drawRoundRect(RectF(bord, bord, LARGEUR - bord, haut - bord), 11f, 11f, p)
        p.shader = null
    }

    /** Le tracé des rayons, appelé une seule fois par palier et par taille. */
    private fun dessinerRayons(c: Canvas, rarete: Rarete, vignette: Boolean) {
        val palier = rarete.ordinal
        if (palier < 2) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val haut = if (vignette) HAUTEUR_VIGNETTE else HAUTEUR
        val bord = 12f + palier * 2f
        val fenetre = if (vignette) FENETRE_VIGNETTE else FENETRE
        c.save()
        c.clipPath(Path().apply {
            addRoundRect(RectF(bord, bord, LARGEUR - bord, haut - bord), 11f, 11f, Path.Direction.CW)
        })
        val cx = LARGEUR / 2f
        val cy = fenetre.top + fenetre.height() * 0.45f
        val n = if (palier >= 3) 24 else 16
        p.style = Paint.Style.FILL
        p.shader = null
        p.color = if (palier >= 3) 0x4DFFEEBE else 0x57FFFFFF
        val secteur = 360f / n
        for (i in 0 until n step 2) {
            val chemin = Path()
            chemin.moveTo(cx, cy)
            chemin.arcTo(RectF(cx - 340f, cy - 340f, cx + 340f, cy + 340f), i * secteur, secteur)
            chemin.close()
            c.drawPath(chemin, p)
        }
        c.restore()
    }

    /**
     * La gemme de coût : la teinte du mot, taillée.
     *
     * Elle est en débord sur la plaque de nom, comme la gemme de mana d'une
     * carte de jeu — c'est ce débord qui donne l'impression d'épaisseur.
     */
    fun degradeGemme(teinte: Float): RadialGradient {
        val gx = GEMME.centerX()
        val gy = GEMME.centerY()
        val gr = GEMME.width() / 2f - 4f
        return RadialGradient(
            gx - gr * 0.3f, gy - gr * 0.35f, gr * 1.4f,
            intArrayOf(
                Color.HSVToColor(floatArrayOf(teinte, 0.30f, 1f)),
                Color.HSVToColor(floatArrayOf(teinte, 0.70f, 0.72f)),
                Color.HSVToColor(floatArrayOf(teinte, 0.85f, 0.34f))
            ),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
    }

    fun dessinerGemme(c: Canvas, p: Paint, degrade: RadialGradient) {
        val gx = GEMME.centerX()
        val gy = GEMME.centerY()
        val gr = GEMME.width() / 2f - 4f
        p.style = Paint.Style.FILL
        p.shader = degrade
        c.drawCircle(gx, gy, gr, p)
        p.shader = null
        p.color = 0x80FFFFFF.toInt()
        c.save()
        c.rotate(-28f, gx - gr * 0.28f, gy - gr * 0.38f)
        c.drawOval(
            RectF(
                gx - gr * 0.74f, gy - gr * 0.66f,
                gx + gr * 0.18f, gy - gr * 0.10f
            ), p
        )
        c.restore()
    }

    /** Le joyau de rareté, entre les deux écus. */
    fun dessinerJoyauRarete(c: Canvas, p: Paint, rarete: Rarete) {
        val palier = rarete.ordinal
        val m = metal(rarete)
        joyau(c, p, LARGEUR / 2f, 399f, 8f + palier, rarete.couleur, if (palier >= 2) 6 else 0)
        if (palier < 3) return
        for (s in intArrayOf(-1, 1)) {
            joyau(c, p, LARGEUR / 2f + s * 22f, 399f, 4.5f, m.joyau, 6)
            volute(c, p, LARGEUR / 2f + s * 34f, 399f, 11f, s.toFloat(), 1f, 1.2f, 2f, m.hi)
        }
    }

    /**
     * Le semis d'étincelles d'une très rare.
     *
     * Tiré du mot, donc **toujours le même pour une carte donnée** : deux
     * ouvertures de la même carte montrent les mêmes étincelles aux mêmes
     * endroits, ce qui en fait une propriété de la pièce et non un effet.
     */
    fun dessinerSemis(c: Canvas, p: Paint, mot: String, rarete: Rarete, vignette: Boolean) {
        if (rarete != Rarete.TRES_RARE) return
        val haut = if (vignette) HAUTEUR_VIGNETTE else HAUTEUR
        var g = mot.fold(7919) { acc, ch -> acc * 31 + ch.code }
        val n = if (vignette) 7 else 11
        for (i in 0 until n) {
            g = g * 1103515245 + 12345
            val x = ((g ushr 8) % 1000) / 1000f * LARGEUR
            val y = ((g ushr 18) % 1000) / 1000f * haut
            val r = 2.5f + ((g ushr 4) % 5)
            etincelle(c, p, x, y, r, 90 + ((g ushr 12) % 100))
        }
    }

    /**
     * Le reflet spéculaire qui traverse le métal quand on incline l'appareil.
     *
     * Réservé aux deux paliers hauts, et calé sur le **même roulis** que
     * l'irisation de l'illustration : les deux doivent glisser ensemble,
     * sinon la carte se lit comme deux objets superposés.
     */
    fun dessinerReflet(c: Canvas, p: Paint, rarete: Rarete, roulis: Float, vignette: Boolean) {
        if (!rarete.distinguee) return
        val haut = if (vignette) HAUTEUR_VIGNETTE else HAUTEUR
        refletBalaye(c, p, roulis, haut, if (rarete == Rarete.TRES_RARE) 0x66 else 0x2E)
    }

    /**
     * Le balayage lui-même, sans la question de savoir qui y a droit.
     *
     * Extrait de [dessinerReflet] parce que le dos de révision l'utilise
     * aussi, et qu'il n'a pas de rareté : un dos est le même pour toutes les
     * cartes du paquet, c'est même sa raison d'être. Une seule implémentation,
     * donc, sinon les deux faces d'un même carton finiraient par accrocher la
     * lumière selon deux angles différents.
     */
    fun refletBalaye(c: Canvas, p: Paint, roulis: Float, haut: Float, force: Int) {
        val dx = roulis * LARGEUR * 0.55f
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            dx, haut, LARGEUR + dx, 0f,
            intArrayOf(0x00FFFFFF, (force shl 24) or 0xFFFFFF, 0x00FFFFFF),
            floatArrayOf(0.32f, 0.5f, 0.68f), Shader.TileMode.CLAMP
        )
        c.drawRoundRect(RectF(0f, 0f, LARGEUR, haut), RAYON, RAYON, p)
        p.shader = null
    }

    /** Les six paliers de Leitner, en pastilles, pour la vignette. */
    fun dessinerBoite(c: Canvas, p: Paint, boite: Int) {
        val r = BOITE_VIGNETTE
        val large = (r.width() - 5f * 2f) / Widderhuelen.BOITE_ACQUISE
        p.style = Paint.Style.FILL
        p.shader = null
        for (i in 0 until Widderhuelen.BOITE_ACQUISE) {
            p.color = if (i < boite) Carnet.COULEUR else 0x40000000
            val x = r.left + i * (large + 2f)
            c.drawRoundRect(RectF(x, r.top, x + large, r.bottom), 2f, 2f, p)
        }
    }
}

/**
 * Le carton : ce que les deux faces d'une carte ont en commun.
 *
 * ## Pourquoi un ViewGroup et pas un empilement de LinearLayout
 *
 * La disposition d'une carte de jeu n'est pas un flux : la plaque de nom, la
 * fenêtre et les écus sont **toujours au même endroit**, quelle que soit la
 * longueur de la glose. Un flux vertical ferait descendre les écus quand une
 * phrase du LOD prend trois lignes, et l'échelle d'ornement — qui suppose que
 * le cadre et le texte coïncident au pixel près — s'effondrerait.
 *
 * Les emplacements vivent dans [Ornement] et sont exprimés en unités de carte
 * (300 × 440). La vue les met à l'échelle de sa largeur réelle, ce qui rend la
 * même disposition valable en vignette de 160 dp et en carte ouverte.
 *
 * ## Pourquoi la géométrie a quitté [CarteOrnee]
 *
 * Depuis que la révision retourne ses cartes, une carte a un recto **et** un
 * verso, et l'illusion du retournement ne tient qu'à une condition : que les
 * deux faces soient le même objet vu des deux côtés. Même rectangle, même
 * rapport, même rayon de coin, mêmes unités.
 *
 * Laisser le dos naître ailleurs aurait suffi à tout perdre : [DosDeCarte],
 * écrit pour la pochette, arrondit ses coins à 14 dp fixes quand la face les
 * arrondit à [Ornement.RAYON] unités. Deux faces qui ne s'arrondissent pas
 * pareil se retournent comme deux objets qui se remplacent. La mesure, la mise
 * à l'échelle et la pose sont donc ici, en amont des deux.
 */
abstract class Carton(context: Context) : ViewGroup(context) {

    private val emplacements = ArrayList<RectF>()

    /** La hauteur de ce carton-là, en unités de carte. */
    protected abstract val hauteurUnites: Float

    /**
     * Le carton doit-il rétrécir pour tenir dans la hauteur qu'on lui donne ?
     *
     * Faux partout où la carte vit dans un flux vertical — la grille du
     * carnet, la pochette : là, la largeur commande et la hauteur suit, et
     * c'est le défilement qui absorbe le reste.
     *
     * Vrai en révision, où le carton partage l'écran avec un pavé de touches
     * et doit se contenter de ce qui reste. C'est une option et non la règle
     * parce qu'un carton qui rétrécirait partout rétrécirait aussi dans un
     * `ScrollView`, dont la hauteur proposée ne veut rien dire.
     */
    var ajusteALaHauteur = false

    fun posee(vue: View, ou: RectF): Carton {
        addView(vue)
        emplacements.add(ou)
        return this
    }

    /**
     * Met les corps de texte à l'échelle du carton.
     *
     * Un `TextView` porte dans son `tag` sa taille et sa marge haute **en
     * unités de carte**. C'est la seule façon qu'une même disposition tienne
     * à 160 dp de vignette et à 340 dp de carte ouverte sans qu'il faille
     * écrire deux jeux de tailles et les tenir synchronisés.
     */
    private fun mettreALEchelle(vue: View, u: Float) {
        (vue.tag as? FloatArray)?.let { t ->
            (vue as? TextView)?.let { tv ->
                tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, t[0] * u)
                tv.setPadding(0, (t[1] * u).toInt(), 0, 0)
            }
        }
        if (vue is ViewGroup) {
            for (i in 0 until vue.childCount) mettreALEchelle(vue.getChildAt(i), u)
        }
    }

    override fun onMeasure(largeurSpec: Int, hauteurSpec: Int) {
        var largeur = MeasureSpec.getSize(largeurSpec)
        if (ajusteALaHauteur) {
            val mode = MeasureSpec.getMode(hauteurSpec)
            val plafond = MeasureSpec.getSize(hauteurSpec)
            if (mode != MeasureSpec.UNSPECIFIED && plafond > 0) {
                val tenable = (plafond * Ornement.LARGEUR / hauteurUnites).toInt()
                if (tenable < largeur) largeur = tenable
            }
        }
        val u = largeur / Ornement.LARGEUR
        val hauteur = (hauteurUnites * u).toInt()
        for (i in 0 until childCount) {
            val r = emplacements[i]
            mettreALEchelle(getChildAt(i), u)
            getChildAt(i).measure(
                MeasureSpec.makeMeasureSpec((r.width() * u).toInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((r.height() * u).toInt(), MeasureSpec.EXACTLY)
            )
        }
        setMeasuredDimension(largeur, hauteur)
    }

    override fun onLayout(change: Boolean, g: Int, h: Int, d: Int, b: Int) {
        val u = (d - g) / Ornement.LARGEUR
        for (i in 0 until childCount) {
            val r = emplacements[i]
            getChildAt(i).layout(
                (r.left * u).toInt(), (r.top * u).toInt(),
                (r.right * u).toInt(), (r.bottom * u).toInt()
            )
        }
    }
}

/**
 * Le recto : le cadre orné, l'illustration du mot, et le texte de la carte.
 *
 * C'est la face que le joueur a gagnée et que le carnet conserve. Tout ce qui
 * relève de la géométrie du carton est dans [Carton] ; ce qui reste ici est le
 * tracé, l'échelle d'ornement, et le reflet des deux paliers hauts.
 */
class CarteOrnee(
    context: Context,
    private val mot: String,
    private val rarete: Rarete,
    private val vignette: Boolean
) : Carton(context), SensorEventListener {

    override val hauteurUnites: Float =
        if (vignette) Ornement.HAUTEUR_VIGNETTE else Ornement.HAUTEUR

    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private val teinte: Float
    private var boite = 0

    /**
     * Tout ce qui se calcule une fois pour la vie de la vue.
     *
     * La carte est tracée en **unités de carte**, jamais en pixels : les
     * dégradés, l'ouverture et le motif ne dépendent donc pas de la taille à
     * l'écran, et il n'y a aucune raison de les refabriquer à chaque trame.
     * C'est la même discipline que l'ancienne illustration s'imposait dans
     * `onSizeChanged`, rendue simplement inutile par le changement d'unité.
     */
    private val fenetre = if (vignette) Ornement.FENETRE_VIGNETTE else Ornement.FENETRE
    private val decoupe = Ornement.cheminFenetre(fenetre, rarete.ordinal >= 2)
    private val degradeFace: android.graphics.LinearGradient
    private val degradeGemme: android.graphics.RadialGradient
    private val motif: Motif

    /** Le roulis du téléphone, ramené dans [-1, 1]. */
    private var roulis = 0f
    private var capteurs: SensorManager? = null

    init {
        val condense = mot.fold(7919) { acc, c -> acc * 31 + c.code }
        teinte = ((condense % 360) + 360) % 360f
        degradeFace = Ornement.degradeFace(teinte, rarete, vignette)
        degradeGemme = Ornement.degradeGemme(teinte)
        motif = Motif(mot, rarete, fenetre)
        setWillNotDraw(false)
        clipChildren = false
    }

    fun avecBoite(valeur: Int): CarteOrnee {
        boite = valeur
        return this
    }

    /**
     * L'ordre est celui d'une carte imprimée : la face, ce qui rayonne
     * derrière l'illustration, l'illustration, puis le métal par-dessus.
     *
     * Deux de ces quatre couches — les rayons et le métal — sont des bitmaps
     * partagés par toutes les cartes du même palier et de la même taille. Ce
     * qui reste à tracer réellement à chaque trame, c'est un rectangle
     * dégradé, le motif du mot, et une poignée de joyaux.
     */
    override fun onDraw(canvas: Canvas) {
        val u = width / Ornement.LARGEUR
        if (u <= 0f) return

        canvas.save()
        canvas.scale(u, u)
        Ornement.dessinerFace(canvas, pinceau, degradeFace, rarete, vignette)
        canvas.restore()

        Ornement.rayons(rarete, width, vignette)?.let { canvas.drawBitmap(it, 0f, 0f, null) }

        canvas.save()
        canvas.scale(u, u)
        motif.peindre(canvas, pinceau, roulis, decoupe)
        canvas.restore()

        Ornement.metal(rarete, width, vignette)?.let { canvas.drawBitmap(it, 0f, 0f, null) }

        canvas.save()
        canvas.scale(u, u)
        if (vignette) {
            Ornement.dessinerBoite(canvas, pinceau, boite)
        } else {
            Ornement.dessinerGemme(canvas, pinceau, degradeGemme)
            Ornement.dessinerJoyauRarete(canvas, pinceau, rarete)
        }
        Ornement.dessinerSemis(canvas, pinceau, mot, rarete, vignette)
        Ornement.dessinerReflet(canvas, pinceau, rarete, roulis, vignette)
        canvas.restore()
    }

    // ------------------------------------------------------------- le vivant

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Seule la carte ouverte suit l'inclinaison : autant de capteurs que
        // de vignettes dans une grille qui défile serait absurde, et l'effet
        // ne se voit pas à cette taille.
        if (vignette || !rarete.distinguee) return
        if (Pochette.animationsReduites(context)) return
        val manager =
            context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        // L'accéléromètre brut mélange la pesanteur et l'accélération
        // linéaire : marcher suffisait à faire trembler le reflet. Le capteur
        // fusionné n'en garde que la pesanteur, ce qui est tout ce dont une
        // orientation a besoin. Il n'existe pas partout, d'où le repli.
        val capteur = manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return
        manager.registerListener(this, capteur, SensorManager.SENSOR_DELAY_UI)
        capteurs = manager
    }

    override fun onDetachedFromWindow() {
        capteurs?.unregisterListener(this)
        capteurs = null
        super.onDetachedFromWindow()
    }

    override fun onAccuracyChanged(capteur: Sensor?, precision: Int) = Unit

    /**
     * Le roulis, lissé, et seulement quand il a vraiment changé.
     *
     * Le filtre passe-bas rend le reflet lourd, ce qui est exactement l'effet
     * voulu : une carte, ça a du poids. Le seuil évite de redessiner trois
     * cents ordres de tracé pour un dixième de degré.
     */
    override fun onSensorChanged(evenement: SensorEvent) {
        if (evenement.values.isEmpty()) return
        val cible = (-evenement.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
        if (abs(cible - roulis) < 0.04f) return
        roulis += (cible - roulis) * 0.20f
        invalidate()
    }
}

/**
 * L'illustration générative d'une carte : un motif tiré du mot lui-même.
 *
 * Le tracé n'a pas bougé d'un trait depuis qu'il a été mesuré et défendu : un
 * anneau par lettre, la teinte tirée du mot, une matière par palier — commune
 * mate, grain oblique, halo, irisation — et l'initiale en filigrane. Deux
 * choses ont changé, et elles vont ensemble :
 *
 * - le motif se découpe maintenant dans une **arche** quand le palier le
 *   mérite, au lieu d'un rectangle ; c'est [CarteOrnee] qui lui passe
 *   l'ouverture, il ne la choisit pas ;
 * - il n'est plus une `View`, mais un objet peint dans le canevas de la
 *   carte, ce qui permet au métal de passer par-dessus ses bords.
 *
 * Comme la carte se trace en unités de carte, la zone ne change jamais de
 * taille : tout ce qui coûte — dégradés, hachures, positions d'anneaux — est
 * construit **une fois**, ici, et [peindre] n'alloue rien.
 */
class Motif(mot: String, private val rarete: Rarete, private val zone: RectF) {

    private class Anneau(val cx: Float, val cy: Float, val r: Float, val trait: Float, val couleur: Int)

    private val commun = rarete == Rarete.COMMUN
    private val initiale = mot.take(1).uppercase()
    private val opacite = if (commun) 30 else 48

    private val fond: LinearGradient
    private val halo: RadialGradient?
    private val iris: SweepGradient?
    private val brillance: LinearGradient?
    private val hachures = Path()
    private val anneaux: List<Anneau>
    private val matrice = Matrix()
    private val filigrane = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ligneDeBase: Float

    init {
        val condense = mot.fold(7919) { acc, ch -> acc * 31 + ch.code }
        val teinte = ((condense % 360) + 360) % 360f
        fun couleur(s: Float, v: Float) = Color.HSVToColor(floatArrayOf(teinte, s, v))
        val w = zone.width()
        val h = zone.height()

        // Une commune reste dans un mouchoir de poche — deux valeurs proches,
        // peu de saturation ; les autres gardent le dégradé d'origine.
        fond = LinearGradient(
            zone.left, zone.top, zone.left + w * 0.35f, zone.bottom,
            if (commun) couleur(0.16f, 0.93f) else couleur(0.30f, 0.96f),
            if (commun) couleur(0.24f, 0.85f) else couleur(0.55f, 0.72f),
            Shader.TileMode.CLAMP
        )

        // Le grain d'une peu commune : des obliques régulières d'un bord à
        // l'autre, le papier, pas un motif qu'on cherche à lire.
        if (rarete == Rarete.PEU_COMMUN) {
            var x = -h
            while (x < w) {
                hachures.moveTo(zone.left + x, zone.bottom)
                hachures.lineTo(zone.left + x + h, zone.top)
                x += 9f
            }
        }

        halo = if (rarete == Rarete.RARE) RadialGradient(
            zone.centerX(), zone.centerY(), h * 0.72f,
            intArrayOf(
                Color.argb(120, 255, 255, 255),
                Color.argb(40, 255, 255, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        ) else null

        // Un anneau par lettre, jusqu'à six : position et rayon lus dans le mot.
        anneaux = mot.take(6).mapIndexed { i, ch ->
            val g = ch.code
            Anneau(
                zone.left + w * (0.12f + 0.16f * ((g + i * 7) % 6)),
                zone.top + h * (0.15f + 0.14f * ((g / 3 + i * 5) % 6)),
                h * (0.22f + 0.09f * (g % 5)),
                1.4f + (g % 3),
                if (i % 2 == 0) Color.WHITE else couleur(0.65f, 0.45f)
            )
        }

        if (rarete == Rarete.TRES_RARE) {
            // Un tour complet du cercle depuis la teinte du mot. La dernière
            // reprend la première, sinon le dégradé montre sa couture ; la
            // saturation reste basse, sinon l'arc-en-ciel mange le mot.
            val teintes = IntArray(8) { i ->
                Color.HSVToColor(floatArrayOf((teinte + 360f * (i % 7) / 7f) % 360f, 0.45f, 1f))
            }
            iris = SweepGradient(zone.centerX(), zone.centerY(), teintes, null)
            brillance = LinearGradient(
                zone.left, zone.bottom, zone.right, zone.top,
                intArrayOf(Color.TRANSPARENT, Color.argb(96, 255, 255, 255), Color.TRANSPARENT),
                floatArrayOf(0.30f, 0.50f, 0.70f), Shader.TileMode.CLAMP
            )
        } else {
            iris = null
            brillance = null
        }

        filigrane.typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD
        )
        filigrane.textAlign = Paint.Align.CENTER
        filigrane.textSize = h * 0.72f
        filigrane.color = Color.WHITE
        val mesure = Paint.FontMetrics()
        filigrane.getFontMetrics(mesure)
        ligneDeBase = zone.centerY() - (mesure.ascent + mesure.descent) / 2f
    }

    /**
     * Peint le motif dans son ouverture.
     *
     * `roulis` est l'inclinaison de l'appareil ramenée dans [-1, 1] : elle ne
     * fait tourner que deux matrices, ce qui rend le suivi du capteur
     * pratiquement gratuit.
     */
    fun peindre(c: Canvas, p: Paint, roulis: Float, decoupe: Path) {
        c.save()
        // Sans découpe, anneaux, hachures et brillance déborderaient sur le
        // cadre — et l'arche cesserait d'être une arche.
        c.clipPath(decoupe)

        p.style = Paint.Style.FILL
        p.shader = fond
        c.drawRect(zone, p)
        p.shader = null

        if (rarete == Rarete.PEU_COMMUN) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1f
            p.color = Color.WHITE
            p.alpha = 22
            c.drawPath(hachures, p)
            p.alpha = 255
        }

        // Le halo d'une rare, posé avant les anneaux pour rester derrière eux.
        halo?.let {
            p.style = Paint.Style.FILL
            p.shader = it
            c.drawRect(zone, p)
            p.shader = null
        }

        p.style = Paint.Style.STROKE
        for (a in anneaux) {
            p.strokeWidth = a.trait
            p.color = a.couleur
            p.alpha = opacite
            c.drawCircle(a.cx, a.cy, a.r, p)
        }
        p.alpha = 255
        p.style = Paint.Style.FILL

        iris?.let {
            matrice.setRotate(roulis * 55f, zone.centerX(), zone.centerY())
            it.setLocalMatrix(matrice)
            p.shader = it
            p.alpha = 52
            c.drawRect(zone, p)
            p.alpha = 255
            p.shader = null
        }

        // L'initiale en filigrane : la carte dit son mot même en vignette.
        filigrane.style = Paint.Style.FILL
        filigrane.alpha = 64
        c.drawText(initiale, zone.centerX(), ligneDeBase, filigrane)
        if (rarete == Rarete.TRES_RARE) {
            // Sous l'irisation, une lettre pleine se dilue ; détourée, elle tient.
            filigrane.style = Paint.Style.STROKE
            filigrane.strokeWidth = 1.6f
            filigrane.alpha = 92
            c.drawText(initiale, zone.centerX(), ligneDeBase, filigrane)
        }

        brillance?.let {
            matrice.setTranslate(roulis * zone.width() * 0.45f, 0f)
            it.setLocalMatrix(matrice)
            p.shader = it
            c.drawRect(zone, p)
            p.shader = null
        }

        c.restore()
    }
}
