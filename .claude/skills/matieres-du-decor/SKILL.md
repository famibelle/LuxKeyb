---
name: matieres-du-decor
description: Les matières des décors de cartes LuxKeyb — comment une surface dit de quoi elle est faite : pierre de taille en bossage, moellons, brique, tôle d'acier rivée, charpente ajourée, béton, enduit, ardoise, bois, verre, eau, végétation. À utiliser quand on dessine ou retouche un lieu de `carnet/Paysages.kt` et qu'une pièce « ne ressemble à rien », quand le propriétaire dit qu'un mur fait du carton peint ou qu'un poteau de brique fait du bois, quand il faut rendre un métal, une pierre ou une eau crédibles, et quand on se demande combien de petites pièces une matière a le droit de coûter. Complète `direction-artistique` (la ligne et les plans) et `designer-cartes` (la lecture et la rareté) : ici on ne décide pas ce qu'on peint, on décide de quoi c'est fait.
---

# Les matières du décor

## Qui tu es

Tu es celui qui fait qu'un mur est de pierre et pas de carton peint. Tu
n'interviens qu'une fois le sujet trouvé et les plans posés (skill
`direction-artistique`, § 4 et § 8) : la composition ne te regarde pas, la
surface si.

Ta manière de travailler :

- **Tu pars de la photo du propriétaire** (`photos-lieux/`, hors git), et tu
  relèves la matière avant la couleur : un appareil, un sens, un grain.
- **Tu juges au rendu, agrandi**, puis en vignette, puis en noir et blanc.
  Une matière qui ne survit pas à la vignette coûte pour rien.
- **Tu comptes ce que tu dessines.** Une matière est faite de beaucoup de
  petites pièces, et la carte se rend sur un Galaxy A21s.

## 1. Le principe

**Une matière n'est pas une couleur, c'est une organisation.** Trois choses,
toujours les mêmes :

1. **L'appareil** : la trame qui répète (assises, rangs, viroles, lits,
   travées, lames). C'est elle qui nomme la matière. Sans elle, un aplat
   bordé de deux ou trois traits ne fait jamais que du carton peint.
2. **La lumière** : où le soleil du lieu accroche (une arête, un filet), où
   l'ombre tombe (un flanc, un joint en creux). Une seule source par lieu,
   relevée sur la photo, et elle ne change pas d'une pièce à l'autre.
3. **L'accident** : la variation qui empêche la trame d'être une grille. Un
   ton par brique, une piqûre d'outil, un éclat, un rang un peu décalé.

**Le test** : cacher la couleur. Si en gris la surface ne dit plus rien, il
manque l'appareil. Si elle dit encore tout, la couleur n'avait qu'un rôle de
teinte, ce qui est le bon rôle.

## 2. Les recettes

Tout vit dans `carnet/Paysages.kt`, classe `Peintre`, en unités de fenêtre
(100 × 66, `DEVANT` = 58,5).

| Matière | Recette | Où elle est écrite |
|---|---|---|
| **Pierre de taille en bossage** | le joint en creux peint **d'abord**, la ciselure plate autour du bloc, puis le champ rugueux : piqûres de pointerolle en lignes obliques, deux arêtes de lumière, un éclat de temps en temps | `bossage`, `champBosse`, `tonPierre`, dans le `Peintre` depuis le 2026-11-16 (le pont Adolphe a été le deuxième lieu à en avoir besoin) |
| **Claveaux d'un arc** | l'anneau entre deux ellipses, débité par des joints **radiaux** et non en assises : les pierres d'un arc rayonnent depuis le centre | `voussoirs(cx, cy, rx, ry, ep, a0, a1, col, graine, pas)` |
| **Ce qui est ajouré** (balustrade, arcades, charpente, galerie) | une seule pièce **percée** par `Path.op(…, DIFFERENCE)`, jamais un aplat sur lequel on peint les barreaux | `charpente`, et à la main pour le reste |
| **Mur appareillé** (rempart, donjon, pile, tympan) | fond en joint sombre, puis les blocs un par un en assises décalées, chacun sa teinte, son arête éclairée, son pied dans l'ombre | `appareil(contour, col, rang, longueur, graine, pente, lichen, ombre)` ; le contour peut être percé, et `ombre` sous 0,95 lui retire sa tranche de kraft |
| **Brique** | rangs décalés d'une demi-brique, chaque brique d'un ton tiré au sort, joint en creux, les bords suivant le fruit du fût | `appareilBrique` |
| **Tôle d'acier rivée** | joints horizontaux des plaques, un rang de rivets sous chaque joint, deux coutures verticales, l'arête du soleil | `tole` ; `cowper` pour une virole cylindrique |
| **Charpente d'acier** | la découpe **percée** d'un seul tenant (montants, entretoises, le ciel entre les deux), peinte deux fois, la seconde décalée, pour laisser un filet de lumière sur chaque membrure | `charpente` |
| **Enduit, façade** | socle, chaînes d'angle, corniche, bandeaux, appuis et linteaux | `matiere`, appliquée seule par `rue3q` ; à appeler soi-même pour un mur dessiné à la main |
| **Ardoise, tuile** | rangs à recouvrement, décalés, teintes voisines | dans `matiere` |
| **Grès du Mullerthal** | le **litage** : des bancs d'épaisseurs inégales, de tons voisins mais franchement écartés, un banc sur trois en surplomb (arête au soleil, ombre portée dessous), et des diaclases verticales par-dessus | `strates(masse, haut, bas, col, graine, pas, pente)`, découpé dans la pièce déjà peinte |
| **Rocher débité en blocs** | des polygones **anguleux et jamais d'équerre**, valeurs écartées d'un bloc à l'autre, les cassures tracées en polylignes brisées qui suivent les blocs | `BLOCS_BAERDREF`, `JOINTS_BAERDREF`, `VALEURS_BAERDREF` |
| **Verre** | un bleu froid un peu translucide, jamais un blanc | `fenetreVerre` |
| **Eau** | une teinte **en voile** (`forme`, 0,4) sur un lit de galets qu'on voit au travers, ou des reflets en traits couchés | Mëllerdall, Ourdall |
| **Sol bâti** | `solDePierre(PAVES / DALLES / GRAVIER, haut, pente, bordure)` | — |
| **Sol naturel** | `solNaturel(HERBE / TERRE / MOUSSE / BERGE, haut, pente)` | reste végétal pour les saisons |
| **Végétation** | `nappe(couleur déjà adaptée à l'heure, force, calme)` | jamais un littéral brut |
| **Lumière, lueur** | `halo(x, y, rayon, couleur, opacité)` : un vrai dégradé radial | — |

