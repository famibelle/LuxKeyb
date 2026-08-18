package com.example.kreyolkeyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Gestionnaire responsable de la création et du stylisme des layouts de clavier
 * Sépare la logique de création des touches du service principal
 */
class KeyboardLayoutManager(private val context: Context) {
    
    companion object {
        private const val BUTTON_HEIGHT_DP = 48
        // Sous cette hauteur les touches deviennent difficiles à viser : mieux vaut
        // alors rogner ailleurs que continuer à réduire.
        private const val BUTTON_MIN_HEIGHT_DP = 32
        // En paysage la fenêtre IME ne reçoit qu'environ 359 dp de haut, contre 891
        // en portrait sur le même écran : garder 48 dp par touche y faisait occuper
        // au clavier 87 % de l'écran, ne laissant que 51 dp à l'application. Les
        // touches s'y posent donc directement sur le plancher de visée, la hauteur
        // étant la seule ressource qui manque dans cette orientation, où la largeur
        // laisse au contraire chaque touche deux fois plus large qu'en portrait.
        private const val BUTTON_HEIGHT_LANDSCAPE_DP = BUTTON_MIN_HEIGHT_DP
        private const val KEYBOARD_ROW_COUNT = 4
        // Padding vertical du bloc de touches, resserré en paysage pour la même
        // raison. Le service s'en sert pour calculer la place laissée aux rangées,
        // d'où l'exposition ici plutôt qu'une valeur écrite des deux côtés.
        private const val VERTICAL_PADDING_DP = 8
        private const val VERTICAL_PADDING_LANDSCAPE_DP = 4
        private const val BUTTON_MARGIN_DP = 2
        private const val CORNER_RADIUS_DP = 8f
        // Taille de police d'une lettre, en part de la hauteur de touche. Elle
        // valait 16 sp fixes, soit 44 px sur les 116 d'une touche en portrait
        // (38 %) : mesuré sur le même écran, Gboard y dessine ses lettres à
        // environ 62 px, presque une fois et demie plus grandes. Proportionnelle
        // comme le padding des icônes, pour ne pas déborder de la touche réduite
        // en paysage (32 dp), où une taille absolue ne laisserait pas la place.
        private const val KEY_TEXT_HEIGHT_RATIO = 0.52f
        // Les libellés de plusieurs caractères ("123", "ABC", "Potomitan™") sont
        // contraints par la largeur de la touche, pas par sa hauteur : les
        // agrandir les ferait tronquer. Ce rapport reprend leur taille d'avant
        // (16 sp × 0,75) rapportée aux 48 dp de la hauteur nominale.
        private const val WIDE_LABEL_TEXT_RATIO = 0.28f
        private const val HINT_TEXT_SIZE_SP = 8f
        private const val SHADOW_RADIUS = 4f
        private const val TAG = "KeyboardLayoutManager"

        // 🌐 Délai pour l'appui long sur la barre d'espace (1 seconde)
        private const val SPACE_LONG_PRESS_DELAY = 1000L

        fun isLandscape(context: Context): Boolean =
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        fun verticalPaddingDp(context: Context): Int =
            if (isLandscape(context)) VERTICAL_PADDING_LANDSCAPE_DP else VERTICAL_PADDING_DP
    }
    
    // État du clavier
    private var isCapitalMode = false
    private var isCapsLock = false
    private var isNumericMode = false // FORCE ALPHABÉTIQUE PAR DÉFAUT
    private var isEmojiMode = false
    private val keyboardButtons = mutableListOf<View>() // Changé de TextView à View pour supporter ImageButton

    // Hauteur que la fenêtre IME peut réellement accorder aux quatre rangées de
    // touches, renseignée par le service avant chaque création de layout. En
    // paysage cette fenêtre se limite à l'espace entre barre d'état et barre de
    // navigation (288 dp sur un 1080x2400 en 480 dpi) alors que la mise en page
    // nominale en réclame 332 : la rangée du bas (123, espace, entrée…) était
    // coupée en deux, bug signalé le 13/08/2026. Laissé à 0 par le clavier de
    // démonstration des Réglages, qui garde la hauteur nominale.
    private var availableRowsHeightPx = 0

    // Référence optionnelle pour prévisualiser les options d'appui long dans
    // les coins des touches (v8.3.0). Laissé à null par le clavier de démo
    // (SettingsActivity), qui n'a pas d'AccentHandler et n'affiche donc aucun indice.
    var accentHandler: AccentHandler? = null
    
    // 🌐 Handler pour l'appui long personnalisé de la barre d'espace
    private val spaceLongPressHandler = Handler(Looper.getMainLooper())
    private var spaceLongPressRunnable: Runnable? = null
    private var isSpaceLongPressTriggered = false
    
    init {
        // Garantir que le clavier démarre toujours en mode alphabétique
        ensureAlphabeticMode()
    }
    
    // Callbacks pour l'interaction avec les touches
    interface KeyboardInteractionListener {
        fun onKeyPress(key: String)
        fun onLongPress(key: String, button: View) // Changé de TextView à View
        fun onKeyRelease()
    }
    
