package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import com.example.kreyolkeyboard.TranslationDictionary
import com.example.kreyolkeyboard.zuelen.ZuelenSpeller
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
 * mot → emoji aurait été tentant, mais le vocabulaire des jeux est très
 * abstrait : mesuré sur les 1 963 formes de Wuertplaz, 1 453 premiers sens
 * distincts, dont les plus partagés sont « devoir », « marcher », « pouvoir ».
 * Une table raisonnable en aurait couvert une poignée, et un jeu où quelques
 * cartes portent une image et toutes les autres rien du tout se lit comme
 * inachevé. L'illustration est donc **générative pour toutes** : un motif
 * dérivé du mot lui-même, unique et reproductible, qui fait de chaque carte un
 * objet et non une ligne de liste.
 *
 * Depuis que les sept jeux alimentent le carnet, la carte dit aussi **d'où
 * elle vient** : l'emoji du jeu d'origine en vignette, l'étiquette entière sur
 * la carte complète. C'est la seule chose qui distingue deux cartes du même
 * mot gagné à deux endroits, et c'est ce qui fait du carnet la mémoire de tous
 * les jeux plutôt que d'un seul.
 *
 * ## Ce que la rareté a le droit de dire
 *
 * Deux échelles de couleur sont déjà prises : la **teinte** appartient au mot,
 * et c'est elle qui garde la collection variée ; le violet du carnet appartient
 * à la **barre de boîte**, et deux échelles chromatiques sur la même vignette
 * ne se lisent plus (voir [barreDeBoite]). La rareté ne peut donc pas se
 * distinguer par plus de couleur : elle passe par **la matière, le relief et le
 * mouvement**, comme sur une vraie carte à collectionner, où la commune et la
 * brillante partagent souvent la même illustration et ne diffèrent que par le
 * papier.
 *
 * Le corollaire compte autant : les communes sont **volontairement plates**.
 * Un palier ne se voit que par contraste, et rendre les rares plus riches sans
 * rendre les communes plus sobres n'aurait déplacé que la moitié de l'écart.
 */
object CarteCarnet {

    private val FORMAT_DATE = SimpleDateFormat("d MMM yyyy", Locale.FRENCH)

    private const val ENCRE = 0xFF212121.toInt()
    private const val ENCRE_DOUCE = 0xFF616161.toInt()

