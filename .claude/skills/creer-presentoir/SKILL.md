---
name: creer-presentoir
description: Créer ou modifier le présentoir de comptoir en carton du Wuertduell (et les étuis qu'il présente) — dimensions, gabarit à plat, visuels du fronton et de la lèvre, maquette en volume, fichiers pour le fabricant de PLV, contrôles. À utiliser quand on parle de présentoir, de PLV, de stand, de commerce, de librairie, de « display », d'un fronton, ou quand une boîte, une couleur ou une carte change et que le présentoir doit suivre.
---

# Créer le présentoir de comptoir

Le présentoir montre les boîtes du Wuertduell sur un stand (Fierkelsmaart) ou
dans un commerce. Il est fait en même temps que les étuis : il les reprend,
face par face, dans sa maquette. Format, matières et budget général des cartes
imprimées : skill **imprimeur**. Ton et typographie des textes : skill
**textesPublics**.

## 1. Ce qu'il est (état du 2026-11-01)

```
fronton 274 × 190 mm, glissé par deux pattes de 44 × 30 mm dans le dos
┌──────────────────────────────────────┐
│ LËTZEBUERGESCH CLAVIER · WUERTDUELL   │  titre, accroche, 4 vraies cartes,
│ 2 joueurs · dès 8 ans · 15 min   QR   │  la règle d'or, les 4 boîtes,
│                          (prix vide)  │  la pastille du prix
└──────────────────────────────────────┘
┌────────┬────────┬────────┬────────┐   dos 100 mm, double paroi
│ B1     │ E1     │ NA1    │ NA2    │   3 étuis par colonne, l'un derrière l'autre
└────────┴────────┴────────┴────────┘   lèvre 40 mm : le nom de chaque boîte
bac 274 × 60 mm · hauteur montée 290 mm · carton ondulé cannelure E (~2 mm)
```

- **12 étuis**, de face, en **4 colonnes**, une par boîte (ordre `ORDRE` :
  boîte de départ, extension, Niveau A1, Niveau A2). Une colonne fait 66 mm
  (l'étui 64,5 mm et du jeu), les cloisons 2 mm.
- **La lèvre de 40 mm** cache le bas des étuis et laisse voir le titre et le
  haut des cartes de chaque face : c'est ce qui vend. Elle porte le nom de
  chaque boîte dans sa couleur, et ses deux paquets avec leur emblème.
- **Le fronton** : le même habit que le dos des cartes (nuit bleue, rosace
  guillochée, or, filet à perles), WUERTDUELL en EB Garamond, l'accroche
  « Le duel des mots luxembourgeois », **quatre vraies cartes** (une par
  boîte, exportées par l'appli), « 2 joueurs · dès 8 ans · 15 min », le QR de
  `regle-du-jeu.html`, la règle d'or, la légende des quatre couleurs, et une
  **pastille blanche vide** pour l'étiquette de prix du commerçant.
- **Trois pièces** : le bac (d'un seul tenant), le fronton, trois cloisons sans
  impression. Impression recto seul.

## 2. Les scripts

| Quoi | Où |
|---|---|
| Présentoir : gabarit, visuels, BAT, maquette | `scripts/presentoir.py` |
| Étuis (que le présentoir reprend) | `scripts/emballage.py` |
| Rectos des cartes (que les deux reprennent) | `scripts/rectos_boite.py`, `scripts/exporter_rectos.sh` |
| Dessins communs (rosace, lion, joyaux, or) | `cartes-imprimables/dessin/verso_luxe.py`, `carte_appli.py` |
| Fiche technique pour les fabricants | `impression/emballage/LISEZMOI.md` |

L'ordre, depuis la racine du dépôt :

```bash
python3 scripts/emballage.py        # les quatre étuis, la gamme ; ou un seul : … NA1
python3 scripts/presentoir.py       # après les étuis : la maquette lit leurs PDF
```

Sorties dans `impression/emballage/presentoir/` (**privé** : `impression/` est
ignoré par git) :

- `presentoir_bac.pdf`, `presentoir_fronton.pdf`, `presentoir_cloisons.pdf` :
  une pièce par PDF, page 1 le visuel (fond perdu 3 mm), page 2 le tracé seul.
  Un PDF par pièce parce que Chromium donne à toutes les pages la taille de la
  première ;
- `presentoir_bat.pdf` : les trois pièces sur une planche, tracés, cartouche,
  notes en magenta ;
- `presentoir_plat.png`, `presentoir_maquette.png` : aperçus.

## 3. Modifier

- **Les cotes** sont en tête de `presentoir.py` (`COL`, `CLOISON`, `NCOL`, `EP`,
  `PROF`, `LEVRE`, `DOS_H`, `FRONTON_H`, `PATTE_*`). Elles suivent celles de
  l'étui (`E.W`, `E.H`, `E.D` dans `emballage.py`) : si l'imprimeur change le
  gabarit de l'étui, refaire les deux.
- **Les boîtes présentées** : `ORDRE`. Pour un présentoir d'une seule boîte
  (12 boîtes de départ), une liste de quatre fois `'B1'` suffit ; la lèvre et la
  légende suivent.
- **Les cartes du fronton** : `CARTES_FRONTON`, (boîte, mot). Choisir un mélange
  de métaux (au moins un or, un argent) et des dessins qui se lisent de loin. Les
  cartes de l'éventail des étuis sont dans `PRODUITS[…]['eventail']`
  (`emballage.py`), avec une carte or au centre.
- **Une nouvelle boîte** : l'ajouter d'abord à `PRODUITS` (couleur, paquets,
  emblèmes, contenu, `ean=None`) et à `rectos_boite.BOITES`, exporter ses
  rectos, puis l'ajouter à `ORDRE`. Avec cinq colonnes, le bac passe à
  342 mm : vérifier qu'il tient encore sur un comptoir et sur une feuille.
- **Un emblème de paquet** : `embleme()` dans `emballage.py`, recopié de
  `CadreOrne.emblemeDuPaquet` (même dessin que le médaillon des cartes).

## 4. Les règles

- **Rien de provisoire dans un fichier d'impression** (propriétaire,
  2026-11-01). Pas de « … € », pas de « [à compléter] », pas de faux code-barres.
  Ce qui manque n'est pas imprimé et n'apparaît que sur le BAT, en magenta.
  Les mentions de l'éditeur, du pays de fabrication, du marquage CE et les EAN
  sont des paramètres en tête de `emballage.py`.
- **Les cartes montrées sont de vraies cartes** exportées par l'appli
  (`impression/<boîte>/recto/` ou, avant assemblage, `export/`), jamais un
  dessin refait.
- **Textes** : en français, jargon interdit, pas de tiret cadratin, espaces
  insécables. Tout texte nouveau se montre en entier à Medhi avant d'être
  appliqué. Les âges et la durée viennent de `docs/regle-du-jeu.md`
  (Bataille des mots dès 6 ans · 5 min ; Wuertduell dès 8 ans · 15 min).
- **Montrer les images à chaque itération** (`SendUserFile`) : Medhi l'a demandé.
- Pas de logo de tiers, pas de nom d'événement sur le présentoir sans l'accord
  écrit de l'organisateur.

## 5. Les pièges

- **Le plan est vu du dessous.** La face imprimée du bac est l'extérieur, donc
  le plan à plat montre le fond vu d'en dessous : **le flanc de gauche du plan
  est le flanc droit du présentoir**. Chaque paroi est tournée pour se lire une
  fois montée, le haut vers l'extérieur du fond : dos à l'endroit, seconde
  paroi du dos et lèvre tête en bas, flanc gauche du plan tourné de −90° avec
  l'avant à gauche, flanc droit de +90° avec l'avant à droite. Un visuel ne
  sort jamais en miroir quand on le regarde du côté imprimé.
- **Le dos est à double paroi** : la paroi qui fait face au client est la
  seconde, rabattue à l'intérieur ; c'est elle qui porte le visuel côté client.
  Sans elle, le client verrait le carton nu.
- **Les définitions après les dessins** : `CA.joyau` inscrit ses dégradés en
  dessinant. Calculer `defs()` après avoir dessiné le fronton, la lèvre, le dos
  et un flanc, sinon les perles du filet sortent sans couleur.
- **Rogner le fronton** à son rectangle et à son fond perdu : la rosace
  débordait entre les deux pattes.
- **La maquette CSS 3D** : `rotateX(90deg)` met le haut d'une image vers le
  fond ; le dessus d'un étui se donne donc tourné de 180° par rapport au plan.
  Chromium sans interface rend 87 px de moins que la hauteur de sa fenêtre :
  ouvrir plus haut et rogner. Les faces des étuis sont prises dans leur PDF
  d'impression : relancer `emballage.py` avant `presentoir.py`.
- **PDF par Chromium** : pas de transparence avant un dégradé, pas de texte
  détouré (police Type 3 refusée par les imprimeurs). Contrôler avec
  `pdffonts` : EB Garamond et Roboto en TrueType incorporé, rien d'autre.

## 6. Contrôler avant de montrer

1. `pdfinfo` : tailles de page = cotes + 6 mm de fond perdu (bac 484 × 346 mm,
   fronton 280 × 226 mm, cloisons 234 × 71 mm).
2. `pdffonts` sur chaque PDF : polices incorporées, aucune Type 3.
3. Le QR du fronton se relit : `zxingcpp` (installé en `--user`) sur un rendu
   à 300 dpi. Même contrôle pour un EAN sous l'étui. OpenCV ne lit pas
   les codes-barres sur cette machine, ne pas s'y fier.
4. La maquette à l'œil : chaque paroi à l'endroit, la lèvre ne cache pas les
   titres des étuis, rien ne touche le filet d'or.
5. Le BAT : les notes magenta disent ce qui manque encore.

## 7. Pour le fabricant

Le gabarit est une base de travail, pas un plan de fabrication : un fabricant
de PLV reprend les cotes sur le sien (cannelure, rainages, verrous du fond,
pattes). Lui envoyer `presentoir_bat.pdf` et les trois PDF, demander son
gabarit, et y reporter les visuels (les fonctions `fronton()`, `levre()`,
`dos_exterieur()`, `dos_interieur()`, `flanc()` dessinent chacune dans son
repère, à recaler). Demander au devis : carton, impression, montage à plat ou
monté, livraison au Luxembourg, et un prototype avant la série.

## 8. Pas encore fait

- Les deux cartes de règle de chaque boîte (schéma seulement, `scripts/planche_boite.py`).
- Le carton d'expédition (12 étuis, un présentoir garni).
- Une version du présentoir pour une seule boîte (prévue par `ORDRE`, jamais rendue).
