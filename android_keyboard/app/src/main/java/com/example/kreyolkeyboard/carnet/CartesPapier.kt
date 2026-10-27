package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Le code d'une carte en papier : un code QR **signé**, imprimé sur une carte
 * code de la boîte ou d'un paquet, qui verse ses cartes dans le carnet.
 *
 * ## Le code
 *
 * `LUXKEYB:P:1:<série>:<cartes>:<signature>`
 *
 * - la **série**, douze chiffres hexadécimaux tirés au hasard à l'impression :
 *   un même code ne sert qu'une fois sur un téléphone ;
 * - les **cartes**, séparées par des virgules : un mot (`Kaz`), un numéral avec
 *   sa valeur (`eent#1`) ou un ensemble de `cartes_imprimees.json` (`@B1`, la
 *   boîte de départ) ;
 * - la **signature** ECDSA P-256 (SHA-256, DER, base64 URL sans `=`) de tout
 *   ce qui précède, faite par `scripts/codes_cartes.py` avec la clé privée,
 *   qui ne quitte pas `impression/` (hors dépôt). L'appli n'a que la clé
 *   publique ([CLE_PUBLIQUE]) : elle vérifie sans Internet, et personne ne
 *   fabrique un code sans la clé.
 *
 * ## Ce qu'un code ne fait pas
 *
 * Hors ligne, un téléphone ne sait pas qu'un autre a déjà scanné le même
 * code : une photo du code sert plusieurs fois. C'est accepté (usage scolaire
 * toléré, skill imprimeur § 6) ; le code protège les cartes qui ne se gagnent
 * pas autrement, comme la [CarteFierkelsmaart], pas l'exclusivité d'une
 * boîte.
 *
 * Pourquoi un code et pas la photo de la carte : la lecture d'une photo
 * demandait ML Kit, qui envoie des statistiques à Google (propriétaire,
 * 2026-10-26), ou un moteur hors ligne de plusieurs mégaoctets.
 *
 * La partie texte ([lire], [cartes]) prend sa clé en paramètre et n'a pas de
 * `Context`, pour être testée sur la JVM.
 */
object CartesPapier {

    private const val TAG = "CartesPapier"
    private const val PREFIXE = "LUXKEYB:P:1:"

    /**
     * La clé publique de `impression/cle_cartes/privee.pem`, en X.509 base64.
     * La première clé (01fc926) a été remplacée le jour même : un test publiait
     * des codes signés qui donnaient la boîte et la Fierkelsmaart. Aucun code
     * signé ne va dans le dépôt, sauf pour des cartes qui se gagnent au clavier.
     */
    private const val CLE_PUBLIQUE =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEdrP6FOhPkvCqQA/uh2hP/ND44R4GxKPgoULBIe0kJQXz+pY4EzHocj6hu/k2B10UnbYaeypZ+uJviuuUxGSwqg=="

    private const val ACTIF = "cartes_imprimees.json"
    private const val PREFS = "carnet_papier"
    private const val CLE_SERIES = "series"
    /** Assez pour qu'un code ne serve pas deux fois, sans grossir sans fin. */
    private const val MEMOIRE_SERIES = 500

    data class Code(val serie: String, val elements: List<String>)

    data class CarteImprimee(val forme: String, val nombre: Int?)

    val clePublique: PublicKey by lazy { cle(CLE_PUBLIQUE) }

