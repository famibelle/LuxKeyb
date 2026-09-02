/*
 * Client LuxASR pour navigateur — portage de LuxAsrApiSession.kt, avec
 * LuxAsrSession.kt gardé en repli, exactement comme sur la branche Android.
 *
 * **La voie normale est l'API par lots** : on enregistre l'énoncé entier, on
 * l'envoie d'un bloc à `POST /asr2`, on interroge le travail jusqu'à son terme,
 * on insère le texte. Pas de flux, pas d'hypothèses intermédiaires. C'est le
 * point d'entrée que l'Université documente et soutient ; le WebSocket est
 * déprécié chez eux.
 *
 * Pourquoi : les deux servent le même modèle — sur 62 énoncés mesurés le
 * 1er septembre 2026, 43 transcriptions sont identiques au caractère près —
 * mais l'API décode l'énoncé **d'un seul tenant**, ce qui vaut 26,8 % de mots
 * erronés contre 38,3 % sur les énoncés de 8 à 22 s, le régime que vise un
 * clavier. Le délai final est équivalent, avec un plafond bien plus serré.
 *
 * Ce qu'on perd : l'aperçu pendant qu'on parle. Le champ reste vide jusqu'au
 * bout, d'où les témoins que `simulateur-dictee.js` y pose — un tracé de
 * niveau pendant la parole, un cercle qui tourne pendant la transcription.
 *
 * ---------------------------------------------------------------------------
 *
 * **Ce que le navigateur impose et que le téléphone ignore : CORS.** Mesuré le
 * 2 septembre 2026 depuis `https://famibelle.github.io` :
 *
 * - le préflight `OPTIONS /asr2` répond bien `Access-Control-Allow-Origin: *`,
 *   mais **n'autorise que `Content-Type` et `Authorization`** en entêtes. Le
 *   `X-Filename` que posent l'APK et l'interface de l'Université est donc
 *   impossible ici. Il n'est pas requis : la soumission passe sans lui.
 * - surtout, les réponses **`202 ACCEPTED` ne portent aucun
 *   `Access-Control-Allow-Origin`**, alors que les `200 OK` en portent un. Or
 *   `POST /asr2` répond précisément 202. Le navigateur bloque donc la lecture
 *   de la réponse et le `job_id` n'est jamais lisible — vérifié dans Chrome
 *   contre un serveur qui rejoue ces entêtes à l'octet près : ajouter cette
 *   seule entête au 202 suffit à tout débloquer.
 *
 * Il n'y a rien à faire de notre côté : c'est une entête à ajouter chez eux, ou
 * un serveur à nous, que GitHub Pages ne peut pas être. En attendant, quand la
 * soumission est bloquée, on **rejoue l'audio déjà enregistré sur le
 * WebSocket** au lieu de faire reparler l'utilisateur, et la voie du flux est
 * retenue pour le reste de la visite. Le jour où l'entête apparaît, la page
 * repasse à l'API sans qu'on y touche.
 *
 * `?voie=api` force l'API sans repli, `?voie=ws` force le flux : de quoi
 * comparer les deux sur la même page.
 *
 * ---------------------------------------------------------------------------
 *
 * Ne dessine rien : il rend les mêmes évènements que `SttSession.Listener`
 * côté Android.
 *
 * Ce qui ne se transpose pas du téléphone :
 *
 * - **Le micro du navigateur n'est pas celui d'Android.** Chrome et Firefox
 *   appliquent leur propre suppression de bruit et leur gain automatique, qui
 *   écrasent l'énergie sur laquelle repose la détection de fin d'énoncé. On les
 *   désactive dans les contraintes ; là où le navigateur refuse, le plancher de
 *   bruit adaptatif rattrape le reste.
 * - **Les taux d'erreur publiés ne s'y appliquent pas.** Ils ont été mesurés à
 *   travers la chaîne acoustique d'un téléphone.
 *
 * Ce qui est identique et doit le rester : le silence de 5 s termine l'énoncé,
 * le blanc qui l'entoure n'est jamais envoyé — whisper invente du texte quand
 * on lui donne du silence —, un énoncé est plafonné à 90 s, et la queue
 * répétitive de whisper est coupée à l'affichage.
 */
