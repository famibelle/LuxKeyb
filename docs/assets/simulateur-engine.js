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

  // Nombre de correspondances par préfixe retenues avant scoring
  // (SuggestionEngine.CANDIDATE_POOL_SIZE). Le dictionnaire est parcouru par
  // fréquence décroissante : une fenêtre trop étroite écarterait un mot rare
  // du corpus avant que le contexte n-gramme ou le bonus d'accent puisse le
  // faire remonter. Le simulateur la prenait à 10 (MAX_SUGGESTIONS × 2).
  const CANDIDATE_POOL_SIZE = 40;

  // Fréquence d'une forme venue du LOD (SuggestionEngine.LOD_FREQUENCY). Le
  // corpus ne retient rien sous 3 : la valeur 1 est un marqueur autant qu'un
  // poids, et range ces formes après tous les mots du corpus.
  const LOD_FREQUENCY = 1;

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

  // Miroir de LevenshteinDistance.calculateBounded() : deux rangées au lieu de
  // la matrice entière, casse repliée une fois par chaîne, et abandon dès que
  // le minimum d'une rangée dépasse déjà la borne. Avec les ~123 000 formes que
  // le LOD ajoute au dictionnaire, le repli orthographique parcourt tout : la
  // version à matrice complète, qui réallouait (m+1)×(n+1) cases par mot
  // comparé, y coûtait plusieurs centaines de millisecondes par frappe.
  function levenshteinBounded(s1, s2, maxDistance) {
    const len1 = s1.length;
    const len2 = s2.length;
    if (len1 === 0) return len2;
    if (len2 === 0) return len1;
    if (Math.abs(len1 - len2) > maxDistance) return maxDistance + 1;

    const a = s1.toLowerCase();
    const b = s2.toLowerCase();

    let precedente = new Array(len2 + 1);
    let courante = new Array(len2 + 1);
    for (let j = 0; j <= len2; j++) precedente[j] = j;

    for (let i = 1; i <= len1; i++) {
      courante[0] = i;
      let minimumLigne = i;
      const ca = a.charCodeAt(i - 1);
      for (let j = 1; j <= len2; j++) {
        const cout = ca === b.charCodeAt(j - 1) ? 0 : 1;
        const v = Math.min(
          precedente[j] + 1,          // suppression
          courante[j - 1] + 1,        // insertion
          precedente[j - 1] + cout    // substitution
        );
        courante[j] = v;
        if (v < minimumLigne) minimumLigne = v;
      }
      if (minimumLigne > maxDistance) return maxDistance + 1;
      const echange = precedente;
      precedente = courante;
      courante = echange;
    }
    return precedente[len2];
  }

  function levenshtein(s1, s2) {
    return levenshteinBounded(s1, s2, Infinity);
  }

  // Tri de LevenshteinDistance : distance croissante, puis fréquence
  // décroissante. Stable, comme sortedWith() côté Kotlin.
  function classerCorrections(matches, maxResults) {
    return matches
      .sort((a, b) => a[2] - b[2] || b[1] - a[1])
      .slice(0, maxResults);
  }

  // dictionary: [[word, freq], ...] → [[word, freq, distance], ...]
  function findClosestMatches(input, dictionary, maxDistance, maxResults, lengthTolerance) {
    if (!input) return [];
    const inputLength = input.length;
    const matches = [];
    for (const [word, freq] of dictionary) {
      if (Math.abs(word.length - inputLength) > lengthTolerance) continue;
      const distance = levenshteinBounded(input, word, maxDistance);
      if (distance <= maxDistance) matches.push([word, freq, distance]);
    }
    return classerCorrections(matches, maxResults);
  }

  // `normalizedWords` : les formes du dictionnaire déjà repliées, alignées
  // index à index. Les recalculer ici (le normaliseur passait deux fois par mot,
  // avant même le filtre de longueur) reconstruisait tout le dictionnaire
  // replié à chaque frappe.
  function findClosestMatchesNormalized(input, dictionary, normalizedWords, normalizer, maxDistance, maxResults) {
    if (!input) return [];
    const normalizedInput = normalizer(input);
    const inputLength = normalizedInput.length;
    const matches = [];
    const n = Math.min(dictionary.length, normalizedWords.length);
    for (let i = 0; i < n; i++) {
      const normalizedWord = normalizedWords[i];
      if (Math.abs(normalizedWord.length - inputLength) > maxDistance) continue;
      const distance = levenshteinBounded(normalizedInput, normalizedWord, maxDistance);
      if (distance <= maxDistance) {
        matches.push([dictionary[i][0], dictionary[i][1], distance]);
      }
    }
    return classerCorrections(matches, maxResults);
  }

  // ---- MotsEcartes.GROSSIERETES ----

  // Le clavier ne propose jamais ces mots, ni en complétion, ni en correction,
  // ni en prédiction du mot suivant : ce n'est pas un refus de saisie, qui veut
  // les écrire les écrit lettre à lettre. Liste recopiée de MotsEcartes.kt, et
  // verrouillée contre elle par SimulatorMirrorTest : elle ne doit pas diverger,
  // sans quoi le simulateur proposerait à un visiteur ce que l'application se
  // refuse à mettre dans sa bouche.
  const GROSSIERETES = [
    "Aarsch", "Aarschkrécher", "Aarschkréchesch", "Aarschkréchesche",
    "Aarschkrécheschen", "Aarschlach", "Aarschlächer", "Aasch",
    "Aaschkrécher", "Aaschkréchesch", "Aaschkréchesche", "Aaschkrécheschen",
    "Aaschlach", "Aaschlächer", "Bepisstes", "Beseechtes", "Bordell",
    "Bordelle", "Bordellen", "Drecksak", "Drecksäck", "Eesch", "Emmerdeur",
    "Emmerdeure", "Emmerdeuren", "Emmerdeuse", "Emmerdeusen", "Emmerdeusë",
    "Emmerdéiertes", "Fatzert", "Fatzerte", "Fatzerten", "Fotz", "Fotze",
    "Fotzen", "Gefécktes", "Houer", "Houere", "Houeren", "Hourebud",
    "Hourebude", "Hourebuden", "Klut", "Klute", "Kluten", "Knaschtert",
    "Knaschterte", "Knaschterten", "Knaschtsak", "Knaschtsäck", "Louder",
    "Loudere", "Louderen", "Merd", "Nenn", "Piss", "Puff", "Puffe", "Puffen",
    "Schläimschësser", "Schläimschëssesch", "Schläimschësseschen", "Schäiss",
    "Schäissdreck", "Schäisserei", "Schäissereie", "Schäissereien",
    "Schäisshaiser", "Schäisshaus", "Schäisspabeier", "Seech", "Säckdréier",
    "Tëtt", "Tëtten", "Veraaschtes", "Vullemätti", "Vullemättie",
    "Vullemättien", "bepiss", "bepisse", "bepissen", "bepissend", "bepisst",
    "bepisste", "bepisstem", "bepissten", "bepisstene", "bepisstenem",
    "bepisstenen", "bepisstener", "bepisstent", "bepisster", "beschass",
    "beschäiss", "beschäisse", "beschäissen", "beschäissend", "beschäisst",
    "beseech", "beseeche", "beseechen", "beseechend", "beseechs", "beseecht",
    "beseechte", "beseechtem", "beseechten", "beseechtene", "beseechtenem",
    "beseechtenen", "beseechtener", "beseechtent", "beseechter",
    "emmerdéier", "emmerdéiere", "emmerdéieren", "emmerdéierend",
    "emmerdéiers", "emmerdéiert", "emmerdéierte", "emmerdéiertem",
    "emmerdéierten", "emmerdéiertene", "emmerdéiertenem", "emmerdéiertenen",
    "emmerdéiertener", "emmerdéiertent", "emmerdéierter", "freck", "fuck",
    "féck", "fécke", "fécken", "féckend", "fécks", "féckt", "geféckt",
    "geféckte", "gefécktem", "geféckten", "gefécktene", "gefécktenem",
    "gefécktenen", "gefécktener", "gefécktent", "geféckter", "gehouert",
    "gepisst", "geschass", "geseecht", "houer", "houere", "houeren",
    "houerend", "houers", "houert", "piss", "pisse", "pissen", "pissend",
    "pisst", "schäiss", "schäisse", "schäissegal", "schäissen", "schäissend",
    "schäisst", "seech", "seeche", "seechen", "seechend", "seechs", "seecht",
    "shit", "veraasch", "veraasche", "veraaschen", "veraaschend",
    "veraaschs", "veraascht", "veraaschte", "veraaschtem", "veraaschten",
    "veraaschtene", "veraaschtenem", "veraaschtenen", "veraaschtener",
    "veraaschtent", "veraaschter", "vreck", "Äersch"
  ];
  const GROSSIER = new Set(GROSSIERETES.map((mot) => AccentTolerantMatcher.normalize(mot)));

  function estGrossier(mot) {
    return GROSSIER.has(AccentTolerantMatcher.normalize(mot));
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

  /**
   * Choisit, parmi les candidats qu'un contexte n-gramme propose, la forme
   * capitalisée correspondant à `word`, ou null. Miroir exact de
   * SuggestionEngine.pickContextualCapitalization().
   *
   * Le PREMIER candidat correspondant fait foi, quelle que soit sa casse : les
   * candidats arrivent triés par probabilité décroissante, donc c'est la forme
   * que ce contexte attend. Si c'est la minuscule, il n'y a rien à corriger —
   * aller chercher plus loin une variante capitalisée moins probable
   * reviendrait à imposer une majuscule que le contexte ne demande pas.
   */
  function pickContextualCapitalization(word, candidats) {
    if (word.length < 2) return null;
    if (/\p{Lu}/u.test(word)) return null; // l'utilisateur a donné un signal de casse
    const attendu = candidats.find((c) => c.toLowerCase() === word);
    if (!attendu || attendu === word) return null;
    return attendu;
  }

  const DEFAULT_BILINGUAL_CONFIG = {
    frenchActivationThreshold: 3,
    maxLuxSuggestions: 3,
    maxFrenchSuggestions: 2,
    luxPriorityBoost: 1.5,
    frenchPenalty: 0.8,
    enableFrenchSupport: true,
    luxOnlyMode: false
  };

  /**
   * Faut-il chercher une correction luxembourgeoise pour ce mot ? Miroir de
   * SuggestionEngine.devraitCorriger().
   *
   * Non s'il est trop court, la distance de Levenshtein sur deux lettres
   * rapprochant n'importe quoi de n'importe quoi. Et non, surtout, si le mot est
   * du français reconnu : le repli parcourt toutes les formes luxembourgeoises
   * pour proposer de corriger un mot sans aucune faute, et dans la mauvaise
   * langue. Le contrôle porte sur le mot ENTIER : tant qu'on tape « déche », le
   * français ne le reconnaît pas encore et le repli garde sa place.
   */
  function devraitCorriger(input, estFrancaisConnu) {
    if (input.length < 3) return false;
    return !estFrancaisConnu;
  }

  // ---- SuggestionEngine ----

  class SuggestionEngine {
    constructor() {
      this.dictionary = []; // [[word, freq], ...] trié par fréquence décroissante
      this.normalizedWords = [];
      this.ngramModel = {}; // { word: [{word, probability}, ...] }
      this.frenchWords = []; // [[word, freq], ...]
      this.frenchSet = new Set(); // mêmes formes, pour reconnaître un mot entier
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
      this.corpusSize = list.length;
    }

    /**
     * Ajoute les formes que le corpus ne peut pas donner (le LOD : « Läffelen »,
     * « sprang », « denks »), après tous les mots du corpus et à la fréquence 1.
     * Miroir de la fin de SuggestionEngine.loadDictionary().
     *
     * Elles se rangent en queue du classement par fréquence, là où la fenêtre
     * CANDIDATE_POOL_SIZE ne va les chercher que si moins de quarante mots du
     * corpus partagent le préfixe tapé : elles complètent les préfixes rares
     * sans rien changer à la frappe courante. Sans elles, taper « Läffelen »
     * proposait des corrections vers d'autres mots, le corpus journalistique
     * n'ayant jamais eu à l'écrire.
     *
     * Appelable après coup : le clavier répond dès que le corpus est chargé, le
     * LOD arrive ensuite sans bloquer la page.
     */
    addLodForms(formes) {
      if (!formes || !formes.length) return;
      const corpus = this.dictionary.slice(0, this.corpusSize || this.dictionary.length);
      const normCorpus = this.normalizedWords.slice(0, corpus.length);
      const lod = [];
      const normLod = [];
      for (const forme of formes) {
        const mot = String(forme);
        if (!mot) continue;
        lod.push([mot, LOD_FREQUENCY]);
        normLod.push(AccentTolerantMatcher.normalize(mot));
      }
      this.dictionary = corpus.concat(lod);
      this.normalizedWords = normCorpus.concat(normLod);
      this.lodCount = lod.length;
    }

    loadFrenchDictionary(raw) {
      const list = (raw.words || []).map(([word, freq]) => [String(word).toLowerCase(), freq || 1]);
      list.sort((a, b) => b[1] - a[1]);
      this.frenchWords = list;
      this.frenchSet = new Set(list.map(([word]) => word));
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
          if (matches.length >= CANDIDATE_POOL_SIZE) break;
        }
      }

      // Pas de prédiction par préfixe : correction orthographique, sauf si le
      // mot entier est du français reconnu (devraitCorriger).
      if (matches.length === 0 && devraitCorriger(input, this.isFrenchWord(input))) {
        return this.getSpellCorrectionSuggestions(input);
      }
      return matches;
    }

    // FrenchDictionary.containsWord(). Le simulateur n'a que le petit lexique de
    // démonstration : la reconnaissance y est donc plus étroite que sur le
    // téléphone, mais la règle est la même.
    isFrenchWord(mot) {
      return this.frenchSet.has(mot.toLowerCase());
    }

    getSpellCorrectionSuggestions(input) {
      if (input.length < 3) return [];

      const normalizedMatches = findClosestMatchesNormalized(
        input,
        this.dictionary,
        this.normalizedWords,
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

    // Tous les candidats d'un contexte, dans l'ordre du fichier et sans
    // troncature : la capitalisation contextuelle cherche une forme précise,
    // pas les cinq meilleures.
    ngramCandidateWords(context) {
      const list = this.ngramModel[context];
      if (!list) return [];
      const mots = [];
      for (const entry of list) {
        const word = typeof entry === 'string' ? entry : entry && entry.word;
        if (word) mots.push(word);
      }
      return mots;
    }

    /**
     * Rétablit la majuscule du substantif quand — et seulement quand — le
     * contexte n-gramme atteste la forme capitalisée. Miroir de
     * SuggestionEngine.contextualCapitalization().
     *
     * La casse canonique du dictionnaire ne suffit pas à déclencher la
     * correction : le dictionnaire français de secours capitalise « rue »,
     * « moment », « centre », et un message en français en ressortirait
     * défiguré. Dans « la rue de la gare », le mot précédent est `la`, contexte
     * inconnu, rien ne se déclenche ; dans « an der rue », le contexte
     * « an der » atteste `Rue`.
     */
    contextualCapitalization(word) {
      if (!word || Object.keys(this.ngramModel).length === 0) return null;
      const lastWord = this.wordHistory[this.wordHistory.length - 1];
      if (!lastWord) return null;
      const previousWord = this.wordHistory[this.wordHistory.length - 2];
      const context = this.resolveNgramContext(previousWord, lastWord);
      return pickContextualCapitalization(word, this.ngramCandidateWords(context));
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
      // sansGrossieretesBilingues() : le filtre passe après la fusion, sans
      // rien mettre à la place du mot retiré.
      return this.mergeSuggestionsLuxFirst(lux, french).filter((s) => !estGrossier(s.word));
    }

    // Prédictions contextuelles n-gram (mode après espace) — luxembourgeois uniquement
    generateContextualSuggestions() {
      if (this.wordHistory.length === 0 || Object.keys(this.ngramModel).length === 0) return [];
      return this.getNgramSuggestions().filter((mot) => !estGrossier(mot));
    }
  }

  global.KreyolSimulatorEngine = {
    SuggestionEngine,
    AccentTolerantMatcher,
    levenshtein,
    levenshteinBounded,
    estGrossier,
    devraitCorriger,
    GROSSIERETES,
    applyCasingPattern,
    pickContextualCapitalization,
    calculateDictionaryScore
  };
})(typeof window !== 'undefined' ? window : globalThis);

// Export CommonJS pour les tests Node (sans effet dans le navigateur)
if (typeof module !== 'undefined' && module.exports) {
  module.exports = globalThis.KreyolSimulatorEngine;
}
