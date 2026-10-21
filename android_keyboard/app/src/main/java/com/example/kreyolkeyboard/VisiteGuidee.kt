package com.example.kreyolkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * La visite guidée : un voile sombre sur l'écran, percé autour de ce qu'on
 * présente, et une bulle de deux phrases avec « Suivant » et « Passer ».
 *
 * Reprise de KreyolKeyb 23.3.0 (2026-10-21), où le propriétaire l'a demandée
 * parce que sa mère ne comprenait pas toutes les fonctions de l'application.
 * Il y en a deux, montées par leurs hôtes : celle de l'application (les
 * onglets, les réglages, les trois gestes du clavier et la dictée), dans
 * [SettingsActivity], et celle du carnet (la carte et ses zones, le partage,
 * l'échange), dans `CarnetFragment`.
 *
 * Aucune bibliothèque : une vue posée par-dessus l'hôte, qui dessine le voile
 * et sa découpe. La cible est relue à chaque image, en coordonnées de fenêtre,
 * si bien que la découpe suit une carte qui s'incline ou un onglet du rail
 * d'une tablette sans rien savoir de la mise en page. Les textes ne nomment en
 * luxembourgeois que des noms de l'application, jamais une phrase composée ici.
 */
object VisiteGuidee {

    /** Le fichier de préférences où les deux visites se notent faites. */
    const val PREFS = "lux_onboarding_prefs"
    const val VISITE_APPLICATION = "visite_application_faite"
    const val VISITE_CARNET = "visite_carnet_faite"

    /**
     * L'application se présente-t-elle d'elle-même ?
     *
     * Une fois, et seulement sur l'accueil du jour : tant que le clavier n'est
     * pas installé, c'est l'installation qui passe en premier. Jamais par-dessus
     * la pochette de bienvenue ni un écran ouvert devant l'accueil.
     */
    fun doitSeLancer(dejaFaite: Boolean, accueilDuJour: Boolean, autreChoseOuvert: Boolean): Boolean =
        !dejaFaite && accueilDuJour && !autreChoseOuvert

    /** Le carnet se présente une fois, dès qu'il porte une carte à montrer. */
    fun carnetDoitSeLancer(dejaFaite: Boolean, cartes: Int, carteDemandee: Boolean): Boolean =
        !dejaFaite && cartes > 0 && !carteDemandee

    /**
     * Une zone de la carte, donnée en unités de carte (300 de large), ramenée
     * aux pixels de l'écran d'après la position et la largeur de la carte.
     */
    fun zoneDeCarte(zone: RectF, gauche: Float, haut: Float, largeurPx: Float, largeurUnites: Float): RectF {
        val u = largeurPx / largeurUnites
        return RectF(gauche + zone.left * u, haut + zone.top * u, gauche + zone.right * u, haut + zone.bottom * u)
    }

    /** Le plus petit rectangle qui contient toutes les zones. */
    fun reunion(vararg zones: RectF): RectF = RectF(zones.first()).apply { zones.drop(1).forEach { union(it) } }

    /**
     * L'avancée dans les étapes, sans Android : [VisiteGuideeTest]. L'hôte ne
     * donne que les étapes qu'il peut montrer (la ligne des réglages n'existe
     * que sur l'accueil du jour), si bien que le compteur ne ment jamais.
     */
    class Deroule(private val nombre: Int, depart: Int = 0) {
        var index = depart.coerceIn(0, (nombre - 1).coerceAtLeast(0))
            private set

        val derniere: Boolean get() = index >= nombre - 1

        /** Passe à l'étape suivante ; `false` quand il n'y en a plus. */
        fun suivante(): Boolean {
            if (derniere) return false
            index++
            return true
        }

        /** Saute à l'étape [rang], bornée : « Passer » mène au rappel de la fin. */
        fun allerA(rang: Int) {
            index = rang.coerceIn(0, (nombre - 1).coerceAtLeast(0))
        }
    }

    /**
     * Ce que fait « Passer » : fermer, ou d'abord sauter au [rappel] qui dit
     * où retrouver la visite. Passée sans ce rappel, elle était perdue pour
     * qui ne savait pas où la chercher.
     */
    fun passerVers(index: Int, rappel: Int?): Int? = rappel?.takeIf { index < it }

    /** « Passer » n'a de sens que s'il reste quelque chose à passer. */
    fun passerVisible(nombre: Int, index: Int, rappel: Int?): Boolean = nombre > 1 && index != rappel

    /** Laquelle des deux visites porte un sujet. */
    enum class Visite { APPLICATION, CARNET }

    /**
     * Un sujet du sommaire « Visite guidée » : quelqu'un qui a passé la visite
     * revient plus tard sur un seul point, la dictée ou le partage d'une
     * carte, sans refaire tout le parcours. [etapes] sont les rangs, dans sa
     * visite, des étapes que le sujet rejoue : l'ordre de ces listes est celui
     * de [SettingsActivity.lancerVisite] (8 étapes, plus le rappel de la
     * fin) et de `CarnetFragment.proposerVisite` (6 étapes), [VisiteGuideeTest] le vérifie.
     * [groupe] range le sujet sous un intertitre du sommaire.
     */
    enum class Sujet(val visite: Visite, val etapes: IntRange, val groupe: Int, val emoji: String) {
        // L'ordre du sommaire, choisi par le propriétaire (2026-10-21) : le
        // clavier, les cartes, le dictionnaire, les jeux, le reste, et les
        // réglages du clavier tout en bas.
        GESTES(Visite.APPLICATION, 0..0, 0, "✋"),
        DICTEE(Visite.APPLICATION, 1..1, 0, "🎤"),
        LIRE_UNE_CARTE(Visite.CARNET, 0..0, 1, "🃏"),
        PARTAGER(Visite.CARNET, 1..1, 1, "📤"),
        DONNER_OU_RECEVOIR(Visite.CARNET, 2..5, 1, "🤝"),
        DICTIONNAIRE(Visite.APPLICATION, 3..3, 2, "📚"),
        JEUX(Visite.APPLICATION, 4..4, 2, "🎮"),
        ACCUEIL(Visite.APPLICATION, 5..5, 2, "🏠"),
        PROGRES(Visite.APPLICATION, 6..6, 2, "📊"),
        REGLAGES(Visite.APPLICATION, 7..7, 2, "⚙️");
    }

    const val ETAPES_APPLICATION = 8
    const val ETAPES_CARNET = 6

    /**
     * Où poser la bulle autour d'une cible qui va de [haut] à [bas] : sous
     * elle (`true`, retrait depuis le haut) ou au-dessus (`false`, retrait
     * depuis le bas). Le côté le plus naturel d'abord (sous une cible du haut
     * de l'écran), l'autre s'il est seul à la contenir ; si aucun ne la
     * contient, en bas de l'écran par-dessus la cible. Vu sur le A21s : la
     * carte entière éclairée ne laissait que 140 dp dessous, et le bouton
     * « Suivant » sortait de l'écran.
     */
    fun placement(haut: Float, bas: Float, ecran: Int, bulle: Int, marge: Int, basEcran: Int): Pair<Boolean, Int> {
        val tientDessous = bas + marge + bulle + basEcran <= ecran
        val tientDessus = haut - marge - bulle >= marge
        val dessousDabord = (haut + bas) / 2f < ecran / 2f
        return when {
            dessousDabord && tientDessous -> true to (bas + marge).toInt()
            !dessousDabord && tientDessus -> false to (ecran - haut + marge).toInt()
            tientDessous -> true to (bas + marge).toInt()
            tientDessus -> false to (ecran - haut + marge).toInt()
            else -> false to basEcran
        }
    }

    /** « 2 / 6 ». */
    fun compteur(rang: Int, montrees: Int): String = "${rang + 1} / $montrees"

    /**
     * Une étape. [preparer] amène l'écran où il faut (un onglet, une carte
     * ouverte) puis appelle sa suite ; [cible] rend la zone à éclairer en
     * coordonnées de fenêtre, ou `null` pour une bulle seule au centre.
     * [autres] ajoute des découpes séparées : la plaque du mot et le chiffre
     * du coin, réunis en un seul rectangle, éclairaient aussi tout le paysage.
     * [icone] se pose devant le titre quand ce qu'on présente n'est pas dans
     * l'application : le micro de la dictée vit dans le clavier.
     * [illustration] fabrique une vue posée entre le titre et le texte :
     * l'échange d'une carte entre deux téléphones, mimé (37.7.7). Placée en
     * dernier, pour la même raison qu'[essai] et [icone] avant [autres].
     */
    class Etape(
        val titre: String,
        val texte: String,
        val preparer: (suite: () -> Unit) -> Unit = { it() },
        val cible: () -> RectF? = { null },
        val icone: Int? = null,
        /** Ouvre de quoi essayer ce que la bulle décrit : le clavier, qui vit hors de l'application. */
        val essai: (() -> Unit)? = null,
        val autres: () -> List<RectF> = { emptyList() },
        val illustration: ((android.content.Context) -> View)? = null
    )

    /** Le rectangle d'une vue en coordonnées de fenêtre, ou `null` si elle n'est pas à l'écran. */
    fun rectDe(vue: View?): RectF? {
        if (vue == null || !vue.isShown || vue.width == 0) return null
        val p = IntArray(2)
        vue.getLocationInWindow(p)
        return RectF(p[0].toFloat(), p[1].toFloat(), (p[0] + vue.width).toFloat(), (p[1] + vue.height).toFloat())
    }

    /** La première vue portant [etiquette] qui soit vraiment à l'écran (le pager en garde d'autres hors champ). */
    fun vueVisible(racine: View, etiquette: String): View? =
        vues(racine, etiquette).firstOrNull { it.getGlobalVisibleRect(android.graphics.Rect()) }

    /**
     * La vue portant [etiquette] dans la page à l'écran, même sous le pli :
     * celle qu'on fait défiler jusqu'à la vue avant de l'éclairer. Les pages
     * voisines du pager sont à côté de l'écran, pas dessous, d'où le seul
     * contrôle horizontal.
     */
    fun vueDeLaPage(racine: View, etiquette: String): View? {
        val p = IntArray(2)
        return vues(racine, etiquette).firstOrNull {
            it.getLocationOnScreen(p)
            val milieu = p[0] + it.width / 2
            milieu in 0 until racine.resources.displayMetrics.widthPixels
        }
    }

    private fun vues(racine: View, etiquette: String): List<View> {
        val trouvees = ArrayList<View>()
        fun parcourir(v: View) {
            if (v.tag == etiquette && v.isShown) trouvees.add(v)
            if (v is ViewGroup) for (i in 0 until v.childCount) parcourir(v.getChildAt(i))
        }
        parcourir(racine)
        return trouvees
    }

    /**
     * Attend que [pret] soit vrai, au plus [delaiMs], puis appelle [puis] :
     * le temps qu'un onglet glisse ou qu'une carte se retourne.
     */
    fun attendre(pret: () -> Boolean, delaiMs: Long = 2500L, puis: () -> Unit) {
        val main = Handler(Looper.getMainLooper())
        val debut = System.currentTimeMillis()
        fun essayer() {
            if (pret() || System.currentTimeMillis() - debut > delaiMs) puis()
            else main.postDelayed({ essayer() }, 80L)
        }
        main.postDelayed({ essayer() }, 80L)
    }

    /**
     * Le voile lui-même, posé sur [hote]. [surFin] reçoit `true` quand la
     * visite est allée au bout, `false` quand elle a été passée.
     */
    @SuppressLint("ViewConstructor")
    class Voile(
        private val hote: FrameLayout,
        private val etapes: List<Etape>,
        depart: Int = 0,
        /** Le rang de l'étape qui dit où retrouver la visite, où « Passer » mène d'abord. */
        private val rappel: Int? = null,
        private val surEtape: (Int) -> Unit = {},
        private val surFin: (terminee: Boolean) -> Unit
    ) : FrameLayout(hote.context) {

        private val d = resources.displayMetrics.density
        private val deroule = Deroule(etapes.size, depart)
        private val voile = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB3000000.toInt() }
        private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f * d
            color = Color.parseColor("#FFC94D")
        }
        private val chemin = Path().apply { fillType = Path.FillType.EVEN_ODD }
        /** Toutes les découpes de l'étape, et leur réunion, qui place la bulle. */
        private var trous: List<RectF> = emptyList()
        private var trou: RectF? = null
        private val ici = IntArray(2)
        private var attenteFinie = true
        private var fermee = false

        private val compteur: TextView
        private val titre: TextView
        private val texte: TextView
        private val suivant: TextView
        private val boutonPasser: TextView
        private val boutonEssayer: TextView
        private val bulle: LinearLayout
        private val cadreIllustration: FrameLayout

        private val suivi = ViewTreeObserver.OnPreDrawListener {
            recalculerTrou(repositionner = false)
            true
        }

        init {
            setWillNotDraw(false)
            isClickable = true
            isFocusable = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            elevation = 40f * d

            fun dp(v: Float) = (v * d).toInt()
            bulle = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20f), dp(16f), dp(20f), dp(14f))
                background = GradientDrawable().apply {
                    cornerRadius = 18f * d
                    setColor(Color.WHITE)
                }
                elevation = 6f * d
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                isClickable = true
            }
            compteur = TextView(context).apply {
                textSize = 12f
                setTextColor(Color.parseColor("#6B6B6B"))
            }
            titre = TextView(context).apply {
                textSize = 19f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#1C1C1C"))
                setPadding(0, dp(4f), 0, dp(6f))
            }
            texte = TextView(context).apply {
                textSize = 16f
                setTextColor(Color.parseColor("#333333"))
                setLineSpacing(0f, 1.2f)
            }
            boutonPasser = TextView(context).apply {
                text = context.getString(R.string.visite_passer)
                textSize = 15f
                setTextColor(Color.parseColor("#6B6B6B"))
                setPadding(dp(4f), dp(12f), dp(16f), dp(12f))
                isClickable = true
                isFocusable = true
                setOnClickListener { passer() }
            }
            suivant = TextView(context).apply {
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(22f), dp(12f), dp(22f), dp(12f))
                minimumHeight = dp(48f)
                background = GradientDrawable().apply {
                    cornerRadius = 24f * d
                    setColor(Color.parseColor("#0080FF"))
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { avancer() }
            }
            boutonEssayer = TextView(context).apply {
                text = context.getString(R.string.visite_essayer)
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#0080FF"))
                gravity = Gravity.CENTER
                setPadding(dp(18f), dp(10f), dp(18f), dp(10f))
                minimumHeight = dp(48f)
                background = GradientDrawable().apply {
                    cornerRadius = 24f * d
                    setStroke((2 * d).toInt(), Color.parseColor("#0080FF"))
                }
                isClickable = true
                isFocusable = true
                visibility = View.GONE
                setOnClickListener { etapes[deroule.index].essai?.invoke() }
            }
            cadreIllustration = FrameLayout(context).apply {
                setPadding(0, dp(2f), 0, dp(8f))
                visibility = View.GONE
            }
            bulle.addView(compteur)
            bulle.addView(titre)
            bulle.addView(cadreIllustration)
            bulle.addView(texte)
            bulle.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(14f), 0, 0)
                addView(boutonPasser)
                addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
                addView(boutonEssayer, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(10f) })
                addView(suivant)
            })
            addView(bulle)
        }

        /** Pose le voile et montre la première étape. */
        fun ouvrir() {
            hote.addView(this)
            viewTreeObserver.addOnPreDrawListener(suivi)
            alpha = 0f
            animate().alpha(1f).setDuration(200L).start()
            montrer()
        }

        /** Le bouton Retour : la visite est passée, comme avec « Passer ». */
        fun passer() {
            if (!attenteFinie || fermee) return finir(terminee = false)
            val vers = passerVers(deroule.index, rappel) ?: return finir(terminee = false)
            deroule.allerA(vers)
            montrer()
        }

        val etapeCourante: Int get() = deroule.index

        private fun avancer() {
            if (!attenteFinie) return
            if (deroule.suivante()) montrer() else finir(terminee = true)
        }

        private fun montrer() {
            val e = etapes[deroule.index]
            surEtape(deroule.index)
            attenteFinie = false
            bulle.animate().alpha(0f).setDuration(120L).start()
            e.preparer {
                if (fermee) return@preparer
                attenteFinie = true
                // Un sujet d'une seule étape n'a rien à compter : « 1 / 1 » lirait comme une erreur.
                compteur.text = compteur(deroule.index, etapes.size)
                compteur.visibility = if (etapes.size > 1) View.VISIBLE else View.GONE
                titre.text = e.titre
                val icone = e.icone?.let { androidx.core.content.ContextCompat.getDrawable(context, it) }?.mutate()?.apply {
                    val cote = (28f * d).toInt()
                    setBounds(0, 0, cote, cote)
                    setTint(Color.parseColor("#ED2939"))
                }
                titre.setCompoundDrawables(icone, null, null, null)
                titre.compoundDrawablePadding = (10f * d).toInt()
                texte.text = e.texte
                cadreIllustration.removeAllViews()
                val illustration = e.illustration?.invoke(context)
                if (illustration != null) cadreIllustration.addView(illustration)
                cadreIllustration.visibility = if (illustration != null) View.VISIBLE else View.GONE
                boutonEssayer.visibility = if (e.essai != null) View.VISIBLE else View.GONE
                boutonPasser.visibility =
                    if (passerVisible(etapes.size, deroule.index, rappel)) View.VISIBLE else View.INVISIBLE
                suivant.text = context.getString(
                    if (deroule.derniere) R.string.visite_terminer else R.string.visite_suivant
                )
                recalculerTrou(repositionner = true)
                bulle.animate().alpha(1f).setDuration(180L).start()
                bulle.announceForAccessibility("${e.titre}. ${e.texte}")
                suivant.requestFocus()
            }
        }

        private fun recalculerTrou(repositionner: Boolean) {
            if (!attenteFinie || fermee) return
            val e = etapes[deroule.index]
            getLocationInWindow(ici)
            val m = 6f * d
            val nouveaux = (listOfNotNull(e.cible()) + e.autres()).map {
                RectF(it.left - ici[0] - m, it.top - ici[1] - m, it.right - ici[0] + m, it.bottom - ici[1] + m)
            }
            val bouge = nouveaux.size != trous.size || nouveaux.zip(trous).any { (a, b) ->
                abs(a.left - b.left) > 1f || abs(a.top - b.top) > 1f ||
                    abs(a.right - b.right) > 1f || abs(a.bottom - b.bottom) > 1f
            }
            if (bouge) {
                trous = nouveaux
                trou = if (nouveaux.isEmpty()) null else reunion(*nouveaux.toTypedArray())
                invalidate()
            }
            if (repositionner) placerBulle()
        }

        /** Sous la cible si elle est en haut, au-dessus sinon, au centre sans cible. */
        private fun placerBulle() {
            // Une étape sans préparation se montre avant la première mise en
            // page du voile : sans sa hauteur, la bulle partait hors de l'écran.
            if (height == 0) {
                post { recalculerTrou(repositionner = true) }
                return
            }
            val marge = (16 * d).toInt()
            val lp = bulle.layoutParams as LayoutParams
            val largeurMax = (420 * d).toInt()
            lp.width = if (width - 2 * marge > largeurMax) largeurMax else LayoutParams.MATCH_PARENT
            lp.setMargins(marge, 0, marge, 0)
            val t = trou
            if (t == null) {
                lp.gravity = Gravity.CENTER
                lp.topMargin = 0
                lp.bottomMargin = 0
            } else {
                val largeur = if (lp.width == LayoutParams.MATCH_PARENT) width - 2 * marge else lp.width
                bulle.measure(
                    MeasureSpec.makeMeasureSpec(largeur, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                )
                val basEcran = marge + (androidx.core.view.ViewCompat.getRootWindowInsets(this)
                    ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())?.bottom ?: 0)
                val (dessous, retrait) = placement(t.top, t.bottom, height, bulle.measuredHeight, marge, basEcran)
                lp.gravity = (if (dessous) Gravity.TOP else Gravity.BOTTOM) or Gravity.CENTER_HORIZONTAL
                lp.topMargin = if (dessous) retrait else 0
                lp.bottomMargin = if (dessous) 0 else retrait
            }
            bulle.layoutParams = lp
        }

        override fun onDraw(canvas: Canvas) {
            chemin.reset()
            chemin.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            val r = 14f * d
            trous.forEach { chemin.addRoundRect(it, r, r, Path.Direction.CW) }
            canvas.drawPath(chemin, voile)
            trous.forEach { canvas.drawRoundRect(it, r, r, halo) }
        }

        private fun finir(terminee: Boolean) {
            if (fermee) return
            fermee = true
            viewTreeObserver.removeOnPreDrawListener(suivi)
            animate().alpha(0f).setDuration(160L).withEndAction {
                (parent as? ViewGroup)?.removeView(this)
            }.start()
            surFin(terminee)
        }
    }
}
