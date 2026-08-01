/*
 * Interface du simulateur de clavier Klavyé Kréyòl Karukera.
 * Port du comportement de InputProcessor.kt / KeyboardLayoutManager.kt /
 * AccentHandler.kt / KreyolInputMethodServiceRefactored.kt (android_keyboard/),
 * réutilisant le moteur de suggestions dans simulateur-engine.js.
 */
(function () {
  'use strict';

  const ALPHA_ROWS = [
    ['a', 'z', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'ò', 'p'],
    ['q', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'm'],
    ['⇧', 'w', 'x', 'c', 'v', 'b', 'n', '⌫'],
    ['123', ',', 'é', '-', ' ', 'è', '.', "'", '⏎']
  ];

  const NUMERIC_ROWS = [
    ['1', '2', '3', '4', '5', '6', '7', '8', '9', '0'],
    ['-', '/', ':', ';', '(', ')', '€', '&', '@', '"'],
    ['=', '.', ',', '?', '!', "'", '+', '*', '⌫'],
    ['ABC', ' ', '⏎']
  ];

  // AccentHandler.kt accentMap (v8.7.3)
  const ACCENT_MAP = {
    a: ['à', 'â'],
    e: ['é', 'è', 'ê'],
    i: ['î', 'ï'],
    o: ['ò', 'ô', 'ó', 'œ'],
    u: ['ù', 'û'],
    n: ['ng', 'ny'],
    c: ['ç', 'ch'],
    d: ['dj'],
    g: ['gn', 'gy'],
    t: ['tj'],
    ',': [';', ':'],
    '.': ['!', '?', '…'],
    "'": ['"', '«', '»']
  };
  const CORNER_HINTS = { e: ['è', 'é'], o: ['ò', 'ó'] };
  const CORNER_LEFT = new Set(['o']);
  const DEDICATED_ACCENTED_KEYS = new Set(['à', 'è', 'ò', 'é', 'ù', 'ì', 'ç']);
  const LETTER_RE = /^[a-zA-Zàáâãäåèéêëìíîïòóôõöøùúûüýÿñçĉĝĥĵŝŭ]$/;

  class KeyboardSimulator {
    constructor(engine, els) {
      this.engine = engine;
      this.els = els;

      this.screenText = '';
      this.currentWord = '';
      this.isCapitalMode = false;
      this.isCapsLock = false;
      this.isNumericMode = false;

      this.currentPopup = null;
      this._popupOutsideHandler = null;
      this._contextualTimer = null;

      this.els.caret = document.createElement('span');
      this.els.caret.className = 'phone-caret';

      this.renderKeyboard();
      this.renderScreen();
      this.updateSuggestions([], false);

      this.els.resetBtn?.addEventListener('click', () => this.reset());
    }

    // ---- helpers de touche ----

    hasAccents(key) {
      return Object.prototype.hasOwnProperty.call(ACCENT_MAP, key.toLowerCase());
    }

    cornerHints(key) {
      const k = key.toLowerCase();
      if (!this.hasAccents(k)) return [];
      return CORNER_HINTS[k] || ACCENT_MAP[k];
    }

    displayText(key) {
      if (key === ' ') return 'Potomitan™';
      if (key === '⇧' || key === '⌫' || key === '⏎') return key;
      if (key === '123') return this.isNumericMode ? 'ABC' : '123';
      if (key === 'ABC') return 'ABC';
      const upper = this.isCapitalMode || this.isCapsLock;
      if (DEDICATED_ACCENTED_KEYS.has(key)) return upper ? key.toUpperCase() : key;
      return upper ? key.toUpperCase() : key.toLowerCase();
    }

    keyClass(key) {
      if (key === '⇧') {
        if (this.isCapsLock) return 'kb-shift kb-shift-caps';
        if (this.isCapitalMode) return 'kb-shift kb-shift-on';
        return 'kb-shift';
      }
      if (key === '⌫') return 'kb-backspace';
      if (key === '⏎') return 'kb-enter';
      if (key === '123' || key === 'ABC') return 'kb-mode';
      if (key === ' ') return 'kb-space-key';
      if ([',', '.', "'", '-'].includes(key)) return 'kb-punct';
      return 'kb-normal';
    }

    keyWeight(key) {
      if (key === ' ') return 4;
      if (key === '⇧' || key === '⌫') return 1.5;
      return 1;
    }

    // ---- rendu clavier ----

    renderKeyboard() {
      const rows = this.isNumericMode ? NUMERIC_ROWS : ALPHA_ROWS;
      this.els.keyboard.innerHTML = '';
      rows.forEach((rowKeys) => {
        const rowEl = document.createElement('div');
        rowEl.className = 'kb-row';
        rowKeys.forEach((key) => rowEl.appendChild(this.createKey(key)));
        this.els.keyboard.appendChild(rowEl);
      });
    }

    createKey(key) {
      const wrap = document.createElement('div');
      wrap.className = 'kb-key-wrap';
      wrap.style.flexGrow = String(this.keyWeight(key));

      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'kb-key ' + this.keyClass(key);
      btn.textContent = this.displayText(key);
      btn.setAttribute('aria-label', key === ' ' ? 'Espace' : key);

      let holdTimer = null;
      let longPressed = false;

      const startHold = (ev) => {
        ev.preventDefault();
        longPressed = false;
        btn.classList.add('kb-key-active');
        if (this.hasAccents(key)) {
          holdTimer = setTimeout(() => {
            longPressed = true;
            this.showAccentPopup(key, btn);
          }, 500);
        }
      };
      const endHold = (ev) => {
        ev.preventDefault();
        btn.classList.remove('kb-key-active');
        if (holdTimer) {
          clearTimeout(holdTimer);
          holdTimer = null;
        }
        if (!longPressed) {
          this.processKey(key);
        }
        longPressed = false;
      };
      const cancelHold = () => {
        btn.classList.remove('kb-key-active');
        if (holdTimer) {
          clearTimeout(holdTimer);
          holdTimer = null;
        }
        longPressed = false;
      };

      btn.addEventListener('pointerdown', startHold);
      btn.addEventListener('pointerup', endHold);
      btn.addEventListener('pointerleave', cancelHold);
      btn.addEventListener('pointercancel', cancelHold);
      btn.addEventListener('contextmenu', (e) => e.preventDefault());

      wrap.appendChild(btn);

      const hints = this.cornerHints(key);
      if (hints.length) {
        const side = CORNER_LEFT.has(key.toLowerCase()) ? 'start' : 'end';
        wrap.appendChild(this.createHint(hints[0], 'top', side));
        if (hints[1]) wrap.appendChild(this.createHint(hints[1], 'bottom', side));
      }

      return wrap;
    }

    createHint(text, vPos, side) {
      const span = document.createElement('span');
      span.className = `kb-hint kb-hint-${vPos} kb-hint-${side}`;
      span.textContent = text;
      return span;
    }

    // ---- popup d'accents ----

    showAccentPopup(baseKey, anchorEl) {
      this.dismissAccentPopup();
      const accents = ACCENT_MAP[baseKey.toLowerCase()];
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

    // Sélection d'un accent (ou de la touche de base depuis le popup) : ajoutée
    // directement, sans régénérer les suggestions — comme onAccentSelected()
    // côté Android, qui met à jour le mot courant "silencieusement".
    selectAccent(accent) {
      const upper = this.isCapitalMode || this.isCapsLock;
      const finalAccent = upper ? accent.toUpperCase() : accent;
      this.screenText += finalAccent;
      this.currentWord += finalAccent;
      this.renderScreen();
      this.dismissAccentPopup();
    }

    // ---- logique de saisie (InputProcessor.kt) ----

    processKey(key) {
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
      if (LETTER_RE.test(character)) {
        this.currentWord += character;
        this.onWordChanged();
      } else {
        this.finalizeCurrentWord();
      }
      this.screenText += character;
      this.renderScreen();
      this.handleAutoCapitalization();
    }

    handleBackspace() {
      if (!this.screenText.length) return;
      this.screenText = this.screenText.slice(0, -1);
      if (this.currentWord.length) {
        this.currentWord = this.currentWord.slice(0, -1);
        this.onWordChanged();
      }
      this.renderScreen();
    }

    handleEnter() {
      this.finalizeCurrentWord();
      this.screenText += '\n';
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

    handleModeSwitch() {
      this.isNumericMode = !this.isNumericMode;
    }

    handleSpace() {
      this.finalizeCurrentWord();
      this.screenText += ' ';
      this.renderScreen();
      this.handleAutoCapitalization();
    }

    onWordChanged() {
      if (this.currentWord) {
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
      clearTimeout(this._contextualTimer);
      this._contextualTimer = setTimeout(() => {
        const preds = this.engine.generateContextualSuggestions();
        this.updateSuggestions(
          preds.map((w) => ({ word: w, language: 'KREYOL' })),
          false
        );
      }, 100);
    }

    selectSuggestion(word) {
      if (this.currentWord) {
        this.screenText = this.screenText.slice(0, -this.currentWord.length);
      }
      this.screenText += word + ' ';
      this.currentWord = word;
      this.finalizeCurrentWord();
      this.renderScreen();
      this.handleAutoCapitalization();
      this.renderKeyboard();
    }

    shouldAutoCapitalize() {
      const text = this.screenText;
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
      this.els.screenText.textContent = this.screenText;
      this.els.screenText.appendChild(this.els.caret);
      this.els.placeholder.style.display = this.screenText ? 'none' : 'block';
      this.els.screen.scrollTop = this.els.screen.scrollHeight;
    }

    updateSuggestions(list, labeled) {
      const kreyol = list.filter((s) => s.language === 'KREYOL');
      const french = list.filter((s) => s.language === 'FRENCH');
      this.renderSuggRow(this.els.rowKreyol, kreyol, labeled ? 'KR' : null);
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
        btn.addEventListener('click', () => this.selectSuggestion(s.word));
        container.appendChild(btn);
      });
    }

    reset() {
      this.screenText = '';
      this.currentWord = '';
      this.isCapitalMode = false;
      this.isCapsLock = false;
      this.isNumericMode = false;
      this.engine.clearHistory();
      this.dismissAccentPopup();
      this.updateSuggestions([], false);
      this.renderScreen();
      this.renderKeyboard();
    }
  }

  window.KreyolKeyboardSimulator = KeyboardSimulator;
})();
