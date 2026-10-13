package com.example.kreyolkeyboard

import android.content.ContextWrapper
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile
import kotlin.random.Random

/**
 * Banc de correction : des fautes de frappe injectées dans des phrases que le
 * clavier n'a jamais vues, et ce que la rangée luxembourgeoise propose.
 *
 * Ne tourne que sur demande, il prend une à deux minutes :
 * ```
 * BANC_CORRECTION=1 ./gradlew testDebugUnitTest --tests "*BancCorrectionTest" --rerun
 * ```
 * `--rerun` est indispensable : Gradle ne voit pas les variables
 * d'environnement et rendrait sinon le résultat d'un passage précédent, tiré
 * de son cache. `BANC_RATIOS="off,16,64"` produit un tableau par seuil de
 * correction sur préfixe (`64@4` : seuil 64 à partir de 4 lettres).
 * Le tableau s'affiche et s'écrit dans `app/build/banc-correction.md`.
 *
 * **Les phrases** viennent du corpus de traduction de la ZLS (CC0), qui
 * n'entre pas dans l'entraînement ; l'archive est celle que
 * `Dictionnaires/zls_source.py` met en cache dans `Dictionnaires/zls_data/`
 * (lancer `python Dictionnaires/zls_source.py` une fois si elle manque).
 *
 * **Le moteur** est le vrai : [SuggestionEngine.rangeeLuxembourgeoise] suit le
 * chemin de la barre (complétion, n-grammes du contexte, correction, casse,
 * filtre des grossièretés), sans le français, éteint par défaut.
 *
 * **Les fautes** sont tirées avec une graine fixe, une par mot, sur le mot
 * entier tapé d'un coup (la barre au moment d'appuyer sur espace). Les
 * touches voisines sont celles de la disposition par défaut, calculées par
 * [VoisinsClavier] : c'est la géométrie de l'écran, la même pour le doigt et
 * pour le moteur.
 *
 * Deux lignes ne mesurent pas une faute :
 * - « mot juste » : le mot tapé sans faute. Il doit rester en tête ; chaque
 *   fois qu'autre chose passe devant, c'est un mot juste qu'un remplacement
 *   automatique abîmerait (étape 5 du plan).
 * - « minuscule » : un nom tapé sans sa majuscule, réussi seulement si la
 *   barre rend la casse exacte.
 */
class BancCorrectionTest {

    private val parType = 2000
    private val graine = 20261013

    private val assets = File("src/main/assets")

    private fun moteur(): SuggestionEngine {
        val m = SuggestionEngine(ContextWrapper(null))
        m.ouvrirActif = { File(assets, it).inputStream() }
        runBlocking { m.chargerLuxembourgeois() }
        return m
    }

    /** Segments luxembourgeois du corpus ZLS, depuis l'archive en cache. */
    private fun phrases(): List<String> {
        val dossier = File("../../Dictionnaires/zls_data")
        val archive = dossier.listFiles { f -> f.name.endsWith(".zip") }?.maxByOrNull { it.name }
            ?: error("archive ZLS absente de ${dossier.absolutePath} : lancer zls_source.py")
        ZipFile(archive).use { zip ->
            val entree = zip.entries().asSequence().first { it.name.endsWith(".jsonl") }
            return zip.getInputStream(entree).bufferedReader().readLines()
                .filter { it.isNotBlank() }
                .map { JSONObject(it).getString("lb") }
        }
    }

    /** Les mots comme le clavier les découpe : des suites de lettres. */
    private fun mots(phrase: String): List<String> =
        Regex("\\p{L}+").findAll(phrase).map { it.value }.toList()

    private data class Occurrence(val mot: String, val avant: List<String>, val enTete: Boolean, val apres: String?)

    private data class Ligne(
        var n: Int = 0, var top1: Int = 0, var top3: Int = 0, var vide: Int = 0,
        var vraiMot: Int = 0, var nanos: Long = 0,
        var prefixe: Int = 0, var rateSurPrefixe: Int = 0
    )