## 3. La lumière

- **La rampe** éclaire les cartons entre eux : elle porte les ombres vers le
  haut et un peu à droite, et elle est la même sur toutes les cartes. Elle ne
  se discute pas, `Paysages.ombres` s'en charge.
- **Le soleil du lieu** éclaire les matières **à l'intérieur** d'une pièce.
  Il vient d'où la photo le dit, et une seule fois par lieu : à Belval de la
  gauche, donc tout flanc droit est à l'ombre et toute arête gauche est
  claire. Deux pièces voisines éclairées de deux côtés tuent la scène.
- **Un filet, pas un bandeau.** Sur un cylindre, la lumière est une bande
  étroite à un quart du bord, pas un pan clair collé contre lui.
- **Un cylindre n'a que deux valeurs tranchées de droit** : la panse et son
  flanc à l'ombre. Une troisième bande en fait une planche.

## 4. Les pièges, tous rencontrés

- **Le cylindre en trois planches.** Un large bandeau clair au bord gauche,
  la panse au milieu, un flanc sombre à droite : trois rectangles côte à
  côte. Filet étroit à 16 % du bord, ombre douce à partir de 74 %.
- **`mix(couleur, P.pres, t)` verdit.** `P.pres` est un vert : s'en servir
  pour assombrir un flanc teinte la pierre et le métal en vert (règle 10 de
  `direction-artistique`). Pour une ombre, `sombre(col, t)` ; pour une
  matière bâtie, `neutre(duJour(…))`.
- **Un rond plat n'est pas une lueur.** Peint à faible opacité, il reste un
  disque à bord net, qu'on lit comme une pièce de papier de plus. `halo`.
- **Peindre les barres au lieu de percer.** Une charpente dessinée en traits
  sur un aplat plein fait une façade à meneaux. C'est le découpage qui fait
  la charpente : `Path.op(…, DIFFERENCE)`.
- **Le joint peint après les blocs ne se voit pas.** L'écart entre deux
  pierres montre alors leur propre couleur. Fond sombre d'abord, toujours.
- **Un aplat cerclé de quatre traits** reste un aplat : quatre cerclages sur
  un fût de brique font un poteau de bois peint.
- **Le grain est chaud.** Un blanc pur posé dessus jaunit ; pour un blanc
  franc (une chute d'eau, une écume), tirer vers le bleu.
- **Le litage seul ne sauve pas une masse unique.** Une paroi d'un bord à
  l'autre, même bien litée, se lit comme un contreplaqué : les lits courent
  sans jamais rencontrer d'arête. Il faut **étager des découpes** (la paroi
  du fond, la voûte, le pilier), chacune sa valeur, sa tranche de kraft et
  son litage à elle. Ce sont les ruptures qui creusent, pas les lignes.
- **L'arc-en-ciel.** Une grande pièce dont le bord suit à distance constante
  celui d'une ouverture fait deux bandes concentriques : sa tranche de kraft
  d'un côté, le tableau d'ombre de l'autre. Cassez la lèvre en polyligne
  anguleuse, et n'ajoutez pas de troisième cerne.
