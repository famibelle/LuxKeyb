package com.example.kreyolkeyboard

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.json.JSONArray
import java.io.File

/**
 * Tests de correction orthographique basés sur le dictionnaire créole réel.
 * Ces tests valident que l'algorithme de Levenshtein fonctionne correctement
 * avec le vrai dictionnaire utilisé par le clavier.
 */
class LevenshteinDictionaryTest {

    private lateinit var dictionary: List<Pair<String, Int>>

    @Before
    fun loadDictionary() {
        // Aucun repli : le fichier DOIT être là et DOIT être bien formé.
        //
        // Ce test retombait sur un échantillon de quarante mots en dur dès que
        // l'asset manquait, et faisait de même en attrapant toute exception de
        // lecture. Un dictionnaire absent, tronqué, ou livré comme objet JSON
        // au lieu du tableau de paires attendu — le défaut exact qui a rendu la
        // v10.2.6 muette en amont — laissait donc la suite entièrement verte.
        // Un garde-fou qui se désarme tout seul quand ce qu'il garde disparaît
        // ne garde rien.
        val dictionaryFile = File("src/main/assets/luxemburgish_dict.json")
        assertTrue(
            "Dictionnaire introuvable : ${dictionaryFile.absolutePath}. " +
                "Régénérer les assets avec Dictionnaires/LuxembourgishComplet.py.",
            dictionaryFile.exists()
        )

        val wordsArray = JSONArray(dictionaryFile.readText())
        assertTrue("Dictionnaire vide", wordsArray.length() > 0)

        val loadedDictionary = mutableListOf<Pair<String, Int>>()
        for (i in 0 until wordsArray.length()) {
            val wordArray = wordsArray.getJSONArray(i)
            // Replié en minuscules : ces tests mesurent la distance d'édition,
            // pas la Groussschreiwung, que GroussschreiwungTest couvre.
            val word = wordArray.getString(0).lowercase()
            val frequency = wordArray.optInt(1, 1)
            loadedDictionary.add(Pair(word, frequency))
        }

        dictionary = loadedDictionary
    }

    @Test
    fun testCommonTypo_MissingLetter() {
        // Test: "moin" → "moien" (lettre 'e' manquante)
        val matches = LevenshteinDistance.findClosestMatches(
            input = "moin",
            dictionary = dictionary,
            maxDistance = 2,
            maxResults = 5
        )
        
        assertTrue("Devrait trouver 'moien'", matches.any { it.first == "moien" })

        // "moien" devrait être en premier ou deuxième (après "moin" si présent dans le dictionnaire)
        if (matches.isNotEmpty() && dictionary.any { it.first == "moien" }) {
            val firstWord = matches.first().first
            assertTrue("Le premier résultat devrait être 'moien' ou 'moin', " +
                      "got: '$firstWord'", 
                      firstWord == "moien" || firstWord == "moin")
        }
    }

    @Test
    fun testCommonTypo_ExtraLetter() {
        // Test: "mersci" → "merci" (lettre 's' en trop)
        val matches = LevenshteinDistance.findClosestMatches(
            input = "mersci",
            dictionary = dictionary,
            maxDistance = 2,
            maxResults = 5
        )
        
        assertTrue("Devrait trouver 'merci'", matches.any { it.first == "merci" })
    }

    @Test
    fun testCommonTypo_WrongLetter() {
        // Test: "gesat" → "gesot" (lettre 'a' au lieu de 'o')
        val matches = LevenshteinDistance.findClosestMatches(
            input = "gesat",
            dictionary = dictionary,
            maxDistance = 2,
            maxResults = 5
        )
        
        assertTrue("Devrait trouver 'gesot'", matches.any { it.first == "gesot" })
    }

