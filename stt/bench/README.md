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

## Banc de la dictée en ligne (LuxASR)

Deux scripts, à lire ensemble :

```bash
python stt/bench/prepare_dataset.py --work W --files 6 --slice-files 6
python stt/bench/bench_luxasr.py --work W --out R/luxasr_service.json
python stt/bench/bench_device.py --work W --out R/luxasr_tel.json \
       --device 192.168.1.236:36331 --clips 20
python stt/bench/compare_luxasr.py --hote R/luxasr_service.json \
       --tel R/luxasr_tel.json --work W
```

`bench_luxasr.py` parle au service depuis le poste, dans le protocole exact de
`LuxAsrSession` (PCM 16 bits, 16 kHz, trames de 160 ms, `stop` final) : c'est le
service seul, avec de l'audio parfait. `bench_device.py` fait rejouer les mêmes
tranches par le haut-parleur du téléphone devant son propre micro et laisse
l'application faire tout le reste. L'écart entre les deux est le coût de la
chaîne acoustique et du détecteur de fin d'énoncé, mesuré et non supposé.

Ce qui est délicat dans le banc sur téléphone :

- **Rien ne doit changer d'application pendant la mesure.** Basculer vers un
  lecteur audio déclencherait `onFinishInput` et couperait la dictée. La page
  servie par `adb reverse` est donc à la fois le lecteur et le champ de saisie,
  et elle renvoie ses horodatages au poste — d'où le délai réel entre la fin de
  la parole et le texte.
- **Chrome n'autorise le son qu'après un geste.** Un appui sur « Démarrer »
  lance un fichier de silence sur l'élément `<audio>` ; les lectures suivantes,
  programmées, passent alors sans refus.
- **Les appuis sont conditionnés à Chrome au premier plan.** Un enchaînement
  raté envoie sinon des appuis aveugles dans l'application qui se trouve
  dessous — ici une conversation personnelle, à un doigt du bouton « envoyer ».
  `assurer_chrome()` interrompt le banc plutôt que de taper à l'aveugle.
- **Le clavier n'apparaît pas dans `uiautomator dump`** sur One UI 4 : le
  bouton micro est retrouvé par repli géométrique, à vérifier sur une capture
  avant de faire confiance aux chiffres.
- **Deux horloges.** Les estampilles de la page viennent du téléphone, celles
  du pilote du poste ; les événements sont donc appariés par index, jamais par
  date, et les durées ne se calculent qu'entre événements d'une même horloge.

**Notation.** Le corpus ne donne de référence que par fichier de 60 s. Chaque
tranche est donc notée par alignement d'infixe — début et fin de la référence
gratuits — ce qui est optimiste par construction et le reste des deux côtés.
`compare_luxasr.py --work` ajoute le WER de pipeline, tranches recollées contre
la référence entière : c'est le protocole du banc embarqué du 28 août, donc le
seul chiffre directement comparable à ses 72,1 % (tiny) et 36,0 % (base).

## Le grain de la dictée est un réglage du serveur

`accumulated_text` n'arrive pas quand la personne parle, mais quand le service
décide de décoder : par défaut toutes les 5 s d'audio, ou sur une pause de
0,8 s, plafonné à 30 s. Le message `connected` annonce ces valeurs dans
`config.chunk_params`, et `processing` dit ensuite pourquoi il a décodé —
`periodic_timeout`, `periodic_pause`, `silence_detected`.

Elles sont réglables, ce que leur propre client web n'exploite pas : il
n'envoie que `language`, `use_context` et les options de traduction. Le serveur
accepte pourtant un message

```json
{"type": "config", "language": "lb",
 "chunk_params": {"periodic_send_interval": 2.0, "silence_threshold": 0.5,
                  "max_chunk_duration": 30.0}}
```

et le réémet en accusé. **Uniquement sous cette forme imbriquée** : les mêmes
clés à plat sont ignorées en silence, sans accusé ni erreur — c'est ce qui rend
le premier essai trompeur. Rien de tout cela n'est documenté, sur une API déjà
non authentifiée ; si le serveur cessait de lire ces clés on retomberait sur son
défaut sans rien casser.

`compare_grain.py` apparie deux passages de `bench_device.py` sur les mêmes
tranches. Il faut les enchaîner dos à dos sans bouger le téléphone : entre deux
séances, l'acoustique de la pièce se mélange à l'effet cherché.

```bash
python3 bench_device.py --work $W --out $W/a.json --device $D --parents 002,006 --clips 19
# installer l'APK modifié, puis
python3 bench_device.py --work $W --out $W/b.json --device $D --parents 002,006 --clips 19
python3 compare_grain.py --a $W/a.json --b $W/b.json --nom-a défaut --nom-b réglé
```

Mesuré ainsi le 30 août, 19 tranches, seul l'APK changeant entre les deux :

| | défaut 5,0 / 0,8 | réglé 2,0 / 0,5 |
|---|---|---|
| WER pondéré | 31,7 % | 37,0 % |
| WER médian | 27,9 % | 30,8 % |
| 1er texte | 6,53 s | 4,32 s |
| texte avant la fin de la parole | 6/19 | 13/19 |
| mises à jour | 34 | 49 |

L'écart apparié a une médiane **nulle** : la moitié des tranches ne bouge pas,
et la moyenne perd 4,9 points à cause de quelques-unes qui se dégradent
franchement. Compter les mises à jour ne suffit pas à dire que la dictée est
devenue progressive — la dernière remplace toujours la précédente, y compris
quand tout est arrivé après coup ; le critère retenu est donc `premier texte <
durée de l'énoncé`.
