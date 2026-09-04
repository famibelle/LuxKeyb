# Corpus sources et attribution

Le dictionnaire, le modèle n-grammes, le jeu de phrases à trous et la table de
traductions livrés dans l'application
(`android_keyboard/app/src/main/assets/luxemburgish_dict.json`,
`luxemburgish_ngrams.json`, `luxemburgish_cloze.json` et
`luxemburgish_translations.json`) sont dérivés de trois jeux de données
publics.
Deux d'entre eux sont sous licence Creative Commons et **exigent la citation de
leurs auteurs**. Cette page remplit cette obligation ; les crédits sont
également affichés dans l'application, onglet « À propos », carte « Sources ».

## LuxAlign

Phrases d'articles de presse publiés par **RTL.lu**, appariées à leurs
équivalents anglais et français.

- Dépôt : <https://huggingface.co/datasets/fredxlpy/LuxAlign> (version `v3`)
- Auteurs : Fred Philippy, Siwen Guo, Jacques Klein, Tegawendé F. Bissyandé
- Licence : **CC BY-NC 4.0** — attribution obligatoire, usage non commercial
- Apport : 180 342 phrases, ~3,1 M de mots, prose suivie de 17 mots en moyenne.
  C'est la source du volume lexical et de la quasi-totalité des n-grammes.

```bibtex
@inproceedings{philippy-etal-2025-luxembedder,
    title     = "{L}ux{E}mbedder: A Cross-Lingual Approach to Enhanced {L}uxembourgish Sentence Embeddings",
    author    = "Philippy, Fred and Guo, Siwen and Klein, Jacques and Bissyande, Tegawende",
    booktitle = "Proceedings of the 31st International Conference on Computational Linguistics",
    year      = "2025",
    address   = "Abu Dhabi, UAE",
    publisher = "Association for Computational Linguistics",
    url       = "https://aclanthology.org/2025.coling-main.753/",
    pages     = "11369--11379"
}
```

## LETZ

Phrases d'exemple du **Lëtzebuerger Online Dictionnaire** (<https://lod.lu>),
dont les données d'origine sont publiées en CC0 par le Luxembourgish Open Data
Platform.

- Dépôt : <https://huggingface.co/datasets/fredxlpy/LETZ> (configs `LETZ-SYN`, `LETZ-WoT`)
- Auteurs : Fred Philippy, Shohreh Haddadan, Siwen Guo
- Licence : **CC BY 4.0** — attribution obligatoire, usage commercial autorisé
- Apport : 5 862 phrases, ~53 k mots. Cent fois plus petit que LuxAlign, mais
  seul porteur du registre quotidien — deuxième personne, famille, objets
  courants. Ce sont les mots qu'on tape sur un téléphone et que la presse
  écrite n'emploie jamais.

```bibtex
@inproceedings{philippy-etal-2024-forget,
    title     = "Forget {NLI}, Use a Dictionary: Zero-Shot Topic Classification for Low-Resource Languages with Application to {L}uxembourgish",
    author    = "Philippy, Fred and Haddadan, Shohreh and Guo, Siwen",
    booktitle = "Proceedings of the 3rd Annual Meeting of the Special Interest Group on Under-resourced Languages @ LREC-COLING 2024",
    year      = "2024",
    address   = "Torino, Italia",
    publisher = "ELRA and ICCL",
    url       = "https://aclanthology.org/2024.sigul-1.13",
    pages     = "97--104"
}
```

## ParaLux — jeu d'évaluation, pas corpus

