package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.View
import android.view.WindowManager
import java.util.WeakHashMap

/**
 * L'inclinaison du téléphone, rendue à la carte ouverte.
 *
 * ## Pourquoi la carte bouge
 *
 * Le reflet spéculaire d'une carte distinguée suit déjà le roulis de
 * l'appareil (voir `Ornement.dessinerReflet`). Tant que la carte reste
 * immobile, cette lumière glisse sur un objet qui ne bouge pas : l'œil le
 * lit comme un défaut, pas comme un reflet. Faire pivoter la carte de
 * quelques degrés referme la boucle — la lumière a enfin une surface qui
 * lui donne raison.
 *
 * ## Ce que ça coûte
 *
 * Rien, ou presque. `rotationX` et `rotationY` sont des propriétés du
 * `RenderNode` : le GPU les applique à la composition, sans jamais
 * repasser par `onDraw`. Le reflet, lui, appelle `invalidate()` et rejoue
 * quelques centaines d'ordres de tracé. L'inclinaison est donc l'effet le
 * moins cher des deux, alors que c'est le plus visible.
 *
 * ## Les quatre décisions
 *
 * - **Le neutre est relatif, pas absolu.** Personne ne tient son téléphone
 *   à plat : au repos, le tangage vaut déjà soixante degrés. Une carte
 *   calée sur l'orientation absolue serait donc penchée en permanence. On
 *   retient l'orientation du premier échantillon comme repos, et on ne
 *   joue que l'écart. Un [RAPPEL] très lent ramène ce repos vers la
 *   posture courante, sans quoi la carte resterait de travers dès que le
 *   joueur s'allonge ou change de main.
 * - **[AMPLITUDE] est petite, et ce n'est pas de la timidité.** Au-delà
 *   d'une dizaine de degrés, les filets d'or d'un pixel se mettent à
 *   scintiller — l'anticrénelage ne suit pas le sous-pixel en mouvement —
 *   et la typographie du bord fuyant devient illisible. Les jeux de cartes
 *   vont plus loin, mais ils ont de la grande illustration là où le carnet
 *   a du texte de neuf unités.
 * - **La carte contre-pivote.** Quand le bord droit du téléphone descend,
 *   le bord droit de la carte vient vers le joueur. La carte résiste au
 *   geste au lieu de le suivre, comme un objet posé qui garderait son
 *   aplomb : c'est ce décalage qui se lit comme de la profondeur. Une
 *   carte qui suivrait exactement l'écran ne bougerait pas, puisqu'elle
 *   est déjà dessinée dessus.
 * - **[TYPE_GRAVITY][Sensor.TYPE_GRAVITY] plutôt que l'accéléromètre brut.**
 *   L'accéléromètre mélange la pesanteur et l'accélération linéaire :
 *   marcher suffit à faire trembler la carte. Le capteur fusionné isole la
 *   pesanteur et supprime ce tremblement.
 *
 * ## Ce que l'inclinaison n'a pas le droit de faire
 *
 * Elle ne récompense pas. L'ornement monte avec la rareté ; la physique,
 * non. Une commune qui ne répondrait pas à la main se lirait comme un
 * bogue, pas comme une carte moins précieuse — le privilège d'un haut
 * palier, c'est la lumière, pas le poids.
 *
 * Et elle ne descend jamais dans la grille : seule la carte ouverte suit
 * l'appareil. Une grille entière qui s'incline à l'unisson donne le mal de
 * mer et se bat contre le défilement.
 */
object Inclinaison {

    /**
     * Le débattement en degrés, de part et d'autre du repos.
     *
     * Public parce que [Carton] en a besoin : un appui du doigt s'exprime en
     * degrés, et le tracé de la tranche en roulis. Sans le même dénominateur
     * des deux côtés, l'épaisseur du carton ne correspondrait pas à sa
     * propre inclinaison.
     */
    const val AMPLITUDE = 7f

