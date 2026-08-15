package com.example.kreyolkeyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
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
        private const val BUTTON_MARGIN_DP = 2
        private const val CORNER_RADIUS_DP = 8f
        private const val TEXT_SIZE_SP = 16f
        private const val HINT_TEXT_SIZE_SP = 8f
        private const val SHADOW_RADIUS = 4f
        private const val TAG = "KeyboardLayoutManager"

        // Couleurs du drapeau luxembourgeois : rouge Pantone 032, blanc, et le
        // bleu ciel Pantone 299 — le bleu clair qui distingue ce drapeau de
        // celui des Pays-Bas.
        private const val ROUGE = "#ED2939"
        private const val BLANC = "#FFFFFF"
        private const val BLEU = "#00A1DE"

        private const val BLANC_ACTIF = "#E0E0E0"   // majuscule enclenchée
        private const val BORDURE = "#D0D0D0"
        private const val ENCRE = "#1A1A1A"
        private const val ENCRE_ATTENUEE = "#666666"

        // 🌐 Délai pour l'appui long sur la barre d'espace (1 seconde)
        private const val SPACE_LONG_PRESS_DELAY = 1000L
    }
    
    // État du clavier
    private var isCapitalMode = false
    private var isCapsLock = false
    private var isNumericMode = false // FORCE ALPHABÉTIQUE PAR DÉFAUT
    private var isEmojiMode = false
    private val keyboardButtons = mutableListOf<View>() // Changé de TextView à View pour supporter ImageButton

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
     * Crée le layout principal du clavier avec toutes les rangées
     */
    fun createKeyboardLayout(): LinearLayout {
        Log.d("KeyboardLayoutManager", "🎯 createKeyboardLayout - isNumericMode: $isNumericMode")
        
        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dpToPx(8), dpToPx(8), 
                dpToPx(8), dpToPx(8)
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
     * Crée le layout alphabétique (QWERTZ luxembourgeois)
     */
    private fun createAlphabeticLayout(mainLayout: LinearLayout) {
        // QWERTZ et non AZERTY : c'est la disposition des claviers physiques au
        // Luxembourg (suisse-français) et celle que partagent l'allemand et le
        // luxembourgeois écrit. L'AZERTY était un héritage créole, pas un choix
        // luxembourgeois — remplacé sans repli, l'application n'étant pas encore
        // publiée.
        val row1 = arrayOf("q", "w", "e", "r", "t", "z", "u", "i", "o", "p")
        // "é" occupe la case immédiatement à droite du "l", exactement là où le
        // QWERTZ suisse-français la place : c'est la diacritique n°1 du
        // luxembourgeois et cette position complète la rangée d'accueil à 10
        // touches, alignée sur les rangées 1 et 3.
        val row2 = arrayOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "é")
        val row3 = arrayOf("⇧", "y", "x", "c", "v", "b", "n", "m", "⌫")
        // Les deux autres diacritiques porteuses gardent leur touche dédiée.
        // Comptages sur le corpus brut POTOMITAN/luxembourgish-corpus (158
        // documents, 204 366 caractères) et non sur luxemburgish_dict.json,
        // dont les fréquences sont cumulées d'une régénération à l'autre :
        //   é 2596 · ë 1251 · ä 1004 | ü 155 · à 55 · ö 48 · ê 48 · è 33
        // Le décrochage après ä (4× moins fréquent que ü) est ce qui justifie
        // trois touches dédiées et pas quatre ; ü et les suivantes restent en
        // appui long sur "u", "a", "o" et "e".
        //
        // L'apostrophe gagne la touche dédiée que réclamaient les retours
        // utilisateurs, et le corpus le confirme : 649 occurrences (469 en ’
        // typographique, 180 en ' ASCII), soit plus que ü et 4,5× le trait
        // d'union (143). L'élision est structurelle en luxembourgeois — d'Land,
        // s'Kanner, hunn's. Attention, luxemburgish_dict.json l'affiche à zéro
        // et ce zéro ne veut rien dire : le tokenizer de LuxembourgishComplet.py
        // coupe les mots dessus. Le trait d'union reste donc en appui long sur
        // "." — 143 occurrences ici contre 21,7 % des mots en créole, où il
        // avait une touche à lui.
        val row4 = arrayOf("123", ",", "ä", " ", "ë", "'", ".", "EMOJI", "⏎")

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
                
                // Configurer la taille et le padding de l'icône (différent selon la touche)
                val iconPadding = when (key) {
                    "⏎" -> dpToPx(8)  // Moins de padding pour l'icône Enter (plus grande)
                    "⌫" -> dpToPx(10) // Padding moyen pour Backspace
                    "⇧" -> dpToPx(12) // Padding normal pour Shift
                    else -> dpToPx(12)
                }
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
                    dpToPx(BUTTON_HEIGHT_DP),
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
                // touche (" " → "LuxKeyb™", "EMOJI" → "😀", "ABC"). Voir le
                // commentaire de getKeyFromButton().
                tag = key
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
                // Taille de police personnalisée pour le branding LuxKeyb™ discret
                textSize = if (key == " ") TEXT_SIZE_SP * 0.75f else TEXT_SIZE_SP
                setTypeface(typeface, Typeface.BOLD)
                
                // Calcul du poids selon le type de touche
                val weight = getKeyWeight(key)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    dpToPx(BUTTON_HEIGHT_DP),
                    weight
                ).apply {
                    setMargins(
                        dpToPx(BUTTON_MARGIN_DP), 0, 
                        dpToPx(BUTTON_MARGIN_DP), 0
                    )
                }
            }
        }
        
        // Application du style des touches
        applyKeyStyleToView(button, key)
        
        // Ajouter le bouton à la liste de suivi
        keyboardButtons.add(button)
        
        // Configuration des événements tactiles
        setupButtonInteractions(button, key)

        // Aperçu des options d'appui long dans les coins de la touche (v8.3.0)
        val hints = accentHandler?.takeIf { it.hasAccents(key) }?.getCornerHintsForKey(key)
        if (!hints.isNullOrEmpty()) {
            val onStartSide = accentHandler?.isCornerHintOnStartSide(key) == true
            return wrapWithLongPressHints(button, hints, onStartSide, key)
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
            setTextColor(Color.parseColor("#CCFFFFFF")) // Même blanc semi-transparent que le texte LuxKeyb™
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
    private fun wrapWithLongPressHints(
        inner: View,
        hints: List<String>,
        onStartSide: Boolean,
        key: String
    ): FrameLayout {
        val outerParams = inner.layoutParams
        inner.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        val horizontalGravity = if (onStartSide) Gravity.START else Gravity.END

        return FrameLayout(context).apply {
            layoutParams = outerParams
            addView(inner)
            addView(createHintLabel(hints[0], Gravity.TOP or horizontalGravity, onStartSide, key))
            if (hints.size > 1) {
                addView(createHintLabel(hints[1], Gravity.BOTTOM or horizontalGravity, onStartSide, key))
            }
        }
    }

    private fun createHintLabel(
        hintText: String,
        gravity: Int,
        onStartSide: Boolean,
        key: String
    ): TextView {
        return TextView(context).apply {
            text = hintText
            textSize = HINT_TEXT_SIZE_SP
            // L'aperçu s'efface volontairement derrière le glyphe principal,
            // mais il doit rester lisible sur les touches colorées.
            setTextColor(Color.parseColor(hintInk(key)))
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
     * Applique le style visuel des touches (supporte Button et ImageButton)
     */
    private fun applyKeyStyleToView(view: View, key: String) {
        val drawable = GradientDrawable().apply {
            cornerRadius = dpToPx(CORNER_RADIUS_DP.toInt()).toFloat()
            setColor(Color.parseColor(keyBackground(key)))
            // Le blanc du drapeau étant aussi celui des touches de lettres, il
            // leur faut un contour pour rester distinctes du fond du clavier.
            setStroke(dpToPx(1), Color.parseColor(BORDURE))
        }

        view.background = drawable

        val encre = Color.parseColor(keyForeground(key))

        if (view is Button) {
            view.setTextColor(encre)

            // Ombre portée pour l'effet de profondeur.
            // setShadowLayer() sous rendu accéléré matériellement est une source connue
            // de texte invisible sur certains GPU/drivers (rapporté sur Honor 200/SDK 36) ;
            // LAYER_TYPE_SOFTWARE force le rendu logiciel de cette vue pour l'éviter
            view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            // Ombre claire sur les touches colorées, sombre sur les blanches :
            // une ombre noire sous du texte blanc le rend sale.
            val ombre = if (keyBackground(key) == ROUGE) "#40FFFFFF" else "#40000000"
            view.setShadowLayer(SHADOW_RADIUS, 0f, dpToPx(1).toFloat(), Color.parseColor(ombre))
        }

        if (view is android.widget.ImageButton) {
            view.setColorFilter(encre)
        }
    }

    /**
     * Fond d'une touche, aux trois couleurs du drapeau luxembourgeois.
     *
     * Les lettres occupent le blanc, majoritaire comme la bande centrale.
     * Le rouge marque ce qui agit : Entrée et les changements de mode. Le bleu
     * ciel revient à la barre d'espace et à la ponctuation, qui accompagnent la
     * frappe sans l'interrompre.
     */
    private fun keyBackground(key: String): String = when (key) {
        "⏎", "123", "ABC", "EMOJI" -> ROUGE
        " ", ",", ".", "'" -> BLEU
        "⇧" -> if (isCapsLock || isCapitalMode) BLANC_ACTIF else BLANC
        else -> BLANC
    }

    /**
     * Encre d'une touche, choisie sur le contraste réellement mesuré.
     *
     * Blanc sur le rouge du drapeau donne 4,2:1, suffisant pour les glyphes
     * larges et gras des touches. Blanc sur le bleu ciel ne donnerait que
     * 2,9:1 — illisible ; ce bleu porte donc une encre sombre (5,9:1).
     */
    private fun keyForeground(key: String): String = when (keyBackground(key)) {
        ROUGE -> BLANC
        BLANC_ACTIF -> ENCRE_ATTENUEE
        else -> ENCRE
    }

    /** Encre des aperçus de coin : même famille que le glyphe, en plus discret. */
    private fun hintInk(key: String): String =
        if (keyBackground(key) == ROUGE) "#CCFFFFFF" else "#99333333"

    /**
     * Surcharge de compatibilité pour les appels qui passent encore un Button.
     */
    private fun applyKeyStyle(button: Button, key: String) {
        applyKeyStyleToView(button, key)
    }
    
    /**
     * Configure les interactions tactiles pour un bouton
     */
    private fun setupButtonInteractions(button: View, key: String) {
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
            addTouchAnimation(button)
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
                    
                    // Feedback haptique
                    performHapticFeedback(view)
                    
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
     * Ajoute une animation tactile et feedback haptique au bouton
     */
    /**
     * Ajoute une animation tactile et feedback haptique au bouton
     */
    private fun addTouchAnimation(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // Animation d'appui (100ms comme l'original)
                    v.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(100)
                        .start()
                    
                    // 📳 FEEDBACK HAPTIQUE MODERNE
                    performHapticFeedback(v)
                    
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
     * Exécute le feedback haptique classique (comme dans la version originale)
     */
    private fun performHapticFeedback(view: android.view.View) {
        try {
            // Feedback haptique léger (identique à la version originale)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                view.performHapticFeedback(
                    android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            }
        } catch (e: Exception) {
            // Silencieusement ignorer si feedback haptique non supporté
            Log.d(TAG, "Feedback haptique non disponible: ${e.message}")
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
                    // keyBackground() prévoit BLANC_ACTIF pour la majuscule
                    // enclenchée, mais seule la branche Button ci-dessous
                    // restylait la touche — shift étant une ImageButton, ce gris
                    // n'était jamais rendu et le fond restait blanc dans les
                    // trois états.
                    applyKeyStyleToView(button, key)
                    Log.e("SHIFT_REAL_DEBUG", "🎨 ICON UPDATED TO: ${if (isCapsLock) "CAPS" else if (isCapitalMode) "ON" else "OFF"}")
                } else if (button is Button) {
                    // Si c'est un Button classique, mettre à jour le texte
                    button.text = getDisplayText(key)
                    applyKeyStyle(button, key)
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
            " " -> "LuxKeyb™"
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
            "⇧", "⌫" -> 1.5f // Touches de fonction plus larges
            else -> 1.0f     // Touches normales
        }
    }
    
    private fun calculateRowWeight(keys: Array<String>): Float {
        return keys.sumOf { getKeyWeight(it).toDouble() }.toFloat()
    }
    
    /**
     * Retrouve la touche brute d'un bouton. Le tag fait foi : le libellé
     * affiché ne suffit pas, parce que getDisplayText() en réécrit plusieurs
     * (" " → "LuxKeyb™", "EMOJI" → "😀", "123" → "ABC" en mode numérique).
     * Relire le libellé faisait alors passer la touche par le `else` de
     * getDisplayText(), qui la repassait en minuscules — la barre d'espace
     * s'affichait "luxkeyb™" dès le premier updateKeyboardDisplay().
     * Le repli sur le texte ne sert plus qu'aux boutons construits ailleurs.
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