Les chiffres de prédiction publiés sur [la page corpus du site](https://famibelle.github.io/LuxKeyb/corpus.html)
sont mesurés sur **ParaLux**, qui n'entre jamais dans le dictionnaire.

- Dépôt : <https://huggingface.co/datasets/fredxlpy/ParaLux>
- Licence : **CC BY-NC 4.0**
- Citation : **la même que LuxAlign** — ParaLux est issu du même article
  (LuxEmbedder, Philippy et al.), le dépôt renvoyant à sa préproduction arXiv
  `2412.03331`. Reprendre le BibTeX ci-dessus.

Pourquoi il n'a pas été retenu comme corpus : c'est un banc d'essai de
détection de paraphrase, pas un texte. Ses 312 exemples ne contiennent que
**312 phrases authentiques distinctes** — le jeu est bâti sur 156 paires
mutuelles, chaque phrase figurant une fois comme ancre et une fois comme
paraphrase d'une autre. Cela pèse 5 669 mots, soit 0,18 % du corpus. Surtout,
sa colonne `not_paraphrase` contient des altérations **fabriquées exprès pour
être fausses** (« aus hirem Haus » là où l'original dit « aus hirem Auto ») :
les verser dans un dictionnaire de fréquences reviendrait à y injecter des
phrases délibérément erronées.

Pourquoi il est précieux quand même : **aucune de ses phrases ne figure dans
LuxAlign**, alors qu'ils partagent leur source RTL.lu. C'est donc le seul jeu
réellement inédit dont on dispose. Une partition aléatoire du corpus
d'entraînement flatte le modèle — mêmes articles, même période, même style —
et annonçait 23,9 % de bonnes propositions en top-3 là où ParaLux en donne
18,8 %. C'est ce dernier chiffre qui est publié.

Comme rien n'en est redistribué, seul l'usage local du jeu est concerné par sa
licence.

## Conséquence sur la licence du projet

Le code de l'application et les données qu'elle embarque **ne sont pas sous la
même licence**, et c'est délibéré :

- le **code** reste sous la licence du dépôt ;
- les **assets de dictionnaire** (`luxemburgish_dict.json`,
  `luxemburgish_ngrams.json`) sont des œuvres dérivées de LuxAlign et héritent
  donc de sa clause **NonCommercial** ;
- l'actif du jeu « Wuertlück » (`luxemburgish_cloze.json`) va plus loin : il ne
  contient pas des fréquences mais **1 600 phrases entières reprises telles
  quelles** des deux corpus, un mot masqué près. C'est une redistribution
  d'extraits, pas une statistique agrégée. Elle est couverte par les mêmes
  licences — CC BY-NC pour LuxAlign, CC BY pour LETZ — **à condition que
  l'attribution soit visible**. Elle l'est à trois endroits : la clé `sources`
  du fichier lui-même, la carte « Règles du jeu » sous le plateau, et la
  mention de la source sous chaque phrase. Ne pas retirer ces trois affichages.

« Open source » ne lève pas la clause NC : les licences approuvées par l'OSI
autorisent explicitement l'usage commercial, ce que CC BY-NC interdit. Une
distribution payante de l'application, ou une réutilisation commerciale des
fichiers de dictionnaire, demande l'accord préalable des ayants droit — le
contact indiqué par les auteurs pour les données RTL est <ai@rtl.lu>.

## LOD — Lëtzebuerger Online Dictionnaire

Dictionnaire officiel de la langue luxembourgeoise, publié par le **Zenter fir
d'Lëtzebuerger Sprooch** (ZLS). Il sert deux fois : les **traductions
françaises** affichées dans les jeux et l'onglet Wierderbuch, et depuis le
2026-09-02 les **formes que le corpus ne connaît pas**. Il n'entre toujours pas
dans le modèle n-grammes, qui reste entièrement corpus.

- Portail : <https://data.public.lu/fr/organizations/zenter-fir-dletzebuerger-sprooch/>
- Site : <https://lod.lu>
- Éditeur : Zenter fir d'Lëtzebuerger Sprooch (ministère de l'Éducation nationale)
- Licence : **CC0 1.0** — domaine public, aucune obligation
- Apport aux gloses : 27 081 articles glosés en français et 199 015 graphies
  indexées, qui donnent une traduction à 20 604 des 38 410 formes du
  dictionnaire (53,6 % des formes, 87,8 % des occurrences) et à 68 248 des
  84 855 formes que le LOD ajoute au clavier.
- Apport aux formes : **84 855 formes proposables** et 26 424 variantes de la
  règle d'Eifel connues du seul correcteur, soit 38 410 → 123 265 formes
  reconnues à la frappe.

### Pourquoi le corpus ne suffisait pas

Des locuteurs ont signalé des mots manquants. LuxAlign est du journalisme
RTL.lu : il n'écrit jamais ce qu'on tape sur un téléphone. Manquaient ainsi
`Läffelen`, `Forschetten`, `Telleren`, `Mounden`, `sprang`, `denks`,
`schaffesch`, `schreifs` — toutes attestées au LOD. Le dictionnaire couvre
38 410 formes, le LOD en propose 103 688, et leur intersection n'est que de
18 752.

L'inverse est vrai aussi, et c'est pourquoi il s'agit d'une **union et jamais
d'un remplacement** : 19 374 entrées du dictionnaire sont inconnues du LOD —
`Rue`, `CSV`, `RTL`, `Bettel`, `Juncker`, `OGBL` — soit 7,3 % des occurrences
du corpus. Noms propres, sigles et emprunts, qu'aucun dictionnaire de langue
n'a vocation à lister.

La couverture « intégrale » n'existe pas : le luxembourgeois compose à
l'infini, et certaines flexions courantes (`kaafs`, `lafs`, `schwätzs`)
n'apparaissent dans aucune des deux sources.

Deux ressources sont consultées, et il faut les deux :

| Jeu de données | Fichier | Rôle |
|---|---|---|
| *Linguistesch Daten* | `new_lod-art.xml` | les articles, donc les traductions |
| *Index vun der Sich-Funktioun* | `new_lod-search.xml` | les graphies, donc les flexions |

L'index de recherche n'est pas un confort. Le dictionnaire de l'application
contient des formes fléchies (`Haiser`, `huet`, `goufen`) que la liste des
lemmes du LOD ne connaît pas : passer par les `<spelling>` de l'index fait
monter la couverture de 36 % à 54 % des formes, et de 72 % à 88 % des
occurrences.

**CC0 n'oblige à rien, et on cite quand même.** Le ZLS est crédité dans la clé
`attribution` de l'actif, dans la carte « Sources » de l'onglet « À propos », et
`TranslationAssetTest` échoue si la mention disparaît. La licence rend cette
attribution facultative en droit ; elle ne la rend pas facultative ici.

Régénération, dans cet ordre et après `LuxembourgishComplet.py` :

```bash
python Dictionnaires/generate_lod_forms.py --strict     # les formes
python Dictionnaires/generate_translations.py --strict  # les gloses, qui lisent les formes
```

`Dictionnaires/lod_source.py` porte l'accès partagé : il résout l'URL de la
dernière édition via l'API de data.public.lu — le ZLS republie chaque trimestre
sous un chemin horodaté — et met les deux XML en cache sous
`Dictionnaires/luxemburgish_data/lod/` (180 Mo, hors dépôt).

## Lexique 3.83 — le français de la seconde rangée

Base lexicale de référence du français, publiée par Boris New et Christophe
Pallier. Elle n'alimente **que le français** : ni le dictionnaire
luxembourgeois, ni le modèle n-grammes, ni les jeux, ni le Wierderbuch.

- Site : <http://www.lexique.org>
- Auteurs : Boris New, Christophe Pallier
- Licence : **CC BY-SA 4.0** — attribution et partage à l'identique
- Citation : New, B., Pallier, C., Brysbaert, M., Ferrand, L. (2004).
  *Lexique 2 : A New French Lexical Database.* Behavior Research Methods,
  Instruments, & Computers, 36(3), 516-524.
- Apport : **125 348 formes fléchies** et leur fréquence par million, contre
  les 662 mots aux fréquences écrites à la main que le projet livrait
  auparavant, hérités du clavier créole dont il est issu.

### Pourquoi cet actif comptait plus qu'il n'y paraissait

`french_simple_dict.json` sert deux fonctions, et la seconde est la plus
exposée. Il remplit la seconde rangée de suggestions, à partir de trois lettres
— visible, mais secondaire. Il alimente surtout `containsWord()`, donc
`SuggestionEngine.isKnownWord()`, donc la décision du correcteur orthographique
système de souligner un mot ou non. Or `res/xml/kreyol_spellchecker.xml`
déclare la locale **`fr`** : le clavier **remplace le correcteur français du
système**. Avec 662 mots, il soulignait la quasi-totalité du français écrit par
l'utilisateur, dans toutes ses applications.

C'est pourquoi les formes rares sont conservées au lieu d'être coupées sur un
seuil de fréquence : un mot rare mais correct ne doit pas être souligné. 92 805
des 125 348 formes sont au plancher de 1 occurrence par million, et elles ne
remonteront jamais dans les suggestions — elles servent à ne pas être
signalées comme des fautes.

### Deux pièges du fichier source

- Les fréquences de Lexique sont données **par entrée (forme, lemme,
  catégorie)** et non par forme : `est` figure en ADJ, NOM, AUX et VER avec
  quatre valeurs différentes. Il faut les **sommer** par graphie, sinon la
  forme la plus fréquente du français hérite du score du point cardinal.
- Deux registres sont livrés, `freqfilms2` (sous-titres) et `freqlivres`. On
  additionne les deux, pour la même raison que LuxAlign et LETZ coexistent côté
  luxembourgeois : `bonjour` vaut 569,88 aux sous-titres contre 50,74 aux
  livres, un rapport de 11, mais ne garder que le parlé perdrait `cependant` et
  `notamment`, que les gens écrivent aussi.

### Conséquence sur la licence

CC BY-SA impose le **partage à l'identique de l'œuvre dérivée** :
`french_simple_dict.json` est donc distribué sous CC BY-SA 4.0, séparément des
actifs luxembourgeois qui héritent, eux, de la clause NonCommercial de
LuxAlign. L'attribution figure dans le fichier lui-même (clés `source`,
`licence`, `attribution`), dans ce document, et dans la carte « Sources » de
l'application. `FrenchDictAssetTest` échoue si elle disparaît.

