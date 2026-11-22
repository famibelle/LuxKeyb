---
name: mesurerQualite
description: Mesurer la qualité ou la rapidité du Lëtzebuergesch Clavier (LuxKeyb), c'est-à-dire la prédiction (top-3, couverture, économie de frappes), la correction des fautes, les soulignements abusifs, le temps par frappe sur le A21s, la fluidité du carnet et des cartes (images par seconde, gels). Fixe quels corpus d'évaluation utiliser, quels bancs lancer, comment ne pas se tromper de chiffre et comment publier un résultat. À utiliser avant d'annoncer un gain ou une perte, après un changement du moteur de suggestions ou du dictionnaire, ou quand Medhi demande « est-ce que c'est mieux ? », « combien ? », « c'est lent ».
---

# Mesurer

Un changement n'est « mieux » que s'il a été mesuré avant **et** après, sur le même banc, avec la même tokenisation que l'application. Les tests unitaires ne mesurent pas la qualité : un dictionnaire bien formé et de la bonne taille peut être mal classé avec une suite toute verte.

## 1. Sur quel texte

- **Le corpus du ZLS est le jeu principal** (*Méisproochegen Iwwersetzungskorpus*, CC0, `Dictionnaires/zls_source.py`) : environ 10 000 segments absents de l'entraînement, 135 000 frappes, ±0,1 point de bruit. Il porte aussi les traductions fr/de/en, ce qui permet de comparer les langues sur le même contenu.
- **ParaLux** à côté (`docs/scripts/export_paralux.py`) : 312 phrases seulement (±0,8 point). Ne garder que `anchor` et `paraphrase`, jamais `not_paraphrase`, faites exprès fausses.
- **Jamais un découpage aléatoire du corpus d'entraînement** : mêmes articles, même période, même registre ; il flatte le modèle (23,9 % contre 18,8 %).
- **Ne jamais faire entrer ParaLux ni le ZLS dans l'entraînement**, même pour « gagner un peu » : ce sont les seuls textes jamais vus. Rien de ParaLux n'est redistribué.
- **Tokeniser comme le clavier** : lettres seulement (`Character.isLetter`), l'apostrophe coupe, les mots d'une lettre comptent (`d`, `a`, `e`). Un banc qui filtre `{2,}` mesure un modèle que l'application n'exécute pas : c'est arrivé, et un gain de 3,7 points avait l'air d'une perte de 1,1.
- Comparer en **pliant la casse des deux côtés** (casse canonique du dictionnaire contre casse du texte).

## 2. Les bancs

| Question | Banc | Commande |
|---|---|---|
| Le bon mot est-il dans la barre ? (top-1/3, couverture, contexte reconnu) | `generate_corpus_stats.py`, sur les **assets livrés**, ZLS et ParaLux | `python docs/scripts/generate_corpus_stats.py` (écrit `docs/assets/corpus_stats.json`, donc le chiffre du site) |
| Même chose, en CI | `bench_frappes.js` (dictionnaire du corpus seul ; le niveau LOD ne change pas le top-3) | `node docs/scripts/bench_frappes.js <paralux.json>` |
| Combien de touches évitées (KSR), soulignements abusifs par langue, corrections indues | `bench_qualite.py` (rejoue les règles du moteur, ne l'exécute pas : relire les constantes en tête s'il a changé) | `python3 docs/scripts/bench_qualite.py` |
| La correction des fautes (voisine, manquante, en trop, inversion, accent, doublement, mots collés, mot correct qui doit rester premier) | `BancCorrectionTest`, le **vrai moteur** sur les phrases ZLS | voir ci-dessous |

```bash
cd android_keyboard
BANC_CORRECTION=1 ./gradlew testDebugUnitTest --tests "*BancCorrectionTest" --rerun
# résultat : app/build/banc-correction.md
# BANC_RATIOS="off,16,64" : un tableau par seuil ; BANC_DOUBLEMENT=9 : autre facteur ; BANC_DETAILS=<famille> : les ratés
```

- **`--rerun` est obligatoire** : le cache de Gradle ignore les variables `BANC_*` et rejoue un ancien résultat en silence.
- Le banc est ignoré dans la suite normale (`assumeTrue`), ne pas le rendre obligatoire.
- `BancEfficaciteTest.kt` (s'il traîne non suivi dans le dépôt) appartient à une autre session : ne pas le committer ni le modifier.
- Lire **toutes les familles** du tableau, pas seulement celle qu'on visait : un réglage qui gagne sur les fautes peut faire perdre la complétion en cours de mot (c'est ce qui a fixé `ratioCorrectionSurPrefixe = 64` et refusé le découpage des mots collés quand une complétion existe).

### Références (pour savoir si un chiffre est plausible)

Au 2026-10-14 : top-3 ZLS 18,1 %, ParaLux 20,8 %. Correction, top-1 / top-3 : touche voisine 81,2 / 85,2 %, inversion 77,4 / 85,5, lettre en trop 91,7 / 96,0, lettre doublée 78,1 / 83,2, accent 90,3 / 98,4, mots collés 71,9 / 76,6, mot correct gardé premier ~92 %. Les valeurs à jour sont dans `CLAUDE.md` et `docs/assets/corpus_stats.json`.

## 3. La rapidité, sur le A21s

L'émulateur ne mesure rien de transposable (logiciel, ~50× plus lent pour certains calculs). **On mesure sur le A21s, en build release** : un build debug est environ 3× plus lent, ne jamais citer ses chiffres.

