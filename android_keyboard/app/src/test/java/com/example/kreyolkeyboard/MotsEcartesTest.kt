package com.example.kreyolkeyboard

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La liste de [MotsEcartes] est tenue à la main : elle se relit donc ici, où
 * une erreur se voit.
 *
 * Deux régressions muettes sont possibles et n'échouent nulle part ailleurs :
 * un mot visé qui repasse (l'application se remet à proposer du vocabulaire
 * confessionnel), et un mot courant qui se fait prendre au passage (le jeu
 * perd « bekannt » ou « Här » sans que personne le remarque).
 */
class MotsEcartesTest {

    private val vises = listOf(
        // Religion
        "Ramadan", "Kierch", "Kierchen", "Moschee", "Synagog", "Poopst",
        "Bëschof", "Relioun", "Islam", "Koran", "Bibel", "Gott", "Chrëscht",
        "kathoulesch", "jiddesch", "Judden", "Moslemen", "Gebiet", "bieden",
        "Engel", "Däiwel", "Kommioun", "Klouschter", "Kapell", "Abtei",
        // Partis et étiquettes idéologiques
        "CSV", "LSAP", "ADR", "DP", "Piraten", "Rassismus", "rassistesch",
        "Antisemitismus", "Faschismus", "Extremismus", "Populismus", "Nazien",
        // Questions disputées et registre qui choque
        "Ofdreiwung", "Vergewaltegung", "vergewaltegt", "Prostitutioun",
        "sexuell", "Sex", "Pedophilie", "Kondom", "Drogen", "Kokain", "Heroin",
        "Drogendealer", "Attentat", "Attentäter", "Terrorismus", "terroristesch",
        "Mord", "Selbstmord"
    )

    /**
     * Mots dont le sens religieux existe mais n'est pas le sens courant. Les
     * écarter appauvrirait les jeux sans rien régler — et `bekannt`, `Här` et
     * `Mass` comptent parmi les formes les plus fréquentes du dictionnaire.
     */
    private val gardes = listOf(
        // Sens courant qui n'est pas le sens religieux
        "bekannt", "bekannten", "Här", "Hären", "Mass", "Massen", "Kräiz",
        "Wonner", "Por", "Sënn", "Séil", "Testament", "Seminaire", "Laien",
        "weien", "widmen", "Chrëschtdag", "Ouschteren", "Oktav", "Kierchbierg",
        // Institutions et vie ordinaire : un apprenant en a besoin
        "Regierung", "Minister", "Chamber", "Deputéiert", "Wahlen", "Partei",
        "Oppositioun", "Koalitioun", "Gewerkschaft", "Streik", "Police",
        "Geriicht", "Prisong", "Riichter", "Affekot", "Krich", "Zaldoten",
        "Waff", "Doud", "Spidol", "krank", "Kriibs", "Aids", "Gewalt",
        // Homographes et voisins qu'un filtre par préfixe emporterait
        "Gréng", "gréng", "Geschlecht", "Adress", "Drogerie", "Morgen"
    )

    @Test
    fun `les formes visees sont ecartees`() {
        for (mot in vises) {
            assertTrue("« $mot » devrait être écarté", MotsEcartes.estEcarte(mot))
        }
    }

    @Test
    fun `les mots courants restent proposables`() {
        for (mot in gardes) {
            assertFalse("« $mot » ne devrait pas être écarté", MotsEcartes.estEcarte(mot))
        }
    }

    @Test
    fun `les grossieretes ne sont jamais proposees`() {
        val grossier = listOf(
            "Fotz", "Fotzen", "Houer", "Louder", "Aarschlach", "Aasch",
            "Drecksak", "Schäiss", "Schäissdreck", "Bordell", "Puff",
            "Emmerdeur", "Knaschtsak", "Tëtt", "fuck", "shit", "féckt",
            "veraascht", "schäissegal"
        )
        for (mot in grossier) {
            assertTrue("« $mot » devrait être filtré", MotsEcartes.estGrossier(mot))
            // Le filtre du clavier implique celui des jeux et du mot du jour.
            assertTrue("« $mot » devrait aussi être écarté", MotsEcartes.estEcarte(mot))
        }
    }

    /**
     * Le garde-fou de cette liste-là : la catégorie `FRECHHEET` du LOD marque
     * ces mots comme injures, alors que ce sont des mots parfaitement
     * ordinaires dont l'injure n'est qu'un emploi second. S'y être fié aurait
     * privé le clavier de « Vull », « Sak » et « Kou`.
     */
    @Test
    fun `les mots ordinaires que le LOD marque aussi comme injures restent proposes`() {
        val ordinaires = listOf(
            "Vull", "Vullen", "Sak", "Kou", "Kéi", "Geess", "Iesel", "Ochs",
            "Noss", "Quetsch", "Porrett", "See", "Hex", "Draach", "Idiot",
            "Bock", "Af", "Schwäin", "Gauner", "Trampel",
            // Collisions écartées à la main lors du relevé sur les gloses
            "Sakgaass", "Chili", "Kuss", "Bees", "Picknick", "Schlamassel",
            "gewichst", "Baatsch", "Witz",
            // La ville, qui n'est pas la variante crue « Eesch »
            "Esch"
        )
        for (mot in ordinaires) {
            assertFalse("« $mot » ne doit pas être filtré", MotsEcartes.estGrossier(mot))
        }
    }

