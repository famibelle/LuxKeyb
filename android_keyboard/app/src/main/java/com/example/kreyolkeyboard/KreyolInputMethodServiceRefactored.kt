package com.example.kreyolkeyboard

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.HorizontalScrollView
import android.widget.Button
import android.graphics.Color
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import kotlinx.coroutines.*

/**
 * Service principal du clavier créole refactorisé
 * Version modulaire utilisant des composants séparés pour une meilleure maintenabilité
 */
class KreyolInputMethodServiceRefactored : InputMethodService(),
    KeyboardLayoutManager.KeyboardInteractionListener,
    SuggestionEngine.SuggestionListener,
    AccentHandler.AccentSelectionListener,
    InputProcessor.InputProcessorListener {
    
    companion object {
        private const val TAG = "KreyolIME-Potomitan™"
        private const val MAX_SUGGESTIONS = 3
    }
    
    // Composants modulaires
    private lateinit var keyboardLayoutManager: KeyboardLayoutManager
    private lateinit var suggestionEngine: SuggestionEngine
    private lateinit var accentHandler: AccentHandler
    private lateinit var inputProcessor: InputProcessor
    
    // Vues principales
    private var suggestionsView: LinearLayout? = null
    private var mainKeyboardView: View? = null
    
    // État du service
    private var isInitialized = false
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "=== KREYOL IME SERVICE REFACTORISÉ onCreate() ===")
        
        try {
            initializeComponents()
            Log.d(TAG, "Service initialisé avec succès")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'initialisation: ${e.message}", e)
        }
    }
    
    /**
     * Initialise tous les composants modulaires
     */
    private fun initializeComponents() {
        // Créer les gestionnaires
        keyboardLayoutManager = KeyboardLayoutManager(this).apply {
            setInteractionListener(this@KreyolInputMethodServiceRefactored)
        }
        
        suggestionEngine = SuggestionEngine(this).apply {
            setSuggestionListener(this@KreyolInputMethodServiceRefactored)
        }
        
        accentHandler = AccentHandler(this).apply {
            setAccentSelectionListener(this@KreyolInputMethodServiceRefactored)
        }
        
        inputProcessor = InputProcessor(this).apply {
            setInputProcessorListener(this@KreyolInputMethodServiceRefactored)
        }
        
        // Initialiser le moteur de suggestions de façon asynchrone
        GlobalScope.launch {
            suggestionEngine.initialize()
            isInitialized = true
        }
    }
    
    override fun onCreateInputView(): View? {
        Log.d(TAG, "onCreateInputView() appelée")
        
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }
        
        // Créer la zone de suggestions
        createSuggestionsArea(mainLayout)
        
        // Créer le clavier principal
        val keyboardLayout = keyboardLayoutManager.createKeyboardLayout()
        mainLayout.addView(keyboardLayout)
        mainKeyboardView = keyboardLayout
        
        return mainLayout
    }
    
    /**
     * Crée la zone des suggestions
     */
    private fun createSuggestionsArea(parentLayout: LinearLayout) {
        val suggestionsContainer = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(48)
            )
            setBackgroundColor(Color.parseColor("#FFFFFF"))
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
        }
        
        suggestionsView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        suggestionsContainer.addView(suggestionsView)
        parentLayout.addView(suggestionsContainer)
    }
    
    // ===== IMPLÉMENTATION KeyboardInteractionListener =====
    
    override fun onKeyPress(key: String) {
        Log.d(TAG, "=== TOUCHE PRESSÉE: '$key' ===")
        
        if (accentHandler.isLongPressActive()) {
            Log.d(TAG, "Appui long actif - ignorer l'appui court")
            return
        }
        
        Log.d(TAG, "InputProcessor initialisé: ${::inputProcessor.isInitialized}")
        Log.d(TAG, "Traitement de la touche par InputProcessor")
        inputProcessor.processKeyPress(key)
        Log.d(TAG, "=== FIN TRAITEMENT TOUCHE ===")
    }
    
    override fun onLongPress(key: String, button: TextView) {
        Log.d(TAG, "Appui long sur: $key")
        
        if (accentHandler.hasAccents(key)) {
            accentHandler.startLongPressTimer(key, button)
        }
    }
    
    override fun onKeyRelease() {
        accentHandler.cancelLongPress()
    }
    
    // ===== IMPLÉMENTATION SuggestionListener =====
    
    override fun onSuggestionsReady(suggestions: List<String>) {
        displaySuggestions(suggestions)
    }
    
    override fun onDictionaryLoaded(wordCount: Int) {
        Log.d(TAG, "Dictionnaire chargé: $wordCount mots")
    }
    
    override fun onNgramModelLoaded() {
        Log.d(TAG, "Modèle N-gram chargé")
    }
    
    override fun onModeChanged(newMode: SuggestionEngine.SuggestionMode) {
        Log.d(TAG, "Mode de suggestion changé: $newMode")
        // Ici on pourrait mettre à jour l'interface si nécessaire
    }
    
    // ===== IMPLÉMENTATION AccentSelectionListener =====
    
    override fun onAccentSelected(accent: String) {
        Log.d(TAG, "🎯 onAccentSelected appelé avec accent: '$accent'")
        
        // Supprimer le caractère de base et insérer l'accent
        val inputConnection = currentInputConnection
        if (inputConnection != null) {
            inputConnection.deleteSurroundingText(1, 0)
            inputConnection.commitText(accent, 1)
            
            // ✅ CORRECTION: Mettre à jour le mot courant SANS déclencher onWordChanged()
            // pour éviter la cascade d'événements qui provoque 60+ updateKeyboardDisplay()
            val currentWord = inputProcessor.getCurrentWord()
            if (currentWord.isNotEmpty()) {
                val updatedWord = currentWord.dropLast(1) + accent
                // Mise à jour directe du mot sans déclencher les callbacks
                inputProcessor.updateCurrentWordSilently(updatedWord)
                Log.d(TAG, "✅ Mot mis à jour silencieusement: '$currentWord' → '$updatedWord'")
            }
        }
        
        Log.d(TAG, "✅ onAccentSelected terminé sans cascade d'événements")
    }
    
    override fun onLongPressStarted(baseKey: String) {
        Log.d(TAG, "Appui long démarré pour: $baseKey")
    }
    
    override fun onLongPressCancelled() {
        Log.d(TAG, "Appui long annulé")
    }
    
    // ===== IMPLÉMENTATION InputProcessorListener =====
    
    override fun onWordChanged(word: String) {
        Log.d(TAG, "onWordChanged appelé avec: '$word'")
        if (word.isNotEmpty() && isInitialized) {
            Log.d(TAG, "Génération suggestions dictionnaire pour: '$word'")
            suggestionEngine.setSuggestionMode(SuggestionEngine.SuggestionMode.DICTIONARY)
            suggestionEngine.generateDictionarySuggestions(word)
        } else {
            Log.d(TAG, "Affichage de suggestions vides (mot vide ou non initialisé)")
            displaySuggestions(emptyList())
        }
    }
    
    override fun onWordCompleted(word: String) {
        Log.d(TAG, "Mot complété: '$word' - Ajout à l'historique")
        suggestionEngine.addWordToHistory(word)
        
        // Générer les suggestions contextuelles pour le prochain mot
        CoroutineScope(Dispatchers.Main).launch {
            delay(200) // Pause optimale pour une transition fluide
            Log.d(TAG, "Génération suggestions contextuelles après '$word'")
            suggestionEngine.setSuggestionMode(SuggestionEngine.SuggestionMode.CONTEXTUAL)
            suggestionEngine.generateContextualSuggestions()
        }
    }
    
    override fun onModeChanged(isNumeric: Boolean, isCapital: Boolean, isCapsLock: Boolean) {
        Log.e("SHIFT_REAL_DEBUG", "🚨 onModeChanged CALLED! isCapital=$isCapital, isCapsLock=$isCapsLock")
        
        // ✅ CORRECTION: Mettre à jour les états AVANT l'affichage
        keyboardLayoutManager.updateKeyboardStates(isNumeric, isCapital, isCapsLock)
        
        // Mettre à jour l'affichage du clavier
        keyboardLayoutManager.updateKeyboardDisplay()
        
        // Si on change vers le mode numérique, recréer le layout
        if (keyboardLayoutManager.switchKeyboardMode() != isNumeric) {
            refreshKeyboardLayout()
        }
    }
    
    override fun onSpecialKeyPressed(key: String) {
        Log.d(TAG, "Touche spéciale: $key")
        // Traitement supplémentaire si nécessaire
    }
    
    /**
     * Affiche les suggestions dans la barre de suggestions
     */
    private fun displaySuggestions(suggestions: List<String>) {
        Log.d(TAG, "displaySuggestions appelé avec ${suggestions.size} suggestions: ${suggestions.joinToString(", ")}")
        suggestionsView?.let { container ->
            Log.d(TAG, "Container de suggestions trouvé, vidage des vues existantes")
            container.removeAllViews()
            
            suggestions.take(MAX_SUGGESTIONS).forEach { suggestion ->
                val suggestionButton = Button(this).apply {
                    text = suggestion
                    textSize = 14f
                    setTextColor(Color.parseColor("#333333"))
                    setBackgroundColor(Color.parseColor("#E3F2FD"))
                    setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
                    
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    ).apply {
                        setMargins(dpToPx(4), 0, dpToPx(4), 0)
                    }
                    
                    setOnClickListener {
                        inputProcessor.processSuggestionSelection(suggestion)
                        
                        // Déclencher les suggestions contextuelles après la sélection
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(300) // Petite pause pour que l'espace soit traité
                            suggestionEngine.setSuggestionMode(SuggestionEngine.SuggestionMode.CONTEXTUAL)
                            suggestionEngine.generateContextualSuggestions()
                        }
                    }
                }
                
                container.addView(suggestionButton)
            }
        }
    }
    
    /**
     * Actualise le layout du clavier
     */
    private fun refreshKeyboardLayout() {
        mainKeyboardView?.let { oldView ->
            val parent = oldView.parent as? ViewGroup
            parent?.removeView(oldView)
            
            val newLayout = keyboardLayoutManager.createKeyboardLayout()
            parent?.addView(newLayout)
            mainKeyboardView = newLayout
        }
    }
    
    // ===== MÉTHODES DE CYCLE DE VIE =====
    
    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        Log.d(TAG, "onStartInput - restarting: $restarting")
        
        inputProcessor.resetState()
        suggestionEngine.clearHistory()
        displaySuggestions(emptyList())
    }
    
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Log.d(TAG, "onStartInputView - restarting: $restarting")
        
        if (!restarting) {
            keyboardLayoutManager.updateKeyboardDisplay()
        }
    }
    
    override fun onFinishInput() {
        super.onFinishInput()
        Log.d(TAG, "onFinishInput")
        
        accentHandler.dismissAccentPopup()
        inputProcessor.resetState()
    }
    
    override fun onDestroy() {
        Log.d(TAG, "=== DESTRUCTION DU SERVICE ===")
        
        try {
            // Nettoyage des composants dans l'ordre inverse de création
            accentHandler.cleanup()
            // inputProcessor.setInputProcessorListener(null) // À commenter pour éviter l'erreur
            suggestionEngine.cleanup()
            keyboardLayoutManager.cleanup()
            
            // Nettoyage des vues
            suggestionsView?.removeAllViews()
            suggestionsView = null
            mainKeyboardView = null
            
            Log.d(TAG, "Nettoyage terminé avec succès")
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du nettoyage: ${e.message}", e)
        } finally {
            super.onDestroy()
        }
    }
    
    // ===== MÉTHODES D'ÉVALUATION =====
    
    override fun onEvaluateFullscreenMode(): Boolean {
        return false // Toujours en mode compact pour une meilleure UX
    }
    
    override fun onEvaluateInputViewShown(): Boolean {
        return true
    }
    
    override fun isExtractViewShown(): Boolean {
        return false
    }
    
    // ===== MÉTHODES UTILITAIRES =====
    
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
