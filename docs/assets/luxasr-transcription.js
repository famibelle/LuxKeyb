/*
 * Transcription d'un enregistrement par l'API par lots de LuxASR, locuteurs
 * séparés — la bibliothèque de la rubrique Labs « Transcription ».
 *
 * La référence est la skill `.claude/skills/transcription/SKILL.md` : on n'y
 * revient ici que sur ce qui explique le code.
 *
 * Ne dessine rien, comme `luxasr-client.js` : la page reçoit des évènements et
 * un document normalisé, et décide seule de leur affichage. C'est aussi ce qui
 * permet de tester le normaliseur sous Node
 * (`node docs/scripts/test_luxasr_transcription.js`).
 *
 * ---------------------------------------------------------------------------
 *
 * **Le chemin** : les octets bruts du fichier en `POST /asr2`, jamais en
 * multipart (l'ancien flux, déprécié), puis `GET /v3/asr/jobs/{id}` jusqu'à
 * `completed`, puis `/result`. `diarization=Enabled`, `outfmt=json` : le JSON
 * est le seul format qui porte à la fois les temps et les locuteurs.
 *
 * **Le résultat ne se lit qu'une fois** : le service l'efface dès qu'il a été
 * servi (documenté). On ne le redemande donc jamais, et la page en garde la
 * réponse brute.
 *
 * **CORS** : relevé le 2 septembre puis le 29 octobre 2026, le `202 Accepted`
 * de `/asr2` ne porte pas d'`Access-Control-Allow-Origin`. Le navigateur envoie la requête, le
 * service crée le travail, mais la page ne peut pas lire son `job_id`. L'envoi
 * passe donc par `XMLHttpRequest`, dont les évènements d'envoi permettent de
 * séparer deux échecs qui rendent tous deux `status 0` : fichier entièrement
 * parti puis réponse illisible (`CORS`), ou envoi coupé (`RESEAU`). Sur `CORS`
 * on ne réessaie jamais : chaque essai ferait travailler leur serveur pour
 * rien.
 *
 * **Le schéma du JSON n'est pas documenté**, mais relevé le 29 octobre 2026 :
 * un tableau de segments `{speaker, start, end, text, words}` à la racine, des
 * mots `{start, end, word, probability, speaker}`. Le normaliseur accepte cette
 * forme, les variantes de WhisperX, ainsi que SRT, VTT et le texte à préfixes
 * de locuteur. Ce qu'il ne reconnaît pas, il le dit (`format: 'inconnu'`) au
 * lieu de deviner : la page montre alors la réponse brute.
 */
