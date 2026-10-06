# Vidéo de présentation

`Letzebuergesch_Clavier_presentation_fr.mp4` : 50 s, 1920 × 1080, 30 i/s, sans
son, en français. Elle sert au champ « Vidéo YouTube » de la fiche Play Store,
une fois mise en ligne sur YouTube (non répertoriée ou publique, pas conçue
pour les enfants, sans restriction d'âge, monétisation coupée, intégration
autorisée).

Faite le 2026-10-05 sous la 33.1.0, sur l'émulateur `pixel9`. Il ne filme pas
en temps réel (environ 3 images par seconde au mieux) : la vidéo est donc
montée image par image à partir de captures pilotées par `adb`, avec un disque
bleu à chaque appui. La dictée est reconstituée à partir de la vraie capture
de dictée (`captures-emulateur-pixel9/20-clavier-dictee-luxasr.png`), les mots
révélés un par un : l'émulateur ne peut pas entendre de voix.

Pour la refaire :

1. `drive.py` pilote la frappe : `Rec("frappe").type(...)`, `chip(...)` pour
   toucher une suggestion. Chaque séquence (`frappe`, `accents`, `wb`, `jeux`,
   `carnet`) est un dossier de captures plus un `log.json` des appuis. Les
   captures ne sont pas versionnées (une centaine de PNG pleine taille).
2. `python3 dictee.py` fabrique la séquence `dictee`.
3. `scenes.json` fixe les titres, les recadrages et la durée de chaque image.
4. `python3 panneaux.py` rend les fonds (Chrome), puis `python3 compose.py`
   assemble le MP4 (PIL et ffmpeg).

Avant de capturer : langue de l'appli fixée (`cmd locale set-app-locales`),
Gboard et le clavier créole désactivés le temps des captures (sinon ils
reprennent la main), correcteur système coupé, carnet sauvegardé puis restauré.
