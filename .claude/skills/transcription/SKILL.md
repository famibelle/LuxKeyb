---
name: transcription
description: Transcrire et diariser (« qui a dit quoi ») des enregistrements en luxembourgeois avec l'API par lots de LuxASR, le service de reconnaissance de la parole de l'Université du Luxembourg — paramètres de /asr2 et du suivi /v3/asr/jobs, formats de sortie, séparation des locuteurs (num_speakers), schéma JSON relevé, découpage en phrases et tours de parole, exports TXT/SRT/VTT/JSON, contrainte CORS côté navigateur, conditions d'usage du service. Couvre aussi la transcription en direct d'une longue écoute par le flux WebSocket (docs/assets/luxasr-direct.js : liaisons renouvelées, paragraphes). À utiliser quand on transcrit un fichier audio ou vidéo ou un micro en continu, quand on touche à la page Labs « Transcription » (docs/labs-transcription.html, docs/assets/luxasr-transcription.js, docs/assets/luxasr-direct.js), quand on appelle /asr2 depuis du code, ou quand une transcription LuxASR revient vide, mal découpée, sur une seule voix ou bloquée par le navigateur. Pas pour la dictée du clavier (énoncés courts), qui a son propre client.
---

# Transcription LuxASR — enregistrements, locuteurs, exports

