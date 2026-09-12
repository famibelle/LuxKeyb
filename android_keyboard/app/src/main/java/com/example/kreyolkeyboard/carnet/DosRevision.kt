package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.kreyolkeyboard.R
import kotlin.math.abs

/**
 * Le verso d'une carte de révision : la question, écrite sur le dos du carton.
 *
 * ## Pourquoi un dos, et pourquoi celui-ci
 *
 * La révision retournait déjà ses cartes — sauf qu'il n'y avait rien à
 * retourner. `revelation` faisait partir la carte de `rotationY = -85°` pour
 * l'amener à zéro, mais rien n'occupait ces -85° : la carte surgissait de
 * profil, du néant, et la question d'avant flottait sur un aplat gris. Il ne
 * manquait pas un décor, il manquait **la première moitié du geste**.
 *
 * Ce dos-ci est donc la face question. « Verso » n'y veut pas dire « caché » :
 * en [FormeQuestion.RECONNAISSANCE], le dos porte le mot luxembourgeois et le
 * recto son sens.
 *
 * ## Ce qu'un dos de révision n'a pas le droit de dire
 *
 * [DosDeCarte], celui de la pochette, porte déjà la règle : un dos qui
 * trahirait la carte supprimerait le seul instant que la pochette fabrique.
 * En révision elle se durcit, parce que la question honnête est « le
 * savez-vous ? » et que toute fuite corrompt l'auto-notation. Deux tentations
 * ont donc été écartées, toutes deux à portée de copier-coller :
 *
 * - **La teinte générative.** [Motif] tire sa teinte du condensé du mot. Un
 *   dos teinté serait reconnaissable *par mot* au bout de quelques semaines :
 *   la fuite parfaite, invisible à la relecture du code.
 * - **Le liseré de rareté.** La pochette l'allume volontairement. Ici,
 *   « cette carte est très rare » se lit « ce mot est difficile », et le
 *   joueur ajuste sa réponse avant d'avoir cherché.
 *
 * Le dos est donc rigoureusement le même pour les douze cartes d'une session.
 * C'est aussi ce qui interdit un dos aux couleurs du jeu : le paquet mélange
 * les sept, et la couleur trahirait la provenance en même temps qu'elle
 * ferait clignoter l'écran. La révision est le rituel de l'application,
 * au-dessus des sept jeux — d'où le logo, et non l'emoji d'un jeu.
 *
 * ## Le motif
 *
 * Il n'est pas une vignette du logo collée sur un fond : c'est **la
 * composition du logo agrandie**. Le logo porte deux angles qui se répondent
 * en diagonale, rouge en haut à gauche, bleu clair en bas à droite, le lion
 * blanc entre les deux. Le dos reprend cette structure à l'échelle du carton,
 * ce qui le rend net à toute taille sans le moindre asset, et laisse une
 * bande blanche oblique où le texte de la question vient naturellement se
 * poser.
 *
 * Le lion n'y figure qu'en filigrane très effacé, tiré du `ic_launcher` livré
 * dans l'APK. C'est un pis-aller assumé : il n'existe aucun tracé vectoriel du
 * lion, seulement des PNG dont le plus grand fait 192 px, et un filigrane est
 * la seule échelle où cette définition ne se voit pas. Le jour où le lion
 * existe en `Path` comme le reste du carnet, [lion] est le seul endroit à
 * changer.
 */
class DosRevision(context: Context) : Carton(context), SensorEventListener {

    override val hauteurUnites: Float = Ornement.HAUTEUR

    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private val carton = RectF(0f, 0f, Ornement.LARGEUR, Ornement.HAUTEUR)
    private val angleRouge: Path
    private val angleBleu: Path
    private val fond: LinearGradient

    /** Le panneau de saisie n'est tracé que si quelque chose s'y écrit. */
    var avecArdoise = false

    /** Le roulis du téléphone, ramené dans [-1, 1]. Voir [onSensorChanged]. */
    private var roulis = 0f
    private var capteurs: SensorManager? = null

    init {
        setWillNotDraw(false)
        angleRouge = angleHautGauche()
        angleBleu = angleBasDroit()
        fond = LinearGradient(
            0f, 0f, Ornement.LARGEUR * 0.6f, Ornement.HAUTEUR,
            BLANC, BLANC_OMBRE, Shader.TileMode.CLAMP
        )
    }

