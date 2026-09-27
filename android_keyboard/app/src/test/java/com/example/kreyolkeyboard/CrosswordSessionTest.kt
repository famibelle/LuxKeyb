package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.crossword.CrosswordDifficulty
import com.example.kreyolkeyboard.crossword.CrosswordGrid
import com.example.kreyolkeyboard.crossword.CrosswordSession
import com.example.kreyolkeyboard.crossword.CrosswordWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Règles de la partie de mots croisés, sans Android.
 *
 * Ce qui se joue ici décide de ce que le jeu enseigne : une case qui se valide
 * par erreur apprend une faute, une faute montrée trop tôt dicte la réponse.
 *
 * La grille de test, trois mots :
 *
 * ```
 *     H A U S      HAUS   (0,0) horizontal
 *     E . S .      HEEM   (0,0) vertical, croise HAUS sur le H
 *     E . E .      USERËN (0,2) vertical, croise HAUS sur le U
 *     M . R .
 *     . . Ë .
 *     . . N .
 * ```
 * Elle est petite exprès : elle porte un croisement partagé (la case (0,0)
 * appartient à deux mots et leur donne le même numéro) et une lettre accentuée,
 * qui sont les deux seules choses que la partie traite autrement que du texte.
 */
class CrosswordSessionTest {

    private fun grilleDeTest() = CrosswordGrid(
        width = 4,
        height = 6,
        difficulty = CrosswordDifficulty.FACILE,
        words = listOf(
            CrosswordWord("HAUS", "Haus", "maison", 0, 0, across = true),
            CrosswordWord("HEEM", "heem", "à la maison", 0, 0, across = false),
            CrosswordWord("USERËN", "userën", "user", 0, 2, across = false)
        )
    )

    @Test
    fun `la numerotation suit l'ordre de lecture et se partage`() {
        val grille = grilleDeTest()
        // Deux mots qui partent de la même case portent le même numéro : c'est
        // la règle des mots croisés, et elle évite d'écrire deux fois « 1 »
        // dans un coin de case de trente pixels.
        assertEquals(1, grille.numeros[0])
        assertEquals(1, grille.numeros[1])
        assertEquals(2, grille.numeros[2])
    }

    @Test
    fun `les cases hors des mots ne sont pas jouables`() {
        val grille = grilleDeTest()
        assertTrue(grille.estCaseJouable(0, 3))
        assertTrue(grille.estCaseJouable(5, 2))
        assertFalse("(4,0) n'est traversée par aucun mot", grille.estCaseJouable(4, 0))
        assertEquals('S', grille.solutionAt(0, 3))
        assertNull(grille.solutionAt(4, 0))
    }

    @Test
    fun `toucher deux fois une case croisee change de sens`() {
        val partie = CrosswordSession(grilleDeTest())
        partie.selectionner(0, 0)
        val premier = partie.motSelectionne
        partie.selectionner(0, 0)
        val second = partie.motSelectionne
        assertTrue(
            "la seconde touche doit passer à l'autre mot de la case",
            premier != second
        )
        // Une case qui n'appartient qu'à un mot ne bascule nulle part.
        partie.selectionner(1, 0)
        partie.selectionner(1, 0)
        assertEquals(1, partie.motSelectionne)
    }

    @Test
    fun `ecrire avance et saute les cases deja remplies`() {
        val partie = CrosswordSession(grilleDeTest())
        // On remplit HEEM par le vertical, ce qui donne son H à HAUS.
        partie.selectionnerMot(1)
        "HEEM".forEach { partie.ecrire(it) }
        assertEquals('H', partie.lettreAt(0, 0))

        // En repartant sur HAUS, la première case vide est la deuxième lettre :
        // la sélection ne doit pas obliger à réécrire le H déjà acquis.
        partie.selectionnerMot(0)
        assertEquals(1, partie.caseSelectionnee)
        "AUS".forEach { partie.ecrire(it) }
        assertTrue(partie.motJuste(0))
        assertEquals(2, partie.motsJustes())
    }

    @Test
    fun `effacer recule quand la case courante est vide`() {
        val partie = CrosswordSession(grilleDeTest())
        partie.selectionnerMot(0)
        partie.ecrire('H')
        partie.ecrire('A')
        // Écrire avance : le curseur est sur la troisième case, qui est vide.
        // L'effacement recule donc et enlève le A, comme un retour arrière
        // partout ailleurs — s'il effaçait la case courante il ne ferait rien,
        // et il faudrait appuyer deux fois pour voir quelque chose disparaître.
        partie.effacer()
        assertNull(partie.lettreAt(0, 1))
        partie.effacer()
        assertNull(partie.lettreAt(0, 0))
    }

    @Test
    fun `une faute ne se lit qu'une fois le mot rempli`() {
        val partie = CrosswordSession(grilleDeTest())
        partie.selectionnerMot(0)
        partie.ecrire('X')
        // La case est fausse, mais le mot n'est pas rempli : c'est l'écran qui
        // décide de ne rien montrer, et il s'appuie sur motRempli.
        assertTrue(partie.caseFausse(0, 0))
        assertFalse(partie.motRempli(0))

        "AUS".forEach { partie.ecrire(it) }
        assertTrue(partie.motRempli(0))
        assertFalse(partie.motJuste(0))
    }

    @Test
    fun `la partie se termine quand tous les mots sont justes`() {
        val partie = CrosswordSession(grilleDeTest())
        assertFalse(partie.termine())
        partie.reveler()
        assertTrue(partie.termine())
        assertEquals(3, partie.motsJustes())
        // Les accents font partie de la réponse : c'est tout l'objet du jeu.
        assertEquals('Ë', partie.lettreAt(4, 2))
    }

    @Test
    fun `la forme canonique dit si le mot garde sa majuscule`() {
        val grille = grilleDeTest()
        assertTrue("Haus est un substantif", grille.words[0].enseigneUneMajuscule)
        assertFalse("heem ne l'est pas", grille.words[1].enseigneUneMajuscule)
    }
}
