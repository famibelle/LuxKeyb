/*
 * Transcription en direct par le flux de LuxASR — la seconde option de la
 * rubrique Labs « Transcription ».
 *
 * Même service et même protocole que la dictée (`luxasr-client.js`,
 * `LuxAsrSession.kt`) : un WebSocket, la configuration en JSON, puis le PCM
 * 16 bits à 16 kHz en trames binaires. Le service relit la partie non engagée
 * toutes les 0,5 à 1 s : `accumulated_text` ne fait que grandir,
 * `partial_text` est la queue encore instable, et `send_reason:
 * "silence_commit"` avec une queue vide dit qu'une phrase est close.
 *
 * Ce qui change par rapport à la dictée : elle dure. La dictée s'arrête
 * d'elle-même après 5 s de silence et 90 s au plus ; ici on écoute une
 * réunion jusqu'à ce qu'on arrête. D'où trois choix :
 *
 * - **Le silence ne clôt rien.** Il ne fait que marquer les paragraphes.
 * - **La liaison est renouvelée** à la première phrase close passé
 *   ROTATION_S, et de force passé ROTATION_FORCE_S : chaque message porte tout
 *   l'`accumulated_text` de la liaison, qui sinon grandirait sans fin. On
 *   envoie « stop », le service engage la queue, et une nouvelle liaison
 *   prend la suite. Le micro, lui, ne s'arrête pas : ce qu'il capte pendant
 *   le raccord est gardé, puis envoyé d'un trait à la nouvelle liaison, ce
 *   que le service accepte puisqu'il découpe sur les échantillons reçus et
 *   non sur l'horloge (`stt/bench/probe_gap.py`).
 * - **Une liaison coupée est reprise** de la même façon, ECHECS_MAX fois.
 *
 * Pas de séparation des voix : le flux n'en fait pas. C'est la voie par lots
 * (`luxasr-transcription.js`) qui la donne, avec un fichier.
 *
 * CORS ne concerne pas cette voie : un WebSocket n'y est pas soumis.
 *
 * Ne dessine rien : la page reçoit des évènements et des paragraphes.
 */