(function (racine) {
  'use strict';

  /* `LUXASR_BASE` ne sert qu'aux tests, qui jouent le service sur une autre
     origine locale pour reproduire CORS au réel. */
  var BASE = racine.LUXASR_BASE || 'https://luxasr.uni.lu';

  /* Nos plafonds. Le site du service accepte 500 Mo et ne fixe pas de durée ;
     son API est ouverte « for limited access » : on reste bien en deçà. */
  var TAILLE_MAX = 100 * 1024 * 1024;
  var DUREE_MAX_S = 90 * 60;

  /* Sondage poli, calqué sur leur client officiel (`luxasr_transcribe.py`) :
     2 s, puis 1,2 fois plus à chaque tour, plafonné ici à 10 s. La dictée
     sonde à 200 ms pour un énoncé de 10 s ; un fichier d'une heure ne le
     justifie pas. */
  var SONDAGE_INITIAL_MS = 2000;
  var SONDAGE_FACTEUR = 1.2;
  var SONDAGE_MAX_MS = 10000;
  var ATTENTE_MAX_MS = 60 * 60 * 1000;

  /* Sondages illisibles d'affilée avant de conclure à une coupure. */
  var ECHECS_SONDAGE_MAX = 6;

  // --- Type de contenu ----------------------------------------------------

  /* Le service veut un `Content-Type: audio/*` ou `video/*` et un vrai
     conteneur avec une piste son (documenté). Une vidéo part donc telle quelle,
     sans être décodée dans le navigateur, ce qui prendrait des centaines de Mo
     de mémoire pour une heure de film. */
  var PAR_EXTENSION = {
    wav: 'audio/wav', mp3: 'audio/mpeg', mp2: 'audio/mpeg', m4a: 'audio/mp4',
    aac: 'audio/aac', ogg: 'audio/ogg', oga: 'audio/ogg', opus: 'audio/ogg',
    flac: 'audio/flac', weba: 'audio/webm', wma: 'audio/x-ms-wma',
    mp4: 'video/mp4', mov: 'video/quicktime', qta: 'video/quicktime',
    webm: 'video/webm', wmv: 'video/x-ms-wmv'
  };
  var SYNONYMES = {
    'audio/x-wav': 'audio/wav', 'audio/wave': 'audio/wav', 'audio/vnd.wave': 'audio/wav',
    'audio/x-m4a': 'audio/mp4', 'audio/m4a': 'audio/mp4', 'audio/mp3': 'audio/mpeg',
    'audio/x-flac': 'audio/flac'
  };

  function extension(nom) {
    var m = /\.([a-z0-9]+)$/i.exec(nom || '');
    return m ? m[1].toLowerCase() : '';
  }

  /** Le `Content-Type` à annoncer, ou `null` si le fichier n'est pas un média connu. */
  function typeAudio(fichier) {
    var t = (fichier.type || '').toLowerCase().split(';')[0];
    if (SYNONYMES[t]) return SYNONYMES[t];
    if (t.indexOf('audio/') === 0 || t.indexOf('video/') === 0) return t;
    return PAR_EXTENSION[extension(fichier.name)] || null;
  }

  /** Un résultat déjà transcrit, plutôt qu'un enregistrement ? */
  function estResultat(fichier) {
    var e = extension(fichier.name);
    return e === 'json' || e === 'srt' || e === 'vtt' || e === 'txt';
  }

  // --- Client /asr2 -------------------------------------------------------

  /**
   * Soumet [fichier] et suit le travail jusqu'au résultat.
   *
   * [ecouteur] reçoit :
   * - `onEtat('ENVOI', fraction|null)`, puis `onEtat('FILE', {position, eta})`,
   *   `onEtat('TRAITEMENT', {eta})`, et enfin `onResultat(texteBrut, jobId)` ;
   *   `eta` est l'estimation du service en secondes, quand il en donne une ;
   * - ou `onErreur(code, detail)`, `code` parmi `CORS`, `RESEAU`, `REFUS`
   *   (détail : `{status, message}`), `ECHEC`, `INTROUVABLE`, `DELAI`,
   *   `SANS_JOB`.
   *
   * [options] : `diarisation` (vrai par défaut), `locuteurs` (nombre de voix,
   * 1 à 10, sinon automatique), `prompt`.
   * Rend `{ annuler() }` : on cesse d'envoyer ou de sonder. Le service n'a pas
   * d'annulation publique ; un travail déjà soumis s'y termine de lui-même.
   */
  function transcrire(fichier, options, ecouteur) {
    options = options || {};
    var fini = false, xhr = null, minuteur = null;
    var t0 = Date.now(), echecs = 0, delai = SONDAGE_INITIAL_MS;

    function prevenir(nom, a, b) {
      if (fini && nom !== 'onResultat' && nom !== 'onErreur') return;
      var f = ecouteur && ecouteur[nom];
      if (f) f.call(ecouteur, a, b);
    }
    function terminer(nom, a, b) {
      if (fini) return;
      prevenir(nom, a, b);
      fini = true;
    }

    var params = ['language=lb',
                  'diarization=' + (options.diarisation === false ? 'Disabled' : 'Enabled'),
                  'outfmt=json'];
    // `num_speakers` n'est pas documenté pour l'API : c'est ce qu'envoie leur
    // propre formulaire au même `/asr2` (relevé dans luxasr_main.js), et
    // seulement avec la diarisation, comme ici.
    var n = parseInt(options.locuteurs, 10);
    if (options.diarisation !== false && n >= 1 && n <= 10) params.push('num_speakers=' + n);
    // Au-delà de 900 caractères, le service ignore la suite (documenté).
    if (options.prompt) params.push('prompt=' + encodeURIComponent(options.prompt.slice(0, 900)));

    xhr = new XMLHttpRequest();
    var envoye = false, dernierProgres = 0;
    xhr.open('POST', BASE + '/asr2?' + params.join('&'));
    // Pas de `X-Filename` : le préflight n'autorise que `Content-Type` et
    // `Authorization`.
    xhr.setRequestHeader('Content-Type', typeAudio(fichier) || 'audio/wav');
    xhr.upload.onprogress = function (e) {
      dernierProgres = e.lengthComputable && e.total ? e.loaded / e.total : dernierProgres;
      prevenir('onEtat', 'ENVOI', e.lengthComputable ? dernierProgres : null);
    };
    xhr.upload.onload = function () { envoye = true; prevenir('onEtat', 'ENVOI', 1); };
    xhr.onload = function () {
      if (fini) return;
      var corps = null;
      try { corps = JSON.parse(xhr.responseText); } catch (e) {}
      if (xhr.status !== 200 && xhr.status !== 202) {
        terminer('onErreur', 'REFUS', { status: xhr.status, message: messageDe(corps, xhr.responseText) });
        return;
      }
      if (!corps || !corps.job_id) { terminer('onErreur', 'SANS_JOB', xhr.responseText.slice(0, 300)); return; }
      prevenir('onEtat', 'FILE', {});
      sonder(String(corps.job_id));
    };
    xhr.onerror = function () {
      // Même `status 0` dans les deux cas : seul l'envoi complet distingue
      // une réponse que CORS rend illisible d'une connexion coupée.
      terminer('onErreur', envoye || dernierProgres >= 0.999 ? 'CORS' : 'RESEAU');
    };
    prevenir('onEtat', 'ENVOI', 0);
    xhr.send(fichier);

    function sonder(job) {
      if (Date.now() - t0 > ATTENTE_MAX_MS) { terminer('onErreur', 'DELAI'); return; }
      minuteur = setTimeout(function () {
        if (fini) return;
        fetch(BASE + '/v3/asr/jobs/' + encodeURIComponent(job), { cache: 'no-store' })
          .then(function (r) { return r.json(); })
          .then(function (s) {
            if (fini) return;
            echecs = 0;
            var etat = s && s.status;
            var eta = nombre(s, ['eta_seconds']);
            if (etat === 'completed') { recuperer(job); return; }
            if (etat === 'failed') { terminer('onErreur', 'ECHEC', messageDe(s, '')); return; }
            // Identifiant inconnu, expiré ou supprimé : 404 `not_found`.
            if (etat === 'not_found') { terminer('onErreur', 'INTROUVABLE'); return; }
            if (etat === 'processing') prevenir('onEtat', 'TRAITEMENT', { eta: eta });
            else prevenir('onEtat', 'FILE', { position: nombre(s, ['queue_position', 'position']), eta: eta });
            sonder(job);
          }, function () {
            if (fini) return;
            if (++echecs >= ECHECS_SONDAGE_MAX) { terminer('onErreur', 'RESEAU'); return; }
            sonder(job);
          });
      }, delai);
      delai = Math.min(SONDAGE_MAX_MS, delai * SONDAGE_FACTEUR);
    }

    /* Une seule lecture : le service efface le résultat dès qu'il l'a servi. */
    function recuperer(job) {
      fetch(BASE + '/v3/asr/jobs/' + encodeURIComponent(job) + '/result', { cache: 'no-store' })
        .then(function (r) {
          if (!r.ok) throw new Error('result ' + r.status);
          return r.text();
        })
        .then(function (texte) { terminer('onResultat', texte, job); },
              function () { terminer('onErreur', 'RESEAU'); });
    }

    return {
      annuler: function () {
        if (fini) return;
        fini = true;
        clearTimeout(minuteur);
        try { if (xhr && xhr.readyState !== 4) xhr.abort(); } catch (e) {}
      }
    };
  }

  function messageDe(corps, brut) {
    if (corps && typeof corps === 'object') {
      var m = corps.error || corps.message || corps.detail || corps.reason;
      if (typeof m === 'string') return m.slice(0, 300);
    }
    return (brut || '').replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 300);
  }

  function nombre(o, cles) {
    for (var i = 0; i < cles.length; i++) {
      var v = o && o[cles[i]];
      if (typeof v === 'number' && isFinite(v)) return v;
    }
    return null;
  }

  // --- Normalisation ------------------------------------------------------

  /* Préfixe de locuteur en tête de réplique : `SPEAKER_00:`, `[SPEAKER_00]`,
     `Speaker 1:`, `Spriecher 1:`, `Locuteur 1 :`, `Intervenant 1 :`. */
  var PREFIXE = /^\s*\[?\s*((?:speaker|spriecher|sprecher|locuteur|intervenant|spk)[ _-]?\d+)\s*\]?\s*[:：\-–—]?\s*/i;

  function premier(o, cles) {
    for (var i = 0; i < cles.length; i++) {
      var v = o[cles[i]];
      if (v !== undefined && v !== null && v !== '') return v;
    }
    return null;
  }

  /** Nombre de secondes, depuis un nombre ou `hh:mm:ss.mmm` / `mm:ss,mmm`. */
  function secondes(v) {
    if (typeof v === 'number') return isFinite(v) ? v : null;
    if (typeof v !== 'string') return null;
    var s = v.trim().replace(',', '.');
    if (/^\d+(\.\d+)?$/.test(s)) return parseFloat(s);
    var m = /^(?:(\d+):)?(\d{1,2}):(\d{1,2}(?:\.\d+)?)$/.exec(s);
    return m ? (+(m[1] || 0)) * 3600 + (+m[2]) * 60 + parseFloat(m[3]) : null;
  }

  function nettoyer(t) { return String(t == null ? '' : t).replace(/\s+/g, ' ').trim(); }

  /** Recolle des mots à la Whisper, dont l'espace est souvent dans le mot. */
  function recoller(mots) {
    var t = '';
    mots.forEach(function (w) {
      var m = typeof w === 'string' ? w : premier(w, ['word', 'text', 'token']);
      if (m == null) return;
      m = String(m);
      t += (/^\s/.test(m) || !t) ? m : ' ' + m;
    });
    return nettoyer(t);
  }

  function libelle(v) { return v === null || v === undefined || v === '' ? null : String(v); }

  /*
   * Découpage en phrases d'un segment dont les mots sont horodatés. Relevé le
   * 29 octobre 2026 : LuxASR rend deux minutes d'une même voix en un seul
   * segment, soit un sous-titre de 2 249 caractères et une surbrillance qui
   * couvre tout. On ferme une phrase sur un point quand elle a assez duré, sur
   * une virgule quand elle devient longue, et n'importe où au-delà. Les seuils
   * visent un sous-titre : deux lignes de 42 caractères, le `maxlen` du service.
   * La lecture n'en souffre pas, la page recolle les phrases d'un même tour.
   */
  var PHRASE_MIN_S = 2, PHRASE_MIN_CAR = 25;
  var PHRASE_SOUPLE_S = 4, PHRASE_SOUPLE_CAR = 60;
  var PHRASE_MAX_S = 8, PHRASE_MAX_CAR = 100;
  /* Écart toléré entre deux mots dans le texte (espaces, ponctuation isolée). */
  var ALIGNEMENT_JEU = 12;

  function motDe(w) { return nettoyer(premier(w, ['word', 'text', 'token'])); }

  /**
   * Place chaque mot dans le texte du segment, pour couper ce texte-là et non
   * des mots recollés : Whisper sépare les élisions (« d' » puis « Leit »), et
   * les recoller écrirait « d' Leit » là où le service a écrit « d'Leit ».
   * Rend la position de début de chaque mot, ou `null` si l'un d'eux manque.
   */
  function aligner(texte, mots) {
    var pos = 0, debuts = [];
    for (var i = 0; i < mots.length; i++) {
      var m = motDe(mots[i]);
      var k = m ? texte.indexOf(m, pos) : pos;
      if (k < 0 || k - pos > ALIGNEMENT_JEU) return null;
      debuts.push(k);
      pos = k + m.length;
    }
    return debuts;
  }

  /** Un segment brut → un ou plusieurs segments normalisés. */
  function segmentsDe(el) {
    if (typeof el === 'string') el = { text: el };
    if (!el || typeof el !== 'object') return [];
    var loc = libelle(premier(el, ['speaker', 'spk', 'speaker_id', 'speaker_label', 'label']));
    var debut = secondes(premier(el, ['start', 'begin', 'start_time', 'from', 'startTime']));
    var fin = secondes(premier(el, ['end', 'stop', 'end_time', 'to', 'endTime']));
    var mots = Array.isArray(el.words) ? el.words.filter(function (w) { return w && typeof w === 'object'; }) : [];
    var texte = premier(el, ['text', 'transcript', 'sentence', 'content']);
    texte = texte != null ? nettoyer(texte) : recoller(mots);

    var parMot = mots.some(function (w) { return libelle(premier(w, ['speaker', 'spk', 'speaker_id'])); });
    if (!loc && !parMot) {
      var p = PREFIXE.exec(texte);
      if (p) { loc = p[1]; texte = texte.slice(p[0].length); }
    }
    if (!mots.length) return texte ? [{ debut: debut, fin: fin, locuteur: loc, texte: texte }] : [];

    // Les mots portent le locuteur (WhisperX) : un segment peut en couvrir
    // deux. On coupe à chaque changement — un mot sans locuteur suit le
    // précédent —, et en phrases à l'intérieur d'une même voix.
    var groupes = [], g = null;
    // `suite` : le groupe commence au milieu d'une phrase, après une coupe sur
    // une virgule ou en force. Son point final le ferme alors toujours, même
    // court ; sinon la phrase suivante s'y accrocherait.
    mots.forEach(function (w, i) {
      var l = libelle(premier(w, ['speaker', 'spk', 'speaker_id'])) || (g ? g.loc : loc);
      var a = secondes(premier(w, ['start', 'begin']));
      var b = secondes(premier(w, ['end', 'stop']));
      var m = motDe(w);
      if (!g || g.loc !== l || g.ferme) {
        g = { loc: l, i0: i, debut: a, fin: b, car: 0, ferme: false,
              suite: !!(g && g.ferme && g.loc === l && !g.point) };
        groupes.push(g);
      } else {
        if (g.debut == null) g.debut = a;
        if (b != null) g.fin = b;
      }
      g.i1 = i;
      g.car += m.length + 1;
      var duree = g.debut != null && g.fin != null ? g.fin - g.debut : 0;
      g.point = /[.?!…]["»”)]?$/.test(m);
      g.ferme = (g.point && (g.suite || duree >= PHRASE_MIN_S || g.car >= PHRASE_MIN_CAR)) ||
                (/[,;:]$/.test(m) && (duree >= PHRASE_SOUPLE_S || g.car >= PHRASE_SOUPLE_CAR)) ||
                duree >= PHRASE_MAX_S || g.car >= PHRASE_MAX_CAR;
    });

    var debuts = aligner(texte, mots);
    return groupes.map(function (g, n) {
      var dernier = n === groupes.length - 1;
      // Le texte entre deux mots revient au groupe qui précède : rien ne se
      // perd, ni ponctuation ni élision.
      var t = debuts ? nettoyer(texte.slice(n === 0 ? 0 : debuts[g.i0],
                                            dernier ? texte.length : debuts[groupes[n + 1].i0]))
                     : recoller(mots.slice(g.i0, g.i1 + 1));
      return { debut: g.debut != null ? g.debut : (n === 0 ? debut : null),
               fin: g.fin != null ? g.fin : (dernier ? fin : null),
               locuteur: g.loc, texte: t };
    }).filter(function (sg) { return sg.texte; });
  }

  var CLES_LISTE = ['segments', 'result', 'results', 'transcription', 'transcript',
                    'utterances', 'chunks', 'data'];

  /** Le tableau de segments d'une réponse JSON, à un niveau de profondeur près. */
  function trouverListe(o, profondeur) {
    if (Array.isArray(o)) {
      return o.length && o.every(function (e) { return e && typeof e === 'object'; }) ? o : null;
    }
    if (!o || typeof o !== 'object' || profondeur > 2) return null;
    for (var i = 0; i < CLES_LISTE.length; i++) {
      var v = o[CLES_LISTE[i]];
      var l = v && typeof v === 'object' ? trouverListe(v, profondeur + 1) : null;
      if (l) return l;
    }
    return null;
  }

  function depuisJson(o) {
    // Réponse multi-format (`outfmt=json,srt`) : une clé par format.
    if (o && !Array.isArray(o) && typeof o === 'object' && o.json != null) {
      var j = o.json;
      if (typeof j === 'string') { try { j = JSON.parse(j); } catch (e) { return depuisTexteBrut(j); } }
      return depuisJson(j);
    }
    var liste = trouverListe(o, 0);
    if (liste) {
      var segs = [];
      liste.forEach(function (el) { segs = segs.concat(segmentsDe(el)); });
      return { segments: segs, format: 'json' };
    }
    var texte = o && typeof o === 'object' ? premier(o, ['text', 'transcription', 'transcript']) : null;
    if (typeof texte === 'string') return depuisTexte(texte);
    if (o && typeof o === 'object' && typeof o.srt === 'string') return depuisTexteBrut(o.srt);
    return { segments: [], format: 'inconnu' };
  }

  var FLECHE = /^\s*(\d{1,2}:)?\d{1,2}:\d{1,2}[.,]\d{1,3}\s*-->\s*(\d{1,2}:)?\d{1,2}:\d{1,2}[.,]\d{1,3}/;

  /** SRT et VTT : des blocs séparés par une ligne vide, une ligne de temps. */
  function depuisSousTitres(texte, vtt) {
    var segs = [];
    texte.replace(/\r\n?/g, '\n').split(/\n\s*\n/).forEach(function (bloc) {
      var lignes = bloc.split('\n');
      var k = 0;
      while (k < lignes.length && !FLECHE.test(lignes[k])) k++;
      if (k >= lignes.length) return;   // en-tête WEBVTT, NOTE, STYLE, numéro seul
      var bornes = lignes[k].split('-->');
      var corps = lignes.slice(k + 1).join(' ');
      var loc = null;
      if (vtt) {
        var v = /<v(?:\.[^\s>]*)?\s+([^>]+)>/.exec(corps);
        if (v) loc = nettoyer(v[1]);
        corps = corps.replace(/<[^>]+>/g, '').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');
      }
      corps = nettoyer(corps);
      if (!loc) {
        var p = PREFIXE.exec(corps);
        if (p) { loc = p[1]; corps = corps.slice(p[0].length); }
      }
      if (corps) {
        segs.push({ debut: secondes(bornes[0].trim()),
                    fin: secondes(bornes[1].trim().split(/\s+/)[0]),
                    locuteur: loc, texte: corps });
      }
    });
    return { segments: vtt ? segs : nommer(segs), format: vtt ? 'vtt' : 'srt' };
  }

  /* Ligne de texte à la Whisper : `[00:01.000 --> 00:04.000] texte`, ou notre
     propre export TXT : `[0:01] Nom : texte`. */
  var TEMPS_LIGNE = /^\s*\[\s*((?:\d+:)?\d{1,2}:\d{1,2}(?:[.,]\d+)?)\s*(?:-->\s*((?:\d+:)?\d{1,2}:\d{1,2}(?:[.,]\d+)?)\s*)?\]\s*/;

  /* `Nom : texte` avec un nom quelconque (« Anna », « Dr. Weber ») : trop
     ambigu ligne à ligne, on ne le retient que s'il est régulier sur tout le
     document — c'est le cas des exports SRT et TXT après renommage. */
  var NOM_LIBRE = /^([^\s:.!?,;][^:.!?;]{0,38}?)\s?:\s+/;

  function nommer(segs) {
    var libres = segs.filter(function (s) { return !s.locuteur; });
    var trouves = libres.map(function (s) {
      var m = NOM_LIBRE.exec(s.texte);
      return m && m[1].trim().split(/\s+/).length <= 4 ? m : null;
    });
    var n = trouves.filter(Boolean).length;
    var distincts = {};
    trouves.forEach(function (m) { if (m) distincts[m[1].trim()] = true; });
    if (!n || n < libres.length * 0.6 || Object.keys(distincts).length > 12) return segs;
    libres.forEach(function (s, i) {
      var m = trouves[i];
      if (m) { s.locuteur = m[1].trim(); s.texte = s.texte.slice(m[0].length); }
    });
    return segs;
  }

  /**
   * Texte ligne à ligne. Une ligne à préfixe de locuteur ouvre une réplique ;
   * les lignes suivantes sans préfixe la prolongent. Sans aucun locuteur, un
   * paragraphe (séparé par une ligne vide) fait un segment.
   */
  function depuisTexte(texte) {
    var segs = [], courant = null;
    texte.replace(/\r\n?/g, '\n').split('\n').forEach(function (ligne) {
      if (!ligne.trim()) { if (courant && !courant.locuteur) courant = null; return; }
      var debut = null, fin = null, t = TEMPS_LIGNE.exec(ligne);
      if (t) { debut = secondes(t[1]); fin = t[2] ? secondes(t[2]) : null; ligne = ligne.slice(t[0].length); }
      var p = PREFIXE.exec(ligne);
      if (p || t || !courant) {
        courant = { debut: debut, fin: fin, locuteur: p ? p[1] : null,
                    texte: nettoyer(p ? ligne.slice(p[0].length) : ligne) };
        segs.push(courant);
      } else {
        courant.texte = nettoyer(courant.texte + ' ' + ligne);
      }
    });
    return { segments: nommer(segs.filter(function (s) { return s.texte; })), format: 'text' };
  }

  function depuisTexteBrut(texte) {
    var debut = texte.replace(/^﻿/, '').trimStart();
    if (/^WEBVTT/.test(debut)) return depuisSousTitres(debut, true);
    if (/^\d+\s*\n/.test(debut) && FLECHE.test(debut.split('\n')[1] || '')) return depuisSousTitres(debut, false);
    if (FLECHE.test(debut.split('\n')[0])) return depuisSousTitres(debut, false);
    return depuisTexte(debut);
  }

  /**
   * N'importe quelle réponse du service (ou fichier qu'il a produit) → un
   * document : `{ segments, locuteurs, horodate, diarise, format }`.
   *
   * `locuteurs` liste les libellés bruts dans l'ordre d'apparition : la page
   * les présente « Locuteur 1, 2… » et laisse les renommer.
   */
  function normaliser(brut) {
    var doc;
    if (brut && typeof brut === 'object') {
      doc = depuisJson(brut);
    } else {
      var s = String(brut == null ? '' : brut).replace(/^﻿/, '');
      var o, json = false;
      if (/^\s*[[{]/.test(s)) { try { o = JSON.parse(s); json = true; } catch (e) {} }
      doc = json ? depuisJson(o) : (s.trim() ? depuisTexteBrut(s) : { segments: [], format: 'text' });
    }
    var vus = {}, locuteurs = [];
    doc.segments.forEach(function (sg) {
      if (sg.locuteur && !vus[sg.locuteur]) { vus[sg.locuteur] = true; locuteurs.push(sg.locuteur); }
    });
    doc.locuteurs = locuteurs;
    doc.horodate = doc.segments.some(function (sg) { return sg.debut != null; });
    doc.diarise = locuteurs.length > 0;
    return doc;
  }

  /* Un tour plus long est coupé en paragraphes, chacun avec son horodatage :
     deux minutes d'un seul tenant ne se lisent ni ne se parcourent. La coupe
     attend un début de phrase, sauf au-delà de TOUR_FORCE_S. */
  var TOUR_MAX_S = 45, TOUR_FORCE_S = 75;

  /**
   * Les segments regroupés pour la lecture : les segments consécutifs d'un
   * même locuteur forment un tour de parole. Sans locuteurs, on coupe en
   * paragraphes sur les silences de plus de 2 s. Dans les deux cas, un
   * paragraphe passé TOUR_MAX_S s'arrête à la fin de phrase suivante.
   */
  function tours(doc) {
    var res = [], courant = null;
    doc.segments.forEach(function (sg, i) {
      var precedent = doc.segments[i - 1];
      var coupe = !courant ||
        (doc.diarise ? sg.locuteur !== courant.locuteur
                     : !(precedent && precedent.fin != null && sg.debut != null && sg.debut - precedent.fin < 2)) ||
        (courant.debut != null && sg.debut != null &&
         (sg.debut - courant.debut >= TOUR_FORCE_S ||
          (sg.debut - courant.debut >= TOUR_MAX_S && /[.?!…]["»”)]?$/.test(precedent.texte))));
      if (coupe) { courant = { locuteur: sg.locuteur, debut: sg.debut, indices: [] }; res.push(courant); }
      courant.indices.push(i);
    });
    return res;
  }

  // --- Exports ------------------------------------------------------------

  function deuxChiffres(n) { return (n < 10 ? '0' : '') + n; }

  /** `1:02:03` au-delà d'une heure, `2:03` en deçà : pour l'affichage. */
  function horloge(s) {
    s = Math.max(0, Math.floor(s || 0));
    var h = Math.floor(s / 3600), m = Math.floor(s / 60) % 60, r = s % 60;
    return (h ? h + ':' + deuxChiffres(m) : m) + ':' + deuxChiffres(r);
  }

  function horodatage(s, sep) {
    var ms = Math.max(0, Math.round((s || 0) * 1000));
    var h = Math.floor(ms / 3600000), m = Math.floor(ms / 60000) % 60;
    var r = Math.floor(ms / 1000) % 60, f = ms % 1000;
    return deuxChiffres(h) + ':' + deuxChiffres(m) + ':' + deuxChiffres(r) + sep +
           (f < 100 ? (f < 10 ? '00' : '0') : '') + f;
  }

  /** Bornes complètes pour les sous-titres : une fin manquante prend le début suivant. */
  function cues(doc) {
    return doc.segments.map(function (sg, i) {
      var suivant = doc.segments[i + 1];
      var debut = sg.debut != null ? sg.debut : 0;
      var fin = sg.fin != null ? sg.fin
              : (suivant && suivant.debut != null ? suivant.debut : debut + 3);
      return { debut: debut, fin: Math.max(fin, debut + 0.1), sg: sg };
    });
  }

  /** [nom] : libellé brut → nom affiché (renommé ou « Locuteur n »). */
  function versTexte(doc, nom) {
    return tours(doc).map(function (t) {
      var texte = t.indices.map(function (i) { return doc.segments[i].texte; }).join(' ');
      var tete = (doc.horodate ? '[' + horloge(t.debut) + '] ' : '') +
                 (t.locuteur ? nom(t.locuteur) + ' : ' : '');
      return tete + texte;
    }).join('\n\n') + '\n';
  }

  function versSrt(doc, nom) {
    return cues(doc).map(function (c, i) {
      return (i + 1) + '\n' + horodatage(c.debut, ',') + ' --> ' + horodatage(c.fin, ',') + '\n' +
             (c.sg.locuteur ? nom(c.sg.locuteur) + ': ' : '') + c.sg.texte;
    }).join('\n\n') + '\n';
  }

  function versVtt(doc, nom) {
    function echapper(t) { return t.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }
    return 'WEBVTT\n\n' + cues(doc).map(function (c) {
      return horodatage(c.debut, '.') + ' --> ' + horodatage(c.fin, '.') + '\n' +
             (c.sg.locuteur ? '<v ' + echapper(nom(c.sg.locuteur)) + '>' : '') + echapper(c.sg.texte);
    }).join('\n\n') + '\n';
  }

  function versJson(doc, nom, fichier) {
    return JSON.stringify({
      source: 'LuxASR, Université du Luxembourg (https://luxasr.uni.lu)',
      fichier: fichier || null,
      locuteurs: doc.locuteurs.map(function (l) { return { id: l, nom: nom(l) }; }),
      segments: doc.segments.map(function (sg) {
        return { start: sg.debut, end: sg.fin, speaker: sg.locuteur ? nom(sg.locuteur) : null,
                 speaker_id: sg.locuteur, text: sg.texte };
      })
    }, null, 2) + '\n';
  }

  racine.LuxAsrTranscription = {
    TAILLE_MAX: TAILLE_MAX,
    DUREE_MAX_S: DUREE_MAX_S,
    typeAudio: typeAudio,
    estResultat: estResultat,
    transcrire: transcrire,
    normaliser: normaliser,
    tours: tours,
    horloge: horloge,
    versTexte: versTexte,
    versSrt: versSrt,
    versVtt: versVtt,
    versJson: versJson
  };
})(typeof window !== 'undefined' ? window : globalThis);
