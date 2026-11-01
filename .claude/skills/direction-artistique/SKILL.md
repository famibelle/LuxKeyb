---
name: direction-artistique
description: Direction artistique de LuxKeyb — la ligne visuelle des cartes et de leurs décors, en particulier le « théâtre de papier » (décor en cartons découpés superposés, parallaxe multi-plans dans la fenêtre de la carte). À utiliser quand on discute de l'univers visuel, du style des paysages et des décors, de la profondeur et du mouvement dans la fenêtre d'une carte (inclinaison, parallaxe, profondeur de champ), quand on dessine un nouveau lieu ou un nouveau plan de décor, quand on ajoute une animation au décor, ou quand on se demande si un choix graphique « fait LuxKeyb ». Couvre la direction décidée le 2026-11-01 : couleurs vives peintes à la gouache sur carton, et le dessin du mot en plaque épaisse sur un pied de métal, dont seule la lumière bouge.
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
  haute au loin, posées sur le plancher de la scène (la chaussée pavée des
  débuts a disparu le 2026-10-30). Ses coulisses de rare sont deux piliers de
  pierre et deux pignons de maison, pas des arbres. Il a été mis au point sur
  une maquette qui reprend les outils de tracé de `Paysages.kt` en JavaScript
  (`maquette/`, à côté de cette skill : `node capture.js` rend les quatre
  heures, les plans penchés, les silhouettes et la vignette ; `vegetal.py` dit
  quelles couleurs `Habillage` prendrait pour de la verdure), faute
  d'appareil : la voie à reprendre pour un prochain lieu ;
- une carte gagnée un **jour de fête** garde un paysage à plat (ses lumières
  éclairent le paysage entier et ne se répartissent pas en calques).

**Le rendu en carton, fait le 2026-10-29** (branche `feat/theatre-papier`,
`Paysages.rendrePlans`), vu sur l'émulateur, pas encore jugé en main :

1. **L'ombre entre les plans.** Chaque carton porte une ombre nette et marquée
   sur tous ceux de derrière (plus légère sur le premier calque, la toile de
   fond). La lumière est celle de la **rampe**, au bas de la scène et un peu à
   gauche : les ombres montent au-dessus des crêtes et longent le bord droit
   des cartons, là où on les voit. Une lumière d'en haut les cacherait derrière
   le carton même qui les porte. Courte et douce jusqu'au 2026-10-30, elle ne
   séparait pas les cartons : la fenêtre se lisait comme une seule image à
   liseré blanc (jugement du propriétaire, maquette à l'appui).
2. **La bordure blanche**, la signature du théâtre de papier : l'âme du
   papier, tout autour de chaque découpe, blanche même la nuit (à peine
   teintée par le ciel). Rien de ce qui est trop fin pour une paire de
   ciseaux (rayons d'une roue, haubans) n'en a : bordé, il devenait un trait
   blanc. Celle du lointain est plus pâle, tirée vers l'horizon. Le liseré
   au seul bord haut des premières versions ne se voyait pas la nuit.
3. **Plus de flou.** Le lointain s'efface vers la couleur de l'horizon (30 %),
   le milieu à peine (10 %).
4. **Les coulisses des rares** : un quatrième carton, deux arbres en silhouette
   sur les côtés, sous l'arche du cadre, qui glissent à peine (0,3 %), et
   depuis le 2026-10-30 une **seconde rangée** un pas en retrait (un arbre plus
   petit et plus clair, collé au carton du proche) : deux châssis étagés font
   une boîte de théâtre, un seul faisait un cadre.
5. **Le nuage des très rares** : un nuage de carton qui traverse le ciel en
   75 s, entre le lointain et le milieu.
6. **Le plancher** (2026-10-30) : chaque lieu se pose sur des lames de bois au
   lieu de descendre jusqu'au bas de la fenêtre. Le carton du proche s'arrête
   net dans la rainure du devant de la scène (`DEVANT`, 58,5 unités, juste
   au-dessus du bandeau du nom, qui cache tout ce qui est plus bas sur la
   carte ouverte) ; la ruelle du Breedewee et les rives de la Sûre et de la
   Moselle ont leur propre rainure plus haut. Les rivières sont des rangées de
   vagues découpées (`riviere`), pas une eau qui descend jusqu'au bord. Le
   plancher est peint par bandes, une par carton, et reçoit les ombres sans
   en porter ni avoir de bordure.

Tout est peint une fois au rendu des calques ; seul le nuage se place à chaque
trame. La vignette de la grille garde son rendu à plat. **Reste à faire** :
mesurer le coût du rendu sur le Galaxy A21s en build release (budgets dans
`carnet/CLAUDE.md`), le jugement en main, d'autres éléments qui vivent seuls
selon le lieu (drapeau, barque sur la Sûre, vagues sur la Moselle) à la place
du nuage générique, et le cadre en rideau de scène (§ 6).

### La gouache vive sur carton, et la pancarte (décidées le 2026-11-01)

Le propriétaire a retenu et validé sur maquette (« je valide la direction
actuelle », 2026-11-01), pour **toutes les cartes**, une évolution du
théâtre de papier inspirée d'une vitrine en papier découpé (Google Store,
Paris) : **des couleurs vives, peintes à la gouache sur du carton**. Pour
l'instant c'est une **maquette** (`maquette/gouache/`), rien n'est dans
`Paysages.kt` ni dans `CadreOrne.kt`.
Dans `maquette/gouache/`, `node capture.js` rend le Breedewee aux quatre
heures (avec la place du dessin en rouge), et `node rendre.js pancarte.html
sortie.png` la fenêtre de la carte « Bus » avec sa pancarte, à la place
exacte de la fenêtre d'un recto imprimé à 300 dpi.

**Le décor :**

- **Quatre palettes vives**, une par heure (`maquette/gouache/vives.js`) :
  le matin, ciel bleu franc qui passe au rose puis au jaune pâle vers
  l'horizon, soleil jaune vif, collines lavande, verts francs, eau bleu
  soutenu ; l'après-midi orangé ; le soir violet, rose et orange ; la nuit
  bleu profond, la vivacité y venant des lumières dorées. L'heure doit
  toujours dire la rareté au premier coup d'œil (le soir et l'après-midi
  sont encore trop proches).
