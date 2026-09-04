# Recommandations — état au 4 septembre 2026

Note écrite au terme d'une série de mesures sur le clavier : corpus, mémoire,
latence, et une session sur un vrai téléphone d'entrée de gamme. Elle dit ce
qu'il reste à faire, dans quel ordre, et — tout aussi important — **ce qu'il ne
faut pas faire**, avec les chiffres qui l'établissent.

Comme [`ACCESSIBILITE.md`](ACCESSIBILITE.md) et [`SWIPE-ESPACE.md`](SWIPE-ESPACE.md),
ce fichier vit à la racine : le filtre de chemins de `build-apk.yml` couvre
`android_keyboard/**`, l'y déposer déclencherait un build complet à chaque
correction de typo.

---

## 1. Le seul verrou pour la production

L'application est **déjà publiée en test fermé** sur le Play Store. Ce qui
manque n'est pas technique :

- **Douze testeurs pendant quatorze jours consécutifs.** Exigence de Google,
  pas un choix. État : **9 sur 12** (`docs/stats/testeurs.json`, tenu à la main,
  il n'existe pas d'API publique). Le délai ne dépend de personne d'autre.
- **Un courriel à <ai@rtl.lu> avant la publication ouverte.**
  `luxemburgish_cloze.json` redistribue 1 600 phrases entières de RTL.lu, mot
  pour mot : ce n'est plus une statistique agrégée mais de la republication.
  CC BY-NC l'autorise pour un usage non commercial avec attribution visible —
  les deux conditions sont remplies — mais c'est le seul point où un tiers peut
  dire non, et les auteurs indiquent eux-mêmes ce contact. Voir
  [`NOTICE.md`](NOTICE.md).

Tout le reste de la fiche est prêt : licence explicite, description à jour,
captures du Store et du guide refaites sous la 11.3.0+.

---

## 2. À faire, par ordre de valeur

### 2.1 Le repli Levenshtein reste le chemin le plus lent

Mesuré sur un Galaxy A21s (2,7 Go de RAM, build de debug), journaux à l'appui :

| frappe | latence |
|---|---|
| trouve un préfixe luxembourgeois | **46 – 93 ms** |
| déclenche le repli de correction | **670 – 1 180 ms** |

La 11.4.1 a supprimé le cas le plus fréquent — un mot français reconnu ne
déclenche plus la correction luxembourgeoise, ce qui a fait passer la dernière
lettre de « déchet » de 914 ms à 50 ms. **Mais le repli reste cher partout
ailleurs** : mot allemand, nom propre, composé, vraie faute de frappe.

`getSpellCorrectionSuggestions()` parcourt les 38 442 formes **deux fois**, la
passe normalisée puis la directe. Trois leviers, par rendement décroissant :

1. **Ne pas lancer la seconde passe** quand la première a répondu — c'est déjà
   le cas (`if (normalizedMatches.isNotEmpty()) return`), mais la passe directe
   se déclenche donc systématiquement sur les mots vraiment inconnus, qui sont
   précisément le cas fréquent. À mesurer : que rapporte-t-elle réellement ?
2. **Filtrer par longueur avant de calculer la distance.** `maxDistance = 2`
   implique `|len(a) − len(b)| ≤ 2` : un test entier écarte la grande majorité
   des candidats avant toute allocation.
3. **Relever le seuil de déclenchement de 3 à 4 lettres.** Sous quatre lettres,
   une distance de 2 rapproche presque n'importe quoi.

### 2.2 L'allemand, deuxième langue de la seconde rangée

Mesuré sur les 158 tours des conférences de presse du gouvernement, avec le
découpage réel du moteur : **26,7 % des phrases avaient au moins un mot
souligné avant** le dictionnaire français, **23,1 % après**. Le gain est réel
mais modeste, et ce qui reste souligné est **presque intégralement de
l'allemand** : `sehr`, `können`, `unsere`, `glaube`, `miteinander`, `nur`,
`sondern`, `geht`, `dieser`, `deshalb`.

Au Luxembourg, la langue qu'on insère dans le luxembourgeois n'est pas d'abord
le français, c'est l'allemand. Sur ce corpus, il cause plus de soulignements
que le français n'en causait.

