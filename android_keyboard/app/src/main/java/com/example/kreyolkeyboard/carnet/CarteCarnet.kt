package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
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
    val exemple: String?,
    val blason: Blasonnement = Blasonnement.AUCUN,
    /** La traduction officielle du ZLS de [exemple], ou `null` s'il n'y en a pas. */
    val traductionExemple: String? = null,
    /** La catégorie du LOD en clair (« Nom féminin », « Verbe »), ou `null`. */
    val categorie: String? = null
)

/**
 * Le rendu d'une carte du carnet.
 *
 * **Pas d'emoji, et une image seulement là où elle est juste.** Le vocabulaire
 * des jeux est très abstrait : mesuré sur les 1 690 substantifs glosés du
 * carnet, 1 504 têtes de glose distinctes, soit 1,12 mot par dessin. Une
 * bibliothèque d'images ne peut donc pas couvrir la collection, et l'argument
 * qui tenait ici — « un jeu où quelques cartes portent une image et les autres
 * rien du tout se lit comme inachevé » — a été résolu autrement : *toutes* les
 * cartes reçoivent un sujet génératif, le tracé de leur mot, et le dessin est
 * un **surcroît** qui en distingue trois sur cent. Rien n'est inachevé, parce
 * que rien n'est vide ; l'enluminure est une propriété collectionnable de plus,
 * indépendante de la rareté. Voir [Blason] et [Meubles].
 *
 * ## La carte est une carte à jouer, et son ornement monte avec la rareté
 *
 * La disposition suit celle d'une carte de collection, et elle est **fixe** :
 * gemme de coût en débord, plaque de nom, ouverture d'illustration, ligne de
 * type, panneau de texte, deux écus et une ligne de série. Rien ne descend
 * quand une phrase du LOD prend trois lignes — c'est ce qui permet de lire une
 * grille de cartes sans en lire aucune. Les emplacements sont dans
 * [Ornement] et le tracé dans [CarteOrnee] ; ce fichier ne fait plus que
 * choisir *quoi* poser dans chaque case.
 *
 * Le cadre, lui, s'enrichit palier par palier : étain nu, bronze à rivets,
 * argent à volutes et arche, or à clef de voûte et étincelles. Voir
 * [Ornement] pour la liste exacte de ce que chaque palier ajoute, et pour ce
 * que cette échelle a coûté.
 *
 * ## Ce que la rareté a le droit de dire
 *
 * La règle a **changé**, et il faut le dire franchement. Elle disait : la
 * teinte appartient au mot, donc la rareté ne peut pas prendre une couleur de
 * plus, seulement une matière. Elle dit maintenant : la teinte appartient au
 * mot **à l'intérieur du cadre** — la face et la gemme de coût —, et le métal
 * du cadre appartient au palier.
 *
 * Ce qui autorise les deux à cohabiter, c'est que le métal *encadre* au lieu
 * de recouvrir : quatre valeurs de métal contre trois cent soixante teintes de
 * face, et c'est toujours la face et l'illustration qui remplissent l'œil.
 * Le corollaire d'origine tient toujours, et il porte même plus qu'avant : les
 * communes sont **volontairement nues**. Un palier ne se voit que par
 * contraste, et enrichir les rares sans garder les communes sobres n'aurait
 * déplacé que la moitié de l'écart.
 */
object CarteCarnet {

    private val FORMAT_DATE = SimpleDateFormat("dd.MM.yy", Locale.FRENCH)

