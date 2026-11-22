---
name: vegetation-automne
description: La végétation et les pelouses des cartes d'automne LuxKeyb, de jour comme de nuit — pourquoi la rouille posée par l'habillage fait une « tenue de camouflage », comment peindre une haie, un massif ou une pelouse dans leurs couleurs d'automne feuille par feuille, les soustraire à la rouille sans casser les autres saisons, garder les feuilles lisibles le soir et la nuit, semer les feuilles tombées, et vérifier aux quatre heures. À utiliser quand on regarde ou retouche une carte d'automne (Hierscht) dans `carnet/Paysages.kt` ou `carnet/Habillage.kt`, quand le propriétaire dit qu'un feuillage d'automne fait camouflage, taches ou purée, qu'une herbe reste verte à côté d'arbres roux, ou qu'on ne voit plus les feuilles la nuit. Complète `matieres-du-decor` (de quoi une surface est faite) et `direction-artistique` (la ligne).
---

# La végétation à l'automne

## Qui tu es

Tu reprends, lieu par lieu, ce qui a été appris sur la charmille et la
pelouse d'Ansebuerg le 2026-11-22 (commits `b79f4a2c`, `7030619f`,
`9950beb8`). Le propriétaire avait entouré les massifs d'automne : « une
sorte de tenue de camouflage ». La cause n'est pas propre à Ansebuerg, elle
est dans `Habillage` : **toute** végétation peinte d'un aplat vert la subit.

Ta manière de travailler :

- **Tu regardes l'automne aux quatre heures**, jamais le seul matin : chaque
  heure trompe à sa façon (§ 2).
- **Tu ne touches pas aux autres saisons.** Ce que tu changes ne vaut que
  pour `Saison.HIERSCHT`, et tu le prouves au pixel près (§ 6).
- **Tu montres, puis tu commits et tu pousses à chaque étape**, comme le
  propriétaire l'a demandé pour la revue des paysages.

## 0. Ce qui est réglé pour toutes les cartes (2026-11-22, `a4d12ac8`)

La revue des 27 lieux a montré le même défaut partout. Il est corrigé à la
source, une fois pour toutes ; le travail par lieu ne consiste plus qu'à
**dire ce qui est de l'herbe et ce qui est persistant**.

| Outil | Où | Effet |
|---|---|---|
| **Rampe de valeur** | `Habillage.fond`, branche `HIERSCHT`, rampes `ROUX`, `OR`, `HERBE` | chaque pixel de végétation garde sa valeur et prend la teinte d'automne de cette valeur (brun, roux, or) : le dessin reste lisible, plus de taches. Le roux et l'or se partagent la scène en dégradés de vingt unités (`tache = max(24, 96u)`). Force 0,76 de jour, 0,58 le soir, 0,5 la nuit |
| **`verdure(col)`** | `Peintre.relever` | à l'automne seulement, un vert franc (g > 1,08 r et g > 1,15 b) compte comme végétation : les prés et buissons qui restaient verts l'après-midi et le soir roussissent |
| **`herbe { }`** | `Peintre` | pré, pelouse, rive, mousse : relevé à l'alpha `HERBE_RELEVE` (0xA0), lu par `Habillage` comme herbe, qui passe à l'olive et à la paille au lieu de roussir. Ne marque qu'à l'automne : les autres saisons gardent leur relevé au pixel près. `solNaturel(HERBE)` l'applique de lui-même |
| **`persistant { }`** | `Peintre` | épicéa, if, cyprès, buis : `mineral` à l'automne (garde son vert), inchangé aux autres saisons |

**Pourquoi le lieu doit marquer l'herbe** : le pré et l'arbre ont
exactement le même vert (`vert()`), mesuré à Rout Bréck : (103, 174, 55)
pour le pré, (78, 149, 28) pour le feuillage, même rapport de teinte. Un
tri par la couleur a été essayé et ne sépare rien.

**Essais écartés** : des zones restées vertes parmi les rousses
(« vert tardif ») refaisaient, de près, des taches de camouflage ; une
force de 0,82 avec des zones de dix unités faisait un aplat orange.

