package com.potomitan.luxreunion

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.potomitan.luxreunion.Charte.dp
import com.potomitan.luxreunion.noyau.ConsignesCompteRendu
import com.potomitan.luxreunion.noyau.Normaliseur
import com.potomitan.luxreunion.noyau.Reunion

/**
 * L'accueil : deux choses à faire, en gros, et les réunions passées. Rien
 * d'autre : la secrétaire doit pouvoir s'en servir sans explication.
 */
class AccueilActivity : Activity() {

    private val app get() = ReunionApp.instance
    private lateinit var liste: LinearLayout
    private lateinit var haut: LinearLayout
    private var listeA = 0L
    /** Pendant une réunion, l'état change dix fois par seconde : la liste, lue sur le disque, suit au plus toutes les 2 s. */
    private val rafraichir = {
        remplirHaut()
        if (System.currentTimeMillis() - listeA > 2_000) remplirListe()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val colonne = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(24))
        }
        haut = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        colonne.addView(haut)

        colonne.addView(Charte.carte(this).apply {
            addView(Charte.titre(this@AccueilActivity, "🎙️  Réunion en direct", 22f))
            addView(Charte.texte(this@AccueilActivity,
                "J'écris ce qui se dit pendant la réunion, avec la traduction en français. À la fin, je prépare le compte rendu.", 16f, Charte.GRIS)
                .also { Charte.marge(it, haut = 6, bas = 14) })
            addView(Charte.boutonPrincipal(this@AccueilActivity, "Commencer une réunion") {
                if (EtatDirect.enCours) ouvrirDirect()
                else startActivity(Intent(this@AccueilActivity, PreparationActivity::class.java)
                    .putExtra(PreparationActivity.EXTRA_MODE, Reunion.Mode.DIRECT.name))
            })
        })
        colonne.addView(Charte.carte(this).apply {
            addView(Charte.titre(this@AccueilActivity, "📁  Un enregistrement", 20f))
            addView(Charte.texte(this@AccueilActivity,
                "Vous avez déjà un fichier audio ou vidéo de la réunion : je le transcris et je prépare le compte rendu.",
                16f, Charte.GRIS).also { Charte.marge(it, haut = 6, bas = 14) })
            addView(Charte.boutonSecondaire(this@AccueilActivity, "Choisir un fichier") {
                startActivity(Intent(this@AccueilActivity, PreparationActivity::class.java)
                    .putExtra(PreparationActivity.EXTRA_MODE, Reunion.Mode.FICHIER.name))
            })
        })

        colonne.addView(Charte.rubrique(this, "Mes réunions"))
        liste = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        colonne.addView(liste)

        colonne.addView(Charte.texte(this,
            "Transcription et traduction : LuxASR, Université du Luxembourg.\nCompte rendu : Claude (Anthropic) ou Mistral, selon les réglages.\nVersion ${BuildConfig.VERSION_NAME}",
            12f, Charte.GRIS_CLAIR).apply {
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(8))
            // L'accès aux réglages d'installation (clé d'API) : un appui long, loin des doigts pressés.
            setOnLongClickListener {
                startActivity(Intent(this@AccueilActivity, ReglagesActivity::class.java)
                    .putExtra(ReglagesActivity.EXTRA_ADMIN, true))
                true
            }
        })

        val defilement = ScrollView(this).apply { addView(colonne) }
        Charte.page(this, "Lëtzebuergesch Réunion", defilement, retour = false,
                    action = "Réglages" to { startActivity(Intent(this, ReglagesActivity::class.java)) })
    }

    override fun onResume() {
        super.onResume()
        app.reprendre()
        Ui.abonner(rafraichir)
        remplir()
    }

    override fun onPause() {
        super.onPause()
        Ui.desabonner(rafraichir)
    }

    private fun ouvrirDirect() = startActivity(Intent(this, DirectActivity::class.java))

    private fun remplir() {
        remplirHaut()
        remplirListe()
    }

    private var cleHaut = ""

    private fun remplirHaut() {
        // Reconstruit seulement quand ce qu'il montre change (une fois par seconde en réunion).
        val cle = "${EtatDirect.enCours}|${EtatDirect.secondes.toLong()}|${app.reglages.accordVu}"
        if (cle == cleHaut) return
        cleHaut = cle
        haut.removeAllViews()
        if (EtatDirect.enCours) {
            haut.addView(Charte.carte(this).apply {
                background = Charte.fond(this@AccueilActivity, Charte.BLEU_PALE, 16, bordure = Charte.BLEU)
                addView(Charte.titre(this@AccueilActivity, "🔴  Réunion en cours · ${Normaliseur.horloge(EtatDirect.secondes)}", 18f))
                addView(Charte.boutonPrincipal(this@AccueilActivity, "Revenir à la réunion") { ouvrirDirect() }
                    .also { Charte.marge(it, haut = 12) })
            })
        }
        if (!app.reglages.accordVu) {
            haut.addView(Charte.carte(this).apply {
                background = Charte.fond(this@AccueilActivity, Charte.ORANGE_PALE, 16)
                addView(Charte.titre(this@AccueilActivity, "Avant votre première réunion", 18f))
                addView(Charte.texte(this@AccueilActivity,
                    "• Prévenez les participants que la réunion est enregistrée, et obtenez leur accord.\n" +
                    "• Le son part à l'Université du Luxembourg (LuxASR) pour être transcrit et traduit. Elle l'efface après traitement.\n" +
                    "• Le texte part chez Anthropic (Claude) ou Mistral pour rédiger le compte rendu.\n" +
                    "• Sur ce téléphone, l'enregistrement est effacé dès que la transcription est faite.\n" +
                    "• La traduction et le compte rendu peuvent contenir des erreurs : relisez avant de diffuser.",
                    15f).also { Charte.marge(it, haut = 8, bas = 12) })
                addView(Charte.boutonPrincipal(this@AccueilActivity, "J'ai compris", Charte.ORANGE) {
                    app.reglages.accordVu = true
                    remplir()
                })
            })
        }
    }

    private fun remplirListe() {
        listeA = System.currentTimeMillis()
        liste.removeAllViews()
        val reunions = app.registre.toutes()
        if (reunions.isEmpty()) {
            liste.addView(Charte.texte(this, "Vos réunions apparaîtront ici.", 15f, Charte.GRIS_CLAIR).apply {
                setPadding(dp(20), dp(8), dp(20), dp(8))
            })
        }
        for (r in reunions) liste.addView(ligne(r))
    }

    private fun ligne(r: Reunion): View {
        val (libelle, couleur, fondCouleur) = statut(r)
        return Charte.carte(this).apply {
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = Charte.touchable(Charte.fond(this@AccueilActivity, Charte.BLANC, 16), clair = false)
            isClickable = true
            setOnClickListener {
                if (r.etape == Reunion.Etape.ECOUTE && EtatDirect.reunionId == r.id) ouvrirDirect()
                else startActivity(Intent(this@AccueilActivity, ReunionActivity::class.java).putExtra(ReunionActivity.EXTRA_ID, r.id))
            }
            addView(Charte.texte(this@AccueilActivity, r.titre.ifBlank { "Réunion" }, 17f, Charte.ENCRE, gras = true).apply {
                maxLines = 2
            })
            val details = buildString {
                append(ReunionApp.dateCourte(r.creee))
                if (r.dureeS >= 60) append(" · ").append(ConsignesCompteRendu.duree(r.dureeS))
                if (r.mode == Reunion.Mode.FICHIER) append(" · fichier")
            }
            addView(Charte.texte(this@AccueilActivity, details, 14f, Charte.GRIS).also { Charte.marge(it, haut = 2, bas = 8) })
            addView(Charte.pastille(this@AccueilActivity, libelle, couleur, fondCouleur),
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    companion object {
        fun statut(r: Reunion): Triple<String, Int, Int> = when {
            r.etape == Reunion.Etape.ECOUTE && EtatDirect.reunionId == r.id -> Triple("En cours", Charte.BLEU, Charte.BLEU_PALE)
            r.erreur != null -> Triple("À reprendre", Charte.ROUGE, Charte.ROUGE_PALE)
            r.etape == Reunion.Etape.PRETE -> Triple("Compte rendu prêt", Charte.VERT, Charte.VERT_PALE)
            r.sansCle -> Triple("Transcription prête", Charte.VERT, Charte.VERT_PALE)
            else -> Triple("En préparation…", Charte.ORANGE, Charte.ORANGE_PALE)
        }
    }
}
