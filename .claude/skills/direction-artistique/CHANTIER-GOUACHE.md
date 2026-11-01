# Chantier : la gouache vive sur carton et la plaque du mot

**Objectif : écrire dans l'appli la direction artistique validée par le propriétaire le 2026-11-01, vérifier chaque étape sur l'émulateur, et pousser.**

## 0. Avant de commencer

- Récupérer la branche, qui est à jour avec `main` :

  ```bash
  git fetch origin claude/trusting-carson-9twue3 && git checkout claude/trusting-carson-9twue3
  ```

- Lire la skill `direction-artistique`, § 2, sous-partie « La gouache vive sur carton, et la plaque ». Elle fait référence pour toutes les décisions.
- Ouvrir la maquette `.claude/skills/direction-artistique/maquette/gouache/`. Elle montre le rendu attendu, et ses fonctions sont la recette à porter en Kotlin :
  - `node capture.js` : le Breedewee aux quatre heures ;
  - `node rendre.js pancarte.html sortie.png` : la fenêtre de la carte « Bus » ;
  - `node anim.js` : l'inclinaison, image par image ;
  - fonctions à porter : `peint`, `cielPeint` et `nuagePeint` (`peint.js`), les palettes `VIVES` (`vives.js`), et `plaque` (`anim.html`).

## 1. Les quatre palettes vives

Dans `carnet/Paysages.kt`, classe `Heure`, tableau `BASE` : reprendre les valeurs de `VIVES` (`maquette/gouache/vives.js`).
- Les nacres et les encres (`NACRES`, `ENCRES`) ne changent pas.
- La teinte du champ (`Heure.de`) s'applique comme avant.
- Vérifier que les quatre heures restent distinctes au premier coup d'œil, l'après-midi et le soir surtout.

## 2. Le rendu gouache des cartons

