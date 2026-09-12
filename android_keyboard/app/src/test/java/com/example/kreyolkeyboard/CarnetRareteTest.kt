package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.carnet.JeuCarte
import com.example.kreyolkeyboard.carnet.Rarete
import com.example.kreyolkeyboard.zuelen.ZuelenSpeller
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La rareté des cartes du carnet.
 *
 * Elle est lue sur le **rang de fréquence** d'une forme dans
 * `luxemburgish_dict.json`, qui est trié par fréquence décroissante : le rang
 * est donc gratuit et vérifiable, là où des « points de vie » inventés
 * apprendraient au joueur quelque chose de faux sur sa langue.
 *
 * Les seuils ont été **mesurés sur le vivier réel** avant d'être écrits. Ce
 * test rejoue la mesure sur les actifs livrés : une régénération du
 * dictionnaire ou des grilles qui déplacerait la courbe rendrait toutes les
 * cartes communes, ou toutes très rares, sans que rien d'autre ne le signale.
 */
class CarnetRareteTest {

    private fun rangs(): Map<String, Int> {
        val fichier = File("src/main/assets/luxemburgish_dict.json")
        assertTrue("luxemburgish_dict.json manquant", fichier.exists())
        val tableau = JSONArray(fichier.readText())
        val rangs = HashMap<String, Int>(tableau.length())
        for (i in 0 until tableau.length()) {
            rangs[tableau.getJSONArray(i).getString(0)] = i
        }
        return rangs
    }

    private fun formesDe(actif: String): Set<String> {
        val fichier = File("src/main/assets/$actif")
        assertTrue("$actif manquant", fichier.exists())
        val grilles = JSONObject(fichier.readText()).getJSONArray("grilles")
        val formes = HashSet<String>()
        for (i in 0 until grilles.length()) {
            val mots = grilles.getJSONObject(i).getJSONArray("mots")
            for (j in 0 until mots.length()) formes.add(mots.getJSONObject(j).getString("f"))
        }
        return formes
    }

    @Test
    fun `les seuils rangent dans l'ordre attendu`() {
        assertEquals(Rarete.COMMUN, Rarete.pourRang(0))
        assertEquals(Rarete.COMMUN, Rarete.pourRang(Rarete.SEUIL_COMMUN - 1))
        assertEquals(Rarete.PEU_COMMUN, Rarete.pourRang(Rarete.SEUIL_COMMUN))
        assertEquals(Rarete.PEU_COMMUN, Rarete.pourRang(Rarete.SEUIL_PEU_COMMUN - 1))
        assertEquals(Rarete.RARE, Rarete.pourRang(Rarete.SEUIL_PEU_COMMUN))
        assertEquals(Rarete.RARE, Rarete.pourRang(Rarete.SEUIL_RARE - 1))
        assertEquals(Rarete.TRES_RARE, Rarete.pourRang(Rarete.SEUIL_RARE))
    }

    /**
     * Un mot que le dictionnaire de fréquences ne connaît pas est le plus rare
     * de tous, et non le plus commun : c'est la lecture juste, et le repli sûr
     * si un jour un jeu alimentait le carnet depuis le vivier du LOD, dont les
     * formes n'ont pas de fréquence de corpus.
     */
    @Test
    fun `un rang inconnu est traite comme le plus rare`() {
        assertEquals(Rarete.TRES_RARE, Rarete.pourRang(null))
    }

    /**
     * La courbe doit rester celle d'un jeu de cartes : le commun domine, la
     * dernière catégorie se mérite. Mesuré au moment de l'écriture sur les
     * 1 963 formes de Wuertplaz : 37 / 34 / 20 / 8 %.
     */
    @Test
    fun `la courbe de rarete de Wuertplaz reste jouable`() {
        val rangs = rangs()
        val formes = formesDe("luxemburgish_chassecroise.json")
        assertTrue("vivier Wuertplaz trop maigre: ${formes.size}", formes.size >= 1000)

        val parts = formes.groupingBy { Rarete.pourRang(rangs[it]) }.eachCount()
        fun pourcent(r: Rarete) = (parts[r] ?: 0) * 100 / formes.size

        assertTrue(
            "le commun doit dominer sans tout prendre (${pourcent(Rarete.COMMUN)} %)",
            pourcent(Rarete.COMMUN) in 20..55
        )
        assertTrue(
            "le très rare doit rester une trouvaille (${pourcent(Rarete.TRES_RARE)} %)",
            pourcent(Rarete.TRES_RARE) in 2..20
        )
        Rarete.values().forEach {
            assertTrue(
                "aucun palier ne doit être vide : ${it.libelle}",
                (parts[it] ?: 0) > 0
            )
        }
    }