    private var interactionListener: KeyboardInteractionListener? = null
    
    fun setInteractionListener(listener: KeyboardInteractionListener) {
        this.interactionListener = listener
    }

    /**
     * Déclare la hauteur disponible pour les rangées de touches, marges comprises.
     * À appeler avant createKeyboardLayout() : la hauteur des touches en découle.
     */
    fun setAvailableRowsHeight(heightPx: Int) {
        availableRowsHeightPx = heightPx.coerceAtLeast(0)
    }

    /**
     * Hauteur d'une touche : la hauteur nominale tant qu'elle tient, sinon la part
     * de place restante, sans jamais descendre sous le seuil de visée.
     */
    private fun keyHeightPx(): Int {
        val nominal = dpToPx(
            if (isLandscape(context)) BUTTON_HEIGHT_LANDSCAPE_DP else BUTTON_HEIGHT_DP
        )
        if (availableRowsHeightPx <= 0) return nominal
        val verticalMargins = dpToPx(BUTTON_MARGIN_DP) * 2
        val fitted = availableRowsHeightPx / KEYBOARD_ROW_COUNT - verticalMargins
        return fitted.coerceIn(dpToPx(BUTTON_MIN_HEIGHT_DP), nominal)
    }
    
    /**
     * Crée le layout principal du clavier avec toutes les rangées
     */
    fun createKeyboardLayout(): LinearLayout {
        Log.d("KeyboardLayoutManager", "🎯 createKeyboardLayout - isNumericMode: $isNumericMode")
        
        val verticalPaddingPx = dpToPx(verticalPaddingDp(context))
        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dpToPx(8), verticalPaddingPx,
                dpToPx(8), verticalPaddingPx
            )
        }
        
        // Créer les différentes rangées selon le mode
        when {
            isEmojiMode -> {
                Log.d("KeyboardLayoutManager", "😀 Création du layout EMOJI")
                createEmojiLayout(mainLayout)
            }
            isNumericMode -> {
                Log.d("KeyboardLayoutManager", "🔢 Création du layout NUMÉRIQUE")
                createNumericLayout(mainLayout)
            }
            else -> {
                Log.d("KeyboardLayoutManager", "🔤 Création du layout ALPHABÉTIQUE")
                createAlphabeticLayout(mainLayout)
            }
        }
        
        return mainLayout
    }
    
    /**
     * Crée le layout alphabétique (AZERTY créole)
     */
    private fun createAlphabeticLayout(mainLayout: LinearLayout) {
        // v10.11.3 : "ò" retiré de cette rangée, qui repasse à 10 touches. Elle en
        // portait 11, ce qui la rendait la plus étroite du clavier alors qu'elle est
        // la plus frappée : mesuré sur Pixel 5, 29,1 dp par touche (4,6 mm) contre
        // 32,4 dp en rangée 2 et 37,0 dp en rangée 3, pour 48,9 % de la frappe ici
        // (fréquences des lettres pondérées par celles des mots de creole_dict.json)
        // contre 23,4 % et 17,5 %. La largeur était donc inversement proportionnelle
        // à l'usage. Sans "ò", chaque touche de la rangée passe à 32,4 dp (5,1 mm),
        // soit 3,3 dp de plus, et les deux rangées de dix touches deviennent
        // identiques (mesuré après coup avec scripts/geo_puces.py, la géométrie
        // rendue ne se déduisant pas des constantes).
        // "ò" ne pesant que 1,24 % des lettres frappées et restant accessible en
        // appui long sur "o" (AccentHandler, où il figure en tête, et affiché dans
        // l'indice de coin de la touche), l'échange se fait contre un appui long sur
        // un caractère rare. Même raisonnement que é et è, dédiés en rangée 4 où ils
        // ne coûtent aucune largeur aux lettres.
        val row1 = arrayOf("a", "z", "e", "r", "t", "y", "u", "i", "o", "p")
        val row2 = arrayOf("q", "s", "d", "f", "g", "h", "j", "k", "l", "m")
        // v10.11.4 : l'apostrophe redevient une touche visible, ici et non en
        // rangée 4. Cette rangée est celle qui peut le mieux se le permettre : ses
        // touches sont les plus larges du clavier (37,0 dp mesurés, contre 32,4 en
        // rangées 1 et 2 et 28,7 en rangée 4) et elle ne porte que 17,5 % de la
        // frappe. Le financement vient du poids de ⇧ et ⌫, ramené de 1,5 à 1,25
        // (getKeyWeight) : lettres et apostrophe à 34,7 dp, ⇧ et ⌫ à 43,4 dp, donc
        // tout reste plus large que les lettres des deux rangées du dessus, et la
        // barre d'espace n'est pas touchée.
        //
        // La v9.1.0 avait retiré cette touche au motif de « 0 occurrence dans
        // creole_dict.json ». Le constat est exact mais ne mesurait pas le bon
        // corpus : l'orthographe GEREC écrit l'élision avec un trait d'union
        // ("ba-w", "an-nou"), donc un dictionnaire de mots créoles ne peut pas
        // contenir d'apostrophe. En français, que ce clavier écrit aussi et pour
        // lequel il propose des suggestions, elle vaut 5,5 caractères pour mille
        // (prose du dépôt, 152 000 caractères), soit autant que la virgule (6,5) et
        // le point (7,1), qui ont tous deux une touche dédiée.
        //
        // Elle reste sous l'appui long de "," (AccentHandler), comme é et è restent
        // sous celui de "e" malgré leur touche dédiée.
        val row3 = arrayOf("⇧", "w", "x", "c", "v", "b", "n", "'", "⌫")
        // v8.6.0 : "-" ajouté en touche dédiée (21,7% des mots créoles en
        // contiennent un, fréquence cumulée supérieure à celle de "ò")
        // v9.1.0 : "'" retiré (0 occurrence dans creole_dict.json, contre 1088
        // mots pour "-") au profit d'une touche emoji dédiée ; l'apostrophe
        // reste accessible en appui long sur "," (AccentHandler).
        val row4 = arrayOf("123", ",", "é", "-", " ", "è", ".", "EMOJI", "⏎")
        
        mainLayout.addView(createKeyboardRow(row1))
        mainLayout.addView(createKeyboardRow(row2))
        mainLayout.addView(createKeyboardRow(row3))
        mainLayout.addView(createKeyboardRow(row4))
    }
    
    /**
     * Crée le layout numérique
     */
    private fun createNumericLayout(mainLayout: LinearLayout) {
        val row1 = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        val row2 = arrayOf("-", "/", ":", ";", "(", ")", "€", "&", "@", "\"")
        val row3 = arrayOf("=", ".", ",", "?", "!", "'", "+", "*", "⌫")
        val row4 = arrayOf("ABC", "EMOJI", " ", "⏎")

        mainLayout.addView(createKeyboardRow(row1))
        mainLayout.addView(createKeyboardRow(row2))
        mainLayout.addView(createKeyboardRow(row3))
        mainLayout.addView(createKeyboardRow(row4))
    }

    /**
     * Crée le layout emoji : jeu exhaustif (~1900 emojis de base, Unicode
     * 16.0, tons de peau en appui long) organisé en catégories avec onglets,
     * chaque catégorie défilant verticalement, le swipe latéral changeant de
     * catégorie (EmojiPickerView, RecyclerView/ViewPager2 virtualisés).
     * Accessible depuis le clavier alphabétique et depuis le mode 123.
     */
    private fun createEmojiLayout(mainLayout: LinearLayout) {
        val controlRow = arrayOf("ABC", "⌫", " ", "⏎")

        val picker = EmojiPickerView(context, accentHandler).apply {
            onEmojiSelected = { emoji -> interactionListener?.onKeyPress(emoji) }
        }

        mainLayout.addView(picker)
        mainLayout.addView(createKeyboardRow(controlRow))
    }
    
    /**
     * Crée une rangée de touches
     */
    private fun createKeyboardRow(keys: Array<String>): LinearLayout {
        val rowLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dpToPx(2), 0, dpToPx(2))
            }
        }
        
        val totalWeight = calculateRowWeight(keys)
        
        for (key in keys) {
            // createKeyButton() alimente déjà keyboardButtons avec la touche
            // interactive brute (avant l'éventuel enrobage des indices de coin) ;
            // un second ajout ici dupliquait chaque touche dans la liste.
            val button = createKeyButton(key, totalWeight)
            rowLayout.addView(button)
        }
        
        return rowLayout
    }
    
    /**
     * Crée un bouton de touche individuel (Button ou ImageButton selon le type)
     */
    private fun createKeyButton(key: String, totalWeight: Float): View {
        // Déterminer si on utilise une icône Material Design
        val useIcon = key in listOf("⌫", "⏎", "⇧")
        
        val button: View = if (useIcon) {
            // Créer un ImageButton pour les touches avec icônes
            android.widget.ImageButton(context).apply {
                // Définir l'icône selon la touche
                setImageResource(when (key) {
                    "⌫" -> R.drawable.ic_backspace
                    "⏎" -> R.drawable.ic_keyboard_return
                    "⇧" -> if (isCapsLock) R.drawable.ic_shift_caps
                           else if (isCapitalMode) R.drawable.ic_shift_on
                           else R.drawable.ic_shift_off
                    else -> R.drawable.ic_backspace // Fallback
                })
                
                // Teinter l'icône en blanc pour visibilité sur fond coloré
                setColorFilter(Color.WHITE)
                
                // Padding de l'icône, différent selon la touche et proportionnel à la
                // hauteur de touche : écrit en dur, il mangeait la même hauteur sur une
                // touche réduite, et l'icône y devenait deux fois plus petite qu'en
                // portrait (constaté en paysage, 12 dp de flèche dans une touche de 36).
                // Les rapports reprennent les valeurs d'origine, rapportées aux 48 dp
                // de la hauteur nominale.
                val iconPaddingRatio = when (key) {
                    "⏎" -> 8f / BUTTON_HEIGHT_DP  // Moins de padding pour l'icône Enter (plus grande)
                    "⌫" -> 10f / BUTTON_HEIGHT_DP // Padding moyen pour Backspace
                    else -> 12f / BUTTON_HEIGHT_DP // Padding normal pour Shift
                }
                val iconPadding = (keyHeightPx() * iconPaddingRatio).toInt()
                setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                
                // Description pour accessibilité
                contentDescription = when (key) {
                    "⌫" -> "Supprimer"
                    "⏎" -> "Entrée"
                    "⇧" -> "Majuscule"
                    else -> key
                }
                
                // Stocker la clé dans le tag pour identification
                tag = key
                
                // Calcul du poids selon le type de touche
                val weight = getKeyWeight(key)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    keyHeightPx(),
                    weight
                ).apply {
                    setMargins(
                        dpToPx(BUTTON_MARGIN_DP), 0, 
                        dpToPx(BUTTON_MARGIN_DP), 0
                    )
                }
            }
        } else {
            // Créer un Button classique pour les autres touches
            Button(context).apply {
                text = getDisplayText(key)
                // La touche brute est mémorisée ici, comme sur les ImageButton :
                // getKeyFromButton() la relisait depuis le libellé affiché, ce
                // qui ne survit pas aux touches dont le libellé diffère de la
                // touche (" " → "Potomitan™", "EMOJI" → "😀", "ABC"). Voir le
                // commentaire de getKeyFromButton().
                tag = key
                // « 123 » et « ABC » ne tiennent pas en pleine taille dans la
                // largeur d'une touche en portrait : le libellé se renvoyait à la
                // ligne et sa seconde ligne débordait sous le bas de la touche
                maxLines = 1
                // Le thème AppCompat d'une activité impose textAllCaps=true
                // aux Button : les touches doivent refléter exactement l'état
                // shift, quel que soit le contexte (IME ou clavier d'essai)
                isAllCaps = false
                // Button a une élévation/StateListAnimator implicite qui le
                // fait dessiner par-dessus ses voisins ajoutés après lui dans
                // un FrameLayout, quel que soit l'ordre d'ajout (constaté en
                // testant les indices d'appui long v8.3.0 : un enfant ajouté
                // après restait invisible tant que ceci n'était pas neutralisé).
                elevation = 0f
                stateListAnimator = null
                // Taille de police dérivée de la hauteur de touche, en pixels :
                // le clavier doit rester lisible sans dépendre de l'échelle de
                // police du système, qui pourrait faire déborder la lettre de sa
                // touche. Les libellés longs (Potomitan™ sur l'espace, les modes
                // "123" et "ABC") gardent leur taille réduite, sans quoi ils ne
                // tiennent plus sur une seule ligne dans une touche étroite.
                val labelRatio = if (key == " " || key == "123" || key == "ABC") {
                    WIDE_LABEL_TEXT_RATIO
                } else {
                    KEY_TEXT_HEIGHT_RATIO
                }
                setTextSize(TypedValue.COMPLEX_UNIT_PX, keyHeightPx() * labelRatio)
                setTypeface(typeface, Typeface.BOLD)
                // Le style Button par défaut apporte 30 px de padding sur chaque
                // bord, hérités de son fond d'origine. L'apparence des touches
                // vient entièrement du GradientDrawable posé plus bas, et ce
                // padding ne fait que rogner le texte : sur une touche réduite en
                // paysage il ne restait que 45 px pour une ligne de 57, coupant
                // les jambages (q, g, j, p, y) ; en portrait c'est lui qui
                // tronquait le libellé « 123 » en « 12 ».
                setPadding(0, 0, 0, 0)
                minHeight = 0
                minWidth = 0
                
                // Calcul du poids selon le type de touche
                val weight = getKeyWeight(key)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    keyHeightPx(),
                    weight
                ).apply {
                    setMargins(
                        dpToPx(BUTTON_MARGIN_DP), 0, 
                        dpToPx(BUTTON_MARGIN_DP), 0
                    )
                }
            }
        }
        
        // Application du style Guadeloupe
        applyGuadeloupeStyleToView(button, key)
        
        // Ajouter le bouton à la liste de suivi
        keyboardButtons.add(button)
        
        // Configuration des événements tactiles
        setupButtonInteractions(button, key)

        // Aperçu des options d'appui long dans les coins de la touche (v8.3.0)
        val hints = accentHandler?.takeIf { it.hasAccents(key) }?.getCornerHintsForKey(key)
        if (!hints.isNullOrEmpty()) {
            val onStartSide = accentHandler?.isCornerHintOnStartSide(key) == true
            return wrapWithLongPressHints(button, hints, onStartSide)
        }

        // 🌐 Indice visuel : l'appui long sur la barre d'espace change de clavier
        // système (voir setupSpaceLongPress). Seulement sur l'IME réel
        // (accentHandler non nul) : le clavier de démo des Réglages n'a pas de
        // fenêtre système pour ouvrir le sélecteur de claviers.
        if (key == " " && accentHandler != null) {
            return wrapWithSpaceGlobeHint(button)
        }

        return button
    }

    /**
     * Superpose un petit indice 🌐 dans le coin de la barre d'espace, pour rendre
     * découvrable l'appui long sans ajouter de touche dédiée qui réduirait la
     * largeur des touches déjà denses de la rangée du bas.
     */
    private fun wrapWithSpaceGlobeHint(inner: View): FrameLayout {
        val outerParams = inner.layoutParams
        inner.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        val hint = TextView(context).apply {
            text = "🌐"
            textSize = HINT_TEXT_SIZE_SP + 2f
            setTextColor(Color.parseColor("#CCFFFFFF")) // Même blanc semi-transparent que le texte Potomitan™
            isClickable = false
            isFocusable = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply {
                setMargins(0, dpToPx(2), dpToPx(4), 0)
            }
        }
        return FrameLayout(context).apply {
            layoutParams = outerParams
            addView(inner)
            addView(hint)
        }
    }

    /**
     * Enveloppe une touche dans un FrameLayout pour superposer, en haut et en
     * bas d'un même côté (droit par défaut, gauche si onStartSide), un aperçu
     * des deux premières options d'appui long. La touche d'origine garde
     * exactement sa zone tactile, son style et son ancrage pour la popup
     * d'accents (le FrameLayout se contente de prendre sa place dans la
     * rangée) ; keyboardButtons ne référence jamais ce FrameLayout, seulement la
     * touche brute qu'il contient.
     */
    private fun wrapWithLongPressHints(inner: View, hints: List<String>, onStartSide: Boolean): FrameLayout {
        val outerParams = inner.layoutParams
        inner.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        val horizontalGravity = if (onStartSide) Gravity.START else Gravity.END

        return FrameLayout(context).apply {
            layoutParams = outerParams
            addView(inner)
            addView(createHintLabel(hints[0], Gravity.TOP or horizontalGravity, onStartSide))
            if (hints.size > 1) {
                addView(createHintLabel(hints[1], Gravity.BOTTOM or horizontalGravity, onStartSide))
            }
        }
    }

    private fun createHintLabel(hintText: String, gravity: Int, onStartSide: Boolean): TextView {
        return TextView(context).apply {
            text = hintText
            textSize = HINT_TEXT_SIZE_SP
            setTextColor(Color.parseColor("#99333333"))
            isClickable = false
            isFocusable = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                gravity
            ).apply {
                if (onStartSide) {
                    setMargins(dpToPx(3), dpToPx(2), 0, dpToPx(2))
                } else {
                    setMargins(0, dpToPx(2), dpToPx(3), dpToPx(2))
                }
            }
        }
    }
    
    /**
     * Applique le style visuel spécifique à la Guadeloupe (supporte Button et ImageButton)
     */
    private fun applyGuadeloupeStyleToView(view: View, key: String) {
        val drawable = GradientDrawable().apply {
            cornerRadius = dpToPx(CORNER_RADIUS_DP.toInt()).toFloat()
            
            when (key) {
                "⇧" -> {
                    // Touche Shift avec nuance de blanc/gris
                    val colors = when {
                        isCapsLock -> intArrayOf(Color.parseColor("#E8E8E8"), Color.parseColor("#D0D0D0")) // Gris moyen activé
                        isCapitalMode -> intArrayOf(Color.parseColor("#F0F0F0"), Color.parseColor("#E0E0E0")) // Gris clair actif
                        else -> intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#F8F8F8")) // Blanc neutre
                    }
                    setColors(colors)
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
                "⌫" -> {
                    // Touche Supprimer avec couleur semi-transparente
                    setColors(intArrayOf(
                        Color.parseColor("#CCFFFFFF"), // Blanc semi-transparent
                        Color.parseColor("#C0F0F0F0")  // Gris très clair semi-transparent
                    ))
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
                "⏎" -> {
                    // Touche Entrée avec vert tropical
                    setColors(intArrayOf(
                        Color.parseColor("#00C853"), // Vert tropical vif
                        Color.parseColor("#00A843")  // Vert tropical foncé
                    ))
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
                ",", ".", "'", "-" -> {
                    // Touches virgule, point, apostrophe et trait d'union avec orange caraïbe
                    setColors(intArrayOf(
                        Color.parseColor("#FF8C00"), // Orange caraïbe vif
                        Color.parseColor("#FF7000")  // Orange caraïbe foncé
                    ))
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
                "123", "ABC", "EMOJI" -> {
                    // Touches de mode avec vert tropical
                    setColors(intArrayOf(
                        Color.parseColor("#00C853"), // Vert tropical vif
                        Color.parseColor("#00A843")  // Vert tropical foncé
                    ))
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
                "à", "è", "ò", "é", "ù", "ì", "ç" -> {
                    // Touches créoles avec nuance de blanc/gris
                    setColors(intArrayOf(
                        Color.parseColor("#FFFFFF"), // Blanc
                        Color.parseColor("#F8F8F8")  // Blanc cassé
                    ))
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
                " " -> {
                    // Barre d'espace avec bleu caraïbe
                    setColors(intArrayOf(
                        Color.parseColor("#1E90FF"), // Bleu caraïbe
                        Color.parseColor("#0000FF")  // Bleu pour dégradé
                    ))
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
                else -> {
                    // Touches normales avec gradient blanc/gris
                    setColors(intArrayOf(
                        Color.parseColor("#FFFFFF"),
                        Color.parseColor("#F5F5F5")
                    ))
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
            }
            
            // Bordure subtile
            setStroke(dpToPx(1), Color.parseColor("#D0D0D0"))
        }
        
        view.background = drawable
        
        // Couleur du texte (seulement pour Button, pas ImageButton)
        if (view is Button) {
            view.setTextColor(when (key) {
                "⇧" -> if (isCapsLock || isCapitalMode) Color.parseColor("#666666") else Color.parseColor("#333333")
                ",", ".", "'", "-" -> Color.WHITE // Texte blanc sur fond orange caraïbe
                "⏎", "123", "ABC", "EMOJI" -> Color.WHITE // Texte blanc sur fond vert tropical
                "à", "è", "ò", "é", "ù", "ì", "ç" -> Color.parseColor("#333333") // Texte gris foncé sur fond blanc
                " " -> Color.parseColor("#CCFFFFFF") // Blanc semi-transparent pour Potomitan™ - discret mais lisible
                else -> Color.parseColor("#333333")
            })
            
            // Ombre portée pour l'effet de profondeur
            // setShadowLayer() sous rendu accéléré matériellement est une source connue
            // de texte invisible sur certains GPU/drivers (rapporté sur Honor 200/SDK 36) ;
            // LAYER_TYPE_SOFTWARE force le rendu logiciel de cette vue pour l'éviter
            view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            view.setShadowLayer(SHADOW_RADIUS, 0f, dpToPx(1).toFloat(), Color.parseColor("#40000000"))
        }
        
        // Teinte de l'icône pour ImageButton
        if (view is android.widget.ImageButton) {
            // Couleur des icônes selon le type de touche
            when (key) {
                "⇧" -> view.setColorFilter(if (isCapsLock || isCapitalMode) Color.parseColor("#666666") else Color.parseColor("#333333"))
                "⌫" -> view.setColorFilter(Color.parseColor("#333333")) // Icône gris foncé sur fond blanc
                "⏎" -> view.setColorFilter(Color.WHITE) // Icône blanche sur fond vert tropical
                else -> view.setColorFilter(Color.WHITE)
            }
        }
    }
    
    /**
     * Applique le style visuel spécifique à la Guadeloupe (compatibilité avec ancien code)
     */
    private fun applyGuadeloupeStyle(button: Button, key: String) {
        applyGuadeloupeStyleToView(button, key)
    }
    
    /**
     * Configure les interactions tactiles pour un bouton
     */
    private fun setupButtonInteractions(button: View, key: String) {
        // Le son de frappe est joué explicitement par KeyFeedback, avec l'effet propre
        // à la touche. Sans cette ligne, performClick() y ajouterait son clic
        // d'interface générique et chaque touche sonnerait deux fois.
        button.isSoundEffectsEnabled = false

        // 🌐 Appui long personnalisé pour la barre d'espace (1 seconde)
        if (key == " ") {
            // Pas de setOnClickListener ici : setupSpaceLongPress() gère déjà le clic
            // court via son OnTouchListener. Les deux coexistant provoquaient un double
            // appel à onKeyPress() (l'OnTouchListener ne consomme jamais l'événement, donc
            // le clic natif se déclenchait aussi) → double espace inséré à chaque frappe.
            button.setOnLongClickListener(null) // Désactiver le listener par défaut
            setupSpaceLongPress(button, key)
        } else {
            button.setOnClickListener {
                interactionListener?.onKeyPress(key)
            }
            button.setOnLongClickListener {
                interactionListener?.onLongPress(key, button)
                true
            }
            // Animation tactile pour les touches autres que la barre d'espace
            addTouchAnimation(button, key)
        }
    }
    
    /**
     * 🌐 Configure l'appui long personnalisé de 1 seconde pour la barre d'espace
     */
    private fun setupSpaceLongPress(button: View, key: String) {
        button.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    isSpaceLongPressTriggered = false
                    
                    // Animation d'appui (100ms)
                    view.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(100)
                        .start()
                    
                    KeyFeedback.onKeyPress(view, key)
                    
                    // Démarrer le timer de 1 seconde pour l'appui long
                    spaceLongPressRunnable = Runnable {
                        isSpaceLongPressTriggered = true
                        Log.d(TAG, "⏱️ Appui long 1s détecté sur barre d'espace")
                        interactionListener?.onLongPress(key, button)
                    }
                    spaceLongPressHandler.postDelayed(spaceLongPressRunnable!!, SPACE_LONG_PRESS_DELAY)
                    
                    false
                }
                android.view.MotionEvent.ACTION_UP -> {
                    // Annuler le timer si relâché avant 1 seconde
                    spaceLongPressRunnable?.let { spaceLongPressHandler.removeCallbacks(it) }
                    
                    // Animation de relâchement (120ms)
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(120)
                        .start()
                    
                    interactionListener?.onKeyRelease()
                    
                    // Si relâché rapidement (pas d'appui long), c'est un clic normal
                    if (!isSpaceLongPressTriggered) {
                        interactionListener?.onKeyPress(key)
                    }
                    
                    false
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    // Annuler le timer en cas d'annulation
                    spaceLongPressRunnable?.let { spaceLongPressHandler.removeCallbacks(it) }
                    
                    // Animation de relâchement (120ms)
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(120)
                        .start()
                    
                    interactionListener?.onKeyRelease()
                    false
                }
                else -> false
            }
        }
    }
    
    /**
     * Animation d'appui, vibration et son de frappe sur un bouton de touche.
     *
     * Le retour part sur ACTION_DOWN, à l'instant où le doigt se pose, comme sur
     * tous les claviers : la frappe se sent et s'entend avant le relâchement. La
     * touche est passée pour que le son soit celui de sa nature (espace,
     * suppression, entrée ou frappe standard).
     */
    private fun addTouchAnimation(view: View, key: String) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // Animation d'appui (100ms comme l'original)
                    v.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(100)
                        .start()
                    
                    KeyFeedback.onKeyPress(v, key)
                    
                    false
                }
                android.view.MotionEvent.ACTION_UP, 
                android.view.MotionEvent.ACTION_CANCEL -> {
                    // Animation de relâchement (120ms comme l'original)
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(120)
                        .start()
                    
                    interactionListener?.onKeyRelease()
                    false
                }
                else -> false
            }
        }
    }
    
    /**
     * Met à jour l'affichage du clavier selon l'état actuel
     */
    
    /**
     * Met à jour les états internes du clavier
     */
    fun updateKeyboardStates(isNumeric: Boolean, isEmoji: Boolean, isCapital: Boolean, isCapsLock: Boolean) {
        Log.e("SHIFT_REAL_DEBUG", "🚨 UPDATING KEYBOARD STATES! isCapital=$isCapital, isCapsLock=$isCapsLock")
        this.isNumericMode = isNumeric
        this.isEmojiMode = isEmoji
        this.isCapitalMode = isCapital
        this.isCapsLock = isCapsLock
    }

    fun updateKeyboardDisplay() {
        Log.e("SHIFT_REAL_DEBUG", "🚨🚨🚨 updateKeyboardDisplay() CALLED! 🚨🚨🚨")
        
        val shiftButtons = keyboardButtons.filter { getKeyFromButton(it) == "⇧" }
        Log.e("SHIFT_REAL_DEBUG", "🔢 NOMBRE DE BOUTONS SHIFT TROUVÉS: ${shiftButtons.size}")
        Log.e("SHIFT_REAL_DEBUG", "📊 ÉTAT ACTUEL: isCapitalMode=$isCapitalMode, isCapsLock=$isCapsLock")
        
        keyboardButtons.forEach { button ->
            val key = getKeyFromButton(button)
            
            // Mise à jour du style pour la touche Shift
            if (key == "⇧") {
                Log.e("SHIFT_REAL_DEBUG", "🚨 UPDATING SHIFT BUTTON! isCapitalMode=$isCapitalMode, isCapsLock=$isCapsLock")
                
                // Si c'est un ImageButton, mettre à jour l'icône
                if (button is android.widget.ImageButton) {
                    val newIcon = if (isCapsLock) R.drawable.ic_shift_caps
                                  else if (isCapitalMode) R.drawable.ic_shift_on
                                  else R.drawable.ic_shift_off
                    button.setImageResource(newIcon)
                    // Le fond doit suivre l'état au même titre que l'icône :
                    // applyGuadeloupeStyleToView() prévoit un dégradé gris pour
                    // la majuscule enclenchée, mais seule la branche Button
                    // ci-dessous restylait la touche : shift étant une
                    // ImageButton, ce gris n'était jamais rendu et le fond
                    // restait blanc dans les trois états.
                    applyGuadeloupeStyleToView(button, key)
                    Log.e("SHIFT_REAL_DEBUG", "🎨 ICON UPDATED TO: ${if (isCapsLock) "CAPS" else if (isCapitalMode) "ON" else "OFF"}")
                } else if (button is Button) {
                    // Si c'est un Button classique, mettre à jour le texte
                    button.text = getDisplayText(key)
                    applyGuadeloupeStyle(button, key)
                }
                
                Log.e("SHIFT_REAL_DEBUG", "🚨 SHIFT STYLE APPLIED!")
            } else if (button is Button) {
                // Pour les autres touches, mettre à jour le texte normalement
                button.text = getDisplayText(key)
            }
        }
    }
    
    /**
     * Commute entre les modes majuscule/minuscule
     */
    fun toggleCapsMode(): Boolean {
        when {
            !isCapitalMode && !isCapsLock -> {
                isCapitalMode = true
                isCapsLock = false
            }
            isCapitalMode && !isCapsLock -> {
                isCapitalMode = true
                isCapsLock = true
            }
            else -> {
                isCapitalMode = false
                isCapsLock = false
            }
        }
        // ❌ SUPPRIMÉ: updateKeyboardDisplay() - déjà appelé par InputProcessor
        Log.e("SHIFT_REAL_DEBUG", "🚨 toggleCapsMode: isCapital=$isCapitalMode, isCapsLock=$isCapsLock")
        return isCapitalMode
    }
    
    /**
     * Commute entre mode alphabétique et numérique. Depuis le layout emoji, la
     * touche "ABC" partage ce même point d'entrée : dans ce cas on revient à
     * l'alphabétique (pas un toggle numérique, qui rouvrirait le mode 123).
     */
    fun switchKeyboardMode(): Boolean {
        if (isEmojiMode) {
            isEmojiMode = false
            isNumericMode = false
        } else {
            isNumericMode = !isNumericMode
        }
        return isNumericMode
    }
    
    /**
     * Retourne l'état actuel du mode numérique sans le modifier
     */
    fun isNumericMode(): Boolean {
        return isNumericMode
    }

    /**
     * Retourne l'état actuel du mode emoji sans le modifier
     */
    fun isEmojiMode(): Boolean {
        return isEmojiMode
    }

    /**
     * Active le layout emoji (accessible depuis le mode 123)
     */
    fun switchToEmojiMode() {
        isEmojiMode = true
        isNumericMode = false
        Log.d("KeyboardLayoutManager", "😀 MODE EMOJI ACTIVÉ")
    }

    /**
     * Force le mode alphabétique (pour l'initialisation)
     */
    fun switchKeyboardModeToAlphabetic() {
        isNumericMode = false
        isEmojiMode = false
        Log.d("KeyboardLayoutManager", "🔤 MODE FORCÉ À ALPHABÉTIQUE")
    }

    /**
     * Garantit que le clavier démarre en mode alphabétique
     */
    private fun ensureAlphabeticMode() {
        isNumericMode = false
        isEmojiMode = false
        isCapitalMode = false
        isCapsLock = false
        Log.d("KeyboardLayoutManager", "🚀 INITIALISATION : Mode alphabétique garanti")
    }
    
    /**
     * Force publiquement le retour au mode alphabétique
     */
    fun forceAlphabeticMode() {
        ensureAlphabeticMode()
        Log.d("KeyboardLayoutManager", "🔄 FORCE : Retour au mode alphabétique")
    }
    
    /**
     * Nettoie les ressources
     */
    fun cleanup() {
        keyboardButtons.forEach { button ->
            cleanupView(button)
        }
        keyboardButtons.clear()
        interactionListener = null
    }
    
    // Méthodes utilitaires privées
    
    private fun getDisplayText(key: String): String {
        return when (key) {
            " " -> "Potomitan™"
            "⇧" -> "⇧"
            "⌫" -> "⌫"
            "⏎" -> "⏎"
            "123" -> if (isNumericMode) "ABC" else "123"
            // Les rangées des modes 123 et EMOJI déclarent littéralement "ABC"
            // pour le retour à l'alphabétique ; sans cette branche elle tombait
            // dans le `else` et s'affichait "abc", en basculant en "ABC" au
            // gré du shift, un état qui ne la concerne pas.
            "ABC" -> "ABC"
            "EMOJI" -> "😀"
            // Caractères accentués créoles - respecter le mode majuscule/minuscule
            "à", "è", "ò", "é", "ù", "ì", "ç" -> if (isCapitalMode) key.uppercase() else key
            else -> if (isCapitalMode) key.uppercase() else key.lowercase()
        }
    }
    
    private fun getKeyWeight(key: String): Float {
        return when (key) {
            " " -> 4.0f      // Barre d'espace plus large
            // v10.11.4 : 1,5 → 1,25 pour financer l'apostrophe ajoutée en rangée 3
            // sans rétrécir les lettres sous la largeur des rangées 1 et 2. Ces deux
            // touches restent les plus larges de leur rangée, ce qui compte : elles
            // sont aux deux extrémités, là où la visée du pouce est la plus mauvaise.
            "⇧", "⌫" -> 1.25f
            else -> 1.0f     // Touches normales
        }
    }
    
    private fun calculateRowWeight(keys: Array<String>): Float {
        return keys.sumOf { getKeyWeight(it).toDouble() }.toFloat()
    }
    
    /**
     * Touche brute portée par une vue. Le tag fait foi : le libellé affiché ne
     * permet pas de la retrouver dès qu'il en diffère (" " → "Potomitan™",
     * "EMOJI" → "😀", "123" → "ABC" en mode numérique). Relire le libellé
     * renvoyait alors une fausse touche à getDisplayText(), qui la traitait
     * comme une lettre et la passait en majuscules au gré du shift.
     * Le repli sur le texte reste pour les vues créées hors createKeyButton().
     */
    private fun getKeyFromButton(button: View): String {
        (button.tag as? String)?.let { return it }
        return when (button) {
            is Button -> button.text.toString().lowercase()
            else -> ""
        }
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
    
    private fun cleanupView(view: View) {
        view.setOnClickListener(null)
        view.setOnLongClickListener(null)
        view.setOnTouchListener(null)
        view.background = null
        
        // Nettoyer les animations en cours
        view.animate().cancel()
        view.clearAnimation()
        
        // Nettoyer les références du parent
        (view.parent as? ViewGroup)?.removeView(view)
    }
}
