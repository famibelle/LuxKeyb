# Essai de Lëtzebuergesch Réunion contre les vrais services, 29 octobre 2026

Appareil : émulateur `pixel9` (Android 16, 1080 × 2424, densité 420, GPU `swangle_indirect`).
Build : `Letzebuergesch_Reunion_v0.1.0_debug.apk`, depuis `main` à `54923057`.
Aucune modification du code de l'application pendant l'essai.

## 1. Construction, installation, tests sur appareil

- `./gradlew installDebug` : **réussi** (4 min 6 s, premier build).
- `./gradlew connectedDebugAndroidTest` : **7 tests sur 7 réussis** (1 min 26 s).
  Détail dans `connectedDebugAndroidTest.txt` :

  | Test | Durée |
  |---|---|
  | `lesEcransSOuvrent` | 33,0 s |
  | `encodeurAac` | 3,9 s |
  | `sdkClaudeEnFlux` | 3,4 s |
  | `directSurUnVraiWebSocket` | 2,2 s |
  | `cleChiffreeParLeKeystore` | 0,5 s |
  | `lotsSurAndroid` | 0,3 s |
  | `normaliseurSurUnReleveReel` | 0,3 s |

  Ces tests tournent contre un `MockWebServer` local : aucun ne touche LuxASR ni Anthropic.
- À savoir : `connectedDebugAndroidTest` **désinstalle l'application** à la fin (comportement
  normal d'AGP). Il faut relancer `installDebug` après, sinon l'essai suivant échoue sur
  « application introuvable ». J'ai réinstallé, ce qui donne un vrai premier lancement.

## 2. Accueil

**01-accueil.png**, premier lancement.
- Ce qui marche : la carte orange « Avant votre première réunion » est claire, en phrases
  simples ; elle dit où part le son, où part le texte, que l'enregistrement est effacé, et
  qu'il faut relire. Le bouton « J'ai compris » est bien visible.
- Ce qui cloche :
  - le bouton « Choisir un fichier » est coupé par le bas de l'écran : on ne voit pas
    qu'il existe tant qu'on n'a pas défilé ;
  - le titre « Lëtzebuergesch Réunion » n'est pas centré sur l'écran : il est décalé
    vers la gauche par le bouton « Réglages ». Pas de lion ni d'icône dans la barre,
    alors que le README parle de la charte du clavier avec le lion.

**02-accueil-apres-jai-compris.png**
- La carte d'avertissement disparaît, les deux choix tiennent sur l'écran, « Mes réunions »
  dit « Vos réunions apparaîtront ici. » : clair.
- Le pied de page crédite LuxASR et Claude, avec la version.
- Rien n'indique que ce petit texte gris s'ouvre par un appui long (c'est voulu : réservé
  à l'installateur).

## 3. Réglages

**03-reglages.png** et **04-reglages-bas.png**
- Ce qui marche : trois cartes (nom, mots à bien écrire, modèle de compte rendu) avec une
  explication sous chaque titre ; la carte « Vos données » redit les règles en mots simples.
- Ce qui cloche :
  - **pas de bouton « Enregistrer »** et aucun message qui dise que c'est enregistré tout
    seul. Une secrétaire se demandera si ce qu'elle a tapé est gardé. (Ça l'est : voir 05.)
  - Les exemples des champs (« Marie Schmit », « BCEE / Kirchberg / Comité exécutif »)
    sont en gris sur un fond gris clair. On peut croire que les champs sont déjà remplis,
    surtout « Mots à bien écrire ».

**05-reglages-nom-garde.png** : j'ai tapé « Essai Medhi » dans « Votre nom », je suis revenu
à l'accueil, puis j'ai rouvert les réglages. Le nom est bien gardé.

**Réglages d'installation.** L'appui long sur le petit texte gris de l'accueil ouvre
**le même écran « Réglages »**, avec une carte « Installation » en plus, **tout en bas**. La
première fois, j'ai cru qu'il ne s'était rien passé : l'écran du haut est identique, et il
faut défiler trois écrans pour trouver la carte. Le titre de la barre devrait changer, ou
l'écran s'ouvrir directement sur la carte.

**06-reglages-installation.png**
- Ce qui marche : la carte est encadrée d'orange, elle explique où prendre la clé, qu'elle
  est chiffrée, et ce que coûte un compte rendu. Le modèle `claude-opus-5-5` est prérempli.
  « Aucune clé : le compte rendu se fera dans l'application Claude. » est clair.
