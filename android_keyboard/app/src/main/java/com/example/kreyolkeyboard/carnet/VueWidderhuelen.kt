package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
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
 * Deux choix d'écran qui ne vont pas de soi :
 *
 * - **Le pavé est celui de Kräizwuert, avec une touche `⇧` en plus.** Kräizwuert
 *   laisse volontairement vide l'emplacement de la majuscule, sa grille étant
 *   tout en capitales ; ici la majuscule de substantif est **l'objet de la
 *   question**, donc elle doit pouvoir être produite. La touche reprend
 *   exactement la place qu'elle occupe sur le clavier, et les rangées gardent
 *   leur alignement. Voir [CrosswordData.RANGEES] pour le reste du
 *   raisonnement, qui vaut ici mot pour mot : le jeu entraîne les positions de
 *   doigts dont on se sert en écrivant un message.
 * - **La carte révélée est celle du carnet** ([CarteCarnet.complete]), pas un
 *   verso écrit pour l'occasion. C'est la même carte que le joueur a gagnée,
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
    private val corps = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
    }
    private lateinit var tvProgres: TextView

    private var saisie = StringBuilder()
    private var majuscule = false

    private fun dp(v: Float) = (v * d).toInt()

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
        colonne.addView(ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            isVerticalScrollBarEnabled = false
            addView(corps)
        })
        racine.addView(colonne, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        hote.addView(racine)
        racine.alpha = 0f
        racine.animate().alpha(1f).setDuration(160).start()
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

    private fun afficherQuestion() {
        val q = session.courante ?: return afficherBilan()
        saisie = StringBuilder()
        // La majuscule n'est jamais préarmée, même pour un substantif : la
        // produire fait partie de la question.
        majuscule = false

        tvProgres.text = "${session.rang} / ${session.total}"
        corps.removeAllViews()
        corps.setPadding(dp(16f), dp(16f), dp(16f), dp(24f))

        when (q.forme) {
            FormeQuestion.RECONNAISSANCE -> questionReconnaissance(q)
            // Quand la phrase réclame une forme sœur, le dire : sans cela le
            // joueur cherche le mot de sa carte et se trompe sans comprendre
            // pourquoi.
            FormeQuestion.PHRASE_A_TROUS -> questionTapee(
                q,
                q.phraseTrouee ?: "",
                if (q.demandeUneAutreForme) {
                    "Quel mot manque ? (une forme de « ${q.contenu.carte.forme} »)"
                } else {
                    "Quel mot manque ?"
                }
            )
            FormeQuestion.GLOSE -> questionTapee(q, q.contenu.glose, "Comment l'écrit-on ?")
        }
    }

    /**
     * Boîtes 0 et 1 : la forme, et rien d'autre.
     *
     * Pas de choix multiple : quatre propositions se départagent sans lire le
     * mot, c'est la mesure que `generate_cloze.py` a déjà faite pour Wuertlück.
     * Ici la seule question honnête est « le savez-vous ? », et le joueur est le
     * seul à pouvoir y répondre.
     */
    private fun questionReconnaissance(q: QuestionRevision) {
        corps.addView(consigne("Vous souvenez-vous de ce mot ?"))
        corps.addView(TextView(ctx).apply {
            layoutParams = pleineLargeur().apply { topMargin = dp(28f); bottomMargin = dp(28f) }
            text = q.motAttendu
            textSize = 34f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(q.contenu.carte.origine.couleur)
        })
        corps.addView(bouton("Retourner la carte", Carnet.COULEUR) {
            revelation(q, verdict = null)
        })
    }

    private fun questionTapee(q: QuestionRevision, enonce: String, intitule: String) {
        corps.addView(consigne(intitule))
        corps.addView(TextView(ctx).apply {
            layoutParams = pleineLargeur().apply { topMargin = dp(16f) }
            text = if (q.forme == FormeQuestion.PHRASE_A_TROUS) "« $enonce »" else enonce
            textSize = if (q.forme == FormeQuestion.PHRASE_A_TROUS) 17f else 22f
            gravity = Gravity.CENTER
            setTypeface(null, if (q.forme == FormeQuestion.PHRASE_A_TROUS) Typeface.ITALIC else Typeface.BOLD)
            setTextColor(Color.parseColor("#212121"))
            setLineSpacing(0f, 1.2f)
        })

        val ardoise = TextView(ctx).apply {
            layoutParams = pleineLargeur().apply { topMargin = dp(20f); bottomMargin = dp(14f) }
            textSize = 26f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(Carnet.COULEUR)
            setPadding(dp(10f), dp(12f), dp(10f), dp(12f))
            background = GradientDrawable().apply {
                cornerRadius = 12f * d
                setColor(Color.WHITE)
                setStroke(dp(1f), Color.parseColor("#D0D0D0"))
            }
        }
        corps.addView(ardoise)

        val pave = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        corps.addView(pave)

        val valider = bouton("Valider", Carnet.COULEUR) {
            val verdict = SessionWidderhuelen.verdict(
                saisie.toString(),
                q.motAttendu,
                SessionWidderhuelen.acceptees(q, paquet)
            )
            revelation(q, verdict)
        }
        corps.addView(valider)

        fun rafraichir() {
            ardoise.text = if (saisie.isEmpty()) "…" else saisie.toString()
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
        corps.removeAllViews()

        verdict?.let { corps.addView(bandeauVerdict(it, q)) }

        corps.addView(CarteCarnet.complete(ctx, q.contenu).apply {
            layoutParams = pleineLargeur().apply { topMargin = dp(12f) }
            cameraDistance = 9000f * d
            rotationY = -85f
            animate().rotationY(0f).setDuration(320)
                .setInterpolator(DecelerateInterpolator()).start()
        })

        if (verdict == null) {
            corps.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = pleineLargeur().apply { topMargin = dp(16f) }
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
            corps.addView(bouton("Suivant", Carnet.COULEUR) {
                noter(q, verdict)
            }.apply {
                (layoutParams as LinearLayout.LayoutParams).topMargin = dp(16f)
            })
        }
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
            setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
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
        corps.removeAllViews()
        corps.addView(TextView(ctx).apply {
            layoutParams = pleineLargeur().apply { topMargin = dp(24f) }
            text = "Session terminée"
            textSize = 22f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(Carnet.COULEUR)
        })
        corps.addView(TextView(ctx).apply {
            layoutParams = pleineLargeur().apply { topMargin = dp(10f) }
            text = "${session.reussies.size} sur ${session.total} retrouvés."
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#424242"))
        })
        if (session.ratees.isNotEmpty()) {
            corps.addView(TextView(ctx).apply {
                layoutParams = pleineLargeur().apply { topMargin = dp(14f) }
                text = "À revoir demain : " + session.ratees.joinToString(", ")
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#757575"))
                setLineSpacing(0f, 1.2f)
            })
        }
        if (monteesParLeClavier.isNotEmpty()) {
            corps.addView(TextView(ctx).apply {
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
        corps.addView(bouton("Fermer", Carnet.COULEUR) { fermer() }.apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(26f)
        })
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
        setPadding(0, dp(9f), 0, dp(9f))
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

    private fun consigne(texte: String) = TextView(ctx).apply {
        layoutParams = pleineLargeur()
        text = texte
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(Color.parseColor("#757575"))
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
}