    private const val ENCRE = 0xFF1B1610.toInt()
    private const val ENCRE_DOUCE = 0xFF4A4234.toInt()
    private const val ENCRE_PALE = 0xFF7A7160.toInt()

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
                exemple = ZuelenSpeller.decomposition(valeur).ifEmpty { null },
                // Un numéral n'est pas au classement — il n'est dans aucune
                // grille — mais son champ ne fait aucun doute : c'est une
                // mesure. Les cartes de Zuelwuert forment donc une famille de
                // couleur, au lieu du semis aléatoire qu'elles étaient.
                blason = Blasonnement(champ = Champ.TEMPS, nature = Nature.AUTRE)
            )
        }
        val fiche = TranslationDictionary.fiche(context, carte.forme)
        val exemple = TranslationDictionary.exemplesTraduits(context, fiche).firstOrNull()
        return ContenuCarte(
            carte = carte,
            rarete = Carnet.rarete(context, carte),
            rang = Carnet.rang(context, carte.forme),
            glose = fiche.glose,
            autresFormes = (listOf(fiche.mot) + fiche.formes)
                .distinct()
                .filter { it != carte.forme },
            exemple = exemple?.phrase,
            // Le blason se lit sur le représentant, comme la glose : le joueur
            // a gagné « Männer », c'est le rangement de « Mann » qui vaut. La
            // fiche est déjà là, donc cela ne coûte pas une recherche de plus.
            blason = Armorial.pour(context, fiche.mot, carte.forme),
            traductionExemple = exemple?.traduction,
            categorie = TranslationDictionary.categorie(context, fiche.mot)
        )
    }

    /**
     * La vignette de la grille du carnet : le cadre, l'illustration, le mot.
     *
     * Elle ne porte **pas** le texte de la carte ouverte, et c'est délibéré :
     * à 160 dp, un panneau de glose, une phrase et une ligne de série
     * deviendraient six lignes de quatre pixels que personne ne lit, tout en
     * écrasant l'ornement qui, lui, se lit encore très bien à cette taille.
     * Une très rare se reconnaît à sa dorure, pas à une pastille.
     *
     * Ce qui reste, c'est le strict nécessaire pour choisir quelle carte
     * ouvrir : le mot, son sens en une ligne coupée, et le ou les jeux qui
     * l'ont donnée. La barre de Leitner est peinte sur le cadre.
     */
    fun vignette(context: Context, c: ContenuCarte, cote: Int): View {
        val carte = CarteOrnee(context, c.carte.forme, c.rarete, vignette = true, blason = c.blason)
            .avecBoite(c.carte.boite)

        carte.posee(
            ligne(context, c.carte.forme, taille = 18f, couleur = ENCRE, gras = true),
            Ornement.NOM_VIGNETTE
        )

        // L'emoji du jeu plutôt que son nom : à cette taille, un nom de jeu
        // prendrait toute la ligne et chasserait le sens, qui est ce que la
        // carte apprend.
        val provenance = c.carte.jeux.joinToString("") { it.emoji }
        carte.posee(
            ligne(
                context,
                "$provenance ${c.glose.ifEmpty { "—" }}",
                taille = 11.5f, couleur = ENCRE_DOUCE, gras = false
            ),
            Ornement.GLOSE_VIGNETTE
        )
        return carte
    }

    /**
     * La carte entière, telle qu'on la regarde quand on l'a choisie.
     *
     * L'ordre est celui d'une carte de collection, et chaque case dit une
     * chose que le carnet connaissait déjà :
     *
     * - la **gemme de coût**, c'est la longueur du mot — le seul chiffre qui
     *   mesure un effort réel, celui qu'il faudra taper lettre à lettre quand
     *   la carte passera en production ;
     * - la **ligne de type** ne nomme la nature que lorsqu'elle se déduit sans
     *   étiquetage grammatical, c'est-à-dire la majuscule du substantif et le
     *   numéral. Pour tout le reste elle se tait et ne porte que le jeu ;
     * - les deux **écus** comptent les rencontres et la boîte de révision ;
     * - la **ligne de série** situe la carte dans la collection : son numéro
     *   d'entrée, le sigle du jeu, le jour de la capture, et le rang de
     *   fréquence qui a décidé de sa rareté.
     */
    fun complete(context: Context, c: ContenuCarte): View {
        val jeu = c.carte.origine
        val metal = Ornement.metal(c.rarete)
        val carte = CarteOrnee(context, c.carte.forme, c.rarete, vignette = false, blason = c.blason)

        carte.posee(
            ligne(context, "${c.carte.forme.length}", taille = 25f, couleur = Color.WHITE, gras = true),
            Ornement.GEMME
        )
        carte.posee(
            ligne(context, c.carte.forme, taille = 21f, couleur = ENCRE, gras = true),
            Ornement.PLAQUE
        )

        // Chaque moitié dit ce qu'elle est : « Substantif · Wuertplaz » se
        // lisait comme si le jeu était un substantif.
        // La catégorie du LOD d'abord ; un mot qu'il ignore (« RTL », « Bettel »)
        // garde la seule déduction sûre, la majuscule du nom.
        val nature = when {
            c.carte.nombre != null -> "Nombre"
            c.categorie != null -> c.categorie
            c.carte.forme.first().isUpperCase() -> "Nom"
            else -> null
        }
        val typeLigne = nature?.let { "$it · gagné à ${jeu.nom}" } ?: "Gagné à ${jeu.nom}"
        // La plaque tient environ 34 caractères en corps 12 ; au-delà
        // (« Nom masculin ou neutre · gagné à Kräizwuert ») le corps diminue
        // plutôt que de couper le libellé.
        val corps = if (typeLigne.length <= 34) 12f else (12f * 34 / typeLigne.length).coerceAtLeast(9f)
        carte.posee(
            ligne(context, typeLigne, taille = corps, couleur = ENCRE, gras = true),
            Ornement.TYPE
        )

        carte.posee(panneau(context, c), Ornement.PANNEAU_TEXTE)

        carte.posee(
            ligne(context, "${c.carte.rencontres}", taille = 15f, couleur = Color.WHITE, gras = true),
            Ornement.ECU_G_TEXTE
        )
        carte.posee(
            ligne(
                context,
                if (c.carte.acquise) "✓" else "${c.carte.boite + 1}",
                taille = 15f, couleur = Color.WHITE, gras = true
            ),
            Ornement.ECU_D_TEXTE
        )

        // Le corps de 7,5 est imposé par la bande, qui vit maintenant dans la
        // marge : voir [Ornement.SERIE_G]. C'est celui des libellés d'écu.
        val serie = "n° %03d · %s · %s".format(
            Locale.FRENCH, c.carte.numero, jeu.sigle, FORMAT_DATE.format(Date(c.carte.premiereFois))
        )
        carte.posee(
            ligne(context, serie, taille = 7.5f, couleur = metal.trait, gras = true, ou = Gravity.START),
            Ornement.SERIE_G
        )
        carte.posee(
            ligne(
                context,
                c.rang?.let { "${it + 1}ᵉ" } ?: "hors corpus",
                taille = 7.5f, couleur = metal.trait, gras = true, ou = Gravity.END
            ),
            Ornement.SERIE_D
        )
        return carte
    }

    /**
     * Le panneau de texte : le sens, la phrase du LOD, la famille.
     *
     * C'est la boîte de texte d'une carte à jouer, et c'est le cœur de
     * celle-ci : sur une carte de jeu ce cadre dit ce que la carte *fait*, ici
     * il dit ce que le mot *veut dire*. Les trois lignes se coupent plutôt que
     * de déborder — la carte a une taille fixe, et une glose à rallonge ne
     * peut pas pousser les écus hors du cadre.
     */
    private fun panneau(context: Context, c: ContenuCarte): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL

            // La traduction de la phrase n'existe que si le ZLS l'a publiée ;
            // sinon rien ne la remplace, pas même une mention.
            val traduction = c.traductionExemple?.takeIf { c.exemple != null }

            // Le panneau a une hauteur fixe, déjà remplie par la glose sur deux
            // lignes, la phrase et la famille. Quand la traduction s'ajoute, la
            // glose se contente d'une ligne et la famille cède sa place : la
            // phrase traduite dit le sens mieux qu'une seconde ligne de glose,
            // et la famille reste lisible dans la fiche du Wierderbuch.
            addView(bloc(context, c.glose.ifEmpty { "sens non répertorié" }, 15f, ENCRE, 0f).apply {
                setTypeface(null, Typeface.BOLD)
                maxLines = if (traduction != null) 1 else 2
            })

            c.exemple?.let { phrase ->
                // La décomposition d'un numéral est une explication, pas une
                // citation : elle ne prend pas les guillemets. Espaces
                // insécables à l'intérieur, sinon le « » » fermant part seul
                // à la ligne.
                val texte = if (c.carte.nombre != null) phrase else "« $phrase »"
                addView(bloc(context, texte, 12f, ENCRE_DOUCE, 4f).apply {
                    setTypeface(null, Typeface.ITALIC)
                    maxLines = 2
                })
            }

            if (traduction != null) {
                addView(bloc(context, traduction, 10f, ENCRE_PALE, 1f).apply { maxLines = 2 })
            } else if (c.autresFormes.isNotEmpty()) {
                addView(
                    bloc(
                        context,
                        "Même famille : " + c.autresFormes.take(6).joinToString(", "),
                        10.5f, ENCRE_PALE, 3f
                    ).apply { maxLines = 1 }
                )
            }
        }

    /**
     * Une ligne posée dans une case : centrée, sur une seule ligne, coupée.
     *
     * La taille est exprimée **en unités de carte** et voyage dans le `tag` ;
     * c'est [CarteOrnee] qui la convertit en pixels une fois qu'il connaît sa
     * largeur réelle. Sans ça, la même carte serait illisible en vignette ou
     * ridicule en grand.
     */
    private fun ligne(
        context: Context,
        contenu: String,
        taille: Float,
        couleur: Int,
        gras: Boolean,
        ou: Int = Gravity.CENTER
    ): TextView = TextView(context).apply {
        text = contenu
        setTextColor(couleur)
        if (gras) setTypeface(null, Typeface.BOLD)
        gravity = ou or Gravity.CENTER_VERTICAL
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
        tag = floatArrayOf(taille, 0f)
    }

    /** Une ligne du panneau de texte : elle, a le droit de revenir à la ligne. */
    private fun bloc(
        context: Context,
        contenu: String,
        taille: Float,
        couleur: Int,
        margeHaute: Float
    ): TextView = TextView(context).apply {
        text = contenu
        setTextColor(couleur)
        setLineSpacing(0f, 1.12f)
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        tag = floatArrayOf(taille, margeHaute)
    }
}
