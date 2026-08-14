package com.example.kreyolkeyboard

/**
 * À la mémoire de mon père, Saint-Ange Corneille Famibelle
 */

import android.inputmethodservice.InputMethodService
import android.content.Context
import android.content.Intent
import android.app.ActivityManager
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.HorizontalScrollView
import android.widget.Button
import android.widget.Toast
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.KeyEvent
import kotlinx.coroutines.*
import android.os.Handler
import android.os.Looper
import java.util.Timer
import java.util.TimerTask
import com.example.kreyolkeyboard.BilingualSuggestion
import com.example.kreyolkeyboard.SuggestionLanguage
import com.example.kreyolkeyboard.gamification.CreoleDictionaryWithUsage
import com.example.kreyolkeyboard.gamification.LuxLevels
import com.example.kreyolkeyboard.gamification.LevelUpNotifier
import com.example.kreyolkeyboard.gamification.WordCommitListener

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
        private const val TAG = "LuxIME-Potomitan™"
        private const val MAX_SUGGESTIONS = 5  // 3 Kreyòl + 2 Français (mode bilingue)
        private const val ONBOARDING_PREFS = "lux_onboarding_prefs"
        private const val PREF_FIRST_REAL_USE_TIP_SHOWN = "first_real_use_tip_shown"
        private const val PREF_SHARE_CHIP_SHOWN = "share_invite_chip_shown"
        // Mot-dièse séparé du lien par une espace : ce message s'insère au fil
        // du texte de l'utilisateur, il ne peut pas se permettre de saut de ligne.
        private const val SHARE_INVITE_MESSAGE = "Geschriwwe mam Lëtzebuergesch Clavier, " +
            "https://play.google.com/store/apps/details?id=com.potomitan.luxkeyboard&pcampaignid=web_share" +
            " ${SettingsActivity.SHARE_HASHTAG}"

        // Passage de niveau. Clé distincte de « last_celebrated_level_index »,
        // qu'utilise SettingsActivity pour sa carte partageable : les deux
        // suivis sont volontairement indépendants, pour que la notification ne
        // consomme pas le franchissement et que l'utilisateur retrouve quand
        // même sa carte en ouvrant l'application.
        private const val GAMIFICATION_PREFS = "lux_gamification_prefs"
        private const val PREF_LAST_NOTIFIED_LEVEL = "last_notified_level_index"

        /**
         * Saisie dont le contenu ne doit jamais être conservé, statistiques de
         * vocabulaire comprises.
         * `internal` (et non private) pour être testable en JVM sans EditorInfo.
         *
         * Couvre les mots de passe sous toutes leurs déclarations (texte masqué,
         * texte visible, formulaire web, code numérique) ainsi que les champs que
         * l'application déclare non mémorisables via IME_FLAG_NO_PERSONALIZED_LEARNING.
         * Le mode « mot de passe visible » compte autant que les autres : c'est
         * bien un mot de passe, seul son affichage diffère.
         */
        internal fun isSensitiveInput(inputType: Int, imeOptions: Int): Boolean {
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            val inputClass = inputType and InputType.TYPE_MASK_CLASS

            val isPasswordText = inputClass == InputType.TYPE_CLASS_TEXT && variation in setOf(
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            )
            val isPasswordNumber = inputClass == InputType.TYPE_CLASS_NUMBER &&
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            val noLearning = (imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0

            return isPasswordText || isPasswordNumber || noLearning
        }

        // 🔧 FIX SAMSUNG A21S: Détection appareils low-end
        private fun isLowEndDevice(context: Context): Boolean {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            return activityManager.isLowRamDevice || 
                   activityManager.memoryClass <= 256 // 256MB ou moins = low-end
        }
    }
    
    // Composants modulaires
    private lateinit var keyboardLayoutManager: KeyboardLayoutManager
    private lateinit var suggestionEngine: SuggestionEngine
    private lateinit var accentHandler: AccentHandler
    private lateinit var inputProcessor: InputProcessor
    
    // 🎮 Gamification: Tracking d'utilisation du vocabulaire
    private lateinit var dictionaryWithUsage: CreoleDictionaryWithUsage
    
    // Vues principales
    private var suggestionsView: LinearLayout? = null
    private var kreyolRow: LinearLayout? = null
    private var frenchRow: LinearLayout? = null
    private var frenchRowScroll: HorizontalScrollView? = null
    private var mainKeyboardView: View? = null
    
    // État du service
    private var isInitialized = false
    
    // 🔧 FIX SAMSUNG A21S: Gestion coroutines liées au cycle de vie
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // 🔍 MONITORING MÉMOIRE A21S
    private var memoryMonitoringJob: Job? = null
    private var lastMemoryWarning = 0L
    
    // Gestion suppression par mots (appui long Delete)
    private var deleteTimer: Timer? = null
    private var deleteHandler = Handler(Looper.getMainLooper())
    private var isDeleteLongPressActive = false
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "=== KREYOL IME SERVICE REFACTORISÉ onCreate() ===")
        
        // 🔍 DIAGNOSTIC SAMSUNG A21S: Informations système détaillées
        logSystemInfo()
        
        try {
            initializeComponents()
            Log.d(TAG, "✅ Service initialisé avec succès")
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERREUR CRITIQUE lors de l'initialisation: ${e.message}", e)
            // Log stack trace complète pour A21s debugging
            Log.e(TAG, "Stack trace complète:", e)
        }
    }
    
    /**
     * 🔍 DIAGNOSTIC A21S: Log des informations système pour debugging
     */
    private fun logSystemInfo() {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            
            Log.d(TAG, "📊 DIAGNOSTIC SYSTÈME A21S:")
            Log.d(TAG, "  • RAM totale: ${memInfo.totalMem / (1024 * 1024)}MB")
            Log.d(TAG, "  • RAM disponible: ${memInfo.availMem / (1024 * 1024)}MB")
            Log.d(TAG, "  • Seuil low memory: ${memInfo.threshold / (1024 * 1024)}MB")
            Log.d(TAG, "  • Low RAM device: ${activityManager.isLowRamDevice}")
            Log.d(TAG, "  • Memory class: ${activityManager.memoryClass}MB")
            Log.d(TAG, "  • Large memory class: ${activityManager.largeMemoryClass}MB")
            
            // Informations Android
            Log.d(TAG, "  • Android SDK: ${android.os.Build.VERSION.SDK_INT}")
            Log.d(TAG, "  • Model: ${android.os.Build.MODEL}")
            Log.d(TAG, "  • Manufacturer: ${android.os.Build.MANUFACTURER}")
            Log.d(TAG, "  • Device: ${android.os.Build.DEVICE}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur diagnostic système: ${e.message}", e)
        }
    }
    
    /**
     * 🔍 MONITORING MÉMOIRE A21S: Surveillance continue pour détecter les fuites
     */
    private fun startMemoryMonitoring() {
        memoryMonitoringJob = serviceScope.launch {
            while (isActive) {
                try {
                    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    val memInfo = ActivityManager.MemoryInfo()
                    activityManager.getMemoryInfo(memInfo)
                    
                    val availableMB = memInfo.availMem / (1024 * 1024)
                    val lowMemThresholdMB = memInfo.threshold / (1024 * 1024)
                    
                    // Alerter si mémoire critique (seulement toutes les 30 secondes)
                    val now = System.currentTimeMillis()
                    if (availableMB < lowMemThresholdMB && (now - lastMemoryWarning) > 30000) {
                        Log.w(TAG, "⚠️ A21S MÉMOIRE CRITIQUE: ${availableMB}MB disponible (seuil: ${lowMemThresholdMB}MB)")
                        lastMemoryWarning = now
                        
                        // Suggestion de nettoyage sur A21s
                        System.gc()
                        Log.d(TAG, "🔧 Garbage collection forcé pour A21s")
                    }
                    
                    delay(10000) // Vérifier toutes les 10 secondes
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur monitoring mémoire A21s: ${e.message}", e)
                    delay(30000) // En cas d'erreur, attendre plus longtemps
                }
            }
        }
    }
    
    /**
     * Initialise tous les composants modulaires
     * 🔧 FIX SAMSUNG A21S: Initialisation adaptative selon les capacités de l'appareil
     */
    private fun initializeComponents() {
        val isLowEnd = isLowEndDevice(this)
        Log.d(TAG, if (isLowEnd) "🔧 Appareil détecté: Low-end (A21s compatible)" else "🚀 Appareil détecté: Standard")
        
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
        // Permet à KeyboardLayoutManager d'afficher un aperçu des options
        // d'appui long dans les coins des touches (v8.3.0)
        keyboardLayoutManager.accentHandler = accentHandler
        
        inputProcessor = InputProcessor(this).apply {
            setInputProcessorListener(this@KreyolInputMethodServiceRefactored)
        }
        
        // 🎮 Gamification: Initialiser le tracking d'utilisation du vocabulaire
        dictionaryWithUsage = CreoleDictionaryWithUsage(this)

        // Les compteurs d'utilisation ne servaient qu'aux écrans de statistiques :
        // le moteur classait les suggestions sur la seule fréquence du corpus, donc
        // sur ce que le kréyòl écrit emploie en général plutôt que sur ce que cet
        // utilisateur-ci écrit. Les rebrancher sur le scoring fait remonter son
        // vocabulaire propre, sans rien envoyer hors de l'appareil.
        suggestionEngine.setUsageCountProvider { word ->
            dictionaryWithUsage.getWordUsageCount(word)
        }

        // Connecter le listener de tracking au InputProcessor
        inputProcessor.setWordCommitListener(object : WordCommitListener {
            override fun onWordCommitted(word: String) {
                Log.d(TAG, "🔍 onWordCommitted appelé avec: '$word'")

                if (isSensitiveField()) {
                    // Champ de mot de passe ou saisie explicitement non
                    // mémorisable : aucun comptage. Sortie avant toute écriture,
                    // y compris l'horodatage du tunnel.
                    Log.d(TAG, "🔒 Champ sensible: mot ignoré")
                    return
                }

                // Tunnel d'activation local : horodater le tout premier mot
                // commité (diagnostic affiché dans À Propos, rien ne sort du
                // téléphone)
                val funnelPrefs = getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE)
                if (!funnelPrefs.contains("funnel_first_word")) {
                    funnelPrefs.edit().putLong("funnel_first_word", System.currentTimeMillis()).apply()
                }

                // Tracker le mot dans le dictionnaire (seulement si présent)
                val result = dictionaryWithUsage.trackWordUsage(word)
                Log.d(TAG, "🎯 Résultat tracking '$word': ${result.tracked}")

                if (result.tracked) {
                    Log.d(TAG, "🎮 Gamification: Mot tracké '$word'")
                }

                // Un passage de niveau ne peut survenir que si le mot vient
                // d'être découvert : inutile de refaire le calcul à chaque mot
                // validé, ce qui se verrait à la frappe.
                if (result.newlyDiscovered) {
                    maybeNotifyLevelUp()
                }
            }
        })
        
        Log.d(TAG, "✅ Gamification initialisée avec tracking du vocabulaire")
        
        // 🔧 FIX SAMSUNG A21S: Initialisation adaptative selon les capacités
        serviceScope.launch {
            try {
                if (isLowEnd) {
                    // Sur A21s: Initialisation graduelle pour éviter les pics de mémoire
                    Log.d(TAG, "🔧 Initialisation optimisée A21s - Chargement graduel")
                    delay(500) // Laisser le service se stabiliser
                    suggestionEngine.initialize()
                    delay(200) // Pause entre les étapes
                } else {
                    // Appareils standard: Initialisation normale
                    suggestionEngine.initialize()
                }
                isInitialized = true
                Log.d(TAG, "✅ Moteur de suggestions initialisé (mode: ${if (isLowEnd) "A21s optimisé" else "standard"})")

                // 🟢🔵 Support bilingue Kreyòl + Français (Kreyòl-first, Français dès 3 lettres)
                suggestionEngine.enableBilingualSupport()
                Log.d(TAG, "🎯 Mode suggestions bilingue avec AccentTolerantMatching activé")
                
                // Démarrer monitoring mémoire sur A21s
                if (isLowEnd) {
                    startMemoryMonitoring()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur initialisation suggestions: ${e.message}", e)
                // Sur A21s, réessayer avec un mode plus conservateur
                if (isLowEnd && !isInitialized) {
                    Log.d(TAG, "🔧 Tentative de récupération pour A21s...")
                    delay(1000)
                    try {
                        suggestionEngine.initialize()
                        isInitialized = true
                        suggestionEngine.enableBilingualSupport()
                        Log.d(TAG, "✅ Récupération A21s réussie + support bilingue activé")
                    } catch (e2: Exception) {
                        Log.e(TAG, "❌ Échec récupération A21s: ${e2.message}", e2)
                    }
                }
            }
        }
    }
    
    override fun onCreateInputView(): View? {
        Log.d(TAG, "onCreateInputView() appelée")
        
        // 🅰️ FORCER LE MODE ALPHABÉTIQUE AU DÉMARRAGE
        keyboardLayoutManager.forceAlphabeticMode()
        Log.d(TAG, "✅ Mode alphabétique forcé lors de la création du clavier")
        
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }
        
        // Créer la zone de suggestions
        createSuggestionsArea(mainLayout)
        
        // 📱 PADDING ADAPTATIF SELON MODE DE NAVIGATION
        // Créer un conteneur avec padding pour éviter que la navigation bar masque le clavier
        val keyboardContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val adaptivePadding = getAdaptiveNavigationPadding()
            setPadding(0, 0, 0, adaptivePadding)
            Log.d(TAG, "✅ Padding adaptatif appliqué: ${adaptivePadding}px")
        }
        
        // Créer le clavier principal
        val keyboardLayout = keyboardLayoutManager.createKeyboardLayout()
        keyboardContainer.addView(keyboardLayout)
        mainLayout.addView(keyboardContainer)
        mainKeyboardView = keyboardContainer

        return mainLayout
    }
    
    /**
     * Crée la zone des suggestions : deux rangées empilées (Kreyòl puis Français) pour
     * que le français reste toujours entièrement visible, sans scroll ni troncature,
     * même quand les suggestions kreyòl sont longues ("Bonmaten-la"). La rangée
     * française reste toujours réservée en hauteur (INVISIBLE, jamais GONE) même
     * quand elle est vide : un GONE/VISIBLE dynamique décale toutes les rangées du
     * clavier en dessous pendant la frappe (ex. au franchissement du seuil de 3
     * lettres qui active le fallback français), ce qui fait atterrir un tap en cours
     * sur la touche de la rangée voisine — bug constaté et corrigé le 23/07/2026.
     */
    private fun createSuggestionsArea(parentLayout: LinearLayout) {
        val suggestionsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.parseColor("#FFFFFF"))
        }

        val kreyolScroll = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(44)
            )
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(2))
        }
        kreyolRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        kreyolScroll.addView(kreyolRow)

        val frScroll = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(44)
            )
            setPadding(dpToPx(8), dpToPx(2), dpToPx(8), dpToPx(4))
            visibility = View.INVISIBLE
        }
        frenchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        frScroll.addView(frenchRow)
        frenchRowScroll = frScroll

        suggestionsContainer.addView(kreyolScroll)
        suggestionsContainer.addView(frScroll)
        // Alias historique : les modes non-bilingues (prédictions contextuelles) affichent
        // dans la rangée du haut, la rangée française reste masquée dans ce cas.
        suggestionsView = kreyolRow

        parentLayout.addView(suggestionsContainer)
    }
    
    // ===== IMPLÉMENTATION KeyboardInteractionListener =====
    
    override fun onKeyPress(key: String) {
        Log.d(TAG, "=== TOUCHE PRESSÉE: '$key' ===")
        
        // 🌐 BOUTON GLOBE - TEMPORAIREMENT DÉSACTIVÉ (bug système Android)
        // TODO: Réactiver quand le problème système sera résolu
        /*
        if (key == "🌐") {
            Log.d(TAG, "🌐 BOUTON GLOBE DÉTECTÉ - INTERCEPTION DIRECTE!")
            try {
                val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.showInputMethodPicker()
                Log.d(TAG, "✅ Globe intercepté avec succès: InputMethod Picker affiché")
                return // Arrêter ici pour éviter le traitement normal qui cause le crash
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur lors de l'interception du bouton Globe: ${e.message}", e)
                return // Même en cas d'erreur, ne pas continuer pour éviter le crash
            }
        }
        */
        
        if (accentHandler.isLongPressActive()) {
            Log.d(TAG, "Appui long actif - ignorer l'appui court")
            return
        }
        
        Log.d(TAG, "InputProcessor initialisé: ${::inputProcessor.isInitialized}")
        Log.d(TAG, "Traitement de la touche par InputProcessor")
        inputProcessor.processKeyPress(key)
        Log.d(TAG, "=== FIN TRAITEMENT TOUCHE ===")
    }
    
    override fun onLongPress(key: String, button: View) {
        Log.d(TAG, "🔗 Appui long sur: $key")
        
        when (key) {
            "⌫" -> {
                // Suppression par mots avec appui long sur Delete
                Log.d(TAG, "🗑️ Démarrage suppression par mots (Delete)")
                startWordDeletion()
            }
            " " -> {
                // 🌐 Appui long sur barre d'espace : afficher le sélecteur de claviers
                // (plutôt que de basculer silencieusement vers le "prochain" clavier,
                // ce qui surprenait l'utilisateur en changeant d'IME sans prévenir)
                Log.d(TAG, "🌐 Appui long sur barre d'espace - Affichage du sélecteur de claviers")

                val shouldSwitch = inputProcessor.processSpaceLongPress()
                if (shouldSwitch) {
                    showKeyboardPicker()
                }
            }
            else -> {
                // Gestion des accents pour les autres touches
                if (accentHandler.hasAccents(key)) {
                    accentHandler.startLongPressTimer(key, button)
                }
            }
        }
    }
    
    override fun onKeyRelease() {
        // Arrêter la suppression par mots si active
        stopWordDeletion()
        
        // Arrêter les accents
        accentHandler.cancelLongPress()
    }
    
    // ===== IMPLÉMENTATION SuggestionListener =====
    
    override fun onSuggestionsReady(suggestions: List<String>) {
        // 📝 RETOUR AUX SUGGESTIONS SIMPLES - Couleurs d'origine
        Log.d(TAG, "📝 Affichage suggestions simples: ${suggestions.joinToString(", ")}")
        displaySuggestions(suggestions)
    }
    
    override fun onBilingualSuggestionsReady(suggestions: List<BilingualSuggestion>) {
        Log.d(TAG, "🎯 Affichage suggestions bilingues: ${suggestions.joinToString(", ") { "${it.word}(${it.language})" }}")
        displayBilingualSuggestions(suggestions)
    }
    
    override fun onDictionaryLoaded(wordCount: Int) {
        Log.d(TAG, "🟢 Dictionnaire kreyòl chargé: $wordCount mots")
    }
    
    override fun onFrenchDictionaryLoaded(wordCount: Int) {
        Log.d(TAG, "🔵 Dictionnaire français chargé: $wordCount mots")
    }
    
    override fun onNgramModelLoaded() {
        Log.d(TAG, "🟢 Modèle N-gram kreyòl chargé")
    }
    
    override fun onModeChanged(newMode: SuggestionEngine.SuggestionMode) {
        Log.d(TAG, "Mode de suggestion changé: $newMode")
        // Ici on pourrait mettre à jour l'interface si nécessaire
    }
    
    // ===== IMPLÉMENTATION AccentSelectionListener =====
    
    override fun onAccentSelected(accent: String, baseCharacter: String) {
        Log.d(TAG, "🎯 onAccentSelected appelé - accent: '$accent', base: '$baseCharacter'")
        
        val inputConnection = currentInputConnection
        if (inputConnection != null) {
            val textBefore = inputConnection.getTextBeforeCursor(10, 0)?.toString() ?: ""
            Log.d(TAG, "📝 Texte avant accent: '$textBefore'")
            
            // ✅ BUG FIX CORRECT: Ajouter l'accent directement 
            // Le caractère de base n'a pas été ajouté à cause de l'appui long
            inputConnection.commitText(accent, 1)
            Log.d(TAG, "✅ Accent '$accent' ajouté (remplace '$baseCharacter' conceptuel)")

            // Mettre à jour le mot courant en ajoutant l'accent — uniquement
            // pour un vrai caractère de mot (lettre accentuée, digraphe...).
            // Depuis l'ajout du panneau emoji (v10.1.0), cette même popup sert
            // aussi à choisir un ton de peau ; un emoji n'est pas une lettre et
            // ne doit pas polluer le suivi du mot en cours utilisé par les
            // suggestions (sinon prochaine recherche dictionnaire faite avec
            // un préfixe du genre "🥭ka").
            if (accent.all { it.isLetter() }) {
                val currentWord = inputProcessor.getCurrentWord()
                val updatedWord = currentWord + accent
                inputProcessor.updateCurrentWordSilently(updatedWord)
                Log.d(TAG, "✅ Mot mis à jour: '$currentWord' + '$accent' → '$updatedWord'")
            } else {
                inputProcessor.finalizeCurrentWordFromEmoji()
            }
            
            // 🔍 DIAGNOSTIC: Vérifier l'état final
            val textAfter = inputConnection.getTextBeforeCursor(10, 0)?.toString() ?: ""
            Log.d(TAG, "📝 Texte après accent: '$textAfter'")
        }
        
        Log.d(TAG, "✅ onAccentSelected terminé - BUG FIX v2 appliqué")
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
            Log.d(TAG, "� Génération suggestions SIMPLES pour: '$word'")
            suggestionEngine.setSuggestionMode(SuggestionEngine.SuggestionMode.DICTIONARY)
            suggestionEngine.generateDictionarySuggestions(word)  // Retour méthode simple
        } else {
            Log.d(TAG, "Affichage de suggestions vides (mot vide ou non initialisé)")
            displaySuggestions(emptyList())
        }
    }
    
    override fun onWordCompleted(word: String) {
        Log.d(TAG, "Mot complété: '$word' - Ajout à l'historique")
        suggestionEngine.addWordToHistory(word)
        
        // 🔧 FIX SAMSUNG A21S: Utiliser serviceScope et réduire le délai
        serviceScope.launch {
            delay(100) // Délai réduit pour A21s (performance limitée)
            Log.d(TAG, "Génération suggestions contextuelles après '$word'")
            suggestionEngine.setSuggestionMode(SuggestionEngine.SuggestionMode.CONTEXTUAL)
            suggestionEngine.generateContextualSuggestions()
        }
    }
    
    override fun onModeChanged(isNumeric: Boolean, isEmoji: Boolean, isCapital: Boolean, isCapsLock: Boolean) {
        // ✅ Vérifier si le mode numérique ou emoji a changé (avant de mettre à jour les états)
        val currentNumericMode = keyboardLayoutManager.isNumericMode()
        val currentEmojiMode = keyboardLayoutManager.isEmojiMode()
        val needsLayoutRefresh = currentNumericMode != isNumeric || currentEmojiMode != isEmoji

        // ✅ CORRECTION: Mettre à jour les états AVANT l'affichage
        keyboardLayoutManager.updateKeyboardStates(isNumeric, isEmoji, isCapital, isCapsLock)
        
        // Mettre à jour l'état du mode majuscule dans AccentHandler
        accentHandler.isCapitalMode = isCapital || isCapsLock
        
        // Mettre à jour l'affichage du clavier
        keyboardLayoutManager.updateKeyboardDisplay()
        
        // Si on change vers le mode numérique, recréer le layout
        if (needsLayoutRefresh) {
            refreshKeyboardLayout()
        }
    }
    
    override fun onSpecialKeyPressed(key: String) {
        Log.d(TAG, "Touche spéciale: $key")
        // Traitement supplémentaire si nécessaire
    }
    
    /**
     * Affiche les suggestions dans la barre de suggestions (mode simple : dictionnaire
     * hors bilingue, ou prédictions contextuelles n-gram — toutes deux Kreyòl uniquement).
     * Réutilise le même style de puce pleine arrondie que le mode bilingue, pour éviter
     * qu'un second look (l'ancien rectangle bleu pastel) ne cohabite avec le premier.
     */
    private fun displaySuggestions(suggestions: List<String>) {
        Log.d(TAG, "displaySuggestions appelé avec ${suggestions.size} suggestions: ${suggestions.joinToString(", ")}")
        // Mode simple : pas de français, la 2e rangée reste masquée (mais réservée en hauteur)
        frenchRow?.removeAllViews()
        frenchRowScroll?.visibility = View.INVISIBLE
        suggestionsView?.let { container ->
            Log.d(TAG, "Container de suggestions trouvé, vidage des vues existantes")
            container.removeAllViews()

            suggestions.take(MAX_SUGGESTIONS).forEach { suggestion ->
                addSuggestionChip(container, BilingualSuggestion(suggestion, 0f, SuggestionLanguage.KREYOL))
            }
        }
    }
    
    /**
     * 🎯 Affiche les suggestions bilingues sur deux rangées empilées (Kreyòl en haut,
     * Français en bas) : le français reste toujours entièrement visible, sans scroll
     * ni troncature, même quand une suggestion kreyòl est longue ("Bonmaten-la"). La
     * rangée française se masque (hauteur nulle) quand elle est vide.
     */
    private fun displayBilingualSuggestions(suggestions: List<BilingualSuggestion>) {
        Log.d(TAG, "displayBilingualSuggestions appelé avec ${suggestions.size} suggestions bilingues")
        val kreyolContainer = kreyolRow ?: return
        val frenchContainer = frenchRow ?: return

        kreyolContainer.removeAllViews()
        frenchContainer.removeAllViews()

        val kreyolSuggestions = suggestions.filter { it.language == SuggestionLanguage.KREYOL }
        val frenchSuggestions = suggestions.filter { it.language == SuggestionLanguage.FRENCH }

        if (kreyolSuggestions.isNotEmpty()) {
            addLanguageLabel(kreyolContainer, kreyolSuggestions.first().getShortLabel())
            kreyolSuggestions.forEach { addSuggestionChip(kreyolContainer, it) }
        }

        if (frenchSuggestions.isNotEmpty()) {
            addLanguageLabel(frenchContainer, frenchSuggestions.first().getShortLabel())
            frenchSuggestions.forEach { addSuggestionChip(frenchContainer, it) }
        }
        // INVISIBLE (jamais GONE) : garder la hauteur de la rangée réservée en permanence
        // évite que l'apparition/disparition des suggestions françaises ne décale les
        // rangées du clavier en dessous pendant la frappe (cf. bug du 23/07/2026).
        frenchRowScroll?.visibility = if (frenchSuggestions.isNotEmpty()) View.VISIBLE else View.INVISIBLE

        Log.d(TAG, "✅ ${suggestions.size} suggestions bilingues affichées (${kreyolSuggestions.size} Kreyòl / ${frenchSuggestions.size} Français)")
    }

    /**
     * Ajoute le micro-label (KR/FR) en tête d'une rangée de suggestions
     */
    private fun addLanguageLabel(container: LinearLayout, label: String) {
        val groupLabel = TextView(this).apply {
            text = label
            textSize = 10f
            setTextColor(KeyboardColors.TEXT_SECONDARY)
            setPadding(dpToPx(4), 0, dpToPx(2), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
        }
        container.addView(groupLabel)
    }

    /**
     * Ajoute une puce de suggestion arrondie (fond plein, texte blanc) dans une rangée
     */
    private fun addSuggestionChip(container: LinearLayout, bilingualSuggestion: BilingualSuggestion) {
        val suggestionButton = Button(this).apply {
            text = bilingualSuggestion.word
            textSize = 14f
            setTextColor(KeyboardColors.CHIP_TEXT)

            val bgColor = bilingualSuggestion.getColor()
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(16).toFloat()
                setColor(bgColor)
            }

            val colorHex = String.format("#%06X", 0xFFFFFF and bgColor)
            Log.d(TAG, "🎨 Bouton '${bilingualSuggestion.word}': ${bilingualSuggestion.getLanguageName()} → fond $colorHex")

            setPadding(dpToPx(14), dpToPx(6), dpToPx(14), dpToPx(6))

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(dpToPx(3), 0, dpToPx(4), 0)
            }

            setOnClickListener {
                inputProcessor.processSuggestionSelection(bilingualSuggestion.word)

                // 🔧 FIX SAMSUNG A21S: Performance optimisée
                serviceScope.launch {
                    delay(150)
                    suggestionEngine.setSuggestionMode(SuggestionEngine.SuggestionMode.CONTEXTUAL)
                    suggestionEngine.generateContextualSuggestions()
                }
            }
        }

        container.addView(suggestionButton)
    }

    /**
     * Actualise le layout du clavier en préservant le conteneur avec padding
     */
    private fun refreshKeyboardLayout() {
        mainKeyboardView?.let { containerView ->
            // mainKeyboardView est le conteneur avec padding, pas le clavier directement
            if (containerView is LinearLayout && containerView.childCount > 0) {
                // Retirer l'ancien clavier du conteneur
                val oldKeyboard = containerView.getChildAt(0)
                containerView.removeView(oldKeyboard)
                
                // Créer et ajouter le nouveau clavier dans le même conteneur
                val newKeyboard = keyboardLayoutManager.createKeyboardLayout()
                containerView.addView(newKeyboard)
                
                Log.d(TAG, "🔄 Clavier actualisé (padding préservé: ${containerView.paddingBottom}px)")
            } else {
                Log.w(TAG, "⚠️ mainKeyboardView n'est pas un conteneur LinearLayout valide")
            }
        }
    }
    
    // ===== MÉTHODES DE CYCLE DE VIE =====
    
    /**
     * Champ dont le contenu ne doit jamais être conservé, statistiques de
     * vocabulaire comprises : mots de passe (visibles ou masqués, texte ou
     * numériques), et champs que l'application déclare non mémorisables via
     * IME_FLAG_NO_PERSONALIZED_LEARNING.
     */
    private fun isSensitiveField(): Boolean {
        val editorInfo = currentInputEditorInfo ?: return true
        return isSensitiveInput(editorInfo.inputType, editorInfo.imeOptions)
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        Log.d(TAG, "onStartInput - restarting: $restarting")
        
        inputProcessor.resetState()
        suggestionEngine.clearHistory()
        displaySuggestions(emptyList())
    }
    
    /**
     * Le framework signale ici tout déplacement du curseur : tap dans le texte,
     * sélection, effacement remontant dans un mot déjà validé, ou modification
     * faite par l'application elle-même. C'est le seul point où l'on apprend que
     * le texte a bougé sans passer par nos touches.
     *
     * Absent de ce service jusqu'ici (il n'existait que dans le service legacy
     * KreyolInputMethodService), ce qui laissait le mot suivi par InputProcessor
     * dériver du texte réel : revenir éditer un mot existant ne produisait alors
     * plus aucune suggestion, et taper après un déplacement de curseur en
     * produisait pour le mot précédent.
     */
    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )

        if (!::inputProcessor.isInitialized) return
        inputProcessor.syncWordWithCursor(newSelStart, newSelEnd)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Log.d(TAG, "onStartInputView - restarting: $restarting")
        
        // 🅰️ S'ASSURER QUE LE MODE ALPHABÉTIQUE EST ACTIF À CHAQUE FOIS
        if (!restarting) {
            keyboardLayoutManager.forceAlphabeticMode()
            keyboardLayoutManager.updateKeyboardDisplay()
            Log.d(TAG, "✅ Mode alphabétique garanti lors du démarrage de la saisie")
        }

        maybeShowFirstRealUseTip(info)
    }

    /**
     * Publie la pastille d'icône si l'utilisateur vient de franchir un palier.
     *
     * Rien n'est affiché par-dessus l'application en cours : la notification
     * est silencieuse et sans bandeau (voir [LevelUpNotifier]), l'utilisateur
     * découvre la pastille sur son écran d'accueil quand il y revient.
     *
     * Au tout premier appel, le niveau courant est mémorisé sans rien publier :
     * un utilisateur qui installe cette version avec déjà des centaines de mots
     * à son actif ne doit pas être notifié rétroactivement. Même règle que la
     * célébration existante dans SettingsActivity.
     */
    private fun maybeNotifyLevelUp() {
        try {
            val totalWords = dictionaryWithUsage.getTotalWords()
            if (totalWords <= 0) return

            val discovered = dictionaryWithUsage.getDiscoveredWordsCount()
            val currentIndex = LuxLevels.indexFor(discovered, totalWords)

            val prefs = getSharedPreferences(GAMIFICATION_PREFS, Context.MODE_PRIVATE)
            val lastNotified = prefs.getInt(PREF_LAST_NOTIFIED_LEVEL, -1)

            if (lastNotified == -1) {
                prefs.edit().putInt(PREF_LAST_NOTIFIED_LEVEL, currentIndex).apply()
                return
            }
            if (currentIndex <= lastNotified) return

            // La pastille de la barre d'onglets est posée quoi qu'il arrive :
            // elle ne dépend pas de la permission de notification, et reste donc
            // le signal de repli quand celle-ci a été refusée.
            prefs.edit()
                .putInt(PREF_LAST_NOTIFIED_LEVEL, currentIndex)
                .putBoolean(SettingsActivity.PREF_LEVEL_BADGE_PENDING, true)
                .apply()
            LevelUpNotifier.notifyLevelUp(this, LuxLevels.LEVELS[currentIndex].label)
        } catch (e: Exception) {
            // Un échec de notification ne doit jamais perturber la frappe
            Log.e(TAG, "Erreur lors de la notification de niveau", e)
        }
    }

    /**
     * Confirmation de succès + astuce accents, affichée une seule fois, la
     * première fois que le clavier est réellement utilisé en dehors de
     * l'écran de test intégré à l'app (Lëtzebuergesch Clavier elle-même) — un
     * utilisateur qui bascule directement vers Messages après avoir
     * sélectionné le clavier ne voyait jusqu'ici aucun signal de succès.
     */
    private fun maybeShowFirstRealUseTip(info: EditorInfo?) {
        val targetPackage = info?.packageName ?: return
        if (targetPackage == packageName) return // écran de test intégré à l'app, pas un vrai usage

        val prefs = getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE)

        if (!prefs.getBoolean(PREF_FIRST_REAL_USE_TIP_SHOWN, false)) {
            Toast.makeText(
                this,
                "Apiyé lontan asi on lèt pou wè aksan la (é, è, ò...)",
                Toast.LENGTH_LONG
            ).show()
            prefs.edit().putBoolean(PREF_FIRST_REAL_USE_TIP_SHOWN, true).apply()
        }

        // Puce de partage : état séparé de l'astuce accents ci-dessus, retentée
        // à chaque champ tant qu'elle n'a pas pu s'afficher sur un vrai champ
        // de composition (action clavier "Envoyer") — sinon un premier focus
        // sur un champ destinataire/recherche de contact (action "Suivant"/
        // "Rechercher", qui réinterprète le texte collé comme une requête de
        // contact et le tronque — repro sur l'écran "Nouvelle conversation" de
        // Messages, testé le 02/08/2026) consommerait l'occasion pour toujours.
        if (!prefs.getBoolean(PREF_SHARE_CHIP_SHOWN, false) &&
            info.imeOptions and EditorInfo.IME_MASK_ACTION == EditorInfo.IME_ACTION_SEND
        ) {
            showShareInviteChip()
            prefs.edit().putBoolean(PREF_SHARE_CHIP_SHOWN, true).apply()
        }
    }

    /**
     * Puce unique invitant à partager le clavier, affichée dans la barre de
     * suggestions au moment du tout premier usage réel (avant même que
     * l'utilisateur ait tapé une lettre de son propre message). Un tap
     * insère directement le message prêt-à-envoyer dans le champ en cours
     * (SMS, WhatsApp...) via commitText : contrairement à une suggestion
     * normale, ce n'est pas un mot à finaliser, donc on n'appelle pas
     * inputProcessor.processSuggestionSelection() ici. Dès que l'utilisateur
     * tape son propre texte, displaySuggestions()/displayBilingualSuggestions()
     * vide le conteneur et la puce disparaît naturellement.
     */
    private fun showShareInviteChip() {
        val container = kreyolRow ?: return
        lateinit var chip: Button
        chip = Button(this).apply {
            text = "📤 Envoyer un mot à un ami"
            textSize = 14f
            setTextColor(KeyboardColors.CHIP_TEXT)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(16).toFloat()
                setColor(Color.parseColor("#FF8A00"))
            }
            setPadding(dpToPx(14), dpToPx(6), dpToPx(14), dpToPx(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(dpToPx(3), 0, dpToPx(4), 0)
            }
            setOnClickListener {
                currentInputConnection?.commitText(SHARE_INVITE_MESSAGE, 1)
                container.removeView(chip)
            }
        }
        container.addView(chip, 0)
    }
    
    override fun onFinishInput() {
        // Sauter super.onFinishInput() cassait le cycle de vie interne du service :
        // après un changement d'app (et retour), le framework ne rappelait plus
        // jamais onStartInput()/onStartInputView(), laissant le clavier invisible
        // indéfiniment. onEvaluateInputViewShown() (plus bas) retourne déjà
        // toujours true, ce qui est la bonne façon de garder le clavier affiché.
        super.onFinishInput()
        Log.d(TAG, "onFinishInput")

        accentHandler.dismissAccentPopup()
        inputProcessor.resetState()
    }
    
    /**
     * MÉTHODE TEMPORAIREMENT DÉSACTIVÉE - Gère le changement vers le prochain clavier IME (bouton Globe 🌐)
     * TODO: Réactiver quand le problème système Android sera résolu
     */
    /*
    override fun switchInputMethod(imeSubtypeToken: String?) {
        Log.d(TAG, "🌐 switchInputMethod appelé avec token: $imeSubtypeToken")
        try {
            // Méthode 1: Utiliser la méthode standard du système
            val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val token = window.window?.attributes?.token
            
            if (token != null) {
                // Utiliser switchToNextInputMethod avec le bon token de fenêtre
                val switched = inputMethodManager.switchToNextInputMethod(token, false)
                Log.d(TAG, "✅ switchToNextInputMethod réussi: $switched")
                if (!switched) {
                    // Fallback: afficher le sélecteur
                    inputMethodManager.showInputMethodPicker()
                    Log.d(TAG, "✅ Fallback: InputMethod Picker affiché")
                }
            } else {
                // Si pas de token, utiliser le sélecteur directement
                inputMethodManager.showInputMethodPicker()
                Log.d(TAG, "✅ Token null: InputMethod Picker affiché")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur lors du changement de clavier: ${e.message}", e)
            // Dernier recours: super.switchInputMethod()
            try {
                super.switchInputMethod(imeSubtypeToken)
                Log.d(TAG, "✅ Super.switchInputMethod réussi")
            } catch (superException: Exception) {
                Log.e(TAG, "❌ Super.switchInputMethod également échoué: ${superException.message}", superException)
            }
        }
    }
    */
    
    /**
     * MÉTHODE TEMPORAIREMENT DÉSACTIVÉE - Interception directe des touches système, notamment le bouton Globe
     * TODO: Réactiver quand le problème système Android sera résolu
     */
    /*
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        Log.d(TAG, "🔧 onKeyDown appelé avec keyCode: $keyCode (Globe = ${KeyEvent.KEYCODE_LANGUAGE_SWITCH})")
        
        // Intercepter spécifiquement le bouton Globe
        if (keyCode == KeyEvent.KEYCODE_LANGUAGE_SWITCH) {
            Log.d(TAG, "🌐 INTERCEPTION DIRECTE du bouton Globe!")
            try {
                // Utiliser directement InputMethodManager sans passer par switchInputMethod()
                val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.showInputMethodPicker()
                Log.d(TAG, "✅ Globe intercepté: InputMethod Picker affiché avec succès")
                return true // Consommer l'événement pour éviter le traitement par défaut
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur lors de l'interception Globe: ${e.message}", e)
                return false // Laisser le système traiter en cas d'erreur
            }
        }
        
        // Pour toutes les autres touches, utiliser le comportement par défaut
        return super.onKeyDown(keyCode, event)
    }
    */
    
    override fun onDestroy() {
        Log.d(TAG, "=== DESTRUCTION DU SERVICE ===")
        
        try {
            // 🎮 Gamification: Sauvegarder les changements non sauvegardés
            if (::dictionaryWithUsage.isInitialized) {
                dictionaryWithUsage.onDestroy()
                Log.d(TAG, "✅ Gamification: Sauvegarde finale effectuée")
            }
            
            // 🔧 FIX SAMSUNG A21S: Arrêter monitoring et annuler coroutines
            memoryMonitoringJob?.cancel()
            serviceScope.cancel()
            Log.d(TAG, "✅ Monitoring mémoire et coroutines annulés pour A21s")
            
            // Arrêter la suppression par mots si active
            stopWordDeletion()
            
            // Nettoyage des composants dans l'ordre inverse de création
            accentHandler.cleanup()
            // inputProcessor.setInputProcessorListener(null) // À commenter pour éviter l'erreur
            suggestionEngine.cleanup()
            keyboardLayoutManager.cleanup()
            
            // Nettoyage des vues
            suggestionsView?.removeAllViews()
            suggestionsView = null
            frenchRow?.removeAllViews()
            frenchRow = null
            kreyolRow = null
            frenchRowScroll = null
            mainKeyboardView = null
            
            Log.d(TAG, "Nettoyage terminé avec succès - Compatible A21s")
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du nettoyage: ${e.message}", e)
        } finally {
            super.onDestroy()
        }
    }
    
    // ===== SUPPRESSION PAR MOTS (APPUI LONG DELETE) =====
    
    /**
     * Démarre la suppression continue par mots avec appui long
     */
    private fun startWordDeletion() {
        if (isDeleteLongPressActive) return
        
        isDeleteLongPressActive = true
        Log.d(TAG, "🔥 Début suppression par mots avec appui long")
        
        // Première suppression immédiate d'un mot
        deleteWordBeforeCursor()
        
        // Puis suppression continue toutes les 300ms
        deleteTimer = Timer()
        deleteTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                deleteHandler.post {
                    if (isDeleteLongPressActive) {
                        deleteWordBeforeCursor()
                    }
                }
            }
        }, 500, 300) // Délai initial 500ms, puis toutes les 300ms
    }
    
    /**
     * Arrête la suppression continue par mots
     */
    private fun stopWordDeletion() {
        if (!isDeleteLongPressActive) return
        
        isDeleteLongPressActive = false
        deleteTimer?.cancel()
        deleteTimer = null
        Log.d(TAG, "🛑 Arrêt suppression par mots")
    }
    
    /**
     * Supprime le mot précédent en utilisant les espaces comme délimiteurs
     */
    private fun deleteWordBeforeCursor() {
        val inputConnection = currentInputConnection ?: return
        
        try {
            // Récupérer le texte avant le curseur (jusqu'à 100 caractères)
            val textBeforeCursor = inputConnection.getTextBeforeCursor(100, 0)?.toString() ?: ""
            
            if (textBeforeCursor.isEmpty()) {
                Log.d(TAG, "Aucun texte avant le curseur")
                return
            }
            
            // Trouver le dernier mot (délimité par des espaces)
            var deleteCount = 0
            var i = textBeforeCursor.length - 1
            
            // Ignorer les espaces en fin
            while (i >= 0 && textBeforeCursor[i].isWhitespace()) {
                deleteCount++
                i--
            }
            
            // Compter les caractères du mot
            while (i >= 0 && !textBeforeCursor[i].isWhitespace()) {
                deleteCount++
                i--
            }
            
            if (deleteCount > 0) {
                inputConnection.deleteSurroundingText(deleteCount, 0)
                Log.d(TAG, "🗑️ Supprimé $deleteCount caractères (mot complet)")
                
                // Optionnel: Le processeur d'entrée se mettra à jour automatiquement
                // via les prochaines interactions utilisateur
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la suppression par mots: ${e.message}")
            // Fallback: suppression caractère par caractère
            inputConnection.deleteSurroundingText(1, 0)
        }
    }
    
    // ===== CHANGEMENT DE CLAVIER (APPUI LONG BARRE D'ESPACE) =====
    
    /**
     * 🌐 Affiche le sélecteur système de clavier (liste des IME installés)
     * Utilisé lors de l'appui long sur la barre d'espace.
     *
     * On affiche toujours le sélecteur plutôt que de basculer directement vers le
     * "prochain" clavier : un changement silencieux d'IME (ex. bascule vers Gboard sans
     * confirmation) après un simple appui d'1 seconde surprend l'utilisateur, qui peut
     * se retrouver sur un autre clavier sans comprendre pourquoi. Le sélecteur laisse
     * le choix explicitement, comme le globe des claviers Android standards.
     */
    private fun showKeyboardPicker() {
        try {
            Log.d(TAG, "🌐 Affichage du sélecteur de claviers")
            val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.showInputMethodPicker()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur affichage sélecteur de claviers: ${e.message}", e)
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
    
    /**
     * 📱 Détecte le mode de navigation système actif
     * @return Code du mode: 0=3-button, 1=2-button, 2=Gesture, -1=Unknown
     */
    private fun detectNavigationMode(): Int {
        return try {
            val navigationMode = android.provider.Settings.Secure.getInt(
                contentResolver,
                "navigation_mode",
                0  // 0 par défaut (3-button)
            )
            
            val modeName = when (navigationMode) {
                0 -> "3-button navigation"
                1 -> "2-button navigation"
                2 -> "Gesture navigation"
                else -> "Unknown navigation mode"
            }
            
            Log.d(TAG, "📱 Mode de navigation détecté: $modeName (valeur: $navigationMode)")
            return navigationMode
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur détection mode navigation: ${e.message}")
            return -1  // Unknown
        }
    }
    
    /**
     * 📏 Calcule le padding bottom adapté selon le mode de navigation
     * Utilise la hauteur réelle de la navigation bar système + marge adaptée
     * @return Padding en pixels
     */
    private fun getAdaptiveNavigationPadding(): Int {
        val navigationMode = detectNavigationMode()
        
        // Obtenir la hauteur système de la navigation bar
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val rawNavBarHeight = if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            (48 * resources.displayMetrics.density).toInt() // Fallback 48dp
        }
        // Certaines ROM OEM (constaté sur un téléphone bas de gamme non identifié,
        // "P300", en portrait uniquement) renvoient une valeur aberrante pour ce
        // dimen système, ce qui pousse tout le clavier hors de l'écran visible.
        // Une vraie navigation bar Android ne dépasse jamais ~64dp : on plafonne
        // pour ignorer les valeurs corrompues plutôt que de leur faire confiance.
        val maxNavBarHeightPx = (64 * resources.displayMetrics.density).toInt()
        val systemNavBarHeight = rawNavBarHeight.coerceIn(0, maxNavBarHeightPx)
        
        val padding = when (navigationMode) {
            0 -> {
                // 3-button navigation: hauteur système + marge sécurité 12dp
                val marginDp = 12
                val marginPx = (marginDp * resources.displayMetrics.density).toInt()
                val paddingPx = systemNavBarHeight + marginPx
                Log.d(TAG, "🔘 3-button: NavBar ${systemNavBarHeight}px + ${marginDp}dp marge = ${paddingPx}px")
                paddingPx
            }
            1 -> {
                // 2-button navigation: hauteur système + marge sécurité 8dp
                val marginDp = 8
                val marginPx = (marginDp * resources.displayMetrics.density).toInt()
                val paddingPx = systemNavBarHeight + marginPx
                Log.d(TAG, "🔘 2-button: NavBar ${systemNavBarHeight}px + ${marginDp}dp marge = ${paddingPx}px")
                paddingPx
            }
            2 -> {
                // Gesture navigation: padding minimal 20dp (juste la barre indicateur)
                val paddingDp = 20
                val paddingPx = (paddingDp * resources.displayMetrics.density).toInt()
                Log.d(TAG, "👆 Gesture: Padding minimal ${paddingDp}dp = ${paddingPx}px")
                paddingPx
            }
            else -> {
                // Mode inconnu: padding de sécurité
                val paddingDp = 48
                val paddingPx = (paddingDp * resources.displayMetrics.density).toInt()
                Log.d(TAG, "⚠️ Mode inconnu: Padding sécurité ${paddingDp}dp = ${paddingPx}px")
                paddingPx
            }
        }
        
        Log.d(TAG, "✅ Padding adaptatif calculé: ${padding}px")
        return padding
    }
    
}
