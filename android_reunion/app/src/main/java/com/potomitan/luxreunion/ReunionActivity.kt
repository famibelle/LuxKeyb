package com.potomitan.luxreunion

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.potomitan.luxreunion.Charte.dp
import com.potomitan.luxreunion.noyau.ConsignesCompteRendu
import com.potomitan.luxreunion.noyau.Reunion
import com.potomitan.luxreunion.noyau.TexteTranscription

/**
 * Une réunion après coup : où en est sa préparation, puis le compte rendu
 * (modifiable : la secrétaire le corrige avant de l'envoyer), la
 * transcription complète et la traduction du direct.
 */
class ReunionActivity : Activity() {

    private val app get() = ReunionApp.instance
    private lateinit var id: String
    private lateinit var r: Reunion
    private lateinit var etatCadre: LinearLayout
    private lateinit var onglets: LinearLayout
    private lateinit var corps: LinearLayout
    private var onglet = Onglet.COMPTE_RENDU
    private var champCompteRendu: EditText? = null
    private var brouillon: TextView? = null
    private var cleAffichee = ""
    private val rafraichir = { maj() }

    private enum class Onglet(val libelle: String) {
        COMPTE_RENDU("Compte rendu"), TRANSCRIPTION("Transcription"), TRADUCTION("Traduction")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        id = intent.getStringExtra(EXTRA_ID) ?: run { finish(); return }
        r = app.registre.lire(id) ?: run { finish(); return }