LuxASR (<https://luxasr.uni.lu>) est le service de reconnaissance de la parole
luxembourgeoise de l'Université du Luxembourg (Peter Gilles et son équipe). Le
clavier l'utilise de deux façons, à ne pas confondre :

| | Dictée du clavier | Transcription en direct (§ 6) | Transcription d'un fichier |
|---|---|---|---|
| Voie | WebSocket `wss://luxasr.uni.lu/prod/ws/transcribe` | le même WebSocket, en continu | HTTP par lots : `POST /asr2` puis `/v3/asr/jobs/…` |
| Code | `LuxAsrSession.kt`, `docs/assets/luxasr-client.js` | `docs/assets/luxasr-direct.js` | `docs/assets/luxasr-transcription.js`, `luxasr_transcrire.py` (ici) |
| Durée | un énoncé : 5 s de silence ou 90 s l'arrêtent | jusqu'à l'arrêt, une heure au plus | le fichier entier |
| Traduction | non (le simulateur du site l'affiche) | **oui, en français**, sur demande | non |
| Locuteurs | non | non | **oui** (`diarization=Enabled`) |
| Horodatage | non | par paragraphe, à une seconde près | oui, jusqu'au mot (`outfmt=json`) |
| Amorçage (`prompt`), `beam_size` | non | non | oui |
| Navigateur | passe (un WebSocket ignore CORS) | passe | **bloqué par CORS** : le 202 de `/asr2` n'a pas d'en-tête (§ 5) |

Les relevés bruts sont dans `releves/` : `releves/2026-10-29/api-officielle.md`
(leur documentation « API access », copiée telle quelle : **la référence**),
`api.md` (son analyse : documenté ou seulement observé, citations et URL), `sonde-cors.txt`,
`essai.md` et `resultat.json` (une vraie réponse). Ce qui suit en est la
synthèse ; en cas de doute, c'est le relevé qui fait foi, et un relevé plus
récent l'emporte.

## 1. Conditions d'usage — à respecter avant toute ligne de code

- **L'accord de l'Université et son cadre.** Leur section « API access »
  dit : « We are opening API access now for limited access. […] If you plan
  to integrate our service into another application, contact us first for
  permission and conditions. » Accord obtenu, relayé par le propriétaire du
  dépôt le 29 octobre 2026 : **usage de laboratoire et exploratoire, charge
  modérée, pour des tests et l'exploration de l'API.** C'est le cadre de la
  page Labs. Tout ce qui en sortirait (usage de production, volume, nouvelle
  surface publique) repasse d'abord par eux. Le contact est Peter Gilles
  (adresse sur leur page).
- **C'est leur serveur.** Un appel à la fois, jamais en parallèle ; un
  travail soumis est suivi jusqu'au bout, jamais resoumis ; chaque essai de
  mise au point se compte et se justifie.
- **Sondage** : celui de leur client officiel, 2 s puis 1,2 fois plus espacé
  à chaque tour (plafonné à 10 s chez nous, 15 s chez eux en file).
- **Plafonds de la page** : 100 Mo et 90 min. Le service accepte 500 Mo
  (limite du site) et ne fixe pas de durée ; on reste bien en deçà.
- **Données** (leur page) : traitement sur un serveur de l'Université au
  Luxembourg, pas d'entraînement, pas de transmission à des tiers ; l'audio
  est effacé dès la fin du traitement, le résultat dès qu'il est lu (ou au
  bout de 4 h). Base légale RGPD 6.1.e ; contact dpo@uni.lu. Toute interface
  le dit *avant* l'envoi. Notre site ne reçoit et ne garde rien ; ne jamais
  faire transiter l'audio par un serveur à nous sans mettre à jour la
  politique de confidentialité.
- **Consentement** des personnes enregistrées : la page le demande.
- **Remerciement** : toute surface qui montre un résultat crédite LuxASR et
  l'Université du Luxembourg.
- Signaler une erreur du service : par courriel à Peter Gilles, avec l'heure
  exacte et la taille approximative du fichier (leur consigne).

## 2. L'API par lots (documentée sur leur page, section « API access »)

### Soumettre — `POST https://luxasr.uni.lu/asr2?…`

Corps : les **octets bruts du fichier**, jamais `multipart/form-data`.
`Content-Type: audio/*` ou `video/*` du vrai type (`audio/wav`, `audio/mpeg`,
`audio/mp4`, `video/mp4`…). Le serveur vérifie que c'est un média décodable
avec une piste son. Formats cités : wav, mp3, mp2, m4a, mp4, mov, qta, ogg,
webm, wmv, wmav2. En-tête facultatif `X-Filename`, **interdit dans le
navigateur** : le préflight n'autorise que `Content-Type` et `Authorization`.

Paramètres, en chaîne de requête :

| Paramètre | Valeurs | Statut |
|---|---|---|
| `language` | `lb` (défaut), `de` | documenté ; tout autre est rejeté |
| `diarization` | `Enabled` (défaut) / `Disabled` | documenté. Toujours le passer : la dictée met `Disabled`, la transcription `Enabled` |
| `hesitations` | `Hide` (défaut) / `Keep` | documenté (ëëë, äh, ähm) |
| `outfmt` | `colored_text`, `text`, `srt`, `vtt`, `maxqda`, `textgrid`, `textgrid_aligned`, `ipa`, `zipa`, `vowels`, `json` | documenté. Liste à virgules possible (`text,json,srt`) : réponse JSON à une clé par format, plus un éventuel `warning`. `json2` existe côté serveur, non documenté |
| `beam_size` | entier (5 sur le site) | documenté ; laisser le défaut |
| `min_silence_duration_ms` | entier (2000) | documenté ; laisser le défaut |
| `maxlen` | 42 | documenté, pour SRT et VTT du service |
| `prompt` | texte, **900 caractères au plus** (la suite est ignorée) | documenté : les mots importants d'abord, orthographe et majuscules exactes, séparés par des virgules ou des points, et seulement des mots qui peuvent être dans l'audio (sinon ils peuvent y apparaître) ; encoder pour l'URL. Le champ de la page accepte 900 caractères et le dit |
| `num_speakers` | entier 1 à 10, seulement avec `diarization=Enabled` | **non documenté pour l'API**, mais c'est ce qu'envoie leur propre formulaire au même `/asr2` (`luxasr_main.js`), et **le serveur en tient compte** (essai du 29 octobre 2026, § 3). Envoyé par la page et le script quand on le choisit |
| `vad_filter`, `origin`, `target_langs`, `speaker_identification` | | envoyés par leur site, non documentés : ne pas les utiliser sans leur accord |

Réponse : **202** `{"job_id": "…", "status": "queued"}`.

### Suivre — `GET /v3/asr/jobs/{job_id}`

`status` : `queued`, `processing`, `completed`, `failed`. Estimations
« best-effort », absentes quand il n'y en a pas : `queue_position` (1 = le
prochain), `queue_wait_seconds`, `eta_seconds` en file ; `eta_seconds` en
traitement. Identifiant inconnu, expiré ou supprimé : **404**
`{"status": "not_found"}`. Le code HTTP du suivi (202 en cours, 200 à la fin)
n'est pas documenté ; leur script tient un 200 pour fini.

