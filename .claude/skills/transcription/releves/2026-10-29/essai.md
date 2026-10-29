# Essai réel de transcription diarisée, 2026-10-29

Deuxième travail soumis ce jour-là ; le premier était la sonde CORS (`sonde-cors.txt`). Un troisième, avec `num_speakers=2`, est décrit à la fin.

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

## Avec num_speakers=2

Troisième travail, demandé ensuite par le propriétaire : le **même fichier** (sha256 `d8297786…7dcd`), relancé avec le script de la branche mis à jour (`240a2226`) :

```
python3 .claude/skills/transcription/luxasr_transcrire.py pattcast20_182-302.mp3 \
  -o .claude/skills/transcription/releves/2026-10-29/resultat-2voix.json --locuteurs 2
```

Paramètres envoyés : `language=lb`, `diarization=Enabled`, `outfmt=json`, `num_speakers=2`. Le serveur a accepté le paramètre, et le résultat change : il en tient donc compte, même s'il n'est pas documenté pour l'API.

### Temps

```
envoi de pattcast20_182-302.mp3 (3.8 Mo, audio/mpeg)
travail 8065afda-8272-475a-9ead-98f1e2c8a234
[   2.2 s] processing eta_seconds=10
[   7.9 s] completed
écrit : .claude/skills/transcription/releves/2026-10-29/resultat-2voix.json
temps total 9.96 s
```

- Soumis à 08:17:44 UTC. Pas de file d'attente.
- Fini en 7,9 s au plus (6,6 s sans `num_speakers`). L'estimation du serveur disait 10 s.
- 10,0 s en tout. `resultat-2voix.json` fait 76 735 octets, même schéma que `resultat.json`.

### Segments

**11 segments** au lieu de 1, et 414 mots : 317 en `SPEAKER_00`, 97 en `SPEAKER_01`. Aucun segment ne mélange deux locuteurs dans ses mots. Le locuteur suit le rôle : `SPEAKER_01` pose les questions, `SPEAKER_00` répond.

| # | Locuteur | Début, fin (s) | Début du texte |
|---|---|---|---|
| 0 | 01 | 0,03 à 0,98 | « Fréier oder spéider. » |
| 1 | 00 | 0,98 à 1,03 | « 3. » (probabilité 0,06) |
| 2 | 01 | 1,03 à 5,03 | « Okay. Gutt, wéi erkennen ech dann, … ? » |
| 3 | 00 | 5,03 à 5,72 | « Awer iwwerhuelen. » |
| 4 | 00 | 5,73 à 29,97 | « Ne, de Klassiker, … » |
| 5 | 01 | 29,97 à 37,04 | « Dat kennen ech vu mir aus. Dat ass en anert Theema. Mee d'Strecken u sech ass awer net schlecht, wann d'Hënn opstinn. Nee, dat ass. » |
| 6 | 00 | 37,04 à 48,97 | « Dat ass sécher, dat ass ganz gutt. Dat ass wéi bei eis Mënschen och, … » |
| 7 | 01 | 49,65 à 55,52 | « Soll ech dann direkt reagéieren … ? » |
| 8 | 00 | 55,52 à 76,96 | « Virbeugen ass dann u sech … » |
| 9 | 01 | 78,18 à 89,03 | « ofbaut. Wéi ass dat dann beim Déierendokter? … Also mir schwätzen elo mol reng vu medezinneschen hier, … déi et ginn. Dat hänkt elo vu. » |
| 10 | 00 | 89,03 à 119,98 | « Déierendokter of, de klasseschen Déierendokter, … » |

### Changements de locuteur : 9 trouvés, comparés au tableau

| Attendu (tableau plus haut) | Trouvé | Verdict |
|---|---|---|
| vers 5,1 à 5,7 : question, puis réponse | 5,03 | trouvé |
| 29,8 : aparté de l'animatrice | 29,97 | trouvé (+0,2 s) |
| vers 33,5 : retour à l'invitée (incertain) | 37,04 | trouvé, ailleurs : LuxASR laisse « Mee d'Strecken … opstinn » à l'animatrice et donne « Dat ass sécher, dat ass ganz gutt » à l'invitée. À la lecture, c'est plus cohérent que mon tableau. La vraie frontière est sans doute à 36,6 s (« Nee, dat ass sécher » est de l'invitée), donc environ 0,4 s de retard. |
| 49,7 : question | 49,65 | trouvé |
| 55,2 : réponse | 55,52 | trouvé (+0,3 s) |
| 78,6 : question | 78,18 | trouvé (−0,4 s : le dernier mot de l'invitée, « ofbaut. », passe à l'animatrice) |
| 82,1 : réponse | 89,03 | trouvé, plus tard : LuxASR donne à l'animatrice « Also mir schwätzen elo mol reng vu medezineschen hier, … ». C'est vraisemblablement elle qui précise sa question, et mon tableau se trompait. L'invitée commence à « Dat hänkt elo vum Déierendokter of » (88,2 s), et la coupure tombe au milieu de cette phrase (environ 0,8 s de retard). |
| (aucun) | 0,98 et 1,03 | **faux** : un segment de 50 ms, « 3. » (probabilité 0,06), à la place du « Jo. » du premier essai |

**Bilan** :
- Les 6 changements sûrs du tableau, plus l'incertain, sont trouvés. Les écarts vont de −0,4 à +0,8 s, et là où LuxASR s'écarte de mon tableau, c'est vraisemblablement mon tableau qui avait tort.
- Il y a 2 faux changements, tous les deux dans la première seconde.
- Sans `num_speakers` : 0 changement trouvé. Avec `num_speakers=2` : 7 justes et 2 faux.
- Je n'ai toujours pas écouté l'extrait : ces verdicts reposent sur la cohérence du texte.

### Le texte, lui, s'est dégradé

Même audio, mais un texte différent. 414 mots au lieu de 375, et une probabilité moyenne de 0,913 au lieu de 0,937.

- **Une boucle de répétition** de 28,3 à 28,9 s : « deen vun » 18 fois de suite (« dat ass esou eent vun deen vun deen vun … v fir Arthros »). Le premier essai donnait « Dat ass esou eent vun deen typeschen Unzeeche fir Arthroos ».
- « Arthroos » devient « dach d'Ros » (2,1 à 4,6 s).
- « Jo. » devient « 3. ».
- Un « Awer iwwerhuelen. » apparaît à 5,0 s (probabilité 0,31 à 0,49).
- À l'inverse, le faux mot du premier essai (« Wéi ginn. Wéi ginn. » à 87,4 s) a disparu.

C'est une hypothèse, non vérifiable d'ici : le service transcrirait l'audio par tranches de locuteur, et des tranches plus courtes donnent moins de contexte à Whisper.

### Pour la page (aucun fichier modifié)

- Le normaliseur mis à jour sur la branche lit `resultat-2voix.json` : 40 segments (les segments longs sont redécoupés), 10 tours de parole, `diarise: true`. Ses tests passent : 20 sur 20.
- `num_speakers` est ce qui rend la séparation exploitable sur ce cas. Proposer le nombre de voix dans le formulaire est justifié, et le laisser sur AUTO revient au premier essai.
- La boucle « deen vun » × 18 passera telle quelle dans la transcription affichée, puisque la page ne touche jamais au texte reconnu.
