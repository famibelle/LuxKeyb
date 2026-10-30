package com.potomitan.luxreunion.noyau

import org.json.JSONObject

/*
 * Transcription et traduction en direct par le flux WebSocket de LuxASR :
 * transposition du client de la page Labs (docs/assets/luxasr-direct.js),
 * dont il garde le protocole, les seuils et le découpage en paragraphes
 * (skill transcription, § 6). Ce qui change, c'est ce qu'on fait d'une panne.
 *
 * Sur la page, le direct EST la transcription : quand le service ne répond
 * plus, on s'arrête. Ici, le téléphone enregistre la réunion en parallèle et
 * la transcription complète se fait après, à partir de cet enregistrement. Le
 * direct ne sert qu'à suivre la réunion pendant qu'elle a lieu. Il ne doit
 * donc jamais arrêter la réunion : une coupure le met HORS_LIGNE, il retente
 * à intervalles croissants, et l'audio qu'il n'a pas pu envoyer est jeté après
 * RETARD_MAX_S. Le paragraphe suivant porte alors `lacune`, pour que l'écran
 * dise qu'un passage manque au direct (il sera dans la version complète).
 *
 * Rien ici ne touche au micro ni au réseau : la capture est dans le service
 * Android, et les liaisons passent par [FabriqueLiaison]. Toutes les méthodes
 * s'appellent sur le fil de [Planificateur], et les liaisons y rappellent
 * aussi : il n'y a donc aucun verrou. C'est aussi ce qui permet de jouer une
 * réunion entière en test, sur une horloge simulée.
 */

/** Un paragraphe du direct. `partiel` : la queue encore instable, sur le dernier seulement. */
data class Paragraphe(
    val debut: Double,
    val texte: String,
    val partiel: String? = null,
    val traduction: String? = null,
    /** De l'audio précédant ce paragraphe n'a pas pu être transcrit en direct. */
    val lacune: Boolean = false
)

fun interface Annulable { fun annuler() }

/** Le fil sur lequel tout se passe, et son horloge (en millisecondes). */
interface Planificateur {
    fun plus(ms: Long, action: () -> Unit): Annulable
    fun maintenant(): Long
}

/** Une liaison WebSocket ouverte vers le service. */
interface Liaison {
    fun envoyerTexte(texte: String): Boolean
    fun envoyerOctets(octets: ByteArray): Boolean
    /** Fermeture normale, après ce qui attend encore. */
    fun fermer()
    /** Octets confiés à la liaison et pas encore partis. */
    fun enAttente(): Long
}

/** Ce que la liaison rappelle, toujours sur le fil du [Planificateur]. */
interface EcouteurLiaison {
    fun surOuverture()
    fun surMessage(texte: String)
    /** Fermée ou en échec : une seule fois, et plus rien après. */
    fun surFermeture()
}

fun interface FabriqueLiaison {
    fun ouvrir(ecouteur: EcouteurLiaison): Liaison
}