        val colonne = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(24))
        }
        colonne.addView(Charte.texte(this, ReunionApp.date(r.creee).replaceFirstChar { it.uppercase() } +
            (if (r.dureeS >= 60) " · " + ConsignesCompteRendu.duree(r.dureeS) else ""), 14f, Charte.GRIS).apply {
            setPadding(dp(20), dp(8), dp(20), dp(4))
        })
        etatCadre = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        colonne.addView(etatCadre)
        onglets = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(12), dp(16), 0)
        }
        colonne.addView(onglets)
        corps = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        colonne.addView(corps)
        colonne.addView(Charte.texte(this, "Supprimer cette réunion", 15f, Charte.ROUGE, gras = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(28), dp(16), dp(12))
            setOnClickListener { confirmerSuppression() }
        })
        Charte.page(this, r.titre.ifBlank { "Réunion" }, ScrollView(this).apply { addView(colonne) })
        construireOnglets()
    }

    override fun onResume() {
        super.onResume()
        Ui.abonner(rafraichir)
        cleAffichee = ""
        maj()
    }

    override fun onPause() {
        super.onPause()
        Ui.desabonner(rafraichir)
        sauverCorrections()
    }

    /** Les corrections de la secrétaire, gardées dès qu'elle quitte l'écran. */
    private fun sauverCorrections() {
        val champ = champCompteRendu ?: return
        val texte = champ.text.toString()
        val f = r.fichierCompteRendu
        if (f.exists() && texte.isNotBlank() && texte != f.readText()) Reunion.ecrireAtomique(f, texte)
    }

    // --- État ---------------------------------------------------------------------

    private fun maj() {
        r = app.registre.lire(id) ?: run { finish(); return }
        val progres = ServiceTraitement.progres[id]
        // Le compte rendu qui s'écrit, sans tout reconstruire.
        progres?.brouillon?.let { b -> brouillon?.text = b }
        val cle = "${r.etape}|${r.erreur}|${r.sansCle}|${r.depuisDirect}|${progres?.texte}|${progres?.fraction?.times(100)?.toInt()}|${onglet}"
        if (cle == cleAffichee) return
        val etapeAvant = cleAffichee.substringBefore('|')
        cleAffichee = cle
        etatCadre.removeAllViews()
        if (r.etape != Reunion.Etape.PRETE || r.erreur != null) etatCadre.addView(carteEtat(progres))
        r.erreur?.let { e ->
            etatCadre.addView(Charte.encadre(this, e, Charte.ROUGE_PALE, Charte.ROUGE))
            etatCadre.addView(Charte.boutonPrincipal(this, "Réessayer") {
                r.erreur = null
                r.enregistrer()
                ServiceTraitement.lancer(this)
                maj()
            }.also { pousser(it) })
        }
        if (r.sansCle && r.erreur == null) {
            etatCadre.addView(Charte.encadre(this,
                "Le compte rendu automatique n'est pas activé sur ce téléphone. Vous pouvez le faire rédiger dans l'application Claude : " +
                "j'y envoie la transcription avec les consignes, il ne vous reste qu'à valider l'envoi.", Charte.ORANGE_PALE))
            etatCadre.addView(Charte.boutonPrincipal(this, "Rédiger dans Claude", Charte.ORANGE) { redigerDansClaude() }
                .also { pousser(it) })
        }
        // Le contenu de l'onglet ne se reconstruit qu'au changement d'étape :
        // la secrétaire peut être en train de corriger le compte rendu.
        if (etapeAvant != r.etape.name || corps.childCount == 0 || brouillon != null && r.etape == Reunion.Etape.PRETE) remplirOnglet()
    }

    private fun pousser(v: View) {
        v.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(dp(16), dp(4), dp(16), dp(8)) }
    }

    private fun carteEtat(progres: ServiceTraitement.Progres?): View = Charte.carte(this).apply {
        addView(Charte.titre(this@ReunionActivity, "Préparation du compte rendu", 18f).also { Charte.marge(it, bas = 8) })
        val e = r.etape
        val enCours = progres != null
        addView(ligneEtape(true, if (r.mode == Reunion.Mode.FICHIER) "Fichier reçu" else "Réunion enregistrée", null))
        val transcrite = e == Reunion.Etape.REDACTION || e == Reunion.Etape.PRETE
        when {
            r.depuisDirect -> addView(ligneEtape(null, "Transcription complète",
                "Faite à partir du direct : les voix n'y sont pas séparées."))
            transcrite -> addView(ligneEtape(true, "Transcription complète (qui a dit quoi)", null))
            else -> addView(ligneEtape(false, "Transcription complète (qui a dit quoi)",
                if (enCours && progres!!.etape != Reunion.Etape.REDACTION) avecPourcentage(progres) else "En attente…"))
        }
        when {
            e == Reunion.Etape.PRETE -> addView(ligneEtape(true, "Compte rendu", null))
            r.sansCle -> addView(ligneEtape(null, "Compte rendu", "Pas de rédaction automatique sur ce téléphone."))
            e == Reunion.Etape.REDACTION -> addView(ligneEtape(false, "Compte rendu",
                if (enCours) progres!!.texte else "En attente…"))
            else -> addView(ligneEtape(false, "Compte rendu", null, actif = false))
        }
        if (r.erreur == null && !r.sansCle && e != Reunion.Etape.PRETE) {
            addView(Charte.texte(this@ReunionActivity,
                "Vous pouvez fermer l'application : je vous préviens quand le compte rendu est prêt.", 13f, Charte.GRIS_CLAIR)
                .also { Charte.marge(it, haut = 10) })
        }
    }

    private fun avecPourcentage(p: ServiceTraitement.Progres): String =
        p.fraction?.let { "${p.texte} ${(it * 100).toInt()} %" } ?: p.texte

    /** fait : vrai (✓), faux (en cours), null (sauté). */
    private fun ligneEtape(fait: Boolean?, titre: String, detail: String?, actif: Boolean = true): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
            val (signe, couleur) = when (fait) {
                true -> "✓" to Charte.VERT
                null -> "–" to Charte.GRIS_CLAIR
                false -> (if (actif) "⏳" else "○") to (if (actif) Charte.ORANGE else Charte.GRIS_CLAIR)
            }
            addView(Charte.texte(this@ReunionActivity, signe, 18f, couleur, gras = true).apply { gravity = Gravity.CENTER },
                    LinearLayout.LayoutParams(dp(32), ViewGroup.LayoutParams.WRAP_CONTENT))
            val textes = LinearLayout(this@ReunionActivity).apply { orientation = LinearLayout.VERTICAL }
            textes.addView(Charte.texte(this@ReunionActivity, titre, 16f, if (actif) Charte.ENCRE else Charte.GRIS_CLAIR, gras = true))
            if (detail != null) textes.addView(Charte.texte(this@ReunionActivity, detail, 14f, Charte.GRIS))
            addView(textes, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

    // --- Onglets --------------------------------------------------------------------

    private fun ongletsVisibles(): List<Onglet> =
        if (r.mode == Reunion.Mode.DIRECT) Onglet.values().toList() else listOf(Onglet.COMPTE_RENDU, Onglet.TRANSCRIPTION)

    /** Comme les onglets du clavier : l'onglet choisi en orange, sur fond orange pâle, souligné. */
    private fun construireOnglets() {
        onglets.removeAllViews()
        for (o in ongletsVisibles()) {
            val choisi = o == onglet
            onglets.addView(TextView(this).apply {
                text = o.libelle
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                setTextColor(if (choisi) Charte.ORANGE else Charte.GRIS)
                setPadding(dp(4), dp(12), dp(4), dp(12))
                background = if (choisi) android.graphics.drawable.LayerDrawable(arrayOf(
                    Charte.fond(this@ReunionActivity, Charte.ORANGE_PALE, 10),
                    android.graphics.drawable.GradientDrawable().apply { setColor(Charte.ORANGE) })).apply {
                        setLayerInset(1, dp(24), dp(44), dp(24), 0)
                    } else Charte.fond(this@ReunionActivity, Charte.BLANC, 10)
                setOnClickListener {
                    sauverCorrections()
                    onglet = o
                    construireOnglets()
                    remplirOnglet()
                }
            }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
    }

    private fun remplirOnglet() {
        corps.removeAllViews()
        champCompteRendu = null
        brouillon = null
        when (onglet) {
            Onglet.COMPTE_RENDU -> ongletCompteRendu()
            Onglet.TRANSCRIPTION -> ongletTexte(
                if (r.fichierTranscription.exists()) r.fichierTranscription.readText()
                else if (r.depuisDirect) TexteTranscription.depuisDirect(r.lireDirect()) else null,
                "La transcription complète sera ici dès qu'elle sera prête.", "Transcription")
            Onglet.TRADUCTION -> ongletTexte(
                r.lireDirect().takeIf { it.isNotEmpty() }?.let { TexteTranscription.traductionDirect(it) },
                "Rien n'a été traduit pendant la réunion.", "Traduction")
        }
    }

    private fun ongletCompteRendu() {
        val carte = Charte.carte(this)
        corps.addView(carte)
        val f = r.fichierCompteRendu
        if (r.etape == Reunion.Etape.PRETE && f.exists()) {
            carte.addView(Charte.texte(this, "Relisez et corrigez si besoin : vos modifications sont gardées.", 14f, Charte.GRIS))
            val champ = EditText(this).apply {
                setText(f.readText())
                setTextColor(Charte.ENCRE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setLineSpacing(0f, 1.2f)
                background = null
                gravity = Gravity.TOP or Gravity.START
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                setPadding(0, dp(8), 0, dp(8))
            }
            champCompteRendu = champ
            carte.addView(champ)
            corps.addView(Charte.boutonPrincipal(this, "✉️  Envoyer le compte rendu") {
                sauverCorrections()
                partager("Compte rendu — ${r.titre}", champ.text.toString())
            }.also { pousser(it) })
            corps.addView(Charte.boutonSecondaire(this, "Copier") {
                copier(champ.text.toString())
            }.also { pousser(it) })
            corps.addView(Charte.boutonSecondaire(this, "Rédiger à nouveau", Charte.GRIS) { confirmerNouvelleRedaction() }
                .also { pousser(it) })
            return
        }
        val b = ServiceTraitement.progres[id]?.brouillon
        if (r.etape == Reunion.Etape.REDACTION && !r.sansCle) {
            carte.addView(Charte.texte(this, "Claude rédige…", 14f, Charte.GRIS))
            brouillon = Charte.texte(this, b ?: "", 16f, Charte.TEXTE).also { carte.addView(it) }
        } else {
            carte.addView(Charte.texte(this, if (r.sansCle) "Pas de compte rendu automatique : voir ci-dessus."
                                             else "Le compte rendu sera ici dès qu'il sera prêt.", 15f, Charte.GRIS_CLAIR))
        }
    }

    private fun ongletTexte(texte: String?, vide: String, nom: String) {
        val carte = Charte.carte(this)
        corps.addView(carte)
        if (texte.isNullOrBlank()) {
            carte.addView(Charte.texte(this, vide, 15f, Charte.GRIS_CLAIR))
            return
        }
        carte.addView(Charte.texte(this, texte, 15f, Charte.TEXTE).apply { setTextIsSelectable(true) })
        corps.addView(Charte.boutonSecondaire(this, "Envoyer la ${nom.lowercase()}") {
            partager("$nom — ${r.titre}", texte)
        }.also { pousser(it) })
    }

    // --- Actions ---------------------------------------------------------------------

    private fun partager(sujet: String, texte: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, sujet)
            putExtra(Intent.EXTRA_TEXT, texte)
        }
        startActivity(Intent.createChooser(i, "Envoyer avec…"))
    }

    private fun copier(texte: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(r.titre, texte))
        Toast.makeText(this, "Copié.", Toast.LENGTH_SHORT).show()
    }

    /** Sans clé d'API : la transcription et les consignes partent dans l'application Claude. */
    private fun redigerDansClaude() {
        val (texte, source) = ServiceTraitement.sourceDuCompteRendu(r)
        val reglages = app.reglages
        val tout = ConsignesCompteRendu.systeme(reglages.modeleCompteRendu) + "\n\n" +
            ConsignesCompteRendu.message(ServiceTraitement.infos(r, reglages), texte, source)
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(r.titre, tout))
        val envoi = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, tout)
        }
        try {
            startActivity(Intent(envoi).setPackage("com.anthropic.claude"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Le texte est copié. Choisissez Claude, ou collez-le sur claude.ai.", Toast.LENGTH_LONG).show()
            startActivity(Intent.createChooser(envoi, "Rédiger le compte rendu avec…"))
        }
    }

    private fun confirmerNouvelleRedaction() {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("Rédiger à nouveau ?")
            .setMessage("Claude rédige un nouveau compte rendu. Vos corrections sur celui-ci seront perdues.")
            .setPositiveButton("Rédiger à nouveau") { _, _ ->
                champCompteRendu = null
                r.etape = Reunion.Etape.REDACTION
                r.erreur = null
                r.sansCle = false
                r.enregistrer()
                ServiceTraitement.lancer(this)
                cleAffichee = ""
                maj()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun confirmerSuppression() {
        if (EtatDirect.reunionId == id && EtatDirect.enCours) {
            Toast.makeText(this, "Terminez d'abord la réunion.", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("Supprimer cette réunion ?")
            .setMessage("Le compte rendu, la transcription et la traduction seront effacés de ce téléphone.")
            .setPositiveButton("Supprimer") { _, _ ->
                champCompteRendu = null
                app.registre.supprimer(r)
                finish()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    companion object {
        const val EXTRA_ID = "id"
    }
}
