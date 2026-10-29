# Documentation officielle de l'API LuxASR (section « API access »)

Copiée depuis <https://luxasr.uni.lu> par le propriétaire du dépôt le
29 octobre 2026, telle quelle, en anglais. C'est la référence : `api.md`, à
côté, en est l'analyse (ce qui est documenté, ce qui n'est qu'observé).

Ce que ce texte ne dit pas : rien sur les navigateurs ni sur CORS. Les
exemples sont en `curl`, qui ignore CORS ; c'est pourquoi `curl` marche là où
une page web reste bloquée (SKILL.md § 5).

---

We are opening API access now for limited access. We reserve the right to modify or suspend access to the API at any time. If you plan to integrate our service into another application, [contact us](mailto:peter.gilles@uni.lu) first for permission and conditions. The LuxASR transcription API base URL is `https://luxasr.uni.lu`.

Clients must use the queued `/asr2` job flow and send the file as raw bytes in the request body (not `multipart/form-data`), with `Content-Type: audio/*` or `video/*`.

## Step A: Submit transcription job

POST `/asr2?...params...` with the raw file bytes in the request body (the same bytes you would save to disk — not a multipart form upload).

Set `Content-Type` to a matching `audio/*` or `video/*` MIME type (e.g. `audio/wav`, `audio/mpeg`, `audio/mp4`, `video/mp4`) and optionally `X-Filename` with the original file name. The server checks that the payload is decodable media with an audio track (same formats as the web upload: wav, mp3, mp2, m4a, mp4, mov, qta, ogg, webm, wmv, wmav2, and other common audio/video containers).

Response: `{"job_id":"<id>","status":"queued"}`

HTTP: `202`

## Step B: Poll status

GET `/v3/asr/jobs/<job_id>` until `status` is `completed` or `failed`.

Status values:

* `queued`
* `processing`
* `completed`
* `failed`

While a job is waiting or running, the status response also contains best-effort time estimates (in seconds; fields are omitted when no estimate is available):

* `queued`: `queue_position` (1 = next in line), `queue_wait_seconds` (estimated time until processing starts) and `eta_seconds` (estimated time until the result is ready).
* `processing`: `eta_seconds` (estimated remaining time).

Example: `{"job_id":"<id>","status":"queued","queue_position":2,"queue_wait_seconds":75,"eta_seconds":130}`

An unknown `job_id` (never submitted, expired or deleted) returns HTTP `404` with `{"status":"not_found",...}`. For data protection, the transcription result is deleted from the server 4 hours after completion, or immediately after it has been successfully fetched (Step C), whichever comes first. Afterwards the job id returns `404`, so save the result on your side. Uploaded audio is deleted as soon as processing ends.

## Step C: Fetch transcription result

GET `/v3/asr/jobs/<job_id>/result` (only after status is `completed`; the result can be fetched only once). For a single `outfmt`, the response body format matches that parameter. For a comma-separated `outfmt` list, the response is JSON with one key per format (see below).

## Parameter usage

Send parameters as query string (recommended for raw body), e.g.:

* `language=lb|de` (input language; defaults to `lb`, other values are rejected)
* `diarization=Enabled|Disabled` (defaults to `Enabled`)
* `hesitations=Hide|Keep` (defaults to `Hide`; filled pauses such as ëëë, ËË, äh, ähm)
* `outfmt=colored_text|text|srt|vtt|maxqda|textgrid|textgrid_aligned|ipa|zipa|vowels|json` (one format, or several separated by commas)
* `beam_size=5`
* `min_silence_duration_ms=2000`
* `maxlen=42` (for SRT and VTT)
* `prompt=...` (optional; see below)

## Multiple output formats (`outfmt`)

The API can return several formats from a single transcription job. Pass a comma-separated list, e.g. `outfmt=text,json,srt`. Duplicates are ignored; order is preserved. This is API-only — the website form still selects one format. Audio is transcribed once; each requested format is then produced from that same result. `maxlen` still applies if `srt` or `vtt` is included.

When more than one format is requested, `GET /v3/asr/jobs/<job_id>/result` returns JSON (`Content-Type: application/json`). Keys are the format names; values are the corresponding transcripts (plain-text formats as strings, `json` as a JSON value). A `warning` field is included when applicable. Example:

```
{
  "text": "Moien …",
  "json": [ { "start": 0.0, "end": 1.2, "text": "Moien" } ],
  "srt": "1\n00:00:00,000 --> 00:00:01,200\nMoien\n"
}
```

## Whisper prompt (`prompt`)

Optional short hint to improve spelling of uncommon names, brands, and technical terms across the entire recording (not only the opening segment). Keep it under 900 characters; text beyond that limit is ignored. Put the most important words first, use exact spelling and capitalization, and separate items with commas or periods. Example:

```
Iechternach, Weiswampach, Préizerdaul.
```

Only include words or phrases that may actually appear in the audio — avoid filler text you would not say aloud, as it can show up in the transcript. URL-encode the value in query strings (spaces as `%20`, commas as `%2C`).

## Curl examples

A) /asr2 with WAV raw body

```
curl -X POST "https://luxasr.uni.lu/asr2?language=lb&diarization=Enabled&hesitations=Hide&outfmt=json" \
-H "Content-Type: audio/wav" \
-H "X-Filename: sample.wav" \
--data-binary "@/path/to/sample.wav"
```

B) /asr2 with MP3 raw body and prompt

```
curl -X POST "https://luxasr.uni.lu/asr2?language=lb&diarization=Disabled&hesitations=Keep&outfmt=text&prompt=Iechternach%2C%20Weiswampach%2C%20Pr%C3%A9izerdaul." \
-H "Content-Type: audio/mpeg" \
-H "X-Filename: sample.mp3" \
--data-binary "@/path/to/sample.mp3"
```

C) Poll + fetch

```
curl -s "https://luxasr.uni.lu/v3/asr/jobs/<job_id>"
curl -s "https://luxasr.uni.lu/v3/asr/jobs/<job_id>/result"
```

D) /asr2 with several output formats

```
curl -X POST "https://luxasr.uni.lu/asr2?language=lb&diarization=Enabled&hesitations=Hide&outfmt=text,json,srt" \
-H "Content-Type: audio/wav" \
-H "X-Filename: sample.wav" \
--data-binary "@/path/to/sample.wav"
```