    fun cle(base64: String): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(base64(base64)))

    /** Vrai pour un texte qui se présente comme un code de carte en papier, signé ou non. */
    fun estCode(texte: String): Boolean = texte.trim().startsWith(PREFIXE)

    // ---------------------------------------------------------- les textes

    /** Le code lu dans [texte], ou `null` s'il est mal formé ou si sa signature ne tient pas. */
    fun lire(texte: String, cle: PublicKey = clePublique): Code? {
        val t = texte.trim()
        if (!t.startsWith(PREFIXE)) return null
        val coupe = t.lastIndexOf(':')
        if (coupe <= PREFIXE.length) return null
        val signe = t.substring(0, coupe)
        val champs = signe.removePrefix(PREFIXE).split(":")
        if (champs.size != 2) return null
        val (serie, cartes) = champs
        if (serie.length != 12 || !serie.all { it in '0'..'9' || it in 'a'..'f' }) return null
        val elements = cartes.split(",").filter { it.isNotEmpty() }
        if (elements.isEmpty() || elements.any { it.length > 64 }) return null
        val signature = try {
            base64(t.substring(coupe + 1))
        } catch (e: IllegalArgumentException) {
            return null
        }
        val juste = try {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(cle)
                update(signe.toByteArray(Charsets.UTF_8))
                verify(signature)
            }
        } catch (e: Exception) {
            false
        }
        return if (juste) Code(serie, elements) else null
    }

    /**
     * Les cartes d'un code, ensembles déployés et doublons retirés, ou `null`
     * si le code nomme un ensemble que cette version de l'appli ne connaît
     * pas : une boîte plus récente que l'appli.
     */
    fun cartes(code: Code, ensembles: Map<String, List<String>>): List<CarteImprimee>? {
        val vues = LinkedHashMap<String, CarteImprimee>()
        for (e in code.elements) {
            val liste = if (e.startsWith("@")) ensembles[e.substring(1)] ?: return null else listOf(e)
            for (element in liste) carte(element)?.let { vues.putIfAbsent(it.forme, it) }
        }
        return vues.values.toList()
    }

    /** `Kaz` ou `eent#1`. */
    internal fun carte(element: String): CarteImprimee? {
        val (forme, valeur) = element.split("#", limit = 2).let { it[0] to it.getOrNull(1) }
        if (forme.isBlank()) return null
        return CarteImprimee(forme, valeur?.let { it.toIntOrNull() ?: return null })
    }

    /**
     * Décode le base64 standard ou URL, avec ou sans `=`. `java.util.Base64`
     * n'existe qu'à partir de l'API 26 et `android.util.Base64` pas sur la JVM.
     */
    internal fun base64(s: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val sortie = java.io.ByteArrayOutputStream()
        var tampon = 0
        var bits = 0
        for (ch in s.trimEnd('=')) {
            val v = when (ch) {
                '+', '-' -> 62
                '/', '_' -> 63
                else -> alphabet.indexOf(ch).takeIf { it >= 0 } ?: throw IllegalArgumentException("base64 : $ch")
            }
            tampon = (tampon shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                sortie.write((tampon shr bits) and 0xFF)
            }
        }
        return sortie.toByteArray()
    }

    // ---------------------------------------------------------- l'appareil

    @Volatile
    private var ensembles: Map<String, List<String>>? = null

    /** Les ensembles de `cartes_imprimees.json` (écrit par `scripts/candidats_cartes.py`). */
    fun ensembles(context: Context): Map<String, List<String>> = ensembles ?: try {
        val racine = JSONObject(context.assets.open(ACTIF).bufferedReader().use { it.readText() })
            .getJSONObject("ensembles")
        racine.keys().asSequence().associateWith { id ->
            val cartes = racine.getJSONObject(id).getJSONArray("cartes")
            (0 until cartes.length()).map { cartes.getString(it) }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Ensembles illisibles: ${e.message}")
        emptyMap()
    }.also { ensembles = it }

    @Volatile
    private var paquets: Map<String, List<Pair<String, Int>>>? = null

    /**
     * Les « paquets » de chaque ensemble de `cartes_imprimees.json` : leur nom
     * et leur nombre de cartes, dans l'ordre des cartes. Lus pour le
     * médaillon de la carte imprimée ([ImpressionCarte.paquet]).
     */
    fun paquets(context: Context): Map<String, List<Pair<String, Int>>> = paquets ?: try {
        val racine = JSONObject(context.assets.open(ACTIF).bufferedReader().use { it.readText() })
            .getJSONObject("ensembles")
        racine.keys().asSequence().associateWith { id ->
            val liste = racine.getJSONObject(id).optJSONArray("paquets")
            if (liste == null) emptyList()
            else (0 until liste.length()).map { k -> liste.getJSONArray(k).let { it.getString(0) to it.getInt(1) } }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Paquets illisibles: ${e.message}")
        emptyMap()
    }.also { paquets = it }

    fun dejaUtilise(context: Context, serie: String): Boolean = serie in series(context)

    fun noterUtilise(context: Context, serie: String) {
        val gardees = (series(context) + serie).takeLast(MEMOIRE_SERIES)
        // Hors sauvegarde, comme les jetons de Cession : backup_rules.xml est une liste blanche.
        prefs(context).edit().putString(CLE_SERIES, gardees.joinToString(",")).commit()
    }

    private fun series(context: Context): List<String> =
        prefs(context).getString(CLE_SERIES, "").orEmpty().split(",").filter { it.isNotEmpty() }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
