package com.potomitan.luxreunion.noyau

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/*
 * Le normaliseur des réponses de l'API par lots de LuxASR : transposition en
 * Kotlin de celui de la page Labs (docs/assets/luxasr-transcription.js), dont
 * il garde les règles et les seuils. La référence reste la skill
 * `.claude/skills/transcription/SKILL.md` (§ 2 pour le schéma, § 4 pour la
 * normalisation) ; on n'y revient ici que sur ce qui explique le code.
 *
 * L'application ne demande jamais que `outfmt=json` : c'est le seul format qui
 * porte à la fois les temps et les locuteurs. Le texte brut n'est accepté
 * qu'en repli, si le service répondait autre chose.
 */

data class Segment(val debut: Double?, val fin: Double?, val locuteur: String?, val texte: String)

/** Une boucle de Whisper retirée : où, combien de fois, quel motif. */
data class Boucle(val debut: Double?, val fois: Int, val motif: String)

class Document(val segments: List<Segment>, val format: String, val boucles: List<Boucle>) {
    /** Les libellés bruts (`SPEAKER_00`…), dans l'ordre d'apparition. */
    val locuteurs: List<String> = segments.mapNotNull { it.locuteur }.distinct()
    val horodate: Boolean get() = segments.any { it.debut != null }
    val diarise: Boolean get() = locuteurs.isNotEmpty()
}

/** Segments consécutifs lus d'un seul tenant : un tour de parole, ou un paragraphe. */
class Tour(val locuteur: String?, val debut: Double?, val indices: List<Int>)

object Normaliseur {

    /* Préfixe de locuteur en tête de réplique : `SPEAKER_00:`, `[SPEAKER_00]`,
       `Speaker 1:`, `Spriecher 1:`, `Locuteur 1 :`, `Intervenant 1 :`. */
    private val PREFIXE = Regex(
        "^\\s*\\[?\\s*((?:speaker|spriecher|sprecher|locuteur|intervenant|spk)[ _-]?\\d+)\\s*\\]?\\s*[:：\\-–—]?\\s*",
        RegexOption.IGNORE_CASE)

    /* Les espaces du `\\s` de JavaScript, Unicode compris (insécable…), écrits à
       la main : le drapeau (?U) n'est pas garanti par le moteur d'Android. */
    private val ESPACES = Regex("[\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+")
    private val FIN_PHRASE = Regex("[.?!…][\"»”)]?$")
    private val FIN_SOUPLE = Regex("[,;:]$")
    private val JETON = Regex("[\\p{L}\\p{N}'’-]+")
    private val HORS_CLE = Regex("[^\\p{L}\\p{N}'’-]")

    /* Découpage en phrases (skill § 4) : on ferme une phrase sur un point quand
       elle a assez duré, sur une virgule quand elle devient longue, et
       n'importe où au-delà. */
    private const val PHRASE_MIN_S = 2.0
    private const val PHRASE_MIN_CAR = 25
    private const val PHRASE_SOUPLE_S = 4.0
    private const val PHRASE_SOUPLE_CAR = 60
    private const val PHRASE_MAX_S = 8.0
    private const val PHRASE_MAX_CAR = 100
    /** Écart toléré entre deux mots dans le texte (espaces, ponctuation isolée). */
    private const val ALIGNEMENT_JEU = 12

    /* Boucle de Whisper : un motif de 1 à 4 mots répété au moins BOUCLE_MIN
       fois d'affilée (relevé du 29 octobre 2026 : « deen vun » 18 fois). */
    private const val BOUCLE_MIN = 5
    private const val BOUCLE_BLOC_MAX = 4

    /* Un tour plus long est coupé en paragraphes ; la coupe attend un début de
       phrase, sauf au-delà de TOUR_FORCE_S. */
    private const val TOUR_MAX_S = 45.0
    private const val TOUR_FORCE_S = 75.0

