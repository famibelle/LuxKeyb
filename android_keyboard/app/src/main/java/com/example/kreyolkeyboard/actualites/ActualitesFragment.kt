package com.example.kreyolkeyboard.actualites

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import com.example.kreyolkeyboard.R
import java.text.DateFormat
import java.util.Date

/**
 * La page des actualités de l'INLL : la liste des dernières publications de
 * son site, chacune ouvrant l'article complet dans le navigateur.
 *
 * Le cache s'affiche d'abord, le flux frais le remplace quand il arrive ; sans
 * réseau, le cache reste, avec une ligne qui le dit.
 */
class ActualitesFragment : Fragment() {

    private lateinit var liste: LinearLayout
    private lateinit var etat: TextView
    private lateinit var reessayer: Button
    private val principal = Handler(Looper.getMainLooper())
    @Volatile private var visible = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val colonne = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }

        colonne.addView(TextView(ctx).apply {
            text = getString(R.string.act_intro)
            textSize = 14f
            setTextColor(Color.parseColor("#555555"))
            setPadding(0, 0, 0, dp(12))
        })

        etat = TextView(ctx).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, 0, 0, dp(8))
        }
        colonne.addView(etat)

        reessayer = Button(ctx).apply {
            text = getString(R.string.act_reessayer)
            visibility = View.GONE
            setOnClickListener { charger() }
        }
        colonne.addView(reessayer)

        liste = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        colonne.addView(liste)

        colonne.addView(TextView(ctx).apply {
            text = getString(R.string.act_voir_site)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1565C0"))
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(8))
            setOnClickListener { ouvrir(FluxInll.site(langue())) }
        })
        colonne.addView(TextView(ctx).apply {
            text = getString(R.string.act_source)
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
        })

        return ScrollView(ctx).apply { addView(colonne) }
    }

    override fun onStart() {
        super.onStart()
        visible = true
        charger()
    }

    override fun onStop() {
        visible = false
        super.onStop()
    }

    private fun langue() = getString(R.string.langue_flux_inll)

    private fun charger() {
        val ctx = requireContext().applicationContext
        val langue = langue()
        val cache = FluxInll.enCache(ctx, langue)
        afficher(cache)
        reessayer.visibility = View.GONE
        etat.text = getString(R.string.act_chargement)
        etat.visibility = View.VISIBLE

        Thread {
            val frais = FluxInll.telecharger(ctx, langue)
            principal.post {
                if (!visible || !isAdded) return@post
                when {
                    frais != null -> {
                        afficher(frais)
                        etat.visibility = View.GONE
                    }
                    cache.isNotEmpty() -> etat.text = getString(R.string.act_hors_ligne)
                    else -> {
                        etat.text = getString(R.string.act_indisponible)
                        reessayer.visibility = View.VISIBLE
                    }
                }
            }
        }.start()
    }

    private fun afficher(actualites: List<Actualite>) {
        liste.removeAllViews()
        val format = DateFormat.getDateInstance(DateFormat.LONG, androidx.core.os.ConfigurationCompat.getLocales(resources.configuration)[0])
        for (a in actualites) liste.addView(carte(a, format))
    }

    private fun carte(a: Actualite, format: DateFormat): View {
        val ctx = requireContext()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(10).toFloat()
            }
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
            isClickable = true
            setOnClickListener { ouvrir(a.lien) }

            a.date?.let { d ->
                addView(TextView(ctx).apply {
                    text = format.format(Date(d))
                    textSize = 12f
                    setTextColor(Color.parseColor("#00A1DE"))
                    setTypeface(null, Typeface.BOLD)
                })
            }
            addView(TextView(ctx).apply {
                text = html(a.titre)
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#1C1C1C"))
                setPadding(0, dp(2), 0, 0)
            })
            if (a.resume.isNotEmpty()) addView(TextView(ctx).apply {
                text = html(a.resume)
                textSize = 14f
                setTextColor(Color.parseColor("#555555"))
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(4), 0, 0)
            })
        }
    }

    private fun html(s: String) = HtmlCompat.fromHtml(s, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()

    private fun ouvrir(adresse: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(adresse)))
        } catch (e: android.content.ActivityNotFoundException) {
            etat.text = adresse
            etat.visibility = View.VISIBLE
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
