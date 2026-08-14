package com.example.kreyolkeyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

/**
 * Gestionnaire des accents et caractères spéciaux pour le clavier créole
 * Gère les popups d'accents et la sélection de caractères diacritiques
 */
class AccentHandler(private val context: Context) {
    
    companion object {
        private const val TAG = "AccentHandler"
        private const val LONG_PRESS_DELAY = 500L
        private const val POPUP_ELEVATION_DP = 8f
        private const val ACCENT_BUTTON_SIZE_DP = 48
        private const val ACCENT_BUTTON_MARGIN_DP = 4
    }
    
    // État du mode majuscule
    var isCapitalMode: Boolean = false
    
    // Configuration des accents pour chaque touche de base.
    //
    // Reconstruite pour le luxembourgeois à partir d'un comptage des
    // diacritiques réellement présents dans luxemburgish_dict.json (6 342 mots),
    // en occurrences cumulées / nombre de mots porteurs :
    //   é 6347/933 · ë 2877/355 · ä 2129/403 · ü 97/25 · è 97/47 · à 37/3
    //   ô 29/3 · ê 22/7 · ö 12/4 · û 8/3 · ç 3/1 · â 2/1 · ï 1/1
    //
    // Chaque liste est classée par fréquence décroissante. é, ë et ä sont par
    // ailleurs des touches dédiées de la rangée 4, mais restent listées ici :
    // c'est la convention amont (v8.7.0), l'appui long servant de second chemin.
    //
    // Les digraphes créoles GEREC (ch, dj, ng, ny, gn, gy, tj) sont retirés :
    // ils notaient des phonèmes du kréyòl. Le luxembourgeois n'a pas d'équivalent
    // qui justifierait une touche — « ch » et « sch » s'y écrivent avec des
    // lettres toutes déjà présentes au clavier.
    //
    // Le trait d'union rejoint l'appui long sur "." : il n'apparaît que dans
    // 2,8 % des mots luxembourgeois (fréquence cumulée 377), très loin des
    // 21,7 % du créole qui lui valaient une touche dédiée en amont.
    private val accentMap = mapOf(
        "a" to listOf("ä", "à", "â"),
        "e" to listOf("é", "ë", "è", "ê"),
        "u" to listOf("ü", "û", "ù"),
        "o" to listOf("ô", "ö"),
        "i" to listOf("ï", "î"),
        "c" to listOf("ç"),
        // La ponctuation reste accessible sans aller-retour vers le mode 123
        // (convention amont v8.3.0). L'apostrophe vit sous "," faute de touche
        // dédiée depuis que la rangée 4 accueille la touche emoji.
        "," to listOf(";", ":", "'"),
        "." to listOf("-", "!", "?", "…")
    )

    // Ordre d'affichage des aperçus en coin, quand il doit différer du popup.
    // "a" et "e" ont leur diacritique le plus fréquent (ä, é) déjà visible en
    // rangée 4 : l'aperçu met donc en avant ceux qui n'ont aucune autre porte
    // d'entrée, plutôt que de répéter une touche que l'utilisateur voit déjà.
    private val cornerHintOverrides = mapOf(
        "a" to listOf("à", "â"),
        "e" to listOf("è", "ê")
    )

    // Touches dont les aperçus en coin s'affichent à gauche plutôt qu'à droite
    // (toute touche absente de cet ensemble garde le coin droit par défaut).
    // Vide en luxembourgeois : l'exception amont visait le "o" de la rangée 1,
    // que la touche dédiée "ò" jouxtait en créole. La rangée 1 luxembourgeoise
    // n'a pas de touche accentuée, plus rien ne se chevauche.
    private val cornerHintOnStartSide = emptySet<String>()

    // Tons de peau pour le panneau emoji exhaustif (v10.1.0), chargés depuis
    // emoji_data.json au démarrage du clavier (EmojiData.skinTones) : clé =
    // emoji neutre/jaune affiché dans la grille, valeur = les 5 tons de peau
    // du plus clair au plus foncé, dans cet ordre. Contrairement à
    // accentMap (fixe, connu à la compilation), c'est une donnée chargée à
    // l'exécution, d'où un champ mutable séparé plutôt qu'une entrée de plus
    // dans accentMap.
    private var emojiSkinTones: Map<String, List<String>> = emptyMap()

    fun loadEmojiSkinTones(skinTones: Map<String, List<String>>) {
        emojiSkinTones = skinTones
    }

    // ├ëtat actuel
    private var currentAccentPopup: PopupWindow? = null
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var isLongPressTriggered = false
    private var currentBaseCharacter: String? = null
    
    // Callbacks
    interface AccentSelectionListener {
        fun onAccentSelected(accent: String, baseCharacter: String)
        fun onLongPressStarted(baseKey: String)
        fun onLongPressCancelled()
    }
    