    @Test
    fun `la casse et les accents ne comptent pas`() {
        assertTrue(MotsEcartes.estEcarte("RAMADAN"))
        assertTrue(MotsEcartes.estEcarte("kierch"))
        assertTrue(MotsEcartes.estEcarte("BESCHOF"))
    }

    @Test
    fun `une phrase est ecartee sur un seul mot`() {
        assertTrue(MotsEcartes.phraseEcartee("E Samschdeg gouf et am jiddesche Musée eng Attack."))
        assertFalse(MotsEcartes.phraseEcartee("De Kierchbierg ass e Quartier vun der Stad."))
    }

    /**
     * Le découpage se fait sur les non-lettres : `LSAP-Deputéierten` doit être
     * vu comme `LSAP` suivi du reste, sans quoi le nom du parti passerait dès
     * qu'il est composé — et il l'est presque toujours dans les dépêches.
     */
    /**
     * Les mots qui désignent le sujet d'une dépêche écartent la phrase mais
     * restent proposables comme vocabulaire : `Police` et `Accident` sont des
     * mots utiles, c'est le fait divers qui n'a pas sa place dans un jeu.
     */
    @Test
    fun `un fait divers est ecarte sans que ses mots le soient`() {
        assertTrue(MotsEcartes.phraseEcartee("Accident um Freideg zu Mamer mat engem Blesséierten."))
        assertTrue(MotsEcartes.phraseEcartee("D'Täter sinn no der Dot onerkannt gelaf."))
        assertTrue(MotsEcartes.phraseEcartee("Den Auto war e puer Deeg virdrun zu Namur geklaut ginn."))
        assertFalse(MotsEcartes.estEcarte("Police"))
        assertFalse(MotsEcartes.estEcarte("Accident"))
        assertFalse(MotsEcartes.estEcarte("Geriicht"))
        assertFalse(MotsEcartes.estEcarte("Affer"))
    }

    @Test
    fun `un sigle compose est reconnu`() {
        assertTrue(MotsEcartes.phraseEcartee("Sou den LSAP-Deputéierten e Méindeg."))
        assertTrue(MotsEcartes.phraseEcartee("Eng Fuerderung, déi d'ADR ënnerstëtzt."))
        assertFalse(MotsEcartes.phraseEcartee("Seng Adress steet um Formulaire."))
    }

    /**
     * La liste vaut par ce qu'elle retire, mais elle ne doit pas amputer le
     * dictionnaire : ces formes restent tapables, suggérables et cherchables.
     *
     * Elle pèse 0,29 % des occurrences du corpus, dont près de la moitié pour
     * les quatre sigles de partis (`CSV`, `LSAP`, `DP`, `ADR`), omniprésents
     * dans des dépêches politiques. Le garde-fou est à 1 % : au-delà, ce n'est
     * plus un filtre de neutralité, c'est une amputation du vocabulaire.
     */
    @Test
    fun `la part ecartee du dictionnaire reste marginale`() {
        val fichier = File("src/main/assets/luxemburgish_dict.json")
        assertTrue("luxemburgish_dict.json manquant", fichier.exists())
        val tableau = JSONArray(fichier.readText())

        var total = 0L
        var ecarte = 0L
        for (i in 0 until tableau.length()) {
            val entree = tableau.getJSONArray(i)
            val n = entree.getLong(1)
            total += n
            if (MotsEcartes.estEcarte(entree.getString(0))) ecarte += n
        }
        assertTrue("dictionnaire vide", total > 0)
        assertTrue(
            "$ecarte occurrences écartées sur $total : le filtre déborde",
            ecarte * 100 < total
        )
    }

    /**
     * Wuertlück filtre ses phrases sur la même liste, et une phrase de dépêche
     * parle souvent d'autre chose que du mot à trouver — un député y est nommé
     * par son parti. Allonger la liste rétrécit donc la réserve du jeu, et un
     * niveau qui tomberait sous les dix items servirait des manches répétitives
     * sans que rien n'échoue.
     *
     * Au moment de l'écriture : 1 373 phrases conservées sur 1 600, réparties
     * 316 / 583 / 474 sur les trois niveaux — les 156 phrases de faits divers
     * retirées en plus des 71 écartées pour leur vocabulaire.
     */
    @Test
    fun `Wuertluck garde une reserve suffisante a chaque niveau`() {
        val fichier = File("src/main/assets/luxemburgish_cloze.json")
        assertTrue("luxemburgish_cloze.json manquant", fichier.exists())
        val items = JSONObject(fichier.readText()).getJSONArray("items")

        val restants = IntArray(4)
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val phrase = item.getString("s")
            val propositions = mutableListOf(item.getString("a"))
            val leurres = item.getJSONArray("d")
            for (j in 0 until leurres.length()) propositions.add(leurres.getString(j))

            if (MotsEcartes.phraseEcartee(phrase)) continue
            if (propositions.any { MotsEcartes.estEcarte(it) }) continue
            restants[item.optInt("l", 2)]++
        }

        for (niveau in 1..3) {
            assertTrue(
                "niveau $niveau : ${restants[niveau]} phrases après filtrage",
                restants[niveau] >= 100
            )
        }
    }
}
