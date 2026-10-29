package com.potomitan.luxreunion

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import com.potomitan.luxreunion.Charte.dp
import com.potomitan.luxreunion.noyau.Redaction
import com.potomitan.luxreunion.noyau.Fournisseur
import kotlin.concurrent.thread

/**
 * Les réglages de la secrétaire (son nom, ses mots, son modèle de compte
 * rendu), et, cachés derrière un appui long sur le pied de l'accueil, ceux de
 * la personne qui installe l'application : la clé d'API Claude, le modèle,
 * la transcription complète après le direct.
 */
class ReglagesActivity : Activity() {

    private val reglages get() = ReunionApp.instance.reglages

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val colonne = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(24))
        }

        colonne.addView(section("Votre nom", "Il apparaît comme rédacteur du compte rendu.",
            Charte.champ(this, "Marie Schmit", valeur = reglages.redacteur)) { reglages.redacteur = it })
        colonne.addView(section("Mots à bien écrire",
            "Noms de personnes, d'entreprises, de lieux, sigles… Un par ligne. Ils aident la transcription et le compte rendu à les écrire correctement.",
            Charte.champ(this, "BCEE\nKirchberg\nComité exécutif", lignes = 4, valeur = reglages.glossaire)) { reglages.glossaire = it })
        colonne.addView(section("Modèle de compte rendu",
            "Facultatif. Collez un ancien compte rendu, ou décrivez la présentation souhaitée : le compte rendu en suivra la forme.",
            Charte.champ(this, "Procès-verbal de la réunion du…", lignes = 5, valeur = reglages.modeleCompteRendu)) {
            reglages.modeleCompteRendu = it
        })

        colonne.addView(Charte.carte(this).apply {
            addView(Charte.titre(this@ReglagesActivity, "Vos données", 18f))
            addView(Charte.texte(this@ReglagesActivity,
                "• Pendant la réunion et après, le son est envoyé à LuxASR (Université du Luxembourg), qui le transcrit et le traduit, puis l'efface.\n" +
                "• Le texte de la réunion est envoyé à ${reglages.fournisseur.nom} pour rédiger le compte rendu.\n" +
                "• Sur ce téléphone, l'enregistrement est effacé dès que la transcription est faite. Les comptes rendus restent jusqu'à ce que vous supprimiez la réunion.\n" +
                "• Rien n'est sauvegardé ailleurs, ni transféré vers un nouveau téléphone.",
                15f).also { Charte.marge(it, haut = 8) })
        })

        colonne.addView(administration())

        Charte.page(this, "Réglages", ScrollView(this).apply { addView(colonne) })
    }

    private fun section(titre: String, aide: String, champ: EditText, ecrire: (String) -> Unit) =
        Charte.carte(this).apply {
            addView(Charte.titre(this@ReglagesActivity, titre, 18f))
            addView(Charte.texte(this@ReglagesActivity, aide, 14f, Charte.GRIS).also { Charte.marge(it, haut = 4) })
            champ.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) { ecrire(s?.toString() ?: "") }
            })
            addView(champ)
        }

    /**
     * Le compte rendu automatique : le service (Claude ou Mistral), sa clé
     * d'API et son modèle. La clé n'est jamais dans l'APK : on la colle ici,
     * elle est chiffrée par le Keystore du téléphone et ne s'affiche plus.
     */
    private fun administration() = Charte.carte(this).apply {
        background = Charte.fond(this@ReglagesActivity, Charte.BLANC, 16, bordure = Charte.ORANGE)
        addView(Charte.titre(this@ReglagesActivity, "Compte rendu automatique", 18f))
        val aide = Charte.texte(this@ReglagesActivity, "", 14f, Charte.GRIS).also { Charte.marge(it, haut = 4) }
        addView(aide)

        // Le service : deux boutons, celui choisi en bleu plein.
        val choix = LinearLayout(this@ReglagesActivity).apply { orientation = LinearLayout.HORIZONTAL }
        addView(choix)
        Charte.marge(choix, haut = 10)
        val cle = Charte.champ(this@ReglagesActivity, "").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        addView(Charte.texte(this@ReglagesActivity, "Clé d'API", 14f, Charte.GRIS).also { Charte.marge(it, haut = 12) })
        addView(cle)
        val modele = Charte.champ(this@ReglagesActivity, reglages.fournisseur.modele, valeur = reglages.modele)
        fun afficherFournisseur() {
            val f = reglages.fournisseur
            aide.text = "Clé d'API ${f.nom} (${f.console}). Elle reste chiffrée sur ce téléphone. " +
                "Fixez un plafond de dépenses dans la console du service."
            cle.hint = if (reglages.aUneCle) "•••••• (clé enregistrée)" else "Collez la clé ici"
            modele.hint = f.modele
            modele.setText(reglages.modele)
            choix.removeAllViews()
            for (autre in Fournisseur.values()) {
                val actif = autre == f
                val b = if (actif) Charte.boutonPrincipal(this@ReglagesActivity, autre.nom) {}
                        else Charte.boutonSecondaire(this@ReglagesActivity, autre.nom) {}
                b.setOnClickListener {
                    if (autre != reglages.fournisseur) {
                        // Une clé ne vaut que pour son service : changer de service l'efface.
                        reglages.fournisseur = autre
                        reglages.cle = null
                        afficherFournisseur()
                    }
                }
                choix.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { setMargins(dp(3), 0, dp(3), 0) })
            }
        }
        afficherFournisseur()
        addView(Charte.texte(this@ReglagesActivity, "Modèle", 14f, Charte.GRIS).also { Charte.marge(it, haut = 12) })
        addView(modele)
        val etat = Charte.texte(this@ReglagesActivity, if (reglages.aUneCle) "Une clé est enregistrée." else "Aucune clé : le compte rendu ne se rédige pas tout seul.",
                                14f, Charte.GRIS).also { Charte.marge(it, haut = 10, bas = 8) }
        addView(etat)
        addView(Charte.boutonPrincipal(this@ReglagesActivity, "Enregistrer et vérifier") {
            val saisie = cle.text.toString().trim()
            reglages.modele = modele.text.toString()
            val m = reglages.modele
            val aVerifier = saisie.ifBlank { reglages.cle ?: "" }
            if (aVerifier.isBlank()) { etat.text = "Saisissez une clé."; return@boutonPrincipal }
            etat.text = "Vérification…"
            etat.setTextColor(Charte.GRIS)
            thread {
                val resultat = try {
                    Redaction.creer(reglages.fournisseur, aVerifier, m).verifierCle()
                    null
                } catch (e: Redaction.Erreur) { e.message }
                Ui.main.post {
                    if (resultat == null) {
                        if (saisie.isNotBlank()) reglages.cle = saisie
                        cle.setText("")
                        cle.hint = "•••••• (clé enregistrée)"
                        etat.text = "✓ Clé valide et enregistrée."
                        etat.setTextColor(Charte.VERT)
                        // Les réunions qui attendaient une clé peuvent repartir.
                        for (r in ReunionApp.instance.registre.toutes()) if (r.sansCle) { r.sansCle = false; r.enregistrer() }
                        ServiceTraitement.lancer(this@ReglagesActivity)
                    } else {
                        etat.text = "✗ $resultat"
                        etat.setTextColor(Charte.ROUGE)
                    }
                }
            }
        }.also { Charte.marge(it, haut = 4) })
        addView(Charte.boutonSecondaire(this@ReglagesActivity, "Effacer la clé", Charte.ROUGE) {
            reglages.cle = null
            cle.hint = "Collez la clé ici"
            etat.text = "Clé effacée."
            etat.setTextColor(Charte.GRIS)
        }.also { Charte.marge(it, haut = 8) })

        val ligne = LinearLayout(this@ReglagesActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, 0)
        }
        ligne.addView(Charte.texte(this@ReglagesActivity,
            "Après une réunion en direct, faire la transcription complète (voix séparées). Désactivé, le compte rendu part du direct.",
            14f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        ligne.addView(Switch(this@ReglagesActivity).apply {
            isChecked = reglages.lotsApresDirect
            setOnCheckedChangeListener { _, v -> reglages.lotsApresDirect = v }
        })
        addView(ligne)
    }

    companion object {
        const val EXTRA_ADMIN = "admin"
    }
}
