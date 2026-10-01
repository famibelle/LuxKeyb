package com.example.kreyolkeyboard.stt

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Le réseau trop lent se voit-il vraiment dans la file d'OkHttp ?
 *
 * Ce n'est pas vérifiable sur l'émulateur ni à travers `adb reverse` : la pile
 * réseau de l'un et le tunnel de l'autre accusent réception de tout ce qu'on
 * leur donne, puis brident en aval. Le téléphone n'y voit jamais de lenteur
 * (mesuré le 1er octobre 2026 : près de 2 Mo absorbés en 70 s par le tunnel).
 * Un vrai réseau, lui, ne peut pas tricher ainsi : la connexion est chiffrée de
 * bout en bout, et seul le serveur de l'Université accuse réception.
 *
 * On reproduit donc la seule chose qui compte : un pair qui cesse de lire,
 * comme un lien montant saturé. Avec le tampon d'envoi réduit, l'audio en
 * retard doit apparaître dans `queueSize()` après quelques secondes de parole,
 * et non après des minutes cachées dans le tampon du système. Mesuré sur la
 * JVM le 1er octobre 2026 : alerte après 2,7 s d'audio avec le petit tampon,
 * 58 s avec celui par défaut.
 */
class FileEnvoiReseauTest {

    private val serveur = ServerSocket().apply {
        // Le tampon du pair compte aussi dans ce qui disparaît sans se voir :
        // petit, comme celui d'un lien qui ne suit pas.
        receiveBufferSize = 4096
        bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
    }
    private val client = OkHttpClient.Builder()
        .socketFactory(PetitTamponSocketFactory())
        .build()

    @After
    fun fermer() {
        serveur.close()
        client.dispatcher.executorService.shutdown()
    }

    @Test
    fun unPairQuiNeLitPlusSeVoitEnQuelquesSecondesDAudio() {
        val ws = ouvrir(lit = false)
        val envoye = envoyerJusquALaLenteur(ws, plafond = 20 * RetardEnvoi.OCTETS_PAR_SECONDE)
        assertNotNull("la file n'a jamais grossi : le système absorbe tout le retard", envoye)
        val secondes = envoye!!.toDouble() / RetardEnvoi.OCTETS_PAR_SECONDE
        assertTrue("lenteur vue après %.1f s d'audio, attendu ≤ 4 s".format(secondes), secondes <= 4.0)
        ws.cancel()
    }

    @Test
    fun unPairQuiSuitNeDeclenchePasDAlerte() {
        val ws = ouvrir(lit = true)
        val envoye = envoyerJusquALaLenteur(ws, plafond = 10 * RetardEnvoi.OCTETS_PAR_SECONDE)
        assertTrue("alerte à tort après $envoye octets", envoye == null)
        ws.cancel()
    }

    /**
     * Envoie de l'audio à quatre fois le temps réel, par blocs de 160 ms comme
     * AudioRecorder, et rend le volume envoyé quand la file atteint le seuil
     * d'alerte, ou null si elle ne l'atteint jamais avant [plafond].
     */
    private fun envoyerJusquALaLenteur(ws: WebSocket, plafond: Long): Long? {
        val bloc = ByteArray((RetardEnvoi.OCTETS_PAR_SECONDE * 160 / 1000).toInt())
        var envoye = 0L
        while (envoye < plafond) {
            ws.send(bloc.toByteString())
            envoye += bloc.size
            Thread.sleep(40)
            if (ws.queueSize() >= RetardEnvoi.LENT_OCTETS) return envoye
        }
        return null
    }

    private fun ouvrir(lit: Boolean): WebSocket {
        thread(isDaemon = true) {
            val s = serveur.accept()
            poignee(s)
            if (lit) {
                val tampon = ByteArray(65536)
                while (s.getInputStream().read(tampon) >= 0) Unit
            }
            // Sinon : ne plus jamais lire, et garder la connexion ouverte.
        }
        val ouvert = CountDownLatch(1)
        val ws = client.newWebSocket(
            Request.Builder().url("ws://127.0.0.1:${serveur.localPort}/").build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) = ouvert.countDown()
            })
        assertTrue("connexion WebSocket locale", ouvert.await(5, TimeUnit.SECONDS))
        return ws
    }

    /** Le strict minimum d'une poignée de main WebSocket (RFC 6455). */
    private fun poignee(s: Socket) {
        val entree = s.getInputStream().bufferedReader(Charsets.ISO_8859_1)
        var cle = ""
        while (true) {
            val ligne = entree.readLine() ?: return
            if (ligne.isEmpty()) break
            if (ligne.startsWith("Sec-WebSocket-Key:", ignoreCase = true)) {
                cle = ligne.substringAfter(':').trim()
            }
        }
        val accept = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1")
                .digest((cle + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()))
        s.getOutputStream().write(
            ("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\n" +
             "Connection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n").toByteArray())
        s.getOutputStream().flush()
    }
}
