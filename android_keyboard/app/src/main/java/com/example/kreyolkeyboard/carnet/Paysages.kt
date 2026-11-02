package com.example.kreyolkeyboard.carnet

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Le lieu que la fenêtre d'une carte montre : un par champ de sens.
 *
 * ## Pourquoi un lieu
 *
 * La fenêtre portait une matière teintée, puis le tracé du mot. Huit teintes
 * sur une face désaturée se confondaient à 160 dp, d'où la partition qui
 * doublait la couleur par une forme. Un paysage fait les deux à la fois : la
 * Rout Bréck ne se confond avec aucun autre lieu, en deutéranopie comme en
 * vignette, et c'est une raison de collectionner que la carte n'avait pas.
 *
 * Les noms sont ceux du pays, en luxembourgeois. Ils restent à faire relire
 * par quelqu'un qui parle la langue ; ils n'apparaissent pour l'instant qu'ici.
 *
 * Le dernier, [BREEDEWEE], n'est la fenêtre d'aucun champ : c'est le lieu des
 * cartes venues du jeu imprimé (voir [CartesPapier.corriger]), qui le portent
 * à la place de celui de leur champ. Il reste hors de l'album des paysages
 * ([ALBUM]) : l'album ne propose pas une case qu'aucun jeu ne remplit.
 */
enum class Lieu(val nom: String) {
    ROUT_BRECK("Rout Bréck"),
    VEIANEN("Veianen"),
    ESCH_SAUER("Esch-Sauer"),
    MUSEL("Musel"),
    NEIMENSTER("Neimënster"),
    GELLE_FRA("Gëlle Fra"),
    FOUER("Schueberfouer"),
    MELLERDALL("Mëllerdall"),
    STAD("Stad"),
    BREEDEWEE("Breedewee");

    /** Le champ dont ce lieu est la fenêtre, `null` pour la ville des mots sans champ et le Breedewee. */
    fun champ(): Champ? = Champ.values().firstOrNull { de(it) == this }

    companion object {
        /** Les neuf lieux des champs, ceux de l'album des paysages. */
        val ALBUM: List<Lieu> = values().filter { it != BREEDEWEE }

        /** Le lieu d'une carte : celui qu'elle impose, sinon celui de son champ. */
        fun de(blason: Blasonnement): Lieu = blason.lieu ?: de(blason.champ)

        fun de(champ: Champ?): Lieu = when (champ) {
            Champ.MOUVEMENT -> ROUT_BRECK
            Champ.TEMPS -> VEIANEN
            Champ.TERRITOIRE -> ESCH_SAUER
            Champ.ECONOMIE -> MUSEL
            Champ.SAVOIR -> NEIMENSTER
            Champ.PUBLIC -> GELLE_FRA
            Champ.SOCIETE -> FOUER
            Champ.VIE -> MELLERDALL
            null -> STAD
        }
    }
}

/**
 * L'heure d'une carte, et sa palette : la rareté fait avancer le jour.
 *
 * Commune le matin, peu commune l'après-midi, rare le soir aux fenêtres
 * allumées, très rare la nuit étoilée. La rareté reste fixée par le rang de
 * fréquence ; l'heure ne fait que la rendre visible d'un coup d'œil, sans
 * compter les joyaux.
 *
 * Les couleurs sont en entiers ARGB calculés à la main, sans `android.graphics.Color` :
 * la palette reste ainsi vérifiable sur la JVM des tests.
 */
class Heure private constructor(
    val rang: Int,
    val cielHaut: Int,
    val cielMilieu: Int?,
    val cielBas: Int,
    val astre: Int,
    val astreX: Float,
    val astreY: Float,
    val astreR: Float,
    val loin: Int,
    val milieu: Int,
    val pres: Int,
    val sol: Int,
    val pierre: Int,
    val toit: Int,
    val fenetre: Int,
    val allume: Boolean,
    val eau: Int,
    val nuit: Boolean
) {
    /** Le dégradé nacré des sujets, du clair au sombre : il prend la couleur de l'heure. */
    val nacre: IntArray = NACRES[rang]

    /** Le cœur des nœuds et le liseré des dessins. */
    val encre: Int = ENCRES[rang]

    companion object {
        private val NACRES = arrayOf(
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFEBE7EF.toInt(), 0xFFCDD5DF.toInt()),
            intArrayOf(0xFFFFFAF0.toInt(), 0xFFF6E5CC.toInt(), 0xFFDCC19C.toInt()),
            intArrayOf(0xFFFFF4EE.toInt(), 0xFFF3D6CF.toInt(), 0xFFD8A9A8.toInt()),
            intArrayOf(0xFFFFFBEE.toInt(), 0xFFF6E6B6.toInt(), 0xFFD9B766.toInt())
        )
        private val ENCRES = intArrayOf(
            0xFF7F8A96.toInt(), 0xFF8C6A48.toInt(), 0xFF6B4A5C.toInt(), 0xFF5B4A2A.toInt()
        )

        private fun h(s: Long) = s.toInt()

        /**
         * Les quatre heures en couleurs vives, comme peintes à la gouache
         * (direction validée par le propriétaire le 2026-11-01, palettes de la
         * maquette `vives.js` de la skill `direction-artistique`) : le matin,
         * un ciel bleu franc qui passe au rose puis au jaune pâle vers
         * l'horizon, des verts francs et une eau soutenue ; l'après-midi
         * orangé ; le soir violet, rose et orange ; la nuit bleu profond, où la
         * vivacité vient des lumières dorées.
         */
        private val BASE = arrayOf(
            Heure(0, h(0xFF7EC3F2), h(0xFFF5C3DA), h(0xFFFFE9A6), h(0xFFFFE066), 22f, 15f, 6f,
                h(0xFF6F9BEA), h(0xFF5DBB63), h(0xFF2F8F5B), h(0xFF2A6B45),
                h(0xFFF4CF94), h(0xFFE0674A), h(0xFF4B6A8A), false, h(0xFF3E9DEB), false),
            Heure(1, h(0xFFFFA94D), h(0xFFFFD27A), h(0xFFFFF0B8), h(0xFFFFF39A), 78f, 13f, 7f,
                h(0xFFE38B5C), h(0xFF9CC43F), h(0xFF4E8A2E), h(0xFF3B5E22),
                h(0xFFFFD9A0), h(0xFFD9503A), h(0xFF6A4A2E), false, h(0xFF2FA8DE), false),
            Heure(2, h(0xFF4B3BA8), h(0xFFFF6FA8), h(0xFFFFB25C), h(0xFFFFD36A), 68f, 31f, 10f,
                h(0xFF9B5FB8), h(0xFF6B3F8F), h(0xFF3E2766), h(0xFF2A1A48),
                h(0xFFF0A890), h(0xFF8A2F5A), h(0xFFFFCD48), true, h(0xFFE8789A), false),
            Heure(3, h(0xFF0B1450), null, h(0xFF2D4BB0), h(0xFFFFF4C8), 79f, 12f, 4.6f,
                h(0xFF2F4DA0), h(0xFF22357F), h(0xFF16245A), h(0xFF0C1638),
                h(0xFF6A79B8), h(0xFF3A3F7A), h(0xFFFFD45A), true, h(0xFF2D4FA8), true)
        )

        /**
         * La palette d'une rareté, le ciel légèrement teinté par le champ.
         *
         * Légèrement : 14 % au zénith, 10 % à l'horizon. Assez pour qu'un
         * tiroir garde sa couleur, pas assez pour qu'un matin vire au violet.
         */
        fun de(rarete: Rarete, teinte: Float?): Heure {
            val b = BASE[rarete.ordinal]
            if (teinte == null) return b
            val t = hsl(teinte, 0.55f, 0.62f)
            return Heure(
                b.rang, melange(b.cielHaut, t, 0.14f), b.cielMilieu?.let { melange(it, t, 0.12f) },
                melange(b.cielBas, t, 0.10f), b.astre, b.astreX, b.astreY, b.astreR,
                b.loin, b.milieu, b.pres, b.sol, b.pierre, b.toit, b.fenetre, b.allume, b.eau, b.nuit
            )
        }

        /** Mélange linéaire de deux couleurs opaques, composante par composante. */
        fun melange(a: Int, b: Int, t: Float): Int {
            fun canal(decalage: Int): Int {
                val x = (a shr decalage) and 0xFF
                val y = (b shr decalage) and 0xFF
                return (x + (y - x) * t).roundToInt().coerceIn(0, 255)
            }
            return (0xFF shl 24) or (canal(16) shl 16) or (canal(8) shl 8) or canal(0)
        }

        /** HSL vers ARGB opaque, comme le `hsl()` de CSS. */
        fun hsl(teinte: Float, s: Float, l: Float): Int {
            val a = s * min(l, 1f - l)
            fun f(n: Int): Int {
                val k = (n + teinte / 30f) % 12f
                val v = l - a * max(-1f, min(k - 3f, min(9f - k, 1f)))
                return (v * 255f).roundToInt().coerceIn(0, 255)
            }
            return (0xFF shl 24) or (f(0) shl 16) or (f(8) shl 8) or f(4)
        }
    }
}

/**
 * Les dix lieux, dessinés par le code dans une boîte de 100 × 66 unités.
 *
 * Aucun fichier image : chaque lieu est une suite de plans (crête lointaine,
 * colline, bâti, eau, premier plan) et la même suite se repeint aux quatre
 * heures en changeant de palette. Le hasard des fenêtres et des bosquets est
 * **graine fixe**, si bien qu'un lieu est le même sur toutes les cartes.
 *
 * Le rendu est mis en cache par lieu, heure et taille : comme le métal, il ne
 * dépend pas du mot, et trente vignettes d'un même tiroir partagent un bitmap.
 */
object Paysages {

    const val LARGEUR = 100f
    const val HAUTEUR = 66f

