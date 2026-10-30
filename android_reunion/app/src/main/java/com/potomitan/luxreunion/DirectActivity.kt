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
 * Pendant la réunion, en sous-titres : ce qui vient de se dire, en
 * luxembourgeois, en grand ; sa traduction en français en petit dessous ; sur
 * tablette, côte à côte. Seules les dernières lignes restent à l'écran, comme
 * la page LuxASR Live : en grand, chaque phrase avec sa traduction, affichées
 * ensemble dès que le service a traduit la phrase (il ne traduit qu'une phrase
 * complète) ; en bas, un bandeau « En direct » où ce qui se dit s'écrit mot à
 * mot, en attendant. « Voir tout le texte » donne le texte
 * complet, par paragraphes, à qui a raté une phrase. L'écran reste allumé,
 * et un seul bouton termine.
 *
 * L'écran ne fait que montrer : c'est le service d'écoute qui tient la
 * réunion. Quitter cet écran ne l'arrête pas.
 */
class DirectActivity : Activity() {

    private val reglages get() = ReunionApp.instance.reglages
    private lateinit var micro: MicroPulsant
    private lateinit var etat: TextView
    private lateinit var chrono: TextView
    private lateinit var liste: LinearLayout
    private lateinit var defilement: ScrollView
    private lateinit var vide: TextView
    private lateinit var retourDirect: TextView
    private lateinit var terminer: Button
    private val vues = mutableListOf<VueParagraphe>()
    private val donnees = mutableListOf<Paragraphe>()
    private var suivre = true
    /** Sous-titres (par défaut) ou texte complet. */
    private var sousTitres = true
    private lateinit var vueSousTitres: LinearLayout
    private lateinit var stLb: TextView
    private lateinit var stFr: TextView
    private lateinit var boiteLb: FrameLayout
    private lateinit var boiteFr: FrameLayout
    private lateinit var bascule: TextView
    /** La traduction affichée au dernier passage : ce qui s'y ajoute ressort. */
    private var entetes: View? = null
    private lateinit var stDirect: TextView
    private lateinit var boiteDirect: FrameLayout
    private var fin = false
    private val rafraichir = { maj() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val contenu = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        contenu.addView(bandeauEtat())
        if (large()) contenu.addView(entetesColonnes().also { entetes = it })

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
        vueSousTitres = sousTitresVue()
        bascule = Charte.pastille(this, "", Charte.BLEU, Charte.BLANC).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            elevation = dp(4f)
            setOnClickListener { basculer() }
        }
        val cadre = FrameLayout(this).apply {
            addView(defilement, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(vueSousTitres, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(vide, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
            addView(retourDirect, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                                                          Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(72) })
            addView(bascule, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                                                     Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(16) })
        }
        appliquerMode()
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
            setPadding(dp(8), dp(6), dp(8), dp(6))
            elevation = dp(2f)
        }
        micro = MicroPulsant(this)
        b.addView(micro)
        val textes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, 0, 0) }
        etat = Charte.texte(this, "Connexion…", 15f, Charte.ENCRE, gras = true)
        textes.addView(etat)
        chrono = Charte.texte(this, "0:00", 14f, Charte.GRIS)
        textes.addView(chrono)
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
            tailleSousTitres()
            majSousTitres(EtatDirect.paragraphes)
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
        etat.setTextColor(if (couleur == Charte.VERT) Charte.ENCRE else couleur)
        etat.text = texte
        chrono.text = Normaliseur.horloge(EtatDirect.secondes)
        // Le micro pulse tant que ça enregistre, réseau ou pas.
        micro.actif = !EtatDirect.microEnPanne && EtatDirect.etat != DirectLuxAsr.Etat.ARRET
        micro.niveau = EtatDirect.niveau

        val ps = EtatDirect.paragraphes
        vide.visibility = if (ps.isEmpty()) View.VISIBLE else View.GONE
        bascule.visibility = if (ps.isEmpty()) View.GONE else View.VISIBLE
        if (sousTitres) { majSousTitres(ps); return }
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

    // --- Sous-titres ------------------------------------------------------------

    private fun sousTitresVue(): LinearLayout {
        stLb = TextView(this).apply { setTextColor(Charte.ENCRE); setLineSpacing(0f, 1.12f) }
        stFr = TextView(this).apply { setTextColor(Charte.GRIS); setLineSpacing(0f, 1.12f) }
        stDirect = TextView(this).apply { setTextColor(Charte.GRIS); setLineSpacing(0f, 1.1f) }
        boiteLb = boite(stLb)
        boiteFr = boite(stFr)
        boiteDirect = boite(stDirect, Charte.BLANC)
        // La phrase et sa traduction, ensemble : côte à côte sur tablette, l'une sous l'autre sinon.
        val paire = LinearLayout(this).apply {
            if (large()) {
                orientation = LinearLayout.HORIZONTAL
                addView(boiteLb, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f))
                addView(boiteFr, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(28) })
            } else {
                orientation = LinearLayout.VERTICAL
                addView(boiteLb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(boiteFr, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(14) })
            }
        }
        // Le bandeau du direct : ce qui se dit en ce moment, mot à mot, pas encore traduit.
        val bandeau = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Charte.fond(this@DirectActivity, Charte.BLANC, 12, Charte.TRAIT)
            setPadding(dp(14), dp(8), dp(14), dp(10))
            addView(Charte.texte(this@DirectActivity, "●  EN DIRECT", 11f, Charte.ROUGE, gras = true).apply { letterSpacing = 0.08f })
            addView(boiteDirect, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(2) })
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(72))
            addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
            addView(paire, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
            addView(bandeau, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }.also { tailleSousTitres() }
    }

    /**
     * Une boîte de hauteur fixe (quelques lignes), le texte calé en bas : les
     * lignes les plus anciennes sortent par le haut, sous un dégradé.
     */
    private fun boite(t: TextView, fond: Int = Charte.FOND): FrameLayout = BoiteBas().apply {
        addView(t, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        addView(View(context).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(fond, fond and 0x00FFFFFF))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22), Gravity.TOP))
    }

    /**
     * Un FrameLayout mesure un enfant « wrap_content » sans dépasser sa propre
     * hauteur : le texte aurait gardé ses premières lignes et perdu les
     * dernières. Ici le texte se mesure en entier et se cale en bas.
     */
    private inner class BoiteBas : FrameLayout(this@DirectActivity) {
        override fun onMeasure(largeur: Int, hauteur: Int) {
            super.onMeasure(largeur, hauteur)
            val l = MeasureSpec.makeMeasureSpec(measuredWidth - paddingLeft - paddingRight, MeasureSpec.EXACTLY)
            getChildAt(0)?.measure(l, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        }

        override fun onLayout(change: Boolean, g: Int, h: Int, d: Int, b: Int) {
            super.onLayout(change, g, h, d, b)
            val t = getChildAt(0) ?: return
            val bas = b - h - paddingBottom
            t.layout(paddingLeft, bas - t.measuredHeight, paddingLeft + t.measuredWidth, bas)
        }
    }

    private fun tailleSousTitres() {
        val e = reglages.echelle
        val tablette = large()
        stLb.setTextSize(TypedValue.COMPLEX_UNIT_SP, (if (tablette) 28f else 25f) * e)
        stFr.setTextSize(TypedValue.COMPLEX_UNIT_SP, (if (tablette) 21f else 18f) * e)
        stFr.setTypeface(null, Typeface.ITALIC)
        stDirect.setTextSize(TypedValue.COMPLEX_UNIT_SP, (if (tablette) 18f else 16f) * e)
        (boiteLb.layoutParams ?: return).height = stLb.lineHeight * LIGNES_LB
        boiteFr.layoutParams.height = stFr.lineHeight * LIGNES_FR
        (boiteDirect.layoutParams ?: return).height = stDirect.lineHeight * LIGNES_DIRECT
        boiteLb.requestLayout(); boiteFr.requestLayout(); boiteDirect.requestLayout()
    }

    private fun majSousTitres(ps: List<Paragraphe>) {
        // En grand : les dernières phrases traduites, chacune avec sa traduction,
        // telles que le service les a associées. La plus récente en noir.
        val phrases = EtatDirect.phrases
        val lb = SpannableStringBuilder()
        val fr = SpannableStringBuilder()
        var taille = 0
        val gardees = phrases.asReversed().takeWhile { (t, _) -> (taille < MAX_CARS).also { taille += t.length + 1 } }.asReversed()
        gardees.forEachIndexed { k, (t, tr) ->
            val recente = k == gardees.size - 1
            ajouter(lb, t, if (recente) Charte.ENCRE else Charte.GRIS_CLAIR)
            ajouter(fr, tr, if (recente) Charte.ENCRE else Charte.GRIS)
        }
        stLb.text = lb
        stFr.text = fr

        // En bas : ce qui s'est dit depuis la dernière phrase traduite, puis la fin
        // encore provisoire, mot à mot.
        val confirme = ps.joinToString(" ") { it.texte }.trim()
        val partiel = ps.lastOrNull()?.partiel?.trim().orEmpty()
        val derniere = phrases.lastOrNull()?.first
        val reste = if (derniere == null) confirme else {
            val k = finDe(confirme, derniere)
            if (k >= 0) confirme.substring(k).trim() else ""
        }
        val direct = SpannableStringBuilder()
        ajouter(direct, fin(reste, MAX_CARS_DIRECT), Charte.GRIS)
        ajouter(direct, partiel, Charte.GRIS_CLAIR)
        if (direct.isEmpty() && !ps.isEmpty()) ajouter(direct, "…", Charte.GRIS_CLAIR)
        stDirect.text = direct
    }

    private fun ajouter(b: SpannableStringBuilder, t: String, couleur: Int) {
        if (t.isBlank()) return
        if (b.isNotEmpty()) b.append(' ')
        val d = b.length
        b.append(t)
        b.setSpan(ForegroundColorSpan(couleur), d, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /**
     * Où finit, dans le texte confirmé, la dernière phrase traduite : la phrase
     * entière, sinon sa fin (les espaces peuvent différer), sinon -1.
     */
    private fun finDe(texte: String, phrase: String): Int {
        var i = texte.lastIndexOf(phrase)
        if (i >= 0) return i + phrase.length
        val bout = phrase.takeLast(24)
        i = texte.lastIndexOf(bout)
        return if (i >= 0) i + bout.length else -1
    }

    /** Les derniers caractères d'un texte, coupés à un espace. */
    private fun fin(t: String, max: Int): String {
        if (t.length <= max) return t
        val coupe = t.indexOf(' ', t.length - max)
        return if (coupe < 0) t.takeLast(max) else t.substring(coupe + 1)
    }

    private fun basculer() {
        sousTitres = !sousTitres
        appliquerMode()
        maj()
        if (!sousTitres) { suivre = true; retourDirect.visibility = View.GONE; descendre() }
    }

    private fun appliquerMode() {
        vueSousTitres.visibility = if (sousTitres) View.VISIBLE else View.GONE
        defilement.visibility = if (sousTitres) View.GONE else View.VISIBLE
        entetes?.visibility = if (sousTitres) View.GONE else View.VISIBLE
        if (sousTitres) retourDirect.visibility = View.GONE
        bascule.text = if (sousTitres) "☰  Voir tout le texte" else "▭  Revenir aux sous-titres"
    }

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
        /** Les lignes gardées à l'écran en sous-titres. */
        private const val LIGNES_LB = 4
        private const val LIGNES_FR = 4
        private const val LIGNES_DIRECT = 2
        private const val MAX_CARS_DIRECT = 220
        /** Assez pour remplir la boîte, sans mettre en page tout le texte de la réunion. */
        private const val MAX_CARS = 420
    }
}
