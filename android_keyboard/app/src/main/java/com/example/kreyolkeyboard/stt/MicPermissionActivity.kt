package com.example.kreyolkeyboard.stt

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.Log
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.kreyolkeyboard.R

/**
 * Écran sans fond qui précède la première dictée : il dit où part la voix, puis
 * demande RECORD_AUDIO.
 *
 * Un InputMethodService ne peut pas demander de permission d'exécution : le
 * système exige une Activity au premier plan, et la fenêtre de saisie n'en est
 * pas une. C'est le détour habituel des claviers qui proposent la dictée.
 *
 * **L'écran d'information est une obligation de Google Play**, pas une
 * politesse. Toute donnée personnelle envoyée à un tiers d'une façon que
 * l'utilisateur n'attend pas forcément exige, dans l'application elle-même,
 * un avertissement visible qui dit quoi, à qui et pourquoi, suivi d'un geste
 * d'accord, et **avant** la demande d'autorisation du système. Un clavier
 * qui enverrait la voix sans cela s'expose au refus de sa mise à jour. Le
 * texte reprend la politique de confidentialité, section « La dictée
 * vocale ».
 *
 * L'accord est gardé dans un fichier à part, hors de la liste de sauvegarde
 * Android : sur un nouveau téléphone, la question est reposée.
 */
class MicPermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!accordDonne(this)) {
            montrerInformation()
            return
        }
        demanderMicro()
    }

    private fun montrerInformation() {
        val sombre = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val theme = if (sombre) android.R.style.Theme_DeviceDefault_Dialog_Alert
                    else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        var repondu = false
        val dialogue = AlertDialog.Builder(this, theme)
            .setTitle(R.string.stt_info_titre)
            .setMessage(R.string.stt_info_texte)
            .setPositiveButton(R.string.stt_info_accepter) { _, _ ->
                repondu = true
                enregistrerAccord(this, true)
                demanderMicro()
            }
            .setNegativeButton(R.string.stt_info_refuser) { _, _ ->
                repondu = true
                deliver(false)
            }
            // Retour, ou toucher à côté : c'est un refus, et rien n'est envoyé.
            .setOnDismissListener { if (!repondu) deliver(false) }
            .create()
        dialogue.show()
        // Le lien vers la politique de confidentialité doit être cliquable.
        dialogue.findViewById<TextView>(android.R.id.message)?.let {
            Linkify.addLinks(it, Linkify.WEB_URLS)
            it.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun demanderMicro() {
        if (hasPermission(this)) {
            deliver(true)
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Avant Android 6 la permission est accordée à l'installation ; si
            // hasPermission() l'a refusée ici, c'est un refus définitif que
            // rien ne peut demander à l'utilisateur.
            deliver(false)
            return
        }

        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CODE) return
        deliver(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
    }

    private var livre = false

    private fun deliver(granted: Boolean) {
        if (livre) return
        livre = true
        Log.d(TAG, "permission micro : ${if (granted) "accordée" else "refusée"}")
        // Le callback est consommé puis effacé : le conserver retiendrait le
        // service IME bien après la fermeture de cet écran.
        val callback = pendingResult
        pendingResult = null
        callback?.invoke(granted)
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val TAG = "MicPermission"
        private const val REQUEST_CODE = 4201

        private var pendingResult: ((Boolean) -> Unit)? = null

        private const val PREFS_DICTEE = "lux_dictee_prefs"
        private const val KEY_ACCORD = "accord_information_dictee"

        /** L'utilisateur a lu l'écran d'information et accepté que sa voix parte. */
        fun accordDonne(context: Context): Boolean =
            context.getSharedPreferences(PREFS_DICTEE, Context.MODE_PRIVATE)
                .getBoolean(KEY_ACCORD, false)

        private fun enregistrerAccord(context: Context, accord: Boolean) {
            context.getSharedPreferences(PREFS_DICTEE, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ACCORD, accord).apply()
        }

        /** Ce qu'il faut avoir avant d'ouvrir le micro : l'accord, puis la permission. */
        fun pret(context: Context): Boolean = accordDonne(context) && hasPermission(context)

        // ContextCompat et non Context.checkSelfPermission, qui n'existe qu'à
        // partir d'API 23 alors que le clavier descend jusqu'à 21.
        fun hasPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

        /**
         * Ouvre l'écran d'information (au premier usage) puis la demande de
         * permission, depuis l'IME. [onResult] est appelé sur le thread
         * principal, que l'utilisateur accepte, refuse, ou quitte sans
         * répondre.
         */
        fun request(context: Context, onResult: (Boolean) -> Unit) {
            pendingResult = onResult
            val intent = Intent(context, MicPermissionActivity::class.java).apply {
                // NEW_TASK est obligatoire : le contexte appelant est un
                // Service, qui n'a pas de pile d'activités où empiler celle-ci.
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            }
            context.startActivity(intent)
        }
    }
}
