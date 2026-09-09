package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.example.kreyolkeyboard.TranslationDictionary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tout ce qu'une carte affiche, rassemblé une fois.
 *
 * Le mot montré est **celui que le joueur a rencontré**, pas le représentant
 * de sa famille : il a posé « gefrote », c'est « gefrote » qu'il a gagné.
 * La glose et la phrase d'exemple, elles, viennent de la fiche du représentant
 * — c'est là que le dictionnaire les range, et c'est le bon sens pour toute la
 * famille.
 */
data class ContenuCarte(
    val carte: CarteMot,
    val rarete: Rarete,
    val rang: Int?,
    val glose: String,
    val autresFormes: List<String>,
    val exemple: String?
)

/**
 * Le rendu d'une carte du carnet.
 *
 * **Pas d'illustration récupérée ailleurs, et pas d'emoji.** Un tableau
 * mot → emoji aurait été tentant, mais le vocabulaire du jeu est très
 * abstrait : mesuré sur les 1 963 formes de Wuertplaz, 1 453 premiers sens
 * distincts, dont les plus partagés sont « devoir », « marcher », « pouvoir ».
 * Une table raisonnable en aurait couvert une poignée, et un jeu où quelques
 * cartes portent une image et toutes les autres rien du tout se lit comme
 * inachevé. L'illustration est donc **générative pour toutes** : un motif
 * dérivé du mot lui-même, unique et reproductible, qui fait de chaque carte un
 * objet et non une ligne de liste.
 */
object CarteWuert {

    private val FORMAT_DATE = SimpleDateFormat("d MMM yyyy", Locale.FRENCH)

    private const val ENCRE = 0xFF212121.toInt()
    private const val ENCRE_DOUCE = 0xFF616161.toInt()
    private const val TEAL = 0xFF00796B.toInt()

    fun contenu(context: Context, carte: CarteMot): ContenuCarte {
        val fiche = TranslationDictionary.fiche(context, carte.forme)
        val exemple = TranslationDictionary.exemples(context, fiche).firstOrNull()
        return ContenuCarte(
            carte = carte,
            rarete = CarnetWuertplaz.rarete(context, carte.forme),
            rang = CarnetWuertplaz.rang(context, carte.forme),
            glose = fiche.glose,
            autresFormes = (listOf(fiche.mot) + fiche.formes)
                .distinct()
                .filter { it != carte.forme },
            exemple = exemple
        )
    }

    /**
     * La vignette de la grille du carnet : illustration, mot, rareté, sens.
     *
     * Le sens tient sur une ligne et se coupe : à deux colonnes, une glose à
     * rallonge ferait des vignettes de hauteurs différentes et la grille
     * cesserait d'en être une.
     */
    fun vignette(context: Context, c: ContenuCarte, cote: Int): View {
        val d = context.resources.displayMetrics.density

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = cadre(d, c.rarete, epais = false)
            setPadding((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (8 * d).toInt())

            addView(IllustrationWuert(context, c.carte.forme, c.rarete).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (cote * 0.62f).toInt()
                )
            })

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (6 * d).toInt() }

                addView(TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                    text = c.carte.forme
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(TEAL)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
                addView(TextView(context).apply {
                    text = c.rarete.symbole
                    textSize = 13f
                    setTextColor(c.rarete.couleur)
                })
            })

            addView(TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = if (c.glose.isEmpty()) "—" else c.glose
                textSize = 12f
                setTextColor(ENCRE_DOUCE)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }
    }

    /**
     * La carte entière, telle qu'on la regarde quand on l'a choisie.
     *
     * L'ordre suit celui d'une carte à collectionner : identité en haut,
     * illustration, puis ce que la carte apprend — le sens, une phrase du
     * dictionnaire officiel qui montre le mot en situation, la famille, et un
     * pied qui situe la carte dans la collection.
     */
    fun complete(context: Context, c: ContenuCarte): View {
        val d = context.resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = cadre(d, c.rarete, epais = true)
            setPadding(dp(14f), dp(14f), dp(14f), dp(14f))

            // Identité : le mot, et ce que la fréquence dit de lui.
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = largeur()

                addView(TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                    text = c.carte.forme
                    textSize = 24f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(TEAL)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
                addView(TextView(context).apply {
                    text = "${c.rarete.symbole} ${c.rarete.libelle}"
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    setPadding(dp(9f), dp(4f), dp(9f), dp(4f))
                    background = GradientDrawable().apply {
                        cornerRadius = 20f * d
                        setColor(c.rarete.couleur)
                    }
                })
            })

            addView(TextView(context).apply {
                layoutParams = largeur().apply { topMargin = dp(2f) }
                // La majuscule est une information, pas une décoration : en
                // luxembourgeois elle marque le substantif. C'est la seule
                // nature qu'on puisse affirmer sans étiquetage grammatical,
                // donc la seule qu'on affirme.
                val nature = if (c.carte.forme.first().isUpperCase())
                    "Substantif · " else ""
                text = "$nature${c.carte.forme.length} lettres"
                textSize = 12f
                setTextColor(ENCRE_DOUCE)
            })

            addView(IllustrationWuert(context, c.carte.forme, c.rarete).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(130f)
                ).apply { topMargin = dp(10f); bottomMargin = dp(12f) }
            })

            addView(TextView(context).apply {
                layoutParams = largeur()
                text = if (c.glose.isEmpty()) "sens non répertorié" else c.glose
                textSize = 17f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ENCRE)
                setLineSpacing(0f, 1.15f)
            })

            c.exemple?.let { phrase ->
                addView(separateur(context, d))
                addView(TextView(context).apply {
                    layoutParams = largeur()
                    text = "« $phrase »"
                    textSize = 13f
                    setTypeface(null, Typeface.ITALIC)
                    setTextColor(ENCRE_DOUCE)
                    setLineSpacing(0f, 1.2f)
                })
            }

            if (c.autresFormes.isNotEmpty()) {
                addView(separateur(context, d))
                addView(TextView(context).apply {
                    layoutParams = largeur()
                    text = "Même famille : " + c.autresFormes.take(8).joinToString(", ")
                    textSize = 12f
                    setTextColor(ENCRE_DOUCE)
                    setLineSpacing(0f, 1.15f)
                })
            }

            addView(separateur(context, d))
            addView(TextView(context).apply {
                layoutParams = largeur()
                val vues = if (c.carte.rencontres > 1)
                    " · rencontré ${c.carte.rencontres} fois" else ""
                val rang = c.rang?.let { " · ${it + 1}ᵉ mot le plus fréquent" } ?: ""
                text = "n° ${c.carte.numero}$rang · " +
                    FORMAT_DATE.format(Date(c.carte.premiereFois)) + vues
                textSize = 11f
                setTextColor(0xFF9E9E9E.toInt())
            })
        }
    }

    private fun largeur() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun separateur(context: Context, d: Float) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (1 * d).toInt()
        ).apply { topMargin = (10 * d).toInt(); bottomMargin = (10 * d).toInt() }
        setBackgroundColor(0xFFE0E0E0.toInt())
    }

    /** Le cadre : blanc, arrondi, bordé de la couleur de la rareté. */
    private fun cadre(d: Float, rarete: Rarete, epais: Boolean) =
        GradientDrawable().apply {
            cornerRadius = 14f * d
            setColor(Color.WHITE)
            setStroke(((if (epais) 3f else 2f) * d).toInt(), rarete.couleur)
        }
}

