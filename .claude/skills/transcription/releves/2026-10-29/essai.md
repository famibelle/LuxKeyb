# Essai réel de transcription diarisée, 2026-10-29

Deuxième et dernier travail soumis ce jour-là. Le premier était la sonde CORS (`sonde-cors.txt`).

## Le fichier

| | |
|---|---|
| Source | RTL Radio Lëtzebuerg, podcast « Pattcast – De Podcast iwwert Déieren », épisode 20 « Arthrose bei Hënn », publié le 16/12/2024. © CLT-UFA S.A. |
| Où | Flux public <https://www.rtl.lu/podcast/feed/pattcast-lb.xml>, fichier <https://rtlradio-streaming.rtl.lu/podcast/pattcast/2024/12/13/97e48bf373787baa866c3318a7bb497b.mp3> (814 s, sha256 `a5c08011…b1ee`) |
| Extrait envoyé | De 182,05 s à 301,85 s (3:02 à 5:02), coupé sur deux silences, sans réencodage (`ffmpeg -ss 182.05 -to 301.85 -c copy`). |
| Format | MP3, 256 kb/s, 44,1 kHz, stéréo, 3 836 119 octets (sha256 `d8297786…7dcd`). Envoyé en `audio/mpeg`. |
| **Durée** | **119,85 s** |
| Sujet | L'arthrose chez le chien : une animatrice pose les questions, une vétérinaire invitée répond. |

Droits : c'est un podcast public, mais protégé. Il n'existait aucun dialogue luxembourgeois sous licence libre :
- Wikimedia Commons n'a que des lectures à une voix et un monologue ;
- data.public.lu et Hugging Face n'ont que des voix isolées, ou des corpus en CC BY-NC-ND ;
- les leçons CC0 de Radio 100,7 sont surtout en français.

Le propriétaire du dépôt a choisi de publier `resultat.json` tel quel. C'est un court extrait (2 min sur 13 min 34), gardé pour analyser le format, et il sera retiré si l'ayant droit le demande. L'audio n'est pas dans le dépôt.

## Le nombre de voix

Dans l'épisode, deux animatrices et une invitée, toutes des femmes. **Dans l'extrait, au moins deux voix** : celle qui pose les questions et l'invitée qui répond. Je ne peux pas dire si les questions viennent d'une seule animatrice ou des deux.

Je n'ai pas écouté l'extrait. J'ai situé les tours de deux façons :
- par la structure du texte : une question courte, puis une longue réponse ;
- par une transcription locale de contrôle (modèle Whisper turbo du ZLS, sur la machine, sans rien envoyer), avec la hauteur de voix de chaque segment. Elle est d'environ 166 à 183 Hz pour les questions et de 186 à 205 Hz pour les réponses : des voix proches.

Changements de locuteur attendus, aux temps du résultat :

| Début, fin (s) | Texte rendu par LuxASR | Qui |
|---|---|---|
| 0,0 à 5,1 | « Fréier oder spéider? Jo. Okay. Gutt, wéi erkennen ech dann, dass mäi Mupp ufänkt Arthroos ze maachen? » | fin de réponse de l'invitée, puis question de l'animatrice (la frontière exacte est incertaine) |
| 5,7 à 29,1 | « Ma, de Klassiker, … typeschen Unzeeche fir Arthroos. » | invitée |
| 29,8 à 33,1 | « Dat kennen ech vu mir aus. Dat ass en anert Theema. » | probablement l'animatrice (aparté) |
| 33,5 à 49,6 | « Mee d'Strecken u sech ass awer net schlecht, … » | invitée, avec peut-être une réplique de l'animatrice à 36,6 s (« Nee, dat sécher, dat ass ganz gutt. ») |
| 49,7 à 55,1 | « Soll ech dann direkt reagéieren oder mol ofwaarden, … ? » | animatrice |
| 55,2 à 78,5 | réponse | invitée |
| 78,6 à 82,0 | « Wéi ass dat dann beim Déierendokter? Wéi gëtt dat da gewéintlech do behandelt? » | animatrice |
| 82,1 à 119,98 | réponse | invitée |

On attendait donc **au moins 6 changements de locuteur**.

## La commande et l'attente

