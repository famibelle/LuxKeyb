package com.example.kreyolkeyboard.carnet

import com.example.kreyolkeyboard.R
import androidx.annotation.StringRes
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.kreyolkeyboard.TranslationDictionary
import com.example.kreyolkeyboard.applicatifDansLaLangue

/**
 * Le carnet : toutes les cartes gagnées, consultables en dehors d'une partie.
 *
 * C'est ce qui manquait à « Ce que vous avez gagné », qui repartait de zéro à
 * chaque grille et se lisait comme un journal. Ici la collection est
 * permanente, elle a un lieu, et chaque mot y est un objet.
 *
 * Quatre décisions d'écran :
 *
 * - **Un `DialogFragment` plein écran**, comme le Guide et À Propos : pas
 *   d'entrée au manifeste, pas de cycle de vie d'activité en plus, et surtout
 *   pas un cinquième onglet — la barre en porte quatre et `REAL_COUNT` pilote
 *   le modulo du pager cyclique.
 * - **Le chargement est fait sur un fil de fond.** Ouvrir le carnet demande de
 *   relever les rangs de fréquence (balayage de `luxemburgish_dict.json`, 1,27
 *   Mo) et de charger les phrases d'exemple du LOD (2,6 Mo) : sur le fil
 *   principal, c'est un gel visible au moment précis où l'écran doit
 *   apparaître.
 * - **Une grille de vignettes, et la carte entière au toucher**, qui se
 *   retourne pour arriver. La vignette fait la collection, la carte fait la
 *   leçon ; tout mettre dans la vignette rendrait la grille illisible, tout
 *   mettre dans la carte supprimerait la collection.
 * - **La méthode est un objet, pas une pastille.** [BoiteLeitner] remplace le
 *   bouton « Réviser N cartes », qui disait le compte et rien d'autre : sept
 *   casiers de bois, les cartes dues soulevées dans leur fente, et le compte
 *   gravé sur une plaque de laiton. Les casiers se **consultent** ; la révision
 *   reste une cible unique, décidée par l'échéance. Laisser choisir son casier
 *   ferait réviser des cartes pas encore dues et fausserait le calendrier.
 * - **Un filtre par jeu, et seulement sur les jeux déjà joués.** Depuis que les
 *   sept alimentent le carnet, « d'où vient cette carte » est une vraie
 *   question ; mais afficher sept filtres à qui n'a joué qu'à un seul jeu
 *   transforme une collection en formulaire. Les filtres apparaissent au fur et
 *   à mesure que les jeux donnent des cartes, et la ligne disparaît tant qu'il
 *   n'y en a qu'un.
 */
class CarnetFragment : DialogFragment() {

    companion object {
        /**
         * Largeur visée pour une vignette, en dp — celle mesurée en portrait
         * sur un téléphone (deux colonnes sur ~393dp de large). Le nombre de
         * colonnes en dérive plutôt que de rester figé à deux : à l'italienne
         * la largeur disponible triple sans que la hauteur d'écran suive, et
         * des vignettes toujours carrées sur deux colonnes débordent en bas
         * de l'écran avant même de montrer le nom du mot.
         */
        private const val LARGEUR_CIBLE_VIGNETTE_DP = 165f

        /** Couleur des pages de l'album : un papier crème, pas le gris de l'écran. */
        private val PAPIER = Color.parseColor("#FFFBF2")
        /** La couverture de l'album, sur laquelle les pages sont posées. */
        private val COUVERTURE = Color.parseColor("#5D4037")
    }

    /**
     * Les quatre façons de regarder la collection.
     *
     * [ETAGERE] n'est pas un tri mais une disposition : elle range les cartes
     * par boîte de Leitner, casier par casier, avec les casiers vides. C'est le
     * seul endroit où la méthode se voit — la vignette porte déjà la boîte
     * d'*une* carte en six pastilles, mais rien ne disait où en était la
     * collection, ni combien de mots attendaient dans chaque casier.
     *
     * Elle recoupe désormais [BoiteLeitner], qui ouvre un casier à la fois, et
     * la redondance est voulue : l'étagère déplie les sept casiers d'un seul
     * tenant pour qu'on les compare, la boîte en ouvre un pour qu'on le lise.
     * Ce ne sont pas deux chemins vers le même écran mais deux questions,
     * « où en suis-je » et « qu'y a-t-il là-dedans ».
     */
    private enum class Tri(@StringRes val libelle: Int) {
        RECENT(R.string.tri_recent), ALPHA(R.string.tri_alpha), RARETE(R.string.tri_rarete), ETAGERE(R.string.tri_etagere)
    }

