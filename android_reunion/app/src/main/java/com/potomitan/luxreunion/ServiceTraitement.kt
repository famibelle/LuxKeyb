package com.potomitan.luxreunion

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.potomitan.luxreunion.noyau.ConsignesCompteRendu
import com.potomitan.luxreunion.noyau.InfosReunion
import com.potomitan.luxreunion.noyau.LotsLuxAsr
import com.potomitan.luxreunion.noyau.Normaliseur
import com.potomitan.luxreunion.noyau.Redaction
import com.potomitan.luxreunion.noyau.Reunion
import com.potomitan.luxreunion.noyau.TexteTranscription
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Après la réunion, sans que la secrétaire ait rien à faire : compresser
 * l'enregistrement, l'envoyer à LuxASR, attendre la transcription complète
 * (voix séparées), puis faire rédiger le compte rendu (Claude ou Mistral).
 *
 * Chaque étape est notée dans la réunion (meta.json) avant de passer à la
 * suivante, et le `job_id` de LuxASR dès qu'on le connaît : une application
 * tuée en route reprend où elle en était, au prochain passage à l'accueil.
 * Un service au premier plan, pour que le travail continue écran éteint.
 */
class ServiceTraitement : Service() {

    /** Où en est une réunion, pour l'écran : une phrase, une fraction, et le compte rendu qui s'écrit. */
    data class Progres(val etape: Reunion.Etape, val texte: String, val fraction: Double? = null,
                       val brouillon: String? = null)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notif = ReunionApp.notification(this, ReunionApp.CANAL_TRAITEMENT, "Préparation du compte rendu",
                                            "Je transcris la réunion et je rédige le compte rendu.", null, enCours = true)
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIF, notif)
        synchronized(Companion) {
            if (actif) return START_NOT_STICKY
            actif = true
        }
        thread(name = "reunion-traitement") {
            try { boucle() } catch (e: Throwable) { Log.e(TAG, "traitement", e) }
            synchronized(Companion) { actif = false }
            Ui.main.post {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    /** Android 15 borne les services de synchronisation à six heures par jour. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    private fun boucle() {
        val app = ReunionApp.instance
        val faites = mutableSetOf<String>()
        while (true) {
            val r = app.registre.toutes().firstOrNull { aTraiter(it) && it.id !in faites } ?: break
            faites += r.id
            traiter(r)
            progres.remove(r.id)
            Ui.signaler()
        }
    }

    private fun publier(r: Reunion, p: Progres) {
        progres[r.id] = p
        Ui.signaler()
    }

    private fun traiter(r: Reunion) {
        val app = ReunionApp.instance
        try {
            if (r.etape == Reunion.Etape.ENREGISTREE) preparer(r, app.reglages)
            if (r.etape == Reunion.Etape.TRANSCRIPTION) transcrire(r, app.reglages)
            if (r.etape == Reunion.Etape.REDACTION) rediger(r, app.reglages)
        } catch (e: InterruptedException) {
            return
        } catch (e: Exception) {
            Log.e(TAG, "réunion ${r.id}", e)
            r.erreur = messageErreur(e)
            r.enregistrer()
            prevenir(r, "Le compte rendu n'a pas pu être préparé", r.erreur!!)
        }
    }

    /** L'enregistrement → les parties à envoyer, ou directement le direct. */
    private fun preparer(r: Reunion, reglages: Reglages) {
        if (r.mode == Reunion.Mode.DIRECT) {
            val pcm = r.fichierPcm
            if (!reglages.lotsApresDirect || !pcm.exists() || pcm.length() < 32_000) {
                // Rien d'enregistré (micro en panne) ou transcription complète
                // désactivée : le compte rendu part du direct.
                r.depuisDirect = true
                r.etape = Reunion.Etape.REDACTION
                r.enregistrer()
                return
            }
            publier(r, Progres(Reunion.Etape.ENREGISTREE, "Préparation de l'enregistrement…", 0.0))
            r.parties = Encodeur.parties(r) { f -> publier(r, Progres(Reunion.Etape.ENREGISTREE, "Préparation de l'enregistrement…", f)) }
            pcm.delete()
        }
        r.etape = Reunion.Etape.TRANSCRIPTION
        r.enregistrer()
    }

    private fun transcrire(r: Reunion, reglages: Reglages) {
        val lots = LotsLuxAsr()
        val options = LotsLuxAsr.Options(
            diarisation = true,
            locuteurs = r.participants.size.takeIf { it in 2..10 },
            prompt = ConsignesCompteRendu.promptLuxAsr(r.participants, reglages.glossaire))
        val n = r.parties.size
        for (k in r.parties.indices) {
            var essais = 0
            while (true) {
                val p = r.parties[k]
                val resultat = "lots-${k + 1}.json"
                if (p.resultat?.let { r.fichier(it).exists() } == true) break
                val quoi = if (n > 1) "partie ${k + 1} sur $n" else "l'enregistrement"
                val job = p.job ?: run {
                    val f = r.fichier(p.fichier)
                    if (!f.exists()) throw IllegalStateException("L'enregistrement a disparu du téléphone.")
                    publier(r, Progres(Reunion.Etape.TRANSCRIPTION, "Envoi de $quoi à LuxASR…", 0.0))
                    val j = soumettreAvecReprise(lots, f, p.type, options) { fr ->
                        publier(r, Progres(Reunion.Etape.TRANSCRIPTION, "Envoi de $quoi à LuxASR…", fr))
                    }
                    remplacer(r, k, p.copy(job = j))
                    j
                }
                try {
                    lots.attendre(job, r.fichier(resultat), { e ->
                        val texte = when (e) {
                            is LotsLuxAsr.Etat.File -> "En file d'attente chez LuxASR" +
                                (e.position?.let { " (${if (it == 1) "1re" else "${it}e"})" } ?: "") + attente(e.eta)
                            is LotsLuxAsr.Etat.Traitement -> "Transcription de $quoi" + attente(e.eta)
                        }
                        publier(r, Progres(Reunion.Etape.TRANSCRIPTION, texte))
                    })
                    remplacer(r, k, r.parties[k].copy(resultat = resultat))
                    break
                } catch (e: LotsLuxAsr.Erreur) {
                    // Travail expiré (plus de 4 h) ou perdu : on le soumet à nouveau, une fois.
                    if (e.code == LotsLuxAsr.Code.INTROUVABLE && essais++ == 0) {
                        remplacer(r, k, r.parties[k].copy(job = null))
                        continue
                    }
                    if (e.code == LotsLuxAsr.Code.ECHEC && r.mode == Reunion.Mode.DIRECT) {
                        // LuxASR n'a pas pu transcrire : le compte rendu partira du direct.
                        r.depuisDirect = true
                        r.etape = Reunion.Etape.REDACTION
                        r.enregistrer()
                        return
                    }
                    throw e
                }
            }
        }
        val docs = r.parties.map { Normaliseur.normaliser(r.fichier(it.resultat!!).readText()) to it.debutS }
        val (texte, _) = TexteTranscription.depuisLots(docs)
        if (docs.all { it.first.segments.isEmpty() }) {
            if (r.mode == Reunion.Mode.DIRECT && r.lireDirect().isNotEmpty()) r.depuisDirect = true
            else throw IllegalStateException("LuxASR n'a reconnu aucune parole dans l'enregistrement.")
        }
        Reunion.ecrireAtomique(r.fichierTranscription, texte)
        // La transcription est là : l'audio n'a plus rien à faire sur le téléphone.
        r.effacerAudio()
        r.etape = Reunion.Etape.REDACTION
        r.erreur = null
        r.enregistrer()
    }

    /** Un envoi coupé (réseau mobile) est repris deux fois, après une pause. */
    private fun soumettreAvecReprise(lots: LotsLuxAsr, f: java.io.File, type: String, options: LotsLuxAsr.Options,
                                     progres: (Double) -> Unit): String {
        var essai = 0
        while (true) {
            try { return lots.soumettre(f, type, options, progres) }
            catch (e: LotsLuxAsr.Erreur) {
                if (e.code != LotsLuxAsr.Code.RESEAU || ++essai > 2) throw e
                Thread.sleep(20_000L * essai)
            }
        }
    }

    private fun remplacer(r: Reunion, k: Int, p: Reunion.Partie) {
        r.parties = r.parties.toMutableList().also { it[k] = p }
        r.enregistrer()
    }

    private fun attente(eta: Double?): String {
        if (eta == null || eta <= 0) return "…"
        val min = Math.round(eta / 60.0)
        return if (min < 1) ", encore moins d'une minute…" else ", encore environ $min min…"
    }

    private fun rediger(r: Reunion, reglages: Reglages) {
        val cle = reglages.cle
        if (cle.isNullOrBlank()) {
            r.sansCle = true
            r.enregistrer()
            prevenir(r, "Transcription prête : ${r.titre}", "Le compte rendu automatique n'est pas activé. Ouvrez la réunion pour le faire rédiger dans Claude.")
            return
        }
        val (texte, source) = sourceDuCompteRendu(r)
        if (texte.isBlank()) throw IllegalStateException("Aucune parole n'a été reconnue : il n'y a rien à résumer.")
        val infos = infos(r, reglages)
        publier(r, Progres(Reunion.Etape.REDACTION, "Rédaction du compte rendu par ${reglages.fournisseur.nom}…"))
        var dernier = 0L
        // Builds debug : files/essai-mistral.txt peut donner l'adresse d'un faux service
        // Mistral (essais sans crédit, captures). Une version publiée ne le lit jamais.
        val baseEssai = if (BuildConfig.DEBUG) File(filesDir, "essai-mistral.txt").takeIf { it.exists() }?.readText()?.trim() else null
        val cr = Redaction.creer(reglages.fournisseur, cle, reglages.modele, baseEssai).rediger(
            ConsignesCompteRendu.systeme(reglages.modeleCompteRendu),
            ConsignesCompteRendu.message(infos, texte, source)) { brouillon ->
            val t = System.currentTimeMillis()
            if (t - dernier > 300) {
                dernier = t
                publier(r, Progres(Reunion.Etape.REDACTION, "Rédaction du compte rendu par ${reglages.fournisseur.nom}…", brouillon = brouillon))
            }
        }
        Reunion.ecrireAtomique(r.fichierCompteRendu, cr + "\n")
        r.etape = Reunion.Etape.PRETE
        r.erreur = null
        r.sansCle = false
        r.enregistrer()
        prevenir(r, "Compte rendu prêt", r.titre)
    }

    private fun prevenir(r: Reunion, titre: String, texte: String) {
        getSystemService(NotificationManager::class.java).notify(r.id.hashCode(),
            ReunionApp.notification(this, ReunionApp.CANAL_PRET, titre, texte, ReunionApp.ouvrirReunion(this, r.id), enCours = false))
    }

    companion object {
        private const val TAG = "ServiceTraitement"
        private const val NOTIF = 2

        @Volatile private var actif = false
        val progres = ConcurrentHashMap<String, Progres>()

        fun aTraiter(r: Reunion): Boolean =
            r.erreur == null && !r.sansCle &&
                r.etape in setOf(Reunion.Etape.ENREGISTREE, Reunion.Etape.TRANSCRIPTION, Reunion.Etape.REDACTION)

        fun lancer(ctx: Context) {
            // Refusé si l'application est en arrière-plan (Android 12+) :
            // le traitement reprendra au prochain passage à l'accueil.
            try { ctx.startForegroundService(Intent(ctx, ServiceTraitement::class.java)) }
            catch (e: Exception) { Log.w(TAG, "lancement différé : ${e.message}") }
        }

        /** Ce que Claude reçoit : la transcription complète, ou le direct faute de mieux. */
        fun sourceDuCompteRendu(r: Reunion): Pair<String, ConsignesCompteRendu.Source> {
            if (!r.depuisDirect && r.parties.isNotEmpty() && r.parties.all { p -> p.resultat?.let { r.fichier(it).exists() } == true }) {
                val docs = r.parties.map { Normaliseur.normaliser(r.fichier(it.resultat!!).readText()) to it.debutS }
                return TexteTranscription.depuisLots(docs)
            }
            return TexteTranscription.depuisDirect(r.lireDirect()) to ConsignesCompteRendu.Source.DIRECT
        }

        fun infos(r: Reunion, reglages: Reglages) = InfosReunion(
            titre = r.titre, date = ReunionApp.date(r.creee), dureeS = r.dureeS,
            participants = r.participants, ordreDuJour = r.ordreDuJour,
            glossaire = reglages.glossaire, redacteur = reglages.redacteur, modele = reglages.modeleCompteRendu)

        /** Une erreur en clair, et ce qu'on peut y faire. */
        fun messageErreur(e: Exception): String = when (e) {
            is Redaction.Erreur -> when (e.code) {
                Redaction.Code.CLE, Redaction.Code.DROITS ->
                    e.message + " Demandez à la personne qui a installé l'application de vérifier la clé."
                Redaction.Code.RESEAU -> "Pas de connexion internet pour joindre le service de rédaction. Réessayez quand le téléphone est connecté."
                Redaction.Code.LIMITE, Redaction.Code.SURCHARGE -> e.message + " Réessayez dans quelques minutes."
                else -> e.message ?: "Erreur du service de rédaction."
            }
            is LotsLuxAsr.Erreur -> when (e.code) {
                LotsLuxAsr.Code.RESEAU -> "Pas de connexion avec LuxASR. Réessayez quand le téléphone est connecté."
                LotsLuxAsr.Code.REFUS -> "LuxASR a refusé l'enregistrement (${e.message})."
                LotsLuxAsr.Code.ECHEC -> "LuxASR n'a pas pu transcrire l'enregistrement (${e.message})."
                LotsLuxAsr.Code.DELAI -> "LuxASR met trop de temps à répondre. Réessayez plus tard."
                else -> "LuxASR : ${e.message}"
            }
            is java.io.IOException -> "Erreur de réseau ou de stockage : ${e.message}"
            else -> e.message ?: e.javaClass.simpleName
        }
    }
}
