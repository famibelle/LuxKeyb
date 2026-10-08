package com.example.kreyolkeyboard

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
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
            // Sans identifiant, la position n'est pas sauvegardée : changer la
            // langue recrée l'écran, qui repartirait en haut, loin du choix fait.
            id = R.id.reglages_defilement
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            addView(contenu())
        })
        setContentView(racine)
        // Bord à bord sous Android 15 : le bandeau bleu passe sous la barre d'état.
        BordABord.appliquer(
            racine, haut = racine.getChildAt(0),
            lateraux = { listOf(racine.getChildAt(0), racine.getChildAt(1)) }
        )
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
            contentDescription = getString(R.string.retour)
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        })

        addView(TextView(this@KeyboardSettingsActivity).apply {
            text = getString(R.string.ks_titre)
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
            addView(titreSection(R.string.ks_apparence))
            addView(explication(R.string.ks_apparence_intro))
            addView(choixTheme())
            addView(explication(R.string.ks_apparence_note))
        })
        addView(espacement())

        addView(carte().apply {
            addView(titreSection(R.string.ks_disposition))
            addView(explication(R.string.ks_disposition_intro))
            addView(choixDisposition())
            addView(explication(R.string.ks_disposition_note))
            // Sur tablette seulement : un téléphone couché n'a pas la largeur
            // d'un clavier coupé en deux.
            if (KeyboardLayoutManager.isTablet(this@KeyboardSettingsActivity)) {
                addView(interrupteur(
                    R.string.ks_clavier_scinde,
                    KeyboardPreferences.clavierScinde(this@KeyboardSettingsActivity)
                ) { actif ->
                    KeyboardPreferences.setClavierScinde(this@KeyboardSettingsActivity, actif)
                })
                addView(explication(R.string.ks_clavier_scinde_note))
            }
        })
        addView(espacement())

        addView(carte().apply {
            addView(titreSection(R.string.ks_suggestions))
            addView(explication(R.string.ks_suggestions_intro))
            addView(interrupteur(
                R.string.ks_propositions_francais,
                KeyboardPreferences.propositionsFrancais(this@KeyboardSettingsActivity)
            ) { actif ->
                KeyboardPreferences.setPropositionsFrancais(this@KeyboardSettingsActivity, actif)
                Log.d(TAG, "Propositions en français : $actif")
            })
            addView(explication(R.string.ks_suggestions_note))
        })
        addView(espacement())

        addView(carte().apply {
            addView(titreSection(R.string.ks_retour_frappe))
            addView(explication(R.string.ks_retour_frappe_intro))
            addView(interrupteur(
                R.string.ks_vibration,
                KeyboardPreferences.hapticEnabled(this@KeyboardSettingsActivity)
            ) { actif ->
                KeyboardPreferences.setHapticEnabled(this@KeyboardSettingsActivity, actif)
            })
            addView(interrupteur(
                R.string.ks_son,
                KeyboardPreferences.soundEnabled(this@KeyboardSettingsActivity)
            ) { actif ->
                KeyboardPreferences.setSoundEnabled(this@KeyboardSettingsActivity, actif)
            })
            addView(explication(R.string.ks_retour_frappe_note))
        })
        addView(espacement())

        addView(carte().apply {
            addView(titreSection(R.string.ks_appui_long))
            addView(explication(R.string.ks_appui_long_intro))
            addView(choixDelaiAppuiLong())
            addView(explication(R.string.ks_appui_long_note))
        })
        addView(espacement())

        addView(carte().apply {
            addView(titreSection(R.string.ks_emojis))
            addView(explication(R.string.ks_emojis_intro))
            addView(boutonSecondaire(R.string.ks_emojis_vider) {
                EmojiRecents.vider(this@KeyboardSettingsActivity)
                Toast.makeText(
                    this@KeyboardSettingsActivity,
                    getString(R.string.ks_emojis_effaces),
                    Toast.LENGTH_SHORT
                ).show()
            })
        })
        addView(espacement())

        addView(carte().apply {
            addView(titreSection(R.string.ks_langue))
            addView(explication(R.string.ks_langue_intro))
            addView(choixLangue())
            addView(explication(R.string.ks_langue_note))
        })
    }

    /**
     * Un bouton d'action discret, sur le modèle des interrupteurs voisins :
     * couleurs posées à la main pour ne pas dépendre du thème AppCompat, qui
     * rendrait le libellé presque invisible sur la carte blanche.
     */
    private fun boutonSecondaire(@StringRes libelleRes: Int, onClick: () -> Unit): View =
        Button(this).apply {
            val libelle = getString(libelleRes)
            text = libelle
            textSize = 15f
            isAllCaps = false
            setTextColor(Color.parseColor(BLEU))
            setBackgroundColor(Color.parseColor("#F1F5F9"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            setOnClickListener {
                onClick()
                Log.d(TAG, "Action « $libelle » déclenchée")
            }
        }

    /**
     * Les trois positions du thème, en boutons radio.
     *
     * Un groupe radio et non un interrupteur : trois états, dont un, « comme le
     * téléphone », n'est ni l'un ni l'autre des deux autres et se perdrait dans
     * une bascule à deux positions.
     */
    private fun choixTheme(): View = groupeRadio(
        options = KeyboardTheme.Mode.entries,
        actuel = KeyboardPreferences.themeMode(this),
        libelle = { getString(it.libelle) }
    ) { mode ->
        KeyboardPreferences.setThemeMode(this, mode)
        Log.d(TAG, "Thème du clavier : ${mode.cle}")
    }

    /**
     * La langue de l'interface, « comme le téléphone » en tête. La liste est
     * typée `LangueInterface.Langue?`, `null` tenant la place du téléphone.
     *
     * L'écran se recrée dans la nouvelle langue dès le choix fait, comme toutes
     * les activités ouvertes : c'est AppCompat qui s'en charge.
     */
    private fun choixLangue(): View = groupeRadio(
        options = listOf<LangueInterface.Langue?>(null) + LangueInterface.Langue.entries,
        actuel = LangueInterface.actuelle(this),
        libelle = { it?.nom ?: getString(R.string.theme_systeme) }
    ) { langue ->
        Log.d(TAG, "Langue de l'interface : ${langue?.tag ?: "téléphone"}")
        LangueInterface.choisir(this, langue)
    }

    /** Les deux dispositions de la page des lettres (v29.5.0). */
    private fun choixDisposition(): View = groupeRadio(
        options = DispositionClavier.entries,
        actuel = KeyboardPreferences.disposition(this),
        libelle = { getString(it.libelle) }
    ) { disposition ->
        KeyboardPreferences.setDisposition(this, disposition)
        Log.d(TAG, "Disposition du clavier : ${disposition.cle}")
    }

    /**
     * Les crans du délai d'appui long (v29.5.0), en boutons radio comme le
     * thème : quatre durées nommées se lisent mieux qu'un curseur, et chacune
     * se retrouve à l'identique d'un téléphone à l'autre.
     */
    private fun choixDelaiAppuiLong(): View = groupeRadio(
        options = KeyboardPreferences.DelaiAppuiLong.entries,
        actuel = KeyboardPreferences.delaiAppuiLong(this),
        libelle = { getString(it.libelle) }
    ) { delai ->
        KeyboardPreferences.setDelaiAppuiLong(this, delai)
        Log.d(TAG, "Délai d'appui long : ${delai.ms} ms")
    }

    /**
     * Un choix exclusif parmi [options], en boutons radio verticaux.
     *
     * Les couleurs sont posées à la main pour la même raison que sur les
     * interrupteurs voisins : l'état non coché du thème est un gris presque blanc,
     * invisible sur une carte blanche.
     */
    private fun <T> groupeRadio(
        options: List<T>,
        actuel: T,
        libelle: (T) -> String,
        onChoix: (T) -> Unit
    ): View = RadioGroup(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        options.forEach { option ->
            addView(RadioButton(this@KeyboardSettingsActivity).apply {
                id = View.generateViewId()
                text = libelle(option)
                textSize = 16f
                setTextColor(Color.parseColor(ENCRE))
                isChecked = option == actuel
                setPadding(dp(8), dp(12), 0, dp(12))
                buttonTintList = ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf(-android.R.attr.state_checked)
                    ),
                    intArrayOf(Color.parseColor(BLEU), Color.parseColor("#757575"))
                )
            })
        }
        setOnCheckedChangeListener { groupe, idCoche ->
            val rang = groupe.indexOfChild(groupe.findViewById<View>(idCoche))
            if (rang in options.indices) onChoix(options[rang])
        }
    }

    private fun espacement(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(16)
        )
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

    private fun titreSection(@StringRes texte: Int): TextView = TextView(this).apply {
        setText(texte)
        textSize = 17f
        setTextColor(Color.parseColor(BLEU))
        setTypeface(null, Typeface.BOLD)
        setPadding(0, 0, 0, dp(4))
    }

    private fun explication(@StringRes texte: Int): TextView = TextView(this).apply {
        setText(texte)
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
        @StringRes libelleRes: Int,
        actifAuDepart: Boolean,
        onChange: (Boolean) -> Unit
    ): View = Switch(this).apply {
        val libelle = getString(libelleRes)
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
