/*
 * Démonstration de dictée de la page labs-luxasr : le micro, le bandeau et le
 * texte, posés sur `luxasr-client.js` qui fait tout le protocole.
 *
 * Volontairement sans clavier : cette page-ci répond à « à quoi ressemble
 * LuxASR ». Le clavier complet avec sa dictée est sur `simulateur.html?asr=1`.
 */
(function () {
  'use strict';

  var BANDEAU = {
    LOADING: '🌐 LuxASR verbannen…',
    LISTENING: '🌐 LuxASR · Schwätzt…',
    FINALIZING: '🌐 LuxASR · Transkriptioun…',
    IDLE: 'Prêt, touchez le micro et parlez en luxembourgeois'
  };

  var ui = {}, client = null, aDuTexte = false;

  function setBandeau(etat) {
    ui.bandeau.textContent = BANDEAU[etat] || BANDEAU.IDLE;
    ui.bandeau.classList.toggle('vif', etat !== 'IDLE');
    ui.micro.classList.toggle('actif', etat === 'LISTENING');
    ui.micro.classList.toggle('attente', etat === 'LOADING' || etat === 'FINALIZING');
    var actif = etat !== 'IDLE';
    ui.micro.setAttribute('aria-pressed', String(actif));
    ui.micro.textContent = actif ? '⏹' : '🎙️';
    ui.micro.setAttribute('aria-label', actif ? 'Arrêter la dictée' : 'Démarrer la dictée');
    if (!actif) niveau(0);
  }

  function niveau(v) { ui.micro.style.setProperty('--niveau', v.toFixed(3)); }

  function afficher(texte, partiel) {
    aDuTexte = !!texte;
    ui.texte.textContent = texte || 'Le texte reconnu s’affichera ici.';
    ui.texte.classList.toggle('partiel', !!partiel && !!texte);
    ui.texte.classList.toggle('vide', !texte);
    ui.effacer.hidden = !texte;
  }

  function chrono(t) { ui.chrono.textContent = t; }
  function erreur(t) { ui.erreur.textContent = t; ui.erreur.hidden = !t; }

  var ecouteur = {
    onEtat: setBandeau,
    onNiveau: niveau,
    onPartiel: function (texte) { afficher(texte, true); },
    onPasse: function (secondes, ms) { chrono(secondes.toFixed(1) + ' s → ' + ms + ' ms'); },
    onFinal: function (texte, duree) {
      afficher(texte, false);
      chrono(texte ? '✅ ' + duree.toFixed(1) + ' s' : '');
      if (!texte) {
        erreur('Rien n’est revenu. Les énoncés très courts rendent souvent vide : ' +
               'essayez une phrase entière.');
      }
    },
    onErreur: function (quoi, detail) {
      if (quoi === 'MIC') {
        erreur(detail === 'NotAllowedError' || detail === 'SecurityError'
          ? '🎙️ Micro refusé. Autorisez-le pour ce site (l’icône dans la barre ' +
            'd’adresse) puis réessayez.'
          : '🎙️ Aucun micro accessible sur cet appareil.');
      } else {
        erreur('🌐 Service injoignable. LuxASR est une démonstration de recherche : ' +
               'il lui arrive d’être hors ligne, et une connexion est indispensable.');
      }
    }
  };

  function init() {
    ['gate', 'consent', 'live', 'micro', 'bandeau', 'texte', 'chrono', 'erreur', 'effacer']
      .forEach(function (k) { ui[k] = document.getElementById('asr-' + k); });
    if (!ui.gate) return;

    if (!window.LuxAsrClient || !window.LuxAsrClient.disponible()) {
      ui.consent.disabled = true;
      ui.consent.textContent = '🚫 Navigateur incompatible';
      erreur('Ce navigateur ne donne pas accès au micro : il faut une page en HTTPS ' +
             'et un navigateur récent. L’APK ci-dessous fait la même démonstration.');
      ui.gate.appendChild(ui.erreur);
      return;
    }

    client = window.LuxAsrClient(ecouteur);

    ui.consent.addEventListener('click', function () {
      ui.gate.hidden = true;
      ui.live.hidden = false;
      afficher('', false); erreur(''); chrono('');
      client.start();   // le clic est le geste utilisateur qu'exigent micro et audio
    });
    ui.micro.addEventListener('click', function () {
      if (client.occupe) { client.stop(); return; }
      erreur(''); chrono(''); afficher('', false);
      client.start();
    });
    ui.effacer.addEventListener('click', function () {
      client.cancel();
      afficher('', false); chrono(''); erreur(''); setBandeau('IDLE');
    });
    // Quitter la page coupe la capture et la connexion, sans compter sur le
    // ramasse-miettes.
    window.addEventListener('pagehide', function () { client.cancel(); });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else { init(); }
})();
