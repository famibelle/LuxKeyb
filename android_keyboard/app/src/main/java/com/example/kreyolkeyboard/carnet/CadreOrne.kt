package com.example.kreyolkeyboard.carnet

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.LruCache
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import com.example.kreyolkeyboard.KeyFeedback
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
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
     * L'épaisseur apparente du carton à plein roulis, en unités de carte.
     * Voir [dessinerTranche] : elle est volontairement plus grande que la
     * vérité.
     */
    const val EPAISSEUR = 5f

    /** Le cœur du carton, que l'impression ne recouvre pas. */
    private const val TRANCHE = 0xFFFBF6EA.toInt()

    /** La ligne où la face s'arrête sur la tranche. */
    private const val TRANCHE_FIL = 0xFF6E6559.toInt()

    /** Sur quelle largeur le bord fuyant tombe dans l'ombre. */
    private const val LARGEUR_OMBRE = 28f

    /**
     * De combien un roulis de 1 déplace le balayage, en largeurs de carte.
     *
     * Nommé plutôt qu'écrit dans [refletBalaye] parce que [Carton] a besoin de
     * l'**inverser** : pour poser la lumière exactement sous le pouce, il faut
     * savoir quel roulis l'y amène. Les deux formules doivent donc lire le
     * même nombre, sinon le reflet suivrait le doigt de loin.
     */
    const val ETALEMENT = 0.55f

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
    val GEMME = RectF(11f, 17f, 65f, 71f)
    val TYPE = RectF(46f, 256f, 254f, 284f)
    val PANNEAU = RectF(38f, 289f, 262f, 384f)
    /** Le texte, en retrait du panneau : le double filet passe entre les deux. */
    val PANNEAU_TEXTE = RectF(48f, 297f, 252f, 378f)
    val ECU_G = RectF(26f, 375f, 78f, 410f)
    val ECU_D = RectF(222f, 375f, 274f, 410f)
    /** Le chiffre d'un écu : sous le libellé gravé, pas par-dessus. */
    val ECU_G_TEXTE = RectF(26f, 382f, 78f, 408f)
    val ECU_D_TEXTE = RectF(222f, 382f, 274f, 408f)
    /**
     * La ligne de série est passée **dans la marge**, où ce genre de mention
     * vit sur une carte imprimée : numéro, jeu, date, rang — de
     * l'administratif, qui n'a pas à disputer sa place au contenu.
     *
     * Sur le plateau elle ne manquait pas seulement d'air, elle **traversait
     * les volutes**. À `bord = 18`, les spirales du bas sont centrées en
     * (23, 417) et (277, 417) sur 22 unités, et l'or en pose deux secondes en
     * x = 39 et x = 261 : quatre spirales sous un texte qui allait de 30 à
     * 270, et l'exposant du rang illisible à droite.
     *
     * La bande retenue est la même sur les quatre paliers, parce qu'elle est
     * ancrée au bord bas et non à la marge, qui varie de 12 à 18 :
     *
     * - **428 en haut.** Une volute a perdu 62 % de son rayon quand elle passe
     *   à l'aplomb de son centre (`r = taille · exp(-1,75 t)`, et l'angle bas
     *   tombe à t ≈ 0,56) : aucune ne descend plus bas que ~427, or et rare
     *   confondus, et les secondes spirales de l'or s'arrêtent vers 420.
     * - **437,5 en bas.** Le filet de contour extérieur commence là —
     *   `RectF(1.2, …, haut - 1.2)` tracé en 2,5 d'épaisseur.
     *
     * Neuf unités et demie, donc, et le corps descend à 7,5 : c'est celui des
     * libellés gravés dans les écus, pas une taille inventée pour l'occasion.
     * Serré, et c'est le prix — en échange la ligne se lit d'un bloc.
     *
     * Le texte reste en `trait`, le ton sombre du métal, et non en `hi` : au
     * bas du bandeau le dégradé est entre `lo` et `mid`, où le sombre tient
     * 4,4:1 sur l'or et 4,0:1 sur l'argent, contre 2,4:1 et 2,2:1 au clair.
     */
    val SERIE_G = RectF(22f, 428f, 176f, 437.5f)
    val SERIE_D = RectF(176f, 428f, 278f, 437.5f)
    /** Le joyau de rareté, ses satellites et ses volutes, entre les écus. */
    val JOYAU_CENTRAL = RectF(102f, 386f, 198f, 412f)
    val NOM_VIGNETTE = RectF(20f, 218f, 280f, 248f)
    val GLOSE_VIGNETTE = RectF(20f, 249f, 280f, 269f)
    val BOITE_VIGNETTE = RectF(30f, 277f, 270f, 281f)

    // ----------------------------------------------------------- primitives

    /**
     * Une volute **gravée** : un sillon creusé dans la matière, pas un trait
     * posé dessus.
     *
     * La lumière du cadre vient d'en haut à gauche (son dégradé va de `hi` à
     * `lo`). Un creux y montre donc sa paroi haute dans l'ombre et sa lèvre
     * basse dans la lumière ; l'inverse se lit comme un relief. Le fond est
     * translucide pour que la matière, métal ou face teintée, reste visible
     * au fond du sillon. Chaque passe va dans sa propre couche : des segments
     * translucides aux bouts ronds se superposeraient en chapelet aux jointures.
     */
    private fun volute(
        c: Canvas, p: Paint, x: Float, y: Float, taille: Float,
        sx: Float, sy: Float, tours: Float, epais: Float, m: Metal
    ) {
        val marge = epais + 2f
        val cadre = RectF(x - taille - marge, y - taille - marge, x + taille + marge, y + taille + marge)
        passe(c, p, cadre, 0x9E, Color.WHITE, x + 0.45f, y + 0.85f, taille, sx, sy, tours, epais)
        passe(c, p, cadre, 0x6B, Color.BLACK, x, y, taille, sx, sy, tours, epais * 0.8f)
        passe(c, p, cadre, 0x5C, m.trait, x - 0.25f, y - 0.45f, taille, sx, sy, tours, epais * 0.35f)
    }

    /** Une passe de [volute], opaque dans une couche rendue à [alpha]. */
    private fun passe(
        c: Canvas, p: Paint, cadre: RectF, alpha: Int, couleur: Int,
        x: Float, y: Float, taille: Float, sx: Float, sy: Float, tours: Float, epais: Float
    ) {
        c.saveLayerAlpha(cadre, alpha)
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
        c.restore()
    }

    /**
     * Une feuille d'acanthe **gravée** dans la bande de métal, comme les
     * volutes : lèvre claire en bas à droite, creux sombre. Posée en aplat
     * pâle à cheval sur le bord du cadre, elle se lisait comme une tache.
     */
    private fun feuille(c: Canvas, p: Paint, x: Float, y: Float, l: Float, angle: Float) {
        val chemin = Path()
        chemin.moveTo(0f, 0f)
        chemin.quadTo(l * 0.45f, -l * 0.38f, l, 0f)
        chemin.quadTo(l * 0.45f, l * 0.38f, 0f, 0f)
        p.style = Paint.Style.FILL
        p.shader = null
        for ((dx, dy, couleur) in arrayOf(
            Triple(0.45f, 0.85f, 0x8CFFFFFF.toInt()),
            Triple(0f, 0f, 0x4D000000)
        )) {
            c.save()
            c.translate(x + dx, y + dy)
            c.rotate(angle)
            p.color = couleur
            c.drawPath(chemin, p)
            c.restore()
        }
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
            // La pointe s'arrête avant le filet extérieur : à 22 unités, celle
            // de la plaque venait buter contre le bord de la carte (298 sur 300).
            val q = min(r.height() * 0.55f, min(r.left, LARGEUR - r.right) - 9f)
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
        p.color = Color.BLACK
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
        // Un dégradé est multiplié par l'alpha du pinceau : sans ce retour à
        // l'opaque, l'écu héritait du filet tracé juste avant et devenait
        // transparent (16 % à gauche, 50 % à droite).
        p.color = Color.BLACK
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
            chemin.addRoundRect(r, 10f, 10f, Path.Direction.CW)
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
        //
        // Sur la grande carte, les angles du haut appartiennent à la gemme et
        // à la pointe de la plaque, peintes par-dessus : la volute n'y montrait
        // que des bouts de spirale. Seuls les angles du bas en reçoivent, plus
        // petites et logées dans le coin, sous la courbe des écus, au-dessus
        // de la ligne de série (428). Le nombre visible par palier est inchangé.
        val volutes = intArrayOf(0, 2, 4, 8)[palier]
        if (volutes > 0) {
            val coins = if (vignette) arrayOf(
                floatArrayOf(bord + 5f, bord + 5f, 1f, 1f),
                floatArrayOf(LARGEUR - bord - 5f, bord + 5f, -1f, 1f),
                floatArrayOf(bord + 5f, haut - bord - 5f, 1f, -1f),
                floatArrayOf(LARGEUR - bord - 5f, haut - bord - 5f, -1f, -1f)
            ) else arrayOf(
                floatArrayOf(0f, 0f, 0f, 0f),
                floatArrayOf(0f, 0f, 0f, 0f),
                floatArrayOf(bord + 5f, haut - bord - 3f, 1f, -1f),
                floatArrayOf(LARGEUR - bord - 5f, haut - bord - 3f, -1f, -1f)
            )
            val taille = (13f + palier * 3f) * (if (vignette) 1f else 0.65f)
            for (i in 0 until volutes) {
                val coin = coins[i % 4]
                if (coin[2] == 0f) continue
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
                volute(c, p, x + dx, y + dy, t, sx, sy, 1.35f, if (vignette) 3.2f else 2.6f, m)
            }
        }

        // 5. Les feuilles d'acanthe sur les flancs (Très rare).
        if (palier >= 3) {
            val depart = fenetre.top + 30f
            val pas = (haut - depart - 80f) / 5f
            for (i in 0 until 5) {
                val y = depart + i * pas
                feuille(c, p, bord / 2f - 3f, y, 9f, -28f)
                feuille(c, p, LARGEUR - bord / 2f + 3f, y, 9f, 208f)
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
        c.drawRoundRect(PANNEAU, 9f, 9f, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.4f
        p.color = m.mid
        c.drawRoundRect(PANNEAU, 9f, 9f, p)
        p.strokeWidth = 1f
        p.color = m.trait
        c.drawRoundRect(PANNEAU, 9f, 9f, p)
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
     * La teinte d'un mot : le seul endroit qui la décide.
     *
     * Elle se lit sur les **trois premières lettres**, et non sur le mot
     * entier. Le mot entier donnait à `Woch`, `Wochen` et `Woche` trois
     * couleurs sans rapport : le carnet cachait activement qu'il s'agit d'un
     * seul mot à trois états, alors que c'est exactement ce qu'un carnet de
     * vocabulaire devrait montrer. Le préfixe suffit à les réunir, et il ne
     * demande de consulter aucun lemme — donc il vaut aussi pour les 647
     * formes que `luxemburgish_familles.json` ne rattache à rien.
     *
     * Le prix est l'homonymie de préfixe : `Stad` et `Statist` tomberont sur
     * la même teinte. C'est sans conséquence, parce qu'une teinte n'identifie
     * pas une carte — la plaque porte le mot — elle en rapproche.
     *
     * La face, la gemme et le motif la partagent : une carte dont la fenêtre
     * jurerait avec son carton se lirait comme un défaut d'impression.
     */
    fun teinteDe(mot: String): Float {
        val condense = condense(mot)
        return ((condense % 360) + 360) % 360f
    }

    /**
     * La teinte d'un mot **qui a un champ** : la couleur du champ, écartée.
     *
     * Sans champ, on retombe sur [teinteDe] et rien ne change. Avec, la carte
     * prend la teinte de son domaine — et c'est ce qui fait qu'un tiroir du
     * carnet cesse d'être un nuancier aléatoire pour devenir un rangement.
     *
     * L'écart de ±12° n'est pas une décoration : huit teintes strictement
     * identiques feraient de chaque champ un aplat, et deux cartes voisines du
     * même domaine deviendraient indiscernables l'une de l'autre. Il est tiré
     * du même condensé que [teinteDe], donc des trois premières lettres, ce qui
     * garde `Woch`, `Wochen` et `Woche` exactement sur la même couleur.
     *
     * Douze degrés, enfin, parce que c'est moins que la moitié du plus petit
     * intervalle entre deux champs (34°, de 20 à 52) : deux domaines ne peuvent
     * donc jamais se recouvrir, quel que soit le mot.
     */
    fun teinteDe(mot: String, champ: Champ?): Float {
        if (champ == null) return teinteDe(mot)
        val ecart = (((condense(mot) shr 3) % 25) + 25) % 25 - 12
        return ((champ.hue + ecart) % 360f + 360f) % 360f
    }

    /** Le condensé des trois premières lettres, seule source des teintes. */
    private fun condense(mot: String): Int =
        mot.lowercase().take(3).fold(7919) { acc, c -> acc * 31 + c.code }

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
            volute(c, p, LARGEUR / 2f + s * 34f, 399f, 11f, s.toFloat(), 1f, 1.2f, 2f, m)
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
        // Le semis est peint après le métal : tiré sur toute la carte, il
        // tombait sur le cadre. Il reste dans la face, rayon maximal compris.
        val marge = 12f + rarete.ordinal * 2f + 7f
        // Ni sur le texte ni sur le métal posé sur la face : une étincelle y
        // passe pour un défaut d'impression. On retire, au même générateur.
        val interdits = if (vignette) arrayOf(NOM_VIGNETTE, GLOSE_VIGNETTE, BOITE_VIGNETTE)
        else arrayOf(GEMME, PLAQUE, TYPE, PANNEAU, ECU_G, ECU_D, JOYAU_CENTRAL)
        var poses = 0
        var essais = 0
        while (poses < n && essais < n * 8) {
            essais++
            g = g * 1103515245 + 12345
            val x = marge + ((g ushr 8) % 1000) / 1000f * (LARGEUR - 2f * marge)
            val y = marge + ((g ushr 18) % 1000) / 1000f * (haut - 2f * marge)
            val r = 2.5f + ((g ushr 4) % 5)
            if (interdits.any { x > it.left - r && x < it.right + r && y > it.top - r && y < it.bottom + r }) continue
            // Ni sur la sertissure de la fenêtre : dedans ou dehors, pas à cheval.
            val m = r + 5f
            val f = if (vignette) FENETRE_VIGNETTE else FENETRE
            val dehors = !dansArche(x, y, RectF(f.left - m, f.top - m, f.right + m, f.bottom + m))
            val dedans = dansArche(x, y, RectF(f.left + m, f.top + m, f.right - m, f.bottom - m))
            if (!dehors && !dedans) continue
            etincelle(c, p, x, y, r, 90 + ((g ushr 12) % 100))
            poses++
        }
    }

    /** Le point est-il dans l'ouverture en plein cintre de [cheminFenetre] ? */
    private fun dansArche(x: Float, y: Float, r: RectF): Boolean {
        if (x < r.left || x > r.right || y > r.bottom) return false
        val fleche = r.width() * 0.30f
        val base = r.top + fleche
        if (y >= base) return true
        val dx = (x - r.centerX()) / (r.width() / 2f)
        val dy = (y - base) / fleche
        return dx * dx + dy * dy <= 1f
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
     * L'épaisseur du carton : ce qui le sépare d'une découpe de papier.
     *
     * ## Pourquoi c'est le manque le plus criant
     *
     * Une carte inclinée montre sa tranche. C'est la chose qu'on ne remarque
     * jamais consciemment et qui décide pourtant, à elle seule, si le cerveau
     * range ce qu'il voit dans les objets ou dans les images. Le carnet
     * savait déjà pencher ses cartes et y faire glisser une lumière ; ce
     * qu'il ne savait pas, c'est leur donner un bord.
     *
     * ## Quel bord, et pourquoi celui-là
     *
     * Un `rotationY` positif fait **fuir le bord droit** — c'est la
     * convention d'Android, et c'est celle sur laquelle [Inclinaison] a réglé
     * son contre-pivot. Or la tranche qu'on voit n'est pas celle du bord qui
     * s'éloigne mais celle du bord qui **s'approche** : c'est sa face
     * latérale qui tourne vers l'œil, l'autre tournant le dos. Un roulis
     * positif se dessine donc avec la tranche à gauche et l'ombre à droite,
     * ce qui est l'inverse de ce que la main écrit spontanément.
     *
     * ## Une épaisseur assumée comme fausse
     *
     * Une carte à jouer fait trois dixièmes de millimètre, soit une unité et
     * demie de carte, et sa projection à sept degrés vaut deux dixièmes
     * d'unité — invisible. [EPAISSEUR] est donc un mensonge délibéré, et la
     * racine appliquée au roulis en est un second : sans elle, la tranche ne
     * se déplierait que dans le dernier quart du débattement, alors que le
     * téléphone passe sa vie dans le premier.
     */
    fun dessinerTranche(c: Canvas, p: Paint, roulis: Float, haut: Float) {
        val ouverture = abs(roulis)
        if (ouverture < 0.02f) return
        val e = EPAISSEUR * ouverture.pow(0.55f)
        val contour = RectF(0f, 0f, LARGEUR, haut)
        val fuiteADroite = roulis > 0f

        // Le bord qui s'éloigne tombe dans l'ombre. Un dégradé, et non un
        // aplat : une arête franche se lirait comme un trait d'encre.
        c.save()
        if (fuiteADroite) c.clipRect(LARGEUR - LARGEUR_OMBRE, 0f, LARGEUR, haut)
        else c.clipRect(0f, 0f, LARGEUR_OMBRE, haut)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            if (fuiteADroite) LARGEUR else 0f, 0f,
            if (fuiteADroite) LARGEUR - LARGEUR_OMBRE else LARGEUR_OMBRE, 0f,
            ((0x44 * ouverture).toInt() shl 24), 0x00000000, Shader.TileMode.CLAMP
        )
        c.drawRoundRect(contour, RAYON, RAYON, p)
        p.shader = null
        c.restore()

        // Le bord qui vient vers le joueur montre son cœur. La découpe est
        // rectangulaire et parallèle aux pixels, donc sans crénelage : les
        // coins arrondis viennent du rectangle tracé dedans, lui antialiasé.
        c.save()
        if (fuiteADroite) c.clipRect(0f, 0f, e, haut) else c.clipRect(LARGEUR - e, 0f, LARGEUR, haut)
        p.color = TRANCHE
        c.drawRoundRect(contour, RAYON, RAYON, p)
        c.restore()

        // Là où l'impression s'arrête sur la tranche, il reste une ligne. Elle
        // s'arrête avant les coins : à cette hauteur, la tranche a déjà tourné.
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.9f
        p.color = TRANCHE_FIL
        p.alpha = (230f * ouverture).toInt().coerceAtMost(230)
        val x = if (fuiteADroite) e else LARGEUR - e
        c.drawLine(x, RAYON * 0.8f, x, haut - RAYON * 0.8f, p)
        p.alpha = 255
    }

    /**
     * Les arêtes que le pouce franchit à la hauteur [y], en unités de carte.
     *
     * ## Ce que le doigt est censé sentir
     *
     * Une carte de collection est gravée : le cadre est en relief sur la
     * face, l'ouverture est creusée dedans, les écus dépassent. Un pouce qui
     * la traverse franchit donc une poignée de marches, et leur **rythme**
     * dépend de la hauteur à laquelle il passe — à mi-carte il ne rencontre
     * que le cadre et les flancs de l'ouverture, en bas il traverse les deux
     * écus et le joyau. C'est cette différence-là qui distingue une surface
     * gravée d'un curseur à crans, et c'est pour elle que la liste est
     * calculée à partir d'un `y`.
     *
     * ## Pourquoi la rareté a le droit d'y être
     *
     * Sur la face, oui : le palier est déjà sous les yeux, une main qui le
     * confirme n'apprend rien à personne. Sur le dos de révision, non — et
     * c'est pourquoi [DosRevision] ne passe pas par ici mais donne sa propre
     * géométrie, la même pour les douze cartes d'une session.
     */
    fun aretes(rarete: Rarete, y: Float): FloatArray {
        val palier = rarete.ordinal
        val bord = 12f + palier * 2f
        val brut = ArrayList<Float>(14)
        // Le bord du carton, puis celui du plateau : les deux seules marches
        // que le doigt trouve à n'importe quelle hauteur.
        brut.add(BORD_CARTE)
        brut.add(LARGEUR - BORD_CARTE)
        brut.add(bord)
        brut.add(LARGEUR - bord)
        dansLaBande(brut, y, GEMME, GEMME.left + 4f, GEMME.right - 4f)
        dansLaBande(brut, y, PLAQUE, PLAQUE.left, PLAQUE.right)
        dansLaBande(brut, y, FENETRE, FENETRE.left, FENETRE.right)
        dansLaBande(brut, y, TYPE, TYPE.left, TYPE.right)
        dansLaBande(brut, y, PANNEAU, PANNEAU.left, PANNEAU.right)
        dansLaBande(brut, y, ECU_G, ECU_G.left, ECU_G.right)
        dansLaBande(brut, y, ECU_D, ECU_D.left, ECU_D.right)
        if (y > ECU_G.top && y < ECU_G.bottom) {
            val r = 8f + palier
            brut.add(LARGEUR / 2f - r)
            brut.add(LARGEUR / 2f + r)
        }
        // Rien pour la ligne de série : elle est passée dans la marge, où le
        // métal est lisse et où `bord` est déjà la seule marche du doigt.
        return crans(brut)
    }

    private fun dansLaBande(
        brut: MutableList<Float>, y: Float, bande: RectF, gauche: Float, droite: Float
    ) {
        if (y <= bande.top || y >= bande.bottom) return
        brut.add(gauche)
        brut.add(droite)
    }

    /**
     * Trie des arêtes et fond celles qui se touchent.
     *
     * La gemme de coût et la plaque de nom se croisent en hauteur, et leurs
     * flancs finissent à une unité l'un de l'autre : deux vibrations séparées
     * par un cinquième de millimètre ne se sentent pas comme deux marches,
     * elles se sentent comme un défaut.
     */
    fun crans(brut: MutableList<Float>): FloatArray {
        brut.sort()
        val net = ArrayList<Float>(brut.size)
        for (x in brut) {
            if (net.isEmpty() || x - net[net.size - 1] >= ECART_MIN) net.add(x)
        }
        return net.toFloatArray()
    }

    /** En deçà, deux arêtes n'en font qu'une sous le doigt. */
    private const val ECART_MIN = 6f

    /** Un carton lisse. Partagé : il est vide et personne n'y écrit. */
    val SANS_ARETE = FloatArray(0)

    /**
     * Où se trouve le bord du carton pour le doigt.
     *
     * Deux unités en dedans du bord géométrique : c'est le moment où le pouce
     * quitte la carte, et une carte qui se termine sans qu'on la sente finir
     * est une carte qui n'avait pas de bord.
     */
    const val BORD_CARTE = 2f

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
        val dx = roulis * LARGEUR * ETALEMENT
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
 *
 * ## Pourquoi la lumière et la main sont ici aussi
 *
 * Le carton répondait au téléphone et pas à la main. On pouvait passer le
 * pouce dessus dix secondes sans que rien n'arrive, ce qui suffisait à le
 * ranger parmi les images : un objet réel réagit d'abord à ce qui le touche,
 * et seulement ensuite à la façon dont on le penche.
 *
 * Trois choses le sortent de là, et elles vivent toutes ici parce qu'elles
 * valent pour les deux faces :
 *
 * - **La tranche** ([Ornement.dessinerTranche]) — l'épaisseur qui se déplie
 *   sur le bord qui s'approche. C'est le seul des trois qui ne demande pas
 *   qu'on touche la carte, et probablement celui qui compte le plus.
 * - **Le doigt prend la lumière** — tant que le pouce est posé, c'est lui et
 *   non la pesanteur qui dit où tombe le reflet. Il n'a fallu inventer aucun
 *   tracé : [Ornement.refletBalaye] et [Motif.peindre] lisaient déjà un
 *   roulis, il leur en est simplement donné un autre.
 * - **L'appui** — le carton s'enfonce du côté pressé et remonte en dépassant
 *   légèrement son aplomb. C'est ce dépassement, et non l'enfoncement, qui se
 *   lit comme de la masse.
 *
 * ## Deux roulis, et pourquoi ils ne peuvent pas n'en faire qu'un
 *
 * [roulis] dit **où tombe la lumière**, [orientation] dit **comment le carton
 * est tourné**. Le doigt n'a le droit d'écrire que dans le premier : un pouce
 * posé au bord droit déplace un reflet, il ne fait pas pivoter la carte de
 * quarante degrés. Les confondre donnerait une tranche de cinq unités sur un
 * carton parfaitement à plat, c'est-à-dire l'exact contraire de l'effet
 * cherché.
 *
 * ## Ce que la main n'a pas le droit de faire
 *
 * Elle ne descend pas dans la grille, pour la même raison que l'inclinaison :
 * une vignette de 160 dp n'a la place ni d'une tranche ni d'un reflet, et un
 * `ACTION_DOWN` capté par chaque carte se battrait avec le défilement.
 * [sensibleAuDoigt] est donc faux par défaut et s'allume à la main, là où un
 * carton occupe l'écran pour lui seul.
 */
abstract class Carton(context: Context) : ViewGroup(context), SensorEventListener {

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

    // ------------------------------------------------- la lumière et la main

    /**
     * Ce carton-là suit-il la pesanteur ?
     *
     * Faux par défaut, c'est-à-dire pour une vignette de grille : autant
     * d'abonnements au capteur que de cartes visibles dans une colonne qui
     * défile serait absurde, et à 160 dp ni la tranche ni le reflet n'ont la
     * place d'exister.
     */
    protected open val suitLaLumiere: Boolean get() = false

    /**
     * Ce carton-là répond-il au doigt ?
     *
     * Allumé là où un carton occupe l'écran pour lui seul — la carte ouverte
     * du carnet, les deux faces de la révision. **Pas** dans la pochette : le
     * voile y prend l'appui pour passer à la carte suivante, et un carton qui
     * consommerait le geste supprimerait cette navigation-là. La révélation
     * d'un tirage est déjà un spectacle ; le pouce n'y a rien à ajouter.
     *
     * À allumer **une fois la mise en place finie**, comme
     * [Inclinaison.suivre] et pour la même raison : tant qu'un
     * `ViewPropertyAnimator` fait entrer la carte, il est seul à avoir le
     * droit d'écrire dans `rotationY`.
     */
    var sensibleAuDoigt = false
        set(valeur) {
            field = valeur
            if (valeur) Inclinaison.perspective(this)
        }

    private var capteurs: SensorManager? = null

    /** Le roulis de l'appareil depuis le repos, dans [-1, 1], tel que dessiné. */
    private var pesanteur = 0f

    /** La même posture que celle qui fait pivoter la carte, voir [Posture]. */
    private val posture = Posture(this, LISSAGE)

    /** Le roulis que dicte le doigt, dans [-1, 1]. */
    private var doigt = 0f

    /** La part du doigt dans la lumière : 0 la pesanteur, 1 le pouce. */
    private var main = 0f

    /** L'enfoncement, dans [0, 1] — et un peu en dessous au rebond. */
    private var profondeur = 0f
    private var appuiX = 0f
    private var appuiY = 0f

    private var fonduMain: ValueAnimator? = null
    private var fonduAppui: ValueAnimator? = null

    /**
     * Les arêtes que le doigt franchira pendant ce geste-ci, en unités de
     * carte, triées.
     *
     * Calculées à la pose du doigt et gardées pour tout le geste : un pouce
     * qui traverse une carte suit une horizontale, sa hauteur ne change
     * pratiquement pas, et refaire la liste à chaque `ACTION_MOVE` coûterait
     * une allocation par trame pour un résultat identique. Le rythme reste
     * ainsi stable d'un bord à l'autre d'un même balayage.
     */
    private var relief: FloatArray = Ornement.SANS_ARETE
    private var derniereX = 0f

    /**
     * Où tombe la lumière, dans [-1, 1].
     *
     * C'est ce que lisent le reflet et l'irisation du motif. Le doigt
     * l'emporte tant qu'il est posé, puis la pesanteur la reprend en une
     * seconde environ : une lumière qui sauterait au relâchement dirait que
     * le pouce était un mode, pas une main.
     */
    protected val roulis: Float get() = pesanteur + (doigt - pesanteur) * main

    /**
     * Comment le carton est réellement tourné, dans les mêmes unités.
     *
     * C'est ce que lit la tranche, et le doigt n'y écrit pas — sauf par
     * l'appui, qui fait pivoter le carton pour de bon et mérite donc que son
     * bord s'épaississe. [Inclinaison.AMPLITUDE] est le dénominateur commun
     * qui ramène des degrés à un roulis.
     */
    protected val assiette: Float
        get() = pesanteur + profondeur * appuiY / Inclinaison.AMPLITUDE

    /**
     * Le relief de ce carton-là à la hauteur [y], en unités de carte.
     *
     * Vide par défaut : un carton qui n'annonce rien est lisse, et le doigt
     * n'y sentira que le contact. C'est le bon comportement pour une vignette
     * — qui d'ailleurs ne reçoit jamais de doigt — et le seul honnête pour
     * une face qu'on ajouterait sans lui dessiner de gravure.
     */
    protected open fun aretes(y: Float): FloatArray = Ornement.SANS_ARETE

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!suitLaLumiere || Pochette.animationsReduites(context)) return
        val manager =
            context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        // L'accéléromètre brut mélange la pesanteur et l'accélération
        // linéaire : marcher suffisait à faire trembler le reflet. Le capteur
        // fusionné n'en garde que la pesanteur, et n'existe pas partout.
        val capteur = manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return
        manager.registerListener(this, capteur, SensorManager.SENSOR_DELAY_UI)
        capteurs = manager
    }

    override fun onDetachedFromWindow() {
        capteurs?.unregisterListener(this)
        capteurs = null
        posture.oublier()
        pesanteur = 0f
        // Un carton qui reviendrait à l'écran encore enfoncé se lirait comme
        // un bogue : l'appui appartient au geste, pas à la vue. Le test évite
        // au passage de fabriquer un état pour chacune des vignettes d'une
        // grille, qui n'ont jamais été touchées et ne le seront jamais.
        if (sensibleAuDoigt) reposer()
        super.onDetachedFromWindow()
    }

    /**
     * Rend le carton à son aplomb, tout de suite et sans transition.
     *
     * À appeler avant de lui faire jouer une animation qui écrit dans
     * `rotationY` — un retournement, typiquement. Le `ViewPropertyAnimator`
     * ne connaît pas l'arbitrage d'[Inclinaison] et écrirait dans la même
     * propriété que le rebond de l'appui : c'est exactement le tremblement à
     * deux écrivains que cet arbitrage existe pour empêcher.
     */
    fun reposer() {
        fonduMain?.cancel()
        fonduAppui?.cancel()
        fonduMain = null
        fonduAppui = null
        main = 0f
        profondeur = 0f
        appuiX = 0f
        appuiY = 0f
        scaleX = 1f
        scaleY = 1f
        relief = Ornement.SANS_ARETE
        Inclinaison.appui(this, 0f, 0f)
        invalidate()
    }

    override fun onAccuracyChanged(capteur: Sensor?, precision: Int) = Unit

    /**
     * Le roulis, lissé, et seulement quand il a vraiment changé.
     *
     * Le filtre passe-bas rend la lumière lourde, ce qui est exactement
     * l'effet voulu : une carte, ça a du poids. Le seuil évite de rejouer
     * trois cents ordres de tracé pour un dixième de degré.
     */
    override fun onSensorChanged(evenement: SensorEvent) {
        posture.echantillon(evenement.values)
        if (abs(posture.roulis - pesanteur) < SEUIL) return
        pesanteur = posture.roulis
        invalidate()
    }

    /**
     * Le geste, en trois temps : saisir, glisser, lâcher.
     *
     * `super` voit tout, y compris l'appui qu'on réclame ensuite, pour que la
     * machinerie de clic d'Android continue de fonctionner : la carte ouverte
     * du carnet est `clickable` uniquement pour empêcher le voile de se
     * fermer sous elle, et il n'y a aucune raison de lui retirer ça.
     *
     * Les animations réduites ne coupent plus le geste entier, seulement ce
     * qui bouge : le retour tactile passe outre. Il n'a jamais gêné personne,
     * il n'occupe pas l'écran, et pour qui coupe les animations c'est
     * précisément le seul retour qui reste — le supprimer avec elles serait
     * l'exact contraire de ce que ce réglage demande.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val herite = super.onTouchEvent(event)
        if (!sensibleAuDoigt) return herite
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                saisir(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                glisser(event.x)
                return true
            }
            // Le `CANCEL` compte autant que le `UP` : dans la fiche du carnet,
            // c'est le `ScrollView` qui reprend le geste dès qu'il devient
            // vertical, et le carton doit alors se relever comme s'il avait
            // été lâché.
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> lacher()
        }
        return herite
    }

    private fun saisir(x: Float, y: Float) {
        if (width <= 0 || height <= 0) return
        val u = Ornement.LARGEUR / width
        relief = aretes(y * u)
        derniereX = x * u
        // Le contact lui-même. Un carton posé ne claque pas quand on le
        // touche, mais un écran qui ne répond pas à un doigt posé n'a rien
        // touché du tout.
        KeyFeedback.onCardRidge(this)
        if (Pochette.animationsReduites(context)) return
        doigt = lumiereEn(x)
        // La lumière arrive sous le pouce dans le temps que met le carton à
        // s'enfoncer, et n'y saute pas : le doigt n'est pas une lampe qu'on
        // allume, c'est une surface qui bascule vers lui.
        animerMain(1f, ENFONCEMENT)
        // Le point pressé s'enfonce : à droite, c'est le bord droit qui part
        // en arrière (`rotationY` positif) ; en bas, c'est le bord bas, donc
        // le haut qui revient (`rotationX` négatif).
        val demiL = width / 2f
        val demiH = height / 2f
        appuiY = APPUI * ((x - demiL) / demiL).coerceIn(-1f, 1f)
        appuiX = -APPUI * ((y - demiH) / demiH).coerceIn(-1f, 1f)
        animerAppui(1f, ENFONCEMENT, DecelerateInterpolator())
    }

    /**
     * Le doigt avance : on regarde ce qu'il vient de franchir, puis on
     * déplace la lumière.
     *
     * Une seule vibration par événement, même quand plusieurs arêtes ont été
     * franchies d'un coup. C'est ce que fait une vraie surface gravée : un
     * balayage lent donne des marches distinctes parce que les événements
     * arrivent plus serrés que les arêtes, un balayage rapide donne un
     * frottement. Le pas minimum, lui, empêche un pouce immobile posé
     * exactement sur une arête de la franchir cent fois par tremblement.
     */
    private fun glisser(x: Float) {
        val u = Ornement.LARGEUR / width
        val ou = x * u
        if (abs(ou - derniereX) >= PAS_MIN) {
            val bas = min(derniereX, ou)
            val haut = max(derniereX, ou)
            derniereX = ou
            if (relief.any { it > bas && it <= haut }) KeyFeedback.onCardRidge(this)
        }
        if (Pochette.animationsReduites(context)) return
        doigt = lumiereEn(x)
        invalidate()
    }

    private fun lacher() {
        relief = Ornement.SANS_ARETE
        if (Pochette.animationsReduites(context)) return
        // Le dépassement est tout l'intérêt : le carton remonte, passe son
        // aplomb et revient. C'est la seule chose de la liste qui se lise
        // comme de la masse plutôt que comme une animation.
        animerAppui(0f, REBOND, OvershootInterpolator(2.2f))
        animerMain(0f, RETOUR)
    }

    private fun animerMain(vers: Float, duree: Long) {
        fonduMain?.cancel()
        fonduMain = ValueAnimator.ofFloat(main, vers).apply {
            duration = duree
            addUpdateListener {
                main = it.animatedValue as Float
                this@Carton.invalidate()
            }
            start()
        }
    }

    /**
     * Quel roulis pose la tache de lumière exactement sous [x].
     *
     * L'inverse de ce que fait [Ornement.refletBalaye], à mi-hauteur — le
     * balayage étant diagonal, la tache dérive un peu vers les bords haut et
     * bas, et c'est très bien ainsi : une lumière qui collerait au doigt au
     * pixel près serait un curseur, pas un reflet.
     */
    private fun lumiereEn(x: Float): Float =
        ((x / width) - 0.5f).div(Ornement.ETALEMENT).coerceIn(-1f, 1f)

    private fun animerAppui(vers: Float, duree: Long, courbe: TimeInterpolator) {
        fonduAppui?.cancel()
        fonduAppui = ValueAnimator.ofFloat(profondeur, vers).apply {
            duration = duree
            interpolator = courbe
            addUpdateListener {
                profondeur = it.animatedValue as Float
                Inclinaison.appui(this@Carton, appuiX * profondeur, appuiY * profondeur)
                val echelle = 1f - CREUX * profondeur
                this@Carton.scaleX = echelle
                this@Carton.scaleY = echelle
                // La tranche dépend de l'assiette, qui vient de bouger.
                this@Carton.invalidate()
            }
            start()
        }
    }

    private companion object {
        /** Le lissage du roulis du capteur, et le seuil sous lequel on l'ignore. */
        const val LISSAGE = 0.20f
        // Sur le roulis déjà lissé, qui avance d'un cinquième de l'écart par
        // échantillon : 0,04 sur la cible d'avant vaut 0,008 ici.
        const val SEUIL = 0.008f

        /** L'enfoncement maximum, en degrés, au bord du carton. */
        const val APPUI = 2.6f

        /** Ce que le carton perd en taille quand on appuie dessus. */
        const val CREUX = 0.015f

        const val ENFONCEMENT = 90L
        const val REBOND = 300L

        /** Le temps que met la pesanteur à reprendre la lumière au doigt. */
        const val RETOUR = 700L

        /** De combien le doigt doit avancer avant qu'on regarde le relief. */
        const val PAS_MIN = 2f
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
    private val vignette: Boolean,
    private val blason: Blasonnement = Blasonnement.AUCUN
) : Carton(context) {

    override val hauteurUnites: Float =
        if (vignette) Ornement.HAUTEUR_VIGNETTE else Ornement.HAUTEUR

    /**
     * Toute carte ouverte suit la pesanteur, et plus seulement les deux
     * paliers hauts.
     *
     * Le reflet, lui, reste un privilège de rareté ; la tranche n'en est pas
     * un. Une commune qui resterait plate pendant qu'une rare prend de
     * l'épaisseur ne se lirait pas comme moins précieuse, mais comme moins
     * réelle — et c'est précisément la distinction qu'[Inclinaison] refuse de
     * faire depuis qu'elle existe.
     */
    override val suitLaLumiere: Boolean get() = !vignette

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

    init {
        // La face, la gemme et la fenêtre prennent la teinte du champ quand le
        // mot en a un : c'est ce qui fait qu'un tiroir du carnet se lit comme
        // un rangement et non comme un nuancier. Sans champ, rien ne change.
        teinte = Ornement.teinteDe(mot, blason.champ)
        degradeFace = Ornement.degradeFace(teinte, rarete, vignette)
        degradeGemme = Ornement.degradeGemme(teinte)
        motif = Motif(mot, rarete, fenetre, blason)
        setWillNotDraw(false)
        clipChildren = false
    }

    /**
     * Le relief de la face, celui du cadre orné.
     *
     * Une vignette n'en a pas : elle ne reçoit jamais de doigt, et à 160 dp
     * ses arêtes seraient plus serrées que le seuil de perception.
     */
    override fun aretes(y: Float): FloatArray =
        if (vignette) Ornement.SANS_ARETE else Ornement.aretes(rarete, y)

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
        // La tranche par-dessus le métal : c'est le bord du carton, et le
        // cadre s'arrête dessus comme l'impression s'arrête sur la coupe.
        Ornement.dessinerTranche(canvas, pinceau, assiette, hauteurUnites)
        Ornement.dessinerReflet(canvas, pinceau, rarete, roulis, vignette)
        canvas.restore()
    }

}

/**
 * L'illustration d'une carte : la matière de son palier, le sujet de son mot.
 *
 * ## Pourquoi deux objets là où il n'y en avait qu'un
 *
 * L'ancien `Motif` peignait tout dans une seule méthode : le grain du bronze
 * et l'initiale du mot s'y suivaient à quelques lignes d'écart, et l'on ne
 * pouvait toucher à l'un sans relire l'autre. Or la fenêtre dit **deux**
 * choses, qui n'ont ni la même source ni la même durée de vie :
 *
 * - **ce que vaut la carte** — le palier, d'où viennent le grain, le halo et
 *   l'irisation, mesurés et défendus de longue date ;
 * - **quel mot elle porte** — le sujet, qui est la partie qu'on cherche encore.
 *
 * Elles sont désormais [Matiere] et [Sujet], et [Motif] n'est plus que leur
 * assemblage dans l'ordre d'une carte imprimée : la matière dessous, le sujet
 * au milieu, ce que la matière pose par-dessus. Essayer un autre sujet — un
 * meuble héraldique, un poinçon — ne demande donc plus d'ouvrir le grain.
 *
 * ## Ce que le sujet est devenu
 *
 * Il était un anneau par lettre, plus l'initiale en filigrane. Les anneaux
 * tiraient leur position du code des caractères : c'était un bruit stable et
 * unique par mot, mais un bruit — rien n'y disait le mot, et deux formes d'un
 * même lemme n'y avaient aucun air de famille. Le sujet est maintenant le
 * **tracé** du mot, lettre à lettre : voir [Trace].
 *
 * L'initiale disparaît sans être remplacée. Elle se justifiait par « la carte
 * dit son mot même en vignette » — mais [Ornement.PLAQUE] le porte déjà en
 * toutes lettres, à la vignette comme à la carte ouverte, et une lettre géante
 * derrière un mot lisible n'ajoutait qu'un doublon.
 */
class Motif(
    mot: String,
    rarete: Rarete,
    zone: RectF,
    blason: Blasonnement = Blasonnement.AUCUN
) {

    private val teinte = Ornement.teinteDe(mot, blason.champ)
    private val matiere = Matiere(rarete, zone, teinte)
    private val partition = Partition(blason.nature, zone)

    /**
     * Le meuble s'il y en a un, le tracé sinon — et jamais les deux.
     *
     * Le repli n'est pas un pis-aller : le tracé porte 97 % du carnet, et
     * c'est le meuble qui est l'exception. Un nom de meuble que
     * [Meubles] ne connaît pas retombe ici sans bruit, ce qui permet à
     * l'actif et à la bibliothèque d'avancer chacun à son rythme.
     */
    private val sujet: Sujet =
        blason.meuble?.let { Enluminure.pour(it, zone, rarete) } ?: Trace(mot, zone, teinte)

    /**
     * Peint le motif dans son ouverture.
     *
     * L'ordre dit d'où vient chaque chose : le palier pose sa matière, le sens
     * la divise, le mot s'inscrit dedans, et le palier reprend la main pour ce
     * qui doit briller par-dessus.
     *
     * `roulis` est l'inclinaison de l'appareil ramenée dans [-1, 1] : elle ne
     * fait tourner que deux matrices, ce qui rend le suivi du capteur
     * pratiquement gratuit. Sans découpe, le tracé et la brillance
     * déborderaient sur le cadre — et l'arche cesserait d'être une arche.
     */
    fun peindre(c: Canvas, p: Paint, roulis: Float, decoupe: Path) {
        c.save()
        c.clipPath(decoupe)
        matiere.dessous(c, p)
        partition.peindre(c, p)
        sujet.peindre(c)
        matiere.dessus(c, p, roulis)
        c.restore()
    }
}

/**
 * La partition : la division du champ, avant qu'on y pose quoi que ce soit.
 *
 * L'héraldique divise l'écu — plein, coupé, tranché — et c'est ici la seule
 * information de la fenêtre qui **ne coûte ni donnée ni dessin** : elle se lit
 * sur la forme du mot, que le luxembourgeois écrit avec une majuscule quand
 * c'est un substantif.
 *
 * Elle rend un service qu'on n'attendait pas d'elle. Huit teintes sur une face
 * désaturée, ça se confond : deux verts voisins ne se distinguent pas à 160 dp,
 * et encore moins en deutéranopie. Une forme qui double la couleur rétablit la
 * lecture — c'est le raisonnement des insignes de rareté, qui comptent des
 * symboles au lieu de se fier au vert et au bleu-gris.
 *
 * Deux couches, et il faut les deux : un aplat à 26 % ne se voit pas sur une
 * face déjà claire, et c'est le filet qui donne la ligne de partage. Sans lui,
 * la partition ne servirait justement plus la lisibilité qui la justifie.
 */
private class Partition(nature: Nature, zone: RectF) {

    private val aplat = Path()
    private val ligne = Path()

    init {
        val h = zone.height()
        when (nature) {
            // Coupé : une bande en chef, la forme la plus franche, pour la
            // nature la plus nombreuse après les noms.
            Nature.VERBE -> {
                val y = zone.top + h * 0.40f
                aplat.addRect(zone.left, zone.top, zone.right, y, Path.Direction.CW)
                ligne.moveTo(zone.left, y)
                ligne.lineTo(zone.right, y)
            }
            // Tranché : une diagonale. Ce qui n'est ni nom ni verbe est
            // hétéroclite, et la diagonale est la division qui ne prétend rien.
            Nature.AUTRE -> {
                aplat.moveTo(zone.left, zone.bottom)
                aplat.lineTo(zone.right, zone.top)
                aplat.lineTo(zone.right, zone.bottom)
                aplat.close()
                ligne.moveTo(zone.left, zone.bottom)
                ligne.lineTo(zone.right, zone.top)
            }
            // Chevron : la division la plus stable, pour le substantif, qui
            // est aussi ce que le carnet porte le plus.
            Nature.NOM -> {
                val faite = zone.top + h * 0.14f
                aplat.moveTo(zone.left, zone.bottom)
                aplat.lineTo(zone.centerX(), faite)
                aplat.lineTo(zone.right, zone.bottom)
                aplat.close()
                ligne.moveTo(zone.left, zone.bottom)
                ligne.lineTo(zone.centerX(), faite)
                ligne.lineTo(zone.right, zone.bottom)
            }
        }
    }

    fun peindre(c: Canvas, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        p.alpha = 66
        c.drawPath(aplat, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.6f
        p.alpha = 107
        c.drawPath(ligne, p)

        p.alpha = 255
        p.style = Paint.Style.FILL
    }
}

/**
 * Ce que le **palier** met dans la fenêtre : commune mate, grain oblique,
 * halo, irisation.
 *
 * Le tracé n'a pas bougé d'un trait depuis qu'il a été mesuré ; il a seulement
 * changé de maison. Il se peint en deux temps parce que le sujet s'intercale :
 * [dessous] pose la face et sa texture, [dessus] pose ce qui doit passer
 * par-dessus le sujet — l'irisation d'une très rare et sa brillance.
 *
 * La teinte lui est donnée plutôt que calculée : c'est la même que celle de la
 * face et de la gemme, et une carte dont la fenêtre jurerait avec son carton
 * se lirait comme un défaut d'impression.
 */
private class Matiere(private val rarete: Rarete, private val zone: RectF, teinte: Float) {

    private val commun = rarete == Rarete.COMMUN
    private val fond: LinearGradient
    private val hachures = Path()
    private val halo: RadialGradient?
    private val iris: Bitmap?
    private val brillance: LinearGradient?
    private val matrice = Matrix()

    init {
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

        if (rarete == Rarete.TRES_RARE) {
            // Un tour complet du cercle depuis la teinte du mot, saturation
            // basse, sinon l'arc-en-ciel mange le mot. Calculé pixel par pixel
            // et non en SweepGradient : sa couture laissait une rangée de
            // pixels corrompus, un trait jaune du centre vers le bord.
            val cote = 96
            val demi = cote / 2f
            val pixels = IntArray(cote * cote)
            val hsv = floatArrayOf(0f, 0.45f, 1f)
            for (y in 0 until cote) {
                for (x in 0 until cote) {
                    val angle = Math.atan2((y + 0.5f - demi).toDouble(), (x + 0.5f - demi).toDouble())
                    val tour = ((angle / (2.0 * Math.PI)) + 1.0) % 1.0
                    hsv[0] = ((teinte + 360f * tour.toFloat()) % 360f + 360f) % 360f
                    pixels[y * cote + x] = Color.HSVToColor(hsv)
                }
            }
            iris = Bitmap.createBitmap(pixels, cote, cote, Bitmap.Config.ARGB_8888)
            brillance = LinearGradient(
                zone.left, zone.bottom, zone.right, zone.top,
                intArrayOf(Color.TRANSPARENT, Color.argb(96, 255, 255, 255), Color.TRANSPARENT),
                floatArrayOf(0.30f, 0.50f, 0.70f), Shader.TileMode.CLAMP
            )
        } else {
            iris = null
            brillance = null
        }
    }

    /** La face de la fenêtre et sa texture, sous le sujet. */
    fun dessous(c: Canvas, p: Paint) {
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

        // Le halo d'une rare, posé avant le sujet pour rester derrière lui.
        halo?.let {
            p.style = Paint.Style.FILL
            p.shader = it
            c.drawRect(zone, p)
            p.shader = null
        }
        p.style = Paint.Style.FILL
    }

    /** Ce qui glisse par-dessus le sujet quand l'appareil tourne. */
    fun dessus(c: Canvas, p: Paint, roulis: Float) {
        iris?.let {
            val rayon = Math.hypot(zone.width() / 2.0, zone.height() / 2.0).toFloat()
            c.save()
            c.clipRect(zone)
            c.rotate(roulis * 55f, zone.centerX(), zone.centerY())
            p.shader = null
            p.alpha = 52
            p.isFilterBitmap = true
            c.drawBitmap(
                it, null,
                RectF(zone.centerX() - rayon, zone.centerY() - rayon, zone.centerX() + rayon, zone.centerY() + rayon),
                p
            )
            p.isFilterBitmap = false
            p.alpha = 255
            c.restore()
        }
        brillance?.let {
            matrice.setTranslate(roulis * zone.width() * 0.45f, 0f)
            it.setLocalMatrix(matrice)
            p.shader = it
            c.drawRect(zone, p)
            p.shader = null
        }
    }
}

/**
 * Ce que le **mot** met dans la fenêtre.
 *
 * Une seule implémentation aujourd'hui, [Trace], et c'est tout l'intérêt de
 * l'interface : la question « quelle image pour quel mot » n'est pas tranchée,
 * et le jour où elle le sera, c'est ici que la réponse se branchera — sans
 * qu'un seul trait de la matière change.
 */
private interface Sujet {
    fun peindre(c: Canvas)
}

/**
 * L'enluminure : le meuble du mot, gravé dans la matière.
 *
 * C'est le sujet des cartes qu'on collectionne pour elles-mêmes — cent vingt
 * mots sur deux mille huit cents, à peu près quatre sur cent. La rareté
 * n'y est pour rien : elle est fixée par le rang de fréquence et n'est pas
 * négociable, alors que l'enluminure est un second axe de désirabilité, qui
 * peut échoir à une commune comme à une très rare.
 *
 * La gravure est celle du reste du carnet — une ombre décalée vers le bas à
 * droite, puis la matière claire — parce que la lumière du carnet vient d'en
 * haut à gauche depuis les volutes. Un meuble posé à plat aurait l'air collé.
 *
 * Le plein cintre des deux paliers hauts mange le haut de la fenêtre : le
 * meuble y rentre d'un cran et descend, au lieu d'être rogné par la découpe.
 */
private class Enluminure private constructor(chemin: Path, matrice: Matrix) : Sujet {

    private val plume = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /**
     * Le meuble déjà mis à l'échelle et posé : la carte se trace en unités de
     * carte, donc la transformation n'a aucune raison d'être refaite à chaque
     * trame — et le chemin de [Meubles] reste intact pour les autres cartes.
     */
    private val trace = Path().also {
        chemin.transform(matrice, it)
        // `transform` recopie la règle de remplissage, mais on ne la laisse pas
        // à la charge d'un détail d'implémentation : sans « pair-impair », la
        // porte d'une maison cesse d'être un trou et la silhouette se bouche.
        it.fillType = Path.FillType.EVEN_ODD
    }

    override fun peindre(c: Canvas) {
        c.save()
        c.translate(DECALAGE_X, DECALAGE_Y)
        plume.color = OMBRE
        c.drawPath(trace, plume)
        c.restore()
        plume.color = TRAIT
        c.drawPath(trace, plume)
    }

    companion object {
        private val OMBRE = Color.argb(77, 0, 0, 0)
        private val TRAIT = Color.argb(235, 255, 255, 255)
        private const val DECALAGE_X = 2.2f
        private const val DECALAGE_Y = 2.8f

        /** `null` si la bibliothèque ne connaît pas ce meuble. */
        fun pour(nom: String, zone: RectF, rarete: Rarete): Enluminure? {
            val forme = Meubles.chemin(nom) ?: return null
            val arche = rarete.ordinal >= 2
            val taille = zone.height() * (if (arche) 0.60f else 0.72f)
            val m = Matrix()
            m.setScale(taille / Meubles.COTE, taille / Meubles.COTE)
            m.postTranslate(
                zone.centerX(),
                zone.centerY() + zone.height() * (if (arche) 0.09f else 0.02f)
            )
            return Enluminure(forme, m)
        }
    }
}

/**
 * Le tracé du mot : une signature gravée, lue lettre à lettre.
 *
 * ## La règle
 *
 * Voyelle en haut, consonne en bas, la hauteur affinée par le code du
 * caractère ; un nœud sur chaque voyelle, qui donne la scansion. Le pas est
 * **constant et calé à gauche**, jamais étiré sur la largeur : c'est ce détail
 * qui fait tout le travail, parce que deux formes qui partagent leur début
 * partagent alors leurs points *exactement*, au lieu de seulement se
 * ressembler. Dans une grille, `Woch`, `Wochen` et `Woche` se lisent enfin
 * comme un seul mot à trois états.
 *
 * La teinte va dans le même sens : [Ornement.teinteDe] la lit sur les trois
 * premières lettres, si bien que les formes d'une même famille tombent sur la
 * même couleur **sans qu'on ait eu à consulter le moindre lemme**. Le prix est
 * l'homonymie de préfixe, et il est modeste : une teinte n'identifie rien,
 * elle rapproche.
 *
 * ## Ce que ça coûte
 *
 * Rien. Pas un octet d'actif, pas une ligne de données, aucune couverture à
 * atteindre — le tracé vaut pour les 3 732 formes du carnet comme pour les
 * numéraux de Zuelwuert, que le corpus de fréquences ne connaît même pas. Ce
 * qu'il ne fait pas, il faut le dire aussi : il montre **le mot**, pas **la
 * chose**. C'est un monogramme, pas une illustration.
 *
 * Comme la carte se trace en unités de carte, le chemin et les nœuds sont
 * construits **une fois**, ici, et [peindre] n'alloue rien.
 */
private class Trace(mot: String, zone: RectF, teinte: Float) : Sujet {

    private val chemin = Path()
    private val noeudsX: FloatArray
    private val noeudsY: FloatArray
    private val rayon: Float
    private val coeur: Float
    private val couleurCoeur: Int
    private val plume = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        val lettres = mot.toCharArray()
        val n = lettres.size
        val w = zone.width()
        val h = zone.height()

        // Un mot de sept lettres occupe toute la largeur utile ; un plus court
        // s'arrête avant, un plus long resserre son pas. Le pas ne dépend donc
        // jamais de la longueur du mot voisin, ce qui est la condition pour
        // que deux préfixes identiques se superposent.
        val pas = w * 0.76f / (maxOf(n, 7) - 1).toFloat()
        val x0 = zone.left + w * 0.12f
        val cy = zone.centerY()
        val amp = h * 0.30f

        val xs = FloatArray(n)
        val ys = FloatArray(n)
        var voyelles = 0
        for (i in 0 until n) {
            val ch = lettres[i]
            val estVoyelle = ch.lowercaseChar() in VOYELLES
            if (estVoyelle) voyelles++
            // Un mot d'une seule lettre n'a pas de tracé : on le centre.
            xs[i] = if (n == 1) zone.centerX() else x0 + pas * i
            ys[i] = cy + (if (estVoyelle) -1f else 1f) *
                amp * (0.42f + 0.58f * ((ch.code % 7) / 6f))
        }

        // Deux quadratiques par segment, par le milieu : la courbe passe par
        // chaque lettre sans le dépassement qu'une seule donnerait.
        if (n >= 2) {
            chemin.moveTo(xs[0], ys[0])
            for (i in 0 until n - 1) {
                val ax = xs[i]; val ay = ys[i]
                val bx = xs[i + 1]; val by = ys[i + 1]
                chemin.quadTo(ax + (bx - ax) * 0.55f, ay, (ax + bx) / 2f, (ay + by) / 2f)
                chemin.quadTo(bx - (bx - ax) * 0.55f, by, bx, by)
            }
        }

        noeudsX = FloatArray(voyelles)
        noeudsY = FloatArray(voyelles)
        var k = 0
        for (i in 0 until n) {
            if (lettres[i].lowercaseChar() in VOYELLES) {
                noeudsX[k] = xs[i]; noeudsY[k] = ys[i]; k++
            }
        }

        // Un mot long doit maigrir, sinon ses nœuds se recouvrent : à seize
        // lettres, un pas fait onze unités et un nœud d'origine en ferait dix
        // de rayon. Sept lettres est la longueur de référence.
        val maigreur = (7f / maxOf(n, 7)).coerceAtMost(1f)
        val epaisseur = h * 0.055f * maigreur
        rayon = h * 0.055f * maigreur
        coeur = h * 0.024f * maigreur
        couleurCoeur = Color.HSVToColor(floatArrayOf(teinte, 0.55f, 0.62f))

        plume.strokeWidth = epaisseur
        plume.strokeCap = Paint.Cap.ROUND
        plume.strokeJoin = Paint.Join.ROUND
    }

    /**
     * Le tracé se pose deux fois : une ombre décalée, puis le trait clair.
     *
     * C'est la gravure du reste du carnet — la lumière vient d'en haut à
     * gauche, comme pour les volutes et les écus — et c'est ce qui empêche un
     * trait blanc de disparaître sur la face claire d'une commune.
     */
    override fun peindre(c: Canvas) {
        plume.style = Paint.Style.STROKE
        c.save()
        c.translate(1.4f, 1.8f)
        plume.color = OMBRE
        c.drawPath(chemin, plume)
        c.restore()
        plume.color = TRAIT
        c.drawPath(chemin, plume)

        plume.style = Paint.Style.FILL
        for (i in noeudsX.indices) {
            plume.color = OMBRE
            c.drawCircle(noeudsX[i] + 1.4f, noeudsY[i] + 1.8f, rayon, plume)
            plume.color = TRAIT
            c.drawCircle(noeudsX[i], noeudsY[i], rayon, plume)
            plume.color = couleurCoeur
            c.drawCircle(noeudsX[i], noeudsY[i], coeur, plume)
        }
    }

    private companion object {
        /** Les voyelles du luxembourgeois, diacritiques compris. */
        const val VOYELLES = "aeiouyäëéèêîïôöüû"
        val OMBRE = Color.argb(72, 0, 0, 0)
        val TRAIT = Color.argb(235, 255, 255, 255)
    }
}
