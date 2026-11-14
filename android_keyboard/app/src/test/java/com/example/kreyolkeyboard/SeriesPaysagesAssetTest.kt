package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.carnet.Champ
import com.example.kreyolkeyboard.carnet.Lieu
import com.example.kreyolkeyboard.carnet.Rarete
import com.example.kreyolkeyboard.carnet.Series
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La série des paysages est complétable, case par case, dans chaque langue.
 *
 * Une case est un lieu (le champ du mot) à une heure (sa rareté). Une série
 * qu'on ne peut pas finir est pire que pas de série : le joueur la croit à sa
 * portée et s'y use. La preuve passe par Wuertplaz seul, le jeu qui se joue
 * sans connaître la langue, et dont les grilles sont fixes : chaque case doit
 * y être atteinte par au moins [PLANCHER] mots, pour qu'une grille écartée par
 * le filtre de neutralité ne suffise pas à la fermer.
 *
 * Le calcul refait celui de la carte : la forme gagnée remonte à son
 * représentant de famille (`TranslationDictionary.fiche`), qui donne son champ
 * (`Armorial`), et son rang dans le dictionnaire donne sa rareté.
 */
class SeriesPaysagesAssetTest {

    private fun actif(nom: String): JSONObject = JSONObject(File("src/main/assets/$nom").readText())

    private val representant: Map<String, String> by lazy {
        val r = HashMap<String, String>()
        val familles = actif("luxemburgish_familles.json").getJSONObject("familles")
        for (tete in familles.keys()) {
            r[tete] = tete
            for (forme in familles.getString(tete).split(" ")) if (forme.isNotEmpty()) r.putIfAbsent(forme, tete)
        }
        r
    }

    private val champs: Map<String, Champ?> by lazy {
        val b = actif("luxemburgish_blasons.json").getJSONObject("blasons")
        b.keys().asSequence().associateWith { Champ.parId(b.getJSONObject(it).optString("c").ifEmpty { null }) }
    }

    private val rangs: Map<String, Int> by lazy {
        val d = JSONArray(File("src/main/assets/luxemburgish_dict.json").readText())
        HashMap<String, Int>().also { m -> for (i in 0 until d.length()) m.putIfAbsent(d.getJSONArray(i).getString(0), i) }
    }

    private fun cases(asset: String): Map<String, Int> {
        val compte = HashMap<String, Int>()
        val grilles = actif(asset).getJSONArray("grilles")
        val vus = HashSet<String>()
        for (g in 0 until grilles.length()) {
            val mots = grilles.getJSONObject(g).getJSONArray("mots")
            for (i in 0 until mots.length()) {
                val forme = mots.getJSONObject(i).getString("f")
                if (!vus.add(forme)) continue
                val tete = representant[forme] ?: forme
                val champ = champs[tete]
                // Une carte gagnée après la deuxième vague de lieux : son lieu
                // est tiré sur son représentant parmi ceux du champ.
                val rarete = Rarete.pourRang(rangs[forme])
                val cle = Series.cleDePaysage(Lieu.pour(champ, tete, rarete), rarete)
                compte[cle] = (compte[cle] ?: 0) + 1
            }
        }
        return compte
    }

    @Test
    fun lAlbumCompteQuatreVingtSeptCasesDistinctes() {
        // Le premier lieu de chacun des neuf champs (avec la ville des mots
        // sans champ) aux quatre heures, les dix-sept ajoutés sans la nuit.
        assertEquals(87, Series.PAYSAGES.size)
        assertEquals(87, Series.PAYSAGES.toSet().size)
        Series.PAYSAGES.forEach { assertTrue(it, Series.paysageDe(it) != null) }
    }

    @Test
    fun chaqueLieuSaitDeQuelChampIlEstLaFenetre() {
        Lieu.ALBUM.forEach { assertTrue(it.name, it in Lieu.choix(it.champ())) }
        // Le premier lieu de chaque champ reste celui d'avant la deuxième vague.
        (Champ.values().toList() + null).forEach { assertEquals(Lieu.de(it), Lieu.choix(it)[0]) }
    }

    /** Aucun champ ne mène au Breedewee : sa case ne se remplirait jamais. */
    @Test
    fun leBreedeweeEstHorsDeLAlbum() {
        assertTrue(Lieu.BREEDEWEE !in Lieu.ALBUM)
        assertTrue((Champ.values().toList() + null).none { Lieu.BREEDEWEE in Lieu.choix(it) })
        assertTrue(Series.PAYSAGES.none { it.startsWith(Lieu.BREEDEWEE.name) })
    }

    @Test
    fun wuertplazRemplitChaqueCaseDansLesQuatreLangues() {
        val manques = ArrayList<String>()
        for (suffixe in listOf("", "_de", "_en", "_pt")) {
            val asset = "luxemburgish_chassecroise$suffixe.json"
            val compte = cases(asset)
            for (cle in Series.PAYSAGES) {
                val n = compte[cle] ?: 0
                if (n < PLANCHER) manques += "$asset : la case $cle n'est atteinte que par $n mots"
            }
        }
        assertTrue(manques.joinToString("\n"), manques.isEmpty())
    }

    private companion object {
        const val PLANCHER = 3
    }
}
