package com.example.kreyolkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * La sauvegarde Android ne doit emporter que des réglages.
 *
 * La politique de confidentialité publiée promet que la progression, le
 * carnet et tout ce qui vient de la frappe « ne quittent jamais votre
 * appareil ». Jusqu'à la 26.0.2, une règle `include path="."` envoyait tous
 * les fichiers de préférences dans la sauvegarde Google et le transfert
 * d'appareil : carnet, niveau célébré, date du premier mot, emojis récents.
 *
 * Rien de tout ça ne casse quoi que ce soit à l'écran, d'où ce test : il
 * relit les deux fichiers de règles et refuse tout ce qui n'est pas dans la
 * liste blanche. Ajouter un fichier ici, c'est promettre qu'il ne contient
 * qu'un choix de l'utilisateur, jamais une trace de son usage.
 */
class BackupRulesTest {

    private val listeBlanche = setOf(
        "kreyol_clavier_prefs.xml", // vibration, son, thème
        "lux_keyboard_prefs.xml"    // majuscule automatique
    )

    private fun lire(nom: String): Element {
        val fichier = File("src/main/res/xml/$nom")
        assertTrue("$nom introuvable", fichier.exists())
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(fichier).documentElement
    }

    private fun enfants(parent: Element, balise: String): List<Element> {
        val liste = parent.getElementsByTagName(balise)
        return (0 until liste.length).map { liste.item(it) as Element }
    }

    /**
     * Une section sans aucun `include` sauvegarde **tout** : c'est la valeur
     * par défaut d'Android, pas une section vide. Chaque section doit donc en
     * porter au moins un, et chacun doit viser un fichier de la liste.
     */
    private fun verifierSection(section: Element, ou: String) {
        val inclus = enfants(section, "include")
        assertTrue("$ou : aucun include, Android sauvegarderait tout", inclus.isNotEmpty())
        inclus.forEach {
            assertEquals("$ou : domaine inattendu", "sharedpref", it.getAttribute("domain"))
            assertTrue(
                "$ou : ${it.getAttribute("path")} n'est pas un fichier de réglages",
                it.getAttribute("path") in listeBlanche
            )
        }
    }

    @Test
    fun laSauvegardeAvantAndroid12NEmporteQueLesReglages() {
        verifierSection(lire("backup_rules.xml"), "backup_rules.xml")
    }

    @Test
    fun leCloudEtLeTransfertNEmportentQueLesReglages() {
        val racine = lire("data_extraction_rules.xml")
        listOf("cloud-backup", "device-transfer").forEach { balise ->
            val sections = enfants(racine, balise)
            assertEquals("une seule section $balise attendue", 1, sections.size)
            verifierSection(sections.single(), balise)
        }
    }

    @Test
    fun leManifesteUtiliseCesDeuxFichiers() {
        val manifeste = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifeste.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertTrue(manifeste.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
    }
}
