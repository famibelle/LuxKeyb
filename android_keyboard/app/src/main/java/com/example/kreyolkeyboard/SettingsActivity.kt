package com.example.kreyolkeyboard

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlin.random.Random

class SettingsActivity : AppCompatActivity() {
    private var currentTab = 0 // 0 = home, 1 = stats
    private lateinit var viewPager: ViewPager2
    private lateinit var tabBar: LinearLayout
    
    // 🔧 FIX CRITIQUE: Scope lié au lifecycle de l'activité
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    companion object {
        // Système de cache ultra-léger pour les modifications
        private val pendingUpdates = ConcurrentHashMap<String, Int>(16, 0.75f, 1)
        private var lastSaveTime = 0L
        private const val SAVE_INTERVAL_MS = 30000L // 30 secondes
        private const val MAX_PENDING_UPDATES = 50 // Limite pour éviter l'accumulation
        
        private var saveExecutor: ScheduledExecutorService? = null
        
        // Fonction statique pour mettre à jour l'usage d'un mot (appelée depuis le clavier)
        @JvmStatic
        fun updateWordUsage(context: Context, word: String) {
            // Filtrer les mots trop courts ou invalides
            if (word.length < 2 || word.isBlank()) return
            
            // Incrémenter dans le cache (thread-safe)
            pendingUpdates.merge(word.lowercase().trim(), 1) { old, new -> old + new }
            
            // Si trop d'updates en attente, forcer une sauvegarde
            if (pendingUpdates.size >= MAX_PENDING_UPDATES) {
                flushPendingUpdates(context)
            }
            
            // Programmer une sauvegarde différée si pas déjà programmée
            scheduleDelayedSave(context)
        }
        
        // Sauvegarde différée pour optimiser les I/O
        private fun scheduleDelayedSave(context: Context) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastSaveTime < SAVE_INTERVAL_MS) return
            
            if (saveExecutor == null) {
                saveExecutor = Executors.newSingleThreadScheduledExecutor()
            }
            