### Récupérer — `GET /v3/asr/jobs/{job_id}/result`

**Une seule lecture** : « the result can be fetched only once », il est effacé
aussitôt. Ne jamais le redemander ; garder la réponse brute dès la première
lecture (la page l'offre au téléchargement, le script l'écrit avant tout).

### Temps

« Up to 5 % of the audio file's duration » ; pour `json` avec diarisation, le
serveur estime 4 s + 0,062 × durée. Relevé : 2 min traitées en 6,6 s au plus,
sans file d'attente, 8,9 s de bout en bout avec l'envoi de 3,8 Mo.

### Le JSON (relevé le 29 octobre 2026)

Non décrit par leur page, mais observé :

```
[ { "speaker": "SPEAKER_00", "start": 0.031, "end": 119.978,
    "text": "Fréier oder spéider? …",
    "words": [ { "start": 0.031, "end": 0.411, "word": " Fréier",
                 "probability": 0.779, "speaker": "SPEAKER_00" }, … ] } ]
```

Un **tableau à la racine** ; un segment par tour de parole détecté, qui peut
durer des minutes ; des mots horodatés à espace en tête (Whisper), avec leur
locuteur et leur probabilité. Aucun autre champ (ni langue, ni durée, ni
modèle). Whisper sépare les élisions : le mot « d' » puis le mot « Leit »,
quand le texte du segment porte « d'Leit ».

## 3. Ce que vaut la diarisation — à dire honnêtement

Deux essais du 29 octobre 2026 sur le même fichier (`releves/2026-10-29/essai.md`) :
2 min d'un podcast de RTL, une animatrice et une invitée, voix féminines de
hauteur proche, questions de 3 à 5 s, 7 changements de voix attendus.

| | Sans `num_speakers` (`resultat.json`) | `num_speakers=2` (`resultat-2voix.json`) |
|---|---|---|
| Voix trouvées | 1 (375 mots sur 375 en `SPEAKER_00`) | 2 (317 et 97 mots), le locuteur suit le rôle |
| Changements de voix | 0 sur 7 | **7 sur 7**, à −0,4 / +0,8 s près, et 2 faux dans la première seconde (un segment de 50 ms, « 3. ») |
| Texte | bon, probabilité moyenne 0,937 ; « Wéi ginn. Wéi ginn. » en double | moins bon, 0,913 : « Arthroos » devient « dach d'Ros », et une **boucle « vun deen » × 19** à 0:27 |
| Temps | 6,6 s au plus | 7,9 s au plus |

Ce qu'on en retient :

- **Le nombre de voix fait la séparation.** Sans lui, des voix proches et des
  répliques courtes restent sur une seule voix, ce que leur page annonce
  (« short turns by speakers or many overlapping speaker turns are not
  segmented correctly »). La page le demande donc, « à détecter » par défaut,
  et prévient quand la détection automatique n'a trouvé qu'une voix.
- **Il peut coûter de la justesse au texte**, hypothèse non vérifiable d'ici :
  le service transcrirait par tranches de locuteur, des tranches plus courtes
  donnant moins de contexte à Whisper. La page le dit (« au prix parfois d'un
  peu de justesse »).