    /**
     * Les mêmes seuils appliqués à Kräizwuert, qui pourra alimenter le carnet
     * plus tard : ses grilles faciles plafonnent volontairement dans les mots
     * les plus fréquents, donc son commun est plus lourd — mais les quatre
     * paliers doivent tout de même exister.
     */
    @Test
    fun `les memes seuils tiennent pour le vivier de Kraizwuert`() {
        val rangs = rangs()
        val formes = formesDe("luxemburgish_crossword.json")
        val parts = formes.groupingBy { Rarete.pourRang(rangs[it]) }.eachCount()
        Rarete.values().forEach {
            assertTrue(
                "aucun palier ne doit être vide : ${it.libelle}",
                (parts[it] ?: 0) > 0
            )
        }
        assertTrue(
            "le commun ne doit pas tout absorber",
            (parts[Rarete.COMMUN] ?: 0) * 100 / formes.size <= 75
        )
    }

    /**
     * Le piège que `TranslationDictionary.fiche` doit éviter, documenté sur
     * les données réelles.
     *
     * Une carte affiche **la forme rencontrée**, elle doit donc afficher la
     * glose de cette forme-là. Prendre celle du représentant de sa famille
     * présentait le substantif « Notze » (utilité) sous la glose du verbe
     * « notzen » (profiter de) — le même piège que « rout », rouge et non
     * « se reposer ». Ce test vérifie que la table livrée contient bien des
     * formes dans ce cas : si elle cessait d'en contenir, la règle deviendrait
     * gratuite, et quelqu'un pourrait la retirer sans rien casser en apparence.
     */
    @Test
    fun `des formes ont leur propre glose distincte de leur famille`() {
        val traductions = File("src/main/assets/luxemburgish_translations.json")
        val familles = File("src/main/assets/luxemburgish_familles.json")
        assertTrue("actifs de traduction manquants", traductions.exists() && familles.exists())

        val gloses = JSONObject(traductions.readText()).getJSONObject("translations")
        val tableFamilles = JSONObject(familles.readText()).getJSONObject("familles")

        var divergentes = 0
        val cles = tableFamilles.keys()
        while (cles.hasNext()) {
            val representant = cles.next()
            val gloseRepresentant = gloses.optString(representant, "")
            if (gloseRepresentant.isEmpty()) continue
            for (forme in tableFamilles.getString(representant).split(" ")) {
                if (forme.isEmpty()) continue
                val propre = gloses.optString(forme, "")
                if (propre.isNotEmpty() && propre != gloseRepresentant) divergentes++
            }
        }

        assertTrue(
            "aucune forme ne diverge de son représentant : la règle de " +
                "priorité de glose serait sans objet ($divergentes)",
            divergentes >= 1000
        )
    }

    /**
     * La lecture de la rareté d'un numéral, mesurée sur l'actif livré.
     *
     * Ce test justifie l'existence de [Rarete.pourNombre] : si un jour le
     * corpus contenait les composés, la lecture par rang redeviendrait la
     * bonne, et cette règle-ci n'aurait plus lieu d'être. Tant que la mesure
     * tient, elle l'a.
     */
    @Test
    fun `le corpus ne contient presque aucun numeral compose`() {
        val rangs = rangs()
        val absents = (0..ZuelenSpeller.MAXIMUM).count {
            rangs[ZuelenSpeller.enLettres(it)] == null
        }
        assertTrue(
            "le corpus connaîtrait maintenant les numéraux ($absents absents " +
                "sur 101) : la lecture par rang redeviendrait la bonne",
            absents >= 70
        )
    }

    /**
     * Les quatre paliers d'un numéral suivent ce que son orthographe demande :
     * forme isolée, dizaine ronde, composé à liaison « an », composé où la
     * règle d'Eifel fait tomber le n.
     */
    @Test
    fun `la rarete d'un numeral suit son orthographe`() {
        assertEquals(Rarete.COMMUN, Rarete.pourNombre(8))
        assertEquals(Rarete.COMMUN, Rarete.pourNombre(19))
        assertEquals(Rarete.COMMUN, Rarete.pourNombre(ZuelenSpeller.MAXIMUM))
        assertEquals(Rarete.PEU_COMMUN, Rarete.pourNombre(20))
        assertEquals(Rarete.PEU_COMMUN, Rarete.pourNombre(90))
        // 21 = een + an + zwanzeg : le n de liaison se maintient.
        assertEquals(Rarete.RARE, Rarete.pourNombre(21))
        assertEquals(Rarete.RARE, Rarete.pourNombre(99))
        // 56 = sechs + a + fofzeg : la règle d'Eifel fait tomber le n.
        assertEquals(Rarete.TRES_RARE, Rarete.pourNombre(56))
        assertEquals(Rarete.TRES_RARE, Rarete.pourNombre(42))
        // Hors bornes, le repli commun à tout le carnet.
        assertEquals(Rarete.TRES_RARE, Rarete.pourNombre(-1))
        assertEquals(Rarete.TRES_RARE, Rarete.pourNombre(1000))
    }

