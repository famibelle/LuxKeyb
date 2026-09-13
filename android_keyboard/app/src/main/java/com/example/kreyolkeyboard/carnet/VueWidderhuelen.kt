package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.kreyolkeyboard.crossword.CrosswordData

/**
 * L'écran d'une session de révision.
 *
 * Posé sur la vue racine du carnet, comme le voile de `montrerCarte`, et non
 * dans un second `DialogFragment` : la révision est un mode du carnet, pas une
 * destination de plus. La barre d'onglets en porte quatre et `REAL_COUNT`
 * pilote le modulo du pager cyclique ; le hub, lui, défile déjà depuis sa
 * septième carte de jeu.
 *
 * ## La carte a deux faces, et l'écran est bâti autour d'elles
 *
 * L'écran était un flux : une consigne, un énoncé, un pavé, un bouton, le tout
 * dans un `ScrollView`, et la carte n'arrivait qu'à la révélation. Le
 * retournement était donc un faux — la carte partait de `rotationY = -85°`
 * sans que rien n'occupe ces -85°.
 *
 * Il est maintenant vrai. La question est écrite sur [DosRevision], le
 * retournement se joue en deux temps comme celui de la pochette, et le recto
 * est la carte du carnet. D'où la disposition en trois bandes :
 *
 * - l'en-tête, fixe ;
 * - **la scène**, qui prend tout ce qui reste et ne contient que le carton ;
 * - **le bas**, qui porte ce qui n'est pas la carte : le pavé, les boutons.
 *
 * C'est la scène qui décide de la taille du carton, via
 * [Carton.ajusteALaHauteur] : une question tapée laisse moins de place qu'une
 * question de reconnaissance, et une carte révélée, dont le pavé a disparu,
 * en retrouve. Le carton grandit donc au moment où il est de profil, c'est-
 * à-dire invisible — même rapport, même rayon, même ombre, seulement vu de
 * plus près.
 *
 * ## Trois choix d'écran qui ne vont pas de soi
 *
 * - **Le pavé est celui de Kräizwuert, avec une touche `⇧` en plus.** Kräizwuert
 *   laisse volontairement vide l'emplacement de la majuscule, sa grille étant
 *   tout en capitales ; ici la majuscule de substantif est **l'objet de la
 *   question**, donc elle doit pouvoir être produite. La touche reprend
 *   exactement la place qu'elle occupe sur le clavier, et les rangées gardent
 *   leur alignement. Voir [CrosswordData.RANGEES] pour le reste du
 *   raisonnement, qui vaut ici mot pour mot : le jeu entraîne les positions de
 *   doigts dont on se sert en écrivant un message.
 * - **L'ardoise est sur la carte**, à l'emplacement du panneau de texte du
 *   recto ([Ornement.PANNEAU_TEXTE]). Ce que le joueur tape apparaît donc
 *   exactement là où le sens du mot l'attend de l'autre côté du carton — et
 *   ça libère du même coup la hauteur qu'une ardoise séparée prenait au pavé.
 * - **La carte révélée est celle du carnet** ([CarteCarnet.complete]), pas un
 *   recto écrit pour l'occasion. C'est la même carte que le joueur a gagnée,
 *   avec sa glose, sa phrase, sa famille et sa rareté : la révision montre la
 *   collection, elle ne la double pas.
 */
