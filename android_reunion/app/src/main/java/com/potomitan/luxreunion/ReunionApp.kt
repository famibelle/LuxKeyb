package com.potomitan.luxreunion

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.potomitan.luxreunion.noyau.Annulable
import com.potomitan.luxreunion.noyau.DirectLuxAsr
import com.potomitan.luxreunion.noyau.Paragraphe
import com.potomitan.luxreunion.noyau.Planificateur
import com.potomitan.luxreunion.noyau.Registre
import com.potomitan.luxreunion.noyau.Reunion
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

class ReunionApp : Application() {

    lateinit var registre: Registre
        private set
    lateinit var reglages: Reglages
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        registre = Registre(File(filesDir, "reunions"))
        reglages = Reglages(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL_ECOUTE, "Réunion en cours", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "Pendant qu'une réunion est écoutée et traduite." })
        nm.createNotificationChannel(NotificationChannel(CANAL_TRAITEMENT, "Préparation du compte rendu", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "Pendant que la transcription et le compte rendu se préparent." })
        nm.createNotificationChannel(NotificationChannel(CANAL_PRET, "Compte rendu prêt", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "Quand un compte rendu est prêt, ou qu'il faut votre aide." })
    }

    /**
     * À chaque retour sur l'accueil : une réunion restée « en écoute » sans
     * service d'écoute a été interrompue (téléphone éteint, application
     * tuée). Ce qui a été enregistré jusque-là est gardé et traité. Puis on
     * relance le traitement de ce qui attend.
     */
    fun reprendre() {
        for (r in registre.toutes()) {
            if (r.etape == Reunion.Etape.ECOUTE && EtatDirect.reunionId != r.id) {
                r.dureeS = r.fichierPcm.length() / 32_000.0
                r.etape = Reunion.Etape.ENREGISTREE
                r.enregistrer()
            }
        }
        if (registre.toutes().any { ServiceTraitement.aTraiter(it) }) ServiceTraitement.lancer(this)
    }

    companion object {
        lateinit var instance: ReunionApp
            private set

        const val CANAL_ECOUTE = "ecoute"
        const val CANAL_TRAITEMENT = "traitement"
        const val CANAL_PRET = "pret"

        private val DATE = SimpleDateFormat("EEEE d MMMM yyyy, HH 'h' mm", Locale.FRENCH)
        private val DATE_COURTE = SimpleDateFormat("EEE d MMM, HH 'h' mm", Locale.FRENCH)

        /** « jeudi 29 octobre 2026, 14 h 05 » */
        fun date(ms: Long): String = synchronized(DATE) { DATE.format(Date(ms)) }
        fun dateCourte(ms: Long): String = synchronized(DATE_COURTE) { DATE_COURTE.format(Date(ms)) }

        fun ouvrirReunion(ctx: Context, id: String): PendingIntent =
            PendingIntent.getActivity(ctx, id.hashCode(),
                Intent(ctx, ReunionActivity::class.java).putExtra(ReunionActivity.EXTRA_ID, id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        fun notification(ctx: Context, canal: String, titre: String, texte: String, intent: PendingIntent?,
                         enCours: Boolean): Notification =
            Notification.Builder(ctx, canal)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(Charte.BLEU)
                .setContentTitle(titre)
                .setContentText(texte)
                .setStyle(Notification.BigTextStyle().bigText(texte))
                .setOngoing(enCours)
                .setAutoCancel(!enCours)
                .apply { if (intent != null) setContentIntent(intent) }
                .build()
    }
}

/** Le fil principal, et un moyen de prévenir les écrans ouverts. */
object Ui {
    val main = Handler(Looper.getMainLooper())
    private val abonnes = CopyOnWriteArrayList<() -> Unit>()
    private var prevu = false

    fun abonner(f: () -> Unit) { abonnes += f }
    fun desabonner(f: () -> Unit) { abonnes -= f }

    /** Prévient les écrans, au plus dix fois par seconde. */
    fun signaler() {
        main.post {
            if (prevu) return@post
            prevu = true
            main.postDelayed({ prevu = false; abonnes.forEach { it() } }, 100)
        }
    }
}

/** L'état du direct en cours, que le service d'écoute tient à jour et que l'écran lit. */
object EtatDirect {
    @Volatile var reunionId: String? = null
    @Volatile var etat: DirectLuxAsr.Etat = DirectLuxAsr.Etat.REPOS
    @Volatile var paragraphes: List<Paragraphe> = emptyList()
    @Volatile var secondes = 0.0
    @Volatile var niveau = 0f
    @Volatile var retard = 0.0
    /** Le service a fini : l'écran du direct peut passer à la réunion. */
    @Volatile var termine = false
    /** Le micro n'a pas pu s'ouvrir. */
    @Volatile var microEnPanne = false

    val enCours: Boolean get() = reunionId != null && !termine

    /**
     * Une nouvelle réunion commence. Appelé avant de lancer le service, et
     * pas seulement par lui : l'écran du direct s'ouvre peut-être avant que
     * le service ait démarré, et ne doit voir ni « aucune réunion » ni la
     * réunion précédente, terminée.
     */
    fun commencer(id: String) {
        reunionId = id; etat = DirectLuxAsr.Etat.CONNEXION; paragraphes = emptyList()
        secondes = 0.0; niveau = 0f; retard = 0.0; termine = false; microEnPanne = false
    }
}

/** Le planificateur du direct, sur un fil Android à lui. */
class PlanificateurAndroid(looper: Looper) : Planificateur {
    private val h = Handler(looper)
    override fun plus(ms: Long, action: () -> Unit): Annulable {
        val r = Runnable(action)
        h.postDelayed(r, ms)
        return Annulable { h.removeCallbacks(r) }
    }
    override fun maintenant(): Long = SystemClock.elapsedRealtime()
}
