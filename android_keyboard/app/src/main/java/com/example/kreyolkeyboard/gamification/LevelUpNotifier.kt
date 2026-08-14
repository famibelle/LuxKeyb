package com.example.kreyolkeyboard.gamification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.kreyolkeyboard.R
import com.example.kreyolkeyboard.SettingsActivity

/**
 * Annonce un passage de niveau par une pastille sur l'icône de l'application.
 *
 * Android n'expose aucune API de badge : la pastille est dérivée d'une
 * notification active dans un canal autorisé à badger. Le canal est donc créé
 * en IMPORTANCE_LOW, ce qui donne exactement le comportement recherché — pas de
 * son, pas de vibration, et surtout **pas de bandeau déroulant** par-dessus la
 * conversation en cours pendant que l'utilisateur écrit. La notification attend
 * dans le volet, la pastille attend sur l'écran d'accueil, et l'utilisateur
 * ouvre l'application au moment qu'il choisit.
 *
 * Rien n'est envoyé hors de l'appareil : le niveau est calculé localement.
 */
object LevelUpNotifier {

    private const val TAG = "LevelUpNotifier"
    private const val CHANNEL_ID = "lux_niveaux"
    private const val NOTIFICATION_ID = 4201

    /** Teal de la carte de niveau partageable, pour rester cohérent d'un support à l'autre. */
    private val ACCENT_COLOR = Color.parseColor("#0E6E76")

    /**
     * Crée le canal. Idempotent, à appeler avant toute notification.
     * Sans effet avant Android 8, où les canaux n'existent pas.
     */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Niveau op Lëtzebuergesch",
            NotificationManager.IMPORTANCE_LOW  // silencieux, sans bandeau
        ).apply {
            description = "Vous prévient quand vous atteignez un nouveau niveau de vocabulaire."
            setShowBadge(true)  // c'est cette ligne qui autorise la pastille d'icône
            enableVibration(false)
        }

        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    /**
     * Retire la notification de niveau, et avec elle la pastille de l'icône :
     * la pastille étant dérivée de la notification active, il n'y a pas d'autre
     * façon de l'éteindre. À appeler dès que l'utilisateur a vu sa progression,
     * quel que soit le chemin par lequel il y est arrivé.
     *
     * Sans effet si aucune notification n'est affichée.
     */
    fun clear(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.w(TAG, "Impossible de retirer la notification de niveau", e)
        }
    }

    /** L'utilisateur a-t-il accordé la permission de notifier (Android 13+) ? */
    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Publie la notification de passage de niveau. Sans permission, ne fait
     * rien : le clavier se comporte alors exactement comme avant.
     *
     * @param levelLabel libellé complet du niveau atteint, emoji compris.
     */
    fun notifyLevelUp(context: Context, levelLabel: String) {
        if (!canNotify(context)) {
            Log.d(TAG, "Permission de notification absente : rien n'est publié")
            return
        }

        ensureChannel(context)

        val openStats = Intent(context, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(SettingsActivity.EXTRA_OPEN_TAB, SettingsActivity.TAB_STATS)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openStats,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Formulation reprise telle quelle de la célébration existante dans
        // SettingsActivity : ne pas rédiger de kréyòl nouveau ici.
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            // Silhouette monochrome : Android ne garde que l'alpha de cette
            // icône, une image en couleurs y apparaîtrait comme un carré plein.
            .setSmallIcon(R.drawable.ic_notification_level)
            // Teinte la silhouette et, sur les lanceurs qui la reprennent, la
            // pastille d'icône elle-même : sans couleur explicite, celle-ci est
            // dérivée de l'icône de l'app, presque blanche, donc invisible.
            .setColor(ACCENT_COLOR)
            .setContentTitle("Ou vansé !")
            .setContentText("Ou rivé nivo $levelLabel")
            .setPriority(NotificationCompat.PRIORITY_LOW)  // équivalent pré-Android 8
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Pastille de niveau publiée : $levelLabel")
        } catch (e: SecurityException) {
            // Permission révoquée entre la vérification et l'envoi
            Log.w(TAG, "Notification refusée par le système", e)
        }
    }
}
