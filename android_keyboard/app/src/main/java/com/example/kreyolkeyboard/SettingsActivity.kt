package com.example.kreyolkeyboard

import android.content.Context
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.util.Log

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d("SettingsActivity", "Création de l'activité de test")
        
        // Créer l'interface programmatiquement
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        val title = TextView(this).apply {
            text = "Test du Clavier Kreyòl"
            textSize = 24f
            setPadding(0, 0, 0, 32)
        }
        
        val instruction = TextView(this).apply {
            text = "Cliquez dans le champ ci-dessous pour tester le clavier :"
            textSize = 16f
            setPadding(0, 0, 0, 16)
        }
        
        val editText = EditText(this).apply {
            hint = "Écrivez votre texte ici..."
            textSize = 18f
            setPadding(16, 16, 16, 16)
            minHeight = 120
        }
        
        val showKeyboardButton = Button(this).apply {
            text = "Forcer l'affichage du clavier"
            textSize = 16f
            setOnClickListener {
                Log.d("SettingsActivity", "Bouton forcé cliqué")
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(editText, InputMethodManager.SHOW_FORCED)
                Log.d("SettingsActivity", "Tentative forcée d'affichage du clavier")
            }
        }
        
        val switchKeyboardButton = Button(this).apply {
            text = "Basculer vers Kreyol"
            textSize = 16f
            setOnClickListener {
                Log.d("SettingsActivity", "Bouton basculement cliqué")
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.switchToLastInputMethod(editText.windowToken)
                Log.d("SettingsActivity", "Tentative de basculement vers Kreyol")
            }
        }
        
        layout.addView(title)
        layout.addView(instruction)
        layout.addView(editText)
        layout.addView(showKeyboardButton)
        layout.addView(switchKeyboardButton)
        
        setContentView(layout)
        
        // Forcer le focus sur le champ de texte après un délai
        editText.postDelayed({
            editText.requestFocus()
            Log.d("SettingsActivity", "Focus demandé sur le champ de texte")
            
            // Forcer l'affichage du clavier
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(editText, InputMethodManager.SHOW_FORCED)
            Log.d("SettingsActivity", "Tentative forcée d'affichage du clavier")
        }, 1000)
    }
}
