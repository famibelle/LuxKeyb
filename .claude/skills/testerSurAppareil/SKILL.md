---
name: testerSurAppareil
description: Installer, piloter, vérifier et capturer le Lëtzebuergesch Clavier (LuxKeyb) sur le Galaxy A21s de Medhi ou sur l'émulateur Android (AVD pixel9), c'est-à-dire les règles d'accord, la remise du clavier par défaut, le contrôle du premier plan, les langues, le carnet de test, uiautomator, les captures, le filmage et les GIF filigranés. À utiliser dès qu'il faut « tester sur le téléphone », « essayer sur le A21s », lancer l'émulateur, faire une capture ou un GIF pour le site, ou vérifier une fonction à l'écran avant de la publier.
---

# Tester sur un appareil

## 1. Quel appareil

**Le A21s d'abord, quand il est libre** : l'hôte n'a pas la mémoire pour deux émulateurs, et une autre session peut déjà occuper l'émulateur. L'émulateur reste utile quand le téléphone est pris, pour les langues du système, ou pour une capture en 1080 px de large.

`adb devices` liste les deux. **Toujours épingler la cible** : `ANDROID_SERIAL=emulator-5554` pour l'émulateur, `-s <adresse du téléphone>:<port>` pour le téléphone (le port change à chaque reconnexion et quand le téléphone se met en veille : le relire dans `adb devices`, ne jamais le coder en dur).

Les deux noms de l'application, nécessaires dans presque toutes les commandes :
```
paquet : com.potomitan.luxkeyboard
clavier : com.potomitan.luxkeyboard/com.example.kreyolkeyboard.KreyolInputMethodServiceRefactored
activité : com.potomitan.luxkeyboard/com.example.kreyolkeyboard.SettingsActivity
```

## 2. Le A21s : ce qui est permis

Medhi a délégué **toute l'application `com.potomitan.luxkeyboard`** sur ce téléphone : installer, désinstaller, effacer ses données, la remplacer par un build debug, sans redemander. C'est un téléphone de test.

- **Ne rien remettre à la fin** : laisser le build installé tel quel, ne pas réinstaller une release « propre », ne pas proposer de le faire (consigne donnée trois fois).
- **Seule exception : remettre notre clavier par défaut** s'il a été désélectionné (après un `force-stop`, un `pm clear` ou une installation) :
  `adb shell ime set com.potomitan.luxkeyboard/com.example.kreyolkeyboard.KreyolInputMethodServiceRefactored`
- **Le reste du téléphone n'est pas délégué** : autres applis, réglages système, Play Store.
- **Ne jamais toucher au wifi** : le débogage sans fil s'éteint avec, et la session est perdue.
- **Contrôler le premier plan avant toute frappe injectée** (tap, swipe, text, keyevent) :
  `adb shell dumpsys window | grep mCurrentFocus` doit nommer `com.potomitan.luxkeyboard`. Sinon, s'arrêter. Jamais de `keyevent BACK` sans ce contrôle : le 2026-10-11, des taps à l'aveugle sont tombés dans « Débogage sans fil » et « Options de développement ». Derrière Messages se trouve aussi cet écran.
