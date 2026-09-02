/*
 * Dictée du simulateur : le micro de l'application, posé sur le clavier de la
 * page. Port de la partie dictée de KreyolInputMethodServiceRefactored —
 * bouton micro au bord droit de la rangée de suggestions, bandeau qui prend
 * leur place pendant la dictée, témoins posés dans le champ de saisie —
 * au-dessus de `luxasr-client.js`, qui porte le protocole.
 *
 * **La voix quitte l'appareil.** C'est le service LuxASR de l'Université du
 * Luxembourg qui transcrit, et non un modèle embarqué comme dans la version
 * publiée du clavier. La dictée n'est donc pas branchée par défaut : elle
 * demande `?asr=1` dans l'adresse, et un consentement explicite avant que le
 * micro ne s'ouvre. Cf. `docs/labs-luxasr.html`, qui la présente.
 *
 * **Rien ne s'affiche pendant qu'on parle**, parce que l'énoncé part d'un seul
 * bloc à la fin : c'est ce que fait l'API par lots, et c'est ce qui lui vaut
 * onze points et demi de justesse sur le flux. Le champ n'est pas laissé vide
 * pour autant — les deux témoins ci-dessous l'occupent, aux deux temps de la
 * dictée.
 */
(function () {
  'use strict';

  var BANDEAU = {
    LOADING: '🌐 LuxASR verbannen…',
    LISTENING: '🌐 LuxASR · Schwätzt…',
    FINALIZING: '🌐 LuxASR · Transkriptioun…'
  };

  /** Amplitude du battement du micro (MIC_LEVEL_SCALE côté Android). */
  var MIC_LEVEL_SCALE = 0.18;

  /**
   * Le chronométrage de la passe finale reste affiché quelques secondes après
   * la fin de la dictée : sans ce délai, le bandeau disparaît dans le même
   * souffle que l'arrivée du texte et le chiffre n'est jamais lisible.
   * (holdFinalTiming(), FINAL_TIMING_HOLD_MS.)
   */
  var FINAL_TIMING_HOLD_MS = 4000;

  /** Un message d'échec s'efface tout seul, comme le Toast de l'application. */
  var MESSAGE_MS = 3500;

  /** Période d'une image du tracé de niveau affiché pendant la parole. */
  var METER_PERIOD_MS = 110;

  /** Largeur du tracé, en barres. */
  var METER_BARS = 6;

  /** Symbole qui ouvre le tracé, pour qu'on sache de quoi il parle. */
  var MIC_GLYPH = '🎤';

  /** Période d'une image de l'indicateur de transcription. */
  var SPINNER_PERIOD_MS = 140;

  /**
   * Les barres partielles (U+2581–2587) et les quarts de cercle (U+25D0–25D3)
   * appartiennent à des blocs Unicode que rien n'oblige une police système à
   * couvrir. Un caractère manquant afficherait un rectangle vide au milieu du
   * texte de l'utilisateur, ce qui est pire que pas d'animation du tout : on
   * vérifie donc, et on retombe sur des points et des barres ASCII.
   *
   * `Paint.hasGlyph()` n'existe pas ici ; la mesure sur canevas en tient lieu —
   * un glyphe absent est rendu par le rectangle de substitution, qui a la
   * largeur de U+FFFF.
   */
  function glyphesRendus(liste) {
    try {
      var c = document.createElement('canvas').getContext('2d');
      if (!c) return false;
      c.font = '16px sans-serif';
      var absent = c.measureText('￿').width;
      return liste.every(function (g) {
        var l = c.measureText(g).width;
        return l > 0 && Math.abs(l - absent) > 0.01;
      });
    } catch (e) { return false; }
  }

  var BARRES = ['▁', '▂', '▃', '▄', '▅', '▆', '▇'];
  var CERCLES = ['◐', '◓', '◑', '◒'];
  var METER_GLYPHS = glyphesRendus(BARRES) ? BARRES : ['.', '.', ':', ':', '|', '|', '|'];
  var SPINNER_FRAMES = glyphesRendus(CERCLES) ? CERCLES : ['·', '··', '···', '··'];

  function id(x) { return document.getElementById(x); }

  function brancherDictee(sim) {
    if (new URLSearchParams(location.search).get('asr') !== '1') return;

    var els = {
      avis: id('asr-avis'), activer: id('asr-activer'), note: id('asr-note'),
      micro: id('mic-btn'), bandeau: id('sugg-banner'), rangees: id('sugg-stack')
    };
    if (!els.micro || !els.avis) return;

    els.avis.hidden = false;

    if (!window.LuxAsrClient || !window.LuxAsrClient.disponible()) {
      els.activer.disabled = true;
      els.activer.textContent = '🚫 Navigateur incompatible';
      els.note.textContent = 'Ce navigateur ne donne pas accès au micro : il faut ' +
        'une page en HTTPS et un navigateur récent.';
      els.note.hidden = false;
      return;
    }

    var chronoPasse = '', minuteurBandeau = null;

    /** Le bandeau prend la place des suggestions ; à null, elles reviennent. */
    function afficherBandeau(texte) {
      clearTimeout(minuteurBandeau);
      var visible = texte !== null;
      els.bandeau.hidden = !visible;
      // Les rangées gardent leur place : les masquer en display ferait sauter
      // le clavier d'un demi-centimètre à chaque appui sur le micro.
      els.rangees.style.visibility = visible ? 'hidden' : 'visible';
      if (visible) els.bandeau.textContent = texte;
    }

    function bandeauEtat(etat) {
      var base = BANDEAU[etat];
      if (!base) return;
      afficherBandeau(chronoPasse ? base + '  ' + chronoPasse : base);
    }

    /** Message éphémère à la place du bandeau — l'équivalent d'un Toast. */
    function message(texte) {
      afficherBandeau(texte);
      minuteurBandeau = setTimeout(function () { afficherBandeau(null); }, MESSAGE_MS);
    }

    /**
     * Le micro n'a que deux états visuels, et ils doivent se distinguer sans
     * couleur : l'opacité change en même temps que la teinte, pour rester
     * lisible en cas de daltonisme comme sur un écran délavé au soleil.
     * L'anneau qui tourne pendant l'écoute est accroché à `.mic-on` en CSS.
     */
    function teinte(ecoute) {
      els.micro.classList.toggle('mic-on', ecoute);
      els.micro.setAttribute('aria-pressed', String(ecoute));
      if (!ecoute) els.micro.style.transform = 'scale(1)';
    }

    // ---- témoins posés dans le champ de saisie ----------------------------
    //
    // Les deux occupent le même créneau et la même boucle : le tracé de niveau
    // pendant qu'on parle, le cercle pendant que ça transcrit. Ils passent par
    // setDictationText(), donc par un texte de composition — souligné,
    // transitoire, remplacé d'un bloc par la transcription. Rien n'est jamais
    // figé dans le champ.

    var boucle = null, niveauCourant = 0, niveauxRecents = [], image = 0;
    var dernierPartiel = '';

    function arreterBoucle() {
      if (boucle) { clearInterval(boucle); boucle = null; }
    }

    /**
     * Un micro suivi de six barres qui défilent, pendant la parole.
     *
     * Ce témoin-ci n'est pas décoratif : les barres suivent l'énergie
     * réellement captée, donc une voix trop lointaine, une main sur le micro ou
     * un autre onglet qui l'a réquisitionné se voient sur-le-champ, à l'endroit
     * même où l'on regarde.
     *
     * Cadencé à METER_PERIOD_MS et non aux ~6 blocs par seconde de la capture :
     * chaque image réécrit et remesure la ligne de texte du téléphone, et
     * personne ne lit un VU-mètre à cette vitesse.
     */
    function demarrerTrace() {
      arreterBoucle();
      niveauxRecents = [];
      boucle = setInterval(function () {
        niveauxRecents.push(niveauCourant);
        while (niveauxRecents.length > METER_BARS) niveauxRecents.shift();
        var trace = '';
        var manquantes = METER_BARS - niveauxRecents.length;
        while (manquantes-- > 0) trace += METER_GLYPHS[0];
        niveauxRecents.forEach(function (n) {
          var i = Math.round(Math.max(0, Math.min(1, n)) * (METER_GLYPHS.length - 1));
          trace += METER_GLYPHS[i];
        });
        sim.setDictationText(MIC_GLYPH + ' ' + trace);
      }, METER_PERIOD_MS);
    }

    /**
     * Un petit cercle qui tourne, pendant que la transcription se calcule.
     *
     * Il existe parce que l'API par lots ne rend rien avant la fin : entre
     * l'arrêt du micro et le texte il s'écoule une seconde et trois dixièmes en
     * médiane, pendant lesquelles le champ resterait vide. Sans repère,
     * l'utilisateur croit que son appui n'a pas été pris.
     *
     * Quand des hypothèses ont déjà été affichées — c'est le cas du flux, pas
     * de l'API — le cercle se place **après** elles, pour dire « ce n'est pas
     * fini » plutôt que d'effacer ce que l'utilisateur a vu se construire.
     */
    function demarrerCercle() {
      arreterBoucle();
      image = 0;
      boucle = setInterval(function () {
        var prefixe = dernierPartiel ? dernierPartiel + ' ' : '';
        sim.setDictationText(prefixe + SPINNER_FRAMES[image % SPINNER_FRAMES.length]);
        image++;
      }, SPINNER_PERIOD_MS);
    }

    var client = window.LuxAsrClient({
      onEtat: function (etat) {
        teinte(etat === 'LISTENING');
        if (etat === 'LISTENING') demarrerTrace();
        else if (etat === 'FINALIZING') demarrerCercle();
        else arreterBoucle();

        if (etat === 'IDLE') {
          // Le chiffre de la passe finale reste lisible un moment, puis les
          // suggestions reprennent la barre.
          if (chronoPasse) {
            afficherBandeau('✅ ' + chronoPasse);
            minuteurBandeau = setTimeout(function () {
              chronoPasse = '';
              afficherBandeau(null);
            }, FINAL_TIMING_HOLD_MS);
          } else {
            afficherBandeau(null);
          }
        } else {
          bandeauEtat(etat);
        }
      },

      onNiveau: function (v) {
        niveauCourant = v;
        els.micro.style.transform = 'scale(' + (1 + MIC_LEVEL_SCALE * v).toFixed(3) + ')';
      },

      onPasse: function (secondes, ms) {
        chronoPasse = secondes.toFixed(1) + ' s → ' + ms + ' ms';
        if (!els.bandeau.hidden && client.occupe) bandeauEtat(client.etat);
      },

      onPartiel: function (texte) {
        arreterBoucle();
        dernierPartiel = texte;
        sim.setDictationText(texte);
      },

      onFinal: function (texte) {
        arreterBoucle();
        var garde = texte || dernierPartiel;
        dernierPartiel = '';
        // Rien à valider et rien à garder : le témoin était le seul contenu du
        // champ, il faut l'effacer plutôt que de le figer.
        if (garde) sim.finishDictation(garde);
        else sim.cancelDictation();
      },

      onErreur: function (quoi, detail) {
        arreterBoucle();
        dernierPartiel = '';
        sim.cancelDictation();
        chronoPasse = '';
        teinte(false);
        message(quoi === 'MIC'
          ? (detail === 'NotAllowedError' || detail === 'SecurityError'
              ? 'Dictée impossible sans accès au micro'
              : 'Micro indisponible')
          // Un échec de connexion n'est pas une incapacité de l'appareil :
          // confondre les deux fait accuser le téléphone à tort.
          : 'Service LuxASR injoignable');
      }
    });

    // Une frappe pendant la dictée la termine, sans écrire la touche : cf.
    // KeyboardSimulator.processKey().
    sim.dictationInterrupter = function () {
      if (!client.occupe) return false;
      client.stop();
      return true;
    };

    els.micro.addEventListener('click', function () {
      // Second appui : l'utilisateur a fini de parler, on fige. Pendant la
      // finalisation, l'appui est ignoré plutôt que d'ouvrir une seconde
      // dictée dont le texte se mélangerait à la première.
      if (client.occupe) { client.stop(); return; }
      chronoPasse = '';
      client.start();
    });

    els.activer.addEventListener('click', function () {
      els.avis.classList.add('arme');
      els.activer.hidden = true;
      els.note.hidden = false;
      els.micro.hidden = false;
      client.start();   // le clic est le geste utilisateur qu'exigent micro et audio
    });

    // Quitter la page coupe la capture et la connexion, sans compter sur le
    // ramasse-miettes.
    window.addEventListener('pagehide', function () {
      arreterBoucle();
      client.cancel();
    });
  }

  window.brancherDictee = brancherDictee;
})();
