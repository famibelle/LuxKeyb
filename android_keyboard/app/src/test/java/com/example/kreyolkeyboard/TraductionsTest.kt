package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.carnet.UniteRythme
import com.example.kreyolkeyboard.carnet.Widderhuelen
import com.example.kreyolkeyboard.carnet.arrondiRythme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Les cinq langues de l'interface : anglais (`values/`, la langue par défaut),
 * français, allemand, portugais et luxembourgeois.
 *
 * Une traduction qui manque ne casse rien de visible : Android retombe sur
 * l'anglais, et un francophone lit soudain une ligne en anglais au milieu de
 * son écran. Une variable perdue (`%1$s`) est pire, elle fait planter
 * `getString` au moment d'afficher. Ce test refuse les deux, sur les fichiers
 * livrés.
 */
class TraductionsTest {

    private val res = File("src/main/res")
    private val langues = listOf("fr", "de", "pt", "lb")

    /** Clé → contenu ; pluriels et listes aplatis en `cle[quantite]`, `cle[rang]`. */
    private fun lire(dossier: String): Map<String, String> {
        val fichier = File(res, "$dossier/strings.xml")
        assertTrue("$fichier manque", fichier.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(fichier)
        val sortie = LinkedHashMap<String, String>()
        val racine = doc.documentElement.childNodes
        for (i in 0 until racine.length) {
            val e = racine.item(i) as? Element ?: continue
            if (e.getAttribute("translatable") == "false") continue
            val nom = e.getAttribute("name")
            when (e.tagName) {
                "string" -> sortie[nom] = e.textContent
                "plurals", "string-array" -> {
                    val items = e.getElementsByTagName("item")
                    for (j in 0 until items.length) {
                        val item = items.item(j) as Element
                        val q = item.getAttribute("quantity").ifEmpty { j.toString() }
                        sortie["$nom[$q]"] = item.textContent
                    }
                }
            }
        }
        return sortie
    }

    private fun variables(texte: String): Set<String> =
        Regex("%(\\d+\\$)?[sd]").findAll(texte).map { it.value }.toSet()

    @Test
    fun chaqueLangueTraduitToutEtRienDePlus() {
        val reference = lire("values").keys
        for (langue in langues) {
            val cles = lire("values-$langue").keys
            assertEquals("values-$langue : clés manquantes", emptySet<String>(), reference - cles)
            assertEquals("values-$langue : clés en trop", emptySet<String>(), cles - reference)
        }
    }

    @Test
    fun lesVariablesSontLesMemesDansToutesLesLangues() {
        val reference = lire("values")
        for (langue in langues) {
            for ((cle, texte) in lire("values-$langue")) {
                val attendu = variables(reference.getValue(cle))
                // « EXEMPLE » au singulier n'affiche pas son nombre : c'est permis.
                if (cle.endsWith("[one]") && variables(texte).isEmpty()) continue
                assertEquals("values-$langue/$cle", attendu, variables(texte))
            }
        }
    }

    /**
     * La légende gravée sous un casier de la boîte tient dans une fente
     * étroite : sept signes au plus, pour les intervalles livrés.
     */
    @Test
    fun lesLegendesDesCasiersTiennentSousLaFente() {
        for (dossier in listOf("values") + langues.map { "values-$it" }) {
            val textes = lire(dossier)
            for (jours in Widderhuelen.INTERVALLES) {
                val (n, unite) = arrondiRythme(jours)
                val cle = when (unite) {
                    UniteRythme.JOUR -> "rythme_jours_court"
                    UniteRythme.SEMAINE -> "rythme_semaines_court"
                    UniteRythme.MOIS -> "rythme_mois_court"
                }
                val modele = textes["$cle[${if (n == 1) "one" else "other"}]"]
                    ?: textes.getValue("$cle[other]")
                val legende = modele.replace("%d", n.toString())
                assertTrue("$dossier : « $legende » dépasse sept signes", legende.length <= 7)
            }
        }
    }
}
