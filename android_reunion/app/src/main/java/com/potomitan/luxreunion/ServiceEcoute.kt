package com.potomitan.luxreunion

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import com.potomitan.luxreunion.noyau.DirectLuxAsr
import com.potomitan.luxreunion.noyau.FabriqueOkHttp
import com.potomitan.luxreunion.noyau.Paragraphe
import com.potomitan.luxreunion.noyau.Reunion
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * L'écoute d'une réunion : un service au premier plan, de type micro, pour
 * que l'écoute continue écran éteint ou application en arrière-plan.
 *
 * Deux choses en parallèle, depuis le même micro :
 * - **l'enregistrement**, dans le dossier de la réunion (`audio.pcm`). C'est
 *   la source de la transcription complète, après la réunion ; il ne dépend
 *   pas du réseau et ne s'interrompt jamais ;
 * - **le direct** (DirectLuxAsr) : transcription et traduction en français,
 *   pour suivre la réunion pendant qu'elle a lieu. Il peut s'interrompre
 *   sur une coupure de réseau, et reprend seul.
 */
class ServiceEcoute : Service() {

    private var reunion: Reunion? = null
    private var fil: HandlerThread? = null
    private var planif: PlanificateurAndroid? = null
    private var direct: DirectLuxAsr? = null
    private var capture: Capture? = null
    private var sortie: OutputStream? = null
    @Volatile private var octets = 0L
    private var verrou: PowerManager.WakeLock? = null
    private var termine = false
    private var finalise = false
    private var dernierEnregistrement = 0L
    private var derniereNotif: DirectLuxAsr.Etat? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DEMARRER -> demarrer(intent.getStringExtra(EXTRA_ID))
            ACTION_TERMINER -> terminer()
            // Relancé par le système sans demande : rien à reprendre ici, la
            // réunion interrompue sera traitée au prochain passage à l'accueil.
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun notifier(texte: String): android.app.Notification {
        val ouvrir = PendingIntent.getActivity(this, 1,
            Intent(this, DirectActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return ReunionApp.notification(this, ReunionApp.CANAL_ECOUTE, "Réunion en cours", texte, ouvrir, enCours = true)
    }

    private fun demarrer(id: String?) {
        val notif = notifier("J'écoute et je traduis.")
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(NOTIF, notif)
        val r = id?.let { ReunionApp.instance.registre.lire(it) }
        if (r == null || reunion != null) { if (reunion == null) arreterService(); return }
        reunion = r
        r.etape = Reunion.Etape.ECOUTE
        r.enregistrer()

        EtatDirect.apply {
            reunionId = r.id; etat = DirectLuxAsr.Etat.CONNEXION; paragraphes = emptyList()
            secondes = 0.0; niveau = 0f; retard = 0.0; termine = false; microEnPanne = false
        }
        verrou = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "luxreunion:ecoute").apply { acquire(5 * 3600_000L) }

        val f = HandlerThread("reunion-direct").apply { start() }
        fil = f
        val p = PlanificateurAndroid(f.looper)
        planif = p
        val d = DirectLuxAsr(ecouteur, FabriqueOkHttp(p), p, traduire = true, cible = "fr")
        direct = d
        p.plus(0) { d.start() }

        octets = r.fichierPcm.length()
        sortie = BufferedOutputStream(FileOutputStream(r.fichierPcm, true), 64 * 1024)
        val c = Capture { pcm, niveau ->
            try { sortie?.write(pcm) } catch (_: Exception) {}
            octets += pcm.size
            EtatDirect.niveau = niveau
            EtatDirect.secondes = octets / 32_000.0
            p.plus(0) { d.audio(pcm) }
            Ui.signaler()
        }
        capture = c
        if (!c.demarrer()) {
            // Micro refusé ou pris par une autre application (un appel en cours…).
            EtatDirect.microEnPanne = true
            capture = null
            terminer()
        }
    }

    private val ecouteur = object : DirectLuxAsr.Ecouteur {
        override fun surEtat(etat: DirectLuxAsr.Etat) {
            EtatDirect.etat = etat
            Ui.signaler()
            val texte = when (etat) {
                DirectLuxAsr.Etat.ECOUTE -> "J'écoute et je traduis."
                DirectLuxAsr.Etat.HORS_LIGNE -> "Pas de réseau : la traduction reprendra seule. L'enregistrement continue."
                DirectLuxAsr.Etat.CONNEXION -> "Connexion à LuxASR…"
                else -> return
            }
            if (etat != derniereNotif && !termine) {
                derniereNotif = etat
                getSystemService(NotificationManager::class.java).notify(NOTIF, notifier(texte))
            }
        }

        override fun surTexte(paragraphes: List<Paragraphe>) {
            EtatDirect.paragraphes = paragraphes
            Ui.signaler()
            // Au fil de l'eau, pour qu'une application tuée ne perde pas le direct.
            val maintenant = System.currentTimeMillis()
            if (maintenant - dernierEnregistrement > 5_000) {
                dernierEnregistrement = maintenant
                reunion?.enregistrerDirect(paragraphes)
            }
        }

        override fun surRetard(retard: Double) {
            EtatDirect.retard = retard
            Ui.signaler()
        }

        override fun surFin(paragraphes: List<Paragraphe>, raison: DirectLuxAsr.Raison?) {
            EtatDirect.paragraphes = paragraphes
            // Durée maximale atteinte : la réunion se termine d'elle-même.
            if (raison == DirectLuxAsr.Raison.DUREE) Ui.main.post { terminer() }
            else finaliser(paragraphes)
        }
    }

    /** « Terminer la réunion » : le micro se coupe, le direct engage sa fin, puis on conclut. */
    private fun terminer() {
        if (termine) return
        termine = true
        capture?.arreter()
        capture = null
        try { sortie?.close() } catch (_: Exception) {}
        sortie = null
        val d = direct
        val p = planif
        if (d == null || p == null) { finaliser(EtatDirect.paragraphes); return }
        p.plus(0) {
            if (d.etat == DirectLuxAsr.Etat.REPOS) finaliser(d.paragraphes())
            else d.stop()
        }
        // Si le service ne conclut pas, on garde ce qui est affiché.
        p.plus(12_000) {
            if (!finalise) { val texte = d.paragraphes(); d.annuler(); finaliser(texte) }
        }
    }

    private fun finaliser(paragraphes: List<Paragraphe>) {
        Ui.main.post {
            if (finalise) return@post
            finalise = true
            if (!termine) {
                // Le direct s'est arrêté seul : la réunion s'arrête avec lui.
                termine = true
                capture?.arreter()
                try { sortie?.close() } catch (_: Exception) {}
            }
            reunion?.let { r ->
                r.enregistrerDirect(paragraphes)
                r.dureeS = Encodeur.duree(r.fichierPcm)
                r.etape = Reunion.Etape.ENREGISTREE
                if (EtatDirect.microEnPanne && r.dureeS < 1) r.erreur = "Le micro n'a pas pu s'ouvrir. Fermez les autres applications qui l'utilisent (appel en cours, dictaphone…) et réessayez."
                r.enregistrer()
            }
            EtatDirect.paragraphes = paragraphes
            EtatDirect.termine = true
            EtatDirect.etat = DirectLuxAsr.Etat.REPOS
            Ui.signaler()
            ServiceTraitement.lancer(this)
            arreterService()
        }
    }

    private fun arreterService() {
        fil?.quitSafely()
        fil = null
        verrou?.let { if (it.isHeld) it.release() }
        verrou = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        capture?.arreter()
        try { sortie?.close() } catch (_: Exception) {}
        verrou?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        private const val NOTIF = 1
        private const val ACTION_DEMARRER = "demarrer"
        private const val ACTION_TERMINER = "terminer"
        private const val EXTRA_ID = "id"

        fun demarrer(ctx: Context, id: String) {
            ctx.startForegroundService(Intent(ctx, ServiceEcoute::class.java).setAction(ACTION_DEMARRER).putExtra(EXTRA_ID, id))
        }

        fun terminer(ctx: Context) {
            ctx.startService(Intent(ctx, ServiceEcoute::class.java).setAction(ACTION_TERMINER))
        }
    }
}
