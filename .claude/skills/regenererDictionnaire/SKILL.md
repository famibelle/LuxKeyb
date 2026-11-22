---
name: regenererDictionnaire
description: Régénérer ou modifier les données linguistiques du Lëtzebuergesch Clavier (LuxKeyb), c'est-à-dire le dictionnaire et les n-grammes, les formes du LOD et le filtre de Bloom, les traductions et familles, les exemples, les grilles de Kräizwuert et Wuertplaz en quatre langues, Wuertlück, les blasons et les séries du carnet, puis resynchroniser le simulateur et la page corpus du site. À utiliser dès qu'on touche à Dictionnaires/, à un script generate_*.py, à un fichier luxemburgish_*.json, au corpus, à une liste de mots (fautes du corpus, mots écartés), ou que Medhi signale un mot manquant, une glose fausse ou une grille bizarre.
---

# Régénérer les données de la langue

Tous les assets vivent dans `android_keyboard/app/src/main/assets/` et sont **écrits directement par les scripts** de `Dictionnaires/`, avec des sauvegardes horodatées dans `Dictionnaires/backups/`. Le schéma de chaque fichier (le contrat que lit le moteur) est décrit dans `CLAUDE.md`, section *Asset schema* : le relire avant de changer un format.

## 1. Avant

- Les deux corpus (LuxAlign, LETZ) sont publics : pas besoin de `HF_TOKEN`. Le LOD (180 Mo) et le corpus ZLS sont mis en cache sous `Dictionnaires/luxemburgish_data/`, hors du dépôt ; le premier lancement les télécharge.
- **Toujours `--strict`** sur les scripts qui l'acceptent. Sans lui, un corpus qui ne se charge pas donne un dictionnaire valide, de bonne taille, privé de tout son vocabulaire ou de tout son registre familier.
- Ne jamais ajouter au dictionnaire ni aux n-grammes **ParaLux** ni le **corpus ZLS** : ce sont les seuls jeux d'évaluation jamais vus. Le ZLS ne sert qu'à afficher les traductions officielles des exemples.
- Pas de Wikipédia (évaluée et rejetée : elle écrase le registre parlé), pas de conférences de presse (elles faisaient entrer de l'allemand : `und`, `wir`, `auch`).

## 2. L'ordre, celui de la CI

Depuis `Dictionnaires/`. L'ordre compte : chaque script lit ce que les précédents ont écrit.

```bash
python LuxembourgishComplet.py --strict        # dictionnaire + n-grammes (remplace, ne fusionne jamais)
python generate_lod_forms.py --strict          # formes du LOD absentes du dico + filtre de Bloom
python generate_cloze.py --strict              # Wuertlück : lit le dico et les n-grammes
python generate_translations.py --strict       # gloses fr/de/en/pt, familles, exemples, ids LOD, catégories, noms propres
for L in fr de en pt; do python generate_crossword.py --strict --langue $L & done; wait      # Kräizwuert
for L in fr de en pt; do python generate_chassecroise.py --strict --langue $L & done; wait   # Wuertplaz, après Kräizwuert
python generate_blasons.py                     # champs et meubles des cartes : lit familles, grilles, Wuertlück, traductions
python generate_series.py                      # séries du carnet : lit tout ce qui précède
```

- `generate_lod_forms.py` **soustrait le dictionnaire** du LOD et reconstruit le filtre de Bloom sur l'union : il doit tourner après chaque changement du dictionnaire, même d'un seul mot (la CI refuse les doublons entre les deux fichiers).
- **La CI régénère tout jusqu'au chassé-croisé à chaque build, mais pas les blasons ni les séries.** Or tout changement du dictionnaire rebat les grilles. Donc en local : tout relancer jusqu'au bout et **committer tous les assets ensemble**, sinon les tests d'assets (`BlasonsAssetTest`, `SeriesPaysagesAssetTest`…) échouent en CI sur un tag.
- `generate_french_dict.py` est à part : Lexique 3.83 est figé, l'asset français est suivi dans git et ne se relance que si la source change.
- `GRAINE` est fixe dans les générateurs de jeux : relancer sur le même corpus ne réécrit rien. Un diff énorme sur une grille veut dire que ses entrées ont changé.

### Modifier une liste à la main

- **Une coquille du corpus** (`hunnn`) : l'ajouter à `FAUTES_DU_CORPUS` dans `LuxembourgishComplet.py` (lu aussi par `FautesDuCorpusTest`), puis toute la chaîne.
- **Un mot à ne pas proposer** (religion, partis, registre choquant) : `MotsEcartes.kt`, côté Kotlin, appliqué au chargement ; pas besoin de régénérer. Chercher **les deux formes** de l'emprunt (`Vergewaltegung` et `Viol`), comparer sur la forme entière, jamais un préfixe (`Kierch` ≠ `Kierchbierg`), et compléter `MotsEcartesTest` des deux côtés : ce qui part et le quasi-homonyme qui reste.
- **Une orthographe de Zuelwuert** : aucun asset, tout est dans `ZuelenSpeller` ; toute forme doit être `suggest="true"` dans l'index du LOD.

## 3. Vérifier

1. Depuis `android_keyboard/` : `./gradlew testDebugUnitTest --rerun`. **Le `--rerun` est obligatoire** : les tests d'assets lisent les fichiers par `File`, Gradle ne les voit pas comme entrées et rejoue l'ancien résultat vert.
2. Les garde-fous de la CI (`build-apk.yml`, étape *Verify Generated Assets*) donnent les ordres de grandeur attendus. Les plus parlants :
   - dico : tableau `[[mot, fréquence]]`, ≥ 20 000 entrées, ≥ 15 000 capitalisées ;
   - n-grammes : ≥ 15 000 contextes, dont des clés à deux mots, ≥ 10 000 candidats capitalisés ;
   - ~300 grilles par jeu et par langue, ~100 par niveau ;
   - ≥ 15 000 formes glosées, ≥ 300 mots de 5 lettres pour Wuertriet.
   Un chiffre qui s'effondre veut presque toujours dire qu'une source n'a pas été chargée.
3. **Les constantes de score sont calibrées sur l'échelle des fréquences** (`EDIT_DISTANCE_WEIGHT`, seuils de correction). Si le maximum du dictionnaire change beaucoup, `SuggestionScoringTest.testDistanceBeatsFrequencyAtShippedDictionaryScale` le dira ; les autres tests de score, sur des fréquences codées en dur, resteront verts.
4. Mesurer l'effet sur la prédiction et la correction avant d'annoncer un gain (voir la skill **mesurerQualite**). Ne jamais publier un chiffre mesuré avec une autre tokenisation que celle du clavier.

## 4. Resynchroniser le site, à la main

La CI ne touche pas à `docs/`. Le simulateur et la page corpus gardent leurs propres copies, qui vieillissent sans bruit. Depuis la racine :

```bash
cp android_keyboard/app/src/main/assets/luxemburgish_dict.json docs/assets/
python docs/scripts/build_simulateur_ngrams.py    # simulateur-ngrams.json et simulateur-lod.json
python docs/scripts/generate_corpus_stats.py      # docs/assets/corpus_stats.json (évaluation ZLS + ParaLux)
```

Ne **pas** resynchroniser le français du simulateur : ses 662 mots sont figés volontairement pour la démonstration.

## 5. Committer

- Un seul commit pour les assets et les scripts qui les produisent, avec dans le corps les chiffres avant/après (formes, contextes, top-3 si mesuré).
- Les changements `docs/` peuvent aller dans le même commit ou directement sur `main` juste après.
- Puis, si Medhi le demande, une version (skill **publierVersion**).

## Liste de contrôle

- [ ] `--strict` partout ; aucun corpus d'évaluation dans l'entraînement.
- [ ] Toute la chaîne relancée dans l'ordre, blasons et séries compris.
- [ ] `testDebugUnitTest --rerun` vert.
- [ ] Ordres de grandeur conformes aux garde-fous de la CI.
- [ ] Site resynchronisé (dico, n-grammes du simulateur, LOD du simulateur, stats du corpus).
- [ ] Tous les assets dans le même commit.

## Annexe : la resynchronisation du site en détail

Repris tel quel de `CLAUDE.md` le 2026-10-21, quand ces recettes en ont été retirées.

**After every dictionary regeneration, resync the site by hand — CI will not.** `build-apk.yml` filters on `android_keyboard/`, `Dictionnaires/` and `.github/`, so it never touches `docs/`. The simulator and the corpus page keep their own copies, which go stale silently: the simulator would keep advertising the old word count while the app ships a new dictionary. Three commands, from the repo root:

```bash
cp android_keyboard/app/src/main/assets/luxemburgish_dict.json docs/assets/
python docs/scripts/build_simulateur_ngrams.py    # 5.1 MB → 1.4 MB, lossless ; écrit aussi simulateur-lod.json
python docs/scripts/generate_corpus_stats.py      # refreshes docs/assets/corpus_stats.json
```

The simulator reads `docs/assets/simulateur-ngrams.json`, **not** the APK's file. It is the same contexts, candidates and order, re-encoded as `{"an der": ["rue", …]}` instead of `{"an der": [{"word": "rue", "probability": 0.08}, …]}` — 1.4 MB instead of 5.1 MB for a file the browser refetches on every visit. `probability` served only a sort that the file's own order already performs, and `simulateur-engine.js` accepts both encodings so the APK's file can be dropped in for comparison. Equivalence is not assumed: it was checked over all 26 172 contexts and 7 624 typing events, zero divergence. Dropping two-word contexts that duplicate their one-word fallback was measured too — only 229 of 16 249, 10 KB, not worth it.