    @Test
    fun banc() {
        assumeTrue("BANC_CORRECTION=1 pour lancer le banc", System.getenv("BANC_CORRECTION") == "1")

        val moteur = moteur()
        val connus = HashSet<String>()
        JSONArray(File(assets, "luxemburgish_dict.json").readText()).let { t ->
            for (i in 0 until t.length()) connus.add(t.getJSONArray(i).getString(0).lowercase())
        }
        JSONObject(File(assets, "luxemburgish_lod_forms.json").readText()).getJSONArray("suggest").let { t ->
            for (i in 0 until t.length()) connus.add(t.getString(i).lowercase())
        }

        // Formes repliées triées : « la faute est-elle le début d'un autre
        // mot ? » par dichotomie. C'est le cas où le moteur complète au lieu
        // de corriger (étape 3 du plan).
        val replies = connus.map { AccentTolerantMatcher.normalize(it) }.sorted()
        fun debutDeMot(tape: String): Boolean {
            val t = AccentTolerantMatcher.normalize(tape.lowercase())
            var i = replies.binarySearch(t)
            if (i < 0) i = -i - 1
            return i < replies.size && replies[i].startsWith(t)
        }

        val occurrences = ArrayList<Occurrence>()
        val inconnus = ArrayList<Occurrence>()
        var motsVus = 0
        var horsVocabulaire = 0
        for (phrase in phrases()) {
            val m = mots(phrase)
            for ((i, mot) in m.withIndex()) {
                motsVus++
                if (mot.lowercase() !in connus) {
                    horsVocabulaire++
                    inconnus.add(Occurrence(mot, m.subList(maxOf(0, i - 2), i), i == 0, m.getOrNull(i + 1)))
                    continue
                }
                occurrences.add(Occurrence(mot, m.subList(maxOf(0, i - 2), i), i == 0, m.getOrNull(i + 1)))
            }
        }
        occurrences.shuffle(Random(graine))

        var alea = Random(graine)
        val voisins = VoisinsClavier.de(DispositionClavier.DEFAUT)
        val alphabet = ('a'..'z').toList()
        fun voisinDe(c: Char): Char? = alphabet.filter { voisins.sontVoisines(c, it) }.randomOrNull(alea)

        // Chaque type : (nom, faute à injecter ou null si le mot ne s'y prête pas)
        val types: List<Pair<String, (String) -> String?>> = listOf(
            "touche voisine" to { w ->
                val pos = w.indices.filter { w[it].lowercaseChar() in alphabet }
                pos.randomOrNull(alea)?.let { p ->
                    voisinDe(w[p].lowercaseChar())?.let { v ->
                        w.substring(0, p) + (if (w[p].isUpperCase()) v.uppercaseChar() else v) + w.substring(p + 1)
                    }
                }
            },
            "lettre oubliée" to { w ->
                if (w.length < 4) null else alea.nextInt(1, w.length).let { p -> w.removeRange(p, p + 1) }
            },
            "lettre en trop" to { w ->
                val p = alea.nextInt(w.length)
                val c = w[p].lowercaseChar()
                val ajout = if (alea.nextBoolean()) c else (voisinDe(c) ?: c)
                w.substring(0, p + 1) + ajout + w.substring(p + 1)
            },
            "lettres inversées" to { w ->
                val pos = (0 until w.length - 1).filter { w[it] != w[it + 1] }
                pos.randomOrNull(alea)?.let { p -> w.substring(0, p) + w[p + 1] + w[p] + w.substring(p + 2) }
            },
            "accent oublié" to { w ->
                val sans = AccentTolerantMatcher.normalize(w.lowercase())
                if (sans == w.lowercase()) null
                else sans.mapIndexed { i, c -> if (w[i].isUpperCase()) c.uppercaseChar() else c }.joinToString("")
            },
            "deux fautes (voisine + oubli)" to { w ->
                if (w.length < 5) null else {
                    val p = alea.nextInt(1, w.length)
                    val sans = w.removeRange(p, p + 1)
                    val q = sans.indices.filter { sans[it].lowercaseChar() in alphabet }.randomOrNull(alea)
                    q?.let { qq -> voisinDe(sans[qq].lowercaseChar())?.let { v -> sans.substring(0, qq) + v + sans.substring(qq + 1) } }
                }
            },
            "première lettre voisine" to { w ->
                if (w[0].lowercaseChar() !in alphabet) null
                else voisinDe(w[0].lowercaseChar())?.let { v ->
                    (if (w[0].isUpperCase()) v.uppercaseChar() else v) + w.substring(1)
                }
            },
            "lettre doublée simple" to { w ->
                val pos = (1 until w.length).filter { w[it] == w[it - 1] }
                pos.randomOrNull(alea)?.let { p -> w.removeRange(p, p + 1) }
            },
            "deux touches voisines" to { w ->
                if (w.length < 5) null else {
                    val pos = w.indices.filter { w[it].lowercaseChar() in alphabet }.shuffled(alea).take(2)
                    if (pos.size < 2) null else {
                        val c = w.toCharArray()
                        var ok = true
                        for (p in pos) { val v = voisinDe(c[p].lowercaseChar()); if (v == null) ok = false else c[p] = v }
                        if (ok) String(c) else null
                    }
                }
            },
            "touche voisine, mot de 3-4 lettres" to { w ->
                if (w.length > 4) null else w.indices.filter { w[it].lowercaseChar() in alphabet }.randomOrNull(alea)?.let { p ->
                    voisinDe(w[p].lowercaseChar())?.let { v -> w.substring(0, p) + v + w.substring(p + 1) }
                }
            },
            "touche voisine, mot de 8 lettres et plus" to { w ->
                if (w.length < 8) null else w.indices.filter { w[it].lowercaseChar() in alphabet }.randomOrNull(alea)?.let { p ->
                    voisinDe(w[p].lowercaseChar())?.let { v -> w.substring(0, p) + v + w.substring(p + 1) }
                }
            },
            "mot juste" to { w -> w },
        )

        // BANC_RATIOS="1,2" : un tableau par seuil de correction sur préfixe
        // ("off" pour ne jamais corriger un début de mot).
        val ratios = (System.getenv("BANC_RATIOS") ?: moteur.ratioCorrectionSurPrefixe.toString())
            .split(",").map { it.trim() }
        val sortie = StringBuilder()
        for (ratio in ratios) {
        // « 4@5 » : seuil 4, à partir de 5 lettres.
        val (r, lg) = (ratio.split("@") + listOf("")).let { it[0] to it[1] }
        moteur.ratioCorrectionSurPrefixe = if (r == "off") Double.MAX_VALUE else r.toDouble()
        if (lg.isNotEmpty()) moteur.longueurCorrectionSurPrefixe = lg.toInt()
        // BANC_DOUBLEMENT=9 : multiplicateur des corrections par lettre doublée.
        System.getenv("BANC_DOUBLEMENT")?.let { moteur.facteurDoublement = it.toInt() }
        alea = Random(graine)
        val resultats = LinkedHashMap<String, Ligne>()
        fun mesurer(nom: String, occ: Occurrence, tape: String, attendu: String, casseExacte: Boolean) {
            val l = resultats.getOrPut(nom) { Ligne() }
            moteur.clearHistory()
            occ.avant.forEach { moteur.addWordToHistory(it) }
            val debut = System.nanoTime()
            val barre = moteur.rangeeLuxembourgeoise(tape)
            l.nanos += System.nanoTime() - debut
            l.n++
            if (tape.lowercase() != attendu.lowercase() && tape.lowercase() in connus) l.vraiMot++
            fun egal(a: String) = if (casseExacte) a == attendu else a.equals(attendu, ignoreCase = true)
            if (barre.isEmpty()) l.vide++
            if (barre.firstOrNull()?.let(::egal) == true) l.top1++
            val dansLes3 = barre.take(3).any(::egal)
            if (System.getenv("BANC_DETAILS") == nom && l.n <= 400 && !barre.take(3).any(::egal)) {
                println("RATE\t$tape\t$attendu\t$barre")
            }
            if (dansLes3) l.top3++
            if (tape.lowercase() != attendu.lowercase() && debutDeMot(tape)) {
                l.prefixe++
                if (!dansLes3) l.rateSurPrefixe++
            }
        }

        for ((nom, fauter) in types) {
            var pris = 0
            for (occ in occurrences) {
                if (pris >= parType) break
                if (occ.mot.length < 3) continue
                val tape = fauter(occ.mot) ?: continue
                if (nom != "mot juste" && tape == occ.mot) continue
                mesurer(nom, occ, tape, occ.mot, casseExacte = false)
                pris++
            }
        }
        // Un mot juste en cours de frappe : le début seul doit déjà le
        // proposer. Ce que la correction ne doit pas abîmer.
        run {
            var pris = 0
            for (occ in occurrences) {
                if (pris >= parType) break
                if (occ.mot.length < 5) continue
                val n = alea.nextInt(3, occ.mot.length)
                mesurer("mot juste en cours de frappe", occ, occ.mot.substring(0, n), occ.mot, casseExacte = false)
                pris++
            }
        }
        // Un nom au milieu d'une phrase, tapé tout en minuscules.
        run {
            var pris = 0
            for (occ in occurrences) {
                if (pris >= parType) break
                if (occ.enTete || occ.mot.length < 3 || !occ.mot[0].isUpperCase() || occ.mot.drop(1).any { it.isUpperCase() }) continue
                mesurer("minuscule (casse exacte)", occ, occ.mot.lowercase(), occ.mot, casseExacte = true)
                pris++
            }
        }
        // Un mot juste mais absent du dictionnaire (un composé, un nom) : la
        // barre ne doit pas proposer de le couper en deux. Ici « 1re place »
        // et « dans les 3 » comptent les fois où AUCUNE coupure n'y figure.
        run {
            val l = resultats.getOrPut("mot inconnu non coupé") { Ligne() }
            for (occ in inconnus.shuffled(Random(graine)).take(parType)) {
                if (occ.mot.length < 4) continue
                moteur.clearHistory()
                occ.avant.forEach { moteur.addWordToHistory(it) }
                val debut = System.nanoTime()
                val barre = moteur.rangeeLuxembourgeoise(occ.mot)
                l.nanos += System.nanoTime() - debut
                l.n++
                if (barre.isEmpty()) l.vide++
                if (barre.firstOrNull()?.contains(' ') != true) l.top1++
                if (System.getenv("BANC_DETAILS") == "mot inconnu non coupé" && barre.take(3).any { ' ' in it }) {
                    println("COUPE\t${occ.mot}\t$barre")
                }
                if (barre.take(3).none { ' ' in it }) l.top3++
            }
        }
        // Deux mots collés : la barre propose-t-elle les deux mots séparés ?
        run {
            var pris = 0
            for (occ in occurrences) {
                if (pris >= parType) break
                val suivant = occ.apres ?: continue
                if (occ.mot.length < 2 || suivant.length < 2 || suivant.lowercase() !in connus) continue
                mesurer("mots collés", occ, occ.mot + suivant.lowercase(), occ.mot + " " + suivant, casseExacte = false)
                pris++
            }
        }

        // Vitesse : des phrases tapées lettre à lettre, une barre par frappe,
        // comme sur le téléphone. Le JVM d'un ordinateur va bien plus vite
        // qu'un Galaxy A21s : ce sont les écarts qui comptent, pas l'absolu.
        val durees = ArrayList<Long>()
        run {
            val toutes = phrases().shuffled(Random(graine)).take(300)
            for (phrase in toutes) {
                moteur.clearHistory()
                for (mot in mots(phrase)) {
                    for (n in 1..mot.length) {
                        val debut = System.nanoTime()
                        moteur.rangeeLuxembourgeoise(mot.substring(0, n))
                        durees.add(System.nanoTime() - debut)
                    }
                    moteur.addWordToHistory(mot)
                }
            }
            durees.sort()
        }
        val pct = { a: Int, n: Int -> if (n == 0) "–" else "%.1f %%".format(100.0 * a / n) }
        val texte = buildString {
            appendLine("# Banc de correction, seuil sur préfixe : $ratio, lettre doublée × ${moteur.facteurDoublement}")
            appendLine()
            appendLine("Corpus ZLS : $motsVus mots, ${pct(horsVocabulaire, motsVus)} hors vocabulaire (exclus : aucune correction ne peut les rendre).")
            appendLine("Graine $graine, $parType fautes par type au plus, disposition ${DispositionClavier.DEFAUT.cle}.")
            appendLine()
            val q = { f: Double -> "%.2f".format(durees[((durees.size - 1) * f).toInt()] / 1e6) }
            appendLine("Vitesse, ${durees.size} frappes sur 300 phrases : médiane ${q(0.5)} ms, 95 % sous ${q(0.95)} ms, 99 % sous ${q(0.99)} ms, pire ${q(1.0)} ms.")
            appendLine()
            appendLine("| Type | n | 1re place | dans les 3 | barre vide | faute = autre vrai mot | faute = début d'un mot | dont ratées | ms / mot |")
            appendLine("|---|---:|---:|---:|---:|---:|---:|---:|---:|")
            for ((nom, l) in resultats) {
                appendLine("| $nom | ${l.n} | ${pct(l.top1, l.n)} | ${pct(l.top3, l.n)} | ${pct(l.vide, l.n)} | " +
                    "${pct(l.vraiMot, l.n)} | ${pct(l.prefixe, l.n)} | ${pct(l.rateSurPrefixe, l.n)} | ${"%.2f".format(l.nanos / 1e6 / maxOf(l.n, 1))} |")
            }
        }
        println(texte)
        sortie.appendLine(texte)
        }
        File("build/banc-correction.md").writeText(sortie.toString())
    }
}
