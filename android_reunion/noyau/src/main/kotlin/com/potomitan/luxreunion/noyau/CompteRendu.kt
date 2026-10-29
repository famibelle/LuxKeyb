package com.potomitan.luxreunion.noyau

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.messages.MessageCountTokensParams
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.StopReason
import java.time.Duration

/*
 * Le compte rendu d'une réunion, rédigé par Claude à partir de la
 * transcription. Les consignes reprennent celles de la page Labs
 * (`consignesCompteRendu` dans luxasr-transcription.js) en les adaptant à qui
 * s'en sert ici : une secrétaire de direction, qui relit le texte puis le
 * diffuse par e-mail. D'où un texte brut, sans Markdown, prêt à coller.
 *
 * Les réunions mêlent le luxembourgeois et le français. LuxASR, réglé sur le
 * luxembourgeois, transcrit mal les passages en français : les consignes le
 * disent à Claude, qui sait les reconnaître et les reconstituer quand le sens
 * est clair, et les signaler sinon.
 */

/** Ce que la secrétaire a dit de la réunion, et ses préférences. */
data class InfosReunion(
    val titre: String,
    /** La date et l'heure, déjà écrites en français (« jeudi 29 octobre 2026, 14 h 05 »). */
    val date: String,
    val dureeS: Double,
    val participants: List<String>,
    val ordreDuJour: String,
    /** Noms propres, sigles et termes maison, tels qu'ils s'écrivent. */
    val glossaire: String = "",
    /** Qui signe le compte rendu. */
    val redacteur: String = "",
    /** Un ancien compte rendu, ou des consignes de mise en forme. */
    val modele: String = ""
)

object ConsignesCompteRendu {

    enum class Source {
        /** Transcription complète par lots, voix séparées. */
        VOIX,
        /** Transcription complète, une seule voix reconnue ou pas de séparation. */
        SANS_VOIX,
        /** Le texte du direct, faute de mieux : ni voix, ni garantie d'exhaustivité. */
        DIRECT
    }

    fun systeme(modele: String): String {
        val base = """
            Tu assistes une secrétaire de direction au Luxembourg. À partir de la transcription automatique d'une réunion, tu rédiges le compte rendu qu'elle relira, corrigera si besoin, puis diffusera.

            Ce qu'il faut savoir de la transcription :
            - Elle est produite par LuxASR, la reconnaissance vocale de l'Université du Luxembourg, réglée sur le luxembourgeois. Elle contient des erreurs : mots mal reconnus, noms propres déformés, ponctuation approximative.
            - Les réunions mêlent souvent le luxembourgeois et le français, parfois l'allemand ou l'anglais. Les passages qui ne sont pas en luxembourgeois sont mal transcrits : ils peuvent apparaître déformés, écrits phonétiquement ou à moitié traduits. Reconstitue leur sens quand il est clair ; sinon, signale-les.
            - Quand les voix sont séparées, elles sont numérotées (Voix 1, Voix 2…) et la séparation peut se tromper. N'attribue un nom à une voix que si la transcription le rend évident (la personne se présente, on l'appelle par son nom, elle préside…). Sinon, garde « Voix N » ou écris « un participant ».

            Règles :
            - N'invente rien. Si une information manque (une échéance, un responsable, un chiffre), écris « non précisé ».
            - Écris en français, dans un style professionnel, neutre et concis, à la troisième personne, comme un procès-verbal.
            - Écris en texte brut, prêt à coller dans un e-mail : pas de Markdown, ni dièses, ni astérisques, ni tableaux. Titres de section en MAJUSCULES, listes avec des tirets.
            - Écris les noms propres et les termes de l'entreprise exactement comme dans la liste des mots fournie, quand il y en a une.

            Plan du compte rendu :
            COMPTE RENDU — suivi du titre de la réunion
            Date, durée, rédacteur (s'il est indiqué)
            PARTICIPANTS
            ORDRE DU JOUR (celui fourni, sinon les points abordés dans l'ordre)
            RÉSUMÉ (cinq lignes au plus)
            POINTS DISCUTÉS (pour chaque point : ce qui a été dit, les positions exprimées)
            DÉCISIONS
            ACTIONS À MENER (une ligne par action : qui — quoi — pour quand)
            QUESTIONS EN SUSPENS
            À VÉRIFIER (les passages incertains ou mal transcrits, avec leur horodatage entre crochets, pour que la secrétaire puisse les contrôler)

            Omets une section vide plutôt que d'y écrire « néant », sauf DÉCISIONS et ACTIONS À MENER, qui restent toujours. Ne commente pas ta démarche : rends seulement le compte rendu.
        """.trimIndent()
        if (modele.isBlank()) return base
        return base + "\n\nLa secrétaire a fourni ce modèle (un ancien compte rendu, ou des consignes de mise en forme). " +
            "Pour la forme, il prime sur le plan ci-dessus ; pour le fond, n'en reprends rien :\n" +
            "<<<\n" + modele.trim() + "\n>>>"
    }

