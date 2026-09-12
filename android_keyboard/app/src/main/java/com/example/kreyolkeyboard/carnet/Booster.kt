package com.example.kreyolkeyboard.carnet

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
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

            val rarete = c.rarete

            if (!animations) {
                // Sans animations, la lueur devient un état plutôt qu'un
                // mouvement : la carte rare arrive face visible, mais elle
                // arrive quand même sur son halo.
                scene.removeAllViews()
                if (rarete.distinguee) scene.addView(HaloRarete(ctx, rarete).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER
                    )
                    alpha = 0.45f
                })
                scene.addView(face)
                if (neuve) bandeau.visibility = View.VISIBLE
                return
            }

            val dos = DosDeCarte(ctx, jeu, rarete).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    (ctx.resources.displayMetrics.heightPixels * 0.44f).toInt(),
                    Gravity.CENTER
                )
            }

            // La lueur qui précède une carte rare.
            //
            // C'est le seul endroit du carnet où la rareté se sait **avant**
            // d'être vue, et c'est voulu : l'attente est ce qui transforme un
            // retournement en événement. Une commune n'en a pas, et ne perd
            // rien — c'est de ne pas l'avoir qui dit ce qu'elle est.
            val halo = if (rarete.distinguee) HaloRarete(ctx, rarete).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
                alpha = 0f
            } else null

            scene.removeAllViews()
            halo?.let { scene.addView(it) }
            scene.addView(dos)

            // Le dos arrive, puis se retourne de lui-même. Le retournement est
            // la récompense : la faire attendre un appui de plus ne la rend
            // pas plus douce, elle rend la pochette longue. Une rare le fait
            // attendre un peu, une très rare un peu plus : la durée est la
            // seule chose qu'une animation sache dire, et elle le dit sans mot.
            dos.alpha = 0f
            dos.scaleX = 0.9f
            dos.scaleY = 0.9f
            dos.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start()

            val attente = when (rarete) {
                Rarete.TRES_RARE -> 900L
                Rarete.RARE -> 620L
                else -> 380L
            }
            halo?.animate()?.alpha(1f)?.setDuration(attente)?.start()

            dos.postDelayed({
                if (ferme || dos.parent == null) return@postDelayed
                dos.cameraDistance = 12000f * d
                face.cameraDistance = 12000f * d
                dos.animate().rotationY(90f).setDuration(170)
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction {
                        if (ferme) return@withEndAction
                        scene.removeAllViews()
                        // Le halo reste derrière la carte révélée et s'efface
                        // doucement : la lueur ne s'éteint pas au moment où
                        // elle a enfin quelque chose à éclairer.
                        halo?.let {
                            scene.addView(it)
                            it.animate().alpha(0.35f).setDuration(900).start()
                        }
                        scene.addView(face)
                        face.rotationY = -90f
                        face.animate().rotationY(0f)
                            .setDuration(if (rarete.distinguee) 300L else 210L)
                            .setInterpolator(
                                if (rarete.distinguee) OvershootInterpolator(1.2f)
                                else DecelerateInterpolator()
                            )
                            .withEndAction {
                                if (ferme) return@withEndAction
                                // La carte a fini de se poser : elle suit
                                // maintenant l'inclinaison de l'appareil.
                                Inclinaison.suivre(face)
                                if (rarete == Rarete.TRES_RARE) eclater(scene, rarete)
                                if (neuve) {
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
            }, attente)
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

    /**
     * L'éclat qui accompagne l'arrivée d'une très rare.
     *
     * Il ne dure pas : une demi-seconde, et la vue se retire d'elle-même. Un
     * effet permanent aurait fini par gêner la lecture de la carte, qui reste
     * ce qu'on est venu voir.
     */
    private fun eclater(scene: FrameLayout, rarete: Rarete) {
        val eclat = EclatCarte(scene.context, rarete).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        }
        scene.addView(eclat)
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 520L
            addUpdateListener { eclat.avancement = it.animatedValue as Float }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    (eclat.parent as? ViewGroup)?.removeView(eclat)
                }
            })
            start()
        }
    }
}