class VueWidderhuelen(
    private val hote: ViewGroup,
    private val paquet: List<ContenuCarte>,
    private val monteesParLeClavier: List<String>,
    private val surNotation: (forme: String, verdict: Verdict) -> Unit,
    private val surFin: () -> Unit
) {

    private val ctx: Context = hote.context
    private val d = ctx.resources.displayMetrics.density
    private val session = SessionWidderhuelen(paquet)

    private val racine = FrameLayout(ctx).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(Color.parseColor("#F5F5F5"))
        isClickable = true
    }
    private val colonne = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
    }

    /**
     * La scène : le carton, et rien d'autre.
     *
     * Elle ne bouge pas d'une question à l'autre, ce qui est ce qui permet
     * d'y armer [Inclinaison] une seule fois : le suivi s'applique à la scène,
     * le retournement à la carte qui est dedans, et les deux se composent sans
     * s'écrire l'un sur l'autre.
     */
    private val scene = FrameLayout(ctx)
    private val bas = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
    }
    private lateinit var tvProgres: TextView

    private var saisie = StringBuilder()
    private var majuscule = false
    private var ardoise: TextView? = null
    private var dos: DosRevision? = null

    private fun dp(v: Float) = (v * d).toInt()

    /**
     * La hauteur des touches, selon ce que l'écran peut céder.
     *
     * Le carton et un pavé de quatre rangées se disputent la même colonne, et
     * c'est le carton qui perd : il prend ce qui reste. Sous 620 dp de haut,
     * ce reste devient trop petit pour que la question s'y lise, et quatre
     * millimètres pris à chaque rangée valent mieux qu'un énoncé de six
     * pixels. Au-dessus, rien ne change.
     */
    private val hauteurTouche =
        if (ctx.resources.displayMetrics.heightPixels / d < 620f) 5f else 9f

    /**
     * Ouvre la session.
     *
     * File vide **et** rien monté par le clavier : il n'y a rien à dire, on ne
     * s'affiche pas. File vide mais des cartes montées par la frappe : on
     * s'ouvre directement sur le bilan, sinon le joueur ne saurait pas pourquoi
     * son paquet a fondu.
     */
    fun ouvrir() {
        if (session.fini && monteesParLeClavier.isEmpty()) {
            surFin()
            return
        }
        colonne.addView(enTete())
        scene.setPadding(dp(16f), dp(12f), dp(16f), dp(8f))
        colonne.addView(scene, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        bas.setPadding(dp(16f), dp(4f), dp(16f), dp(16f))
        colonne.addView(bas, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        racine.addView(colonne, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        hote.addView(racine)
        racine.alpha = 0f
        racine.animate().alpha(1f).setDuration(160).start()
        // Armé sur la scène et non sur la carte : la scène survit à toutes les
        // questions, donc le capteur n'est enregistré qu'une fois, et le
        // retournement garde `rotationY` de la carte pour lui seul.
        Inclinaison.suivre(scene)
        afficherQuestion()
    }

    private fun fermer() {
        racine.animate().alpha(0f).setDuration(160).withEndAction {
            hote.removeView(racine)
            surFin()
        }.start()
    }

    private fun enTete() = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Carnet.COULEUR)
        setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        addView(TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            text = "🔁  Widderhuelen"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        tvProgres = TextView(ctx).apply {
            textSize = 14f
            setTextColor(0xFFE8E0FF.toInt())
        }
        addView(tvProgres)
        addView(TextView(ctx).apply {
            text = "✕"
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(dp(18f), 0, dp(2f), 0)
            isClickable = true
            // Quitter en cours de session garde ce qui a été noté : une carte
            // répondue est notée à la réponse, pas au bilan.
            setOnClickListener { fermer() }
        })
    }

    // ------------------------------------------------------------------ états

    /**
     * La question, écrite sur le dos du carton.
     *
     * Les trois formes partagent la même carte et les mêmes emplacements :
     * la consigne sur la plaque de nom, l'énoncé dans la fenêtre, et — pour
     * les deux formes tapées — l'ardoise dans le panneau de texte.
     */
    private fun afficherQuestion() {
        val q = session.courante ?: return afficherBilan()
        saisie = StringBuilder()
        // La majuscule n'est jamais préarmée, même pour un substantif : la
        // produire fait partie de la question.
        majuscule = false
        tvProgres.text = "${session.rang} / ${session.total}"

        val tapee = q.forme != FormeQuestion.RECONNAISSANCE
        val carton = DosRevision(ctx).apply { avecArdoise = tapee }

        carton.posee(
            ligne(
                when (q.forme) {
                    FormeQuestion.RECONNAISSANCE -> "Vous souvenez-vous ?"
                    FormeQuestion.PHRASE_A_TROUS -> "Quel mot manque ?"
                    FormeQuestion.GLOSE -> "Comment l'écrit-on ?"
                },
                taille = 13f, couleur = ENCRE, gras = true
            ),
            Ornement.PLAQUE
        )
        carton.posee(enonce(q), Ornement.FENETRE)

        if (tapee) {
            val vue = bloc("…", 22f, Carnet.COULEUR, gras = true).apply { maxLines = 2 }
            ardoise = vue
            carton.posee(vue, Ornement.PANNEAU_TEXTE)
        } else {
            ardoise = null
        }

        poserCarton(carton)
        dos = carton

        bas.removeAllViews()
        if (tapee) construireSaisie(q) else {
            bas.addView(bouton("Retourner la carte", Carnet.COULEUR) {
                revelation(q, verdict = null)
            })
        }
    }

    /**
     * L'énoncé, dans l'ouverture d'illustration.
     *
     * Le mot d'une question de reconnaissance ne prend **pas** la couleur de
     * son jeu, contrairement à ce que faisait l'ancien écran. Sur un dos, une
     * couleur de jeu trahit la provenance de la carte, et le paquet mélange
     * les sept : le joueur y lirait un indice avant d'avoir cherché.
     */
    private fun enonce(q: QuestionRevision): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        when (q.forme) {
            FormeQuestion.RECONNAISSANCE ->
                addView(bloc(q.motAttendu, 32f, ENCRE, gras = true).apply { maxLines = 2 })
            FormeQuestion.GLOSE ->
                addView(bloc(q.contenu.glose, 20f, ENCRE, gras = true).apply { maxLines = 4 })
            FormeQuestion.PHRASE_A_TROUS -> {
                addView(
                    bloc("« ${q.phraseTrouee ?: ""} »", 15f, ENCRE, gras = false).apply {
                        setTypeface(null, Typeface.ITALIC)
                        maxLines = 5
                    }
                )
                // Quand la phrase réclame une forme sœur, le dire : sans cela
                // le joueur cherche le mot de sa carte et se trompe sans
                // comprendre pourquoi.
                if (q.demandeUneAutreForme) {
                    addView(
                        bloc(
                            "une forme de « ${q.contenu.carte.forme} »",
                            11f, ENCRE_PALE, gras = false, margeHaute = 8f
                        ).apply { maxLines = 2 }
                    )
                }
            }
        }
    }

    /** Le pavé et le bouton de validation, sous la carte. */
    private fun construireSaisie(q: QuestionRevision) {
        val pave = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        bas.addView(pave)

        val valider = bouton("Valider", Carnet.COULEUR) {
            val verdict = SessionWidderhuelen.verdict(
                saisie.toString(),
                q.motAttendu,
                SessionWidderhuelen.acceptees(q, paquet)
            )
            revelation(q, verdict)
        }.apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(8f) }
        bas.addView(valider)

        fun rafraichir() {
            ardoise?.text = if (saisie.isEmpty()) "…" else saisie.toString()
            valider.isEnabled = saisie.isNotEmpty()
            valider.alpha = if (saisie.isEmpty()) 0.4f else 1f
        }
        construirePave(pave) { rafraichir() }
        rafraichir()
    }

    /**
     * La carte révélée, et ce que la réponse valait.
     *
     * En reconnaissance, le joueur se note lui-même après avoir vu la carte.
     * En production, le verdict est déjà connu, et c'est la carte qui explique
     * ce qui manquait.
     */
    private fun revelation(q: QuestionRevision, verdict: Verdict?) {
        val poser = {
            bas.removeAllViews()
            verdict?.let {
                bas.addView(bandeauVerdict(it, q).apply {
                    (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(10f)
                })
            }
            if (verdict == null) {
                bas.addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = pleineLargeur()
                    addView(bouton("Pas su", 0xFFB0575E.toInt()) { noter(q, Verdict.FAUX) }.apply {
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            width = 0; weight = 1f; rightMargin = dp(6f)
                        }
                    })
                    addView(bouton("Je savais", 0xFF2E7D32.toInt()) { noter(q, Verdict.EXACT) }.apply {
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            width = 0; weight = 1f; leftMargin = dp(6f)
                        }
                    })
                })
            } else {
                bas.addView(bouton("Suivant", Carnet.COULEUR) { noter(q, verdict) })
            }

            val recto = CarteCarnet.complete(ctx, q.contenu)
            poserCarton(recto)
            if (recto is Carton && !Pochette.animationsReduites(ctx)) {
                recto.rotationY = -90f
                // Le second temps n'est lancé qu'une fois la scène remesurée :
                // le pavé vient de disparaître, et c'est cette mesure-là qui
                // donne au recto la largeur que le dos n'avait pas.
                recto.post {
                    recto.animate().rotationY(0f).setDuration(220)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
            }
        }

        // Lâché tout de suite : un second appui sur « Valider » pendant le
        // retournement relancerait sinon un deuxième geste sur la même vue.
        val sortant = dos
        dos = null
        if (sortant == null || Pochette.animationsReduites(ctx)) {
            poser()
            return
        }
        // Premier temps : le dos se met de profil. C'est là, et pas ailleurs,
        // que la carte change de taille — de profil, elle est invisible.
        sortant.animate().rotationY(90f).setDuration(170)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { poser() }
            .start()
    }

    /** Pose un carton au centre de la scène, ajusté à ce qui reste de hauteur. */
    private fun poserCarton(carton: View) {
        scene.removeAllViews()
        (carton as? Carton)?.ajusteALaHauteur = true
        carton.cameraDistance = 12000f * d
        scene.addView(carton, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
    }

    /**
     * Le bandeau de verdict.
     *
     * [Verdict.DETAIL] porte la seule chose qu'il faut dire : la forme exacte,
     * en face de ce qui a été tapé. La carte ne monte pas de boîte, et le dire
     * évite de faire passer pour une brimade ce qui est la leçon.
     */
    private fun bandeauVerdict(verdict: Verdict, q: QuestionRevision): View {
        val (couleur, titre, detail) = when (verdict) {
            Verdict.EXACT -> Triple(0xFF2E7D32.toInt(), "Exact", null)
            Verdict.DETAIL -> Triple(
                0xFFEF6C00.toInt(), "Presque",
                "Vous avez écrit « ${saisie} », le mot s'écrit « ${q.motAttendu} ». " +
                    "La carte reste dans sa boîte."
            )
            Verdict.FAUX -> Triple(
                0xFFB0575E.toInt(), "Pas cette fois",
                "Le mot était « ${q.motAttendu} »."
            )
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = pleineLargeur()
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
            background = GradientDrawable().apply {
                cornerRadius = 12f * d
                setColor(Color.WHITE)
                setStroke(dp(2f), couleur)
            }
            addView(TextView(ctx).apply {
                text = titre
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(couleur)
            })
            detail?.let {
                addView(TextView(ctx).apply {
                    layoutParams = pleineLargeur().apply { topMargin = dp(4f) }
                    text = it
                    textSize = 13f
                    setTextColor(Color.parseColor("#424242"))
                    setLineSpacing(0f, 1.2f)
                })
            }
        }
    }

    /**
     * Enregistre la réponse.
     *
     * La session ne compte que réussi ou raté — c'est le score de la manche, et
     * un « presque » y est une réussite. Le carnet, lui, reçoit le **verdict
     * entier** : c'est de lui que dépend la boîte, et c'est là que
     * [Verdict.DETAIL] cesse de promouvoir.
     *
     * La carte est identifiée par sa forme à elle, jamais par le mot demandé :
     * une phrase trouée peut réclamer une autre forme de la famille, et
     * l'enregistrement doit retomber sur la bonne carte.
     */
    private fun noter(q: QuestionRevision, verdict: Verdict) {
        if (session.repondre(verdict != Verdict.FAUX)) {
            surNotation(q.contenu.carte.forme, verdict)
        }
        afficherQuestion()
    }

    /**
     * Le bilan, et la seule mention du clavier.
     *
     * Les cartes montées par la frappe ne sont pas passées par une question :
     * les annoncer ici est la seule façon de dire au joueur pourquoi son paquet
     * était plus court que son arriéré.
     */
    private fun afficherBilan() {
        tvProgres.text = ""
        dos = null
        scene.removeAllViews()
        bas.removeAllViews()

        val feuille = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(ctx).apply {
                layoutParams = pleineLargeur()
                text = "Session terminée"
                textSize = 22f
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                setTextColor(Carnet.COULEUR)
            })
            addView(TextView(ctx).apply {
                layoutParams = pleineLargeur().apply { topMargin = dp(10f) }
                text = "${session.reussies.size} sur ${session.total} retrouvés."
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#424242"))
            })
            if (session.ratees.isNotEmpty()) {
                addView(TextView(ctx).apply {
                    layoutParams = pleineLargeur().apply { topMargin = dp(14f) }
                    text = "À revoir demain : " + session.ratees.joinToString(", ")
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#757575"))
                    setLineSpacing(0f, 1.2f)
                })
            }
            if (monteesParLeClavier.isNotEmpty()) {
                addView(TextView(ctx).apply {
                    layoutParams = pleineLargeur().apply { topMargin = dp(18f) }
                    text = "Vous avez écrit vous-même " +
                        monteesParLeClavier.joinToString(", ") +
                        " depuis la dernière fois : ces cartes n'avaient rien à prouver."
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(0xFF2E7D32.toInt())
                    setLineSpacing(0f, 1.2f)
                })
            }
        }
        scene.addView(feuille, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        bas.addView(bouton("Fermer", Carnet.COULEUR) { fermer() })
    }

    // ------------------------------------------------------------------- pavé

    /**
     * Le pavé, dans la disposition du clavier, avec `⇧` à sa place.
     *
     * Chaque rangée pèse dix unités comme celles du clavier, ce qui aligne les
     * touches d'une rangée à l'autre : la troisième porte sept lettres entre
     * `⇧` et `⌫`, tous deux d'une unité et demie, et la quatrième porte les
     * quatre voyelles infléchies en touches doubles, centrées.
     */
    private fun construirePave(hote: LinearLayout, apresSaisie: () -> Unit) {
        hote.removeAllViews()
        val touchesLettres = ArrayList<Pair<TextView, Char>>()

        fun majuscules(actif: Boolean) {
            majuscule = actif
            touchesLettres.forEach { (vue, lettre) ->
                vue.text = (if (actif) lettre.uppercaseChar() else lettre.lowercaseChar()).toString()
            }
        }

        CrosswordData.RANGEES.forEachIndexed { rang, rangee ->
            val ligne = LinearLayout(ctx).apply {
                layoutParams = pleineLargeur().apply { bottomMargin = dp(5f) }
                orientation = LinearLayout.HORIZONTAL
            }
            val poids = if (rang == CrosswordData.RANGEE_ACCENTS) 2f else 1f

            if (rang == CrosswordData.RANGEE_EFFACEMENT) {
                ligne.addView(touche("⇧", 1.5f) {
                    majuscules(!majuscule)
                })
            } else if (rang == CrosswordData.RANGEE_ACCENTS) {
                ligne.addView(espaceur(1f))
            }

            rangee.forEach { lettre ->
                val vue = touche(lettre.lowercaseChar().toString(), poids) {
                    saisie.append(if (majuscule) lettre.uppercaseChar() else lettre.lowercaseChar())
                    // Une majuscule ne vaut que pour la lettre suivante, comme
                    // sur le clavier.
                    if (majuscule) majuscules(false)
                    apresSaisie()
                }
                touchesLettres.add(vue to lettre)
                ligne.addView(vue)
            }

            if (rang == CrosswordData.RANGEE_EFFACEMENT) {
                ligne.addView(touche("⌫", 1.5f) {
                    if (saisie.isNotEmpty()) saisie.deleteCharAt(saisie.length - 1)
                    apresSaisie()
                })
            } else if (rang == CrosswordData.RANGEE_ACCENTS) {
                ligne.addView(espaceur(1f))
            }

            hote.addView(ligne)
        }
        majuscules(false)
    }

    private fun touche(libelle: String, poids: Float, action: () -> Unit) = TextView(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, poids
        ).apply { setMargins(dp(1.5f), 0, dp(1.5f), 0) }
        text = libelle
        textSize = 16f
        gravity = Gravity.CENTER
        setPadding(0, dp(hauteurTouche), 0, dp(hauteurTouche))
        setTypeface(null, Typeface.BOLD)
        setTextColor(Color.parseColor("#212121"))
        background = GradientDrawable().apply {
            cornerRadius = 8f * d
            setColor(Color.WHITE)
            setStroke(dp(1f), Color.parseColor("#D0D0D0"))
        }
        isClickable = true
        setOnClickListener { action() }
    }

    private fun espaceur(poids: Float) = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, poids
        )
    }

    // ------------------------------------------------------------- fabriques

    private fun pleineLargeur() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    /**
     * Une ligne posée dans un emplacement du carton.
     *
     * La taille est exprimée **en unités de carte** et voyage dans le `tag`,
     * exactement comme celles de [CarteCarnet] : c'est [Carton] qui la
     * convertit en pixels une fois qu'il connaît sa largeur réelle. Sans ça,
     * la question serait illisible sur le petit carton d'une question tapée.
     */
    private fun ligne(
        contenu: String,
        taille: Float,
        couleur: Int,
        gras: Boolean
    ): TextView = TextView(ctx).apply {
        text = contenu
        setTextColor(couleur)
        if (gras) setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
        tag = floatArrayOf(taille, 0f)
    }

    /** Un texte qui a le droit de revenir à la ligne, centré dans sa case. */
    private fun bloc(
        contenu: String,
        taille: Float,
        couleur: Int,
        gras: Boolean,
        margeHaute: Float = 0f
    ): TextView = TextView(ctx).apply {
        text = contenu
        setTextColor(couleur)
        if (gras) setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        setLineSpacing(0f, 1.14f)
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        tag = floatArrayOf(taille, margeHaute)
    }

    private fun bouton(libelle: String, couleur: Int, action: () -> Unit) = TextView(ctx).apply {
        layoutParams = pleineLargeur()
        text = libelle
        textSize = 15f
        gravity = Gravity.CENTER
        setTypeface(null, Typeface.BOLD)
        setTextColor(Color.WHITE)
        setPadding(dp(16f), dp(13f), dp(16f), dp(13f))
        background = GradientDrawable().apply {
            cornerRadius = 24f * d
            setColor(couleur)
        }
        isClickable = true
        setOnClickListener { if (isEnabled) action() }
    }

    private companion object {
        const val ENCRE = 0xFF1B1610.toInt()
        const val ENCRE_PALE = 0xFF7A7160.toInt()
    }
}