            saveExecutor?.schedule({
                flushPendingUpdates(context)
            }, SAVE_INTERVAL_MS, TimeUnit.MILLISECONDS)
        }
        
        // Vider le cache en mémoire vers le fichier
        @JvmStatic
        fun flushPendingUpdates(context: Context, scope: CoroutineScope? = null) {
            if (pendingUpdates.isEmpty()) return
            
            // Copie atomique du cache pour libérer rapidement la mémoire
            val updatesToSave = HashMap<String, Int>(pendingUpdates)
            pendingUpdates.clear()
            lastSaveTime = System.currentTimeMillis()
            
            // 🔧 FIX CRITIQUE: Utiliser le scope fourni ou créer un scope avec SupervisorJob
            // Cela évite les JobCancellationException et fuites mémoire
            val executionScope = scope ?: CoroutineScope(Dispatchers.IO + SupervisorJob())
            
            executionScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        saveUpdatesToFile(context, updatesToSave)
                    }
                } catch (e: CancellationException) {
                    Log.d("SettingsActivity", "💾 Sauvegarde annulée, rollback des updates")
                    // En cas d'annulation, remettre les updates dans le cache
                    updatesToSave.forEach { (word, count) ->
                        pendingUpdates.merge(word, count) { old, new -> old + new }
                    }
                    throw e // Important: re-throw CancellationException
                } catch (e: Exception) {
                    Log.e("SettingsActivity", "❌ Erreur sauvegarde: ${e.message}")
                    // En cas d'erreur, remettre les updates dans le cache
                    updatesToSave.forEach { (word, count) ->
                        pendingUpdates.merge(word, count) { old, new -> old + new }
                    }
                }
            }
        }
        
        // Sauvegarde optimisée par lecture partielle
        private suspend fun saveUpdatesToFile(context: Context, updates: Map<String, Int>) {
            val usageFile = File(context.filesDir, "luxemburgish_dict_with_usage.json")
            
            if (!usageFile.exists()) {
                // Créer le fichier s'il n'existe pas
                createInitialUsageFile(context)
            }
            
            // Lecture streaming pour économiser la mémoire
            val existingData = try {
                usageFile.bufferedReader().use { reader ->
                    val sb = StringBuilder(8192) // Buffer fixe
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line)
                    }
                    JSONObject(sb.toString())
                }
            } catch (e: Exception) {
                Log.w("SettingsActivity", "Fichier corrompu, recréation")
                createInitialUsageFile(context)
                JSONObject()
            }
            
            // Appliquer seulement les modifications nécessaires
            var hasChanges = false
            updates.forEach { (word, incrementCount) ->
                if (existingData.has(word)) {
                    val currentCount = existingData.optInt(word, 0)
                    existingData.put(word, currentCount + incrementCount)
                    hasChanges = true
                } else {
                    // Nouveau mot, l'ajouter seulement s'il est dans le dictionnaire
                    if (isWordInDictionary(context, word)) {
                        existingData.put(word, incrementCount)
                        hasChanges = true
                    }
                }
            }
            
            // Sauvegarder seulement si des changements ont été faits
            if (hasChanges) {
                // Écriture atomique pour éviter la corruption
                val tempFile = File(context.filesDir, "luxemburgish_dict_with_usage.json.tmp")
                tempFile.bufferedWriter().use { writer ->
                    writer.write(existingData.toString())
                }
                tempFile.renameTo(usageFile)
                
                val motsSauvegardes = updates.map { "${it.key}(+${it.value})" }.joinToString(", ")
                Log.d("SettingsActivity", "Sauvegardé ${updates.size} mots: $motsSauvegardes")
            }
        }
        
        // Vérification rapide si un mot existe dans le dictionnaire
        private fun isWordInDictionary(context: Context, word: String): Boolean {
            return try {
                context.assets.open("luxemburgish_dict.json").bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (line!!.contains("\"$word\"", ignoreCase = true)) {
                            return true
                        }
                    }
                }
                false
            } catch (e: Exception) {
                false
            }
        }
        
        // Création optimisée du fichier initial
        private fun createInitialUsageFile(context: Context) {
            val usageFile = File(context.filesDir, "luxemburgish_dict_with_usage.json")
            
            // Créer un fichier complètement vide sans aucune donnée de démonstration
            val emptyUsageObject = JSONObject()
            usageFile.writeText(emptyUsageObject.toString())
        }
        
        // Nettoyage des ressources
        @JvmStatic
        fun cleanup() {
            saveExecutor?.shutdown()
            saveExecutor = null
            pendingUpdates.clear()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Restaurer l'onglet actif si l'activité a été recréée
        currentTab = savedInstanceState?.getInt("currentTab", 0) ?: 0
        
        // Masquer la barre d'action (bandeau noir)
        supportActionBar?.hide()
        
        Log.d("SettingsActivity", "Création de l'activité principale Lëtzebuergesch")
        
        // Layout principal vertical : Titre, Tabs en haut, puis ViewPager
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }
        
        // En-tête principal avec le titre de l'app
        val appHeader = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 20, 16, 16)
            setBackgroundColor(Color.parseColor("#0080FF"))
        }
        
        val appTitle = TextView(this).apply {
            text = "Lëtzebuergesch Clavier"
            textSize = 22f
            setTextColor(Color.parseColor("#F8F8FF"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        
        appHeader.addView(appTitle)
        
        // Créer la barre d'onglets horizontale
        tabBar = createTabBar()
        
        // ViewPager2 pour le contenu avec navigation swipe
        viewPager = ViewPager2(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            adapter = SettingsPagerAdapter(this@SettingsActivity)
            
            // Callback pour synchroniser avec la barre d'onglets
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    currentTab = position
                    updateTabBar()
                }
            })
        }
        
        mainLayout.addView(appHeader)
        mainLayout.addView(tabBar)
        mainLayout.addView(viewPager)
        
        setContentView(mainLayout)
        
        // Restaurer l'onglet précédent après la création de l'interface
        viewPager.setCurrentItem(currentTab, false)
        
        Log.d("SettingsActivity", "Interface avec tabs en haut créée avec succès")
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Sauvegarder l'onglet actif avant que l'activité soit recréée
        outState.putInt("currentTab", currentTab)
        Log.d("SettingsActivity", "💾 Sauvegarde de l'onglet actif: $currentTab")
    }
    
    override fun onDestroy() {
        // 🔧 FIX CRITIQUE: Sauvegarder avec le scope de l'activité avant annulation
        flushPendingUpdates(this, activityScope)
        
        // 🔧 FIX CRITIQUE: Annuler toutes les coroutines de l'activité
        activityScope.cancel()
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
    
    private fun createTabBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                140 // Hauteur augmentée pour afficher les emojis complets sans coupure
            )
            setBackgroundColor(Color.WHITE)
            elevation = 4f // Ombre légère pour séparer du contenu
            
            // Container pour les onglets
            val tabContainer = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f // Prend le reste de la hauteur
                )
                gravity = Gravity.CENTER
            }
            
            // Tab Accueil
            val homeTab = createTab(0, "🏠", "Accueil")
            tabContainer.addView(homeTab)
            Log.d("SettingsActivity", "Onglet Accueil créé et ajouté")
            
            // Tab Statistiques  
            val statsTab = createTab(1, "📊", "Mäi Lëtzebuergesch")
            tabContainer.addView(statsTab)
            Log.d("SettingsActivity", "Onglet Statistiques créé et ajouté")
            
            // Ligne de séparation en bas (fine)
            val separator = View(this@SettingsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    2
                )
                setBackgroundColor(Color.parseColor("#E0E0E0"))
            }
            
            addView(tabContainer)
            addView(separator)
        }
    }
    
    private fun createTab(tabIndex: Int, emoji: String, label: String): LinearLayout {
        Log.d("SettingsActivity", "Création onglet $tabIndex: $emoji $label")
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 12, 24, 12) // Padding vertical augmenté
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
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
                // Conversion pixels en DP pour meilleur affichage
                val density = resources.displayMetrics.density
                minHeight = (60 * density).toInt() // 60dp en pixels
            }
            
            // Label du tab
            val labelView = TextView(this@SettingsActivity).apply {
                text = label
                textSize = 10f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 2)
                setTextColor(
                    if (tabIndex == currentTab) 
                        Color.parseColor("#FF8C00") 
                    else 
                        Color.GRAY
                )
                setTypeface(null, if (tabIndex == currentTab) Typeface.BOLD else Typeface.NORMAL)
            }
            
            addView(emojiView)
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
                viewPager.currentItem = tabIndex
            }
        }
    }
    
    private fun updateTabBar() {
        tabBar.removeAllViews()
        
        // Container pour les onglets
        val tabContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f // Prend le reste de la hauteur
            )
            gravity = Gravity.CENTER
        }
        
        // Tabs
        tabContainer.addView(createTab(0, "🏠", "Accueil"))
        tabContainer.addView(createTab(1, "🇱🇺", "Mäi Lëtzebuergesch"))
        
        // Ligne de séparation en bas
        val separator = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2
            )
            setBackgroundColor(Color.parseColor("#E0E0E0"))
        }
        
        tabBar.addView(tabContainer)
        tabBar.addView(separator)
    }
    

    
    fun createHomeContent(): LinearLayout {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 32)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }
        
        // En-tête compact avec logo uniquement
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 24, 16, 24)
            setBackgroundColor(Color.parseColor("#0080FF"))
        }
        
        val logoImage = ImageView(this).apply {
            setImageResource(R.drawable.logo_potomitan)
            layoutParams = LinearLayout.LayoutParams(180, 60)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        
        headerLayout.addView(logoImage)
        
        // Description principale
        val descriptionCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.WHITE)
        }
        
        val missionTitle = TextView(this).apply {
            text = "🌟 Notre Mission"
            textSize = 20f
            setTextColor(Color.parseColor("#0080FF"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        
        val missionText = TextView(this).apply {
            text = "Ce clavier a été spécialement conçu pour préserver et promouvoir le "Lëtzebuergesch". Il met à disposition de tous un outil moderne pour écrire dans notre belle langue luxembourgeoise avec :\n\n" +
                    "💡 Suggestions de mots en Lëtzebuergesch\n" +
                    "🔡 Diacritiques intégrés dans le clavier\n" +
                    "🇱🇺 Design aux couleurs du Luxembourg\n"
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
            setLineSpacing(0f, 1.2f)
        }
        
        descriptionCard.addView(missionTitle)
        descriptionCard.addView(missionText)
        
        // Instructions d'installation
        val installCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#E8F4FD"))
        }
        
        val installTitle = TextView(this).apply {
            text = "📱 Comment activer le clavier ?"
            textSize = 18f
            setTextColor(Color.parseColor("#0080FF"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }
        
        val installSteps = TextView(this).apply {
            text = "1️⃣ Appuyez sur 'Activer le clavier' ci-dessous\n" +
                    "2️⃣ Dans les paramètres, activez 'Lëtzebuergesch Clavier'\n" +
                    "3️⃣ Revenez ici et testez le clavier\n" +
                    "4️⃣ Changez de clavier en appuyant sur l'icône clavier dans la barre de notifications"
            textSize = 15f
            setTextColor(Color.parseColor("#444444"))
            setLineSpacing(0f, 1.3f)
        }
        
        installCard.addView(installTitle)
        installCard.addView(installSteps)
        
        // Boutons d'action
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
        }
        
        val activateButton = Button(this).apply {
            text = "🔧 Activer le clavier dans les paramètres"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#0080FF"))
            setTextColor(Color.parseColor("#F8F8FF"))
            setPadding(20, 16, 20, 16)
            setOnClickListener {
                val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                startActivity(intent)
            }
        }
        
        val testTitle = TextView(this).apply {
            text = "✍️ Zone de test du clavier"
            textSize = 18f
            setTextColor(Color.parseColor("#0080FF"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 12)
        }
        
        val testDescription = TextView(this).apply {
            text = "Tapez dans le champ ci-dessous pour tester le clavier Lëtzebuergesch :"
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12)
        }
        
        val testEditText = EditText(this).apply {
            hint = "Schreift op Lëtzebuergesch... (Écrivez en luxembourgeois...)"
            textSize = 16f
            setPadding(16, 16, 16, 16)
            minHeight = 120
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.parseColor("#1C1C1C"))
            setHintTextColor(Color.parseColor("#999999"))
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.setMargins(8, 8, 8, 8)
            this.layoutParams = layoutParams
        }
        
        val switchButton = Button(this).apply {
            text = "🔄 Basculer vers Lëtzebuergesch Clavier"
            textSize = 14f
            setBackgroundColor(Color.parseColor("#228B22"))
            setTextColor(Color.parseColor("#F8F8FF"))
            setPadding(16, 12, 16, 12)
            setOnClickListener {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        }
        
        // Section Sources littéraires
        val sourcesCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#F0F8E8"))
        }
        
        val sourcesTitle = TextView(this).apply {
            text = "📚 Sources littéraires luxembourgeoises"
            textSize = 18f
            setTextColor(Color.parseColor("#228B22"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }
        
        val sourcesText = TextView(this).apply {
            text = "Les suggestions de mots en Lëtzebuergesch sont construites sur les travaux des défenseurs du Luxembourgeois :\n\n" +
                    "✍️ Auteurs et linguistes luxembourgeois de référence,\n\n" +
                    "Les transcriptions des conférences de presse du sip.gouvernement.lu"
            textSize = 14f
            setTextColor(Color.parseColor("#2F5233"))
            setLineSpacing(0f, 1.3f)
        }
        
        sourcesCard.addView(sourcesTitle)
        sourcesCard.addView(sourcesText)
        
        // Footer
        val footerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#F8F9FA"))
        }
        
        val footerText = TextView(this).apply {
            text = "Fait au Luxembourg avec au ❤️\n" +
                    "Préservons le luxembourgeoise pour les générations futures !\n\n" +
                    "© LuxKeyb™ - Lëtzebuergesch Clavier\n"

            textSize = 12f
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
        }
        
        footerCard.addView(footerText)
        
        // Assembler
        buttonLayout.addView(activateButton)
        buttonLayout.addView(testTitle)
        buttonLayout.addView(testDescription)
        buttonLayout.addView(testEditText)
        buttonLayout.addView(switchButton)
        
        mainLayout.addView(headerLayout)
        mainLayout.addView(descriptionCard)
        mainLayout.addView(installCard)
        mainLayout.addView(buttonLayout)
        mainLayout.addView(sourcesCard)
        mainLayout.addView(footerCard)
        
        return mainLayout
    }
    
    fun createStatsContent(): LinearLayout {
        Log.d("SettingsActivity", "Création du contenu des statistiques")
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            setBackgroundColor(Color.WHITE)
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
        
        val levelContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 40)
        }
        
        // Message de progression vers le niveau suivant
        val progressMessage = TextView(this).apply {
            text = if (wordsRemaining > 0) {
                "Votre niveau actuel est $levelName, plus que $wordsRemaining mot${if (wordsRemaining > 1) "s" else ""} restant${if (wordsRemaining > 1) "s" else ""} à découvrir pour passer au niveau suivant $nextLevelName"
            } else {
                "Vous avez atteint le niveau maximum : $levelName ! 👑"
            }
            textSize = 16f
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            setPadding(16, 0, 16, 24)
            setLineSpacing(6f, 1f)
        }
        
        levelContainer.addView(progressMessage)
        
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
        
        val percentageText = TextView(this).apply {
            text = "${String.format("%.1f", stats.coveragePercentage)}%"
            textSize = 32f
            setTextColor(Color.parseColor("#1C1C1C"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 8)
        }
        
        val percentageLabel = TextView(this).apply {
            text = "${stats.wordsDiscovered} mots découverts sur les ${stats.totalWords} mots du dictionnaire Lëtzebuergesch"
            textSize = 14f
            setTextColor(Color.parseColor("#999999"))
            gravity = Gravity.CENTER
        }
        
        levelContainer.addView(levelBadge)
        levelContainer.addView(percentageText)
        levelContainer.addView(percentageLabel)
        
        // === Mot du Jour - Design épuré ===
        val (wordOfDay, usageCount) = getWordOfTheDay()
        
        val wordContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 40, 24, 40)
            setBackgroundColor(Color.parseColor("#FAFAFA"))
        }
        
        val wordLabel = TextView(this).apply {
            text = "MOT DU JOUR"
            textSize = 12f
            setTextColor(Color.parseColor("#FF8C00"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.1f
            setPadding(0, 0, 0, 16)
        }
        
        val wordText = TextView(this).apply {
            text = wordOfDay
            textSize = 48f
            setTextColor(Color.parseColor("#1C1C1C"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        
        val wordUsage = TextView(this).apply {
            text = if (usageCount > 0) "utilisé $usageCount fois" else "nouveau mot à découvrir"
            textSize = 14f
            setTextColor(Color.parseColor("#999999"))
            gravity = Gravity.CENTER
        }
        
        wordContainer.addView(wordLabel)
        wordContainer.addView(wordText)
        wordContainer.addView(wordUsage)
        
        // === Top 5 - Liste simple ===
        val top5Container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 40, 24, 40)
        }
        
        val top5Title = TextView(this).apply {
            text = "Mots les plus utilisés"
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
                textSize = 16f
                setTextColor(Color.parseColor("#FF8C00"))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 16, 0)
            }
            
            val wordName = TextView(this).apply {
                text = word.first
                textSize = 16f
                setTextColor(Color.parseColor("#1C1C1C"))
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
            
            val wordCount = TextView(this).apply {
                text = "${word.second}"
                textSize = 16f
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
            text = "Statistiques globales"
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
        
        statsRow.addView(createStatBlock("${stats.wordsDiscovered}", "Mots découverts"))
        statsRow.addView(createStatBlock("${stats.totalUsages}", "Utilisations"))
        
        statsGridContainer.addView(statsRow)
        
        // === Boutons de contrôle ===
        val buttonsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 0)
        }
        
        // Bouton "Actualiser" masqué mais code conservé pour réactivation future si nécessaire
        /*
        val refreshButton = Button(this).apply {
            text = "⟳ Actualiser"
            textSize = 14f
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.parseColor("#1C1C1C"))
            setPadding(24, 16, 24, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                Log.d("SettingsActivity", "🔄 Bouton actualiser pressé")
                
                // Laisser un délai pour que les sauvegardes différées se terminent
                // CreoleDictionaryWithUsage sauvegarde automatiquement après 30 secondes d'inactivité
                Toast.makeText(this@SettingsActivity, "Actualisation des statistiques...", Toast.LENGTH_SHORT).show()
                
                // Attendre un peu puis recharger l'activité
                postDelayed({
                    Log.d("SettingsActivity", "🔄 Rechargement de l'activité après délai")
                    recreate() // Redémarre complètement l'activité
                }, 1000) // Attendre 1 seconde
            }
        }
        
        buttonsContainer.addView(refreshButton)
        */
        
        // === Mots Découverts ===
        val discoveredWordsContainer = createWordListSection(
            "🔍 Mots Découverts (${stats.discoveredWordsList.size})",
            stats.discoveredWordsList,
            "#4CAF50"
        )
        
        // === Mots à Découvrir ===
        val wordsToDiscoverContainer = createWordListSection(
            "🌟 Mots à découvrir",
            stats.wordsToDiscover,
            "#2196F3"
        )
        
        // Assembler
        statsContainer.addView(levelContainer)
        statsContainer.addView(wordContainer)
        statsContainer.addView(top5Container)
        statsContainer.addView(statsGridContainer)
        statsContainer.addView(discoveredWordsContainer)
        statsContainer.addView(wordsToDiscoverContainer)
        statsContainer.addView(buttonsContainer)
        
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
            setPadding(0, 24, 0, 0)
            
            // Titre de la section
            val sectionTitle = TextView(this@SettingsActivity).apply {
                text = title
                textSize = 16f
                setTextColor(Color.parseColor("#1C1C1C"))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 16)
            }
            addView(sectionTitle)
            
            if (words.isEmpty()) {
                // Message si aucun mot
                val emptyMessage = TextView(this@SettingsActivity).apply {
                    text = "Aucun mot dans cette catégorie pour le moment"
                    textSize = 14f
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
                        300 // Hauteur maximale
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
                val screenWidth = resources.displayMetrics.widthPixels - 96
                
                words.forEach { word ->
                    // Créer le chip du mot
                    val wordChip = TextView(this@SettingsActivity).apply {
                        text = word
                        textSize = 13f
                        setTextColor(Color.parseColor(accentColor))
                        setPadding(10, 5, 10, 5)
                        setBackgroundColor(Color.parseColor("${accentColor}20"))
                        setSingleLine(true)
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
        Log.d("SettingsActivity", "🔍 Chargement des statistiques du vocabulaire luxembourgeois")
        return try {
            // Charger le dictionnaire luxembourgeois depuis les assets
            val inputStream = assets.open("luxemburgish_dict.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            
            var totalWords = 0
            var wordsDiscovered = 0
            var totalUsages = 0
            val wordUsages = mutableListOf<Pair<String, Int>>()
            val discoveredWords = mutableListOf<String>()
            
            // Fichier d'usage utilisateur (optionnel)
            val usageFile = File(filesDir, "luxemburgish_dict_with_usage.json")
            val usageData = if (usageFile.exists()) {
                try {
                    JSONObject(usageFile.readText())
                } catch (e: Exception) {
                    Log.w("SettingsActivity", "Erreur lecture usage file: ${e.message}")
                    JSONObject()
                }
            } else {
                JSONObject()
            }
            
            Log.d("SettingsActivity", "📂 Fichier usage existe: ${usageFile.exists()}")
            Log.d("SettingsActivity", "📄 Dictionnaire luxembourgeois: ${jsonObject.length()} mots")
            
            if (usageFile.exists()) {
                val usageContent = usageFile.readText()
                Log.d("SettingsActivity", "📄 Contenu fichier usage (${usageContent.length} chars): ${usageContent.take(500)}...")
            }
            
            // Parcourir tous les mots du dictionnaire luxembourgeois
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val word = keys.next()
                totalWords++
                
                // Vérifier l'usage utilisateur (format CreoleDictionaryWithUsage)
                val userCount = if (usageData.has(word)) {
                    try {
                        val wordData = usageData.getJSONObject(word)
                        wordData.optInt("user_count", 0)
                    } catch (e: Exception) {
                        // Fallback en cas de format simple
                        usageData.optInt(word, 0)
                    }
                } else {
                    0
                }
                
                if (userCount > 0) {
                    totalUsages += userCount
                    wordUsages.add(Pair(word, userCount))
                    
                    // Compter comme "découvert" si utilisé au moins une fois
                    wordsDiscovered++
                    if (word.length >= 3) {
                        discoveredWords.add(word)
                    }
                }
            }
            
            Log.d("SettingsActivity", "Stats luxembourgeoises: $totalWords mots, Usage: $totalUsages, Découverts: $wordsDiscovered")
            
            val topWords = wordUsages.filter { it.first.length >= 3 }.sortedByDescending { it.second }.take(5)
            val coverage = if (totalWords > 0) (wordsDiscovered.toFloat() / totalWords * 100) else 0f
            
            // Générer les mots à découvrir (mots non utilisés avec longueur >= 3)
            val wordsToDiscoverCandidates = mutableListOf<String>()
            val dictKeys = jsonObject.keys()
            while (dictKeys.hasNext()) {
                val word = dictKeys.next()
                val userCount = if (usageData.has(word)) {
                    try {
                        val wordData = usageData.getJSONObject(word)
                        wordData.optInt("user_count", 0)
                    } catch (e: Exception) {
                        usageData.optInt(word, 0)
                    }
                } else {
                    0
                }
                if (userCount == 0 && word.length >= 3) {
                    wordsToDiscoverCandidates.add(word)
                }
            }
            
            val wordsToDiscoverList = wordsToDiscoverCandidates.shuffled().take(10)
            
            VocabularyStats(
                totalWords,
                wordsDiscovered,
                totalUsages,
                topWords,
                coverage,
                discoveredWords.sorted(),
                wordsToDiscoverList
            )
            
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur chargement stats luxembourgeoises: ${e.message}", e)
            VocabularyStats(0, 0, 0, emptyList(), 0f, emptyList(), emptyList())
        }
    }
    
    // Classe pour stocker les seuils calculés dynamiquement
    data class GaussianLevels(
        val level1: Int, // Novice
        val level2: Int, // Ufänker
        val level3: Int, // Fortgeschratt
        val level4: Int, // Mëttel
        val level5: Int, // Hirsch
        val level6: Int, // Aquila
        val level7: Int, // Expert
        val levelMax: Int // Meeschter (tous les mots)
    )
    
    // Calcul dynamique des seuils basés sur une distribution gaussienne adaptée
    // Plus de niveaux dans la partie basse pour une progression motivante pour les débutants
    private fun calculateGaussianLevels(): GaussianLevels {
        return try {
            // Charger le dictionnaire pour compter le nombre de mots
            val inputStream = assets.open("luxemburgish_dict.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val totalWords = jsonObject.length()
            
            // Calculs gaussiens
            val mean = totalWords / 2.0  // Moyenne μ
            val sigma = totalWords / 6.0  // Écart-type σ (pour couvrir ±3σ = 99.7%)
            
            // Distribution gaussienne adaptée pour une progression plus motivante
            // Les niveaux bas ont des paliers plus rapprochés pour encourager les débutants
            val level1 = 50  // Noviz: 0-50 mots (~1.5% - vraiment débutant)
            val level2 = (mean - 2.5 * sigma).toInt().coerceAtLeast(level1)  // Ufänker: ~270 mots (~8%)
            val level3 = (mean - 1.5 * sigma).toInt().coerceAtLeast(level2)  // Fortgeschratt: ~810 mots (~17%)
            val level4 = (mean - 0.7 * sigma).toInt().coerceAtLeast(level3)  // Mëttel bas: ~1240 mots (~13%)
            val level5 = (mean + 1.3 * sigma).toInt().coerceAtMost(totalWords)  // Hirsch: ~2320 mots (~33% - pic central élargi)
            val level6 = (mean + 2.5 * sigma).toInt().coerceAtMost(totalWords)  // Aquila: ~2970 mots (~17%)
            val level7 = (totalWords - 1).coerceAtMost(totalWords)  // Expert: 3238 mots (~8%)
            val levelMax = totalWords  // Meeschter: 3239 mots (perfection absolue!)
            
            Log.d("SettingsActivity", "📊 Calcul gaussien adapté: Total=$totalWords, μ=$mean, σ=$sigma")
            Log.d("SettingsActivity", "📊 Seuils motivants: L1=$level1, L2=$level2, L3=$level3, L4=$level4, L5=$level5, L6=$level6, L7=$level7, Max=$levelMax")
            
            GaussianLevels(level1, level2, level3, level4, level5, level6, level7, levelMax)
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur calcul gaussien: ${e.message}", e)
            // Valeurs par défaut en cas d'erreur (distribution motivante)
            GaussianLevels(50, 270, 810, 1240, 2320, 2970, 3238, 3239)
        }
    }
    
    private fun getCurrentLevel(wordsDiscovered: Int): String {
        val levels = calculateGaussianLevels()
        
        return when {
            wordsDiscovered >= levels.levelMax -> "🧙‍♀️ Meeschter"  // Tous les mots!
            wordsDiscovered >= levels.level7 -> "👑 Expert"          // ~0.13% - très rare
            wordsDiscovered >= levels.level6 -> "🦅 Aquila"          // ~2.14%
            wordsDiscovered >= levels.level5 -> "🦌 Hirsch"          // ~13.59%
            wordsDiscovered >= levels.level4 -> "💎 Mëttel"          // ~34.13% - pic central
            wordsDiscovered >= levels.level3 -> "🔥 Fortgeschratt"   // ~34.13%
            wordsDiscovered >= levels.level2 -> "🌱 Ufänker"         // ~13.59%
            wordsDiscovered >= levels.level1 -> "🐣 Noviz"          // ~2.14%
            else -> "🐣 Noviz"                                       // débutant absolu
        }
    }
    
    private fun getNextLevelInfo(wordsDiscovered: Int): Pair<String, Int> {
        val levels = calculateGaussianLevels()
        
        return when {
            wordsDiscovered >= levels.levelMax -> Pair("Meeschter", 0) // Niveau maximum atteint!
            wordsDiscovered >= levels.level7 -> Pair("Meeschter", levels.levelMax - wordsDiscovered)
            wordsDiscovered >= levels.level6 -> Pair("Expert", levels.level7 - wordsDiscovered)
            wordsDiscovered >= levels.level5 -> Pair("Aquila", levels.level6 - wordsDiscovered)
            wordsDiscovered >= levels.level4 -> Pair("Hirsch", levels.level5 - wordsDiscovered)
            wordsDiscovered >= levels.level3 -> Pair("Mëttel", levels.level4 - wordsDiscovered)
            wordsDiscovered >= levels.level2 -> Pair("Fortgeschratt", levels.level3 - wordsDiscovered)
            wordsDiscovered >= levels.level1 -> Pair("Ufänker", levels.level2 - wordsDiscovered)
            else -> Pair("Ufänker", levels.level1 - wordsDiscovered)  // FIX: Si < level1, le prochain niveau est Ufänker!
        }
    }

    
    // Adapter pour ViewPager2
    private class SettingsPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 2
        
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> HomeFragment()
                1 -> StatsFragment()
                else -> HomeFragment()
            }
        }
    }
    
    // Fragment pour l'accueil
    class HomeFragment : Fragment() {
        override fun onCreateView(
            inflater: android.view.LayoutInflater,
            container: android.view.ViewGroup?,
            savedInstanceState: android.os.Bundle?
        ): View {
            val activity = requireActivity() as SettingsActivity
            val scrollView = ScrollView(activity)
            scrollView.addView(activity.createHomeContent())
            return scrollView
        }
    }
    
    // Fragment pour les statistiques
    class StatsFragment : Fragment() {
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
                    Toast.makeText(activity, "Actualisation des statistiques...", Toast.LENGTH_SHORT).show()
                    
                    // Forcer la sauvegarde des données en attente
                    flushPendingUpdates(activity, activity.activityScope)
                    
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
            val statsContent = activity.createStatsContent()
            scrollView.addView(statsContent)
            
            // Ajouter le ScrollView dans le SwipeRefreshLayout
            swipeRefreshLayout.addView(scrollView)
            
            Log.d("SettingsActivity", "StatsFragment créé avec Pull-to-Refresh")
            return swipeRefreshLayout
        }
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
                    return Pair("Bonjou", 0)
                }
                
                // Utiliser la date comme seed pour avoir le même mot toute la journée
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dateString = dateFormat.format(Date())
                val seed = dateString.hashCode().toLong()
                val random = Random(seed)
                
                val selectedWord = allWords[random.nextInt(allWords.size)]
                // Lire directement l'entier
                usageCount = jsonObject.optInt(selectedWord, 0)
                
                return Pair(selectedWord, usageCount)
            } else {
                Log.d("SettingsActivity", "Fichier usage n'existe pas, création depuis assets")
                // Charger depuis les assets (format JSONObject)
                val jsonString = assets.open("luxemburgish_dict.json").bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(jsonString)
                Log.d("SettingsActivity", "Dictionnaire luxembourgeois chargé: ${jsonObject.length()} mots")
                
                allWords = mutableListOf<String>().apply {
                    jsonObject.keys().forEach { word -> add(word) }
                }
                
                if (allWords.isEmpty()) {
                    return Pair("Bonjou", 0)
                }
                
                // Utiliser la date comme seed
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dateString = dateFormat.format(Date())
                val seed = dateString.hashCode().toLong()
                val random = Random(seed)
                
                val selectedWord = allWords[random.nextInt(allWords.size)]
                
                return Pair(selectedWord, 0)
            }
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur mot du jour: ${e.message}")
            Pair("Bonjou", 0)
        }
    }
}