- Une autre session (l'appli créole) peut utiliser le téléphone en même temps.

Particularités de ce Samsung :
- Écran 720 × 1600. Retour depuis l'accueil de l'appli la met en arrière-plan sans la fermer : pour un `onCreate` neuf, il faut un `force-stop` (puis `ime set`).
- Une release (clé de production) et un build debug ont des signatures différentes : passer de l'un à l'autre impose de désinstaller. Une release n'est pas `debuggable`, donc `run-as` y est impossible.
- `media` n'existe pas : régler le volume par `cmd media_session volume --stream 3 --set 15`.
- Le journal est inondé par SurfaceFlinger : `adb logcat -d --pid=$(adb shell pidof com.potomitan.luxkeyboard)`.
- Appairage sans fil : le port de la fenêtre « code d'association » sert seulement à `adb pair` ; le port de connexion est celui de l'écran principal.
- Le téléphone est en Muet et le son de frappe de notre clavier est coupé, à la demande de Medhi.

## 3. L'émulateur : quartier libre

Installer, effacer, modifier les données, piloter : sans demander ni restaurer.

- **`./gradlew --stop` avant de lancer l'émulateur** : avec les démons Gradle résidents, l'hôte (12 Go) manque de mémoire et l'émulateur gèle ou meurt (souvent sur la page Séries du carnet).
- **Fenêtre visible**, jamais `-no-window` : Medhi veut voir ce qui se passe.
  `~/Android/Sdk/emulator/emulator -avd pixel9 -gpu swiftshader_indirect -no-audio &`
- AVD de travail `pixel9` : 1080 × 2424, densité 420. Deux autres existent sur l'hôte : `a12` (Android 12, 2026-10-05) et `tablette10` (2560 × 1600, 2026-10-07) ; n'en lancer qu'un à la fois.
- **L'émulateur déclare toujours un clavier physique** : sans `adb shell settings put secure show_ime_with_hard_keyboard 1`, notre clavier n'affiche que sa barre de suggestions.
- Au démarrage à froid, « Interface ne répond pas » : toucher « Attendre ». **Une ANR juste après l'installation** vient de la préparation des cartes, une fois par installation : attendre avant de faire défiler.
- **Disque interne plein** (`INSTALL_FAILED_INSUFFICIENT_STORAGE`, `adb shell df -h /data`) : la partition fait 6 Go et le Play Store y empile ses mises à jour, même sans compte. Nettoyé le 2026-10-31 de 130 Mo à 1 Go libres :
  - effacer les restes de nos sessions : `/sdcard/*.mp4`, `/sdcard/*.xml`, `/sdcard/Download/`, `/data/local/tmp/*.xml|json|txt` (garder une copie sur l'hôte de ce qui sert encore) ;
  - pour les applis Google inutiles aux tests (Photos, Gmail, Maps, YouTube Music, Drive, Voice Access, Agenda, Bien-être numérique) : `pm uninstall-system-updates <paquet>` puis `pm clear <paquet>`, elles reviennent à leur version d'usine ;
  - finir par `pm art cleanup`. Ne pas toucher aux Services Google, à WebView, au Play Store, à Chrome ni aux applis des autres sessions (`com.potomitan.*`).
  - Désinstaller notre appli ne libère presque rien : ce n'est pas une parade (sauvegarder avant `shared_prefs` et `files` par `run-as … tar`, si on le fait quand même).
- Il est partagé avec d'autres sessions : vérifier `dumpsys package com.potomitan.luxkeyboard | grep lastUpdateTime` avant de conclure sur ce qu'on voit, et ne laisser aucun réglage global (proxy, mode avion).
- Ne pas utiliser la touche Entrée pour avancer dans une bulle : elle active la vue focalisée dessous.

## 4. Les pièges communs

- **Le clavier par défaut retombe sur Gboard** après `adb install -r`, `am force-stop`, `pm clear` ou un changement de langue système. Refaire `ime set`. Sans notre clavier, l'accueil n'est pas en mode « Aujourd'hui » et la visite guidée ne se lance pas.
- Build debug : depuis `android_keyboard/`, `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleDebug`, puis `adb install -r app/build/outputs/apk/debug/*.apk`. Vérifier que le fichier existe avant de désinstaller quoi que ce soit.
- Relancer proprement l'appli : `adb shell am start -S -W -n com.potomitan.luxkeyboard/com.example.kreyolkeyboard.SettingsActivity` (le `-S` force l'arrêt : refaire `ime set` si l'on a besoin du clavier).
- **Lire l'écran sans capture** : `adb shell uiautomator dump /dev/tty`. Pour voir le clavier, `uiautomator dump --windows` (sans lui, l'IME n'y est pas). Le dump liste aussi les boutons cachés sous une pochette. Pendant qu'une carte s'anime, il ne rend rien : chercher le bouton à sa couleur dans une capture (`bleu.py` de la skill guideUtilisateur pour le bleu #0080FF).
- Les boutons d'`AlertDialog` sont en majuscules dans le dump (« FERMER »), les emoji en entités (`&#128228;`).
- Dans une boucle shell, `adb` mange l'entrée standard : `< /dev/null`.
- `input tap` met environ 0,5 s à partir : chronométrer côté appli (journal), pas à l'image.
- Couper le correcteur système le temps d'une capture de clavier (`settings put secure spell_checker_enabled 0`, remettre à 1 après), sinon des mots luxembourgeois sont soulignés.

### Préparer des données

- **Préférences d'un build debug** : `adb shell run-as com.potomitan.luxkeyboard cat shared_prefs/<fichier>.xml`, application arrêtée pour écrire.
- **Ajouter une carte au carnet** : une entrée dans le JSON de `shared_prefs/carnet_cartes.xml`, par exemple `{"m":"Waasser","d":<ms>,"n":3,"g":"ws,kw","k":<numéro>,"b":2}`.
- **Rejouer une première fois** : `pm clear` (puis `ime set`), ou retirer les clés voulues de `lux_onboarding_prefs.xml`.
- **Langue de l'appli** : `adb shell cmd locale set-app-locales com.potomitan.luxkeyboard --locales de` (`""` pour revenir). Le sélecteur de claviers suit la langue du système, pas celle de l'appli. Pour la langue du système (émulateur seulement), passer par Paramètres › Languages avec `uiautomator`.
- **Inclinaison d'une carte** (émulateur) : `adb emu sensor set acceleration X:9.81:0` ; rouvrir la carte à plat avant chaque inclinaison. **Lancer alors l'émulateur avec `-gpu swangle_indirect`** : avec `swiftshader_indirect`, son moteur de rendu plante (signal 11 dans RenderThread, vu deux fois le 2026-10-29) dès qu'une carte ouverte penche, et chaque commande met 45 s. Avec swangle, une capture prend 4 s. Les GIF d'inclinaison se fabriquent à partir de captures prises à plusieurs angles.

## 5. Capturer, filmer, faire un GIF

- **Partager les captures avec Medhi au fil de l'eau** (`SendUserFile`), il l'a demandé.
- Capture : `adb exec-out screencap -p > x.png`.
- **L'émulateur ne se filme pas** (environ 3 images/s, lecture logicielle) : les GIF y sont **synthétisés** à partir de captures d'états, avec un disque bleu qui montre les touchers (`compose.py`, sauvegardé hors du dépôt).
- **Le A21s se filme** : `adb exec-out screenrecord --output-format=h264 --size 720x1600 -`. Le flux n'émet une image que si l'écran change : dater chaque image à son arrivée.
- **Toute carte visible sur une image publiée porte le filigrane « LuxKeyb »** : `python docs/scripts/filigrane_cartes.py <png>`, **une seule fois** par capture (une seconde passe le double). Sous le voile sombre de la visite, la détection automatique échoue : passer des boîtes à la main (`gif_aide.py` de la skill guideUtilisateur).
- GIF : palette ffmpeg (`palettegen` / `paletteuse=dither=none:diff_mode=rectangle`), pas de gifsicle sur la machine.
- Phrases de démonstration : frappe « Ech hunn op der Schueberfouer Gromperekichelcher giess », dictée « Op der Kiermes iessen ech ëmmer Gromperekichelcher mat Äppelkompott. ». Pas de sujet de maladie ni de covid dans un extrait audio.
- Pas de créole visible : un fil SMS de démo peut en contenir, prendre un numéro neuf et cadrer serré.

## Liste de contrôle

- [ ] Appareil choisi et épinglé (`ANDROID_SERIAL` ou `-s`).
- [ ] Sur le A21s : premier plan vérifié avant chaque série d'entrées ; wifi intouché.
- [ ] Notre clavier par défaut après chaque installation ou arrêt forcé.
- [ ] Émulateur : `gradlew --stop`, fenêtre visible, `show_ime_with_hard_keyboard 1`.
- [ ] Captures partagées avec Medhi ; cartes filigranées une seule fois.
- [ ] Sur le A21s, rien à remettre en état à la fin.
