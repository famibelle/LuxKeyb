# API LuxASR : relevés du 2026-10-29

Lu le 2026-10-29 vers 07 h 10 UTC. Les citations sont recopiées telles quelles,
en anglais. Chaque point dit s'il est **documenté** (section « API access » de
la page d'accueil) ou seulement **observé** (code du site, script officiel,
réponses du serveur).

## Sources

| URL | Réponse | Contenu |
|---|---|---|
| <https://luxasr.uni.lu/> | 200, 46 660 octets | Formulaire, « Available options », « Further information », dont la section **« API access »**. C'est la seule documentation de l'API. |
| <https://luxasr.uni.lu/static/luxasr_transcribe.py> | 200, 4 163 octets, sha256 `1403d74b…ab77da` | Le client officiel en ligne de commande. |
| <https://luxasr.uni.lu/static/luxasr_main.js?v=1792053216> | 200, 126 094 octets | Le code du formulaire : ce que le site envoie vraiment à `/asr2`. |
| <https://luxasr.uni.lu/static/luxasr_formats.js?v=1792053110> | 200, 3 475 octets | La logique des formats. La liste vient du serveur (`window.LUXASR_OUTFMTS`, écrit dans la page). |
| <https://luxasr.uni.lu/docs> | 200, 865 octets | Page « LuxASR API Documentation » **vide** : un titre « ASR_LUX API Documentation », un intertitre « Overview », rien d'autre. |
| `/api`, `/api/`, `/openapi.json`, `/redoc`, `/asr`, `/robots.txt` | 404 | Pas de lien « API » sur la page d'accueil. La documentation est un panneau dépliable de la page. |
| <https://luxasr.uni.lu/v2/asr> (GET) | 200, page « Request Rejected » | Rejeté par le pare-feu applicatif de l'Université. `/v2/asr` n'est plus cité nulle part. |

Aucun travail n'a été soumis pour ces lectures : seulement des GET de pages et de scripts.

## Endpoints

Documenté :