    /**
     * Un huitième du tas : un tiroir du carnet montre jusqu'à trente-six
     * paysages différents (neuf lieux, quatre heures) et chacun pèse un demi
     * mégaoctet en vignette. Un cache plus petit que ce qu'une seule page
     * affiche ne cache plus rien, il redessine tout à chaque trame.
     */
    private val CACHE = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceIn(8L * 1024 * 1024, 48L * 1024 * 1024).toInt()
    ) {
        override fun sizeOf(cle: String, valeur: Bitmap): Int = valeur.byteCount
    }

    /**
     * Où tombe la boîte 100 × 66 dans une ouverture de [l] × [h] : elle la
     * couvre et se rogne sur les côtés, comme `xMidYMid slice` en SVG.
     * Rend l'échelle, puis le décalage x et y.
     */
    fun cadrage(l: Float, h: Float): FloatArray {
        val e = max(l / LARGEUR, h / HAUTEUR)
        return floatArrayOf(e, (l - LARGEUR * e) / 2f, (h - HAUTEUR * e) / 2f)
    }

    /**
     * Le lieu à l'heure dite, rendu à [largeurPx] × [hauteurPx], habillé par
     * la saison de [souvenir] s'il y en a un (voir [Habillage]).
     *
     * Le souvenir entre dans la clé : neuf lieux, quatre heures, quatre
     * saisons et cinq fêtes, mais un tiroir n'en montre qu'une poignée à la
     * fois, et deux cartes gagnées le même hiver partagent toujours leur rendu.
     */
    fun image(lieu: Lieu, heure: Heure, teinte: Float?, largeurPx: Int, hauteurPx: Int,
              souvenir: Souvenir? = null, facture: Facture = Facture.VIGNETTE): Bitmap? {
        if (largeurPx <= 0 || hauteurPx <= 0) return null
        val cle = cle(lieu, heure, teinte, largeurPx, hauteurPx, souvenir, facture)
        CACHE.get(cle)?.let { if (!it.isRecycled) return it }
        val bitmap = rendu(lieu, heure, largeurPx, hauteurPx, souvenir, facture) ?: return null
        CACHE.put(cle, bitmap)
        return bitmap
    }

    private fun cle(lieu: Lieu, heure: Heure, teinte: Float?, l: Int, h: Int, souvenir: Souvenir?,
                    facture: Facture = Facture.VIGNETTE) =
        "${lieu.ordinal}_${heure.rang}_${teinte}_${souvenir?.saison}_${souvenir?.fete}_${facture.ordinal}_${l}x$h"

    /** Le rendu s'il est déjà en cache, sans rien calculer. */
    fun enCache(lieu: Lieu, heure: Heure, teinte: Float?, largeurPx: Int, hauteurPx: Int,
                souvenir: Souvenir? = null, facture: Facture = Facture.VIGNETTE): Bitmap? =
        CACHE.get(cle(lieu, heure, teinte, largeurPx, hauteurPx, souvenir, facture))?.takeIf { !it.isRecycled }

    /**
     * Rend le lieu hors du fil principal, puis appelle [pret] sur le fil
     * principal. Réservé aux vignettes.
     *
     * Sur le Galaxy A21s (2026-10-12), un paysage de vignette coûte environ
     * 100 ms, surtout la passe de saison de [Habillage]. La grille du
     * téléphone est un `ScrollView` qui ne recycle rien, et Android enregistre
     * le dessin de **toutes** ses vignettes dès l'ouverture, même hors de
     * l'écran : un carnet de 36 cartes figeait l'écran 4 à 5 secondes, et le
     * gel grandit avec le carnet. La vignette montre son ciel le temps que le
     * lieu arrive. La carte ouverte reste synchrone : un seul rendu, et une
     * carte qu'on vient de toucher ne doit pas apparaître vide.
     *
     * Deux vignettes qui demandent le même rendu ne le paient qu'une fois.
     */
    fun demander(lieu: Lieu, heure: Heure, teinte: Float?, largeurPx: Int, hauteurPx: Int,
                 souvenir: Souvenir?, pret: (Bitmap) -> Unit) {
        if (largeurPx <= 0 || hauteurPx <= 0) return
        val cle = cle(lieu, heure, teinte, largeurPx, hauteurPx, souvenir)
        synchronized(EN_COURS) {
            val attente = EN_COURS[cle]
            if (attente != null) {
                attente += pret
                return
            }
            EN_COURS[cle] = mutableListOf(pret)
        }
        FILS.execute {
            val bitmap = try {
                rendu(lieu, heure, largeurPx, hauteurPx, souvenir)
            } catch (e: OutOfMemoryError) {
                null
            }
            // En cache dès qu'il est rendu, sans attendre le fil principal :
            // une vignette qui se dessine entre-temps le trouve déjà.
            if (bitmap != null) CACHE.put(cle, bitmap)
            PRINCIPAL.post {
                val qui = synchronized(EN_COURS) { EN_COURS.remove(cle) }.orEmpty()
                if (bitmap != null) qui.forEach { it(bitmap) }
            }
        }
    }

    /**
     * Les demandes en vol. Le carnet en dépose aussi depuis son fil de
     * chargement (voir `CarnetFragment.chargerEnFond`) : d'où le verrou. Les
     * rappels, eux, passent toujours sur le fil principal.
     */
    private val EN_COURS = HashMap<String, MutableList<(Bitmap) -> Unit>>()
    private val PRINCIPAL by lazy { Handler(Looper.getMainLooper()) }

    /**
     * Trois fils de fond : le rendu n'a aucun état partagé, et le A21s a huit
     * cœurs. Le premier écran de la grille, huit vignettes, se peint ainsi en
     * trois tours pendant que le carnet se charge.
     */
    private val FILS by lazy {
        Executors.newFixedThreadPool(3) { tache ->
            Thread({
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                tache.run()
            }, "paysages").apply { isDaemon = true }
        }
    }

    /**
     * Fait tourner une fois, en petit et pour rien, le rendu de chaque
     * saison. Fait pour un fil de fond, pendant que le carnet se charge.
     *
     * Le premier passage dans la passe de saison de [Habillage] n'est pas
     * encore compilé : sur un Galaxy A21s (2026-10-12), les trois premiers
     * paysages de vignette y passaient 720 à 760 ms chacun, les suivants 80
     * à 100 ms. Ces trois-là étaient justement ceux du haut de la grille.
     */
    fun prechauffer() {
        val heure = Heure.de(Rarete.COMMUN, null)
        for (saison in Saison.values()) {
            try {
                rendu(Lieu.MELLERDALL, heure, 136, 102, Souvenir(saison, null))?.recycle()
            } catch (e: OutOfMemoryError) {
                return
            }
        }
    }

    /** Un rendu hors cache, que l'appelant garde pour lui. */
    fun rendu(lieu: Lieu, heure: Heure, largeurPx: Int, hauteurPx: Int, souvenir: Souvenir? = null,
              facture: Facture = Facture.VIGNETTE): Bitmap? {
        if (largeurPx <= 0 || hauteurPx <= 0) return null
        val bitmap = Bitmap.createBitmap(largeurPx, hauteurPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val k = cadrage(largeurPx.toFloat(), hauteurPx.toFloat())
        c.save()
        c.translate(k[1], k[2])
        c.scale(k[0], k[0])
        if (souvenir == null) {
            dessiner(c, lieu, heure, facture = facture, echelle = k[0])
            c.restore()
            return bitmap
        }
        // Avec une saison, le lieu est peint à part sur un calque transparent,
        // puis posé sur le ciel : ce calque est aussi le masque dont
        // [Habillage] a besoin, et le lieu n'est peint qu'une fois.
        dessiner(c, lieu, heure, ciel = true, lieuAussi = false, facture = facture, echelle = k[0])
        c.restore()
        val calque = Bitmap.createBitmap(largeurPx, hauteurPx, Bitmap.Config.ARGB_8888)
        val cc = Canvas(calque)
        cc.translate(k[1], k[2])
        cc.scale(k[0], k[0])
        dessiner(cc, lieu, heure, ciel = false, facture = facture, echelle = k[0])
        c.drawBitmap(calque, 0f, 0f, null)
        Habillage.appliquer(bitmap, calque, lieu, heure, souvenir, k)
        calque.recycle()
        return bitmap
    }

    /**
     * De combien chaque plan glisse au plus quand la carte penche à fond, en
     * part de la largeur (de la hauteur pour le tangage) : le ciel, les
     * nuages, le lointain, le milieu, le proche, puis les coulisses.
     *
     * C'est le fond qui bouge le plus, comme derrière une vitre : le cadre de
     * la carte est ce qu'il y a de plus près, le dessin du mot est posé
     * devant et ne bouge pas, et plus un plan est loin du verre, plus il
     * glisse quand l'œil change d'angle. Les valeurs sont celles de la
     * maquette `cinq_plans.html` (skill `direction-artistique`, règle 8).
     *
     * Le sixième plan n'existe que sur les rares et les très rares : les
     * coulisses, juste derrière la vitre (voir [nombreDePlans]).
     */
    val GLISSEMENT = floatArrayOf(0.05f, 0.04f, 0.03f, 0.017f, 0.007f, 0.003f)

    /** Le rang des nuages parmi les plans : une bande qui défile, voir [nuages]. */
    const val NUAGES = 1

    /**
     * Cinq cartons pour toutes les cartes, six à partir de rare (décision du
     * propriétaire, 2026-11-01, règle 8 de la skill `direction-artistique`) :
     * une commune, la carte qu'on manipule le plus, a le plein effet de
     * théâtre. Les rares et les très rares gardent en plus leurs coulisses,
     * la seule différence de profondeur qui reste entre les paliers ; la
     * rareté se dit par l'heure, le métal et la plaque, qu'on voit sans
     * pencher la carte.
     */
    fun nombreDePlans(heure: Heure): Int = if (heure.rang >= Rarete.RARE.ordinal) 6 else 5

    /**
     * Le lieu en cinq calques, six pour une rare ([nombreDePlans]), pour la
     * carte ouverte : voir [GLISSEMENT]. Le premier est le ciel et ne laisse
     * rien de vide ; le deuxième est la bande des nuages ([NUAGES]), que
     * [CadreOrne] fait traverser le ciel ; les autres sont transparents autour
     * de ce qu'ils portent.
     *
     * Chaque calque déborde de la fenêtre de quoi glisser sans découvrir son
     * bord : voir [marge], et [CadreOrne] qui le peint centré sur elle.
     *
     * Les calques sont des **cartons de théâtre de papier** (skill
     * `direction-artistique`) : chaque pièce est un carton peint à la gouache
     * ([Facture.GOUACHE]), chaque calque porte une ombre nette sur ceux de
     * derrière et un liseré de lumière au bord haut ([trancher]), et le
     * lointain s'efface par la couleur ([ESTOMPE]), sans flou. Tout cela est
     * peint ici, une fois : pencher la carte ne coûte rien de plus.
     *
     * `null` pour une carte gagnée un jour de fête, qui garde son rendu à
     * plat, sans nuages : les feux, les braises et les lumières s'ajoutent à
     * la lumière du paysage entier, et ne se répartissent pas en calques.
     */
    fun plans(lieu: Lieu, heure: Heure, teinte: Float?, largeurPx: Int, hauteurPx: Int,
              souvenir: Souvenir? = null): Array<Bitmap>? {
        if (largeurPx <= 0 || hauteurPx <= 0 || souvenir?.fete != null) return null
        val cle = cle(lieu, heure, teinte, largeurPx, hauteurPx, souvenir)
        val caches = Array(nombreDePlans(heure)) { CACHE.get("${cle}_p$it")?.takeIf { b -> !b.isRecycled } }
        if (caches.all { it != null }) return caches.requireNoNulls()
        val rendus = rendrePlans(lieu, heure, largeurPx, hauteurPx, souvenir)
        rendus.forEachIndexed { i, b -> CACHE.put("${cle}_p$i", b) }
        return rendus
    }

    /**
     * Ce que le calque [i] déborde de chaque côté d'une fenêtre de [n] pixels :
     * sa plus grande course, plus un pixel de garde.
     *
     * Jusqu'en 37.4.1 les calques avaient la taille exacte de la fenêtre, le
     * dessin seul était agrandi dedans : en glissant, le bord de l'image
     * découvrait une bande du fond de la fenêtre (vu sur un Galaxy A21s).
     */
    fun marge(i: Int, n: Int): Int = ceil(GLISSEMENT[i] * n).toInt() + 1

    /**
     * La profondeur de chaque calque, en plans, pour l'ombre qu'il reçoit :
     * plus un carton est loin de celui qui la porte, plus elle monte et
     * s'étale. Les nuages n'en reçoivent pas (-1).
     */
    private val PROFONDEUR = intArrayOf(0, -1, 1, 2, 3, 4)

    private fun rendrePlans(lieu: Lieu, heure: Heure, l: Int, h: Int, souvenir: Souvenir?): Array<Bitmap> {
        val n = nombreDePlans(heure)
        val tailles = Array(n) { i -> intArrayOf(l + 2 * marge(i, l), h + 2 * marge(i, h)) }
        val ks = Array(n) { i ->
            cadrage(tailles[i][0].toFloat(), tailles[i][1].toFloat())
        }
        fun toile(i: Int, dessin: (Canvas) -> Unit): Bitmap {
            val b = Bitmap.createBitmap(tailles[i][0], tailles[i][1], Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            c.translate(ks[i][1], ks[i][2])
            c.scale(ks[i][0], ks[i][0])
            dessin(c)
            return b
        }
        // Le calque [i] porte le plan [i] − 2 du lieu : 0 le lointain, 1 le milieu, 2 le proche.
        fun calque(i: Int, sol: Sol = Sol.SANS) = toile(i) {
            dessiner(it, lieu, heure, ciel = false, lieuAussi = true, seul = i - 2, sol = sol,
                facture = Facture.GOUACHE, echelle = ks[i][0])
        }
        fun peindre(i: Int, dessin: Peintre.() -> Unit) = toile(i) {
            Peintre(it, heure, facture = Facture.GOUACHE, echelle = ks[i][0]).dessin()
        }
        val ciel = toile(0) { dessiner(it, lieu, heure, ciel = true, lieuAussi = false, facture = Facture.GOUACHE, echelle = ks[0][0]) }
        val loin = calque(2)
        val milieu = calque(3)
        val proche = calque(4)
        // Les silhouettes avant la saison : la neige ou les fleurs que
        // [Habillage] pose par-dessus ne sont pas du carton.
        val formes = arrayOf(loin.extractAlpha(), milieu.extractAlpha(), proche.extractAlpha())
        val unite = ks[0][0]
        if (souvenir != null) {
            // Le ciel aussi, sans masque : l'hiver le pâlit, l'été le réchauffe.
            Habillage.appliquerPlans(arrayOf(ciel, loin, milieu, proche), arrayOf(null, loin, milieu, proche),
                lieu, heure, souvenir, arrayOf(ks[0], ks[2], ks[3], ks[4]))
        }
        // Après la saison, qui reconnaît la végétation à ses couleurs d'origine.
        eclaircir(loin, formes[0], heure.cielBas, ESTOMPE[0])
        eclaircir(milieu, formes[1], heure.cielBas, ESTOMPE[1])
        // Les coulisses des rares, posées après la saison : ce sont des
        // châssis de théâtre, pas des arbres du lieu. Au Breedewee, des
        // piliers et des pignons : des arbres n'auraient rien à faire dans la
        // ruelle. Deux rangées, étagées : la seconde, un pas en retrait, est
        // collée au carton du proche, dont elle partage la course.
        val coulisses = if (n < 6) null else {
            val fond2 = peindre(4) { if (lieu == Lieu.BREEDEWEE) pignons() else coulissesDuFond() }
            Canvas(proche).drawBitmap(fond2, 0f, 0f, null)
            val forme2 = Bitmap.createBitmap(tailles[4][0], tailles[4][1], Bitmap.Config.ALPHA_8)
            Canvas(forme2).apply { drawBitmap(formes[2], 0f, 0f, null); drawBitmap(fond2, 0f, 0f, null) }
            formes[2].recycle()
            formes[2] = forme2
            fond2.recycle()
            peindre(5) { if (lieu == Lieu.BREEDEWEE) piliers() else coulisses() }
        }
        val plans = listOfNotNull(ciel, nuages(heure, l, h), loin, milieu, proche, coulisses).toTypedArray()
        // Le plancher, là où un lieu en a un : il reçoit les ombres sans en
        // porter, et sans bordure, ce n'est pas un carton.
        val planchers = Array(plans.size) { i ->
            if (i in 3..4 && aUnPlancher(lieu, i - 2)) calque(i, sol = Sol.SEUL) else null
        }
        val silhouettes = arrayOfNulls<Bitmap>(plans.size)
        silhouettes[2] = formes[0]
        silhouettes[3] = formes[1]
        silhouettes[4] = formes[2]
        if (coulisses != null) silhouettes[5] = coulisses.extractAlpha()
        // Chaque carton porte son ombre sur tous ceux de derrière : là où un
        // carton intermédiaire la cache, c'est en penchant qu'on la découvre,
        // comme dans une vraie boîte. Les nuages n'en reçoivent pas : ils
        // voyagent, et portent la leur sur leur propre carton ([nuages]).
        for (devant in 2 until plans.size) {
            val forme = silhouettes[devant] ?: continue
            for (derriere in 0 until devant) {
                if (derriere == NUAGES) continue
                val force = when {
                    derriere == 0 && devant == 2 -> OMBRE_SUR_LE_CIEL
                    derriere == 0 || derriere == 2 -> OMBRE_SUR_LE_FOND
                    else -> 1f
                }
                ombrer(
                    plans[derriere], forme,
                    marge(derriere, l) - marge(devant, l), marge(derriere, h) - marge(devant, h),
                    unite, heure, PROFONDEUR[devant] - PROFONDEUR[derriere], force
                )
            }
            trancher(plans[devant], forme, unite, heure, force = if (devant == 2) 0.6f else 1f)
            planchers[devant]?.let {
                val derriere = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER) }
                Canvas(plans[devant]).drawBitmap(it, 0f, 0f, derriere)
                it.recycle()
            }
        }
        silhouettes.forEach { it?.recycle() }
        return plans
    }

    /**
     * Les nuages de carton, le seul plan qui bouge seul (règle 7 de la skill
     * `direction-artistique`) : une bande de toute la largeur de la boîte, de
     * [BANDE_NUAGES] unités de haut, qui se raccorde à elle-même. [CadreOrne]
     * la fait traverser le ciel de gauche à droite, et glisser avec la carte
     * quand on la penche.
     *
     * Chaque nuage porte son ombre sur son propre carton, sous lui, pour
     * qu'elle voyage avec lui ; aucun autre carton ne lui en fait. Les mêmes
     * nuages pour tous les lieux : ils passent, leur place ne dit rien.
     */
    private fun nuages(heure: Heure, l: Int, h: Int): Bitmap {
        val k = cadrage(l.toFloat(), h.toFloat())[0]
        val b = Bitmap.createBitmap(ceil(LARGEUR * k).toInt(), ceil(BANDE_NUAGES * k).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.scale(k, k)
        val p = Peintre(c, heure, facture = Facture.GOUACHE, echelle = k)
        var i = 0
        while (i < NUEES.size) {
            val x = NUEES[i]; val y = NUEES[i + 1]; val longueur = NUEES[i + 2]
            // Un nuage qui déborde à droite repart à gauche : la bande se répète.
            for (decalage in floatArrayOf(0f, -LARGEUR)) {
                if (x + decalage + longueur > -2f && x + decalage < LARGEUR + 2f) p.nuage(x + decalage, y, longueur)
            }
            i += 3
        }
        val forme = b.extractAlpha()
        trancher(b, forme, k, heure, force = 0.6f)
        forme.recycle()
        return b
    }

    /** La hauteur de la bande des nuages, en unités du paysage : le haut du ciel. */
    const val BANDE_NUAGES = 16f

    /**
     * Les nuages : x, y, longueur, en unités du paysage (maquette
     * `cinq_plans.html`).
     */
    private val NUEES = floatArrayOf(57f, 5.5f, 15f, 80f, 11f, 10f, 5f, 8f, 11f)

    /**
     * La part de chemin vers la couleur de l'horizon, par plan : le lointain
     * s'efface, le milieu à peine, le proche garde ses couleurs.
     *
     * C'est la perspective atmosphérique, qui remplace le flou d'avant (la
     * profondeur de champ d'un appareil photo, jusqu'en 37.8.0) : au théâtre
     * de papier tout est net, la profondeur vient de l'empilement des cartons
     * et de leurs ombres, et le lointain se reconnaît à sa couleur.
     */
    private val ESTOMPE = floatArrayOf(0.30f, 0.10f, 0f)

    /**
     * Ce qui tombe sur le premier calque est plus léger : la toile de fond
     * est loin derrière les cartons, et une ombre franche sur un ciel se lit
     * comme une fumée. Moins encore pour la crête, posée sur le ciel même.
     */
    private const val OMBRE_SUR_LE_FOND = 0.6f
    private const val OMBRE_SUR_LE_CIEL = 0.35f

    /**
     * L'ombre d'un carton sur un carton de derrière, en unités de la boîte :
     * où elle tombe par plan d'écart (vers le haut, vers la droite), puis son
     * flou.
     *
     * La lumière est celle de la **rampe**, au bas de la scène et un peu à
     * gauche : elle porte les ombres vers le haut, au-dessus des crêtes, là
     * où on les voit, et de côté, le long des bords droits des cartons. Une
     * lumière d'en haut les cacherait derrière le carton même qui les porte.
     * La même rampe éclaire toutes les cartes.
     *
     * Nette et marquée (2026-10-30) : l'ombre courte et douce d'avant ne
     * séparait pas les cartons, la fenêtre se lisait comme une seule image à
     * liseré blanc. C'est le vide qu'on voit derrière chaque découpe qui fait
     * le théâtre de papier.
     */
    private const val OMBRE_MONTEE = 1f
    private const val OMBRE_COTE = 0.55f
    private const val OMBRE_FLOU = 0.22f

    /**
     * Mélange [b] vers [vers] de la part [t], là où la silhouette [forme] (de
     * même taille) est pleine, sans toucher la transparence de [b].
     */
    private fun eclaircir(b: Bitmap, forme: Bitmap, vers: Int, t: Float) {
        if (t <= 0f) return
        val w = b.width
        val h = b.height
        val px = IntArray(w * h)
        val mk = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        forme.getPixels(mk, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val a = mk[i] ushr 24
            if (a == 0) continue
            val c = px[i]
            px[i] = (c and 0xFF000000.toInt()) or (Heure.melange(c, vers, t * a / 255f) and 0x00FFFFFF)
        }
        b.setPixels(px, 0, w, 0, 0, w, h)
    }

    /**
     * Peint sur [derriere] l'ombre de la silhouette [forme], posée à
     * ([dx], [dy]) pixels de son origine, [ecart] plans plus loin : plus le
     * carton de derrière est loin, plus l'ombre monte et s'étale. Elle ne
     * tombe que sur ce que [derriere] porte.
     */
    private fun ombrer(derriere: Bitmap, forme: Bitmap, dx: Int, dy: Int, unite: Float, heure: Heure,
                       ecart: Int, force: Float) {
        val flou = Paint().apply {
            maskFilter = BlurMaskFilter(max(1f, OMBRE_FLOU * unite * ecart), BlurMaskFilter.Blur.NORMAL)
        }
        val ou = IntArray(2)
        val ombre = forme.extractAlpha(flou, ou)
        val p = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            color = OMBRE
            alpha = ((if (heure.nuit) 0.7f else 0.5f) * force * 255f).roundToInt()
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
        Canvas(derriere).drawBitmap(
            ombre, dx + ou[0] + OMBRE_COTE * unite * ecart, dy + ou[1] - OMBRE_MONTEE * unite * ecart, p
        )
        ombre.recycle()
    }

    /** Une ombre de carton, pas de suie : un bleu nuit presque noir. */
    private const val OMBRE = 0xFF0B1020.toInt()

    /**
     * La gouache est un peu mince : 84 % d'opacité, le kraft transparaît
     * (maquette `peint.js`).
     */
    private const val GOUACHE_ALPHA = 214

    /** Le sens des touches courtes et obliques de la pierre, en radians. */
    private const val OBLIQUE = 1f

    /**
     * L'ombre d'une pièce sur ce qui est derrière elle dans le même carton,
     * en unités : elle monte (la rampe), et son flou, en écart type.
     */
    private const val OMBRE_PIECE_MONTEE = 0.55f
    private const val OMBRE_PIECE_FLOU = 0.55f

    /**
     * La tranche du carton, vue d'en haut : un liseré de lumière le long du
     * bord haut de chaque découpe, que la scène éclaire. Posé derrière ce que
     * le calque porte, il ne dépasse qu'en haut.
     *
     * Jusqu'au chantier gouache (2026-11-02), une bordure blanche faisait le
     * tour de chaque découpe, l'âme du papier. Le carton peint montre le sien
     * autrement : sa couleur kraft reparaît au bord de chaque pièce, là où le
     * pinceau ne l'a pas couverte (voir [Peintre.peint]). Repris de la
     * maquette `cinq_plans.html` de la skill `direction-artistique`, validée
     * par le propriétaire le 2026-11-01.
     */
    private fun trancher(b: Bitmap, forme: Bitmap, unite: Float, heure: Heure, force: Float = 1f) {
        val p = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            color = Heure.melange(heure.cielBas, 0xFFFFFFFF.toInt(), if (heure.nuit) 0.3f else 0.6f)
            alpha = ((if (heure.nuit) 0.45f else 0.7f) * force * 255f).roundToInt()
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
        }
        Canvas(b).drawBitmap(forme, 0f, -max(1f, 0.3f * unite), p)
    }

    /**
     * Ce qu'on peint d'un lieu qui a un plancher : tout (la vignette, la
     * carte à plat), tout sauf lui (les cartons), ou lui seul.
     */
    enum class Sol { AVEC, SANS, SEUL }

    /**
     * Les plans qui ont une bande de [Peintre.plancher] : celui du proche
     * partout, qui se pose sur le devant de la scène, et celui du milieu là
     * où il s'arrête lui aussi au-dessus du bandeau (la ruelle du Breedewee,
     * les rives de la Sûre et de la Moselle).
     */
    private fun aUnPlancher(lieu: Lieu, plan: Int) =
        plan == 2 || (plan == 1 && (lieu == Lieu.BREEDEWEE || lieu == Lieu.ESCH_SAUER || lieu == Lieu.MUSEL))

    fun dessiner(c: Canvas, lieu: Lieu, heure: Heure, ciel: Boolean = true, lieuAussi: Boolean = true,
                 seul: Int = -1, sol: Sol = Sol.AVEC, facture: Facture = Facture.VIGNETTE, echelle: Float = 1f) {
        val p = Peintre(c, heure, seul, sol, facture, echelle)
        if (ciel) { if (facture == Facture.GOUACHE) p.cielPeint() else p.ciel() }
        if (!lieuAussi) return
        when (lieu) {
            Lieu.VEIANEN -> p.veianen()
            Lieu.ROUT_BRECK -> p.routBreck()
            Lieu.ESCH_SAUER -> p.eschSauer()
            Lieu.MUSEL -> p.musel()
            Lieu.NEIMENSTER -> p.neimenster()
            Lieu.GELLE_FRA -> p.gelleFra()
            Lieu.FOUER -> p.fouer()
            Lieu.MELLERDALL -> p.mellerdall()
            Lieu.STAD -> p.stad()
            Lieu.BREEDEWEE -> p.breedewee()
        }
    }

    /**
     * Le hasard à graine fixe (mulberry32) : le même que celui de la maquette,
     * pour que l'appli dessine exactement les lieux qui ont été choisis.
     */
    internal class Alea(graine: Int) {
        private var s = graine
        operator fun invoke(): Float {
            s += 0x6D2B79F5
            var t = s
            t = (t xor (t ushr 15)) * (1 or t)
            t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
            return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f
        }
    }

    /**
     * Un chemin écrit comme en SVG, en coordonnées absolues : M, L, H, V, C, Q, Z.
     *
     * Les lieux ont été mis au point en SVG ; recopier leurs contours tels
     * quels évite de les retraduire à la main, ce qui est l'endroit où une
     * colline se retrouve à l'envers.
     */
    internal fun chemin(d: String): Path {
        val path = Path()
        val jetons = Regex("[MLHVCQZmlhvcqz]|-?\\d*\\.?\\d+(?:[eE]-?\\d+)?").findAll(d).map { it.value }.toList()
        var i = 0
        var cmd = 'M'
        var x = 0f
        var y = 0f
        fun n(): Float = jetons[i++].toFloat()
        while (i < jetons.size) {
            val j = jetons[i]
            if (j[0].isLetter()) { cmd = j[0]; i++; if (cmd == 'Z' || cmd == 'z') { path.close(); continue } }
            when (cmd) {
                'M' -> { x = n(); y = n(); path.moveTo(x, y); cmd = 'L' }
                'L' -> { x = n(); y = n(); path.lineTo(x, y) }
                'H' -> { x = n(); path.lineTo(x, y) }
                'V' -> { y = n(); path.lineTo(x, y) }
                'C' -> {
                    val a = n(); val b = n(); val cc = n(); val dd = n(); x = n(); y = n()
                    path.cubicTo(a, b, cc, dd, x, y)
                }
                'Q' -> { val a = n(); val b = n(); x = n(); y = n(); path.quadTo(a, b, x, y) }
                else -> i++
            }
        }
        return path
    }

    /**
     * Le feuillage d'une coulisse, côté gauche, en unités du paysage : x, y,
     * rayon. Il passe sous l'arche du cadre des rares, qui mange les coins
     * hauts, et ne descend pas sous la moitié de la fenêtre ; la droite en
     * est le miroir.
     */
    private val FEUILLES = floatArrayOf(
        0f, 12f, 7.5f, 6f, 9.5f, 6f, 11f, 13f, 4.6f, 14.5f, 17.5f, 3.4f,
        2f, 19.5f, 6.5f, 8f, 21f, 4.6f, 1f, 27f, 5.5f, 5.5f, 29.5f, 3.6f, 11.5f, 25f, 2.8f
    )

    /**
     * La seconde rangée de coulisses ([Peintre.coulissesDuFond]), côté
     * gauche, comme [FEUILLES] : un arbre plus petit, un pas en retrait, qui
     * dépasse sous et à côté du feuillage de devant.
     */
    /**
     * La rainure du devant de la scène, où le carton du proche de chaque
     * lieu se pose sur le plancher. Le bandeau du nom cache le bas de la
     * fenêtre ouverte, sous 62 unités environ : plus bas, le plancher ne se
     * verrait que dans la vignette.
     */
    private const val DEVANT = 58.5f

    private val FEUILLES_DU_FOND = floatArrayOf(
        13f, 27f, 4.6f, 18.5f, 29.5f, 4f, 15.5f, 33.5f, 4.4f, 20.5f, 35f, 3f, 11.5f, 32f, 3.4f, 16.5f, 23.5f, 3.4f
    )

    /**
     * Comment les grandes pièces d'un lieu sont peintes.
     *
     * - [VIGNETTE] : des aplats, avec le bord de carton seul. En petit, des
     *   coups de pinceau feraient du bruit, et trente vignettes se peignent
     *   à l'ouverture du carnet.
     * - [GOUACHE] : le carton peint de la carte ouverte et de la carte
     *   imprimée, avec son ombre, ses coups de pinceau et son bord sec.
     */
    enum class Facture { VIGNETTE, GOUACHE }

    /**
     * Les coups de pinceau, préparés d'avance en tuiles qui se répètent sans
     * couture, puis posés dans le sens de chaque pièce par la matrice de leur
     * [BitmapShader].
     *
     * La maquette (`peint.js`) trace jusqu'à sept cents touches de trois poils
     * par pièce : des milliers de tracés par carte, trop pour un Galaxy A21s.
     * Une touche y est une version plus claire ou plus sombre de la couleur de
     * la pièce, posée en transparence ; c'est exactement un voile de blanc ou
     * de noir, si bien qu'une seule tuile sert à toutes les couleurs. Deux
     * tuiles : des touches longues (le ciel, l'eau, les collines, le
     * feuillage, tournées selon la pièce) et des touches courtes et obliques
     * (la pierre et le grès des pièces hautes).
     */
    private object Pinceaux {
        /** Le côté d'une tuile, en unités du paysage. */
        const val TUILE = 24f

        /** Les touches par unité carrée, comme la maquette sur une pièce moyenne. */
        private const val DENSITE = 0.5f

        private val tuiles = HashMap<String, Bitmap>()

        /**
         * La tuile à [echelle] pixels par unité, bornée : au-delà de dix, sur
         * la carte imprimée, elle est agrandie au rendu, et des touches de
         * plusieurs unités supportent très bien l'agrandissement.
         */
        @Synchronized
        fun tuile(courte: Boolean, echelle: Float): Bitmap {
            val q = (echelle.coerceIn(2f, 10f) * 2f).roundToInt() / 2f
            val cle = "${courte}_$q"
            tuiles[cle]?.takeIf { !it.isRecycled }?.let { return it }
            return dessiner(courte, q).also { tuiles[cle] = it }
        }

        private fun dessiner(courte: Boolean, q: Float): Bitmap {
            val n = ceil(TUILE * q).toInt()
            val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            c.scale(n / TUILE, n / TUILE)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
            val t = Path()
            val r = Alea(if (courte) 77 else 55)
            val decalages = FloatArray(3)
            repeat((TUILE * TUILE * DENSITE).roundToInt()) {
                val x = r() * TUILE
                val y = r() * TUILE
                val a = (r() - 0.5f) * (if (courte) 1.4f else 0.5f)
                val l = if (courte) 1.5f + r() * 3f else 2.5f + r() * 6f
                val w = 0.9f + r() * 1.3f
                // Plus claire ou plus sombre que la pièce, comme la maquette :
                // un voile de blanc ou de noir.
                val v = (r() - 0.5f) * 0.34f
                val blanc = v > 0f
                val force = if (blanc) v * 1.4f else -v
                val dx = cos(a) * l / 2f
                val dy = sin(a) * l / 2f
                val px = -sin(a)
                val py = cos(a)
                val portee = l / 2f + w
                for (poil in -1..1) {
                    p.color = if (blanc) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
                    p.alpha = ((0.4f + r() * 0.3f) * force * 255f).roundToInt()
                    p.strokeWidth = w * 0.45f
                    val ox = px * poil * w * 0.35f
                    val oy = py * poil * w * 0.35f
                    val jx = px * (r() - 0.5f) * 0.6f
                    val jy = py * (r() - 0.5f) * 0.6f
                    // La tuile se répète : une touche qui déborde est aussi
                    // peinte de l'autre côté.
                    val nx = repliements(x, portee, decalages)
                    val xs = decalages.copyOf(nx)
                    val ny = repliements(y, portee, decalages)
                    for (i in 0 until nx) for (k in 0 until ny) {
                        val ex = xs[i] + ox
                        val ey = decalages[k] + oy
                        t.rewind()
                        t.moveTo(x - dx + ex, y - dy + ey)
                        t.quadTo(x + ex + jx, y + ey + jy, x + dx + ex, y + dy + ey)
                        c.drawPath(t, p)
                    }
                }
            }
            return b
        }

        /** Les décalages où peindre une touche en [v] de [portee] pour que la tuile se raccorde. */
        private fun repliements(v: Float, portee: Float, sortie: FloatArray): Int {
            var n = 0
            sortie[n++] = 0f
            if (v - portee < 0f) sortie[n++] = TUILE
            if (v + portee > TUILE) sortie[n++] = -TUILE
            return n
        }
    }

    /** Tout l'outillage de tracé d'un lieu, sur un canevas déjà à l'échelle. */
    private class Peintre(
        val c: Canvas, val P: Heure, private val seul: Int = -1, private val sol: Sol = Sol.AVEC,
        private val facture: Facture = Facture.VIGNETTE,
        /** Les pixels par unité du canevas : la finesse des coups de pinceau. */
        private val echelle: Float = 1f
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val BAS = HAUTEUR

        /**
         * Le plan où l'on peint : 0 le lointain, 1 le milieu, 2 le proche.
         * Chaque lieu le fait avancer à mesure qu'il approche, jamais reculer.
         */
        private var plan = 0

        /**
         * Faux quand on ne rend qu'un plan et que ce n'est pas celui-ci : le
         * tracé est tu, mais tout le reste se déroule, si bien que les tirages
         * au sort restent les mêmes et qu'un plan seul tombe exactement sur
         * le lieu entier.
         */
        val actif: Boolean get() = (seul < 0 || plan == seul) && when (sol) {
            Sol.AVEC -> true
            Sol.SANS -> !dansLePlancher
            Sol.SEUL -> dansLePlancher
        }

        /** Vrai le temps de peindre le [plancher]. */
        private var dansLePlancher = false

        fun plan(n: Int) { plan = n }

        fun mix(a: Int, b: Int, t: Float) = Heure.melange(a, b, t)

        fun teinte(col: Int, opacite: Float) {
            p.shader = null
            p.color = col
            p.alpha = (opacite * 255f).roundToInt().coerceIn(0, 255)
        }

        fun rect(x: Float, y: Float, w: Float, h: Float, col: Int, o: Float = 1f) {
            if (!actif) return
            if (enCarton(w, h, o)) {
                val t = Path()
                t.moveTo(x, y); t.lineTo(x + w, y); t.lineTo(x + w, y + h); t.lineTo(x, y + h); t.close()
                boite.set(x, y, x + w, y + h)
                peint(t, col, sensDe(boite), boite)
                return
            }
            p.style = Paint.Style.FILL; teinte(col, o)
            c.drawRect(x, y, x + w, y + h, p)
        }

        fun rond(cx: Float, cy: Float, r: Float, col: Int, o: Float = 1f) {
            if (!actif) return
            p.style = Paint.Style.FILL; teinte(col, o)
            c.drawCircle(cx, cy, r, p)
        }

        fun forme(d: String, col: Int, o: Float = 1f) {
            if (!actif) return
            forme(chemin(d), col, o)
        }

        fun forme(path: Path, col: Int, o: Float = 1f) {
            if (!actif) return
            if (o >= 0.95f) {
                path.computeBounds(boite, true)
                if (enCarton(boite.width(), boite.height(), o)) {
                    peint(path, col, sensDe(boite), boite)
                    return
                }
            }
            p.style = Paint.Style.FILL; teinte(col, o)
            c.drawPath(path, p)
        }

        // ------------------------------------------------- le carton peint

        private val boite = RectF()

        /**
         * Vrai pour une pièce assez grande pour être un carton peint : les
         * petits détails (fenêtres, joints, lanternes), les bandes trop fines
         * pour un bord de carton (le liseré clair du pont) et ce qui est posé
         * en transparence (ombres, reflets, brume) restent des aplats nets.
         */
        private fun enCarton(w: Float, h: Float, o: Float) = o >= 0.95f && w * h >= 14f && min(w, h) >= 1.2f

        /** Le sens des coups de pinceau : horizontaux, ou courts et obliques sur une pièce haute (la pierre). */
        private fun sensDe(b: RectF) = if (b.height() > b.width() * 1.4f) OBLIQUE else 0f

        /** La couleur du carton, un brun kraft que la nuit assombrit. */
        private val kraft = mix(0xFFB08A5A.toInt(), P.pres, if (P.nuit) 0.55f else 0.18f)

        private val gouache by lazy { Paint(Paint.ANTI_ALIAS_FLAG) }
        private val ombreCarton by lazy { Paint(Paint.ANTI_ALIAS_FLAG) }
        private val bord by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE } }
        private val brosses = HashMap<Boolean, BitmapShader>()
        private val placeBrosse = Matrix()
        private val versPixels = Matrix()

        /**
         * Une pièce de carton peint, comme `peint` dans la maquette
         * (`peint.js` de la skill `direction-artistique`) :
         *
         * 1. le carton kraft, avec son ombre, que la rampe porte vers le haut ;
         * 2. la gouache, mate et un peu mince : le kraft transparaît ;
         * 3. les coups de pinceau dans le sens de la forme ([dir], en radians :
         *    0 horizontaux, [OBLIQUE] courts et obliques), pris dans
         *    [Pinceaux] ;
         * 4. le bord sec : le pinceau n'a pas couvert jusqu'à la découpe, le
         *    carton reparaît par endroits. C'est la tranche brune qui a
         *    remplacé la bordure blanche du papier.
         *
         * En [Facture.VIGNETTE], seuls le carton, l'aplat et le bord sec.
         *
         * [union] : une forme faite de plusieurs morceaux qui se chevauchent
         * (un feuillage de ronds) ; son bord de carton se pose à l'extérieur
         * seulement, faute de quoi chaque rond garderait le sien.
         */
        fun peint(chemin: Path, col: Int, dir: Float, b: RectF, ombre: Float = 1f, union: Boolean = false) {
            if (!actif) return
            val plein = facture == Facture.GOUACHE
            if (plein) ombrer(chemin, ombre)
            gouache.shader = null
            gouache.style = Paint.Style.FILL
            gouache.color = kraft
            c.drawPath(chemin, gouache)
            if (union) {
                bord.pathEffect = null
                bord.color = kraft
                bord.strokeWidth = 0.9f
                c.drawPath(chemin, bord)
            }
            c.save()
            c.clipPath(chemin)
            gouache.color = col
            gouache.alpha = GOUACHE_ALPHA
            c.drawPath(chemin, gouache)
            val r = Alea(((b.left * 131f + b.top * 71f + b.right * 17f + b.bottom) * 10f).roundToInt())
            if (plein) brosser(dir, b, r)
            if (!union) {
                bord.color = kraft
                bord.alpha = 217
                bord.strokeWidth = 0.9f
                bord.pathEffect = DashPathEffect(
                    floatArrayOf(0.6f + r() * 1.4f, 0.4f + r() * 1.2f, 1.5f + r(), 0.8f, 0.3f, 1.1f), 0f
                )
                c.drawPath(chemin, bord)
                bord.pathEffect = null
                bord.alpha = 89
                bord.strokeWidth = 1.3f
                c.drawPath(chemin, bord)
            }
            c.restore()
        }

        /**
         * L'ombre du carton sur ce qui est derrière lui dans le même calque,
         * tracée en pixels : le flou d'un `BlurMaskFilter` ne suit pas
         * toujours l'échelle du canevas.
         */
        @Suppress("DEPRECATION")
        private fun ombrer(chemin: Path, force: Float) {
            val m = c.matrix
            val enPixels = Path(chemin)
            enPixels.transform(m)
            enPixels.offset(0f, -OMBRE_PIECE_MONTEE * echelle)
            val sigma = OMBRE_PIECE_FLOU * echelle
            ombreCarton.maskFilter = BlurMaskFilter(max(0.5f, (sigma - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)
            ombreCarton.color = OMBRE
            ombreCarton.alpha = ((if (P.nuit) 0.5f else 0.32f) * force * 255f).roundToInt()
            c.save()
            c.setMatrix(versPixels)
            c.drawPath(enPixels, ombreCarton)
            c.restore()
        }

        /** Les coups de pinceau sur la pièce, déjà découpée par le canevas. */
        private fun brosser(dir: Float, b: RectF, r: Alea) {
            val courte = dir == OBLIQUE
            val tuile = Pinceaux.tuile(courte, echelle)
            val brosse = brosses.getOrPut(courte) {
                BitmapShader(tuile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            }
            val unite = Pinceaux.TUILE / tuile.width
            placeBrosse.setScale(unite, unite)
            placeBrosse.postTranslate(r() * Pinceaux.TUILE, r() * Pinceaux.TUILE)
            placeBrosse.postRotate(Math.toDegrees(dir.toDouble()).toFloat(), b.centerX(), b.centerY())
            brosse.setLocalMatrix(placeBrosse)
            gouache.color = 0xFF000000.toInt()
            gouache.shader = brosse
            gouache.isFilterBitmap = true
            c.drawRect(b.left - 2f, b.top - 2f, b.right + 2f, b.bottom + 2f, gouache)
            gouache.shader = null
        }

        /**
         * Le ciel peint, comme `cielPeint` dans la maquette : le dégradé, de
         * larges touches horizontales un peu plus claires ou plus sombres,
         * puis l'astre, peint après d'un seul aplat.
         */
        fun cielPeint() {
            ciel()
            val r = Alea(5)
            val t = Path()
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            for (i in 0 until 260) {
                val x = r() * 110f - 5f
                val y = r() * 46f
                val l = 6f + r() * 12f
                // La couleur de la touche suit un dégradé du haut vers le bas
                // sans le rose du milieu : c'est ce qui la fait voir.
                val base = mix(P.cielHaut, P.cielBas, if (y < 23f) y / 46f else y / 40f)
                val v = if (r() < 0.5f) mix(base, 0xFFFFFFFF.toInt(), 0.12f) else mix(base, 0xFF000000.toInt(), 0.05f)
                teinte(v, 0.3f + r() * 0.2f)
                p.strokeWidth = 1.4f + r() * 2f
                t.rewind()
                t.moveTo(x, y)
                t.quadTo(x + l / 2f, y + (r() - 0.5f) * 0.8f, x + l, y + (r() - 0.5f) * 0.6f)
                if (actif) c.drawPath(t, p)
            }
            p.style = Paint.Style.FILL
            p.strokeCap = Paint.Cap.BUTT
            if (!P.nuit) rond(P.astreX, P.astreY, P.astreR, P.astre, 0.95f)
        }


        fun trait(d: String, col: Int, largeur: Float, o: Float = 1f) {
            if (!actif) return
            p.style = Paint.Style.STROKE; p.strokeWidth = largeur; p.strokeCap = Paint.Cap.BUTT
            teinte(col, o)
            c.drawPath(chemin(d), p)
            p.style = Paint.Style.FILL
        }

        fun ligne(x1: Float, y1: Float, x2: Float, y2: Float, col: Int, largeur: Float, o: Float = 1f) {
            if (!actif) return
            p.style = Paint.Style.STROKE; p.strokeWidth = largeur; teinte(col, o)
            c.drawLine(x1, y1, x2, y2, p)
            p.style = Paint.Style.FILL
        }

        fun anneau(cx: Float, cy: Float, r: Float, col: Int, largeur: Float) {
            if (!actif) return
            p.style = Paint.Style.STROKE; p.strokeWidth = largeur; teinte(col, 1f)
            c.drawCircle(cx, cy, r, p)
            p.style = Paint.Style.FILL
        }

        fun triangle(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, col: Int, o: Float = 1f) {
            val t = Path(); t.moveTo(x1, y1); t.lineTo(x2, y2); t.lineTo(x3, y3); t.close()
            forme(t, col, o)
        }

        // ------------------------------------------------------ les plans

        fun ciel() {
            val couleurs = P.cielMilieu?.let { intArrayOf(P.cielHaut, it, P.cielBas) }
                ?: intArrayOf(P.cielHaut, P.cielBas)
            val positions = if (P.cielMilieu != null) floatArrayOf(0f, 0.58f, 1f) else null
            p.style = Paint.Style.FILL
            p.color = 0xFF000000.toInt()
            p.shader = LinearGradient(0f, 0f, 0f, BAS, couleurs, positions, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, LARGEUR, BAS, p)
            p.shader = null

            val transparent = P.astre and 0x00FFFFFF
            fun halo(r: Float, o: Float) {
                p.color = 0xFF000000.toInt()
                p.alpha = (o * 255f).roundToInt()
                p.shader = RadialGradient(
                    P.astreX, P.astreY, r,
                    intArrayOf((0xD9 shl 24) or (P.astre and 0xFFFFFF), transparent),
                    null, Shader.TileMode.CLAMP
                )
                c.drawCircle(P.astreX, P.astreY, r, p)
                p.shader = null
            }
            if (P.nuit) {
                val r = Alea(41)
                for (k in 0 until 46) {
                    val x = r() * 100f
                    val y = r() * 38f
                    val t = 0.18f + r() * 0.38f
                    val o = if (k % 5 == 0) 1f else 0.35f + r() * 0.6f
                    rond(x, y, t, 0xFFFFF8E0.toInt(), o)
                }
                halo(P.astreR * 3.2f, 0.55f)
                rond(P.astreX, P.astreY, P.astreR, P.astre)
                rond(P.astreX + 1.9f, P.astreY - 1.1f, P.astreR * 0.92f, mix(P.cielHaut, P.cielBas, 0.25f))
            } else {
                halo(P.astreR * 3.4f, 1f)
                rond(P.astreX, P.astreY, P.astreR, P.astre, 0.95f)
            }
            // La brume du matin.
            if (P.rang == 0) rect(0f, 26f, 100f, 16f, 0xFFFFFFFF.toInt(), 0.16f)
        }

        fun crete(y0: Float, amp: Float, graine: Int, col: Int, x0: Float = 0f, x1: Float = 100f,
                  f1: Float = 0.07f, f2: Float = 0.19f) {
            val r = Alea(graine)
            val p1 = r() * 6f
            val p2 = r() * 6f
            val t = Path()
            t.moveTo(x0, BAS)
            var x = x0
            while (x <= x1) {
                t.lineTo(x, y0 + sin(x * f1 + p1) * amp + sin(x * f2 + p2) * amp * 0.45f)
                x += 2f
            }
            t.lineTo(x1, BAS)
            t.close()
            forme(t, col)
        }

        fun bosquet(x0: Float, x1: Float, y: Float, taille: Float, graine: Int, col: Int) {
            val r = Alea(graine)
            var x = x0
            while (x <= x1) {
                val rr = taille * (0.65f + 0.5f * r())
                val cx = x + r() * taille * 0.4f
                val cy = y - rr * 0.35f + r() * 1.2f
                rond(cx, cy, rr, col)
                x += taille * 1.05f
            }
        }

        fun sapins(col: Int, vararg liste: Float) {
            var i = 0
            while (i < liste.size) {
                val x = liste[i]; val y = liste[i + 1]; val h = liste[i + 2]
                triangle(x, y - h, x + h * 0.32f, y, x - h * 0.32f, y, col)
                i += 3
            }
        }

        fun fenetre(x: Float, y: Float, w: Float, h: Float, rnd: Alea?) {
            val allumee = P.allume && (rnd == null || rnd() < 0.7f)
            if (allumee) rect(x - w * 0.6f, y - h * 0.4f, w * 2.2f, h * 1.8f, P.fenetre, 0.22f)
            rect(x, y, w, h, if (allumee) P.fenetre else mix(P.pierre, P.pres, 0.55f))
        }

        fun maison(x: Float, y: Float, w: Float, h: Float, rnd: Alea?, toit: Float? = null) {
            val ombre = mix(P.pierre, P.pres, 0.28f)
            rect(x, y - h, w, h, P.pierre)
            rect(x + w * 0.62f, y - h, w * 0.38f, h, ombre, 0.55f)
            val th = toit ?: (w * 0.55f)
            triangle(x - 0.4f, y - h, x + w / 2f, y - h - th, x + w + 0.4f, y - h, P.toit)
            if (h > 2.4f) fenetre(x + w * 0.3f, y - h * 0.62f, w * 0.2f, h * 0.26f, rnd)
        }

        fun eau(y: Float, graine: Int, vararg lumieres: Float) {
            val r = Alea(graine)
            rect(0f, y, 100f, BAS - y, P.eau)
            val clair = mix(P.eau, 0xFFFFFFFF.toInt(), if (P.nuit) 0.18f else 0.45f)
            for (k in 0 until 14) {
                val yy = y + 1f + r() * (BAS - y - 1.5f)
                val xx = r() * 92f
                val w = 3f + r() * 9f
                rect(xx, yy, w, 0.35f, clair, 0.7f)
            }
            if (P.allume) for (x in lumieres) rect(x - 0.3f, y + 0.6f, 0.6f, (BAS - y) * 0.7f, P.fenetre, 0.45f)
        }

        /**
         * Coupe net à [base] tout ce qui se peint ensuite, jusqu'au
         * `c.restore()` : le bas d'un carton glissé dans sa rainure.
         */
        fun poser(base: Float) {
            c.save()
            c.clipRect(-2f, -2f, LARGEUR + 2f, base)
        }

        /**
         * L'eau en carton : une rangée de vagues découpées de [y] à [bas], le
         * bord haut en festons, comme les rivières des théâtres de papier.
         * Pour les lieux posés sur un [plancher] : la rivière s'arrête net sur
         * lui au lieu de descendre jusqu'au bas de la fenêtre comme [eau].
         */
        fun riviere(y: Float, bas: Float, graine: Int, vararg lumieres: Float) {
            val r = Alea(graine)
            val t = Path()
            t.moveTo(-1f, bas); t.lineTo(-1f, y + 0.7f)
            var x = -1f
            while (x < LARGEUR + 1f) {
                t.quadTo(x + 1.6f, y - 0.5f, x + 3.2f, y + 0.7f)
                x += 3.2f
            }
            t.lineTo(LARGEUR + 1f, bas); t.close()
            forme(t, P.eau)
            val clair = mix(P.eau, 0xFFFFFFFF.toInt(), if (P.nuit) 0.18f else 0.45f)
            for (k in 0 until 8) {
                val yy = y + 1.4f + r() * (bas - y - 1.9f)
                rect(r() * 92f, yy, 3f + r() * 7f, 0.3f, clair, 0.7f)
            }
            if (P.allume) for (lx in lumieres) rect(lx - 0.3f, y + 1f, 0.6f, bas - y - 1.3f, P.fenetre, 0.45f)
        }

        /**
         * Les coulisses : deux châssis de feuillage, un de chaque côté, qui
         * encadrent la scène sans entrer dans son milieu, où se tient le
         * dessin du mot. Une silhouette sombre en aplat, comme un décor de
         * théâtre de papier vu à contre-jour.
         */
        fun coulisses() {
            val feuillage = mix(P.pres, 0xFF000000.toInt(), if (P.nuit) 0.25f else 0.38f)
            val tronc = mix(P.sol, 0xFF000000.toInt(), 0.3f)
            for (droite in booleanArrayOf(false, true)) {
                fun x(v: Float) = if (droite) LARGEUR - v else v
                val t = Path()
                t.moveTo(x(0.6f), BAS); t.lineTo(x(1.8f), 34f); t.lineTo(x(3.4f), 26f)
                t.lineTo(x(4.6f), 35f); t.lineTo(x(5.6f), BAS); t.close()
                forme(t, tronc)
                trait("M${x(3.8f)} 39Q${x(8.5f)} 35 ${x(12.5f)} 28.5", tronc, 1f)
                val r = Alea(if (droite) 62 else 61)
                feuillage(FEUILLES, ::x, r, feuillage)
            }
        }

        /**
         * Un feuillage de ronds ([ronds] : x, y, rayon), découpé d'un seul
         * carton : tous les ronds réunis, peints d'un coup, le bord de carton
         * à l'extérieur seulement.
         */
        private fun feuillage(ronds: FloatArray, x: (Float) -> Float, r: Alea, col: Int) {
            val f = Path()
            for (i in ronds.indices step 3) {
                val rr = ronds[i + 2] * (0.9f + 0.2f * r())
                f.addCircle(x(ronds[i]), ronds[i + 1], rr, Path.Direction.CW)
            }
            f.computeBounds(boite, true)
            peint(f, col, -0.6f, boite, 0.8f, union = true)
        }

        /**
         * La seconde rangée de coulisses des rares, un pas en retrait de la
         * première : un arbre de chaque côté, plus petit, plus clair, qui
         * dépasse de derrière le feuillage de devant. Deux châssis étagés au
         * lieu d'un seul : c'est la boîte d'un théâtre, pas un cadre.
         */
        fun coulissesDuFond() {
            val feuillage = mix(P.pres, 0xFF000000.toInt(), if (P.nuit) 0.12f else 0.22f)
            val tronc = mix(P.sol, 0xFF000000.toInt(), 0.15f)
            for (droite in booleanArrayOf(false, true)) {
                fun x(v: Float) = if (droite) LARGEUR - v else v
                val t = Path()
                t.moveTo(x(13.4f), BAS); t.lineTo(x(14.3f), 40f); t.lineTo(x(15.4f), 33f)
                t.lineTo(x(16.5f), 40f); t.lineTo(x(17.4f), BAS); t.close()
                forme(t, tronc)
                val r = Alea(if (droite) 64 else 63)
                feuillage(FEUILLES_DU_FOND, ::x, r, feuillage)
            }
        }

        /**
         * La seconde rangée du Breedewee : deux pignons de maison derrière les
         * piliers, posés sur le plancher, une fenêtre allumée le soir.
         */
        fun pignons() {
            val enduit = mix(P.pierre, 0xFF000000.toInt(), if (P.nuit) 0.12f else 0.22f)
            for (droite in booleanArrayOf(false, true)) {
                fun x(v: Float) = if (droite) LARGEUR - v else v
                forme("M${x(-1f)} 61L${x(-1f)} 31L${x(6f)} 24.5L${x(13f)} 31L${x(13f)} 61Z", enduit)
                forme("M${x(-1.6f)} 31.4L${x(6f)} 23.6L${x(13.6f)} 31.4L${x(13.6f)} 32.6L${x(6f)} 25L${x(-1.6f)} 32.6Z", P.toit)
                fenetre(min(x(8.2f), x(10.6f)), 36f, 2.4f, 3.4f, null)
                fenetre(min(x(8.2f), x(10.6f)), 44f, 2.4f, 3.4f, null)
            }
        }

        /**
         * Le plancher de la scène, de [y0] à [y1] : des lames qui fuient vers
         * le fond, plus claires vers la rampe, et la rainure où le carton de
         * devant est glissé. Peint par bandes, une par carton, pour que chaque
         * bande glisse avec le carton qui s'y tient.
         */
        fun plancher(y0: Float, y1: Float) {
            dansLePlancher = true
            val bois = mix(P.pierre, P.toit, 0.42f)
            val loin = mix(bois, 0xFF000000.toInt(), if (P.nuit) 0.25f else 0.16f)
            val pres = mix(bois, if (P.allume) P.fenetre else P.cielBas, if (P.nuit) 0.15f else 0.25f)
            c.save()
            c.clipRect(-1f, y0, LARGEUR + 1f, y1)
            if (actif) {
                p.style = Paint.Style.FILL
                p.color = 0xFF000000.toInt()
                p.shader = LinearGradient(0f, 50f, 0f, BAS, loin, pres, Shader.TileMode.CLAMP)
                c.drawRect(-1f, y0, LARGEUR + 1f, y1, p)
                p.shader = null
            }
            val joint = mix(bois, 0xFF000000.toInt(), 0.3f)
            var x = -70f
            while (x <= 170f) { ligne(50f + (x - 50f) * 0.22f, 40f, x, BAS, joint, 0.22f, 0.55f); x += 8.5f }
            val r = Alea(7)
            for (k in 0 until 9) {
                val t = (k + 1) / 10f
                val y = 50f + 16f * t * t
                val xb = 8f + r() * 84f
                ligne(xb, y, xb + 4f + 6f * t, y, joint, 0.22f, 0.55f)
            }
            if (actif) {
                val lumiere = 0xFFFFE2A0.toInt()
                p.color = 0xFF000000.toInt()
                p.shader = LinearGradient(0f, BAS, 0f, 58f,
                    (((if (P.nuit) 0.35f else 0.28f) * 255f).roundToInt() shl 24) or (lumiere and 0xFFFFFF),
                    lumiere and 0xFFFFFF, Shader.TileMode.CLAMP)
                c.drawRect(-1f, 58f, LARGEUR + 1f, BAS, p)
                p.shader = null
            }
            rect(-1f, y0, LARGEUR + 2f, 0.45f, mix(bois, 0xFF000000.toInt(), 0.6f), 0.85f)
            c.restore()
            dansLePlancher = false
        }

        /**
         * Un nuage de carton peint, de [l] unités de long, sa base droite à
         * [y] + 4 × [l] / 20 : trois bosses rondes, comme découpé aux ciseaux
         * (`nuagePeint` dans la maquette).
         */
        fun nuage(x: Float, y: Float, l: Float) {
            val s = l / 20f
            val col = mix(P.cielHaut, 0xFFFFFFFF.toInt(), if (P.nuit) 0.3f else 0.85f)
            val t = Path()
            t.moveTo(x, y + 4f * s)
            t.lineTo(x + 20f * s, y + 4f * s)
            arc(t, x + 17f * s, y + 3f * s, 3f * s, 0.3f, -PI.toFloat() * 0.7f)
            arc(t, x + 11.5f * s, y + 1.2f * s, 4.4f * s, -0.2f, -PI.toFloat() * 0.95f)
            arc(t, x + 5f * s, y + 2.6f * s, 3.2f * s, -0.4f, -PI.toFloat() * 1.05f)
            t.close()
            boite.set(x, y - 4f * s, x + 20f * s, y + 4f * s)
            peint(t, col, 0f, boite, 1.2f)
        }

        /** Un arc de cercle tourné dans le sens inverse des aiguilles, de [de] à [a] radians, comme `arc` du canevas HTML. */
        private fun arc(t: Path, cx: Float, cy: Float, r: Float, de: Float, a: Float) {
            var balayage = a - de
            while (balayage > 0f) balayage -= 2f * PI.toFloat()
            t.arcTo(RectF(cx - r, cy - r, cx + r, cy + r),
                Math.toDegrees(de.toDouble()).toFloat(), Math.toDegrees(balayage.toDouble()).toFloat())
        }

        /**
         * Les coulisses du Breedewee : deux piliers de pierre à contre-jour, une
         * lanterne tournée vers la scène. Le même emplacement que le feuillage
         * des [coulisses], sous l'arche du cadre des rares.
         */
        fun piliers() {
            val pierre = mix(P.pierre, 0xFF000000.toInt(), if (P.nuit) 0.3f else 0.45f)
            val joint = mix(pierre, 0xFF000000.toInt(), 0.3f)
            val fer = mix(pierre, 0xFF000000.toInt(), 0.35f)
            for (droite in booleanArrayOf(false, true)) {
                fun x(v: Float) = if (droite) LARGEUR - v else v
                forme("M${x(-1f)} 66L${x(-1f)} 24L${x(7.2f)} 24L${x(7.2f)} 26.2L${x(6.4f)} 26.2L${x(6.4f)} 66Z", pierre)
                rect(min(x(-1f), x(8f)), 22.4f, 9f, 1.8f, pierre)
                var y = 29.6f
                while (y < BAS) { ligne(x(-1f), y, x(6.4f), y, joint, 0.4f); y += 3.4f }
                if (P.allume) rond(x(9.8f), 33.5f, 2.8f, P.fenetre, 0.25f)
                ligne(x(6.4f), 31f, x(10f), 31f, fer, 0.6f)
                forme("M${x(9f)} 32L${x(10.6f)} 32L${x(10.3f)} 35L${x(9.3f)} 35Z", if (P.allume) P.fenetre else joint)
            }
        }

        /**
         * Une lanterne sur sa potence, scellée en ([x], [y]) dans le flanc d'un
         * pilier et tournée vers la droite ou vers la gauche. Allumée le soir.
         */
        fun lanterne(x: Float, y: Float, versDroite: Boolean) {
            val s = if (versDroite) 1f else -1f
            val fer = mix(P.toit, 0xFF000000.toInt(), 0.45f)
            ligne(x, y, x + 3.6f * s, y, fer, 0.55f)
            trait("M$x ${y + 2.2f}Q${x + 1.6f * s} ${y + 2f} ${x + 2.6f * s} $y", fer, 0.4f)
            if (P.allume) rond(x + 3.6f * s, y + 2.3f, 2.6f, P.fenetre, 0.25f)
            ligne(x + 3.6f * s, y, x + 3.6f * s, y + 0.9f, fer, 0.4f)
            forme("M${x + 2.8f * s} ${y + 0.9f}L${x + 4.4f * s} ${y + 0.9f}L${x + 4.1f * s} ${y + 3.6f}L${x + 3.1f * s} ${y + 3.6f}Z",
                if (P.allume) P.fenetre else mix(P.pierre, P.pres, 0.55f))
            rect(x + 2.6f * s - (if (versDroite) 0f else 2f), y + 0.5f, 2f, 0.5f, fer)
        }

        // ------------------------------------------------------ les lieux

        /** Veianen : le château sur son éperon, la ville basse et l'Our. */
        fun veianen() {
            val r = Alea(3)
            crete(33f, 2.4f, 5, P.loin)
            plan(1)
            forme("M0 66L0 45C14 41 30 36 42 32C52 29 66 29 76 32C86 35 94 40 100 42L100 66Z", P.milieu)
            val pi = P.pierre
            val om = mix(P.pierre, P.pres, 0.3f)
            rect(43f, 27f, 36f, 6f, om)
            var x = 43f
            while (x < 79f) { rect(x, 25.8f, 1.3f, 1.4f, om); x += 2.4f }
            rect(52f, 18f, 18f, 10f, pi); triangle(51.4f, 18f, 61f, 10.5f, 70.6f, 18f, P.toit)
            rect(45.5f, 15f, 5.5f, 13f, pi); triangle(45f, 15f, 48.25f, 7.5f, 51.5f, 15f, P.toit)
            rect(48.6f, 15f, 2.4f, 13f, om, 0.6f)
            rect(70f, 13f, 6.5f, 15f, pi); triangle(69.4f, 13f, 73.25f, 5.8f, 77.1f, 13f, P.toit)
            rect(73.8f, 13f, 2.7f, 15f, om, 0.6f)
            rect(61.8f, 13.5f, 2.2f, 5f, pi); triangle(61.5f, 13.5f, 62.9f, 9f, 64.3f, 13.5f, P.toit)
            for (fx in floatArrayOf(54f, 57.5f, 61f, 64.5f, 67f)) fenetre(fx, 21f, 1.3f, 2.2f, r)
            fenetre(47.4f, 19f, 1.1f, 1.8f, r); fenetre(72.4f, 17f, 1.2f, 2f, r); fenetre(72.4f, 22f, 1.2f, 2f, r)
            bosquet(30f, 44f, 34.5f, 2.4f, 8, mix(P.milieu, P.pres, 0.4f))
            bosquet(80f, 100f, 37f, 2.6f, 9, mix(P.milieu, P.pres, 0.4f))
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            forme("M0 66L0 52C20 48 40 50 60 49C80 48 92 50 100 52L100 66Z", P.pres)
            x = 14f
            while (x < 90f) {
                val h = 3f + r() * 2f
                maison(x, 50.5f - sin(x * 0.05f) * 0.6f, 3.8f, h, r)
                x += 5.4f
            }
            riviere(55.5f, DEVANT, 12, 17f, 39f, 61f, 82f)
            rond(2f, 50f, 7f, P.sol); rond(98f, 51f, 6f, P.sol)
            c.restore()
        }

        /** La Rout Bréck : le pont rouge au-dessus de la Pétrusse, le Kirchberg derrière. */
        fun routBreck() {
            val r = Alea(4)
            val fl = mix(P.loin, P.cielBas, 0.25f)
            val tours = floatArrayOf(8f, 4f, 15f, 13f, 3f, 11f, 18f, 5f, 19f, 26f, 3.4f, 13f, 31f, 4.2f, 16f, 86f, 4f, 12f, 92f, 3f, 9f)
            var i = 0
            while (i < tours.size) {
                val x = tours[i]; val w = tours[i + 1]; val h = tours[i + 2]
                rect(x, 38f - h, w, h, fl)
                if (P.allume) {
                    var y = 40f - h
                    while (y < 36f) { if (r() < 0.55f) rect(x + 0.8f, y, w - 1.6f, 0.5f, P.fenetre, 0.7f); y += 2.2f }
                }
                i += 3
            }
            crete(37f, 1.6f, 6, P.loin)
            plan(1)
            forme("M0 66L0 38C14 40 28 50 42 62L42 66Z", P.milieu)
            forme("M100 66L100 39C86 41 72 50 58 62L58 66Z", P.milieu)
            bosquet(0f, 40f, 46f, 2.2f, 7, mix(P.milieu, P.pres, 0.45f))
            bosquet(60f, 100f, 47f, 2.2f, 8, mix(P.milieu, P.pres, 0.45f))
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            rect(0f, 56f, 100f, 10f, P.pres)
            bosquet(34f, 66f, 57.5f, 2f, 9, P.sol)
            val rouge = intArrayOf(0xFFE8432E.toInt(), 0xFFE0482F.toInt(), 0xFFB8323A.toInt(), 0xFF8A2433.toInt())[P.rang]
            val rc = mix(rouge, 0xFFFFFFFF.toInt(), 0.25f)
            for (x in floatArrayOf(17f, 41f, 65f, 89f)) {
                trait("M${x - 5.5f} 33.5L$x 61L${x + 5.5f} 33.5", rouge, 1.5f)
                trait("M${x - 0.4f} 33.5L$x 61", mix(rouge, 0xFF000000.toInt(), 0.3f), 0.5f)
            }
            rect(-1f, 30f, 102f, 3.6f, rouge)
            rect(-1f, 30f, 102f, 0.7f, rc)
            if (P.allume) { var x = 2f; while (x < 100f) { rond(x, 29.5f, 0.45f, P.fenetre); x += 4f } }
            c.restore()
        }

        /** Esch-Sauer : le bourg dans sa boucle, la tour sur l'éperon. */
        fun eschSauer() {
            val r = Alea(5)
            crete(30f, 2.6f, 15, P.loin)
            plan(1)
            plancher(56f, DEVANT)
            poser(56f)
            forme("M0 66L0 27C10 25 20 30 28 40C32 46 33 56 31 66Z", P.milieu)
            forme("M100 66L100 26C90 24 80 30 73 38C69 46 69 56 71 66Z", P.milieu)
            bosquet(0f, 28f, 30f, 2.4f, 16, mix(P.milieu, P.pres, 0.35f))
            bosquet(72f, 100f, 30f, 2.4f, 17, mix(P.milieu, P.pres, 0.35f))
            riviere(51.5f, 56f, 18, 44f, 52f, 58f)
            c.restore()
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            forme("M30 66C31 54 38 42 50 37C62 42 69 54 70 66Z", mix(P.milieu, P.pres, 0.5f))
            val om = mix(P.pierre, P.pres, 0.3f)
            rect(47.5f, 25f, 5f, 12f, om)
            var x = 47.5f
            while (x < 52.5f) { rect(x, 23.8f, 0.8f, 1.4f, om); x += 1.4f }
            fenetre(49.4f, 28f, 1f, 2f, r)
            val maisons = floatArrayOf(40f, 48f, 4f, 3.5f, 44f, 45f, 3.8f, 3.2f, 48.5f, 43.5f, 4f, 3.4f, 53f, 44.5f, 3.6f, 3.2f,
                57f, 47f, 4f, 3.4f, 36f, 52f, 4.2f, 3.6f, 61f, 51f, 4f, 3.6f, 45.5f, 50.5f, 4f, 3.6f, 52f, 51f, 4.2f, 3.8f)
            var i = 0
            while (i < maisons.size) { maison(maisons[i], maisons[i + 1], maisons[i + 2], maisons[i + 3], r); i += 4 }
            rect(58.4f, 38f, 2.2f, 6.8f, P.pierre); triangle(58.2f, 38f, 59.5f, 33f, 60.8f, 38f, P.toit)
            sapins(P.sol, 4f, DEVANT, 9f, 9f, DEVANT, 7f, 94f, DEVANT, 8f, 89f, DEVANT, 6.5f)
            c.restore()
        }

        /** La Musel : les vignes en terrasses, le village au bord de l'eau. */
        fun musel() {
            val r = Alea(6)
            crete(30f, 2f, 20, P.loin, x0 = 30f)
            plan(1)
            plancher(56f, DEVANT)
            poser(56f)
            rect(0f, 40f, 100f, 7f, P.milieu)
            var x = 52f
            while (x < 100f) { maison(x, 45f, 3.4f, 2.6f + r() * 1.6f, r); x += 4.2f }
            rect(76.4f, 34f, 2.4f, 11f, P.pierre); triangle(76.1f, 34f, 77.6f, 27f, 79.1f, 34f, P.toit)
            fenetre(77.2f, 37f, 0.8f, 1.6f, r)
            riviere(46.5f, 56f, 21, 56f, 64f, 77f, 90f)
            forme("M62 52.6L86 52.6L84 54.4L64 54.4Z", P.sol)
            rect(80f, 51f, 3.2f, 1.8f, P.sol)
            if (P.allume) rond(81.6f, 51.9f, 0.4f, P.fenetre)
            c.restore()
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            val vigne = mix(P.milieu, 0xFF6F8F3E.toInt(), if (P.nuit) 0.1f else 0.35f)
            forme("M0 66L0 14C18 20 38 34 60 66Z", vigne)
            val rang = mix(vigne, P.sol, 0.55f)
            p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(0.7f, 0.9f), 0f)
            for (k in 0 until 13) {
                val y = 18f + k * 3.7f
                val x1 = min(60f, (y - 14f) * 1.12f + (if (y > 40f) (y - 40f) * 0.35f else 0f))
                trait("M-1 ${y}L$x1 ${y + min(6f, x1 * 0.08f)}", rang, 0.7f)
            }
            p.pathEffect = null
            c.restore()
        }

        /** Neimënster : l'abbaye au pied du rocher du Bock, le pont du Grund. */
        fun neimenster() {
            val r = Alea(7)
            crete(32f, 1.8f, 23, P.loin, x0 = 30f)
            plan(1)
            val roc = mix(P.pierre, P.pres, 0.42f)
            val roc2 = mix(roc, P.sol, 0.3f)
            bosquet(80f, 100f, 36f, 2.6f, 24, mix(P.milieu, P.pres, 0.3f))
            forme("M0 66L0 11L9 9.6L16 12L24 11.4L31 15L37 24L40 36L42 66Z", roc)
            var y = 16f
            while (y < 60f) { trait("M0 ${y}L${30f + y * 0.22f} ${y + 0.8f}", roc2, 0.4f, 0.7f); y += 4.5f }
            val rempart = mix(P.pierre, P.pres, 0.2f)
            rect(0f, 6.5f, 30f, 4f, rempart)
            var x = 0f
            while (x < 30f) { rect(x, 5.4f, 1.2f, 1.3f, rempart); x += 2.2f }
            rect(21f, 2f, 4.5f, 6f, rempart)
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            forme("M37 37.5L93 37.5L91 34L39 34Z", P.toit)
            rect(38f, 37.5f, 54f, 13f, P.pierre)
            rect(38f, 47.5f, 54f, 3f, mix(P.pierre, P.pres, 0.25f))
            x = 40f
            while (x < 91f) { fenetre(x, 39.4f, 1.3f, 2.2f, r); fenetre(x, 43.6f, 1.3f, 2.2f, r); x += 3.6f }
            rect(45f, 21f, 6.4f, 29.5f, P.pierre)
            rect(49f, 21f, 2.4f, 29.5f, mix(P.pierre, P.pres, 0.3f), 0.6f)
            triangle(44.6f, 21f, 48.2f, 11f, 51.8f, 21f, P.toit)
            rond(48.2f, 24.5f, 1.3f, mix(P.pierre, P.pres, 0.45f))
            fenetre(47.5f, 28f, 1.4f, 3f, r)
            riviere(53.5f, DEVANT, 25, 42f, 52f, 63f, 74f, 85f)
            forme("M52 52L100 52L100 56.2L97 56.2C97 54 93 54 93 56.2L85 56.2C85 54 81 54 81 56.2L73 56.2C73 54 69 54 69 56.2L61 56.2C61 54 57 54 57 56.2L52 56.2Z",
                mix(P.pierre, P.pres, 0.15f))
            c.restore()
        }

        /** La Gëlle Fra : l'obélisque et sa statue dorée, le pont Adolphe derrière. */
        fun gelleFra() {
            crete(34f, 1.4f, 27, P.loin)
            plan(1)
            val pi = mix(P.pierre, P.pres, 0.22f)
            rect(-1f, 22f, 102f, 2.6f, pi)
            trait("M6 54Q50 -6 94 54", pi, 3f)
            var x = 14f
            while (x <= 86f) {
                val t = (x - 6f) / 88f
                val y = (1 - t) * (1 - t) * 54f + 2 * (1 - t) * t * (-6f) + t * t * 54f
                if (y > 24.5f) rect(x - 0.35f, 24.4f, 0.7f, y - 24.4f, pi)
                x += 4.5f
            }
            if (P.allume) { x = 3f; while (x < 100f) { rond(x, 21.6f, 0.45f, P.fenetre); x += 5f } }
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            bosquet(0f, 100f, 50f, 2.8f, 28, P.milieu)
            bosquet(0f, 100f, 55f, 2.4f, 29, mix(P.milieu, P.pres, 0.5f))
            rect(0f, 56.5f, 100f, 10f, P.pres)
            val ob = P.pierre
            val obo = mix(P.pierre, P.pres, 0.3f)
            rect(75f, 52f, 14f, 6.5f, ob); rect(76.5f, 49f, 11f, 3.2f, ob); rect(83f, 49f, 4.5f, 9.5f, obo, 0.5f)
            forme("M79.2 49L80.4 12L83.6 12L84.8 49Z", ob)
            forme("M82.4 12L83.6 12L84.8 49L82.6 49Z", obo, 0.55f)
            triangle(80f, 12f, 82f, 9.6f, 84f, 12f, ob)
            val or = if (P.nuit) 0xFFFFD36A.toInt() else 0xFFE2AC36.toInt()
            if (P.allume) rond(82f, 5f, 6f, or, 0.18f)
            forme("M81.3 9.6L81.6 5.6L82.4 5.6L82.7 9.6Z", or)
            rond(82f, 4.7f, 0.85f, or)
            trait("M81.6 6L79.6 2.6M82.4 6L84.4 2.6", or, 0.55f)
            anneau(82f, 1.8f, 1.5f, or, 0.45f)
            c.restore()
        }

        /** La Schueberfouer : la grande roue, les tentes, les guirlandes. */
        fun fouer() {
            crete(42f, 1.4f, 31, P.loin)
            plan(1)
            val cx = 67f; val cy = 27f; val R = 19.5f
            val struct = if (P.nuit || P.rang == 2) mix(P.pierre, P.pres, 0.1f) else mix(0xFFFFFFFF.toInt(), P.pierre, 0.35f)
            trait("M57 57L$cx ${cy}L77 57M59.5 57L$cx ${cy}L74.5 57", mix(struct, P.pres, 0.35f), 0.9f)
            for (k in 0 until 16) {
                val a = k / 16f * PI.toFloat() * 2f
                ligne(cx, cy, cx + R * cos(a), cy + R * sin(a), struct, 0.35f)
            }
            anneau(cx, cy, R, struct, 0.9f)
            anneau(cx, cy, R - 2.2f, struct, 0.4f)
            rond(cx, cy, 1.6f, struct)
            if (P.allume) for (k in 0 until 48) {
                val a = k / 48f * PI.toFloat() * 2f
                rond(cx + R * cos(a), cy + R * sin(a), 0.42f, if (k % 2 == 1) P.fenetre else 0xFFFF8FB8.toInt())
            }
            val coul = intArrayOf(0xFFD6453A.toInt(), 0xFFE9B02F.toInt(), 0xFF2F9C95.toInt(),
                0xFF5A6FD1.toInt(), 0xFFD4569A.toInt(), 0xFF3F9A4F.toInt())
            val noir = 0xFF000000.toInt()
            for (k in 0 until 12) {
                val a = k / 12f * PI.toFloat() * 2f + 0.2f
                val x = cx + R * cos(a); val y = cy + R * sin(a)
                ligne(x, y, x, y + 1.4f, struct, 0.3f)
                p.style = Paint.Style.FILL
                teinte(if (P.nuit) mix(coul[k % 6], noir, 0.25f) else coul[k % 6], 1f)
                if (actif) c.drawRoundRect(x - 1.5f, y + 1.3f, x + 1.5f, y + 3.7f, 0.6f, 0.6f, p)
                if (P.allume) rect(x - 0.9f, y + 1.8f, 1.8f, 0.9f, P.fenetre, 0.85f)
            }
            plan(2)
            plancher(DEVANT, BAS)
            rect(0f, 57f, 100f, DEVANT - 57f, P.pres)
            fun tente(x: Float, w: Float, h: Float, col: Int) {
                val toile = if (P.nuit) mix(0xFFF3ECE0.toInt(), noir, 0.55f) else 0xFFF6EFE2.toInt()
                val y = DEVANT
                triangle(x - w, y, x, y - h, x + w, y, toile)
                val bande = if (P.nuit) mix(col, noir, 0.45f) else col
                for (k in intArrayOf(-2, 0, 2)) triangle(x, y - h, x + k * w / 3f - w / 6f, y, x + k * w / 3f + w / 6f, y, bande)
                ligne(x, y - h, x, y - h - 2.4f, P.sol, 0.3f)
                triangle(x, y - h - 2.4f, x + 1.8f, y - h - 1.9f, x, y - h - 1.4f, 0xFFD6453A.toInt())
            }
            tente(12f, 9f, 11f, 0xFFD6453A.toInt())
            tente(31f, 7f, 9f, 0xFF2F6FB0.toInt())
            tente(46f, 5.5f, 7f, 0xFFD6453A.toInt())
            trait("M0 40Q14 47 26 43Q38 39 50 45", P.sol, 0.25f)
            for (k in 0..20) {
                val t = k / 20f
                val x = t * 50f
                val yy = if (t <= 0.52f) {
                    val u = t / 0.52f
                    (1 - u) * (1 - u) * 40f + 2 * (1 - u) * u * 47f + u * u * 43f
                } else {
                    val u = (t - 0.52f) / 0.48f
                    (1 - u).pow(2) * 43f + 2 * (1 - u) * u * 39f + u * u * 45f
                }
                rond(x, yy + 0.5f, 0.55f, if (P.allume) coul[k % 6] else mix(P.pierre, P.pres, 0.2f), if (P.allume) 0.95f else 1f)
            }
        }

        /** Le Mëllerdall : les rochers de grès et la Schéissendëmpel. */
        fun mellerdall() {
            val r = Alea(10)
            crete(30f, 2.2f, 33, P.loin)
            bosquet(0f, 100f, 34f, 2.2f, 34, mix(P.loin, P.milieu, 0.5f))
            plan(1)
            val roc = mix(P.pierre, 0xFFB48A55.toInt(), if (P.nuit) 0.15f else 0.32f)
            val roco = mix(roc, P.pres, 0.4f)
            val mousse = mix(P.milieu, 0xFF4F7D3A.toInt(), if (P.nuit) 0.1f else 0.35f)
            forme("M0 66L0 13C4 9 12 9 16 13L18 30C17 40 20 52 23 66Z", roc)
            forme("M11 12C14 12 16 13 16 13L18 30C17 40 20 52 23 66L15 66C14 50 13 30 11 12Z", roco, 0.6f)
            forme("M100 66L100 17C96 11 86 11 82 17L80 33C82 44 78 55 79 66Z", roc)
            forme("M82 17L80 33C82 44 78 55 79 66L86 66C85 50 86 30 84 15Z", roco, 0.6f)
            var y = 18f
            while (y < 64f) {
                trait("M0 ${y}L${16f + y * 0.08f} ${y + 0.5f}", roco, 0.4f)
                trait("M${81f - y * 0.02f} ${y}L100 ${y + 0.5f}", roco, 0.4f)
                y += 4.2f
            }
            bosquet(0f, 16f, 12f, 2.2f, 35, mousse)
            bosquet(83f, 100f, 15f, 2.2f, 36, mousse)
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            forme("M36 33L64 33L64 38.5L56 38.5C56 35.6 44 35.6 44 38.5L36 38.5Z", mix(P.pierre, P.pres, 0.2f))
            val blanc = if (P.nuit) 0xFF9FB3D8.toInt() else 0xFFFFFFFF.toInt()
            rect(44.6f, 37.5f, 10.8f, 7f, blanc, 0.85f)
            forme("M38 44.5L62 44.5L64 46.5L36 46.5Z", roco)
            rect(40f, 46.5f, 20f, 6f, blanc, 0.8f)
            forme("M32 52.5L68 52.5L70 54.5L30 54.5Z", roco)
            rect(34f, 54.5f, 32f, 4.5f, blanc, 0.75f)
            val ecume = mix(P.eau, 0xFFFFFFFF.toInt(), 0.5f)
            for (k in 0 until 14) {
                val x = 40f + r() * 20f
                rect(x, 38f + r() * 18f, 0.3f, 2f + r() * 3f, ecume, 0.8f)
            }
            riviere(56.5f, DEVANT, 37)
            forme("M0 66C4 58 10 56 16 60C14 62 12 64 12 66Z", mousse)
            forme("M100 66C96 58 90 56 84 60C86 62 88 64 88 66Z", mousse)
            c.restore()
        }

        /** La ville vue de la Corniche : les flèches de la cathédrale, les remparts, le Grund. */
        fun stad() {
            val r = Alea(11)
            crete(36f, 1.2f, 39, P.loin)
            plan(1)
            val pi = P.pierre
            val om = mix(P.pierre, P.pres, 0.3f)
            var x = 0f
            while (x < 100f) {
                val h = 5f + r() * 6f
                maison(x, 38f, 4.2f, h, r, toit = 2.6f + r() * 2.4f)
                x += 4.6f
            }
            val fleches = floatArrayOf(30f, 3.2f, 8f, 36f, 2.2f, 15f, 42.5f, 2.4f, 16f)
            var i = 0
            while (i < fleches.size) {
                val fx = fleches[i]; val w = fleches[i + 1]; val fy = fleches[i + 2]
                rect(fx - w / 2f, fy + 8f, w, 30f - fy, pi)
                triangle(fx - w / 2f - 0.2f, fy + 8f, fx, fy, fx + w / 2f + 0.2f, fy + 8f, P.toit)
                i += 3
            }
            rect(0f, 37f, 100f, 13f, om)
            x = 0f
            while (x < 100f) { rect(x, 35.8f, 1.4f, 1.4f, om); x += 2.6f }
            x = 4f
            while (x < 100f) {
                forme("M$x 50L$x 44.5Q${x + 2.4f} 41.5 ${x + 4.8f} 44.5L${x + 4.8f} 50Z", mix(om, P.sol, 0.5f))
                if (P.allume && r() < 0.5f) rect(x + 1.8f, 45.5f, 1.2f, 1.8f, P.fenetre)
                x += 9f
            }
            // Le Grund et l'Alzette, sur le devant de la scène : la rive et ses
            // maisons, puis la rivière en rangée de vagues découpées, posées
            // sur le plancher.
            plan(2)
            plancher(DEVANT, BAS)
            bosquet(0f, 100f, 52.5f, 2.4f, 40, P.milieu)
            rect(0f, 54f, 100f, DEVANT - 54f, P.pres)
            x = 6f
            while (x < 96f) { maison(x, DEVANT - 2f, 4.4f, 2.8f + r() * 1.3f, r); x += 6.2f }
            riviere(DEVANT - 2.4f, DEVANT, 41, 10f, 30f, 52f, 71f)
        }

        /**
         * Le Breedewee : une porte de pierre, la ruelle derrière, la ville haute
         * au loin, le tout posé sur le plancher de la scène.
         *
         * La porte est un cadre de scène dans la fenêtre : ce qu'on voit à
         * travers glisse contre ses bords quand on penche la carte, la
         * parallaxe la plus lisible qui soit. Derrière le dessin du mot, dans
         * l'axe, la ruelle n'a pas de fenêtre, seulement une porte basse. La
         * ruelle et la porte tiennent debout chacune dans sa rainure du
         * [plancher] : jusqu'en 37.9.2, une chaussée pavée descendait jusqu'au
         * bas de la fenêtre et rien ne disait où les cartons étaient posés.
         *
         * Les couleurs tiennent compte de la végétation de [Habillage], qui se
         * reconnaît à sa couleur : la porte est en [Heure.pierre] et les
         * façades en enduit, la pierre tirée vers l'horizon, ce qui ne passe
         * pour de la verdure à aucune heure.
         */
        fun breedewee() {
            val r = Alea(12)
            val pierre = P.pierre
            val joint = mix(pierre, P.pres, 0.42f)

            // La ville haute, au-dessus de la ruelle : des pignons, et l'église
            // dans leur creux, dont le clocher passe derrière la clé de la porte.
            val pignons = floatArrayOf(0f, 7f, 9f, 6f, 9f, 12f, 15f, 8f, 8f, 23f, 7f, 10f, 30f, 9f, 8f, 39f, 6f, 11f,
                45f, 10f, 9f, 66f, 8f, 11f, 74f, 9f, 8f, 83f, 7f, 10f, 90f, 10f, 9f)
            var i = 0
            while (i < pignons.size) {
                val x = pignons[i]; val w = pignons[i + 1]; val h = pignons[i + 2]
                rect(x, 33f - h, w, h + 33f, pierre)
                triangle(x - 0.3f, 33f - h, x + w / 2f, 33f - h - w * 0.45f, x + w + 0.3f, 33f - h, P.toit)
                if (P.allume) {
                    var y = 35f - h
                    while (y < 31f) { if (r() < 0.45f) rect(x + w * 0.4f, y, 0.9f, 1.3f, P.fenetre, 0.8f); y += 3f }
                }
                i += 3
            }
            rect(55f, 22f, 11f, 44f, pierre)
            triangle(54.6f, 22f, 60.5f, 17.5f, 66.4f, 22f, P.toit)
            rect(57.5f, 12f, 4f, 10f, pierre)
            triangle(57.1f, 12f, 59.5f, 4f, 61.9f, 12f, P.toit)

            // La ruelle derrière la porte : des façades enduites, plus claires
            // que la pierre de la porte, qui s'en détache.
            plan(1)
            val facades = floatArrayOf(-1f, 12f, 16f, 11f, 11f, 14f, 22f, 12f, 17f, 34f, 12f, 15f, 46f, 10f, 18f,
                56f, 12f, 14f, 68f, 11f, 17f, 79f, 11f, 15f, 90f, 11f, 16f)
            i = 0
            while (i < facades.size) {
                val x = facades[i]; val w = facades[i + 1]; val h = facades[i + 2]
                val enduit = mix(pierre, P.cielBas, if ((i / 3) % 2 == 0) 0.3f else 0.42f)
                rect(x, 50f - h, w, h, enduit)
                triangle(x - 0.4f, 50f - h, x + w / 2f, 50f - h - w * 0.3f, x + w + 0.4f, 50f - h, P.toit)
                // Dans l'axe, derrière le mot, pas de fenêtre (règle 5 de la
                // skill `direction-artistique` : le mot reste l'acteur).
                if (x + w <= 42f || x >= 58f) {
                    var fy = 53.5f - h
                    while (fy < 45f) {
                        var fx = x + 2.4f
                        while (fx < x + w - 2.6f) { fenetre(fx, fy, 1.5f, 2.4f, r); fx += 4f }
                        fy += 5f
                    }
                }
                i += 3
            }
            forme("M48.4 50L48.4 45.2Q51 42.6 53.6 45.2L53.6 50Z",
                if (P.allume) mix(P.fenetre, P.toit, 0.35f) else mix(pierre, P.pres, 0.55f))
            // La ruelle s'arrête net sur le plancher, comme un carton glissé
            // dans sa rainure.
            plancher(50f, 55f)

            // La porte, sur le devant de la scène.
            plan(2)
            val cx = 50f
            val naissance = 30f
            val ri = 13.5f
            val re = 19.5f
            val seuil = 55f
            plancher(seuil, BAS)
            // La porte : deux piliers et l'arc plein cintre, d'un seul carton.
            val porte = Path()
            porte.moveTo(cx - re, seuil)
            porte.lineTo(cx - re, naissance)
            porte.arcTo(RectF(cx - re, naissance - re, cx + re, naissance + re), 180f, 180f)
            porte.lineTo(cx + re, seuil)
            porte.lineTo(cx + ri, seuil)
            porte.lineTo(cx + ri, naissance)
            porte.arcTo(RectF(cx - ri, naissance - ri, cx + ri, naissance + ri), 0f, -180f)
            porte.lineTo(cx - ri, seuil)
            porte.close()
            forme(porte, pierre)
            // Les tailloirs, qui débordent : ils font la silhouette de la porte.
            rect(cx - re - 1f, naissance - 0.2f, re - ri + 2f, 1.7f, pierre)
            rect(cx + ri - 1f, naissance - 0.2f, re - ri + 2f, 1.7f, pierre)
            // Les claveaux, et la clé qui dépasse de l'arc.
            val n = 9
            val pi = PI.toFloat()
            for (k in 1 until n) {
                val a = pi + k * pi / n
                ligne(cx + ri * cos(a), naissance + ri * sin(a), cx + re * cos(a), naissance + re * sin(a), joint, 0.4f)
            }
            val demi = pi / n / 2f
            val haut = 1.5f * pi
            fun px(rr: Float, a: Float) = cx + rr * cos(a)
            fun py(rr: Float, a: Float) = naissance + rr * sin(a)
            val angles = floatArrayOf(haut - demi * 0.9f, haut - demi * 1.15f, haut + demi * 1.15f, haut + demi * 0.9f)
            val rayons = floatArrayOf(ri - 0.6f, re + 1.4f, re + 1.4f, ri - 0.6f)
            val cle = Path()
            cle.moveTo(px(rayons[0], angles[0]), py(rayons[0], angles[0]))
            for (q in 1..3) cle.lineTo(px(rayons[q], angles[q]), py(rayons[q], angles[q]))
            cle.close()
            forme(cle, pierre)
            ligne(px(rayons[0], angles[0]), py(rayons[0], angles[0]), px(rayons[1], angles[1]), py(rayons[1], angles[1]), joint, 0.4f)
            ligne(px(rayons[3], angles[3]), py(rayons[3], angles[3]), px(rayons[2], angles[2]), py(rayons[2], angles[2]), joint, 0.4f)
            // Les assises des piliers, joints croisés.
            for (gauche in booleanArrayOf(true, false)) {
                val xa = if (gauche) cx - re else cx + ri
                val w = re - ri
                var decale = gauche
                var y = naissance + 1.5f + 3.4f
                while (y < seuil) {
                    ligne(xa, y, xa + w, y, joint, 0.4f)
                    val xj = xa + w * (if (decale) 0.38f else 0.62f)
                    ligne(xj, y - 3.4f, xj, y, joint, 0.4f)
                    decale = !decale
                    y += 3.4f
                }
            }
            lanterne(cx + re, naissance + 6f, true)
            lanterne(cx - re, naissance + 6f, false)
        }
    }
}
