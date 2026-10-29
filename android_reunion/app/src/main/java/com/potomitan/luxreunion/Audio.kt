package com.potomitan.luxreunion

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Process
import com.potomitan.luxreunion.noyau.Reunion
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import kotlin.concurrent.thread
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Le micro, en 16 kHz mono 16 bits : le format du flux de LuxASR, et celui
 * qu'on enregistre. Comme la dictée du clavier (AudioRecorder.kt) : source
 * VOICE_RECOGNITION, réglée pour la reconnaissance et sans le traitement
 * agressif du mode téléphonie, tampon de quatre fois le minimum.
 */
class Capture(
    /**
     * Builds debug seulement : un enregistrement PCM 16 kHz mono 16 bits lu au
     * rythme réel à la place du micro, pour essayer le direct sur un émulateur
     * dont le micro ne transmet rien (WSL). Une fois le fichier fini, du silence.
     */
    private val fichierTest: File? = null,
    private val surBloc: (pcm: ByteArray, niveau: Float) -> Unit
) {
    @Volatile private var actif = false
    private var record: AudioRecord? = null
    private var fil: Thread? = null

    @SuppressLint("MissingPermission")
    fun demarrer(): Boolean {
        fichierTest?.let { return demarrerFichier(it) }
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return false
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 4, BLOC * 2 * 4))
        } catch (e: Throwable) { return false }
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return false }
        try { rec.startRecording() } catch (e: Throwable) { rec.release(); return false }
        // Une capture qui démarre mais reste arrêtée ne rend que du silence.
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) { rec.release(); return false }
        record = rec
        actif = true
        fil = thread(name = "reunion-micro") {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val s = ShortArray(BLOC)
            while (actif) {
                var lus = 0
                while (lus < BLOC && actif) {
                    val n = rec.read(s, lus, BLOC - lus)
                    if (n <= 0) break
                    lus += n
                }
                if (lus <= 0) { if (actif) Thread.sleep(20); continue }
                val pcm = ByteArray(lus * 2)
                for (i in 0 until lus) {
                    val v = s[i].toInt()
                    pcm[2 * i] = (v and 0xFF).toByte()
                    pcm[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
                }
                surBloc(pcm, niveau(pcm))
            }
        }
        return true
    }

    private fun demarrerFichier(f: File): Boolean {
        actif = true
        fil = thread(name = "reunion-fichier-test") {
            BufferedInputStream(FileInputStream(f)).use { entree ->
                val debut = System.nanoTime()
                var n = 0L
                while (actif) {
                    val pcm = ByteArray(BLOC * 2)
                    var lus = 0
                    while (lus < pcm.size) {
                        val k = entree.read(pcm, lus, pcm.size - lus)
                        if (k <= 0) break
                        lus += k
                    }
                    // Au rythme réel : un bloc toutes les 160 ms, comme le micro.
                    val attente = debut + (n + 1) * BLOC * 1_000_000_000L / RATE - System.nanoTime()
                    if (attente > 0) Thread.sleep(attente / 1_000_000, (attente % 1_000_000).toInt())
                    n++
                    if (actif) surBloc(pcm, niveau(pcm))
                }
            }
        }
        return true
    }

    private fun niveau(pcm: ByteArray): Float {
        val lus = pcm.size / 2
        if (lus == 0) return 0f
        var somme = 0.0
        for (i in 0 until lus) {
            val v = (pcm[2 * i].toInt() and 0xFF) or (pcm[2 * i + 1].toInt() shl 8)
            val f = v / 32768.0
            somme += f * f
        }
        val rms = sqrt(somme / lus)
        return sqrt(min(1.0, rms / NIVEAU_PLEIN)).toFloat()
    }

    fun arreter() {
        actif = false
        fil?.join(1000)
        fil = null
        record?.let {
            try { it.stop() } catch (_: Throwable) {}
            it.release()
        }
        record = null
    }

    companion object {
        const val RATE = 16_000
        /** 160 ms, comme le clavier et la page Labs. */
        const val BLOC = 2_560
        /** Même échelle que le micro du clavier. */
        const val NIVEAU_PLEIN = 0.18
    }
}

/**
 * L'enregistrement brut d'une réunion → les fichiers envoyés à LuxASR.
 *
 * En AAC (m4a) à 32 kbit/s : une heure de réunion pèse 14 Mo au lieu de
 * 115 Mo en WAV, ce qui compte sur un réseau mobile. Au-delà de 90 minutes,
 * l'enregistrement est coupé en parties, chacune un travail LuxASR à part :
 * c'est la durée que la page Labs s'impose déjà par fichier. Si l'encodeur du
 * téléphone fait défaut, on retombe sur du WAV, en parties de 30 minutes.
 */
object Encodeur {
    private const val PARTIE_AAC_S = 90 * 60
    private const val PARTIE_WAV_S = 30 * 60
    private const val DEBIT = 32_000
    private const val OCTETS_PAR_S = Capture.RATE * 2

