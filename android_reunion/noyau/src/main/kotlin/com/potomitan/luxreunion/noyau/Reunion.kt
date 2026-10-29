package com.potomitan.luxreunion.noyau

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/*
 * Une réunion, et tout ce qu'on en garde, dans un dossier à elle, dans le
 * stockage privé de l'application (jamais sauvegardé ailleurs : allowBackup
 * est à false). On y écrit au fil de l'eau, pour qu'une application tuée en
 * pleine réunion ne perde ni l'enregistrement ni le texte déjà reçu :
 *
 *     meta.json            ce qui suit (titre, participants, étape…)
 *     direct.json          les paragraphes du direct, traduction comprise
 *     audio.pcm            l'enregistrement brut, 16 kHz mono 16 bits
 *     source.<ext>         le fichier choisi, en mode « un enregistrement »
 *     partie-<n>.m4a       l'enregistrement compressé, envoyé à LuxASR
 *     lots-<n>.json        la réponse de LuxASR pour cette partie
 *     transcription.txt    la transcription complète, voix séparées
 *     compte-rendu.txt     le compte rendu, tel que la secrétaire l'a corrigé
 *
 * Les fichiers audio sont effacés dès que la transcription complète est
 * obtenue : ils ne servent plus à rien, et une réunion de direction n'a pas à
 * traîner dans un téléphone.
 */
class Reunion(val dossier: File) {

    enum class Mode { DIRECT, FICHIER }

    enum class Etape {
        /** Le direct tourne : le service d'écoute écrit l'enregistrement. */
        ECOUTE,
        /** L'audio est prêt : il reste à le compresser et à l'envoyer. */
        ENREGISTREE,
        /** Les parties sont chez LuxASR, ou en route. */
        TRANSCRIPTION,
        /** La transcription est là : il reste le compte rendu. */
        REDACTION,
        PRETE
    }

    /** Une partie de l'enregistrement, envoyée comme un travail à part. */
    data class Partie(
        val fichier: String,
        val type: String,
        val debutS: Double,
        val job: String? = null,
        val resultat: String? = null
    )

    val id: String get() = dossier.name
    var titre = ""
    var creee = 0L
    var mode = Mode.DIRECT
    var participants: List<String> = emptyList()
    var ordreDuJour = ""
    var etape = Etape.ECOUTE
    var dureeS = 0.0
    var nomSource: String? = null
    var parties: List<Partie> = emptyList()
    /** Le dernier échec, en clair pour la secrétaire ; `null` quand tout va bien. */
    var erreur: String? = null
    /** Le compte rendu attend une clé d'API : le traitement ne peut aller plus loin. */
    var sansCle = false
    /** La transcription complète a échoué pour de bon : le compte rendu part du direct. */
    var depuisDirect = false

    val fichierMeta get() = File(dossier, "meta.json")
    val fichierDirect get() = File(dossier, "direct.json")
    val fichierPcm get() = File(dossier, "audio.pcm")
    val fichierTranscription get() = File(dossier, "transcription.txt")
    val fichierCompteRendu get() = File(dossier, "compte-rendu.txt")
    fun fichier(nom: String) = File(dossier, nom)

    fun enregistrer() {
        val o = JSONObject()
            .put("titre", titre)
            .put("creee", creee)
            .put("mode", mode.name)
            .put("participants", JSONArray(participants))
            .put("ordreDuJour", ordreDuJour)
            .put("etape", etape.name)
            .put("dureeS", dureeS)
            .put("nomSource", nomSource ?: JSONObject.NULL)
            .put("erreur", erreur ?: JSONObject.NULL)
            .put("sansCle", sansCle)
            .put("depuisDirect", depuisDirect)
            .put("parties", JSONArray(parties.map { p ->
                JSONObject().put("fichier", p.fichier).put("type", p.type).put("debutS", p.debutS)
                    .put("job", p.job ?: JSONObject.NULL).put("resultat", p.resultat ?: JSONObject.NULL)
            }))
        ecrireAtomique(fichierMeta, o.toString(2))
    }

