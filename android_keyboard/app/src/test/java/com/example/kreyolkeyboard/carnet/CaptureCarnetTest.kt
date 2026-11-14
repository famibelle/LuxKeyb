package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.fragment.app.FragmentActivity
import com.example.kreyolkeyboard.VisiteGuidee
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Le carnet tel qu'un joueur l'ouvre : quelques dizaines de cartes gagnées à
 * Wuertplaz, rendues par le vrai [CarnetFragment] dans un téléphone de
 * 360 × 800 dp. Ne tourne qu'avec `-Pplanche=<dossier> -PplancheCarnet`.
 *
 * - `carnet_grille.png` : la grille des vignettes, au tri par défaut ;
 * - `carnet_series.png` : la vue des séries, l'album des paysages en tête ;
 * - `carnet_carte.png` : une carte rare ouverte, telle qu'au toucher d'une vignette.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "fr-w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptureCarnetTest {

    private val dossier: File? = System.getProperty("planche")?.takeIf { it.isNotBlank() }?.let { File(it) }

    private fun attendre(fois: Int = 40) {
        repeat(fois) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(150)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun capturer(v: View, sortie: File) {
        val l = 720
        val h = 1600
        v.measure(View.MeasureSpec.makeMeasureSpec(l, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, l, h)
        val b = Bitmap.createBitmap(l, h, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(b))
        sortie.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun remplir() {
        val ctx = RuntimeEnvironment.getApplication()
        // Le carnet d'un joueur qui connaît déjà la visite guidée.
        ctx.getSharedPreferences(VisiteGuidee.PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(VisiteGuidee.VISITE_CARNET, true).commit()
        Carnet.vider(ctx)
        // Les bascules des lieux notées sur un carnet vide : toutes les cartes
        // suivent la grille de 9 × 3, comme pour un joueur qui commence.
        Carnet.basculeDesLieux(ctx)
        Carnet.basculeDeLaGrille(ctx)
        val grilles = JSONObject(File("src/main/assets/luxemburgish_chassecroise.json").readText()).getJSONArray("grilles")
        var n = 0
        for (g in 0 until 8) {
            val mots = grilles.getJSONObject(g).getJSONArray("mots")
            for (i in 0 until mots.length()) if (Carnet.ajouter(ctx, mots.getJSONObject(i).getString("f"), JeuCarte.WUERTPLAZ)) n++
        }
    }

    private fun ouvrir(tri: String?): CarnetFragment {
        val activite = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val f = CarnetFragment()
        if (tri != null) {
            val champ = CarnetFragment::class.java.getDeclaredField("tri").apply { isAccessible = true }
            val valeur = champ.type.enumConstants.first { (it as Enum<*>).name == tri }
            champ.set(f, valeur)
        }
        f.show(activite.supportFragmentManager, "carnet")
        attendre()
        return f
    }

    @Test
    fun grille() {
        assumeTrue(dossier != null && System.getProperty("planche.carnet") != null)
        dossier!!.mkdirs()
        remplir()
        val f = ouvrir(null)
        capturer(f.dialog!!.window!!.decorView, File(dossier, "carnet_grille.png"))
    }

    @Test
    fun series() {
        assumeTrue(dossier != null && System.getProperty("planche.carnet") != null)
        dossier!!.mkdirs()
        remplir()
        val f = ouvrir("SERIES")
        capturer(f.dialog!!.window!!.decorView, File(dossier, "carnet_series.png"))
    }

    @Test
    fun carte() {
        assumeTrue(dossier != null && System.getProperty("planche.carnet") != null)
        dossier!!.mkdirs()
        remplir()
        val f = ouvrir(null)
        val ctx = RuntimeEnvironment.getApplication()
        val c = Carnet.cartes(ctx).map { CarteCarnet.contenu(ctx, it) }.first { it.rarete == Rarete.RARE }
        CarnetFragment::class.java.getDeclaredMethod("montrerCarte", ContenuCarte::class.java, List::class.java)
            .apply { isAccessible = true }.invoke(f, c, listOf(c))
        attendre()
        capturer(f.dialog!!.window!!.decorView, File(dossier, "carnet_carte.png"))
    }
}