    /**
     * La courbe des numéraux doit rester jouable elle aussi : Zuelwuert tire
     * ses produits dans les tables de 2 à 10, et aucun palier ne doit y être
     * vide ni tout absorber.
     */
    @Test
    fun `la courbe de rarete de Zuelwuert reste jouable`() {
        val produits = (2..10).flatMap { a -> (2..10).map { b -> a * b } }
        val parts = produits.groupingBy { Rarete.pourNombre(it) }.eachCount()
        Rarete.values().forEach {
            assertTrue(
                "aucun palier ne doit être vide : ${it.libelle}",
                (parts[it] ?: 0) > 0
            )
        }
        Rarete.values().forEach {
            assertTrue(
                "aucun palier ne doit tout absorber : ${it.libelle}",
                (parts[it] ?: 0) * 100 / produits.size <= 50
            )
        }
    }

    /**
     * Les identifiants des jeux sont **la clé de stockage** d'une carte : les
     * renommer relirait les carnets déjà collectés comme des cartes sans
     * provenance. Ce test les fige, et vérifie qu'aucun doublon n'existe.
     */
    @Test
    fun `les identifiants de jeu sont uniques et stables`() {
        val ids = JeuCarte.values().map { it.id }
        assertEquals(7, ids.size)
        assertEquals(7, ids.toSet().size)
        assertEquals(
            listOf("ws", "wm", "wr", "wl", "zw", "kw", "wp"),
            ids
        )
        JeuCarte.values().forEach {
            assertEquals("l'identifiant reste court", 2, it.id.length)
            assertEquals(it, JeuCarte.parId(it.id))
        }
        assertTrue("un identifiant inconnu ne doit rien résoudre", JeuCarte.parId("xx") == null)
    }

    /**
     * Un jeu doit se reconnaître à son emoji et à sa couleur : ce sont les
     * deux seules choses qui disent d'où vient une carte, sur la vignette du
     * carnet comme au dos de la pochette.
     */
    @Test
    fun `chaque jeu se distingue a l'oeil`() {
        assertEquals(7, JeuCarte.values().map { it.emoji }.toSet().size)
        assertEquals(7, JeuCarte.values().map { it.couleur }.toSet().size)
        assertEquals(7, JeuCarte.values().map { it.nom }.toSet().size)
    }

    /**
     * Le symbole et la couleur doivent différer d'un palier à l'autre : c'est
     * tout ce qui distingue les cartes une fois posées côte à côte dans la
     * grille du carnet.
     */
    @Test
    fun `chaque palier se distingue a l'oeil`() {
        assertEquals(4, Rarete.values().size)
        assertEquals(4, Rarete.values().map { it.symbole }.toSet().size)
        assertEquals(4, Rarete.values().map { it.couleur }.toSet().size)
        assertEquals(4, Rarete.values().map { it.libelle }.toSet().size)
    }

    /**
     * L'insigne se lit **sans couleur**.
     *
     * C'est la seule marque de rareté qui survive à une vision déficiente : le
     * vert de *Peu commun* et le bleu-gris de *Commun* se confondent en
     * deutéranopie, et la vignette n'a pas la place d'un libellé. Compter des
     * symboles marche pour tout le monde — encore faut-il que le compte soit
     * différent d'un palier à l'autre, ce que ce test fige.
     */
    @Test
    fun `l'insigne compte les paliers sans recourir a la couleur`() {
        assertEquals(4, Rarete.values().map { it.insigne }.toSet().size)
        Rarete.values().forEachIndexed { rang, palier ->
            assertEquals(
                "le nombre de symboles doit suivre le palier",
                rang + 1,
                palier.insigne.length / palier.symbole.length
            )
            assertTrue(
                "l'insigne ne répète que le symbole du palier",
                palier.insigne.startsWith(palier.symbole)
            )
        }
        assertEquals("●", Rarete.COMMUN.insigne)
        assertEquals("✦✦✦✦", Rarete.TRES_RARE.insigne)
    }

    /**
     * Un seul palier de bascule pour toutes les marques de rareté.
     *
     * Le coin coupé, le double filet du cadre, l'ombre portée, le halo de la
     * pochette et l'éclat d'arrivée se déclenchent tous sur `distinguee`. Le
     * jour où l'un d'eux se met à tester `== TRES_RARE` de son côté, une carte
     * gagnera le coin sans le halo, et cela se lira comme un bug plutôt que
     * comme une distinction. Ce test fige les deux paliers concernés.
     */
    @Test
    fun `les marques de rarete s'allument au meme palier`() {
        assertEquals(
            listOf(Rarete.RARE, Rarete.TRES_RARE),
            Rarete.values().filter { it.distinguee }
        )
    }
}