    fun message(infos: InfosReunion, transcription: String, source: Source): String {
        val b = StringBuilder()
        b.append("Réunion : ").append(infos.titre.ifBlank { "non précisé" }).append('\n')
        b.append("Date : ").append(infos.date).append('\n')
        if (infos.dureeS > 0) b.append("Durée : ").append(duree(infos.dureeS)).append('\n')
        if (infos.redacteur.isNotBlank()) b.append("Rédigé par : ").append(infos.redacteur.trim()).append('\n')
        b.append("Participants annoncés : ")
            .append(if (infos.participants.isEmpty()) "non précisé" else infos.participants.joinToString(", "))
            .append('\n')
        if (infos.ordreDuJour.isNotBlank()) b.append("Ordre du jour :\n").append(infos.ordreDuJour.trim()).append('\n')
        if (infos.glossaire.isNotBlank()) {
            b.append("Mots et noms propres à bien orthographier : ").append(listeMots(infos.glossaire).joinToString(", ")).append('\n')
        }
        b.append('\n').append(when (source) {
            Source.VOIX -> "Transcription complète. Chaque réplique commence par son horodatage entre crochets et la voix qui parle."
            Source.SANS_VOIX -> "Transcription complète. Les voix n'y sont pas séparées ; chaque paragraphe commence par son horodatage entre crochets."
            Source.DIRECT -> "Transcription faite en direct pendant la réunion : les voix n'y sont pas séparées, et des passages peuvent manquer (coupures de réseau). Chaque paragraphe commence par son horodatage entre crochets."
        }).append('\n')
        b.append("--- Début de la transcription ---\n")
        b.append(transcription.trim()).append('\n')
        b.append("--- Fin de la transcription ---\n")
        return b.toString()
    }

    /**
     * Les mots donnés à LuxASR (`prompt`) : les participants d'abord, puis le
     * glossaire, sans dépasser 900 caractères ni couper un mot. Leur doc
     * demande de n'y mettre que ce qui peut être prononcé : des noms, pas des
     * phrases.
     */
    fun promptLuxAsr(participants: List<String>, glossaire: String): String? {
        val mots = (participants.map { it.trim() } + listeMots(glossaire)).filter { it.isNotEmpty() }.distinct()
        val b = StringBuilder()
        for (m in mots) {
            val ajout = if (b.isEmpty()) m else ", $m"
            if (b.length + ajout.length + 1 > LotsLuxAsr.PROMPT_MAX) break
            b.append(ajout)
        }
        return if (b.isEmpty()) null else "$b."
    }

    /** Une liste saisie librement : un terme par ligne, ou séparés par des virgules. */
    fun listeMots(texte: String): List<String> =
        texte.split('\n', ',', ';').map { it.trim().trimEnd('.') }.filter { it.isNotEmpty() }

    fun duree(s: Double): String {
        val min = Math.round(s / 60.0)
        return if (min < 60) "$min min" else "${min / 60} h ${String.format(java.util.Locale.ROOT, "%02d", min % 60)}"
    }
}

/** La transcription en texte, telle qu'on la donne à Claude et à la secrétaire. */
object TexteTranscription {

    /**
     * Les parties d'une réunion (une par travail LuxASR), recollées dans le
     * temps. Les voix sont numérotées dans l'ordre où elles parlent ; d'une
     * partie à l'autre, LuxASR ne sait pas que c'est la même personne, d'où la
     * mention de la partie quand il y en a plusieurs.
     */
    fun depuisLots(parties: List<Pair<Document, Double>>): Pair<String, ConsignesCompteRendu.Source> {
        val segments = mutableListOf<Segment>()
        parties.forEachIndexed { k, (doc, decalage) ->
            for (s in doc.segments) {
                segments += s.copy(debut = s.debut?.plus(decalage), fin = s.fin?.plus(decalage),
                                   locuteur = s.locuteur?.let { "${k + 1}|$it" })
            }
        }
        val doc = Document(segments, "json", emptyList())
        val numeros = mutableMapOf<String, Int>()
        val parPartie = mutableMapOf<Int, Int>()
        val plusieurs = parties.size > 1
        val nom: (String) -> String = { l ->
            val partie = l.substringBefore('|').toInt()
            val n = numeros.getOrPut(l) { ((parPartie[partie] ?: 0) + 1).also { parPartie[partie] = it } }
            if (plusieurs) "Voix $n (partie $partie)" else "Voix $n"
        }
        val source = if (doc.locuteurs.size > 1) ConsignesCompteRendu.Source.VOIX else ConsignesCompteRendu.Source.SANS_VOIX
        // Une seule voix reconnue : la nommer n'apprendrait rien et gênerait la lecture.
        val texte = if (source == ConsignesCompteRendu.Source.VOIX) Normaliseur.versTexte(doc, nom)
                    else Normaliseur.versTexte(Document(segments.map { it.copy(locuteur = null) }, "json", emptyList())) { it }
        return texte to source
    }