- Ce qui cloche : le réglage « Après une réunion en direct, faire la transcription complète
  (voix séparées). Désactivé, le compte rendu part du direct. » est compréhensible pour
  l'installateur, pas pour une secrétaire (« transcription complète », « part du direct »).
  Ça va puisque c'est la carte d'installation.

**Clé d'API.** Je n'ai pas de clé d'API Claude dans cet environnement : la rédaction
automatique n'a pas pu être essayée. On teste à la place le bouton « Rédiger dans Claude ».

**07-verifier-sans-cle.png** : « Enregistrer et vérifier » avec le champ vide. Message
« Saisissez une clé. », en petit gris, au-dessus du bouton : on le voit à peine.

**08-verifier-cle-fausse.png** : une fausse clé (`sk-ant-cle-fausse-pour-essai`). Moins de
8 s après, « ✗ La clé d'API Claude est refusée. », en rouge. Clair. La vérification
appelle bien Anthropic et lit le refus.

**09-cle-effacee.png** : « Effacer la clé » affiche « Clé effacée. », mais **le champ garde
ses points** (les 28 caractères de la fausse clé). On ne sait pas si c'est effacé ou non.

## 4. Mode fichier

### Premier essai : l'extrait de 2 min en m4a (10 à 20)

**10-choisir-fichier.png** : l'écran « Un enregistrement ». Titre prérempli « Réunion du jeudi
29 octobre », champs Participants et Ordre du jour avec leurs exemples en gris, bouton
« Transcrire » collé en bas. Clair.

**11-transcrire-sans-fichier.png** : « Transcrire » sans fichier affiche « Choisissez d'abord le
fichier. ». Bien. Le bouton reste pourtant bleu vif, comme s'il était actif.

**12-selecteur-fichier.png**, **13-telechargements.png** : c'est le sélecteur d'Android. Il s'ouvre
sur « Récents », vide (« Aucun élément ») : il faut ouvrir le menu ☰ puis « Téléchargements ».
Une secrétaire peut croire que son fichier n'existe pas. Le sélecteur montre aussi
`champs.html` : il ne filtre pas sur l'audio et la vidéo.

**14-fichier-choisi.png** : « reunion_rtl.m4a · 1,5 Mo ». Bien.

**15-accueil-transcription-lancee-par-erreur.png** : **une erreur de ma part, pas de l'appli**.
Quand le clavier s'ouvre, le bouton « Transcrire », collé en bas de l'écran, remonte juste
au-dessus du clavier. Mon toucher prévu pour le champ « Participants » est tombé dessus, et
la transcription est partie avec le titre par défaut et sans participants. J'ai donc manqué
les captures du suivi de ce premier envoi (il a duré moins d'une minute).
- À retenir quand même : **une secrétaire peut faire la même chose**. Avec le clavier ouvert,
  « Transcrire » se trouve à l'endroit où l'on vient de voir le champ Participants
  (confirmé par l'arbre de l'écran : bouton à y = 1357 à 1509 clavier ouvert). Et il n'y a
  aucune confirmation avant l'envoi.
- Résultat de cet envoi (travail LuxASR `5eacea6f…`) : 1 seule voix (`SPEAKER_00` de 0:00
  à 1:59), comme le relevé du 29 octobre sans `num_speakers`.

**16-reunion-fichier-sans-cle.png** : la réunion. « ✓ Fichier reçu », « ✓ Transcription
complète (qui a dit quoi) », « – Compte rendu : Pas de rédaction automatique sur ce
téléphone. » L'encadré orange explique qu'on peut faire rédiger dans l'application Claude,
avec le bouton « Rédiger dans Claude ». Très clair.
- La ligne dit « qui a dit quoi » alors qu'ici une seule voix a été trouvée, et la
  transcription ne nomme personne. Rien ne prévient que les voix n'ont pas été séparées.
  Le README dit que la page Labs prévient dans ce cas ; l'appli, non.

**17-onglet-transcription.png**, **18-transcription-bas.png** : le texte horodaté par
paragraphe ([0:00], [0:45], [1:34]), lisible, en luxembourgeois. Pas d'étiquette de voix (une
seule voix). Le texte n'est pas modifiable ici. En bas : « Envoyer la transcription » et
« Supprimer cette réunion ».