- **Build release de mesure** : signé par une clé jetable passée en variables d'environnement (`KEYSTORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). Il faut désinstaller la version présente (signature différente). Les `Log` sont retirés en release : sonder avec `println("CHRONO …")` et lire `logcat --pid`.
- **Temps par frappe** : frappes cadencées dans le champ d'essai de l'accueil, temps de la frappe à la barre calculée ; donner médiane, p90 et max (repère 37.3.0 : 7,5 / 20 / 25 ms). Le Wierderbuch referme le clavier, il ne sert pas pour ça.
- **Fluidité** (carnet, cartes, pochette) : une **sonde Choreographer** dans un `src/debug` temporaire (compteur d'images, journal `SONDE`), et au besoin `Debug.startMethodTracingSampling` déclenché par un fichier posé avec `run-as`. **Ne jamais committer `src/debug`.** Une activité de banc temporaire (`BancPochetteActivity`, `BancCartesActivity`) peut ouvrir directement l'écran à mesurer, à supprimer après usage.
- **Pièges** :
  - le film par wifi exagère les accrocs (gigue du transport) ;
  - le « Janky » de `gfxinfo` est bimodal d'un lancement à l'autre, et `framestats` ne rend rien d'exploitable sur ce Samsung ;
  - `input tap` met environ 0,5 s à partir : chronométrer côté appli ;
  - le premier lancement après une installation est lent (compilation) : ne pas le compter, sauf si c'est lui qu'on mesure ;
  - le shell du téléphone calcule sur 32 bits.
- Toujours tester **appui, relâcher et glissé** après un changement de rendu de la carte ouverte (une régression des couches n'apparaissait qu'après un appui).
- Les règles du téléphone (premier plan, wifi, clavier par défaut) sont dans la skill **testerSurAppareil**.

## 4. Publier un chiffre

- Dire **sur quoi** (ZLS ou ParaLux, combien de frappes ou de phrases), **avant / après**, et **ce qui ne bouge pas**. Un gain sur une famille avec une perte ailleurs se dit.
- Un chiffre ancien mesuré autrement (tokenisation, corpus, assets) n'est **pas comparable** : le dire plutôt que le mettre côte à côte.
- Ce qui n'a pas été mesuré reste « non mesuré », jamais déduit.
- Le chiffre du site vient de `generate_corpus_stats.py` sur les assets livrés : le relancer après chaque régénération (skill **regenererDictionnaire**).
- Consigner les nouveaux repères dans `CLAUDE.md` (local) avec leur date.

## Liste de contrôle

- [ ] Banc lancé avant et après, même corpus, même tokenisation que le clavier.
- [ ] `--rerun` pour tout banc Gradle.
- [ ] Toutes les familles lues, pas seulement la visée.
- [ ] Vitesse : A21s, build release, chiffres en médiane / p90 / max.
- [ ] `src/debug` et activités de banc retirés avant tout commit.
- [ ] Résultat annoncé avec son corpus, sa taille et ce qui ne bouge pas.

## Annexe : tokenisation, ZLS et ParaLux en détail

Repris tel quel de `CLAUDE.md` le 2026-10-21, quand ces recettes en ont été retirées.

**The keyboard, the pipeline and the two benchmarks must tokenise alike, and they did not.** `InputProcessor.isWordCharacter` is `Character.isLetter()`, so the app splits on the apostrophe and counts single letters. `generate_corpus_stats.py` filtered `>= 2` in **two** places (the regex *and* `decouper()`), and `bench_frappes.js` used `[\p{L}\-']{2,}`, which additionally kept `'` inside words. Both were aligned on 2026-09-04. Until then they measured a model the application never runs — and the misalignment made the single-letter fix *look* like a 1.1-point regression when it is a 3.7-point gain. **The published 18.8 % predates this and is not comparable to 20.8 %.**

**The main evaluation set is the ZLS translation corpus, not ParaLux** (since 2026-09-04). `Dictionnaires/zls_source.py` pulls the *Méisproochegen Iwwersetzungskorpus* (CC0, 10 807 segments, ~153 600 words, professionally translated to FR/DE/EN, orthographically standardised) from data.public.lu, and `generate_corpus_stats.py` evaluates on the 10 038 segments that are **not** in LuxAlign + LETZ (6.84 % overlap): **135 343 typing events against ParaLux's 2 703**, so ±0.1 point of noise instead of ±0.8. Both are measured side by side and they agree within one point on coverage and context recognition; the top-3 differs (18.1 % ZLS vs 20.8 % ParaLux) in the expected direction, ParaLux sharing LuxAlign's RTL source and register. **Do not add this corpus to training**: measured, it buys 1 719 mostly thematic forms and +0.1 point of top-3, and it would destroy the only large unseen set the project has. The reasoning is recorded beside `CORPUS_SOURCES`.

**Measuring prediction quality: use `fredxlpy/ParaLux`, never a random split.** A held-out slice of the training corpus comes from the same RTL articles, period and register as what stays in, and flatters the model — it reports 23.9 % top-3 where ParaLux reports **18.8 %**. ParaLux shares LuxAlign's RTL.lu source and its paper, yet **none of its sentences appear in LuxAlign**, which makes it the only genuinely unseen set available. Two traps: keep only `anchor` and `paraphrase` (`not_paraphrase` holds sentences deliberately falsified for the original benchmark), and do not read `312 examples × 2 columns` as 624 sentences — it is 156 mutual pairs, so 312 distinct sentences, and that is the whole set. It is an evaluation set only: never feed it to the dictionary, and nothing from it is redistributed. `docs/scripts/generate_corpus_stats.py` runs it against the **shipped assets**, so the published figure is the one users get.
