package com.example.kreyolkeyboard.carnet

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.example.kreyolkeyboard.TranslationDictionary

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
 * - **Un filtre par jeu, et seulement sur les jeux déjà joués.** Depuis que les
 *   sept alimentent le carnet, « d'où vient cette carte » est une vraie
 *   question ; mais afficher sept filtres à qui n'a joué qu'à un seul jeu
 *   transforme une collection en formulaire. Les filtres apparaissent au fur et
 *   à mesure que les jeux donnent des cartes, et la ligne disparaît tant qu'il
 *   n'y en a qu'un.
 */
class CarnetFragment : DialogFragment() {

    private enum class Tri(val libelle: String) {
        RECENT("Récent"), ALPHA("A → Z"), RARETE("Rareté")
    }

    private var tri = Tri.RECENT
    private var filtre: JeuCarte? = null
    private var contenus: List<ContenuCarte> = emptyList()
    private var aRevoir = 0

    private lateinit var boutonReviser: TextView

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
            text = "Ouverture du carnet…"
        }
        colonne.addView(tvResume)

        // La révision, au-dessus de la collection et non au fond : c'est ce
        // qu'on vient faire quand des cartes sont dues, et la grille est ce
        // qu'on vient regarder le reste du temps. Caché tant qu'il n'y a rien
        // à revoir, pour ne pas promettre une session vide.
        boutonReviser = TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16f), dp(6f), dp(16f), dp(10f)) }
            textSize = 15f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(16f), dp(13f), dp(16f), dp(13f))
            background = GradientDrawable().apply {
                cornerRadius = 24f * d
                setColor(accent)
            }
            visibility = View.GONE
            isClickable = true
            setOnClickListener { lancerRevision() }
        }
        colonne.addView(boutonReviser)

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
                text = t.libelle
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
        colonne.addView(ligneTri)

        conteneurGrille = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), 0, dp(12f), dp(24f))
            // L'ombre portée des cartes rares déborde de leur vignette : sans
            // ces deux drapeaux, elle est rognée au ras du cadre et le relief
            // disparaît exactement là où il devait se voir.
            clipToPadding = false
            clipChildren = false
        }
        colonne.addView(ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            clipToPadding = false
            clipChildren = false
            addView(conteneurGrille)
        })

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
        val ctx = requireContext().applicationContext
        val principal = Handler(Looper.getMainLooper())
        Thread {
            TranslationDictionary.charger(ctx)
            TranslationDictionary.chargerExemples(ctx)
            // Donner une échéance aux cartes qui n'en ont pas, ici et non à la
            // capture : c'est ce qui étale un carnet déjà rempli au lieu de le
            // rendre entièrement dû le même jour. Voir [Carnet.planifier].
            Carnet.planifier(ctx)
            val dues = Carnet.aRevoir(ctx)
            val prets = Carnet.cartes(ctx).map { CarteCarnet.contenu(ctx, it) }
            principal.post {
                if (!isAdded) return@post
                contenus = prets
                aRevoir = dues
                majBoutonReviser()
                ligneTri.visibility = if (prets.isEmpty()) View.GONE else View.VISIBLE
                construireFiltres()
                surlignerTri()
                remplirGrille()
            }
        }.start()
    }

    private fun majBoutonReviser() {
        boutonReviser.visibility = if (aRevoir == 0) View.GONE else View.VISIBLE
        boutonReviser.text = if (aRevoir == 1) "🔁  Réviser 1 carte"
        else "🔁  Réviser $aRevoir cartes"
    }

    /**
     * Lance une session.
     *
     * Tout ce qui lit un fichier est fait en fond : les rangs de fréquence, les
     * phrases d'exemple, et surtout les compteurs de frappe de
     * [PreuveDeFrappe], qui vivent dans `filesDir`. Les cartes que le joueur a
     * écrites lui-même montent d'une boîte **sans passer par une question**, et
     * le bilan de la session est le seul endroit qui le dit.
     */
    private fun lancerRevision() {
        val ctx = requireContext().applicationContext
        val principal = Handler(Looper.getMainLooper())
        boutonReviser.isEnabled = false
        Thread {
            TranslationDictionary.charger(ctx)
            TranslationDictionary.chargerExemples(ctx)
            Carnet.planifier(ctx)
            val file = Carnet.file(ctx)
            val ecrites = PreuveDeFrappe.ecritesDepuisLaDerniereFois(ctx, file.map { it.forme })
            ecrites.forEach { Carnet.noter(ctx, it, reussi = true) }
            val aDemander = file.filter { it.forme !in ecrites }
                .map { CarteCarnet.contenu(ctx, it) }
            principal.post {
                if (!isAdded) return@post
                boutonReviser.isEnabled = true
                VueWidderhuelen(
                    hote = racine,
                    paquet = aDemander,
                    monteesParLeClavier = ecrites.toList(),
                    surNotation = { forme, reussi -> Carnet.noter(ctx, forme, reussi) },
                    surFin = { if (isAdded) chargerEnFond() }
                ).ouvrir()
            }
        }.start()
    }

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

        ligneJeux.addView(puce("Tous", null))
        presents.forEach { ligneJeux.addView(puce("${it.emoji} ${it.nom}", it)) }
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
            tvResume.text = "Le carnet est vide."
            conteneurGrille.addView(TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (40 * d).toInt() }
                text = "Chaque mot gagné dans l'un des sept jeux devient une " +
                    "carte et vient s'ajouter ici. Trouvez un mot, devinez-en " +
                    "un, écrivez-en un : la première carte est à une partie " +
                    "d'ici."
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
            append("${visibles.size} mot")
            if (visibles.size > 1) append("s")
            filtre?.let { append(" dans ${it.nom}") }
            append(" — ")
            append(
                Rarete.values().reversed()
                    .filter { (parRarete[it] ?: 0) > 0 }
                    .joinToString("   ") { "${it.symbole} ${parRarete[it]}" }
            )
        }

        val ordonnes = when (tri) {
            Tri.RECENT -> visibles.sortedByDescending { it.carte.numero }
            Tri.ALPHA -> visibles.sortedBy { it.carte.forme.lowercase() }
            Tri.RARETE -> visibles.sortedWith(
                compareByDescending<ContenuCarte> { it.rarete.ordinal }
                    .thenBy { it.carte.forme.lowercase() }
            )
        }

        // Deux colonnes, largeur calculée : les vignettes sont carrées à la
        // marge près, sinon leurs illustrations n'ont pas la même hauteur d'une
        // ligne à l'autre et la grille ondule.
        val dispo = resources.displayMetrics.widthPixels - (24 * d).toInt() * 2
        val cote = (dispo - (10 * d).toInt()) / 2

        var ligne: LinearLayout? = null
        ordonnes.forEachIndexed { i, c ->
            if (i % 2 == 0) {
                ligne = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    clipChildren = false
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = (10 * d).toInt() }
                }
                conteneurGrille.addView(ligne)
            }
            ligne?.addView(CarteCarnet.vignette(ctx, c, cote).apply {
                layoutParams = LinearLayout.LayoutParams(
                    cote, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { if (i % 2 == 0) rightMargin = (10 * d).toInt() }
                isClickable = true
                setOnClickListener { montrerCarte(c) }
            })
        }
    }

    /**
     * La carte entière, posée au-dessus de la grille et **retournée pour
     * arriver** : c'est le geste qui fait la carte à collectionner, et il ne
     * coûte qu'une rotation de vue.
     */
    private fun montrerCarte(c: ContenuCarte) {
        val ctx = context ?: return
        val d = resources.displayMetrics.density

        val voile = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xCC000000.toInt())
            isClickable = true
        }

        val defilement = ScrollView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply { setMargins((22 * d).toInt(), 0, (22 * d).toInt(), 0) }
            isVerticalScrollBarEnabled = false
        }
        val carte = CarteCarnet.complete(ctx, c)
        defilement.addView(carte)
        voile.addView(defilement)

        val fermer = { ->
            voile.animate().alpha(0f).setDuration(160)
                .withEndAction { racine.removeView(voile) }.start()
        }
        voile.setOnClickListener { fermer() }
        // La carte ne ferme pas : on peut la lire et la faire défiler.
        carte.isClickable = true

        racine.addView(voile)
        voile.alpha = 0f
        voile.animate().alpha(1f).setDuration(160).start()

        // Une carte rare se retourne plus lentement et dépasse légèrement son
        // aplomb avant de se poser : le même geste, mais qui prend son temps.
        // C'est la seule chose que la durée d'une animation sait dire, et elle
        // le dit sans un mot.
        carte.cameraDistance = 9000f * d
        carte.rotationY = -85f
        carte.animate().rotationY(0f)
            .setDuration(if (c.rarete.distinguee) 470L else 360L)
            .setInterpolator(
                if (c.rarete.distinguee) OvershootInterpolator(1.4f)
                else DecelerateInterpolator()
            ).start()
    }

    override fun onStart() {
        super.onStart()
        // Sans cela le dialogue s'ajuste à son contenu et laisse l'activité
        // visible sur les bords : la collection mérite tout l'écran.
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
}