**19-envoyer-transcription.png** : le partage d'Android, avec le texte. Le texte partagé
commence directement par « [0:00] Fréier oder spéider? » : **ni titre, ni date de la
réunion**. Collé dans un e-mail, on ne sait pas de quelle réunion il s'agit. (L'émulateur n'a
pas d'appli de messagerie : je n'ai pas pu aller plus loin.)

**20-rediger-dans-claude.png** : l'appli Claude n'est pas installée sur l'émulateur. Le
bouton ouvre le partage d'Android, avec un texte qui commence par « Tu assistes une secrétaire
de direction au Luxembourg… » (les consignes puis la transcription). L'appli affiche « Le
texte est copié. Choisissez Claude, ou collez-le sur claude.ai. », mais ce message est **à
moitié caché** par l'aperçu du presse-papiers d'Android 16 et par la feuille de partage.
Sans l'appli Claude, la secrétaire ne sait pas quoi faire.

### Second essai : l'épisode complet en MP3 (35 à 41)

Fichier : l'épisode 20 du podcast « Pattcast » de RTL, en entier (13 min 34, MP3 256 kb/s,
26,1 Mo, trois voix de femmes : deux animatrices et une invitée). Titre « Pattcast RTL episode
20 », trois participants (« Animatrice », « Coanimatrice », « Docteur »), donc 3 voix demandées.

**35-fichier-mp3-pret.png** : tout est rempli, « pattcast_episode20.mp3 · 26,1 Mo ».

**36 à 39** : le suivi, relevé toutes les 10 s (le traitement était trop rapide pour 30 s) :

| Temps | Ce qu'affiche l'écran |
|---|---|
| 0 s | l'écran de préparation (36) |
| 20 s | « ✓ Fichier reçu », « ⏳ Transcription de l'enregistrement, encore environ 1 min… » (37) |
| 31 s, 41 s | idem |
| 52 s | « encore moins d'une minute… » (38) |
| 63 s | « Pas de rédaction automatique sur ce téléphone. » + l'encadré « Rédiger dans Claude » (39, à 75 s) |

L'envoi des 26 Mo a pris moins de 20 s : il tombe entre deux captures, et je n'ai vu
« Envoi » qu'à l'essai hors ligne (28, 29). Pas de file d'attente ce soir-là. Les messages
sont clairs, avec une durée restante en mots simples.

**40-transcription-voix.png** : **les trois voix sont séparées.** 46 tours de parole sur
13 min 34 : « Voix 1 » 13 fois, « Voix 2 » 8 fois, « Voix 3 » 25 fois, et les tours suivent
les rôles (l'invitée est « Voix 3 »). Aucune boucle de répétition trouvée (le relevé du
29 octobre en avait une avec `num_speakers=2` sur l'extrait).
- Les voix s'appellent « Voix 1 », « Voix 2 », « Voix 3 » : l'appli n'essaie pas de les
  relier aux noms donnés. C'est honnête, mais une secrétaire qui a tapé les noms s'attendra
  peut-être à les voir.
- **Un nom de participant ressort dans le texte** : « [1:20] Voix 3 : Docteur. De
  medezinesche Beräich… » et « [3:38] Voix 3 : Docteur & Coquise ». Les noms partent à LuxASR
  comme amorce, et leur documentation prévient que des mots de l'amorce peuvent apparaître
  dans la transcription. Avec de vrais noms (« Anne Wagner »), ce sera moins visible mais
  tout aussi faux.
- Le dernier tour (13:22) mêle l'animatrice et l'invitée : des remerciements très courts,
  ce que LuxASR annonce ne pas savoir séparer.
- Erreurs de reconnaissance visibles : « Ach d'Russ » pour « Arthrose » (l'invitée dit
  plus loin « Arthros », bien reconnu).

**41-accueil-deux-reunions.png** : « Mes réunions » liste les deux réunions, la plus récente
en haut, avec « Transcription prête » en vert. Clair. Une notification « Transcription
prête : Pattcast RTL episode 20 » est arrivée, avec une **icône de micro** dans la barre
d'état : on peut croire que l'appli écoute encore.

## 5. Mode direct : non testable sur cet émulateur

### Ce que j'ai essayé

L'émulateur tournait sans audio. Je l'ai redémarré avec l'entrée audio de l'hôte
(`-allow-host-audio`, puis `adb emu avd hostmicon`, l'équivalent de « Virtual microphone uses
host audio input »). Plutôt que de jouer le son sur les haut-parleurs du PC, j'ai créé une
sortie audio virtuelle dans PulseAudio (WSLg), jouée par `ffmpeg` et branchée sur le micro de
l'émulateur. J'ai vérifié côté PC que le son passait bien dans cette sortie et que
l'émulateur l'écoutait.

