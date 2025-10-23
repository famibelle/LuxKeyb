package com.example.kreyolkeyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
        private const val SHADOW_RADIUS = 4f
        private const val TAG = "KeyboardLayoutManager"
        
        // 🌐 Délai pour l'appui long sur la barre d'espace (1 seconde)
        private const val SPACE_LONG_PRESS_DELAY = 1000L
    }
    
    // État du clavier
    private var isCapitalMode = false
    private var isCapsLock = false
    private var isNumericMode = false // FORCE ALPHABÉTIQUE PAR DÉFAUT
    private val keyboardButtons = mutableListOf<TextView>()
    
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
        fun onLongPress(key: String, button: TextView)
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
        val row1 = arrayOf("a", "z", "e", "r", "t", "y", "u", "i", "o", "p")
        val row2 = arrayOf("q", "s", "d", "f", "g", "h", "j", "k", "l", "m")
        val row3 = arrayOf("⇧", "w", "x", "c", "v", "b", "n", "⌫")
        val row4 = arrayOf("123", ",", "é", " ", "ë", ".", "'", "⏎")
        
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
        val row3 = arrayOf("=", ".", ",", "?", "!", "'", "\"", "_", "⌫")
        val row4 = arrayOf("ABC", " ", "⏎")
        
        mainLayout.addView(createKeyboardRow(row1))
        mainLayout.addView(createKeyboardRow(row2))
        mainLayout.addView(createKeyboardRow(row3))
        mainLayout.addView(createKeyboardRow(row4))
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
            val button = createKeyButton(key, totalWeight)
            rowLayout.addView(button)
            keyboardButtons.add(button)
        }
        
        return rowLayout
    }
    
    /**
     * Crée un bouton de touche individuel
     */
    private fun createKeyButton(key: String, totalWeight: Float): Button {
        val button = Button(context).apply {
            text = getDisplayText(key)
            // Taille de police personnalisée pour Potomitan™ branding discret
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
        
        // Application du style Guadeloupe
        applyGuadeloupeStyle(button, key)
        
        // Ajouter le bouton à la liste de suivi
        keyboardButtons.add(button)
        
        // Configuration des événements tactiles
        setupButtonInteractions(button, key)
        
        return button
    }
    
    /**
     * Applique le style visuel spécifique à la Guadeloupe
     */
    private fun applyGuadeloupeStyle(button: Button, key: String) {
        val drawable = GradientDrawable().apply {
            cornerRadius = dpToPx(CORNER_RADIUS_DP.toInt()).toFloat()
            
            when (key) {
                "⇧" -> {
                    // Touche Shift avec vert tropical guadeloupéen
                    val colors = when {
                        isCapsLock -> intArrayOf(Color.parseColor("#d9e6dfff"), Color.parseColor("#2E8B57")) // VERT TROPICAL ACTIVÉ
                        isCapitalMode -> intArrayOf(Color.parseColor("#3BAF77"), Color.parseColor("#228B22")) // VERT TROPICAL ACTIF
                        else -> intArrayOf(Color.parseColor("#3BAF77"), Color.parseColor("#32CD32")) // VERT TROPICAL
                    }
                    setColors(colors)
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
                "⌫" -> {
                    // Touche Supprimer avec corail
                    setColors(intArrayOf(
                        Color.parseColor("#FF7F50"), // Corail
                        Color.parseColor("#FF6347")  // Tomate pour dégradé
                    ))
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
                "⏎" -> {
                    // Touche Entrée avec jaune soleil
                    setColors(intArrayOf(
                        Color.parseColor("#FFD700"), // Jaune soleil
                        Color.parseColor("#FFA500")  // Orange pour dégradé
                    ))
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
                "123", "ABC" -> {
                    // Touches de mode avec vert tropical
                    setColors(intArrayOf(
                        Color.parseColor("#3BAF77"), // Vert tropical
                        Color.parseColor("#2E8B57")  // Vert forêt pour dégradé
                    ))
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
                "à", "è", "ò", "é", "ù", "ì", "ç" -> {
                    // Touches créoles avec vert tropical guadeloupéen
                    setColors(intArrayOf(
                        Color.parseColor("#3BAF77"), // Vert tropical
                        Color.parseColor("#2E8B57")  // Vert forêt pour dégradé
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
        
        button.background = drawable
        
        // Couleur du texte
        button.setTextColor(when (key) {
            "⇧" -> if (isCapsLock || isCapitalMode) Color.WHITE else Color.parseColor("#333333")
            "⌫", "⏎", "123", "ABC" -> Color.WHITE
            "à", "è", "ò", "é", "ù", "ì", "ç" -> Color.WHITE // Texte blanc sur fond vert
            " " -> Color.parseColor("#CCFFFFFF") // Blanc semi-transparent pour Potomitan™ - discret mais lisible
            else -> Color.parseColor("#333333")
        })
        
        // Ombre portée pour l'effet de profondeur
        button.setShadowLayer(SHADOW_RADIUS, 0f, dpToPx(1).toFloat(), Color.parseColor("#40000000"))
    }
    
    /**
     * Configure les interactions tactiles pour un bouton
     */
    private fun setupButtonInteractions(button: Button, key: String) {
        button.setOnClickListener {
            interactionListener?.onKeyPress(key)
        }
        
        // 🌐 Appui long personnalisé pour la barre d'espace (1 seconde)
        if (key == " ") {
            button.setOnLongClickListener(null) // Désactiver le listener par défaut
            setupSpaceLongPress(button, key)
        } else {
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
    private fun setupSpaceLongPress(button: Button, key: String) {
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
    private fun addTouchAnimation(button: Button) {
        button.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // Animation d'appui (100ms comme l'original)
                    view.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(100)
                        .start()
                    
                    // 📳 FEEDBACK HAPTIQUE MODERNE
                    performHapticFeedback(view)
                    
                    false
                }
                android.view.MotionEvent.ACTION_UP, 
                android.view.MotionEvent.ACTION_CANCEL -> {
                    // Animation de relâchement (120ms comme l'original)
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
    fun updateKeyboardStates(isNumeric: Boolean, isCapital: Boolean, isCapsLock: Boolean) {
        Log.e("SHIFT_REAL_DEBUG", "🚨 UPDATING KEYBOARD STATES! isCapital=$isCapital, isCapsLock=$isCapsLock")
        this.isNumericMode = isNumeric
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
            button.text = getDisplayText(key)
            
            // Mise à jour du style pour la touche Shift
            if (key == "⇧") {
                Log.e("SHIFT_REAL_DEBUG", "🚨 UPDATING SHIFT BUTTON! isCapitalMode=$isCapitalMode, isCapsLock=$isCapsLock")
                applyGuadeloupeStyle(button as Button, key)
                Log.e("SHIFT_REAL_DEBUG", "🚨 SHIFT STYLE APPLIED!")
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
     * Commute entre mode alphabétique et numérique
     */
    fun switchKeyboardMode(): Boolean {
        isNumericMode = !isNumericMode
        return isNumericMode
    }
    
    /**
     * Force le mode alphabétique (pour l'initialisation)
     */
    fun switchKeyboardModeToAlphabetic() {
        isNumericMode = false
        Log.d("KeyboardLayoutManager", "🔤 MODE FORCÉ À ALPHABÉTIQUE")
    }
    
    /**
     * Garantit que le clavier démarre en mode alphabétique
     */
    private fun ensureAlphabeticMode() {
        isNumericMode = false
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
            cleanupTextView(button)
        }
        keyboardButtons.clear()
        interactionListener = null
    }
    
    // Méthodes utilitaires privées
    
    private fun getDisplayText(key: String): String {
        return when (key) {
            " " -> "🇱🇺Potomitan™"
            "⇧" -> "⇧"
            "⌫" -> "⌫"
            "⏎" -> "⏎"
            "123" -> if (isNumericMode) "ABC" else "123"
            // Caractères accentués créoles - toujours affichés comme ils sont
            "à", "è", "ò", "é", "ù", "ì", "ç" -> key
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
    
    private fun getKeyFromButton(button: TextView): String {
        // Version simple : récupérer depuis le texte affiché
        return button.text.toString().lowercase()
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
    
    private fun cleanupTextView(textView: TextView) {
        textView.setOnClickListener(null)
        textView.setOnLongClickListener(null)
        textView.setOnTouchListener(null)
        textView.background = null
        
        // Nettoyer les animations en cours
        textView.animate().cancel()
        textView.clearAnimation()
        
        // Nettoyer les références du parent
        (textView.parent as? ViewGroup)?.removeView(textView)
    }
}
