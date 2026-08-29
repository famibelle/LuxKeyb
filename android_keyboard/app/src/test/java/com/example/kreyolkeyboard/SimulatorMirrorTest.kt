package com.example.kreyolkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Vérifie que le simulateur du site rejoue bien le moteur de l'application.
 *
 * `docs/assets/simulateur-engine.js` est une réécriture en JavaScript de
 * [SuggestionEngine], servie publiquement pour essayer le clavier sans
 * l'installer. Rien n'empêche les deux de diverger, et ils l'ont fait :
 * `EDIT_DISTANCE_WEIGHT` est passé de 100 000 à 1 000 000 côté Android le
 * 27 août 2026, et le simulateur est resté à 100 000 pendant deux jours — le
 * site rejouait donc, à la vue de tous, un défaut corrigé dans l'application.
 *
 * L'enjeu dépasse la vitrine : `docs/scripts/bench_frappes.js`, le banc qui
 * garde le classement des suggestions en CI, mesure le miroir et non le moteur
 * Kotlin. Sans ce test, le banc pourrait rester vert sur une application
 * cassée — précisément le genre de mesure rassurante qui ne mesure rien.
 *
 * Ce test ne compare pas les deux implémentations ligne à ligne : il verrouille
 * les constantes dont l'échelle dépend du corpus, celles qui ont déjà lâché
 * deux fois en silence.
 */
class SimulatorMirrorTest {

    private fun sourceDuSimulateur(): String {
        // Les tests s'exécutent depuis android_keyboard/app.
        val fichier = File("../../docs/assets/simulateur-engine.js")
        assertTrue(
            "simulateur-engine.js introuvable (${fichier.absolutePath}) : le " +
                "miroir du moteur ne peut pas être vérifié",
            fichier.exists()
        )
        return fichier.readText()
    }

    /** Extrait l'unique nombre capturé par [motif], ou échoue en le disant. */
    private fun constante(source: String, motif: Regex, nom: String): Double {
        val trouvees = motif.findAll(source).map { it.groupValues[1] }.toList()
        assertEquals(
            "$nom : attendu exactement une occurrence dans simulateur-engine.js, " +
                "trouvé ${trouvees.size} ($trouvees)",
            1, trouvees.size
        )
        return trouvees.first().toDouble()
    }

    @Test
    fun `le simulateur applique le meme poids de distance d'edition`() {
        val poids = constante(
            sourceDuSimulateur(),
            Regex("""\(3 - distance\)\s*\*\s*(\d+)"""),
            "EDIT_DISTANCE_WEIGHT"
        )
        assertEquals(
            "Le simulateur du site n'applique pas le même poids de distance que " +
                "l'application : une correction à deux éditions vers un mot très " +
                "fréquent y repasserait devant une correction à une édition.",
            SuggestionEngine.EDIT_DISTANCE_WEIGHT, poids, 0.0
        )
    }

    @Test
    fun `le simulateur applique le meme poids de contexte n-gramme`() {
        val poids = constante(
            sourceDuSimulateur(),
            Regex("""scores\.get\(word\)\s*\|\|\s*0\)\s*\+\s*(\d+)\)"""),
            "NGRAM_CONTEXT_WEIGHT"
        )
        assertEquals(
            "Le simulateur du site n'applique pas le même poids de contexte que " +
                "l'application. Il sert aussi de socle au banc de frappes de la CI " +
                "(docs/scripts/bench_frappes.js) : divergent, ce banc mesurerait " +
                "autre chose que ce qui est livré.",
            SuggestionEngine.NGRAM_CONTEXT_WEIGHT, poids, 0.0
        )
    }
}