- Les verdicts de l'essai reposent sur la cohérence du texte, pas sur une
  écoute. Un seul fichier, une seule paire de voix : on peut écrire « sur
  notre essai », pas une promesse générale de « qui a dit quoi ».

## 4. Normaliser le résultat : phrases, puis tours de parole

Tout résultat, quel qu'en soit le format, est ramené à :

```js
{ segments: [{ debut: 12.3, fin: 15.8, locuteur: "SPEAKER_00", texte: "…" }],
  locuteurs: ["SPEAKER_00", …],   // ordre d'apparition
  horodate: true, diarise: true,
  format: "json" }                // json | srt | vtt | text | inconnu
```

Règles, dans `normaliser()` de `docs/assets/luxasr-transcription.js` :

1. Chaîne JSON → le tableau de segments : la racine (cas réel), sinon les
   clés `segments`, `result`, `results`, `transcription`, `transcript`,
   `utterances`, `chunks`, `data`. Une réponse multi-format est dépliée sur sa
   clé `json`.
2. Champs d'un segment : locuteur `speaker` › `spk` › `speaker_id` ›
   `speaker_label` › `label` ; début `start` › `begin` › `start_time` › `from` ;
   fin `end` › `stop` › `end_time` › `to` ; texte `text` › `transcript` ›
   `sentence`, sinon les mots recollés. Temps en nombres ou `hh:mm:ss.mmm`.
3. **Les mots horodatés découpent le segment** : à chaque changement de
   locuteur d'un mot, puis en phrases d'une même voix. Une phrase se ferme sur
   un point quand elle dure 2 s ou compte 25 caractères (toujours, si elle a
   commencé en cours de phrase), sur une virgule au-delà de 4 s ou
   60 caractères, n'importe où au-delà de 8 s ou 100 caractères : la taille
   d'un sous-titre de deux lignes de 42.
4. **Le texte coupé est celui du service**, pas les mots recollés : chaque mot
   est placé dans le texte du segment, et l'on coupe ce texte-là. Les
   élisions restent intactes ; si un mot ne s'y retrouve pas, repli sur les
   mots recollés.
5. Sinon SRT, VTT (`<v Nom>`) ou texte. Préfixes de locuteur reconnus :
   `SPEAKER_00:`, `[SPEAKER_00]`, `Speaker 1:`, `Spriecher 1:`,
   `Locuteur 1 :`, `Intervenant 1 :`, et un « Nom : » quelconque s'il est
   régulier dans tout le document (nos propres exports après renommage).
6. Les libellés bruts sont présentés « Locuteur 1, 2… », renommables. La
   diarisation est anonyme : elle sépare des voix, elle ne sait pas qui parle.
7. Lecture : les phrases consécutives d'une même voix forment un tour de
   parole, coupé en paragraphes à la première fin de phrase passé 45 s (75 s
   au plus), pour garder des horodatages où se repérer. Sans locuteurs, on
   coupe aussi sur les silences de plus de 2 s.
8. **Boucles de Whisper** : un motif de 1 à 4 mots répété au moins 5 fois
   d'affilée est réduit à sa première occurrence, sur les mots avant le
   découpage en phrases (sinon une boucle serait coupée en deux), sur le texte
   pour SRT, VTT et texte. C'est la règle de `RepetitionTrimmer.kt` pour la
   dictée, avec un seuil plus haut (5 au lieu de 3 ou 4) parce que la boucle
   peut tomber au milieu du texte, où « nee, nee, nee » doit survivre. Chaque
   boucle est consignée (`doc.boucles` : motif, nombre, instant) et la page
   l'annonce ; la réponse brute la garde.
9. À part ces boucles, on ne change jamais le texte reconnu : ni correction,
   ni reponctuation, ni suppression des faux mots (« 3. », « Wéi ginn. »).

