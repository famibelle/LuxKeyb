package com.example.kreyolkeyboard.stt

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Écran invisible dont le seul rôle est de demander RECORD_AUDIO.
 *
 * Un InputMethodService ne peut pas demander de permission d'exécution : le
 * système exige une Activity au premier plan, et la fenêtre de saisie n'en est
 * pas une. C'est le détour habituel des claviers qui proposent la dictée.
 */
class MicPermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

    private fun deliver(granted: Boolean) {
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

        // ContextCompat et non Context.checkSelfPermission, qui n'existe qu'à
        // partir d'API 23 alors que le clavier descend jusqu'à 21.
        fun hasPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

        /**
         * Ouvre la demande de permission depuis l'IME. [onResult] est appelé sur
         * le thread principal, que l'utilisateur accepte, refuse, ou quitte
         * l'écran système sans répondre.
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
