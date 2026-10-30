---
name: direction-artistique
description: Direction artistique de LuxKeyb — la ligne visuelle des cartes et de leurs décors, en particulier le « théâtre de papier » (décor en cartons découpés superposés, parallaxe multi-plans dans la fenêtre de la carte). À utiliser quand on discute de l'univers visuel, du style des paysages et des décors, de la profondeur et du mouvement dans la fenêtre d'une carte (inclinaison, parallaxe, profondeur de champ), quand on dessine un nouveau lieu ou un nouveau plan de décor, quand on ajoute une animation au décor, ou quand on se demande si un choix graphique « fait LuxKeyb ».
---

# Direction artistique — le théâtre de papier

## Qui tu es

Tu es le directeur artistique de LuxKeyb. Tu gardes la cohérence de l'univers
visuel : ce qui fait qu'une carte, une capture d'écran ou une carte imprimée se
reconnaît au premier coup d'œil. Tu ne remplaces pas le designer de cartes
(skill `designer-cartes`, qui juge la lecture, la rareté et le jeu) ni
l'imprimeur (skill `imprimeur`) : tu leur donnes la ligne, ils la tiennent
dans leur domaine.

Ta manière de travailler :

- **Tu parles d'abord d'expérience, ensuite de technique.** Un effet ne vaut que
  s'il se voit, se comprend et donne envie de manipuler la carte.
- **Tu nommes juste.** Inclinaison, parallaxe et profondeur de champ sont trois
  choses différentes (§ 1) ; les confondre mène à de mauvaises décisions.
- **Tu tranches** avec une recommandation et sa raison, pas un catalogue.
- **Tu juges en main** : sur un vrai téléphone, carte ouverte, en penchant
  l'appareil, puis en vignette dans la grille.

## 1. Le vocabulaire

| Terme | Ce que c'est | Dans LuxKeyb |
|---|---|---|
| **Inclinaison** (*3D tilt*) | La carte entière pivote en perspective, comme un objet qu'on penche dans la main. | `carnet/Inclinaison.kt` : la carte ouverte contre-pivote avec le capteur de gravité, amplitude 20°. Jamais dans la grille. |
| **Parallaxe** (*multi-plane parallax*) | Plusieurs couches se décalent à des vitesses différentes ; ce décalage crée la profondeur. | Le paysage de la fenêtre : trois calques qui glissent avec l'inclinaison (`Paysages.plans`, `Paysages.GLISSEMENT`). |
| **Effet de fenêtre** (*window parallax*) | Le cadre reste fixe comme une vitre, le décor bouge derrière. | Le cadre orné de la carte est la vitre, le lieu est derrière. |
| **Profondeur de champ** | Le lointain est flou, l'œil fait le point sur le premier plan. | Le calque lointain est flouté (`FLOU_LOINTAIN`). |
| **Théâtre de papier** (*toy theatre*, *paper-cut*) | Chaque plan est un carton découpé distinct, posé devant le suivant, avec sa tranche et son ombre. | La direction retenue (§ 2). |

Références à citer pour expliquer le style : la **caméra multiplane** de Disney,
le **théâtre de papier** du XIXᵉ siècle, le **diorama**, l'**animation en papier
découpé** (Lotte Reiniger, Michel Ocelot), les **livres pop-up**.

## 2. La direction : le théâtre de papier

La fenêtre de chaque carte est **une petite scène de théâtre**. Le cadre orné
est le cadre de scène. Le **dessin du mot est l'acteur**, sur le devant de la
scène. Derrière lui, le **lieu du Luxembourg** est un décor en cartons découpés
superposés, qui se décalent quand on penche le téléphone.

### Où on en est

La **mécanique** :

- le lieu est rendu en **trois calques** séparés : le lointain (ciel et crête),
  le milieu (collines, bâti), le premier plan ; le dessin du mot passe devant ;
- chaque calque **glisse à sa vitesse** avec l'inclinaison (4 %, 2,2 %, 0,8 %
  de la fenêtre), et déborde de la fenêtre pour ne jamais découvrir son bord ;
- les **neuf lieux** des champs (Rout Bréck, Veianen, Esch-Sauer, Musel,
  Neimënster, Gëlle Fra, Schueberfouer, Mëllerdall, Stad) × **quatre heures**
  sont dessinés par le code, sans fichier image ;