Exports : TXT (`[1:02] Nom : texte`), SRT et VTT (une phrase par sous-titre,
locuteur en tête ou en `<v Nom>`), JSON normalisé, et la réponse brute du
service. Les exports TXT, SRT, VTT et JSON se rouvrent dans la page.

## 5. Le navigateur et CORS — le point dur

Relevé le 2 septembre puis le 29 octobre 2026 (`releves/2026-10-29/sonde-cors.txt`) :

```
OPTIONS /asr2          200  ACAO : *  en-têtes permis : Content-Type, Authorization
POST /asr2             202  ACAO : — absent —
GET  jobs/{id}         200  ACAO : *  (completed)
GET  jobs/{id}/result  200  ACAO : *
```

Le navigateur envoie le fichier, le service crée le travail, mais la page
**ne peut pas lire le `job_id`** : la rubrique ne fonctionne pour personne
tant que ce n'est pas corrigé. Confirmé depuis le site publié le 29 octobre
2026 (message « Votre fichier n'est pas parti »). Leur documentation n'en dit
rien : elle ne vise que des clients comme `curl`, que CORS ne concerne pas.
Le même défaut rend aussi illisible, dans le navigateur, le 404
`not_found` du suivi : la page le prend alors pour une coupure (`RESEAU`). Cause probable, à leur proposer : nginx n'ajoute
un `add_header` qu'aux codes 200, 201, 204, 206 et 3xx, **pas au 202**, sauf
avec le drapeau `always` :

```nginx
add_header Access-Control-Allow-Origin * always;
```

Le même correctif couvre les sondages « en cours », s'ils répondent 202 (non
observé le 29 octobre : le travail de la sonde était déjà fini).

Ce que fait la page en attendant :

- **Une demi-seconde de silence d'abord** (`sonderCors`, `outfmt=text`,
  `diarization=Disabled`, 16 044 octets) : si sa réponse est illisible, le
  vrai fichier ne part pas. Leur serveur ne traite qu'un travail minuscule au
  lieu d'une heure d'audio, et le visiteur ne téléverse pas 100 Mo pour rien.
  Une réponse lue est mémorisée sept jours dans ce navigateur
  (`luxasr-cors-lisible`) : les envois suivants partent sans vérification.
- Envoi par `XMLHttpRequest` : un fichier entièrement parti puis une réponse
  illisible donne `CORS` (`fichierParti: true`), un envoi coupé donne
  `RESEAU` (les deux rendent `status 0`).
- Sur `CORS`, **aucun nouvel essai** (chaque essai crée un travail inutile
  chez eux) ; elle renvoie vers le formulaire de <https://luxasr.uni.lu> puis
  « Ouvrir un résultat ». Un échec CORS de moins de sept jours est rappelé au
  visiteur avant son essai suivant.
- Pas de mandataire maison pour contourner : l'audio passerait par un tiers
  non déclaré.

