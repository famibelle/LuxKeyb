# Les phrases d'exemple du LOD comme corpus des n-grammes — mesure du 2026-11-20

Script : `Dictionnaires/mesure_exemples_lod.py`. Il ne touche à aucun fichier de
l'application : il reconstruit en mémoire le modèle n-grammes du pipeline (mêmes
seuils, même découpage que le clavier) pour chaque variante, et les compare sur
les mêmes jeux.

**Contrôle :** la variante A reproduit les chiffres publiés de la page corpus
(ZLS : top-3 18,1 %, contexte reconnu 87,8 %, couverture 93,6 % ; ParaLux :
20,8 %). La reconstruction est donc fidèle.

## Ce qu'il y a dans le LOD

| | |
|---|---|
| Exemples bruts (`<example>`) | 58 962 |
| Écartés (VULG 8, PEJ 1) | 9 |
| Phrases distinctes | 55 555 |
| Mots | 506 020, dont 63 992 mots vedettes (`<inflectedHeadword>`) |
| Phrases de LETZ retrouvées dans le LOD | 94,8 % |
| Segments du ZLS qui sont des exemples du LOD | **5 715 sur 10 807** |
| Quasi-doublons ZLS ↔ LOD (≥ 90 % de ressemblance) | 8 |
| Recouvrement avec ParaLux | 0 |
| **Exemples utilisables à l'entraînement** (hors ZLS et quasi-doublons) | **49 834** |

LETZ n'était que 10 % du LOD : l'apport porte sur ~0,45 M de mots, contre 53 k.

## Résultats (top-3 du mot suivant, en %)

Le jeu ZLS est toujours le même : les 10 068 segments inédits de la page corpus,
séparés en deux parties.

| Variante | ZLS publié | ZLS issus du LOD (5 007) | ZLS Chambre, presse (5 061) | ParaLux | 2ᵉ personne (1 155 évts) |
|---|---|---|---|---|---|
| **A** actuel (LuxAlign + LETZ) | 18,07 | 15,13 | 19,40 | 20,90 | 14,3 |
| **B** + LOD hors ZLS, poids 1 | **19,61** | 19,46 | **19,67** | 20,79 | **25,5** |
| C LuxAlign + LOD (sans LETZ) | 19,52 | 19,24 | 19,65 | 20,75 | 25,1 |
| D + LOD pondéré ×3 | 20,27 | 22,24 | 19,37 | 20,68 | 28,1 |
| N naïf : LOD complet, ZLS inclus | *20,42* | *22,00* | 19,71 | 20,72 | *28,6* |

Écarts B − A, intervalle de confiance à 95 % (bootstrap, 1 000 tirages par phrase) :

| | top-3 | top-3 2ᵉ personne | complétion sur 2 lettres |
|---|---|---|---|
| ZLS publié | +1,44 à +1,65 | +9,4 à +13,4 | +0,55 à +0,72 |
| ZLS issus du LOD | +4,10 à +4,58 | +10,2 à +14,5 | +1,39 à +1,72 |
| **ZLS Chambre, presse** | **+0,19 à +0,36** | +1,3 à +10,1 | +0,08 à +0,28 |
| ParaLux | −0,49 à +0,30 | (4 événements) | −0,46 à +0,37 |

Autres mesures, de A à B : contexte reconnu 87,8 → 89,1 % ; couverture lexicale
(dictionnaire du corpus seul) 93,6 → 94,4 % ; contextes du modèle 27 746 → 30 929
(+11 %).

## Ce que ça veut dire

1. **Le gain est réel, et il est là où on l'attendait.** Sur la partie du ZLS
   qui n'a rien à voir avec le LOD (Chambre, presse), le gain est petit mais
   certain, +0,3 point, sans aucune régression. Sur la deuxième personne, le
   top-3 passe de 14 à 25 %. ParaLux, qui est du RTL, ne bouge pas.
2. **Le +4,3 points de la partie LOD du ZLS est flatteur.** Ces phrases sont
   écrites dans le même style que l'entraînement, comme ParaLux partage celui de
   LuxAlign. Le chiffre honnête à publier reste le ZLS complet : 18,1 → 19,6 %.
3. **Le piège de l'évaluation est confirmé.** En entraînant naïvement sur tout
   le LOD, le score monte artificiellement de 0,8 point (20,4 au lieu de 19,6),
   et le filtre de `generate_corpus_stats.py` ne garderait plus que **5 061
   segments sur 10 807**. Il faut donc retirer de l'entraînement les exemples
   présents dans le ZLS.
