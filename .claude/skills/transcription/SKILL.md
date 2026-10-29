---
name: transcription
description: Transcrire et diariser (« qui a dit quoi ») des enregistrements en luxembourgeois avec l'API par lots de LuxASR, le service de reconnaissance de la parole de l'Université du Luxembourg — paramètres de /asr2 et du suivi /v3/asr/jobs, formats de sortie, séparation des locuteurs (num_speakers), schéma JSON relevé, découpage en phrases et tours de parole, exports TXT/SRT/VTT/JSON, contrainte CORS côté navigateur, conditions d'usage du service. À utiliser quand on transcrit un fichier audio ou vidéo, quand on touche à la page Labs « Transcription » (docs/labs-transcription.html, docs/assets/luxasr-transcription.js), quand on appelle /asr2 depuis du code, ou quand une transcription LuxASR revient vide, mal découpée, sur une seule voix ou bloquée par le navigateur. Pas pour la dictée en direct du clavier (WebSocket), qui a son propre client.
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
| Horodatage | non | oui, jusqu'au mot (`outfmt=json`) |
| Amorçage (`prompt`), `beam_size` | non | oui |
| Navigateur | passe (un WebSocket ignore CORS) | **bloqué par CORS** : le 202 de `/asr2` n'a pas d'en-tête (§ 5) |

Les relevés bruts sont dans `releves/` : `releves/2026-10-29/api.md` (la
documentation lue sur leur site, citations et URL), `sonde-cors.txt`,
`essai.md` et `resultat.json` (une vraie réponse). Ce qui suit en est la
synthèse ; en cas de doute, c'est le relevé qui fait foi, et un relevé plus
récent l'emporte.

## 1. Conditions d'usage — à respecter avant toute ligne de code

- **Une intégration demande leur accord.** Leur section « API access » dit :
  « We are opening API access now for limited access. […] If you plan to
  integrate our service into another application, contact us first for
  permission and conditions. » La page Labs est une intégration : **elle ne
  se publie pas avant leur accord écrit.** Le contact est Peter Gilles
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
| `prompt` | texte, **900 caractères au plus** (la suite est ignorée) | documenté : seulement des mots qui peuvent être dans l'audio ; encoder pour l'URL |
| `num_speakers` | entier 1 à 10, seulement avec `diarization=Enabled` | **non documenté pour l'API** : c'est ce qu'envoie leur propre formulaire au même `/asr2` (`luxasr_main.js`). Envoyé par la page et le script quand on le choisit |
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

Essai du 29 octobre 2026 (`releves/2026-10-29/essai.md`) : 2 min d'un podcast
de RTL, une animatrice et une invitée, voix féminines de hauteur proche,
questions de 3 à 5 s. Sans `num_speakers` : **une seule voix trouvée**,
375 mots sur 375 en `SPEAKER_00`, aucun des 6 changements attendus. Leur page
annonce cette limite (« short turns by speakers or many overlapping speaker
turns are not segmented correctly ») et conseille de donner le nombre de voix.
La transcription, elle, est bonne (probabilité moyenne 0,937), à un artefact
près : « Wéi ginn. Wéi ginn. » en double, de durée nulle et de faible
probabilité.

Conséquences : la page demande le nombre de voix (« à détecter » par défaut),
et prévient quand la détection automatique n'en a trouvé qu'une. **L'effet de
`num_speakers` n'est pas encore mesuré** : prochain essai à faire, sur le même
extrait, avec `--locuteurs 2`, et à consigner dans `releves/`. Ne pas promettre
« qui a dit quoi » dans un texte public avant ce résultat.

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
8. On ne change jamais le texte reconnu : ni correction, ni reponctuation, ni
   suppression des artefacts.

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
tant que ce n'est pas corrigé. Cause probable, à leur proposer : nginx n'ajoute
un `add_header` qu'aux codes 200, 201, 204, 206 et 3xx, **pas au 202**, sauf
avec le drapeau `always` :

```nginx
add_header Access-Control-Allow-Origin * always;
```

Le même correctif couvre les sondages « en cours », s'ils répondent 202 (non
observé le 29 octobre : le travail de la sonde était déjà fini).

Ce que fait la page en attendant :

- Envoi par `XMLHttpRequest` : un fichier entièrement parti puis une réponse
  illisible donne `CORS`, un envoi coupé donne `RESEAU` (les deux rendent
  `status 0`).
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

## 6. Transcrire en ligne de commande

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

## 7. Tester une modification

1. **Normaliseur** (sans réseau) : `node docs/scripts/test_luxasr_transcription.js`.
   Le premier cas est la vraie réponse du 29 octobre 2026 : fidélité du texte
   (élisions comprises), une phrase par sous-titre, durées et longueurs
   bornées. Les autres sont synthétiques et le disent. Tout nouveau relevé réel
   s'ajoute en tête.
2. **Page** : Playwright avec Chromium préinstallé, contre un faux service sur
   une **autre origine locale** (`window.LUXASR_BASE`, posé par
   `addInitScript`), ce qui reproduit CORS au réel. Scénarios : succès
   (202 → `queued` → `processing` → `completed` → JSON au format du § 2),
   CORS (202 sans en-tête), travail en échec, `not_found`, fichier refusé
   avant envoi, import d'un résultat, une seule voix détectée. Contrôler à
   360 px (aucun défilement horizontal), en thème sombre et en anglais.
3. **En vrai**, quand le réseau le permet : un enregistrement court à deux voix,
   une seule soumission, et le relevé consigné dans `releves/`.

## 8. Où est quoi

- `docs/labs-transcription.html` : la rubrique Labs, FR/EN (`?lang=en`).
- `docs/assets/luxasr-transcription.js` : client `/asr2`, normaliseur, exports,
  sans aucun dessin (même principe que `luxasr-client.js`).
- `docs/labs.html` : la page Labs (dictée) ; les deux pages partagent le
  sous-menu « Dictée · Transcription ».
- `docs/scripts/test_luxasr_transcription.js` : tests du normaliseur.
- `.claude/skills/transcription/luxasr_transcrire.py` : transcription d'un
  fichier et sonde CORS.
- `.claude/skills/transcription/releves/` : relevés réels, datés.
- `stt/bench/luxasr_api.py` : client `/asr2` du banc de mesure de la dictée.