(function (racine) {
  'use strict';

  /* `LUXASR_BASE_FLUX` et `LUXASR_ROTATION_S` ne servent qu'aux tests, qui
     jouent le service en local et raccourcissent les liaisons. */
  var ENDPOINT = (racine.LUXASR_BASE_FLUX || 'wss://luxasr.uni.lu') + '/prod/ws/transcribe';
  var ROTATION_S = racine.LUXASR_ROTATION_S || 120;
  var ROTATION_FORCE_S = 600;

  var RATE = 16000;
  var BLOC_MS = 160;               // comme AudioRecorder et luxasr-client.js

  /* Une heure d'écoute au plus par séance : le service est partagé. */
  var DUREE_MAX_S = 60 * 60;

  /* Attente du dernier texte après « stop », comme la dictée. */
  var GRACE_STOP_MS = 4000;

  /* Un silence plus long ouvre un paragraphe ; un paragraphe plus long que
     PARAGRAPHE_MAX_S s'arrête à la phrase close suivante. */
  var PAUSE_PARAGRAPHE_MS = 2500;
  var PARAGRAPHE_MAX_S = 45;

  /* Le premier aperçu arrive ~1,1 s après le début de la parole (médiane du
     banc du 19 septembre 2026) : l'horodatage d'un paragraphe en tient compte. */
  var LATENCE_S = 1;

  /* Audio en attente d'envoi, en secondes : au-delà du premier seuil on le
     signale, au-delà du second on arrête, comme la dictée sur réseau lent. */
  var RETARD_ALERTE_S = 2;
  var RETARD_MAX_S = 30;

  var ECHECS_MAX = 3;
  /* Une liaison qui ne s'ouvre pas dans ce délai est tenue pour perdue. */
  var CONNEXION_MAX_MS = 8000;
  var LEVEL_FULL_SCALE = 0.18;     // même échelle que le micro du clavier

  /* Le worklet ne fait qu'accumuler, comme celui de luxasr-client.js. */
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
    'registerProcessor("bloc-pcm-direct", BlocPcm);'
  ].join('\n');

  /**
   * Une séance d'écoute. [ecouteur] reçoit :
   * - `onEtat(etat)` parmi `REPOS`, `CONNEXION`, `ECOUTE`, `ARRET` ;
   * - `onNiveau(0..1)` à chaque bloc capté ;
   * - `onTexte(paragraphes)`, `[{ debut, texte, partiel }]`, `partiel` étant
   *   la queue encore instable du dernier paragraphe ;
   * - `onAlerte('LENT', secondesEnRetard)` puis `onAlerte('LENT', 0)` au retour ;
   * - `onFin(paragraphes, raison)`, `raison` à `null` sur un arrêt demandé,
   *   sinon `MIC`, `SERVICE`, `LENT` ou `DUREE`.
   */
  function LuxAsrDirect(ecouteur) {
    var ctx = null, flux = null, source = null, noeud = null, puits = null;
    var moduleCharge = false, resteEchantillon = 0;
    var etat = 'REPOS', generation = 0;

    var liaison = null;          // { ws, id, ouverte, close }
    var numero = 0, echecs = 0;
    var enAttente = [];          // blocs PCM16 pas encore confiés au socket
    var echantillons = 0;        // captés depuis le début de la séance
    var lent = false;

    /** Paragraphes des liaisons closes : `{ debut, texte }`. */
    var figes = [];
    /** Liaison en cours : texte engagé, queue, et débuts de paragraphe. */
    var acc = '', queue = '', marques = [], debutLiaison = 0;
    /** Une phrase est close, et quand : le texte suivant décidera du paragraphe. */
    var close = true, closeA = 0, debutParagraphe = 0;
    var renouvellement = false, minuteur = null;

    function prevenir(nom, a, b) {
      var f = ecouteur && ecouteur[nom];
      if (f) f.call(ecouteur, a, b);
    }
    function setEtat(e) { if (etat !== e) { etat = e; prevenir('onEtat', e); } }
    function secondes() { return echantillons / RATE; }

    // --- Capture : la même que luxasr-client.js ---------------------------

    function ouvrirMicro() {
      return navigator.mediaDevices.getUserMedia({
        audio: { channelCount: 1, echoCancellation: false, noiseSuppression: false, autoGainControl: false }
      }).then(function (s) {
        flux = s;
        if (!ctx) {
          var C = window.AudioContext || window.webkitAudioContext;
          try { ctx = new C({ sampleRate: RATE }); } catch (e) { ctx = new C(); }
        }
        return ctx.state === 'suspended' ? ctx.resume() : null;
      }).then(function () {
        if (!moduleCharge && ctx.audioWorklet) {
          var url = URL.createObjectURL(new Blob([WORKLET], { type: 'text/javascript' }));
          return ctx.audioWorklet.addModule(url).then(function () { URL.revokeObjectURL(url); moduleCharge = true; });
        }
      });
    }

    function brancher() {
      var taille = Math.max(128, Math.round(ctx.sampleRate * BLOC_MS / 1000));
      source = ctx.createMediaStreamSource(flux);
      if (moduleCharge) {
        noeud = new AudioWorkletNode(ctx, 'bloc-pcm-direct', { processorOptions: { taille: taille } });
        noeud.port.onmessage = function (e) { surBloc(e.data); };
      } else {
        var n = 1; while (n < taille) n *= 2;
        noeud = ctx.createScriptProcessor(Math.min(16384, n), 1, 1);
        noeud.onaudioprocess = function (e) { surBloc(new Float32Array(e.inputBuffer.getChannelData(0))); };
      }
      puits = ctx.createGain(); puits.gain.value = 0;
      source.connect(noeud); noeud.connect(puits); puits.connect(ctx.destination);
      resteEchantillon = 0;
    }

    function debrancher() {
      try { if (source) source.disconnect(); } catch (e) {}
      try { if (noeud) { noeud.disconnect(); if (noeud.port) noeud.port.onmessage = null; } } catch (e) {}
      try { if (puits) puits.disconnect(); } catch (e) {}
      source = noeud = puits = null;
      if (flux) { flux.getTracks().forEach(function (t) { t.stop(); }); flux = null; }
    }

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
      if (etat !== 'ECOUTE' && etat !== 'CONNEXION') return;
      var bloc = reechantillonner(brut, ctx.sampleRate);
      if (!bloc.length) return;
      var pcm = new Int16Array(bloc.length), somme = 0;
      for (var i = 0; i < bloc.length; i++) {
        pcm[i] = Math.max(-1, Math.min(1, bloc[i])) * 32767;
        somme += bloc[i] * bloc[i];
      }
      echantillons += bloc.length;
      envoyer(pcm.buffer);
      prevenir('onNiveau', Math.sqrt(Math.min(1, Math.sqrt(somme / bloc.length) / LEVEL_FULL_SCALE)));
      if (secondes() >= DUREE_MAX_S) { terminer('DUREE'); return; }
      surveillerRetard();
    }

    function envoyer(octets) {
      if (liaison && liaison.ouverte && !renouvellement) liaison.ws.send(octets);
      else enAttente.push(octets);
    }

    function surveillerRetard() {
      var ws = liaison && liaison.ouverte ? liaison.ws : null;
      var attente = enAttente.reduce(function (n, b) { return n + b.byteLength; }, 0) +
                    (ws ? ws.bufferedAmount : 0);
      var retard = attente / (RATE * 2);
      if (retard >= RETARD_MAX_S) { terminer('LENT'); return; }
      var estLent = retard >= RETARD_ALERTE_S;
      if (estLent !== lent) { lent = estLent; prevenir('onAlerte', 'LENT', estLent ? retard : 0); }
    }

    // --- Liaisons ---------------------------------------------------------

    function ouvrirLiaison() {
      var gen = generation, l = { id: ++numero, ouverte: false, finie: false };
      liaison = l;
      try { l.ws = new WebSocket(ENDPOINT); } catch (e) { perdue(l); return; }
      l.ws.binaryType = 'arraybuffer';
      l.delai = setTimeout(function () {
        if (gen === generation && l === liaison && !l.ouverte && !l.finie) perdue(l);
      }, CONNEXION_MAX_MS);
      l.ws.onopen = function () {
        if (gen !== generation || l !== liaison) { try { l.ws.close(1000); } catch (e) {} return; }
        clearTimeout(l.delai);
        l.ouverte = true;
        echecs = 0;
        // Les réglages partent avant la première trame ; puis ce qui a été
        // capté pendant l'ouverture, d'un trait.
        l.ws.send(JSON.stringify({ type: 'config', language: 'lb' }));
        enAttente.forEach(function (b) { l.ws.send(b); });
        enAttente = [];
        if (etat === 'CONNEXION') setEtat('ECOUTE');
        // Arrêt demandé pendant un raccord : cette liaison ne sert qu'à vider
        // l'audio en attente.
        if (etat === 'ARRET') demanderStop(l);
      };
      l.ws.onmessage = function (ev) {
        if (gen !== generation || l !== liaison || typeof ev.data !== 'string') return;
        var m; try { m = JSON.parse(ev.data); } catch (e) { return; }
        if (m.type === 'transcription') surTranscription(m);
        else if (m.type === 'recording_stopped') conclureLiaison(l);
      };
      l.ws.onerror = function () { if (gen === generation && l === liaison && !l.finie) perdue(l); };
      l.ws.onclose = function () { if (gen === generation && l === liaison && !l.finie) perdue(l); };
    }

    /** Liaison coupée sans qu'on l'ait demandé : on la reprend, ou on s'arrête. */
    function perdue(l) {
      if (l.finie) return;
      l.finie = true;
      var etaitOuverte = l.ouverte;
      // Un renouvellement en cours n'a plus d'objet : la liaison suivante
      // reçoit l'audio en attente dès son ouverture.
      renouvellement = false;
      clearTimeout(minuteur);
      figer();
      fermer(l);
      if (etat === 'ARRET') { finir(null); return; }
      if (!etaitOuverte && etat === 'CONNEXION' && numero === 1) { terminer('SERVICE'); return; }
      if (++echecs > ECHECS_MAX) { terminer('SERVICE'); return; }
      ouvrirLiaison();
    }

    function fermer(l) {
      clearTimeout(l.delai);
      try { l.ws.onopen = l.ws.onmessage = l.ws.onerror = l.ws.onclose = null; l.ws.close(1000); } catch (e) {}
    }

    function demanderStop(l) {
      try { l.ws.send(JSON.stringify({ type: 'stop' })); } catch (e) {}
      clearTimeout(minuteur);
      // Si `recording_stopped` n'arrive pas, on garde ce qui a été affiché.
      minuteur = setTimeout(function () { conclureLiaison(l); }, GRACE_STOP_MS);
    }

    function renouveler() {
      if (renouvellement || !liaison || !liaison.ouverte) return;
      renouvellement = true;
      demanderStop(liaison);
    }

    /** Fin d'une liaison demandée (renouvellement ou arrêt) : on fige son texte. */
    function conclureLiaison(l) {
      if (l.finie || l !== liaison) return;
      l.finie = true;
      clearTimeout(minuteur);
      figer();
      fermer(l);
      renouvellement = false;
      if (etat === 'ECOUTE') { ouvrirLiaison(); return; }
      if (etat === 'ARRET') {
        // Le micro est coupé, mais de l'audio capté pendant le raccord attend
        // encore : une dernière liaison le transcrit.
        if (enAttente.length) { ouvrirLiaison(); return; }
        finir(null);
      }
    }

    // --- Texte ------------------------------------------------------------

    function surTranscription(m) {
      var nouveau = typeof m.accumulated_text === 'string' && m.accumulated_text ? m.accumulated_text : acc;
      var q = typeof m.partial_text === 'string' ? m.partial_text : '';
      var maintenant = Date.now();

      // Du texte nouveau après une phrase close, ou le premier d'une liaison :
      // nouveau paragraphe si la pause a été longue ou si le paragraphe en
      // cours a assez duré ; sinon le texte prolonge le paragraphe précédent.
      // Une liaison renouvelée en pleine phrase (renouvellement forcé) le
      // prolonge toujours.
      var nouveauTexte = nouveau.length > acc.length || q.trim();
      if (nouveauTexte && (close || !marques.length)) {
        var avant = marques.length || figes.length;
        var suite = avant && (!close || (maintenant - closeA < PAUSE_PARAGRAPHE_MS &&
                                         secondes() - debutParagraphe < PARAGRAPHE_MAX_S));
        var debut = Math.max(0, secondes() - LATENCE_S);
        if (!suite) debutParagraphe = debut;
        marques.push({ debut: debut, offset: acc.length, suite: !!suite });
        close = false;
      }
      acc = nouveau;
      queue = q;
      if (m.send_reason === 'silence_commit' && !q.trim()) { close = true; closeA = maintenant; }

      prevenir('onTexte', paragraphes());
      var duree = secondes() - debutLiaison;
      if ((close && duree >= ROTATION_S) || duree >= ROTATION_FORCE_S) renouveler();
    }

    /** Les paragraphes de la liaison en cours, tirés du texte engagé. */
    function courants(avecQueue) {
      var res = [];
      marques.forEach(function (mk, i) {
        var fin = i + 1 < marques.length ? marques[i + 1].offset : acc.length;
        res.push({ debut: mk.debut, texte: acc.slice(mk.offset, fin).trim(), suite: mk.suite });
      });
      if (avecQueue && queue.trim() && res.length) res[res.length - 1].partiel = queue.trim();
      return res;
    }

    function fusionner(base, ajout) {
      var res = base.map(function (p) { return { debut: p.debut, texte: p.texte, partiel: p.partiel }; });
      ajout.forEach(function (p) {
        var prec = res[res.length - 1];
        if (p.suite && prec) {
          prec.texte = (prec.texte + ' ' + p.texte).trim();
          if (p.partiel) prec.partiel = p.partiel;
        } else {
          res.push({ debut: p.debut, texte: p.texte, partiel: p.partiel });
        }
      });
      return res.filter(function (p) { return p.texte || p.partiel; });
    }

    /** Tout le texte de la séance, la queue instable à part sur le dernier paragraphe. */
    function paragraphes() { return fusionner(figes, courants(true)); }

    /** Fige la liaison qui finit. Après « stop », le service a déjà tout
        engagé ; sinon (grâce écoulée, coupure) on garde la queue affichée. */
    function figer() {
      var cour = courants(true).map(function (p) {
        if (p.partiel) { p.texte = (p.texte + ' ' + p.partiel).trim(); delete p.partiel; }
        return p;
      });
      figes = fusionner(figes, cour);
      acc = ''; queue = ''; marques = [];
      debutLiaison = secondes();
    }

    // --- Arrêts -----------------------------------------------------------

    /** Arrêt imposé (micro, service, réseau, durée) : on garde le texte. */
    function terminer(raison) {
      if (etat === 'REPOS') return;
      if (raison === 'DUREE' || raison === 'LENT') {
        // Le service répond encore : on lui laisse engager la queue.
        arreter(raison);
        return;
      }
      if (liaison) { liaison.finie = true; fermer(liaison); }
      figer();
      finir(raison);
    }

    var raisonFin = null;

    function arreter(raison) {
      if (etat !== 'ECOUTE' && etat !== 'CONNEXION') return;
      raisonFin = raison || null;
      var connexion = etat === 'CONNEXION';
      setEtat('ARRET');
      debrancher();
      if (connexion || !liaison) {
        if (liaison) { liaison.finie = true; fermer(liaison); }
        figer();
        finir(raisonFin);
        return;
      }
      if (renouvellement) return;          // conclureLiaison s'en chargera
      if (liaison.ouverte) demanderStop(liaison);
    }

    function finir(raison) {
      clearTimeout(minuteur);
      debrancher();
      generation++;
      liaison = null; enAttente = []; renouvellement = false;
      var texte = paragraphes();
      setEtat('REPOS');
      prevenir('onFin', texte, raison || raisonFin);
      raisonFin = null;
    }

    return {
      get etat() { return etat; },
      get secondes() { return secondes(); },
      paragraphes: paragraphes,

      start: function () {
        if (etat !== 'REPOS') return;
        var gen = ++generation;
        figes = []; acc = ''; queue = ''; marques = []; enAttente = [];
        echantillons = 0; debutLiaison = 0; close = true; closeA = 0; debutParagraphe = 0;
        echecs = 0; numero = 0; lent = false; renouvellement = false; raisonFin = null;
        setEtat('CONNEXION');
        ouvrirMicro().then(function () {
          if (gen !== generation) { debrancher(); return; }
          // Le micro capte dès qu'il est prêt ; ce qu'il entend avant
          // l'ouverture de la liaison part d'un trait à l'ouverture.
          brancher();
          ouvrirLiaison();
        }).catch(function () {
          if (gen !== generation) return;
          debrancher();
          generation++;
          setEtat('REPOS');
          prevenir('onFin', [], 'MIC');
        });
      },

      /** Arrêt demandé : le service engage la fin, puis on rend le texte. */
      stop: function () { arreter(null); },

      /** Coupe tout sans attendre : page quittée. */
      annuler: function () {
        if (etat === 'REPOS') return;
        generation++;
        clearTimeout(minuteur);
        if (liaison) { liaison.finie = true; fermer(liaison); }
        debrancher();
        liaison = null; enAttente = [];
        setEtat('REPOS');
      }
    };
  }

  LuxAsrDirect.disponible = function () {
    return !!(window.isSecureContext && window.WebSocket &&
              navigator.mediaDevices && navigator.mediaDevices.getUserMedia);
  };

  racine.LuxAsrDirect = LuxAsrDirect;
})(typeof window !== 'undefined' ? window : globalThis);