    private var accentListener: AccentSelectionListener? = null
    
    fun setAccentSelectionListener(listener: AccentSelectionListener) {
        this.accentListener = listener
    }
    
    /**
     * V├®rifie si une touche a des accents disponibles
     */
    fun hasAccents(key: String): Boolean {
        return accentMap.containsKey(key.lowercase()) || emojiSkinTones.containsKey(key)
    }
    
    /**
     * Démarre le timer de pression longue pour une touche
     */
    fun startLongPressTimer(key: String, anchorButton: View) {
        if (!hasAccents(key)) return
        
        cancelLongPress()
        currentBaseCharacter = key  // Stocker le caractère de base
        
        longPressRunnable = Runnable {
            isLongPressTriggered = true
            showAccentPopup(key, anchorButton)
            accentListener?.onLongPressStarted(key)
        }
        
        longPressHandler.postDelayed(longPressRunnable!!, LONG_PRESS_DELAY)
    }
    
    /**
     * Annule la pression longue en cours
     */
    fun cancelLongPress() {
        longPressRunnable?.let {
            longPressHandler.removeCallbacks(it)
            longPressRunnable = null
        }
        
        if (isLongPressTriggered) {
            accentListener?.onLongPressCancelled()
            isLongPressTriggered = false
        }
    }
    
    /**
     * V├®rifie si une pression longue est en cours
     */
    fun isLongPressActive(): Boolean {
        return isLongPressTriggered
    }
    
    /**
     * Affiche la popup d'accents pour une touche de base
     */
    fun showAccentPopup(baseKey: String, anchorButton: View) {
        val accents = accentMap[baseKey.lowercase()] ?: emojiSkinTones[baseKey] ?: return
        
        // Fermer la popup existante si elle existe
        dismissAccentPopup()
        
        try {
            // Créer le layout de la popup
            val popupLayout = createAccentPopupLayout(accents, baseKey)
            
            // Créer la popup window
            currentAccentPopup = PopupWindow(
                popupLayout,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                false  // ✅ CORRECTION: focusable=false pour éviter fermeture IME
            ).apply {
                // Style de la popup
                setBackgroundDrawable(createPopupBackground())
                elevation = dpToPx(POPUP_ELEVATION_DP).toFloat()
                
                // Configuration pour IME
                isTouchable = true  // ✅ Permet interaction avec popup
                isOutsideTouchable = true  // ✅ Ferme popup si clic extérieur
                
                // Animation d'entrée/sortie
                animationStyle = android.R.style.Animation_Dialog
                
                // Affichage au-dessus de la touche
                showAsDropDown(
                    anchorButton,
                    calculatePopupX(anchorButton, popupLayout),
                    -anchorButton.height - dpToPx(50)
                )
            }
            
            Log.d(TAG, "Popup d'accents affichée pour '$baseKey' avec ${accents.size} options")
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'affichage de la popup: ${e.message}", e)
        }
    }
    
    /**
     * Ferme la popup d'accents actuelle
     */
    fun dismissAccentPopup() {
        currentAccentPopup?.let { popup ->
            try {
                if (popup.isShowing) {
                    popup.dismiss()
                } else {
                    // Popup d├®j├á ferm├®
                }
            } catch (e: Exception) {
                Log.w(TAG, "Erreur lors de la fermeture de la popup: ${e.message}")
            }
        }
        currentAccentPopup = null
        isLongPressTriggered = false
    }
    
