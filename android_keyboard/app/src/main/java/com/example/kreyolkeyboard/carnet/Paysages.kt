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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

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
 * Le [BREEDEWEE], la ruelle du Grund, est le lieu des cartes venues du jeu
 * imprimé (voir [CartesPapier.corriger]), qui le portent à la place de celui
 * de leur champ. Depuis la grille de 9 × 3 (2026-11-14), il est aussi l'un
 * des trois lieux de la ville, pour les mots sans champ.
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
    BREEDEWEE("Breedewee"),

    // Les lieux de la deuxième vague (2026-11-13), d'après les photos du
    // propriétaire : chacun partage un champ avec un lieu de la première
    // ([choix]). À la fin de la liste, pour ne pas décaler les autres.
    PAFENDALL("Pafendall"),
    ANSEBUERG("Ansebuerg"),
    BELVAL("Belval"),
    BAERDREF("Bäerdref"),
    NEI_BRECK("Nei Bréck"),

    // La troisième vague (2026-11-14), d'après les photos du propriétaire.
    FELSMILLEN("Felsmillen"),
    OURDALL("Ourdall"),
    STAUSEI("Stauséi"),
    CONZEFENN("Conzefenn"),
    HAFF_REIMECH("Haff Réimech"),
    HOHLLAY("Hohllay"),
    NAPOLEONSGAART("Napoleonsgaart"),
    KALLEKTUFFQUELL("Kallektuffquell"),
    PEITRUSDALL("Péitrusdall"),
    WOLLEFSSCHLUCHT("Wollefsschlucht"),
    PRIEDEGTSTULL("Priedegtstull"),
    DUMONGSHAFF("Dumongshaff");

    /** Le champ dont ce lieu est une fenêtre, `null` pour les lieux de la ville des mots sans champ. */
    fun champ(): Champ? = Champ.values().firstOrNull { this in choix(it) }

    companion object {
        /**
         * Les lieux de l'album des paysages : tous, rangés par champ comme
         * leur grille de 9 × 3 ([choix]), le premier de chaque champ d'abord.
         */
        val ALBUM: List<Lieu> by lazy { (Champ.values().toList() + null).flatMap { choix(it) } }

        /** Les lieux ajoutés à un champ, qui n'ont pas de nuit : voir [pour]. */
        val ALBUM_SANS_NUIT: Set<Lieu> by lazy { ALBUM.filter { it != choix(it.champ())[0] }.toSet() }

        /** Le lieu d'une carte : celui qu'elle impose, sinon le premier de son champ. */
        fun de(blason: Blasonnement): Lieu = blason.lieu ?: de(blason.champ)

        /**
         * Les trois lieux d'un champ, le premier d'abord : celui que
         * montraient toutes ses cartes avant la deuxième vague.
         *
         * **La grille de 9 × 3** (2026-11-14) : les huit champs et la ville
         * des mots sans champ ont chacun trois lieux, pour qu'un enfant sache
         * d'un coup d'œil ce qui manque à l'album (« le troisième lieu du
         * savoir »). Le Priedegtstull, la chaire de pierre, passe au savoir
         * et à la parole ; le Péitrusdall, sous le pont Adolphe, rejoint la
         * Nei Bréck à la chose publique ; la passerelle du Dumongshaff va à la
         * société, et le Breedewee, ruelle du Grund, entre dans la ville. Les
         * cartes gagnées avant gardent leur lieu ([choix39], [Carnet.basculeDeLaGrille]).
         */
        fun choix(champ: Champ?): List<Lieu> = when (champ) {
            Champ.MOUVEMENT -> listOf(ROUT_BRECK, PAFENDALL, FELSMILLEN)
            Champ.TEMPS -> listOf(VEIANEN, ANSEBUERG, OURDALL)
            Champ.TERRITOIRE -> listOf(ESCH_SAUER, STAUSEI, CONZEFENN)
            Champ.ECONOMIE -> listOf(MUSEL, BELVAL, HAFF_REIMECH)
            Champ.SAVOIR -> listOf(NEIMENSTER, HOHLLAY, PRIEDEGTSTULL)
            Champ.PUBLIC -> listOf(GELLE_FRA, NEI_BRECK, PEITRUSDALL)
            Champ.SOCIETE -> listOf(FOUER, NAPOLEONSGAART, DUMONGSHAFF)
            Champ.VIE -> listOf(MELLERDALL, BAERDREF, KALLEKTUFFQUELL)
            null -> listOf(STAD, WOLLEFSSCHLUCHT, BREEDEWEE)
        }

        /**
         * La répartition de la 39.0.0, d'avant la grille de 9 × 3 : deux
         * lieux au savoir, à la chose publique et à la société, cinq à la
         * ville. Elle ne sert plus qu'aux cartes gagnées entre les deux
         * bascules ([pour39]). **Ne plus la changer.**
         */
        private fun choix39(champ: Champ?): List<Lieu> = when (champ) {
            Champ.SAVOIR -> listOf(NEIMENSTER, HOHLLAY)
            Champ.PUBLIC -> listOf(GELLE_FRA, NEI_BRECK)
            Champ.SOCIETE -> listOf(FOUER, NAPOLEONSGAART)
            null -> listOf(STAD, PEITRUSDALL, WOLLEFSSCHLUCHT, PRIEDEGTSTULL, DUMONGSHAFF)
            else -> choix(champ)
        }

        /**
         * Le sel du tirage de la grille de 9 × 3, cherché par
         * `scripts/sel_des_lieux.py` : celui qui laisse au moins trois
         * mots de Wuertplaz à chaque case de l'album dans les quatre langues
         * (`SeriesPaysagesAssetTest`). Le sel `lieu:` de la 39.0.0 n'en
         * laissait qu'un à la case rare du Péitrusdall dans les grilles
         * anglaises : le champ public n'y a que quatorze rares.
         */
        private const val SEL = "lieu:9x3:2779:"

        /**
         * Le lieu d'un mot de [champ] gagné depuis la grille de 9 × 3 : parmi
         * les [choix] du champ, tiré une fois pour toutes sur le mot
         * (son représentant de famille), si bien que le même mot montre
         * toujours le même lieu. Les cartes gagnées avant la deuxième vague
         * gardent le premier lieu de leur champ, celles gagnées sous la
         * 39.0.0 leur tirage d'alors ([pour39], [Carnet.basculeDesLieux]).
         *
         * La nuit des très rares reste au premier lieu du champ : il y a trop
         * peu de très rares par champ pour remplir deux cases (trois mots du
         * champ public dans les grilles anglaises de Wuertplaz), et l'album
         * doit rester complétable dans chaque langue ([ALBUM_SANS_NUIT]).
         *
         * **Ne pas changer le sel ni le hachage** : ils fixent le lieu des
         * cartes déjà gagnées. Une nouvelle répartition se fait comme
         * celle-ci : une bascule de plus, l'ancienne table gardée.
         */
        fun pour(champ: Champ?, mot: String, rarete: Rarete): Lieu = tirer(choix(champ), SEL, mot, rarete)

        /** Le tirage de la 39.0.0, pour les cartes gagnées sous cette version : voir [pour]. */
        fun pour39(champ: Champ?, mot: String, rarete: Rarete): Lieu = tirer(choix39(champ), "lieu:", mot, rarete)

        private fun tirer(l: List<Lieu>, sel: String, mot: String, rarete: Rarete): Lieu {
            if (l.size == 1 || rarete == Rarete.TRES_RARE) return l[0]
            var h = 0x811C9DC5.toInt()
            for (ch in "$sel$mot") { h = h xor ch.code; h *= 0x01000193 }
            return l[(h ushr 1) % l.size]
        }

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
        "${lieu.ordinal}_${heure.rang}_${teinte}_${souvenir?.saison}_${souvenir?.fete}_${facture.ordinal}_${l}x${h}_$VIF"

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
            if (PAPIER && facture == Facture.GOUACHE) Grain.poser(bitmap)
            raviver(bitmap)
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
        val releve = Bitmap.createBitmap(largeurPx, hauteurPx, Bitmap.Config.ALPHA_8)
        dessiner(cc, lieu, heure, ciel = false, facture = facture, echelle = k[0], releve = aLEchelle(releve, k))
        c.drawBitmap(calque, 0f, 0f, null)
        Habillage.appliquer(bitmap, calque, lieu, heure, souvenir, k, releve)
        calque.recycle()
        releve.recycle()
        if (PAPIER && facture == Facture.GOUACHE) Grain.poser(bitmap)
        raviver(bitmap)
        return bitmap
    }

    /**
     * De combien chaque plan glisse au plus quand la carte penche à fond, en
     * part de la largeur (de la hauteur pour le tangage) : le ciel, les
     * nuages, le lointain, le milieu, le proche, puis le surcadrage.
     *
     * C'est le fond qui bouge le plus, comme derrière une vitre : le cadre de
     * la carte est ce qu'il y a de plus près, le dessin du mot est posé
     * devant et ne bouge pas, et plus un plan est loin du verre, plus il
     * glisse quand l'œil change d'angle. Les valeurs sont celles de la
     * maquette `cinq_plans.html` (skill `direction-artistique`, règle 8).
     *
     * Le sixième rang est celui du surcadrage ([Surcadre], [brins]), juste
     * derrière la vitre : il n'a pas de calque, ses brins sont posés un à un.
     */
    val GLISSEMENT = floatArrayOf(0.05f, 0.04f, 0.03f, 0.017f, 0.007f, 0.003f)

    /** Le rang des nuages parmi les plans : une bande qui défile, voir [nuages]. */
    const val NUAGES = 1

    /**
     * Cinq cartons pour toutes les cartes (décision du propriétaire,
     * 2026-11-01, règle 8 de la skill `direction-artistique`), quelle que
     * soit la rareté : une commune, la carte qu'on manipule le plus, a le
     * plein effet de théâtre. La rareté se dit par l'heure, le métal et la
     * plaque, qu'on voit sans pencher la carte. Le surcadrage ([brins]) vient
     * en plus, devant.
     */
    const val PLANS = 5

    /**
     * Le lieu en cinq calques ([PLANS]), pour la
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
        val n = PLANS
        val caches = Array(n) { CACHE.get("${cle}_p$it")?.takeIf { b -> !b.isRecycled } }
        val ombresOk = !PAPIER || (2 until n).all { CACHE.get("${cle}_o$it")?.isRecycled == false }
        val nBrins = gabarits(lieu, souvenir?.saison).size
        val brinsOk = (0 until nBrins).all {
            CACHE.get("${cle}_b$it")?.isRecycled == false && CACHE.get("${cle}_c$it")?.isRecycled == false
        }
        val hiver = souvenir?.saison == Saison.WANTER
        val neigeOk = !hiver || (2 until n).all { CACHE.get("${cle}_n$it")?.isRecycled == false }
        if (caches.all { it != null } && ombresOk && brinsOk && neigeOk) return caches.requireNoNulls()
        val ombres = arrayOfNulls<Bitmap>(n)
        val neiges = arrayOfNulls<Bitmap>(n)
        val rendus = rendrePlans(lieu, heure, largeurPx, hauteurPx, souvenir, ombres, neiges)
        rendus.forEachIndexed { i, b -> CACHE.put("${cle}_p$i", b) }
        ombres.forEachIndexed { i, b -> if (b != null) CACHE.put("${cle}_o$i", b) }
        neiges.forEachIndexed { i, b -> if (b != null) CACHE.put("${cle}_n$i", b) }
        gabarits(lieu, souvenir?.saison).forEachIndexed { i, g ->
            val (image, ombre) = rendreBrin(g, heure, echelleDuPlan(g.plan, largeurPx, hauteurPx))
            CACHE.put("${cle}_b$i", image)
            CACHE.put("${cle}_c$i", ombre)
        }
        return rendus
    }

    /**
     * Un brin du surcadrage ([Surcadre]) : une branche, ou ce qui pousse au
     * pied de la scène, découpé à part avec son ombre pour que la carte
     * ouverte le fasse bouger à chaque trame (2026-11-14) : les branches se
     * balancent autour de leur attache, hors champ dans le coin ; les
     * roseaux, les fougères et les herbes ploient depuis leur pied. Une
     * brise lente au repos, un vrai coup de vent quand on secoue le
     * téléphone (voir [Vie.balancement]).
     *
     * [boite] est en unités de la boîte 100 × 66 du calque [plan] (4 le
     * proche, 5 le rang de devant, qui glisse comme le sixième plan de
     * [GLISSEMENT]) ; ([px], [py]) est le point qui ne bouge pas.
     */
    class Brin internal constructor(
        val image: Bitmap, val ombre: Bitmap, val plan: Int, val boite: RectF,
        val px: Float, val py: Float, val sorte: Sorte, val droite: Boolean, val phase: Float
    ) {
        enum class Sorte { BRANCHE, TRONC, PIED }
    }

    /**
     * La neige des crêtes d'une carte d'hiver, une couche par calque (à
     * partir du lointain), de la taille de son calque : un masque, 255 sous
     * la neige. Elle s'est accumulée sur le haut de chaque carton ;
     * [CadreOrne] la pose à part, par-dessus le carton, pour qu'une secousse
     * la fasse tomber et qu'elle se reforme peu à peu. `null` hors de
     * l'hiver ; une case `null` si le cache l'a perdue.
     */
    fun neiges(lieu: Lieu, heure: Heure, teinte: Float?, largeurPx: Int, hauteurPx: Int,
               souvenir: Souvenir? = null): Array<Bitmap?>? {
        if (largeurPx <= 0 || hauteurPx <= 0 || souvenir?.fete != null || souvenir?.saison != Saison.WANTER) return null
        val cle = cle(lieu, heure, teinte, largeurPx, hauteurPx, souvenir)
        return Array(PLANS) { CACHE.get("${cle}_n$it")?.takeIf { b -> !b.isRecycled } }
    }

    /** La couleur de la neige des crêtes, à l'heure de la carte. */
    fun couleurNeige(heure: Heure): Int = if (heure.nuit) 0xFFBECCE4.toInt() else 0xFFFAFCFF.toInt()

    /** Les brins du surcadrage de la carte, s'ils sont rendus ; `null` sinon, vide sans surcadrage. */
    fun brins(lieu: Lieu, heure: Heure, teinte: Float?, largeurPx: Int, hauteurPx: Int,
              souvenir: Souvenir? = null): Array<Brin>? {
        if (largeurPx <= 0 || hauteurPx <= 0 || souvenir?.fete != null) return null
        val cle = cle(lieu, heure, teinte, largeurPx, hauteurPx, souvenir)
        val g = gabarits(lieu, souvenir?.saison)
        val out = ArrayList<Brin>(g.size)
        for ((i, b) in g.withIndex()) {
            val image = CACHE.get("${cle}_b$i")?.takeIf { !it.isRecycled } ?: return null
            val ombre = CACHE.get("${cle}_c$i")?.takeIf { !it.isRecycled } ?: return null
            out += Brin(image, ombre, b.plan, b.boite, b.px, b.py, b.sorte, b.droite, b.phase)
        }
        return out.toTypedArray()
    }

    /** Les pixels par unité du calque [plan] d'une fenêtre de [l] × [h] pixels. */
    private fun echelleDuPlan(plan: Int, l: Int, h: Int): Float =
        cadrage((l + 2 * marge(plan, l)).toFloat(), (h + 2 * marge(plan, h)).toFloat())[0]

    /** Le pied de ce qui monte de hors champ, sous le bas de la fenêtre. */
    private const val PIED_HORS_CHAMP = HAUTEUR + 2f

    /**
     * Ce qu'il faut découper pour le surcadrage de [lieu] : chaque brin, sa
     * boîte (côté droit en miroir du gauche), son point fixe et son dessin.
     */
    private class Gabarit(
        val plan: Int, val droite: Boolean, val sorte: Brin.Sorte, val boite: RectF,
        val px: Float, val py: Float, val phase: Float, val dessin: Peintre.() -> Unit
    )

    private fun gabarits(lieu: Lieu, saison: Saison?): List<Gabarit> {
        val s = Surcadre.de(lieu) ?: return emptyList()
        val g = ArrayList<Gabarit>()
        for (droite in booleanArrayOf(false, true)) {
            val cote = if (droite) Surcadre.DROITE else Surcadre.GAUCHE
            val graine = if (droite) 2 else 1
            fun x(v: Float) = if (droite) LARGEUR - v else v
            fun boite(l: Float, t: Float, r: Float, b: Float) =
                if (droite) RectF(LARGEUR - r, t, LARGEUR - l, b) else RectF(l, t, r, b)
            val pied = s.pied
            if (pied != null && s.piedCotes and cote != 0) {
                // Le second rang, à demi-taille, planté dans la rainure du proche.
                val base = DEVANT + 0.6f
                g += Gabarit(4, droite, Brin.Sorte.PIED, boite(9f, 36.5f, 26.5f, base + 1.5f), x(15f), base,
                    graine * 1.3f + 0.9f) {
                    c.translate(12.5f, base - PIED_HORS_CHAMP * 0.5f)
                    c.scale(0.5f, 0.5f)
                    pied(pied, saison, graine + 10, lointain = true)
                }
                g += Gabarit(5, droite, Brin.Sorte.PIED, boite(-6f, 23f, 27f, PIED_HORS_CHAMP + 1f), x(5f),
                    PIED_HORS_CHAMP, graine * 1.3f) { pied(pied, saison, graine, lointain = false) }
            }
            val haut = s.haut
            if (haut != null && s.hautCotes and cote != 0) {
                val p = s.portee
                val ph = graine * 2.1f + 0.4f
                g += when (haut) {
                    Surcadre.Essence.FEUILLU -> Gabarit(5, droite, Brin.Sorte.BRANCHE, boite(-10f, -7f, p + 8f, 27f),
                        x(-4f), 2f, ph) { feuillu(saison, p, graine) }
                    Surcadre.Essence.SAULE -> Gabarit(5, droite, Brin.Sorte.BRANCHE, boite(-8f, -6f, p * 0.8f + 9f, 43f),
                        x(-4f), 1.5f, ph) { saule(saison, p, graine) }
                    Surcadre.Essence.VIGNE -> Gabarit(5, droite, Brin.Sorte.BRANCHE, boite(-8f, -7f, p + 8f, 24f),
                        x(-4f), 3f, ph) { vigne(saison, p, graine) }
                    Surcadre.Essence.BOULEAU -> Gabarit(5, droite, Brin.Sorte.TRONC,
                        boite(-6f, -6f, p + 6f, PIED_HORS_CHAMP + 1f), x(0.5f), PIED_HORS_CHAMP, ph) { bouleau(saison, p, graine) }
                }
            }
        }
        return g
    }

    /**
     * Découpe un brin dans sa propre feuille, à [k] pixels par unité : la
     * tranche de kraft, le grain, les couleurs ravivées comme les cartons,
     * et son ombre à part.
     */
    private fun rendreBrin(g: Gabarit, heure: Heure, k: Float): Pair<Bitmap, Bitmap> {
        val w = ceil(g.boite.width() * k).toInt().coerceAtLeast(1)
        val h = ceil(g.boite.height() * k).toInt().coerceAtLeast(1)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.scale(k, k)
        c.translate(-g.boite.left, -g.boite.top)
        if (g.droite) { c.translate(LARGEUR, 0f); c.scale(-1f, 1f) }
        Peintre(c, heure, facture = Facture.GOUACHE, echelle = k).apply(g.dessin)
        val forme = b.extractAlpha()
        krafter(b, forme, k, heure)
        val ombre = flouter(forme, k)
        forme.recycle()
        Grain.poser(b)
        raviver(b)
        return b to ombre
    }

    /**
     * Des couleurs plus vives et plus chaudes (essai du 2026-11-14 : « on m'a
     * dit que les couleurs paraissaient un peu ternes »), de 0 (le rendu
     * d'avant) à 1 et au-delà : plus de saturation, un peu plus de contraste,
     * et la lumière tirée vers l'or, posées une fois sur chaque calque rendu.
     * Le grain du papier et le kraft pâlissent les aplats ; ce réglage leur
     * rend leur éclat sans repeindre les palettes une à une. Le propriétaire
     * a retenu le cran fort, [VIF_RETENU], sur une commune, une rare et une
     * très rare côte à côte (2026-11-14).
     */
    const val VIF_RETENU = 1.8f

    /** L'horloge des planches de relecture, en secondes ; `null` pour la vraie. */
    @Volatile internal var horlogeEssai: Float? = null

    @Volatile internal var VIF = VIF_RETENU

    private fun raviver(b: Bitmap) {
        val v = VIF
        if (v <= 0f) return
        val m = android.graphics.ColorMatrix().apply { setSaturation(1f + 0.45f * v) }
        val contraste = 1f + 0.1f * v
        val decale = (1f - contraste) * 128f
        m.postConcat(android.graphics.ColorMatrix(floatArrayOf(
            contraste * (1f + 0.07f * v), 0f, 0f, 0f, decale + 6f * v,
            0f, contraste * (1f + 0.015f * v), 0f, 0f, decale + 2f * v,
            0f, 0f, contraste * (1f - 0.09f * v), 0f, decale - 4f * v,
            0f, 0f, 0f, 1f, 0f
        )))
        val copie = b.copy(Bitmap.Config.ARGB_8888, false)
        val p = Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(m)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
        }
        Canvas(b).drawBitmap(copie, 0f, 0f, p)
        copie.recycle()
    }

    /**
     * Le papier découpé (repris de KreyolKeyb, 2026-11-13) : des feuilles de
     * papier opaques, sans coups de pinceau, avec leur grain ([Grain]), une
     * fine tranche de kraft tout autour de chaque découpe ([krafter]) et une
     * ombre portée qui se pose à chaque trame ([ombres]). Faux pour retrouver
     * le carton peint à la gouache.
     */
    internal const val PAPIER = true

    /**
     * Les ombres portées des cartons, une par calque à partir du lointain, de
     * la taille de son calque : sa silhouette, floutée. [Motif] les pose à
     * chaque trame juste derrière le carton qui les porte, mais elles glissent
     * avec le carton de derrière, sur lequel elles tombent : pencher la carte
     * fait varier l'écart entre une découpe et son ombre, comme dans une vraie
     * boîte de théâtre de papier. Avant, l'ombre était peinte une fois dans le
     * carton de derrière et restait collée au même endroit.
     *
     * `null` hors papier ou pour une fête ; une case `null` si le cache l'a perdue.
     */
    fun ombres(lieu: Lieu, heure: Heure, teinte: Float?, largeurPx: Int, hauteurPx: Int,
               souvenir: Souvenir? = null): Array<Bitmap?>? {
        if (!PAPIER || largeurPx <= 0 || hauteurPx <= 0 || souvenir?.fete != null) return null
        val cle = cle(lieu, heure, teinte, largeurPx, hauteurPx, souvenir)
        return Array(PLANS) { CACHE.get("${cle}_o$it")?.takeIf { b -> !b.isRecycled } }
    }

    /**
     * Où tombe l'ombre d'un carton, en unités de la boîte : la lumière de la
     * rampe, au bas de la scène et un peu à gauche, la porte vers le haut et
     * vers la droite (règle 4 de la skill `direction-artistique`).
     */
    const val OMBRE_PORTEE_X = 0.6f
    const val OMBRE_PORTEE_Y = -1.1f

    /** La couleur de l'ombre portée d'un carton, à l'heure de la carte. */
    fun couleurOmbrePortee(heure: Heure): Int =
        ((if (heure.nuit) 0.55f else 0.42f) * 255f).roundToInt() shl 24 or (OMBRE and 0xFFFFFF)

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

    private fun rendrePlans(lieu: Lieu, heure: Heure, l: Int, h: Int, souvenir: Souvenir?,
                            ombres: Array<Bitmap?> = arrayOfNulls(PLANS),
                            neiges: Array<Bitmap?>? = null): Array<Bitmap> {
        val n = PLANS
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
        // Avec une saison, il dresse aussi le relevé de sa végétation.
        val releves = arrayOfNulls<Bitmap>(n)
        fun calque(i: Int, sol: Sol = Sol.SANS) = toile(i) {
            val releve = if (souvenir != null && sol == Sol.SANS) {
                Bitmap.createBitmap(tailles[i][0], tailles[i][1], Bitmap.Config.ALPHA_8).also { r -> releves[i] = r }
            } else null
            dessiner(it, lieu, heure, ciel = false, lieuAussi = true, seul = i - 2, sol = sol, vivant = true,
                facture = Facture.GOUACHE, echelle = ks[i][0], releve = releve?.let { r -> aLEchelle(r, ks[i]) })
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
            val aPart = if (neiges != null) arrayOfNulls<Bitmap>(4) else null
            Habillage.appliquerPlans(arrayOf(ciel, loin, milieu, proche), arrayOf(null, loin, milieu, proche),
                lieu, heure, souvenir, arrayOf(ks[0], ks[2], ks[3], ks[4]), arrayOf(null, releves[2], releves[3], releves[4]), aPart)
            aPart?.forEachIndexed { i, b -> if (b != null) neiges!![i + 1] = b }
        }
        releves.forEach { it?.recycle() }
        // Après la saison, qui reconnaît la végétation à ses couleurs d'origine.
        eclaircir(loin, formes[0], heure.cielBas, ESTOMPE[0])
        eclaircir(milieu, formes[1], heure.cielBas, ESTOMPE[1])
        // Le surcadrage naturel n'est pas peint dans les cartons : ses brins
        // sont découpés à part ([gabarits]) pour bouger à chaque trame.
        val plans = arrayOf(ciel, nuages(heure, l, h), loin, milieu, proche)
        // Le plancher, là où un lieu en a un : il reçoit les ombres sans en
        // porter, et sans bordure, ce n'est pas un carton.
        val planchers = Array(plans.size) { i ->
            if (i in 3..4 && aUnPlancher(lieu, i - 2)) calque(i, sol = Sol.SEUL) else null
        }
        val silhouettes = arrayOfNulls<Bitmap>(plans.size)
        silhouettes[2] = formes[0]
        silhouettes[3] = formes[1]
        silhouettes[4] = formes[2]
        if (PAPIER) {
            // Le papier découpé : chaque carton a sa tranche de kraft tout
            // autour et son ombre à part, posée à chaque trame ; le plancher
            // vient dessous, sans tranche ni ombre.
            for (devant in 2 until plans.size) {
                val forme = silhouettes[devant] ?: continue
                krafter(plans[devant], forme, unite, heure)
                ombres[devant] = flouter(forme, unite)
                planchers[devant]?.let {
                    val derriere = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER) }
                    Canvas(plans[devant]).drawBitmap(it, 0f, 0f, derriere)
                    it.recycle()
                }
            }
            silhouettes.forEach { it?.recycle() }
            plans.forEach { Grain.poser(it); raviver(it) }
            return plans
        }
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
        if (PAPIER) krafter(b, forme, k, heure) else trancher(b, forme, k, heure, force = 0.6f)
        forme.recycle()
        return b
    }

    /**
     * La tranche de kraft d'un carton de papier découpé : la silhouette [forme]
     * posée derrière lui en brun, décalée tout autour, un peu plus en bas.
     * C'est ce liseré qui détache une découpe de celle de derrière, même
     * quand leurs couleurs sont voisines.
     */
    private fun krafter(b: Bitmap, forme: Bitmap, unite: Float, heure: Heure) {
        val p = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            color = Heure.melange(KRAFT, heure.pres, if (heure.nuit) 0.55f else 0.15f)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
        }
        val r = max(1f, 0.32f * unite)
        val c = Canvas(b)
        for (k in 0 until 12) {
            val a = k / 12f * 2f * PI.toFloat()
            c.drawBitmap(forme, cos(a) * r, sin(a) * r + 0.25f * r, p)
        }
    }

    /** La silhouette [forme] floutée, de la même taille : l'ombre portée d'un carton. */
    private fun flouter(forme: Bitmap, unite: Float): Bitmap {
        val p = Paint().apply { maskFilter = BlurMaskFilter(max(1f, 0.45f * unite), BlurMaskFilter.Blur.NORMAL) }
        val ou = IntArray(2)
        val flou = forme.extractAlpha(p, ou)
        val b = Bitmap.createBitmap(forme.width, forme.height, Bitmap.Config.ALPHA_8)
        Canvas(b).drawBitmap(flou, ou[0].toFloat(), ou[1].toFloat(), null)
        flou.recycle()
        return b
    }

    /** Le brun du kraft, le dos du papier. */
    private const val KRAFT = 0xFFB08A5A.toInt()

    /**
     * Le grain du papier, posé une fois sur chaque calque rendu : une tuile
     * de bruit clair et sombre très léger, et quelques fibres. Il ne touche
     * que ce que le calque porte.
     */
    private object Grain {
        private var tuile: Bitmap? = null

        @Synchronized
        private fun tuile(): Bitmap = tuile?.takeIf { !it.isRecycled } ?: run {
            val n = 160
            val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
            val r = Alea(97)
            val px = IntArray(n * n)
            for (i in px.indices) {
                val v = r()
                val a = (r() * 20f).toInt()
                px[i] = if (v < 0.5f) (a shl 24) else ((a + 4) shl 24) or 0xFFFFFF
            }
            b.setPixels(px, 0, n, 0, 0, n, n)
            val c = Canvas(b)
            val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 0.8f; style = Paint.Style.STROKE }
            repeat(70) {
                val x = r() * n; val y = r() * n; val l = 3f + r() * 9f; val a = r() * 6.3f
                f.color = if (r() < 0.6f) 0x1EFFFFFF else 0x14000000
                c.drawLine(x, y, x + cos(a) * l, y + sin(a) * l, f)
            }
            b.also { tuile = it }
        }

        fun poser(b: Bitmap) {
            val p = Paint().apply {
                shader = BitmapShader(tuile(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            }
            Canvas(b).drawRect(0f, 0f, b.width.toFloat(), b.height.toFloat(), p)
        }
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

    /**
     * Les façades du Grund au Breedewee, de toutes les couleurs, réglées pour
     * chaque heure : rose, jaune, bleu, vert, orange (maquette `vives.js`).
     */
    private val FACADES = arrayOf(
        intArrayOf(0xFFF7A8B8.toInt(), 0xFFFFD66B.toInt(), 0xFF8EC9F0.toInt(), 0xFF9BDBA8.toInt(), 0xFFFFB070.toInt()),
        intArrayOf(0xFFF59A8C.toInt(), 0xFFFFC94D.toInt(), 0xFF7FC4E8.toInt(), 0xFFA7D67A.toInt(), 0xFFFF9E5A.toInt()),
        intArrayOf(0xFFD9789A.toInt(), 0xFFE8A85A.toInt(), 0xFF8A86C8.toInt(), 0xFF7FA88C.toInt(), 0xFFE08A6A.toInt()),
        intArrayOf(0xFF4F5C9E.toInt(), 0xFF6A6AA8.toInt(), 0xFF3E5A9A.toInt(), 0xFF4A7088.toInt(), 0xFF5A5A90.toInt())
    )

    /** De combien les couleurs des photos glissent vers la palette de l'heure, du matin à la nuit. */
    private val ATTENUE = floatArrayOf(0.04f, 0.14f, 0.42f, 0.72f)

    /** Les trois chutes du Schéissendëmpel, en x. */
    private val CHUTES = floatArrayOf(37.5f, 50f, 62.5f)

    /** La grande roue de la Schueberfouer : centre x, y, rayon. */
    private val ROUE = floatArrayOf(20f, 22f, 19f)

    /** Les couleurs des nacelles de la grande roue. */
    private val GONDOLES = intArrayOf(0xFFD6453A.toInt(), 0xFFE9B02F.toInt(), 0xFF2F9C95.toInt(),
        0xFF5A6FD1.toInt(), 0xFFD4569A.toInt(), 0xFF3F9A4F.toInt())

    /**
     * Les maisons du Grund vues de trois quarts ([Peintre.rue3q]), de la plus
     * proche à la plus lointaine : largeur le long de la rue et hauteur (au
     * premier plan), mur, colombages (1), hauteur du toit, lucarnes (1).
     * Étroites et hautes, comme les maisons du quai.
     */
    private val MAISONS_GRUND_3Q = arrayOf(
        floatArrayOf(14f, 27f, 2f, 0f, 6f, 1f), floatArrayOf(10f, 22f, 1f, 1f, 5f, 0f),
        floatArrayOf(13f, 28f, 0f, 0f, 6.5f, 1f), floatArrayOf(10f, 21f, 3f, 0f, 5f, 0f),
        floatArrayOf(14f, 25f, 2f, 1f, 6f, 1f), floatArrayOf(11f, 23f, 1f, 0f, 5.5f, 0f),
        floatArrayOf(13f, 27f, 3f, 0f, 6f, 1f), floatArrayOf(11f, 22f, 0f, 1f, 5.5f, 0f),
        floatArrayOf(13f, 25f, 2f, 0f, 6f, 1f), floatArrayOf(11f, 23f, 1f, 0f, 5.5f, 0f),
        floatArrayOf(14f, 26f, 3f, 1f, 6f, 1f), floatArrayOf(12f, 24f, 0f, 0f, 5.5f, 0f),
        floatArrayOf(13f, 25f, 2f, 0f, 6f, 1f), floatArrayOf(12f, 23f, 1f, 0f, 5.5f, 0f)
    )

    /**
     * Une perspective de trois quarts ([Peintre.rue3q], [Peintre.planche3q]) :
     * la base des façades part de [proche] et fuit vers [fuite], d'ordinaire
     * hors de la fenêtre ; les pignons fuient vers [fuiteCote]. Une longueur
     * réelle `u` le long de la façade tombe à `u / (u + recul)` du chemin vers
     * le point de fuite, et tout y est réduit d'autant ; [taille] met la
     * rangée à l'échelle d'un plan lointain. [quai] : la hauteur du mur de
     * quai sous les façades, au premier plan, zéro pour une rue sans eau.
     */
    internal class Fuite(
        val proche: FloatArray,
        val fuite: FloatArray,
        val fuiteCote: FloatArray,
        recul: Float = -1f,
        val taille: Float = 1f,
        val quai: Float = 0f
    ) {
        /**
         * Par défaut, celui qui donne au premier plan une façade de la
         * largeur qu'elle aurait vue à 45° environ : sans cela, une rangée
         * lointaine s'allonge en barres basses.
         */
        val recul: Float = if (recul > 0f) recul else {
            val dx = fuite[0] - proche[0]; val dy = fuite[1] - proche[1]
            sqrt(dx * dx + dy * dy) / (0.7f * taille)
        }

        fun t(u: Float) = u / (u + recul)
        fun x(u: Float) = proche[0] + t(u) * (fuite[0] - proche[0])
        fun y(u: Float) = proche[1] + t(u) * (fuite[1] - proche[1])
        fun echelle(u: Float) = 1f - t(u)

        /** Le bord de l'eau au pied du quai, à l'abscisse [x] de la fenêtre. */
        fun eau(x: Float): Float {
            val t = ((x - proche[0]) / (fuite[0] - proche[0])).coerceAtLeast(-0.2f)
            return proche[1] + t * (fuite[1] - proche[1]) + quai * taille * (1f - t)
        }
    }

    /**
     * [n] maisons tirées au sort pour [Peintre.rue3q], dans l'ordre d'une rue
     * : largeur et hauteur autour de [largeur] et [hauteur], quatre murs.
     */
    internal fun maisonsAuHasard(n: Int, graine: Int, largeur: Float, hauteur: Float): Array<FloatArray> {
        val r = Alea(graine)
        return Array(n) {
            floatArrayOf(largeur * (0.8f + 0.4f * r()), hauteur * (0.8f + 0.4f * r()), (r() * 4f).toInt().coerceAtMost(3).toFloat(),
                if (r() < 0.25f) 1f else 0f, hauteur * (0.2f + 0.06f * r()), if (r() < 0.5f) 1f else 0f)
        }
    }

    /** La ville basse sous la Gëlle Fra : une rue qui fuit vers le pied du monument. */
    internal val RUE_GELLE_FRA = Fuite(floatArrayOf(-4f, 58f), floatArrayOf(86f, 47f), floatArrayOf(-160f, 54f), 170f, taille = 0.62f)
    private val MAISONS_GELLE_FRA = maisonsAuHasard(60, 350, 15f, 22f)

    /** Les deux rues du village d'Esch-sur-Sûre, qui fuient vers le fond de la boucle. */
    internal val RUE_ESCH_HAUT = Fuite(floatArrayOf(36f, 43.5f), floatArrayOf(150f, 33f), floatArrayOf(-120f, 40f), taille = 0.3f)
    internal val RUE_ESCH_BAS = Fuite(floatArrayOf(30f, 51.5f), floatArrayOf(150f, 33f), floatArrayOf(-120f, 47f), taille = 0.36f)
    private val MAISONS_ESCH_HAUT = maisonsAuHasard(32, 117, 12f, 18f)
    private val MAISONS_ESCH_BAS = maisonsAuHasard(36, 118, 12f, 18f)

    /** La rue du Grund, au bord de l'Alzette : elle fuit vers la droite. */
    internal val RUE_GRUND = Fuite(floatArrayOf(13f, 50.5f), floatArrayOf(178f, 34f), floatArrayOf(-140f, 40f), 150f, quai = 3f)

    /** La pente du tablier de la Rout Bréck, vu d'en bas. */
    private const val TABLIER_PENTE = 0.17f

    /** Les moutons du premier plan de Conzefenn, en x. */
    private val MOUTONS = floatArrayOf(6f, 13f, 88f, 95f)

    /** Les mâts des bannières devant les hauts fourneaux de Belval, en x. */
    private val BANNIERES = floatArrayOf(4f, 16f, 30f, 70f, 84f, 97f)

    /**
     * Ce qui vit dans un lieu, sur la carte ouverte (repris de KreyolKeyb,
     * 2026-11-13) : un élément par lieu, lent, posé à chaque trame sur le
     * carton qui le porte et qui glisse donc avec lui ; et ce que fait le lieu
     * quand on secoue le téléphone. Le temps est celui de l'horloge, en
     * secondes : rien à faire avancer, une carte qui revient reprend où en est
     * le monde.
     *
     * Sur une image figée (partage, carte imprimée), [Motif] la pose à
     * l'instant zéro : la roue, le tram, le drapeau ou la péniche y restent,
     * à leur place de la vignette.
     *
     * | Lieu | Ce qui bouge | À la secousse |
     * |---|---|---|
     * | Veianen | la brume de l'Our glisse sous le château | elle file, des oiseaux quittent les arbres |
     * | Esch-Sauer | le drapeau sur la tour | il claque |
     * | Neimënster | des choucas tournent devant les remparts | ils s'égaillent |
     * | Mëllerdall | l'eau coule des trois chutes | elles grossissent et éclaboussent |
     * | Stad | des reflets sur l'Alzette | des ronds sur l'eau |
     * | Musel | une péniche descend la Moselle | elle tangue |
     * | Schueberfouer | la grande roue tourne, les chevaux de bois montent et descendent | la roue s'emballe |
     * | Nei Bréck | le tram traverse le pont | des oiseaux quittent les arbres |
     * | Belval | les bannières ondulent | elles claquent |
     * | Bäerdref | quelques feuilles tombent sous l'arche | une pluie de feuilles |
     * | Ansebuerg | deux oiseaux planent au-dessus du château | d'autres sortent de la charmille |
     * | Pafendall | la cabine monte et descend | elle tressaute |
     * | Rout Bréck | un bus passe sur le pont | des oiseaux quittent les arbres |
     * | Gëlle Fra | un éclat d'or passe sur la statue, la nuit les lampes du pont scintillent | des oiseaux quittent les arbres |
     *
     * La nuit et le soir (rare et très rare), ce qui a des lumières les
     * allume : la roue et ses ampoules qui courent, le tram, la péniche, la
     * cabine, les reflets du Grund.
     */
    class Vie internal constructor(private val lieu: Lieu, private val heure: Heure) {

        private var peintre: Peintre? = null
        private var secousse = -100f
        private var force = 0f
        private var acquis = 0f

        /** Vrai si [calque] porte quelque chose de vivant. */
        fun vitDans(calque: Int): Boolean = when (lieu) {
            Lieu.VEIANEN, Lieu.MUSEL, Lieu.NEI_BRECK, Lieu.NEIMENSTER, Lieu.ANSEBUERG, Lieu.OURDALL,
            Lieu.HAFF_REIMECH, Lieu.PEITRUSDALL, Lieu.DUMONGSHAFF -> calque == 3 || calque == 4
            Lieu.FOUER -> calque == 3 || calque == 4
            Lieu.BREEDEWEE -> calque == 3 || calque == 4
            Lieu.GELLE_FRA -> calque == 4 || (calque == 3 && !PAPIER)
            else -> calque == 4
        }

        /** Une secousse de [f] (0 à 1) à l'instant [temps]. */
        fun secouer(f: Float, temps: Float) {
            neigeAvant = neige(temps)
            neigeApres = max(0.12f, neigeAvant - f.coerceIn(0f, 1f) * 0.9f)
            // Pendant qu'on secoue, les secousses se suivent : ce qui tombe s'additionne.
            val perte = neigeAvant - neigeApres
            chute = if (temps - secousse in 0f..2.5f) min(1f, chute + perte) else perte
            acquis += poussee(temps)
            secousse = temps
            force = f.coerceIn(0f, 1f)
        }

        /** Ce qu'une secousse pousse encore, de 0 à [force], qui retombe en quelques secondes. */
        private fun rafale(temps: Float): Float {
            val d = temps - secousse
            return if (d < 0f || d > 6f) 0f else force * exp(-d * 1.2f)
        }

        /** Le chemin parcouru grâce à la dernière secousse, qui ne revient jamais en arrière. */
        private fun poussee(temps: Float): Float {
            val d = temps - secousse
            return if (d < 0f || d > 60f) (if (d > 60f) force else 0f) else force * (1f - exp(-d * 1.2f))
        }

        private fun elan(temps: Float) = acquis + poussee(temps)

        /** Le niveau de la neige des crêtes juste avant et juste après la dernière secousse. */
        private var neigeAvant = 1f
        private var neigeApres = 1f

        /**
         * Ce qui reste de la neige accumulée sur les cartons à l'instant
         * [temps], de 1 (toute) à presque rien : une secousse en fait tomber
         * selon sa force, en une demi-seconde, puis elle se reforme en une
         * vingtaine de secondes, sous la neige qui continue de tomber.
         */
        fun neige(temps: Float): Float {
            if (temps <= 0f) return 1f
            val d = temps - secousse
            if (d < 0f) return 1f
            if (d < 0.5f) return neigeAvant + (neigeApres - neigeAvant) * (d / 0.5f)
            return min(1f, neigeApres + (d - 0.5f) / 20f)
        }

        private var chute = 0f

        /** Ce que les dernières secousses ont fait tomber de neige, de 0 à 1. */
        fun chuteDeNeige(): Float = chute

        /** Les secondes depuis la dernière secousse (négatif s'il n'y en a pas eu). */
        fun depuisSecousse(temps: Float): Float = if (temps <= 0f) -1f else temps - secousse

        /**
         * Le balancement d'un brin du surcadrage ([Brin]) à l'instant [temps],
         * environ de -1 à 1 : une brise lente au repos, à peine visible, faite
         * de deux ondes qui ne se répètent pas ; et à la secousse, un coup de
         * vent qui oscille et s'amortit en trois secondes environ. [phase]
         * décale chaque brin, pour que le vent passe de l'un à l'autre au
         * lieu de tout pousser d'un bloc. Rien sur une image figée.
         */
        fun balancement(temps: Float, phase: Float): Float {
            if (temps <= 0f) return 0f
            val brise = 0.1f * sin(temps * 0.8f + phase) + 0.06f * sin(temps * 0.31f + phase * 1.7f)
            val d = temps - secousse - phase * 0.04f
            val coup = if (d < 0f || d > 6f) 0f else force * exp(-d * 1.3f) * sin(d * 8f)
            return brise + coup
        }

        /**
         * Pose ce qui vit dans le carton [calque], sur un canevas déjà à
         * l'échelle de la boîte 100 × 66 de ce carton, à l'instant [temps]
         * (0 pour une image figée).
         */
        fun dessiner(c: Canvas, calque: Int, temps: Float) {
            val p = peintre?.also { it.c = c } ?: Peintre(c, heure).also { peintre = it }
            val raf = rafale(temps)
            val vol = temps - secousse
            when (lieu) {
                Lieu.VEIANEN -> if (calque == 3) p.brumeDeVeianen(temps * 0.5f + elan(temps) * 10f)
                    else envol(p, vol, 8f, 30f, 1f)
                Lieu.ESCH_SAUER -> {
                    p.drapeau(13f, 0.9f, temps * 3f + elan(temps) * 8f, 1f + raf * 1.6f)
                    envol(p, vol, 94f, 40f, -1f)
                }
                Lieu.NEIMENSTER -> if (calque == 3) choucas(p, temps, raf) else envol(p, vol, 90f, 50f, -1f)
                Lieu.MELLERDALL -> chutes(p, temps, raf)
                Lieu.STAD -> alzette(p, temps, vol)
                Lieu.MUSEL -> if (calque == 3) {
                    val x = ((60f + temps * 1.6f + 24f) % 150f) - 24f
                    p.peniche(x, sin(temps * 1.3f) * 0.2f * (1f + raf * 5f))
                } else envol(p, vol, 6f, 10f, 1f)
                Lieu.FOUER -> if (calque == 3) p.grandeRoue(temps * 0.12f + elan(temps) * 2f, temps)
                    else p.chevaux(temps * (1f + raf * 2f))
                Lieu.NEI_BRECK -> if (calque == 3) p.enPerspective(50f, 30f, 0.12f) {
                    val x = ((4f + temps * 5f + elan(temps) * 30f + 20f) % 140f) - 20f
                    p.tram(x)
                    sonnerie(p, x + 15f, 25.4f, raf)
                } else envol(p, vol, 8f, 30f, 1f)
                Lieu.BELVAL -> for ((k, x) in BANNIERES.withIndex()) {
                    p.banniere(x, sin(temps * 1.4f + k) * 0.4f + raf * 1.6f * sin(temps * 9f + k), k)
                }
                Lieu.BAERDREF -> feuillesQuiTombent(p, temps, raf)
                Lieu.ANSEBUERG -> if (calque == 3) {
                    for (k in 0..1) {
                        val a = temps * 0.25f + k * PI.toFloat()
                        // Une secousse les égaille au-dessus du château, puis ils reprennent leur ronde.
                        val r = 26f + raf * 18f
                        p.oiseau(50f + cos(a * (1f + raf)) * r, 6f + sin(a * 2f) * 1.6f - raf * 5f, 1.4f,
                            0.5f + 0.5f * sin(temps * (3f + raf * 12f) + k), oiseaux(p))
                    }
                } else { envol(p, vol, 20f, 40f, 1f); envol(p, vol, 80f, 42f, -1f) }
                Lieu.PAFENDALL -> p.cabine(16f + (1f - cos(temps * 0.35f)) * 0.5f * 34f + raf * sin(temps * 24f) * 0.8f)
                Lieu.ROUT_BRECK -> {
                    val x = ((6f + temps * 6f + elan(temps) * 30f + 14f) % 128f) - 14f
                    p.bus(x)
                    sonnerie(p, x + 9f, p.tablier(x + 9f) - 4.6f, raf)
                    envol(p, vol, 90f, 44f, -1f)
                }
                Lieu.GELLE_FRA -> if (calque == 3) lampes(p, temps) else {
                    eclat(p, temps)
                    pluieDOr(p, temps, raf)
                    envol(p, vol, 10f, 52f, 1f)
                }
                Lieu.BREEDEWEE -> if (calque == 3) lanterneVivante(p, temps, 81f, 36f, false, 1)
                    else { lanterneVivante(p, temps, 30f, 31f, true, 0); chat(p, temps, vol) }
                Lieu.FELSMILLEN -> { feuillesQuiTombent(p, temps, raf); envol(p, vol, 12f, 30f, 1f) }
                Lieu.OURDALL -> if (calque == 3) {
                    reflets(p, temps, 0f, 62f, 37f, 50f, raf)
                    ronds(p, vol, 43f, 12f, 34f, 52f)
                } else envol(p, vol, 90f, 10f, -1f)
                Lieu.STAUSEI -> {
                    reflets(p, temps, 0f, 72f, 48f, 57f, raf)
                    ronds(p, vol, 52f, 20f, 44f, 62f)
                    envol(p, vol, 96f, 36f, -1f)
                }
                Lieu.CONZEFENN -> for ((k, mx) in MOUTONS.withIndex()) {
                    val leve = raf > 0.1f
                    val saut = raf * 1.2f * abs(sin(temps * 12f + k))
                    p.mouton(mx, 47f + (k % 2) * 3f - saut, 1f,
                        if (leve) 0f else 0.5f + 0.5f * sin(temps * 0.8f + k * 1.7f), k % 3 != 0)
                }
                Lieu.HAFF_REIMECH -> if (calque == 3) {
                    reflets(p, temps, 0f, 100f, 48f, 60f, raf)
                    ronds(p, vol, 54f, 24f, 50f, 76f)
                } else envol(p, vol, 4f, 30f, 1f)
                Lieu.HOHLLAY -> {
                    // Une secousse fait tomber une pluie de gouttes de la voûte, et
                    // sortir les chauves-souris de la grotte.
                    goutte(p, temps, 50f, 12f, 54f); goutte(p, temps + 1.3f, 70f, 13f, 55f)
                    for (k in 0 until (raf * 8f).toInt()) goutte(p, temps * 1.7f + k * 0.37f, 30f + (k * 23 % 46), 12f, 55f)
                    chauvesSouris(p, vol, 50f, 26f)
                }
                Lieu.NAPOLEONSGAART -> { balise(p, temps, raf); envol(p, vol, 100f, 44f, -1f) }
                Lieu.KALLEKTUFFQUELL -> {
                    // La source déborde : les gouttes se pressent et se multiplient.
                    val n = 5 + (raf * 9f).toInt()
                    for (k in 0 until n) {
                        goutte(p, temps * (1f + raf * 1.5f) + k * 0.7f, 12f + (k * 17 % 76), 26f, 40f, rond = true)
                    }
                    ronds(p, vol, 41f, 22f, 48f, 70f)
                }
                Lieu.PEITRUSDALL -> if (calque == 3) {
                    val x = ((54f + temps * 5f + elan(temps) * 30f + 20f) % 140f) - 20f
                    p.tram(x, 2.2f)
                    sonnerie(p, x + 15f, 2.2f, raf)
                } else envol(p, vol, 6f, 30f, 1f)
                Lieu.WOLLEFSSCHLUCHT -> { feuillesQuiTombent(p, temps, raf); envol(p, vol, 50f, 10f, 1f) }
                Lieu.PRIEDEGTSTULL -> { feuillesQuiTombent(p, temps, raf); envol(p, vol, 20f, 20f, 1f) }
                Lieu.DUMONGSHAFF -> if (calque == 3) canards(p, temps, raf) else envol(p, vol, 94f, 20f, -1f)
            }
        }

        /** Des reflets qui jouent sur une eau, de [x0] à [x1], entre [y0] et [y1]. */
        private fun reflets(p: Peintre, temps: Float, x0: Float, x1: Float, y0: Float, y1: Float, raf: Float = 0f) {
            val clair = p.mix(heure.eau, 0xFFFFFFFF.toInt(), if (heure.nuit) 0.2f else 0.5f)
            val n = ((x1 - x0) / 10f).toInt().coerceAtLeast(3)
            for (k in 0 until n) {
                // Une secousse ride l'eau : les reflets dansent plus vite et plus loin.
                val x = x0 + (k + 0.5f) * (x1 - x0) / n + sin(temps * (0.4f + raf * 5f) + k * 1.3f) * (2f + raf * 3f)
                val y = y0 + ((k * 7) % 5) / 4f * (y1 - y0)
                val o = 0.3f + 0.35f * sin(temps * (1.1f + raf * 6f) + k * 2.3f) + raf * 0.3f
                p.rect(x - 1.5f, y, 3f + (k % 2) * 2f, 0.3f, clair, o.coerceAtLeast(0f))
            }
        }

        /**
         * Une goutte qui tombe de ([x], [haut]) jusqu'à [bas] toutes les trois
         * secondes, et le rond qu'elle fait en arrivant si [rond].
         */
        private fun goutte(p: Peintre, temps: Float, x: Float, haut: Float, bas: Float, rond: Boolean = false) {
            val u = (temps % 3f) / 3f
            val eau = if (heure.nuit) 0xFFB8C8E8.toInt() else 0xFFE8F4FF.toInt()
            if (u < 0.5f) {
                val y = haut + (bas - haut) * (u / 0.5f).pow(2)
                p.rond(x, y, 0.35f, eau, 0.9f)
            } else if (rond) {
                val d = (u - 0.5f) / 0.5f
                p.anneau(x, bas, 0.4f + d * 2.6f, p.mix(eau, heure.eau, d), 0.2f)
            }
        }

        /** Le feu rouge de l'antenne de Napoleonsgaart, qui clignote, plus vif le soir et la nuit. */
        private fun balise(p: Peintre, temps: Float, raf: Float = 0f) {
            // Une secousse l'affole : elle clignote vite le temps que ça se calme.
            val periode = if (raf > 0.08f) 0.3f else 1.6f
            if ((temps % periode) > periode * 0.32f && temps > 0f) return
            val rouge = 0xFFFF3B30.toInt()
            if (heure.allume) p.rond(86f, 0.2f, 1.4f, rouge, 0.3f)
            p.rond(86f, 0.2f, 0.5f, rouge, 1f)
        }

        /** Deux canards qui glissent sur l'étang de Dumongshaff ; une secousse les fait filer. */
        private fun canards(p: Peintre, temps: Float, raf: Float) {
            val brun = p.duJour(0xFF7A5A3A.toInt())
            val vert = p.duJour(0xFF2F6A4A.toInt())
            for (k in 0..1) {
                val x = 4f + ((temps * (0.8f + raf * 4f) + k * 9f) % 30f)
                val y = 50f + k * 3f + sin(temps * 1.5f + k) * 0.2f
                p.ovalePapier(x, y, 1.6f, 0.7f, brun, 0f)
                p.rond(x + 1.3f, y - 0.9f, 0.55f, vert)
            }
        }

        /**
         * Des ronds qui s'élargissent sur l'eau, à la hauteur [y], un par
         * abscisse de [xs], l'un après l'autre après la secousse ; aplatis,
         * l'eau étant vue de trois quarts.
         */
        private fun ronds(p: Peintre, vol: Float, y: Float, vararg xs: Float) {
            if (vol < 0f || vol > 3f) return
            val clair = p.mix(heure.eau, 0xFFFFFFFF.toInt(), if (heure.nuit) 0.25f else 0.55f)
            for ((k, x) in xs.withIndex()) {
                val d = vol - k * 0.35f
                if (d < 0f) continue
                val yy = y + (k % 2) * 1.5f
                p.c.save()
                p.c.scale(1f, 0.38f, x, yy)
                p.anneau(x, yy, 0.6f + d * 2.6f, p.mix(clair, heure.eau, (d / 2.6f).coerceIn(0f, 1f)), 0.45f)
                p.c.restore()
            }
        }

        /**
         * Le tram ou le bus qui sonne en filant, l'avant en ([x], [y]) : son
         * phare s'allume et deux traits de son en partent, le temps que la
         * secousse retombe.
         */
        private fun sonnerie(p: Peintre, x: Float, y: Float, raf: Float) {
            if (raf < 0.06f) return
            val o = (raf * 1.4f).coerceAtMost(1f)
            p.rond(x, y + 2f, 1.6f, 0xFFFFF4C8.toInt(), 0.35f * o)
            p.rond(x, y + 2f, 0.5f, 0xFFFFF4C8.toInt(), o)
            val col = if (heure.nuit) 0xFFFFF4C8.toInt() else p.mix(heure.pres, 0xFF000000.toInt(), 0.45f)
            for (k in 0..1) {
                val r = 1.6f + k * 1.2f
                p.trait("M${x + r * 0.7f} ${y - r * 0.7f}Q${x + r * 1.1f} ${y + 0.2f} ${x + r * 0.7f} ${y + r * 0.9f}", col, 0.25f, o)
            }
        }

        /** Une pluie d'éclats d'or le long de la Gëlle Fra, à la secousse. */
        private fun pluieDOr(p: Peintre, temps: Float, raf: Float) {
            if (raf < 0.05f) return
            val x0 = if (PAPIER) 83f else 82f
            for (k in 0 until 7) {
                val o = (raf * (0.5f + 0.5f * sin(temps * 9f + k * 1.7f))).coerceIn(0f, 1f)
                if (o < 0.05f) continue
                val x = x0 + sin(k * 2.1f) * 3f
                val y = 6f + k * 4.2f
                val r = 0.8f + 0.8f * o
                p.ligne(x - r, y, x + r, y, 0xFFFFF8D8.toInt(), 0.25f, o)
                p.ligne(x, y - r, x, y + r, 0xFFFFF8D8.toInt(), 0.25f, o)
                p.rond(x, y, 0.35f, 0xFFFFFFFF.toInt(), o)
            }
        }

        /**
         * Une lanterne du Breedewee : sa flamme vacille sans cesse, de jour
         * comme de nuit (plus discrète le jour), et une secousse la fait
         * tanguer au bout de sa potence, puis elle se calme en quelques
         * secondes.
         */
        private fun lanterneVivante(p: Peintre, temps: Float, x: Float, y: Float, versDroite: Boolean, k: Int) {
            val flamme = if (temps <= 0f) 0.5f else
                (0.55f + 0.3f * sin(temps * 13f + k * 2f) + 0.15f * sin(temps * 7.3f + k) + 0.1f * sin(temps * 23f)).coerceIn(0f, 1f)
            val d = temps - secousse - k * 0.08f
            val tangue = if (d < 0f || d > 7f) 0f else 22f * force * exp(-d * 0.9f) * sin(d * 5.5f)
            val brise = if (temps <= 0f) 0f else 1.2f * sin(temps * 0.9f + k)
            p.lanterne(x, y, versDroite, tangue + brise, flamme)
        }

        /**
         * Le chat du Breedewee : toutes les vingt secondes il traverse la
         * ruelle au petit trot, de gauche à droite ; une secousse le fait
         * détaler dans l'autre sens, ventre à terre.
         */
        private fun chat(p: Peintre, temps: Float, vol: Float) {
            if (temps <= 0f) return
            val sol = DEVANT - 0.4f
            if (vol in 0f..2.2f) {
                val x = 108f - vol * 60f
                p.chat(x, sol, versDroite = false, foulee = vol * 22f, galop = 1f)
                return
            }
            // Effrayé, il ne revient pas tout de suite.
            if (vol in 0f..12f) return
            val u = (temps + 18f) % 22f
            if (u > 7f) return
            p.chat(-8f + u / 7f * 116f, sol, versDroite = true, foulee = u * 11f, galop = 0f)
        }

        /** Les chauves-souris qui sortent de la grotte à la secousse, en zigzag. */
        private fun chauvesSouris(p: Peintre, vol: Float, x: Float, y: Float) {
            if (vol < 0f || vol > 4f) return
            val col = if (heure.nuit) 0xFF0E0A14.toInt() else 0xFF2A2230.toInt()
            for (k in 0 until 5) {
                val d = vol - k * 0.15f
                if (d < 0f) continue
                val sens = if (k % 2 == 0) 1f else -1f
                val bx = x + sens * (d * 8f + k) + sin(d * 7f + k) * 1.5f
                val by = y - d * 6f + cos(d * 9f + k) * 1.2f
                p.oiseau(bx, by, 1.1f, 0.5f + 0.5f * sin(vol * 30f + k), col)
            }
        }

        private fun oiseaux(p: Peintre) = if (heure.nuit) 0xCCFFFFFF.toInt() else p.mix(heure.pres, 0xFF000000.toInt(), 0.4f)

        /**
         * La volée qui quitte les arbres en ([x], [y]) [vol] secondes après
         * la secousse, vers la droite ([sens] 1) ou la gauche, et qui
         * s'éloigne en montant avant de disparaître.
         */
        private fun envol(p: Peintre, vol: Float, x: Float, y: Float, sens: Float) {
            if (vol < 0f || vol > 5f) return
            val col = oiseaux(p)
            for (k in 0 until 4) {
                val d = vol - k * 0.12f
                if (d < 0f) continue
                val bx = x + sens * (d * 9f + k * 1.6f)
                val by = y - d * 7f - k * 1.2f + sin(d * 3f + k) * 0.6f
                p.oiseau(bx, by, 1.2f - k * 0.1f, 0.5f + 0.5f * sin(vol * 14f + k * 1.7f), col)
            }
        }

        /** Les choucas qui tournent devant les remparts du Bock ; une secousse les égaille. */
        private fun choucas(p: Peintre, temps: Float, raf: Float) {
            val col = oiseaux(p)
            for (k in 0 until 3) {
                val a = temps * 0.5f + k * 2.1f
                val r = 6f + k * 1.2f + raf * 14f
                p.oiseau(84f + cos(a) * r, 18f + sin(a) * r * 0.45f - raf * 6f, 1.1f, 0.5f + 0.5f * sin(temps * 5f + k), col)
            }
        }

        /** L'eau qui coule des trois chutes : des filets clairs qui descendent, et l'écume au pied. */
        private fun chutes(p: Peintre, temps: Float, raf: Float) {
            val blanc = if (heure.nuit) 0xFFD8E2F4.toInt() else 0xFFFFFFFF.toInt()
            for ((i, x) in CHUTES.withIndex()) {
                for (k in 0 until 4) {
                    val u = ((temps * 0.9f + k * 0.25f + i * 0.37f) % 1f)
                    val y = 39f + u * 11f
                    val dx = (k - 1.5f) * 0.5f * (0.6f + u * 0.6f)
                    p.ligne(x + dx, y, x + dx * 1.05f, y + 2.2f, blanc, 0.28f + raf * 0.3f, 0.75f)
                }
                p.ovalePapier(x, 51f, 3.6f + raf * 2.4f + sin(temps * 3f + i) * 0.3f, 1.2f + raf * 0.6f, blanc, 0f)
                if (raf > 0.05f) for (k in 0 until 5) {
                    val a = PI.toFloat() * (0.15f + 0.7f * k / 4f)
                    val d = ((temps * 2f + k * 0.2f) % 1f) * 5f * raf
                    p.rond(x + cos(a) * d * 1.4f, 50.5f - sin(a) * d, 0.3f, blanc, 0.8f)
                }
            }
        }

        /** Les reflets qui jouent sur l'Alzette ; une secousse y fait des ronds. */
        private fun alzette(p: Peintre, temps: Float, vol: Float) {
            val eau = p.mix(heure.eau, 0xFF5F7046.toInt(), if (heure.nuit) 0.25f else 0.45f)
            val clair = p.mix(eau, 0xFFFFFFFF.toInt(), if (heure.nuit) 0.2f else 0.5f)
            for (k in 0 until 9) {
                val x = 4f + k * 11f + sin(temps * 0.4f + k * 1.3f) * 2.5f
                val o = 0.35f + 0.35f * sin(temps * 1.1f + k * 2.3f)
                p.rect(x, RUE_GRUND.eau(x) + 1.6f + (k % 3) * 1.2f, 3f + (k % 2) * 2f, 0.3f, if (heure.allume && k % 2 == 0) heure.fenetre else clair, o)
            }
            if (vol in 0f..3f) for (k in 0..1) {
                val d = vol - k * 0.5f
                if (d < 0f) continue
                val ax = 30f + k * 44f
                p.anneau(ax, RUE_GRUND.eau(ax) + 2.4f, 0.6f + d * 2.2f, p.mix(clair, eau, (d / 2.5f).coerceIn(0f, 1f)), 0.25f)
            }
        }

        /** Les feuilles qui tombent sous l'arche de Berdorf : deux ou trois, une pluie à la secousse. */
        private fun feuillesQuiTombent(p: Peintre, temps: Float, raf: Float) {
            val n = 3 + (raf * 10f).toInt()
            for (k in 0 until n) {
                val u = ((temps * (0.05f + 0.01f * (k % 3)) + k * 0.137f) % 1f)
                val x = 20f + (k * 37 % 64) + sin(temps * 1.5f + k) * 2f
                val y = -2f + u * 60f
                p.rond(x, y, 0.55f, p.vert(0.3f + 0.1f * (k % 4)))
            }
        }

        /** Les lampes du pont Adolphe, derrière la Gëlle Fra, qui scintillent la nuit. */
        private fun lampes(p: Peintre, temps: Float) {
            if (!heure.allume) return
            var x = 3f
            var k = 0
            while (x < 100f) {
                val o = 0.15f + 0.25f * (0.5f + 0.5f * sin(temps * 2.3f + k * 1.9f))
                p.rond(x, 21.6f, 1.2f, heure.fenetre, o)
                x += 5f; k++
            }
        }

        /** L'éclat d'or qui passe sur la Gëlle Fra, toutes les huit secondes. */
        private fun eclat(p: Peintre, temps: Float) {
            val u = (temps % 8f) / 8f
            val o = (sin(u * PI.toFloat())).pow(8)
            if (o < 0.02f) return
            val x = if (PAPIER) 83f else 82f
            val y = if (PAPIER) 6f + u * 26f else 5f + u * 4f
            p.ligne(x - 1.6f, y, x + 1.6f, y, 0xFFFFF8D8.toInt(), 0.3f, o)
            p.ligne(x, y - 1.6f, x, y + 1.6f, 0xFFFFF8D8.toInt(), 0.3f, o)
            p.rond(x, y, 0.5f, 0xFFFFFFFF.toInt(), o)
        }

        companion object {
            /** La vie de [lieu] à l'heure dite, `null` pour un lieu sans rien qui bouge. */
            fun pour(lieu: Lieu, heure: Heure): Vie? = Vie(lieu, heure)
        }
    }

    /** Une ombre de carton, pas de suie : un bleu nuit presque noir. */
    private const val OMBRE = 0xFF0B1020.toInt()

    /**
     * Le surcadrage naturel d'un lieu (demande du propriétaire, 2026-11-14) :
     * « les branches d'arbres sur les côtés et les roseaux au premier plan
     * encadrent le pont et ajoutent de la profondeur entre le premier plan et
     * l'arrière-plan ». Des branches qui entrent par un coin du haut ([haut],
     * sur les côtés [hautCotes]), et au pied de la scène des roseaux, des
     * fougères ou des herbes ([pied], sur les côtés [piedCotes]), sur le
     * carton le plus proche de la vitre ; un second rang du pied, plus petit
     * et plus clair, est planté dans la rainure du proche.
     *
     * Chaque lieu a le sien, choisi d'après sa photo : du côté où le sujet ne
     * se tient pas (la tour d'Esch à gauche, la branche à droite), de la
     * vigne à la Moselle, des saules sur l'eau, des bouleaux et des
     * linaigrettes dans la tourbière, des fougères sous le grès. Pas de
     * surcadrage là où le lieu a déjà son cadre de scène (la voûte du Bock,
     * la charmille d'Ansembourg, la porte du Breedewee) ni là où les deux
     * bords sont pris (la roue et le carrousel de la Schueberfouer).
     */
    internal class Surcadre(
        val haut: Essence?, val hautCotes: Int,
        val pied: Pied?, val piedCotes: Int,
        /** Jusqu'où la branche avance vers le milieu, en unités : moins là où un sujet tient le haut. */
        val portee: Float = 30f
    ) {
        enum class Essence { FEUILLU, SAULE, BOULEAU, VIGNE }
        enum class Pied { ROSEAUX, FOUGERES, HERBES, LINAIGRETTES, FLEURS }

        companion object {
            const val GAUCHE = 1
            const val DROITE = 2
            const val DEUX = 3

            fun de(lieu: Lieu): Surcadre? = when (lieu) {
                Lieu.ROUT_BRECK -> Surcadre(Essence.FEUILLU, DROITE, Pied.FOUGERES, DEUX)
                Lieu.NEI_BRECK -> Surcadre(Essence.FEUILLU, DROITE, Pied.ROSEAUX, DEUX)
                Lieu.PEITRUSDALL -> Surcadre(Essence.FEUILLU, DEUX, Pied.HERBES, GAUCHE, portee = 24f)
                Lieu.PAFENDALL -> Surcadre(Essence.FEUILLU, GAUCHE, Pied.FOUGERES, GAUCHE, portee = 26f)
                Lieu.VEIANEN -> Surcadre(Essence.FEUILLU, DEUX, Pied.HERBES, DEUX, portee = 25f)
                Lieu.OURDALL -> Surcadre(Essence.SAULE, GAUCHE, Pied.ROSEAUX, GAUCHE)
                Lieu.ESCH_SAUER -> Surcadre(Essence.SAULE, DROITE, Pied.ROSEAUX, DROITE)
                Lieu.STAD -> Surcadre(Essence.SAULE, GAUCHE, Pied.ROSEAUX, DROITE)
                Lieu.HAFF_REIMECH -> Surcadre(Essence.SAULE, GAUCHE, Pied.ROSEAUX, DEUX)
                Lieu.DUMONGSHAFF -> Surcadre(Essence.SAULE, DROITE, Pied.ROSEAUX, DEUX)
                Lieu.STAUSEI -> Surcadre(Essence.FEUILLU, GAUCHE, Pied.ROSEAUX, GAUCHE)
                Lieu.MUSEL -> Surcadre(Essence.VIGNE, DROITE, null, 0, portee = 32f)
                Lieu.GELLE_FRA -> Surcadre(Essence.FEUILLU, GAUCHE, Pied.FLEURS, GAUCHE, portee = 28f)
                Lieu.BELVAL -> Surcadre(Essence.BOULEAU, DROITE, Pied.HERBES, DEUX, portee = 22f)
                Lieu.CONZEFENN -> Surcadre(Essence.BOULEAU, GAUCHE, Pied.LINAIGRETTES, DEUX)
                Lieu.NAPOLEONSGAART -> Surcadre(null, 0, Pied.HERBES, DEUX)
                Lieu.MELLERDALL -> Surcadre(Essence.FEUILLU, DEUX, Pied.FOUGERES, DEUX, portee = 24f)
                Lieu.FELSMILLEN -> Surcadre(Essence.FEUILLU, DROITE, Pied.FOUGERES, GAUCHE)
                Lieu.PRIEDEGTSTULL -> Surcadre(Essence.FEUILLU, GAUCHE, Pied.FOUGERES, DROITE, portee = 26f)
                Lieu.KALLEKTUFFQUELL, Lieu.WOLLEFSSCHLUCHT, Lieu.HOHLLAY, Lieu.BAERDREF ->
                    Surcadre(null, 0, Pied.FOUGERES, DEUX)
                Lieu.NEIMENSTER, Lieu.ANSEBUERG, Lieu.BREEDEWEE, Lieu.FOUER -> null
            }
        }
    }

    /** Le blanc cassé du papier, vers lequel pâlit le ciel. */
    private const val CREME = 0xFFFCF5EC.toInt()

    /** Ce qui n'est pas de la végétation efface le relevé : voir [Peintre.relever]. */
    private val EFFACER = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

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
                 seul: Int = -1, sol: Sol = Sol.AVEC, facture: Facture = Facture.VIGNETTE, echelle: Float = 1f,
                 releve: Canvas? = null, vivant: Boolean = false) {
        val p = Peintre(c, heure, seul, sol, facture, echelle, releve, vivant)
        if (ciel) { if (PAPIER) p.cielPapier() else if (facture == Facture.GOUACHE) p.cielPeint() else p.ciel() }
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
            Lieu.PAFENDALL -> p.pafendall()
            Lieu.ANSEBUERG -> p.ansebuerg()
            Lieu.BELVAL -> p.belval()
            Lieu.BAERDREF -> p.baerdref()
            Lieu.NEI_BRECK -> p.neiBreck()
            Lieu.FELSMILLEN -> p.felsmillen()
            Lieu.OURDALL -> p.ourdall()
            Lieu.STAUSEI -> p.stausei()
            Lieu.CONZEFENN -> p.conzefenn()
            Lieu.HAFF_REIMECH -> p.haffReimech()
            Lieu.HOHLLAY -> p.hohllay()
            Lieu.NAPOLEONSGAART -> p.napoleonsgaart()
            Lieu.KALLEKTUFFQUELL -> p.kallektuffquell()
            Lieu.PEITRUSDALL -> p.peitrusdall()
            Lieu.WOLLEFSSCHLUCHT -> p.wollefsschlucht()
            Lieu.PRIEDEGTSTULL -> p.priedegtstull()
            Lieu.DUMONGSHAFF -> p.dumongshaff()
        }
    }

    /** Un canevas sur [b], à l'échelle [k] d'un [cadrage]. */
    private fun aLEchelle(b: Bitmap, k: FloatArray) = Canvas(b).apply { translate(k[1], k[2]); scale(k[0], k[0]) }

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
     * La rainure du devant de la scène, où le carton du proche de chaque
     * lieu se pose sur le plancher. Le bandeau du nom cache le bas de la
     * fenêtre ouverte, sous 62 unités environ : plus bas, le plancher ne se
     * verrait que dans la vignette.
     */
    private const val DEVANT = 58.5f

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
        var c: Canvas, val P: Heure, private val seul: Int = -1, private val sol: Sol = Sol.AVEC,
        private val facture: Facture = Facture.VIGNETTE,
        /** Les pixels par unité du canevas : la finesse des coups de pinceau. */
        private val echelle: Float = 1f,
        /**
         * Le relevé de la végétation, à la même échelle que [c] : chaque
         * pièce y est posée pleine si sa couleur, avant la gouache, est de la
         * végétation ([Habillage.vegetal]), et l'efface sinon, si bien que la
         * pièce de devant décide. [Habillage] le lit au lieu de deviner pixel
         * par pixel sur le carton peint. Les pièces peintes dans [mineral]
         * n'en sont jamais.
         */
        private val releve: Canvas? = null,
        /**
         * Vrai pour les cartons de la carte ouverte : ce qui bouge seul
         * ([Vie]) n'y est pas peint, [Vie] le pose à chaque trame. Faux pour
         * la vignette et le paysage à plat, qui le gardent immobile.
         */
        val vivant: Boolean = false
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val BAS = HAUTEUR

        private val releveP = Paint(Paint.ANTI_ALIAS_FLAG)
        private val vegetaux = HashMap<Int, Boolean>()
        private var mineraux = 0

        /**
         * Peint [dessin] en pierre, bois, enduit ou métal : quelle que soit sa
         * couleur, la saison n'y verra jamais de végétation. Le soir et la
         * nuit, les palettes rapprochent la pierre des collines (le rocher du
         * Bock, le grès du Mëllerdall, les façades bleues du Breedewee la
         * nuit), et le printemps les aurait fleuries.
         */
        fun mineral(dessin: () -> Unit) {
            mineraux++
            try { dessin() } finally { mineraux-- }
        }

        /** Pose sur le [releve] une pièce de couleur [col], tracée par [trace]. */
        private fun relever(col: Int, o: Float, trace: (Canvas, Paint) -> Unit) {
            val r = releve ?: return
            if (o < 0.95f || !actif) return
            val vegetal = mineraux == 0 && vegetaux.getOrPut(col) { Habillage.vegetal(col, P) }
            releveP.style = Paint.Style.FILL
            releveP.xfermode = if (vegetal) null else EFFACER
            releveP.color = 0xFFFFFFFF.toInt()
            trace(r, releveP)
        }

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
            relever(col, o) { r, rp -> r.drawRect(x, y, x + w, y + h, rp) }
        }

        fun rond(cx: Float, cy: Float, r: Float, col: Int, o: Float = 1f) {
            if (!actif) return
            p.style = Paint.Style.FILL; teinte(col, o)
            c.drawCircle(cx, cy, r, p)
            relever(col, o) { rc, rp -> rc.drawCircle(cx, cy, r, rp) }
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
            relever(col, o) { r, rp -> r.drawPath(path, rp) }
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
            relever(col, 1f) { r, rp -> r.drawPath(chemin, rp) }
            val plein = facture == Facture.GOUACHE
            if (PAPIER) {
                // Le papier découpé : une feuille opaque, son ombre douce que
                // la rampe porte vers le haut, et dessous une fine tranche de
                // kraft qui dépasse en bas à droite. Ni pinceau ni bord sec.
                gouache.shader = null
                gouache.style = Paint.Style.FILL
                if (plein && ombre > 0f) {
                    ombrerPapier(chemin, ombre)
                    c.save()
                    c.translate(0.15f, 0.32f)
                    gouache.color = kraft
                    c.drawPath(chemin, gouache)
                    c.restore()
                }
                gouache.color = col
                c.drawPath(chemin, gouache)
                return
            }
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

        /**
         * L'ombre d'une feuille de papier sur ce qui est derrière elle dans le
         * même calque : à peine décalée vers le haut (la rampe), douce, dense
         * contre le bord découpé.
         */
        @Suppress("DEPRECATION")
        private fun ombrerPapier(chemin: Path, force: Float) {
            val enPixels = Path(chemin)
            enPixels.transform(c.matrix)
            enPixels.offset(0.12f * echelle, -0.3f * echelle)
            ombreCarton.maskFilter = BlurMaskFilter(max(0.5f, 0.5f * echelle), BlurMaskFilter.Blur.NORMAL)
            ombreCarton.color = OMBRE
            ombreCarton.alpha = ((if (P.nuit) 0.55f else 0.4f) * force * 255f).roundToInt().coerceAtMost(255)
            c.save()
            c.setMatrix(versPixels)
            c.drawPath(enPixels, ombreCarton)
            c.restore()
        }

        /** Une pièce de papier découpé, quelle que soit sa taille : une feuille, une lanière. */
        fun piece(t: Path, col: Int, ombre: Float = 1f) {
            if (!actif) return
            t.computeBounds(boite, true)
            peint(t, col, 0f, boite, ombre)
        }

        /** Une pièce de papier écrite comme en SVG. */
        fun pieceD(d: String, col: Int, ombre: Float = 1f) = piece(chemin(d), col, ombre)

        private fun disque(cx: Float, cy: Float, r: Float) = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }

        /**
         * Le ciel en papier découpé (repris de KreyolKeyb) : un fond pâle,
         * puis des bandes de papier aux bords ondulés, de plus en plus
         * soutenues vers le haut, et l'astre en deux disques collés. La nuit,
         * des étoiles piquées dans le papier.
         */
        fun cielPapier() {
            val fond = if (P.nuit) P.cielBas else mix(P.cielBas, CREME, 0.3f)
            p.shader = null
            p.style = Paint.Style.FILL
            teinte(fond, 1f)
            if (actif) c.drawRect(-2f, -2f, LARGEUR + 2f, BAS + 2f, p)
            val bords = floatArrayOf(34f, 24f, 15f, 7f)
            for ((i, y) in bords.withIndex()) {
                val t = i / 3f
                val haut = mix(P.cielBas, P.cielMilieu ?: P.cielBas, 0.5f)
                val col = mix(mix(haut, P.cielHaut, 0.3f + 0.7f * t), CREME, if (P.nuit) 0.04f else 0.22f - 0.18f * t)
                val ph = Alea(40 + i)() * 6f
                val bande = Path()
                bande.moveTo(-2f, -2f)
                bande.lineTo(LARGEUR + 2f, -2f)
                var x = LARGEUR + 2f
                while (x >= -2f) { bande.lineTo(x, y + sin(x * 0.09f + ph) * 1.6f + sin(x * 0.23f + ph) * 0.6f); x -= 2f }
                bande.close()
                piece(bande, col, 0.6f)
            }
            if (P.nuit) {
                val r = Alea(41)
                for (k in 0 until 40) {
                    val x = r() * 100f
                    val y = r() * 36f
                    val t = 0.16f + r() * 0.3f
                    rond(x, y, t, 0xFFFFF8E0.toInt(), if (k % 5 == 0) 1f else 0.4f + r() * 0.5f)
                }
                piece(disque(P.astreX, P.astreY, P.astreR * 1.8f), mix(P.cielHaut, P.astre, 0.22f), 0.5f)
                piece(disque(P.astreX, P.astreY, P.astreR), P.astre, 0.6f)
                piece(disque(P.astreX + 1.9f, P.astreY - 1.1f, P.astreR * 0.92f), mix(P.cielHaut, P.astre, 0.22f), 0.6f)
            } else {
                piece(disque(P.astreX, P.astreY, P.astreR * 1.9f), mix(P.astre, CREME, 0.55f), 0.5f)
                piece(disque(P.astreX, P.astreY, P.astreR), P.astre, 0.6f)
            }
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
            val t = chemin(d)
            c.drawPath(t, p)
            p.style = Paint.Style.FILL
            relever(col, o) { r, rp -> rp.style = Paint.Style.STROKE; rp.strokeWidth = largeur; r.drawPath(t, rp) }
        }

        fun ligne(x1: Float, y1: Float, x2: Float, y2: Float, col: Int, largeur: Float, o: Float = 1f) {
            if (!actif) return
            p.style = Paint.Style.STROKE; p.strokeWidth = largeur; teinte(col, o)
            c.drawLine(x1, y1, x2, y2, p)
            p.style = Paint.Style.FILL
            relever(col, o) { r, rp -> rp.style = Paint.Style.STROKE; rp.strokeWidth = largeur; r.drawLine(x1, y1, x2, y2, rp) }
        }

        fun anneau(cx: Float, cy: Float, r: Float, col: Int, largeur: Float) {
            if (!actif) return
            p.style = Paint.Style.STROKE; p.strokeWidth = largeur; teinte(col, 1f)
            c.drawCircle(cx, cy, r, p)
            p.style = Paint.Style.FILL
            relever(col, 1f) { rc, rp -> rp.style = Paint.Style.STROKE; rp.strokeWidth = largeur; rc.drawCircle(cx, cy, r, rp) }
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

        fun fenetre(x: Float, y: Float, w: Float, h: Float, rnd: Alea?) = mineral {
            val allumee = P.allume && (rnd == null || rnd() < 0.7f)
            if (allumee) rect(x - w * 0.6f, y - h * 0.4f, w * 2.2f, h * 1.8f, P.fenetre, 0.22f)
            rect(x, y, w, h, if (allumee) P.fenetre else mix(P.pierre, P.pres, 0.55f))
        }

        fun maison(x: Float, y: Float, w: Float, h: Float, rnd: Alea?, toit: Float? = null) = mineral {
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

        // ------------------------------------------------- le surcadrage naturel
        //
        // Les branches et le pied de la scène ([Surcadre]), dessinés pour le
        // côté gauche : la droite est leur miroir. Chaque élément est fait de
        // quelques feuilles de papier seulement (toutes les feuilles d'un même
        // ton découpées d'un seul tenant), pour ne pas multiplier les ombres
        // floues à peindre. Les tracés tournent tous dans le même sens, si
        // bien que leurs morceaux se réunissent au lieu de se trouer.

        private val PIED = PIED_HORS_CHAMP

        fun pied(pied: Surcadre.Pied, saison: Saison?, graine: Int, lointain: Boolean) = when (pied) {
            Surcadre.Pied.ROSEAUX -> roseaux(saison, graine, lointain)
            Surcadre.Pied.FOUGERES -> fougeres(saison, graine, lointain)
            Surcadre.Pied.HERBES -> herbes(saison, graine, lointain, 0)
            Surcadre.Pied.FLEURS -> herbes(saison, graine, lointain, 1)
            Surcadre.Pied.LINAIGRETTES -> herbes(saison, graine, lointain, 2)
        }

        /** Le point de la courbe (x0, y0)–(cx, cy)–(x1, y1) à [t] dans [o][0..1], sa tangente dans [o][2..3]. */
        private fun bezier(t: Float, x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float, o: FloatArray) {
            val u = 1f - t
            o[0] = u * u * x0 + 2f * u * t * cx + t * t * x1
            o[1] = u * u * y0 + 2f * u * t * cy + t * t * y1
            o[2] = 2f * u * (cx - x0) + 2f * t * (x1 - cx)
            o[3] = 2f * u * (cy - y0) + 2f * t * (y1 - cy)
        }

        /** Un ruban effilé le long de la courbe (x0, y0)–(cx, cy)–(x1, y1), large de [l0] au départ et de [l1] au bout. */
        private fun ruban(x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float, l0: Float, l1: Float): Path {
            val n = 16
            val o = FloatArray(4)
            val gx = FloatArray(n + 1); val gy = FloatArray(n + 1)
            val dx = FloatArray(n + 1); val dy = FloatArray(n + 1)
            for (i in 0..n) {
                val t = i / n.toFloat()
                bezier(t, x0, y0, cx, cy, x1, y1, o)
                val long = max(1e-4f, sqrt(o[2] * o[2] + o[3] * o[3]))
                val w = (l0 + (l1 - l0) * t) / 2f
                val nx = -o[3] / long * w; val ny = o[2] / long * w
                gx[i] = o[0] + nx; gy[i] = o[1] + ny; dx[i] = o[0] - nx; dy[i] = o[1] - ny
            }
            val t = Path()
            t.moveTo(gx[0], gy[0])
            for (i in 1..n) t.lineTo(gx[i], gy[i])
            for (i in n downTo 0) t.lineTo(dx[i], dy[i])
            t.close()
            return t
        }

        /** Une feuille en amande attachée en ([x], [y]), longue de [l], large de 2 × [w], tournée de [a]. */
        private fun amande(x: Float, y: Float, l: Float, w: Float, a: Float): Path {
            val ca = cos(a); val sa = sin(a)
            val mx = x + l * 0.5f * ca; val my = y + l * 0.5f * sa
            val nx = -sa * w * 2f; val ny = ca * w * 2f
            return Path().apply {
                moveTo(x, y)
                quadTo(mx + nx, my + ny, x + l * ca, y + l * sa)
                quadTo(mx - nx, my - ny, x, y)
                close()
            }
        }

        /** Une feuille de vigne à cinq lobes, de rayon [rr], la queue vers [a0]. */
        private fun pampre(cx: Float, cy: Float, rr: Float, a0: Float): Path {
            val t = Path()
            val n = 48
            for (i in 0..n) {
                val th = 0.3f + (2f * PI.toFloat() - 0.6f) * i / n
                val rad = rr * (0.58f + 0.42f * abs(cos(2.5f * (th - PI.toFloat()))))
                val a = a0 - th
                if (i == 0) t.moveTo(cx + rad * cos(a), cy + rad * sin(a)) else t.lineTo(cx + rad * cos(a), cy + rad * sin(a))
            }
            t.lineTo(cx, cy)
            t.close()
            return t
        }

        private fun noir(col: Int, t: Float) = mix(col, 0xFF000000.toInt(), t)

        /** Le bois des branches, à contre-jour. */
        private fun bois(): Int = sombre(duJour(0xFF6B4B33.toInt()), 0.3f)

        /**
         * Les deux tons des feuilles d'une branche selon la saison où la carte
         * a été gagnée, un peu à contre-jour : c'est le plus proche de la vitre.
         * `null` l'hiver, où les branches sont nues sous la neige.
         */
        private fun feuilles(saison: Saison?): IntArray? {
            val t = when (saison) {
                Saison.WANTER -> return null
                Saison.HIERSCHT -> intArrayOf(duJour(0xFFB4501E.toInt()), duJour(0xFFE39A2E.toInt()))
                Saison.FREIJOER -> intArrayOf(vert(0.35f), mix(vert(0.75f), 0xFFC2E070.toInt(), 0.4f))
                else -> intArrayOf(vert(0.05f), vert(0.32f))
            }
            val o = if (P.nuit) 0.1f else 0.16f
            return IntArray(2) { noir(t[it], o) }
        }

        /** Les tons du pied de la scène : sombre, clair, sec. Le second rang ([lointain]) prend la couleur du milieu. */
        private fun tonsDuPied(saison: Saison?, lointain: Boolean): IntArray {
            val t = when (saison) {
                Saison.HIERSCHT -> intArrayOf(duJour(0xFF9A5A22.toInt()), duJour(0xFFD08A34.toInt()), duJour(0xFFC9A869.toInt()))
                Saison.WANTER -> intArrayOf(duJour(0xFF7A6448.toInt()), duJour(0xFFB59A72.toInt()), duJour(0xFFCDB894.toInt()))
                Saison.FREIJOER -> intArrayOf(vert(0.25f), mix(vert(0.7f), 0xFFB8DC6A.toInt(), 0.35f), duJour(0xFFC9B87A.toInt()))
                else -> intArrayOf(vert(0.1f), vert(0.42f), duJour(0xFFC9B06A.toInt()))
            }
            return IntArray(3) {
                if (lointain) mix(t[it], P.milieu, 0.28f) else noir(t[it], if (P.nuit) 0.12f else 0.2f)
            }
        }

        /** Des roseaux qui montent de hors champ : tiges, massettes, et de longues feuilles qui retombent. */
        private fun roseaux(saison: Saison?, graine: Int, lointain: Boolean) {
            val r = Alea(graine * 7 + 300)
            val (fonce, clair, sec) = tonsDuPied(saison, lointain)
            val sechees = saison == Saison.HIERSCHT || saison == Saison.WANTER
            val tige = if (sechees) sec else mix(fonce, sec, 0.35f)
            val brun = sombre(duJour(0xFF6E4527.toInt()), if (lointain) 0f else 0.15f)
            val tiges = Path(); val lames = Path(); val lames2 = Path(); val massettes = Path()
            for (i in 0 until 7) {
                val xb = -1.5f + i * 2f + r() * 0.8f
                val yt = 27f + i * 1.7f + r() * 5f
                val xt = xb + (r() - 0.25f) * 4f
                tiges.addPath(ruban(xb, PIED, xb + (xt - xb) * 0.3f, (PIED + yt) / 2f, xt, yt, 0.75f, 0.3f))
                if (i % 2 == 0) massettes.addRoundRect(xt - 0.8f, yt + 1.4f, xt + 0.8f, yt + 6.4f, 0.8f, 0.8f, Path.Direction.CCW)
            }
            for (i in 0 until 8) {
                val xb = -1f + i * 1.8f + r()
                val haut = 34f + r() * 14f
                val penche = (if (i % 3 == 2) -1f else 1f) * (3f + r() * 6f)
                (if (i % 2 == 0) lames else lames2).addPath(
                    ruban(xb, PIED, xb + penche * 0.2f, haut - 4f, xb + penche, haut + abs(penche) * 0.5f, 1.5f, 0.05f))
            }
            piece(lames2, clair, 0.8f)
            piece(tiges, tige, 0.8f)
            piece(massettes, brun, 0.8f)
            piece(lames, fonce, 0.8f)
        }

        /** Des fougères dont les frondes s'ouvrent en éventail depuis le coin. */
        private fun fougeres(saison: Saison?, graine: Int, lointain: Boolean) {
            val r = Alea(graine * 7 + 400)
            val (fonce, clair, _) = tonsDuPied(saison, lointain)
            // Le bout et le point de contrôle de chaque fronde.
            val frondes = floatArrayOf(-3f, 37f, -2f, 45f, 4f, 33f, 3f, 41f, 13f, 40f, 8f, 39f, 19f, 51f, 12f, 45f)
            val o = FloatArray(4)
            for (k in 0 until 4) {
                val x0 = 3f + r() * 1.5f
                val xt = frondes[k * 4] + (r() - 0.5f) * 2f; val yt = frondes[k * 4 + 1] + (r() - 0.5f) * 2f
                val cx = frondes[k * 4 + 2]; val cy = frondes[k * 4 + 3]
                val f = ruban(x0, PIED, cx, cy, xt, yt, 0.7f, 0.15f)
                var t = 0.24f
                while (t < 0.97f) {
                    bezier(t, x0, PIED, cx, cy, xt, yt, o)
                    val a = atan2(o[3], o[2])
                    val l = 0.8f + 3.6f * (1f - t)
                    val w = 0.38f + 0.2f * (1f - t)
                    f.addPath(amande(o[0], o[1], l, w, a - 1.15f))
                    f.addPath(amande(o[0], o[1], l, w, a + 1.15f))
                    t += 0.07f
                }
                piece(f, if (k % 2 == 0) fonce else clair, 0.8f)
            }
        }

        /**
         * De hautes herbes, avec des épis ([fleurs] 0), des fleurs des champs
         * (1) ou les houppes blanches des linaigrettes de la tourbière (2).
         */
        private fun herbes(saison: Saison?, graine: Int, lointain: Boolean, fleurs: Int) {
            val r = Alea(graine * 7 + 500)
            val (fonce, clair, sec) = tonsDuPied(saison, lointain)
            val g = Array(3) { Path() }
            val tiges = Path(); val tetes = Path(); val tetes2 = Path()
            val hMin = if (fleurs > 0) 44f else 38f
            for (i in 0 until 14) {
                val xb = -2f + i * 1.15f + r() * 0.8f
                val haut = hMin + r() * 12f
                val penche = (r() - 0.3f) * 9f
                g[i % 3].addPath(ruban(xb, PIED, xb + penche * 0.25f, haut - 2f, xb + penche, haut + abs(penche) * 0.45f, 1.1f, 0.05f))
            }
            val hiver = saison == Saison.WANTER
            for (i in 0 until (if (fleurs > 0) 5 else 4)) {
                val xb = i * 2.6f + r()
                val yt = (if (fleurs > 0) 40f else 31f) + r() * 7f
                val xt = xb + (r() - 0.2f) * 5f
                tiges.addPath(ruban(xb, PIED, xb + (xt - xb) * 0.2f, (PIED + yt) / 2f, xt, yt, 0.4f, 0.2f))
                when {
                    fleurs == 0 -> for (j in 0 until 5) tetes.addPath(amande(xt, yt + j * 1.1f, 1.6f, 0.4f, if (j % 2 == 0) 0.9f else 2.2f))
                    hiver -> {}
                    fleurs == 1 -> {
                        val t = if (i % 2 == 0) tetes else tetes2
                        for (k in 0 until 5) {
                            val a = k * 2f * PI.toFloat() / 5f
                            t.addCircle(xt + 0.8f * cos(a), yt + 0.8f * sin(a), 0.75f, Path.Direction.CCW)
                        }
                    }
                    else -> {
                        tetes.addCircle(xt, yt, 1.1f, Path.Direction.CCW)
                        tetes.addCircle(xt - 0.7f, yt + 0.6f, 0.9f, Path.Direction.CCW)
                        tetes.addCircle(xt + 0.7f, yt + 0.5f, 0.9f, Path.Direction.CCW)
                    }
                }
            }
            piece(g[1], clair, 0.8f)
            piece(g[2], sec, 0.8f)
            piece(tiges, if (fleurs == 0) sec else fonce, 0.6f)
            when (fleurs) {
                0 -> piece(tetes, sec, 0.6f)
                1 -> { piece(tetes, duJour(0xFFD8443A.toInt()), 0.6f); piece(tetes2, duJour(0xFFF2C12E.toInt()), 0.6f) }
                else -> piece(tetes, mix(CREME, P.cielBas, if (P.nuit) 0.5f else 0.1f), 0.6f)
            }
            piece(g[0], fonce, 0.8f)
        }

        /**
         * Une branche de feuillu qui entre par le coin du haut et avance de
         * [portee] unités, ses rameaux et ses feuilles qui pendent. L'hiver,
         * nue, la neige posée dessus.
         */
        fun feuillu(saison: Saison?, portee: Float, graine: Int) {
            val r = Alea(graine * 7 + 600)
            val tons = feuilles(saison)
            val cx = portee * 0.45f; val cy = 0.5f
            val branche = ruban(-4f, 2f, cx, cy, portee, 9f, 2.8f, 0.45f)
            val bouts = arrayListOf(floatArrayOf(-4f, 2f, cx, cy, portee, 9f))
            val o = FloatArray(4)
            val rameaux = floatArrayOf(0.3f, 4f, 10f, 0.45f, 2f, 7f, 0.55f, 6f, 8f, 0.78f, 2f, 9f, 0.92f, 4f, 5f, 0.16f, 7f, -6f, 0.05f, 3f, 13f)
            for (i in rameaux.indices step 3) {
                bezier(rameaux[i], -4f, 2f, cx, cy, portee, 9f, o)
                val xa = o[0]; val ya = o[1]
                val b = floatArrayOf(xa, ya, xa + rameaux[i + 1] * 0.6f, ya + rameaux[i + 2] * 0.2f,
                    xa + rameaux[i + 1], ya + rameaux[i + 2])
                branche.addPath(ruban(b[0], b[1], b[2], b[3], b[4], b[5], 0.9f, 0.25f))
                bouts += b
            }
            if (tons == null) {
                piece(branche, bois(), 0.9f)
                piece(ruban(-4f, 0.9f, cx, cy - 1.1f, portee * 0.85f, 7f, 1f, 0.15f),
                    mix(0xFFFFFFFF.toInt(), P.cielBas, 0.12f), 0.5f)
                return
            }
            val f1 = Path(); val f2 = Path()
            for (b in bouts) {
                var t = 0.12f
                while (t <= 1f) {
                    bezier(t, b[0], b[1], b[2], b[3], b[4], b[5], o)
                    for (k in 0 until 2) {
                        val a = PI.toFloat() / 2f + (r() - 0.5f) * 2f
                        (if (r() < 0.5f) f1 else f2).addPath(amande(o[0], o[1], 3.4f + r() * 1.6f, 0.6f + r() * 0.2f, a))
                    }
                    t += 0.1f
                }
            }
            // La masse du feuillage dans le coin, d'où sort la branche.
            for (k in 0 until 16) {
                val x = -2f + r() * 9f; val y = r() * 15f
                (if (r() < 0.55f) f1 else f2).addPath(amande(x, y, 3.6f + r() * 1.6f, 0.65f + r() * 0.2f, r() * 2f * PI.toFloat()))
            }
            piece(f1, tons[0], 0.7f)
            piece(branche, bois(), 0.9f)
            piece(f2, tons[1], 0.7f)
        }

        /** Une branche de saule pleureur au-dessus de l'eau : un rideau de brins qui pendent. */
        fun saule(saison: Saison?, portee: Float, graine: Int) {
            val r = Alea(graine * 7 + 700)
            val tons = feuilles(saison)
            val x1 = portee * 0.8f
            val branche = ruban(-4f, 1.5f, x1 * 0.5f, -0.5f, x1, 3.5f, 2.4f, 0.5f)
            val brins = Path(); val f1 = Path(); val f2 = Path()
            val o = FloatArray(4)
            for (k in 0 until 10) {
                bezier(0.05f + k * 0.095f, -4f, 1.5f, x1 * 0.5f, -0.5f, x1, 3.5f, o)
                val xa = o[0]; val ya = o[1]
                val long = 26f - k * 1.9f + r() * 5f
                val xb = xa + 0.5f + r() * 2.5f; val yb = ya + long
                val bx = xa + 2f + r() * 2f; val by = ya + long * 0.25f
                brins.addPath(ruban(xa, ya, bx, by, xb, yb, 0.45f, 0.2f))
                if (tons == null) continue
                var u = 0.12f + r() * 0.05f
                var sens = 1f
                while (u <= 1f) {
                    bezier(u, xa, ya, bx, by, xb, yb, o)
                    (if (r() < 0.5f) f1 else f2).addPath(
                        amande(o[0], o[1], 3f + r(), 0.24f, PI.toFloat() / 2f + sens * (0.25f + 0.2f * r())))
                    sens = -sens
                    u += 0.1f + r() * 0.03f
                }
            }
            if (tons == null) {
                // L'hiver, les brins nus du saule, couleur de paille.
                piece(branche, bois(), 0.9f)
                piece(brins, duJour(0xFFC9A23A.toInt()), 0.5f)
                return
            }
            piece(f1, tons[0], 0.7f)
            piece(branche, bois(), 0.9f)
            piece(brins, tons[0], 0.5f)
            piece(f2, tons[1], 0.7f)
        }

        /** Un bouleau au bord de la scène : le tronc blanc tout du long, une branche et ses rameaux qui pendent. */
        fun bouleau(saison: Saison?, portee: Float, graine: Int) {
            val r = Alea(graine * 7 + 800)
            val tons = feuilles(saison)
            val tronc = Path().apply {
                moveTo(-3f, -3f); lineTo(3.6f, -3f); quadTo(3.9f, 30f, 5f, PIED); lineTo(-3f, PIED); close()
            }
            piece(tronc, duJour(0xFFEDE6D6.toInt()), 1f)
            val marque = sombre(ardoise(), 0.4f)
            // Les taches noires de l'écorce, mordant sur les bords, et les lenticelles.
            var y = 1f
            while (y < 62f) {
                val bord = 3.6f + y * 0.02f
                if (r() < 0.6f) forme(amande(bord + 0.3f, y, 1.2f + r() * 1.6f, 0.35f + r() * 0.3f, PI.toFloat() + (r() - 0.5f) * 0.5f), marque)
                else forme(amande(-0.6f + r() * 2.6f, y, 0.8f + r() * 1.2f, 0.12f, (r() - 0.5f) * 0.2f), marque)
                y += 1.6f + r() * 3.2f
            }
            val gris = sombre(duJour(0xFF5A4A40.toInt()), 0.2f)
            val branche = ruban(3.4f, 9f, portee * 0.4f, 3.5f, portee, 7f, 1.3f, 0.3f)
            val f1 = Path(); val f2 = Path()
            val o = FloatArray(4)
            for (k in 0 until 7) {
                bezier(0.12f + k * 0.13f, 3.4f, 9f, portee * 0.4f, 3.5f, portee, 7f, o)
                val xa = o[0]; val ya = o[1]
                val long = 6f + r() * 6f
                val xb = xa + 1f + r(); val yb = ya + long
                branche.addPath(ruban(xa, ya, xa + 1.2f, ya + long * 0.3f, xb, yb, 0.35f, 0.15f))
                if (tons == null) continue
                var u = 0.2f + r() * 0.1f
                var sens = 1f
                while (u <= 1f) {
                    bezier(u, xa, ya, xa + 1.2f, ya + long * 0.3f, xb, yb, o)
                    (if (r() < 0.5f) f1 else f2).addPath(
                        amande(o[0], o[1], 2f + r() * 0.6f, 0.6f, PI.toFloat() / 2f + sens * (0.9f + 0.5f * r())))
                    sens = -sens
                    u += 0.17f + r() * 0.06f
                }
            }
            piece(branche, gris, 0.9f)
            if (tons == null) return
            piece(f1, tons[0], 0.7f)
            piece(f2, mix(tons[1], 0xFFE6D84A.toInt(), if (saison == Saison.HIERSCHT) 0.3f else 0f), 0.7f)
        }

        /** Un sarment de vigne de la Moselle : ses feuilles et, l'été et l'automne, une grappe. */
        fun vigne(saison: Saison?, portee: Float, graine: Int) {
            val r = Alea(graine * 7 + 900)
            val tons = feuilles(saison)
            val cep = sombre(duJour(0xFF7A5634.toInt()), 0.2f)
            val x0 = -4f; val y0 = 3f; val cx = portee * 0.5f; val cy = -1.5f; val x1 = portee; val y1 = 6f
            piece(ruban(x0, y0, cx, cy, x1, y1, 1.4f, 0.4f), cep, 0.9f)
            val o = FloatArray(4)
            for (t in floatArrayOf(0.3f, 0.8f)) {
                bezier(t, x0, y0, cx, cy, x1, y1, o)
                val x = o[0]; val yy = o[1]
                trait("M$x ${yy}Q${x + 2f} ${yy + 1f} ${x + 1.5f} ${yy + 2.6f}Q${x + 0.8f} ${yy + 3.3f} ${x + 0.5f} ${yy + 2.4f}", cep, 0.25f)
            }
            if (tons == null) return
            val f1 = Path(); val f2 = Path()
            for ((i, t) in floatArrayOf(0.15f, 0.38f, 0.72f, 0.93f).withIndex()) {
                bezier(t, x0, y0, cx, cy, x1, y1, o)
                val rr = 4.6f - 0.4f * i + r() * 0.5f
                val a0 = -PI.toFloat() / 2f + (r() - 0.5f) * 0.6f
                (if (i % 2 == 0) f1 else f2).addPath(pampre(o[0] + (r() - 0.5f), o[1] + rr * 0.95f, rr, a0))
            }
            piece(f1, tons[0], 0.7f)
            piece(f2, tons[1], 0.7f)
            if (saison == Saison.SUMMER || saison == Saison.HIERSCHT || saison == null) {
                bezier(0.55f, x0, y0, cx, cy, x1, y1, o)
                val grappe = Path()
                val rangs = intArrayOf(4, 3, 3, 2, 1)
                for ((j, n) in rangs.withIndex()) for (i in 0 until n) {
                    grappe.addCircle(o[0] + (i - (n - 1) / 2f) * 1.55f + (if (j % 2 == 0) 0f else 0.3f),
                        o[1] + 2f + j * 1.4f, 0.85f, Path.Direction.CCW)
                }
                val grain = if (saison == Saison.HIERSCHT) duJour(0xFF6A3E72.toInt()) else duJour(0xFFA8C25A.toInt())
                piece(grappe, noir(grain, 0.1f), 0.7f)
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

        /**
         * Une pièce de carton peint écrite comme en SVG : [dir] est le sens
         * des coups de pinceau, [ombre] la force de son ombre (`peint` de la
         * maquette, appelé directement).
         */
        fun carton(d: String, col: Int, dir: Float, ombre: Float = 1f) {
            if (!actif) return
            val t = chemin(d)
            t.computeBounds(boite, true)
            peint(t, col, dir, boite, ombre)
        }

        /** Un rond de carton peint (`rondPeint` de la maquette). */
        fun rondPeint(cx: Float, cy: Float, r: Float, col: Int, ombre: Float = 0.8f) {
            if (!actif) return
            val t = Path()
            t.addCircle(cx, cy, r, Path.Direction.CW)
            boite.set(cx - r, cy - r, cx + r, cy + r)
            peint(t, col, -0.6f, boite, ombre)
        }

        /**
         * Un feuillage de ronds de carton peint, deux rangées, la claire
         * devant la sombre (`feuillagePeint` de la maquette) : là où le
         * [bosquet] posait des ronds plats.
         */
        fun feuillagePeint(x0: Float, x1: Float, y: Float, taille: Float, graine: Int, col: Int) {
            val r = Alea(graine)
            val ronds = ArrayList<Float>()
            var x = x0
            while (x <= x1) {
                val rr = taille * (0.65f + 0.5f * r())
                val cx = x + r() * taille * 0.4f
                val cy = y - rr * 0.35f + r() * 1.2f
                ronds.add(cx); ronds.add(cy); ronds.add(rr)
                x += taille * 1.05f
            }
            val sombre = mix(col, 0xFF000000.toInt(), 0.12f)
            val claire = mix(col, 0xFFFFFFFF.toInt(), 0.1f)
            for (i in ronds.indices step 3) rondPeint(ronds[i], ronds[i + 1] - ronds[i + 2] * 0.35f, ronds[i + 2], sombre)
            for (i in ronds.indices step 3) {
                val rr = ronds[i + 2]
                rondPeint(ronds[i] + rr * 0.25f, ronds[i + 1] + rr * 0.25f, rr * 0.82f, claire)
            }
        }

        /** Un arc de cercle tourné dans le sens inverse des aiguilles, de [de] à [a] radians, comme `arc` du canevas HTML. */
        private fun arc(t: Path, cx: Float, cy: Float, r: Float, de: Float, a: Float) {
            var balayage = a - de
            while (balayage > 0f) balayage -= 2f * PI.toFloat()
            t.arcTo(RectF(cx - r, cy - r, cx + r, cy + r),
                Math.toDegrees(de.toDouble()).toFloat(), Math.toDegrees(balayage.toDouble()).toFloat())
        }

        /**
         * Une lanterne sur sa potence, scellée en ([x], [y]) dans le flanc d'un
         * pilier et tournée vers la droite ou vers la gauche. Allumée le soir.
         */
        fun lanterne(x: Float, y: Float, versDroite: Boolean, angle: Float = 0f, flamme: Float = -1f) {
            val s = if (versDroite) 1f else -1f
            val fer = mix(P.toit, 0xFF000000.toInt(), 0.45f)
            ligne(x, y, x + 3.6f * s, y, fer, 0.55f)
            trait("M$x ${y + 2.2f}Q${x + 1.6f * s} ${y + 2f} ${x + 2.6f * s} $y", fer, 0.4f)
            // La lanterne pend à sa potence : elle tourne autour du crochet.
            c.save()
            c.rotate(angle, x + 3.6f * s, y)
            val allumee = P.allume || flamme >= 0f
            if (allumee) {
                val f = if (flamme >= 0f) flamme else 0.5f
                rond(x + 3.6f * s, y + 2.3f, 2.6f + 0.5f * f, P.fenetre, (if (P.allume) 0.18f else 0.1f) + 0.14f * f)
            }
            ligne(x + 3.6f * s, y, x + 3.6f * s, y + 0.9f, fer, 0.4f)
            val verre = when {
                flamme >= 0f -> mix(P.fenetre, 0xFFFFFFFF.toInt(), 0.15f + 0.35f * flamme)
                P.allume -> P.fenetre
                else -> mix(P.pierre, P.pres, 0.55f)
            }
            forme("M${x + 2.8f * s} ${y + 0.9f}L${x + 4.4f * s} ${y + 0.9f}L${x + 4.1f * s} ${y + 3.6f}L${x + 3.1f * s} ${y + 3.6f}Z", verre)
            rect(x + 2.6f * s - (if (versDroite) 0f else 2f), y + 0.5f, 2f, 0.5f, fer)
            c.restore()
        }

        /**
         * Un chat roux qui court sur le plancher, les pattes en ([x], [y]),
         * vers la droite ou vers la gauche ; [foulee] fait tourner ses pattes,
         * [galop] l'allonge et le fait bondir.
         */
        fun chat(x: Float, y: Float, versDroite: Boolean, foulee: Float, galop: Float) {
            val roux = duJour(0xFFD9822B.toInt())
            val fonce = sombre(roux, 0.35f)
            val clair = duJour(0xFFF3D9B0.toInt())
            c.save()
            if (!versDroite) c.scale(-1f, 1f, x, y)
            val bond = abs(sin(foulee)) * (0.2f + 0.6f * galop)
            val yy = y - bond
            // Les pattes : deux paires qui se croisent, plus écartées au galop.
            val ecart = 0.5f + 0.5f * galop
            for ((dx, ph) in listOf(-1.7f to 0f, -1.3f to PI.toFloat(), 1.5f to PI.toFloat() * 0.5f, 1.9f to PI.toFloat() * 1.5f)) {
                val a = sin(foulee + ph) * ecart
                ligne(x + dx, yy - 1.4f, x + dx + a * 1.3f, y - 0.1f, fonce, 0.55f)
            }
            // La queue, dressée, qui ondule.
            trait("M${x - 2.4f} ${yy - 2.2f}Q${x - 4.4f} ${yy - 2.6f + sin(foulee * 0.5f)} ${x - 4.2f} ${yy - 4.8f}", roux, 0.55f)
            ovalePapier(x, yy - 2.3f, 2.8f + 0.4f * galop, 1.15f, roux, 0f)
            // Deux rayures, la tête, les oreilles, le museau clair.
            ligne(x - 0.6f, yy - 3.3f, x - 0.3f, yy - 2.2f, fonce, 0.35f)
            ligne(x + 0.6f, yy - 3.4f, x + 0.9f, yy - 2.3f, fonce, 0.35f)
            rond(x + 2.9f, yy - 3.1f, 1.05f, roux)
            triangle(x + 2.1f, yy - 3.7f, x + 2.5f, yy - 5f, x + 3f, yy - 3.9f, roux)
            triangle(x + 3f, yy - 3.9f, x + 3.6f, yy - 5f, x + 3.8f, yy - 3.6f, roux)
            rond(x + 3.6f, yy - 2.8f, 0.45f, clair)
            rond(x + 3.2f, yy - 3.3f, 0.16f, if (P.nuit) 0xFFE8F070.toInt() else 0xFF1E2A10.toInt())
            c.restore()
        }

        // ------------------------------------------- les lieux en papier découpé
        //
        // Redessinés ou ajoutés le 2026-11-13 d'après les photos du
        // propriétaire, dans la boîte 100 × 66 : le dessin du mot tient au
        // centre (17–83 × 12,5–58,5), si bien que ce qui fait le lieu vit dans
        // la bande du haut et sur les bords. Ce qui bouge seul ([Vie]) n'est
        // peint ici que hors de la carte ouverte (`vivant` faux).

        /** Une couleur de la photo de référence, tirée vers la palette de l'heure. */
        fun duJour(col: Int): Int = mix(col, P.pres, ATTENUE[P.rang])

        /** [col] dans l'ombre, de [t] : un brun de terre, qui prend la nuit la couleur de l'heure. */
        fun sombre(col: Int, t: Float): Int = mix(col, mix(0xFF2E2418.toInt(), P.pres, ATTENUE[P.rang]), t)

        /** L'ardoise des toits du pays. */
        fun ardoise(): Int = duJour(0xFF4A5468.toInt())

        /** Un vert de feuillage, de [nuance] 0 (sombre) à 1 (clair), dans la palette de l'heure. */
        fun vert(nuance: Float): Int =
            mix(mix(P.pres, P.milieu, nuance), 0xFF6FA845.toInt(), if (P.nuit) 0.05f else 0.22f)

        /** Des bosses de feuillage posées sur la ligne [crete], de [x0] à [x1]. */
        private fun bosses(x0: Float, x1: Float, crete: (Float) -> Float, rMin: Float, rMax: Float, r: Alea): Path {
            val t = Path()
            var x = x0
            while (x < x1) {
                val rr = rMin + (rMax - rMin) * r()
                t.addCircle(x, crete(x) + rr * 0.25f, rr, Path.Direction.CW)
                x += rr * (1.1f + r() * 0.4f)
            }
            return t
        }

        /** Une lisière : tout ce qui est sous la ligne [crete], de [x0] à [x1], festonné de feuillage. */
        fun lisiere(x0: Float, x1: Float, crete: (Float) -> Float, rMin: Float, rMax: Float, graine: Int, col: Int) {
            if (!actif) return
            val t = Path()
            t.moveTo(x0, BAS + 2f)
            var x = x0
            while (x <= x1) { t.lineTo(x, crete(x)); x += 1.5f }
            t.lineTo(x1, crete(x1)); t.lineTo(x1, BAS + 2f); t.close()
            t.addPath(bosses(x0, x1, crete, rMin, rMax, Alea(graine)))
            piece(t, col)
        }

        /** Une touffe de feuillage : des ronds ([ronds] : x, y, rayon) découpés d'une seule feuille. */
        fun touffe(col: Int, vararg ronds: Float) {
            if (!actif) return
            val t = Path()
            for (i in ronds.indices step 3) t.addCircle(ronds[i], ronds[i + 1], ronds[i + 2], Path.Direction.CW)
            piece(t, col)
        }

        /** Un rectangle de papier. */
        fun feuille(x: Float, y: Float, w: Float, h: Float, col: Int, ombre: Float = 1f) {
            if (!actif) return
            piece(Path().apply { addRect(x, y, x + w, y + h, Path.Direction.CW) }, col, ombre)
        }

        /** Un ovale de papier. */
        fun ovalePapier(cx: Float, cy: Float, rx: Float, ry: Float, col: Int, ombre: Float = 0.4f) {
            if (!actif) return
            piece(Path().apply { addOval(cx - rx, cy - ry, cx + rx, cy + ry, Path.Direction.CW) }, col, ombre)
        }

        /** Une maison du pays posée sur [bas] : le mur [mur], un toit d'ardoise de [toitH], une ou deux fenêtres. */
        fun maisonArdoise(x: Float, bas: Float, w: Float, h: Float, toitH: Float, mur: Int, rnd: Alea?) = mineral {
            feuille(x, bas - h, w, h, mur, 0.6f)
            pieceD("M${x - 0.4f} ${bas - h + 0.2f}L${x + w * 0.5f} ${bas - h - toitH}L${x + w + 0.4f} ${bas - h + 0.2f}Z", ardoise(), 0.6f)
            if (h > 2.2f) fenetre(x + w * 0.2f, bas - h * 0.62f, w * 0.2f, h * 0.26f, rnd)
            if (w > 4f && h > 2.2f) fenetre(x + w * 0.6f, bas - h * 0.62f, w * 0.2f, h * 0.26f, rnd)
        }

        /**
         * Peint [bloc] vu de trois quarts, comme le propriétaire veut ses
         * prises de vue (2026-11-14) : ses horizontales penchent de [pente]
         * autour de ([px], [py]), si bien qu'une façade ou un pont fuit au lieu
         * de se présenter de face. Le relevé de la végétation suit.
         */
        fun enPerspective(px: Float, py: Float, pente: Float, bloc: () -> Unit) {
            val m = Matrix().apply { setSkew(0f, pente, px, py) }
            c.save()
            c.concat(m)
            releve?.let { it.save(); it.concat(m) }
            try { bloc() } finally { c.restore(); releve?.restore() }
        }

        /**
         * Peint [bloc], dessiné à plat dans le rectangle (0, 0, [l], [h]), sur
         * le quadrilatère [q] (haut gauche, haut droit, bas droit, bas gauche,
         * huit coordonnées) en vraie perspective : les fenêtres d'une façade
         * qui fuit rapetissent et se serrent vers le fond, comme peintes sur
         * une planche vue de trois quarts.
         */
        fun surQuad(l: Float, h: Float, q: FloatArray, bloc: () -> Unit) {
            val m = Matrix()
            if (!m.setPolyToPoly(floatArrayOf(0f, 0f, l, 0f, l, h, 0f, h), 0, q, 0, 4)) return
            c.save()
            c.concat(m)
            releve?.let { it.save(); it.concat(m) }
            try { bloc() } finally { c.restore(); releve?.restore() }
        }

        private fun quad(q: FloatArray) = Path().apply {
            moveTo(q[0], q[1]); lineTo(q[2], q[3]); lineTo(q[4], q[5]); lineTo(q[6], q[7]); close()
        }

        /**
         * Une rangée de maisons vue de trois quarts, au lieu d'une bande de
         * façades de face (demande du propriétaire, 2026-11-14 : « peintes
         * sur une planche de trois quarts », sans « bandes blanches
         * horizontales qui se succèdent »). Les façades fuient selon [f]
         * ([Fuite]), chaque maison montre son toit et, quand elle dépasse sa
         * voisine de devant, son pignon ; la première montre le sien en entier.
         * Peintes du fond vers l'avant, comme on colle des cartons.
         */
        fun rue3q(r: Fuite, maisons: Array<FloatArray>, murs: IntArray, rnd: Alea, ecart: Float = 0f) = mineral {
            val tl = r.taille
            val toit = ardoise()
            val faitage = mix(toit, 0xFFFFFFFF.toInt(), 0.18f)
            val poutre = duJour(0xFF4A3A2E.toInt())
            val debuts = FloatArray(maisons.size)
            var u = 0f
            // [ecart] : des maisons séparées, dont on voit chaque pignon, comme
            // dans un village ; sans écart, une rangée mitoyenne de ville.
            for (i in maisons.indices) { debuts[i] = u; u += maisons[i][0] + ecart }
            u -= ecart
            // Le pignon fuit vers la gauche, d'autant moins que la maison est loin.
            fun profondeur(x: Float, y: Float, e: Float): FloatArray {
                val dx = r.fuiteCote[0] - x
                val dy = r.fuiteCote[1] - y
                val k = 9f * tl * e / sqrt(dx * dx + dy * dy)
                return floatArrayOf(dx * k, dy * k)
            }
            for (i in maisons.indices.reversed()) {
                val m = maisons[i]
                val w = m[0]; val h = m[1]; val mur = murs[m[2].toInt()]; val toitH = m[4]
                val ua = debuts[i]; val ub = ua + w
                val ax = r.x(ua); val ay = r.y(ua); val ea = r.echelle(ua)
                val bx = r.x(ub); val by = r.y(ub); val eb = r.echelle(ub)
                val hautA = ay - h * tl * ea; val hautB = by - h * tl * eb
                val pa = profondeur(ax, ay, ea)
                val pb = profondeur(bx, by, eb)
                // Le pignon, à l'ombre, et son triangle sous le toit.
                val pignon = sombre(mur, 0.28f)
                val cote = floatArrayOf(ax + pa[0], hautA + pa[1], ax, hautA, ax, ay, ax + pa[0], ay + pa[1])
                piece(quad(cote), pignon, 0.5f)
                val sommetA = floatArrayOf(ax + pa[0] * 0.5f, hautA + pa[1] * 0.5f - toitH * tl * ea)
                piece(Path().apply {
                    moveTo(ax, hautA); lineTo(sommetA[0], sommetA[1]); lineTo(ax + pa[0], hautA + pa[1]); close()
                }, pignon, 0.3f)
                val fin = h * tl * ea < 4.5f
                if (!fin) surQuad(6f, h, cote) {
                    var fy = 3f
                    while (fy < h - 3f) { fenetre(2.4f, fy, 1.2f, 2.4f, rnd); fy += 5.2f }
                }
                // La façade, peinte à plat puis posée en perspective.
                val facade = floatArrayOf(ax, hautA, bx, hautB, bx, by, ax, ay)
                piece(quad(facade), mur, 0.6f)
                if (!fin) surQuad(w, h, facade) {
                    if (m[3] > 0f) {
                        var px = 0.4f
                        while (px <= w) { ligne(px, 0f, px, h, poutre, 0.3f); px += 2.6f }
                        for (k in 1..2) { val y = h * k / 3f; ligne(0f, y, w, y, poutre, 0.3f) }
                    }
                    var fy = 2.6f
                    while (fy < h - 4f) {
                        var fx = 1.6f
                        while (fx < w - 2f) { fenetre(fx, fy, 1.3f, 2.4f, rnd); fx += 3.3f }
                        fy += 4.8f
                    }
                    // La porte au rez-de-chaussée.
                    rect(w * 0.42f, h - 3.6f, 1.8f, 3.6f, sombre(mur, 0.55f))
                }
                // Le pan du toit, qui monte vers le faîte en reculant.
                val sommetB = floatArrayOf(bx + pb[0] * 0.5f, hautB + pb[1] * 0.5f - toitH * tl * eb)
                val pan = floatArrayOf(sommetA[0], sommetA[1], sommetB[0], sommetB[1], bx + 0.4f * tl * eb, hautB + 0.3f * tl, ax - 0.4f * tl * ea, hautA + 0.3f * tl)
                piece(quad(pan), toit, 0.6f)
                ligne(sommetA[0], sommetA[1], sommetB[0], sommetB[1], faitage, 0.35f * tl * (ea + eb))
                if (m[5] > 0f && !fin) surQuad(w, 6f, pan) {
                    var lx = 2.2f
                    while (lx < w - 3f) {
                        feuille(lx, 2.4f, 2f, 2.6f, mur, 0.3f)
                        fenetre(lx + 0.5f, 3f, 1f, 1.6f, rnd)
                        pieceD("M${lx - 0.4f} 2.6L${lx + 1f} 0.9L${lx + 2.4f} 2.6Z", toit, 0f)
                        lx += 4.6f
                    }
                }
            }
            // Le quai, sous les façades, jusqu'au bord de l'eau.
            if (r.quai <= 0f) return@mineral
            val pierre = duJour(0xFFA59A86.toInt())
            piece(Path().apply {
                moveTo(r.x(0f) - 12f * tl, r.y(0f) + 1f * tl)
                var v = 0f
                while (v <= u) { lineTo(r.x(v), r.y(v)); v += 2f }
                lineTo(r.x(u), r.eau(r.x(u)))
                v = u
                while (v >= 0f) { lineTo(r.x(v), r.eau(r.x(v))); v -= 2f }
                lineTo(r.x(0f) - 12f * tl, r.eau(r.x(0f) - 12f * tl))
                close()
            }, pierre, 0.5f)
        }

        /** Une étoile à cinq branches de rayon [r]. */
        fun etoile(cx: Float, cy: Float, r: Float): Path {
            val t = Path()
            for (k in 0 until 10) {
                val a = -PI.toFloat() / 2f + k * PI.toFloat() / 5f
                val rr = if (k % 2 == 0) r else r * 0.42f
                if (k == 0) t.moveTo(cx + rr * cos(a), cy + rr * sin(a)) else t.lineTo(cx + rr * cos(a), cy + rr * sin(a))
            }
            t.close()
            return t
        }

        /** Un cadre de scène taillé dans une feuille : tout le devant de la scène, moins l'[ouverture]. */
        private fun cadre(ouverture: String): Path {
            val t = Path().apply { addRect(-3f, -3f, LARGEUR + 3f, DEVANT + 0.2f, Path.Direction.CW) }
            t.op(chemin(ouverture), Path.Op.DIFFERENCE)
            return t
        }

        // ---- Veianen, d'après la photo du château au-dessus de la brume

        fun veianenPapier() {
            val r = Alea(103)
            lisiere(-2f, 102f, { x -> 27f - 3f * sin(x * 0.06f + 1f) - max(0f, x - 55f) * 0.1f }, 1.2f, 1.9f, 104,
                mix(P.loin, P.cielBas, 0.12f))
            lisiere(-2f, 102f, { x -> 33f + 2f * sin(x * 0.09f) }, 1.4f, 2.2f, 105, mix(P.loin, P.milieu, 0.35f))
            plan(1)
            pieceD("M-2 66L-2 43C12 40 26 36 36 33C50 30 70 30 84 33C92 35 98 37 102 38L102 66Z", P.milieu)
            touffe(vert(0.3f), 24f, 34f, 3f, 28f, 32.5f, 3.2f, 32f, 33.5f, 2.6f)
            touffe(vert(0.3f), 90f, 34.5f, 3f, 95f, 33f, 3.2f, 99f, 35f, 2.8f)
            val mur = duJour(0xFFEADDBE.toInt())
            val ombre = mix(mur, P.pres, 0.28f)
            val toit = ardoise()
            enPerspective(60f, 30f, 0.07f) {
                mineral {
                    pieceD("M33 36L37 30L88 30L91 36Z", duJour(0xFFA39377.toInt()))
                    // La tour ronde de gauche et sa flèche.
                    feuille(33.5f, 17f, 4.2f, 14f, mur)
                    feuille(36.1f, 17f, 1.6f, 14f, ombre, 0f)
                    pieceD("M32.8 17.2L35.6 8.4L38.4 17.2Z", toit)
                    feuille(37.6f, 24f, 7f, 7f, mur)
                    // Le palais et ses grands toits d'ardoise.
                    feuille(44f, 14f, 26f, 17f, mur)
                    feuille(63f, 14f, 7f, 17f, ombre, 0f)
                    pieceD("M43 14.4L46 8L68 8L71 14.4Z", toit)
                    // La chapelle et son clocher.
                    feuille(55.6f, 4.5f, 2.8f, 4f, mur)
                    pieceD("M55.1 4.8L57 0.4L58.9 4.8Z", toit)
                    // La tour carrée de droite et l'aile basse.
                    feuille(70f, 12f, 5.4f, 19f, mur)
                    feuille(73.4f, 12f, 2f, 19f, ombre, 0f)
                    pieceD("M69.4 12.2L72.7 5.6L76 12.2Z", toit)
                    feuille(75.4f, 22f, 12.6f, 9f, mur)
                    pieceD("M74.8 22.2L77.2 18.6L86 18.6L88.6 22.2Z", toit)
                    for (fx in floatArrayOf(46.2f, 49.8f, 53.4f, 60.6f, 64.2f, 67.4f)) {
                        fenetre(fx, 17.5f, 1.2f, 2f, r); fenetre(fx, 23f, 1.2f, 2f, r)
                    }
                    fenetre(35f, 21f, 1f, 1.8f, r); fenetre(71.6f, 16f, 1.1f, 2f, r); fenetre(71.6f, 22f, 1.1f, 2f, r)
                    for (fx in floatArrayOf(77.5f, 80.5f, 83.5f)) fenetre(fx, 25f, 1.1f, 1.8f, r)
                }
            }
            if (!vivant) brumeDeVeianen(0f)
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            val tronc = duJour(0xFF5A4030.toInt())
            feuille(5f, 30f, 1.6f, 30f, tronc, 0.6f)
            feuille(93f, 32f, 1.6f, 28f, tronc, 0.6f)
            touffe(vert(0.55f), -2f, 36f, 9f, 6f, 28f, 8f, 3f, 19f, 6.5f, 13f, 38f, 6.5f, -1f, 48f, 7f, 11f, 49f, 6f)
            touffe(vert(0.75f), 4f, 24f, 4.5f, 10f, 33f, 4f, 2f, 42f, 4f)
            touffe(vert(0.5f), 101f, 34f, 9f, 92f, 28f, 7f, 97f, 45f, 8f, 88f, 47f, 6f)
            touffe(vert(0.72f), 94f, 31f, 4f, 98f, 39f, 3.6f)
            lisiere(-2f, 102f, { x -> 53f + 1.6f * sin(x * 0.35f) }, 1.6f, 2.6f, 106, vert(0.35f))
            c.restore()
        }

        /** La brume de la vallée de l'Our : deux bandes de papier pâle, glissées de [d] unités. */
        fun brumeDeVeianen(d: Float) {
            val col = if (P.nuit) mix(P.cielBas, 0xFFFFFFFF.toInt(), 0.25f) else mix(0xFFFFFFFF.toInt(), P.cielBas, 0.3f)
            for ((k, y) in floatArrayOf(31f, 34.6f).withIndex()) {
                val dec = ((d * (1f + 0.5f * k)) % 30f + 30f) % 30f - 30f
                val t = Path()
                var x = -32f
                t.moveTo(x + dec, y + 3f)
                while (x <= 132f) { t.lineTo(x + dec, y + sin(x * 0.2094f + k) * 0.9f); x += 2f }
                x = 132f
                while (x >= -32f) { t.lineTo(x + dec, y + 3.2f + sin(x * 0.2094f + 2f + k) * 0.7f); x -= 2f }
                t.close()
                piece(t, col, 0.4f)
            }
        }

        // ---- Esch-Sauer, d'après la photo de la tour ronde au-dessus du bourg

        fun eschSauerPapier() {
            val r = Alea(115)
            lisiere(-2f, 102f, { x -> 12f + 5f * cos(x * 0.028f) + sin(x * 0.2f) * 0.6f }, 1.5f, 2.4f, 116,
                mix(P.loin, P.milieu, 0.45f))
            pieceD("M80 22C84 18 90 17 95 20L99 36L78 38Z", duJour(0xFF9C7C56.toInt()), 0.5f)
            plan(1)
            plancher(56f, DEVANT)
            poser(56f)
            lisiere(-2f, 102f, { x -> 31f - x * 0.04f + sin(x * 0.15f) }, 1.6f, 2.6f, 117, P.milieu)
            val blanc = duJour(0xFFF4F0E8.toInt())
            val creme = duJour(0xFFE6D8BE.toInt())
            mineral {
                feuille(29.2f, 23.5f, 2.4f, 6f, blanc)
                pieceD("M28.9 23.7L30.4 18.5L31.9 23.7Z", ardoise())
            }
            // Le village en deux rues de trois quarts, qui fuient vers le
            // fond de la boucle de la Sûre, l'église entre les deux.
            val murs = intArrayOf(blanc, creme, blanc, blanc)
            rue3q(RUE_ESCH_HAUT, MAISONS_ESCH_HAUT, murs, r, ecart = 6f)
            mineral {
                feuille(56.5f, 33.5f, 3f, 9f, blanc)
                pieceD("M56.1 33.7L58 25L59.9 33.7Z", ardoise())
            }
            rue3q(RUE_ESCH_BAS, MAISONS_ESCH_BAS, murs, r, ecart = 6f)
            riviere(51.5f, 56f, 118, 44f, 52f, 58f)
            c.restore()
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            val roc = duJour(0xFFB09670.toInt())
            mineral {
                pieceD("M-2 58.5L-2 41C4 39 12 38 20 40C26 42 31 50 35 58.5Z", roc)
                pieceD("M8 58.5C10 52 16 48 22 47C26 50 28 54 29 58.5Z", mix(roc, P.pres, 0.25f), 0.6f)
            }
            pieceD("M-2 43C4 40 12 39 20 41C24 42 27 45 29 48L26 48C22 44.5 14 42.5 -2 45.5Z", duJour(0xFF9A8C58.toInt()), 0.5f)
            val pierre = duJour(0xFFA79A88.toInt())
            mineral {
                pieceD("M3 42L3 9.2C3 7.6 23 7.6 23 9.2L23 42Z", pierre)
                pieceD("M17 42L17 8.3C20 8.5 22.4 8.8 23 9.2L23 42Z", mix(pierre, P.pres, 0.32f), 0f)
                pieceD("M3 42L3 9.2C3.4 8.7 4.6 8.4 6 8.2L6 42Z", mix(pierre, 0xFFFFFFFF.toInt(), 0.16f), 0f)
                feuille(2.6f, 7.8f, 20.8f, 1.6f, mix(pierre, P.pres, 0.18f), 0.5f)
                val joint = mix(pierre, P.pres, 0.4f)
                for (y in floatArrayOf(14f, 20f, 26f, 32f, 38f)) ligne(3f, y, 23f, y, joint, 0.18f, 0.6f)
                val trou = mix(P.pres, 0xFF000000.toInt(), 0.4f)
                rect(10.4f, 13f, 2f, 3.2f, trou); rond(11.4f, 13f, 1f, trou)
                rect(15.2f, 25.5f, 1.5f, 3f, trou); rect(6.5f, 32f, 1.3f, 2.6f, trou)
                if (P.allume) rect(10.7f, 13.6f, 1.4f, 2.2f, P.fenetre, 0.9f)
                ligne(13f, 8f, 13f, 0.6f, duJour(0xFF6A6A6A.toInt()), 0.35f)
            }
            if (!vivant) drapeau(13f, 0.9f, 0f)
            pieceD("M93 58.5L93 51L88 51L92.4 45L89.6 45L94 37L98.4 45L95.6 45L100 51L95 51L95 58.5Z", vert(0.05f))
            c.restore()
        }

        /** Le drapeau du pays à sa hampe en ([x], [y]) : rouge, blanc, bleu clair, ondulé à la [phase]. */
        fun drapeau(x: Float, y: Float, phase: Float, force: Float = 1f) {
            val couleurs = intArrayOf(duJour(0xFFED2939.toInt()), duJour(0xFFF7F7F7.toInt()), duJour(0xFF00A1DE.toInt()))
            val l = 5.2f
            val h = 1.05f
            val n = 8
            for ((k, col) in couleurs.withIndex()) {
                val t = Path()
                for (i in 0..n) {
                    val u = i / n.toFloat()
                    val o = sin(u * 5f - phase) * 0.45f * u * force
                    if (i == 0) t.moveTo(x + u * l, y + k * h + o) else t.lineTo(x + u * l, y + k * h + o)
                }
                for (i in n downTo 0) {
                    val u = i / n.toFloat()
                    t.lineTo(x + u * l, y + (k + 1) * h + sin(u * 5f - phase) * 0.45f * u * force)
                }
                t.close()
                piece(t, col, 0.3f)
            }
        }

        // ---- Neimënster, vu des casemates du Bock (photo du propriétaire)

        fun neimensterPapier() {
            val r = Alea(125)
            val toit = ardoise()
            val facades = intArrayOf(duJour(0xFFF1E6CF.toInt()), duJour(0xFFE9C9B8.toInt()), duJour(0xFFF3DC96.toInt()),
                duJour(0xFFE6E2D6.toInt()))
            mineral {
                var x = 22f
                var k = 0
                while (x < 62f) {
                    val w = 4f + r() * 2.5f
                    val h = 4f + r() * 4f
                    feuille(x, 25f - h, w, h + 2f, facades[k % 4], 0.6f)
                    pieceD("M${x - 0.3f} ${25.2f - h}L${x + 0.8f} ${22.6f - h}L${x + w - 0.8f} ${22.6f - h}L${x + w + 0.3f} ${25.2f - h}Z", toit, 0.5f)
                    fenetre(x + w * 0.25f, 25f - h * 0.6f, w * 0.18f, 1.4f, r)
                    fenetre(x + w * 0.6f, 25f - h * 0.6f, w * 0.18f, 1.4f, r)
                    x += w + 0.4f; k++
                }
                // Une tourelle à flèche bleue, au-dessus des toits.
                feuille(33f, 11.5f, 2.2f, 8f, facades[3])
                pieceD("M32.6 11.7L34.1 7.2L35.6 11.7Z", duJour(0xFF4E6E9A.toInt()))
                val rempart = duJour(0xFFBDB29A.toInt())
                feuille(20f, 25f, 45f, 9f, rempart)
                var cx = 20f
                while (cx < 64f) { rect(cx, 24.2f, 1.2f, 1f, rempart); cx += 2.4f }
            }
            plan(1)
            lisiere(12f, 70f, { x -> 37f + 2.4f * sin(x * 0.21f) }, 2f, 3.2f, 126, vert(0.45f))
            val roc = duJour(0xFFBFAD86.toInt())
            val maconnerie = duJour(0xFFCFC1A0.toInt())
            mineral {
                pieceD("M57 66L59 42C60 36 62 32 63 28L102 27L102 66Z", roc)
                pieceD("M62 30L63.5 11L102 8.5L102 30C88 31.5 74 31.5 62 30Z", maconnerie)
                val joint = mix(maconnerie, P.pres, 0.3f)
                for (y in floatArrayOf(14f, 18f, 22f, 26f)) ligne(63.4f, y, 102f, y - 1.4f, joint, 0.16f, 0.6f)
                val strate = mix(roc, P.pres, 0.3f)
                for (x in floatArrayOf(66f, 72f, 79f, 86f, 93f)) ligne(x, 32f, x - 1f, 64f, strate, 0.22f, 0.6f)
                // Le palais jaune sur la crête.
                feuille(80f, 3.5f, 18f, 6f, duJour(0xFFE9C05A.toInt()))
                pieceD("M79.4 3.7L81.6 0.6L96.4 0.6L98.6 3.7Z", toit)
                for (fx in floatArrayOf(82f, 85f, 88f, 91f, 94f)) fenetre(fx, 5.4f, 1f, 1.8f, r)
            }
            touffe(vert(0.35f), 64f, 31f, 2.4f, 68f, 30.5f, 2f, 98f, 28.5f, 2.6f)
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            // La voûte de la casemate, en cadre de scène : sa face, et dedans
            // le creux plus sombre de la galerie.
            val pierre = duJour(0xFFC6B088.toInt())
            mineral {
                piece(cadre("M19.5 60L19.5 33C19.5 19 33 10 54 9.6C72 9.2 87 13 94 24L96 35L96 60Z"), sombre(pierre, 0.5f))
                piece(cadre("M16.5 60L16.5 32C16.5 16 32 6.8 54 6.4C74 6 90 10 97.5 22L99.5 34L99.5 60Z"), pierre)
                // La roche taillée de la galerie : des bancs clairs et sombres.
                val clair = mix(pierre, 0xFFFFFFFF.toInt(), 0.16f)
                pieceD("M-3 14C3 12 9 13 14 16C11 17 5 17 -3 18Z", clair, 0.4f)
                pieceD("M-3 27C3 25 10 26 15 29C11 30 4 30 -3 31Z", clair, 0.4f)
                pieceD("M-3 40C3 38.4 10 39 15.5 42C11 43 4 43 -3 44Z", clair, 0.4f)
                pieceD("M30 -3L36 3C42 1.6 50 1.4 58 2.4C66 1.4 76 2 84 4.6L92 -3Z", sombre(pierre, 0.18f), 0.4f)
            }
            touffe(vert(0.6f), 20f, 55f, 3f, 25f, 56f, 2.6f, 29f, 57.5f, 2f)
            touffe(vert(0.55f), 88f, 53f, 3.4f, 94f, 50f, 3.2f, 98f, 55f, 3f)
            // Le garde-corps de fer.
            val fer = duJour(0xFF2E3238.toInt())
            fun rampe(x: Float) = 45f - (x - 17f) * 0.018f
            mineral {
                ligne(16.5f, rampe(16.5f), 100f, rampe(100f), fer, 0.5f)
                ligne(16.5f, 56f, 100f, 55.4f, fer, 0.3f)
                var bx = 19f
                while (bx < 99f) { ligne(bx, rampe(bx), bx, DEVANT, fer, 0.28f); bx += 4.4f }
            }
            c.restore()
        }

        // ---- Mëllerdall, le Schéissendëmpel (photo du propriétaire)

        fun mellerdallPapier() {
            val r = Alea(135)
            val brume = mix(P.loin, P.cielBas, 0.25f)
            val tronc = sombre(brume, 0.35f)
            for (k in 0 until 14) {
                val x = r() * 100f
                val w = 0.5f + r() * 0.5f
                pieceD("M$x 44L${x + w * 0.3f} -3L${x + w * 0.7f} -3L${x + w} 44Z", tronc, 0.3f)
            }
            // Le houppier qui retombe du haut de la fenêtre, comme une frange.
            val houppier = mix(brume, 0xFFD8B46A.toInt(), if (P.nuit) 0.1f else 0.35f)
            val frange = Path()
            frange.moveTo(-3f, -3f)
            var fx = -3f
            while (fx <= 103f) { frange.lineTo(fx, 7f + 3f * sin(fx * 0.15f) + 1.5f * sin(fx * 0.5f)); fx += 1.5f }
            frange.lineTo(103f, -3f); frange.close()
            frange.op(bosses(-3f, 103f, { x -> 7f + 3f * sin(x * 0.15f) }, 1.6f, 2.6f, Alea(137)), Path.Op.UNION)
            piece(frange, houppier)
            lisiere(-2f, 102f, { x -> 38f + 1.5f * sin(x * 0.12f) }, 1.4f, 2.2f, 136, mix(brume, P.milieu, 0.4f))
            plan(1)
            val mousse = duJour(0xFF5F8A3C.toInt())
            val roc = duJour(0xFF8C8A78.toInt())
            mineral {
                pieceD("M-2 66L-2 21C6 19 14 22 21 28C26 34 28 45 28 66Z", roc)
                pieceD("M102 66L102 19C94 17 85 21 79 27C74 34 72 45 72 66Z", roc)
            }
            pieceD("M-2 24C6 21 13 23 19 28C14 27 6 26 -2 28Z", mousse, 0.4f)
            pieceD("M102 22C94 19 86 22 81 27C87 25 95 25 102 26Z", mousse, 0.4f)
            touffe(mousse, 6f, 34f, 3f, 12f, 40f, 3.4f, 4f, 48f, 3f, 94f, 33f, 3f, 88f, 41f, 3.4f, 96f, 48f, 3f)
            val pont = duJour(0xFF928D78.toInt())
            enPerspective(50f, 30f, 0.06f) {
                mineral {
                    pieceD("M16 26L84 26L84 31C80 33 76 37 73 44L66 44C65 37 59 32 50 32C41 32 35 37 34 44L27 44C24 37 20 33 16 31Z", pont)
                    trait("M34 44C35 37 41 32 50 32C59 32 65 37 66 44", mix(pont, P.pres, 0.35f), 0.7f)
                }
                pieceD("M16 26L84 26L84 27.6C70 28.6 30 28.6 16 27.6Z", mousse, 0.3f)
                // La rambarde de bois, en croix de Saint-André.
                val bois = duJour(0xFF6B4A2E.toInt())
                mineral {
                    ligne(16.5f, 20.6f, 83.5f, 20.6f, bois, 0.5f)
                    var x = 17f
                    while (x < 83f) {
                        ligne(x, 20.4f, x, 26f, bois, 0.45f)
                        ligne(x, 21f, x + 8.25f, 25.6f, bois, 0.25f)
                        ligne(x, 25.6f, x + 8.25f, 21f, bois, 0.25f)
                        x += 8.25f
                    }
                    ligne(83f, 20.4f, 83f, 26f, bois, 0.45f)
                }
            }
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            pieceD("M-2 60L-2 50C20 48 80 48 102 50L102 60Z", mix(P.eau, 0xFFC9A562.toInt(), if (P.nuit) 0.2f else 0.5f), 0.4f)
            val rocher = duJour(0xFF7F806E.toInt())
            mineral {
                pieceD("M38 50.5C38 42 42 38 50 38C58 38 62 42 62 50.5Z", rocher)
                pieceD("M20 52C20 45 24 42 30 42C35 42 37 46 37 52Z", rocher)
                pieceD("M63 52C63 45 66 41 72 41C78 41 81 46 81 52Z", rocher)
            }
            pieceD("M38.6 43C40 39.5 44 38 50 38C56 38 60 39.5 61.4 43C56 41 44 41 38.6 43Z", mousse, 0.3f)
            pieceD("M20.6 46C22 43 26 42 30 42C33 42 35.4 43.5 36.4 46C32 44.6 25 44.6 20.6 46Z", mousse, 0.3f)
            pieceD("M63.6 45C65 42 68 41 72 41C76 41 79.4 42.5 80.4 45C76 43.6 68 43.6 63.6 45Z", mousse, 0.3f)
            val eau = if (P.nuit) mix(P.cielBas, 0xFFFFFFFF.toInt(), 0.45f) else mix(0xFFFFFFFF.toInt(), P.cielBas, 0.12f)
            for (x in CHUTES) {
                pieceD("M${x - 1f} 39L${x + 1f} 39L${x + 1.8f} 51L${x - 1.8f} 51Z", eau, 0.3f)
                ovalePapier(x, 51f, 3.6f, 1.2f, eau)
            }
            pieceD("M-2 58.5L-2 48C4 46 10 47 14 51C16 54 16.6 57 16.6 58.5Z", mousse)
            pieceD("M102 58.5L102 48C96 46 90 47 86 51C84 54 83.4 57 83.4 58.5Z", mousse)
            c.restore()
        }

        // ---- Stad, le Grund au bord de l'Alzette (photo du propriétaire)

        fun stadPapier() {
            val r = Alea(145)
            lisiere(-2f, 34f, { x -> 22f + 3f * sin(x * 0.2f) }, 1.8f, 2.6f, 146, mix(P.loin, P.milieu, 0.4f))
            val rocher = duJour(0xFFB7AA8C.toInt())
            val rempart = duJour(0xFFCFC3A5.toInt())
            mineral {
                pieceD("M24 66L26 36L102 33L102 66Z", rocher)
                pieceD("M30 36.5L30 24L102 22L102 34Z", rempart)
                for ((bx, w) in listOf(38f to 8f, 50f to 9f, 62f to 8f)) {
                    pieceD("M$bx 37L$bx 22C$bx 19.4 ${bx + w} 19.4 ${bx + w} 22L${bx + w} 37Z", rempart)
                    feuille(bx + w * 0.62f, 21f, w * 0.38f, 16f, mix(rempart, P.pres, 0.22f), 0f)
                }
            }
            lisiere(28f, 102f, { x -> 21.5f + 0.8f * sin(x * 0.5f) }, 1.1f, 1.7f, 147, vert(0.4f))
            feuille(71.4f, 10f, 1.2f, 12f, duJour(0xFF5A4030.toInt()), 0.5f)
            touffe(vert(0.5f), 72f, 11f, 4.6f, 68f, 14f, 3.8f, 76f, 14.5f, 3.8f, 72f, 6.5f, 3.6f)
            plan(1)
            val murs = intArrayOf(duJour(0xFFE9DDC5.toInt()), duJour(0xFFD9C6A0.toInt()), duJour(0xFFF3EFE7.toInt()),
                duJour(0xFFE3E1DA.toInt()))
            rue3q(RUE_GRUND, MAISONS_GRUND_3Q, murs, r)
            touffe(vert(0.6f), 58f, 43.5f, 2.6f, 62f, 42.5f, 3f, 66f, 43.4f, 2.2f)
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            // L'Alzette, qui fuit avec le quai : son bord monte vers la droite.
            val eau = mix(P.eau, 0xFF5F7046.toInt(), if (P.nuit) 0.25f else 0.45f)
            val g = RUE_GRUND
            piece(Path().apply {
                moveTo(-2f, BAS); lineTo(-2f, g.eau(-2f))
                var x = -2f
                while (x <= 102f) { lineTo(x, g.eau(x)); x += 4f }
                lineTo(102f, BAS); close()
            }, eau, 0.4f)
            // Les reflets des façades, couchés dans la fuite.
            val reflet = mix(eau, 0xFFFFFFFF.toInt(), if (P.nuit) 0.12f else 0.3f)
            var u = 1f
            for (m in MAISONS_GRUND_3Q) {
                val x0 = g.x(u); val x1 = g.x(u + m[0] - 2f)
                ligne(x0, g.eau(x0) + 1f, x1, g.eau(x1) + 1f, reflet, 0.5f * g.echelle(u), 0.5f)
                if (P.allume) rect((x0 + x1) / 2f - 0.3f, g.eau((x0 + x1) / 2f) + 0.5f, 0.6f, 5f * g.echelle(u), P.fenetre, 0.4f)
                u += m[0]
            }
            touffe(vert(0.7f), 1f, 55f, 4f, 7f, 56.5f, 3f)
            c.restore()
        }

        // ---- Musel, le monument de Schengen (photo du propriétaire)

        fun muselPapier() {
            val r = Alea(155)
            lisiere(-2f, 102f, { x -> 29f + 2f * sin(x * 0.08f) }, 1.4f, 2.2f, 156, mix(P.loin, P.milieu, 0.3f))
            var x = 66f
            while (x < 100f) { maisonArdoise(x, 31.5f, 3.6f, 2.4f + r() * 1.2f, 1.6f, duJour(0xFFEDE3C8.toInt()), r); x += 4.4f }
            plan(1)
            plancher(56f, DEVANT)
            poser(56f)
            pieceD("M-2 47L-2 32.5L102 32.5L102 47Z", P.eau, 0.4f)
            val clair = mix(P.eau, 0xFFFFFFFF.toInt(), if (P.nuit) 0.18f else 0.4f)
            for (k in 0 until 10) rect(r() * 92f, 34f + r() * 11f, 3f + r() * 7f, 0.3f, clair, 0.7f)
            if (!vivant) peniche(60f, 0f)
            pieceD("M-2 66L-2 46L102 46L102 66Z", vert(0.6f), 0.5f)
            c.restore()
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            touffe(vert(0.35f), -2f, 8f, 8f, 7f, 3f, 7f, 15f, 6f, 6f, -1f, 16f, 6f)
            // Les trois stèles d'acier corten, leur étoile évidée et leur plaque.
            val rouille = duJour(0xFFA8502A.toInt())
            val tranche = mix(rouille, P.pres, 0.4f)
            val or = duJour(0xFFE6C15A.toInt())
            val plaque = duJour(0xFFDAD6CC.toInt())
            mineral {
                for ((sx, y0) in listOf(5f to 26f, 44f to 20f, 82f to 18f)) {
                    val w = 10f
                    val y1 = 56f
                    pieceD("M${sx + w} ${y0}L${sx + w + 3f} ${y0 + 1f}L${sx + w + 3f} ${y1 - 0.6f}L${sx + w} ${y1}Z", tranche)
                    feuille(sx, y0, w, y1 - y0, rouille)
                    val cx = sx + w / 2f
                    val cy = y0 + 8f
                    piece(etoile(cx + 0.5f, cy + 0.4f, 3.4f), mix(rouille, 0xFF000000.toInt(), 0.45f), 0f)
                    piece(etoile(cx, cy, 2.6f), or, 0.4f)
                    feuille(sx + 1.5f, y0 + 14f, w - 3f, 8f, plaque, 0.4f)
                    for (k in 0 until 4) {
                        ligne(sx + 2.4f, y0 + 16f + k * 1.4f, sx + w - 2.4f, y0 + 16f + k * 1.4f, mix(plaque, P.pres, 0.5f), 0.2f)
                    }
                }
            }
            val buis = duJour(0xFF4F7A35.toInt())
            pieceD("M-2 58.5L-2 50C-2 48.4 -0.6 47.6 1.4 47.6L33 47.6C35 47.6 36.4 48.6 36.4 50L36.4 58.5Z", buis)
            pieceD("M66 58.5L66 51C66 49.6 67 49 68.6 49L102 49L102 58.5Z", buis)
            c.restore()
        }

        /** La péniche sur la Moselle, sa proue en [x], [tangage] en unités. */
        fun peniche(x: Float, tangage: Float) {
            pieceD("M$x ${42.6f + tangage}L${x + 22f} ${42.6f - tangage}L${x + 20.6f} ${44.6f - tangage}L${x + 1.4f} ${44.6f + tangage}Z",
                duJour(0xFF2F4A3A.toInt()), 0.3f)
            feuille(x + 15.5f, 40.4f - tangage * 0.5f, 4f, 2.2f, duJour(0xFFEDE8DC.toInt()), 0.3f)
            if (P.allume) rond(x + 17f, 41.4f - tangage * 0.5f, 0.45f, P.fenetre)
        }

        // ---- Schueberfouer, la grande roue et le carrousel (photo du propriétaire)

        fun fouerPapier() {
            lisiere(-2f, 102f, { x -> 46f + sin(x * 0.1f) }, 1.2f, 1.8f, 165, P.loin)
            plan(1)
            val treillis = duJour(0xFF3FA05A.toInt())
            val blanc = duJour(0xFFF2F2EE.toInt())
            mineral {
                for (x0 in floatArrayOf(8f, 32f)) {
                    ligne(x0, 57f, ROUE[0], ROUE[1] + 1f, treillis, 1.3f)
                    p.pathEffect = DashPathEffect(floatArrayOf(0.6f, 0.9f), 0f)
                    ligne(x0, 57f, ROUE[0], ROUE[1] + 1f, blanc, 0.5f)
                    p.pathEffect = null
                }
                if (!vivant) grandeRoue(0f)
            }
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            val or = duJour(0xFFE2B04A.toInt())
            val rouge = duJour(0xFFC23B3B.toInt())
            val creme = duJour(0xFFF6EAD0.toInt())
            mineral {
                pieceD("M64 25C68 16 80 11.5 92 11.5C100 11.5 104 15 104 25Z", or)
                trait("M68 21C72 16.5 80 14 90 14", mix(or, 0xFFFFFFFF.toInt(), 0.35f), 0.4f)
                feuille(64f, 25f, 40f, 2.6f, rouge, 0.5f)
                val t = Path()
                t.moveTo(64f, 27.4f)
                var x = 64f
                while (x < 104f) { t.quadTo(x + 1.5f, 30.4f, x + 3f, 27.4f); x += 3f }
                t.lineTo(104f, 27f); t.lineTo(64f, 27f); t.close()
                piece(t, or, 0.4f)
                for (px in floatArrayOf(68f, 74f, 80f, 86f, 92f, 98f)) ligne(px, 29f, px, 52f, duJour(0xFFD9B45A.toInt()), 0.4f)
                pieceD("M62 52L104 52L104 55.5L62 55.5Z", rouge)
                feuille(62f, 52f, 42f, 0.9f, or, 0f)
                var bx = 65.5f
                while (bx < 104f) { rond(bx, 26.3f, 0.36f, if (P.allume) P.fenetre else creme); bx += 2.2f }
                // Une baraque, en bas à gauche.
                feuille(-2f, 44f, 15f, 14.5f, duJour(0xFFF3D9E2.toInt()))
                pieceD("M-3 44.4L5.5 40L14 44.4Z", rouge)
                feuille(0f, 46f, 11f, 3f, if (P.allume) P.fenetre else duJour(0xFFE8E2D0.toInt()), 0.3f)
            }
            if (!vivant) chevaux(0f)
            c.restore()
        }

        /** La grande roue, tournée de [angle] radians ; la nuit, ses ampoules courent au [temps]. */
        fun grandeRoue(angle: Float, temps: Float = 0f) {
            val cx = ROUE[0]; val cy = ROUE[1]; val rr = ROUE[2]
            // De trois quarts : la roue se voit en ellipse.
            c.save()
            c.scale(0.78f, 1f, cx, cy)
            try { roue(cx, cy, rr, angle, temps) } finally { c.restore() }
        }

        private fun roue(cx: Float, cy: Float, rr: Float, angle: Float, temps: Float) {
            val structure = duJour(0xFFE8E8E4.toInt())
            for (k in 0 until 24) {
                val a = angle + k / 24f * 2f * PI.toFloat()
                ligne(cx, cy, cx + rr * cos(a), cy + rr * sin(a), structure, 0.3f)
            }
            anneau(cx, cy, rr, structure, 0.8f)
            anneau(cx, cy, rr - 2f, structure, 0.35f)
            val disques = intArrayOf(duJour(0xFFF2C230.toInt()), duJour(0xFFE0483A.toInt()), duJour(0xFF3FA05A.toInt()))
            for ((i, d) in floatArrayOf(8f, 5.6f, 3.2f).withIndex()) rond(cx, cy, d, disques[i])
            if (P.allume) {
                val pas = (temps * 4f).toInt()
                for (k in 0 until 40) {
                    if ((k + pas) % 3 == 0) continue
                    val a = angle + k / 40f * 2f * PI.toFloat()
                    rond(cx + rr * cos(a), cy + rr * sin(a), 0.38f, if (k % 2 == 0) P.fenetre else 0xFFFF8FB8.toInt())
                }
            }
            for (k in 0 until 12) {
                val a = angle + k / 12f * 2f * PI.toFloat()
                val x = cx + rr * cos(a)
                val y = cy + rr * sin(a)
                ligne(x, y, x, y + 1.3f, structure, 0.25f)
                rect(x - 1.3f, y + 1.2f, 2.6f, 2f, duJour(GONDOLES[k % GONDOLES.size]))
            }
        }

        /** Les chevaux de bois du carrousel, qui montent et descendent au [temps]. */
        fun chevaux(temps: Float) {
            val blanc = duJour(0xFFF7F3EA.toInt())
            val selle = duJour(0xFFC23B3B.toInt())
            for ((k, x) in floatArrayOf(74f, 86f, 98f).withIndex()) {
                val y = 43f + sin(temps * 2f + k * 2.1f) * 1.4f
                pieceD("M${x - 3f} ${y}C${x - 3f} ${y - 1.6f} ${x + 2f} ${y - 1.8f} ${x + 2.6f} ${y - 0.8f}L${x + 3.6f} ${y - 3.4f}" +
                    "L${x + 4.6f} ${y - 3f}L${x + 3.6f} ${y + 0.2f}C${x + 3.2f} ${y + 1.4f} ${x - 2.4f} ${y + 1.6f} ${x - 3f} ${y}Z", blanc, 0.4f)
                ligne(x - 2f, y + 1f, x - 2.6f, y + 3.2f, blanc, 0.45f)
                ligne(x + 2f, y + 1f, x + 2.6f, y + 3.2f, blanc, 0.45f)
                rect(x - 1f, y - 1.5f, 1.8f, 0.8f, selle)
            }
        }

        // ---- Nei Bréck, le pont Adolphe (photo du propriétaire)

        fun neiBreck() {
            val r = Alea(175)
            val creme = duJour(0xFFE6DAC0.toInt())
            val toit = ardoise()
            val cuivre = duJour(0xFF5FA88A.toInt())
            enPerspective(30f, 25f, 0.06f) {
                mineral {
                    // La tour de la Spuerkeess et son dôme de cuivre, à gauche.
                    feuille(3.5f, 9f, 5.4f, 24f, creme)
                    pieceD("M3 9.2C3 4.8 9.4 4.8 9.4 9.2Z", cuivre)
                    feuille(5.7f, 2.4f, 1f, 2.6f, cuivre, 0.3f)
                    fenetre(5.4f, 13f, 1.4f, 2.4f, r); fenetre(5.4f, 19f, 1.4f, 2.4f, r)
                    // Le grand bâtiment et son pavillon central.
                    feuille(12f, 18f, 50f, 15f, creme)
                    pieceD("M11.4 18.2L13.4 14.6L60.6 14.6L62.6 18.2Z", toit)
                    feuille(32f, 13f, 10f, 20f, creme)
                    pieceD("M31.4 13.2C32 9 42 9 42.6 13.2Z", toit)
                    var fx = 13.5f
                    while (fx < 61f) { fenetre(fx, 21f, 1.1f, 1.9f, r); fenetre(fx, 26f, 1.1f, 1.9f, r); fx += 3f }
                }
            }
            lisiere(56f, 102f, { x -> 27f + sin(x * 0.3f) }, 1.4f, 2f, 176, mix(P.loin, P.milieu, 0.3f))
            plan(1)
            val pierre = duJour(0xFFD8C8A2.toInt())
            enPerspective(50f, 30f, 0.12f) {
                mineral {
                    pieceD("M-2 29.6L102 29.6L102 46L95.4 46C95.4 42.4 93 40.4 90.4 40.4C87.8 40.4 85.4 42.4 85.4 46L81.6 46" +
                        "C79.6 37.6 67.6 33.6 55 33.6C42.4 33.6 30.4 37.6 28.4 46L24.6 46C24.6 42.4 22.2 40.4 19.6 40.4" +
                        "C17 40.4 14.6 42.4 14.6 46L10.8 46C10.8 42.4 8.4 40.4 5.8 40.4C3.2 40.4 -2 42.4 -2 46Z", pierre)
                    feuille(24.6f, 46f, 3.8f, 20f, pierre)
                    feuille(81.6f, 46f, 3.8f, 20f, pierre)
                    feuille(-2f, 28.4f, 104f, 1.6f, mix(pierre, P.pres, 0.25f), 0.5f)
                    trait("M28.4 46C30.4 37.6 42.4 33.6 55 33.6C67.6 33.6 79.6 37.6 81.6 46", mix(pierre, P.pres, 0.35f), 0.5f)
                    for (lx in floatArrayOf(70f, 82f, 94f)) {
                        ligne(lx, 22.6f, lx, 28.4f, duJour(0xFF55585E.toInt()), 0.3f)
                        if (P.allume) rond(lx, 22.4f, 0.6f, P.fenetre)
                    }
                }
                if (!vivant) tram(4f)
            }
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            pieceD("M1 58.5L4 46L1.5 46L6 34L3.5 34L8 20L12.5 34L10 34L14.5 46L12 46L15 58.5Z", vert(0.05f))
            lisiere(-2f, 102f, { x -> 48f + 4f * sin(x * 0.09f) - max(0f, x - 66f) * 0.25f }, 2f, 3.2f, 177, vert(0.7f))
            touffe(vert(0.45f), 22f, 52f, 4f, 30f, 54f, 3.4f)
            c.restore()
        }

        /** Le tram sur le pont, son nez en [x]. */
        fun tram(x: Float, y: Float = 25.4f) {
            feuille(x, y, 15f, 3f, duJour(0xFF2C3A58.toInt()), 0.4f)
            rect(x + 0.8f, y + 0.5f, 13.4f, 1.1f, if (P.allume) P.fenetre else duJour(0xFFB8C8D8.toInt()))
            rect(x, y + 2.2f, 15f, 0.4f, duJour(0xFFC9D2DC.toInt()))
            if (P.allume) rond(x + 15f, y + 2f, 0.5f, 0xFFFFF4C8.toInt())
        }

        // ---- Belval, les hauts fourneaux (photo du propriétaire)

        fun belval() {
            val r = Alea(185)
            mineral {
                feuille(70f, 30f, 8f, 22f, duJour(0xFFB9C4CF.toInt()), 0.5f)
                feuille(86f, 26f, 10f, 26f, duJour(0xFFC9B9A9.toInt()), 0.5f)
            }
            plan(1)
            val brique = duJour(0xFFB8735A.toInt())
            val beton = duJour(0xFFBBAE98.toInt())
            val acier = duJour(0xFF5A4038.toInt())
            val rouille = duJour(0xFF8A5A3E.toInt())
            mineral {
                pieceD("M8 60L9.6 1.5L13.4 1.5L15 60Z", brique)
                for (y in floatArrayOf(8f, 16f, 24f)) ligne(9.4f, y, 13.6f, y, mix(brique, P.pres, 0.3f), 0.3f)
                // Les cowpers, à gauche.
                for ((x, w, y0) in listOf(Triple(17f, 8f, 14f), Triple(26f, 7f, 18f))) {
                    pieceD("M$x 60L$x ${y0 + w / 2f}C$x ${y0 - w * 0.1f} ${x + w} ${y0 - w * 0.1f} ${x + w} ${y0 + w / 2f}L${x + w} 60Z", beton)
                    feuille(x + w * 0.65f, y0 + 1f, w * 0.35f, 59f - y0, mix(beton, P.pres, 0.25f), 0f)
                }
                // Le haut fourneau : la charpente, le gueulard et les conduites.
                pieceD("M36 60L38 20L46 14L54 14L62 20L64 60Z", acier)
                feuille(46f, 4f, 8f, 10f, rouille)
                trait("M50 4C50 -2 62 -2 66 6L70 18", acier, 1.6f)
                trait("M46 8C40 6 36 8 34 14", acier, 1.4f)
                for (y in floatArrayOf(24f, 32f, 40f, 48f)) ligne(38f, y, 62f, y, rouille, 0.35f)
                // À droite, les cuves et la tour de charge.
                for ((x, w, y0) in listOf(Triple(66f, 8f, 20f), Triple(76f, 8f, 24f))) {
                    pieceD("M$x 60L$x ${y0 + 3f}L${x + w / 2f} ${y0}L${x + w} ${y0 + 3f}L${x + w} 60Z", beton)
                    feuille(x + w * 0.65f, y0 + 2f, w * 0.35f, 58f - y0, mix(beton, P.pres, 0.25f), 0f)
                }
                pieceD("M86 60L87 12L93 12L94 60Z", acier)
                for (y in floatArrayOf(18f, 26f, 34f, 42f, 50f)) {
                    ligne(87f, y, 93f, y + 8f, rouille, 0.3f); ligne(93f, y, 87f, y + 8f, rouille, 0.3f)
                }
                trait("M84 22L74 18L70 18", acier, 1.2f)
                if (P.allume) for (k in 0 until 9) rond(38f + r() * 56f, 14f + r() * 40f, 0.4f, P.fenetre)
            }
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            pieceD("M-2 60L-2 54L102 54L102 60Z", vert(0.6f), 0.4f)
            if (!vivant) for ((k, x) in BANNIERES.withIndex()) banniere(x, 0f, k)
            touffe(vert(0.4f), 22f, 50f, 3f, 26f, 51.5f, 2.6f)
            c.restore()
        }

        /** Une bannière blanche sur son mât en [x], gonflée de [vent]. */
        fun banniere(x: Float, vent: Float, k: Int) {
            ligne(x, 28f, x, DEVANT, duJour(0xFF8C9096.toInt()), 0.35f)
            val toile = duJour(if (k % 2 == 0) 0xFFF4F4F2.toInt() else 0xFFDCE6EE.toInt())
            pieceD("M${x + 0.3f} 29L${x + 3.4f + vent} 29.4L${x + 3.2f + vent * 1.4f} 43L${x + 0.3f} 43Z", toile, 0.4f)
        }

        // ---- Bäerdref, l'arche de grès dans la forêt (photo du propriétaire)

        fun baerdref() {
            val r = Alea(195)
            val feuillage = mix(vert(0.8f), 0xFF8FD05A.toInt(), if (P.nuit) 0.05f else 0.3f)
            lisiere(-2f, 102f, { x -> 6f + 3f * sin(x * 0.13f) }, 2.4f, 3.6f, 196, feuillage)
            for (k in 0 until 9) feuille(r() * 100f, 10f + r() * 10f, 0.6f + r() * 0.5f, 34f, mix(feuillage, P.pres, 0.45f), 0.3f)
            lisiere(-2f, 102f, { x -> 26f + 3f * sin(x * 0.11f) }, 2f, 3f, 197, mix(feuillage, P.milieu, 0.3f))
            plan(1)
            pieceD("M-2 66L-2 47C30 43 70 43 102 46L102 66Z", duJour(0xFFF0E2C2.toInt()), 0.5f)
            mineral { pieceD("M54 50C54 45 58 42.5 64 42.5C70 42.5 73 45.5 74 50Z", duJour(0xFF9A9684.toInt())) }
            pieceD("M55 46C57 43.5 60 42.6 64 42.6C68 42.6 71 43.6 72.6 46C68 44.6 60 44.6 55 46Z", duJour(0xFF6E8A44.toInt()), 0.3f)
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            val roche = duJour(0xFFC99A62.toInt())
            mineral {
                piece(cadre("M16 60L16 42C16 30 26 22 45 21C64 20.4 81 24 86 33L88 42L88 60Z"), sombre(roche, 0.5f))
                piece(cadre("M12.5 60L12.5 40C12.5 26 22.6 16.4 45 15.4C67 14.6 84.4 18.6 90.4 30L92.4 40L92.4 60Z"), roche)
                val strate = mix(roche, P.pres, 0.22f)
                for (y in floatArrayOf(6f, 11f)) ligne(-2f, y, 102f, y + 0.8f, strate, 0.3f, 0.7f)
                for (y in floatArrayOf(28f, 38f, 48f)) {
                    ligne(-2f, y, 12f, y + 0.5f, strate, 0.3f, 0.7f)
                    ligne(93f, y, 102f, y + 0.4f, strate, 0.3f, 0.7f)
                }
                pieceD("M84 60C84 51 88 45.6 95 45.6C101 45.6 104 51 104 60Z", duJour(0xFFA9A28C.toInt()))
            }
            val mousse = duJour(0xFF7C9A48.toInt())
            pieceD("M-2 2C20 0 60 -1 102 1L102 4C70 3 30 3.6 -2 5Z", mousse, 0.3f)
            touffe(mousse, 2f, 30f, 2.6f, 4f, 44f, 3f, 1f, 54f, 2.6f)
            pieceD("M85 49C88 46 92 45.6 96 45.6C100 45.6 102.6 47.6 103.4 50C98 48.6 90 48.6 85 49Z", mousse, 0.3f)
            c.restore()
        }

        // ---- Ansebuerg, les jardins baroques du Grand-Château (photo du propriétaire)

        fun ansebuerg() {
            val r = Alea(205)
            lisiere(-2f, 102f, { x -> 24f + 2.4f * sin(x * 0.07f + 0.6f) }, 1.6f, 2.4f, 206, mix(P.loin, P.milieu, 0.3f))
            plan(1)
            val ocre = duJour(0xFFDDB05A.toInt())
            val aile = duJour(0xFFEDE9E0.toInt())
            val toit = ardoise()
            val terrasse = duJour(0xFFD8CCAE.toInt())
            enPerspective(50f, 31f, 0.08f) {
                mineral {
                    feuille(16f, 22f, 18f, 10f, ocre)
                    pieceD("M15.4 22.2L17.6 19L32.4 19L34.6 22.2Z", toit)
                    feuille(62f, 18f, 26f, 14f, aile)
                    pieceD("M61.4 18.2L63.6 15L86 15L88.6 18.2Z", toit)
                    feuille(34f, 16f, 28f, 16f, ocre)
                    pieceD("M33.4 16.2L36 12.6L60 12.6L62.6 16.2Z", toit)
                    for (x in floatArrayOf(36f, 54f)) {
                        feuille(x, 10f, 6f, 22f, ocre)
                        pieceD("M${x - 0.6f} 10.2L${x + 3f} 2.4L${x + 6.6f} 10.2Z", toit)
                        ligne(x + 3f, 2.4f, x + 3f, 0.4f, toit, 0.25f)
                        for (y in floatArrayOf(14f, 20f, 26f)) fenetre(x + 2.4f, y, 1.2f, 2f, r)
                    }
                    for (fx in floatArrayOf(17.6f, 21f, 24.4f, 27.8f, 31.2f)) fenetre(fx, 25f, 1f, 1.8f, r)
                    for (fx in floatArrayOf(43.5f, 47f, 50.5f)) { fenetre(fx, 19f, 1.1f, 2f, r); fenetre(fx, 25f, 1.1f, 2f, r) }
                    var fx = 64f
                    while (fx < 87f) { fenetre(fx, 21f, 1f, 1.8f, r); fenetre(fx, 26f, 1f, 1.8f, r); fx += 3f }
                    // La terrasse, ses arcades et sa balustrade.
                    feuille(6f, 32f, 88f, 8f, terrasse)
                    var ax = 38f
                    while (ax < 62f) {
                        pieceD("M$ax 40L$ax 36C$ax 34.2 ${ax + 3.2f} 34.2 ${ax + 3.2f} 36L${ax + 3.2f} 40Z", mix(terrasse, P.pres, 0.45f), 0f)
                        ax += 5f
                    }
                    ligne(6f, 32.4f, 94f, 32.4f, mix(terrasse, P.pres, 0.3f), 0.4f)
                    var bx = 7f
                    while (bx < 94f) { ligne(bx, 31f, bx, 32.4f, terrasse, 0.3f); bx += 1.6f }
                    ligne(6f, 31f, 94f, 31f, terrasse, 0.4f)
                }
            }
            for (x in floatArrayOf(12f, 88f)) {
                pieceD("M$x 40L${x - 1.6f} 34C${x - 1.4f} 30 ${x - 0.4f} 27 $x 26C${x + 0.4f} 27 ${x + 1.4f} 30 ${x + 1.6f} 34Z", vert(0.15f))
            }
            touffe(vert(0.4f), 4f, 30f, 4f, 96f, 30f, 4f)
            pieceD("M-2 66L-2 40L102 40L102 66Z", duJour(0xFF8BAA4A.toInt()), 0.4f)
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            // La charmille taillée en arche, cadre de scène de feuillage.
            val haie = vert(0.25f)
            val charmille = cadre("M17 60L17 30C17 15 31 5.4 50 5.4C69 5.4 83 15 83 30L83 60Z")
            val feuilles = Path()
            for (k in 0..34) {
                val u = k / 34f
                val (px, py) = when {
                    u < 0.25f -> 17f to 58f - u / 0.25f * 28f
                    u > 0.75f -> 83f to 30f + (u - 0.75f) / 0.25f * 28f
                    else -> {
                        val a = PI.toFloat() * (1f + (u - 0.25f) / 0.5f)
                        (50f + 33f * cos(a)) to (30f + 24.6f * sin(a))
                    }
                }
                feuilles.addCircle(px, py, 1.4f + r() * 1f, Path.Direction.CW)
            }
            charmille.op(feuilles, Path.Op.UNION)
            piece(charmille, haie)
            val pierre = duJour(0xFFB3AD98.toInt())
            mineral {
                for ((x, s) in listOf(24f to 1f, 76f to -1f)) lion(x, DEVANT, s, pierre)
                feuille(36f, 56.4f, 28f, 2.4f, duJour(0xFF9C988C.toInt()), 0.4f)
            }
            c.restore()
        }

        /** Un lion de pierre couché sur son socle, tourné vers le centre de la scène ([s] : 1 ou -1). */
        fun lion(x: Float, bas: Float, s: Float, col: Int) {
            feuille(x - 4f, bas - 3f, 8f, 3f, mix(col, P.pres, 0.2f))
            pieceD("M${x - 3.6f * s} ${bas - 3f}C${x - 3.6f * s} ${bas - 6f} ${x + 0.4f * s} ${bas - 7f} ${x + 2.2f * s} ${bas - 6.2f}L${x + 3.6f * s} ${bas - 3f}Z", col)
            touffe(mix(col, P.pres, 0.12f), x + 2.4f * s, bas - 7.6f, 2.3f)
            touffe(col, x + 3.2f * s, bas - 7.2f, 1.4f)
        }

        // ---- Pafendall, l'ascenseur panoramique (photo du propriétaire)

        fun pafendall() {
            val r = Alea(215)
            lisiere(-2f, 102f, { x -> 40f + 2.4f * sin(x * 0.08f) }, 1.6f, 2.4f, 216, mix(P.loin, P.milieu, 0.3f))
            var x = 22f
            while (x < 76f) { maisonArdoise(x, 47f, 3.6f, 2.6f + r() * 1.4f, 1.8f, duJour(0xFFEFE2C8.toInt()), r); x += 4.6f }
            plan(1)
            lisiere(-2f, 102f, { x -> 49f + 2f * sin(x * 0.2f) }, 1.8f, 2.8f, 217, vert(0.4f))
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            val beton = duJour(0xFFEDEDEB.toInt())
            val gris = duJour(0xFF55595F.toInt())
            val verre = duJour(0xFF9FD3EC.toInt())
            enPerspective(80f, 12f, 0.07f) {
                mineral {
                    // La passerelle vers la ville haute.
                    feuille(-3f, 9.2f, 82f, 5.6f, mix(beton, P.pres, 0.1f))
                    feuille(-3f, 9.2f, 82f, 2.6f, verre, 0.3f)
                    feuille(-3f, 13.6f, 82f, 1.2f, gris, 0f)
                    var mx = 0f
                    while (mx < 79f) { ligne(mx, 9.2f, mx, 11.8f, gris, 0.18f); mx += 3f }
                    // La tour, ses lames, sa plateforme vitrée.
                    feuille(80f, -3f, 14f, 61.5f, beton)
                    feuille(91f, -3f, 3f, 61.5f, mix(beton, P.pres, 0.18f), 0f)
                    feuille(84.5f, 0f, 5f, 14f, mix(beton, gris, 0.35f), 0.2f)
                    var ly = 0.8f
                    while (ly < 14f) { ligne(84.5f, ly, 89.5f, ly, gris, 0.2f); ly += 1.1f }
                    feuille(94f, 17f, 9f, 7f, verre, 0.5f)
                    feuille(94f, 24f, 9f, 1.2f, gris, 0f)
                    ligne(79.3f, 14.8f, 79.3f, DEVANT, gris, 0.5f)
                }
            }
            if (!vivant) cabine(30f)
            touffe(vert(0.55f), 1f, 52f, 7f, 10f, 55f, 5f, -2f, 42f, 6f, 6f, 46f, 4f)
            c.restore()
        }

        /** La cabine vitrée de l'ascenseur, son haut en [y]. */
        fun cabine(y: Float) {
            val gris = duJour(0xFF55595F.toInt())
            feuille(74.2f, y, 5f, 6f, duJour(0xFF9FD3EC.toInt()), 0.4f)
            ligne(74.2f, y, 79.2f, y, gris, 0.35f)
            ligne(74.2f, y + 6f, 79.2f, y + 6f, gris, 0.35f)
            if (P.allume) rect(74.8f, y + 0.6f, 3.8f, 4.8f, P.fenetre, 0.6f)
        }

        /** Le bus qui passe sur la Rout Bréck, son nez en [x]. */
        fun bus(x: Float) {
            if (PAPIER) {
                // Sur le tablier qui monte : le bus suit la pente.
                c.save()
                c.rotate(Math.toDegrees(kotlin.math.atan(TABLIER_PENTE.toDouble())).toFloat(), x, tablier(x))
                busDroit(x, tablier(x) - 1.4f - 3.2f)
                c.restore()
                return
            }
            busDroit(x, 26.8f)
        }

        private fun busDroit(x: Float, y: Float) {
            feuille(x, y, 11f, 3.2f, duJour(0xFFF2F4F6.toInt()), 0.4f)
            rect(x + 0.6f, y + 0.6f, 9.8f, 1f, if (P.allume) P.fenetre else duJour(0xFF5A7896.toInt()))
            rect(x, y + 2.2f, 11f, 0.45f, duJour(0xFF2F6FB0.toInt()))
            if (P.allume) rond(x + 11f, y + 2.4f, 0.5f, 0xFFFFF4C8.toInt())
        }

        /** Un oiseau en vol, de [t] unités d'envergure, les ailes à [aile] (0 baissées, 1 levées). */
        fun oiseau(x: Float, y: Float, t: Float, aile: Float, col: Int) {
            if (!actif) return
            p.style = Paint.Style.STROKE
            p.strokeWidth = t * 0.22f
            p.strokeCap = Paint.Cap.ROUND
            teinte(col, 1f)
            val v = Path()
            v.moveTo(x - t, y - t * (0.2f + 0.6f * aile))
            v.quadTo(x - t * 0.4f, y - t * 0.5f * aile, x, y)
            v.quadTo(x + t * 0.4f, y - t * 0.5f * aile, x + t, y - t * (0.2f + 0.6f * aile))
            c.drawPath(v, p)
            p.style = Paint.Style.FILL
            p.strokeCap = Paint.Cap.BUTT
        }

        // ------------------------------------------- la troisième vague (2026-11-14)

        /** Un cumulus de papier : des bosses rondes sur une base plate, la face à l'ombre dessous. */
        fun cumulus(x: Float, y: Float, l: Float, graine: Int, teinte: Int? = null) {
            val r = Alea(graine)
            val t = Path()
            t.addRoundRect(RectF(x, y - l * 0.12f, x + l, y), l * 0.06f, l * 0.06f, Path.Direction.CW)
            var cx = x + l * 0.12f
            while (cx < x + l * 0.9f) {
                val rr = l * (0.1f + 0.14f * sin(PI.toFloat() * (cx - x) / l) + r() * 0.05f)
                t.addCircle(cx, y - rr * 0.8f, rr, Path.Direction.CW)
                cx += rr * (0.9f + r() * 0.3f)
            }
            val blanc = teinte ?: if (P.nuit) mix(P.cielHaut, 0xFFFFFFFF.toInt(), 0.3f) else 0xFFFFFFFF.toInt()
            piece(Path().apply { addRoundRect(RectF(x + l * 0.05f, y - l * 0.08f, x + l * 0.95f, y), l * 0.04f, l * 0.04f, Path.Direction.CW) },
                mix(blanc, P.cielHaut, 0.3f), 0.3f)
            piece(t, blanc, 0.4f)
        }

        /** Un pylône en treillis posé en [x] sur [bas], jusqu'à [haut], large de [w] au pied. */
        fun pylone(x: Float, bas: Float, haut: Float, w: Float, col: Int) {
            fun g(y: Float) = x - w / 2f + (w * 0.35f) * (bas - y) / (bas - haut)
            fun d(y: Float) = x + w / 2f - (w * 0.35f) * (bas - y) / (bas - haut)
            ligne(g(bas), bas, g(haut), haut, col, 0.35f)
            ligne(d(bas), bas, d(haut), haut, col, 0.35f)
            var y = bas
            val pas = 2.6f
            while (y - pas >= haut) {
                ligne(g(y), y, d(y - pas), y - pas, col, 0.18f)
                ligne(d(y), y, g(y - pas), y - pas, col, 0.18f)
                y -= pas
            }
            ligne(g(haut) - 0.8f, haut + 2f, d(haut) + 0.8f, haut + 2f, col, 0.3f)
        }

        /** Un mouton à tête noire en ([x], [y]), de taille [s] ; [broute] de 0 (tête levée) à 1 (tête dans l'herbe). */
        fun mouton(x: Float, y: Float, s: Float, broute: Float, versDroite: Boolean = true) {
            val laine = duJour(0xFFF1ECE0.toInt())
            val tete = duJour(0xFF2E2A28.toInt())
            val k = if (versDroite) 1f else -1f
            ligne(x - 1.2f * s, y + 0.8f * s, x - 1.2f * s, y + 2f * s, tete, 0.3f * s)
            ligne(x + 1.2f * s, y + 0.8f * s, x + 1.2f * s, y + 2f * s, tete, 0.3f * s)
            ovalePapier(x, y, 2.2f * s, 1.4f * s, laine, 0.3f)
            ovalePapier(x + k * 2.4f * s, y - 0.6f * s + broute * 1.4f * s, 0.8f * s, 0.6f * s, tete, 0f)
        }

        /**
         * Les lames d'un caillebotis qui fuit, entre ses bords [g] et [d]
         * (x selon y), de [bas] à [haut] : plus serrées au loin.
         */
        fun lames(g: (Float) -> Float, d: (Float) -> Float, bas: Float, haut: Float, col: Int) {
            for (k in 1..14) {
                val y = haut + (bas - haut) * (k / 14f).pow(1.7f)
                ligne(g(y), y, d(y), y, col, 0.15f + 0.2f * k / 14f, 0.8f)
            }
        }

        // ---- Felsmillen, les rochers de grès et le caillebotis (photo du propriétaire)

        fun felsmillen() {
            val r = Alea(225)
            val fond = mix(vert(0.8f), P.cielBas, 0.25f)
            lisiere(-2f, 102f, { x -> 8f + 3f * sin(x * 0.17f) }, 2.2f, 3.4f, 226, fond)
            for (k in 0 until 10) feuille(30f + r() * 72f, 4f + r() * 8f, 0.6f + r() * 0.5f, 40f, sombre(fond, 0.4f), 0.3f)
            plan(1)
            pieceD("M-2 66L-2 40C30 36 70 34 102 36L102 66Z", vert(0.75f), 0.5f)
            val gres = duJour(0xFFB7A889.toInt())
            val clair = mix(gres, 0xFFFFFFFF.toInt(), 0.18f)
            mineral {
                pieceD("M-3 66L-3 -3L34 -3C38 4 36 10 31 13C35 17 34 24 28 27C31 31 30 38 25 42L24 66Z", gres)
                pieceD("M8 6C16 3 26 4 31 10C25 11 16 11 8 9Z", clair, 0.4f)
                pieceD("M6 20C14 17 24 18 29 24C22 25 14 25 6 23Z", clair, 0.4f)
                pieceD("M4 34C12 31 20 32 25 38C19 39 11 39 4 37Z", clair, 0.4f)
                pieceD("M40 30C42 26 50 25 56 27C58 30 56 34 50 35C44 35 40 34 40 30Z", gres)
            }
            touffe(duJour(0xFF6E8A44.toInt()), 30f, 13f, 1.6f, 27f, 27f, 1.6f, 22f, 42f, 2f)
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            val bois = duJour(0xFFB08A60.toInt())
            pieceD("M58 58.5C66 48 78 40 84 34L90 34C88 40 96 50 102 54L102 58.5Z", bois)
            lames({ y -> 58f + (58.5f - y) / 24.5f * 26f }, { y -> min(102f, 102f - (58.5f - y) / 24.5f * 12f) }, 58.5f, 34f, sombre(bois, 0.35f))
            touffe(vert(0.65f), -2f, 56f, 4f, 6f, 57f, 3.4f, 14f, 57.5f, 3f, 24f, 58f, 2.6f)
            c.restore()
        }

        // ---- Ourdall, la passerelle le long du lac de l'Our (photos du propriétaire)

        fun ourdall() {
            val r = Alea(235)
            lisiere(-2f, 102f, { x -> 22f + 4f * sin(x * 0.05f + 0.5f) }, 2f, 3f, 236, mix(vert(0.4f), P.loin, 0.3f))
            pylone(8f, 26f, 15f, 3f, duJour(0xFF6A6E74.toInt()))
            plan(1)
            pieceD("M-2 66L-2 34C30 33 70 33 102 35L102 66Z", mix(P.eau, 0xFF6F8A60.toInt(), 0.35f), 0.4f)
            val clair = mix(P.eau, 0xFFFFFFFF.toInt(), if (P.nuit) 0.18f else 0.4f)
            for (k in 0 until 8) rect(r() * 60f, 36f + r() * 14f, 3f + r() * 6f, 0.3f, clair, 0.7f)
            lisiere(62f, 102f, { x -> 32f - (x - 62f) * 0.05f }, 1.6f, 2.4f, 237, vert(0.35f))
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            val bois = duJour(0xFFB9A07A.toInt())
            val gris = duJour(0xFF8A8E92.toInt())
            pieceD("M30 58.5L64 36L69 36L84 58.5Z", bois)
            lames({ y -> 30f + (58.5f - y) / 22.5f * 34f }, { y -> 84f - (58.5f - y) / 22.5f * 15f }, 58.5f, 36f, sombre(bois, 0.3f))
            // Le garde-corps de gauche : le grillage, puis la main courante.
            var x = 16f
            while (x < 64f) {
                val t = (x - 16f) / 48f
                ligne(x, 45f - t * 11.6f, x + 2.4f, 58.5f - t * 22f, gris, 0.12f)
                ligne(x + 2.4f, 45f - (t + 0.05f) * 11.6f, x, 58.5f - t * 22f, gris, 0.12f)
                x += 2.4f
            }
            pieceD("M14 42.6L64 32.4L64 33.6L16 45.2Z", bois)
            for (px in floatArrayOf(16f, 32f, 46f, 57f)) ligne(px, 45f - (px - 16f) / 48f * 11.6f, px, 58.5f - (px - 16f) / 48f * 22f, sombre(bois, 0.3f), 0.5f)
            // Celui de droite, et les arbres qui surplombent.
            pieceD("M98 41L70 32.6L70 33.8L98 43.4Z", bois)
            for (px in floatArrayOf(96f, 86f, 77f)) ligne(px, 42f - (98f - px) * 0.3f, px, 58.5f - (98f - px) * 0.85f, sombre(bois, 0.3f), 0.5f)
            touffe(vert(0.5f), 84f, 4f, 8f, 94f, 10f, 7f, 100f, 2f, 8f, 76f, -2f, 6f)
            c.restore()
        }

        // ---- Stauséi, le barrage de la Haute-Sûre et ses anses boisées (photos du propriétaire)

        fun stausei() {
            lisiere(-2f, 102f, { x -> 14f + 5f * cos((x - 55f) * 0.05f) }, 2f, 3f, 246, mix(P.loin, vert(0.3f), 0.4f))
            pieceD("M18 40L18 29C34 27.4 64 27.4 82 29L82 40Z", mix(P.eau, 0xFF4F9A80.toInt(), 0.35f), 0.3f)
            lisiere(-2f, 46f, { x -> 20f + x * 0.1f }, 1.8f, 2.6f, 247, vert(0.25f))
            lisiere(56f, 102f, { x -> 24f - (x - 56f) * 0.12f }, 1.8f, 2.6f, 248, vert(0.35f))
            plan(1)
            val beton = duJour(0xFFD8CDB4.toInt())
            mineral {
                pieceD("M14 36C30 33 60 33 92 36L92 44C60 40 30 40 14 44Z", beton)
                pieceD("M14 35C30 32 60 32 92 35L92 36.4C60 33.4 30 33.4 14 36.4Z", sombre(beton, 0.18f))
                for (x in floatArrayOf(28f, 42f, 56f, 70f, 84f)) ligne(x, 34.6f, x, 41.4f, sombre(beton, 0.25f), 0.25f)
                pieceD("M-2 50C6 46 14 44 20 46C24 47 26 50 24 52L-2 52Z", duJour(0xFFB9B0A0.toInt()))
            }
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            pieceD("M-2 66L-2 46C30 44 70 44 102 46L102 66Z", mix(P.eau, 0xFF3F8A6A.toInt(), 0.45f), 0.4f)
            // La passerelle d'Insenborn, en bas à droite.
            val bois = duJour(0xFFC9B79A.toInt())
            pieceD("M74 58.5L84 47L90 47L92 58.5Z", bois)
            pieceD("M72 55L83 44.4L83 46.4L74 57Z", sombre(bois, 0.15f))
            pieceD("M94 55L91 44.4L91 46.4L93 57Z", sombre(bois, 0.15f))
            touffe(vert(0.55f), 96f, 40f, 7f, 100f, 50f, 7f, 90f, 34f, 5f)
            touffe(vert(0.4f), -2f, 56f, 6f, 6f, 58f, 5f)
            c.restore()
        }

        // ---- Conzefenn, les tourbières de Troisvierges et leurs moutons (photo du propriétaire)

        fun conzefenn() {
            val r = Alea(255)
            val epiceas = sombre(vert(0.15f), 0.25f)
            val foret = Path()
            foret.moveTo(-3f, BAS + 2f)
            var x = -3f
            while (x <= 103f) {
                val base = 26f + (if (x < 40f) -4f * sin(x * 0.08f) else 1.5f * sin(x * 0.1f))
                foret.lineTo(x, base); foret.lineTo(x + 0.8f, base - 2.6f - r() * 1.2f)
                x += 1.6f
            }
            foret.lineTo(103f, BAS + 2f); foret.close()
            piece(foret, epiceas)
            plan(1)
            pieceD("M-2 66L-2 31C30 29.5 70 29.5 102 30.5L102 66Z", vert(0.85f), 0.4f)
            touffe(vert(0.45f), 22f, 30f, 2.4f, 26f, 29f, 2.6f, 74f, 30f, 2.4f, 78f, 30.5f, 2f)
            for (k in 0 until 18) mouton(r() * 100f, 33f + r() * 7f, 0.45f + r() * 0.15f, r(), r() < 0.5f)
            for (k in 0 until 10) rond(r() * 100f, 36f + r() * 6f, 0.4f, duJour(0xFFF2C230.toInt()))
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            pieceD("M-2 66L-2 44C30 42 70 42 102 44L102 66Z", vert(0.7f), 0.4f)
            if (!vivant) for ((k, mx) in MOUTONS.withIndex()) mouton(mx, 47f + (k % 2) * 3f, 1f, if (k % 2 == 0) 1f else 0f, k % 3 != 0)
            val bois = duJour(0xFF7A6248.toInt())
            for (px in floatArrayOf(10f, 46f, 70f, 92f)) feuille(px, 46f, 1.4f, 12.5f, bois, 0.6f)
            pieceD("M-2 51L102 48L102 49.2L-2 52.4Z", bois, 0.6f)
            pieceD("M60 55L102 52L102 53L60 56.2Z", bois, 0.6f)
            for (k in 0 until 14) rond(r() * 100f, 53f + r() * 5f, 0.5f, duJour(0xFFF2C230.toInt()))
            c.restore()
        }

        // ---- Haff Réimech, le pont couvert (photo du propriétaire)

        fun haffReimech() {
            lisiere(-2f, 102f, { x -> 16f + 3f * sin(x * 0.09f) }, 2f, 3.2f, 266, mix(vert(0.4f), P.loin, 0.35f))
            plan(1)
            pieceD("M-2 66L-2 46L102 46L102 66Z", mix(P.eau, 0xFF5F7046.toInt(), 0.4f), 0.4f)
            val pierre = duJour(0xFFA8A090.toInt())
            val bois = duJour(0xFF8C6A48.toInt())
            enPerspective(50f, 22f, 0.08f) {
                mineral {
                    for ((px, w) in listOf(10f to 10f, 78f to 12f)) {
                        feuille(px, 27f, w, 22f, pierre)
                        feuille(px + w * 0.65f, 27f, w * 0.35f, 22f, sombre(pierre, 0.2f), 0f)
                    }
                    pieceD("M-3 17L102 14L102 18.6L-3 21.6Z", sombre(duJour(0xFF7A6A58.toInt()), 0.25f))
                    pieceD("M-3 21.6L102 18.6L102 26L-3 28Z", bois)
                    var x = -2f
                    while (x < 101f) {
                        val y = 21.6f - (x + 3f) * 0.0286f
                        rect(x + 0.8f, y + 1.2f, 3f, 3.2f, sombre(bois, 0.6f))
                        if (P.allume && x.toInt() % 3 == 0) rect(x + 1.2f, y + 1.6f, 2.2f, 2.4f, P.fenetre, 0.5f)
                        x += 5f
                    }
                    pieceD("M-3 28L102 26L102 27.6L-3 29.6Z", sombre(bois, 0.35f))
                }
            }
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            pieceD("M-3 4C20 2 40 6 60 3C70 2 84 6 103 2L103 4.4C84 8 70 4.4 60 5.4C40 8.6 20 4.6 -3 6.6Z", duJour(0xFF7A7048.toInt()), 0.5f)
            touffe(vert(0.5f), 0f, 30f, 6f, 4f, 44f, 6f, -2f, 54f, 5f, 96f, 36f, 6f, 100f, 48f, 6f, 92f, 54f, 5f)
            c.restore()
        }

        // ---- Hohllay, la grotte de Berdorf (photo du propriétaire)

        fun hohllay() {
            val gres = duJour(0xFFC9895A.toInt())
            val rose = duJour(0xFFD9A58A.toInt())
            val creux = sombre(gres, 0.8f)
            mineral {
                feuille(-3f, -3f, 106f, 72f, creux, 0f)
                pieceD("M-3 10L103 10L103 52L-3 52Z", rose, 0.4f)
                for ((ax, w) in listOf(6f to 10f, 30f to 16f, 56f to 16f, 88f to 10f)) {
                    pieceD("M$ax 52L$ax ${44f - w * 0.4f}C$ax ${30f - w * 0.3f} ${ax + w} ${30f - w * 0.3f} ${ax + w} ${44f - w * 0.4f}L${ax + w} 52Z", creux, 0.3f)
                }
            }
            plan(1)
            mineral {
                for (px in floatArrayOf(18f, 76f)) {
                    pieceD("M$px 58L${px + 1f} 20C${px - 2f} 16 ${px + 10f} 16 ${px + 7f} 20L${px + 8f} 58Z", rose)
                    pieceD("M$px 58L${px + 0.6f} 46C${px + 3f} 44 ${px + 6f} 44 ${px + 8f} 46L${px + 8f} 58Z", duJour(0xFF8AA04A.toInt()), 0.3f)
                }
                pieceD("M-3 -3L103 -3L103 12C80 16 60 10 50 12C40 14 20 10 -3 14Z", gres)
            }
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            mineral {
                piece(cadre("M8 60L8 30C8 16 26 8 50 8C74 8 92 16 92 30L92 60Z"), gres)
                val trace = sombre(gres, 0.25f)
                for (k in 0 until 6) {
                    val x0 = -2f + k * 18f
                    trait("M$x0 6Q${x0 + 8f} -1 ${x0 + 16f} 6", trace, 0.35f, 0.8f)
                    trait("M${x0 + 2f} 4Q${x0 + 8f} -1.6 ${x0 + 14f} 4", trace, 0.25f, 0.7f)
                }
                for (y in floatArrayOf(20f, 30f, 40f)) { trait("M0 ${y}Q4 ${y - 4f} 8 $y", trace, 0.3f); trait("M92 ${y}Q96 ${y - 4f} 100 $y", trace, 0.3f) }
                pieceD("M-2 58.5C10 52 30 52 40 56L40 58.5Z", duJour(0xFF8E9A6A.toInt()))
                pieceD("M60 58.5C70 53 90 52 102 55L102 58.5Z", duJour(0xFF8E9A6A.toInt()))
            }
            c.restore()
        }

        // ---- Napoleonsgaart, la tour sur la butte (photo du propriétaire)

        fun napoleonsgaart() {
            cumulus(2f, 24f, 28f, 276)
            cumulus(38f, 16f, 32f, 277)
            cumulus(68f, 28f, 30f, 278)
            plan(1)
            pieceD("M-2 66L-2 44C20 42 40 40 54 38C64 36 76 36 86 38C94 40 98 42 102 43L102 66Z", vert(0.85f), 0.4f)
            pieceD("M56 44C66 35 80 33 92 35C96 37 100 41 102 44Z", vert(0.65f), 0.4f)
            val grille = duJour(0xFF9AA0A6.toInt())
            var gx = 40f
            while (gx < 102f) { ligne(gx, 37.6f, gx, 42f, grille, 0.15f); gx += 3f }
            ligne(40f, 38f, 102f, 37.4f, grille, 0.15f)
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            pieceD("M30 58.5C44 52 64 48 80 46L86 46C78 50 66 54 58 58.5Z", duJour(0xFF4A4C50.toInt()), 0.4f)
            val pierre = duJour(0xFF6E6862.toInt())
            mineral {
                feuille(74f, 48f, 24f, 10.5f, sombre(pierre, 0.1f))
                feuille(77f, 40f, 18f, 9f, pierre)
                // Vue de trois quarts : la face éclairée, puis celle de côté qui fuit.
                pieceD("M78.6 58.5L79.6 6L86 5L86.4 58.5Z", mix(pierre, 0xFFFFFFFF.toInt(), 0.08f))
                pieceD("M86.4 58.5L86 5L92 6.6L93 58.5Z", sombre(pierre, 0.3f))
                pieceD("M79 4.4L86 3.2L92.6 5L86 6.4Z", sombre(pierre, 0.15f), 0.4f)
                val joint = sombre(pierre, 0.3f)
                var y = 8f
                while (y < 40f) { ligne(79.8f, y, 86f, y - 0.9f, joint, 0.12f, 0.7f); ligne(86f, y - 0.9f, 92.4f, y + 0.7f, joint, 0.12f, 0.5f); y += 1.8f }
                for (fy in floatArrayOf(15f, 27f)) {
                    for (fx in floatArrayOf(81f, 83.4f)) rect(fx, fy - (fx - 79f) * 0.15f, 1.3f, 1.6f, if (P.allume) P.fenetre else sombre(pierre, 0.6f))
                    for (fx in floatArrayOf(87.6f, 90f)) rect(fx, fy - 0.9f + (fx - 86f) * 0.27f, 1.1f, 1.6f, if (P.allume) P.fenetre else sombre(pierre, 0.7f))
                }
                feuille(84.4f, 50f, 3.2f, 8.5f, duJour(0xFF2F5FB0.toInt()), 0.3f)
                val fer = duJour(0xFF55595F.toInt())
                ligne(80f, 2.4f, 92f, 2.4f, fer, 0.2f)
                for (fx in floatArrayOf(80f, 83f, 86f, 89f, 92f)) ligne(fx, 2.4f, fx, 4.6f, fer, 0.15f)
                ligne(86f, 4.6f, 86f, -1f, fer, 0.25f)
            }
            pylone(5f, DEVANT, -2f, 4f, duJour(0xFF55595F.toInt()))
            touffe(vert(0.4f), 100f, 44f, 5f, 96f, 50f, 4f)
            c.restore()
        }

        // ---- Kallektuffquell, la source de tuf (photo du propriétaire)

        fun kallektuffquell() {
            lisiere(40f, 102f, { x -> 10f + 3f * sin(x * 0.2f) }, 2f, 3f, 286, vert(0.6f))
            plan(1)
            val r = Alea(287)
            val tuf = duJour(0xFF8C7458.toInt())
            val mousse = duJour(0xFF8FB040.toInt())
            mineral {
                pieceD("M-3 -3L70 -3C78 6 86 14 96 18L103 20L103 38L-3 38Z", tuf)
                var x = 2f
                while (x < 98f) { ligne(x, 24f + r() * 4f, x + r(), 36f, sombre(tuf, 0.5f), 0.5f, 0.6f); x += 3f }
                feuille(-3f, 24f, 106f, 2.4f, sombre(tuf, 0.45f), 0f)
            }
            pieceD("M-3 -3L66 -3C70 6 64 14 56 20C44 26 24 24 10 26C4 27 0 26 -3 27Z", mousse)
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            val bassin = mix(duJour(0xFF6FB8B0.toInt()), P.eau, 0.2f)
            pieceD("M-3 66L-3 37L103 37L103 66Z", bassin, 0.4f)
            rect(-3f, 38f, 106f, 0.5f, mix(bassin, 0xFFFFFFFF.toInt(), 0.5f), 0.7f)
            val galet = duJour(0xFFD8D2C4.toInt())
            for (k in 0 until 10) {
                val u = k / 9f
                ovalePapier(-1f + u * 40f, 50f + u * u * 8f, 2.6f, 1.6f, galet)
            }
            touffe(vert(0.5f), 96f, 40f, 4f, 100f, 46f, 5f)
            c.restore()
        }

        // ---- Péitrusdall, la vallée de la Pétrusse sous le pont Adolphe (photo du propriétaire)

        fun peitrusdall() {
            lisiere(16f, 84f, { x -> 34f + 2f * sin(x * 0.2f) }, 2f, 3f, 296, mix(vert(0.4f), P.loin, 0.35f))
            plan(1)
            val pierre = duJour(0xFFD3C29C.toInt())
            mineral {
                pieceD("M-3 6L86 5L86 10L-3 11Z", pierre)
                feuille(-3f, 5f, 89f, 0.8f, sombre(pierre, 0.25f), 0f)
                pieceD("M-3 10.6L50 10.2L46 13C30 14 14 26 8 46L-3 46Z", pierre)
                pieceD("M8 46C14 26 30 13 46 12.6C62 12 76 18 86 28L82 30C74 21 62 16 46 16.4C32 17 18 28 12 46Z", sombre(pierre, 0.12f))
                for (ax in floatArrayOf(2f, 10f, 18f, 26f)) {
                    val h = 12f + (30f - ax) * 0.3f
                    pieceD("M$ax ${h + 6f}L$ax ${h + 2f}C$ax ${h - 0.6f} ${ax + 5f} ${h - 0.6f} ${ax + 5f} ${h + 2f}L${ax + 5f} ${h + 6f}Z", sombre(pierre, 0.45f), 0f)
                }
                for (lx in floatArrayOf(14f, 40f, 66f)) { ligne(lx, 1f, lx, 5f, duJour(0xFF55585E.toInt()), 0.25f); if (P.allume) rond(lx, 0.8f, 0.6f, P.fenetre) }
            }
            if (!vivant) tram(54f, 2.2f)
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            pieceD("M-3 58.5L-3 44C30 40 70 40 103 42L103 58.5Z", vert(0.8f), 0.4f)
            pieceD("M60 58.5C66 50 70 44 72 38L75 38C76 44 84 52 96 58.5Z", duJour(0xFF6A6C70.toInt()), 0.4f)
            mineral { pieceD("M90 58.5L92 40C96 38 100 38 103 39L103 58.5Z", duJour(0xFFA59A86.toInt())) }
            touffe(vert(0.3f), -2f, 20f, 8f, 6f, 30f, 9f, 2f, 44f, 8f, 14f, 40f, 6f)
            touffe(vert(0.45f), 98f, 10f, 8f, 90f, 20f, 9f, 100f, 32f, 8f, 84f, 30f, 6f)
            c.restore()
        }

        // ---- Wollefsschlucht, la gorge du Loup (photo du propriétaire)

        fun wollefsschlucht() {
            val r = Alea(305)
            feuille(-3f, -3f, 106f, 72f, mix(P.cielBas, 0xFFFFF8D8.toInt(), if (P.nuit) 0.05f else 0.4f), 0f)
            val houppier = mix(vert(0.8f), 0xFFE8E070.toInt(), if (P.nuit) 0.05f else 0.3f)
            for (k in 0 until 7) feuille(30f + r() * 40f, 4f, 0.6f + r() * 0.5f, 36f, mix(houppier, P.pres, 0.4f), 0.3f)
            lisiere(-2f, 102f, { x -> 6f + 3f * sin(x * 0.2f) }, 2f, 3f, 306, houppier)
            plan(1)
            val gres = duJour(0xFF9C8460.toInt())
            val mousse = duJour(0xFF5F8A3C.toInt())
            val rouille = duJour(0xFFC9784A.toInt())
            mineral {
                pieceD("M-3 -3L30 -3C28 10 34 20 30 30C27 40 32 50 34 66L-3 66Z", gres)
                pieceD("M103 -3L70 -3C72 10 66 22 70 32C73 42 68 52 66 66L103 66Z", gres)
                pieceD("M6 44C12 40 20 42 26 48C20 50 12 50 6 48Z", rouille, 0.3f)
                pieceD("M80 34C86 30 94 32 100 38C94 40 86 40 80 38Z", rouille, 0.3f)
            }
            pieceD("M-3 20C8 16 20 18 28 26C20 28 8 30 -3 32Z", mousse, 0.4f)
            pieceD("M103 12C92 8 80 12 72 20C80 22 92 24 103 26Z", mousse, 0.4f)
            touffe(mousse, 4f, 56f, 4f, 12f, 60f, 4f, 96f, 52f, 4f, 88f, 58f, 4f)
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            val bois = duJour(0xFFB0805A.toInt())
            pieceD("M30 58.5L47 30L53 30L70 58.5Z", bois)
            lames({ y -> 30f + (58.5f - y) / 28.5f * 17f }, { y -> 70f - (58.5f - y) / 28.5f * 17f }, 58.5f, 30f, sombre(bois, 0.3f))
            for (s in floatArrayOf(1f, -1f)) {
                fun x(v: Float) = if (s > 0f) v else 100f - v
                pieceD("M${x(16f)} 44L${x(46f)} 25L${x(46f)} 26.4L${x(18f)} 46.6Z", sombre(bois, 0.1f))
                for (k in 0..4) {
                    val t = k / 4f
                    val px = 17f + t * 29f
                    val haut = 45f - t * 19f
                    val bas = 58.5f - t * 28.5f
                    ligne(x(px), haut, x(px), bas, sombre(bois, 0.25f), 0.5f - t * 0.25f)
                    if (k < 4) {
                        val nx = 17f + (t + 0.25f) * 29f
                        ligne(x(px), haut, x(nx), 58.5f - (t + 0.25f) * 28.5f, sombre(bois, 0.2f), 0.25f)
                    }
                }
            }
            c.restore()
        }

        // ---- Priedegtstull, la falaise et ses escaliers (photo du propriétaire)

        fun priedegtstull() {
            val r = Alea(315)
            val foret = mix(vert(0.6f), P.cielBas, 0.25f)
            lisiere(-2f, 64f, { x -> 8f + 3f * sin(x * 0.15f) }, 2f, 3f, 316, foret)
            for (k in 0 until 9) feuille(r() * 60f, 2f, 0.5f + r() * 0.4f, 40f, sombre(foret, 0.35f), 0.3f)
            plan(1)
            pieceD("M-3 66L-3 30C10 28 26 34 40 40C46 44 50 52 50 66Z", duJour(0xFF8A6A50.toInt()), 0.4f)
            val marche = duJour(0xFF6A6058.toInt())
            mineral { for (k in 0 until 12) feuille(16f + k * 1.2f, 56f - k * 2.2f, 6f, 0.9f, marche, 0.3f) }
            pieceD("M-3 66L-3 36C4 34 10 38 14 44L16 66Z", duJour(0xFF5F8A3C.toInt()))
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            val gres = duJour(0xFFC8B89A.toInt())
            mineral {
                pieceD("M58 58.5L60 -3L103 -3L103 58.5Z", gres)
                for (y in floatArrayOf(4f, 10f, 17f, 25f, 34f, 44f)) {
                    ligne(59.6f, y, 103f, y + 0.6f, sombre(gres, 0.2f), 0.25f, 0.7f)
                    ligne(59.6f, y + 1.2f, 103f, y + 1.8f, mix(gres, 0xFFFFFFFF.toInt(), 0.2f), 0.4f, 0.6f)
                }
                pieceD("M78 58.5L80 24C81 20 83 18 84 22L86 58.5Z", sombre(gres, 0.7f))
                pieceD("M94 20C98 18 102 18 103 20L103 30C99 29 96 26 94 20Z", duJour(0xFFC9A060.toInt()), 0.3f)
            }
            c.restore()
        }

        // ---- Dumongshaff, la passerelle sur l'étang (photo du propriétaire)

        fun dumongshaff() {
            lisiere(-2f, 102f, { x -> 30f + 2f * sin(x * 0.1f) }, 1.6f, 2.4f, 326, mix(vert(0.5f), P.loin, 0.4f))
            val ecorce = duJour(0xFF6A5A4A.toInt())
            trait("M6 40L7 12M7 22L2 14M7 18L12 8M7 28L13 22", ecorce, 0.6f)
            plan(1)
            pieceD("M-3 66L-3 40C30 38 70 38 103 40L103 66Z", vert(0.85f), 0.4f)
            pieceD("M-3 58.5L-3 44C10 42 30 44 50 47C70 50 80 54 90 58.5Z", mix(P.eau, 0xFF6A7A50.toInt(), 0.45f), 0.4f)
            feuille(95f, 18f, 1.4f, 24f, ecorce, 0.5f)
            touffe(vert(0.55f), 92f, 14f, 7f, 100f, 22f, 8f, 86f, 24f, 6f)
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            val bois = duJour(0xFF9C8A70.toInt())
            fun pont(x: Float) = 46f - 18f * (1f - ((x - 53f) / 43f).pow(2))
            enPerspective(53f, 40f, 0.1f) {
                pieceD("M10 46C30 36 70 34 96 40L96 42.4C70 37 30 39 10 48.4Z", sombre(bois, 0.2f))
                pieceD("M10 38C30 28 70 26 96 32L96 33.6C70 27.8 30 29.8 10 39.6Z", bois)
                var x = 12f
                while (x < 96f) { ligne(x, pont(x) - 8f + 0.6f, x, pont(x), bois, 0.45f); x += 2.4f }
                feuille(8f, 36f, 2f, 14f, sombre(bois, 0.1f))
                feuille(95f, 30f, 2f, 14f, sombre(bois, 0.1f))
            }
            val roseau = vert(0.6f)
            val rr = Alea(327)
            for (k in 0 until 40) {
                val rx = rr() * 100f
                if (rx in 30f..70f && rr() < 0.6f) continue
                val h = 4f + rr() * 6f
                ligne(rx, DEVANT, rx + (rr() - 0.5f) * 2f, DEVANT - h, roseau, 0.3f)
            }
            c.restore()
        }
        // ---- Rout Bréck, le pont Grande-Duchesse Charlotte vu d'en bas (photo du propriétaire)

        /** Le haut du garde-corps de la Rout Bréck en [x] : le tablier monte vers la droite. */
        fun tablier(x: Float) = (x + 3f) * TABLIER_PENTE

        fun routBreckPapier() {
            val r = Alea(335)
            mineral {
                feuille(89f, 26f, 4f, 12f, mix(P.loin, P.cielBas, 0.3f), 0.4f)
                feuille(94.5f, 29f, 3f, 9f, mix(P.loin, P.cielBas, 0.4f), 0.4f)
            }
            lisiere(-2f, 102f, { x -> 34f + 3f * sin(x * 0.07f) }, 2f, 3f, 336, mix(vert(0.3f), P.loin, 0.35f))
            plan(1)
            lisiere(-2f, 102f, { x -> 42f + 4f * sin(x * 0.09f + 1f) }, 2.4f, 3.6f, 337, vert(0.45f))
            maisonArdoise(30f, 50f, 5f, 3f, 2f, duJour(0xFFE8E2D6.toInt()), r)
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            val rouge = duJour(0xFFC94459.toInt())
            val fer = duJour(0xFF55595F.toInt())
            mineral {
                // Les jambes en V, à gauche, sous le tablier.
                pieceD("M14 58.5L22 58.5L6 8L-1 9Z", sombre(rouge, 0.22f))
                pieceD("M-3 51L4 58.5L40 14L33 9.5Z", rouge)
                // Le tablier, sa face et son dessous.
                pieceD("M-3 0L103 ${tablier(103f)}L103 ${tablier(103f) + 6f}L-3 6.5Z", rouge)
                pieceD("M-3 6.5L103 ${tablier(103f) + 6f}L103 ${tablier(103f) + 8.6f}L-3 9.4Z", sombre(rouge, 0.4f))
                ligne(-3f, tablier(-3f) - 1.4f, 103f, tablier(103f) - 1.4f, fer, 0.3f)
                var x = 0f
                while (x <= 100f) { ligne(x, tablier(x) - 1.4f, x, tablier(x), fer, 0.12f); x += 1.6f }
                for (k in 0..7) {
                    val lx = 6f + k * 13f
                    ligne(lx, tablier(lx) - 1.4f, lx, tablier(lx) - 3.6f, fer, 0.2f)
                    if (P.allume) rond(lx, tablier(lx) - 3.8f, 1.6f, P.fenetre, 0.3f)
                    rond(lx, tablier(lx) - 3.8f, 0.45f, if (P.allume) 0xFFFFF4C8.toInt() else duJour(0xFFE8E2D0.toInt()))
                }
            }
            touffe(vert(0.55f), 86f, 46f, 8f, 96f, 40f, 8f, 100f, 52f, 6f, 78f, 54f, 6f)
            c.restore()
        }

        // ---- Breedewee, la rue Large et sa porte (photo du propriétaire)

        fun breedeweePapier() {
            val r = Alea(355)
            val enduit = duJour(0xFFB9B2A6.toInt())
            val ocre = duJour(0xFFD3C29A.toInt())
            val creme = duJour(0xFFEDE4D0.toInt())
            mineraux++
            // La grande façade du fond, ses fenêtres cintrées et son oculus.
            feuille(22f, -3f, 42f, 46f, enduit)
            for (fx in floatArrayOf(28f, 38f, 52f)) for (fy in floatArrayOf(3f, 14f)) {
                pieceD("M$fx ${fy + 6f}L$fx ${fy + 1.6f}C$fx ${fy - 0.4f} ${fx + 3.2f} ${fy - 0.4f} ${fx + 3.2f} ${fy + 1.6f}L${fx + 3.2f} ${fy + 6f}Z",
                    if (P.allume && r() < 0.6f) P.fenetre else creme, 0.3f)
                ligne(fx + 1.6f, fy + 0.2f, fx + 1.6f, fy + 6f, sombre(enduit, 0.4f), 0.15f)
            }
            anneau(46f, 27f, 1.6f, creme, 0.5f)
            plan(1)
            // Les façades de droite, qui montent avec la rue.
            feuille(60f, -3f, 21f, 61.5f, ocre)
            feuille(81f, -3f, 22f, 61.5f, enduit)
            for (fy in floatArrayOf(4f, 16f, 28f)) {
                for (fx in floatArrayOf(64f, 72f, 85f, 94f)) {
                    feuille(fx, fy, 3.6f, 6f, creme, 0.3f)
                    rect(fx + 0.5f, fy + 0.5f, 2.6f, 5f, if (P.allume && r() < 0.5f) P.fenetre else sombre(enduit, 0.45f))
                }
            }
            pieceD("M64 52L64 43C64 40 69 40 69 43L69 52Z", sombre(ocre, 0.5f), 0.3f)
            if (!vivant) lanterne(81f, 36f, false)
            for (y in floatArrayOf(46f, 49f, 52f, 55f)) ligne(81f, y, 103f, y, sombre(enduit, 0.35f), 0.3f)
            ligne(81f, -3f, 81f, 58.5f, sombre(enduit, 0.5f), 0.4f)
            // La rue pavée qui monte vers le fond.
            pieceD("M14 66C30 52 44 45 54 41L60 41C60 47 62 55 64 66Z", duJour(0xFF8C8478.toInt()), 0.3f)
            for (k in 0 until 30) rond(20f + r() * 40f, 44f + r() * 14f, 0.35f, sombre(duJour(0xFF8C8478.toInt()), 0.3f), 0.7f)
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            val pierre = duJour(0xFFB7AA90.toInt())
            val mur = duJour(0xFFA89A80.toInt())
            pieceD("M-3 -3L8 -3L12 58.5L-3 58.5Z", mur)
            for (y in floatArrayOf(6f, 14f, 22f, 30f, 38f, 46f, 54f)) ligne(-3f, y, 8f + y * 0.065f, y + 0.4f, sombre(mur, 0.3f), 0.2f)
            // La porte : son passage sombre, ses piédroits, son arc à bossages.
            pieceD("M14 58.5L14 25C14 18 26 18 26 25L26 58.5Z", sombre(enduit, 0.65f), 0f)
            feuille(10f, 24f, 4f, 34.5f, pierre)
            feuille(26f, 24f, 4f, 34.5f, pierre)
            for (y in floatArrayOf(28f, 33f, 38f, 43f, 48f, 53f)) {
                ligne(10f, y, 14f, y, sombre(pierre, 0.3f), 0.2f); ligne(26f, y, 30f, y, sombre(pierre, 0.3f), 0.2f)
            }
            pieceD("M10 25C10 13 30 13 30 25L26 25C26 18 14 18 14 25Z", pierre)
            for (k in 0..6) {
                val a = PI.toFloat() * (1f + k / 6f)
                ligne(20f + cos(a) * 6f, 25f + sin(a) * 6f, 20f + cos(a) * 10f, 25f + sin(a) * 10f, sombre(pierre, 0.35f), 0.2f)
            }
            mineraux--
            // Les lanternes vacillent sur la carte ouverte : [Vie] les y pose.
            if (!vivant) lanterne(30f, 31f, true)
            touffe(vert(0.5f), 4f, 57f, 2.4f, 9f, 57.5f, 2f)
            c.restore()
        }

        // ---- Gëlle Fra, la statue dorée sur son obélisque, au couchant (photo du propriétaire)

        fun gelleFraPapier() {
            val rose = mix(P.cielBas, 0xFFF0A070.toInt(), if (P.nuit) 0.05f else 0.35f)
            val mauve = mix(P.cielHaut, 0xFFB08AA8.toInt(), if (P.nuit) 0.1f else 0.35f)
            cumulus(-8f, 30f, 46f, 345, mauve)
            cumulus(30f, 18f, 44f, 346, rose)
            cumulus(58f, 40f, 50f, 347, mauve)
            plan(1)
            lisiere(-2f, 102f, { x -> 53f + sin(x * 0.3f) }, 1.4f, 2f, 348, vert(0.4f))
            val r = Alea(349)
            // La ville basse, en rue qui fuit vers le pied du monument.
            val murs = intArrayOf(duJour(0xFFE8E0D0.toInt()), duJour(0xFFE9D6B4.toInt()), duJour(0xFFF1ECE2.toInt()),
                duJour(0xFFDCD3C4.toInt()))
            rue3q(RUE_GELLE_FRA, MAISONS_GELLE_FRA, murs, r)
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            val granit = duJour(0xFF3A3A40.toInt())
            mineral {
                pieceD("M76 58.5L77.4 38L88.6 38L90 58.5Z", granit)
                feuille(84.6f, 38f, 4f, 20.5f, sombre(granit, 0.25f), 0f)
                feuille(76.4f, 36.6f, 13.2f, 1.6f, mix(granit, 0xFFFFFFFF.toInt(), 0.1f), 0.5f)
                statue(83f, 36.6f)
            }
            c.restore()
        }

        /** La Gëlle Fra, les pieds en ([x], [pied]) : sa robe qui flotte, ses bras levés, la couronne. */
        fun statue(x: Float, pied: Float) {
            val or = if (P.nuit) 0xFFFFD36A.toInt() else 0xFFE2AC36.toInt()
            val pli = mix(or, 0xFF6A4A10.toInt(), 0.35f)
            if (P.allume) rond(x, pied - 18f, 14f, or, 0.12f)
            pieceD("M${x - 2.2f} ${pied}C${x - 3f} ${pied - 6f} ${x - 2.6f} ${pied - 14f} ${x - 1.8f} ${pied - 20f}" +
                "L${x + 1.8f} ${pied - 20f}C${x + 2.4f} ${pied - 14f} ${x + 4.4f} ${pied - 6f} ${x + 3.8f} ${pied}Z", or, 0.5f)
            trait("M${x - 0.6f} ${pied - 18f}C${x - 1f} ${pied - 10f} ${x - 0.4f} ${pied - 4f} ${x - 0.8f} ${pied}", pli, 0.25f)
            trait("M${x + 1f} ${pied - 17f}C${x + 1.6f} ${pied - 10f} ${x + 2.4f} ${pied - 4f} ${x + 2.6f} ${pied}", pli, 0.25f)
            pieceD("M${x - 1.8f} ${pied - 19.8f}L${x - 1.4f} ${pied - 25f}L${x + 1.4f} ${pied - 25f}L${x + 1.8f} ${pied - 19.8f}Z", or, 0.4f)
            rond(x, pied - 26.6f, 1.3f, or)
            for (s in floatArrayOf(-1f, 1f)) {
                ligne(x + s * 1.3f, pied - 24.6f, x + s * 2.6f, pied - 29.5f, or, 0.9f)
                ligne(x + s * 2.6f, pied - 29.5f, x + s * 1.2f, pied - 31.4f, or, 0.75f)
            }
            trait("M${x - 2.4f} ${pied - 31.8f}Q$x ${pied - 33.6f} ${x + 2.4f} ${pied - 31.8f}Q$x ${pied - 30.4f} ${x - 2.4f} ${pied - 31.8f}", pli, 0.5f)
        }

        // ------------------------------------------------------ les lieux

        /** Veianen : le château sur son éperon, la ville basse et l'Our. */
        fun veianen() {
            if (PAPIER) { veianenPapier(); return }
            val r = Alea(3)
            crete(33f, 2.4f, 5, P.loin)
            plan(1)
            forme("M0 66L0 45C14 41 30 36 42 32C52 29 66 29 76 32C86 35 94 40 100 42L100 66Z", P.milieu)
            val pi = P.pierre
            val om = mix(P.pierre, P.pres, 0.3f)
            mineral {
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
            }
            bosquet(30f, 44f, 34.5f, 2.4f, 8, mix(P.milieu, P.pres, 0.4f))
            bosquet(80f, 100f, 37f, 2.6f, 9, mix(P.milieu, P.pres, 0.4f))
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            forme("M0 66L0 52C20 48 40 50 60 49C80 48 92 50 100 52L100 66Z", P.pres)
            var x = 14f
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
            if (PAPIER) { routBreckPapier(); return }
            val r = Alea(4)
            val fl = mix(P.loin, P.cielBas, 0.25f)
            val tours = floatArrayOf(8f, 4f, 15f, 13f, 3f, 11f, 18f, 5f, 19f, 26f, 3.4f, 13f, 31f, 4.2f, 16f, 86f, 4f, 12f, 92f, 3f, 9f)
            var i = 0
            mineral {
                while (i < tours.size) {
                    val x = tours[i]; val w = tours[i + 1]; val h = tours[i + 2]
                    rect(x, 38f - h, w, h, fl)
                    if (P.allume) {
                        var y = 40f - h
                        while (y < 36f) { if (r() < 0.55f) rect(x + 0.8f, y, w - 1.6f, 0.5f, P.fenetre, 0.7f); y += 2.2f }
                    }
                    i += 3
                }
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
            if (PAPIER) { eschSauerPapier(); return }
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
            mineral {
                rect(47.5f, 25f, 5f, 12f, om)
                var x = 47.5f
                while (x < 52.5f) { rect(x, 23.8f, 0.8f, 1.4f, om); x += 1.4f }
                fenetre(49.4f, 28f, 1f, 2f, r)
            }
            val maisons = floatArrayOf(40f, 48f, 4f, 3.5f, 44f, 45f, 3.8f, 3.2f, 48.5f, 43.5f, 4f, 3.4f, 53f, 44.5f, 3.6f, 3.2f,
                57f, 47f, 4f, 3.4f, 36f, 52f, 4.2f, 3.6f, 61f, 51f, 4f, 3.6f, 45.5f, 50.5f, 4f, 3.6f, 52f, 51f, 4.2f, 3.8f)
            var i = 0
            while (i < maisons.size) { maison(maisons[i], maisons[i + 1], maisons[i + 2], maisons[i + 3], r); i += 4 }
            mineral { rect(58.4f, 38f, 2.2f, 6.8f, P.pierre); triangle(58.2f, 38f, 59.5f, 33f, 60.8f, 38f, P.toit) }
            sapins(P.sol, 4f, DEVANT, 9f, 9f, DEVANT, 7f, 94f, DEVANT, 8f, 89f, DEVANT, 6.5f)
            c.restore()
        }

        /** La Musel : les vignes en terrasses, le village au bord de l'eau. */
        fun musel() {
            if (PAPIER) { muselPapier(); return }
            val r = Alea(6)
            crete(30f, 2f, 20, P.loin, x0 = 30f)
            plan(1)
            plancher(56f, DEVANT)
            poser(56f)
            rect(0f, 40f, 100f, 7f, P.milieu)
            var x = 52f
            while (x < 100f) { maison(x, 45f, 3.4f, 2.6f + r() * 1.6f, r); x += 4.2f }
            mineral { rect(76.4f, 34f, 2.4f, 11f, P.pierre); triangle(76.1f, 34f, 77.6f, 27f, 79.1f, 34f, P.toit) }
            fenetre(77.2f, 37f, 0.8f, 1.6f, r)
            riviere(46.5f, 56f, 21, 56f, 64f, 77f, 90f)
            mineral {
                forme("M62 52.6L86 52.6L84 54.4L64 54.4Z", P.sol)
                rect(80f, 51f, 3.2f, 1.8f, P.sol)
            }
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
            if (PAPIER) { neimensterPapier(); return }
            val r = Alea(7)
            crete(32f, 1.8f, 23, P.loin, x0 = 30f)
            plan(1)
            val roc = mix(P.pierre, P.pres, 0.42f)
            val roc2 = mix(roc, P.sol, 0.3f)
            bosquet(80f, 100f, 36f, 2.6f, 24, mix(P.milieu, P.pres, 0.3f))
            mineral {
                forme("M0 66L0 11L9 9.6L16 12L24 11.4L31 15L37 24L40 36L42 66Z", roc)
                var y = 16f
                while (y < 60f) { trait("M0 ${y}L${30f + y * 0.22f} ${y + 0.8f}", roc2, 0.4f, 0.7f); y += 4.5f }
                val rempart = mix(P.pierre, P.pres, 0.2f)
                rect(0f, 6.5f, 30f, 4f, rempart)
                var x = 0f
                while (x < 30f) { rect(x, 5.4f, 1.2f, 1.3f, rempart); x += 2.2f }
                rect(21f, 2f, 4.5f, 6f, rempart)
            }
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            mineral {
                forme("M37 37.5L93 37.5L91 34L39 34Z", P.toit)
                rect(38f, 37.5f, 54f, 13f, P.pierre)
                rect(38f, 47.5f, 54f, 3f, mix(P.pierre, P.pres, 0.25f))
                var x = 40f
                while (x < 91f) { fenetre(x, 39.4f, 1.3f, 2.2f, r); fenetre(x, 43.6f, 1.3f, 2.2f, r); x += 3.6f }
                rect(45f, 21f, 6.4f, 29.5f, P.pierre)
                rect(49f, 21f, 2.4f, 29.5f, mix(P.pierre, P.pres, 0.3f), 0.6f)
                triangle(44.6f, 21f, 48.2f, 11f, 51.8f, 21f, P.toit)
                rond(48.2f, 24.5f, 1.3f, mix(P.pierre, P.pres, 0.45f))
                fenetre(47.5f, 28f, 1.4f, 3f, r)
            }
            riviere(53.5f, DEVANT, 25, 42f, 52f, 63f, 74f, 85f)
            mineral {
                forme("M52 52L100 52L100 56.2L97 56.2C97 54 93 54 93 56.2L85 56.2C85 54 81 54 81 56.2L73 56.2C73 54 69 54 69 56.2L61 56.2C61 54 57 54 57 56.2L52 56.2Z",
                    mix(P.pierre, P.pres, 0.15f))
            }
            c.restore()
        }

        /** La Gëlle Fra : l'obélisque et sa statue dorée, le pont Adolphe derrière. */
        fun gelleFra() {
            if (PAPIER) { gelleFraPapier(); return }
            crete(34f, 1.4f, 27, P.loin)
            plan(1)
            val pi = mix(P.pierre, P.pres, 0.22f)
            var x = 14f
            mineral {
                rect(-1f, 22f, 102f, 2.6f, pi)
                trait("M6 54Q50 -6 94 54", pi, 3f)
                while (x <= 86f) {
                    val t = (x - 6f) / 88f
                    val y = (1 - t) * (1 - t) * 54f + 2 * (1 - t) * t * (-6f) + t * t * 54f
                    if (y > 24.5f) rect(x - 0.35f, 24.4f, 0.7f, y - 24.4f, pi)
                    x += 4.5f
                }
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
            mineral {
                rect(75f, 52f, 14f, 6.5f, ob); rect(76.5f, 49f, 11f, 3.2f, ob); rect(83f, 49f, 4.5f, 9.5f, obo, 0.5f)
                forme("M79.2 49L80.4 12L83.6 12L84.8 49Z", ob)
                forme("M82.4 12L83.6 12L84.8 49L82.6 49Z", obo, 0.55f)
                triangle(80f, 12f, 82f, 9.6f, 84f, 12f, ob)
            }
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
            if (PAPIER) { fouerPapier(); return }
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
            if (PAPIER) { mellerdallPapier(); return }
            val r = Alea(10)
            crete(30f, 2.2f, 33, P.loin)
            feuillagePeint(0f, 100f, 34f, 2.2f, 34, mix(P.loin, P.milieu, 0.5f))
            plan(1)
            // Le grès en pièces étagées (maquette `peint.js`, `mellerdallPeint`) :
            // une masse, une face à l'ombre, une face claire, des strates
            // collées, peintes de touches courtes et obliques. La face à
            // l'ombre tire vers la tuile plutôt que vers le vert : du grès,
            // pas du kaki.
            val roc = mix(P.pierre, 0xFFB48A55.toInt(), if (P.nuit) 0.15f else 0.32f)
            val roco = mix(roc, P.toit, 0.38f)
            val clair = mix(roc, 0xFFFFFFFF.toInt(), 0.22f)
            val mousse = mix(P.milieu, 0xFF4F7D3A.toInt(), if (P.nuit) 0.1f else 0.35f)
            mineral {
                carton("M0 66L0 13C4 9 12 9 16 13L18 30C17 40 20 52 23 66Z", roc, OBLIQUE)
                carton("M11 12C14 12 16 13 16 13L18 30C17 40 20 52 23 66L15 66C14 50 13 30 11 12Z", mix(roc, roco, 0.6f), OBLIQUE, 0.7f)
                carton("M1 14C4 11 8 10.5 10 11.5L9 28C8 38 9 50 10 66L3 66C3 50 2 30 1 14Z", clair, OBLIQUE, 0.5f)
                carton("M100 66L100 17C96 11 86 11 82 17L80 33C82 44 78 55 79 66Z", roc, OBLIQUE)
                carton("M82 17L80 33C82 44 78 55 79 66L86 66C85 50 86 30 84 15Z", mix(roc, roco, 0.6f), OBLIQUE, 0.7f)
                carton("M99 18C97 14 93 13 91 13.5L92 30C93 42 92 54 92 66L98 66C98 50 99 32 99 18Z", clair, OBLIQUE, 0.5f)
                for (y in floatArrayOf(24f, 37f, 50f)) {
                    carton("M0 ${y}L${16f + y * 0.08f} ${y + 0.6f}L${16.4f + y * 0.08f} ${y + 2.4f}L0 ${y + 2f}Z", mix(roc, roco, 0.35f), 0f, 0.6f)
                    carton("M${81f - y * 0.02f} ${y + 1f}L100 ${y + 1.6f}L100 ${y + 3.4f}L${80.6f - y * 0.02f} ${y + 3f}Z", mix(roc, roco, 0.35f), 0f, 0.6f)
                }
            }
            feuillagePeint(0f, 16f, 12f, 2.2f, 35, mousse)
            feuillagePeint(83f, 100f, 15f, 2.2f, 36, mousse)
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            val blanc = if (P.nuit) 0xFF9FB3D8.toInt() else 0xFFFFFFFF.toInt()
            mineral {
                forme("M36 33L64 33L64 38.5L56 38.5C56 35.6 44 35.6 44 38.5L36 38.5Z", mix(P.pierre, P.pres, 0.2f))
                rect(44.6f, 37.5f, 10.8f, 7f, blanc, 0.85f)
                forme("M38 44.5L62 44.5L64 46.5L36 46.5Z", roco)
                rect(40f, 46.5f, 20f, 6f, blanc, 0.8f)
                forme("M32 52.5L68 52.5L70 54.5L30 54.5Z", roco)
            }
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
            if (PAPIER) { stadPapier(); return }
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
            mineral {
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
         * parallaxe la plus lisible qui soit. Depuis le chantier gouache
         * (maquette `breedewee.js`, 2026-11-01), elle est élargie en
         * avant-scène autour du dessin du mot, qui tient dans 17–83 ×
         * 12,5–58,5 : ses piliers restent hors de cette boîte, son arc en
         * anse de panier passe au-dessus, et un lierre fleuri retombe de ses
         * épaules. Sa taille reste à revoir avec le propriétaire.
         *
         * Une ruelle de pierre reste beige, même vive : ses façades prennent
         * les couleurs du Grund ([FACADES]), et la ville haute en garde un
         * reflet. Derrière le dessin du mot, dans l'axe, la ruelle n'a pas de
         * fenêtre, seulement une porte basse. La ruelle et la porte tiennent
         * debout chacune dans sa rainure du [plancher] ; la chaussée pavée de
         * la maquette n'est pas reprise (règle 4 bis de la skill : rien ne
         * descend jusqu'au bas de la fenêtre).
         */
        fun breedewee() {
            if (PAPIER) { breedeweePapier(); return }
            val r = Alea(12)
            val pierre = P.pierre
            val joint = mix(pierre, P.pres, 0.42f)
            val couleurs = FACADES[P.rang]
            // Tout est pierre et enduit, sauf la verdure du rocher et le lierre :
            // la nuit, les façades bleues tomberaient dans la végétation.
            mineraux++

            // La ville haute, au-dessus de la ruelle : des pignons, et l'église
            // dans leur creux, dont le clocher passe derrière la clé de la porte.
            val pignons = floatArrayOf(0f, 7f, 9f, 6f, 9f, 12f, 15f, 8f, 8f, 23f, 7f, 10f, 30f, 9f, 8f, 39f, 6f, 11f,
                45f, 10f, 9f, 66f, 8f, 11f, 74f, 9f, 8f, 83f, 7f, 10f, 90f, 10f, 9f)
            var i = 0
            while (i < pignons.size) {
                val x = pignons[i]; val w = pignons[i + 1]; val h = pignons[i + 2]
                rect(x, 33f - h, w, h + 33f, mix(couleurs[(i / 3 + 2) % 5], pierre, 0.35f))
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
            // La verdure du rocher, devant la ville haute.
            mineraux--
            bosquet(0f, 54f, 33f, 2.6f, 51, P.milieu)
            bosquet(66f, 100f, 33f, 2.6f, 52, P.milieu)
            mineraux++

            // La ruelle derrière la porte : des façades enduites aux couleurs
            // du Grund.
            plan(1)
            val facades = floatArrayOf(-1f, 12f, 16f, 11f, 11f, 14f, 22f, 12f, 17f, 34f, 12f, 15f, 46f, 10f, 18f,
                56f, 12f, 14f, 68f, 11f, 17f, 79f, 11f, 15f, 90f, 11f, 16f)
            i = 0
            while (i < facades.size) {
                val x = facades[i]; val w = facades[i + 1]; val h = facades[i + 2]
                rect(x, 50f - h, w, h, couleurs[(i / 3) % 5])
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
            plancher(50f, DEVANT)

            // La porte, sur le devant de la scène : deux piliers et un arc en
            // anse de panier, d'un seul carton.
            plan(2)
            val cx = 50f
            val naissance = 30f
            val ix = 34.5f
            val iy = 21f
            val ex = 41.5f
            val ey = 27f
            val seuil = DEVANT
            plancher(seuil, BAS)
            val porte = Path()
            porte.moveTo(cx - ex, seuil)
            porte.lineTo(cx - ex, naissance)
            porte.arcTo(RectF(cx - ex, naissance - ey, cx + ex, naissance + ey), 180f, 180f)
            porte.lineTo(cx + ex, seuil)
            porte.lineTo(cx + ix, seuil)
            porte.lineTo(cx + ix, naissance)
            porte.arcTo(RectF(cx - ix, naissance - iy, cx + ix, naissance + iy), 0f, -180f)
            porte.lineTo(cx - ix, seuil)
            porte.close()
            forme(porte, pierre)
            // Les tailloirs et les socles, qui débordent : ils font la silhouette.
            rect(cx - ex - 1.2f, naissance - 0.2f, ex - ix + 2.4f, 1.8f, pierre)
            rect(cx + ix - 1.2f, naissance - 0.2f, ex - ix + 2.4f, 1.8f, pierre)
            rect(cx - ex - 0.8f, seuil - 2.2f, ex - ix + 1.6f, 2.2f, pierre)
            rect(cx + ix - 0.8f, seuil - 2.2f, ex - ix + 1.6f, 2.2f, pierre)
            // Les claveaux, et la clé qui dépasse de l'arc.
            val n = 15
            val pi = PI.toFloat()
            fun px(rx: Float, a: Float) = cx + rx * cos(a)
            fun py(ry: Float, a: Float) = naissance + ry * sin(a)
            for (k in 1 until n) {
                val a = pi + k * pi / n
                ligne(px(ix, a), py(iy, a), px(ex, a), py(ey, a), joint, 0.4f)
            }
            val demi = pi / n / 2f
            val haut = 1.5f * pi
            val angles = floatArrayOf(haut - demi * 0.9f, haut - demi * 1.15f, haut + demi * 1.15f, haut + demi * 0.9f)
            val rx = floatArrayOf(ix - 0.6f, ex + 1.4f, ex + 1.4f, ix - 0.6f)
            val ry = floatArrayOf(iy - 0.6f, ey + 1.4f, ey + 1.4f, iy - 0.6f)
            val cle = Path()
            cle.moveTo(px(rx[0], angles[0]), py(ry[0], angles[0]))
            for (q in 1..3) cle.lineTo(px(rx[q], angles[q]), py(ry[q], angles[q]))
            cle.close()
            forme(cle, pierre)
            ligne(px(rx[0], angles[0]), py(ry[0], angles[0]), px(rx[1], angles[1]), py(ry[1], angles[1]), joint, 0.4f)
            ligne(px(rx[3], angles[3]), py(ry[3], angles[3]), px(rx[2], angles[2]), py(ry[2], angles[2]), joint, 0.4f)
            // Le lierre fleuri qui retombe des épaules de l'arche : vert à
            // toute heure, assombri le soir et la nuit.
            mineraux--
            val lr = Alea(53)
            val lierre = mix(0xFF4CB35A.toInt(), P.pres, if (P.nuit) 0.6f else if (P.allume) 0.4f else 0.15f)
            val lierreSombre = mix(lierre, 0xFF000000.toInt(), 0.25f)
            for (gauche in booleanArrayOf(true, false)) {
                val sx = if (gauche) -1f else 1f
                for (k in 0 until 9) {
                    val t = k / 8f
                    val a = pi + (if (gauche) 0.15f + t * 0.55f else pi - 0.15f - t * 0.55f)
                    val x = px(ex + 0.5f, a)
                    val y = py(ey + 0.5f, a)
                    rond(x + (lr() - 0.5f) * 2f, y + (lr() - 0.5f) * 2f, 1.6f + lr() * 1.2f, if (k % 2 == 1) lierre else lierreSombre)
                }
                for (k in 0 until 4) rond(cx + sx * (ex - 1f + lr() * 2f), naissance + 2f + k * 3f + lr(), 1.2f + lr() * 0.8f, lierreSombre)
                for (k in 0 until 6) {
                    val a = pi + (if (gauche) 0.2f + lr() * 0.5f else pi - 0.2f - lr() * 0.5f)
                    rond(px(ex + 1f, a), py(ey + 1f, a) + lr() * 2f, 0.55f, couleurs[if (k % 2 == 1) 0 else 1])
                }
            }
            mineraux++
            // Les assises des piliers, joints croisés.
            for (gauche in booleanArrayOf(true, false)) {
                val xa = if (gauche) cx - ex else cx + ix
                val w = ex - ix
                var decale = gauche
                var y = naissance + 1.6f + 3.4f
                while (y < seuil - 2.2f) {
                    ligne(xa, y, xa + w, y, joint, 0.4f)
                    val xj = xa + w * (if (decale) 0.38f else 0.62f)
                    ligne(xj, y - 3.4f, xj, y, joint, 0.4f)
                    decale = !decale
                    y += 3.4f
                }
            }
            // Les lanternes, sous l'arc, tournées vers la scène.
            lanterne(cx + ix, naissance + 4f, false)
            lanterne(cx - ix, naissance + 4f, true)
            mineraux--
        }
    }
}
