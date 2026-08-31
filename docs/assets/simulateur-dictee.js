/*
 * Dictée du simulateur : le micro de l'application, posé sur le clavier de la
 * page. Port de la partie dictée de KreyolInputMethodServiceRefactored —
 * bouton micro au bord droit de la rangée de suggestions, bandeau qui prend
 * leur place pendant la dictée, texte en composition souligné qui se réécrit à
 * chaque hypothèse — au-dessus de `luxasr-client.js`, qui porte le protocole.
 *
 * **La voix quitte l'appareil.** C'est le service LuxASR de l'Université du
 * Luxembourg qui transcrit, et non un modèle embarqué comme dans la version
 * publiée du clavier. La dictée n'est donc pas branchée par défaut : elle
 * demande `?asr=1` dans l'adresse, et un consentement explicite avant que le
 * micro ne s'ouvre. Cf. `docs/labs-luxasr.html`, qui la présente.
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
     */
    function teinte(ecoute) {
      els.micro.classList.toggle('mic-on', ecoute);
      els.micro.setAttribute('aria-pressed', String(ecoute));
      if (!ecoute) els.micro.style.transform = 'scale(1)';
    }

    var client = window.LuxAsrClient({
      onEtat: function (etat) {
        teinte(etat === 'LISTENING');
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
        els.micro.style.transform = 'scale(' + (1 + MIC_LEVEL_SCALE * v).toFixed(3) + ')';
      },

      onPasse: function (secondes, ms) {
        chronoPasse = secondes.toFixed(1) + ' s → ' + ms + ' ms';
        if (!els.bandeau.hidden && client.actif) bandeauEtat(client.etat);
      },

      onPartiel: function (texte) { sim.setDictationText(texte); },

      onFinal: function (texte) { sim.finishDictation(texte); },

      onErreur: function (quoi, detail) {
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
    window.addEventListener('pagehide', function () { client.cancel(); });
  }

  window.brancherDictee = brancherDictee;
})();
