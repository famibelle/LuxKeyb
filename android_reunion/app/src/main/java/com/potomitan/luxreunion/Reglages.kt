package com.potomitan.luxreunion

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.potomitan.luxreunion.noyau.RedacteurClaude
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Les préférences de la secrétaire (son nom, ses mots, son modèle de compte
 * rendu) et celles de la personne qui installe l'application (la clé d'API,
 * le modèle). La clé n'est jamais dans l'APK : elle est saisie une fois sur
 * le téléphone, et chiffrée par une clé du Keystore d'Android, qui ne quitte
 * pas l'appareil.
 */
class Reglages(ctx: Context) {
    private val p = ctx.getSharedPreferences("reglages", Context.MODE_PRIVATE)

    private fun chaine(cle: String, defaut: String = "") = p.getString(cle, defaut) ?: defaut
    private fun ecrire(cle: String, v: String) = p.edit().putString(cle, v).apply()

    /** Qui signe le compte rendu. */
    var redacteur: String get() = chaine("redacteur"); set(v) = ecrire("redacteur", v)
    /** Noms propres, sigles, termes maison : pour LuxASR et pour Claude. */
    var glossaire: String get() = chaine("glossaire"); set(v) = ecrire("glossaire", v)
    /** Un ancien compte rendu, ou des consignes de mise en forme. */
    var modeleCompteRendu: String get() = chaine("modeleCR"); set(v) = ecrire("modeleCR", v)

    var modeleClaude: String
        get() = chaine("modeleClaude").ifBlank { RedacteurClaude.MODELE }
        set(v) = ecrire("modeleClaude", v.trim())

    /** La transcription complète (voix séparées) après une réunion en direct. */
    var lotsApresDirect: Boolean
        get() = p.getBoolean("lotsApresDirect", true)
        set(v) = p.edit().putBoolean("lotsApresDirect", v).apply()

    /** Les explications du premier lancement ont été lues. */
    var accordVu: Boolean
        get() = p.getBoolean("accordVu", false)
        set(v) = p.edit().putBoolean("accordVu", v).apply()

    /** Taille du texte pendant la réunion (A− / A+). */
    var echelle: Float
        get() = p.getFloat("echelle", 1f)
        set(v) = p.edit().putFloat("echelle", v).apply()

    var cle: String?
        get() = p.getString("cle", null)?.let { Coffre.dechiffrer(it) }
        set(v) {
            if (v.isNullOrBlank()) p.edit().remove("cle").apply()
            else p.edit().putString("cle", Coffre.chiffrer(v.trim())).apply()
        }

    val aUneCle: Boolean get() = p.contains("cle")
}

/** AES-GCM, clé dans le Keystore d'Android. */
private object Coffre {
    private const val ALIAS = "luxreunion-cle-api"

    private fun cle(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return kg.generateKey()
    }

    fun chiffrer(texte: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, cle())
        val b64 = Base64.getEncoder()
        return b64.encodeToString(c.iv) + ":" + b64.encodeToString(c.doFinal(texte.toByteArray(Charsets.UTF_8)))
    }

    fun dechiffrer(s: String): String? = try {
        val (iv, donnees) = s.split(':', limit = 2)
        val b64 = Base64.getDecoder()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, cle(), GCMParameterSpec(128, b64.decode(iv)))
        String(c.doFinal(b64.decode(donnees)), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }
}