    fun contenu(context: Context, carte: CarteMot): ContenuCarte {
        // Un numéral n'a pas de fiche au dictionnaire : sa leçon est sa
        // décomposition, qui est exactement ce que Zuelwuert enseigne.
        carte.nombre?.let { valeur ->
            return ContenuCarte(
                carte = carte,
                rarete = Rarete.pourNombre(valeur),
                rang = null,
                glose = "le nombre $valeur",
                autresFormes = emptyList(),
                exemple = ZuelenSpeller.decomposition(valeur).ifEmpty { null }
            )
        }
        val fiche = TranslationDictionary.fiche(context, carte.forme)
        val exemple = TranslationDictionary.exemples(context, fiche).firstOrNull()
        return ContenuCarte(
            carte = carte,
            rarete = Carnet.rarete(context, carte),
            rang = Carnet.rang(context, carte.forme),
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
        val jeu = c.carte.origine

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = cadre(d, c.rarete, epais = false)
            poserLeRelief(this, c.rarete, d)
            setPadding((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (8 * d).toInt())

            addView(IllustrationCarte(context, c.carte.forme, c.rarete).apply {
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
                    text = c.carte.forme
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(jeu.couleur)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                })
                addView(pastilleRarete(context, c.rarete, d, avecLibelle = false))
            })

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                // L'emoji du jeu plutôt que son nom : à cette taille, un nom
                // de jeu prendrait toute la ligne et chasserait le sens, qui
                // est ce que la carte apprend.
                addView(TextView(context).apply {
                    text = c.carte.jeux.joinToString("") { it.emoji }
                    textSize = 10f
                    setPadding(0, 0, (4 * d).toInt(), 0)
                })
                addView(TextView(context).apply {
                    text = if (c.glose.isEmpty()) "—" else c.glose
                    textSize = 12f
                    setTextColor(ENCRE_DOUCE)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                })
            })

            addView(barreDeBoite(context, c.carte.boite, d))
        }
    }

    /**
     * Où en est la carte dans sa révision : un segment par boîte franchie.
     *
     * **Pas une couleur**, une barre. La couleur du cadre et le symbole
     * appartiennent déjà à la rareté, et deux échelles de couleur sur la même
     * vignette ne se lisent plus : le joueur ne saurait plus si le violet dit
     * « rare » ou « presque acquis ». La barre prend la couleur du carnet, qui
     * n'est celle d'aucun jeu ni d'aucun palier de rareté.
     */
    fun barreDeBoite(context: Context, boite: Int, d: Float): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (3 * d).toInt()
            ).apply { topMargin = (6 * d).toInt() }
            repeat(Widderhuelen.BOITE_ACQUISE) { rang ->
                addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
                    ).apply { if (rang > 0) leftMargin = (2 * d).toInt() }
                    background = GradientDrawable().apply {
                        cornerRadius = 2f * d
                        setColor(
                            if (rang < boite) Carnet.COULEUR else 0xFFE0E0E0.toInt()
                        )
                    }
                })
            }
        }

    /**
     * La carte entière, telle qu'on la regarde quand on l'a choisie.
     *
     * L'ordre suit celui d'une carte à collectionner : identité en haut,
     * illustration, puis ce que la carte apprend — le sens, une phrase du
     * dictionnaire officiel qui montre le mot en situation, la famille, et un
     * pied qui situe la carte dans la collection et nomme le jeu qui l'a
     * donnée.
     */
    fun complete(context: Context, c: ContenuCarte): View {
        val d = context.resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()
        val jeu = c.carte.origine

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
                    setTextColor(jeu.couleur)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
                addView(pastilleRarete(context, c.rarete, d, avecLibelle = true))
            })

            addView(TextView(context).apply {
                layoutParams = largeur().apply { topMargin = dp(2f) }
                // La majuscule est une information, pas une décoration : en
                // luxembourgeois elle marque le substantif. C'est la seule
                // nature qu'on puisse affirmer sans étiquetage grammatical,
                // donc la seule qu'on affirme. Un numéral, lui, s'annonce.
                val nature = when {
                    c.carte.nombre != null -> "Numéral · "
                    c.carte.forme.first().isUpperCase() -> "Substantif · "
                    else -> ""
                }
                text = "$nature${c.carte.forme.length} lettres"
                textSize = 12f
                setTextColor(ENCRE_DOUCE)
            })

            // Seule la carte ouverte suit l'inclinaison du téléphone : dans la
            // grille, autant de capteurs que de vignettes serait absurde, et
            // l'effet ne se voit pas à cette taille.
            addView(IllustrationCarte(context, c.carte.forme, c.rarete, reactive = true).apply {
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
                    // La décomposition d'un numéral est une explication, pas
                    // une citation : elle ne prend pas les guillemets.
                    text = if (c.carte.nombre != null) phrase else "« $phrase »"
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

            // La provenance, en toutes lettres. Une carte gagnée dans deux jeux
            // les porte tous les deux : c'est la trace qu'un même mot a été
            // reconnu de deux manières, et c'est plus flatteur qu'un compteur.
            addView(separateur(context, d))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = largeur()
                c.carte.jeux.forEachIndexed { i, j ->
                    addView(etiquetteJeu(context, j, d).apply {
                        (layoutParams as LinearLayout.LayoutParams).leftMargin =
                            if (i == 0) 0 else dp(6f)
                    })
                }
            })

            // Où en est la révision de cette carte. La barre reprend celle de
            // la vignette, pour qu'un joueur qui ouvre une carte retrouve le
            // même repère qu'il vient de voir dans la grille.
            addView(separateur(context, d))
            addView(TextView(context).apply {
                layoutParams = largeur()
                text = if (c.carte.acquise) "Révision · carte acquise"
                else "Révision · boîte ${c.carte.boite + 1} sur ${Widderhuelen.BOITE_ACQUISE}"
                textSize = 12f
                setTextColor(ENCRE_DOUCE)
            })
            addView(barreDeBoite(context, c.carte.boite, d))

            addView(TextView(context).apply {
                layoutParams = largeur().apply { topMargin = dp(10f) }
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

    /** L'étiquette d'un jeu : son emoji, son nom, sa couleur. */
    fun etiquetteJeu(context: Context, jeu: JeuCarte, d: Float): TextView =
        TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = "${jeu.emoji} ${jeu.nom}"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(jeu.couleur)
            setPadding((8 * d).toInt(), (4 * d).toInt(), (8 * d).toInt(), (4 * d).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 12f * d
                setColor(Color.WHITE)
                setStroke((1 * d).toInt(), jeu.couleur)
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

    /**
     * Le cadre : blanc, arrondi, et d'autant plus travaillé que la carte est rare.
     *
     * Trois reliefs pour quatre paliers. Les deux premiers gardent le filet
     * simple d'origine — c'est le fond du panier, il n'a rien à annoncer. Une
     * **rare** gagne un second filet clair en retrait, l'astuce d'encadreur qui
     * donne de la profondeur sans ajouter de couleur. Une **très rare** perd le
     * filet uni au profit d'un dégradé circulaire : la bordure ne se lit plus
     * comme un trait mais comme une matière.
     */
    private fun cadre(d: Float, rarete: Rarete, epais: Boolean): Drawable {
        val trait = ((if (epais) 3f else 2f) * d).toInt()
        if (rarete == Rarete.TRES_RARE) return CadreIrise(d, epais)

        val plein = GradientDrawable().apply {
            cornerRadius = 14f * d
            setColor(Color.WHITE)
            setStroke(trait, rarete.couleur)
        }
        if (rarete != Rarete.RARE) return plein

        val retrait = trait + (2f * d).toInt()
        return LayerDrawable(
            arrayOf<Drawable>(
                plein,
                GradientDrawable().apply {
                    cornerRadius = 11f * d
                    setColor(Color.TRANSPARENT)
                    setStroke((1f * d).toInt(), eclaircir(rarete.couleur, 0.60f))
                }
            )
        ).apply { setLayerInset(1, retrait, retrait, retrait, retrait) }
    }

    /**
     * L'ombre portée des cartes rares dans la grille.
     *
     * Une très rare se décolle de la page, une rare l'effleure, les autres y
     * restent posées. L'ombre est teintée du palier là où Android le permet :
     * en gris elle ne dirait que « ceci est en avant », en violet elle dit
     * lequel des quatre paliers.
     */
    private fun poserLeRelief(vue: View, rarete: Rarete, d: Float) {
        if (!rarete.distinguee) return
        vue.elevation = (if (rarete == Rarete.TRES_RARE) 6f else 3f) * d
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            vue.outlineSpotShadowColor = rarete.couleur
            vue.outlineAmbientShadowColor = rarete.couleur
        }
    }

    /**
     * La pastille de rareté : l'insigne, et le libellé quand la place le permet.
     *
     * En vignette, seuls les deux paliers hauts la portent. Une pastille sur
     * toutes les cartes deviendrait un élément d'interface qu'on cesse de voir ;
     * réservée aux deux tiers supérieurs, elle reste une distinction.
     */
    private fun pastilleRarete(
        context: Context,
        rarete: Rarete,
        d: Float,
        avecLibelle: Boolean
    ): TextView = TextView(context).apply {
        val nu = !avecLibelle && !rarete.distinguee
        text = if (avecLibelle) "${rarete.insigne} ${rarete.libelle}" else rarete.insigne
        textSize = if (avecLibelle) 12f else if (nu) 12f else 10f
        setTypeface(null, Typeface.BOLD)
        if (nu) {
            // Les paliers bas gardent le symbole nu : pas de fond, pas de cadre,
            // rien qui ressemble à une décoration qu'ils n'ont pas méritée.
            setTextColor(rarete.couleur)
            return@apply
        }
        setTextColor(Color.WHITE)
        // La pastille d'une vignette reste serrée : la glose partage la ligne.
        val large = if (avecLibelle) 9f else 6f
        val haut = if (avecLibelle) 4f else 2f
        setPadding((large * d).toInt(), (haut * d).toInt(), (large * d).toInt(), (haut * d).toInt())
        background = GradientDrawable().apply {
            cornerRadius = 20f * d
            setColor(rarete.couleur)
        }
    }
}

/**
 * Le cadre d'une très rare : un dégradé circulaire en guise de filet.
 *
 * [GradientDrawable.setStroke] ne prend qu'une couleur unie, et c'est la seule
 * raison pour laquelle cette classe existe : il faut un [Paint] porteur d'un
 * [SweepGradient] pour qu'une bordure change de teinte sur son tour. Le
 * dégradé boucle — sa dernière couleur est la première — sinon la couture se
 * voit sur le bord droit de la carte.
 */
private class CadreIrise(private val d: Float, epais: Boolean) : Drawable() {

    private val trait = (if (epais) 3f else 2f) * d
    private val rayon = 14f * d
    private val zone = RectF()

    private val fond = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val bord = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = trait
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        if (bounds.isEmpty) return
        val base = Rarete.TRES_RARE.couleur
        bord.shader = SweepGradient(
            bounds.exactCenterX(), bounds.exactCenterY(),
            intArrayOf(
                base,
                eclaircir(base, 0.55f),
                0xFFFFD54F.toInt(),
                eclaircir(base, 0.30f),
                0xFF4FC3F7.toInt(),
                eclaircir(base, 0.55f),
                base
            ),
            null
        )
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        zone.set(bounds)
        zone.inset(trait / 2f, trait / 2f)
        canvas.drawRoundRect(zone, rayon, rayon, fond)
        canvas.drawRoundRect(zone, rayon, rayon, bord)
    }

    /** Sans contour, pas d'ombre portée : c'est lui qui la découpe. */
    override fun getOutline(outline: Outline) {
        outline.setRoundRect(bounds, rayon)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(filtre: ColorFilter?) = Unit
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}

/**
 * L'illustration d'une carte, dessinée à partir du mot.
 *
 * Le motif est **déterministe** : la même forme donne toujours la même carte,
 * sinon la collection ne serait pas une collection. La teinte vient d'un
 * condensé du mot, les anneaux de ses lettres, et l'initiale sert de filigrane.
 * Rien n'est aléatoire, rien n'est stocké, et il n'y a aucune image dans
 * l'application.
 *
 * La teinte vient du **mot** et non du jeu : c'est ce qui garde la collection
 * variée maintenant que sept jeux l'alimentent, et ce qui fait qu'un mot gagné
 * deux fois reste la même carte.
 *
 * ## Quatre matières pour quatre paliers
 *
 * La teinte étant prise par le mot, c'est la **matière** du panneau qui dit la
 * rareté — exactement comme le papier d'une carte à collectionner :
 *
 * - **Commun** : un aplat mat, dégradé resserré, anneaux effacés. Volontairement
 *   pauvre : sans une commune terne, une très rare n'a rien à surpasser.
 * - **Peu commun** : le dégradé plein, plus un grain de hachures obliques.
 * - **Rare** : un halo clair derrière le filigrane, comme si l'initiale était
 *   éclairée par-derrière, et un coin coupé aux couleurs du palier.
 * - **Très rare** : irisation complète — un dégradé circulaire de teintes
 *   voisines, traversé d'une bande de brillance, qui **suit l'inclinaison du
 *   téléphone** quand la carte est ouverte.
 *
 * ## Ce que coûte le rendu
 *
 * Tous les dégradés et tous les chemins se construisent dans [onSizeChanged],
 * jamais dans [onDraw] : la grille du carnet peut afficher plusieurs centaines
 * de vignettes, et une allocation par trame les rendrait toutes saccadées.
 * L'irisation se pose en aplat translucide plutôt qu'en `PorterDuff.SCREEN` —
 * le rendu est le même à l'œil, et il évite le calque hors écran qu'un mode de
 * fusion impose sur un canevas matériel.
 *
 * [reactive] n'est vrai que pour la carte ouverte : une vignette n'écoute
 * jamais l'accéléromètre.
 */
class IllustrationCarte(
    context: Context,
    private val mot: String,
    private val rarete: Rarete,
    private val reactive: Boolean = false
) : View(context), SensorEventListener {

    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pinceauTexte = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val condense = mot.fold(7919) { acc, c -> acc * 31 + c.code }
    private val teinte = ((condense % 360) + 360) % 360f

    // Tout ce qui ne dépend pas de la taille est calculé une fois : [onDraw]
    // n'alloue rien, la grille en dépend.
    private val lettres = mot.take(6)
    private val initiale = mot.take(1).uppercase()
    private val mesure = Paint.FontMetrics()

    private var fond: LinearGradient? = null
    private var halo: RadialGradient? = null
    private var irisation: SweepGradient? = null
    private var brillance: LinearGradient? = null

    /** Le côté du coin coupé, en pixels : posé par [onSizeChanged], relu par [onDraw]. */
    private var coteCoin = 0f

    private val decoupe = Path()
    private val hachures = Path()
    private val coin = Path()
    private val zone = RectF()
    private val matrice = Matrix()

    /** Le roulis du téléphone, ramené dans [-1, 1]. Voir [onSensorChanged]. */
    private var roulis = 0f
    private var capteurs: SensorManager? = null

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w <= 0 || h <= 0) return
        val d = resources.displayMetrics.density
        val r = 10f * d

        // Le fond. Une commune reste dans un mouchoir de poche — deux valeurs
        // proches, peu de saturation ; les autres gardent le dégradé d'origine.
        fond = if (rarete == Rarete.COMMUN) LinearGradient(
            0f, 0f, w * 0.35f, h.toFloat(),
            couleur(0.16f, 0.93f), couleur(0.24f, 0.85f),
            Shader.TileMode.CLAMP
        ) else LinearGradient(
            0f, 0f, w * 0.35f, h.toFloat(),
            couleur(0.30f, 0.96f), couleur(0.55f, 0.72f),
            Shader.TileMode.CLAMP
        )

        decoupe.reset()
        decoupe.addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, Path.Direction.CW)

        hachures.reset()
        if (rarete == Rarete.PEU_COMMUN) {
            // Des obliques régulières d'un bord à l'autre : le grain du papier,
            // pas un motif qu'on cherche à lire.
            val pas = 9f * d
            var x = -h.toFloat()
            while (x < w) {
                hachures.moveTo(x, h.toFloat())
                hachures.lineTo(x + h, 0f)
                x += pas
            }
        }

        halo = if (rarete == Rarete.RARE) RadialGradient(
            w * 0.5f, h * 0.5f, h * 0.72f,
            intArrayOf(
                Color.argb(120, 255, 255, 255),
                Color.argb(40, 255, 255, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        ) else null

        coin.reset()
        coteCoin = h * 0.26f
        if (rarete.distinguee) {
            coin.moveTo(w - coteCoin, 0f)
            coin.lineTo(w.toFloat(), 0f)
            coin.lineTo(w.toFloat(), coteCoin)
            coin.close()
        }

        if (rarete == Rarete.TRES_RARE) {
            irisation = SweepGradient(w * 0.5f, h * 0.5f, arcEnCiel(), null)
            brillance = LinearGradient(
                0f, h.toFloat(), w.toFloat(), 0f,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.argb(96, 255, 255, 255),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0.30f, 0.50f, 0.70f),
                Shader.TileMode.CLAMP
            )
            appliquerRoulis()
        } else {
            irisation = null
            brillance = null
        }
    }

    private fun couleur(saturation: Float, valeur: Float) =
        Color.HSVToColor(floatArrayOf(teinte, saturation, valeur))

    /**
     * Les teintes de l'irisation : un tour complet du cercle depuis celle du mot.
     *
     * La dernière reprend la première, sinon le dégradé circulaire montre sa
     * couture. La saturation reste basse : à pleine saturation, l'arc-en-ciel
     * mange le mot au lieu de faire briller la carte.
     */
    private fun arcEnCiel(): IntArray {
        val n = 7
        return IntArray(n + 1) { i ->
            val t = (teinte + 360f * (i % n) / n) % 360f
            Color.HSVToColor(floatArrayOf(t, 0.45f, 1f))
        }
    }

    /** Fait tourner l'irisation et glisser la bande de brillance. */
    private fun appliquerRoulis() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        matrice.setRotate(roulis * 55f, w * 0.5f, h * 0.5f)
        irisation?.setLocalMatrix(matrice)
        matrice.setTranslate(roulis * w * 0.45f, 0f)
        brillance?.setLocalMatrix(matrice)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!reactive || rarete != Rarete.TRES_RARE) return
        // Sans animations, la carte garde son irisation mais cesse de bouger :
        // le réglage système dit « pas de mouvement », pas « pas de couleur ».
        if (Pochette.animationsReduites(context)) return
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val capteur = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        manager.registerListener(this, capteur, SensorManager.SENSOR_DELAY_UI)
        capteurs = manager
    }

    override fun onDetachedFromWindow() {
        capteurs?.unregisterListener(this)
        capteurs = null
        super.onDetachedFromWindow()
    }

    override fun onAccuracyChanged(capteur: Sensor?, precision: Int) = Unit

    /**
     * Le roulis, lissé, et seulement quand il a vraiment changé.
     *
     * L'accéléromètre livre une valeur plusieurs dizaines de fois par seconde ;
     * redessiner à chaque fois pour un dixième de degré viderait la batterie
     * sans que personne ne voie la différence. Le filtre passe-bas rend aussi
     * le reflet lourd, ce qui est exactement l'effet voulu : une carte, ça a du
     * poids.
     */
    override fun onSensorChanged(evenement: SensorEvent) {
        if (evenement.values.isEmpty()) return
        val cible = (-evenement.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
        if (abs(cible - roulis) < 0.04f) return
        roulis += (cible - roulis) * 0.20f
        appliquerRoulis()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val d = resources.displayMetrics.density
        val r = 10f * d
        zone.set(0f, 0f, w, h)

        // Le fond, et un arrondi qui suit celui de la carte.
        pinceau.shader = fond
        pinceau.alpha = 255
        canvas.drawRoundRect(zone, r, r, pinceau)
        pinceau.shader = null

        canvas.save()
        // Tout reste dans le panneau : sans découpe, anneaux, hachures et coin
        // déborderaient sur le texte de la carte.
        canvas.clipPath(decoupe)

        // Le grain d'une peu commune.
        if (rarete == Rarete.PEU_COMMUN) {
            pinceau.style = Paint.Style.STROKE
            pinceau.strokeWidth = 1f * d
            pinceau.color = Color.WHITE
            pinceau.alpha = 22
            canvas.drawPath(hachures, pinceau)
            pinceau.style = Paint.Style.FILL
        }

        // Le halo d'une rare, posé avant les anneaux pour rester derrière eux.
        halo?.let {
            pinceau.shader = it
            pinceau.alpha = 255
            canvas.drawRoundRect(zone, r, r, pinceau)
            pinceau.shader = null
        }

        // Un anneau par lettre, jusqu'à six : position et rayon lus dans le mot.
        // Une commune les garde presque invisibles — c'est sa matière, mate.
        pinceau.style = Paint.Style.STROKE
        val opacite = if (rarete == Rarete.COMMUN) 30 else 48
        lettres.forEachIndexed { i, c ->
            val g = c.code
            val cx = w * (0.12f + 0.16f * ((g + i * 7) % 6))
            val cy = h * (0.15f + 0.14f * ((g / 3 + i * 5) % 6))
            val rayon = h * (0.22f + 0.09f * (g % 5))
            pinceau.strokeWidth = (1.4f + (g % 3)) * d
            pinceau.color = if (i % 2 == 0) Color.WHITE else couleur(0.65f, 0.45f)
            pinceau.alpha = opacite
            canvas.drawCircle(cx, cy, rayon, pinceau)
        }
        pinceau.style = Paint.Style.FILL

        // L'irisation d'une très rare : le dégradé circulaire, puis la bande de
        // brillance par-dessus. Les deux suivent l'inclinaison du téléphone.
        irisation?.let {
            pinceau.shader = it
            pinceau.alpha = 52
            canvas.drawRoundRect(zone, r, r, pinceau)
            pinceau.shader = null
        }

        // L'initiale en filigrane : la carte dit son mot même en vignette.
        pinceauTexte.style = Paint.Style.FILL
        pinceauTexte.color = Color.WHITE
        pinceauTexte.alpha = 64
        pinceauTexte.textSize = h * 0.72f
        pinceauTexte.getFontMetrics(mesure)
        val ligneDeBase = h * 0.5f - (mesure.ascent + mesure.descent) / 2f
        canvas.drawText(initiale, w * 0.5f, ligneDeBase, pinceauTexte)
        if (rarete == Rarete.TRES_RARE) {
            // Un contour sur le filigrane : sous l'irisation, une lettre pleine
            // se dilue ; détourée, elle tient.
            pinceauTexte.style = Paint.Style.STROKE
            pinceauTexte.strokeWidth = 1.6f * d
            pinceauTexte.alpha = 92
            canvas.drawText(initiale, w * 0.5f, ligneDeBase, pinceauTexte)
            pinceauTexte.style = Paint.Style.FILL
        }

        brillance?.let {
            pinceau.shader = it
            pinceau.alpha = 255
            canvas.drawRoundRect(zone, r, r, pinceau)
            pinceau.shader = null
        }

        // Le coin coupé des deux paliers hauts. C'est la marque de rareté qui
        // survit à la taille d'une vignette, là où le liseré ne se voyait plus.
        if (rarete.distinguee) {
            pinceau.color = rarete.couleur
            pinceau.alpha = 235
            canvas.drawPath(coin, pinceau)
            pinceau.style = Paint.Style.STROKE
            pinceau.strokeWidth = 1.3f * d
            pinceau.color = Color.WHITE
            pinceau.alpha = 180
            canvas.drawLine(w - coteCoin, 0f, w, coteCoin, pinceau)
            pinceau.style = Paint.Style.FILL
        }
        canvas.restore()

        // Les plus rares gagnent un liseré clair, par-dessus tout le reste.
        if (rarete.distinguee) {
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