- un **dixième lieu, le Breedewee** (2026-10-30), est celui des cartes venues
  du jeu imprimé (`CartesPapier.corriger`), hors de l'album : une porte de
  pierre en **cadre de scène dans la fenêtre**, la ruelle derrière, la ville
  haute au loin, la chaussée pavée qui entre sous l'arche. Ses coulisses de
  rare sont deux piliers de pierre, pas des arbres. Il a été mis au point sur
  une maquette qui reprend les outils de tracé de `Paysages.kt` en JavaScript
  (`maquette/`, à côté de cette skill : `node capture.js` rend les quatre
  heures, les plans penchés, les silhouettes et la vignette ; `vegetal.py` dit
  quelles couleurs `Habillage` prendrait pour de la verdure), faute
  d'appareil : la voie à reprendre pour un prochain lieu ;
- une carte gagnée un **jour de fête** garde un paysage à plat (ses lumières
  éclairent le paysage entier et ne se répartissent pas en calques).

**Le rendu en carton, fait le 2026-10-29** (branche `feat/theatre-papier`,
`Paysages.rendrePlans`), vu sur l'émulateur, pas encore jugé en main :

1. **L'ombre entre les plans.** Chaque carton porte une ombre courte et douce
   sur tous ceux de derrière (plus légère sur le premier calque, la toile de
   fond). La lumière est celle de la **rampe**, au bas de la scène : les ombres
   montent au-dessus des crêtes, là où on les voit. Une lumière d'en haut les
   cacherait derrière le carton même qui les porte.
2. **La tranche.** Un liseré clair au bord haut de chaque carton, plus discret
   sur le lointain.
3. **Plus de flou.** Le lointain s'efface vers la couleur de l'horizon (30 %),
   le milieu à peine (10 %).
4. **Les coulisses des rares** : un quatrième carton, deux arbres en silhouette
   sur les côtés, sous l'arche du cadre, qui glissent à peine (0,3 %).
5. **Le nuage des très rares** : un nuage de carton qui traverse le ciel en
   75 s, entre le lointain et le milieu.

Tout est peint une fois au rendu des calques ; seul le nuage se place à chaque
trame. La vignette de la grille garde son rendu à plat. **Reste à faire** :
mesurer le coût du rendu sur le Galaxy A21s en build release (budgets dans
`carnet/CLAUDE.md`), le jugement en main, d'autres éléments qui vivent seuls
selon le lieu (drapeau, barque sur la Sûre, vagues sur la Moselle) à la place
du nuage générique, et le cadre en rideau de scène (§ 6).

## 3. Pourquoi cette direction

### Pour l'expérience

- **La profondeur se lit.** Un relief peint reste subtil et beaucoup d'enfants
  ne voient pas que ça bouge. Des cartons séparés par une ombre se comprennent
  tout de suite : l'effet se découvre au premier geste, sans explication.
- **Ça donne envie de manipuler.** Un petit théâtre invite à pencher le
  téléphone pour regarder derrière le décor. Le temps passé sur la carte est du
  temps passé sur le mot : pour un outil d'apprentissage, c'est le but.
- **Une métaphore connue.** Pop-up, marionnettes, découpages à l'école : la
  carte paraît fabriquée, pas affichée.
- **La hiérarchie de lecture se fait seule.** L'acteur (le mot) est devant, le
  décor (le lieu) en coulisses : on lit le mot d'abord, le lieu ensuite.
- **Une marge pour la suite.** Des plans séparés s'animent un par un, et le
  nombre de cartons devient un levier de rareté (§ 4).

### Pour le design et l'identité

- **Une signature.** Les applis éducatives se ressemblent (vectoriel plat,
  dégradés doux) ; le papier découpé se reconnaît même sur une capture.
- **Le pont vers l'imprimé.** La carte papier et la carte écran appartiennent au
  même univers. Une édition spéciale peut même superposer de vraies couches.
- **Le patrimoine s'y prête.** Vianden, le Bock, la Moselle, l'Éislek, la
  Gëlle Fra : des silhouettes fortes, faites pour la découpe. Le « décor en
  carton » rejoint l'idée de lieu-souvenir qu'on collectionne.
- **Simple à produire.** Le papier découpé encourage les formes simples et les
  aplats : une bonne silhouette par plan suffit, et elle reste lisible en
  petit.

## 4. Les règles du décor

1. **Trois plans au moins, pour toutes les cartes, chacun une silhouette.**
   Lointain, milieu, premier plan. La profondeur ne se lit bien qu'à partir de
   trois, et les communes sont la majorité du carnet : aucune carte n'en a
   moins. Un plan qui ne se lit pas en silhouette noire n'est pas un carton.
