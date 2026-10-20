# Annonces Reddit

Fait le 2026-10-18 sous la 37.4.7. `python3 reddit.py` refait tout (Chrome,
ImageMagick, ffmpeg) à partir de `sources/` et du Reel de `../video/`.

## Formats

Formats Reddit Ads relevés le 2026-10-18 : image en 1:1, 4:5 ou 16:9, 3 Mo
au plus ; carrousel de 2 à 6 cartes carrées ; vidéo en 1:1, 4:5 ou 16:9
(le 9:16 n'existe que pour les achats gérés par Reddit), de 5 à 30 s
conseillées.

| Fichier | Format | Usage |
|---|---|---|
| `reddit_image_4x5_<l>.jpg` | 1080 × 1350 | image, à préférer sur téléphone |
| `reddit_image_1x1_<l>.jpg` | 1080 × 1080 | image |
| `reddit_image_16x9_<l>.jpg` | 1920 × 1080 | image, ordinateur |
| `reddit_carrousel_1…6_<l>.jpg` | 1200 × 1200 | carrousel, dans l'ordre |
| `reddit_video_annonce_<l>.mp4` | 1080 × 1350, 23,5 s | **la vidéo faite pour Reddit**, à préférer |
| `reddit_video_4x5_fr.mp4` | 1080 × 1350, 25 s | le Reel recadré |
| `reddit_video_1x1_fr.mp4` | 1080 × 1080, 25 s | vidéo |
| `reddit_video_16x9_fr.mp4` | 1920 × 1080, 25 s | vidéo, ordinateur |
| `reddit_video_vignette_fr.jpg` | 1200 × 628 | vignette des vidéos |

`<l>` vaut `fr` ou `en`. Les vidéos tirées du Reel n'existent qu'en
français, parce que le Reel est en français.

Depuis le 2026-10-20, la dictée est montrée partout par le motif retenu pour
toutes les créations : le grand micro rouge qui pulse et la phrase « Op der
Kiermes iessen ech ëmmer Gromperekichelcher mat Äppelkompott » qui s'écrit
mot à mot dans sa bulle (`reel.ÉcranDictée`), dans le Reel comme dans
`reddit_video_annonce_<l>.mp4`. Le pied des images porte le tampon « 100 %
gratuit » des supports (`docs/assets/tampon-gratuit.css`).

Ordre des promesses, fixé par le propriétaire le 2026-10-18 : l'écriture,
puis la dictée, puis la collection de cartes, et enfin les jeux. Le carrousel
le suit : 1 mots devinés, 2 accents, 3 dictée, 4 carte du carnet,
5 Wuertplaz (les jeux), 6 appel à installer avec le badge Google Play. Sur
les images seules, le clavier qui devine passe devant, la dictée est posée
dessus.

`video_reddit.py` monte la vidéo propre à Reddit dans cet ordre, avec la
phrase de dictée officielle, en français et en anglais. Le Reel, lui, ne suit
pas encore cet ordre : il montre les jeux avant le
carnet, et le Wierderbuch entre les deux premiers et les jeux.

## Textes

Lien : https://play.google.com/store/apps/details?id=com.potomitan.luxkeyboard
Bouton : « Installer » (en anglais : « Install »).

Le titre peut faire jusqu'à 300 caractères, mais un téléphone le coupe vers
80 : chaque titre proposé ci-dessous tient dans cette longueur.

Français :

- Écrivez le luxembourgeois comme vous le parlez. Gratuit sur Google Play.
- Tapez, il propose le mot. Parlez, il écrit en luxembourgeois.
- Chaque mot gagné devient une carte à collectionner. Clavier gratuit.

Anglais :

- Write Luxembourgish the way you speak it. Free on Google Play.
- Type, it suggests the word. Speak, it writes in Luxembourgish.
- Every word you win becomes a collectible card. Free keyboard.

Titres refaits le 2026-10-20 : plus de « sans publicité », qui ne répond à
aucune douleur, ni de promesse en négatif (« soulignés en rouge ? Plus avec
ce clavier »).

## Si on pose la question en commentaire

La dictée envoie la voix à LuxASR, le service de l'Université du Luxembourg,
et il faut une connexion. Ce qu'on tape au clavier reste sur le téléphone.
C'est ce que dit la fiche Google Play, et rien de plus.
