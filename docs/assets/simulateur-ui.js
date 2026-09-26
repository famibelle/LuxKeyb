/*
 * Interface du simulateur de clavier Lëtzebuergesch Clavier.
 * Port du comportement de InputProcessor.kt / KeyboardLayoutManager.kt /
 * AccentHandler.kt / KreyolInputMethodServiceRefactored.kt (android_keyboard/),
 * réutilisant le moteur de suggestions dans simulateur-engine.js.
 *
 * Les noms globaux restent ceux du dépôt amont KreyolKeyb, dont cette page est
 * un portage : les renommer rendrait chaque futur `git merge` plus coûteux pour
 * un gain nul côté page, comme pour le paquet Kotlin com.example.kreyolkeyboard.
 */
(function () {
  'use strict';

  // QWERTZ et non AZERTY : la disposition des claviers physiques au Luxembourg
  // (suisse-français), celle que partagent l'allemand et le luxembourgeois écrit.
  // Cf. KeyboardLayoutManager.createAlphabeticLayout().
  const ALPHA_ROWS = [
    ['q', 'w', 'e', 'r', 't', 'z', 'u', 'i', 'o', 'p'],
    // « é » ferme la rangée d'accueil, là où le QWERTZ suisse-français la place :
    // c'est la diacritique n°1 du luxembourgeois (2 596 occurrences dans le corpus).
    ['a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'é'],
    ['⇧', 'y', 'x', 'c', 'v', 'b', 'n', 'm', '⌫'],
    // « ä » et « ë » gardent leur touche dédiée (1 004 et 1 251 occurrences), et
    // l'apostrophe la sienne : l'élision est structurelle — d'Land, s'Kanner.
    ['123', ',', 'ä', ' ', 'ë', "'", '.', 'EMOJI', '⏎']
  ];

  const NUMERIC_ROWS = [
    ['1', '2', '3', '4', '5', '6', '7', '8', '9', '0'],
    ['-', '/', ':', ';', '(', ')', '€', '&', '@', '"'],
    ['=', '.', ',', '?', '!', "'", '+', '*', '#', '⌫'],
    ['ABC', 'EMOJI', ' ', '⏎']
  ];

  // Rangée de contrôle sous le panneau emoji (createEmojiLayout()).
  const EMOJI_CONTROL_ROW = ['ABC', '⌫', ' ', '⏎'];

  // Géométrie et tailles de police reprises de KeyboardLayoutManager. Le
  // téléphone du simulateur est dessiné à la largeur d'un écran de 360 dp, donc
  // un dp y vaut un pixel et ces constantes se lisent telles quelles.
  const KEY_HEIGHT_PX = 48;
  const KEY_TEXT_HEIGHT_RATIO = 0.62;
  const WIDE_LABEL_TEXT_RATIO = 0.28;
  const SPACE_LABEL_TEXT_RATIO = 0.22;
  const LABEL_WIDTH_RATIO = 0.90;
  const EMOJI_WIDTH_RATIO = 0.77;

  // Les touches à icône de l'application (ic_shift_off/on/caps.xml,
  // ic_backspace.xml, ic_keyboard_return.xml). Les tracés sont recopiés tels
  // quels : le pathData d'un vector Android est un « d » SVG. Le padding, lui
  // aussi repris de createKeyButton(), s'exprime en part de la hauteur de touche
  // et l'icône se dessine dedans comme un FIT_CENTER.
  const ICONS = {
    backspace: {
      viewBox: '0 0 24 24',
      d: 'M22,3L7,3c-0.69,0 -1.23,0.35 -1.59,0.88L0,12l5.41,8.11c0.36,0.53 0.9,0.89 1.59,0.89h15c1.1,0 2,-0.9 2,-2L24,5c0,-1.1 -0.9,-2 -2,-2zM19,15.59L17.59,17 14,13.41 10.41,17 9,15.59 12.59,12 9,8.41 10.41,7 14,10.59 17.59,7 19,8.41 15.41,12 19,15.59z',
      padRatio: 10 / 48
    },
    // Cadre recadré sur l'encre (19 × 12) et tracé replacé dedans, exactement
    // comme le fait le <group> du vector : sans cela la flèche ne remplirait que
    // la moitié de sa touche.
    enter: {
      viewBox: '0 0 19 12',
      d: 'M19,7v4H5.83l3.58,-3.59L8,6l-6,6 6,6 1.41,-1.41L5.83,13H21V7z',
      transform: 'translate(-2,-6)',
      padRatio: 4 / 48
    },
    shiftOff: {
      viewBox: '0 0 24 24',
      d: 'M12,3L3,12h5v6h8v-6h5L12,3zM12,5.83L16.17,10H14v6h-4v-6H7.83L12,5.83z',
      fillRule: 'evenodd',
      padRatio: 6 / 48
    },
    shiftOn: {
      viewBox: '0 0 24 24',
      d: 'M12,3L3,12h5v6h8v-6h5L12,3z',
      padRatio: 6 / 48
    },
    shiftCaps: {
      viewBox: '0 0 24 24',
      d: 'M12,2L3,11h5v5h8v-5h5L12,2zM8,18h8v2H8z',
      padRatio: 6 / 48
    }
  };

  // AccentHandler.accentMap, reconstruite pour le luxembourgeois : chaque liste
  // est classée par fréquence décroissante des diacritiques dans les corpus
  // LuxAlign + LETZ. Les digraphes créoles GEREC (ch, dj, ng…) n'y sont plus,
  // et le trait d'union est passé en appui long sur « . ».
  const ACCENT_MAP = {
    a: ['ä', 'à', 'â'],
    e: ['é', 'ë', 'è', 'ê'],
    u: ['ü', 'û', 'ù'],
    o: ['ô', 'ö'],
    i: ['ï', 'î'],
    c: ['ç'],
    // Ordres pris sur le corpus et non sur l'habitude française : « : » 122 ≫
    // « ; » 6, « ? » 133 ≫ « ! » 1.
    ',': [':', ';'],
    '.': ['-', '?', '!', '…'],
    // L'ASCII ' reste sur la touche — seule forme sûre en adresse, identifiant ou
    // mot de passe — et l'apostrophe typographique ’, que le corpus emploie 2,6×
    // plus, ouvre le popup ; suivent les guillemets courbes.
    "'": ['’', '“', '”', '"']
  };
  // AccentHandler.cornerHintOverrides : « a » et « e » ont leurs diacritiques les
  // plus fréquentes déjà visibles ailleurs (ä et ë en rangée 4, é en fin de
  // rangée d'accueil), l'aperçu met donc en avant celles qui n'ont pas d'autre
  // porte d'entrée.
  const CORNER_HINTS = { a: ['à', 'â'], e: ['è', 'ê'] };

  // Ce qui appartient à un mot, comme InputProcessor.isWordCharacter() :
  // Character.isLetter(), donc toute lettre, majuscules accentuées comprises.
  // La liste explicite qu'on avait ici ne connaissait que les minuscules
  // accentuées : « É » en début de phrase coupait le mot en cours.
  const isWordString = (s) => s.length > 0 && Array.from(s).every((c) => /\p{L}/u.test(c));

  // Gestes et délais, repris de l'application.
  const ACCENT_LONG_PRESS_MS = 500;      // AccentHandler.LONG_PRESS_DELAY
  const BACKSPACE_LONG_PRESS_MS = 400;   // ViewConfiguration.getLongPressTimeout()
  const SPACE_SLOP_PX = 8;               // scaledTouchSlop : 8 dp
  const SPACE_CURSOR_STEP_PX = 10;       // SPACE_CURSOR_STEP_DP
  const SYNC_CURSEUR_MS = 120;           // DELAI_SYNC_CURSEUR
  const EMOJI_RECENTS_MAX = 30;          // EmojiRecents.CAPACITE
  const SWIPE_MIN_PX = 40;               // le balayage entre catégories d'emoji
  const HAPTIC_MS = 10;                  // une impulsion brève par frappe
  const TICK_MS = 4;                     // un cran de curseur

  class KeyboardSimulator {
    constructor(engine, els) {
      this.engine = engine;
      this.els = els;

      // Le texte du champ et la position du curseur : l'éditeur d'Android, que
      // le clavier n'écrit jamais qu'à travers l'InputConnection. Le simulateur
      // n'ajoutait qu'au bout ; le curseur mobile est ce qui permet le
      // glissement sur la barre d'espace et l'appui dans le texte.
      this._text = '';
      this.cursor = 0;
      this.currentWord = '';
      this.isCapitalMode = false;
      this.isCapsLock = false;
      this.isNumericMode = false;
      this.isEmojiMode = false;

      // Dernière capitalisation appliquée d'office : { origine, corrige }. Une
      // seule touche Retour arrière la défait, comme le veut la convention de
      // toute correction automatique (InputProcessor.lastAutoCapitalization).
      this.lastAutoCapitalization = null;

      // Le jeu d'emojis (~1900) n'est chargé qu'à la première ouverture du
      // panneau : la page n'a pas à payer 48 ko pour un mode qu'on n'ouvre pas.
      this.emojiData = null;
      this.emojiLoading = false;
      this.emojiCategory = 0;
      // Les catégories affichées : celles du jeu, précédées des emojis récents
      // quand il y en a. Figées à l'ouverture du panneau et non tenues à jour
      // pendant qu'il est ouvert : réordonner la grille sous le doigt de qui
      // enchaîne trois emojis lui ferait manquer le troisième.
      this.emojiCategories = null;
      // Récents (EmojiRecents) : en mémoire seulement. L'application les garde
      // dans ses préférences ; le simulateur, lui, ne laisse rien dans le
      // navigateur de qui l'essaie.
      this.recentEmojis = [];

      this.currentPopup = null;
      this._popupOutsideHandler = null;
      this._contextualTimer = null;
      this._syncTimer = null;
      this._signature = null;
      this._toucher = false;
      this._balayage = false;
      this._suppression = { debut: null, repetition: null };

      // Texte dicté en cours : longueur de la composition et texte qui la
      // précède. `null` tant qu'aucune dictée n'est ouverte — cf.
      // setDictationText(), qui porte la sémantique de setComposingText().
      this.composingLength = 0;
      this.dictationBase = null;
      // Installé par simulateur-dictee.js : rend true si une dictée était en
      // cours et vient d'être arrêtée par la frappe.
      this.dictationInterrupter = null;

      this.els.caret = document.createElement('span');
      this.els.caret.className = 'phone-caret';
      this._avant = null;
      this._apres = null;

      this.renderKeyboard();
      this.renderScreen();
      this.updateSuggestions([], false);

      this.els.resetBtn?.addEventListener('click', () => this.reset());
      this.els.screen?.addEventListener('click', (e) => this.placeCaretFromClick(e));
      this.bindPhysicalKeyboard();

      // La taille des libellés dépend de la largeur réelle des touches, qui
      // change avec celle de la fenêtre : elle se recalcule à chaque
      // redimensionnement du clavier, et pas seulement au rendu.
      if (window.ResizeObserver) {
        new ResizeObserver(() => this.sizeKeyLabels()).observe(this.els.keyboard);
      }
    }

    // ---- le champ : texte, curseur ----

    // Écrit tel quel par le rejeu des exemples (simulateur-demo.js). Le curseur
    // reste à la fin quand il y était, ce qui est le cas de toute frappe.
    get screenText() { return this._text; }
    set screenText(valeur) {
      const alaFin = this.cursor >= this._text.length;
      this._text = valeur;
      this.cursor = alaFin ? valeur.length : Math.min(this.cursor, valeur.length);
    }

    textBefore(n) {
      return this._text.slice(Math.max(0, this.cursor - n), this.cursor);
    }

    textAfter(n) {
      return this._text.slice(this.cursor, this.cursor + n);
    }

    /** commitText(texte, 1) : écrit au curseur et le pose après. */
    insertText(texte) {
      this._text = this._text.slice(0, this.cursor) + texte + this._text.slice(this.cursor);
      this.cursor += texte.length;
    }

    /** deleteSurroundingText(avant, apres). */
    deleteAround(avant, apres) {
      const debut = Math.max(0, this.cursor - avant);
      this._text = this._text.slice(0, debut) + this._text.slice(this.cursor + apres);
      this.cursor = debut;
    }

    /**
     * Déplace le curseur de `pas` caractères, négatif vers la gauche
     * (InputProcessor.moveCursorBy). On passe d'une paire de substituts à la
     * suivante d'un seul cran : un emoji est un caractère pour l'œil.
     */
    moveCursorBy(pas) {
      const texte = this._text;
      let c = this.cursor;
      for (let i = 0; i < Math.abs(pas); i++) {
        if (pas > 0) {
          if (c >= texte.length) break;
          const haut = texte.charCodeAt(c);
          c += haut >= 0xD800 && haut <= 0xDBFF && c + 1 < texte.length ? 2 : 1;
        } else {
          if (c <= 0) break;
          const bas = texte.charCodeAt(c - 1);
          c -= bas >= 0xDC00 && bas <= 0xDFFF && c - 2 >= 0 ? 2 : 1;
        }
      }
      if (c === this.cursor) return;
      this.cursor = c;
      this.renderScreen();
    }

    /**
     * Resynchronise le mot courant avec le texte réellement présent avant le
     * curseur (InputProcessor.syncWordWithCursor). `currentWord` n'est alimenté
     * que par les frappes : sans cette remise à niveau il divergerait du texte
     * dès que le curseur bouge autrement qu'en tapant — retour arrière qui
     * remonte dans un mot déjà validé, appui dans le texte, glissement sur
     * l'espace — et les suggestions travailleraient sur un préfixe périmé.
     */
    syncWordWithCursor() {
      const trouve = this.textBefore(64).match(/\p{L}+$/u);
      const mot = trouve ? trouve[0] : '';
      if (mot === this.currentWord) return;
      this.currentWord = mot;
      this.onWordChanged();
    }

    // Comme l'application, qui attend 120 ms (DELAI_SYNC_CURSEUR) : traverser
    // une phrase du doigt ne doit pas recalculer trente fois les suggestions,
    // la seule position qui compte est celle où le doigt s'arrête.
    planSync() {
      clearTimeout(this._syncTimer);
      this._syncTimer = setTimeout(() => this.syncWordWithCursor(), SYNC_CURSEUR_MS);
    }

    /** Un appui dans le texte y pose le curseur, comme dans n'importe quel champ. */
    placeCaretFromClick(e) {
      if (this.composingLength || this.dictationBase !== null) return;
      const index = this.offsetFromPoint(e.clientX, e.clientY);
      this.cursor = index === null ? this._text.length : index;
      this.renderScreen();
      clearTimeout(this._syncTimer);
      this.syncWordWithCursor();
    }

    offsetFromPoint(x, y) {
      let noeud = null;
      let decalage = 0;
      if (document.caretPositionFromPoint) {
        const pos = document.caretPositionFromPoint(x, y);
        if (pos) { noeud = pos.offsetNode; decalage = pos.offset; }
      } else if (document.caretRangeFromPoint) {
        const rng = document.caretRangeFromPoint(x, y);
        if (rng) { noeud = rng.startContainer; decalage = rng.startOffset; }
      }
      if (!noeud) return null;
      if (noeud === this._avant) return Math.min(decalage, this.cursor);
      if (noeud === this._apres) return this.cursor + Math.min(decalage, this._text.length - this.cursor);
      return null;
    }

    // ---- clavier physique (confort desktop, en plus du clavier tactile) ----

    bindPhysicalKeyboard() {
      document.addEventListener('keydown', (e) => {
        if (e.ctrlKey || e.metaKey || e.altKey) return; // laisser les raccourcis navigateur

        const target = e.target;
        if (target && (target.tagName === 'A' || target.tagName === 'BUTTON' || target.isContentEditable)) {
          return; // laisser Tab+Entrée/Espace activer nativement liens, boutons, touches virtuelles
        }

        if (e.key === 'Backspace') {
          e.preventDefault(); // sinon navigation arrière du navigateur hors champ éditable
          this.processKey('⌫');
          return;
        }
        if (e.key === 'Enter') {
          e.preventDefault();
          this.processKey('⏎');
          return;
        }
        if (e.key === ' ') {
          e.preventDefault(); // sinon défilement de la page
          this.processKey(' ');
          return;
        }
        // Les flèches font ce que fait le glissement sur la barre d'espace.
        if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
          e.preventDefault();
          this.moveCursorBy(e.key === 'ArrowLeft' ? -1 : 1);
          this.planSync();
          return;
        }
        if (e.key === 'Escape') {
          this.dismissAccentPopup();
          return;
        }
        // Tab complète avec la première suggestion, comme la touche de
        // complétion d'un terminal. Uniquement quand il y en a une, et jamais
        // sur Maj+Tab : sinon la touche cesserait de servir à naviguer dans la
        // page, et un visiteur au clavier s'y retrouverait piégé.
        if (e.key === 'Tab' && !e.shiftKey) {
          const premiere = this.els.rowLux.querySelector('.chip');
          if (premiere) {
            e.preventDefault();
            this.selectSuggestion(premiere.textContent);
          }
          return;
        }
        if (e.key.length === 1) {
          e.preventDefault();
          this.insertPhysicalChar(e.key);
        }
      });
    }

    // Comme handleCharacter(), mais sans reforcer la casse : le clavier
    // physique (Maj/Verr.Maj/touches mortes gérées par l'OS) fournit déjà
    // le bon caractère dans e.key.
    insertPhysicalChar(character) {
      if (this.dictationInterrupter && this.dictationInterrupter()) return;
      if (isWordString(character)) {
        this.currentWord += character;
        this.lastAutoCapitalization = null;
        this.onWordChanged();
      } else {
        this.finalizeCurrentWord();
      }
      this.insertText(character);
      this.renderScreen();
      this.handleAutoCapitalization();
      this.renderKeyboard();
    }

    // Retour tactile : l'application vibre à chaque touche par défaut
    // (KeyFeedback). Seulement pour un doigt — une souris n'a rien à sentir —
    // et là où le navigateur le permet ; un iframe d'un autre site le refuse
    // sans rien casser.
    buzz(ms) {
      if (!this._toucher || !navigator.vibrate) return;
      try { navigator.vibrate(ms || HAPTIC_MS); } catch (e) { /* refusé : sans suite */ }
    }

    // ---- helpers de touche ----

    hasAccents(key) {
      return Object.prototype.hasOwnProperty.call(ACCENT_MAP, key.toLowerCase()) ||
        Boolean(this.emojiData && this.emojiData.skinTones && this.emojiData.skinTones[key]);
    }

    /** Les variantes d'une touche : accents d'une lettre, ou tons de peau d'un emoji. */
    accentsFor(key) {
      return ACCENT_MAP[key.toLowerCase()] ||
        (this.emojiData && this.emojiData.skinTones && this.emojiData.skinTones[key]) || null;
    }

    cornerHints(key) {
      const k = key.toLowerCase();
      if (!Object.prototype.hasOwnProperty.call(ACCENT_MAP, k)) return [];
      return CORNER_HINTS[k] || ACCENT_MAP[k];
    }

    displayText(key) {
      if (key === ' ') return 'LuxKeyb™';
      if (key === '⇧' || key === '⌫' || key === '⏎') return key;
      if (key === '123') return this.isNumericMode ? 'ABC' : '123';
      if (key === 'ABC') return 'ABC';
      if (key === 'EMOJI') return '😀';
      const upper = this.isCapitalMode || this.isCapsLock;
      return upper ? key.toUpperCase() : key.toLowerCase();
    }

    /**
     * Fond d'une touche, aux trois couleurs du drapeau luxembourgeois
     * (KeyboardLayoutManager.keyBackground) : les lettres occupent le blanc,
     * le rouge marque ce qui agit, le bleu ciel accompagne la frappe.
     */
    keyClass(key) {
      if (key === '⇧') {
        return 'kb-shift' + (this.isCapitalMode || this.isCapsLock ? ' kb-shift-on' : '');
      }
      if (key === '⏎' || key === '123' || key === 'ABC' || key === 'EMOJI') return 'kb-action';
      if (key === ' ') return 'kb-space-key';
      if ([',', '.', "'"].includes(key)) return 'kb-punct';
      return 'kb-normal';
    }

    keyWeight(key) {
      if (key === ' ') return 4;
      // KeyboardLayoutManager.getKeyWeight() : 1,5 et non 1,25. C'est ce qui
      // pose la rangée 3 à exactement 10 unités (1,5 + 7 lettres + 1,5), donc à
      // la même largeur de touche que les rangées 1 et 2. Le 1,25 datait du
      // temps où l'apostrophe vivait en rangée 3 ; elle est en rangée 4
      // depuis, et la réduction laissait la rangée à 9,5 unités, ses lettres
      // 5 % plus larges que celles du dessus.
      if (key === '⇧' || key === '⌫') return 1.5;
      return 1;
    }

    // ---- rendu clavier ----

    /**
     * Reconstruit les touches, mais seulement quand ce qui se voit a changé :
     * mode, majuscule, catégorie d'emoji. Les reconstruire à chaque frappe
     * remplaçait la touche sous le doigt par une neuve, ce qui coupait net son
     * animation d'appui et, pour la barre d'espace, la capture du pointeur qui
     * porte le glissement.
     */
    renderKeyboard(force) {
      const signature = [
        this.isNumericMode, this.isEmojiMode, this.isCapitalMode, this.isCapsLock,
        this.emojiCategory, Boolean(this.emojiData), this.emojiLoading
      ].join('|');
      if (!force && signature === this._signature && this.els.keyboard.firstChild) return;
      this._signature = signature;

      this.els.keyboard.innerHTML = '';
      if (this.isEmojiMode) {
        this.els.keyboard.appendChild(this.createEmojiPanel());
        this.els.keyboard.appendChild(this.createRow(EMOJI_CONTROL_ROW));
      } else {
        const rows = this.isNumericMode ? NUMERIC_ROWS : ALPHA_ROWS;
        rows.forEach((rowKeys) => this.els.keyboard.appendChild(this.createRow(rowKeys)));
      }
      this.sizeKeyLabels();
    }

    createRow(rowKeys) {
      const rowEl = document.createElement('div');
      rowEl.className = 'kb-row';
      rowKeys.forEach((key) => rowEl.appendChild(this.createKey(key)));
      return rowEl;
    }

    /**
     * Taille de police d'un libellé : min(hauteur × ratio, largeur × ratio),
     * exactement createKeyButton(). La largeur d'une touche n'étant connue
     * qu'une fois la rangée disposée, elle se lit sur la touche rendue plutôt
     * que de refaire le partage des poids.
     */
    sizeKeyLabels() {
      this.els.keyboard.querySelectorAll('.kb-key').forEach((btn) => {
        const key = btn.dataset.key;
        if (key === undefined || ICONS[btn.dataset.icon]) return;
        const largeur = btn.getBoundingClientRect().width;
        if (!largeur) return;
        const ratioHauteur =
          key === ' ' ? SPACE_LABEL_TEXT_RATIO
          : (key === '123' || key === 'ABC') ? WIDE_LABEL_TEXT_RATIO
          : KEY_TEXT_HEIGHT_RATIO;
        const ratioLargeur = key === 'EMOJI' ? EMOJI_WIDTH_RATIO : LABEL_WIDTH_RATIO;
        btn.style.fontSize =
          Math.min(KEY_HEIGHT_PX * ratioHauteur, largeur * ratioLargeur).toFixed(1) + 'px';
      });
    }

    /** Icône d'une touche, ou null si elle porte un libellé texte. */
    iconFor(key) {
      if (key === '⌫') return 'backspace';
      if (key === '⏎') return 'enter';
      if (key !== '⇧') return null;
      if (this.isCapsLock) return 'shiftCaps';
      if (this.isCapitalMode) return 'shiftOn';
      return 'shiftOff';
    }

    createIcon(nom) {
      const icone = ICONS[nom];
      const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
      svg.setAttribute('viewBox', icone.viewBox);
      svg.setAttribute('aria-hidden', 'true');
      svg.style.padding = (KEY_HEIGHT_PX * icone.padRatio).toFixed(1) + 'px';
      svg.style.boxSizing = 'border-box';
      const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
      path.setAttribute('d', icone.d);
      if (icone.fillRule) path.setAttribute('fill-rule', icone.fillRule);
      if (icone.transform) path.setAttribute('transform', icone.transform);
      svg.appendChild(path);
      return svg;
    }

    createKey(key) {
      const wrap = document.createElement('div');
      wrap.className = 'kb-key-wrap';
      wrap.style.flexGrow = String(this.keyWeight(key));

      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'kb-key ' + this.keyClass(key);
      btn.dataset.key = key;

      const icone = this.iconFor(key);
      if (icone) {
        btn.dataset.icon = icone;
        btn.appendChild(this.createIcon(icone));
      } else {
        btn.textContent = this.displayText(key);
      }
      btn.setAttribute(
        'aria-label',
        key === ' ' ? 'Espace'
          : key === '⌫' ? 'Supprimer'
          : key === '⏎' ? 'Entrée'
          : key === '⇧' ? 'Majuscule'
          : key === 'EMOJI' ? 'Emojis'
          : key
      );

      if (key === ' ') this.bindSpaceKey(btn);
      else this.bindKey(btn, key);
      btn.addEventListener('contextmenu', (e) => e.preventDefault());

      wrap.appendChild(btn);

      // Aperçu des deux premières options d'appui long, en haut et en bas du
      // côté droit de la touche — wrapWithLongPressHints() n'en connaît pas
      // d'autre côté.
      const hints = this.cornerHints(key);
      if (hints.length) {
        wrap.appendChild(this.createHint(hints[0], 'top'));
        if (hints[1]) wrap.appendChild(this.createHint(hints[1], 'bottom'));
      }

      // 🌐 : dans l'application, un appui long sur l'espace ouvre le sélecteur de
      // claviers du système. Le geste n'a pas d'équivalent dans un navigateur,
      // l'indice reste donc purement visuel ici, avec une infobulle qui le dit.
      if (key === ' ') {
        const globe = document.createElement('span');
        globe.className = 'kb-space-hint';
        globe.textContent = '🌐';
        globe.title = "Dans l'application, un appui long ici ouvre le sélecteur de claviers.";
        wrap.appendChild(globe);
      }

      return wrap;
    }

    /**
     * Une touche ordinaire : appui court, ou appui long de 500 ms (accents
     * d'une lettre) — sauf Retour arrière, dont l'appui long efface par mots
     * (KreyolInputMethodServiceRefactored.onLongPress).
     *
     * Le caractère part au relâchement, comme un clic Android : glisser hors de
     * la touche avant de lever le doigt annule la frappe.
     */
    bindKey(btn, key) {
      let holdTimer = null;
      let longPressed = false;

      const arreter = () => {
        btn.classList.remove('kb-key-active');
        if (holdTimer) {
          clearTimeout(holdTimer);
          holdTimer = null;
        }
        this.stopWordDeletion();
      };
      const startHold = (ev) => {
        ev.preventDefault();
        this._toucher = ev.pointerType === 'touch';
        longPressed = false;
        btn.classList.add('kb-key-active');
        this.buzz();
        if (key === '⌫') {
          holdTimer = setTimeout(() => {
            longPressed = true;
            this.startWordDeletion();
          }, BACKSPACE_LONG_PRESS_MS);
        } else if (this.hasAccents(key)) {
          holdTimer = setTimeout(() => {
            longPressed = true;
            this.showAccentPopup(key, btn);
          }, ACCENT_LONG_PRESS_MS);
        }
      };
      const endHold = (ev) => {
        ev.preventDefault();
        const etait = longPressed;
        arreter();
        longPressed = false;
        if (!etait) this.processKey(key);
      };
      const cancelHold = () => {
        arreter();
        longPressed = false;
      };

      btn.addEventListener('pointerdown', startHold);
      btn.addEventListener('pointerup', endHold);
      btn.addEventListener('pointerleave', cancelHold);
      btn.addEventListener('pointercancel', cancelHold);
    }

    /**
     * La barre d'espace : appui court, ou glissement horizontal qui déplace le
     * curseur (KeyboardLayoutManager.setupSpaceLongPress, v14.0.0).
     *
     * Passé le seuil de glissement, le relâchement n'insère plus d'espace :
     * poser le curseur en traversant un mot ne doit pas laisser un espace
     * derrière soi. Le pointeur est capturé, et le geste garde donc le doigt
     * sur toute la largeur de la page plutôt que sur la seule barre. Le mot
     * courant n'est resynchronisé qu'une fois le doigt levé.
     *
     * L'appui long d'une seconde (sélecteur de claviers du système) n'a pas
     * d'équivalent dans un navigateur.
     */
    bindSpaceKey(btn) {
      let actif = false;
      let glisse = false;
      let ancre = 0;

      const fin = (annule) => {
        if (!actif) return;
        actif = false;
        btn.classList.remove('kb-key-active');
        if (glisse) this.planSync();
        else if (!annule) this.processKey(' ');
        glisse = false;
      };

      btn.addEventListener('pointerdown', (ev) => {
        ev.preventDefault();
        this._toucher = ev.pointerType === 'touch';
        actif = true;
        glisse = false;
        ancre = ev.clientX;
        try { btn.setPointerCapture(ev.pointerId); } catch (e) { /* sans capture : glissement borné à la touche */ }
        btn.classList.add('kb-key-active');
        this.buzz();
      });
      btn.addEventListener('pointermove', (ev) => {
        if (!actif) return;
        if (!glisse) {
          if (Math.abs(ev.clientX - ancre) > SPACE_SLOP_PX) {
            glisse = true;
            ancre = ev.clientX;
            btn.classList.remove('kb-key-active');
          }
          return;
        }
        // Troncature vers zéro des deux côtés : un demi-cran ne déplace rien,
        // ni à gauche ni à droite. Un arrondi au plus proche avancerait d'un
        // caractère à mi-course et reculerait au retour.
        const pas = Math.trunc((ev.clientX - ancre) / SPACE_CURSOR_STEP_PX);
        if (pas !== 0) {
          ancre += pas * SPACE_CURSOR_STEP_PX;
          this.buzz(TICK_MS);
          this.moveCursorBy(pas);
        }
      });
      btn.addEventListener('pointerup', (ev) => { ev.preventDefault(); fin(false); });
      btn.addEventListener('pointercancel', () => fin(true));
    }

    createHint(text, vPos) {
      const span = document.createElement('span');
      span.className = `kb-hint kb-hint-${vPos} kb-hint-end`;
      span.textContent = text;
      return span;
    }

    // ---- suppression par mots (appui long sur ⌫) ----

    // Première suppression immédiate, puis une toutes les 300 ms après 500 ms
    // d'attente (startWordDeletion) : effacer une ligne d'un doigt.
    startWordDeletion() {
      if (this.dictationInterrupter && this.dictationInterrupter()) return;
      if (this._suppression.debut !== null || this._suppression.repetition !== null) return;
      this.deleteWordBeforeCursor();
      this._suppression.debut = setTimeout(() => {
        this._suppression.debut = null;
        this._suppression.repetition = setInterval(() => this.deleteWordBeforeCursor(), 300);
      }, 500);
    }

    stopWordDeletion() {
      clearTimeout(this._suppression.debut);
      clearInterval(this._suppression.repetition);
      this._suppression.debut = null;
      this._suppression.repetition = null;
    }

    /** Le mot précédent, espaces de fin compris (deleteWordBeforeCursor). */
    deleteWordBeforeCursor() {
      const avant = this.textBefore(100);
      if (!avant) return;
      let i = avant.length - 1;
      let compte = 0;
      while (i >= 0 && /\s/.test(avant[i])) { compte++; i--; }
      while (i >= 0 && !/\s/.test(avant[i])) { compte++; i--; }
      if (compte === 0) return;
      this.deleteAround(compte, 0);
      this.lastAutoCapitalization = null;
      this.renderScreen();
      this.syncWordWithCursor();
    }

    // ---- panneau emoji (EmojiPickerView.kt) ----

    /** Les catégories, avec « Récents » en tête quand il y a de quoi la remplir. */
    figerCategories() {
      const categories = this.emojiData.categories;
      this.emojiCategories = this.recentEmojis.length
        ? [{ name: 'Récents', icon: '🕒', emojis: this.recentEmojis.slice() }].concat(categories)
        : categories;
    }

    createEmojiPanel() {
      const panel = document.createElement('div');
      panel.className = 'kb-emoji-panel';

      if (!this.emojiData) {
        this.loadEmojiData();
        const message = document.createElement('div');
        message.className = 'kb-emoji-message';
        message.textContent = this.emojiLoading ? 'Chargement des emojis…' : 'Emojis indisponibles.';
        panel.appendChild(message);
        return panel;
      }
      if (!this.emojiCategories) this.figerCategories();
      const categories = this.emojiCategories;
      if (this.emojiCategory >= categories.length) this.emojiCategory = 0;

      const tabs = document.createElement('div');
      tabs.className = 'kb-emoji-tabs';
      categories.forEach((categorie, index) => {
        const tab = document.createElement('button');
        tab.type = 'button';
        tab.className = 'kb-emoji-tab' + (index === this.emojiCategory ? ' is-active' : '');
        tab.textContent = categorie.icon;
        tab.title = categorie.name;
        tab.addEventListener('click', () => {
          this.emojiCategory = index;
          this.renderKeyboard();
        });
        tabs.appendChild(tab);
      });
      panel.appendChild(tabs);

      const grid = document.createElement('div');
      grid.className = 'kb-emoji-grid';
      categories[this.emojiCategory].emojis.forEach((emoji) => grid.appendChild(this.createEmojiCell(emoji)));
      this.bindEmojiSwipe(grid, categories.length);
      panel.appendChild(grid);

      return panel;
    }

    createEmojiCell(emoji) {
      const cell = document.createElement('button');
      cell.type = 'button';
      cell.className = 'kb-emoji-cell';
      cell.textContent = emoji;

      // Même délai que les touches à appui long du clavier : un emoji à ton de
      // peau ouvre ses cinq variantes (AccentHandler.emojiSkinTones).
      let timer = null;
      let longPressed = false;
      const arreter = () => {
        clearTimeout(timer);
        timer = null;
      };
      cell.addEventListener('pointerdown', (ev) => {
        this._toucher = ev.pointerType === 'touch';
        longPressed = false;
        if (this.hasAccents(emoji)) {
          timer = setTimeout(() => {
            longPressed = true;
            this.showAccentPopup(emoji, cell);
          }, ACCENT_LONG_PRESS_MS);
        }
      });
      cell.addEventListener('pointerup', arreter);
      cell.addEventListener('pointerleave', arreter);
      cell.addEventListener('pointercancel', arreter);
      cell.addEventListener('contextmenu', (e) => e.preventDefault());
      cell.addEventListener('click', () => {
        // Un balayage qui finit sur la cellule d'où il est parti, ou un appui
        // long qui vient d'ouvrir les tons, n'est pas une sélection.
        if (longPressed || this._balayage) {
          longPressed = false;
          return;
        }
        this.buzz();
        this.chooseEmoji(emoji);
      });
      return cell;
    }

    /**
     * Le balayage horizontal change de catégorie (ViewPager2 côté application) ;
     * le vertical défile dans la grille. Les deux gestes sont orthogonaux.
     */
    bindEmojiSwipe(grid, total) {
      let depart = null;
      grid.addEventListener('pointerdown', (ev) => {
        depart = { x: ev.clientX, y: ev.clientY, id: ev.pointerId };
        this._balayage = false;
      });
      grid.addEventListener('pointerup', (ev) => {
        if (!depart || depart.id !== ev.pointerId) return;
        const dx = ev.clientX - depart.x;
        const dy = ev.clientY - depart.y;
        depart = null;
        if (Math.abs(dx) < SWIPE_MIN_PX || Math.abs(dx) < Math.abs(dy) * 1.5) return;
        this._balayage = true;
        const suivante = this.emojiCategory + (dx < 0 ? 1 : -1);
        if (suivante < 0 || suivante >= total) return;
        this.emojiCategory = suivante;
        this.renderKeyboard();
      });
      grid.addEventListener('pointercancel', () => { depart = null; });
    }

    /** EmojiRecents.fusionner : en tête, sans doublon, trente au plus. */
    rememberEmoji(emoji) {
      this.recentEmojis = [emoji].concat(this.recentEmojis.filter((e) => e !== emoji))
        .slice(0, EMOJI_RECENTS_MAX);
    }

    chooseEmoji(emoji) {
      this.rememberEmoji(emoji);
      this.processKey(emoji);
    }

    loadEmojiData() {
      if (this.emojiLoading || this.emojiData) return;
      this.emojiLoading = true;
      // Mode par défaut, pas 'force-cache' : voir simulateur.html — une copie
      // figée survit au rechargement forcé et fait mentir la page.
      fetch('assets/emoji_data.json')
        .then((res) => {
          if (!res.ok) throw new Error('HTTP ' + res.status);
          return res.json();
        })
        .then((data) => {
          this.emojiData = data;
          this.emojiLoading = false;
          if (this.isEmojiMode) this.renderKeyboard();
        })
        .catch((err) => {
          this.emojiLoading = false;
          console.error(err);
          if (this.isEmojiMode) this.renderKeyboard(true);
        });
    }

    // ---- popup d'appui long ----

    showAccentPopup(baseKey, anchorEl) {
      this.dismissAccentPopup();
      const accents = this.accentsFor(baseKey);
      if (!accents) return;

      const popup = document.createElement('div');
      popup.className = 'accent-popup';

      const makeBtn = (ch, isBase) => {
        const b = document.createElement('button');
        b.type = 'button';
        b.className = 'accent-btn' + (isBase ? ' accent-base' : '');
        const upper = this.isCapitalMode || this.isCapsLock;
        b.textContent = upper ? ch.toUpperCase() : ch;
        b.addEventListener('pointerdown', (e) => e.preventDefault());
        b.addEventListener('click', () => this.selectAccent(ch));
        return b;
      };

      popup.appendChild(makeBtn(baseKey, true));
      accents.forEach((a) => popup.appendChild(makeBtn(a, false)));

      document.body.appendChild(popup);
      const rect = anchorEl.getBoundingClientRect();
      const pRect = popup.getBoundingClientRect();
      let left = rect.left + rect.width / 2 - pRect.width / 2;
      left = Math.max(8, Math.min(left, window.innerWidth - pRect.width - 8));
      const top = Math.max(8, rect.top - pRect.height - 10);
      popup.style.left = left + 'px';
      popup.style.top = top + 'px';

      this.currentPopup = popup;
      this._popupOutsideHandler = (e) => {
        if (!popup.contains(e.target)) this.dismissAccentPopup();
      };
      setTimeout(() => document.addEventListener('pointerdown', this._popupOutsideHandler), 0);
    }

    dismissAccentPopup() {
      if (this.currentPopup) {
        this.currentPopup.remove();
        this.currentPopup = null;
      }
      if (this._popupOutsideHandler) {
        document.removeEventListener('pointerdown', this._popupOutsideHandler);
        this._popupOutsideHandler = null;
      }
    }

    // Sélection d'un accent (ou de la touche de base depuis le popup), comme
    // onAccentSelected() côté Android : la variante est écrite au curseur ; si
    // c'est une lettre elle rejoint le mot en cours et les suggestions se
    // régénèrent, exactement comme pour une lettre tapée. Un emoji, lui, n'est
    // pas une lettre : il ne doit pas polluer le mot suivi (« 🥭ka »), il est
    // retenu dans les récents et clôt le mot.
    selectAccent(accent) {
      const upper = this.isCapitalMode || this.isCapsLock;
      const finalAccent = upper ? accent.toUpperCase() : accent;
      this.dismissAccentPopup();
      if (this.dictationInterrupter && this.dictationInterrupter()) return;
      this.insertText(finalAccent);
      if (isWordString(finalAccent)) {
        this.currentWord += finalAccent;
        this.onWordChanged();
      } else {
        this.rememberEmoji(finalAccent);
        this.finalizeCurrentWord();
      }
      this.renderScreen();
    }

    // ---- logique de saisie (InputProcessor.kt) ----

    processKey(key) {
      // Pendant la dictée, la frappe reprend la main : une lettre insérée au
      // milieu d'un texte en composition serait effacée par l'hypothèse
      // suivante, qui remplace la phrase entière. Le premier appui ferme donc
      // la dictée et n'écrit rien, comme le clavier coupe la dictée quand le
      // champ de saisie change.
      if (this.dictationInterrupter && this.dictationInterrupter()) return;
      switch (key) {
        case '⌫':
          this.handleBackspace();
          break;
        case '⏎':
          this.handleEnter();
          break;
        case '⇧':
          this.handleShift();
          break;
        case '123':
        case 'ABC':
          this.handleModeSwitch();
          break;
        case 'EMOJI':
          this.handleEmojiSwitch();
          break;
        case ' ':
          this.handleSpace();
          break;
        default:
          this.handleCharacter(key);
      }
      this.renderKeyboard();
    }

    handleCharacter(key) {
      const character = this.shouldCapitalize() ? key.toUpperCase() : key.toLowerCase();
      if (isWordString(character)) {
        this.currentWord += character;
        this.lastAutoCapitalization = null;
        this.onWordChanged();
      } else {
        this.finalizeCurrentWord();
      }
      this.insertText(character);
      this.renderScreen();
      this.handleAutoCapitalization();
    }

    /**
     * Combien d'unités UTF-16 retirer pour ôter un seul glyphe : un caractère
     * normal, un emoji hors plan de base (paire de substituts), ou un emoji et
     * son modificateur de ton de peau (deux points de code). Supprimer une
     * seule unité laisserait un demi-caractère orphelin, affiché comme un glyphe
     * cassé (InputProcessor.calculateBackspaceLength).
     */
    backspaceLength(avant) {
      if (!avant) return 1;
      const points = Array.from(avant);
      const dernier = points[points.length - 1];
      const ton = dernier.codePointAt(0) >= 0x1F3FB && dernier.codePointAt(0) <= 0x1F3FF;
      if (ton && points.length > 1) return dernier.length + points[points.length - 2].length;
      return dernier.length;
    }

    handleBackspace() {
      // Une majuscule imposée d'office se défait au premier Retour arrière.
      if (this.revertAutoCapitalization()) {
        this.renderScreen();
        return;
      }
      if (this.cursor > 0) this.deleteAround(this.backspaceLength(this.textBefore(4)), 0);
      if (this.currentWord.length) {
        this.currentWord = this.currentWord.slice(0, -1);
        this.onWordChanged();
      }
      this.renderScreen();
      // Remonter dans un mot déjà validé le rend de nouveau « courant ».
      this.syncWordWithCursor();
    }

    handleEnter() {
      this.finalizeCurrentWord();
      this.insertText('\n');
      this.renderScreen();
    }

    handleShift() {
      if (!this.isCapitalMode && !this.isCapsLock) {
        this.isCapitalMode = true;
        this.isCapsLock = false;
      } else if (this.isCapitalMode && !this.isCapsLock) {
        this.isCapitalMode = true;
        this.isCapsLock = true;
      } else {
        this.isCapitalMode = false;
        this.isCapsLock = false;
      }
    }

    /**
     * Bascule 123/ABC. Depuis le panneau emoji, « ABC » remonte ce même chemin :
     * on revient à l'alphabétique plutôt que de basculer le mode numérique, ce
     * qui ouvrirait le 123 au lieu des lettres (InputProcessor.handleModeSwitch).
     */
    handleModeSwitch() {
      if (this.isEmojiMode) {
        this.isEmojiMode = false;
        this.isNumericMode = false;
      } else {
        this.isNumericMode = !this.isNumericMode;
      }
    }

    // Le panneau est reconstruit à chaque ouverture : « Récents » s'y remet à
    // jour, et il s'ouvre sur la première catégorie.
    handleEmojiSwitch() {
      this.isEmojiMode = true;
      this.isNumericMode = false;
      this.emojiCategories = null;
      this.emojiCategory = 0;
    }

    // Réécrit le mot courant avec la majuscule que le contexte atteste, avant
    // qu'il ne soit clos par l'espace — comme InputProcessor.handleSpace(), qui
    // appelle applyContextualCapitalization() avant finalizeCurrentWord() : à ce
    // moment l'historique porte les mots précédents, pas celui qu'on valide.
    applyContextualCapitalization() {
      this.lastAutoCapitalization = null;
      const mot = this.currentWord;
      if (!mot) return;
      const corrige = this.engine.contextualCapitalization(mot);
      if (!corrige || corrige === mot) return;
      // Le curseur peut avoir été déplacé au milieu du mot : on ne réécrit que
      // si ce qui précède est bien le mot courant, sinon on abîmerait le texte
      // au lieu de le corriger.
      if (this.textBefore(mot.length) !== mot) return;
      this.deleteAround(mot.length, 0);
      this.insertText(corrige);
      this.currentWord = corrige;
      this.lastAutoCapitalization = { origine: mot, corrige };
      this.renderScreen();
    }

    /**
     * Défait la capitalisation qui vient d'être imposée, si le Retour arrière
     * suit immédiatement. L'espace est conservé : l'utilisateur voulait annuler
     * la majuscule, pas revenir en arrière dans sa phrase. Un second Retour
     * arrière se comporte normalement.
     */
    revertAutoCapitalization() {
      const dernier = this.lastAutoCapitalization;
      if (!dernier) return false;
      this.lastAutoCapitalization = null;
      const attendu = dernier.corrige + ' ';
      if (this.textBefore(attendu.length) !== attendu) return false;
      this.deleteAround(attendu.length, 0);
      this.insertText(dernier.origine + ' ');
      return true;
    }

    handleSpace() {
      this.applyContextualCapitalization();
      this.finalizeCurrentWord();
      this.insertText(' ');
      this.renderScreen();
      this.handleAutoCapitalization();
    }

    onWordChanged() {
      if (this.currentWord) {
        // Un mot repris en cours de frappe l'emporte sur les prédictions du mot
        // précédent, dont le minuteur de 100 ms pourrait sinon arriver après la
        // première lettre et recouvrir les propositions qu'elle vient de faire.
        clearTimeout(this._contextualTimer);
        const suggestions = this.engine.generateBilingualSuggestions(this.currentWord);
        this.updateSuggestions(suggestions, true);
      } else {
        this.updateSuggestions([], false);
      }
    }

    finalizeCurrentWord() {
      if (!this.currentWord) {
        this.updateSuggestions([], false);
        return;
      }
      this.engine.addWordToHistory(this.currentWord);
      this.currentWord = '';
      // La barre se vide tout de suite (onWordChanged("")), puis les prédictions
      // du mot suivant arrivent 100 ms plus tard.
      this.updateSuggestions([], false);
      clearTimeout(this._contextualTimer);
      this._contextualTimer = setTimeout(() => {
        const preds = this.engine.generateContextualSuggestions();
        this.updateSuggestions(
          preds.map((w) => ({ word: w, language: 'LUX' })),
          false
        );
      }, 100);
    }

    // processSuggestionSelection : le mot en cours est remplacé, et sa fin avec
    // lui quand le curseur est au milieu (« bon|jou » + « bonjou » ne doit pas
    // donner « bonjoujou »), puis une espace est posée.
    selectSuggestion(word) {
      if (this.currentWord) {
        const suite = this.textAfter(64).match(/^\p{L}*/u);
        this.deleteAround(this.currentWord.length, suite ? suite[0].length : 0);
      }
      this.insertText(word + ' ');
      this.currentWord = word;
      this.finalizeCurrentWord();
      this.renderScreen();
      this.handleAutoCapitalization();
      this.renderKeyboard();
    }

    shouldAutoCapitalize() {
      const text = this.textBefore(100);
      if (!text || !text.trim()) return true;
      let lastIdx = -1;
      for (let i = text.length - 1; i >= 0; i--) {
        if ('.!?'.includes(text[i])) {
          lastIdx = i;
          break;
        }
      }
      if (lastIdx !== -1) {
        const after = text.slice(lastIdx + 1);
        if (!after.trim()) return true;
      }
      return false;
    }

    shouldCapitalize() {
      return this.isCapsLock || this.isCapitalMode || this.shouldAutoCapitalize();
    }

    handleAutoCapitalization() {
      if (this.shouldAutoCapitalize()) {
        this.isCapitalMode = true;
      } else if (this.isCapitalMode && !this.isCapsLock) {
        this.isCapitalMode = false;
      }
    }

    // ---- rendu écran / suggestions ----

    renderScreen() {
      const el = this.els.screenText;
      el.textContent = '';
      const composing = Math.min(this.composingLength, this._text.length);
      if (composing) {
        // Le texte en composition est souligné et remplaçable en bloc, comme
        // celui que l'IME passe à setComposingText() : chaque passe de la
        // dictée rend une phrase entière qui annule et remplace la précédente.
        this._avant = null;
        this._apres = null;
        el.appendChild(document.createTextNode(this._text.slice(0, -composing)));
        const span = document.createElement('span');
        span.className = 'phone-composing';
        span.textContent = this._text.slice(-composing);
        el.appendChild(span);
        el.appendChild(this.els.caret);
      } else {
        // Deux nœuds de texte de part et d'autre du curseur : c'est ce qui
        // permet de retrouver, depuis un appui, la position où le poser.
        this._avant = document.createTextNode(this._text.slice(0, this.cursor));
        this._apres = document.createTextNode(this._text.slice(this.cursor));
        el.appendChild(this._avant);
        el.appendChild(this.els.caret);
        el.appendChild(this._apres);
      }
      this.els.placeholder.style.display = this._text ? 'none' : 'block';
      this.keepCaretVisible();
    }

    // Le champ défile : le curseur reste dans la fenêtre, où qu'il aille, et pas
    // seulement au bas du texte comme quand on ne faisait qu'écrire au bout.
    keepCaretVisible() {
      const zone = this.els.screen;
      const caret = this.els.caret;
      if (!zone || !caret) return;
      if (this.cursor >= this._text.length) {
        zone.scrollTop = zone.scrollHeight;
        return;
      }
      // Le bas de la fenêtre porte 36 px de réserve sous le texte, pour le
      // bouton « Effacer » : le curseur ne doit pas s'y glisser.
      const haut = caret.offsetTop;
      const bas = haut + caret.offsetHeight;
      const visibleBas = zone.scrollTop + zone.clientHeight - 36;
      if (haut < zone.scrollTop) zone.scrollTop = Math.max(0, haut - 8);
      else if (bas > visibleBas) zone.scrollTop = bas - zone.clientHeight + 36;
    }

    updateSuggestions(list, labeled) {
      const lux = list.filter((s) => s.language === 'LUX');
      const french = list.filter((s) => s.language === 'FRENCH');
      this.renderSuggRow(this.els.rowLux, lux, labeled ? 'LB' : null);
      this.renderSuggRow(this.els.rowFrench, french, labeled ? 'FR' : null);
      this.els.rowFrench.style.visibility = french.length ? 'visible' : 'hidden';
    }

    renderSuggRow(container, suggestions, label) {
      container.innerHTML = '';
      if (!suggestions.length) return;
      if (label) {
        const l = document.createElement('span');
        l.className = 'sugg-label';
        l.textContent = label;
        container.appendChild(l);
      }
      suggestions.forEach((s) => {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'chip chip-' + s.language.toLowerCase();
        btn.textContent = s.word;
        // Au clic et non au toucher, comme la puce Android : glisser hors d'une
        // puce avant de relâcher annule la sélection.
        btn.addEventListener('pointerdown', (ev) => { this._toucher = ev.pointerType === 'touch'; });
        btn.addEventListener('click', () => {
          this.buzz();
          this.selectSuggestion(s.word);
        });
        container.appendChild(btn);
      });
    }

    // ---- dictée (KreyolInputMethodServiceRefactored.dictationListener) ----

    /**
     * Pose le texte dicté en composition. Chaque hypothèse remplace la
     * précédente en bloc — jamais de recollage de fragments — et le texte déjà
     * tapé avant l'appui sur le micro n'est pas touché.
     */
    setDictationText(texte) {
      if (this.dictationBase === null) {
        this.finalizeCurrentWord();
        // La dictée s'écrit au bout du champ : le curseur y est ramené, la
        // composition n'ayant pas d'autre position que la fin.
        this.cursor = this._text.length;
        let base = this._text;
        // La dictée reprend une phrase entière : elle ne se colle pas au mot
        // précédent.
        if (base && !/\s$/.test(base)) base += ' ';
        this.dictationBase = base;
      }
      this.screenText = this.dictationBase + texte;
      this.cursor = this._text.length;
      this.composingLength = texte.length;
      this.renderScreen();
    }

    /**
     * Fige le texte dicté, comme finishComposingText().
     *
     * Les mots entrent ensuite dans l'historique de prédiction comme s'ils
     * avaient été tapés — **écart délibéré avec l'application**, dont la dictée
     * passe par setComposingText() sans traverser le suivi de mots
     * d'InputProcessor : après une dictée, le clavier propose donc encore la
     * suite du dernier mot *tapé*, qui peut dater. Enchaîner sur ce qui vient
     * d'être dit est ce que l'utilisateur attend, et c'est sans risque ici : le
     * simulateur n'a pas de compteur d'usage à alimenter, seulement un
     * historique de cinq mots gardé en mémoire.
     */
    finishDictation(texte) {
      if (texte) this.setDictationText(texte);
      const dicte = this.composingLength ? this._text.slice(-this.composingLength) : '';
      this.composingLength = 0;
      this.dictationBase = null;
      this.currentWord = '';
      dicte.split(/[^\p{L}\p{N}'’-]+/u)
        .filter(Boolean)
        .forEach((mot) => this.engine.addWordToHistory(mot));
      this.renderScreen();
      this.handleAutoCapitalization();
      this.renderKeyboard();
      if (dicte) {
        const preds = this.engine.generateContextualSuggestions();
        this.updateSuggestions(preds.map((w) => ({ word: w, language: 'LUX' })), false);
      }
    }

    /** Abandonne la dictée et le texte en composition avec elle. */
    cancelDictation() {
      if (this.dictationBase === null) return;
      this.screenText = this.dictationBase;
      this.cursor = this._text.length;
      this.composingLength = 0;
      this.dictationBase = null;
      this.renderScreen();
    }

    reset() {
      this.composingLength = 0;
      this.dictationBase = null;
      this._text = '';
      this.cursor = 0;
      this.currentWord = '';
      this.isCapitalMode = false;
      this.isCapsLock = false;
      this.isNumericMode = false;
      this.isEmojiMode = false;
      this.lastAutoCapitalization = null;
      this.emojiCategories = null;
      this.emojiCategory = 0;
      this.stopWordDeletion();
      clearTimeout(this._syncTimer);
      this.engine.clearHistory();
      this.dismissAccentPopup();
      this.updateSuggestions([], false);
      this.renderScreen();
      this.renderKeyboard();
    }
  }

  window.KreyolKeyboardSimulator = KeyboardSimulator;
})();
