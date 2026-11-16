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

    /**
     * De combien la végétation est ramenée vers son [neutre] : 0 la laisse
     * sous tout le `raviver` de [VIF_RETENU], 1 l'en sort complètement.
     * Voir [Peintre.vert].
     */
    const val VERT_CALME = 0.55f

    /**
     * Le vert de référence de la végétation : un feuillage franc et profond,
     * vers lequel [Peintre.vert] tire toutes ses nuances. Il était plus clair
     * et plus jaune (`#6FA845`), et les lieux paraissaient secs. C'est un
     * vert de forêt, demandé par le propriétaire le 2026-11-15 : « je
     * voudrais un vert forêt, végétation ».
     */
    private const val VERDURE = 0xFF35772E.toInt()

    /** L'horloge des planches de relecture, en secondes ; `null` pour la vraie. */
    @Volatile internal var horlogeEssai: Float? = null

    @Volatile internal var VIF = VIF_RETENU

    /**
     * La couleur à peindre pour que [col] ressorte telle quelle après
     * [raviver] : l'inverse exact de son réchauffement (gains et décalages
     * par canal, contraste compris) et de sa saturation. Pour la pierre et
     * les enduits des bâtiments, que le réchauffement faisait tous virer au
     * jaune paille (propriétaire, 2026-11-14 : « ce jaune IA ») ; le ciel et
     * la végétation gardent leur chaleur.
     */
    internal fun neutre(col: Int): Int {
        val v = VIF
        if (v <= 0f) return col
        val contraste = 1f + 0.1f * v
        val decale = (1f - contraste) * 128f
        val gains = floatArrayOf(contraste * (1f + 0.07f * v), contraste * (1f + 0.015f * v), contraste * (1f - 0.09f * v))
        val decales = floatArrayOf(decale + 6f * v, decale + 2f * v, decale - 4f * v)
        val c = floatArrayOf(((col shr 16) and 0xFF).toFloat(), ((col shr 8) and 0xFF).toFloat(), (col and 0xFF).toFloat())
        for (i in 0..2) c[i] = (c[i] - decales[i]) / gains[i]
        // La saturation inverse, autour de la même luminance que setSaturation.
        val sat = 1f + 0.45f * v
        val lum = 0.213f * c[0] + 0.715f * c[1] + 0.072f * c[2]
        for (i in 0..2) c[i] = lum + (c[i] - lum) / sat
        fun o(x: Float) = x.roundToInt().coerceIn(0, 255)
        return (col and 0xFF000000.toInt()) or (o(c[0]) shl 16) or (o(c[1]) shl 8) or o(c[2])
    }

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

    /**
     * Les trois chutes du Schéissendëmpel : milieu, largeur au bord, lèvre
     * et pied. Ni la même largeur ni la même hauteur : trois colonnes
     * identiques se lisaient comme trois projecteurs, pas comme de l'eau.
     * Deux tombent sous la grande arche du pont, la troisième sous la petite.
     */
    private val CHUTES_MELLERDALL = arrayOf(
        floatArrayOf(23f, 3.8f, 37.4f, 48.8f),
        floatArrayOf(47.6f, 2.6f, 38.6f, 49.4f),
        floatArrayOf(80f, 4.8f, 36.4f, 48.2f))

    /**
     * Les blocs de grès du lit, entre les chutes et sur les deux rives :
     * des masses anguleuses, pas des dômes. Arrondies, elles se lisaient
     * comme des buissons taillés.
     */
    private val ROCHERS_MELLERDALL = arrayOf(
        "M13 52.4L13.6 39.4L16 38L19.6 38.2L21.6 40.6L21.4 52.4Z",
        "M25 51.8L25.6 35L31 32.6L40 32.4L44.6 34.8L45 51.8Z",
        "M50.4 51.4L51 39.6L56 37.4L70 37.2L75.6 39.6L76.4 51.4Z",
        "M83.4 51.8L84 37.6L88 35.2L95 35.4L103 38L103 51.8Z",
        "M-3 53.6L-3 44.4L3 42.6L8.6 43L11.4 45.6L11.8 53.6Z",
        "M90.4 53.2L91 45.4L95 43.6L100.6 44L103 46.4L103 53.2Z")

    /** Les coiffes de mousse sur l'arête supérieure de ces blocs. */
    private val MOUSSES_MELLERDALL = arrayOf(
        "M13.6 39.4L16 38L19.6 38.2L21.6 40.6L21.4 42.4L19.6 40.2L16.2 40L13.8 41.4Z",
        "M25.6 35L31 32.6L40 32.4L44.6 34.8L44.4 37.6L39.8 35.4L31.2 35.6L25.8 37.8Z",
        "M51 39.6L56 37.4L70 37.2L75.6 39.6L75.4 42.2L69.8 40L56.2 40.2L51.2 42.2Z",
        "M84 37.6L88 35.2L95 35.4L103 38L103 40.8L94.6 38.4L88.2 38.2L84.2 40Z",
        "M-3 44.4L3 42.6L8.6 43L11.4 45.6L11.2 47.4L8.4 45.2L3.2 45.4L-3 47Z",
        "M91 45.4L95 43.6L100.6 44L103 46.4L103 48.2L100.4 46L95.2 46L91.2 47.4Z")

    /** La grande roue de la Schueberfouer : centre x, y, rayon. */
    private val ROUE = floatArrayOf(10f, 26f, 23f)

    /** La rosace du moyeu de la grande roue, du bord vers le centre. */
    private val ROSACE = intArrayOf(0xFF2E74B5.toInt(), 0xFF4FA85C.toInt(),
        0xFFF2C230.toInt(), 0xFFD6453A.toInt())

    /** L'abscisse du mât du carrousel, au bord droit de la fenêtre. */
    private const val CARROUSEL = 103f

    /** Les trois chevaux du carrousel que l'on voit passer. */
    private val CHEVAUX = floatArrayOf(86f, 94.5f, 102f)

    /** Les pattes d'un cheval de bois : attache, genou, sabot, en unités. */
    private val PATTES = arrayOf(
        floatArrayOf(-2.6f, 0.6f, -3.8f, 1.8f, -3f, 3.4f),
        floatArrayOf(-1.6f, 0.9f, -2.4f, 2.3f, -1.8f, 3.9f),
        floatArrayOf(2.2f, 0.2f, 3.6f, 1.6f, 3f, 3.4f),
        floatArrayOf(1.2f, 0.8f, 2f, 2.2f, 1.4f, 3.8f))

    /** Les guirlandes au-dessus de l'allée : départ, sommet de la courbe, arrivée. */
    private val GUIRLANDES = arrayOf(
        floatArrayOf(-4f, 3.4f, 34f, 14f, 68f, 4.2f),
        floatArrayOf(12f, 7f, 58f, 17f, 104f, 8f))

    /** Les couleurs des nacelles de la grande roue. */
    private val GONDOLES = intArrayOf(0xFFD6453A.toInt(), 0xFFE9B02F.toInt(), 0xFF2F9C95.toInt(),
        0xFF5A6FD1.toInt(), 0xFFD4569A.toInt(), 0xFF3F9A4F.toInt())

    /**
     * Les maisons du Grund vues de trois quarts ([Peintre.rue3q]), de la plus
     * proche à la plus lointaine : largeur le long de la rue et hauteur (au
     * premier plan), mur, colombages (1), hauteur du toit, lucarnes (1).
     * Étroites et hautes, comme les maisons du quai, mais pas au point de
     * passer devant le rocher : à 27 unités, leur faîtage montait plus haut
     * que la crête, et la falaise, le rempart et la ville haute
     * disparaissaient derrière elles. Le Grund, ce sont des maisons au pied
     * d'une falaise, pas devant un mur.
     */
    private val MAISONS_GRUND_3Q = arrayOf(
        floatArrayOf(14f, 19.5f, 2f, 0f, 4.4f, 1f), floatArrayOf(10f, 16f, 1f, 1f, 3.7f, 0f),
        floatArrayOf(13f, 20f, 0f, 0f, 4.8f, 1f), floatArrayOf(10f, 15.4f, 3f, 0f, 3.7f, 0f),
        floatArrayOf(14f, 18f, 2f, 1f, 4.4f, 1f), floatArrayOf(11f, 16.6f, 1f, 0f, 4f, 0f),
        floatArrayOf(13f, 19.5f, 3f, 0f, 4.4f, 1f), floatArrayOf(11f, 16f, 0f, 1f, 4f, 0f),
        floatArrayOf(13f, 18f, 2f, 0f, 4.4f, 1f), floatArrayOf(11f, 16.6f, 1f, 0f, 4f, 0f),
        floatArrayOf(14f, 18.8f, 3f, 1f, 4.4f, 1f), floatArrayOf(12f, 17.4f, 0f, 0f, 4f, 0f),
        floatArrayOf(13f, 18f, 2f, 0f, 4.4f, 1f), floatArrayOf(12f, 16.6f, 1f, 0f, 4f, 0f)
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

    /**
     * Le village d'Esch-sur-Sûre, **en bas à droite** comme sur la photo du
     * propriétaire (2026-11-15), en deux planches de maisons de trois quarts
     * qui fuient vers le fond de la boucle. C'étaient deux rangées de trente
     * maisons minuscules qui barraient le milieu de la fenêtre : peu de
     * maisons et grandes se lisent, beaucoup et petites font une frise.
     */
    internal val RUE_ESCH_HAUT = Fuite(floatArrayOf(60f, 46f), floatArrayOf(210f, 38f), floatArrayOf(-60f, 43f), taille = 0.6f)
    internal val RUE_ESCH_BAS = Fuite(floatArrayOf(56f, 56f), floatArrayOf(210f, 44f), floatArrayOf(-60f, 52f), taille = 0.78f)
    // Des maisons de village et non d'avenue : un corps bas et un grand
    // comble d'ardoise percé de lucarnes, comme sur la photo. Elles sont
    // écrites à la main plutôt que tirées au hasard, pour que le rapport du
    // toit au mur soit celui-là et pas celui d'un immeuble.
    private val MAISONS_ESCH_HAUT = arrayOf(
        floatArrayOf(13f, 10f, 0f, 0f, 7f, 1f), floatArrayOf(11f, 9f, 1f, 0f, 6.4f, 0f),
        floatArrayOf(14f, 10.5f, 2f, 0f, 7.4f, 1f), floatArrayOf(12f, 9.5f, 3f, 0f, 6.6f, 0f)
    )
    private val MAISONS_ESCH_BAS = arrayOf(
        floatArrayOf(15f, 11f, 0f, 0f, 7.6f, 1f), floatArrayOf(12f, 9.5f, 1f, 0f, 6.8f, 0f),
        floatArrayOf(14f, 10.5f, 2f, 0f, 7.2f, 1f)
    )

    /** Le village de l'autre rive de la Moselle. */
    internal val RUE_MUSEL = Fuite(floatArrayOf(63f, 32.5f), floatArrayOf(175f, 28.5f), floatArrayOf(-80f, 31f), taille = 0.2f)
    private val MAISONS_MUSEL = maisonsAuHasard(26, 157, 12f, 18f)

    /**
     * Le parvis de Schengen fuit vers la gauche, la stèle la plus proche à
     * droite. Sur la photo du propriétaire les trois tôles ne sont pas
     * étalées de face sur toute la largeur : c'est un groupe serré vu de
     * biais, dont chacune montre sa tranche et sa seconde tôle à droite.
     */
    internal val FUITE_STELES = Fuite(
        floatArrayOf(90f, 57f), floatArrayOf(4f, 41f), floatArrayOf(190f, 46f), recul = 34f
    )

    /** Où commence le bord droit de chaque stèle le long de la rangée. */
    private val PAS_STELES = floatArrayOf(0f, 7f, 14f)
    private const val LARGE_STELE = 4.4f      // en unités de rangée, soit ~10 à l'avant
    private const val HAUT_STELE = 42f
    private const val TOLE_L = 11f            // les cotes à plat, pour surQuad
    private const val ETOILE_Y = 11.5f

    /**
     * Le centre de l'étoile de chaque stèle dans la fenêtre, et son rayon :
     * le halo de la nuit est un rond de lumière, il reste dans le plan de
     * l'image au lieu de suivre la perspective de la tôle.
     */
    internal val ETOILES_MUSEL: Array<FloatArray> = Array(PAS_STELES.size) { i ->
        val f = FUITE_STELES
        val ud = PAS_STELES[i]
        val ug = ud + LARGE_STELE
        val e = (f.echelle(ud) + f.echelle(ug)) / 2f
        floatArrayOf(
            (f.x(ud) + f.x(ug)) / 2f,
            (f.y(ud) + f.y(ug)) / 2f - (HAUT_STELE - ETOILE_Y) * e,
            4.8f * e
        )
    }

    /**
     * La ville haute sur la corniche du Bock, à droite : petite, loin, au
     * sommet du rocher, comme sur la photo du propriétaire.
     */
    internal val RUE_NEIMENSTER = Fuite(floatArrayOf(101f, 11f), floatArrayOf(74f, 23.5f), floatArrayOf(190f, 16f), recul = 33f, taille = 0.35f)
    private val MAISONS_NEIMENSTER = maisonsAuHasard(20, 127, 13f, 20f)

    /**
     * L'aile de l'abbaye, qui fuit vers la droite derrière l'église : une
     * longue bâtisse sous un grand toit d'ardoise à lucarnes.
     */
    internal val RUE_ABBAYE = Fuite(floatArrayOf(34f, 46f), floatArrayOf(96f, 35f), floatArrayOf(-110f, 40f), recul = 52f)
    private val ABBAYE = arrayOf(
        floatArrayOf(13f, 11f, 0f, 0f, 7f, 1f), floatArrayOf(11f, 10.5f, 0f, 0f, 6.6f, 1f),
        floatArrayOf(14f, 11f, 0f, 0f, 7.2f, 1f), floatArrayOf(11f, 10f, 0f, 0f, 6.4f, 1f)
    )

    /** Le château de Vianden sur son éperon : le palais, la tour carrée, l'aile basse. */
    internal val FUITE_VEIANEN = Fuite(floatArrayOf(41f, 31f), floatArrayOf(175f, 22f), floatArrayOf(-90f, 27f), taille = 1f)
    private val CHATEAU_VEIANEN = arrayOf(
        floatArrayOf(40f, 17f, 0f, 0f, 6.5f, 1f), floatArrayOf(8f, 20f, 0f, 0f, 7f, 0f), floatArrayOf(18f, 9.5f, 0f, 0f, 4f, 0f)
    )

    /** La Spuerkeess derrière le pont Adolphe : l'aile gauche, le pavillon, l'aile droite ; et sa tour. */
    internal val FUITE_SPUERKEESS = Fuite(floatArrayOf(13f, 33f), floatArrayOf(200f, 21f), floatArrayOf(-110f, 29f), taille = 1f)
    private val SPUERKEESS = arrayOf(
        floatArrayOf(26f, 15f, 0f, 0f, 3.6f, 1f), floatArrayOf(13f, 20f, 0f, 0f, 1.5f, 0f), floatArrayOf(34f, 15f, 0f, 0f, 3.6f, 1f)
    )
    internal val FUITE_TOUR_SPUERKEESS = Fuite(floatArrayOf(3f, 33f), floatArrayOf(200f, 21f), floatArrayOf(-110f, 29f), taille = 1f)
    private val TOUR_SPUERKEESS = arrayOf(floatArrayOf(7f, 24f, 0f, 0f, 0.6f, 0f))

    /** Le château d'Ansembourg : l'aile ocre, le corps central, l'aile blanche, et ses deux tours. */
    internal val FUITE_ANSEBUERG = Fuite(floatArrayOf(22f, 32f), floatArrayOf(190f, 23f), floatArrayOf(-100f, 28f), 165f)
    private val CHATEAU_ANSEBUERG = arrayOf(
        floatArrayOf(22f, 10f, 0f, 0f, 3.4f, 0f), floatArrayOf(34f, 16f, 0f, 0f, 4f, 1f), floatArrayOf(30f, 14f, 1f, 0f, 3.4f, 1f)
    )
    private val TOUR_ANSEBUERG = floatArrayOf(7f, 22f, 0f, 0f, 8f, 0f)

    /** Le pont couvert du Haff Réimech : il entre par la gauche et fuit vers la droite. */
    internal val PONT_REIMECH = Fuite(floatArrayOf(-6f, 28f), floatArrayOf(210f, 19f), floatArrayOf(-150f, 22f), taille = 1f)
    /** La hauteur des piles du pont couvert, au premier plan. */
    private const val PILE_REIMECH = 21f

    /** Le Pfaffenthal, au fond de la vallée sous l'ascenseur. */
    /** La hauteur de la tour de l'ascenseur du Pfaffenthal : elle s'arrête sous le haut de la fenêtre. */
    private const val HAUT_PAFENDALL = 52f

    /** Où monte le versant d'en face du Pfaffenthal, la ville haute sur sa crête. */
    private const val CRETE_PAFENDALL = 26f

    internal val RUE_PAFENDALL = Fuite(floatArrayOf(18f, 48f), floatArrayOf(150f, 41f), floatArrayOf(-100f, 45f), taille = 0.3f)
    private val MAISONS_PAFENDALL = maisonsAuHasard(30, 218, 12f, 19f)

    /** Conzefenn de trois quarts : la lisière de la forêt et la clôture fuient vers la droite. */
    internal val LISIERE_CONZEFENN = Fuite(floatArrayOf(-4f, 37f), floatArrayOf(200f, 22f), floatArrayOf(-150f, 30f), taille = 1f)
    internal val CLOTURE_CONZEFENN = Fuite(floatArrayOf(-2f, 62f), floatArrayOf(200f, 36f), floatArrayOf(-150f, 50f), taille = 1f)

    /** La passerelle de l'étang du Dumongshaff, de trois quarts : le quadrilatère où son dessin de face est posé. */
    private val PASSERELLE_DUMONGSHAFF = floatArrayOf(4f, 22f, 98f, 32.5f, 98f, 47f, 4f, 55f)

    /** Le pont Adolphe vu de la Pétrusse, de trois quarts : où son dessin de face (-3, 0, 89 × 46) est posé. */
    private val PONT_PEITRUSDALL = floatArrayOf(-5f, -2f, 89f, 5f, 89f, 35f, -5f, 51f)
    private const val PONT_PEITRUSDALL_L = 89f
    private const val PONT_PEITRUSDALL_H = 46f

    /** Le mur de tuf du Kallektuffquell, de trois quarts : où son dessin de face (-3, -3, 106 × 41) est posé. */
    private val MUR_TUF = floatArrayOf(-3f, -3f, 103f, -3f, 103f, 31f, -3f, 42f)

    /** Le point ([x], [y]) du mur de tuf dessiné de face, là où le pose [MUR_TUF]. */
    private fun surMurTuf(x: Float, y: Float): FloatArray {
        val m = Matrix()
        m.setPolyToPoly(floatArrayOf(-3f, -3f, 103f, -3f, 103f, 38f, -3f, 38f), 0, MUR_TUF, 0, 4)
        return floatArrayOf(x, y).also { m.mapPoints(it) }
    }

    /** Le rocher de Felsmillen, de trois quarts : où son dessin de face (-3, -3, 41 × 69) est posé. */
    private val ROCHER_FELSMILLEN = floatArrayOf(-3f, -5f, 38f, 2.4f, 38f, 61.6f, -3f, 68f)

    /** La rue du Grund, au bord de l'Alzette : elle fuit vers la droite. */
    internal val RUE_GRUND = Fuite(floatArrayOf(13f, 50.5f), floatArrayOf(178f, 34f), floatArrayOf(-140f, 40f), 150f, quai = 3f)

    /**
     * Les deux rangées du Breedewee, qui montent de part et d'autre de
     * l'escalier et fuient vers le débouché de la ruelle. Les façades y
     * étaient de face, à plat : une maison ne se présente pas de face
     * (propriétaire, 2026-11-16).
     */
    internal val RUE_BREEDEWEE = Fuite(floatArrayOf(84f, 56f), floatArrayOf(54f, 32f), floatArrayOf(400f, 34f), 24f)

    /**
     * Les maisons du Breedewee : largeur, hauteur, mur, colombages, toit,
     * lucarnes.
     *
     * Dans l'esprit de la carte « Fënster » (propriétaire, 2026-11-16) : des
     * maisons **basses**, quatre niveaux, et une fuite **douce**, où chaque
     * maison ne montre qu'un peu de son pignon. Hautes de soixante unités sur
     * une fuite serrée, elles faisaient un boulevard haussmannien qui
     * plongeait dans une ruelle du Grund.
     */
    private val MAISONS_BREEDEWEE = arrayOf(
        floatArrayOf(13f, 25f, 2f, 0f, 6f, 1f), floatArrayOf(11f, 22f, 1f, 0f, 5.2f, 0f),
        floatArrayOf(12f, 24f, 0f, 0f, 5.6f, 1f), floatArrayOf(10f, 21f, 3f, 0f, 5f, 0f),
        floatArrayOf(12f, 23f, 4f, 0f, 5.6f, 1f), floatArrayOf(10f, 22f, 5f, 0f, 5f, 0f),
        floatArrayOf(11f, 24f, 2f, 0f, 5.4f, 0f), floatArrayOf(10f, 21f, 1f, 0f, 5f, 0f),
        floatArrayOf(11f, 23f, 3f, 0f, 5.2f, 1f), floatArrayOf(10f, 21f, 0f, 0f, 5f, 0f))

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
                    val x = ((20f + temps * 1.6f + 24f) % 150f) - 24f
                    p.peniche(x, sin(temps * 1.3f) * 0.2f * (1f + raf * 5f))
                } else {
                    etoilesDeSchengen(p, temps)
                    envol(p, vol, 6f, 10f, 1f)
                }
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
                Lieu.KALLEKTUFFQUELL -> p.surQuad(106f, 41f, MUR_TUF, x0 = -3f, y0 = -3f) {
                    // La source déborde : les gouttes se pressent et se multiplient,
                    // le long du mur qui fuit.
                    val n = 5 + (raf * 9f).toInt()
                    for (k in 0 until n) {
                        goutte(p, temps * (1f + raf * 1.5f) + k * 0.7f, 12f + (k * 17 % 76), 26f, 40f, rond = true)
                    }
                    ronds(p, vol, 41f, 22f, 48f, 70f)
                }
                Lieu.PEITRUSDALL -> if (calque == 3) {
                    // Sur le tablier qui fuit : le tram suit la perspective du pont.
                    val x = ((54f + temps * 5f + elan(temps) * 30f + 20f) % 140f) - 20f
                    p.surQuad(PONT_PEITRUSDALL_L, PONT_PEITRUSDALL_H, PONT_PEITRUSDALL, x0 = -3f, y0 = 0f) {
                        p.tram(x, 2.2f)
                        sonnerie(p, x + 15f, 2.2f, raf)
                    }
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
        /**
         * Les trois étoiles évidées de Schengen, éclairées par-derrière la nuit
         * venue : chacune respire à son rythme, deux ondes lentes décalées d'une
         * stèle à l'autre, pour qu'elles scintillent sans battre ensemble. Une
         * secousse les ravive un instant, comme on souffle sur une braise.
         */
        private fun etoilesDeSchengen(p: Peintre, temps: Float) {
            for ((k, s) in ETOILES_MUSEL.withIndex()) {
                val e = if (temps <= 0f) 0.6f else
                    (0.6f + 0.11f * sin(temps * 1.15f + k * 2.1f) + 0.06f * sin(temps * 3.1f + k * 0.9f) +
                        0.035f * sin(temps * 7.7f + k * 1.7f) + rafale(temps) * 0.25f).coerceIn(0f, 1f)
                p.etoileDeSchengen(s[0], s[1], s[2], e)
            }
        }

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
            for ((i, ch) in CHUTES_MELLERDALL.withIndex()) {
                val x = ch[0]; val w = ch[1]; val haut = ch[2]; val bas = ch[3]
                // Les filets qui descendent, plus vite à la secousse.
                for (k in 0 until 6) {
                    val u = ((temps * (0.9f + raf * 1.2f) + k / 6f + i * 0.37f) % 1f)
                    val y = haut + u * (bas - haut - 2f)
                    val dx = (k - 2.5f) / 2.5f * w * 0.32f * (1f + u * 0.4f)
                    p.ligne(x + dx, y, x + dx * 1.05f, y + 2.6f, blanc, 0.3f + raf * 0.3f, 0.8f)
                }
                // L'écume au pied, qui bat.
                p.brume(x, bas, w * (2.3f + raf * 1.2f) + sin(temps * 3f + i) * 0.3f, 2.2f + raf * 0.8f, 0.95f)
                // La brume qui monte du point de chute et se défait en s'élevant.
                for (k in 0 until 4) {
                    val ph = (temps * (0.22f + raf * 0.3f) + k / 4f + i * 0.21f) % 1f
                    val bx = x + sin(temps * 0.7f + k * 1.9f + i) * (1.2f + ph * 2f)
                    val by = bas - 0.6f - ph * (5.5f + raf * 4f)
                    p.brume(bx, by, w * (1.2f + ph * 1.9f) + raf * 2f, 2f + ph * 2.6f, (1f - ph) * (0.68f + raf * 0.3f))
                }
                if (raf > 0.05f) for (k in 0 until 6) {
                    val a = PI.toFloat() * (0.12f + 0.76f * k / 5f)
                    val d = ((temps * 2f + k * 0.2f) % 1f) * 5f * raf
                    p.rond(x + cos(a) * d * 1.4f, bas - 0.5f - sin(a) * d, 0.3f, blanc, 0.8f)
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
                Lieu.PAFENDALL -> Surcadre(Essence.FEUILLU, DROITE, Pied.FOUGERES, DROITE, portee = 26f)
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

        /**
         * Une fenêtre de vrai verre : un cadre clair, une vitre bleu-gris
         * foncé où le jour pose un reflet du ciel en biais, un croisillon.
         * Allumée le soir et la nuit, elle prend l'or des fenêtres comme
         * [fenetre]. Peinte avec [neutre] : le réglage des couleurs vives ne
         * la jaunit pas.
         */
        fun fenetreVerre(x: Float, y: Float, w: Float, h: Float, rnd: Alea?) = mineral {
            val allumee = P.allume && (rnd == null || rnd() < 0.7f)
            val cadre = neutre(duJour(0xFFF1F1EC.toInt()))
            rect(x - 0.22f, y - 0.22f, w + 0.44f, h + 0.44f, cadre)
            if (allumee) {
                rect(x - w * 0.6f, y - h * 0.4f, w * 2.2f, h * 1.8f, P.fenetre, 0.22f)
                rect(x, y, w, h, P.fenetre)
            } else {
                val verre = neutre(mix(duJour(0xFF34445E.toInt()), P.cielHaut, if (P.nuit) 0.1f else 0.22f))
                rect(x, y, w, h, verre)
                if (!P.nuit) {
                    // Le reflet du ciel, en biais dans le haut de la vitre.
                    val reflet = neutre(mix(verre, P.cielBas, 0.6f))
                    forme(Path().apply {
                        moveTo(x + w * 0.35f, y); lineTo(x + w, y); lineTo(x + w, y + h * 0.18f); lineTo(x + w * 0.1f, y + h * 0.62f); lineTo(x, y + h * 0.62f)
                        lineTo(x, y + h * 0.42f); close()
                    }, reflet, 0.55f)
                }
            }
            // Le croisillon.
            rect(x + w * 0.5f - 0.09f, y, 0.18f, h, cadre, 0.9f)
            rect(x, y + h * 0.4f - 0.09f, w, 0.18f, cadre, 0.9f)
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
                Saison.WANTER -> intArrayOf(neutre(duJour(0xFF6B625A.toInt())), neutre(duJour(0xFF9B9287.toInt())), neutre(duJour(0xFFBDB6A9.toInt())))
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
                piece(brins, neutre(duJour(0xFFB3A692.toInt())), 0.5f)
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

        /**
         * [col] quand les lampes sont allumées : une surface que ses propres
         * ampoules éclairent ne s'éteint pas avec le jour. Sans cela une fête
         * foraine de nuit est une fête foraine grise.
         */
        fun eclaire(col: Int, t: Float): Int = if (P.allume) mix(col, P.fenetre, t) else col

        /** L'ardoise des toits du pays. */
        fun ardoise(): Int = duJour(0xFF4A5468.toInt())

        /**
         * Le grès gris des socles, des chaînes d'angle, des encadrements et
         * des appuis — pas le jaune paille. C'est le même que celui dont
         * [rue3q] habille ses façades : la matière d'un mur vient de ces
         * pierres-là, posées à la bonne échelle, et non d'une texture.
         */
        fun pierreDeTaille(): Int = neutre(duJour(0xFFB9B3A6.toInt()))

        /**
         * Un bloc de pierre posé sur son lit de mortier, rentré de son joint :
         * c'est le mortier du dessous qui reparaît en creux tout autour, et
         * c'est ce creux qui fait voir la pierre. Suit la [pente] du rang.
         */
        private fun blocDePierre(x: Float, y: Float, w: Float, h: Float, pente: Float, col: Int) {
            val ix = w * 0.055f
            val iy = h * 0.09f
            val d = Path()
            d.moveTo(x + ix, y + iy)
            d.lineTo(x + w - ix, y + w * pente + iy)
            d.lineTo(x + w - ix, y + w * pente + h - iy)
            d.lineTo(x + ix, y + h - iy)
            d.close()
            piece(d, col)
        }

        /**
         * **Un appareil de pierre**, pour tout ce qui est bâti : [contour]
         * rempli d'un mortier sombre, puis des blocs irréguliers posés dessus,
         * chacun rentré de son joint. Rangs de [rang] de haut, blocs de
         * [longueur] en moyenne, le tout suivant la [pente] du bâtiment.
         *
         * Demande du propriétaire (2026-11-16) : « respecte la pierre et la
         * matière ». Un mur peint d'un aplat et rayé de quelques traits fins
         * donne de la pierre de taille neuve, jamais un mur du pays ; et à
         * valeur égale deux murs voisins se fondent l'un dans l'autre. Ce
         * sont les joints creux et les tons tirés au hasard — dont une part
         * de pierre lichénée — qui font la matière.
         *
         * Le contour est découpé d'avance (une arche creusée y reste creuse),
         * et tout est peint sous le clip : rien ne déborde.
         */
        fun appareil(contour: Path, col: Int, rang: Float = 2.6f, longueur: Float = 4.6f,
                     graine: Int = 0, pente: Float = 0f, lichen: Float = 0.2f) {
            if (!actif) return
            piece(contour, sombre(col, 0.5f))
            val b = RectF()
            contour.computeBounds(b, true)
            if (b.width() <= 0f || b.height() <= 0f) return
            val r = Alea(graine + 400)
            c.save()
            c.clipPath(contour)
            val largeur = b.width() + longueur * 2f
            var y = b.top - rang - abs(pente) * largeur
            val fin = b.bottom + rang + abs(pente) * largeur
            while (y < fin) {
                val h = rang * (0.82f + r() * 0.36f)
                var x = b.left - longueur * (0.3f + r())
                while (x < b.right + longueur) {
                    val w = longueur * (0.68f + r() * 0.72f)
                    val t = r()
                    blocDePierre(x, y + (x - b.left) * pente, w, h, pente, when {
                        t < 0.32f -> sombre(col, 0.12f + t * 0.55f)
                        t < 0.62f -> mix(col, 0xFFFFFFFF.toInt(), 0.08f + t * 0.2f)
                        t < 0.62f + lichen -> mix(col, vert(0.42f), 0.16f + t * 0.22f)
                        else -> mix(col, neutre(duJour(0xFF8E7C60.toInt())), 0.22f + t * 0.3f)
                    })
                    x += w
                }
                y += h
            }
            c.restore()
        }

        /** [appareil] sur un rectangle, le cas courant d'un mur. */
        fun appareil(x: Float, y: Float, w: Float, h: Float, col: Int, rang: Float = 2.6f,
                     longueur: Float = 4.6f, graine: Int = 0, pente: Float = 0f) =
            appareil(Path().apply { addRect(x, y, x + w, y + h, Path.Direction.CW) }, col, rang, longueur, graine, pente)

        /**
         * [col] à l'ombre, mais une ombre froide : vers un bleu-gris qui suit
         * l'heure, au lieu du brun de [sombre]. Lumière chaude, ombre froide.
         */
        fun ombreFroide(col: Int, t: Float): Int =
            mix(col, mix(0xFF4E5E86.toInt(), P.pres, ATTENUE[P.rang] * 0.6f), t)

        /**
         * Les enduits des façades du pays, comme au Grund, à Clervaux ou à
         * Vianden : gris perle, vieux rose, blanc cassé froid, bleu-gris pâle,
         * ocre rouge, vert sauge, dans l'ordre où [rue3q] les fait se suivre
         * (jamais deux pareils côte à côte), peints avec [neutre].
         */
        fun facades(): IntArray = intArrayOf(
            duJour(0xFFD8DAD8.toInt()), duJour(0xFFD69482.toInt()), duJour(0xFFF0EFE8.toInt()),
            duJour(0xFF9DB3D2.toInt()), duJour(0xFFC66E40.toInt()), duJour(0xFFA3B985.toInt())
        )

        /** Un vert de feuillage, de [nuance] 0 (sombre) à 1 (clair), dans la palette de l'heure. */
        /**
         * Le vert de la végétation, ramené à mi-chemin de son propre [neutre].
         *
         * La végétation n'est pas protégée comme le bâti (règle 10) : elle
         * prend `raviver` en plein, et montait au chartreuse. Elle devenait
         * alors la chose la plus saturée de la fenêtre, devant le sujet qui
         * est posé dessus — contraire à la règle 5, où le mot reste l'acteur
         * (jugé en main sur un A21s le 2026-11-15, sur la Rout Bréck).
         *
         * Le mélange ne touche qu'à la **saturation**, pas à la teinte : le
         * vert reste donc dans la végétation que [Habillage] doit reconnaître
         * pour la blanchir l'hiver et la rouiller l'automne, ce qu'un relevé
         * des quatre saisons confirme sur la Rout Bréck, le Péitrusdall et le
         * Conzefenn.
         */
        fun vert(nuance: Float): Int {
            // Le Luxembourg est très vert, et la carte doit le dire (demande
            // du propriétaire, 2026-11-15) : la référence est un vert de
            // feuillage franc et profond, et elle pèse plus lourd qu'avant
            // dans le mélange. Plus vert ne veut pas dire plus acide : c'est
            // [VERT_CALME] qui tient la saturation, et il ne bouge pas.
            val v = mix(mix(P.pres, P.milieu, nuance), VERDURE, if (P.nuit) 0.09f else 0.52f)
            return mix(v, neutre(v), VERT_CALME)
        }

        /**
         * La nappe de végétation d'un plan : la couleur du plan ramenée dans
         * la famille de [VERDURE], de [force] (plus faible au loin, pour que
         * la perspective aérienne tienne).
         *
         * Les grandes masses d'herbe et les crêtes boisées étaient peintes
         * avec la couleur du plan telle quelle, et le ravivage en faisait un
         * vert pomme presque fluorescent : `#5DBB63` sort en `#27E912` au
         * matin, `#9CC43F` en `#ABEC00` l'après-midi. Le propriétaire les a
         * écartées le 2026-11-15 : « le vert pomme ne convient pas à la
         * scène, ce qu'il faut c'est un vert qui reflète réellement le côté
         * luxuriant et verdoyant de la nature au Luxembourg. » La nuit, les
         * plans sont bleus : on n'y verdit presque pas, comme dans [vert].
         */
        fun nappe(col: Int, force: Float = 0.62f, calme: Float = 0.42f): Int {
            val v = mix(col, VERDURE, if (P.nuit) 0.09f else force)
            return mix(v, neutre(v), calme)
        }

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
         * Peint [bloc], dessiné à plat dans le rectangle ([x0], [y0], [l], [h]), sur
         * le quadrilatère [q] (haut gauche, haut droit, bas droit, bas gauche,
         * huit coordonnées) en vraie perspective : les fenêtres d'une façade
         * qui fuit rapetissent et se serrent vers le fond, comme peintes sur
         * une planche vue de trois quarts.
         */
        fun surQuad(l: Float, h: Float, q: FloatArray, x0: Float = 0f, y0: Float = 0f, bloc: () -> Unit) {
            val m = Matrix()
            if (!m.setPolyToPoly(floatArrayOf(x0, y0, x0 + l, y0, x0 + l, y0 + h, x0, y0 + h), 0, q, 0, 4)) return
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
        /**
         * La matière d'une façade de [w] sur [h], peinte à plat dans
         * `surQuad` : l'enduit un peu irrégulier, la corniche sous le toit,
         * les bandeaux entre les étages, les chaînes d'angle de pierre et le
         * socle de pierre au rez-de-chaussée, avec ses joints. Une maison à
         * [colombages] n'a ni enduit taché ni chaînes : le bois les remplace.
         */
        private fun matiere(w: Float, h: Float, mur: Int, pierre: Int, r: Alea, colombages: Boolean) {
            val clair = neutre(mix(mur, 0xFFFFFFFF.toInt(), 0.35f))
            val ombre = neutre(ombreFroide(mur, 0.3f))
            if (!colombages) {
                // L'enduit : quelques taches plus claires et plus sombres, comme un badigeon.
                for (k in 0 until 7) {
                    val x = r() * w; val y = 1f + r() * (h - 5f)
                    ovalePapierPlat(x, y, 1.2f + r() * 2.4f, 0.8f + r() * 1.6f, if (k % 2 == 0) clair else ombre, 0.16f)
                }
            }
            // La corniche, et l'ombre qu'elle pose sur le mur.
            rect(0f, 0f, w, 0.75f, pierre)
            rect(0f, 0.75f, w, 0.45f, ombre, 0.5f)
            // Les bandeaux entre les étages.
            var y = 2.6f + 4.8f - 1.1f
            while (y < h - 4.4f) { rect(0f, y, w, 0.3f, clair, 0.8f); y += 4.8f }
            // Le socle de pierre au rez-de-chaussée, et ses joints.
            val socle = h - 3.9f
            rect(0f, socle, w, 3.9f, pierre)
            rect(0f, socle - 0.25f, w, 0.25f, ombre, 0.6f)
            val joint = neutre(sombre(pierre, 0.25f))
            for (k in 1..2) rect(0f, socle + k * 1.3f, w, 0.1f, joint, 0.7f)
            if (!colombages) {
                // Les chaînes d'angle, une pierre longue, une pierre courte.
                var cy = 1.2f
                var k = 0
                while (cy < socle - 1f) {
                    val l = if (k % 2 == 0) 1.5f else 0.95f
                    rect(0f, cy, l, 1.15f, pierre)
                    rect(w - l, cy, l, 1.15f, pierre)
                    cy += 1.35f; k++
                }
            }
        }

        /** Un ovale de couleur posée à plat, sans carton ni ombre : une tache d'enduit. */
        private fun ovalePapierPlat(cx: Float, cy: Float, rx: Float, ry: Float, col: Int, o: Float) {
            forme(Path().apply { addOval(cx - rx, cy - ry, cx + rx, cy + ry, Path.Direction.CW) }, col, o)
        }

        fun rue3q(r: Fuite, maisons: Array<FloatArray>, murs: IntArray, rnd: Alea, ecart: Float = 0f, debut: Float = 0f,
                  froid: Boolean = true) = mineral {
            val tl = r.taille
            // [froid] : contre la dominante jaune, des couleurs que le
            // réglage des couleurs vives ne réchauffe pas ([neutre]), des
            // murs qui alternent sans se répéter, et des ombres froides.
            val n: (Int) -> Int = if (froid) { col -> neutre(col) } else { col -> col }
            val toit = n(ardoise())
            val faitage = n(mix(ardoise(), 0xFFFFFFFF.toInt(), 0.18f))
            val poutre = n(duJour(0xFF4A3A2E.toInt()))
            // La pierre de taille des socles, des chaînes d'angle et des
            // encadrements : un grès gris, pas le jaune paille.
            val pierreDeTaille = n(duJour(0xFFB9B3A6.toInt()))
            // Froid : des vitres de vrai verre, au lieu des rectangles olive.
            val vitre: (Float, Float, Float, Float) -> Unit =
                if (froid) { x, y, w, h -> fenetreVerre(x, y, w, h, rnd) } else { x, y, w, h -> fenetre(x, y, w, h, rnd) }
            val debuts = FloatArray(maisons.size)
            var u = debut
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
                val w = m[0]; val h = m[1]; val toitH = m[4]
                val mur = if (froid) n(murs[i % murs.size]) else murs[m[2].toInt()]
                val ua = debuts[i]; val ub = ua + w
                val ax = r.x(ua); val ay = r.y(ua); val ea = r.echelle(ua)
                val bx = r.x(ub); val by = r.y(ub); val eb = r.echelle(ub)
                val hautA = ay - h * tl * ea; val hautB = by - h * tl * eb
                val pa = profondeur(ax, ay, ea)
                val pb = profondeur(bx, by, eb)
                // Le pignon, à l'ombre, et son triangle sous le toit. Froid :
                // une ombre bleutée, la complémentaire de la lumière chaude.
                val pignon = if (froid) n(ombreFroide(murs[i % murs.size], 0.42f)) else sombre(mur, 0.28f)
                val cote = floatArrayOf(ax + pa[0], hautA + pa[1], ax, hautA, ax, ay, ax + pa[0], ay + pa[1])
                piece(quad(cote), pignon, 0.5f)
                val sommetA = floatArrayOf(ax + pa[0] * 0.5f, hautA + pa[1] * 0.5f - toitH * tl * ea)
                piece(Path().apply {
                    moveTo(ax, hautA); lineTo(sommetA[0], sommetA[1]); lineTo(ax + pa[0], hautA + pa[1]); close()
                }, pignon, 0.3f)
                val fin = h * tl * ea < 4.5f
                if (!fin) surQuad(6f, h, cote) {
                    var fy = 3f
                    while (fy < h - 3f) { vitre(2.4f, fy, 1.2f, 2.4f); fy += 5.2f }
                }
                // La façade, peinte à plat puis posée en perspective.
                val facade = floatArrayOf(ax, hautA, bx, hautB, bx, by, ax, ay)
                piece(quad(facade), mur, 0.6f)
                if (!fin) surQuad(w, h, facade) {
                    if (froid) matiere(w, h, murs[i % murs.size], pierreDeTaille, Alea(400 + i), m[3] > 0f)
                    if (m[3] > 0f) {
                        var px = 0.4f
                        while (px <= w) { ligne(px, 0f, px, h, poutre, 0.3f); px += 2.6f }
                        for (k in 1..2) { val y = h * k / 3f; ligne(0f, y, w, y, poutre, 0.3f) }
                    }
                    var fy = 2.6f
                    while (fy < h - 4f) {
                        var fx = 1.6f
                        while (fx < w - 2f) {
                            if (froid) {
                                // Le linteau et l'appui de pierre.
                                rect(fx - 0.3f, fy - 0.62f, 1.9f, 0.36f, pierreDeTaille)
                                rect(fx - 0.42f, fy + 2.66f, 2.14f, 0.42f, pierreDeTaille)
                                rect(fx - 0.42f, fy + 3.08f, 2.14f, 0.16f, n(ombreFroide(murs[i % murs.size], 0.5f)), 0.6f)
                            }
                            vitre(fx, fy, 1.3f, 2.4f); fx += 3.3f
                        }
                        fy += 4.8f
                    }
                    // La porte au rez-de-chaussée.
                    rect(w * 0.42f, h - 3.6f, 1.8f, 3.6f, if (froid) n(ombreFroide(murs[i % murs.size], 0.7f)) else sombre(mur, 0.55f))
                }
                // Le pan du toit, qui monte vers le faîte en reculant.
                val sommetB = floatArrayOf(bx + pb[0] * 0.5f, hautB + pb[1] * 0.5f - toitH * tl * eb)
                val pan = floatArrayOf(sommetA[0], sommetA[1], sommetB[0], sommetB[1], bx + 0.4f * tl * eb, hautB + 0.3f * tl, ax - 0.4f * tl * ea, hautA + 0.3f * tl)
                piece(quad(pan), toit, 0.6f)
                ligne(sommetA[0], sommetA[1], sommetB[0], sommetB[1], faitage, 0.35f * tl * (ea + eb))
                // Les rangs d'ardoises, plus clairs, qui suivent la pente du pan.
                if (froid && !fin) surQuad(w, 6f, pan) {
                    val rang = n(mix(ardoise(), 0xFFFFFFFF.toInt(), 0.22f))
                    var y = 0.9f
                    while (y < 6f) { ligne(0f, y, w, y, rang, 0.16f, 0.55f); y += 0.95f }
                    val joint = n(sombre(ardoise(), 0.3f))
                    var k = 0
                    y = 0.9f
                    while (y < 5.6f) {
                        var x = if (k % 2 == 0) 0.6f else 1.3f
                        while (x < w) { ligne(x, y, x, y + 0.95f, joint, 0.1f, 0.4f); x += 1.4f }
                        y += 0.95f; k++
                    }
                }
                if (m[5] > 0f && !fin) surQuad(w, 6f, pan) {
                    var lx = 2.2f
                    while (lx < w - 3f) {
                        feuille(lx, 2.4f, 2f, 2.6f, mur, 0.3f)
                        vitre(lx + 0.5f, 3f, 1f, 1.6f)
                        pieceD("M${lx - 0.4f} 2.6L${lx + 1f} 0.9L${lx + 2.4f} 2.6Z", toit, 0f)
                        lx += 4.6f
                    }
                }
            }
            // Le quai, sous les façades, jusqu'au bord de l'eau.
            if (r.quai <= 0f) return@mineral
            val pierre = if (froid) n(duJour(0xFF9A968C.toInt())) else duJour(0xFFA59A86.toInt())
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
            if (froid) {
                // L'appareil du mur de quai : une assise, et des joints qui se serrent au loin.
                val joint = n(sombre(duJour(0xFF9A968C.toInt()), 0.3f))
                ligne(r.x(0f) - 12f * tl, (r.y(0f) + r.eau(r.x(0f))) / 2f, r.x(u), (r.y(u) + r.eau(r.x(u))) / 2f, joint, 0.15f * tl, 0.7f)
                var v = 0f
                while (v < u) {
                    val x = r.x(v)
                    ligne(x, r.y(v) + 0.2f, x, r.eau(x) - 0.2f, joint, 0.12f * tl * r.echelle(v), 0.6f)
                    v += 4.5f
                }
            }
        }

        /**
         * Un ouvrage seul de trois quarts (une tour, un socle, un immeuble) :
         * sa face de [w] sur [h] posée en ([x], [bas]), qui fuit de [pente]
         * vers son côté, ce côté à l'ombre, de [p] de profondeur, vers la
         * droite ([versDroite]) ou la gauche, et son dessus. [dessin] peint la
         * face à plat, dans (0, 0, [w], [h]).
         */
        fun boite3q(x: Float, bas: Float, w: Float, h: Float, p: Float, col: Int, versDroite: Boolean = true,
                    pente: Float = 0.08f, dessus: Boolean = true, dessin: (() -> Unit)? = null) = mineral {
            val s = if (versDroite) 1f else -1f
            // Comme les maisons de [rue3q] : une couleur que les couleurs vives
            // ne jaunissent pas, et un côté dans une ombre froide.
            val brut = col
            @Suppress("NAME_SHADOWING") val col = neutre(brut)
            // La face fuit un peu : son bord du côté de la profondeur est plus court.
            val xa = if (versDroite) x else x + w
            val xb = if (versDroite) x + w else x
            val dy = w * pente
            val face = if (versDroite) floatArrayOf(xa, bas - h, xb, bas - h + dy, xb, bas - dy * 0.3f, xa, bas)
                else floatArrayOf(xb, bas - h + dy, xa, bas - h, xa, bas, xb, bas - dy * 0.3f)
            val dx = p * 0.62f * s
            val dyc = -p * 0.3f
            val bord = xb
            val haut = bas - h + dy
            val pied = bas - dy * 0.3f
            val cote = floatArrayOf(bord, haut, bord + dx, haut + dyc + dy * 0.2f, bord + dx, pied + dyc * 0.4f, bord, pied)
            piece(quad(cote), neutre(ombreFroide(brut, 0.4f)), 0.5f)
            if (dessus) {
                val ha = bas - h
                val toit = if (versDroite) floatArrayOf(xa + dx * 0.9f, ha + dyc, bord + dx, haut + dyc + dy * 0.2f, bord, haut, xa, ha)
                    else floatArrayOf(bord + dx, haut + dyc + dy * 0.2f, xa + dx * 0.9f, ha + dyc, xa, ha, bord, haut)
                piece(quad(toit), neutre(mix(brut, 0xFFFFFFFF.toInt(), 0.12f)), 0.3f)
            }
            piece(quad(face), col, 0.6f)
            // La face est déjà rangée haut gauche, haut droit, bas droit, bas gauche.
            dessin?.let { d -> surQuad(w, h, face) { d() } }
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
            lisiere(-2f, 102f, { x -> 33f + 2f * sin(x * 0.09f) }, 1.4f, 2.2f, 105, nappe(mix(P.loin, P.milieu, 0.35f), 0.3f, VERT_CALME))
            plan(1)
            pieceD("M-2 66L-2 43C12 40 26 36 36 33C50 30 70 30 84 33C92 35 98 37 102 38L102 66Z", nappe(P.milieu))
            touffe(vert(0.3f), 24f, 34f, 3f, 28f, 32.5f, 3.2f, 32f, 33.5f, 2.6f)
            touffe(vert(0.3f), 90f, 34.5f, 3f, 95f, 33f, 3.2f, 99f, 35f, 2.8f)
            val mur = duJour(0xFFEADDBE.toInt())
            val ombre = mix(mur, P.pres, 0.28f)
            val toit = ardoise()
            // Le château en volumes de trois quarts : le palais, la tour
            // carrée et l'aile basse fuient vers la droite, chacun avec sa
            // façade, son toit et le pignon qui dépasse de sa voisine.
            val f = FUITE_VEIANEN
            val fin = CHATEAU_VEIANEN.sumOf { it[0].toDouble() }.toFloat()
            mineral {
                piece(Path().apply {
                    moveTo(f.x(0f) - 8f, f.y(0f) + 6f)
                    lineTo(f.x(0f) - 5f, f.y(0f))
                    var u = 0f
                    while (u <= fin) { lineTo(f.x(u), f.y(u)); u += 4f }
                    lineTo(f.x(fin) + 3f, f.y(fin) + 6f * f.echelle(fin))
                    close()
                }, duJour(0xFFA39377.toInt()))
            }
            rue3q(f, CHATEAU_VEIANEN, intArrayOf(duJour(0xFFD6CFC2.toInt())), r)
            mineral {
                // Le clocher de la chapelle, sur le faîte du palais.
                val u = 22f
                val e = f.echelle(u)
                val cx = f.x(u) - 2.6f * e
                val cy = f.y(u) - (CHATEAU_VEIANEN[0][1] + CHATEAU_VEIANEN[0][4]) * e - 0.6f * e
                feuille(cx - 1.4f * e, cy - 4f * e, 2.8f * e, 4.6f * e, mur)
                pieceD("M${cx - 1.9f * e} ${cy - 3.7f * e}L$cx ${cy - 8.4f * e}L${cx + 1.9f * e} ${cy - 3.7f * e}Z", toit)
                // La tour ronde de gauche, devant le pignon du palais : ronde,
                // elle se voit de même sous tous les angles.
                val tx = f.x(0f) - 6f
                val ty = f.y(0f)
                feuille(tx, ty - 15f, 4.4f, 15f, mur)
                feuille(tx + 2.7f, ty - 15f, 1.7f, 15f, ombre, 0f)
                pieceD("M${tx - 0.7f} ${ty - 14.8f}L${tx + 2.2f} ${ty - 23.8f}L${tx + 5.1f} ${ty - 14.8f}Z", toit)
                fenetre(tx + 1.4f, ty - 10f, 1f, 1.8f, r)
            }
            if (!vivant) brumeDeVeianen(0f)
            plan(2)
            solDePierre(SolDePierre.GRAVIER, DEVANT)  // le chemin au bord de l'Our
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
                nappe(mix(P.loin, P.milieu, 0.45f), 0.3f, VERT_CALME))
            pieceD("M80 22C84 18 90 17 95 20L99 36L78 38Z", neutre(duJour(0xFF9C7C56.toInt())), 0.5f)
            plan(1)
            plancher(56f, DEVANT)
            poser(56f)
            lisiere(-2f, 102f, { x -> 31f - x * 0.04f + sin(x * 0.15f) }, 1.6f, 2.6f, 117, nappe(P.milieu))
            val blanc = duJour(0xFFF4F0E8.toInt())
            val creme = duJour(0xFFE6D8BE.toInt())
            // Un second versant boisé, plus sombre et plus près : sur la
            // photo la forêt descend par ressauts jusqu'aux toits, elle ne
            // s'arrête pas sur une seule crête.
            lisiere(-2f, 102f, { x -> 38f + 1.8f * sin(x * 0.19f + 2f) }, 1.3f, 2.1f, 119, nappe(P.milieu, 0.78f))
            // La Sûre : le village est sur la rive proche, devant elle, et
            // non posé dessus.
            riviere(45.5f, 50f, 118, 20f, 30f)
            // Le village en deux planches de trois quarts. Les deux tourelles
            // pâles à toit d'ardoise qui l'accompagnaient — la chapelle du
            // versant et le clocher — ont été retirées à la demande du
            // propriétaire (2026-11-15) : posées sans bâtiment sous elles,
            // elles se lisaient comme deux petits donjons flottants et
            // faisaient concurrence à la vraie tour.
            val murs = intArrayOf(blanc, duJour(0xFFD8DAD8.toInt()), blanc, duJour(0xFFDDB2A4.toInt()), blanc, duJour(0xFFB9C6D6.toInt()))
            rue3q(RUE_ESCH_HAUT, MAISONS_ESCH_HAUT, murs, r, ecart = 5f)
            rue3q(RUE_ESCH_BAS, MAISONS_ESCH_BAS, murs, r, ecart = 5f)
            c.restore()
            plan(2)
            solDePierre(SolDePierre.PAVES, DEVANT, -0.02f)  // la place au bord de la Sûre
            poser(DEVANT)
            // Le schiste d'Esch-sur-Sûre, le rocher et le donjon sont
            // neutralisés : sans cela le ravivage les tirait vers un ocre
            // jaune, et la matière bâtie n'a pas de jaune (règle 10). Leurs
            // faces à l'ombre passent par [sombre] et non plus par un mélange
            // avec [P.pres], qui est un vert : sur une pierre ocre il donnait
            // un olive plausible, sur une pierre grise un pan de mur vert.
            val roc = neutre(duJour(0xFFB09670.toInt()))
            mineral {
                pieceD("M-2 58.5L-2 41C4 39 12 38 20 40C26 42 31 50 35 58.5Z", roc)
                pieceD("M8 58.5C10 52 16 48 22 47C26 50 28 54 29 58.5Z", sombre(roc, 0.25f), 0.6f)
                // Les lits du schiste dans l'éperon, parallèles à sa crête :
                // sans eux le rocher restait un aplat sous une tour appareillée.
                for (dy in floatArrayOf(3f, 7f, 11.5f, 16.5f)) {
                    val fin = 20f - dy * 0.35f
                    pieceD("M-2 ${41f + dy}C4 ${39.4f + dy} 12 ${38.4f + dy} $fin ${40.2f + dy}" +
                        "L$fin ${41.2f + dy}C12 ${39.4f + dy} 4 ${40.4f + dy} -2 ${42f + dy}Z", sombre(roc, 0.2f), 0f)
                }
            }
            pieceD("M-2 43C4 40 12 39 20 41C24 42 27 45 29 48L26 48C22 44.5 14 42.5 -2 45.5Z", neutre(duJour(0xFF9A8C58.toInt())), 0.5f)
            // Le donjon est en **moellon de schiste**, la pierre de l'Ardenne :
            // des dalles minces, irrégulières, posées en assises inégales, d'un
            // gris froid que la rouille des lits traverse par endroits. C'était
            // un aplat uni rayé de cinq joints : à la taille où on le voit, la
            // matière ne se lisait pas (propriétaire, 2026-11-15).
            val pierre = neutre(duJour(0xFF8E8B85.toInt()))
            mineral {
                pieceD("M3 42L3 9.2C3 7.6 23 7.6 23 9.2L23 42Z", sombre(pierre, 0.5f))
                // Les assises, de haut en bas : chaque dalle prend le ton de la
                // face qu'elle occupe (claire à gauche, à l'ombre à droite),
                // puis son écart propre ; le fond sombre fait le mortier.
                val rouille = mix(pierre, duJour(0xFF7A5436.toInt()), 0.5f)
                var y = 9.4f
                while (y < 41.6f) {
                    val h = min(0.95f + r() * 0.55f, 42f - y)
                    var x = 3.2f
                    while (x < 22.8f) {
                        val w = min(2.4f + r() * 3.4f, 22.8f - x)
                        val face = when {
                            x + w * 0.5f < 6f -> mix(pierre, 0xFFFFFFFF.toInt(), 0.16f)
                            x + w * 0.5f > 17f -> sombre(pierre, 0.32f)
                            else -> pierre
                        }
                        val d = r()
                        val dalle = when {
                            d < 0.08f -> mix(face, rouille, 0.3f)
                            d < 0.5f -> sombre(face, 0.06f + r() * 0.12f)
                            else -> mix(face, 0xFFFFFFFF.toInt(), 0.03f + r() * 0.1f)
                        }
                        rect(x, y, w - 0.28f, h - 0.22f, dalle)
                        x += w
                    }
                    y += h
                }
                // Les chaînes d'angle, en grès taillé. Sur un mur de moellon,
                // les angles et les baies sont montés en pierre de taille :
                // c'est ce qui dit que la tour est un ouvrage et non un tas,
                // et c'est la même leçon que les façades du Grund, où la
                // matière vient des chaînes, des encadrements et des appuis.
                val gres = neutre(duJour(0xFFC9BCA4.toInt()))
                for (gauche in booleanArrayOf(true, false)) {
                    val col = if (gauche) mix(gres, 0xFFFFFFFF.toInt(), 0.04f) else sombre(gres, 0.34f)
                    var qy = 9.4f
                    var longue = true
                    while (qy < 41.6f) {
                        val qh = min(if (longue) 2.2f else 1.4f, 42f - qy)
                        val qw = if (longue) 2.4f else 1.6f
                        rect(if (gauche) 3.1f else 22.6f - qw, qy, qw, qh - 0.24f, col)
                        qy += qh
                        longue = !longue
                    }
                }
                feuille(2.6f, 7.8f, 20.8f, 1.6f, sombre(pierre, 0.18f), 0.5f)
                // Les baies dans leur encadrement : la fenêtre haute à linteau
                // cintré, deux archères plus bas.
                val encadre = sombre(gres, 0.14f)
                rect(9.6f, 12.3f, 3.6f, 4.6f, encadre); rond(11.4f, 12.3f, 1.8f, encadre)
                rect(14.5f, 24.8f, 2.9f, 4.4f, encadre)
                rect(5.8f, 31.3f, 2.7f, 4f, encadre)
                val trou = sombre(pierre, 0.8f)
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

        // ---- Neimënster, l'abbaye du Grund (photo du propriétaire)

        fun neimensterPapier() {
            val r = Alea(125)
            val creme = duJour(0xFFF2E8D4.toInt())
            val ard = neutre(ardoise())
            val roche = neutre(duJour(0xFF9C9281.toInt()))
            // Le rocher du Bock ferme la vue à droite, et porte le rempart et
            // la ville haute sur sa corniche : sur la photo c'est la falaise
            // qui tient tout ce côté de l'image, pas une crête en travers.
            mineral {
                val falaise = chemin("M58 60C64 47 70 36 80 26C86 20 94 15.5 103 13L103 60Z")
                piece(falaise, roche)
                // Les lits du grès, parallèles à la corniche : sans eux la
                // falaise restait une dune de sable.
                c.save()
                c.clipPath(falaise)
                val lit = sombre(roche, 0.34f)
                val haut = mix(roche, 0xFFFFFFFF.toInt(), 0.17f)
                for (k in 0 until 10) {
                    val dy = k * 4.6f
                    trait("M54 ${50f + dy}C62 ${42f + dy} 70 ${33f + dy} 82 ${24f + dy}C90 ${18f + dy} 97 ${15f + dy} 106 ${13f + dy}",
                        if (k % 2 == 0) lit else haut, 1.7f, 0.85f)
                }
                // Les joints montants du rocher : chaque banc se débite en
                // blocs, décalés d'un banc à l'autre. Sans eux les lits seuls
                // donnaient des bandes lisses, pas du grès.
                val jointR = sombre(roche, 0.34f)
                for (k in 0 until 230) {
                    val px = 56f + r() * 50f
                    val py = 14f + r() * 44f
                    ligne(px, py, px + 0.45f, py + 1.9f + r() * 1.2f, jointR, 0.3f, 0.7f)
                    ligne(px + 0.55f, py, px + 1f, py + 1.5f, mix(roche, 0xFFFFFFFF.toInt(), 0.24f), 0.26f, 0.5f)
                }
                // Les diaclases : le grès du Bock se débite en gros blocs, et
                // la falaise est coupée de fentes verticales d'où sort la
                // verdure. Sans elles les lits seuls faisaient une dune rayée.
                val fente = sombre(roche, 0.55f)
                for (k in 0 until 11) {
                    val fx = 62f + k * 4f + (k % 3) * 1.6f
                    val fy = 46f - (fx - 60f) * 0.78f
                    val fh = 4f + (k % 4) * 3.2f
                    rect(fx, fy, 0.5f + (k % 2) * 0.35f, fh, fente, 0.75f)
                    rect(fx + 0.6f, fy, 0.35f, fh * 0.8f, mix(roche, 0xFFFFFFFF.toInt(), 0.22f), 0.5f)
                }
                // Les ressauts : la tranche éclairée d'un banc qui avance.
                for (k in 0 until 5) {
                    val rx = 64f + k * 7.5f
                    val ry = 44f - (rx - 60f) * 0.78f
                    pieceD("M$rx ${ry}C${rx + 4f} ${ry - 2.6f} ${rx + 8f} ${ry - 4.4f} ${rx + 12f} ${ry - 5.4f}" +
                        "L${rx + 12f} ${ry - 4.2f}C${rx + 8f} ${ry - 3.2f} ${rx + 4f} ${ry - 1.4f} $rx ${ry + 1.2f}Z",
                        mix(roche, 0xFFFFFFFF.toInt(), 0.26f), 0f)
                }
                // Le pied de la falaise, dans l'ombre des arbres du Grund.
                rect(56f, 38f, 50f, 24f, sombre(roche, 0.28f), 0.4f)
                c.restore()
            }
            // La ville haute, debout sur la corniche, et dessinée AVANT le
            // rempart : posée après et plus haut que lui, elle se lisait comme
            // une rangée de maisons suspendues au-dessus du vide
            // (propriétaire, 2026-11-15). C'est maintenant le mur qui lui cache
            // le pied, comme il le fait en vrai.
            // Des enduits pâles, pas les couleurs du Grund : vue d'en bas et
            // à contre-jour, la ville haute est une ligne claire sur le ciel.
            rue3q(RUE_NEIMENSTER, MAISONS_NEIMENSTER, intArrayOf(
                duJour(0xFFEDE8DC.toInt()), duJour(0xFFDAD6CC.toInt()), duJour(0xFFE7DBCB.toInt())
            ), r)
            mineral {
                // Le rempart, en vrai appareil : des blocs de grès taillés un
                // par un, en assises parallèles à la crête, décalées d'une
                // assise à l'autre. Des joints tracés sur un aplat donnaient
                // un grillage, pas de la pierre (propriétaire, 2026-11-15).
                val mur = neutre(duJour(0xFFC3BBA8.toInt()))
                val rouille = neutre(duJour(0xFFAC9574.toInt()))
                val quadMur = floatArrayOf(77f, 23f, 103f, 8.4f, 103f, 14f, 79f, 27.5f)
                piece(quad(quadMur), sombre(mur, 0.5f))
                surQuad(30f, 6f, quadMur) {
                    c.save()
                    c.clipRect(0f, 0f, 30f, 6f)
                    var by = 0f
                    var rang = 0
                    while (by < 6f) {
                        val bh = min(1.35f + r() * 0.5f, 6f - by)
                        var bx = -1f - (rang % 2) * 1.8f
                        while (bx < 30f) {
                            val bw = 2.4f + r() * 2.6f
                            val d = r()
                            val face = when {
                                d < 0.32f -> sombre(mur, 0.14f + r() * 0.26f)
                                d < 0.64f -> mix(mur, 0xFFFFFFFF.toInt(), 0.08f + r() * 0.18f)
                                else -> mix(mur, rouille, 0.35f + r() * 0.45f)
                            }
                            val w = bw - 0.34f
                            val h = bh - 0.3f
                            rect(bx, by, w, h, face)
                            // L'arête du bloc prend le jour, son pied reste dans le joint.
                            rect(bx, by, w, 0.13f, mix(face, 0xFFFFFFFF.toInt(), 0.32f), 0.75f)
                            rect(bx, by + h - 0.13f, w, 0.13f, sombre(face, 0.45f), 0.6f)
                            bx += bw
                        }
                        by += bh
                        rang++
                    }
                    // Le ruissellement : le pied du mur est plus sombre, le
                    // couronnement lavé par la pluie.
                    rect(0f, 4.3f, 30f, 1.7f, sombre(mur, 0.24f), 0.32f)
                    rect(0f, 0f, 30f, 0.7f, mix(mur, 0xFFFFFFFF.toInt(), 0.16f), 0.4f)
                    c.restore()
                }
                // Les créneaux, de la même pierre, avec leur ombre portée.
                var k = 0
                while (k < 9) {
                    val u = k / 9f
                    val cx = 78f + u * 24f
                    val cy = 24.6f - u * 11.4f
                    rect(cx, cy, 1.3f, 1.1f, mix(mur, 0xFFFFFFFF.toInt(), 0.08f))
                    rect(cx, cy + 1.1f, 1.3f, 0.3f, sombre(mur, 0.35f), 0.45f)
                    k++
                }
            }
            // La verdure qui sort des fentes du rocher, comme sur la photo.
            for (k in 0 until 9) {
                val gx = 64f + k * 4.2f
                val gy = 45f - (gx - 60f) * 0.78f
                touffe(nappe(duJour(0xFF4C7A30.toInt()), 0.6f), gx, gy, 1.2f + (k % 3) * 0.6f,
                    gx + 1.4f, gy + 0.8f, 1f, gx - 1.2f, gy + 0.7f, 0.9f)
            }
            // Les grands arbres derrière l'abbaye, au pied du rocher : sans
            // eux il restait entre les toits et la falaise une large bande de
            // ciel pâle qui se lisait comme un terrain vague.
            // Reculés par l'air : un bois de fond se tire vers la couleur du
            // ciel, sinon il vient au même plan que la friche du devant.
            val bois = mix(sombre(nappe(P.milieu, 0.78f), 0.1f), P.cielBas, if (P.nuit) 0.1f else 0.26f)
            lisiere(18f, 62f, { x -> 31f - (x - 18f) * 0.09f }, 2.2f, 3.4f, 133, bois)
            for (k in 0 until 12) {
                val ax = 20f + r() * 42f
                val ay = 28f + r() * 10f
                val ar = 1.8f + r() * 2.4f
                touffe(if (r() < 0.5f) sombre(bois, 0.22f) else mix(bois, 0xFFFFFFFF.toInt(), if (P.nuit) 0.03f else 0.1f),
                    ax, ay, ar, ax + ar * 0.9f, ay + ar * 0.45f, ar * 0.75f, ax - ar * 0.85f, ay + ar * 0.5f, ar * 0.7f)
            }
            plan(1)
            // Le versant boisé qui tapisse le pied du rocher.
            val versant = nappe(P.milieu, 0.74f)
            lisiere(40f, 103f, { x -> 40f - (x - 40f) * 0.26f }, 1.8f, 3f, 128, versant)
            for (k in 0 until 18) {
                val hx = 42f + r() * 60f
                val hy = 26f + (hx - 40f) * 0.32f + r() * 14f
                val hr = 1.4f + r() * 2f
                touffe(if (r() < 0.5f) sombre(versant, 0.3f) else mix(versant, 0xFFFFFFFF.toInt(), if (P.nuit) 0.03f else 0.1f),
                    hx, hy, hr, hx + hr * 0.9f, hy + hr * 0.4f, hr * 0.75f, hx - hr * 0.8f, hy + hr * 0.5f, hr * 0.7f)
            }
            // L'aile de l'abbaye, qui fuit vers la droite derrière l'église.
            rue3q(RUE_ABBAYE, ABBAYE, intArrayOf(creme), r)
            // L'église Saint-Jean du Grund : la grande toiture d'ardoise très
            // pentue sur sa nef, le clocher à gauche, et l'aiguille.
            mineral {
                val rose = neutre(duJour(0xFFEBD5C6.toInt()))
                val taille = pierreDeTaille()
                // Les maisons du Grund à l'extrême gauche, sous leurs mansardes.
                feuille(-4f, 33f, 11f, 15f, rose)
                c.save(); c.translate(-4f, 33f); matiere(11f, 15f, rose, taille, r, false); c.restore()
                pieceD("M-5 33.4L-3 25.6L8 25.6L10 33.4Z", ard)
                for (fx in floatArrayOf(-2.4f, 1.4f, 5.2f)) {
                    rect(fx - 0.35f, 35.65f, 2.5f, 3.5f, taille)
                    fenetreVerre(fx, 36f, 1.8f, 2.8f, r)
                }
                // La nef et son toit en pavillon, le plus gros volume de la scène.
                feuille(16f, 34f, 20f, 13f, neutre(creme))
                c.save(); c.translate(16f, 34f); matiere(20f, 13f, neutre(creme), taille, r, false); c.restore()
                pieceD("M14.6 34.4L20.5 21.2L32 21.2L37.4 34.4Z", ard)
                pieceD("M20.5 21.2L32 21.2L32 22.2L20.5 22.2Z", mix(ard, 0xFFFFFFFF.toInt(), 0.2f), 0f)
                for (lx in floatArrayOf(18.6f, 24.4f, 30.2f)) {
                    pieceD("M$lx 31.4L${lx + 1.3f} 28.6L${lx + 2.6f} 31.4Z", sombre(ard, 0.25f), 0.3f)
                    rect(lx + 0.8f, 30.4f, 1f, 1.4f, neutre(sombre(creme, 0.7f)))
                    if (P.allume) rect(lx + 0.8f, 30.4f, 1f, 1.4f, P.fenetre, 0.85f)
                }
                // Les baies de la nef dans leur encadrement de grès, avec appui.
                for (fx in floatArrayOf(18f, 23f, 28f, 33f)) {
                    rect(fx - 0.4f, 36.6f, 2.8f, 5.2f, taille)
                    rect(fx - 0.7f, 41.5f, 3.4f, 0.45f, mix(taille, 0xFFFFFFFF.toInt(), 0.2f))
                    fenetreVerre(fx, 37f, 2f, 4.4f, r)
                }
            }
            // Le clocher. Sa flèche est une aiguille d'ardoise très élancée,
            // sur un petit bulbe : c'est la silhouette qu'on reconnaît du
            // Grund, et non le bulbe trapu que j'avais dessiné de mémoire.
            boite3q(7.5f, 46f, 9f, 26f, 4f, creme, versDroite = true, pente = 0.1f, dessus = false) {
                // L'enduit, la corniche, le socle de pierre et les chaînes
                // d'angle : c'est d'eux que vient la matière d'un mur, pas
                // d'une texture posée dessus (leçon de la tour d'Esch-Sauer).
                matiere(9f, 26f, neutre(creme), pierreDeTaille(), r, false)
                val encadre = neutre(sombre(duJour(0xFFB4AEA1.toInt()), 0.2f))
                rond(4.3f, 9.6f, 2.4f, encadre)
                rond(4.3f, 9.6f, 1.8f, neutre(duJour(0xFFE8E2D2.toInt())))
                ligne(4.3f, 9.6f, 4.3f, 8.2f, encadre, 0.33f)
                ligne(4.3f, 9.6f, 5.4f, 10.1f, encadre, 0.3f)
                rect(0f, 13.4f, 9f, 0.7f, encadre, 0.7f)
                for (bx in floatArrayOf(1.8f, 5.4f)) {
                    rect(bx, 15.4f, 1.8f, 5f, encadre)
                    rect(bx + 0.3f, 16f, 1.2f, 4.4f, neutre(sombre(creme, 0.75f)))
                    if (P.allume) rect(bx + 0.3f, 17.4f, 1.2f, 3f, P.fenetre, 0.85f)
                }
            }
            mineral {
                // Le lanternon, le bulbe, puis l'aiguille.
                feuille(9.4f, 16.4f, 5.6f, 3.8f, neutre(creme))
                pieceD("M9 16.6C9 14.2 10.4 12.9 12.2 11.4C14 12.9 15.4 14.2 15.4 16.6Z", ard)
                pieceD("M10.6 11.8L12.2 1.2L13.8 11.8Z", ard)
                pieceD("M12.2 1.2L13.8 11.8L14.8 11.3L13.1 0.8Z", sombre(ard, 0.3f))
                ligne(12.2f, 1.2f, 12.2f, -0.8f, ard, 0.24f)
            }
            plan(2)
            poser(DEVANT)
            // La vallée, en friche luxuriante : sur la photo c'est elle qui
            // tient tout le premier plan, et non un parapet de pierre.
            val buisson = nappe(duJour(0xFF5A8A34.toInt()), 0.62f)
            lisiere(-3f, 103f, { x -> 43f + 2.6f * sin(x * 0.11f) }, 2.4f, 4f, 132, sombre(buisson, 0.12f))
            // Le chemin de gravier clair qui monte en serpentant vers la droite.
            val gravier = neutre(duJour(0xFFE7DFCC.toInt()))
            piece(Path().apply {
                moveTo(26f, DEVANT + 1f)
                cubicTo(33f, 52.5f, 44f, 49f, 58f, 44.5f)
                lineTo(65f, 42.2f)
                lineTo(67f, 43.4f)
                cubicTo(54f, 47.5f, 45f, 51.5f, 40f, DEVANT + 1f)
                close()
            }, gravier)
            // Les buissons qui bordent le chemin, étagés et de valeurs
            // différentes : une friche n'est pas un aplat.
            for (k in 0 until 34) {
                val bx = -4f + r() * 108f
                val by = 44f + r() * 15f
                // Le chemin reste dégagé.
                val surChemin = by > 44f && abs(bx - (30f + (58.5f - by) * 1.6f)) < 5f
                if (surChemin) continue
                val br = 1.6f + r() * 3.4f
                val d = r()
                val col = when {
                    d < 0.3f -> sombre(buisson, 0.22f + r() * 0.16f)
                    d < 0.62f -> buisson
                    else -> mix(buisson, 0xFFFFFFFF.toInt(), if (P.nuit) 0.04f else 0.14f)
                }
                touffe(col, bx, by, br, bx + br * 0.85f, by + br * 0.5f, br * 0.75f,
                    bx - br * 0.8f, by + br * 0.55f, br * 0.7f, bx + br * 0.2f, by - br * 0.5f, br * 0.6f)
            }
            // Le réverbère vert du chemin.
            mineral {
                val fonte = neutre(duJour(0xFF2F4034.toInt()))
                ligne(63f, 37.4f, 63f, 48f, fonte, 0.38f)
                rect(62.2f, 47.4f, 1.6f, 1.2f, fonte)
                if (P.allume) rond(63f, 35.6f, 3.2f, P.fenetre, 0.2f)
                // La cage de verre, son chapeau et son épi.
                forme("M61.5 37.2L61.9 33.8L64.1 33.8L64.5 37.2Z",
                    if (P.allume) P.fenetre else neutre(mix(duJour(0xFFBFD0CC.toInt()), P.pres, 0.3f)))
                pieceD("M60.9 33.9L63 31.8L65.1 33.9Z", fonte)
                ligne(63f, 31.8f, 63f, 30.6f, fonte, 0.26f)
            }
            c.restore()
        }

        // ---- Mëllerdall, le Schéissendëmpel (photo du propriétaire)

        fun mellerdallPapier() {
            // Le Schéissendëmpel, vu de l'aval : les deux parois de grès du
            // vallon, le petit pont jeté de l'une à l'autre, la trouée claire
            // que l'on voit par son arche, et les trois chutes qui tombent du
            // ressaut dans le bassin.
            //
            // Trois choses apprises en le refaisant.
            //
            // 1. Le grès du Mullerthal est **blond**. Peint avec `duJour`
            //    seul, qui tire vers le vert du feuillage, tout le vallon
            //    sortait en une seule masse d'olive.
            // 2. **Le vallon est un lieu sombre.** Tout s'y lit contre une
            //    seule tache de lumière, la trouée sous l'arche. Tant que la
            //    pierre du pont et la roche des rives ont eu la même valeur,
            //    la fenêtre entière se lisait comme un mur de soutènement à
            //    deux lunettes peintes.
            // 3. **Un pont se lit parce qu'il s'appuie sur quelque chose.**
            //    D'un bord à l'autre de la fenêtre, sans culée visible, ce
            //    n'est plus un pont : ce sont les deux parois qui le font.
            val r = Alea(135)
            val gres = neutre(duJour(0xFFD5C29E.toInt()))
            val joint = sombre(gres, 0.45f)
            val roche = neutre(duJour(0xFF9A8870.toInt()))
            val paroi = sombre(roche, 0.46f)
            val bloc = sombre(roche, 0.3f)
            val feuillage = vert(0.2f)
            val canopee = sombre(vert(0.1f), 0.42f)
            val mousse = vert(0.52f)
            val mousseClaire = mix(mousse, 0xFFCDE05A.toInt(), if (P.nuit) 0.08f else 0.3f)
            // Ce que l'on voit par l'arche doit être **plus sombre** que la
            // pierre : une ouverture claire de la même valeur que le tympan
            // n'est pas une ouverture, c'est un ovale peint sur un mur.
            // Seule une mince brume de lumière reste à la clé, pour la
            // profondeur.
            val lointain = sombre(vert(0.28f), if (P.nuit) 0.15f else 0.34f)
            val brumeDuFond = mix(lointain, if (P.nuit) P.cielBas else 0xFFEAF2E0.toInt(), if (P.nuit) 0.4f else 0.62f)

            // ---- Le fond : la trouée, ses fûts, et la hêtraie par rideaux
            // de plus en plus sombres en descendant vers l'eau.
            pieceD("M-3 44L-3 -3L103 -3L103 44Z", brumeDuFond)
            for (k in 0 until 11) {
                val x = 4f + k * 9f + r() * 5f
                val h = 13f + r() * 15f
                val w = 0.5f + r() * 0.6f
                pieceD("M$x 40L${x + w * 0.5f} ${40f - h}L${x + w * 1.15f} ${40f - h}L${x + w * 1.7f} 40Z",
                    mix(neutre(duJour(0xFF8C7C64.toInt())), brumeDuFond, 0.4f + r() * 0.3f), 0.25f)
            }
            lisiere(-3f, 103f, { x -> 18f + 1.8f * sin(x * 0.19f + 1.1f) }, 1f, 1.8f, 141, lointain)
            lisiere(-3f, 103f, { x -> 25f + 1.4f * sin(x * 0.23f + 2.2f) }, 0.9f, 1.6f, 138, sombre(lointain, 0.3f))
            // La frange du houppier, qui ferme le ciel.
            val frange = Path()
            frange.moveTo(-3f, -3f)
            var fx = -3f
            while (fx <= 103f) { frange.lineTo(fx, 3.6f + 2f * sin(fx * 0.14f) + 1f * sin(fx * 0.47f)); fx += 1.5f }
            frange.lineTo(103f, -3f); frange.close()
            frange.op(bosses(-3f, 103f, { x -> 3.6f + 2f * sin(x * 0.14f) }, 1.4f, 2.6f, Alea(137)), Path.Op.UNION)
            piece(frange, canopee)
            plan(1)
            // ---- Le pont : un tablier mince sur une seule grande arche, de
            // trois quarts, qui monte vers la droite.
            fun tablier(x: Float) = 6.2f + (80f - x) * 0.09f
            val ARCHE = floatArrayOf(52f, 26f, 29f, 14.5f)  // centre, demi-portée, naissance, flèche
            fun arche(a: FloatArray): Path {
                val t = Path()
                t.moveTo(a[0] - a[1], a[2])
                for (k in 0..28) {
                    val u = PI.toFloat() * k / 28f
                    t.lineTo(a[0] - cos(u) * a[1], a[2] - sin(u) * a[3])
                }
                t.close()
                return t
            }
            // ---- Le pont est en **vieilles pierres apparentes, recouvertes
            // de mousse** (propriétaire, d'après la photo) : des blocs
            // irréguliers, de tons tous différents, chacun posé sur un lit
            // de mortier sombre qui reparaît en joint. Un aplat rayé de
            // traits fins faisait de la pierre de taille neuve, pas un vieux
            // pont de meunier du Mullerthal.
            val pierres = Alea(145)
            val mortier = sombre(gres, 0.52f)
            fun ton(k: Float): Int = when {
                k < 0.30f -> sombre(gres, 0.14f + k * 0.5f)
                k < 0.58f -> mix(gres, 0xFFFFFFFF.toInt(), 0.1f + k * 0.2f)
                k < 0.80f -> mix(gres, mousse, 0.18f + k * 0.26f)   // la pierre lichénée
                else -> mix(gres, neutre(duJour(0xFF8C7A5E.toInt())), 0.3f + k * 0.4f)
            }
            /** Un bloc, rentré de son joint : le mortier du dessous fait le creux. */
            fun bloc(pts: FloatArray, col: Int) {
                var cx = 0f; var cy = 0f
                for (i in pts.indices step 2) { cx += pts[i]; cy += pts[i + 1] }
                cx /= pts.size / 2; cy /= pts.size / 2
                val d = Path()
                for (i in pts.indices step 2) {
                    val x = cx + (pts[i] - cx) * 0.9f
                    val y = cy + (pts[i + 1] - cy) * 0.84f
                    if (i == 0) d.moveTo(x, y) else d.lineTo(x, y)
                }
                d.close()
                piece(d, col)
            }
            fun dansArche(x: Float, y: Float, marge: Float): Boolean {
                if (y > ARCHE[2]) return false
                val u = (x - ARCHE[0]) / (ARCHE[1] + marge)
                val v = (ARCHE[2] - y) / (ARCHE[3] + marge)
                return u * u + v * v < 1f
            }
            val claveau = 3.4f
            mineral {
                val corps = chemin("M-4 ${tablier(-4f)}L104 ${tablier(104f)}L104 32L-4 32Z")
                corps.op(arche(ARCHE), Path.Op.DIFFERENCE)
                appareil(corps, gres, 2.6f, 4.6f, 145, -0.09f)
                // La ceinture de claveaux, qui donne son épaisseur à l'arche.
                for (k in 0 until 17) {
                    val u0 = PI.toFloat() * k / 17f
                    val u1 = PI.toFloat() * (k + 1) / 17f
                    bloc(floatArrayOf(
                        ARCHE[0] - cos(u0) * ARCHE[1], ARCHE[2] - sin(u0) * ARCHE[3],
                        ARCHE[0] - cos(u1) * ARCHE[1], ARCHE[2] - sin(u1) * ARCHE[3],
                        ARCHE[0] - cos(u1) * (ARCHE[1] + claveau), ARCHE[2] - sin(u1) * (ARCHE[3] + claveau),
                        ARCHE[0] - cos(u0) * (ARCHE[1] + claveau), ARCHE[2] - sin(u0) * (ARCHE[3] + claveau)),
                        ton(pierres()))
                }
                // Le couronnement : une assise de dalles plus longues et
                // plus claires, qui coiffe le parapet.
                var cx2 = -6f
                while (cx2 < 104f) {
                    val w = 5.4f + pierres() * 3.6f
                    bloc(floatArrayOf(cx2, tablier(cx2), cx2 + w, tablier(cx2 + w),
                        cx2 + w, tablier(cx2 + w) + 2.2f, cx2, tablier(cx2) + 2.2f),
                        mix(ton(pierres()), 0xFFFFFFFF.toInt(), 0.16f))
                    cx2 += w
                }
                // L'intrados : le dessous de l'arche est dans l'ombre.
                for (k in 0 until 28) {
                    val u0 = PI.toFloat() * k / 28f
                    val u1 = PI.toFloat() * (k + 1) / 28f
                    ligne(ARCHE[0] - cos(u0) * ARCHE[1], ARCHE[2] - sin(u0) * ARCHE[3],
                        ARCHE[0] - cos(u1) * ARCHE[1], ARCHE[2] - sin(u1) * ARCHE[3], sombre(gres, 0.6f), 0.9f, 0.85f)
                }
            }
            // ---- La mousse qui recouvre le pont. Peinte hors de [mineral],
            // pour que les saisons la trouvent : c'est de la végétation.
            run {
                // La coiffe du couronnement, épaisse et festonnée, qui
                // déborde du parapet.
                // Elle court par plaques, interrompue : une bande verte
                // continue se lit comme une haie posée sur le parapet.
                val r2 = Alea(148)
                var x = -5f
                while (x < 104f) {
                    val l = 6f + r2() * 16f
                    val e = 1.3f + r2() * 1.6f
                    val plaque = Path()
                    plaque.moveTo(x, tablier(x) - 0.7f)
                    plaque.lineTo(x + l, tablier(x + l) - 0.7f)
                    var u = x + l
                    while (u >= x) { plaque.lineTo(u, tablier(u) + e + 0.6f * sin(u * 0.9f)); u -= 1.2f }
                    plaque.close()
                    plaque.op(bosses(x, x + l, { v -> tablier(v) + e * 0.6f }, 0.7f, 1.4f, Alea(147 + x.toInt())), Path.Op.UNION)
                    piece(plaque, if (r2() < 0.5f) mousse else mousseClaire)
                    x += l + 1.5f + r2() * 7f
                }
                // Les plaques accrochées au tympan, dans les joints, et les
                // coulées qui en descendent.
                for (k in 0 until 22) {
                    val mx = -4f + r2() * 108f
                    val my = tablier(mx) + 2.6f + r2() * 14f
                    if (dansArche(mx, my, claveau + 1f)) continue
                    // Une plaque étalée, pas une boule : la mousse suit la
                    // pierre, elle ne s'y colle pas en pompon.
                    val rr = 0.7f + r2() * 1.5f
                    touffe(if (k % 3 == 0) mousseClaire else mousse,
                        mx, my, rr, mx + rr * 1.3f, my + rr * 0.3f, rr * 0.8f,
                        mx - rr * 1.3f, my + rr * 0.35f, rr * 0.75f,
                        mx + rr * 0.5f, my + rr * 0.6f, rr * 0.6f, mx - rr * 0.5f, my + rr * 0.55f, rr * 0.55f)
                    if (r2() < 0.45f) {
                        val mh = 2.4f + r2() * 5f
                        pieceD("M${mx - 1f} ${my}C${mx - 1.3f} ${my + mh * 0.5f} ${mx - 0.5f} ${my + mh * 0.8f} ${mx} ${my + mh}" +
                            "C${mx + 0.6f} ${my + mh * 0.8f} ${mx + 1.3f} ${my + mh * 0.5f} ${mx + 1f} ${my}Z", mousse, 0.3f)
                    }
                }
                // La mousse de l'extrados, qui souligne l'arche, et les
                // touffes qui pendent de son dessous.
                for (k in 0 until 17) {
                    val u = PI.toFloat() * (k + 0.5f) / 17f
                    val rr = claveau + 0.2f + r2() * 1.3f
                    rond(ARCHE[0] - cos(u) * (ARCHE[1] + rr), ARCHE[2] - sin(u) * (ARCHE[3] + rr),
                        0.9f + r2() * 0.9f, if (k % 3 == 0) mousseClaire else mousse, 0.85f)
                }
                for (k in 0 until 7) {
                    val u = PI.toFloat() * (0.12f + 0.76f * k / 6f)
                    val bx = ARCHE[0] - cos(u) * (ARCHE[1] - 0.4f)
                    val by = ARCHE[2] - sin(u) * (ARCHE[3] - 0.4f)
                    pieceD("M${bx - 1.2f} ${by}C${bx - 1.4f} ${by + 1.6f} ${bx - 0.3f} ${by + 2.4f} ${bx} ${by + 3.2f}" +
                        "C${bx + 0.4f} ${by + 2.2f} ${bx + 1.4f} ${by + 1.4f} ${bx + 1f} ${by}Z", mousse, 0.3f)
                }
            }
            // Le garde-corps de branches : des perches brutes, pas une grille.
            val bois = neutre(duJour(0xFF7A6246.toInt()))
            mineral {
                ligne(-4f, tablier(-4f) - 5f, 104f, tablier(104f) - 5f, bois, 0.52f)
                ligne(-4f, tablier(-4f) - 2.5f, 104f, tablier(104f) - 2.5f, bois, 0.38f)
                var x = -2f
                var k = 0
                while (x < 104f) {
                    ligne(x, tablier(x), x, tablier(x) - 5.2f, bois, 0.46f)
                    if (k % 2 == 0) ligne(x, tablier(x) - 0.4f, x + 6.4f, tablier(x + 6.4f) - 4.8f, bois, 0.26f)
                    x += 6.4f; k++
                }
            }
            // ---- Les deux parois du vallon, qui portent les culées. Elles
            // tiennent les colonnes que la plaque du mot laisse libres.
            mineral {
                pieceD("M103 54L103 -3L84.4 -3L81.6 7L87.4 11L79.6 17L86 24L78.4 31L84.8 38L78 45L84 54Z", paroi)
                // Les surplombs : le grès se creuse en abris, et c'est ce
                // décrochement qui dit la paroi plutôt qu'un pan de mur.
                for (d in arrayOf("M103 11L87.4 11L79.6 17L88.6 15L103 16Z",
                        "M103 24L86 24L78.4 31L87.6 28L103 29Z", "M103 38L84.8 38L78 45L87 42L103 43Z"))
                    pieceD(d, sombre(paroi, 0.3f), 0.3f)
                val creux = sombre(paroi, 0.3f)
                for (y in floatArrayOf(3f, 7.5f, 19f, 26f, 33f, 40f, 48f))
                    ligne(103f, y - 1f, 83f, y + 0.4f, creux, 0.28f, 0.6f)
            }
            for (d in arrayOf("M103 -3L84.4 -3L83.6 2.8L92 1.2L103 2.2Z",
                    "M103 16L88.6 15L79.6 17L80.4 20L88 18L103 19Z",
                    "M103 29L87.6 28L78.4 31L79.2 34L87 31.6L103 32.6Z"))
                pieceD(d, mousseClaire, 0.3f)
            plan(2)
            solNaturel(SolNaturel.BERGE)  // les galets du bord, au premier plan
            poser(DEVANT)
            // ---- Le lit du bassin, que l'eau transparente laisse voir.
            val lit = neutre(duJour(0xFF9C8E74.toInt()))
            val bassin = "M-3 62L-3 49.4C14 47.4 34 47.2 50 47.6C68 48 86 47 103 48.2L103 62Z"
            mineral { pieceD(bassin, lit, 0.3f) }
            val galets = intArrayOf(neutre(duJour(0xFFBCB19B.toInt())), neutre(duJour(0xFF9A907C.toInt())),
                neutre(duJour(0xFFCBC1A8.toInt())), neutre(duJour(0xFF847B6A.toInt())))
            for (k in 0 until 44) {
                val gx = r() * 104f - 2f
                val gy = 49.4f + r() * 9f
                val t = 0.6f + (gy - 49f) / 9f
                ovalePapier(gx, gy, (0.9f + r() * 1.1f) * t, (0.5f + r() * 0.5f) * t, galets[k % 4], 0.2f)
            }
            // ---- Le ressaut : des blocs d'altitudes franchement
            // différentes. Alignés, ils refont une assise de mur.
            mineral {
                for ((i, d) in ROCHERS_MELLERDALL.withIndex())
                    pieceD(d, if (i % 2 == 0) bloc else sombre(bloc, 0.2f))
                val rocheJoint = sombre(bloc, 0.4f)
                for (y in floatArrayOf(43f, 46.5f, 50f)) {
                    ligne(14f, y - 0.5f, 22f, y, rocheJoint, 0.28f, 0.7f)
                    ligne(26f, y - 4.5f, 45f, y - 4f, rocheJoint, 0.3f, 0.7f)
                    ligne(51f, y - 0.5f, 76f, y, rocheJoint, 0.3f, 0.7f)
                    ligne(84f, y - 2f, 103f, y - 1.6f, rocheJoint, 0.3f, 0.7f)
                }
            }
            for ((i, d) in MOUSSES_MELLERDALL.withIndex())
                pieceD(d, if (i % 2 == 0) mousse else mix(mousse, mousseClaire, 0.55f), 0.3f)
            // Les traînées humides sous les coiffes : le grès est mouillé
            // au-dessous, et c'est ce qui l'empêche d'être une bande unie.
            for (f in arrayOf(floatArrayOf(16f, 40f, 48f), floatArrayOf(29f, 35f, 46f), floatArrayOf(38f, 35f, 48f),
                    floatArrayOf(58f, 40f, 49f), floatArrayOf(67f, 39f, 47f), floatArrayOf(89f, 38f, 48f),
                    floatArrayOf(97f, 39f, 49f)))
                ligne(f[0], f[1], f[0] + 0.6f, f[2], sombre(bloc, 0.4f), 0.5f, 0.45f)
            // ---- Les chutes. Un blanc bleuté : le grain chaud du papier
            // jaunirait un blanc pur.
            val eauBlanche = if (P.nuit) mix(P.cielBas, 0xFFFFFFFF.toInt(), 0.58f) else 0xFFEFF6FF.toInt()
            val voileEau = mix(eauBlanche, P.eau, 0.42f)
            val ecume = mix(eauBlanche, 0xFFFFFFFF.toInt(), 0.4f)
            for (ch in CHUTES_MELLERDALL) {
                val x = ch[0]; val w = ch[1]; val haut = ch[2]; val bas = ch[3]
                val mi = (haut + bas) / 2f
                // La lèvre de pierre, puis le voile qui s'élargit en tombant.
                ligne(x - w * 0.8f, haut + 0.1f, x + w * 0.8f, haut + 0.1f, sombre(bloc, 0.45f), 0.7f, 0.8f)
                forme("M${x - w * 0.5f} ${haut}C${x - w * 0.24f} ${haut - 0.9f} ${x + w * 0.24f} ${haut - 0.9f} ${x + w * 0.5f} ${haut}" +
                    "C${x + w * 0.5f} $mi ${x + w * 0.5f} ${bas - 3f} ${x + w * 0.48f} $bas" +
                    "L${x - w * 0.48f} ${bas}C${x - w * 0.5f} ${bas - 3f} ${x - w * 0.5f} $mi ${x - w * 0.5f} ${haut}Z",
                    voileEau, 0.34f)
                // Les filets, séparés : entre eux on voit le rocher.
                for (k in 0 until 3) {
                    val t = (k - 1f)
                    trait("M${x + t * w * 0.3f} ${haut + 0.3f}C${x + t * w * 0.34f} ${haut + (bas - haut) * 0.4f} " +
                        "${x + t * w * 0.3f} ${haut + (bas - haut) * 0.72f} ${x + t * w * 0.34f} ${bas - 0.4f}",
                        if (k == 1) eauBlanche else voileEau, 0.26f, 0.6f)
                }
                // L'écume au pied : une masse découpée, pas un halo.
                forme("M${x - w * 1.1f} ${bas + 1f}C${x - w * 0.95f} ${bas - 1.4f} ${x + w * 0.95f} ${bas - 1.4f} ${x + w * 1.1f} ${bas + 1f}" +
                    "C${x + w * 0.55f} ${bas + 2.4f} ${x - w * 0.55f} ${bas + 2.4f} ${x - w * 1.1f} ${bas + 1f}Z", ecume, 0.7f)
            }
            // ---- L'eau du bassin : une teinte posée en voile sur le lit,
            // et des rides qui partent du pied de chaque chute.
            val eau = mix(P.eau, 0xFF86C4C0.toInt(), if (P.nuit) 0.18f else 0.5f)
            forme(bassin, eau, if (P.nuit) 0.48f else 0.4f)
            val ride = mix(eau, 0xFFFFFFFF.toInt(), 0.62f)
            for (ch in CHUTES_MELLERDALL) for (k in 1..3) {
                val rr = ch[1] * (1.8f + k * 1.1f)
                trait("M${ch[0] - rr} ${ch[3] + k * 1.8f}C${ch[0] - rr * 0.4f} ${ch[3] + k * 1.8f + 1.4f} " +
                    "${ch[0] + rr * 0.4f} ${ch[3] + k * 1.8f + 1.4f} ${ch[0] + rr} ${ch[3] + k * 1.8f}",
                    ride, 0.3f, 0.4f - k * 0.07f)
            }
            // ---- La paroi de gauche, au premier plan : une masse sombre
            // et proche, qui donne sa profondeur au vallon et occupe la
            // colonne que la plaque du mot laisse libre.
            val proche = sombre(roche, 0.6f)
            mineral {
                pieceD("M-3 60L-3 -3L19.4 -3L22.6 7L16.4 12L25 19L17.8 27L26.2 35L19 43L26.4 51L20 60Z", proche)
                for (d in arrayOf("M-3 12L16.4 12L25 19L15 16.4L-3 17.4Z", "M-3 27L17.8 27L26.2 35L16 32L-3 33Z",
                        "M-3 43L19 43L26.4 51L16.6 48L-3 49Z"))
                    pieceD(d, sombre(proche, 0.3f), 0.3f)
                val veine = sombre(proche, 0.28f)
                for (y in floatArrayOf(3f, 9f, 22f, 31f, 39f, 47f, 55f)) ligne(-3f, y, 24f, y + 1.6f, veine, 0.34f, 0.6f)
            }
            for (d in arrayOf("M-3 -3L19.4 -3L20.2 2.8L9 1.2L-3 2.2Z", "M-3 17.4L15 16.4L25 19L24.2 22.4L15.4 19.4L-3 20.6Z",
                    "M-3 33L16 32L26.2 35L25.4 38.4L16.4 35.4L-3 36.4Z"))
                pieceD(d, mousse, 0.3f)
            if (!vivant) for (ch in CHUTES_MELLERDALL) {
                brume(ch[0], ch[3], ch[1] * 1.5f, 1.8f, 0.34f)
                brume(ch[0], ch[3] - 3f, ch[1] * 1.1f, 1.8f, 0.2f)
            }
            c.restore()
        }

        /**
         * Une brume d'eau : un ovale blanc qui s'efface vers ses bords, de
         * [rx] sur [ry], centré en ([cx], [cy]). Pas de carton : c'est de l'air.
         */
        fun brume(cx: Float, cy: Float, rx: Float, ry: Float, o: Float) {
            if (!actif || o <= 0f) return
            val blanc = if (P.nuit) 0xFFDDE6F4.toInt() else 0xFFFFFFFF.toInt()
            c.save()
            c.translate(cx, cy)
            c.scale(1f, ry / rx)
            p.style = Paint.Style.FILL
            p.color = 0xFF000000.toInt()
            p.alpha = (o.coerceIn(0f, 1f) * 255f).roundToInt()
            p.shader = RadialGradient(0f, 0f, rx, intArrayOf(blanc, blanc and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            c.drawCircle(0f, 0f, rx, p)
            p.shader = null
            c.restore()
        }

        // ---- Stad, le Grund au bord de l'Alzette (photo du propriétaire)

        fun stadPapier() {
            val r = Alea(145)
            // ---- Le rocher, le rempart et la ville haute.
            //
            // Au Grund, **la ville est au-dessus** : c'est tout le lieu. Le
            // rocher et le rempart étaient bien dessinés, mais sous la ligne
            // des toits : la rangée de maisons les cachait entièrement, et la
            // carte montrait une bourgade au bord d'une rivière, n'importe où
            // en Europe. Ils montent maintenant dans la bande du haut, celle
            // que la plaque du mot laisse libre.
            val distance = mix(P.cielBas, neutre(P.cielBas), 0.6f)
            fun loin(col: Int, t: Float) = mix(neutre(duJour(col)), distance, if (P.nuit) t * 0.5f else t)
            val rocher = loin(0xFF8E8775.toInt(), 0.24f)
            val rempart = loin(0xFFC2BBA8.toInt(), 0.28f)
            fun crete(x: Float) = 25.8f - (x - 18f) * 0.036f
            fun hautRempart(x: Float) = crete(x) - 9.6f
            mineral {
                pieceD("M14 52L17 ${crete(17f)}C40 ${crete(40f) - 1.4f} 70 ${crete(70f) - 1.6f} 103 ${crete(103f)}L103 52Z", rocher)
                // Les strates du grès, et les grandes diaclases verticales
                // qui font la falaise du Bock plutôt qu'un talus.
                for (y in floatArrayOf(27f, 32f, 37f, 42f))
                    trait("M15 ${y}C40 ${y - 2.6f} 70 ${y - 3.2f} 103 ${y - 2.4f}", sombre(rocher, 0.3f), 0.4f, 0.55f)
                for (x in floatArrayOf(24f, 39f, 52f, 68f, 83f, 96f))
                    ligne(x, crete(x) + 1.2f, x - 1.4f, 46f, sombre(rocher, 0.34f), 0.35f, 0.5f)
                // Le rempart qui le couronne, et ses arcades aveugles.
                appareil(chemin("M17 ${crete(17f)}C40 ${crete(40f) - 1.4f} 70 ${crete(70f) - 1.6f} 103 ${crete(103f)}" +
                    "L103 ${hautRempart(103f)}L17 ${hautRempart(17f)}Z"), rempart, 2.3f, 4.4f, 360, -0.036f)
                val creux = sombre(rempart, 0.44f)
                // Le socle, plus sombre : un rempart porte, et cela se voit
                // au pied. Sans lui c'est une bande d'ocre d'un bord à l'autre.
                pieceD("M17 ${crete(17f)}C40 ${crete(40f) - 1.4f} 70 ${crete(70f) - 1.6f} 103 ${crete(103f)}" +
                    "L103 ${crete(103f) - 2.6f}C70 ${crete(70f) - 4f} 40 ${crete(40f) - 3.8f} 17 ${crete(17f) - 2.6f}Z",
                    sombre(rempart, 0.3f))
                for (bx in floatArrayOf(22f, 37f, 52f, 67f, 82f, 94f)) {
                    val w = 10.5f
                    val bas = crete(bx) - 3f
                    val naissance = hautRempart(bx) + 6.4f
                    pieceD("M$bx ${bas}L$bx ${naissance}C$bx ${naissance - 5.6f} ${bx + w} ${naissance - 5.6f} ${bx + w} ${naissance}L${bx + w} ${bas}Z", creux)
                    pieceD("M${bx + 0.9f} ${bas}L${bx + 0.9f} ${naissance}C${bx + 0.9f} ${naissance - 4.7f} ${bx + w - 0.9f} ${naissance - 4.7f} ${bx + w - 0.9f} ${naissance}L${bx + w - 0.9f} ${bas}Z",
                        sombre(rempart, 0.6f), 0.3f)
                }
                // L'arête du chemin de ronde.
                pieceD("M17 ${hautRempart(17f)}L103 ${hautRempart(103f)}L103 ${hautRempart(103f) + 1.4f}L17 ${hautRempart(17f) + 1.4f}Z",
                    mix(rempart, 0xFFFFFFFF.toInt(), 0.22f))
                pieceD("M17 ${hautRempart(17f) + 1.4f}L103 ${hautRempart(103f) + 1.4f}L103 ${hautRempart(103f) + 2.6f}L17 ${hautRempart(17f) + 2.6f}Z",
                    sombre(rempart, 0.34f), 0.3f)
            }
            // ---- La ville haute, posée sur le rempart : **une silhouette**,
            // d'un seul tenant (propriétaire, 2026-11-16). Au dernier plan,
            // des maisons modelées une à une — mur coloré, toit, fenêtres —
            // se lisaient comme une rangée de cabines : autant de détail
            // qu'au premier plan, à une échelle où l'œil ne demande qu'un
            // profil. Une découpe unique rend la distance.
            run {
                val ville = Path()
                var hx = 15f
                while (hx < 103f) {
                    val w = 3.2f + r() * 3.8f
                    val h = 2.4f + r() * 5.2f
                    val th = h * (0.4f + r() * 0.35f)
                    val bas = hautRempart(hx) + 0.8f
                    ville.addRect(hx, bas - h, hx + w, bas, Path.Direction.CW)
                    if (r() < 0.45f) {
                        ville.moveTo(hx - 0.5f, bas - h)
                        ville.lineTo(hx + w / 2f, bas - h - th)
                        ville.lineTo(hx + w + 0.5f, bas - h)
                        ville.close()
                    } else {
                        ville.addRect(hx - 0.5f, bas - h - th * 0.5f, hx + w + 0.5f, bas - h, Path.Direction.CW)
                    }
                    hx += w + 0.4f + r() * 1.5f
                }
                // Le beffroi fait partie de la même découpe : détaché, il
                // redevenait un objet, et la silhouette un décor derrière lui.
                val hb = hautRempart(65f)
                ville.addRect(62f, hb - 9f, 68f, hb + 0.8f, Path.Direction.CW)
                ville.moveTo(61f, hb - 9f); ville.lineTo(65f, hb - 15f); ville.lineTo(69f, hb - 9f); ville.close()
                // Une silhouette doit être **plus sombre** que son fond, et
                // il faut partir de bien plus sombre qu'on ne croit : le
                // calque du fond s'estompe de 30 % vers le ciel, qui au
                // matin est jaune. Un gris-bleu moyen en ressortait ocre,
                // exactement de la valeur du rempart, et la ville haute
                // disparaissait dans son mur.
                mineral { piece(ville, mix(neutre(duJour(0xFF232A38.toInt())), distance, 0.1f)) }
                // La nuit, les seules fenêtres allumées : une silhouette noire
                // au-dessus d'un rempart éclairé serait une ville morte.
                if (P.allume) mineral {
                    val ra = Alea(152)
                    for (k in 0 until 26) {
                        val x = 16f + ra() * 85f
                        val y = hautRempart(x) - 1f - ra() * 5f
                        rect(x, y, 0.8f, 1.1f, P.fenetre, 0.75f)
                    }
                    rect(63.8f, hb - 6.6f, 1.4f, 2.2f, P.fenetre, 0.9f)
                }
            }
            // La végétation qui s'accroche au pied du rocher et dans ses
            // fissures : au Grund, le rocher n'est jamais nu.
            run {
                var vx = 12f
                while (vx < 103f) {
                    val l = 5f + r() * 11f
                    lisiere(vx, vx + l, { x -> crete(x) + 1.2f + 1.4f * sin(x * 0.33f) }, 1.1f, 2f, 147 + vx.toInt(), vert(0.42f))
                    vx += l + 3f + r() * 9f
                }
            }
            for (k in 0 until 9) {
                val tx = 16f + k * 10f + r() * 5f
                touffe(vert(0.5f + (k % 3) * 0.08f), tx, crete(tx) + 4f + r() * 9f, 1.5f + r() * 1.3f,
                    tx + 2f, crete(tx) + 6f + r() * 8f, 1.2f + r() * 1f)
            }
            lisiere(-2f, 20f, { x -> 24f + 3f * sin(x * 0.2f) }, 1.8f, 2.6f, 146, nappe(mix(P.loin, P.milieu, 0.4f), 0.3f, VERT_CALME))
            plan(1)
            // ---- Le clocher de Saint-Jean-du-Grund : la flèche d'ardoise
            // qui monte au-dessus des toits est le seul trait qui dise
            // « Grund » du premier coup d'œil.
            mineral {
                val tour = neutre(duJour(0xFFE6DCC4.toInt()))
                pieceD("M30.8 46L30.8 16.6L37.2 16.6L37.2 46Z", tour)
                pieceD("M29.9 16.6L38.1 16.6L38.1 14.4L29.9 14.4Z", mix(tour, 0xFFFFFFFF.toInt(), 0.22f))
                for (fx in floatArrayOf(31.6f, 34.6f)) {
                    pieceD("M$fx 24.6L$fx 21.8C$fx 20.3 ${fx + 1.8f} 20.3 ${fx + 1.8f} 21.8L${fx + 1.8f} 24.6Z",
                        if (P.allume) P.fenetre else sombre(tour, 0.45f))
                }
                rond(34f, 29.4f, 1.9f, mix(tour, 0xFFFFFFFF.toInt(), 0.5f))
                rond(34f, 29.4f, 1.9f, sombre(tour, 0.4f), 0.25f)
                ligne(34f, 29.4f, 34f, 28.1f, sombre(tour, 0.6f), 0.28f)
                ligne(34f, 29.4f, 35.1f, 29.9f, sombre(tour, 0.6f), 0.24f)
                // La flèche : une aiguille, pas un chapeau. Celle de
                // Saint-Jean est très effilée, avec un petit lanternon.
                val tuile = sombre(neutre(ardoise()), 0.15f)
                pieceD("M30.4 14.4L34 11.4L37.6 14.4Z", tuile)
                pieceD("M31.4 11.4L36.6 11.4L36.6 8.6L31.4 8.6Z", mix(tuile, 0xFFFFFFFF.toInt(), 0.18f))
                pieceD("M30.6 8.6L34 -1.6L37.4 8.6Z", tuile)
                ligne(34f, -1.6f, 34f, -4.4f, sombre(tuile, 0.3f), 0.3f)
                ligne(32.9f, -3.2f, 35.1f, -3.2f, sombre(tuile, 0.3f), 0.26f)
            }
            rue3q(RUE_GRUND, MAISONS_GRUND_3Q, facades(), r)
            touffe(vert(0.6f), 58f, 43.5f, 2.6f, 62f, 42.5f, 3f, 66f, 43.4f, 2.2f)
            plan(2)
            // L'Alzette, entre les deux quais qui fuient : son bord lointain
            // et le bord du quai d'où l'on regarde montent vers la droite.
            val eau = mix(P.eau, 0xFF5F7046.toInt(), if (P.nuit) 0.25f else 0.45f)
            val g = RUE_GRUND
            val riviere = Path().apply {
                moveTo(-2f, quaiProche(-2f) + 0.6f); lineTo(-2f, g.eau(-2f))
                var x = -2f
                while (x <= 102f) { lineTo(x, g.eau(x)); x += 4f }
                lineTo(102f, quaiProche(102f) + 0.6f); close()
            }
            piece(riviere, eau, 0.4f)
            // Les façades se reflètent : leurs couleurs tremblent dans l'eau,
            // coupées de rides claires, plus courtes vers le fond.
            c.save()
            c.clipPath(riviere)
            val murs = facades()
            var u = 0f
            for ((i, m) in MAISONS_GRUND_3Q.withIndex()) {
                val x0 = g.x(u); val x1 = g.x(u + m[0])
                val e = g.echelle(u)
                val haut = g.eau(x0) + 0.3f
                val long = m[1] * 0.55f * e
                // Le reflet en traits couchés qui tremblent, de plus en plus
                // pâles en s'éloignant de la façade.
                val teinteReflet = neutre(mix(murs[i % murs.size], eau, 0.3f))
                var dy = 0f
                var j = 0
                while (dy < long) {
                    val decale = sin(j * 2.3f + i) * 0.5f * e
                    val o = (if (P.nuit) 0.22f else 0.42f) * (1f - dy / long * 0.7f)
                    ligne(x0 + 0.3f + decale, haut + dy, x1 - 0.3f + decale, g.eau(x1) + 0.3f + dy * 0.9f, teinteReflet, 0.55f * e + 0.15f, o)
                    dy += 0.85f * e + 0.2f; j++
                }
                if (P.allume) rect((x0 + x1) / 2f - 0.3f, haut + 0.4f, 0.6f, long * 0.8f, P.fenetre, 0.35f)
                u += m[0]
            }
            val ride = mix(eau, 0xFFFFFFFF.toInt(), if (P.nuit) 0.18f else 0.42f)
            val ra = Alea(149)
            for (k in 0 until 26) {
                val x = ra() * 100f
                val y = g.eau(x) + 0.8f + ra() * (quaiProche(x) - g.eau(x) - 1f)
                val l = 1.5f + ra() * 4f * (y - 40f) / 18f
                ligne(x, y, x + l, y - l * 0.09f, ride, 0.22f, 0.55f)
            }
            c.restore()
            quaiPave()
            touffe(vert(0.7f), 1f, 59f, 3.4f, 6f, 60.5f, 2.6f)
        }

        /** Le bord du quai d'où l'on regarde le Grund, à l'abscisse [x] : il monte vers la droite. */
        private fun quaiProche(x: Float) = 59.6f - (x + 2f) * 0.042f

        /** La matière d'un sol naturel ([solNaturel]). */
        enum class SolNaturel { HERBE, TERRE, MOUSSE, BERGE }

        /**
         * Le premier plan d'un paysage naturel en vraie matière, au lieu du
         * plancher de la scène (propriétaire, 2026-11-14) : une herbe drue,
         * la terre d'un chemin de forêt avec ses feuilles, ses cailloux et
         * ses racines, la mousse sur le grès du Mullerthal, ou la berge de
         * sable et de galets. Posé de [haut] au bas de la fenêtre, penché de
         * [pente] ; tout grossit en approchant. L'herbe et la mousse restent
         * de la végétation : les saisons les colorent et la neige s'y pose.
         */
        fun solNaturel(sorte: SolNaturel, haut: Float = DEVANT, pente: Float = 0f) {
            dansLePlancher = true
            val r = Alea(161 + sorte.ordinal)
            val base = when (sorte) {
                SolNaturel.HERBE -> vert(0.75f)
                // Une terre d'humus brun sombre, pas l'orange du plancher.
                SolNaturel.TERRE -> neutre(duJour(0xFF5E4E3E.toInt()))
                SolNaturel.MOUSSE -> sombre(vert(0.55f), 0.2f)
                SolNaturel.BERGE -> neutre(duJour(0xFFB9AE96.toInt()))
            }
            fun bord(x: Float) = haut + (x + 2f) * pente
            val sol = Path().apply { moveTo(-2f, BAS + 2f); lineTo(-2f, bord(-2f)); lineTo(102f, bord(102f)); lineTo(102f, BAS + 2f); close() }
            if (sorte == SolNaturel.TERRE || sorte == SolNaturel.BERGE) mineral { piece(sol, base, 0.3f) } else piece(sol, base, 0.3f)
            enPerspective(-2f, haut, pente) {
                // Une ligne d'éléments par rang ; [t] de 0 (au fond) à 1 (devant).
                fun rangs(pas0: Float, bloc: (x: Float, y: Float, t: Float) -> Unit) {
                    var y = haut + 0.3f
                    var pas = pas0
                    while (y < BAS + 2f) {
                        val t = ((y - haut) / (BAS - haut)).coerceIn(0f, 1f)
                        var x = -2f + r() * pas * 2f
                        while (x < 104f) { bloc(x, y + r() * pas * 0.6f, t); x += pas * (1.6f + r() * 2.4f) }
                        y += pas
                        pas *= 1.18f
                    }
                }
                when (sorte) {
                    SolNaturel.HERBE -> {
                        val clair = mix(vert(0.75f), 0xFFE8F0A0.toInt(), if (P.nuit) 0.05f else 0.35f)
                        val fonce = sombre(vert(0.75f), 0.3f)
                        rangs(0.55f) { x, y, t ->
                            val l = 0.7f + t * 2.6f
                            val col = if (r() < 0.5f) clair else fonce
                            ligne(x, y, x + (r() - 0.5f) * l * 0.6f, y - l, col, 0.12f + t * 0.22f, 0.9f)
                            ligne(x + 0.3f, y, x + 0.3f + (r() - 0.3f) * l * 0.5f, y - l * 0.8f, col, 0.1f + t * 0.2f, 0.9f)
                            if (r() < 0.04f) rond(x, y - l, 0.2f + t * 0.35f, if (r() < 0.5f) duJour(0xFFF6F2E4.toInt()) else duJour(0xFFF2C230.toInt()))
                        }
                    }
                    SolNaturel.TERRE -> {
                        val caillou = intArrayOf(neutre(duJour(0xFFA39C90.toInt())), neutre(duJour(0xFF8C857A.toInt())))
                        val feuilles = intArrayOf(neutre(duJour(0xFF9A6A3E.toInt())), neutre(duJour(0xFF7E6A42.toInt())), neutre(duJour(0xFF6E4A30.toInt())))
                        val racine = sombre(base, 0.45f)
                        mineral {
                            trait("M-2 ${haut + 2.4f}C20 ${haut + 4f} 34 ${haut + 1.6f} 52 ${haut + 3.6f}", racine, 0.45f, 0.8f)
                            trait("M60 ${BAS - 2f}C70 ${BAS - 5f} 84 ${BAS - 3f} 104 ${BAS - 6f}", racine, 0.7f, 0.8f)
                        }
                        rangs(0.9f) { x, y, t ->
                            val k = r()
                            if (k < 0.35f) mineral { ovalePapier(x, y, 0.3f + t * 0.9f, 0.18f + t * 0.5f, caillou[(r() * 2f).toInt().coerceAtMost(1)], 0.2f) }
                            else if (k < 0.75f) mineral { ovalePapier(x, y, 0.4f + t * 1f, 0.15f + t * 0.35f, feuilles[(r() * 3f).toInt().coerceAtMost(2)], 0.2f) }
                            else mineral { rond(x, y, 0.1f + t * 0.15f, sombre(base, 0.3f), 0.8f) }
                        }
                    }
                    SolNaturel.MOUSSE -> {
                        val gres = neutre(duJour(0xFF8E8676.toInt()))
                        val mousse = vert(0.45f)
                        val claire = mix(mousse, 0xFFD8E070.toInt(), if (P.nuit) 0.05f else 0.35f)
                        rangs(1.6f) { x, y, t ->
                            if (r() < 0.45f) {
                                val rx = 0.8f + t * 2.6f; val ry = 0.45f + t * 1.4f
                                mineral { ovalePapier(x, y, rx, ry, gres, 0.4f) }
                                // Le coussin de mousse sur la pierre.
                                piece(Path().apply { addArc(x - rx * 0.95f, y - ry * 1.05f, x + rx * 0.95f, y + ry * 0.5f, 180f, 180f); close() }, mousse, 0.2f)
                            } else {
                                rond(x, y, 0.35f + t * 0.9f, if (r() < 0.5f) claire else mousse, 0.95f)
                            }
                        }
                    }
                    SolNaturel.BERGE -> {
                        val galets = intArrayOf(duJour(0xFFB9B0A0.toInt()), duJour(0xFF9C9484.toInt()), duJour(0xFFC8BFA8.toInt()), duJour(0xFF857E70.toInt()))
                            .map { neutre(it) }
                        rangs(0.75f) { x, y, t ->
                            if (r() < 0.7f) mineral {
                                ovalePapier(x, y, 0.35f + t * 1.3f, 0.22f + t * 0.75f, galets[(r() * 4f).toInt().coerceAtMost(3)], 0.25f)
                            } else if (r() < 0.25f) {
                                val l = 1f + t * 3f
                                ligne(x, y, x - 0.3f, y - l, vert(0.6f), 0.15f + t * 0.2f)
                                ligne(x + 0.4f, y, x + 0.8f, y - l * 0.8f, vert(0.6f), 0.15f + t * 0.2f)
                            }
                        }
                    }
                }
            }
            dansLePlancher = false
        }

        /** La matière d'un sol de pierre ([solDePierre]). */
        enum class SolDePierre { PAVES, DALLES, GRAVIER }

        /** Le premier plan du Grund : le quai pavé, parallèle à l'Alzette, et sa bordure. */
        private fun quaiPave() = solDePierre(SolDePierre.PAVES, quaiProche(-2f), -0.042f, bordure = true)

        /**
         * Un premier plan en vraie matière, au lieu du plancher de la scène
         * (propriétaire, 2026-11-14) : des pavés de grès gris, de grandes
         * dalles ou une allée de gravier, posés à plat puis penchés de
         * [pente] pour suivre la fuite du lieu, depuis [haut] (à gauche)
         * jusqu'au bas de la fenêtre. Les rangs grossissent en approchant ;
         * [bordure] : une rangée de pierres de taille au bord, contre l'eau
         * ou le gazon. Peint avec [neutre], et compté comme le plancher pour
         * les saisons.
         */
        fun solDePierre(sorte: SolDePierre, haut: Float = DEVANT, pente: Float = 0f, bordure: Boolean = false) {
            dansLePlancher = true
            val joint = neutre(duJour(if (sorte == SolDePierre.GRAVIER) 0xFF9E978A.toInt() else 0xFF5C5A56.toInt()))
            val pierres = intArrayOf(duJour(0xFFA9A59C.toInt()), duJour(0xFFB9B3A6.toInt()), duJour(0xFF9D9991.toInt()), duJour(0xFFC3BCAD.toInt()))
                .map { neutre(it) }
            val y0 = haut
            fun bord(x: Float) = y0 + (x + 2f) * pente
            piece(Path().apply {
                moveTo(-2f, BAS + 2f); lineTo(-2f, bord(-2f)); lineTo(102f, bord(102f)); lineTo(102f, BAS + 2f); close()
            }, joint, 0.3f)
            enPerspective(-2f, y0, pente) {
                val r = Alea(151)
                var y = y0
                if (bordure) {
                    val pierreBord = neutre(duJour(0xFFC9C2B2.toInt()))
                    var bx = -2f
                    while (bx < 112f) {
                        val l = 7f + r() * 3f
                        piece(Path().apply { addRoundRect(bx + 0.15f, y0 + 0.1f, bx + l - 0.15f, y0 + 1.9f, 0.3f, 0.3f, Path.Direction.CW) }, pierreBord, 0.4f)
                        rect(bx + 0.4f, y0 + 0.2f, l - 0.8f, 0.45f, neutre(mix(pierreBord, 0xFFFFFFFF.toInt(), 0.35f)), 0.7f)
                        bx += l
                    }
                    y += 2.1f
                }
                when (sorte) {
                    SolDePierre.PAVES, SolDePierre.DALLES -> {
                        val dalles = sorte == SolDePierre.DALLES
                        var h = if (dalles) 1.6f else 1.1f
                        var k = 0
                        while (y < BAS + 8f) {
                            var x = -2f - (if (k % 2 == 0) 0f else h * (if (dalles) 1.4f else 0.9f))
                            while (x < 112f) {
                                val w = h * (if (dalles) 2.4f + r() * 1.2f else 1.5f + r() * 0.5f)
                                val pierre = pierres[(r() * 4f).toInt().coerceAtMost(3)]
                                val arrondi = if (dalles) 0.08f * h else 0.35f * h
                                val retrait = if (dalles) 0.05f * h else 0.12f * h
                                piece(Path().apply {
                                    addRoundRect(x + retrait, y + retrait, x + w - retrait, y + h - retrait, arrondi, arrondi, Path.Direction.CW)
                                }, pierre, 0.25f)
                                // L'usure : le haut de chaque pierre, poli et plus clair.
                                rect(x + 0.3f * h, y + 0.2f * h, w * 0.5f, 0.14f * h, neutre(mix(pierre, 0xFFFFFFFF.toInt(), 0.3f)), 0.55f)
                                x += w
                            }
                            y += h
                            h *= 1.22f
                            k++
                        }
                    }
                    SolDePierre.GRAVIER -> {
                        // Le gravier : des grains qui grossissent en approchant.
                        var gy = y
                        while (gy < BAS + 4f) {
                            val t = ((gy - y0) / (BAS - y0)).coerceIn(0f, 1.3f)
                            val g = 0.18f + 0.3f * t
                            var gx = -2f + r() * g * 3f
                            while (gx < 112f) {
                                rond(gx, gy + r() * g, g * (0.6f + r() * 0.6f), pierres[(r() * 4f).toInt().coerceAtMost(3)], 0.9f)
                                gx += g * (2.2f + r() * 2f)
                            }
                            gy += g * 1.6f
                        }
                    }
                }
            }
            dansLePlancher = false
        }

        // ---- Musel, le monument de Schengen (photo du propriétaire)

        fun muselPapier() {
            val r = Alea(155)
            lisiere(-2f, 102f, { x -> 29f + 2f * sin(x * 0.08f) }, 1.4f, 2.2f, 156, nappe(mix(P.loin, P.milieu, 0.3f), 0.3f, VERT_CALME))
            // Le village de l'autre rive, en rue qui longe la Moselle en fuyant.
            rue3q(RUE_MUSEL, MAISONS_MUSEL, facades(), r, ecart = 4f)
            plan(1)
            plancher(56f, DEVANT)
            poser(56f)
            val moselle = chemin("M-2 47L-2 32.5L102 32.5L102 47Z")
            piece(moselle, P.eau, 0.4f)
            // La Moselle n'est pas un aplat de bleu : la rive d'en face y pose
            // son ombre, les façades y tremblent en traits couchés, et les rides
            // s'élargissent en approchant.
            c.save()
            c.clipPath(moselle)
            rect(-2f, 32.5f, 104f, 2.4f, sombre(P.eau, 0.38f), 0.5f)
            val murs = facades()
            for (k in 0 until 20) {
                rect(-2f + r() * 104f, 32.8f + r() * 2.6f, 1.4f + r() * 3f, 0.4f + r() * 0.7f,
                    mix(murs[k % murs.size], P.eau, 0.55f), 0.4f)
            }
            val clair = mix(P.eau, 0xFFFFFFFF.toInt(), if (P.nuit) 0.18f else 0.4f)
            for (k in 0 until 30) {
                val u = r()
                rect(-2f + r() * 102f, 34f + u * 12.5f, 2f + r() * (3f + u * 9f), 0.2f + u * 0.35f, clair, 0.3f + u * 0.45f)
            }
            c.restore()
            if (!vivant) peniche(14f, 0f)
            // La pelouse de la rive, rayée par la tonte : un aplat vert la
            // faisait lire comme du feutre de table.
            val pelouse = nappe(P.milieu, 0.7f)
            pieceD("M-2 66L-2 46L102 46L102 66Z", pelouse, 0.5f)
            var py = 46.6f
            var bande = true
            while (py < 58f) {
                val ph = 1.6f + r() * 1.1f
                if (bande) rect(-2f, py, 104f, ph, mix(pelouse, 0xFFFFFFFF.toInt(), if (P.nuit) 0.05f else 0.14f), 0.6f)
                py += ph
                bande = !bande
            }
            c.restore()
            plan(2)
            solDePierre(SolDePierre.DALLES, DEVANT, -0.02f)  // le parvis des stèles
            poser(DEVANT)
            touffe(vert(0.35f), -2f, 8f, 8f, 7f, 3f, 7f, 15f, 6f, 6f, -1f, 16f, 6f)
            // Les trois stèles de Schengen. Sur la photo du propriétaire, chacune
            // est une double tôle d'acier corten : la plaque de devant porte
            // l'étoile *évidée*, et c'est la plaque de derrière, décalée et au
            // soleil, qu'on voit par le trou. L'étoile d'or posée sur la tôle
            // qu'on dessinait avant ratait tout le monument. Le corten est de la
            // matière bâtie : il passe par neutre(), sans quoi raviver() le
            // tirait vers l'orange de signalisation (règle 10) — c'est ce qu'on
            // voyait, trois panneaux de chantier au bord de la Moselle.
            val corten = neutre(duJour(0xFF8C4A2B.toInt()))
            val arriere = neutre(duJour(0xFFD9A978.toInt()))   // la tôle de derrière, au soleil
            val coulure = neutre(duJour(0xFF63341A.toInt()))
            val plaque = neutre(duJour(0xFFD9D3C5.toInt()))
            val bronze = neutre(duJour(0xFF9A7A46.toInt()))
            val fuite = FUITE_STELES
            mineral {
                // Du fond vers l'avant, comme on colle des cartons : la stèle
                // la plus proche recouvre celle qui la précède.
                for (u0 in PAS_STELES.reversed()) {
                    val ud = u0                       // le bord droit, le plus proche
                    val ug = u0 + LARGE_STELE         // le bord gauche, qui fuit
                    val xd = fuite.x(ud); val yd = fuite.y(ud); val ed = fuite.echelle(ud)
                    val xg = fuite.x(ug); val yg = fuite.y(ug); val eg = fuite.echelle(ug)
                    val hd = yd - HAUT_STELE * ed
                    val hg = yg - HAUT_STELE * eg
                    // La tôle de derrière : elle dépasse à droite, monte un peu
                    // plus haut, et c'est elle qu'on voit par l'étoile — d'où sa
                    // couleur plus claire, elle prend le soleil.
                    val dx = 4.2f * ed
                    val dy = -1.8f * ed
                    piece(quad(floatArrayOf(
                        xg + dx * eg / ed, hg + dy, xd + dx, hd + dy, xd + dx, yd - 0.8f, xg + dx * eg / ed, yg - 0.8f
                    )), arriere, 0.6f)
                    // La tranche de la tôle de devant : c'est elle qui dit le
                    // trois quarts, avec la fuite de la rangée.
                    val tr = 2.2f * ed
                    piece(quad(floatArrayOf(
                        xd, hd, xd + tr, hd + 0.7f * ed, xd + tr, yd - 0.4f * ed, xd, yd
                    )), sombre(corten, 0.45f))
                    // La tôle de devant, peinte à plat dans un rectangle de
                    // TOLE_L × HAUT_STELE puis posée sur son quadrilatère : la
                    // rouille, l'étoile et la plaque fuient avec elle.
                    val face = floatArrayOf(xg, hg, xd, hd, xd, yd, xg, yg)
                    surQuad(TOLE_L, HAUT_STELE, face) {
                        // L'étoile est retirée du papier, pas posée dessus.
                        val tole = Path().apply { addRect(0f, 0f, TOLE_L, HAUT_STELE, Path.Direction.CW) }
                        tole.op(etoile(TOLE_L / 2f, ETOILE_Y, 4.8f), Path.Op.DIFFERENCE)
                        piece(tole, corten)
                        // La rouille ne couvre pas d'un aplat : elle coule en
                        // traînées verticales, plus sombres au pied où l'eau
                        // s'accumule, plus claires en haut où la pluie l'a lavée.
                        c.save()
                        c.clipPath(tole)
                        var tx = 0f
                        while (tx < TOLE_L) {
                            val tw = 0.5f + r() * 1.4f
                            val d = r()
                            val teinte = when {
                                d < 0.34f -> sombre(corten, 0.1f + r() * 0.2f)
                                d < 0.68f -> mix(corten, 0xFFFFFFFF.toInt(), 0.04f + r() * 0.1f)
                                else -> mix(corten, coulure, 0.45f)
                            }
                            val haut = r() * HAUT_STELE * 0.55f
                            rect(tx, haut, tw, HAUT_STELE - haut, teinte, 0.4f)
                            tx += tw
                        }
                        rect(0f, HAUT_STELE - 6.5f, TOLE_L, 6.5f, sombre(corten, 0.32f), 0.45f)
                        rect(0f, 0f, TOLE_L, 1.2f, mix(corten, 0xFFFFFFFF.toInt(), 0.14f), 0.4f)
                        c.restore()
                        // La plaque de texte dans son cadre de bronze.
                        feuille(1.4f, 20.5f, TOLE_L - 2.8f, 11f, bronze, 0.4f)
                        rect(2f, 21.2f, TOLE_L - 4f, 9.6f, plaque)
                        for (k in 0 until 5) {
                            ligne(2.7f, 22.5f + k * 1.7f, TOLE_L - 2.7f - r() * 1.8f, 22.5f + k * 1.7f,
                                sombre(plaque, 0.45f), 0.2f)
                        }
                    }
                }
            }
            if (!vivant) for (e in ETOILES_MUSEL) etoileDeSchengen(e[0], e[1], e[2], 0.6f)
            // La haie de buis qui ferme le parvis. C'étaient deux blocs verts à
            // angles droits ; sur la photo c'est une haie basse taillée, au
            // sommet bombé et feuillu, qui passe devant le pied des stèles.
            val buis = nappe(duJour(0xFF436B2A.toInt()), 0.6f)
            for ((x0, x1, haut) in listOf(Triple(-3f, 36.4f, 47.8f), Triple(64f, 103f, 52.4f))) {
                val t = Path().apply { moveTo(x0, DEVANT + 0.2f); lineTo(x0, haut) }
                var hx = x0
                while (hx <= x1) { t.lineTo(hx, haut + 0.8f * sin(hx * 0.55f)); hx += 1.5f }
                t.lineTo(x1, DEVANT + 0.2f); t.close()
                val f = Path()
                var fx = x0
                while (fx <= x1) {
                    f.addCircle(fx, haut + 0.8f * sin(fx * 0.55f) + 0.5f, 0.85f + r() * 0.9f, Path.Direction.CW)
                    fx += 1.3f
                }
                t.op(f, Path.Op.UNION)
                piece(t, buis)
                // Le dessus taillé prend le jour, le flanc reste dans l'ombre :
                // sans ces deux valeurs la haie reste un aplat découpé.
                c.save()
                c.clipPath(t)
                val dessus = mix(buis, 0xFFFFFFFF.toInt(), if (P.nuit) 0.05f else 0.17f)
                var bx = x0
                while (bx <= x1) {
                    val by = haut + 0.8f * sin(bx * 0.55f)
                    rect(bx, by, 1.6f, 1.6f + r() * 1.4f, dessus, 0.6f)
                    rond(bx + r() * 1.4f, by + 2.5f + r() * 7f, 0.5f + r() * 0.6f, sombre(buis, 0.3f), 0.45f)
                    bx += 1.3f
                }
                c.restore()
            }
            c.restore()
        }

        /**
         * L'étoile évidée d'une stèle de Schengen, centrée en ([cx], [cy]) et
         * de [rayon] unités, éclairée par-derrière une fois la nuit tombée :
         * [eclat] de 0 à 1 la
         * fait respirer, comme une étoile qui scintille. Rien de jour — le
         * trou se lit alors tout seul, sur la tôle de derrière au soleil.
         */
        fun etoileDeSchengen(cx: Float, cy: Float, rayon: Float, eclat: Float) {
            if (!actif || !P.allume) return
            // Le halo en vrai dégradé : un rond plat posait sur la tôle un
            // disque à bord net, qu'on lisait comme une pièce de papier de plus.
            val rr = (1.7f + 0.65f * eclat) * rayon
            p.style = Paint.Style.FILL
            p.color = 0xFF000000.toInt()
            p.alpha = ((0.15f + 0.22f * eclat) * 255f).roundToInt()
            p.shader = RadialGradient(
                cx, cy, rr,
                intArrayOf((0xFF shl 24) or (P.fenetre and 0xFFFFFF), P.fenetre and 0x00FFFFFF),
                null, Shader.TileMode.CLAMP
            )
            c.drawCircle(cx, cy, rr, p)
            p.shader = null
            forme(etoile(cx, cy, rayon * 0.95f), mix(P.fenetre, 0xFFFFFFFF.toInt(), 0.3f * eclat), 0.32f + 0.5f * eclat)
        }

        /** La péniche sur la Moselle, sa proue en [x], [tangage] en unités. */
        fun peniche(x: Float, tangage: Float) {
            pieceD("M$x ${42.6f + tangage}L${x + 22f} ${42.6f - tangage}L${x + 20.6f} ${44.6f - tangage}L${x + 1.4f} ${44.6f + tangage}Z",
                duJour(0xFF2F4A3A.toInt()), 0.3f)
            feuille(x + 15.5f, 40.4f - tangage * 0.5f, 4f, 2.2f, duJour(0xFFEDE8DC.toInt()), 0.3f)
            if (P.allume) rond(x + 17f, 41.4f - tangage * 0.5f, 0.45f, P.fenetre)
        }

        // ---- Schueberfouer, la grande roue et le carrousel (photo du propriétaire)

        /**
         * La fête vue d'en bas, dans l'angle de la photo : on est debout dans
         * l'allée, la roue penche au-dessus de l'épaule gauche et l'on regarde
         * **sous** la coupole du carrousel, dont le bord proche descend vers
         * nous. La contre-plongée ne laisse presque pas de sol et pousse tout
         * ce qui compte dans la bande libre du haut, là où la plaque du mot
         * ne va pas.
         */
        fun fouerPapier() {
            // Au loin, la masse sombre des autres métiers : très basse, la
            // contre-plongée ne laisse presque pas d'horizon.
            lisiere(-2f, 102f, { x -> 52.5f + 1.1f * sin(x * 0.13f) }, 1.2f, 2f, 165,
                mix(nappe(P.loin, 0.5f, VERT_CALME), P.cielBas, if (P.nuit) 0.1f else 0.3f))
            plan(1)
            metiers()
            pylonesDeRoue()
            if (!vivant) grandeRoue(0f)
            plan(2)
            solDePierre(SolDePierre.PAVES, DEVANT, -0.02f)  // le Glacis
            poser(DEVANT)
            carrousel()
            if (!vivant) chevaux(0f)
            baraque()
            foule()
            guirlande()
            c.restore()
        }

        /**
         * Les deux chevalets en treillis de la grande roue, vert vif comme sur
         * la photo : vus d'en bas ils s'écartent largement au pied et se
         * rejoignent sous le moyeu.
         */
        private fun pylonesDeRoue() {
            val cx = ROUE[0]; val cy = ROUE[1]
            val vert = neutre(duJour(0xFF2F9C5A.toInt()))
            mineral {
                for (pied in floatArrayOf(-6f, 26f)) chevalet(pied + 3f, 55.5f, cx + 1.7f, cy + 2.4f, sombre(vert, 0.45f), 0.85f, false)
                for (pied in floatArrayOf(-6f, 26f)) chevalet(pied, 57.5f, cx, cy + 1.4f, vert, 1.1f, true)
                rond(cx, cy, 2.4f, neutre(duJour(0xFF9AA3AC.toInt())))
                rond(cx, cy, 1.1f, neutre(duJour(0xFF6E767E.toInt())))
            }
        }

        /**
         * Les autres métiers, au fond de l'allée : une file de toiles rayées
         * dont on ne voit que les toits, la foule en cache le pied.
         */
        private fun metiers() {
            val r = Alea(167)
            val toiles = intArrayOf(eclaire(duJour(0xFFC8402F.toInt()), 0.26f), eclaire(duJour(0xFF3E6FA8.toInt()), 0.26f),
                eclaire(duJour(0xFF2F9C5A.toInt()), 0.26f), eclaire(duJour(0xFFE2B04A.toInt()), 0.26f))
            mineral {
                var x = -8f
                var k = 0
                while (x < 106f) {
                    val l = 9f + r() * 7f
                    val y = 47.5f + r() * 2f
                    val col = toiles[k % toiles.size]
                    pieceD("M$x ${y + 5}L$x ${y + 1}L${x + l / 2f} ${y - 2.4f}L${x + l} ${y + 1}L${x + l} ${y + 5}Z",
                        neutre(mix(col, 0xFFF2EADA.toInt(), 0.55f)), 0.5f)
                    // Le lambrequin rayé sous l'égout du toit, à plat.
                    var sx = x
                    var j = 0
                    while (sx < x + l - 0.2f) {
                        val w = min(1.5f, x + l - sx)
                        rect(sx, y + 1f, w, 1.6f, if (j % 2 == 0) col else mix(col, 0xFFFFF4E2.toInt(), 0.7f), 0.95f)
                        sx += w; j++
                    }
                    rect(x + l * 0.2f, y + 3f, l * 0.6f, 1.6f,
                        if (P.allume) mix(P.fenetre, col, 0.3f) else mix(col, 0xFFFFF4E2.toInt(), 0.45f), 0.9f)
                    if (P.allume) {
                        var bx = x + 0.7f
                        while (bx < x + l) { rond(bx, y + 2.8f, 0.34f, P.fenetre); bx += 2.2f }
                    }
                    x += l + 0.6f + r() * 2f
                    k++
                }
            }
        }

        /** Un chevalet de treillis, du pied ([x0], [y0]) au moyeu ([x1], [y1]). */
        private fun chevalet(x0: Float, y0: Float, x1: Float, y1: Float, col: Int, large: Float, ampoules: Boolean) {
            val e0 = 3.2f; val e1 = 0.8f   // l'écartement des deux membrures, au pied puis en haut
            ligne(x0 - e0, y0, x1 - e1, y1, col, large)
            ligne(x0 + e0, y0, x1 + e1, y1, col, large)
            var t = 0.06f; var k = 0
            while (t < 0.97f) {
                val t2 = min(t + 0.105f, 1f)
                val ax = x0 - e0 + (x1 - e1 - x0 + e0) * t;   val ay = y0 + (y1 - y0) * t
                val bx = x0 + e0 + (x1 + e1 - x0 - e0) * t
                val ax2 = x0 - e0 + (x1 - e1 - x0 + e0) * t2; val ay2 = y0 + (y1 - y0) * t2
                val bx2 = x0 + e0 + (x1 + e1 - x0 - e0) * t2
                ligne(ax, ay, bx, ay, col, large * 0.5f)
                if (k % 2 == 0) ligne(ax, ay, bx2, ay2, col, large * 0.42f)
                else ligne(bx, ay, ax2, ay2, col, large * 0.42f)
                if (ampoules && P.allume) { rond(ax, ay, 0.38f, P.fenetre); rond(bx, ay, 0.38f, P.fenetre) }
                t = t2; k++
            }
        }

        /** La grande roue, tournée de [angle] radians ; la nuit, ses ampoules courent au [temps]. */
        fun grandeRoue(angle: Float, temps: Float = 0f) {
            val cx = ROUE[0]; val cy = ROUE[1]; val rr = ROUE[2]
            // Vue d'en bas et de biais comme sur la photo : elle se voit en
            // ellipse, et son axe penche en arrière.
            c.save()
            c.rotate(-9f, cx, cy)
            c.scale(0.78f, 1f, cx, cy)
            try { mineral { roue(cx, cy, rr, angle, temps) } } finally { c.restore() }
        }

        private fun roue(cx: Float, cy: Float, rr: Float, angle: Float, temps: Float) {
            val acier = if (P.allume) sombre(neutre(duJour(0xFF8B96A4.toInt())), 0.4f) else neutre(duJour(0xFF8B96A4.toInt()))
            // La nuit, la roue est la lumière de la fête : le halo d'abord.
            if (P.allume && actif) {
                p.style = Paint.Style.FILL
                p.shader = RadialGradient(cx, cy, rr * 1.35f,
                    intArrayOf((0x7A shl 24) or (P.fenetre and 0xFFFFFF), P.fenetre and 0x00FFFFFF),
                    null, Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy, rr * 1.35f, p)
                p.shader = null
            }
            val moyeu = rr * 0.4f
            // Les haubans, du moyeu à la jante.
            for (k in 0 until 24) {
                val a = angle + k / 24f * 2f * PI.toFloat()
                ligne(cx + moyeu * cos(a), cy + moyeu * sin(a), cx + rr * cos(a), cy + rr * sin(a), acier, 0.26f)
            }
            // La jante : deux cercles reliés par des traverses.
            anneau(cx, cy, rr, acier, 1f)
            anneau(cx, cy, rr - 2.2f, acier, 0.55f)
            for (k in 0 until 12) {
                val a = angle + (k + 0.5f) / 12f * 2f * PI.toFloat()
                ligne(cx + (rr - 2f) * cos(a), cy + (rr - 2f) * sin(a), cx + rr * cos(a), cy + rr * sin(a), acier, 0.38f)
            }
            // La rosace du moyeu : les anneaux de couleur de la photo, découpés en quartiers.
            // La rosace est faite de panneaux lumineux : la nuit elle garde
            // ses couleurs pleines, elle ne vire pas à l'ambre comme une
            // surface simplement éclairée.
            for ((i, f) in floatArrayOf(1f, 0.76f, 0.54f, 0.32f).withIndex())
                rond(cx, cy, moyeu * f,
                    if (P.allume) mix(ROSACE[i], 0xFFFFFFFF.toInt(), 0.14f) else duJour(ROSACE[i]))
            val joint = if (P.allume) mix(ROSACE[0], 0xFF000000.toInt(), 0.45f) else sombre(duJour(ROSACE[0]), 0.4f)
            for (k in 0 until 16) {
                val a = angle + k / 16f * 2f * PI.toFloat()
                ligne(cx + moyeu * 0.3f * cos(a), cy + moyeu * 0.3f * sin(a),
                    cx + moyeu * cos(a), cy + moyeu * sin(a), joint, 0.3f, 0.55f)
            }
            rond(cx, cy, moyeu * 0.17f, acier)
            // Les nacelles, suspendues sous la jante.
            for (k in 0 until 12) {
                val a = angle + k / 12f * 2f * PI.toFloat()
                val x = cx + (rr - 0.9f) * cos(a)
                val y = cy + (rr - 0.9f) * sin(a)
                val col = if (P.allume) mix(GONDOLES[k % GONDOLES.size], 0xFFFFFFFF.toInt(), 0.1f)
                    else duJour(GONDOLES[k % GONDOLES.size])
                // L'étrier, puis la cabine : sans l'étrier les nacelles ont
                // l'air de flotter à côté de la jante.
                ligne(x - 1.5f, y + 1.9f, x, y, acier, 0.4f)
                ligne(x + 1.5f, y + 1.9f, x, y, acier, 0.4f)
                rect(x - 1.8f, y + 1.7f, 3.6f, 2.8f, col)
                rect(x - 1.8f, y + 1.7f, 3.6f, 0.6f, mix(col, 0xFFFFFFFF.toInt(), 0.4f), 0.9f)
                rect(x - 1.3f, y + 2.6f, 2.6f, 1.3f,
                    if (P.allume) P.fenetre else mix(col, 0xFFFFFFFF.toInt(), 0.6f), 0.9f)
            }
            if (P.allume) {
                val pas = (temps * 4f).toInt()
                for (k in 0 until 44) {
                    if ((k + pas) % 3 == 0) continue
                    val a = angle + k / 44f * 2f * PI.toFloat()
                    val bx = cx + (rr - 0.85f) * cos(a); val by = cy + (rr - 0.85f) * sin(a)
                    val col = if (k % 2 == 0) P.fenetre else 0xFFFF8FB8.toInt()
                    if (k % 2 == 0) rond(bx, by, 1.3f, col, 0.22f)
                    rond(bx, by, 0.52f, col)
                }
            }
        }

        /**
         * L'ordonnée du bord **proche** d'un cercle du carrousel vu d'en bas :
         * le bord qui descend vers nous, et sur lequel se posent la corniche,
         * les barres de cuivre et les chevaux.
         */
        private fun bordProche(x: Float, cy: Float, rx: Float, ry: Float): Float {
            val u = ((x - CARROUSEL) / rx).coerceIn(-1f, 1f)
            return cy + ry * sqrt(1f - u * u)
        }

        /**
         * Le chemin du bord proche, de [x0] à [x1]. Sans [vers] il commence
         * un tracé ; avec, il poursuit celui qu'on lui donne.
         */
        private fun arcProche(x0: Float, x1: Float, cy: Float, rx: Float, ry: Float, vers: Path? = null): Path {
            val t = vers ?: Path()
            if (vers == null) t.moveTo(x0, bordProche(x0, cy, rx, ry))
            var x = x0 + 1.5f
            while (x < x1) { t.lineTo(x, bordProche(x, cy, rx, ry)); x += 1.5f }
            t.lineTo(x1, bordProche(x1, cy, rx, ry))
            return t
        }

        /**
         * Le carrousel à deux étages, vu d'en dessous comme sur la photo : le
         * bord de la coupole descend vers nous, on voit son plafond peint et
         * ses miroirs, et les chevaux passent au-dessus du regard. Son mât est
         * au bord droit de la fenêtre, là où la plaque du mot ne va pas.
         */
        private fun carrousel() {
            val cx = CARROUSEL
            val ivoire = eclaire(neutre(duJour(0xFFF6EFDF.toInt())), 0.45f)
            val plafond = eclaire(neutre(duJour(0xFFE9DBBE.toInt())), 0.34f)
            val or = eclaire(duJour(0xFFE2B04A.toInt()), 0.4f)
            val rouge = eclaire(duJour(0xFFC8402F.toInt()), 0.2f)
            val laiton = eclaire(duJour(0xFFD3A64E.toInt()), 0.35f)
            val ampoule = if (P.allume) P.fenetre else 0xFFFFF0CF.toInt()
            val x0 = 70f
            val cyC = 26f; val rxC = 33f; val ryC = 8.4f
            mineral {
                // Vue d'en dessous, la coupole ne montre qu'un croissant de
                // son dessus, au-delà du bord lointain du plafond. Ce qui fait
                // la masse en l'air, c'est le second étage, au-dessus.
                // La même parabole sert au contour de la calotte, au bord
                // lointain du plafond et à la guirlande : un seul tracé, donc
                // rien qui flotte à côté de rien.
                val calotte = chemin("M${cx - rxC} ${cyC}Q$cx 0.8 ${cx + rxC} ${cyC}Q$cx 10 ${cx - rxC} ${cyC}Z")
                piece(calotte, ivoire, 0.45f)
                c.save(); c.clipPath(calotte)
                for (k in 0 until 13) {
                    val a = PI.toFloat() * (0.5f + k * 0.0435f)
                    val a2 = PI.toFloat() * (0.5f + (k + 1) * 0.0435f)
                    if (k % 2 == 0) triangle(cx, 12.6f, cx + 38f * cos(a), cyC + 2f,
                        cx + 38f * cos(a2), cyC + 2f, mix(ivoire, rouge, 0.3f), 0.95f)
                    ligne(cx, 12.6f, cx + 38f * cos(a), cyC + 2f, or, 0.4f, 0.8f)
                }
                c.restore()
                // La guirlande du bord de coupole : c'est elle qui dit que
                // cette masse en l'air est un toit allumé, et non une colline.
                for (k in 1 until 11) {
                    val t = k / 22f
                    val u = 1f - t
                    rond(u * u * (cx - rxC) + 2f * u * t * cx + t * t * (cx + rxC),
                        u * u * cyC + 2f * u * t * 0.8f + t * t * cyC, 0.44f, ampoule)
                }
                // Le second étage : son tambour peint, ses panneaux et ses
                // deux couronnes d'ampoules, comme sur la photo.
                // Le tambour s'assoit dans la coupole : ses coins bas rejoignent
                // la parabole de la calotte, sinon il a l'air posé dans le ciel.
                val rT = 13.5f
                pieceD("M${cx - rT} 8.6C${cx - rT} 4.2 ${cx + rT} 4.2 ${cx + rT} 8.6" +
                    "L${cx + rT} 15.2C${cx + rT} 20 ${cx - rT} 20 ${cx - rT} 15.2Z", ivoire, 0.45f)
                for (k in 0 until 6) {
                    val bx = cx - 12.3f + k * 4.1f
                    val d = (bx + 1.6f - cx) / rT
                    rect(bx, 7.4f - 2.6f * (1f - d * d), 3.2f, 6.4f + 5.4f * (1f - d * d),
                        if (k % 2 == 0) rouge else mix(ivoire, or, 0.5f), 0.95f)
                }
                for (k in 0 until 12) {
                    val bx = cx - 12.4f + k * 2.25f
                    val d = (bx - cx) / rT
                    rond(bx, 15.5f + 3.6f * (1f - d * d), 0.46f, ampoule)
                    rond(bx, 8.3f - 3.3f * (1f - d * d), 0.4f, ampoule, 0.9f)
                }
                // Le petit dôme qui coiffe le tout, et son épi.
                pieceD("M${cx - 11} 4.4C${cx - 8.4f} -1.4 ${cx + 8.4f} -1.4 ${cx + 11} 4.4" +
                    "C${cx + 6} 2.4 ${cx - 6} 2.4 ${cx - 11} 4.4Z", mix(ivoire, rouge, 0.35f), 0.3f)

                // Le plafond : on le voit entier, il remplit le disque proche.
                val disque = Path()
                disque.moveTo(cx - rxC, cyC)
                arcProche(cx - rxC, cx + rxC, cyC, rxC, ryC, disque)
                disque.quadTo(cx, 10f, cx - rxC, cyC)
                disque.close()
                piece(disque, plafond, 0.3f)
                c.save(); c.clipPath(disque)
                // Les panneaux peints entre les nervures dorées.
                for (k in 0 until 13) {
                    val a = PI.toFloat() * (0.5f + k * 0.0435f)
                    val a2 = PI.toFloat() * (0.5f + (k + 1) * 0.0435f)
                    if (k % 2 == 0) triangle(cx, cyC - 2.4f, cx + 36f * cos(a), cyC + 10f * sin(a),
                        cx + 36f * cos(a2), cyC + 10f * sin(a2), mix(plafond, rouge, 0.3f), 0.9f)
                    ligne(cx, cyC - 2.4f, cx + 36f * cos(a), cyC + 10f * sin(a), or, 0.45f, 0.85f)
                }
                // L'anneau de miroirs autour du mât, et le tambour central.
                for (k in 0 until 6) {
                    val a = PI.toFloat() * (0.52f + k * 0.08f)
                    val mx = cx + 13f * cos(a); val my = cyC - 0.8f + 3.6f * sin(a)
                    rond(mx, my, 0.58f, mix(ivoire, 0xFFFFFFFF.toInt(), 0.5f))
                    if (P.allume) rond(mx, my, 0.34f, P.fenetre, 0.85f)
                }
                pieceD("M${cx - 6} ${cyC - 4.6f}C${cx - 6} ${cyC - 7} ${cx + 6} ${cyC - 7} ${cx + 6} ${cyC - 4.6f}" +
                    "L${cx + 6} ${cyC - 1.6f}C${cx + 6} ${cyC + 0.8f} ${cx - 6} ${cyC + 0.8f} ${cx - 6} ${cyC - 1.6f}Z",
                    mix(plafond, rouge, 0.45f), 0.3f)
                c.restore()

                // La corniche du bord proche, son liseré et ses ampoules.
                val corniche = arcProche(x0 - 1f, cx + 34f, cyC, rxC, ryC)
                longDe(corniche, or, 2.1f)
                longDe(corniche, mix(or, 0xFFFFFFFF.toInt(), 0.5f), 0.45f)
                // Les festons du lambrequin, accrochés sous la corniche.
                var bx = x0
                while (bx < cx + 3f) {
                    val by = bordProche(bx, cyC, rxC, ryC) + 1.1f
                    val by2 = bordProche(bx + 3.6f, cyC, rxC, ryC) + 1.1f
                    val t = Path()
                    t.moveTo(bx, by); t.quadTo(bx + 1.8f, by + 4f, bx + 3.6f, by2)
                    t.lineTo(bx + 3.6f, by2 - 1.3f); t.quadTo(bx + 1.8f, by + 1.6f, bx, by - 1.3f); t.close()
                    forme(t, rouge, 0.95f)
                    rond(bx + 1.8f, (by + by2) / 2f + 3.1f, 0.46f, ampoule)
                    bx += 3.6f
                }
                bx = x0 + 0.5f
                while (bx < cx + 3f) { rond(bx, bordProche(bx, cyC, rxC, ryC) - 0.2f, 0.5f, ampoule); bx += 2.6f }

                // Le mât central, en miroir.
                feuille(cx - 1.8f, cyC, 3.6f, 26f, mix(ivoire, 0xFFFFFFFF.toInt(), 0.25f), 0.4f)
                ligne(cx - 0.6f, cyC + 1f, cx - 0.6f, cyC + 25f, mix(ivoire, 0xFFFFFFFF.toInt(), 0.8f), 0.5f, 0.7f)

                // Le plancher tournant, vu lui aussi par en dessous : on n'en
                // voit que la ceinture peinte et sa guirlande.
                val ceinture = arcProche(x0 + 5f, cx + 31f, 47f, 30f, 6.6f)
                for (k in 14 downTo 0) {
                    val xx = x0 + 5f + (cx + 31f - x0 - 5f) * k / 14f
                    ceinture.lineTo(xx, bordProche(xx, 47f, 30f, 6.6f) + 3.8f)
                }
                ceinture.close()
                piece(ceinture, rouge, 0.5f)
                bx = x0 + 6f
                while (bx < cx + 3f) {
                    val by = bordProche(bx, 47f, 30f, 6.6f)
                    rect(bx, by + 1.1f, 2.1f, 2.1f, mix(rouge, or, 0.6f), 0.85f)
                    rond(bx + 1.05f, by + 0.4f, 0.42f, ampoule)
                    bx += 3.4f
                }

                // Les barres de cuivre : elles s'écartent vers le bas, comme
                // tout ce qu'on regarde d'en bas.
                for (hx in CHEVAUX) {
                    val h = bordProche(hx, cyC, rxC, ryC) + 1.4f
                    val b = bordProche(hx, 47f, 30f, 6.6f) + 0.8f
                    val bx2 = cx + (hx - cx) * 1.07f
                    ligne(hx, h, bx2, b, laiton, 0.6f)
                    ligne(hx - 0.2f, h, bx2 - 0.2f, b, mix(laiton, 0xFFFFFFFF.toInt(), 0.6f), 0.2f, 0.8f)
                }
            }
        }

        /** Un trait le long d'un [chemin] déjà tracé. */
        private fun longDe(chemin: Path, col: Int, largeur: Float) {
            if (!actif) return
            p.style = Paint.Style.STROKE; p.strokeWidth = largeur; teinte(col, 1f)
            c.drawPath(chemin, p)
            p.style = Paint.Style.FILL
        }

        /** Les chevaux de bois du carrousel, qui montent et descendent au [temps]. */
        fun chevaux(temps: Float) {
            val robe = eclaire(mix(neutre(duJour(0xFFF7F1E4.toInt())), 0xFFFFFFFF.toInt(), 0.4f), 0.4f)
            val creux = eclaire(neutre(duJour(0xFFBFB4A0.toInt())), 0.3f)
            val selle = eclaire(duJour(0xFFC8402F.toInt()), 0.2f)
            val or = eclaire(duJour(0xFFE2B04A.toInt()), 0.4f)
            val laiton = eclaire(duJour(0xFFD3A64E.toInt()), 0.35f)
            mineral {
                for ((k, x) in CHEVAUX.withIndex()) {
                    val h = bordProche(x, 26f, 33f, 8.4f) + 8f
                    val b = bordProche(x, 47f, 30f, 6.6f) - 5.5f
                    val y = (h + b) / 2f + sin(temps * 2f + k * 2.1f) * (b - h) * 0.3f
                    // La barre traverse le cheval : on la revoit au-dessus du garrot.
                    ligne(x + 0.3f, y - 7.4f, x + 0.6f, y + 4.4f, laiton, 0.6f)
                    // Les jambes : des membres épais, pas des fils, sinon le
                    // cheval se lit comme un oiseau.
                    for (j in PATTES) {
                        ligne(x + j[0], y + j[1], x + j[2], y + j[3], creux, 1.5f)
                        ligne(x + j[2], y + j[3], x + j[4], y + j[5], creux, 1.15f)
                        rond(x + j[4], y + j[5], 0.6f, sombre(creux, 0.25f))
                    }
                    // Le corps trapu, l'encolure et la tête, d'un seul tenant.
                    pieceD("M${x - 4.6f} ${y + 0.4f}C${x - 5f} ${y - 1.8f} ${x - 2.6f} ${y - 3.2f} ${x - 0.2f} ${y - 3.1f}" +
                        "C${x + 1.2f} ${y - 3.1f} ${x + 1.9f} ${y - 3.8f} ${x + 2.6f} ${y - 4.6f}" +
                        "C${x + 3.2f} ${y - 5.2f} ${x + 4.2f} ${y - 5.6f} ${x + 5.2f} ${y - 5.3f}" +
                        "L${x + 6.9f} ${y - 4.2f}L${x + 6.5f} ${y - 2.8f}L${x + 4.8f} ${y - 2.7f}" +
                        "C${x + 4.2f} ${y - 1.8f} ${x + 3.9f} ${y - 0.9f} ${x + 3.2f} ${y - 0.2f}" +
                        "C${x + 1.6f} ${y + 2f} ${x - 2.8f} ${y + 2.2f} ${x - 4.6f} ${y + 0.4f}Z", robe, 0.5f)
                    // La crinière, la queue, le harnais et l'œil.
                    forme("M${x + 2.6f} ${y - 4.6f}C${x + 3.4f} ${y - 5.4f} ${x + 4.4f} ${y - 5.8f} ${x + 5.2f} ${y - 5.4f}" +
                        "L${x + 4.6f} ${y - 4.2f}C${x + 3.8f} ${y - 4.4f} ${x + 3.2f} ${y - 3.8f} ${x + 2.4f} ${y - 2.8f}" +
                        "L${x + 1.4f} ${y - 2.3f}C${x + 1.9f} ${y - 3.3f} ${x + 2.2f} ${y - 4f} ${x + 2.6f} ${y - 4.6f}Z", or)
                    forme("M${x - 4.5f} ${y - 1.2f}C${x - 6.2f} ${y - 1f} ${x - 7f} ${y + 0.8f} ${x - 6.4f} ${y + 2.8f}" +
                        "L${x - 5.2f} ${y + 2.4f}C${x - 5.6f} ${y + 0.8f} ${x - 5.2f} ${y - 0.2f} ${x - 4.1f} ${y - 0.4f}Z", or)
                    rond(x + 5.4f, y - 4.2f, 0.3f, sombre(creux, 0.45f))
                    ligne(x + 5.9f, y - 3.9f, x + 6.7f, y - 3.6f, or, 0.3f)
                    // La selle et sa dorure.
                    pieceD("M${x - 2.6f} ${y - 2.6f}L${x + 0.7f} ${y - 2.9f}L${x + 1.2f} ${y - 0.4f}L${x - 2.8f} ${y - 0.1f}Z", selle, 0.4f)
                    ligne(x - 2.7f, y - 0.5f, x + 1.15f, y - 0.8f, or, 0.35f)
                    if (P.allume) rond(x - 0.8f, y - 1.6f, 0.32f, P.fenetre)
                }
            }
        }

        /**
         * La baraque du premier plan, devant le pied de la roue, comme celle
         * du coin gauche de la photo : sa marquise à rayures, son comptoir et
         * son enseigne allumée.
         */
        private fun baraque() {
            val creme = eclaire(neutre(duJour(0xFFEFE8DA.toInt())), 0.4f)
            val rouge = eclaire(duJour(0xFFC23B3B.toInt()), 0.22f)
            val or = eclaire(duJour(0xFFE2B04A.toInt()), 0.4f)
            val bleu = eclaire(neutre(duJour(0xFF3E6FA8.toInt())), 0.2f)
            val ampoule = if (P.allume) P.fenetre else mix(0xFFFFF4D8.toInt(), creme, 0.4f)
            mineral {
                feuille(-7f, 41.5f, 22f, 17f, creme)
                rect(-7f, 49f, 22f, 1.1f, bleu, 0.9f)
                rect(-7f, 52.5f, 22f, 5.5f, bleu, 0.85f)
                // L'enseigne, vue d'en bas : son bandeau penche vers nous.
                pieceD("M-8 41.8L15.6 40L15.6 44.4L-8 45.6Z", rouge)
                var sx = -7f
                while (sx < 15f) { rond(sx, 42.6f - (sx + 7f) * 0.078f, 0.42f, ampoule); sx += 2.4f }
                // La marquise à rayures, par en dessous.
                var mx = -8f
                var k = 0
                while (mx < 16f) {
                    val l = min(2.6f, 16f - mx)
                    rect(mx, 45.4f - (mx + 8f) * 0.07f, l, 2.4f, if (k % 2 == 0) rouge else creme, 0.95f)
                    mx += l; k++
                }
                // Le guichet et son comptoir.
                rect(-2f, 48.6f, 11f, 4.2f, if (P.allume) mix(P.fenetre, or, 0.35f) else neutre(duJour(0xFFCFC4AE.toInt())))
                rect(-2f, 52.2f, 11f, 1.2f, or, 0.9f)
                ligne(3.4f, 48.6f, 3.4f, 52.2f, creme, 0.4f)
            }
        }

        /**
         * La foule, en silhouettes découpées, comme au bas de la photo : une
         * ligne irrégulière, jamais une haie, et assez basse pour laisser
         * voir le pied des métiers.
         */
        private fun foule() {
            val encre = neutre(duJour(if (P.nuit) 0xFF191520.toInt() else 0xFF36291E.toInt()))
            val r = Alea(166)
            mineral {
                var x = -7f
                while (x < 106f) {
                    val h = 3.4f + r() * 3.4f
                    val y = 58.5f - h
                    val l = 1.9f + r() * 1.1f
                    val t = Path()
                    t.moveTo(x, 58.5f)
                    t.lineTo(x, y + 1.9f)
                    t.quadTo(x + l * 0.5f, y + 0.5f, x + l, y + 1.9f)
                    t.lineTo(x + l, 58.5f)
                    t.close()
                    forme(t, encre, 1f)
                    rond(x + l / 2f, y + 0.5f, l * 0.37f, encre)
                    // Elles se chevauchent par endroits, et s'espacent ailleurs.
                    x += if (r() < 0.55f) l * 0.72f else l + 0.5f + r() * 3.4f
                }
            }
        }

        /** Les guirlandes d'ampoules tendues au-dessus de l'allée. */
        private fun guirlande() {
            val fil = if (P.nuit) mix(P.pres, 0xFF000000.toInt(), 0.5f) else mix(P.pres, 0xFF2A2118.toInt(), 0.45f)
            val ampoule = if (P.allume) P.fenetre else 0xFFFFF0CF.toInt()
            for (g in GUIRLANDES) {
                val x0 = g[0]; val y0 = g[1]; val xm = g[2]; val ym = g[3]; val x1 = g[4]; val y1 = g[5]
                trait("M$x0 ${y0}Q$xm $ym $x1 $y1", fil, 0.28f, 0.8f)
                for (k in 1 until 13) {
                    val t = k / 13f
                    val u = 1f - t
                    val bx = u * u * x0 + 2f * u * t * xm + t * t * x1
                    val by = u * u * y0 + 2f * u * t * ym + t * t * y1
                    ligne(bx, by, bx, by + 0.7f, fil, 0.2f, 0.8f)
                    rond(bx, by + 1.1f, 0.48f, if (k % 2 == 0) ampoule else mix(ampoule, 0xFFFF9EC0.toInt(), 0.5f),
                        if (P.allume) 1f else 0.85f)
                }
            }
        }

        // ---- Nei Bréck, le pont Adolphe (photo du propriétaire)

        fun neiBreck() {
            val r = Alea(175)
            val creme = duJour(0xFFE6DAC0.toInt())
            val toit = ardoise()
            val cuivre = duJour(0xFF5FA88A.toInt())
            // La Spuerkeess de trois quarts, qui fuit vers la droite comme le
            // pont : sa tour au dôme de cuivre, puis le grand bâtiment et son
            // pavillon central, plus haut, dont le pignon dépasse.
            val murs = intArrayOf(creme)
            rue3q(FUITE_SPUERKEESS, SPUERKEESS, murs, r)
            rue3q(FUITE_TOUR_SPUERKEESS, TOUR_SPUERKEESS, murs, r)
            mineral {
                fun dome(f: Fuite, u0: Float, u1: Float, haut: Float, rayon: Float) {
                    val um = (u0 + u1) / 2f
                    val e = f.echelle(um)
                    val x = f.x(um); val y = f.y(um) - haut * e
                    val l = (f.x(u1) - f.x(u0)) / 2f + 0.4f
                    pieceD("M${x - l} ${y + 0.3f}C${x - l} ${y - rayon * e} ${x + l} ${y - rayon * e} ${x + l} ${y + 0.3f}Z", cuivre)
                    feuille(x - 0.4f * e, y - rayon * e - 2.2f * e, 0.8f * e, 2.6f * e, cuivre, 0.3f)
                }
                dome(FUITE_TOUR_SPUERKEESS, 0f, TOUR_SPUERKEESS[0][0], TOUR_SPUERKEESS[0][1], 5.6f)
                val u0 = SPUERKEESS[0][0]
                dome(FUITE_SPUERKEESS, u0, u0 + SPUERKEESS[1][0], SPUERKEESS[1][1], 5f)
            }
            lisiere(56f, 102f, { x -> 27f + sin(x * 0.3f) }, 1.4f, 2f, 176, nappe(mix(P.loin, P.milieu, 0.3f), 0.3f, VERT_CALME))
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
            solDePierre(SolDePierre.GRAVIER, DEVANT)  // l'allée du parc de la Pétrusse
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
            // Les immeubles de bureaux du fond, de trois quarts.
            val vitre = duJour(0xFF7F95AA.toInt())
            for ((dim, col) in listOf(floatArrayOf(68f, 8f, 22f) to duJour(0xFFB9C4CF.toInt()), floatArrayOf(84f, 10f, 26f) to duJour(0xFFC9B9A9.toInt()))) {
                val (x, w, h) = Triple(dim[0], dim[1], dim[2])
                boite3q(x, 52f, w, h, 6f, col) {
                    var y = 2f
                    while (y < h - 1f) { rect(0.8f, y, w - 1.6f, 0.9f, vitre, 0.7f); y += 2.6f }
                }
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
                // Le haut fourneau : la charpente, son flanc dans l'ombre qui
                // fuit vers la gauche, le gueulard et les conduites.
                pieceD("M36 60L38 20L46 14L41 12L33 18.5L31 58Z", sombre(acier, 0.35f), 0.5f)
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
                // La tour de charge, de trois quarts : son treillis sur la face.
                boite3q(86f, 60f, 8f, 48f, 6f, acier, versDroite = false, pente = 0.1f) {
                    var y = 6f
                    while (y < 46f) { ligne(1f, y, 7f, y + 8f, rouille, 0.3f); ligne(7f, y, 1f, y + 8f, rouille, 0.3f); y += 8f }
                }
                trait("M84 22L74 18L70 18", acier, 1.2f)
                if (P.allume) for (k in 0 until 9) rond(38f + r() * 56f, 14f + r() * 40f, 0.4f, P.fenetre)
            }
            plan(2)
            solDePierre(SolDePierre.DALLES, DEVANT, -0.02f)  // le parvis des hauts fourneaux
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
            solNaturel(SolNaturel.TERRE)  // le chemin sous l'arche
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
            lisiere(-2f, 102f, { x -> 24f + 2.4f * sin(x * 0.07f + 0.6f) }, 1.6f, 2.4f, 206, nappe(mix(P.loin, P.milieu, 0.3f), 0.3f, VERT_CALME))
            plan(1)
            val ocre = duJour(0xFFDDB05A.toInt())
            val aile = duJour(0xFFEDE9E0.toInt())
            val toit = ardoise()
            val terrasse = duJour(0xFFD8CCAE.toInt())
            // Le château de trois quarts : l'aile ocre, le corps central et
            // ses deux tours carrées, l'aile blanche, sur la terrasse à
            // arcades qui fuit avec eux.
            val f = FUITE_ANSEBUERG
            val fin = CHATEAU_ANSEBUERG.sumOf { it[0].toDouble() }.toFloat()
            val terrasseH = 8f
            val bord = floatArrayOf(f.x(-14f), f.y(-14f), f.x(fin + 8f), f.y(fin + 8f),
                f.x(fin + 8f), f.y(fin + 8f) + terrasseH * f.echelle(fin + 8f), f.x(-14f), f.y(-14f) + terrasseH * f.echelle(-14f))
            // Le château d'Ansembourg garde son ocre : [froid] protège du
            // « jaune IA » des enduits ordinaires, mais ici la pierre est
            // vraiment d'un miel chaud (photo du propriétaire), et la
            // neutraliser rendait le sujet plus terne que le feuillage autour
            // de lui — c'est ce que le propriétaire avait relevé en main.
            rue3q(f, arrayOf(CHATEAU_ANSEBUERG[0]), intArrayOf(ocre), r, froid = false)
            rue3q(f, arrayOf(CHATEAU_ANSEBUERG[1]), intArrayOf(ocre), r, debut = CHATEAU_ANSEBUERG[0][0], froid = false)
            rue3q(f, arrayOf(CHATEAU_ANSEBUERG[2]), intArrayOf(ocre, aile), r, debut = CHATEAU_ANSEBUERG[0][0] + CHATEAU_ANSEBUERG[1][0], froid = false)
            for (d in floatArrayOf(3f, 24f)) rue3q(f, arrayOf(TOUR_ANSEBUERG), intArrayOf(ocre), r, debut = CHATEAU_ANSEBUERG[0][0] + d, froid = false)
            mineral {
                piece(Path().apply {
                    moveTo(bord[0], bord[1]); lineTo(bord[2], bord[3]); lineTo(bord[4], bord[5]); lineTo(bord[6], bord[7]); close()
                }, terrasse)
                val largeur = fin + 22f
                surQuad(largeur, terrasseH, bord) {
                    val creux = mix(terrasse, P.pres, 0.45f)
                    var ax = 14f + CHATEAU_ANSEBUERG[0][0] + 2f
                    while (ax < 14f + CHATEAU_ANSEBUERG[0][0] + CHATEAU_ANSEBUERG[1][0] - 3f) {
                        pieceD("M$ax 8L$ax 4C$ax 2.2 ${ax + 3.2f} 2.2 ${ax + 3.2f} 4L${ax + 3.2f} 8Z", creux, 0f)
                        ax += 5f
                    }
                    ligne(0f, 0.4f, largeur, 0.4f, mix(terrasse, P.pres, 0.3f), 0.4f)
                }
                // La balustrade, qui se serre en fuyant.
                var u = -14f
                while (u < fin + 8f) {
                    val e = f.echelle(u)
                    ligne(f.x(u), f.y(u), f.x(u), f.y(u) - 1.4f * e, terrasse, 0.3f * e)
                    u += 1.8f
                }
                ligne(f.x(-14f), f.y(-14f) - 1.4f, f.x(fin + 8f), f.y(fin + 8f) - 1.4f * f.echelle(fin + 8f), terrasse, 0.4f)
            }
            for (x in floatArrayOf(12f, 88f)) {
                pieceD("M$x 40L${x - 1.6f} 34C${x - 1.4f} 30 ${x - 0.4f} 27 $x 26C${x + 0.4f} 27 ${x + 1.4f} 30 ${x + 1.6f} 34Z", vert(0.15f))
            }
            touffe(vert(0.4f), 4f, 30f, 4f, 96f, 30f, 4f)
            // La pelouse, qui part du pied de la terrasse et fuit avec elle.
            val pente = (bord[5] - bord[7]) / (bord[4] - bord[6])
            fun pied(x: Float) = bord[7] + (x - bord[6]) * pente - 0.4f
            pieceD("M-2 66L-2 ${pied(-2f)}L102 ${pied(102f)}L102 66Z", nappe(duJour(0xFFA9C566.toInt()), 0.5f), 0.4f)
            plan(2)
            solDePierre(SolDePierre.GRAVIER, DEVANT)  // l'allée du jardin
            poser(DEVANT)
            // La charmille taillée, en deux massifs latéraux. Elle faisait
            // avant une arche sur toute la fenêtre : un aplat vert qui
            // occupait les deux tiers de l'image et ne laissait du château
            // qu'une lucarne (propriétaire, 2026-11-15). Elle reste donc
            // dans les bandes que le dessin du mot ne prend pas (x < 17 et
            // x > 83) et sous la ligne du toit, en s'abaissant vers le
            // centre comme une charmille taillée en berceau.
            val haie = vert(0.1f)
            for ((bord, dedans) in listOf(-3f to 15f, 103f to 85f)) {
                val massif = Path().apply {
                    moveTo(bord, DEVANT + 0.2f); lineTo(bord, 31f); lineTo(dedans, 39f)
                    lineTo(dedans, DEVANT + 0.2f); close()
                }
                val feuilles = Path()
                for (k in 0..12) {
                    val u = k / 12f
                    feuilles.addCircle(bord + (dedans - bord) * u, 31f + 8f * u, 1.3f + r() * 1f, Path.Direction.CW)
                }
                massif.op(feuilles, Path.Op.UNION)
                piece(massif, haie)
            }
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
            // Le versant d'en face. Le Pfaffenthal est une vallée encaissée :
            // depuis le fond on ne voit pas un horizon dégagé mais la pente
            // boisée qui monte, et la ville haute sur sa crête. Sans elle, le
            // ciel occupait 60 % de la fenêtre et ses bandes jaunes de
            // l'horizon ne représentaient rien (propriétaire, 2026-11-15).
            val crete = CRETE_PAFENDALL
            lisiere(-2f, 102f, { x -> crete + 3.4f * sin(x * 0.055f + 1.2f) }, 2.2f, 3.4f, 213,
                mix(vert(0.08f), P.cielBas, 0.44f))
            // La ville haute sur la crête, **en silhouette** : à ce plan, des
            // maisons dessinées une à une, avec leurs fenêtres et leurs
            // toits, se lisaient comme des étiquettes posées sur la ligne
            // (propriétaire, 2026-11-15). Un lointain se lit en silhouette
            // (règle 1) et s'efface par la couleur (règle 3) : c'est donc un
            // seul découpage, d'un seul ton proche de l'horizon, avec deux ou
            // trois clochers pour qu'on y reconnaisse une ville.
            mineral {
                fun cr(x: Float) = crete + 3.4f * sin(x * 0.055f + 1.2f)
                val rh = Alea(212)
                val ville = neutre(mix(P.loin, P.cielBas, 0.42f))
                val t = Path()
                t.moveTo(-3f, cr(-3f))
                var hx = -3f
                while (hx <= 103f) {
                    val hw = 2.6f + rh() * 3.4f
                    val hh = 1.4f + rh() * 2.6f
                    t.lineTo(hx, cr(hx) - hh)
                    if (rh() < 0.16f) {
                        // Un clocher, de loin en loin.
                        val cx = hx + hw * 0.5f
                        t.lineTo(cx - 0.5f, cr(cx) - hh)
                        t.lineTo(cx - 0.5f, cr(cx) - hh - 3.4f - rh() * 2f)
                        t.lineTo(cx, cr(cx) - hh - 5.4f - rh() * 2f)
                        t.lineTo(cx + 0.5f, cr(cx) - hh - 3.4f - rh() * 2f)
                        t.lineTo(cx + 0.5f, cr(cx) - hh)
                    }
                    t.lineTo(hx + hw, cr(hx + hw) - hh)
                    hx += hw
                }
                t.lineTo(103f, cr(103f) + 5f)
                var bx = 103f
                while (bx >= -3f) { t.lineTo(bx, cr(bx) + 5f); bx -= 4f }
                t.close()
                piece(t, ville, 0.25f)
            }
            // Les bois du versant, en nappes de plus en plus vertes à
            // mesure qu'elles approchent : la vallée du Pfaffenthal est
            // boisée de haut en bas, et le fond ne doit pas être une bande de
            // ciel jaune (propriétaire, 2026-11-15).
            lisiere(-2f, 102f, { x -> crete + 5.4f + 2.8f * sin(x * 0.07f + 0.4f) }, 2.4f, 3.6f, 218,
                mix(vert(0.16f), P.cielBas, 0.3f))
            lisiere(-2f, 102f, { x -> crete + 10.2f + 2.4f * sin(x * 0.09f + 2.1f) }, 2.6f, 3.8f, 219,
                mix(vert(0.3f), P.cielBas, 0.16f))
            // Quelques bouquets détachés sur la pente, pour qu'elle ne soit
            // pas qu'une succession de lignes.
            touffe(mix(vert(0.22f), P.cielBas, 0.24f), 12f, crete + 8f, 3.4f, 30f, crete + 7f, 3f,
                52f, crete + 8.6f, 3.6f, 74f, crete + 7.4f, 3.2f, 92f, crete + 8.8f, 3f)
            touffe(mix(vert(0.38f), P.cielBas, 0.1f), 4f, crete + 13f, 3.8f, 24f, crete + 14f, 3.4f,
                46f, crete + 13.2f, 4f, 66f, crete + 14.4f, 3.6f, 86f, crete + 13f, 3.8f)
            // La pente boisée qui descend vers le fond de la vallée.
            lisiere(-2f, 102f, { x -> 40f + 2.4f * sin(x * 0.08f) }, 1.6f, 2.4f, 216, nappe(mix(P.milieu, P.loin, 0.25f), 0.3f, VERT_CALME))
            // Le Pfaffenthal au fond de la vallée, en rue de trois quarts.
            rue3q(RUE_PAFENDALL, MAISONS_PAFENDALL, facades(), r, ecart = 3f)
            plan(1)
            lisiere(-2f, 102f, { x -> 49f + 2f * sin(x * 0.2f) }, 1.8f, 2.8f, 217, vert(0.4f))
            plan(2)
            solDePierre(SolDePierre.PAVES, DEVANT, -0.02f)  // la rue du Pfaffenthal
            poser(DEVANT)
            // Le béton de la tour est blanc, et c'est lui le sujet du lieu :
            // sur la photo du propriétaire, la tour crève le ciel au milieu
            // et la passerelle la traverse, les deux formant une croix. Elle
            // était une dalle gris-beige collée au bord droit, à moitié hors
            // cadre, que l'on ne remarquait pas (jugé sur la photo, 2026-11-15).
            val beton = duJour(0xFFF7F7F5.toInt())
            // L'acier et le béton dessinés à la main passent par [neutre],
            // comme tout le bâti (règle 10) : sans lui, `raviver` virait le
            // gris au brun et cernait chaque pièce d'un liséré orange. Le
            // [beton] brut reste pour [boite3q], qui neutralise lui-même.
            val gris = neutre(duJour(0xFF55595F.toInt()))
            val betonN = neutre(beton)
            val verre = duJour(0xFF9FD3EC.toInt())
            mineral {
                // L'ordre compte : la passerelle **passe derrière la tour**,
                // comme sur la photo du propriétaire, donc elle se peint
                // avant elle. Peinte après, elle lui passait devant et
                // coupait son béton en deux (2026-11-15).
                //
                // Proportions mesurées sur la photo, rapportées à la largeur
                // dessinée de la tour (sa face **plus** le flanc que
                // [boite3q] projette, `p` × 0,62, soit 9 + 2 = 11) : la tour
                // fait 3,5 fois plus haut que large, et la passerelle court
                // sur 4,8 fois cette largeur. Elle n'en faisait que 3,5, et
                // paraissait courte et basse (propriétaire, 2026-11-15) ; son
                // épaisseur a été relevée deux fois à son œil, 4,3 puis 5,5,
                // et vaut maintenant 6,5 unités, soit 0,59 largeur de tour.
                val passerelle = floatArrayOf(-3f, 16f, 50f, 11.9f, 50f, 17.7f, -3f, 22.5f)
                piece(quad(passerelle), mix(betonN, gris, 0.16f), 0.6f)
                surQuad(53f, 6.5f, passerelle) {
                    // Le garde-corps en résille, et le tablier sombre dessous.
                    rect(0f, 0.45f, 41f, 3.3f, mix(betonN, gris, 0.3f))
                    ligne(0f, 0.45f, 41f, 0.45f, gris, 0.3f)
                    var mx = 1.8f
                    while (mx < 41f) { ligne(mx, 0.9f, mx, 3.75f, gris, 0.12f); mx += 4f }
                    // Le bout vitré : la passerelle s'achève sur ses fenêtres,
                    // dans sa propre hauteur. Il n'y a pas de cube de verre
                    // sur la photo du propriétaire, c'était une invention
                    // (jugement du propriétaire, 2026-11-15).
                    rect(41f, 0.45f, 12f, 3.3f, verre, 0.22f)
                    rect(41f, 0.45f, 12f, 0.8f, mix(verre, 0xFFFFFFFF.toInt(), 0.5f), 0.3f)
                    ligne(41f, 0.45f, 53f, 0.45f, gris, 0.42f)
                    ligne(41f, 3.75f, 53f, 3.75f, gris, 0.42f)
                    ligne(41f, 0.45f, 41f, 3.75f, gris, 0.3f)
                    ligne(52.9f, 0.45f, 52.9f, 3.75f, gris, 0.42f)
                    for (vx in floatArrayOf(45f, 49f)) ligne(vx, 0.45f, vx, 3.75f, gris, 0.16f)
                    // Le tablier, sous toute la longueur.
                    rect(0f, 4.5f, 53f, 2f, gris)
                }

                // La tour par-dessus : elle masque la passerelle derrière elle.
                // Rien sur son toit : les garde-corps et les antennes y
                // faisaient du bruit sans rien dire (propriétaire, 2026-11-15).
                boite3q(9f, DEVANT, 9f, HAUT_PAFENDALL, 3.2f, beton,
                        versDroite = true, pente = 0.05f, dessus = true) {
                    // Le local de machinerie, en haut, plus sombre.
                    rect(0f, 0f, 9f, 2.6f, mix(beton, gris, 0.42f))
                    // La gaine vitrée où monte la cabine, et ses lames d'acier.
                    rect(2.8f, 3.4f, 3.6f, 24f, mix(beton, gris, 0.62f))
                    var ly = 4.2f
                    while (ly < 27f) { ligne(2.8f, ly, 6.4f, ly, gris, 0.26f); ly += 1.1f }
                    // Les deux arêtes, qui donnent sa silhouette à la tour.
                    rect(0f, 0f, 0.65f, HAUT_PAFENDALL, mix(beton, gris, 0.55f))
                    rect(8.35f, 0f, 0.65f, HAUT_PAFENDALL, mix(beton, gris, 0.3f))
                }

                // Le mât d'acier sous la tour, et son pied sur la rue.
                ligne(8.4f, 21.5f, 8.4f, DEVANT, gris, 0.5f)
                ligne(19.6f, 20.5f, 19.6f, DEVANT, gris, 0.4f)
                // Les obliques sous la passerelle.
                ligne(6.2f, 21.7f, -3f, 34f, gris, 0.3f)
                ligne(44f, 18.2f, 38f, DEVANT, gris, 0.3f)
            }
            // Les arbres du pied : sur la photo ils mangent le bas de la
            // tour, et c'est leur vert sombre qui détache son béton blanc.
            touffe(sombre(vert(0.25f), 0.3f), 4f, 44f, 7f, 13f, 47f, 6f, 21f, 45f, 5.5f, -1f, 48f, 6f)
            if (!vivant) cabine(30f)
            touffe(vert(0.55f), 99f, 52f, 7f, 90f, 55f, 5f, 102f, 42f, 6f, 94f, 46f, 4f)
            c.restore()
        }

        /** La cabine vitrée de l'ascenseur, son haut en [y]. */
        fun cabine(y: Float) {
            val gris = duJour(0xFF55595F.toInt())
            // Du verre dans la gaine, et non un carton bleu posé dessus :
            // voir la note du belvédère, même remarque du propriétaire.
            rect(11.8f, y, 3.6f, 6f, duJour(0xFF9FD3EC.toInt()), 0.42f)
            rect(11.8f, y, 3.6f, 1.4f, duJour(0xFFD8EEF8.toInt()), 0.4f)
            ligne(11.8f, y, 15.4f, y, gris, 0.3f)
            ligne(11.8f, y + 6f, 15.4f, y + 6f, gris, 0.4f)
            ligne(11.8f, y, 11.8f, y + 6f, gris, 0.16f)
            ligne(15.4f, y, 15.4f, y + 6f, gris, 0.16f)
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
            // Le rocher de gauche dessiné de face, posé de trois quarts : ses
            // strates fuient vers la droite, comme le caillebotis.
            surQuad(41f, 69f, ROCHER_FELSMILLEN, x0 = -3f, y0 = -3f) {
                mineral {
                    pieceD("M-3 66L-3 -3L34 -3C38 4 36 10 31 13C35 17 34 24 28 27C31 31 30 38 25 42L24 66Z", gres)
                    pieceD("M8 6C16 3 26 4 31 10C25 11 16 11 8 9Z", clair, 0.4f)
                    pieceD("M6 20C14 17 24 18 29 24C22 25 14 25 6 23Z", clair, 0.4f)
                    pieceD("M4 34C12 31 20 32 25 38C19 39 11 39 4 37Z", clair, 0.4f)
                }
                touffe(duJour(0xFF6E8A44.toInt()), 30f, 13f, 1.6f, 27f, 27f, 1.6f, 22f, 42f, 2f)
            }
            mineral { pieceD("M40 30C42 26 50 25 56 27C58 30 56 34 50 35C44 35 40 34 40 30Z", gres) }
            plan(2)
            solNaturel(SolNaturel.TERRE)  // le sentier au pied des rochers
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
            solNaturel(SolNaturel.BERGE)  // la rive du lac
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
            solNaturel(SolNaturel.BERGE)  // la rive du lac
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
            // De trois quarts : la lisière de la forêt fuit vers la droite,
            // ses épicéas rapetissent au loin, et la prairie la suit.
            val f = LISIERE_CONZEFENN
            val fin = 300f
            val epiceas = sombre(vert(0.15f), 0.25f)
            val foret = Path()
            foret.moveTo(-3f, BAS + 2f)
            foret.lineTo(-3f, f.y(0f) - 4f)
            var u = -4f
            while (u <= fin) {
                val e = f.echelle(u)
                val x = f.x(u); val base = f.y(u)
                val h = (7f + r() * 5f) * e
                val l = (1.6f + r() * 0.8f) * e
                foret.lineTo(x - l, base - h * 0.25f)
                foret.lineTo(x, base - h)
                foret.lineTo(x + l, base - h * 0.25f)
                u += 2.4f + r() * 1.6f
            }
            foret.lineTo(103f, f.y(fin) - 2f)
            foret.lineTo(103f, BAS + 2f); foret.close()
            piece(foret, epiceas)
            plan(1)
            // La prairie, son bord le long de la lisière.
            piece(Path().apply {
                moveTo(-2f, 66f); lineTo(-2f, f.y(-4f) + 0.6f)
                var v = 0f
                while (v <= fin) { lineTo(f.x(v), f.y(v) + 0.6f * f.echelle(v)); v += 8f }
                lineTo(102f, f.y(fin)); lineTo(102f, 66f); close()
            }, vert(0.85f), 0.4f)
            // Les moutons et les fleurs posés sur la prairie qui fuit : plus
            // petits et plus serrés au loin, contre la lisière.
            fun auSol(uu: Float, recul: Float): FloatArray {
                val e = f.echelle(uu)
                return floatArrayOf(f.x(uu) - recul * 0.9f * e, f.y(uu) + recul * e, e)
            }
            for (k in 0 until 18) {
                val p = auSol(10f + r() * 220f, 3f + r() * 9f)
                mouton(p[0], p[1], (0.75f + r() * 0.2f) * p[2], r(), r() < 0.5f)
            }
            for (k in 0 until 14) {
                val p = auSol(r() * 240f, 2f + r() * 12f)
                rond(p[0], p[1], 0.55f * p[2], duJour(0xFFF2C230.toInt()))
            }
            plan(2)
            solNaturel(SolNaturel.HERBE)  // la prairie
            poser(DEVANT)
            pieceD("M-2 66L-2 47C30 44.5 70 41.5 102 40L102 66Z", vert(0.7f), 0.4f)
            if (!vivant) for ((k, mx) in MOUTONS.withIndex()) mouton(mx, 47f + (k % 2) * 3f, 1f, if (k % 2 == 0) 1f else 0f, k % 3 != 0)
            // La clôture, qui fuit avec la lisière : ses piquets rapetissent
            // et ses lisses se rejoignent vers le fond.
            val c2 = CLOTURE_CONZEFENN
            val bois = duJour(0xFF7A6248.toInt())
            val piquets = floatArrayOf(0f, 18f, 40f, 66f, 96f, 132f, 176f)
            val bout = piquets.last()
            for (hl in floatArrayOf(9f, 5f)) {
                piece(Path().apply {
                    moveTo(c2.x(0f), c2.y(0f) - hl)
                    lineTo(c2.x(bout), c2.y(bout) - hl * c2.echelle(bout))
                    lineTo(c2.x(bout), c2.y(bout) - (hl - 1.2f) * c2.echelle(bout))
                    lineTo(c2.x(0f), c2.y(0f) - (hl - 1.2f))
                    close()
                }, bois, 0.6f)
            }
            for (pu in piquets) {
                val e = c2.echelle(pu)
                feuille(c2.x(pu) - 0.7f * e, c2.y(pu) - 12.5f * e, 1.4f * e, 12.5f * e, bois, 0.6f)
            }
            for (k in 0 until 14) {
                val e = 0.8f + r() * 0.2f
                rond(r() * 100f, 53f + r() * 5f, 0.5f * e, duJour(0xFFF2C230.toInt()))
            }
            c.restore()
        }

        // ---- Haff Réimech, le pont couvert (photo du propriétaire)

        fun haffReimech() {
            lisiere(-2f, 102f, { x -> 16f + 3f * sin(x * 0.09f) }, 2f, 3.2f, 266, mix(vert(0.4f), P.loin, 0.35f))
            plan(1)
            // Le pont couvert de trois quarts : il entre par la gauche et fuit
            // vers la droite, ses piles et ses fenêtres se serrant au loin.
            val f = PONT_REIMECH
            val long = 340f
            val eauCol = mix(P.eau, 0xFF5F7046.toInt(), 0.4f)
            fun bas(u: Float) = f.y(u) + PILE_REIMECH * f.echelle(u)
            piece(Path().apply {
                moveTo(-2f, 66f); lineTo(-2f, bas(0f) - 2f)
                var u = 0f
                while (u <= long) { lineTo(f.x(u), bas(u) - 2f * f.echelle(u)); u += 10f }
                lineTo(102f, 66f); close()
            }, eauCol, 0.4f)
            val pierre = duJour(0xFFA8A090.toInt())
            val bois = duJour(0xFF8C6A48.toInt())
            val toitPont = sombre(duJour(0xFF7A6A58.toInt()), 0.25f)
            // Le côté d'une boîte : de [u] vers le point de fuite latéral.
            fun cote(x: Float, y: Float, e: Float, p: Float): FloatArray {
                val dx = f.fuiteCote[0] - x; val dy = f.fuiteCote[1] - y
                val k = p * e / sqrt(dx * dx + dy * dy)
                return floatArrayOf(dx * k, dy * k)
            }
            mineral {
                // Les piles, du fond vers l'avant : leur face, et leur côté à l'ombre.
                for (u0 in floatArrayOf(221f, 33f)) {
                    val u1 = u0 + 14f
                    val e0 = f.echelle(u0)
                    val face = floatArrayOf(f.x(u0), f.y(u0), f.x(u1), f.y(u1), f.x(u1), bas(u1), f.x(u0), bas(u0))
                    val d = cote(f.x(u0), f.y(u0), e0, 9f)
                    piece(quad(floatArrayOf(f.x(u0) + d[0], f.y(u0) + d[1], f.x(u0), f.y(u0), f.x(u0), bas(u0), f.x(u0) + d[0], bas(u0) + d[1])),
                        sombre(pierre, 0.3f), 0.5f)
                    piece(quad(face), pierre, 0.6f)
                    surQuad(14f, PILE_REIMECH, face) {
                        val joint = sombre(pierre, 0.25f)
                        var y = 3f
                        while (y < PILE_REIMECH) { ligne(0f, y, 14f, y, joint, 0.2f, 0.6f); y += 3.5f }
                    }
                }
                // La galerie de bois, son toit qui monte vers le faîte, et la poutre.
                val murH = 6.4f
                val haut = { u: Float -> f.y(u) - murH * f.echelle(u) }
                val mur = floatArrayOf(f.x(0f), haut(0f), f.x(long), haut(long), f.x(long), f.y(long), f.x(0f), f.y(0f))
                val d0 = cote(f.x(0f), haut(0f), 1f, 5f)
                val dl = cote(f.x(long), haut(long), f.echelle(long), 5f)
                piece(quad(floatArrayOf(f.x(0f) + d0[0], haut(0f) + d0[1] - 4f, f.x(long) + dl[0], haut(long) + dl[1] - 4f * f.echelle(long),
                    f.x(long) + 0.6f, haut(long) + 0.4f, f.x(0f) - 1f, haut(0f) + 0.6f)), toitPont, 0.6f)
                piece(quad(mur), bois, 0.6f)
                surQuad(long, murH, mur) {
                    val creux = sombre(bois, 0.6f)
                    var x = 2f
                    var k = 0
                    while (x < long - 6f) {
                        rect(x, 1.4f, 5f, 3.4f, creux)
                        if (P.allume && k % 3 == 0) rect(x + 0.6f, 1.8f, 3.8f, 2.6f, P.fenetre, 0.5f)
                        x += 9f; k++
                    }
                }
                val poutre = floatArrayOf(f.x(0f), f.y(0f), f.x(long), f.y(long), f.x(long), f.y(long) + 1.6f * f.echelle(long), f.x(0f), f.y(0f) + 1.6f)
                piece(quad(poutre), sombre(bois, 0.35f), 0.4f)
            }
            plan(2)
            solNaturel(SolNaturel.BERGE)  // la berge de la Sûre
            poser(DEVANT)
            pieceD("M-3 4C20 2 40 6 60 3C70 2 84 6 103 2L103 4.4C84 8 70 4.4 60 5.4C40 8.6 20 4.6 -3 6.6Z", duJour(0xFF7A7048.toInt()), 0.5f)
            touffe(vert(0.5f), 0f, 30f, 6f, 4f, 44f, 6f, -2f, 54f, 5f, 96f, 36f, 6f, 100f, 48f, 6f, 92f, 54f, 5f)
            c.restore()
        }

        // ---- Hohllay, la grotte de Berdorf (photo du propriétaire)

        fun hohllay() {
            // Le grès de la Hohllay est gris-beige, et la grotte est éclairée
            // du dehors : sa pierre passe par [neutre], comme tout le bâti
            // dessiné à la main (règle 10). Sans cela `raviver` la poussait à
            // l'orange vif sur les quatre heures, ce que la skill signalait
            // déjà « à surveiller » et que la photo du propriétaire confirme.
            val gres = neutre(duJour(0xFFBFAE97.toInt()))
            val rose = neutre(duJour(0xFFCFC2AE.toInt()))
            val creux = sombre(gres, 0.8f)
            mineral {
                feuille(-3f, -3f, 106f, 72f, creux, 0f)
                pieceD("M-3 10L103 10L103 52L-3 52Z", rose, 0.4f)
                for ((ax, w) in listOf(6f to 10f, 30f to 16f, 56f to 16f, 88f to 10f)) {
                    pieceD("M$ax 52L$ax ${44f - w * 0.4f}C$ax ${30f - w * 0.3f} ${ax + w} ${30f - w * 0.3f} ${ax + w} ${44f - w * 0.4f}L${ax + w} 52Z", creux, 0.18f)
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
            solNaturel(SolNaturel.TERRE)  // le sol sableux de la grotte
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
            solDePierre(SolDePierre.GRAVIER, DEVANT)  // le chemin du parc
            poser(DEVANT)
            pieceD("M30 58.5C44 52 64 48 80 46L86 46C78 50 66 54 58 58.5Z", duJour(0xFF4A4C50.toInt()), 0.4f)
            val pierre = duJour(0xFF6E6862.toInt())
            mineral {
                // Le socle, de trois quarts comme la tour : sa face, et le côté qui fuit.
                boite3q(72f, 58.5f, 18f, 10.5f, 10f, sombre(pierre, 0.1f), pente = 0.06f)
                boite3q(76f, 49f, 12f, 9f, 8f, pierre, pente = 0.06f)
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
            // Le mur de tuf dessiné de face, posé de trois quarts : il fuit
            // vers la droite, ses filets d'eau se serrant au loin.
            surQuad(106f, 41f, MUR_TUF, x0 = -3f, y0 = -3f) {
                mineral {
                    pieceD("M-3 -3L70 -3C78 6 86 14 96 18L103 20L103 38L-3 38Z", tuf)
                    var x = 2f
                    while (x < 98f) { ligne(x, 24f + r() * 4f, x + r(), 36f, sombre(tuf, 0.5f), 0.5f, 0.6f); x += 3f }
                    feuille(-3f, 24f, 106f, 2.4f, sombre(tuf, 0.45f), 0f)
                }
                pieceD("M-3 -3L66 -3C70 6 64 14 56 20C44 26 24 24 10 26C4 27 0 26 -3 27Z", mousse)
            }
            plan(2)
            solNaturel(SolNaturel.MOUSSE)  // la mousse autour de la source
            poser(DEVANT)
            val bassin = mix(duJour(0xFF6FB8B0.toInt()), P.eau, 0.2f)
            // Le bassin, dont le bord suit le pied du mur qui fuit.
            val bordL = surMurTuf(-3f, 37f); val bordR = surMurTuf(103f, 37f)
            pieceD("M-3 66L-3 ${bordL[1]}L103 ${bordR[1]}L103 66Z", bassin, 0.4f)
            val reflet = mix(bassin, 0xFFFFFFFF.toInt(), 0.5f)
            ligne(-3f, bordL[1] + 1.2f, 103f, bordR[1] + 1.2f * (bordR[1] / bordL[1]), reflet, 0.5f, 0.7f)
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
            // Le pont Adolphe est du bâti : sa pierre passe par [neutre],
            // sinon `raviver` la tire vers l'ocre de la vallée derrière elle
            // et l'arche disparaît (règles 1 et 10 de la skill
            // `direction-artistique`, jugé en main sur un A21s le 2026-11-15).
            val pierre = neutre(duJour(0xFFD3C29C.toInt()))
            // Le pont Adolphe dessiné de face, posé de trois quarts : son pied
            // gauche tout près, son tablier qui fuit vers la droite.
            surQuad(PONT_PEITRUSDALL_L, PONT_PEITRUSDALL_H, PONT_PEITRUSDALL, x0 = -3f, y0 = 0f) { mineral {
                pieceD("M-3 6L86 5L86 10L-3 11Z", pierre)
                feuille(-3f, 5f, 89f, 0.8f, sombre(pierre, 0.25f), 0f)
                pieceD("M-3 10.6L50 10.2L46 13C30 14 14 26 8 46L-3 46Z", pierre)
                pieceD("M8 46C14 26 30 13 46 12.6C62 12 76 18 86 28L82 30C74 21 62 16 46 16.4C32 17 18 28 12 46Z", sombre(pierre, 0.24f))
                for (ax in floatArrayOf(2f, 10f, 18f, 26f)) {
                    val h = 12f + (30f - ax) * 0.3f
                    pieceD("M$ax ${h + 6f}L$ax ${h + 2f}C$ax ${h - 0.6f} ${ax + 5f} ${h - 0.6f} ${ax + 5f} ${h + 2f}L${ax + 5f} ${h + 6f}Z", sombre(pierre, 0.45f), 0f)
                }
                for (lx in floatArrayOf(14f, 40f, 66f)) { ligne(lx, 1f, lx, 5f, duJour(0xFF55585E.toInt()), 0.25f); if (P.allume) rond(lx, 0.8f, 0.6f, P.fenetre) }
            }
                if (!vivant) tram(54f, 2.2f)
            }
            plan(2)
            solNaturel(SolNaturel.HERBE)  // le parc de la vallée
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
            solNaturel(SolNaturel.TERRE)  // le sentier de la gorge
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
            solNaturel(SolNaturel.MOUSSE)  // les grès moussus
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
            solNaturel(SolNaturel.BERGE)  // la rive de l'étang
            poser(DEVANT)
            val bois = duJour(0xFF9C8A70.toInt())
            fun pont(x: Float) = 46f - 18f * (1f - ((x - 53f) / 43f).pow(2))
            // La passerelle dessinée de face, posée de trois quarts : plus haute
            // à gauche, ses barreaux qui se serrent vers la rive d'en face.
            surQuad(90f, 25f, PASSERELLE_DUMONGSHAFF, x0 = 7.5f, y0 = 25.5f) {
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
            solNaturel(SolNaturel.HERBE)  // la pelouse sous le pont
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
            // Le Breedewee, la ruelle en escalier du Grund, **vue par sa
            // porte** (propriétaire, 2026-11-16) : l'escalier n'est plus à
            // côté de la porte, il est dedans. On se tient devant l'arche,
            // et toute la ruelle s'ouvre au travers — les marches qui
            // montent, la rangée de maisons de trois quarts, et le passage
            // voûté tout au fond. La porte est le cadre de scène.
            //
            // Ce qui tient cette carte, et qui a demandé plusieurs essais :
            //
            // - **La matière.** Appareil de pierre pour la porte et les murs
            //   ([appareil]), enduit, corniche, bandeaux et chaînes d'angle
            //   pour les façades ([matiere]), ardoises rang par rang. Et tout
            //   dans [neutre] : peint avec `duJour` seul, le bâti sort d'un
            //   même jaune de miel.
            // - **Les tailles relatives.** Ce qui est au fond de la ruelle est
            //   loin : le mur qui la ferme a des moellons menus et une porte
            //   étroite. À l'échelle du premier plan, un moellon y valait
            //   trois fenêtres de maison.
            // - **Les bords de l'escalier suivent exactement la ligne de
            //   fuite de la rangée.** Décalés de quelques unités, ils
            //   laissent entre eux une bande de ciel.
            val r = Alea(355)
            val pierre = neutre(duJour(0xFFB4AB98.toInt()))
            val mur = neutre(duJour(0xFF9A9080.toInt()))
            val toit = neutre(ardoise())
            val murs = IntArray(6) { neutre(facades()[it]) }
            val pave = neutre(duJour(0xFF8E8678.toInt()))
            val distance = mix(P.cielBas, neutre(P.cielBas), 0.6f)
            mineraux++

            // ---- Le fond de la ruelle : le talus, le mur qui la ferme et
            // son passage voûté, où l'escalier va se rendre.
            pieceD("M33 32L34 20.6C39 19.2 53 18.8 58 20.2L58 32Z",
                mix(sombre(neutre(duJour(0xFF8E8775.toInt())), 0.38f), distance, 0.24f))
            lisiere(35f, 56f, { x -> 21.6f + 0.8f * sin(x * 0.5f) }, 0.55f, 1f, 368, vert(0.4f))
            appareil(35f, 23.4f, 21f, 8.6f, murs[0], 1.1f, 1.9f, 369)
            pieceD("M33.6 23.7L45.5 20.4L57.4 23.7Z", toit)
            ligne(33.6f, 23.7f, 57.4f, 23.7f, neutre(mix(ardoise(), 0xFFFFFFFF.toInt(), 0.18f)), 0.25f)
            val voute = chemin("M42 32L42 29C42 27.2 49 27.2 49 29L49 32Z")
            piece(voute, sombre(neutre(duJour(0xFF2E2820.toInt())), 0.25f), 0f)
            forme(chemin("M42 32L42 30.2C44 29.6 47 29.6 49 30.2L49 32Z"),
                if (P.allume) P.fenetre else mix(pierre, 0xFFFFFFFF.toInt(), 0.3f), if (P.nuit) 0.5f else 0.3f)
            for (k in 0 until 7) {
                val a0 = PI.toFloat() * (1f + k / 7f)
                val a1 = PI.toFloat() * (1f + (k + 1) / 7f)
                val q = Path()
                q.moveTo(45.5f + cos(a0) * 3.5f, 29f + sin(a0) * 1.8f)
                q.lineTo(45.5f + cos(a1) * 3.5f, 29f + sin(a1) * 1.8f)
                q.lineTo(45.5f + cos(a1) * 4.5f, 29f + sin(a1) * 2.5f)
                q.lineTo(45.5f + cos(a0) * 4.5f, 29f + sin(a0) * 2.5f)
                q.close()
                piece(q, if (k % 2 == 0) mix(pierre, 0xFFFFFFFF.toInt(), 0.16f) else sombre(pierre, 0.2f))
            }
            plan(1)
            // ---- La rangée de maisons, de trois quarts, et l'escalier.
            rue3q(RUE_BREEDEWEE, MAISONS_BREEDEWEE, murs, r)
            fun bordG(y: Float) = 36f - (y - 32f) * 1.25f
            fun bordD(y: Float) = 54f + (y - 32f) * 1.25f
            // Le mur de soutènement de gauche : du côté de la montagne, une
            // ruelle du Grund n'a pas de maisons, elle a un mur. Sans lui,
            // l'escalier monte le long d'un vide.
            val soutenement = chemin("M${bordG(70f)} 70L${bordG(32.6f)} 32.6L35.6 25.6L${bordG(70f) - 16f} 60Z")
            appareil(soutenement, mur, 2.8f, 4.6f, 362, -0.55f)
            pieceD("M${bordG(32.6f)} 32.6L35.6 25.6L33.4 24.6L${bordG(32.6f) - 2.6f} 31.4Z", mix(pierre, 0xFFFFFFFF.toInt(), 0.2f))
            val cage = chemin("M${bordG(32.6f)} 32.6L${bordD(32.6f)} 32.6L${bordD(70f)} 70L${bordG(70f)} 70Z")
            piece(cage, sombre(pave, 0.45f), 0.3f)
            c.save()
            c.clipPath(cage)
            val rs = Alea(366)
            var sy = 32.6f
            var pas = 0.85f
            while (sy < 70f) {
                val g = bordG(sy); val d = bordD(sy)
                var mx = g
                while (mx < d) {
                    val mw = 2.4f + rs() * 4.6f
                    rect(mx + 0.14f, sy, mw - 0.28f, pas * 0.52f,
                        mix(pave, 0xFFFFFFFF.toInt(), 0.14f + rs() * 0.24f))
                    mx += mw
                }
                ligne(g, sy, d, sy, mix(pave, 0xFFFFFFFF.toInt(), 0.44f), pas * 0.2f, 0.9f)
                ligne(g, sy + pas * 0.58f, d, sy + pas * 0.58f, sombre(pave, 0.5f), pas * 0.42f, 0.8f)
                sy += pas
                pas *= 1.115f
            }
            c.restore()
            plan(2)
            plancher(DEVANT, BAS)
            poser(DEVANT)
            // ---- La porte, au premier plan, qui encadre toute la ruelle.
            val arcX = 44f; val arcA = 33f; val arcY = 57f; val arcH = 50f
            fun arche(a: Float, h: Float, ys: Float): Path {
                val t = Path()
                t.moveTo(arcX - a, 62f)
                t.lineTo(arcX - a, ys)
                for (i in 0..32) {
                    val u = PI.toFloat() * i / 32f
                    t.lineTo(arcX - cos(u) * a, ys - sin(u) * h)
                }
                t.lineTo(arcX + a, 62f)
                t.close()
                return t
            }
            // L'intrados : l'épaisseur du mur, visible le long du bord de
            // l'arche. C'est elle qui dit qu'on regarde par une ouverture et
            // non à travers un trou découpé dans une feuille.
            val soffite = arche(arcA, arcH, arcY)
            soffite.op(arche(arcA - 2.6f, arcH - 2.6f, arcY + 1.4f), Path.Op.DIFFERENCE)
            piece(soffite, sombre(pierre, 0.45f), 0f)
            // Le tympan : tout le mur, percé de l'arche.
            val tympan = chemin("M-3 -3L103 -3L103 62L-3 62Z")
            tympan.op(arche(arcA, arcH, arcY), Path.Op.DIFFERENCE)
            appareil(tympan, mur, 3.4f, 5.6f, 356)
            // Les claveaux, et la clé saillante.
            val rp = Alea(359)
            for (k in 0 until 21) {
                val a0 = PI.toFloat() * (1f + k / 21f)
                val a1 = PI.toFloat() * (1f + (k + 1) / 21f)
                val t = rp()
                val q = Path()
                q.moveTo(arcX + cos(a0) * arcA, arcY + sin(a0) * arcH)
                q.lineTo(arcX + cos(a1) * arcA, arcY + sin(a1) * arcH)
                q.lineTo(arcX + cos(a1) * (arcA + 5f), arcY + sin(a1) * (arcH + 5f))
                q.lineTo(arcX + cos(a0) * (arcA + 5f), arcY + sin(a0) * (arcH + 5f))
                q.close()
                piece(q, if (k == 10) mix(pierre, 0xFFFFFFFF.toInt(), 0.3f)
                    else if (t < 0.34f) sombre(pierre, 0.1f + t * 0.5f)
                    else if (t < 0.68f) mix(pierre, 0xFFFFFFFF.toInt(), 0.08f + t * 0.16f)
                    else mix(pierre, vert(0.42f), 0.12f + t * 0.2f))
            }
            pieceD("M41.4 6.6L46.6 6.6L46 0.6L42 0.6Z", mix(pierre, 0xFFFFFFFF.toInt(), 0.34f))
            // Les piédroits, en pierre de taille, et leurs impostes.
            appareil(-3f, 57f, 14f, 5f, pierre, 3.4f, 4.2f, 357)
            appareil(77f, 57f, 26f, 5f, pierre, 3.4f, 4.2f, 358)
            rect(-3f, 56f, 15f, 1.3f, mix(pierre, 0xFFFFFFFF.toInt(), 0.22f))
            rect(76f, 56f, 27f, 1.3f, mix(pierre, 0xFFFFFFFF.toInt(), 0.22f))
            // Le seuil usé, sous l'arche.
            rect(9f, 57.2f, 66f, 1.3f, sombre(pierre, 0.25f))
            rect(9f, 57.2f, 66f, 0.4f, mix(pierre, 0xFFFFFFFF.toInt(), 0.25f))
            // Les gonds scellés dans le piédroit de droite.
            for (gy in floatArrayOf(26f, 38f, 50f)) {
                rect(77.6f, gy, 2f, 0.8f, sombre(neutre(duJour(0xFF4A4038.toInt())), 0.1f))
                rond(79.4f, gy + 0.4f, 0.6f, sombre(neutre(duJour(0xFF4A4038.toInt())), 0.1f))
            }
            mineraux--
            // ---- Le lierre fleuri qui retombe de l'arche (direction validée
            // le 2026-11-01) : c'est la seule verdure de la ruelle.
            val lierre = vert(0.4f)
            val lierreClair = vert(0.62f)
            for (k in 0 until 14) {
                val a = PI.toFloat() * (1.02f + 0.5f * k / 13f)
                val lx = arcX + cos(a) * (arcA + 1.4f + r() * 2.4f)
                val ly = arcY + sin(a) * (arcH + 1.4f + r() * 2.4f)
                val chute = 2.4f + r() * 8f
                touffe(if (k % 3 == 0) lierreClair else lierre,
                    lx, ly, 1.3f + r() * 1.1f, lx + 1f, ly + chute * 0.5f, 1f + r() * 0.9f,
                    lx - 0.8f, ly + chute, 0.9f + r() * 0.8f)
                if (k % 4 == 1) rond(lx + 0.6f, ly + chute * 0.6f, 0.45f,
                    if (P.nuit) mix(P.fenetre, 0xFFFFFFFF.toInt(), 0.4f) else 0xFFF3E0F0.toInt(), 0.9f)
            }
            if (!vivant) lanterne(80f, 32f, false)
            touffe(vert(0.5f), 3f, 57f, 2.4f, 84f, 57.5f, 2.2f)
            c.restore()
        }

        // ---- Gëlle Fra, la statue dorée sur son obélisque, au couchant

        fun gelleFraPapier() {
            // Des nuages du couchant, bas sur l'horizon. Ils faisaient avant
            // 46 à 50 unités de large sur une fenêtre de 100 : trois masses
            // grises qui mangeaient tout le ciel et se lisaient comme de la
            // fumée.
            // Mêlés au bas du ciel, qui est chaud, et non au haut, qui est
            // bleu : mélangés au bleu ils sortaient gris, comme de l'orage.
            val rose = mix(P.cielBas, 0xFFFFE2C4.toInt(), if (P.nuit) 0.12f else 0.55f)
            val mauve = mix(P.cielBas, 0xFFF6CEAC.toInt(), if (P.nuit) 0.1f else 0.45f)
            cumulus(-6f, 20f, 26f, 345, mauve)
            cumulus(26f, 13f, 22f, 346, rose)
            cumulus(52f, 24f, 30f, 347, mauve)
            cumulus(2f, 29f, 20f, 351, rose)
            plan(1)
            lisiere(-2f, 102f, { x -> 53f + sin(x * 0.3f) }, 1.4f, 2f, 348, nappe(P.milieu, 0.82f))
            val r = Alea(349)
            // La ville basse, en rue qui fuit vers le pied du monument.
            rue3q(RUE_GELLE_FRA, MAISONS_GELLE_FRA, facades(), r)
            plan(2)
            solDePierre(SolDePierre.DALLES, DEVANT, -0.03f)  // l'esplanade du monument
            poser(DEVANT)
            obelisque(84.5f, 93.5f, 27.5f)
            c.restore()
        }

        /**
         * L'obélisque du Monument du Souvenir, de [x0] à [x1] au sol et dont
         * le sommet est en [haut]. C'était un trapèze presque noir d'un seul
         * tenant : un tuyau de cheminée. Sur la photo du propriétaire c'est un
         * fût de granit rose-beige, lisse, à peine fuyant, dont on ne voit
         * presque pas les joints — donc pas de moellon apparent ici : la
         * matière vient du grain du granit, des arêtes et de la face à l'ombre.
         */
        fun obelisque(x0: Float, x1: Float, haut: Float) = mineral {
            val r = Alea(352)
            val granit = neutre(duJour(0xFFB8A79C.toInt()))
            val bas = DEVANT + 1f
            val rentrant = (x1 - x0) * 0.14f
            val fut = chemin("M$x0 ${bas}L${x0 + rentrant} ${haut}L${x1 - rentrant} ${haut}L$x1 ${bas}Z")
            piece(fut, granit)
            c.save()
            c.clipPath(fut)
            // Le grain du granit : des mouchetures et quelques veines, pas des
            // assises. Les joints sont rares et fins, comme sur la photo.
            for (k in 0 until 220) {
                val gx = x0 - 1f + r() * (x1 - x0 + 2f)
                val gy = haut + r() * (bas - haut)
                val t = r()
                rond(gx, gy, 0.14f + r() * 0.2f,
                    if (t < 0.5f) sombre(granit, 0.2f + r() * 0.2f) else mix(granit, 0xFFFFFFFF.toInt(), 0.14f + r() * 0.16f), 0.5f)
            }
            val joint = sombre(granit, 0.3f)
            for (jy in floatArrayOf(0.22f, 0.47f, 0.73f)) {
                val y = haut + jy * (bas - haut)
                ligne(x0 - 1f, y, x1 + 1f, y - 0.2f, joint, 0.16f, 0.55f)
            }
            // L'arête de gauche prend le couchant, la face de droite reste à
            // l'ombre : c'est elle qui donne son volume au fût.
            forme(chemin("M${x1 - (x1 - x0) * 0.36f} ${bas}L${x1 - rentrant - (x1 - x0) * 0.22f} ${haut}" +
                "L${x1 - rentrant} ${haut}L$x1 ${bas}Z"), neutre(ombreFroide(0xFFB8A79C.toInt(), 0.42f)), 0.5f)
            rect(x0 + rentrant * 0.2f, haut, 1.3f, bas - haut, mix(granit, 0xFFFFF0D8.toInt(), 0.3f), 0.35f)
            c.restore()
            // Le couronnement, à peine saillant, qui porte la statue.
            val clair = mix(granit, 0xFFFFFFFF.toInt(), 0.14f)
            feuille(x0 + rentrant - 0.9f, haut - 1.2f, (x1 - x0) - 2f * rentrant + 1.8f, 1.3f, clair, 0.5f)
            rect(x0 + rentrant - 0.4f, haut + 0.1f, (x1 - x0) - 2f * rentrant + 0.8f, 0.4f, sombre(granit, 0.3f), 0.6f)
            // Le socle à ressauts, au pied.
            for ((dy, dx) in listOf(0f to 0f, 3f to 1.5f, 6f to 3f)) {
                feuille(x0 - dx, bas - 7.5f + dy, (x1 - x0) + 2f * dx, 3.2f, if (dy == 0f) clair else granit, 0.4f)
            }
            statue((x0 + x1) / 2f + 0.7f, haut - 1.2f, 0.74f)
        }

        /**
         * La Gëlle Fra, les pieds en ([x], [pied]), à [echelle] de sa taille
         * de référence (33 unités du socle au sommet de la couronne). Dessinée
         * pleine taille, elle dépassait son propre obélisque.
         */
        fun statue(x: Float, pied: Float, echelle: Float = 1f) {
            c.save()
            c.translate(x, pied)
            c.scale(echelle, echelle)
            c.translate(-x, -pied)
            try { statueEntiere(x, pied) } finally { c.restore() }
        }

        /**
         * Le bronze doré, de profil, tourné vers la gauche. La photo du
         * propriétaire corrige ce qu'on dessinait de mémoire : elle ne tient
         * pas une couronne *au-dessus* de sa tête, bras en arche. Elle est
         * penchée en avant et **tend la couronne de laurier devant elle, à
         * deux mains**, bras tendus vers le haut — c'est cette silhouette-là
         * qu'on reconnaît de loin.
         */
        private fun statueEntiere(x: Float, pied: Float) {
            val or = if (P.nuit) 0xFFFFD36A.toInt() else 0xFFE2AC36.toInt()
            val clair = mix(or, 0xFFFFF4C8.toInt(), 0.5f)
            val ombre = mix(or, 0xFF5A3A08.toInt(), 0.42f)
            val pli = mix(or, 0xFF6A4A10.toInt(), 0.3f)
            // Le halo du soir, en vrai dégradé : un rond plat posait derrière
            // elle un disque à bord net.
            if (P.allume) {
                val rr = 15f
                p.style = Paint.Style.FILL
                p.color = 0xFF000000.toInt()
                p.alpha = 42
                p.shader = RadialGradient(x, pied - 17f, rr,
                    intArrayOf((0xFF shl 24) or (or and 0xFFFFFF), or and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
                c.drawCircle(x, pied - 17f, rr, p)
                p.shader = null
            }
            // La robe : longue et ample, collée au buste, qui s'évase au bas
            // en traîne derrière elle. Un cône étroit en faisait une quille.
            val robe = chemin("M${x - 3.2f} ${pied}C${x - 2.7f} ${pied - 9f} ${x - 2.6f} ${pied - 15f} ${x - 2.2f} ${pied - 19.5f}" +
                "L${x + 1.2f} ${pied - 20f}C${x + 1.6f} ${pied - 15f} ${x + 2f} ${pied - 9f} ${x + 4.6f} ${pied}Z")
            piece(robe, or, 0.5f)
            c.save()
            c.clipPath(robe)
            forme(chemin("M${x - 3.2f} ${pied}C${x - 2.7f} ${pied - 9f} ${x - 2.6f} ${pied - 15f} ${x - 2.2f} ${pied - 19.5f}" +
                "L${x - 0.7f} ${pied - 19.8f}L${x - 1.4f} ${pied}Z"), clair, 0.55f)
            forme(chemin("M${x + 0.5f} ${pied - 19.7f}C${x + 1.2f} ${pied - 14f} ${x + 1.8f} ${pied - 8f} ${x + 4.6f} ${pied}" +
                "L${x + 2f} ${pied}Z"), ombre, 0.5f)
            for (k in 0 until 7) {
                val fx = x - 2.6f + k * 1.05f
                trait("M$fx ${pied - 18.5f}C${fx - 0.3f} ${pied - 12f} ${fx + 0.7f} ${pied - 5f} ${fx + 1f} ${pied}", pli, 0.2f, 0.7f)
            }
            c.restore()
            // Le buste penché en avant, l'épaule, puis la tête inclinée.
            val buste = chemin("M${x - 2.2f} ${pied - 19.5f}L${x - 2f} ${pied - 24.4f}L${x + 0.9f} ${pied - 24.8f}" +
                "L${x + 1.2f} ${pied - 20f}Z")
            piece(buste, or, 0.4f)
            forme(chemin("M${x - 2.2f} ${pied - 19.5f}L${x - 2f} ${pied - 24.4f}L${x - 0.8f} ${pied - 24.6f}L${x - 1f} ${pied - 19.7f}Z"), clair, 0.5f)
            rect(x - 1.3f, pied - 25.4f, 1.3f, 1.4f, ombre, 0.8f)
            rond(x - 0.7f, pied - 26.2f, 1.5f, or)
            rond(x - 1.2f, pied - 26.6f, 0.9f, clair, 0.5f)
            // Les deux bras tendus en avant et vers le haut, coudes à peine
            // pliés, qui tiennent la couronne à deux mains devant son visage.
            // Elle ne la lève pas au-dessus de sa tête en arche : c'est ce que
            // je dessinais de mémoire, et la photo du propriétaire le corrige.
            for ((d2, ep, col) in listOf(
                Triple(0.5f, 1.2f, ombre), Triple(-0.5f, 1f, or)
            )) {
                ligne(x - 1.4f, pied - 23.8f + d2, x - 3.5f, pied - 25.4f + d2, col, ep)
                ligne(x - 3.5f, pied - 25.4f + d2, x - 4.7f, pied - 27.4f + d2, col, ep * 0.85f)
            }
            ligne(x - 1.4f, pied - 24.3f, x - 3.5f, pied - 25.9f, clair, 0.35f, 0.6f)
            // La couronne de laurier, vue presque de profil : un anneau
            // allongé, et non un trait.
            val cour = Path().apply {
                addOval(x - 7.2f, pied - 30.2f, x - 4.4f, pied - 26.6f, Path.Direction.CW)
                addOval(x - 6.5f, pied - 29.5f, x - 5.1f, pied - 27.3f, Path.Direction.CCW)
            }
            piece(cour, or, 0f)
            forme(chemin("M${x - 7.2f} ${pied - 28.6f}Q${x - 6.9f} ${pied - 30.2f} ${x - 5.8f} ${pied - 30.2f}"), clair, 0.6f)
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