    fun parties(r: Reunion, progres: (Double) -> Unit): List<Reunion.Partie> {
        val pcm = r.fichierPcm
        return try {
            decouper(pcm, PARTIE_AAC_S) { k, debut, octets ->
                val f = r.fichier("partie-$k.m4a")
                encoderAac(pcm, debut, octets, f) { progres((debut + it) / pcm.length().toDouble()) }
                Reunion.Partie(f.name, "audio/mp4", debut / OCTETS_PAR_S.toDouble())
            }
        } catch (e: Exception) {
            r.dossier.listFiles()?.filter { it.name.endsWith(".m4a") }?.forEach { it.delete() }
            decouper(pcm, PARTIE_WAV_S) { k, debut, octets ->
                val f = r.fichier("partie-$k.wav")
                ecrireWav(pcm, debut, octets, f)
                progres((debut + octets) / pcm.length().toDouble())
                Reunion.Partie(f.name, "audio/wav", debut / OCTETS_PAR_S.toDouble())
            }
        }
    }

    private fun <T> decouper(pcm: File, partieS: Int, faire: (k: Int, debut: Long, octets: Long) -> T): List<T> {
        // Un nombre impair d'octets : l'application a été tuée en pleine écriture.
        val total = pcm.length() and 1L.inv()
        val taille = partieS.toLong() * OCTETS_PAR_S
        val res = mutableListOf<T>()
        var debut = 0L
        var k = 1
        while (debut < total) {
            val octets = min(taille, total - debut)
            // Une queue de moins de 20 s rejoint la partie précédente plutôt que de faire un travail à elle.
            val reste = total - debut - octets
            val n = if (reste in 1 until 20L * OCTETS_PAR_S) octets + reste else octets
            res += faire(k++, debut, n)
            debut += n
        }
        return res
    }

    private fun ouvrir(pcm: File, debut: Long): InputStream =
        BufferedInputStream(FileInputStream(pcm).apply { channel.position(debut) }, 64 * 1024)

    private fun encoderAac(pcm: File, debut: Long, octets: Long, sortie: File, progres: (Long) -> Unit) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, Capture.RATE, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, DEBIT)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val muxer = MediaMuxer(sortie.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var piste = -1
        var demarre = false
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            ouvrir(pcm, debut).use { entree ->
                val info = MediaCodec.BufferInfo()
                val tampon = ByteArray(8 * 1024)
                var lus = 0L
                var finEntree = false
                while (true) {
                    if (!finEntree) {
                        val i = codec.dequeueInputBuffer(10_000)
                        if (i >= 0) {
                            val buf = codec.getInputBuffer(i)!!
                            buf.clear()
                            val voulu = min(min(buf.capacity().toLong(), tampon.size.toLong()), octets - lus).toInt()
                            val n = if (voulu > 0) entree.read(tampon, 0, voulu) else -1
                            val pts = lus / 2 * 1_000_000L / Capture.RATE
                            if (n <= 0) {
                                codec.queueInputBuffer(i, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                finEntree = true
                            } else {
                                buf.put(tampon, 0, n)
                                codec.queueInputBuffer(i, 0, n, pts, 0)
                                lus += n
                                progres(lus)
                            }
                        }
                    }
                    val o = codec.dequeueOutputBuffer(info, 10_000)
                    if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        piste = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        demarre = true
                    } else if (o >= 0) {
                        val ob = codec.getOutputBuffer(o)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && demarre) {
                            ob.position(info.offset)
                            ob.limit(info.offset + info.size)
                            muxer.writeSampleData(piste, ob, info)
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            if (!demarre) throw IllegalStateException("l'encodeur AAC n'a rien produit")
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            try { if (demarre) muxer.stop() } catch (_: Exception) {}
            muxer.release()
        }
    }

    private fun ecrireWav(pcm: File, debut: Long, octets: Long, sortie: File) {
        FileOutputStream(sortie).use { out ->
            out.write(enteteWav(octets))
            ouvrir(pcm, debut).use { entree ->
                val tampon = ByteArray(64 * 1024)
                var reste = octets
                while (reste > 0) {
                    val n = entree.read(tampon, 0, min(tampon.size.toLong(), reste).toInt())
                    if (n <= 0) break
                    out.write(tampon, 0, n)
                    reste -= n
                }
            }
        }
    }

    fun enteteWav(octets: Long): ByteArray {
        val b = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt((36 + octets).toInt()).put("WAVEfmt ".toByteArray())
        b.putInt(16).putShort(1).putShort(1).putInt(Capture.RATE).putInt(OCTETS_PAR_S).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(octets.toInt())
        return b.array()
    }

    /** Durée d'un fichier PCM brut, en secondes. */
    fun duree(pcm: File): Double = pcm.length() / OCTETS_PAR_S.toDouble()

    /** Pour les tests : ajoute à [pcm] des octets bruts. */
    internal fun ajouter(pcm: File, octets: ByteArray) {
        RandomAccessFile(pcm, "rw").use { it.seek(it.length()); it.write(octets) }
    }
}
