# Banc de frappe sur émulateur — Lëtzebuergesch Clavier contre Gboard

*Mesure du 8 septembre 2026, en cours de consolidation. Ce fichier est à la
racine du dépôt, comme `ACCESSIBILITE.md` et `SWIPE-ESPACE.md`, pour ne pas
déclencher la construction de l'APK à chaque correction.*

## Ce qui est mesuré

**La prédiction du mot suivant, sans qu'une seule lettre du mot cherché ait été
tapée.** À chaque frontière de mot d'une phrase, on regarde si le mot réellement
écrit dans le corpus figure parmi les trois premières suggestions affichées.

Ce n'est **pas** la même chose que les 18,8 % publiés sur ParaLux ni que les
18,1 % publiés sur ZLS : ces chiffres-là comptent aussi les positions où
l'utilisateur a déjà entré des lettres, ce qui est beaucoup plus facile. Le
présent banc mesure le cas le plus dur, et ses valeurs absolues ne sont
comparables qu'entre les deux claviers testés.

- **Corpus** : *Méisproochegen Iwwersetzungskorpus* (ZLS, CC0), 20 phrases tirées
  au sort (graine 20260908) parmi celles de 8 à 15 mots ne contenant que des
  lettres, l'apostrophe droite et les trois voyelles `é ä ë`. 175 positions.
- **Appareil** : AVD `kreyol_test`, Android 14, rendu logiciel.
- **Claviers** : Lëtzebuergesch Clavier 20.3.0 ; Gboard 12.4.05 configuré en
  saisie multilingue **LB + FR**, exactement comme nos deux rangées.
- **Lecture** : capture d'écran de la barre de suggestions, stabilisée (deux
  photos identiques à 0,6 s d'intervalle), puis OCR. Casse et diacritiques
  repliées des deux côtés, l'OCR perdant régulièrement le tréma.

## Résultats

| | barre vide | top-1 | top-3 |
|---|---|---|---|
| **Lëtzebuergesch Clavier 20.3.0** | 30,3 % | 6,9 % | **12,0 %** |
| **Gboard 12.4 (LB + FR)** | *passe en cours* | | |
| *référence hors ligne : nos n-grammes livrés* | *10,9 %* | | *16,0 %* |

Première passe, protocole antérieur (contexte posé dans le champ pour les deux
claviers, 181 positions, jeu de phrases légèrement différent) : LuxKeyb 7,7 % de
top-3 et 28,2 % de barres vides, Gboard **8,3 %** de top-3 et **0,0 %** de barres
vides. Ces chiffres sont conservés ici pour mémoire : ils sous-estiment notre
clavier, pour la raison expliquée plus bas.

## Trois choses apprises en construisant le banc

- **Notre clavier ne reconstruit pas son contexte sur un champ déjà rempli.**
  Ouvrez un champ contenant un brouillon, tapez espace : la barre reste vide,
  alors que Gboard propose immédiatement la suite. Vérifié sur le contexte
  « an der », qui donne *Stad · Police · Rue* quand le texte a été frappé dans la
  session en cours, et rien du tout quand la même phrase a été posée dans le
  champ avant l'ouverture du clavier. **C'est un défaut de l'application, pas du
  banc**, et il mérite un correctif indépendamment de ce comparatif.
- **Gboard, lui, relit le champ**, et le contrôle le prouve : même contexte posé
  dans le champ ou frappé à la main, il propose le même triplet
  (*eng · op · vun*). Les deux méthodes d'injection sont donc équivalentes pour
  lui, ce qui autorise à donner à chaque clavier le mode de saisie qu'il sait
  lire.
- **L'écart entre notre écran et notre propre modèle reste à expliquer** : 30,3 %
  de barres vides à l'écran contre 10,9 % de contextes inconnus dans le fichier
  n-grammes livré, et 12,0 % de top-3 contre 16,0 % pour ce même fichier. Une
  partie vient de ce que le banc écrit les mots dans le champ sans passer par les
  touches : le clavier ne voit alors pas les lettres et son historique de mots
  reste incomplet. Une passe où chaque lettre est réellement frappée sur les
  touches est nécessaire pour trancher.

## Reproduire

Les scripts et les relevés sont dans `banc-emulateur/` : `bench2.py` conduit la
frappe, `score.py` lit les captures, `compare.py` apparie les deux claviers. Ils
dépendent des coordonnées de l'écran de l'AVD `kreyol_test` et sont à réétalonner
sur un autre appareil.

Points à connaître si l'on refait la manipulation :

- `adb shell input text` **ne passe pas les caractères non ASCII** : `ë` fait
  échouer la commande. Les trois lettres accentuées se tapent donc sur leurs
  touches, et le reste du mot par `input text`.
- `adb shell sh -c "…"` **casse le passage d'arguments** : adb recolle les
  arguments avec des espaces, et `input` reçoit un seul mot. Il faut envoyer la
  séquence entière comme une chaîne unique.
- La fenêtre du clavier **n'apparaît pas dans `uiautomator dump`** : la barre de
  suggestions se lit par capture d'écran et OCR, pas par l'arbre d'accessibilité.
- Un aller-retour adb coûte près d'une seconde sur cette machine : chaque
  position tient en un seul appel, les attentes se faisant sur l'appareil.
