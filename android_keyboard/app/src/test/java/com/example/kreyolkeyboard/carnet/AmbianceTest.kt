package com.example.kreyolkeyboard.carnet

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * La neige de la carte ouverte : où elle tombe, et qu'elle suit la main.
 *
 * La dérive ne se voit pas sur un téléphone posé sur une table, ni sur
 * l'émulateur, trop lent pour la filmer : ce test est le seul endroit où le
 * sens de la boule à neige est vérifié.
 */
class AmbianceTest {

    private fun fenetre() = RectF().apply { left = 32f; top = 36f; right = 268f; bottom = 188f }

    /** Un canevas qui ne dessine rien et retient où sont posés les flocons. */
    private class Releve : Canvas() {
        val x = ArrayList<Float>()
        val y = ArrayList<Float>()
        // La neige se pose par ses disques (liseré puis flocon, au même
        // centre), les feuilles par la translation qui précède leur tracé.
        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
            x.add(cx)
            y.add(cy)
        }
        override fun translate(dx: Float, dy: Float) {
            x.add(dx)
            y.add(dy)
        }
    }

    /** L'ambiance de [saison] après [secondes], téléphone tenu selon le bas ([bx], [by]). */
    private fun apres(
        bx: Float, by: Float, secondes: Float,
        saison: Saison = Saison.WANTER, avant: (Ambiance) -> Unit = {}, fete: Fete? = null
    ): Releve {
        val a = Ambiance.pour(Souvenir(saison, fete), fenetre(), 7)!!
        avant(a)
        var t = 0f
        while (t < secondes) {
            a.orienter(bx, by)
            a.avancer(0.05f)
            t += 0.05f
        }
        return Releve().also { a.dessiner(it, Paint()) }
    }

    private fun mediane(v: List<Float>) = v.sorted()[v.size / 2]

    /** Le déplacement médian des flocons entre deux relevés, (x, y). */
    private fun deplacement(de: Releve, a: Releve): Pair<Float, Float> = Pair(
        mediane(a.x.indices.map { a.x[it] - de.x[it] }),
        mediane(a.y.indices.map { a.y[it] - de.y[it] })
    )

    /** Toutes les ambiances : chaque saison, de jour et de nuit, et chaque fête. */
    private fun toutes(): List<Pair<String, () -> Ambiance>> {
        val l = ArrayList<Pair<String, () -> Ambiance>>()
        for (s in Saison.values()) for (nuit in listOf(false, true)) {
            l.add("$s nuit=$nuit" to { Ambiance.pour(Souvenir(s, null), fenetre(), 7, nuit)!! })
        }
        for (f in Fete.values()) {
            l.add("$f" to { Ambiance.pour(Souvenir(Saison.WANTER, f), fenetre(), 7)!! })
        }
        return l
    }

    @Test
    fun chaqueSaisonEtChaqueFeteOntLeurAmbiance() {
        for ((nom, fabrique) in toutes()) assertNotNull(nom, fabrique())
        assertNull(Ambiance.pour(null, fenetre(), 1))
    }

    @Test
    fun toutResteDansLaFenetreEtSeDessine() {
        val f = fenetre()
        for ((nom, fabrique) in toutes()) {
            for ((bx, by) in listOf(0f to 1f, 0.7f to 0.7f, 0f to -1f, -1f to 0f, 0f to 0f)) {
                val a = fabrique()
                var t = 0f
                while (t < 20f) {
                    a.orienter(bx, by)
                    if (t in 5f..5.04f) a.secouer(1f)
                    a.avancer(0.05f)
                    t += 0.05f
                }
                val r = Releve().also { a.dessiner(it, Paint()) }
                assertTrue("$nom ($bx, $by) ne dessine rien", r.x.isNotEmpty())
                // Les étincelles d'un feu peuvent sortir un peu : la fenêtre
                // les découpe. Rien ne doit partir au loin.
                assertTrue("$nom ($bx, $by) sort en x", r.x.all { it > f.left - 40f && it < f.right + 40f })
                assertTrue("$nom ($bx, $by) sort en y", r.y.all { it > f.top - 40f && it < f.bottom + 40f })
            }
        }
    }

    @Test
    fun lesBraisesMontent() {
        val b: (Ambiance) -> Unit = {}
        val de = apres(0f, 1f, 1f, Saison.WANTER, b, Fete.BUERGBRENNEN)
        val a = apres(0f, 1f, 2f, Saison.WANTER, b, Fete.BUERGBRENNEN)
        val (_, dy) = deplacement(de, a)
        assertTrue("montée : $dy", dy < -2f)
    }

    @Test
    fun lesFeuillesSuiventLaMemeBouleANeige() {
        val h = Saison.HIERSCHT
        val (_, debout) = deplacement(apres(0f, 1f, 1f, h), apres(0f, 1f, 2f, h))
        assertTrue("chute : $debout", debout > 2f)
        val (dx, _) = deplacement(apres(0.5f, 0.87f, 1f, h), apres(0.5f, 0.87f, 2f, h))
        assertTrue("dérive à droite : $dx", dx > 1f)
        val (_, retournee) = deplacement(apres(0f, -1f, 1f, h), apres(0f, -1f, 2f, h))
        assertTrue("remontée : $retournee", retournee < -2f)
        val secouee: (Ambiance) -> Unit = { it.secouer(1f) }
        val (_, montee) = deplacement(apres(0f, 1f, 0f, h, secouee), apres(0f, 1f, 0.3f, h, secouee))
        assertTrue("montée : $montee", montee < -5f)
        // Plus lentes que la neige : une feuille plane.
        val (_, neige) = deplacement(apres(0f, 1f, 1f), apres(0f, 1f, 2f))
        assertTrue("feuilles $debout contre neige $neige", debout < neige)
    }

    @Test
    fun deboutLaNeigeTombeDroit() {
        val (dx, dy) = deplacement(apres(0f, 1f, 0f), apres(0f, 1f, 1f))
        assertTrue("chute : $dy", dy > 4f)
        assertTrue("dérive : $dx", abs(dx) < 2f)
    }

    @Test
    fun penchéeLaNeigeTombeEnBiaisEtLeReste() {
        // Bord droit qui descend de 30° : le bas penche à droite.
        val depart = apres(0.5f, 0.87f, 1f)
        val (dx, dy) = deplacement(depart, apres(0.5f, 0.87f, 2f))
        assertTrue("dérive à droite : $dx", dx > 2f)
        assertTrue("chute : $dy", dy > 2f)
        // Toujours penchée après vingt secondes : pas de retour au repos.
        val (dx2, _) = deplacement(apres(0.5f, 0.87f, 20f), apres(0.5f, 0.87f, 21f))
        assertTrue("dérive tenue : $dx2", dx2 > 2f)
        val (gx, _) = deplacement(apres(-0.5f, 0.87f, 1f), apres(-0.5f, 0.87f, 2f))
        assertTrue("dérive à gauche : $gx", gx < -2f)
    }

    @Test
    fun teteEnBasLaNeigeRemonte() {
        val (_, dy) = deplacement(apres(0f, -1f, 1f), apres(0f, -1f, 2f))
        assertTrue("remontée : $dy", dy < -4f)
    }

    @Test
    fun aPlatLaNeigeDescendDoucement() {
        val (_, aPlat) = deplacement(apres(0f, 0f, 1f), apres(0f, 0f, 2f))
        val (_, debout) = deplacement(apres(0f, 1f, 1f), apres(0f, 1f, 2f))
        assertTrue("à plat : $aPlat", aPlat > 1f)
        assertTrue("plus lente qu'à la verticale : $aPlat contre $debout", aPlat < debout * 0.6f)
    }

    @Test
    fun uneSecousseSouleveLaNeigePuisElleRetombe() {
        val secouee: (Ambiance) -> Unit = { it.secouer(1f) }
        // Juste après le coup : la neige monte, à contre-pesanteur.
        val (_, montee) = deplacement(apres(0f, 1f, 0f, avant = secouee), apres(0f, 1f, 0.3f, avant = secouee))
        assertTrue("montée : $montee", montee < -5f)
        // Trois secondes plus tard : elle retombe comme avant.
        val (_, ensuite) = deplacement(apres(0f, 1f, 3f, avant = secouee), apres(0f, 1f, 4f, avant = secouee))
        assertTrue("retombée : $ensuite", ensuite > 4f)
    }
}