```
python3 .claude/skills/transcription/luxasr_transcrire.py pattcast20_182-302.mp3 -o resultat.json
```

C'est le script tel quel, avec ses défauts : `language=lb`, `diarization=Enabled`, `outfmt=json`. Ni `prompt`, ni `beam_size`, ni `num_speakers`. Sortie d'erreur du script :

```
envoi de pattcast20_182-302.mp3 (3.8 Mo, audio/mpeg)
travail 8fcfcbbf-0b02-4ff0-8bf0-0beec59104db
[   2.2 s] processing
[   6.6 s] completed
écrit : …/releves/2026-10-29/resultat.json
temps total 8.91 s
```

- Soumis à 07:54:00 UTC, fichier écrit à 07:54:09 UTC.
- **Pas de file d'attente** : le premier sondage, à 2,2 s, trouve déjà `processing`.
- **Fini en 6,6 s au plus** pour 120 s d'audio, à 2 s près vu le rythme du sondage, soit environ 5 % de la durée.
- **8,9 s en tout**, avec l'envoi de 3,8 Mo et la lecture du résultat.
- L'estimation du site pour `json` avec diarisation (4 s + 0,062 × 120 s = 11,4 s) était plus prudente.

## Le JSON rendu (`resultat.json`, 67 527 octets, gardé tel quel)

```
[                                  ← tableau à la racine, pas d'objet {"segments": …}
  { "speaker": "SPEAKER_00",       ← un segment par tour de parole
    "start": 0.03096875,           ← secondes depuis le début du fichier
    "end": 119.97846875,
    "text": "Fréier oder spéider? …",
    "words": [                     ← 375 mots
      { "start": 0.03096875, "end": 0.41096875,
        "word": " Fréier",         ← espace en tête, comme Whisper
        "probability": 0.7787…,
        "speaker": "SPEAKER_00" }  ← le locuteur est aussi porté par chaque mot
    ] }
]
```

- C'est la forme de l'exemple de la documentation (tableau à la racine), avec en plus `speaker` et `words`.
- Le serveur met lui-même le JSON en forme, avec un retrait de 2 espaces.
- Aucun autre champ : ni langue, ni durée, ni version du modèle.

## La séparation des voix est-elle juste ?

**Non.** Tout l'extrait est attribué à un seul locuteur : **1 segment** de 0,03 à 119,98 s, **375 mots sur 375 en `SPEAKER_00`**. Aucun des 6 changements attendus n'est trouvé.

Cause probable, c'est la limite que la page de LuxASR annonce elle-même : « Sometimes short turns by speakers or many overlapping speaker turns are not segmented correctly ». Ici, les questions durent de 3 à 5 s, et les voix sont toutes féminines et de hauteur proche. La page conseille d'indiquer le nombre de locuteurs. Le paramètre envoyé par le formulaire s'appelle `num_speakers` (voir `api.md`), mais il n'est pas documenté pour l'API et **n'a pas été essayé** : il aurait fallu un troisième travail.

La **transcription**, elle, est bonne :
- probabilité moyenne des mots de 0,937 ;
- le texte se lit correctement, hésitations masquées (le défaut `hesitations=Hide`).

Un seul artefact : à 87,4 s, « Wéi ginn. Wéi ginn. » apparaît deux fois, avec une durée nulle et une probabilité de 0,14 à 0,47. C'est sans doute un faux mot.

## Ce que ça dit pour la page (aucun fichier modifié)

- Le normaliseur de `docs/assets/luxasr-transcription.js` lit ce résultat sans erreur : `format: "json"`, `horodate: true`, `diarise: true`, 1 segment. Les 17 tests synthétiques passent toujours. Le schéma réel est l'un des cas qu'il prévoyait (tableau à la racine, locuteur par mot).
- Avec un seul locuteur, la page affichera un bloc de 2 min. **L'export SRT donne un seul sous-titre de 2 min** (`00:00:00,031 --> 00:01:59,978`, 2 249 caractères) : il faudrait le découper avec les temps des mots, qui sont là.
- À essayer, avec l'accord du service, avant de promettre « qui a dit quoi » : refaire ce même extrait avec `num_speakers=2`. Et prévoir dans la page un message quand une seule voix est détectée.
