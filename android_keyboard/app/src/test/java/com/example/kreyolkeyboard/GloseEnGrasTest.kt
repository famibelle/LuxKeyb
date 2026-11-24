package com.example.kreyolkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le mot français en gras dans la traduction de la phrase d'exemple, sous la
 * phrase luxembourgeoise où le LOD désigne lui-même son mot.
 *
 * Ici rien ne le désigne : la traduction vient du corpus du ZLS, publiée
 * phrase entière. [TranslationDictionary.placesDeLaGlose] doit donc la
 * retrouver, et surtout **se taire quand elle n'est pas sûre** : une ligne
 * sans gras ne se remarque pas, un gras sur le mauvais mot, si. Ce test fige
 * les trois règles, chacune payée par un cas vu dans les actifs livrés.
 */
class GloseEnGrasTest {

    private fun mis(texte: String, glose: String?): List<String> =
        TranslationDictionary.placesDeLaGlose(texte, glose)
            .map { texte.substring(it.first, it.last + 1) }

    @Test
    fun leMotDeLaGloseEstRetrouveDansLaTraduction() {
        assertEquals(
            listOf("abeille"),
            mis("j'ai été piqué par une abeille", "abeille")
        )
        assertEquals(
            listOf("maison"),
            mis("il reste beaucoup de travaux à faire autour de notre maison", "maison")
        )
    }

    @Test
    fun laCasseEtLesAccentsNeFontPasRater() {
        assertEquals(listOf("Jamaïcain"), mis("mon meilleur ami est Jamaïcain", "jamaïcain"))
        assertEquals(listOf("Lac"), mis("Lac gelé", "lac"))
    }

    @Test
    fun lePlurielPasse() {
        // Sans cette tolérance, 13 % des traductions perdaient leur gras.
        assertEquals(
            listOf("chômeurs"),
            mis("la nouvelle mesure vise à réduire le nombre de chômeurs", "chômeur")
        )
        assertEquals(
            listOf("aiguilles à tricoter"),
            mis("ma mère a des aiguilles à tricoter de toutes les tailles", "aiguille à tricoter")
        )
    }

    @Test
    fun unMotCourtNePrendPasDeS() {
        // « pas » n'est pas le pluriel de « pa », et « les » pas celui de
        // « le » : la tolérance s'arrête à quatre lettres.
        assertTrue(mis("il ne marche pas", "pa").isEmpty())
    }

    @Test
    fun uneAcceptionSeRetrouveEntiereOuPasDuTout() {
        // « gëftgréng » se glose « vert criard » et sa phrase dit « vert vif » :
        // grasser le seul « vert » donnerait une moitié d'expression.
        assertTrue(
            mis("la violoniste portait un costume vert vif", "vert criard").isEmpty()
        )
        assertEquals(
            listOf("carte de Nouvel An"),
            mis("j'envoie une carte de Nouvel An à ma tante", "carte de Nouvel An")
        )
    }

    @Test
    fun deuxSensDansLaMemePhraseFontRenoncer() {
        // La glose liste les sens d'un mot, pas ceux d'une phrase : rien ne dit
        // lequel celle-ci emploie.
        assertTrue(mis("on a sorti le landau et la calèche", "landau, calèche").isEmpty())
        // Un seul sens présent, lui, se pose sans hésiter, même quand la glose
        // en liste plusieurs.
        assertEquals(listOf("calèche"), mis("on a sorti la calèche", "landau, calèche"))
    }

    @Test
    fun lesPrecisionsEntreParenthesesNeSontPasCherchees() {
        assertEquals(
            listOf("tombés"),
            mis("trois tombés à pleurer", "tombé (au champ d'honneur)")
        )
    }

    @Test
    fun uneTraductionQuiNEmploiePasLaGloseResteMaigre() {
        // Mesuré : un tiers des traductions. La traduction est fidèle à la
        // phrase, pas au mot, et c'est très bien ainsi.
        assertTrue(mis("nous devons lui changer les idées", "pensée").isEmpty())
        assertTrue(
            mis("le nourrisson est installé paisiblement dans la poussette", "landau, calèche")
                .isEmpty()
        )
    }

    @Test
    fun uneGloseAbsenteOuVideNeCasseRien() {
        assertTrue(mis("une phrase", null).isEmpty())
        assertTrue(mis("une phrase", "").isEmpty())
        assertTrue(mis("", "abeille").isEmpty())
    }

    @Test
    fun toutesLesOccurrencesDuMemeSensSontMisesEnGras() {
        assertEquals(
            listOf("maison", "maison"),
            mis("de maison en maison", "maison")
        )
    }
}