    /** Le direct, en luxembourgeois : c'est l'original qu'il faut à Claude. */
    fun depuisDirect(paragraphes: List<Paragraphe>): String =
        paragraphes.filter { it.texte.isNotBlank() }.joinToString("\n\n") { p ->
            (if (p.lacune) "[…] " else "") + "[${Normaliseur.horloge(p.debut)}] ${p.texte}"
        } + "\n"

    /** Le direct traduit, pour la secrétaire : le français, puis l'original. */
    fun traductionDirect(paragraphes: List<Paragraphe>): String =
        paragraphes.filter { it.texte.isNotBlank() }.joinToString("\n\n") { p ->
            val fr = p.traduction?.takeIf { it.isNotBlank() } ?: "(pas de traduction)"
            "[${Normaliseur.horloge(p.debut)}] $fr\n    ${p.texte}"
        } + "\n"
}

/**
 * L'appel à Claude, par le SDK Java officiel. En flux : un compte rendu
 * d'une réunion de deux heures se rédige en une à deux minutes, et le flux
 * évite qu'une requête aussi longue soit coupée en route.
 */
class RedacteurClaude(
    cle: String,
    private val modele: String = MODELE,
    baseUrl: String? = null
) {
    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(cle)
        .apply { if (baseUrl != null) baseUrl(baseUrl) }
        .timeout(Duration.ofMinutes(10))
        .maxRetries(3)
        .build()

    enum class Code { CLE, DROITS, LIMITE, SURCHARGE, RESEAU, REFUS, AUTRE }

    class Erreur(val code: Code, message: String, cause: Throwable? = null) : Exception(message, cause)

    /** Rend le texte complet ; [surTexte] reçoit le texte déjà écrit, au fil de l'eau. */
    fun rediger(systeme: String, message: String, surTexte: (String) -> Unit = {}): String {
        val params = MessageCreateParams.builder()
            .model(modele)
            .maxTokens(MAX_TOKENS)
            .system(systeme)
            .addUserMessage(message)
            .build()
        val texte = StringBuilder()
        var arret: StopReason? = null
        try {
            client.messages().createStreaming(params).use { flux ->
                for (ev in flux) {
                    ev.contentBlockDelta().ifPresent { d ->
                        d.delta().text().ifPresent { t ->
                            texte.append(t.text())
                            surTexte(texte.toString())
                        }
                    }
                    ev.messageDelta().ifPresent { d -> d.delta().stopReason().ifPresent { arret = it } }
                }
            }
        } catch (e: Exception) {
            throw traduire(e)
        }
        if (arret == StopReason.REFUSAL) throw Erreur(Code.REFUS, "Claude a refusé de rédiger ce compte rendu.")
        if (arret == StopReason.MAX_TOKENS) texte.append("\n\n[Compte rendu interrompu : il dépassait la longueur maximale.]")
        return texte.toString().trim()
    }

    /** Vérifie la clé sans rien faire rédiger : un comptage de jetons ne coûte rien. */
    fun verifierCle() {
        try {
            client.messages().countTokens(MessageCountTokensParams.builder()
                .model(modele)
                .addUserMessage("Moien")
                .build())
        } catch (e: Exception) {
            throw traduire(e)
        }
    }

    private fun traduire(e: Exception): Erreur = when (e) {
        is Erreur -> e
        is AnthropicServiceException -> when (e.statusCode()) {
            401 -> Erreur(Code.CLE, "La clé d'API Claude est refusée.", e)
            403 -> Erreur(Code.DROITS, "La clé d'API Claude n'a pas accès à ce modèle.", e)
            429 -> Erreur(Code.LIMITE, "La limite d'utilisation de Claude est atteinte.", e)
            500, 502, 503, 504, 529 -> Erreur(Code.SURCHARGE, "Claude est momentanément surchargé.", e)
            else -> Erreur(Code.AUTRE, "Claude a répondu une erreur (${e.statusCode()}).", e)
        }
        is AnthropicIoException -> Erreur(Code.RESEAU, "Pas de connexion avec Claude.", e)
        else -> Erreur(Code.AUTRE, e.message ?: e.javaClass.simpleName, e)
    }

    companion object {
        const val MODELE = "claude-opus-5-5"
        /** La réflexion compte dans ce plafond : il laisse large au texte lui-même. */
        const val MAX_TOKENS = 32_000L
    }
}