- **Chaque pièce est un carton peint** (`peint` dans `peint.js`) : le carton
  brun kraft d'abord, avec son ombre ; puis la gouache, mate, un peu mince ;
  des **coups de pinceau** dans le sens de la forme (horizontaux pour le
  ciel et l'eau, courts et obliques pour la pierre et le grès, ronds pour le
  feuillage) ; un **bord sec** où le brun du carton reparaît. Le ciel est
  peint à larges touches ; l'astre se peint après, d'un aplat.
- **La tranche est celle du carton**, brune, et non plus l'âme blanche du
  papier (point 2 ci-dessus) : c'est ce que montrent toutes les maquettes,
  validées par le propriétaire le 2026-11-01. Les rectos imprimés, qui ont
  la bordure blanche, seront refaits avec le reste.
- **Des nuages de carton** flottent dans la bande du haut, et la pierre se
  fait en **pièces étagées** (une masse, une face à l'ombre, une face claire,
  des strates collées).
- **De la couleur dans les lieux qui en manquent** : une ruelle de pierre
  reste beige, même vive. Au Breedewee, les façades prennent les couleurs du
  Grund (rose, jaune, bleu, vert, orange) et un lierre vert fleuri retombe
  de l'arche.

**Le dessin du mot devient une plaque sur un pied** (`maquette/gouache/`,
carte « Bus » : `pancarte.html` pour la fenêtre, `anim.html` et `anim.js`
pour l'inclinaison). Décisions du propriétaire, du 2026-11-01 :

- **Une plaque épaisse, fixe.** La silhouette est une plaque d'environ 1,3
  unité d'épaisseur : sa tranche se voit en bas à droite, plus sombre vers
  l'arrière, et elle porte une ombre sur le décor. **Elle ne bouge pas** :
  ni balancement ni épaisseur qui change de côté (le balancement, essayé,
  a été écarté). Le volume se comprend par le dessin et par la lumière.
- **Seule la lumière bouge à l'inclinaison** : un reflet en bande traverse
  la face quand on penche le téléphone (plus vif sur le métal plein, absent
  aux fortes inclinaisons), le chanfrein du bord de la face s'éclaire du
  côté de la source, qui tourne avec le geste, et un éclat glisse le long
  de la tige et sur le socle. Le décor, lui, continue de glisser carton par
  carton derrière.
- **Le pied et le bord dans le métal du palier**, comme le cadre : acier
  (commune), bronze (peu commune), argent (rare), or (très rare). Une tige
  fine et un socle rond, comme un présentoir de bijou, posé sur le plancher
  avec son ombre. Le pied en bois peint, essayé d'abord, jurait avec la
  nacre ; le verre faisait flotter le dessin.
- **La face suit la même échelle de matière que le support du nom** :
  nacre teintée du métal (acier, bronze) pour la commune et la peu commune,
  qu'on voit le plus et qui doivent rester lumineuses et lisibles ; argent
  et or pleins pour la rare et la très rare (le métal plein sombre, en acier
  ou en bronze, éteignait le dessin). Validée par le propriétaire le
  2026-11-01.
- **La hauteur du socle se juge sur les vrais dessins** : sur un dessin haut
  (arbre, personne), on ne verra presque que le socle.

**Le centre est au dessin.** Depuis la 38.0.0, le dessin du mot tient dans
une boîte de 66 × 46, centrée (17 à 83, 12,5 à 58,5). Le décor vit donc dans
la bande du haut et sur les bords : c'est là que mettre la couleur et les
détails. Une porte, un pont ou un monument placé au centre disparaît derrière
le dessin (la porte du Breedewee a dû être élargie en avant-scène autour de
lui ; sa taille reste à revoir avec le propriétaire).

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
   permis pour le ciel, qui est la toile de fond. En gouache, l'aplat vit par
   ses coups de pinceau, pas par un dégradé.
3. **Le lointain s'efface par la couleur, pas par le flou** : plus clair, moins
   saturé, plus proche du ciel.
4. **Une ombre par plan, nette, toujours du même côté** : la même lumière de
   scène sur toutes les cartes, celle de la rampe, qui porte les ombres vers
   le haut et un peu à droite. C'est le vide qu'on voit derrière chaque
   découpe qui fait le théâtre de papier.
4 bis. **Chaque carton est posé.** Il s'arrête net sur le plancher, dans sa
   rainure ; rien ne descend jusqu'au bas de la fenêtre. Un carton qui se
   perd sous le cadre est une affiche, pas un décor.
5. **Le mot reste l'acteur.** Le décor ne rivalise jamais avec le dessin du
   mot : pas de détail fort ni de contraste maximal derrière lui. Le dessin
   est une plaque épaisse, fixe, sur un pied de métal posé sur le
   plancher : il est sur la scène, pas collé sur le décor.
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

**La gouache vive et la pancarte** (§ 2, décidées le 2026-11-01), dans cet
ordre :

1. Valider les quatre palettes vives sur une même carte (le soir et
   l'après-midi à écarter).
2. Le rendu gouache dans `Paysages.rendrePlans` (carton kraft, coups de
   pinceau, bord sec), une fois pour les dix lieux. Pour tenir sur le Galaxy
   A21s, des textures de pinceau préparées d'avance et posées dans le sens de
   chaque pièce plutôt que des centaines de touches tracées une à une. La
   vignette garde des aplats simples.
3. La plaque dans `CadreOrne` (`Enluminure`) : épaisseur et ombre portée
   dessinées une fois, tige et socle dans le métal du palier, et le reflet
   qui suit l'inclinaison (`Inclinaison.kt`) ; la plaque elle-même ne bouge
   pas.
4. Les retouches lieu par lieu là où la couleur manque, le centre restant
   calme derrière le dessin.
5. Recalibrer la reconnaissance de la végétation des saisons (`Habillage`),
   qui lit les couleurs de la palette.
6. Réexporter les rectos des quatre boîtes et vérifier l'impression.

## 7. Revue d'un décor

Avant de valider un nouveau lieu ou une modification de décor :

- [ ] Chaque plan se lit en silhouette noire.
- [ ] Penché sur un vrai téléphone, on voit les cartons se décaler sans
      chercher.
- [ ] L'ombre entre les plans est présente, nette, du même côté que les autres
      cartes.
- [ ] Chaque carton s'arrête net sur le plancher, au-dessus du bandeau du nom.
- [ ] Le lointain s'efface par la couleur ; rien n'est flou.
- [ ] Le dessin du mot reste ce qu'on voit en premier.
- [ ] La plaque est posée et fixe : son pied descend jusqu'au socle, sur le plancher ; seul son reflet bouge à l'inclinaison.
- [ ] Les couleurs vivent dans la bande du haut et sur les bords, pas derrière le dessin.
- [ ] En vignette dans la grille, le décor est calme et lisible.
- [ ] Aux quatre heures, le décor reste lisible (la nuit surtout).
- [ ] Le mouvement autonome, s'il y en a un, est lent et unique.
- [ ] Animations coupées : la carte reste belle à plat.

Rends la revue en quatre parties : **Verdict** (une phrase), **Ce qui fait
théâtre de papier**, **À corriger** (concret, avec la règle du § 4 concernée),
**Prochain test**.
