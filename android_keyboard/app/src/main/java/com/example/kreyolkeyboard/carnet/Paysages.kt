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

    /**
     * L'inclinaison du pont Adolphe : ses horizontales penchent d'autant
     * autour de (50, 30). Le tablier fuit vers la gauche, où la ville est
     * loin ; c'est le côté droit qui est près de nous. La Vie s'en sert
     * pour le tram, qui roulerait à côté du pont avec une autre valeur.
     */
    internal const val PENTE_NEI_BRECK = 0.075f

    /**
     * Le trou de l'abri sous roche de Berdorf : large et bas comme sur la
     * photo du propriétaire, et porté vers la gauche. Au milieu, la plaque
     * du mot l'aurait masqué tout entier et la carte ouverte n'aurait
     * montré qu'une masse sombre ; décalé, il donne sa clarté verte à la
     * colonne de gauche, et la colonne de droite garde le gros pilier.
     */
    private const val TROU_BAERDREF =
        "M-2 40C-2 29.6 4 22.6 14 18.6C26 13.8 38 12.4 48 13.8C58 15.2 64.4 19 67.6 24.4" +
        "C69.8 28.2 70.4 32.6 69.6 37.4C58 42.6 24 43.8 -2 40Z"

    /**
     * Les blocs du pilier de Berdorf, du haut vers le bas. Anguleux et
     * jamais d'équerre : taillés au rectangle, ils faisaient des panneaux
     * de béton au lieu d'un rocher débité.
     */
    private val BLOCS_BAERDREF = arrayOf(
        "M67.6 43.4L68.8 26.2L74 27.4L77.6 30.2L76.2 44.6Z",
        "M76.2 44.6L77.6 30.2L82 28.8L87.4 31.6L86.6 46.2Z",
        "M86.6 46.2L87.4 31.6L92 30L97 31.4L96.8 47.4Z",
        "M96.8 47.4L97 31.4L101 29.8L104 30.6L104 48.4Z",
        "M66.6 58.7L67.6 43.4L72 44.6L79 45.2L78.8 58.7Z",
        "M78.8 58.7L79 45.2L84 47L90.8 46.4L90.6 58.7Z",
        "M90.6 58.7L90.8 46.4L96 48.2L104 47.6L104 58.7Z")

    /** Les diaclases qui séparent ces blocs : des cassures, pas des joints de mur. */
    private val JOINTS_BAERDREF = arrayOf(
        "M68.8 26.2L74 27.4L77.6 30.2L82 28.8L87.4 31.6L92 30L97 31.4L101 29.8L104 30.6",
        "M67.6 43.4L72 44.6L79 45.2L84 47L90.8 46.4L96 48.2L104 47.6",
        "M77.6 30.2L76.2 44.6", "M87.4 31.6L86.6 46.2", "M97 31.4L96.8 47.4",
        "M79 45.2L78.8 58.7", "M90.8 46.4L90.6 58.7")

    /** Les valeurs de ces blocs, du plus clair au plus sombre : −1 assombrit, +1 éclaircit. */
    private val VALEURS_BAERDREF = floatArrayOf(-0.9f, 0.5f, -0.3f, 0.9f, 0.2f, -0.7f, 0.6f)

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

    /**
     * La ville de Vianden, au pied du château : des toits serrés en bas à
     * gauche, pris de haut comme sur la photo, qui fuient vers la droite.
     * Le château, lui, n'est plus une rangée de maisons de [rue3q] : il est
     * dessiné à la main sur ses trois lignes de fuite, parce que c'est le
     * sujet du lieu et qu'il lui faut ses tours rondes et sa flèche.
     */
    internal val RUE_VEIANEN = Fuite(floatArrayOf(-6f, 62f), floatArrayOf(74f, 48f), floatArrayOf(-150f, 58f), 150f, taille = 0.5f)
    private val MAISONS_VEIANEN = maisonsAuHasard(34, 171, 14f, 20f)

    /** Le château d'Ansembourg : l'aile ocre, le corps central, l'aile blanche, et ses deux tours. */
    internal val FUITE_ANSEBUERG = Fuite(floatArrayOf(22f, 32f), floatArrayOf(190f, 23f), floatArrayOf(-100f, 28f), 165f)
    private val CHATEAU_ANSEBUERG = arrayOf(
        floatArrayOf(22f, 10f, 0f, 0f, 3.4f, 0f), floatArrayOf(34f, 16f, 0f, 0f, 4f, 1f), floatArrayOf(30f, 14f, 1f, 0f, 3.4f, 1f)
    )
    private val TOUR_ANSEBUERG = floatArrayOf(7f, 22f, 0f, 0f, 8f, 0f)

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
    /**
     * Où le dessin de face de la passerelle du Dumongshaff (100 × 30) est
     * posé : haut gauche, haut droit, bas droit, bas gauche.
     *
     * Le bout **proche est à droite**, comme sur la photo, prise de la rive
     * droite : ce côté-là est donc le plus haut du quadrilatère, et l'autre
     * s'écrase en touchant la berge d'en face.
     */
    private val PASSERELLE_DUMONGSHAFF = floatArrayOf(2f, 30f, 100f, 15f, 100f, 51f, 2f, 45f)

    /**
     * Le bord du grand surplomb de tuf du Kallektuffquell à l'abscisse [x].
     * Écrit ici parce que la [Vie] y accroche ses gouttes : elles tombent du
     * dessous de la pierre, et non du vide devant elle.
     */
    internal fun levreTuf(x: Float) = 30f - (x - 26f) * 0.21f + 1.6f * sin(x * 0.09f)

    /** Le dessous du surplomb, d'où la source goutte. */
    internal fun souslevreTuf(x: Float) = levreTuf(x) + 3.4f

    /** Les abscisses d'où la source goutte, le long du surplomb. */
    private val GOUTTES_TUF = floatArrayOf(34f, 46f, 58f, 70f, 82f, 94f)

    /** La rue du Grund, au bord de l'Alzette : elle fuit vers la droite. */
    internal val RUE_GRUND = Fuite(floatArrayOf(13f, 50.5f), floatArrayOf(178f, 34f), floatArrayOf(-140f, 40f), 150f, quai = 3f)

    /**
     * Les deux rangées du Breedewee, qui montent de part et d'autre de
     * l'escalier et fuient vers le débouché de la ruelle. Les façades y
     * étaient de face, à plat : une maison ne se présente pas de face
     * (propriétaire, 2026-11-16).
     */
    internal val RUE_BREEDEWEE = Fuite(floatArrayOf(80f, 57f), floatArrayOf(52f, 33.6f), floatArrayOf(400f, 36f), 24f)

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
        floatArrayOf(15f, 50f, 2f, 0f, 11f, 1f), floatArrayOf(13f, 44f, 1f, 0f, 9.5f, 0f),
        floatArrayOf(14f, 48f, 0f, 0f, 10.5f, 1f), floatArrayOf(13f, 43f, 3f, 0f, 9.5f, 0f),
        floatArrayOf(13f, 46f, 4f, 0f, 10f, 1f))

    /** La pente du tablier de la Rout Bréck, vu d'en bas. */
    private const val TABLIER_PENTE = 0.17f

    /** Les moutons du premier plan de Conzefenn, en x. */
    private val MOUTONS = floatArrayOf(6f, 13f, 88f, 95f)

    /**
     * La taille de ces quatre-là. Elle est écrite ici parce que la [Vie] les
     * redessine à chaque trame quand la carte est ouverte : les deux tracés
     * doivent donner la même bête, sinon le troupeau change de taille dès
     * qu'on prend la carte en main.
     */
    internal const val MOUTON_PROCHE = 1.45f

    /**
     * Les mâts des bannières plantés sur la place des Hauts-Fourneaux, en x
     * (photo du propriétaire). Deux à chaque bord : la plaque du mot couvre
     * la boîte centrale, et une rangée régulière n'aurait battu au vent que
     * derrière elle.
     */
    private val BANNIERES = floatArrayOf(2f, 22f, 36f, 52f, 88f, 96f)

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
     * | Esch-Sauer | des reflets sur le lac | des ronds sur l'eau |
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
                // Depuis que le lieu suit la photo du lac, c'est l'eau qui
                // vit : les reflets jouent sur toute sa largeur, et les
                // oiseaux partent du buisson du bord gauche.
                Lieu.ESCH_SAUER -> if (calque == 3) {
                    reflets(p, temps, 0f, 100f, 32f, 54f, raf)
                    ronds(p, vol, 40f, 20f, 52f, 86f)
                } else envol(p, vol, 6f, 34f, 1f)
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
                // Le tram passe sur le tablier, dans l'inclinaison du pont :
                // avec une autre pente il roule à côté de l'ouvrage.
                Lieu.NEI_BRECK -> if (calque == 3) p.enPerspective(50f, 30f, PENTE_NEI_BRECK) {
                    val x = ((4f + temps * 5f + elan(temps) * 30f + 20f) % 140f) - 20f
                    p.tram(x)
                    sonnerie(p, x + 15f, 9.6f, raf)
                } else envol(p, vol, 8f, 26f, 1f)
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
                // Les deux lanternes sont celles du décor (plan 1 sous l'arc, plan 2 sur
                // le mur de droite) : leurs places doivent suivre quand il est redessiné.
                Lieu.BREEDEWEE -> if (calque == 3) lanterneVivante(p, temps, 44f, 16f, true, 0)
                    else { lanterneVivante(p, temps, 92f, 24f, false, 1); chat(p, temps, vol) }
                Lieu.FELSMILLEN -> { feuillesQuiTombent(p, temps, raf); envol(p, vol, 12f, 30f, 1f) }
                // Le lac est à gauche depuis que le lieu suit la photo, et le
                // coin haut droit est pris par l'arbre : les reflets, les
                // ronds et les oiseaux ont suivi.
                Lieu.OURDALL -> if (calque == 3) {
                    reflets(p, temps, 0f, 60f, 36f, 52f, raf)
                    ronds(p, vol, 44f, 10f, 28f, 46f)
                } else envol(p, vol, 22f, 9f, 1f)
                // Depuis que le lieu suit les photos, l'eau d'aval est une
                // bande au pied du barrage et le ciel une échancrure : les
                // reflets s'y couchent, et les oiseaux montent par la trouée.
                Lieu.STAUSEI -> {
                    reflets(p, temps, 0f, 100f, 50f, 57f, raf)
                    ronds(p, vol, 54f, 20f, 46f, 70f)
                    envol(p, vol, 44f, 10f, 1f)
                }
                Lieu.CONZEFENN -> for ((k, mx) in MOUTONS.withIndex()) {
                    val leve = raf > 0.1f
                    val saut = raf * 1.2f * abs(sin(temps * 12f + k))
                    p.mouton(mx, 47f + (k % 2) * 3f - saut, MOUTON_PROCHE,
                        if (leve) 0f else 0.5f + 0.5f * sin(temps * 0.8f + k * 1.7f), k % 3 != 0)
                }
                // L'étang rend tout : les reflets courent sur toute sa
                // largeur, les ronds partent du bord des roseaux, et la
                // secousse lève les oiseaux de la réserve.
                Lieu.HAFF_REIMECH -> if (calque == 3) {
                    reflets(p, temps, 0f, 100f, 33f, 55f, raf)
                    ronds(p, vol, 44f, 20f, 56f, 82f)
                } else envol(p, vol, 12f, 22f, 1f)
                Lieu.HOHLLAY -> {
                    // Une secousse fait tomber une pluie de gouttes de la voûte, et
                    // sortir les chauves-souris de la grotte.
                    // Les gouttes partent de la lèvre de la voûte, qui est
                    // descendue avec elle : plus haut, elles tombaient du
                    // vide au-dessus de la grotte.
                    goutte(p, temps, 50f, 23f, 52f); goutte(p, temps + 1.3f, 70f, 26f, 53f)
                    for (k in 0 until (raf * 8f).toInt()) goutte(p, temps * 1.7f + k * 0.37f, 30f + (k * 23 % 46), 24f, 53f)
                    chauvesSouris(p, vol, 50f, 34f)
                }
                Lieu.NAPOLEONSGAART -> { balise(p, temps, raf); envol(p, vol, 100f, 44f, -1f) }
                // La source goutte de chaque lèvre du surplomb, et une
                // secousse la fait déborder : les gouttes se pressent et se
                // multiplient. Elles partent du bord de la pierre
                // ([levreTuf]) et tombent dans le bassin.
                Lieu.KALLEKTUFFQUELL -> {
                    val n = 6 + (raf * 10f).toInt()
                    for (k in 0 until n) {
                        val gx = GOUTTES_TUF[k % GOUTTES_TUF.size] + (k / GOUTTES_TUF.size) * 5f
                        goutte(p, temps * (1f + raf * 1.5f) + k * 0.55f,
                            gx, souslevreTuf(gx), 47f, rond = true)
                    }
                    ronds(p, vol, 50f, 30f, 60f, 88f)
                }
                // Plus de tram depuis que le lieu suit la photo : le tram
                // passe sur le pont Adolphe et sur la Rout Bréck, jamais sur
                // la Passerelle. Ce sont les choucas de la vallée qui
                // traversent les arches.
                Lieu.PEITRUSDALL -> if (calque == 3) envol(p, vol, 20f, 22f, 1f)
                    else envol(p, vol, 70f, 20f, -1f)
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
                // D'un bord à l'autre : groupées au milieu, elles ne
                // tombaient que derrière la plaque du mot.
                val x = 2f + (k * 37 % 94) + sin(temps * 1.5f + k) * 2f
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
                Lieu.NEI_BRECK -> Surcadre(Essence.FEUILLU, DROITE, Pied.FOUGERES, DEUX)
                Lieu.PEITRUSDALL -> Surcadre(Essence.FEUILLU, DEUX, Pied.HERBES, GAUCHE, portee = 24f)
                Lieu.PAFENDALL -> Surcadre(Essence.FEUILLU, DROITE, Pied.FOUGERES, DROITE, portee = 26f)
                // La branche passe à gauche et les fougères à droite : au
                // milieu et à droite, le feuillage mangeait le château, qui
                // est tout le lieu.
                Lieu.VEIANEN -> Surcadre(Essence.FEUILLU, GAUCHE, Pied.FOUGERES, DROITE, portee = 20f)
                Lieu.OURDALL -> Surcadre(Essence.SAULE, GAUCHE, Pied.ROSEAUX, GAUCHE)
                // Les coulisses restent à droite, où il n'y a que de l'eau et
                // de la forêt : à gauche elles couvraient la grève, qui est
                // la moitié du lieu. Le saule se retient ([portee]) pour
                // laisser voir la tonnelle du ponton.
                Lieu.ESCH_SAUER -> Surcadre(Essence.SAULE, DROITE, Pied.ROSEAUX, DROITE, portee = 23f)
                Lieu.STAD -> Surcadre(Essence.SAULE, GAUCHE, Pied.ROSEAUX, DROITE)
                Lieu.HAFF_REIMECH -> Surcadre(Essence.SAULE, GAUCHE, Pied.ROSEAUX, DEUX)
                Lieu.DUMONGSHAFF -> Surcadre(Essence.SAULE, DROITE, Pied.ROSEAUX, DEUX)
                Lieu.STAUSEI -> Surcadre(Essence.FEUILLU, GAUCHE, Pied.ROSEAUX, GAUCHE)
                Lieu.MUSEL -> Surcadre(Essence.VIGNE, DROITE, null, 0, portee = 32f)
                Lieu.GELLE_FRA -> Surcadre(Essence.FEUILLU, GAUCHE, Pied.FLEURS, GAUCHE, portee = 28f)
                // Les deux fourneaux tiennent les deux bords : pas de branche.
                Lieu.BELVAL -> Surcadre(null, 0, Pied.HERBES, DEUX)
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
     * la rive de la Moselle).
     */
    private fun aUnPlancher(lieu: Lieu, plan: Int) =
        plan == 2 || (plan == 1 && (lieu == Lieu.BREEDEWEE || lieu == Lieu.MUSEL))

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
         * Un chat qui court sur le pavé, les pattes en ([x], [y]), vers la
         * droite ou vers la gauche ; [foulee] fait tourner ses pattes, [galop]
         * l'allonge et le fait bondir.
         *
         * Il est **d'une seule découpe** : pattes, tronc, croupe, poitrail,
         * tête, oreilles et queue réunis en une silhouette, et non une pile
         * d'ovales et de traits. C'est elle qui fait le chat de nuit, où il
         * ne reste plus que le noir du papier et l'œil allumé ; de jour, la
         * même découpe est rousse et porte ses rayures.
         */
        fun chat(x: Float, y: Float, versDroite: Boolean, foulee: Float, galop: Float) {
            val roux = duJour(0xFFD9822B.toInt())
            val robe = if (P.nuit) mix(0xFF0A0912.toInt(), P.pres, 0.14f) else roux
            val fonce = sombre(robe, 0.35f)
            val clair = duJour(0xFFF3D9B0.toInt())
            c.save()
            if (!versDroite) c.scale(-1f, 1f, x, y)
            val bond = abs(sin(foulee)) * (0.2f + 0.6f * galop)
            val yy = y - bond
            val etire = 1f + 0.14f * galop
            val hx = x + 3.3f * etire
            val hy = yy - 4.9f
            val sil = Path()
            fun masse(cx: Float, cy: Float, rx: Float, ry: Float) {
                sil.op(Path().apply { addOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), Path.Direction.CW) },
                    Path.Op.UNION)
            }
            // Les pattes : deux paires qui se croisent, plus écartées au galop,
            // fuselées de l'épaule au coussinet.
            val ecart = 0.5f + 0.6f * galop
            for ((dx, ph) in listOf(-2.1f to 0f, -1f to PI.toFloat(), 1.3f to PI.toFloat() * 0.5f, 2.1f to PI.toFloat() * 1.5f)) {
                val px = x + dx * etire
                val fx = px + sin(foulee + ph) * ecart * 1.4f
                sil.op(Path().apply {
                    moveTo(px - 0.4f, yy - 3.6f)
                    lineTo(px + 0.4f, yy - 3.6f)
                    lineTo(fx + 0.28f, y)
                    lineTo(fx - 0.28f, y)
                    close()
                }, Path.Op.UNION)
            }
            // Le tronc, la croupe ronde, le poitrail, la tête.
            masse(x, yy - 3.6f, 2.9f * etire, 1f)
            masse(x - 1.9f * etire, yy - 3.7f, 1.35f, 1.35f)
            masse(x + 1.9f * etire, yy - 3.8f, 1.2f, 1.2f)
            masse(hx, hy, 1.1f, 0.95f)
            // Les deux oreilles pointues.
            sil.op(chemin("M${hx - 1f} ${hy - 0.4f}L${hx - 0.5f} ${hy - 2.1f}L${hx + 0.15f} ${hy - 0.8f}Z"), Path.Op.UNION)
            sil.op(chemin("M${hx + 0.15f} ${hy - 0.9f}L${hx + 0.9f} ${hy - 2f}L${hx + 1.05f} ${hy - 0.3f}Z"), Path.Op.UNION)
            // La queue dressée, qui ondule et s'affine jusqu'au bout.
            val ond = sin(foulee * 0.5f) * 0.6f
            sil.op(ruban(x - 2.4f * etire, yy - 4f, x - 5.4f * etire, yy - 5.2f + ond,
                x - 3.8f * etire, yy - 8f + ond * 0.4f, 1.15f, 0.4f), Path.Op.UNION)
            piece(sil, robe)
            if (P.nuit) {
                // La nuit, rien que la découpe noire et l'œil qui luit.
                rond(hx + 0.2f, hy - 0.2f, 0.44f, 0xFFE8F070.toInt(), 0.26f)
                rond(hx + 0.2f, hy - 0.2f, 0.17f, 0xFFE8F070.toInt())
            } else {
                ligne(x - 0.6f, yy - 4.4f, x - 0.3f, yy - 3.3f, fonce, 0.35f)
                ligne(x + 0.6f, yy - 4.5f, x + 0.9f, yy - 3.4f, fonce, 0.35f)
                rond(hx + 0.7f, hy + 0.3f, 0.45f, clair)
                rond(hx + 0.2f, hy - 0.2f, 0.16f, 0xFF1E2A10.toInt())
            }
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
                     graine: Int = 0, pente: Float = 0f, lichen: Float = 0.2f, ombre: Float = 1f) {
            if (!actif) return
            // [ombre] sous 0,95 enlève la tranche de kraft : sur une pièce
            // percée de dix arcades, elle cerclait chaque trou d'un jonc
            // orange et le mur devenait une planche découpée à l'emporte-pièce.
            piece(contour, sombre(col, 0.5f), ombre)
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
         * Un pan de trois quarts qu'on peint à plat dans une boîte de
         * 100 × 100, comme [surQuad], mais **découpé** à son quadrilatère
         * [q] (haut gauche, haut droit, bas droit, bas gauche).
         *
         * Une matière déborde toujours un peu sa boîte — un rang d'ardoises
         * commence avant le bord, une lame dépasse — et [surQuad] ne borne
         * rien. Sur un trapèze effilé, une flèche de clocher par exemple, ce
         * débord part à l'infini de la projection et noie tout le bord de la
         * carte : c'est arrivé au clocher de Vianden le 2026-11-21.
         */
        fun surPan(q: FloatArray, bloc: () -> Unit) {
            if (!actif) return
            val pan = quad(q)
            c.save(); c.clipPath(pan)
            releve?.let { it.save(); it.clipPath(pan) }
            surQuad(100f, 100f, q) { bloc() }
            releve?.restore(); c.restore()
        }

        /**
         * L'ardoise d'une couverture, dans le quadrilatère [q] du pan (faîte
         * à gauche, faîte à droite, égout à droite, égout à gauche) :
         * [rangs] rangs à recouvrement de [par] ardoises, décalés d'un demi.
         *
         * Le ton change surtout d'un **rang** à l'autre et à peine d'une
         * ardoise à sa voisine : tiré par ardoise, l'écart donne un mur de
         * moellons posé sur le toit. Et chaque rang porte dessous l'ombre de
         * son recouvrement, qui est ce qui fait l'ardoise bien plus que la
         * découpe. Peints à plat puis mis en perspective, si bien que les
         * rangs se serrent d'eux-mêmes vers le faîte.
         */
        fun ardoises(q: FloatArray, col: Int, graine: Int, rangs: Int, par: Int) {
            val rr = Alea(graine)
            piece(quad(q), col, 0.5f)
            surPan(q) {
                val hr = 100f / rangs
                var y = 0f
                var k = 0
                while (y < 100f) {
                    val tr = rr()
                    val rang = if (tr < 0.5f) sombre(col, 0.03f + tr * 0.14f)
                        else mix(col, 0xFFFFFFFF.toInt(), 0.02f + (tr - 0.5f) * 0.12f)
                    val l = 100f / par
                    var x = if (k % 2 == 0) -l * 0.5f else 0f
                    while (x < 100f) {
                        val t = rr()
                        rect(x, y, l * 0.92f, hr * 1.4f,
                            if (t < 0.34f) sombre(rang, 0.05f) else if (t < 0.68f) mix(rang, 0xFFFFFFFF.toInt(), 0.035f) else rang)
                        x += l
                    }
                    rect(0f, y + hr * 0.82f, 100f, hr * 0.22f, sombre(col, 0.3f), 0.5f)
                    y += hr
                    k++
                }
            }
        }

        /**
         * Le platelage d'un pont ou d'un ponton, dans le quadrilatère [q] :
         * des lames de bois dans le sens de la longueur, chacune de son ton,
         * sur un fond de joint sombre peint d'abord. (Le [lames] du plancher
         * de théâtre, plus bas, est une autre chose : celui-là est une
         * matière de trois quarts.)
         */
        fun platelage(q: FloatArray, col: Int, graine: Int, n: Int) {
            val rr = Alea(graine)
            piece(quad(q), sombre(col, 0.5f), 0.5f)
            surPan(q) {
                var y = 0f
                while (y < 100f) {
                    val h = (100f / n) * (0.75f + rr() * 0.5f)
                    val t = rr()
                    rect(-2f, y, 104f, h - 100f / n * 0.16f,
                        if (t < 0.42f) sombre(col, 0.06f + t * 0.3f) else mix(col, 0xFFFFFFFF.toInt(), 0.03f + t * 0.16f))
                    y += h
                }
            }
        }

        /**
         * La couleur d'un bloc, tirée au sort : pierre nue, humide ou
         * verdie. Deux pierres voisines du même ton se fondent l'une dans
         * l'autre et l'appareil disparaît.
         *
         * Écrite pour la porte du Breedewee, remontée ici le 2026-11-16
         * quand le pont Adolphe en a eu besoin à son tour.
         */
        fun tonPierre(col: Int, k: Float): Int = when {
            k < 0.3f -> sombre(col, 0.1f + k * 0.35f)
            k < 0.56f -> mix(col, 0xFFFFFFFF.toInt(), 0.06f + k * 0.16f)
            k < 0.78f -> col
            else -> sombre(mix(col, neutre(duJour(0xFF7E8A62.toInt())), 0.28f + k * 0.2f), 0.12f)
        }

        /**
         * Le champ rugueux d'un bossage, dans le rectangle donné : les
         * piqûres de la pointerolle, quelques éclats, et les deux arêtes
         * que la lumière prend. C'est cette rugosité qui fait la pierre ;
         * un aplat bordé de deux traits fait du carton peint.
         */
        fun champBosse(x: Float, y: Float, w: Float, h: Float, col: Int, graine: Int) {
            val rr = Alea(graine)
            // Les deux faces du relief : haut et gauche au jour, bas et
            // droite dans l'ombre du chanfrein.
            rect(x, y, w, h * 0.2f, mix(col, 0xFFFFFFFF.toInt(), 0.26f))
            rect(x, y, w * 0.06f, h, mix(col, 0xFFFFFFFF.toInt(), 0.18f))
            rect(x, y + h * 0.8f, w, h * 0.2f, sombre(col, 0.3f))
            rect(x + w * 0.94f, y, w * 0.06f, h, sombre(col, 0.22f))
            // Les piqûres : des creux et des reliefs, en lignes obliques
            // comme les laisse l'outil.
            val n = (w * h * 0.6f).toInt().coerceIn(6, 34)
            for (i in 0 until n) {
                val px = x + 0.1f + rr() * (w - 0.2f)
                val py = y + 0.1f + rr() * (h - 0.2f)
                val t = rr()
                rect(px, py, 0.16f + rr() * 0.3f, 0.12f + rr() * 0.2f,
                    if (t < 0.5f) sombre(col, 0.18f + t * 0.3f) else mix(col, 0xFFFFFFFF.toInt(), 0.1f + t * 0.2f),
                    0.75f)
            }
            // Un éclat, de temps en temps : un coin mangé par le temps.
            if (rr() < 0.3f) {
                val ex = if (rr() < 0.5f) x else x + w - 1.2f
                forme("M$ex ${y + h}L${ex + 1.2f} ${y + h}L$ex ${y + h - 1f}Z", sombre(col, 0.38f), 0.8f)
            }
        }

        /** Un bloc en bossage : son joint en creux, sa ciselure lisse, son champ rugueux. */
        fun bossage(x: Float, y: Float, w: Float, h: Float, col: Int, k: Float) {
            rect(x, y, w, h, sombre(col, 0.5f))
            val c0 = tonPierre(col, k)
            // La ciselure : la bordure plate que le tailleur laisse
            // autour du bossage, et qui dessine chaque pierre.
            rect(x + 0.22f, y + 0.2f, w - 0.44f, h - 0.4f, mix(c0, 0xFFFFFFFF.toInt(), 0.14f))
            val m = 0.42f
            champBosse(x + 0.22f + m, y + 0.2f + m, w - 0.44f - 2f * m, h - 0.4f - 2f * m,
                c0, (x * 7f + y * 13f).toInt() + 500)
        }

        /**
         * **Les claveaux d'un arc** : l'anneau compris entre l'ellipse de
         * rayons ([rx], [ry]) centrée en ([cx], [cy]) et la même grossie de
         * [ep], de l'angle [a0] à [a1] (0 à droite, π à gauche, comptés
         * au-dessus du centre), débité en pierres par des joints **radiaux**.
         *
         * Un arc ne se taille pas comme un mur : ses pierres rayonnent
         * depuis le centre. Rempli d'un [appareil] à assises horizontales,
         * il fait un mur percé d'un trou ; peint d'une bande unie, un tuyau
         * de caoutchouc.
         */
        fun voussoirs(cx: Float, cy: Float, rx: Float, ry: Float, ep: Float,
                      a0: Float, a1: Float, col: Int, graine: Int, pas: Float = 0.11f) = mineral {
            val r = Alea(graine + 90)
            fun px(a: Float, g: Float) = cx + (rx + g) * cos(a)
            fun py(a: Float, g: Float) = cy - (ry + g) * sin(a)
            // Le joint en creux d'abord, sous les claveaux : peint après,
            // l'écart entre deux pierres montrerait leur propre couleur.
            val anneau = Path()
            var a = a0
            anneau.moveTo(px(a0, 0f), py(a0, 0f))
            while (a < a1) { a = min(a1, a + 0.05f); anneau.lineTo(px(a, 0f), py(a, 0f)) }
            anneau.lineTo(px(a1, ep), py(a1, ep))
            while (a > a0) { a = max(a0, a - 0.05f); anneau.lineTo(px(a, ep), py(a, ep)) }
            anneau.close()
            forme(anneau, sombre(col, 0.5f))
            val j = 0.006f
            a = a0
            while (a < a1) {
                val b = min(a1, a + pas * (0.82f + r() * 0.36f))
                if (b - a > 3f * j) {
                    val t = Path()
                    t.moveTo(px(a + j, 0.12f), py(a + j, 0.12f))
                    t.lineTo(px(b - j, 0.12f), py(b - j, 0.12f))
                    t.lineTo(px(b - j, ep - 0.12f), py(b - j, ep - 0.12f))
                    t.lineTo(px(a + j, ep - 0.12f), py(a + j, ep - 0.12f))
                    t.close()
                    forme(t, tonPierre(col, r()))
                }
                a = b
            }
        }

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

        /**
         * Ce que dit la photo du propriétaire (2026-11-21) :
         *
         * 1. **Le château tient le haut du bord droit**, et rien d'autre ne
         *    lui dispute la bande du ciel : sa longue toiture d'ardoise, ses
         *    tours rondes à coiffe conique du côté proche, la flèche de la
         *    chapelle à l'autre bout, ses murs pâles percés de petites
         *    fenêtres. Il était au milieu de la fenêtre, c'est-à-dire sous le
         *    dessin du mot, et on n'en voyait rien sur la carte ouverte.
         * 2. **La brume traverse la vallée à mi-hauteur**, en bancs : c'est
         *    elle qui détache l'éperon de la forêt et qui donne sa profondeur
         *    au lieu. Sans elle, trois versants verts l'un derrière l'autre.
         * 3. **La ville est en bas à gauche**, petits toits serrés, et le
         *    clocher des Trinitaires se dresse au-dessus d'elle sur son
         *    rocher : une aiguille grise, bien plus fine que le château, qui
         *    tient la colonne de gauche comme le château tient celle de
         *    droite.
         * 4. **La forêt est d'automne**, avec des sapins sombres plantés
         *    dedans. Le vert de base reste vert : c'est la saison qui le
         *    rouille, et un roux peint d'avance serait roux en avril.
         */
        fun veianen() {
            val r = Alea(103)
            // Les trois lignes du château, qui fuient vers la gauche : le
            // faîte, la ligne d'égout et la terrasse. Elles sont ici, en
            // tête, parce que l'éperon et le rocher se peignent avant lui et
            // doivent venir se ranger dessous.
            fun cx(u: Float) = 72f + u * 27f
            fun faite(u: Float) = 7.6f - u * 2f
            fun egout(u: Float) = 12.2f - u * 0.8f
            fun terrasse(u: Float) = 19.4f + u * 2.2f
            /** La terrasse, à l'abscisse [x] de la fenêtre plutôt qu'en [u]. */
            fun terrX(x: Float) = terrasse((x - 72f) / 27f)
            // ---- Le fond : deux crêtes pâles, et le banc de nuages bas qui
            // traîne sur l'horizon à gauche, d'où vient le soleil.
            lisiere(-3f, 103f, { x -> 22.4f - 2.4f * sin(x * 0.05f + 1.2f) }, 1.2f, 1.9f, 104,
                mix(P.loin, P.cielBas, 0.32f))
            pieceD("M-3 25C8 23.4 20 22.8 32 23.6C40 24.2 46 25.2 52 26.2L52 29L-3 29Z",
                mix(0xFFFFFFFF.toInt(), P.cielBas, if (P.nuit) 0.55f else 0.34f), 0.3f)
            lisiere(-3f, 103f, { x -> 26.6f + 2f * sin(x * 0.085f) }, 1.4f, 2.2f, 105,
                nappe(mix(P.loin, P.milieu, 0.55f), 0.34f, VERT_CALME))
            plan(1)
            // ---- Les versants. Le grand bois du milieu ferme la vallée,
            // l'éperon du château monte à droite, et un troisième rideau,
            // plus sombre, descend vers la ville.
            val bois = nappe(duJour(0xFF3F6A2E.toInt()), 0.48f, VERT_CALME)
            lisiere(-3f, 86f, { x -> 29.6f + 0.0016f * (x - 38f) * (x - 38f) }, 2.2f, 3.4f, 106,
                mix(bois, P.loin, 0.3f))
            // La crête de l'éperon suit le pied de la muraille, un demi au
            // -dessous, puis tombe vers la ville à gauche. Dessinée en
            // parabole, elle passait sous le soubassement entre x = 70 et
            // 85 : la bande pâle du lointain se glissait dans l'écart et le
            // château flottait au-dessus de l'herbe (propriétaire,
            // 2026-11-21).
            fun eperon(x: Float) = if (x >= 68f) 22.5f + (x - 68f) * 0.13f else 22.5f + (68f - x) * 0.26f
            lisiere(30f, 104f, { x -> eperon(x) }, 1.6f, 2.6f, 107, bois)
            // ---- L'éperon de schiste sur lequel le château est debout.
            //
            // C'est une **masse**, pas un bandeau. Un muret d'épaisseur
            // constante posé en travers de la pente ne porte rien et ne
            // repose sur rien : il flotte, quoi qu'on mette dessous
            // (propriétaire, 2026-11-21). Ici le rocher monte du fond de la
            // vallée jusqu'à la terrasse, son flanc gauche plonge vers la
            // ville, et c'est le rideau de forêt du devant, peint juste
            // après, qui vient laper son pied : on ne voit donc de lui que
            // ce qui dépasse des arbres, comme sur la photo.
            // Il affleure sur sept ou huit unités seulement, puis les
            // arbres le prennent : un piton de pierre qui descend jusqu'au
            // bas de la fenêtre fait une pyramide, et la photo montre une
            // colline boisée d'où la roche sort par plaques.
            val schiste = neutre(duJour(0xFF8C8275.toInt()))
            val rs = Alea(319)
            val rocher = Path()
            rocher.moveTo(63.5f, 28.4f)
            rocher.lineTo(70.5f, terrX(70.5f) - 0.4f)
            rocher.lineTo(104f, terrX(104f) - 0.4f)
            rocher.lineTo(104f, terrX(104f) + 7.5f)
            var rx = 104f
            while (rx > 64f) {
                rx = max(64f, rx - 3f - rs() * 4f)
                rocher.lineTo(rx, if (rx > 70f) terrX(rx) + 5.6f + rs() * 3.2f
                    else 30.6f + (70f - rx) * 0.5f + rs() * 1.8f)
            }
            rocher.close()
            appareil(rocher, schiste, rang = 1.45f, longueur = 3.1f, graine = 311,
                pente = 0.05f, lichen = 0.2f, ombre = 0.5f)
            lisiere(-3f, 104f, { x -> 40f + 0.0018f * (104f - x) * (104f - x) }, 2.6f, 4.2f, 108,
                sombre(bois, 0.24f))
            // Quelques sapins plantés dans la pente, qui disent l'échelle du
            // château mieux que la forêt elle-même.
            for (k in 0 until 14) {
                val sx = 24f + r() * 78f
                val sy = eperon(sx).coerceAtMost(44f) + 2.4f + r() * 7f
                val sh = 3f + r() * 3.4f
                pieceD("M${sx - sh * 0.36f} ${sy}L$sx ${sy - sh}L${sx + sh * 0.36f} ${sy}Z",
                    sombre(bois, 0.3f + r() * 0.18f), 0.4f)
            }
            // ---- Le château. Tout est accroché à trois lignes qui fuient
            // vers la gauche : le faîte, la ligne d'égout et la terrasse. Le
            // bout proche, à droite, est le plus grand : c'est celui qu'on
            // voit en entier à côté du dessin du mot, et la toiture court sur
            // la bande du haut, au-dessus de lui.
            // Le château est du bâti : il passe par [neutre] (règle 10).
            // Sans cela le ravivage des palettes vives le sortait en or,
            // alors que c'est une bâtisse de schiste crépie de gris clair.
            //
            // Et il a sa matière, comme tout immeuble du carnet (skill
            // `matieres-du-decor`, règle 1 : une matière est une
            // organisation, pas une couleur). Ici trois appareils, tous
            // relevés sur la photo :
            //
            // - **l'ardoise** des toitures, en rangs à recouvrement décalés
            //   d'un demi, de tons voisins. C'est la plus grande surface du
            //   château et c'était un aplat gris ;
            // - **l'enduit** des murs, avec son socle, ses chaînes d'angle,
            //   son bandeau sous la corniche et l'encadrement de pierre de
            //   chaque fenêtre, peints à plat puis posés dans le
            //   quadrilatère de la façade ([surQuad]) ;
            // - **le moellon de schiste** du soubassement, en assises
            //   minces et inégales ([appareil]).
            //
            // Le soleil vient de la gauche, comme sur la photo : toute arête
            // gauche est au jour, tout flanc droit à l'ombre.
            val creme = duJour(0xFFE4DED2.toInt())
            val mur = eclaire(neutre(creme), 0.16f)
            val toit = neutre(ardoise())
            val creux = neutre(ombreFroide(creme, 0.42f))
            val taille = pierreDeTaille()
            val vitre = if (P.allume) P.fenetre else neutre(duJour(0xFF2B3038.toInt()))
            mineral {
                // Le mur de soutènement de la terrasse, posé sur le rocher
                // de l'éperon : une assise régulière de pierre appareillée,
                // au-dessus de la roche brute. C'est l'ouvrage, le rocher
                // est le terrain, et c'est le rocher qui porte.
                appareil(quad(floatArrayOf(cx(-0.06f), terrasse(-0.06f) - 0.4f, 104f, terrasse(1.2f) - 0.4f,
                    104f, terrasse(1.2f) + 3.2f, cx(-0.06f), terrasse(-0.06f) + 3f)),
                    neutre(duJour(0xFFA8A090.toInt())), rang = 1.05f, longueur = 3.2f, graine = 318,
                    pente = 0.055f, lichen = 0.08f, ombre = 0.5f)
                // L'aile basse et blanche a été retirée (propriétaire,
                // 2026-11-21) : posée sur la pente devant le rocher, elle
                // n'avait pas d'assise non plus, et elle cachait l'éperon.
                // Le pignon du bout proche, qui fuit derrière : c'est lui qui
                // donne au corps son épaisseur.
                piece(quad(floatArrayOf(cx(1f), egout(1f), 104f, egout(1f) - 1.4f,
                    104f, terrasse(1f) - 2.4f, cx(1f), terrasse(1f))), creux, 0.5f)
                ardoises(floatArrayOf(cx(1f) + 1.6f, faite(1f), 102.6f, faite(1f) - 1f,
                    104f, egout(1f) - 1.4f, cx(1f), egout(1f)), sombre(toit, 0.16f), 310, 7, 10)
                // La longue façade, puis sa matière peinte à plat dedans :
                // socle, chaînes d'angle, bandeau de corniche, et les deux
                // rangs de fenêtres dans leur encadrement de pierre. C'est à
                // eux qu'on lit la longueur du bâtiment, et sans eux la
                // façade n'est qu'une planche.
                val facade = floatArrayOf(cx(0f), egout(0f), cx(1f), egout(1f), cx(1f), terrasse(1f), cx(0f), terrasse(0f))
                piece(quad(facade), mur, 0.6f)
                surQuad(100f, 40f, facade) {
                    // Le badigeon : l'enduit n'est pas uniforme, il est lavé.
                    for (k in 0 until 9) {
                        val bx = r() * 100f
                        val by = 4f + r() * 32f
                        ovalePapier(bx, by, 4f + r() * 9f, 2.5f + r() * 5f,
                            if (r() < 0.5f) mix(mur, 0xFFFFFFFF.toInt(), 0.09f) else sombre(mur, 0.07f), 0f)
                    }
                    rect(0f, 0f, 100f, 3.4f, taille, 0.9f)           // le bandeau de corniche
                    rect(0f, 3.4f, 100f, 0.9f, sombre(taille, 0.3f), 0.7f)
                    rect(0f, 33f, 100f, 7f, taille, 0.85f)           // le socle
                    rect(0f, 33f, 100f, 0.7f, mix(taille, 0xFFFFFFFF.toInt(), 0.2f), 0.7f)
                    for (k in 0 until 9) ligne(3f + k * 11.5f, 33.6f, 3f + k * 11.5f, 40f, sombre(taille, 0.32f), 0.4f, 0.6f)
                    // Les chaînes d'angle, en pierre de taille : c'est ce qui
                    // dit qu'un mur est un ouvrage et non un panneau.
                    for ((i, bx) in floatArrayOf(0f, 95f).withIndex()) {
                        var qy = 3.4f
                        var longue = true
                        while (qy < 33f) {
                            val qh = if (longue) 5.2f else 3.4f
                            rect(bx, qy, if (longue) 5f else 3.4f, qh - 0.5f,
                                if (i == 0) mix(taille, 0xFFFFFFFF.toInt(), 0.1f) else sombre(taille, 0.2f), 0.9f)
                            qy += qh
                            longue = !longue
                        }
                    }
                    for (k in 0 until 11) {
                        val fx = 7.5f + k * 8.1f
                        for (fy in floatArrayOf(10f, 22f)) {
                            rect(fx - 0.8f, fy - 0.9f, 5.2f, 9.4f, taille, 0.85f)        // l'encadrement
                            rect(fx, fy, 3.6f, 7.4f, vitre, if (P.allume) 0.95f else 0.85f)
                            rect(fx - 1.2f, fy + 7.8f, 6f, 1.1f, sombre(taille, 0.12f), 0.9f)  // l'appui
                        }
                    }
                }
                ardoises(floatArrayOf(cx(0f) + 1.4f, faite(0f), cx(1f) - 1.6f, faite(1f),
                    cx(1f), egout(1f), cx(0f), egout(0f)), toit, 312, 9, 20)
                // Les deux tours rondes du bout proche, coiffées en poivrière.
                // Elles débordent le faîte : c'est ce qui les fait lire comme
                // des tours et non comme deux pans de mur de plus. Un cylindre
                // n'a que deux valeurs tranchées de droit, la panse et son
                // flanc à l'ombre, plus un filet de lumière étroit à un quart
                // du bord : un large bandeau clair en ferait une planche.
                for ((k, tx) in floatArrayOf(91.8f, 97.8f).withIndex()) {
                    val haut = 12f - k * 1.2f
                    val bas = 25f + k * 1.6f
                    val l = 5.2f + k * 0.8f
                    val g = tx - l * 0.5f
                    feuille(g, haut, l, bas - haut, mur, 0.6f)
                    rect(g + l * 0.13f, haut, l * 0.11f, bas - haut, mix(mur, 0xFFFFFFFF.toInt(), 0.2f), 0.8f)
                    rect(g + l * 0.74f, haut, l * 0.26f, bas - haut, creux, 0.9f)
                    // Le cordon de pierre, et deux baies dans leur encadrement.
                    rect(g, haut + 1.2f, l, 0.9f, taille, 0.85f)
                    for (fy in floatArrayOf(4.2f, 8.2f)) {
                        rect(g + l * 0.26f, haut + fy - 0.4f, l * 0.42f, 2.6f, taille, 0.85f)
                        rect(g + l * 0.34f, haut + fy, l * 0.26f, 1.7f, vitre, 0.9f)
                    }
                    // La poivrière, en écailles d'ardoise.
                    val pointe = haut - 7.6f - k * 1.4f
                    val cone = chemin("M${g - l * 0.16f} ${haut + 0.4f}L$tx ${pointe}L${g + l * 1.16f} ${haut + 0.4f}Z")
                    piece(cone, toit, 0.4f)
                    c.save(); c.clipPath(cone)
                    releve?.let { it.save(); it.clipPath(cone) }
                    val rr = Alea(313 + k)
                    var ey = pointe + 0.6f
                    var i = 0
                    while (ey < haut + 0.6f) {
                        val large = (ey - pointe) / (haut + 0.4f - pointe) * l * 1.3f
                        val pas = 0.62f + (ey - pointe) * 0.035f
                        var ex = tx - large * 0.5f + (if (i % 2 == 0) 0f else pas * 0.5f)
                        while (ex < tx + large * 0.5f) {
                            rect(ex, ey, pas * 0.88f, 0.92f,
                                if (rr() < 0.4f) sombre(toit, 0.12f) else mix(toit, 0xFFFFFFFF.toInt(), 0.07f))
                            ex += pas
                        }
                        ey += 0.62f
                        i++
                    }
                    releve?.restore(); c.restore()
                }
                // La flèche de la chapelle, à l'autre bout : une aiguille, et
                // c'est elle qui dit que le château n'est pas une caserne.
                feuille(73.4f, 6.4f, 2.6f, 6.6f, mur, 0.5f)
                rect(73.4f, 6.4f, 0.5f, 6.6f, mix(mur, 0xFFFFFFFF.toInt(), 0.2f), 0.8f)
                rect(75.3f, 6.4f, 0.7f, 6.6f, creux, 0.9f)
                ardoises(floatArrayOf(74.4f, 1.4f, 75f, 1.4f, 76.8f, 6.8f, 72.6f, 6.8f), toit, 314, 7, 4)
                pieceD("M74.2 1.6L74.7 -1.2L75.2 1.6Z", sombre(toit, 0.2f), 0f)
            }
            // Les arbres au pied des murailles, qui lapent le soubassement :
            // sans eux la roche s'arrête net sur l'herbe, et c'est cette
            // couture-là qu'on lit comme un flottement.
            // Elles commencent après l'aile blanche et passent sous elle :
            // semées sur toute la longueur, elles l'avalaient, et c'est elle
            // qui fait socle au château sur la photo.
            val rt = Alea(318)
            for (k in 0 until 18) {
                val gauche = k % 5 == 0
                val tx = if (gauche) 56f + rt() * 14f else 66f + rt() * 40f
                val ty = if (gauche) 31f + rt() * 4f else terrX(tx) + 7.4f + rt() * 3.4f
                val e = 1.3f + rt() * 1.4f
                touffe(if (rt() < 0.4f) bois else sombre(bois, 0.26f),
                    tx, ty, e, tx + e * 1.4f, ty + e * 0.5f, e * 0.8f, tx - e * 1.3f, ty + e * 0.4f, e * 0.75f)
            }
            // ---- La brume de la vallée, posée après le château : elle
            // mange son pied et c'est ce qui le met sur son éperon.
            if (!vivant) brumeDeVeianen(0f)
            plan(2)
            solNaturel(SolNaturel.TERRE, DEVANT, 0.03f)  // le sentier du belvédère d'en face
            poser(DEVANT)
            // ---- Le rocher et le clocher des Trinitaires, qui tiennent la
            // colonne de gauche. L'aiguille est grise et fine : c'est son
            // élancement qui la distingue du château, pas sa taille.
            val roche = neutre(duJour(0xFF8E8880.toInt()))
            val pierre = neutre(duJour(0xFFCFC8B8.toInt()))
            mineral {
                pieceD("M-3 60L-3 47C4 45.4 11 44.6 17 45.4C21 46 24 48 26 51L28 60Z", roche)
                for (dy in floatArrayOf(2.6f, 6f, 9.8f)) {
                    pieceD("M-3 ${47f + dy}C4 ${45.6f + dy} 11 ${44.8f + dy} 16 ${45.6f + dy}" +
                        "L16 ${46.6f + dy}C11 ${45.8f + dy} 4 ${46.6f + dy} -3 ${48f + dy}Z", sombre(roche, 0.26f), 0f)
                }
                // La tour est du moellon de schiste, comme le soubassement
                // du château : des assises minces et inégales, des chaînes
                // d'angle de pierre de taille, et l'abat-son dans son
                // encadrement. Un aplat rayé d'un trait d'ombre en faisait
                // une borne.
                appareil(8.2f, 36f, 3.6f, 10f, pierre, rang = 0.85f, longueur = 1.7f, graine = 315)
                for ((i, qx) in floatArrayOf(8.2f, 10.9f).withIndex()) {
                    var qy = 36f
                    var longue = true
                    while (qy < 45.8f) {
                        val qh = if (longue) 1.7f else 1.1f
                        rect(qx, qy, if (longue) 0.9f else 0.65f, qh - 0.2f,
                            if (i == 0) mix(taille, 0xFFFFFFFF.toInt(), 0.12f) else sombre(taille, 0.22f), 0.9f)
                        qy += qh
                        longue = !longue
                    }
                }
                rect(7.9f, 35.8f, 4.2f, 0.8f, taille, 0.9f)
                rect(9.1f, 38.4f, 1.8f, 2.6f, taille, 0.9f)
                rect(9.45f, 38.8f, 1.1f, 1.9f, if (P.allume) P.fenetre else neutre(duJour(0xFF2B3038.toInt())), 0.9f)
                // La flèche, en écailles d'ardoise comme les poivrières du
                // château : c'est la même couverture, à l'autre bout du lieu.
                ardoises(floatArrayOf(9.85f, 24.6f, 10.15f, 24.6f, 12.4f, 36f, 7.6f, 36f), toit, 316, 9, 4)
                pieceD("M9.75 24.8L10 22.2L10.25 24.8Z", sombre(toit, 0.2f), 0f)
            }
            // ---- La ville, en contrebas : des toits serrés qui fuient vers
            // la droite, pris de haut comme sur la photo.
            rue3q(RUE_VEIANEN, MAISONS_VEIANEN, facades(), r, ecart = 1.2f)
            // ---- Les sapins du bord droit, ceux au travers desquels on
            // regarde : ils ferment la vue de ce côté et donnent sa mesure
            // au château, qui est loin derrière eux.
            val proche = sombre(nappe(duJour(0xFF2A5026.toInt()), 0.5f, VERT_CALME), 0.3f)
            /** Un sapin de papier : cinq étages, du plus étroit en haut au plus large au pied. */
            fun sapin(x: Float, bas: Float, h: Float, col: Int) {
                val t = Path()
                val n = 5
                for (k in 0 until n) {
                    val u = k / (n - 1f)
                    val y = bas - h + h * u * 0.78f
                    val l = h * (0.1f + u * 0.26f)
                    t.moveTo(x - l, y + h * 0.3f)
                    t.lineTo(x, y - h * 0.06f)
                    t.lineTo(x + l, y + h * 0.3f)
                    t.close()
                }
                t.addRect(x - h * 0.03f, bas - h * 0.3f, x + h * 0.03f, bas, Path.Direction.CW)
                piece(t, col, 0.5f)
            }
            for (k in 0 until 4) {
                val sx = floatArrayOf(86f, 92.5f, 99f, 104.5f)[k]
                val sy = floatArrayOf(60f, 64f, 58f, 63f)[k]
                val sh = floatArrayOf(26f, 32f, 23f, 30f)[k]
                sapin(sx, sy, sh, if (k % 2 == 0) proche else sombre(proche, 0.12f))
            }
            lisiere(-3f, 103f, { x -> 55f + 1.8f * sin(x * 0.31f) }, 1.8f, 2.8f, 109, sombre(proche, 0.06f))
            c.restore()
        }

        /** La brume de la vallée de l'Our : deux bandes de papier pâle, glissées de [d] unités. */
        fun brumeDeVeianen(d: Float) {
            val col = if (P.nuit) mix(P.cielBas, 0xFFFFFFFF.toInt(), 0.25f) else mix(0xFFFFFFFF.toInt(), P.cielBas, 0.42f)
            for ((k, y) in floatArrayOf(30f, 34.4f).withIndex()) {
                val dec = ((d * (1f + 0.5f * k)) % 30f + 30f) % 30f - 30f
                // L'épaisseur se pince : d'épaisseur constante, les deux
                // bandes faisaient deux rubans tendus en travers de la
                // vallée, et non des bancs qui traînent sur les pentes.
                fun ep(x: Float) = 0.25f + 2.6f * (0.5f + 0.5f * sin(x * 0.082f + k * 2.3f)).pow(1.6f)
                val t = Path()
                var x = -32f
                t.moveTo(x + dec, y + sin(x * 0.2094f + k) * 0.7f)
                while (x <= 132f) { t.lineTo(x + dec, y + sin(x * 0.2094f + k) * 0.7f); x += 2f }
                x = 132f
                while (x >= -32f) { t.lineTo(x + dec, y + ep(x) + sin(x * 0.2094f + 2f + k) * 0.5f); x -= 2f }
                t.close()
                piece(t, col, 0.4f)
            }
        }

        // ---- Esch-Sauer, d'après la photo du lac de la Haute-Sûre

        /**
         * Ce que dit la photo du propriétaire (2026-11-21) :
         *
         * 1. **Deux versants boisés plongent dans l'eau**, celui de droite
         *    haut et proche, celui de gauche bas et fuyant. C'est leur V qui
         *    tient la bande du haut et les deux colonnes, c'est-à-dire tout
         *    ce que le dessin du mot laisse voir.
         * 2. **Le lac est turquoise au large et vert sombre en approchant.**
         *    C'est ce dégradé qui le creuse ; d'un seul ton il n'était qu'une
         *    prairie de plus entre deux pentes vertes.
         * 3. **La grève est de galets pâles**, large à gauche où l'on se
         *    tient, et elle entre dans l'eau par une frange mouillée.
         * 4. **Un ponton de bois à tonnelle blanche** tient la rive de
         *    droite, sa barque amarrée le long, et des pédalos flottent au
         *    large : ce sont les seules choses construites du lieu, et c'est
         *    à elles qu'on mesure les versants.
         *
         * Jusqu'au 2026-11-21 le lieu suivait une photo perdue de la tour
         * ronde et du bourg dans sa boucle (donjon de moellon de schiste,
         * deux rangées de maisons de trois quarts, le drapeau sur sa hampe).
         * Elle reste dans l'historique si le propriétaire la préfère.
         */
        fun eschSauer() {
            val r = Alea(117)
            // La ligne d'eau. La rive d'en face s'éloigne au milieu de la
            // retenue et revient vers nous aux deux bords, plus franchement à
            // droite où le versant est le plus proche. Tout le lieu en
            // découle : les versants s'arrêtent dessus, le ponton flotte
            // dessous, la grève y entre.
            val bornes = floatArrayOf(-6f, 29.6f, 16f, 27.6f, 34f, 26.4f, 52f, 26f,
                70f, 27.4f, 88f, 30f, 106f, 34.6f)
            fun rive(x: Float): Float {
                var i = 0
                while (i < bornes.size - 4 && x > bornes[i + 2]) i += 2
                val t = ((x - bornes[i]) / (bornes[i + 2] - bornes[i])).coerceIn(0f, 1f)
                return bornes[i + 1] + (bornes[i + 3] - bornes[i + 1]) * t
            }
            /** Le ruban compris entre les courbes [haut] et [bas], de [x0] à [x1]. */
            fun ruban(x0: Float, x1: Float, haut: (Float) -> Float, bas: (Float) -> Float): Path {
                val t = Path()
                t.moveTo(x0, haut(x0))
                var x = x0
                while (x < x1) { x = min(x1, x + 2.5f); t.lineTo(x, haut(x)) }
                while (x > x0) { t.lineTo(x, bas(x)); x = max(x0, x - 2.5f) }
                t.lineTo(x0, bas(x0))
                t.close()
                return t
            }
            // ---- Le fond de la retenue : un dernier versant pâle, noyé de
            // lumière, qui ferme le lac au milieu.
            lisiere(-3f, 103f, { x -> 24.4f + 1.4f * sin(x * 0.055f) }, 1.6f, 2.6f, 118,
                mix(nappe(duJour(0xFF40622C.toInt()), 0.34f, VERT_CALME), P.loin, 0.46f))
            plan(1)
            // ---- Les deux versants : trois rideaux à droite, deux à gauche,
            // du clair au fond au sombre devant. À nuance égale deux rideaux
            // n'en font qu'un et la vallée redevient un aplat.
            val versant = nappe(duJour(0xFF336026.toInt()), 0.5f, VERT_CALME)
            fun cimeD(x: Float) = 24.6f - (x - 32f) * 0.335f + 1.2f * sin(x * 0.085f)
            fun cimeG(x: Float) = 18.6f + (x + 6f) * 0.17f
            lisiere(30f, 106f, { x -> cimeD(x) }, 2.2f, 3.6f, 119, mix(versant, P.loin, 0.28f))
            lisiere(-6f, 42f, { x -> cimeG(x) }, 2.2f, 3.6f, 120, mix(versant, P.loin, 0.22f))
            lisiere(46f, 106f, { x -> 25.8f - (x - 46f) * 0.3f + 0.9f * sin(x * 0.13f) }, 2.6f, 4.2f, 121, versant)
            lisiere(-6f, 34f, { x -> 22f + (x + 6f) * 0.135f }, 2.6f, 4.2f, 122, sombre(versant, 0.18f))
            lisiere(64f, 106f, { x -> 27.4f - (x - 64f) * 0.27f }, 3f, 5f, 123, sombre(versant, 0.32f))
            // Des touffes claires et sombres dans les pentes : sans elles, un
            // versant entier n'est qu'une masse verte.
            for (k in 0 until 22) {
                val droite = k % 3 != 0
                val fx = if (droite) 34f + r() * 72f else -6f + r() * 46f
                val fy = (if (droite) cimeD(fx) else cimeG(fx)) + 1.4f + r() * 7f
                if (fy > rive(fx) - 0.8f) continue
                val t = r()
                touffe(if (t < 0.34f) mix(versant, duJour(0xFFDCEE8E.toInt()), 0.26f)
                       else if (t < 0.7f) versant else sombre(versant, 0.36f),
                    fx, fy, 1.6f + r() * 2f,
                    fx + 2.4f, fy + 1.2f, 1.3f + r() * 1.6f,
                    fx - 2.2f, fy + 1f, 1.2f + r() * 1.5f)
            }
            // ---- Le lac. Il passe par [duJour] mais **pas** par [neutre] :
            // le vert de l'eau est une teinte et non une matière bâtie, et
            // neutralisé il tourne au mauve. Sa saturation est tenue comme
            // celle de la végétation ([calmer]) : le ravivage des palettes
            // vives sort un vert d'eau franc en vert pomme, et le lac devient
            // la chose la plus criarde de la carte, devant le mot qui est
            // posé dessus. Et il **cède le pas au ciel le soir et la nuit**
            // ([P.allume]) : une eau se lit à ce qu'elle reflète.
            fun calmer(col: Int) = mix(col, neutre(col), 0.42f)
            // Le fond du lac. [sombre] vise un brun mêlé de la couleur du
            // proche, qui est rose au couchant : l'eau du soir n'y fonçait
            // pas d'un cheveu et restait une plaine. Le creux d'un lac est
            // froid, à toute heure, et c'est lui qui dit la profondeur.
            fun creux(col: Int, t: Float) = mix(col, duJour(0xFF16323A.toInt()), t)
            // Le dosage du soir a été cherché : forcer le vert pour sauver
            // le lac du couchant le rend boueux, parce que [calmer] le
            // désature et que l'eau du soir est franchement rose. Ce qui
            // fait l'eau à cette heure-là, ce sont ses traînées et le creux
            // sous les deux rives, pas sa teinte.
            val lac = calmer(mix(P.eau, duJour(0xFF2E8E78.toInt()), if (P.allume) 0.42f else 0.74f))
            val large = mix(lac, duJour(0xFF63C9B4.toInt()), if (P.allume) 0.24f else 0.34f)
            piece(ruban(-4f, 104f, { x -> rive(x) }, { 66f }), lac, 0.4f)
            // Le haut-fond, clair et turquoise, le long de la rive d'en face.
            forme(ruban(-4f, 104f, { x -> rive(x) + 1.3f }, { x -> rive(x) + 7f }), large, 0.55f)
            // Le reflet des versants, sombre, collé sous leur rive, et plus
            // profond sous les deux pentes qu'au fond de la retenue : sans
            // lui l'eau est un aplat et la forêt ne tient à rien. C'est aussi
            // ce qui sauve le lac du soir, où l'eau prend le rose du ciel et
            // n'était plus qu'une plaine entre deux versants.
            forme(ruban(-4f, 104f, { x -> rive(x) }, { x -> rive(x) + 1.6f }), creux(lac, 0.5f), 0.5f)
            forme(ruban(58f, 104f, { x -> rive(x) }, { x -> rive(x) + 2f + (x - 58f) * 0.13f }), creux(lac, 0.5f), 0.32f)
            forme(ruban(-4f, 36f, { x -> rive(x) }, { x -> rive(x) + 2f + (36f - x) * 0.07f }), creux(lac, 0.45f), 0.28f)
            // L'eau s'assombrit en venant vers nous, en cinq voiles minces
            // aux bords ondulés : trois voiles droits laissaient trois arêtes
            // en travers du lac, là où le dégradé doit être continu.
            for (k in 0 until 5) forme(ruban(-4f, 104f, { x -> 33f + k * 5.5f + 1.3f * sin(x * 0.07f + k) }, { 68f }),
                creux(lac, 0.6f), 0.13f)
            // Les traînées de lumière, couchées et plus longues en approchant.
            // Elles prennent la couleur de l'astre le soir : c'est à ses
            // traînées, bien plus qu'à sa teinte, qu'on reconnaît une eau.
            val brillance = mix(lac, if (P.allume) mix(0xFFFFFFFF.toInt(), P.astre, 0.45f) else 0xFFFFFFFF.toInt(),
                if (P.nuit) 0.16f else 0.46f)
            for (k in 0 until 28) {
                val lx = -4f + r() * 108f
                val ly = rive(lx) + 2.6f + r() * 26f
                rect(lx, ly, 3f + r() * (3f + (ly - 26f) * 0.45f), 0.3f + r() * 0.16f, brillance, 0.5f + r() * 0.2f)
            }
            // ---- Le ponton de la rive droite, sa tonnelle et sa barque.
            val bois = neutre(duJour(0xFF9A9289.toInt()))
            val toile = neutre(duJour(0xFFF2EFE6.toInt()))
            val coque = neutre(duJour(0xFF3B4650.toInt()))
            mineral {
                // Le reflet d'abord, sous le radeau : un ouvrage posé sur une
                // eau qui ne le rend pas flotte au-dessus du carton.
                forme("M83 37.6L104 40.2L104 45L83 41.6Z", sombre(lac, 0.42f), 0.3f)
                forme("M86 37.8L100 39.6L99.4 42.6L86.4 40.6Z", mix(lac, toile, 0.4f), 0.35f)
                // Le radeau, vu de trois quarts : son bout proche à droite.
                // Son platelage est fait de lames, pas d'un aplat gris : le
                // bois est une matière comme une autre, et deux traits
                // dessus n'en faisaient que du carton peint.
                platelage(floatArrayOf(83f, 31.8f, 104f, 34.4f, 104f, 38.2f, 83f, 34.8f), bois, 304, 7)
                // Le chant du platelage, où l'on voit le bout des lames.
                forme("M83 34.8L104 38.2L104 39.4L83 35.8Z", sombre(bois, 0.42f), 0.9f)
                for (k in 0 until 9) ligne(83.5f + k * 2.4f, 34.9f + k * 0.39f, 83.5f + k * 2.4f, 35.9f + k * 0.4f,
                    sombre(bois, 0.6f), 0.18f, 0.7f)
                // Les trois montants, ronds : un filet de lumière à gauche,
                // le flanc droit à l'ombre.
                for (px in floatArrayOf(85.6f, 92.6f, 99.6f)) {
                    val base = 32.1f + (px - 83f) * 0.145f
                    rect(px - 0.3f, base - 6.6f, 0.6f, 6.6f, bois, 0.95f)
                    rect(px - 0.3f, base - 6.6f, 0.16f, 6.6f, mix(bois, 0xFFFFFFFF.toInt(), 0.3f), 0.9f)
                    rect(px + 0.14f, base - 6.6f, 0.16f, 6.6f, sombre(bois, 0.4f), 0.9f)
                }
                // La tonnelle : de la toile tendue sur ses lés, qui pend en
                // festons au bord. Un triangle blanc faisait une bâche de
                // carton.
                val dais = floatArrayOf(84.2f, 27.4f, 92.6f, 23.6f, 101.6f, 28f, 92.8f, 29.6f)
                piece(quad(dais), toile, 0.5f)
                surPan(dais) {
                    for (k in 1 until 6) ligne(k * 16.7f, 0f, k * 16.7f, 100f, sombre(toile, 0.14f), 0.9f, 0.8f)
                    rect(0f, 56f, 100f, 46f, sombre(toile, 0.1f), 0.5f)
                }
                forme("M84.2 28.6L101.6 29.2L101.6 30.2L84.2 29.6Z", sombre(toile, 0.26f), 0.9f)
                for (k in 0 until 16) rond(84.4f + k * 1.1f, 30.15f + k * 0.04f, 0.3f, sombre(toile, 0.26f), 0.9f)
                // La barque amarrée le long du bord proche : son bordé en
                // virures, sa préceinte, sa ligne de flottaison, sa cabine.
                feuille(89.4f, 32.4f, 7.2f, 2.8f, mix(toile, bois, 0.35f), 0.4f)
                for (k in 0 until 4) rect(90.2f + k * 1.7f, 33.2f, 1.1f, 1.4f, neutre(duJour(0xFF2B3038.toInt())), 0.9f)
                pieceD("M85.4 35.2C90 34.5 97 35.3 102.6 36.6L101.8 38.6C96.4 37.4 90 36.6 85.6 36.8Z", coque, 0.5f)
                forme("M85.4 35.9C90 35.2 97 36 102.6 37.3L102.4 37.9C96.8 36.6 90 35.8 85.5 36.5Z",
                    mix(coque, 0xFFFFFFFF.toInt(), 0.26f), 0.9f)
                forme("M85.6 36.8C90 36.5 96.4 37.3 101.8 38.5L101.7 39.1C96.3 37.9 90 37.1 85.6 37.4Z",
                    sombre(coque, 0.45f), 0.9f)
                for (k in 0 until 5) ligne(87f + k * 3.4f, 35.6f + k * 0.21f, 87f + k * 3.4f, 37.2f + k * 0.26f,
                    sombre(coque, 0.3f), 0.2f, 0.7f)
            }
            // La lampe du ponton, le soir et la nuit.
            if (P.allume) {
                halo(92.6f, 28.4f, 4.2f, P.fenetre, 0.42f)
                rond(92.6f, 28.4f, 0.6f, P.fenetre)
            }
            // ---- Les pédalos. Ils sont d'autant plus grands qu'ils sont
            // proches : ce sont eux qui donnent au lac sa largeur.
            fun pedalo(cx: Float, cy: Float, s: Float, dais: Int) = mineral {
                val blanc = neutre(duJour(0xFFF2EFE6.toInt()))
                forme(Path().apply {
                    addOval(cx - 2f * s, cy + 0.9f * s, cx + 2f * s, cy + 2.6f * s, Path.Direction.CW)
                }, sombre(lac, 0.4f), 0.3f)
                ovalePapier(cx, cy, 2.4f * s, 0.8f * s, blanc, 0.35f)
                forme(Path().apply {
                    addOval(cx - 2.4f * s, cy + 0.1f * s, cx + 2.4f * s, cy + 0.9f * s, Path.Direction.CW)
                }, sombre(blanc, 0.3f), 0.7f)
                ligne(cx - 1.2f * s, cy - 0.4f * s, cx - 1.2f * s, cy - 2.4f * s, blanc, 0.3f * s)
                ligne(cx + 1.2f * s, cy - 0.4f * s, cx + 1.2f * s, cy - 2.4f * s, blanc, 0.3f * s)
                feuille(cx - 1.9f * s, cy - 3.2f * s, 3.8f * s, 1f * s, dais, 0.4f)
            }
            pedalo(70f, 33.4f, 0.6f, duJour(0xFFE2703A.toInt()))
            pedalo(28f, 37.8f, 0.85f, duJour(0xFFD94B3C.toInt()))
            pedalo(10f, 43.4f, 1.15f, duJour(0xFFE8A230.toInt()))
            plan(2)
            // ---- La grève de galets, large à gauche où l'on se tient, et
            // qui sort de la fenêtre avant la rive droite : à droite le lac
            // descend jusqu'au bas du carton, comme sur la photo.
            val greve = neutre(duJour(0xFFB9AE96.toInt()))
            /** La ligne d'eau de la grève, celle de [solNaturel] à la même pente. */
            fun bord(x: Float) = 37.5f + (x + 2f) * 0.85f
            solNaturel(SolNaturel.BERGE, 37.5f, 0.85f)
            poser(DEVANT)
            mineral {
                // La frange mouillée : les galets du bord sont sombres et
                // luisants, et c'est elle qui pose la grève dans l'eau. Elle
                // suffit à marquer la ligne d'eau : un liseré clair par-dessus
                // y mettait un bord de piscine, et la grève des galets secs
                // est déjà, elle, la partie claire.
                forme(ruban(-4f, 30f, { x -> bord(x) }, { x -> bord(x) + 2.8f }), sombre(greve, 0.36f), 0.45f)
                forme(ruban(-4f, 30f, { x -> bord(x) }, { x -> bord(x) + 0.9f }), sombre(greve, 0.5f), 0.4f)
                for (k in 0 until 7) {
                    val gx = -4f + r() * 24f
                    val gy = bord(gx) + 1.4f + r() * 7f
                    ovalePapier(gx, gy, 0.8f + r() * 1.3f, 0.45f + r() * 0.6f,
                        if (r() < 0.5f) sombre(greve, 0.22f) else mix(greve, 0xFFFFFFFF.toInt(), 0.2f), 0.3f)
                }
            }
            // ---- Le buisson du bord gauche, celui au pied duquel on se
            // tient : c'est lui qui donne au carton du proche une découpe, et
            // à la vue son premier plan.
            val buisson = sombre(nappe(duJour(0xFF2C5A28.toInt()), 0.52f, VERT_CALME), 0.32f)
            touffe(buisson, -8f, 42f, 7.5f, -2f, 36f, 5.4f, 3.5f, 41f, 4.8f, -1f, 48f, 6f, 5.5f, 47f, 3.6f)
            touffe(mix(buisson, duJour(0xFFDCEE8E.toInt()), 0.24f), 1f, 36.4f, 2.4f, 6f, 42.6f, 2f, -4f, 34.6f, 2.6f)
            c.restore()
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

        /**
         * Un halo de lumière de [rayon] centré en ([cx], [cy]), qui s'efface
         * vers ses bords. Pas de carton : un rond plat posé sur le décor se
         * lit comme une pièce de papier de plus, et non comme une lueur.
         */
        fun halo(cx: Float, cy: Float, rayon: Float, col: Int, o: Float) {
            if (!actif || o <= 0f) return
            p.style = Paint.Style.FILL
            p.color = 0xFF000000.toInt()
            p.alpha = (o.coerceIn(0f, 1f) * 255f).roundToInt()
            p.shader = RadialGradient(cx, cy, rayon,
                intArrayOf((0xFF shl 24) or (col and 0xFFFFFF), col and 0x00FFFFFF),
                null, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, rayon, p)
            p.shader = null
        }

        // ---- Stad, le Grund vu de la Corniche (photo du propriétaire)

        /**
         * Ce que dit la photo du propriétaire (2026-11-21), prise de la
         * Corniche au couchant, et que la version précédente n'avait pas :
         *
         * 1. **La crête n'est bâtie qu'à gauche.** La ville haute s'arrête
         *    vers le tiers de la vue ; tout le milieu est le parc boisé des
         *    casemates, au-dessus du rempart. Une rangée de maisons d'un bord
         *    à l'autre faisait une ville continue, que Luxembourg n'est pas.
         * 2. **Le Kirchberg ferme l'horizon à droite** : trois tours, dont
         *    une qui prend le couchant. Elles sont le seul trait qui date la
         *    vue, et elles tiennent la colonne de droite, que le dessin du
         *    mot laisse libre.
         * 3. **Saint-Michel couronne la ville haute** : une tour carrée
         *    coiffée d'un bulbe, d'un lanternon et d'une aiguille. Le beffroi
         *    carré planté au milieu de la silhouette n'était celui d'aucune
         *    église d'ici.
         * 4. Confirmés par la photo, donc gardés tels quels : le rocher et
         *    son rempart à arcades qui montent dans la bande du haut, la
         *    flèche d'ardoise de Saint-Jean, les façades du Grund qui fuient
         *    au bord de l'Alzette et leur reflet dans l'eau.
         */
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
            // ---- Le Kirchberg, au loin à droite. Peint avant le bois du
            // plateau, qui lui mange le pied : une tour posée sur la cime
            // flotterait, et c'est le défaut qu'on a déjà payé ailleurs.
            mineral {
                // Une tour lointaine doit être **plus sombre** que son ciel,
                // et il faut partir de bien plus sombre qu'on ne croit : le
                // calque du fond s'estompe de 30 % vers un ciel qui, au
                // matin, est jaune. Un béton clair en ressortait de la
                // couleur exacte du ciel, et le Kirchberg disparaissait.
                // Il faut partir de très sombre, comme pour la silhouette de
                // la ville haute : [duJour] mêle la couleur du proche, qui
                // est rose le matin, si bien qu'un béton moyen ressort beige
                // et que les tours se perdent dans le rempart.
                val beton = loin(0xFF363E4A.toInt(), 0.14f)
                val dore = loin(0xFF6A4612.toInt(), 0.06f)
                for (t in arrayOf(floatArrayOf(80.6f, 85.4f, 4.6f, 0f),
                                  floatArrayOf(86.4f, 89.8f, 6.8f, 1f),
                                  floatArrayOf(92f, 94.8f, 1.6f, 1f))) {
                    val col = if (t[3] < 0.5f) dore else beton
                    val w = t[1] - t[0]
                    pieceD("M${t[0]} ${t[2]}L${t[1]} ${t[2]}L${t[1]} 14.4L${t[0]} 14.4Z", col)
                    // L'arête au jour et le flanc à l'ombre : sans elles, ce
                    // sont trois rectangles posés sur le ciel.
                    rect(t[0], t[2], w * 0.3f, 14.4f - t[2], mix(col, 0xFFFFFFFF.toInt(), 0.26f), 0.85f)
                    rect(t[1] - w * 0.26f, t[2], w * 0.26f, 14.4f - t[2], sombre(col, 0.26f), 0.8f)
                    // Les refends des étages, serrés : c'est à eux qu'on voit
                    // la hauteur, et ils ne coûtent qu'un trait.
                    var y = t[2] + 1.1f
                    while (y < 14.4f) { ligne(t[0], y, t[1], y, sombre(col, 0.22f), 0.14f, 0.5f); y += 1.1f }
                    if (P.allume) {
                        var fy = t[2] + 1.3f
                        var k = 0
                        while (fy < 13.6f) {
                            if ((k * 5 + t[0].toInt()) % 3 != 0)
                                rect(t[0] + 0.5f + (k % 2) * w * 0.4f, fy, 0.5f, 0.7f, P.fenetre, 0.8f)
                            fy += 1.1f; k++
                        }
                    }
                }
            }
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
                while (hx < 46f) {
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
                // Saint-Michel fait partie de la même découpe : détachée, la
                // tour redevenait un objet, et la silhouette un décor
                // derrière elle. Sa coiffe est celle de la photo : un bulbe,
                // un lanternon, une aiguille — et non le toit en pointe d'un
                // beffroi, qui n'est celui d'aucune église d'ici.
                val hb = hautRempart(22f)
                ville.addRect(19.6f, hb - 7.4f, 24.4f, hb + 0.8f, Path.Direction.CW)
                ville.addCircle(22f, hb - 9.2f, 2.2f, Path.Direction.CW)
                ville.addRect(21.1f, hb - 12f, 22.9f, hb - 9.2f, Path.Direction.CW)
                ville.moveTo(20.3f, hb - 11.6f); ville.lineTo(22f, hb - 15.4f); ville.lineTo(23.7f, hb - 11.6f); ville.close()
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
                    for (k in 0 until 18) {
                        val x = 16f + ra() * 29f
                        val y = hautRempart(x) - 1f - ra() * 5f
                        rect(x, y, 0.8f, 1.1f, P.fenetre, 0.75f)
                    }
                    rect(21.3f, hb - 5.6f, 1.4f, 2.2f, P.fenetre, 0.9f)
                }
            }
            // ---- Le parc des casemates, entre la ville haute et le
            // Kirchberg : sur la photo le milieu de la crête n'est pas bâti,
            // c'est un bois. D'un seul tenant lui aussi, et à peine plus
            // clair que la silhouette des maisons : deux masses de même
            // valeur à cette distance, l'une bâtie, l'autre plantée.
            run {
                val parc = Path()
                parc.addRect(44f, hautRempart(44f) - 0.4f, 104f, hautRempart(104f) + 1.2f, Path.Direction.CW)
                var bx = 43f
                while (bx < 105f) {
                    val rr = 1.2f + r() * 1.7f
                    parc.addCircle(bx, hautRempart(bx) - 0.4f - rr * 0.45f - r() * 1.9f, rr, Path.Direction.CW)
                    bx += rr * (0.8f + r() * 0.3f)
                }
                // Pas de [mineral] ici, au contraire de la ville : c'est un
                // bois, et les saisons doivent pouvoir le rouiller et
                // l'enneiger comme le reste de la verdure.
                piece(parc, mix(nappe(duJour(0xFF2A4228.toInt()), 0.4f, VERT_CALME), distance, 0.16f))
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
            //
            // La matière avant la découpe (propriétaire, 2026-11-21) : un
            // enduit badigeonné sur un chaînage de pierre de taille, une
            // corniche, des bandeaux, des baies encadrées, une flèche en
            // vraies ardoises. En aplat crème sous un triangle noir, la tour
            // était un jouet de bois posé devant les toits — la règle déjà
            // payée au château de Veianen : la matière d'un immeuble se
            // respecte, fût-il au second plan, et c'est [matiere], celle-là
            // même dont sont faites les façades du Grund à ses pieds.
            mineral {
                val creme = duJour(0xFFE6DCC4.toInt())
                val tour = neutre(creme)
                val taille = pierreDeTaille()
                val creux = neutre(ombreFroide(creme, 0.34f))
                val vitre = if (P.allume) P.fenetre else neutre(duJour(0xFF2B3038.toInt()))
                // Le flanc gauche, qui fuit vers le point de fuite latéral de
                // la rue : une tour vue de face reste une planche.
                val flanc = floatArrayOf(28.4f, 16.9f, 30.8f, 16.6f, 30.8f, 46f, 28.4f, 45.9f)
                piece(quad(flanc), creux, 0.5f)
                surPan(flanc) {
                    rect(0f, 0f, 100f, 3.6f, sombre(taille, 0.26f), 0.9f)
                    var cy = 6f
                    while (cy < 94f) { rect(0f, cy, 100f, 1.2f, sombre(taille, 0.3f), 0.4f); cy += 16f }
                }
                val face = floatArrayOf(30.8f, 16.6f, 37.2f, 16.6f, 37.2f, 46f, 30.8f, 46f)
                piece(quad(face), tour, 0.6f)
                surQuad(6.4f, 29.4f, face) {
                    val clair = mix(tour, 0xFFFFFFFF.toInt(), 0.3f)
                    val rt = Alea(154)
                    // Le badigeon : un enduit n'est jamais d'un seul ton.
                    for (k in 0 until 9) ovalePapierPlat(rt() * 6.4f, 1.4f + rt() * 25f,
                        0.6f + rt() * 1.3f, 0.5f + rt() * 1f, if (k % 2 == 0) clair else creux, 0.16f)
                    // Les chaînes d'angle, une pierre longue, une pierre
                    // courte. Étroites : à la largeur de celles d'une façade
                    // ([matiere]), elles mangeaient la moitié d'une tour qui
                    // ne fait que six unités de large, et la tour ressortait
                    // en pierre de taille d'un bord à l'autre.
                    var cy = 0.9f
                    var k = 0
                    while (cy < 25f) {
                        val l = if (k % 2 == 0) 0.85f else 0.55f
                        rect(0f, cy, l, 0.8f, taille)
                        rect(6.4f - l, cy, l, 0.8f, taille)
                        cy += 0.95f; k++
                    }
                    // Deux bandeaux, et le socle de pierre avec ses joints.
                    for (by in floatArrayOf(9.2f, 18.4f)) {
                        rect(0f, by, 6.4f, 0.4f, taille)
                        rect(0f, by + 0.4f, 6.4f, 0.22f, creux, 0.6f)
                    }
                    rect(0f, 25.6f, 6.4f, 3.8f, taille)
                    rect(0f, 25.38f, 6.4f, 0.22f, creux, 0.6f)
                    val joint = neutre(sombre(taille, 0.25f))
                    for (j in 1..2) rect(0f, 25.6f + j * 1.25f, 6.4f, 0.09f, joint, 0.7f)
                }
                // La corniche qui porte le beffroi, et l'ombre qu'elle jette.
                pieceD("M29.9 16.6L38.1 16.6L38.1 14.4L29.9 14.4Z", taille)
                forme("M29.9 16.6L38.1 16.6L38.1 17.4L29.9 17.4Z", sombre(taille, 0.45f), 0.5f)
                // Les deux baies du beffroi : encadrement de pierre, abat-son
                // à lames, appui saillant.
                for (fx in floatArrayOf(31.5f, 34.5f)) {
                    pieceD("M${fx - 0.3f} 25.1L${fx - 0.3f} 21.7C${fx - 0.3f} 19.8 ${fx + 2.4f} 19.8 ${fx + 2.4f} 21.7L${fx + 2.4f} 25.1Z", taille)
                    forme("M$fx 24.6L$fx 21.8C$fx 20.3 ${fx + 1.8f} 20.3 ${fx + 1.8f} 21.8L${fx + 1.8f} 24.6Z", vitre, 0.95f)
                    if (!P.allume) for (k in 0 until 4)
                        ligne(fx, 21.9f + k * 0.72f, fx + 1.8f, 21.6f + k * 0.72f, mix(taille, 0xFFFFFFFF.toInt(), 0.22f), 0.2f, 0.7f)
                    rect(fx - 0.45f, 25.1f, 2.7f, 0.4f, taille, 0.95f)
                }
                // L'horloge, dans son cercle de pierre.
                rond(34f, 28.4f, 2.1f, taille)
                rond(34f, 28.4f, 1.6f, mix(tour, 0xFFFFFFFF.toInt(), 0.45f))
                rond(34f, 28.4f, 1.6f, sombre(tour, 0.35f), 0.22f)
                ligne(34f, 28.4f, 34f, 27.2f, sombre(tour, 0.6f), 0.26f)
                ligne(34f, 28.4f, 35f, 28.9f, sombre(tour, 0.6f), 0.22f)
                // La flèche : une aiguille, pas un chapeau, et des rangs
                // d'ardoises, pas un triangle noir. Le lanternon de pierre
                // entre les deux.
                val tuile = sombre(neutre(ardoise()), 0.15f)
                ardoises(floatArrayOf(33.8f, 11.4f, 34.2f, 11.4f, 37.6f, 14.4f, 30.4f, 14.4f), tuile, 155, 4, 7)
                piece(quad(floatArrayOf(31.4f, 8.6f, 36.6f, 8.6f, 36.6f, 11.4f, 31.4f, 11.4f)), mix(taille, tour, 0.4f), 0.5f)
                for (lx in floatArrayOf(32.2f, 34.9f)) rect(lx, 9.2f, 1.1f, 1.8f, vitre, 0.9f)
                ardoises(floatArrayOf(33.8f, -1.6f, 34.2f, -1.6f, 37.4f, 8.6f, 30.6f, 8.6f), tuile, 156, 9, 5)
                // L'arête au jour, à gauche : sans elle, les rangs d'ardoise
                // font un cône et non une aiguille à quatre pans.
                forme("M34 -1.6L30.6 8.6L33.3 8.6Z", mix(tuile, 0xFFFFFFFF.toInt(), 0.16f), 0.42f)
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

        /**
         * La Spuerkeess au fond : la tour à **flèche de cuivre verte** (et
         * non le dôme qu'on lui prêtait), ses combles d'ardoise à lucarnes,
         * et la longue aile qui s'en va vers la droite, de trois quarts.
         *
         * Elle est **coupée par le tablier**, et non posée dessus : son
         * pied descend derrière le pont, qui lui passe devant comme sur la
         * photo. Posée sur la balustrade, elle n'était qu'une frise de
         * toits écrasée contre le bord haut. On n'en voit donc que les
         * combles et la flèche, dans la bande du haut et la colonne de
         * gauche : les deux seuls endroits que la plaque du mot épargne.
         */
        private fun spuerkeess(r: Alea) = mineral {
            val creme = neutre(duJour(0xFFD8D1BD.toInt()))
            val toit = ardoise()
            val cuivre = neutre(duJour(0xFF63A68B.toInt()))
            val loin = mix(toit, P.cielBas, 0.45f)
            // Un seul massif de toits derrière, pâli par la distance : cinq
            // pignons séparés faisaient une dentelure illisible.
            pieceD("M39 13.6L42.4 10.4L49 10.8L52.4 13.6Z", loin, 0.3f)
            // L'aile longue, de trois quarts : sa face, son pignon à
            // l'ombre, et le grand comble d'ardoise posé dessus.
            boite3q(13f, 16f, 25f, 8f, 2f, creme, versDroite = true, pente = 0.045f, dessus = false) {
                for (rg in 0 until 3) for (k in 0 until 11) {
                    fenetreVerre(1.1f + k * 2.16f, 1f + rg * 2.3f, 0.98f, 1.3f, r)
                }
            }
            pieceD("M12.4 10.6L15.8 7.6L36.4 8.5L38.8 11.5Z", toit, 0.5f)
            // Les lucarnes, espacées : un comble de vingt-cinq unités sans
            // elles n'est qu'un trapèze gris, mais huit de rang en faisaient
            // un peigne.
            for (k in 0 until 5) {
                val lx = 17.4f + k * 4.3f
                val ly = 9.3f + k * 0.16f
                pieceD("M$lx ${ly + 1.1f}L$lx ${ly}L${lx + 0.9f} ${ly - 0.8f}L${lx + 1.8f} ${ly}L${lx + 1.8f} ${ly + 1.1f}Z",
                    sombre(toit, 0.28f), 0.3f)
            }
            // Le pavillon central et son clocheton, qui cassent la longueur.
            boite3q(23.6f, 16f, 5.6f, 9.4f, 1.4f, mix(creme, 0xFFFFFFFF.toInt(), 0.08f), versDroite = true,
                pente = 0.045f, dessus = false)
            pieceD("M22.4 9.4L26.4 5.2L30.4 9.6Z", toit, 0.4f)
            ligne(26.4f, 5.2f, 26.4f, 3.9f, cuivre, 0.28f)
            // ---- La tour, la silhouette du lieu.
            boite3q(3.4f, 17f, 7.4f, 12.4f, 2.1f, mix(creme, 0xFFFFFFFF.toInt(), 0.12f), versDroite = true,
                pente = 0.045f, dessus = false) {
                // L'horloge dorée et les deux hautes fenêtres du fût.
                rect(1.3f, 2.6f, 3.6f, 0.5f, neutre(duJour(0xFFC2A24E.toInt())))
                for (k in 0 until 2) fenetreVerre(1.4f + k * 2.3f, 4.2f, 1.1f, 2.8f, null)
            }
            // La galerie d'arcatures sous la flèche : percée, pas peinte.
            feuille(3.2f, 5.9f, 7.8f, 1.7f, mix(creme, 0xFFFFFFFF.toInt(), 0.2f), 0.4f)
            for (k in 0 until 5) rect(3.9f + k * 1.5f, 6.2f, 0.5f, 1.1f, sombre(creme, 0.55f))
            // La flèche de cuivre, pointue : c'est elle qu'on reconnaît de
            // l'autre côté de la vallée. Un dôme n'aurait rien dit.
            pieceD("M4.1 6L7.1 0.2L10.1 6Z", cuivre)
            forme("M7.1 0.2L10.1 6L8.4 6Z", sombre(cuivre, 0.32f), 0.55f)
            // Les clochetons d'angle, au pied de la flèche.
            for (lx in floatArrayOf(3.4f, 10.8f)) pieceD("M${lx - 0.55f} 7.6L$lx 5L${lx + 0.55f} 7.6Z", sombre(toit, 0.2f), 0.3f)
        }

        /**
         * Le pont Adolphe, d'après la photo du propriétaire : la grande
         * arche surbaissée et la **seconde nervure** qu'on voit par-dessous
         * (l'ouvrage est double), le tympan **percé** d'arcades qui
         * raccourcissent vers la clé, les trois arches d'accès de la rive
         * droite, le tablier, sa balustrade et ses réverbères.
         *
         * Tout est dessiné à plat puis incliné d'un coup
         * ([PENTE_NEI_BRECK]). Sur la carte ouverte, la colonne de gauche
         * montre la culée et les plus hautes arcades, celle de droite les
         * arches d'accès, et la bande du haut le tablier d'un bout à
         * l'autre. Derrière la plaque il ne reste que la clé, qui est la
         * partie la moins parlante de l'ouvrage.
         *
         * Le pont occupe le haut et laisse la vallée dessous : dessiné plus
         * grand, il remplissait la fenêtre et se lisait comme un mur.
         */
        private fun pontAdolphe(r: Alea) = enPerspective(50f, 30f, PENTE_NEI_BRECK) {
            // La pierre du pont est un calcaire **gris-beige**. Elle était
            // ocre jaune, ce qui faisait du pain d'épice (règle du bâti sans
            // dominante jaune, 2026-11-14).
            val pierre = neutre(duJour(0xFFC4C0B6.toInt()))
            val clair = mix(pierre, 0xFFFFFFFF.toInt(), 0.24f)
            val corniche = 16f    // le dessus de la corniche
            val tablier = 18f     // le dessous du tablier
            val garde = 11.5f     // le dessus de la balustrade
            val cx = 42f; val cy = 38f; val rx = 30f; val ry = 15.5f; val ep = 2.4f
            val pi = PI.toFloat()
            /** Le dos de la grande arche en [x] : c'est sur lui que le tympan s'appuie. */
            fun extrados(x: Float): Float {
                val u = ((x - cx) / (rx + ep)).coerceIn(-1f, 1f)
                return cy - (ry + ep) * sqrt(1f - u * u)
            }
            /** Le vide d'une arcade : les deux piédroits et le plein cintre dessus. */
            fun arcade(ax: Float, hw: Float, haut: Float, bas: Float): Path = Path().apply {
                moveTo(ax - hw, bas); lineTo(ax - hw, haut + hw)
                arcTo(RectF(ax - hw, haut, ax + hw, haut + 2f * hw), 180f, 180f)
                lineTo(ax + hw, bas); close()
            }
            mineral {
                // ---- La culée de gauche et la pile de droite : c'est par
                // elles que le pont touche terre, et ce sont elles que les
                // deux colonnes libres de la carte ouverte montrent.
                appareil(Path().apply { addRect(-8f, tablier, 12f, 50f, Path.Direction.CW) }, pierre, 2.4f, 4.4f, 171)
                // Les arches d'accès de la rive droite, **percées** d'un
                // seul tenant : peintes pleines et rayées de traits, elles
                // feraient un mur de soutènement.
                val rive = Path().apply { addRect(72f, tablier, 108f, 48f, Path.Direction.CW) }
                val vides = Path()
                val accesX = floatArrayOf(79f, 88.4f, 97.8f)
                for (ax in accesX) {
                    forme(arcade(ax, 3f, 24.4f, 48f), sombre(pierre, 0.5f))
                    vides.addPath(arcade(ax - 0.6f, 2.6f, 24.8f, 48f))
                }
                rive.op(vides, Path.Op.DIFFERENCE)
                appareil(rive, pierre, 2.4f, 4.4f, 172, ombre = 0.9f)
                // ---- Le tympan, percé lui aussi : les arcades sont ce qui
                // fait reconnaître ce pont-là, et elles raccourcissent à
                // mesure que le dos de l'arche remonte vers la clé.
                val tympan = Path()
                tympan.moveTo(12f, tablier)
                tympan.lineTo(72f, tablier)
                var tx = 72f
                while (tx > 12f) { tympan.lineTo(tx, extrados(tx)); tx -= 1.2f }
                tympan.lineTo(12f, extrados(12f))
                tympan.close()
                val arcades = Path()
                val poses = ArrayList<FloatArray>()
                val haut = tablier + 0.5f
                for (ax in floatArrayOf(15f, 20f, 25f, 30f, 34.6f, 49.4f, 54f, 59f, 64f, 69f)) {
                    val bas = extrados(ax) - 0.7f
                    val h = bas - haut
                    if (h < 2.4f) continue
                    val hw = min(2.3f, (h - 1f) * 0.45f)
                    // Le tableau : l'épaisseur de l'ouvrage, à l'ombre, le
                    // long du bord opposé au jour. Seulement le bord : peint
                    // sur tout le trou, il le bouche et l'arcade devient un
                    // panneau sombre au lieu d'un passage.
                    val vide = arcade(ax - 0.45f, hw - 0.28f, haut + 0.3f, bas)
                    forme(Path(arcade(ax, hw, haut, bas)).apply { op(vide, Path.Op.DIFFERENCE) },
                        sombre(pierre, 0.5f))
                    arcades.addPath(vide)
                    poses.add(floatArrayOf(ax, hw))
                }
                tympan.op(arcades, Path.Op.DIFFERENCE)
                appareil(tympan, pierre, 2.2f, 4f, 173, ombre = 0.9f)
                // Chaque arcade a son archivolte : un petit rang de claveaux
                // sur sa tête. Sans lui, le trou n'est qu'une découpe.
                for ((k, p) in poses.withIndex())
                    voussoirs(p[0], haut + p[1], p[1], p[1], 0.62f, 0.05f, pi - 0.05f, clair, 180 + k, 0.5f)
                for ((k, ax) in accesX.withIndex())
                    voussoirs(ax, 24.4f + 3f, 3f, 3f, 0.75f, 0.05f, pi - 0.05f, clair, 190 + k, 0.42f)
                // ---- Les deux nervures. Celle du fond d'abord, plus
                // claire et décalée : c'est elle qu'on aperçoit sous
                // l'arche, et elle dit que l'ouvrage est double. Sans elle
                // le pont n'a qu'une épaisseur de carton.
                voussoirs(42.7f, 37.1f, rx - 3f, ry - 3f, 3f, 0.03f, pi - 0.03f, clair, 174, 0.08f)
                voussoirs(cx, cy, rx, ry, ep, 0.03f, pi - 0.03f, pierre, 175)
                if (!vivant) tram(16f)
                // ---- Le tablier : la corniche, puis la balustrade
                // **ajourée**. Une bande pleine à balustres peints aurait
                // fait un parapet de béton.
                feuille(-8f, corniche, 116f, tablier - corniche, clair, 0.6f)
                val balustrade = Path().apply { addRect(-8f, garde, 108f, corniche, Path.Direction.CW) }
                val jours = Path()
                var bx = -7.4f
                var n = 0
                while (bx < 108f) {
                    // Un dé plein de loin en loin : une balustrade n'est pas
                    // une grille, elle se reprend tous les huit balustres.
                    if (n % 9 != 0) jours.addRect(bx, garde + 0.8f, bx + 0.55f, corniche - 0.8f, Path.Direction.CW)
                    bx += 1.12f
                    n++
                }
                balustrade.op(jours, Path.Op.DIFFERENCE)
                piece(balustrade, mix(pierre, 0xFFFFFFFF.toInt(), 0.32f), 0.9f)
                // ---- Les réverbères, et le pont allumé le soir : c'est
                // son image de nuit.
                val fonte = neutre(duJour(0xFF4A4E54.toInt()))
                for (lx in floatArrayOf(2f, 21f, 40f, 59f, 78f, 97f)) {
                    ligne(lx, garde + 0.3f, lx + 0.22f, garde - 5.4f, fonte, 0.3f)
                    ligne(lx + 0.22f, garde - 5.4f, lx + 1.8f, garde - 5.8f, fonte, 0.24f)
                    if (P.allume) {
                        halo(lx + 2f, garde - 5.6f, 3.6f, 0xFFFFE6A8.toInt(), 0.5f)
                        rond(lx + 2f, garde - 5.7f, 0.4f, 0xFFFFF6D8.toInt())
                    } else rond(lx + 2f, garde - 5.7f, 0.4f, mix(pierre, 0xFFFFFFFF.toInt(), 0.45f))
                }
                if (P.allume) {
                    // Les projecteurs du dessous, qui lèchent l'arche.
                    halo(22f, 32f, 9f, 0xFFFFDF9E.toInt(), 0.14f)
                    halo(62f, 32f, 9f, 0xFFFFDF9E.toInt(), 0.12f)
                }
            }
        }

        fun neiBreck() {
            val r = Alea(175)
            // Ce que dit la photo du propriétaire, et qu'aucune maquette
            // n'avait :
            //
            // 1. **Le pont est en pierre de taille gris-beige**, et non une
            //    bande ocre posée en biais. Sa grande arche est surbaissée,
            //    et on voit la **seconde nervure** par-dessous : l'ouvrage
            //    est double.
            // 2. **Le tympan est percé d'arcades** qui raccourcissent vers
            //    la clé, et la rive droite porte trois arches d'accès.
            // 3. **Le tablier a une balustrade** et une file de réverbères.
            // 4. **La Spuerkeess a une flèche de cuivre verte**, pas un
            //    dôme, et de grands combles d'ardoise à lucarnes.
            // 5. **Tout le bas est une vallée boisée** : on regarde la
            //    Pétrusse par-dessus la cime des arbres, et non depuis une
            //    allée de gravier.
            spuerkeess(r)
            // Le versant d'en face, bas : les arcades doivent s'ouvrir sur
            // le ciel, sinon elles se lisent comme des fenêtres dans un mur.
            lisiere(-4f, 104f, { x -> 25f + 2.4f * sin(x * 0.07f) }, 2.2f, 3.6f, 176,
                nappe(mix(P.loin, P.milieu, 0.5f), 0.3f, VERT_CALME))
            // Un second rideau plus bas et plus franc : un seul versant
            // derrière l'arche faisait un aplat, et la vallée n'avait pas
            // de fond.
            lisiere(-4f, 104f, { x -> 32f + 3f * sin(x * 0.11f + 2f) }, 2.4f, 4f, 179,
                nappe(mix(P.milieu, vert(0.6f), 0.45f), 0.34f, VERT_CALME))
            plan(1)
            pontAdolphe(r)
            plan(2)
            // Le chemin de la vallée, au ras du bord : sans sol, les cinq
            // unités sous [DEVANT] laissaient voir le plancher de la scène.
            solNaturel(SolNaturel.TERRE, DEVANT)
            poser(DEVANT)
            // ---- La vallée de la Pétrusse. Le premier plan est le
            // feuillage lui-même : c'est de la végétation, donc les saisons
            // le rouillent, et la carte d'automne redonne la photo.
            // Quatre rideaux d'écart de valeur, du clair au sombre en
            // approchant : d'un rideau à l'autre, la même nuance de vert
            // refaisait un aplat et la vallée n'avait pas de fond.
            val feuillu = vert(0.68f)
            lisiere(-4f, 104f, { x -> 41f + 3.2f * sin(x * 0.085f + 1.2f) }, 2.8f, 4.6f, 177, mix(feuillu, P.loin, 0.22f))
            lisiere(-4f, 104f, { x -> 47f + 3f * sin(x * 0.12f + 3f) }, 3.2f, 5.4f, 178, feuillu)
            lisiere(-4f, 104f, { x -> 52f + 2.6f * sin(x * 0.16f) }, 4f, 6.6f, 181, sombre(vert(0.5f), 0.14f))
            lisiere(-4f, 104f, { x -> 57f + 2.2f * sin(x * 0.21f + 1f) }, 4.6f, 7.4f, 182, sombre(vert(0.38f), 0.3f))
            for (k in 0 until 8) {
                val fx = -3f + k * 13.5f + r() * 5f
                touffe(if (k % 2 == 0) vert(0.56f) else sombre(vert(0.44f), 0.24f),
                    fx, 53f + r() * 3f, 3.4f + r() * 2.2f, fx + 4.6f, 55f + r() * 2.4f, 3f + r() * 1.8f)
            }
            // Deux sapins sombres, **devant** les rideaux de feuillus :
            // dessinés derrière, ils étaient recouverts aux trois quarts. Ce
            // sont eux qui donnent l'échelle de la vallée sous le pont.
            for (sp in floatArrayOf(14f, 80f)) {
                val h = 24f + r() * 5f
                pieceD("M${sp - 3.6f} 58.5L${sp - 1.7f} ${58.5f - h * 0.45f}L${sp - 2.7f} ${58.5f - h * 0.46f}" +
                    "L${sp - 1.3f} ${58.5f - h * 0.76f}L${sp - 2.1f} ${58.5f - h * 0.77f}L$sp ${58.5f - h}" +
                    "L${sp + 2.1f} ${58.5f - h * 0.77f}L${sp + 1.3f} ${58.5f - h * 0.76f}L${sp + 2.7f} ${58.5f - h * 0.46f}" +
                    "L${sp + 1.7f} ${58.5f - h * 0.45f}L${sp + 3.6f} 58.5Z", sombre(vert(0.26f), 0.3f))
            }
            c.restore()
        }

        /** Le tram sur le pont, son nez en [x]. */
        fun tram(x: Float, y: Float = 8.5f) {
            feuille(x, y, 15f, 3f, duJour(0xFF2C3A58.toInt()), 0.4f)
            rect(x + 0.8f, y + 0.5f, 13.4f, 1.1f, if (P.allume) P.fenetre else duJour(0xFFB8C8D8.toInt()))
            rect(x, y + 2.2f, 15f, 0.4f, duJour(0xFFC9D2DC.toInt()))
            if (P.allume) rond(x + 15f, y + 2f, 0.5f, 0xFFFFF4C8.toInt())
        }

        // ---- Belval, les hauts fourneaux d'Esch (photo du propriétaire)

        /**
         * Une charpente d'acier **percée** : le trapèze de [w] sur [h] posé
         * en ([x], [bas]), découpé d'un seul tenant en [nc] travées et [nr]
         * étages, montants et entretoises, le ciel entre les deux.
         *
         * C'est le découpage qui fait lire une charpente. Peinte pleine, avec
         * ses barres dessinées dessus, elle ne fait qu'une façade à meneaux,
         * et le fourneau une maison.
         */
        fun charpente(x: Float, bas: Float, w: Float, h: Float, col: Int, nc: Int, nr: Int, rentre: Float = 0.07f) = mineral {
            val haut = bas - h
            val g = x + w * rentre
            val d = x + w * (1f - rentre)
            fun px(u: Float, v: Float): Float {
                val a = x + (g - x) * v
                val b = (x + w) + (d - (x + w)) * v
                return a + (b - a) * u
            }
            fun py(u: Float, v: Float): Float = bas + (haut + 0.8f * u - bas) * v
            val cage = Path()
            cage.moveTo(px(0f, 0f), py(0f, 0f)); cage.lineTo(px(0f, 1f), py(0f, 1f))
            cage.lineTo(px(1f, 1f), py(1f, 1f)); cage.lineTo(px(1f, 0f), py(1f, 0f))
            cage.close()
            val trous = Path()
            for (ic in 0 until nc) for (ir in 0 until nr) {
                val ua = 0.1f + 0.8f * (ic + 0.12f) / nc
                val ub = 0.1f + 0.8f * (ic + 0.88f) / nc
                val va = 0.04f + 0.9f * (ir + 0.09f) / nr
                val vb = 0.04f + 0.9f * (ir + 0.91f) / nr
                trous.moveTo(px(ua, va), py(ua, va)); trous.lineTo(px(ub, va), py(ub, va))
                trous.lineTo(px(ub, vb), py(ub, vb)); trous.lineTo(px(ua, vb), py(ua, vb))
                trous.close()
            }
            cage.op(trous, Path.Op.DIFFERENCE)
            // Du métal, pas du carton : chaque membrure prend l'arête du
            // soleil. La découpe est peinte une première fois en acier
            // clair, puis une seconde, décalée, en acier sombre : il reste
            // un filet de lumière en haut et à gauche de chaque barre, là
            // où le soleil de la photo accroche les profilés.
            piece(cage, mix(col, 0xFFFFFFFF.toInt(), 0.34f))
            cage.offset(0.34f, 0.3f)
            forme(cage, col)
        }

        /**
         * Un cowper : le cylindre clair de [w] posé en [x], sa calotte
         * conique en [haut] et sa cheminée. Ils vont par trois ou quatre,
         * serrés contre le fourneau, et ce sont **les seules pièces claires**
         * de la friche : l'acier y est presque noir à contre-jour.
         */
        fun cowper(x: Float, haut: Float, w: Float, col: Int, acier: Int, cone: Boolean = true) = mineral {
            feuille(x, haut, w, 60f - haut, col, 0.6f)
            // La tôle rivée : le cylindre est fait de viroles boulonnées
            // bout à bout, et c'est ce rang de rivets à chaque joint qui le
            // distingue d'un silo de béton.
            val rr = Alea((x * 17f).toInt() + 400)
            var jy = haut + 3.4f
            while (jy < 59f) {
                ligne(x, jy, x + w, jy, sombre(col, 0.3f), 0.3f)
                var rx = x + 0.7f
                while (rx < x + w - 0.4f) {
                    rond(rx, jy - 0.7f, 0.18f, mix(col, 0xFFFFFFFF.toInt(), 0.22f), 0.75f)
                    rx += 1.4f
                }
                jy += 4.6f + rr() * 0.6f
            }
            // Le filet de lumière sur la panse, à un quart du bord : un
            // large bandeau clair collé au bord gauche faisait trois
            // planches côte à côte au lieu d'un cylindre.
            feuille(x + w * 0.16f, haut + 0.4f, w * 0.12f, 59.6f - haut, mix(col, 0xFFFFFFFF.toInt(), 0.26f), 0f)
            if (cone) {
                // Une calotte bombée, pas un cône : pointue, elle faisait
                // des tourelles de château au lieu de récipients sous pression.
                pieceD("M${x - 0.5f} ${haut + 0.3f}Q${x + w * 0.5f} ${haut - w * 0.72f} ${x + w + 0.5f} ${haut + 0.3f}Z", col)
                feuille(x + w * 0.46f - 0.5f, haut - w * 1f, 1f, w * 0.5f, acier, 0.3f)
            } else feuille(x - 0.5f, haut - 1.1f, w + 1f, 1.4f, sombre(col, 0.22f), 0.4f)
            // Le flanc à l'ombre : le soleil vient de la gauche sur la photo.
            feuille(x + w * 0.74f, haut + 0.6f, w * 0.26f, 59.4f - haut, sombre(col, 0.17f), 0f)
        }

        /**
         * Un pan de tôle : les joints horizontaux des plaques, leurs rivets,
         * et l'arête que le soleil prend à gauche. C'est ce qui distingue
         * une masse d'acier d'un aplat de papier gris.
         */
        fun tole(x: Float, y: Float, w: Float, h: Float, col: Int, graine: Int) {
            val rr = Alea(graine)
            var jy = y + 3f
            while (jy < y + h - 0.6f) {
                ligne(x, jy, x + w, jy, sombre(col, 0.3f), 0.3f)
                var rx = x + 0.8f
                while (rx < x + w - 0.5f) {
                    rond(rx, jy - 0.7f, 0.2f, mix(col, 0xFFFFFFFF.toInt(), 0.26f), 0.8f)
                    rx += 1.6f
                }
                jy += 4.2f + rr() * 1.2f
            }
            var jx = x + w * 0.42f
            while (jx < x + w - 0.5f) { ligne(jx, y, jx, y + h, sombre(col, 0.24f), 0.28f); jx += w * 0.46f }
            feuille(x, y, w * 0.08f, h, mix(col, 0xFFFFFFFF.toInt(), 0.26f), 0f)
        }

        /**
         * L'appareil de brique d'une cheminée, du rang du bas [bas] au rang
         * du haut [haut], entre les bords que donne [bord] (gauche et droite
         * à une hauteur) : des rangs décalés d'une demi-brique, chaque brique
         * d'un ton un peu différent, le joint en creux entre elles.
         *
         * C'est l'appareil qui fait la brique. Cerclé de quatre traits
         * seulement, le fût se lisait comme un poteau de bois peint.
         */
        fun appareilBrique(bas: Float, haut: Float, col: Int, graine: Int, bord: (Float) -> FloatArray) {
            val rr = Alea(graine)
            val rang = 1.3f
            var y = haut
            var k = 0
            while (y < bas) {
                val b = bord(y)
                val lb = (b[1] - b[0]) / 4f
                val hh = min(rang - 0.26f, bas - y)
                var bx = b[0] - if (k % 2 == 0) 0f else lb * 0.5f
                while (bx < b[1]) {
                    val x0 = max(b[0], bx)
                    val x1 = min(b[1], bx + lb - 0.22f)
                    val u = rr()
                    if (x1 - x0 > 0.2f) rect(x0, y, x1 - x0, hh,
                        if (u < 0.5f) sombre(col, 0.05f + u * 0.3f) else mix(col, 0xFFFFFFFF.toInt(), (u - 0.5f) * 0.28f))
                    bx += lb
                }
                y += rang
                k++
            }
        }

        /**
         * Le litage du grès sur une masse déjà peinte : des bancs
         * horizontaux de tons voisins de [haut] à [bas], un surplomb tous
         * les trois bancs (son ombre dessous, son arête au soleil), et
         * quelques diaclases verticales. [pente] incline les lits, qui ne
         * sont jamais tout à fait horizontaux.
         *
         * Le dessin est enfermé dans [masse], la pièce qu'on vient de poser
         * : sans ce découpage les lits traverseraient la fenêtre. Le relevé
         * des saisons est découpé avec lui, sinon la roche effacerait de la
         * végétation en dehors d'elle-même.
         *
         * C'est le litage qui fait le grès. Cerclée de trois traits, une
         * masse de roche se lit comme un tas de sable.
         */
        fun strates(masse: Path, haut: Float, bas: Float, col: Int, graine: Int,
                    pas: Float = 3.4f, pente: Float = 0f) {
            if (!actif) return
            val rr = Alea(graine)
            c.save(); c.clipPath(masse)
            releve?.save(); releve?.clipPath(masse)
            /** Tout ce qui est sous une crête ondulée posée en [y] : un banc. */
            fun banc(y: Float, amp: Float, phase: Float, cl: Int, o: Float) {
                val t = Path()
                t.moveTo(-5f, bas + 8f); t.lineTo(-5f, y)
                var x = -5f
                while (x <= LARGEUR + 5f) {
                    t.lineTo(x, y + sin(x * 0.09f + phase) * amp + sin(x * 0.31f + phase) * amp * 0.3f + x * pente)
                    x += 2.5f
                }
                t.lineTo(LARGEUR + 5f, bas + 8f); t.close()
                forme(t, cl, o)
            }
            var y = haut
            var k = 0
            while (y < bas) {
                val amp = 0.3f + rr() * 0.6f
                val phase = rr() * 6.2f
                val u = rr()
                val ton = if (u < 0.5f) sombre(col, 0.08f + u * 0.7f)
                          else mix(col, 0xFFFFFFFF.toInt(), (u - 0.5f) * 0.6f)
                // Un banc sur trois fait saillie : l'arête prend le jour, et
                // l'ombre qu'elle porte dessous est ce qui creuse la roche.
                if (k % 3 == 1) banc(y - 0.4f, amp, phase, mix(col, 0xFFFFFFFF.toInt(), 0.3f), 0.9f)
                banc(y, amp, phase, ton, 1f)
                if (k % 3 == 1) {
                    banc(y + 0.2f, amp, phase, sombre(col, 0.55f), 0.5f)
                    banc(y + 1.3f, amp, phase, ton, 1f)
                }
                y += pas * (0.55f + rr() * 0.9f)
                k++
            }
            // Les diaclases : le grès se débite en blocs, et ces cassures
            // verticales empêchent le litage de se lire comme un store.
            for (i in 0 until 9) {
                val fx = -2f + rr() * 104f
                val y0 = haut + rr() * (bas - haut) * 0.7f
                val l = (bas - haut) * (0.12f + rr() * 0.26f)
                ligne(fx, y0, fx + (rr() - 0.5f) * 2.4f, y0 + l, sombre(col, 0.42f), 0.2f + rr() * 0.24f, 0.6f)
            }
            releve?.restore(); c.restore()
        }

        fun belval() {
            val r = Alea(185)
            // Le fond : la ligne basse des halles et des bâtiments de la
            // Cité des Sciences, loin derrière. Sur la photo le ciel est net
            // et c'est l'acier qui fait tout : le lointain reste une bande.
            lisiere(-2f, 102f, { x -> 46f + 1.6f * sin(x * 0.08f) }, 1.4f, 2.2f, 186,
                neutre(mix(P.loin, P.cielBas, 0.34f)))
            plan(1)
            // Les matières de la photo : l'acier des charpentes presque noir
            // à contre-jour, la brique chaude de la cheminée, le béton clair
            // des cowpers et du long mur de droite. Tout passe par [neutre],
            // comme le bâti (règle 10) : sans lui le gris vire au brun.
            val acier = neutre(duJour(0xFF3B4654.toInt()))
            val brique = neutre(duJour(0xFF9A5A44.toInt()))
            val beton = neutre(duJour(0xFFC9C3B6.toInt()))
            val mur = neutre(duJour(0xFFBDB49F.toInt()))
            mineral {
                // Le long mur à refends qui ferme la place à droite.
                feuille(78f, 30f, 26f, 30f, mur, 0.5f)
                feuille(78f, 28.8f, 26f, 1.4f, sombre(mur, 0.28f), 0.3f)
                var mx = 80f
                while (mx < 104f) { ligne(mx, 30.6f, mx, 60f, sombre(mur, 0.18f), 0.35f); mx += 3.6f }
                // La cheminée de brique : la verticale qui tient toute la
                // photo, et la seule pièce chaude du lieu. Elle sort par le
                // haut de la fenêtre, comme elle sort du cadre de la photo.
                pieceD("M5.6 60L7.4 3.4L13.4 3.4L15.2 60Z", sombre(brique, 0.45f))
                appareilBrique(60f, 3.6f, brique, 188) { y ->
                    val t = (y - 3.4f) / 56.6f
                    floatArrayOf(7.4f - 1.8f * t, 13.4f + 1.8f * t)
                }
                // Le flanc à l'ombre, en voile : le soleil vient de la
                // gauche, et l'appareil doit rester lisible dessous.
                forme(chemin("M12.4 60L11.5 3.6L13.4 3.6L15.2 60Z"), sombre(brique, 0.55f), 0.34f)
                // La couronne de fonte en haut du fût, et les cerclages.
                feuille(6.6f, 1.6f, 7.6f, 2f, sombre(brique, 0.4f), 0.4f)
                for (y in floatArrayOf(16f, 32f, 48f)) {
                    val t = (y - 3.4f) / 56.6f
                    ligne(7.4f - 1.8f * t, y, 13.4f + 1.8f * t, y, sombre(brique, 0.42f), 0.5f)
                }
                // Le grand cylindre clair contre la cheminée.
                cowper(17f, 9.5f, 9f, beton, acier, cone = false)
                // La tour du gueulard : la charpente la plus haute, avec son
                // chevalement. C'est elle qui dépasse de tout sur la photo.
                charpente(28f, 60f, 14f, 57f, acier, 2, 7)
                feuille(26.4f, 1.4f, 17.2f, 2.2f, acier, 0.5f)
                trait("M28 3.6L35 8.4L42 3.6", acier, 0.9f)
                // Le massif du fourneau, et la grosse conduite qui en descend.
                // Le massif du fourneau : la halle de coulée en bas, une
                // charpente ajourée au-dessus, le pot à poussière contre
                // elle. D'un seul tenant, il ne faisait qu'une colline noire.
                pieceD("M36 60L36 34L58 32L58 60Z", acier)
                tole(36f, 32f, 22f, 28f, acier, 410)
                charpente(37f, 34f, 18f, 15f, acier, 3, 2)
                pieceD("M48 60L48 30C48 24.6 56 24.6 56 30L56 60Z", sombre(acier, 0.18f))
                tole(48f, 29f, 8f, 31f, sombre(acier, 0.18f), 420)
                // Les conduites qui descendent du gueulard sur le massif :
                // elles le longent au lieu de l'enjamber en arc-en-ciel.
                trait("M38 9C40 15 44 19 50 23", acier, 2.2f)
                trait("M44 24C50 27 54 32 55 40", sombre(acier, 0.25f), 1.5f)
                trait("M58 27C64 24 70 26 76 30", acier, 1.6f)
                // Les cowpers : trois cylindres clairs à calotte conique,
                // serrés contre le fourneau. Leurs cônes sont ce que la bande
                // du haut laisse voir d'eux sur la carte ouverte.
                cowper(58f, 13f, 7.4f, beton, acier)
                cowper(66.4f, 10f, 8f, beton, acier)
                cowper(75.4f, 15f, 7f, beton, acier)
                // La conduite qui passe devant la grappe de cowpers. Deux
                // tuyaux verticaux collés contre leur flanc à l'ombre n'y
                // faisaient qu'une bande noire de trois unités de large.
                trait("M56.5 30.4C66 29.2 74 29.6 83 31.2", sombre(acier, 0.16f), 1.7f)
                // La charpente de droite, devant le mur.
                charpente(84f, 60f, 13f, 42f, acier, 2, 5)
                if (P.allume) {
                    // La nuit, les fourneaux sont éclairés d'en bas d'une
                    // lumière chaude : c'est l'image que Belval donne de
                    // lui-même, et sans elle la friche n'est qu'une masse
                    // bleue de plus.
                    for (x in floatArrayOf(10f, 46f, 90f)) {
                        halo(x, 44f, 24f, 0xFFFF9A3C.toInt(), 0.4f)
                        halo(x, 52f, 13f, 0xFFFFC46A.toInt(), 0.38f)
                    }
                    for (x in floatArrayOf(61.7f, 70.4f, 78.9f)) halo(x, 18f, 9f, 0xFFFFB45A.toInt(), 0.26f)
                    for (k in 0 until 7) rond(80f + r() * 22f, 33f + r() * 22f, 0.4f, P.fenetre)
                }
            }
            plan(2)
            solDePierre(SolDePierre.DALLES, DEVANT, -0.02f)  // la place des Hauts-Fourneaux
            poser(DEVANT)
            // La haie basse au pied des charpentes, et les mâts des
            // bannières plantés sur la place : c'est tout ce que la photo
            // montre devant l'acier.
            lisiere(-2f, 102f, { x -> 56.8f + 0.8f * sin(x * 0.24f) }, 0.8f, 1.3f, 187,
                nappe(mix(vert(0.1f), 0xFF20371F.toInt(), 0.5f), 0.3f, VERT_CALME))
            if (!vivant) for ((k, x) in BANNIERES.withIndex()) banniere(x, 0f, k)
            c.restore()
        }

        /** Une bannière blanche sur son mât en [x], gonflée de [vent]. */
        fun banniere(x: Float, vent: Float, k: Int) {
            ligne(x, 25f, x, DEVANT, neutre(duJour(0xFF8C9096.toInt())), 0.35f)
            val toile = duJour(if (k % 2 == 0) 0xFFF4F4F2.toInt() else 0xFFDCE6EE.toInt())
            pieceD("M${x + 0.3f} 26L${x + 2.7f + vent} 26.4L${x + 2.5f + vent * 1.4f} 39L${x + 0.3f} 39Z", toile, 0.4f)
        }

        // ---- Bäerdref, l'arche de grès dans la forêt (photo du propriétaire)

        fun baerdref() {
            val r = Alea(195)
            // ---- Ce qu'on voit par le trou : la hêtraie en plein soleil.
            // Sur la photo c'est la seule clarté de l'image, et tout le
            // reste est une roche à contre-jour. Le second rideau d'abord,
            // que la distance pâlit (l'estompe du premier calque).
            lisiere(-2f, 102f, { x -> 9f + 3f * sin(x * 0.08f) }, 2.4f, 3.8f, 196,
                nappe(mix(P.loin, P.milieu, 0.45f), 0.4f, VERT_CALME))
            plan(1)
            val feuillu = vert(0.78f)
            val clair = mix(feuillu, duJour(0xFFEDF4A8.toInt()), if (P.nuit) 0.06f else 0.42f)
            val feuilleSombre = sombre(feuillu, 0.3f)
            // L'ombre du sous-bois, sur laquelle les touffes éclairées se
            // détachent : sans elle, ce qui reste entre deux touffes est le
            // fond nu, et le trou devient un champ uni, orange le soir.
            lisiere(-2f, 102f, { x -> 12f + 3f * sin(x * 0.14f + 2f) }, 2f, 3.2f, 201, feuilleSombre)
            // Les fûts clairs des hêtres, que le soleil prend de côté. Fins
            // et peu nombreux : larges et réguliers, ils faisaient les
            // meneaux d'une fenêtre au lieu d'une futaie.
            val tronc = neutre(duJour(0xFFCFC6B2.toInt()))
            for (k in 0 until 5) {
                val tx = 2f + k * 21f + r() * 8f
                ligne(tx, 8f, tx + (r() - 0.5f) * 3f, 44f, tronc, 0.3f + r() * 0.35f, 0.8f)
            }
            // Le feuillage en touffes découpées et non en nappe unie :
            // autrement le trou se lit comme un aplat vert, et les fûts
            // comme des barreaux. Elles doivent les recouper.
            for (k in 0 until 30) {
                val fx = -3f + r() * 106f
                val fy = 8f + r() * 28f
                val t = r()
                touffe(if (t < 0.3f) clair else if (t < 0.75f) feuillu else feuilleSombre,
                    fx, fy, 1.8f + r() * 2.2f,
                    fx + 2.4f, fy + 1.4f, 1.6f + r() * 1.8f,
                    fx - 2.2f, fy + 1.2f, 1.4f + r() * 1.8f)
            }
            // Le talus éclairé au fond du trou, puis la dalle sur laquelle
            // l'arche repose : c'est cette bande claire qui fait de
            // l'ouverture un œil, et non une tache.
            pieceD("M-3 48L-3 38.6C14 37 34 36.4 52 37.6C64 38.4 80 39.6 103 38.6L103 48Z",
                mix(clair, duJour(0xFFF4EFC6.toInt()), 0.4f), 0.3f)
            mineral {
                pieceD("M-3 48L-3 41.6C16 40.4 40 40.2 60 41.2C76 42 90 42.8 103 42.4L103 48Z",
                    neutre(duJour(0xFFCFC6B0.toInt())), 0.35f)
            }
            plan(2)
            solNaturel(SolNaturel.BERGE)  // le sable de l'abri, semé de cailloux
            poser(DEVANT)
            // ---- La roche. Non pas une masse unique percée d'un trou,
            // qu'un litage seul ne sauvait pas du contreplaqué, mais trois
            // découpes étagées comme au théâtre : la paroi du fond, la
            // voûte qui avance par-dessus nos têtes, le pilier qui ferme à
            // droite. Chacune a sa valeur, sa tranche de kraft et son
            // litage, et ce sont ces ruptures qui creusent l'abri.
            val gres = neutre(duJour(0xFFB5A98F.toInt()))
            val fond = sombre(gres, 0.32f)
            val voute = sombre(gres, 0.62f)
            val jour = mix(gres, 0xFFFFFFFF.toInt(), 0.16f)
            val mousse = nappe(duJour(0xFF7C9A48.toInt()), 0.5f, VERT_CALME)
            mineral {
                val paroi = cadre(TROU_BAERDREF)
                piece(paroi, fond)
                strates(paroi, 10f, 59f, fond, 198, 4.6f, 0.03f)
                // La lèvre de la voûte est cassée, pas arrondie : un arc
                // lisse parallèle au trou faisait un arc-en-ciel de deux
                // bandes, et le tableau sombre que j'avais ajouté dessous
                // n'y ajoutait qu'un troisième cerne.
                val plafond = chemin("M-3 -3L103 -3L103 20L94 24.6L86 23.4L78 25.6L70 20.6L62 13.6" +
                    "L52 10.4L40 9.6L30 12.4L20 12L12 16.2L4 18.6L-3 26Z")
                piece(plafond, voute)
                strates(plafond, -4f, 25f, voute, 199, 6.4f, 0.045f)
                // Les traînées humides sous la voûte : le grès suinte, et
                // sans elles le plafond est une bande unie.
                for (k in 0 until 11) {
                    val tx = 2f + k * 9f + r() * 4f
                    ligne(tx, r() * 4f, tx + 0.8f, 8f + r() * 9f, sombre(voute, 0.4f), 0.5f, 0.5f)
                }
                // Le pilier de droite, le seul morceau que le soleil prend
                // de côté : c'est lui que la colonne libre de la carte
                // ouverte montre en entier, et il ferme le trou à droite.
                val pilier = chemin("M66 58.7L68 21C72 24.4 78 27 86 28C94 29 99 28.4 104 26.6L104 58.7Z")
                piece(pilier, jour)
                // Le grès du Mullerthal se débite en gros blocs, et le
                // pilier en est un empilement : un litage de plus y aurait
                // refait le même store que sur la paroi du fond.
                for ((i, d) in BLOCS_BAERDREF.withIndex()) {
                    val v = VALEURS_BAERDREF[i]
                    forme(d, if (v < 0f) sombre(jour, -v * 0.45f)
                             else mix(jour, 0xFFFFFFFF.toInt(), v * 0.34f), 1f)
                }
                for (d in JOINTS_BAERDREF) trait(d, sombre(gres, 0.65f), 0.5f, 0.7f)
                // Le creux entre le pilier et la paroi, à contre-jour.
                forme("M66 58.7L68 21C70 23.4 72 25 74.6 26.4L73 58.7Z", sombre(gres, 0.55f), 0.45f)
                // Les blocs tombés : sur la photo ils ferment le bas de
                // l'abri, clairs sur le sable, et leur face à l'ombre dit
                // qu'ils sont devant la paroi.
                pieceD("M82 58.7C81 49.2 87 44.6 94 44.6C100.6 44.6 104 49.2 104 58.7Z", mix(gres, 0xFFFFFFFF.toInt(), 0.26f))
                forme("M96 45.2C101 46.6 104 51.2 104 58.7L97 58.7Z", sombre(gres, 0.32f), 0.5f)
                pieceD("M-3 58.7C-3 51.6 2 48.6 7 49.6C11 50.6 12.4 54.6 12 58.7Z", sombre(gres, 0.16f))
            }
            // ---- La mousse tient les lèvres du grès, et au bord haut de la
            // photo une retombée de verdure passe par-dessus l'abri.
            pieceD("M-3 -3L40 -3C38.6 3 30 7 19 7.4C10 7.8 2 6.4 -3 4.4Z", mousse, 0.3f)
            for (k in 0 until 9) {
                val mx = -2f + k * 5f + r() * 3f
                touffe(if (k % 3 == 0) mix(mousse, clair, 0.35f) else mousse,
                    mx, 3f + r() * 4f, 1.2f + r() * 1.4f, mx + 1.8f, 2f + r() * 4f, 1f + r() * 1.2f)
            }
            touffe(mousse, 85f, 43.8f, 2.2f, 91f, 42.8f, 2.6f, 97f, 44f, 2.2f)
            touffe(mousse, 0f, 49f, 1.8f, 5f, 48.4f, 1.6f)
            if (P.nuit) {
                // La nuit, le dehors est la seule lumière : la lune pose une
                // flaque claire sur le sable au droit de l'ouverture. Sans
                // elle l'abri n'est qu'une masse bleue de plus. Au soir, qui
                // est [allume] lui aussi, cette flaque montait dans le trou
                // et y faisait une fumée orange : elle est réservée à la nuit
                // et reste basse, sur le sable.
                halo(26f, 56f, 22f, 0xFFBFD4F0.toInt(), 0.4f)
                halo(22f, 58f, 13f, 0xFFD8E4F6.toInt(), 0.4f)
                halo(58f, 57f, 12f, 0xFFBFD4F0.toInt(), 0.2f)
            }
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
        /**
         * Un mouton des Ardennes, de taille [s], tourné à droite ou non, la
         * tête d'autant plus basse que [broute] approche de 1.
         *
         * Blanc à tête noire et à quatre pattes noires, et la laine lui fait
         * un dos bosselé : c'est ce qui le distingue d'un galet pâle posé sur
         * l'herbe, qui est ce qu'il était. Quatre pattes et non deux, parce
         * qu'une bête de profil en montre toujours au moins trois.
         */
        fun mouton(x: Float, y: Float, s: Float, broute: Float, versDroite: Boolean = true) {
            val laine = duJour(0xFFF1ECE0.toInt())
            val tete = duJour(0xFF2E2A28.toInt())
            val k = if (versDroite) 1f else -1f
            for (px in floatArrayOf(-1.5f, -0.9f, 1f, 1.6f))
                ligne(x + px * s, y + 0.6f * s, x + px * s * 1.06f, y + 2.1f * s, tete, 0.26f * s)
            // Le dos bosselé : trois flocons de laine découpés d'une pièce.
            touffe(laine, x - 1.1f * s, y - 0.2f * s, 1.25f * s,
                x + 0.1f * s, y - 0.55f * s, 1.35f * s,
                x + 1.3f * s, y - 0.1f * s, 1.2f * s,
                x, y + 0.4f * s, 1.3f * s)
            // La queue, du côté opposé à la tête.
            ovalePapier(x - k * 2.1f * s, y - 0.2f * s, 0.45f * s, 0.6f * s, laine, 0f)
            // La tête, et l'oreille qui pend.
            val hx = x + k * 2.2f * s
            val hy = y - 0.7f * s + broute * 1.9f * s
            ovalePapier(hx, hy, 0.85f * s, 0.62f * s, tete, 0f)
            ovalePapier(hx - k * 0.7f * s, hy - 0.35f * s, 0.4f * s, 0.26f * s, tete, 0f)
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

        // ---- Felsmillen, la paroi de grès et le caillebotis (photo du propriétaire)

        /**
         * Ce que dit la photo, et qu'aucune maquette n'avait :
         *
         * 1. **La gorge n'a pas de ciel.** La futaie se referme au-dessus ;
         *    la seule clarté est le fond du vallon, loin, au bout du chemin.
         * 2. **La paroi est litée, et en surplomb.** Des bancs horizontaux
         *    que l'eau a creusés par en dessous : le haut avance au-dessus du
         *    pied, et c'est l'ombre portée sous chaque corniche qui creuse la
         *    roche. Sans elle, ce n'est qu'un mur.
         * 3. **Elle fuit vers la droite** : son arête descend du coin haut
         *    gauche vers le milieu de la fenêtre, et ses lits penchent avec.
         * 4. **La mousse est la pièce la plus claire du lieu**, un vert franc
         *    qui tient tout le pied de la paroi et monte sur ses vires.
         * 5. **Le caillebotis se tord** : large au premier plan, il bombe vers
         *    la droite avant de repartir dans la forêt en se rétrécissant. Ses
         *    lames sont transversales, et sa tranche se voit du côté du jour.
         *
         * La carte ouverte ne laisse que les deux colonnes et la bande du
         * haut : la paroi tient celle de gauche sur toute sa hauteur, le
         * caillebotis monte dans celle de droite, la futaie ferme le haut, et
         * le fond de la gorge passe derrière la plaque comme il disparaît
         * dans la forêt sur la photo.
         */
        fun felsmillen() {
            val r = Alea(225)
            // ---- Le fond du vallon, la seule clarté de la photo.
            // Le fond est boisé d'abord, la clarté ensuite : mélangé au bas du
            // ciel, le vert du lointain sortait jaune et faisait un rideau.
            val lointain = nappe(duJour(0xFF4F6B2E.toInt()), 0.45f, VERT_CALME)
            lisiere(-2f, 102f, { x -> 10f + 3.4f * sin(x * 0.09f) }, 2.4f, 3.8f, 226, lointain)
            // La trouée du fond du vallon : une bande, pas un pan. En grand,
            // elle se lisait comme un mur de lumière derrière les arbres.
            val jour = mix(lointain, duJour(0xFFEFF2C4.toInt()), if (P.nuit) 0.08f else 0.3f)
            pieceD("M-3 40L-3 27C16 24.6 38 24 60 25.6C78 27 92 28 103 27L103 40Z", jour, 0.3f)
            // Les fûts du fond : fins et nombreux, à peine détachés. Plus
            // larges, ils feraient une palissade au lieu d'une futaie.
            val futLoin = mix(neutre(duJour(0xFFCCC2AC.toInt())), lointain, 0.4f)
            for (k in 0 until 15) {
                val tx = -3f + k * 7.2f + r() * 3.6f
                ligne(tx, 6f, tx + (r() - 0.5f) * 2f, 42f, futLoin, 0.22f + r() * 0.28f, 0.7f)
            }
            plan(1)
            val feuillu = vert(0.7f)
            // Le sous-bois : sans lui, ce qui reste entre deux fûts est le
            // fond clair, et la gorge se lit comme un pré.
            lisiere(-2f, 102f, { x -> 34f + 3f * sin(x * 0.13f + 1.4f) }, 2f, 3.2f, 227, sombre(vert(0.5f), 0.3f))
            // Les fûts proches, en vraies découpes : le tronc, sa joue à
            // l'ombre, et le pied qui s'enfonce dans la mousse du plan devant.
            val fut = neutre(duJour(0xFFC9BFA8.toInt()))
            for (tx in floatArrayOf(36f, 53f, 69f, 81f, 93f, 99.5f)) {
                val w = 1.3f + r() * 1.1f
                val pente = (r() - 0.5f) * 3f
                pieceD("M$tx -3L${tx + w} -3L${tx + w + pente} 47L${tx + pente} 47Z", fut)
                forme("M${tx + w * 0.55f} -3L${tx + w} -3L${tx + w + pente} 47L${tx + w * 0.55f + pente} 47Z",
                    sombre(fut, 0.32f), 0.5f)
            }
            // La voûte de feuillage : c'est elle qui fait la gorge. Le ciel
            // ouvert en haut de la fenêtre nous mettait dans une clairière.
            pieceD("M-3 -3L103 -3L103 7C94 12 86 8 77 11C68 14 59 9 50 12C41 15 32 9 23 12C15 14.6 6 12 -3 8Z",
                sombre(feuillu, 0.42f))
            for (k in 0 until 12) {
                val fx = -3f + r() * 106f
                val fy = 1f + r() * 9f
                touffe(if (r() < 0.45f) feuillu else sombre(feuillu, 0.42f),
                    fx, fy, 2f + r() * 2.4f,
                    fx + 2.6f, fy + 1.6f, 1.8f + r() * 1.8f,
                    fx - 2.4f, fy + 1.2f, 1.6f + r() * 1.8f)
            }
            plan(2)
            solNaturel(SolNaturel.MOUSSE)  // le tapis du fond de gorge
            poser(DEVANT)
            // ---- La mousse, la pièce la plus claire du lieu : elle monte au
            // pied de la paroi et passe sous le caillebotis.
            val mousse = nappe(duJour(0xFF7FA83C.toInt()), 0.5f, VERT_CALME)
            val mousseClaire = mix(mousse, duJour(0xFFD6EE78.toInt()), if (P.nuit) 0.08f else 0.3f)
            lisiere(-2f, 102f, { x -> 46f - x * 0.07f }, 2.2f, 3.6f, 228, mousse)
            for (k in 0 until 9) {
                val mx = -2f + r() * 104f
                val my = 44f + r() * 13f
                touffe(mousseClaire, mx, my, 1.3f + r() * 1.2f,
                    mx + 2.2f, my + 0.5f, 1f + r() * 1f, mx + 4.2f, my - 0.2f, 0.8f + r() * 0.9f)
            }
            // Un bloc tombé au bord du chemin, dans la colonne de droite.
            val gres = neutre(duJour(0xFFB3A78C.toInt()))
            val creux = sombre(gres, 0.42f)
            mineral { pieceD("M90 46C91.4 41.6 96 39.6 100 40.4C103 41 104.6 43 105 46Z", mix(gres, 0xFFFFFFFF.toInt(), 0.1f)) }
            touffe(mousseClaire, 92f, 40.6f, 1.4f, 97f, 39.6f, 1.6f, 102f, 41f, 1.2f)
            // ---- La paroi de grès, en trois découpes étagées : la paroi du
            // fond, la corniche qui avance par-dessus, le gros banc qui bombe
            // au pied. D'un seul tenant, litée ou non, elle se lirait comme
            // un panneau de contreplaqué.
            val vire = mix(gres, 0xFFFFFFFF.toInt(), 0.14f)
            val banc = mix(gres, 0xFFFFFFFF.toInt(), 0.08f)
            mineral {
                val paroi = chemin("M-4 -3L20 -3C24 2 27 6 29 10C33 13 35 17 34 21C38 24 42 28 42 33C42.6 38 40 43 37 46C28 48 16 50 4 52L-4 53Z")
                val fond = sombre(gres, 0.18f)
                piece(paroi, fond)
                strates(paroi, -2f, 52f, fond, 229, 3.8f, 0.1f)
                val corniche = chemin("M-4 -3L14 -3C19 2 24 7 28 12C22 15 13 16 5 15C0 14.4 -2 13.6 -4 12.6Z")
                piece(corniche, vire)
                strates(corniche, -3f, 15f, vire, 230, 3f, 0.12f)
                // L'ombre que la corniche porte sous elle : c'est elle qui
                // creuse la roche, et non un trait de plus sur la paroi.
                forme("M-4 12.6C-2 13.6 0 14.4 5 15C13 16 22 15 28 12C26.6 14.4 24 16.2 20.4 17.6C12.6 20 3 19.6 -4 17Z",
                    creux, 0.72f)
                // Un second surplomb à mi-hauteur : la paroi s'étage, elle ne
                // descend pas d'un trait depuis la corniche jusqu'au pied.
                forme("M-4 25.4C4 26.4 14 27 22 26C28 25.2 32 24 35 22.6C34 25.2 31 27.4 26.6 28.8C18 31.4 4 31.4 -4 29.6Z",
                    creux, 0.5f)
                val pied = chemin("M-4 33C5 32.4 13 35 18 40C21.4 43.4 22 48 20 52L-4 54Z")
                piece(pied, banc)
                strates(pied, 32f, 54f, banc, 231, 3.2f, 0.14f)
                forme("M13.6 36C18 39.6 21.6 44 22 50C21 51.6 19 52.6 16 53L18.6 44Z", creux, 0.4f)
            }
            // La mousse tient les vires : des traînées posées sur l'arête des
            // bancs. Semée au hasard, elle mettrait des confettis verts sur
            // une roche qui n'en a pas.
            // Une traînée suit l'arête d'un banc : des ovales aplatis qui se
            // recouvrent. En ronds, même serrés, la mousse faisait un collier
            // de perles vertes sur une roche qui n'en porte pas.
            for (vy in floatArrayOf(14f, 30f)) {
                val x0 = -4f + r() * 4f
                val l = 16f + r() * 12f
                val y0 = vy + r() * 2f
                val t = Path()
                for (i in 0 until 10) {
                    val u = i / 9f
                    val cx = x0 + l * u
                    val cy = y0 + u * 3.4f
                    t.addOval(cx - 2.6f, cy - 0.6f - r() * 0.5f, cx + 2.6f, cy + 0.7f + r() * 0.6f, Path.Direction.CW)
                }
                piece(t, mousse)
            }
            pieceD("M-4 -3L24 -3C22.6 3.6 15 7.6 6 8C0 8.2 -2 7 -4 6Z", mousse, 0.3f)
            // ---- Le caillebotis. Il entre par le bas de la colonne de
            // droite, bombe vers le bord, puis repart dans la forêt : c'est
            // ce coup de reins qui le fait vivre, un ruban droit ne dirait
            // rien. Les lames sont transversales et se serrent au loin.
            // Le bois passe par [neutre] comme toute matière bâtie : sans lui,
            // le brun vire à l'orange et le tablier devient un tapis.
            val bois = neutre(duJour(0xFF9C6A46.toInt()))
            fun recul(y: Float) = ((DEVANT - y) / 26.5f).coerceIn(0f, 1f)
            fun axe(y: Float): Float { val t = recul(y); return 86f + 12f * t - 20f * t * t }
            fun demi(y: Float): Float = 14f - 10.5f * recul(y)
            val tablier = Path()
            var y = DEVANT
            tablier.moveTo(axe(y) - demi(y), y)
            while (y > 32f) { y -= 1.3f; tablier.lineTo(axe(y) - demi(y), y) }
            while (y < DEVANT) { tablier.lineTo(axe(y) + demi(y), y); y += 1.3f }
            tablier.lineTo(axe(DEVANT) + demi(DEVANT), DEVANT)
            tablier.close()
            piece(tablier, bois)
            lames({ yy -> axe(yy) - demi(yy) }, { yy -> axe(yy) + demi(yy) }, DEVANT, 33f, sombre(bois, 0.5f))
            // La tranche du tablier, du côté du jour : elle épaissit en
            // venant vers nous, et c'est ce qui le pose sur la mousse.
            var yb = 33f
            while (yb < DEVANT) {
                val y2 = min(DEVANT, yb + 1.6f)
                ligne(axe(yb) + demi(yb), yb, axe(y2) + demi(y2), y2,
                    sombre(bois, 0.45f), 0.25f + (yb - 33f) / 25.5f * 0.9f)
                yb = y2
            }
            // Le caillebotis ne s'arrête pas en l'air : il passe derrière un
            // buisson, comme il disparaît dans la forêt sur la photo.
            touffe(sombre(vert(0.5f), 0.26f), 77f, 33.6f, 3.2f, 83f, 32.4f, 2.8f, 88.4f, 33.6f, 2.2f)
            touffe(sombre(vert(0.58f), 0.12f), -3f, 54f, 4f, 4f, 56f, 3.4f, 12f, 57.4f, 3f)
            touffe(vert(0.66f), 99f, 56f, 4f, 104f, 54f, 3.4f)
            c.restore()
        }

        // ---- Ourdall, la promenade du lac de l'Our (photos du propriétaire)

        /**
         * Ce que disent les photos, et qu'aucune maquette n'avait :
         *
         * 1. **Le lac est le sujet autant que le caillebotis.** Une grande
         *    eau calme et pâle, ouverte, et une longue colline boisée qui la
         *    ferme au fond : c'est cela, la promenade de l'Ourdall.
         * 2. **Le garde-corps est un grillage**, tendu entre des poteaux sous
         *    une main courante épaisse. On voit le lac au travers : il se
         *    dessine fil par fil, jamais en panneau plein.
         * 3. **Le bois est gris**, délavé par la pluie, et non brun verni.
         * 4. **Les pylônes de la ligne passent sur la crête**, à gauche : le
         *    lac de l'Our est la retenue de pompage de Vianden, et ce sont eux
         *    qui le disent.
         * 5. **Un arbre surplombe la promenade** et occupe tout le coin haut
         *    droit, au-dessus du caillebotis.
         *
         * Sur la carte ouverte, le caillebotis et ses deux garde-corps
         * tiennent la colonne de droite, l'arbre les coiffe dans la bande du
         * haut, et tout le reste est le lac, sa crête et ses pylônes.
         */
        fun ourdall() {
            val r = Alea(235)
            // ---- La crête boisée du fond, et le éperon qui la double plus près.
            val colline = nappe(duJour(0xFF3E5C2A.toInt()), 0.5f, VERT_CALME)
            lisiere(-2f, 102f, { x -> 23f + 2.4f * sin(x * 0.055f) - x * 0.025f }, 2.2f, 3.4f, 236,
                mix(colline, P.loin, 0.2f))
            // Les pylônes et leur ligne : petits, sur la crête, dans la
            // colonne de gauche, là où la carte ouverte les montre.
            val acier = neutre(duJour(0xFF8A9099.toInt()))
            mineral {
                for (px in floatArrayOf(9f, 23f)) pylone(px, 23.6f - px * 0.025f, 14.4f - px * 0.025f, 2.6f, sombre(acier, 0.35f))
                ligne(9f, 16.6f, 23f, 16.2f, acier, 0.12f, 0.5f)
                ligne(23f, 16.2f, 42f, 18.4f, acier, 0.12f, 0.4f)
            }
            lisiere(-2f, 102f, { x -> 28f + 2f * sin(x * 0.09f + 1.2f) - x * 0.02f }, 2f, 3f, 237, colline)
            plan(1)
            // ---- Le lac, la pièce la plus large de la carte.
            val lac = mix(P.eau, neutre(duJour(0xFF4E7A74.toInt())), 0.55f)
            pieceD("M-3 67L-3 33.4C20 32.2 50 32 78 32.8C90 33.2 98 33.6 103 33.2L103 67Z", lac, 0.4f)
            // Le reflet de la colline sous sa rive : sans lui l'eau est un
            // aplat, et la rive ne tient à rien.
            forme("M-3 39.4L-3 34C20 32.8 50 32.6 78 33.4C90 33.8 98 34.2 103 33.8L103 38.6C70 39.8 30 40 -3 39.4Z",
                sombre(lac, 0.3f), 0.3f)
            // Les traînées de lumière : horizontales, et plus longues en
            // approchant. C'est ce qui couche l'eau à plat.
            val brillance = mix(lac, 0xFFFFFFFF.toInt(), if (P.nuit) 0.14f else 0.42f)
            for (k in 0 until 16) {
                val yy = 35f + r() * 22f
                val t = (yy - 35f) / 22f
                rect(-2f + r() * 78f, yy, 4f + r() * (6f + t * 14f), 0.3f + t * 0.3f, brillance, 0.7f)
            }
            plan(2)
            poser(DEVANT)
            // ---- Le caillebotis. Il entre par le coin bas droit et
            // traverse **en biais** vers le fond.
            //
            // Deux géométries ont été essayées avant celle-ci, et toutes
            // deux perdaient le garde-corps, qui est le sujet :
            //
            // - fuyant vers le milieu, ses deux garde-corps convergent
            //   derrière la plaque et il ne reste qu'un ponton ;
            // - dressé le long du bord droit, « monter » et « s'éloigner »
            //   deviennent la même direction : un poteau vertical se couche
            //   sur l'arête du tablier, la main courante retombe dessus, et
            //   le garde-corps se réduit à un trait.
            //
            // En biais, l'arête est oblique et la hauteur du garde-corps s'en
            // détache : dans la colonne libre on voit une bande de grillage
            // haute de dix unités, ses poteaux et sa main courante.
            val bois = neutre(duJour(0xFFA69C8C.toInt()))
            // Le bord intérieur est remonté : le garde-corps extérieur, qui
            // se lève par-dessus les lames, en couvrait presque toutes.
            fun bordInterne(u: Float) = floatArrayOf(104f - 46f * u, 40.6f - 11.2f * u)
            fun bordExterne(u: Float) = floatArrayOf(104f - 44f * u, 62f - 25.5f * u)
            // La rive sous la promenade : sans elle le caillebotis a de l'eau
            // des deux côtés et se lit comme un pont jeté sur le lac.
            val talus = sombre(vert(0.58f), 0.12f)
            val rive = chemin("M104 68L104 61.4C92 59.6 80 54 70 45C65 40.4 61 36.4 57.6 33.6" +
                "C54.6 38.6 52.4 44.6 50.4 50.6C48.8 55.6 47 61.4 46 68Z")
            piece(rive, sombre(talus, 0.14f))
            // Le bord de l'eau, mouillé : il casse la diagonale, qui sans lui
            // se lit comme un mur jeté en travers du lac.
            forme("M57.6 33.6C61 36.4 65 40.4 70 45C80 54 92 59.6 104 61.4L104 63.6C90 62 78 56.4 68 47.4C63 43 59 39 56.6 36.4Z",
                sombre(talus, 0.42f), 0.55f)
            c.save(); c.clipPath(rive)
            releve?.save(); releve?.clipPath(rive)
            for (k in 0 until 12) {
                val bx = 54f + r() * 52f
                val by = 38f + r() * 28f
                val t = r()
                touffe(if (t < 0.34f) mix(talus, duJour(0xFFDCEE8E.toInt()), 0.3f)
                       else if (t < 0.72f) vert(0.66f) else sombre(talus, 0.3f),
                    bx, by, 1.2f + r() * 1.8f, bx + 2.2f, by + 1f, 1f + r() * 1.5f)
            }
            releve?.restore(); c.restore()
            fun hautGarde(u: Float) = 10f - 7f * u
            val tablier = Path()
            var a = bordInterne(0f)
            tablier.moveTo(a[0], a[1])
            var u = 0f
            while (u < 1f) { u = min(1f, u + 0.05f); a = bordInterne(u); tablier.lineTo(a[0], a[1]) }
            while (u > 0f) { a = bordExterne(u); tablier.lineTo(a[0], a[1]); u -= 0.05f }
            a = bordExterne(0f)
            tablier.lineTo(a[0], a[1])
            tablier.close()
            piece(tablier, bois)
            // Les lames, transversales et plus serrées au loin.
            for (k in 1..18) {
                val uu = (k / 18f).pow(1.5f)
                val g = bordInterne(uu); val d = bordExterne(uu)
                ligne(g[0], g[1], d[0], d[1], sombre(bois, 0.5f), 0.62f - 0.46f * uu, 0.9f)
            }
            /**
             * Un garde-corps de la promenade, le long du [bord] du tablier :
             * l'ombre qu'il porte sur les lames, le grillage fil par fil, les
             * poteaux, puis la main courante et son arête au jour.
             *
             * Le grillage ne se peint pas en panneau : c'est à travers lui
             * qu'on voit le lac, et une pièce de papier le boucherait.
             */
            fun gardeCorps(bord: (Float) -> FloatArray, surLeTablier: Boolean) {
                // Celui du bord intérieur se lève au-dessus du lac, celui du
                // bord extérieur par-dessus les lames : l'ombre portée est ce
                // qui pose le second, qui sans elle flotte sur le tablier.
                if (surLeTablier) {
                    var v = 0f
                    while (v < 1f) {
                        val b = bord(v); val c2 = bord(min(1f, v + 0.04f))
                        ligne(b[0], b[1], c2[0] + hautGarde(v) * 0.7f, c2[1],
                            sombre(bois, 0.26f), 0.6f, 0.22f)
                        v += 0.04f
                    }
                }
                val fil = sombre(bois, 0.58f)
                var v = 0f
                while (v < 1f) {
                    val b = bord(v); val c2 = bord(min(1f, v + 0.035f))
                    ligne(b[0], b[1] - hautGarde(v), c2[0], c2[1], fil, 0.12f, 0.45f)
                    ligne(b[0], b[1], c2[0], c2[1] - hautGarde(v), fil, 0.12f, 0.45f)
                    v += 0.035f
                }
                v = 0f
                while (v < 1f) {
                    val b = bord(v)
                    ligne(b[0], b[1], b[0], b[1] - hautGarde(v), sombre(bois, 0.52f), 0.36f - 0.2f * v)
                    v += 0.1f + 0.05f * v
                }
                val main = Path()
                var b = bord(0f)
                main.moveTo(b[0], b[1] - hautGarde(0f))
                v = 0f
                while (v < 1f) { v = min(1f, v + 0.05f); b = bord(v); main.lineTo(b[0], b[1] - hautGarde(v)) }
                while (v > 0f) {
                    b = bord(v); main.lineTo(b[0], b[1] - hautGarde(v) + 0.5f + (1f - v) * 0.9f)
                    v -= 0.05f
                }
                b = bord(0f)
                main.lineTo(b[0], b[1] - hautGarde(0f) + 1.4f)
                main.close()
                piece(main, sombre(bois, 0.3f), 0.9f)
                v = 0f
                while (v < 1f) {
                    b = bord(v); val c2 = bord(min(1f, v + 0.05f))
                    ligne(b[0], b[1] - hautGarde(v), c2[0], c2[1] - hautGarde(min(1f, v + 0.05f)),
                        mix(bois, 0xFFFFFFFF.toInt(), 0.22f), 0.22f, 0.8f)
                    v += 0.05f
                }
            }
            gardeCorps({ v -> bordInterne(v) }, false)
            gardeCorps({ v -> bordExterne(v) }, true)
            // Le bosquet où la promenade s'enfonce, au bout du caillebotis.
            touffe(talus, 54f, 32f, 4.4f, 60f, 34f, 3.6f, 49f, 34.6f, 3.4f, 64f, 36.6f, 2.8f)
            touffe(vert(0.66f), 57f, 30.4f, 2.6f, 51.6f, 31.6f, 2.2f)
            // ---- L'arbre qui surplombe la promenade, dans le coin haut droit.
            val feuillage = vert(0.72f)
            trait("M104 1.6C97 5 92.6 11 90.6 19", sombre(bois, 0.5f), 0.7f)
            for (k in 0 until 16) {
                val fx = 74f + r() * 32f
                val fy = -3f + r() * 17f
                val t = r()
                touffe(if (t < 0.32f) mix(feuillage, duJour(0xFFE6F2A0.toInt()), 0.3f)
                       else if (t < 0.76f) feuillage else sombre(feuillage, 0.34f),
                    fx, fy, 2f + r() * 2.6f,
                    fx + 2.8f, fy + 1.6f, 1.8f + r() * 2f,
                    fx - 2.6f, fy + 1.4f, 1.6f + r() * 2f)
            }
            // Les roseaux de la rive, en bas à gauche.
            touffe(sombre(vert(0.5f), 0.2f), -3f, 56f, 3.6f, 3f, 57.6f, 3f)
            c.restore()
        }

        // ---- Stauséi, le barrage de la Haute-Sûre (photos du propriétaire)

        /**
         * Ce que disent les photos, et qu'aucune maquette n'avait :
         *
         * 1. **Le barrage est une voûte**, un voile de béton tendu entre deux
         *    versants, et sa convexité regarde le lac. Vu d'aval, le milieu
         *    de la crête s'éloigne et remonte dans la fenêtre, les deux
         *    culées descendent vers nous : c'est cet arc qui le distingue
         *    d'un mur jeté en travers de la vallée.
         * 2. **Le béton est coulé par plots verticaux.** Ce sont ces joints
         *    de reprise, de la crête au pied, qui font la matière, avec les
         *    longues traînées sombres que l'eau laisse sous la crête.
         * 3. **La crête est une route** bordée d'un garde-corps ajouré : on
         *    voit le lac au travers, entre les poteaux.
         * 4. **Le lac est vert jade**, encaissé entre deux versants boisés
         *    très raides qui plongent jusqu'à l'eau, et il remonte la vallée
         *    en V jusqu'au fond de la fenêtre.
         * 5. **Une bande pâle de marnage** cerne le lac à la ligne d'eau :
         *    la terre caillouteuse que la baisse du plan d'eau découvre.
         *    C'est elle qui dit qu'on est devant une retenue et non devant un
         *    étang, et elle vaut pour tout le pourtour.
         * 6. **Le ciel n'est qu'une échancrure** entre les deux crêtes.
         *
         * Sur la carte ouverte, les deux bouts de la crête et les deux
         * culées tiennent les colonnes, le lac et ses versants la bande du
         * haut, et l'eau d'aval le bas.
         */
        fun stausei() {
            val r = Alea(245)
            // L'arc de la crête : le milieu s'éloigne, les deux culées
            // viennent vers nous, et la culée de droite est la plus proche —
            // c'est le point de vue de la photo, pris du versant de gauche.
            // Tout le reste en découle : plus on va à droite, plus le
            // tablier est large, plus le voile est haut et plus le lac
            // s'ouvre derrière lui.
            fun crete(x: Float) = 33f + 0.06f * (x - 50f) + 0.0030f * (x - 50f) * (x - 50f)
            fun tablier(x: Float) = 2.2f + (crete(x) - 33f) * 0.22f
            fun piedVoile(x: Float) = crete(x) + 6.5f + (crete(x) - 33f) * 0.42f
            // La ligne d'eau du lac, qui remonte la vallée : elle sort de
            // derrière le barrage vers x = 28, atteint le fond vers x = 58 et
            // redescend vers la rive droite. À gauche elle passe sous la
            // crête, où le barrage la cache : c'est ce qui donne au lieu son
            // asymétrie, celle de la photo.
            val bornes = floatArrayOf(-4f, 44f, 12f, 39f, 24f, 33f, 34f, 27.5f, 46f, 23.5f,
                60f, 21.5f, 74f, 22.5f, 88f, 25f, 104f, 27.5f)
            fun rive(x: Float): Float {
                var i = 0
                while (i < bornes.size - 4 && x > bornes[i + 2]) i += 2
                val t = ((x - bornes[i]) / (bornes[i + 2] - bornes[i])).coerceIn(0f, 1f)
                return bornes[i + 1] + (bornes[i + 3] - bornes[i + 1]) * t
            }
            // Le haut de la grève découverte : un liseré partout, et à
            // gauche un grand pan de terre nue, qui est ce que la photo
            // montre le plus franchement de la retenue.
            fun greveHaut(x: Float) = rive(x) - 0.9f - max(0f, 26f - x) * 0.55f
            /** Le ruban compris entre les courbes [haut] et [bas], de [x0] à [x1]. */
            fun ruban(x0: Float, x1: Float, haut: (Float) -> Float, bas: (Float) -> Float): Path {
                val t = Path()
                t.moveTo(x0, haut(x0))
                var x = x0
                while (x < x1) { x = min(x1, x + 2.5f); t.lineTo(x, haut(x)) }
                while (x > x0) { t.lineTo(x, bas(x)); x = max(x0, x - 2.5f) }
                t.lineTo(x0, bas(x0))
                t.close()
                return t
            }
            // ---- Le fond de la vallée : deux crêtes lointaines qui ferment
            // le V, la seconde plus franche que la première.
            lisiere(-3f, 103f, { x -> 15.6f + 2.2f * sin(x * 0.07f) }, 1.8f, 2.8f, 246,
                mix(nappe(duJour(0xFF40622C.toInt()), 0.38f, VERT_CALME), P.loin, 0.38f))
            lisiere(-3f, 103f, { x -> 19f + 1.6f * sin(x * 0.11f + 2f) }, 2f, 3f, 247,
                mix(nappe(duJour(0xFF3A5E26.toInt()), 0.44f, VERT_CALME), P.loin, 0.16f))
            plan(1)
            // ---- Les deux versants, qui plongent jusqu'à l'eau. Trois
            // rideaux par rive, du clair au fond au sombre devant : à nuance
            // égale deux rideaux n'en font qu'un et la vallée redevient un
            // aplat. Chacun s'arrête là où sa cime rencontre la ligne d'eau,
            // sans quoi il se peindrait sous le lac pour rien.
            val versant = nappe(duJour(0xFF36602A.toInt()), 0.5f, VERT_CALME)
            lisiere(-4f, 44f, { x -> 0.5f + (x + 4f) * 0.406f }, 2.2f, 3.6f, 248, mix(versant, P.loin, 0.2f))
            lisiere(54f, 104f, { x -> 20f - (x - 54f) * 0.36f }, 2.2f, 3.6f, 249, mix(versant, P.loin, 0.2f))
            lisiere(-4f, 38f, { x -> 6f + (x + 4f) * 0.381f }, 2.6f, 4.2f, 250, versant)
            lisiere(68f, 104f, { x -> 21f - (x - 68f) * 0.361f }, 2.6f, 4.2f, 251, versant)
            lisiere(-4f, 30f, { x -> 12f + (x + 4f) * 0.412f }, 3f, 5f, 252, sombre(versant, 0.24f))
            lisiere(84f, 104f, { x -> 23f - (x - 84f) * 0.45f }, 3f, 5f, 253, sombre(versant, 0.24f))
            // Des touffes claires dans les pentes : sans elles, un versant
            // entier n'est qu'une masse verte.
            for (k in 0 until 16) {
                val gauche = k % 2 == 0
                val fx = if (gauche) -4f + r() * 34f else 70f + r() * 36f
                val fy = (if (gauche) 2f + fx * 0.4f else 21f - (fx - 70f) * 0.36f) + r() * 7f
                val t = r()
                touffe(if (t < 0.34f) mix(versant, duJour(0xFFDCEE8E.toInt()), 0.26f)
                       else if (t < 0.7f) versant else sombre(versant, 0.34f),
                    fx, fy, 1.8f + r() * 2.2f,
                    fx + 2.6f, fy + 1.4f, 1.4f + r() * 1.8f,
                    fx - 2.4f, fy + 1.2f, 1.3f + r() * 1.6f)
            }
            // ---- Le marnage : la grève pâle que la retenue découvre, tout
            // le long de la ligne d'eau. Elle est minérale, donc l'hiver ne
            // la blanchit pas et le printemps n'y sème rien.
            val greve = neutre(duJour(0xFFC3B49A.toInt()))
            mineral {
                piece(ruban(-4f, 104f, { x -> greveHaut(x) }, { x -> rive(x) + 0.3f }), greve, 0.5f)
                // Le pan de gauche est une pente, pas un aplat : son ombre le
                // long de l'eau, et son arête au jour en haut.
                forme(ruban(-4f, 26f, { x -> rive(x) - 2f }, { x -> rive(x) + 0.3f }), sombre(greve, 0.26f), 0.5f)
                forme(ruban(-4f, 24f, { x -> greveHaut(x) }, { x -> greveHaut(x) + 1.2f }),
                    mix(greve, 0xFFFFFFFF.toInt(), 0.22f), 0.6f)
                for (k in 0 until 28) {
                    val gx = -4f + r() * 108f
                    val gy = greveHaut(gx) + 0.6f + r() * (rive(gx) - greveHaut(gx) - 0.6f)
                    rond(gx, gy, 0.2f + r() * 0.34f,
                        if (r() < 0.5f) sombre(greve, 0.3f) else mix(greve, 0xFFFFFFFF.toInt(), 0.2f), 0.8f)
                }
            }
            // ---- Le lac, vert jade. Il descend bien au-dessous du barrage :
            // les plans glissent l'un sur l'autre, et un lac arrêté sur la
            // crête découvrirait le ciel sous elle.
            // Le jade de la retenue. Il passe par [duJour] mais **pas** par
            // [neutre] : le vert de l'eau est une teinte, pas une matière
            // bâtie, et neutralisé il tournait au mauve.
            //
            // Et il **cède le pas au ciel le soir et la nuit** ([P.allume]).
            // Une eau se lit à ce qu'elle reflète : de jour c'est le vert du
            // fond, au couchant c'est le ciel. Forcé au jade aux quatre
            // heures, le lac du soir devenait une prairie de plus entre deux
            // versants verts, et c'est exactement ce que la palette de
            // l'heure disait déjà avec son eau rose.
            //
            // La saturation est tenue comme celle de la végétation ([nappe]) :
            // le ravivage des palettes vives sort un vert d'eau franc en vert
            // pomme, et le lac devenait la chose la plus criarde de la carte,
            // devant le mot qui est posé dessus.
            fun calmer(col: Int) = mix(col, neutre(col), 0.45f)
            val lac = calmer(mix(P.eau, duJour(0xFF2E8269.toInt()), if (P.allume) 0.44f else 0.72f))
            piece(ruban(-4f, 104f, { x -> rive(x) + 0.2f }, { 60f }), lac, 0.4f)
            // Le reflet des versants sous leur rive : sans lui l'eau est un
            // aplat et la grève ne tient à rien.
            forme(ruban(-4f, 104f, { x -> rive(x) + 0.2f }, { x -> rive(x) + 2.8f }), sombre(lac, 0.32f), 0.32f)
            // Les traînées de lumière, couchées et plus longues en approchant.
            val brillance = mix(lac, 0xFFFFFFFF.toInt(), if (P.nuit) 0.16f else 0.48f)
            for (k in 0 until 18) {
                val lx = 30f + r() * 74f
                val ly = rive(lx) + 3.4f + r() * (crete(lx) - rive(lx) - 7f).coerceAtLeast(1f)
                val t = (ly - rive(lx)) / 14f
                rect(lx, ly, 3f + r() * (4f + t * 10f), 0.3f, brillance, 0.7f)
            }
            plan(2)
            solNaturel(SolNaturel.BERGE)  // la grève d'aval, sous la fenêtre
            poser(DEVANT)
            // ---- Le voile de béton, de la crête à son pied.
            val beton = neutre(duJour(0xFFB7AF9C.toInt()))
            val voile = ruban(-4f, 104f, { x -> crete(x) }, { x -> piedVoile(x) })
            mineral {
                piece(voile, sombre(beton, 0.14f))
                c.save(); c.clipPath(voile)
                releve?.save(); releve?.clipPath(voile)
                // Les plots de bétonnage : chaque levée a son ton et son
                // joint de reprise, de la crête au pied. C'est cet appareil
                // vertical qui fait le barrage ; un aplat beige bordé de deux
                // traits ne ferait que du carton peint.
                var px = -5f
                while (px < 105f) {
                    // Les levées sont plus larges en venant vers nous.
                    val l = (3.4f + r() * 1.8f) * (1f + (crete(px) - 33f) * 0.06f)
                    val t = r()
                    forme(ruban(px, px + l, { x -> crete(x) - 1f }, { x -> piedVoile(x) + 1f }),
                        if (t < 0.45f) mix(beton, 0xFFFFFFFF.toInt(), 0.06f + t * 0.34f)
                        else sombre(beton, 0.05f + (t - 0.45f) * 0.48f), 0.92f)
                    ligne(px, crete(px) - 1.5f, px, piedVoile(px) + 1.5f, sombre(beton, 0.5f), 0.22f, 0.85f)
                    px += l
                }
                // Les traînées que l'eau laisse sous la crête, et l'ombre du
                // pied que l'eau d'aval mouille.
                for (k in 0 until 11) {
                    val tx = -3f + r() * 106f
                    val h = (piedVoile(tx) - crete(tx)) * (0.4f + r() * 0.58f)
                    ligne(tx, crete(tx) + 0.6f, tx + (r() - 0.5f) * 1.4f, crete(tx) + 0.6f + h,
                        sombre(beton, 0.5f), 0.4f + r() * 0.9f, 0.34f)
                }
                // Le voile s'assombrit en descendant, et son pied est mouillé.
                forme(ruban(-4f, 104f, { x -> crete(x) + (piedVoile(x) - crete(x)) * 0.45f }, { x -> piedVoile(x) }),
                    sombre(beton, 0.3f), 0.22f)
                forme(ruban(-4f, 104f, { x -> piedVoile(x) - 1.5f }, { x -> piedVoile(x) }), sombre(beton, 0.46f), 0.4f)
                releve?.restore(); c.restore()
                // ---- La crête : la route, son parapet amont, et le
                // garde-corps aval, ajouré — c'est entre ses poteaux qu'on
                // voit le lac, et une pièce pleine le boucherait.
                // Le tablier est posé **à plat** sur le voile : sa tranche de
                // kraft, sur cent unités de long, se lisait comme un jonc de
                // caoutchouc tendu d'une rive à l'autre. C'est l'ombre du
                // parapet sur le parement qui dit maintenant l'arête.
                forme(ruban(-4f, 104f, { x -> crete(x) - tablier(x) }, { x -> crete(x) + 0.1f }),
                    mix(beton, 0xFFFFFFFF.toInt(), 0.2f), 0.94f)
                forme(ruban(-4f, 104f, { x -> crete(x) - tablier(x) }, { x -> crete(x) - tablier(x) + 0.7f }),
                    mix(beton, 0xFFFFFFFF.toInt(), 0.36f), 0.8f)
                forme(ruban(-4f, 104f, { x -> crete(x) }, { x -> crete(x) + 0.5f }), sombre(beton, 0.42f), 0.6f)
                var jx = -4f
                while (jx < 104f) {
                    ligne(jx, crete(jx) - tablier(jx) + 0.4f, jx, crete(jx), sombre(beton, 0.2f), 0.18f, 0.4f)
                    jx += 5f + (crete(jx) - 33f) * 0.5f
                }
                // Le garde-corps, aval : ses poteaux grandissent eux aussi en
                // venant vers nous, et c'est entre eux qu'on voit le lac.
                val metal = mix(beton, 0xFFFFFFFF.toInt(), if (P.nuit) 0.3f else 0.52f)
                var gx = -4f
                while (gx < 104f) {
                    val h = tablier(gx) * 0.72f
                    rect(gx, crete(gx) - h, 0.1f + h * 0.14f, h + 0.2f, metal, 0.95f)
                    gx += 2.8f + (crete(gx) - 33f) * 0.22f
                }
                var ux = -4f
                while (ux < 103f) {
                    val h = tablier(ux) * 0.72f
                    ligne(ux, crete(ux) - h, ux + 3f, crete(ux + 3f) - tablier(ux + 3f) * 0.72f, metal, 0.26f)
                    ligne(ux, crete(ux) - h * 0.5f, ux + 3f, crete(ux + 3f) - tablier(ux + 3f) * 0.36f, metal, 0.2f, 0.8f)
                    ux += 3f
                }
            }
            // ---- L'eau d'aval, plus sombre et plus verte que le lac.
            val aval = calmer(mix(P.eau, duJour(0xFF236B57.toInt()), if (P.allume) 0.56f else 0.82f))
            piece(ruban(-4f, 104f, { x -> piedVoile(x) }, { 62f }), aval, 0.4f)
            // L'écume au pied du voile, et les rides du courant.
            forme(ruban(-4f, 104f, { x -> piedVoile(x) }, { x -> piedVoile(x) + 1.1f }),
                mix(aval, 0xFFFFFFFF.toInt(), if (P.nuit) 0.2f else 0.5f), 0.45f)
            // L'ombre que le voile porte sur l'eau d'aval : elle est ce qui
            // dit la hauteur du barrage, et sans elle la retenue flotte.
            // Puis le reflet du parement, couché dessous : une eau sans
            // reflet n'est qu'une aire verte au bas de la fenêtre.
            forme(ruban(-4f, 104f, { x -> piedVoile(x) + 1.1f }, { x -> piedVoile(x) + 2.6f }), sombre(aval, 0.4f), 0.38f)
            forme(ruban(-4f, 104f, { x -> piedVoile(x) + 2.6f }, { x -> piedVoile(x) + 8.5f }),
                mix(aval, beton, 0.55f), 0.22f)
            val ride = mix(aval, 0xFFFFFFFF.toInt(), if (P.nuit) 0.16f else 0.42f)
            for (k in 0 until 14) {
                val ax = -4f + r() * 86f
                val ay = piedVoile(ax) + 2f + r() * (56f - piedVoile(ax)).coerceAtLeast(2f)
                rect(ax, ay, 4f + r() * 9f, 0.32f, ride, 0.7f)
            }
            c.restore()
        }

        // ---- Conzefenn, les tourbières de Troisvierges et leurs moutons (photo du propriétaire)

        /**
         * Ce que dit la photo, et que la première version n'avait pas :
         *
         * 1. **La lisière est un mur**, pas un peigne. Les épicéas de
         *    Troisvierges font une masse presque noire qui barre toute la
         *    largeur, et seules leurs cimes se découpent sur le ciel. Dessinés
         *    un à un et espacés, ils donnaient une fermeture éclair posée sur
         *    le pré. C'est la pièce la plus sombre de la carte, et c'est elle
         *    qui tient le fond.
         * 2. **Le troupeau est nombreux et serré.** Des dizaines de bêtes
         *    éparpillées sur tout le pré, de plus en plus petites et de plus
         *    en plus tassées contre la lisière. Quatre moutons sur une prairie
         *    vide, c'est une illustration de livre d'enfant ; ce pré-là est
         *    couvert.
         * 3. **Le mouton des Ardennes est blanc à tête noire**, sur quatre
         *    pattes noires, et sa laine fait un dos bosselé. La tête baissée
         *    de celui qui broute est ce qui se lit le mieux de loin.
         * 4. **La clôture est en perches rondes** de bois brut : des piquets
         *    qui penchent chacun à sa façon, des lisses d'épaisseurs inégales
         *    qui fléchissent entre eux et les dépassent, une écharpe en
         *    diagonale. Équarrie et d'aplomb, elle faisait une barrière de
         *    jardin devant une tourbière.
         * 5. **L'herbe est haute et drue**, semée de boutons d'or, et un
         *    genêt en fleur marque le bord du pré.
         * 6. **Une trouée à droite** laisse voir, par-dessus la lisière, un
         *    versant plus lointain : c'est ce qui donne au plateau sa
         *    profondeur.
         *
         * Tout fuit vers la droite ([LISIERE_CONZEFENN], [CLOTURE_CONZEFENN]) :
         * sur la carte ouverte, la lisière tient la bande du haut et la
         * clôture les deux colonnes.
         */
        fun conzefenn() {
            val r = Alea(255)
            val f = LISIERE_CONZEFENN
            val fin = 300f
            // ---- Le versant lointain, qu'on voit par la trouée de droite.
            lisiere(-3f, 103f, { x -> 17f + 2.4f * sin(x * 0.06f + 1.2f) }, 2f, 3.2f, 256,
                mix(nappe(duJour(0xFF4A6B38.toInt()), 0.34f, VERT_CALME), P.loin, 0.52f))
            // ---- Le mur d'épicéas : une seule découpe, dont les cimes se
            // recouvrent. Le pré s'arrête quatre unités sous leurs pieds, si
            // bien qu'il reste sous les cimes une bande pleine : c'est elle
            // qui fait le mur, et c'est elle qui manquait.
            // Un vert d'épicéa : froid et profond. Passé par [nappe] seule,
            // il sortait olive, et le mur se confondait avec le versant
            // lointain posé derrière lui.
            //
            // Il tombe **hors** de la famille que [Habillage] reconnaît comme
            // végétale, et c'est voulu : un épicéa ne rouille pas l'automne
            // et ne blanchit pas sous la neige, il la porte. L'automne laisse
            // donc le mur vert au-dessus d'un pré roux, et l'hiver lui pose
            // une ligne de neige sur les cimes. Ne pas « corriger » cette
            // couleur pour la faire rentrer dans la famille.
            val epiceas = sombre(mix(nappe(duJour(0xFF27492C.toInt()), 0.42f, VERT_CALME),
                duJour(0xFF14332C.toInt()), 0.42f), 0.14f)
            val foret = Path()
            foret.moveTo(-3f, BAS + 2f)
            foret.lineTo(-3f, f.y(0f) - 4f)
            var u = -5f
            while (u <= fin) {
                val e = f.echelle(u)
                val x = f.x(u); val base = f.y(u)
                val h = (8f + r() * 6.5f) * e
                val l = (2.4f + r() * 1.4f) * e
                foret.lineTo(x - l, base - h * 0.34f)
                foret.lineTo(x, base - h)
                foret.lineTo(x + l, base - h * 0.34f)
                u += 1.5f + r() * 1.3f
            }
            foret.lineTo(103f, f.y(fin) - 2f)
            foret.lineTo(103f, BAS + 2f); foret.close()
            piece(foret, epiceas)
            // Deux ou trois arbres de devant, un peu plus clairs : sans écart
            // de valeur, la masse reste un aplat découpé en dents de scie.
            for (k in 0 until 9) {
                val uu = -4f + r() * 150f
                val e = f.echelle(uu)
                val h = (7f + r() * 5f) * e
                val l = (2f + r() * 1.2f) * e
                val tx = f.x(uu); val ty = f.y(uu) + 1.6f * e
                pieceD("M${tx - l} $ty L${tx - l * 0.45f} ${ty - h * 0.42f}L${tx - l * 0.72f} ${ty - h * 0.4f}" +
                    "L${tx - l * 0.3f} ${ty - h * 0.74f}L${tx - l * 0.5f} ${ty - h * 0.72f}L$tx ${ty - h}" +
                    "L${tx + l * 0.5f} ${ty - h * 0.72f}L${tx + l * 0.3f} ${ty - h * 0.74f}" +
                    "L${tx + l * 0.72f} ${ty - h * 0.4f}L${tx + l * 0.45f} ${ty - h * 0.42f}L${tx + l} ${ty}Z",
                    if (k % 3 == 0) mix(epiceas, duJour(0xFF5E8A46.toInt()), 0.26f) else sombre(epiceas, 0.2f), 0.5f)
            }
            plan(1)
            // ---- La prairie, son bord le long de la lisière.
            piece(Path().apply {
                moveTo(-2f, 66f); lineTo(-2f, f.y(-4f) + 6.5f)
                var v = 0f
                while (v <= fin) { lineTo(f.x(v), f.y(v) + 6.5f * f.echelle(v)); v += 8f }
                lineTo(102f, f.y(fin) + 1f); lineTo(102f, 66f); close()
            }, mix(mix(vert(0.78f), duJour(0xFF57A33E.toInt()), 0.3f), P.loin, 0.1f), 0.4f)
            // L'ombre que la lisière porte sur le pré : un pré qui vient
            // buter contre un mur noir sans transition se lit comme un
            // découpage collé.
            forme(Path().apply {
                moveTo(-2f, f.y(-4f) + 6.5f)
                var v = 0f
                while (v <= fin) { lineTo(f.x(v), f.y(v) + 6.5f * f.echelle(v)); v += 8f }
                lineTo(102f, f.y(fin) + 1f)
                v = fin
                while (v >= 0f) { lineTo(f.x(v), f.y(v) + (6.5f + 3.4f) * f.echelle(v)); v -= 8f }
                lineTo(-2f, f.y(-4f) + 9.9f); close()
            }, sombre(vert(0.6f), 0.3f), 0.3f)
            // Les moutons et les fleurs posés sur la prairie qui fuit : plus
            // petits et plus serrés au loin, contre la lisière.
            fun auSol(uu: Float, recul: Float): FloatArray {
                val e = f.echelle(uu)
                return floatArrayOf(f.x(uu) - recul * 0.9f * e, f.y(uu) + recul * e, e)
            }
            for (k in 0 until 34) {
                val p = auSol(6f + r() * 240f, 4.6f + r() * 9f)
                mouton(p[0], p[1], (0.9f + r() * 0.35f) * p[2], r(), r() < 0.5f)
            }
            for (k in 0 until 18) {
                val p = auSol(r() * 240f, 4f + r() * 12f)
                rond(p[0], p[1], 0.55f * p[2], duJour(0xFFF2C230.toInt()))
            }
            // Le genêt en fleur, au bord du pré, dans la colonne de droite.
            val genet = sombre(vert(0.55f), 0.1f)
            touffe(genet, 92f, 36.5f, 3.2f, 97f, 37.5f, 2.6f, 88f, 37.6f, 2f)
            for (k in 0 until 14) rond(86f + r() * 14f, 34.4f + r() * 4.4f, 0.4f + r() * 0.3f, duJour(0xFFF5D43A.toInt()))
            plan(2)
            solNaturel(SolNaturel.HERBE)  // la prairie
            poser(DEVANT)
            // Le pré du devant. Sa lèvre est douce (0,2) et l'herbe haute
            // l'enjambe : à pleine ombre, sa tranche de kraft faisait une
            // marche en travers du pré.
            pieceD("M-2 66L-2 47C30 44.5 70 41.5 102 40L102 66Z", vert(0.7f), 0.2f)
            // L'herbe du devant est haute : des touffes dressées, de deux
            // valeurs, de plus en plus drues en approchant. Sans elles, le
            // pré est une moquette et la clôture est plantée dans du feutre.
            val herbe = vert(0.62f)
            for (k in 0 until 110) {
                val hx = -2f + r() * 106f
                val hy = 41f + r() * 18f
                val hh = 1.4f + max(0f, hy - 44f) * 0.16f + r() * 1.6f
                ligne(hx, hy, hx + (r() - 0.5f) * 1.4f, hy - hh,
                    if (r() < 0.45f) mix(herbe, duJour(0xFFDCEE8E.toInt()), 0.3f) else sombre(herbe, 0.26f),
                    0.2f + r() * 0.16f, 0.8f)
            }
            if (!vivant) for ((k, mx) in MOUTONS.withIndex())
                mouton(mx, 47f + (k % 2) * 3f, MOUTON_PROCHE, if (k % 2 == 0) 1f else 0f, k % 3 != 0)
            // ---- La clôture de perches. Rien n'y est d'équerre : les
            // piquets penchent chacun à sa façon, les lisses fléchissent
            // entre eux et les dépassent, et une écharpe tient le tout.
            val c2 = CLOTURE_CONZEFENN
            val bois = neutre(duJour(0xFF8A7458.toInt()))
            val bout = 182f
            /** Une perche ronde posée à [h0] au-dessus du pied, épaisse de [ep], qui fléchit de [fleche]. */
            fun perche(h0: Float, ep: Float, fleche: Float, col: Int) {
                fun y(v: Float) = c2.y(v) - h0 * c2.echelle(v) +
                    sin((v + 6f) / (bout + 6f) * PI.toFloat()) * fleche * c2.echelle(v)
                val t = Path()
                var v = -6f
                t.moveTo(c2.x(v), y(v))
                while (v < bout) { v = min(bout, v + 8f); t.lineTo(c2.x(v), y(v)) }
                while (v > -6f) { t.lineTo(c2.x(v), y(v) + ep * c2.echelle(v)); v = max(-6f, v - 8f) }
                t.lineTo(c2.x(-6f), y(-6f) + ep)
                t.close()
                piece(t, col, 0.6f)
                // La joue à l'ombre, sous la perche : c'est elle qui l'arrondit.
                val j = Path()
                v = -6f
                j.moveTo(c2.x(v), y(v) + ep * 0.55f)
                while (v < bout) { v = min(bout, v + 8f); j.lineTo(c2.x(v), y(v) + ep * 0.55f * c2.echelle(v)) }
                while (v > -6f) { j.lineTo(c2.x(v), y(v) + ep * c2.echelle(v)); v = max(-6f, v - 8f) }
                j.close()
                forme(j, sombre(col, 0.4f), 0.55f)
            }
            perche(10.4f, 1.5f, 0.9f, mix(bois, 0xFFFFFFFF.toInt(), 0.1f))
            perche(5.6f, 1.2f, 1.3f, bois)
            // L'écharpe : la perche de travers qui empêche la clôture de
            // coucher. C'est elle qu'on remarque sur la photo.
            val e0 = c2.echelle(10f); val e1 = c2.echelle(74f)
            pieceD("M${c2.x(10f)} ${c2.y(10f) + 0.6f}L${c2.x(10f) + 1.3f * e0} ${c2.y(10f) + 0.6f}" +
                "L${c2.x(74f) + 1.1f * e1} ${c2.y(74f) - 11.4f * e1}L${c2.x(74f)} ${c2.y(74f) - 11.4f * e1}Z",
                sombre(bois, 0.12f), 0.5f)
            for (pu in floatArrayOf(0f, 18f, 40f, 66f, 96f, 132f, 176f)) {
                val e = c2.echelle(pu)
                val px = c2.x(pu); val py = c2.y(pu) + 0.8f * e
                // Le piquet penche, et il n'est pas de la même épaisseur en
                // haut qu'en bas : c'est une perche, pas un tasseau.
                val pen = (r() - 0.5f) * 2.6f * e
                val w = (1.7f + r() * 0.6f) * e
                val h = (13.5f + r() * 2.4f) * e
                val ton = if (r() < 0.4f) mix(bois, 0xFFFFFFFF.toInt(), 0.12f) else sombre(bois, 0.1f + r() * 0.2f)
                pieceD("M${px - w * 0.5f} $py L${px + w * 0.5f} $py" +
                    "L${px + w * 0.38f + pen} ${py - h}L${px - w * 0.38f + pen} ${py - h}Z", ton, 0.6f)
                forme("M${px + w * 0.1f} $py L${px + w * 0.5f} $py" +
                    "L${px + w * 0.38f + pen} ${py - h}L${px + w * 0.1f + pen} ${py - h}Z", sombre(ton, 0.38f), 0.5f)
            }
            for (k in 0 until 16) {
                val e = 0.8f + r() * 0.2f
                rond(r() * 100f, 52f + r() * 6f, 0.5f * e, duJour(0xFFF2C230.toInt()))
            }
            c.restore()
        }

        // ---- Haff Réimech, le Biodiversum sur l'étang (photo du propriétaire)

        /**
         * Ce que dit la photo du propriétaire (2026-11-21) :
         *
         * 1. **Le lieu est une réserve d'étangs**, pas une rivière. L'ancienne
         *    version y faisait fuir un pont de bois couvert sur ses piles de
         *    pierre, et ce pont tenait toute la fenêtre : il n'y a pas de pont
         *    au Haff Réimech.
         * 2. **Le Biodiversum est le sujet** : une coque de bois retournée,
         *    posée sur son île, fendue d'une grande ogive. C'est la seule
         *    chose bâtie du lieu et elle se reconnaît entre toutes, donc elle
         *    est portée à droite, hors du dessin du mot.
         * 3. **L'étang est un miroir.** Pas une vague, pas un courant : il
         *    rend la coque et la lisière à l'envers, à peine hachées de
         *    quelques traînées couchées. C'est son reflet, et non sa teinte,
         *    qui fait cette eau-là.
         * 4. **La lisière fait le tour de l'étang** et descend jusqu'à l'eau.
         *    Le plateau de la Moselle est plat : une crête en travers lui
         *    donnerait une vallée qu'il n'a pas.
         * 5. **Trois éoliennes** dépassent du bois, au loin à gauche, et
         *    donnent au plateau son échelle.
         * 6. **Les roseaux tiennent le bas**, avec la branche sous laquelle
         *    la photo est prise.
         */
        fun haffReimech() {
            val r = Alea(266)
            /** La ligne d'eau, presque droite : un étang n'a pas de pente. */
            fun rive(x: Float) = 27.5f + (x - 50f) * 0.012f + 0.3f * sin(x * 0.07f)
            /** Le ruban compris entre les courbes [haut] et [bas], de [x0] à [x1]. */
            fun ruban(x0: Float, x1: Float, haut: (Float) -> Float, bas: (Float) -> Float): Path {
                val t = Path()
                t.moveTo(x0, haut(x0))
                var x = x0
                while (x < x1) { x = min(x1, x + 2.5f); t.lineTo(x, haut(x)) }
                while (x > x0) { t.lineTo(x, bas(x)); x = max(x0, x - 2.5f) }
                t.lineTo(x0, bas(x0))
                t.close()
                return t
            }
            // ---- Les trois éoliennes du plateau. Peintes avant le bois, qui
            // leur mange le pied : c'est lui qui les plante, et un mât arrêté
            // sur la cime flotterait. Minérales, pour que l'hiver ne les
            // blanchisse pas et que le printemps n'y sème pas de fleurs.
            val acier = neutre(mix(duJour(0xFFF2F0EA.toInt()), P.loin, 0.46f))
            mineral {
                for (k in 0 until 3) {
                    val ex = 29f + k * 11.4f
                    val moyeu = 10.4f + k * 1.1f
                    val s = 1f - k * 0.09f
                    rect(ex - 0.22f * s, moyeu, 0.44f * s, 22f - moyeu, acier, 0.92f)
                    for (b in 0 until 3) {
                        val a = 0.6f + k * 0.5f + b * 2.0944f
                        ligne(ex, moyeu, ex + sin(a) * 3.5f * s, moyeu - cos(a) * 3.5f * s, acier, 0.3f * s, 0.9f)
                    }
                    rond(ex, moyeu, 0.42f * s, acier, 0.95f)
                }
            }
            // ---- Le bois d'en face, bas et pâle.
            lisiere(-3f, 103f, { x -> 18.4f + 2.2f * sin(x * 0.062f) }, 1.6f, 2.6f, 267,
                mix(nappe(duJour(0xFF40622C.toInt()), 0.32f, VERT_CALME), P.loin, 0.5f))
            plan(1)
            // ---- La lisière de la rive d'en face, en deux rideaux : à
            // nuance égale, deux rideaux n'en font qu'un et le tour de
            // l'étang redevient un aplat. Leur pied descend sous la ligne
            // d'eau, que l'étang vient couvrir.
            val bois = nappe(duJour(0xFF3C6A26.toInt()), 0.5f, VERT_CALME)
            lisiere(-3f, 103f, { x -> 20.2f + 2.4f * sin(x * 0.085f + 1f) }, 2f, 3.2f, 268, mix(bois, P.loin, 0.24f))
            lisiere(-3f, 103f, { x -> 23.2f + 1.8f * sin(x * 0.13f) }, 2.4f, 3.8f, 269, bois)
            // Les touffes de la rive. L'automne de la photo se dit par
            // quelques masses d'or et de rouille au milieu du vert, et non
            // par une lisière entière jaunie : la saison, elle, est habillée
            // après coup ([Habillage]), et une lisière déjà rousse ne lui
            // laisserait rien à dire.
            for (k in 0 until 20) {
                val fx = -4f + r() * 108f
                val fy = 19.6f + r() * 6.2f
                val t = r()
                touffe(if (t < 0.28f) mix(bois, duJour(0xFFE2C04A.toInt()), 0.34f)
                       else if (t < 0.48f) mix(bois, duJour(0xFFBE6C30.toInt()), 0.26f)
                       else if (t < 0.76f) bois else sombre(bois, 0.3f),
                    fx, fy, 1.4f + r() * 1.8f,
                    fx + 2.2f, fy + 1f, 1.2f + r() * 1.4f,
                    fx - 2f, fy + 0.9f, 1.1f + r() * 1.3f)
            }
            // ---- Le Biodiversum : une coque de bardeaux retournée. Le
            // pignon en ogive nous fait face, le flanc fuit vers la droite,
            // et l'aile basse s'en va vers la gauche derrière le dessin du
            // mot. Tout passe par [neutre] (règle 10) : sans cela `raviver`
            // sort le bardeau en cuivre et le pignon en or.
            val bardeau = neutre(duJour(0xFF756B5E.toInt()))
            val pignon = neutre(duJour(0xFF9A9280.toInt()))
            val vitre = if (P.allume) P.fenetre else neutre(duJour(0xFF2A3038.toInt()))
            val ile = neutre(duJour(0xFFB0A68E.toInt()))
            val flanc = chemin("M89 6.4C93.6 7 99 8.9 104 11.8L104 25.8L95.4 26.6C95.4 16.1 93 9.8 89 6.4Z")
            val coque = chemin("M82.6 26.6C82.6 16.1 85 9.8 89 6.4C93 9.8 95.4 16.1 95.4 26.6Z")
            mineral {
                // L'île, en premier : une coque posée sur la ligne d'eau
                // flotte, et c'est le défaut qu'on a déjà payé ailleurs.
                forme(ruban(72f, 104f, { 26.3f }, { x -> rive(x) + 0.2f }), ile, 0.8f)
                // L'aile basse et son toit plat, qui fuit vers la gauche :
                // elle s'amincit en s'éloignant.
                val aile = floatArrayOf(69f, 22.4f, 82.9f, 21f, 82.9f, 26.6f, 69f, 26.1f)
                piece(quad(aile), neutre(duJour(0xFF8A7658.toInt())), 0.6f)
                surPan(aile) {
                    // La bande vitrée, continue, et ses meneaux clairs :
                    // c'est elle qui fait un bâtiment de ce long bandeau,
                    // qui sans elle se lit comme un mur de quai.
                    rect(0f, 30f, 100f, 44f, neutre(duJour(0xFF2A3038.toInt())), 0.95f)
                    if (P.allume) rect(0f, 30f, 100f, 44f, vitre, 0.6f)
                    for (k in 0 until 15) ligne(k * 7f, 28f, k * 7f, 76f, mix(bardeau, 0xFFFFFFFF.toInt(), 0.34f), 1.1f, 0.85f)
                    rect(0f, 74f, 100f, 26f, sombre(bardeau, 0.34f), 0.9f)
                }
                // Le toit plat et son débord, clair : l'arête qui dit que
                // c'est couvert, et non remblayé.
                piece(quad(floatArrayOf(68.4f, 21.7f, 83.3f, 20.2f, 83.3f, 21.4f, 68.4f, 22.9f)),
                    mix(bardeau, 0xFFFFFFFF.toInt(), 0.32f), 0.5f)
                // Le flanc, puis le pignon par-dessus : c'est leur
                // recouvrement qui tourne la coque de trois quarts.
                piece(flanc, sombre(bardeau, 0.24f), 0.6f)
                c.save(); c.clipPath(flanc)
                releve?.let { it.save(); it.clipPath(flanc) }
                // Les membrures du flanc, du faîte à l'égout, serrées de plus
                // en plus en fuyant : c'est à elles qu'on voit que la coque
                // est une coque, et non une colline de carton.
                var u = 0.06f
                while (u < 1f) {
                    val bx = 89f + 15f * u
                    val by = 6.4f + (bx - 89f) * 0.18f + 0.012f * (bx - 89f) * (bx - 89f)
                    ligne(bx, by, bx + 1.6f, 26.8f, sombre(bardeau, 0.42f), 0.24f, 0.55f)
                    ligne(bx + 0.34f, by, bx + 1.94f, 26.8f, mix(bardeau, 0xFFFFFFFF.toInt(), 0.12f), 0.2f, 0.4f)
                    u += 0.075f - u * 0.045f
                }
                // Le flanc est à l'ombre en bas et au jour sous le faîte.
                forme(ruban(94f, 104f, { x -> 19f + (x - 94f) * 0.1f }, { 27f }), sombre(bardeau, 0.4f), 0.3f)
                releve?.restore(); c.restore()
                piece(coque, pignon, 0.6f)
                c.save(); c.clipPath(coque)
                releve?.let { it.save(); it.clipPath(coque) }
                // Les bardeaux du pignon rayonnent de la pointe : posés en
                // rangs horizontaux, ils faisaient un toit d'ardoise collé
                // sur une ogive.
                for (k in -9..9) {
                    val bx = 89f + k * 0.72f
                    ligne(89f, 7.4f, 89f + k * 1.05f, 27f, sombre(pignon, 0.26f), 0.22f, 0.5f)
                    ligne(89f + k * 1.05f + 0.3f, 27f, 89.2f, 7.4f, mix(pignon, 0xFFFFFFFF.toInt(), 0.16f), 0.16f, 0.35f)
                    if (k % 3 == 0) ligne(bx - 3f, 12f + abs(k) * 1.4f, bx + 3f, 12f + abs(k) * 1.4f,
                        sombre(pignon, 0.14f), 0.3f, 0.3f)
                }
                // Le jour vient de la gauche : le flanc droit du pignon
                // tourne à l'ombre, et son arête gauche s'éclaire.
                forme("M91 6.8C93.4 10.4 95.4 17 95.4 26.8L91 26.8Z", sombre(pignon, 0.34f), 0.42f)
                forme("M82.6 26.6C82.6 16.1 85 9.8 89 6.4L89 7.6C85.8 11 83.8 17 83.8 26.6Z",
                    mix(pignon, 0xFFFFFFFF.toInt(), 0.26f), 0.6f)
                releve?.restore(); c.restore()
                // La grande ogive : l'embrasure, le vitrage en retrait, et
                // ses deux meneaux. C'est le seul endroit où la coque
                // s'ouvre, et c'est lui qui dit l'échelle du bâtiment.
                pieceD("M85.9 26.6C85.9 19.6 87.1 15.5 89 13.2C90.9 15.5 92.1 19.6 92.1 26.6Z",
                    mix(pignon, 0xFFFFFFFF.toInt(), 0.3f), 0.4f)
                forme("M86.5 26.6C86.5 20.2 87.5 16.4 89 14.3C90.5 16.4 91.5 20.2 91.5 26.6Z", vitre, 0.92f)
                if (P.allume) halo(89f, 20f, 5.4f, P.fenetre, 0.3f)
                for (mx in floatArrayOf(87.8f, 90.2f)) ligne(mx, 26.6f, mx, 15.6f, mix(pignon, 0xFFFFFFFF.toInt(), 0.2f), 0.18f, 0.7f)
                ligne(86.6f, 21.4f, 91.4f, 21.4f, mix(pignon, 0xFFFFFFFF.toInt(), 0.2f), 0.18f, 0.7f)
            }
            // ---- L'étang. Il passe par [duJour] mais **pas** par [neutre] :
            // le bleu d'une eau est une teinte et non une matière bâtie, et
            // neutralisé il tourne au mauve. Sa saturation est tenue comme
            // celle de la végétation ([calmer]), sans quoi le ravivage des
            // palettes vives en fait la chose la plus criarde de la carte,
            // devant le mot qui est posé dessus. Et il cède le pas au ciel
            // le soir et la nuit ([P.allume]) : une eau se lit à ce qu'elle
            // reflète, et celle-ci plus que toute autre.
            fun calmer(col: Int) = mix(col, neutre(col), 0.4f)
            val etang = calmer(mix(P.eau, duJour(0xFF2C6E92.toInt()), if (P.allume) 0.4f else 0.66f))
            // Le creux de l'eau. [sombre] vise un brun mêlé de la couleur du
            // proche, qui est rose au couchant : l'eau du soir n'y fonçait
            // pas d'un cheveu. Le fond d'un étang est froid, à toute heure.
            fun creux(col: Int, t: Float) = mix(col, duJour(0xFF14303E.toInt()), t)
            piece(ruban(-4f, 104f, { x -> rive(x) }, { 66f }), etang, 0.4f)
            // Le reflet de la lisière, couché sous elle, qui se brouille en
            // descendant : sans lui le bois est posé sur un aplat bleu.
            forme(ruban(-4f, 104f, { x -> rive(x) }, { x -> rive(x) + 3.6f }), creux(etang, 0.44f), 0.5f)
            forme(ruban(-4f, 104f, { x -> rive(x) + 3.6f }, { x -> rive(x) + 7f }), creux(etang, 0.4f), 0.24f)
            // L'eau fonce en venant vers nous, en cinq voiles minces aux
            // bords ondulés : d'un seul ton, l'étang n'était qu'une aire
            // bleue au bas de la fenêtre, et trois voiles droits y
            // laisseraient trois arêtes en travers.
            for (k in 0 until 5) forme(ruban(-4f, 104f, { x -> 34f + k * 5f + 1.4f * sin(x * 0.07f + k) }, { 68f }),
                creux(etang, 0.62f), 0.12f)
            // ---- Le reflet de la coque : la même découpe, retournée sous la
            // ligne d'eau et un peu écrasée. Peinte à plat ([forme]) : un
            // reflet n'a ni tranche de kraft ni ombre portée, et le relevé
            // de la végétation suit le miroir comme le tracé.
            mineral {
                val y0 = rive(89f)
                c.save(); c.scale(1f, -0.9f, 89f, y0)
                releve?.let { it.save(); it.scale(1f, -0.9f, 89f, y0) }
                forme(flanc, creux(mix(sombre(bardeau, 0.24f), etang, 0.46f), 0.14f), 0.72f)
                forme(coque, creux(mix(pignon, etang, 0.42f), 0.12f), 0.72f)
                forme("M86.5 26.6C86.5 20.2 87.5 16.4 89 14.3C90.5 16.4 91.5 20.2 91.5 26.6Z",
                    creux(mix(vitre, etang, 0.4f), 0.16f), 0.8f)
                releve?.restore(); c.restore()
            }
            // Les traînées. Sur un miroir elles sont rares, longues et
            // franchement couchées : c'est ce qui sépare un étang d'un lac
            // battu par le vent. Elles hachent le reflet, et ce sont elles
            // qui font voir que c'en est un.
            val brillance = mix(etang, if (P.allume) mix(0xFFFFFFFF.toInt(), P.astre, 0.45f) else 0xFFFFFFFF.toInt(),
                if (P.nuit) 0.14f else 0.42f)
            for (k in 0 until 24) {
                val lx = -4f + r() * 108f
                val ly = rive(lx) + 2.4f + r() * 28f
                rect(lx, ly, 4f + r() * (5f + (ly - 28f) * 0.5f), 0.26f + r() * 0.16f, brillance, 0.4f + r() * 0.3f)
            }
            plan(2)
            poser(DEVANT)
            // ---- Les roseaux du bord, d'où la photo est prise : une masse
            // d'abord, pour que le carton du proche ait une découpe, puis les
            // tiges dessus. Des tiges seules font un peigne posé sur l'eau.
            val roseau = sombre(nappe(duJour(0xFF55762A.toInt()), 0.5f, VERT_CALME), 0.16f)
            touffe(roseau, -6f, 58f, 8f, 2f, 56f, 6.5f, 10f, 59f, 5.5f, 18f, 61f, 5f,
                94f, 59f, 6.5f, 104f, 57f, 7f, 86f, 61f, 5f)
            for (k in 0 until 58) {
                val t = r()
                val rx = if (k % 3 == 0) 62f + r() * 44f else -6f + r() * 34f
                val h = 5f + t * 10f
                val pente = (r() - 0.5f) * 4f
                val pied = 59.2f - r() * 2.4f
                trait("M$rx ${pied}C${rx + pente * 0.25f} ${pied - h * 0.5f} ${rx + pente * 0.6f} ${pied - h * 0.82f} ${rx + pente} ${pied - h}",
                    if (t < 0.42f) sombre(roseau, 0.26f) else mix(roseau, duJour(0xFFD8CE86.toInt()), 0.2f + t * 0.4f),
                    0.26f + t * 0.3f, 0.9f)
                // Quelques massettes, les seules choses pointues du bord.
                if (t > 0.86f) rect(rx + pente - 0.26f, pied - h - 1.8f, 0.52f, 2f, sombre(duJour(0xFF6B4A2A.toInt()), 0.2f), 0.9f)
            }
            // ---- La branche sous laquelle la photo est prise, au coin haut
            // gauche. Elle reste courte : les coulisses des cartes rares
            // tiennent déjà ce bord, et deux feuillages l'un sur l'autre
            // referment la fenêtre.
            val feuillage = nappe(duJour(0xFF4E7A24.toInt()), 0.52f, VERT_CALME)
            trait("M-4 1.4C2 2.2 7 4.2 11.4 7.4", sombre(duJour(0xFF5A4328.toInt()), 0.2f), 0.7f)
            trait("M3 2.6C4.6 4.6 5.6 6.6 6 9", sombre(duJour(0xFF5A4328.toInt()), 0.2f), 0.45f)
            touffe(feuillage, -4f, 0.4f, 3.2f, 1.4f, 1.6f, 2.4f, 6.6f, 4.2f, 2.2f, 11.4f, 7.6f, 2f, 5.6f, 9.4f, 1.8f)
            touffe(mix(feuillage, duJour(0xFFE2C04A.toInt()), 0.3f), 0.6f, 4.4f, 1.6f, 8.6f, 6.2f, 1.4f, 12.4f, 9f, 1.2f)
            c.restore()
        }

        // ---- Hohllay, la grotte des meules de Berdorf (photo du propriétaire)

        /**
         * Une **empreinte de meule** dans le grès : la cuvette ronde que le
         * carrier laissait en détachant sa pierre, et les gradins
         * concentriques de l'outil autour d'elle.
         *
         * C'est tout le lieu. Posées à plat, les empreintes feraient des
         * cibles peintes sur un mur : elles sont vues de biais sur une voûte,
         * donc **aplaties** de [aplat], et la lumière vient d'en bas à gauche
         * (elle entre par la bouche de la grotte et rebondit sur le sol), si
         * bien que chaque cuvette porte son croissant clair en bas à gauche et
         * son ombre en haut à droite. Sans ces deux croissants, les anneaux
         * restent un dessin, et la roche ne se creuse pas.
         */
        fun meule(cx: Float, cy: Float, rx: Float, aplat: Float, col: Int, graine: Int) {
            if (!actif) return
            val rr = Alea(graine)
            c.save(); c.scale(1f, aplat, cx, cy)
            releve?.let { it.save(); it.scale(1f, aplat, cx, cy) }
            rond(cx, cy, rx, sombre(col, 0.1f + rr() * 0.1f))
            // Trois ou quatre gradins, pas davantage, et d'un ton à l'autre
            // l'écart reste faible : plus nombreux et plus contrastés, les
            // anneaux font une cible peinte et non un creux.
            var k = rx
            var i = 0
            while (k > rx * 0.26f) {
                val t = rr()
                anneau(cx, cy, k, if (i % 2 == 0) mix(col, 0xFFFFFFFF.toInt(), 0.06f + t * 0.1f)
                                  else sombre(col, 0.1f + t * 0.12f), rx * 0.1f)
                k -= rx * (0.2f + rr() * 0.1f)
                i++
            }
            // Les deux croissants : le jour en bas à gauche, l'ombre en haut
            // à droite. Ils sont tracés par-dessus les gradins, en voile.
            p.style = Paint.Style.STROKE; p.strokeWidth = rx * 0.3f
            teinte(mix(col, 0xFFFFFFFF.toInt(), 0.3f), 0.26f)
            c.drawArc(cx - rx * 0.86f, cy - rx * 0.86f, cx + rx * 0.86f, cy + rx * 0.86f, 100f, 110f, false, p)
            teinte(sombre(col, 0.5f), 0.3f)
            c.drawArc(cx - rx * 0.86f, cy - rx * 0.86f, cx + rx * 0.86f, cy + rx * 0.86f, 285f, 105f, false, p)
            p.style = Paint.Style.FILL
            releve?.restore(); c.restore()
        }

        /**
         * Ce que dit la photo, et que l'ancienne version n'avait pas :
         *
         * 1. **C'est une grotte, pas une crypte.** L'ancienne avait deux
         *    piliers d'aplomb, trois arcades en plein cintre et des murs
         *    crème : on y lisait une église basse. Ici rien n'est régulier,
         *    rien n'est d'aplomb, et la voûte pèse.
         * 2. **Le plafond est le sujet.** Il est couvert de grandes empreintes
         *    de meules, rondes, concentriques, qui se recouvrent les unes les
         *    autres. C'est le seul endroit du Luxembourg qui ressemble à ça,
         *    et c'était réduit à quelques petits arcs gris en haut de la
         *    fenêtre.
         * 3. **Le grès est chaud et il est sale** : ocre et rose là où le jour
         *    le prend, vert-noir d'algue et de mousse partout où l'eau
         *    suinte, c'est-à-dire en bas et au fond.
         * 4. **Le jour entre par un côté** et ne va pas loin : le fond de la
         *    grotte est presque noir, et c'est ce noir qui donne l'échelle.
         * 5. **Un gros pilier de roche** sépare deux travées, et la voûte s'en
         *    va de part et d'autre.
         * 6. **Le sol est un chaos de blocs tombés**, mouillés, mangés de
         *    mousse.
         *
         * Sur la carte ouverte, la voûte et ses meules tiennent la bande du
         * haut, la bouche du jour la colonne de gauche et le pilier celle de
         * droite.
         */
        fun hohllay() {
            val r = Alea(295)
            // Le grès passe par [neutre], comme tout le bâti et toute la
            // roche dessinés à la main (règle 10) : sans cela `raviver` le
            // pousse à l'orange vif sur les quatre heures.
            val gres = neutre(duJour(0xFFB29070.toInt()))
            val mouille = neutre(duJour(0xFF4A4A38.toInt()))
            // Un noir **neutre**, écrit comme tel : obtenu en assombrissant
            // le grès, il passait par le brun de [sombre], que `raviver`
            // ressortait en or sale. Le fond d'une grotte n'a pas de couleur.
            val noir = neutre(duJour(0xFF201C17.toInt()))
            /** Le bord bas de la voûte : une arche qui culmine au milieu. */
            // Elle descend bas : la voûte de la Hohllay pèse sur la tête, et
            // c'est elle le sujet. Posée haut, il ne restait que onze unités
            // de plafond au milieu de la fenêtre, c'est-à-dire exactement là
            // où la carte ouverte le montre.
            fun arc(x: Float) = 22f + 0.0050f * (x - 50f) * (x - 50f)
            // ---- La bouche, à gauche : le jour n'entre que par là, et il
            // ne va pas loin. Elle est au lointain ; tout le reste est devant.
            mineral {
                feuille(-3f, -3f, 106f, 72f, sombre(mouille, 0.3f), 0f)
                pieceD("M-4 50C-2 44 1 39 4 35C7 32 11 30.6 13 32.4C14.4 34 13 38.6 12 42C11 46 10.6 48 11 51Z",
                    mix(P.cielBas, 0xFFFFFFFF.toInt(), if (P.nuit) 0.1f else 0.45f), 0f)
                forme("M-4 50C-2 45 0 41 2.6 38C5 35 8 33.6 9.6 35C10.6 36.6 9.6 39.6 9 43C8.4 46.4 8.2 48 8.4 51Z",
                    mix(vert(0.6f), P.cielBas, 0.3f), 0.8f)
            }
            plan(1)
            mineral {
                // Le fond de la grotte, posé au plan du milieu et non au
                // lointain : au lointain, l'estompe des plans le ramenait
                // vers la couleur du ciel et le noir tournait à l'ocre. Or
                // c'est ce noir qui donne l'échelle du lieu.
                pieceD("M13 -4L104 -4L104 68L13 68Z", noir, 0f)
                // La paroi du fond, à peine tirée du noir, avec ses propres
                // empreintes : sans elle le milieu est un trou découpé, et
                // la grotte n'a pas de fond, elle a une ouverture.
                val fond = mix(noir, gres, 0.14f)
                pieceD("M18 38C30 32 48 30 64 32C74 33 82 36 88 40L88 60L18 60Z", fond, 0f)
                for (k in 0 until 5) meule(24f + k * 16f + r() * 6f, 37f + r() * 8f,
                    3.4f + r() * 3.4f, 0.6f + r() * 0.24f, fond, 360 + k)
                forme("M18 38C30 32 48 30 64 32C74 33 82 36 88 40L88 44C80 40 70 37.6 60 37C44 35.6 30 37 18 42Z",
                    sombre(noir, 0.2f), 0.5f)
                // ---- La voûte. Elle n'est pas un bandeau sur lequel on
                // peint des ronds : c'est une suite de **bombements qui se
                // recouvrent**, chacun sa découpe, sa valeur et son ombre
                // portée sur le suivant. C'est la règle du grès de la skill
                // des matières : ce sont les ruptures qui creusent, pas les
                // lignes. D'un seul tenant, la voûte restait un mur beige
                // orné de cibles.
                //
                // Les bombements vont du clair au sombre en s'enfonçant vers
                // la droite : le jour entre par la bouche, à gauche.
                val voute = Path()
                voute.moveTo(-4f, -4f); voute.lineTo(104f, -4f)
                var x = 104f
                while (x > -4f) { voute.lineTo(x, arc(x)); x -= 3f }
                voute.lineTo(-4f, arc(-4f)); voute.close()
                piece(voute, mix(gres, noir, 0.4f))
                c.save(); c.clipPath(voute)
                releve?.let { it.save(); it.clipPath(voute) }
                for (k in 0 until 6) {
                    val cx = -6f + k * 22f + r() * 6f
                    val w = 13f + r() * 6f
                    val bas = arc(cx) + 1.5f - r() * 3f
                    val haut = bas - 9f - r() * 6f
                    val jour = ((cx + 10f) / 110f).coerceIn(0f, 1f)
                    val col = mix(mix(gres, neutre(duJour(0xFFC08A62.toInt())), 0.3f * r()), noir, 0.1f + jour * 0.42f)
                    val bombe = chemin("M${cx - w} -6L${cx - w} ${haut}Q${cx - w * 0.5f} ${bas + 1f} $cx ${bas}" +
                        "Q${cx + w * 0.55f} ${bas + 1f} ${cx + w} ${haut - 2f}L${cx + w} -6Z")
                    piece(bombe, col, 0.5f)
                    c.save(); c.clipPath(bombe)
                    releve?.let { it.save(); it.clipPath(bombe) }
                    // Deux ou trois empreintes par bombement, débordantes :
                    // c'est en débordant qu'elles se recouvrent, comme sur la
                    // photo, au lieu de s'aligner en pois.
                    for (i in 0 until 3) {
                        val mx = cx - w + r() * w * 2f
                        val my = haut - 2f + r() * (bas - haut + 4f)
                        meule(mx, my, 4.5f + r() * 5.5f, 0.5f + r() * 0.3f,
                            if (r() < 0.3f) mix(col, mouille, 0.35f) else col, 296 + k * 4 + i)
                    }
                    // Le suintement, au creux du bombement.
                    if (r() < 0.7f) {
                        val sx = cx - w * 0.5f + r() * w
                        forme("M$sx ${haut - 2f}C${sx + 2f} ${bas - 5f} ${sx - 2f} ${bas - 2f} ${sx + 1f} ${bas + 2f}" +
                            "L${sx + 3.5f + r() * 2f} ${bas + 2f}C${sx + 2f} ${bas - 3f} ${sx + 5f} ${bas - 6f} ${sx + 3f} ${haut - 2f}Z",
                            mouille, 0.3f + r() * 0.25f)
                    }
                    releve?.restore(); c.restore()
                }
                releve?.restore(); c.restore()
                // La lèvre de la voûte, dans l'ombre : c'est elle qui fait
                // avancer le plafond au-dessus du vide.
                val levre = Path()
                levre.moveTo(-4f, arc(-4f) - 1.4f)
                var lx = -4f
                while (lx < 104f) { lx = min(104f, lx + 3f); levre.lineTo(lx, arc(lx) - 1.4f) }
                while (lx > -4f) { levre.lineTo(lx, arc(lx)); lx = max(-4f, lx - 3f) }
                levre.lineTo(-4f, arc(-4f)); levre.close()
                forme(levre, sombre(gres, 0.55f), 0.5f)
            }
            plan(2)
            solNaturel(SolNaturel.TERRE)  // le sol de sable et de blocs
            poser(DEVANT)
            mineral {
                /** Un montant de roche, de [x0] à [x1], qui descend de la voûte au sol. */
                fun pilier(x0: Float, x1: Float, taille: Float, graine: Int, col: Int) {
                    val g = Alea(graine)
                    val w = x1 - x0
                    // Il est galbé, et jamais d'aplomb : un montant droit fait
                    // une colonne, et la grotte redevient une crypte.
                    pieceD("M${x0 + w * 0.06f} 60L${x0 - w * 0.08f} 46C${x0 + w * 0.16f} 38 ${x0 - w * 0.04f} 28 ${x0 + w * 0.1f} ${arc(x0) + 1f}" +
                        "C${x0 + w * 0.3f} ${arc(x0) - 3f} ${x1 - w * 0.25f} ${arc(x1) - 3f} ${x1 - w * 0.06f} ${arc(x1) + 1f}" +
                        "L${x1 - w * 0.16f} 32C${x1 + w * 0.1f} 42 ${x1 - w * 0.12f} 52 $x1 60Z", col)
                    for (k in 0 until 4) {
                        val my = 16f + k * 10f + g() * 5f
                        meule(x0 + w * (0.25f + g() * 0.5f), my, w * (0.3f + g() * 0.22f), 0.78f + g() * 0.2f,
                            if (my > 34f) mix(col, mouille, 0.45f) else col, graine + 40 + k)
                    }
                    // Le flanc à l'ombre, du côté opposé au jour.
                    forme("M${x1 - w * 0.34f} ${arc(x1) + 1f}L${x1 - w * 0.06f} ${arc(x1) + 1f}" +
                        "L${x1 - w * 0.16f} 32C${x1 + w * 0.1f} 42 ${x1 - w * 0.12f} 52 $x1 60L${x1 - w * 0.3f} 60Z",
                        sombre(col, 0.45f), 0.5f)
                    // Le pied vert : tout ce qui est bas est mouillé.
                    forme("M${x0 + w * 0.05f} 60C${x0 + w * 0.1f} 50 ${x1 - w * 0.2f} 48 ${x1 - w * 0.05f} 60Z",
                        mix(col, duJour(0xFF5E7A3A.toInt()), 0.5f), 0.55f)
                }
                // Celui de gauche est au jour, celui de droite à
                // contre-jour : ils n'ont pas la même valeur, et c'est ce qui
                // creuse la grotte entre les deux.
                pilier(10f, 24f, 1f, 310, gres)
                pilier(74f, 92f, 1f, 330, mix(gres, noir, 0.42f))
                // La paroi du fond, derrière le pilier de droite : une
                // seconde travée, plus sombre, qui ouvre la grotte.
                pieceD("M92 60L93 26C96 22 100 22 104 25L104 60Z", mix(noir, gres, 0.18f), 0.4f)
            }
            // ---- Le sol : des blocs tombés, mouillés, mangés de mousse.
            val bloc = mix(neutre(duJour(0xFF8A7C66.toInt())), noir, 0.3f)
            mineral {
                pieceD("M-4 60L-4 48C14 45 34 46 52 48C70 50 88 49 104 46L104 60Z", sombre(bloc, 0.55f), 0.35f)
                for (k in 0 until 7) {
                    val bx = -5f + r() * 112f
                    val by = 48f + r() * 9f
                    val bw = 5f + r() * 11f
                    val bh = 2.4f + r() * 3.4f
                    pieceD("M$bx ${by + bh}L${bx + bw * 0.12f} ${by + bh * 0.2f}L${bx + bw * 0.5f} $by" +
                        "L${bx + bw * 0.92f} ${by + bh * 0.3f}L${bx + bw} ${by + bh}Z",
                        if (r() < 0.5f) sombre(bloc, 0.14f) else sombre(bloc, 0.34f), 0.5f)
                }
            }
            val mousse = mix(duJour(0xFF5E7A2E.toInt()), mouille, 0.52f)
            // La mousse s'étale en plaques rasantes, elle ne pousse pas en
            // boules : des ovales très aplatis, posés sur les blocs.
            for (k in 0 until 16) {
                val gx = -4f + r() * 112f
                val gy = 48f + r() * 10f
                ovalePapier(gx, gy, 2.2f + r() * 3.4f, 0.5f + r() * 0.7f,
                    if (r() < 0.4f) mix(mousse, duJour(0xFF9CBE52.toInt()), 0.28f) else mousse, 0f)
            }
            c.restore()
        }

        // ---- Napoleonsgaart, la tour sur la butte (photo du propriétaire)

        /**
         * Ce que dit la photo, et que l'ancienne version n'avait pas :
         *
         * 1. **La tour est un ouvrage de pierre, massif et élancé** : trois
         *    fois et demie plus haute que large, en moellons assisés, avec
         *    des chaînes d'angle, trois bandes de petites baies en creux, une
         *    corniche et un parapet. C'était un poteau brun à quatre points.
         * 2. **Elle est posée sur une butte d'herbe**, pas sur un pré plat.
         *    La butte a son mur de soutènement au pied et une clôture de
         *    grillage sur sa crête, et c'est elle qui donne la hauteur.
         * 3. **Le chemin monte droit vers elle.** Il part du bas de la
         *    fenêtre, large, et se referme en arrivant au pied : c'est la
         *    seule fuite du lieu, et elle vaut tout le reste.
         * 4. **Deux mâts d'acier l'accompagnent** : un pylône treillis au
         *    bord du champ, et un mât d'antenne plus mince à côté de la tour.
         *    Ils sont du lieu, pas une gêne.
         * 5. **Le ciel est grand et nuageux**, mais il ne doit pas manger la
         *    tour : trois cumulus posés bas, pas une couverture.
         *
         * Sur la carte ouverte, la tour tient la colonne de droite sur toute
         * sa hauteur, le pylône celle de gauche, et le chemin arrive entre
         * les deux.
         */
        fun napoleonsgaart() {
            val r = Alea(275)
            // Trois cumulus posés haut et courts : larges, ils se
            // rejoignaient en une couverture crème et la tour s'y noyait.
            cumulus(2f, 14f, 20f, 276)
            cumulus(30f, 9f, 22f, 277)
            cumulus(56f, 16f, 17f, 278)
            // L'horizon est bas et lointain : on est sur le haut d'une butte,
            // et le pays s'en va de tous les côtés.
            lisiere(-3f, 103f, { x -> 33f + 1.8f * sin(x * 0.08f) }, 1.6f, 2.6f, 279,
                mix(nappe(duJour(0xFF4E6B38.toInt()), 0.34f, VERT_CALME), P.loin, 0.46f))
            plan(1)
            // ---- La butte, et son mur de soutènement.
            val pre = vert(0.7f)
            pieceD("M-3 66L-3 48C14 46 30 45 44 43C56 41 64 36 74 32C82 29 92 29 103 33L103 66Z", pre, 0.4f)
            // Le flanc de la butte est dans l'ombre sous la tour : sans cela
            // elle est un décor posé à plat et non un monticule.
            // L'ombre du flanc passe par le lointain et non par [sombre] :
            // le brun de [sombre] sur un vert d'herbe donne un kaki de toile
            // de tente, et toute la butte s'éteignait.
            forme("M44 43C56 41 64 36 74 32C82 29 92 29 103 33L103 38C92 34.6 82 34.6 74 37C64 40 56 44 46 45.6Z",
                mix(pre, P.loin, 0.26f), 0.94f)
            val pierre = duJour(0xFF8E887E.toInt())
            mineral {
                // Le mur de soutènement, au pied de la tour, en moellons.
                appareil(chemin("M58 43.6C66 39.8 74 35.8 82 34.2C88 33.4 94 33.6 99 34.8L99 38.6C94 37.6 88 37.4 82 38.2C74 39.6 66 43.4 59 47Z"),
                    sombre(pierre, 0.12f), 1.1f, 2.2f, 280, pente = -0.05f, lichen = 0.3f, ombre = 0.5f)
            }
            // La clôture de grillage sur la crête : des poteaux fins et une
            // maille qu'on voit au travers. Pleine, elle ferait un muret.
            val acier = neutre(duJour(0xFF9DA3A8.toInt()))
            fun crete(x: Float) = if (x < 74f) 43f - (x - 44f) * 0.37f else 32f + (x - 74f) * 0.14f
            var gx = 44f
            while (gx < 103f) {
                val y = crete(gx)
                ligne(gx, y, gx, y - 5.4f, acier, 0.22f, 0.85f)
                ligne(gx, y - 5.4f, min(103f, gx + 5f), crete(min(103f, gx + 5f)) - 5.4f, acier, 0.18f, 0.8f)
                ligne(gx, y - 2.6f, min(103f, gx + 5f), crete(min(103f, gx + 5f)) - 2.6f, acier, 0.12f, 0.5f)
                // La maille : deux obliques par travée suffisent à la dire.
                for (u in floatArrayOf(0f, 2.5f)) {
                    ligne(gx + u, y, gx + u + 2.5f, y - 5.4f, acier, 0.1f, 0.3f)
                    ligne(gx + u, y - 5.4f, gx + u + 2.5f, y, acier, 0.1f, 0.3f)
                }
                gx += 5f
            }
            // ---- La tour. Un socle évasé, le fût, la corniche, le parapet.
            val taille = neutre(duJour(0xFF8A8278.toInt()))
            val joint = sombre(taille, 0.42f)
            mineral {
                // Le socle, plus large, et son appareil de gros moellons.
                boite3q(71f, 33f, 13f, 6f, 5f, sombre(taille, 0.14f), pente = 0.05f)
                // La porte, dans le socle : la seule couleur franche du lieu.
                feuille(76.4f, 29.4f, 2.4f, 3.6f, duJour(0xFF2F5FB0.toInt()), 0.3f)
                // Le fût, de trois quarts, avec ses baies peintes à plat.
                boite3q(74f, 28f, 9f, 24f, 4.2f, taille, pente = 0.06f) {
                    // Les chaînes d'angle : deux bandes claires qui tiennent
                    // la tour debout. Sans elles, le fût est une planche.
                    // Les moellons sont petits et nombreux : à deux unités
                    // de long sur neuf de large, ils faisaient six briques
                    // par assise et la tour passait pour un mur de jardin.
                    // Peu de lichen : la valeur par défaut verdit une pierre
                    // sur cinq, et la tour prenait une mousse de sous-bois
                    // qu'elle n'a pas, posée en plein vent sur une butte.
                    appareil(Path().apply { addRect(0f, 0f, 9f, 24f, Path.Direction.CW) },
                        sombre(taille, 0.1f), 0.78f, 1.4f, 281, lichen = 0.06f)
                    rect(0f, 0f, 1.1f, 24f, mix(taille, 0xFFFFFFFF.toInt(), 0.18f), 0.6f)
                    rect(7.9f, 0f, 1.1f, 24f, sombre(taille, 0.2f), 0.6f)
                    // Les trois bandes de baies, en creux : le tableau à
                    // l'ombre d'abord, le jour de la baie ensuite.
                    for (by in floatArrayOf(5.2f, 11.4f, 17.6f)) {
                        rect(1.6f, by - 0.5f, 5.8f, 2.6f, joint, 0.45f)
                        for (k in 0 until 3) rect(1.9f + k * 1.9f, by, 1.2f, 1.6f,
                            if (P.allume) P.fenetre else sombre(taille, 0.72f))
                    }
                }
                // La corniche et le parapet, un peu plus larges que le fût.
                boite3q(73.4f, 4.4f, 10.2f, 1.6f, 4.6f, mix(taille, 0xFFFFFFFF.toInt(), 0.1f), pente = 0.06f)
                boite3q(74.4f, 2.8f, 8.2f, 2.2f, 4f, sombre(taille, 0.08f), pente = 0.06f)
            }
            // Le garde-corps du belvédère et le petit mât, sur la plate-forme.
            for (px in floatArrayOf(74.6f, 77.4f, 80.2f, 82.6f)) ligne(px, 2.8f, px, 0.6f, acier, 0.2f)
            ligne(74.4f, 0.8f, 82.8f, 0.4f, acier, 0.22f)
            ligne(81f, 0.6f, 81.4f, -3f, acier, 0.25f)
            plan(2)
            solNaturel(SolNaturel.HERBE)  // le pré du haut de la butte
            poser(DEVANT)
            // ---- Les deux talus, puis le chemin entre eux.
            //
            // Les talus sont des pièces **opaques**, et non des voiles posés
            // sur le pré. Un voile peint dans le plan du proche se compose
            // avec le plan du milieu qui est derrière lui, et non avec le
            // vert qu'on voit dessous : le talus de droite, voilé à trois
            // dixièmes, sortait kaki sur toute la moitié basse de la carte.
            // Dans un théâtre de papier, une pente est une découpe de plus.
            forme("M-3 60L-3 54C10 52 24 50 36 47.6L56 42L66.6 40.6L4 60Z", mix(pre, P.loin, 0.16f), 0.94f)
            forme("M30 60L72 41.4L80 40C88 41 96 43 103 46L103 60Z",
                mix(pre, duJour(0xFF63A33C.toInt()), 0.3f), 0.94f)
            // Le chemin. Large en bas, il se referme au pied de la tour :
            // c'est la seule fuite du lieu, et c'est elle qui fait monter.
            val bitume = neutre(duJour(0xFF5A5A5C.toInt()))
            pieceD("M4 60L30 60L72 41.4L66.6 40.6Z", bitume, 0.45f)
            // L'usure du milieu, plus claire, et les deux bords mangés
            // d'herbe : un ruban gris uni se lit comme une planche posée.
            forme("M14 60L24 60L70 41.2L67.6 40.8Z", mix(bitume, 0xFFFFFFFF.toInt(), 0.16f), 0.4f)
            forme("M4 60L7 60L67.4 40.6L66.6 40.6Z", sombre(bitume, 0.3f), 0.5f)
            forme("M28 60L30 60L72 41.4L71 41.2Z", sombre(bitume, 0.3f), 0.5f)
            // L'herbe des talus, brin par brin. C'est elle qui fait le vert
            // du lieu : la bande sous la fenêtre, peinte par [solNaturel],
            // est drue et vive, et au-dessus l'aplat seul sortait kaki à
            // côté d'elle. Les brins s'arrêtent au bord du chemin.
            for (k in 0 until 170) {
                val hx = -3f + r() * 108f
                val hy = 41f + r() * 18f
                val tl = ((60f - hy) / 19.4f).coerceIn(0f, 1f)
                val tr = ((60f - hy) / 18.6f).coerceIn(0f, 1f)
                if (hx > 4f + 62.6f * tl && hx < 30f + 42f * tr) continue
                ligne(hx, hy, hx + (r() - 0.5f) * 1.2f, hy - 1.1f - r() * 1.8f,
                    if (r() < 0.5f) mix(pre, duJour(0xFFDCEE8E.toInt()), 0.34f) else sombre(pre, 0.22f),
                    0.2f + r() * 0.14f, 0.8f)
            }
            // ---- Les deux aciers du lieu : le grand pylône treillis au bord
            // du champ, et le mât d'antenne qui double la tour.
            pylone(6f, 52f, -4f, 7f, sombre(acier, 0.3f))
            pylone(64f, 36f, 9f, 2.4f, sombre(acier, 0.22f))
            ligne(64f, 12f, 76f, 16f, sombre(acier, 0.22f), 0.14f, 0.7f)
            touffe(vert(0.4f), 100f, 46f, 5f, 96f, 52f, 4f)
            c.restore()
        }

        // ---- Kallektuffquell, la source de tuf du Mullerthal

        /**
         * Ce qu'est le lieu, et que l'ancienne version n'avait pas. C'était
         * la carte la plus en retard de l'album : une palissade ocre rayée de
         * traits verticaux sous un aplat vert pomme, devant une nappe
         * turquoise opaque.
         *
         * 1. **Le tuf n'est pas un mur droit, c'est un dôme en surplomb.**
         *    L'eau chargée de calcaire dépose sa pierre en lèvres qui
         *    avancent, l'une au-dessus de l'autre, et chacune porte son ombre
         *    dessous. Ce sont ces lèvres et leurs ombres qui font le lieu.
         * 2. **La mousse en fait la couleur** : des coussins qui débordent de
         *    chaque lèvre et pendent devant la pierre, du vert tendre au vert
         *    sombre.
         * 3. **L'eau ne coule pas, elle goutte.** Un rideau de gouttes tombe
         *    des surplombs : c'est ce qui vit sur la carte, et une secousse
         *    fait déborder la source.
         * 4. **C'est un sous-bois sombre**, fermé par la futaie, avec des
         *    hêtres clairs et des fougères — pas un pré au soleil.
         * 5. **Le bassin est transparent** : une eau peu profonde posée en
         *    voile sur ses galets, qu'on voit au travers, comme celle du
         *    Mëllerdall.
         *
         * Sur la carte ouverte, le dôme tient la bande du haut et la colonne
         * de droite, les hêtres et les fougères celle de gauche.
         */
        fun kallektuffquell() {
            val r = Alea(287)
            /** Le ruban compris entre les courbes [haut] et [bas], de [x0] à [x1]. */
            fun ruban(x0: Float, x1: Float, haut: (Float) -> Float, bas: (Float) -> Float): Path {
                val t = Path()
                t.moveTo(x0, haut(x0))
                var x = x0
                while (x < x1) { x = min(x1, x + 2f); t.lineTo(x, haut(x)) }
                while (x > x0) { t.lineTo(x, bas(x)); x = max(x0, x - 2f) }
                t.lineTo(x0, bas(x0))
                t.close()
                return t
            }
            // ---- La futaie ferme le ciel : deux rideaux, du clair au fond
            // au sombre devant. Le jour n'entre que par le haut à gauche.
            lisiere(-3f, 103f, { x -> 6f + 3.2f * sin(x * 0.062f + 1.2f) }, 2.2f, 3.6f, 286,
                mix(nappe(duJour(0xFF345A28.toInt()), 0.42f, VERT_CALME), P.loin, 0.36f))
            plan(1)
            lisiere(-3f, 103f, { x -> 12.5f + 2.8f * sin(x * 0.085f + 3f) }, 2.4f, 4f, 288,
                sombre(nappe(duJour(0xFF2C5024.toInt()), 0.46f, VERT_CALME), 0.2f))
            // Des fûts dans la futaie du fond : sans eux, le sous-bois est
            // un aplat vert sombre en travers de la fenêtre.
            for (i in 0 until 9) {
                val fx = -2f + r() * 106f
                ligne(fx, 10f + r() * 4f, fx + (r() - 0.5f) * 2f, 30f + r() * 8f,
                    if (r() < 0.5f) sombre(duJour(0xFF55503F.toInt()), 0.3f) else mix(duJour(0xFF8A8268.toInt()), P.loin, 0.4f),
                    0.5f + r() * 0.7f, 0.5f)
            }
            // ---- Le surplomb de tuf. Un seul, grand, plutôt que quatre
            // petites lèvres : empilées, elles se lisaient comme les
            // terrasses d'un talus de terre. Ce qu'il faut voir, c'est
            // **une masse de mousse qui avance au-dessus d'un creux noir**,
            // et l'eau qui tombe de son bord.
            val tuf = neutre(duJour(0xFFBCB19C.toInt()))
            val noirTuf = neutre(duJour(0xFF2C2A26.toInt()))
            val mousse = nappe(duJour(0xFF63982A.toInt()), 0.58f, 0.3f)
            val mousseClaire = mix(mousse, duJour(0xFFD4E86C.toInt()), 0.3f)
            val eauClaire = mix(0xFFFFFFFF.toInt(), duJour(0xFF9CC8D8.toInt()), 0.45f)
            /** Le fond du creux sous le surplomb : au-delà, la paroi ruisselante. */
            fun creuxTuf(x: Float) = souslevreTuf(x) + 5.6f + 2.6f * sin(x * 0.07f + 1f)
            mineral {
                // La paroi du fond, ruisselante, d'abord : tout le reste se
                // pose devant elle.
                piece(ruban(24f, 104f, { x -> souslevreTuf(x) }, { 50f }),
                    mix(sombre(tuf, 0.34f), mousse, 0.14f), 0.5f)
                // Les côtes de tuf : l'eau dépose sa pierre en coulées
                // verticales. Elles ne partent **pas toutes du même niveau**
                // et leurs tons sont franchement écartés : d'égale longueur
                // et de tons voisins, elles donnaient un store de bambou.
                var cx = 24f
                while (cx < 104f) {
                    val l = 0.9f + r() * 2.4f
                    val haut = creuxTuf(cx) - 1f + r() * r() * 11f
                    val t = r()
                    forme(ruban(cx, cx + l, { haut }, { 50f }), when {
                        t < 0.3f -> sombre(tuf, 0.34f + t * 0.5f)
                        t < 0.58f -> mix(sombre(tuf, 0.2f), mousse, 0.22f)
                        t < 0.84f -> mix(tuf, 0xFFFFFFFF.toInt(), 0.1f + t * 0.2f)
                        else -> tuf
                    }, 0.92f)
                    cx += l
                }
                // Les traînées mouillées, par-dessus : c'est l'eau qui
                // descend partout, pas seulement là où elle goutte.
                for (i in 0 until 10) {
                    val wx = 24f + r() * 80f
                    forme(ruban(wx, wx + 1.4f + r() * 2.6f, { creuxTuf(wx) + r() * 4f }, { 50f }),
                        sombre(tuf, 0.55f), 0.3f)
                }
                // Le creux sous le surplomb : c'est lui, et rien d'autre, qui
                // dit que la pierre avance au-dessus du vide.
                piece(ruban(24f, 104f, { x -> souslevreTuf(x) - 0.4f }, { x -> creuxTuf(x) }), noirTuf, 0.5f)
                forme(ruban(24f, 104f, { x -> creuxTuf(x) - 2.2f }, { x -> creuxTuf(x) }),
                    mix(noirTuf, tuf, 0.3f), 0.5f)
                // La lèvre elle-même, pâle et éclairée sur son dessus.
                piece(ruban(22f, 104f, { x -> levreTuf(x) }, { x -> souslevreTuf(x) }), tuf, 0.5f)
                forme(ruban(22f, 104f, { x -> levreTuf(x) }, { x -> levreTuf(x) + 1f }),
                    mix(tuf, 0xFFFFFFFF.toInt(), 0.3f), 0.8f)
                for (i in 0 until 16) {
                    val ax = 24f + r() * 80f
                    ovalePapier(ax, levreTuf(ax) + 1.4f + r() * 1.6f, 0.4f + r() * 0.7f, 0.3f + r() * 0.4f,
                        sombre(tuf, 0.4f), 0f)
                }
            }
            // Le film d'eau qui luit sur la paroi, tiré vers le bleu : un
            // blanc pur jaunirait sur le grain chaud.
            for (i in 0 until 14) {
                val wx = 24f + r() * 80f
                ligne(wx, creuxTuf(wx), wx + (r() - 0.5f) * 1.6f, 49f, eauClaire, 0.3f + r() * 0.3f, 0.3f)
            }
            // ---- La mousse : des coussins à cheval sur la lèvre, qui
            // débordent dessus et pendent devant le creux. C'est elle la
            // couleur du lieu, et elle n'est pas minérale : la saison la
            // prend comme le reste de la verdure.
            var mx = 21f
            while (mx < 105f) {
                val my = levreTuf(mx)
                val e = 1.4f + r() * 1.8f
                touffe(if (r() < 0.38f) mousseClaire else if (r() < 0.76f) mousse else sombre(mousse, 0.26f),
                    mx, my - e * 0.5f, e,
                    mx + e * 1.3f, my - e * 0.1f, e * 0.8f,
                    mx - e * 1.2f, my - e * 0.2f, e * 0.75f,
                    mx + e * 0.2f, my + e * 0.9f, e * 0.7f)
                mx += 3.4f + r() * 3.4f
            }
            // Quelques franges qui pendent dans le creux, plus bas.
            for (i in 0 until 9) {
                val fx = 24f + r() * 80f
                val fy = souslevreTuf(fx) + 0.6f
                ligne(fx, fy, fx + (r() - 0.5f) * 1.2f, fy + 1.6f + r() * 2.6f,
                    sombre(mousse, 0.34f), 0.5f + r() * 0.5f, 0.9f)
            }
            // ---- Le rideau de gouttes, peint aussi sur l'image fixe : la
            // [Vie] les fait tomber sur la carte ouverte, mais la vignette
            // et la carte imprimée ne bougent pas, et sans elles rien ne dit
            // qu'une source coule là.
            if (!vivant) for (gx in GOUTTES_TUF) {
                val gy = souslevreTuf(gx)
                val h = 6f + r() * 8f
                // Un filet clair doublé d'une ombre : seul, le trait pâle
                // disparaissait sur le tuf, qui est pâle lui aussi.
                ligne(gx + 0.3f, gy, gx + 0.3f, gy + h, sombre(tuf, 0.5f), 0.3f, 0.35f)
                ligne(gx, gy, gx, gy + h, eauClaire, 0.34f, 0.8f)
                rond(gx, gy + h + 2f + r() * 6f, 0.45f, eauClaire, 0.95f)
            }
            plan(2)
            solNaturel(SolNaturel.MOUSSE, DEVANT)  // le sol détrempé de la source
            poser(DEVANT)
            // ---- Le bassin : son lit de galets d'abord, l'eau posée dessus
            // en voile. Une nappe opaque en faisait une piscine ; ici on voit
            // le fond au travers, et c'est ce qui dit qu'elle est peu profonde.
            fun bordBassin(x: Float) = 47.5f - (x - 20f) * 0.02f + 0.7f * sin(x * 0.16f)
            val galet = neutre(duJour(0xFFB4ACA0.toInt()))
            mineral {
                piece(ruban(-4f, 104f, { x -> bordBassin(x) }, { 62f }), sombre(galet, 0.34f), 0.4f)
                for (i in 0 until 30) {
                    val gx = -4f + r() * 108f
                    val gy = bordBassin(gx) + 0.8f + r() * 9f
                    val t = (gy - bordBassin(gx)) / 10f
                    ovalePapier(gx, gy, 0.7f + t * 1.6f + r() * 0.5f, 0.4f + t * 0.9f + r() * 0.3f,
                        if (r() < 0.5f) sombre(galet, 0.2f) else mix(galet, 0xFFFFFFFF.toInt(), 0.18f), 0f)
                }
            }
            val eau = mix(duJour(0xFF5E9E92.toInt()), P.eau, 0.3f)
            forme(ruban(-4f, 104f, { x -> bordBassin(x) }, { 62f }), eau, 0.42f)
            forme(ruban(-4f, 104f, { x -> bordBassin(x) }, { x -> bordBassin(x) + 0.8f }),
                mix(eau, eauClaire, 0.6f), 0.55f)
            for (i in 0 until 9) {
                val wx = -4f + r() * 104f
                rect(wx, bordBassin(wx) + 2f + r() * 7f, 3f + r() * 7f, 0.3f, eauClaire, 0.4f)
            }
            // ---- Les hêtres du bord gauche, ceux au travers desquels on
            // regarde : des fûts clairs et lisses qui sortent de la fenêtre.
            val ecorce = neutre(duJour(0xFF9A9287.toInt()))
            for ((i, tx) in floatArrayOf(6f, 16.5f, 97f).withIndex()) {
                val l = 3.4f - i * 0.6f
                pieceD("M${tx - l * 0.5f} -3L${tx + l * 0.5f} -3L${tx + l * 0.36f} 52L${tx - l * 0.42f} 52Z",
                    ecorce, 0.6f)
                rect(tx + l * 0.12f, -3f, l * 0.3f, 55f, sombre(ecorce, 0.3f), 0.8f)
                rect(tx - l * 0.46f, -3f, l * 0.16f, 55f, mix(ecorce, 0xFFFFFFFF.toInt(), 0.22f), 0.7f)
                for (j in 0 until 4) ovalePapier(tx + (r() - 0.5f) * l, 4f + j * 12f + r() * 6f,
                    0.5f + r() * 0.6f, 0.26f + r() * 0.3f, sombre(ecorce, 0.26f), 0f)
            }
            // ---- Les fougères du pied, drues à gauche où l'on se tient.
            for (i in 0 until 12) {
                val fx = if (i % 3 == 0) 80f + r() * 26f else -4f + r() * 26f
                val fy = 50f + r() * 8f
                val h = 5f + r() * 5f
                val sens = if (r() < 0.5f) 1f else -1f
                val tige = if (r() < 0.4f) mousseClaire else sombre(mousse, 0.18f)
                ligne(fx, fy, fx + sens * h * 0.35f, fy - h, tige, 0.3f, 0.95f)
                for (j in 1..6) {
                    val u = j / 6f
                    val px = fx + sens * h * 0.35f * u
                    val py = fy - h * u
                    val l = h * 0.3f * (1f - u * 0.65f)
                    ligne(px, py, px - sens * l, py - l * 0.55f, tige, 0.22f, 0.9f)
                    ligne(px, py, px + sens * l * 0.8f, py - l * 0.4f, tige, 0.22f, 0.9f)
                }
            }
            c.restore()
        }

        // ---- Péitrusdall, la Passerelle vue du fond de la vallée (photo du propriétaire)

        /**
         * Ce que dit la photo, et que l'ancienne version n'avait pas :
         *
         * 1. **Ce n'est pas le pont Adolphe.** La carte montrait une copie de
         *    la grande arche surbaissée de la [neiBreck], à deux cases de là
         *    dans l'album. Le pont de la Pétrusse, c'est **la Passerelle**,
         *    le vieux viaduc : une file d'arches en plein cintre portées par
         *    des piles très hautes et très minces.
         * 2. **On la regarde d'en bas**, du fond de la vallée, en forte
         *    contre-plongée : le tablier barre le haut de la fenêtre et les
         *    piles descendent hors du cadre.
         * 3. **Le tablier est moderne et il déborde.** Une dalle claire,
         *    posée sur le vieil ouvrage, portée de part et d'autre par des
         *    **béquilles d'acier en Y** au droit de chaque pile. C'est ce
         *    détail-là qui fait reconnaître la Passerelle d'aujourd'hui.
         * 4. **Les arches montrent leurs claveaux** rayonnants, et les
         *    tympans sont en appareil régulier de pierre claire.
         * 5. **On voit le ciel au travers**, et la cime des arbres de la
         *    vallée au bas des ouvertures.
         * 6. **Un éperon de rocher tient le pied de gauche**, avec son mur
         *    de soutènement et son lierre.
         *
         * Sur la carte ouverte, le tablier, les béquilles et le haut des
         * arches tiennent la bande du haut, et une pile chaque colonne.
         */
        fun peitrusdall() {
            val r = Alea(295)
            /** Le dessus du tablier en [x] : il descend vers la gauche et s'incurve. */
            fun tabl(x: Float) = 7.2f - x * 0.036f + 0.00012f * (x - 50f) * (x - 50f)
            val naissance = 26.5f    // la ligne de naissance des arches
            val rayon = 9.5f
            val axes = floatArrayOf(-2f, 24f, 50f, 76f, 102f)   // les piles
            val arches = floatArrayOf(11f, 37f, 63f, 89f)
            // ---- Ce qu'on voit au travers : le ciel, et la cime des arbres
            // de la vallée au bas des ouvertures.
            // Un carré de ciel haut posé dans chaque ouverture a été
            // essayé, pour dire la contre-plongée : il écrase le dégradé du
            // ciel en un aplat et les quatre arches deviennent quatre
            // vitres dépolies. Le ciel de la fenêtre passe au travers tel
            // quel, c'est mieux.
            //
            // La cime des arbres de la vallée, basse : posée haut, la futaie
            // bouchait les quatre ouvertures et le viaduc devenait un mur.
            lisiere(-4f, 104f, { x -> 44f + 1.6f * sin(x * 0.13f) }, 1.6f, 2.6f, 296,
                mix(nappe(duJour(0xFF4A6B30.toInt()), 0.36f, VERT_CALME), P.loin, 0.44f))
            plan(1)
            // ---- Le viaduc. La pierre passe par [neutre], comme tout le
            // bâti : sans cela `raviver` la tire vers l'ocre de la vallée
            // derrière elle et l'ouvrage s'y fond (règle 10).
            val pierre = neutre(duJour(0xFFCFC6B2.toInt()))
            val acier = neutre(duJour(0xFF55585E.toInt()))
            mineral {
                // La masse : tympans et piles d'un seul tenant, **percée**
                // de ses arches. Peintes par-dessus un mur plein, les arches
                // ne seraient que des trous noirs dessinés.
                val masse = Path()
                masse.moveTo(-6f, 64f)
                masse.lineTo(-6f, tabl(-6f) + 3.2f)
                var x = -6f
                while (x < 106f) { x = min(106f, x + 4f); masse.lineTo(x, tabl(x) + 3.2f) }
                masse.lineTo(106f, 64f); masse.close()
                for (cx in arches) {
                    val trou = Path()
                    trou.moveTo(cx - rayon, 64f)
                    trou.lineTo(cx - rayon, naissance)
                    trou.arcTo(RectF(cx - rayon, naissance - rayon, cx + rayon, naissance + rayon), 180f, 180f)
                    trou.lineTo(cx + rayon, 64f)
                    trou.close()
                    masse.op(trou, Path.Op.DIFFERENCE)
                }
                appareil(masse, pierre, 1.15f, 2.3f, 297, lichen = 0.1f, ombre = 0.5f)
                // Les claveaux de chaque arche : ils rayonnent depuis le
                // centre, ils ne suivent pas les assises du tympan.
                for ((k, cx) in arches.withIndex())
                    voussoirs(cx, naissance, rayon, rayon, 1.8f, 0f, PI.toFloat(), pierre, 298 + k)
                // Le tableau de l'arche, à l'ombre : l'épaisseur de l'ouvrage,
                // et c'est elle qui dit qu'on voit au travers.
                for (cx in arches) {
                    val creux = Path()
                    creux.moveTo(cx - rayon, 64f)
                    creux.lineTo(cx - rayon, naissance)
                    creux.arcTo(RectF(cx - rayon, naissance - rayon, cx + rayon, naissance + rayon), 180f, 180f)
                    creux.lineTo(cx + rayon, 64f)
                    creux.close()
                    val dedans = Path()
                    dedans.moveTo(cx - rayon + 1.2f, 64f)
                    dedans.lineTo(cx - rayon + 1.2f, naissance)
                    dedans.arcTo(RectF(cx - rayon + 1.2f, naissance - rayon + 1.2f, cx + rayon - 1.2f, naissance + rayon - 1.2f), 180f, 180f)
                    dedans.lineTo(cx + rayon - 1.2f, 64f)
                    dedans.close()
                    dedans.offset(0.9f, 0.9f)
                    creux.op(dedans, Path.Op.DIFFERENCE)
                    forme(creux, sombre(pierre, 0.55f), 0.7f)
                }
                // Les piles : leur arête au jour, leur flanc à l'ombre. Sans
                // ces deux-là, une pile de trente unités de haut est une
                // bande de papier posée debout.
                for (px in axes) {
                    forme("M${px - 3.6f} $naissance L${px - 2.2f} $naissance L${px - 2.8f} 64L${px - 4.6f} 64Z",
                        mix(pierre, 0xFFFFFFFF.toInt(), 0.16f), 0.6f)
                    forme("M${px + 2f} $naissance L${px + 3.6f} $naissance L${px + 4.6f} 64L${px + 2.6f} 64Z",
                        sombre(pierre, 0.34f), 0.55f)
                }
            }
            // ---- Les béquilles d'acier en Y, sous la dalle, au droit de
            // chaque pile : c'est par elles que le tablier d'aujourd'hui
            // déborde du vieil ouvrage.
            for (px in axes) {
                val sous = tabl(px) + 3.2f
                ligne(px, 17.5f, px - 5.4f, sous, acier, 0.5f)
                ligne(px, 17.5f, px + 5.4f, sous, acier, 0.5f)
                ligne(px, 17.5f, px, 21.5f, acier, 0.55f)
                feuille(px - 1.1f, 16.9f, 2.2f, 1.4f, acier, 0.3f)
            }
            // ---- La dalle du tablier, par-dessus tout : sa tranche claire,
            // son dessous à l'ombre, son garde-corps à claire-voie.
            mineral {
                val beton = neutre(duJour(0xFFC3BDB1.toInt()))
                val dalle = Path()
                dalle.moveTo(-6f, tabl(-6f))
                var x = -6f
                while (x < 106f) { x = min(106f, x + 4f); dalle.lineTo(x, tabl(x)) }
                while (x > -6f) { dalle.lineTo(x, tabl(x) + 3.2f); x = max(-6f, x - 4f) }
                dalle.lineTo(-6f, tabl(-6f) + 3.2f); dalle.close()
                piece(dalle, beton, 0.5f)
                val dessous = Path()
                dessous.moveTo(-6f, tabl(-6f) + 1.9f)
                x = -6f
                while (x < 106f) { x = min(106f, x + 4f); dessous.lineTo(x, tabl(x) + 1.9f) }
                while (x > -6f) { dessous.lineTo(x, tabl(x) + 3.2f); x = max(-6f, x - 4f) }
                dessous.lineTo(-6f, tabl(-6f) + 3.2f); dessous.close()
                forme(dessous, sombre(beton, 0.42f), 0.55f)
            }
            // Le garde-corps : des montants fins et deux filets. Plein, il
            // ferait un bandeau de plus au-dessus de la dalle.
            var gx = -5f
            while (gx < 105f) { ligne(gx, tabl(gx), gx, tabl(gx) - 2.2f, acier, 0.18f, 0.8f); gx += 3.4f }
            ligne(-5f, tabl(-5f) - 2.2f, 105f, tabl(105f) - 2.2f, acier, 0.22f)
            ligne(-5f, tabl(-5f) - 1.1f, 105f, tabl(105f) - 1.1f, acier, 0.14f, 0.6f)
            // Les candélabres du tablier.
            for (lx in floatArrayOf(14f, 50f, 86f)) {
                ligne(lx, tabl(lx) - 2f, lx, tabl(lx) - 6.4f, acier, 0.22f)
                feuille(lx - 0.9f, tabl(lx) - 7f, 1.8f, 0.7f, acier, 0.3f)
                if (P.allume) halo(lx, tabl(lx) - 6.6f, 3.4f, P.fenetre, 0.5f)
            }
            plan(2)
            solNaturel(SolNaturel.HERBE)  // le parc du fond de la vallée
            poser(DEVANT)
            // ---- L'éperon de rocher qui tient le pied de gauche, son mur
            // de soutènement et son lierre.
            val roche = neutre(duJour(0xFFB7A684.toInt()))
            mineral {
                val eperon = chemin("M-4 62L-4 40C4 38 12 39 18 42C24 45 27 50 28 56L30 62Z")
                piece(eperon, roche)
                strates(eperon, 38f, 62f, roche, 299, 3f, 0.08f)
                appareil(chemin("M-4 42L-4 36C4 34 12 35 18 38L17 43C11 40 4 39 -4 42Z"),
                    sombre(roche, 0.16f), 1.2f, 2.4f, 300, pente = 0.06f, lichen = 0.3f, ombre = 0.5f)
            }
            // Le lierre qui retombe du haut de l'éperon.
            val lierre = sombre(vert(0.5f), 0.14f)
            for (k in 0 until 7) {
                val lx = -4f + r() * 22f
                ligne(lx, 35f, lx + (r() - 0.5f) * 3f, 35f + 4f + r() * 7f, lierre, 0.3f, 0.8f)
                touffe(if (r() < 0.4f) vert(0.62f) else lierre, lx, 35f + r() * 6f, 1.2f + r() * 1.4f,
                    lx + 1.6f, 37f + r() * 6f, 1f + r() * 1.2f)
            }
            // Le fond de la vallée, et un lampadaire du parc.
            // Le fond de la vallée tient **tout en bas** : remonté, il
            // coupait les piles à mi-hauteur et le viaduc redevenait un
            // aqueduc vu de plain-pied, alors qu'on le regarde d'en bas.
            pieceD("M-3 60L-3 57C20 55.6 50 55.6 70 56.6C84 57.4 94 57.8 103 57.4L103 60Z", vert(0.72f), 0.4f)
            ligne(36f, 58f, 36f, 48f, acier, 0.3f)
            feuille(34.8f, 47f, 2.4f, 0.9f, acier, 0.3f)
            if (P.allume) halo(36f, 47.6f, 4f, P.fenetre, 0.5f)
            touffe(vert(0.42f), 96f, 50f, 6f, 103f, 54f, 7f, 90f, 56f, 5f)
            c.restore()
        }

        // ---- Wollefsschlucht, la gorge du Loup (photo du propriétaire)

        /**
         * Ce que disent les photos, et que l'ancienne version n'avait pas :
         *
         * 1. **C'est une faille, et son sujet est l'étroitesse.** Deux parois
         *    de grès montent droit, presque à se toucher, et entre elles il
         *    ne reste qu'une fente noire. L'ancienne version était un
         *    caillebotis orange en pyramide entre deux murs ocre : symétrique,
         *    large, et ouverte.
         * 2. **Le grès est pâle et lité.** Gris-crème, des bancs horizontaux
         *    d'épaisseurs inégales, des diaclases verticales par-dessus, et de
         *    longues coulées vertes d'algue là où l'eau descend.
         * 3. **Un escalier de pierre descend sur la gauche**, taillé dans la
         *    pente, ses marches moussues et inégales. Ce sont les deux
         *    marcheurs de la photo qui donnent l'échelle ; ici c'est
         *    l'escalier qui s'en charge.
         * 4. **Il n'y a pas de ciel**, seulement un peu de jour tout en haut
         *    de la fente. C'est ce qui fait qu'on est dedans et non devant.
         * 5. **Le sol est un tapis de feuilles mortes** et de mousse, entre
         *    des blocs tombés.
         *
         * Sur la carte ouverte, la grande paroi tient la colonne de droite
         * sur toute sa hauteur, l'escalier celle de gauche, et la fente noire
         * passe entre les deux dans la bande du haut.
         */
        fun wollefsschlucht() {
            val r = Alea(305)
            // Un grès **froid**. Celui du Mullerthal est ocre au soleil,
            // mais au fond de la faille il ne reçoit que le jour du haut :
            // tiré au chaud, il donnait une gorge en pain d'épice.
            val gres = neutre(duJour(0xFFB4B4B0.toInt()))
            // L'algue tire la pierre vers le vert sans la remplacer : à
            // demi-teinte et posée à trois dixièmes, elle faisait des vitres
            // vertes plaquées sur la paroi.
            val algue = mix(gres, duJour(0xFF40561F.toInt()), 0.6f)
            val noir = neutre(duJour(0xFF221E19.toInt()))
            // ---- La fente, et le peu de jour qui y tombe.
            mineral {
                feuille(-3f, -3f, 106f, 72f, noir, 0f)
                pieceD("M40 -4L58 -4L55 11C51 8 46 7 42 8Z",
                    mix(P.cielBas, 0xFFFFFFFF.toInt(), if (P.nuit) 0.12f else 0.5f), 0f)
            }
            plan(1)
            mineral {
                // La paroi du fond, dans la fente : à peine tirée du noir, et
                // litée elle aussi. Sans elle, la fente est un trou découpé.
                val fond = chemin("M36 -4L62 -4L60 60L34 60Z")
                // La paroi du fond doit être **noire**, pas grise : c'est
                // l'écart entre elle et la paroi du jour qui fait l'étroitesse.
                // À deux tiers de noir elle sortait du même gris que le
                // reste, et la faille se refermait en un mur continu.
                val tonFond = mix(noir, gres, 0.16f)
                piece(fond, tonFond)
                strates(fond, -3f, 60f, tonFond, 306, 2.6f, 0.04f)
                // Puis on la rend à la nuit. [strates] éclaircit un banc sur
                // deux en le mélangeant au blanc, ce qui ramène une paroi
                // noire au gris moyen : le voile par-dessus lui laisse son
                // litage et lui reprend sa lumière.
                forme(fond, noir, 0.62f)
                // La paroi de gauche, en retrait : elle ferme la fente de
                // l'autre côté et porte la pente où l'escalier est taillé.
                val gauche = chemin("M-4 -4L34 -4L40 26C34 32 24 38 12 42L-4 46Z")
                val tonGauche = mix(gres, noir, 0.56f)
                piece(gauche, tonGauche)
                strates(gauche, -3f, 46f, tonGauche, 307, 3f, 0.08f)
                forme(gauche, noir, 0.3f)
                // Les coulées vertes : elles descendent, elles ne tachent pas
                // au hasard. C'est l'eau qui les pose.
                for (k in 0 until 5) {
                    val cx = -2f + r() * 36f
                    forme("M$cx -4C${cx + 1.5f} 8 ${cx - 1.5f} 16 ${cx + 1f} 26L${cx + 3.4f} 26C${cx + 1f} 16 ${cx + 4f} 8 ${cx + 2.5f} -4Z",
                        algue, 0.16f + r() * 0.14f)
                }
            }
            plan(2)
            solNaturel(SolNaturel.TERRE)  // le tapis de feuilles du fond de gorge
            poser(DEVANT)
            mineral {
                // ---- La grande paroi de droite, la plus proche. Elle est
                // faite de trois découpes étagées, et non d'un seul pan : un
                // tenant unique, même bien lité, se lit comme du contreplaqué,
                // parce que les lits y courent sans jamais rencontrer d'arête.
                val face = chemin("M60 -4L106 -4L106 60L58 60C56 50 59 40 57 30C55.6 22 58 10 60 -4Z")
                piece(face, mix(gres, 0xFFFFFFFF.toInt(), 0.1f))
                strates(face, -3f, 60f, mix(gres, 0xFFFFFFFF.toInt(), 0.1f), 308, 3.2f, 0.05f)
                // Le tableau du bord gauche, à l'ombre : c'est l'épaisseur de
                // la paroi, et c'est elle qui dit qu'on voit dans la fente.
                forme("M60 -4L63 -4C61 10 58.6 22 60 30C62 40 59 50 61 60L58 60C56 50 59 40 57 30C55.6 22 58 10 60 -4Z",
                    sombre(gres, 0.72f), 0.75f)
                // Les diaclases : des cassures verticales par-dessus le
                // litage, légèrement de travers, qui débitent la paroi.
                for (fx in floatArrayOf(68f, 79f, 88f, 97f)) {
                    val pen = (r() - 0.5f) * 5f
                    ligne(fx, -4f, fx + pen, 60f, sombre(gres, 0.5f), 0.3f + r() * 0.3f, 0.55f)
                    ligne(fx + 0.5f, -4f, fx + pen + 0.5f, 60f, mix(gres, 0xFFFFFFFF.toInt(), 0.2f), 0.2f, 0.4f)
                }
                // L'éperon qui avance au pied, à droite : c'est la rupture de
                // découpe qui creuse, pas une ligne de plus.
                val eperon = chemin("M78 60L80 34C84 30 92 29 99 32L106 36L106 60Z")
                val ton = mix(gres, 0xFFFFFFFF.toInt(), 0.1f)
                piece(eperon, ton)
                strates(eperon, 29f, 60f, ton, 309, 3.6f, 0.07f)
                // L'ombre que l'éperon porte sur la paroi derrière lui.
                forme("M78 60L80 34C83.4 30.6 88 29.4 93 29.6C88 31 83.6 33.4 81.6 36.6C79 41 78.6 51 79.6 60Z",
                    sombre(gres, 0.5f), 0.4f)
                // La vire du haut, qui avance au-dessus de la fente.
                val vire = chemin("M58 -4L106 -4L106 9L74 11C66 11 60 8 57.6 4Z")
                val tonVire = mix(gres, 0xFFFFFFFF.toInt(), 0.16f)
                piece(vire, tonVire)
                strates(vire, -4f, 11f, tonVire, 310, 2.8f, 0.04f)
                forme("M57.6 4C60 8 66 11 74 11L106 9L106 12.6L74 14.6C65 14.6 59.4 11 57 6.6Z",
                    sombre(gres, 0.55f), 0.6f)
            }
            // Les coulées vertes de la grande paroi, et la mousse de son pied.
            for (k in 0 until 6) {
                val cx = 60f + r() * 44f
                forme("M$cx -4C${cx + 2f} 12 ${cx - 2f} 26 ${cx + 1f} 40L${cx + 4f} 40C${cx + 1f} 26 ${cx + 5f} 12 ${cx + 3f} -4Z",
                    algue, 0.14f + r() * 0.14f)
            }
            // ---- L'escalier de pierre, taillé dans la pente de gauche. Ses
            // marches sont inégales et moussues, et il tourne en descendant.
            val marche = neutre(duJour(0xFF97968E.toInt()))
            val mousse = mix(duJour(0xFF5E7A2E.toInt()), gres, 0.3f)
            mineral {
                // La pente de terre où il est creusé, d'abord.
                pieceD("M-4 60L-4 44C8 41 20 36 30 29C34 26 38 24 41 25L44 60Z", sombre(marche, 0.42f), 0.5f)
                for (k in 0 until 13) {
                    val t = k / 12f
                    val mx = 36f - t * 36f
                    val my = 27f + t * 29f + t * t * 3f
                    val l = 9f + t * 7f
                    val h = 1.5f + t * 1.1f
                    pieceD("M$mx ${my} L${mx - l} ${my + h * 0.5f}L${mx - l} ${my + h * 1.6f}L$mx ${my + h}Z",
                        if (r() < 0.4f) mix(marche, 0xFFFFFFFF.toInt(), 0.1f) else sombre(marche, 0.1f + r() * 0.2f), 0.4f)
                    // Le nez de la marche, à l'ombre, et sa mousse.
                    forme("M$mx ${my + h}L${mx - l} ${my + h * 1.6f}L${mx - l} ${my + h * 2.2f}L$mx ${my + h * 1.7f}Z",
                        sombre(marche, 0.52f), 0.6f)
                    if (r() < 0.6f) ovalePapier(mx - l * (0.2f + r() * 0.6f), my + h * 0.7f,
                        1f + r() * 1.6f, 0.3f + r() * 0.3f, mousse, 0f)
                }
                // La main courante, un simple fer planté dans la roche.
                val fer = neutre(duJour(0xFF6E6A62.toInt()))
                ligne(37f, 24f, 2f, 52f, fer, 0.3f, 0.9f)
                for (px in floatArrayOf(33f, 24f, 15f, 6f)) ligne(px, 26.4f + (37f - px) * 0.8f, px, 30f + (37f - px) * 0.8f, fer, 0.26f, 0.9f)
            }
            // ---- Le tapis de feuilles, et les blocs tombés.
            val feuilles = mix(neutre(duJour(0xFF8A6A44.toInt())), gres, 0.2f)
            pieceD("M-4 60L-4 50C10 48 24 50 36 53C46 55.4 54 56 60 55.6L60 60Z", feuilles, 0.35f)
            for (k in 0 until 16) {
                val fx = -4f + r() * 66f
                val fy = 50f + r() * 9f
                ovalePapier(fx, fy, 0.8f + r() * 0.9f, 0.3f + r() * 0.3f,
                    if (r() < 0.5f) mix(feuilles, duJour(0xFFC08A4A.toInt()), 0.4f) else sombre(feuilles, 0.3f), 0f)
            }
            for (k in 0 until 4) {
                val bx = -2f + r() * 56f
                val by = 52f + r() * 6f
                val bw = 4f + r() * 7f
                mineral {
                    pieceD("M$bx ${by + 3f}L${bx + bw * 0.14f} ${by + 0.6f}L${bx + bw * 0.56f} $by" +
                        "L${bx + bw * 0.9f} ${by + 0.8f}L${bx + bw} ${by + 3f}Z", sombre(gres, 0.3f), 0.5f)
                }
                ovalePapier(bx + bw * 0.5f, by + 0.6f, bw * 0.3f, 0.4f, mousse, 0f)
            }
            c.restore()
        }

        // ---- Priedegtstull, l'aiguille et sa passerelle (photo du propriétaire)

        /**
         * Ce que dit la photo du propriétaire (2026-11-21) :
         *
         * 1. **Une aiguille de grès détachée de la falaise**, à gauche,
         *    coiffée d'une plateforme avec son garde-corps et son fanion.
         *    C'est elle le lieu, et la carte n'en montrait rien.
         * 2. **Une passerelle d'acier la relie à la falaise**, en travers du
         *    vide. On la regarde d'en bas, en forte contre-plongée : elle
         *    barre le haut de la fenêtre, au-dessus du dessin du mot.
         * 3. **La grande falaise de grès tient tout le côté droit**, litée en
         *    bancs d'épaisseurs inégales, avec ses ressauts, ses diaclases et
         *    la verdure qui pousse dans les fissures.
         * 4. **Un escalier taillé monte au pied de la falaise**, avec sa main
         *    courante de fer.
         * 5. **Un bouleau clair monte entre les deux**, sa cime au-dessus de
         *    la passerelle : c'est lui qui remplit la trouée.
         * 6. **Le ciel est franc, plein de gros cumulus**, et c'est la seule
         *    carte où l'on regarde vers le haut.
         *
         * Sur la carte ouverte : la passerelle, la plateforme et les cimes
         * dans la bande du haut, l'aiguille dans la colonne de gauche, la
         * falaise et l'escalier dans celle de droite.
         */
        fun priedegtstull() {
            val r = Alea(315)
            val gres = neutre(duJour(0xFFC0AA8A.toInt()))
            val acier = neutre(duJour(0xFF4A4740.toInt()))
            val bois = nappe(duJour(0xFF3E6B2A.toInt()), 0.5f, VERT_CALME)
            // ---- Le fond : la futaie du plateau, loin derrière, et la cime
            // du bouleau qui monte dans la trouée.
            // La futaie du fond est **basse** : c'est le ciel qui doit
            // remplir la trouée entre l'aiguille et la falaise, comme sur la
            // photo. Portée en haut de la fenêtre, elle noyait l'aiguille
            // dans un panneau vert et le lieu n'était plus qu'un amas de
            // grès beige d'un bord à l'autre.
            lisiere(-3f, 103f, { x -> 33f - 0.06f * x + 2.6f * sin(x * 0.11f) }, 2f, 3.2f, 316,
                mix(bois, P.loin, 0.42f))
            plan(1)
            // Des fûts dans la trouée : sans eux, un panneau vert plat entre
            // l'aiguille et la falaise.
            for (k in 0 until 7) {
                val fx = 24f + r() * 30f
                ligne(fx, 33f + r() * 3f, fx + (r() - 0.5f) * 2.4f, 46f + r() * 10f,
                    if (r() < 0.5f) sombre(bois, 0.42f) else mix(duJour(0xFF8A8268.toInt()), P.loin, 0.3f),
                    0.4f + r() * 0.6f, 0.55f)
            }
            // Le bouleau : un fût clair qui monte du fond du ravin, et sa
            // cime au-dessus de la passerelle.
            val bouleau = neutre(duJour(0xFFD8D2C4.toInt()))
            pieceD("M36.4 58L37.6 9L39.6 9L40.4 58Z", bouleau, 0.5f)
            rect(38.6f, 9f, 0.7f, 49f, sombre(bouleau, 0.3f), 0.8f)
            for (k in 0 until 7) ovalePapier(37f + r() * 3f, 14f + k * 6f + r() * 4f,
                0.5f + r() * 0.6f, 0.2f + r() * 0.2f, sombre(bouleau, 0.4f), 0f)
            trait("M38.4 16L30 11M38.4 20L46 14M38.4 12L33 6M38.4 13L45 7", sombre(bouleau, 0.2f), 0.4f)
            // Six touffes et non neuf, serrées sur le fût : la cime
            // remplissait toute la trouée, et c'est le ciel qui doit y être.
            for (k in 0 until 6) {
                val bx = 30f + r() * 18f
                val by = 0.5f + r() * 8f
                val e = 2f + r() * 2.2f
                touffe(if (r() < 0.4f) mix(bois, duJour(0xFFD8E86C.toInt()), 0.3f) else bois,
                    bx, by, e, bx + e * 1.2f, by + e * 0.5f, e * 0.75f, bx - e * 1.1f, by + e * 0.4f, e * 0.7f)
            }
            // ---- Le grès du Mullerthal, des deux côtés du vide. Le litage
            // seul ne sauve pas une masse unique : une paroi d'un bord à
            // l'autre, même bien litée, se lit comme un contreplaqué, les
            // lits courant sans jamais rencontrer d'arête. Ce sont les
            // **ruptures** qui creusent. Chaque paroi est donc **étagée en
            // découpes** — la paroi du fond, l'éperon qui avance, le pied —
            // chacune avec sa valeur, sa tranche de kraft et son litage à
            // elle, plus ses diaclases verticales par-dessus.
            /** Une découpe de grès : sa masse, son litage, ses diaclases. */
            fun bancDeGres(d: String, col: Int, graine: Int, pas: Float, fentes: Int) {
                val m = chemin(d)
                val b = RectF()
                m.computeBounds(b, true)
                mineral {
                    piece(m, col)
                    strates(m, b.top, b.bottom, col, graine, pas = pas, pente = 0.03f)
                    c.save(); c.clipPath(m)
                    releve?.let { it.save(); it.clipPath(m) }
                    val rr = Alea(graine + 50)
                    for (k in 0 until fentes) {
                        val fx = b.left + rr() * b.width()
                        val fy = b.top + rr() * b.height() * 0.6f
                        val fh = b.height() * (0.2f + rr() * 0.5f)
                        rect(fx, fy, 0.45f + rr() * 0.5f, fh, sombre(col, 0.55f), 0.8f)
                        rect(fx + 0.75f, fy, 0.35f, fh * 0.8f, mix(col, 0xFFFFFFFF.toInt(), 0.24f), 0.6f)
                    }
                    releve?.restore(); c.restore()
                }
            }
            // La falaise de droite, en quatre découpes étagées.
            bancDeGres("M52 60L56 30C58 22 62 14 68 8C74 2 84 -2 104 -4L104 60Z",
                sombre(gres, 0.26f), 317, 3.2f, 5)
            bancDeGres("M68 60L71 34C72 26 76 18 82 12C88 7 94 4 104 2L104 60Z", gres, 320, 2.7f, 4)
            bancDeGres("M66 9C72 3 82 -1 104 -3L104 3C86 5 77 9 71 15Z",
                mix(gres, 0xFFFFFFFF.toInt(), 0.14f), 321, 1.8f, 2)
            bancDeGres("M56 60L61 43C66 39 74 37 82 39C90 41 97 45 104 47L104 60Z",
                sombre(gres, 0.42f), 322, 3.6f, 3)
            // La verdure dans les fissures de la falaise.
            for (k in 0 until 12) {
                val vx = 56f + r() * 48f
                val vy = 2f + r() * 50f
                if (vy < (30f - (vx - 56f) * 0.9f)) continue
                val e = 1.2f + r() * 1.8f
                touffe(if (r() < 0.35f) mix(bois, duJour(0xFFD8E86C.toInt()), 0.26f) else sombre(bois, 0.2f),
                    vx, vy, e, vx + e, vy + e * 0.5f, e * 0.7f)
            }
            // ---- La passerelle d'acier, de l'aiguille à la falaise. Vue
            // d'en bas : on voit le dessous du tablier et ses entretoises,
            // et le garde-corps se découpe sur le ciel.
            fun pont(x: Float) = 11.6f - (x - 24f) * 0.062f
            mineral {
                piece(Path().apply {
                    moveTo(23f, pont(23f)); lineTo(61f, pont(61f))
                    lineTo(61f, pont(61f) + 1.5f); lineTo(23f, pont(23f) + 1.5f); close()
                }, acier, 0.5f)
                forme(Path().apply {
                    moveTo(23f, pont(23f) + 1.5f); lineTo(61f, pont(61f) + 1.5f)
                    lineTo(61f, pont(61f) + 2.3f); lineTo(23f, pont(23f) + 2.3f); close()
                }, sombre(acier, 0.4f), 0.95f)
                // Les entretoises sous le tablier, en zigzag.
                var zx = 24f
                var haut = true
                while (zx < 60f) {
                    ligne(zx, pont(zx) + 2.2f, zx + 4f, pont(zx + 4f) + (if (haut) 2.4f else 4.2f),
                        sombre(acier, 0.2f), 0.3f, 0.9f)
                    haut = !haut
                    zx += 4f
                }
                // Le garde-corps : montants serrés et deux lisses, qui se
                // découpent sur le ciel.
                var gx = 23.4f
                while (gx < 61f) {
                    ligne(gx, pont(gx), gx, pont(gx) - 2.6f, acier, 0.26f, 0.9f)
                    gx += 1.7f
                }
                ligne(23f, pont(23f) - 2.6f, 61f, pont(61f) - 2.6f, acier, 0.4f)
                ligne(23f, pont(23f) - 1.3f, 61f, pont(61f) - 1.3f, acier, 0.26f, 0.8f)
            }
            plan(2)
            solNaturel(SolNaturel.MOUSSE, DEVANT)  // le fond du ravin, moussu
            poser(DEVANT)
            // ---- L'aiguille de grès, détachée de la falaise : c'est elle le
            // lieu. Elle s'évase vers le haut, comme sur la photo, et sa
            // plateforme la coiffe.
            // Détachée du bord : collée à lui, elle se lisait comme une
            // seconde falaise et le lieu n'avait plus de sujet. Et elle est
            // **plus claire** que la paroi d'en face, qui est à contre-jour :
            // sans cet écart de valeur, deux masses de grès identiques.
            //
            // Elle est étagée en trois découpes comme la falaise : son corps,
            // le bloc qui avance à gauche en plein jour, et le flanc droit
            // dans son ombre. D'une seule pièce, elle n'était qu'une planche
            // de grès rayée.
            val clair = mix(gres, 0xFFFFFFFF.toInt(), 0.12f)
            bancDeGres("M1.6 60C0.4 50 0.6 40 2.2 32C3.4 25 6 17.6 13 15" +
                "C19.6 12.8 25.4 14 27.4 17.4C29 21.6 27 27 28 33C28.8 39 26.4 45 24.4 50" +
                "C23 53.6 22.2 57 22 60Z", clair, 318, 2.9f, 4)
            bancDeGres("M1.6 60C0.4 50 0.6 40 2.2 32C3.4 25 6 17.6 13 15L14.6 18.6" +
                "C9.4 22.6 6.6 28.4 6.2 34.6C5.6 43 6 51.6 7 60Z",
                mix(gres, 0xFFFFFFFF.toInt(), 0.24f), 323, 2.4f, 3)
            bancDeGres("M18.6 16C23.4 14.4 26 15.2 27.4 17.4C29 21.6 27 27 28 33" +
                "C28.8 39 26.4 45 24.4 50C23 53.6 22.2 57 22 60L16.6 60" +
                "C17.8 52 19.4 44 18.8 36C18.2 28 18 21.4 18.6 16Z",
                sombre(gres, 0.4f), 324, 3.1f, 3)
            // La mousse et les buissons accrochés dans ses fissures.
            for (k in 0 until 10) {
                val vx = -3f + r() * 30f
                val vy = 16f + r() * 40f
                val e = 1.1f + r() * 1.8f
                touffe(if (r() < 0.35f) mix(bois, duJour(0xFFD8E86C.toInt()), 0.26f) else sombre(bois, 0.22f),
                    vx, vy, e, vx + e, vy + e * 0.5f, e * 0.7f)
            }
            // La plateforme du sommet, son garde-corps et son fanion.
            mineral {
                piece(Path().apply {
                    moveTo(4f, 15.4f); lineTo(21f, 14.6f); lineTo(21f, 16.2f); lineTo(4f, 17.2f); close()
                }, sombre(acier, 0.2f), 0.4f)
                var px = 5f
                while (px < 21f) {
                    ligne(px, 15.2f, px, 12.2f, acier, 0.24f, 0.9f)
                    px += 2.2f
                }
                ligne(4f, 12.4f, 21f, 11.6f, acier, 0.36f)
                ligne(4f, 13.8f, 21f, 13f, acier, 0.24f, 0.8f)
            }
            ligne(9f, 15f, 9f, 3.6f, acier, 0.32f)
            pieceD("M9 3.8L16.6 6.2L9 8.8Z", duJour(0xFFED2939.toInt()), 0.3f)
            // ---- L'escalier au pied de la falaise, avec sa main courante.
            mineral {
                for (k in 0 until 13) {
                    val ex = 58f + k * 2.4f
                    val ey = 58f - k * 2.5f
                    feuille(ex, ey, 3.4f, 0.9f, mix(gres, 0xFFFFFFFF.toInt(), 0.12f), 0.3f)
                    forme(Path().apply {
                        moveTo(ex, ey + 0.9f); lineTo(ex + 3.4f, ey + 0.9f)
                        lineTo(ex + 3.4f, ey + 2.5f); lineTo(ex, ey + 2.5f); close()
                    }, sombre(gres, 0.45f), 0.85f)
                }
                var hx = 58f
                while (hx < 88f) {
                    ligne(hx, 58f - (hx - 58f) * 1.04f, hx, 55f - (hx - 58f) * 1.04f, acier, 0.26f, 0.9f)
                    hx += 3.4f
                }
                ligne(58f, 55f, 89f, 22.8f, acier, 0.34f)
            }
            // ---- Les buissons du fond du ravin, qui ferment le bas.
            lisiere(-3f, 103f, { x -> 52f + 2.4f * sin(x * 0.29f) }, 2f, 3.2f, 319, sombre(bois, 0.3f))
            c.restore()
        }

        // ---- Dumongshaff, la passerelle sur l'étang (photo du propriétaire)

        /**
         * Ce que dit la photo, et que l'ancienne version n'avait pas :
         *
         * 1. **Le bois est gris**, lavé par la pluie, et non d'un ocre de
         *    bois verni. C'est le premier défaut qu'on voyait.
         * 2. **On la regarde depuis la rive droite** : son extrémité proche
         *    est grande, avec ses deux gros poteaux et son tablier de
         *    planches posé au sol, et elle fuit vers la gauche où elle touche
         *    l'autre berge, beaucoup plus petite. De face, elle n'était qu'un
         *    bandeau en travers de la fenêtre.
         * 3. **Le garde-corps est un peigne de barreaux serrés** entre une
         *    lisse haute courbe et une lisse basse. Ce sont eux qui font
         *    reconnaître cette passerelle-là.
         * 4. **Le tablier est une arche très surbaissée**, à peine bombée.
         * 5. **L'eau est sombre et verte**, et la passerelle s'y reflète.
         * 6. **Les roseaux montent haut devant**, et mangent le pied de
         *    l'ouvrage.
         *
         * Sur la carte ouverte, le bout proche et ses poteaux tiennent la
         * colonne de droite, l'autre bout et la berge celle de gauche.
         */
        fun dumongshaff() {
            val r = Alea(327)
            // Le pré d'en face et sa digue, sous un ciel bas.
            lisiere(-2f, 102f, { x -> 29f + 1.6f * sin(x * 0.1f) }, 1.4f, 2.2f, 326,
                mix(nappe(duJour(0xFF4E7034.toInt()), 0.36f, VERT_CALME), P.loin, 0.42f))
            // Le jeune arbre nu, au bord gauche.
            val ecorce = neutre(duJour(0xFF6A6358.toInt()))
            trait("M6 40L7 12M7 22L2 14M7 18L12 8M7 28L13 22", ecorce, 0.6f)
            plan(1)
            val pre = vert(0.78f)
            pieceD("M-3 66L-3 38C30 36 70 36 103 38L103 66Z", pre, 0.4f)
            // La digue : une arête d'herbe plus sombre, qui dit que le pré
            // est plus haut que l'eau.
            forme("M-3 42C30 39.6 70 39.6 103 41.6L103 44C70 42 30 42 -3 44.4Z", sombre(pre, 0.2f), 0.4f)
            plan(2)
            solNaturel(SolNaturel.BERGE)  // la rive de l'étang
            poser(DEVANT)
            // ---- L'eau. Sombre, verte, et c'est elle qui porte le reflet.
            // Une eau d'étang : verte, **sombre** et calme. Laissée à sa
            // saturation, le ravivage la sortait en émeraude, et la nappe
            // sous la passerelle se lisait comme une pelouse.
            fun calmer(col: Int) = mix(col, neutre(col), 0.5f)
            val eau = calmer(mix(P.eau, duJour(0xFF27452F.toInt()), if (P.allume) 0.55f else 0.84f))
            pieceD("M-3 60L-3 41.4C14 40.4 34 42 52 44.6C68 47 84 49.6 103 50L103 60Z", eau, 0.4f)
            // ---- La passerelle, dessinée de face puis posée de trois quarts :
            // son bout proche à droite est grand, l'autre fuit vers la berge
            // de gauche, et ses barreaux se serrent en s'éloignant.
            // Un bois **gris**, lavé par la pluie. Tiré au chaud, la
            // passerelle sortait en pin verni au bord d'un étang.
            val bois = neutre(duJour(0xFF9C9B96.toInt()))
            val ombreBois = sombre(bois, 0.4f)
            /** Le dessus du tablier en [x], dans le dessin à plat : une arche très surbaissée. */
            fun tablierPlat(x: Float) = 17f + 3.4f * ((x - 50f) / 50f).pow(2)
            fun passerelle(reflet: Boolean) {
                val ton = if (reflet) mix(eau, bois, 0.4f) else bois
                val creux = if (reflet) mix(eau, ombreBois, 0.45f) else ombreBois
                // Le tablier : sa face de chant, puis la poutre de rive
                // dessous, plus sombre.
                val dessus = Path()
                dessus.moveTo(2f, tablierPlat(2f))
                var x = 2f
                while (x < 98f) { x = min(98f, x + 4f); dessus.lineTo(x, tablierPlat(x)) }
                while (x > 2f) { dessus.lineTo(x, tablierPlat(x) + 1.6f); x = max(2f, x - 4f) }
                dessus.lineTo(2f, tablierPlat(2f) + 1.6f); dessus.close()
                piece(dessus, ton, 0.5f)
                val poutre = Path()
                poutre.moveTo(2f, tablierPlat(2f) + 1.6f)
                x = 2f
                while (x < 98f) { x = min(98f, x + 4f); poutre.lineTo(x, tablierPlat(x) + 1.6f) }
                while (x > 2f) { poutre.lineTo(x, tablierPlat(x) + 4f); x = max(2f, x - 4f) }
                poutre.lineTo(2f, tablierPlat(2f) + 4f); poutre.close()
                piece(poutre, creux, 0.4f)
                // Le peigne des barreaux, entre la lisse basse et la lisse
                // haute. Ils sont serrés : c'est ce qui fait la passerelle.
                x = 4f
                while (x < 97f) {
                    ligne(x, tablierPlat(x) - 8.6f, x, tablierPlat(x) - 0.2f, ton, 0.62f, 0.95f)
                    ligne(x + 0.34f, tablierPlat(x) - 8.4f, x + 0.34f, tablierPlat(x) - 0.4f, creux, 0.2f, 0.6f)
                    x += 1.7f
                }
                // Les deux lisses, par-dessus les barreaux.
                for (h in floatArrayOf(9.4f, 3.2f)) {
                    val lisse = Path()
                    lisse.moveTo(2f, tablierPlat(2f) - h)
                    x = 2f
                    while (x < 98f) { x = min(98f, x + 4f); lisse.lineTo(x, tablierPlat(x) - h) }
                    while (x > 2f) { lisse.lineTo(x, tablierPlat(x) - h + 1.3f); x = max(2f, x - 4f) }
                    lisse.lineTo(2f, tablierPlat(2f) - h + 1.3f); lisse.close()
                    piece(lisse, if (h > 5f) mix(ton, 0xFFFFFFFF.toInt(), 0.1f) else ton, 0.4f)
                }
                // Les quatre montants : deux à chaque bout, plus gros que les
                // barreaux, et qui descendent sous le tablier.
                for (px in floatArrayOf(2.4f, 8f, 90f, 96f)) {
                    feuille(px, tablierPlat(px) - 11f, 2.2f, 19f, ton, 0.5f)
                    feuille(px + 1.5f, tablierPlat(px) - 11f, 0.7f, 19f, creux, 0.4f)
                }
            }
            surQuad(100f, 30f, PASSERELLE_DUMONGSHAFF) { passerelle(false) }
            // Le reflet, retourné et noyé dans l'eau : une passerelle posée
            // sur une eau qui ne la rend pas flotte au-dessus d'un carton vert.
            c.save()
            // Le miroir est à la ligne d'eau, sous le tablier, et non au
            // bas de la fenêtre : plus bas, le reflet tombait tout entier
            // sous le bandeau du nom et ne se voyait jamais.
            c.scale(1f, -0.5f, 0f, 47f)
            surQuad(100f, 30f, PASSERELLE_DUMONGSHAFF) { passerelle(true) }
            c.restore()
            forme("M-3 60L-3 41.4C14 40.4 34 42 52 44.6C68 47 84 49.6 103 50L103 60Z", eau, 0.5f)
            // Les traînées de l'eau, couchées, par-dessus tout.
            val brillance = mix(eau, 0xFFFFFFFF.toInt(), if (P.nuit) 0.14f else 0.34f)
            for (k in 0 until 9) {
                val wx = -2f + r() * 74f
                rect(wx, 44f + r() * 13f, 3f + r() * 8f, 0.3f, brillance, 0.65f)
            }
            // ---- Le tablier de planches posé au sol, au bout proche.
            mineral {
                pieceD("M84 58.5L88 50.6C92 49.6 97 49.4 103 50.2L103 58.5Z", neutre(duJour(0xFF8E8A80.toInt())), 0.4f)
                for (k in 0 until 5) ligne(88f + k * 3.4f, 50.4f + k * 0.2f, 86.6f + k * 3.6f, 58.5f,
                    sombre(neutre(duJour(0xFF8E8A80.toInt())), 0.35f), 0.22f, 0.7f)
            }
            // ---- Les roseaux, qui montent haut devant et mangent le pied de
            // l'ouvrage. Ils sont plus hauts et plus drus à droite, où l'on
            // se tient.
            val roseau = vert(0.6f)
            for (k in 0 until 70) {
                val rx = -3f + r() * 106f
                if (rx in 24f..62f && r() < 0.68f) continue
                val h = 4f + r() * 8f + (if (rx > 70f) 4f else 0f)
                ligne(rx, DEVANT, rx + (r() - 0.5f) * 3f, DEVANT - h,
                    if (r() < 0.4f) mix(roseau, duJour(0xFFD8E884.toInt()), 0.3f) else sombre(roseau, 0.2f),
                    0.26f + r() * 0.16f, 0.9f)
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
            // Le Breedewee, d'après la photo du propriétaire
            // (`photos-lieux/13-breedewee-porte.webp`, reçue le 2026-11-16).
            //
            // Elle corrige trois choses qu'aucune maquette n'avait :
            //
            // 1. **Ce n'est pas un escalier.** C'est une ruelle **pavée**,
            //    qui **tourne** et disparaît dans le virage. L'escalier
            //    inventé ici ne menait nulle part parce qu'il n'existe pas.
            // 2. **La porte est un arc à bossages** : de gros claveaux
            //    saillants, chanfreinés, et des piédroits faits des mêmes
            //    blocs, verdis de mousse. Pas un appareil régulier.
            // 3. **Les murs sont hauts, sombres et presque aveugles** : un
            //    enduit gris-vert, une petite fenêtre cintrée, une porte de
            //    bois. Pas les façades colorées du Grund.
            //
            // Et une lanterne de fer forgé pend sous l'arc.
            val r = Alea(355)
            val taille = neutre(duJour(0xFFCBC5B3.toInt()))      // la pierre de la porte
            val enduit = neutre(duJour(0xFF6E7468.toInt()))      // l'enduit sombre des murs
            val enduitClair = neutre(duJour(0xFFCFCABC.toInt()))
            val bois = neutre(duJour(0xFF5A4634.toInt()))
            val zinc = neutre(duJour(0xFF9DA2A6.toInt()))
            val fer = sombre(neutre(duJour(0xFF2C2E2C.toInt())), 0.2f)
            val pave = neutre(duJour(0xFF93907F.toInt()))
            val lueur = if (P.allume) P.fenetre else mix(taille, 0xFFFFFFFF.toInt(), 0.45f)
            mineraux++

            // ---- Le fond : la ruelle qui tourne et s'enfonce dans l'ombre.
            pieceD("M18 48L18 2L64 2L64 48Z", sombre(enduit, 0.35f))
            // Le jour qui vient du virage, au bout de la ruelle.
            forme(chemin("M22 44L23.4 34C25 32.6 28.4 32.6 30 34L30 44Z"),
                mix(lueur, enduit, 0.3f), if (P.nuit) 0.4f else 0.42f)
            plan(1)
            // ---- Les murs de la ruelle : hauts, sombres, presque aveugles.
            // Le grand pan d'enduit de droite, dans l'ombre.
            pieceD("M30 50L31 4L66 2L66 50Z", enduit)
            pieceD("M30 50L31 4L40 3.6L39.4 50Z", sombre(enduit, 0.22f), 0.3f)
            // Le bâtiment clair, au-dessus et à gauche.
            // Le mur de gauche de la ruelle, sous le bâtiment clair : un
            // enduit gris, avec son arête vive contre le jour du virage.
            pieceD("M15 46L15.8 20L31 19L31 46Z", mix(enduit, enduitClair, 0.3f))
            ligne(31f, 19f, 31f, 46f, sombre(enduit, 0.4f), 0.35f)
            pieceD("M23 46L23 38.4C23 36.6 28 36.6 28 38.4L28 46Z", bois)
            pieceD("M10 20.4L10 -3L31 -3L31 19.4Z", enduitClair)
            for (fx in floatArrayOf(13f, 24f)) {
                feuille(fx - 0.5f, 3.5f, 4.4f, 6f, mix(enduitClair, 0xFFFFFFFF.toInt(), 0.3f), 0.3f)
                rect(fx, 4f, 3.4f, 5f, if (P.allume && r() < 0.6f) P.fenetre else neutre(duJour(0xFF3E4654.toInt())))
                ligne(fx + 1.7f, 4f, fx + 1.7f, 9f, sombre(enduitClair, 0.3f), 0.18f)
            }
            // La petite fenêtre cintrée et les deux portes de bois du fond.
            feuille(41f, 26f, 5f, 6.6f, mix(enduit, 0xFFFFFFFF.toInt(), 0.22f), 0.3f)
            pieceD("M42 32L42 28.6C42 27 45 27 45 28.6L45 32Z", sombre(enduit, 0.55f))
            pieceD("M50 44L50 35.6C50 33.4 55 33.4 55 35.6L55 44Z", bois)
            for (dx in floatArrayOf(51.4f, 53.4f)) ligne(dx, 35.4f, dx, 44f, sombre(bois, 0.4f), 0.2f)
            pieceD("M32.6 44L32.6 37.4C32.6 35.8 36.4 35.8 36.4 37.4L36.4 44Z", sombre(bois, 0.2f))
            // Le bandeau d'enduit clair au pied des murs, comme sur la photo.
            pieceD("M30 50L66 50L66 44.6L30 45.6Z", mix(enduit, 0xFFFFFFFF.toInt(), 0.4f), 0.3f)
            // La lanterne de fer forgé, pendue sous l'arc.
            if (!vivant) lanterne(44f, 16f, true)
            plan(2)
            // ---- Le pavé : de gros pavés qui tournent avec la ruelle, et
            // grossissent en venant vers nous.
            // **La ruelle tourne sur la gauche** en s'enfonçant (photo) :
            // son axe se déplace vers la droite à mesure qu'elle vient vers
            // nous, et elle s'évase vite — au premier plan le pavé tient
            // toute la largeur, sinon le ciel reparaît dans les angles sous
            // la rainure du devant.
            fun axe(y: Float) = 25f + (y - 44f) * 1.45f
            fun demi(y: Float) = 5f + (y - 44f) * 3.1f
            fun gauche(y: Float) = max(-6f, axe(y) - demi(y))
            fun droite(y: Float) = min(106f, axe(y) + demi(y))
            val rue = Path()
            run {
                rue.moveTo(gauche(44f), 44f)
                var y = 44f
                while (y < 72f) { rue.lineTo(gauche(y), y); y += 2f }
                rue.lineTo(gauche(72f), 72f); rue.lineTo(droite(72f), 72f)
                y = 72f
                while (y > 44f) { rue.lineTo(droite(y), y); y -= 2f }
                rue.close()
            }
            piece(rue, sombre(pave, 0.45f), 0.3f)
            c.save()
            c.clipPath(rue)
            val rp = Alea(366)
            var py = 44.4f
            var ph = 0.5f
            while (py < 72f) {
                var px = gauche(py) - 6f
                val fin = droite(py) + 6f
                while (px < fin) {
                    val pw = ph * 1.5f + rp() * ph * 1.1f
                    val t = rp()
                    // Les rangs se bombent : un pavé est posé sur une
                    // chaussée en dos d'âne, et la courbe du virage se lit
                    // d'abord dans ses joints.
                    val bombe = ((px - axe(py)) / max(6f, demi(py))).let { it * it } * ph * 1.1f
                    rect(px + ph * 0.16f, py + ph * 0.14f - bombe, pw - ph * 0.32f, ph * 1.3f,
                        if (t < 0.34f) sombre(pave, 0.12f + t * 0.4f)
                        else if (t < 0.7f) mix(pave, 0xFFFFFFFF.toInt(), 0.06f + t * 0.2f)
                        else mix(pave, neutre(duJour(0xFF7E7A6C.toInt())), 0.3f + t * 0.3f))
                    px += pw
                }
                py += ph * 1.5f
                ph *= 1.2f
            }
            c.restore()
            poser(DEVANT)
            // ---- La porte : l'arc à bossages et ses deux piédroits.
            // La porte est vue **de trois quarts** (photo) : sa ligne de
            // naissance penche, le piédroit de droite est plus près donc
            // plus bas et plus large, et l'on voit l'épaisseur du passage
            // du côté gauche. Symétrique, elle se lisait de face.
            val xg = 18f; val xd = 64f; val yg = 23f; val yd = 31f; val arcH = 19f
            fun arcPorte(dx: Float, dy: Float, dh: Float, pince: Float): Path {
                val t = Path()
                val g = xg + dx + pince; val d = xd + dx - pince
                t.moveTo(g, 62f)
                t.lineTo(g, yg + dy)
                for (i in 0..30) {
                    val u = i / 30f
                    val m = 2f * u - 1f
                    t.lineTo(g + u * (d - g), yg + dy + u * (yd - yg) - (arcH + dh) * sqrt(1f - m * m))
                }
                t.lineTo(d, 62f)
                t.close()
                return t
            }
            val ouvert = arcPorte(0f, 0f, 0f, 0f)
            // Le mur de la porte, percé de l'arc : à gauche un piédroit
            // étroit, à droite un massif plus large, qui est plus près.
            val massif = chemin("M-3 -3L92 -3L92 62L-3 62Z")
            massif.op(ouvert, Path.Op.DIFFERENCE)
            piece(massif, sombre(taille, 0.42f))
            // Le mur sombre du bâtiment de droite, où court la gouttière.
            pieceD("M92 -3L103 -3L103 62L92 62Z", sombre(enduit, 0.25f))
            // Les blocs en bossage des piédroits, deux rangs décalés.
            val rb = Alea(372)
            var by = 62f
            while (by > -4f) {
                val hb = 2.8f + rb() * 1.4f
                bossage(-4f + rb() * 1.6f, by - hb, 8f + rb() * 5f, hb, taille, rb())
                bossage(62f + rb() * 1.4f, by - hb, 9f + rb() * 5f, hb, taille, rb())
                bossage(75f + rb() * 1.6f, by - hb - rb() * 1.2f, 9f + rb() * 5f, hb, taille, rb())
                by -= hb
            }
            // Le linteau de pierre au-dessus de l'arc : des assises plus
            // longues, qui ferment le mur jusqu'au bord haut.
            var ly = 10f
            while (ly > -4f) {
                val hb = 2.8f + rb() * 1.2f
                var lx = -4f + rb() * 2f
                while (lx < 92f) {
                    val wb = 9f + rb() * 7f
                    bossage(lx, ly - hb, wb, hb, taille, rb())
                    lx += wb
                }
                ly -= hb
            }
            // L'épaisseur du passage, vue du côté gauche : le fond de
            // l'arc est décalé vers le point de fuite, et la bande qui
            // reste entre les deux est l'intrados.
            val soffite = arcPorte(0f, 0f, 0f, 0f)
            soffite.op(arcPorte(-3.4f, 1.6f, -2.4f, 1.2f), Path.Op.DIFFERENCE)
            piece(soffite, sombre(taille, 0.34f), 0f)
            // Les claveaux de l'arc, en bossage eux aussi : c'est eux qu'on
            // voit d'abord sur la photo.
            fun surArc(u: Float, e: Float): FloatArray {
                val g = xg - e; val d = xd + e
                val m = 2f * u - 1f
                return floatArrayOf(g + u * (d - g),
                    yg + u * (yd - yg) - (arcH + e) * sqrt(1f - m * m))
            }
            for (k in 0 until 19) {
                val u0 = k / 19f
                val u1 = (k + 1) / 19f
                val t = rb()
                val a0 = surArc(u0 + (u1 - u0) * 0.09f, 0f)
                val a1 = surArc(u1 - (u1 - u0) * 0.09f, 0f)
                val b1 = surArc(u1 - (u1 - u0) * 0.09f, 7f)
                val b0 = surArc(u0 + (u1 - u0) * 0.09f, 7f)
                val c0 = surArc(u0, 0f); val c1 = surArc(u1, 0f)
                val d1 = surArc(u1, 7f); val d0 = surArc(u0, 7f)
                piece(Path().apply {
                    moveTo(c0[0], c0[1]); lineTo(c1[0], c1[1]); lineTo(d1[0], d1[1]); lineTo(d0[0], d0[1]); close()
                }, sombre(taille, 0.42f))
                val col = tonPierre(taille, t)
                val claveau = Path().apply {
                    moveTo(a0[0], a0[1]); lineTo(a1[0], a1[1]); lineTo(b1[0], b1[1]); lineTo(b0[0], b0[1]); close()
                }
                piece(claveau, mix(col, 0xFFFFFFFF.toInt(), 0.14f))   // la ciselure
                // Le champ rugueux du claveau, découpé dans sa ciselure.
                val bb = RectF()
                claveau.computeBounds(bb, true)
                c.save()
                c.clipPath(claveau)
                champBosse(bb.left + 0.55f, bb.top + 0.55f, bb.width() - 1.1f, bb.height() - 1.1f, col, 600 + k * 17)
                c.restore()
            }
            // La descente de gouttière, sur le massif de droite.
            rect(84f, -3f, 1.6f, 48f, zinc)
            rect(84f, -3f, 0.5f, 48f, mix(zinc, 0xFFFFFFFF.toInt(), 0.35f))
            pieceD("M84 45L85.6 45L89.6 52L88 52.6Z", zinc)
            rect(88f, 52f, 1.6f, 10f, zinc)
            for (cy in floatArrayOf(8f, 26f, 42f)) rect(83.4f, cy, 2.8f, 0.9f, sombre(zinc, 0.3f))
            mineraux--
            // La mousse qui verdit le pied des piédroits et le bas de l'arc.
            // La mousse et l'humidité se logent **dans les joints** et au
            // pied des piédroits : en boules posées sur la pierre, elles se
            // lisaient comme des buissons collés.
            for (k in 0 until 9) {
                val mx = if (k % 2 == 0) -4f + r() * 14f else 62f + r() * 26f
                val my = 16f + r() * 42f
                // Une plaque de mousse s'étale dans un joint : plusieurs
                // taches basses qui se touchent, jamais un trait vert.
                for (i in 0 until 4) {
                    val dx = mx + i * (0.8f + r() * 0.7f)
                    forme(Path().apply {
                        addOval(dx, my + r() * 0.5f - 0.25f, dx + 1f + r() * 1.2f, my + 0.5f + r() * 0.5f, Path.Direction.CW)
                    }, if (i % 3 == 0) sombre(vert(0.5f), 0.1f) else sombre(vert(0.34f), 0.2f), 0.85f)
                }
            }
            for (k in 0 until 5) {
                val mx = if (k % 2 == 0) -3f + r() * 10f else 64f + r() * 22f
                val my = 55f + r() * 4f
                for (i in 0 until 5) ovalePapier(mx + i * (1f + r() * 0.8f), my + r() * 1.2f,
                    0.7f + r() * 0.8f, 0.4f + r() * 0.4f,
                    if (i % 3 == 0) vert(0.56f) else sombre(vert(0.38f), 0.12f), 0.2f)
            }
            if (!vivant) lanterne(92f, 24f, false)
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
