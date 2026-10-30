package com.potomitan.luxreunion

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.potomitan.luxreunion.Charte.dp
import com.potomitan.luxreunion.Charte.large
import com.potomitan.luxreunion.noyau.DirectLuxAsr
import com.potomitan.luxreunion.noyau.Normaliseur
import com.potomitan.luxreunion.noyau.Paragraphe

/**
 * Pendant la réunion : ce qui se dit, en luxembourgeois, en grand ; sa
 * traduction en français en petit dessous ; sur tablette, côte à côte. L'écran reste allumé, le texte défile
 * seul tant qu'on ne remonte pas le lire, et un seul bouton termine.
 *
 * L'écran ne fait que montrer : c'est le service d'écoute qui tient la
 * réunion. Quitter cet écran ne l'arrête pas.
 */
class DirectActivity : Activity() {

    private val reglages get() = ReunionApp.instance.reglages
    private lateinit var point: TextView
    private lateinit var etat: TextView
    private lateinit var chrono: TextView
    private lateinit var niveau: View
    private lateinit var liste: LinearLayout
    private lateinit var defilement: ScrollView
    private lateinit var vide: TextView
    private lateinit var retourDirect: TextView
    private lateinit var terminer: Button
    private val vues = mutableListOf<VueParagraphe>()
    private val donnees = mutableListOf<Paragraphe>()
    private var suivre = true
    private var fin = false
    private val rafraichir = { maj() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val contenu = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        contenu.addView(bandeauEtat())
        if (large()) contenu.addView(entetesColonnes())

        liste = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(24))
        }
        defilement = ScrollView(this).apply {
            addView(liste)
            setOnScrollChangeListener { _, _, y, _, _ ->
                val bas = (liste.height - height - y) < dp(120)
                if (bas != suivre) {
                    suivre = bas
                    retourDirect.visibility = if (bas) View.GONE else View.VISIBLE
                }
            }
        }
        vide = Charte.texte(this, "Je vous écoute.\nLe texte s'affichera ici dès que quelqu'un parlera, avec sa traduction en dessous.", 18f, Charte.GRIS_CLAIR).apply {
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(48), dp(32), dp(48))
        }
        retourDirect = Charte.pastille(this, "↓  Revenir au direct", Charte.BLANC, Charte.BLEU).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            elevation = dp(6f)
            visibility = View.GONE
            setOnClickListener { suivre = true; visibility = View.GONE; descendre() }
        }
        val cadre = FrameLayout(this).apply {
            addView(defilement, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(vide, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
            addView(retourDirect, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                                                          Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(16) })
        }
        contenu.addView(cadre, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        terminer = Charte.boutonPrincipal(this, "■  Terminer la réunion", Charte.ROUGE) { confirmerFin() }
        val titre = EtatDirect.reunionId?.let { ReunionApp.instance.registre.lire(it)?.titre } ?: "Réunion"
        Charte.page(this, titre, contenu, bas = Charte.barreBasse(this, terminer))
    }

    private fun bandeauEtat(): View {
        val b = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Charte.BLANC)
            setPadding(dp(16), dp(10), dp(8), dp(10))
            elevation = dp(2f)
        }
        point = Charte.texte(this, "●", 18f, Charte.ORANGE)
        b.addView(point)
        val textes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, 0, 0) }
        etat = Charte.texte(this, "Connexion…", 15f, Charte.ENCRE, gras = true)
        textes.addView(etat)
        val ligne = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        chrono = Charte.texte(this, "0:00", 14f, Charte.GRIS)
        ligne.addView(chrono)
        val jauge = FrameLayout(this).apply { background = Charte.fond(this@DirectActivity, Charte.TRAIT, 3) }
        niveau = View(this).apply { background = Charte.fond(this@DirectActivity, Charte.VERT, 3) }
        jauge.addView(niveau, FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT))
        ligne.addView(jauge, LinearLayout.LayoutParams(dp(80), dp(6)).apply { leftMargin = dp(10) })
        textes.addView(ligne)
        b.addView(textes, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        b.addView(boutonTaille("A−", -1))
        b.addView(boutonTaille("A+", +1))
        return b
    }

    private fun boutonTaille(t: String, sens: Int) = Charte.texte(this, t, 17f, Charte.BLEU, gras = true).apply {
        gravity = Gravity.CENTER
        background = Charte.touchable(Charte.fond(this@DirectActivity, Charte.BLEU_PALE, 10), clair = false)
        contentDescription = if (sens > 0) "Agrandir le texte" else "Réduire le texte"
        setOnClickListener {
            val i = (ECHELLES.indexOfFirst { it >= reglages.echelle - 0.01f }.takeIf { it >= 0 } ?: 1) + sens
            reglages.echelle = ECHELLES[i.coerceIn(0, ECHELLES.size - 1)]
            vues.forEachIndexed { k, v -> v.lier(donnees[k], v.recent) }
        }
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(44)).apply { leftMargin = dp(6) }
    }

    private fun entetesColonnes(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(24), dp(12), dp(24), dp(4))
        addView(Charte.texte(this@DirectActivity, "LËTZEBUERGESCH", 13f, Charte.BLEU, gras = true).apply { letterSpacing = 0.08f },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f))
        addView(Charte.texte(this@DirectActivity, "FRANÇAIS", 13f, Charte.GRIS, gras = true).apply { letterSpacing = 0.08f },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(24) })
    }

    override fun onResume() {
        super.onResume()
        if (EtatDirect.reunionId == null) { finish(); return }
        Ui.abonner(rafraichir)
        maj()
    }

    override fun onPause() {
        super.onPause()
        Ui.desabonner(rafraichir)
    }

    private fun confirmerFin() {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("Terminer la réunion ?")
            .setMessage("L'enregistrement s'arrête. Je prépare ensuite la transcription complète et le compte rendu.")
            .setPositiveButton("Terminer") { _, _ ->
                terminer.isEnabled = false
                terminer.text = "Fin de la réunion…"
                ServiceEcoute.terminer(this)
            }
            .setNegativeButton("Continuer la réunion", null)
            .show()
    }

    private fun maj() {
        val id = EtatDirect.reunionId
        if (EtatDirect.termine && id != null && !fin) {
            fin = true
            startActivity(Intent(this, ReunionActivity::class.java).putExtra(ReunionActivity.EXTRA_ID, id))
            finish()
            return
        }
        val (couleur, texte) = when {
            EtatDirect.microEnPanne -> Charte.ROUGE to "Le micro ne répond pas"
            EtatDirect.etat == DirectLuxAsr.Etat.HORS_LIGNE -> Charte.ROUGE to "Pas de réseau · j'enregistre quand même"
            EtatDirect.etat == DirectLuxAsr.Etat.CONNEXION -> Charte.ORANGE to "Connexion à LuxASR…"
            EtatDirect.etat == DirectLuxAsr.Etat.ARRET -> Charte.GRIS to "Fin de la réunion…"
            EtatDirect.retard > 0 -> Charte.ORANGE to "Réseau lent · la traduction prend du retard"
            else -> Charte.VERT to "J'écoute et j'écris"
        }
        point.setTextColor(couleur)
        etat.text = texte
        chrono.text = Normaliseur.horloge(EtatDirect.secondes)
        (niveau.layoutParams as FrameLayout.LayoutParams).width = (dp(80) * EtatDirect.niveau).toInt()
        niveau.requestLayout()

        val ps = EtatDirect.paragraphes
        vide.visibility = if (ps.isEmpty()) View.VISIBLE else View.GONE
        while (vues.size > ps.size) { liste.removeView(vues.removeAt(vues.size - 1)); donnees.removeAt(donnees.size - 1) }
        var change = false
        ps.forEachIndexed { i, p ->
            // Une traduction arrive jusqu'à deux secondes après son texte : les
            // trois derniers paragraphes l'attendent encore (« … »).
            val recent = i >= ps.size - 3
            if (i >= vues.size) {
                val v = VueParagraphe()
                vues += v
                donnees += p
                liste.addView(v)
                v.lier(p, recent)
                change = true
            } else if (donnees[i] != p || vues[i].recent != recent) {
                donnees[i] = p
                vues[i].lier(p, recent)
                change = true
            }
        }
        if (change && suivre) descendre()
    }

    private fun descendre() = defilement.post { defilement.smoothScrollTo(0, liste.height) }

    /** Un paragraphe : l'heure, le luxembourgeois, puis sa traduction en français. */
    private inner class VueParagraphe : LinearLayout(this@DirectActivity) {
        private val heure = Charte.texte(context, "", 13f, Charte.GRIS_CLAIR)
        private val lacune = Charte.texte(context, "⋯  Coupure de réseau : un passage manque ici. Il sera dans le compte rendu.",
                                          13f, Charte.ORANGE)
        private val fr = TextView(context)
        private val lb = TextView(context)

        init {
            orientation = VERTICAL
            addView(lacune)
            addView(heure)
            lb.setTextColor(Charte.ENCRE)
            lb.setLineSpacing(0f, 1.15f)
            fr.setTextColor(Charte.GRIS)
            fr.setLineSpacing(0f, 1.15f)
            if (large()) {
                // Tablette : deux colonnes, l'original à gauche (la plus large), la traduction à droite.
                val rangee = LinearLayout(context).apply { orientation = HORIZONTAL }
                rangee.addView(lb, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f))
                rangee.addView(fr, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(24) })
                addView(rangee)
            } else {
                addView(lb)
                addView(fr, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
            }
            background = android.graphics.drawable.InsetDrawable(
                android.graphics.drawable.GradientDrawable().apply {
                    setColor(Charte.BLANC); setStroke(dp(1), Charte.TRAIT)
                }, -dp(2), -dp(2), -dp(2), 0)
            // Après le fond : un fond remplace la marge intérieure par la sienne
            // (ici négative), et le texte se retrouvait collé au bord de l'écran.
            setPadding(dp(20), dp(14), dp(20), dp(14))
        }

        var recent = true
            private set

        fun lier(p: Paragraphe, recent: Boolean) {
            this.recent = recent
            val e = reglages.echelle
            val tablette = large()
            lacune.visibility = if (p.lacune) View.VISIBLE else View.GONE
            heure.text = Normaliseur.horloge(p.debut)
            lb.setTextSize(TypedValue.COMPLEX_UNIT_SP, (if (tablette) 23f else 21f) * e)
            fr.setTextSize(TypedValue.COMPLEX_UNIT_SP, (if (tablette) 18f else 15f) * e)
            fr.setTypeface(null, if (tablette) Typeface.NORMAL else Typeface.ITALIC)
            val traduction = p.traduction
            if (traduction.isNullOrBlank()) {
                fr.text = if (recent) "…" else "(pas de traduction pour ce passage)"
                fr.setTextColor(Charte.GRIS_CLAIR)
            } else {
                fr.text = traduction
                fr.setTextColor(Charte.GRIS)
            }
            val t = SpannableStringBuilder(p.texte)
            val partiel = p.partiel
            if (!partiel.isNullOrBlank()) {
                val debut = t.length
                t.append(if (t.isEmpty()) partiel else " $partiel")
                t.setSpan(ForegroundColorSpan(Charte.GRIS_CLAIR), debut, t.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            lb.text = t
        }
    }

    companion object {
        private val ECHELLES = floatArrayOf(0.85f, 1f, 1.2f, 1.4f, 1.7f, 2f)
    }
}