**Résultat : l'appli ne reçoit qu'une seconde de son, au tout début, puis des zéros.** J'ai
copié son enregistrement brut (`audio.pcm`) au bout de 178 s : niveau moyen −41 dB, une
seule seconde non nulle (la première), 0,2 % d'échantillons non nuls. Couper puis rallumer
l'entrée de l'hôte pendant la réunion n'y change rien. C'est l'émulateur sous WSL qui ne
transmet pas le son du micro en continu, pas l'appli. Medhi m'a ensuite dit d'abandonner le
micro et de passer par les MP3.

Conséquences :
- **non vérifiés** : la traduction sous chaque paragraphe, le renouvellement de la liaison
  après 2 min, « Revenir au direct », la reprise de la traduction après une coupure, et le
  compte rendu après une réunion en direct. Il faut les refaire sur un vrai téléphone (le
  A21s) ou une tablette ;
- **l'étape 6 (tablette en paysage) n'a pas été faite** : sans son, le côte à côte n'aurait
  rien montré.

Pour ne pas envoyer de silence à LuxASR pour la transcription complète, j'ai terminé les
deux réunions muettes **réseau coupé**, puis je les ai supprimées avant tout envoi. Le flux
direct, lui, a bien envoyé environ 3 min puis 4 min de silence à LuxASR : c'est le coût de
l'essai.

### Ce qu'on a quand même pu voir

**21-preparation-direct.png**, **22-preparation-remplie.png** : « Nouvelle réunion », titre,
participants, ordre du jour, et le rappel orange « Pensez à prévenir les participants que la
réunion est enregistrée et traduite automatiquement. » Bien. Avec le clavier ouvert, le bouton
« Démarrer la réunion » remonte au-dessus du clavier (même risque qu'au mode fichier).

**23-permission-micro.png**, **24-permission-notifications.png** : les deux demandes d'Android,
l'une après l'autre. Rien n'explique avant pourquoi l'appli veut envoyer des notifications.

**25-direct-debut.png** : « J'écoute et je traduis », pastille verte, chrono, jauge de niveau,
A− et A+, et au centre « Je vous écoute. La traduction s'affichera ici dès que quelqu'un
parlera. » Clair. Le gros bouton rouge « Terminer la réunion » est bien visible.

**26-direct-silence-sans-reseau.png** : réseau coupé (`svc wifi disable`, `svc data disable`) :
le bandeau dit bien « **Pas de réseau · j'enregistre quand même** », avec une pastille rouge.
- Le texte passe sur deux lignes et coupe « quand même » au milieu.
- Au centre, « La traduction s'affichera ici dès que quelqu'un parlera » reste affiché, alors
  que sans réseau aucune traduction ne viendra.

**27-terminer-confirmation.png** : « Terminer la réunion ? L'enregistrement s'arrête. Je
prépare ensuite la transcription complète et le compte rendu. » avec « CONTINUER LA RÉUNION »
et « TERMINER ». Clair. Boîte de dialogue au style Android par défaut (angles carrés, boutons
vert-bleu en majuscules), hors de la charte bleu et orange.

**28-suivi-sans-reseau.png** : juste après, hors ligne : « ✓ Réunion enregistrée »,
« Préparation de l'enregistrement… 4 % », trois onglets (Compte rendu, Transcription,
Traduction). Bien.

**29-suivi-sans-reseau-30s.png** : 30 s plus tard, toujours hors ligne : « **Envoi de
l'enregistrement à LuxASR… 0 %** », **sans dire qu'il n'y a pas de réseau**. On croirait
l'appli bloquée. Le journal montre qu'environ une minute plus tard l'envoi échoue sur
« Unable to resolve host "luxasr.uni.lu" ».

**30-supprimer-confirmation.png** : « Supprimer cette réunion ? Le compte rendu, la
transcription et la traduction seront effacés de ce téléphone. » Elle ne dit pas que
**l'enregistrement, pas encore transcrit, est perdu** et que la transcription en cours est
abandonnée.