    private var tri = Tri.RECENT
    private var filtre: JeuCarte? = null
    private var contenus: List<ContenuCarte> = emptyList()

    /**
     * La carte entière ouverte, s'il y en a une : elle passe avant le casier.
     * On la feuillette dans l'ordre de l'écran d'où on l'a ouverte.
     */
    private var lecteur: LecteurCartes? = null

    /** Le défilement de la grille, et l'album qui le remplace sur tablette. */
    private lateinit var defilementGrille: ScrollView
    private lateinit var zoneAlbum: FrameLayout
    private var pagesAlbum: ViewPager2? = null

    private lateinit var racine: FrameLayout
    private lateinit var conteneurGrille: LinearLayout
    private lateinit var tvResume: TextView
    private lateinit var ligneTri: LinearLayout
    private lateinit var ligneJeux: LinearLayout
    private lateinit var defilementJeux: HorizontalScrollView

    private val accent = Carnet.COULEUR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_DeviceDefault_Light_NoActionBar)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val d = resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()

        val colonne = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        colonne.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(accent)
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
            // Le dialogue s'étend sous la barre d'état : le bandeau la colore
            // et son titre s'en écarte, au lieu de s'écrire sous l'heure.
            setOnApplyWindowInsetsListener { v, insets ->
                val haut = if (android.os.Build.VERSION.SDK_INT >= 30) {
                    insets.getInsets(android.view.WindowInsets.Type.statusBars()).top
                } else {
                    @Suppress("DEPRECATION") insets.systemWindowInsetTop
                }
                v.setPadding(dp(16f), dp(14f) + haut, dp(16f), dp(14f))
                insets
            }
            addView(TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                text = "📔  Mäi Carnet"
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
            addView(TextView(ctx).apply {
                text = "✕"
                textSize = 22f
                setTextColor(Color.WHITE)
                setPadding(dp(20f), 0, dp(4f), 0)
                isClickable = true
                setOnClickListener { dismiss() }
            })
        })

        tvResume = TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(16f), dp(12f), dp(16f), dp(6f))
            textSize = 14f
            setTextColor(Color.parseColor("#424242"))
            text = getString(R.string.carnet_ouverture)
        }
        colonne.addView(tvResume)


        // Le filtre par jeu défile : sept jeux et un « Tous » ne tiennent pas
        // sur la largeur d'un téléphone, et une ligne qui se replie en deux
        // repousserait la collection hors de l'écran.
        ligneJeux = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12f), 0, dp(12f), dp(6f))
        }
        defilementJeux = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            visibility = View.GONE
            addView(ligneJeux)
        }
        colonne.addView(defilementJeux)

        ligneTri = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12f), 0, dp(12f), dp(8f))
            visibility = View.GONE
        }
        Tri.values().forEach { t ->
            ligneTri.addView(TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp(4f), 0, dp(4f), 0) }
                setText(t.libelle)
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(14f), dp(7f), dp(14f), dp(7f))
                tag = t
                isClickable = true
                setOnClickListener {
                    tri = t
                    surlignerTri()
                    remplirGrille()
                }
            })
        }
        // La ligne de tri défile comme celle des jeux : « Étagère » est le
        // quatrième bouton, et quatre puces dépassent la largeur d'un petit
        // téléphone. Le `ScrollView` se replie tout seul quand `ligneTri` passe
        // en `GONE`, il n'y a donc rien de plus à piloter.
        colonne.addView(HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            addView(ligneTri)
        })

        conteneurGrille = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), 0, dp(12f), dp(24f))
            // L'ombre portée des cartes rares déborde de leur vignette : sans
            // ces deux drapeaux, elle est rognée au ras du cadre et le relief
            // disparaît exactement là où il devait se voir.
            clipToPadding = false
            clipChildren = false
        }
        defilementGrille = ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            clipToPadding = false
            clipChildren = false
            addView(conteneurGrille)
        }
        colonne.addView(defilementGrille)

        zoneAlbum = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            visibility = View.GONE
        }
        colonne.addView(zoneAlbum)

        racine = FrameLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(colonne)
        }

        chargerEnFond()
        return racine
    }

    /**
     * Assemble les contenus hors du fil principal, puis rend la main.
     *
     * `chargerExemples` a son propre verrou et `lireRangs` balaye 1,27 Mo :
     * les deux sont faits ici, une fois, avant que quoi que ce soit
     * s'affiche.
     */
    private fun chargerEnFond() {
        val ctx = requireContext().applicatifDansLaLangue()
        val principal = Handler(Looper.getMainLooper())
        Thread {
            TranslationDictionary.charger(ctx)
            TranslationDictionary.chargerExemples(ctx)
            // Donner une échéance aux cartes qui n'en ont pas, ici et non à la
            // capture : c'est ce qui étale un carnet déjà rempli au lieu de le
            // rendre entièrement dû le même jour. Voir [Carnet.planifier].
            Carnet.planifier(ctx)
            val prets = Carnet.cartes(ctx).map { CarteCarnet.contenu(ctx, it) }
            principal.post {
                if (!isAdded) return@post
                contenus = prets
                ligneTri.visibility = if (prets.isEmpty()) View.GONE else View.VISIBLE
                construireFiltres()
                surlignerTri()
                remplirGrille()
            }
        }.start()
    }

    /**
     * L'état du carnet, remis dans la boîte.
     *
     * La boîte compte elle-même ce qui est dû, à partir des cartes qu'elle
     * affiche : c'est le seul moyen que sa plaque et ses cartes soulevées ne se
     * contredisent jamais. Un second décompte tiré de [Carnet.aRevoir]
     * annoncerait un jour quatre cartes là où aucun casier n'en soulève, et ce
     * serait lu comme un bogue et non comme une échéance.
     */

    /**
     * Les filtres, un par jeu qui a déjà donné une carte, plus « Tous ».
     *
     * Un seul jeu représenté ne mérite pas de filtre : le bouton « Tous » et le
     * bouton du jeu diraient la même chose.
     */
    private fun construireFiltres() {
        val ctx = context ?: return
        val d = resources.displayMetrics.density
        ligneJeux.removeAllViews()

        val presents = JeuCarte.values().filter { jeu ->
            contenus.any { jeu in it.carte.jeux }
        }
        if (presents.size < 2) {
            defilementJeux.visibility = View.GONE
            filtre = null
            return
        }
        defilementJeux.visibility = View.VISIBLE

        fun puce(libelle: String, jeu: JeuCarte?) = TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins((4 * d).toInt(), 0, (4 * d).toInt(), 0) }
            text = libelle
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding((12 * d).toInt(), (7 * d).toInt(), (12 * d).toInt(), (7 * d).toInt())
            tag = jeu
            isClickable = true
            setOnClickListener {
                filtre = jeu
                surlignerFiltres()
                remplirGrille()
            }
        }

        ligneJeux.addView(puce(getString(R.string.carnet_tous), null))
        presents.forEach { ligneJeux.addView(puce("${it.emoji} ${it.libelle(ctx)}", it)) }
        if (filtre != null && filtre !in presents) filtre = null
        surlignerFiltres()
    }

    private fun surlignerFiltres() {
        val d = resources.displayMetrics.density
        for (i in 0 until ligneJeux.childCount) {
            val vue = ligneJeux.getChildAt(i) as TextView
            val jeu = vue.tag as? JeuCarte
            val actif = jeu == filtre
            // Chaque filtre prend la couleur de son jeu : c'est la même que
            // celle de la carte du hub et que celle du mot sur la vignette.
            val couleur = jeu?.couleur ?: accent
            vue.setTextColor(if (actif) Color.WHITE else couleur)
            vue.background = GradientDrawable().apply {
                cornerRadius = 20f * d
                setColor(if (actif) couleur else Color.WHITE)
                setStroke((1 * d).toInt(), couleur)
            }
        }
    }

    private fun surlignerTri() {
        for (i in 0 until ligneTri.childCount) {
            val vue = ligneTri.getChildAt(i) as TextView
            val actif = vue.tag == tri
            vue.setTextColor(if (actif) Color.WHITE else Color.parseColor("#616161"))
            vue.background = GradientDrawable().apply {
                cornerRadius = 20f * resources.displayMetrics.density
                setColor(if (actif) accent else Color.WHITE)
                setStroke(
                    (1 * resources.displayMetrics.density).toInt(),
                    if (actif) accent else Color.parseColor("#D0D0D0")
                )
            }
        }
    }

    private fun remplirGrille() {
        val ctx = context ?: return
        val d = resources.displayMetrics.density
        conteneurGrille.removeAllViews()

        if (contenus.isEmpty()) {
            tvResume.text = getString(R.string.carnet_vide)
            conteneurGrille.addView(TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (40 * d).toInt() }
                text = getString(R.string.carnet_vide_explication)
                textSize = 15f
                gravity = Gravity.CENTER
                setLineSpacing(0f, 1.25f)
                setTextColor(Color.parseColor("#757575"))
                setPadding((24 * d).toInt(), 0, (24 * d).toInt(), 0)
            })
            return
        }

        val visibles = filtre?.let { jeu -> contenus.filter { jeu in it.carte.jeux } }
            ?: contenus

        val parRarete = visibles.groupingBy { it.rarete }.eachCount()
        tvResume.text = buildString {
            val mots = resources.getQuantityString(R.plurals.mots, visibles.size, visibles.size)
            append(filtre?.let { getString(R.string.carnet_mots_dans, mots, it.libelle(ctx)) } ?: mots)
            append(" · ")
            append(
                Rarete.values().reversed()
                    .filter { (parRarete[it] ?: 0) > 0 }
                    .joinToString("   ") { "${it.symbole} ${parRarete[it]}" }
            )
        }

        // Largeur calculée, colonnes adaptées à ce qu'elle laisse : les
        // vignettes visent LARGEUR_CIBLE_VIGNETTE_DP et restent carrées à la
        // marge près, sinon leurs illustrations n'ont pas la même hauteur
        // d'une ligne à l'autre et la grille ondule. Fixer les colonnes à
        // deux, comme avant, grossissait les vignettes avec la largeur de
        // l'écran plutôt que d'en tenir compte : à l'italienne, sur un
        // téléphone, elles débordaient de la hauteur disponible et
        // masquaient jusqu'au nom du mot sans un défilement.
        val gouttiere = (10 * d).toInt()
        // Toute la largeur de l'écran : le carnet s'ouvre par-dessus les
        // onglets, rail compris, il n'a pas à en retirer la place. On n'en
        // retire que les marges de la grille, 12 dp de part et d'autre : en
        // ôter 24 laissait à droite une bande deux fois plus large qu'à gauche.
        val dispo = resources.displayMetrics.widthPixels - (12 * d).toInt() * 2
        val cible = (LARGEUR_CIBLE_VIGNETTE_DP * d).toInt()
        val colonnes = ((dispo + gouttiere) / (cible + gouttiere)).coerceAtLeast(2)
        val cote = (dispo - (colonnes - 1) * gouttiere) / colonnes

        if (tri == Tri.ETAGERE) {
            // Le résumé change de sujet avec la disposition : par casiers, ce
            // qui compte n'est plus la rareté mais l'avancement, et le prix à
            // payer pour une carte acquise est ce qu'aucun écran ne disait.
            val acquises = visibles.count { it.carte.acquise }
            tvResume.text = resources.getQuantityString(
                R.plurals.carnet_acquis_sur, visibles.size, acquises, visibles.size
            )
            montrerAlbum(false)
            remplirEtagere(ctx, visibles, cote, colonnes)
            return
        }

        val ordonnes = when (tri) {
            Tri.ALPHA -> visibles.sortedBy { it.carte.forme.lowercase() }
            Tri.RARETE -> visibles.sortedWith(
                compareByDescending<ContenuCarte> { it.rarete.ordinal }
                    .thenBy { it.carte.forme.lowercase() }
            )
            else -> visibles.sortedByDescending { it.carte.numero }
        }
        if (enAlbum()) {
            montrerAlbum(true)
            quandMesuree(zoneAlbum) { if (isAdded) construireAlbum(ordonnes) }
            return
        }
        montrerAlbum(false)
        emettreVignettes(ctx, ordonnes, cote, colonnes, ordre = ordonnes)
    }

    // ---------------------------------------------------------------- l'album

    /**
     * Sur tablette, la collection se feuillette comme un album au lieu de
     * défiler : des pages qui tiennent chacune sur l'écran, deux à deux quand
     * l'écran est couché, et qu'on tourne d'un glissé.
     *
     * Le défilement d'une grille convient au téléphone, où l'on cherche une
     * carte. Sur un grand écran posé devant soi, on regarde sa collection, et
     * c'est la page qui en fait un objet : une place pour chaque carte, celles
     * de la dernière page qui attendent encore la leur, et le nombre de pages
     * qui dit à lui seul l'ampleur de ce qu'on a gagné.
     *
     * L'étagère garde son défilement : ses casiers sont des chapitres de
     * longueurs inégales, que des pages fixes couperaient n'importe où.
     */
    private fun enAlbum(): Boolean = resources.configuration.smallestScreenWidthDp >= 600

    /**
     * Lance [action] une fois que [vue] a une taille : la zone de l'album sort
     * de `GONE` au moment où on la remplit, et ses pages se calculent sur sa
     * hauteur, qu'elle n'a pas encore.
     */
    private fun quandMesuree(vue: View, action: () -> Unit) {
        if (vue.width > 0 && vue.height > 0 && !vue.isLayoutRequested) {
            action()
            return
        }
        vue.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int
            ) {
                if (v.width <= 0 || v.height <= 0) return
                v.removeOnLayoutChangeListener(this)
                // Hors de la passe de mise en page en cours : l'album y ajoute
                // des vues.
                v.post(action)
            }
        })
    }

    private fun montrerAlbum(oui: Boolean) {
        defilementGrille.visibility = if (oui) View.GONE else View.VISIBLE
        zoneAlbum.visibility = if (oui) View.VISIBLE else View.GONE
        if (!oui) {
            zoneAlbum.removeAllViews()
            pagesAlbum = null
        }
    }

    private fun construireAlbum(liste: List<ContenuCarte>) {
        val ctx = context ?: return
        val d = resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()
        zoneAlbum.removeAllViews()

        val largeur = zoneAlbum.width
        val hauteur = zoneAlbum.height
        if (largeur <= 0 || hauteur <= 0) return

        // Deux pages côte à côte dès que l'écran est plus large que haut.
        val doublePage = largeur > hauteur
        val marge = dp(16f)
        val reliure = dp(14f)
        // Le carnet s'étend sous la barre de navigation : le pied s'en écarte,
        // sinon ses flèches passent sous les boutons du système.
        val insets = zoneAlbum.rootWindowInsets
        val barreNavigation = when {
            insets == null -> 0
            android.os.Build.VERSION.SDK_INT >= 30 ->
                insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
            else -> @Suppress("DEPRECATION") insets.systemWindowInsetBottom
        }
        val hauteurPied = dp(48f) + barreNavigation
        val largeurPage = if (doublePage) (largeur - 2 * marge - 2 * reliure - reliure) / 2
            else largeur - 2 * marge - 2 * reliure
        val hauteurPage = hauteur - hauteurPied - 2 * reliure - dp(8f)

        // Les vignettes visent la taille de la grille, puis s'ajustent pour
        // remplir la page exactement : ni bande vide, ni rangée coupée.
        val interieur = dp(18f)
        val gouttiere = dp(12f)
        val utileL = largeurPage - 2 * interieur
        val utileH = hauteurPage - 2 * interieur - dp(20f)
        // Parmi les grilles possibles, la plus garnie dont les vignettes ne
        // descendent pas sous 80 % de leur taille de grille ; à nombre égal,
        // la plus grande. Arrondir chaque côté à part laissait un tiers de la
        // page vide, faute de place pour une rangée entière à pleine taille.
        // À 85 %, une tablette de 10 pouces couchée perdait sa troisième
        // rangée pour quelques pixels et gardait une bande vide sous la deuxième.
        val plancher = LARGEUR_CIBLE_VIGNETTE_DP * d * 0.80f
        fun coteDe(c: Int, r: Int) = minOf(
            (utileL - (c - 1) * gouttiere) / c,
            (utileH - (r - 1) * gouttiere) / r
        )
        val (colonnes, rangees) = (2..8).flatMap { c -> (2..6).map { r -> c to r } }
            .filter { (c, r) -> coteDe(c, r) >= plancher }
            .maxWithOrNull(compareBy<Pair<Int, Int>>({ it.first * it.second }, { coteDe(it.first, it.second) }))
            ?: (2 to 2)
        val cote = coteDe(colonnes, rangees)
        val parPage = colonnes * rangees
        val pages = liste.chunked(parPage).ifEmpty { listOf(emptyList()) }
        val parVue = if (doublePage) 2 else 1
        val vues = (pages.size + parVue - 1) / parVue

        val pied = TextView(ctx).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#616161"))
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
        }

        val pager = ViewPager2(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                hauteur - hauteurPied
            )
            offscreenPageLimit = 1
            adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun getItemCount() = vues
                override fun onCreateViewHolder(parent: ViewGroup, type: Int) =
                    object : RecyclerView.ViewHolder(FrameLayout(ctx).apply {
                        layoutParams = RecyclerView.LayoutParams(
                            RecyclerView.LayoutParams.MATCH_PARENT,
                            RecyclerView.LayoutParams.MATCH_PARENT
                        )
                    }) {}
                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val cadre = holder.itemView as FrameLayout
                    cadre.removeAllViews()
                    cadre.addView(doublePageDeLAlbum(
                        ctx, pages, position * parVue, parVue, liste,
                        largeurPage, hauteurPage, reliure, interieur, gouttiere,
                        cote, colonnes, rangees
                    ), FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    ))
                }
            }
            // On tourne la page autour de la reliure, et non en la faisant
            // glisser comme un écran : c'est tout ce qui distingue un album
            // d'une liste qui défile de côté.
            setPageTransformer { page, position ->
                page.cameraDistance = 20000f * d
                page.pivotY = page.height / 2f
                page.pivotX = if (position < 0) page.width.toFloat() else 0f
                page.rotationY = (position * 55f).coerceIn(-90f, 90f)
                page.alpha = 1f - (kotlin.math.abs(position) * 0.4f).coerceAtMost(1f)
            }
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    val premiere = position * parVue + 1
                    val derniere = minOf(premiere + parVue - 1, pages.size)
                    val numeros = if (derniere > premiere) "$premiere–$derniere" else "$premiere"
                    pied.text = "$numeros / ${pages.size}"
                }
            })
        }
        pagesAlbum = pager
        zoneAlbum.addView(pager)

        // Le pied : le numéro des pages, entre deux flèches qui les tournent
        // aussi, pour qui ne pense pas à glisser.
        fun fleche(signe: String, @StringRes description: Int, pas: Int) = TextView(ctx).apply {
            text = signe
            textSize = 26f
            setTextColor(Carnet.COULEUR)
            gravity = Gravity.CENTER
            contentDescription = getString(description)
            layoutParams = LinearLayout.LayoutParams(dp(56f), dp(48f))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val cible = (pager.currentItem + pas).coerceIn(0, vues - 1)
                pager.setCurrentItem(cible, true)
            }
        }
        zoneAlbum.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(48f), Gravity.BOTTOM
            ).apply { bottomMargin = barreNavigation }
            addView(fleche("‹", R.string.album_page_precedente, -1))
            addView(pied, LinearLayout.LayoutParams(dp(140f), dp(48f)))
            addView(fleche("›", R.string.album_page_suivante, 1))
        })
        pied.text = "${if (doublePage && pages.size > 1) "1–2" else "1"} / ${pages.size}"
    }

    /**
     * Une vue de l'album : une page, ou deux posées sur la couverture de part
     * et d'autre de la reliure.
     */
    private fun doublePageDeLAlbum(
        ctx: Context,
        pages: List<List<ContenuCarte>>,
        premiere: Int,
        parVue: Int,
        ordre: List<ContenuCarte>,
        largeurPage: Int,
        hauteurPage: Int,
        reliure: Int,
        interieur: Int,
        gouttiere: Int,
        cote: Int,
        colonnes: Int,
        rangees: Int
    ): View {
        val d = resources.displayMetrics.density
        val couverture = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(reliure, reliure, reliure, reliure)
            background = GradientDrawable().apply {
                cornerRadius = 16f * d
                setColor(COUVERTURE)
            }
            elevation = 6f * d
        }
        for (k in 0 until parVue) {
            val indice = premiere + k
            val gauche = parVue == 2 && k == 0
            val page = if (indice < pages.size) {
                pageDeLAlbum(ctx, pages[indice], indice + 1, ordre, largeurPage, hauteurPage,
                    interieur, gouttiere, cote, colonnes, rangees,
                    derniere = indice == pages.lastIndex,
                    ombre = if (parVue == 2) (if (gauche) Gravity.END else Gravity.START) else null)
            } else {
                // Le verso blanc d'une dernière page de droite.
                View(ctx).apply {
                    background = GradientDrawable().apply {
                        cornerRadius = 8f * d
                        setColor(PAPIER)
                    }
                }
            }
            couverture.addView(page, LinearLayout.LayoutParams(largeurPage, hauteurPage).apply {
                if (gauche) rightMargin = reliure
            })
        }
        return couverture
    }

    /**
     * Une page : les vignettes à leur place, des pochettes vides pour celles
     * qui restent à gagner sur la dernière, et le numéro de la page en bas.
     */
    private fun pageDeLAlbum(
        ctx: Context,
        cartes: List<ContenuCarte>,
        numero: Int,
        ordre: List<ContenuCarte>,
        largeurPage: Int,
        hauteurPage: Int,
        interieur: Int,
        gouttiere: Int,
        cote: Int,
        colonnes: Int,
        rangees: Int,
        derniere: Boolean,
        ombre: Int?
    ): View {
        val d = resources.displayMetrics.density
        val page = FrameLayout(ctx).apply {
            background = GradientDrawable().apply {
                cornerRadius = 8f * d
                setColor(PAPIER)
            }
            clipChildren = false
        }

        val grille = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
        }
        for (r in 0 until rangees) {
            val ligne = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
            }
            for (k in 0 until colonnes) {
                val i = r * colonnes + k
                val parametres = LinearLayout.LayoutParams(cote, cote).apply {
                    if (k < colonnes - 1) rightMargin = gouttiere
                }
                val c = cartes.getOrNull(i)
                when {
                    c != null -> ligne.addView(CarteCarnet.vignette(ctx, c, cote).apply {
                        isClickable = true
                        setOnClickListener { montrerCarte(c, ordre) }
                    }, parametres)
                    // Sur la dernière page seulement : les places qui restent
                    // à prendre. Ailleurs une page est pleine par construction.
                    derniere && cartes.isNotEmpty() -> ligne.addView(View(ctx).apply {
                        background = GradientDrawable().apply {
                            cornerRadius = 10f * d
                            setStroke((2 * d).toInt(), Color.parseColor("#DCD0B8"), 9f * d, 6f * d)
                        }
                    }, parametres)
                    else -> ligne.addView(View(ctx), parametres)
                }
            }
            grille.addView(ligne, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { if (r < rangees - 1) bottomMargin = gouttiere })
        }
        page.addView(grille, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply {
            // Centrée dans ce qui reste au-dessus du numéro de page : le reste
            // se partage en haut et en bas au lieu de tomber sous la grille.
            topMargin = interieur
            bottomMargin = interieur + (20 * d).toInt()
        })

        // Le creux de la reliure : une ombre douce le long du bord intérieur.
        if (ombre != null) {
            page.addView(View(ctx).apply {
                background = GradientDrawable(
                    if (ombre == Gravity.END) GradientDrawable.Orientation.LEFT_RIGHT
                    else GradientDrawable.Orientation.RIGHT_LEFT,
                    intArrayOf(Color.TRANSPARENT, Color.parseColor("#26000000"))
                )
            }, FrameLayout.LayoutParams(
                (22 * d).toInt(), FrameLayout.LayoutParams.MATCH_PARENT, ombre
            ))
        }

        page.addView(TextView(ctx).apply {
            text = "$numero"
            textSize = 12f
            setTextColor(Color.parseColor("#A1887F"))
        }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { bottomMargin = (8 * d).toInt() })
        return page
    }

    /**
     * La collection rangée par casier, casiers vides compris.
     *
     * Les sept casiers sont posés même quand ils sont vides, et c'est tout
     * l'intérêt : un casier vide est encore un casier, il montre le chemin qui
     * reste. Les masquer donnerait une liste de trois lignes qui ne dit plus
     * qu'il y a une échelle.
     *
     * À l'intérieur d'un casier, l'ordre est alphabétique. Ni la capture ni la
     * rareté n'ont de sens ici — le casier *est* déjà l'ordre, celui de la
     * mémoire, et on vient y chercher un mot précis.
     */
    private fun remplirEtagere(
        ctx: Context,
        visibles: List<ContenuCarte>,
        cote: Int,
        colonnes: Int
    ) {
        val aujourdHui = Widderhuelen.aujourdHui()
        val parCasier = visibles.groupBy {
            it.carte.boite.coerceIn(0, Widderhuelen.BOITE_ACQUISE)
        }
        val casiers = (0..Widderhuelen.BOITE_ACQUISE).map { boite ->
            parCasier[boite].orEmpty().sortedBy { it.carte.forme.lowercase() }
        }
        // On feuillette l'étagère d'un casier au suivant, sans s'arrêter aux
        // cloisons.
        val ordre = casiers.flatten()
        casiers.forEachIndexed { boite, dedans ->
            val dues = dedans.count {
                Widderhuelen.estDue(it.carte.boite, it.carte.jourEcheance, aujourdHui)
            }
            conteneurGrille.addView(etiquetteCasier(ctx, boite, dedans.size, dues))
            emettreVignettes(ctx, dedans, cote, colonnes, ordre = ordre)
        }
    }

    /**
     * L'étiquette d'un casier, et sa planche.
     *
     * Le libellé dit le **rythme**, jamais le numéro de boîte : « revu chaque
     * semaine » se comprend sans rien savoir de Leitner, « boîte 2 » demande
     * qu'on ait lu la documentation. La planche, elle, est la barre de six
     * segments que chaque vignette porte déjà sur son cadre (voir
     * [Ornement.dessinerBoite]) — le casier et les cartes qu'on y trouve
     * disent donc la même chose de la même façon, ce qui est la seule raison de
     * ne pas avoir inventé un autre indicateur ici.
     */


    /** Referme la carte ouverte. Rend `false` s'il n'y en avait aucune. */
    private fun fermerCarte(): Boolean {
        val l = lecteur?.takeIf { it.ouvert } ?: return false
        l.fermer()
        return true
    }

    /** Les vignettes d'une liste, deux par ligne, dans [hote]. */
    private fun emettreVignettes(
        ctx: Context,
        liste: List<ContenuCarte>,
        cote: Int,
        colonnes: Int,
        hote: LinearLayout = conteneurGrille,
        ordre: List<ContenuCarte> = liste
    ) {
        val d = resources.displayMetrics.density
        val gouttiere = (10 * d).toInt()
        var ligne: LinearLayout? = null
        liste.forEachIndexed { i, c ->
            val posColonne = i % colonnes
            if (posColonne == 0) {
                ligne = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    clipChildren = false
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = gouttiere }
                }
                hote.addView(ligne)
            }
            ligne?.addView(CarteCarnet.vignette(ctx, c, cote).apply {
                layoutParams = LinearLayout.LayoutParams(
                    cote, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { if (posColonne != colonnes - 1) rightMargin = gouttiere }
                isClickable = true
                setOnClickListener { montrerCarte(c, ordre) }
            })
        }
    }

    /** La carte entière, feuilletable dans l'ordre de l'écran d'où on l'ouvre : voir [LecteurCartes]. */
    private fun montrerCarte(c: ContenuCarte, ordre: List<ContenuCarte> = listOf(c)) {
        if (context == null) return
        val feuillet = ordre.ifEmpty { listOf(c) }
        lecteur = LecteurCartes(
            racine, feuillet, feuillet.indexOf(c).coerceAtLeast(0),
            surFermeture = { lecteur = null }
        ).also { it.ouvrir() }
    }

    override fun onStart() {
        super.onStart()
        // Sans cela le dialogue s'ajuste à son contenu et laisse l'activité
        // visible sur les bords : la collection mérite tout l'écran.
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        // Le bouton Retour dépile : la carte ouverte, puis le casier ouvert,
        // puis le carnet. Il fermait le dialogue entier depuis n'importe quelle
        // profondeur, ce qui passait tant que le carnet n'avait aucune
        // navigation et devient faux depuis que la boîte en a une.
        //
        // Les deux actions sont consommées, pas seulement `ACTION_UP` :
        // `Dialog` annule sur la levée mais retient la descente, et ne
        // consommer que l'une des deux laisse un retour fantôme au prochain
        // appui.
        dialog?.setOnKeyListener { _, code, evenement ->
            // Les flèches d'un clavier physique feuillettent : la carte
            // ouverte, sinon les pages de l'album.
            val pas = when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> -1
                KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                else -> 0
            }
            when {
                pas != 0 && lecteur != null -> {
                    if (evenement.action == KeyEvent.ACTION_DOWN) lecteur?.feuilleter(pas)
                    true
                }
                pas != 0 && pagesAlbum != null -> {
                    if (evenement.action == KeyEvent.ACTION_DOWN) {
                        pagesAlbum?.let { it.setCurrentItem(it.currentItem + pas, true) }
                    }
                    true
                }
                code != KeyEvent.KEYCODE_BACK -> false
                lecteur == null -> false
                else -> {
                    if (evenement.action == KeyEvent.ACTION_UP) {
                        fermerCarte()
                    }
                    true
                }
            }
        }
    }
}
