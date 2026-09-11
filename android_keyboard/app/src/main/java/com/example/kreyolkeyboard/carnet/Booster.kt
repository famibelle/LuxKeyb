package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * La pochette de fin de partie : les cartes gagnées se retournent une à une.
 *
 * C'est le geste qui manquait entre la partie et la collection. Le carnet
 * conserve, mais il ne raconte rien : on y va pour consulter. La pochette,
 * elle, **paie la partie au moment où elle se termine** — on ne consulte pas
 * ses cartes, on les découvre.
 *
 * Elle a été écrite pour Wuertplaz et sert aujourd'hui les sept jeux : une
 * manche de Wuertlück, une grille de Kräizwuert et une partie de Wuertriet se
 * terminent toutes sur le même geste, aux couleurs du jeu qu'on vient de
 * quitter.
 *
 * Quatre choix qui portent le reste :
 *
 * - **Toutes les cartes de la partie, pas seulement les neuves.** Un paquet
 *   dont on connaît déjà la moitié reste un paquet ; n'ouvrir que les
 *   nouveautés ferait des parties sans récompense dès que le joueur commence à
 *   connaître le vocabulaire, c'est-à-dire exactement quand il progresse. Les
 *   neuves passent devant et portent un bandeau.
 * - **Une carte se retourne toute seule, un appui passe à la suivante.** Deux
 *   appuis par carte auraient donné le rythme authentique d'un paquet, et une
 *   corvée sur une grille de neuf mots. Le retournement est la récompense, il
 *   n'a pas à se mériter une deuxième fois.
 * - **« Passer » est toujours offert**, dès la première carte. La pochette est
 *   un cadeau, pas un péage entre le joueur et la grille suivante.
 * - **Elle n'arrive jamais après « Solution ».** Une grille révélée ne verse
 *   rien au carnet, donc elle n'ouvre pas de pochette : la récompense suit ce
 *   qui a été gagné, pas ce qui a été montré. Même règle partout : un mot
 *   passé dans Wuertmix, une réponse fausse dans Wuertlück, un Wuertriet perdu
 *   ne donnent pas de carte.
 */
object Booster {