**31-apres-suppression.png** : retour à l'accueil, la réunion a disparu, le dossier est
effacé. Mais le service de traitement continuait de tourner : quand l'envoi a échoué, il a
voulu écrire dans le dossier supprimé et a levé une `FileNotFoundException`. Il s'est ensuite
arrêté seul, **sans plantage visible**, et rien n'est parti chez LuxASR. Extrait du journal
(`journal-suppression-pendant-traitement.txt`) :

```
E/ServiceTraitement: réunion 2026-10-29-190806
E/ServiceTraitement: com.potomitan.luxreunion.noyau.LotsLuxAsr$Erreur: Unable to resolve host "luxasr.uni.lu": No address associated with hostname
E/ServiceTraitement: traitement
E/ServiceTraitement: java.io.FileNotFoundException: /data/user/0/com.potomitan.luxreunion/files/reunions/2026-10-29-190806/meta.json.tmp: open failed: ENOENT (No such file or directory)
E/ServiceTraitement:   at com.potomitan.luxreunion.noyau.Reunion$Companion.ecrireAtomique(Reunion.kt:155)
E/ServiceTraitement:   at com.potomitan.luxreunion.noyau.Reunion.enregistrer(Reunion.kt:92)
E/ServiceTraitement:   at com.potomitan.luxreunion.ServiceTraitement.traiter(ServiceTraitement.kt:92)
E/ServiceTraitement:   at com.potomitan.luxreunion.ServiceTraitement.boucle(ServiceTraitement.kt:70)
```

Si le réseau était revenu entre la suppression et l'échec, je ne sais pas si l'envoi serait
parti quand même : à vérifier.

**32-direct2-debut.png**, **33-direct-A-plus.png**, **34-direct-A-moins.png** : seconde
réunion. LuxASR n'a reçu qu'une seconde de son et a renvoyé un seul mot : « déi », traduit
« la ».
- **Mise en page : le paragraphe est collé au bord gauche de l'écran et coupé.** On lit
  « 0:00 » amputé de son premier chiffre, « la » et « déi » contre le bord, sans la marge
  des autres cartes. L'arbre de l'écran donne au texte toute la largeur (x de 0 à 1080),
  sans marge intérieure.
- **A+ et A− marchent** : deux A+ grossissent nettement la traduction et le luxembourgeois ;
  quatre A− les ramènent plus petits qu'au départ.
- La jauge de niveau est restée vide tout du long, ce qui est juste puisque l'appli ne
  recevait que des zéros. Elle n'a donc pas pu être vérifiée avec du son.

## 6. Ce que l'essai a coûté aux services

- **LuxASR, API par lots : 2 travaux.** L'extrait de 2 min (`5eacea6f-a051-40cf-889f-6770acecbd81`,
  lancé par erreur sans participants) et l'épisode de 13 min 34
  (`a62f716e-30e9-4c6a-93f4-b78d3f6bea9b`). Un appel à la fois, suivis jusqu'au bout.
- **LuxASR, flux direct** : deux liaisons, environ 3 min puis 4 min, presque uniquement du
  silence. Aucune transcription complète de ce silence n'a été envoyée.
- **Anthropic** : un seul appel, la vérification de la fausse clé, refusée.
- Medhi a proposé une clé Mistral pendant l'essai. Je ne l'ai pas utilisée : l'appli ne parle
  qu'à Anthropic, et la coller dans le champ « Clé d'API Claude » l'aurait envoyée chez
  Anthropic. Elle n'apparaît dans aucun fichier de ce dossier.

## 7. Problèmes, du plus grave au moins grave

1. **Hors ligne, le suivi affiche « Envoi de l'enregistrement à LuxASR… 0 % » sans dire
   qu'il n'y a pas de réseau** (29). La secrétaire croira l'appli bloquée.
2. **« Supprimer cette réunion » pendant le traitement** : la boîte ne dit pas que
   l'enregistrement pas encore transcrit est perdu (30). Le service continue, puis lève une
   `FileNotFoundException` en écrivant dans le dossier effacé (31, journal). Pas de plantage
   ici, mais il reste à vérifier qu'un envoi ne peut pas partir après la suppression.
3. **Le bouton « Transcrire » / « Démarrer la réunion » remonte au-dessus du clavier, à
   l'endroit du champ Participants**, et part sans confirmation (15, 22). Un doigt pressé
   lance la transcription d'un fichier sans participants, ce qui donne une seule voix.
4. **En direct, le paragraphe est collé au bord gauche et coupé** (32, 33, 34) : il
   manque la marge intérieure.