/**
 * Le dos d'une carte.
 *
 * Volontairement identique pour toutes les cartes d'une même pochette : un dos
 * qui trahirait la carte supprimerait le seul instant que la pochette fabrique.
 * Ce qu'il dit, c'est **le jeu** — sa couleur et son emoji — et non le mot
 * caché dessous : on sait d'où vient le paquet, jamais ce qu'il contient.
 *
 * Le motif ne change donc pas avec [rarete] : seul le liseré s'allume. Le mot
 * reste secret, mais le paquet a le droit de faire savoir qu'il tient quelque
 * chose — c'est ce que fait n'importe quel joueur qui hésite avant de
 * retourner une carte, et ce que le halo derrière le dos raconte déjà.
 */
class DosDeCarte(
    context: Context,
    private val jeu: JeuCarte,
    private val rarete: Rarete = Rarete.COMMUN
) : View(context) {

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

        // Le liseré, comme sur la face — et allumé quand la carte est rare.
        pinceau.style = Paint.Style.STROKE
        pinceau.strokeWidth = 3f * d
        pinceau.color = if (rarete.distinguee)
            eclaircir(rarete.couleur, 0.55f) else Color.WHITE
        pinceau.alpha = if (rarete == Rarete.TRES_RARE) 190
        else if (rarete == Rarete.RARE) 130
        else 60
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

/**
 * Le halo qui monte derrière une carte rare avant qu'elle ne se retourne.
 *
 * Un simple dégradé radial, aux couleurs du palier. Il tient sa place parce
 * qu'il ne dit **rien du mot** : le joueur apprend qu'il tient quelque chose,
 * pas quoi. C'est la promesse, et la carte est le paiement.
 */
class HaloRarete(context: Context, private val rarete: Rarete) : View(context) {

    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lueur: RadialGradient? = null

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w <= 0 || h <= 0) return
        val vif = eclaircir(rarete.couleur, 0.30f)
        val coeur = if (rarete == Rarete.TRES_RARE) 150 else 96
        lueur = RadialGradient(
            w * 0.5f, h * 0.5f, maxOf(w, h) * 0.55f,
            intArrayOf(
                Color.argb(coeur, Color.red(vif), Color.green(vif), Color.blue(vif)),
                Color.argb(coeur / 3, Color.red(vif), Color.green(vif), Color.blue(vif)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val shader = lueur ?: return
        pinceau.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pinceau)
    }
}

/**
 * L'éclat d'une très rare, au moment où elle se pose.
 *
 * Douze rais qui partent du centre et un anneau qui s'ouvre, tous deux
 * s'effaçant à mesure qu'ils s'écartent. Les angles sont réguliers et non
 * tirés au hasard : un éclat qui change de forme à chaque carte se remarque,
 * et ce n'est pas lui qu'on doit remarquer.
 *
 * [avancement] va de 0 à 1 et n'est piloté que par l'animateur de [Booster] :
 * la vue ne connaît pas le temps, elle ne connaît que sa position dedans.
 */
class EclatCarte(context: Context, private val rarete: Rarete) : View(context) {

    var avancement: Float = 0f
        set(valeur) {
            field = valeur
            invalidate()
        }

    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f || avancement <= 0f) return
        val d = resources.displayMetrics.density
        val cx = w / 2f
        val cy = h / 2f
        val portee = minOf(w, h) * 0.48f
        val reste = 1f - avancement

        // Les rais : ils partent d'un cercle qui grandit et gardent une
        // longueur fixe, ce qui donne l'impression qu'ils fuient le centre.
        pinceau.color = eclaircir(rarete.couleur, 0.62f)
        pinceau.strokeWidth = 2.5f * d
        pinceau.alpha = (reste * 220).toInt().coerceIn(0, 255)
        val rais = 12
        val depart = portee * (0.35f + 0.55f * avancement)
        val longueur = portee * 0.16f * reste
        for (i in 0 until rais) {
            val angle = (2.0 * Math.PI * i / rais).toFloat()
            val dx = kotlin.math.cos(angle)
            val dy = kotlin.math.sin(angle)
            canvas.drawLine(
                cx + dx * depart, cy + dy * depart,
                cx + dx * (depart + longueur), cy + dy * (depart + longueur),
                pinceau
            )
        }

        // L'anneau, plus discret, qui donne l'échelle de l'éclat.
        pinceau.color = Color.WHITE
        pinceau.strokeWidth = 1.5f * d
        pinceau.alpha = (reste * 120).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, portee * (0.30f + 0.62f * avancement), pinceau)
    }
}
