/*
 * Dictée LuxASR dans le navigateur — portage de LuxAsrSession.kt.
 *
 * Même service, même protocole et mêmes réglages que l'APK de démonstration :
 * WebSocket vers `wss://luxasr.uni.lu/prod/ws/transcribe`, PCM 16 bits
 * little-endian 16 kHz mono en trames binaires, contrôle en JSON,
 * `accumulated_text` qui réécrit l'énoncé entier à chaque passe. La page
 * n'existe que pour montrer la dictée sans rien installer : c'est le même
 * déroulé, à l'écran, avec le même bandeau.
 *
 * Ce qui ne se transpose pas du téléphone :
 *
 * - **Le micro du navigateur n'est pas celui d'Android.** Chrome et Firefox
 *   appliquent leur propre suppression de bruit et leur gain automatique, qui
 *   écrasent l'énergie sur laquelle repose la détection de fin d'énoncé. On les
 *   désactive dans les contraintes ; là où le navigateur refuse, le plancher de
 *   bruit adaptatif rattrape le reste.
 * - **Les chiffres de WER de la page ne s'y appliquent pas.** Ils ont été
 *   mesurés à travers la chaîne acoustique d'un téléphone. Un micro de portable
 *   à cinquante centimètres n'est pas le même signal.
 *
 * Ce qui est identique et doit le rester : l'audio ne part qu'une fois la
 * connexion établie, jamais avant ; le silence termine l'énoncé au bout de 1,5 s
 * pour ne pas laisser le service broder ; un énoncé est plafonné à 90 s ; et la
 * queue répétitive de whisper est coupée à l'affichage (voir `couperBoucle`).
 */
