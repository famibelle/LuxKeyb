package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.carnet.Rarete
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
}