4. **Ne pas pondérer.** Multiplier le poids du LOD par 3 gonfle la partie LOD
   du ZLS (+7 points) mais efface le gain sur la Chambre et la presse (−0,03
   point par rapport à A) et fait baisser ParaLux. Le modèle se met à écrire
   comme un dictionnaire.
5. **Le mot illustré : à ôter du décompte du dictionnaire.** Il ne change rien
   aux n-grammes. En revanche, le compter ajoute 3 286 formes au seuil de 3 et
   double la fréquence de 33 mots sans raison linguistique (`millen` 16 → 65,
   `sau` 9 → 26, `schappeg` 0 → 10, des noms de pays). Le gain en complétion,
   +0,15 point, n'apparaît que sur les phrases de style LOD.
6. **Pistes essayées et rejetées.**
   - **Ne jamais proposer le mot illustré (B2)** : −0,07 point.
   - **Exiger qu'une suite ait été vue deux fois (A2, B3)** : −0,04 point.

   Les suggestions étranges de certains contextes rares (`an dengem` → `gulasch`,
   `fierkelsjelli`) ne viennent pas du mot illustré. Le LOD décrit des objets
   concrets, et plusieurs de ses exemples parlent du même goulasch. Ces
   suggestions n'apparaissent qu'après ces deux mots précis, et disparaissent
   dès la première lettre tapée.
7. **LETZ devient presque redondant** : 94,8 % de ses phrases sont dans le LOD.
   Le retirer (variante C) coûte 0,1 point. On peut le garder par prudence, ou
   le retirer pour simplifier les licences. Le jeu Wuertlück, qui cite LETZ sous
   ses phrases, est un sujet à part.

## Exemples de ce qui change dans le clavier

| Contexte | Avant (A) | Après (B) |
|---|---|---|
| `du` | jour, logement, travail | hues, muss, jour |
| `weess` | datt, net, dass | du, net, de |
| `weess de` | minister, georges, paul | kee, schonn, minister |
| `hues du` | gëschter, e, do | eng, e, schonn |
| `wëlls de` | (aucun) | nach, net, e |
| `kanns du` | (aucun) | mer, mir, dech |
| `weess du` | (aucun) | wéi, wou, datt |
| `looss dech` | (aucun) | net, vu, impfen |
| `ech wunnen` | (aucun) | zu, um, op |
| `mäi papp` | (aucun) | huet, ass, war |

3 185 contextes nouveaux, dont 68 à la deuxième personne. Le premier choix
change dans 1 718 contextes existants. Après `du`, le modèle actuel propose du
français (« du jour », « du travail ») venu de LuxAlign.

## Recommandation

Verser les exemples du LOD dans le pipeline, **avec un poids de 1**, en
appliquant trois règles :

1. retirer les exemples présents dans le corpus du ZLS, comparés avec
   `cle_de_comparaison`, ainsi que les quasi-doublons ;
2. ne pas compter le mot illustré dans la fréquence du dictionnaire, mais le
   garder dans les n-grammes ;
3. écarter les exemples marqués VULG, PEJ et VEREELZT (aucun exemple ne porte
   VEREELZT aujourd'hui, mais la règle protège les éditions futures).

Le LOD est en CC0 : cela ne change rien aux licences, puisque les fichiers de
dictionnaire restent soumis à la clause NonCommercial de LuxAlign.

## Reproduire

```bash
pip install datasets
HF_HUB_DISABLE_XET=1 python Dictionnaires/mesure_exemples_lod.py --sortie rapport.json
```

Environ 15 minutes. Réseau nécessaire : `data.public.lu`,
`download.data.public.lu`, `huggingface.co` et le CDN de Hugging Face
(`us.aws.cdn.hf.co`).

## Suite : branché dans le pipeline (même jour)

`LuxembourgishComplet.py` verse désormais les exemples, avec les règles
ci-dessus (code partagé : `lod_source.exemples_pour_entrainement`). Deux
différences avec la variante B mesurée plus haut :

- les phrases que LETZ apporte déjà ne comptent qu'une fois (la variante B les
  comptait deux fois) : 4 723 phrases ;
- les quasi-doublons sont cherchés contre **tout** le corpus du ZLS, et pas
  seulement contre ses segments inédits : 443 exemples écartés au lieu de 8.
  Plusieurs exemples du LOD sont des variantes d'une même phrase (« fir eng
  Bluttanalys » / « fir dës Bluttanalys »).