    private val CLES_LISTE = listOf("segments", "result", "results", "transcription", "transcript",
                                    "utterances", "chunks", "data")
    private val CLES_LOCUTEUR = listOf("speaker", "spk", "speaker_id", "speaker_label", "label")
    private val CLES_LOCUTEUR_MOT = listOf("speaker", "spk", "speaker_id")
    private val CLES_DEBUT = listOf("start", "begin", "start_time", "from", "startTime")
    private val CLES_FIN = listOf("end", "stop", "end_time", "to", "endTime")
    private val CLES_TEXTE = listOf("text", "transcript", "sentence", "content")
    private val CLES_MOT = listOf("word", "text", "token")

    // --- Entrée ---------------------------------------------------------------

    /**
     * N'importe quelle réponse du service → un document. Ce qu'il ne reconnaît
     * pas, il le dit (`format == "inconnu"`) au lieu de deviner.
     */
    fun normaliser(brut: String): Document {
        val s = brut.removePrefix("﻿")
        val journal = mutableListOf<Boucle>()
        val debut = s.trimStart()
        val json: Any? = if (debut.startsWith("[") || debut.startsWith("{")) {
            try { JSONTokener(s).nextValue() } catch (e: Exception) { null }
        } else null
        val (segments, format) = when {
            json != null -> depuisJson(json, journal)
            s.isBlank() -> emptyList<Segment>() to "text"
            else -> depuisTexte(s)
        }
        // Le texte n'a pas de mots horodatés : ses boucles se retirent segment par segment.
        val propres = if (format == "json") segments
                      else segments.map { it.copy(texte = reduireTexte(it.texte, journal, it.debut)) }
        return Document(propres, format, journal)
    }

    /** Les boucles de Whisper d'un texte, réduites (même règle que le normaliseur). */
    fun reduireBoucles(texte: String): String = reduireTexte(texte, mutableListOf(), null)

    private fun depuisJson(o: Any, journal: MutableList<Boucle>): Pair<List<Segment>, String> {
        // Réponse multi-format (`outfmt=json,srt`) : une clé par format.
        if (o is JSONObject && o.has("json") && !o.isNull("json")) {
            var j = o.get("json")
            if (j is String) {
                j = try { JSONTokener(j).nextValue() } catch (e: Exception) { return depuisTexte(j as String) }
            }
            return depuisJson(j, journal)
        }
        val liste = trouverListe(o, 0)
        if (liste != null) {
            val segs = mutableListOf<Segment>()
            for (i in 0 until liste.length()) segs += segmentsDe(liste.get(i), journal)
            return segs to "json"
        }
        if (o is JSONObject) {
            val texte = premier(o, listOf("text", "transcription", "transcript"))
            if (texte is String) return depuisTexte(texte)
        }
        return emptyList<Segment>() to "inconnu"
    }

    /** Le tableau de segments d'une réponse JSON, à un niveau de profondeur près. */
    private fun trouverListe(o: Any?, profondeur: Int): JSONArray? {
        if (o is JSONArray) {
            if (o.length() == 0) return null
            for (i in 0 until o.length()) if (o.opt(i) !is JSONObject) return null
            return o
        }
        if (o !is JSONObject || profondeur > 2) return null
        for (cle in CLES_LISTE) {
            val v = o.opt(cle)
            if (v is JSONObject || v is JSONArray) trouverListe(v, profondeur + 1)?.let { return it }
        }
        return null
    }

    /** Texte ligne à ligne : une ligne à préfixe ouvre une réplique, les suivantes la prolongent. */
    private fun depuisTexte(texte: String): Pair<List<Segment>, String> {
        val segs = mutableListOf<Segment>()
        var locCourant: String? = null
        var texteCourant: String? = null
        fun pousser() {
            texteCourant?.let { t -> if (t.isNotEmpty()) segs += Segment(null, null, locCourant, t) }
        }
        for (brute in texte.replace(Regex("\r\n?"), "\n").split("\n")) {
            if (brute.isBlank()) {
                if (texteCourant != null && locCourant == null) { pousser(); texteCourant = null }
                continue
            }
            val p = PREFIXE.find(brute)
            if (p != null || texteCourant == null) {
                pousser()
                locCourant = p?.groupValues?.get(1)
                texteCourant = nettoyer(if (p != null) brute.substring(p.range.last + 1) else brute)
            } else {
                texteCourant = nettoyer("$texteCourant $brute")
            }
        }
        pousser()
        return segs to "text"
    }

    // --- Segments ----------------------------------------------------------------

