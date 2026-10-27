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
 * métal de son palier, jusque dans le fond perdu. Sa ligne du bas et sa
 * gemme restent ainsi à près de 4 mm de la coupe : à 86 mm, la ligne du bas
 * tombait pile sur la limite des 3 mm de sécurité (BAT v2, 2026-10-26).
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
    private const val HAUTEUR_CARTE_MM = 84f

    private fun px(mm: Float): Int = (mm * DPI / 25.4f).roundToInt()

    /**
     * Le coût au jeu, en pièces : il suit la longueur du mot, comme sa Force
     * (règle du jeu). 1 à 4 lettres, 1 pièce ; 5 à 7, 2 ; 8 à 10, 3 ; 11 et
     * plus, 4. Un nombre ne coûte rien.
     *
     * L'écu montrait ● ◆◆ ★★★ ✦✦✦✦ : seul le nombre de symboles comptait, la
     * forme changeait sans rien dire, et c'étaient les symboles de la rareté.
     * Le propriétaire ne les comprenait pas, et le public commence à 6 ans
     * (2026-10-27) : des pièces identiques se comptent d'un coup d'œil
     * jusqu'à quatre, et chacun sait qu'on paie avec.
     */
    fun cout(c: ContenuCarte): Int {
        if (c.carte.nombre != null) return 0
        val n = c.carte.forme.length
        return when {
            n <= 4 -> 1
            n <= 7 -> 2
            n <= 10 -> 3
            else -> 4
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

    /**
     * Les ensembles numérotés de `cartes_imprimees.json`, dans l'ordre de la
     * collection : la boîte de départ, puis les extensions.
     *
     * Un numéro par carte, comme sur les cartes à collectionner : il dit ce
     * qui manque et permet une liste à cocher (idée reprise des cartes du
     * commerce, 2026-10-27). Sans total : la collection grandit avec les
     * extensions, et un « / 100 » imprimé serait faux à la suivante.
     *
     * La carte promotionnelle (FK27, la Fierkelsmaart) est hors numérotation,
     * comme les cartes promo des grands jeux : son édition se lit déjà à
     * droite de la ligne.
     */
    val COLLECTION = listOf("B1", "SCHOUL1", "NATUR1")

    /**
     * Le numéro de collection d'une carte : sa place dans les ensembles de
     * [COLLECTION], mis bout à bout (Kaz 1, sechs 52, Buch 53…), ou `null`
     * pour une carte hors collection.
     *
     * Les numéros se lisent sur l'ordre des listes : une fois une édition
     * imprimée, ne plus réordonner ses cartes ni en insérer, et mettre les
     * nouvelles dans un ensemble ajouté à la fin. Si un ensemble manque, rien
     * n'est numéroté plutôt que de décaler tous les numéros qui le suivent.
     */
    fun numero(forme: String, nombre: Int?, ensembles: Map<String, List<String>>): Int? {
        val cle = if (nombre != null) "$forme#$nombre" else forme
        var avant = 0
        for (id in COLLECTION) {
            val cartes = ensembles[id] ?: return null
            val i = cartes.indexOf(cle)
            if (i >= 0) return avant + i + 1
            avant += cartes.size
        }
        return null
    }

    /**
     * Les paquets du jeu imprimé, dont le médaillon de la carte porte
     * l'emblème ([Ornement.dessinerMedaillonImprime]). [nom] est celui des
     * « paquets » de `cartes_imprimees.json`, écrit par
     * `scripts/candidats_cartes.py`.
     */
    enum class Paquet(val nom: String) {
        DEIEREN("Déieren"), IESSEN("Iessen"), SCHOUL("Schoul"), NATUR("Natur"), FIERKELSMAART("Fierkelsmaart");

        companion object {
            fun deNom(nom: String): Paquet? = entries.firstOrNull { it.nom == nom }
        }
    }

    /**
     * Le paquet d'une carte : l'ensemble qui la contient, découpé par ses
     * « paquets » dans l'ordre de ses cartes (la boîte : 26 Déieren puis
     * 26 Iessen). `null` pour une carte hors collection, ou si l'actif ne dit
     * pas ses paquets.
     */
    fun paquet(
        forme: String, nombre: Int?,
        ensembles: Map<String, List<String>>, paquets: Map<String, List<Pair<String, Int>>>
    ): Paquet? {
        val cle = if (nombre != null) "$forme#$nombre" else forme
        for ((id, cartes) in ensembles) {
            val i = cartes.indexOf(cle)
            if (i < 0) continue
            var fin = 0
            for ((nom, taille) in paquets[id] ?: return null) {
                fin += taille
                if (i < fin) return Paquet.deNom(nom)
            }
            return null
        }
        return null
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