/**
 * L'illustration d'une carte, dessinée à partir du mot.
 *
 * Le motif est **déterministe** : la même forme donne toujours la même carte,
 * sinon la collection ne serait pas une collection. La teinte vient d'un
 * condensé du mot, les anneaux de ses lettres, et l'initiale sert de filigrane.
 * Rien n'est aléatoire, rien n'est stocké, et il n'y a aucune image dans
 * l'application.
 */
class IllustrationWuert(
    context: Context,
    private val mot: String,
    private val rarete: Rarete
) : View(context) {

    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pinceauTexte = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val condense = mot.fold(7919) { acc, c -> acc * 31 + c.code }
    private val teinte = ((condense % 360) + 360) % 360f

    private var fond: LinearGradient? = null

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w <= 0 || h <= 0) return
        fond = LinearGradient(
            0f, 0f, w * 0.35f, h.toFloat(),
            couleur(0.30f, 0.96f), couleur(0.55f, 0.72f),
            Shader.TileMode.CLAMP
        )
    }

    private fun couleur(saturation: Float, valeur: Float) =
        Color.HSVToColor(floatArrayOf(teinte, saturation, valeur))

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val d = resources.displayMetrics.density
        val r = 10f * d

        // Le fond, et un arrondi qui suit celui de la carte.
        pinceau.shader = fond
        pinceau.alpha = 255
        canvas.drawRoundRect(0f, 0f, w, h, r, r, pinceau)
        pinceau.shader = null

        canvas.save()
        // Les anneaux restent dans le panneau : sans découpe ils déborderaient
        // sur le texte de la carte.
        val chemin = android.graphics.Path().apply {
            addRoundRect(
                android.graphics.RectF(0f, 0f, w, h), r, r,
                android.graphics.Path.Direction.CW
            )
        }
        canvas.clipPath(chemin)

        // Un anneau par lettre, jusqu'à six : position et rayon lus dans le mot.
        pinceau.style = Paint.Style.STROKE
        val lettres = mot.take(6)
        lettres.forEachIndexed { i, c ->
            val g = c.code
            val cx = w * (0.12f + 0.16f * ((g + i * 7) % 6))
            val cy = h * (0.15f + 0.14f * ((g / 3 + i * 5) % 6))
            val rayon = h * (0.22f + 0.09f * (g % 5))
            pinceau.strokeWidth = (1.4f + (g % 3)) * d
            pinceau.color = if (i % 2 == 0) Color.WHITE else couleur(0.65f, 0.45f)
            pinceau.alpha = 48
            canvas.drawCircle(cx, cy, rayon, pinceau)
        }
        pinceau.style = Paint.Style.FILL

        // L'initiale en filigrane : la carte dit son mot même en vignette.
        pinceauTexte.color = Color.WHITE
        pinceauTexte.alpha = 64
        pinceauTexte.textSize = h * 0.72f
        val mesure = Paint.FontMetrics().also { pinceauTexte.getFontMetrics(it) }
        canvas.drawText(
            mot.take(1).uppercase(),
            w * 0.5f,
            h * 0.5f - (mesure.ascent + mesure.descent) / 2f,
            pinceauTexte
        )
        canvas.restore()

        // Les plus rares gagnent un liseré clair : le seul écart visuel entre
        // les paliers, en plus de la couleur du cadre.
        if (rarete == Rarete.TRES_RARE || rarete == Rarete.RARE) {
            pinceau.style = Paint.Style.STROKE
            pinceau.strokeWidth = 2f * d
            pinceau.color = Color.WHITE
            pinceau.alpha = if (rarete == Rarete.TRES_RARE) 190 else 120
            canvas.drawRoundRect(
                1f * d, 1f * d, w - 1f * d, h - 1f * d, r, r, pinceau
            )
            pinceau.style = Paint.Style.FILL
        }
    }
}