**Marqués à ce jour** : herbe à Conzefenn (prairie, pré du devant),
Musel (pelouse de la rive), Ourdall (rive et ses touffes), Felsmillen
(mousse), Mëllerdall (mousse du pont et des vires), Bäerdref (touffes de
mousse) ; persistants à Conzefenn (épicéas) et Ansebuerg (ifs).

## 1. D'où venait le camouflage (avant `a4d12ac8`)

`Habillage.fond` roussissait l'automne pixel par pixel, **après** le décor :

| Ce qu'il fait | Valeur | Conséquence |
|---|---|---|
| Tache de bruit lissé | `tache = max(3, 12u)`, soit 2,5 unités de la boîte | bien plus large qu'une feuille : la tache ne suit pas le dessin |
| Mélange vers la rouille | `t = 0,38 à 0,58`, quatre roux | le vert reste à moitié : vert, olive, orange, rouge mêlés, c'est le camouflage |
| Force réduite le soir et la nuit | `forceRouille = 0,55` dès `P.rang >= 2` | le soir, encore moins de roux, plus de vert sale |
| Ne touche que la végétation | le relevé (`releve`) dressé par le `Peintre` | une pièce compte si sa couleur, avant la gouache, est à moins de 30 des segments de l'heure (`Habillage.vegetal`) |

D'où deux défauts qui se cachent l'un l'autre :

1. **Le camouflage**, sur toute pièce reconnue comme végétale : un aplat vert
   vire en taches. Un feuillage dessiné feuille par feuille le subit aussi,
   et son dessin se noie sous les taches.
2. **Le vert d'été qui reste.** Une herbe dont la couleur sort des segments
   de l'heure n'est pas reconnue : elle échappe à la rouille et reste verte
   à côté d'arbres roux. À Ansebuerg, la pelouse (`nappe(duJour(0xFFA9C566), 0.5f)`)
   rouillait le matin et la nuit, et restait verte l'après-midi et le soir.

## 2. Le principe

**Ce qui change de matière à l'automne est peint par le lieu, dans ses
couleurs d'automne, et soustrait à la rouille.** `Habillage` garde tout le
reste (collines lointaines, fond de forêt, feuilles qui volent par-dessus).

- Le `Peintre` connaît la saison depuis le 2026-11-22 : `saison: Saison?`,
  passée par `dessiner(…, saison = …)` depuis le rendu à plat et les cartons.
  `null` hors saison (et sur la vignette sans souvenir).
- **Soustraire à la rouille** : peindre la pièce de base dans `mineral { }`,
  qui efface le relevé sous elle. Les `forme` à opacité < 0,95 n'écrivent
  jamais dans le relevé : seule la pièce de base décide.
- **Seulement à l'automne.** Au printemps et en hiver, la pièce doit rester
  coupée dans un **vert reconnu**, sans `mineral` : c'est ce qui la fleurit
  et la blanchit. Découper la base dans un ton sombre non végétal (même
  « pour la profondeur ») lui retire toutes les saisons d'un coup : le sombre
  se pose en **voile** (`forme(massif, creux, 0.85f)`) dans le clip.

```kotlin
val automne = saison == Saison.HIERSCHT
val haie = if (automne) duJour(0xFF9A5A22.toInt()) else vert(0.1f)
// …
if (automne) mineral { piece(massif, haie) } else piece(massif, haie)
```

## 3. Un feuillage d'automne (haie, massif, buisson)

La recette est celle de la charmille (`ansebuerg()`, bloc « La charmille
taillée ») : un bord découpé en feuilles, puis des bouquets de six feuilles
en quinconce, triés en tons par la lumière.

| Ton | Été | Automne (jour) |
|---|---|---|
| Base (la pièce) | `vert(0.1f)` | `duJour(0xFF9A5A22)` |
| Creux, en voile 0,85 | `sombre(haie, 0.42f)` | `sombre(duJour(0xFF7A4020), 0.38f)` |
| Feuillage | `mix(haie, vert(0.45f), 0.5f)` | roux `duJour(0xFFC0702A)` |
| Bouquets déjà dorés | (le feuillage) | `duJour(0xFFD29A38)` |
| Au jour | `mix(vert(0.7f), duJour(0xFFD8E89A), 0.28f)` | `mix(duJour(0xFFEBB848), duJour(0xFFF6DC8A), 0.3f)` |