Vérifier où en est le service (crée un travail d'une demi-seconde de silence) :

```bash
python3 .claude/skills/transcription/luxasr_transcrire.py --sonde-cors
```

Quand l'en-tête apparaît sur le 202 : consigner la sonde dans `releves/`,
mettre à jour ce paragraphe, l'en-tête de `luxasr-transcription.js`, celui de
`luxasr-client.js` et le paragraphe « Pour les curieux » de la page.

## 6. La transcription en direct

Même service et même protocole que la dictée (`LuxAsrSession.kt`) :

- `config` en JSON d'abord (`{"type": "config", "language": "lb"}`), puis le
  PCM 16 bits little-endian, 16 kHz mono, en trames binaires de ~160 ms ;
  `{"type": "stop"}` pour conclure ;
- en retour, `connected`, puis des `transcription` : `accumulated_text` (engagé,
  ne fait que grandir), `partial_text` (queue encore instable), et
  `send_reason: "silence_commit"` avec une queue vide quand le service clôt une
  phrase, après un peu moins d'une seconde de silence ; `recording_stopped`
  après « stop », une fois toute la queue engagée ; `error`.

Ce que `luxasr-direct.js` y ajoute pour une écoute qui dure :

1. **Le silence ne clôt rien** : la dictée s'arrête après 5 s de silence, le
   direct seulement quand on le demande, une heure au plus.
2. **La liaison est renouvelée** à la première phrase close passé 120 s, de
   force passé 600 s : chaque message porte tout l'`accumulated_text` de la
   liaison, qui sinon grandirait sans fin. « stop » sur l'ancienne,
   `recording_stopped`, puis une nouvelle liaison. Le micro ne s'arrête pas :
   ce qu'il capte pendant le raccord est gardé et envoyé d'un trait à
   l'ouverture, puisque le service découpe sur les échantillons reçus et non
   sur l'horloge (`stt/bench/probe_gap.py`).
3. **Une liaison coupée est reprise** de la même façon, trois fois ; une
   liaison qui ne s'ouvre pas en 8 s est tenue pour perdue. Le texte déjà
   écrit est toujours gardé.
4. **Paragraphes** : un silence de plus de 2,5 s entre une phrase close et le
   texte suivant en ouvre un, de même qu'un paragraphe de plus de 45 s à la
   phrase close suivante. L'horodatage est l'instant d'arrivée du texte moins
   la latence du premier aperçu (~1 s), d'où « à une seconde près ».
5. **Réseau lent** : au-delà de 2 s d'audio en attente d'envoi, la page le
   signale ; au-delà de 30 s, elle arrête et garde le texte.
6. Les boucles de Whisper sont réduites à l'affichage et à l'export par la
   même règle que les fichiers (§ 4, règle 8).
7. L'écran est gardé allumé (`navigator.wakeLock`) pendant l'écoute : sur un
   téléphone, un écran qui s'éteint coupe le micro.
8. **Traduction en direct**, sur demande (`options.traduction`, désactivée par
   défaut : c'est un travail de plus chez eux). La configuration porte
   `translation_enabled: true` et `translation_target: "fr"`, comme leur propre
   client `rt.js` ; seul le français a été vérifié (26 septembre 2026). Les
   traductions reviennent dans des messages `translation` à part
   (`success`, `accumulated_translation` : tout ce qui a été traduit sur la
   liaison), phrase par phrase, 0,7 à 2,2 s après l'engagement ; le
   `translation` des messages `transcription` n'est qu'un état, jamais le
   texte. Chaque ajout est rattaché au paragraphe de la dernière phrase
   engagée sur sa liaison. Comme il arrive après le texte, une liaison qui
   finit reste ouverte jusqu'à 3,5 s pour ses seuls messages `translation`,
   au renouvellement comme à l'arrêt ; l'arrêt ne rend la main qu'ensuite.
   Autres langues cibles : à relever d'abord dans `rt.js` sur leur site, puis
   à vérifier en direct, avant de les proposer.

Pas de séparation des voix : le flux n'en fait pas. Pour « qui a dit quoi »,
enregistrer puis passer par le fichier.

Ce que le flux réel fait d'une liaison de deux minutes n'a pas été mesuré :
la dictée n'en ouvre que de 90 s au plus. À relever au premier essai réel
(durée, taille des messages, coupures éventuelles) et à consigner dans
`releves/`.

## 7. Transcrire en ligne de commande

```bash
# Diarisation activée, JSON brut dans un fichier (lu une seule fois : il est écrit d'abord)
python3 .claude/skills/transcription/luxasr_transcrire.py interview.m4a -o interview.json

# En donnant le nombre de voix (num_speakers)
python3 .claude/skills/transcription/luxasr_transcrire.py interview.m4a -o interview.json --locuteurs 2

# Texte seul, sans séparation des voix
python3 .claude/skills/transcription/luxasr_transcrire.py dictee.wav --sans-diarisation --outfmt text

# Amorcer l'orthographe des noms propres (900 caractères au plus)
python3 .claude/skills/transcription/luxasr_transcrire.py conseil.mp3 -o conseil.json \
    --prompt "Gemengerot, Schäffen, Bettembourg, Lydie Polfer"
```

Dépendance : `requests`. Le JSON obtenu s'ouvre dans la page (« Vous avez déjà
un résultat LuxASR ? »).

L'environnement cloud de Claude n'atteint pas forcément `luxasr.uni.lu` : sa
politique réseau doit autoriser ce domaine. Sinon, les relevés se font depuis
une machine qui y accède et se commitent dans `releves/AAAA-MM-JJ/`. Ne
jamais simuler une réponse en la présentant comme réelle.

## 8. Tester une modification

1. **Normaliseur** (sans réseau) : `node docs/scripts/test_luxasr_transcription.js`.
   Les deux premiers cas sont les vraies réponses du 29 octobre 2026 : avec
   `num_speakers=2`, deux voix, les 9 changements du service conservés et la
   boucle réduite sans rien toucher d'autre ; sans, fidélité du texte (élisions
   comprises), une phrase par sous-titre, durées et longueurs bornées. Les
   autres sont synthétiques et le disent. Tout nouveau relevé réel s'ajoute en
   tête.