    /**
     * Le lissage du suivi.
     *
     * Un filtre passe-bas donne du poids à la carte : elle arrive sur son
     * inclinaison au lieu d'y sauter. C'est la même intention que le
     * lissage du reflet, et il faut que les deux se ressemblent — sinon la
     * lumière devance la surface qui la porte.
     */
    private const val LISSAGE = 0.18f

    /**
     * La dérive du repos vers la posture courante.
     *
     * Très lent — de l'ordre d'une dizaine de secondes — pour que la carte
     * tenue penchée finisse par se remettre d'aplomb sans que le
     * mouvement se voie.
     */
    private const val RAPPEL = 0.006f

    /** La distance de caméra par défaut d'Android, en densités. */
    private const val CAMERA_DEFAUT = 1280f

    /**
     * Une perspective assez lointaine pour que sept degrés restent une
     * inclinaison et non un écrasement du bord fuyant.
     */
    private const val CAMERA = 9000f

    /**
     * Les vues déjà suivies, pour ne pas en armer une deux fois.
     *
     * On n'y range qu'un drapeau, jamais le suiveur : une `WeakHashMap` dont
     * la valeur référencerait sa clé retiendrait la vue pour toujours. Le
     * suiveur, lui, est tenu en vie par la liste d'écouteurs de la vue —
     * c'est-à-dire exactement aussi longtemps qu'il doit l'être.
     */
    private val suivies = WeakHashMap<View, Boolean>()

    /**
     * Ce qui incline chaque vue, en degrés : `[pesanteurX, pesanteurY,
     * appuiX, appuiY]`.
     *
     * ## Pourquoi une somme, et pas deux écrivains
     *
     * `rotationX` et `rotationY` n'ont qu'une valeur. Tant que la pesanteur
     * était seule à écrire dedans, la question ne se posait pas ; depuis que
     * le doigt enfonce le carton, deux sources visent la même propriété, et
     * la dernière servie gagne — soixante fois par seconde, ce qui se voit
     * comme un tremblement et non comme un appui.
     *
     * Elles sont donc rangées séparément et **additionnées** au moment
     * d'écrire. La pesanteur continue de dire où est le bas ; l'appui dit
     * seulement de combien le carton s'enfonce en plus. Les deux restent
     * vraies en même temps, ce qui est exactement ce qu'on observe en posant
     * le pouce sur une carte tenue en main.
     *
     * Un `FloatArray` en valeur, et non le suiveur : une [WeakHashMap] dont
     * la valeur référencerait sa clé retiendrait la vue pour toujours. Quatre
     * flottants ne référencent rien.
     */
    private val etats = WeakHashMap<View, FloatArray>()

    private fun etat(vue: View): FloatArray =
        etats.getOrPut(vue) { FloatArray(4) }

    private fun appliquer(vue: View, e: FloatArray) {
        vue.rotationX = e[0] + e[2]
        vue.rotationY = e[1] + e[3]
    }

    /**
     * L'enfoncement du carton sous le doigt, en degrés, composé avec la
     * pesanteur s'il y en a une.
     *
     * Passer par ici plutôt que d'écrire dans `rotationX` directement est ce
     * qui rend l'appui visible sur une carte du carnet, où l'inclinaison est
     * armée **sur la carte elle-même** ; sans cela, le prochain échantillon
     * du capteur effacerait l'appui avant qu'on l'ait vu.
     */
    fun appui(vue: View, degresX: Float, degresY: Float) {
        val e = etat(vue)
        e[2] = degresX
        e[3] = degresY
        appliquer(vue, e)
    }

    /**
     * Recule la caméra de [vue] assez pour que quelques degrés restent une
     * inclinaison et non un écrasement du bord fuyant.
     *
     * Les appelants qui ont déjà réglé leur perspective — la pochette et la
     * révision le font pour leur retournement — gardent la leur : on ne
     * touche qu'au défaut d'Android.
     */
    fun perspective(vue: View) {
        val densite = vue.resources.displayMetrics.density
        if (vue.cameraDistance <= CAMERA_DEFAUT * densite) {
            vue.cameraDistance = CAMERA * densite
        }
    }

