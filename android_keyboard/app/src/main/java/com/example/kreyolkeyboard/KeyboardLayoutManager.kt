package com.example.kreyolkeyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
    }
    
    // État du clavier
    private var isCapitalMode = false
    private var isCapsLock = false
    private var isNumericMode = false // FORCE ALPHABÉTIQUE PAR DÉFAUT
    private val keyboardButtons = mutableListOf<TextView>()
    
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
        val row1 = arrayOf("a", "z", "e", "r", "t", "y", "u", "i", "o", "ò", "p")
        val row2 = arrayOf("q", "s", "d", "f", "g", "h", "j", "k", "l", "m")
        val row3 = arrayOf("⇧", "w", "x", "c", "v", "b", "n", "⌫")
        val row4 = arrayOf("123", ",", "é", " ", "è", ".", "'", "⏎")
        
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
            textSize = TEXT_SIZE_SP
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
                    // Touche Shift avec couleurs ULTRA-VISIBLES pour test
                    val colors = when {
                        isCapsLock -> intArrayOf(Color.parseColor("#FFD700"), Color.parseColor("#FFA500")) // JAUNE BRILLANT
                        isCapitalMode -> intArrayOf(Color.parseColor("#FF0000"), Color.parseColor("#CC0000")) // ROUGE VIF  
                        else -> intArrayOf(Color.parseColor("#00FF00"), Color.parseColor("#00CC00")) // VERT VIF
                    }
                    setColors(colors)
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
                "⌫", "⏎", "123", "ABC" -> {
                    // Touches de fonction avec couleurs des Antilles
                    setColors(intArrayOf(
                        Color.parseColor("#FF6B35"), // Orange chaleureux
                        Color.parseColor("#E55A2B")
                    ))
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
                "à", "è", "ò", "é", "ù", "ì", "ç" -> {
                    // Touches créoles avec gradient spécial
                    setColors(intArrayOf(
                        Color.parseColor("#43A047"), // Vert tropical
                        Color.parseColor("#2E7D32")
                    ))
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
                " " -> {
                    // Barre d'espace avec gradient subtil
                    setColors(intArrayOf(
                        Color.parseColor("#FFFFFF"),
                        Color.parseColor("#F0F0F0")
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
        
        button.setOnLongClickListener { 
            interactionListener?.onLongPress(key, button)
            true
        }
        
        // Animation tactile
        addTouchAnimation(button)
    }
    
    /**
     * Ajoute une animation tactile au bouton
     */
    private fun addTouchAnimation(button: Button) {
        button.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                    false
                }
                android.view.MotionEvent.ACTION_UP, 
                android.view.MotionEvent.ACTION_CANCEL -> {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
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
    fun updateKeyboardStates(isNumeric: Boolean, isCapital: Boolean, isCapsLock: Boolean) {
        Log.e("SHIFT_REAL_DEBUG", "🚨 UPDATING KEYBOARD STATES! isCapital=$isCapital, isCapsLock=$isCapsLock")
        this.isNumericMode = isNumeric
        this.isCapitalMode = isCapital
        this.isCapsLock = isCapsLock
    }

    fun updateKeyboardDisplay() {
        Log.e("SHIFT_REAL_DEBUG", "🚨🚨🚨 updateKeyboardDisplay() CALLED! 🚨🚨🚨")
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
        updateKeyboardDisplay()
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
            " " -> "espace"
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
        // Logique pour retrouver la clé d'origine depuis le bouton
        // (à implémenter selon les besoins)
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