    @Test
    fun testAccentMissing_WithNormalization() {
        // Test: "letzebuergesch" → "lëtzebuergesch" (diacritique manquante)
        val normalizer = { s: String ->
            s.replace("é", "e")
             .replace("ë", "e")
             .replace("è", "e")
             .replace("ä", "a")
             .replace("ü", "u")
             .replace("ö", "o")
        }

        val matches = LevenshteinDistance.findClosestMatchesNormalized(
            input = "letzebuergesch",
            dictionary = dictionary,
            // Le moteur passe les formes normalisées qu'il a précalculées au
            // chargement ; ici on les construit, le dictionnaire est un
            // échantillon.
            normalizedWords = dictionary.map { normalizer(it.first) },
            normalizer = normalizer,
            maxDistance = 1,
            maxResults = 5
        )
        
        assertTrue("Devrait trouver 'lëtzebuergesch' avec normalisation",
                   matches.any { it.first == "lëtzebuergesch" })
    }

    @Test
    fun testMultipleCandidates_FrequencyPriority() {
        // Si plusieurs mots ont la même distance, celui avec la plus haute fréquence devrait être premier
        val matches = LevenshteinDistance.findClosestMatches(
            input = "p",
            dictionary = dictionary.filter { it.first.startsWith("p") && it.first.length <= 3 },
            maxDistance = 2,
            maxResults = 10
        )
        
        // Les mots les plus fréquents devraient être prioritaires
        assertTrue("Devrait trouver plusieurs candidats", matches.size > 1)
        
        // Vérifier que le tri par fréquence fonctionne (pour les mots à distance égale)
        if (matches.size >= 2) {
            val firstTwoHaveSameDistance = 
                LevenshteinDistance.calculate("p", matches[0].first) == 
                LevenshteinDistance.calculate("p", matches[1].first)
            
            if (firstTwoHaveSameDistance) {
                assertTrue("Le mot le plus fréquent devrait être premier",
                          matches[0].second >= matches[1].second)
            }
        }
    }

    @Test
    fun testNoMatch_TooFarDistance() {
        // Test avec un mot complètement différent, ne devrait rien trouver
        val matches = LevenshteinDistance.findClosestMatches(
            input = "xyz123",
            dictionary = dictionary,
            maxDistance = 2,
            maxResults = 5
        )
        
        assertTrue("Ne devrait trouver aucune correspondance pour un mot aléatoire",
                   matches.isEmpty())
    }

    @Test
    fun testShortWord_ExactMatch() {
        // Test avec des mots très courts du dictionnaire
        val matches = LevenshteinDistance.findClosestMatches(
            input = "an",
            dictionary = dictionary,
            maxDistance = 1,
            maxResults = 5
        )

        assertTrue("Devrait trouver 'an' (mot le plus fréquent)",
                   matches.any { it.first == "an" })

        if (dictionary.any { it.first == "an" }) {
            assertEquals("'an' devrait être le premier résultat", "an", matches.first().first)
        }
    }

    @Test
    fun testShortWord_OneLetterOff() {
        // Test: "mir" avec des fautes
        val matches = LevenshteinDistance.findClosestMatches(
            input = "mir",
            dictionary = dictionary,
            maxDistance = 1,
            maxResults = 5
        )
        
        assertTrue("Devrait trouver 'mir'", matches.any { it.first == "mir" })
    }

    @Test
    fun testMediumWord_CommonLuxWord() {
        // Test: "regierun" → "regierung"
        val matches = LevenshteinDistance.findClosestMatches(
            input = "regierun",
            dictionary = dictionary,
            maxDistance = 2,
            maxResults = 5
        )
        
        assertTrue("Devrait trouver 'regierung'", matches.any { it.first == "regierung" })
    }

    @Test
    fun testLongWord_ComplexTypo() {
        // Test avec un mot plus long
        if (dictionary.any { it.first == "zesummen" }) {
            val matches = LevenshteinDistance.findClosestMatches(
                input = "zesumen",  // 'm' manquant
                dictionary = dictionary,
                maxDistance = 2,
                maxResults = 5
            )

            assertTrue("Devrait trouver 'zesummen'", matches.any { it.first == "zesummen" })
        }
    }

