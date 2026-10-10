package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.security.SecureRandom

/**
 * Céder une carte de la main à la main, par deux codes QR.
 *
 * Le donneur affiche une **offre**, le receveur la scanne, reçoit la carte et
 * affiche une **réception**, que le donneur scanne pour lâcher la carte. Rien
 * ne passe par Internet. Le protocole, ce qu'il garantit et ce qu'il ne
 * garantit pas sont dans `CESSION-CARTES.md` à la racine du dépôt.
 *
 * La partie texte ([offre], [lireOffre], [reception], [lireReception]) n'a pas
 * de `Context`, pour être testée sur la JVM.
 */
object Cession {

    private const val PREFIXE = "LUXKEYB"
    private const val VERSION = "1"

    /** Une offre vaut dix minutes : une capture d'écran ne sert pas le lendemain. */
    const val VALIDITE_S = 10 * 60L
    private const val DERIVE_S = 2 * 60L

    data class Offre(val jeton: String, val echeance: Long, val forme: String, val nombre: Int?)

    private val alea = SecureRandom()

    fun nouveauJeton(): String = buildString {
        repeat(8) { append("%02x".format(alea.nextInt(256))) }
    }

    /** Le texte du code d'offre. La forme vient en dernier : rien ne l'empêche de contenir « : ». */
    fun offre(o: Offre): String =
        listOf(PREFIXE, "O", VERSION, o.jeton, o.echeance.toString(), o.nombre?.toString() ?: "", o.forme)
            .joinToString(":")

    /**
     * Une offre lue sur un code, ou `null` si ce n'en est pas une, si elle est
     * mal formée ou si elle a expiré à [maintenant] (en secondes).
     */
    fun lireOffre(texte: String, maintenant: Long): Offre? {
        val parts = texte.trim().split(":", limit = 7)
        if (parts.size != 7 || parts[0] != PREFIXE || parts[1] != "O" || parts[2] != VERSION) return null
        val jeton = parts[3].takeIf { estJeton(it) } ?: return null
        val echeance = parts[4].toLongOrNull() ?: return null
        val nombre = if (parts[5].isEmpty()) null else parts[5].toIntOrNull() ?: return null
        val forme = parts[6].takeIf { it.isNotBlank() && it.length <= 64 } ?: return null
        // Deux horloges de téléphones ne sont jamais tout à fait d'accord : deux
        // minutes de tolérance dans les deux sens.
        if (maintenant > echeance + DERIVE_S || echeance - maintenant > VALIDITE_S + DERIVE_S) return null
        return Offre(jeton, echeance, forme, nombre)
    }

    fun reception(jeton: String): String = listOf(PREFIXE, "R", VERSION, jeton).joinToString(":")

    /** Le jeton d'un code de réception, ou `null`. */
    fun lireReception(texte: String): String? {
        val parts = texte.trim().split(":")
        if (parts.size != 4 || parts[0] != PREFIXE || parts[1] != "R" || parts[2] != VERSION) return null
        return parts[3].takeIf { estJeton(it) }
    }

    private fun estJeton(s: String) = s.length == 16 && s.all { it in '0'..'9' || it in 'a'..'f' }

    // ---------------------------------------------------------- le receveur

    private const val PREFS = "carnet_cession"
    private const val CLE_RECUS = "recus"
    /** Assez pour qu'un même code ne s'importe pas deux fois, sans grossir sans fin. */
    private const val MEMOIRE_RECUS = 200

    /**
     * Vrai si ce téléphone a déjà reçu l'offre [jeton]. Les jetons vivent dans
     * leur propre fichier de préférences, hors sauvegarde ; ils ne disent rien
     * d'autre qu'un hasard déjà vu.
     */
    fun dejaRecue(context: Context, jeton: String): Boolean =
        jeton in prefs(context).getString(CLE_RECUS, "").orEmpty().split(",")

    fun noterRecue(context: Context, jeton: String) {
        val recus = prefs(context).getString(CLE_RECUS, "").orEmpty()
            .split(",").filter { it.isNotEmpty() }
        val gardes = (recus + jeton).takeLast(MEMOIRE_RECUS)
        prefs(context).edit().putString(CLE_RECUS, gardes.joinToString(",")).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------------------------------------------------------- le dessin

    /** Le code QR de [texte], en noir sur blanc, [cote] pixels de côté. */
    fun codeQR(texte: String, cote: Int): Bitmap {
        val matrice = QRCodeWriter().encode(
            texte, BarcodeFormat.QR_CODE, cote, cote,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 2,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
        )
        val pixels = IntArray(matrice.width * matrice.height) { i ->
            if (matrice.get(i % matrice.width, i / matrice.width)) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, matrice.width, matrice.height, Bitmap.Config.ARGB_8888)
    }
}