Le chemin est tracé par le français : deux paliers, partage grammatical, filtre
de Bloom pour la reconnaissance. Trois contraintes connues d'avance :

- la rangée du bas n'a que **deux places** (`frenchSuggs.take(2)`), et il
  faudra laisser le score décider plutôt que réserver une place par langue ;
- le code couleur est à court de couleurs — rouge et bleu sont les deux bandes
  utilisables du drapeau. `BilingualSuggestion.getShortLabel()` renvoie déjà
  « LB » et « FR » : l'étiquette devra devenir le repère, pas la couleur ;
- en paysage il n'y a qu'une rangée pour tout le monde.

### 2.3 Le glissement sur la barre d'espace

Voir [`SWIPE-ESPACE.md`](SWIPE-ESPACE.md), écrit avant tout code. En résumé :
le glissement horizontal doit **déplacer le curseur**, pas changer de clavier,
et le travail est petit parce que `syncWordWithCursor()` existe déjà et que le
clavier ne compose pas. Deux à trois heures de code, autant de réglage sur
appareil.

### 2.4 Signaler le défaut de lod.lu au ZLS

Leur route de recherche `/sich/<langue>/<mot>` ne répond pas à une ouverture à
froid : le composant émet sa requête sur un bus d'événements avant que rien
n'écoute, et la page retombe sur son état vide. Vérifié en navigateur le
2026-09-03 ; leur API, elle, répond correctement. Le clavier contourne en
passant par `/artikel/<ID>`, mais **toute personne qui partage un lien de
recherche lod.lu envoie les gens sur cette page vide**.

### 2.5 Les jeux de données du ZLS — ce qui reste à en tirer

Les neuf jeux de <https://data.public.lu/fr/organizations/zenter-fir-dletzebuerger-sprooch/>
ont été analysés le 2026-09-04. Deux étaient déjà utilisés (données
linguistiques et index de recherche du LOD). Un troisième l'est depuis :
l'**Iwwersetzungskorpus**, comme jeu d'évaluation (voir `CORPUS.md`).

Restent deux pistes et quatre impasses :

- **Flexiounstabellen** (CC0, 9 851 tables : 6 204 verbes, 3 647 adjectifs).
  N'apporte **aucune couverture** — 19 072 de ses 19 074 formes fléchies sont
  déjà reconnues par le clavier, l'index de recherche les portait. Sa valeur est
  la **structure** : savoir que `aachten` est le datif masculin singulier de
  `aacht`. Utile pour afficher un tableau de conjugaison dans la fiche
  Wierderbuch, et la jointure serait immédiate — les tables portent le même
  identifiant d'article que `luxemburgish_lod_ids.json`, dont 7 882 des 9 851
  sont déjà indexés.
- **Le corpus ZLS republie périodiquement.** Une édition ultérieure fournirait
  un jeu d'évaluation frais sans rien coûter.
- **LOD Public API** : une API en ligne, sans objet pour un clavier hors ligne.
- **Sproochemodell fir Sproocherkennung** et **Dataset for finetuning ASR** :
  pertinents pour la dictée seulement, gelée jusqu'au rendez-vous avec l'Uni.lu.
- **Hackathon LOD data** (349 Mo) et **Hackathon data 2** (1,9 Go) : licence
  `notspecified`. Sans licence déclarée, aucun droit de redistribuer ce qui en
  dérive. À ne pas ouvrir.

---

## 3. Ce qu'il ne faut pas faire

Ces quatre points sont mesurés. Ils sont ici pour éviter qu'on les retente.

### 3.1 Ne pas remplir la barre de suggestions quand elle est vide

Sur 32 086 positions du corpus gouvernemental :

| stratégie | remplit | bon mot en top-3 |
|---|---|---|
| contexte n-gramme réel | — | **18,6 %** |
| reprendre le contexte d'avant le mot inconnu | 60 % | 2,0 % |
| afficher les trois mots les plus fréquents | 100 % | 2,7 % |
| oracle « après un mot inconnu », ajusté sur son propre jeu de test | 100 % | 4,3 % |

Des puces justes une fois sur vingt-cinq, **habillées exactement comme celles
qui le sont une fois sur cinq**, valent moins qu'une barre vide : elles invitent
à toucher, et elles détruisent le signal que porte la barre. La barre qui
s'éteint est l'aveu honnête que le modèle n'a rien à dire.