2. **Page** : Playwright avec Chromium préinstallé, contre un faux service sur
   une **autre origine locale** (`window.LUXASR_BASE`, posé par
   `addInitScript`), ce qui reproduit CORS au réel. Scénarios : succès
   (202 → `queued` → `processing` → `completed` → JSON au format du § 2),
   CORS (202 sans en-tête), travail en échec, `not_found`, fichier refusé
   avant envoi, import d'un résultat, et les deux vraies réponses rejouées
   (une seule voix détectée ; deux voix et une boucle réduite). Contrôler à
   360 px (aucun défilement horizontal), en thème sombre et en anglais.
3. **Direct** : un faux service WebSocket local qui suit le protocole du § 6
   (un mot numéroté par demi-seconde de son, engagé après 0,8 s de silence),
   sur `window.LUXASR_BASE_FLUX`, avec `window.LUXASR_ROTATION_S` raccourci ;
   Chromium lancé avec `--use-fake-device-for-media-stream`,
   `--use-fake-ui-for-media-stream` et `--use-file-for-fake-audio-capture=`
   un WAV qui alterne son et silences. Vérifier que les mots arrivent sans trou
   ni doublon à travers les renouvellements et une coupure, et les messages
   du service injoignable et du micro refusé. Pour la traduction, le faux
   service répond 0,8 s après chaque engagement, et après
   `recording_stopped` pour le dernier (« motN » devenu « tradN ») : chaque
   paragraphe doit porter la traduction de ses propres mots, et aucune ne
   doit manquer aux renouvellements ni à l'arrêt.
4. **En vrai**, quand le réseau le permet : un enregistrement court à deux voix,
   une seule soumission, et le relevé consigné dans `releves/` ; pour le
   direct, quelques minutes d'écoute, au moins deux renouvellements.

## 9. Où est quoi

- `docs/labs-transcription.html` : la rubrique Labs, FR/EN (`?lang=en`).
- `docs/assets/luxasr-transcription.js` : client `/asr2`, normaliseur, exports,
  sans aucun dessin (même principe que `luxasr-client.js`).
- `docs/assets/luxasr-direct.js` : client du flux pour le direct, sans dessin.
- `docs/labs.html` : la page Labs (dictée) ; les deux pages partagent le
  sous-menu « Dictée · Transcription ».
- `docs/scripts/test_luxasr_transcription.js` : tests du normaliseur.
- `.claude/skills/transcription/luxasr_transcrire.py` : transcription d'un
  fichier et sonde CORS.
- `.claude/skills/transcription/releves/` : relevés réels, datés.
- `stt/bench/luxasr_api.py` : client `/asr2` du banc de mesure de la dictée.
