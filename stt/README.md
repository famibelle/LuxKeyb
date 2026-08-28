# Dictée vocale luxembourgeoise

Reconnaissance vocale embarquée, **entièrement hors ligne**. L'audio ne quitte
jamais l'appareil : c'est ce qui laisse la politique de confidentialité publiée
inchangée, et ce n'est pas un détail d'implémentation mais la raison d'avoir
écarté l'API distante.

## Modèle

[`unilux/whisper-tiny-v1-luxembourgish`](https://huggingface.co/unilux/whisper-tiny-v1-luxembourgish),
projet **LuxASR**, Université du Luxembourg — Département des sciences humaines.

- Architecture : Whisper tiny affiné, 37,8 M paramètres, 4 couches, `d_model` 384
- Licence : **[open-mdw](https://www.openmdw.org)** (Open Model, Data & Weights)
  — permissive, usage commercial autorisé. À ne pas confondre avec la CC BY-NC
  des corpus du dictionnaire, qui, elle, interdit la redistribution commerciale.
- Asset embarqué : `ggml-lb-tiny-q5_1.bin`, 31 Mo

**La qualité en luxembourgeois n'est pas mesurée.** Aucun WER n'est publié pour
ce modèle, et LuxASR fait tourner `large-v3-turbo` — non diffusé — sur son propre
site et son API. `tiny` est le plus faible de la famille. Le seul contrôle
effectué est un test de fumée qui vérifie la conversion, pas la précision.
Mesurer un WER sur du vrai audio luxembourgeois reste à faire.

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
chaque commit de la branche et le publie en préversion sous un tag fixe :

    https://github.com/famibelle/LuxKeyb/releases/download/labs/LetzebuergeschClavier-Labs.apk

La préversion est marquée `prerelease` et n'est jamais « latest » : le bouton de
téléchargement du site continue de servir les versions stables.

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

Conséquence assumée : le coût d'une passe croît avec la durée de l'énoncé. La
dictée se termine d'elle-même à 30 s, ou après 1,6 s de silence.

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

Non vérifié : la précision en luxembourgeois, la latence réelle sur un téléphone,
et le comportement mémoire sous pression. L'émulateur disponible est x86_64,
environ **50× plus lent que l'hôte** pour ggml et sujet à des artefacts de rendu
sous swiftshader : ses temps ne disent rien d'un appareil ARM. x86_64 n'est
inclus qu'en build debug, précisément pour garder ce chemin de test ouvert.
