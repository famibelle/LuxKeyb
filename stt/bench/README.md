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

## Sonde du protocole : ce que devient le décodage quand on cesse d'émettre

Trois questions dont dépend l'architecture de la dictée, et auxquelles rien dans
le protocole publié ne répond. `probe_gap.py` les tranche en jouant cinq
conditions sur le même audio, le trou expérimental étant placé dans une pause
que le locuteur a réellement laissée — couper ailleurs mesurerait la découpe et
non la reprise :

```bash
python stt/bench/probe_gap.py --work $W --out R/sonde.json \
       --fichiers 3 --repet 2 --duree 22 --trou 3 --queue 8
```

`plein` · `silence_insere` (le trou est **émis**) · `coupure` (rien n'est émis,
l'horloge avançant) · `queue_silence` et `queue_coupure` (même distinction, en
fin d'énoncé avant « stop »). La chronologie est identique dans tous les cas :
seul change le droit du silence à partir sur le réseau. Une hypothèse n'est
comptée « dans la fenêtre » que 1,5 s après son ouverture, au-delà du délai de
traitement du service (~270 ms) — sans cette marge on compterait le décodage de
ce qui précédait le trou.

Mesuré le 1er septembre 2026, 3 fichiers × 5 conditions × 2 passages
(`results/2026-09-01-sonde-flux.json`) :

| | plein | silence émis | flux suspendu |
|---|---|---|---|
| WER pondéré, trou médian | 40,1 % | 36,6 % | 37,0 % |
| passes dans la fenêtre de 6,5 s (queue) | — | 3 | 0 |
| répétition, max sur 30 passages | 0 % | 0 % | 0 % |

1. **Le découpeur compte les échantillons reçus, pas l'horloge.** Sur la même
   fenêtre, 8 s de silence émises déclenchent 3 hypothèses et 8 s sans émission
   aucune. Suspendre le flux suspend le décodage : un robinet audio est jouable
   côté client, sans rien demander au serveur.
2. **Le contexte survit à l'interruption** : +0,4 pt de WER, du bruit. Et les
   deux conditions à trou passent **sous** la condition sans trou — une pause
   offre au service une frontière de découpe à un endroit que le locuteur a
   choisi. Une bonne frontière vaut ici 3,5 points.
3. **La queue hallucinée ne se reproduit pas à 8 s.** Ni gonflement du texte
   (76 mots médians contre 78) ni boucle. L'hallucination observée le 29 août à
   10 s de blanc reste une observation unique ; elle ne justifie pas à elle
   seule un hangover de 1,5 s. Elle se reproduit en revanche franchement à
   ~30 s — voir le volet téléphone ci-dessous.

## Parole enchaînée : le banc que les tranches ne peuvent pas remplacer

Tout ce que `bench_luxasr.py` et `bench_device.py` mesurent passe par `slices/`,
dont les tranches sont découpées **aux silences** (médiane 3,7 s). C'est le
régime « phrase par phrase », le seul où une pause coïncide forcément avec une
frontière de phrase — donc le seul où la segmentation par pauses ne peut pas se
tromper. Le banc était aveugle par construction au reproche qu'on lui fait.

`bench_continu.py` rejoue de la parole enchaînée dans deux découpes et deux
régimes :

```bash
# une à trois phrases : la dictée que ce clavier veut servir
python stt/bench/bench_continu.py --work $W --out R/enonce.json --decoupe enonce
# fichiers de 60 s : le pire cas, la queue de la distribution des usages
python stt/bench/bench_continu.py --work $W --out R/60s.json --decoupe fichier
```

`continu` tient une seule session ; `hangover` rejoue l'application telle
qu'elle est — `SILENCE_HANGOVER_MS` termine l'énoncé, l'utilisateur rouvre une
session, les textes se recollent. **Le régime `hangover` y est optimiste** : la
reprise est instantanée (`--reprise 0`), alors qu'en vrai il faut d'abord
s'apercevoir que le micro s'est fermé, et tout ce qui est dit entre-temps est
perdu. Une coupure est comptée intra-phrase quand le segment qu'elle termine ne
finit pas sur une ponctuation forte, ou quand le suivant démarre en minuscule :
c'est le jugement du service sur sa propre sortie, le texte rendu étant ponctué
et capitalisé.

`vad.py` est le port fidèle de `detecterFinDEnonce()` — mêmes constantes, même
cadence de blocs, plancher de bruit adaptatif compris. Toute divergence avec le
Kotlin fausserait le seul chiffre qui compte ici ; si une constante bouge
là-bas, elle doit bouger ici.

Mesuré le 1er septembre 2026, 22 énoncés de 8 à 22 s (médiane 12 s) tirés de
6 fichiers (`results/2026-09-01-parole-continue.json`) :

| | continu | hangover |
|---|---|---|
| WER pondéré | 38,3 % | 35,5 % |
| WER médian | 34,7 % | 33,9 % |
| sessions pour 22 énoncés | 22 | 24 |
| coupures intra-phrase | 0 | 2 |

Écart apparié : **médiane nulle**, moyenne −1,6 pt en faveur du régime coupé.
Autrement dit, dans le régime visé, **la coupure sur pause ne coûte pas
d'exactitude — elle coûte des interruptions** : 2 énoncés sur 22 (9 %) sont
coupés au milieu d'une phrase, et le WER n'en souffre pas parce que la pause
offre au service une frontière propre. Allonger le hangover supprime
l'interruption mais rend cette frontière ; le robinet audio obtient les deux.

Le détecteur seul, sans aucune session, donne la même chose sous un autre
angle : 2 énoncés sur 22 dans le régime visé, mais 7 coupures sur 6 minutes de
monologue, soit 1,2 par minute. Lire les deux ensemble — la dictée courte est
peu exposée, la dictée longue beaucoup.

### Sur le téléphone, le détecteur se déclenche bien moins

`bench_device_continu.py` rejoue les fichiers de 60 s au haut-parleur et laisse
le détecteur embarqué décider.

```bash
python stt/bench/bench_device_continu.py --work $W --out R/tel.json \
       --device 192.168.1.37:34737 --fichiers 3
```

**La coupure ne se lit pas dans `logcat`** : l'APK Labs est un build release et
sur ce One UI aucune ligne `LuxAsrSession` n'atteint le tampon (vérifié — on y
voit Chromium et le système, rien de l'IME). Elle se lit dans le DOM : la dictée
arrive en texte de composition, et `finishComposingText()` produit un
`compositionend` dans le champ de la page. C'est l'application qui annonce sa
coupure, à l'instant où elle la décide. Autocontrôle : chaque passage doit finir
sur au moins un `compositionend` ; zéro ne veut pas dire « aucune coupure » mais
« le signal ne remonte pas ».

Un premier instrument déduisait la coupure d'un silence du texte. Il comptait
faux — 5 s sans mise à jour arrivent aussi au démarrage, avant la première
hypothèse — et les ré-appuis déclenchés sur ces fausses coupures **arrêtaient**
la dictée en cours, un second appui valant « stop ». Ne pas y revenir : ce
régime-là mesurait l'instrument.

Deux constats, le 1er septembre 2026 :

- Sur 3 minutes de parole rejouée, **le détecteur n'a pas refermé le micro une
  seule fois**, là où le même audio propre en prédisait 6 coupures. Le bruit de
  la pièce tient le seuil adaptatif au-dessus du silence. En conditions réelles,
  notre segmentation par pauses mord donc beaucoup moins qu'en laboratoire — ce
  qu'il faut savoir avant d'attribuer une gêne observée à cette cause plutôt
  qu'au découpage du service.
- Un passage a tenu jusqu'au plafond de 90 s, soit 60 s d'audio puis ~30 s de
  silence de pièce : **270 mots pour 192 attendus, 19,9 % de répétition, WER
  61,5 %** contre 27,8 % sur le même fichier quand la session s'arrête à temps.
  La queue hallucinée, introuvable à 8 s de blanc, est bien là à 30 s.
