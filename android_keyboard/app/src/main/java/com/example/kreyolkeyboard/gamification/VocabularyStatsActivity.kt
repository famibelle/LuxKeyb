package com.example.kreyolkeyboard.gamification

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.kreyolkeyboard.R
import java.io.File
import org.json.JSONObject
import android.util.Log

/**
 * Activity pour afficher les statistiques de vocabulaire
 * Version simplifiée et compacte
 */
class VocabularyStatsActivity : AppCompatActivity() {
    
    private val TAG = "VocabStatsActivity"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vocabulary_stats)
        
        // Configuration de la barre d'action
        supportActionBar?.apply {
            title = "Mon Kreyòl"
            setDisplayHomeAsUpEnabled(true)
        }
        
        // Charger et afficher les statistiques
        loadAndDisplayStats()
        
        // Bouton rafraîchir
        findViewById<Button>(R.id.btnRefresh)?.setOnClickListener {
            loadAndDisplayStats()
        }
        
        // Bouton fermer
        findViewById<Button>(R.id.btnClose)?.setOnClickListener {
            finish()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    private fun loadAndDisplayStats() {
        try {
            // Charger les statistiques depuis le fichier
            val stats = loadVocabularyStats()
            
            // Afficher les statistiques
            displayStats(stats)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du chargement des statistiques: ${e.message}", e)
            displayError()
        }
    }
    
    private fun loadVocabularyStats(): VocabularyStats {
        // IMPORTANT: Activity et Service IME ne partagent PAS le même filesDir
        // On doit utiliser le chemin complet de l'app
        val appDataDir = applicationContext.dataDir
        val dictFile = File(appDataDir, "files/luxemburgish_dict_with_usage.json")
        
        Log.d(TAG, "Tentative de chargement depuis: ${dictFile.absolutePath}")
        
        if (!dictFile.exists()) {
            Log.w(TAG, "Fichier dictionnaire introuvable: ${dictFile.absolutePath}")
            return VocabularyStats(
                coveragePercentage = 0f,
                wordsDiscovered = 0,
                totalWords = 0,
                totalUsages = 0,
                topWords = emptyList(),
                recentWords = emptyList(),
                masteredWords = 0
            )
        }
        
        val jsonContent = dictFile.readText()
        // Le fichier est un JSONObject: {"mot": {"frequency": X, "user_count": Y}}
        val jsonDict = org.json.JSONObject(jsonContent)
        
        var totalWords = 0
        var usedWords = 0
        var totalUsages = 0
        val wordUsageList = mutableListOf<WordUsageStats>()
        val recentWordsList = mutableListOf<String>()
        var masteredCount = 0
        
        // Parcourir toutes les clés (mots) du dictionnaire
        val keys = jsonDict.keys()
        while (keys.hasNext()) {
            val word = keys.next()
            val wordData = jsonDict.getJSONObject(word)
            val frequency = wordData.getInt("frequency")
            val userCount = wordData.getInt("user_count")
            
            totalWords++
            
            if (userCount > 0) {
                usedWords++
                totalUsages += userCount
                wordUsageList.add(WordUsageStats(word, userCount, frequency))
                
                // Mots récents (1-3 utilisations)
                if (userCount in 1..3) {
                    recentWordsList.add(word)
                }
                
                // Mots maîtrisés (10+ utilisations)
                if (userCount >= 10) {
                    masteredCount++
                }
            }
        }
        
        // Trier par nombre d'utilisations (décroissant)
        wordUsageList.sortByDescending { it.userCount }
        
        // Top 10 mots
        val topWords = wordUsageList.take(10)
        
        // Calculer le pourcentage de couverture
        val coverage = if (totalWords > 0) (usedWords * 100f / totalWords) else 0f
        
        return VocabularyStats(
            coveragePercentage = coverage,
            wordsDiscovered = usedWords,
            totalWords = totalWords,
            totalUsages = totalUsages,
            topWords = topWords,
            recentWords = recentWordsList.take(10),
            masteredWords = masteredCount
        )
    }
    
    private fun displayStats(stats: VocabularyStats) {
        // Niveau actuel basé sur le nombre de mots
        val (level, emoji, nextLevel, wordsToNext) = getCurrentLevel(stats.wordsDiscovered)
        
        // Afficher le niveau
        findViewById<TextView>(R.id.tvLevel)?.text = "$emoji $level $emoji"
        
        // Barre de progression
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val tvProgress = findViewById<TextView>(R.id.tvProgress)
        
        val progressPercent = if (wordsToNext > 0) {
            ((stats.wordsDiscovered % 100) * 100 / 100).coerceIn(0, 100)
        } else {
            100
        }
        
        progressBar?.apply {
            max = 100
            progress = progressPercent
        }
        tvProgress?.text = "${stats.wordsDiscovered}/${stats.wordsDiscovered + wordsToNext} mots"
        
        // Statistiques principales
        findViewById<TextView>(R.id.tvCoverage)?.text = 
            "📊 ${String.format("%.1f", stats.coveragePercentage)}% du dictionnaire exploré"
        
        // Top 5 mots
        val topWordsContainer = findViewById<LinearLayout>(R.id.topWordsContainer)
        topWordsContainer?.removeAllViews()
        
        stats.topWords.take(5).forEachIndexed { index, wordStats ->
            val medal = when(index) {
                0 -> "🥇"
                1 -> "🥈"
                2 -> "🥉"
                else -> "${index + 1}."
            }
            
            val dots = "●".repeat(wordStats.userCount.coerceAtMost(8))
            val wordView = TextView(this).apply {
                text = "$medal ${wordStats.word}    $dots ${wordStats.userCount}×"
                textSize = 16f
                setPadding(0, 8, 0, 8)
                setTextColor(resources.getColor(android.R.color.white, null))
            }
            topWordsContainer?.addView(wordView)
        }
        
        // Statistiques détaillées
        findViewById<TextView>(R.id.tvTotalWords)?.text = "✓ ${stats.wordsDiscovered} mots différents utilisés"
        findViewById<TextView>(R.id.tvTotalUsages)?.text = "✓ ${stats.totalUsages} utilisations totales"
        findViewById<TextView>(R.id.tvMastered)?.text = "✓ ${stats.masteredWords} mots maîtrisés (≥10×)"
        
        // Progression vers le prochain niveau
        findViewById<TextView>(R.id.tvNextLevel)?.text = 
            "🎯 PROGRESSION VERS $nextLevel\n   Plus que $wordsToNext mots à découvrir!"
    }
    
    private fun displayError() {
        findViewById<TextView>(R.id.tvLevel)?.text = "⚠️ ERREUR"
        findViewById<TextView>(R.id.tvCoverage)?.text = "Impossible de charger les statistiques"
    }
    
    private fun getCurrentLevel(wordsUsed: Int): LevelInfo {
        return when {
            wordsUsed < 100 -> LevelInfo(
                "DÉBUTANT", "⭐", "🌟 APPRENANT", 100 - wordsUsed
            )
            wordsUsed < 250 -> LevelInfo(
                "APPRENANT", "🌟", "💫 INTERMÉDIAIRE", 250 - wordsUsed
            )
            wordsUsed < 500 -> LevelInfo(
                "INTERMÉDIAIRE", "💫", "✨ AVANCÉ", 500 - wordsUsed
            )
            wordsUsed < 1000 -> LevelInfo(
                "AVANCÉ", "✨", "🔥 EXPERT", 1000 - wordsUsed
            )
            wordsUsed < 1500 -> LevelInfo(
                "EXPERT", "🔥", "👑 MAÎTRE", 1500 - wordsUsed
            )
            wordsUsed < 2000 -> LevelInfo(
                "MAÎTRE", "👑", "🏅 LÉGENDE", 2000 - wordsUsed
            )
            else -> LevelInfo(
                "LÉGENDE", "🏅", "MAX", 0
            )
        }
    }
    
    data class LevelInfo(
        val name: String,
        val emoji: String,
        val nextLevelName: String,
        val wordsToNext: Int
    )
}