5. **« Rédiger dans Claude » sans l'appli Claude** : le message « Le texte est copié… » est à
   moitié caché par l'aperçu du presse-papiers et la feuille de partage (20). Sans l'appli
   Claude installée, la secrétaire ne sait pas quoi faire.
6. **« Transcription complète (qui a dit quoi) » est cochée même quand une seule voix a été
   trouvée** (16, 17), sans avertissement. Sans participants, LuxASR ne sépare pas les voix.
7. **Les noms des participants peuvent ressortir dans la transcription** (40 : « Docteur. »
   à 1:20 et à 3:38), parce qu'ils servent d'amorce à LuxASR.
8. **« Envoyer la transcription » envoie le texte sans titre ni date** (19).
9. **Le sélecteur de fichier s'ouvre sur « Récents », vide** (12), et ne filtre pas sur
   l'audio et la vidéo (13 montre `champs.html`).
10. **Réglages d'installation difficiles à trouver** : l'appui long ouvre le même écran
    « Réglages », avec la carte « Installation » trois écrans plus bas (05, 06).
11. **« Effacer la clé » laisse les points dans le champ** (09).
12. **Réglages sans bouton « Enregistrer » ni confirmation** (03, 04) ; exemples des champs en
    gris sur gris, qu'on peut prendre pour des valeurs déjà remplies (03, 10).
13. **Sans réseau, « La traduction s'affichera ici dès que quelqu'un parlera » reste
    affiché** (26), et « Pas de réseau · j'enregistre quand même » se coupe sur deux lignes.
14. **Boîtes de confirmation au style Android par défaut** (27, 30), hors de la charte.
15. **La notification « Transcription prête » porte une icône de micro** (41) : on peut croire
    que l'appli écoute encore.
16. **Accueil** : « Choisir un fichier » coupé par le bas au premier lancement (01) ; titre
    décalé vers la gauche, pas de lion dans la barre (01, 02).
17. **« Saisissez une clé. »** s'affiche en petit gris, presque invisible (07).

Non vérifié, faute de son sur l'émulateur : la traduction en direct, le renouvellement de la
liaison après 2 min, « Revenir au direct », la reprise après coupure, le compte rendu d'une
réunion en direct, le côte à côte sur tablette. À refaire sur un vrai appareil.

## 8. Complément : compte rendu par Mistral (même soir, 19 h 30)