    private class Groupe(val loc: String?, val i0: Int, var debut: Double?, var fin: Double?, val suite: Boolean) {
        var i1 = i0
        var car = 0
        var ferme = false
        var point = false
    }

    private class Jeton(val debut: Int, val fin: Int, val cle: String)

    /** Un segment brut → un ou plusieurs segments normalisés. */
    private fun segmentsDe(brut: Any?, journal: MutableList<Boucle>): List<Segment> {
        val el: JSONObject = when (brut) {
            is String -> JSONObject().put("text", brut)
            is JSONObject -> brut
            else -> return emptyList()
        }
        var loc = libelle(premier(el, CLES_LOCUTEUR))
        val debut = secondes(premier(el, CLES_DEBUT))
        val fin = secondes(premier(el, CLES_FIN))
        val tableau = el.optJSONArray("words")
        var mots: List<JSONObject> = if (tableau == null) emptyList()
            else (0 until tableau.length()).mapNotNull { tableau.opt(it) as? JSONObject }
        val brutTexte = premier(el, CLES_TEXTE)
        var texte = if (brutTexte != null) nettoyer(brutTexte.toString()) else recoller(mots)

        val parMot = mots.any { libelle(premier(it, CLES_LOCUTEUR_MOT)) != null }
        if (loc == null && !parMot) {
            PREFIXE.find(texte)?.let { p ->
                loc = p.groupValues[1]
                texte = texte.substring(p.range.last + 1)
            }
        }
        if (mots.isEmpty()) {
            return if (texte.isNotEmpty()) listOf(Segment(debut, fin, loc, reduireTexte(texte, journal, debut)))
                   else emptyList()
        }

        // Les mots placés dans le texte ; s'ils ne s'y retrouvent pas, on repart
        // des mots recollés. Les boucles se retirent ici, avant le découpage en
        // phrases, qui pourrait couper une boucle en deux.
        var debuts = aligner(texte, mots)
        if (debuts == null) {
            texte = recoller(mots)
            debuts = aligner(texte, mots)
        }
        if (debuts != null) {
            val positions = debuts
            val jetons = mots.mapIndexed { k, w ->
                val m = motDe(w)
                Jeton(positions[k], positions[k] + m.length, cle(m))
            }
            val motsAvant = mots
            val sb = sansBoucles(texte, jetons, journal) { k -> secondes(premier(motsAvant[k], listOf("start", "begin"))) }
            if (sb != null) {
                mots = mots.filterIndexed { k, _ -> k !in sb.second }
                texte = sb.first
                debuts = aligner(texte, mots)
            }
        }

        // Les mots portent le locuteur : un segment peut en couvrir deux. On
        // coupe à chaque changement — un mot sans locuteur suit le précédent —,
        // et en phrases à l'intérieur d'une même voix.
        val groupes = mutableListOf<Groupe>()
        var g: Groupe? = null
        mots.forEachIndexed { i, w ->
            val l = libelle(premier(w, CLES_LOCUTEUR_MOT)) ?: (if (g != null) g!!.loc else loc)
            val a = secondes(premier(w, listOf("start", "begin")))
            val b = secondes(premier(w, listOf("end", "stop")))
            val m = motDe(w)
            val courant = g
            val actuel: Groupe
            if (courant == null || courant.loc != l || courant.ferme) {
                // `suite` : le groupe commence au milieu d'une phrase ; son point
                // final le fermera alors toujours, même court.
                val suite = courant != null && courant.ferme && courant.loc == l && !courant.point
                actuel = Groupe(l, i, a, b, suite)
                groupes += actuel
                g = actuel
            } else {
                actuel = courant
                if (actuel.debut == null) actuel.debut = a
                if (b != null) actuel.fin = b
            }
            actuel.i1 = i
            actuel.car += m.length + 1
            val d0 = actuel.debut
            val d1 = actuel.fin
            val duree = if (d0 != null && d1 != null) d1 - d0 else 0.0
            actuel.point = FIN_PHRASE.containsMatchIn(m)
            actuel.ferme = (actuel.point && (actuel.suite || duree >= PHRASE_MIN_S || actuel.car >= PHRASE_MIN_CAR)) ||
                (FIN_SOUPLE.containsMatchIn(m) && (duree >= PHRASE_SOUPLE_S || actuel.car >= PHRASE_SOUPLE_CAR)) ||
                duree >= PHRASE_MAX_S || actuel.car >= PHRASE_MAX_CAR
        }

        val positions = debuts
        return groupes.mapIndexed { n, gr ->
            val dernier = n == groupes.size - 1
            // Le texte entre deux mots revient au groupe qui précède : rien ne se
            // perd, ni ponctuation ni élision.
            val t = if (positions != null) {
                nettoyer(texte.substring(if (n == 0) 0 else positions[gr.i0],
                                         if (dernier) texte.length else positions[groupes[n + 1].i0]))
            } else recoller(mots.subList(gr.i0, gr.i1 + 1))
            Segment(gr.debut ?: (if (n == 0) debut else null),
                    gr.fin ?: (if (dernier) fin else null),
                    gr.loc, t)
        }.filter { it.texte.isNotEmpty() }
    }

