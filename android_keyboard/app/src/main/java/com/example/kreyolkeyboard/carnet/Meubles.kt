package com.example.kreyolkeyboard.carnet

import android.graphics.Path
import android.graphics.RectF

/**
 * Les meubles : les silhouettes que porte une carte enluminée.
 *
 * ## Ce que cette bibliothèque n'essaie pas de faire
 *
 * Elle n'essaie pas de **couvrir le carnet**, et c'est le renversement qui la
 * rend possible. La mesure de l'exploration est sans appel : sur les 1 690
 * substantifs glosés, 1 504 têtes de glose distinctes, soit **1,12 mot par
 * dessin**, et 89 % des têtes ne concernent qu'un seul mot. Il n'y a pas de
 * Pareto — la courbe de couverture colle à la diagonale. Une bibliothèque de
 * cent vingt silhouettes ne couvrirait pas 16 % des substantifs.
 *
 * Alors on ne demande plus au meuble de désigner chaque mot. Dans une vraie
 * collection, toutes les cartes ne sont pas illustrées : l'illustration est ce
 * qui **distingue** quelques-unes. On ne dessine donc plus ce qu'il faudrait
 * couvrir, on dessine ce qu'on sait bien dessiner, et la carte enluminée
 * devient une propriété collectionnable en soi — indépendante de la rareté,
 * qui, elle, est fixée par la fréquence et n'est pas négociable.
 *
 * Soixante-quatorze silhouettes pour soixante-quinze mots d'un carnet de deux
 * mille huit cents : à peu près trois cartes sur cent. C'est la proportion
 * visée, et aucun calcul de couverture ne l'aurait trouvée.
 *
 * ## L'attribution est ailleurs, et elle est manuelle
 *
 * Le tableau mot → meuble n'est pas ici : il est dans
 * `Dictionnaires/generate_blasons.py`, écrit à la main, une ligne à la fois.
 * La raison est une contrainte de justesse. La carte affiche **toute** la
 * glose, et un dessin n'en illustre qu'un sens : sur la face réponse d'un
 * outil de révision, une image qui tranche une polysémie est activement
 * fausse. `Feier` est « feu, incendie, **fête** », `Bierg` « montagne, côte,
 * **garant** », `Nol` « clou, **ongle**, **aiguille** » — aucun des trois n'est
 * enluminé, et le générateur refuse de sortir si un meuble attribué n'existe
 * pas ici.
 *
 * ## Le dessin
 *
 * Chaque silhouette est tracée dans un **carré de cent unités centré sur
 * l'origine**, et [chemin] la met à l'échelle de la fenêtre. Le remplissage
 * est en `EVEN_ODD` : une forme posée à l'intérieur d'une autre y creuse un
 * trou, ce qui donne la porte d'une maison ou l'œil d'un poisson sans avoir à
 * tracer le contour à l'envers. Deux chemins qui doivent rester pleins côte à
 * côte se **touchent** donc sans se chevaucher — le tronc de l'arbre est abouté
 * à sa couronne, jamais glissé dessous.
 *
 * Les chemins sont construits une fois et gardés : la carte se trace en unités
 * de carte, et rien de ceci ne dépend de la taille à l'écran.
 */
internal object Meubles {

    /** Le côté du carré de tracé. Tout meuble tient dedans, marges comprises. */
    const val COTE = 100f

    private val cache = HashMap<String, Path>()

    /**
     * Le chemin d'un meuble, dans son carré de cent unités, ou `null` s'il
     * n'existe pas.
     *
     * Un nom inconnu n'est pas une erreur à faire remonter : la carte retombe
     * simplement sur son tracé, et c'est un état parfaitement présentable —
     * c'est même celui de 97 % du carnet.
     */
    fun chemin(nom: String): Path? = cache[nom] ?: TABLE[nom]?.let { trace ->
        Path().apply {
            fillType = Path.FillType.EVEN_ODD
            trace(this)
            cache[nom] = this
        }
    }

    /** Les noms dessinés, pour le test d'actif. */
    fun noms(): Set<String> = TABLE.keys

    // -- petit outillage de tracé ------------------------------------------
    // Il n'y a pas de bibliothèque de formes dans `android.graphics` : sans
    // ces quatre lignes, chaque rectangle arrondi coûterait un RectF nommé.

    private fun Path.pol(vararg v: Float) {
        moveTo(v[0], v[1])
        var i = 2
        while (i < v.size) { lineTo(v[i], v[i + 1]); i += 2 }
        close()
    }

    private fun Path.disque(cx: Float, cy: Float, r: Float) =
        addCircle(cx, cy, r, Path.Direction.CW)

    private fun Path.arrondi(l: Float, t: Float, r: Float, b: Float, rayon: Float) =
        addRoundRect(RectF(l, t, r, b), rayon, rayon, Path.Direction.CW)

    private fun Path.ovale(l: Float, t: Float, r: Float, b: Float) =
        addOval(RectF(l, t, r, b), Path.Direction.CW)

    // -- la bibliothèque ---------------------------------------------------

