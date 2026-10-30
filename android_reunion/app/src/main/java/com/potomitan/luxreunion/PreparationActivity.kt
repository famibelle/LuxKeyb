package com.potomitan.luxreunion

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.potomitan.luxreunion.Charte.dp
import com.potomitan.luxreunion.noyau.ConsignesCompteRendu
import com.potomitan.luxreunion.noyau.LotsLuxAsr
import com.potomitan.luxreunion.noyau.Reunion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Avant la réunion : un titre (déjà rempli), et, si elle en a le temps, les
 * participants et l'ordre du jour. Tout est facultatif sauf le bouton.
 *
 * Les participants servent deux fois : leurs noms partent à LuxASR pour être
 * bien orthographiés (paramètre `prompt`), et leur nombre l'aide à séparer
 * les locuteurs (`num_speakers`), ce qui change tout quand les voix se ressemblent
 * (skill transcription, § 3).
 */
class PreparationActivity : Activity() {

    private lateinit var mode: Reunion.Mode
    private lateinit var titre: EditText
    private lateinit var participants: EditText
    private lateinit var ordre: EditText
    private var fichier: Uri? = null
    private var nomFichier = ""
    private var tailleFichier = 0L
    private var vueFichier: TextView? = null
    private lateinit var bouton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = runCatching { Reunion.Mode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: "") }.getOrDefault(Reunion.Mode.DIRECT)
        val direct = mode == Reunion.Mode.DIRECT

        val colonne = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(16))
        }
        if (!direct) {
            colonne.addView(Charte.carte(this).apply {
                addView(Charte.titre(this@PreparationActivity, "L'enregistrement", 18f))
                vueFichier = Charte.texte(this@PreparationActivity, "Aucun fichier choisi.", 15f, Charte.GRIS)
                    .also { Charte.marge(it, haut = 6, bas = 12) }
                addView(vueFichier)
                addView(Charte.boutonSecondaire(this@PreparationActivity, "Choisir le fichier") { choisirFichier() })
            })
        }
        val jour = SimpleDateFormat("EEEE d MMMM", Locale.FRENCH).format(Date())
        colonne.addView(Charte.carte(this).apply {
            addView(Charte.titre(this@PreparationActivity, "Titre", 18f))
            titre = Charte.champ(this@PreparationActivity, "Par exemple : Comité de direction", valeur = "Réunion du $jour")
            addView(titre)
        })
        colonne.addView(Charte.carte(this).apply {
            addView(Charte.titre(this@PreparationActivity, "Participants", 18f))
            addView(Charte.texte(this@PreparationActivity,
                "Facultatif. Un nom par ligne. Les noms aident à les écrire correctement et à distinguer les locuteurs.",
                14f, Charte.GRIS).also { Charte.marge(it, haut = 4) })
            participants = Charte.champ(this@PreparationActivity, "Anne Wagner\nJos Weber", lignes = 3)
            addView(participants)
        })
        colonne.addView(Charte.carte(this).apply {
            addView(Charte.titre(this@PreparationActivity, "Ordre du jour", 18f))
            addView(Charte.texte(this@PreparationActivity, "Facultatif. Le compte rendu suivra ces points.", 14f, Charte.GRIS)
                .also { Charte.marge(it, haut = 4) })
            ordre = Charte.champ(this@PreparationActivity, "1. Budget\n2. Recrutements\n3. Divers", lignes = 3)
            addView(ordre)
        })
        if (direct) {
            colonne.addView(Charte.encadre(this,
                "Pensez à prévenir les participants que la réunion est enregistrée et traduite automatiquement.",
                Charte.ORANGE_PALE))
        }

        bouton = Charte.boutonPrincipal(this, if (direct) "🎙️  Démarrer la réunion" else "Transcrire") {
            if (direct) demarrerDirect() else transcrireFichier()
        }
        Charte.page(this, if (direct) "Nouvelle réunion" else "Un enregistrement",
                    ScrollView(this).apply { addView(colonne) }, bas = Charte.barreBasse(this, bouton))
    }

    /** Un nom par ligne, ou séparés par des virgules. */
    private fun lignes(t: EditText) = ConsignesCompteRendu.listeMots(t.text.toString())

    private fun nouvelleReunion(): Reunion =
        ReunionApp.instance.registre.nouvelle(System.currentTimeMillis(), mode).apply {
            titre = this@PreparationActivity.titre.text.toString().trim().ifBlank { "Réunion" }
            participants = lignes(this@PreparationActivity.participants)
            ordreDuJour = ordre.text.toString().trim()
        }

    // --- Direct ---------------------------------------------------------------

    private fun demarrerDirect() {
        if (EtatDirect.enCours) {
            startActivity(Intent(this, DirectActivity::class.java))
            finish()
            return
        }
        val manquantes = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            manquantes += Manifest.permission.RECORD_AUDIO
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            manquantes += Manifest.permission.POST_NOTIFICATIONS
        }
        if (manquantes.isNotEmpty()) { requestPermissions(manquantes.toTypedArray(), DEMANDE_MICRO); return }
        val r = nouvelleReunion().apply {
            etape = Reunion.Etape.ECOUTE
            enregistrer()
        }
        EtatDirect.commencer(r.id)
        ServiceEcoute.demarrer(this, r.id)
        startActivity(Intent(this, DirectActivity::class.java))
        finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode != DEMANDE_MICRO) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            demarrerDirect()
            return
        }
        // Refusé : on explique, et on ouvre les réglages si Android ne redemandera plus.
        val definitif = !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("Le micro est nécessaire")
            .setMessage("Sans accès au micro, je ne peux pas écouter la réunion." +
                        if (definitif) "\n\nAutorisez le micro dans les réglages du téléphone : Autorisations › Micro." else "")
            .setPositiveButton(if (definitif) "Ouvrir les réglages" else "Réessayer") { _, _ ->
                if (definitif) startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                else demarrerDirect()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    // --- Fichier ----------------------------------------------------------------

    private fun choisirFichier() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "video/*"))
        }, DEMANDE_FICHIER)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != DEMANDE_FICHIER || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        fichier = uri
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { nomFichier = c.getString(it) ?: "" }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { tailleFichier = c.getLong(it) }
            }
        }
        vueFichier?.text = "$nomFichier · ${"%.1f".format(Locale.FRENCH, tailleFichier / 1e6)} Mo"
        vueFichier?.setTextColor(Charte.ENCRE)
    }

    private fun transcrireFichier() {
        val uri = fichier
        if (uri == null) { Toast.makeText(this, "Choisissez d'abord le fichier.", Toast.LENGTH_LONG).show(); return }
        if (tailleFichier > TAILLE_MAX) {
            Toast.makeText(this, "Ce fichier dépasse 200 Mo : enregistrez-le en qualité plus faible, ou coupez-le.", Toast.LENGTH_LONG).show()
            return
        }
        val type = contentResolver.getType(uri)?.takeIf { it.startsWith("audio/") || it.startsWith("video/") }
            ?: LotsLuxAsr.typeDe(nomFichier)
        if (type == null) { Toast.makeText(this, "Ce fichier n'est ni un son ni une vidéo.", Toast.LENGTH_LONG).show(); return }
        bouton.isEnabled = false
        bouton.text = "Copie du fichier…"
        val r = nouvelleReunion()
        thread {
            try {
                // Une copie dans le dossier de la réunion : le traitement doit
                // pouvoir reprendre après un redémarrage, quand l'accès au
                // fichier choisi aura été perdu. Elle est effacée après la transcription.
                val ext = nomFichier.substringAfterLast('.', "").lowercase().takeIf { it.length in 1..5 } ?: "bin"
                val copie = r.fichier("source.$ext")
                contentResolver.openInputStream(uri)!!.use { entree -> copie.outputStream().use { entree.copyTo(it, 256 * 1024) } }
                r.nomSource = nomFichier
                r.parties = listOf(Reunion.Partie(copie.name, type, 0.0))
                r.etape = Reunion.Etape.TRANSCRIPTION
                r.enregistrer()
                Ui.main.post {
                    ServiceTraitement.lancer(this)
                    startActivity(Intent(this, ReunionActivity::class.java).putExtra(ReunionActivity.EXTRA_ID, r.id))
                    finish()
                }
            } catch (e: Exception) {
                ReunionApp.instance.registre.supprimer(r)
                Ui.main.post {
                    bouton.isEnabled = true
                    bouton.text = "Transcrire"
                    Toast.makeText(this, "Le fichier n'a pas pu être lu : ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        const val EXTRA_MODE = "mode"
        private const val DEMANDE_MICRO = 1
        private const val DEMANDE_FICHIER = 2
        private const val TAILLE_MAX = 200L * 1024 * 1024
    }
}
