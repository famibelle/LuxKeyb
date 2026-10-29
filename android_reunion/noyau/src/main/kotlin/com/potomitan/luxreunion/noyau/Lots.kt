package com.potomitan.luxreunion.noyau

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.File
import java.io.IOException

/*
 * L'API par lots de LuxASR, telle que documentée (skill transcription, § 2,
 * et releves/2026-10-29/api-officielle.md) : les octets bruts du fichier en
 * `POST /asr2`, puis `GET /v3/asr/jobs/{id}` jusqu'à `completed`, puis
 * `/result`, qui ne se lit qu'une fois.
 *
 * Une application n'est pas une page web : CORS ne la concerne pas, et le
 * `job_id` du 202 se lit sans difficulté. C'est ce qui rend ici possible
 * l'envoi d'un fichier, toujours bloqué sur la page Labs.
 *
 * Les appels sont bloquants : l'appelant les fait sur son propre fil, et
 * enregistre le `job_id` avant de sonder, pour reprendre le suivi si
 * l'application est tuée entre-temps (le service garde un résultat 4 h).
 */
class LotsLuxAsr(
    private val base: String = "https://luxasr.uni.lu",
    private val http: OkHttpClient = Reseau.lots,
    private val pause: (Long) -> Unit = { Thread.sleep(it) }
) {
    data class Options(
        val diarisation: Boolean = true,
        /** Nombre de voix, 1 à 10 ; sinon le service les compte lui-même. */
        val locuteurs: Int? = null,
        /** Noms propres et jargon à bien orthographier (900 caractères au plus). */
        val prompt: String? = null
    )

    sealed class Etat {
        data class File(val position: Int?, val eta: Double?) : Etat()
        data class Traitement(val eta: Double?) : Etat()
    }

    enum class Code { REFUS, ECHEC, INTROUVABLE, DELAI, RESEAU, SANS_JOB }

    class Erreur(val code: Code, message: String) : Exception(message)

    /** Soumet [fichier] et rend le `job_id`. [progres] reçoit la fraction envoyée. */
    fun soumettre(fichier: File, typeContenu: String, options: Options, progres: (Double) -> Unit = {}): String {
        val url = base.toHttpUrl().newBuilder().addPathSegment("asr2")
            .addQueryParameter("language", "lb")
            .addQueryParameter("diarization", if (options.diarisation) "Enabled" else "Disabled")
            .addQueryParameter("hesitations", "Hide")
            .addQueryParameter("outfmt", "json")
            .apply {
                // `num_speakers` n'est pas documenté pour l'API : c'est ce
                // qu'envoie leur propre formulaire, et seulement avec la
                // diarisation (skill § 3, essai du 29 octobre 2026).
                val n = options.locuteurs
                if (options.diarisation && n != null && n in 1..10) addQueryParameter("num_speakers", "$n")
                options.prompt?.takeIf { it.isNotBlank() }?.let { addQueryParameter("prompt", it.take(PROMPT_MAX)) }
            }.build()
        val corps = CorpsFichier(fichier, typeContenu.toMediaType(), progres)
        val requete = Request.Builder().url(url).post(corps)
            .header("X-Filename", fichier.name)
            .build()
        val reponse = try { http.newCall(requete).execute() } catch (e: IOException) {
            throw Erreur(Code.RESEAU, e.message ?: "envoi coupé")
        }
        reponse.use { rep ->
            val texte = rep.body?.string() ?: ""
            if (rep.code != 200 && rep.code != 202) throw Erreur(Code.REFUS, "HTTP ${rep.code} : ${messageDe(texte)}")
            val job = try { JSONObject(texte).opt("job_id")?.toString() } catch (e: Exception) { null }
            if (job.isNullOrEmpty() || job == "null") throw Erreur(Code.SANS_JOB, texte.take(300))
            return job
        }
    }

    /**
     * Sonde [job] jusqu'à `completed`, puis écrit le résultat dans
     * [destination] avant toute autre chose : le service l'efface dès qu'il
     * l'a servi. Sondage poli, calqué sur leur client officiel : 2 s, puis
     * 1,2 fois plus à chaque tour, 10 s au plus.
     */
    fun attendre(job: String, destination: File, surEtat: (Etat) -> Unit = {}, annule: () -> Boolean = { false }) {
        var delai = SONDAGE_INITIAL_MS
        var ecoule = 0L
        var echecs = 0
        while (true) {
            if (annule()) throw InterruptedException()
            if (ecoule > ATTENTE_MAX_MS) throw Erreur(Code.DELAI, "toujours en cours après ${ecoule / 60000} min")
            pause(delai)
            ecoule += delai
            delai = minOf(SONDAGE_MAX_MS, (delai * SONDAGE_FACTEUR).toLong())
            val s = try {
                http.newCall(Request.Builder().url("$base/v3/asr/jobs/$job").build()).execute().use { rep ->
                    val texte = rep.body?.string() ?: ""
                    if (rep.code == 404) JSONObject().put("status", "not_found")
                    else JSONObject(texte)
                }
            } catch (e: Exception) {
                if (++echecs >= ECHECS_SONDAGE_MAX) throw Erreur(Code.RESEAU, e.message ?: "sondage impossible")
                continue
            }
            echecs = 0
            val eta = nombre(s, "eta_seconds")
            when (DirectLuxAsr.chaine(s, "status")) {
                "completed" -> { recuperer(job, destination); return }
                "failed" -> throw Erreur(Code.ECHEC, messageDe(s.toString()))
                "not_found" -> throw Erreur(Code.INTROUVABLE, "travail inconnu ou expiré")
                "processing" -> surEtat(Etat.Traitement(eta))
                else -> surEtat(Etat.File(nombre(s, "queue_position")?.toInt(), eta))
            }
        }
    }

    /** Une seule lecture : le service efface le résultat dès qu'il l'a servi. */
    private fun recuperer(job: String, destination: File) {
        val requete = Request.Builder().url("$base/v3/asr/jobs/$job/result").build()
        try {
            http.newCall(requete).execute().use { rep ->
                if (!rep.isSuccessful) throw Erreur(Code.RESEAU, "résultat : HTTP ${rep.code}")
                val tmp = File(destination.path + ".part")
                tmp.writeText(rep.body?.string() ?: "")
                if (!tmp.renameTo(destination)) throw IOException("écriture de $destination impossible")
            }
        } catch (e: IOException) {
            throw Erreur(Code.RESEAU, e.message ?: "lecture du résultat coupée")
        }
    }

    private class CorpsFichier(val fichier: File, val type: MediaType, val progres: (Double) -> Unit) : RequestBody() {
        override fun contentType() = type
        override fun contentLength() = fichier.length()
        override fun writeTo(sink: BufferedSink) {
            val total = fichier.length().coerceAtLeast(1)
            var envoye = 0L
            var dernier = -1
            fichier.source().use { src ->
                while (true) {
                    val n = src.read(sink.buffer, 64 * 1024L)
                    if (n < 0) break
                    sink.emit()
                    envoye += n
                    val pct = (envoye * 100 / total).toInt()
                    if (pct != dernier) { dernier = pct; progres(envoye.toDouble() / total) }
                }
            }
        }
    }

    companion object {
        const val PROMPT_MAX = 900
        private const val SONDAGE_INITIAL_MS = 2_000L
        private const val SONDAGE_FACTEUR = 1.2
        private const val SONDAGE_MAX_MS = 10_000L
        /** File d'attente comprise : une réunion de trois heures prend du temps. */
        private const val ATTENTE_MAX_MS = 3 * 3600_000L
        private const val ECHECS_SONDAGE_MAX = 6

        /** Le `Content-Type` d'un fichier d'après son extension, ou `null`. */
        fun typeDe(nom: String): String? = PAR_EXTENSION[nom.substringAfterLast('.', "").lowercase()]

        private val PAR_EXTENSION = mapOf(
            "wav" to "audio/wav", "mp3" to "audio/mpeg", "mp2" to "audio/mpeg", "m4a" to "audio/mp4",
            "aac" to "audio/aac", "ogg" to "audio/ogg", "oga" to "audio/ogg", "opus" to "audio/ogg",
            "flac" to "audio/flac", "weba" to "audio/webm", "wma" to "audio/x-ms-wma", "amr" to "audio/amr",
            "3gp" to "video/3gpp", "mp4" to "video/mp4", "mov" to "video/quicktime", "qta" to "video/quicktime",
            "webm" to "video/webm", "wmv" to "video/x-ms-wmv"
        )

        private fun nombre(o: JSONObject, cle: String): Double? =
            if (!o.has(cle) || o.isNull(cle)) null else (o.opt(cle) as? Number)?.toDouble()

        internal fun messageDe(brut: String): String {
            try {
                val o = JSONObject(brut)
                for (c in listOf("error", "message", "detail", "reason")) {
                    DirectLuxAsr.chaine(o, c)?.let { return it.take(300) }
                }
            } catch (_: Exception) {}
            return brut.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim().take(300)
        }
    }
}