Restent 44 676 phrases. Sur les fichiers livrés, mesuré par
`generate_corpus_stats.py` : top-3 ZLS 18,1 → **19,5 %**, contexte reconnu
87,8 → 89,0 %, couverture 93,6 → 94,4 %, ParaLux 20,8 → 20,8 %. Banc de frappes
ParaLux (`bench_frappes.js`) : 22,5 / 37,1 / 59,6 % → 22,7 / 37,2 / 59,3 %,
dans le bruit.

## Conséquence sur les cartes : la rareté bouge (décision du 2026-11-20)

Dans le carnet, la rareté d'une carte est le rang de sa forme dans
`luxemburgish_dict.json` (`Rarete.pourRang`, seuils 3 000 / 6 500 / 9 000).
Les phrases d'exemple du LOD font monter les mots du quotidien : 1 378 des
12 000 premiers mots changent de palier. Le propriétaire a choisi d'**accepter
les nouvelles raretés** plutôt que de les figer : elles disent mieux la langue
courante (« Kaz » n'est pas un mot rare).

### Cartes imprimées (`cartes_imprimees.json`) : 39 sur 209 changent de palier

Les nombres ne bougent pas : leur rareté se lit sur leur orthographe.

- **B1** (12) : Kaz (très rare → commune), Hond (peu commune → commune), Päerd (rare → commune), iessen (peu commune → commune), sprangen (très rare → rare), léif (très rare → rare), Kuch (rare → peu commune), Kéis (très rare → peu commune), Brout (rare → peu commune), drénken (peu commune → commune), schwammen (rare → peu commune), schéin (peu commune → commune)
- **E1** (7) : Reen (peu commune → commune), Sonn (peu commune → commune), Insel (peu commune → commune), Mound (très rare → rare), kachen (très rare → peu commune), kal (peu commune → commune), waarm (peu commune → commune)
- **NA1** (9) : Kaddo (très rare → rare), Kaffi (peu commune → commune), Fleesch (peu commune → commune), gesond (peu commune → commune), Noper (peu commune → commune), Apdikt (très rare → rare), Architekt (rare → peu commune), Supermarché (peu commune → commune), brong (très rare → rare)
- **NA2** (11) : Box (peu commune → commune), Mond (peu commune → commune), Gaart (peu commune → commune), Schaf (très rare → peu commune), Schong (peu commune → commune), Toilette (rare → peu commune), modern (peu commune → commune), Duerf (peu commune → commune), Kolleeg (rare → peu commune), Marathon (rare → peu commune), Bibliothéik (rare → peu commune)

Composition des boîtes (communes / peu communes / rares / très rares) :

| Boîte | avant | après |
|---|---|---|
| B1, départ | 14 / 7 / 5 / 26 | 20 / 7 / 3 / 22 |
| E1, extension | 27 / 7 / 0 / 18 | 32 / 3 / 1 / 16 |
| NA1, niveau A1 | 26 / 9 / 4 / 13 | 31 / 5 / 6 / 10 |
| NA2, niveau A2 | 22 / 18 / 5 / 7 | 28 / 17 / 1 / 6 |

### Fait dans cette branche

- `luxemburgish_blasons.json` régénéré (`generate_blasons.py`) : les grilles
  tirent un autre vocabulaire, et seuls 47 % des lemmes jouables avaient
  encore un champ. Après : 64 % du carnet.
- `generate_chassecroise.py` garantit désormais chaque case de l'album des
  paysages (au moins 3 mots par case et par langue) : la case du soir du Haff
  Réimech était tombée à 2 mots dans les grilles portugaises. Une grille
  portugaise remplacée.
- « Smartphone » perd son dessin : plus aucun jeu ne le donne.
- Les 628 tests unitaires passent.

### Reste à faire, sur une machine qui fait tourner l'émulateur

Les rendus de cartes sont dessinés par l'application elle-même
(`scripts/exporter_rectos.sh`) ; ce conteneur n'a pas de KVM pour l'émulateur.

1. Réexporter les rectos des quatre boîtes et relancer
   `scripts/rectos_boite.py assembler` (PDF d'impression, `liste.csv`), puis
   `scripts/bat_cartes.py` pour chaque BAT. Une carte qui change de palier
   change d'heure (matin, après-midi, soir, nuit) et de cadre.
2. Revoir l'équilibre des boîtes avec ces nouveaux comptes : B1 perd quatre
   très rares, et « Kaz », sa carte n° 001, devient commune.
3. L'affiche, le tract et le triptyque montrent « Kaz » très rare au cadre
   d'or (`docs/assets/cartes/carte_kaz_wuertduell.webp`) : choisir une autre
   carte vedette, ou réexporter celle-ci.
4. Dans l'application, les cartes déjà gagnées changent de palier à la mise à
   jour : à annoncer dans les notes de version.
