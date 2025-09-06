package com.example.kreyolkeyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity
import android.util.Log
import android.widget.LinearLayout
import android.widget.Button
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
import org.json.JSONArray
import java.io.IOException
import android.view.MotionEvent
import android.os.Handler
import android.os.Looper
import android.widget.PopupWindow
import android.view.LayoutInflater

class KreyolInputMethodService : InputMethodService() {
    
    private val TAG = "KreyolIME"
    
    // 🇬🇵 PALETTE COULEURS GUADELOUPE 🇬🇵
    companion object {
        // Couleurs principales - Palette "Pur Guadeloupe"
        const val BLEU_CARAIBE = "#0080FF"        // Bleu des eaux caribéennes
        const val JAUNE_SOLEIL = "#FFD700"        // Jaune du soleil tropical
        const val VERT_CANNE = "#228B22"          // Vert de la canne à sucre
        const val NOIR_VOLCANIQUE = "#1C1C1C"    // Noir des racines volcaniques
        const val BLANC_CORAL = "#F8F8FF"        // Blanc du corail
        
        // Couleurs secondaires pour nuances
        const val BLEU_LAGON = "#87CEEB"          // Bleu plus clair du lagon
        const val ORANGE_COUCHER = "#FF8C00"     // Orange du coucher de soleil
        const val ROUGE_HIBISCUS = "#DC143C"     // Rouge de l'hibiscus
        const val BEIGE_SABLE = "#F5F5DC"        // Beige du sable fin
    }
    
    private var dictionary: List<Pair<String, Int>> = emptyList()
    private var currentWord = ""
    private var suggestionsView: LinearLayout? = null
    private var suggestionsViewId: Int = View.NO_ID
    
    // Variables pour l'appui long
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var isLongPressTriggered = false
    
    // Gestion des majuscules/minuscules
    private var isCapitalMode = false
    private var isCapsLock = false
    private var keyboardButtons = mutableListOf<Button>()
    private var isUpdatingKeyboard = false
    private var isNumericMode = false
    private var mainKeyboardLayout: LinearLayout? = null
    