(function () {
  'use strict';

  var ENDPOINT = 'wss://luxasr.uni.lu/prod/ws/transcribe';
  var RATE = 16000;

  /* Réglages du découpage côté serveur. Repris tels quels de LuxAsrSession :
     2 s au lieu des 5 s par défaut fait apparaître le texte pendant qu'on
     parle, contre une exactitude un peu plus variable. Le serveur ne les lit
     que sous cette forme imbriquée ; à plat, il les ignore en silence. */
  var CHUNK_INTERVAL_S = 2.0;
  var CHUNK_SILENCE_S = 0.5;
  var CHUNK_MAX_S = 30.0;

  var BLOC_MS = 160;              // taille d'un bloc audio, comme AudioRecorder
  var FINAL_GRACE_MS = 4000;      // attente du dernier segment après « stop »
  var SILENCE_HANGOVER_MS = 1500; // silence qui termine l'énoncé
  var SPEECH_FLOOR_RMS = 0.012;   // plancher absolu sous lequel rien n'est parole
  var SPEECH_MARGIN = 2.5;        // marge au-dessus du bruit ambiant
  var NOISE_RISE = 0.02;          // vitesse de remontée du plancher de bruit
  var MAX_UTTERANCE_MS = 90000;   // garde-fou si le silence n'arrive jamais
  var LEVEL_FULL_SCALE = 0.18;    // même échelle que le micro du clavier

  var BANDEAU = {
    LOADING: '🌐 LuxASR verbannen…',
    LISTENING: '🌐 LuxASR · Schwätzt…',
    FINALIZING: '🌐 LuxASR · Transkriptioun…'
  };

  var ui = {};
  var etat = 'IDLE';
  var ws = null, ctx = null, flux = null, source = null, noeud = null, puits = null;
  var moduleCharge = false;
  var accumule = '', generation = 0, debutMs = 0, minuteurFinal = null;
  var parle = false, dernierSonMs = 0, plancherBruit = 0, resteEchantillon = 0;

  // --- Coupe-boucle -------------------------------------------------------

  /*
   * Port de RepetitionTrimmer.kt : coupe la queue que les modèles de la famille
   * whisper produisent quand le décodage boucle en fin d'énoncé. La première
   * occurrence du motif est conservée — c'est généralement ce qui a réellement
   * été dit ; la suite est inventée.
   */
  var MAX_BLOC = 8;
  function seuil(longueurBloc) { return longueurBloc === 1 ? 4 : 3; }

  function decouper(texte) {
    var mots = [], debut = -1, i, c, estMot;
    for (i = 0; i <= texte.length; i++) {
      c = texte.charAt(i);
      estMot = i < texte.length && /[\p{L}\p{N}'’-]/u.test(c);
      if (estMot && debut < 0) debut = i;
      if (!estMot && debut >= 0) {
        var cle = texte.slice(debut, i).toLowerCase().replace(/^[-'’]+|[-'’]+$/g, '');
        if (cle) mots.push({ fin: i, cle: cle });
        debut = -1;
      }
    }
    return mots;
  }

  function couperBoucle(texte) {
    var mots = decouper(texte);
    if (mots.length < 2) return texte;
    for (var bloc = 1; bloc <= MAX_BLOC; bloc++) {
      if (mots.length < bloc * 2) break;
      var i = mots.length - 1;
      while (i - bloc >= 0 && mots[i].cle === mots[i - bloc].cle) i--;
      if (mots.length - 1 - i < bloc * (seuil(bloc) - 1)) continue;
      if (i >= mots.length - 1) continue;
      return texte.slice(0, mots[i].fin).replace(/\s+$/, '');
    }
    return texte;
  }

  // --- Capture audio ------------------------------------------------------

  /* Le worklet ne fait qu'accumuler : il rend des blocs de ~160 ms au fil de
     l'eau, comme AudioRecorder sur Android, pour que la cadence d'envoi et
     celle de la détection de silence soient les mêmes que sur le téléphone. */
  var WORKLET = [
    'class BlocPcm extends AudioWorkletProcessor {',
    '  constructor(o){ super();',
    '    this.taille = o.processorOptions.taille;',
    '    this.buf = new Float32Array(this.taille); this.n = 0; }',
    '  process(entrees){',
    '    const ch = entrees[0] && entrees[0][0];',
    '    if (!ch) return true;',
    '    for (let i = 0; i < ch.length; i++) {',
    '      this.buf[this.n++] = ch[i];',
    '      if (this.n === this.taille) {',
    '        this.port.postMessage(this.buf.slice()); this.n = 0; } }',
    '    return true; } }',
    'registerProcessor("bloc-pcm", BlocPcm);'
  ].join('\n');

  function contexte() {
    if (!ctx) {
      var C = window.AudioContext || window.webkitAudioContext;
      // Demander 16 kHz évite le rééchantillonnage ; là où le navigateur
      // refuse (Safari ancien), `reechantillonner` s'en charge.
      try { ctx = new C({ sampleRate: RATE }); } catch (e) { ctx = new C(); }
    }
    return ctx.state === 'suspended' ? ctx.resume().then(function () { return ctx; })
                                     : Promise.resolve(ctx);
  }

  function ouvrirMicro() {
    return navigator.mediaDevices.getUserMedia({
      audio: {
        channelCount: 1,
        // Le traitement du navigateur lisse l'énergie sur laquelle repose la
        // détection de fin d'énoncé ; on le décline quand c'est possible.
        echoCancellation: false, noiseSuppression: false, autoGainControl: false
      }
    }).then(function (s) {
      flux = s;
      return contexte();
    }).then(function (c) {
      if (!moduleCharge && c.audioWorklet) {
        var url = URL.createObjectURL(new Blob([WORKLET], { type: 'text/javascript' }));
        return c.audioWorklet.addModule(url).then(function () {
          URL.revokeObjectURL(url); moduleCharge = true;
        });
      }
    });
  }

  /* Le graphe n'est branché qu'ici, après l'ouverture de la connexion : capter
     pendant que la liaison s'établit enregistrerait une amorce que le service
     ne verrait jamais, et qu'on croirait pourtant dictée. */
  function brancher() {
    var taille = Math.max(128, Math.round(ctx.sampleRate * BLOC_MS / 1000));
    source = ctx.createMediaStreamSource(flux);
    if (moduleCharge) {
      noeud = new AudioWorkletNode(ctx, 'bloc-pcm', { processorOptions: { taille: taille } });
      noeud.port.onmessage = function (e) { surBloc(e.data); };
    } else {
      // Repli pour les navigateurs sans AudioWorklet : déprécié, mais c'est
      // cela ou pas de dictée du tout.
      var n = 1; while (n < taille) n *= 2;
      noeud = ctx.createScriptProcessor(Math.min(16384, n), 1, 1);
      noeud.onaudioprocess = function (e) { surBloc(new Float32Array(e.inputBuffer.getChannelData(0))); };
    }
    // Un puits muet : sans destination, certains navigateurs ne font pas
    // tourner le graphe.
    puits = ctx.createGain(); puits.gain.value = 0;
    source.connect(noeud); noeud.connect(puits); puits.connect(ctx.destination);
    resteEchantillon = 0;
  }

  function debrancher() {
    try { if (source) source.disconnect(); } catch (e) {}
    try { if (noeud) { noeud.disconnect(); if (noeud.port) noeud.port.onmessage = null; } } catch (e) {}
    try { if (puits) puits.disconnect(); } catch (e) {}
    source = noeud = puits = null;
    // Les pistes sont réellement fermées entre deux dictées : le voyant
    // d'enregistrement du navigateur doit s'éteindre quand on ne capte plus.
    if (flux) { flux.getTracks().forEach(function (t) { t.stop(); }); flux = null; }
  }

  /* Rééchantillonnage linéaire vers 16 kHz, avec report de la position
     fractionnaire d'un bloc au suivant pour ne pas dériver. L'interpolation
     ignore le raccord entre deux blocs — un échantillon toutes les 160 ms,
     inaudible et sans effet sur la reconnaissance. */
  function reechantillonner(bloc, srcRate) {
    if (srcRate === RATE) return bloc;
    var ratio = srcRate / RATE;
    var out = new Float32Array(Math.ceil((bloc.length - resteEchantillon) / ratio) + 1);
    var i = resteEchantillon, k = 0, i0, f;
    while (i < bloc.length - 1) {
      i0 = Math.floor(i); f = i - i0;
      out[k++] = bloc[i0] * (1 - f) + bloc[i0 + 1] * f;
      i += ratio;
    }
    resteEchantillon = Math.max(0, i - bloc.length);
    return out.subarray(0, k);
  }

  function surBloc(brut) {
    if (etat !== 'LISTENING' || !ws || ws.readyState !== 1) return;
    var bloc = reechantillonner(brut, ctx.sampleRate);
    if (!bloc.length) return;

    var pcm = new Int16Array(bloc.length), somme = 0, s, i;
    for (i = 0; i < bloc.length; i++) {
      s = Math.max(-1, Math.min(1, bloc[i]));
      somme += s * s;
      pcm[i] = s * 32767;
    }
    ws.send(pcm.buffer);

    var rms = Math.sqrt(somme / bloc.length);
    niveau(Math.sqrt(Math.max(0, Math.min(1, rms / LEVEL_FULL_SCALE))));
    detecterFinDEnonce(rms);
  }

  /*
   * Termine l'énoncé quand la parole s'arrête, plutôt que d'attendre un clic.
   *
   * Mesuré sur téléphone : couper deux secondes après la fin de la parole
   * tronque le dernier segment, laisser tourner dix secondes de silence fait
   * **inventer** le service, qui re-segmente le blanc. On coupe donc à la
   * source : pas de silence envoyé, pas de silence à halluciner.
   */
  function detecterFinDEnonce(rms) {
    var now = Date.now();
    if (debutMs && now - debutMs >= MAX_UTTERANCE_MS) { arreter(); return; }

    // Plancher adaptatif : il redescend d'un coup sur le silence et ne remonte
    // que lentement, de sorte qu'une pièce bruyante relève le seuil sans qu'une
    // voyelle tenue le fasse. Un seuil fixe ne vaudrait que pour un micro.
    plancherBruit = rms < plancherBruit ? rms
                  : plancherBruit + (rms - plancherBruit) * NOISE_RISE;
    var seuilParole = Math.max(SPEECH_FLOOR_RMS, plancherBruit * SPEECH_MARGIN);

    if (rms >= seuilParole) { parle = true; dernierSonMs = now; return; }
    if (!parle || now - dernierSonMs < SILENCE_HANGOVER_MS) return;
    arreter();
  }

  // --- Session ------------------------------------------------------------

  function demarrer() {
    if (etat !== 'IDLE') return;
    var gen = ++generation;
    accumule = ''; parle = false; dernierSonMs = 0; plancherBruit = 0;
    erreur(''); afficher('', false); chrono('');
    setEtat('LOADING');

    ouvrirMicro().then(function () {
      if (gen !== generation) { debrancher(); return; }
      ouvrirSocket(gen);
    }).catch(function (e) {
      if (gen !== generation) return;
      debrancher(); setEtat('IDLE');
      erreur(e && (e.name === 'NotAllowedError' || e.name === 'SecurityError')
        ? '🎙️ Micro refusé. Autorisez-le pour ce site — l’icône dans la barre d’adresse — puis réessayez.'
        : '🎙️ Aucun micro accessible sur cet appareil.');
    });
  }

  function ouvrirSocket(gen) {
    try { ws = new WebSocket(ENDPOINT); } catch (e) { echecService(gen); return; }
    ws.binaryType = 'arraybuffer';

    ws.onopen = function () {
      if (gen !== generation) { try { ws.close(); } catch (e) {} return; }
      // Réglages envoyés avant la première trame : le serveur les applique à ce
      // qu'il reçoit ensuite, pas à ce qu'il a déjà mis de côté.
      ws.send(JSON.stringify({
        type: 'config', language: 'lb',
        chunk_params: {
          periodic_send_interval: CHUNK_INTERVAL_S,
          silence_threshold: CHUNK_SILENCE_S,
          max_chunk_duration: CHUNK_MAX_S
        }
      }));
      brancher();
      debutMs = Date.now();
      setEtat('LISTENING');
    };

    ws.onmessage = function (ev) {
      if (gen !== generation || typeof ev.data !== 'string') return;
      traiter(ev.data);
    };
    ws.onerror = function () { if (gen === generation && etat !== 'IDLE') echecService(gen); };
    ws.onclose = function () { if (gen === generation && etat !== 'IDLE') conclure(); };
  }

  function echecService(gen) {
    if (gen !== generation) return;
    debrancher(); setEtat('IDLE');
    erreur('🌐 Service injoignable. LuxASR est une démonstration de recherche : ' +
           'il lui arrive d’être hors ligne, et une connexion est indispensable.');
  }

  function traiter(brut) {
    var m; try { m = JSON.parse(brut); } catch (e) { return; }
    if (m.type === 'transcription') {
      // `accumulated_text` porte tout l'énoncé depuis le début : on remplace en
      // bloc, on ne recolle jamais de fragments. Le texte retenu n'est pas
      // filtré, seulement celui qui est affiché — une passe ultérieure peut
      // lever l'ambiguïté d'une boucle naissante.
      accumule = m.accumulated_text || accumule;
      var proc = m.metrics && m.metrics.processing_time;
      chrono(((Date.now() - debutMs) / 1000).toFixed(1) + ' s → ' +
             Math.round((proc || 0) * 1000) + ' ms');
      var propre = couperBoucle(accumule);
      if (propre) afficher(propre, true);
    } else if (m.type === 'recording_stopped') {
      conclure();
    } else if (m.type === 'error') {
      erreur('🌐 Le service a répondu par une erreur : ' + (m.message || 'sans détail') + '.');
    }
  }

  /** Termine la dictée : le serveur vide ce qu'il retenait, puis on conclut. */
  function arreter() {
    if (etat !== 'LISTENING' && etat !== 'LOADING') return;
    debrancher();
    setEtat('FINALIZING');
    if (!ws || ws.readyState !== 1) { conclure(); return; }
    ws.send(JSON.stringify({ type: 'stop' }));
    // Si le dernier segment n'arrive pas, on rend quand même ce qui a été
    // accumulé : mieux vaut un texte partiel qu'un bandeau figé.
    clearTimeout(minuteurFinal);
    minuteurFinal = setTimeout(function () {
      if (etat === 'FINALIZING') conclure();
    }, FINAL_GRACE_MS);
  }

  function annuler() {
    if (etat === 'IDLE') return;
    generation++;
    clearTimeout(minuteurFinal);
    debrancher();
    try { if (ws) ws.close(1000); } catch (e) {}
    ws = null; accumule = '';
    setEtat('IDLE'); afficher('', false); chrono('');
  }

  function conclure() {
    if (etat === 'IDLE') return;
    clearTimeout(minuteurFinal);
    var texte = couperBoucle(accumule);
    debrancher();
    try { if (ws) ws.close(1000); } catch (e) {}
    ws = null;
    var duree = debutMs ? ((Date.now() - debutMs) / 1000).toFixed(1) : null;
    setEtat('IDLE');
    afficher(texte, false);
    chrono(texte ? '✅ ' + duree + ' s' : '');
    if (!texte) {
      erreur('Rien n’est revenu. Les énoncés très courts rendent souvent vide : ' +
             'essayez une phrase entière.');
    }
  }

  // --- Affichage ----------------------------------------------------------

  function setEtat(suivant) {
    if (etat === suivant) return;
    etat = suivant;
    var actif = etat !== 'IDLE';
    ui.micro.classList.toggle('actif', etat === 'LISTENING');
    ui.micro.classList.toggle('attente', etat === 'LOADING' || etat === 'FINALIZING');
    ui.micro.setAttribute('aria-pressed', String(actif));
    ui.micro.textContent = actif ? '⏹' : '🎙️';
    ui.micro.setAttribute('aria-label', actif ? 'Arrêter la dictée' : 'Démarrer la dictée');
    ui.bandeau.textContent = BANDEAU[etat] || 'Prêt — touchez le micro et parlez en luxembourgeois';
    ui.bandeau.classList.toggle('vif', actif);
    if (!actif) niveau(0);
  }

  function niveau(v) { ui.micro.style.setProperty('--niveau', v.toFixed(3)); }

  function afficher(texte, partiel) {
    ui.texte.textContent = texte;
    ui.texte.classList.toggle('partiel', !!partiel && !!texte);
    ui.texte.classList.toggle('vide', !texte);
    if (!texte) ui.texte.textContent = 'Le texte reconnu s’affichera ici.';
    ui.effacer.hidden = !texte;
  }

  function chrono(t) { ui.chrono.textContent = t; }
  function erreur(t) { ui.erreur.textContent = t; ui.erreur.hidden = !t; }

  // --- Mise en route ------------------------------------------------------

  function init() {
    ['gate', 'consent', 'live', 'micro', 'bandeau', 'texte', 'chrono', 'erreur', 'effacer']
      .forEach(function (k) { ui[k] = document.getElementById('asr-' + k); });
    if (!ui.gate) return;

    var securise = window.isSecureContext ||
                   location.protocol === 'https:' || location.hostname === 'localhost';
    if (!securise || !window.WebSocket ||
        !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      ui.consent.disabled = true;
      ui.consent.textContent = '🚫 Navigateur incompatible';
      erreur('Ce navigateur ne donne pas accès au micro — il faut une page en HTTPS ' +
             'et un navigateur récent. L’APK ci-dessous fait la même démonstration.');
      ui.erreur.hidden = false;
      ui.gate.appendChild(ui.erreur);
      return;
    }

    ui.consent.addEventListener('click', function () {
      ui.gate.hidden = true;
      ui.live.hidden = false;
      afficher('', false);
      demarrer();   // le clic est le geste utilisateur qu'exigent micro et audio
    });
    ui.micro.addEventListener('click', function () {
      if (etat === 'IDLE') demarrer(); else arreter();
    });
    ui.effacer.addEventListener('click', function () {
      if (etat !== 'IDLE') annuler(); else { afficher('', false); chrono(''); erreur(''); }
    });
    // Quitter la page coupe la capture et la connexion, sans compter sur le
    // ramasse-miettes.
    window.addEventListener('pagehide', annuler);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else { init(); }
})();
