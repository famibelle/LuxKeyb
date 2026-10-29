package com.potomitan.luxreunion.noyau

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Le compte rendu par Mistral (API de chat, `POST /v1/chat/completions`), en
 * flux comme pour Claude : les morceaux arrivent en lignes `data: {…}`, la
 * dernière est `data: [DONE]`. Mistral n'a pas de SDK Java officiel : appel
 * direct avec OkHttp. La clé n'est jamais dans l'APK : saisie sur le
 * téléphone, chiffrée par le Keystore (Reglages.kt).
 */
class RedacteurMistral(
    private val cle: String,
    private val modele: String = MODELE,
    private val base: String = "https://api.mistral.ai",
    private val http: OkHttpClient = CLIENT
) : Redacteur {

    override fun rediger(systeme: String, message: String, surTexte: (String) -> Unit): String {
        val corps = JSONObject()
            .put("model", modele)
            .put("stream", true)
            .put("max_tokens", MAX_TOKENS)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", systeme))
                .put(JSONObject().put("role", "user").put("content", message)))
        val requete = Request.Builder().url("$base/v1/chat/completions")
            .header("Authorization", "Bearer $cle")
            .header("Accept", "text/event-stream")
            .post(corps.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val texte = StringBuilder()
        var fin: String? = null
        try {
            http.newCall(requete).execute().use { rep ->
                if (!rep.isSuccessful) throw erreurHttp(rep.code, rep.body?.string() ?: "")
                val source = rep.body!!.source()
                while (true) {
                    val ligne = source.readUtf8Line() ?: break
                    if (!ligne.startsWith("data:")) continue
                    val donnees = ligne.removePrefix("data:").trim()
                    if (donnees == "[DONE]") break
                    val choix = JSONObject(donnees).optJSONArray("choices")?.optJSONObject(0) ?: continue
                    val morceau = choix.optJSONObject("delta")?.let { DirectLuxAsr.chaine(it, "content") }
                    if (!morceau.isNullOrEmpty()) {
                        texte.append(morceau)
                        surTexte(texte.toString())
                    }
                    DirectLuxAsr.chaine(choix, "finish_reason")?.let { fin = it }
                }
            }
        } catch (e: Redaction.Erreur) {
            throw e
        } catch (e: IOException) {
            throw Redaction.Erreur(Redaction.Code.RESEAU, "Pas de connexion avec Mistral.", e)
        }
        if (fin == "length") texte.append("\n\n[Compte rendu interrompu : il dépassait la longueur maximale.]")
        return texte.toString().trim()
    }

    /** La liste des modèles : un appel gratuit, qui suffit à éprouver la clé. */
    override fun verifierCle() {
        val requete = Request.Builder().url("$base/v1/models").header("Authorization", "Bearer $cle").build()
        try {
            http.newCall(requete).execute().use { rep ->
                if (!rep.isSuccessful) throw erreurHttp(rep.code, rep.body?.string() ?: "")
            }
        } catch (e: IOException) {
            throw Redaction.Erreur(Redaction.Code.RESEAU, "Pas de connexion avec Mistral.", e)
        }
    }

    private fun erreurHttp(code: Int, corps: String): Redaction.Erreur = when (code) {
        401 -> Redaction.Erreur(Redaction.Code.CLE, "La clé d'API Mistral est refusée.")
        403 -> Redaction.Erreur(Redaction.Code.DROITS, "La clé d'API Mistral n'a pas accès à ce modèle.")
        429 -> Redaction.Erreur(Redaction.Code.LIMITE, "La limite d'utilisation de Mistral est atteinte.")
        in 500..599 -> Redaction.Erreur(Redaction.Code.SURCHARGE, "Mistral est momentanément indisponible.")
        else -> Redaction.Erreur(Redaction.Code.AUTRE, "Mistral a répondu une erreur ($code) : ${LotsLuxAsr.messageDe(corps)}")
    }

    companion object {
        const val MODELE = "mistral-large-latest"
        const val MAX_TOKENS = 16_000

        private val CLIENT: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.MINUTES)
                .build()
        }
    }
}