    /**
     * Place chaque mot dans le texte du segment, pour couper ce texte-là et non
     * des mots recollés : Whisper sépare les élisions (« d' » puis « Leit »).
     */
    private fun aligner(texte: String, mots: List<JSONObject>): IntArray? {
        var pos = 0
        val debuts = IntArray(mots.size)
        mots.forEachIndexed { i, w ->
            val m = motDe(w)
            val k = if (m.isNotEmpty()) texte.indexOf(m, pos) else pos
            if (k < 0 || k - pos > ALIGNEMENT_JEU) return null
            debuts[i] = k
            pos = k + m.length
        }
        return debuts
    }

    private class Trouvee(val i: Int, val bloc: Int, val fois: Int)

    private fun boucles(cles: List<String>): List<Trouvee> {
        val res = mutableListOf<Trouvee>()
        var i = 0
        while (i < cles.size) {
            var trouve: Trouvee? = null
            var b = 1
            while (b <= BOUCLE_BLOC_MAX && trouve == null) {
                var n = 1
                while (i + (n + 1) * b <= cles.size && memeBloc(cles, i, i + n * b, b)) n++
                if (n >= BOUCLE_MIN && cles[i].isNotEmpty()) trouve = Trouvee(i, b, n)
                b++
            }
            if (trouve != null) {
                res += trouve
                i += trouve.bloc * trouve.fois
            } else i++
        }
        return res
    }

    private fun memeBloc(c: List<String>, a: Int, b: Int, n: Int): Boolean {
        for (k in 0 until n) if (c[a + k] != c[b + k]) return false
        return true
    }

    /**
     * Retire de [texte] les répétitions de chaque boucle, de la fin de la
     * première occurrence à la fin de la dernière. Rend `null` sans boucle,
     * sinon le texte et les indices des jetons ôtés.
     */
    private fun sansBoucles(texte: String, jetons: List<Jeton>, journal: MutableList<Boucle>,
                            temps: (Int) -> Double?): Pair<String, Set<Int>>? {
        val trouvees = boucles(jetons.map { it.cle })
        if (trouvees.isEmpty()) return null
        val morceaux = StringBuilder()
        var pos = 0
        val retires = mutableSetOf<Int>()
        for (r in trouvees) {
            val garde = jetons[r.i + r.bloc - 1]
            val dernier = jetons[r.i + r.bloc * r.fois - 1]
            morceaux.append(texte, pos, garde.fin)
            pos = dernier.fin
            for (k in r.i + r.bloc until r.i + r.bloc * r.fois) retires += k
            journal += Boucle(temps(r.i), r.fois, nettoyer(texte.substring(jetons[r.i].debut, garde.fin)))
        }
        morceaux.append(texte, pos, texte.length)
        return nettoyer(morceaux.toString()) to retires
    }

    private fun reduireTexte(texte: String, journal: MutableList<Boucle>, debut: Double?): String {
        val jetons = JETON.findAll(texte).map { Jeton(it.range.first, it.range.last + 1, cle(it.value)) }.toList()
        return sansBoucles(texte, jetons, journal) { debut }?.first ?: texte
    }

    // --- Lecture ------------------------------------------------------------------

