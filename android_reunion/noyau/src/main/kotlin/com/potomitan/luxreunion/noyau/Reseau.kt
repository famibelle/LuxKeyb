package com.potomitan.luxreunion.noyau

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import java.net.InetAddress
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

/** Les clients HTTP de l'application, réglés comme ceux de la dictée du clavier. */
object Reseau {

    /**
     * Pour le flux du direct. Ping toutes les 20 s, sans quoi les
     * intermédiaires coupent une liaison silencieuse ; petit tampon d'envoi
     * TCP, pour que l'audio en retard reste visible dans la file d'OkHttp
     * (`queueSize`) au lieu de disparaître dans celle du système
     * (LuxAsrSession.kt du clavier).
     */
    val flux: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .socketFactory(PetitTamponSocketFactory())
            .build()
    }

    /** Pour l'API par lots : un envoi de 30 Mo sur un réseau mobile prend son temps. */
    val lots: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.MINUTES)
            .readTimeout(2, TimeUnit.MINUTES)
            .build()
    }
}

/** Les liaisons du direct, sur un vrai WebSocket. Les rappels passent par [planif]. */
class FabriqueOkHttp(
    private val planif: Planificateur,
    private val url: String = DirectLuxAsr.ENDPOINT,
    private val http: OkHttpClient = Reseau.flux
) : FabriqueLiaison {

    override fun ouvrir(ecouteur: EcouteurLiaison): Liaison {
        var signale = false
        fun fermeture() {
            planif.plus(0) {
                if (!signale) { signale = true; ecouteur.surFermeture() }
            }
        }
        val ws = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                planif.plus(0) { ecouteur.surOuverture() }
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                planif.plus(0) { ecouteur.surMessage(text) }
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = fermeture()
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = fermeture()
        })
        return object : Liaison {
            override fun envoyerTexte(texte: String) = ws.send(texte)
            override fun envoyerOctets(octets: ByteArray) = ws.send(octets.toByteString())
            override fun fermer() { ws.close(1000, null) }
            override fun enAttente() = ws.queueSize()
        }
    }
}

internal class PetitTamponSocketFactory(
    private val base: SocketFactory = SocketFactory.getDefault()
) : SocketFactory() {

    private fun regle(s: Socket): Socket = s.apply {
        try { sendBufferSize = 32 * 1024 } catch (_: SocketException) {}
    }

    override fun createSocket(): Socket = regle(base.createSocket())
    override fun createSocket(host: String?, port: Int): Socket =
        regle(base.createSocket(host, port))
    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
        regle(base.createSocket(host, port, localHost, localPort))
    override fun createSocket(host: InetAddress?, port: Int): Socket =
        regle(base.createSocket(host, port))
    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
        regle(base.createSocket(address, port, localAddress, localPort))
}