- **Toutes les feuilles ne tournent pas ensemble** : un bouquet entier est
  doré ou roux, choisi par un champ **lent et sans tirage** :
  `sin(cx*0.31f - cy*0.47f + graine) + 0.35f*sin(cx*0.9f + cy*0.4f) > 0.25f`.
  La variation suit les bouquets, donc les feuilles : c'est un accident, pas
  une tache. Ne jamais tirer ces zones au sort par pixel ou par grosse case,
  on refait le camouflage.
- **Aucun `r()` de plus dans le chemin commun**, sinon la suite des tirages
  change et l'été bouge aussi. Un tirage propre à l'automne prend son
  propre `Alea(graine)`.
- Ces tons sont voisins de ceux des branches du surcadrage (`feuilles()` :
  `0xFFB4501E`, `0xFFE39A2E`) et des herbes du pied (`tonsDuPied()`) : une
  carte d'automne garde une seule famille de roux.

## 4. Une pelouse d'automne

```kotlin
if (saison != Saison.HIERSCHT) pieceD(gazon, nappe(duJour(0xFFA9C566.toInt()), 0.5f), 0.4f)
else {
    val herbe = duJour(0xFF9E9C5E.toInt()).let { mix(it, neutre(it), 0.3f) }
    mineral { pieceD(gazon, herbe, 0.4f) }
    // puis les feuilles tombées, voir ci-dessous
}
```

- **Une herbe passée, tirée vers le gris.** Pure (`duJour(0xFFA6A65C)`),
  l'heure la rendait vert acide le matin.
- **Les feuilles tombées disent l'automne mieux que la couleur.** Deux cents
  amandes, trois tons (roux, or, brun `0xFF8E4A22`), un `Alea` à elles :
  - six sur dix **au pied des massifs**, le reste semé partout ;
  - **plus petites vers le fond** : `e = 0.55 + 0.6 × profondeur` ;
  - longueur `(1.5 à 2.3) × e`, largeur `0.24 × l`. À `(0.8 à 1.3) × e`,
    on ne les voyait plus du tout à la taille de la carte ;
  - chacune son ombre, la même amande décalée de `(0.15, 0.22) × e`, en
    `sombre(herbe, 0.45f)` à 0,45.
- La pelouse se peint avant les massifs (plan du milieu) : la moitié des
  feuilles du pied finit sous la haie, c'est voulu, elles en sortent.

## 5. Le soir et la nuit

- **Le soir** passe bien par `duJour` seul : la haie est plus rouge et plus
  sombre, mais les trois valeurs restent écartées.
- **La nuit**, `duJour` ramène les trois tons vers le bleu de l'heure et la
  haie devient une masse violette où plus aucune feuille ne se lit. Les
  feuilles **au jour** prennent alors un or de lune **sans passer par
  l'heure** :
  ```kotlin
  if (P.nuit) mix(duJour(0xFFEBB848.toInt()), 0xFFC8B48A.toInt(), 0.45f)
  ```
  C'est le seul ton à traiter : creux et feuillage restent à l'heure, ce
  sont eux qui font la nuit.
- `P.nuit` et non `P.allume` : `allume` est vrai le soir aussi (piège déjà
  noté dans `matieres-du-decor`).

## 6. Comment vérifier

```bash
bash .claude/skills/vegetation-automne/planche_automne.sh <scratchpad>/avant LIEU
# … la retouche …
bash .claude/skills/vegetation-automne/planche_automne.sh <scratchpad>/apres LIEU <scratchpad>/avant
```

Le script rend les quatre saisons aux quatre heures (`-PplancheRarete=TOUTES`
sur `PlancheCartesTest.saisons`), écrit `automne_heures.png` (2 × 2 : matin,
après-midi / soir, nuit) et `zoom_x2.png`, et, avec un rendu de référence,
compte les octets changés au printemps, en été et en hiver.

