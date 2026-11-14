package com.example.kreyolkeyboard.carnet

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.robolectric.RuntimeEnvironment
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Calendar

/**
 * Les planches de relecture des cartes : pas un test, un outil, repris de
 * KreyolKeyb (2026-11). Les cartes y sont dessinées par le vrai code, sur un
 * vrai canevas (le rendu natif de Robolectric), sans appareil ni émulateur.
 *
 * Ne tourne que sur demande, avec le dossier où poser les images :
 *
 *     ./gradlew :app:testDebugUnitTest --tests '*PlancheCartesTest*' -Pplanche=/chemin/du/dossier [-PplancheCartes] [-PplancheSaisons]
 *
 * - `planche_cartes_<lieu>.png` : les cartes entières d'un lieu aux quatre
 *   raretés, telles que l'image de partage les fige (le théâtre de papier) ;
 * - `planche_lieux_<heure>.png` : la fenêtre de chaque lieu à une heure,
 *   droite puis penchée à gauche et à droite, pour voir glisser les cartons
 *   et leurs ombres ;
 * - `planche_saisons.png` : le surcadrage de quelques lieux aux quatre saisons.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlancheCartesTest {

    private val dossier: File? = System.getProperty("planche")?.takeIf { it.isNotBlank() }?.let { File(it) }

    private fun date(mois: Int): Long = Calendar.getInstance().apply { set(2026, mois, 15, 12, 0, 0) }.timeInMillis

    private fun blason(lieu: Lieu): Blasonnement = Blasonnement(champ = lieu.champ(), lieu = lieu)

    @Test
    fun cartes() {
        assumeTrue(dossier != null && System.getProperty("planche.cartes") != null)
        val ctx = RuntimeEnvironment.getApplication()
        dossier!!.mkdirs()
        for (lieu in Lieu.values()) {
            val cases = Rarete.values().mapIndexed { i, rarete ->
                val carte = CarteMot("Bréck", date(Calendar.JUNE), 1, i + 1, setOf(JeuCarte.WUERTPLAZ))
                val contenu = ContenuCarte(
                    carte = carte, rarete = rarete, rang = null, glose = "pont",
                    autresFormes = emptyList(), exemple = "D'Bréck ass rout.", blason = blason(lieu)
                )
                "${lieu.nom} · ${rarete.name}" to PartageCarte.dessiner(ctx, contenu)
            }
            planche(cases, 4, File(dossier, "planche_cartes_${lieu.name.lowercase()}.png"), reduire = 2)
        }
    }

    @Test
    fun lieux() {
        assumeTrue(dossier != null)
        dossier!!.mkdirs()
        // La fenêtre de la carte ouverte, en unités de carte, rendue à trois pixels par unité.
        val zone = Ornement.FENETRE
        val visible = android.graphics.RectF(zone.left, zone.top, zone.right, Ornement.PLAQUE.top + 4f)
        val echelle = 3f
        val decoupe = android.graphics.Path().apply { addRect(visible, android.graphics.Path.Direction.CW) }
        val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
        for (rarete in Rarete.values()) {
            val cases = ArrayList<Pair<String, Bitmap>>()
            for (lieu in Lieu.values()) {
                val motif = Motif("Bréck", rarete, zone, blason(lieu)).apply { profondeur = true }
                for (regard in floatArrayOf(-1f, 0f, 1f)) {
                    val b = Bitmap.createBitmap((visible.width() * echelle).toInt(), (visible.height() * echelle).toInt(),
                        Bitmap.Config.ARGB_8888)
                    val c = Canvas(b)
                    c.scale(echelle, echelle)
                    c.translate(-visible.left, -visible.top)
                    motif.peindre(c, pinceau, 0f, decoupe, echelle, avecIris = false, avecSujet = regard == 0f,
                        regardX = regard, regardY = 0f)
                    cases += "${lieu.nom} · regard $regard" to b
                }
            }
            planche(cases, 3, File(dossier, "planche_lieux_${rarete.name.lowercase()}.png"), reduire = 1)
        }
    }

    /**
     * Les saisons du surcadrage (`Paysages.Surcadre`) : quelques lieux, un
     * par essence de branche et par pied, aux quatre saisons, à l'heure de
     * la commune. Ne tourne qu'avec `-PplancheSaisons`.
     */
    @Test
    fun saisons() {
        assumeTrue(dossier != null && System.getProperty("planche.saisons") != null)
        dossier!!.mkdirs()
        val zone = Ornement.FENETRE
        val visible = android.graphics.RectF(zone.left, zone.top, zone.right, Ornement.PLAQUE.top + 4f)
        val echelle = 3f
        val decoupe = android.graphics.Path().apply { addRect(visible, android.graphics.Path.Direction.CW) }
        val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
        val cases = ArrayList<Pair<String, Bitmap>>()
        val lieux = listOf(Lieu.NEI_BRECK, Lieu.HAFF_REIMECH, Lieu.CONZEFENN, Lieu.MUSEL, Lieu.MELLERDALL, Lieu.GELLE_FRA)
        for (lieu in lieux) for (mois in intArrayOf(4, 7, 10, 1)) {
            val souvenir = Souvenir.du(2026, mois, 15)
            val motif = Motif("Bréck", Rarete.COMMUN, zone, blason(lieu), souvenir).apply { profondeur = true }
            val b = Bitmap.createBitmap((visible.width() * echelle).toInt(), (visible.height() * echelle).toInt(),
                Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            c.scale(echelle, echelle)
            c.translate(-visible.left, -visible.top)
            motif.peindre(c, pinceau, 0f, decoupe, echelle, avecIris = false, avecSujet = true, regardX = 0f, regardY = 0f)
            cases += "${lieu.nom} · ${souvenir.saison.nom}" to b
        }
        planche(cases, 4, File(dossier, "planche_saisons.png"), reduire = 1)
    }

    /**
     * Une même carte avant et après un réglage des couleurs (`Paysages.VIF`),
     * pour juger côte à côte. Ne tourne qu'avec `-PplancheVif`.
     */
    @Test
    fun vif() {
        assumeTrue(dossier != null && System.getProperty("planche.vif") != null)
        val ctx = RuntimeEnvironment.getApplication()
        dossier!!.mkdirs()
        val cases = ArrayList<Pair<String, Bitmap>>()
        for (rarete in listOf(Rarete.COMMUN, Rarete.RARE, Rarete.TRES_RARE)) for (vif in floatArrayOf(Paysages.VIF_RETENU)) {
            Paysages.VIF = vif
            val carte = CarteMot("Bréck", date(Calendar.JUNE), 1, 1, setOf(JeuCarte.WUERTPLAZ))
            val contenu = ContenuCarte(
                carte = carte, rarete = rarete, rang = null, glose = "pont",
                autresFormes = emptyList(), exemple = "D'Bréck ass rout.", blason = blason(Lieu.NEI_BRECK)
            )
            cases += "${rarete.name} · vif $vif" to PartageCarte.dessiner(ctx, contenu)
        }
        Paysages.VIF = Paysages.VIF_RETENU
        planche(cases, 3, File(dossier, "planche_vif.png"), reduire = 1)
    }

    private fun planche(cases: List<Pair<String, Bitmap>>, colonnes: Int, sortie: File, reduire: Int) {
        val l = cases[0].second.width / reduire
        val h = cases[0].second.height / reduire
        val lignes = (cases.size + colonnes - 1) / colonnes
        val out = Bitmap.createBitmap(colonnes * (l + 8), lignes * (h + 34), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { textSize = 22f; color = Color.BLACK }
        cases.forEachIndexed { i, (titre, b) ->
            val x = (i % colonnes) * (l + 8f)
            val y = (i / colonnes) * (h + 34f)
            c.drawText(titre, x + 4f, y + 24f, p)
            c.drawBitmap(Bitmap.createScaledBitmap(b, l, h, true), x, y + 32f, p)
        }
        sortie.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
