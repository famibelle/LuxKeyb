package com.example.kreyolkeyboard.carnet

import android.app.AlertDialog
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import com.example.kreyolkeyboard.R
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * Le fichier d'impression d'une carte : le recto du jeu de cartes imprimé,
 * dessiné **par le code de l'appli**.
 *
 * Un premier BAT redessinait la carte à part, en SVG : il était moins beau que
 * la carte du téléphone (propriétaire, 2026-10-26). Rien ne remplace le dessin
 * qui a été mis au point écran par écran — le métal moulé, la plaque gravée,
 * les joyaux, le paysage aux trois plans. La carte étant tracée en unités de
 * carte (300 × 440), elle se dessine sans perte à la résolution d'impression.
 *
 * ## Le format
 *
 * Poker, 63 × 88 mm, fond perdu de 3 mm : un fichier de 69 × 94 mm, à
 * [DPI] points par pouce (1630 × 2220 px). La carte de l'appli est plus
 * étroite que le poker (300 × 440 contre 63 × 88) : elle est posée sur
 * [HAUTEUR_CARTE_MM] de haut, centrée, et le reste du format est rempli du
 * métal de son palier, jusque dans le fond perdu. Ses textes du bas et sa
 * gemme restent ainsi à plus de 3 mm de la coupe.
 *
 * ## Ce qui change sur la carte
 *
 * Voir `CarteCarnet.complete(…, impression = true)` : pas de médaillon de
 * provenance, de saison, de sceau ni de plume, qui racontent un carnet ; les
 * écus disent le **coût** et le **type**, qui servent au Wuertduell
 * (`docs/regle-du-jeu.md`), et la carte spéciale porte son pouvoir.
 *
 * Le fichier ne sort de l'appareil que par le partage Android, sur un geste :
 * un appui long sur « Partager » dans le lecteur de cartes. Il ne contient que
 * des données de dictionnaire, comme l'image partagée.
 */
object ImpressionCarte {

    private const val TAG = "ImpressionCarte"

    /** Le nom du jeu, écrit à la place du numéro d'entrée au carnet. */
    const val JEU = "Wuertduell"

    const val DPI = 600
    private const val LARGEUR_MM = 63f
    private const val HAUTEUR_MM = 88f
    private const val FOND_PERDU_MM = 3f
    private const val HAUTEUR_CARTE_MM = 86f

    private fun px(mm: Float): Int = (mm * DPI / 25.4f).roundToInt()

    /**
     * Le coût au jeu : il suit la longueur du mot, comme sa Force (règle du
     * jeu). Un nombre ne coûte rien.
     */
    fun cout(c: ContenuCarte): String {
        if (c.carte.nombre != null) return "0"
        val n = c.carte.forme.length
        return when {
            n <= 4 -> "●"
            n <= 7 -> "◆◆"
            n <= 10 -> "★★★"
            else -> "✦✦✦✦"
        }
    }

    /** Le type au jeu, lu sur la catégorie de la pastille : tous les noms se jouent pareil. */
    @StringRes
    fun type(@StringRes categorie: Int): Int = when (categorie) {
        R.string.cat_nombre, R.string.cat_verbe, R.string.cat_adjectif -> categorie
        R.string.cat_nom, R.string.cat_nom_f, R.string.cat_nom_m, R.string.cat_nom_n,
        R.string.cat_nom_mf, R.string.cat_nom_mn, R.string.cat_nom_fn, R.string.cat_nom_propre -> R.string.cat_nom
        else -> R.string.cat_mot
    }

    /** Le pouvoir d'une carte spéciale, écrit sur la carte imprimée ; `null` pour les autres. */
    fun pouvoir(context: Context, c: ContenuCarte): String? =
        if (CarteFierkelsmaart.est(c.carte.forme)) context.getString(R.string.fierkelsmaart_pouvoir) else null