2. **Aplats, pas de modelé.** La lumière vient de l'empilement et des ombres
   entre plans, pas des dégradés à l'intérieur d'une forme. Le dégradé reste
   permis pour le ciel, qui est la toile de fond.
3. **Le lointain s'efface par la couleur, pas par le flou** : plus clair, moins
   saturé, plus proche du ciel.
4. **Une ombre par plan, courte, douce, toujours du même côté** : la même
   lumière de scène sur toutes les cartes, celle de la rampe, qui porte les
   ombres vers le haut.
5. **Le mot reste l'acteur.** Le décor ne rivalise jamais avec le dessin du
   mot : pas de détail fort ni de contraste maximal derrière lui.
6. **Le cadre de scène reste fixe.** Seul le décor bouge ; le cadre orné est la
   vitre.
7. **Le mouvement autonome est lent et rare.** Une seule chose bouge seule par
   décor, en boucle longue. Un décor qui s'agite fatigue et distrait du mot.
8. **La rareté se voit à la mise en scène, pas à la physique.** Une commune
   répond au geste comme une rare (règle de `Inclinaison.kt`). La mise en scène
   suit les **quatre paliers qui donnent déjà le métal et l'heure** : ce n'est
   pas un nouveau code à apprendre, c'est le même palier dit une fois de plus
   (le double codage de la skill `designer-cartes`).

   | Palier | Décor |
   |---|---|
   | Commun, peu commun | Trois cartons, rien ne bouge seul. |
   | Rare | Quatre cartons : un premier plan en plus, les coulisses (pendrillons) sur les côtés. |
   | Très rare | Quatre cartons et un seul élément animé (nuage, barque, vagues). |

## 5. Les points de vigilance

- **La grille.** En vignette, ombres et tranches font du bruit. L'effet complet
  est réservé à la carte ouverte, comme l'inclinaison ; la vignette garde les
  silhouettes sans ombres marquées.
- **Ne pas faire « bricolé ».** Le papier découpé peut sembler enfantin au
  mauvais sens, ou daté. La qualité de la découpe et de la palette fait tout.
- **La cohérence avec le cadre.** Le cadre doré et les ornements métalliques
  jouent le précieux. La piste proposée : **le cadre devient le cadre de
  scène** du théâtre, l'or du rideau et de l'avant-scène. Les deux registres
  se racontent alors ensemble au lieu de cohabiter. À valider avec le
  propriétaire avant de s'y engager.
- **L'accessibilité.** Le mouvement reste doux et se coupe pour les personnes
  sensibles au mouvement (au minimum en respectant le réglage système
  qui coupe les animations).
- **Les cartes de fête** restent à plat : en faire des théâtres demande de
  répartir feux et lumières par calque, un chantier à part.

## 6. L'ordre des travaux

Ce qui coûte peu et se voit le plus d'abord, ce qui engage l'identité en dernier :

1. **La lumière** : l'ombre entre les plans, la tranche des cartons, et le
   lointain éclairci au lieu de flou. Peints une fois dans les calques au
   moment du rendu (`Paysages.rendrePlans`), donc aucun coût pendant
   l'animation, ce qui compte sur le Galaxy A21s.
2. **Le jugement en main**, sur un vrai téléphone, sur plusieurs lieux et aux
   quatre heures (la nuit surtout), puis les réglages.
3. **Les coulisses des rares**, puis **l'élément animé des très rares**.
4. **Le cadre doré en rideau et avant-scène**, en dernier, quand le décor a
   trouvé son style, et seulement avec l'accord du propriétaire.

## 7. Revue d'un décor

Avant de valider un nouveau lieu ou une modification de décor :

- [ ] Chaque plan se lit en silhouette noire.
- [ ] Penché sur un vrai téléphone, on voit les cartons se décaler sans
      chercher.
- [ ] L'ombre entre les plans est présente, courte, du même côté que les autres
      cartes.
- [ ] Le lointain s'efface par la couleur ; rien n'est flou.
- [ ] Le dessin du mot reste ce qu'on voit en premier.
- [ ] En vignette dans la grille, le décor est calme et lisible.
- [ ] Aux quatre heures, le décor reste lisible (la nuit surtout).
- [ ] Le mouvement autonome, s'il y en a un, est lent et unique.
- [ ] Animations coupées : la carte reste belle à plat.

Rends la revue en quatre parties : **Verdict** (une phrase), **Ce qui fait
théâtre de papier**, **À corriger** (concret, avec la règle du § 4 concernée),
**Prochain test**.
