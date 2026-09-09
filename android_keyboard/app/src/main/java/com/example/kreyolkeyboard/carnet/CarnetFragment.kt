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
import android.widget.FrameLayout
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
 * Trois décisions d'écran :
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
 */
class CarnetFragment : DialogFragment() {

    private enum class Tri(val libelle: String) {
        RECENT("Récent"), ALPHA("A → Z"), RARETE("Rareté")
    }

    private var tri = Tri.RECENT
    private var contenus: List<ContenuCarte> = emptyList()

    private lateinit var racine: FrameLayout
    private lateinit var conteneurGrille: LinearLayout
    private lateinit var tvResume: TextView
    private lateinit var ligneTri: LinearLayout

    private val teal = Color.parseColor("#00796B")
    private val inerte = Color.parseColor("#BDBDBD")

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
            setBackgroundColor(teal)
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
        }
        colonne.addView(ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
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
            val prets = CarnetWuertplaz.cartes(ctx).map { CarteWuert.contenu(ctx, it) }
            principal.post {
                if (!isAdded) return@post
                contenus = prets
                ligneTri.visibility = if (prets.isEmpty()) View.GONE else View.VISIBLE
                surlignerTri()
                remplirGrille()
            }
        }.start()
    }

    private fun surlignerTri() {
        for (i in 0 until ligneTri.childCount) {
            val vue = ligneTri.getChildAt(i) as TextView
            val actif = vue.tag == tri
            vue.setTextColor(if (actif) Color.WHITE else Color.parseColor("#616161"))
            vue.background = GradientDrawable().apply {
                cornerRadius = 20f * resources.displayMetrics.density
                setColor(if (actif) teal else Color.WHITE)
                setStroke(
                    (1 * resources.displayMetrics.density).toInt(),
                    if (actif) teal else Color.parseColor("#D0D0D0")
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
                text = "Chaque mot verrouillé dans Wuertplaz devient une carte " +
                    "et vient s'ajouter ici. Terminez une grille pour ouvrir " +
                    "la collection."
                textSize = 15f
                gravity = Gravity.CENTER
                setLineSpacing(0f, 1.25f)
                setTextColor(Color.parseColor("#757575"))
                setPadding((24 * d).toInt(), 0, (24 * d).toInt(), 0)
            })
            return
        }

        val parRarete = contenus.groupingBy { it.rarete }.eachCount()
        tvResume.text = buildString {
            append("${contenus.size} mot")
            if (contenus.size > 1) append("s")
            append(" — ")
            append(
                Rarete.values().reversed()
                    .filter { (parRarete[it] ?: 0) > 0 }
                    .joinToString("   ") { "${it.symbole} ${parRarete[it]}" }
            )
        }

        val ordonnes = when (tri) {
            Tri.RECENT -> contenus.sortedByDescending { it.carte.numero }
            Tri.ALPHA -> contenus.sortedBy { it.carte.forme.lowercase() }
            Tri.RARETE -> contenus.sortedWith(
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
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = (10 * d).toInt() }
                }
                conteneurGrille.addView(ligne)
            }
            ligne?.addView(CarteWuert.vignette(ctx, c, cote).apply {
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
        val carte = CarteWuert.complete(ctx, c)
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

        carte.cameraDistance = 9000f * d
        carte.rotationY = -85f
        carte.animate().rotationY(0f).setDuration(360)
            .setInterpolator(DecelerateInterpolator()).start()
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
