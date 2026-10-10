package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import com.example.kreyolkeyboard.R
import com.example.kreyolkeyboard.TranslationDictionary
import com.example.kreyolkeyboard.zuelen.ZuelenSpeller
import org.json.JSONObject

/**
 * Les séries du carnet : des pages d'album **finies**, à emplacements vides.
 *
 * C'est ce qui manquait à la collection : on accumulait sans jamais rien
 * terminer, et une collection sans emplacement vide n'a pas de « encore
 * trois ». Voir `GAMIFICATION-CARNET.md` à la racine du dépôt.
 *
 * Trois séries, toutes connues d'avance et **prouvées complétables** :
 *
 * - [NOMBRES] : les cent un nombres de 0 à 100, ceux que Zuelwuert sait
 *   écrire (de 35.0.0 à 36.x, seulement les dix-huit produits composés des
 *   tables). Les 29 nombres simples ne sortent qu'en Facile. `SeriesTest`
 *   rejoue des manches pour le vérifier ;
 * - [ENLUMINURES] : les dessins dont un mot sort d'un jeu dans les quatre
 *   langues de glose, lus dans `luxemburgish_series.json`
 *   (`Dictionnaires/generate_series.py`, qui fait la preuve) ;
 * - [JEUX] : une carte de chacun des sept jeux.
 *
 * Deux règles, qui valent pour toutes :
 *
 * - **Une série compte des emplacements, pas des cartes.** Le dessin du
 *   téléphone est porté par `Handy`, `Telefon` et `Smartphone` : gagner l'un
 *   remplit l'emplacement, les deux autres n'en ouvrent pas de nouveaux.
 * - **Un emplacement vide est un indice, jamais la réponse** : le nombre en
 *   chiffres, la silhouette du dessin, l'emblème du jeu. Jamais le mot.
 *
 * Le calcul ([avancement]) n'a pas de `Context`, pour être testé sur la JVM
 * comme [Widderhuelen].
 */
enum class Serie(val id: String, @StringRes val titre: Int) {
    /**
     * `nombres_101` et non plus `nombres` : la série a grandi de 18 à 101
     * emplacements après la 36.x, et un joueur qui avait fini les dix-huit porte
     * déjà `nombres` dans ses séries fêtées. Garder l'identifiant lui volerait
     * la fête des cent un.
     */
    NOMBRES("nombres_101", R.string.serie_nombres),
    ENLUMINURES("enluminures", R.string.serie_enluminures),
    JEUX("jeux", R.string.serie_jeux)
}

/**
 * Un emplacement de série. [cle] est la valeur du numéral, le nom du dessin ou
 * l'identifiant du jeu ; [cartes] celles qui le remplissent, dans l'ordre de
 * capture, vide tant qu'il attend.
 */
data class Emplacement<C>(val cle: String, val cartes: List<C>) {
    val rempli: Boolean get() = cartes.isNotEmpty()
}

data class Avancement<C>(val serie: Serie, val emplacements: List<Emplacement<C>>) {
    val remplis: Int get() = emplacements.count { it.rempli }
    val total: Int get() = emplacements.size
    val complete: Boolean get() = total > 0 && remplis == total
}

object Series {

    private const val TAG = "Series"
    private const val ACTIF = "luxemburgish_series.json"

    /**
     * Les cent un nombres que `ZuelenData.newRound` peut tirer, de 0 à 100 :
     * tous en Facile, les composés seulement en Normal et en Difficile.
     * `SeriesTest` vérifie que les manches les tirent tous, et rien d'autre.
     */
    val NOMBRES: List<Int> = (0..ZuelenSpeller.MAXIMUM).toList()

    @Volatile
    private var enluminures: List<String>? = null