    private val TABLE: Map<String, (Path) -> Unit> = mapOf(

        // ---- la maison et ce qu'elle contient ----------------------------

        "maison" to { p ->
            p.pol(-48f, -4f, 0f, -44f, 48f, -4f, 36f, -4f, 36f, 44f, -36f, 44f, -36f, -4f)
            p.pol(-11f, 44f, -11f, 12f, 11f, 12f, 11f, 44f)   // la porte, en réserve
        },
        "porte" to { p ->
            p.pol(-30f, 46f, -30f, -30f, -22f, -42f, 22f, -42f, 30f, -30f, 30f, 46f)
            p.disque(16f, 6f, 5f)                              // la poignée
        },
        "fenetre" to { p ->
            p.pol(-36f, -40f, 36f, -40f, 36f, 40f, -36f, 40f)
            p.pol(-28f, -32f, -4f, -32f, -4f, -4f, -28f, -4f)
            p.pol(4f, -32f, 28f, -32f, 28f, -4f, 4f, -4f)
            p.pol(-28f, 4f, -4f, 4f, -4f, 32f, -28f, 32f)
            p.pol(4f, 4f, 28f, 4f, 28f, 32f, 4f, 32f)
        },
        "table" to { p ->
            p.pol(-46f, -18f, 46f, -18f, 46f, -6f, -46f, -6f)
            p.pol(-36f, -6f, -26f, -6f, -26f, 42f, -36f, 42f)
            p.pol(26f, -6f, 36f, -6f, 36f, 42f, 26f, 42f)
        },
        "banc" to { p ->
            p.pol(-46f, -34f, 46f, -34f, 46f, -24f, -46f, -24f)   // le dossier
            p.pol(-46f, 0f, 46f, 0f, 46f, 10f, -46f, 10f)         // l'assise
            p.pol(-38f, -34f, -30f, -34f, -30f, 42f, -38f, 42f)
            p.pol(30f, -34f, 38f, -34f, 38f, 42f, 30f, 42f)
        },
        "lit" to { p ->
            p.pol(-48f, -6f, -48f, -34f, -36f, -34f, -36f, -6f)   // la tête de lit
            p.pol(-48f, -6f, 48f, -6f, 48f, 18f, -48f, 18f)
            p.pol(-32f, -4f, -8f, -4f, -8f, 8f, -32f, 8f)         // l'oreiller
            p.pol(-46f, 18f, -38f, 18f, -38f, 38f, -46f, 38f)
            p.pol(38f, 18f, 46f, 18f, 46f, 38f, 38f, 38f)
        },
        "tiroir" to { p ->
            p.pol(-44f, -36f, 44f, -36f, 44f, 36f, -44f, 36f)
            p.pol(-34f, -26f, 34f, -26f, 34f, -2f, -34f, -2f)
            p.pol(-34f, 6f, 34f, 6f, 34f, 30f, -34f, 30f)
        },
        "coffre" to { p ->
            p.pol(-42f, -38f, 42f, -38f, 42f, 38f, -42f, 38f)
            p.disque(-6f, 0f, 18f)                                // le cadran
            p.disque(-6f, 0f, 6f)
            p.pol(26f, -8f, 32f, -8f, 32f, 8f, 26f, 8f)           // la poignée
        },
        "miroir" to { p ->
            p.ovale(-30f, -46f, 30f, 22f)
            p.ovale(-22f, -38f, 22f, 14f)
            p.pol(-6f, 22f, 6f, 22f, 6f, 38f, -6f, 38f)           // le pied
            p.pol(-22f, 38f, 22f, 38f, 22f, 46f, -22f, 46f)
        },
        "robinet" to { p ->
            p.pol(-10f, -34f, 10f, -34f, 10f, 6f, 26f, 6f, 26f, 20f, -10f, 20f)
            p.pol(-30f, -42f, 30f, -42f, 30f, -34f, -30f, -34f)   // le volant
            p.pol(-4f, 26f, 4f, 26f, 4f, 44f, -4f, 44f)           // le filet d'eau
        },
        "quille" to { p ->
            p.moveTo(0f, -46f)
            p.cubicTo(10f, -46f, 14f, -30f, 8f, -18f)
            p.cubicTo(2f, -8f, 20f, 6f, 20f, 24f)
            p.cubicTo(20f, 38f, 10f, 46f, 0f, 46f)
            p.cubicTo(-10f, 46f, -20f, 38f, -20f, 24f)
            p.cubicTo(-20f, 6f, -2f, -8f, -8f, -18f)
            p.cubicTo(-14f, -30f, -10f, -46f, 0f, -46f)
            p.close()
        },

        // ---- ce qu'on bâtit ----------------------------------------------

        "village" to { p ->
            p.pol(-46f, 12f, -28f, -6f, -10f, 12f, -16f, 12f, -16f, 42f, -40f, 42f, -40f, 12f)
            p.pol(-2f, 20f, 14f, 4f, 30f, 20f, 24f, 20f, 24f, 42f, 4f, 42f, 4f, 20f)
            p.pol(34f, 4f, 40f, -22f, 46f, 4f, 46f, 42f, 34f, 42f)   // le clocher
        },
        "ville" to { p ->
            p.pol(-46f, 46f, -46f, -6f, -26f, -6f, -26f, 46f)
            p.pol(-20f, 46f, -20f, -34f, -2f, -34f, -2f, 46f)
            p.pol(4f, 46f, 4f, -16f, 22f, -16f, 22f, 46f)
            p.pol(28f, 46f, 28f, -44f, 44f, -44f, 44f, 46f)
            p.pol(-40f, 4f, -32f, 4f, -32f, 12f, -40f, 12f)          // quelques fenêtres
            p.pol(-14f, -24f, -6f, -24f, -6f, -16f, -14f, -16f)
            p.pol(32f, -34f, 40f, -34f, 40f, -26f, 32f, -26f)
        },
        "eglise" to { p ->
            p.pol(-4f, -50f, 4f, -50f, 4f, -42f, 12f, -42f, 12f, -34f, 4f, -34f,
                  4f, -26f, -4f, -26f, -4f, -34f, -12f, -34f, -12f, -42f, -4f, -42f)
            p.pol(-16f, -22f, 0f, -26f, 16f, -22f, 16f, 46f, -16f, 46f)  // le clocher
            p.pol(16f, 6f, 44f, 6f, 44f, 46f, 16f, 46f)                  // la nef
            p.pol(-44f, 6f, -16f, 6f, -16f, 46f, -44f, 46f)
            p.pol(-8f, 20f, 8f, 20f, 8f, 46f, -8f, 46f)                  // le portail
        },
        "ecole" to { p ->
            p.pol(-46f, -8f, 0f, -34f, 46f, -8f, 46f, 44f, -46f, 44f)
            p.pol(-2f, -50f, 2f, -50f, 2f, -36f, -2f, -36f)               // le mât
            p.pol(2f, -50f, 22f, -44f, 2f, -38f)                          // le fanion
            p.pol(-30f, 4f, -14f, 4f, -14f, 20f, -30f, 20f)
            p.pol(14f, 4f, 30f, 4f, 30f, 20f, 14f, 20f)
            p.pol(-10f, 16f, 10f, 16f, 10f, 44f, -10f, 44f)
        },
        "pilier" to { p ->
            p.pol(-32f, -46f, 32f, -46f, 32f, -36f, -32f, -36f)
            p.pol(-24f, -36f, 24f, -36f, 20f, 34f, -20f, 34f)
            p.pol(-34f, 34f, 34f, 34f, 34f, 46f, -34f, 46f)
        },
        "poteau" to { p ->
            p.pol(-6f, -20f, 6f, -20f, 6f, 46f, -6f, 46f)
            p.pol(-34f, -46f, 34f, -46f, 34f, -24f, -34f, -24f)
            p.pol(-26f, -40f, 26f, -40f, 26f, -30f, -26f, -30f)
        },
        "tunnel" to { p ->
            p.moveTo(-46f, 44f)
            p.lineTo(-46f, 4f)
            p.cubicTo(-46f, -30f, 46f, -30f, 46f, 4f)
            p.lineTo(46f, 44f)
            p.close()
            p.moveTo(-24f, 44f)
            p.lineTo(-24f, 8f)
            p.cubicTo(-24f, -12f, 24f, -12f, 24f, 8f)
            p.lineTo(24f, 44f)
            p.close()
        },
        "grue" to { p ->
            p.pol(-8f, -20f, 4f, -20f, 4f, 44f, -8f, 44f)             // le mât
            p.pol(-46f, -32f, 44f, -32f, 44f, -22f, -46f, -22f)       // la flèche
            p.pol(-30f, -22f, -22f, -22f, -22f, -6f, -30f, -6f)       // le contrepoids
            p.pol(30f, -22f, 34f, -22f, 34f, 6f, 30f, 6f)             // le câble
            p.pol(24f, 6f, 40f, 6f, 40f, 18f, 24f, 18f)               // le crochet
            p.pol(-26f, 44f, 22f, 44f, 22f, 50f, -26f, 50f)
        },
        "moulin" to { p ->
            p.pol(-24f, 46f, -14f, -14f, 14f, -14f, 24f, 46f)
            p.pol(-4f, -46f, 4f, -46f, 4f, -18f, -4f, -18f)
            p.pol(-46f, -36f, -8f, -32f, -8f, -24f, -46f, -28f)       // les ailes
            p.pol(46f, -20f, 8f, -24f, 8f, -32f, 46f, -28f)
            p.pol(-20f, -50f, -12f, -50f, -8f, -20f, -16f, -20f)
            p.pol(20f, -50f, 12f, -50f, 8f, -20f, 16f, -20f)
        },
        "tente" to { p ->
            p.pol(-48f, 40f, 0f, -44f, 48f, 40f)
            p.pol(-14f, 40f, 0f, 2f, 14f, 40f)                        // l'ouverture
        },

        // ---- la table ------------------------------------------------------

        "pain" to { p ->
            p.moveTo(-46f, 26f)
            p.cubicTo(-46f, -18f, -30f, -34f, 0f, -34f)
            p.cubicTo(30f, -34f, 46f, -18f, 46f, 26f)
            p.cubicTo(46f, 34f, 40f, 36f, 32f, 36f)
            p.lineTo(-32f, 36f)
            p.cubicTo(-40f, 36f, -46f, 34f, -46f, 26f)
            p.close()
            for (i in -1..1) {
                val x = i * 22f
                p.pol(x - 12f, -18f, x + 2f, -22f, x + 5f, -13f, x - 9f, -9f)
            }
        },
        "fromage" to { p ->
            p.pol(-44f, 30f, -44f, -8f, 44f, -30f, 44f, 30f)
            p.disque(-20f, 12f, 7f)
            p.disque(4f, -2f, 5f)
            p.disque(24f, 14f, 6f)
        },
        "gateau" to { p ->
            p.pol(-40f, 44f, -40f, 4f, 40f, 4f, 40f, 44f)
            p.pol(-34f, 4f, -34f, -16f, 34f, -16f, 34f, 4f)
            p.pol(-3f, -16f, 3f, -16f, 3f, -34f, -3f, -34f)           // la bougie
            p.moveTo(0f, -50f)
            p.cubicTo(7f, -44f, 6f, -36f, 0f, -36f)
            p.cubicTo(-6f, -36f, -7f, -44f, 0f, -50f)
            p.close()
            p.pol(-40f, 16f, 40f, 16f, 40f, 22f, -40f, 22f)
        },
        "pizza" to { p ->
            p.moveTo(0f, -44f)
            p.cubicTo(30f, -44f, 46f, -18f, 40f, 12f)
            p.lineTo(-40f, 12f)
            p.cubicTo(-46f, -18f, -30f, -44f, 0f, -44f)
            p.close()
            p.pol(-44f, 12f, 44f, 12f, 44f, 30f, -44f, 30f)           // la croûte
            p.disque(-16f, -14f, 7f)
            p.disque(12f, -20f, 6f)
            p.disque(16f, -2f, 7f)
        },
        "fruits" to { p ->
            p.moveTo(-14f, -12f)                                       // la poire
            p.cubicTo(-4f, -12f, 2f, 0f, 2f, 16f)
            p.cubicTo(2f, 34f, -8f, 44f, -20f, 44f)
            p.cubicTo(-32f, 44f, -42f, 34f, -42f, 16f)
            p.cubicTo(-42f, 0f, -34f, -12f, -26f, -14f)
            p.cubicTo(-26f, -26f, -24f, -34f, -20f, -40f)
            p.lineTo(-14f, -38f)
            p.cubicTo(-18f, -32f, -20f, -24f, -20f, -14f)
            p.close()
            p.moveTo(20f, -8f)                                         // la pomme
            p.cubicTo(34f, -8f, 44f, 4f, 44f, 20f)
            p.cubicTo(44f, 34f, 34f, 44f, 22f, 44f)
            p.cubicTo(10f, 44f, 2f, 34f, 2f, 20f)
            p.cubicTo(2f, 4f, 8f, -8f, 20f, -8f)
            p.close()
            p.pol(20f, -10f, 26f, -10f, 26f, -26f, 20f, -26f)
        },
        "lait" to { p ->
            p.pol(-26f, -18f, 26f, -18f, 32f, 44f, -32f, 44f)          // le pot
            p.pol(-16f, -34f, 16f, -34f, 18f, -18f, -18f, -18f)        // le col
            p.pol(-20f, -42f, 20f, -42f, 20f, -34f, -20f, -34f)        // le couvercle
            p.pol(26f, -8f, 42f, -2f, 42f, 18f, 26f, 24f)              // l'anse
            p.pol(28f, 2f, 36f, 6f, 36f, 12f, 28f, 16f)
        },
        "bouteille" to { p ->
            p.pol(-8f, -46f, 8f, -46f, 8f, -42f, -8f, -42f)
            p.moveTo(-8f, -42f)
            p.lineTo(8f, -42f)
            p.lineTo(8f, -20f)
            p.cubicTo(8f, -8f, 22f, -4f, 22f, 12f)
            p.lineTo(22f, 40f)
            p.cubicTo(22f, 45f, 18f, 46f, 12f, 46f)
            p.lineTo(-12f, 46f)
            p.cubicTo(-18f, 46f, -22f, 45f, -22f, 40f)
            p.lineTo(-22f, 12f)
            p.cubicTo(-22f, -4f, -8f, -8f, -8f, -20f)
            p.close()
            p.pol(-18f, 8f, 18f, 8f, 18f, 26f, -18f, 26f)              // l'étiquette
        },
        "vin" to { p ->
            p.moveTo(-26f, -42f)
            p.lineTo(26f, -42f)
            p.cubicTo(26f, -10f, 14f, 2f, 4f, 4f)
            p.lineTo(4f, 34f)
            p.lineTo(22f, 34f)
            p.lineTo(22f, 44f)
            p.lineTo(-22f, 44f)
            p.lineTo(-22f, 34f)
            p.lineTo(-4f, 34f)
            p.lineTo(-4f, 4f)
            p.cubicTo(-14f, 2f, -26f, -10f, -26f, -42f)
            p.close()
            p.pol(-22f, -22f, 22f, -22f, 20f, -12f, -20f, -12f)        // le vin
        },
        "cafe" to { p ->
            p.pol(-34f, -12f, 22f, -12f, 18f, 28f, -30f, 28f)          // la tasse
            p.moveTo(22f, -6f)
            p.cubicTo(44f, -6f, 46f, 20f, 24f, 20f)
            p.lineTo(24f, 10f)
            p.cubicTo(34f, 10f, 34f, 4f, 22f, 4f)
            p.close()
            p.pol(-42f, 32f, 34f, 32f, 34f, 42f, -42f, 42f)            // la soucoupe
            p.pol(-16f, -44f, -10f, -44f, -10f, -22f, -16f, -22f)      // la vapeur
            p.pol(2f, -44f, 8f, -44f, 8f, -22f, 2f, -22f)
        },
        "couteau" to { p ->
            p.moveTo(-44f, -18f)
            p.cubicTo(-20f, -30f, 4f, -30f, 14f, -22f)
            p.lineTo(14f, -6f)
            p.lineTo(-44f, -6f)
            p.close()
            p.pol(14f, -22f, 44f, -20f, 44f, -8f, 14f, -6f)            // le manche
        },
        "eau" to { p ->
            for (i in 0..2) {
                val y = -26f + i * 26f
                p.moveTo(-46f, y)
                p.cubicTo(-30f, y - 13f, -16f, y + 13f, 0f, y)
                p.cubicTo(16f, y - 13f, 30f, y + 13f, 46f, y)
                p.lineTo(46f, y + 9f)
                p.cubicTo(30f, y + 22f, 16f, y - 4f, 0f, y + 9f)
                p.cubicTo(-16f, y + 22f, -30f, y - 4f, -46f, y + 9f)
                p.close()
            }
        },

        // ---- le vivant -----------------------------------------------------

        "arbre" to { p ->
            // La couronne se ferme sur une base droite à y = 8, et le tronc part
            // de cette même ligne : aboutés, jamais superposés — sinon la règle
            // pair-impair creuse le tronc dans la couronne.
            p.moveTo(-32f, 8f)
            p.cubicTo(-46f, 2f, -46f, -22f, -28f, -30f)
            p.cubicTo(-24f, -48f, 8f, -52f, 20f, -38f)
            p.cubicTo(42f, -36f, 48f, -12f, 32f, -2f)
            p.cubicTo(36f, 4f, 35f, 8f, 30f, 8f)
            p.close()
            p.pol(-8f, 8f, 8f, 8f, 11f, 46f, -11f, 46f)
        },
        "coeur" to { p ->
            p.moveTo(0f, 44f)
            p.cubicTo(-46f, 14f, -46f, -20f, -24f, -32f)
            p.cubicTo(-10f, -40f, 0f, -30f, 0f, -20f)
            p.cubicTo(0f, -30f, 10f, -40f, 24f, -32f)
            p.cubicTo(46f, -20f, 46f, 14f, 0f, 44f)
            p.close()
        },
        "poisson" to { p ->
            p.moveTo(46f, 0f)
            p.cubicTo(30f, -26f, -6f, -30f, -22f, -14f)
            p.lineTo(-46f, -26f); p.lineTo(-36f, 0f); p.lineTo(-46f, 26f); p.lineTo(-22f, 14f)
            p.cubicTo(-6f, 30f, 30f, 26f, 46f, 0f)
            p.close()
            p.disque(22f, -6f, 4.5f)
        },
        "chat" to { p ->
            p.pol(-34f, -14f, -30f, -46f, -8f, -28f)
            p.pol(34f, -14f, 30f, -46f, 8f, -28f)
            p.moveTo(0f, -32f)
            p.cubicTo(26f, -32f, 38f, -14f, 38f, 4f)
            p.cubicTo(38f, 26f, 22f, 40f, 0f, 40f)
            p.cubicTo(-22f, 40f, -38f, 26f, -38f, 4f)
            p.cubicTo(-38f, -14f, -26f, -32f, 0f, -32f)
            p.close()
        },
        "chien" to { p ->
            p.moveTo(-26f, -30f)                                       // l'oreille tombante
            p.cubicTo(-44f, -28f, -46f, 6f, -32f, 14f)
            p.lineTo(-24f, -4f)
            p.close()
            p.moveTo(26f, -30f)
            p.cubicTo(44f, -28f, 46f, 6f, 32f, 14f)
            p.lineTo(24f, -4f)
            p.close()
            p.moveTo(0f, -34f)
            p.cubicTo(22f, -34f, 32f, -18f, 32f, 0f)
            p.cubicTo(32f, 14f, 26f, 24f, 16f, 30f)
            p.lineTo(16f, 42f)
            p.lineTo(-16f, 42f)
            p.lineTo(-16f, 30f)
            p.cubicTo(-26f, 24f, -32f, 14f, -32f, 0f)
            p.cubicTo(-32f, -18f, -22f, -34f, 0f, -34f)
            p.close()
            p.disque(0f, 34f, 5f)                                      // la truffe
        },
        "lion" to { p ->
            // La crinière : douze pointes autour du mufle, ce qui le distingue
            // du chien et du chat à la vignette, où la fourrure ne se lit pas.
            for (i in 0..11) {
                val a = i * Math.PI.toFloat() / 6f
                val c = Math.cos(a.toDouble()).toFloat()
                val s = Math.sin(a.toDouble()).toFloat()
                p.pol(c * 26f - s * 10f, s * 26f + c * 10f,
                      c * 48f, s * 48f,
                      c * 26f + s * 10f, s * 26f - c * 10f)
            }
            p.disque(0f, 0f, 28f)
            p.disque(-11f, -6f, 4f)
            p.disque(11f, -6f, 4f)
            p.pol(0f, 4f, 8f, 12f, -8f, 12f)
        },
        "cheval" to { p ->
            p.moveTo(-30f, 46f)                                        // l'encolure
            p.cubicTo(-30f, 10f, -20f, -8f, -4f, -18f)
            p.lineTo(-4f, -34f)
            p.lineTo(6f, -22f)
            p.cubicTo(24f, -22f, 40f, -12f, 44f, 2f)
            p.lineTo(24f, 10f)
            p.cubicTo(20f, 2f, 10f, -2f, 2f, 2f)
            p.cubicTo(-6f, 12f, -8f, 26f, -8f, 46f)
            p.close()
            p.pol(-22f, -14f, -14f, -44f, -6f, -20f)                   // la crinière
            p.disque(14f, -8f, 3.5f)
        },
        "boeuf" to { p ->
            p.moveTo(-32f, -22f)                                       // les cornes
            p.cubicTo(-48f, -34f, -46f, -8f, -34f, -2f)
            p.lineTo(-30f, -12f)
            p.close()
            p.moveTo(32f, -22f)
            p.cubicTo(48f, -34f, 46f, -8f, 34f, -2f)
            p.lineTo(30f, -12f)
            p.close()
            p.moveTo(0f, -28f)
            p.cubicTo(20f, -28f, 30f, -16f, 30f, 2f)
            p.cubicTo(30f, 24f, 18f, 40f, 0f, 40f)
            p.cubicTo(-18f, 40f, -30f, 24f, -30f, 2f)
            p.cubicTo(-30f, -16f, -20f, -28f, 0f, -28f)
            p.close()
            p.disque(-9f, 22f, 4f)
            p.disque(9f, 22f, 4f)
        },
        "chevreuil" to { p ->
            p.pol(-14f, -18f, -18f, -40f, -30f, -34f, -26f, -46f, -14f, -46f, -10f, -24f)
            p.pol(14f, -18f, 18f, -40f, 30f, -34f, 26f, -46f, 14f, -46f, 10f, -24f)
            p.moveTo(0f, -22f)
            p.cubicTo(16f, -22f, 24f, -8f, 24f, 8f)
            p.cubicTo(24f, 28f, 14f, 44f, 0f, 44f)
            p.cubicTo(-14f, 44f, -24f, 28f, -24f, 8f)
            p.cubicTo(-24f, -8f, -16f, -22f, 0f, -22f)
            p.close()
            p.disque(-9f, 0f, 3.5f)
            p.disque(9f, 0f, 3.5f)
        },
        "merle" to { p ->
            p.moveTo(-4f, -34f)                                        // la tête et le corps
            p.cubicTo(12f, -34f, 22f, -22f, 22f, -8f)
            p.cubicTo(38f, 0f, 46f, 16f, 40f, 34f)
            p.lineTo(-16f, 34f)
            p.cubicTo(-32f, 34f, -40f, 20f, -34f, 4f)
            p.cubicTo(-30f, -8f, -22f, -18f, -20f, -22f)
            p.cubicTo(-18f, -30f, -12f, -34f, -4f, -34f)
            p.close()
            p.pol(-20f, -24f, -46f, -18f, -20f, -12f)                  // le bec
            p.pol(28f, 22f, 46f, 40f, 22f, 34f)                        // la queue
            p.disque(-6f, -22f, 3.5f)
            p.pol(-8f, 34f, -4f, 34f, -4f, 44f, -12f, 44f)
            p.pol(10f, 34f, 14f, 34f, 14f, 44f, 6f, 44f)
        },
        "main" to { p ->
            p.pol(-30f, 46f, -30f, 4f, -22f, 4f, -22f, 46f)            // la paume
            p.arrondi(-30f, -2f, 30f, 46f, 8f)
            p.arrondi(-26f, -30f, -14f, 6f, 6f)
            p.arrondi(-11f, -42f, 1f, 6f, 6f)
            p.arrondi(4f, -38f, 16f, 6f, 6f)
            p.arrondi(19f, -26f, 31f, 6f, 6f)
            p.moveTo(-30f, 10f)                                        // le pouce
            p.cubicTo(-44f, 4f, -50f, 18f, -40f, 26f)
            p.lineTo(-30f, 26f)
            p.close()
        },
        "pied" to { p ->
            p.moveTo(-18f, -44f)
            p.cubicTo(2f, -44f, 12f, -30f, 14f, -10f)
            p.cubicTo(16f, 8f, 30f, 18f, 34f, 30f)
            p.cubicTo(38f, 42f, 28f, 46f, 12f, 46f)
            p.lineTo(-14f, 46f)
            p.cubicTo(-30f, 46f, -36f, 38f, -34f, 22f)
            p.cubicTo(-32f, 4f, -34f, -44f, -18f, -44f)
            p.close()
        },
        "tete" to { p ->
            p.moveTo(-6f, -46f)                                        // le profil
            p.cubicTo(18f, -46f, 30f, -30f, 30f, -10f)
            p.cubicTo(30f, 2f, 26f, 8f, 32f, 14f)
            p.cubicTo(36f, 18f, 30f, 22f, 24f, 22f)
            p.cubicTo(24f, 34f, 20f, 40f, 8f, 42f)
            p.lineTo(8f, 46f)
            p.lineTo(-16f, 46f)
            p.lineTo(-16f, 20f)
            p.cubicTo(-30f, 12f, -34f, -12f, -24f, -30f)
            p.cubicTo(-20f, -40f, -14f, -46f, -6f, -46f)
            p.close()
            p.disque(6f, -14f, 4f)
        },
        "dent" to { p ->
            p.moveTo(0f, -40f)
            p.cubicTo(20f, -40f, 34f, -30f, 34f, -12f)
            p.cubicTo(34f, 4f, 26f, 16f, 22f, 34f)
            p.cubicTo(20f, 44f, 8f, 46f, 6f, 32f)
            p.cubicTo(4f, 18f, -4f, 18f, -6f, 32f)
            p.cubicTo(-8f, 46f, -20f, 44f, -22f, 34f)
            p.cubicTo(-26f, 16f, -34f, 4f, -34f, -12f)
            p.cubicTo(-34f, -30f, -20f, -40f, 0f, -40f)
            p.close()
        },
        "ange" to { p ->
            p.ovale(-12f, -46f, 12f, -22f)                             // la tête
            p.ovale(-16f, -52f, 16f, -40f)                             // l'auréole
            p.ovale(-11f, -49f, 11f, -43f)
            p.pol(-18f, -18f, 18f, -18f, 26f, 46f, -26f, 46f)          // la robe
            p.moveTo(-18f, -14f)                                       // les ailes
            p.cubicTo(-40f, -22f, -50f, -4f, -44f, 16f)
            p.cubicTo(-34f, 8f, -24f, 4f, -18f, 4f)
            p.close()
            p.moveTo(18f, -14f)
            p.cubicTo(40f, -22f, 50f, -4f, 44f, 16f)
            p.cubicTo(34f, 8f, 24f, 4f, 18f, 4f)
            p.close()
        },

        // ---- le ciel -------------------------------------------------------

        "soleil" to { p ->
            p.disque(0f, 0f, 21f)
            for (i in 0..11) {
                val a = i * Math.PI.toFloat() / 6f
                val c = Math.cos(a.toDouble()).toFloat()
                val s = Math.sin(a.toDouble()).toFloat()
                val r2 = if (i % 2 == 1) 40f else 47f
                p.pol(c * 28f - s * 4.5f, s * 28f + c * 4.5f,
                      c * r2, s * r2,
                      c * 28f + s * 4.5f, s * 28f - c * 4.5f)
            }
        },
        "lune" to { p ->
            // Deux disques et la règle pair-impair : le recouvrement se creuse,
            // et le croissant sort sans qu'on ait à tracer deux arcs.
            p.disque(-4f, 0f, 44f)
            p.disque(18f, -14f, 40f)
        },
        "pluie" to { p ->
            p.moveTo(-40f, 2f)
            p.cubicTo(-40f, -14f, -26f, -22f, -14f, -18f)
            p.cubicTo(-8f, -34f, 16f, -36f, 22f, -20f)
            p.cubicTo(38f, -22f, 44f, -6f, 34f, 2f)
            p.close()
            for (i in -1..1) {
                val x = i * 24f
                p.moveTo(x, 14f)
                p.cubicTo(x + 10f, 26f, x + 8f, 40f, x, 40f)
                p.cubicTo(x - 8f, 40f, x - 10f, 26f, x, 14f)
                p.close()
            }
        },

        // ---- ce qui roule et ce qui vole -----------------------------------

        "voiture" to { p ->
            p.moveTo(-46f, 20f)
            p.lineTo(-42f, -2f)
            p.lineTo(-28f, -4f)
            p.lineTo(-16f, -24f)
            p.lineTo(20f, -24f)
            p.lineTo(30f, -4f)
            p.lineTo(46f, 0f)
            p.lineTo(46f, 20f)
            p.close()
            p.pol(-12f, -18f, 0f, -18f, 0f, -6f, -20f, -6f)            // les vitres
            p.pol(6f, -18f, 17f, -18f, 24f, -6f, 6f, -6f)
            p.disque(-26f, 22f, 12f)
            p.disque(26f, 22f, 12f)
            p.disque(-26f, 22f, 5f)
            p.disque(26f, 22f, 5f)
        },
        "velo" to { p ->
            p.disque(-28f, 14f, 24f)
            p.disque(-28f, 14f, 18f)
            p.disque(28f, 14f, 24f)
            p.disque(28f, 14f, 18f)
            p.pol(-28f, 11f, -2f, -14f, 4f, -8f, -25f, 17f)            // le cadre
            p.pol(-2f, -14f, 28f, 11f, 25f, 17f, -8f, -8f)
            p.pol(-28f, 11f, 28f, 11f, 28f, 17f, -28f, 17f)
            p.pol(-8f, -22f, 8f, -22f, 8f, -16f, -8f, -16f)            // la selle
            p.pol(24f, -28f, 40f, -28f, 40f, -22f, 24f, -22f)          // le guidon
            p.pol(26f, -26f, 32f, -26f, 32f, 12f, 26f, 12f)
        },
        "avion" to { p ->
            p.moveTo(0f, -46f)
            p.cubicTo(8f, -46f, 12f, -34f, 12f, -18f)
            p.lineTo(46f, 6f)
            p.lineTo(46f, 18f)
            p.lineTo(12f, 8f)
            p.lineTo(12f, 28f)
            p.lineTo(24f, 38f)
            p.lineTo(24f, 46f)
            p.lineTo(0f, 40f)
            p.lineTo(-24f, 46f)
            p.lineTo(-24f, 38f)
            p.lineTo(-12f, 28f)
            p.lineTo(-12f, 8f)
            p.lineTo(-46f, 18f)
            p.lineTo(-46f, 6f)
            p.lineTo(-12f, -18f)
            p.cubicTo(-12f, -34f, -8f, -46f, 0f, -46f)
            p.close()
        },
        "helicoptere" to { p ->
            p.pol(-44f, -34f, 44f, -34f, 44f, -28f, -44f, -28f)        // le rotor
            p.pol(-3f, -28f, 3f, -28f, 3f, -16f, -3f, -16f)
            p.moveTo(-22f, -16f)                                       // la cabine
            p.cubicTo(2f, -16f, 16f, -6f, 18f, 6f)
            p.lineTo(44f, 10f)
            p.lineTo(44f, 20f)
            p.lineTo(16f, 20f)
            p.cubicTo(10f, 26f, -2f, 28f, -14f, 28f)
            p.cubicTo(-32f, 28f, -40f, 18f, -40f, 6f)
            p.cubicTo(-40f, -6f, -32f, -16f, -22f, -16f)
            p.close()
            p.pol(38f, 6f, 48f, 2f, 48f, 26f, 38f, 22f)                // le rotor de queue
            p.pol(-30f, 30f, 8f, 30f, 8f, 38f, -30f, 38f)              // les patins
            p.pol(-22f, 24f, -16f, 24f, -16f, 34f, -22f, 34f)
        },
        "bateau" to { p ->
            p.moveTo(-46f, 20f)                                        // la coque
            p.lineTo(46f, 20f)
            p.lineTo(36f, 44f)
            p.lineTo(-36f, 44f)
            p.close()
            p.pol(-3f, -46f, 3f, -46f, 3f, 16f, -3f, 16f)              // le mât
            p.pol(6f, -40f, 38f, 14f, 6f, 14f)                         // les voiles
            p.pol(-6f, -34f, -6f, 14f, -32f, 14f)
        },

        // ---- ce qui se lit et ce qui se dit --------------------------------

        "livre" to { p ->
            p.moveTo(0f, -26f)
            p.cubicTo(-14f, -36f, -34f, -38f, -48f, -34f)
            p.lineTo(-48f, 30f)
            p.cubicTo(-34f, 26f, -14f, 28f, 0f, 38f)
            p.cubicTo(14f, 28f, 34f, 26f, 48f, 30f)
            p.lineTo(48f, -34f)
            p.cubicTo(34f, -38f, 14f, -36f, 0f, -26f)
            p.close()
            p.pol(-3f, -20f, 3f, -20f, 3f, 34f, -3f, 34f)
        },
        "lettre" to { p ->
            p.pol(-46f, -30f, 46f, -30f, 46f, 30f, -46f, 30f)
            p.pol(-38f, -22f, 38f, -22f, 0f, 8f)                       // le rabat
        },
        "carte" to { p ->
            p.arrondi(-46f, -30f, 46f, 30f, 6f)
            p.pol(-46f, -18f, 46f, -18f, 46f, -6f, -46f, -6f)          // la bande
            p.pol(-36f, 6f, -4f, 6f, -4f, 12f, -36f, 12f)
            p.pol(-36f, 18f, 12f, 18f, 12f, 24f, -36f, 24f)
        },
        "photo" to { p ->
            p.pol(-42f, -38f, 42f, -38f, 42f, 44f, -42f, 44f)          // le cadre
            p.pol(-34f, -30f, 34f, -30f, 34f, 20f, -34f, 20f)
            p.pol(-34f, 20f, -12f, -12f, 2f, 8f, 12f, -4f, 34f, 20f)   // le paysage
            p.disque(16f, -18f, 7f)
        },
        "ticket" to { p ->
            p.moveTo(-46f, -26f)
            p.lineTo(46f, -26f)
            p.lineTo(46f, -6f)
            p.cubicTo(38f, -6f, 38f, 6f, 46f, 6f)
            p.lineTo(46f, 26f)
            p.lineTo(-46f, 26f)
            p.lineTo(-46f, 6f)
            p.cubicTo(-38f, 6f, -38f, -6f, -46f, -6f)
            p.close()
            p.pol(-30f, -14f, 10f, -14f, 10f, -8f, -30f, -8f)
            p.pol(-30f, 4f, -4f, 4f, -4f, 10f, -30f, 10f)
        },
        "loupe" to { p ->
            p.disque(-8f, -14f, 32f)
            p.disque(-8f, -14f, 24f)
            p.pol(8f, 12f, 22f, 2f, 46f, 34f, 34f, 44f)
        },
        "lunettes" to { p ->
            p.disque(-24f, 4f, 22f)
            p.disque(-24f, 4f, 15f)
            p.disque(24f, 4f, 22f)
            p.disque(24f, 4f, 15f)
            p.pol(-6f, -2f, 6f, -2f, 6f, 4f, -6f, 4f)                  // le pont
            p.pol(-46f, -14f, -38f, -6f, -42f, -2f, -50f, -10f)        // les branches
            p.pol(46f, -14f, 38f, -6f, 42f, -2f, 50f, -10f)
        },
        "micro" to { p ->
            p.arrondi(-16f, -46f, 16f, 4f, 16f)
            p.moveTo(-28f, -6f)                                        // l'arceau
            p.cubicTo(-28f, 18f, 28f, 18f, 28f, -6f)
            p.lineTo(20f, -6f)
            p.cubicTo(20f, 8f, -20f, 8f, -20f, -6f)
            p.close()
            p.pol(-4f, 14f, 4f, 14f, 4f, 38f, -4f, 38f)
            p.pol(-20f, 38f, 20f, 38f, 20f, 46f, -20f, 46f)
        },
        "camera" to { p ->
            p.arrondi(-44f, -20f, 16f, 26f, 6f)
            p.pol(20f, -14f, 44f, -28f, 44f, 34f, 20f, 20f)            // l'objectif en saillie
            p.disque(-14f, 3f, 14f)
            p.disque(-14f, 3f, 7f)
            p.pol(-34f, -30f, -14f, -30f, -14f, -20f, -34f, -20f)      // la bobine
            p.pol(-30f, 26f, -18f, 26f, -18f, 38f, -30f, 38f)
        },
        "telephone" to { p ->
            p.arrondi(-26f, -46f, 26f, 46f, 8f)
            p.pol(-19f, -32f, 19f, -32f, 19f, 30f, -19f, 30f)          // l'écran
            p.pol(-7f, -42f, 7f, -42f, 7f, -37f, -7f, -37f)            // l'écouteur
            p.disque(0f, 38f, 5f)
        },
        "antenne" to { p ->
            p.pol(-6f, -20f, 6f, -20f, 12f, 46f, -12f, 46f)            // le pylône
            p.pol(-16f, 4f, 16f, 4f, 18f, 12f, -18f, 12f)
            p.disque(0f, -30f, 8f)
            p.moveTo(-20f, -46f)                                       // les ondes
            p.cubicTo(-32f, -34f, -32f, -18f, -20f, -6f)
            p.lineTo(-27f, -1f)
            p.cubicTo(-42f, -16f, -42f, -36f, -27f, -51f)
            p.close()
            p.moveTo(20f, -46f)
            p.cubicTo(32f, -34f, 32f, -18f, 20f, -6f)
            p.lineTo(27f, -1f)
            p.cubicTo(42f, -16f, 42f, -36f, 27f, -51f)
            p.close()
        },
        "drapeau" to { p ->
            p.pol(-38f, -48f, -30f, -48f, -30f, 48f, -38f, 48f)        // la hampe
            p.moveTo(-30f, -44f)
            p.cubicTo(-6f, -54f, 18f, -34f, 42f, -44f)
            p.lineTo(42f, 0f)
            p.cubicTo(18f, 10f, -6f, -10f, -30f, 0f)
            p.close()
        },
        "horloge" to { p ->
            p.disque(0f, 2f, 42f)
            p.disque(0f, 2f, 34f)
            p.pol(-4f, 2f, 4f, 2f, 4f, -24f, -4f, -24f)                // les aiguilles
            p.pol(-4f, -2f, -4f, 6f, 22f, 6f, 22f, -2f)
            p.disque(0f, 2f, 5f)
            p.pol(-10f, -48f, 10f, -48f, 10f, -40f, -10f, -40f)        // le remontoir
        },

        // ---- l'outil et le métal -------------------------------------------

        "marteau" to { p ->
            p.pol(-44f, -34f, -4f, -34f, -4f, -8f, -44f, -8f)
            p.pol(-22f, -8f, -10f, -8f, -4f, 44f, -20f, 44f)
            p.pol(-4f, -30f, 30f, -30f, 44f, -12f, 30f, -12f, -4f, -12f)
        },
        "or" to { p ->
            p.pol(-30f, -34f, 30f, -34f, 38f, -12f, -38f, -12f)        // trois lingots
            p.pol(-46f, -6f, 14f, -6f, 22f, 16f, -54f, 16f)
            p.pol(-14f, -6f, 46f, -6f, 54f, 16f, -22f, 16f)
            p.pol(-38f, 22f, 38f, 22f, 46f, 44f, -46f, 44f)
        },
        "casque" to { p ->
            p.moveTo(-44f, 18f)
            p.cubicTo(-44f, -26f, -22f, -44f, 0f, -44f)
            p.cubicTo(22f, -44f, 44f, -26f, 44f, 18f)
            p.lineTo(30f, 18f)
            p.cubicTo(30f, -18f, 16f, -30f, 0f, -30f)
            p.cubicTo(-16f, -30f, -30f, -18f, -30f, 18f)
            p.close()
            p.pol(-48f, 18f, 48f, 18f, 48f, 30f, -48f, 30f)            // le bord
            p.pol(-4f, -44f, 4f, -44f, 4f, 16f, -4f, 16f)              // la crête
        },
        "jean" to { p ->
            p.moveTo(-30f, -42f)
            p.lineTo(30f, -42f)
            p.lineTo(34f, 46f)
            p.lineTo(8f, 46f)
            p.lineTo(0f, -6f)
            p.lineTo(-8f, 46f)
            p.lineTo(-34f, 46f)
            p.close()
            p.pol(-30f, -42f, 30f, -42f, 30f, -32f, -30f, -32f)        // la ceinture
            p.pol(-26f, -26f, -14f, -26f, -16f, -12f, -26f, -12f)      // la poche
        },
        "chaussure" to { p ->
            p.moveTo(-42f, 16f)
            p.lineTo(-42f, -18f)
            p.lineTo(-20f, -18f)
            p.lineTo(-14f, 0f)
            p.cubicTo(4f, 4f, 30f, 10f, 42f, 18f)
            p.lineTo(42f, 30f)
            p.lineTo(-42f, 30f)
            p.close()
            p.pol(-42f, 30f, 44f, 30f, 44f, 40f, -42f, 40f)            // la semelle
            p.pol(-36f, -12f, -22f, -12f, -20f, -4f, -36f, -4f)
        },
        "cigarette" to { p ->
            p.pol(-46f, 10f, 30f, 10f, 30f, 30f, -46f, 30f)
            p.pol(30f, 10f, 46f, 10f, 46f, 30f, 30f, 30f)              // le filtre
            p.moveTo(-38f, 2f)                                         // la fumée
            p.cubicTo(-46f, -12f, -30f, -20f, -38f, -34f)
            p.lineTo(-30f, -38f)
            p.cubicTo(-22f, -22f, -38f, -14f, -30f, -2f)
            p.close()
        },
    )
}
