package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.kreyolkeyboard.TranslationDictionary

/**
 * La boîte de Leitner comme jeu à part entière dans Spiller.
 *
 * Un [Fragment] qui affiche [BoiteLeitner] et gère les interactions : taper
 * un casier affiche la liste des cartes dedans, taper la plaque de laiton
 * lance une session de révision si des cartes sont dues.
 */
class BoiteFragment : Fragment() {

    private lateinit var racine: FrameLayout
    private lateinit var boite: BoiteLeitner
    private var panneauCasier: View? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val d = resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()

        racine = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val colonne = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Boîte de Leitner
        boite = BoiteLeitner(ctx).apply {
            isClickable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply { setMargins(dp(24f), 0, dp(24f), 0) }
            surCasier = { boiteNo -> montrerCasier(boiteNo) }
            surRevision = { lancerRevision() }
            surCarte = { carte -> lancerRevisionCarte(carte) }
        }
        colonne.addView(boite)

        racine.addView(colonne)

        return racine
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chargerEnFond()
    }

    private fun chargerEnFond() {
        val ctx = requireContext().applicationContext
        Thread {
            val cartes = Carnet.cartes(ctx)
            val contenus = cartes.map { CarteCarnet.contenu(ctx, it) }
            val aujourd = Widderhuelen.aujourdHui()
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                boite.poser(contenus.map { it.carte }, aujourd)
            }
        }.start()
    }

    private fun montrerCasier(boiteNo: Int) {
        val ctx = context ?: return
        val d = resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()
        panneauCasier?.let {
            it.animate().alpha(0f).setDuration(120)
                .withEndAction { racine.removeView(it) }.start()
        }
        panneauCasier = null

        val aujourdHui = Widderhuelen.aujourdHui()
        val file = Carnet.file(ctx)
        val contenus = file.map { CarteCarnet.contenu(ctx, it) }
        val dedans = contenus
            .filter { it.carte.boite.coerceIn(0, Widderhuelen.BOITE_ACQUISE) == boiteNo }
            .sortedBy { it.carte.forme.lowercase() }
        val dues = dedans.count {
            Widderhuelen.estDue(it.carte.boite, it.carte.jourEcheance, aujourdHui)
        }

        val colonne = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), 0, dp(12f), dp(24f))
            clipToPadding = false
            clipChildren = false
        }
        colonne.addView(etiquetteCasier(ctx, boiteNo, dedans.size, dues))

        if (dedans.isEmpty()) {
            colonne.addView(TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp(12f), dp(28f), dp(12f), 0) }
                text = if (boiteNo >= Widderhuelen.BOITE_ACQUISE)
                    "Aucune carte n'est encore acquise. Une carte arrive ici " +
                        "après six révisions réussies, la dernière à trois mois " +
                        "d'intervalle : c'est le bout du chemin, pas une étape."
                else "Ce casier est vide pour le moment. Les cartes y montent " +
                    "depuis le casier précédent, une révision réussie à la fois."
                textSize = 15f
                gravity = Gravity.CENTER
                setLineSpacing(0f, 1.25f)
                setTextColor(Color.parseColor("#757575"))
            })
        } else {
            val dispo = resources.displayMetrics.widthPixels - dp(24f) * 2
            emettreVignettes(ctx, dedans, (dispo - dp(10f)) / 2, colonne)
        }

        val panneau = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.WHITE)
                setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
                isClickable = true
                addView(TextView(ctx).apply {
                    text = "‹  La boîte"
                    textSize = 16f
                    setTextColor(Carnet.COULEUR)
                })
                setOnClickListener { panneauCasier?.let { p ->
                    panneauCasier = null
                    p.animate().alpha(0f).setDuration(120)
                        .withEndAction { racine.removeView(p) }.start()
                } }
            })
            addView(ScrollView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
                clipToPadding = false
                clipChildren = false
                addView(colonne)
            })
        }

        panneauCasier = panneau
        racine.addView(panneau)
        panneau.alpha = 0f
        panneau.animate().alpha(1f).setDuration(140).start()
    }

    private fun lancerRevision() {
        val ctx = requireContext().applicationContext
        val principal = Handler(Looper.getMainLooper())
        boite.isEnabled = false
        Thread {
            TranslationDictionary.charger(ctx)
            TranslationDictionary.chargerExemples(ctx)
            Carnet.planifier(ctx)
            val file = Carnet.file(ctx)
            val ecrites = PreuveDeFrappe.ecritesDepuisLaDerniereFois(ctx, file.map { it.forme })
            ecrites.forEach { Carnet.noter(ctx, it, Verdict.EXACT) }
            val aDemander = file.filter { it.forme !in ecrites }
                .map { CarteCarnet.contenu(ctx, it) }
            principal.post {
                if (!isAdded) return@post
                boite.isEnabled = true
                VueWidderhuelen(
                    hote = racine,
                    paquet = aDemander,
                    monteesParLeClavier = ecrites.toList(),
                    surNotation = { forme, verdict -> Carnet.noter(ctx, forme, verdict) },
                    surFin = { if (isAdded) chargerEnFond() }
                ).ouvrir()
            }
        }.start()
    }

    private fun lancerRevisionCarte(carte: CarteMot) {
        val ctx = requireContext().applicationContext
        val principal = Handler(Looper.getMainLooper())
        boite.isEnabled = false
        Thread {
            TranslationDictionary.charger(ctx)
            TranslationDictionary.chargerExemples(ctx)
            val contenu = CarteCarnet.contenu(ctx, carte)
            principal.post {
                if (!isAdded) return@post
                boite.isEnabled = true
                VueWidderhuelen(
                    hote = racine,
                    paquet = listOf(contenu),
                    monteesParLeClavier = emptyList(),
                    surNotation = { forme, verdict -> Carnet.noter(ctx, forme, verdict) },
                    surFin = { if (isAdded) chargerEnFond() }
                ).ouvrir()
            }
        }.start()
    }

    private fun emettreVignettes(
        ctx: Context,
        liste: List<ContenuCarte>,
        cote: Int,
        hote: LinearLayout
    ) {
        val d = resources.displayMetrics.density
        var ligne: LinearLayout? = null
        liste.forEachIndexed { i, c ->
            if (i % 2 == 0) {
                ligne = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    clipChildren = false
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = (10 * d).toInt() }
                }
                hote.addView(ligne)
            }
            ligne?.addView(CarteCarnet.vignette(ctx, c, cote).apply {
                layoutParams = LinearLayout.LayoutParams(
                    cote, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { if (i % 2 == 0) rightMargin = (10 * d).toInt() }
                isClickable = true
                setOnClickListener { ouvrirCarte(c) }
            })
        }
    }

    private var voileCarte: View? = null

    private fun ouvrirCarte(contenu: ContenuCarte) {
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
        val carte = CarteCarnet.complete(ctx, contenu)
        defilement.addView(carte)
        voile.addView(defilement)

        voile.setOnClickListener { fermerCarte() }
        carte.isClickable = true

        voileCarte = voile
        racine.addView(voile)
        voile.alpha = 0f
        voile.animate().alpha(1f).setDuration(160).start()

        carte.cameraDistance = 9000f * d
        carte.rotationY = -85f
        carte.animate().rotationY(0f)
            .setDuration(if (contenu.rarete.distinguee) 470L else 360L)
            .setInterpolator(
                if (contenu.rarete.distinguee) OvershootInterpolator(1.4f)
                else DecelerateInterpolator()
            )
            .start()
    }

    private fun fermerCarte() {
        val voile = voileCarte ?: return
        voileCarte = null
        voile.animate().alpha(0f).setDuration(160)
            .withEndAction { racine.removeView(voile) }.start()
    }
}
