package com.example.kreyolkeyboard

import androidx.annotation.StringRes
import android.Manifest
import android.content.ClipData
import android.content.Context
import android.animation.ValueAnimator
import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import com.example.kreyolkeyboard.gamification.LuxLevels
import com.example.kreyolkeyboard.gamification.LevelUpNotifier
import android.content.Intent
import android.content.SharedPreferences
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.CountDownTimer
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlin.random.Random
import com.example.kreyolkeyboard.wordsearch.WordSearchGenerator
import com.example.kreyolkeyboard.wordsearch.WordSearchPuzzle
import com.example.kreyolkeyboard.wordsearch.WordSearchWord
import com.example.kreyolkeyboard.wordsearch.WordSearchDifficulty
import com.example.kreyolkeyboard.wordsearch.WordSearchThemes
import com.example.kreyolkeyboard.wordsearch.WordSearchGridAdapter
import com.example.kreyolkeyboard.wuertriet.WuertrietData
import com.example.kreyolkeyboard.wuertriet.WuertrietRow
import com.example.kreyolkeyboard.wuertriet.LetterState
import com.example.kreyolkeyboard.wuertriet.color
import com.example.kreyolkeyboard.cloze.ClozeData
import com.example.kreyolkeyboard.cloze.ClozeDifficulty
import com.example.kreyolkeyboard.cloze.ClozeQuestion
import com.example.kreyolkeyboard.crossword.CrosswordData
import com.example.kreyolkeyboard.crossword.CrosswordDifficulty
import com.example.kreyolkeyboard.crossword.CrosswordGrid
import com.example.kreyolkeyboard.crossword.CrosswordSession
import com.example.kreyolkeyboard.chassecroise.ChasseCroiseData
import com.example.kreyolkeyboard.chassecroise.ChasseCroiseSession
import com.example.kreyolkeyboard.carnet.Carnet
import com.example.kreyolkeyboard.carnet.Booster
import com.example.kreyolkeyboard.carnet.CarteAccueil
import com.example.kreyolkeyboard.carnet.CarteCarnet
import com.example.kreyolkeyboard.carnet.CarnetFragment
import com.example.kreyolkeyboard.carnet.BoiteFragment
import com.example.kreyolkeyboard.carnet.JeuCarte
import com.example.kreyolkeyboard.carnet.Pochette
import com.example.kreyolkeyboard.zuelen.ZuelenData
import com.example.kreyolkeyboard.zuelen.ZuelenDifficulty
import com.example.kreyolkeyboard.zuelen.ZuelenQuestion
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import com.google.android.play.core.review.ReviewManagerFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.core.content.FileProvider
import java.io.FileOutputStream
import android.widget.Toast
import android.widget.GridView
import android.widget.ScrollView

class SettingsActivity : AppCompatActivity() {
    /**
     * Le jeu ouvert dans l'onglet Spiller (rang dans `GamesFragment.jeux`), ou -1.
     *
     * Tenu par l'activité et non par le fragment : c'est elle qui sauvegarde
     * son état à la rotation, et le fragment de l'onglet est recréé à neuf par
     * le pager. Sans cela, tourner le téléphone ramenait au choix des jeux. La
     * partie elle-même repart de zéro : chaque jeu calcule sa grille d'après
     * l'écran à l'ouverture, et une grille de portrait ne tient pas en paysage.
     */
    internal var jeuOuvert = -1

    /**
     * Demandes faites depuis l'accueil « Aujourd'hui » à un autre onglet : le
     * jeu à ouvrir dans Spiller (par son nom), le mot à chercher dans le
     * Wierderbuch. Consommées par l'onglet visé à son onResume(), qui est
     * appelé quand il devient l'onglet courant, qu'il existe déjà ou non.
     */
    internal var jeuDemande: String? = null
    /** Avec [jeuDemande] = la Boîte de Leitner : lancer la séance dès l'ouverture. */
    internal var revisionDemandee = false
    internal var rechercheDemandee: String? = null

    private var currentTab = 0 // 0 = démarrage, 1 = spiller, 2 = wierderbuch, 3 = mäi lëtzebuergesch (stats)
    private lateinit var viewPager: ViewPager2
    private lateinit var tabBar: LinearLayout

    /** Un écran qui sait recevoir les touches d'un clavier physique. */
    interface JeuAuClavier {
        fun surToucheClavier(event: android.view.KeyEvent): Boolean
    }

    /** Le jeu à l'écran qui sait recevoir les touches d'un clavier physique. */
    var jeuAuClavier: JeuAuClavier? = null

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        // Un champ de texte qui a le focus garde ses touches ; sinon, le jeu
        // ouvert les prend avant la navigation.
        if (currentFocus !is EditText && jeuAuClavier?.surToucheClavier(event) == true) return true
        return super.dispatchKeyEvent(event)
    }
    private lateinit var bottomInstallBanner: LinearLayout

    /**
     * Étape dépliée dans la carte « Configuration rapide » : null laisse
     * l'ouverture automatique décider (l'étape qui reste à faire), -1 signifie
     * que l'utilisateur les a toutes repliées.
     */
    private var etapeConfigOuverte: Int? = null

    /** Les 3 lignes restent-elles visibles une fois la configuration terminée ? */
    private var detailsConfigDeplies = false

    /**
     * Présence de la pastille de niveau dans la barre d'onglets telle qu'elle
     * est actuellement dessinée, à distinguer de l'état enregistré dans les
     * préférences. Le service de saisie pose la pastille pendant que
     * l'application est en arrière-plan : sans ce repère, [onResume] ne saurait
     * pas si la barre affichée est à jour.
     */
    private var levelBadgeDrawn = false
    
    // 🔧 FIX CRITIQUE: Scope lié au lifecycle de l'activité
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    companion object {
        /**
         * Les écrans de texte, ramenés à une colonne centrée sur tablette (voir
         * [LargeurLecture]). Les jeux à grille n'y sont pas : ils ont leurs deux
         * colonnes ([DeuxColonnes]). Le Wierderbuch non plus : il montre sa
         * liste et sa fiche côte à côte.
         */
        private val ECRANS_DE_LECTURE = setOf(
            OnboardingFragment::class.java,
            StatsFragment::class.java,
            AboutFragment::class.java,
            GuideFragment::class.java,
            com.example.kreyolkeyboard.actualites.ActualitesFragment::class.java,
            WordScrambleFragment::class.java,
            WuertrietFragment::class.java,
            ClozeFragment::class.java,
            ZuelenFragment::class.java,
        )

        const val PRIVACY_POLICY_URL = "https://famibelle.github.io/LuxKeyb/privacy/privacy-policy.html"

        /**
         * Pause dans la frappe avant d'ouvrir la pochette « Moien ». À 2,5 s,
         * le temps de chercher la lettre suivante suffisait à l'ouvrir, et les
         * touches visées tombaient sur la carte.
         */
        private const val DELAI_POCHETTE_MS = 4000L

        /** Partage de l'activation à proposer à la prochaine ouverture. */
        private const val PREF_PARTAGE_EN_ATTENTE = "partage_activation_en_attente"
        private const val REQ_REGLAGES_CLAVIER = 7301

        /** Onglet à ouvrir au démarrage, quand l'activité est lancée depuis le clavier. */
        const val EXTRA_OPEN_TAB = "open_tab"
        const val TAB_STATS = 3

        /** Largeur du rail d'onglets d'une tablette couchée. */
        const val RAIL_LARGEUR_DP = 104
        private const val TAB_SPILLER = 1
        private const val TAB_WIERDERBUCH = 2

        /**
         * Les actualités de l'INLL (33.2.x, test fermé seulement) restent dans
         * le code mais sans lien : les conditions de l'INLL interdisent de
         * reprendre ses contenus sans autorisation écrite. À rallumer quand
         * elle sera obtenue.
         */
        private const val ACTUALITES_INLL = false

        private const val ACCUEIL_PREFS = "lux_accueil_prefs"
        private const val PREF_DERNIER_JEU_NOM = "dernier_jeu_nom"
        private const val PREF_CORRECTEUR_ACCUEIL_MASQUE = "correcteur_accueil_masque"
        private const val PREF_DERNIER_JEU_EMOJI = "dernier_jeu_emoji"
        /** Nom de la Boîte de Leitner dans GamesFragment.jeux : le bouton « Réviser » l'ouvre. */
        private const val JEU_LEITNER = "Boîte de Leitner"

        /** Code de la demande de permission POST_NOTIFICATIONS (pastille de niveau). */
        private const val REQUEST_NOTIFICATIONS = 4201

        /**
         * Posé par le service de saisie au franchissement d'un palier, effacé
         * quand l'utilisateur affiche enfin ses statistiques. Sert la pastille
         * de la barre d'onglets. Écrit aussi dans KreyolInputMethodServiceRefactored.
         */
        const val PREF_LEVEL_BADGE_PENDING = "level_badge_pending"

        /**
         * Mot-dièse commun à tous les partages sortants, pour que les messages
         * envoyés depuis l'application se retrouvent entre eux sur les réseaux.
         *
         * Toujours placé en dernier, après une ligne vide : collé juste derrière
         * le lien Play Store, certains clients l'aspireraient dans l'URL.
         * Le message inséré par la puce du clavier fait exception, il tient sur
         * une seule ligne au milieu de ce que l'utilisateur écrit.
         */
        const val SHARE_HASHTAG = "#LëtzebuergeschClavier"

        // Les astuces de la carte « Astuce de la semaine » vivent dans les
        // ressources (`astuces_semaine`), une liste par langue. Chaque entrée
        // décrit une fonctionnalité réellement présente dans l'application :
        // ASTUCES.md donne, pour chacune, le code qui la justifie ; toute
        // astuce ajoutée doit y être sourcée, et toute astuce dont la source
        // disparaît doit être retirée des deux côtés, dans toutes les langues.
        // Les thèmes sont volontairement entrelacés : l'index avance d'un cran
        // par semaine, donc deux astuces voisines se suivent à l'écran.
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Sur tablette, les écrans de texte sont ramenés à une colonne de
        // lecture centrée. Une seule surveillance plutôt qu'un appel dans
        // chaque écran : elle couvre aussi le guide et les actualités, ouverts
        // dans une feuille.
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: androidx.fragment.app.FragmentManager,
                    f: Fragment,
                    v: View,
                    savedInstanceState: Bundle?
                ) {
                    if (f.javaClass in ECRANS_DE_LECTURE) LargeurLecture.borner(v)
                }
            },
            true
        )

        if (!ACTUALITES_INLL) com.example.kreyolkeyboard.actualites.FluxInll.oublier(this)

        // Restaurer l'onglet actif si l'activité a été recréée, ou honorer
        // l'onglet demandé par l'intent (puce de niveau tapée depuis le clavier).
        //
        // Gardé dans une variable locale en plus de currentTab : la mise en page
        // du ViewPager déclenche onPageSelected(), qui réécrit currentTab à 0
        // avant que le post{} plus bas ne le lise. Sans cette copie, l'onglet
        // demandé est systématiquement perdu entre les deux.
        val requestedTab = savedInstanceState?.getInt("currentTab", 0)
            ?: intent?.getIntExtra(EXTRA_OPEN_TAB, 0)
            ?: 0
        currentTab = requestedTab
        jeuOuvert = savedInstanceState?.getInt("jeuOuvert", -1) ?: -1
        
        // Masquer la barre d'action (bandeau noir)
        supportActionBar?.hide()
        
        Log.d("SettingsActivity", "Création de l'activité principale Lëtzebuergesch Clavier")
        
        // Layout principal vertical : Tabs en haut, puis ViewPager
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }
        
        // Créer la barre d'onglets horizontale
        tabBar = createTabBar()
        
        // ViewPager2 pour le contenu avec navigation swipe
        viewPager = ViewPager2(this).apply {
            id = R.id.onglets
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            adapter = SettingsPagerAdapter(this@SettingsActivity)
            
            // 🎨 Effet de swipe style Tinder
            setPageTransformer(TinderSwipeTransformer())
            
            // Callback pour synchroniser avec la barre d'onglets
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    // Calculer la position réelle (0, 1 ou 2) avec modulo
                    currentTab = position % SettingsPagerAdapter.REAL_COUNT
                    updateTabBar()
                    majBandeauInstallation()
                }
            })
            
            // 🔄 Démarrer au milieu de la plage virtuelle pour permettre le swipe dans les deux sens
            post {
                // Après une rotation, ViewPager2 a déjà rétabli sa page et les
                // fragments qu'elle portait : y sauter à nouveau en créerait
                // une neuve, et le jeu en cours serait perdu.
                if (savedInstanceState != null &&
                    currentItem % SettingsPagerAdapter.REAL_COUNT == requestedTab
                ) return@post
                val startPosition = SettingsPagerAdapter.START_POSITION - (SettingsPagerAdapter.START_POSITION % SettingsPagerAdapter.REAL_COUNT) + requestedTab
                setCurrentItem(startPosition, false)
            }
        }
        
        if (navigationLaterale()) {
            mainLayout.addView(createAppHeader())
            mainLayout.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
                addView(tabBar)
                viewPager.layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
                )
                addView(viewPager)
            })
        } else {
            mainLayout.addView(tabBar)
            mainLayout.addView(viewPager)
        }

        // FrameLayout racine : mainLayout en plein écran + bandeau d'installation
        // superposé, ancré en bas, visible dès l'onboarding (indépendant du scroll
        // du contenu en dessous)
        bottomInstallBanner = createBottomInstallBanner()
        val rootLayout = FrameLayout(this).apply {
            // Visible sous la barre de navigation en bord à bord (Android 15+) :
            // sans fond, c'est celui du thème AppCompat, sombre, qui s'y montre.
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            addView(mainLayout, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            addView(bottomInstallBanner, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.BOTTOM })
        }

        setContentView(rootLayout)
        // Bord à bord : la bande sous la barre d'état est peinte du bleu du
        // bandeau. La marge va sur mainLayout, pas sur le bandeau lui-même :
        // updateTabBar() reconstruit ce dernier à chaque changement d'onglet, et
        // la barre d'onglets est masquée au premier lancement. Le bas, bandeau
        // d'installation compris, s'écarte de la navigation et du clavier.
        // En paysage, le bandeau et les onglets s'étendent sous l'encoche et
        // seul leur contenu s'en écarte ; sinon une bande grise longe l'écran
        // du côté de la caméra.
        BordABord.appliquer(
            rootLayout, haut = mainLayout, couleurHaut = Color.parseColor("#0080FF"),
            lateraux = { (0 until tabBar.childCount).map { tabBar.getChildAt(it) } + viewPager }
        )

        recordFunnelStep("funnel_first_open")
        applyFirstRunMode()

        Log.d("SettingsActivity", "Interface avec tabs en haut et swipe cyclique créée avec succès")

        // Le partage reporté passe seul : la demande d'avis et celle des
        // notifications attendent l'ouverture suivante plutôt que de s'empiler.
        val partageMontre = savedInstanceState == null && proposerPartageEnAttente()
        if (!partageMontre) {
            maybeAskForReview()
            maybeAskForNotificationPermission()
        }
    }

    /**
     * Demande la permission de notification, une seule fois, et seulement une
     * fois le clavier réellement configuré : avant cela l'utilisateur est en
     * pleine installation, et une demande de plus dans ce tunnel déjà long ne
     * serait qu'une occasion supplémentaire d'abandonner.
     *
     * Cette permission ne sert qu'à la pastille de passage de niveau. Refusée,
     * le clavier fonctionne exactement comme avant : voir [LevelUpNotifier],
     * qui ne publie rien sans elle. La demande vient de l'activité parce qu'un
     * service de saisie ne peut pas afficher de dialogue de permission.
     */
    private fun maybeAskForNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!isKeyboardEnabled() || !isKeyboardSelected()) return
        if (LevelUpNotifier.canNotify(this)) {
            LevelUpNotifier.ensureChannel(this)
            return
        }

        val prefs = getSharedPreferences("lux_onboarding_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("notification_permission_asked", false)) return
        prefs.edit().putBoolean("notification_permission_asked", true).apply()

        LevelUpNotifier.ensureChannel(this)
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
    }

    // Mode « première ouverture » : tant que le clavier n'a jamais été activé,
    // la barre d'onglets et le swipe sont masqués pour concentrer sur la
    // configuration. Le flag ne se pose qu'une fois : un utilisateur configuré
    // qui désélectionne plus tard le clavier garde l'accès à tout.
    //
    // Le mode restreint s'arrêtait auparavant à la configuration *complète* —
    // clavier activé **et** sélectionné comme clavier courant. C'était trop
    // tard : quelqu'un qui a activé le clavier puis essayé un autre, ou qu'une
    // mise à jour système a désélectionné, se retrouvait renvoyé dans le
    // tunnel, sans Wierderbuch ni jeux, alors qu'il connaît déjà l'application.
    // Or ces deux-là ne dépendent que des assets et marchent sans clavier
    // installé. La barre revient donc dès l'activation, et ne repart plus.
    private fun onboardingPrefs() =
        getSharedPreferences("lux_onboarding_prefs", Context.MODE_PRIVATE)

    // Tunnel d'activation local : horodate chaque jalon du parcours
    // (première ouverture, activation, sélection, premier mot) une seule
    // fois, en SharedPreferences — diagnostic consultable dans À Propos,
    // rien ne quitte le téléphone, cohérent avec la politique « aucune
    // collecte » de l'app
    private fun recordFunnelStep(key: String) {
        val prefs = onboardingPrefs()
        if (!prefs.contains(key)) {
            prefs.edit().putLong(key, System.currentTimeMillis()).apply()
        }
    }

    private fun applyFirstRunMode() {
        if (onboardingPrefs().getBoolean("onboarding_completed", false)) return
        if (isKeyboardEnabled() && isKeyboardSelected()) {
            // Utilisateur déjà configuré (ex. mise à jour de l'app) :
            // poser le flag sans jamais montrer le mode restreint
            onboardingPrefs().edit().putBoolean("onboarding_completed", true).apply()
            return
        }
        if (!aDejaActiveLeClavier()) {
            tabBar.visibility = View.GONE
            viewPager.isUserInputEnabled = false
        }
        majBandeauInstallation()
    }

    /**
     * Le clavier a-t-il déjà été activé, maintenant ou par le passé ?
     *
     * `funnel_keyboard_enabled` est horodaté une seule fois, au premier passage
     * à l'état activé : il survit donc à une désactivation, à une mise à jour
     * système qui désélectionne le clavier, ou au détour par un autre clavier.
     */
    private fun aDejaActiveLeClavier(): Boolean =
        isKeyboardEnabled() || onboardingPrefs().contains("funnel_keyboard_enabled")

    /**
     * Rend la navigation dès l'activation, sans attendre la sélection.
     *
     * [onOnboardingCompleted] ne se déclenche qu'à la configuration complète ;
     * sans ce complément, quelqu'un qui active le clavier puis referme le
     * sélecteur système resterait enfermé jusqu'à sa prochaine ouverture de
     * l'application, alors que la condition d'accès est déjà remplie.
     */
    fun revelerNavigationSiClavierActive() {
        majBandeauInstallation()
        if (tabBar.visibility == View.VISIBLE) return
        if (!aDejaActiveLeClavier()) return
        tabBar.visibility = View.VISIBLE
        tabBar.alpha = 0f
        tabBar.animate().alpha(1f).setDuration(400).start()
        viewPager.isUserInputEnabled = true
    }

    /**
     * Le bandeau d'installation ne paraît que sur Démarrage, et seulement tant
     * que le clavier n'a jamais été configuré.
     *
     * Il était posé une fois pour toutes, ce qui suffisait quand Démarrage
     * était le seul onglet atteignable avant configuration. Maintenant que les
     * quatre le sont, il se superposerait au bas de la grille de Wuertsich et
     * de la liste du Wierderbuch — un rappel permanent qui mangerait le
     * contenu qu'il est censé faire découvrir.
     */
    private fun majBandeauInstallation() {
        if (!::bottomInstallBanner.isInitialized) return
        // Une fois le clavier activé, l'étape 2 s'ouvre d'elle-même (sélecteur
        // ouvert au retour des réglages) et sa carte porte son propre bouton :
        // le bandeau ne ferait que doubler l'appel, par-dessus le clavier d'essai.
        val aConfigurer = !onboardingPrefs().getBoolean("onboarding_completed", false) &&
            !isKeyboardEnabled()
        if (aConfigurer && currentTab == 0) {
            bottomInstallBanner.alpha = 1f
            bottomInstallBanner.visibility = View.VISIBLE
        } else {
            bottomInstallBanner.visibility = View.GONE
        }
    }

    // Appelé par l'onboarding quand la configuration vient d'aboutir :
    // pose le flag et révèle la navigation avec un léger fondu
    /** Vrai tant que la pochette de bienvenue est à l'écran : le clavier reste baissé. */
    var pochetteAccueilOuverte = false
        private set

    fun onOnboardingCompleted() {
        val prefs = onboardingPrefs()
        if (!prefs.getBoolean("onboarding_completed", false)) {
            prefs.edit().putBoolean("onboarding_completed", true).apply()
        }
        viewPager.isUserInputEnabled = true
        if (tabBar.visibility != View.VISIBLE) {
            tabBar.visibility = View.VISIBLE
            tabBar.alpha = 0f
            tabBar.animate().alpha(1f).setDuration(400).start()
        }
        if (bottomInstallBanner.visibility == View.VISIBLE) {
            bottomInstallBanner.animate().alpha(0f).setDuration(300)
                .withEndAction { bottomInstallBanner.visibility = View.GONE }
                .start()
        }
        // Le clavier d'essai (dictionnaires + moteur de suggestions chargés
        // dans le processus de l'app) n'a plus de raison d'exister une fois
        // la configuration terminée : la carte qui l'hébergeait disparaît
        // du wizard, mais l'objet restait sinon référencé indéfiniment
        demoKeyboardManager?.cleanup()
        demoKeyboardManager = null
        demoEngine = null
        demoEngineReady = false

        // La pochette « Moien » attend le premier mot. Ouverte à la sélection du
        // clavier, elle tombait au moment exact où l'on touchait le champ
        // d'essai, prenait ce toucher, et l'étape 3 se faisait derrière elle.
        // Si le mot a déjà été écrit (ailleurs, ou avant ce rafraîchissement),
        // elle s'ouvre tout de suite ; sinon c'est [planifierPochette] qui
        // l'ouvre, une fois la frappe arrêtée.
        if (aEcritUnMot()) maybeShowActivationSuccessCard()
    }

    /** Écoute du jalon `funnel_first_word`, tenue ici : l'abonnement est faible. */
    private var ecoutePremierMot: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private val minuteriePochette = Handler(Looper.getMainLooper())
    private val ouvrirPochetteApresPause = Runnable {
        if (!isFinishing && !isDestroyed && aEcritUnMot()) maybeShowActivationSuccessCard()
    }

    /**
     * Ouvre la pochette « Moien » après une pause dans la frappe, jamais au
     * milieu : l'étape 3 propose d'écrire « Moien alleguer », et une pochette
     * ouverte après le premier mot baisserait le clavier avant le second.
     */
    private fun planifierPochette() {
        if (onboardingPrefs().getBoolean("activation_success_card_shown", false)) return
        minuteriePochette.removeCallbacks(ouvrirPochetteApresPause)
        minuteriePochette.postDelayed(ouvrirPochetteApresPause, DELAI_POCHETTE_MS)
    }

    // Récompense l'utilisateur juste après un parcours d'activation identifié
    // comme un point de friction (réglages système, sélecteur) : un seul
    // affichage, jamais reposé même si l'onboarding se rejoue. La récompense
    // est la carte « Moien », versée au carnet et ouverte comme une pochette ;
    // le partage vient ensuite, une fois la pochette refermée. Le message
    // proposé au partage est fixe, écrit avant que l'utilisateur ait tapé
    // quoi que ce soit avec le clavier — aucun contenu personnel n'est lu.
    private fun maybeShowActivationSuccessCard() {
        val prefs = onboardingPrefs()
        if (prefs.getBoolean("activation_success_card_shown", false)) return
        prefs.edit().putBoolean("activation_success_card_shown", true).apply()

        // L'onboarding lève le clavier sur le champ d'essai dès que le clavier
        // est sélectionné, et la fenêtre rétrécie coupait la carte sous la
        // plaque du nom : le sens, qui est ce qu'on offre, restait caché.
        pochetteAccueilOuverte = true
        currentFocus?.clearFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(window.decorView.windowToken, 0)

        val ctx = applicatifDansLaLangue()
        Thread {
            val neuve = CarteAccueil.offrir(ctx)
            TranslationDictionary.charger(ctx)
            TranslationDictionary.chargerExemples(ctx)
            val contenu = Carnet.cartes(ctx).firstOrNull { it.forme == CarteAccueil.FORME }
                ?.let { CarteCarnet.contenu(ctx, it) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (contenu == null) {
                    pochetteAccueilOuverte = false
                    reporterPartage()
                    return@runOnUiThread
                }
                Booster.ouvrir(
                    hote = findViewById(android.R.id.content),
                    jeu = JeuCarte.ACCUEIL,
                    contenus = listOf(contenu),
                    nouvelles = if (neuve) setOf(CarteAccueil.FORME) else emptySet(),
                    animations = !Pochette.animationsReduites(ctx),
                    surCarnet = { CarnetFragment().show(supportFragmentManager, "carnet") },
                    surFin = {
                        pochetteAccueilOuverte = false
                        reporterPartage()
                    }
                )
            }
        }.start()
    }

    /**
     * Le partage n'est plus proposé dans la foulée de la pochette : c'était la
     * deuxième fenêtre de suite avant même d'avoir vraiment écrit. Il l'est à
     * l'ouverture suivante de l'application, une seule fois, et prend alors la
     * place des demandes d'avis et de notifications, pour ne pas les empiler.
     */
    private fun reporterPartage() {
        onboardingPrefs().edit().putBoolean(PREF_PARTAGE_EN_ATTENTE, true).apply()
    }

    /** Vrai si la fenêtre de partage a été montrée. */
    private fun proposerPartageEnAttente(): Boolean {
        val prefs = onboardingPrefs()
        if (!prefs.getBoolean(PREF_PARTAGE_EN_ATTENTE, false)) return false
        // « Il est activé ! » au-dessus de la carte « Revenir au clavier »
        // se contredirait : le partage attend que le clavier soit de retour.
        if (!isKeyboardEnabled() || !isKeyboardSelected()) return false
        prefs.edit().remove(PREF_PARTAGE_EN_ATTENTE).apply()
        showActivationShareDialog(carteOfferte = true)
        return true
    }

    private fun showActivationShareDialog(carteOfferte: Boolean) {
        val bravo = getString(R.string.sa_bravo_et_ass_geschafft_le)
        AlertDialog.Builder(this)
            .setTitle("🎉 Lëtzebuergesch Clavier ass aktivéiert !")
            .setMessage(if (carteOfferte) getString(R.string.sa_la_carte_moien_est_dans, bravo) else bravo)
            .setPositiveButton(getString(R.string.sa_partager_la_nouvelle)) { _, _ -> shareActivationSuccess() }
            .setNegativeButton(getString(R.string.sa_plus_tard), null)
            .setCancelable(true)
            .show()
    }

    // Partage natif (chooser Android), message pré-rédigé et fixe : célèbre
    // l'activation, pas un contenu écrit par l'utilisateur
    private fun shareActivationSuccess() {
        val message = getString(R.string.sa_ech_schreiwen_elo_op_letzebuergesch, packageName) +
            SHARE_HASHTAG
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.sa_partager_le_letzebuergesch_clavier)))
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur partage activation: ${e.message}")
            Toast.makeText(this, getString(R.string.sa_impossible_de_partager_pour_le), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Demande d'avis Google Play (In-App Review), déclenchée seulement après
     * un vrai usage du clavier (flag posé par le service IME) et à partir de
     * la 2ᵉ ouverture de l'app — le moment où l'utilisateur revient de lui-même.
     * L'API Play limite elle-même la fréquence d'affichage ; on ne tente
     * qu'une fois pour ne pas consommer le quota inutilement.
     */
    private fun maybeAskForReview() {
        // Mêmes clés que dans KreyolInputMethodServiceRefactored
        val prefs = getSharedPreferences("lux_onboarding_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("first_real_use_tip_shown", false)) return
        if (prefs.getBoolean("review_flow_requested", false)) return

        val openCount = prefs.getInt("settings_open_count_after_use", 0) + 1
        prefs.edit().putInt("settings_open_count_after_use", openCount).apply()
        if (openCount < 2) return

        try {
            val manager = ReviewManagerFactory.create(this)
            manager.requestReviewFlow().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    prefs.edit().putBoolean("review_flow_requested", true).apply()
                    manager.launchReviewFlow(this, task.result)
                    Log.d("SettingsActivity", "Flux d'avis Google Play lancé")
                } else {
                    Log.d("SettingsActivity", "Flux d'avis indisponible: ${task.exception?.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur demande d'avis: ${e.message}")
        }
    }
    
    /**
     * La pastille de l'onglet statistiques n'était lue qu'au moment de
     * construire la barre d'onglets, donc uniquement au démarrage à froid.
     * Un palier franchi pendant que l'application dormait dans la pile des
     * tâches restait invisible au retour (constaté sur émulateur le
     * 08/08/2026) — et quand la permission de notification a été refusée,
     * cette pastille est le seul signal qui existe.
     */
    override fun onResume() {
        super.onResume()
        // Revenu dans l'appli, de lui-même ou ramené : plus rien à guetter.
        arreterGuetActivation()

        if (::tabBar.isInitialized && hasPendingLevelBadge() != levelBadgeDrawn) {
            Log.d("SettingsActivity", "🔄 Pastille de niveau à rafraîchir au retour au premier plan")
            updateTabBar()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Sauvegarder l'onglet actif avant que l'activité soit recréée
        outState.putInt("currentTab", currentTab)
        outState.putInt("jeuOuvert", jeuOuvert)
        Log.d("SettingsActivity", "💾 Sauvegarde de l'onglet actif: $currentTab")
    }
    
    override fun onDestroy() {
        // 🔧 FIX CRITIQUE: Annuler toutes les coroutines de l'activité
        activityScope.cancel()
        ecoutePremierMot?.let { onboardingPrefs().unregisterOnSharedPreferenceChangeListener(it) }
        ecoutePremierMot = null
        minuteriePochette.removeCallbacks(ouvrirPochetteApresPause)
        arreterGuetActivation()
        Log.d("SettingsActivity", "✅ Coroutines de l'activité annulées proprement")
        
        super.onDestroy()
    }
    
    /**
     * 🔧 FIX CRITIQUE: Ajouter délai avant fermeture pour éviter "Consumer closed input channel"
     * Laisse le temps aux derniers événements tactiles d'être traités
     */
    override fun onBackPressed() {
        // Délai de 100ms pour traiter les événements en cours
        Handler(Looper.getMainLooper()).postDelayed({
            super.onBackPressed()
        }, 100)
    }
    
    // Bandeau d'installation ancré en bas, superposé au contenu de l'onboarding
    // (indépendant du scroll) : rappel visuel permanent tant que le clavier
    // n'est ni activé ni sélectionné. S'ajoute au CTA déjà présent dans la
    // carte de démo (createDemoKeyboardCard) sans le remplacer — même style
    // et même action pour rester cohérent.
    private fun createBottomInstallBanner(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#0080FF"))
            elevation = 12f
            setPadding(32, 28, 32, 28)
            setOnClickListener { poursuivreInstallation() }

            val label = TextView(this@SettingsActivity).apply {
                text = getString(R.string.sa_ca_vous_plait_installez_le)
                textSize = 15f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            addView(label)
        }
    }

    /**
     * Sur une tablette couchée, les onglets passent dans un rail à gauche.
     *
     * En haut, ils prenaient 70 dp sur les 800 de hauteur, et c'est la hauteur
     * qui manque aux jeux à grille ; la largeur, elle, est en trop. Le bandeau
     * bleu reste en haut, sur toute la largeur.
     */
    private fun navigationLaterale(): Boolean = DeuxColonnes.actives(this)

    /** Le bandeau bleu : titre et engrenage des réglages du clavier. */
    private fun createAppHeader(): LinearLayout {
            val appHeader = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(Color.parseColor("#0080FF"))
                gravity = Gravity.CENTER
                setPadding(16, 16, 16, 16)
            }
            
            val appTitle = TextView(this@SettingsActivity).apply {
                text = "Lëtzebuergesch Clavier"
                textSize = 22f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                // Poids 1 : le titre occupe la place laissée par l'engrenage et reste
                // centré sur le bandeau entier.
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }

            // Les réglages du clavier vivent derrière cet engrenage, dans leur propre
            // écran : c'est la convention Android, et la barre porte déjà sept onglets.
            // Icône vectorielle blanche, et non l'emoji ⚙️ : la police emoji du
            // système le dessine en gris bleuté, une teinte que le bandeau bleu
            // avale. Le tracé blanc ne dépend plus de la police du téléphone et
            // ressort de la même façon sur tous les appareils.
            val densite = resources.displayMetrics.density
            val settingsButton = ImageView(this@SettingsActivity).apply {
                setImageResource(R.drawable.ic_settings_gear)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = getString(R.string.ks_titre)
                // 48 dp de zone tactile, icône dessinée à 26 dp au centre : c'est
                // l'encombrement qu'avait déjà le TextView, la hauteur du bandeau
                // ne bouge donc pas.
                val zone = (48 * densite).toInt()
                layoutParams = LinearLayout.LayoutParams(zone, zone)
                val marge = (11 * densite).toInt()
                setPadding(marge, marge, marge, marge)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    startActivity(Intent(this@SettingsActivity, KeyboardSettingsActivity::class.java))
                }
            }

            // Cale de la largeur de l'engrenage, à gauche : sans elle le titre,
            // centré dans la place restante, se décale visiblement vers la gauche.
            appHeader.addView(View(this@SettingsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (48 * densite).toInt(), 1
                )
            })
            appHeader.addView(appTitle)
            appHeader.addView(settingsButton)
            return appHeader
    }

    private fun createTabBar(): LinearLayout {
        val rail = navigationLaterale()
        return LinearLayout(this).apply {
            orientation = if (rail) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            layoutParams = if (rail) {
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            } else {
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            setBackgroundColor(Color.WHITE)
            elevation = 4f // Ombre légère pour séparer du contenu

            // Container pour les onglets
            val tabContainer = LinearLayout(this@SettingsActivity).apply {
                orientation = if (rail) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                // Hauteur suivant le contenu, et non 140 px figés : l'emoji seul en
                // réclamait 165 (60 dp), donc le libellé de chaque onglet était rogné
                // hors de la vue et la barre n'identifiait sept destinations que par
                // des emojis nus.
                layoutParams = if (rail) {
                    LinearLayout.LayoutParams(
                        (RAIL_LARGEUR_DP * resources.displayMetrics.density).toInt(),
                        LinearLayout.LayoutParams.MATCH_PARENT
                    )
                } else {
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                gravity = if (rail) Gravity.CENTER_HORIZONTAL or Gravity.TOP else Gravity.CENTER
                if (rail) setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
            }
            
            // Tab Démarrage
            // v29.4.0 : « Haut » (aujourd'hui) et non plus « Démarrage ». Une fois le
            // clavier installé, l'onglet montre le jour (cartes à revoir, mot du
            // jour, dernier jeu) ; le nom luxembourgeois suit Spiller et Wierderbuch.
            val startTab = createTab(0, "🏠", "Haut")
            tabContainer.addView(startTab)
            Log.d("SettingsActivity", "Onglet Démarrage créé et ajouté")

            // Tab Spiller : tous les jeux derrière une seule destination.
            // Ils occupaient quatre onglets sur neuf, soit 44 % de la barre,
            // pour une activité que l'on choisit une fois par session.
            val gamesTab = createTab(1, "🎮", "Spiller")
            tabContainer.addView(gamesTab)
            Log.d("SettingsActivity", "Onglet Spiller créé et ajouté")

            // Tab Wierderbuch
            val dictionaryTab = createTab(2, "📚", "Wierderbuch")
            tabContainer.addView(dictionaryTab)
            Log.d("SettingsActivity", "Onglet Wierderbuch créé et ajouté")

            // Tab Statistiques (en dernier, sur demande du propriétaire — 2026-09-15)
            val statsTab = createTab(3, "📊", "Mäi Lëtzebuergesch")
            tabContainer.addView(statsTab)
            Log.d("SettingsActivity", "Onglet Statistiques créé et ajouté")

            // Guide et À Propos ne sont plus des onglets : ce sont des pages de
            // référence que l'on lit une fois, pas des destinations
            // quotidiennes. Elles s'ouvrent depuis le pied de l'onglet
            // Démarrage, en plein écran (voir SheetFragment).

            // Ligne de séparation fine : en bas de la barre, à droite du rail
            val separator = View(this@SettingsActivity).apply {
                layoutParams = if (rail) {
                    LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
                } else {
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)
                }
                setBackgroundColor(Color.parseColor("#E0E0E0"))
            }
            
            if (!rail) addView(createAppHeader())
            addView(tabContainer)
            addView(separator)
        }
    }
    
    /** Préférences partagées avec le service de saisie pour la progression. */
    private fun gamificationPrefs() =
        getSharedPreferences("lux_gamification_prefs", Context.MODE_PRIVATE)

    /** Un palier a-t-il été franchi sans que l'utilisateur ait rouvert ses statistiques ? */
    private fun hasPendingLevelBadge(): Boolean =
        gamificationPrefs().getBoolean(PREF_LEVEL_BADGE_PENDING, false)

    private fun createTab(tabIndex: Int, emoji: String, label: String): LinearLayout {
        Log.d("SettingsActivity", "Création onglet $tabIndex: $emoji $label")
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(8, 10, 8, 8)
            layoutParams = if (navigationLaterale()) {
                // Dans le rail : empilés en haut, chacun à sa hauteur.
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (4 * resources.displayMetrics.density).toInt() }
            } else {
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f
                )
            }
            // Background légèrement coloré si onglet actif
            setBackgroundColor(
                if (tabIndex == currentTab) 
                    Color.parseColor("#FFF5E6") // Beige clair orangé
                else 
                    Color.WHITE
            )
            
            // Emoji du tab
            val emojiView = TextView(this@SettingsActivity).apply {
                text = emoji
                textSize = 32f // Augmenté encore plus
                gravity = Gravity.CENTER
                setPadding(0, 4, 0, 2)
                // Emoji légèrement teinté si actif pour plus de cohérence visuelle
                alpha = if (tabIndex == currentTab) 1.0f else 0.6f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                // Plus de minHeight de 60 dp : il réservait à l'emoji seul plus de
                // hauteur que la barre n'en avait, ce qui chassait le libellé.
            }
            
            // Label du tab
            val labelView = TextView(this@SettingsActivity).apply {
                text = label
                // 11sp : quatre onglets se partagent la largeur au lieu de
                // neuf, et « Wierderbuch », le plus long, tient largement.
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 2)
                // Garde-fou conservé bien que quatre onglets laissent la place :
                // un libellé plus long qu'attendu doit passer à la ligne ou se
                // terminer en points de suspension, jamais repousser ses voisins.
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(
                    if (tabIndex == currentTab) 
                        Color.parseColor("#FF8C00") 
                    else 
                        Color.GRAY
                )
                setTypeface(null, if (tabIndex == currentTab) Typeface.BOLD else Typeface.NORMAL)
            }
            
            // Pastille de niveau non vu : le franchissement d'un palier n'est
            // célébré qu'au dessin du contenu de l'onglet statistiques. Sans ce
            // repère, quelqu'un qui ouvre l'application et reste sur Démarrage
            // ne saurait jamais qu'il a quelque chose à y voir.
            if (tabIndex == TAB_STATS && hasPendingLevelBadge()) {
                levelBadgeDrawn = true
                addView(FrameLayout(this@SettingsActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    addView(emojiView)
                    addView(View(this@SettingsActivity).apply {
                        val d = resources.displayMetrics.density
                        layoutParams = FrameLayout.LayoutParams(
                            (10 * d).toInt(), (10 * d).toInt()
                        ).apply { gravity = Gravity.TOP or Gravity.END }
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(Color.parseColor("#FF8C00"))
                            setStroke((2 * d).toInt(), Color.WHITE)
                        }
                    })
                })
            } else {
                if (tabIndex == TAB_STATS) levelBadgeDrawn = false
                addView(emojiView)
            }
            addView(labelView)

            // Indicateur orange en bas si tab actif
            if (tabIndex == currentTab) {
                val indicator = View(this@SettingsActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        60,
                        4
                    ).apply {
                        topMargin = 6
                    }
                    setBackgroundColor(Color.parseColor("#FF8C00"))
                }
                addView(indicator)
            }
            
            setOnClickListener {
                // Calculer la position virtuelle la plus proche pour le tabIndex demandé
                val currentPosition = viewPager.currentItem
                val currentRealTab = currentPosition % SettingsPagerAdapter.REAL_COUNT
                val targetRealTab = tabIndex
                
                // Calculer la distance la plus courte en tenant compte du cycle
                val forwardDistance = (targetRealTab - currentRealTab + SettingsPagerAdapter.REAL_COUNT) % SettingsPagerAdapter.REAL_COUNT
                val backwardDistance = (currentRealTab - targetRealTab + SettingsPagerAdapter.REAL_COUNT) % SettingsPagerAdapter.REAL_COUNT
                
                // Choisir la direction la plus courte
                val distance = minOf(forwardDistance, backwardDistance)
                val targetPosition = if (forwardDistance <= backwardDistance) {
                    currentPosition + forwardDistance
                } else {
                    currentPosition - backwardDistance
                }

                // Défilement animé pour un onglet voisin seulement. Au-delà,
                // ViewPager2 s'arrête en chemin : un saut de trois pages
                // atterrissait une ou deux pages trop tôt, la barre d'onglets
                // affichant pourtant l'onglet demandé — `onPageSelected` reçoit
                // bien la position visée, c'est le défilement qui n'y arrive
                // pas. Constaté sur émulateur pour 0 → 3 (on atterrit sur
                // Wuertsich) et 0 → 5 (on atterrit sur À Propos). Le défaut
                // vaut pour toute distance ≥ 2 et ne dépend pas du nombre
                // d'onglets ; il devient simplement visible depuis l'accueil
                // avec un huitième onglet.
                viewPager.setCurrentItem(targetPosition, distance <= 1)
            }
        }
    }
    
    /**
     * Reconstruit la barre après un changement d'onglet, pour que l'onglet actif
     * change d'aspect.
     *
     * Elle recopiait auparavant toute la construction de [createTabBar], et les deux
     * copies ont divergé : l'engrenage des réglages et la correction de hauteur des
     * onglets n'existaient que dans l'original, donc ne s'affichaient jamais, cette
     * fonction étant appelée dès le premier changement d'onglet. Une seule
     * construction fait désormais autorité.
     */
    private fun updateTabBar() {
        tabBar.removeAllViews()
        val fraiche = createTabBar()
        val enfants = (0 until fraiche.childCount).map { fraiche.getChildAt(it) }
        fraiche.removeAllViews() // une vue ne peut pas avoir deux parents
        enfants.forEach { tabBar.addView(it) }
        // Les enfants neufs n'ont pas encore l'écart de l'encoche (paysage).
        BordABord.ecarterLateralement(enfants)
    }

    // Onglet 1 : Démarrage / Onboarding
    fun createOnboardingContent(): LinearLayout {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 32)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val isEnabled = isKeyboardEnabled()
        val isSelected = isKeyboardSelected()
        // Distingue le tout premier setup d'un retour après désélection
        // (mise à jour système, changement de clavier...) : le ton et
        // l'habillage diffèrent, mais les étapes restent les mêmes
        val hasCompletedBefore = onboardingPrefs().getBoolean("onboarding_completed", false)

        // Toute reconstruction complète de l'onglet (retour au premier plan,
        // changement détecté dans les réglages système) rend la main à
        // l'ouverture automatique : c'est l'étape qui reste à faire qui doit
        // être dépliée, pas celle que l'utilisateur avait ouverte à la main
        // deux écrans plus tôt.
        etapeConfigOuverte = null

        // 🔍 Log pour déboguer l'état du clavier
        Log.d("SettingsActivity", "📋 État du clavier: isEnabled=$isEnabled, isSelected=$isSelected")

        // Jalons du tunnel d'activation (horodatés une seule fois)
        if (isEnabled) recordFunnelStep("funnel_keyboard_enabled")
        if (isSelected) recordFunnelStep("funnel_keyboard_selected")

        // Nudge « activation inachevée » : l'utilisateur est allé dans les
        // réglages mais le clavier n'est toujours pas activé — le cas le
        // plus fréquent est l'abandon au second des deux avertissements
        // système (qui annule silencieusement l'activation)
        val settingsVisitAt = onboardingPrefs().getLong("settings_visit_at", 0L)
        if (isEnabled && settingsVisitAt != 0L) {
            onboardingPrefs().edit().remove("settings_visit_at").apply()
        }
        val showIncompleteNudge = !isEnabled && settingsVisitAt != 0L
        if (showIncompleteNudge) recordFunnelStep("funnel_settings_return_no_enable")

        // Retour au clavier après un détour : l'utilisateur a déjà configuré
        // l'application, le clavier est toujours installé, mais ce n'est plus
        // lui qui s'ouvre. C'est l'état où l'on se retrouve après un appui
        // long sur la barre d'espace suivi d'un choix dans le sélecteur
        // système — et l'observation de terrain qui a motivé cette carte est
        // que personne n'en revient tout seul.
        //
        // Android interdit à un clavier de se remettre en service lui-même :
        // un IME peut demander à partir (showInputMethodPicker), jamais à
        // revenir. Une fois l'autre clavier actif, il n'y a plus rien à
        // chercher dans le clavier, et le seul chemin de retour passe par
        // l'application ou par la petite icône de la barre de navigation. La
        // carte est donc placée en tête, avant même la configuration rapide.
        if (hasCompletedBefore && isEnabled && !isSelected) {
            mainLayout.addView(createRetourClavierCard())
            mainLayout.addView(createSpacing(16))
        }

        // Accueil « Aujourd'hui » (v29.4.0). Une fois le clavier installé et
        // l'installation déjà menée à terme une fois, l'onglet ne s'ouvre plus
        // sur la configuration, faite pour de bon, mais sur ce qui fait revenir :
        // les cartes à revoir, le mot du jour, le dernier jeu, la progression.
        // Tout ce qui concerne la configuration passe dans un volet replié, sous
        // une seule ligne. Tant que le clavier n'est pas actif, rien ne change.
        val modeAujourdhui = hasCompletedBefore && isEnabled && isSelected
        val cible: LinearLayout = if (!modeAujourdhui) mainLayout else LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, enDp(12), 0, 0)
        }
        if (modeAujourdhui) {
            mainLayout.addView(creerAujourdhui())
            // Le correcteur était rangé dans le volet replié ci-dessous, que
            // personne ne rouvre une fois le clavier installé : un utilisateur
            // configuré ne le découvrait jamais. Android interdit de le choisir
            // à sa place, donc on le lui propose ici, jusqu'à ce qu'il le fasse
            // ou qu'il dise « plus tard ».
            if (!isSpellCheckerSelected() &&
                !onboardingPrefs().getBoolean(PREF_CORRECTEUR_ACCUEIL_MASQUE, false)) {
                mainLayout.addView(carteCorrecteurAccueil())
            }
            mainLayout.addView(createSpacing(8))
            mainLayout.addView(ligneConfiguration(cible))
            mainLayout.addView(cible)
            mainLayout.addView(createSpacing(16))
        }

        // Bandeau de réussite : le clavier est utilisable dès qu'il est
        // activé et sélectionné, avant même que l'utilisateur ait écrit quoi
        // que ce soit. Son texte le dit alors sans prétendre que la
        // configuration est terminée, sinon il contredirait l'anneau 2/3.
        if (isEnabled && isSelected) {
            cible.addView(createReadyBanner(aEcritUnMot()))
            cible.addView(createSpacing(12))
        }

        // Carte de configuration, isolée dans son propre conteneur : déplier
        // une étape ne reconstruit qu'elle, et ne refait pas le clavier
        // d'essai (rechargement des dictionnaires, perte du texte déjà tapé
        // dedans).
        val conteneurConfig = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        remplirCarteConfig(conteneurConfig, isEnabled, isSelected, hasCompletedBefore, showIncompleteNudge)
        cible.addView(conteneurConfig)
        cible.addView(createSpacing(24))

        // Essai du clavier : un vrai clavier interactif avec suggestions
        // bilingues, à essayer sans rien installer. Il ouvrait l'onglet, pour
        // que la motivation précède la mécanique, mais il occupait alors toute
        // la hauteur visible et repoussait sous la ligne de flottaison le
        // bouton qui ouvre les réglages Android : l'action attendue de
        // l'utilisateur ne se voyait plus sans faire défiler. Il passe donc
        // sous la carte de configuration, où il reste la première chose que
        // l'on rencontre en descendant. Inutile pour un utilisateur qui
        // revient après une désélection : il connaît déjà.
        if ((!isEnabled || !isSelected) && !hasCompletedBefore) {
            cible.addView(createDemoKeyboardCard())
            cible.addView(createSpacing(24))
        }

        // Correcteur orthographique : fonctionnalité indépendante des 3 étapes
        // critiques (fonctionne même sans avoir activé le clavier Kréyòl),
        // sortie du parcours numéroté pour ne pas laisser croire à une
        // "étape 4" alors que la barre de progression annonce 3 étapes
        val extrasTitle = TextView(this).apply {
            text = getString(R.string.sa_pour_aller_plus_loin_optionnel)
            textSize = 16f
            setTextColor(Color.parseColor("#666666"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }
        cible.addView(extrasTitle)

        cible.addView(createGroussschreiwungCard())
        cible.addView(createSpacing(16))
        cible.addView(createSpellCheckerCard())
        cible.addView(createSpacing(24))

        // Bascule d'un clavier à l'autre : l'aller et le retour n'utilisent
        // pas le même geste (chemins vérifiés à l'émulateur), et c'est le
        // retour vers le luxembourgeois qui bloque les utilisateurs, l'appui long sur
        // la barre d'espace des autres claviers ne changeant que leur propre
        // langue. Affiché une fois la configuration terminée, au moment où la
        // question se pose vraiment.
        if (isEnabled && isSelected) {
            val switchCard = createRoundedCard("#E3F2FD")

            val switchTitle = TextView(this).apply {
                text = getString(R.string.sa_passer_du_francais_au_luxembourgeois)
                textSize = 18f
                setTextColor(Color.parseColor("#0D47A1"))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 12)
            }

            val switchIntro = TextView(this).apply {
                text = getString(R.string.sa_letzebuergesch_clavier_ne_remplace_pas)
                textSize = 14f
                setTextColor(Color.parseColor("#1565C0"))
                setLineSpacing(0f, 1.3f)
                setPadding(0, 0, 0, 12)
            }

            val switchAwayTitle = TextView(this).apply {
                text = getString(R.string.sa_quitter_le_luxembourgeois)
                textSize = 15f
                setTextColor(Color.parseColor("#0D47A1"))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 4)
            }

            val switchAway = TextView(this).apply {
                text = getString(R.string.sa_appuyez_une_seconde_sur_la)
                textSize = 14f
                setTextColor(Color.parseColor("#1565C0"))
                setLineSpacing(0f, 1.3f)
                setPadding(0, 0, 0, 12)
            }

            val switchBackTitle = TextView(this).apply {
                text = getString(R.string.sa_revenir_au_luxembourgeois)
                textSize = 15f
                setTextColor(Color.parseColor("#0D47A1"))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 4)
            }

            val switchBack = TextView(this).apply {
                text = getString(R.string.sa_le_geste_est_pas_symetrique)
                textSize = 14f
                setTextColor(Color.parseColor("#1565C0"))
                setLineSpacing(0f, 1.3f)
                setPadding(0, 0, 0, 12)
            }

            val switchNote = TextView(this).apply {
                text = getString(R.string.sa_le_choix_vaut_pour_toutes)
                textSize = 13f
                setTextColor(Color.parseColor("#5C6BC0"))
                setLineSpacing(0f, 1.3f)
                setPadding(0, 0, 0, 16)
            }

            val switchButton = Button(this).apply {
                text = getString(R.string.sa_ouvrir_le_selecteur_de_claviers_2)
                textSize = 15f
                setBackgroundColor(Color.parseColor("#0080FF"))
                setTextColor(Color.WHITE)
                setPadding(24, 16, 24, 16)
                setOnClickListener { openInputMethodPicker() }
            }

            switchCard.addView(switchTitle)
            switchCard.addView(switchIntro)
            switchCard.addView(switchAwayTitle)
            switchCard.addView(switchAway)
            switchCard.addView(switchBackTitle)
            switchCard.addView(switchBack)
            switchCard.addView(switchNote)
            switchCard.addView(switchButton)

            cible.addView(switchCard)
            cible.addView(createSpacing(16))
        }

        // Section "Astuce" si tout est configuré
        if (isEnabled && isSelected) {
            val tipCard = createRoundedCard("#FFF9E6")
            
            val tipHeader = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, 8)
            }
            
            val tipIcon = TextView(this).apply {
                text = "💡"
                textSize = 24f
                setPadding(0, 0, 12, 0)
            }
            
            val tipTitle = TextView(this).apply {
                text = getString(R.string.sa_astuce_de_la_semaine)
                textSize = 16f
                setTextColor(Color.parseColor("#F57C00"))
                setTypeface(null, Typeface.BOLD)
            }
            
            tipHeader.addView(tipIcon)
            tipHeader.addView(tipTitle)
            
            val tipText = TextView(this).apply {
                text = getTipOfTheWeek()
                textSize = 14f
                setTextColor(Color.parseColor("#666666"))
                setLineSpacing(0f, 1.3f)
            }
            
            tipCard.addView(tipHeader)
            tipCard.addView(tipText)
            
            mainLayout.addView(tipCard)
            mainLayout.addView(createSpacing(16))
            
            // Lien vers statistiques
            val statsLinkCard = createRoundedCard("#E8F5E9")
            
            val statsLinkLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            
            val statsIcon = TextView(this).apply {
                text = "📊"
                textSize = 32f
                setPadding(0, 0, 16, 0)
            }
            
            val statsTextContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
            
            val statsTitle = TextView(this).apply {
                text = getString(R.string.sa_decouvrez_vos_statistiques)
                textSize = 16f
                setTextColor(Color.parseColor("#2E7D32"))
                setTypeface(null, Typeface.BOLD)
            }
            
            val statsDesc = TextView(this).apply {
                text = getString(R.string.sa_suivez_votre_progression_et_montez)
                textSize = 13f
                setTextColor(Color.parseColor("#558B2F"))
            }
            
            statsTextContainer.addView(statsTitle)
            statsTextContainer.addView(statsDesc)
            
            val statsArrow = TextView(this).apply {
                text = "→"
                textSize = 24f
                setTextColor(Color.parseColor("#2E7D32"))
            }
            
            statsLinkLayout.addView(statsIcon)
            statsLinkLayout.addView(statsTextContainer)
            statsLinkLayout.addView(statsArrow)
            
            statsLinkCard.addView(statsLinkLayout)
            statsLinkCard.setOnClickListener {
                // Et non `currentItem = 1` : le pager est cyclique, et la
                // position absolue 1 est le bord gauche de la plage virtuelle.
                // Le contenu affiché était bien celui des statistiques, mais on
                // atterrissait là où il n'y a plus rien à balayer vers la
                // gauche. On vise la position la plus proche du bon onglet.
                allerAOnglet(TAB_STATS)
            }
            
            if (!modeAujourdhui) mainLayout.addView(statsLinkCard)
        }

        // Guide et À Propos ont quitté la barre d'onglets : ce sont des pages
        // qu'on lit une fois, et elles y coûtaient deux neuvièmes de la largeur
        // à chaque ouverture de l'application. Elles atterrissent ici, en pied
        // de l'écran de configuration, qui est déjà l'endroit où l'on vient
        // quand on cherche à comprendre plutôt qu'à jouer.
        mainLayout.addView(createSpacing(8))
        if (ACTUALITES_INLL) {
            mainLayout.addView(createReferenceLink(
                "📰", getString(R.string.act_lien_titre),
                getString(R.string.act_lien_resume),
                SheetFragment.PAGE_ACTUALITES
            ))
            mainLayout.addView(createSpacing(8))
        }
        mainLayout.addView(createReferenceLink(
            "📖", getString(R.string.sa_guide_utilisation),
            getString(R.string.sa_reglages_correcteur_astuces_de_saisie),
            SheetFragment.PAGE_GUIDE
        ))
        mainLayout.addView(createSpacing(8))
        mainLayout.addView(createReferenceLink(
            "ℹ️", getString(R.string.sa_propos_2),
            getString(R.string.sa_version_sources_des_donnees_licences),
            SheetFragment.PAGE_A_PROPOS
        ))
        mainLayout.addView(createSpacing(16))

        return mainLayout
    }

    // ===== Accueil « Aujourd'hui » =====

    private fun accueilPrefs() = getSharedPreferences(ACCUEIL_PREFS, Context.MODE_PRIVATE)

    /**
     * Retient le dernier jeu ouvert, pour la carte « Reprendre » de l'accueil.
     * Un fichier de préférences à part, hors de la liste de sauvegarde Android :
     * c'est une trace d'usage, elle ne quitte pas le téléphone.
     */
    internal fun retenirDernierJeu(emoji: String, nom: String) {
        accueilPrefs().edit()
            .putString(PREF_DERNIER_JEU_NOM, nom)
            .putString(PREF_DERNIER_JEU_EMOJI, emoji)
            .apply()
    }

    /** Ouvre l'onglet Spiller, et le jeu [nom] dedans s'il est donné. */
    private fun ouvrirSpiller(nom: String? = null) {
        jeuDemande = nom
        revisionDemandee = false
        allerAOnglet(TAB_SPILLER)
    }

    /** Ouvre la Boîte de Leitner avec sa séance de révision déjà lancée. */
    private fun lancerRevisionDepuisAccueil() {
        jeuDemande = JEU_LEITNER
        revisionDemandee = true
        allerAOnglet(TAB_SPILLER)
    }

    private fun carteAccueil(fond: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(enDp(18), enDp(16), enDp(18), enDp(16))
        background = GradientDrawable().apply {
            cornerRadius = enDp(16).toFloat()
            setColor(fond)
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = enDp(12) }
        isClickable = true
        isFocusable = true
    }

    private fun texteAccueil(texte: CharSequence, taille: Float, couleur: Int, gras: Boolean = false) =
        TextView(this).apply {
            text = texte
            textSize = taille
            setTextColor(couleur)
            if (gras) setTypeface(null, Typeface.BOLD)
            setLineSpacing(0f, 1.15f)
        }

    private fun boutonAccueil(libelle: String, fond: Int, encre: Int, action: () -> Unit) =
        Button(this).apply {
            text = libelle
            textSize = 15f
            isAllCaps = false
            setTextColor(encre)
            background = GradientDrawable().apply {
                cornerRadius = enDp(22).toFloat()
                setColor(fond)
            }
            setPadding(enDp(20), enDp(8), enDp(20), enDp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = enDp(12) }
            setOnClickListener { action() }
        }

    /**
     * L'accueil d'un utilisateur installé : quatre cartes, de la plus urgente
     * à la plus lointaine. Les cartes à revoir d'abord, parce que la
     * répétition espacée ne marche que si l'on revient le jour dit ; le mot du
     * jour ensuite ; le dernier jeu ; la progression, qui se regarde plutôt
     * qu'elle ne s'agit.
     *
     * Le mot du jour et la progression lisent le fichier d'usage, soit
     * plusieurs dizaines de milliers d'entrées : ils se remplissent sur un fil
     * à part pour ne pas figer l'onglet à chaque retour, qui le reconstruit.
     */
    private fun creerAujourdhui(): LinearLayout {
        val colonne = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val encre = Color.parseColor("#1C1C1C")
        val gris = Color.parseColor("#6B6B6B")

        colonne.addView(texteAccueil("Moien ! 👋", 24f, encre, gras = true))
        colonne.addView(texteAccueil(
            java.util.Locale.getDefault().let { langue ->
                // L'ordre du jour et du mois suit la langue du téléphone :
                // « samedi 3 octobre », « Saturday, October 3 ».
                java.text.SimpleDateFormat(
                    android.text.format.DateFormat.getBestDateTimePattern(langue, "EEEEdMMMM"),
                    langue
                ).format(java.util.Date()).replaceFirstChar { it.titlecase(langue) }
            },
            14f, gris
        ).apply { setPadding(0, 0, 0, enDp(14)) })

        // 1. Les cartes à revoir. Le nombre est celui de la file du jour,
        // plafonnée comme dans Spiller : jamais l'arriéré (voir majBanniereCarnet).
        val total = Carnet.taille(this)
        val dues = Carnet.aRevoir(this)
        val violet = Carnet.COULEUR
        colonne.addView(carteAccueil(violet).apply {
            val blanc = Color.WHITE
            val pale = Color.parseColor("#E8E0FF")
            when {
                dues > 0 -> {
                    addView(texteAccueil(
                        resources.getQuantityString(R.plurals.accueil_cartes_a_revoir, dues, dues),
                        20f, blanc, gras = true))
                    addView(texteAccueil(getString(R.string.sa_quelques_minutes_suffisent), 14f, pale))
                    addView(boutonAccueil(getString(R.string.sa_reviser_maintenant), blanc, violet) {
                        lancerRevisionDepuisAccueil()
                    })
                    setOnClickListener { lancerRevisionDepuisAccueil() }
                }
                total > 0 -> {
                    addView(texteAccueil(getString(R.string.sa_rien_revoir_aujourd_hui), 20f, blanc, gras = true))
                    addView(texteAccueil(
                        resources.getQuantityString(R.plurals.accueil_cartes_carnet, total, total),
                        14f, pale))
                    addView(boutonAccueil(getString(R.string.sa_gagner_autres_cartes), blanc, violet) { ouvrirSpiller() })
                    setOnClickListener { ouvrirSpiller() }
                }
                else -> {
                    addView(texteAccueil(getString(R.string.sa_votre_carnet_est_vide), 20f, blanc, gras = true))
                    addView(texteAccueil(
                        getString(R.string.sa_chaque_mot_trouve_dans_un), 14f, pale))
                    addView(boutonAccueil(getString(R.string.sa_jouer), blanc, violet) { ouvrirSpiller() })
                    setOnClickListener { ouvrirSpiller() }
                }
            }
        })

        // 2. Le mot du jour, rempli en arrière-plan
        val tvMot = texteAccueil("…", 30f, encre, gras = true)
        val tvGlose = texteAccueil("", 16f, gris).apply { visibility = View.GONE }
        val carteMot = carteAccueil(Color.WHITE).apply {
            addView(texteAccueil(getString(R.string.sa_mot_du_jour), 12f, Color.parseColor("#FF8C00"), gras = true).apply {
                letterSpacing = 0.1f
                setPadding(0, 0, 0, enDp(4))
            })
            addView(tvMot)
            addView(tvGlose)
            addView(texteAccueil(getString(R.string.sa_voir_dans_le_wierderbuch), 14f, Color.parseColor("#1976D2")).apply {
                setPadding(0, enDp(8), 0, 0)
            })
        }
        colonne.addView(carteMot)

        // 3. Le dernier jeu. « Rejouer » et non « Reprendre » : le jeu repart
        // d'une partie neuve, chaque grille étant calculée pour l'écran.
        val nomJeu = accueilPrefs().getString(PREF_DERNIER_JEU_NOM, null)
        val emojiJeu = accueilPrefs().getString(PREF_DERNIER_JEU_EMOJI, null)
        colonne.addView(carteAccueil(Color.WHITE).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(texteAccueil(emojiJeu ?: "🎮", 30f, encre).apply {
                setPadding(0, 0, enDp(14), 0)
            })
            addView(LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                if (nomJeu != null) {
                    addView(texteAccueil(getString(R.string.sa_rejouer_2, nomJeu), 18f, encre, gras = true))
                    addView(texteAccueil(getString(R.string.sa_votre_dernier_jeu), 14f, gris))
                } else {
                    addView(texteAccueil(getString(R.string.sa_choisir_un_jeu), 18f, encre, gras = true))
                    addView(texteAccueil(getString(R.string.sa_sept_jeux_pour_apprendre_les), 14f, gris))
                }
            })
            addView(texteAccueil("›", 26f, gris))
            setOnClickListener { ouvrirSpiller(nomJeu) }
        })

        // 4. La progression, remplie en arrière-plan
        val tvNiveau = texteAccueil("…", 18f, encre, gras = true)
        val tvReste = texteAccueil("", 14f, gris)
        val zoneBarre = FrameLayout(this)
        colonne.addView(carteAccueil(Color.WHITE).apply {
            addView(tvNiveau)
            addView(zoneBarre)
            addView(tvReste)
            setOnClickListener { allerAOnglet(TAB_STATS) }
        })

        Thread {
            val (mot, _) = getWordOfTheDay()
            val forme = formeAffichee(mot)
            val glose = TranslationDictionary.traduire(this, mot)
            val decouverts = loadVocabularyStats().wordsDiscovered
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                tvMot.text = forme
                if (glose != null) {
                    tvGlose.text = getString(R.string.sa_en_francais_3, glose)
                    tvGlose.visibility = View.VISIBLE
                }
                carteMot.setOnClickListener {
                    rechercheDemandee = forme
                    allerAOnglet(TAB_WIERDERBUCH)
                }

                val niveau = LuxLevels.LEVELS[getCurrentLevelIndex(decouverts)]
                tvNiveau.text = "${niveau.emoji}  ${niveau.name}"
                creerBarreNiveau(decouverts)?.let { zoneBarre.addView(it) }
                val (suivant, reste) = getNextLevelInfo(decouverts)
                tvReste.text = if (reste <= 0) getString(R.string.sa_vous_avez_atteint_le_plus)
                    else resources.getQuantityString(R.plurals.encore_mots_avant, reste, nombre(reste), suivant) + " ›"
            }
        }.start()

        // Sur une tablette couchée, les quatre cartes tiennent en deux rangées
        // sous la salutation et la date.
        if (DeuxColonnes.actives(this)) DeuxColonnes.parPaires(colonne, 2)

        return colonne
    }

    /**
     * Barre de progression vers le palier suivant, ou `null` au plus haut
     * niveau. Partagée par l'accueil et « Mäi Lëtzebuergesch », qui doivent
     * montrer la même chose.
     */
    private fun creerBarreNiveau(motsDecouverts: Int): View? {
        val index = getCurrentLevelIndex(motsDecouverts)
        if (index == LuxLevels.MAX_INDEX) return null
        val seuils = calculateGaussianThresholds()
        val depuis = seuils[index]
        val etape = (seuils[index + 1] - depuis).coerceAtLeast(1)
        val fait = (motsDecouverts - depuis).coerceIn(0, etape)
        return android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = etape
            progress = fait
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#0E6E76"))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#DDE6E7"))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, enDp(10)).apply {
                topMargin = enDp(8); bottomMargin = enDp(8)
            }
            contentDescription = resources.getQuantityString(R.plurals.mots_jusqu_au_niveau, fait, fait, nombre(etape))
        }
    }

    /**
     * Invitation au correcteur sur l'accueil, pour qui a installé le clavier
     * sans jamais ouvrir le volet de configuration. Un toucher ouvre
     * directement le bon écran d'Android ; « Plus tard » la retire de
     * l'accueil, la carte complète restant dans le volet.
     */
    private fun carteCorrecteurAccueil(): LinearLayout {
        val encre = Color.parseColor("#1C1C1C")
        val gris = Color.parseColor("#6B6B6B")
        val bleu = Color.parseColor("#0080FF")
        val coupe = isSpellCheckerChosenButOff()
        val carte = carteAccueil(Color.WHITE)
        return carte.apply {
            addView(texteAccueil(getString(R.string.sa_fini_le_trait_rouge_sous), 18f, encre, gras = true))
            addView(texteAccueil(
                if (coupe) getString(R.string.sa_la_correction_orthographique_est_coupee)
                else getString(R.string.sa_android_souligne_vos_mots_luxembourgeois),
                14f, gris).apply { setPadding(0, enDp(4), 0, 0) })
            // L'avertissement d'Android parle de mots de passe et de cartes
            // bancaires : dit d'avance, il fait moins peur.
            if (!coupe) addView(texteAccueil(
                getString(R.string.sa_android_affichera_un_avertissement_comme),
                12f, Color.parseColor("#9E9E9E")).apply { setPadding(0, enDp(6), 0, 0) })
            addView(LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, enDp(12), 0, 0)
                addView(boutonAccueil(if (coupe) getString(R.string.sa_rallumer) else getString(R.string.sa_activer), bleu, Color.WHITE) {
                    openSpellCheckerSettings()
                }.apply { (layoutParams as LinearLayout.LayoutParams).topMargin = 0 })
                addView(texteAccueil(getString(R.string.sa_plus_tard), 15f, gris).apply {
                    setPadding(enDp(20), enDp(12), enDp(20), enDp(12))
                    setOnClickListener {
                        onboardingPrefs().edit()
                            .putBoolean(PREF_CORRECTEUR_ACCUEIL_MASQUE, true).apply()
                        (carte.parent as? ViewGroup)?.removeView(carte)
                    }
                })
            })
            setOnClickListener { openSpellCheckerSettings() }
        }
    }

    /**
     * La ligne qui remplace, en mode « Aujourd'hui », toute la configuration :
     * elle la déplie et la replie sur place.
     */
    private fun ligneConfiguration(volet: LinearLayout): LinearLayout {
        val fleche = texteAccueil("›", 22f, Color.parseColor("#6B6B6B"))
        return carteAccueil(Color.WHITE).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(texteAccueil("✅", 20f, Color.BLACK).apply { setPadding(0, 0, enDp(12), 0) })
            addView(LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(texteAccueil(getString(R.string.sa_clavier_installe), 16f, Color.parseColor("#1C1C1C"), gras = true))
                addView(texteAccueil(getString(R.string.sa_configuration_reglages_changer_de_clavier), 13f,
                    Color.parseColor("#6B6B6B")))
            })
            addView(fleche)
            setOnClickListener {
                val ouvrir = volet.visibility != View.VISIBLE
                volet.visibility = if (ouvrir) View.VISIBLE else View.GONE
                fleche.rotation = if (ouvrir) 90f else 0f
            }
        }
    }

    /**
     * Ligne d'accès à une page de référence, ouverte en plein écran.
     *
     * Volontairement plus discrète que les cartes de configuration au-dessus :
     * ces deux pages ne demandent aucune action, elles répondent à une question
     * que l'utilisateur ne se pose pas encore.
     */
    private fun createReferenceLink(
        emoji: String,
        titre: String,
        resume: String,
        page: String
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.WHITE)
        setPadding(20, 18, 20, 18)
        isClickable = true
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        addView(TextView(this@SettingsActivity).apply {
            text = emoji
            textSize = 22f
            setPadding(0, 0, 18, 0)
        })
        addView(LinearLayout(this@SettingsActivity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            addView(TextView(this@SettingsActivity).apply {
                text = titre
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#1C1C1C"))
            })
            addView(TextView(this@SettingsActivity).apply {
                text = resume
                textSize = 13f
                setTextColor(Color.parseColor("#888888"))
            })
        })
        addView(TextView(this@SettingsActivity).apply {
            text = "›"
            textSize = 24f
            setTextColor(Color.parseColor("#BBBBBB"))
        })

        setOnClickListener {
            SheetFragment.pour(page).show(supportFragmentManager, "sheet_$page")
        }
    }
    
    /**
     * Amène le pager sur un onglet, en restant dans le cycle courant.
     *
     * Le ViewPager répète les onglets sur une longue plage virtuelle pour que
     * le balayage puisse tourner dans les deux sens indéfiniment ; une position
     * absolue n'a donc de sens que relativement à celle où l'on se trouve.
     */
    private fun allerAOnglet(onglet: Int) {
        val position = viewPager.currentItem
        val actuel = position % SettingsPagerAdapter.REAL_COUNT
        val avant = (onglet - actuel + SettingsPagerAdapter.REAL_COUNT) % SettingsPagerAdapter.REAL_COUNT
        val arriere = (actuel - onglet + SettingsPagerAdapter.REAL_COUNT) % SettingsPagerAdapter.REAL_COUNT
        val cible = if (avant <= arriere) position + avant else position - arriere
        viewPager.setCurrentItem(cible, avant.coerceAtMost(arriere) <= 1)
    }

    /** Conversion en pixels d'une dimension exprimée en dp. */
    private fun enDp(valeur: Int): Int = (valeur * resources.displayMetrics.density).toInt()

    /** Entier avec l'espace des milliers : « 38 442 » et non « 38442 ». */
    private fun nombre(n: Int): String =
        java.text.NumberFormat.getIntegerInstance(java.util.Locale.FRANCE).format(n)

    /**
     * Une couleur `#RRGGBB` reprise avec l'opacité demandée.
     *
     * Concaténer les deux chaînes — `"$couleur20"` — ne donne pas ce qu'on
     * croit : `Color.parseColor` lit huit chiffres comme `#AARRGGBB`, si bien
     * que `"#4CAF50" + "20"` devient un alpha de 0x4C sur le brun `#AF5020`.
     * L'astuce se lit comme un ajout de transparence et produit une autre
     * teinte, opaque.
     */
    fun avecOpacite(couleur: String, alpha: Int): Int =
        (alpha shl 24) or (Color.parseColor(couleur) and 0x00FFFFFF)

    /**
     * Un mot a-t-il déjà été écrit avec le clavier ? Le jalon est posé par le
     * service de saisie au premier mot validé, y compris dans le champ de test
     * de l'application : c'est donc le seul signal honnête pour cocher la
     * troisième étape, qui n'est pas un réglage mais un essai.
     */
    private fun aEcritUnMot(): Boolean = onboardingPrefs().contains("funnel_first_word")

    /**
     * Carte à coins arrondis, réservée à l'onglet Démarrage. [createCard] reste
     * la carte carrée utilisée par tous les autres onglets : les arrondir tous
     * d'un coup dépasserait ce qui a été demandé ici.
     */
    private fun createRoundedCard(backgroundColor: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(enDp(18), enDp(18), enDp(18), enDp(18))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = enDp(16).toFloat()
                setColor(Color.parseColor(backgroundColor))
            }
        }
    }

    /**
     * Anneau « n/3 » : arc proportionnel au nombre d'étapes faites, chiffre au
     * centre. Il remplace l'ancienne barre de progression, qui disait où on en
     * était mais pas ce qu'il restait.
     */
    private class ProgressRingView(context: Context, private val total: Int) : View(context) {
        var done: Int = 0
            set(value) {
                field = value
                invalidate()
            }

        private val densite = context.resources.displayMetrics.density
        private val piste = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f * densite
            color = Color.parseColor("#E8EAED")
        }
        private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f * densite
            strokeCap = Paint.Cap.ROUND
        }
        private val encre = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 15f * densite
            typeface = Typeface.DEFAULT_BOLD
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val marge = piste.strokeWidth / 2f
            val cadre = android.graphics.RectF(marge, marge, width - marge, height - marge)
            val couleur = Color.parseColor(if (done >= total) "#4CAF50" else "#0080FF")
            arc.color = couleur
            encre.color = couleur
            canvas.drawArc(cadre, 0f, 360f, false, piste)
            if (done > 0) {
                canvas.drawArc(cadre, -90f, 360f * done / total, false, arc)
            }
            val ligneDeBase = height / 2f - (encre.descent() + encre.ascent()) / 2f
            canvas.drawText("$done/$total", width / 2f, ligneDeBase, encre)
        }
    }

    /**
     * Bandeau vert de réussite. Deux textes distincts selon que l'utilisateur
     * a déjà écrit un mot ou non : afficher « Tout est prêt » au-dessus d'un
     * anneau qui affiche 2/3 ferait dire deux choses différentes au même écran.
     */
    private fun createReadyBanner(aDejaEcrit: Boolean): LinearLayout {
        val banner = createRoundedCard("#4CAF50").apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val icone = TextView(this).apply {
            text = "✅"
            textSize = 24f
            setPadding(0, 0, enDp(14), 0)
        }

        val textes = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }

        val titre = TextView(this).apply {
            tag = "bandeau_pret_titre"
            text = if (aDejaEcrit) getString(R.string.sa_tout_est_pret) else getString(R.string.sa_clavier_en_place)
            textSize = 17f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }

        val sousTitre = TextView(this).apply {
            tag = "bandeau_pret_sous_titre"
            text = if (aDejaEcrit) getString(R.string.sa_vous_pouvez_taper_en_letzebuergesch)
                   else getString(R.string.sa_ecrivez_un_mot_pour_terminer)
            textSize = 13f
            setTextColor(Color.parseColor("#E8F5E9"))
            setPadding(0, enDp(2), 0, 0)
        }

        textes.addView(titre)
        textes.addView(sousTitre)
        banner.addView(icone)
        banner.addView(textes)
        return banner
    }

    /**
     * Carte « Revenir au clavier luxembourgeois », en tête de l'onglet
     * Démarrage quand le clavier est installé mais qu'un autre est en service.
     *
     * Elle existe parce qu'Android ne laisse pas un clavier se réactiver
     * lui-même : `showInputMethodPicker()` ne fonctionne que depuis le clavier
     * courant ou depuis une activité au premier plan. Une fois parti, le
     * clavier ne peut plus rien pour l'utilisateur ; seule l'application le
     * peut, et encore faut-il qu'elle le propose au lieu de rejouer un tunnel
     * de configuration en trois étapes à quelqu'un qui a tout configuré.
     *
     * Le clavier n'est ni désinstallé ni désactivé dans cet état, et le texte
     * le dit : la seule chose à faire est de le rechoisir. Un seul bouton,
     * pleine largeur, et l'autre chemin — l'icône de la barre de navigation —
     * rappelé en dessous pour la fois d'après, puisque c'est celui qui marche
     * sans quitter l'application où l'on écrit.
     *
     * Rien à rafraîchir à la main : [OnboardingFragment] observe
     * `DEFAULT_INPUT_METHOD` et reconstruit l'onglet dès que le choix est fait,
     * ce qui fait disparaître la carte et apparaître le bandeau de réussite.
     *
     * Les tailles de texte sont au-dessus de celles des autres cartes de
     * l'onglet : c'est l'écran que lit quelqu'un qui a perdu son clavier et qui
     * ne sait pas pourquoi, et rien d'utile n'y descend sous 16 sp.
     */
    private fun createRetourClavierCard(): LinearLayout {
        val card = createRoundedCard("#FFF3E0")

        val titre = TextView(this).apply {
            text = getString(R.string.sa_revenir_au_clavier_luxembourgeois)
            textSize = 19f
            setTextColor(Color.parseColor("#E65100"))
            setTypeface(null, Typeface.BOLD)
            setLineSpacing(0f, 1.25f)
            setPadding(0, 0, 0, enDp(10))
        }

        val explication = TextView(this).apply {
            text = getString(R.string.sa_en_ce_moment_est_un)
            textSize = 16f
            setTextColor(Color.parseColor("#5D4037"))
            setLineSpacing(0f, 1.35f)
            setPadding(0, 0, 0, enDp(16))
        }

        val bouton = Button(this).apply {
            text = getString(R.string.sa_choisir_le_clavier_luxembourgeois)
            textSize = 17f
            setBackgroundColor(Color.parseColor("#0080FF"))
            setTextColor(Color.WHITE)
            minHeight = enDp(56)
            setPadding(enDp(16), enDp(14), enDp(16), enDp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { openInputMethodPicker() }
        }

        val autreChemin = TextView(this).apply {
            text = getString(R.string.sa_sans_passer_par_ici_pendant)
            textSize = 16f
            setTextColor(Color.parseColor("#795548"))
            setLineSpacing(0f, 1.35f)
            setPadding(0, enDp(14), 0, 0)
        }

        card.addView(titre)
        card.addView(explication)
        card.addView(bouton)
        card.addView(autreChemin)
        return card
    }

    /**
     * (Re)construit la carte de configuration dans son conteneur. Passer par le
     * conteneur plutôt que par un rafraîchissement complet de l'onglet évite de
     * reconstruire le clavier d'essai à chaque fois qu'une étape se déplie.
     */
    private fun remplirCarteConfig(
        conteneur: LinearLayout,
        isEnabled: Boolean,
        isSelected: Boolean,
        hasCompletedBefore: Boolean,
        showIncompleteNudge: Boolean
    ) {
        conteneur.removeAllViews()
        conteneur.addView(
            createQuickSetupCard(isEnabled, isSelected, hasCompletedBefore, showIncompleteNudge) {
                remplirCarteConfig(conteneur, isEnabled, isSelected, hasCompletedBefore, showIncompleteNudge)
            }
        )
    }

    /**
     * Carte « Configuration rapide » : une ligne compacte par étape, et une
     * seule dépliée à la fois — celle qui reste à faire, sauf si l'utilisateur
     * en ouvre une autre.
     *
     * Le repli est piloté par l'état, jamais systématique : la description de
     * l'étape en cours, l'avertissement Android et le rappel des deux
     * validations successives sont ce qui fait passer l'utilisateur à travers
     * les réglages système. Les réduire à un chevron ferait gagner de la place
     * là où le tunnel se joue.
     */
    private fun createQuickSetupCard(
        isEnabled: Boolean,
        isSelected: Boolean,
        hasCompletedBefore: Boolean,
        showIncompleteNudge: Boolean,
        onRebuild: () -> Unit
    ): LinearLayout {
        val card = createRoundedCard("#FFFFFF")

        val etape3Faite = isEnabled && isSelected && aEcritUnMot()
        val faites = listOf(isEnabled, isSelected, etape3Faite).count { it }
        val toutFait = faites == 3

        // === En-tête : titre, état, anneau ===
        val entete = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val textesEntete = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }

        val titre = TextView(this).apply {
            text = getString(R.string.sa_configuration_rapide)
            textSize = 18f
            setTextColor(Color.parseColor("#1C1C1C"))
            setTypeface(null, Typeface.BOLD)
        }

        val sousTitre = TextView(this).apply {
            text = when {
                toutFait -> getString(R.string.sa_les_etapes_sont_faites)
                isEnabled && isSelected -> getString(R.string.sa_plus_qu_essayer)
                // Sans « sans doute après une mise à jour » : la cause la plus
                // fréquente est un détour volontaire par un autre clavier, et
                // annoncer un incident système à quelqu'un qui a simplement
                // changé de clavier l'envoie chercher au mauvais endroit.
                hasCompletedBefore && isEnabled -> getString(R.string.sa_le_clavier_luxembourgeois_est_plus_2)
                hasCompletedBefore -> getString(R.string.sa_le_clavier_luxembourgeois_est_plus)
                isEnabled -> getString(R.string.sa_plus_qu_une_etape)
                else -> getString(R.string.sa_etapes_pour_taper_en_letzebuergesch)
            }
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, enDp(3), enDp(12), 0)
            setLineSpacing(0f, 1.25f)
        }

        val anneau = ProgressRingView(this, 3).apply {
            done = faites
            contentDescription = resources.getQuantityString(R.plurals.etapes_terminees, faites, faites)
            layoutParams = LinearLayout.LayoutParams(enDp(52), enDp(52))
        }

        textesEntete.addView(titre)
        textesEntete.addView(sousTitre)
        entete.addView(textesEntete)
        entete.addView(anneau)
        card.addView(entete)

        // Une fois les 3 étapes faites, la carte n'a plus rien à demander :
        // elle se replie sur son en-tête et laisse la place au reste de
        // l'onglet, au lieu de garder trois lignes cochées en haut de l'écran
        // pour toujours.
        if (toutFait && !detailsConfigDeplies) {
            card.addView(TextView(this).apply {
                text = getString(R.string.sa_voir_les_etapes)
                textSize = 14f
                setTextColor(Color.parseColor("#0080FF"))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, enDp(14), 0, 0)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    detailsConfigDeplies = true
                    onRebuild()
                }
            })
            return card
        }

        // === Étape dépliée : celle qui reste à faire, sauf choix contraire ===
        val etapeParDefaut = when {
            !isEnabled -> 0
            !isSelected -> 1
            !etape3Faite -> 2
            else -> -1
        }
        val ouverte = etapeConfigOuverte ?: etapeParDefaut

        fun basculer(index: Int): () -> Unit = {
            etapeConfigOuverte = if (ouverte == index) -1 else index
            onRebuild()
        }

        fun corps(): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(enDp(40), 0, 0, enDp(16))
        }

        fun texte(contenu: String): TextView = TextView(this).apply {
            text = contenu
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            setLineSpacing(0f, 1.35f)
            setPadding(0, 0, 0, enDp(12))
        }

        fun encart(fond: String, encre: String, contenu: String): TextView = TextView(this).apply {
            text = contenu
            textSize = 13f
            setTextColor(Color.parseColor(encre))
            setLineSpacing(0f, 1.3f)
            setPadding(enDp(12), enDp(12), enDp(12), enDp(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = enDp(10).toFloat()
                setColor(Color.parseColor(fond))
            }
        }

        // Le libellé porte le fait que le bouton quitte l'application : un
        // chevron seul laisserait croire à une navigation interne, alors que
        // ces deux étapes se terminent dans les réglages Android.
        fun bouton(libelle: String, action: () -> Unit): Button = Button(this).apply {
            text = libelle
            textSize = 15f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setPadding(enDp(20), enDp(14), enDp(20), enDp(14))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = enDp(10).toFloat()
                setColor(Color.parseColor("#0080FF"))
            }
            setOnClickListener { action() }
        }

        // === Étape 1 : activer le clavier ===
        val corps1 = corps().apply {
            addView(texte(getString(R.string.sa_trouvez_letzebuergesch_clavier_dans_ecran)))
            when {
                showIncompleteNudge -> addView(encart("#FFF3E0", "#BF360C",
                    getString(R.string.sa_presque_validez_bien_les_avertissements)))
                !isEnabled -> addView(encart("#FFF8E1", "#5D4037",
                    getString(R.string.sa_android_affiche_un_avertissement_de)))
            }
            if (!isEnabled) {
                addView(TextView(this@SettingsActivity).apply {
                    text = getString(R.string.sa_lire_la_politique_de_confidentialite)
                    textSize = 13f
                    setTextColor(Color.parseColor("#0080FF"))
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, enDp(10), 0, 0)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { openPrivacyPolicy() }
                })
            }
            addView(createSpacing(12))
            addView(bouton(
                if (isEnabled) getString(R.string.sa_rouvrir_les_reglages_android) else getString(R.string.sa_ouvrir_les_reglages_android)
            ) { openKeyboardSettings() })
        }

        val ligne1 = createSetupRow(
            numero = 1,
            faite = isEnabled,
            verrouillee = false,
            titre = getString(R.string.sa_activer_le_clavier),
            sousTitre = if (isEnabled) getString(R.string.sa_le_clavier_est_active) else getString(R.string.sa_dans_les_reglages_android),
            ouverte = ouverte == 0,
            contenu = corps1,
            onToggle = basculer(0)
        )

        // === Étape 2 : sélectionner le clavier ===
        val corps2 = corps().apply {
            addView(texte(getString(R.string.sa_choisissez_letzebuergesch_clavier_dans_la)))
            addView(bouton(getString(R.string.sa_ouvrir_le_selecteur_de_claviers)) { openInputMethodPicker() })
        }

        val ligne2 = createSetupRow(
            numero = 2,
            faite = isSelected,
            verrouillee = !isEnabled,
            titre = getString(R.string.sa_selectionner_le_clavier_2),
            sousTitre = when {
                isSelected -> getString(R.string.sa_letzebuergesch_clavier_est_selectionne)
                !isEnabled -> getString(R.string.sa_terminez_abord_etape)
                else -> getString(R.string.sa_choisissez_le_dans_la_liste)
            },
            ouverte = ouverte == 1,
            contenu = corps2,
            onToggle = basculer(1)
        )

        // === Étape 3 : essayer le clavier ===
        val champTest = EditText(this).apply {
            tag = "onboarding_test_field"
            hint = getString(R.string.sa_schreift_op_letzebuergesch_ecrivez_en)
            // Sans correcteur système, comme le champ du clavier d'essai : celui
            // de Gboard, encore choisi à ce stade, soulignait en rouge « Moien »,
            // le mot même que l'étape demande d'écrire. Notre clavier ignore ce
            // drapeau et propose ses suggestions comme ailleurs.
            // Multiligne comme avant : sans ce drapeau, l'indication tenait sur
            // une ligne coupée et la touche Entrée devenait « Terminé ».
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            textSize = 16f
            setPadding(enDp(14), enDp(14), enDp(14), enDp(14))
            minHeight = enDp(56)
            setTextColor(Color.parseColor("#1C1C1C"))
            setHintTextColor(Color.parseColor("#999999"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = enDp(10).toFloat()
                setColor(Color.parseColor("#F7F7F7"))
                setStroke(enDp(1), Color.parseColor("#E0E0E0"))
            }
            // Force le scroll vers ce champ quand il obtient le focus
            setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    // Délai : laisser le clavier s'ouvrir avant de recadrer
                    Handler(Looper.getMainLooper()).postDelayed({
                        view.parent?.requestChildFocus(view, view)
                    }, 300)
                }
            }
        }

        val corps3 = corps().apply {
            addView(texte(getString(R.string.sa_ecrivez_moien_alleguer_et_regardez)))
            addView(champTest)
        }

        val ligne3 = createSetupRow(
            numero = 3,
            faite = etape3Faite,
            verrouillee = !isEnabled || !isSelected,
            titre = getString(R.string.sa_essayer_le_clavier),
            sousTitre = when {
                etape3Faite -> getString(R.string.sa_vous_avez_ecrit_vos_premiers)
                !isEnabled || !isSelected -> getString(R.string.sa_terminez_les_etapes_et)
                else -> getString(R.string.sa_ecrivez_un_mot_pour_verifier)
            },
            ouverte = ouverte == 2,
            contenu = corps3,
            onToggle = basculer(2)
        )

        // La troisième étape se coche pendant la frappe, sans reconstruire la
        // carte : reconstruire ferait perdre le focus et refermerait le clavier
        // au premier mot écrit. Le jalon lu est celui du service de saisie, donc
        // la pastille ne s'allume pas sur un texte collé ou tapé avec un autre
        // clavier — et ne se rallume pas à faux au rafraîchissement suivant.
        //
        // Le texte du champ change avant que le service n'ait posé son jalon :
        // relire le jalon à chaque frappe laissait l'étape à 2/3 après le
        // premier mot, et ne la cochait qu'au deuxième. On écoute donc le jalon
        // lui-même ; le service tourne dans le même processus, l'écoute est
        // prévenue dès son écriture. Les frappes, elles, repoussent la pochette.
        if (!etape3Faite) {
            fun cocher() {
                if (anneau.done >= 3) return
                marquerEtapeFaite(ligne3)
                anneau.done = 3
                sousTitre.text = getString(R.string.sa_les_etapes_sont_faites)
                window.decorView.findViewWithTag<TextView>("bandeau_pret_titre")
                    ?.text = getString(R.string.sa_tout_est_pret)
                window.decorView.findViewWithTag<TextView>("bandeau_pret_sous_titre")
                    ?.text = getString(R.string.sa_vous_pouvez_taper_en_letzebuergesch)
            }
            ecoutePremierMot?.let { onboardingPrefs().unregisterOnSharedPreferenceChangeListener(it) }
            ecoutePremierMot = SharedPreferences.OnSharedPreferenceChangeListener { _, cle ->
                if (cle == "funnel_first_word" && aEcritUnMot()) {
                    cocher()
                    planifierPochette()
                }
            }.also { onboardingPrefs().registerOnSharedPreferenceChangeListener(it) }
            champTest.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (s.isNullOrEmpty() || !aEcritUnMot()) return
                    cocher()
                    planifierPochette()
                }
            })
        }

        card.addView(createSpacing(6))
        card.addView(ligne1)
        card.addView(createSetupSeparator())
        card.addView(ligne2)
        card.addView(createSetupSeparator())
        card.addView(ligne3)

        if (toutFait) {
            card.addView(TextView(this).apply {
                text = getString(R.string.sa_masquer_le_detail)
                textSize = 14f
                setTextColor(Color.parseColor("#0080FF"))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, enDp(12), 0, 0)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    detailsConfigDeplies = false
                    onRebuild()
                }
            })
        }

        return card
    }

    /**
     * Une ligne d'étape : pastille d'état, titre, sous-titre d'une ligne, et
     * son contenu déplié en dessous. Verrouillée, la ligne est grisée et sans
     * chevron — pas de cadenas : c'est une icône de plus pour dire ce que le
     * gris dit déjà.
     */
    private fun createSetupRow(
        numero: Int,
        faite: Boolean,
        verrouillee: Boolean,
        titre: String,
        sousTitre: String,
        ouverte: Boolean,
        contenu: View?,
        onToggle: () -> Unit
    ): LinearLayout {
        val bloc = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val ligne = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // 56 dp : au-delà des 48 dp de cible tactile minimale, la ligne
            // reste confortable à viser pour une main qui tremble
            minimumHeight = enDp(56)
            setPadding(0, enDp(12), 0, enDp(12))
            contentDescription = when {
                faite -> getString(R.string.sa_etape_terminee, titre)
                verrouillee -> getString(R.string.sa_etape_verrouillee, titre)
                else -> titre
            }
            if (!verrouillee) {
                isClickable = true
                isFocusable = true
                setOnClickListener { onToggle() }
            }
        }

        val pastille = TextView(this).apply {
            tag = "pastille_etape"
            text = if (faite) "✓" else numero.toString()
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(when {
                faite -> Color.WHITE
                verrouillee -> Color.parseColor("#9E9E9E")
                else -> Color.parseColor("#0080FF")
            })
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(when {
                    faite -> "#4CAF50"
                    verrouillee -> "#F0F0F0"
                    else -> "#E3F2FD"
                }))
            }
            layoutParams = LinearLayout.LayoutParams(enDp(28), enDp(28)).apply {
                rightMargin = enDp(12)
            }
        }

        val colonne = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }

        colonne.addView(TextView(this).apply {
            text = titre
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor(if (verrouillee) "#9E9E9E" else "#1C1C1C"))
        })

        colonne.addView(TextView(this).apply {
            text = sousTitre
            textSize = 13f
            setTextColor(Color.parseColor(if (verrouillee) "#BDBDBD" else "#666666"))
            setPadding(0, enDp(2), enDp(8), 0)
        })

        val chevron = TextView(this).apply {
            text = "›"
            textSize = 22f
            setTextColor(Color.parseColor("#B0B0B0"))
            rotation = if (ouverte) 90f else 0f
            visibility = if (verrouillee) View.INVISIBLE else View.VISIBLE
        }

        ligne.addView(pastille)
        ligne.addView(colonne)
        ligne.addView(chevron)
        bloc.addView(ligne)

        if (ouverte && !verrouillee && contenu != null) {
            bloc.addView(contenu)
        }

        return bloc
    }

    /** Filet de séparation entre deux lignes, aligné sur le texte. */
    private fun createSetupSeparator(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1
        ).apply { leftMargin = enDp(40) }
        setBackgroundColor(Color.parseColor("#EEEEEE"))
    }

    /** Passe la pastille d'une ligne d'étape au vert, sans reconstruire la carte. */
    private fun marquerEtapeFaite(ligne: View) {
        val pastille = ligne.findViewWithTag<TextView>("pastille_etape") ?: return
        pastille.text = "✓"
        pastille.setTextColor(Color.WHITE)
        (pastille.background as? GradientDrawable)?.setColor(Color.parseColor("#4CAF50"))
    }

    // Fonction pour créer une card d'étape
    /**
     * Carte du correcteur orthographique. Elle emprunte l'habillage des étapes
     * de configuration (badge, icône, titre) mais pas leur mécanique : ce n'est
     * pas une étape du parcours numéroté, et sa mise en page diverge sur trois
     * points.
     *
     * La carte entière est la cible de clic, signalée par un chevron : un
     * bouton pleine largeur donnait à une fonction optionnelle le même poids
     * visuel qu'aux trois étapes qui, elles, conditionnent l'usage du clavier.
     *
     * Ne reste visible que la promesse, plus l'avertissement qu'Android
     * affichera. Ce dernier ne peut pas être replié : le dialogue système
     * prévient que le correcteur « peut collecter tout le texte que vous tapez,
     * y compris des données personnelles comme les mots de passe », et c'est là
     * que l'utilisateur non prévenu abandonne. Une ligne le désamorce.
     *
     * La marche à suivre, elle, se déplie à la demande, sous un intitulé qui
     * annonce ce qu'on y trouve : elle ne sert qu'une fois l'écran système
     * ouvert, où la sélection se fait dans un sous-menu (« Correcteur par
     * défaut ») que rien ne signale.
     */
    /**
     * Interrupteur de la correction automatique de la Groussschreiwung.
     *
     * Le luxembourgeois capitalise tous ses substantifs, et le clavier rétablit
     * la majuscule quand le contexte l'atteste — « an der rue » devient « an
     * der Rue » à la validation du mot. La fonction est active par défaut :
     * elle n'a d'intérêt que si elle agit sans qu'on la cherche.
     *
     * Mais une correction imposée qu'on ne peut pas éteindre est une fonction
     * subie, et c'est le seul endroit de l'application où l'on peut la couper.
     */
    private fun createGroussschreiwungCard(): LinearLayout {
        val card = createRoundedCard("#FFFFFF")

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconText = TextView(this).apply {
            text = "🔠"
            textSize = 24f
            setPadding(0, 0, 12, 0)
        }

        val titleText = TextView(this).apply {
            text = getString(R.string.sa_majuscules_automatiques)
            textSize = 18f
            setTextColor(Color.parseColor("#333333"))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }

        val prefs = getSharedPreferences(
            KreyolInputMethodServiceRefactored.KEYBOARD_PREFS_NAME, Context.MODE_PRIVATE
        )
        val interrupteur = Switch(this).apply {
            isChecked = prefs.getBoolean(
                KreyolInputMethodServiceRefactored.PREF_AUTO_CAPITALIZE, true
            )
            setOnCheckedChangeListener { _, coche ->
                prefs.edit()
                    .putBoolean(
                        KreyolInputMethodServiceRefactored.PREF_AUTO_CAPITALIZE, coche
                    )
                    .apply()
            }
        }

        header.addView(iconText)
        header.addView(titleText)
        header.addView(interrupteur)

        val description = TextView(this).apply {
            text = getString(R.string.sa_retablit_la_majuscule_des_substantifs)
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, 10, 0, 0)
        }

        card.addView(header)
        card.addView(description)
        return card
    }

    private fun createSpellCheckerCard(): LinearLayout {
        val estActif = isSpellCheckerSelected()
        val coupe = isSpellCheckerChosenButOff()
        val card = createRoundedCard("#FFFFFF")

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 12)
        }

        val badgeView = TextView(this).apply {
            text = "✚"
            textSize = 20f
            setTextColor(Color.parseColor(if (estActif) "#4CAF50" else "#0080FF"))
            setTypeface(null, Typeface.BOLD)
            setPadding(12, 8, 12, 8)
            setBackgroundColor(Color.parseColor(if (estActif) "#E8F5E9" else "#E3F2FD"))
        }

        val iconText = TextView(this).apply {
            text = "🔤"
            textSize = 24f
            setPadding(16, 0, 12, 0)
        }

        val titleText = TextView(this).apply {
            text = getString(R.string.sa_corriger_orthographe_partout)
            textSize = 18f
            setTextColor(Color.parseColor("#333333"))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val marqueur = TextView(this).apply {
            text = if (estActif) "✓" else "›"
            textSize = if (estActif) 24f else 34f
            setTextColor(Color.parseColor(if (estActif) "#4CAF50" else "#757575"))
            setTypeface(null, Typeface.BOLD)
            setPadding(12, 0, 4, 0)
        }

        header.addView(badgeView)
        header.addView(iconText)
        header.addView(titleText)
        header.addView(marqueur)

        val descText = TextView(this).apply {
            // Le bénéfice s'énonce par ce qu'il apporte, pas par ce qu'il
            // supprime, mais le trait rouge reste nommé : c'est à lui que
            // l'utilisateur reconnaît la gêne qu'il subit tous les jours.
            text = if (estActif) {
                getString(R.string.sa_vos_mots_luxembourgeois_sont_reconnus)
            } else if (coupe) {
                getString(R.string.sa_le_correcteur_luxembourgeois_est_bien)
            } else {
                getString(R.string.sa_faites_reconnaitre_vos_mots_luxembourgeois)
            }
            textSize = 16f
            setTextColor(Color.parseColor("#666666"))
            setLineSpacing(0f, 1.3f)
            setPadding(0, 0, 0, if (estActif) 0 else 12)
        }

        card.addView(header)
        card.addView(descText)

        if (estActif) return card

        val avertissement = TextView(this).apply {
            text = getString(R.string.sa_android_vous_previendra_qu_un)
            textSize = 13f
            setTextColor(Color.parseColor("#9E9E9E"))
            setLineSpacing(0f, 1.3f)
            setPadding(0, 0, 0, 12)
        }
        // Déjà choisi et seulement coupé : Android ne repose pas la question.
        if (!coupe) card.addView(avertissement)

        val details = TextView(this).apply {
            text = if (coupe) getString(R.string.sa_dans_ecran_qui_ouvre_allumez_2) else getString(R.string.sa_dans_ecran_qui_ouvre_allumez)
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            setLineSpacing(0f, 1.35f)
            setPadding(0, 0, 0, 12)
            visibility = View.GONE
        }

        val lien = TextView(this).apply {
            text = getString(R.string.sa_ce_qu_android_va_vous)
            textSize = 14f
            setTextColor(Color.parseColor("#0080FF"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
            setOnClickListener {
                val ouvert = details.visibility == View.VISIBLE
                details.visibility = if (ouvert) View.GONE else View.VISIBLE
                text = if (ouvert) getString(R.string.sa_ce_qu_android_va_vous) else getString(R.string.sa_masquer)
            }
        }

        card.addView(details)
        card.addView(lien)

        card.isClickable = true
        card.setOnClickListener { openSpellCheckerSettings() }

        return card
    }

    private fun createStepCard(
        badge: String,
        isCompleted: Boolean,
        isLocked: Boolean,
        icon: String,
        title: String,
        description: String,
        buttonText: String,
        buttonEnabled: Boolean,
        buttonAction: () -> Unit
    ): LinearLayout {
        val card = createRoundedCard("#FFFFFF")
        
        // Appliquer une opacité si verrouillé
        if (isLocked) {
            card.alpha = 0.6f
        }
        
        // Header avec numéro et icône
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 12)
        }
        
        val badgeView = TextView(this).apply {
            text = badge
            textSize = 20f
            setTextColor(
                when {
                    isLocked -> Color.parseColor("#999999")
                    isCompleted -> Color.parseColor("#4CAF50")
                    else -> Color.parseColor("#0080FF")
                }
            )
            setTypeface(null, Typeface.BOLD)
            setPadding(12, 8, 12, 8)
            setBackgroundColor(
                when {
                    isLocked -> Color.parseColor("#F5F5F5")
                    isCompleted -> Color.parseColor("#E8F5E9")
                    else -> Color.parseColor("#E3F2FD")
                }
            )
        }
        
        val iconText = TextView(this).apply {
            text = icon
            textSize = 24f
            setPadding(16, 0, 12, 0)
        }
        
        val titleText = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(if (isLocked) Color.parseColor("#999999") else Color.parseColor("#333333"))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        
        if (isCompleted) {
            val checkIcon = TextView(this).apply {
                text = "✓"
                textSize = 24f
                setTextColor(Color.parseColor("#4CAF50"))
                setTypeface(null, Typeface.BOLD)
            }
            header.addView(badgeView)
            header.addView(iconText)
            header.addView(titleText)
            header.addView(checkIcon)
        } else if (isLocked) {
            val lockIcon = TextView(this).apply {
                text = "🔒"
                textSize = 20f
            }
            header.addView(badgeView)
            header.addView(iconText)
            header.addView(titleText)
            header.addView(lockIcon)
        } else {
            header.addView(badgeView)
            header.addView(iconText)
            header.addView(titleText)
        }
        
        val descText = TextView(this).apply {
            text = description
            textSize = 16f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, 0, 0, 16)
            setLineSpacing(0f, 1.3f)
        }
        
        val button = Button(this).apply {
            text = buttonText
            textSize = 15f
            setBackgroundColor(
                when {
                    isLocked -> Color.parseColor("#EEEEEE")
                    isCompleted -> Color.parseColor("#E0E0E0")
                    buttonEnabled -> Color.parseColor("#0080FF")
                    else -> Color.parseColor("#BDBDBD")
                }
            )
            setTextColor(
                when {
                    isLocked -> Color.parseColor("#999999")
                    isCompleted -> Color.parseColor("#757575")
                    else -> Color.WHITE
                }
            )
            setPadding(24, 16, 24, 16)
            this.isEnabled = buttonEnabled && !isCompleted && !isLocked
            setOnClickListener {
                if (!isCompleted && !isLocked) {
                    buttonAction()
                }
            }
        }
        
        card.addView(header)
        card.addView(descText)
        card.addView(button)
        
        return card
    }
    
    // Onglet 3 : À Propos
    fun createAboutContent(): LinearLayout {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 32)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        // Mission
        val missionCard = createCard("#FFFFFF")
        
        val missionTitle = TextView(this).apply {
            text = getString(R.string.sa_notre_mission)
            textSize = 20f
            setTextColor(Color.parseColor("#0080FF"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        
        val missionText = TextView(this).apply {
            text = getString(R.string.sa_ce_clavier_ete_specialement_concu)
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
            setLineSpacing(0f, 1.3f)
        }
        
        missionCard.addView(missionTitle)
        missionCard.addView(missionText)
        mainLayout.addView(missionCard)
        mainLayout.addView(createSpacing(16))

        // Partage
        val shareCard = createCard("#E8F5FF")

        val shareTitle = TextView(this).apply {
            text = "📣 Maacht Reklamm fir d'Sprooch !"
            textSize = 18f
            setTextColor(Color.parseColor("#0080FF"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }

        val shareText = TextView(this).apply {
            text = getString(R.string.sa_ce_clavier_grandit_grace_au)
            textSize = 14f
            setTextColor(Color.parseColor("#333333"))
            setLineSpacing(0f, 1.3f)
            setPadding(0, 0, 0, 16)
        }

        val shareButton = Button(this).apply {
            text = getString(R.string.sa_partager_application)
            textSize = 15f
            setBackgroundColor(Color.parseColor("#0080FF"))
            setTextColor(Color.WHITE)
            setPadding(24, 24, 24, 24)
            setOnClickListener { shareApp() }
        }

        val rateButton = Button(this).apply {
            text = getString(R.string.sa_noter_application)
            textSize = 15f
            setBackgroundColor(Color.parseColor("#FFB300"))
            setTextColor(Color.parseColor("#333333"))
            setPadding(24, 24, 24, 24)
            setOnClickListener { openPlayStoreListing() }
        }

        val shareProverb = TextView(this).apply {
            text = "« Mir wëlle bleiwe wat mir sinn »"
            textSize = 14f
            setTextColor(Color.parseColor("#0080FF"))
            setTypeface(null, Typeface.ITALIC)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.3f)
        }

        shareCard.addView(shareTitle)
        shareCard.addView(shareText)
        shareCard.addView(shareButton)
        shareCard.addView(createSpacing(12))
        shareCard.addView(rateButton)
        shareCard.addView(createSpacing(16))
        shareCard.addView(shareProverb)
        mainLayout.addView(shareCard)
        mainLayout.addView(createSpacing(16))

        // Sources littéraires
        val sourcesCard = createCard("#F0F8E8")
        
        val sourcesTitle = TextView(this).apply {
            text = getString(R.string.sa_sources_litteraires)
            textSize = 18f
            setTextColor(Color.parseColor("#228B22"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }
        
        // Attribution des sources. Trois des quatre exigent la citation de
        // leurs auteurs : cette carte n'est pas décorative, elle remplit
        // l'obligation « BY ». LuxAlign porte en plus une clause
        // NonCommercial, et Lexique un partage à l'identique qui porte sur
        // l'actif français dérivé. Seul le LOD, en CC0, n'impose rien — il est
        // cité quand même. Détail complet et références bibliographiques dans
        // Dictionnaires/CORPUS.md.
        val sourcesText = TextView(this).apply {
            text = getString(R.string.sa_les_suggestions_de_mots_sont)
            textSize = 14f
            setTextColor(Color.parseColor("#2F5233"))
            setLineSpacing(0f, 1.3f)
        }
        
        sourcesCard.addView(sourcesTitle)
        sourcesCard.addView(sourcesText)
        mainLayout.addView(sourcesCard)
        mainLayout.addView(createSpacing(16))
        
        // Informations app
        val infoCard = createCard("#F8F9FA")
        
        val infoTitle = TextView(this).apply {
            text = getString(R.string.sa_informations)
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#333333"))
            setPadding(0, 0, 0, 24)
        }

        val versionText = TextView(this).apply {
            text = getString(R.string.sa_version_potomitantm_letzebuergesch_clavier_fait, BuildConfig.VERSION_NAME)
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            setLineSpacing(0f, 1.3f)
            gravity = Gravity.CENTER
        }
        
        infoCard.addView(infoTitle)
        infoCard.addView(versionText)
        mainLayout.addView(infoCard)
        mainLayout.addView(createSpacing(16))

        // Confidentialité
        val privacyCard = createCard("#FFF8E1")

        val privacyTitle = TextView(this).apply {
            text = getString(R.string.sa_confidentialite)
            textSize = 18f
            setTextColor(Color.parseColor("#5D4037"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }

        val privacyText = TextView(this).apply {
            text = getString(R.string.sa_zero_collecte_de_donnees_personnelles)
            textSize = 14f
            setTextColor(Color.parseColor("#5D4037"))
            setLineSpacing(0f, 1.3f)
        }

        val privacyLink = TextView(this).apply {
            text = getString(R.string.sa_lire_la_politique_de_confidentialite)
            textSize = 14f
            setTextColor(Color.parseColor("#0080FF"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 12, 0, 0)
            setOnClickListener { openPrivacyPolicy() }
        }

        privacyCard.addView(privacyTitle)
        privacyCard.addView(privacyText)
        privacyCard.addView(privacyLink)
        mainLayout.addView(privacyCard)
        mainLayout.addView(createSpacing(16))

        // Tunnel d'activation : diagnostic local du parcours de configuration
        mainLayout.addView(createFunnelCard())

        return mainLayout
    }

    // ═══ Clavier d'essai du wizard ═══
    // Un vrai clavier Kréyòl interactif (les mêmes composants que l'IME :
    // KeyboardLayoutManager + SuggestionEngine) branché sur un champ de
    // démonstration : l'utilisateur essaie les suggestions bilingues AVANT
    // d'accepter les avertissements système. Aucune activation requise,
    // tout tourne dans l'activité.
    private var demoEngine: SuggestionEngine? = null
    private var demoEngineReady = false
    private var demoKeyboardManager: KeyboardLayoutManager? = null

    private fun createDemoKeyboardCard(): LinearLayout {
        val card = createRoundedCard("#FFFFFF")

        val title = TextView(this).apply {
            text = getString(R.string.sa_essayez_le_tout_de_suite)
            textSize = 18f
            setTextColor(Color.parseColor("#333333"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 4)
        }
        val caption = TextView(this).apply {
            text = getString(R.string.sa_tapez_moien_et_touchez_une)
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, 0, 0, 12)
        }

        val demoField = EditText(this).apply {
            hint = getString(R.string.sa_probeiert_et_hei_essayez_ici)
            // Ne jamais ouvrir le clavier système (Gboard) sur ce champ :
            // c'est le clavier d'essai ci-dessous qui écrit dedans
            showSoftInputOnFocus = false
            // Sans correcteur système : il soulignerait les mots créoles en
            // rouge, à rebours de ce que la démo veut montrer
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            textSize = 16f
            setPadding(16, 16, 16, 16)
            minHeight = 90
            setBackgroundColor(Color.parseColor("#F9F9F9"))
            setTextColor(Color.parseColor("#1C1C1C"))
            setHintTextColor(Color.parseColor("#999999"))
        }

        val suggestionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 4)
            minimumHeight = 110
        }

        // Pont entre le « aha » et l'action : la démo crée la motivation
        // mais ne la convertissait pas encore — l'utilisateur devait
        // comprendre seul qu'il fallait redescendre vers l'étape 1. Ce
        // bouton apparaît au premier signe d'engagement (première touche
        // pressée) et enchaîne directement vers l'activation système
        var installCtaShown = false
        val installCta = Button(this).apply {
            text = getString(R.string.sa_ca_vous_plait_installez_le)
            textSize = 15f
            isAllCaps = false
            setBackgroundColor(Color.parseColor("#0080FF"))
            setTextColor(Color.WHITE)
            setPadding(24, 20, 24, 20)
            // INVISIBLE (pas GONE) dès la création : réserve sa place tout de
            // suite pour que son apparition ne décale jamais le clavier situé
            // juste en dessous. Un décalage au premier caractère tapé ferait
            // rater les touches suivantes, frappées de mémoire par l'utilisateur
            // (repro confirmée en test automatisé : les taps suivants
            // atterrissaient sur ce bouton une fois révélé, ouvrant les
            // réglages système en pleine frappe)
            visibility = View.INVISIBLE
            alpha = 0f
            setOnClickListener { poursuivreInstallation() }
        }

        fun revealInstallCta() {
            recordFunnelStep("funnel_demo_first_key")
            if (!installCtaShown) {
                installCtaShown = true
                installCta.visibility = View.VISIBLE
                installCta.animate().alpha(1f).setDuration(300).start()
            }
        }

        val keyboardContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#ECEFF1"))
        }

        // Moteur partagé entre les refreshs du wizard : dictionnaires
        // chargés une seule fois
        val engine = demoEngine ?: SuggestionEngine(this).also { created ->
            demoEngine = created
            activityScope.launch {
                created.initialize()
                created.enableBilingualSupport()
                demoEngineReady = true
            }
        }

        fun cursor(): Int =
            demoField.selectionStart.let { if (it >= 0) it else demoField.text.length }

        fun currentWord(): String =
            demoField.text.toString().substring(0, cursor())
                .takeLastWhile { it.isLetter() || it == '\'' || it == '-' }

        fun clearChips() = suggestionsRow.removeAllViews()

        fun refreshSuggestions() {
            val word = currentWord()
            if (word.isNotEmpty() && demoEngineReady) {
                engine.generateBilingualSuggestions(word)
            } else {
                clearChips()
            }
        }

        engine.setSuggestionListener(object : SuggestionEngine.SuggestionListener {
            override fun onSuggestionsReady(suggestions: List<String>) {}
            override fun onBilingualSuggestionsReady(suggestions: List<BilingualSuggestion>) {
                clearChips()
                suggestions.take(3).forEach { suggestion ->
                    val chip = TextView(this@SettingsActivity).apply {
                        text = suggestion.word
                        textSize = 15f
                        setTextColor(Color.WHITE)
                        setTypeface(null, Typeface.BOLD)
                        setPadding(28, 14, 28, 14)
                        background = android.graphics.drawable.GradientDrawable().apply {
                            cornerRadius = 40f
                            setColor(suggestion.getColor())
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { rightMargin = 12 }
                        setOnClickListener {
                            val pos = cursor()
                            val word = currentWord()
                            val start = (pos - word.length).coerceAtLeast(0)
                            demoField.text.replace(start, pos, suggestion.word + " ")
                            clearChips()
                            revealInstallCta()
                        }
                    }
                    suggestionsRow.addView(chip)
                }
            }
            override fun onDictionaryLoaded(wordCount: Int) {}
            override fun onNgramModelLoaded() {}
            override fun onFrenchDictionaryLoaded(wordCount: Int) {}
            override fun onModeChanged(newMode: SuggestionEngine.SuggestionMode) {}
        })

        demoKeyboardManager?.cleanup()
        val manager = KeyboardLayoutManager(this)
        // La démonstration montre le clavier tel que l'utilisateur l'aura.
        manager.definirDisposition(KeyboardPreferences.disposition(this))
        demoKeyboardManager = manager
        // Mirroir local de l'état shift (le manager n'expose pas de getter) :
        // cycle minuscules → majuscule ponctuelle → verrouillage → minuscules
        var demoCapital = false
        var demoCapsLock = false

        fun insertText(t: String) {
            demoField.text.insert(cursor(), t)
        }

        manager.setInteractionListener(object : KeyboardLayoutManager.KeyboardInteractionListener {
            override fun onKeyPress(key: String) {
                when (key) {
                    "⌫" -> {
                        val pos = cursor()
                        if (pos > 0) demoField.text.delete(pos - 1, pos)
                    }
                    "⏎" -> insertText("\n")
                    "⇧" -> {
                        when {
                            !demoCapital && !demoCapsLock -> demoCapital = true
                            demoCapital && !demoCapsLock -> demoCapsLock = true
                            else -> { demoCapital = false; demoCapsLock = false }
                        }
                        manager.updateKeyboardStates(manager.isNumericMode(), manager.isEmojiMode(), demoCapital, demoCapsLock)
                        manager.updateKeyboardDisplay()
                    }
                    "123", "ABC" -> {
                        manager.switchKeyboardMode()
                        manager.applyMode()
                    }
                    "EMOJI" -> {
                        manager.switchToEmojiMode()
                        manager.applyMode()
                    }
                    else -> {
                        insertText(if (demoCapital || demoCapsLock) key.uppercase() else key)
                        if (demoCapital && !demoCapsLock) {
                            demoCapital = false
                            manager.updateKeyboardStates(manager.isNumericMode(), manager.isEmojiMode(), false, false)
                            manager.updateKeyboardDisplay()
                        }
                        revealInstallCta()
                    }
                }
                refreshSuggestions()
            }
            // Pas de popup d'accents en démo : é, è et ò sont déjà des touches directes
            override fun onLongPress(key: String, button: View) {}
            override fun onKeyRelease() {}
        })
        keyboardContainer.addView(manager.createKeyboardLayout())

        card.addView(title)
        card.addView(caption)
        card.addView(demoField)
        card.addView(suggestionsRow)
        card.addView(installCta)
        card.addView(createSpacing(8))
        card.addView(keyboardContainer)
        return card
    }

    // Carte diagnostic du tunnel d'activation : quand chaque jalon a été
    // franchi (données 100 % locales). Sert à comprendre où le parcours
    // accroche quand un utilisateur montre son téléphone, sans télémétrie
    private fun createFunnelCard(): LinearLayout {
        val card = createCard("#F8F9FA")

        val title = TextView(this).apply {
            text = getString(R.string.sa_diagnostic_activation)
            textSize = 18f
            setTextColor(Color.parseColor("#333333"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }
        card.addView(title)

        val prefs = onboardingPrefs()
        val firstOpen = prefs.getLong("funnel_first_open", 0L)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)

        fun funnelLine(label: String, key: String): String {
            val ts = prefs.getLong(key, 0L)
            return when {
                ts == 0L -> getString(R.string.sa_pas_encore, label)
                firstOpen == 0L || ts <= firstOpen -> "$label : ${dateFormat.format(Date(ts))}"
                else -> {
                    val minutes = (ts - firstOpen) / 60000
                    val delta = when {
                        minutes < 1 -> getString(R.string.sa_moins_une_minute_apres_ouverture)
                        minutes < 60 -> getString(R.string.sa_min_apres_ouverture, minutes)
                        minutes < 1440 -> getString(R.string.sa_apres_ouverture_2, minutes / 60)
                        else -> getString(R.string.sa_apres_ouverture, minutes / 1440)
                    }
                    "$label : $delta"
                }
            }
        }

        val lines = TextView(this).apply {
            text = listOf(
                if (firstOpen == 0L) getString(R.string.sa_premiere_ouverture_pas_encore)
                else getString(R.string.sa_premiere_ouverture, dateFormat.format(Date(firstOpen))),
                funnelLine(getString(R.string.sa_premier_essai_clavier_de_demo), "funnel_demo_first_key"),
                funnelLine(getString(R.string.sa_clavier_active), "funnel_keyboard_enabled"),
                funnelLine(getString(R.string.sa_retour_sans_avoir_active), "funnel_settings_return_no_enable"),
                funnelLine(getString(R.string.sa_clavier_selectionne), "funnel_keyboard_selected"),
                funnelLine(getString(R.string.sa_premier_mot_tape), "funnel_first_word")
            ).joinToString("\n")
            textSize = 14f
            setTextColor(Color.parseColor("#333333"))
            setLineSpacing(0f, 1.5f)
        }
        card.addView(lines)

        val note = TextView(this).apply {
            text = getString(R.string.sa_ces_horodatages_restent_sur_votre)
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 8, 0, 0)
        }
        card.addView(note)

        return card
    }

    fun createGuideContent(): LinearLayout {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 32)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val guideTitle = TextView(this).apply {
            text = getString(R.string.sa_guide_de_utilisateur)
            textSize = 20f
            setTextColor(Color.parseColor("#0080FF"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        mainLayout.addView(guideTitle)
        mainLayout.addView(createSpacing(8))

        addGuideSection(
            mainLayout, "#E3F2FD", getString(R.string.sa_installation_et_activation),
            getString(R.string.sa_le_clavier_doit_etre_active)
        )

        addGuideSection(
            mainLayout, "#FFFFFF", getString(R.string.sa_ouvrir_les_parametres_de_clavier),
            getString(R.string.sa_depuis_onglet_haut_le_bouton)
        )
        addGuideImage(mainLayout, R.drawable.guide_screenshot_install_settings, getString(R.string.sa_ecran_systeme_listant_les_claviers))

        addGuideSection(
            mainLayout, "#FFF8E1", getString(R.string.sa_valider_les_avertissements_android),
            getString(R.string.sa_en_activant_interrupteur_android_affiche)
        )
        addGuideImage(mainLayout, R.drawable.guide_screenshot_install_warning, getString(R.string.sa_avertissement_systeme_affiche_pour_tout))

        addGuideSection(
            mainLayout, "#FFFFFF", getString(R.string.sa_selectionner_le_clavier),
            getString(R.string.sa_de_retour_dans_application_etape)
        )
        addGuideImage(mainLayout, R.drawable.guide_screenshot_install_picker, getString(R.string.sa_selecteur_systeme_de_mode_de))

        addGuideSection(
            mainLayout, "#F0F8E8", getString(R.string.sa_configuration_terminee),
            getString(R.string.sa_les_deux_etapes_cochees_le)
        )
        addGuideImage(mainLayout, R.drawable.guide_screenshot_install_done, getString(R.string.sa_les_trois_etapes_cochees_clavier))

        addGuideSection(
            mainLayout, "#FFFFFF", getString(R.string.sa_ecrire_en_letzebuergesch),
            getString(R.string.sa_le_clavier_demarre_en_mode)
        )

        addGuideSection(
            mainLayout, "#F0F8E8", getString(R.string.sa_accents_et_caracteres_speciaux),
            getString(R.string.sa_les_lettres_du_luxembourgeois_ont)
        )
        addGuideImage(mainLayout, R.drawable.guide_screenshot_accents, getString(R.string.sa_popup_accents_sur_la_lettre))

        addGuideSection(
            mainLayout, "#FFFFFF", getString(R.string.sa_suggestions_et_autocompletion),
            getString(R.string.sa_une_barre_de_suggestions_apparait)
        )
        addGuideImage(mainLayout, R.drawable.guide_screenshot_suggestions, getString(R.string.sa_barre_de_suggestions_active))

        addGuideSection(
            mainLayout, "#F0F8E8", getString(R.string.sa_correction_orthographique_partout),
            getString(R.string.sa_activez_le_correcteur_luxembourgeois_dans)
        )

        addGuideSection(
            mainLayout, "#FFFFFF", getString(R.string.sa_chiffres_et_symboles),
            getString(R.string.sa_le_bouton_en_bas_gauche)
        )
        addGuideImage(mainLayout, R.drawable.guide_screenshot_numeric, getString(R.string.sa_mode_chiffres_et_symboles))

        addGuideSection(
            mainLayout, "#F0F8E8", getString(R.string.sa_jeux_de_vocabulaire),
            getString(R.string.sa_sept_jeux_reunis_dans_onglet)
        )

        addGuideSection(
            mainLayout, "#FFF3E0", getString(R.string.sa_les_cartes_du_carnet),
            getString(R.string.sa_chaque_mot_gagne_dans_un)
        )

        addGuideImage(mainLayout, R.drawable.guide_carte_haut, getString(R.string.sa_le_haut_de_la_carte_2))
        addGuideSection(
            mainLayout, "#FFFFFF", getString(R.string.sa_le_haut_de_la_carte),
            getString(R.string.sa_le_mot_tel_que_vous)
        )

        addGuideImage(mainLayout, R.drawable.guide_carte_texte, getString(R.string.sa_le_texte_de_la_carte_2))
        addGuideSection(
            mainLayout, "#FFF3E0", getString(R.string.sa_le_texte_de_la_carte),
            getString(R.string.sa_la_ligne_de_nature_ce)
        )

        addGuideImage(mainLayout, R.drawable.guide_carte_bas, getString(R.string.sa_le_bas_de_la_carte_2))
        addGuideSection(
            mainLayout, "#FFFFFF", getString(R.string.sa_le_bas_de_la_carte),
            getString(R.string.sa_ecu_vues_combien_de_fois)
        )

        addGuideImage(mainLayout, R.drawable.guide_carte_vignette, getString(R.string.sa_la_petite_carte_dans_la))
        addGuideSection(
            mainLayout, "#FFF3E0", getString(R.string.sa_dans_la_grille_du_carnet),
            getString(R.string.sa_la_petite_carte_ne_garde)
        )

        addGuideSection(
            mainLayout, "#FFFFFF", getString(R.string.sa_progression),
            getString(R.string.sa_chaque_mot_que_vous_tapez)
        )

        val faqCard = createCard("#FFF8E1")
        val faqTitle = TextView(this).apply {
            text = getString(R.string.sa_questions_frequentes)
            textSize = 18f
            setTextColor(Color.parseColor("#5D4037"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }
        val faqText = TextView(this).apply {
            text = getString(R.string.sa_le_clavier_luxembourgeois_apparait_pas)
            textSize = 14f
            setTextColor(Color.parseColor("#5D4037"))
            setLineSpacing(0f, 1.3f)
        }
        val faqPrivacyLink = TextView(this).apply {
            text = getString(R.string.sa_lire_la_politique_de_confidentialite)
            textSize = 14f
            setTextColor(Color.parseColor("#0080FF"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 12, 0, 0)
            setOnClickListener { openPrivacyPolicy() }
        }
        faqCard.addView(faqTitle)
        faqCard.addView(faqText)
        faqCard.addView(faqPrivacyLink)
        mainLayout.addView(faqCard)

        return mainLayout
    }

    private fun addGuideSection(parent: LinearLayout, backgroundColor: String, title: String, body: String) {
        val card = createCard(backgroundColor)
        val titleView = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(Color.parseColor("#333333"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }
        val bodyView = TextView(this).apply {
            text = body
            textSize = 14f
            setTextColor(Color.parseColor("#333333"))
            setLineSpacing(0f, 1.3f)
        }
        card.addView(titleView)
        card.addView(bodyView)
        parent.addView(card)
        parent.addView(createSpacing(16))
    }

    private fun addGuideImage(parent: LinearLayout, drawableResId: Int, description: String) {
        val card = createCard("#FFFFFF")
        val image = ImageView(this).apply {
            setImageResource(drawableResId)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = description
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val caption = TextView(this).apply {
            text = description
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }
        card.addView(image)
        card.addView(caption)
        parent.addView(card)
        parent.addView(createSpacing(16))
    }

    // Helpers pour créer les éléments UI
    private fun createCard(backgroundColor: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor(backgroundColor))
        }
    }
    
    private fun createSpacing(heightDp: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (heightDp * resources.displayMetrics.density).toInt()
            )
        }
    }
    
    private fun createChecklistItem(isChecked: Boolean, title: String, description: String): LinearLayout {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        
        val checkbox = TextView(this).apply {
            text = if (isChecked) "✅" else "⚠️"
            textSize = 24f
            setPadding(0, 0, 16, 0)
        }
        
        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        
        val titleText = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(if (isChecked) Color.parseColor("#228B22") else Color.parseColor("#FF6B35"))
            setTypeface(null, Typeface.BOLD)
        }
        
        val descText = TextView(this).apply {
            text = description
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            setLineSpacing(0f, 1.2f)
        }
        
        textContainer.addView(titleText)
        textContainer.addView(descText)
        
        item.addView(checkbox)
        item.addView(textContainer)
        
        return item
    }
    
    private fun createGuideCard(icon: String, title: String, description: String): LinearLayout {
        val card = createCard("#FFFFFF")
        
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        
        val iconText = TextView(this).apply {
            text = icon
            textSize = 28f
            setPadding(0, 0, 16, 0)
        }
        
        val titleText = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        
        header.addView(iconText)
        header.addView(titleText)
        
        val descText = TextView(this).apply {
            text = description
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            setLineSpacing(0f, 1.3f)
            setPadding(0, 8, 0, 0)
        }
        
        card.addView(header)
        card.addView(descText)
        
        return card
    }
    

    // Fonction pour vérifier si le clavier est activé
    fun isKeyboardEnabled(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledIMEs = imm.enabledInputMethodList
        val myPackageName = packageName
        
        return enabledIMEs.any { it.packageName == myPackageName }
    }
    
    // Fonction pour vérifier si le clavier est sélectionné comme clavier actif
    fun isKeyboardSelected(): Boolean {
        try {
            val currentIme = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            )
            return currentIme?.contains(packageName) == true
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur vérification clavier sélectionné: ${e.message}")
            return false
        }
    }
    
    // Notre correcteur travaille-t-il vraiment ? Il faut qu'il soit choisi ET
    // que l'interrupteur général « Utiliser le correcteur » soit allumé : choisi
    // mais coupé, il ne souligne rien, et la carte annonçait pourtant « actif ».
    fun isSpellCheckerSelected(): Boolean = isSpellCheckerChosen() && isSpellCheckingOn()

    private fun isSpellCheckerChosen(): Boolean {
        return try {
            val current = Settings.Secure.getString(contentResolver, "selected_spell_checker")
            current?.contains(packageName) == true
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur vérification correcteur sélectionné: ${e.message}")
            false
        }
    }

    // Absent sur une installation neuve : Android le considère alors allumé.
    private fun isSpellCheckingOn(): Boolean = try {
        Settings.Secure.getString(contentResolver, "spell_checker_enabled") != "0"
    } catch (e: Exception) {
        true
    }

    /** Choisi, mais l'interrupteur général l'empêche de travailler. */
    fun isSpellCheckerChosenButOff(): Boolean = isSpellCheckerChosen() && !isSpellCheckingOn()

    // Fonction pour ouvrir les paramètres où choisir le correcteur orthographique
    private fun openSpellCheckerSettings() {
        // ACTION_INPUT_METHOD_SETTINGS ouvre la liste des CLAVIERS, pas le
        // sélecteur de correcteur orthographique. Le seul point d'entrée public
        // vers cet écran est ce composant Settings (standard AOSP depuis
        // Android 4.2), avec repli sur l'écran clavier si absent sur certains ROM.
        try {
            val intent = Intent().apply {
                setClassName("com.android.settings", "com.android.settings.Settings\$SpellCheckersSettingsActivity")
                // Sans FLAG_ACTIVITY_NEW_TASK : voir openKeyboardSettings().
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur ouverture écran correcteur, repli sur les paramètres clavier: ${e.message}")
            try {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                // Seul cas où un Toast d'instruction reste utile : l'écran de
                // repli n'est pas celui attendu, la carte de l'étape 4 ne
                // décrit donc pas ce que l'utilisateur a sous les yeux
                Toast.makeText(this,
                    getString(R.string.sa_dans_langues_et_saisie_ouvrez),
                    Toast.LENGTH_LONG
                ).show()
            } catch (ex: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (ex2: Exception) {
                    Toast.makeText(this, getString(R.string.sa_impossible_ouvrir_les_parametres), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Ce qui reste à faire pour installer le clavier, depuis les boutons qui ne
     * savent pas où en est l'utilisateur (bandeau du bas, bouton du clavier
     * d'essai) : ils renvoyaient tous à l'étape 1, y compris quand elle était
     * faite.
     */
    private fun poursuivreInstallation() {
        if (isKeyboardEnabled()) openInputMethodPicker() else openKeyboardSettings()
    }

    private val minuterieGuet = Handler(Looper.getMainLooper())
    private var guetEnCours: Runnable? = null

    /**
     * Ramène l'utilisateur dans l'appli dès que le clavier est activé, sans
     * qu'il ait à trouver Retour (34.5.0). Il en était à l'interrupteur, puis
     * à l'avertissement d'Android : dès qu'il touche OK, l'appli referme
     * l'écran des réglages qu'elle avait ouvert, et l'étape 2 ouvre d'elle-même
     * le choix du clavier.
     *
     * Cela ne marche que parce que les réglages s'ouvrent dans la tâche de
     * l'appli (34.4.1) : finishActivity ne referme que ce que l'activité a
     * lancé pour résultat.
     *
     * On interroge InputMethodManager à intervalle court plutôt que d'observer
     * le réglage : ENABLED_INPUT_METHODS n'est plus lisible depuis Android 14
     * pour une appli qui vise au-delà de l'API 33, et la notification d'un
     * ContentObserver arrivait à une appli en arrière-plan avec dix secondes
     * de retard (mesuré sur Android 16), le temps que l'utilisateur cherche
     * Retour. L'appli reste vivante pendant ce temps : elle est juste sous les
     * réglages, dans la même tâche. Le guet s'arrête au bout de trois minutes,
     * ou dès que l'appli revient au premier plan.
     */
    private fun guetterActivation() {
        arreterGuetActivation()
        val fin = SystemClock.uptimeMillis() + 3 * 60_000L
        val guet = object : Runnable {
            override fun run() {
                if (guetEnCours !== this) return
                if (isKeyboardEnabled()) {
                    arreterGuetActivation()
                    Log.d("SettingsActivity", "🔙 Clavier activé : retour automatique dans l'appli")
                    @Suppress("DEPRECATION")
                    finishActivity(REQ_REGLAGES_CLAVIER)
                } else if (SystemClock.uptimeMillis() < fin) {
                    minuterieGuet.postDelayed(this, 400)
                } else {
                    arreterGuetActivation()
                }
            }
        }
        guetEnCours = guet
        minuterieGuet.postDelayed(guet, 400)
    }

    private fun arreterGuetActivation() {
        guetEnCours?.let { minuterieGuet.removeCallbacks(it) }
        guetEnCours = null
    }

    // Ouvre les paramètres de clavier système, directement. Il y avait avant un
    // écran « Avant de continuer » montrant une capture de l'avertissement
    // d'Android : retiré en 34.4.0, parce que la capture, nette, ressemblait
    // plus à une vraie fenêtre que la vraie, grisée par le thème sombre, et
    // qu'on touchait le « OK » de l'image sans que rien ne se passe. La carte
    // de l'étape 1 dit la même chose avant le départ. Pas de Toast non plus :
    // il s'affichait par-dessus l'écran système, sans garantie de durée.
    private fun openKeyboardSettings() {
        try {
            // Horodater le départ vers les réglages : si l'utilisateur
            // revient sans avoir activé le clavier (abandon au premier des
            // deux avertissements, ligne pas trouvée...), l'onboarding
            // affiche une carte d'encouragement ciblée
            onboardingPrefs().edit()
                .putLong("settings_visit_at", System.currentTimeMillis()).apply()
            // Pas de FLAG_ACTIVITY_NEW_TASK : l'écran des réglages doit
            // s'empiler sur l'appli, pour que Retour y ramène. Avec ce
            // drapeau, Samsung le posait dans la tâche Réglages déjà ouverte,
            // par-dessus ce qu'on y avait laissé, et Retour menait à un ancien
            // écran (« Correction orthographique ») au lieu de l'étape 2.
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            if (!isKeyboardEnabled()) guetterActivation()
            // Tentative de surlignage de la ligne IME dans l'écran système :
            // extra non documenté, respecté par les Settings AOSP/Pixel,
            // ignoré silencieusement ailleurs (pas d'effet de bord).
            intent.putExtra(
                ":settings:fragment_args_key",
                "$packageName/com.example.kreyolkeyboard.KreyolInputMethodServiceRefactored"
            )
            // « pour résultat » : c'est ce qui permet de refermer l'écran des
            // réglages depuis l'appli (finishActivity), voir guetterActivation().
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_REGLAGES_CLAVIER)
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur ouverture paramètres clavier: ${e.message}")
            // Fallback vers paramètres généraux
            try {
                val intent = Intent(Settings.ACTION_SETTINGS)
                startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(this, getString(R.string.sa_impossible_ouvrir_les_parametres), Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun openPrivacyPolicy() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur ouverture politique de confidentialité: ${e.message}")
            Toast.makeText(this, getString(R.string.sa_impossible_ouvrir_la_politique_de), Toast.LENGTH_SHORT).show()
        }
    }

    // Ouvre la fiche Play Store pour noter l'app (complément de l'In-App Review, soumis à quota)
    private fun openPlayStoreListing() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
            } catch (ex: Exception) {
                Log.e("SettingsActivity", "Erreur ouverture fiche Play Store: ${ex.message}")
                Toast.makeText(this, getString(R.string.sa_play_store_indisponible), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Fonction pour partager l'application (bouche-à-oreille)
    private fun shareApp() {
        val message = getString(R.string.sa_ech_schreiwen_op_letzebuergesch_op, packageName) +
                SHARE_HASHTAG
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.sa_partager_le_letzebuergesch_clavier)))
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur partage application: ${e.message}")
            Toast.makeText(this, getString(R.string.sa_impossible_de_partager_pour_le), Toast.LENGTH_SHORT).show()
        }
    }

    // Ouvre le sélecteur de clavier système, immédiatement. Ne pas ajouter de
    // Toast d'instruction ici : sa gravité est ignorée depuis API 30, il
    // s'affiche en bas par-dessus le sélecteur et masque l'entrée à choisir ;
    // l'instruction est déjà portée par la carte de l'étape 2, visible
    // derrière le dialogue.
    private fun openInputMethodPicker() {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur ouverture sélecteur clavier: ${e.message}")
            Toast.makeText(this, 
                getString(R.string.sa_impossible_ouvrir_le_selecteur_touchez), 
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    fun createStatsContent(): LinearLayout {
        Log.d("SettingsActivity", "Création du contenu des statistiques")
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        val stats = loadVocabularyStats()
        Log.d("SettingsActivity", "Stats chargées: ${stats.wordsDiscovered} mots découverts, ${stats.totalUsages} utilisations")
        
        // Container principal
        val statsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 32)
        }
        
        // === Niveau - Badge minimaliste ===
        val level = getCurrentLevel(stats.wordsDiscovered)
        val levelParts = level.split(" ")
        val levelEmoji = levelParts[0]
        val levelName = if (levelParts.size > 1) levelParts.drop(1).joinToString(" ") else ""
        
        // Calcul des mots restants pour le niveau suivant
        val (nextLevelName, wordsRemaining) = getNextLevelInfo(stats.wordsDiscovered)

        // L'utilisateur consulte enfin sa progression : la pastille de l'onglet
        // a rempli son office, on l'éteint et on redessine la barre.
        if (hasPendingLevelBadge()) {
            gamificationPrefs().edit().putBoolean(PREF_LEVEL_BADGE_PENDING, false).apply()
            if (::tabBar.isInitialized) tabBar.post { updateTabBar() }
        }

        // La notification et la pastille d'icône disent « il y a quelque chose à
        // voir ici » : cet écran est précisément ce quelque chose. setAutoCancel
        // ne les efface qu'au tap sur la notification, si bien que l'utilisateur
        // arrivé par le lanceur gardait une pastille sur son écran d'accueil
        // après avoir déjà tout vu.
        LevelUpNotifier.clear(this)

        // Célébration + carte partageable si un niveau vient d'être franchi
        maybeCelebrateLevelUp(stats.wordsDiscovered, levelEmoji, levelName)

        // 🔍 DEBUG: Log pour vérifier les calculs
        val thresholdsDebug = calculateGaussianThresholds()
        Log.d("SettingsActivity", "📊 DEBUG Niveau: wordsDiscovered=${stats.wordsDiscovered}, " +
                "levelName=$levelName, nextLevelName=$nextLevelName, wordsRemaining=$wordsRemaining")
        Log.d("SettingsActivity", "📊 DEBUG Seuils: ${thresholdsDebug.joinToString(", ")}")
        
        val levelContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 40)
        }

        // v29.3.0 : l'écran s'ouvrait sur « 0.0% » en très gros et « 7 mots
        // découverts sur les 38442 mots » : pour qui débute, lire qu'il ne sait
        // rien. On montre d'abord le niveau, puis le chemin jusqu'au palier
        // suivant, seul objectif à portée ; la part du dictionnaire entier
        // passe en petit, sans pourcentage tant qu'il ne dépasse pas 1 %.
        val levelBadge = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(24, 16, 24, 16)
        }

        val levelEmojiText = TextView(this).apply {
            text = levelEmoji
            textSize = 48f
            setPadding(0, 0, 16, 0)
        }

        val levelNameText = TextView(this).apply {
            text = levelName
            textSize = 28f
            setTextColor(Color.parseColor("#1C1C1C"))
            setTypeface(null, Typeface.BOLD)
        }

        levelBadge.addView(levelEmojiText)
        levelBadge.addView(levelNameText)
        levelContainer.addView(levelBadge)

        val totalWords = getTotalDictionaryWords()
        val auSommet = getCurrentLevelIndex(stats.wordsDiscovered) == LuxLevels.MAX_INDEX

        creerBarreNiveau(stats.wordsDiscovered)?.let { barre ->
            levelContainer.addView(FrameLayout(this).apply {
                setPadding(enDp(24), 0, enDp(24), 0)
                addView(barre)
            })
        }

        val progressMessage = TextView(this).apply {
            text = if (auSommet) {
                getString(R.string.sa_vous_avez_atteint_le_plus)
            } else {
                resources.getQuantityString(R.plurals.encore_mots_avant, wordsRemaining, nombre(wordsRemaining), nextLevelName)
            }
            textSize = 17f
            setTextColor(Color.parseColor("#1C1C1C"))
            gravity = Gravity.CENTER
            setPadding(16, 8, 16, 8)
        }
        levelContainer.addView(progressMessage)

        val part = if (totalWords > 0) stats.wordsDiscovered * 100.0 / totalWords else 0.0
        val percentageLabel = TextView(this).apply {
            val mots = resources.getQuantityString(
                R.plurals.mots_decouverts_sur, stats.wordsDiscovered,
                nombre(stats.wordsDiscovered), nombre(totalWords)
            )
            text = if (part >= 1.0) "$mots (${part.toInt()} %)" else mots
            textSize = 14f
            setTextColor(Color.parseColor("#777777"))
            gravity = Gravity.CENTER
            setPadding(16, 0, 16, 24)
        }
        levelContainer.addView(percentageLabel)

        // Partage permanent de la carte de niveau. Jusqu'ici, shareLevelCard()
        // n'était atteignable que par le bouton de la boîte de célébration :
        // répondre « Plus tard » perdait la carte définitivement, puisque le
        // palier était déjà marqué comme célébré et que la boîte ne
        // réapparaissait jamais. L'astuce qui promet de partager sa carte
        // « depuis Mäi Lëtzebuergesch » décrit désormais quelque chose qui existe.
        // Placé après le niveau : on partage ce qu'on vient de lire.
        val shareLevelButton = Button(this).apply {
            text = getString(R.string.sa_partager_ma_carte_de_niveau_2)
            textSize = 15f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0E6E76"))
            setPadding(24, 16, 24, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                try {
                    shareLevelCard(
                        buildLevelCardBitmap(levelEmoji, levelName, stats.wordsDiscovered),
                        levelName
                    )
                } catch (e: Exception) {
                    Log.e("SettingsActivity", "Erreur partage carte de niveau: ${e.message}")
                    Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.sa_impossible_de_partager_pour_le),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        levelContainer.addView(shareLevelButton)

        // === Mot du Jour - Design épuré ===
        val (wordOfDay, usageCount) = getWordOfTheDay()
        
        val wordContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 40, 24, 40)
            setBackgroundColor(Color.parseColor("#FAFAFA"))
        }
        
        val wordLabel = TextView(this).apply {
            text = getString(R.string.sa_mot_du_jour)
            textSize = 12f
            setTextColor(Color.parseColor("#FF8C00"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.1f
            setPadding(0, 0, 0, 16)
        }
        
        val wordText = TextView(this).apply {
            // Forme du dictionnaire et non clé du fichier d'usage, qui est en
            // minuscules : « Brauereien », pas « brauereien ». L'appli enseigne
            // la majuscule des noms dans ses jeux, elle ne l'efface pas ici.
            text = formeAffichee(wordOfDay)
            textSize = 48f
            setTextColor(Color.parseColor("#1C1C1C"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        
        // Sans sa traduction, le mot du jour est une suite de lettres qu'on
        // regarde une seconde et qu'on oublie. C'est la seule chose qui en fait
        // un mot du jour plutôt qu'un tirage au sort.
        //
        // La glose est annoncée, pas seulement posée sous le mot : entre le mot
        // en 48sp et la ligne d'usage en gris, une ligne grise de plus se lit
        // comme un sous-titre quelconque. « en français : » lève l'ambiguïté en
        // trois mots, et le sens lui-même est repris en plus sombre.
        val glose = TranslationDictionary.traduire(this@SettingsActivity, wordOfDay)
        val wordGloss = TextView(this).apply {
            text = if (glose == null) "" else SpannableStringBuilder(getString(R.string.sa_en_francais_2)).apply {
                setSpan(ForegroundColorSpan(Color.parseColor("#999999")),
                    0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val debut = length
                append(glose)
                setSpan(ForegroundColorSpan(Color.parseColor("#333333")),
                    debut, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12)
            visibility = if (glose == null) View.GONE else View.VISIBLE
        }

        val wordUsage = TextView(this).apply {
            text = if (usageCount > 0) resources.getQuantityString(R.plurals.utilise_fois, usageCount, usageCount) else getString(R.string.sa_nouveau_mot_decouvrir)
            textSize = 14f
            setTextColor(Color.parseColor("#999999"))
            gravity = Gravity.CENTER
        }
        
        wordContainer.addView(wordLabel)
        wordContainer.addView(wordText)
        wordContainer.addView(wordGloss)
        wordContainer.addView(wordUsage)
        
        // === Top 5 - Liste simple ===
        val top5Container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 0, 24, 40)
        }
        
        val top5Title = TextView(this).apply {
            text = getString(R.string.sa_mots_les_plus_utilises)
            textSize = 16f
            setTextColor(Color.parseColor("#1C1C1C"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 24)
        }
        
        top5Container.addView(top5Title)
        
        stats.topWords.take(5).forEachIndexed { index, word ->
            val wordRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, 16)
            }
            
            val rank = TextView(this).apply {
                text = "${index + 1}."
                textSize = 20f
                setTextColor(Color.parseColor("#FF8C00"))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 16, 0)
            }
            
            val wordName = TextView(this).apply {
                val glose = TranslationDictionary.traduire(this@SettingsActivity, word.first)
                text = if (glose != null) {
                    SpannableStringBuilder(word.first).apply {
                        val debut = length
                        append("  ").append(glose)
                        setSpan(ForegroundColorSpan(Color.parseColor("#999999")),
                            debut, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        setSpan(RelativeSizeSpan(0.7f),
                            debut, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                } else word.first
                textSize = 20f
                setTextColor(Color.parseColor("#1C1C1C"))
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
            
            val wordCount = TextView(this).apply {
                text = "${word.second}"
                textSize = 20f
                setTextColor(Color.parseColor("#999999"))
                gravity = Gravity.END
            }
            
            wordRow.addView(rank)
            wordRow.addView(wordName)
            wordRow.addView(wordCount)
            top5Container.addView(wordRow)
        }
        
        // === Statistiques - Grille 2x2 ===
        val statsGridContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 40, 24, 40)
        }
        
        val statsGridTitle = TextView(this).apply {
            text = getString(R.string.sa_statistiques_globales)
            textSize = 16f
            setTextColor(Color.parseColor("#1C1C1C"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 32)
        }
        
        statsGridContainer.addView(statsGridTitle)
        
        // Ligne unique: Découverts | Utilisations
        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 32)
        }
        
        statsRow.addView(createStatBlock("${stats.wordsDiscovered}", getString(R.string.sa_mots_decouverts_2)))
        statsRow.addView(createStatBlock("${stats.totalUsages}", getString(R.string.sa_utilisations)))
        
        statsGridContainer.addView(statsRow)
        
        // === Mots à Découvrir ===
        val wordsToDiscoverContainer = createWordListSection(
            getString(R.string.sa_mots_decouvrir),
            stats.wordsToDiscover,
            "#2196F3"
        )
        
        // === Mots Découverts ===
        val discoveredWordsContainer = createWordListSection(
            getString(R.string.sa_mots_decouverts, stats.discoveredWordsList.size),
            stats.discoveredWordsList,
            "#4CAF50"
        )
        
        // Assembler
        statsContainer.addView(levelContainer)
        statsContainer.addView(wordContainer)
        statsContainer.addView(wordsToDiscoverContainer)
        statsContainer.addView(top5Container)
        statsContainer.addView(statsGridContainer)
        statsContainer.addView(discoveredWordsContainer)
        
        mainLayout.addView(statsContainer)
        
        return mainLayout
    }
    
    private fun createStatBlock(number: String, label: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            
            val numText = TextView(this@SettingsActivity).apply {
                text = number
                textSize = 36f
                setTextColor(Color.parseColor("#1C1C1C"))
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 8)
            }
            
            val labelText = TextView(this@SettingsActivity).apply {
                text = label
                textSize = 12f
                setTextColor(Color.parseColor("#999999"))
                gravity = Gravity.CENTER
            }
            
            addView(numText)
            addView(labelText)
        }
    }
    
    private fun createWordListSection(title: String, words: List<String>, accentColor: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            
            // Titre de la section
            val sectionTitle = TextView(this@SettingsActivity).apply {
                text = title
                textSize = 24f  // Augmenté de 1.5x (16f * 1.5)
                setTextColor(Color.parseColor("#1C1C1C"))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 16)
            }
            addView(sectionTitle)
            
            if (words.isEmpty()) {
                // Message si aucun mot
                val emptyMessage = TextView(this@SettingsActivity).apply {
                    text = getString(R.string.sa_aucun_mot_dans_cette_categorie)
                    textSize = 21f  // Augmenté de 1.5x (14f * 1.5)
                    setTextColor(Color.parseColor("#999999"))
                    setTypeface(null, Typeface.ITALIC)
                    setPadding(16, 12, 16, 12)
                    setBackgroundColor(Color.parseColor("#F5F5F5"))
                }
                addView(emptyMessage)
            } else {
                // Conteneur pour les mots avec scroll
                val scrollView = ScrollView(this@SettingsActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        450 // Hauteur maximale augmentée de 1.5x (300 * 1.5)
                    )
                }
                
                // Container avec retour à la ligne automatique (FlowLayout simulé)
                val wordsContainer = LinearLayout(this@SettingsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(12, 12, 12, 12)
                    setBackgroundColor(Color.parseColor("#FAFAFA"))
                }
                
                // Créer des lignes dynamiques qui s'adaptent à la largeur
                var currentRow = LinearLayout(this@SettingsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 6
                    }
                }
                wordsContainer.addView(currentRow)
                
                var currentRowWidth = 0
                // Calculer la largeur disponible: largeur écran - padding container (24) - padding statsContainer (48) - marges (24)
                // La largeur du contenu, et non celle de l'écran : sur tablette
                // l'onglet est ramené à une colonne (voir LargeurLecture).
                val screenWidth = LargeurLecture.largeur(this@SettingsActivity) - 96
                
                words.forEach { word ->
                    // Créer le chip du mot, suivi de sa traduction quand on la
                    // connaît. Le chip mesure sa propre largeur juste après,
                    // donc l'ajout de la glose est absorbé par le passage à la
                    // ligne : rien d'autre n'est à ajuster.
                    val wordChip = TextView(this@SettingsActivity).apply {
                        // Une seule acception sur un chip : « Aarbechtsmaart ·
                        // marché du travail, marché de l'emploi » déborde de la
                        // largeur de l'écran. Le sens complet est dans l'onglet
                        // Wierderbuch.
                        val glose = TranslationDictionary.traduire(this@SettingsActivity, word)
                            ?.substringBefore(",")
                        text = if (glose != null) {
                            SpannableStringBuilder(word).apply {
                                val debut = length
                                append(" · ").append(glose)
                                setSpan(ForegroundColorSpan(Color.parseColor("#8A8A8A")),
                                    debut, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                setSpan(RelativeSizeSpan(0.75f),
                                    debut, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            }
                        } else word
                        textSize = 19.5f  // Augmenté de 1.5x (13f * 1.5)
                        setTextColor(Color.parseColor(accentColor))
                        setPadding(15, 7, 15, 7)  // Augmenté de 1.5x (10, 5, 10, 5)
                        setBackgroundColor(avecOpacite(accentColor, 0x20))
                        setSingleLine(true)
                        // Filet de sécurité : un mot composé suivi de sa glose
                        // peut dépasser la largeur de l'écran, et le calcul de
                        // passage à la ligne ne saurait alors où le couper.
                        maxWidth = screenWidth
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            rightMargin = 5
                            bottomMargin = 5
                        }
                    }
                    
                    // Mesurer la largeur du mot avant de l'ajouter
                    wordChip.measure(
                        View.MeasureSpec.UNSPECIFIED,
                        View.MeasureSpec.UNSPECIFIED
                    )
                    val wordWidth = wordChip.measuredWidth + 10 // +marge droite + espace sécurité
                    
                    // Si le mot ne rentre pas dans la ligne actuelle, créer une nouvelle ligne
                    if (currentRowWidth + wordWidth > screenWidth && currentRowWidth > 0) {
                        currentRow = LinearLayout(this@SettingsActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.START or Gravity.CENTER_VERTICAL
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                bottomMargin = 6
                            }
                        }
                        wordsContainer.addView(currentRow)
                        currentRowWidth = 0
                    }
                    
                    currentRow.addView(wordChip)
                    currentRowWidth += wordWidth
                }
                
                scrollView.addView(wordsContainer)
                addView(scrollView)
            }
        }
    }
    
    // === Fonctions de chargement de données ===
    
    data class VocabularyStats(
        val totalWords: Int,
        val wordsDiscovered: Int,
        val totalUsages: Int,
        val topWords: List<Pair<String, Int>>,
        val coveragePercentage: Float,
        val discoveredWordsList: List<String>,
        val wordsToDiscover: List<String>
    )
    
    private fun loadVocabularyStats(): VocabularyStats {
        Log.d("SettingsActivity", "🔍 Chargement des statistiques du vocabulaire")
        return try {
            // Toujours charger le total depuis le dictionnaire source
            val totalDictWords = getTotalDictionaryWords()
            
            // Essayer le fichier avec usage
            val usageFile = File(filesDir, "luxemburgish_dict_with_usage.json")
            Log.d("SettingsActivity", "📂 Fichier usage existe: ${usageFile.exists()}")
            Log.d("SettingsActivity", "📂 Chemin fichier: ${usageFile.absolutePath}")
            
            if (usageFile.exists()) {
                val jsonString = usageFile.readText()
                Log.d("SettingsActivity", "📄 Contenu fichier (${jsonString.length} chars): ${jsonString.take(200)}...")
                val jsonObject = JSONObject(jsonString)
                Log.d("SettingsActivity", "🔑 Clés JSON trouvées: ${jsonObject.keys().asSequence().toList().size}")
                
                var wordsDiscovered = 0
                var totalUsages = 0
                val wordUsages = mutableListOf<Pair<String, Int>>()
                val discoveredWords = mutableListOf<String>()
                
                val motsTrouves = mutableListOf<String>()
                jsonObject.keys().forEach { word ->
                    // Gérer les deux formats possibles
                    val userCount = try {
                        val rawValue = jsonObject.get(word)
                        when (rawValue) {
                            is Int -> {
                                // Format simplifié: "mot": 1
                                rawValue
                            }
                            is JSONObject -> {
                                // Format complet: "mot": {"frequency": X, "user_count": Y}
                                rawValue.optInt("user_count", 0)
                            }
                            else -> 0
                        }
                    } catch (e: Exception) {
                        Log.e("SettingsActivity", "Erreur lecture '$word': ${e.message}")
                        0
                    }
                    
                    if (userCount > 0) {
                        totalUsages += userCount
                        wordUsages.add(Pair(word, userCount))
                        motsTrouves.add("$word($userCount)")

                        // Un mot est "découvert" dès qu'il a été utilisé au moins une fois
                        // (même définition que CreoleDictionaryWithUsage.getDiscoveredWordsCount())
                        wordsDiscovered++
                        // Ne garder que les mots de 3 lettres ou plus pour l'affichage
                        if (word.length >= 3) {
                            discoveredWords.add(word)
                        }
                    }
                }
                
                Log.d("SettingsActivity", "Mots avec usage > 0: ${motsTrouves.joinToString(", ")}")
                Log.d("SettingsActivity", "Total: $totalDictWords mots, Usage: $totalUsages, Découverts: $wordsDiscovered")
                
                val topWords = wordUsages.filter { it.first.length >= 3 }.sortedByDescending { it.second }.take(5)
                    .map { formeAffichee(it.first) to it.second }
                val coverage = if (totalDictWords > 0) (wordsDiscovered.toFloat() / totalDictWords * 100) else 0f
                
                // Les mots à découvrir : peu ou pas employés, et proposables.
                //
                // Ils étaient tirés dans le dictionnaire entier, sans filtre :
                // d'où les noms de localités sans glose (« Ierpeldeng-Sauer »)
                // et le vocabulaire d'actualité que le corpus de dépêches
                // charrie — c'est ainsi que « Ramadan » se retrouvait proposé.
                // estProposable() exige une glose qui apprenne quelque chose et
                // écarte le confessionnel ; il écarte aussi, par la seule
                // exigence de glose, l'essentiel des noms propres, personnalités
                // politiques comprises.
                //
                // Le compteur se lit comme plus haut : optInt() ne reconnaît que
                // l'entier nu des premières versions, si bien que le seuil ne
                // filtrait rien du tout.
                val dejaEmploye = wordUsages.toMap()
                val wordsToDiscoverCandidates = jsonObject.keys().asSequence()
                    .filter { word ->
                        word.length >= 3 &&
                            (dejaEmploye[word] ?: 0) <= 2 &&
                            TranslationDictionary.estProposable(this, word)
                    }
                    .toList()
                val wordsToDiscoverList = wordsToDiscoverCandidates.shuffled().take(5).map { formeAffichee(it) }
                
                return VocabularyStats(
                    totalDictWords,
                    wordsDiscovered,
                    totalUsages,
                    topWords,
                    coverage,
                    discoveredWords.map { formeAffichee(it) }.sortedBy { it.lowercase() },
                    wordsToDiscoverList
                )
            }
            
            // Sinon créer un fichier vide pour la première installation
            val emptyUsageObject = JSONObject()
            usageFile.writeText(emptyUsageObject.toString())
            
            // Retourner des statistiques avec le vrai total de mots du dictionnaire
            return VocabularyStats(
                totalWords = totalDictWords,
                wordsDiscovered = 0,
                totalUsages = 0,
                topWords = emptyList(),
                coveragePercentage = 0f,
                discoveredWordsList = emptyList(),
                wordsToDiscover = emptyList()
            )
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur chargement stats: ${e.message}")
            VocabularyStats(0, 0, 0, emptyList(), 0f, emptyList(), emptyList())
        }
    }
    
    // Niveaux : la logique vit désormais dans gamification/LuxLevels.kt, pour
    // que le service de saisie puisse détecter un passage de niveau au moment
    // où l'utilisateur tape. Les méthodes ci-dessous restent des raccourcis
    // locaux qui fournissent la taille du dictionnaire.

    private fun getCurrentLevel(wordsDiscovered: Int): String =
        LuxLevels.labelFor(wordsDiscovered, getTotalDictionaryWords())

    private fun getNextLevelInfo(wordsDiscovered: Int): Pair<String, Int> =
        LuxLevels.nextLevelInfo(wordsDiscovered, getTotalDictionaryWords())

    /**
     * Index du niveau actuel (0 = Pipirit ... 7 = Benzo), même logique que getCurrentLevel()
     */
    private fun getCurrentLevelIndex(wordsDiscovered: Int): Int =
        LuxLevels.indexFor(wordsDiscovered, getTotalDictionaryWords())

    /**
     * Affiche la célébration de passage de niveau avec carte partageable.
     * Ne se déclenche que sur une progression réelle : au premier passage,
     * le niveau courant est mémorisé silencieusement (pas de célébration
     * rétroactive pour un utilisateur existant).
     */
    private fun maybeCelebrateLevelUp(wordsDiscovered: Int, levelEmoji: String, levelName: String) {
        try {
            val prefs = getSharedPreferences("lux_gamification_prefs", Context.MODE_PRIVATE)
            val currentIndex = getCurrentLevelIndex(wordsDiscovered)
            val lastCelebrated = prefs.getInt("last_celebrated_level_index", -1)

            if (lastCelebrated == -1) {
                prefs.edit().putInt("last_celebrated_level_index", currentIndex).apply()
                return
            }
            if (currentIndex <= lastCelebrated) return

            prefs.edit().putInt("last_celebrated_level_index", currentIndex).apply()

            val cardBitmap = buildLevelCardBitmap(levelEmoji, levelName, wordsDiscovered)

            val preview = ImageView(this).apply {
                setImageBitmap(cardBitmap)
                adjustViewBounds = true
                setPadding(32, 24, 32, 8)
            }

            AlertDialog.Builder(this)
                .setTitle("🎉 Bravo ! Dir sidd virugaangen !")
                .setMessage(getString(R.string.sa_dir_hutt_den_niveau_erreecht, levelName))
                .setView(preview)
                .setPositiveButton(getString(R.string.sa_partager)) { _, _ -> shareLevelCard(cardBitmap, levelName) }
                .setNegativeButton(getString(R.string.sa_plus_tard), null)
                .show()
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur célébration de niveau: ${e.message}")
        }
    }

    /**
     * Dessine la carte de niveau partageable (1080×1350, format portrait réseaux sociaux)
     */
    private fun buildLevelCardBitmap(levelEmoji: String, levelName: String, wordsDiscovered: Int): Bitmap {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = width / 2f

        // Fond dégradé mer des Caraïbes
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                Color.parseColor("#0E6E76"), Color.parseColor("#052E33"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Soleil décoratif en haut à droite
        val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#33F6E9D2")
        }
        canvas.drawCircle(width - 120f, 130f, 190f, sunPaint)

        // Eyebrow
        val eyebrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E3AE5E")
            textSize = 42f
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("MÄI NIVEAU OP LËTZEBUERGESCH", cx, 240f, eyebrowPaint)

        // Emoji du niveau
        val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 280f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(levelEmoji, cx, 620f, emojiPaint)

        // Nom du niveau
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 116f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(levelName, cx, 790f, namePaint)

        // Compteur de mots
        val statsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DDEEEE")
            textSize = 54f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(getString(R.string.sa_mots_luxembourgeois_decouverts, wordsDiscovered), cx, 900f, statsPaint)

        // Séparateur
        val linePaint = Paint().apply { color = Color.parseColor("#33FFFFFF"); strokeWidth = 3f }
        canvas.drawLine(cx - 220f, 1010f, cx + 220f, 1010f, linePaint)

        // Pied de carte
        val footerBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 56f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("Lëtzebuergesch Clavier", cx, 1120f, footerBoldPaint)

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A9D4D6")
            textSize = 42f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Gratis Tastatur um Google Play", cx, 1195f, footerPaint)

        return bitmap
    }

    /**
     * Enregistre la carte dans le cache et ouvre le sélecteur de partage
     * (image + texte avec lien tracké utm_source=level_share)
     */
    private fun shareLevelCard(bitmap: Bitmap, levelName: String) {
        try {
            val imagesDir = File(cacheDir, "images").apply { mkdirs() }
            val imageFile = File(imagesDir, "niveau_lux.png")
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", imageFile)

            val message = getString(R.string.sa_ech_sinn_um_niveau_am, levelName, packageName) +
                    SHARE_HASHTAG

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, message)
                // Le flag FLAG_GRANT_READ_URI_PERMISSION ne s'applique qu'à l'URI
                // porté par setData()/ClipData, pas à EXTRA_STREAM seul. Sans ClipData,
                // sous Android 14 l'aperçu du sélecteur ET l'app cible (ex. Messages)
                // reçoivent un SecurityException et l'image ne s'attache pas (le partage
                // retombe en SMS texte). On expose donc l'URI via ClipData pour que la
                // permission de lecture soit bien propagée.
                clipData = ClipData.newUri(contentResolver, "niveau_lux.png", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.sa_partager_ma_carte_de_niveau)))
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur partage carte de niveau: ${e.message}")
            Toast.makeText(this, getString(R.string.sa_impossible_de_partager_la_carte), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Calcule les seuils de niveau de façon dynamique selon la taille du dictionnaire
     *
     * Progression motivante basée sur des pourcentages du dictionnaire total:
     * - Pipirit (début): 0% - démarrage
     * - Ti moun: 1.5% - premiers pas (rapide à atteindre!)
     * - Débrouya: 5% - débrouillard
     * - An mitan: 12% - au milieu du chemin
     * - Kompè Lapen: 25% - bon niveau
     * - Kompè Zamba: 45% - niveau avancé
     * - Potomitan: 70% - expert
     * - Benzo: 100% - maître absolu (tous les mots!)
     * 
     * Avantages:
     * - S'adapte automatiquement à la croissance du dictionnaire
     * - Progression douce au début (1.5% pour Ti moun)
     * - Écarts progressifs entre niveaux (motivant!)
     * - Benzo reste l'objectif ultime (100%)
     * 
     * Exemples pour 3680 mots:
     * - Ti moun: 55 mots, Débrouya: 184 mots, An mitan: 442 mots
     * - Kompè Lapen: 920 mots, Kompè Zamba: 1656 mots
     * - Potomitan: 2576 mots, Benzo: 3680 mots
     * 
     * @return IntArray avec 8 seuils calculés dynamiquement
     */
    private fun calculateGaussianThresholds(): IntArray =
        LuxLevels.thresholds(getTotalDictionaryWords())
    
    /**
     * Récupère le nombre total de mots dans le dictionnaire
     * Utilise un cache pour éviter de relire le fichier à chaque fois
     */
    private var cachedTotalWords: Int? = null
    private var formesCanoniques: Map<String, String> = emptyMap()

    /**
     * Forme d'un mot telle que le dictionnaire l'écrit, majuscule des noms
     * comprise. Repli sur le mot lui-même si le dictionnaire ne le connaît pas.
     */
    private fun formeAffichee(mot: String): String {
        getTotalDictionaryWords()
        return formesCanoniques[mot.lowercase()] ?: mot
    }
    
    private fun getTotalDictionaryWords(): Int {
        // Retourner depuis le cache si disponible
        cachedTotalWords?.let { return it }
        
        return try {
            // Toujours charger le dictionnaire source depuis assets
            // car luxemburgish_dict_with_usage.json peut être vide (nouveau install)
            val jsonString = assets.open("luxemburgish_dict.json").bufferedReader().use { it.readText() }
            val jsonArray = org.json.JSONArray(jsonString)
            val count = jsonArray.length()

            // Même lecture, pour la casse : les clés du fichier d'usage sont en
            // minuscules, le dictionnaire porte la forme à afficher. Il est trié
            // par fréquence décroissante, donc pour un homographe (« Froen » /
            // « froen ») la première rencontrée est la plus courante.
            val formes = HashMap<String, String>(count * 2)
            for (i in 0 until count) {
                val forme = jsonArray.optJSONArray(i)?.optString(0).orEmpty()
                if (forme.isNotEmpty()) formes.putIfAbsent(forme.lowercase(), forme)
            }
            formesCanoniques = formes

            cachedTotalWords = count
            Log.d("SettingsActivity", "📊 Total mots dictionnaire: $count")
            count
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur comptage mots: ${e.message}")
            // Repli sur la taille connue du dictionnaire livré. Sert de
            // dénominateur aux paliers de progression : une valeur trop basse
            // ferait afficher des pourcentages supérieurs à 100 %.
            37734
        }
    }
    
    // Adapter pour ViewPager2 avec swipe cyclique
    private class SettingsPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        companion object {
            const val REAL_COUNT = 4 // Nombre réel d'onglets (jeux regroupés)
            const val VIRTUAL_COUNT = Int.MAX_VALUE // Nombre virtuel pour simuler l'infini
            const val START_POSITION = VIRTUAL_COUNT / 2 // Position de départ au milieu
        }

        override fun getItemCount(): Int = VIRTUAL_COUNT

        override fun createFragment(position: Int): Fragment {
            // Utiliser le modulo pour revenir aux vraies pages
            val realPosition = position % REAL_COUNT
            return when (realPosition) {
                0 -> OnboardingFragment()
                1 -> GamesFragment()
                2 -> DictionaryFragment()
                3 -> StatsFragment()
                else -> OnboardingFragment()
            }
        }
    }
    
    // Fragment pour le démarrage / onboarding
    class OnboardingFragment : Fragment() {
        private var rootView: ScrollView? = null

        // Observe les réglages système du clavier au lieu de les sonder
        // toutes les 2 secondes : réaction immédiate quand l'utilisateur
        // active ou sélectionne le clavier (notamment pendant que le
        // sélecteur système est affiché par-dessus l'activité, qui reste
        // resumed), et plus de Handler périodique qui tourne à vide.
        private val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val activity = activity as? SettingsActivity ?: return
                val wasEnabled = lastKnownEnabled
                val wasSelected = lastKnownSelected
                val changed = shouldRefresh(
                    activity.isKeyboardEnabled(),
                    activity.isKeyboardSelected(),
                    activity.isSpellCheckerSelected()
                )
                if (changed) {
                    refreshContent()
                    chainNextStep(wasEnabled, wasSelected)
                    confirmerCorrecteurSiActive()
                }
            }
        }

        override fun onCreateView(
            inflater: android.view.LayoutInflater,
            container: android.view.ViewGroup?,
            savedInstanceState: android.os.Bundle?
        ): View {
            val activity = requireActivity() as SettingsActivity
            rootView = ScrollView(activity).apply {
                isFillViewport = true
                isVerticalScrollBarEnabled = true
            }
            refreshContent()
            return rootView!!
        }
        
        override fun onResume() {
            super.onResume()
            // Rafraîchir pour rattraper les changements survenus pendant que
            // le fragment était masqué (ex. activation dans les réglages
            // système), puis observer les réglages en continu
            val wasEnabled = lastKnownEnabled
            val wasSelected = lastKnownSelected
            refreshContent()
            chainNextStep(wasEnabled, wasSelected)
            confirmerCorrecteurSiActive()

            val resolver = requireContext().contentResolver
            resolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD), false, settingsObserver)
            resolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_INPUT_METHODS), false, settingsObserver)
            resolver.registerContentObserver(
                Settings.Secure.getUriFor("selected_spell_checker"), false, settingsObserver)
            resolver.registerContentObserver(
                Settings.Secure.getUriFor("spell_checker_enabled"), false, settingsObserver)
        }

        override fun onPause() {
            super.onPause()
            requireContext().contentResolver.unregisterContentObserver(settingsObserver)
        }

        private var lastKnownEnabled = false
        private var lastKnownSelected = false
        private var lastKnownSpellCheckerOn = false

        // État du correcteur au dernier passage, null avant le premier : sans
        // cela, ouvrir l'application avec un correcteur déjà actif afficherait
        // la confirmation à chaque fois.
        private var correcteurConnu: Boolean? = null

        /**
         * Dit « c'est bon » quand le correcteur vient de passer actif, en
         * général au retour de l'écran système où l'utilisateur l'a choisi.
         * Sans ce mot, il revient sans savoir si sa manipulation a marché.
         */
        private fun confirmerCorrecteurSiActive() {
            val avant = correcteurConnu
            correcteurConnu = lastKnownSpellCheckerOn
            if (avant == false && lastKnownSpellCheckerOn) {
                Toast.makeText(requireContext(),
                    getString(R.string.sa_correcteur_active_vos_mots_luxembourgeois),
                    Toast.LENGTH_LONG).show()
            }
        }

        private fun shouldRefresh(currentEnabled: Boolean, currentSelected: Boolean, currentSpellCheckerOn: Boolean): Boolean {
            val hasChanged = currentEnabled != lastKnownEnabled || currentSelected != lastKnownSelected || currentSpellCheckerOn != lastKnownSpellCheckerOn
            lastKnownEnabled = currentEnabled
            lastKnownSelected = currentSelected
            lastKnownSpellCheckerOn = currentSpellCheckerOn
            return hasChanged
        }

        private fun refreshContent() {
            val activity = requireActivity() as SettingsActivity
            lastKnownEnabled = activity.isKeyboardEnabled()
            lastKnownSelected = activity.isKeyboardSelected()
            lastKnownSpellCheckerOn = activity.isSpellCheckerSelected()
            rootView?.removeAllViews()
            rootView?.addView(activity.createOnboardingContent())
            if (lastKnownEnabled && lastKnownSelected) {
                // Configuration aboutie : révéler la navigation (idempotent)
                activity.onOnboardingCompleted()
            } else {
                // Activé mais pas encore sélectionné : la navigation revient
                // quand même, elle ne dépend que de l'activation.
                activity.revelerNavigationSiClavierActive()
            }
            Log.d("SettingsActivity", "🔄 Contenu de l'onboarding rafraîchi (enabled=$lastKnownEnabled, selected=$lastKnownSelected, spellChecker=$lastKnownSpellCheckerOn)")
        }

        // Enchaîne automatiquement l'étape suivante quand une action système
        // vient d'aboutir, pour économiser des taps de navigation : clavier
        // sélectionné → focus sur le champ de test (le clavier Kréyòl
        // apparaît aussitôt) ; clavier activé (retour des réglages système)
        // → ouverture directe du sélecteur. À appeler après refreshContent(),
        // qui met à jour lastKnownEnabled/lastKnownSelected. Le délai initial
        // laisse l'utilisateur voir l'étape passer au vert avant la suite.
        private fun chainNextStep(wasEnabled: Boolean, wasSelected: Boolean) {
            when {
                !wasSelected && lastKnownSelected -> rootView?.postDelayed({
                    runWhenWindowFocused { focusTestField() }
                }, 400)
                !wasEnabled && lastKnownEnabled -> rootView?.postDelayed({
                    runWhenWindowFocused {
                        (activity as? SettingsActivity)?.openInputMethodPicker()
                    }
                }, 400)
            }
        }

        // showInputMethodPicker() et showSoftInput() sont ignorés par le
        // système tant que la fenêtre n'a pas repris le focus après le retour
        // des réglages (InputMethodManagerService rejette les clients non
        // courants, vu dans logcat : « Ignoring showInputMethodPickerFromClient »).
        // On attend donc le focus fenêtre, plus une courte marge pour que le
        // système réenregistre l'activité comme client de saisie courant.
        private fun runWhenWindowFocused(attemptsLeft: Int = 10, action: () -> Unit) {
            if (!isAdded) return
            if (requireActivity().hasWindowFocus()) {
                rootView?.postDelayed({ if (isAdded) action() }, 150)
            } else if (attemptsLeft > 0) {
                rootView?.postDelayed({ runWhenWindowFocused(attemptsLeft - 1, action) }, 200)
            }
        }

        private fun focusTestField() {
            if ((activity as? SettingsActivity)?.pochetteAccueilOuverte == true) return
            val field = rootView?.findViewWithTag<EditText>("onboarding_test_field") ?: return
            field.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        }
    }
    
    // Fragment pour l'à propos
    class AboutFragment : Fragment() {
        override fun onCreateView(
            inflater: android.view.LayoutInflater,
            container: android.view.ViewGroup?,
            savedInstanceState: android.os.Bundle?
        ): View {
            val activity = requireActivity() as SettingsActivity
            val scrollView = ScrollView(activity)
            scrollView.addView(activity.createAboutContent())
            return scrollView
        }
    }

    // Fragment pour le guide de l'utilisateur
    class GuideFragment : Fragment() {
        override fun onCreateView(
            inflater: android.view.LayoutInflater,
            container: android.view.ViewGroup?,
            savedInstanceState: android.os.Bundle?
        ): View {
            val activity = requireActivity() as SettingsActivity
            val scrollView = ScrollView(activity)
            scrollView.addView(activity.createGuideContent())
            return scrollView
        }
    }

    // Fragment pour les statistiques
    class StatsFragment : Fragment() {
        private var scrollView: ScrollView? = null

        override fun onCreateView(
            inflater: android.view.LayoutInflater,
            container: android.view.ViewGroup?,
            savedInstanceState: android.os.Bundle?
        ): View {
            Log.d("SettingsActivity", "Création de la vue StatsFragment")
            val activity = requireActivity() as SettingsActivity

            // Créer le SwipeRefreshLayout pour le Pull-to-Refresh
            val swipeRefreshLayout = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(activity).apply {
                setColorSchemeColors(
                    Color.parseColor("#0080FF"), // Bleu principal
                    Color.parseColor("#4CAF50"), // Vert
                    Color.parseColor("#FF9800")  // Orange
                )
                setProgressBackgroundColorSchemeColor(Color.WHITE)

                // Configurer l'action de rafraîchissement
                setOnRefreshListener {
                    Log.d("SettingsActivity", "🔄 Pull-to-Refresh déclenché")

                    // Afficher un message
                    Toast.makeText(activity, getString(R.string.sa_actualisation_des_statistiques), Toast.LENGTH_SHORT).show()

                    // Attendre un peu puis recréer l'activité
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        Log.d("SettingsActivity", "🔄 Rechargement de l'activité après pull-to-refresh")
                        activity.recreate() // Redémarre complètement l'activité
                    }, 500) // Attendre 500ms
                }
            }

            val scrollView = ScrollView(activity).apply {
                setBackgroundColor(Color.WHITE)
                isFillViewport = true
            }
            this.scrollView = scrollView
            val statsContent = activity.createStatsContent()
            scrollView.addView(statsContent)

            // Ajouter le ScrollView dans le SwipeRefreshLayout
            swipeRefreshLayout.addView(scrollView)
            
            Log.d("SettingsActivity", "StatsFragment créé avec Pull-to-Refresh")
            return swipeRefreshLayout
        }

        override fun onResume() {
            super.onResume()
            // Recharge les stats à chaque retour au premier plan (ex. après une session de
            // frappe) : sans ce rafraîchissement, l'onglet réaffiche les chiffres capturés à
            // sa création jusqu'à un pull-to-refresh manuel ou un redémarrage de l'activité.
            val activity = requireActivity() as? SettingsActivity ?: return
            val container = scrollView ?: return
            container.removeAllViews()
            container.addView(activity.createStatsContent())
        }

        override fun onDestroyView() {
            super.onDestroyView()
            scrollView = null
        }
    }
    
    /**
     * Astuce de la semaine : l'index suit le numéro de semaine plutôt qu'un
     * tirage aléatoire seedé sur la date (comme [getWordOfTheDay]), pour que la
     * liste soit parcourue en entier et que deux semaines de suite ne retombent
     * jamais sur la même astuce. Le décalage de fuseau est ajouté pour que le
     * changement se fasse à minuit local et non à minuit UTC.
     *
     * Le +3 cale la bascule sur le lundi : le jour 0 de l'ère Unix étant un
     * jeudi, sans lui l'astuce changerait en plein milieu de semaine.
     */
    private fun getTipOfTheWeek(): String {
        val calendar = Calendar.getInstance()
        val localMillis = calendar.timeInMillis +
                calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)
        val dayIndex = TimeUnit.MILLISECONDS.toDays(localMillis)
        val weekIndex = (dayIndex + 3) / 7
        val astuces = resources.getStringArray(R.array.astuces_semaine)
        return astuces[(weekIndex % astuces.size).toInt()]
    }

    /**
     * Tire un mot du jour que l'application peut proposer — glose instructive,
     * et pas une forme mise à l'écart — en gardant le tirage déterministe pour
     * la journée.
     *
     * On ne filtre pas la liste avant de tirer : la construire coûterait un
     * parcours de 38 000 formes à chaque ouverture de l'onglet, pour une chance
     * sur deux de tomber juste du premier coup. Quelques essais successifs sur
     * le même générateur suffisent dans l'immense majorité des cas.
     *
     * Deux garde-fous, parce qu'un mot du jour sans glose ne se signale par
     * rien — la ligne de traduction passe en `GONE` et l'écran a l'air normal,
     * seulement moins utile :
     *
     * - le critère est [TranslationDictionary.estProposable] et non la simple
     *   présence d'une glose. Le luxembourgeois a emprunté assez de mots au
     *   français pour que 1 278 formes se glosent par elles-mêmes
     *   (« Accident » → accident) : la traduction est là, elle n'apprend rien,
     *   et [MotsEcartes] retire au passage le vocabulaire confessionnel que le
     *   corpus de dépêches charrie ;
     * - si les vingt tirages échouent — table absente, ou malchance — on
     *   parcourt la liste au lieu de rendre le dernier tirage tel quel. Le
     *   parcours part de l'index tiré et reste donc déterministe : le mot du
     *   jour ne change pas d'un affichage à l'autre dans la journée.
     */
    private fun tirerMotTraduisible(mots: List<String>, random: Random): String {
        var index = random.nextInt(mots.size)
        repeat(20) {
            if (TranslationDictionary.estProposable(this, mots[index])) {
                return mots[index]
            }
            index = random.nextInt(mots.size)
        }
        for (decalage in mots.indices) {
            val candidat = mots[(index + decalage) % mots.size]
            if (TranslationDictionary.estProposable(this, candidat)) return candidat
        }
        // Aucune glose nulle part : l'actif manque. Un mot sans traduction vaut
        // mieux qu'un écran vide.
        return mots[index]
    }

    private fun getWordOfTheDay(): Pair<String, Int> {
        return try {
            val usageFile = File(filesDir, "luxemburgish_dict_with_usage.json")
            
            val allWords: List<String>
            val usageCount: Int
            
            if (usageFile.exists()) {
                val jsonString = usageFile.readText()
                val jsonObject = JSONObject(jsonString)
                
                allWords = mutableListOf<String>().apply {
                    jsonObject.keys().forEach { word -> add(word) }
                }
                
                if (allWords.isEmpty()) {
                    return Pair("Moien", 0)
                }
                
                // Utiliser la date comme seed pour avoir le même mot toute la journée
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dateString = dateFormat.format(Date())
                val seed = dateString.hashCode().toLong()
                val random = Random(seed)
                
                val selectedWord = tirerMotTraduisible(allWords, random)
                // Le fichier d'usage porte deux formats : l'objet
                // {"frequency", "user_count"} qu'écrit CreoleDictionaryWithUsage,
                // et l'entier nu des toutes premières versions. optInt() ne lit
                // que le second, si bien qu'un mot déjà employé cent fois
                // s'annonçait quand même « nouveau mot à découvrir ».
                usageCount = when (val brut = jsonObject.opt(selectedWord)) {
                    is Int -> brut
                    is JSONObject -> brut.optInt("user_count", 0)
                    else -> 0
                }
                
                return Pair(selectedWord, usageCount)
            } else {
                Log.d("SettingsActivity", "Fichier usage n'existe pas, création depuis assets")
                // Charger depuis les assets
                val jsonString = assets.open("luxemburgish_dict.json").bufferedReader().use { it.readText() }
                val jsonArray = org.json.JSONArray(jsonString)
                Log.d("SettingsActivity", "Dictionnaire chargé: ${jsonArray.length()} mots")
                
                allWords = mutableListOf<String>().apply {
                    for (i in 0 until jsonArray.length()) {
                        val wordArray = jsonArray.getJSONArray(i)
                        add(wordArray.getString(0))  // Premier élément = le mot
                    }
                }
                
                if (allWords.isEmpty()) {
                    return Pair("Moien", 0)
                }
                
                // Utiliser la date comme seed
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dateString = dateFormat.format(Date())
                val seed = dateString.hashCode().toLong()
                val random = Random(seed)
                
                val selectedWord = tirerMotTraduisible(allWords, random)
                
                return Pair(selectedWord, 0)
            }
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur mot du jour: ${e.message}")
            Pair("Moien", 0)
        }
    }
    
    /**
     * 🎨 Transformateur personnalisé pour effet Tinder Swipe
     * 
     * Caractéristiques :
     * - Rotation de -15° à +15° selon la direction du swipe
     * - Translation verticale : la carte se soulève légèrement
     * - Scale : la carte rétrécit un peu en s'éloignant
     * - Fade out progressif
     * - Élévation : la page courante est au-dessus
     */
    private class TinderSwipeTransformer : ViewPager2.PageTransformer {
        override fun transformPage(page: View, position: Float) {
            page.apply {
                when {
                    position < -1 -> { // [-Infinity,-1)
                        // Page complètement à gauche, hors écran
                        alpha = 0f
                        translationX = 0f
                        translationY = 0f
                        rotation = 0f
                        scaleX = 1f
                        scaleY = 1f
                    }
                    position <= 1 -> { // [-1,1]
                        // Page visible ou en transition
                        
                        // 🎯 Effet Tinder : rotation + translation + scale
                        val absPosition = Math.abs(position)
                        
                        // Rotation de -15° à +15° selon la direction du swipe
                        rotation = -15f * position
                        
                        // Translation verticale : la carte se soulève légèrement
                        translationY = -Math.abs(position) * 50f
                        
                        // Translation horizontale pour accentuer le mouvement
                        translationX = position * width * 0.3f
                        
                        // Scale : la carte rétrécit un peu en s'éloignant
                        val scale = 1f - absPosition * 0.2f
                        scaleX = scale
                        scaleY = scale
                        
                        // Alpha : fade out progressif
                        alpha = 1f - absPosition * 0.5f
                        
                        // Élévation : la page courante est au-dessus
                        elevation = (1f - absPosition) * 10f
                    }
                    else -> { // (1,+Infinity]
                        // Page complètement à droite, hors écran
                        alpha = 0f
                        translationX = 0f
                        translationY = 0f
                        rotation = 0f
                        scaleX = 1f
                        scaleY = 1f
                    }
                }
            }
        }
    }
    
    // Fragment pour les mots mêlés
    class WordSearchFragment : Fragment() {
        
        /**
         * Ce qui doit survivre à une rotation : la grille, ses mots trouvés et
         * le score. Même raison que pour Kräizwuert, voir
         * [CrosswordFragment.Partie].
         */
        class Partie : androidx.lifecycle.ViewModel() {
            var puzzle: WordSearchPuzzle? = null
            val casesTrouvees = mutableSetOf<Int>()
            var startTime: Long = 0
            var wordsFound = 0
            var score = 0
            val gagnes = mutableListOf<String>()
            val neuves = mutableSetOf<String>()
        }

        private val memoire by lazy {
            androidx.lifecycle.ViewModelProvider(this)[Partie::class.java]
        }

        private var currentPuzzle: WordSearchPuzzle?
            get() = memoire.puzzle
            set(valeur) { memoire.puzzle = valeur }
        private var startTime: Long
            get() = memoire.startTime
            set(valeur) { memoire.startTime = valeur }
        private var wordsFound: Int
            get() = memoire.wordsFound
            set(valeur) { memoire.wordsFound = valeur }
        private lateinit var gridView: GridView
        private lateinit var wordsListContainer: LinearLayout
        private lateinit var tvTheme: TextView
        private lateinit var tvScore: TextView
        private lateinit var boutonCarnet: TextView

        /** Les formes gagnées dans la grille en cours, dans l'ordre du tracé. */
        private val gagnes get() = memoire.gagnes

        /** Celles que le carnet n'avait jamais vues. */
        private val neuves get() = memoire.neuves

        /** La pochette de fin de grille, posée au-dessus de tout. */
        private var pochette: View? = null
        
        override fun onCreateView(
            inflater: android.view.LayoutInflater,
            container: android.view.ViewGroup?,
            savedInstanceState: android.os.Bundle?
        ): View {
            val activity = requireActivity() as SettingsActivity
            
            // ScrollView pour permettre le défilement si nécessaire
            return ScrollView(activity).apply {
                setBackgroundColor(Color.parseColor("#F5F5F5"))
                
                val mainLayout = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(8, 8, 8, 8) // Réduction du padding de 16 à 8
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    
                    // En-tête avec thème et score
                    val headerLayout = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(8, 4, 8, 8) // Réduction du padding
                        gravity = Gravity.CENTER_VERTICAL
                        
                        tvTheme = TextView(activity).apply {
                            text = getString(R.string.sa_chargement)
                            textSize = 16f
                            setTextColor(Color.parseColor("#9C27B0"))
                            setTypeface(null, Typeface.BOLD)
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            )
                        }
                        addView(tvTheme)
                        
                        tvScore = TextView(activity).apply {
                            text = "⭐ 0"
                            textSize = 16f
                            setTextColor(Color.parseColor("#FF9800"))
                            setTypeface(null, Typeface.BOLD)
                            gravity = Gravity.END
                        }
                        addView(tvScore)

                        // L'entrée du carnet, dans l'en-tête de chaque jeu.
                        // Elle reste visible même quand rien n'a été gagné :
                        // un joueur qui revient doit retrouver sa collection
                        // sans avoir à finir une grille d'abord.
                        boutonCarnet = Pochette.bouton(
                            this@WordSearchFragment,
                            Color.parseColor("#9C27B0"),
                            petit = true
                        ).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { leftMargin = 12 }
                        }
                        addView(boutonCarnet)
                    }
                    addView(headerLayout)
                    
                    // Grille de mots mêlés
                    gridView = GridView(activity).apply {
                        // Calculer la taille disponible pour la grille
                        val screenWidth = LargeurLecture.largeurEcran(activity)
                        val deuxColonnes = DeuxColonnes.actives(activity)
                        val availableWidth = ((screenWidth - 48) *
                            (if (deuxColonnes) DeuxColonnes.PART_GRILLE else 1f)).toInt()
                        
                        // La grille est toujours 8x8
                        val gridSize = 8
                        // Calculer la taille d'une cellule en fonction de la largeur
                        var cellSize = availableWidth / gridSize
                        // Sur tablette, la largeur seule donnait des cases de
                        // trois centimètres et une grille plus haute que
                        // l'écran : la hauteur borne aussi. Un téléphone
                        // couché avait le même défaut, en pire : une seule
                        // rangée visible. Debout, il garde sa grille.
                        val configuration = resources.configuration
                        if (configuration.smallestScreenWidthDp >= 600 ||
                            configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                            val budgetHauteur = (resources.displayMetrics.heightPixels *
                                (if (deuxColonnes) DeuxColonnes.PART_HAUTEUR else 0.5f)).toInt()
                            cellSize = minOf(cellSize, budgetHauteur / gridSize)
                        }
                        // Hauteur de la grille = 8 cellules + espacements + padding
                        val gridHeight = (cellSize * gridSize) + (4 * (gridSize - 1)) + 24
                        
                        layoutParams = if (cellSize * gridSize < availableWidth) {
                            LinearLayout.LayoutParams(gridHeight, gridHeight)
                                .apply { gravity = Gravity.CENTER_HORIZONTAL }
                        } else {
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                gridHeight
                            )
                        }
                        setPadding(12, 12, 12, 12)
                        stretchMode = GridView.STRETCH_COLUMN_WIDTH
                        setBackgroundColor(Color.parseColor("#F5F5F5")) // Fond gris très clair
                        verticalSpacing = 4 // Espacement vertical entre les lignes
                        horizontalSpacing = 4 // Espacement horizontal entre les colonnes
                        
                        // 🔧 FIX: Gérer les touches au niveau de la GridView pour permettre le swipe entre cellules
                        setOnTouchListener { view, event ->
                            // Demander au parent de ne pas intercepter les événements
                            parent?.requestDisallowInterceptTouchEvent(true)
                            
                            // Calculer quelle cellule est touchée
                            val position = pointToPosition(event.x.toInt(), event.y.toInt())
                            
                            if (position != android.widget.AdapterView.INVALID_POSITION) {
                                val adapter = adapter as? WordSearchGridAdapter
                                adapter?.handleTouchEvent(position, event)
                            }
                            
                            // Réactiver l'interception après ACTION_UP ou ACTION_CANCEL
                            if (event.action == android.view.MotionEvent.ACTION_UP ||
                                event.action == android.view.MotionEvent.ACTION_CANCEL) {
                                parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            
                            true // Consommer l'événement
                        }
                    }
                    addView(gridView)
                    
                    // Bouton nouvelle grille
                    val btnNewGame = Button(activity).apply {
                        text = getString(R.string.sa_nouvelle_grille_2)
                        textSize = 14f
                        setTextColor(Color.WHITE)
                        setBackgroundColor(Color.parseColor("#9C27B0"))
                        setPadding(24, 10, 24, 10) // Réduction du padding vertical
                        setTypeface(null, Typeface.BOLD)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 8, 0, 8) // Réduction des marges de 16 à 8
                        }
                        setOnClickListener {
                            generateNewPuzzle()
                        }
                    }
                    addView(btnNewGame)
                    
                    // Liste des mots à trouver
                    val wordsTitle = TextView(activity).apply {
                        text = getString(R.string.sa_mots_trouver)
                        textSize = 14f // Réduction de 16 à 14
                        setTextColor(Color.parseColor("#333333"))
                        setTypeface(null, Typeface.BOLD)
                        setPadding(8, 4, 8, 4) // Réduction du padding
                    }
                    addView(wordsTitle)
                    
                    wordsListContainer = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(8, 4, 8, 8) // Réduction du padding
                        setBackgroundColor(Color.parseColor("#FFFFFF"))
                    }
                    addView(wordsListContainer)

                    if (DeuxColonnes.actives(activity)) {
                        DeuxColonnes.repartir(
                            this,
                            enHaut = listOf(headerLayout),
                            aGauche = listOf(gridView)
                        )
                    }
                }
                
                addView(mainLayout)
                
                // Générer la première grille après que la vue soit créée
                post {
                    // Le post() s'exécute au prochain passage de la boucle de messages :
                    // si l'utilisateur a déjà changé d'onglet entre-temps, le fragment
                    // n'est plus attaché et requireActivity()/requireContext() planterait.
                    if (isAdded) {
                        val enCours = currentPuzzle
                        if (enCours == null) {
                            generateNewPuzzle()
                        } else {
                            // Après une rotation : la même grille, ses mots
                            // trouvés, le même score.
                            displayPuzzle(enCours)
                            Pochette.rafraichir(boutonCarnet, activity)
                            updateScore(0)
                        }
                    }
                }
            }
        }

        private fun generateNewPuzzle() {
            try {
                val activity = requireActivity() as SettingsActivity

                // Générer une nouvelle grille 8x8 avec des mots aléatoires du dictionnaire
                memoire.casesTrouvees.clear()
                currentPuzzle = WordSearchGenerator.generatePuzzle(
                    context = activity,
                    theme = "lux", // Thème unique
                    gridSize = 8,
                    difficulty = WordSearchDifficulty.NORMAL
                )

                // Afficher la grille
                displayPuzzle(currentPuzzle!!)

                // Réinitialiser
                startTime = System.currentTimeMillis()
                wordsFound = 0
                gagnes.clear()
                neuves.clear()
                enleverPochette()
                Pochette.rafraichir(boutonCarnet, activity)
                updateScore(0)

                Log.d("WordSearchFragment", "Nouvelle grille générée: ${currentPuzzle?.words?.size} mots")

            } catch (e: Exception) {
                Log.e("WordSearchFragment", "Erreur génération: ${e.message}", e)
                // context (nullable) au lieu de requireContext() : si le fragment vient
                // justement d'être détaché, ce bloc catch ne doit pas planter à son tour.
                context?.let { Toast.makeText(it, getString(R.string.sa_erreur_lors_de_la_generation), Toast.LENGTH_SHORT).show() }
            }
        }
        
        private fun displayPuzzle(puzzle: WordSearchPuzzle) {
            val activity = requireActivity() as SettingsActivity
            
            // Configurer l'adaptateur de la grille
            val adapter = WordSearchGridAdapter(activity, puzzle, memoire.casesTrouvees)
            adapter.setOnWordFoundListener { word ->
                onWordFound(word)
            }
            gridView.adapter = adapter
            gridView.numColumns = puzzle.gridSize
            
            // Afficher le titre simple sans thème
            tvTheme.text = getString(R.string.sa_mots_luxembourgeois)
            
            // Afficher la liste des mots
            displayWordsList(puzzle.words)
        }
        
        private fun displayWordsList(words: List<WordSearchWord>) {
            wordsListContainer.removeAllViews()
            val activity = requireActivity() as SettingsActivity
            
            words.forEach { word ->
                val wordView = TextView(activity).apply {
                    // Le mot est déjà donné : afficher sa traduction n'aide pas
                    // à le trouver dans la grille, mais c'est la seule chose
                    // qui distingue une grille de vocabulaire d'un exercice de
                    // repérage de lettres.
                    val puce = if (word.isFound) "✅ " else "📝 "
                    val glose = TranslationDictionary.traduire(activity, word.canonical)
                    val ligne = SpannableStringBuilder(puce).append(word.word.uppercase())
                    if (glose != null) {
                        val debut = ligne.length
                        ligne.append("  ").append(glose)
                        ligne.setSpan(
                            ForegroundColorSpan(Color.parseColor("#777777")),
                            debut, ligne.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        ligne.setSpan(
                            RelativeSizeSpan(0.85f),
                            debut, ligne.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    text = ligne
                    textSize = 14f
                    setPadding(12, 8, 12, 8)
                    setTextColor(if (word.isFound) Color.parseColor("#4CAF50") else Color.parseColor("#333333"))
                    setTypeface(null, if (word.isFound) Typeface.BOLD else Typeface.NORMAL)
                }
                wordsListContainer.addView(wordView)
            }
        }
        
        private fun onWordFound(word: String) {
            wordsFound++
            
            // Mettre à jour la liste
            val trouve = currentPuzzle?.words?.find { it.word.equals(word, ignoreCase = true) }
            trouve?.isFound = true
            displayWordsList(currentPuzzle?.words ?: emptyList())

            encarter(trouve?.canonical)
            
            // Calculer les points
            val points = word.length * 10
            updateScore(points)
            
            // Vérifier si tous les mots sont trouvés
            // Toast.setGravity() est ignoré par le système depuis Android 11 : on utilise
            // un bandeau (vue applicative, pas une fenêtre système) pour l'ancrer en haut
            // et éviter qu'elle ne recouvre le mot qui vient de passer en vert dans la liste.
            val message = if (wordsFound == currentPuzzle?.words?.size) {
                getString(R.string.sa_felicitations_tous_les_mots_trouves)
            } else {
                getString(R.string.sa_mot_trouve_pts, word, points)
            }
            bandeauEnHaut(requireView(), message, longue = wordsFound == currentPuzzle?.words?.size)

            if (wordsFound == currentPuzzle?.words?.size) ouvrirPochette()
        }

        /**
         * Verse au carnet un mot **trouvé dans la grille**.
         *
         * Le mot était donné d'avance ici : ce qui se gagne n'est pas sa
         * traduction mais sa graphie, repérée lettre à lettre. C'est tout de
         * même une rencontre, et le carnet les garde toutes.
         */
        private fun encarter(forme: String?) {
            val ctx = context ?: return
            if (forme.isNullOrBlank()) return
            if (Carnet.ajouter(ctx, forme, JeuCarte.WUERTSICH)) neuves.add(forme)
            gagnes.add(forme)
            Pochette.rafraichir(boutonCarnet, ctx)
        }

        /** La pochette, une fois la grille complète. */
        private fun ouvrirPochette() {
            val grille = currentPuzzle ?: return
            enleverPochette()
            Pochette.ouvrir(
                fragment = this,
                jeu = JeuCarte.WUERTSICH,
                formes = gagnes.toList(),
                neuves = HashSet(neuves),
                encoreValide = { currentPuzzle === grille },
                surVue = { pochette = it }
            )
        }

        private fun enleverPochette() {
            pochette?.let { (it.parent as? ViewGroup)?.removeView(it) }
            pochette = null
        }

        override fun onDestroyView() {
            super.onDestroyView()
            enleverPochette()
        }
        
        private fun updateScore(points: Int) {
            // Le score se cumule d'une grille à l'autre, et survit à une
            // rotation : il est gardé avec la partie, plus relu dans le texte.
            memoire.score += points
            tvScore.text = "⭐ ${memoire.score}"
        }
    }
    
    // Fragment pour le jeu de mots mélangés
    class WordScrambleFragment : Fragment() {
        private var rootView: ScrollView? = null
        
        private lateinit var tvScore: TextView
        private lateinit var tvWordNumber: TextView
        private lateinit var tvTranslation: TextView
        private lateinit var gridScrambled: GridView
        private lateinit var gridAnswer: GridView
        private lateinit var btnValidate: Button
        private lateinit var btnSkip: Button
        private lateinit var btnHint: Button
        private lateinit var btnClear: Button
        private lateinit var progressBar: ProgressBar
        
        private var scrambledAdapter: com.example.kreyolkeyboard.wordscramble.ScrambledLettersAdapter? = null
        private var answerAdapter: com.example.kreyolkeyboard.wordscramble.AnswerLettersAdapter? = null
        
        /**
         * Ce qui doit survivre à une rotation : la série de mots, le mot en
         * cours avec ses lettres déjà placées, et le score. Même raison que
         * pour Kräizwuert, voir [CrosswordFragment.Partie].
         */
        class Partie : androidx.lifecycle.ViewModel() {
            var currentWord: String = ""
            var scrambledLetters: List<Char> = listOf()
            val currentAnswer = mutableListOf<Char?>()
            val selectedPositions = mutableListOf<Int>()
            var gameWords: List<String> = listOf()
            var currentWordIndex = 0
            var wordsCorrect = 0
            var score = 0
            var difficulty = com.example.kreyolkeyboard.wordscramble.ScrambleDifficulty.NORMAL
            val gagnes = mutableListOf<String>()
            val neuves = mutableSetOf<String>()
        }

        private val memoire by lazy {
            androidx.lifecycle.ViewModelProvider(this)[Partie::class.java]
        }

        private var currentWord: String
            get() = memoire.currentWord
            set(valeur) { memoire.currentWord = valeur }
        private var scrambledLetters: List<Char>
            get() = memoire.scrambledLetters
            set(valeur) { memoire.scrambledLetters = valeur }
        private val currentAnswer get() = memoire.currentAnswer
        private val selectedPositions get() = memoire.selectedPositions
        private var gameWords: List<String>
            get() = memoire.gameWords
            set(valeur) { memoire.gameWords = valeur }
        private var currentWordIndex: Int
            get() = memoire.currentWordIndex
            set(valeur) { memoire.currentWordIndex = valeur }
        private var wordsCorrect: Int
            get() = memoire.wordsCorrect
            set(valeur) { memoire.wordsCorrect = valeur }
        private var score: Int
            get() = memoire.score
            set(valeur) { memoire.score = valeur }
        private var difficulty: com.example.kreyolkeyboard.wordscramble.ScrambleDifficulty
            get() = memoire.difficulty
            set(valeur) { memoire.difficulty = valeur }

        private lateinit var boutonCarnet: TextView

        /** Les mots remis dans l'ordre pendant la manche. Un mot passé n'y est pas. */
        private val gagnes get() = memoire.gagnes
        private val neuves get() = memoire.neuves
        private var pochette: View? = null
        
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val activity = requireActivity() as SettingsActivity
            
            rootView = ScrollView(activity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.parseColor("#F5F5F5"))
                isFillViewport = true
                
                val mainLayout = LinearLayout(activity).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.VERTICAL
                    setPadding(32, 16, 32, 16)
                    
                    // En-tête avec score
                    val headerLayout = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                        setBackgroundColor(Color.WHITE)
                        setPadding(24, 24, 24, 24)
                        elevation = 8f
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = 32
                        
                        tvScore = TextView(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                            )
                            text = getString(R.string.sa_score_2)
                            textSize = 24f
                            setTypeface(null, Typeface.BOLD)
                            gravity = Gravity.CENTER
                            setTextColor(Color.parseColor("#4CAF50"))
                        }
                        addView(tvScore)

                        boutonCarnet = Pochette.bouton(
                            this@WordScrambleFragment,
                            Color.parseColor("#1976D2"),
                            petit = true
                        )
                        addView(boutonCarnet)
                    }
                    addView(headerLayout)
                    
                    // Numéro du mot et progression
                    val progressLayout = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        orientation = LinearLayout.VERTICAL
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = 32
                        
                        tvWordNumber = TextView(activity).apply {
                            text = getString(R.string.sa_mot_2)
                            textSize = 16f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.parseColor("#333333"))
                            setPadding(0, 0, 0, 16)
                        }
                        addView(tvWordNumber)
                        
                        progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                16
                            )
                            max = 10
                            progress = 0
                        }
                        addView(progressBar)
                    }
                    addView(progressLayout)
                    
                    // Titre
                    val title = TextView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        text = getString(R.string.sa_remets_les_lettres_dans_ordre)
                        textSize = 18f
                        setTypeface(null, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#1976D2"))
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = 32
                    }
                    addView(title)

                    // Traduction du mot caché. Contrairement aux autres jeux
                    // ce n'est pas un simple rappel de vocabulaire mais la
                    // consigne elle-même : sans elle, remettre des lettres
                    // dans l'ordre se joue par permutations, pas par le sens.
                    tvTranslation = TextView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        textSize = 16f
                        gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#555555"))
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = 24
                        visibility = View.GONE
                    }
                    addView(tvTranslation)
                    
                    // Label lettres disponibles
                    val labelScrambled = TextView(activity).apply {
                        text = getString(R.string.sa_lettres_disponibles)
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#333333"))
                        setPadding(0, 0, 0, 16)
                    }
                    addView(labelScrambled)
                    
                    // Grille des lettres mélangées
                    gridScrambled = GridView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        numColumns = 5
                        verticalSpacing = 16
                        horizontalSpacing = 16
                        stretchMode = GridView.STRETCH_COLUMN_WIDTH
                        gravity = Gravity.CENTER
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = 48
                        
                        setOnItemClickListener { _, _, position, _ ->
                            if (!selectedPositions.contains(position)) {
                                addLetterToAnswer(position)
                            }
                        }
                    }
                    addView(gridScrambled)
                    
                    // Label réponse
                    val labelAnswer = TextView(activity).apply {
                        text = getString(R.string.sa_ta_reponse)
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#333333"))
                        setPadding(0, 0, 0, 16)
                    }
                    addView(labelAnswer)
                    
                    // Grille de la réponse
                    gridAnswer = GridView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        numColumns = 5
                        verticalSpacing = 16
                        horizontalSpacing = 16
                        stretchMode = GridView.STRETCH_COLUMN_WIDTH
                        gravity = Gravity.CENTER
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = 48
                        
                        setOnItemClickListener { _, _, position, _ ->
                            removeLetterFromAnswer(position)
                        }
                    }
                    addView(gridAnswer)
                    
                    // Boutons d'action ligne 1
                    val buttonRow1 = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = 24
                        
                        btnClear = Button(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply { setMargins(8, 0, 8, 0) }
                            text = getString(R.string.sa_effacer)
                            setBackgroundColor(Color.parseColor("#FF9800"))
                            setTextColor(Color.WHITE)
                            setOnClickListener { clearAnswer() }
                        }
                        addView(btnClear)
                        
                        btnHint = Button(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply { setMargins(8, 0, 8, 0) }
                            text = getString(R.string.sa_indice)
                            setBackgroundColor(Color.parseColor("#FFC107"))
                            setTextColor(Color.WHITE)
                            setOnClickListener { showHint() }
                        }
                        addView(btnHint)
                    }
                    addView(buttonRow1)
                    
                    // Boutons d'action ligne 2
                    val buttonRow2 = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                        
                        btnValidate = Button(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply { setMargins(8, 0, 8, 0) }
                            text = getString(R.string.sa_valider)
                            setBackgroundColor(Color.parseColor("#4CAF50"))
                            setTextColor(Color.WHITE)
                            setTypeface(null, Typeface.BOLD)
                            isEnabled = false
                            setOnClickListener { validateAnswer() }
                        }
                        addView(btnValidate)
                        
                        btnSkip = Button(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply { setMargins(8, 0, 8, 0) }
                            text = getString(R.string.sa_passer)
                            setBackgroundColor(Color.parseColor("#9E9E9E"))
                            setTextColor(Color.WHITE)
                            setOnClickListener { skipWord() }
                        }
                        addView(btnSkip)
                    }
                    addView(buttonRow2)
                }
                
                addView(mainLayout)

                post {
                    // Même précaution que WordSearchFragment.generateNewPuzzle() :
                    // ce post() peut s'exécuter après que l'utilisateur a changé
                    // d'onglet, auquel cas le fragment n'est plus attaché.
                    if (isAdded) {
                        if (gameWords.isEmpty()) {
                            startNewGame()
                        } else {
                            // Après une rotation : le même mot, avec les
                            // lettres déjà placées.
                            Pochette.rafraichir(boutonCarnet, requireContext())
                            progressBar.max = gameWords.size
                            if (currentWordIndex < gameWords.size) afficherMot()
                        }
                    }
                }
            }

            return rootView!!
        }
        
        private fun startNewGame() {
            score = 0
            currentWordIndex = 0
            wordsCorrect = 0
            gagnes.clear()
            neuves.clear()
            enleverPochette()
            Pochette.rafraichir(boutonCarnet, requireContext())
            
            gameWords = com.example.kreyolkeyboard.wordscramble.WordScrambleData.loadWords(requireContext(), difficulty)
            
            if (gameWords.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.sa_erreur_de_chargement), Toast.LENGTH_SHORT).show()
                return
            }
            
            progressBar.max = gameWords.size
            loadNextWord()
        }
        
        private fun loadNextWord() {
            if (currentWordIndex >= gameWords.size) {
                endGame()
                return
            }
            
            currentWord = gameWords[currentWordIndex]
            var allScrambledLetters = com.example.kreyolkeyboard.wordscramble.WordScrambleData.scrambleWord(currentWord)
            
            currentAnswer.clear()
            selectedPositions.clear()
            repeat(currentWord.length) { currentAnswer.add(null) }
            
            // Pré-remplir la première et la dernière lettre
            if (currentWord.isNotEmpty()) {
                currentAnswer[0] = currentWord[0]
                if (currentWord.length > 1) {
                    currentAnswer[currentWord.length - 1] = currentWord[currentWord.length - 1]
                }
                
                // Retirer la première et dernière lettre des lettres mélangées
                val lettersToRemove = mutableListOf<Char>()
                lettersToRemove.add(currentWord[0])
                if (currentWord.length > 1) {
                    lettersToRemove.add(currentWord[currentWord.length - 1])
                }
                
                scrambledLetters = allScrambledLetters.toMutableList().apply {
                    lettersToRemove.forEach { letter ->
                        remove(letter)
                    }
                }
            } else {
                scrambledLetters = allScrambledLetters
            }
            
            afficherMot()
        }

        /** Montre le mot en cours dans l'état où il est. */
        private fun afficherMot() {
            scrambledAdapter = com.example.kreyolkeyboard.wordscramble.ScrambledLettersAdapter(requireContext(), scrambledLetters)
            answerAdapter = com.example.kreyolkeyboard.wordscramble.AnswerLettersAdapter(requireContext(), currentAnswer)
            
            gridScrambled.adapter = scrambledAdapter
            gridAnswer.adapter = answerAdapter
            selectedPositions.forEach { scrambledAdapter?.markAsSelected(it) }
            btnValidate.isEnabled = currentAnswer.all { it != null }
            
            gridScrambled.numColumns = minOf(scrambledLetters.size, 5)
            gridAnswer.numColumns = minOf(currentWord.length, 5)
            
            // Ajuster la hauteur des grilles
            val numRowsScrambled = (scrambledLetters.size + 4) / 5
            val numRowsAnswer = (currentWord.length + 4) / 5
            gridScrambled.layoutParams.height = numRowsScrambled * 136 // 120 + 16 spacing
            gridAnswer.layoutParams.height = numRowsAnswer * 136
            
            tvWordNumber.text = getString(R.string.sa_mot, currentWordIndex + 1, gameWords.size)
            tvScore.text = getString(R.string.sa_score, score)
            progressBar.progress = currentWordIndex

            val glose = TranslationDictionary.traduire(requireContext(), currentWord)
            if (glose != null) {
                tvTranslation.text = "💡 $glose"
                tvTranslation.visibility = View.VISIBLE
            } else {
                // Le tirage ne propose normalement que des mots traduits ; ce
                // cas ne survient qu'en repli, table des gloses absente.
                tvTranslation.visibility = View.GONE
            }
        }

        
        private fun addLetterToAnswer(position: Int) {
            val emptyIndex = currentAnswer.indexOfFirst { it == null }
            if (emptyIndex != -1) {
                currentAnswer[emptyIndex] = scrambledLetters[position]
                selectedPositions.add(position)
                
                scrambledAdapter?.markAsSelected(position)
                answerAdapter?.updateLetters(currentAnswer)
                
                if (currentAnswer.all { it != null }) {
                    btnValidate.isEnabled = true
                }
            }
        }
        
        private fun removeLetterFromAnswer(position: Int) {
            if (position < currentAnswer.size && currentAnswer[position] != null) {
                currentAnswer[position] = null
                
                if (position < selectedPositions.size) {
                    selectedPositions.removeAt(position)
                }
                
                val nonNullLetters = currentAnswer.filterNotNull().toMutableList()
                currentAnswer.clear()
                currentAnswer.addAll(nonNullLetters)
                repeat(currentWord.length - nonNullLetters.size) { currentAnswer.add(null) }
                
                scrambledAdapter?.clearSelections()
                selectedPositions.forEachIndexed { index, pos ->
                    if (index < selectedPositions.size) {
                        scrambledAdapter?.markAsSelected(pos)
                    }
                }
                
                answerAdapter?.updateLetters(currentAnswer)
                btnValidate.isEnabled = false
            }
        }
        
        private fun validateAnswer() {
            val answer = currentAnswer.filterNotNull().joinToString("")
            
            if (answer.equals(currentWord, ignoreCase = true)) {
                score += 100
                
                Toast.makeText(requireContext(), getString(R.string.sa_correct_pts), Toast.LENGTH_SHORT).show()

                encarter(currentWord)
                wordsCorrect++
                currentWordIndex++
                loadNextWord()
            } else {
                Toast.makeText(requireContext(), getString(R.string.sa_essaie_encore), Toast.LENGTH_SHORT).show()
                clearAnswer()
            }
        }
        
        private fun skipWord() {
            val glose = TranslationDictionary.traduire(requireContext(), currentWord)
            val revelation = if (glose != null) getString(R.string.sa_le_mot_etait_2, currentWord, glose)
                             else getString(R.string.sa_le_mot_etait, currentWord)
            Toast.makeText(requireContext(), revelation, Toast.LENGTH_SHORT).show()
            currentWordIndex++
            loadNextWord()
        }
        
        private fun showHint() {
            val firstEmpty = currentAnswer.indexOfFirst { it == null }
            if (firstEmpty != -1) {
                val correctLetter = currentWord[firstEmpty]
                
                val posInScrambled = scrambledLetters.indexOfFirst { 
                    it == correctLetter && !selectedPositions.contains(scrambledLetters.indexOf(it))
                }
                
                if (posInScrambled != -1) {
                    addLetterToAnswer(posInScrambled)
                    score -= 20
                    tvScore.text = getString(R.string.sa_score, score)
                    Toast.makeText(requireContext(), getString(R.string.sa_indice_pts), Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        private fun clearAnswer() {
            currentAnswer.clear()
            selectedPositions.clear()
            repeat(currentWord.length) { currentAnswer.add(null) }
            
            // Re-pré-remplir la première et dernière lettre
            if (currentWord.isNotEmpty()) {
                currentAnswer[0] = currentWord[0]
                if (currentWord.length > 1) {
                    currentAnswer[currentWord.length - 1] = currentWord[currentWord.length - 1]
                }
            }
            
            scrambledAdapter?.clearSelections()
            answerAdapter?.updateLetters(currentAnswer)
            btnValidate.isEnabled = false
        }
        
        /**
         * La fin de manche : la pochette d'abord, le bilan ensuite.
         *
         * L'`AlertDialog` est une fenêtre à part : ouvert en même temps que la
         * pochette, il la recouvrirait. Les cartes se regardent, puis le score
         * se lit.
         */
        private fun endGame() {
            val manche = gameWords
            enleverPochette()
            Pochette.ouvrir(
                fragment = this,
                jeu = JeuCarte.WUERTMIX,
                formes = gagnes.toList(),
                neuves = HashSet(neuves),
                encoreValide = { gameWords === manche },
                surVue = { pochette = it },
                surFin = { if (isAdded && gameWords === manche) montrerLeBilan() }
            )
        }

        private fun montrerLeBilan() {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.sa_partie_terminee))
                .setMessage(getString(R.string.sa_score_final_mots_reussis, score, wordsCorrect, gameWords.size))
                .setPositiveButton(getString(R.string.sa_rejouer)) { _, _ ->
                    startNewGame()
                }
                .setNegativeButton(getString(R.string.sa_ok), null)
                .show()
        }

        /**
         * Verse au carnet un mot **remis dans l'ordre**.
         *
         * Un mot passé ne compte pas : sa réponse a été montrée, pas trouvée.
         */
        private fun encarter(forme: String) {
            val ctx = context ?: return
            if (forme.isBlank()) return
            if (Carnet.ajouter(ctx, forme, JeuCarte.WUERTMIX)) neuves.add(forme)
            gagnes.add(forme)
            Pochette.rafraichir(boutonCarnet, ctx)
        }

        private fun enleverPochette() {
            pochette?.let { (it.parent as? ViewGroup)?.removeView(it) }
            pochette = null
        }
        
        override fun onDestroyView() {
            super.onDestroyView()
            enleverPochette()
            rootView = null
        }
    }

    // Fragment pour le Wuertriet : deviner un mot kréyòl de 5 lettres en 6 essais
    class WuertrietFragment : Fragment() {
        private var rootView: ScrollView? = null

        private lateinit var gridBoard: LinearLayout
        private lateinit var editGuess: EditText
        private lateinit var btnSubmit: Button
        private lateinit var tvAttempts: TextView
        private lateinit var legendContainer: LinearLayout

        /**
         * Ce qui doit survivre à une rotation : le mot à trouver et les essais
         * déjà joués. Même raison que pour Kräizwuert, voir
         * [CrosswordFragment.Partie].
         */
        class Partie : androidx.lifecycle.ViewModel() {
            var targetWord: String = ""
            var currentAttempt = 0
            var gameOver = false
            val rows = mutableListOf<WuertrietRow>()
            val letterBestState = mutableMapOf<Char, LetterState>()
        }

        private val memoire by lazy {
            androidx.lifecycle.ViewModelProvider(this)[Partie::class.java]
        }

        private var targetWord: String
            get() = memoire.targetWord
            set(valeur) { memoire.targetWord = valeur }
        private var currentAttempt: Int
            get() = memoire.currentAttempt
            set(valeur) { memoire.currentAttempt = valeur }
        private var gameOver: Boolean
            get() = memoire.gameOver
            set(valeur) { memoire.gameOver = valeur }
        private val rows get() = memoire.rows
        private val letterBestState get() = memoire.letterBestState

        private lateinit var boutonCarnet: TextView
        private var pochette: View? = null

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val activity = requireActivity() as SettingsActivity

            rootView = ScrollView(activity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.parseColor("#F5F5F5"))

                val mainLayout = LinearLayout(activity).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.VERTICAL
                    setPadding(32, 16, 32, 16)

                    // Titre + compteur d'essais sur la même ligne (gain de place vertical)
                    val headerRow = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = 12

                        val title = TextView(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            )
                            text = "🟩 Wuertriet"
                            textSize = 18f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.parseColor("#1976D2"))
                        }
                        addView(title)

                        boutonCarnet = Pochette.bouton(
                            this@WuertrietFragment,
                            Color.parseColor("#4CAF50"),
                            petit = true
                        ).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { rightMargin = 12 }
                        }
                        addView(boutonCarnet)

                        tvAttempts = TextView(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            text = getString(R.string.sa_essai_2, WuertrietData.MAX_ATTEMPTS)
                            textSize = 14f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.parseColor("#333333"))
                        }
                        addView(tvAttempts)
                    }
                    addView(headerRow)

                    // Grille de la partie (6 essais x 5 lettres) : un simple LinearLayout,
                    // pas une GridView — une GridView (AbsListView) vole le geste de scroll
                    // vertical à la ScrollView parente même quand elle est en lecture seule.
                    gridBoard = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 16 }
                        orientation = LinearLayout.VERTICAL
                    }
                    addView(gridBoard)

                    // Légende des lettres essayées : sur une seule ligne avec son label,
                    // juste sous la grille, pour rester visible au-dessus du clavier virtuel.
                    val legendRow = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = 16

                        val legendTitle = TextView(activity).apply {
                            text = getString(R.string.sa_lettres_deja_jouees)
                            textSize = 14f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.parseColor("#333333"))
                            setPadding(0, 0, 12, 0)
                        }
                        addView(legendTitle)

                        legendContainer = LinearLayout(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            orientation = LinearLayout.HORIZONTAL
                        }
                        val legendScroll = HorizontalScrollView(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            )
                            // Même défaut que la barre de suggestions du clavier :
                            // la barre de défilement se dessine par-dessus le
                            // contenu, et cette rangée n'est haute que d'une puce.
                            isHorizontalScrollBarEnabled = false
                            addView(legendContainer)
                        }
                        addView(legendScroll)
                    }
                    addView(legendRow)

                    // Saisie de la proposition (le champ porte directement son propre libellé en hint)
                    val inputRow = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = 16

                        editGuess = EditText(activity).apply {
                            id = R.id.essai_wuertriet
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply { setMargins(0, 0, 16, 0) }
                            hint = getString(R.string.sa_votre_mot_de_lettres, WuertrietData.WORD_LENGTH)
                            setHintTextColor(Color.parseColor("#9E9E9E"))
                            textSize = 18f
                            setTextColor(Color.parseColor("#212121"))
                            setTypeface(null, Typeface.BOLD)
                            gravity = Gravity.CENTER
                            letterSpacing = 0.08f
                            setPadding(16, 24, 16, 24)
                            background = android.graphics.drawable.GradientDrawable().apply {
                                cornerRadius = 12f
                                setColor(Color.WHITE)
                                setStroke(4, Color.parseColor("#2196F3"))
                            }
                            filters = arrayOf(android.text.InputFilter.LengthFilter(WuertrietData.WORD_LENGTH))
                            setSingleLine(true)
                            isAllCaps = true // après setSingleLine() : sinon la transformation majuscules est écrasée
                            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                            // Entrée sur un clavier physique n'arrive pas comme
                            // l'action « Terminé » du clavier à l'écran, mais
                            // comme une touche, sans action : on l'accepte aussi.
                            setOnEditorActionListener { _, actionId, event ->
                                val entreePhysique = event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER &&
                                    event.action == android.view.KeyEvent.ACTION_DOWN
                                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || entreePhysique) {
                                    submitGuess()
                                    true
                                } else false
                            }
                        }
                        addView(editGuess)

                        btnSubmit = Button(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.MATCH_PARENT
                            )
                            text = getString(R.string.sa_valider)
                            setBackgroundColor(Color.parseColor("#4CAF50"))
                            setTextColor(Color.WHITE)
                            setTypeface(null, Typeface.BOLD)
                            setOnClickListener { submitGuess() }
                        }
                        addView(btnSubmit)
                    }
                    addView(inputRow)

                    // Bouton nouvelle partie
                    val btnNewGame = Button(activity).apply {
                        text = getString(R.string.sa_nouvelle_partie)
                        textSize = 14f
                        setTextColor(Color.WHITE)
                        setBackgroundColor(Color.parseColor("#9C27B0"))
                        setPadding(24, 10, 24, 10)
                        setTypeface(null, Typeface.BOLD)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, 16, 0, 8) }
                        setOnClickListener { startNewGame() }
                    }
                    addView(btnNewGame)

                    // Règles du jeu, sous la zone de jeu
                    val rulesCard = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(24, 20, 24, 20)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = 16 }
                        background = android.graphics.drawable.GradientDrawable().apply {
                            cornerRadius = 12f
                            setColor(Color.WHITE)
                        }

                        val rulesTitle = TextView(activity).apply {
                            text = getString(R.string.sa_regles_du_jeu)
                            textSize = 16f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.parseColor("#1976D2"))
                            setPadding(0, 0, 0, 12)
                        }
                        addView(rulesTitle)

                        val rulesText = TextView(activity).apply {
                            text = getString(R.string.sa_devine_le_mot_luxembourgeois_de, WuertrietData.WORD_LENGTH, WuertrietData.MAX_ATTEMPTS)
                            textSize = 14f
                            setTextColor(Color.parseColor("#333333"))
                        }
                        addView(rulesText)
                    }
                    addView(rulesCard)
                }

                addView(mainLayout)

                post {
                    // Même précaution que les autres jeux : si l'utilisateur a déjà
                    // changé d'onglet, le fragment n'est plus attaché.
                    if (isAdded) {
                        if (targetWord.isEmpty()) startNewGame() else reprendre()
                    }
                }
            }

            return rootView!!
        }

        private fun renderBoard() {
            val activity = requireActivity()
            gridBoard.removeAllViews()
            rows.forEach { row ->
                val rowLayout = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = 6 }
                }
                row.letters.forEachIndexed { index, letter ->
                    val state = row.states[index]
                    val cell = TextView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(0, 88, 1f).apply {
                            if (index > 0) marginStart = 6
                        }
                        gravity = Gravity.CENTER
                        text = letter?.toString()?.uppercase() ?: ""
                        textSize = 18f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(if (state == LetterState.EMPTY) Color.parseColor("#333333") else Color.WHITE)
                        setBackgroundColor(state.color())
                    }
                    rowLayout.addView(cell)
                }
                gridBoard.addView(rowLayout)
            }
        }

        /** Après une rotation : la même grille d'essais, la même légende. */
        private fun reprendre() {
            Pochette.rafraichir(boutonCarnet, requireActivity())
            renderBoard()
            editGuess.isEnabled = !gameOver
            btnSubmit.isEnabled = !gameOver
            tvAttempts.text = getString(
                R.string.sa_essai,
                minOf(currentAttempt + 1, WuertrietData.MAX_ATTEMPTS),
                WuertrietData.MAX_ATTEMPTS
            )
            afficherLegende()
        }

        private fun startNewGame() {
            val activity = requireActivity()
            targetWord = WuertrietData.pickRandomWord(activity)
            currentAttempt = 0
            gameOver = false
            letterBestState.clear()
            enleverPochette()
            Pochette.rafraichir(boutonCarnet, activity)
            rows.clear()
            repeat(WuertrietData.MAX_ATTEMPTS) {
                rows.add(
                    WuertrietRow(
                        letters = List(WuertrietData.WORD_LENGTH) { null },
                        states = List(WuertrietData.WORD_LENGTH) { LetterState.EMPTY }
                    )
                )
            }
            renderBoard()
            editGuess.setText("")
            editGuess.isEnabled = true
            btnSubmit.isEnabled = true
            tvAttempts.text = getString(R.string.sa_essai, currentAttempt + 1, WuertrietData.MAX_ATTEMPTS)
            legendContainer.removeAllViews()
        }

        private fun submitGuess() {
            if (gameOver) return
            val activity = requireActivity()
            val guess = editGuess.text.toString().trim().lowercase()

            if (guess.length != WuertrietData.WORD_LENGTH) {
                showTopMessage("D'Wuert muss ${WuertrietData.WORD_LENGTH} Buschtawen hunn")
                return
            }
            if (!WuertrietData.isValidWord(activity, guess)) {
                showTopMessage("❌ Dëst Wuert ass net am Wierderbuch")
                return
            }

            val states = WuertrietData.evaluateGuess(targetWord, guess)
            rows[currentAttempt] = WuertrietRow(guess.toList(), states)
            renderBoard()
            updateLegend(guess, states)

            val won = states.all { it == LetterState.CORRECT }
            currentAttempt++

            when {
                won -> {
                    gameOver = true
                    endGame(true)
                }
                currentAttempt >= WuertrietData.MAX_ATTEMPTS -> {
                    gameOver = true
                    endGame(false)
                }
                else -> {
                    editGuess.setText("")
                    tvAttempts.text = getString(R.string.sa_essai, currentAttempt + 1, WuertrietData.MAX_ATTEMPTS)
                }
            }
        }

        // Toast.setGravity() est ignoré depuis Android 11 : ancré en haut par un
        // bandeau pour ne pas se faire masquer par le clavier virtuel (même piste
        // que WordSearchFragment).
        private fun showTopMessage(message: String) {
            bandeauEnHaut(requireView(), message, longue = false)
        }

        private fun updateLegend(guess: String, states: List<LetterState>) {
            val activity = requireActivity()
            guess.forEachIndexed { index, letter ->
                val newState = states[index]
                val existing = letterBestState[letter]
                if (existing == null || statePriority(newState) > statePriority(existing)) {
                    letterBestState[letter] = newState
                }
            }
            afficherLegende()
        }

        private fun afficherLegende() {
            val activity = requireActivity()
            legendContainer.removeAllViews()
            letterBestState.entries.sortedBy { it.key }.forEach { (letter, state) ->
                val chip = TextView(activity).apply {
                    text = letter.toString().uppercase()
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    setBackgroundColor(state.color())
                    setPadding(24, 16, 24, 16)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, 8, 0) }
                }
                legendContainer.addView(chip)
            }
        }

        private fun statePriority(state: LetterState): Int = when (state) {
            LetterState.CORRECT -> 3
            LetterState.PRESENT -> 2
            LetterState.ABSENT -> 1
            LetterState.EMPTY -> 0
        }

        /**
         * La fin de partie.
         *
         * **Une seule carte est en jeu, et elle ne se gagne qu'en trouvant.**
         * Un mot perdu a été montré, pas deviné : il donne sa traduction — ce
         * que le jeu enseigne — mais pas sa carte. C'est la même règle que la
         * « Solution » de Kräizwuert.
         */
        private fun endGame(won: Boolean) {
            editGuess.isEnabled = false
            btnSubmit.isEnabled = false

            val mot = targetWord
            val neuve = won && Carnet.ajouter(requireContext(), mot, JeuCarte.WUERTRIET)
            if (won) Pochette.rafraichir(boutonCarnet, requireContext())

            enleverPochette()
            Pochette.ouvrir(
                fragment = this,
                jeu = JeuCarte.WUERTRIET,
                formes = if (won) listOf(mot) else emptyList(),
                neuves = if (neuve) setOf(mot) else emptySet(),
                encoreValide = { targetWord == mot },
                surVue = { pochette = it },
                surFin = { if (isAdded && targetWord == mot) montrerLeBilan(won) }
            )
        }

        private fun montrerLeBilan(won: Boolean) {
            // Le mot n'a été montré à personne pendant la partie : la fin est
            // le seul moment où sa traduction peut être donnée sans livrer la
            // réponse. C'est là que le jeu apprend quelque chose.
            val glose = TranslationDictionary.traduire(requireContext(), targetWord)
            val motEtGlose = targetWord.uppercase() + (glose?.let { "\n« $it »" } ?: "")

            AlertDialog.Builder(requireContext())
                .setTitle(if (won) getString(R.string.sa_bravo) else getString(R.string.sa_domaj))
                .setMessage(
                    if (won) resources.getQuantityString(R.plurals.trouve_en_essais, currentAttempt, currentAttempt, motEtGlose)
                    else getString(R.string.sa_le_mot_etait, motEtGlose)
                )
                .setPositiveButton(getString(R.string.sa_rejouer)) { _, _ -> startNewGame() }
                .setNegativeButton(getString(R.string.sa_ok), null)
                .show()
        }

        private fun enleverPochette() {
            pochette?.let { (it.parent as? ViewGroup)?.removeView(it) }
            pochette = null
        }

        override fun onDestroyView() {
            super.onDestroyView()
            enleverPochette()
            rootView = null
        }
    }

    // Fragment pour le Wuertlück : une phrase authentique dont un mot manque,
    // et quatre propositions. Les phrases, la réponse et les leurres viennent
    // tels quels de l'actif ; ce fragment ne fait que présenter et compter.
    class ClozeFragment : Fragment() {
        private var rootView: ScrollView? = null

        private lateinit var tvScore: TextView
        private lateinit var tvProgress: TextView
        private lateinit var progressBar: ProgressBar
        private lateinit var tvSentence: TextView
        private lateinit var tvSource: TextView
        private lateinit var tvFeedback: TextView
        private lateinit var optionsContainer: LinearLayout
        private lateinit var btnNext: Button
        private lateinit var difficultyRow: LinearLayout

        private val optionButtons = mutableListOf<Button>()

        /**
         * Ce qui doit survivre à une rotation : la manche, la question en
         * cours, le score et la réponse déjà donnée. Même raison que pour
         * Kräizwuert, voir [CrosswordFragment.Partie].
         */
        class Partie : androidx.lifecycle.ViewModel() {
            var round: List<ClozeQuestion> = emptyList()
            var questionIndex = 0
            var score = 0
            var answered = false
            /** La proposition touchée à la question en cours, -1 avant. */
            var choisi = -1
            var difficulty = ClozeDifficulty.NORMALE
            val gagnes = mutableListOf<String>()
            val neuves = mutableSetOf<String>()
        }

        private val memoire by lazy {
            androidx.lifecycle.ViewModelProvider(this)[Partie::class.java]
        }

        private var round: List<ClozeQuestion>
            get() = memoire.round
            set(valeur) { memoire.round = valeur }
        private var questionIndex: Int
            get() = memoire.questionIndex
            set(valeur) { memoire.questionIndex = valeur }
        private var score: Int
            get() = memoire.score
            set(valeur) { memoire.score = valeur }
        private var answered: Boolean
            get() = memoire.answered
            set(valeur) { memoire.answered = valeur }
        private var difficulty: ClozeDifficulty
            get() = memoire.difficulty
            set(valeur) { memoire.difficulty = valeur }

        private lateinit var boutonCarnet: TextView

        /** Les mots retrouvés dans la phrase pendant la manche. */
        private val gagnes get() = memoire.gagnes
        private val neuves get() = memoire.neuves
        private var pochette: View? = null

        private val couleurNeutre = Color.parseColor("#1976D2")
        private val couleurJuste = Color.parseColor("#4CAF50")
        private val couleurFausse = Color.parseColor("#E53935")
        private val couleurInerte = Color.parseColor("#BDBDBD")

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val activity = requireActivity() as SettingsActivity

            rootView = ScrollView(activity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.parseColor("#F5F5F5"))

                val mainLayout = LinearLayout(activity).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.VERTICAL
                    setPadding(32, 16, 32, 16)

                    // Titre et score sur une ligne : la phrase à trous a besoin
                    // de toute la hauteur qu'on peut lui laisser.
                    val headerRow = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 12 }
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL

                        val title = TextView(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                            )
                            text = "📝 Wuertlück"
                            textSize = 18f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(couleurNeutre)
                        }
                        addView(title)

                        boutonCarnet = Pochette.bouton(
                            this@ClozeFragment,
                            Color.parseColor("#FF8C00"),
                            petit = true
                        ).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { rightMargin = 12 }
                        }
                        addView(boutonCarnet)

                        tvScore = TextView(activity).apply {
                            text = "0 / ${ClozeData.QUESTIONS_PER_ROUND}"
                            textSize = 14f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.parseColor("#333333"))
                        }
                        addView(tvScore)
                    }
                    addView(headerRow)

                    // Choix de la difficulté : elle porte sur la fréquence du
                    // mot masqué, pas sur le nombre de propositions.
                    difficultyRow = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 16 }
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                    }
                    ClozeDifficulty.values().forEach { niveau ->
                        val bouton = Button(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                            ).apply { setMargins(4, 0, 4, 0) }
                            setText(niveau.label)
                            textSize = 12f
                            isAllCaps = false
                            setTextColor(Color.WHITE)
                            tag = niveau
                            setOnClickListener {
                                difficulty = niveau
                                startNewRound()
                            }
                        }
                        difficultyRow.addView(bouton)
                    }
                    addView(difficultyRow)

                    tvProgress = TextView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 6 }
                        text = getString(R.string.sa_question_2, ClozeData.QUESTIONS_PER_ROUND)
                        textSize = 13f
                        setTextColor(Color.parseColor("#666666"))
                    }
                    addView(tvProgress)

                    progressBar = ProgressBar(
                        activity, null, android.R.attr.progressBarStyleHorizontal
                    ).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 16 }
                        max = ClozeData.QUESTIONS_PER_ROUND
                        progress = 0
                    }
                    addView(progressBar)

                    // Carte de la phrase
                    val sentenceCard = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 20 }
                        orientation = LinearLayout.VERTICAL
                        setPadding(28, 28, 28, 24)
                        background = GradientDrawable().apply {
                            cornerRadius = 12f
                            setColor(Color.WHITE)
                        }

                        tvSentence = TextView(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            textSize = 18f
                            setLineSpacing(0f, 1.25f)
                            setTextColor(Color.parseColor("#212121"))
                        }
                        addView(tvSentence)

                        // La source est affichée par phrase : les deux corpus
                        // sont sous licence Creative Commons et exigent la
                        // citation de leurs auteurs.
                        tvSource = TextView(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { topMargin = 14 }
                            textSize = 11f
                            setTextColor(Color.parseColor("#9E9E9E"))
                        }
                        addView(tvSource)
                    }
                    addView(sentenceCard)

                    // Les quatre propositions, une par ligne : les mots
                    // luxembourgeois composés sont longs, deux colonnes les
                    // couperaient.
                    optionsContainer = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        orientation = LinearLayout.VERTICAL
                    }
                    repeat(4) { position ->
                        val bouton = Button(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { bottomMargin = 12 }
                            textSize = 16f
                            setTextColor(Color.WHITE)
                            setTypeface(null, Typeface.BOLD)
                            isAllCaps = false
                            setOnClickListener { onOptionChosen(position) }
                        }
                        optionButtons.add(bouton)
                        optionsContainer.addView(bouton)
                    }
                    addView(optionsContainer)

                    tvFeedback = TextView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = 4; bottomMargin = 8 }
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        visibility = View.GONE
                    }
                    addView(tvFeedback)

                    btnNext = Button(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = 4 }
                        text = getString(R.string.sa_question_suivante)
                        setBackgroundColor(couleurNeutre)
                        setTextColor(Color.WHITE)
                        setTypeface(null, Typeface.BOLD)
                        isAllCaps = false
                        visibility = View.INVISIBLE
                        setOnClickListener { goToNextQuestion() }
                    }
                    addView(btnNext)

                    val btnRestart = Button(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, 16, 0, 8) }
                        text = getString(R.string.sa_nouvelle_partie)
                        textSize = 14f
                        setBackgroundColor(Color.parseColor("#9C27B0"))
                        setTextColor(Color.WHITE)
                        setTypeface(null, Typeface.BOLD)
                        isAllCaps = false
                        setOnClickListener { startNewRound() }
                    }
                    addView(btnRestart)

                    // Règles + crédits des corpus
                    val rulesCard = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = 16 }
                        orientation = LinearLayout.VERTICAL
                        setPadding(24, 20, 24, 20)
                        background = GradientDrawable().apply {
                            cornerRadius = 12f
                            setColor(Color.WHITE)
                        }

                        val rulesTitle = TextView(activity).apply {
                            text = getString(R.string.sa_regles_du_jeu)
                            textSize = 16f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(couleurNeutre)
                            setPadding(0, 0, 0, 12)
                        }
                        addView(rulesTitle)

                        val rulesText = TextView(activity).apply {
                            text = getString(R.string.sa_chaque_phrase_est_une_phrase)
                            textSize = 14f
                            setTextColor(Color.parseColor("#333333"))
                        }
                        addView(rulesText)

                        val creditsText = TextView(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { topMargin = 16 }
                            text = getString(R.string.sa_phrases_extraites_des_corpus) +
                                ClozeData.attribution(activity)
                            textSize = 11f
                            setTextColor(Color.parseColor("#757575"))
                        }
                        addView(creditsText)
                    }
                    addView(rulesCard)
                }

                addView(mainLayout)

                post {
                    // Même précaution que les autres jeux : ce post() peut
                    // s'exécuter après un changement d'onglet.
                    if (isAdded) {
                        if (round.isEmpty()) startNewRound() else reprendre()
                    }
                }
            }

            return rootView!!
        }

        private fun startNewRound() {
            val activity = requireActivity()
            round = ClozeData.newRound(activity, difficulty)
            questionIndex = 0
            score = 0
            answered = false
            gagnes.clear()
            neuves.clear()
            enleverPochette()
            Pochette.rafraichir(boutonCarnet, activity)
            tvScore.text = "0 / ${ClozeData.QUESTIONS_PER_ROUND}"
            progressBar.max = maxOf(1, round.size)
            progressBar.progress = 0
            highlightDifficulty()

            if (round.isEmpty()) {
                showMissingAsset()
                return
            }
            renderQuestion()
        }

        /**
         * Après une rotation : la même question, et la réponse déjà donnée
         * reste affichée sans compter une seconde fois.
         */
        private fun reprendre() {
            Pochette.rafraichir(boutonCarnet, requireActivity())
            tvScore.text = "$score / ${round.size}"
            progressBar.max = maxOf(1, round.size)
            val choisi = memoire.choisi
            progressBar.progress = if (choisi >= 0) questionIndex + 1 else questionIndex
            highlightDifficulty()
            renderQuestion()
            if (choisi >= 0) {
                answered = true
                memoire.choisi = choisi
                montrerVerdict(choisi)
            }
        }

        private fun highlightDifficulty() {
            for (i in 0 until difficultyRow.childCount) {
                val bouton = difficultyRow.getChildAt(i) as Button
                val actif = bouton.tag == difficulty
                bouton.setBackgroundColor(if (actif) couleurNeutre else couleurInerte)
            }
        }

        /**
         * Sans l'actif, le jeu ne se rabat pas sur des phrases de secours : il
         * le dit. Un jeu de dépannage jouable masquerait une livraison cassée.
         */
        private fun showMissingAsset() {
            tvSentence.text = getString(R.string.sa_les_phrases_du_wuertluck_ont)
            tvSource.text = ""
            tvProgress.text = ""
            tvFeedback.visibility = View.GONE
            btnNext.visibility = View.INVISIBLE
            optionButtons.forEach {
                it.visibility = View.GONE
            }
        }

        private fun renderQuestion() {
            val question = round[questionIndex]
            answered = false
            memoire.choisi = -1

            tvProgress.text = getString(R.string.sa_question, questionIndex + 1, round.size)
            tvSource.text = getString(R.string.sa_phrase_du_corpus, question.source)
            tvSentence.text = sentenceWithBlank(question)
            tvFeedback.visibility = View.GONE
            btnNext.visibility = View.INVISIBLE

            optionButtons.forEachIndexed { position, bouton ->
                val proposition = question.options.getOrNull(position)
                if (proposition == null) {
                    bouton.visibility = View.GONE
                } else {
                    bouton.visibility = View.VISIBLE
                    bouton.text = proposition
                    bouton.isEnabled = true
                    bouton.setBackgroundColor(couleurNeutre)
                }
            }
        }

        /** La phrase avec son trou matérialisé, en gras et en couleur. */
        private fun sentenceWithBlank(question: ClozeQuestion): CharSequence {
            val trou = "_____"
            val texte = question.before + trou + question.after
            return SpannableString(texte).apply {
                val debut = question.before.length
                setSpan(
                    ForegroundColorSpan(couleurNeutre),
                    debut, debut + trou.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    StyleSpan(Typeface.BOLD),
                    debut, debut + trou.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        /** La phrase complétée, le mot retrouvé mis en évidence. */
        private fun sentenceWithAnswer(question: ClozeQuestion): CharSequence {
            val texte = question.completed
            return SpannableString(texte).apply {
                val debut = question.before.length
                setSpan(
                    ForegroundColorSpan(couleurJuste),
                    debut, debut + question.answer.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    StyleSpan(Typeface.BOLD),
                    debut, debut + question.answer.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        private fun onOptionChosen(position: Int) {
            if (answered || round.isEmpty()) return
            val question = round[questionIndex]
            val choix = question.options.getOrNull(position) ?: return
            answered = true
            memoire.choisi = position

            val juste = choix == question.answer
            if (juste) {
                score++
                tvScore.text = "$score / ${round.size}"
                encarter(question.answer)
            }

            montrerVerdict(position)
        }

        /** Fige les propositions et donne le verdict de la proposition [position]. */
        private fun montrerVerdict(position: Int) {
            val question = round[questionIndex]
            val juste = question.options.getOrNull(position) == question.answer
            // Toutes les propositions se figent : la bonne en vert, celle qu'on
            // a touchée en rouge si elle était fausse. Voir la bonne réponse
            // compte autant que marquer le point.
            optionButtons.forEachIndexed { i, bouton ->
                val proposition = question.options.getOrNull(i)
                bouton.isEnabled = false
                bouton.setBackgroundColor(
                    when {
                        proposition == question.answer -> couleurJuste
                        i == position -> couleurFausse
                        else -> couleurInerte
                    }
                )
            }

            tvSentence.text = sentenceWithAnswer(question)
            // La glose ne s'affiche qu'une fois la question tranchée : donnée
            // avant, elle désignerait la bonne case. Les mots masqués sont des
            // mots pleins, donc le LOD en glose la plupart — mais pas tous, et
            // une réponse sans traduction se contente du verdict.
            val glose = TranslationDictionary.traduire(requireContext(), question.answer)
            val gloseAffichee = glose?.let { " (${question.answer} : $it)" } ?: ""
            tvFeedback.apply {
                text = if (juste) getString(R.string.sa_richteg, gloseAffichee)
                       else getString(R.string.sa_la_phrase_disait, question.answer) +
                            (glose?.let { " : $it" } ?: "")
                setTextColor(if (juste) couleurJuste else couleurFausse)
                visibility = View.VISIBLE
            }

            progressBar.progress = questionIndex + 1
            btnNext.visibility = View.VISIBLE
            btnNext.text =
                if (questionIndex + 1 >= round.size) getString(R.string.sa_voir_le_resultat)
                else getString(R.string.sa_question_suivante)
        }

        private fun goToNextQuestion() {
            if (questionIndex + 1 >= round.size) {
                endRound()
                return
            }
            questionIndex++
            renderQuestion()
        }

        /**
         * Verse au carnet le mot **retrouvé dans la phrase**.
         *
         * Une réponse fausse ne donne rien : la bonne proposition s'affiche
         * alors en vert, mais elle a été montrée, pas trouvée.
         */
        private fun encarter(forme: String) {
            val ctx = context ?: return
            if (forme.isBlank()) return
            if (Carnet.ajouter(ctx, forme, JeuCarte.WUERTLUECK)) neuves.add(forme)
            gagnes.add(forme)
            Pochette.rafraichir(boutonCarnet, ctx)
        }

        /** La pochette d'abord, le bilan de manche ensuite. */
        private fun endRound() {
            val manche = round
            enleverPochette()
            Pochette.ouvrir(
                fragment = this,
                jeu = JeuCarte.WUERTLUECK,
                formes = gagnes.toList(),
                neuves = HashSet(neuves),
                encoreValide = { round === manche },
                surVue = { pochette = it },
                surFin = { if (isAdded && round === manche) montrerLeBilan() }
            )
        }

        private fun montrerLeBilan() {
            val total = round.size
            val message = when {
                score == total -> getString(R.string.score_sans_faute, score, total)
                score == 0 -> getString(R.string.score_aucune)
                score * 2 >= total -> resources.getQuantityString(R.plurals.score_bonnes_reponses, score, score, total)
                else -> resources.getQuantityString(R.plurals.score_bonnes_reponses_relance, score, score, total)
            }
            AlertDialog.Builder(requireContext())
                .setTitle(if (score * 2 >= total) getString(R.string.sa_bravo) else getString(R.string.sa_encore_un_effort))
                .setMessage(message)
                .setPositiveButton(getString(R.string.sa_rejouer)) { _, _ -> startNewRound() }
                .setNegativeButton(getString(R.string.sa_ok), null)
                .show()
        }

        private fun enleverPochette() {
            pochette?.let { (it.parent as? ViewGroup)?.removeView(it) }
            pochette = null
        }

        override fun onDestroyView() {
            super.onDestroyView()
            enleverPochette()
            optionButtons.clear()
            rootView = null
        }
    }

    /**
     * Fragment « Zuelwuert » : une multiplication, quatre orthographes de son
     * résultat.
     *
     * Le seul jeu qui ne tire rien du dictionnaire : ses mots se calculent
     * (voir [ZuelenSpeller]). Il n'affiche donc pas de glose française — le
     * chiffre est déjà à l'écran, il est sa propre traduction.
     */
    class ZuelenFragment : Fragment() {
        private var rootView: ScrollView? = null

        private lateinit var tvScore: TextView
        private lateinit var tvProgress: TextView
        private lateinit var progressBar: ProgressBar
        private lateinit var tvOperation: TextView
        private lateinit var tvConsigne: TextView
        private lateinit var tvFeedback: TextView
        private lateinit var optionsContainer: LinearLayout
        private lateinit var btnNext: Button
        private lateinit var difficultyRow: LinearLayout

        private val optionButtons = mutableListOf<Button>()

        /**
         * Ce qui doit survivre à une rotation : la manche, la question en
         * cours, le score et la réponse déjà donnée. Même raison que pour
         * Kräizwuert, voir [CrosswordFragment.Partie].
         */
        class Partie : androidx.lifecycle.ViewModel() {
            var round: List<ZuelenQuestion> = emptyList()
            var questionIndex = 0
            var score = 0
            var answered = false
            /** La proposition touchée à la question en cours, -1 avant. */
            var choisi = -1
            var difficulty = ZuelenDifficulty.NORMALE
            val gagnes = LinkedHashMap<String, Int>()
            val neuves = mutableSetOf<String>()
        }

        private val memoire by lazy {
            androidx.lifecycle.ViewModelProvider(this)[Partie::class.java]
        }

        private var round: List<ZuelenQuestion>
            get() = memoire.round
            set(valeur) { memoire.round = valeur }
        private var questionIndex: Int
            get() = memoire.questionIndex
            set(valeur) { memoire.questionIndex = valeur }
        private var score: Int
            get() = memoire.score
            set(valeur) { memoire.score = valeur }
        private var answered: Boolean
            get() = memoire.answered
            set(valeur) { memoire.answered = valeur }
        private var difficulty: ZuelenDifficulty
            get() = memoire.difficulty
            set(valeur) { memoire.difficulty = valeur }

        private lateinit var boutonCarnet: TextView

        /**
         * Les numéraux bien orthographiés de la manche, avec leur valeur.
         *
         * La valeur suit la forme jusqu'au carnet : elle est la seule manière
         * de lire la rareté d'un composé, que le corpus ne contient pas.
         */
        private val gagnes get() = memoire.gagnes
        private val neuves get() = memoire.neuves
        private var pochette: View? = null

        private val couleurNeutre = Color.parseColor("#00897B")
        private val couleurJuste = Color.parseColor("#4CAF50")
        private val couleurFausse = Color.parseColor("#E53935")
        private val couleurInerte = Color.parseColor("#BDBDBD")

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val activity = requireActivity() as SettingsActivity

            rootView = ScrollView(activity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.parseColor("#F5F5F5"))

                val mainLayout = LinearLayout(activity).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.VERTICAL
                    setPadding(32, 16, 32, 16)

                    val headerRow = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 12 }
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL

                        val title = TextView(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                            )
                            text = "🔢 Zuelwuert"
                            textSize = 18f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(couleurNeutre)
                        }
                        addView(title)

                        boutonCarnet = Pochette.bouton(
                            this@ZuelenFragment,
                            couleurNeutre,
                            petit = true
                        ).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { rightMargin = 12 }
                        }
                        addView(boutonCarnet)

                        tvScore = TextView(activity).apply {
                            text = "0 / ${ZuelenData.QUESTIONS_PER_ROUND}"
                            textSize = 14f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.parseColor("#333333"))
                        }
                        addView(tvScore)
                    }
                    addView(headerRow)

                    // La difficulté porte sur les tables tirées, sur la finesse
                    // des leurres, et — au niveau le plus dur — sur le fait que
                    // le produit n'est plus donné.
                    difficultyRow = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 16 }
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                    }
                    ZuelenDifficulty.values().forEach { niveau ->
                        val bouton = Button(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                            ).apply { setMargins(4, 0, 4, 0) }
                            setText(niveau.label)
                            textSize = 12f
                            isAllCaps = false
                            setTextColor(Color.WHITE)
                            tag = niveau
                            setOnClickListener {
                                difficulty = niveau
                                startNewRound()
                            }
                        }
                        difficultyRow.addView(bouton)
                    }
                    addView(difficultyRow)

                    tvProgress = TextView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 6 }
                        text = getString(R.string.sa_question_2, ZuelenData.QUESTIONS_PER_ROUND)
                        textSize = 13f
                        setTextColor(Color.parseColor("#666666"))
                    }
                    addView(tvProgress)

                    progressBar = ProgressBar(
                        activity, null, android.R.attr.progressBarStyleHorizontal
                    ).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 16 }
                        max = ZuelenData.QUESTIONS_PER_ROUND
                        progress = 0
                    }
                    addView(progressBar)

                    // Carte de l'opération
                    val operationCard = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 20 }
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(28, 28, 28, 24)
                        background = GradientDrawable().apply {
                            cornerRadius = 12f
                            setColor(Color.WHITE)
                        }

                        tvOperation = TextView(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            textSize = 34f
                            gravity = Gravity.CENTER
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.parseColor("#212121"))
                        }
                        addView(tvOperation)

                        tvConsigne = TextView(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { topMargin = 10 }
                            textSize = 13f
                            gravity = Gravity.CENTER
                            setTextColor(Color.parseColor("#9E9E9E"))
                        }
                        addView(tvConsigne)
                    }
                    addView(operationCard)

                    // Une proposition par ligne : « fënnefasiwwenzeg » et son
                    // leurre allemand ne tiennent pas côte à côte.
                    optionsContainer = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        orientation = LinearLayout.VERTICAL
                    }
                    repeat(ZuelenData.OPTIONS_PER_QUESTION) { position ->
                        val bouton = Button(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { bottomMargin = 12 }
                            textSize = 16f
                            setTextColor(Color.WHITE)
                            setTypeface(null, Typeface.BOLD)
                            isAllCaps = false
                            setOnClickListener { onOptionChosen(position) }
                        }
                        optionButtons.add(bouton)
                        optionsContainer.addView(bouton)
                    }
                    addView(optionsContainer)

                    tvFeedback = TextView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = 4; bottomMargin = 8 }
                        textSize = 14f
                        setLineSpacing(0f, 1.2f)
                        gravity = Gravity.CENTER
                        visibility = View.GONE
                    }
                    addView(tvFeedback)

                    btnNext = Button(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = 4 }
                        text = getString(R.string.sa_question_suivante)
                        setBackgroundColor(couleurNeutre)
                        setTextColor(Color.WHITE)
                        setTypeface(null, Typeface.BOLD)
                        isAllCaps = false
                        visibility = View.INVISIBLE
                        setOnClickListener { goToNextQuestion() }
                    }
                    addView(btnNext)

                    val btnRestart = Button(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, 16, 0, 8) }
                        text = getString(R.string.sa_nouvelle_partie)
                        textSize = 14f
                        setBackgroundColor(Color.parseColor("#9C27B0"))
                        setTextColor(Color.WHITE)
                        setTypeface(null, Typeface.BOLD)
                        isAllCaps = false
                        setOnClickListener { startNewRound() }
                    }
                    addView(btnRestart)

                    val rulesCard = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = 16 }
                        orientation = LinearLayout.VERTICAL
                        setPadding(24, 20, 24, 20)
                        background = GradientDrawable().apply {
                            cornerRadius = 12f
                            setColor(Color.WHITE)
                        }

                        addView(TextView(activity).apply {
                            text = getString(R.string.sa_regles_du_jeu)
                            textSize = 16f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(couleurNeutre)
                            setPadding(0, 0, 0, 12)
                        })

                        addView(TextView(activity).apply {
                            text = getString(R.string.sa_une_multiplication_et_quatre_facons)
                            textSize = 14f
                            setLineSpacing(0f, 1.2f)
                            setTextColor(Color.parseColor("#333333"))
                        })

                        // Les orthographes ne sont pas de nous : elles ont été
                        // vérifiées une à une contre le dictionnaire officiel.
                        addView(TextView(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { topMargin = 16 }
                            text = getString(R.string.sa_orthographes_verifiees_contre_le_letzebuerger)
                            textSize = 11f
                            setTextColor(Color.parseColor("#757575"))
                        })
                    }
                    addView(rulesCard)
                }

                addView(mainLayout)

                post {
                    // Même précaution que les autres jeux : ce post() peut
                    // s'exécuter après un changement d'onglet.
                    if (isAdded) {
                        if (round.isEmpty()) startNewRound() else reprendre()
                    }
                }
            }

            return rootView!!
        }

        private fun startNewRound() {
            round = ZuelenData.newRound(difficulty)
            questionIndex = 0
            score = 0
            answered = false
            gagnes.clear()
            neuves.clear()
            enleverPochette()
            context?.let { Pochette.rafraichir(boutonCarnet, it) }
            tvScore.text = "0 / ${round.size.coerceAtLeast(1)}"
            progressBar.max = maxOf(1, round.size)
            progressBar.progress = 0
            highlightDifficulty()
            if (round.isNotEmpty()) renderQuestion()
        }

        /**
         * Après une rotation : la même question, et la réponse déjà donnée
         * reste affichée sans compter une seconde fois.
         */
        private fun reprendre() {
            context?.let { Pochette.rafraichir(boutonCarnet, it) }
            tvScore.text = "$score / ${round.size}"
            progressBar.max = maxOf(1, round.size)
            val choisi = memoire.choisi
            progressBar.progress = if (choisi >= 0) questionIndex + 1 else questionIndex
            highlightDifficulty()
            renderQuestion()
            if (choisi >= 0) {
                answered = true
                memoire.choisi = choisi
                montrerVerdict(choisi)
            }
        }

        private fun highlightDifficulty() {
            for (i in 0 until difficultyRow.childCount) {
                val bouton = difficultyRow.getChildAt(i) as Button
                val actif = bouton.tag == difficulty
                bouton.setBackgroundColor(if (actif) couleurNeutre else couleurInerte)
            }
        }

        private fun renderQuestion() {
            val question = round[questionIndex]
            answered = false
            memoire.choisi = -1

            tvProgress.text = getString(R.string.sa_question, questionIndex + 1, round.size)
            tvOperation.text = question.enonce
            tvConsigne.text = if (question.montreLeProduit)
                getString(R.string.sa_comment_ecrit_ce_nombre)
            else
                getString(R.string.sa_calculez_puis_choisissez_orthographe)
            tvFeedback.visibility = View.GONE
            btnNext.visibility = View.INVISIBLE

            optionButtons.forEachIndexed { position, bouton ->
                val proposition = question.options.getOrNull(position)
                if (proposition == null) {
                    bouton.visibility = View.GONE
                } else {
                    bouton.visibility = View.VISIBLE
                    bouton.text = proposition.texte
                    bouton.isEnabled = true
                    bouton.setBackgroundColor(couleurNeutre)
                }
            }
        }

        private fun onOptionChosen(position: Int) {
            if (answered || round.isEmpty()) return
            val question = round[questionIndex]
            val choix = question.options.getOrNull(position) ?: return
            answered = true
            memoire.choisi = position

            if (choix.juste) {
                score++
                tvScore.text = "$score / ${round.size}"
                encarter(choix.texte, question.produit)
            }

            montrerVerdict(position)
        }

        /** Fige les propositions et donne le verdict de la proposition [position]. */
        private fun montrerVerdict(position: Int) {
            val question = round[questionIndex]
            val choix = question.options.getOrNull(position) ?: return
            optionButtons.forEachIndexed { i, bouton ->
                val proposition = question.options.getOrNull(i)
                bouton.isEnabled = false
                bouton.setBackgroundColor(
                    when {
                        proposition?.juste == true -> couleurJuste
                        i == position -> couleurFausse
                        else -> couleurInerte
                    }
                )
            }

            // Le produit reste caché pendant la question au niveau difficile ;
            // une fois répondu il n'y a plus de raison de le taire.
            tvOperation.text = "${question.gauche} × ${question.droite} = ${question.produit}"

            // C'est ici que le jeu enseigne : la raison de la faute commise,
            // pas seulement le verdict. Une bonne réponse rappelle la forme.
            tvFeedback.apply {
                val raison = choix.raison.texte(requireContext())
                text = if (choix.juste)
                    getString(R.string.zuelen_juste, raison)
                else
                    getString(R.string.zuelen_faux, raison, question.reponse)
                setTextColor(if (choix.juste) couleurJuste else couleurFausse)
                visibility = View.VISIBLE
            }

            progressBar.progress = questionIndex + 1
            btnNext.visibility = View.VISIBLE
            btnNext.text =
                if (questionIndex + 1 >= round.size) getString(R.string.sa_voir_le_resultat)
                else getString(R.string.sa_question_suivante)
        }

        private fun goToNextQuestion() {
            if (questionIndex + 1 >= round.size) {
                endRound()
                return
            }
            questionIndex++
            renderQuestion()
        }

        /**
         * Verse au carnet le numéral **bien orthographié**.
         *
         * Une réponse fausse ne donne rien : la bonne forme s'affiche alors en
         * vert, mais elle a été montrée, pas écrite. Et une même manche ne tire
         * jamais deux fois le même produit, donc jamais deux fois la même
         * carte.
         */
        private fun encarter(forme: String, valeur: Int) {
            val ctx = context ?: return
            if (forme.isBlank()) return
            if (Carnet.ajouter(ctx, forme, JeuCarte.ZUELWUERT, valeur)) neuves.add(forme)
            gagnes[forme] = valeur
            Pochette.rafraichir(boutonCarnet, ctx)
        }

        /** La pochette d'abord, le bilan de manche ensuite. */
        private fun endRound() {
            val manche = round
            enleverPochette()
            Pochette.ouvrir(
                fragment = this,
                jeu = JeuCarte.ZUELWUERT,
                formes = gagnes.keys.toList(),
                neuves = HashSet(neuves),
                encoreValide = { round === manche },
                surVue = { pochette = it },
                surFin = { if (isAdded && round === manche) montrerLeBilan() }
            )
        }

        private fun montrerLeBilan() {
            val total = round.size
            val message = when {
                score == total -> getString(R.string.score_sans_faute, score, total)
                score == 0 -> getString(R.string.score_aucune)
                score * 2 >= total -> resources.getQuantityString(R.plurals.score_bonnes_reponses, score, score, total)
                else -> resources.getQuantityString(R.plurals.score_bonnes_reponses_relance, score, score, total)
            }
            AlertDialog.Builder(requireContext())
                .setTitle(if (score * 2 >= total) getString(R.string.sa_bravo) else getString(R.string.sa_encore_un_effort))
                .setMessage(message)
                .setPositiveButton(getString(R.string.sa_rejouer)) { _, _ -> startNewRound() }
                .setNegativeButton(getString(R.string.sa_ok), null)
                .show()
        }

        private fun enleverPochette() {
            pochette?.let { (it.parent as? ViewGroup)?.removeView(it) }
            pochette = null
        }

        override fun onDestroyView() {
            super.onDestroyView()
            enleverPochette()
            optionButtons.clear()
            rootView = null
        }
    }

    /**
     * Fragment « Kräizwuert » : une grille de mots croisés numérotée, ses
     * définitions en français, et un pavé de lettres pour y répondre.
     *
     * C'est le seul jeu qui demande d'**écrire** le luxembourgeois. Trois
     * conséquences sur l'écran :
     *
     * - Le pavé de lettres est fourni par le jeu et non par le clavier système.
     *   Il porte Ä Ë É Ö Ü, que le clavier actif de l'appareil n'a aucune raison
     *   d'offrir — et si le joueur ne peut pas écrire « gréng », la seule chose
     *   que le jeu lui apprend est de laisser tomber l'accent.
     * - La grille est en capitales, comme toute grille de mots croisés, mais la
     *   forme canonique est rappelée à chaque mot trouvé : la capitale efface
     *   justement la majuscule des substantifs, qui est une règle de la langue.
     * - Rien n'est corrigé lettre à lettre. Une case fausse ne se signale
     *   qu'une fois son mot entièrement rempli, sinon le jeu dicte la réponse.
     */
    class CrosswordFragment : Fragment(), JeuAuClavier {

        private var rootView: ScrollView? = null

        private lateinit var tvProgres: TextView
        private lateinit var tvDefinition: TextView
        private lateinit var tvNumero: TextView
        private lateinit var tvRetour: TextView
        private lateinit var conteneurGrille: LinearLayout
        private lateinit var conteneurPave: LinearLayout
        private lateinit var conteneurHorizontal: LinearLayout
        private lateinit var conteneurVertical: LinearLayout
        private lateinit var ligneDifficulte: LinearLayout

        /**
         * Ce qui doit survivre à une rotation : la partie, pas l'écran.
         *
         * Tourner une tablette recrée tout l'écran, et la grille en cours
         * repartait de zéro avec ses mots déjà trouvés. Le ViewModel survit à
         * la recréation ; il disparaît avec le jeu quand on le ferme.
         */
        class Partie : androidx.lifecycle.ViewModel() {
            var session: CrosswordSession? = null
            var difficulte = CrosswordDifficulty.NORMALE
            val resolus = mutableSetOf<Int>()
            val cartesNeuves = mutableSetOf<Int>()
            var solutionMontree = false
        }

        private val memoire by lazy {
            androidx.lifecycle.ViewModelProvider(this)[Partie::class.java]
        }

        private var session: CrosswordSession?
            get() = memoire.session
            set(valeur) { memoire.session = valeur }
        private var difficulte: CrosswordDifficulty
            get() = memoire.difficulte
            set(valeur) { memoire.difficulte = valeur }

        /** Fond de chaque case jouable, indexé par ligne × largeur + colonne. */
        private val fondsCase = mutableMapOf<Int, GradientDrawable>()
        private val lettresCase = mutableMapOf<Int, TextView>()
        private val lignesDefinition = mutableMapOf<Int, TextView>()

        /** Mots déjà trouvés, pour ne féliciter qu'une fois. */
        private val resolus get() = memoire.resolus

        private lateinit var boutonCarnet: TextView

        /** Emplacements dont le mot est entré au carnet pour la première fois. */
        private val cartesNeuves get() = memoire.cartesNeuves

        /** La pochette de fin de grille, posée au-dessus de tout. */
        private var pochette: View? = null

        /**
         * Vrai dès que « Solution » a été touché.
         *
         * Une grille révélée ne verse rien au carnet et n'ouvre pas de
         * pochette : la récompense suit ce qui a été trouvé, pas ce qui a été
         * montré. Les mots déjà gagnés avant la révélation, eux, restent
         * acquis — ils l'ont été.
         */
        private var solutionMontree: Boolean
            get() = memoire.solutionMontree
            set(valeur) { memoire.solutionMontree = valeur }

        private val couleurNeutre = Color.parseColor("#1976D2")
        private val couleurJuste = Color.parseColor("#4CAF50")
        private val couleurFausse = Color.parseColor("#E53935")
        private val couleurInerte = Color.parseColor("#BDBDBD")
        private val fondCase = Color.WHITE
        private val fondMotChoisi = Color.parseColor("#E3F2FD")
        private val fondCaseChoisie = Color.parseColor("#90CAF9")
        private val fondJuste = Color.parseColor("#C8E6C9")
        private val fondJusteChoisi = Color.parseColor("#A5D6A7")
        private val fondFaux = Color.parseColor("#FFCDD2")

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val activity = requireActivity() as SettingsActivity

            rootView = ScrollView(activity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.parseColor("#F5F5F5"))

                val colonne = LinearLayout(activity).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.VERTICAL
                    // Marges serrées, et elles ne sont pas décoratives : la
                    // grille et le pavé doivent tenir ensemble sous la barre
                    // d'onglets, sinon il faut faire défiler l'écran entre
                    // chaque lettre.
                    setPadding(24, 10, 24, 16)

                    val entete = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 8 }
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL

                        addView(TextView(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                            )
                            text = "🧩 Kräizwuert"
                            textSize = 18f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(couleurNeutre)
                        })

                        boutonCarnet = Pochette.bouton(
                            this@CrosswordFragment,
                            Color.parseColor("#C2185B"),
                            petit = true
                        ).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { rightMargin = 12 }
                        }
                        addView(boutonCarnet)

                        tvProgres = TextView(activity).apply {
                            textSize = 14f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.parseColor("#333333"))
                        }
                        addView(tvProgres)
                    }
                    addView(entete)

                    ligneDifficulte = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 10 }
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                    }
                    CrosswordDifficulty.values().forEach { niveau ->
                        ligneDifficulte.addView(Button(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                            ).apply { setMargins(4, 0, 4, 0) }
                            setText(niveau.label)
                            textSize = 12f
                            isAllCaps = false
                            minHeight = 0
                            minimumHeight = 0
                            setPadding(0, 14, 0, 14)
                            setTextColor(Color.WHITE)
                            tag = niveau
                            setOnClickListener {
                                difficulte = niveau
                                nouvelleGrille()
                            }
                        })
                    }
                    addView(ligneDifficulte)

                    // La définition du mot sélectionné, en grand et au-dessus de
                    // la grille. C'est le choix qui remplace les cases-flèches :
                    // une définition écrite dans une case de trente pixels ne se
                    // lit pas, et la reléguer sous la grille obligerait à faire
                    // défiler l'écran entre chaque lettre.
                    val carteDefinition = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 10 }
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(20, 14, 20, 14)
                        background = GradientDrawable().apply {
                            cornerRadius = 12f
                            setColor(Color.WHITE)
                        }

                        tvNumero = TextView(activity).apply {
                            textSize = 16f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(couleurNeutre)
                            setPadding(0, 0, 14, 0)
                        }
                        addView(tvNumero)

                        tvDefinition = TextView(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                            )
                            textSize = 16f
                            setLineSpacing(0f, 1.2f)
                            setTextColor(Color.parseColor("#212121"))
                        }
                        addView(tvDefinition)
                    }
                    addView(carteDefinition)

                    // La grille, ligne par ligne. Un LinearLayout et non une
                    // GridView : celle-ci vole le geste de défilement vertical
                    // à la ScrollView parente, même en lecture seule (même
                    // piège que Wuertriet et Wuertsich).
                    conteneurGrille = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = 14 }
                        orientation = LinearLayout.VERTICAL
                    }
                    addView(conteneurGrille)

                    tvRetour = TextView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 10 }
                        textSize = 14f
                        gravity = Gravity.CENTER
                        setTypeface(null, Typeface.BOLD)
                        setLineSpacing(0f, 1.2f)
                        visibility = View.INVISIBLE
                        // La ligne garde sa place même vide : sans cela, la
                        // grille et le pavé sautent d'un cran à chaque mot
                        // trouvé, et le doigt tombe à côté de la touche visée.
                        text = " "
                    }
                    addView(tvRetour)

                    conteneurPave = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 14 }
                        orientation = LinearLayout.VERTICAL
                    }
                    addView(conteneurPave)
                    construirePave(activity)

                    val ligneBoutons = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 16 }
                        orientation = LinearLayout.HORIZONTAL

                        addView(Button(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                            ).apply { rightMargin = 8 }
                            text = getString(R.string.sa_nouvelle_grille)
                            textSize = 13f
                            isAllCaps = false
                            setBackgroundColor(Color.parseColor("#9C27B0"))
                            setTextColor(Color.WHITE)
                            setTypeface(null, Typeface.BOLD)
                            setOnClickListener { nouvelleGrille() }
                        })
                        addView(Button(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                            )
                            text = getString(R.string.sa_solution)
                            textSize = 13f
                            isAllCaps = false
                            setBackgroundColor(couleurInerte)
                            setTextColor(Color.WHITE)
                            setTypeface(null, Typeface.BOLD)
                            setOnClickListener { montrerLaSolution() }
                        })
                    }
                    addView(ligneBoutons)

                    val carteDefinitions = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 16 }
                        orientation = LinearLayout.VERTICAL
                        setPadding(24, 20, 24, 20)
                        background = GradientDrawable().apply {
                            cornerRadius = 12f
                            setColor(Color.WHITE)
                        }

                        addView(TextView(activity).apply {
                            text = getString(R.string.sa_horizontalement)
                            textSize = 15f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(couleurNeutre)
                            setPadding(0, 0, 0, 8)
                        })
                        conteneurHorizontal = LinearLayout(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { bottomMargin = 14 }
                            orientation = LinearLayout.VERTICAL
                        }
                        addView(conteneurHorizontal)

                        addView(TextView(activity).apply {
                            text = getString(R.string.sa_verticalement)
                            textSize = 15f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(couleurNeutre)
                            setPadding(0, 0, 0, 8)
                        })
                        conteneurVertical = LinearLayout(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            orientation = LinearLayout.VERTICAL
                        }
                        addView(conteneurVertical)
                    }
                    addView(carteDefinitions)

                    val carteRegles = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        orientation = LinearLayout.VERTICAL
                        setPadding(24, 20, 24, 20)
                        background = GradientDrawable().apply {
                            cornerRadius = 12f
                            setColor(Color.WHITE)
                        }

                        addView(TextView(activity).apply {
                            text = getString(R.string.sa_regles_du_jeu)
                            textSize = 16f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(couleurNeutre)
                            setPadding(0, 0, 0, 12)
                        })
                        addView(TextView(activity).apply {
                            text = getString(R.string.sa_chaque_definition_est_le_sens)
                            textSize = 14f
                            setLineSpacing(0f, 1.2f)
                            setTextColor(Color.parseColor("#333333"))
                        })
                        addView(TextView(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { topMargin = 16 }
                            text = getString(R.string.sa_definitions_et_vocabulaire) +
                                CrosswordData.attribution(activity)
                            textSize = 11f
                            setTextColor(Color.parseColor("#757575"))
                        })
                    }
                    addView(carteRegles)

                    if (DeuxColonnes.actives(activity)) {
                        DeuxColonnes.repartir(
                            this,
                            enHaut = listOf(entete, ligneDifficulte),
                            aGauche = listOf(carteDefinition, conteneurGrille)
                        )
                    }
                }

                addView(colonne)

                post {
                    // Même précaution que les autres jeux : ce post() peut
                    // s'exécuter après un changement d'onglet.
                    if (isAdded) {
                        if (session == null) nouvelleGrille() else reprendre()
                    }
                }
            }

            return rootView!!
        }

        /**
         * Le pavé de saisie, dans la disposition choisie pour le clavier : voir
         * [CrosswordData.pave] pour le raisonnement.
         *
         * Chaque rangée pèse autant d'unités que les rangées du clavier (dix
         * sur « Luxembourg », onze sur « Suisse allemand »), et c'est ce qui
         * aligne les touches d'une rangée à l'autre : la troisième porte sept
         * lettres entre l'emplacement vide de `⇧` et `⌫`, à leur largeur du
         * clavier ; la quatrième porte les diacritiques restantes en touches
         * doubles, centrées.
         *
         * Il ne dépend pas de la grille ; il est refait seulement quand la
         * disposition a changé entre-temps (voir [onResume]).
         */
        private fun construirePave(activity: SettingsActivity) {
            val disposition = KeyboardPreferences.disposition(activity)
            dispositionDuPave = disposition
            conteneurPave.removeAllViews()
            val pave = CrosswordData.pave(disposition)
            lettresDuPave = (pave.lettres.joinToString("") + pave.accents).toSet()

            fun nouvelleLigne() = LinearLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 5 }
                orientation = LinearLayout.HORIZONTAL
                weightSum = pave.largeur
            }
            fun ajouterLettres(ligne: LinearLayout, lettres: String, poids: Float) =
                lettres.forEach { lettre ->
                    ligne.addView(toucheDuPave(activity, lettre.toString(), poids) {
                        session?.ecrire(lettre)
                        apresSaisie()
                    })
                }

            pave.lettres.forEachIndexed { rang, lettres ->
                val ligne = nouvelleLigne()
                val derniere = rang == pave.lettres.lastIndex
                if (derniere) {
                    // L'emplacement de la touche majuscule reste vide : la
                    // grille est tout en capitales, mais retirer la place
                    // décalerait la rangée par rapport aux deux du dessus.
                    ligne.addView(espaceurDuPave(activity, pave.poidsEffacement))
                }
                ajouterLettres(ligne, lettres, 1f)
                if (derniere) {
                    ligne.addView(toucheDuPave(activity, "⌫", pave.poidsEffacement) {
                        session?.effacer()
                        apresSaisie()
                    })
                }
                conteneurPave.addView(ligne)
            }

            val ligneAccents = nouvelleLigne()
            val marge = (pave.largeur - 2f * pave.accents.length) / 2f
            ligneAccents.addView(espaceurDuPave(activity, marge))
            ajouterLettres(ligneAccents, pave.accents, 2f)
            ligneAccents.addView(espaceurDuPave(activity, marge))
            conteneurPave.addView(ligneAccents)
        }

        /** La disposition du pavé affiché, pour le refaire si elle a changé. */
        private var dispositionDuPave: DispositionClavier? = null

        /** Les lettres que le pavé propose : un clavier physique n'écrit que celles-là. */
        private var lettresDuPave: Set<Char> = emptySet()

        /**
         * Un clavier physique branché (tablette avec étui-clavier) écrit dans
         * la grille comme le pavé : mêmes lettres, ⌫ pour effacer. Une lettre
         * absente du pavé, un chiffre par exemple, est ignorée.
         */
        override fun surToucheClavier(event: android.view.KeyEvent): Boolean {
            if (event.action != android.view.KeyEvent.ACTION_DOWN) return false
            if (event.keyCode == android.view.KeyEvent.KEYCODE_DEL) {
                session?.effacer()
                apresSaisie()
                return true
            }
            val code = event.unicodeChar
            if (code == 0 || code and android.view.KeyCharacterMap.COMBINING_ACCENT != 0) return false
            val lettre = code.toChar().uppercaseChar()
            if (lettre !in lettresDuPave) return false
            session?.ecrire(lettre)
            apresSaisie()
            return true
        }

        override fun onPause() {
            (activity as? SettingsActivity)?.let { if (it.jeuAuClavier === this) it.jeuAuClavier = null }
            super.onPause()
        }

        override fun onResume() {
            super.onResume()
            (activity as? SettingsActivity)?.jeuAuClavier = this
            // Les réglages du clavier s'ouvrent par-dessus cet écran : au
            // retour, le pavé suit la disposition qu'on vient d'y choisir.
            val activity = activity as? SettingsActivity ?: return
            if (::conteneurPave.isInitialized &&
                dispositionDuPave != KeyboardPreferences.disposition(activity)
            ) {
                construirePave(activity)
            }
        }

        /** Place réservée dans une rangée du pavé, sans touche dessous. */
        private fun espaceurDuPave(activity: SettingsActivity, poids: Float) =
            View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, poids
                )
            }

        private fun toucheDuPave(
            activity: SettingsActivity,
            libelle: String,
            poids: Float = 1f,
            action: () -> Unit
        ) = TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, poids
            ).apply { setMargins(3, 0, 3, 0) }
            text = libelle
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 9, 0, 9)
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#212121"))
            background = GradientDrawable().apply {
                cornerRadius = 8f * resources.displayMetrics.density
                setColor(Color.WHITE)
                setStroke(
                    (1f * resources.displayMetrics.density).toInt(),
                    Color.parseColor("#D0D0D0")
                )
            }
            isClickable = true
            setOnClickListener { action() }
        }

        private fun nouvelleGrille() {
            val activity = requireActivity()
            val grille = CrosswordData.newGrid(activity, difficulte)
            resolus.clear()
            cartesNeuves.clear()
            solutionMontree = false
            enleverPochette()
            Pochette.rafraichir(boutonCarnet, activity)
            surlignerDifficulte()
            // Sans cela « Solution affichée — cette grille ne compte pas »
            // survit au changement de grille et accuse la suivante.
            tvRetour.visibility = View.INVISIBLE

            if (grille == null) {
                session = null
                conteneurGrille.removeAllViews()
                conteneurHorizontal.removeAllViews()
                conteneurVertical.removeAllViews()
                tvProgres.text = ""
                tvNumero.text = ""
                tvDefinition.text = getString(R.string.sa_aucune_grille_disponible_actif_luxemburgish_2)
                return
            }

            val nouvelle = CrosswordSession(grille)
            session = nouvelle
            // On commence sur le 1, pas sur le premier mot du fichier : celui-ci
            // est le mot d'amorce du générateur, qui peut porter n'importe quel
            // numéro et laisse croire que la grille commence au milieu.
            nouvelle.selectionnerMot(maxOf(0, grille.numeros.indexOf(1)))
            construireGrille(requireActivity() as SettingsActivity, grille)
            construireDefinitions(requireActivity() as SettingsActivity, grille)
            rafraichir()
        }

        /**
         * Redessine la partie en cours dans un écran neuf, après une rotation :
         * mêmes lettres, même mot choisi, à la taille du nouvel écran.
         */
        private fun reprendre() {
            val activity = requireActivity() as SettingsActivity
            val partie = session ?: return nouvelleGrille()
            Pochette.rafraichir(boutonCarnet, activity)
            surlignerDifficulte()
            construireGrille(activity, partie.grid)
            construireDefinitions(activity, partie.grid)
            rafraichir()
            when {
                solutionMontree -> {
                    tvRetour.text = getString(R.string.sa_solution_affichee_cette_grille_ne_2)
                    tvRetour.setTextColor(Color.parseColor("#757575"))
                    tvRetour.visibility = View.VISIBLE
                }
                partie.termine() -> {
                    tvRetour.text = getString(R.string.sa_grille_terminee_mots_sur, partie.grid.words.size)
                    tvRetour.setTextColor(couleurJuste)
                    tvRetour.visibility = View.VISIBLE
                }
            }
        }

        private fun surlignerDifficulte() {
            for (i in 0 until ligneDifficulte.childCount) {
                val bouton = ligneDifficulte.getChildAt(i) as Button
                bouton.setBackgroundColor(
                    if (bouton.tag == difficulte) couleurNeutre else couleurInerte
                )
            }
        }

        /**
         * Dessine la grille.
         *
         * Le côté d'une case se déduit de la largeur de l'écran et du nombre de
         * colonnes : une taille fixe déborderait sur les grilles de onze
         * colonnes, et laisserait la moitié de l'écran vide sur celles de neuf.
         */
        private fun construireGrille(activity: SettingsActivity, grille: CrosswordGrid) {
            conteneurGrille.removeAllViews()
            fondsCase.clear()
            lettresCase.clear()

            val densite = resources.displayMetrics.density
            val deuxColonnes = DeuxColonnes.actives(activity)
            val disponible = ((LargeurLecture.largeurEcran(activity) - (48 * 2)) *
                (if (deuxColonnes) DeuxColonnes.PART_GRILLE else 1f)).toInt()
            // Trois bornes, et la troisième est celle qui compte : une grille
            // haute chassait le pavé hors de l'écran, en commençant par sa
            // rangée d'accents — c'est-à-dire par les cinq touches pour
            // lesquelles ce pavé existe. La grille cède donc quelques pixels
            // plutôt que le pavé, qui est le seul des deux dont on ne peut pas
            // se passer sans faire défiler l'écran à chaque lettre.
            // En deux colonnes, le pavé n'est plus sous la grille : elle
            // reprend la hauteur qu'il occupait.
            val budgetHauteur = (resources.displayMetrics.heightPixels *
                (if (deuxColonnes) DeuxColonnes.PART_HAUTEUR else 0.40f)).toInt()
            val cote = minOf(
                disponible / grille.width,
                budgetHauteur / grille.height,
                (44 * densite).toInt()
            )

            for (r in 0 until grille.height) {
                val ligne = LinearLayout(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.HORIZONTAL
                }

                for (c in 0 until grille.width) {
                    val cadre = FrameLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(cote, cote).apply {
                            setMargins(1, 1, 1, 1)
                        }
                    }

                    if (grille.estCaseJouable(r, c)) {
                        val fond = GradientDrawable().apply {
                            cornerRadius = 3f * densite
                            setColor(fondCase)
                            setStroke((1f * densite).toInt(), Color.parseColor("#9E9E9E"))
                        }
                        cadre.background = fond

                        grille.numerosParCase[r * grille.width + c]?.let { numero ->
                            cadre.addView(TextView(activity).apply {
                                layoutParams = FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.WRAP_CONTENT,
                                    FrameLayout.LayoutParams.WRAP_CONTENT
                                ).apply { gravity = Gravity.START or Gravity.TOP }
                                text = numero.toString()
                                textSize = 8f
                                setPadding((2 * densite).toInt(), 0, 0, 0)
                                setTextColor(Color.parseColor("#757575"))
                            })
                        }

                        val lettre = TextView(activity).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                            gravity = Gravity.CENTER
                            textSize = 16f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.parseColor("#212121"))
                        }
                        cadre.addView(lettre)

                        cadre.isClickable = true
                        cadre.setOnClickListener {
                            session?.selectionner(r, c)
                            rafraichir()
                        }

                        fondsCase[r * grille.width + c] = fond
                        lettresCase[r * grille.width + c] = lettre
                    }

                    ligne.addView(cadre)
                }
                conteneurGrille.addView(ligne)
            }
        }

        private fun construireDefinitions(activity: SettingsActivity, grille: CrosswordGrid) {
            conteneurHorizontal.removeAllViews()
            conteneurVertical.removeAllViews()
            lignesDefinition.clear()

            listOf(true to conteneurHorizontal, false to conteneurVertical)
                .forEach { (horizontal, conteneur) ->
                    grille.definitions(horizontal).forEach { index ->
                        val mot = grille.words[index]
                        val vue = TextView(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { bottomMargin = 8 }
                            text = getString(R.string.sa_lettres_3, grille.numeros[index], mot.clue, mot.length)
                            textSize = 14f
                            setLineSpacing(0f, 1.15f)
                            isClickable = true
                            setOnClickListener {
                                session?.selectionnerMot(index)
                                rafraichir()
                            }
                        }
                        lignesDefinition[index] = vue
                        conteneur.addView(vue)
                    }
                }
        }

        /**
         * Appelé après chaque touche du pavé : rafraîchit, puis dit ce qui vient
         * d'être trouvé.
         *
         * Le message n'apparaît qu'au moment où un mot devient juste, et il
         * rappelle la forme canonique — c'est la seule chose que la grille, tout
         * en capitales, ne peut pas montrer.
         */
        private fun apresSaisie() {
            val partie = session ?: return
            val grille = partie.grid

            val nouveaux = grille.words.indices.filter {
                it !in resolus && partie.motJuste(it)
            }
            resolus.addAll(nouveaux)
            encarter(nouveaux)
            // Un mot achevé rend la main au suivant : sans cela le pavé continue
            // d'écrire dans un mot déjà juste, et le joueur doit viser une case
            // pour repartir.
            if (partie.motSelectionne in resolus && !partie.termine()) {
                avancerAuMotSuivant(partie)
            }
            rafraichir()

            when {
                partie.termine() -> {
                    tvRetour.text = getString(R.string.sa_grille_terminee_mots_sur, grille.words.size)
                    tvRetour.setTextColor(couleurJuste)
                    tvRetour.visibility = View.VISIBLE
                    if (!solutionMontree) ouvrirPochette()
                }
                nouveaux.isNotEmpty() -> {
                    val mot = grille.words[nouveaux.first()]
                    tvRetour.text = if (mot.enseigneUneMajuscule) {
                        getString(R.string.sa_un_substantif_hors_de_la, mot.canonical)
                    } else {
                        getString(R.string.sa_ecrit_en_minuscules, mot.canonical)
                    }
                    tvRetour.setTextColor(couleurJuste)
                    tvRetour.visibility = View.VISIBLE
                }
                else -> {
                    val choisi = partie.motSelectionne
                    if (choisi >= 0 && partie.motRempli(choisi) && !partie.motJuste(choisi)) {
                        tvRetour.text = getString(R.string.sa_ce_est_pas_le_mot)
                        tvRetour.setTextColor(couleurFausse)
                        tvRetour.visibility = View.VISIBLE
                    } else {
                        tvRetour.visibility = View.INVISIBLE
                    }
                }
            }
        }

        /**
         * Verse au carnet les mots qui viennent d'être écrits.
         *
         * Kräizwuert est le seul jeu où le joueur **écrit** le mot lui-même, à
         * partir de sa seule définition : la carte s'y mérite plus qu'ailleurs.
         * Elle se prend sur la forme canonique — la grille, elle, est tout en
         * capitales, et la majuscule des substantifs y disparaît.
         *
         * Une grille révélée ne verse rien : `reveler()` remplit les cases
         * sans que personne les ait trouvées, et [apresSaisie] n'est de toute
         * façon plus appelé à ce moment-là.
         */
        private fun encarter(nouveaux: List<Int>) {
            if (nouveaux.isEmpty() || solutionMontree) return
            val ctx = context ?: return
            val grille = session?.grid ?: return
            nouveaux.forEach { emplacement ->
                val forme = grille.words[emplacement].canonical
                if (Carnet.ajouter(ctx, forme, JeuCarte.KRAIZWUERT)) {
                    cartesNeuves.add(emplacement)
                }
            }
            Pochette.rafraichir(boutonCarnet, ctx)
        }

        /** La pochette, une fois la grille entièrement trouvée. */
        private fun ouvrirPochette() {
            val partie = session ?: return
            val grille = partie.grid
            enleverPochette()
            Pochette.ouvrir(
                fragment = this,
                jeu = JeuCarte.KRAIZWUERT,
                formes = resolus.sorted().map { grille.words[it].canonical },
                neuves = cartesNeuves.mapTo(HashSet()) { grille.words[it].canonical },
                encoreValide = { session === partie },
                surVue = { pochette = it }
            )
        }

        private fun enleverPochette() {
            pochette?.let { (it.parent as? ViewGroup)?.removeView(it) }
            pochette = null
        }

        /**
         * Passe au premier mot encore faux, dans l'ordre des numéros. Le tour
         * est circulaire : après le dernier on revient au début, parce que les
         * mots trouvés ne le sont pas dans l'ordre de la grille.
         */
        private fun avancerAuMotSuivant(partie: CrosswordSession) {
            val grille = partie.grid
            val ordre = grille.words.indices.sortedWith(
                compareBy({ grille.numeros[it] }, { !grille.words[it].across })
            )
            val depuis = ordre.indexOf(partie.motSelectionne)
            for (pas in 1..ordre.size) {
                val candidat = ordre[(depuis + pas) % ordre.size]
                if (!partie.motJuste(candidat)) {
                    partie.selectionnerMot(candidat)
                    return
                }
            }
        }

        private fun montrerLaSolution() {
            val partie = session ?: return
            solutionMontree = true
            partie.reveler()
            resolus.addAll(partie.grid.words.indices)
            rafraichir()
            tvRetour.text = getString(R.string.sa_solution_affichee_cette_grille_ne_2)
            tvRetour.setTextColor(Color.parseColor("#757575"))
            tvRetour.visibility = View.VISIBLE
        }

        /** Repeint la grille, la définition courante et les listes. */
        private fun rafraichir() {
            val partie = session ?: return
            val grille = partie.grid

            val choisi = partie.motSelectionne
            val mot = grille.words.getOrNull(choisi)

            for (r in 0 until grille.height) {
                for (c in 0 until grille.width) {
                    val cle = r * grille.width + c
                    val fond = fondsCase[cle] ?: continue
                    val vue = lettresCase[cle] ?: continue

                    val lettre = partie.lettreAt(r, c)
                    vue.text = lettre?.toString() ?: ""

                    val dansLeMot = mot != null && mot.indexOf(r, c) >= 0
                    val caseCourante = dansLeMot &&
                        mot!!.indexOf(r, c) == partie.caseSelectionnee
                    // Une faute ne se montre qu'une fois le mot rempli : le
                    // signaler à la frappe reviendrait à dicter la réponse
                    // lettre par lettre.
                    val revelee = grille.motsSur(r, c).any { partie.motRempli(it) }
                    val fausse = revelee && partie.caseFausse(r, c)
                    val juste = grille.motsSur(r, c).any {
                        partie.motRempli(it) && partie.motJuste(it)
                    }

                    // Un mot trouvé reste vert de bout en bout, curseur compris :
                    // laisser la dernière case en bleu donnait un mot vert à
                    // une case près, qu'on lit comme une faute.
                    fond.setColor(
                        when {
                            fausse -> fondFaux
                            juste && caseCourante -> fondJusteChoisi
                            juste -> fondJuste
                            caseCourante -> fondCaseChoisie
                            dansLeMot -> fondMotChoisi
                            else -> fondCase
                        }
                    )
                    vue.setTextColor(
                        if (fausse) couleurFausse else Color.parseColor("#212121")
                    )
                }
            }

            if (mot != null) {
                tvNumero.text = "${grille.numeros[choisi]} " +
                    if (mot.across) "➡️" else "⬇️"
                tvDefinition.text = getString(R.string.sa_lettres_2, mot.clue, mot.length)
            }

            lignesDefinition.forEach { (index, vue) ->
                val trouve = index in resolus
                vue.setTextColor(
                    when {
                        trouve -> Color.parseColor("#9E9E9E")
                        index == choisi -> couleurNeutre
                        else -> Color.parseColor("#333333")
                    }
                )
                vue.setTypeface(null, if (index == choisi) Typeface.BOLD else Typeface.NORMAL)
            }

            tvProgres.text = getString(R.string.sa_mots, partie.motsJustes(), grille.words.size)
        }

        override fun onDestroyView() {
            super.onDestroyView()
            enleverPochette()
            fondsCase.clear()
            lettresCase.clear()
            lignesDefinition.clear()
            // La partie, elle, reste : voir [Partie].
            rootView = null
        }
    }

    /**
     * Fragment « Wuertplaz » : une grille vide, la liste des mots à y caser, et
     * pas une seule définition.
     *
     * C'est le seul jeu **jouable sans connaître un mot de luxembourgeois**.
     * Les six autres supposent une compréhension préalable, ne serait-ce que
     * pour lire une définition ; ici la déduction est géométrique — une
     * longueur, des croisements — et la langue s'apprend en récompense.
     *
     * Trois conséquences sur l'écran :
     *
     * - **La glose française n'apparaît qu'au verrouillage d'un mot**, c'est-à-
     *   dire quand tous ses croisements sont posés. La montrer au dépôt ferait
     *   résoudre la grille par sondage — poser, regarder si la glose s'allume,
     *   retirer. Même discipline que Kräizwuert, qui ne signale une lettre
     *   fausse qu'une fois son mot entièrement écrit.
     * - **Un mot incompatible ne se pose pas.** Ce n'est pas une correction,
     *   c'est le crayon : on n'écrit pas deux lettres dans la même case. Ce qui
     *   reste faux est un mot compatible mais mal placé, et celui-là attend
     *   d'être confronté à tous ses croisements pour se signaler.
     * - **Les mots sont groupés par longueur** dans la liste, parce que c'est
     *   ainsi qu'on joue : on cherche d'abord ce qui a la bonne taille. Les
     *   ranger dans le désordre obligerait à recompter chaque mot à chaque
     *   essai, ce qui n'apprend rien.
     */
    class ChasseCroiseFragment : Fragment() {

        private var rootView: ScrollView? = null

        // Les confettis de fin de grille sont posés dans le cadre de contenu de
        // l'activité (android.R.id.content), au-dessus de tout : ils ne
        // défilent pas et ne décalent rien. Suivi ici pour être retirés.
        private var confetti: ConfettiView? = null

        /** La pochette de fin de grille, posée au même endroit. */
        private var pochette: View? = null

        private lateinit var tvProgres: TextView
        private lateinit var tvRetour: TextView
        private lateinit var conteneurGrille: LinearLayout
        private lateinit var conteneurMots: LinearLayout
        private lateinit var titreGagnes: TextView
        private lateinit var boutonCarnet: TextView
        private lateinit var conteneurGagnes: LinearLayout
        private lateinit var ligneDifficulte: LinearLayout

        /**
         * Ce qui doit survivre à une rotation : la partie, pas l'écran. Même
         * raison que pour Kräizwuert, voir [CrosswordFragment.Partie].
         */
        class Partie : androidx.lifecycle.ViewModel() {
            var session: ChasseCroiseSession? = null
            var difficulte = CrosswordDifficulty.NORMALE
            val resolus = mutableSetOf<Int>()
            val gagnesAffiches = mutableSetOf<Int>()
            var retraits = 0
            val cartesNeuves = mutableSetOf<Int>()
        }

        private val memoire by lazy {
            androidx.lifecycle.ViewModelProvider(this)[Partie::class.java]
        }

        private var session: ChasseCroiseSession?
            get() = memoire.session
            set(valeur) { memoire.session = valeur }
        private var difficulte: CrosswordDifficulty
            get() = memoire.difficulte
            set(valeur) { memoire.difficulte = valeur }

        private val fondsCase = mutableMapOf<Int, GradientDrawable>()
        private val lettresCase = mutableMapOf<Int, TextView>()
        private val chipsParMot = mutableMapOf<Int, TextView>()

        /** Mots déjà verrouillés, pour ne récompenser qu'une fois. */
        private val resolus get() = memoire.resolus

        /**
         * Lignes de « Ce que vous avez gagné » déjà portées à l'écran : sert à
         * n'animer l'entrée que des nouvelles, la liste étant reconstruite en
         * entier à chaque rafraîchissement.
         */
        private val gagnesAffiches get() = memoire.gagnesAffiches

        /**
         * Nombre de mots repris de la grille dans la partie en cours. Un mot
         * gagné ne se reprend plus, donc ceci ne compte que les tâtonnements —
         * c'est la note de fin de grille.
         */
        private var retraits: Int
            get() = memoire.retraits
            set(valeur) { memoire.retraits = valeur }

        /**
         * Emplacements dont le mot est entré au carnet pour la première fois
         * — jamais rencontré dans aucune partie précédente. C'est ce qui
         * distingue « nouveau » de « revu » dans la liste des sens gagnés, et
         * c'est le seul frisson que la collection ait à offrir.
         */
        private val cartesNeuves get() = memoire.cartesNeuves

        private val couleurNeutre = Color.parseColor("#00796B")
        private val couleurJuste = Color.parseColor("#4CAF50")
        private val couleurFausse = Color.parseColor("#E53935")
        private val couleurInerte = Color.parseColor("#BDBDBD")
        private val fondCase = Color.WHITE
        private val fondPose = Color.parseColor("#E0F2F1")
        private val fondPossible = Color.parseColor("#B2DFDB")
        private val fondJuste = Color.parseColor("#C8E6C9")
        private val fondFaux = Color.parseColor("#FFCDD2")

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val activity = requireActivity() as SettingsActivity

            rootView = ScrollView(activity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.parseColor("#F5F5F5"))

                val colonne = LinearLayout(activity).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.VERTICAL
                    setPadding(24, 10, 24, 16)

                    val entete = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 8 }
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL

                        addView(TextView(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                            )
                            text = "🔡 Wuertplaz"
                            textSize = 18f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(couleurNeutre)
                        })

                        tvProgres = TextView(activity).apply {
                            textSize = 14f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.parseColor("#333333"))
                        }
                        addView(tvProgres)
                    }
                    addView(entete)

                    ligneDifficulte = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 10 }
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                    }
                    CrosswordDifficulty.values().forEach { niveau ->
                        ligneDifficulte.addView(Button(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                            ).apply { setMargins(4, 0, 4, 0) }
                            setText(niveau.label)
                            textSize = 12f
                            isAllCaps = false
                            minHeight = 0
                            minimumHeight = 0
                            setPadding(0, 14, 0, 14)
                            setTextColor(Color.WHITE)
                            tag = niveau
                            setOnClickListener {
                                difficulte = niveau
                                nouvelleGrille()
                            }
                        })
                    }
                    addView(ligneDifficulte)

                    // La ligne de récompense, au-dessus de la grille : c'est ce
                    // que le joueur gagne, et c'est la seule chose que ce jeu
                    // enseigne. Elle garde sa place même vide, sinon la grille
                    // saute d'un cran à chaque mot verrouillé et le doigt tombe
                    // à côté de la case visée.
                    //
                    // Hauteur figée à deux lignes — minLines ET maxLines — pour
                    // que la carte ne change jamais la mise en page, quel que
                    // soit le message : « 🔥 3 mots d'un coup ! » tient sur une
                    // ligne, les formes sur la seconde, et une glose trop
                    // longue est tronquée plutôt que de pousser la grille (sans
                    // quoi c'est l'appui suivant qui paie le décalage et tombe
                    // sur la mauvaise case). Le texte complet des sens gagnés
                    // reste lisible dans « Ce que vous avez gagné ».
                    tvRetour = TextView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 10 }
                        textSize = 15f
                        gravity = Gravity.CENTER
                        setTypeface(null, Typeface.BOLD)
                        setLineSpacing(0f, 1.2f)
                        setPadding(16, 14, 16, 14)
                        minLines = 2
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        background = GradientDrawable().apply {
                            cornerRadius = 12f
                            setColor(Color.WHITE)
                        }
                        visibility = View.INVISIBLE
                        text = " "
                    }
                    addView(tvRetour)

                    // Un LinearLayout et non une GridView : celle-ci vole le
                    // geste de défilement vertical à la ScrollView parente,
                    // même en lecture seule (même piège que Kräizwuert).
                    conteneurGrille = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = 14 }
                        orientation = LinearLayout.VERTICAL
                    }
                    addView(conteneurGrille)

                    conteneurMots = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 14 }
                        orientation = LinearLayout.VERTICAL
                    }
                    addView(conteneurMots)

                    // Les sens gagnés, qui s'accumulent au lieu de passer.
                    //
                    // Le bandeau du haut annonce, il ne conserve pas : plusieurs
                    // mots se verrouillent souvent d'un coup, et « Grille
                    // terminée » recouvrait le dernier lot. Mesuré sur les 284
                    // grilles livrées, vingt ordres de pose chacune : 58,8 % des
                    // mots ne montraient jamais leur traduction. Or c'est la
                    // seule chose que ce jeu enseigne, donc la récompense doit
                    // rester lisible après coup. La liste grandit vers le bas,
                    // sous les pastilles : elle ne déplace ni la grille ni les
                    // mots à poser, dont les appuis suivants dépendent.
                    //
                    // La ligne porte aussi l'entrée du carnet, et c'est
                    // délibérément là : le bouton dit « cette liste a une
                    // maison permanente » à l'endroit exact où la liste vit.
                    // Il reste visible même quand rien n'a encore été gagné,
                    // pour qu'un joueur qui revient retrouve sa collection
                    // sans avoir à finir une grille d'abord.
                    titreGagnes = TextView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                        )
                        text = getString(R.string.sa_ce_que_vous_avez_gagne_2)
                        textSize = 15f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(couleurNeutre)
                        visibility = View.INVISIBLE
                    }

                    boutonCarnet = TextView(activity).apply {
                        text = getString(R.string.carnet_bouton)
                        textSize = 13f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.WHITE)
                        setPadding(20, 10, 20, 10)
                        background = GradientDrawable().apply {
                            cornerRadius = 20f * resources.displayMetrics.density
                            setColor(couleurNeutre)
                        }
                        isClickable = true
                        setOnClickListener { ouvrirCarnet() }
                    }

                    addView(LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 8 }
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(titreGagnes)
                        addView(boutonCarnet)
                    })

                    conteneurGagnes = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 14 }
                        orientation = LinearLayout.VERTICAL
                    }
                    addView(conteneurGagnes)

                    val ligneBoutons = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 16 }
                        orientation = LinearLayout.HORIZONTAL

                        addView(Button(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                            ).apply { rightMargin = 8 }
                            text = getString(R.string.sa_nouvelle_grille)
                            textSize = 13f
                            isAllCaps = false
                            setBackgroundColor(Color.parseColor("#00796B"))
                            setTextColor(Color.WHITE)
                            setTypeface(null, Typeface.BOLD)
                            setOnClickListener { nouvelleGrille() }
                        })
                        addView(Button(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                            )
                            text = getString(R.string.sa_solution)
                            textSize = 13f
                            isAllCaps = false
                            setBackgroundColor(couleurInerte)
                            setTextColor(Color.WHITE)
                            setTypeface(null, Typeface.BOLD)
                            setOnClickListener { montrerLaSolution() }
                        })
                    }
                    addView(ligneBoutons)

                    val carteRegles = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        orientation = LinearLayout.VERTICAL
                        setPadding(24, 20, 24, 20)
                        background = GradientDrawable().apply {
                            cornerRadius = 12f
                            setColor(Color.WHITE)
                        }

                        addView(TextView(activity).apply {
                            text = getString(R.string.sa_regles_du_jeu)
                            textSize = 16f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(couleurNeutre)
                            setPadding(0, 0, 0, 12)
                        })
                        addView(TextView(activity).apply {
                            text = getString(R.string.sa_tous_les_mots_vous_sont)
                            textSize = 14f
                            setLineSpacing(0f, 1.2f)
                            setTextColor(Color.parseColor("#333333"))
                        })
                        addView(TextView(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { topMargin = 16 }
                            text = getString(R.string.sa_traductions_et_vocabulaire) +
                                ChasseCroiseData.attribution(activity)
                            textSize = 11f
                            setTextColor(Color.parseColor("#757575"))
                        })
                    }
                    addView(carteRegles)

                    if (DeuxColonnes.actives(activity)) {
                        DeuxColonnes.repartir(
                            this,
                            enHaut = listOf(entete, ligneDifficulte),
                            aGauche = listOf(tvRetour, conteneurGrille)
                        )
                    }
                }

                addView(colonne)

                post {
                    // Même précaution que les autres jeux : ce post() peut
                    // s'exécuter après un changement d'onglet.
                    if (isAdded) {
                        if (session == null) nouvelleGrille() else reprendre()
                    }
                }
            }

            return rootView!!
        }

        private fun nouvelleGrille() {
            val activity = requireActivity() as SettingsActivity
            val grille = ChasseCroiseData.newGrid(activity, difficulte)
            resolus.clear()
            gagnesAffiches.clear()
            cartesNeuves.clear()
            retraits = 0
            enleverConfettis()
            enleverPochette()
            surlignerDifficulte()
            majBoutonCarnet()
            titreGagnes.animate().cancel()
            titreGagnes.scaleX = 1f
            titreGagnes.scaleY = 1f
            tvRetour.animate().cancel()
            tvRetour.alpha = 1f
            tvRetour.translationY = 0f
            tvRetour.scaleX = 1f
            tvRetour.scaleY = 1f
            tvRetour.visibility = View.INVISIBLE

            if (grille == null) {
                session = null
                conteneurGrille.removeAllViews()
                conteneurMots.removeAllViews()
                conteneurGagnes.removeAllViews()
                titreGagnes.visibility = View.GONE
                tvProgres.text = ""
                annoncer(
                    getString(R.string.sa_aucune_grille_disponible_actif_luxemburgish),
                    couleurFausse
                )
                return
            }

            session = ChasseCroiseSession(grille) { it.shuffled() }
            construireGrille(activity, grille)
            construireListe(activity, session!!)
            rafraichir()
        }

        /**
         * Redessine la partie en cours dans un écran neuf, après une rotation :
         * mêmes mots posés, mêmes sens gagnés, à la taille du nouvel écran.
         */
        private fun reprendre() {
            val activity = requireActivity() as SettingsActivity
            val partie = session ?: return nouvelleGrille()
            surlignerDifficulte()
            majBoutonCarnet()
            construireGrille(activity, partie.grid)
            construireListe(activity, partie)
            rafraichir()
        }

        private fun surlignerDifficulte() {
            for (i in 0 until ligneDifficulte.childCount) {
                val bouton = ligneDifficulte.getChildAt(i) as Button
                bouton.setBackgroundColor(
                    if (bouton.tag == difficulte) couleurNeutre else couleurInerte
                )
            }
        }

        /**
         * Dessine la grille.
         *
         * Le côté d'une case se déduit de la largeur de l'écran et du nombre de
         * colonnes, borné aussi par une part de la hauteur : sans cette
         * troisième borne, une grille de onze lignes pousse la liste des mots
         * hors de l'écran — et sans la liste, ce jeu n'existe pas.
         */
        private fun construireGrille(activity: SettingsActivity, grille: CrosswordGrid) {
            conteneurGrille.removeAllViews()
            fondsCase.clear()
            lettresCase.clear()

            // Le balayage de verrouillage fait légèrement grossir une case
            // au-delà de sa ligne : sans cela elle serait rognée en haut et
            // en bas. Purement visuel, aucun effet sur la mise en page.
            conteneurGrille.clipChildren = false
            conteneurGrille.clipToPadding = false

            val densite = resources.displayMetrics.density
            val deuxColonnes = DeuxColonnes.actives(activity)
            val disponible = ((LargeurLecture.largeurEcran(activity) - (48 * 2)) *
                (if (deuxColonnes) DeuxColonnes.PART_GRILLE else 1f)).toInt()
            // En deux colonnes, la liste des mots n'est plus sous la grille.
            val budgetHauteur = (resources.displayMetrics.heightPixels *
                (if (deuxColonnes) DeuxColonnes.PART_HAUTEUR else 0.38f)).toInt()
            val cote = minOf(
                disponible / grille.width,
                budgetHauteur / grille.height,
                (44 * densite).toInt()
            )

            for (r in 0 until grille.height) {
                val ligne = LinearLayout(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.HORIZONTAL
                }

                for (c in 0 until grille.width) {
                    val cadre = FrameLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(cote, cote).apply {
                            setMargins(1, 1, 1, 1)
                        }
                    }

                    if (grille.estCaseJouable(r, c)) {
                        val fond = GradientDrawable().apply {
                            cornerRadius = 3f * densite
                            setColor(fondCase)
                            setStroke((1f * densite).toInt(), Color.parseColor("#9E9E9E"))
                        }
                        cadre.background = fond

                        val lettre = TextView(activity).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                            gravity = Gravity.CENTER
                            textSize = 16f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.parseColor("#212121"))
                        }
                        cadre.addView(lettre)

                        cadre.isClickable = true
                        cadre.setOnClickListener { toucherCase(r, c) }

                        fondsCase[r * grille.width + c] = fond
                        lettresCase[r * grille.width + c] = lettre
                    }

                    ligne.addView(cadre)
                }
                ligne.clipChildren = false
                ligne.clipToPadding = false
                conteneurGrille.addView(ligne)
            }
        }

        /**
         * La liste des mots, groupée par longueur et rangée par longueur
         * croissante — c'est ainsi qu'on joue : on cherche d'abord ce qui a la
         * bonne taille.
         *
         * Le retour à la ligne est calculé à la mesure du texte plutôt que sur
         * un nombre fixe de pastilles : « ASS » et « MËTTELPUNKT » n'occupent
         * pas la même largeur, et trois par ligne laisserait la moitié de
         * l'écran vide sur les mots courts.
         */
        private fun construireListe(activity: SettingsActivity, partie: ChasseCroiseSession) {
            conteneurMots.removeAllViews()
            chipsParMot.clear()

            val grille = partie.grid
            val densite = resources.displayMetrics.density
            // En deux colonnes, la liste n'a que la colonne de droite.
            val largeurDispo = ((LargeurLecture.largeurEcran(activity) - (48 * 2)) *
                (if (DeuxColonnes.actives(activity)) 1f - DeuxColonnes.PART_GRILLE else 1f)).toInt()
            val ecart = (8 * densite).toInt()

            partie.liste
                .groupBy { grille.words[it].length }
                .toSortedMap()
                .forEach { (longueur, mots) ->
                    conteneurMots.addView(TextView(activity).apply {
                        text = getString(R.string.sa_lettres, longueur)
                        textSize = 12f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#757575"))
                        setPadding(2, 4, 0, 6)
                    })

                    var ligne = ligneDeMots(activity)
                    var reste = largeurDispo
                    mots.forEach { index ->
                        val texte = grille.words[index].answer
                        val chip = chipMot(activity, texte, index)
                        val largeur =
                            (chip.paint.measureText(texte) + 40 * densite).toInt()
                        if (largeur > reste && ligne.childCount > 0) {
                            conteneurMots.addView(ligne)
                            ligne = ligneDeMots(activity)
                            reste = largeurDispo
                        }
                        ligne.addView(chip)
                        reste -= largeur + ecart
                        chipsParMot[index] = chip
                    }
                    if (ligne.childCount > 0) conteneurMots.addView(ligne)
                }
        }

        private fun ligneDeMots(activity: SettingsActivity) = LinearLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
        }

        private fun chipMot(activity: SettingsActivity, texte: String, index: Int) =
            TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = 8; bottomMargin = 8 }
                text = texte
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(22, 12, 22, 12)
                setTextColor(Color.parseColor("#212121"))
                background = GradientDrawable().apply {
                    cornerRadius = 8f * resources.displayMetrics.density
                    setColor(Color.WHITE)
                    setStroke(
                        (1f * resources.displayMetrics.density).toInt(),
                        Color.parseColor("#D0D0D0")
                    )
                }
                isClickable = true
                setOnClickListener {
                    val partie = session ?: return@setOnClickListener
                    if (partie.estPose(index)) return@setOnClickListener
                    partie.choisir(index)
                    tvRetour.visibility = View.INVISIBLE
                    rafraichir()
                }
            }

        /**
         * Une case touchée : on pose le mot choisi, ou on retire celui qui est
         * là.
         *
         * Une case peut appartenir à deux emplacements, l'horizontal et le
         * vertical. On prend le premier qui accepte le mot choisi — l'ambiguïté
         * est rare, parce qu'elle demande que les deux emplacements aient la
         * même longueur *et* acceptent le même mot, et le joueur la lève en
         * touchant une autre case du mot visé.
         *
         * Un mot gagné ne se retire plus : le geste est refusé et dit
         * pourquoi, plutôt que de ne rien faire — un appui sans effet se lit
         * comme une panne.
         */
        private fun toucherCase(r: Int, c: Int) {
            val partie = session ?: return
            val emplacements = partie.grid.motsSur(r, c)

            if (partie.motChoisi >= 0) {
                val cible = emplacements.firstOrNull {
                    partie.peutPoser(it, partie.motChoisi)
                }
                if (cible == null) {
                    annoncer(getString(R.string.sa_ce_mot_ne_peut_pas), couleurFausse)
                    return
                }
                partie.poser(cible)
                apresCoup()
                return
            }

            val occupe = emplacements.firstOrNull { it in partie.occupes } ?: return
            if (partie.retirer(occupe)) {
                retraits++
                tvRetour.visibility = View.INVISIBLE
                rafraichir()
            } else {
                annoncer(
                    getString(R.string.sa_est_gagne_il_reste_en, partie.grid.words[occupe].canonical),
                    couleurNeutre
                )
            }
        }

        /**
         * Appelé après chaque pose : rafraîchit, puis dit ce qui vient d'être
         * gagné.
         *
         * La glose n'apparaît qu'ici, au verrouillage, et le mot est rappelé
         * sous sa forme canonique parce que la grille est tout en capitales.
         *
         * Le rappel s'arrête là. Kräizwuert ajoute « un substantif : hors de
         * la grille, il garde sa majuscule », et c'est justifié là-bas : le
         * joueur a produit l'orthographe lui-même, sans jamais voir la forme
         * écrite. Ici les mots sont donnés dans la liste, déjà casés comme il
         * faut — la phrase ne dit alors que ce que l'écran montre déjà, et
         * elle le répète à chaque mot gagné.
         */
        private fun apresCoup() {
            val partie = session ?: return
            val grille = partie.grid

            val nouveaux = grille.words.indices.filter {
                it !in resolus && partie.verrouille(it)
            }
            resolus.addAll(nouveaux)
            encarter(nouveaux)
            rafraichir()

            when {
                partie.termine() -> {
                    balayerMots(nouveaux)
                    retourHaptique(fort = true)
                    // Une note douce : trois paliers, tous félicitants. Elle
                    // suit les tâtonnements ([retraits]), pas le chrono — le
                    // jeu n'est pas contre la montre.
                    val (etoiles, mention) = when {
                        retraits == 0 -> 3 to getString(R.string.sa_sans_une_seule_reprise)
                        retraits <= 2 -> 2 to getString(R.string.sa_bien_joue)
                        else -> 1 to getString(R.string.sa_grille_bouclee)
                    }
                    annoncerCarte(
                        getString(R.string.sa_grille_terminee_mots, grille.words.size).repeat(etoiles) + "  $mention",
                        couleurJuste
                    )
                    lancerConfettis()
                    ouvrirPochette()
                    annoncerA11y(
                        getString(R.string.wuertplaz_terminee_a11y, grille.words.size,
                            resources.getQuantityString(R.plurals.etoiles_sur_3, etoiles, etoiles))
                    )
                }
                nouveaux.isNotEmpty() -> celebrerGains(nouveaux)
                grille.words.indices.any { partie.fautif(it) } -> {
                    annoncer(
                        getString(R.string.sa_un_mot_est_la_mauvaise),
                        couleurFausse
                    )
                }
                else -> tvRetour.visibility = View.INVISIBLE
            }
        }

        /**
         * Verse au carnet les mots qui viennent d'être gagnés.
         *
         * C'est le seul point d'entrée de la collection permanente : un mot
         * n'y entre qu'une fois **verrouillé**, c'est-à-dire prouvé par ses
         * croisements. Un mot simplement posé, ou posé puis repris, ne compte
         * pas — la carte se gagne comme la glose se gagne.
         *
         * La forme versée est la forme canonique, celle que le joueur lit dans
         * la liste et que la table des gloses sait traduire ; la grille, elle,
         * est tout en capitales.
         */
        private fun encarter(nouveaux: List<Int>) {
            if (nouveaux.isEmpty()) return
            val ctx = context ?: return
            val grille = session?.grid ?: return
            nouveaux.forEach { emplacement ->
                val forme = grille.words[emplacement].canonical
                if (Carnet.ajouter(ctx, forme, JeuCarte.WUERTPLAZ)) {
                    cartesNeuves.add(emplacement)
                }
            }
            majBoutonCarnet()
        }

        private fun majBoutonCarnet() {
            val ctx = context ?: return
            Pochette.rafraichir(boutonCarnet, ctx)
        }

        private fun ouvrirCarnet() = Pochette.montrerLeCarnet(this)

        /**
         * La pochette de fin de grille.
         *
         * Elle n'arrive qu'après une grille **gagnée** : « Solution » ne passe
         * pas par ici, ne verse rien au carnet, et n'ouvre donc pas de
         * pochette. Le reste — le chargement en fond, le délai plancher, la
         * vue hôte — appartient à [Pochette], qui le fait pour les sept jeux.
         */
        private fun ouvrirPochette() {
            val partie = session ?: return
            val grille = partie.grid
            enleverPochette()
            Pochette.ouvrir(
                fragment = this,
                jeu = JeuCarte.WUERTPLAZ,
                formes = resolus.map { grille.words[it].canonical },
                neuves = cartesNeuves.mapTo(HashSet()) { grille.words[it].canonical },
                delai = Pochette.DELAI,
                encoreValide = { session === partie },
                surVue = { pochette = it }
            )
        }

        private fun enleverPochette() {
            pochette?.let { (it.parent as? ViewGroup)?.removeView(it) }
            pochette = null
        }

        /**
         * La récompense d'un ou plusieurs mots verrouillés d'un coup.
         *
         * Le verrouillage est le seul moment où ce jeu enseigne, et un joueur a
         * signalé qu'il passait inaperçu : les cases changeaient d'un vert pâle
         * à un autre, sans mouvement ni son. Ici on le ponctue — balayage des
         * cases du mot, retour haptique, carte qui entre en scène — et on
         * distingue le coup double : plusieurs mots d'un coup est l'événement
         * le plus gratifiant de la partie, il mérite une carte à part (orange)
         * et un retour haptique plus appuyé. Le détail des sens va, comme
         * avant, dans « Ce que vous avez gagné » ; la carte ne fait que fêter.
         */
        private fun celebrerGains(nouveaux: List<Int>) {
            val grille = session?.grid ?: return
            val combo = nouveaux.size > 1
            balayerMots(nouveaux)
            retourHaptique(fort = combo)

            if (combo) {
                val formes = nouveaux.take(3)
                    .joinToString(" · ") { grille.words[it].canonical }
                val suite = if (nouveaux.size > 3) " +${nouveaux.size - 3}" else ""
                annoncerCarte(
                    getString(R.string.wuertplaz_combo, nouveaux.size) + "\n$formes$suite",
                    Color.parseColor("#FB8C00")
                )
                annoncerA11y(
                    getString(R.string.wuertplaz_mots_gagnes_a11y, nouveaux.size,
                        nouveaux.joinToString(", ") {
                            "${grille.words[it].canonical}, ${grille.words[it].clue}"
                        })
                )
            } else {
                val mot = grille.words[nouveaux.first()]
                annoncerCarte("✅ ${mot.canonical} : ${mot.clue}", couleurJuste)
                annoncerA11y(getString(R.string.sa_mot_gagne, mot.canonical, mot.clue))
            }
        }

        /**
         * Balaye les cases des mots donnés : chaque case s'allume en vert vif
         * avec un léger rebond, l'une après l'autre dans le sens du mot, et les
         * mots s'enchaînent. On lit un courant qui parcourt le mot.
         *
         * Purement visuel : aucune vue n'est ajoutée ni retirée, seules la
         * couleur du fond et l'échelle de la case bougent, et un unique
         * `rafraichir()` en fin de course remet chaque case à sa couleur
         * d'état réelle. Sauté quand les animations système sont coupées.
         */
        private fun balayerMots(emplacements: List<Int>) {
            val partie = session ?: return
            val grille = partie.grid
            if (emplacements.isEmpty() || animationsReduites()) return
            val vif = Color.parseColor("#69F0AE")
            var pas = 0L
            emplacements.forEach { emplacement ->
                val mot = grille.words.getOrNull(emplacement) ?: return@forEach
                for (i in 0 until mot.length) {
                    val cle = mot.rowAt(i) * grille.width + mot.colAt(i)
                    val fond = fondsCase[cle] ?: continue
                    val cadre = lettresCase[cle]?.parent as? View ?: continue
                    tvRetour.postDelayed({
                        if (!isAdded || session !== partie) return@postDelayed
                        fond.setColor(vif)
                        cadre.scaleX = 0.8f
                        cadre.scaleY = 0.8f
                        cadre.animate()
                            .scaleX(1f).scaleY(1f)
                            .setInterpolator(OvershootInterpolator(2.5f))
                            .setDuration(280)
                            .start()
                    }, pas)
                    pas += 45L
                }
                pas += 120L
            }
            tvRetour.postDelayed(
                { if (isAdded && session === partie) rafraichir() },
                pas + 260L
            )
        }

        /**
         * Un retour haptique sur le verrouillage. `fort` (coup double, grille
         * terminée) rejoue l'impulsion deux fois de plus. Passe par
         * `performHapticFeedback`, qui ne demande aucune permission et suit le
         * réglage haptique du système ; rien à faire pour le désactiver.
         */
        private fun retourHaptique(fort: Boolean) {
            val v = rootView ?: return
            val effet = if (Build.VERSION.SDK_INT >= 30)
                HapticFeedbackConstants.CONFIRM
            else
                HapticFeedbackConstants.LONG_PRESS
            v.performHapticFeedback(effet)
            if (fort) {
                v.postDelayed({ v.performHapticFeedback(effet) }, 85)
                v.postDelayed({ v.performHapticFeedback(effet) }, 170)
            }
        }

        /**
         * La carte de récompense : fond plein de la couleur donnée, texte
         * blanc, et une entrée en scène (fondu + léger rebond) pour qu'on la
         * voie apparaître. `annoncer` reste pour les messages neutres, sur
         * fond blanc et sans animation.
         */
        private fun annoncerCarte(texte: String, couleurFond: Int) {
            tvRetour.animate().cancel()
            tvRetour.text = texte
            tvRetour.setTextColor(Color.WHITE)
            (tvRetour.background as? GradientDrawable)?.setColor(couleurFond)
            tvRetour.visibility = View.VISIBLE
            if (animationsReduites()) {
                tvRetour.alpha = 1f
                tvRetour.translationY = 0f
                tvRetour.scaleX = 1f
                tvRetour.scaleY = 1f
                return
            }
            tvRetour.alpha = 0f
            tvRetour.translationY = 14f
            tvRetour.scaleX = 0.96f
            tvRetour.scaleY = 0.96f
            tvRetour.animate()
                .alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setInterpolator(OvershootInterpolator(1.7f))
                .setDuration(300)
                .start()
        }

        private fun annoncerA11y(texte: String) {
            rootView?.announceForAccessibility(texte)
        }

        /** Vrai si l'utilisateur a coupé les animations système. */
        private fun animationsReduites(): Boolean = try {
            Settings.Global.getFloat(
                requireContext().contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (e: Exception) {
            false
        }

        /** Un petit rebond d'échelle, pour attirer l'œil sur un compteur qui bouge. */
        private fun pop(v: View) {
            v.animate().cancel()
            v.scaleX = 1f
            v.scaleY = 1f
            v.animate().scaleX(1.14f).scaleY(1.14f).setDuration(110)
                .withEndAction {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(170)
                        .setInterpolator(OvershootInterpolator(3f)).start()
                }.start()
        }

        /**
         * L'entrée d'une ligne fraîchement gagnée dans « Ce que vous avez
         * gagné » : elle glisse depuis la gauche et un fond vert s'éteint sur
         * elle, le temps qu'on la repère.
         */
        private fun animerEntreeLigne(v: TextView) {
            v.alpha = 0f
            v.translationX = -24f
            v.animate().alpha(1f).translationX(0f).setDuration(300)
                .setInterpolator(OvershootInterpolator(1.4f)).start()

            val surligne = Color.parseColor("#B2DFDB")
            val fond = GradientDrawable().apply {
                cornerRadius = 8f * resources.displayMetrics.density
                setColor(surligne)
            }
            v.background = fond
            v.setPadding(10, 6, 10, 6)
            ValueAnimator.ofArgb(surligne, Color.TRANSPARENT).apply {
                duration = 1100
                startDelay = 220
                addUpdateListener { fond.setColor(it.animatedValue as Int) }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        v.background = null
                        v.setPadding(0, 0, 0, 0)
                    }
                })
                start()
            }
        }

        /**
         * Les confettis de fin de grille : posés dans le cadre de contenu de
         * l'activité, au-dessus de tout, sans défilement ni décalage. Sautés
         * quand les animations système sont coupées.
         */
        private fun lancerConfettis() {
            if (animationsReduites()) return
            val hote = activity?.findViewById<ViewGroup>(android.R.id.content)
                ?: return
            enleverConfettis()
            val vue = ConfettiView(hote.context)
            vue.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            vue.isClickable = false
            confetti = vue
            hote.addView(vue)
            vue.demarrer { enleverConfettis() }
        }

        private fun enleverConfettis() {
            confetti?.let {
                it.stopper()
                (it.parent as? ViewGroup)?.removeView(it)
            }
            confetti = null
        }

        private fun annoncer(texte: String, couleur: Int) {
            tvRetour.animate().cancel()
            tvRetour.alpha = 1f
            tvRetour.translationY = 0f
            tvRetour.scaleX = 1f
            tvRetour.scaleY = 1f
            (tvRetour.background as? GradientDrawable)?.setColor(Color.WHITE)
            tvRetour.text = texte
            tvRetour.setTextColor(couleur)
            tvRetour.visibility = View.VISIBLE
        }

        private fun montrerLaSolution() {
            val partie = session ?: return
            partie.reveler()
            resolus.addAll(partie.grid.words.indices)
            rafraichir()
            annoncer(
                getString(R.string.sa_solution_affichee_cette_grille_ne),
                Color.parseColor("#757575")
            )
        }

        /** Repeint la grille, les pastilles et le compteur. */
        private fun rafraichir() {
            val partie = session ?: return
            val grille = partie.grid

            fun casesDe(emplacement: Int): List<Int> {
                val mot = grille.words[emplacement]
                return (0 until mot.length).map {
                    mot.rowAt(it) * grille.width + mot.colAt(it)
                }
            }

            val gagnees = grille.words.indices
                .filter { partie.verrouille(it) }.flatMap { casesDe(it) }.toSet()
            val fautives = grille.words.indices
                .filter { partie.fautif(it) }.flatMap { casesDe(it) }.toSet()
            // Les emplacements où le mot choisi pourrait aller. C'est ce qui
            // rend le geste « toucher un mot puis une case » lisible : sans
            // cela, le joueur vise à l'aveugle et le refus lui paraît
            // arbitraire.
            val possibles = if (partie.motChoisi >= 0)
                partie.emplacementsPossibles(partie.motChoisi)
                    .flatMap { casesDe(it) }.toSet()
            else emptySet()

            for (r in 0 until grille.height) {
                for (c in 0 until grille.width) {
                    val cle = r * grille.width + c
                    val fond = fondsCase[cle] ?: continue
                    val vue = lettresCase[cle] ?: continue

                    val lettre = partie.lettreAt(r, c)
                    vue.text = lettre?.toString() ?: ""

                    fond.setColor(
                        when {
                            cle in fautives -> fondFaux
                            cle in gagnees -> fondJuste
                            cle in possibles -> fondPossible
                            lettre != null -> fondPose
                            else -> fondCase
                        }
                    )
                    vue.setTextColor(
                        if (cle in fautives) couleurFausse
                        else Color.parseColor("#212121")
                    )
                }
            }

            chipsParMot.forEach { (index, chip) ->
                val pose = partie.estPose(index)
                val choisi = index == partie.motChoisi
                // Un mot gagné se distingue d'un mot seulement posé : le
                // premier est acquis et ne bougera plus, le second peut
                // encore être repris. Les deux étaient gris, donc rien ne
                // disait lesquels étaient encore en jeu.
                val gagne = partie.grid.words.indices.any {
                    partie.motDe(it) == index && partie.verrouille(it)
                }
                (chip.background as? GradientDrawable)?.apply {
                    setColor(
                        when {
                            gagne -> fondJuste
                            pose -> Color.parseColor("#EEEEEE")
                            choisi -> fondPossible
                            else -> Color.WHITE
                        }
                    )
                    setStroke(
                        ((if (choisi) 2f else 1f) * resources.displayMetrics.density).toInt(),
                        if (choisi) couleurNeutre else Color.parseColor("#D0D0D0")
                    )
                }
                chip.setTextColor(
                    when {
                        gagne -> couleurNeutre
                        pose -> Color.parseColor("#9E9E9E")
                        else -> Color.parseColor("#212121")
                    }
                )
                chip.paintFlags = if (pose) {
                    chip.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    chip.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }
            }

            tvProgres.text = getString(R.string.sa_mots, partie.motsJustes(), grille.words.size)
            rafraichirGagnes(partie)
        }

        /**
         * Reconstruit la liste des sens gagnés.
         *
         * L'ordre est celui des verrouillages, que [resolus] conserve puisque
         * `mutableSetOf` est un LinkedHashSet : l'ordre d'obtention raconte la
         * partie, là où l'ordre de la grille ne dit rien. C'est aussi pourquoi
         * l'ordre vit ici et non dans la partie, dont l'ensemble des gagnés est
         * un HashSet.
         *
         * La liste ne perd jamais une ligne : un mot gagné ne se retire plus
         * (voir `ChasseCroiseSession.retirer`), donc ce qui est versé est
         * acquis. Elle est tout de même reconstruite à chaque rafraîchissement
         * plutôt que tenue par ajouts, pour que l'affichage n'ait qu'une seule
         * source de vérité.
         */
        private fun rafraichirGagnes(partie: ChasseCroiseSession) {
            val ctx = context ?: return
            conteneurGagnes.removeAllViews()

            val n = resolus.size
            // INVISIBLE et non GONE : le bouton du carnet partage sa ligne, et
            // la ligne ne doit pas se replier quand le titre s'efface.
            titreGagnes.visibility = if (n == 0) View.INVISIBLE else View.VISIBLE
            // Le compteur vit dans le titre : c'est là que l'œil va quand la
            // liste grandit, et il n'ajoute aucune vue à la mise en page.
            titreGagnes.text = getString(R.string.sa_ce_que_vous_avez_gagne, n)

            var duNeuf = false
            resolus.forEach { index ->
                val mot = partie.grid.words[index]
                val nouveau = index !in gagnesAffiches
                if (nouveau) {
                    gagnesAffiches.add(index)
                    duNeuf = true
                }
                // « ✨ » signale une carte que le carnet n'avait jamais vue,
                // toutes parties confondues. Sans ce repère, la vingtième
                // rencontre de « Haus » se lit comme la première.
                val marque = if (index in cartesNeuves) "✨ " else ""
                val ligne = SpannableString("$marque${mot.canonical} : ${mot.clue}")
                ligne.setSpan(
                    StyleSpan(Typeface.BOLD), marque.length,
                    marque.length + mot.canonical.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                ligne.setSpan(
                    ForegroundColorSpan(couleurNeutre), marque.length,
                    marque.length + mot.canonical.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                val tv = TextView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = 7 }
                    text = ligne
                    textSize = 14f
                    setLineSpacing(0f, 1.15f)
                    setTextColor(Color.parseColor("#333333"))
                }
                conteneurGagnes.addView(tv)
                if (nouveau && !animationsReduites()) animerEntreeLigne(tv)
            }
            if (duNeuf && !animationsReduites()) pop(titreGagnes)
        }

        override fun onDestroyView() {
            super.onDestroyView()
            enleverConfettis()
            enleverPochette()
            fondsCase.clear()
            lettresCase.clear()
            chipsParMot.clear()
            // La partie, elle, reste : voir [Partie].
            rootView = null
        }

        /**
         * Les confettis de fin de grille.
         *
         * Un `ValueAnimator` fait tomber une quarantaine de rectangles pendant
         * ~1,8 s, chacun avec sa vitesse, sa dérive et sa rotation, en
         * s'effaçant sur la fin. Aucune dépendance, aucune image : `onDraw`
         * seul. La vue se retire d'elle-même à la fin (voir [demarrer]).
         */
        private class ConfettiView(context: Context) : View(context) {

            private class Bout(
                val x0: Float, val vx: Float,
                val vy: Float, val delai: Float,
                val rot0: Float, val vrot: Float,
                val cote: Float, val couleur: Int
            )

            private val bouts = ArrayList<Bout>()
            private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
            private var t = 0f
            private var anim: ValueAnimator? = null

            private val palette = intArrayOf(
                Color.parseColor("#00796B"), Color.parseColor("#4CAF50"),
                Color.parseColor("#80CBC4"), Color.parseColor("#FFB74D"),
                Color.parseColor("#A5D6A7"), Color.parseColor("#26A69A")
            )

            fun demarrer(surFin: () -> Unit) {
                val d = resources.displayMetrics.density
                val w = if (width > 0) width
                    else resources.displayMetrics.widthPixels
                val alea = java.util.Random()
                bouts.clear()
                repeat(42) {
                    bouts.add(
                        Bout(
                            x0 = alea.nextFloat() * w,
                            vx = (alea.nextFloat() - 0.5f) * 240f * d,
                            vy = (900f + alea.nextFloat() * 700f) * d,
                            delai = alea.nextFloat() * 0.35f,
                            rot0 = alea.nextFloat() * 360f,
                            vrot = (alea.nextFloat() - 0.5f) * 900f,
                            cote = (5f + alea.nextFloat() * 6f) * d,
                            couleur = palette[alea.nextInt(palette.size)]
                        )
                    )
                }
                anim?.cancel()
                anim = ValueAnimator.ofFloat(0f, 1.8f).apply {
                    duration = 1800
                    addUpdateListener { t = it.animatedValue as Float; invalidate() }
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            surFin()
                        }
                    })
                    start()
                }
            }

            fun stopper() {
                anim?.cancel()
                anim = null
            }

            override fun onDraw(canvas: Canvas) {
                val h = height.toFloat()
                for (b in bouts) {
                    val u = (t - b.delai).coerceAtLeast(0f)
                    if (u <= 0f) continue
                    val x = b.x0 + b.vx * u
                    val y = 0.5f * b.vy * u * u          // chute accélérée
                    if (y - b.cote > h) continue
                    pinceau.color = b.couleur
                    pinceau.alpha =
                        (255 * (1f - (t / 1.8f)).coerceIn(0f, 1f)).toInt()
                    canvas.save()
                    canvas.rotate(b.rot0 + b.vrot * u, x, y)
                    canvas.drawRect(
                        x - b.cote / 2, y - b.cote / 2,
                        x + b.cote / 2, y + b.cote / 2, pinceau
                    )
                    canvas.restore()
                }
            }
        }
    }

    // Fragment « Wierderbuch » : un champ de saisie et une liste de résultats.
    // C'est le seul onglet qui ne joue à rien — on y cherche un mot, dans un
    // sens ou dans l'autre, et on lit sa traduction.
    class DictionaryFragment : Fragment() {

        private var rootView: ScrollView? = null
        private lateinit var champRecherche: EditText

        /**
         * Le panneau de droite où s'ouvre la fiche, sur tablette en paysage
         * seulement ; ailleurs elle reste une fenêtre ancrée en bas.
         *
         * Côte à côte, on parcourt les résultats sans ouvrir et fermer une
         * fenêtre à chaque mot : c'est ce que le grand écran apporte à un
         * dictionnaire.
         */
        private var panneauFiche: FrameLayout? = null

        /** La fiche ouverte dans le panneau, pour la retrouver après une rotation. */
        class FicheOuverte : androidx.lifecycle.ViewModel() {
            var resultat: TranslationDictionary.Resultat? = null
        }

        private val ficheOuverte by lazy {
            androidx.lifecycle.ViewModelProvider(this)[FicheOuverte::class.java]
        }
        private lateinit var conteneurResultats: LinearLayout
        private lateinit var tvEtat: TextView

        // La recherche parcourt 20 000 entrées : à la vitesse de frappe, c'est
        // une dizaine de parcours par mot tapé. On attend 200 ms de silence
        // avant de chercher, ce qui ramène cela à un seul.
        private val delaiRecherche = Handler(Looper.getMainLooper())
        private var rechercheEnAttente: Runnable? = null

        override fun onCreateView(
            inflater: android.view.LayoutInflater,
            container: android.view.ViewGroup?,
            savedInstanceState: android.os.Bundle?
        ): View {
            val activity = requireActivity() as SettingsActivity

            val racine = ScrollView(activity).apply {
                setBackgroundColor(Color.parseColor("#F5F5F5"))
                isFillViewport = true
            }

            val colonne = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
            }

            colonne.addView(TextView(activity).apply {
                text = "📚 Wierderbuch"
                textSize = 22f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#1976D2"))
                setPadding(0, 0, 0, 8)
            })

            // Le chargement reste ici, seul le compteur s'en va : c'est lui qui
            // évite d'analyser 2,7 Mo de JSON dans la première recherche, où
            // l'attente se verrait entre la frappe et les résultats.
            TranslationDictionary.charger(activity)

            // Les exemples, eux, partent sur un fil de fond : la fiche est le
            // seul écran qui en montre, et les charger à son ouverture ferait
            // attendre 2,6 Mo d'analyse au moment précis où elle doit
            // apparaître. Le contexte de l'application, jamais le fragment :
            // le fil survit à l'onglet. Rien à synchroniser au retour — la
            // table est lue par un accès protégé, et la fiche qui la
            // demanderait trop tôt attend simplement la fin de l'analyse.
            val applicatif = activity.applicatifDansLaLangue()
            Thread {
                TranslationDictionary.chargerExemples(applicatif)
                TranslationDictionary.chargerArticles(applicatif)
            }.start()

            colonne.addView(TextView(activity).apply {
                text = getString(R.string.sa_tapez_un_mot_luxembourgeois_ou)
                textSize = 14f
                setTextColor(Color.parseColor("#666666"))
                setLineSpacing(0f, 1.2f)
                setPadding(0, 0, 0, 20)
            })

            champRecherche = EditText(activity).apply {
                // Un identifiant fixe : Android garde ainsi la requête à la
                // rotation, et la recherche repart d'elle-même.
                id = R.id.recherche_wierderbuch
                hint = getString(R.string.sa_haus_maison_kaz_chat)
                textSize = 18f
                // Couleurs explicites : sur fond blanc imposé, la couleur de
                // texte héritée du thème est elle-même claire, et le champ
                // paraissait vide alors qu'il contenait la requête.
                setTextColor(Color.parseColor("#1C1C1C"))
                setHintTextColor(Color.parseColor("#BBBBBB"))
                setSingleLine(true)
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                setPadding(24, 20, 24, 20)
                setBackgroundColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun afterTextChanged(s: android.text.Editable?) {
                        rechercheEnAttente?.let { delaiRecherche.removeCallbacks(it) }
                        val requete = s?.toString() ?: ""
                        val tache = Runnable { if (isAdded) afficherResultats(requete) }
                        rechercheEnAttente = tache
                        delaiRecherche.postDelayed(tache, 200)
                    }

                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                })
            }
            colonne.addView(champRecherche)

            tvEtat = TextView(activity).apply {
                textSize = 15f
                setTextColor(Color.parseColor("#999999"))
                setPadding(4, 20, 4, 8)
                setLineSpacing(0f, 1.25f)
            }
            colonne.addView(tvEtat)

            conteneurResultats = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
                setPadding(0, 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            colonne.addView(conteneurResultats)

            // La source est en CC0 : la citation n'est pas due, elle est rendue.
            // C'est aussi ce qui dit à l'utilisateur d'où sort la traduction
            // qu'il lit, et donc jusqu'où il peut lui faire confiance.
            colonne.addView(TextView(activity).apply {
                text = getString(R.string.sa_traductions_et_exemples_issus_du)
                textSize = 12f
                setTextColor(Color.parseColor("#AAAAAA"))
                setLineSpacing(0f, 1.2f)
                setPadding(4, 28, 4, 8)
            })

            racine.addView(colonne)
            rootView = racine

            afficherResultats("")
            if (!DeuxColonnes.actives(activity)) {
                panneauFiche = null
                return racine
            }

            // Tablette en paysage : la liste à gauche, la fiche à droite.
            val panneau = FrameLayout(activity).apply {
                setBackgroundColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
                )
            }
            panneauFiche = panneau
            val choisie = ficheOuverte.resultat
            if (choisie != null) montrerDansLePanneau(activity, choisie) else inviterAuChoix(activity)

            return LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(Color.parseColor("#F5F5F5"))
                addView(racine, LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
                ))
                addView(panneau)
            }
        }

        /** Le panneau vide : il dit à quoi il sert plutôt que de rester blanc. */
        private fun inviterAuChoix(activity: SettingsActivity) {
            val panneau = panneauFiche ?: return
            panneau.removeAllViews()
            panneau.addView(TextView(activity).apply {
                text = getString(R.string.wb_choisir_un_mot)
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#999999"))
                setLineSpacing(0f, 1.25f)
                setPadding(48, 48, 48, 48)
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }

        private fun montrerDansLePanneau(
            activity: SettingsActivity,
            resultat: TranslationDictionary.Resultat
        ) {
            val panneau = panneauFiche ?: return
            ficheOuverte.resultat = resultat
            panneau.removeAllViews()
            panneau.addView(contenuFiche(activity, resultat, null))
        }

        override fun onResume() {
            super.onResume()
            // Un mot demandé depuis l'accueil (le mot du jour) : le champ le
            // reçoit, la recherche part comme si on l'avait tapé.
            val activite = activity as? SettingsActivity ?: return
            activite.rechercheDemandee?.let { mot ->
                activite.rechercheDemandee = null
                champRecherche.setText(mot)
                champRecherche.setSelection(mot.length)
            }
        }

        /**
         * Affiche les résultats d'une requête, ou l'invite quand elle est vide.
         *
         * Le cas « rien trouvé » mérite une explication plutôt qu'un vide :
         * près de la moitié des formes du dictionnaire de saisie n'ont pas de
         * traduction, et ce sont massivement des noms propres. Sans ce message,
         * l'utilisateur qui cherche « Bettel » croit l'application cassée.
         */
        private fun afficherResultats(requete: String) {
            val activity = activity as? SettingsActivity ?: return
            conteneurResultats.removeAllViews()

            val nettoyee = TranslationDictionary.nettoyerRequete(requete)
            if (nettoyee.length < 2) {
                tvEtat.text = getString(R.string.sa_entrez_au_moins_deux_lettres)
                return
            }

            val resultats = TranslationDictionary.rechercher(activity, nettoyee)
            if (resultats.isEmpty()) {
                tvEtat.text = getString(R.string.sa_aucun_resultat_pour_les_noms, nettoyee)
                return
            }

            tvEtat.text = resources.getQuantityString(R.plurals.resultats, resultats.size, resultats.size)

            resultats.forEachIndexed { rang, resultat ->
                conteneurResultats.addView(ligneResultat(activity, resultat, rang))
            }
        }

        private fun ligneResultat(
            activity: SettingsActivity,
            resultat: TranslationDictionary.Resultat,
            rang: Int
        ): View = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 16, 20, 16)
            // Une ligne sur deux légèrement teintée : la liste peut compter
            // quarante entrées, et rien d'autre ne sépare une glose du mot
            // suivant.
            setBackgroundColor(
                if (rang % 2 == 0) Color.WHITE else Color.parseColor("#FAFAFA")
            )
            isClickable = true
            setOnClickListener { ouvrirFiche(activity, resultat) }
            // L'appui long garde la copie à un seul geste : c'est l'action
            // courante, et la faire passer par la fiche coûterait deux taps à
            // qui veut seulement coller un mot ailleurs.
            setOnLongClickListener { copierMot(activity, resultat.mot); true }

            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                addView(TextView(activity).apply {
                    text = resultat.mot
                    textSize = 19f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#1C1C1C"))
                })
                addView(TextView(activity).apply {
                    text = resultat.glose
                    textSize = 16f
                    setTextColor(Color.parseColor("#555555"))
                    setPadding(0, 4, 0, 0)
                })
            })

            // Le chevron de [createReferenceLink] plutôt qu'une puce d'action :
            // c'est déjà, ailleurs dans l'application, ce qui annonce qu'une
            // ligne ouvre quelque chose, et quarante chevrons se lisent comme
            // une colonne là où quarante puces bleues se lisaient comme du
            // bruit — en prenant la largeur des gloses à trois sens, qui sont
            // justement les plus utiles.
            addView(TextView(activity).apply {
                text = "›"
                textSize = 22f
                setTextColor(Color.parseColor("#BBBBBB"))
                setPadding(20, 0, 0, 0)
            })
        }

        /**
         * Fiche d'un mot : ses sens un par un, et les actions.
         *
         * La liste ne porte plus que du contenu. Une puce « lod.lu » par ligne
         * répétait quarante fois la même étiquette, et toute action ajoutée
         * ensuite — prononciation, favori — aurait ajouté une puce de plus.
         * Ici elles ont la place de porter un vrai libellé.
         *
         * La fiche a d'abord annoncé la provenance du mot — « mot du corpus,
         * 65 occurrences » contre « forme du LOD ». C'était une confidence de
         * pipeline : elle décrit d'où vient notre fichier, pas le mot que la
         * personne cherche, et personne n'ouvre un dictionnaire pour lire un
         * décompte d'occurrences. Retirée, avec la table de fréquences qui
         * n'existait que pour elle.
         */
        private fun ouvrirFiche(
            activity: SettingsActivity,
            resultat: TranslationDictionary.Resultat
        ) {
            if (panneauFiche != null) {
                montrerDansLePanneau(activity, resultat)
                return
            }
            // Une Dialog ordinaire ancrée en bas, et non un BottomSheetDialog :
            // Material 1.12 et 1.13 y appellent Window.setStatusBarColor et
            // setNavigationBarColor, obsolètes depuis Android 15, et la Play
            // Console le signale. Ce n'était qu'un usage sur toute l'appli. Le
            // contenu défile déjà dans un ScrollView, qui prend la hauteur de
            // l'écran s'il la dépasse : plus de position repliée à gérer.
            val dialogue = Dialog(activity)
            dialogue.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialogue.setContentView(contenuFiche(activity, resultat, dialogue))
            dialogue.window?.apply {
                setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setGravity(Gravity.BOTTOM)
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }
            dialogue.show()
        }

        private fun contenuFiche(
            activity: SettingsActivity,
            resultat: TranslationDictionary.Resultat,
            /** Null quand la fiche est dans le panneau : rien à fermer. */
            dialogue: Dialog?
        ): View {
            val colonne = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
                setPadding(40, 36, 40, 44)
            }

            colonne.addView(TextView(activity).apply {
                text = resultat.mot
                textSize = 30f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#1C1C1C"))
            })

            colonne.addView(titreSection(activity, getString(R.string.sa_en_francais), 30))

            // Le générateur assemble les acceptions avec « , » ; rien ne lui
            // interdit d'en produire une qui contienne elle-même une virgule.
            // Découper là est donc une heuristique : au pire une acception
            // s'affiche sur deux lignes, jamais aucune n'est perdue.
            resultat.glose.split(", ").filter { it.isNotBlank() }.forEach { sens ->
                colonne.addView(TextView(activity).apply {
                    text = "•  $sens"
                    textSize = 17f
                    setTextColor(Color.parseColor("#333333"))
                    setPadding(0, 0, 0, 8)
                    setLineSpacing(0f, 1.15f)
                })
            }

            // Les phrases du LOD, après le sens et avant la morphologie : elles
            // illustrent ce qu'on vient de lire. Une glose dit ce que le mot
            // veut dire, jamais comment il s'emploie — « Haus = maison » ne
            // fait pas deviner « ech ginn heem ». Le mot cherché est mis en
            // gras dans la phrase, comme le LOD le balise lui-même : c'est ce
            // qui fait lire une illustration plutôt qu'une phrase de plus.
            //
            // Un mot sur cinq n'en a pas — noms propres, formes que le ZLS n'a
            // pas illustrées. La section disparaît alors, plutôt que de poser
            // un titre sur du vide.
            //
            // Sous la phrase, sa traduction française quand le ZLS en a publié
            // une, et rien sinon : pas de traduction approchée, pas de mention
            // « traduction indisponible » qui ferait paraître deux fiches sur
            // trois inachevées.
            val exemples = TranslationDictionary.exemplesTraduits(activity, resultat)
            if (exemples.isNotEmpty()) {
                colonne.addView(titreSection(
                    activity,
                    resources.getQuantityString(R.plurals.fiche_exemples, exemples.size),
                    26
                ))
                // Chacune sur son fond, séparées d'un vrai intervalle : à dix
                // pixels l'une de l'autre et sur le blanc de la fiche, les deux
                // phrases se lisaient comme un seul paragraphe, et la seconde
                // paraissait continuer la première.
                exemples.forEachIndexed { rang, exemple ->
                    colonne.addView(TextView(activity).apply {
                        text = SpannableStringBuilder(phraseIllustree(exemple.phrase, resultat)).apply {
                            exemple.traduction?.let { francais ->
                                append("\n")
                                val debut = length
                                append(francais)
                                setSpan(RelativeSizeSpan(0.85f), debut, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                setSpan(
                                    ForegroundColorSpan(Color.parseColor("#777777")),
                                    debut, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            }
                        }
                        textSize = 16f
                        setTextColor(Color.parseColor("#333333"))
                        setLineSpacing(0f, 1.25f)
                        setPadding(24, 20, 24, 20)
                        background = GradientDrawable().apply {
                            cornerRadius = 12f
                            setColor(Color.parseColor("#F6F7F8"))
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            if (rang < exemples.size - 1) bottomMargin = 14
                        }
                    })
                }
            }

            // L'une sous l'autre, en pleine largeur : côte à côte, la seconde
            // n'avait la place que d'un nom de domaine, « lod.lu ↗ », qui
            // suppose de savoir déjà ce qu'est le LOD. Le libellé dit
            // maintenant où l'on va ; l'attribution, elle, reste en pied
            // d'onglet.
            // Les autres formes du même mot, après le sens : on vient chercher
            // ce que le mot veut dire, la morphologie est un second temps.
            // C'est aussi ce qui rend le regroupement lisible — la liste ne
            // montre plus « Forschett » et « Forschetten » l'un sous l'autre,
            // la fiche dit qu'ils sont le même mot.
            if (resultat.formes.isNotEmpty()) {
                colonne.addView(titreSection(activity, getString(R.string.sa_autres_formes), 26))
                colonne.addView(TextView(activity).apply {
                    // Toutes, désormais. Elles étaient plafonnées à dix pour que
                    // « sinn » et ses vingt-deux formes ne poussent pas les
                    // boutons hors de l'écran ; le « … » qui suivait annonçait
                    // qu'il en manquait douze sans donner aucun moyen de les
                    // voir. La feuille s'ouvrant maintenant déployée, le
                    // contenu défile au lieu d'être coupé.
                    text = resultat.formes.joinToString(" · ")
                    textSize = 16f
                    setTextColor(Color.parseColor("#555555"))
                    setLineSpacing(0f, 1.2f)
                })
            }

            colonne.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 30, 0, 0)

                addView(boutonFiche(
                    activity, getString(R.string.sa_copier_le_mot),
                    Color.parseColor("#1976D2"), Color.WHITE, null
                ) {
                    copierMot(activity, resultat.mot)
                    dialogue?.dismiss()
                }.apply {
                    (layoutParams as LinearLayout.LayoutParams).bottomMargin = 20
                })

                addView(boutonFiche(
                    activity, getString(R.string.sa_voir_sur_le_dictionnaire_officiel),
                    Color.WHITE, Color.parseColor("#2C7A8C"), Color.parseColor("#B9D6DD")
                ) {
                    ouvrirLod(activity, resultat.mot)
                    dialogue?.dismiss()
                })
            })

            return ScrollView(activity).apply {
                addView(colonne)
                // Affichage bord à bord (Android 15) : la fenêtre passe sous la
                // barre de navigation, le bas de la fiche doit la contourner.
                clipToPadding = false
                androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this) { vue, insets ->
                    val barres = insets.getInsets(
                        androidx.core.view.WindowInsetsCompat.Type.systemBars()
                    )
                    vue.setPadding(0, 0, 0, barres.bottom)
                    insets
                }
            }
        }

        /**
         * Intitulé d'une section de la fiche. Les trois se ressemblent au
         * pixel près ; seul l'espace au-dessus du premier diffère, la fiche
         * ouvrant sur le mot en 30sp.
         */
        private fun titreSection(
            activity: SettingsActivity,
            libelle: String,
            marge: Int
        ): TextView = TextView(activity).apply {
            text = libelle
            textSize = 11f
            letterSpacing = 0.12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#AAAAAA"))
            setPadding(0, marge, 0, 10)
        }

        /**
         * La phrase d'exemple, le mot cherché en gras.
         *
         * On met en gras toute forme de la famille : la phrase du LOD emploie
         * le mot fléchi (« déi Blus passt gutt bei deng blo **Aen** »), et
         * chercher la seule forme affichée n'en surlignerait presque jamais
         * aucune. Une flexion que le LOD n'a pas glosée n'est pas dans la
         * famille : la phrase s'affiche alors sans gras, ce qui reste lisible.
         */
        private fun phraseIllustree(
            phrase: String,
            resultat: TranslationDictionary.Resultat
        ): CharSequence {
            val formes = (resultat.formes + resultat.mot)
                .mapTo(HashSet()) { AccentTolerantMatcher.normalize(it) }
            val rendu = SpannableString(phrase)
            var depuis = 0
            for (mot in TranslationDictionary.decouperEnMots(phrase)) {
                val debut = phrase.indexOf(mot, depuis)
                if (debut < 0) continue
                depuis = debut + mot.length
                if (AccentTolerantMatcher.normalize(mot) in formes) {
                    rendu.setSpan(
                        StyleSpan(Typeface.BOLD), debut, depuis,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            return rendu
        }

        private fun boutonFiche(
            activity: SettingsActivity,
            libelle: String,
            fond: Int,
            encre: Int,
            bordure: Int?,
            action: () -> Unit
        ): TextView = TextView(activity).apply {
            text = libelle
            textSize = 15f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(encre)
            setPadding(20, 32, 20, 32)
            background = GradientDrawable().apply {
                cornerRadius = 14f
                setColor(fond)
                if (bordure != null) setStroke(3, bordure)
            }
            isClickable = true
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        /**
         * Copie le mot dans le presse-papiers.
         *
         * Le Toast se tait à partir d'Android 13 : le système affiche lui-même
         * une confirmation de copie, et les deux se superposaient.
         */
        private fun copierMot(activity: SettingsActivity, mot: String) {
            val presse = activity.getSystemService(Context.CLIPBOARD_SERVICE)
                    as? android.content.ClipboardManager ?: return
            presse.setPrimaryClip(ClipData.newPlainText("Wierderbuch", mot))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(activity, getString(R.string.sa_copie, mot), Toast.LENGTH_SHORT).show()
            }
        }

        /**
         * Ouvre la fiche LOD du mot dans le navigateur.
         *
         * **Par l'identifiant d'article, jamais par la recherche.** La route
         * `/sich/<langue>/<mot>` du LOD paraissait le lien naturel — c'est
         * celui que le site fabrique lui-même — mais elle ne fonctionne pas
         * quand on y arrive de l'extérieur : le composant qui la sert émet sa
         * requête sur un bus d'événements dans son `mounted()`, et à
         * l'ouverture à froid d'un onglet neuf l'écouteur n'est pas encore là.
         * La recherche se perd, et la page propose d'ajouter le mot au
         * dictionnaire — y compris pour « Haus ». Vérifié au navigateur le
         * 2026-09-03 ; c'est un défaut de leur côté, pas du nôtre.
         *
         * `/artikel/<id>` est rendue par leur serveur et arrive directement
         * sur l'article, ce qui vaut mieux qu'une liste de résultats de toute
         * façon. Les identifiants viennent de `luxemburgish_lod_ids.json`,
         * indexé par la forme que la fiche affiche.
         *
         * Le repli sur la recherche reste là pour les mots hors table — noms
         * propres, formes que l'index du LOD ne rattache à rien. Il ne mènera
         * à rien tant que leur défaut dure, mais c'est déjà ce que donnait
         * l'ancienne adresse.
         */
        private fun ouvrirLod(activity: SettingsActivity, mot: String) {
            val article = TranslationDictionary.articleLod(activity, mot)
            val url = if (article != null) LOD_ARTICLE + Uri.encode(article)
                      else LOD_RECHERCHE + Uri.encode(mot)
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                Log.e("DictionaryFragment", "Ouverture de lod.lu impossible", e)
                Toast.makeText(activity, getString(R.string.sa_impossible_ouvrir_lod_lu), Toast.LENGTH_SHORT).show()
            }
        }

        companion object {
            private const val LOD_ARTICLE = "https://lod.lu/artikel/"
            private const val LOD_RECHERCHE = "https://lod.lu/sich/lb/"
        }

        override fun onDestroyView() {
            super.onDestroyView()
            rechercheEnAttente?.let { delaiRecherche.removeCallbacks(it) }
            rechercheEnAttente = null
            rootView = null
            panneauFiche = null
        }
    }

    /**
     * Onglet « Spiller » : le choix du jeu, puis le jeu choisi.
     *
     * Ce fragment ne joue à rien lui-même. Il montre quatre cartes et, au tap,
     * installe le fragment du jeu dans son propre conteneur. Aucun pager
     * imbriqué : les jeux comportent des grilles qui se manipulent au doigt
     * (le glissé de Wuertsich, notamment), et un second ViewPager leur aurait
     * disputé chaque geste horizontal.
     *
     * Le retour au choix passe par [OnBackPressedCallback] plutôt que par
     * l'override d'`onBackPressed` de l'activité : le rappel n'est actif que
     * pendant qu'un jeu est ouvert, si bien que le bouton Retour continue de
     * quitter l'application partout ailleurs, sans que l'activité ait à savoir
     * ce que ses onglets contiennent.
     */
    class GamesFragment : Fragment() {

        private var rootView: LinearLayout? = null
        private var conteneurJeu: FrameLayout? = null
        private var barreRetour: LinearLayout? = null
        private var grilleChoix: View? = null

        /** La bannière du carnet, en tête du hub, remise à jour au retour. */
        private var tvCarnetTotal: TextView? = null
        private var tvCarnetDetail: TextView? = null
        private var tvCarnetRevision: TextView? = null

        private val retourAuChoix = object : androidx.activity.OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = fermerLeJeu()
        }

        /**
         * [nom] identifie le jeu (il est retenu pour « Rejouer à … ») et reste le
         * même dans toutes les langues : les sept jeux portent un nom
         * luxembourgeois. Seule la Boîte de Leitner a un [titre] traduit.
         */
        private data class Jeu(
            val emoji: String,
            val nom: String,
            @StringRes val resume: Int,
            val couleur: String,
            @StringRes val titre: Int? = null,
            val fabrique: () -> Fragment
        )

        private val jeux = listOf(
            Jeu("📚", "Boîte de Leitner", R.string.sa_revisez_vos_cartes_intervalle_regulier,
                "#8B4513", titre = R.string.jeu_leitner) { BoiteFragment() },
            Jeu("🎲", "Wuertsich", R.string.sa_retrouvez_les_mots_caches_dans,
                "#9C27B0") { WordSearchFragment() },
            Jeu("🔤", "Wuertmix", R.string.sa_remettez_les_lettres_dans_ordre,
                "#1976D2") { WordScrambleFragment() },
            Jeu("🟩", "Wuertriet", R.string.sa_devinez_le_mot_de_lettres,
                "#4CAF50") { WuertrietFragment() },
            Jeu("📝", "Wuertlück", R.string.sa_completez_la_phrase_laquelle_il,
                "#FF8C00") { ClozeFragment() },
            Jeu("🔢", "Zuelwuert", R.string.sa_ecrivez_en_lettres_le_resultat,
                "#00897B") { ZuelenFragment() },
            Jeu("🧩", "Kräizwuert", R.string.sa_ecrivez_les_mots_dans_la,
                "#C2185B") { CrosswordFragment() },
            Jeu("🔡", "Wuertplaz", R.string.sa_casez_les_mots_donnes_dans,
                "#00796B") { ChasseCroiseFragment() }
        )

        override fun onCreateView(
            inflater: android.view.LayoutInflater,
            container: android.view.ViewGroup?,
            savedInstanceState: android.os.Bundle?
        ): View {
            val activity = requireActivity() as SettingsActivity

            val colonne = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#F5F5F5"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }

            barreRetour = construireBarreRetour(activity).also {
                it.visibility = View.GONE
                colonne.addView(it)
            }

            grilleChoix = construireGrilleChoix(activity).also { colonne.addView(it) }

            conteneurJeu = FrameLayout(activity).apply {
                id = R.id.conteneur_jeu
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }
            colonne.addView(conteneurJeu)

            requireActivity().onBackPressedDispatcher
                .addCallback(viewLifecycleOwner, retourAuChoix)

            rootView = colonne
            // Après une rotation, on rouvre le jeu qui était ouvert, et le
            // même : le système l'a restauré dans son cadre, avec sa partie.
            activity.jeuOuvert.takeIf { it in jeux.indices }?.let { i ->
                val restaure = childFragmentManager.findFragmentById(R.id.conteneur_jeu)
                if (restaure != null) ouvrirLeJeu(jeux[i], restaure) else ouvrirLeJeu(jeux[i])
            }
            return colonne
        }

        private fun construireBarreRetour(activity: SettingsActivity) =
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.WHITE)
                setPadding(16, 12, 16, 12)
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                addView(TextView(activity).apply {
                    text = getString(R.string.sa_tous_les_jeux)
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#1976D2"))
                })
                setOnClickListener { fermerLeJeu() }
            }

        private fun construireGrilleChoix(activity: SettingsActivity): View {
            val colonne = LinearLayout(activity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.VERTICAL
                setPadding(20, 24, 20, 24)
            }

            colonne.addView(TextView(activity).apply {
                text = "🎮 Spiller"
                textSize = 22f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#1C1C1C"))
                setPadding(4, 0, 4, 6)
            })
            colonne.addView(TextView(activity).apply {
                text = getString(R.string.sa_huit_facons_de_travailler_son)
                textSize = 14f
                setTextColor(Color.parseColor("#666666"))
                setLineSpacing(0f, 1.25f)
                setPadding(4, 0, 4, 20)
            })

            colonne.addView(banniereCarnet(activity))

            // Deux cartes par ligne : une carte pleine largeur par jeu aurait
            // poussé les derniers hors de l'écran, là où on ne les découvre
            // plus. Un nombre impair de jeux laisse le dernier occuper toute
            // la ligne — c'est voulu, il est ainsi le plus visible.
            // Quatre sur une tablette couchée : les huit jeux y tiennent alors
            // sur un écran, au lieu de cartes de 50 cm de large qu'il fallait
            // faire défiler.
            val parLigne = if (DeuxColonnes.actives(activity)) 4 else 2
            jeux.chunked(parLigne).forEach { paire ->
                colonne.addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = 16 }
                    paire.forEachIndexed { rang, jeu ->
                        addView(carteJeu(activity, jeu, marginDroite = rang < paire.size - 1))
                    }
                })
            }

            // Le choix défile depuis la septième carte. Six tenaient dans un
            // écran de téléphone, si bien que le hub n'avait jamais eu besoin
            // de défiler ; la septième tombait sous le bord, et rien ne le
            // signalait : la carte existait, elle était simplement
            // inatteignable. Le poids fait prendre à la vue la hauteur restante
            // sous la barre de retour, et jamais plus, sinon les cartes se
            // centrent au lieu de commencer en haut.
            return ScrollView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
                addView(colonne)
            }
        }

        /**
         * La bannière du carnet, au-dessus des sept jeux.
         *
         * Le carnet était une pastille au fond d'un seul jeu : pour le
         * découvrir il fallait avoir choisi Wuertplaz, puis avoir fini une
         * grille. C'était l'inverse de ce qu'il est — la chose qui relie les
         * sept parties entre elles, et la seule qui reste quand la partie est
         * finie. Il est donc en tête du hub, pleine largeur, au-dessus des jeux
         * plutôt qu'à côté d'eux.
         *
         * Elle n'affiche que ce qui se lit **sans toucher aux actifs** : le
         * total et les jeux représentés sortent des préférences. Les raretés
         * demanderaient le balayage de `luxemburgish_dict.json` (1,27 Mo), qui
         * n'a rien à faire sur le fil principal à l'ouverture d'un onglet —
         * elles sont dans le carnet lui-même, à une touche d'ici.
         */
        private fun banniereCarnet(activity: SettingsActivity): View {
            val d = resources.displayMetrics.density
            fun dp(v: Float) = (v * d).toInt()
            val accent = Carnet.COULEUR

            return LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(18f) }
                background = GradientDrawable().apply {
                    cornerRadius = 16f * d
                    setColor(accent)
                }

                addView(TextView(activity).apply {
                    text = "📔"
                    textSize = 34f
                    setPadding(0, 0, dp(14f), 0)
                })

                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                    addView(TextView(activity).apply {
                        text = "Mäi Carnet"
                        textSize = 18f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.WHITE)
                    })
                    tvCarnetTotal = TextView(activity).apply {
                        textSize = 13f
                        setTextColor(0xFFE8E0FF.toInt())
                        setLineSpacing(0f, 1.2f)
                    }
                    addView(tvCarnetTotal)
                    // Ce qui est dû aujourd'hui, sous le total. Se lit dans les
                    // préférences comme le reste de la bannière : la règle
                    // tient, rien ici ne touche aux actifs.
                    tvCarnetRevision = TextView(activity).apply {
                        textSize = 13f
                        setTypeface(null, Typeface.BOLD)
                        setPadding(0, dp(4f), 0, 0)
                    }
                    addView(tvCarnetRevision)
                    tvCarnetDetail = TextView(activity).apply {
                        textSize = 13f
                        setPadding(0, dp(4f), 0, 0)
                    }
                    addView(tvCarnetDetail)
                })

                addView(TextView(activity).apply {
                    text = getString(R.string.sa_ouvrir)
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(accent)
                    setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
                    background = GradientDrawable().apply {
                        cornerRadius = 20f * d
                        setColor(Color.WHITE)
                    }
                })

                isClickable = true
                setOnClickListener {
                    CarnetFragment().show(parentFragmentManager, "carnet")
                }
                majBanniereCarnet()
            }
        }

        /**
         * Remet la bannière à jour.
         *
         * Appelée à la construction et à chaque retour sur le hub : une partie
         * qui vient de se finir a presque toujours changé le total, et une
         * bannière figée ferait mentir la seule chose qu'elle affiche.
         */
        private fun majBanniereCarnet() {
            val ctx = context ?: return
            val total = Carnet.taille(ctx)
            tvCarnetTotal?.text = when (total) {
                0 -> getString(R.string.sa_les_mots_que_vous_gagnez)
                else -> resources.getQuantityString(R.plurals.cartes_collectees, total, total)
            }
            // Le décompte est celui de la file, donc plafonné : la bannière
            // annonce ce que la prochaine session contient, jamais l'arriéré.
            // Promettre « 213 cartes à revoir » est la façon de n'en faire
            // réviser aucune.
            val dues = Carnet.aRevoir(ctx)
            tvCarnetRevision?.apply {
                if (dues == 0) {
                    visibility = View.GONE
                } else {
                    visibility = View.VISIBLE
                    text = resources.getQuantityString(R.plurals.cartes_a_revoir_aujourdhui, dues, dues)
                    setTextColor(Color.WHITE)
                }
            }
            val jeux = Carnet.jeuxRepresentes(ctx)
            tvCarnetDetail?.apply {
                if (jeux.isEmpty()) {
                    text = getString(R.string.sa_les_sept_jeux_versent)
                    setTextColor(0xFFCFC2F0.toInt())
                } else {
                    // Les emojis des jeux qui ont déjà donné une carte : la
                    // collection se lit d'un coup d'œil comme une carte de
                    // progression, sans compter ni classer.
                    text = jeux.joinToString(" ") { it.emoji } +
                        getString(R.string.sa_jeux, jeux.size, JeuCarte.JEUX.size)
                    setTextColor(0xFFE8E0FF.toInt())
                }
            }
        }

        override fun onResume() {
            super.onResume()
            majBanniereCarnet()
            // Un jeu demandé depuis l'accueil (« Reprendre », « Réviser »)
            val activite = activity as? SettingsActivity ?: return
            activite.jeuDemande?.let { nom ->
                val revision = activite.revisionDemandee
                activite.jeuDemande = null
                activite.revisionDemandee = false
                jeux.firstOrNull { it.nom == nom }?.let { jeu ->
                    if (revision && nom == JEU_LEITNER) ouvrirLeJeu(jeu, BoiteFragment.pourRevision())
                    else ouvrirLeJeu(jeu)
                }
            }
        }

        private fun carteJeu(activity: SettingsActivity, jeu: Jeu, marginDroite: Boolean) =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(16, 24, 16, 24)
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = 16f * resources.displayMetrics.density
                    setStroke(
                        (1.5f * resources.displayMetrics.density).toInt(),
                        activity.avecOpacite(jeu.couleur, 0x55)
                    )
                }
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                ).apply { if (marginDroite) rightMargin = 16 }

                addView(TextView(activity).apply {
                    text = jeu.emoji
                    textSize = 40f
                    gravity = Gravity.CENTER
                })
                addView(TextView(activity).apply {
                    text = jeu.titre?.let { activity.getString(it) } ?: jeu.nom
                    textSize = 17f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor(jeu.couleur))
                    setPadding(0, 8, 0, 4)
                })
                addView(TextView(activity).apply {
                    setText(jeu.resume)
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#777777"))
                    setLineSpacing(0f, 1.2f)
                })

                isClickable = true
                setOnClickListener { ouvrirLeJeu(jeu) }
            }

        private fun ouvrirLeJeu(jeu: Jeu, fragment: Fragment = jeu.fabrique()) {
            val conteneur = conteneurJeu ?: return
            (activity as? SettingsActivity)?.jeuOuvert = jeux.indexOf(jeu)
            // La Boîte de Leitner a sa propre carte sur l'accueil
            if (jeu.nom != JEU_LEITNER) (activity as? SettingsActivity)?.retenirDernierJeu(jeu.emoji, jeu.nom)
            if (!fragment.isAdded) {
                childFragmentManager.beginTransaction().apply {
                    // Un seul jeu à la fois dans le cadre.
                    childFragmentManager.fragments.forEach { remove(it) }
                    add(conteneur.id, fragment)
                }.commit()
            }
            grilleChoix?.visibility = View.GONE
            conteneur.visibility = View.VISIBLE
            barreRetour?.visibility = View.VISIBLE
            retourAuChoix.isEnabled = true
        }

        private fun fermerLeJeu() {
            val conteneur = conteneurJeu ?: return
            (activity as? SettingsActivity)?.jeuOuvert = -1
            // Le hub redevient visible sans repasser par onResume : la
            // bannière se remettrait à jour au prochain onglet, c'est-à-dire
            // trop tard pour la partie qu'on vient de finir.
            majBanniereCarnet()
            childFragmentManager.findFragmentById(conteneur.id)?.let {
                childFragmentManager.beginTransaction().remove(it).commit()
            }
            conteneur.visibility = View.GONE
            barreRetour?.visibility = View.GONE
            grilleChoix?.visibility = View.VISIBLE
            retourAuChoix.isEnabled = false
        }

        override fun onDestroyView() {
            super.onDestroyView()
            rootView = null
            conteneurJeu = null
            barreRetour = null
            grilleChoix = null
            tvCarnetTotal = null
            tvCarnetDetail = null
            tvCarnetRevision = null
        }
    }

    /**
     * Enveloppe plein écran pour les pages de référence — Guide, À Propos —
     * sorties de la barre d'onglets.
     *
     * Un [DialogFragment] plutôt qu'une Activity : les deux pages existent déjà
     * sous forme de Fragment, et les héberger ici évite deux déclarations de
     * manifeste et deux cycles de vie de plus pour un contenu qu'on ouvre et
     * qu'on referme.
     */
    class SheetFragment : androidx.fragment.app.DialogFragment() {

        companion object {
            private const val ARG_PAGE = "page"
            const val PAGE_GUIDE = "guide"
            const val PAGE_A_PROPOS = "a_propos"
            const val PAGE_ACTUALITES = "actualites"

            fun pour(page: String) = SheetFragment().apply {
                arguments = android.os.Bundle().apply { putString(ARG_PAGE, page) }
            }
        }

        override fun onCreate(savedInstanceState: android.os.Bundle?) {
            super.onCreate(savedInstanceState)
            setStyle(STYLE_NORMAL, android.R.style.Theme_DeviceDefault_Light_NoActionBar)
        }

        override fun onCreateView(
            inflater: android.view.LayoutInflater,
            container: android.view.ViewGroup?,
            savedInstanceState: android.os.Bundle?
        ): View {
            val activity = requireActivity() as SettingsActivity
            val page = arguments?.getString(ARG_PAGE) ?: PAGE_GUIDE

            val colonne = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
            }

            colonne.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.parseColor("#2196F3"))
                setPadding(16, 14, 16, 14)
                addView(TextView(activity).apply {
                    text = when (page) {
                        PAGE_GUIDE -> getString(R.string.sa_guide)
                        PAGE_ACTUALITES -> getString(R.string.act_titre_page)
                        else -> getString(R.string.sa_propos)
                    }
                    textSize = 18f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                })
                addView(TextView(activity).apply {
                    text = "✕"
                    textSize = 22f
                    setTextColor(Color.WHITE)
                    setPadding(20, 0, 8, 0)
                    isClickable = true
                    setOnClickListener { dismiss() }
                })
            })

            val hote = FrameLayout(activity).apply {
                id = View.generateViewId()
                // Le fond des deux pages : en paysage, la bande de l'encoche
                // est ce fond-là, et une bande blanche longeait le gris.
                setBackgroundColor(Color.parseColor("#F5F5F5"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }
            colonne.addView(hote)
            // Fenêtre plein écran : bord à bord comme l'activité sous Android 15.
            BordABord.appliquer(
                colonne, haut = colonne.getChildAt(0),
                lateraux = { listOf(colonne.getChildAt(0), colonne.getChildAt(1)) }
            )

            if (savedInstanceState == null) {
                childFragmentManager.beginTransaction()
                    .replace(
                        hote.id,
                        when (page) {
                            PAGE_GUIDE -> GuideFragment()
                            PAGE_ACTUALITES -> com.example.kreyolkeyboard.actualites.ActualitesFragment()
                            else -> AboutFragment()
                        }
                    )
                    .commit()
            }

            return colonne
        }

        override fun onStart() {
            super.onStart()
            // Sans cela le dialogue s'ajuste à son contenu et laisse le fond de
            // l'activité visible sur les bords : ces deux pages sont de la
            // lecture longue, elles méritent tout l'écran.
            dialog?.window?.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }
}

/**
 * Message bref ancré en haut de l'écran, à la place de la Snackbar de Material.
 *
 * La bibliothèque Material a été retirée en 27.0.0 : même inutilisés, ses
 * composants restaient dans le dex (R8 garde `MaterialDatePicker` par un layout
 * interne) et y laissaient `Window.setStatusBarColor` / `setNavigationBarColor`,
 * obsolètes depuis Android 15, que la Play Console signale. Ce bandeau est une
 * simple vue posée dans le contenu de l'activité, comme l'était la Snackbar.
 */
internal fun bandeauEnHaut(ancre: View, message: String, longue: Boolean) {
    val racine = ancre.rootView.findViewById<ViewGroup>(android.R.id.content) as? FrameLayout ?: return
    val densite = ancre.resources.displayMetrics.density
    val marge = (8 * densite).toInt()
    val bandeau = TextView(ancre.context).apply {
        text = message
        textSize = 14f
        setTextColor(Color.WHITE)
        setPadding((16 * densite).toInt(), (14 * densite).toInt(), (16 * densite).toInt(), (14 * densite).toInt())
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#323232"))
            cornerRadius = 4 * densite
        }
        elevation = 6 * densite
        alpha = 0f
    }
    // Bord à bord (Android 15+) : le contenu commence sous la barre d'état, le
    // bandeau doit s'en écarter pour ne pas recouvrir l'heure. Les encarts de la
    // fenêtre comptent la barre d'état même quand le système a déjà écarté le
    // contenu (avant Android 15), d'où la soustraction de sa position réelle.
    val barres = ViewCompat.getRootWindowInsets(ancre)?.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    )
    val position = IntArray(2).also { racine.getLocationInWindow(it) }
    racine.addView(bandeau, FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        Gravity.TOP
    ).apply {
        setMargins(
            marge + maxOf(0, (barres?.left ?: 0) - position[0]),
            marge + maxOf(0, (barres?.top ?: 0) - position[1]),
            marge + maxOf(0, (barres?.right ?: 0) - (racine.rootView.width - position[0] - racine.width)),
            marge
        )
    })
    bandeau.animate().alpha(1f).setDuration(150).start()
    bandeau.postDelayed({
        bandeau.animate().alpha(0f).setDuration(150).withEndAction {
            racine.removeView(bandeau)
        }.start()
    }, if (longue) 2750L else 1500L)
}