    /**
     * Fait suivre à [vue] l'inclinaison de l'appareil.
     *
     * À appeler **une fois le retournement terminé**, depuis son
     * `withEndAction` : l'ouverture d'une carte anime déjà `rotationY`, et
     * deux écrivains sur la même propriété font trembler la carte pendant
     * toute l'animation.
     *
     * Le suivi s'arme et se désarme tout seul avec la vue ; il n'y a rien à
     * libérer côté appelant.
     */
    fun suivre(vue: View) {
        if (suivies.containsKey(vue)) return
        if (Pochette.animationsReduites(vue.context)) return
        val manager =
            vue.context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val capteur = manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return

        perspective(vue)

        val suiveur = Suiveur(vue, manager, capteur)
        suivies[vue] = true
        vue.addOnAttachStateChangeListener(suiveur)
        if (vue.isAttachedToWindow) suiveur.armer()
    }

    /**
     * Le suivi d'une vue : un écouteur de capteur qui ne vit que tant que la
     * vue est à l'écran.
     */
    private class Suiveur(
        private val vue: View,
        private val manager: SensorManager,
        private val capteur: Sensor
    ) : SensorEventListener, View.OnAttachStateChangeListener {

        private var arme = false
        private var cale = false
        private var reposX = 0f
        private var reposY = 0f
        private var roulis = 0f
        private var tangage = 0f

        fun armer() {
            if (arme) return
            manager.registerListener(this, capteur, SensorManager.SENSOR_DELAY_UI)
            arme = true
        }

        private fun desarmer() {
            if (!arme) return
            manager.unregisterListener(this)
            arme = false
            // Le repos est perdu avec le capteur : une carte qui revient à
            // l'écran doit reprendre la posture du moment, pas celle d'avant.
            cale = false
            roulis = 0f
            tangage = 0f
        }

        override fun onViewAttachedToWindow(v: View) = armer()

        /**
         * La vue quitte l'écran : on rend le capteur et on remet la carte
         * d'aplomb, pour qu'elle ne réapparaisse pas figée sur la dernière
         * inclinaison connue. L'écouteur, lui, reste en place — si la vue
         * revient, le suivi reprend de lui-même.
         */
        override fun onViewDetachedFromWindow(v: View) {
            desarmer()
            val e = etat(vue)
            e[0] = 0f
            e[1] = 0f
            appliquer(vue, e)
        }

        override fun onAccuracyChanged(lequel: Sensor?, precision: Int) = Unit

        override fun onSensorChanged(evenement: SensorEvent) {
            if (evenement.values.size < 2) return

            // Le capteur parle dans le repère de l'appareil, pas dans celui de
            // l'écran : en paysage, ses deux axes sont échangés.
            val brutX = evenement.values[0]
            val brutY = evenement.values[1]
            var x = brutX
            var y = brutY
            when (rotationEcran()) {
                Surface.ROTATION_90 -> { x = -brutY; y = brutX }
                Surface.ROTATION_180 -> { x = -brutX; y = -brutY }
                Surface.ROTATION_270 -> { x = brutY; y = -brutX }
            }

            if (!cale) {
                reposX = x
                reposY = y
                cale = true
            }

            val g = SensorManager.GRAVITY_EARTH
            val cibleRoulis = ((reposX - x) / g).coerceIn(-1f, 1f)
            val cibleTangage = ((reposY - y) / g).coerceIn(-1f, 1f)
            roulis += (cibleRoulis - roulis) * LISSAGE
            tangage += (cibleTangage - tangage) * LISSAGE
            reposX += (x - reposX) * RAPPEL
            reposY += (y - reposY) * RAPPEL

            // Deux écritures de propriété, pas un seul ordre de tracé : la
            // carte n'est pas redessinée, elle est recomposée.
            val e = etat(vue)
            e[0] = tangage * AMPLITUDE
            e[1] = roulis * AMPLITUDE
            appliquer(vue, e)
        }

        @Suppress("DEPRECATION")
        private fun rotationEcran(): Int {
            val fenetres = vue.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            return fenetres?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        }
    }
}