    fun charger(): Reunion {
        val o = JSONObject(fichierMeta.readText())
        titre = o.optString("titre", "")
        creee = o.optLong("creee", 0L)
        mode = enumOu(o, "mode", Mode.DIRECT)
        participants = o.optJSONArray("participants")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
        ordreDuJour = o.optString("ordreDuJour", "")
        etape = enumOu(o, "etape", Etape.ENREGISTREE)
        dureeS = o.optDouble("dureeS", 0.0)
        nomSource = DirectLuxAsr.chaine(o, "nomSource")
        erreur = DirectLuxAsr.chaine(o, "erreur")
        sansCle = o.optBoolean("sansCle", false)
        depuisDirect = o.optBoolean("depuisDirect", false)
        parties = o.optJSONArray("parties")?.let { a ->
            (0 until a.length()).map { i ->
                val p = a.getJSONObject(i)
                Partie(p.optString("fichier"), p.optString("type"), p.optDouble("debutS", 0.0),
                       DirectLuxAsr.chaine(p, "job"), DirectLuxAsr.chaine(p, "resultat"))
            }
        } ?: emptyList()
        return this
    }

    // --- Direct ---------------------------------------------------------------

    fun enregistrerDirect(paragraphes: List<Paragraphe>) {
        val a = JSONArray()
        for (p in paragraphes) {
            a.put(JSONObject().put("debut", p.debut).put("texte", p.texte)
                .put("traduction", p.traduction ?: JSONObject.NULL).put("lacune", p.lacune))
        }
        ecrireAtomique(fichierDirect, a.toString())
    }

    fun lireDirect(): List<Paragraphe> {
        if (!fichierDirect.exists()) return emptyList()
        return try {
            val a = JSONArray(fichierDirect.readText())
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Paragraphe(o.optDouble("debut", 0.0), o.optString("texte", ""),
                           traduction = DirectLuxAsr.chaine(o, "traduction"), lacune = o.optBoolean("lacune", false))
            }
        } catch (e: Exception) { emptyList() }
    }

    /** Les fichiers audio, qu'on efface une fois la transcription complète obtenue. */
    fun effacerAudio() {
        fichierPcm.delete()
        parties.forEach { File(dossier, it.fichier).delete() }
        dossier.listFiles()?.filter { it.name.startsWith("source.") }?.forEach { it.delete() }
    }

    companion object {
        private inline fun <reified E : Enum<E>> enumOu(o: JSONObject, cle: String, defaut: E): E =
            try { enumValueOf<E>(o.optString(cle, defaut.name)) } catch (e: IllegalArgumentException) { defaut }

        /** Écrit à côté puis renomme : un fichier coupé en deux ne remplace jamais le bon. */
        fun ecrireAtomique(f: File, texte: String) {
            val tmp = File(f.path + ".tmp")
            tmp.writeText(texte)
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }
}

/** Toutes les réunions, une par dossier. */
class Registre(val racine: File) {

    init { racine.mkdirs() }

    fun toutes(): List<Reunion> =
        (racine.listFiles() ?: emptyArray())
            .filter { File(it, "meta.json").exists() }
            .mapNotNull { d -> try { Reunion(d).charger() } catch (e: Exception) { null } }
            .sortedByDescending { it.creee }

    fun lire(id: String): Reunion? {
        val d = File(racine, id)
        return if (File(d, "meta.json").exists()) try { Reunion(d).charger() } catch (e: Exception) { null } else null
    }

    fun nouvelle(maintenant: Long, mode: Reunion.Mode): Reunion {
        val base = java.text.SimpleDateFormat("yyyy-MM-dd-HHmmss", java.util.Locale.ROOT).format(java.util.Date(maintenant))
        var d = File(racine, base)
        var n = 2
        while (d.exists()) d = File(racine, "$base-${n++}")
        d.mkdirs()
        return Reunion(d).apply { creee = maintenant; this.mode = mode }
    }

    fun supprimer(r: Reunion) { r.dossier.deleteRecursively() }
}