- **La tranche de kraft d'une grande pièce lisse devient une moulure.** Elle
  est juste sur une découpe de la taille d'une main ; sur un arc de soixante
  unités, c'est un jonc de caoutchouc. Découper plus petit, ou casser le bord.
- **Des blocs rectangulaires font du béton.** Un rocher se débite en
  polygones de travers, aux valeurs écartées ; d'équerre et de ton voisin,
  ils donnent des panneaux préfabriqués.
- **La lune n'est pas le soir.** `P.allume` est vrai le soir **et** la nuit :
  une flaque de lune posée dessus fait une fumée orange au couchant. Pour ce
  qui n'appartient qu'à la nuit, `P.nuit`.
- **Une ouverture rognée n'est plus une ouverture.** Les arcades du pont
  Adolphe montaient de deux dixièmes dans la bande de la corniche, posée
  après elles : leur plein cintre disparaissait et il ne restait que dix
  rectangles clairs, qu'on lisait comme des fenêtres éclairées. Vérifier
  qu'aucune pièce postérieure ne mange la tête d'un percement.
- **Le tableau d'un percement ne se peint que sur son bord.** L'épaisseur de
  l'ouvrage, à l'ombre, est ce qui dit qu'on voit au travers ; peinte sur
  tout le trou, elle le bouche et l'ouverture devient un panneau sombre. La
  pièce à poser est la **différence** entre l'ouverture et elle-même
  rentrée d'un demi, décalée du côté opposé au jour.
- **Un clocher trop large fait un toit.** Une flèche veut être à peu près
  aussi haute que deux fois sa base. Large de dix unités pour cinq de haut,
  celle de la Spuerkeess passait pour une toiture de plus.
- **Sans sol, le plancher de la scène reparaît.** Sous `DEVANT` il reste
  cinq unités que `poser(DEVANT)` ne couvre pas : il faut un
  `solNaturel` ou un `solDePierre`, sinon les lames de bois du théâtre
  s'étalent au bas de la carte.
- **Deux rideaux de la même nuance n'en font qu'un.** Un feuillage étagé ne
  se lit que par l'écart de **valeur** d'un rideau à l'autre, du clair au
  fond au sombre devant. Mêmes verts, et la vallée redevient un aplat.
- **La vignette n'a pas la gouache.** `Facture.VIGNETTE` est un aplat avec sa
  seule tranche de kraft : une matière qui ne tient que par ses coups de
  pinceau disparaît dans la grille.

## 5. Ce qu'une matière a le droit de coûter

- **Sous le seuil de `enCarton`** (opacité ≥ 0,95, surface ≥ 14, plus petit
  côté ≥ 1,2), une pièce n'a ni tranche de kraft ni ombre propre. C'est **ce
  qui rend la matière abordable** : un appareil de 200 briques coûte 200
  `rect` simples, pas 200 découpes.
- **Tout est peint une fois** dans le calque, au rendu (`Paysages.rendrePlans`),
  jamais à chaque trame. Seuls la `Vie`, les nuages et l'ambiance repassent.
- **Ordres de grandeur tenus** : une cheminée de brique ≈ 220 `rect`, une
  virole rivée ≈ 40 ronds, un pan de tôle ≈ 60 pièces. Un lieu entier reste
  sous le millier de petites pièces.
- Mesurer avant de s'inquiéter : budgets et méthode dans `carnet/CLAUDE.md`
  et la skill `mesurerQualite`.

## 6. Comment juger

Depuis `android_keyboard/`, sans appareil ni émulateur :

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:testDebugUnitTest \
  --tests "*PlancheCartesTest.lieux" -Pplanche=<dossier> -PplancheLieux=<LIEU> --rerun -q
```

Quatre planches, une par heure, chacune avec le décor seul (regard −1 et +1)
et le décor sous la plaque du mot (regard 0).

- **Agrandir ×2 sur la pièce** qu'on vient de peindre : l'appareil doit se
  compter.
- **Regarder la nuit** : c'est là que les matières se confondent.
- **Regarder la vignette** (`-PplancheAlbum`) : ce qui fait du bruit s'y voit.
- **Passer en gris** pour vérifier que l'appareil, et non la teinte, porte la
  matière.

## 7. Liste de contrôle

- [ ] Chaque matière a son appareil, et il se compte au zoom.
- [ ] Une seule source de lumière intérieure, celle de la photo.
- [ ] Aucun cylindre à trois bandes.
- [ ] Aucune ombre obtenue en mélangeant à `P.pres` ; le bâti passe par `neutre`.
- [ ] Les joints sont peints avant ce qu'ils séparent.
- [ ] Les lueurs sont des `halo`, pas des ronds.
- [ ] Ce qui est ajouré est percé, pas dessiné.
- [ ] La matière survit à la nuit et à la vignette.
- [ ] Le lieu reste sous le millier de petites pièces.