    /**
     * Les deux angles portent eux-mêmes le coin arrondi du carton.
     *
     * La solution évidente aurait été de tracer deux triangles francs et de
     * découper le tout à `clipPath`. Mais une découpe par chemin n'est pas
     * antialiasée : les coins du dos seraient crénelés là où ceux de la face
     * sont lisses, et c'est précisément le genre d'écart d'un pixel qui fait
     * lire le retournement comme deux objets qui se remplacent.
     */
    private fun angleHautGauche(): Path = Path().apply {
        val r = Ornement.RAYON
        moveTo(r, 0f)
        lineTo(ANGLE_HAUT, 0f)
        lineTo(0f, ANGLE_BAS)
        lineTo(0f, r)
        arcTo(RectF(0f, 0f, 2f * r, 2f * r), 180f, 90f)
        close()
    }

    private fun angleBasDroit(): Path = Path().apply {
        val r = Ornement.RAYON
        val l = Ornement.LARGEUR
        val h = Ornement.HAUTEUR
        moveTo(l - r, h)
        lineTo(l - ANGLE_HAUT, h)
        lineTo(l, h - ANGLE_BAS)
        lineTo(l, h - r)
        arcTo(RectF(l - 2f * r, h - 2f * r, l, h), 0f, 90f)
        close()
    }

    /**
     * L'ordre est celui d'une impression : le fond, les deux aplats, les
     * liserés qui les séparent, le filigrane, puis le panneau et le bord.
     *
     * Tout est tracé en unités de carte, comme la face : c'est ce qui rend le
     * dos superposable au recto au pixel près, quelle que soit la taille à
     * laquelle la scène décide de le poser.
     */
    override fun onDraw(canvas: Canvas) {
        val u = width / Ornement.LARGEUR
        if (u <= 0f) return
        val l = Ornement.LARGEUR
        val h = Ornement.HAUTEUR

        canvas.save()
        canvas.scale(u, u)

        pinceau.style = Paint.Style.FILL
        pinceau.shader = fond
        canvas.drawRoundRect(carton, Ornement.RAYON, Ornement.RAYON, pinceau)
        pinceau.shader = null

        // Les deux angles du logo, portés à l'échelle du carton.
        pinceau.color = ROUGE
        canvas.drawPath(angleRouge, pinceau)
        pinceau.color = BLEU
        canvas.drawPath(angleBleu, pinceau)

        // Le liseré blanc détache l'aplat de la bande ; le filet d'or, plus
        // fin et en retrait, est ce qui fait le carton plutôt que l'affiche.
        // Les quatre extrémités tombent sur des bords droits, loin des coins :
        // aucune n'a besoin d'être rattrapée par un arrondi.
        pinceau.style = Paint.Style.STROKE
        pinceau.color = BLANC
        pinceau.strokeWidth = 4f
        canvas.drawLine(ANGLE_HAUT, 0f, 0f, ANGLE_BAS, pinceau)
        canvas.drawLine(l - ANGLE_HAUT, h, l, h - ANGLE_BAS, pinceau)
        pinceau.color = OR
        pinceau.alpha = 140
        pinceau.strokeWidth = 1.2f
        canvas.drawLine(ANGLE_HAUT - 26f, 0f, 0f, ANGLE_BAS - 38f, pinceau)
        canvas.drawLine(l - ANGLE_HAUT + 26f, h, l, h - ANGLE_BAS + 38f, pinceau)
        pinceau.alpha = 255

        // Le filigrane, dans la bande claire. Il est posé en unités de carte
        // comme tout le reste, donc il suit la mise à l'échelle du canvas.
        lion(context)?.let { marque ->
            val cote = l * 0.62f
            val gauche = (l - cote) / 2f
            val haut = h * 0.30f - cote / 2f
            pinceau.style = Paint.Style.FILL
            pinceau.alpha = FILIGRANE
            canvas.drawBitmap(
                marque, null, RectF(gauche, haut, gauche + cote, haut + cote), pinceau
            )
            pinceau.alpha = 255
        }

        if (avecArdoise) {
            // L'ardoise est creusée à l'emplacement du panneau de texte du
            // recto : ce que le joueur tape apparaît exactement là où le sens
            // du mot l'attend de l'autre côté.
            pinceau.style = Paint.Style.FILL
            pinceau.color = BLANC
            pinceau.alpha = 235
            canvas.drawRoundRect(Ornement.PANNEAU, 7f, 7f, pinceau)
            pinceau.alpha = 255
            pinceau.style = Paint.Style.STROKE
            pinceau.strokeWidth = 1.4f
            pinceau.color = OR
            canvas.drawRoundRect(Ornement.PANNEAU, 7f, 7f, pinceau)
        }

        // Le bord, au même retrait que celui de la face.
        pinceau.style = Paint.Style.STROKE
        pinceau.strokeWidth = 1.6f
        pinceau.color = ENCRE
        pinceau.alpha = 46
        canvas.drawRoundRect(
            RectF(1.2f, 1.2f, l - 1.2f, h - 1.2f),
            Ornement.RAYON, Ornement.RAYON, pinceau
        )
        pinceau.alpha = 255

        // Le reflet, enfin : le dos étant le même pour toutes les cartes,
        // c'est la seule surface du carnet où le balayage se voit à chaque
        // question et pas seulement sur une rare.
        Ornement.refletBalaye(canvas, pinceau, roulis, h, 0x4A)
        canvas.restore()
    }