    // Mapping des accents pour appui long
    private val accentMap = mapOf(
        "A" to arrayOf("à", "á", "â", "ä", "ã", "å", "æ"),
        "E" to arrayOf("é", "è", "ê", "ë"),
        "I" to arrayOf("í", "ì", "î", "ï"),
        "O" to arrayOf("ó", "ò", "ô", "ö", "õ", "ø"),
        "U" to arrayOf("ú", "ù", "û", "ü"),
        "C" to arrayOf("ç", "ć", "č"),
        "N" to arrayOf("ñ"),
        "Y" to arrayOf("ý", "ÿ")
    )
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "=== KREYOL IME SERVICE onCreate() APPELÉ ! ===")
        
        try {
            Log.d(TAG, "Initialisation du dictionnaire...")
            dictionary = emptyList()
            currentWord = "" // Reset du mot actuel
            loadDictionary() // Activer le chargement du dictionnaire
            Log.d(TAG, "Variables initialisées et dictionnaire chargé")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'initialisation", e)
        }
    }
    
    private fun updateSuggestions(input: String) {
        Log.d(TAG, "=== updateSuggestions appelée avec input: '$input' ===")
        Log.d(TAG, "suggestionsView est null: ${suggestionsView == null}")
        Log.d(TAG, "isNumericMode: $isNumericMode")
        
        // Vérifier la validité de suggestionsView
        if (suggestionsView == null) {
            Log.e(TAG, "suggestionsView est null ! Tentative de récupération...")
            // Essayer de récupérer la vue depuis le layout principal
            val currentView = mainKeyboardLayout
            if (currentView != null && currentView.childCount > 1) {
                val suggestionsContainer = currentView.getChildAt(1) as? HorizontalScrollView
                suggestionsView = suggestionsContainer?.getChildAt(0) as? LinearLayout
                if (suggestionsView != null) {
                    suggestionsViewId = suggestionsView!!.id
                    Log.d(TAG, "suggestionsView récupérée avec succès !")
                }
            }
            
            if (suggestionsView == null) {
                Log.e(TAG, "Impossible de récupérer suggestionsView, abandon de la mise à jour")
                return
            }
        }
        
        // En mode numérique, vider les suggestions
        if (isNumericMode) {
            suggestionsView?.removeAllViews()
            Log.d(TAG, "Mode numérique - suggestions vidées")
            return
        }
        
        suggestionsView?.removeAllViews()
        
        try {
            val suggestions = if (input.isEmpty()) {
                // Montrer les mots les plus fréquents quand pas d'input
                dictionary.take(8).map { it.first }
            } else {
                // Filtrer le dictionnaire par l'input actuel
                dictionary.filter { it.first.startsWith(input.lowercase(), ignoreCase = true) }
                    .take(8)
                    .map { it.first }
            }
            
            Log.d(TAG, "Suggestions trouvées pour '$input': ${suggestions.joinToString(", ")}")
            Log.d(TAG, "Nombre de suggestions: ${suggestions.size}")
            
            suggestions.forEach { suggestion ->
                val button = Button(this).apply {
                    text = suggestion
                    textSize = 14f
                    // 🇬🇵 Style Guadeloupe pour les suggestions
                    setBackgroundColor(Color.parseColor(ORANGE_COUCHER))
                    setTextColor(Color.parseColor(BLANC_CORAL))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(20, 12, 20, 12)
                    elevation = 2f
                    
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    ).apply {
                        marginEnd = 8
                    }
                    
                    setOnClickListener {
                        Log.d(TAG, "Suggestion sélectionnée: $suggestion")
                        // Remplacer le mot actuel par la suggestion
                        val inputConnection = currentInputConnection
                        if (inputConnection != null && currentWord.isNotEmpty()) {
                            // Supprimer le mot partiel
                            inputConnection.deleteSurroundingText(currentWord.length, 0)
                        }
                        // Insérer la suggestion complète
                        inputConnection?.commitText("$suggestion ", 1)
                        currentWord = ""
                        updateSuggestions("")
                    }
                }
                
                suggestionsView?.addView(button)
                Log.d(TAG, "Bouton de suggestion ajouté: $suggestion")
            }
            
            Log.d(TAG, "=== updateSuggestions terminée avec succès ===")
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la mise à jour des suggestions", e)
        }
    }

    private fun loadDictionary() {
        try {
            Log.d(TAG, "Chargement du dictionnaire créole...")
            val inputStream = assets.open("creole_dict.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            
            val jsonArray = JSONArray(jsonString)
            val tempList = mutableListOf<Pair<String, Int>>()
            
            for (i in 0 until jsonArray.length()) {
                val entry = jsonArray.getJSONArray(i)
                val word = entry.getString(0)
                val frequency = entry.getInt(1)
                tempList.add(Pair(word, frequency))
            }
            
            dictionary = tempList.sortedByDescending { it.second } // Trier par fréquence
            Log.d(TAG, "Dictionnaire chargé: ${dictionary.size} mots")
            
        } catch (e: IOException) {
            Log.e(TAG, "Erreur lors du chargement du dictionnaire", e)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du parsing du dictionnaire", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy() appelé !")
    }
    
    override fun onStartInput(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        Log.d(TAG, "=== KREYOL onStartInput appelé - restarting: $restarting ===")
        Log.d(TAG, "EditorInfo: $info")
    }
    
    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Log.d(TAG, "=== KREYOL onStartInputView appelé - restarting: $restarting ===")
        
        // Vérifier et initialiser suggestionsView si nécessaire
        if (suggestionsView == null) {
            Log.d(TAG, "suggestionsView est null, tentative de récupération depuis la vue existante")
            val currentView = mainKeyboardLayout
            if (currentView != null && suggestionsViewId != View.NO_ID) {
                suggestionsView = currentView.findViewById<LinearLayout>(suggestionsViewId)
                Log.d(TAG, "suggestionsView récupérée par ID: ${suggestionsView != null}")
            }
        }
        
        // Initialiser avec les suggestions de base
        if (suggestionsView != null) {
            Log.d(TAG, "Initialisation des suggestions de base")
            updateSuggestions("")
        } else {
            Log.e(TAG, "Impossible de récupérer suggestionsView !")
        }
        
        // Vérifier si on a une vue
        Log.d(TAG, "Vue d'entrée disponible, clavier devrait être visible")
    }
    
    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        
        Log.d(TAG, "onUpdateSelection - oldSel: $oldSelStart-$oldSelEnd, newSel: $newSelStart-$newSelEnd")
        
        // Si la sélection a changé ou le texte a été modifié depuis l'extérieur
        if (newSelStart != newSelEnd || oldSelStart != newSelStart) {
            // Essayer de récupérer le mot actuel depuis le curseur
            val inputConnection = currentInputConnection
            if (inputConnection != null && !isNumericMode) {
                try {
                    // Récupérer le texte avant le curseur pour détecter le mot en cours
                    val textBeforeCursor = inputConnection.getTextBeforeCursor(50, 0)?.toString() ?: ""
                    val words = textBeforeCursor.split(Regex("\\s+"))
                    val lastWord = if (words.isNotEmpty()) words.last() else ""
                    
                    // Mettre à jour currentWord seulement si différent et pas d'espace à la fin
                    if (lastWord != currentWord && !textBeforeCursor.endsWith(" ")) {
                        currentWord = lastWord
                        Log.d(TAG, "Synchronisation currentWord: '$currentWord'")
                        updateSuggestions(currentWord)
                    } else if (textBeforeCursor.endsWith(" ")) {
                        // Si l'utilisateur a ajouté un espace, vider currentWord
                        currentWord = ""
                        Log.d(TAG, "Espace détecté, currentWord vidé")
                        updateSuggestions("")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur lors de la synchronisation du mot actuel", e)
                }
            }
        }
    }
    
    override fun onFinishInput() {
        super.onFinishInput()
        Log.d(TAG, "onFinishInput appelé")
    }
    
    override fun onEvaluateFullscreenMode(): Boolean {
        Log.d(TAG, "onEvaluateFullscreenMode appelé")
        return false // Désactivé le mode plein écran
    }
    
    override fun onEvaluateInputViewShown(): Boolean {
        Log.d(TAG, "onEvaluateInputViewShown appelé")
        return true // Force l'affichage du clavier
    }

    override fun onCreateInputView(): View? {
        Log.d(TAG, "=== KREYOL onCreateInputView appelé ! ===")
        
        try {
            Log.d(TAG, "Création du clavier AZERTY avec support majuscules/minuscules...")
            
            // Réinitialiser la liste des boutons
            keyboardButtons.clear()
            
            // Créer le layout principal avec fond volcanique
            val mainLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor(NOIR_VOLCANIQUE))
                setPadding(8, 8, 8, 8)
            }
            
            // Stocker la référence pour les changements de mode
            mainKeyboardLayout = mainLayout
            
            // Titre du clavier - Style Guadeloupe
            val titleView = TextView(this).apply {
                text = "Klavié Kreyòl Karukera 🇬🇵"
                textSize = 16f
                setBackgroundColor(Color.parseColor(BLEU_CARAIBE))
                setTextColor(Color.parseColor(BLANC_CORAL))
                setPadding(16, 12, 16, 12)
                gravity = Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            mainLayout.addView(titleView)
            
            // Barre de suggestions - Style tropical
            val suggestionsContainer = HorizontalScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(Color.parseColor(BEIGE_SABLE))
                setPadding(8, 8, 8, 8)
            }
            
            suggestionsView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                id = View.generateViewId() // Donner un ID unique
            }
            
            // Stocker l'ID pour récupération ultérieure
            suggestionsViewId = suggestionsView!!.id
            
            suggestionsContainer.addView(suggestionsView)
            mainLayout.addView(suggestionsContainer)
            
            // Initialiser avec les mots les plus fréquents
            updateSuggestions("")
            
            // Créer le clavier selon le mode
            createKeyboardLayout(mainLayout)
            
            // Mettre à jour l'affichage initial du clavier
            updateKeyboardDisplay()
            
            Log.d(TAG, "=== CLAVIER KREYÒL CRÉÉ AVEC SUCCÈS ! suggestionsView: ${suggestionsView != null} ===")
            return mainLayout
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la création de la vue du clavier", e)
            return null
        }
    }
    
    // 🇬🇵 FONCTION DE STYLE GUADELOUPE
    private fun applyGuadeloupeStyle(button: Button, key: String) {
        when {
            // Touches de lettres - Blanc corail sur fond bleu caraïbe
            key.length == 1 && key.matches(Regex("[a-zA-Z]")) -> {
                button.setBackgroundColor(Color.parseColor(BLANC_CORAL))
                button.setTextColor(Color.parseColor(BLEU_CARAIBE))
                button.setTypeface(null, android.graphics.Typeface.BOLD)
                button.textSize = 16f
            }
            
            // Touches spéciales importantes - Jaune soleil
            key in arrayOf("⌫", "⏎", "↑", "ABC", "123") -> {
                button.setBackgroundColor(Color.parseColor(JAUNE_SOLEIL))
                button.setTextColor(Color.parseColor(NOIR_VOLCANIQUE))
                button.setTypeface(null, android.graphics.Typeface.BOLD)
                button.textSize = 15f
            }
            
            // Barre d'espace - Vert canne à sucre avec texte spécial
            key == "ESPACE" -> {
                button.setBackgroundColor(Color.parseColor(VERT_CANNE))
                button.setTextColor(Color.parseColor(BLANC_CORAL))
                button.setTypeface(null, android.graphics.Typeface.BOLD)
                button.text = "🇬🇵 ESPACE • Potomitan™"
                button.textSize = 12f
            }
            
            // Touches numériques - Bleu lagon
            key.matches(Regex("[0-9]")) -> {
                button.setBackgroundColor(Color.parseColor(BLEU_LAGON))
                button.setTextColor(Color.parseColor(NOIR_VOLCANIQUE))
                button.setTypeface(null, android.graphics.Typeface.BOLD)
                button.textSize = 16f
            }
            
            // Autres touches de ponctuation - Beige sable
            else -> {
                button.setBackgroundColor(Color.parseColor(BEIGE_SABLE))
                button.setTextColor(Color.parseColor(NOIR_VOLCANIQUE))
                button.setTypeface(null, android.graphics.Typeface.NORMAL)
                button.textSize = 15f
            }
        }
        
        // Bordure subtile et padding élégant pour toutes les touches
        button.setPadding(12, 16, 12, 16)
        button.elevation = 4f // Légère ombre pour l'effet 3D
    }
    
    private fun createKeyboardRow(keys: Array<String>): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(4, 4, 4, 4)
        }
        
        for (key in keys) {
            val button = Button(this).apply {
                text = key
                tag = key // Stocker la valeur originale dans le tag
                textSize = 14f
                
                // 🇬🇵 DESIGN GUADELOUPE : Appliquer les couleurs selon le type de touche
                applyGuadeloupeStyle(this, key)
                
                // Gérer la taille des boutons
                val params = LinearLayout.LayoutParams(
                    if (key == "ESPACE") 0 else ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                if (key == "ESPACE") {
                    params.weight = 3f // L'espace prend plus de place
                } else {
                    params.weight = 1f
                }
                params.setMargins(3, 3, 3, 3) // Légèrement plus d'espace
                layoutParams = params
                
                // Gestion des événements tactiles pour l'appui long
                setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            isLongPressTriggered = false
                            // Vérifier si cette touche a des accents (uniquement pour les lettres)
                            if (key.length == 1 && key.matches(Regex("[a-zA-Z]")) && accentMap.containsKey(key.uppercase())) {
                                startLongPressTimer(key, this)
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            cancelLongPress()
                            if (!isLongPressTriggered) {
                                // Appui court normal
                                handleKeyPress(key)
                            }
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            cancelLongPress()
                            true
                        }
                        else -> false
                    }
                }
            }
            
            // Ajouter le bouton à la liste pour la gestion des majuscules
            keyboardButtons.add(button)
            row.addView(button)
        }
        
        return row
    }
    
    private fun updateKeyboardDisplay() {
        if (isUpdatingKeyboard) {
            Log.d(TAG, "Mise à jour du clavier déjà en cours, ignorée")
            return
        }
        
        isUpdatingKeyboard = true
        
        try {
            keyboardButtons.forEach { button ->
                val originalText = button.tag as? String ?: button.text.toString().lowercase()
                val displayText = if (isCapitalMode || isCapsLock) {
                    originalText.uppercase()
                } else {
                    originalText.lowercase()
                }
                
                // Mettre à jour l'affichage du bouton seulement si nécessaire
                val newText = when (originalText) {
                    "⇧" -> "⇧" // Toujours le même symbole
                    "⌫", "⏎", "ESPACE", "123", "ABC" -> originalText
                    "1", "2", "3", "4", "5", "6", "7", "8", "9", "0" -> originalText // Chiffres
                    "@", "#", "$", "%", "&", "-", "+", "(", ")", "/", "*", "\"", "'", ":", ";", "!", "?", ",", "." -> originalText // Symboles
                    else -> if (isNumericMode) originalText else displayText // En mode numérique, pas de changement de casse
                }
                
                // Mettre à jour seulement si le texte a changé
                if (button.text.toString() != newText) {
                    button.text = newText
                }
                
                // Colorer la touche Shift selon son état
                if (originalText == "⇧") {
                    val newColor = when {
                        isCapsLock -> Color.BLUE
                        isCapitalMode -> Color.CYAN
                        else -> Color.LTGRAY
                    }
                    
                    // Mettre à jour seulement si la couleur a changé
                    if (button.background !is android.graphics.drawable.ColorDrawable || 
                        (button.background as? android.graphics.drawable.ColorDrawable)?.color != newColor) {
                        button.setBackgroundColor(newColor)
                    }
                }
            }
            
            Log.d(TAG, "Clavier mis à jour - Mode majuscule: $isCapitalMode, Caps Lock: $isCapsLock")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la mise à jour du clavier", e)
        } finally {
            isUpdatingKeyboard = false
        }
    }
    
    private fun createKeyboardLayout(mainLayout: LinearLayout) {
        // Sauvegarder la référence aux suggestions AVANT suppression
        val savedSuggestionsView = suggestionsView
        val savedSuggestionsViewId = suggestionsViewId
        
        // Supprimer les rangées existantes (garder titre et suggestions)
        val childCount = mainLayout.childCount
        for (i in childCount - 1 downTo 2) { // Garder les 2 premiers enfants (titre + suggestions)
            mainLayout.removeViewAt(i)
        }
        
        // Restaurer la référence aux suggestions après suppression
        if (savedSuggestionsView != null && savedSuggestionsViewId != View.NO_ID) {
            suggestionsView = savedSuggestionsView
            suggestionsViewId = savedSuggestionsViewId
            Log.d(TAG, "Référence suggestionsView restaurée: ${suggestionsView != null}")
        } else {
            // Fallback : chercher dans la hiérarchie
            if (mainLayout.childCount > 1) {
                val suggestionsContainer = mainLayout.getChildAt(1) as? HorizontalScrollView
                suggestionsView = suggestionsContainer?.getChildAt(0) as? LinearLayout
                if (suggestionsView != null) {
                    suggestionsViewId = suggestionsView!!.id
                }
                Log.d(TAG, "Référence suggestionsView trouvée par fallback: ${suggestionsView != null}")
            }
        }
        
        if (isNumericMode) {
            // Mode numérique
            val row1 = createKeyboardRow(arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"))
            mainLayout.addView(row1)
            
            val row2 = createKeyboardRow(arrayOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/"))
            mainLayout.addView(row2)
            
            val row3 = createKeyboardRow(arrayOf("*", "\"", "'", ":", ";", "!", "?", "⌫"))
            mainLayout.addView(row3)
            
            val row4 = createKeyboardRow(arrayOf("ABC", ",", "ESPACE", ".", "⏎"))
            mainLayout.addView(row4)
        } else {
            // Mode alphabétique AZERTY
            val row1 = createKeyboardRow(arrayOf("a", "z", "e", "r", "t", "y", "u", "i", "o", "p"))
            mainLayout.addView(row1)
            
            val row2 = createKeyboardRow(arrayOf("q", "s", "d", "f", "g", "h", "j", "k", "l", "m"))
            mainLayout.addView(row2)
            
            val row3 = createKeyboardRow(arrayOf("⇧", "w", "x", "c", "v", "b", "n", "⌫"))
            mainLayout.addView(row3)
            
            val row4 = createKeyboardRow(arrayOf("123", "ESPACE", "⏎"))
            mainLayout.addView(row4)
        }
        
        // Rafraîchir les suggestions après reconstruction
        Log.d(TAG, "Reconstruction du clavier terminée, suggestionsView: ${suggestionsView != null}")
    }
    
    private fun switchKeyboardMode() {
        Log.d(TAG, "Basculement de mode - Actuel: ${if (isNumericMode) "Numérique" else "Alphabétique"}")
        
        isNumericMode = !isNumericMode
        
        // Réinitialiser le mode majuscule en passant au mode numérique
        if (isNumericMode) {
            isCapitalMode = false
            isCapsLock = false
            currentWord = "" // Réinitialiser le mot en cours
        }
        
        // Recréer le clavier avec le nouveau mode
        val currentView = mainKeyboardLayout
        if (currentView != null) {
            keyboardButtons.clear() // Nettoyer la liste des boutons
            createKeyboardLayout(currentView)
            updateKeyboardDisplay()
            
            // Forcer la mise à jour des suggestions après reconstruction
            Handler(Looper.getMainLooper()).post {
                Log.d(TAG, "Post-reconstruction: suggestionsView = ${suggestionsView != null}")
                if (!isNumericMode) {
                    // Mode alphabétique - restaurer les suggestions
                    updateSuggestions(currentWord)
                    Log.d(TAG, "Suggestions restaurées pour mode alphabétique avec mot: '$currentWord'")
                } else {
                    // Mode numérique - vider les suggestions
                    updateSuggestions("")
                    Log.d(TAG, "Suggestions vidées pour mode numérique")
                }
            }
        }
        
        Log.d(TAG, "Mode basculé vers: ${if (isNumericMode) "Numérique" else "Alphabétique"}")
    }
    
    private fun startLongPressTimer(key: String, button: Button) {
        longPressRunnable = Runnable {
            isLongPressTriggered = true
            showAccentPopup(key, button)
        }
        longPressHandler.postDelayed(longPressRunnable!!, 500) // 500ms pour déclencher l'appui long
    }
    
    private fun cancelLongPress() {
        longPressRunnable?.let {
            longPressHandler.removeCallbacks(it)
        }
        longPressRunnable = null
    }
    
    private fun showAccentPopup(baseKey: String, anchorButton: Button) {
        val accents = accentMap[baseKey.uppercase()] ?: return
        
        Log.d(TAG, "Affichage popup accents pour $baseKey: ${accents.joinToString()}")
        
        // Créer un layout horizontal pour les accents
        val popupLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE)
            setPadding(8, 8, 8, 8)
        }
        
        // Ajouter la lettre de base en premier
        val baseButton = Button(this).apply {
            text = baseKey.lowercase()
            textSize = 18f
            setBackgroundColor(Color.LTGRAY)
            setTextColor(Color.BLACK)
            setPadding(16, 12, 16, 12)
            setOnClickListener {
                handleKeyPress(baseKey)
                dismissAccentPopup()
            }
        }
        popupLayout.addView(baseButton)
        
        // Ajouter les accents
        accents.forEach { accent ->
            val accentButton = Button(this).apply {
                text = accent
                textSize = 18f
                setBackgroundColor(Color.parseColor("#E3F2FD")) // Bleu très clair
                setTextColor(Color.parseColor("#1976D2")) // Bleu foncé
                setPadding(16, 12, 16, 12)
                setOnClickListener {
                    handleAccentSelection(accent)
                    dismissAccentPopup()
                }
            }
            popupLayout.addView(accentButton)
        }
        
        // Créer et afficher le popup
        currentAccentPopup = PopupWindow(
            popupLayout,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 8f
            showAsDropDown(anchorButton, 0, -anchorButton.height - 20)
        }
    }
    
    private var currentAccentPopup: PopupWindow? = null
    
    private fun dismissAccentPopup() {
        currentAccentPopup?.dismiss()
        currentAccentPopup = null
    }
    
    private fun handleAccentSelection(accent: String) {
        Log.d(TAG, "Accent sélectionné: $accent")
        
        val inputConnection = currentInputConnection
        if (inputConnection != null) {
            // Appliquer le mode majuscule/minuscule à l'accent si nécessaire
            val finalAccent = if (isCapitalMode || isCapsLock) {
                accent.uppercase()
            } else {
                accent.lowercase()
            }
            
            inputConnection.commitText(finalAccent, 1)
            currentWord += finalAccent
            Log.d(TAG, "Mot actuel après accent: '$currentWord' (longueur: ${currentWord.length})")
            Log.d(TAG, "Caractères dans currentWord: ${currentWord.map { it.code }.joinToString(",")}")
            updateSuggestions(currentWord)
            
            // Désactiver le mode majuscule après un accent (sauf si Caps Lock)
            if (isCapitalMode && !isCapsLock) {
                isCapitalMode = false
                // Post la mise à jour pour éviter les conflits
                Handler(Looper.getMainLooper()).post {
                    updateKeyboardDisplay()
                }
            }
        } else {
            Log.w(TAG, "InputConnection est null lors de la sélection d'accent !")
        }
    }
    
    private fun handleKeyPress(key: String) {
        Log.d(TAG, "Touche pressée: $key")
        
        val inputConnection = currentInputConnection
        if (inputConnection != null) {
            when (key) {
                "⌫" -> {
                    // Gestion du backspace
                    inputConnection.deleteSurroundingText(1, 0)
                    if (currentWord.isNotEmpty()) {
                        currentWord = currentWord.dropLast(1)
                        Log.d(TAG, "Backspace - Mot après effacement: '$currentWord'")
                        updateSuggestions(currentWord)
                    } else {
                        // Si currentWord est déjà vide, réinitialiser avec suggestions par défaut
                        Log.d(TAG, "Backspace - Mot vide, affichage suggestions par défaut")
                        updateSuggestions("")
                    }
                }
                "⏎" -> {
                    // Touche Entrée
                    inputConnection.sendKeyEvent(
                        android.view.KeyEvent(
                            android.view.KeyEvent.ACTION_DOWN,
                            android.view.KeyEvent.KEYCODE_ENTER
                        )
                    )
                    inputConnection.sendKeyEvent(
                        android.view.KeyEvent(
                            android.view.KeyEvent.ACTION_UP,
                            android.view.KeyEvent.KEYCODE_ENTER
                        )
                    )
                    currentWord = ""
                    updateSuggestions("")
                }
                "ESPACE" -> {
                    // Espace termine le mot actuel
                    inputConnection.commitText(" ", 1)
                    currentWord = ""
                    updateSuggestions("")
                }
                "123" -> {
                    // Basculer vers le mode numérique
                    switchKeyboardMode()
                }
                "ABC" -> {
                    // Basculer vers le mode alphabétique
                    switchKeyboardMode()
                }
                "⇧" -> {
                    // Gestion de la touche Shift (seulement en mode alphabétique)
                    if (!isNumericMode) {
                        handleShiftPress()
                    }
                }
                else -> {
                    if (isNumericMode) {
                        // Mode numérique - insérer chiffres et symboles directement
                        inputConnection.commitText(key, 1)
                        // En mode numérique, on ne fait pas de suggestions de mots
                        Log.d(TAG, "Caractère numérique/symbole inséré: $key")
                    } else {
                        // Mode alphabétique - appliquer le mode majuscule/minuscule
                        val textToInsert = if (isCapitalMode || isCapsLock) {
                            key.uppercase()
                        } else {
                            key.lowercase()
                        }
                        
                        inputConnection.commitText(textToInsert, 1)
                        currentWord += textToInsert
                        // Réduire les logs pour éviter le spam
                        if (currentWord.length <= 3) { // Log seulement pour les premiers caractères
                            Log.d(TAG, "Mot actuel: '$currentWord'")
                        }
                        updateSuggestions(currentWord)
                        
                        // Désactiver le mode majuscule après une lettre (sauf si Caps Lock)
                        if (isCapitalMode && !isCapsLock) {
                            isCapitalMode = false
                            // Post la mise à jour pour éviter les conflits
                            Handler(Looper.getMainLooper()).post {
                                updateKeyboardDisplay()
                            }
                        }
                    }
                }
            }
        } else {
            Log.w(TAG, "InputConnection est null !")
        }
    }
    
    private fun handleShiftPress() {
        Log.d(TAG, "Touche Shift pressée - Mode actuel: Capital=$isCapitalMode, CapsLock=$isCapsLock")
        
        val previousCapitalMode = isCapitalMode
        val previousCapsLock = isCapsLock
        
        when {
            isCapsLock -> {
                // Déjà en Caps Lock, désactiver complètement
                isCapsLock = false
                isCapitalMode = false
                Log.d(TAG, "Caps Lock désactivé")
            }
            isCapitalMode -> {
                // Déjà en mode majuscule, activer Caps Lock
                isCapsLock = true
                isCapitalMode = false
                Log.d(TAG, "Caps Lock activé")
            }
            else -> {
                // Mode normal, activer mode majuscule
                isCapitalMode = true
                isCapsLock = false
                Log.d(TAG, "Mode majuscule activé")
            }
        }
        
        // Mettre à jour seulement si l'état a changé
        if (previousCapitalMode != isCapitalMode || previousCapsLock != isCapsLock) {
            // Post la mise à jour pour éviter les conflits
            Handler(Looper.getMainLooper()).post {
                updateKeyboardDisplay()
            }
        }
    }
}