(function () {
  'use strict';

  var BASE = 'https://luxasr.uni.lu';
  var ENDPOINT_FLUX = BASE.replace(/^http/, 'ws') + '/prod/ws/transcribe';
  var RATE = 16000;

  /* Paramètres de la soumission par lots, repris de LuxAsrApiSession.
     `diarization` est désactivée : une dictée n'a qu'un locuteur, et la
     séparation coûte du temps de calcul pour rien. */
  var CHEMIN_LOTS = '/asr2?language=lb&diarization=Disabled&outfmt=text';

  /** Période d'interrogation du travail (SONDAGE_MS). */
  var SONDAGE_MS = 200;

  /** Au-delà, on abandonne : la file est partagée et peut s'allonger. */
  var TIMEOUT_LOT_MS = 30000;

  /**
   * Combien de temps on patiente sur des sondages illisibles avant de conclure
   * que la voie ne passe pas.
   *
   * Un sondage bloqué ressemble exactement à un travail pas encore prêt : c'est
   * le `202 ACCEPTED` de « processing » qui ne porte pas d'entête CORS, quand
   * le `200 OK` de « completed » en porte une. On ne peut donc pas distinguer
   * les deux, et abandonner au premier échec ferait basculer sur le flux à
   * chaque dictée alors que la soumission, elle, serait passée. Le délai couvre
   * une file chargée — 1,3 s en médiane, 2,1 s au pire lors du banc.
   */
  var GRACE_SONDAGE_MS = 8000;

  /** Marge gardée de part et d'autre de la parole avant l'envoi (MARGE_MS). */
  var MARGE_MS = 300;

  /* Réglages du découpage côté serveur, pour la voie du flux seulement. Le
     serveur ne les lit que sous cette forme imbriquée ; à plat, il les ignore
     en silence. */
  var CHUNK_INTERVAL_S = 2.0;
  var CHUNK_SILENCE_S = 0.5;
  var CHUNK_MAX_S = 30.0;

  var BLOC_MS = 160;              // taille d'un bloc audio, comme AudioRecorder
  var FINAL_GRACE_MS = 4000;      // attente du dernier segment après « stop »
  var SILENCE_HANGOVER_MS = 5000; // silence qui termine l'énoncé
  var SPEECH_FLOOR_RMS = 0.012;   // plancher absolu sous lequel rien n'est parole
  var SPEECH_MARGIN = 2.5;        // marge au-dessus du bruit ambiant
  var NOISE_RISE = 0.02;          // vitesse de remontée du plancher de bruit
  var MAX_UTTERANCE_MS = 90000;   // garde-fou si le silence n'arrive jamais
  var LEVEL_FULL_SCALE = 0.18;    // même échelle que le micro du clavier

  /**
   * Voie retenue pour la visite. Passe à `'ws'` dès qu'une soumission par lots
   * est bloquée, pour ne pas refaire enregistrer un travail que le navigateur
   * ne pourra pas lire — et pour ne pas encombrer leur file avec.
   */
  var voieRetenue = null;

  function voieDemandee() {
    var v = new URLSearchParams(location.search).get('voie');
    return v === 'ws' || v === 'api' ? v : 'auto';
  }

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

  // --- Conteneur WAV ------------------------------------------------------

  /**
   * float32 [-1, 1] → conteneur WAV PCM 16 bits.
   *
   * L'API veut les octets **bruts** du fichier dans le corps de la requête, pas
   * un envoi `multipart/form-data` : elle vérifie que la charge utile est un
   * média décodable, donc il lui faut un vrai conteneur et pas du PCM nu.
   */
  function wav(samples) {
    var octets = samples.length * 2;
    var buf = new ArrayBuffer(44 + octets);
    var vue = new DataView(buf), i;
    function texte(pos, s) { for (var k = 0; k < s.length; k++) vue.setUint8(pos + k, s.charCodeAt(k)); }
    texte(0, 'RIFF'); vue.setUint32(4, 36 + octets, true); texte(8, 'WAVEfmt ');
    vue.setUint32(16, 16, true); vue.setUint16(20, 1, true); vue.setUint16(22, 1, true);
    vue.setUint32(24, RATE, true); vue.setUint32(28, RATE * 2, true);
    vue.setUint16(32, 2, true); vue.setUint16(34, 16, true);
    texte(36, 'data'); vue.setUint32(40, octets, true);
    for (i = 0; i < samples.length; i++) {
      vue.setInt16(44 + i * 2, Math.max(-1, Math.min(1, samples[i])) * 32767, true);
    }
    return buf;
  }

  /* Le worklet ne fait qu'accumuler : il rend des blocs de ~160 ms au fil de
     l'eau, comme AudioRecorder sur Android, pour que la cadence de la détection
     de silence soit la même que sur le téléphone. */
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

  /**
   * Une session de dictée. [ecouteur] reçoit, comme SttSession.Listener :
   * `onEtat(etat)` parmi IDLE / LOADING / LISTENING / FINALIZING,
   * `onNiveau(0..1)`, `onPartiel(texte)`, `onFinal(texte)`,
   * `onPasse(secondesAudio, msService)`, `onErreur('MIC' | 'SERVICE')`.
   *
   * `onPartiel` ne se produit que sur la voie du flux : par lots, il n'y a
   * qu'un `onFinal`.
   */
  function LuxAsrClient(ecouteur) {
    var ws = null, ctx = null, flux = null, source = null, noeud = null, puits = null;
    var moduleCharge = false;
    var etat = 'IDLE', accumule = '', generation = 0, debutMs = 0, minuteurFinal = null;
    var parle = false, dernierSonMs = 0, plancherBruit = 0, resteEchantillon = 0;

    /** Voie de la session en cours, figée à `start()`. */
    var enLots = true;

    /* Énoncé accumulé, pour la voie par lots : les blocs à 16 kHz, le nombre
       d'échantillons vus, et les bornes de la parole dedans. */
    var blocs = [], echantillons = 0, premierSon = -1, dernierSon = -1;

    function prevenir(nom, a, b) {
      var f = ecouteur && ecouteur[nom];
      if (f) f.call(ecouteur, a, b);
    }

    function setEtat(suivant) {
      if (etat === suivant) return;
      etat = suivant;
      prevenir('onEtat', etat);
    }

    // --- Capture audio ----------------------------------------------------

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
        noeud.onaudioprocess = function (e) {
          surBloc(new Float32Array(e.inputBuffer.getChannelData(0)));
        };
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

    function pcm16(bloc) {
      var out = new Int16Array(bloc.length);
      for (var i = 0; i < bloc.length; i++) {
        out[i] = Math.max(-1, Math.min(1, bloc[i])) * 32767;
      }
      return out;
    }

    function surBloc(brut) {
      if (etat !== 'LISTENING') return;
      var bloc = reechantillonner(brut, ctx.sampleRate);
      if (!bloc.length) return;

      var somme = 0, i;
      for (i = 0; i < bloc.length; i++) somme += bloc[i] * bloc[i];
      var rms = Math.sqrt(somme / bloc.length);

      if (enLots) {
        // Par lots, rien ne part avant la fin : on garde tout en mémoire, en
        // notant où commence et où finit la parole.
        var debut = echantillons;
        blocs.push(new Float32Array(bloc));
        echantillons += bloc.length;
        if (estParole(rms)) { premierSon = premierSon < 0 ? debut : premierSon;
                              dernierSon = debut + bloc.length; }
      } else {
        if (!ws || ws.readyState !== 1) return;
        ws.send(pcm16(bloc).buffer);
      }

      prevenir('onNiveau', Math.sqrt(Math.max(0, Math.min(1, rms / LEVEL_FULL_SCALE))));
      detecterFinDEnonce(rms);
    }

    /**
     * Plancher adaptatif : il redescend d'un coup sur le silence et ne remonte
     * que lentement, de sorte qu'une pièce bruyante relève le seuil sans qu'une
     * voyelle tenue le fasse. Un seuil fixe ne vaudrait que pour le micro sur
     * lequel il a été réglé.
     */
    function estParole(rms) {
      plancherBruit = rms < plancherBruit ? rms
                    : plancherBruit + (rms - plancherBruit) * NOISE_RISE;
      return rms >= Math.max(SPEECH_FLOOR_RMS, plancherBruit * SPEECH_MARGIN);
    }

    /*
     * Termine l'énoncé quand la parole s'arrête, plutôt que d'attendre un
     * second appui.
     *
     * Mesuré sur téléphone : couper deux secondes après la fin de la parole
     * tronque le dernier segment, laisser tourner dix secondes de silence fait
     * **inventer** le service. Cinq secondes tiennent le milieu, et par lots le
     * blanc des bords n'est de toute façon pas envoyé.
     */
    function detecterFinDEnonce(rms) {
      var now = Date.now();
      if (debutMs && now - debutMs >= MAX_UTTERANCE_MS) { api.stop(); return; }
      // En lots, `estParole` a déjà été appelée par `surBloc` : la rappeler
      // ferait avancer le plancher de bruit deux fois par bloc.
      var parlant = enLots ? dernierSon === echantillons : estParole(rms);
      if (parlant) { parle = true; dernierSonMs = now; return; }
      if (!parle || now - dernierSonMs < SILENCE_HANGOVER_MS) return;
      api.stop();
    }

    // --- Voie par lots ----------------------------------------------------

    /**
     * Ne garde que la parole, avec une marge de part et d'autre.
     *
     * Whisper hallucine sur le silence — dix secondes de blanc lui faisaient
     * inventer une phrase entière, et un énoncé plafonné à 90 s rendait 270
     * mots pour 192 attendus. En lot, il suffit de ne pas mettre le silence
     * dans le fichier.
     *
     * Les pauses **internes** sont conservées : les retirer recollerait des
     * mots que le locuteur a séparés, et le modèle décode de toute façon
     * l'énoncé entier d'un coup. Seuls les bords sont coupés.
     */
    function decouperSurLaParole() {
      if (!blocs.length || premierSon < 0 || dernierSon <= premierSon) return new Float32Array(0);
      var tout = new Float32Array(echantillons), i = 0, b;
      for (b = 0; b < blocs.length; b++) { tout.set(blocs[b], i); i += blocs[b].length; }
      var marge = Math.round(MARGE_MS * RATE / 1000);
      return tout.subarray(Math.max(0, premierSon - marge),
                           Math.min(tout.length, dernierSon + marge));
    }

    function transcrireLot(gen, audio) {
      var t0 = Date.now();
      var secondes = audio.length / RATE;

      // Pas de `X-Filename` : le préflight n'autorise que `Content-Type` et
      // `Authorization`, et le service n'en a pas besoin pour accepter le lot.
      fetch(BASE + CHEMIN_LOTS, {
        method: 'POST', headers: { 'Content-Type': 'audio/wav' }, body: wav(audio)
      }).then(function (r) {
        if (!r.ok && r.status !== 202) throw new Error('asr2 ' + r.status);
        return r.json();
      }).then(function (j) {
        if (!j || !j.job_id) throw new Error('asr2 sans job_id');
        return sonder(gen, j.job_id, t0);
      }).then(function (texte) {
        if (gen !== generation) return;
        var propre = couperBoucle((texte || '').trim());
        debrancher();
        prevenir('onPasse', secondes, Date.now() - t0);
        prevenir('onFinal', propre, secondes);
        setEtat('IDLE');
      }).catch(function (e) {
        if (gen !== generation) return;
        // Soumission bloquée : plutôt que de faire reparler l'utilisateur, on
        // rejoue sur le flux ce qui est déjà enregistré, et on retient la voie.
        if (voieDemandee() === 'auto' && voieRetenue !== 'ws') {
          voieRetenue = 'ws';
          enLots = false;
          rejouerSurFlux(gen, audio);
          return;
        }
        echecService(gen);
      });
    }

    /** Interroge le travail jusqu'à `completed`, puis rend son texte. */
    function sonder(gen, job, t0) {
      var luUnEtat = false;
      return new Promise(function (resoudre, rejeter) {
        (function tour() {
          if (gen !== generation) return;
          if (Date.now() - t0 > TIMEOUT_LOT_MS) { rejeter(new Error('job ' + job + ' toujours en cours')); return; }
          setTimeout(function () {
            if (gen !== generation) return;
            fetch(BASE + '/v3/asr/jobs/' + job).then(function (r) { return r.json(); })
              .then(function (s) {
                luUnEtat = true;
                if (s.status === 'failed') { rejeter(new Error('job ' + job + ' en échec')); return; }
                if (s.status !== 'completed') { tour(); return; }
                fetch(BASE + '/v3/asr/jobs/' + job + '/result')
                  .then(function (r) {
                    if (!r.ok) throw new Error('result ' + r.status);
                    return r.text();
                  }).then(resoudre, rejeter);
              }, function (e) {
                // Illisible ne veut pas dire perdu : cf. GRACE_SONDAGE_MS.
                if (luUnEtat || Date.now() - t0 < GRACE_SONDAGE_MS) { tour(); return; }
                rejeter(e);
              });
          }, SONDAGE_MS);
        })();
      });
    }

    // --- Voie du flux -----------------------------------------------------

    function config() {
      return JSON.stringify({
        type: 'config', language: 'lb',
        chunk_params: {
          periodic_send_interval: CHUNK_INTERVAL_S,
          silence_threshold: CHUNK_SILENCE_S,
          max_chunk_duration: CHUNK_MAX_S
        }
      });
    }

    /**
     * Ouvre la liaison. [apresOuverture] décide ce qui suit : brancher le micro
     * pour une dictée en direct, ou déverser un énoncé déjà enregistré.
     *
     * Les réglages partent avant la première trame : le serveur les applique à
     * ce qu'il reçoit ensuite, pas à ce qu'il a déjà mis de côté.
     */
    function ouvrirSocket(gen, apresOuverture) {
      try { ws = new WebSocket(ENDPOINT_FLUX); } catch (e) { echecService(gen); return; }
      ws.binaryType = 'arraybuffer';

      ws.onopen = function () {
        if (gen !== generation) { try { ws.close(); } catch (e) {} return; }
        ws.send(config());
        apresOuverture();
      };

      ws.onmessage = function (ev) {
        if (gen !== generation || typeof ev.data !== 'string') return;
        traiter(ev.data);
      };
      ws.onerror = function () { if (gen === generation && etat !== 'IDLE') echecService(gen); };
      ws.onclose = function () { if (gen === generation && etat !== 'IDLE') conclure(); };
    }

    /**
     * Rejoue sur le flux un énoncé déjà capté, quand la soumission par lots
     * n'a pas pu être lue.
     *
     * Le découpage du service suit **les échantillons reçus et non l'horloge**
     * — c'est ce qu'a établi `stt/bench/probe_gap.py` : huit secondes de flux
     * suspendu ne produisent aucune hypothèse, huit secondes de silence émis en
     * produisent trois. Déverser l'énoncé d'un trait est donc légitime, et
     * arrive au même découpage qu'une émission en temps réel.
     */
    function rejouerSurFlux(gen, audio) {
      accumule = '';
      ouvrirSocket(gen, function () {
        var pas = Math.round(RATE * BLOC_MS / 1000), i = 0;
        (function pousser() {
          if (gen !== generation || !ws || ws.readyState !== 1) return;
          if (i >= audio.length) {
            ws.send(JSON.stringify({ type: 'stop' }));
            clearTimeout(minuteurFinal);
            minuteurFinal = setTimeout(function () {
              if (etat === 'FINALIZING') conclure();
            }, FINAL_GRACE_MS);
            return;
          }
          ws.send(pcm16(audio.subarray(i, Math.min(audio.length, i + pas))).buffer);
          i += pas;
          setTimeout(pousser, 0);
        })();
      });
    }

    function echecService(gen) {
      if (gen !== generation) return;
      generation++;
      debrancher();
      try { if (ws) ws.close(1000); } catch (e) {}
      ws = null;
      setEtat('IDLE');
      prevenir('onErreur', 'SERVICE');
    }

    function traiter(brut) {
      var m; try { m = JSON.parse(brut); } catch (e) { return; }
      if (m.type === 'transcription') {
        // `accumulated_text` porte tout l'énoncé depuis le début : on remplace
        // en bloc, on ne recolle jamais de fragments. Le texte retenu n'est pas
        // filtré, seulement celui qui est livré — une passe ultérieure peut
        // lever l'ambiguïté d'une boucle naissante.
        accumule = m.accumulated_text || accumule;
        var proc = m.metrics && m.metrics.processing_time;
        prevenir('onPasse', (Date.now() - debutMs) / 1000, Math.round((proc || 0) * 1000));
        var propre = couperBoucle(accumule);
        if (propre) prevenir('onPartiel', propre);
      } else if (m.type === 'recording_stopped') {
        conclure();
      } else if (m.type === 'error') {
        prevenir('onErreur', 'SERVICE');
      }
    }

    function conclure() {
      if (etat === 'IDLE') return;
      clearTimeout(minuteurFinal);
      var texte = couperBoucle(accumule);
      var duree = debutMs ? (Date.now() - debutMs) / 1000 : 0;
      debrancher();
      try { if (ws) ws.close(1000); } catch (e) {}
      ws = null;
      setEtat('IDLE');
      prevenir('onFinal', texte, duree);
    }

    var api = {
      get etat() { return etat; },
      /** Vrai tant que le micro capte ; faux dès « stop ». */
      get actif() { return etat === 'LISTENING' || etat === 'LOADING'; },
      /** Vrai tant que la session n'est pas conclue, finalisation comprise. */
      get occupe() { return etat !== 'IDLE'; },
      /** `'api'` ou `'ws'` : la voie de la session en cours. */
      get voie() { return enLots ? 'api' : 'ws'; },

      start: function () {
        if (etat !== 'IDLE') return;
        var gen = ++generation;
        var demandee = voieDemandee();
        enLots = demandee === 'api' || (demandee === 'auto' && voieRetenue !== 'ws');
        accumule = ''; parle = false; dernierSonMs = 0; plancherBruit = 0; debutMs = 0;
        blocs = []; echantillons = 0; premierSon = -1; dernierSon = -1;
        setEtat('LOADING');

        ouvrirMicro().then(function () {
          if (gen !== generation) { debrancher(); return; }
          if (enLots) {
            // Rien à attendre : on capte, on enverra à la fin.
            brancher();
            debutMs = Date.now();
            setEtat('LISTENING');
          } else {
            // L'audio ne part qu'une fois la connexion établie, jamais avant :
            // capter pendant que la liaison s'établit enregistrerait une amorce
            // que le service ne verrait jamais, et qu'on croirait pourtant dictée.
            ouvrirSocket(gen, function () {
              brancher();
              debutMs = Date.now();
              setEtat('LISTENING');
            });
          }
        }).catch(function (e) {
          if (gen !== generation) return;
          generation++;
          debrancher(); setEtat('IDLE');
          prevenir('onErreur', 'MIC', e && e.name);
        });
      },

      /** Termine la dictée : on envoie ce qui a été dit, puis on conclut. */
      stop: function () {
        if (etat !== 'LISTENING' && etat !== 'LOADING') return;
        var gen = generation;
        debrancher();
        setEtat('FINALIZING');

        if (enLots) {
          var audio = decouperSurLaParole();
          // Rien d'audible : on rend la main sans déranger le service.
          if (!audio.length) { prevenir('onFinal', '', 0); setEtat('IDLE'); return; }
          transcrireLot(gen, audio);
          return;
        }

        if (!ws || ws.readyState !== 1) { conclure(); return; }
        ws.send(JSON.stringify({ type: 'stop' }));
        // Si le dernier segment n'arrive pas, on rend quand même ce qui a été
        // accumulé : mieux vaut un texte partiel qu'un bandeau figé.
        clearTimeout(minuteurFinal);
        minuteurFinal = setTimeout(function () {
          if (etat === 'FINALIZING') conclure();
        }, FINAL_GRACE_MS);
      },

      /** Coupe tout sans rendre de texte : changement de champ, page quittée. */
      cancel: function () {
        if (etat === 'IDLE') return;
        generation++;
        clearTimeout(minuteurFinal);
        debrancher();
        try { if (ws) ws.close(1000); } catch (e) {}
        ws = null; accumule = ''; blocs = []; echantillons = 0;
        setEtat('IDLE');
      }
    };
    return api;
  }

  /** Le navigateur peut-il capter et téléverser du son ? */
  LuxAsrClient.disponible = function () {
    var securise = window.isSecureContext ||
                   location.protocol === 'https:' || location.hostname === 'localhost';
    return !!(securise && window.WebSocket && window.fetch &&
              navigator.mediaDevices && navigator.mediaDevices.getUserMedia);
  };

  LuxAsrClient.couperBoucle = couperBoucle;
  window.LuxAsrClient = LuxAsrClient;
})();