    /**
     * Cr├®e le layout de la popup d'accents
     */
    private fun createAccentPopupLayout(accents: List<String>, baseKey: String): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                dpToPx(8), dpToPx(8),
                dpToPx(8), dpToPx(8)
            )
            
            // Ajouter d'abord la touche de base
            addView(createAccentButton(baseKey, isBase = true))
            
            // Ajouter les variantes d'accents
            accents.forEach { accent ->
                addView(createAccentButton(accent, isBase = false))
            }
        }
    }
    
    /**
     * Crée un bouton d'accent individuel
     */
    private fun createAccentButton(accent: String, isBase: Boolean): Button {
        return Button(context).apply {
            // Appliquer la majuscule si le mode est actif
            text = if (isCapitalMode) accent.uppercase() else accent
            textSize = 18f
            setTextColor(if (isBase) Color.parseColor("#666666") else Color.parseColor("#333333"))
            
            // Taille et style
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(ACCENT_BUTTON_SIZE_DP),
                dpToPx(ACCENT_BUTTON_SIZE_DP)
            ).apply {
                setMargins(
                    dpToPx(ACCENT_BUTTON_MARGIN_DP), 0,
                    dpToPx(ACCENT_BUTTON_MARGIN_DP), 0
                )
            }
            
            // Style visuel
            background = createAccentButtonBackground(isBase)
            
            // ├ëv├®nement de clic
            setOnClickListener {
                handleAccentSelection(accent)
            }
            
            // Animation tactile l├®g├¿re
            setOnTouchListener { view, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(50).start()
                        false
                    }
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(50).start()
                        false
                    }
                    else -> false
                }
            }
        }
    }
    
    /**
     * G├¿re la s├®lection d'un accent
     */
    private fun handleAccentSelection(accent: String) {
        val baseChar = currentBaseCharacter ?: ""
        // Appliquer la majuscule si le mode est actif
        val finalAccent = if (isCapitalMode) accent.uppercase() else accent
        accentListener?.onAccentSelected(finalAccent, baseChar)
        dismissAccentPopup()
        currentBaseCharacter = null  // Nettoyer après usage
        
        Log.d(TAG, "Accent sélectionné: '$finalAccent' pour base: '$baseChar'")
    }
    
    /**
     * Cr├®e l'arri├¿re-plan de la popup
     */
    private fun createPopupBackground(): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dpToPx(12).toFloat()
            setColors(intArrayOf(
                Color.parseColor("#FFFFFF"),
                Color.parseColor("#F8F8F8")
            ))
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            setStroke(dpToPx(1), Color.parseColor("#E0E0E0"))
        }
    }
    
    /**
     * Cr├®e l'arri├¿re-plan d'un bouton d'accent
     */
    private fun createAccentButtonBackground(isBase: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dpToPx(8).toFloat()
            
            if (isBase) {
                // Bouton de base (caract├¿re original) - style att├®nu├®
                setColors(intArrayOf(
                    Color.parseColor("#F5F5F5"),
                    Color.parseColor("#E8E8E8")
                ))
                setStroke(dpToPx(1), Color.parseColor("#D0D0D0"))
            } else {
                // Boutons d'accents - style actif
                setColors(intArrayOf(
                    Color.parseColor("#FFFFFF"),
                    Color.parseColor("#F0F0F0")
                ))
                setStroke(dpToPx(1), Color.parseColor("#C0C0C0"))
            }
            
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
        }
    }
    
    /**
     * Calcule la position X de la popup pour qu'elle soit centrée
     */
    private fun calculatePopupX(anchorButton: View, popupLayout: LinearLayout): Int {
        // Mesurer la largeur approximative de la popup
        val buttonWidth = dpToPx(ACCENT_BUTTON_SIZE_DP + ACCENT_BUTTON_MARGIN_DP * 2)
        
        // Récupérer la clé de base depuis le tag (ImageButton) ou le texte (Button)
        val baseKey = when (anchorButton) {
            is Button -> anchorButton.text.toString().lowercase()
            is android.widget.ImageButton -> (anchorButton.tag as? String)?.lowercase() ?: ""
            else -> ""
        }
        
        val accentCount = accentMap[baseKey]?.size ?: 0
        val totalButtons = accentCount + 1 // +1 pour la touche de base
        val popupWidth = totalButtons * buttonWidth + dpToPx(16) // +padding
        
        // Centrer par rapport au bouton ancre
        val anchorWidth = anchorButton.width
        return (anchorWidth - popupWidth) / 2
    }
    
    /**
     * Obtient tous les accents disponibles pour une touche
     */
    fun getAccentsForKey(key: String): List<String> {
        return accentMap[key.lowercase()] ?: emojiSkinTones[key] ?: emptyList()
    }

    /**
     * Obtient les accents à afficher en aperçu dans les coins de la touche,
     * dans l'ordre haut-droit puis bas-droit (peut différer de l'ordre du
     * popup d'appui long, voir cornerHintOverrides)
     */
    fun getCornerHintsForKey(key: String): List<String> {
        return cornerHintOverrides[key.lowercase()] ?: getAccentsForKey(key)
    }

    /**
     * Indique si les aperçus en coin de cette touche doivent s'afficher côté
     * gauche (haut-gauche/bas-gauche) plutôt que côté droit (par défaut)
     */
    fun isCornerHintOnStartSide(key: String): Boolean {
        return key.lowercase() in cornerHintOnStartSide
    }

    /**
     * Ajoute un nouvel accent ├á une touche existante
     */
    fun addAccentToKey(baseKey: String, accent: String) {
        val key = baseKey.lowercase()
        val currentAccents = accentMap[key]?.toMutableList() ?: mutableListOf()
        
        if (accent !in currentAccents) {
            currentAccents.add(accent)
            // Note: Pour une impl├®mentation compl├¿te, il faudrait mettre ├á jour accentMap
            // qui est actuellement immutable
        }
    }
    
    /**
     * Nettoie les ressources
     */
    fun cleanup() {
        dismissAccentPopup()
        cancelLongPress()
        accentListener = null
    }
    
    // M├®thodes utilitaires
    
    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
    
    private fun dpToPx(dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
