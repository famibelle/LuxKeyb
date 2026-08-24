package com.example.kreyolkeyboard

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Écran des réglages du clavier, atteint par l'engrenage du bandeau de
 * [SettingsActivity].
 *
 * Écran à part et non onglet : les réglages vivent derrière un engrenage sur
 * Android, et la barre de [SettingsActivity] porte déjà sept onglets. Ils ont
 * d'abord vécu dans une carte de l'onglet À Propos (v10.11.7), qui est une page de
 * présentation où personne ne cherche un interrupteur.
 *
 * Cet écran a vocation à grandir : les points 3 à 6 de ACCESSIBILITE.md sont tous
 * des réglages (taille des touches, délai de l'appui long, nombre de propositions),
 * dont plusieurs à plusieurs crans.
 */
class KeyboardSettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "KeyboardSettings"
        private const val BLEU = "#0080FF"
        private const val ENCRE = "#333333"
        private const val ENCRE_DOUCE = "#666666"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Le thème de l'application est Theme.AppCompat, qui pose une barre d'action
        // sombre : elle doublerait le bandeau bleu ci-dessous. SettingsActivity la
        // masque de la même façon.
        supportActionBar?.hide()

        val racine = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }
        racine.addView(bandeau())
        racine.addView(ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            addView(contenu())
        })
        setContentView(racine)
    }

    /** Bandeau bleu avec la flèche de retour, repris de l'écran principal. */
    private fun bandeau(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        setBackgroundColor(Color.parseColor(BLEU))
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(12), dp(16), dp(12))

        addView(TextView(this@KeyboardSettingsActivity).apply {
            text = "←"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            // Cible confortable : cette flèche est le seul moyen de sortir.
            minWidth = dp(48)
            minHeight = dp(48)
            contentDescription = "Retour"
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        })

        addView(TextView(this@KeyboardSettingsActivity).apply {
            text = "Réglages du clavier"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(8), 0, 0, 0)
        })
    }

    private fun contenu(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setPadding(dp(16), dp(16), dp(16), dp(24))

        addView(carte().apply {
            addView(titreSection("Retour de frappe"))
            addView(explication(
                "Ce que le clavier fait à chaque appui. Le choix s'applique dès le " +
                        "retour dans un champ de saisie."
            ))
            addView(interrupteur(
                "Vibration à la frappe",
                KeyboardPreferences.hapticEnabled(this@KeyboardSettingsActivity)
            ) { actif ->
                KeyboardPreferences.setHapticEnabled(this@KeyboardSettingsActivity, actif)
            })
            addView(interrupteur(
                "Son de frappe",
                KeyboardPreferences.soundEnabled(this@KeyboardSettingsActivity)
            ) { actif ->
                KeyboardPreferences.setSoundEnabled(this@KeyboardSettingsActivity, actif)
            })
            addView(explication(
                "Ces deux réglages sont dans l'application et non dans ceux du " +
                        "téléphone : sur beaucoup d'appareils, le réglage de vibration au " +
                        "toucher ne gouverne que le clavier du constructeur."
            ))
        })
    }

    private fun carte(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.WHITE)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun titreSection(texte: String): TextView = TextView(this).apply {
        text = texte
        textSize = 17f
        setTextColor(Color.parseColor(BLEU))
        setTypeface(null, Typeface.BOLD)
        setPadding(0, 0, 0, dp(4))
    }

    private fun explication(texte: String): TextView = TextView(this).apply {
        text = texte
        textSize = 14f
        setTextColor(Color.parseColor(ENCRE_DOUCE))
        setLineSpacing(0f, 1.3f)
        setPadding(0, dp(4), 0, dp(4))
    }

    /**
     * Une ligne de réglage avec son interrupteur.
     *
     * Les couleurs sont posées explicitement : les états non cochés du thème sont un
     * gris presque blanc, invisible sur une carte blanche, ce qui avait déjà fait
     * disparaître des boutons radio d'un premier essai de cet écran.
     */
    private fun interrupteur(
        libelle: String,
        actifAuDepart: Boolean,
        onChange: (Boolean) -> Unit
    ): View = Switch(this).apply {
        text = libelle
        textSize = 16f
        setTextColor(Color.parseColor(ENCRE))
        isChecked = actifAuDepart
        setPadding(0, dp(14), 0, dp(14))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val etats = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        thumbTintList = ColorStateList(
            etats, intArrayOf(Color.parseColor(BLEU), Color.parseColor("#BDBDBD"))
        )
        trackTintList = ColorStateList(
            etats, intArrayOf(Color.parseColor("#90CAF9"), Color.parseColor("#757575"))
        )
        setOnCheckedChangeListener { _, coche ->
            onChange(coche)
            Log.d(TAG, "Réglage « $libelle » : $coche")
        }
    }

    private fun dp(valeur: Int): Int = (valeur * resources.displayMetrics.density).toInt()
}
