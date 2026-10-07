package com.example.kreyolkeyboard.actualites

import android.content.Context
import android.util.Log
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Une publication de l'INLL, telle que son flux RSS la donne. */
data class Actualite(
    val titre: String,
    val lien: String,
    /** Date de mise en ligne, en millisecondes ; `null` si le flux ne la donne pas. */
    val date: Long?,
    val resume: String
)

/**
 * Les actualités de l'Institut national des langues Luxembourg (INLL), lues
 * dans le flux RSS de son site (`https://www.inll.lu/<langue>/feed/`).
 *
 * Le site est un WordPress en quatre langues : `fr`, `lu`, `de`, `en`. La
 * langue du flux suit celle de l'application par la ressource
 * `langue_flux_inll` ; le portugais, que l'INLL ne publie pas, prend le
 * français, langue administrative que les lusophones du pays lisent plus
 * souvent que l'anglais.
 *
 * Le téléphone ne contacte l'INLL que quand l'utilisateur ouvre la page des
 * actualités : jamais en arrière-plan, et rien n'est envoyé hors de la requête
 * elle-même. Le dernier flux reçu est gardé dans le cache de l'application,
 * pour que la page s'affiche aussitôt et reste lisible sans réseau.
 */
object FluxInll {

    private const val TAG = "FluxInll"
    private const val DELAI_MS = 10_000

    fun adresse(langue: String) = "https://www.inll.lu/$langue/feed/"

    /** La page d'accueil du site, dans la même langue que le flux. */
    fun site(langue: String) = "https://www.inll.lu/$langue/"

    private fun fichierCache(context: Context, langue: String) =
        File(context.cacheDir, "actualites_inll_$langue.xml")

    /**
     * Efface les flux gardés en cache, toutes langues : ce que les 33.2.x ont
     * téléchargé ne reste pas sur le téléphone quand la page est désactivée.
     */
    fun oublier(context: Context) {
        context.cacheDir.listFiles { f -> f.name.startsWith("actualites_inll_") }
            ?.forEach { it.delete() }
    }

    /** Le dernier flux reçu, ou une liste vide si rien n'a encore été reçu. */
    fun enCache(context: Context, langue: String): List<Actualite> {
        val f = fichierCache(context, langue)
        if (!f.exists()) return emptyList()
        return try {
            f.inputStream().use { lire(it) }
        } catch (e: Exception) {
            Log.w(TAG, "cache illisible", e)
            emptyList()
        }
    }

    /**
     * Télécharge le flux, le garde en cache et renvoie ses publications.
     * À appeler hors du fil principal. `null` en cas d'échec (pas de réseau,
     * site injoignable, flux illisible) : l'appelant garde alors le cache.
     */
    fun telecharger(context: Context, langue: String): List<Actualite>? {
        val connexion = (URL(adresse(langue)).openConnection() as HttpURLConnection).apply {
            connectTimeout = DELAI_MS
            readTimeout = DELAI_MS
            instanceFollowRedirects = true
        }
        return try {
            if (connexion.responseCode != HttpURLConnection.HTTP_OK) return null
            val octets = connexion.inputStream.use { it.readBytes() }
            val actualites = lire(octets.inputStream())
            // On ne remplace le cache que par un flux qui a pu être lu : un
            // portail captif d'hôtel répond 200 avec une page HTML.
            if (actualites.isNotEmpty()) fichierCache(context, langue).writeBytes(octets)
            actualites
        } catch (e: Exception) {
            Log.w(TAG, "flux injoignable", e)
            null
        } finally {
            connexion.disconnect()
        }
    }

    /**
     * Lit un flux RSS 2.0. Une entrée sans titre ou sans lien est ignorée : on
     * ne saurait ni l'afficher ni l'ouvrir.
     */
    fun lire(flux: InputStream): List<Actualite> {
        val usine = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
        }
        val doc = usine.newDocumentBuilder().parse(flux)
        val items = doc.getElementsByTagName("item")
        return (0 until items.length).mapNotNull { i ->
            val item = items.item(i) as Element
            val titre = texte(item, "title")
            val lien = texte(item, "link")
            if (titre.isEmpty() || lien.isEmpty() || estOffreEmploi(titre)) return@mapNotNull null
            Actualite(
                titre = titre,
                lien = lien,
                date = date(texte(item, "pubDate")),
                resume = resume(texte(item, "description"))
            )
        }
    }

    /**
     * Les offres d'emploi de l'INLL passent dans le même flux que ses cours et
     * ses événements, mais elles ne parlent pas de la langue à qui l'apprend.
     * L'INLL les titre en français dans toutes ses langues (« Recrutement :
     * Chef(fe) de service adjoint… » jusque dans le flux anglais) ; les autres
     * mots sont là pour le jour où il les traduirait.
     */
    internal fun estOffreEmploi(titre: String): Boolean {
        val debut = titre.trim().lowercase(Locale.ROOT)
        return MOTS_OFFRE_EMPLOI.any { debut.startsWith(it) }
    }

    private val MOTS_OFFRE_EMPLOI = listOf(
        "recrutement", "recruitment", "stellenangebot", "stellenausschreibung", "rekrutéierung"
    )

    private fun texte(item: Element, balise: String): String {
        val noeuds = item.getElementsByTagName(balise)
        return if (noeuds.length == 0) "" else noeuds.item(0).textContent.trim()
    }

    private fun date(rfc822: String): Long? = try {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH).parse(rfc822)?.time
    } catch (e: Exception) {
        null
    }

    /**
     * Le résumé en texte brut. WordPress y met parfois du HTML, une ellipse
     * « [&#8230;] » quand il coupe, et une phrase de signature (« L'article …
     * est apparu en premier sur … ») qui ne dit rien au lecteur. Les entités
     * restent telles quelles : l'écran les décode avec le reste du HTML.
     */
    internal fun resume(description: String): String =
        description
            .replace(Regex("(?s)<p>\\s*L.article <a .*$"), "")
            .replace(Regex("<[^>]+>"), " ")
            .replace("[&#8230;]", "…")
            .replace("[…]", "…")
            .replace(Regex("\\s+"), " ")
            .trim()
}