Le repli « ignorer le mot inconnu » échoue pour une raison précise et non pour
un manque de réglage : il répond à la **question précédente**. Après
« direkter vum hcpn », il propose les candidats de la place qu'occupe déjà
`hcpn` — *Statec, Fonds, Lycée*.

### 3.2 Ne pas ajouter de correction Levenshtein pour le français

Le repli ne porte que sur le luxembourgeois, et c'est délibéré. `FrenchDictionary`
n'a que la recherche par préfixe. Ajouter une passe française « par symétrie »
doublerait le chemin le plus lent du clavier pour corriger des fautes dans une
langue qu'on ne fait qu'**insérer**.

### 3.3 Ne pas remplacer le partage grammatical du français par un seuil de fréquence

Mesuré sur les insertions françaises réelles d'un corpus luxembourgeois :

| | formes | proposé | reconnu |
|---|---|---|---|
| tout garder | 125 348 | 75,0 % | 100 % |
| **écarter les formes verbales rares** | **71 586** | **71,9 %** | 100 % |
| fréquence ≥ 2 | 25 811 | 60,6 % | 100 % |

`résilience`, `incitatif`, `législation` sont au plancher de fréquence et sont
précisément ce qu'on insère ; la queue de la conjugaison ne l'est par personne.

### 3.4 Ne pas verser l'Iwwersetzungskorpus du ZLS à l'entraînement

Mesuré : +1 719 formes et +1 099 contextes, mais sur ParaLux le top-3 passe de
20,8 % à **20,9 %** — trois événements sur 2 703. En échange on perdrait le seul
grand jeu inédit du projet : 10 038 segments, 135 343 événements, cinquante fois
ParaLux. Les formes gagnées sont thématiques (`Kryptowärung`, `Atomprogramm`)
ou des noms propres, et 1 189 des 1 719 n'apparaissent que trois fois.

### 3.5 Ne pas rétrécir davantage l'index de suggestion français

Il ne pèse que **673 Ko**, parce qu'il ne porte que des indices entiers — les
chaînes doivent exister de toute façon pour le correcteur. Le diviser par trois
économise 400 Ko et coûte 11 points de propositions.

---

## 4. Trois leçons de mesure

Elles ont chacune coûté une conclusion fausse dans cette session.

**Mesurer sur un vrai téléphone d'entrée de gamme.** L'émulateur sous
swiftshader donne des latences sans rapport, et il ne montre pas ce qu'un
Galaxy A21s montre : le clavier n'y est pas tué, il est **compressé en swap** —
124 Mo relevés en zram sur la version installée.

**Ne jamais comparer deux versions qui ne découpent pas les mots pareil.** Le
correctif des mots d'une lettre est apparu comme une régression de 1,1 point
alors qu'il apporte 3,7 points : `generate_corpus_stats.py` filtrait `>= 2` à
deux endroits et `bench_frappes.js` gardait l'apostrophe dans le mot, quand
`InputProcessor.isWordCharacter` fait l'inverse sur les deux points. **Les trois
tokenisations — application, pipeline, bancs — doivent rester alignées**, et
rien ne le vérifie aujourd'hui : un test le mériterait.

**Distinguer debug et release.** Les journaux sont supprimés en release
(`-assumenosideeffects` sur `android.util.Log`, vérifié dans le dex livré), donc
toute mesure de latence passe par un build de debug, non minifié : les valeurs
absolues sont pessimistes, seuls les rapports sont fiables. Et une mesure
mémoire prise sur un debug embarquant le modèle STT ne veut rien dire.

---

## 5. Deux points d'hygiène

- **Relire les chiffres de la fiche Play Store à chaque envoi.** Ils ont
  annoncé 8 792 mots et « environ 3 Mo » pendant des mois, quand l'application
  en livrait cinq fois plus. La procédure est dans
  `LuxKeybPlayStore/texts/fichePlayStore.md`, section « À régler ».
- **La touche `-` mérite un arbitrage.** Le corpus actuel la place troisième
  parmi les caractères non alphabétiques (44 193 occurrences, 3,2× `:`), alors
  qu'elle n'est qu'un appui long sur le point. Upstream lui donne une touche
  entière. Voir la section *Keyboard layout* de `CLAUDE.md`.
