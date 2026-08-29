/*
 * Moteur de suggestion du simulateur Lëtzebuergesch Clavier.
 * Port JS fidèle de SuggestionEngine.kt / LevenshteinDistance.kt /
 * AccentTolerantMatcher.kt / BilingualSuggestion.kt (android_keyboard/).
 * Toute divergence de comportement avec l'app Android est un bug de ce fichier.
 *
 * Les noms globaux (KreyolSimulatorEngine, plus bas) restent ceux du dépôt
 * amont KreyolKeyb, dont ce fichier est un portage : les renommer rendrait
 * chaque futur `git merge` plus coûteux pour un gain nul côté page. Même
 * politique que le paquet Kotlin com.example.kreyolkeyboard.
 */
(function (global) {
  'use strict';

  const MAX_SUGGESTIONS = 5; // 3 lëtzebuergesch + 2 français, comme SuggestionEngine.MAX_SUGGESTIONS
  const MIN_WORD_LENGTH = 1;

  // ---- AccentTolerantMatcher ----

  const NORMALIZATION_MAP = {};
  for (const c of 'àáâäãåāăą') NORMALIZATION_MAP[c] = 'a';
  for (const c of 'èéêëēėęě') NORMALIZATION_MAP[c] = 'e';
  for (const c of 'ìíîïīįĩ') NORMALIZATION_MAP[c] = 'i';
  for (const c of 'òóôöõøōőœ') NORMALIZATION_MAP[c] = 'o';
  for (const c of 'ùúûüūůũűų') NORMALIZATION_MAP[c] = 'u';
  for (const c of 'ýÿŷ') NORMALIZATION_MAP[c] = 'y';
  NORMALIZATION_MAP['ç'] = 'c';
  NORMALIZATION_MAP['ñ'] = 'n';

  const AccentTolerantMatcher = {
    normalize(text) {
      if (!text) return text;
      let out = '';
      for (const ch of text) {
        const lower = ch.toLowerCase();
        if (lower === 'ß') out += 'ss';
        else out += NORMALIZATION_MAP[lower] || lower;
      }
      return out;
    },
    matches(input, target) {
      return this.normalize(input) === this.normalize(target);
    },
    startsWith(input, dictionaryWord) {
      return this.normalize(dictionaryWord).startsWith(this.normalize(input));
    },
    hasAccents(word) {
      return word !== this.normalize(word);
    }
  };

  // ---- LevenshteinDistance ----

  function levenshtein(s1, s2) {
    const len1 = s1.length;
    const len2 = s2.length;
    if (len1 === 0) return len2;
    if (len2 === 0) return len1;

    const dp = [];
    for (let i = 0; i <= len1; i++) dp.push(new Array(len2 + 1).fill(0));
    for (let i = 0; i <= len1; i++) dp[i][0] = i;
    for (let j = 0; j <= len2; j++) dp[0][j] = j;

    for (let i = 1; i <= len1; i++) {
      for (let j = 1; j <= len2; j++) {
        const cost = s1[i - 1].toLowerCase() === s2[j - 1].toLowerCase() ? 0 : 1;
        dp[i][j] = Math.min(
          dp[i - 1][j] + 1,
          dp[i][j - 1] + 1,
          dp[i - 1][j - 1] + cost
        );
      }
    }
    return dp[len1][len2];
  }

  // dictionary: [[word, freq], ...] → [[word, freq, distance], ...]
  function findClosestMatches(input, dictionary, maxDistance, maxResults, lengthTolerance) {
    if (!input) return [];
    const inputLength = input.length;
    const candidates = dictionary.filter(
      ([word]) => Math.abs(word.length - inputLength) <= lengthTolerance
    );
    return candidates
      .map(([word, freq]) => [word, freq, levenshtein(input, word)])
      .filter(([, , d]) => d <= maxDistance)
      .sort((a, b) => a[2] - b[2] || b[1] - a[1])
      .slice(0, maxResults);
  }

  function findClosestMatchesNormalized(input, dictionary, normalizer, maxDistance, maxResults) {
    if (!input) return [];
    const normalizedInput = normalizer(input);
    const inputLength = normalizedInput.length;
    const candidates = dictionary.filter(
      ([word]) => Math.abs(normalizer(word).length - inputLength) <= 2
    );
    return candidates
      .map(([word, freq]) => [word, freq, levenshtein(normalizedInput, normalizer(word))])
      .filter(([, , d]) => d <= maxDistance)
      .sort((a, b) => a[2] - b[2] || b[1] - a[1])
      .slice(0, maxResults);
  }

  // ---- casing / scoring (SuggestionEngine companion) ----

  function isLetter(ch) {
    return /\p{L}/u.test(ch);
  }
  function isUpper(ch) {
    return isLetter(ch) && ch === ch.toUpperCase() && ch !== ch.toLowerCase();
  }
  function isLower(ch) {
    return isLetter(ch) && ch === ch.toLowerCase() && ch !== ch.toUpperCase();
  }

  // Groussschreiwung : un signal EXPLICITE de l'utilisateur l'emporte, sinon
  // la casse du dictionnaire fait foi. Une frappe en minuscules n'est pas une
  // demande de minuscules, c'est l'absence de signal — recopier la casse de la
  // frappe détruirait la majuscule du substantif (« hau » → « haus »).
  // Miroir de SuggestionEngine.applyCasingPattern.
  function applyCasingPattern(input, suggestion) {
    if (!input || !suggestion) return suggestion;

    const letters = [...input].filter(isLetter);
    if (letters.length >= 2 && letters.every(isUpper)) {
      return suggestion.toUpperCase();
    }

    if (isUpper(input[0]) &&
        [...input.slice(1)].every((ch) => isLower(ch) || !isLetter(ch))) {
      return suggestion.charAt(0).toUpperCase() + suggestion.slice(1);
    }

    // Motif mixte : une majuscule ailleurs qu'en tête, choix délibéré.
    if (![...input.slice(1)].some(isUpper)) return suggestion;

    let result = '';
    for (let i = 0; i < suggestion.length; i++) {
      if (i < input.length) {
        const inputChar = input[i];
        const suggestionChar = suggestion[i];
        if (isUpper(inputChar)) result += suggestionChar.toUpperCase();
        else if (isLower(inputChar)) result += suggestionChar.toLowerCase();
        else result += suggestionChar;
      } else {
        result += suggestion[i];
      }
    }
    return result;
  }

  function calculateDictionaryScore(word, input, frequency, levenshteinDistance) {
    let score = frequency;
    const distance = levenshteinDistance || 0;

    if (distance > 0) {
      // 1 000 000 et non 100 000 : le poids doit dépasser la fréquence la plus
      // haute du dictionnaire, sinon une correction à deux éditions vers un mot
      // très fréquent repasse devant une correction à une édition. Relevé à
      // 100 000 ici alors que le moteur Android était passé à 1 000 000 le
      // 27 août — le simulateur rejouait donc le bug corrigé dans l'application.
      score += (3 - distance) * 1000000;
    }
    if (AccentTolerantMatcher.startsWith(input, word)) {
      score += 50;
    }
    if (word.length <= 6) {
      score += 10;
    }
    if (word.length > 12) {
      score -= 10;
    }
    if (AccentTolerantMatcher.hasAccents(word)) {
      score += 5;
    }
    return score;
  }

  // ---- BilingualConfig defaults (BilingualSuggestion.kt) ----

  const DEFAULT_BILINGUAL_CONFIG = {
    frenchActivationThreshold: 3,
    maxLuxSuggestions: 3,
    maxFrenchSuggestions: 2,
    luxPriorityBoost: 1.5,
    frenchPenalty: 0.8,
    enableFrenchSupport: true,
    luxOnlyMode: false
  };

  // ---- SuggestionEngine ----

  class SuggestionEngine {
    constructor() {
      this.dictionary = []; // [[word, freq], ...] trié par fréquence décroissante
      this.normalizedWords = [];
      this.ngramModel = {}; // { word: [{word, probability}, ...] }
      this.frenchWords = []; // [[word, freq], ...]
      this.wordHistory = [];
      this.bilingualConfig = { ...DEFAULT_BILINGUAL_CONFIG };
    }

    loadDictionary(rawArray) {
      // La casse livrée est conservée (« Joer », « RTL ») : les comparaisons
      // passent par normalizedWords, qui replie déjà.
      const list = rawArray.map(([word, freq]) => [String(word), freq || 1]);
      list.sort((a, b) => b[1] - a[1]);
      this.dictionary = list;
      this.normalizedWords = list.map(([word]) => AccentTolerantMatcher.normalize(word));
    }

    loadFrenchDictionary(raw) {
      const list = (raw.words || []).map(([word, freq]) => [String(word).toLowerCase(), freq || 1]);
      list.sort((a, b) => b[1] - a[1]);
      this.frenchWords = list;
    }

    loadNgramModel(raw) {
      this.ngramModel = raw || {};
    }

    addWordToHistory(word) {
      const clean = word.toLowerCase().trim();
      if (clean.length >= MIN_WORD_LENGTH) {
        this.wordHistory.push(clean);
        if (this.wordHistory.length > 5) this.wordHistory.shift();
      }
    }

    clearHistory() {
      this.wordHistory = [];
    }

    shouldActivateFrench(input) {
      const c = this.bilingualConfig;
      return c.enableFrenchSupport && !c.luxOnlyMode && input.length >= c.frenchActivationThreshold;
    }

    adjustScoreByLanguage(score, language) {
      const c = this.bilingualConfig;
      return language === 'LUX' ? score * c.luxPriorityBoost : score * c.frenchPenalty;
    }

    // → [[word, freq, distance], ...]
    getDictionarySuggestions(input) {
      if (input.length < MIN_WORD_LENGTH) return [];

      const normalizedInput = AccentTolerantMatcher.normalize(input);
      const matches = [];
      for (let i = 0; i < this.dictionary.length; i++) {
        if (this.normalizedWords[i].startsWith(normalizedInput)) {
          matches.push([this.dictionary[i][0], this.dictionary[i][1], 0]);
          if (matches.length >= MAX_SUGGESTIONS * 2) break;
        }
      }

      if (matches.length === 0 && input.length >= 3) {
        return this.getSpellCorrectionSuggestions(input);
      }
      return matches;
    }

    getSpellCorrectionSuggestions(input) {
      if (input.length < 3) return [];

      const normalizedMatches = findClosestMatchesNormalized(
        input,
        this.dictionary,
        (str) => AccentTolerantMatcher.normalize(str),
        2,
        MAX_SUGGESTIONS
      );
      if (normalizedMatches.length > 0) return normalizedMatches;

      return findClosestMatches(input, this.dictionary, 2, MAX_SUGGESTIONS, 2);
    }

    // Le modèle porte deux familles de clés dans un seul objet plat : un mot
    // ("der", issu des bigrammes) et deux mots ("an der", issu des trigrammes).
    // La paire est essayée en premier, nettement plus précise, avec repli sur le
    // dernier mot seul. Cf. SuggestionEngine.resolveNgramContext().
    resolveNgramContext(previousWord, lastWord) {
      const twoWordContext = previousWord ? previousWord + ' ' + lastWord : null;
      if (twoWordContext && Object.prototype.hasOwnProperty.call(this.ngramModel, twoWordContext)) {
        return twoWordContext;
      }
      return lastWord;
    }

    // → [word, ...] triés par probabilité décroissante
    getNgramSuggestions() {
      const lastWord = this.wordHistory[this.wordHistory.length - 1];
      if (!lastWord) return [];
      const previousWord = this.wordHistory[this.wordHistory.length - 2];
      const list = this.ngramModel[this.resolveNgramContext(previousWord, lastWord)];
      if (!list) return [];

      // Deux encodages sont acceptés, et donnent exactement les mêmes
      // suggestions :
      //   - {word, probability}, celui du fichier embarqué dans l'APK ;
      //   - une simple chaîne, celui de simulateur-ngrams.json, qui pèse 1,4 Mo
      //     au lieu de 5,1 Mo pour un fichier retéléchargé à chaque visite.
      // Les probabilités n'ont jamais servi qu'à ce tri, et les deux fichiers
      // sont écrits par probabilité décroissante : le tri est donc déjà fait.
      // Il est conservé pour le format de l'APK, et neutre sur l'autre — le
      // tri de JavaScript est stable depuis ES2019, une liste de zéros garde
      // son ordre. Cette tolérance permet de déposer le fichier de l'APK tel
      // quel dans assets/ pour comparer les deux moteurs.
      const seen = new Set();
      const suggestions = [];
      for (const entry of list) {
        const word = typeof entry === 'string' ? entry : entry.word;
        const prob = (entry && typeof entry.probability === 'number')
          ? entry.probability : 0;
        if (word && !seen.has(word)) {
          seen.add(word);
          suggestions.push([word, prob]);
        }
      }
      suggestions.sort((a, b) => b[1] - a[1]);
      return suggestions.slice(0, MAX_SUGGESTIONS).map((s) => s[0]);
    }

    // → [{word, score, language}, ...] casse déjà appliquée
    getLuxSuggestions(input) {
      const dictMatches = this.getDictionarySuggestions(input);
      const ngramMatches = this.wordHistory.length > 0 ? this.getNgramSuggestions() : [];

      const scores = new Map();
      for (const [word, freq, distance] of dictMatches) {
        scores.set(word, calculateDictionaryScore(word, input, freq, distance));
      }

      const lowerInput = input.toLowerCase();
      for (const word of ngramMatches) {
        if (word.toLowerCase().startsWith(lowerInput)) {
          // Miroir de SuggestionEngine.NGRAM_CONTEXT_WEIGHT : à 50, face à des
          // fréquences qui montent à 100 105, le contexte ne réordonnait rien.
          scores.set(word, (scores.get(word) || 0) + 150000);
        }
      }

      const result = [...scores.entries()].map(([word, score]) => ({
        word: applyCasingPattern(input, word),
        score: this.adjustScoreByLanguage(score, 'LUX'),
        language: 'LUX'
      }));
      result.sort((a, b) => b.score - a.score);
      return result.slice(0, this.bilingualConfig.maxLuxSuggestions);
    }

    getFrenchSuggestions(input) {
      const prefix = input.toLowerCase();
      if (!this.frenchWords.length) return [];

      const matches = this.frenchWords
        .filter(([word]) => word.startsWith(prefix))
        .sort((a, b) => b[1] - a[1] || a[0].length - b[0].length)
        .slice(0, this.bilingualConfig.maxFrenchSuggestions);

      const result = matches.map(([word, freq]) => {
        const baseScore = calculateDictionaryScore(word, input, freq, 0);
        return {
          word: applyCasingPattern(input, word),
          score: this.adjustScoreByLanguage(baseScore, 'FRENCH'),
          language: 'FRENCH'
        };
      });
      result.sort((a, b) => b.score - a.score);
      return result;
    }

    // Positions 1-3 réservées au luxembourgeois, 4-5 français optionnel
    mergeSuggestionsLuxFirst(luxSuggs, frenchSuggs) {
      const result = [];
      const used = new Set();

      for (const s of luxSuggs.slice(0, 3)) {
        const key = s.word.toLowerCase();
        if (!used.has(key)) {
          result.push(s);
          used.add(key);
        }
      }
      for (const s of frenchSuggs.slice(0, 2)) {
        const key = s.word.toLowerCase();
        if (result.length < MAX_SUGGESTIONS && !used.has(key)) {
          result.push(s);
          used.add(key);
        }
      }
      for (const s of luxSuggs.slice(3)) {
        const key = s.word.toLowerCase();
        if (result.length < MAX_SUGGESTIONS && !used.has(key)) {
          result.push(s);
          used.add(key);
        }
      }
      return result;
    }

    // Suggestions bilingues (mode frappe) — équivalent generateBilingualSuggestions()
    generateBilingualSuggestions(input) {
      if (input.length < MIN_WORD_LENGTH) return [];
      const lux = this.getLuxSuggestions(input);
      const french = this.shouldActivateFrench(input) ? this.getFrenchSuggestions(input) : [];
      return this.mergeSuggestionsLuxFirst(lux, french);
    }

    // Prédictions contextuelles n-gram (mode après espace) — luxembourgeois uniquement
    generateContextualSuggestions() {
      if (this.wordHistory.length === 0 || Object.keys(this.ngramModel).length === 0) return [];
      return this.getNgramSuggestions();
    }
  }

  global.KreyolSimulatorEngine = {
    SuggestionEngine,
    AccentTolerantMatcher,
    levenshtein,
    applyCasingPattern,
    calculateDictionaryScore
  };
})(typeof window !== 'undefined' ? window : globalThis);

// Export CommonJS pour les tests Node (sans effet dans le navigateur)
if (typeof module !== 'undefined' && module.exports) {
  module.exports = globalThis.KreyolSimulatorEngine;
}