    /**
     * Ouvre la pochette au-dessus de [hote].
     *
     * [jeu] est celui d'où sortent ces cartes : il donne sa couleur au dos et
     * au bouton du carnet, pour que la pochette appartienne visiblement à la
     * partie qu'on vient de finir.
     *
     * [nouvelles] porte les formes que le carnet n'avait jamais vues ; elles
     * sont montrées d'abord et signalées. [surFin] est appelé à la fermeture,
     * quelle qu'en soit la manière — dernier appui, « Passer », ou voile
     * touché — pour que l'appelant puisse rendre la main sans compter les
     * chemins.
     */
    fun ouvrir(
        hote: ViewGroup,
        jeu: JeuCarte,
        contenus: List<ContenuCarte>,
        nouvelles: Set<String>,
        animations: Boolean,
        surCarnet: () -> Unit,
        surFin: () -> Unit
    ): View? {
        if (contenus.isEmpty()) {
            surFin()
            return null
        }
        val ctx = hote.context
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()

        // Les neuves d'abord : c'est ce qu'on veut voir, et cela évite que la
        // seule carte inédite d'une grille arrive après six déjà connues.
        val ordre = contenus.sortedByDescending { it.carte.forme in nouvelles }

        val voile = FrameLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xE6000000.toInt())
            isClickable = true
        }

        val colonne = LinearLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20f), dp(18f), dp(20f), dp(16f))
        }

        val titre = TextView(ctx).apply {
            text = "🎁 Votre pochette — ${jeu.nom}"
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        colonne.addView(titre)

        val progres = TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3f); bottomMargin = dp(10f) }
            textSize = 13f
            setTextColor(0xFFB0BEC5.toInt())
        }
        colonne.addView(progres)

        val bandeau = TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8f) }
            text = "✨  NOUVELLE CARTE"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(12f), dp(5f), dp(12f), dp(5f))
            background = GradientDrawable().apply {
                cornerRadius = 20f * d
                setColor(0xFFFB8C00.toInt())
            }
            visibility = View.INVISIBLE
        }
        colonne.addView(bandeau)

        // La scène : le dos et la carte s'y remplacent, elle ne bouge pas.
        val scene = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        colonne.addView(scene)

        val boutons = LinearLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12f) }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val passer = TextView(ctx).apply {
            text = "Passer"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFB0BEC5.toInt())
            setPadding(dp(22f), dp(11f), dp(22f), dp(11f))
            isClickable = true
        }
        boutons.addView(passer)

        // La sortie vers le carnet, offerte seulement au bilan : proposée dès
        // la première carte, elle inviterait à quitter la pochette avant de
        // l'avoir ouverte.
        val versCarnet = TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(10f) }
            text = "📔 Mon carnet"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(20f), dp(11f), dp(20f), dp(11f))
            background = GradientDrawable().apply {
                cornerRadius = 24f * d
                setColor(jeu.couleur)
            }
            isClickable = true
            visibility = View.GONE
        }
        boutons.addView(versCarnet)
        colonne.addView(boutons)

        voile.addView(colonne)
        hote.addView(voile)

        var rang = -1
        var ferme = false

        fun fermer() {
            if (ferme) return
            ferme = true
            voile.animate().alpha(0f).setDuration(180)
                .withEndAction {
                    (voile.parent as? ViewGroup)?.removeView(voile)
                    surFin()
                }.start()
        }

        /** Le bilan, une fois la dernière carte vue. */
        fun bilan() {
            scene.removeAllViews()
            bandeau.visibility = View.INVISIBLE
            progres.text = ""
            val neuves = contenus.count { it.carte.forme in nouvelles }
            titre.text = "🎁 " + contenus.size + " carte" +
                (if (contenus.size > 1) "s" else "") +
                (if (neuves > 0) " · $neuves nouvelle" +
                    (if (neuves > 1) "s" else "") else "")
            scene.addView(TextView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
                text = if (neuves > 0)
                    "Elles sont rangées dans votre carnet."
                else
                    "Vous les aviez déjà toutes : elles restent dans votre carnet."
                textSize = 15f
                gravity = Gravity.CENTER
                setLineSpacing(0f, 1.25f)
                setTextColor(0xFFECEFF1.toInt())
            })
            passer.text = "Fermer"
            passer.setTextColor(Color.WHITE)
            versCarnet.visibility = View.VISIBLE
        }

        lateinit var suivante: () -> Unit

        suivante = fun() {
            rang++
            if (rang >= ordre.size) {
                bilan()
                return
            }
            val c = ordre[rang]
            val neuve = c.carte.forme in nouvelles
            progres.text = "${rang + 1} / ${ordre.size}"
            bandeau.visibility = View.INVISIBLE

            val face = ScrollView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
                isVerticalScrollBarEnabled = false
                addView(CarteCarnet.complete(ctx, c))
            }

            if (!animations) {
                scene.removeAllViews()
                scene.addView(face)
                if (neuve) bandeau.visibility = View.VISIBLE
                return
            }

            val dos = DosDeCarte(ctx, jeu).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    (ctx.resources.displayMetrics.heightPixels * 0.44f).toInt(),
                    Gravity.CENTER
                )
            }
            scene.removeAllViews()
            scene.addView(dos)

            // Le dos arrive, puis se retourne de lui-même. Le retournement est
            // la récompense : la faire attendre un appui de plus ne la rend
            // pas plus douce, elle rend la pochette longue.
            dos.alpha = 0f
            dos.scaleX = 0.9f
            dos.scaleY = 0.9f
            dos.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start()

            dos.postDelayed({
                if (ferme || dos.parent == null) return@postDelayed
                dos.cameraDistance = 12000f * d
                face.cameraDistance = 12000f * d
                dos.animate().rotationY(90f).setDuration(170)
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction {
                        if (ferme) return@withEndAction
                        scene.removeAllViews()
                        scene.addView(face)
                        face.rotationY = -90f
                        face.animate().rotationY(0f).setDuration(210)
                            .setInterpolator(DecelerateInterpolator())
                            .withEndAction {
                                if (!ferme && neuve) {
                                    bandeau.visibility = View.VISIBLE
                                    bandeau.alpha = 0f
                                    bandeau.scaleX = 0.8f
                                    bandeau.scaleY = 0.8f
                                    bandeau.animate().alpha(1f).scaleX(1f).scaleY(1f)
                                        .setDuration(220).start()
                                }
                            }
                            .start()
                    }.start()
            }, 380)
        }

        // Un appui n'importe où sur le voile passe à la carte suivante ; le
        // bouton ferme. Deux gestes, aucun à deviner : le libellé du bouton
        // change quand il n'y a plus rien à voir.
        voile.setOnClickListener { if (rang >= ordre.size) fermer() else suivante() }
        passer.setOnClickListener { fermer() }
        versCarnet.setOnClickListener {
            fermer()
            surCarnet()
        }

        voile.alpha = 0f
        voile.animate().alpha(1f).setDuration(200).start()
        suivante()
        return voile
    }
}