Medhi a fourni une clé Mistral de test, valable 4 h, rangée dans `.env` à la racine du dépôt
(fichier ignoré par git, jamais dans l'APK ni dans ce dossier). J'ai installé la version de
`main` qui gère Mistral (`306416d9`) par-dessus la précédente : les deux réunions transcrites
sont restées.

**42-reglages-installation-mistral.png** : la carte s'appelle « Compte rendu automatique »,
avec un choix « Claude (Anthropic) » / « Mistral ». **Le bouton « Claude (Anthropic) » passe
sur deux lignes, est coupé en bas, et n'est pas aligné avec « Mistral ».**

**43-mistral-choisi.png** : Mistral choisi, le texte d'aide et le modèle (`mistral-large-latest`)
suivent. Bien.

**44-cle-mistral-verifiee.png** : premier essai « ✗ La clé d'API Mistral est refusée. ». C'était
**ma saisie** : `adb input text` avait perdu des caractères en tapant 45 caractères d'un coup.
La même clé répond 200 sur `/v1/models` en direct. Le message de l'appli était donc juste.

**45-cle-mistral-verifiee-2.png** : clé retapée par morceaux : « ✓ Clé valide et enregistrée. »

**46-accueil-redaction.png** : à l'enregistrement de la clé, l'appli relance seule la rédaction
des deux réunions restées « sans clé » (les deux en même temps). Elles passent à « À reprendre ».

**47-reunion-a-reprendre.png** : « **Mistral a répondu une erreur (402) : API budget
exhausted.** » avec un bouton « Réessayer ». Les deux appels ont été refusés en moins d'une
seconde, et un appel minuscule fait à la main (5 jetons, `mistral-small-latest`) reçoit la
même réponse (`billing_api_budget_exhausted`) : **la clé s'authentifie, mais son budget est à
zéro**. Aucun compte rendu n'a donc pu être rédigé, et l'appli n'a rien consommé.

Ce qui cloche sur cet écran :
- le message est **brut et en anglais**, avec un code HTTP : une secrétaire ne comprend pas
  « API budget exhausted ». Il faudrait « Le crédit du compte Mistral est épuisé : prévenez la
  personne qui a installé l'application. » ;
- l'écran se contredit : « ⏳ Compte rendu · En attente… » et « Rédaction en cours… » restent
  affichés sous l'erreur ;
- « Réessayer » relancera la même erreur tant que le crédit n'est pas rechargé, sans le dire ;
- à l'accueil, « À reprendre » ne dit pas pourquoi.

Problèmes à ajouter à la liste du § 7 :

18. **Erreur Mistral 402 affichée brute, en anglais** (47), sous un « En attente… » et un
    « Rédaction en cours… » contradictoires. À placer juste après le n° 2 par gravité.
19. **Bouton « Claude (Anthropic) » coupé et mal aligné** dans les réglages d'installation (42).

## 9. Complément : le compte rendu, rédigé à la main (même soir, 19 h 45)

La clé OpenAI fournie ensuite a le même problème que la clé Mistral : elle s'authentifie, mais
le compte n'a plus de crédit (`insufficient_quota`, `credit_balance_exhausted`). L'appli ne
gère de toute façon pas OpenAI ; rien n'a été codé. Clé rangée dans `.env`, hors git.

Contournement décidé par Medhi : j'ai rédigé moi-même le compte rendu de l'épisode
(`compte-rendu-pattcast.txt`), en suivant à la lettre les consignes que l'appli envoie au
service (`ConsignesCompteRendu.systeme` et `.message` : plan, texte brut, « Voix N » tant
qu'un nom n'est pas évident, rien d'inventé, section « À VÉRIFIER » avec horodatages), à partir
des mêmes informations (titre, date, rédacteur « Essai Medhi », trois participants annoncés,
voix séparées). Puis je l'ai placé dans la réunion comme l'appli le fait après une rédaction
réussie (`compte-rendu.txt`, étape `PRETE`, erreur effacée), application arrêtée.

**48-accueil-cr-pret.png** : la réunion passe à « Compte rendu prêt ».

**49-compte-rendu.png** : l'onglet « Compte rendu » montre le texte, précédé de « Relisez et
corrigez si besoin : vos modifications sont gardées. » Les titres en majuscules et les tirets
rendent bien en texte brut.

**50-correction-en-cours.png** : le texte se corrige directement. Ma correction est tombée au
mauvais endroit (le texte avait défilé entre deux touchers), mais **elle est bien gardée** :
après être sorti puis revenu, elle est dans `compte-rendu.txt`.

**51-compte-rendu-bas.png** : en bas, « ✉️ Envoyer le compte rendu », « Copier », « Rédiger à
nouveau » et « Supprimer cette réunion ». « Rédiger à nouveau » n'a pas été touché : il aurait
rappelé Mistral, sans crédit.

**52-envoyer-compte-rendu.png** : le partage d'Android reçoit le compte rendu corrigé, qui
commence par « COMPTE RENDU — Pattcast RTL episode 20 », la date et le rédacteur. Le code
passe aussi un objet « Compte rendu — Pattcast RTL episode 20 ». (Pas d'appli de messagerie sur
l'émulateur pour aller jusqu'à l'e-mail.)

Ce que la rédaction a fait ressortir :
- **En mode fichier, la date du compte rendu est celle de l'import**, pas celle de la réunion :
  « jeudi 29 octobre 2026, 19 h 21 » pour un podcast publié le 16 décembre 2024. Le champ
  `dureeS` vaut aussi 0 pour un fichier : la durée n'est pas transmise, alors qu'elle est
  connue (13 min 34). Il faudrait demander la date de la réunion dans l'écran « Un
  enregistrement », et passer la durée.
- **Les consignes imposent le tiret cadratin** (« COMPTE RENDU — titre », « qui — quoi — pour
  quand »), que le projet évite ailleurs dans ses textes publics.
- **La séparation des voix se trompe par endroits** : des questions d'animatrice se retrouvent
  dans les répliques de l'invitée ([2:20], [4:28]), et l'inverse ([7:58]). La section « À
  VÉRIFIER » est donc indispensable, et c'est elle qui rend le compte rendu utilisable.

Problèmes à ajouter à la liste du § 7 :

20. **En mode fichier, date de l'import au lieu de la date de la réunion, et durée absente**
    (48, 49).
21. **Le plan imposé par les consignes contient des tirets cadratins** (49).