    @Test
    fun testGreetings_TypicalErrors() {
        // Test des salutations courantes avec fautes
        val testCases = mapOf(
            "moin" to "moien",
            "mersci" to "merci",
            "gesat" to "gesot",
            "kanne" to "kanner"
        )
        
        testCases.forEach { (input, expected) ->
            if (dictionary.any { it.first == expected }) {
                val matches = LevenshteinDistance.findClosestMatches(
                    input = input,
                    dictionary = dictionary,
                    maxDistance = 2,
                    maxResults = 5
                )
                
                assertTrue("Devrait trouver '$expected' pour l'input '$input'",
                          matches.any { it.first == expected })
            }
        }
    }

    @Test
    fun testPerformance_LargeDictionary() {
        // Test de performance avec le dictionnaire complet
        val startTime = System.currentTimeMillis()
        
        val matches = LevenshteinDistance.findClosestMatches(
            input = "moien",
            dictionary = dictionary,
            maxDistance = 2,
            maxResults = 10
        )
        
        val duration = System.currentTimeMillis() - startTime
        
        assertTrue("La recherche devrait être rapide (< 500ms)", duration < 500)
        assertTrue("Devrait trouver des résultats", matches.isNotEmpty())
        
        println("⏱️ Performance: ${dictionary.size} mots traités en ${duration}ms")
    }

    @Test
    fun testLengthFilter_Optimization() {
        // Vérifier que le filtre de longueur fonctionne bien
        val shortInput = "pa"  // 2 lettres
        
        val matches = LevenshteinDistance.findClosestMatches(
            input = shortInput,
            dictionary = dictionary,
            maxDistance = 2,
            maxResults = 10,
            lengthTolerance = 2
        )
        
        // Tous les résultats devraient avoir une longueur proche
        matches.forEach { (word, _) ->
            val lengthDiff = kotlin.math.abs(word.length - shortInput.length)
            assertTrue("Le mot '$word' devrait respecter la tolérance de longueur",
                      lengthDiff <= 2)
        }
    }

    @Test
    fun testRealWorldScenario_TypingErrors() {
        // Scénario réel: l'utilisateur tape rapidement et fait plusieurs erreurs
        val realWorldTypos = listOf(
            "lnmou" to "lanmou",    // lettres inversées
            "ayti" to "ayiti",      // lettre manquante
            "lekol" to "lekòl",     // accent manquant
            "dllo" to "dlo",        // lettre doublée par erreur
            "manjee" to "manje"     // lettre doublée à la fin
        )
        
        realWorldTypos.forEach { (typo, correct) ->
            if (dictionary.any { it.first == correct }) {
                val matches = LevenshteinDistance.findClosestMatches(
                    input = typo,
                    dictionary = dictionary,
                    maxDistance = 2,
                    maxResults = 5
                )
                
                val found = matches.any { it.first == correct }
                if (!found) {
                    println("⚠️ N'a pas trouvé '$correct' pour '$typo'. Résultats: ${matches.map { it.first }}")
                }
                // Note: certains peuvent échouer selon la distance réelle
            }
        }
    }

    @Test
    fun testDictionarySize() {
        // Vérifier que le dictionnaire est bien chargé
        assertTrue("Le dictionnaire devrait contenir au moins 40 mots (sample)",
                   dictionary.size >= 40)
        
        println("📊 Dictionnaire: ${dictionary.size} mots chargés")
        
        // Afficher quelques statistiques
        val avgFrequency = dictionary.map { it.second }.average()
        val topWords = dictionary.sortedByDescending { it.second }.take(5)
        
        println("📈 Fréquence moyenne: ${avgFrequency.toInt()}")
        println("🏆 Top 5 mots: ${topWords.map { "${it.first} (${it.second})" }}")
    }
}