class DirectLuxAsr(
    private val ecouteur: Ecouteur,
    private val fabrique: FabriqueLiaison,
    private val planif: Planificateur,
    private val traduire: Boolean = true,
    private val cible: String = "fr",
    private val r: Reglages = Reglages()
) {
    enum class Etat { REPOS, CONNEXION, ECOUTE, HORS_LIGNE, ARRET }

    /** Pourquoi le direct s'est arrêté de lui-même ; `null` sur un arrêt demandé. */
    enum class Raison { DUREE }

    interface Ecouteur {
        fun surEtat(etat: Etat)
        fun surTexte(paragraphes: List<Paragraphe>)
        /** Audio en souffrance au-delà du seuil d'alerte (`retard` en s), puis 0 au retour. */
        fun surRetard(retard: Double) {}
        /**
         * Une phrase et sa traduction, telles que le service les associe
         * (`source_text` et la part nouvelle de `accumulated_translation`) :
         * de quoi afficher les deux ensemble, en sous-titres.
         */
        fun surPhrase(texte: String, traduction: String) {}
        fun surFin(paragraphes: List<Paragraphe>, raison: Raison?)
    }

    /** Les seuils de la page Labs, et ceux qui sont propres à l'application. */
    data class Reglages(
        val rotationS: Double = 120.0,
        val rotationForceS: Double = 600.0,
        val graceStopMs: Long = 4_000,
        val pauseParagrapheMs: Long = 2_500,
        val paragrapheMaxS: Double = 45.0,
        /** Le premier aperçu arrive ~1,1 s après le début de la parole. */
        val latenceS: Double = 1.0,
        val retardAlerteS: Double = 2.0,
        /** Au-delà, l'audio en souffrance est jeté : le direct reprend au présent. */
        val retardMaxS: Double = 30.0,
        /** Échecs d'affilée repris aussitôt, avant de passer HORS_LIGNE. */
        val echecsMax: Int = 3,
        val repriseMs: Long = 15_000,
        val repriseMaxMs: Long = 60_000,
        /** La traduction d'une phrase arrive jusqu'à 2,2 s après elle. */
        val traductionGraceMs: Long = 3_500,
        val connexionMaxMs: Long = 8_000,
        /** Une réunion de quatre heures au plus : le service est partagé. */
        val dureeMaxS: Double = 4 * 3600.0
    )

    private inner class Lien(val gen: Int) : EcouteurLiaison {
        var ws: Liaison? = null
        var ouverte = false
        var finie = false
        var delai: Annulable? = null
        var grace: Annulable? = null
        var tradAttendue = false
        var indexCommit: Int? = null
        var tradVu = ""

        override fun surOuverture() = ouverture(this)
        override fun surMessage(texte: String) = message(this, texte)
        override fun surFermeture() {
            if (gen == generation && this === liaison && !finie) perdue(this)
        }
    }

    private class Marque(val debut: Double, val offset: Int, val suite: Boolean, val lacune: Boolean)
    private class Courant(val debut: Double, val texte: String, val suite: Boolean, val lacune: Boolean,
                          var partiel: String? = null)

    var etat = Etat.REPOS
        private set
    private var generation = 0
    private var liaison: Lien? = null
    private var echecs = 0
    private var reprise: Annulable? = null
    private var repriseMs = r.repriseMs

    private val enAttente = ArrayDeque<ByteArray>()
    private var octetsEnAttente = 0L
    private var echantillons = 0L
    private var lent = false
    /** De l'audio a été jeté depuis le dernier paragraphe ouvert. */
    private var lacuneEnCours = false

    private var traductions = mutableMapOf<Int, String>()
    private val enGrace = mutableListOf<Lien>()
    private var attenteFin = false

    /** Paragraphes des liaisons closes. */
    private var figes: List<Paragraphe> = emptyList()
    private var acc = ""
    private var queue = ""
    private var marques = mutableListOf<Marque>()
    private var debutLiaison = 0.0
    private var close = true
    private var closeA = 0L
    private var debutParagraphe = 0.0
    private var renouvellement = false
    private var minuteur: Annulable? = null
    private var raisonFin: Raison? = null

    val secondes: Double get() = echantillons / RATE.toDouble()

    // --- Commandes ------------------------------------------------------------

    fun start() {
        if (etat != Etat.REPOS) return
        generation++
        figes = emptyList(); acc = ""; queue = ""; marques = mutableListOf()
        enAttente.clear(); octetsEnAttente = 0; traductions = mutableMapOf()
        enGrace.clear(); attenteFin = false
        echantillons = 0; debutLiaison = 0.0; close = true; closeA = 0; debutParagraphe = 0.0
        echecs = 0; lent = false; renouvellement = false; raisonFin = null; lacuneEnCours = false
        repriseMs = r.repriseMs
        setEtat(Etat.CONNEXION)
        ouvrirLiaison()
    }

    /** Un bloc de PCM 16 bits little-endian, 16 kHz mono, tel que capté. */
    fun audio(pcm: ByteArray) {
        if (etat != Etat.ECOUTE && etat != Etat.CONNEXION && etat != Etat.HORS_LIGNE) return
        echantillons += pcm.size / 2
        envoyer(pcm)
        if (secondes >= r.dureeMaxS) { arreter(Raison.DUREE); return }
        surveillerRetard()
    }

    /** Arrêt demandé : le service engage la fin, puis on rend le texte. */
    fun stop() = arreter(null)

    /** Coupe tout sans attendre. */
    fun annuler() {
        if (etat == Etat.REPOS) return
        generation++
        minuteur?.annuler()
        reprise?.annuler()
        liaison?.let { it.finie = true; fermer(it) }
        enGrace.toList().forEach { fermer(it) }
        liaison = null
        enAttente.clear(); octetsEnAttente = 0
        setEtat(Etat.REPOS)
    }

    /** Tout le texte de la séance, la queue instable à part sur le dernier paragraphe. */
    fun paragraphes(): List<Paragraphe> =
        fusionner(figes, courants(true)).mapIndexed { i, p ->
            traductions[i]?.let { p.copy(traduction = it) } ?: p
        }

    // --- Envoi ----------------------------------------------------------------

    private fun envoyer(octets: ByteArray) {
        val l = liaison
        if (l != null && l.ouverte && !l.finie && !renouvellement && l.ws?.envoyerOctets(octets) == true) return
        enAttente.addLast(octets)
        octetsEnAttente += octets.size
        // Le direct reprend au présent : au-delà de RETARD_MAX_S, le plus
        // ancien part à la poubelle (il est dans l'enregistrement).
        val max = (r.retardMaxS * RATE * 2).toLong()
        while (octetsEnAttente > max && enAttente.isNotEmpty()) {
            octetsEnAttente -= enAttente.removeFirst().size
            lacuneEnCours = true
        }
    }

    private fun surveillerRetard() {
        val l = liaison
        val ws = if (l != null && l.ouverte && !l.finie) l.ws else null
        val retard = (octetsEnAttente + (ws?.enAttente() ?: 0L)) / (RATE * 2.0)
        if (ws != null && retard >= r.retardMaxS) {
            // Le réseau ne suit plus : ce qui attend dans la liaison ne peut
            // pas être repris, on la coupe et on repart au présent.
            lacuneEnCours = true
            perdue(l!!)
            return
        }
        val estLent = retard >= r.retardAlerteS
        if (estLent != lent) {
            lent = estLent
            ecouteur.surRetard(if (estLent) retard else 0.0)
        }
    }

    // --- Liaisons -------------------------------------------------------------

    private fun ouvrirLiaison() {
        reprise?.annuler()
        reprise = null
        val l = Lien(generation)
        liaison = l
        l.ws = try { fabrique.ouvrir(l) } catch (e: Exception) { null }
        if (l.ws == null) { perdue(l); return }
        l.delai = planif.plus(r.connexionMaxMs) {
            if (l.gen == generation && l === liaison && !l.ouverte && !l.finie) perdue(l)
        }
    }

    private fun ouverture(l: Lien) {
        if (l.gen != generation || l !== liaison || l.finie) { l.ws?.fermer(); return }
        l.delai?.annuler()
        l.ouverte = true
        echecs = 0
        repriseMs = r.repriseMs
        // Les réglages partent avant la première trame ; puis ce qui a été
        // capté pendant l'ouverture, d'un trait.
        val config = JSONObject().put("type", "config").put("language", "lb")
        if (traduire) config.put("translation_enabled", true).put("translation_target", cible)
        val ws = l.ws ?: return
        ws.envoyerTexte(config.toString())
        while (enAttente.isNotEmpty()) ws.envoyerOctets(enAttente.removeFirst())
        octetsEnAttente = 0
        if (etat == Etat.CONNEXION || etat == Etat.HORS_LIGNE) setEtat(Etat.ECOUTE)
        // Arrêt demandé pendant l'ouverture : cette liaison ne sert qu'à vider
        // l'audio en attente.
        if (etat == Etat.ARRET) demanderStop(l)
    }

    private fun message(l: Lien, texte: String) {
        if (l.gen != generation) return
        val m = try { JSONObject(texte) } catch (e: Exception) { return }
        val type = chaine(m, "type")
        // Une liaison finie ne reçoit plus que sa traduction en retard.
        if (type == "translation") { surTraduction(l, m); return }
        if (l !== liaison || l.finie) return
        when (type) {
            "transcription" -> surTranscription(l, m)
            "recording_stopped" -> conclureLiaison(l)
        }
    }

    /** Liaison coupée sans qu'on l'ait demandé : on la reprend, aussitôt ou plus tard. */
    private fun perdue(l: Lien) {
        if (l.finie) return
        l.finie = true
        renouvellement = false
        minuteur?.annuler()
        figer()
        fermer(l)
        if (etat == Etat.ARRET) { finir(null); return }
        if (++echecs <= r.echecsMax) { ouvrirLiaison(); return }
        setEtat(Etat.HORS_LIGNE)
        val attente = repriseMs
        repriseMs = minOf(r.repriseMaxMs, repriseMs * 2)
        reprise = planif.plus(attente) {
            if (l.gen == generation && etat == Etat.HORS_LIGNE) ouvrirLiaison()
        }
    }

    private fun fermer(l: Lien) {
        l.delai?.annuler()
        l.grace?.annuler()
        enGrace.remove(l)
        try { l.ws?.fermer() } catch (e: Exception) {}
    }

    private fun demanderStop(l: Lien) {
        try { l.ws?.envoyerTexte("{\"type\":\"stop\"}") } catch (e: Exception) {}
        minuteur?.annuler()
        // Le « stop » part derrière l'audio en souffrance : la grâce en tient compte.
        val retardMs = ((l.ws?.enAttente() ?: 0L) * 1000 / (RATE * 2))
        minuteur = planif.plus(r.graceStopMs + retardMs) { conclureLiaison(l) }
    }

    private fun renouveler() {
        val l = liaison ?: return
        if (renouvellement || !l.ouverte) return
        renouvellement = true
        demanderStop(l)
    }

    /** Fin d'une liaison demandée (renouvellement ou arrêt) : on fige son texte. */
    private fun conclureLiaison(l: Lien) {
        if (l.finie || l !== liaison) return
        l.finie = true
        minuteur?.annuler()
        figer()
        // Sa dernière phrase n'est peut-être pas encore traduite : la liaison
        // reste ouverte, pour ses seuls messages `translation`.
        if (traduire && l.tradAttendue) {
            enGrace += l
            l.grace = planif.plus(r.traductionGraceMs) { fermer(l); verifierFin() }
        } else {
            fermer(l)
        }
        renouvellement = false
        when (etat) {
            Etat.ECOUTE -> ouvrirLiaison()
            Etat.ARRET -> {
                // Le micro est coupé, mais de l'audio capté pendant le raccord
                // attend encore : une dernière liaison le transcrit.
                if (enAttente.isNotEmpty()) { ouvrirLiaison(); return }
                attenteFin = true
                verifierFin()
            }
            else -> {}
        }
    }

    private fun verifierFin() {
        if (attenteFin && enGrace.isEmpty()) { attenteFin = false; finir(null) }
    }

    // --- Texte ----------------------------------------------------------------

    private fun surTranscription(l: Lien, m: JSONObject) {
        val nouveau = chaine(m, "accumulated_text")?.takeIf { it.isNotEmpty() } ?: acc
        val q = chaine(m, "partial_text") ?: ""
        val maintenant = planif.maintenant()

        // Du texte nouveau après une phrase close, ou le premier d'une liaison :
        // nouveau paragraphe si la pause a été longue ou si le paragraphe en
        // cours a assez duré ; sinon le texte prolonge le paragraphe précédent.
        val nouveauTexte = nouveau.length > acc.length || q.isNotBlank()
        if (nouveauTexte && (close || marques.isEmpty())) {
            val avant = marques.isNotEmpty() || figes.isNotEmpty()
            val suite = avant && !lacuneEnCours &&
                (!close || (maintenant - closeA < r.pauseParagrapheMs && secondes - debutParagraphe < r.paragrapheMaxS))
            val debut = maxOf(0.0, secondes - r.latenceS)
            if (!suite) debutParagraphe = debut
            marques += Marque(debut, acc.length, suite, lacuneEnCours)
            lacuneEnCours = false
            close = false
        }
        if (nouveau.length > acc.length) {
            // Du texte engagé : sa traduction viendra, rattachée à ce paragraphe.
            l.tradAttendue = true
            l.indexCommit = fusionner(figes, courantsDe(nouveau, false)).size - 1
        }
        acc = nouveau
        queue = q
        if (chaine(m, "send_reason") == "silence_commit" && q.isBlank()) { close = true; closeA = maintenant }

        ecouteur.surTexte(paragraphes())
        val duree = secondes - debutLiaison
        if ((close && duree >= r.rotationS) || duree >= r.rotationForceS) renouveler()
    }

    private fun courants(avecQueue: Boolean) = courantsDe(acc, avecQueue)

    private fun courantsDe(texte: String, avecQueue: Boolean): List<Courant> {
        val res = marques.mapIndexed { i, mk ->
            val fin = if (i + 1 < marques.size) marques[i + 1].offset else texte.length
            Courant(mk.debut, texte.substring(minOf(mk.offset, texte.length), minOf(fin, texte.length)).trim(),
                    mk.suite, mk.lacune)
        }
        if (avecQueue && queue.isNotBlank() && res.isNotEmpty()) res.last().partiel = queue.trim()
        return res
    }

    /**
     * Un message `translation` : ce qui s'ajoute à `accumulated_translation`
     * revient au paragraphe de la dernière phrase engagée sur cette liaison.
     */
    private fun surTraduction(l: Lien, m: JSONObject) {
        if (!traduire || (m.has("success") && !m.optBoolean("success", true))) return
        val t = chaine(m, "accumulated_translation") ?: return
        val vu = l.tradVu
        val ajout = (if (t.startsWith(vu) || t.length > vu.length) t.substring(minOf(vu.length, t.length)) else "").trim()
        l.tradVu = t
        l.tradAttendue = false
        val i = l.indexCommit ?: -1
        if (ajout.isNotEmpty() && i >= 0) traductions[i] = ((traductions[i] ?: "") + " " + ajout).trim()
        val source = chaine(m, "source_text")?.trim()
        if (ajout.isNotEmpty() && !source.isNullOrEmpty()) ecouteur.surPhrase(source, ajout)
        ecouteur.surTexte(paragraphes())
        if (l.finie && l in enGrace) { fermer(l); verifierFin() }
    }

    private fun fusionner(base: List<Paragraphe>, ajout: List<Courant>): List<Paragraphe> {
        val res = base.toMutableList()
        for (p in ajout) {
            val prec = res.lastOrNull()
            if (p.suite && prec != null) {
                res[res.size - 1] = prec.copy(texte = "${prec.texte} ${p.texte}".trim(),
                                              partiel = p.partiel ?: prec.partiel)
            } else {
                res += Paragraphe(p.debut, p.texte, p.partiel, lacune = p.lacune)
            }
        }
        return res.filter { it.texte.isNotEmpty() || !it.partiel.isNullOrEmpty() }
    }

    /** Fige la liaison qui finit, queue comprise : c'est ce qui était affiché. */
    private fun figer() {
        val cour = courants(true).map { p ->
            val q = p.partiel
            if (q != null) Courant(p.debut, "${p.texte} $q".trim(), p.suite, p.lacune) else p
        }
        figes = fusionner(figes, cour).map { it.copy(partiel = null) }
        acc = ""; queue = ""; marques = mutableListOf()
        debutLiaison = secondes
    }

    // --- Arrêts ---------------------------------------------------------------

    private fun arreter(raison: Raison?) {
        if (etat != Etat.ECOUTE && etat != Etat.CONNEXION && etat != Etat.HORS_LIGNE) return
        raisonFin = raison
        val sansLiaison = etat != Etat.ECOUTE || liaison == null
        setEtat(Etat.ARRET)
        reprise?.annuler()
        val l = liaison
        if (sansLiaison || l == null) {
            // Rien d'ouvert pour engager la fin : on garde ce qui est affiché.
            l?.let { it.finie = true; fermer(it) }
            figer()
            finir(raisonFin)
            return
        }
        if (renouvellement) return          // conclureLiaison s'en chargera
        if (l.ouverte) demanderStop(l)
        // Sinon une liaison s'ouvre : ouverture() demandera l'arrêt.
    }

    private fun finir(raison: Raison?) {
        minuteur?.annuler()
        reprise?.annuler()
        enGrace.toList().forEach { fermer(it) }
        attenteFin = false
        generation++
        liaison = null
        enAttente.clear(); octetsEnAttente = 0
        renouvellement = false
        val texte = paragraphes()
        setEtat(Etat.REPOS)
        ecouteur.surFin(texte, raison ?: raisonFin)
        raisonFin = null
    }

    private fun setEtat(e: Etat) {
        if (etat != e) { etat = e; ecouteur.surEtat(e) }
    }

    companion object {
        const val RATE = 16_000
        const val ENDPOINT = "wss://luxasr.uni.lu/prod/ws/transcribe"

        /** `optString` d'Android rend "null" pour une valeur nulle : on la lit à la main. */
        internal fun chaine(o: JSONObject, cle: String): String? =
            if (!o.has(cle) || o.isNull(cle)) null else o.opt(cle) as? String
    }
}