1. **Avant de toucher**, rendre et regarder l'automne aux quatre heures :
   noter où il y a camouflage (matin, nuit, en général) et où il reste du
   vert d'été (après-midi, soir, en général).
2. **Après**, regarder aux quatre heures, puis le zoom : les feuilles se
   comptent-elles ? Les feuilles tombées se voient-elles ?
3. **Printemps, été, hiver à 0** octet changé. Sinon, un `r()` s'est glissé
   dans le chemin commun ou une couleur de base a bougé.
4. `./gradlew testDebugUnitTest`, montrer (`SendUserFile`), puis commit et
   push, un commit par étape (`fix(carnet): …`).

## 7. Les pièges, tous rencontrés

- **Le camouflage** : laisser une haie dessinée sous la rouille. Le dessin
  se noie sous des taches plus grandes que les feuilles.
- **Les saisons perdues** : découper la base dans un sombre non végétal.
  La haie ne fleurit plus, ne roussit plus, ne blanchit plus.
- **L'herbe verte à côté d'arbres roux** : une couleur qui sort des segments
  de l'heure échappe à la rouille selon l'heure. Ne jamais conclure sur le
  matin seul.
- **Le vert acide** : un jaune-vert pur sous `duJour` le matin. Le griser.
- **Les feuilles invisibles** : sous une unité et demie, une feuille tombée
  ne se voit pas à la taille de la carte.
- **La masse violette** : la nuit, sans or de lune, plus rien ne se lit.
- **L'été qui bouge** : un tirage ajouté dans le chemin commun.

## 8. Ce qui reste, et quand factoriser

- **Pour un lieu nouveau ou retouché** : marquer ses prés et mousses en
  `herbe { }`, ses conifères en `persistant { }`, puis vérifier aux quatre
  heures. Une lisière (`lisiere`) qui descend jusqu'au bas de la fenêtre
  est un bois, pas un pré : elle roussit (Rout Bréck).
- **Le sol du plancher** (`solNaturel` posé sur le devant de la scène)
  n'est pas habillé du tout : il reste vert à l'automne, ce qui est juste
  pour une herbe rase au Luxembourg.
- **Les crêtes lointaines** restent parfois vertes le matin (Esch-Sauer,
  Stauséi) : perspective aérienne, laissé tel quel.
- La charmille et la pelouse d'Ansebuerg gardent leur recette sur mesure
  (§ 3 et 4), antérieure à la rampe : elle reste la référence pour un
  feuillage au premier plan qui doit se lire feuille à feuille.
- **Factoriser au deuxième lieu**, comme `bossage` l'a été au pont Adolphe :
  dès qu'un autre lieu a besoin d'un feuillage ou d'une pelouse d'automne,
  sortir la recette d'Ansebuerg en fonctions du `Peintre` (par exemple
  `feuillage(massif, graine)` et `pelouse(gazon, pied, graine)`, qui lisent
  `saison` eux-mêmes), refaire Ansebuerg dessus et vérifier qu'il ne bouge
  pas, puis ajouter la recette au tableau de `matieres-du-decor`.
- **Coût** : la charmille et la pelouse ajoutent quelques centaines
  d'amandes en `forme` sous le seuil `enCarton`, peintes une fois au rendu.
  Rester sous le millier de petites pièces par lieu.

## Liste de contrôle

- [ ] Automne regardé aux quatre heures, avant et après.
- [ ] Prés et mousses en `herbe { }`, conifères en `persistant { }`.
- [ ] Ce qui est retouché est dans `if (saison == Saison.HIERSCHT)`, base en `mineral { }`.
- [ ] Hors automne, la base reste coupée dans un vert reconnu.
- [ ] Tons dorés et roux choisis par bouquet, champ lent, sans tirage.
- [ ] Pelouse grisée, feuilles tombées visibles, plus serrées au pied, plus petites au fond.
- [ ] La nuit, les feuilles au jour gardent leur or de lune.
- [ ] Printemps, été, hiver : 0 octet changé.
- [ ] Tests verts ; montré, puis commit et push.