- `POST /asr2?…params…` : le corps contient les octets bruts du fichier. Citation : « Clients must use the queued /asr2 job flow and send the file as raw bytes in the request body (not multipart/form-data), with Content-Type: audio/* or video/*. » Réponse : `{"job_id":"<id>","status":"queued"}`, HTTP 202.
- `GET /v3/asr/jobs/<job_id>` : l'état du travail.
- `GET /v3/asr/jobs/<job_id>/result` : le résultat. Citation : « only after status is completed; the result can be fetched only once ».

Observé dans `luxasr_main.js`, non documenté pour l'API :

- `POST /translate` : la traduction, avec `/v3/translate/jobs` pour le suivi.
- `/v3/asr/vowels/<token>/logparam.csv` : le CSV du format `vowels`.
- `/v2/asr` : absent de la page, du script et du code du site.

## Paramètres de `/asr2`

La page dit : « Send parameters as query string (recommended for raw body) ». Liste documentée, recopiée :

```
language=lb|de (input language; defaults to lb, other values are rejected)
diarization=Enabled|Disabled (defaults to Enabled)
hesitations=Hide|Keep (defaults to Hide; filled pauses such as ëëë, ËË, äh, ähm)
outfmt=colored_text|text|srt|vtt|maxqda|textgrid|textgrid_aligned|ipa|zipa|vowels|json (one format, or several separated by commas)
beam_size=5
min_silence_duration_ms=2000
maxlen=42 (for SRT and VTT)
prompt=... (optional; see below)
```

En-têtes documentés : `Content-Type` (« a matching audio/* or video/* MIME type (e.g. audio/wav, audio/mpeg, audio/mp4, video/mp4) ») et `X-Filename`, facultatif, avec le nom d'origine du fichier.

**Défaut de `diarization` : `Enabled`**, documenté. Le script officiel dit dans son aide « default: Disabled for SRT/VTT, Enabled otherwise », mais son code met toujours `Enabled` quand l'option manque.

### Toutes les valeurs de `outfmt`

| Valeur | API (documentée) | Formulaire du site | Script officiel (`choices`) | `LUXASR_OUTFMTS` du serveur |
|---|---|---|---|---|
| `colored_text` | oui | oui (« Text ») | oui | oui |
| `text` | oui | non | oui (défaut du script) | oui |
| `srt` | oui | oui | oui | oui |
| `vtt` | oui | oui | oui | oui |
| `maxqda` | oui | oui | oui | oui |
| `textgrid` | oui | oui | oui | oui |
| `textgrid_aligned` | oui | oui | non | oui |
| `ipa` | oui | oui | non | oui |
| `zipa` | oui | oui | non | oui |
| `vowels` | oui | oui (« Formant chart ») | non | oui |
| `json` | oui | oui | oui | oui |
| `json2` | **non** | non | non | oui (sans libellé, « translatable: false ») |

Plusieurs formats à la fois, documenté : « Pass a comma-separated list, e.g. outfmt=text,json,srt. Duplicates are ignored; order is preserved. This is API-only ». Le résultat est alors un JSON (`Content-Type: application/json`) avec une clé par format, et « A warning field is included when applicable ».

### Nombre de locuteurs

- **Non documenté pour l'API.** Le paramètre n'est pas dans la liste ci-dessus. La page en parle seulement pour le formulaire : « an optional number of speakers field to guide diarization », « If you already know the speaker count, set Number of speakers in the input form to help stabilize segmentation. »
- **Observé dans le code du site : `num_speakers`.** C'est un entier de 1 à 10 (le choix AUTO l'omet), envoyé seulement quand `diarization=Enabled`, dans la chaîne de requête du même `POST /asr2`. Extrait de `luxasr_main.js` :

  ```js
  const numSpeakers = (diarization === "Enabled" && numSpeakersValue !== '') ? parseInt(numSpeakersValue, 10) : null;
  …
  "num_speakers": Number.isNaN(numSpeakers) ? null : numSpeakers,
  ```
- **Non essayé** depuis un client d'API : le budget était de deux travaux, et l'essai du point 4 utilise le script tel quel, sans ce paramètre.

### Autres paramètres envoyés par le site, absents de la documentation de l'API

Le formulaire envoie, extrait de `luxasr_main.js` :

```js
params = {
    "beam_size": 5,
    "vad_filter": true,
    "min_silence_duration_ms": minSilence,
    "language": language,
    "diarization": diarization,
    "hesitations": hesitations,
    "num_speakers": …,
    "outfmt": outfmt,
    "maxlen": maxlen,
    "target_langs": target_langs
};
// + prompt (tronqué à 900 caractères), origin = "Webinterface" (ou "SpeakerIdInternal"),
// + speaker_identification = true quand l'identification des locuteurs est activée
```

`vad_filter`, `target_langs`, `origin` et `speaker_identification` ne sont pas documentés pour l'API. Le script officiel envoie aussi `origin=CLI`. Une valeur vide ou nulle n'est pas envoyée.

### `prompt`

Documenté : « Keep it under 900 characters; text beyond that limit is ignored. » La consigne dit aussi : « Only include words or phrases that may actually appear in the audio […] URL-encode the value in query strings ».

## Suivi du travail

Documenté :

- États : `queued`, `processing`, `completed`, `failed`.
- Champs d'estimation, en secondes, « best-effort », absents quand il n'y a pas d'estimation :
  - pour `queued` : `queue_position` (1 = le prochain), `queue_wait_seconds`, `eta_seconds` ;
  - pour `processing` : `eta_seconds`.
- Exemple : `{"job_id":"<id>","status":"queued","queue_position":2,"queue_wait_seconds":75,"eta_seconds":130}`.
- Identifiant inconnu, expiré ou supprimé : HTTP 404 avec `{"status":"not_found",...}`.

Non documenté :

- **Le code HTTP du suivi** (202 tant que le travail n'est pas fini, 200 à la fin) vient du relevé du 2026-09-02, pas de la doc. Le script officiel considère un 200 comme fini : `if state == "completed" or status_resp.status_code == 200`.
- **Le rythme de sondage** : il n'est pas documenté. Le script officiel sonde toutes les 2 s, et allonge l'intervalle de 1,2 × à chaque sondage jusqu'à 15 s tant que le travail est `queued`.

## Limites

| Limite | Statut | Relevé |
|---|---|---|
| Taille | Documentée pour le site. Pour l'API, la page dit seulement « same formats as the web upload » | « The maximal size for upload is 500 MB. » Le site refuse au-delà (`LUXASR_MAX_UPLOAD_MB`). |
| Durée | **Non documentée** | Pas de durée maximale. « Long audios (up to a file size of 500 MB) can be processed […] it may take considerably longer ». Le code du site limite la **traduction** à 4 h d'audio (`translationMaxSeconds = 4 * 3600`). |
| Temps de traitement | Indicatif | « The recognition duration takes up to 5% of the audio file's duration (e.g., 3 minutes for 60 minutes of audio). » Dans `LUXASR_OUTFMTS`, le profil `json` vaut `base` 4 s et `rtf_diar` 0,062 (avec diarisation) ou `rtf_plain` 0,026 (sans). |
| Formats acceptés | Documentés | « wav, mp3, mp2, m4a, mp4, mov, qta, ogg, webm, wmv, wmav2, and other common audio/video containers ». Le serveur vérifie que c'est un média décodable avec une piste audio. |
| `prompt` | Documentée | 900 caractères. |
| Débit (requêtes par minute, travaux simultanés) | **Non documentée** | Rien sur la page. |
| Conservation | Documentée | « the transcription result is deleted from the server 4 hours after completion, or immediately after it has been successfully fetched (Step C), whichever comes first. […] Uploaded audio is deleted as soon as processing ends. » |

## Conditions d'utilisation

Documentées, section « API access » :

> We are opening API access now for limited access. We reserve the right to modify or suspend access to the API at any time.
> If you plan to integrate our service into another application, contact us first for permission and conditions.

Les erreurs se signalent par courriel à Peter Gilles (adresse sur la page), « with the exact time when it occurred and the approximate file size of the audio ».

Protection des données, résumé de la page :
- traitement sur un serveur dédié de l'Université, au Luxembourg ;
- pas d'entraînement sur les données, pas de transmission à des tiers ;
- base légale : RGPD art. 6.1.e (mission d'intérêt public) ; contact : la déléguée à la protection des données (dpo@uni.lu).

Aucune licence d'utilisation des résultats, aucune obligation de citation et aucun quota ne sont écrits. Le modèle `large-v3-turbo` du site et de l'API « is not yet freely available ».

## Schéma du JSON

- **Non décrit.** Le seul indice est l'exemple de la réponse à plusieurs formats :

  ```json
  {
    "text": "Moien …",
    "json": [ { "start": 0.0, "end": 1.2, "text": "Moien" } ],
    "srt": "1\n00:00:00,000 --> 00:00:01,200\nMoien\n"
  }
  ```

  Si l'exemple est fidèle, `json` est **un tableau de segments à la racine**, et non un objet `{"segments": […]}`. L'exemple ne montre aucun champ de locuteur.
- Le formulaire annonce « Click any word to play audio from that point • Hover over words to see confidence levels ». Le texte enrichi porte donc des mots horodatés avec une confiance. On ne sait pas si le format `json` les contient.
- **Confirmé par l'essai** (`resultat.json`, `essai.md`) : c'est bien un tableau de segments `{speaker, start, end, text, words}` à la racine, avec des mots `{start, end, word, probability, speaker}`.

## Écarts avec SKILL.md, pour la mise à jour

Je n'ai rien modifié, comme demandé. Voici ce que ces relevés changent :

1. `language` accepte aussi `de` (documenté).
2. `hesitations=Hide|Keep` manque, avec `Hide` par défaut.
3. Il manque trois valeurs d'`outfmt` : `zipa`, `vowels`, et `json2` (non documentée). La liste à plusieurs formats est documentée, et le résultat porte alors un éventuel champ `warning`.
4. **Le résultat ne se lit qu'une fois** et disparaît dès qu'il a été lu. La page et le script ne doivent jamais le redemander. La réponse brute doit être gardée dès la première lecture.
5. Les états sont `queued`, `processing`, `completed` et `failed`, avec `queue_position`, `queue_wait_seconds` et `eta_seconds`. Un identifiant inconnu donne 404 avec `not_found`.
6. Le nombre de locuteurs s'appelle `num_speakers` dans le formulaire. Il n'est pas documenté pour l'API et pas essayé.
7. 500 Mo est la limite du site. Les 100 Mo et 90 min de la page Labs restent nos choix.
8. Le JSON est peut-être un tableau à la racine, à vérifier sur `resultat.json`.

## Relevé CORS du jour

Voir `sonde-cors.txt`. Le préflight `OPTIONS /asr2` répond 200 avec `Access-Control-Allow-Origin: *`, et n'autorise que `Content-Type, Authorization`. Le `POST /asr2` répond **202 sans `Access-Control-Allow-Origin`**. Les deux GET (suivi et résultat) répondent 200 avec `*`.

Le constat du 2026-09-02 tient toujours. Limite de la sonde : la demi-seconde de silence était déjà finie au premier sondage, donc un sondage qui répond 202 « en cours » n'a pas été observé aujourd'hui.
