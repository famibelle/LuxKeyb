# Banc d'essai de la dictée

Mesure la qualité et la latence de la dictée vocale, à deux niveaux qu'il ne
faut pas confondre :

- **le modèle** — une passe sur un fichier entier, comme le ferait n'importe quel
  banc d'essai ASR ;
- **le produit** — le découpage temps réel de `SttSession` rejoué énoncé par
  énoncé, avec ses passes partielles, ses tours sautés et sa passe finale.

Les deux ne disent pas la même chose, et l'écart est le résultat principal :
mesuré sur des fichiers de 60 s, `tiny` rend 48,7 % de WER et `base` 36,6 %,
soit douze points qui ressemblent à un arbitrage de confort. Mesurés sur des
énoncés de quelques secondes — le régime réel d'un clavier — ils rendent 72,1 %
et 36,0 %. `tiny` s'effondre sans contexte, `base` non.

## Lancer

```bash
cmake -B build -S stt/bench && cmake --build build -j
python stt/bench/prepare_dataset.py --work W --files 50 --slice-files 15
python stt/bench/run_bench.py --stage full   --work W --model M --binary build/lux_bench --out R/tiny_full.json
python stt/bench/run_bench.py --stage stream --work W --model M --binary build/lux_bench --out R/tiny_stream.json
python stt/bench/analyze_stream.py R/tiny_stream.json W R/tiny_stream_summary.json
python stt/bench/report.py R rapport.html
```

Pour comparer une autre taille de modèle, `convert_model.py` accepte `--repo` et
`--quant` — vers un dossier de travail, **jamais vers `assets/`** : un modèle de
60 Mo qui y atterrit part dans l'APK au build suivant sans que personne ne l'ait
demandé.

## Ce qui compte dans la conception

**Parité stricte avec l'APK.** `lux_bench.cpp` recopie les paramètres de décodage
de `whisper_jni.cpp` un par un. `whisper-cli` de l'exemple amont n'expose pas
`single_segment` et ne reproduit pas la cadence : mesurer avec lui aurait mesuré
un autre logiciel. Toute modification d'un des deux fichiers doit être répercutée
dans l'autre.

**Horloge virtuelle, calcul réel.** En mode `stream`, l'audio arrive par blocs de
64 ms comme le ferait `AudioRecorder`, et une passe part quand 0,6 s d'audio
nouveau s'est accumulé *et* que le worker est libre — ce qui reproduit l'échec du
`compareAndSet` et donc les tours sautés. Seule l'arrivée de l'audio est simulée ;
chaque passe whisper est réellement exécutée et chronométrée.

**Le corpus ne rentre pas dans le dépôt.** `Akabi/Luxemburgish_Press_Conferences_Gov`
ne déclare aucune licence : l'audio et les WAV dérivés restent dans le dossier de
travail, seuls les agrégats de `results/` sont versionnés. Même politique que
ParaLux pour le dictionnaire.

**Le cache est indexé sur le fichier source, pas sur le rang du tirage.** Nommer
`001.f32`, `002.f32` paraît plus lisible et rend le dossier de travail
silencieusement faux dès que le tirage change : un passage interrompu laisse des
fichiers que le suivant réutilise sous une autre référence. Constaté ici —
8 fichiers sur 50 mesurés contre la transcription d'un autre, avec des WER de 96
à 152 % qui ressemblaient à s'y méprendre à une panne du modèle.

**Le seuil de silence est calibré, pas deviné.** Une conférence de presse n'a pas
de vrai silence ; à -32 dB aucune respiration n'est vue et toutes les tranches
tombent sur la borne des 15 s, en plein mot. Le tableau des mesures est dans
`prepare_dataset.py`.

## Ce que le banc ne mesure pas

La latence sur un téléphone — tout est mesuré sur un hôte x86 à 6 cœurs, et le
rapport ARM/x86 sur ggml va couramment de 3 à 8×. Le comportement mémoire sous
pression. Et le harnais tourne dans un processus isolé, là où l'IME subit en plus
le rendu du clavier et le throttling thermique : ses chiffres sont un plancher
optimiste.
