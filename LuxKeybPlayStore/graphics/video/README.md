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

## Reel vertical (Instagram)

`Letzebuergesch_Clavier_reel_fr.mp4` : 25 s, 1080 × 1920 (9:16), 30 i/s, en
français, avec une piste audio muette (la musique s'ajoute dans Instagram).
Accroche de 0,1 s, entière dès la première image : elle sert de vignette,
c'est là qu'on la voit, pas dans la lecture. On y trouve
le message et, dessous, le clavier qui propose « Schueberfouer ». Puis suggestions,
dictée, Wierderbuch, jeux, carnet
(20 s à eux cinq), puis
l'appel à télécharger : badge Google Play, puis une barre de recherche qui
tape « Lëtzebuergesch Clavier ». Pas de « lien dans la bio » : la vidéo est
publiée depuis un compte personnel, elle doit se suffire à elle-même. Le badge
(`badge_google_play_fr.png`) est `docs/assets/google-play-badge-fr.svg` rendu
par Chromium, le texte en Inter faute de Google Sans.

Depuis le 2026-10-20, la scène de dictée du Reel n'est plus reprise de la
vidéo de présentation : c'est le motif retenu pour toutes les créations,
dessiné par `ÉcranDictée` dans `reel.py` (grand micro rouge à halos qui pulse
légèrement, phrase du LOD qui s'écrit mot à mot dans sa bulle, 3,6 s).

`python3 reel.py` le refait : il découpe l'écran du téléphone dans la vidéo
de présentation ci-dessus pour les séquences animées, et prend les captures de
`captures-emulateur-pixel9` pour les écrans entiers. Le carnet montre les
cartes au design de la 37, avec leurs décors de paysage (Sonn, Brëll, la
grille de Mäi Carnet, la série des paysages). Les autres captures de cartes
de `docs/Screenshots` ont l'ancien fond uni ou le filigrane « LuxKeyb », tirées
de `docs/Screenshots`, comme sur la page des nouveautés. Rien n'est placé sous
y = 1640, où Instagram pose la légende et les boutons.