## Iwwersetzungskorpus du ZLS — jeu d'évaluation, pas corpus

Corpus de traduction parallèle publié par le **Zenter fir d'Lëtzebuerger
Sprooch** (Tech-in-GOV 2025) : 10 807 segments, ~153 600 mots luxembourgeois
traduits par des professionnels en français, allemand et anglais, à partir de
sources publiques (Chambre, presse, LOD) et **orthographiquement
standardisés**.

- Portail : <https://data.public.lu/fr/datasets/meisproochegen-iwwersetzungskorpus-fir-dletzebuergescht/>
- Éditeur : Zenter fir d'Lëtzebuerger Sprooch
- Licence : **CC0 1.0** — domaine public, aucune obligation. Le ZLS est crédité
  quand même, comme pour le LOD.
- Accès : `Dictionnaires/zls_source.py`, qui demande à l'API de data.public.lu
  la ressource la plus récente et met l'archive en cache hors du dépôt.

### Pourquoi il n'entre pas dans le dictionnaire

Il le pourrait, et cela a été mesuré le 2026-09-04. L'ajouter à l'entraînement
apporte 1 719 formes et 1 099 contextes, mais sur ParaLux — le seul jeu
indépendant des deux — le top-3 passe de 20,8 % à 20,9 % et la couverture de
94,5 % à 94,7 % : trois événements sur 2 703, soit du bruit. Les formes gagnées
sont thématiques (`Kryptowärung`, `Palliativmedezin`, `Atomprogramm`) ou des
noms propres, 66 % capitalisées, et 1 189 des 1 719 n'apparaissent que trois
fois dans 153 000 mots.

