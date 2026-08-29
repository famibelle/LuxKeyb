# Dictée vocale luxembourgeoise

> ⚠️ **Sur cette branche (`feat/luxasr-online`), la reconnaissance est
> distante.** L'audio quitte l'appareil, le modèle whisper est exclu de l'APK,
> et cette branche est un support de démonstration qui ne doit pas être
> fusionnée. Voir [« La dictée en ligne »](#la-dictée-en-ligne--cette-branche)
> juste en dessous. Tout le reste de ce fichier décrit la dictée **embarquée**,
> celle de `feat/speech-to-text-lb` : le code en est toujours là, compilé et
> empaqueté, mais inerte faute de modèle à charger.

Reconnaissance vocale embarquée, **entièrement hors ligne**. L'audio ne quitte
jamais l'appareil : c'est ce qui laisse la politique de confidentialité publiée
inchangée, et ce n'est pas un détail d'implémentation mais la raison d'avoir
écarté l'API distante — jusqu'à ce que la mesure du 28 août 2026, plus bas,
montre que le modèle embarqué n'atteint pas la qualité qui rendrait la dictée
utile.

## La dictée en ligne — cette branche

Le service [LuxASR](https://luxasr.uni.lu) de l'Université du Luxembourg fait
tourner `large-v3-turbo`, que sa licence ne permet pas d'embarquer. Il rend le
même énoncé correct, ponctué et capitalisé, en **~270 ms** de traitement, là où
le modèle embarqué rend 72 % de WER (chiffres et protocole dans la section
suivante). C'est la seule raison d'être de cette branche.

| Élément | Rôle |
|---|---|
| `stt/DictationSession.kt` | ce que l'IME attend d'une dictée : `start`, `stop`, `isActive`, `isBusy` |
| `stt/LuxAsrSession.kt` | client WebSocket vers `wss://luxasr.uni.lu/prod/ws/transcribe` |
| `stt/SttSession.kt` | l'implémentation embarquée, inchangée, désormais derrière la même interface |

L'interface existe pour que l'arbitrage tienne en une ligne au point de
construction, pas pour laisser croire que les deux se valent.

**Protocole**, relevé dans le client `scriptrt.js` v2.1.0 du service et vérifié
contre sa version v2.3.0 : PCM 16 bits little-endian, 16 kHz mono, en trames
binaires ; messages de contrôle en JSON. Le serveur découpe lui-même sur les
silences et gère le contexte entre segments — c'est exactement ce que notre
découpage local perd, et pourquoi la sortie est ponctuée alors que la nôtre ne
l'est pas.

**Ce que ça coûte.** L'audio quitte l'appareil, ce que la politique de
confidentialité publiée affirme ne jamais arriver. D'où : le bandeau affiche
🌐 LuxASR pendant toute la dictée, la préversion est publiée sous un tag et un
nom de fichier distincts (`labs-luxasr`), le flashcode du site continue de
servir la version hors ligne, et le site de LuxASR demandant un accord préalable
à toute intégration, cette branche est le support de cette demande — pas son
contournement.

**Le modèle est exclu de l'APK**, qui passe de 38,4 Mo à 6,22 Mo. L'exclusion
est faite dans `androidResources.ignoreAssetsPattern` plutôt que laissée à la
CI : un développeur qui a converti le modèle pour l'autre branche l'a dans ses
assets et le réempaquetterait sans s'en apercevoir. La CI vérifie ensuite son
absence de l'APK produit — l'exclusion est le moyen, la vérification est la
preuve. Le garde-fou de `build.gradle` est donc lui aussi inversé : il ne refuse
plus une release sans modèle, il signale un modèle présent dans les assets.

La bibliothèque native, elle, reste compilée et empaquetée : `libwhisper` et les
trois `libggml` pèsent 3,73 Mo, soit 60 % de l'APK une fois le modèle parti,
pour du code qui ne peut rien faire ici. Elles restent parce que retirer
`externalNativeBuild` ferait diverger la configuration CMake, la CI et les
vérifications d'APK sur une branche dont tout l'intérêt est d'être jetable. Le
jour où la taille compte, c'est le premier endroit où couper.

**Échecs.** Tout échec du WebSocket rendait `MODEL_UNAVAILABLE`, dont le libellé
accuse le téléphone (« Dictée indisponible sur cet appareil ») quand c'est le
réseau qui manque. `SERVICE_UNREACHABLE` existe depuis, avec sa propre phrase.
Il n'y a **aucun repli hors ligne** : sans connexion, pas de dictée.

## Modèle

[`unilux/whisper-tiny-v1-luxembourgish`](https://huggingface.co/unilux/whisper-tiny-v1-luxembourgish),
projet **LuxASR**, Université du Luxembourg — Département des sciences humaines.

- Architecture : Whisper tiny affiné, 37,8 M paramètres, 4 couches, `d_model` 384
- Licence : **[open-mdw](https://www.openmdw.org)** (Open Model, Data & Weights)
  — permissive, usage commercial autorisé. À ne pas confondre avec la CC BY-NC
  des corpus du dictionnaire, qui, elle, interdit la redistribution commerciale.
- Asset embarqué : `ggml-lb-tiny-q5_1.bin`, 31 Mo — **exclu de l'APK sur
  cette branche**, où il ne serait jamais chargé

**La qualité est mesurée depuis le 28 août 2026, et elle disqualifie `tiny`.**
Sur 50 min de conférences de presse gouvernementales luxembourgeoises
(`Akabi/Luxemburgish_Press_Conferences_Gov`, transcriptions humaines), `tiny`
rend **48,7 % de WER** en une passe sur 60 s de parole continue. Mais le chiffre
qui compte est celui du régime réel d'un clavier — des énoncés de quelques
secondes — et là il tombe à **72,1 %** : privé de contexte, `tiny` s'effondre.

`base` ne s'effondre pas : 32,9 % en continu, **36,0 % sur des énoncés de 4 s**,
soit 3,1 points de perte contre 22,9. Dans le régime qui nous intéresse, l'écart
entre les deux modèles n'est pas de douze points mais de **trente-six** — la
moitié des erreurs. Il coûte +27,5 Mo d'asset et ×1,9 de calcul. Mesurer les
modèles sur des fichiers longs, comme le fait tout banc d'essai ASR, aurait
laissé croire à un choix de confort ; c'en est un de viabilité.

Le protocole, le harnais et le rapport sont dans [`bench/`](bench/). Deux
réserves à ne pas perdre de vue : la carte du modèle ne publie pas ses données
d'entraînement, donc une contamination par ce corpus public est plausible et le
WER mesuré est un *plafond* ; et tous les temps ci-dessous sont ceux d'un hôte
x86, jamais d'un téléphone.

## L'asset n'est pas versionné

31 Mo dans l'historique git, c'est définitif, et une réécriture casserait la base
de fusion partagée avec KreyolKeyb. Le modèle suit donc le même chemin que le
dictionnaire : le job CI `generate-stt-model` le régénère et le transmet aux
jobs de build par `upload-artifact` — jamais par le checkout.

**Après un clone, il faut donc le construire une fois :**

```bash
python stt/convert_model.py --output android_keyboard/app/src/main/assets
```

Le script documente les trois pièges de la conversion (safetensors seuls,
`max_length` déplacé par transformers, filtres mel absents du dépôt HF).

Sans lui, rien ne casse — un asset absent est simplement absent de l'APK — mais
le clavier ship un bouton micro qui s'excuse à chaque appui. C'est pourquoi
`build.gradle` **refuse les tâches de release** quand le fichier manque ou fait
moins de 25 Mo, et se contente d'un avertissement en debug.

## Essayer sans compiler

Le workflow **Labs** (`.github/workflows/labs.yml`) construit un APK signé à
chaque commit de la branche et le publie en préversion sous un tag fixe, un tag
par canal, pour que les deux dictées ne s'écrasent pas l'une l'autre :

| Canal | Branche | Adresse, invariante |
|---|---|---|
| Dictée embarquée | `feat/speech-to-text-lb` | `releases/download/labs/LetzebuergeschClavier-Labs.apk` |
| Démonstration en ligne | `feat/luxasr-online` | `releases/download/labs-luxasr/LetzebuergeschClavier-LuxASR-Demo.apk` |

Pages d'installation, avec flashcode : [labs.html](https://famibelle.github.io/LuxKeyb/labs.html)
et [labs-luxasr.html](https://famibelle.github.io/LuxKeyb/labs-luxasr.html).
Elles vivent sur `main`, seule branche que GitHub Pages publie.

Les deux préversions sont marquées `prerelease` et ne sont jamais « latest » :
le bouton de téléchargement du site continue de servir les versions stables.

## Architecture embarquée

| Élément | Rôle |
|---|---|
| `app/src/main/cpp/whisper.cpp` | sous-module épinglé sur `v1.7.4` |
| `app/src/main/cpp/whisper_jni.cpp` | pont JNI : ouvrir, transcrire, fermer |
| `stt/SttEngine.kt` | enveloppe Kotlin, cycle de vie du contexte natif |
| `stt/AudioRecorder.kt` | capture 16 kHz mono → flottants [-1, 1] |
| `stt/SttSession.kt` | découpage temps réel, VAD, hypothèses successives |
| `stt/MicPermissionActivity.kt` | demande RECORD_AUDIO (impossible depuis un IME) |

### Le « temps réel » est une stratégie, pas une propriété du modèle

Whisper consomme une fenêtre mel de 30 s et n'a aucun mode streaming. La dictée
re-transcrit donc, toutes les 900 ms, **la totalité** de l'énoncé accumulé
depuis l'appui sur le micro. Chaque passe rend une phrase complète qui annule et
remplace la précédente — ce qui correspond exactement au texte en composition
d'un IME (`setComposingText`), et évite d'avoir à recoller des fragments.

On a longtemps écrit ici que le coût d'une passe croissait avec la durée de
l'énoncé. **C'est faux au premier ordre**, et la mesure le montre : une passe
coûte 824 ms sur 0–2 s d'audio et 1 030 ms sur 10–16 s. L'audio est multiplié par
huit, le coût par 1,25. L'encodeur de Whisper travaille toujours sur une fenêtre
de 30 s, remplie de silence le reste du temps, et il pèse 80 à 95 % d'une passe ;
seul le décodage grandit.

La borne des 30 s n'est donc pas là pour contenir un coût qui s'emballe, mais
parce que la fenêtre mel s'arrête là.

**Mesuré sur un Samsung ancien, le 28 août 2026 :** 4,5 s d'audio → 6 571 ms,
6,9 s → 6 183 ms, 11,0 s → 6 164 ms. Le coût est plat, et six fois celui de
l'hôte x86. Sur cet appareil, `tiny` fait attendre six secondes après la fin de
la phrase et n'affiche aucune hypothèse pendant qu'on parle.

### `audio_ctx` : la fausse bonne idée, mesurée et écartée

`whisper_full_params.audio_ctx` tronque le contexte de l'encodeur et paraît donc
être *le* levier : il attaque le poste qui coûte tout. Il a été mesuré, et il ne
marche pas.

| `audio_ctx` | WER `tiny` | WER `base` | gain |
|---|---|---|---|
| 1500 (défaut) | 72,1 % | **36,0 %** | 1,00× |
| 768 | 105,6 % | 41,3 % | 2,2–2,3× |
| 512 | 279,8 % | 79,2 % | 2,6–3,5× |
| 384 | 407,9 % | 111,2 % | 1,5–4,8× |
| 256 | 574,6 % | 218,9 % | 1,4–5,7× |

Au-delà de 768 le WER dépasse 100 % : le modèle n'est plus imprécis, il boucle.
La raison est structurelle — les 1 500 positions ne sont pas un paramètre de
configuration mais une propriété apprise, et les tronquer met le modèle hors de
sa distribution d'entraînement. Dimensionner `audio_ctx` sur la durée de
l'énoncé, qui semble pourtant l'évidence, donne 177 % de WER avec `base`.

Le seul point exploitable est **768**, qui coûte 5,3 points à `base` pour 2,3× de
vitesse. À n'envisager que si la latence bloque tout le reste.

Corollaire à ne pas perdre : à contexte réduit, whisper rend parfois une
séquence UTF-8 tronquée. `whisper_jni.cpp` assainit désormais la sortie avant
`NewStringUTF()`, dont le comportement sur des octets malformés est indéfini —
en luxembourgeois, où é, ë et ä sont des séquences de deux octets, le cas n'a
rien d'exotique.

La dictée se termine d'elle-même à 30 s, ou après 1,6 s de silence.

### Mémoire — le point sensible

whisper alloue **~165 Mo de tampons de calcul** à l'initialisation, dimensionnés
au pire cas et indépendamment des paramètres de décodage (mesuré : encode
64,8 Mo, decode 95,9 Mo, cross 3,9 Mo), auxquels s'ajoutent les 31 Mo du modèle.
C'est beaucoup pour un processus IME, que le système tue volontiers en
arrière-plan.

D'où la règle : **le contexte n'est jamais chargé à l'ouverture du clavier**,
seulement au premier appui sur le micro, et il est rendu dès `onFinishInput()`.
Si des remontées de terrain montrent des morts du processus malgré cela,
l'échappatoire est un service en `android:process=":stt"` — plus de code (IPC,
audio déporté), à ne payer que si le besoin est démontré.

### Placement du micro

Bord droit de la première rangée de suggestions. La rangée du bas du clavier
pèse déjà exactement 12 unités de largeur, et l'appui long sur la barre d'espace
est pris par le sélecteur d'IME du système : c'était la seule place libre.

## Deux pièges déjà payés

**`temperature_inc` doit rester à 0.** Par défaut whisper rejoue le décodage
jusqu'à six fois en montant la température quand l'entropie ou la
log-probabilité sortent des seuils — ce qui arrive sur tout silence ou passage
bruité. Pour un clavier, c'est une latence non bornée après l'appui sur
« stop » : constaté sur un enregistrement de 4 s qui a brûlé 121 s de CPU sans
jamais rendre la main.

**`whisper_context` n'est pas thread-safe.** Deux `whisper_full()` concurrents,
ou un `whisper_free()` pendant une transcription, corrompent la mémoire du
processus. `SttEngine` sérialise donc `load`/`transcribe`/`release` sur un
verrou, `abort()` restant volontairement dehors. Ne pas retirer ce verrou au
motif que `SttSession` n'a qu'un exécuteur mono-thread : c'est un invariant
réparti sur deux classes, qu'un futur appelant casserait sans rien voir.

## Vérifié / non vérifié

Vérifié : la conversion produit un modèle qui charge et décode ; les 4 symboles
JNI survivent à R8 malgré `-repackageclasses ''` ; le modèle est bien `Stored`
(non compressé) dans l'APK, condition du mmap par `AAsset_getBuffer()` ; les
127 tests unitaires passent.

Mesuré depuis le 28 août 2026 : la précision (ci-dessus), le coût réel d'une
passe, et la chronologie complète de la dictée — première hypothèse à 1,55 s avec
`tiny` et 2,54 s avec `base`, texte définitif à +0,87 s et +1,79 s, sur l'hôte.
Aucun des 161 énoncés rejoués ne reste sans hypothèse avec `tiny` ; `base` en
laisse 13, c'est-à-dire qu'il n'a presque plus de marge.

Sur la branche en ligne, vérifié : l'APK publié ne contient pas le modèle
(6,22 Mo, la CI le prouve à chaque build), le bandeau distingue « service
injoignable » de « appareil incapable » — constaté sur l'émulateur wifi et
données coupées, le journal donnant `Unable to resolve host "luxasr.uni.lu"` —
et la transcription revient ponctuée et capitalisée. Non vérifié : le
comportement du service sous une connexion lente plutôt qu'absente, et sa
disponibilité hors des heures de bureau.

Non vérifié pour la dictée embarquée : la latence réelle sur un téléphone,
et le comportement mémoire sous pression. L'émulateur disponible est x86_64,
environ **50× plus lent que l'hôte** pour ggml et sujet à des artefacts de rendu
sous swiftshader : ses temps ne disent rien d'un appareil ARM. x86_64 n'est
inclus qu'en build debug, précisément pour garder ce chemin de test ouvert.
