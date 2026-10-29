---
name: transcription
description: Transcrire et diariser (« qui a dit quoi ») des enregistrements en luxembourgeois avec l'API par lots de LuxASR, le service de reconnaissance de la parole de l'Université du Luxembourg — paramètres de /asr2 et du suivi /v3/asr/jobs, formats de sortie, séparation des locuteurs, normalisation du résultat en tours de parole, exports TXT/SRT/VTT/JSON, contrainte CORS côté navigateur, règles d'usage du service. À utiliser quand on transcrit un fichier audio ou vidéo, quand on touche à la page Labs « Transcription » (docs/labs-transcription.html, docs/assets/luxasr-transcription.js), quand on appelle /asr2 depuis du code, ou quand une transcription LuxASR revient vide, mal découpée ou bloquée par le navigateur. Pas pour la dictée en direct du clavier (WebSocket), qui a son propre client.
---

# Transcription LuxASR — enregistrements, locuteurs, exports

LuxASR (<https://luxasr.uni.lu>) est le service de reconnaissance de la parole
luxembourgeoise de l'Université du Luxembourg (Peter Gilles et son équipe). Le
clavier l'utilise de deux façons, à ne pas confondre :

| | Dictée en direct | Transcription d'un fichier (cette skill) |
|---|---|---|
| Voie | WebSocket `wss://luxasr.uni.lu/prod/ws/transcribe` | HTTP par lots : `POST /asr2` puis `/v3/asr/jobs/…` |
| Code | `LuxAsrSession.kt`, `docs/assets/luxasr-client.js` | `docs/assets/luxasr-transcription.js`, `luxasr_transcrire.py` (ici) |
| Locuteurs | non | **oui** (`diarization=Enabled`) |
| Horodatage | non | oui (selon `outfmt`) |
| Amorçage (`prompt`), `beam_size` | non | oui |
| Navigateur | passe (un WebSocket ignore CORS) | **bloqué par CORS** tant que le service n'a pas corrigé ses en-têtes (§ 4) |

## 1. Règles d'usage — à respecter avant toute ligne de code

- **C'est leur serveur, pas le nôtre.** Un appel à la fois, jamais en
  parallèle ; un travail soumis est suivi jusqu'au bout, pas resoumis.
  L'accès aux lots nous a été accordé pour un usage de laboratoire : ne pas
  mettre à genoux leurs serveurs est une condition explicite
  (`stt/bench/luxasr_api.py`). Avant d'ouvrir une page publique plus large, ou
  d'augmenter les plafonds, demander d'abord à l'Université.
- **Sondage poli** : toutes les 2 s, puis toutes les 5 s au-delà d'une minute.
  Le client de dictée sonde à 200 ms parce qu'un énoncé dure 10 s ; un fichier
  d'une heure ne le justifie pas.
- **Plafonds de la page** (`luxasr-transcription.js`) : 100 Mo et 90 min par
  fichier. Ce sont nos choix, pas des limites documentées du service.
- **La voix part à l'Université.** Toute interface le dit *avant* l'envoi, avec
  un lien vers <https://luxasr.uni.lu/>. Notre site ne reçoit et ne garde rien :
  la page tourne dans le navigateur. Ne jamais faire transiter l'audio par un
  serveur à nous sans mise à jour de la politique de confidentialité.
- **Consentement** : on ne transcrit que ce qu'on a le droit d'enregistrer ; la
  page le rappelle.
- **Remerciement** : toute surface qui montre un résultat crédite LuxASR et
  l'Université du Luxembourg.

## 2. L'API par lots

### Soumettre — `POST https://luxasr.uni.lu/asr2`

Le corps est fait des **octets bruts du fichier**, avec un
`Content-Type: audio/*`. Pas de `multipart/form-data`, qui est l'ancien flux
déprécié (`/v2/asr`, encore cité dans des exemples publics). Le serveur vérifie
que la charge est un média décodable : il faut un vrai conteneur (WAV, MP3,
M4A, OGG, FLAC, WebM), jamais du PCM nu.

Paramètres d'URL :

| Paramètre | Valeurs | Notes |
|---|---|---|
| `language` | `lb` | Le seul que nous utilisons. |
| `diarization` | `Enabled` / `Disabled` | **Défaut côté service : `Enabled`.** Toujours le passer explicitement. Le clavier le met à `Disabled` (un seul locuteur), la transcription à `Enabled`. |
| `outfmt` | `text`, `colored_text`, `json`, `srt`, `vtt`, `textgrid`, `maxqda` (relevés dans leur script `luxasr_transcribe.py`) ; la doc du site cite aussi `textgrid_aligned`, `ipa` | Une liste séparée par des virgules (`json,srt`) rend un JSON avec une clé par format : documenté pour l'API, **non essayé** sur `/asr2`. La page demande `json`. |
| `prompt` | texte libre | Amorçage orthographique appliqué à tout l'enregistrement : noms de personnes, de lieux, jargon. Vérifié sur `/asr2` (banc `stt/bench/bench_api.py`). |
| `beam_size` | entier | Largeur du faisceau. Laisser le défaut du service. |
| `min_silence_duration_ms` | entier | Découpage sur les silences. Laisser le défaut. |

En-tête facultatif `X-Filename: nom.ext` : utile hors navigateur, **interdit
dans le navigateur** (le préflight CORS n'autorise que `Content-Type` et
`Authorization`).

Réponse : `202 Accepted`, `{"job_id": "…"}`.

Le service propose aussi, dans son formulaire web, un « nombre de
locuteurs » qui stabilise la diarisation. **Le nom du paramètre d'API
correspondant n'est pas relevé** : ne pas en inventer un. Le relever dans la
documentation d'API de <https://luxasr.uni.lu> avant de l'ajouter.

### Suivre — `GET /v3/asr/jobs/{job_id}`

JSON avec `status` : un état d'attente (file), puis `processing`, puis
`completed` ou `failed`. Le passage à `processing` marque la fin de la file
d'attente : c'est elle qui fait l'essentiel de l'attente, et elle dépend de
leur charge, pas de notre audio. Tout autre champ (position dans la file…)
est un bonus à afficher s'il est présent, jamais à exiger.

### Récupérer — `GET /v3/asr/jobs/{job_id}/result`

Le corps est le résultat **dans le format demandé** : du texte pour `text`,
une chaîne JSON pour `json`, un fichier SRT pour `srt`… Le lire en texte, puis
le confier au normaliseur (§ 3).

### Quels formats et pour quoi faire

- **`json`** : celui qu'on demande pour la diarisation. Il porte segments,
  temps et locuteurs (et les mots horodatés, selon le site). **Son schéma exact
  n'est pas documenté publiquement.** Les modèles sont de la famille Whisper,
  d'où la forme attendue, celle de WhisperX :
  `{"segments": [{"start", "end", "text", "speaker", "words": […]}]}`.
  Le normaliseur accepte cette forme et ses variantes ; s'il ne reconnaît rien,
  la page affiche la réponse brute et propose de la télécharger. Elle ne
  devine pas.
- **`srt` / `vtt`** : sous-titres. Leur script les associe par défaut à
  `diarization=Disabled` : ne pas compter dessus pour les locuteurs. Nos exports
  SRT/VTT sont fabriqués **à partir des segments normalisés**, locuteur compris.
- **`text`** : texte seul, avec ou sans préfixe de locuteur selon le service.
- **`textgrid`** : pour Praat (linguistes). Pas exposé par la page.

Le premier vrai résultat JSON qui passe par la page ou le script doit être
gardé : on remplace alors les « attendus » de ce paragraphe par le schéma
relevé, et on ajoute ce cas aux tests (§ 6).

## 3. Normaliser le résultat : segments, puis tours de parole

Tout résultat, quel qu'en soit le format, est ramené à :

```js
{ segments: [{ debut: 12.3, fin: 15.8, locuteur: "SPEAKER_00", texte: "…" }],
  horodate: true,          // false pour un texte sans temps
  diarise: true,           // au moins un locuteur reconnu
  format: "json" }         // json | srt | vtt | text
```

Règles, implémentées dans `normaliser()` de `docs/assets/luxasr-transcription.js` :

1. Chaîne JSON valide → chercher le tableau de segments : la racine, puis les
   clés `segments`, `result`, `results`, `transcription`, `transcript`,
   `utterances`, `chunks`, `data`, à un niveau de profondeur. Une réponse
   multi-format (`{"json": …, "srt": …}`) est dépliée sur sa clé `json`.
2. Champs d'un segment, par ordre de préférence :
   locuteur `speaker` › `spk` › `speaker_id` › `speaker_label` › `label` ;
   début `start` › `begin` › `start_time` › `from` ; fin `end` › `stop` ›
   `end_time` › `to` ; texte `text` › `transcript` › `sentence`, sinon les mots
   recollés. Les temps peuvent être des nombres ou des chaînes `hh:mm:ss.mmm`.
3. **Un segment dont les mots changent de locuteur est coupé** à chaque
   changement : WhisperX attribue le locuteur au mot, et un segment de 30 s
   peut en couvrir deux.
4. Sinon SRT (`00:00:01,000 --> …`), VTT (`WEBVTT`, balises `<v Nom>`), ou
   texte ligne à ligne. Préfixes de locuteur reconnus en tête de réplique :
   `SPEAKER_00:`, `[SPEAKER_00]`, `Speaker 1:`, `Spriecher 1:`,
   `Locuteur 1 :`, `Intervenant 1 :`.
5. Les libellés bruts (`SPEAKER_00`) sont numérotés **dans l'ordre
   d'apparition** et présentés en « Locuteur 1, 2… », renommables. La
   diarisation est anonyme : elle sépare des voix, elle ne sait pas qui parle.
6. Pour la lecture, les segments consécutifs d'un même locuteur sont fusionnés
   en **tours de parole**. Les exports SRT/VTT gardent les segments, plus courts
   et donc lisibles en sous-titres.
7. On ne change jamais le texte reconnu : ni correction, ni reponctuation. La
   coupe des boucles de Whisper (`couperBoucle` de `luxasr-client.js`) concerne
   la dictée, pas ce flux, où le service livre un texte déjà fini.

Exports : TXT (`[00:01:02] Locuteur : texte`), SRT et VTT (avec `<v Nom>`),
JSON normalisé, et la réponse brute du service, toujours téléchargeable, pour
ne rien perdre de ce que le normaliseur aurait ignoré.

## 4. Le navigateur et CORS — le point dur

Relevé le 2 septembre 2026 depuis `https://famibelle.github.io` (en-tête de
`docs/assets/luxasr-client.js`) : les réponses **`202 Accepted`** de
`POST /asr2` et des sondages « en cours » ne portent pas
d'`Access-Control-Allow-Origin`, alors que les `200 OK` en portent un. Le
navigateur laisse partir la requête, le service crée le travail, mais la page
**ne peut pas lire le `job_id`**.

Cause probable, à proposer à l'Université : nginx n'ajoute un `add_header` qu'aux
codes 200, 201, 204, 206, 301, 302, 303, 304, 307 et 308, **pas au 202**, sauf
si la directive porte le drapeau `always` :

```nginx
add_header Access-Control-Allow-Origin * always;
```

Conséquences pour la page :

- Elle envoie le fichier par `XMLHttpRequest` pour suivre la progression de
  l'envoi, et pour distinguer deux échecs qui donnent tous deux `status 0` :
  envoi **terminé** puis réponse illisible (CORS : le fichier est arrivé) ;
  envoi **interrompu** (réseau).
- En cas de CORS, elle **ne réessaie pas** (chaque essai crée un travail
  inutile chez eux) et propose les deux voies qui marchent : le formulaire de
  <https://luxasr.uni.lu>, ou `luxasr_transcrire.py`. Elle sait ensuite
  **ouvrir un résultat existant** (JSON, SRT, VTT, TXT), avec l'audio en option,
  pour le lire, renommer les locuteurs et l'exporter.
- Pas de mandataire (proxy) maison pour contourner : l'audio passerait par un
  tiers non déclaré. Si un jour il en faut un, c'est une décision produit et
  une mise à jour de la politique de confidentialité.

Vérifier où en est le service (crée un travail minuscule d'une demi-seconde de
silence) :

```bash
python3 .claude/skills/transcription/luxasr_transcrire.py --sonde-cors
```

Si la sonde montre l'en-tête sur le 202, la page marche telle quelle : mettre
à jour ce paragraphe, l'en-tête de `luxasr-client.js` et l'encadré de la page.

## 5. Transcrire en ligne de commande

```bash
# Diarisation activée, JSON brut dans un fichier
python3 .claude/skills/transcription/luxasr_transcrire.py interview.m4a -o interview.json

# Texte seul, sans séparation des locuteurs
python3 .claude/skills/transcription/luxasr_transcrire.py dictee.wav --sans-diarisation --outfmt text

# Amorcer l'orthographe des noms propres
python3 .claude/skills/transcription/luxasr_transcrire.py conseil.mp3 -o conseil.json \
    --prompt "Gemengerot, Schäffen, Bettembourg, Lydie Polfer"
```

Dépendance : `requests`. Le script envoie le fichier tel quel, avec un
`Content-Type` déduit de l'extension, et affiche les états du travail sur la
sortie d'erreur. Le JSON obtenu s'ouvre ensuite dans la page (« Ouvrir un
résultat »).

Cette session cloud n'atteint pas forcément `luxasr.uni.lu` : la politique
réseau de l'environnement doit autoriser ce domaine. Sans cela, ne pas simuler
de réponse en la présentant comme réelle.

## 6. Tester une modification

1. **Normaliseur** (sans réseau) : `node docs/scripts/test_luxasr_transcription.js`.
   Les cas y sont synthétiques et le disent : ils couvrent les formes que le
   normaliseur accepte, pas le schéma réel du service. Le jour où un vrai
   résultat est relevé, l'ajouter comme cas (anonymisé).
2. **Page** : Playwright avec Chromium préinstallé, en interceptant
   `https://luxasr.uni.lu/**` par `page.route` pour jouer trois scénarios :
   succès (202 → `processing` → `completed` → résultat JSON), CORS (réponse sans
   en-tête : l'encadré d'échec et la voie de repli doivent apparaître), import
   d'un résultat. Contrôler à 360 px de large (aucun défilement horizontal) et
   en thème sombre (`data-theme="dark"`).
3. **En vrai**, quand le réseau le permet : un enregistrement court à deux voix
   (1 à 2 min), une seule soumission, puis garder le JSON brut pour le § 2.

## 7. Où est quoi

- `docs/labs-transcription.html` : la rubrique Labs, FR/EN (`?lang=en`).
- `docs/assets/luxasr-transcription.js` : client `/asr2`, normaliseur,
  exports, sans aucun dessin (même principe que `luxasr-client.js`).
- `docs/labs.html` : la page Labs (dictée) ; les deux pages partagent le
  sous-menu « Dictée · Transcription ».
- `stt/bench/luxasr_api.py` : client `/asr2` du banc de mesure (dictée,
  diarisation désactivée).
- `.claude/skills/transcription/luxasr_transcrire.py` : transcription d'un
  fichier et sonde CORS.
