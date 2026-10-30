# Lëtzebuergesch Réunion

Une application Android pour une secrétaire de direction qui assiste à des
réunions en luxembourgeois (souvent mêlé de français) :

1. **pendant la réunion**, ce qui se dit s'écrit en direct, en grand, en
   luxembourgeois. La traduction en français est dessous, en plus petit, ou
   côte à côte sur tablette ;
2. **à la fin**, un seul bouton. L'application envoie l'enregistrement
   complet à LuxASR pour une transcription où les voix sont séparées (« qui a dit
   quoi »), puis fait rédiger le compte rendu par Claude ;
3. elle **relit, corrige, envoie** le compte rendu par e-mail.

Elle transcrit aussi un **fichier** audio ou vidéo déjà enregistré.

Elle reprend la charte de Lëtzebuergesch Clavier (barre bleue `#0080FF`, cartes
blanches sur `#F5F5F5`, accent orange, le lion), avec une pastille micro sur
l'icône pour distinguer les deux applications.

## Avant de s'en servir pour de vrai

- **LuxASR** ouvre son API « for limited access » et demande qu'on le
  contacte avant de l'intégrer dans une application
  (`.claude/skills/transcription/SKILL.md`, § 1). L'accord actuel couvre un
  usage de laboratoire, à charge modérée. Un usage régulier en réunion doit
  être validé par eux. Chaque réunion leur coûte deux passages : le direct,
  puis la transcription complète. Le second peut se désactiver dans les
  réglages d'installation.
- **Confidentialité.** Le son part à l'Université du Luxembourg, le texte chez
  Anthropic. Il faut l'accord de l'employeur (son délégué à la protection des
  données), et les participants doivent être prévenus de l'enregistrement.
  L'application le rappelle au premier lancement et avant chaque réunion.

## Installation sur le téléphone

1. Télécharger l'APK depuis le workflow « 🎙️ Lëtzebuergesch Réunion — APK »
   (onglet Actions du dépôt, artefact `letzebuergesch-reunion-release-apk`).
   Il est signé avec la clé du clavier : chaque version s'installe par-dessus
   la précédente sans perdre les réunions.
2. L'installer (autoriser l'installation depuis le navigateur ou le
   gestionnaire de fichiers).
3. **Activer le compte rendu automatique.** Sur l'accueil, faire un appui
   long sur le petit texte gris en bas de page. Les réglages d'installation
   s'ouvrent : y coller une clé d'API Claude (console.anthropic.com), puis
   « Enregistrer et vérifier ». La clé est chiffrée par le Keystore du
   téléphone ; elle n'est jamais dans l'APK. Fixer un plafond de dépenses dans
   la console : un compte rendu coûte environ 0,15 à 0,25 $ par heure de
   réunion avec `claude-opus-5-5`.

   Sans clé, tout fonctionne sauf la rédaction automatique. Un bouton
   « Rédiger dans Claude » envoie alors la transcription et les consignes
   dans l'application Claude.
4. Dans « Réglages », la secrétaire peut ajouter son nom, ses **mots à bien
   écrire** (noms propres, sigles) et un **modèle de compte rendu** (un ancien
   compte rendu suffit).

Pour la qualité, le plus important est le micro. Il faut une tablette ou un
téléphone posé au centre de la table, idéalement avec un micro de conférence
USB-C, et non un téléphone dans un sac.

## Comment c'est fait

```
noyau/   Kotlin pur, sans Android, testé sur la JVM :
         Transcription.kt  normaliseur des réponses /asr2 (port de luxasr-transcription.js)
         Direct.kt         le direct : liaisons, traduction, reprises (port de luxasr-direct.js)
         Lots.kt           l'API par lots : envoi brut, suivi de la file, résultat lu une fois
         CompteRendu.kt    consignes du compte rendu, appel à Claude (SDK Java officiel)
         Reunion.kt        une réunion = un dossier dans le stockage privé
app/     Android, sans AndroidX : écrans construits en code (Charte.kt),
         ServiceEcoute (micro → enregistrement + direct, service au premier plan),
         ServiceTraitement (AAC → LuxASR → Claude, repris après redémarrage)
```

Choix qui ne se devinent pas :

- **Le direct ne s'arrête jamais de lui-même.** Une coupure de réseau le met
  hors ligne, il retente seul, et l'enregistrement continue : la
  transcription complète se fait à partir de l'enregistrement, pas du
  direct. Un passage manqué est signalé à l'écran.
- **L'enregistrement est écrit brut (PCM) pendant la réunion**, et compressé
  en AAC seulement à la fin. Un fichier m4a n'est lisible qu'une fois
  finalisé, alors que le PCM survit à une application tuée. Au-delà de
  90 minutes, il part en plusieurs travaux LuxASR.
- **L'audio est effacé du téléphone** dès que la transcription complète est
  obtenue. La sauvegarde Android est désactivée.
- **Pas de CORS ici** : c'est ce qui rend possible l'envoi d'un fichier, encore
  bloqué sur la page Labs.

## Construire et tester

```bash
cd android_reunion
./gradlew -p noyau test                         # le noyau, sur la JVM
./gradlew -p noyau compileVerifAppKotlin -PverifierApp   # typage du module Android, sans SDK
./gradlew assembleDebug                         # l'APK (SDK Android requis)
./gradlew connectedDebugAndroidTest             # sur un appareil ou un émulateur
```

La CI (`.github/workflows/reunion-apk.yml`) fait les trois. Les tests sur
émulateur vérifient ce que la JVM ne garantit pas : le SDK Claude (Jackson,
kotlin-reflect), OkHttp et l'encodeur AAC sur un vrai Android.

L'icône se refabrique avec `python3 android_reunion/scripts/icone.py`.