    /**
     * Les segments regroupés pour la lecture : les segments consécutifs d'un
     * même locuteur forment un tour de parole. Sans locuteurs, on coupe en
     * paragraphes sur les silences de plus de 2 s. Dans les deux cas, un
     * paragraphe passé TOUR_MAX_S s'arrête à la fin de phrase suivante.
     */
    fun tours(doc: Document): List<Tour> {
        val res = mutableListOf<Pair<Segment, MutableList<Int>>>()
        doc.segments.forEachIndexed { i, sg ->
            val precedent = doc.segments.getOrNull(i - 1)
            val courant = res.lastOrNull()?.first
            val c0 = courant?.debut
            val d = sg.debut
            val coupe = courant == null ||
                (if (doc.diarise) sg.locuteur != courant.locuteur
                 else !(precedent?.fin != null && d != null && d - precedent.fin!! < 2)) ||
                (c0 != null && d != null &&
                 (d - c0 >= TOUR_FORCE_S ||
                  (d - c0 >= TOUR_MAX_S && precedent != null && FIN_PHRASE.containsMatchIn(precedent.texte))))
            if (coupe) res += sg to mutableListOf()
            res.last().second += i
        }
        return res.map { (sg, indices) -> Tour(sg.locuteur, sg.debut, indices) }
    }

    /** `1:02:03` au-delà d'une heure, `2:03` en deçà. */
    fun horloge(s: Double?): String {
        val t = maxOf(0L, Math.floor(s ?: 0.0).toLong())
        val h = t / 3600
        val m = (t / 60) % 60
        val r = t % 60
        return (if (h > 0) "$h:${deux(m)}" else "$m") + ":" + deux(r)
    }

    private fun deux(n: Long) = if (n < 10) "0$n" else "$n"

    /** Le texte lisible, un tour par paragraphe ; [nom] : libellé brut → nom affiché. */
    fun versTexte(doc: Document, nom: (String) -> String): String =
        tours(doc).joinToString("\n\n") { t ->
            val texte = t.indices.joinToString(" ") { doc.segments[it].texte }
            val tete = (if (doc.horodate) "[${horloge(t.debut)}] " else "") +
                       (if (t.locuteur != null) "${nom(t.locuteur)} : " else "")
            tete + texte
        } + "\n"

    // --- Outils -------------------------------------------------------------------

    private fun premier(o: JSONObject, cles: List<String>): Any? {
        for (c in cles) {
            if (!o.has(c) || o.isNull(c)) continue
            val v = o.get(c)
            if (v is String && v.isEmpty()) continue
            return v
        }
        return null
    }

    /** Nombre de secondes, depuis un nombre ou `hh:mm:ss.mmm` / `mm:ss,mmm`. */
    internal fun secondes(v: Any?): Double? {
        if (v is Number) return v.toDouble().takeIf { it.isFinite() }
        if (v !is String) return null
        val s = v.trim().replace(',', '.')
        if (Regex("^\\d+(\\.\\d+)?$").matches(s)) return s.toDouble()
        val m = Regex("^(?:(\\d+):)?(\\d{1,2}):(\\d{1,2}(?:\\.\\d+)?)$").find(s) ?: return null
        val h = m.groupValues[1].ifEmpty { "0" }.toDouble()
        return h * 3600 + m.groupValues[2].toDouble() * 60 + m.groupValues[3].toDouble()
    }

    private fun nettoyer(t: String): String = t.replace(ESPACES, " ").trim()

    private fun libelle(v: Any?): String? = if (v == null || v == "") null else v.toString()

    private fun motDe(w: JSONObject): String = nettoyer(premier(w, CLES_MOT)?.toString() ?: "")

    private fun cle(m: String): String = m.lowercase().replace(HORS_CLE, "")

    /** Recolle des mots à la Whisper, dont l'espace est souvent dans le mot. */
    private fun recoller(mots: List<JSONObject>): String {
        val t = StringBuilder()
        for (w in mots) {
            val m = premier(w, CLES_MOT)?.toString() ?: continue
            if (t.isEmpty() || m.firstOrNull()?.isWhitespace() == true) t.append(m) else t.append(' ').append(m)
        }
        return nettoyer(t.toString())
    }
}