En le gardant dehors, on obtient en revanche **le seul grand jeu inédit du
projet** : 6,84 % de recouvrement avec LuxAlign + LETZ, donc 10 038 segments et
**135 343 événements de frappe** après filtrage — contre 312 phrases et 2 703
événements pour ParaLux. Cinquante fois plus de matière, soit ±0,1 point de
bruit statistique au lieu de ±0,8.

L'échange serait mauvais : quelques formes thématiques contre l'instrument qui
mesure tout le reste.

### Ce qu'il mesure

Les deux jeux sont évalués côte à côte par `docs/scripts/generate_corpus_stats.py` :

| | ParaLux | ZLS |
|---|---|---|
| phrases inédites | 312 | 10 038 |
| événements | 2 703 | 135 343 |
| couverture lexicale | 94,5 % | 93,6 % |
| contexte reconnu | 87,6 % | 87,8 % |
| top-3 | 20,8 % | 18,1 % |

Ils s'accordent à moins d'un point sur la couverture et le contexte, ce qui les
valide mutuellement. L'écart de 2,7 points sur le top-3 va dans le sens
attendu : ParaLux partage la source RTL.lu et le registre de LuxAlign, il
flatte légèrement. **Publier le chiffre ZLS**, garder ParaLux comme témoin.

## Corpus retiré

`POTOMITAN/luxembourgish-corpus` (157 tours de parole de conférences de presse
gouvernementales) alimentait le dictionnaire jusqu'à cette version. Il a été
retiré pour deux raisons :

1. **Contamination allemande.** Les ministres passent régulièrement au
   Hochdeutsch en pleine réponse. 2,7 % des occurrences étaient des mots
   allemands sans ambiguïté, et 42 d'entre eux étaient livrés dans le
   dictionnaire : `und` 372, `wir` 352, `auch` 324, `ich` 124, `ist` 120,
   `für` 112. Sur un préfixe `au`, le clavier proposait `auch`.
2. **Registre inadapté.** Personne ne tutoie en conférence de presse : le
   corpus ne contenait pratiquement aucune forme de deuxième personne.

Attention en revanche : **`dass` n'est pas un germanisme.** C'est une variante
orthographique luxembourgeoise légitime, employée dans les deux corpus
retenus (`dass` 10 749 contre `datt` 14 189 dans le dictionnaire livré). Ne
pas l'ajouter à une éventuelle liste d'exclusion.