    /**
     * L'appui long sur « Partager » : la carte montrée, ou la carte
     * Fierkelsmaart, que personne ne possède encore (aucun jeu ne la donne).
     */
    fun choisir(context: Context, montree: ContenuCarte) {
        AlertDialog.Builder(context)
            .setTitle(R.string.impression_titre)
            .setItems(
                arrayOf(
                    context.getString(R.string.impression_cette_carte, montree.carte.forme),
                    context.getString(R.string.impression_fierkelsmaart)
                )
            ) { _, choix ->
                exporter(context, if (choix == 0) montree else CarteFierkelsmaart.contenu(context))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Dessine le recto, l'enregistre et ouvre le partage.
     *
     * La carte prépare son métal et son paysage sur deux fils de fond, comme
     * dans le lecteur ([CarteOrnee.attendreSaMatiere]) : à cette largeur, le
     * métal moulé se calcule en quelques secondes. Le PNG s'écrit lui aussi
     * hors du fil principal.
     */
    fun exporter(context: Context, contenu: ContenuCarte) {
        Toast.makeText(context, R.string.impression_en_cours, Toast.LENGTH_LONG).show()
        val largeur = px(LARGEUR_MM + 2 * FOND_PERDU_MM)
        val hauteur = px(HAUTEUR_MM + 2 * FOND_PERDU_MM)
        val hauteurCarte = px(HAUTEUR_CARTE_MM)
        val largeurCarte = (hauteurCarte * Ornement.LARGEUR / Ornement.HAUTEUR).roundToInt()

        val carte = CarteCarnet.complete(context, contenu, impression = true) as? CarteOrnee ?: return
        carte.attendreSaMatiere {
            try {
                val image = dessiner(carte, contenu.rarete, largeur, hauteur)
                val principal = Handler(Looper.getMainLooper())
                Thread({
                    val fichier = try {
                        enregistrer(context, image, contenu.carte.forme)
                    } catch (e: Exception) {
                        Log.e(TAG, "Fichier d'impression non écrit: ${e.message}", e)
                        null
                    } finally {
                        image.recycle()
                    }
                    if (fichier != null) principal.post { partager(context, fichier) }
                }, "impression").start()
            } catch (e: Throwable) {
                // Un OutOfMemoryError sur un petit téléphone : rien n'est perdu.
                Log.e(TAG, "Impression impossible: ${e.message}", e)
            }
        }
        carte.measure(
            View.MeasureSpec.makeMeasureSpec(largeurCarte, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        carte.layout(0, 0, carte.measuredWidth, carte.measuredHeight)
    }

    /**
     * Le métal du palier sur tout le format, fond perdu compris, puis la
     * carte au centre. Le métal suit la diagonale du cadre : clair en haut à
     * gauche, sombre en bas à droite, comme la lumière du carnet.
     */
    private fun dessiner(carte: CarteOrnee, rarete: Rarete, largeur: Int, hauteur: Int): Bitmap {
        val image = Bitmap.createBitmap(largeur, hauteur, Bitmap.Config.ARGB_8888)
        val c = Canvas(image)
        val m = Ornement.metal(rarete)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(
            0f, 0f, largeur * 0.6f, hauteur.toFloat(),
            intArrayOf(m.hi, m.mid, m.lo), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, largeur.toFloat(), hauteur.toFloat(), p)
        c.save()
        c.translate((largeur - carte.width) / 2f, (hauteur - carte.height) / 2f)
        carte.draw(c)
        c.restore()
        return image
    }

    private fun enregistrer(context: Context, image: Bitmap, forme: String): File {
        val dossier = File(context.cacheDir, "images").apply { mkdirs() }
        // Un seul fichier d'impression dans le cache : le précédent s'efface.
        dossier.listFiles { f -> f.name.startsWith("impression_") }?.forEach { it.delete() }
        val nom = forme.filter { it.isLetterOrDigit() }.ifEmpty { "carte" }
        val fichier = File(dossier, "impression_${nom}_69x94mm_${DPI}dpi.png")
        FileOutputStream(fichier).use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return fichier
    }

    /** La recette de [PartageCarte], `ClipData` comprise (Android 14). */
    private fun partager(context: Context, fichier: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fichier)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(context.contentResolver, fichier.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(intent, context.getString(R.string.impression_partager))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Partage impossible: ${e.message}", e)
        }
    }
}