Dans `Paysages.rendrePlans` et le `Peintre`, chaque grande pièce devient un carton peint :
1. **le carton kraft d'abord**, avec son ombre ;
2. **la gouache**, un peu mince (alpha d'environ 0,84) ;
3. **des coups de pinceau dans le sens de la forme** : horizontaux pour le ciel et l'eau, courts et obliques pour la pierre, ronds pour le feuillage ;
4. **un bord sec** où le kraft reparaît.

Règles :
- **Le ciel** est peint à larges touches, et l'astre se peint après, d'un seul aplat.
- **Les petits détails** (fenêtres, joints, lanternes, traits fins) restent des aplats nets.
- **Une forme faite de plusieurs ronds** (un feuillage) reçoit son bord de carton à l'extérieur seulement : voir `union` dans `peint.js`.
- **La tranche brune du carton remplace la bordure blanche** actuelle (décision validée).
- **Les performances.** Ne pas tracer des centaines de touches une par une sur le Galaxy A21s. Préparer quelques textures de pinceau, en tuiles, et les poser dans le sens de chaque pièce (`BitmapShader` avec une matrice tournée). Tout se peint une seule fois au rendu des calques.
- **La vignette** garde des aplats simples, avec seulement le bord de carton.
- **Les nuages de carton** flottent dans la bande du haut.

## 2 bis. Cinq plans pour toutes les cartes, et les nuages qui traversent

Décision du propriétaire : la profondeur ne dépend plus de la rareté. Voir la skill, § 4, règle 8 ; la maquette est `node anim.js cinq_plans.html images`.

- **`nombreDePlans` vaut 5 pour toutes les cartes.** Les plans viennent du lieu lui-même :

  | Plan | Contenu | Glissement (maquette) |
  |---|---|---|
  | 1. Le ciel | la toile peinte, l'astre, les étoiles | 5 % |
  | 2. Les nuages | les nuages de carton | 4 % |
  | 3. Le lointain | crête, ville au loin, tours | 3 % |
  | 4. Le milieu | collines, façades, bâti | 1,7 % |
  | 5. Le proche | le premier plan, posé sur le plancher | 0,7 % |

- **Séparer le ciel du lointain.** Aujourd'hui, le ciel et la crête partagent le calque 0.
- **Les rares et les très rares gardent leurs coulisses** (décision du propriétaire). Elles forment un sixième carton sur les côtés, devant les cinq autres, qui glisse de 0,3 % et porte son ombre sur eux. Les coulisses actuelles sont conservées : arbres, et au Breedewee piliers et pignons.
- **Les nuages traversent le ciel de toutes les cartes** : c'est le nuage des très rares d'aujourd'hui (`Paysages.nuage`, `CadreOrne.peindreNuage`, `PERIODE_NUAGE`), généralisé.
  - Leur ombre est peinte sur leur propre carton, pour voyager avec eux.
  - Aucun autre carton ne leur fait d'ombre.
  - Les cartes de fête, rendues à plat, n'en ont pas.
- **Le coût à l'ouverture.** Chaque plan ombre ceux de derrière : on passe de 3 à environ 10 ombres, calculées une seule fois. À mesurer sur le Galaxy A21s. Si c'est trop lent, limiter l'ombre au seul plan de derrière.

## 3. La plaque du mot

Dans `carnet/CadreOrne.kt`, classe `Enluminure` (`Sujet`). Le rendu de référence est `anim.html`, fonction `plaque`.
- **Une plaque épaisse et fixe.** La tranche d'environ 1,3 unité est vue en bas à droite, plus sombre vers l'arrière, avec une ombre portée sur le décor. Rien ne bouge : ni balancement, ni épaisseur qui change de côté.
- **La face** :

  | Rareté | Face |
  |---|---|
  | Commune | nacre teintée acier |
  | Peu commune | nacre teintée bronze |
  | Rare | argent plein |
  | Très rare | or plein |

  Le **bord de la face** est dans le métal du palier (`Ornement.metal`).
- **Le pied** : une tige fine (environ 1,6 unité) et un socle rond, dans le métal du palier, posés sur le plancher avec leur ombre. L'ancienne ombre ovale sous le dessin disparaît.
- **Seule la lumière bouge à l'inclinaison** (`Inclinaison.kt`) :
  - un reflet en bande traverse la face, plus vif sur le métal plein ;
  - le chanfrein du bord s'éclaire du côté de la source, qui tourne avec le geste ;
  - un éclat glisse sur la tige et sur le socle.

  Le reste de la plaque se peint une seule fois. Il faut respecter les animations coupées : dans ce cas, le reflet reste au repos.
- **La hauteur du socle** se juge sur de vrais dessins : un dessin haut (Bam, Kand) et un dessin bas (Bus, Auto).

## 4. Les lieux qui manquent de couleur

- Lieu par lieu, ajouter de la couleur dans la bande du haut et sur les bords, jamais derrière le dessin (boîte de 17 à 83 sur 12,5 à 58,5).
- **Le Breedewee** : reprendre `maquette/gouache/breedewee.js`, avec les façades aux couleurs du Grund, le lierre vert et la porte élargie. La taille de la porte reste à revoir avec le propriétaire.

## 5. Les saisons

`Habillage` reconnaît la végétation à sa distance aux couleurs `milieu`, `pres` et `loin` de l'heure. Avec les nouvelles palettes :
- relancer `.claude/skills/direction-artistique/maquette/vegetal.py` (en l'adaptant aux nouvelles palettes) ;
- vérifier sur l'émulateur que la pierre, les façades et les plaques ne fleurissent pas au printemps et ne roussissent pas en automne.

## 5 bis. Les ambiances de saison, en papier peint

Toutes sont conservées : neige, pétales, aigrettes, lucioles, feuilles, oiseaux, et les ambiances des fêtes. Le code est dans `carnet/Ambiance.kt` ; la maquette est `node anim.js automne.html images`, la carte « Bus » gagnée en automne.

- **Ce qui est matière devient du papier peint à la gouache**, dans les couleurs vives :
  - feuilles, pétales, flocons, aigrettes et confettis ;
  - chacun avec un liseré de kraft, une nervure ou un trait, et une petite ombre ;
  - concrètement, `Tournoyants.Style` et les sprites.
- **Ce qui est lumière reste de la lumière** : lucioles, braises, lumières, feux d'artifice.
- **Les oiseaux montent sur le carton des nuages** (plan 2) et glissent avec lui quand on penche.
- **Le ciel ne doit pas s'agiter.** Une carte d'été en plein jour a déjà ses nuages, ses oiseaux et ses aigrettes : moins d'oiseaux, et un jugement sur l'émulateur.
- **La place ne change pas** : devant le décor, derrière la plaque, sur la carte ouverte seulement, et coupée avec les animations du système.

## 6. Vérifications avant chaque push

- `./gradlew testDebugUnitTest assembleDebug` au vert.
- Sur l'émulateur, avec un carnet de test (méthode de `scripts/rectos_boite.py carnet`) :
  - les quatre raretés, en carte ouverte au repos et penchée, plus la vignette ;
  - plusieurs lieux, dont le Breedewee (cartes à provenance `pp`) ;
  - les quatre saisons, et une carte de fête (qui reste à plat) ;
  - les animations coupées.
- Le temps d'ouverture d'une carte, en build release, à comparer avec avant.
- Les rectos des quatre boîtes à réexporter à la fin (`scripts/exporter_rectos.sh`, puis `scripts/rectos_boite.py assembler`).

## 7. Retour attendu

- Des captures de chaque étape (quatre heures, plaque au repos et penchée, vignettes, Breedewee).
- Les temps mesurés.
- Les commits poussés sur `claude/trusting-carson-9twue3`, un par étape, dans l'ordre 1 à 6.
