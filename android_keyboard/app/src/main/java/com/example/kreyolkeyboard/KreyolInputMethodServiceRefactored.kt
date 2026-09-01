package com.example.kreyolkeyboard

/**
 * À la mémoire de mon père, Saint-Ange Corneille Famibelle
 */

import android.inputmethodservice.InputMethodService
import android.content.Context
import android.content.Intent
import android.app.ActivityManager
import android.util.Log
import android.util.TypedValue
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
import android.os.Build
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
import com.example.kreyolkeyboard.stt.MicPermissionActivity
import com.example.kreyolkeyboard.stt.SttSession
import android.widget.ImageView
import android.view.Gravity

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
        private const val MAX_SUGGESTIONS = 5  // 3 Lëtzebuergesch + 2 Français (mode bilingue)
        // Hauteur d'une rangée de suggestions : ce que le clavier consomme en
        // hauteur en dehors des rangées de touches elles-mêmes. Le padding
        // vertical du bloc de touches vit dans KeyboardLayoutManager, qui le pose.
        private const val SUGGESTION_ROW_HEIGHT_DP = 44
        // En paysage les suggestions tiennent sur une seule rangée un peu plus
        // basse : les deux rangées empilées coûtaient 88 dp sur les 359 dp que la
        // fenêtre IME reçoit dans cette orientation (contre 891 dp en portrait).
        private const val SUGGESTION_ROW_HEIGHT_LANDSCAPE_DP = 38
        // Intervalle vide autour d'une puce de suggestion, horizontalement comme
        // verticalement : la marge d'erreur de visée. Cf. ACCESSIBILITE.md, point 1.
        //
        // Cet intervalle n'appartient à aucune vue cliquable, donc un appui qui y
        // tombe ne fait rien, et c'est le bon comportement : un appui perdu coûte un
        // geste, un appui sur le mot voisin en coûte cinq (effacer, retaper,
        // revalider). Mesuré sur Pixel 5 (440 dpi) avant ce changement : 6,9 dp
        // (1,10 mm) entre deux puces d'une même rangée, et surtout **3,6 dp
        // (0,58 mm) entre la rangée luxembourgeoise et la rangée française**, alors que
        // l'imprécision d'un doigt est d'abord verticale. Un demi-millimètre trop
        // bas validait un mot français à la place du mot luxembourgeois visé.
        //
        // Les 12 dp sont pris sur la hauteur des puces (38 dp → 34 dp), pas sur la
        // hauteur des rangées : SUGGESTION_ROW_HEIGHT_DP ne change pas, donc le
        // clavier occupe exactement la même place et aucune autre rangée ne bouge.
        // L'échange est favorable : 0,4 mm de hauteur de cible contre 1,3 mm de
        // séparation.
        private const val SUGGESTION_CHIP_GAP_DP = 12
        // Largeur minimale d'une puce. Ce n'est pas un élargissement : le style
        // Button de la plateforme impose déjà 88 dp, ce qui rend les puces de
        // « an » ou « et » aussi larges que celles des mots longs. La valeur est
        // fixée ici pour que la taille de cible ne dépende plus d'un défaut de
        // thème susceptible de changer sans qu'on s'en aperçoive.
        // Taille du mot proposé dans une puce. Elle valait 14 sp, la plus petite
        // du clavier : 32 px de glyphe quand une lettre de touche en fait 38,
        // alors que c'est ce texte que l'on lit pour décider d'accepter une
        // suggestion. Le fond de la puce mesure 82 px de haut et ne dépend pas
        // d'elle, il restait donc 50 px inutilisés autour du mot. À 18 sp les
        // glyphes rejoignent ceux des touches, et un mot long tient encore dans
        // la rangée réduite du paysage (38 dp).
        // Réserve verticale entre le bord de la barre et la puce, du côté qui
        // touche l'extérieur (haut de la rangée luxembourgeoise, bas de la rangée
        // française). Le côté intérieur, lui, porte l'écart entre les deux
        // rangées : cf. suggestionRowInnerPadDp().
        private const val SUGGESTION_ROW_OUTER_PAD_DP = 4
        private const val SUGGESTION_TEXT_SIZE_SP = 18f
        // Réserve verticale à l'intérieur d'une puce, entre le fond arrondi et le
        // mot. C'est elle qui borne la taille de police réellement affichable :
        // cf. fitTextToChipHeight().
        private const val SUGGESTION_CHIP_PADDING_V_DP = 6

        /**
         * Marge intérieure du bouton micro. Le bouton est carré et fait la
         * hauteur d'une rangée de suggestions ; ce retrait ramène le glyphe à
         * une taille optique comparable à celle du texte des puces, tout en
         * laissant la zone tactile occuper le carré entier.
         */
        private const val MIC_ICON_PADDING_DP = 10

        /**
         * Amplitude du battement du micro, en fraction de sa taille. 18 % se
         * voit du coin de l'œil sans faire déborder le glyphe de la marge que
         * MIC_ICON_PADDING_DP lui réserve.
         */
        private const val MIC_LEVEL_SCALE = 0.18f

        /** Épaisseur du trait de l'anneau tournant autour du micro. */
        private const val MIC_RING_WIDTH_DP = 2

        /**
         * Durée d'un tour d'anneau. Assez lent pour être calme sous le pouce,
         * assez rapide pour qu'on voie le mouvement du coin de l'œil — le
         * témoin du champ de saisie fait son tour en un peu plus d'une
         * demi-seconde, mais lui n'a qu'une seconde et demie à occuper.
         */
        private const val MIC_RING_PERIOD_MS = 1400L

        /**
         * Affiche la durée de chaque passe whisper dans le bandeau de dictée.
         *
         * Diagnostic du canal Labs. La latence de la dictée n'a jamais été
         * mesurée sur un appareil réel : le banc d'essai de `stt/bench` tourne
         * sur un hôte x86 dont les temps ne disent rien d'un ARM, et un binaire
         * déporté par adb mesurerait un processus isolé, sans le rendu du
         * clavier ni le throttling. Le seul chiffre juste se lit ici. À
         * repasser à false une fois la mesure faite.
         */
        private const val SHOW_PASS_TIMING = true

        /**
         * Délègue la dictée au service en ligne LuxASR plutôt qu'au modèle
         * embarqué. **L'audio quitte alors l'appareil.**
         *
         * Vrai uniquement sur `feat/luxasr-online`, la branche de démonstration
         * du rendez-vous avec l'Université du Luxembourg. Ne doit pas atteindre
         * une version publiée avant, dans cet ordre : l'accord de LuxASR, et une
         * politique de confidentialité réécrite — celle qui est en ligne
         * aujourd'hui affirme le contraire.
         */
        private const val USE_LUXASR_ONLINE = true

        /** Durée d'affichage du chronométrage final, une fois la dictée finie. */
        private const val FINAL_TIMING_HOLD_MS = 4000L

        /** Période d'une image de l'indicateur de transcription. */
        private const val SPINNER_PERIOD_MS = 140L

        /** Période d'une image du tracé de niveau affiché pendant la parole. */
        private const val METER_PERIOD_MS = 110L

        /** Largeur du tracé, en barres. */
        private const val METER_BARS = 6

        /** Symbole qui ouvre le tracé, pour qu'on sache de quoi il parle. */
        private const val MIC_GLYPH = "🎤"

        /**
         * Hauteurs du tracé de niveau. Les barres partielles (U+2581–U+2587)
         * sont largement présentes, mais comme les quarts de cercle elles
         * appartiennent à un bloc que rien n'oblige une surcouche à couvrir :
         * même vérification, même repli, pour la même raison — un rectangle
         * vide au milieu du texte de quelqu'un est pire que pas d'animation.
         */
        private val METER_GLYPHS: List<String> by lazy {
            val barres = listOf("▁", "▂", "▃", "▄", "▅", "▆", "▇")
            val dispo = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                barres.all { android.graphics.Paint().hasGlyph(it) }
            if (dispo) barres else listOf(".", ".", ":", ":", "|", "|", "|")
        }

        /**
         * Images de l'indicateur posé dans le champ de saisie.
         *
         * Les quarts de cercle (U+25D0–U+25D3) donnent un vrai cercle qui
         * tourne, mais ils appartiennent au bloc « formes géométriques » et
         * rien ne garantit qu'une surcouche les couvre — un caractère manquant
         * afficherait un rectangle vide au milieu du texte de l'utilisateur,
         * ce qui est pire que pas d'animation du tout. On vérifie donc leur
         * présence à l'exécution, et on retombe sur des points, qui existent
         * partout, quand la police ne suit pas. `hasGlyph` n'existe qu'à
         * partir d'Android 6 ; en dessous on ne prend pas le risque.
         */
        private val SPINNER_FRAMES: List<String> by lazy {
            val cercles = listOf("◐", "◓", "◑", "◒")
            val dispo = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                cercles.all { android.graphics.Paint().hasGlyph(it) }
            if (dispo) cercles else listOf("·", "··", "···", "··")
        }
        private const val SUGGESTION_CHIP_MIN_WIDTH_DP = 88
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

        /**
         * Réglages du clavier lui-même, distincts de l'accueil et de la
         * progression. Le premier est la correction automatique de la
         * Groussschreiwung, active par défaut : le luxembourgeois capitalise
         * tous ses substantifs, et l'intérêt de la fonction est précisément
         * qu'elle agisse sans qu'on la cherche. Elle reste débrayable, parce
         * qu'une correction imposée que l'on ne peut pas éteindre est une
         * fonction subie.
         */
        const val KEYBOARD_PREFS_NAME = "lux_keyboard_prefs"
        const val PREF_AUTO_CAPITALIZE = "auto_capitalize_nouns"
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
    private var luxRow: LinearLayout? = null
    private var frenchRow: LinearLayout? = null
    private var frenchRowScroll: HorizontalScrollView? = null
    private var mainKeyboardView: View? = null

    /**
     * Dictée vocale. Construite paresseusement au premier appui sur le micro :
     * whisper alloue ~165 Mo de tampons de calcul, qu'il serait absurde de
     * réserver dans le processus IME pour un utilisateur qui ne dicte jamais.
     */
    private var sttSession: com.example.kreyolkeyboard.stt.DictationSession? = null
    private var micButton: ImageView? = null
    @Volatile private var dictationLevel = 0f
    private var micRing: MicRingDrawable? = null
    private var micRingAnimator: android.animation.ValueAnimator? = null

    /**
     * Bandeau d'état de la dictée, affiché à la place des suggestions.
     *
     * Le changement de teinte du micro ne suffisait pas : sur un téléphone en
     * main, une icône de 8 mm qui passe du gris au vert ne se remarque pas, et
     * l'utilisateur n'avait donc aucun moyen de savoir que le clavier
     * l'écoutait — ni, ensuite, qu'il était en train de transcrire.
     */
    private var dictationStatusView: TextView? = null

    /**
     * Chronométrage de la dernière passe whisper, affiché dans le bandeau.
     *
     * Diagnostic du canal Labs, pas une fonctionnalité : la latence de la
     * dictée n'a jamais été mesurée sur un appareil réel. Mettre
     * [SHOW_PASS_TIMING] à false suffit à le retirer, sans rien changer
     * d'autre.
     */
    private var lastPassTiming: String? = null
    private var pendingFinalTiming: String? = null
    private var luxScrollView: HorizontalScrollView? = null

    /**
     * Visibilité de la rangée française avant que la dictée ne la masque.
     * Elle est pilotée par le moteur de suggestions ; la dictée doit la rendre
     * telle qu'elle l'a trouvée, sans quoi des suggestions périmées
     * réapparaîtraient sous le texte dicté.
     */
    private var frenchRowVisibilityBeforeDictation: Int? = null

    /**
     * Texte en composition posé par la dictée. Distinct du mot courant
     * d'InputProcessor : celui-ci suit la frappe au clavier, celui-là est
     * remplacé en bloc à chaque hypothèse de whisper.
     */
    private var dictationComposing = false
    private var dictationPartial = ""
    private val spinnerHandler = Handler(Looper.getMainLooper())
    private var spinnerRunnable: Runnable? = null
    private var spinnerFrame = 0
    /** Dernières énergies captées, de la plus ancienne à la plus récente. */
    private val niveauxRecents = ArrayDeque<Float>()

    /**
     * Palette avec laquelle la vue d'entrée courante a été construite.
     *
     * C'est la seule façon fiable de savoir s'il faut la reconstruire : les
     * couleurs sont figées dans les widgets au moment de leur création, et rien
     * dans [KeyboardTheme] ne peut répondre à la place du service, l'écran de
     * réglages rafraîchissant la palette globale avant que le service reprenne la
     * main. `null` tant qu'aucune vue n'a été construite.
     */
    private var paletteDeLaVue: KeyboardTheme.Palette? = null
    
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
        Log.d(TAG, "=== LUXEMBOURGISH IME SERVICE REFACTORISÉ onCreate() ===")
        
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

        // Correction de la Groussschreiwung à la validation du mot. Trois
        // conditions, dans cet ordre : le réglage est actif, le champ n'est pas
        // sensible — on ne retouche jamais un mot de passe — et le contexte
        // n-gramme atteste réellement la forme capitalisée. Le moteur ne
        // capitalise pas sur la seule casse canonique du dictionnaire :
        // 161 des 662 mots du dictionnaire français de secours y sont
        // capitalisés (« rue », « moment », « centre »…), et un message en
        // français en ressortirait défiguré.
        inputProcessor.setCapitalizationProvider { mot ->
            if (!capitalisationAutomatiqueActive() || isSensitiveField()) null
            else suggestionEngine.contextualCapitalization(mot)
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

                // 🟢🔵 Support bilingue Lëtzebuergesch + Français (Lëtzebuergesch-first, Français dès 3 lettres)
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
    
    private fun capitalisationAutomatiqueActive(): Boolean =
        getSharedPreferences(KEYBOARD_PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_AUTO_CAPITALIZE, true)

    override fun onCreateInputView(): View? {
        Log.d(TAG, "onCreateInputView() appelée")

        // Avant toute vue : les couleurs sont posées sur les widgets à leur
        // construction, donc la palette doit être résolue en amont. C'est aussi ce
        // qui rattrape une rotation ou une bascule jour/nuit du système, qui
        // rappellent cette méthode sans repasser par onStartInputView().
        KeyboardTheme.refresh(this)
        paletteDeLaVue = KeyboardTheme.palette()

        // 🅰️ FORCER LE MODE ALPHABÉTIQUE AU DÉMARRAGE
        keyboardLayoutManager.forceAlphabeticMode()
        Log.d(TAG, "✅ Mode alphabétique forcé lors de la création du clavier")

        // La palette est résolue avant de poser la moindre couleur, et retenue :
        // c'est elle que onStartInputView() comparera pour savoir si la vue
        // gardée en cache par InputMethodService est encore à la bonne couleur.
        KeyboardTheme.refresh(this)
        paletteDeLaVue = KeyboardTheme.palette()
        
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(KeyboardTheme.palette().fondClavier)
        }
        
        // Créer la zone de suggestions
        createSuggestionsArea(mainLayout)
        
        // 📱 Conteneur dont le bas se décale de ce que la barre de navigation
        // masque réellement du clavier, mesuré après la mise en page (voir
        // adjustForNavigationBarOverlap). Zéro tant que rien ne prouve un
        // recouvrement : le système place déjà la fenêtre de saisie au-dessus de
        // la barre, et réserver la place une seconde fois laissait une bande
        // vide entre la dernière rangée et le bas de l'écran.
        val keyboardContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
        }
        mainLayout.post { adjustForNavigationBarOverlap(mainLayout, keyboardContainer) }

        // Créer le clavier principal, dimensionné pour la place que la fenêtre
        // IME accordera réellement (voir availableRowsHeightPx)
        keyboardLayoutManager.setAvailableRowsHeight(computeAvailableRowsHeight())
        val keyboardLayout = keyboardLayoutManager.createKeyboardLayout()
        keyboardContainer.addView(keyboardLayout)
        mainLayout.addView(keyboardContainer)
        mainKeyboardView = keyboardContainer

        return mainLayout
    }
    
    /**
     * Crée la zone des suggestions : deux rangées empilées (Lëtzebuergesch puis Français) pour
     * que le français reste toujours entièrement visible, sans scroll ni troncature,
     * même quand les suggestions luxembourgeoises sont longues ("Sproochenmeeschter"). La rangée
     * française reste toujours réservée en hauteur (INVISIBLE, jamais GONE) même
     * quand elle est vide : un GONE/VISIBLE dynamique décale toutes les rangées du
     * clavier en dessous pendant la frappe (ex. au franchissement du seuil de 3
     * lettres qui active le fallback français), ce qui fait atterrir un tap en cours
     * sur la touche de la rangée voisine — bug constaté et corrigé le 23/07/2026.
     */
    private fun createSuggestionsArea(parentLayout: LinearLayout) {
        val rowHeightPx = dpToPx(suggestionRowHeightDp())

        val suggestionsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(KeyboardTheme.palette().fondSuggestions)
        }

        val luxScroll = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                rowHeightPx
            )
            // Le padding bas porte la moitié de l'intervalle qui sépare une puce
            // kréyòl de la puce française juste en dessous, l'autre moitié venant du
            // padding haut de la rangée française. En paysage cette rangée n'existe
            // pas : rien à séparer, et la hauteur disponible y est trop courte pour
            // la dépenser en vide.
            setPadding(
                dpToPx(8),
                dpToPx(SUGGESTION_ROW_OUTER_PAD_DP),
                dpToPx(8),
                dpToPx(suggestionRowInnerPadDp())
            )
        }
        luxRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        luxScroll.addView(luxRow)

        // Le micro occupe le bord droit de la première rangée de suggestions.
        // C'est la seule place disponible sans rien sacrifier : la rangée du bas
        // pèse déjà exactement 12 unités de largeur, et l'appui long sur la barre
        // d'espace est pris par le sélecteur de claviers du système.
        val luxRowWithMic = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                rowHeightPx
            )
            gravity = Gravity.CENTER_VERTICAL
        }
        // Largeur 0 + poids 1 : les suggestions prennent toute la place que le
        // micro ne consomme pas, et cessent donc de passer sous lui quand elles
        // sont longues.
        luxScroll.layoutParams = LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
        )
        luxRowWithMic.addView(luxScroll)
        luxRowWithMic.addView(createDictationStatusView())
        luxRowWithMic.addView(createMicButton(rowHeightPx))
        luxScrollView = luxScroll
        suggestionsContainer.addView(luxRowWithMic)

        // En paysage la seconde rangée n'est pas construite du tout : les suggestions
        // françaises rejoignent la rangée luxembourgeoise, qui défile horizontalement et où
        // l'étiquette de langue et la couleur des puces les distinguent déjà. Le
        // décalage des rangées de touches que la réserve permanente évitait ne peut
        // pas se produire ici, puisque la mise en page est figée pour l'orientation :
        // rien n'apparaît ni ne disparaît pendant la frappe.
        // Le service survit aux rotations : sans cette remise à null, ces champs
        // gardent la rangée française construite lors d'une mise en page portrait
        // précédente, vue détachée de la hiérarchie affichée. Les suggestions
        // françaises y étaient bien ajoutées, mais dans le vide, et disparaissaient
        // de l'écran dès la première rotation.
        frenchRow = null
        frenchRowScroll = null

        if (!isLandscape()) {
            val frScroll = HorizontalScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    rowHeightPx
                )
                // Moitié haute de l'intervalle entre les deux rangées, cf. luxScroll.
                setPadding(
                    dpToPx(8),
                    dpToPx(suggestionRowInnerPadDp()),
                    dpToPx(8),
                    dpToPx(SUGGESTION_ROW_OUTER_PAD_DP)
                )
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
            suggestionsContainer.addView(frScroll)
        }

        // Alias historique : les modes non-bilingues (prédictions contextuelles) affichent
        // dans la rangée du haut, la rangée française reste masquée dans ce cas.
        suggestionsView = luxRow

        parentLayout.addView(suggestionsContainer)
    }

    // =========================================================================
    // DICTÉE VOCALE
    //
    // Modèle whisper tiny affiné pour le luxembourgeois (unilux/LuxASR),
    // embarqué dans l'APK et exécuté localement : aucun octet d'audio ne quitte
    // l'appareil, ce qui laisse la politique de confidentialité inchangée.
    // =========================================================================

    /**
     * Bouton micro, carré, calé sur la hauteur d'une rangée de suggestions pour
     * ne pas rallonger le clavier d'un pixel.
     */
    private fun createMicButton(rowHeightPx: Int): View {
        val button = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(rowHeightPx, rowHeightPx)
            setImageResource(R.drawable.ic_mic)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val pad = dpToPx(MIC_ICON_PADDING_DP)
            setPadding(pad, pad, pad, pad)
            contentDescription = getString(R.string.stt_mic_description)
            isClickable = true
            isFocusable = true
            setOnClickListener { onMicTapped() }
        }
        micButton = button
        applyMicTint(listening = false)
        return button
    }

    /**
     * Bandeau occupant la place des suggestions pendant la dictée. Construit
     * une fois et masqué, plutôt qu'ajouté et retiré : insérer une vue dans la
     * rangée en cours de dictée relance une passe de mise en page sous le
     * doigt de l'utilisateur.
     */
    private fun createDictationStatusView(): View {
        val view = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
            )
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(12), 0, dpToPx(8), 0)
            textSize = SUGGESTION_TEXT_SIZE_SP - 2f
            maxLines = 1
            visibility = View.GONE
        }
        dictationStatusView = view
        return view
    }

    /**
     * Le micro n'a que deux états visuels, et ils doivent se distinguer sans
     * couleur : l'opacité change en même temps que la teinte, pour rester
     * lisible en cas de daltonisme comme sur un écran délavé au soleil.
     */
    private fun applyMicTint(listening: Boolean) {
        val palette = paletteDeLaVue ?: KeyboardTheme.palette()
        micButton?.apply {
            setColorFilter(if (listening) palette.accent else palette.encreAttenuee)
            alpha = if (listening) 1.0f else 0.65f
            if (!listening) {
                scaleX = 1f
                scaleY = 1f
            }
        }
        if (listening) startMicRing(palette.accent) else stopMicRing()
    }

    /**
     * Fait tourner un anneau autour du micro pendant l'écoute.
     *
     * Le micro pulsait déjà au rythme de la voix, mais ce retour s'éteint
     * exactement quand on doute : entre deux phrases, dans une pièce calme,
     * quand on hésite avant de commencer. L'anneau tourne indépendamment de ce
     * qui est capté, et dit donc « ça écoute » et non « ça entend ».
     *
     * Même vocabulaire visuel que le témoin posé dans le champ de saisie
     * pendant la transcription — un arc qui fait le tour — pour que les deux
     * temps de la dictée se lisent comme une seule chose qui avance.
     *
     * L'animation est supprimée quand le système annonce des animations
     * désactivées : c'est un réglage d'accessibilité, pas une préférence
     * esthétique, et une rotation perpétuelle est exactement ce qu'il vise.
     */
    private fun startMicRing(couleur: Int) {
        val button = micButton ?: return
        micRing?.teinte(couleur)
        if (micRingAnimator?.isRunning == true) return

        val anneau = micRing ?: MicRingDrawable(couleur, dpToPx(MIC_RING_WIDTH_DP).toFloat())
            .also { micRing = it }
        button.background = anneau

        if (android.provider.Settings.Global.getFloat(
                contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) {
            anneau.angle = -90f          // arc figé en haut : présent, immobile
            return
        }

        micRingAnimator = android.animation.ValueAnimator.ofFloat(0f, 360f).apply {
            duration = MIC_RING_PERIOD_MS
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { anneau.angle = it.animatedValue as Float - 90f }
            start()
        }
    }

    private fun stopMicRing() {
        micRingAnimator?.cancel()
        micRingAnimator = null
        micButton?.background = null
    }

    /**
     * Affiche le bandeau de dictée, ou le retire et rend la rangée de
     * suggestions. [resId] à null remet l'affichage normal.
     */
    private fun showDictationStatus(resId: Int?) {
        val status = dictationStatusView ?: return
        val palette = paletteDeLaVue ?: KeyboardTheme.palette()

        if (resId == null) {
            status.visibility = View.GONE
            luxScrollView?.visibility = View.VISIBLE
            frenchRowVisibilityBeforeDictation?.let { frenchRowScroll?.visibility = it }
            frenchRowVisibilityBeforeDictation = null
            return
        }

        if (status.visibility != View.VISIBLE) {
            // Les suggestions affichées sont celles du mot tapé avant l'appui
            // sur le micro : les laisser sous un texte dicté inviterait à
            // valider un mot sans rapport.
            frenchRowVisibilityBeforeDictation = frenchRowScroll?.visibility
            frenchRowScroll?.visibility = View.INVISIBLE
            luxScrollView?.visibility = View.GONE
            status.visibility = View.VISIBLE
        }
        status.setTextColor(palette.encre)
        val timing = lastPassTiming.takeIf { SHOW_PASS_TIMING }
        status.text = if (timing == null) getString(resId)
                      else "${getString(resId)}  $timing"
    }

    /**
     * Laisse le chronométrage de la passe finale à l'écran quelques secondes
     * après la fin de la dictée. Sans ce délai, le bandeau disparaît dans le
     * même souffle que l'arrivée du texte et le chiffre n'est jamais lisible.
     */
    private fun holdFinalTiming() {
        val timing = pendingFinalTiming ?: return
        pendingFinalTiming = null
        val status = dictationStatusView ?: return
        lastPassTiming = timing
        // Le passage à IDLE a déjà rendu les deux rangées de suggestions : il
        // faut les remasquer, et surtout re-mémoriser l'état de la rangée
        // française, que showDictationStatus(null) vient d'oublier — sans quoi
        // elle resterait masquée après le délai.
        frenchRowVisibilityBeforeDictation = frenchRowScroll?.visibility
        frenchRowScroll?.visibility = View.INVISIBLE
        luxScrollView?.visibility = View.GONE
        status.visibility = View.VISIBLE
        status.text = "✅ $timing"
        status.postDelayed({
            lastPassTiming = null
            if (sttSession?.isBusy != true) showDictationStatus(null)
        }, FINAL_TIMING_HOLD_MS)
    }

    /**
     * Fait respirer le micro au rythme de la voix captée. C'est le seul retour
     * réellement immédiat : la première hypothèse de whisper n'arrive qu'après
     * une seconde ou deux, pendant lesquelles rien ne distinguait un micro qui
     * enregistre d'un micro muet.
     */
    private fun applyMicLevel(level: Float) {
        val button = micButton ?: return
        val scale = 1f + MIC_LEVEL_SCALE * level.coerceIn(0f, 1f)
        button.scaleX = scale
        button.scaleY = scale
    }

    private fun onMicTapped() {
        val session = sttSession
        if (session != null && session.isActive) {
            // Second appui : l'utilisateur a fini de parler, on fige.
            session.stop()
            return
        }
        if (session != null && session.isBusy) {
            // Finalisation en cours : le micro est déjà fermé mais la dernière
            // passe tourne encore. On ignore l'appui plutôt que d'ouvrir une
            // seconde dictée par-dessus, dont le texte se mélangerait au sien.
            return
        }

        // Un mot de passe dicté finirait dans le presse-papier vocal du
        // système chez d'autres claviers ; ici il ne sortirait pas de
        // l'appareil, mais la règle du projet est qu'un champ sensible ne
        // déclenche aucun traitement du tout.
        if (isSensitiveField()) {
            showDictationMessage(R.string.stt_not_in_password)
            return
        }

        if (!MicPermissionActivity.hasPermission(this)) {
            MicPermissionActivity.request(this) { granted ->
                if (granted) startDictation()
                else showDictationMessage(R.string.stt_permission_denied)
            }
            return
        }

        startDictation()
    }

    private fun startDictation() {
        val session = sttSession ?: newDictationSession().also { sttSession = it }
        dictationComposing = false
        session.start()
    }

    /**
     * Choisit entre la dictée embarquée et le service en ligne LuxASR.
     *
     * Ce n'est pas un réglage d'implémentation. La dictée embarquée garantit que
     * l'audio ne quitte jamais l'appareil, ce sur quoi repose la politique de
     * confidentialité publiée ; le service en ligne rompt cette garantie en
     * échange d'une qualité que le matériel mobile ne permet pas — 72 % de WER
     * mesurés pour le modèle embarqué contre 25 % pour le service, avec
     * ponctuation et capitalisation.
     *
     * Le chemin en ligne est l'API par lots `/asr2`, et non le WebSocket : à
     * qualité et à délai final mesurés équivalents, elle décode l'énoncé d'un
     * seul tenant et supprime toute la segmentation. Voir [LuxAsrApiSession]
     * pour les chiffres ; [LuxAsrSession] reste sur la branche en repli.
     *
     * [USE_LUXASR_ONLINE] n'est vrai que sur la branche de démonstration
     * préparée pour le rendez-vous avec l'Université du Luxembourg, dont le
     * service demande explicitement qu'on les contacte avant toute intégration.
     */
    private fun newDictationSession(): com.example.kreyolkeyboard.stt.DictationSession =
        if (USE_LUXASR_ONLINE) com.example.kreyolkeyboard.stt.LuxAsrApiSession(dictationListener)
        else SttSession(this, dictationListener)

    private val dictationListener = object : SttSession.Listener {

        /**
         * Hypothèse intermédiaire : posée en texte de composition, donc
         * soulignée et remplaçable en bloc. C'est exactement la sémantique dont
         * whisper a besoin — chaque passe rend une phrase entière qui annule et
         * remplace la précédente, sans qu'il faille recoller des fragments.
         */
        override fun onPartial(text: String) {
            val ic = currentInputConnection ?: return
            stopDictationSpinner()
            dictationPartial = text
            dictationComposing = true
            ic.setComposingText(text, 1)
        }

        override fun onFinal(text: String) {
            val ic = currentInputConnection ?: return
            stopDictationSpinner()
            if (text.isNotEmpty()) {
                ic.setComposingText(text, 1)
            } else if (dictationPartial.isEmpty()) {
                // Rien à valider et rien à garder : l'animation était le seul
                // contenu du champ, il faut l'effacer.
                ic.setComposingText("", 1)
            } else {
                // On a coupé l'animation mais le champ porte encore la dernière
                // hypothèse suivie du glyphe : on le réécrit sans lui.
                ic.setComposingText(dictationPartial, 1)
            }
            // Si la passe finale ne rend rien alors que des hypothèses avaient
            // été affichées, on garde la dernière plutôt que d'effacer sous les
            // yeux de l'utilisateur un texte qu'il a vu se construire.
            ic.finishComposingText()
            dictationComposing = false
            dictationPartial = ""
        }

        override fun onLevel(level: Float) {
            dictationLevel = level
            applyMicLevel(level)
        }

        override fun onPassTiming(audioSeconds: Float, ms: Long, partial: Boolean) {
            if (!SHOW_PASS_TIMING) return
            lastPassTiming = "%.1f s → %d ms".format(audioSeconds, ms)
            if (partial) {
                // Rafraîchit le bandeau en place, sans le faire réapparaître
                // s'il a déjà été retiré.
                if (dictationStatusView?.visibility == View.VISIBLE) {
                    showDictationStatus(
                        if (USE_LUXASR_ONLINE) R.string.stt_online_listening
                        else R.string.stt_listening
                    )
                }
            } else {
                // La passe finale est le chiffre qui compte — le délai entre le
                // relâchement du micro et le texte. Il est retenu ici parce que
                // le passage à IDLE, qui suit immédiatement, retirerait le
                // bandeau avant qu'on ait pu le lire.
                pendingFinalTiming = lastPassTiming
            }
        }

        override fun onStateChanged(state: SttSession.State) {
            applyMicTint(listening = state == SttSession.State.LISTENING)
            when (state) {
                SttSession.State.LISTENING -> startDictationMeter()
                SttSession.State.FINALIZING -> {
                    // Le VU-mètre et le témoin se partagent la même boucle :
                    // on l'arrête avant de la relancer sous l'autre forme.
                    stopDictationSpinner()
                    startDictationSpinner()
                }
                else -> stopDictationSpinner()
            }
            showDictationStatus(
                when (state) {
                    // Le chargement du modèle prend jusqu'à une seconde au
                    // premier appui : sans message, l'utilisateur croit que
                    // son appui n'a pas été pris et appuie une seconde fois.
                    SttSession.State.LOADING ->
                        if (USE_LUXASR_ONLINE) R.string.stt_online_connecting
                        else R.string.stt_preparing
                    SttSession.State.LISTENING ->
                        if (USE_LUXASR_ONLINE) R.string.stt_online_listening
                        else R.string.stt_listening
                    SttSession.State.FINALIZING ->
                        if (USE_LUXASR_ONLINE) R.string.stt_online_transcribing
                        else R.string.stt_transcribing
                    SttSession.State.IDLE -> null
                }
            )
            if (state == SttSession.State.IDLE) holdFinalTiming()
        }

        override fun onError(error: SttSession.Error) {
            stopDictationSpinner()
            if (dictationComposing) currentInputConnection?.finishComposingText()
            dictationComposing = false
            dictationPartial = ""
            showDictationMessage(
                when (error) {
                    SttSession.Error.MIC_UNAVAILABLE -> R.string.stt_mic_unavailable
                    SttSession.Error.MODEL_UNAVAILABLE -> R.string.stt_model_unavailable
                    SttSession.Error.SERVICE_UNREACHABLE -> R.string.stt_service_unreachable
                }
            )
        }
    }

    /**
     * Petit indicateur de travail posé **dans le champ de saisie**, en texte de
     * composition, pendant que la transcription se calcule.
     *
     * Il existe parce que l'API par lots ne rend rien avant la fin : entre
     * l'arrêt du micro et le texte il s'écoule 1,7 s en médiane (mesuré sur
     * 62 énoncés, 1,8 s au pire), pendant lesquelles le champ resterait vide et
     * le clavier muet. Sans repère, l'utilisateur croit que son appui n'a pas
     * été pris — c'est le même raisonnement que le bandeau « préparation », à
     * ceci près que le regard est sur le curseur, pas sur le clavier.
     *
     * Le texte de composition est le bon véhicule : souligné, transitoire, et
     * remplacé d'un bloc par la transcription quand elle arrive. Rien n'est
     * jamais validé dans le champ.
     *
     * Quand des hypothèses ont déjà été affichées — c'est le cas du flux
     * WebSocket, pas de l'API — l'indicateur se place **après** elles, pour que
     * l'animation dise « ce n'est pas fini » plutôt que d'effacer ce que
     * l'utilisateur a vu se construire.
     */
    /**
     * Petit micro suivi d'un tracé de niveau, posé **dans le champ de saisie**
     * pendant qu'on parle.
     *
     * Même raison que le témoin de transcription, à l'autre bout de la dictée,
     * et même endroit : le regard est sur le curseur, pas sur le clavier. La
     * différence est que celui-ci n'est pas décoratif — les barres suivent
     * l'énergie réellement captée, donc l'animation dit « je t'entends » et non
     * seulement « je tourne ». Un micro coupé par une autre application, une
     * main posée dessus, une voix trop lointaine se voient immédiatement.
     *
     * Le tracé défile : chaque image décale l'historique d'un cran et pousse la
     * mesure du moment à droite, du côté du curseur, là où le texte va
     * atterrir.
     *
     * Cadencé à [METER_PERIOD_MS] et non aux ~25 mesures par seconde que publie
     * la capture : chaque image est un `setComposingText`, donc un aller-retour
     * vers l'application et un rendu de sa part. Vingt-cinq par seconde
     * saccadent les champs lents pour une animation que personne ne lit à cette
     * vitesse.
     */
    private fun startDictationMeter() {
        if (spinnerRunnable != null) return
        if (currentInputConnection == null) return
        niveauxRecents.clear()
        val boucle = object : Runnable {
            override fun run() {
                val conn = currentInputConnection ?: return
                niveauxRecents.addLast(dictationLevel)
                while (niveauxRecents.size > METER_BARS) niveauxRecents.removeFirst()
                val trace = StringBuilder()
                repeat(METER_BARS - niveauxRecents.size) { trace.append(METER_GLYPHS.first()) }
                for (n in niveauxRecents) {
                    val i = (n.coerceIn(0f, 1f) * (METER_GLYPHS.size - 1)).toInt()
                    trace.append(METER_GLYPHS[i])
                }
                conn.setComposingText("$MIC_GLYPH $trace", 1)
                dictationComposing = true
                spinnerHandler.postDelayed(this, METER_PERIOD_MS)
            }
        }
        spinnerRunnable = boucle
        boucle.run()
    }

    private fun startDictationSpinner() {
        if (spinnerRunnable != null) return
        val ic = currentInputConnection ?: return
        spinnerFrame = 0
        val boucle = object : Runnable {
            override fun run() {
                val conn = currentInputConnection ?: return
                val prefixe = if (dictationPartial.isEmpty()) "" else "$dictationPartial "
                conn.setComposingText(
                    prefixe + SPINNER_FRAMES[spinnerFrame % SPINNER_FRAMES.size], 1)
                dictationComposing = true
                spinnerFrame++
                spinnerHandler.postDelayed(this, SPINNER_PERIOD_MS)
            }
        }
        spinnerRunnable = boucle
        boucle.run()
    }

    /**
     * Retire l'animation. Ne touche pas au champ : l'appelant enchaîne toujours
     * sur un `setComposingText` définitif ou sur un `finishComposingText`, et
     * effacer ici ferait clignoter le champ entre les deux.
     */
    private fun stopDictationSpinner() {
        spinnerRunnable?.let { spinnerHandler.removeCallbacks(it) }
        spinnerRunnable = null
    }

    private fun showDictationMessage(resId: Int) {
        applyMicTint(listening = false)
        showDictationStatus(null)
        Toast.makeText(this, getString(resId), Toast.LENGTH_SHORT).show()
    }

    /**
     * Coupe la dictée et abandonne le texte en composition. Appelée dès que le
     * clavier change de champ ou disparaît : laisser tourner le micro derrière
     * un clavier fermé serait à la fois un bug de batterie et un problème de
     * confiance.
     */
    private fun cancelDictation() {
        val session = sttSession ?: return
        if (!session.isActive && !dictationComposing) return

        session.cancel()
        stopDictationSpinner()
        dictationPartial = ""
        if (dictationComposing) {
            currentInputConnection?.finishComposingText()
            dictationComposing = false
        }
        applyMicTint(listening = false)
        showDictationStatus(null)
    }

    /** Hauteur d'une rangée de suggestions, réduite en paysage. */
    private fun suggestionRowHeightDp(): Int =
        if (isLandscape()) SUGGESTION_ROW_HEIGHT_LANDSCAPE_DP else SUGGESTION_ROW_HEIGHT_DP

    /** Nombre de rangées de suggestions réellement empilées : une seule en paysage. */
    private fun suggestionRowCount(): Int = if (isLandscape()) 1 else 2

    /**
     * Réserve verticale du côté intérieur d'une rangée : la moitié de l'écart qui
     * sépare les deux rangées empilées. En paysage il n'y a qu'une rangée, donc
     * rien à séparer, et la hauteur y est trop courte pour la dépenser en vide.
     */
    private fun suggestionRowInnerPadDp(): Int =
        if (suggestionRowCount() > 1) SUGGESTION_CHIP_GAP_DP / 2 else 2

    /**
     * Hauteur réellement occupée par une puce : la rangée moins ce que son
     * conteneur réserve en haut et en bas. Les puces sont en MATCH_PARENT, donc
     * c'est aussi la place dont dispose le mot qu'elles portent.
     */
    private fun suggestionChipHeightPx(): Int =
        dpToPx(suggestionRowHeightDp()) - dpToPx(SUGGESTION_ROW_OUTER_PAD_DP) - dpToPx(suggestionRowInnerPadDp())

    /**
     * Ramène la police d'une puce à ce que sa hauteur peut afficher en entier.
     *
     * La taille demandée est en sp : elle suit donc l'échelle de police du
     * système, alors que la puce, elle, est en dp et ne bouge pas. Au réglage
     * « Grande » d'Android, la ligne de texte devenait plus haute que la puce et
     * TextView la rognait à hauteur du padding : mesuré sur émulateur à
     * l'échelle 1,3, il restait 29 px de vide au-dessus du mot contre 16 en
     * dessous, jambages de « j », « q » et « g » coupés net. Le mot paraissait
     * posé trop bas dans sa puce alors qu'il était simplement trop grand pour
     * elle.
     *
     * Les touches du clavier règlent le même problème en dérivant leur police de
     * la hauteur de touche (cf. KeyboardLayoutManager) ; ici on garde la taille
     * en sp tant qu'elle tient, et on ne la réduit qu'au-delà, pour respecter le
     * réglage de l'utilisateur aussi loin que la barre le permet.
     *
     * La hauteur de ligne se lit sur la police elle-même (ascent/descent, sans
     * la réserve de police puisque includeFontPadding est désactivé), et non sur
     * un ratio écrit en dur qui vieillirait mal si la police changeait.
     */
    private fun fitTextToChipHeight(view: TextView) {
        val dispoPx = suggestionChipHeightPx() - 2 * dpToPx(SUGGESTION_CHIP_PADDING_V_DP)
        val metrics = view.paint.fontMetricsInt
        val lignePx = metrics.descent - metrics.ascent
        if (lignePx > dispoPx && dispoPx > 0) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, view.textSize * dispoPx / lignePx)
        }
    }

    /**
     * Demi-intervalle porté par chaque puce de suggestion. Deux puces voisines
     * portent chacune la leur, donc le vide effectif entre elles vaut
     * SUGGESTION_CHIP_GAP_DP.
     */
    private fun suggestionChipHalfGapPx(): Int = dpToPx(SUGGESTION_CHIP_GAP_DP) / 2

    private fun isLandscape(): Boolean = KeyboardLayoutManager.isLandscape(this)
    
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
        Log.d(TAG, "🟢 Dictionnaire luxembourgeois chargé: $wordCount mots")
    }
    
    override fun onFrenchDictionaryLoaded(wordCount: Int) {
        Log.d(TAG, "🔵 Dictionnaire français chargé: $wordCount mots")
    }
    
    override fun onNgramModelLoaded() {
        Log.d(TAG, "🟢 Modèle N-gram luxembourgeois chargé")
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
                // setCurrentWord() et non une mise à jour muette : c'est le
                // rappel onWordChanged() qui régénère les suggestions, exactement
                // comme pour une lettre tapée normalement. Sans lui, un mot
                // commencé par un digraphe choisi en appui long ("tj", "dj",
                // "ch"...) n'obtenait jamais de propositions.
                inputProcessor.setCurrentWord(updatedWord)
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
     * hors bilingue, ou prédictions contextuelles n-gram — toutes deux Lëtzebuergesch uniquement).
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
                addSuggestionChip(container, BilingualSuggestion(suggestion, 0f, SuggestionLanguage.LUXEMBOURGISH))
            }
        }
    }
    
    /**
     * 🎯 Affiche les suggestions bilingues sur deux rangées empilées (Lëtzebuergesch en haut,
     * Français en bas) : le français reste toujours entièrement visible, sans scroll
     * ni troncature, même quand une suggestion luxembourgeoise est longue ("Sproochenmeeschter"). La
     * rangée française se masque (hauteur nulle) quand elle est vide.
     */
    private fun displayBilingualSuggestions(suggestions: List<BilingualSuggestion>) {
        Log.d(TAG, "displayBilingualSuggestions appelé avec ${suggestions.size} suggestions bilingues")
        val luxContainer = luxRow ?: return
        // En paysage il n'y a qu'une rangée : les deux langues la partagent.
        val frenchContainer = frenchRow ?: luxContainer

        luxContainer.removeAllViews()
        frenchContainer.removeAllViews()

        val luxSuggestions = suggestions.filter { it.language == SuggestionLanguage.LUXEMBOURGISH }
        val frenchSuggestions = suggestions.filter { it.language == SuggestionLanguage.FRENCH }

        if (luxSuggestions.isNotEmpty()) {
            addLanguageLabel(luxContainer, luxSuggestions.first().getShortLabel())
            luxSuggestions.forEach { addSuggestionChip(luxContainer, it) }
        }

        if (frenchSuggestions.isNotEmpty()) {
            addLanguageLabel(frenchContainer, frenchSuggestions.first().getShortLabel())
            frenchSuggestions.forEach { addSuggestionChip(frenchContainer, it) }
        }
        // INVISIBLE (jamais GONE) : garder la hauteur de la rangée réservée en permanence
        // évite que l'apparition/disparition des suggestions françaises ne décale les
        // rangées du clavier en dessous pendant la frappe (cf. bug du 23/07/2026).
        frenchRowScroll?.visibility = if (frenchSuggestions.isNotEmpty()) View.VISIBLE else View.INVISIBLE

        Log.d(TAG, "✅ ${suggestions.size} suggestions bilingues affichées (${luxSuggestions.size} Lëtzebuergesch / ${frenchSuggestions.size} Français)")
    }

    /**
     * Ajoute le micro-label (KR/FR) en tête d'une rangée de suggestions
     */
    private fun addLanguageLabel(container: LinearLayout, label: String) {
        val groupLabel = TextView(this).apply {
            text = label
            textSize = 10f
            setTextColor(KeyboardTheme.palette().encreAttenuee)
            setPadding(dpToPx(4), 0, dpToPx(2), 0)
            // Le centrage vertical se joue ici, sur la vue : la vue occupe toute la
            // hauteur de la rangée (MATCH_PARENT), donc le layout_gravity posé sur
            // ses LayoutParams n'a rien à décaler et l'étiquette restait collée en
            // haut de la rangée, à côté de puces dont le mot, lui, est centré.
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(groupLabel)
    }

    /**
     * Ajoute une puce de suggestion arrondie (fond plein) dans une rangée.
     * L'encre suit la couleur de fond, voir BilingualSuggestion.getTextColor().
     */
    private fun addSuggestionChip(container: LinearLayout, bilingualSuggestion: BilingualSuggestion) {
        val suggestionButton = Button(this).apply {
            text = bilingualSuggestion.word
            textSize = SUGGESTION_TEXT_SIZE_SP
            // Sans cela, la ligne réserve au-dessus et au-dessous du mot les
            // parties de la police qu'aucune lettre latine n'atteint, et comme
            // cette réserve est plus épaisse en haut qu'en bas, le mot centré
            // paraît posé trop bas dans sa puce (mesuré : 29 px de vide au-dessus
            // contre 14 en dessous).
            includeFontPadding = false
            setTextColor(bilingualSuggestion.getTextColor())

            val bgColor = bilingualSuggestion.getColor()
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(16).toFloat()
                setColor(bgColor)
            }

            val colorHex = String.format("#%06X", 0xFFFFFF and bgColor)
            Log.d(TAG, "🎨 Bouton '${bilingualSuggestion.word}': ${bilingualSuggestion.getLanguageName()} → fond $colorHex")

            setPadding(
                dpToPx(14),
                dpToPx(SUGGESTION_CHIP_PADDING_V_DP),
                dpToPx(14),
                dpToPx(SUGGESTION_CHIP_PADDING_V_DP)
            )
            // Après le padding, qui borne la place restante pour le mot.
            fitTextToChipHeight(this)
            // Le son de frappe est joué par KeyFeedback : sans cette ligne,
            // performClick() ajouterait son clic d'interface et la puce sonnerait deux fois.
            isSoundEffectsEnabled = false

            minWidth = dpToPx(SUGGESTION_CHIP_MIN_WIDTH_DP)
            minimumWidth = dpToPx(SUGGESTION_CHIP_MIN_WIDTH_DP)
            gravity = android.view.Gravity.CENTER

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                // La moitié de l'intervalle de chaque côté : deux puces voisines
                // portent chacune la sienne, et leur somme fait l'écart voulu.
                setMargins(suggestionChipHalfGapPx(), 0, suggestionChipHalfGapPx(), 0)
            }

            setOnClickListener { chip ->
                // v10.11.6 : la vibration manquait ici. Les touches en donnaient une,
                // pas les puces, alors que valider une proposition est l'action
                // principale de qui écrit à un ou deux appuis par mot : le geste le
                // plus important était le seul sans confirmation tactile.
                //
                // Au clic et non au toucher, contrairement aux touches : glisser hors
                // d'une puce avant de relâcher annule la sélection, et cette sortie de
                // secours ne doit pas vibrer comme si le mot avait été écrit. La
                // vibration signifie ici « le mot est écrit », et rien d'autre.
                // Vérifié sur émulateur en comptant les vibrations enregistrées par
                // dumpsys vibrator_manager : un geste annulé n'en produit aucune.
                //
                // Nuance mesurée le même jour : l'annulation fonctionne pour tout
                // glissement qui reste dans la fenêtre du clavier (puce voisine,
                // intervalle entre deux puces, rangées de touches), mais **pas** vers
                // le haut hors du clavier. Le doigt quitte alors la fenêtre, qui ne
                // reçoit plus d'événements de déplacement, donc la vue ne se sait
                // jamais quittée et le clic part au relâchement.
                KeyFeedback.onKeyPress(chip)

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

        // Réglages du retour de frappe relus à chaque prise de focus : le service
        // survit au passage dans l'écran de l'application, donc un interrupteur
        // changé là-bas doit s'appliquer dès le retour dans un champ de saisie.
        KeyFeedback.refresh(this)

        // Le thème se relit au même moment et pour la même raison, mais lui ne
        // suffit pas à se relire : les couleurs sont posées sur les vues à leur
        // construction, et InputMethodService garde la vue d'entrée en cache d'une
        // saisie à l'autre. Un changement de palette impose de la reconstruire,
        // qu'il vienne de l'écran de réglages ou du mode sombre du téléphone.
        //
        // La comparaison porte sur la palette avec laquelle la vue a été construite,
        // et non sur ce que renverrait un « refresh a-t-il changé quelque chose ? ».
        // L'écran de réglages partage ce processus : au moment du clic il a déjà
        // rafraîchi la palette globale, si bien qu'un tel booléen serait toujours
        // faux ici et que le clavier garderait ses anciennes couleurs.
        KeyboardTheme.refresh(this)
        if (paletteDeLaVue !== KeyboardTheme.palette()) {
            Log.d(TAG, "Thème changé : reconstruction de la vue d'entrée")
            setInputView(onCreateInputView())
        }

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
                getString(R.string.first_use_accent_tip),
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
        val container = luxRow ?: return
        lateinit var chip: Button
        chip = Button(this).apply {
            text = "📤 Envoyer un mot à un ami"
            textSize = 14f
            setTextColor(KeyboardColors.CHIP_TEXT_ON_RED)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(16).toFloat()
                setColor(Color.parseColor("#FF8A00"))
            }
            // Même réserve verticale et même garde-fou que les puces de suggestion :
            // cette puce partage leur rangée, elle doit tenir dans la même hauteur.
            includeFontPadding = false
            setPadding(
                dpToPx(14),
                dpToPx(SUGGESTION_CHIP_PADDING_V_DP),
                dpToPx(14),
                dpToPx(SUGGESTION_CHIP_PADDING_V_DP)
            )
            fitTextToChipHeight(this)
            // Le son de frappe est joué par KeyFeedback : sans cette ligne,
            // performClick() ajouterait son clic d'interface et la puce sonnerait deux fois.
            isSoundEffectsEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                // Même intervalle que les puces de suggestion : une seule règle pour
                // tout ce qui se touche dans cette rangée.
                setMargins(suggestionChipHalfGapPx(), 0, suggestionChipHalfGapPx(), 0)
            }
            setOnClickListener {
                // Même règle que les puces de suggestion : ce qui écrit du texte se
                // sent, et se sent au moment où le texte part.
                KeyFeedback.onKeyPress(chip)
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

        // Le champ de saisie change : la dictée en cours n'a plus de
        // destinataire. On rend aussi les ~165 Mo de tampons de whisper, que
        // rien ne justifie de garder pendant que l'utilisateur est ailleurs —
        // c'est précisément ce qui ferait du processus IME une cible du tueur
        // de mémoire.
        cancelDictation()
        sttSession?.releaseModel()
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

            // Dictée : coupe le micro, libère le contexte whisper et arrête son
            // thread de travail.
            sttSession?.shutdown()
            sttSession = null
            
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
            luxRow = null
            frenchRowScroll = null
            mainKeyboardView = null
            stopMicRing()
            micRing = null
            micButton = null
            dictationStatusView = null
            luxScrollView = null
            
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
     * 📏 Hauteur restant aux quatre rangées de touches une fois retiré tout ce que
     * le clavier consomme par ailleurs.
     *
     * `screenHeightDp` donne la hauteur d'écran barre de navigation déduite, mais
     * pas la barre d'état, sous laquelle la fenêtre IME commence : il faut la
     * retirer aussi, sans quoi le compte est trop généreux d'exactement sa hauteur
     * et la rangée du bas reste rognée (mesuré sur l'émulateur, 72 px de trop).
     * En portrait la place dépasse largement le besoin et la hauteur nominale des
     * touches est conservée ; en paysage (288 dp sur un 1080x2400 en 480 dpi) elle
     * est inférieure aux 332 dp de la mise en page nominale, et sans ce calcul la
     * rangée du bas est coupée en deux au lieu d'être simplement plus basse.
     *
     * Rien n'est réservé ici pour la barre de navigation : le système la déduit
     * déjà de la fenêtre de saisie, et le cas contraire est rattrapé après coup
     * par adjustForNavigationBarOverlap(), qui dispose alors d'autant de hauteur
     * supplémentaire puisque la fenêtre descend jusqu'au bas de l'écran.
     */
    private fun computeAvailableRowsHeight(): Int {
        val statusBarId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarPx = if (statusBarId > 0) {
            resources.getDimensionPixelSize(statusBarId)
        } else {
            dpToPx(24)
        }
        val windowHeightPx = dpToPx(resources.configuration.screenHeightDp) - statusBarPx
        val suggestionsPx = dpToPx(suggestionRowHeightDp()) * suggestionRowCount()
        val keyboardPaddingPx = dpToPx(KeyboardLayoutManager.verticalPaddingDp(this)) * 2
        // Marge d'arrondi : le calcul tombe sinon au pixel près sur le bas de la
        // fenêtre, où la moindre différence de mesure ronge à nouveau la dernière rangée
        val safetyPx = dpToPx(4)
        val available = windowHeightPx - suggestionsPx - keyboardPaddingPx - safetyPx
        Log.d(TAG, "📏 Hauteur fenêtre ${windowHeightPx}px (barre d'état ${statusBarPx}px déduite), " +
            "dont suggestions ${suggestionsPx}px, padding clavier ${keyboardPaddingPx}px " +
            "→ ${available}px pour les rangées")
        return available
    }

    /**
     * 📏 Décale le bas du clavier de ce que la barre de navigation lui masque
     * réellement, mesuré une fois la mise en page faite.
     *
     * L'ancienne version estimait ce décalage à partir du mode de navigation et
     * du dimen système `navigation_bar_height`, sans vérifier qu'il servait à
     * quelque chose. Or le système place déjà la fenêtre de saisie au-dessus de
     * la barre : la réserve était payée deux fois et laissait une bande vide
     * sous la dernière rangée (constaté en paysage sur émulateur Android 13).
     * Elle exposait aussi le clavier aux ROM qui renvoient une valeur aberrante
     * pour ce dimen (signalé sur un "P300", clavier poussé hors de l'écran).
     *
     * Comparer la position réelle du clavier à celle de la barre traite les deux
     * cas sans rien supposer : zéro quand le système a déjà fait le travail, le
     * recouvrement exact quand il ne l'a pas fait.
     */
    private fun adjustForNavigationBarOverlap(root: View, container: View) {
        val insets = window?.window?.decorView?.rootWindowInsets ?: return
        val navBarPx = if (android.os.Build.VERSION.SDK_INT >= 30) {
            insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetBottom
        }

        val displayHeightPx = if (android.os.Build.VERSION.SDK_INT >= 30) {
            (getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
                .maximumWindowMetrics.bounds.height()
        } else {
            resources.displayMetrics.heightPixels
        }

        val position = IntArray(2)
        root.getLocationOnScreen(position)
        val keyboardBottomPx = position[1] + root.height
        val overlapPx = keyboardBottomPx - (displayHeightPx - navBarPx)

        val paddingPx = if (overlapPx > 0) overlapPx else 0
        Log.d(TAG, "📏 Bas du clavier ${keyboardBottomPx}px, barre de navigation à " +
            "${displayHeightPx - navBarPx}px → recouvrement ${overlapPx}px, padding ${paddingPx}px")
        if (container.paddingBottom != paddingPx) {
            container.setPadding(0, 0, 0, paddingPx)
        }
    }

}
