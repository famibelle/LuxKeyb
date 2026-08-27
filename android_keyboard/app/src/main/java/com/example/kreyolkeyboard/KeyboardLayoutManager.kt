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
        //
        // Ce qui borne ce rapport est la largeur, pas la hauteur : les jambages
        // passent encore à 0,72 même en paysage, mais le "m" y occupe 78 % de la
        // largeur de touche (mesuré sur un Galaxy A15) et vient toucher les
        // aperçus d'appui long des coins, plus encore sur un écran étroit. À
        // 0,62 il en occupe 66 %, sans collision.
        private const val KEY_TEXT_HEIGHT_RATIO = 0.62f
        // Les libellés de plusieurs caractères ("123", "ABC", "LuxKeyb™") sont
        // contraints par la largeur de la touche, pas par sa hauteur : les
        // agrandir les ferait tronquer. Ce rapport reprend leur taille d'avant
        // (16 sp × 0,75) rapportée aux 48 dp de la hauteur nominale.
        private const val WIDE_LABEL_TEXT_RATIO = 0.28f
        // La signature LuxKeyb™ de la barre d'espace n'est pas une commande :
        // elle ne s'appuie pas, elle ne se lit qu'une fois. Elle partageait la
        // taille des libellés de mode ("123", "ABC"), qui eux se visent, ce qui
        // la posait au même niveau de présence que le reste du clavier.
        private const val SPACE_LABEL_TEXT_RATIO = 0.22f
        // La hauteur ne peut pas commander seule : une touche est plus haute que
        // large, et un glyphe large finit par déborder puis se faire remplacer
        // par une ellipse. Ces rapports plafonnent la police à une part de la
        // largeur de touche. 0,90 pour un libellé latin, dont la lettre la plus
        // large ("m") n'occupe que 0,9 em, ce qui ne mord qu'au-delà des écrans
        // étroits ; 0,77 pour l'emoji de la touche 😀, dessiné dans un carré
        // d'environ 1,2 em, qui réclamait plus que sa touche dès la 10.12.3 et
        // s'affichait "…" (signalé le 19/08/2026).
        private const val LABEL_WIDTH_RATIO = 0.90f
        private const val EMOJI_WIDTH_RATIO = 0.77f
        // Padding latéral du bloc de touches, retiré de la largeur d'écran pour
        // savoir ce qui revient réellement à chaque touche.
        private const val KEYBOARD_SIDE_PADDING_DP = 8
        // Aperçus d'appui long dans les coins des touches. Ils valaient 8 sp
        // fixes : 24 px sur un HONOR 200, contre 49 px de hauteur pour la
        // lettre de la même touche depuis que celle-ci suit sa hauteur. Ils
        // suivent désormais la touche eux aussi, en restant assez discrets pour
        // ne pas venir toucher la lettre centrale.
        private const val HINT_TEXT_HEIGHT_RATIO = 0.21f
        private const val SHADOW_RADIUS = 4f
        private const val TAG = "KeyboardLayoutManager"

        // Les couleurs vivent dans KeyboardTheme, qui en tient deux jeux : le
        // drapeau luxembourgeois (rouge Pantone 032 et bleu ciel Pantone 299) est
        // commun aux deux thèmes, seul le blanc des lettres bascule en anthracite.

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
     * Largeur que la rangée accorde à une touche, marges déduites. Pendant du
     * calcul de hauteur ci-dessus : la taille du libellé se plafonne dessus,
     * sans quoi un glyphe large déborde d'une touche étroite.
     */
    private fun keyWidthPx(weight: Float, totalWeight: Float): Int {
        val disponible = context.resources.displayMetrics.widthPixels -
            dpToPx(KEYBOARD_SIDE_PADDING_DP) * 2
        val part = (disponible * weight / totalWeight).toInt()
        return (part - dpToPx(BUTTON_MARGIN_DP) * 2).coerceAtLeast(1)
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
                dpToPx(KEYBOARD_SIDE_PADDING_DP), verticalPaddingPx,
                dpToPx(KEYBOARD_SIDE_PADDING_DP), verticalPaddingPx
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
        // v10.12.15 : "#" ajouté. C'est la seule page de symboles du clavier (il
        // n'y a pas de seconde page comme le "=\<" de Gboard), donc son absence
        // signifiait qu'aucun hashtag ne pouvait être écrit sans changer de
        // clavier. La rangée 3 n'avait que 9 touches contre 10 aux rangées 1 et 2 :
        // la dixième aligne simplement leurs largeurs, sans rétrécir aucune touche
        // en deçà de ce qui existe déjà ailleurs. Placé en 9e colonne, sous "@" de
        // la rangée du dessus, l'autre caractère des identifiants et des réseaux.
        val row3 = arrayOf("=", ".", ",", "?", "!", "'", "+", "*", "#", "⌫")
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
            // Un LinearLayout horizontal aligne par défaut ses enfants sur la
            // ligne de base de leur texte : une touche au libellé plus petit
            // que ses voisines se retrouve poussée vers le bas pour que les
            // deux lignes de base coïncident. C'est ce qui décalait « 123 » et
            // « ABC », seuls libellés à taille réduite de leur rangée. Les
            // touches doivent s'aligner sur leur cadre, pas sur leur texte.
            isBaselineAligned = false
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
                    // Entrée : 8 dp jusqu'en 10.12.8. Sa touche est la plus
                    // étroite des trois porteuses d'icône (poids 1 contre 1,25
                    // pour shift et retour arrière) et sa flèche la plus large :
                    // c'est donc la largeur qui borne sa mise à l'échelle, et le
                    // padding y coûte deux fois, en largeur comme en hauteur.
                    // Réduit à 4 dp, il laisse la flèche remplir la touche comme
                    // ses voisines remplissent les leurs.
                    "⏎" -> 4f / BUTTON_HEIGHT_DP
                    "⌫" -> 10f / BUTTON_HEIGHT_DP // Padding moyen pour Backspace
                    // Shift : 12 dp jusqu'en 10.12.6, ce qui le faisait paraître
                    // plus petit que ses voisines une fois les lettres agrandies
                    // (36 px de flèche contre 51 pour la corbeille et 54 pour un
                    // « b », mesurés sur un Galaxy A15). Deux causes cumulées :
                    // le plus fort padding des trois icônes, et un tracé qui
                    // n'occupe que 62 % de la hauteur de son cadre là où celui
                    // de la corbeille en remplit près de 80. Le padding
                    // compense la différence de tracé.
                    else -> 6f / BUTTON_HEIGHT_DP
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
                // touche (" " → "LuxKeyb™", "EMOJI" → "😀", "ABC"). Voir le
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
                // touche. Les libellés longs (LuxKeyb™ sur l'espace, les modes
                // "123" et "ABC") gardent leur taille réduite, sans quoi ils ne
                // tiennent plus sur une seule ligne dans une touche étroite.
                val labelRatio = when (key) {
                    " " -> SPACE_LABEL_TEXT_RATIO
                    "123", "ABC" -> WIDE_LABEL_TEXT_RATIO
                    else -> KEY_TEXT_HEIGHT_RATIO
                }
                val widthRatio = if (key == "EMOJI") EMOJI_WIDTH_RATIO else LABEL_WIDTH_RATIO
                val taillePx = minOf(
                    keyHeightPx() * labelRatio,
                    keyWidthPx(getKeyWeight(key), totalWeight) * widthRatio
                )
                setTextSize(TypedValue.COMPLEX_UNIT_PX, taillePx)
                // Même raison que pour les puces de suggestion : la réserve de
                // police au-dessus de la lettre est plus épaisse que celle du
                // dessous, ce qui pose le caractère trop bas dans sa touche.
                includeFontPadding = false
                // Graisse normale : à la taille de police calculée ci-dessus, le
                // gras d'origine couvrait un tiers de pixels sombres en plus
                // (6,5 % contre 4,7 % sur les trois rangées de lettres, mesuré
                // sur émulateur 440 dpi) et refermait les contreformes du g et
                // du m. Les touches sont déjà séparées par leur fond et leur
                // ombre : la lettre n'a pas besoin d'être épaissie pour se viser.
                setTypeface(typeface, Typeface.NORMAL)
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
        
        // Application du style des touches
        applyKeyStyleToView(button, key)
        
        // Ajouter le bouton à la liste de suivi
        keyboardButtons.add(button)
        
        // Configuration des événements tactiles
        setupButtonInteractions(button, key)

        // Aperçu des options d'appui long dans les coins de la touche (v8.3.0)
        val hints = accentHandler?.takeIf { it.hasAccents(key) }?.getCornerHintsForKey(key)
        if (!hints.isNullOrEmpty()) {
            return wrapWithLongPressHints(button, hints, key)
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
            setTextSize(TypedValue.COMPLEX_UNIT_PX, keyHeightPx() * HINT_TEXT_HEIGHT_RATIO * 1.2f)
            // Reste à 0xCC, plus franc que la signature depuis la 10.12.9 : celle-ci
            // ne dit rien à faire, l'indice si.
            setTextColor(Color.parseColor("#CCFFFFFF"))
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
     * bas du côté droit, un aperçu des deux premières options d'appui long. La
     * touche d'origine garde exactement sa zone tactile, son style et son
     * ancrage pour la popup d'accents (le FrameLayout se contente de prendre sa
     * place dans la rangée) ; keyboardButtons ne référence jamais ce
     * FrameLayout, seulement la touche brute qu'il contient.
     */
    private fun wrapWithLongPressHints(inner: View, hints: List<String>, key: String): FrameLayout {
        val outerParams = inner.layoutParams
        inner.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        val hintColor = hintColorFor(key)

        return FrameLayout(context).apply {
            layoutParams = outerParams
            addView(inner)
            addView(createHintLabel(hints[0], Gravity.TOP or Gravity.END, hintColor))
            if (hints.size > 1) {
                addView(createHintLabel(hints[1], Gravity.BOTTOM or Gravity.END, hintColor))
            }
        }
    }

    /**
     * Couleur d'un indice de coin. Upstream la calcule par contraste WCAG sur
     * le dégradé de la touche ; notre palette n'a que trois fonds (rouge, bleu,
     * blanc) dont hintInk() documente déjà l'encre mesurée, alors on la relit
     * plutôt que de la recalculer.
     */
    private fun hintColorFor(key: String): Int = hintInk(key)

    private fun createHintLabel(hintText: String, gravity: Int, textColor: Int): TextView {
        return TextView(context).apply {
            text = hintText
            setTextSize(TypedValue.COMPLEX_UNIT_PX, keyHeightPx() * HINT_TEXT_HEIGHT_RATIO)
            setTextColor(textColor)
            isClickable = false
            isFocusable = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                gravity
            ).apply {
                setMargins(0, dpToPx(2), dpToPx(3), dpToPx(2))
            }
        }
    }
    
    /**
     * Applique le style visuel des touches (supporte Button et ImageButton)
     */
    private fun applyKeyStyleToView(view: View, key: String) {
        val drawable = GradientDrawable().apply {
            cornerRadius = dpToPx(CORNER_RADIUS_DP.toInt()).toFloat()
            setColor(keyBackground(key))
            // Le blanc du drapeau étant aussi celui des touches de lettres, il
            // leur faut un contour pour rester distinctes du fond du clavier.
            setStroke(dpToPx(1), KeyboardTheme.palette().bordure)
        }

        view.background = drawable

        val encre = keyForeground(key)

        if (view is Button) {
            view.setTextColor(encre)

            // Ombre portée pour l'effet de profondeur.
            // setShadowLayer() sous rendu accéléré matériellement est une source connue
            // de texte invisible sur certains GPU/drivers (rapporté sur Honor 200/SDK 36) ;
            // LAYER_TYPE_SOFTWARE force le rendu logiciel de cette vue pour l'éviter.
            // L'espace en est exempté : son ombre détourait la signature et lui
            // rendait la présence que sa graisse normale vient de lui retirer.
            if (key != " ") {
                view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                // Ombre claire sur les touches colorées, sombre sur les autres :
                // une ombre noire sous du texte blanc le rend sale. Le rouge ne
                // bougeant pas d'un thème à l'autre, cette distinction non plus.
                val p = KeyboardTheme.palette()
                val ombre = if (keyBackground(key) == p.accent) p.ombreSurAccent else p.ombreSurTouche
                view.setShadowLayer(SHADOW_RADIUS, 0f, dpToPx(1).toFloat(), ombre)
            }
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
    private fun keyBackground(key: String): Int {
        val p = KeyboardTheme.palette()
        return when (key) {
            "⏎", "123", "ABC", "EMOJI" -> p.accent
            " ", ",", ".", "'" -> p.secondaire
            "⇧" -> if (isCapsLock || isCapitalMode) p.toucheActive else p.touche
            else -> p.touche
        }
    }

    /**
     * Encre d'une touche, choisie sur le contraste réellement mesuré.
     *
     * Blanc sur le rouge du drapeau donne 4,2:1, suffisant pour les glyphes
     * larges et gras des touches. Blanc sur le bleu ciel ne donnerait que
     * 2,9:1 — illisible ; ce bleu porte donc une encre sombre (5,9:1).
     *
     * Le choix se fait sur le **fond** et non sur la touche, ce qui est exactement
     * ce qu'il faut pour un thème : le rouge et le bleu ne bougeant pas d'un thème
     * à l'autre, leurs encres non plus, et seul le cas des touches de lettres suit
     * la palette. Cette fonction n'a pas eu à changer de forme.
     */
    private fun keyForeground(key: String): Int {
        val p = KeyboardTheme.palette()
        return when (keyBackground(key)) {
            p.accent -> p.encreSurAccent
            p.secondaire -> p.encreSurSecondaire
            p.toucheActive -> p.encreAttenuee
            else -> p.encre
        }
    }

    /** Encre des aperçus de coin : même famille que le glyphe, en plus discret. */
    private fun hintInk(key: String): Int {
        val p = KeyboardTheme.palette()
        return if (keyBackground(key) == p.accent) p.apercuSurAccent else p.apercu
    }

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
                    // keyBackground() prévoit toucheActive pour la majuscule
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