    // ------------------------------------------------------------- le vivant

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Pochette.animationsReduites(context)) return
        val manager =
            context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        // Même repli que la face : l'accéléromètre brut mêle la pesanteur à
        // l'accélération linéaire, et marcher suffisait à faire trembler le
        // reflet. Le capteur fusionné n'existe pas partout.
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

    override fun onAccuracyChanged(lequel: Sensor?, precision: Int) = Unit

    /**
     * Le roulis, lissé, et seulement quand il a vraiment changé.
     *
     * Le seuil compte plus ici que sur la face : un dos se retrace pour une
     * poignée d'ordres, mais il reste affiché tant que le joueur cherche sa
     * réponse, c'est-à-dire bien plus longtemps qu'une carte ouverte.
     */
    override fun onSensorChanged(evenement: SensorEvent) {
        if (evenement.values.isEmpty()) return
        val cible = (-evenement.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
        if (abs(cible - roulis) < 0.04f) return
        roulis += (cible - roulis) * 0.20f
        invalidate()
    }

    companion object {
        private const val ROUGE = 0xFFFF2F1D.toInt()
        private const val BLEU = 0xFF00A3ED.toInt()
        private const val BLANC = 0xFFFFFFFF.toInt()
        private const val BLANC_OMBRE = 0xFFE4E8EC.toInt()
        private const val OR = 0xFFE0B74E.toInt()
        private const val ENCRE = 0xFF1B1610.toInt()

        /** Où les deux angles coupent les bords, en unités de carte. */
        private const val ANGLE_HAUT = 176f
        private const val ANGLE_BAS = 258f

        /** L'opacité du filigrane, sur 255. Voir la note de classe. */
        private const val FILIGRANE = 34

        private var marque: Bitmap? = null
        private var marqueEssayee = false

        /**
         * Le lion, détouré de son fond.
         *
         * `ic_launcher` est livré sur un fond opaque `#F5F7F7` : posé tel quel
         * sur le carton, il y ferait un carré clair, ce qui se lit comme un
         * autocollant et non comme un filigrane. L'alpha est donc recalculé
         * depuis la distance à cette teinte, ce qui a l'avantage de conserver
         * l'antialiasing des traits plutôt que de les découper au seuil.
         *
         * Trente-sept mille pixels parcourus une fois pour la vie du
         * processus, et le résultat est partagé par toutes les cartes.
         */
        private fun lion(context: Context): Bitmap? {
            if (marqueEssayee) return marque
            marqueEssayee = true
            marque = try {
                val source = BitmapFactory.decodeResource(
                    context.resources, R.mipmap.ic_launcher
                ) ?: return null
                val l = source.width
                val h = source.height
                val pixels = IntArray(l * h)
                source.getPixels(pixels, 0, l, 0, 0, l, h)
                for (i in pixels.indices) {
                    val px = pixels[i]
                    val ecart = maxOf(
                        abs(Color.red(px) - 245),
                        abs(Color.green(px) - 247),
                        abs(Color.blue(px) - 247)
                    )
                    val a = minOf(Color.alpha(px), minOf(255, ecart * 255 / SEUIL_FOND))
                    pixels[i] = (a shl 24) or (px and 0x00FFFFFF)
                }
                Bitmap.createBitmap(pixels, l, h, Bitmap.Config.ARGB_8888)
            } catch (e: Throwable) {
                // Un filigrane manquant n'est pas une raison de perdre la
                // question : le dos tient debout sur sa seule géométrie.
                null
            }
            return marque
        }

        /** La largeur de la rampe de détourage, en niveaux. */
        private const val SEUIL_FOND = 58
    }
}