/**
 * Le dos d'une carte.
 *
 * Volontairement identique pour toutes les cartes d'une même pochette : un dos
 * qui trahirait la carte supprimerait le seul instant que la pochette fabrique.
 * Ce qu'il dit, c'est **le jeu** — sa couleur et son emoji — et non le mot
 * caché dessous : on sait d'où vient le paquet, jamais ce qu'il contient.
 */
class DosDeCarte(context: Context, private val jeu: JeuCarte) : View(context) {

    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pinceauTexte = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private var fond: LinearGradient? = null

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w <= 0 || h <= 0) return
        fond = LinearGradient(
            0f, 0f, w * 0.4f, h.toFloat(),
            eclaircir(jeu.couleur, 0.22f), assombrir(jeu.couleur, 0.45f),
            Shader.TileMode.CLAMP
        )
    }

    /** Le dégradé du dos : la couleur du jeu, une fois levée, une fois posée. */
    private fun eclaircir(couleur: Int, part: Float) = Color.rgb(
        (Color.red(couleur) + (255 - Color.red(couleur)) * part).toInt(),
        (Color.green(couleur) + (255 - Color.green(couleur)) * part).toInt(),
        (Color.blue(couleur) + (255 - Color.blue(couleur)) * part).toInt()
    )

    private fun assombrir(couleur: Int, part: Float) = Color.rgb(
        (Color.red(couleur) * (1 - part)).toInt(),
        (Color.green(couleur) * (1 - part)).toInt(),
        (Color.blue(couleur) * (1 - part)).toInt()
    )

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val d = resources.displayMetrics.density
        val r = 14f * d

        pinceau.shader = fond
        pinceau.alpha = 255
        canvas.drawRoundRect(0f, 0f, w, h, r, r, pinceau)
        pinceau.shader = null

        // Le liseré, comme sur la face.
        pinceau.style = Paint.Style.STROKE
        pinceau.strokeWidth = 3f * d
        pinceau.color = Color.WHITE
        pinceau.alpha = 60
        canvas.drawRoundRect(
            2f * d, 2f * d, w - 2f * d, h - 2f * d, r, r, pinceau
        )

        // Des anneaux concentriques, très effacés : de la matière, pas un motif
        // qu'on cherche à lire.
        pinceau.strokeWidth = 1.5f * d
        pinceau.alpha = 26
        for (i in 1..5) {
            canvas.drawCircle(w / 2f, h / 2f, h * 0.10f * i, pinceau)
        }
        pinceau.style = Paint.Style.FILL

        // Quatre cases, comme une grille, et l'emoji du jeu au centre : le
        // motif est le même pour les sept, la marque change.
        val cote = h * 0.075f
        val ecart = cote * 2.6f
        val gx = w / 2f - cote - ecart / 2f
        val gy = h / 2f - cote - ecart / 2f
        pinceau.color = Color.WHITE
        for (l in 0..1) for (c in 0..1) {
            pinceau.alpha = if ((l + c) % 2 == 0) 200 else 96
            val x = gx + c * (cote + ecart)
            val y = gy + l * (cote + ecart)
            canvas.drawRoundRect(
                RectF(x, y, x + cote, y + cote), 4f * d, 4f * d, pinceau
            )
        }

        pinceauTexte.textSize = h * 0.10f
        val mesure = Paint.FontMetrics().also { pinceauTexte.getFontMetrics(it) }
        canvas.drawText(
            jeu.emoji, w / 2f, h / 2f - (mesure.ascent + mesure.descent) / 2f,
            pinceauTexte
        )
    }
}