    /**
     * Les dessins de la série des enluminures. Un actif illisible donne une
     * série vide, que le carnet n'affiche pas : mieux vaut une série absente
     * qu'une série fausse.
     */
    fun enluminures(context: Context): List<String> {
        enluminures?.let { return it }
        val lus = try {
            val racine = JSONObject(
                context.assets.open(ACTIF).bufferedReader().use { it.readText() }
            )
            val tableau = racine.getJSONArray("enluminures")
            List(tableau.length()) { tableau.getString(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Séries illisibles: ${e.message}", e)
            emptyList()
        }
        enluminures = lus
        return lus
    }

    /**
     * L'avancement d'une série sur une collection.
     *
     * [carte], [meuble] disent comment lire une carte, ce qui garde le calcul
     * indépendant de [ContenuCarte] et de ses actifs : les tests passent des
     * cartes nues.
     */
    fun <C> avancement(
        serie: Serie,
        collection: List<C>,
        carte: (C) -> CarteMot,
        meuble: (C) -> String?,
        enluminures: List<String>
    ): Avancement<C> {
        val ordonnees = collection.sortedBy { carte(it).numero }
        val emplacements = when (serie) {
            Serie.NOMBRES -> NOMBRES.map { n ->
                Emplacement("$n", ordonnees.filter { carte(it).nombre == n })
            }
            Serie.ENLUMINURES -> enluminures.map { m ->
                Emplacement(m, ordonnees.filter { meuble(it) == m })
            }
            Serie.JEUX -> JeuCarte.JEUX.map { jeu ->
                Emplacement(jeu.id, ordonnees.filter { jeu in carte(it).jeux })
            }
        }
        return Avancement(serie, emplacements)
    }

    /**
     * Les séries que les cartes [neuves] viennent de compléter, et qui n'ont
     * encore jamais été fêtées. Les marque comme fêtées.
     *
     * Une série ne se complète qu'en gagnant une carte neuve, puisque le carnet
     * ne fait que grandir : on ne recalcule donc que les séries qu'une neuve
     * peut concerner. La marque garantit le « une seule fois » même si une
     * pochette se rejoue. Elle vit dans un fichier de préférences à part, que
     * les règles de sauvegarde n'incluent pas, et ne contient que des
     * identifiants de séries.
     *
     * Lit la fiche de chaque carte pour les enluminures : **hors du fil
     * principal**, comme l'assemblage de la pochette qui l'appelle.
     */
    fun aFeter(context: Context, neuves: Collection<CarteMot>): List<Serie> {
        if (neuves.isEmpty()) return emptyList()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fetees = prefs.getStringSet(CLE_FETEES, emptySet()).orEmpty()
        val dessins = enluminures(context)
        val meubles = HashMap<String, String?>()
        fun meuble(c: CarteMot): String? = if (c.nombre != null) null else meubles.getOrPut(c.forme) {
            val fiche = TranslationDictionary.fiche(context, c.forme)
            Armorial.pour(context, fiche.mot, c.forme).meuble
        }

        val concernees = Serie.values().filter { s ->
            s.id !in fetees && when (s) {
                Serie.NOMBRES -> neuves.any { it.nombre in NOMBRES }
                Serie.JEUX -> true
                Serie.ENLUMINURES -> dessins.isNotEmpty() && neuves.any { meuble(it) in dessins }
            }
        }
        if (concernees.isEmpty()) return emptyList()

        val collection = Carnet.cartes(context)
        val completes = concernees.filter { s ->
            avancement(s, collection, { it }, { meuble(it) }, dessins).complete
        }
        if (completes.isNotEmpty()) {
            prefs.edit().putStringSet(CLE_FETEES, fetees + completes.map { it.id }).apply()
        }
        return completes
    }

    private const val PREFS = "carnet_series"
    private const val CLE_FETEES = "fetees"

    /** Les trois séries d'une collection de cartes déjà assemblées. */
    fun toutes(context: Context, contenus: List<ContenuCarte>): List<Avancement<ContenuCarte>> {
        val dessins = enluminures(context)
        return Serie.values()
            .filter { it != Serie.ENLUMINURES || dessins.isNotEmpty() }
            .map { avancement(it, contenus, { c -> c.carte }, { c -> c.blason.meuble }, dessins) }
    }
}

/**
 * Les étapes de la collection : la 10ᵉ carte, la 25ᵉ, la 50ᵉ…
 *
 * Une série fête un emplacement rempli ; rien ne fêtait le carnet lui-même, et
 * la centième carte arrivait comme la quatre-vingt-dix-neuvième. Les paliers
 * s'espacent avec la taille : au début une étape vient vite, plus tard elle se
 * mérite.
 *
 * Chaque palier se fête **une fois**, à la pochette qui le franchit. La marque
 * vit à côté des séries fêtées, dans un fichier que la sauvegarde n'emporte
 * pas, et ne contient qu'un nombre. Un carnet qui a déjà dépassé plusieurs
 * paliers à la mise à jour n'est fêté qu'une fois, pour le plus haut.
 */
object Etapes {

    val PALIERS = listOf(10, 25, 50, 100, 250, 500, 1000, 2000)

    private const val PREFS = "carnet_series"
    private const val CLE = "etape"

    /** Le plus haut palier que [taille] atteint au-delà de [deja], ou `null`. */
    fun franchi(taille: Int, deja: Int): Int? = PALIERS.lastOrNull { it in (deja + 1)..taille }

    /** Le palier à fêter pour un carnet de [taille] cartes, marqué comme fêté. */
    fun aFeter(context: Context, taille: Int): Int? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val palier = franchi(taille, prefs.getInt(CLE, 0)) ?: return null
        prefs.edit().putInt(CLE, palier).apply()
        return palier
    }
}
