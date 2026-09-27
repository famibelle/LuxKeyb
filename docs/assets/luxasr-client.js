/*
 * Client LuxASR pour navigateur — portage de LuxAsrSession.kt : le flux est
 * désormais la voie par défaut, les lots ne servent plus qu'à comparer
 * (`?voie=api`).
 *
 * **Le service a changé de moteur le 16 septembre 2026** (`PeterGilles/LuxASRlive`,
 * réponses `engine: "whisperlive_buffer"`) : il redécode l'énoncé non engagé
 * toutes les 0,5 à 1 s et n'engage un mot qu'après l'avoir vu à la même place
 * dans trois hypothèses de suite. `accumulated_text` ne fait donc plus que
 * grandir, et la queue encore instable arrive à part dans `partial_text`.
 *
 * Mesuré le 19 septembre 2026 sur les 22 énoncés de 8 à 22 s du banc du
 * 1er septembre (même audio, même WER infixe) :
 *
 *     flux (WS)     38,3 % → 25,4 %   texte final 0,23 s après l'arrêt
 *     lots (/asr2)  26,8 % → 26,9 %   texte final 1,30 s après l'arrêt
 *
 * Le flux a rattrapé les lots et les dépasse maintenant sur la vitesse, tout
 * en gardant l'aperçu qui se construit pendant qu'on parle — premier aperçu
 * 1,1 s en médiane, là où les lots ne montrent rien avant la fin. C'est
 * pourquoi il est la voie par défaut depuis le 19 septembre 2026 ; l'API par
 * lots reste jouable via `?voie=api`, pour comparer les deux sur la même page.
 *
 * ---------------------------------------------------------------------------
 *
 * **CORS ne concerne plus que la comparaison par lots.** Mesuré le
 * 2 septembre 2026 depuis `https://famibelle.github.io` : les réponses
 * `202 ACCEPTED` de `POST /asr2` ne portent aucun `Access-Control-Allow-Origin`,
 * contrairement aux `200 OK` qui en portent un — le navigateur bloque donc la
 * lecture du `job_id`. C'est resté vrai, mais cela ne touche plus la visite
 * par défaut : le flux passe par un WebSocket, que cette politique ne limite
 * pas. Un essai explicite en lots (`?voie=api`) qui échoue rejoue l'audio déjà
 * enregistré sur le flux plutôt que de faire reparler l'utilisateur.
 *
 * `?voie=ws` force le flux sans ambiguïté ; c'est déjà le comportement par
 * défaut, l'option existe pour l'exclusion mutuelle avec `?voie=api`.
 *
 * ---------------------------------------------------------------------------
 *
 * **Le service traduit aussi, nativement, sur la voie du flux.** Repéré dans
 * leur propre client (`rt.js`, capté depuis `luxasr.uni.lu` le
 * 19 septembre 2026), qui envoie `translation_enabled`/`translation_target`
 * dans le `config` et affiche le résultat dans un second panneau. Vérifié en
 * direct le 26 septembre 2026 : de vraies traductions françaises reviennent,
 * une phrase entière à la fois, dans des messages `type: "translation"`
 * séparés — le `translation` que portent les messages `transcription` n'est
 * qu'un état (`enabled`, `pending_source`), jamais le texte. Rien de tout ceci
 * n'existe côté Android : c'est une capacité du service, pas du protocole que
 * `LuxAsrSession.kt` utilise, et elle est **désactivée par défaut** ici aussi
 * (`options.traduction`) pour ne pas faire payer à leur service un aller-
 * retour que la plupart des appelants n'utilisent pas.
 *
 * ---------------------------------------------------------------------------
 *
 * Ne dessine rien : il rend les mêmes évènements que `SttSession.Listener`
 * côté Android, plus `onTraduction` qui n'a pas d'équivalent là-bas.
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
 * Ce qui est identique et doit le rester : le silence de 5 s termine l'énoncé
 * côté client — le service, lui, ne décode plus le silence depuis son moteur
 * du 16 septembre et n'a plus besoin qu'on le lui cache —, un énoncé est
 * plafonné à 90 s, et la queue répétitive de whisper est coupée à l'affichage,
 * jamais dans l'état conservé.
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

  var BLOC_MS = 160;              // taille d'un bloc audio, comme AudioRecorder
  var FINAL_GRACE_MS = 4000;      // attente du dernier segment après « stop »
  var SILENCE_HANGOVER_MS = 5000; // silence qui termine l'énoncé
  var SPEECH_FLOOR_RMS = 0.012;   // plancher absolu sous lequel rien n'est parole
  var SPEECH_MARGIN = 2.5;        // marge au-dessus du bruit ambiant
  var NOISE_RISE = 0.02;          // vitesse de remontée du plancher de bruit
  var MAX_UTTERANCE_MS = 90000;   // garde-fou si le silence n'arrive jamais
  var LEVEL_FULL_SCALE = 0.18;    // même échelle que le micro du clavier

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
   * `onPasse(secondesAudio, msService)`, `onErreur('MIC' | 'SERVICE')`,
   * `onTraduction(texteAccumule)`.
   *
   * `onPartiel` ne se produit que sur la voie du flux : par lots, il n'y a
   * qu'un `onFinal`. `onTraduction` de même, et seulement si `options.traduction`
   * est vrai : le service traduit une phrase à la fois, quelques secondes
   * après l'avoir engagée (0,7 à 2,2 s mesurés le 26 septembre 2026), jamais
   * mot à mot — c'est un second flux, pas un sous-titrage de l'aperçu.
   *
   * [options.traduction] active la traduction en direct (désactivée par
   * défaut : elle ajoute un aller-retour côté service que la plupart des
   * sessions n'utilisent pas). [options.langueCible] choisit la langue
   * (`'fr'` par défaut) parmi celles que le service accepte.
   */
  function LuxAsrClient(ecouteur, options) {
    options = options || {};
    var traductionActive = !!options.traduction;
    var langueCible = options.langueCible || 'fr';

    var ws = null, ctx = null, flux = null, source = null, noeud = null, puits = null;
    var moduleCharge = false;
    var etat = 'IDLE', generation = 0, debutMs = 0, minuteurFinal = null;
    /** Texte engagé par le service (flux) : il ne fait que grandir. */
    var accumule = '';
    /** Queue encore instable du flux, remplacée à chaque passe. */
    var attente = '';
    /** Traduction accumulée, phrase par phrase (voie du flux, si demandée). */
    var traduction = '';
    /** Un essai en lots déjà retombé sur le flux une fois pour cette dictée. */
    var essayeFlux = false;
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
        // Soumission bloquée (typiquement CORS sur le 202) : plutôt que de
        // faire reparler l'utilisateur, on rejoue une seule fois sur le flux
        // ce qui est déjà enregistré. Ne se produit qu'avec `?voie=api`
        // explicite, puisque le flux est désormais la voie par défaut.
        if (!essayeFlux) {
          essayeFlux = true;
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
      // La langue est le seul réglage qui nous concerne toujours ; l'ancien
      // `chunk_params` n'est plus lu depuis que le service ne découpe plus
      // en morceaux (moteur du 16 septembre 2026). `translation_enabled` et
      // `translation_target` sont ceux de leur propre client (`rt.js`, capté
      // le 19 septembre 2026) : vérifiés en direct le 26 septembre 2026,
      // ils traduisent réellement, phrase par phrase.
      var c = { type: 'config', language: 'lb' };
      if (traductionActive) {
        c.translation_enabled = true;
        c.translation_target = langueCible;
      }
      return JSON.stringify(c);
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
      accumule = ''; attente = ''; traduction = '';
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

    /** Engagé puis instable, ce que l'utilisateur doit voir à cet instant. */
    function texteVisible() {
      return attente ? (accumule + ' ' + attente).trim() : accumule;
    }

    function traiter(brut) {
      var m; try { m = JSON.parse(brut); } catch (e) { return; }
      if (m.type === 'transcription') {
        // Deux champs, deux natures : `accumulated_text` est engagé et ne fait
        // que grandir, `partial_text` est la queue que la passe suivante peut
        // encore réécrire. On les montre ensemble, remplacés en bloc. Le texte
        // livré est filtré, celui qu'on retient ne l'est pas — la queue d'une
        // passe ultérieure peut très bien lever l'ambiguïté d'une boucle
        // naissante.
        accumule = m.accumulated_text || accumule;
        attente = m.partial_text || '';
        var proc = m.metrics && m.metrics.processing_time;
        prevenir('onPasse', (Date.now() - debutMs) / 1000, Math.round((proc || 0) * 1000));
        var propre = couperBoucle(texteVisible());
        if (propre) prevenir('onPartiel', propre);
      } else if (m.type === 'translation') {
        // Un message à part, indépendant des passes de `transcription` : le
        // champ `translation` qu'elles portent n'est qu'un état
        // (`enabled`, `pending_source`), jamais le texte traduit lui-même.
        // Émis phrase par phrase, avec un délai propre (0,7 à 2,2 s mesurés) :
        // ce n'est pas un sous-titrage synchrone de l'aperçu.
        if (m.success === false) return;
        traduction = typeof m.accumulated_translation === 'string'
          ? m.accumulated_translation : traduction;
        if (traduction) prevenir('onTraduction', traduction);
      } else if (m.type === 'recording_stopped') {
        conclure();
      } else if (m.type === 'error') {
        prevenir('onErreur', 'SERVICE');
      }
    }

    function conclure() {
      if (etat === 'IDLE') return;
      clearTimeout(minuteurFinal);
      // Après « stop », le service engage toute la queue avant de répondre
      // `recording_stopped`, qui n'en laisse donc plus. Si c'est le délai de
      // grâce qui conclut, on garde la queue : c'est ce qui était affiché.
      var texte = couperBoucle(texteVisible());
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
        // Le flux est la voie par défaut depuis le 19 septembre 2026 : il
        // égale ou dépasse les lots en justesse et en délai, et donne en plus
        // l'aperçu qui se construit pendant qu'on parle. Les lots ne restent
        // joignables que sur demande explicite, pour comparer.
        enLots = demandee === 'api';
        accumule = ''; attente = ''; traduction = ''; essayeFlux = false;
        parle = false; dernierSonMs = 0; plancherBruit = 0; debutMs = 0;
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
        ws = null; accumule = ''; attente = ''; traduction = ''; blocs = []; echantillons = 0;
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
